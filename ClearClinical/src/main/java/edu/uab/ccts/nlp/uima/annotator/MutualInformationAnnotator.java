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
			description = "URL to the mutal information database",
			mandatory = true,
			defaultValue=SemEval2015Constants.default_db_url)
	private String miDatabaseUrl = null;

	public static final String PARAM_MI_DATABASE_USER = "miDatabaseUser";
	@ConfigurationParameter(
			name = PARAM_MI_DATABASE_URL,
			description = "User for mutal information database",
			defaultValue=SemEval2015Constants.default_db_user)
	private String miDatabaseUser = null;

	public static final String PARAM_MI_DATABASE_PASSWORD = "miDatabasePassword";
	@ConfigurationParameter(
			name = PARAM_MI_DATABASE_URL, 
			description = "URL to the mutal information database",
			defaultValue=SemEval2015Constants.default_db_password)
	private String miDatabasePassword = null;

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

	@Override
	public void process(JCas jCas) throws AnalysisEngineProcessException {

		System.out.println("Database URL was:"+miDatabaseUrl); System.out.flush();
		System.out.println("Database User was:"+miDatabaseUser); System.out.flush();
		System.out.println("Database Password was:"+miDatabasePassword); System.out.flush();
		JCas appView = null;
		String docid = null;
		Hashtable<String,Integer> unigram_counts = new Hashtable<String,Integer>();
		Hashtable<String,Hashtable<String,Integer>> bigram_counts = new Hashtable<String,Hashtable<String,Integer>>(); //Use || to divide
		
		try {
			Class.forName("org.hsqldb.jdbc.JDBCDriver" );
		} catch (Exception e) {
			System.out.println("ERROR: failed to load HSQLDB JDBC driver.");
			e.printStackTrace();
			return;
		}
		try
		{
			appView = jCas.getView(SemEval2015Constants.APP_VIEW);
		} catch (CASException e)
		{
			e.printStackTrace();
			System.exit(0);
		}
		for(DocumentID di : JCasUtil.select(jCas, DocumentID.class)) {
			docid = di.getDocumentID();
		}
		if(isTrain) {
			String cur_cover=null,prev_cover="___DOCUMENT_START___";
			for(BaseToken tok : JCasUtil.select(appView, BaseToken.class)) {	
				cur_cover = tok.getCoveredText();
				Integer ucount = unigram_counts.get(cur_cover);
				if(ucount==null) unigram_counts.put(cur_cover,1);
				else {
					unigram_counts.put(cur_cover,ucount+1);
				}
				Hashtable<String,Integer>bcounts = bigram_counts.get(prev_cover);
				if(bcounts==null) {
					//First token is not present
					Hashtable<String,Integer> newbie = new Hashtable<String,Integer>();
					newbie.put(cur_cover, 1);
					bigram_counts.put(prev_cover,newbie);
				} else {
					Integer bcount = bcounts.get(cur_cover);
					if(bcount==null) bcounts.put(cur_cover,1);
					else bcounts.put(cur_cover, (bcount+1));
				}
				prev_cover = cur_cover;
			}


			//Write our results to the database
			Connection _dbConnection = null;
			try {
				_dbConnection = DriverManager.getConnection(miDatabaseUrl);
				PreparedStatement pst = _dbConnection.prepareStatement(
						"INSERT INTO NLP_UNIGRAM (DOCID,TOKEN,OBSERVED) VALUES (?,?,?)");
				for(String qkey : unigram_counts.keySet()){
					Integer count = unigram_counts.get(qkey);
					pst.setString(1, docid);
					pst.setString(2,qkey);
					pst.setInt(3, count);
					pst.executeUpdate();
				}
				pst = _dbConnection.prepareStatement(
						"INSERT INTO NLP_BIGRAM (DOCID,FIRST_TOKEN,SECOND_TOKEN,"+
						"OBSERVED) VALUES (?,?,?,?)");
				pst.setString(1, docid);
				for(String first : bigram_counts.keySet()){
					Hashtable<String,Integer> thecounts = bigram_counts.get(first);
					for(String second : thecounts.keySet()){
						Integer observed = thecounts.get(second);
						pst.setString(2,first);
						pst.setString(3,second);
						pst.setInt(4, observed);
						pst.executeUpdate();
					}
				}
				_dbConnection.close();
			} catch (Exception e)
			{
				this.getContext().getLogger().log(Level.SEVERE,"Failed to update database");
				e.printStackTrace();
			}

		} else {
			Connection _dbConnection;
			ResultSet resultset;
			try {
				_dbConnection = DriverManager.getConnection(miDatabaseUrl, miDatabaseUser, miDatabasePassword);
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
				MutualInformationAnnotator.PARAM_MI_DATABASE_USER,
				SemEval2015Constants.default_db_user,
				MutualInformationAnnotator.PARAM_MI_DATABASE_PASSWORD,
				SemEval2015Constants.default_db_password,
				MutualInformationAnnotator.PARAM_IS_TRAINING,
				true);
	}

}
