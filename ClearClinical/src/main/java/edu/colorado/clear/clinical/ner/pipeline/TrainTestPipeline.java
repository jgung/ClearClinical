package edu.colorado.clear.clinical.ner.pipeline;

import edu.colorado.clear.clinical.ner.annotators.CopySentencesAndTokensAnnotator;

import com.google.common.base.Function;

import edu.colorado.clear.clinical.ner.annotators.*;
import edu.colorado.clear.clinical.ner.util.SemEval2015CUIAnnotationStatistics;
import edu.colorado.clear.clinical.ner.util.SemEval2015CollectionReader;
import edu.colorado.clear.clinical.ner.util.SemEval2015Constants;
import edu.colorado.clear.clinical.ner.util.SemEval2015GoldAnnotator;

import org.apache.commons.io.FileUtils;
import org.apache.log4j.Level;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;
import org.apache.uima.collection.CollectionReader;
import org.apache.uima.jcas.JCas;
import org.cleartk.classifier.CleartkAnnotator;
import org.cleartk.classifier.CleartkSequenceAnnotator;
import org.cleartk.classifier.crfsuite.CRFSuiteStringOutcomeDataWriter;
import org.cleartk.classifier.jar.*;
import org.cleartk.classifier.libsvm.LIBSVMBooleanOutcomeDataWriter;
import org.cleartk.eval.AnnotationStatistics;
import org.cleartk.semeval2015.type.DisorderSpan;
import org.uimafit.component.ViewCreatorAnnotator;
import org.uimafit.component.ViewTextCopierAnnotator;
import org.uimafit.factory.AggregateBuilder;
import org.uimafit.factory.AnalysisEngineFactory;
import org.uimafit.factory.CollectionReaderFactory;
import org.uimafit.pipeline.JCasIterable;
import org.uimafit.pipeline.SimplePipeline;
import org.uimafit.util.JCasUtil;

import java.io.File;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * This should correspond to Task 1 for Semeval 2015
 *
 */
public class TrainTestPipeline
{

	public static String resourceDirPath = "src/main/resources/";

	public static String semeval_train = resourceDirPath + "semeval-2015-task-14/subtasks-a-b/data/train";
	public static String semeval_devel = resourceDirPath + "semeval-2015-task-14/subtasks-a-b/data/devel";
	public static String minidev = resourceDirPath + "semeval-2015-task-14/subtasks-a-b/data/minidev";
	public static String abbrFile = resourceDirPath + "data/abbr.txt";
	public static String cuiMapFile = resourceDirPath + "data/cuiMap.txt";

	public static String crfModels = "target/models/crf";
	public static String relModels = "target/models/rel";

	public static boolean SPAN_RESOLUTION = true;
	public static boolean VERBOSE = true;
	public static boolean USE_YTEX = false;
	public static boolean SKIP_TRAINING = false;

	public static void main(String... args) throws Throwable
	{
		String[] trainExtension = {SemEval2015CollectionReader.PIPE_SUFFIX};

		File crfModelDir = new File(crfModels);
		File relModelDir = new File(relModels);

		File trainDir = new File(semeval_train);
		//File testDir = new File(semeval_devel);
		File testDir = new File(minidev);

		Collection<File> trainFiles = FileUtils.listFiles(trainDir,
				trainExtension, true);
		Collection<File> testFiles = FileUtils.listFiles(testDir,
				trainExtension, true);
		
		for(String arg:args){
			if(arg.equalsIgnoreCase("-ytex")) {
				USE_YTEX=true;
				System.out.println("Using YTEX to Disambiguate");
			}
			if(arg.equalsIgnoreCase("-skiptraining")) {
				System.out.println("Skipping Training");
				SKIP_TRAINING=true;
			}
		}

		if(!SKIP_TRAINING) train(trainFiles, crfModelDir, relModelDir);
		AnnotationStatistics<String> stats = test(testFiles, crfModelDir, relModelDir);
		System.out.println("Spanning stats");
		System.out.println(stats);
	}

