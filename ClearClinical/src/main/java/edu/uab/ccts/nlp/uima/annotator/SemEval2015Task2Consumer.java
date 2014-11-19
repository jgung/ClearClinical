package edu.uab.ccts.nlp.uima.annotator;

import java.io.File;
import java.util.List;

import org.apache.ctakes.ytex.uima.ApplicationContextHolder;
import org.apache.ctakes.ytex.uima.dao.SegmentRegexDao;
import org.apache.ctakes.ytex.uima.model.SegmentRegex;
import org.apache.uima.UimaContext;
import org.apache.uima.analysis_engine.AnalysisEngineDescription;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.jcas.JCas;
import org.apache.uima.resource.ResourceInitializationException;
import org.uimafit.component.JCasAnnotator_ImplBase;
import org.uimafit.descriptor.ConfigurationParameter;
import org.uimafit.factory.AnalysisEngineFactory;

/**
 * Writes out the ClearClinical results for SemEval2015 (Task 14) Task 2 (formerly Task C)
 * @author ozborn
 *
 */
public class SemEval2015Task2Consumer extends JCasAnnotator_ImplBase {
	
	public static String resourceDirPath = "src/main/resources/";

	public static final String PARAM_OUTPUT_DIRECTORY = "outputDir";
	@ConfigurationParameter(
			name = PARAM_OUTPUT_DIRECTORY,
			description = "Path to the output directory for Task 2",
			defaultValue="src/main/resources/template_results/")
	private String outputDir = resourceDirPath+"template_results/";

	
	public void initialize(UimaContext context) throws ResourceInitializationException
	{
		super.initialize(context);
		try {
			File out = new File(outputDir);
			if(!out.exists()) { 
				if(!out.mkdir()) System.err.println("Could not make directory "+outputDir); 
			}
		} catch (Exception e) {e.printStackTrace();}
	}
	
	@Override
	public void process(JCas aJCas) throws AnalysisEngineProcessException {
		

	}
	

	
	
	public static AnalysisEngineDescription createAnnotatorDescription()
		      throws ResourceInitializationException {

			return AnalysisEngineFactory.createPrimitiveDescription(
						        SemEval2015Task2Consumer.class,
						        SemEval2015Task2Consumer.PARAM_OUTPUT_DIRECTORY,
						        resourceDirPath+"semeval-2015-task-14/subtask-c/data");
	}

}
