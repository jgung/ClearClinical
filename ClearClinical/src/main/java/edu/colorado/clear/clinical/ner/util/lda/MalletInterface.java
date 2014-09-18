package edu.colorado.clear.clinical.ner.util.lda;

import cc.mallet.pipe.*;
import cc.mallet.pipe.iterator.FileIterator;
import cc.mallet.topics.ParallelTopicModel;
import cc.mallet.types.*;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.filefilter.HiddenFileFilter;
import org.apache.commons.io.filefilter.SuffixFileFilter;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.util.*;
import java.util.regex.Pattern;

public class MalletInterface
{

	public static String stopList = "data/enstoplist.txt";
	public static String directory = "semeval-2014-unlabeled-mimic-notes.v1/";
	public static String TEXT_SUFFIX = ".text";

	public static int[] nTopics = new int[]{10, 25, 50, 100};
	public static int TOP_TOPICS = 5;
	public static int iterations = 1000;

	public static void main(String[] args) throws Exception
	{
		generateTopics(directory, stopList, nTopics, iterations);
	}

	public static void generateTopics(String directory, String stopList, int[] nTopics, int iterations) throws IOException
	{
		HashMap<String, List<SortableTopic>> wordTopicDistribution =
				new HashMap<>();

		for (int k : nTopics)
		{
			// Begin by importing documents from text to feature sequences
			List<Pipe> pipeList = new ArrayList<>();
			// Pipes: lowercase, tokenize, remove stopwords, map to features
			// Read data from File objects
			pipeList.add(new Input2CharSequence("UTF-8"));
			Pattern tokenPattern =
					Pattern.compile("[\\p{L}\\p{N}_]+");
			// Tokenize raw strings
			pipeList.add(new CharSequence2TokenSequence(tokenPattern));
			// Normalize all tokens to all lowercase
			pipeList.add(new TokenSequenceLowercase());
			pipeList.add(new TokenSequenceRemoveStopwords(new File(stopList), "UTF-8", false, false, false));
			pipeList.add(new TokenSequence2FeatureSequence());
			InstanceList instances = new InstanceList(new SerialPipes(pipeList));

			instances.addThruPipe(new FileIterator(new File(directory),
					new SuffixFileFilter(".txt"))); // data, label, name fields

			// Create a model with 100 topics, alpha_t = 0.01, beta_w = 0.01
			//  Note that the first parameter is passed as the sum over topics, while
			//  the second is the parameter for a single dimension of the Dirichlet prior.
			ParallelTopicModel model = new ParallelTopicModel(k, 1.0, 0.01);

			model.addInstances(instances);

			// Use two parallel samplers, which each look at one half the corpus and combine
			// statistics after every iteration.
			model.setNumThreads(2);

			// Run the model for 50 iterations and stop (this is for testing only,
			// for real applications, use 1000 to 2000 iterations)
			model.setNumIterations(iterations);
			model.estimate();

			// The data alphabet maps word IDs to strings
			Alphabet dataAlphabet = instances.getDataAlphabet();

			FeatureSequence tokens = (FeatureSequence) model.getData().get(0).instance.getData();
			LabelSequence topics = model.getData().get(0).topicSequence;

			Formatter out = new Formatter(new StringBuilder(), Locale.US);
			for (int position = 0; position < tokens.getLength(); position++)
			{
				out.format("%s-%d ", dataAlphabet.lookupObject(tokens.getIndexAtPosition(position)), topics.getIndexAtPosition(position));
			}
			System.out.println(out);

			// Estimate the topic distribution of the first instance,
			// given the current Gibbs state.
			double[] topicDistribution = model.getTopicProbabilities(0);

			// Get an array of sorted sets of word ID/count pairs
			List<TreeSet<IDSorter>> topicSortedWords = model.getSortedWords();

			// Show top 5 words in topics with proportions for the first document
			for (int topic = 0; topic < k; topic++)
			{
				Iterator<IDSorter> iterator = topicSortedWords.get(topic).iterator();
				out = new Formatter(new StringBuilder(), Locale.US);
				out.format("%d\t%.3f\t", topic, topicDistribution[topic]);
				int rank = 0;
				while (iterator.hasNext() && rank < TOP_TOPICS)
				{
					IDSorter idCountPair = iterator.next();
					out.format("%s (%.0f) ", dataAlphabet.lookupObject(idCountPair.getID()), idCountPair.getWeight());
					rank++;
				}
				System.out.println(out);
			}
			getPWordGivenTopic(model, wordTopicDistribution);
			String outputFile = "data/wordTopicDist-" + k + ".txt";
			writeWordTopicDistribution(wordTopicDistribution, outputFile);
		}
	}

	public static void getPWordGivenTopic(ParallelTopicModel model, HashMap<String, List<SortableTopic>> wordTopicDistribution)
	{
		for (int topic = 0; topic < model.numTopics; topic++)
		{
			double weightSumTopic = 0;
			for (int type = 0; type < model.numTypes; type++)
			{
				int[] topicCounts = model.typeTopicCounts[type];
				double weight = model.beta;
				int index = 0;
				while (index < topicCounts.length &&
						topicCounts[index] > 0)
				{
					int currentTopic = topicCounts[index] & model.topicMask;
					if (currentTopic == topic)
					{
						weight += topicCounts[index] >> model.topicBits;
						break;
					}
					index++;
				}
				weightSumTopic += weight;
			}
			for (int type = 0; type < model.numTypes; type++)
			{
				int[] topicCounts = model.typeTopicCounts[type];
				double weight = model.beta;
				int index = 0;
				while (index < topicCounts.length &&
						topicCounts[index] > 0)
				{
					int currentTopic = topicCounts[index] & model.topicMask;
					if (currentTopic == topic)
					{
						weight += topicCounts[index] >> model.topicBits;
						break;
					}
					index++;
				}
				double wordGivenTopic = weight / weightSumTopic;
				String word = model.alphabet.lookupObject(type).toString();
				List<SortableTopic> wordTopicList = wordTopicDistribution.get(word);
				if (wordTopicList == null)
				{
					wordTopicList = new ArrayList<>();
				}
				wordTopicList.add(new SortableTopic(topic, wordGivenTopic));
				wordTopicDistribution.put(word, wordTopicList);
			}
		}
	}

	public static void writeWordTopicDistribution(HashMap<String, List<SortableTopic>> wtdist, String out)
	{
		StringBuilder output = new StringBuilder();
		for (String key : wtdist.keySet())
		{
			List<SortableTopic> wlist = wtdist.get(key);
			Collections.sort(wlist);
			String append = key + ":|:";
			for (int i = 0; i < TOP_TOPICS; ++i)
			{
				SortableTopic t = wlist.get(i);
				append += t.topic + "::" + Math.log(t.probability) + " ";
			}
			append = append.trim();
			append += "\n";
			output.append(append);
		}
		try
		{
			FileUtils.writeStringToFile(new File(out), output.toString());
		} catch (IOException e)
		{
			e.printStackTrace();
		}
	}

	public static void collectFiles(File directory, List<File> files) throws IOException
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

	public static class SortableTopic implements Comparable<SortableTopic>
	{

		public int topic;
		public double probability;

		public SortableTopic(int t, double p)
		{
			this.topic = t;
			this.probability = p;
		}

		public int compareTo(SortableTopic arg0)
		{
			if (this.probability > arg0.probability)
				return -1;
			else
				return 1;
		}

	}
}