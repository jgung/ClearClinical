package edu.uab.ccts.nlp.uima.annotator;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Hashtable;

import org.apache.ctakes.typesystem.type.structured.DocumentID;
import org.apache.ctakes.typesystem.type.syntax.BaseToken;
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
import org.cleartk.semeval2015.type.TokenMutualInformation;

import edu.colorado.clear.clinical.ner.util.SemEval2015Constants;

/**
 * Should compute token and bigram counts for the input document
 * @author ozborn
 *
 */
public class MutualInformationAnnotator extends JCasAnnotator_ImplBase {

	public static final String default_db_name = "unsup";
	public static final String default_db_path = "src/main/resources/hsqldb/"+default_db_name;
	public static final String default_db_url = "jdbc:hsqldb:file:"+default_db_path;
	public static final String default_db_user = "SA";
	public static final String default_db_password = "";
	public static final String default_unsupervised_corpus = "src/main/resources/"+
	"semeval-2014-unlabelled-mimic-notes.v1";

	public static final String unigram_table_create = 
			"CREATE TABLE NLP_UNIGRAM ( "+
					"DOCID VARCHAR(40) NOT NULL "+
					", MI_TOKEN VARCHAR(100) NOT NULL "+ 
					", OBSERVED INT DEFAULT 0 , "+
					" PRIMARY KEY(docid,mi_token) )";

	public static final String bigram_table_create = "CREATE TABLE NLP_BIGRAM ("+
			"DOCID VARCHAR(40) NOT NULL "+
			", FIRST_TOKEN VARCHAR(100) NOT NULL "+ 
			", SECOND_TOKEN VARCHAR(100) NOT NULL "+ 
			", OBSERVED INT DEFAULT 0 ,"+
			" PRIMARY KEY(docid,first_token,second_token))";

	public static final String unigram_index = "CREATE INDEX tindex ON NLP_UNIGRAM (mi_token)";
	public static final String bigram_index1 = "CREATE INDEX first_index ON NLP_BIGRAM (first_token)";
	public static final String bigram_index2 = "CREATE INDEX second_index ON NLP_BIGRAM (second_token)";

	public static final String PARAM_MI_DATABASE_URL = "miDatabaseUrl";
	@ConfigurationParameter(
			name = PARAM_MI_DATABASE_URL,
			description = "URL to the mutal information database",
			mandatory = true,
			defaultValue=MutualInformationAnnotator.default_db_url)
	private static String miDatabaseUrl = null;

	public static final String PARAM_MI_DATABASE_USER = "miDatabaseUser";
	@ConfigurationParameter(
			name = PARAM_MI_DATABASE_URL,
			description = "User for mutal information database",
			defaultValue=MutualInformationAnnotator.default_db_user)
	private String miDatabaseUser = null;

	public static final String PARAM_MI_DATABASE_PASSWORD = "miDatabasePassword";
	@ConfigurationParameter(
			name = PARAM_MI_DATABASE_URL, 
			description = "URL to the mutal information database",
			defaultValue=MutualInformationAnnotator.default_db_password)
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

	private int global_observed_unigrams=0, global_observed_bigrams=0;


	public int getGlobalObservedUnigrams(){ return global_observed_unigrams; }
	public int getGlobalObservedBigrams(){ return global_observed_bigrams; }

