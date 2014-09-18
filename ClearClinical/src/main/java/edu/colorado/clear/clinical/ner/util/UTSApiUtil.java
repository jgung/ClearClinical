package edu.colorado.clear.clinical.ner.util;

import gov.nih.nlm.umls.uts.webservice.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/*
 * Used to query UMLS Metathesaurus. Not intended for external use.
 */
public class UTSApiUtil
{

	/*
	    DISO|Disorders|T019|Congenital Abnormality
		DISO|Disorders|T020|Acquired Abnormality
		DISO|Disorders|T037|Injury or Poisoning
		DISO|Disorders|T046|Pathologic Function
		DISO|Disorders|T047|Disease or Syndrome
		DISO|Disorders|T048|Mental or Behavioral Dysfunction
		DISO|Disorders|T049|Cell or Molecular Dysfunction
  		DISO|Disorders|T050|Experimental Model of Disease
		DISO|Disorders|T191|Neoplastic Process
		DISO|Disorders|T190|Anatomical Abnormality
		DISO|Disorders|T184|Sign or Symptom
	 */
	public static List<String> tuis = Arrays.asList("T019", "T020",
			"T037", "T047", "T048", "T049", "T050", "T191", "T190", "T184");
	// DISO|Disorders|T033|Finding
	public static String finding = "T033";
	private static String username = "gungjm";
	private static String password = "cTAKESN3R";
	private static String serviceName = "http://umlsks.nlm.nih.gov";
	private static String umlsRelease = "2012AB";
	private static String SOURCE_FILTER = "SNOMEDCT";
	private static String CUILESS = "CUI-less";
	private String proxyGrantTicket;
	private UtsWsSecurityController securityService;
	private UtsWsContentController utsContentService;
	private UtsWsFinderController utsFinderService;

	public UTSApiUtil()
	{
		utsContentService = (new UtsWsContentControllerImplService()).getUtsWsContentControllerImplPort();
		securityService = (new UtsWsSecurityControllerImplService()).getUtsWsSecurityControllerImplPort();//create the reference variables
		//instantiate and handshake
		utsFinderService = (new UtsWsFinderControllerImplService())
				.getUtsWsFinderControllerImplPort();
		try
		{
			proxyGrantTicket = securityService.getProxyGrantTicket(username, password);
		} catch (UtsFault_Exception e)
		{
			e.printStackTrace();
		}
	}

	public static void main(String... args)
	{
		UTSApiUtil util = new UTSApiUtil();
		//		ConceptDTO result1 = util.getConcept("C0018787");
		//		System.out.println(result1.getUi() );
		//		System.out.println(result1.getDefaultPreferredName());
		//		System.out.println(result1.getSemanticTypes());
		//		System.out.println(result1.getClassType());
		for (UiLabel l : util.filterConcepts(util.findConcepts("AR")))
		{
			System.out.println(l.getLabel() + " - " + l.getUi());
		}
	}

	public ConceptDTO getConcept(String CUI)
	{
		try
		{
			return utsContentService.getConcept(securityService.getProxyTicket(proxyGrantTicket, serviceName), umlsRelease, CUI);
		} catch (UtsFault_Exception e)
		{
			e.printStackTrace();
			return null;
		}
	}

	public List<UiLabel> findConcepts(String search)
	{

		Psf myPsf = new Psf();
		myPsf.setPageLn(5);
		List<UiLabel> addLabels = new ArrayList<>();

		try
		{
			List<UiLabel> myUiLabels = utsFinderService.findConcepts(securityService.getProxyTicket(proxyGrantTicket, serviceName),
					umlsRelease, "atom", search, "words", myPsf);
			for (UiLabel l : myUiLabels)
			{
				List<UiLabelRootSource> myUiLabelsRootSource =
						utsFinderService.findSourceConcepts(securityService.getProxyTicket(proxyGrantTicket, serviceName), umlsRelease,
								"atom", l.getLabel(), "words", myPsf);
				for (UiLabelRootSource s : myUiLabelsRootSource)
				{
					if (s.getRootSource().equals(SOURCE_FILTER))
					{
						addLabels.add(l);
						break;
					}
				}
			}
			return addLabels;
		} catch (UtsFault_Exception e)
		{
			e.printStackTrace();
		}
		return addLabels;

	}

	public List<UiLabel> filterConcepts(List<UiLabel> conceptList)
	{
		List<UiLabel> filtered = new ArrayList<>();
		for (UiLabel l : conceptList)
		{
			ConceptDTO concept = getConcept(l.getUi());
			boolean disease = false;
			String cui = l.getUi();
			for (String s : concept.getSemanticTypes())
			{
				if (tuis.contains(s))
				{
					l.setUi(cui);
					disease = true;
					break;
				} else if (s.equals(finding))
				{
					disease = true;
					l.setUi(CUILESS);
					break;
				}
			}
			if (disease)
			{
				filtered.add(l);
			}
		}
		return filtered;
	}

}
