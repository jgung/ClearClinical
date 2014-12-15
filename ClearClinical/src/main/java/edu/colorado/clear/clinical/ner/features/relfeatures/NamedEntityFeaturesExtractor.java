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

import org.apache.ctakes.typesystem.type.textsem.EntityMention;
import org.apache.ctakes.typesystem.type.textsem.IdentifiedAnnotation;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.jcas.JCas;
import org.apache.uima.jcas.tcas.Annotation;
import org.cleartk.classifier.Feature;
import org.cleartk.classifier.feature.extractor.CleartkExtractorException;
import org.cleartk.classifier.feature.extractor.annotationpair.DistanceExtractor;
import org.cleartk.classifier.feature.extractor.simple.NamingExtractor;
import org.cleartk.classifier.feature.extractor.simple.SimpleFeatureExtractor;
import org.cleartk.score.type.ScoredAnnotation;
import org.uimafit.util.JCasUtil;

import java.util.ArrayList;
import java.util.List;

public class NamedEntityFeaturesExtractor implements RelationFeaturesExtractor
{

	private SimpleFeatureExtractor namedEntityType = new SimpleFeatureExtractor()
	{
		@Override
		public List<Feature> extract(JCas jCas, Annotation ann) throws CleartkExtractorException
		{
			List<Feature> features = new ArrayList<>();
			List<IdentifiedAnnotation> umlsFeatures = JCasUtil.selectCovered(jCas, IdentifiedAnnotation.class, ann);
			for (IdentifiedAnnotation id : umlsFeatures)
			{
				features.add(new Feature("TypeID", id.getTypeID()));
			}
			return features;
		}
	};

	/**
	 * All extractors for mention 1, with features named to distinguish them from mention 2
	 */
	private SimpleFeatureExtractor mention1FeaturesExtractor = new NamingExtractor(
			"mention1",
			namedEntityType);

	/**
	 * All extractors for mention 2, with features named to distinguish them from mention 1
	 */
	private SimpleFeatureExtractor mention2FeaturesExtractor = new NamingExtractor(
			"mention2",
			namedEntityType);

	/**
	 * Number of named entities between the two mentions
	 */
	private DistanceExtractor nEntityMentionsBetween = new DistanceExtractor(null, EntityMention.class);

	@Override
	public List<Feature> extract(JCas jCas, ScoredAnnotation arg1, ScoredAnnotation arg2)
			throws AnalysisEngineProcessException
	{

		List<Feature> features = new ArrayList<>();
		features.addAll(this.mention1FeaturesExtractor.extract(jCas, arg1));
		features.addAll(this.mention2FeaturesExtractor.extract(jCas, arg2));
		features.addAll(this.nEntityMentionsBetween.extract(jCas, arg1, arg2));

		return features;
	}

}
