package edu.colorado.clear.clinical.ner.pipeline;

import com.google.common.base.Function;
import edu.colorado.clear.clinical.ner.annotators.*;
import edu.colorado.clear.clinical.ner.util.SemEval2015CollectionReader;
import edu.colorado.clear.clinical.ner.util.SemEval2015Constants;
import edu.colorado.clear.clinical.ner.util.SemEval2015TaskCGoldAnnotator;
import org.apache.commons.io.FileUtils;
import org.apache.ctakes.clinicalpipeline.ClinicalPipelineFactory;
import org.apache.ctakes.typesystem.type.syntax.BaseToken;
import org.apache.ctakes.typesystem.type.textspan.Sentence;
import org.apache.log4j.Level;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.cas.CASException;
import org.apache.uima.collection.CollectionReader;
import org.apache.uima.jcas.JCas;
import org.cleartk.classifier.CleartkAnnotator;
import org.cleartk.classifier.CleartkSequenceAnnotator;
import org.cleartk.classifier.crfsuite.CRFSuiteStringOutcomeDataWriter;
import org.cleartk.classifier.jar.*;
import org.cleartk.classifier.libsvm.LIBSVMBooleanOutcomeDataWriter;
import org.cleartk.eval.AnnotationStatistics;
import org.cleartk.semeval2015.type.DiseaseDisorderAttribute;
import org.uimafit.component.JCasAnnotator_ImplBase;
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

public class TrainTestPipelineTaskC
{

	public static String resourceDirPath = "src/main/resources/";

	public static String semeval_train_c = resourceDirPath + "semeval-2015-task-14/task-c-train/subtask-c/data/train";
	public static String semeval_devel_c = resourceDirPath + "semeval-2015-task-14/task-c-dev/subtask-c/data/devel";

	public static String abbrFile = resourceDirPath + "data/abbr.txt";
	public static String cuiMapFile = resourceDirPath + "data/cuiMap.txt";

	public static String crfModels = "target/models/crf";
	public static String attributeModels = "target/models/attribute";
	public static String relModels = "target/models/rel";

	public static boolean SPAN_RESOLUTION = false;
	public static boolean VERBOSE = false;

	public static void main(String... args) throws Throwable
	{
		String[] trainExtension = {SemEval2015CollectionReader.PIPE_SUFFIX};

		File crfModelDir = new File(crfModels);
		File relModelDir = new File(relModels);
		File attModelDir = new File(attributeModels);

		File trainDir = new File(semeval_train_c);
		File testDir = new File(semeval_devel_c);

		Collection<File> trainFiles = FileUtils.listFiles(trainDir,
				trainExtension, true);
		Collection<File> testFiles = FileUtils.listFiles(testDir,
				trainExtension, true);

		train(trainFiles, crfModelDir, relModelDir, attModelDir);
		AnnotationStatistics<String> stats = test(testFiles, crfModelDir, relModelDir, attModelDir);
		System.out.println(stats);
	}

