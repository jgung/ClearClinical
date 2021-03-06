package edu.uab.ccts.nlp.uima.annotator;

import edu.colorado.clear.clinical.ner.util.SemEval2015Constants;
import org.apache.ctakes.typesystem.type.structured.DocumentID;
import org.apache.uima.UimaContext;
import org.apache.uima.analysis_engine.AnalysisEngineDescription;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.cas.CASException;
import org.apache.uima.jcas.JCas;
import org.apache.uima.jcas.cas.FSArray;
import org.apache.uima.resource.ResourceInitializationException;
import org.cleartk.semeval2015.type.DiseaseDisorder;
import org.cleartk.semeval2015.type.DiseaseDisorderAttribute;
import org.cleartk.semeval2015.type.DisorderRelation;
import org.cleartk.semeval2015.type.DisorderSpan;
import org.uimafit.component.JCasAnnotator_ImplBase;
import org.uimafit.descriptor.ConfigurationParameter;
import org.uimafit.factory.AnalysisEngineFactory;
import org.uimafit.util.JCasUtil;

import java.io.File;
import java.io.FileWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.List;

/**
 * Writes out the ClearClinical results for SemEval2015 (Task 14) Task 2 (formerly Task C)
 * @author ozborn
 *
 */
public class SemEval2015Task2Consumer extends JCasAnnotator_ImplBase {
	
	public static final String PARAM_OUTPUT_DIRECTORY = "outputDir";
	public static String resourceDirPath = "src/main/resources/";
	@ConfigurationParameter(
			name = PARAM_OUTPUT_DIRECTORY,
			description = "Path to the output directory for Task 2",
			defaultValue="src/main/resources/template_results/")
	private String outputDir = resourceDirPath+"template_results/";
	public static boolean VERBOSE = false;

	public static AnalysisEngineDescription createAnnotatorDescription()
			throws ResourceInitializationException
	{

		return AnalysisEngineFactory.createPrimitiveDescription(
				SemEval2015Task2Consumer.class,
				SemEval2015Task2Consumer.PARAM_OUTPUT_DIRECTORY,
				resourceDirPath + "semeval-2015-task-14/subtask-c/data");
	}
	
	public void initialize(UimaContext context) throws ResourceInitializationException
	{
		super.initialize(context);
		try {
			File out = new File(outputDir);
			if (!out.exists())
			{
				if (!out.mkdir()) System.out.println("Could not make directory " + outputDir);
			} else
			{
				if (VERBOSE) System.out.println(outputDir + " exists!");
			}
		} catch (Exception e)
		{
			e.printStackTrace();
		}
	}

	@Override
	public void process(JCas aJCas) throws AnalysisEngineProcessException
	{
		JCas appView = null, goldView = null;
		//Should be using appView, need to copy in annotations to appView for testing
		try
		{
			appView = aJCas.getView(SemEval2015Constants.APP_VIEW);
			goldView = aJCas.getView(SemEval2015Constants.GOLD_VIEW);

		} catch (CASException e)
		{
			e.printStackTrace();
		}

		String docid = null;
		for (DocumentID di : JCasUtil.select(appView, DocumentID.class))
		{
			docid = di.getDocumentID();
			break;
		}

		String filepath = outputDir + File.separator +
				docid.substring(0, docid.length() - 4) + "pipe";
		try
		{
			Writer writer = new FileWriter(filepath);
			for (DiseaseDisorder ds : JCasUtil.select(appView, DiseaseDisorder.class))
			{
				associateSpans(appView, ds);
				String results = getDiseaseDisorderSemEval2015Format(aJCas, docid, ds);
				if (VERBOSE) System.out.println(results);
				writer.write(results + "\n");
			}
			writer.close();
		} catch (Exception e)
		{
			e.printStackTrace();
		}
	}

