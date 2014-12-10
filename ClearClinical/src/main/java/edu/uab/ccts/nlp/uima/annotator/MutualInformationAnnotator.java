package edu.uab.ccts.nlp.uima.annotator;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Hashtable;

import org.apache.ctakes.typesystem.type.structured.DocumentID;
import org.apache.ctakes.typesystem.type.syntax.BaseToken;
import org.apache.uima.UimaContext;
import org.apache.uima.analysis_engine.AnalysisEngineDescription;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.cas.CASException;
import org.apache.uima.jcas.JCas;
import org.apache.uima.resource.ResourceInitializationException;
import org.apache.uima.util.Level;
import org.uimafit.component.JCasAnnotator_ImplBase;
import org.uimafit.descriptor.ConfigurationParameter;
import org.uimafit.factory.AnalysisEngineFactory;
import org.uimafit.util.JCasUtil;

import edu.colorado.clear.clinical.ner.util.SemEval2015Constants;

/**
 * Should compute token and bigram counts for the input document
 * @author ozborn
 *
 */
public class MutualInformationAnnotator extends JCasAnnotator_ImplBase {

	private int global_distinct_unigrams=0, global_observed_unigrams=0;
	private int global_distinct_bigrams=0, global_observed_bigrams=0;

	public static final String PARAM_MI_DATABASE_URL = "miDatabaseUrl";
	@ConfigurationParameter(
			name = PARAM_MI_DATABASE_URL,
			description = "URL to the mutal information database")
	private String miDatabaseUrl = null;

	public static final String PARAM_SPLIT_TOKEN_REGEX = "splitTokenRegex";
	@ConfigurationParameter(
			name = PARAM_SPLIT_TOKEN_REGEX,
			description = "String used to tokenize n-grams")
	private String splitTokenRegex = null;

	public static final String PARAM_IS_TRAINING = "isTraining";
	@ConfigurationParameter(
			name = PARAM_IS_TRAINING,
			description = "Indicates whether this annotator should operate in training mode")
	private boolean isTrain = true;


	/**
	 * Should make sure it can connect to the database
	 */
	public void initialize(UimaContext aContext) throws ResourceInitializationException
	{
		super.initialize(aContext);
		Connection _dbConnection = null;
		ResultSet resultset = null;
		if(miDatabaseUrl==null) aContext.getLogger().log(Level.SEVERE,"Given null DBRUL from MIAnnotator init");
		else { aContext.getLogger().log(Level.INFO,"Got dburl of "+miDatabaseUrl); }
		try {
			_dbConnection = DriverManager.getConnection(miDatabaseUrl);
			Statement st = _dbConnection.createStatement();
			String query ="SELECT COUNT(*) FROM NLP_UNIGRAM";
			resultset = (ResultSet) st.executeQuery(query);
			while(resultset.next()){
				global_distinct_unigrams = resultset.getInt(1);
			}
			resultset.close();
			_dbConnection.close();

		} catch (Exception e)
		{
			aContext.getLogger().log(Level.SEVERE,"Failed to get connection to database");
			e.printStackTrace();
		}

	}


	@Override
	public void process(JCas jCas) throws AnalysisEngineProcessException {
		JCas appView = null;
		String docid = null;
		Hashtable<String,Integer> unigram_counts = new Hashtable<String,Integer>();
		Hashtable<String,Integer> bigram_counts = new Hashtable<String,Integer>(); //Use || to divide
		try
		{
			appView = jCas.getView(SemEval2015Constants.APP_VIEW);
		} catch (CASException e)
		{
			e.printStackTrace();
			System.exit(0);
		}
		if(isTrain) {
			String cur_cover=null,prev_cover=null;
			for(BaseToken tok : JCasUtil.select(appView, BaseToken.class)) {	
				cur_cover = tok.getCoveredText();
				Integer ucount = unigram_counts.get(cur_cover);
				if(ucount==null) unigram_counts.put(cur_cover,1);
				else {
					ucount = ucount+1;
				}
				if(prev_cover!=null){
					String query_key = prev_cover+"||"+cur_cover;
					Integer bcount = bigram_counts.get(query_key);
					if(bcount==null) bigram_counts.put(query_key,1);
					else bcount = bcount+1;
				}
				prev_cover = cur_cover;
			}

			for(DocumentID di : JCasUtil.select(appView, DocumentID.class)) {
				docid = di.getDocumentID(); break;
			}

			//Write our results to the database
			Connection _dbConnection = null;
			try {
				_dbConnection = DriverManager.getConnection(miDatabaseUrl);
				PreparedStatement pst = _dbConnection.prepareStatement(
						"INSERT INTO NLP_UNIGRAM (DOCID,TOKEN,COUNT) VALUES (?,?,?)");
				pst.setString(1, docid);
				for(String qkey : unigram_counts.keySet()){
					Integer count = unigram_counts.get(qkey);
					pst.setString(2,qkey);
					pst.setInt(3, count);
					int insert_count =  pst.executeUpdate();
				}
				pst = _dbConnection.prepareStatement(
						"INSERT INTO NLP_BIGRAM (DOCID,FIRST_TOKEN,SECOND_TOKEN,"+
								"COUNT) VALUES (?,?,?,?)");
				pst.setString(1, docid);
				for(String qkey : bigram_counts.keySet()){
					Integer count = bigram_counts.get(qkey);
					String[] tokens = qkey.split("||");
					pst.setString(2,tokens[0]);
					pst.setString(3,tokens[1]);
					pst.setInt(4, count);
					int insert_count =  pst.executeUpdate();
				}
				_dbConnection.close();
			} catch (Exception e)
			{
				this.getContext().getLogger().log(Level.SEVERE,"Failed to get connection to database");
				e.printStackTrace();
			}

		}

	}

	public static AnalysisEngineDescription createAnnotatorDescription()
			throws ResourceInitializationException {

		return AnalysisEngineFactory.createPrimitiveDescription(
				MutualInformationAnnotator.class,
				MutualInformationAnnotator.PARAM_MI_DATABASE_URL,
				SemEval2015Constants.default_db_url,
				MutualInformationAnnotator.PARAM_IS_TRAINING,
				true);
	}

}
