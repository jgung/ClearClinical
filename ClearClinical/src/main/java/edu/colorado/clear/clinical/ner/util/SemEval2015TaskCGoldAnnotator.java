package edu.colorado.clear.clinical.ner.util;

import edu.colorado.clear.clinical.ner.annotators.DisjointSpanAnnotator;
import org.apache.commons.io.FileUtils;
import org.apache.ctakes.typesystem.type.structured.DocumentID;
import org.apache.uima.UimaContext;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.cas.CASException;
import org.apache.uima.jcas.JCas;
import org.apache.uima.resource.ResourceInitializationException;
import org.cleartk.semeval2015.type.DiseaseDisorderAttribute;
import org.cleartk.semeval2015.type.DisorderSpan;
import org.uimafit.component.JCasAnnotator_ImplBase;
import org.uimafit.descriptor.ConfigurationParameter;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class SemEval2015TaskCGoldAnnotator extends JCasAnnotator_ImplBase
{

	public static final String PARAM_TRAINING = "training";
	public static final String PARAM_CUI_MAP = "cuiMap";
	public static final int dd_doc = 0;
	public static final int dd_spans = 1;
	public static final int dd_cui = 2;
	public static final int ni_norm = 3;
	public static final int ni_cue = 4;
	public static final int sc_norm = 5;
	public static final int sc_cue = 6;
	public static final int ui_norm = 7;
	public static final int ui_cue = 8;
	public static final int cc_norm = 9;
	public static final int cc_cue = 10;
	public static final int sv_norm = 11;
	public static final int sv_cue = 12;
	public static final int co_norm = 13;
	public static final int co_cue = 14;
	public static final int gc_norm = 15;
	public static final int gc_cue = 16;
	public static final int bl_norm = 17;
	public static final int bl_cue = 18;
	public static final int totalFields = 19;
	public static boolean VERBOSE = false;
	public static HashMap<String, String> stringCUIMap;
	@ConfigurationParameter(
			name = PARAM_TRAINING,
			description = "indicates whether we should build a CUI map using this annotator")
	protected boolean training = true;
	@ConfigurationParameter(
			name = PARAM_CUI_MAP,
			description = "file to read CUI map from in testing and application")
	protected String cuiMap = null;

	public static void writeMapToFile(HashMap<String, String> stringCUIMap, File outputFile)
	{
		StringBuilder output = new StringBuilder();
		for (String key : stringCUIMap.keySet())
			output.append(key).append("\t").append(stringCUIMap.get(key)).append("\n");

		try
		{
			FileUtils.writeStringToFile(outputFile, output.toString());
		} catch (IOException e)
		{
			e.printStackTrace();
		}
	}

	public static HashMap<String, String> getMap(File outputFile)
	{
		String input;
		HashMap<String, String> cuiMap = new HashMap<>();
		try
		{
			input = FileUtils.readFileToString(outputFile);
		} catch (IOException e)
		{
			System.err.println("Unable to read text-cui map file: " + outputFile.getPath());
			return cuiMap;
		}
		for (String line : input.split("\n"))
		{
			String[] fields = line.split("\t");
			cuiMap.put(fields[0], fields[1]);
		}
		return cuiMap;
	}

	public void initialize(UimaContext context) throws ResourceInitializationException
	{
		super.initialize(context);
		if (training)
			stringCUIMap = new HashMap<>();
		else
			stringCUIMap = getMap(new File(cuiMap));
	}

	public void process(JCas jCas) throws AnalysisEngineProcessException
	{

		JCas pipedView = null;
		try
		{
			pipedView = jCas.getView(SemEval2015Constants.PIPED_VIEW);
		} catch (CASException e)
		{
			e.printStackTrace();
			System.exit(0);
		}

		List<List<DisorderSpan>> disjointSpans = new ArrayList<>();
		List<DisorderSpan> usedSpans = new ArrayList<>();
		String docId = "";

		for (String line : pipedView.getDocumentText().split("\n"))
		{

			String[] fields = line.split("\\|");
			if (fields.length < totalFields)
			{
				System.out.println("Wrong format: " + line);
				continue;
			}
			docId = fields[dd_doc];
			String cui = fields[dd_cui];
			String[] ddSpans = fields[dd_spans].split(",");

			ArrayList<DisorderSpan> spans = new ArrayList<>();
			String text = "";
			try
			{


				for (String ddSpan : ddSpans)
				{
					String[] startBegin = ddSpan.split("-");
					int begin = Integer.parseInt(startBegin[0]);
					int end = Integer.parseInt(startBegin[1]);

					DisorderSpan disorder = null;
					for (DisorderSpan s : usedSpans)
					{
						if (s.getBegin() == begin && s.getEnd() == end)
						{
							disorder = s;
							break;
						}
					}
					if (disorder == null)
					{
						disorder = new DisorderSpan(jCas, begin, end);
						disorder.setChunk("");
						disorder.setCui(cui);
						disorder.addToIndexes(jCas);
						usedSpans.add(disorder);
					}
					spans.add(disorder);
					String disorderText = disorder.getCoveredText().trim().toLowerCase();
					text += disorderText + " ";
				}
				text = text.trim();
				if (training)
				{
					text = text.replaceAll("[\\s\\r\\n]", " "); // replace any newline characters and whatnot
					text = text.replaceAll("\\s+", " ");
					stringCUIMap.put(text, cui);
				}
				if (spans.size() > 1) /* multi-span disorder */
				{
					disjointSpans.add(spans);
				}

			/* Extract attributes */
				String blNorm = fields[bl_norm];
				String blOffsets = fields[bl_cue];
				if (!blOffsets.equals("null"))
				{
					String[] offsets = blOffsets.split("-");
					int begin = Integer.parseInt(offsets[0]);
					int end = Integer.parseInt(offsets[1]);
					DiseaseDisorderAttribute bl = new DiseaseDisorderAttribute(jCas, begin, end);
					bl.setNorm(blNorm);
					bl.setAttributeType("bodyLocation");
					bl.addToIndexes();
				}
				String coNorm = fields[co_norm];
				String coOffsets = fields[co_cue];
				if (!coOffsets.equals("null"))
				{
					String[] offsets = coOffsets.split("-");
					int begin = Integer.parseInt(offsets[0]);
					int end = Integer.parseInt(offsets[1]);
					DiseaseDisorderAttribute co = new DiseaseDisorderAttribute(jCas, begin, end);
					co.setNorm(coNorm);
					co.setAttributeType("conditionalClass");
					co.addToIndexes();
				}
				String ccNorm = fields[cc_norm];
				String ccOffsets = fields[cc_cue];
				if (!ccOffsets.equals("null"))
				{
					String[] offsets = ccOffsets.split("-");
					int begin = Integer.parseInt(offsets[0]);
					int end = Integer.parseInt(offsets[1]);
					DiseaseDisorderAttribute cc = new DiseaseDisorderAttribute(jCas, begin, end);
					cc.setNorm(ccNorm);
					cc.setAttributeType("courseClass");
					cc.addToIndexes();
				}
				String gcNorm = fields[gc_norm];
				String gcOffsets = fields[gc_cue];
				if (!gcOffsets.equals("null"))
				{
					String[] offsets = gcOffsets.split("-");
					int begin = Integer.parseInt(offsets[0]);
					int end = Integer.parseInt(offsets[1]);
					DiseaseDisorderAttribute gc = new DiseaseDisorderAttribute(jCas, begin, end);
					gc.setNorm(gcNorm);
					gc.setAttributeType("genericClass");
					gc.addToIndexes();
				}
				String niNorm = fields[ni_norm];
				String niOffsets = fields[ni_cue];
				if (!niOffsets.equals("null"))
				{
					String[] offsets = niOffsets.split("-");
					int begin = Integer.parseInt(offsets[0]);
					int end = Integer.parseInt(offsets[1]);
					DiseaseDisorderAttribute ni = new DiseaseDisorderAttribute(jCas, begin, end);
					ni.setNorm(niNorm);
					ni.setAttributeType("negationIndicator");
					ni.addToIndexes();
				}
				String svNorm = fields[sv_norm];
				String svOffsets = fields[sv_cue];
				if (!svOffsets.equals("null"))
				{
					String[] offsets = svOffsets.split("-");
					int begin = Integer.parseInt(offsets[0]);
					int end = Integer.parseInt(offsets[1]);
					DiseaseDisorderAttribute sv = new DiseaseDisorderAttribute(jCas, begin, end);
					sv.setNorm(svNorm);
					sv.setAttributeType("severityClass");
					sv.addToIndexes();
				}
				String scNorm = fields[sc_norm];
				String scOffsets = fields[sc_cue];
				if (!scOffsets.equals("null"))
				{
					String[] offsets = scOffsets.split("-");
					int begin = Integer.parseInt(offsets[0]);
					int end = Integer.parseInt(offsets[1]);
					DiseaseDisorderAttribute sc = new DiseaseDisorderAttribute(jCas, begin, end);
					sc.setNorm(scNorm);
					sc.setAttributeType("subjectClass");
					sc.addToIndexes();
				}
				String uiNorm = fields[ui_norm];
				String uiOffsets = fields[ui_cue];
				if (!uiOffsets.equals("null"))
				{
					String[] offsets = uiOffsets.split("-");
					int begin = Integer.parseInt(offsets[0]);
					int end = Integer.parseInt(offsets[1]);
					DiseaseDisorderAttribute ui = new DiseaseDisorderAttribute(jCas, begin, end);
					ui.setNorm(uiNorm);
					ui.setAttributeType("uncertaintyIndicator");
					ui.addToIndexes();
				}
			} catch (NumberFormatException e)
			{
				System.out.println("Piped format error in line: " + line);
				e.printStackTrace();
			}
		}
		/* Add relations for multi-span disorders */
		for (List<DisorderSpan> multiSpanDisorder : disjointSpans)
		{
			while (multiSpanDisorder.size() > 1)
			{
				DisorderSpan arg1 = multiSpanDisorder.remove(0);
				DisorderSpan arg2 = multiSpanDisorder.get(0);
				DisjointSpanAnnotator.createRelation(jCas, arg1, arg2, SemEval2015GoldAnnotator.DISJOINT_SPAN);
				if (VERBOSE)
					System.out.println("Added relation: " + arg1.getCoveredText() + "--" + arg2.getCoveredText());
			}
		}
		/* add doc id for output purposes */
		DocumentID id = new DocumentID(jCas);
		id.setDocumentID(docId);
		id.addToIndexes();
	}

}
