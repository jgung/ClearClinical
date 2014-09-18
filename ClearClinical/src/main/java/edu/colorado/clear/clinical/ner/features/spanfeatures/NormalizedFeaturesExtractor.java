package edu.colorado.clear.clinical.ner.features.spanfeatures;

import org.apache.ctakes.typesystem.type.syntax.BaseToken;
import org.apache.uima.jcas.JCas;
import org.apache.uima.jcas.tcas.Annotation;
import org.cleartk.classifier.Feature;
import org.cleartk.classifier.feature.extractor.CleartkExtractorException;
import org.cleartk.classifier.feature.extractor.simple.SimpleFeatureExtractor;

import java.util.ArrayList;
import java.util.List;

public class NormalizedFeaturesExtractor implements SimpleFeatureExtractor
{

	public List<Feature> extract(JCas jCas, Annotation token)
			throws CleartkExtractorException
	{

		List<Feature> features = new ArrayList<>();
		String normalizedForm = ((BaseToken) token).getNormalizedForm();
		if (normalizedForm != null)
		{
			String[] tokens = normalizedForm.split("\\s");
			for (String s : tokens)
			{
				features.add(new Feature("NORMALIZED_FORM_TOKEN", s));
			}
			features.add(new Feature("FULL_NORMALIZED_FORM", normalizedForm));
		}
		return features;
	}
}
