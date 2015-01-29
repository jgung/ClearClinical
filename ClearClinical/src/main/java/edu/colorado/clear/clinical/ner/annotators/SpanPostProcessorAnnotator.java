package edu.colorado.clear.clinical.ner.annotators;

import edu.colorado.clear.clinical.ner.util.SemEval2015Constants;
import edu.colorado.clear.clinical.ner.util.UTSApiUtil;
import gov.nih.nlm.umls.uts.webservice.UiLabel;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.ctakes.typesystem.type.refsem.OntologyConcept;
import org.apache.ctakes.typesystem.type.refsem.UmlsConcept;
import org.apache.ctakes.typesystem.type.relation.BinaryTextRelation;
import org.apache.ctakes.typesystem.type.structured.DocumentID;
import org.apache.ctakes.typesystem.type.textsem.IdentifiedAnnotation;
import org.apache.ctakes.typesystem.type.textspan.Sentence;
import org.apache.uima.UimaContext;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.cas.CASException;
import org.apache.uima.cas.FeatureStructure;
import org.apache.uima.jcas.JCas;
import org.apache.uima.jcas.cas.FSArray;
import org.apache.uima.resource.ResourceInitializationException;
import org.cleartk.semeval2015.type.DiseaseDisorder;
import org.cleartk.semeval2015.type.DisorderSpan;
import org.cleartk.semeval2015.type.DisorderSpanRelation;
import org.uimafit.component.JCasAnnotator_ImplBase;
import org.uimafit.descriptor.ConfigurationParameter;
import org.uimafit.util.JCasUtil;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class SpanPostProcessorAnnotator extends JCasAnnotator_ImplBase
{
	public static final String PARAM_CUI_FILE_PATH = "cuiFilePath";
	public static final String PARAM_USE_YTEX = "useYtex";
	private static final Log log = LogFactory
			.getLog(SpanPostProcessorAnnotator.class);
	public static String CUI_LESS = "CUI-less";
	public static boolean VERBOSE = false;
	@ConfigurationParameter(
			name = PARAM_CUI_FILE_PATH,
			description = "cui file path")
	protected String cuiFilePath = null;
	protected boolean useYtex = false;
	@ConfigurationParameter(
			name = PARAM_USE_YTEX,
			description = "whether to use ytex for CUI mapping / disambiguation")
	protected UTSApiUtil util;

	public static String getCUI(String coveredText)
	{
		String cui = CUI_LESS;
		if (DocIDAnnotator.stringCUIMap != null)
		{
			if (DocIDAnnotator.stringCUIMap.containsKey(coveredText))
			{
				cui = DocIDAnnotator.stringCUIMap.get(coveredText);
			}
		}
		return cui;
	}

	public void initialize(UimaContext context) throws ResourceInitializationException
	{
		super.initialize(context);
		if(!useYtex) util = new UTSApiUtil();
	}

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
		String docID = ((DocumentID) JCasUtil.select(applicationView, DocumentID.class).toArray()[0]).getDocumentID();
		log.info("\t\tSpan post processing: " + docID);


		Collection<DisorderSpanRelation> rels = JCasUtil.select(applicationView, DisorderSpanRelation.class);
		List<DisorderSpan> usedSpans = new ArrayList<>();

		for (BinaryTextRelation rel : rels)
		{
			DisorderSpan arg1 = (DisorderSpan) rel.getArg1().getArgument();
			DisorderSpan arg2 = (DisorderSpan) rel.getArg2().getArgument();

			String text = arg1.getCoveredText() + " " + arg2.getCoveredText();
			text = text.trim().toLowerCase();
			text = text.replaceAll("[\\s\\r\\n]", " "); // replace any newline characters and whatnot
			text = text.replaceAll("\\s+", " ");
			String cui = CUI_LESS;
			boolean add = false;
			log.debug("Looking for cuis for binary text relation:" + text);
			if (DocIDAnnotator.stringCUIMap.containsKey(text))
			{
				cui = getCUI(text);
				log.debug("Got CUI from direct map lookup:" + cui);
				add = true;
			} else
			{
				if(!useYtex) {
					List<UiLabel> list = util.filterConcepts(util.findConcepts(text));
					if (list.size() > 0)
					{
						cui = list.get(0).getUi();
						log.debug(text + " UTS CUI:" + cui);
						add = true;
					} else
					{
						log.debug(text + " UTS CUI Lookup Failure");
					}
				}
			}

			DiseaseDisorder disorder = new DiseaseDisorder(applicationView);
			FSArray relSpans = new FSArray(applicationView, 2);
			relSpans.set(0, arg1);
			relSpans.set(1, arg2);
			disorder.setSpans(relSpans);
			disorder.setBegin(arg1.getBegin());
			disorder.setEnd(arg2.getEnd());
			disorder.addToIndexes();

			if (add)
			{
				arg1.setCui(cui);
				arg2.setCui(cui);
				usedSpans.add(arg1);
				usedSpans.add(arg2);
				disorder.setCui(cui);
			}
		}

		for (Sentence s : JCasUtil.select(applicationView, Sentence.class))
		{

			List<DisorderSpan> spans = JCasUtil.selectCovered(applicationView, DisorderSpan.class, s);
			for (DisorderSpan span : spans)
			{
				if (usedSpans.contains(span))
					continue;
				String text = span.getCoveredText();
				text = text.trim().toLowerCase();
				text = text.replaceAll("[\\s\\r\\n]", " "); // replace any newline characters and whatnot
				text = text.replaceAll("\\s+", " ");
				String cui = CUI_LESS;
				boolean add = false;
				log.debug("LOOKING for cuis for this disorder text :" + text + " in this context:" + s.getCoveredText());
				if (DocIDAnnotator.stringCUIMap.containsKey(text))
				{
					cui = getCUI(text);
					log.debug("Direct Map:" + cui);
					add = true;
				} else
				{
					if(!useYtex) {
						List<UiLabel> list = util.filterConcepts(util.findConcepts(text));
						if (list.size() > 0)
						{
							cui = list.get(0).getUi();
							log.debug("UTS CUI:" + list.get(0).getUi() + " Label:" + cui);
							add = true;
						}
					} else {
						int text_length = 0;
						for (IdentifiedAnnotation ia : JCasUtil.subiterate(applicationView, IdentifiedAnnotation.class, span, true, false))
						{
							FSArray fsArray = ia.getOntologyConceptArr();
							if (fsArray == null) continue;
							if (ia.getEnd() - ia.getBegin() < text_length) continue;
							text_length = ia.getEnd() - ia.getBegin();
							for (FeatureStructure featureStructure : fsArray.toArray())
							{
								OntologyConcept con = (OntologyConcept) featureStructure;
								UmlsConcept uc = null;
								if (con instanceof UmlsConcept) uc = (UmlsConcept) con;
								String message = "YTEX Got CUI:" + uc.getCui() + " from " + ia.getCoveredText() + "  ;SemType:" + uc.getTui() + " Score:" + uc.getScore();
								if (uc.getOid() != null) message += " OID:" + uc.getOid();
								if (uc.getOui() != null) message += " OUI:" + uc.getOui();
								if (uc.getDisambiguated() == true)
								{
									message = "YTEX Accepted " + message;
									if (uc.getScore() < 1) continue;
									cui = uc.getCui();
									add = true;
									log.info(message);
								} else
								{
									message = "YTEX Rejected " + message;
								}
							}
						}
					}
				}
				DiseaseDisorder disorder = new DiseaseDisorder(applicationView);
				FSArray relSpans = new FSArray(applicationView, 1);
				relSpans.set(0, span);
				disorder.setSpans(relSpans);
				disorder.setBegin(span.getBegin());
				disorder.setEnd(span.getEnd());
				disorder.addToIndexes();
				if (add)
				{
					disorder.setCui(cui);
					span.setCui(cui);
				}
			}
		}

	}



}