	public static void train(Collection<File> files, File ddModelDir, File relModelDir, File attModelDir) throws Throwable
	{
		CollectionReader reader = CollectionReaderFactory.createCollectionReader(
				SemEval2015CollectionReader.class,
				SemEval2015CollectionReader.PARAM_FILES,
				files);

		AggregateBuilder builder = new AggregateBuilder();
//		builder.add(ClinicalPipelineFactory.getDefaultPipeline());
		builder.add(ClinicalPipelineFactory.getTokenProcessingPipeline());

		builder.add(AnalysisEngineFactory.createPrimitiveDescription(SemEval2015TaskCGoldAnnotator.class,
				SemEval2015TaskCGoldAnnotator.PARAM_TRAINING,
				true,
				SemEval2015TaskCGoldAnnotator.PARAM_CUI_MAP,
				TrainTestPipeline.cuiMapFile));
		builder.add(AnalysisEngineFactory.createPrimitiveDescription(AbbreviationAnnotator.class,
				AbbreviationAnnotator.PARAM_FILE,
				new File(abbrFile)));

//		builder.add(AnalysisEngineFactory.createPrimitiveDescription(
//				DisorderSpanAnnotator.class,
//				CleartkSequenceAnnotator.PARAM_IS_TRAINING,
//				true,
//				DefaultSequenceDataWriterFactory.PARAM_OUTPUT_DIRECTORY,
//				ddModelDir.getPath(),
//				DefaultSequenceDataWriterFactory.PARAM_DATA_WRITER_CLASS_NAME,
//				CRFSuiteStringOutcomeDataWriter.class));

		builder.add(AnalysisEngineFactory.createPrimitiveDescription(
				AttributeAnnotator.class,
				CleartkSequenceAnnotator.PARAM_IS_TRAINING,
				true,
				DefaultSequenceDataWriterFactory.PARAM_OUTPUT_DIRECTORY,
				attModelDir.getPath(),
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

//		Train.main(ddModelDir);
		Train.main(attModelDir);
		if (SPAN_RESOLUTION) Train.main(relModelDir, "-c", "10", "-s", "0", "-t", "0");

		SemEval2015TaskCGoldAnnotator.writeMapToFile(SemEval2015TaskCGoldAnnotator.stringCUIMap, new File(cuiMapFile));
	}

	public static AnnotationStatistics<String> test(Collection<File> files, File ddDir, File relDir, File attDir) throws Throwable
	{

		CollectionReader reader = CollectionReaderFactory.createCollectionReader(
				SemEval2015CollectionReader.class,
				SemEval2015CollectionReader.PARAM_FILES,
				files);

		AggregateBuilder builder = new AggregateBuilder();
//		builder.add(ClinicalPipelineFactory.getDefaultPipeline());
		builder.add(ClinicalPipelineFactory.getTokenProcessingPipeline());
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
				CopySentencesAndTokens.class));

//		builder.add(AnalysisEngineFactory.createPrimitiveDescription(
//				DisorderSpanAnnotator.class,
//				CleartkAnnotator.PARAM_IS_TRAINING,
//				false,
//				GenericJarClassifierFactory.PARAM_CLASSIFIER_JAR_PATH,
//				new File(ddDir, "model.jar")));

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

		AnnotationStatistics<String> stats = new AnnotationStatistics<>();
		Function<DiseaseDisorderAttribute, ?> annotationToSpan = AnnotationStatistics.annotationToSpan();
		Function<DiseaseDisorderAttribute, String> annotationToOutcome = AnnotationStatistics.annotationToFeatureValue("attributeType");

		if (!VERBOSE) suppressLogging();

		for (JCas jCas : new JCasIterable(reader, builder.createAggregate()))
		{
			JCas goldView = jCas.getView(SemEval2015Constants.GOLD_VIEW);
			JCas systemView = jCas.getView(SemEval2015Constants.APP_VIEW);

			Collection<DiseaseDisorderAttribute> goldSpans = JCasUtil.select(goldView, DiseaseDisorderAttribute.class);
			Collection<DiseaseDisorderAttribute> systemSpans = JCasUtil.select(systemView, DiseaseDisorderAttribute.class);

			stats.add(goldSpans, systemSpans, annotationToSpan, annotationToOutcome);
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

	public static class CopySentencesAndTokens extends JCasAnnotator_ImplBase
	{
		@Override
		public void process(JCas jCas) throws AnalysisEngineProcessException
		{
			JCas appView = null;
			try
			{
				appView = jCas.getView(SemEval2015Constants.APP_VIEW);
			} catch (CASException e)
			{
				e.printStackTrace();
			}
			for (Sentence s : JCasUtil.select(jCas, Sentence.class))
			{
				Sentence sCopy = new Sentence(appView, s.getBegin(), s.getEnd());
				sCopy.setSegmentId(s.getSegmentId());
				sCopy.setSentenceNumber(s.getSentenceNumber());
				sCopy.addToIndexes(appView);
				for (BaseToken t : JCasUtil.selectCovered(jCas, BaseToken.class, s))
				{
					BaseToken tCopy = new BaseToken(appView, t.getBegin(), t.getEnd());
					tCopy.setPartOfSpeech(t.getPartOfSpeech());
					tCopy.setLemmaEntries(t.getLemmaEntries());
					tCopy.setNormalizedForm(t.getNormalizedForm());
					tCopy.setTokenNumber(t.getTokenNumber());
					tCopy.addToIndexes(appView);
				}
			}
		}
	}

}