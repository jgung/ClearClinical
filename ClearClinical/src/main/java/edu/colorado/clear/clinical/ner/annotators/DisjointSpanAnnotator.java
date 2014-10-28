package edu.colorado.clear.clinical.ner.annotators;

import com.google.common.collect.Lists;
import edu.colorado.clear.clinical.ner.features.relfeatures.*;
import edu.colorado.clear.clinical.ner.util.SemEval2015Constants;
import edu.colorado.clear.clinical.ner.util.SemEval2015GoldAnnotator;
import org.apache.ctakes.typesystem.type.relation.BinaryTextRelation;
import org.apache.ctakes.typesystem.type.relation.RelationArgument;
import org.apache.ctakes.typesystem.type.textspan.Sentence;
import org.apache.uima.UimaContext;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.cas.CASException;
import org.apache.uima.jcas.JCas;
import org.apache.uima.jcas.tcas.Annotation;
import org.apache.uima.resource.ResourceInitializationException;
import org.cleartk.classifier.CleartkAnnotator;
import org.cleartk.classifier.CleartkProcessingException;
import org.cleartk.classifier.Feature;
import org.cleartk.classifier.Instance;
import org.cleartk.semeval2015.type.DisorderSpan;
import org.uimafit.descriptor.ConfigurationParameter;
import org.uimafit.util.JCasUtil;

import java.util.*;

/* Adapted from the cTAKES relation extractor */
public class DisjointSpanAnnotator extends CleartkAnnotator<Boolean>
{

	public static final String NO_RELATION_CATEGORY = "-NONE-";

	public static final String PARAM_PROBABILITY_OF_KEEPING_A_NEGATIVE_EXAMPLE =
			"ProbabilityOfKeepingANegativeExample";
	public static boolean VERBOSE = false;
	@ConfigurationParameter(
			name = PARAM_PROBABILITY_OF_KEEPING_A_NEGATIVE_EXAMPLE,
			mandatory = false,
			description = "probability that a negative example should be retained for training")
	protected double probabilityOfKeepingANegativeExample = 1;
	protected Random coin = new Random(0);
	private List<RelationFeaturesExtractor> featureExtractors = this.getFeatureExtractors();
	private Class<? extends Annotation> coveringClass = Sentence.class;

	public static void createRelation(
			JCas jCas,
			DisorderSpan arg1,
			DisorderSpan arg2,
			String predictedCategory)
	{
		RelationArgument relArg1 = new RelationArgument(jCas);
		relArg1.setArgument(arg1);
		relArg1.setRole("arg1");
		relArg1.addToIndexes();
		RelationArgument relArg2 = new RelationArgument(jCas);
		relArg2.setArgument(arg2);
		relArg2.setRole("arg2");
		relArg2.addToIndexes();
		BinaryTextRelation relation = new BinaryTextRelation(jCas);
		relation.setArg1(relArg1);
		relation.setArg2(relArg2);
		relation.setCategory(predictedCategory);
		relation.addToIndexes();
	}

	protected List<RelationFeaturesExtractor> getFeatureExtractors()
	{
		return Lists.newArrayList(
				new TokenFeaturesExtractor(),
				new PartOfSpeechFeaturesExtractor(),
				new PhraseChunkingExtractor(),
				new DependencyTreeFeaturesExtractor(),
				new DependencyPathFeaturesExtractor(),
				new NamedEntityFeaturesExtractor());
	}

	protected Class<? extends BinaryTextRelation> getRelationClass()
	{
		return BinaryTextRelation.class;
	}

