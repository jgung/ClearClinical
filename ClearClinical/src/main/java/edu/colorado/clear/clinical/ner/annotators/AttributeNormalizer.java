package edu.colorado.clear.clinical.ner.annotators;

import edu.colorado.clear.clinical.ner.util.SemEval2015Constants;
import org.apache.ctakes.typesystem.type.syntax.BaseToken;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.cas.CASException;
import org.apache.uima.fit.util.JCasUtil;
import org.apache.uima.jcas.JCas;
import org.cleartk.classifier.CleartkAnnotator;
import org.cleartk.classifier.Feature;
import org.cleartk.classifier.Instance;
import org.cleartk.semeval2015.type.DiseaseDisorderAttribute;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by jamesgung on 12/20/14.
 */
public class AttributeNormalizer extends CleartkAnnotator<String>
{

	public void process(JCas jCas) throws AnalysisEngineProcessException
	{
		JCas view = jCas;
		if (!this.isTraining())
		{
			try
			{
				view = jCas.getView(SemEval2015Constants.APP_VIEW);
			} catch (CASException e)
			{
				e.printStackTrace();
			}
		}

		for (DiseaseDisorderAttribute span: JCasUtil.select(view, DiseaseDisorderAttribute.class))
		{
			List<Feature> features = new ArrayList<>();
			String coveredText = span.getCoveredText().toLowerCase();
			String type = span.getAttributeType();
			features.add(new Feature("*FULLTEXT",coveredText));
			for (BaseToken t: JCasUtil.selectCovered(view, BaseToken.class, span))
			{
				String normalized = t.getNormalizedForm();
				if (normalized == null)
					normalized = "";
				features.add(new Feature("*UNIGRAM", normalized));
			}
			for (Feature f: features)
			{
				f.setName(f.getName().concat("*" + type));
			}

			if (this.isTraining()) {
				Instance<String> instance = new Instance<>(span.getNorm(), features);
//				System.out.println("span: " + span.getCoveredText());
//				for (Feature f: features)
//				{
//					System.out.println("\t" + f.getName() + "\t" + f.getValue());
//				}
				this.dataWriter.write(instance);

			} else {
				String norm = this.classifier.classify(features);
				span.setNorm(norm);
			}
		}

	}


}
