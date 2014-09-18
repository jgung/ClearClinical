package edu.colorado.clear.clinical.ner.features.spanfeatures;

import org.apache.commons.io.FileUtils;
import org.apache.uima.jcas.JCas;
import org.apache.uima.jcas.tcas.Annotation;
import org.cleartk.classifier.Feature;
import org.cleartk.classifier.feature.extractor.CleartkExtractorException;
import org.cleartk.classifier.feature.extractor.simple.SimpleFeatureExtractor;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class TopicFeatureExtractor implements SimpleFeatureExtractor
{

	public static int topTopics = 3;
	public HashMap<String, ArrayList<Topic>> pathMap = new HashMap<>();

	public TopicFeatureExtractor(File wordTopicsFile)
	{
		String paths;
		try
		{
			paths = FileUtils.readFileToString(wordTopicsFile);
			double minProb = 1E-5;
			for (String line : paths.split("\n"))
			{
				String[] fields = line.split(":\\|:");
				String token = fields[0];
				String[] topTopics = fields[1].split(" ");
				ArrayList<Topic> topics = new ArrayList<>();
				for (String t : topTopics)
				{
					String[] topic = t.split("::");
					int id = Integer.parseInt(topic[0]);
					double prob = Double.parseDouble(topic[1]);
					if (prob > minProb)
					{
						topics.add(new Topic(id, prob));
					}
				}
				pathMap.put(token, topics);
			}
		} catch (IOException e)
		{
			e.printStackTrace();
		}
	}

	@Override
	public List<Feature> extract(JCas view, Annotation token)
			throws CleartkExtractorException
	{
		List<Feature> features = new ArrayList<>();
		String key = token.getCoveredText().toLowerCase();
		features.addAll(getTopicFeatures(key, topTopics));
		return features;
	}

	public List<Feature> getTopicFeatures(String key, int top)
	{
		List<Feature> features = new ArrayList<>();
		ArrayList<Topic> topics = pathMap.get(key);
		if (topics != null && topics.size() > 0)
		{
			features.add(new Feature("TOP-TOPIC", "TOPIC-" + topics.get(0).id));
			for (int i = 0; i < topics.size() && i < top; ++i)
			{
				features.add(new Feature("TOPIC", "TOPIC-" + topics.get(i).id));
			}
		} else
			features.add(new Feature("TOP-TOPIC", "TOPIC-NONE"));
		return features;
	}

	public static class Topic
	{
		public double logProb;
		public int id;

		public Topic(int id, double logProb)
		{
			this.id = id;
			this.logProb = logProb;
		}
	}

}
