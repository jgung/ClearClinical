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
 * Should compute single and double token counts for the input document
 * The construction of the MI corpus needs to be adjusted to wrap the SELECT
 * and UPDATE statements together in a transaction
 * @author ozborn
 *
 */
public class MutualInformationAnnotator extends JCasAnnotator_ImplBase {


	public static final String default_db_name = "small";
	public static final String default_db_path = "src/main/resources/hsqldb/"+default_db_name;
	public static final String default_db_url = "jdbc:hsqldb:file:"+default_db_path;
	public static final String default_db_user = "SA";
	public static final String default_db_password = "";
	private static final boolean VERBOSE = true;


	/*
	public static final String default_db_name = "medics";
	public static final String default_db_url = "jdbc:oracle:thin:medics/i2b2data@genome-bmidb.ad.uab.edu:1521:ccts2";
	public static final String default_db_user = "medics";
	public static final String default_db_password = "i2b2data";
	 */

	public static final String default_unsupervised_corpus = "src/main/resources/"+
			"semeval-2014-unlabelled-mimic-notes.v1";

	public static final String unigram_table_create = 
			"CREATE TABLE NLP_UNIGRAM ( "+
					//"DOCID VARCHAR(40) NOT NULL "+
					" MI_TOKEN VARCHAR(100) NOT NULL "+ 
					", OBSERVED INT DEFAULT 0 , "+
					//" PRIMARY KEY(docid,mi_token) )";
					" PRIMARY KEY(mi_token) )";

	public static final String bigram_table_create = "CREATE TABLE NLP_BIGRAM ("+
			//"DOCID VARCHAR(40) NOT NULL "+
			" FIRST_TOKEN VARCHAR(100) NOT NULL "+ 
			", SECOND_TOKEN VARCHAR(100) NOT NULL "+ 
			", OBSERVED INT DEFAULT 0 ,"+
			" PRIMARY KEY(first_token,second_token))";
	//" PRIMARY KEY(docid,first_token,second_token))";

	//public static final String unigram_index = "CREATE INDEX tindex ON NLP_UNIGRAM (mi_token)";
	public static final String bigram_index1 = "CREATE INDEX first_index ON NLP_BIGRAM (first_token)";
	public static final String bigram_index2 = "CREATE INDEX second_index ON NLP_BIGRAM (second_token)";
	//FIXME Wrap SELECT and UPDATE statements in HSQLDB PROCEDURE
	public static final String update_counts = " CREATE PROCEDURE update_counts("+
			"first VARCHAR(100), second VARCHAR(100), addme INT) "+
			"  MODIFIES SQL DATA     BEGIN ATOMIC   "+
			"  DECLARE ucount INTEGER, bcount INTEGER;"+
			"  SET ucount = SELECT OBSERVED FROM NLP_UNIGRAM WHERE MI_TOKEN=first"+
			" IF";
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
	
	public static final String PARAM_IS_CONSTRUCTION = "isConstruction";
	@ConfigurationParameter(
			name = PARAM_IS_CONSTRUCTION,
			description = "Indicates whether this annotator should construct a database")
	private boolean isConstruction = false;

	

	private int global_observed_unigrams=0, global_observed_bigrams=0;


	public int getGlobalObservedUnigrams(){ return global_observed_unigrams; }
	public int getGlobalObservedBigrams(){ return global_observed_bigrams; }

