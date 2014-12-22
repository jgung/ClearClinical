package edu.colorado.clear.clinical.ner.pipeline;


import edu.colorado.clear.clinical.ner.annotators.*;
import edu.colorado.clear.clinical.ner.util.SemEval2015Constants;
import edu.colorado.clear.clinical.ner.util.SemEval2015CorpusCollectionReader;
import edu.uab.ccts.nlp.uima.annotator.MutualInformationAnnotator;

import org.apache.ctakes.clinicalpipeline.ClinicalPipelineFactory;
import org.apache.log4j.Level;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;
import org.apache.uima.UIMAFramework;
import org.apache.uima.analysis_engine.AnalysisEngineDescription;
import org.apache.uima.collection.CollectionProcessingEngine;
import org.apache.uima.collection.CollectionReaderDescription;
import org.apache.uima.collection.metadata.CpeDescription;
import org.hsqldb.server.Server;
import org.uimafit.component.ViewCreatorAnnotator;
import org.uimafit.component.ViewTextCopierAnnotator;
import org.uimafit.factory.AggregateBuilder;
import org.uimafit.factory.AnalysisEngineFactory;
//import org.uimafit.factory.CollectionReaderFactory;
import org.apache.uima.fit.cpe.*;
import org.apache.uima.fit.factory.CollectionReaderFactory;

import java.util.Collections;
import java.util.List;

/**
 * This constructs a MutualInformationCorpus on unsupervised data
 * Should be smarter and not just use adjacent BaseTokens
 */
public class MutualInformationPipeline
{

	public static String resourceDirPath = "src/main/resources/";
	public static String unsupervised_corpus_root_path = 
	"/home/ozborn/semeval-2014-unlabeled-mimic-notes.v1";

	public static boolean VERBOSE = false;

	public static void main(String... args) throws Throwable
	{
		
		harvestMutualInformation();
		System.out.println("Complete");
	}

	public static void harvestMutualInformation() throws Throwable
	{

		AggregateBuilder builder = new AggregateBuilder();
		builder.add(ClinicalPipelineFactory.getTokenProcessingPipeline());
	
		/* Create new views, and copy annotations between views for evaluation */
		builder.add(AnalysisEngineFactory.createPrimitiveDescription(
				ViewCreatorAnnotator.class,
				ViewCreatorAnnotator.PARAM_VIEW_NAME,
				SemEval2015Constants.APP_VIEW));
		builder.add(AnalysisEngineFactory.createPrimitiveDescription(
				ViewTextCopierAnnotator.class,
				ViewTextCopierAnnotator.PARAM_SOURCE_VIEW_NAME,
				SemEval2015Constants.GOLD_VIEW,
				ViewTextCopierAnnotator.PARAM_DESTINATION_VIEW_NAME,
				SemEval2015Constants.APP_VIEW));
		builder.add(AnalysisEngineFactory.createPrimitiveDescription(
				CopySentencesAndTokensAnnotator.class));

		builder.add(AnalysisEngineFactory.createPrimitiveDescription(MutualInformationAnnotator.class,
				MutualInformationAnnotator.PARAM_MI_DATABASE_URL,
				MutualInformationAnnotator.default_db_url,
				MutualInformationAnnotator.PARAM_MI_DATABASE_USER,
				MutualInformationAnnotator.default_db_user,
				MutualInformationAnnotator.PARAM_MI_DATABASE_PASSWORD,
				MutualInformationAnnotator.default_db_password,
				MutualInformationAnnotator.PARAM_IS_TRAINING,
				true));

		if (!VERBOSE) suppressLogging();
		Server hsqlServer = new Server();
		hsqlServer.setDatabaseName(0, MutualInformationAnnotator.default_db_name);
		hsqlServer.setDatabasePath(0, "file:"+MutualInformationAnnotator.default_db_path);
		hsqlServer.start();
		MutualInformationAnnotator.initialize_database();
		AnalysisEngineDescription aed = builder.createAggregateDescription();
		CpeBuilder cbuild = new CpeBuilder();
		cbuild.setAnalysisEngine(aed);
		CollectionReaderDescription crd  = CollectionReaderFactory.createReaderDescription(
				SemEval2015CorpusCollectionReader.class,
				SemEval2015CorpusCollectionReader.PARAM_FILES,
				unsupervised_corpus_root_path);
		cbuild.setReader(crd);
		cbuild.setMaxProcessingUnitThreadCount(8);
		CpeDescription midesc = cbuild.getCpeDescription();
		//midesc.setInputQueueSize(8);
		//midesc.setOutputQueueSize(8);
		//midesc.setProcessingUnitThreadCount(8);
		CollectionProcessingEngine mifast = UIMAFramework.produceCollectionProcessingEngine(midesc);
		mifast.process();
		
		hsqlServer.stop();
	}

	public static void suppressLogging()
	{
		@SuppressWarnings("unchecked")
		List<Logger> loggers = Collections.<Logger>list(LogManager.getCurrentLoggers());
		loggers.add(LogManager.getRootLogger());
		for (Logger logger : loggers)
			logger.setLevel(Level.OFF);
	}

}
