package edu.colorado.clear.clinical.ner.util;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.filefilter.HiddenFileFilter;
import org.apache.ctakes.typesystem.type.structured.DocumentID;
import org.apache.uima.UimaContext;
import org.apache.uima.cas.CASException;
import org.apache.uima.collection.CollectionException;
import org.apache.uima.jcas.JCas;
import org.apache.uima.resource.ResourceInitializationException;
import org.apache.uima.util.Progress;
import org.apache.uima.util.ProgressImpl;
import org.cleartk.util.ViewURIUtil;
import org.uimafit.component.JCasCollectionReader_ImplBase;
import org.uimafit.descriptor.ConfigurationParameter;

public class SemEval2015CorpusCollectionReader extends
		JCasCollectionReader_ImplBase {

	public static final String TEXT_SUFFIX = "txt";
	public static final String PARAM_FILES = "files";
	@ConfigurationParameter(
			name = PARAM_FILES,
			description = "points to a semeval-2014-unlabeled-mimic-notes.v1")
	protected Collection<File> files;

	protected List<File> textFiles = new ArrayList<>();
	protected int totalFiles = 0;

	public static void collectFiles(File directory, Collection<File> files) throws IOException
	{
		File[] dirFiles = directory.listFiles((FileFilter) HiddenFileFilter.VISIBLE);
		for (File f : dirFiles)
		{
			if (f.isDirectory())
			{
				collectFiles(f, files);
			} else if (f.getPath().endsWith(TEXT_SUFFIX))
			{
				files.add(f);
			}
		}
	}

	public void initialize(UimaContext context) throws ResourceInitializationException
	{
		for (File f : files)
		{
			File textFile = new File(f.getPath());
			if (textFile.exists())
			{
				textFiles.add(textFile);
			}
		}
		totalFiles = textFiles.size();
	}

	public void getNext(JCas jCas) throws IOException, CollectionException
	{
		File textFile = textFiles.remove(0);
		String fileText = FileUtils.readFileToString(textFile);
		String first_line = fileText.split("\n")[0];
		DocumentID id = new DocumentID(jCas);
		id.setDocumentID(textFile.getName().replaceAll("."+TEXT_SUFFIX, "")+"-"+first_line.split("\t")[6]);
		jCas.setDocumentText(fileText);
		id.addToIndexes();
		ViewURIUtil.setURI(jCas, textFile.toURI());
	}

	public boolean hasNext() throws IOException, CollectionException
	{
		return (textFiles.size() > 0);
	}

	public Progress[] getProgress()
	{
		return new Progress[]{
				new ProgressImpl(totalFiles - textFiles.size(),
						totalFiles,
						Progress.ENTITIES)};
	}

}
