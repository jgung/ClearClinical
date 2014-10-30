package edu.colorado.clear.clinical.ner.annotators;

import edu.colorado.clear.clinical.ner.util.SemEval2015Constants;

import org.apache.ctakes.typesystem.type.refsem.OntologyConcept;
import org.apache.ctakes.typesystem.type.syntax.BaseToken;
import org.apache.ctakes.typesystem.type.textsem.IdentifiedAnnotation;
import org.apache.ctakes.typesystem.type.textspan.Sentence;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.cas.CASException;
import org.apache.uima.cas.FeatureStructure;
import org.apache.uima.jcas.JCas;
import org.apache.uima.jcas.cas.FSArray;
import org.uimafit.component.JCasAnnotator_ImplBase;
import org.uimafit.util.JCasUtil;

public class CopySentencesAndTokensAnnotator extends JCasAnnotator_ImplBase
{
	@Override
	public void process(JCas jCas) throws AnalysisEngineProcessException
	{
		JCas applicationView = null;

		try
		{
			applicationView = jCas.getView(SemEval2015Constants.APP_VIEW);
		} catch (CASException e)
		{
			e.printStackTrace();
		}

		for (Sentence s : JCasUtil.select(jCas, Sentence.class))
		{
			Sentence sCopy = new Sentence(applicationView, s.getBegin(), s.getEnd());
			sCopy.setSegmentId(s.getSegmentId());
			sCopy.setSentenceNumber(s.getSentenceNumber());
			sCopy.addToIndexes(applicationView);
			for (BaseToken t : JCasUtil.selectCovered(jCas, BaseToken.class, s))
			{
				BaseToken tCopy = new BaseToken(applicationView, t.getBegin(), t.getEnd());
				tCopy.setPartOfSpeech(t.getPartOfSpeech());
				tCopy.setLemmaEntries(t.getLemmaEntries());
				tCopy.setNormalizedForm(t.getNormalizedForm());
				tCopy.setTokenNumber(t.getTokenNumber());
				tCopy.addToIndexes(applicationView);
			}
		}
		for (IdentifiedAnnotation ia : JCasUtil.select(jCas, IdentifiedAnnotation.class))
		{
			//I'm afraid to clone this stuff... following methodology above
			IdentifiedAnnotation iCopy = new IdentifiedAnnotation(applicationView, ia.getBegin(), ia.getEnd());
			iCopy.setConditional(ia.getConditional());
			iCopy.setConfidence(ia.getConfidence());
			iCopy.setDiscoveryTechnique(ia.getDiscoveryTechnique());
			iCopy.setGeneric(ia.getGeneric());
			iCopy.setHistoryOf(ia.getHistoryOf());
			iCopy.setPolarity(ia.getPolarity());
			iCopy.setSegmentID(ia.getSegmentID());
			iCopy.setSubject(ia.getSubject());
			iCopy.setUncertainty(ia.getUncertainty());
			FSArray oarray = ia.getOntologyConceptArr();
			if(oarray!=null) {
				FSArray fsaCopy = new FSArray(applicationView,oarray.size());
				for(int i=0;i<oarray.size();i++){ 
					OntologyConcept oc = (OntologyConcept) oarray.get(i);
					fsaCopy.set(i, oc);
				}
				iCopy.setOntologyConceptArr(oarray);
			}
			iCopy.addToIndexes(applicationView);
		}
	}
}
