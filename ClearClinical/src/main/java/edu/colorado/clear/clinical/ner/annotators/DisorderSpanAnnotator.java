package edu.colorado.clear.clinical.ner.annotators;

import edu.colorado.clear.clinical.ner.features.spanfeatures.NormalizedFeaturesExtractor;
import edu.colorado.clear.clinical.ner.features.spanfeatures.UMLSFeaturesExtractor;
import edu.colorado.clear.clinical.ner.util.SemEval2015Constants;
import org.apache.ctakes.typesystem.type.syntax.BaseToken;
import org.apache.ctakes.typesystem.type.textspan.Sentence;
import org.apache.uima.UimaContext;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.cas.CASException;
import org.apache.uima.jcas.JCas;
import org.apache.uima.resource.ResourceInitializationException;
import org.cleartk.classifier.CleartkSequenceAnnotator;
import org.cleartk.classifier.Feature;
import org.cleartk.classifier.Instances;
import org.cleartk.classifier.chunking.BIOChunking;
import org.cleartk.classifier.feature.extractor.CleartkExtractor;
import org.cleartk.classifier.feature.extractor.CleartkExtractor.Bag;
import org.cleartk.classifier.feature.extractor.CleartkExtractor.Following;
import org.cleartk.classifier.feature.extractor.CleartkExtractor.Preceding;
import org.cleartk.classifier.feature.extractor.simple.*;
import org.cleartk.classifier.feature.extractor.simple.CharacterCategoryPatternExtractor.PatternType;
import org.cleartk.classifier.feature.function.*;
import org.cleartk.classifier.feature.function.CharacterNGramFeatureFunction.Orientation;
import org.cleartk.semeval2015.type.DisorderSpan;
import org.cleartk.util.ViewURIUtil;
import org.uimafit.util.JCasUtil;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class DisorderSpanAnnotator extends CleartkSequenceAnnotator<String>
{
	public static boolean VERBOSE = true;
	protected String discourseSection;
	private SimpleFeatureExtractor extractor;
	//	private BILOChunking<BaseToken, DisorderSpan> chunking;
	private CleartkExtractor contextExtractor;
	//	private CleartkExtractor umlsContextExtractor;
	private CleartkExtractor bagExtractor;
	private UMLSFeaturesExtractor umlsExtractor;
	//	private IOChunking<BaseToken, DisorderSpan> chunking;
	private BIOChunking<BaseToken, DisorderSpan> chunking;

	public static List<List<Feature>> extractFeatures(JCas jCas, DisorderSpanAnnotator annotator, Sentence s)
			throws AnalysisEngineProcessException
	{
		List<BaseToken> tokens = JCasUtil.selectCovered(jCas, BaseToken.class, s);
		List<List<Feature>> featureLists = new ArrayList<>();

		File docFile = new File(ViewURIUtil.getURI(jCas));
		File typeDir = docFile.getParentFile();
		String docType = typeDir.getName();
		for (BaseToken token : tokens)
		{
			List<Feature> features = new ArrayList<>();
			if (token.getCoveredText().equals(":"))
			{
				String beforeColon = JCasUtil.selectPreceding(jCas, BaseToken.class, token, 1).get(0).getCoveredText();
				if (beforeColon.length() > 3)
					annotator.discourseSection = beforeColon;
			}
			features.addAll(annotator.extractor.extract(jCas, token));
			features.addAll(annotator.contextExtractor.extract(jCas, token));
//			features.addAll(annotator.bagExtractor.extract(jCas, token));
//			features.addAll(annotator.umlsExtractor.extract(jCas, token));
//			features.addAll(annotator.umlsContextExtractor.extract(jCas, token));
			features.add(new Feature("DISCOURSE_SECTION" + annotator.discourseSection, token.getNormalizedForm()));
			features.add(new Feature("DOC_TYPE" + docType, token.getNormalizedForm()));
			features.add(new Feature("DISCOURSE_SECTION", annotator.discourseSection));
			featureLists.add(features);
		}
		return featureLists;
	}

	public void initialize(UimaContext context) throws ResourceInitializationException
	{
		super.initialize(context);

//		SimpleFeatureExtractor topicFeatureExtractor = new TopicFeatureExtractor(new File("data/topicModel64-500.txt"));

		FeatureFunctionExtractor normalizedTokenFeatureExtractor = new FeatureFunctionExtractor(
				new NormalizedFeaturesExtractor(),
				new NumericTypeFeatureFunction(),
				new CharacterNGramFeatureFunction(Orientation.RIGHT_TO_LEFT, 0, 2, 2, true),
				new CharacterNGramFeatureFunction(Orientation.RIGHT_TO_LEFT, 0, 3, 3, true),
				new CharacterNGramFeatureFunction(Orientation.LEFT_TO_RIGHT, 0, 2, 2, true),
				new CharacterNGramFeatureFunction(Orientation.LEFT_TO_RIGHT, 0, 3, 3, true)
		);

		FeatureFunctionExtractor tokenFeatureExtractor = new FeatureFunctionExtractor(
				new CoveredTextExtractor(),
				new LowerCaseFeatureFunction(),
				new CapitalTypeFeatureFunction()
		);

		// the token feature extractor: text, char pattern (uppercase, digits, etc.), and part-of-speech
		this.extractor = new CombinedExtractor(
				tokenFeatureExtractor,
				normalizedTokenFeatureExtractor,
				new CharacterCategoryPatternExtractor(PatternType.REPEATS_MERGED),
				new TypePathExtractor(BaseToken.class, "partOfSpeech")
//				,topicFeatureExtractor
		);

		// the context feature extractor: the features above for the 3 preceding and 3 following tokens
		this.contextExtractor = new CleartkExtractor(
				BaseToken.class,
				this.extractor,
				new Preceding(1),
				new Following(1)
		);

		this.umlsExtractor = new UMLSFeaturesExtractor();

//		this.umlsContextExtractor = new CleartkExtractor(
//				BaseToken.class,
//				this.umlsExtractor,
//				new Preceding(1),
//				new Following(1));

		this.bagExtractor = new CleartkExtractor(
				BaseToken.class,
				new CoveredTextExtractor(),
				new Bag(new Following(5), new Preceding(5)));

		this.chunking = new BIOChunking<>(
				BaseToken.class,
				DisorderSpan.class,
				"chunk");

//		this.chunking = new BILOChunking<BaseToken, DisorderSpan>(
//				BaseToken.class,
//				DisorderSpan.class,
//				"chunk");
//		this.chunking = new IOChunking<BaseToken, DisorderSpan>(
//				BaseToken.class,
//				DisorderSpan.class,
//				"chunk");

		discourseSection = "DOC_START";

	}

	public void process(JCas jCas) throws AnalysisEngineProcessException
	{

		JCas appView = null;
		if (!this.isTraining())
		{
			try
			{
				appView = jCas.getView(SemEval2015Constants.APP_VIEW);
			} catch (CASException e)
			{
				e.printStackTrace();
			}
		}
		if (VERBOSE) System.out.println("Extracting features for: " + ViewURIUtil.getURI(jCas).getPath());
		for (Sentence sentence : JCasUtil.select(jCas, Sentence.class))
		{
			List<List<Feature>> featureLists = extractFeatures(jCas, this, sentence);

			if (this.isTraining())
			{
				List<DisorderSpan> disorderSpans = new ArrayList<>();

				disorderSpans.addAll(JCasUtil.selectCovered(
						jCas,
						DisorderSpan.class,
						sentence.getBegin(), sentence.getEnd()));

				List<BaseToken> goldTokens = JCasUtil.selectCovered(
						jCas,
						BaseToken.class,
						sentence.getBegin(), sentence.getEnd());

				List<String> outcomes = this.chunking.createOutcomes(jCas, goldTokens, disorderSpans);
				this.dataWriter.write(Instances.toInstances(outcomes, featureLists));
			} else
			{
				List<BaseToken> cleanTokens = JCasUtil.selectCovered(
						appView,
						BaseToken.class,
						sentence.getBegin(), sentence.getEnd());
				List<String> outcomes = this.classifier.classify(featureLists);
				this.chunking.createChunks(appView, cleanTokens, outcomes);
			}
		}
	}
}
