package edu.colorado.clear.clinical.ner.annotators;

import org.apache.ctakes.typesystem.type.syntax.BaseToken;
import org.apache.ctakes.typesystem.type.textspan.Sentence;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.cas.CASException;
import org.apache.uima.jcas.JCas;
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
			applicationView = jCas.getView(DisorderSpanAnnotator.APP_VIEW);
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
	}
}
