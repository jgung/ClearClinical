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
import org.cleartk.classifier.Feature;
import org.cleartk.classifier.feature.extractor.CleartkExtractor;
import org.cleartk.classifier.feature.extractor.CleartkExtractor.Bag;
import org.cleartk.classifier.feature.extractor.CleartkExtractor.Covered;
import org.cleartk.classifier.feature.extractor.CleartkExtractor.Following;
import org.cleartk.classifier.feature.extractor.CleartkExtractor.Preceding;
import org.cleartk.classifier.feature.extractor.simple.NamingExtractor;
import org.cleartk.classifier.feature.extractor.simple.SimpleFeatureExtractor;
import org.cleartk.classifier.feature.extractor.simple.TypePathExtractor;
import org.cleartk.semeval2015.type.DisorderSpan;

import java.util.ArrayList;
import java.util.List;

public class PartOfSpeechFeaturesExtractor implements RelationFeaturesExtractor
{

	private SimpleFeatureExtractor pos = new TypePathExtractor(BaseToken.class, "partOfSpeech");

	/**
	 * All part-of-speech tags of the mention as a bag
	 */
	private SimpleFeatureExtractor tokenPOS = new CleartkExtractor(
			BaseToken.class,
			pos,
			new Bag(new Covered()),
			new Preceding(3), new Following(3));

	/**
	 * All extractors for mention 1, with features named to distinguish them from mention 2
	 */
	private SimpleFeatureExtractor mention1FeaturesExtractor = new NamingExtractor(
			"mention1",
			tokenPOS);

	/**
	 * All extractors for mention 2, with features named to distinguish them from mention 1
	 */
	private SimpleFeatureExtractor mention2FeaturesExtractor = new NamingExtractor(
			"mention2",
			tokenPOS);

	@Override
	public List<Feature> extract(JCas jCas, DisorderSpan arg1, DisorderSpan arg2)
			throws AnalysisEngineProcessException
	{
		List<Feature> features = new ArrayList<>();
		features.addAll(this.mention1FeaturesExtractor.extract(jCas, arg1));
		features.addAll(this.mention2FeaturesExtractor.extract(jCas, arg2));
		return features;
	}

}
