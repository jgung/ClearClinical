package edu.uab.ccts.nlp.uima.annotator;

import java.util.ArrayList;
import java.util.List;

import org.apache.ctakes.typesystem.type.textsem.IdentifiedAnnotation;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.cas.CASException;
import org.apache.uima.jcas.JCas;
import org.cleartk.semeval2015.type.DiseaseDisorder;
import org.cleartk.semeval2015.type.DiseaseDisorderAttribute;
import org.uimafit.component.JCasAnnotator_ImplBase;
import org.uimafit.descriptor.ConfigurationParameter;
import org.uimafit.util.JCasUtil;

import edu.colorado.clear.clinical.ner.util.SemEval2015Constants;

public class BaselineTemplateAnnotator extends JCasAnnotator_ImplBase {

	public static final String PARAM_VIEW = "diseaseDisorderView";
	@ConfigurationParameter(
			name = PARAM_VIEW,
			description = "View containing DiseaseDisorders",
			defaultValue= "PIPE_VIEW")
	private String ddView = SemEval2015Constants.GOLD_VIEW;


	/**
	 * Just looks for simple overlap and transfers CTAKES attributes to DiseaseDisorderAttribute
	 * Takes 1st one, doesn't do anything clever
	 */
	@Override
	public void process(JCas aJCas) throws AnalysisEngineProcessException {
		JCas theview = null;
		try { 
			theview = aJCas.getView(ddView);
		} catch (CASException e) { e.printStackTrace(); }

		for(DiseaseDisorder ds : JCasUtil.select(theview, DiseaseDisorder.class)) {	
			List<DiseaseDisorderAttribute> attlist = new ArrayList<DiseaseDisorderAttribute>();
			for (IdentifiedAnnotation ia : JCasUtil.selectCovered(theview,IdentifiedAnnotation.class, ds)){
				if(ia.getGeneric()==true) {
					DiseaseDisorderAttribute dda = new DiseaseDisorderAttribute(aJCas);
					dda.setBegin(ia.getBegin());
					dda.setEnd(ia.getEnd());
					dda.setNorm("true");
					dda.setAttributeType(SemEval2015Constants.GENERIC_RELATION);
					dda.addToIndexes();
					attlist.add(dda);
				}
				if(ia.getConditional()==true) {
					DiseaseDisorderAttribute dda = new DiseaseDisorderAttribute(aJCas);
					dda.setBegin(ia.getBegin());
					dda.setEnd(ia.getEnd());
					dda.setNorm("true");
					dda.setAttributeType(SemEval2015Constants.CONDITIONAL_RELATION);
					dda.addToIndexes();
					attlist.add(dda);
				}
				if(ia.getSubject()!=null) {
					DiseaseDisorderAttribute dda = new DiseaseDisorderAttribute(aJCas);
					dda.setBegin(ia.getBegin());
					dda.setEnd(ia.getEnd());
					dda.setNorm(ia.getSubject());
					dda.setAttributeType(SemEval2015Constants.CONDITIONAL_RELATION);
					dda.addToIndexes();
					attlist.add(dda);
				}
				if(ia.getUncertainty()==1) {
					DiseaseDisorderAttribute dda = new DiseaseDisorderAttribute(aJCas);
					dda.setBegin(ia.getBegin());
					dda.setEnd(ia.getEnd());
					dda.setNorm("yes");
					dda.setAttributeType(SemEval2015Constants.CONDITIONAL_RELATION);
					dda.addToIndexes();
					attlist.add(dda);
				}
				if(ia.getPolarity()==1) {
					DiseaseDisorderAttribute dda = new DiseaseDisorderAttribute(aJCas);
					dda.setBegin(ia.getBegin());
					dda.setEnd(ia.getEnd());
					dda.setNorm("yes");
					dda.setAttributeType(SemEval2015Constants.CONDITIONAL_RELATION);
					dda.addToIndexes();
					attlist.add(dda);
				}
			}
		}
	}

}
