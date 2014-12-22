package edu.colorado.clear.clinical.ner.pipeline;

import com.google.common.base.Function;

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
import org.apache.uima.UIMAException;
import org.apache.uima.analysis_engine.AnalysisEngine;
import org.apache.uima.collection.CollectionReader;
import org.apache.uima.jcas.JCas;
import org.cleartk.classifier.CleartkAnnotator;
import org.cleartk.classifier.CleartkSequenceAnnotator;
import org.cleartk.classifier.crfsuite.CRFSuiteStringOutcomeDataWriter;
import org.cleartk.classifier.jar.*;
import org.cleartk.classifier.liblinear.LIBLINEARBooleanOutcomeDataWriter;
import org.cleartk.classifier.liblinear.LIBLINEARStringOutcomeDataWriter;
import org.cleartk.eval.AnnotationStatistics;
import org.cleartk.semeval2015.type.DiseaseDisorderAttribute;
import org.cleartk.semeval2015.type.DisorderRelation;
import org.cleartk.semeval2015.type.DisorderSpan;
import org.hsqldb.server.Server;
import org.uimafit.component.ViewCreatorAnnotator;
import org.uimafit.component.ViewTextCopierAnnotator;
import org.uimafit.factory.AggregateBuilder;
import org.uimafit.factory.AnalysisEngineFactory;
import org.uimafit.factory.CollectionReaderFactory;
import org.uimafit.pipeline.JCasIterable;
import org.uimafit.pipeline.SimplePipeline;
import org.uimafit.util.JCasUtil;

import java.io.File;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.*;


/**
 * This should correspond to Task 2a
 *
 */
public class TrainTestPipelineTaskC
{

	public static String resourceDirPath = "src/main/resources/";

	//public static String semeval_train_c = resourceDirPath + "semeval-2015-task-14/subtask-c/data/train";
	//public static String semeval_devel_c = resourceDirPath + "semeval-2015-task-14/subtask-c/data/devel";
	//public static String mini_devel_c = resourceDirPath + "semeval-2015-task-14/subtask-c/data/devel";
	//Directory format has changed with the new data file, new format is below. Minidev needs to be manually created if used.
	public static String semeval_train_c = resourceDirPath + "semeval-2015-task-14/data/train";
	public static String semeval_devel_c = resourceDirPath + "semeval-2015-task-14/data/devel";
	public static String mini_devel_c = resourceDirPath + "semeval-2015-task-14/data/minidev";

	public static String abbrFile = resourceDirPath + "data/abbr.txt";
	public static String cuiMapFile = resourceDirPath + "data/cuiMap.txt";

	public static String crfModels = "target/models/crf";
	public static String attributeModels = "target/models/attribute";
	public static String relModels = "target/models/rel";
	public static String attRelModels = "target/models/attRel";
	public static String attNormModels = "target/models/attNorm";

	public static boolean SPAN_RESOLUTION = true;
	public static boolean VERBOSE = true;
	public static boolean USE_YTEX = false;
	public static boolean USE_MI = false;
	public static boolean SKIP_TRAINING = false;
	
	public static Connection dbConnection = getDatabaseConnection();

	public static void main(String... args) throws Throwable
	{
		String[] trainExtension = {SemEval2015CollectionReader.PIPE_SUFFIX};

		File crfModelDir = new File(crfModels);
		File relModelDir = new File(relModels);
		File attModelDir = new File(attributeModels);
		File attRelsDir = new File(attRelModels);
		File attNormDir = new File(attNormModels);

		File trainDir = new File(semeval_train_c);
		File testDir = new File(semeval_devel_c);

		Collection<File> trainFiles = FileUtils.listFiles(trainDir,
				trainExtension, true);
		Collection<File> testFiles = FileUtils.listFiles(testDir,
				trainExtension, true);
		
		for(String arg:args){
			if(arg.equalsIgnoreCase("-ytex")) USE_YTEX=true;
			if(arg.equalsIgnoreCase("-mi")) USE_MI=true;
			if(arg.equalsIgnoreCase("-skiptraining")) SKIP_TRAINING=true;
		}

		if (!SKIP_TRAINING) train(trainFiles, crfModelDir, relModelDir, attModelDir, attRelsDir, attNormDir);
		String stats = test(testFiles, crfModelDir, relModelDir, attModelDir, attRelsDir, attNormDir);
		System.out.println(stats);
	}

