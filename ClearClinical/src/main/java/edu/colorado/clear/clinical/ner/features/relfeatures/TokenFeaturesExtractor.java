/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package edu.colorado.clear.clinical.ner.features.relfeatures;

import org.apache.ctakes.typesystem.type.syntax.BaseToken;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.jcas.JCas;
import org.apache.uima.jcas.tcas.Annotation;
import org.cleartk.classifier.Feature;
import org.cleartk.classifier.feature.extractor.CleartkExtractor;
import org.cleartk.classifier.feature.extractor.CleartkExtractor.*;
import org.cleartk.classifier.feature.extractor.annotationpair.DistanceExtractor;
import org.cleartk.classifier.feature.extractor.simple.CoveredTextExtractor;
import org.cleartk.classifier.feature.extractor.simple.NamingExtractor;
import org.cleartk.classifier.feature.extractor.simple.SimpleFeatureExtractor;
import org.cleartk.score.type.ScoredAnnotation;
import org.uimafit.util.JCasUtil;

import java.util.ArrayList;
import java.util.List;

public class TokenFeaturesExtractor implements RelationFeaturesExtractor
{

	private SimpleFeatureExtractor coveredText = new CoveredTextExtractor();

	/**
	 * First word of the mention, last word of the mention, all words of the mention as a bag, the
	 * preceding 3 words, the following 3 words
	 */
	private SimpleFeatureExtractor tokenContext = new CleartkExtractor(
			BaseToken.class,
			coveredText,
			new FirstCovered(1),
			new LastCovered(1),
			new Bag(new Covered()),
			new Preceding(3),
			new Following(3),
			new Bag(new Preceding(5), new Following(5)));

	/**
	 * All extractors for mention 1, with features named to distinguish them from mention 2
	 */
	private SimpleFeatureExtractor mention1FeaturesExtractor = new NamingExtractor(
			"mention1",
			coveredText,
			tokenContext);

	/**
	 * All extractors for mention 2, with features named to distinguish them from mention 1
	 */
	private SimpleFeatureExtractor mention2FeaturesExtractor = new NamingExtractor(
			"mention2",
			coveredText,
			tokenContext);

	/**
	 * First word, last word, and all words between the mentions
	 */
	private CleartkExtractor tokensBetween = new CleartkExtractor(
			BaseToken.class,
			new NamingExtractor("BetweenMentions", coveredText),
			new FirstCovered(1),
			new LastCovered(1),
			new Bag(new Covered()));

	/**
	 * Number of words between the mentions
	 */
	private DistanceExtractor nTokensBetween = new DistanceExtractor(null, BaseToken.class);

	private static List<Feature> getConcatFeatures(JCas jCas, Annotation arg1, Annotation arg2)
	{

		ArrayList<Feature> features = new ArrayList<>();
		/* concatenated text feature */
		features.add(new Feature("ARG1-ARG2",
				arg1.getCoveredText().toLowerCase().trim().replace("\\s", " ") + "-"
						+ arg2.getCoveredText().toLowerCase().trim().replace("\\s", " ")));
		String arg1Normalized = "";
		String arg2Normalized = "";
		for (BaseToken t : JCasUtil.selectCovered(jCas, BaseToken.class, arg1))
		{
			arg1Normalized += t.getNormalizedForm() + " ";
		}
		for (BaseToken t : JCasUtil.selectCovered(jCas, BaseToken.class, arg2))
		{
			arg2Normalized += t.getNormalizedForm() + " ";
		}
		arg1Normalized = arg1Normalized.trim();
		arg2Normalized = arg2Normalized.trim();
		features.add(new Feature("ARG1-ARG2-NORMALIZED", arg1Normalized + "-" + arg2Normalized));
		return features;
	}

	@Override
	public List<Feature> extract(JCas jCas, ScoredAnnotation mention1, ScoredAnnotation mention2)
			throws AnalysisEngineProcessException
	{
		List<Feature> features = new ArrayList<>();

		features.addAll(this.mention1FeaturesExtractor.extract(jCas, mention1));
		features.addAll(this.mention2FeaturesExtractor.extract(jCas, mention2));
		features.addAll(this.tokensBetween.extractBetween(jCas, mention1, mention2));
		features.addAll(this.nTokensBetween.extract(jCas, mention1, mention2));
		features.addAll(getConcatFeatures(jCas, mention1, mention2));

		return features;
	}
}
