/**
 * Based on DirectedDistanceExtractor
 */
package edu.colorado.clear.clinical.ner.features.relfeatures;

import org.apache.ctakes.typesystem.type.syntax.BaseToken;
import org.apache.uima.cas.FeatureStructure;
import org.apache.uima.jcas.JCas;
import org.apache.uima.jcas.cas.FSList;
import org.apache.uima.jcas.cas.NonEmptyFSList;
import org.apache.uima.jcas.tcas.Annotation;
import org.cleartk.classifier.Feature;
import org.cleartk.score.type.ScoredAnnotation;
import org.cleartk.util.AnnotationUtil;
import org.uimafit.util.JCasUtil;

import edu.uab.ccts.nlp.uima.annotator.MutualInformationAnnotator;

import java.sql.Connection;
import java.util.Collections;
import java.util.List;

public class MutualInformationFeaturesExtractor implements RelationFeaturesExtractor
{
	  String name="MutInf";
	  Connection dbConnection = null;

	  public MutualInformationFeaturesExtractor(Connection conn){
	    dbConnection = conn;
	  }

	  public List<Feature> extract(JCas jCas, ScoredAnnotation annotation1, ScoredAnnotation annotation2) {
		  String featureName = this.name;
		  Double featureValue = 0.0;
		  List<BaseToken> covered_ones = JCasUtil.selectCovered(jCas, BaseToken.class, annotation1);
		  List<BaseToken> covered_twos = JCasUtil.selectCovered(jCas, BaseToken.class, annotation2);
		  BaseToken onehead = null, twohead = null;
		  if(covered_ones.size()>0) onehead = covered_ones.get(covered_ones.size()-1);
		  if(covered_twos.size()>0) twohead = covered_twos.get(covered_twos.size()-1);
		  if(onehead!=null && twohead != null) {
			  String onecover = onehead.getCoveredText();
			  String twocover = twohead.getCoveredText();
			  Integer joint_count = MutualInformationAnnotator.getBiTokenCount(dbConnection, "NLP_BIGRAM", onecover, twocover);
			  if(joint_count>0){
				  Integer first_count = MutualInformationAnnotator.getOneTokenCount(dbConnection, "NLP_UNIGRAM", onecover);
				  Integer second_count = MutualInformationAnnotator.getOneTokenCount(dbConnection, "NLP_UNIGRAM", twocover);
				  Integer all_uni = MutualInformationAnnotator.getTokenTotals(dbConnection, "NLP_UNIGRAM");
				  Integer all_bi = MutualInformationAnnotator.getTokenTotals(dbConnection, "NLP_BIGRAM");
				  featureValue = MutualInformationAnnotator.calculateMutualInformation(all_uni, all_bi, first_count, second_count, joint_count);
			  }
		  }
		  //System.out.println("MI is:"+featureValue+" for "+annotation1.getCoveredText()+" --- "+annotation2.getCoveredText());
				  // +"   ;;;  "+onehead+"   --- "+twohead);
	    return Collections.singletonList(new Feature(featureName, featureValue));
	  }
}