	public static void train(Collection<File> files, File ddModelDir, File relModelDir,
	                         File attModelDir, File attRelsDir, File attNormDir) throws Throwable
	{
		CollectionReader reader = CollectionReaderFactory.createCollectionReader(
				SemEval2015CollectionReader.class,
				SemEval2015CollectionReader.PARAM_FILES,
				files);

		AggregateBuilder builder = new AggregateBuilder();
//		builder.add(ClinicalPipelineFactory.getDefaultPipeline());
//		builder.add(ApplicationPipeline.getClearDefaultPipeline(USE_YTEX));
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

	public static String test(Collection<File> files, File ddDir,
	                          File relDir, File attDir, File attRelsDir, File attNormDir) throws Throwable
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
				"template_results"));

		if (!VERBOSE) suppressLogging();

		Server hsqlServer = null;
		if(USE_MI){
			hsqlServer = new Server();
			hsqlServer.setDatabaseName(0, MutualInformationAnnotator.default_db_name);
			hsqlServer.setDatabasePath(0, "file:"+MutualInformationAnnotator.default_db_path);
			hsqlServer.start();
		}

		if (!VERBOSE) suppressLogging();

		String stats = getRelationStats(reader, builder.createAggregate());
		if(hsqlServer!=null && USE_MI) hsqlServer.stop();

		return stats;

	}

	public static String getRelationStats(CollectionReader reader, AnalysisEngine engine)
			throws UIMAException, IOException
	{

		AnnotationStatistics<String> stats = new AnnotationStatistics<>();
		AnnotationStatistics<String> normStats = new AnnotationStatistics<>();
		AnnotationStatistics<String> spanStats = new AnnotationStatistics<>();

		Function<DiseaseDisorderAttribute, ?> annotationToSpan = AnnotationStatistics.annotationToSpan();
		Function<DiseaseDisorderAttribute, String> annotationToOutcome = AnnotationStatistics.annotationToFeatureValue("attributeType");
		Function<DiseaseDisorderAttribute, String> annotationToNorm = AnnotationStatistics.annotationToFeatureValue("norm");
		Function<DisorderSpan, ?> disorderToSpan = AnnotationStatistics.annotationToSpan();
		Function<DisorderSpan, String> disorderToChunk = AnnotationStatistics.annotationToFeatureValue("chunk");

		double totalC = 0;
		double totalT = 0;
		double totalST = 0;
		Map<String, Integer> correct = new HashMap<>();  /* correct system predictions */
		Map<String, Integer> total = new HashMap<>(); /* system predictions */
		Map<String, Integer> goldTotal = new HashMap<>(); /* gold predictions */
		StringBuilder results = new StringBuilder();
		results.append("type\trecall\tprecision\tf1\tgoldCount\tsysCount\ttotalCorrect\n");
		for (JCas jCas : new JCasIterable(reader, engine))
		{
			JCas goldView = jCas.getView(SemEval2015Constants.GOLD_VIEW);
			JCas systemView = jCas.getView(SemEval2015Constants.APP_VIEW);//FIXME No sofaFS with name APPLICATION_VIEW found.
			Collection<DiseaseDisorderAttribute> goldSpans = JCasUtil.select(goldView, DiseaseDisorderAttribute.class);
			Collection<DiseaseDisorderAttribute> systemSpans = JCasUtil.select(systemView, DiseaseDisorderAttribute.class);
			Collection<DisorderSpan> goldDisorderSpans = JCasUtil.select(goldView, DisorderSpan.class);
			Collection<DisorderSpan> systemDisorderSpans = JCasUtil.select(systemView, DisorderSpan.class);

			stats.add(goldSpans, systemSpans, annotationToSpan, annotationToOutcome);
			normStats.add(goldSpans, systemSpans, annotationToSpan, annotationToNorm);
			spanStats.add(goldDisorderSpans, systemDisorderSpans, disorderToSpan, disorderToChunk);

			List<DisorderRelation> goldRelations = new ArrayList<>(JCasUtil.select(goldView, DisorderRelation.class));
			List<DisorderRelation> sysRelations = new ArrayList<>(JCasUtil.select(systemView, DisorderRelation.class));
			for (DisorderRelation rel : sysRelations)
				increment(total, rel.getCategory());
			for (DisorderRelation rel : goldRelations)
			{
				increment(goldTotal, rel.getCategory());
				List<DisorderRelation> remove = new ArrayList<>();
				for (DisorderRelation sysRel : sysRelations)
				{
					if (sysRel.getCategory().equals(AttributeRelationAnnotator.NO_RELATION_CATEGORY))
						continue;
					if (sysRel.getArg1().getArgument().getBegin() == rel.getArg1().getArgument().getBegin()
							&& sysRel.getArg2().getArgument().getBegin() == rel.getArg2().getArgument().getBegin())
					{
						if (sysRel.getCategory().equals(rel.getCategory()))
						{
							increment(correct, rel.getCategory());
							remove.add(sysRel);
							break;
						}
					}
				}
				sysRelations.removeAll(remove);
			}
		}
		for (String key : goldTotal.keySet())
		{
			Integer c = correct.get(key);  // correct system predictions
			if (c == null)
				c = 0;
			totalC += c;
			Integer t = goldTotal.get(key); // total number of gold predictions
			if (t == null)
				t = 0;
			totalT += t;
			Integer st = total.get(key); // total number of system predictions
			if (st == null)
				st = 0;
			totalST += st;
			double recall = (double) c / t;
			double precision = (double) c / st;
			if (c == 0)
			{
				recall = 0;
				precision = 0;
			}
			double f1 = (2 * precision * recall) / (precision + recall);
			if (precision + recall == 0)
				f1 = 0;
			results.append(key + "\t" + recall + "\t" + precision + "\t" + f1 + "\t" + t + "\t" + st + "\t" + c + "\n");
		}
		double recall = totalC / totalT;
		double precision = totalC / totalST;
		if (totalC == 0)
		{
			recall = 0;
			precision = 0;
		}
		double f1 = (2 * precision * recall) / (precision + recall);
		results.append("Overall\t" + recall + "\t" + precision + "\t" + f1 + "\t" + totalT + "\t" + totalST + "\t" + totalC + "\n");
		results.append("\t").append(stats.toString());
		results.append("\t").append(normStats.toString());
		results.append("\t").append(spanStats.toString());


		return results.toString();
	}

	public static void increment(Map<String, Integer> map, String key)
	{
		Integer i = map.get(key);
		if (i == null)
		{
			i = 1;
		} else
		{
			++i;
		}
		map.put(key, i);
	}
	
	public static void suppressLogging()
	{
		@SuppressWarnings("unchecked")
		List<Logger> loggers = Collections.<Logger>list(LogManager.getCurrentLoggers());
		loggers.add(LogManager.getRootLogger());
		for (Logger logger : loggers)
			logger.setLevel(Level.OFF);
	}
	
	public static Connection getDatabaseConnection() {
		Connection con = null;
		try {
			Class.forName("org.hsqldb.jdbc.JDBCDriver" );
			con = DriverManager.getConnection(MutualInformationAnnotator.default_db_url);
			if(con!=null) {
				System.out.println("Initialized database connection");
			} else {
				System.out.println("Failed to initialize database connection");
			}
		} catch (Exception e) { e.printStackTrace(); }
		return con;
	}

	/*
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
	*/

}
