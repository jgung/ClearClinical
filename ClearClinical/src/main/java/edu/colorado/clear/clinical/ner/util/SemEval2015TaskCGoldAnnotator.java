package edu.colorado.clear.clinical.ner.util;

import edu.colorado.clear.clinical.ner.annotators.DisjointSpanAnnotator;

import org.apache.commons.io.FileUtils;
import org.apache.ctakes.typesystem.type.structured.DocumentID;
import org.apache.uima.UimaContext;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.cas.CASException;
import org.apache.uima.jcas.JCas;
import org.apache.uima.jcas.cas.FSArray;
import org.apache.uima.resource.ResourceInitializationException;
import org.cleartk.semeval2015.type.DiseaseDisorder;
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
	public static final int dt_norm = 19;
	public static final int te_norm = 20;
	public static final int te_cue = 21;
	public static final int totalFields = SemEval2015Constants.TOTAL_FIELDS;
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


	@SuppressWarnings("unused")
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

		List<DisorderSpan> usedSpans = new ArrayList<>();
		List<DisorderSpan> prevSpans = new ArrayList<>();
		DiseaseDisorder prev_disease = null;
   		List<List<DisorderSpan>> disjointSpans = new ArrayList<>();
		String docId = "";

		for (String line : pipedView.getDocumentText().split("\n"))
		{

    		List<DiseaseDisorderAttribute> diseaseAtts = new ArrayList<>();
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
						//Should be using appView, need to copy in annotations to appView for testing
						disorder.setChunk("");
						disorder.setCui(cui);
						disorder.addToIndexes(jCas);
						usedSpans.add(disorder);
					}
					spans.add(disorder);
					String disorderText = disorder.getCoveredText().trim().toLowerCase();
					text += disorderText + " "; //Used only for CUI map
				}
				text = text.trim();
				if (training)
				{
					text = text.replaceAll("[\\s\\r\\n]", " "); // replace any newline characters and whatnot
					text = text.replaceAll("\\s+", " ");
					stringCUIMap.put(text, cui);
				}

				//Determine if we have seen this disease before
				//Required to handle situation where next line is NOT a new disease but an additional anatomical mapping
				
				/*
				boolean seen_before = true;
				for(int i=0;i<spans.size();i++){
					DisorderSpan cur = spans.get(i);
					if(prevSpans==null || !spanSeenBefore(cur,prevSpans)){
						seen_before = false;
						break;
					}
				}
				if(seen_before) {
					System.out.println(docId+" seen before!"+line); System.out.flush();
					List<DiseaseDisorderAttribute> temp = new ArrayList<DiseaseDisorderAttribute>();
					DiseaseDisorderAttribute prevbody = null;
					DiseaseDisorderAttribute prevcourse = null;
					DiseaseDisorderAttribute prevsubject = null;
					FSArray prevatts = prev_disease.getAttributes();
					for (int i=0;i<prevatts.size();i++){
						DiseaseDisorderAttribute cur = (DiseaseDisorderAttribute) prevatts.get(i);
						if(cur.getAttributeType().equals(SemEval2015Constants.BODY_RELATION)){
							prevbody = cur;
						} else if(cur.getAttributeType().equals(SemEval2015Constants.COURSE_RELATION)){
							prevcourse = cur;
						} else if(cur.getAttributeType().equals(SemEval2015Constants.SUBJECT_RELATION)){
							prevsubject = cur;
						}
					}
					while(temp.size()==0) {
						extractAttribute(jCas, temp, fields,
								bl_norm,bl_cue,SemEval2015Constants.BODY_RELATION);			
						if(temp.size()!=0 && prevbody!=null){
							DiseaseDisorderAttribute bodytest = temp.get(0);
							if(bodytest.getBegin()!=prevbody.getBegin()||
									bodytest.getEnd()!=prevbody.getEnd()) { 
								System.out.println("Same disease, different body location");
								break;
							}
						}
						extractAttribute(jCas, temp, fields,
								cc_norm,cc_cue,SemEval2015Constants.COURSE_RELATION);			
						if(temp.size()!=0 && prevcourse!=null){
							DiseaseDisorderAttribute coursetest = temp.get(0);
							if(coursetest.getBegin()!=prevcourse.getBegin()||
									coursetest.getEnd()!=prevcourse.getEnd()) { 
								System.out.println("Difference is course!");
								break;
							}
						}
						extractAttribute(jCas, temp, fields,
								sc_norm,sc_cue,SemEval2015Constants.SUBJECT_RELATION);			
						if(temp.size()!=0 && prevsubject!=null){
							DiseaseDisorderAttribute subjecttest = temp.get(0);
							if(subjecttest.getBegin()!=prevsubject.getBegin()||
									subjecttest.getEnd()!=prevsubject.getEnd()) { 
								System.out.println("Same disease, different subject!");
								break;
							}
						}
						System.out.println("Failed to find diff...");
						System.exit(0);
					}
					FSArray joineddiseaseAttributes = new FSArray(jCas,prev_disease.getAttributes().size()+1);
					FSArray prevdiseaseAttributes = prev_disease.getAttributes();
					for(int j=0;j<prevdiseaseAttributes.size();j++){
						joineddiseaseAttributes.set(j,prevdiseaseAttributes.get(j));
					}
					joineddiseaseAttributes.set(prev_disease.getAttributes().size(),temp.remove(0));
					prev_disease.setAttributes(joineddiseaseAttributes);
				}
				*/
				
				
				if (spans.size() > 1) /* multi-span disorder */
				{
					disjointSpans.add(spans);
				}

				//Set up disease
				DiseaseDisorder disease = new DiseaseDisorder(jCas);
				FSArray relSpans = new FSArray(jCas,spans.size());
				int min_begin=-1,max_end=-1;
				for(int i=0;i<spans.size();i++){
					DisorderSpan ds = spans.get(i);
					if(ds.getBegin()<min_begin || min_begin==-1) min_begin = ds.getBegin();
					if(ds.getEnd()>max_end ) max_end = ds.getEnd();
					relSpans.set(i, ds); 
				}
				disease.setSpans(relSpans);
				disease.setBegin(min_begin);
				disease.setEnd(max_end);
				disease.setCui(cui);
				disease.addToIndexes(jCas);

				/* Extract attributes */
				extractAttribute(jCas, diseaseAtts, fields,
						bl_norm,bl_cue,SemEval2015Constants.BODY_RELATION);
				extractAttribute(jCas, diseaseAtts, fields,
						co_norm,co_cue,SemEval2015Constants.CONDITIONAL_RELATION);
				extractAttribute(jCas, diseaseAtts, fields,
						gc_norm,gc_cue,SemEval2015Constants.GENERIC_RELATION);
				extractAttribute(jCas, diseaseAtts, fields,
						ni_norm,ni_cue,SemEval2015Constants.NEGATION_RELATION);
				extractAttribute(jCas, diseaseAtts, fields,
						sv_norm,sv_cue,SemEval2015Constants.SEVERITY_RELATION);
				extractAttribute(jCas, diseaseAtts, fields,
						sc_norm,sc_cue,SemEval2015Constants.SUBJECT_RELATION);
				extractAttribute(jCas, diseaseAtts, fields,
						ui_norm,ui_cue,SemEval2015Constants.UNCERTAINITY_RELATION);
				extractAttribute(jCas, diseaseAtts, fields,
						cc_norm,cc_cue,SemEval2015Constants.COURSE_RELATION);
				/*
				String ccNorm = fields[cc_norm];
				String ccOffsets = fields[cc_cue];
				//Hack to handle errors in training discharge task 2 data (03087-026480.pipe, 17644-017974.pipe,15230-012950.pipe )
				if(ccOffsets.equals("nul") || ccOffsets.equals("unmarked")) ccOffsets="null";
				if (!ccOffsets.equals("null"))
				{
					String[] offsets = ccOffsets.split("-");
					int begin = Integer.parseInt(offsets[0]);
					int end = Integer.parseInt(offsets[1]);
					DiseaseDisorderAttribute cc = new DiseaseDisorderAttribute(jCas, begin, end);
					cc.setNorm(ccNorm);
					cc.setAttributeType(SemEval2015Constants.COURSE_RELATION);
					cc.addToIndexes();
					diseaseAtts.add(cc);
				}
				*/
				
				if(totalFields>19) {
					extractAttribute(jCas, diseaseAtts, fields,
						te_norm,te_cue,SemEval2015Constants.TEMPORAL_RELATION);
					String dtNorm = fields[dt_norm];
					if (!dtNorm.equals(SemEval2015Constants.defaultNorms.get(SemEval2015Constants.DOCTIME_RELATION)))
					{
						int begin = 1;
						int end = jCas.getDocumentText().length()-1;
						DiseaseDisorderAttribute dt = new DiseaseDisorderAttribute(jCas, begin, end);
						dt.setNorm(dtNorm);
						dt.setAttributeType(SemEval2015Constants.DOCTIME_RELATION);
						dt.addToIndexes();
						diseaseAtts.add(dt);
					}
				}

				FSArray diseaseAttributes = new FSArray(jCas,diseaseAtts.size());
				for(int i=0;i<diseaseAtts.size();i++){
					diseaseAttributes.set(i,diseaseAtts.get(i));
				}
				disease.setAttributes(diseaseAttributes);
				//System.out.println("Disease ("+fields[1]+") in "+fields[0]+" set "+diseaseAttributes.size()+" attributes.");
				prevSpans = spans;
				prev_disease = disease;

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



	private void extractAttribute(JCas jCas,
			List<DiseaseDisorderAttribute> dAtts, String[] fields,
			int input_norm, int input_cue, String relation) {
		String blNorm = fields[input_norm];
		String blOffsets = fields[input_cue];
		if (!blOffsets.equals("null"))
		{
			DiseaseDisorderAttribute bl = new DiseaseDisorderAttribute(jCas);
			String[] commasplit = blOffsets.split(",");
			FSArray attspans = new FSArray(jCas,commasplit.length);
			for(int i=0;i<commasplit.length;i++){
				String[] offsets = commasplit[i].split("-");
				int begin = Integer.parseInt(offsets[0]);
				int end = Integer.parseInt(offsets[1]);
				if(i==0)bl.setBegin(begin);
				bl.setEnd(end);
				DisorderSpan ds = new DisorderSpan(jCas, begin, end);
				attspans.set(i, ds);
			}
			bl.setNorm(blNorm);
			bl.setAttributeType(relation);
			bl.setSpans(attspans);
			bl.addToIndexes();
			dAtts.add(bl);
		} 
	}


	/**
	 * Determine if span ds is present in prevSpans, if yes, return true
	 * @param ds
	 * @param prevSpans
	 * @return
	 */
	private boolean spanSeenBefore(DisorderSpan ds, List<DisorderSpan> prevSpans){
		for(int j=0;j<prevSpans.size();j++){
			DisorderSpan old = prevSpans.get(j);
			if(ds.getBegin()==old.getBegin() && ds.getEnd()==old.getEnd()) {
				System.out.println("See before:"+ds.getBegin()+"-"+ds.getEnd());
				return true;
			}
		}
		return false;
	}

}