	public void initialize(UimaContext context) throws ResourceInitializationException
	{
		super.initialize(context);
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

		// lookup from pair of annotations to binary text relation
		// note: assumes that there will be at most one relation per pair
		Map<List<Annotation>, BinaryTextRelation> relationLookup;
		relationLookup = new HashMap<>();
		if (this.isTraining())
		{
			relationLookup = new HashMap<>();
			for (BinaryTextRelation relation : JCasUtil.select(jCas, this.getRelationClass()))
			{
				Annotation arg1 = relation.getArg1().getArgument();
				Annotation arg2 = relation.getArg2().getArgument();
				// The key is a list of args so we can do bi-directional lookup
				relationLookup.put(Arrays.asList(arg1, arg2), relation);
			}
		}

		// walk through each sentence in the text
		for (Annotation coveringAnnotation : JCasUtil.select(jCas, coveringClass))
		{

			// collect all relevant relation arguments from the sentence
			List<DisorderSpanPair> candidatePairs;
			if (this.isTraining())
			{
				candidatePairs = this.getSpanPairs(jCas, coveringAnnotation);
			} else
			{
				candidatePairs = this.getSpanPairs(appView, coveringAnnotation); // appView
			}

			// walk through the pairs of annotations
			for (DisorderSpanPair pair : candidatePairs)
			{
				DisorderSpan arg1 = pair.getArg1();
				DisorderSpan arg2 = pair.getArg2();

				// apply all the feature extractors to extract the list of features
				List<Feature> features = new ArrayList<>();
				for (RelationFeaturesExtractor extractor : this.featureExtractors)
				{
					features.addAll(extractor.extract(jCas, arg1, arg2));
				}

				// sanity check on feature values
				for (Feature feature : features)
				{
					if (feature.getValue() == null)
					{
						String message = "Null value found in %s from %s";
						throw new IllegalArgumentException(String.format(message, feature, features));
					}
				}

				// during training, feed the features to the data writer
				if (this.isTraining())
				{
					String category = this.getRelationCategory(relationLookup, arg1, arg2);
					if (category == null)
					{
						continue;
					}
					boolean rel;
					if (!category.equals(NO_RELATION_CATEGORY))
					{
						if (VERBOSE)
							System.out.println("RELATION " + category + ": " + arg1.getCoveredText() + " " + arg2.getCoveredText());
						rel = true;
					} else
					{
						rel = false;
					}

					this.dataWriter.write(new Instance<>(rel, features));
				} else
				{
					boolean rel = this.classify(features);
					// add a relation annotation if a true relation was predicted
					if (rel)
					{
						createRelation(appView, arg1, arg2, SemEval2015GoldAnnotator.DISJOINT_SPAN);
					}
				}
			}
		}
	}

	protected String getRelationCategory(
			Map<List<Annotation>, BinaryTextRelation> relationLookup,
			DisorderSpan arg1,
			DisorderSpan arg2)
	{
		BinaryTextRelation relation = relationLookup.get(Arrays.asList(arg1, arg2));
		String category;
		if (relation != null)
		{
			category = relation.getCategory();
		} else if (coin.nextDouble() <= this.probabilityOfKeepingANegativeExample)
		{
			category = NO_RELATION_CATEGORY;
		} else
		{
			category = null;
		}
		return category;
	}

	protected boolean classify(List<Feature> features) throws CleartkProcessingException
	{
		return this.classifier.classify(features);
	}

	public List<DisorderSpanPair> getSpanPairs(JCas jCas, Annotation sentence)
	{

		List<DisorderSpan> spans =
				JCasUtil.selectCovered(jCas, DisorderSpan.class, sentence.getBegin(), sentence.getEnd());

		List<DisorderSpanPair> pairs = new ArrayList<>();
		for (DisorderSpan arg1 : spans)
		{
			for (DisorderSpan arg2 : spans)
			{
				if (!arg1.equals(arg2))
				{
					// only consider ordered pairs
					if (arg1.getBegin() < arg2.getBegin())
					{
						pairs.add(new DisorderSpanPair(arg1, arg2));
					}
				}
			}
		}
		return pairs;
	}

	public static class DisorderSpanPair
	{

		private final DisorderSpan arg1;
		private final DisorderSpan arg2;

		public DisorderSpanPair(DisorderSpan arg1, DisorderSpan arg2)
		{
			this.arg1 = arg1;
			this.arg2 = arg2;
		}

		public final DisorderSpan getArg1()
		{
			return arg1;
		}

		public final DisorderSpan getArg2()
		{
			return arg2;
		}
	}
}
