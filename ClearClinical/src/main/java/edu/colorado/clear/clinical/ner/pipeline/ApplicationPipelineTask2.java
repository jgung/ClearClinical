package edu.colorado.clear.clinical.ner.pipeline;

import edu.colorado.clear.clinical.ner.annotators.*;
import edu.colorado.clear.clinical.ner.util.SemEval2015CollectionReader;
import edu.colorado.clear.clinical.ner.util.SemEval2015Constants;
import edu.colorado.clear.clinical.ner.util.SemEval2015TaskCGoldAnnotator;
import edu.uab.ccts.nlp.uima.annotator.MutualInformationAnnotator;
import edu.uab.ccts.nlp.uima.annotator.SemEval2015Task2Consumer;

import org.apache.commons.io.FileUtils;
import org.apache.ctakes.clinicalpipeline.ClinicalPipelineFactory;
import org.apache.log4j.Level;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;
import org.apache.uima.collection.CollectionReader;
import org.cleartk.classifier.CleartkAnnotator;
import org.cleartk.classifier.CleartkSequenceAnnotator;
import org.cleartk.classifier.crfsuite.CRFSuiteStringOutcomeDataWriter;
import org.cleartk.classifier.jar.*;
import org.cleartk.classifier.liblinear.LIBLINEARBooleanOutcomeDataWriter;
import org.cleartk.classifier.liblinear.LIBLINEARStringOutcomeDataWriter;
import org.cleartk.util.ae.UriToDocumentTextAnnotator;
import org.cleartk.util.cr.UriCollectionReader;
import org.hsqldb.server.Server;
import org.uimafit.component.ViewCreatorAnnotator;
import org.uimafit.component.ViewTextCopierAnnotator;
import org.uimafit.factory.AggregateBuilder;
import org.uimafit.factory.AnalysisEngineFactory;
import org.uimafit.factory.CollectionReaderFactory;
import org.uimafit.pipeline.SimplePipeline;

import java.io.File;
import java.util.Collection;
import java.util.Collections;
import java.util.List;


/**
 * This should correspond to Task 2a
 *
 */
public class ApplicationPipelineTask2
{

	
	public static String resourceDirPath = "src/main/resources/";

	public static String semeval_train_c = resourceDirPath + "semeval-2015-task-14/data/train";
	public static String semeval_test_c = resourceDirPath + "semeval-2015-task-14/data/devel";

	public static String abbrFile = resourceDirPath + "data/abbr.txt";
	public static String cuiMapFile = resourceDirPath + "data/cuiMap.txt";

	public static String crfModels = "target/modelsFull/crf";
	public static String attributeModels = "target/modelsFull/attribute";
	public static String relModels = "target/modelsFull/rel";
	public static String attRelModels = "target/modelsFull/attRel";
	public static String attNormModels = "target/modelsFull/attNorm";

	public static boolean SPAN_RESOLUTION = true;
	public static boolean VERBOSE = true;
	public static boolean USE_YTEX = false;
	public static boolean USE_MI = false;
	public static boolean SKIP_TRAINING = false;

	public static void main(String... args) throws Throwable
	{
		String[] trainExtension = {SemEval2015CollectionReader.PIPE_SUFFIX};
		String[] testExtension = {SemEval2015CollectionReader.TEXT_SUFFIX};

		File crfModelDir = new File(crfModels);
		File relModelDir = new File(relModels);
		File attModelDir = new File(attributeModels);
		File attRelsDir = new File(attRelModels);
		File attNormDir = new File(attNormModels);

		File trainDir = new File(semeval_train_c);
		File testDir = new File(semeval_test_c);

		Collection<File> trainFiles = FileUtils.listFiles(trainDir,
				trainExtension, true);
		Collection<File> testFiles = FileUtils.listFiles(testDir,
				testExtension, true);
		
		for(String arg:args){
			if(arg.equalsIgnoreCase("-ytex")) USE_YTEX=true;
			if(arg.equalsIgnoreCase("-mi")) USE_MI=true;
			if(arg.equalsIgnoreCase("-skiptraining")) SKIP_TRAINING=true;
		}

		if (!SKIP_TRAINING) train(trainFiles, crfModelDir, relModelDir, attModelDir, attRelsDir, attNormDir);
		test(testFiles, crfModelDir, relModelDir, attModelDir, attRelsDir, attNormDir);
	}

