package edu.colorado.clear.clinical.ner.pipeline;

import edu.colorado.clear.clinical.ner.annotators.*;
import edu.colorado.clear.clinical.ner.util.SemEval2015CollectionReader;
import edu.colorado.clear.clinical.ner.util.SemEval2015Constants;
import edu.uab.ccts.nlp.uima.annotator.SegmentRegexAnnotator;

import org.apache.commons.io.FileUtils;
import org.apache.ctakes.assertion.medfacts.cleartk.PolarityCleartkAnalysisEngine;
import org.apache.ctakes.chunker.ae.Chunker;
import org.apache.ctakes.chunker.ae.adjuster.ChunkAdjuster;
import org.apache.ctakes.clinicalpipeline.ClinicalPipelineFactory.CopyNPChunksToLookupWindowAnnotations;
import org.apache.ctakes.clinicalpipeline.ClinicalPipelineFactory.RemoveEnclosedLookupWindows;
import org.apache.ctakes.contexttokenizer.ae.ContextDependentTokenizerAnnotator;
import org.apache.ctakes.core.ae.TokenizerAnnotatorPTB;
import org.apache.ctakes.dependency.parser.ae.ClearNLPDependencyParserAE;
import org.apache.ctakes.dictionary.lookup.ae.UmlsDictionaryLookupAnnotator;
import org.apache.ctakes.lvg.ae.LvgAnnotator;
import org.apache.ctakes.postagger.POSTagger;
import org.apache.ctakes.ytex.uima.annotators.DBConsumer;
import org.apache.ctakes.ytex.uima.annotators.SenseDisambiguatorAnnotator;
import org.apache.ctakes.ytex.uima.annotators.SentenceDetector;
import org.apache.uima.UIMAFramework;
import org.apache.uima.analysis_engine.AnalysisEngineDescription;
import org.apache.uima.collection.CollectionReader;
import org.apache.uima.resource.ResourceInitializationException;
import org.apache.uima.util.XMLInputSource;
import org.cleartk.classifier.CleartkAnnotator;
import org.cleartk.classifier.jar.GenericJarClassifierFactory;
import org.cleartk.util.ae.UriToDocumentTextAnnotator;
import org.cleartk.util.cr.UriCollectionReader;
import org.uimafit.component.ViewCreatorAnnotator;
import org.uimafit.component.ViewTextCopierAnnotator;
import org.uimafit.factory.AggregateBuilder;
import org.uimafit.factory.AnalysisEngineFactory;
import org.uimafit.pipeline.SimplePipeline;

import java.io.File;
import java.net.URL;
import java.util.Collection;

/**
 * This is currently just the application pipeline for subtasks-a-b
 *
 */
public class ApplicationPipeline
{
	public static boolean USE_YTEX = false;
	public static boolean SKIP_TRAINING = false;
	
	public static void main(String... args) throws Throwable
	{
		String[] testExtension = {SemEval2015CollectionReader.TEXT_SUFFIX};
		String[] trainExtension = {SemEval2015CollectionReader.PIPE_SUFFIX};

		File crfModelDir = new File(TrainTestPipeline.crfModels);
		File relModelDir = new File(TrainTestPipeline.relModels);

		File trainDir = new File(TrainTestPipeline.semeval_train);
		File testDir = new File(TrainTestPipeline.semeval_devel);

		Collection<File> trainFiles = FileUtils.listFiles(trainDir,
				trainExtension, true);
		Collection<File> testFiles = FileUtils.listFiles(testDir,
				testExtension, true);

		for(String arg:args){
			if(arg.equalsIgnoreCase("-ytex")) USE_YTEX=true;
			if(arg.equalsIgnoreCase("-skiptraining")) SKIP_TRAINING=true;
		}
		
		TrainTestPipeline.train(trainFiles, crfModelDir, relModelDir);
		apply(testFiles, crfModelDir, relModelDir);
	}

