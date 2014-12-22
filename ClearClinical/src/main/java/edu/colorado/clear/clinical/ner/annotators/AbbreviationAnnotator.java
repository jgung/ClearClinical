package edu.colorado.clear.clinical.ner.annotators;

import org.apache.commons.io.FileUtils;
import org.apache.ctakes.typesystem.type.syntax.BaseToken;
import org.apache.ctakes.typesystem.type.textspan.Sentence;
import org.apache.uima.UimaContext;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.jcas.JCas;
import org.apache.uima.resource.ResourceInitializationException;
import org.cleartk.util.ViewURIUtil;
import org.uimafit.component.JCasAnnotator_ImplBase;
import org.uimafit.descriptor.ConfigurationParameter;
import org.uimafit.util.JCasUtil;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;

public class AbbreviationAnnotator extends JCasAnnotator_ImplBase
{

	public static final String PARAM_FILE = "file";
	protected HashMap<String, String> abbrMap = new HashMap<>();
	@ConfigurationParameter(
			name = PARAM_FILE,
			description = "file containing abbreviation mappings")
	private File file = null;

	public void initialize(UimaContext context) throws ResourceInitializationException
	{
		super.initialize(context);
		try
		{
			String abbrStr = FileUtils.readFileToString(file);
			for (String line : abbrStr.split("\n"))
			{
				String abbr = line.substring(0, line.indexOf(" ")).trim();
				String mapping = line.substring(line.indexOf(" ") + 1).trim();
				abbrMap.put(abbr, mapping);
			}
		} catch (IOException e)
		{
			e.printStackTrace();
		}

	}

	public void process(JCas jCas) throws AnalysisEngineProcessException
	{
		System.out.println(ViewURIUtil.getURI(jCas).getPath());
		for (Sentence s : JCasUtil.select(jCas, Sentence.class))
		{
			for (BaseToken t : JCasUtil.selectCovered(jCas, BaseToken.class, s))
			{
				String word = t.getCoveredText().toUpperCase();
				String mapping = abbrMap.get(word);
				if (mapping != null)
					t.setNormalizedForm(mapping);
				else
					t.setNormalizedForm(word.toLowerCase());
			}
		}
	}

}
