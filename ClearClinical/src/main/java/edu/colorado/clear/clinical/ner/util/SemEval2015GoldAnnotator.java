package edu.colorado.clear.clinical.ner.util;

import edu.colorado.clear.clinical.ner.annotators.DisjointSpanAnnotator;
import org.apache.commons.io.FileUtils;
import org.apache.ctakes.typesystem.type.structured.DocumentID;
import org.apache.uima.UimaContext;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.cas.CASException;
import org.apache.uima.jcas.JCas;
import org.apache.uima.resource.ResourceInitializationException;
import org.cleartk.semeval2015.type.DisorderSpan;
import org.cleartk.util.ViewURIUtil;
import org.uimafit.component.JCasAnnotator_ImplBase;
import org.uimafit.descriptor.ConfigurationParameter;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class SemEval2015GoldAnnotator extends JCasAnnotator_ImplBase
{

	public static final String PARAM_TRAINING = "training";
	public static final String PARAM_CUI_MAP = "cuiMap";
	public static String DISJOINT_SPAN = "dspan";
	public static HashMap<String, String> stringCUIMap;
	public static boolean VERBOSE = false;
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

		if (VERBOSE) System.out.println("GoldAnnotator: " + ViewURIUtil.getURI(jCas).getPath());

		JCas pipedView = null;
		try
		{
			pipedView = jCas.getView(SemEval2015CollectionReader.PIPED_VIEW);
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

			String[] fields = line.split("\\|\\|");
			docId = fields[0];
			String cui = fields[2];
			ArrayList<DisorderSpan> spans = new ArrayList<>();
			String text = "";

			for (int i = 3; i < fields.length - 1; i = i + 2)
			{
				int begin = Integer.parseInt(fields[i]);
				int end = Integer.parseInt(fields[i + 1]);

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

		}

		/* Add relations for multi-span disorders */
		for (List<DisorderSpan> multiSpanDisorder : disjointSpans)
		{
			while (multiSpanDisorder.size() > 1)
			{
				DisorderSpan arg1 = multiSpanDisorder.remove(0);
				DisorderSpan arg2 = multiSpanDisorder.get(0);
				DisjointSpanAnnotator.createRelation(jCas, arg1, arg2, DISJOINT_SPAN);
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
