package edu.colorado.clear.clinical.ner.util;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.filefilter.HiddenFileFilter;
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

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class SemEval2015CollectionReader extends JCasCollectionReader_ImplBase
{

	public static final String PIPE_SUFFIX = "pipe";
	public static final String TEXT_SUFFIX = "text";

	public static final String PIPED_VIEW = "PIPE_VIEW";

	public static final String PARAM_FILES = "files";
	@ConfigurationParameter(
			name = PARAM_FILES,
			description = "points to a semeval-2014-task-7 data directory")
	protected Collection<File> files;

	protected List<File> pipedFiles = new ArrayList<>();
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
			} else if (f.getPath().endsWith(PIPE_SUFFIX))
			{
				files.add(f);
			}
		}
	}

	public void initialize(UimaContext context) throws ResourceInitializationException
	{
		for (File f : files)
		{
			String path = f.getPath().replace(PIPE_SUFFIX, TEXT_SUFFIX);
			File textFile = new File(path);
			if (textFile.exists())
			{
				pipedFiles.add(f);
				textFiles.add(textFile);
			}
		}
		totalFiles = pipedFiles.size();
	}

	public void getNext(JCas jCas) throws IOException, CollectionException
	{
		JCas pipedView;
		try
		{
			pipedView = jCas.createView(PIPED_VIEW);
		} catch (CASException ce)
		{
			throw new CollectionException(ce);
		}

		File pipeFile = pipedFiles.remove(0);
		String annotations = FileUtils.readFileToString(pipeFile);
		File textFile = textFiles.remove(0);
		String fileText = FileUtils.readFileToString(textFile);

		jCas.setDocumentText(fileText);
		ViewURIUtil.setURI(jCas, textFile.toURI());
		pipedView.setDocumentText(annotations);
	}

	public boolean hasNext() throws IOException, CollectionException
	{
		return (pipedFiles.size() > 0);
	}

	public Progress[] getProgress()
	{
		return new Progress[]{
				new ProgressImpl(totalFiles - pipedFiles.size(),
						totalFiles,
						Progress.ENTITIES)};
	}

}