	public static void apply(Collection<File> files, File crfDir, File relDir) throws Throwable
	{
		CollectionReader reader = UriCollectionReader.getCollectionReaderFromFiles(files);

		AggregateBuilder builder = new AggregateBuilder();
		builder.add(UriToDocumentTextAnnotator.getDescription());

		//Replace default pipeline
		//builder.add(ClinicalPipelineFactory.getDefaultPipeline());
		builder.add(getClearDefaultPipeline(USE_YTEX));
		
		builder.add(AnalysisEngineFactory.createPrimitiveDescription(DocIDAnnotator.class,
				DocIDAnnotator.PARAM_CUI_MAP_PATH,
				TrainTestPipeline.cuiMapFile));
		builder.add(AnalysisEngineFactory.createPrimitiveDescription(AbbreviationAnnotator.class,
				AbbreviationAnnotator.PARAM_FILE,
				new File(TrainTestPipeline.abbrFile)));

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

		if (TrainTestPipeline.SPAN_RESOLUTION)
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

		if (!TrainTestPipeline.VERBOSE) TrainTestPipeline.suppressLogging();

		SimplePipeline.runPipeline(reader, builder.createAggregate());
	}
	
	
	  /** Build description of new pipeline, changes versus base pipeline include:
	   * Replace SimpleSegmentAnnotator with SegmentRegexAnnotator
	   * Replace SentenceDetectorAnnotator with ytex version (allow multi-line sentences)
	   * Replace DictionaryLookupAnnotator with ytex DictionaryLookupAnnotatorUMLS
	   * Add YTEX Word Sense Disambiguation
	   * TODO
	   * -add rule-based annotator of UAB rules for CUI mapping
	   * -add appropriate output (database, text files) 
	   */
	  public static AnalysisEngineDescription getClearDefaultPipeline(boolean use_ytex) throws ResourceInitializationException{
		    AggregateBuilder builder = new AggregateBuilder();
		    //builder.add(ClinicalPipelineFactory.getTokenProcessingPipeline());
		    
		    //New Segment Annotator that does more than make every segment the same
		    builder.add(SegmentRegexAnnotator.createAnnotatorDescription());
		    
		    //Using YTEX SentenceDetector
			builder.add(AnalysisEngineFactory.createPrimitiveDescription(
					SentenceDetector.class,
					SentenceDetector.SD_MODEL_FILE_PARAM,
					"org/apache/ctakes/core/sentdetect/sd-med-model.zip"
					));
			
		   
		    builder.add(TokenizerAnnotatorPTB.createAnnotatorDescription());
		    builder.add(LvgAnnotator.createAnnotatorDescription());
		    builder.add(ContextDependentTokenizerAnnotator.createAnnotatorDescription());
		    builder.add(POSTagger.createAnnotatorDescription());
		    builder.add(Chunker.createAnnotatorDescription());
		    
		    // Standard Chunk Adjustment
		    //builder.add(getStandardChunkAdjusterAnnotator());
		    builder.add(ChunkAdjuster.createAnnotatorDescription(new String[] { "NP", "NP" },  1));
		    // adjust NP in NP PP NP to span all three
		    builder.add(ChunkAdjuster.createAnnotatorDescription(new String[] { "NP", "PP", "NP" }, 2));
		    
		    builder.add(AnalysisEngineFactory.createPrimitiveDescription(CopyNPChunksToLookupWindowAnnotations.class));
		    builder.add(AnalysisEngineFactory.createPrimitiveDescription(RemoveEnclosedLookupWindows.class));
		    
		    builder.add(UmlsDictionaryLookupAnnotator.createAnnotatorDescription());
	
		    if(use_ytex) builder.add(AnalysisEngineFactory.createPrimitiveDescription(SenseDisambiguatorAnnotator.class));
			
		    
		    builder.add(ClearNLPDependencyParserAE.createAnnotatorDescription());
		    builder.add(PolarityCleartkAnalysisEngine.createAnnotatorDescription());
		    
		    //Adding YTEX DBConsumer, FIXME, need to extend DBConsumer class or find YTEX one
            URL typeurl = Thread.currentThread().getContextClassLoader().getResource("org/apache/ctakes/ytex/types/TypeSystem.xml");
            try {
		    Object descriptor = UIMAFramework.getXMLParser().parse(
                    new XMLInputSource(typeurl));
		    if (descriptor instanceof AnalysisEngineDescription) {
			builder.add(AnalysisEngineFactory.createPrimitiveDescription(DBConsumer.class,
					"analysisBatch","testClear1"
			));
		    }} catch (Exception e) { e.printStackTrace(); }
		    
		    return builder.createAggregateDescription();
		  }

}