	@Override
	public void process(JCas jCas) throws AnalysisEngineProcessException {

		System.out.println("Input MI Database URL was:"+miDatabaseUrl); System.out.flush();
		this.getContext().getLogger().log(Level.FINE,"Database User was:"+miDatabaseUser);
		this.getContext().getLogger().log(Level.FINE,"Database Password was:"+miDatabasePassword);
		JCas appView = null;
		String docid = null;
		Hashtable<String,Integer> unigram_counts = new Hashtable<String,Integer>();
		Hashtable<String,Hashtable<String,Integer>> bigram_counts = new Hashtable<String,Hashtable<String,Integer>>(); //Use || to divide

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
		getTokenCounts(appView, unigram_counts, bigram_counts);
		if(isTrain) {
			//Writes to our Mutual Information Database
			Connection _dbConnection = null;
			try {
				//_dbConnection = DriverManager.getConnection(miDatabaseUrl,"SA","");
				_dbConnection = DriverManager.getConnection(default_db_url);
				PreparedStatement pst = _dbConnection.prepareStatement(
						"INSERT INTO NLP_UNIGRAM (DOCID,MI_TOKEN,OBSERVED) VALUES (?,?,?)");
				for(String qkey : unigram_counts.keySet()){
					try {
						Integer count = unigram_counts.get(qkey);
						pst.setString(1, docid);
						pst.setString(2,qkey);
						pst.setInt(3, count);
						pst.executeUpdate();
					} catch (java.sql.SQLIntegrityConstraintViolationException ok) {ok.printStackTrace();}

				}
				pst = _dbConnection.prepareStatement(
						"INSERT INTO NLP_BIGRAM (DOCID,FIRST_TOKEN,SECOND_TOKEN,"+
						"OBSERVED) VALUES (?,?,?,?)");
				pst.setString(1, docid);
				for(String first : bigram_counts.keySet()){
					Hashtable<String,Integer> thecounts = bigram_counts.get(first);
					for(String second : thecounts.keySet()){
						try {
							Integer observed = thecounts.get(second);
							pst.setString(2,first);
							pst.setString(3,second);
							pst.setInt(4, observed);
							pst.executeUpdate();
						} catch (java.sql.SQLIntegrityConstraintViolationException ok) {}
					}
				}
				_dbConnection.close();
			} catch (Exception e)
			{
				this.getContext().getLogger().log(Level.SEVERE,"Failed to update database");
				e.printStackTrace();
			}

		} else {
			//Not training, create the required MutualInformation Annotations
			// with statistics
			Connection _dbConnection;
			ResultSet resultset;
			try {
				//_dbConnection = DriverManager.getConnection(miDatabaseUrl, miDatabaseUser, miDatabasePassword);
				_dbConnection = DriverManager.getConnection(miDatabaseUrl);
				Statement st = _dbConnection.createStatement();
				String query ="SELECT SUM(OBSERVED) FROM NLP_UNIGRAM";
				resultset = (ResultSet) st.executeQuery(query);
				while(resultset.next()){
					global_observed_unigrams = resultset.getInt(1);
				}
				resultset.close();
				query ="SELECT SUM(OBSERVED) FROM NLP_BIGRAM";
				resultset = (ResultSet) st.executeQuery(query);
				while(resultset.next()){
					global_observed_bigrams = resultset.getInt(1);
				}
				Integer all_unigram_count = getTokenCounts(_dbConnection, "NLP_UNIGRAM");
				Integer all_bigram_count = getTokenCounts(_dbConnection, "NLP_BIGRAM");
				
				for(String first : bigram_counts.keySet()){
				Hashtable<String,Integer> thecounts = bigram_counts.get(first);
				Integer first_count = unigram_counts.get(first);
					for(String second : thecounts.keySet()){
						Integer second_count = unigram_counts.get(first);
						TokenMutualInformation tkmi = new TokenMutualInformation(appView);
						tkmi.setAnnotation_x(first);
						tkmi.setAnnotation_y(second);
						tkmi.setCount_x(first_count);
						tkmi.setCount_y(second_count);
						Integer jointcount = thecounts.get(second);
						tkmi.setCount_xy(jointcount);
						double mi = (((double)first_count/all_unigram_count)*((double)second_count/all_unigram_count))/((double)jointcount/all_bigram_count);
						tkmi.setMi(mi);
						tkmi.addToIndexes();
					}
				}
				_dbConnection.close();

			} catch (Exception e)
			{
				this.getContext().getLogger().log(Level.SEVERE,"Failed to get connection to database");
				e.printStackTrace();
			}

		}

	}
	private void getTokenCounts(JCas appView,
			Hashtable<String, Integer> unigram_counts,
			Hashtable<String, Hashtable<String, Integer>> bigram_counts
		) {
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
	}

	
	private int getTokenCounts(Connection conn, String table_name){
		Integer count = 0;
		try {
			String ucountsql = "SELECT SUM(OBSERVED) FROM "+table_name;
			Statement st = conn.createStatement();
			ResultSet resultset = (ResultSet) st.executeQuery(ucountsql);
			while(resultset.next()){
				count = resultset.getInt(1);
			}
			resultset.close();
		} catch (Exception e) { e.printStackTrace(); }
		return count;
	}

	

	public static void initialize_database() {
		try {
			Class.forName("org.hsqldb.jdbc.JDBCDriver" );
		} catch (Exception e) {
			System.out.println("ERROR: failed to load HSQLDB JDBC driver.");
			e.printStackTrace();
			return;
		}
		try {
			Connection _dbConnection = DriverManager.getConnection(default_db_url);
			Statement st = _dbConnection.createStatement();
			st.execute(unigram_table_create);
			st.execute(bigram_table_create);
			st.execute(unigram_index);
			st.execute(bigram_index1);
			st.execute(bigram_index2);
			_dbConnection.close();
		} catch (Exception e) { e.printStackTrace(); }
	}

	public static AnalysisEngineDescription createAnnotatorDescription()
			throws ResourceInitializationException {

		return AnalysisEngineFactory.createPrimitiveDescription(
				MutualInformationAnnotator.class,
				MutualInformationAnnotator.PARAM_MI_DATABASE_URL,
				MutualInformationAnnotator.default_db_url,
				MutualInformationAnnotator.PARAM_MI_DATABASE_USER,
				MutualInformationAnnotator.default_db_user,
				MutualInformationAnnotator.PARAM_MI_DATABASE_PASSWORD,
				MutualInformationAnnotator.default_db_password,
				MutualInformationAnnotator.PARAM_IS_TRAINING,
				true);
	}

}
