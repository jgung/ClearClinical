package edu.colorado.clear.clinical.ner.features.spanfeatures;

import org.apache.ctakes.typesystem.type.syntax.BaseToken;
import org.apache.ctakes.typesystem.type.textsem.IdentifiedAnnotation;
import org.apache.uima.jcas.JCas;
import org.apache.uima.jcas.tcas.Annotation;
import org.cleartk.classifier.Feature;
import org.cleartk.classifier.feature.extractor.CleartkExtractorException;
import org.cleartk.classifier.feature.extractor.simple.SimpleFeatureExtractor;
import org.uimafit.util.JCasUtil;

import java.util.*;

public class UMLSFeaturesExtractor implements SimpleFeatureExtractor
{

	public static <T extends Comparable<? super T>> List<T> asSortedList(Collection<T> c)
	{
		List<T> list = new ArrayList<>(c);
		java.util.Collections.sort(list);
		return list;
	}

	public List<Feature> getCombinedFeatures(List<IdentifiedAnnotation> anns)
	{
		List<Feature> features = new ArrayList<>();
		Set<Integer> types = new TreeSet<>();
		for (IdentifiedAnnotation a : anns)
			types.add(a.getTypeID());
		if (types.size() == 0)
			return features;
		String featureString = "";
		for (int i : asSortedList(types))
			featureString += i;

		features.add(new Feature("UMLS_ANNS", featureString));
		return features;
	}

	public List<Feature> getIBFeatures(JCas jCas, Annotation token, List<IdentifiedAnnotation> anns)
	{
		List<Feature> features = new ArrayList<>();
		List<BaseToken> prevTokens = JCasUtil.selectPreceding(jCas, BaseToken.class, token, 1);
		List<IdentifiedAnnotation> prevAnns = new ArrayList<>();
		if (prevTokens.size() > 0)
		{
			BaseToken prevToken = prevTokens.get(0);
			prevAnns = JCasUtil.selectCovering(jCas,
					IdentifiedAnnotation.class, prevToken.getBegin(), prevToken.getEnd());
		}
		for (IdentifiedAnnotation a : anns)
		{
			String prefix = "B"; // BEGIN - first token in cTAKES annotation
			for (IdentifiedAnnotation pa : prevAnns)
				if (pa.getTypeID() == a.getTypeID())
					prefix = "I"; // INSIDE
			features.add(new Feature("CTAKES_ANN", prefix + a.getTypeID()));
		}
		return features;
	}

	public List<Feature> extract(JCas jCas, Annotation token) throws CleartkExtractorException
	{
		List<Feature> features = new ArrayList<>();
		/* this is a huge time consumer-need to find more efficient way */
		List<IdentifiedAnnotation> anns = JCasUtil.selectCovering(jCas, IdentifiedAnnotation.class, token.getBegin(), token.getEnd());
		features.addAll(this.getIBFeatures(jCas, token, anns));
		features.addAll(this.getCombinedFeatures(anns));
		return features;
	}
}
