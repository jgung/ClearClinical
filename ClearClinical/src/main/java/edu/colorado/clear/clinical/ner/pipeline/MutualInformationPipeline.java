package edu.colorado.clear.clinical.ner.pipeline;


import edu.colorado.clear.clinical.ner.annotators.*;
import edu.colorado.clear.clinical.ner.util.SemEval2015Constants;
import edu.colorado.clear.clinical.ner.util.SemEval2015CorpusCollectionReader;
import edu.colorado.clear.clinical.ner.util.SemEval2015TaskCGoldAnnotator;
import edu.uab.ccts.nlp.uima.annotator.MutualInformationAnnotator;
import edu.uab.ccts.nlp.uima.annotator.SemEval2015Task2Consumer;

import org.apache.commons.io.FileUtils;
import org.apache.ctakes.clinicalpipeline.ClinicalPipelineFactory;
import org.apache.ctakes.typesystem.type.syntax.BaseToken;
import org.apache.log4j.Level;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;
import org.apache.uima.collection.CollectionReader;
import org.apache.uima.jcas.JCas;
import org.cleartk.semeval2015.type.DiseaseDisorderAttribute;
import org.hsqldb.server.Server;
import org.uimafit.component.ViewCreatorAnnotator;
import org.uimafit.component.ViewTextCopierAnnotator;
import org.uimafit.factory.AggregateBuilder;
import org.uimafit.factory.AnalysisEngineFactory;
import org.uimafit.factory.CollectionReaderFactory;
import org.uimafit.pipeline.JCasIterable;
import org.uimafit.util.JCasUtil;

import java.io.File;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * This should construct a MutualInformationCorpus on unsupervised data
 *
 */
public class MutualInformationPipeline
{

	public static String resourceDirPath = "src/main/resources/";
	public static String unsupervised_corpus_root_path = 
	"/home/ozborn/semeval-2014-unlabeled-mimic-notes.v1";

	public static boolean VERBOSE = false;

	public static void main(String... args) throws Throwable
	{
		String[] trainExtension = {SemEval2015CorpusCollectionReader.TEXT_SUFFIX};
		
		Collection<File> trainFiles = FileUtils.listFiles(
				new File(unsupervised_corpus_root_path),
				trainExtension, true);

		harvestMutualInformation(trainFiles);
		System.out.println("Complete");
	}

	public static void harvestMutualInformation(Collection<File> files) throws Throwable
	{

		CollectionReader reader = CollectionReaderFactory.createCollectionReader(
				SemEval2015CorpusCollectionReader.class,
				SemEval2015CorpusCollectionReader.PARAM_FILES,
				files);

		AggregateBuilder builder = new AggregateBuilder();
		builder.add(ClinicalPipelineFactory.getTokenProcessingPipeline());
		/*
		builder.add(AnalysisEngineFactory.createPrimitiveDescription(SemEval2015TaskCGoldAnnotator.class,
				SemEval2015TaskCGoldAnnotator.PARAM_TRAINING,
				false,
				SemEval2015TaskCGoldAnnotator.PARAM_CUI_MAP,
				TrainTestPipeline.cuiMapFile));
		builder.add(AnalysisEngineFactory.createPrimitiveDescription(DocIDAnnotator.class,
				DocIDAnnotator.PARAM_CUI_MAP_PATH,
				cuiMapFile));
		builder.add(AnalysisEngineFactory.createPrimitiveDescription(AbbreviationAnnotator.class,
				AbbreviationAnnotator.PARAM_FILE,
				new File(abbrFile)));
		*/

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

//		builder.add(AnalysisEngineFactory.createPrimitiveDescription(
//				DisorderSpanAnnotator.class,
//				CleartkAnnotator.PARAM_IS_TRAINING,
//				false,
//				GenericJarClassifierFactory.PARAM_CLASSIFIER_JAR_PATH,
//				new File(ddDir, "model.jar")));

		/*
		builder.add(AnalysisEngineFactory.createPrimitiveDescription(
				AttributeAnnotator.class,
				CleartkAnnotator.PARAM_IS_TRAINING,
				false,
				GenericJarClassifierFactory.PARAM_CLASSIFIER_JAR_PATH,
				new File(attDir, "model.jar")));

		if (SPAN_RESOLUTION)
		{
			builder.add(AnalysisEngineFactory.createPrimitiveDescription(
					DisjointSpanAnnotator.class,
					CleartkAnnotator.PARAM_IS_TRAINING,
					false,
					GenericJarClassifierFactory.PARAM_CLASSIFIER_JAR_PATH,
					new File(relDir, "model.jar").getPath()));
			builder.add(AnalysisEngineFactory.createPrimitiveDescription(
					SpanPostProcessorAnnotator.class,
					SpanPostProcessorAnnotator.PARAM_OUT_FILE_PATH,
					TrainTestPipeline.resourceDirPath + "results",
					SpanPostProcessorAnnotator.PARAM_CUI_FILE_PATH,
					TrainTestPipeline.resourceDirPath + "cuis"));
		}
		*/

		builder.add(AnalysisEngineFactory.createPrimitiveDescription(MutualInformationAnnotator.class,
				MutualInformationAnnotator.PARAM_MI_DATABASE_URL,
				MutualInformationAnnotator.default_db_url,
				MutualInformationAnnotator.PARAM_MI_DATABASE_USER,
				MutualInformationAnnotator.default_db_user,
				MutualInformationAnnotator.PARAM_MI_DATABASE_PASSWORD,
				MutualInformationAnnotator.default_db_url,
				MutualInformationAnnotator.PARAM_IS_TRAINING,
				true));

		if (!VERBOSE) suppressLogging();
		Server hsqlServer = new Server();
		hsqlServer.setDatabaseName(0, MutualInformationAnnotator.default_db_name);
		hsqlServer.setDatabasePath(0, "file:"+MutualInformationAnnotator.default_db_path);
		hsqlServer.start();
		MutualInformationAnnotator.initialize_database();
		for (JCas jCas : new JCasIterable(reader, builder.createAggregate()))
		{
			/*
			for(BaseToken tok : JCasUtil.select(jCas, BaseToken.class)) {
				System.out.println("We do have tokens...");
			}
			*/
			//System.out.println("Processed "+jCas.getDocumentText());
		}
		
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
