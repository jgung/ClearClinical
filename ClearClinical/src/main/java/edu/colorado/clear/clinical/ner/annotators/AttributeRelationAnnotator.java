package edu.colorado.clear.clinical.ner.annotators;

import com.google.common.collect.Lists;

import edu.colorado.clear.clinical.ner.features.relfeatures.*;
import edu.colorado.clear.clinical.ner.pipeline.TrainTestPipelineTaskC;
import edu.colorado.clear.clinical.ner.util.SemEval2015Constants;
import edu.uab.ccts.nlp.uima.annotator.MutualInformationAnnotator;

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
import org.cleartk.semeval2015.type.DiseaseDisorderAttribute;
import org.cleartk.semeval2015.type.DisorderRelation;
import org.cleartk.semeval2015.type.DisorderSpan;
import org.uimafit.descriptor.ConfigurationParameter;
import org.uimafit.util.JCasUtil;

import java.sql.Connection;
import java.sql.DriverManager;
import java.util.*;

/* Adapted from the cTAKES relation extractor */
public class AttributeRelationAnnotator extends CleartkAnnotator<String>
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
	private static Connection dbConnection = null;
	private List<RelationFeaturesExtractor> featureExtractors = this.getFeatureExtractors();
	private Class<? extends Annotation> coveringClass = Sentence.class;
	
	static {
		dbConnection = TrainTestPipelineTaskC.dbConnection;
	}

	public static void createRelation(
			JCas jCas,
			DiseaseDisorderAttribute arg1,
			DisorderSpan arg2,
			String predictedCategory)
	{
		RelationArgument relArg1 = new RelationArgument(jCas);
		relArg1.setArgument(arg1);
		relArg1.setRole("arg1");
		RelationArgument relArg2 = new RelationArgument(jCas);
		relArg2.setArgument(arg2);
		relArg2.setRole("arg2");
		DisorderRelation relation = new DisorderRelation(jCas);
		List<Sentence> s = JCasUtil.selectCovering(jCas, Sentence.class, arg1.getBegin(), arg1.getEnd());
		List<Sentence> s2 = JCasUtil.selectCovering(jCas, Sentence.class, arg2.getBegin(), arg2.getEnd());
		for (Sentence sent : s)
		{
			if (!s2.contains(sent))
				return;
		}
		relArg1.addToIndexes();
		relArg2.addToIndexes();

		relation.setArg1(relArg1);
		relation.setArg2(relArg2);
		relation.setCategory(predictedCategory);

		relation.addToIndexes();
	}


	
	protected List<RelationFeaturesExtractor> getFeatureExtractors()
	{
		//dbConnection = getDatabaseConnection();
		return Lists.newArrayList(
				new TokenFeaturesExtractor(),
				new PartOfSpeechFeaturesExtractor(),
				new PhraseChunkingExtractor(),
				new DependencyTreeFeaturesExtractor(),
				new DependencyPathFeaturesExtractor(),
				new ScoredDistanceFeaturesExtractor(dbConnection),
				new NamedEntityFeaturesExtractor());
	}

	protected Class<? extends DisorderRelation> getRelationClass()
	{
		return DisorderRelation.class;
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

		Map<List<Annotation>, DisorderRelation> relationLookup;
		relationLookup = new HashMap<>();
		if (this.isTraining())
		{
			relationLookup = new HashMap<>();
			for (DisorderRelation relation : JCasUtil.select(jCas, this.getRelationClass()))
			{
				Annotation arg1 = relation.getArg1().getArgument();
				Annotation arg2 = relation.getArg2().getArgument();
				// The key is a list of args so we can do bi-directional lookup
				relationLookup.put(Arrays.asList(arg1, arg2), relation);
			}
		}

		for (Annotation coveringAnnotation : JCasUtil.select(jCas, coveringClass))
		{

			List<DisorderRel> candidatePairs;
			if (this.isTraining())
			{
				candidatePairs = this.getSpanPairs(jCas, coveringAnnotation);
			} else
			{
				candidatePairs = this.getSpanPairs(appView, coveringAnnotation); // appView
			}

			for (DisorderRel pair : candidatePairs)
			{
				DiseaseDisorderAttribute arg1 = pair.getArg1();
				DisorderSpan arg2 = pair.getArg2();

				List<Feature> features = new ArrayList<>();
				for (RelationFeaturesExtractor extractor : this.featureExtractors)
				{
					features.addAll(extractor.extract(jCas, arg1, arg2));
				}

				if (this.isTraining())
				{
					String category = this.getRelationCategory(relationLookup, arg1, arg2);
					if (category == null)
						continue;
					this.dataWriter.write(new Instance<>(category, features));
				} else
				{
					String rel = this.classify(features);
					createRelation(appView, arg1, arg2, rel);
				}
			}
		}
		//try {
		//	if(dbConnection!=null) { System.out.println("Closing dbConnection for "+this.hashCode()); dbConnection.close(); } 
		//} catch (Exception e) { e.printStackTrace(); }
	}

	protected String getRelationCategory(
			Map<List<Annotation>, DisorderRelation> relationLookup,
			DiseaseDisorderAttribute arg1,
			DisorderSpan arg2)
	{
		DisorderRelation relation = relationLookup.get(Arrays.asList(arg1, arg2));
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

	protected String classify(List<Feature> features) throws CleartkProcessingException
	{
		return this.classifier.classify(features);
	}

	public List<DisorderRel> getSpanPairs(JCas jCas, Annotation sentence)
	{
		int start = sentence.getBegin();
		int end = sentence.getEnd();
		List<DiseaseDisorderAttribute> attSpans =
				JCasUtil.selectCovered(jCas, DiseaseDisorderAttribute.class, start, end);
		List<DisorderSpan> disorderSpans =
				JCasUtil.selectCovered(jCas, DisorderSpan.class, start, end);
		List<DisorderRel> pairs = new ArrayList<>();
		for (DiseaseDisorderAttribute att : attSpans)
			for (DisorderSpan disorder : disorderSpans)
				pairs.add(new DisorderRel(att, disorder));

		return pairs;
	}

	public static class DisorderRel
	{

		private final DiseaseDisorderAttribute arg1;
		private final DisorderSpan arg2;

		public DisorderRel(DiseaseDisorderAttribute arg1, DisorderSpan arg2)
		{
			this.arg1 = arg1;
			this.arg2 = arg2;
		}

		public final DiseaseDisorderAttribute getArg1()
		{
			return arg1;
		}

		public final DisorderSpan getArg2()
		{
			return arg2;
		}
	}
}