	public static void train(Collection<File> files, File ddModelDir, File relModelDir,
	                         File attModelDir, File attRelsDir, File attNormDir) throws Throwable
	{
		CollectionReader reader = CollectionReaderFactory.createCollectionReader(
				SemEval2015CollectionReader.class,
				SemEval2015CollectionReader.PARAM_FILES,
				files);

		AggregateBuilder builder = new AggregateBuilder();
		builder.add(ClinicalPipelineFactory.getTokenProcessingPipeline());

		builder.add(AnalysisEngineFactory.createPrimitiveDescription(SemEval2015TaskCGoldAnnotator.class,
				SemEval2015TaskCGoldAnnotator.PARAM_TRAINING,
				true,
				SemEval2015TaskCGoldAnnotator.PARAM_CUI_MAP,
				TrainTestPipeline.cuiMapFile));
		builder.add(AnalysisEngineFactory.createPrimitiveDescription(AbbreviationAnnotator.class,
				AbbreviationAnnotator.PARAM_FILE,
				new File(abbrFile)));

		builder.add(AnalysisEngineFactory.createPrimitiveDescription(
				DisorderSpanAnnotator.class,
				CleartkSequenceAnnotator.PARAM_IS_TRAINING,
				true,
				DefaultSequenceDataWriterFactory.PARAM_OUTPUT_DIRECTORY,
				ddModelDir.getPath(),
				DefaultSequenceDataWriterFactory.PARAM_DATA_WRITER_CLASS_NAME,
				CRFSuiteStringOutcomeDataWriter.class));

		builder.add(AnalysisEngineFactory.createPrimitiveDescription(
				AttributeAnnotator.class,
				CleartkSequenceAnnotator.PARAM_IS_TRAINING,
				true,
				DefaultSequenceDataWriterFactory.PARAM_OUTPUT_DIRECTORY,
				attModelDir.getPath(),
				DefaultSequenceDataWriterFactory.PARAM_DATA_WRITER_CLASS_NAME,
				CRFSuiteStringOutcomeDataWriter.class));

		if(USE_MI) {
			builder.add(AnalysisEngineFactory.createPrimitiveDescription(MutualInformationAnnotator.class,
					MutualInformationAnnotator.PARAM_MI_DATABASE_URL,
					MutualInformationAnnotator.default_db_url,
					MutualInformationAnnotator.PARAM_MI_DATABASE_USER,
					MutualInformationAnnotator.default_db_user,
					MutualInformationAnnotator.PARAM_MI_DATABASE_PASSWORD,
					MutualInformationAnnotator.default_db_url,
					MutualInformationAnnotator.PARAM_IS_TRAINING,
					true));
		}

		if (SPAN_RESOLUTION)
		{
			builder.add(AnalysisEngineFactory.createPrimitiveDescription(
					DisjointSpanAnnotator.class,
					DisjointSpanAnnotator.PARAM_IS_TRAINING,
					true,
					DirectoryDataWriterFactory.PARAM_OUTPUT_DIRECTORY,
					relModelDir.getPath(),
					DefaultDataWriterFactory.PARAM_DATA_WRITER_CLASS_NAME,
					LIBLINEARBooleanOutcomeDataWriter.class));
		}

		builder.add(AnalysisEngineFactory.createPrimitiveDescription(
				AttributeRelationAnnotator.class,
				AttributeRelationAnnotator.PARAM_IS_TRAINING,
				true,
				DefaultDataWriterFactory.PARAM_OUTPUT_DIRECTORY,
				attRelsDir.getPath(),
				DefaultDataWriterFactory.PARAM_DATA_WRITER_CLASS_NAME,
				LIBLINEARStringOutcomeDataWriter.class));


		builder.add(AnalysisEngineFactory.createPrimitiveDescription(
				AttributeNormalizer.class,
				AttributeNormalizer.PARAM_IS_TRAINING,
				true,
				DefaultDataWriterFactory.PARAM_OUTPUT_DIRECTORY,
				attNormDir.getPath(),
				DefaultDataWriterFactory.PARAM_DATA_WRITER_CLASS_NAME,
				LIBLINEARStringOutcomeDataWriter.class));

		if (!VERBOSE)
		{ /* turn off logging */
			@SuppressWarnings("unchecked")
			List<Logger> loggers = Collections.<Logger>list(LogManager.getCurrentLoggers());
			loggers.add(LogManager.getRootLogger());
			for (Logger logger : loggers)
				logger.setLevel(Level.OFF);
		}

		SimplePipeline.runPipeline(reader, builder.createAggregate());

		Train.main(ddModelDir);
		Train.main(attModelDir);
		if (SPAN_RESOLUTION) Train.main(relModelDir, "-c", "10", "-s", "1");
		Train.main(attRelsDir, "-c", "10", "-s", "1");
		Train.main(attNormDir, "-c", "10", "-s", "1");

		SemEval2015TaskCGoldAnnotator.writeMapToFile(SemEval2015TaskCGoldAnnotator.stringCUIMap, new File(cuiMapFile));
	}

