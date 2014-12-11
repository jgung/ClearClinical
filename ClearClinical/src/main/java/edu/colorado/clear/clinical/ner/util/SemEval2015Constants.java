package edu.colorado.clear.clinical.ner.util;

import java.util.Hashtable;

import org.apache.uima.cas.CAS;

public class SemEval2015Constants
{

	public static final String APP_VIEW = "APPLICATION_VIEW";
	public static final String PIPED_VIEW = "PIPE_VIEW";
	public static final String GOLD_VIEW = CAS.NAME_DEFAULT_SOFA;

	public static final String OUTPUT_SEPERATOR = "|";
	public static final int TOTAL_FIELDS = 19; //22 in previous version

	public static final String NEGATION_RELATION = "negationIndicator";
	public static final String SUBJECT_RELATION = "subjectClass";
	public static final String UNCERTAINITY_RELATION = "uncertaintyIndicator";
	public static final String COURSE_RELATION = "courseClass";
	public static final String SEVERITY_RELATION = "severityClass";
	public static final String CONDITIONAL_RELATION = "conditionalClass";
	public static final String GENERIC_RELATION = "genericClass";
	public static final String BODY_RELATION = "bodyLocation";
	public static final String DOCTIME_RELATION = "doctimeClass";
	public static final String TEMPORAL_RELATION = "temporalClass";

	public static final Hashtable<String,String> defaultNorms = new Hashtable<String,String>();
	
	static {
		defaultNorms.put(NEGATION_RELATION, "no");
		defaultNorms.put(SUBJECT_RELATION, "patient");
		defaultNorms.put(UNCERTAINITY_RELATION, "no");
		defaultNorms.put(COURSE_RELATION, "unmarked");
		defaultNorms.put(SEVERITY_RELATION, "unmarked");
		defaultNorms.put(CONDITIONAL_RELATION, "false");
		defaultNorms.put(GENERIC_RELATION, "false");
		defaultNorms.put(BODY_RELATION, "null");
		defaultNorms.put(DOCTIME_RELATION, "unknown");
		defaultNorms.put(TEMPORAL_RELATION, "none");
	}
	

	
}
