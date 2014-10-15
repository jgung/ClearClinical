ClearClinical
=============

Repository for collaboration on various clinical NLP tasks

Licensing Issues
----------------

# SEMEVAL 2015 #
* Set up the semeval 2015 task 14 data by either:
	* Untar the semeval 2015 task 14 data tin src/main/resources  OR
	* Run src/main/resources/setup_semeval_data.bsh
		 * enter the CUAB team password

# UMLS #
You will need to have a UMLS username/password to use UMLS to run ClearClinical (for SNOMED CT) just as you would CTAKES. Eclipse users can fill in username and password in the launch templates in ClearClinical/src/main/resources/eclipse and copy them to YOUR_WORKSPACE/.metadata/.plugins/org.eclipse.debug.core/.launches

# ORACLE #
Unfortunately there is an Oracle OJDBC dependency requiring you to download ojdbc7.jar and put it in your maven repo. This will be fixed in the future, postgres?