	public static void test(Collection<File> files, File ddDir,
	                          File relDir, File attDir, File attRelsDir, File attNormDir) throws Throwable
	{

		CollectionReader reader = UriCollectionReader.getCollectionReaderFromFiles(files);

		AggregateBuilder builder = new AggregateBuilder();
		builder.add(UriToDocumentTextAnnotator.getDescription());
		builder.add(ClinicalPipelineFactory.getTokenProcessingPipeline());
		builder.add(AnalysisEngineFactory.createPrimitiveDescription(AbbreviationAnnotator.class,
				AbbreviationAnnotator.PARAM_FILE,
				new File(abbrFile)));
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
		builder.add(AnalysisEngineFactory.createPrimitiveDescription(DocIDAnnotator.class,
				DocIDAnnotator.PARAM_CUI_MAP_PATH,
				cuiMapFile));
		builder.add(AnalysisEngineFactory.createPrimitiveDescription(
				DisorderSpanAnnotator.class,
				CleartkAnnotator.PARAM_IS_TRAINING,
				false,
				GenericJarClassifierFactory.PARAM_CLASSIFIER_JAR_PATH,
				new File(ddDir, "model.jar")));

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
					SpanPostProcessorAnnotator.PARAM_CUI_FILE_PATH,
					TrainTestPipeline.resourceDirPath + "cuis"));
		}

		builder.add(AnalysisEngineFactory.createPrimitiveDescription(
				AttributeRelationAnnotator.class,
				CleartkAnnotator.PARAM_IS_TRAINING,
				false,
				GenericJarClassifierFactory.PARAM_CLASSIFIER_JAR_PATH,
				new File(attRelsDir, "model.jar").getPath()));

		builder.add(AnalysisEngineFactory.createPrimitiveDescription(
				AttributeNormalizer.class,
				AttributeNormalizer.PARAM_IS_TRAINING,
				false,
				GenericJarClassifierFactory.PARAM_CLASSIFIER_JAR_PATH,
				new File(attNormDir, "model.jar").getPath()));

		if(USE_MI) {
			builder.add(AnalysisEngineFactory.createPrimitiveDescription(MutualInformationAnnotator.class,
					MutualInformationAnnotator.PARAM_MI_DATABASE_URL,
					MutualInformationAnnotator.default_db_url,
					MutualInformationAnnotator.PARAM_MI_DATABASE_USER,
					MutualInformationAnnotator.default_db_user,
					MutualInformationAnnotator.PARAM_MI_DATABASE_PASSWORD,
					MutualInformationAnnotator.default_db_password,
					MutualInformationAnnotator.PARAM_IS_TRAINING,
					false));
		}

		builder.add(AnalysisEngineFactory.createPrimitiveDescription(SemEval2015Task2Consumer.class,
				SemEval2015Task2Consumer.PARAM_OUTPUT_DIRECTORY,
				"template_results_test"));

		if (!VERBOSE) suppressLogging();

		Server hsqlServer = null;
		if(USE_MI){
			hsqlServer = new Server();
			hsqlServer.setDatabaseName(0, MutualInformationAnnotator.default_db_name);
			hsqlServer.setDatabasePath(0, "file:"+MutualInformationAnnotator.default_db_path);
			hsqlServer.start();
		}

		if (!VERBOSE) suppressLogging();

		SimplePipeline.runPipeline(reader, builder.createAggregate());

		if(hsqlServer!=null && USE_MI) hsqlServer.stop();



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