	public static void train(Collection<File> files, File crfModelDir, File relModelDir) throws Throwable
	{
		CollectionReader reader = CollectionReaderFactory.createCollectionReader(
				SemEval2015CollectionReader.class,
				SemEval2015CollectionReader.PARAM_FILES,
				files);

		AggregateBuilder builder = new AggregateBuilder();
		builder.add(ApplicationPipeline.getClearDefaultPipeline(USE_YTEX));
		builder.add(AnalysisEngineFactory.createPrimitiveDescription(SemEval2015GoldAnnotator.class,
				SemEval2015GoldAnnotator.PARAM_TRAINING,
				true,
				SemEval2015GoldAnnotator.PARAM_CUI_MAP,
				TrainTestPipeline.cuiMapFile));
		builder.add(AnalysisEngineFactory.createPrimitiveDescription(AbbreviationAnnotator.class,
				AbbreviationAnnotator.PARAM_FILE,
				new File(abbrFile)));

		builder.add(AnalysisEngineFactory.createPrimitiveDescription(
				DisorderSpanAnnotator.class,
				CleartkSequenceAnnotator.PARAM_IS_TRAINING,
				true,
				DefaultSequenceDataWriterFactory.PARAM_OUTPUT_DIRECTORY,
				crfModelDir.getPath(),
				DefaultSequenceDataWriterFactory.PARAM_DATA_WRITER_CLASS_NAME,
				CRFSuiteStringOutcomeDataWriter.class));

		if (SPAN_RESOLUTION)
		{
			builder.add(AnalysisEngineFactory.createPrimitiveDescription(
					DisjointSpanAnnotator.class,
					DisjointSpanAnnotator.PARAM_IS_TRAINING,
					true,
					DirectoryDataWriterFactory.PARAM_OUTPUT_DIRECTORY,
					relModelDir.getPath(),
					DefaultDataWriterFactory.PARAM_DATA_WRITER_CLASS_NAME,
					LIBSVMBooleanOutcomeDataWriter.class));
		}

		if (!VERBOSE)
		{ /* turn off logging */
			@SuppressWarnings("unchecked")
			List<Logger> loggers = Collections.<Logger>list(LogManager.getCurrentLoggers());
			loggers.add(LogManager.getRootLogger());
			for (Logger logger : loggers)
				logger.setLevel(Level.OFF);
		}

		SimplePipeline.runPipeline(reader, builder.createAggregate());

		Train.main(crfModelDir);
		if (SPAN_RESOLUTION) Train.main(relModelDir, "-c", "10", "-s", "0", "-t", "0");

		SemEval2015GoldAnnotator.writeMapToFile(SemEval2015GoldAnnotator.stringCUIMap, new File(cuiMapFile));
	}

	public static AnnotationStatistics<String> test(Collection<File> files, File crfDir, File relDir) throws Throwable
	{

		CollectionReader reader = CollectionReaderFactory.createCollectionReader(
				SemEval2015CollectionReader.class,
				SemEval2015CollectionReader.PARAM_FILES,
				files);

		AggregateBuilder builder = new AggregateBuilder();
		builder.add(ApplicationPipeline.getClearDefaultPipeline(USE_YTEX));
		builder.add(AnalysisEngineFactory.createPrimitiveDescription(SemEval2015GoldAnnotator.class,
				SemEval2015GoldAnnotator.PARAM_TRAINING,
				false,
				SemEval2015GoldAnnotator.PARAM_CUI_MAP,
				TrainTestPipeline.cuiMapFile));

		builder.add(AnalysisEngineFactory.createPrimitiveDescription(DocIDAnnotator.class,
				DocIDAnnotator.PARAM_CUI_MAP_PATH,
				cuiMapFile));
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

		builder.add(AnalysisEngineFactory.createPrimitiveDescription(
				DisorderSpanAnnotator.class,
				CleartkAnnotator.PARAM_IS_TRAINING,
				false,
				GenericJarClassifierFactory.PARAM_CLASSIFIER_JAR_PATH,
				new File(crfDir, "model.jar")));
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

		AnnotationStatistics<String> stats = new AnnotationStatistics<>();
		SemEval2015CUIAnnotationStatistics cuistats = new SemEval2015CUIAnnotationStatistics();
		Function<DisorderSpan, ?> annotationToSpan = AnnotationStatistics.annotationToSpan();
		Function<DisorderSpan, String> annotationToOutcome = AnnotationStatistics.annotationToFeatureValue("chunk");

		if (!VERBOSE) suppressLogging();

		for (JCas jCas : new JCasIterable(reader, builder.createAggregate()))
		{
			JCas goldView = jCas.getView(SemEval2015Constants.GOLD_VIEW);
			JCas systemView = jCas.getView(SemEval2015Constants.APP_VIEW);

			Collection<DisorderSpan> goldSpans = JCasUtil.select(goldView, DisorderSpan.class);
			Collection<DisorderSpan> systemSpans = JCasUtil.select(systemView, DisorderSpan.class);
			Collection<DisorderSpan> goldCUIMappings = JCasUtil.select(goldView, DisorderSpan.class);
			Collection<DisorderSpan> systemCUIMappings = JCasUtil.select(systemView, DisorderSpan.class);
		
			stats.add(goldSpans, systemSpans, annotationToSpan, annotationToOutcome);
			cuistats.add(goldCUIMappings,systemCUIMappings);
		}

		return stats;

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
