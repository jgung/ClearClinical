/**
 * Based on DirectedDistanceExtractor
 */
package edu.colorado.clear.clinical.ner.features.relfeatures;

import org.apache.uima.jcas.JCas;
import org.apache.uima.jcas.tcas.Annotation;
import org.cleartk.classifier.Feature;
import org.cleartk.score.type.ScoredAnnotation;
import org.cleartk.util.AnnotationUtil;
import org.uimafit.util.JCasUtil;

import java.util.Collections;
import java.util.List;

public class ScoredDistanceFeaturesExtractor implements RelationFeaturesExtractor
{
	  String name;
	  Class<? extends ScoredAnnotation> unitClass;

	  public ScoredDistanceFeaturesExtractor(String name, Class<? extends ScoredAnnotation> unitClass) {
	    this.name = name;
	    this.unitClass = unitClass;
	  }

	  public List<Feature> extract(JCas jCas, ScoredAnnotation annotation1, ScoredAnnotation annotation2) {
	    String featureName = Feature.createName(this.name, "ScoredDDistance", this.unitClass.getSimpleName());

	    Annotation firstAnnotation, secondAnnotation;
	    boolean negate = false;
	    if (annotation1.getBegin() <= annotation2.getBegin()) {
	      firstAnnotation = annotation1;
	      secondAnnotation = annotation2;
	    } else {
	      firstAnnotation = annotation2;
	      secondAnnotation = annotation1;
	      negate = true;
	    }

	    int featureValue = 0;

	    if (AnnotationUtil.overlaps(annotation1, annotation2)) {
	      featureValue = 0;
	    } else {
	      List<? extends ScoredAnnotation> annotations = JCasUtil.selectCovered(
	          jCas,
	          unitClass,
	          firstAnnotation.getEnd(),
	          secondAnnotation.getBegin());
	      for(ScoredAnnotation sa : annotations){
	    	 featureValue += sa.getScore(); 
	      }
	    }
	    if (negate)
	      featureValue = -featureValue;

	    return Collections.singletonList(new Feature(featureName, featureValue));
	  }
}