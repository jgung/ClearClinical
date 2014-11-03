package edu.colorado.clear.clinical.ner.annotators;

import edu.colorado.clear.clinical.ner.util.SemEval2015CollectionReader;
import edu.colorado.clear.clinical.ner.util.SemEval2015Constants;
import edu.colorado.clear.clinical.ner.util.UTSApiUtil;
import gov.nih.nlm.umls.uts.webservice.UiLabel;

import org.apache.commons.io.FileUtils;
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
import org.cleartk.semeval2015.type.DisorderSpan;
import org.cleartk.util.ViewURIUtil;
import org.uimafit.component.JCasAnnotator_ImplBase;
import org.uimafit.descriptor.ConfigurationParameter;
import org.uimafit.util.JCasUtil;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class SpanPostProcessorAnnotator extends JCasAnnotator_ImplBase
{

	public static final String PARAM_OUT_FILE_PATH = "outFilePath";
	public static final String PARAM_CUI_FILE_PATH = "cuiFilePath";
	public static String DISEASE_DISORDER = "Disease_Disorder";
	public static String CUI_LESS = "CUI-less";
	public static String PIPE = "||";
	public static boolean VERBOSE = false;
	@ConfigurationParameter(
			name = PARAM_OUT_FILE_PATH,
			description = "output file path")
	protected String outFilePath = null;
	@ConfigurationParameter(
			name = PARAM_CUI_FILE_PATH,
			description = "cui file path")
	protected String cuiFilePath = null;
	protected UTSApiUtil util;

	private static final Log log = LogFactory
			.getLog(SpanPostProcessorAnnotator.class);

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
		util = new UTSApiUtil();
	}

	public void process(JCas jCas) throws AnalysisEngineProcessException
	{
		String fileName = new File(ViewURIUtil.getURI(jCas).getPath()).getName();
		String docID = ((DocumentID) JCasUtil.select(jCas, DocumentID.class).toArray()[0]).getDocumentID();

		log.info("\t\tSpan post processing: " + docID);

		JCas applicationView = null;
		try
		{
			applicationView = jCas.getView(SemEval2015Constants.APP_VIEW);
		} catch (CASException e)
		{
			e.printStackTrace();
		}

		Collection<BinaryTextRelation> rels = JCasUtil.select(applicationView, BinaryTextRelation.class);
		List<DisorderSpan> usedSpans = new ArrayList<>();
		List<SortableDisorder> disorders = new ArrayList<>();

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
			log.info("Looking for cuis for binary text relation:"+text);
			if (DocIDAnnotator.stringCUIMap.containsKey(text))
			{
				cui = getCUI(text);
				log.info("Got CUI from direct map lookup:"+cui);
				add = true;
			}
			//} else {
			//Replacing lookup service with YTEX Word Sense Disambiguation
			//FIXME need to get overlapping, but no function in JCasUtil
			//for(IdentifiedAnnotation ia : JCasUtil.selectCovered(applicationView,IdentifiedAnnotation.class, arg1.getBegin(),arg1.getEnd())){
			/*
				for(IdentifiedAnnotation ia : JCasUtil.subiterate(applicationView,IdentifiedAnnotation.class, arg1,true,false)){
					log.info(text+" - COVERING IA:"+ia.getCoveredText()+" from: "+ia.getBegin()+"-"+ia.getEnd());
					FSArray fsArray = ia.getOntologyConceptArr();
					if(fsArray==null) continue;
					for(FeatureStructure featureStructure : fsArray.toArray()){
						OntologyConcept oc = (OntologyConcept) featureStructure;
						if(oc.getDisambiguated()==true) {
							cui = oc.getCode();
							String message = "YTEX Code:"+cui+" Scheme:"+oc.getCodingScheme()+" Score:"+oc.getScore()+" OID:"+oc.getOid();
							if(oc.getOid()!=null) message+=" OID:"+oc.getOid();
							if(oc.getOui()!=null) message+=" OUI:"+oc.getOui();
							log.info(message);
							add = true;
						} else {
							log.info("YTEX Rejected "+oc.getCode());

						}
					}
				}
			 */
			//Fallback to historic identification
			if(cui.equals("CUI_LESS")){
				List<UiLabel> list = util.filterConcepts(util.findConcepts(text));
				if (list.size() > 0)
				{
					cui = list.get(0).getLabel();
					log.info(text+" UTS CUI:"+cui);
					add = true;
				} else {
					log.info(text+" UTS CUI Lookup Failure");
				}
			}

			//}

			if (add)
			{
				arg1.setCui(cui);
				arg2.setCui(cui);
				usedSpans.add(arg1);
				usedSpans.add(arg2);
				disorders.add(new SortableDisorder(Arrays.asList(arg1, arg2)));
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
				log.info("LOOKING for cuis for this disorder text :"+text+" in this context:"+s.getCoveredText());
				if (DocIDAnnotator.stringCUIMap.containsKey(text))
				{
					cui = getCUI(text);
					log.info("Direct Map:"+cui);
					add = true;
				} else {
					List<UiLabel> list = util.filterConcepts(util.findConcepts(text));
					if (list.size() > 0)
					{
						cui = list.get(0).getLabel();
						log.info("UTS CUI:"+list.get(0).getUi()+" Label:"+cui);
						add = true;
					}
					int text_length = 0;
					for(IdentifiedAnnotation ia : JCasUtil.subiterate(applicationView,IdentifiedAnnotation.class, span,true,false)){
						FSArray fsArray = ia.getOntologyConceptArr();
						if(fsArray==null) continue;
						if(ia.getEnd()-ia.getBegin() < text_length) continue;
						text_length = ia.getEnd()-ia.getBegin();
						for(FeatureStructure featureStructure : fsArray.toArray()){
							OntologyConcept con = (OntologyConcept) featureStructure;
							UmlsConcept uc = null;
							if(con instanceof UmlsConcept) uc = (UmlsConcept) con;
							String message = "YTEX Got CUI:"+uc.getCui()+" from "+ia.getCoveredText()+"  ;SemType:"+uc.getTui()+" Score:"+uc.getScore();
							if(uc.getOid()!=null) message+=" OID:"+uc.getOid();
							if(uc.getOui()!=null) message+=" OUI:"+uc.getOui();
							if(uc.getDisambiguated()==true) {
								message = "YTEX Accepted "+message;
								if(uc.getScore()<1) continue;
								cui = uc.getCui();
								add = true;
								log.info(message);
							} else {
								message = "YTEX Rejected "+message;
							}
						}
					}
				}
				if (add)
				{
					span.setCui(cui);
					disorders.add(new SortableDisorder(Arrays.asList(span)));
				}
			}
		}

		/* Sort by increasing span index */
		Collections.sort(disorders);
		/* Write spans to pipe format */
		StringBuilder outFile = new StringBuilder();
		StringBuilder cuiFile = new StringBuilder();
		for (SortableDisorder d : disorders)
		{
			outFile.append(d.toString(docID));
			cuiFile.append(d.getText()).append("\n");
			if (VERBOSE)
			{
				if (d.spans.size() > 1)
					System.out.println("Disjoint Span:\t" + d.getText());
				else
					System.out.println(d.getText());
			}
		}

		fileName = fileName.replace(SemEval2015CollectionReader.TEXT_SUFFIX, SemEval2015CollectionReader.PIPE_SUFFIX);

		try
		{
			FileUtils.writeStringToFile(new File(cuiFilePath + File.separator + fileName), outFile.toString());
			FileUtils.writeStringToFile(new File(outFilePath + File.separator + fileName), cuiFile.toString());
		} catch (IOException e)
		{
			e.printStackTrace();
		}

	}

	public static class SortableDisorder implements Comparable<SortableDisorder>
	{

		protected List<DisorderSpan> spans;
		protected int start;
		protected int end;
		protected String text;

		public SortableDisorder(List<DisorderSpan> d)
		{
			this.spans = d;
			start = d.get(0).getBegin();
			end = d.get(0).getEnd();
			this.text = "";
			for (DisorderSpan span : spans)
			{
				if (span.getBegin() < start)
					start = span.getBegin();
				if (span.getEnd() > end)
					end = span.getEnd();
				this.text += span.getCoveredText().trim().toLowerCase().replace("\\s", " ") + " ";
			}
			this.text = this.text.trim();
		}

		public int compareTo(SortableDisorder o)
		{
			if (o.start == this.start)
				return this.end - o.end;

			return this.start - o.start;
		}

		public String toString(String docId)
		{
			String spanText = "";
			for (DisorderSpan s : spans)
			{
				spanText += PIPE + s.getBegin() + PIPE + s.getEnd();
			}
			String cui = getCUI(text);
			return docId + PIPE + DISEASE_DISORDER + PIPE + cui + spanText + "\n";
		}

		public String getText()
		{
			return text;
		}

	}


}