	/**
	 * FIXME Need to handle multiple lines
	 */
	private String getDiseaseDisorderSemEval2015Format(JCas jcas, String docid, DiseaseDisorder dd)
	{
		StringBuffer output_lines = new StringBuffer(2000);
		output_lines.append(docid);
		output_lines.append(SemEval2015Constants.OUTPUT_SEPERATOR);
		FSArray spans = dd.getSpans();
		for (int i = 0; i < spans.size(); i++)
		{
			DisorderSpan ds = (DisorderSpan) spans.get(i);
			output_lines.append(ds.getBegin() + "-" + ds.getEnd());
			if (i != spans.size() - 1) output_lines.append(",");
//			System.out.print(ds.getCoveredText() + "\t");
		}
		output_lines.append(SemEval2015Constants.OUTPUT_SEPERATOR);
		output_lines.append(dd.getCui());
		output_lines.append(SemEval2015Constants.OUTPUT_SEPERATOR);
		FSArray atts = dd.getAttributes();
		output_lines.append(fetchAttributeString(atts, SemEval2015Constants.NEGATION_RELATION));
		output_lines.append(fetchAttributeString(atts, SemEval2015Constants.SUBJECT_RELATION));
		output_lines.append(fetchAttributeString(atts, SemEval2015Constants.UNCERTAINITY_RELATION));
		output_lines.append(fetchAttributeString(atts, SemEval2015Constants.COURSE_RELATION));
		output_lines.append(fetchAttributeString(atts, SemEval2015Constants.SEVERITY_RELATION));
		output_lines.append(fetchAttributeString(atts, SemEval2015Constants.CONDITIONAL_RELATION));
		output_lines.append(fetchAttributeString(atts, SemEval2015Constants.GENERIC_RELATION));
		output_lines.append(fetchAttributeString(atts, SemEval2015Constants.BODY_RELATION));
		//output_lines.append(fetchAttributeString(atts, SemEval2015Constants.DOCTIME_RELATION));
		//output_lines.append(fetchAttributeString(atts, SemEval2015Constants.TEMPORAL_RELATION));
//		System.out.println();
		return output_lines.toString();
	}

	/**
	 * Too bad UIMA doesn't have built in hashtables...
	 */
	private String fetchAttributeString(FSArray atts, String type)
	{
		String norm = SemEval2015Constants.defaultNorms.get(type);
		String cue = "null";
		if (atts != null)
		{
			for (int i = 0; i < atts.size(); i++)
			{
				DiseaseDisorderAttribute dda = (DiseaseDisorderAttribute) atts.get(i);
				if (type.equals(dda.getAttributeType()))
				{
					norm = dda.getNorm();
					if (!type.equals(SemEval2015Constants.DOCTIME_RELATION))
					{
						FSArray attspans = dda.getSpans();
						if (attspans == null)
						{
//							System.out.println(dda.getBegin() + " to " + dda.getEnd() + " has no atts!!!!");
							continue;
						}
						for (int j = 0; j < attspans.size(); j++)
						{
							DiseaseDisorderAttribute ds = (DiseaseDisorderAttribute) attspans.get(j);
							if (j == 0) cue = (ds.getBegin() + "-" + ds.getEnd());
							else
							{
								cue = cue + "," + ds.getBegin() + "-" + ds.getEnd();
							}
//							System.out.print(ds.getCoveredText() + "\t");

						}
						String out = norm + SemEval2015Constants.OUTPUT_SEPERATOR + cue;
//						if (type.equals(SemEval2015Constants.BODY_RELATION))
							out += SemEval2015Constants.OUTPUT_SEPERATOR;
						return out;
					} else if (!type.equals(SemEval2015Constants.BODY_RELATION)){
						return norm;
					} else
					{
						return norm + SemEval2015Constants.OUTPUT_SEPERATOR;
					}
				}
			}
		}
		return norm + SemEval2015Constants.OUTPUT_SEPERATOR + cue + SemEval2015Constants.OUTPUT_SEPERATOR;
	}

	public static void associateSpans(JCas jCas, DiseaseDisorder dd)
	{
		List<DiseaseDisorderAttribute> atts = new ArrayList<>();
		for (DisorderRelation rel: JCasUtil.select(jCas, DisorderRelation.class))
		{
			DisorderSpan s = (DisorderSpan) rel.getArg2().getArgument();

			for (DisorderSpan span : JCasUtil.select(dd.getSpans(), DisorderSpan.class))
			{
				if (span == s)
				{
					atts.add((DiseaseDisorderAttribute) rel.getArg1().getArgument());
				}
			}
		}
		FSArray relSpans = new FSArray(jCas, atts.size());
		int min_begin = -1, max_end = -1;
		for (int i = 0; i < atts.size(); i++)
		{
			DiseaseDisorderAttribute ds = atts.get(i);
			if (ds.getBegin() < min_begin || min_begin == -1) min_begin = ds.getBegin();
			if (ds.getEnd() > max_end) max_end = ds.getEnd();
			relSpans.set(i, ds);
		}
		dd.setAttributes(relSpans);
	}

}
