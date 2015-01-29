package edu.colorado.clear.clinical.ner.annotators;

import edu.colorado.clear.clinical.ner.util.SemEval2015Constants;

import org.apache.commons.io.FileUtils;
import org.apache.ctakes.typesystem.type.structured.DocumentID;
import org.apache.uima.UimaContext;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.cas.CASException;
import org.apache.uima.cas.CASRuntimeException;
import org.apache.uima.jcas.JCas;
import org.apache.uima.resource.ResourceInitializationException;
import org.cleartk.util.ViewURIUtil;
import org.uimafit.component.JCasAnnotator_ImplBase;
import org.uimafit.descriptor.ConfigurationParameter;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;

/*
	Extract the report type based on the directory structure of the current file,
    set DocumentID to pipe file format identifier (based ony SemEval2014 Task 7 data)
 */
public class DocIDAnnotator extends JCasAnnotator_ImplBase
{

	public static final String DISCHARGE_SUMMARY = "DISCHARGE_SUMMARY";
	public static final String ECHO_REPORT = "ECHO_REPORT";
	public static final String RADIOLOGY_REPORT = "RADIOLOGY_REPORT";
	public static final String ECG_REPORT = "ECG_REPORT";
	public static final String IDENTIFIER_EXT = ".txt";
	public static final String PARAM_CUI_MAP_PATH = "cuiMapPath";
	public static HashMap<String, String> stringCUIMap;
	@ConfigurationParameter(
			name = PARAM_CUI_MAP_PATH,
			description = "cui map file path")
	protected String cuiMapPath = null;

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
		stringCUIMap = getMap(new File(cuiMapPath));
	}

	public void process(JCas jCas) throws AnalysisEngineProcessException
	{

		JCas appView = null;
		try { appView = jCas.getView(SemEval2015Constants.APP_VIEW); } 
		catch (CASException e) {  e.printStackTrace();
		} catch (CASRuntimeException cre) {
			try {
				appView = jCas.createView(SemEval2015Constants.APP_VIEW);
			} catch (CASException e1) {
				e1.printStackTrace();
			}
		}

//		if (fileString.contains(DISCHARGE_SUMMARY))
//			newType = DISCHARGE_SUMMARY;
//		else if (fileString.contains(ECHO_REPORT))
//			newType = ECHO_REPORT;
//		else if (fileString.contains(RADIOLOGY_REPORT))
//			newType = RADIOLOGY_REPORT;
//		else if (fileString.contains(ECG_REPORT))
//			newType = ECG_REPORT;

		String name = new File(ViewURIUtil.getURI(jCas).getPath()).getName();
//		name = name.replace(SemEval2015CollectionReader.TEXT_SUFFIX, "");
//		name = name + "-" + newType + IDENTIFIER_EXT;
		/* add doc id for output purposes */
		DocumentID id = new DocumentID(appView);
		id.setDocumentID(name);
		id.addToIndexes();
	}
}