	@Override
	public void process(JCas jCas) throws AnalysisEngineProcessException {
		String message = "Input MI Database URL was:"+miDatabaseUrl;
		if(isTrain) message+=" ; training...";
		if(isConstruction) message+= " ; constructing MI database....";
		if(VERBOSE) { System.out.println(message); System.out.flush(); }
		this.getContext().getLogger().log(Level.FINE,"Database User was:"+miDatabaseUser);
		this.getContext().getLogger().log(Level.FINE,"Database Password was:"+miDatabasePassword);
		JCas appView = null;
		JCas goldView = null;
		String docid = null;
		Hashtable<String,Integer> unigram_counts = new Hashtable<String,Integer>();
		Hashtable<String,Hashtable<String,Integer>> bigram_counts = new Hashtable<String,Hashtable<String,Integer>>(); //Use || to divide

		try
		{
			if(!isTrain) {
				appView = jCas.getView(SemEval2015Constants.APP_VIEW);
				getTokenCounts(appView, unigram_counts, bigram_counts);
			} else {
				goldView = jCas.getView(SemEval2015Constants.GOLD_VIEW);
				getTokenCounts(goldView, unigram_counts, bigram_counts);
			}
		} catch (CASException e)
		{
			e.printStackTrace();
			System.exit(0);
		}
		for(DocumentID di : JCasUtil.select(jCas, DocumentID.class)) {
			docid = di.getDocumentID();
		}
		if(isConstruction) {
			//Writes to our Mutual Information Database (HSQLDB)
			Connection _dbConnection = null;
			try {
				_dbConnection = DriverManager.getConnection(default_db_url);
				_dbConnection.setTransactionIsolation(Connection.TRANSACTION_SERIALIZABLE);
				PreparedStatement p_insert = _dbConnection.prepareStatement(
						"INSERT INTO NLP_UNIGRAM (MI_TOKEN,OBSERVED) VALUES (?,?)");
				PreparedStatement p_update = _dbConnection.prepareStatement(
						"UPDATE NLP_UNIGRAM SET OBSERVED=? WHERE MI_TOKEN= ? ");
				for(String qkey : unigram_counts.keySet()){
					Integer count = unigram_counts.get(qkey);
					Integer old_count = null;
					try {
						_dbConnection.setTransactionIsolation(Connection.TRANSACTION_SERIALIZABLE);
						old_count = getOneTokenCount(_dbConnection,"NLP_UNIGRAM",qkey);
						//pst.setString(1, docid);
						_dbConnection.setTransactionIsolation(Connection.TRANSACTION_SERIALIZABLE);
						if(old_count==0) {
							p_insert.setString(1,qkey);
							p_insert.setInt(2, count.intValue());
							p_insert.executeUpdate();
						} else {
							p_update.setInt(1, count.intValue()+old_count.intValue());
							p_update.setString(2,qkey);
							p_update.executeUpdate();
						}
					} catch (java.sql.SQLIntegrityConstraintViolationException ok) {
						System.out.println("Problem with "+qkey+" with count"+count);
						if(old_count!=null) System.out.println("The old count was:"+old_count);
						else System.out.println("null");
						ok.getCause();}

				}
				p_insert = _dbConnection.prepareStatement(
						"INSERT INTO NLP_BIGRAM (FIRST_TOKEN,SECOND_TOKEN,"+
						"OBSERVED) VALUES (?,?,?)");
				//pst.setString(1, docid);
				p_update = _dbConnection.prepareStatement(
						"UPDATE NLP_BIGRAM SET OBSERVED = ? WHERE FIRST_TOKEN=? AND"+
						" SECOND_TOKEN=? ");
				for(String first : bigram_counts.keySet()){
					Hashtable<String,Integer> thecounts = bigram_counts.get(first);
					for(String second : thecounts.keySet()){
						try {
							_dbConnection.setTransactionIsolation(Connection.TRANSACTION_SERIALIZABLE);
							Integer old_count = getBiTokenCount(_dbConnection,"NLP_BIGRAM",first,second);
							Integer observed = thecounts.get(second);
							if(observed==null) System.out.println("Observed was null for "+first+" and "+second);
							_dbConnection.setTransactionIsolation(Connection.TRANSACTION_SERIALIZABLE);
							if(old_count==0){
								p_insert.setString(1,first);
								p_insert.setString(2,second);
								p_insert.setInt(3, observed);
								p_insert.executeUpdate();
							} else {
								p_update.setInt(1, observed);
								p_update.setString(2,first);
								p_update.setString(3,second);
								p_update.executeUpdate();
							}
						} catch (java.sql.SQLIntegrityConstraintViolationException ok) {
							ok.printStackTrace();}
					}
				}
				_dbConnection.close();
			} catch (Exception e)
			{
				this.getContext().getLogger().log(Level.SEVERE,"Failed to update database");
				e.printStackTrace();
			}

		} else {
			//Not making database, create the required MutualInformation Annotations
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
				Integer all_unigram_count = getTokenTotals(_dbConnection, "NLP_UNIGRAM");
				Integer all_bigram_count = getTokenTotals(_dbConnection, "NLP_BIGRAM");
				//if(VERBOSE)System.out.println("All Unigram counts"+all_unigram_count);
				//if(VERBOSE)System.out.println("All Bigram counts"+all_bigram_count);
				for(String first : bigram_counts.keySet()){
					Hashtable<String,Integer> thecounts = bigram_counts.get(first);
					Integer first_count = getOneTokenCount(_dbConnection, "NLP_UNIGRAM", first);
					for(String second : thecounts.keySet()){
						Integer second_count = getOneTokenCount(_dbConnection, "NLP_UNIGRAM", second);
						Integer jointcount = getBiTokenCount(_dbConnection, "NLP_BIGRAM", first, second);
						TokenMutualInformation tkmi = null;
						if(isTrain) tkmi = new TokenMutualInformation(goldView);
						else tkmi = new TokenMutualInformation(appView);
						tkmi.setAnnotation_x(first);
						tkmi.setAnnotation_y(second);
						tkmi.setCount_x(first_count);
						tkmi.setCount_y(second_count);
						tkmi.setCount_xy(jointcount);
						double mi = Math.log((((double)first_count/all_unigram_count)*((double)second_count/all_unigram_count))/((double)jointcount/all_bigram_count));
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
	private void getTokenCounts(JCas jcas,
			Hashtable<String, Integer> unigram_counts,
			Hashtable<String, Hashtable<String, Integer>> bigram_counts
			) {
		String cur_cover=null,prev_cover="___DOCUMENT_START___";
		for(BaseToken tok : JCasUtil.select(jcas, BaseToken.class)) {	
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
				else bcounts.put(cur_cover, new Integer(bcount+1));
			}
			prev_cover = cur_cover;
		}
	}


	private int getTokenTotals(Connection conn, String table_name){
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


	private int getOneTokenCount(Connection conn, String table_name, String token){
		Integer count = 0;
		String ucountsql = null;
		String clean = "";
		try {
			clean = token.replaceAll("'","''" );
			ucountsql = "SELECT OBSERVED FROM "+table_name+
					" WHERE MI_TOKEN=\'"+clean+"\'";
			Statement st = conn.createStatement();
			//System.out.println(ucountsql);
			ResultSet resultset = (ResultSet) st.executeQuery(ucountsql);
			while(resultset.next()){
				count = resultset.getInt(1);
			}
			resultset.close();
		} catch (Exception e) { System.out.println(clean+" ----TOKEN____"+token); System.out.println(ucountsql); e.printStackTrace(); }
		return count;
	}




	private int getBiTokenCount(Connection conn, String table_name, String first, String second){
		Integer count = 0;
		String f1="",s2="";
		try {
			f1 = first.replaceAll("'","''" );
			s2 = second.replaceAll("'","''" );
			String ucountsql = "SELECT OBSERVED FROM "+table_name+
					" WHERE FIRST_TOKEN=\'"+f1+"\' AND SECOND_TOKEN=\'"+s2+"\'";
			Statement st = conn.createStatement();
			ResultSet resultset = (ResultSet) st.executeQuery(ucountsql);
			while(resultset.next()){
				count = resultset.getInt(1);
			}
			resultset.close();
		} catch (Exception e) { 
			System.out.println(first+"--------"+second+"     COUNT"+count);
			System.out.println(f1+"---+----"+s2);
			e.printStackTrace(); }
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
			//st.execute(unigram_index);
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
