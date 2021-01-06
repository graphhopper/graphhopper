/*
 *  Licensed to GraphHopper GmbH under one or more contributor
 *  license agreements. See the NOTICE file distributed with this work for
 *  additional information regarding copyright ownership.
 *
 *  GraphHopper GmbH licenses this file to you under the Apache License,
 *  Version 2.0 (the "License"); you may not use this file except in
 *  compliance with the License. You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package com.graphhopper.routing.util.spatialrules;

import com.graphhopper.GraphHopper;
import com.graphhopper.util.JsonFeature;
import com.graphhopper.util.JsonFeatureCollection;
import com.graphhopper.routing.ev.Country;
import com.graphhopper.routing.util.parsers.SpatialRuleParser;
import com.graphhopper.routing.util.parsers.TagParser;
import com.graphhopper.routing.util.parsers.TagParserFactory;
import com.graphhopper.util.Helper;
import com.graphhopper.util.PMap;
import org.locationtech.jts.geom.Envelope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * Helper class to build the spatial rule index. This is kind of an ugly plugin mechanism to avoid requiring a
 * jackson dependency on the core.
 *
 * @author Robin Boldt
 */
public class SpatialRuleLookupHelper {

    private static final Logger logger = LoggerFactory.getLogger(SpatialRuleLookupHelper.class);
    static final String JSON_ID_FIELD = "ISO3166-1:alpha3";

    /**
     * This method limits the JsonFeatures to the specified subset
     */
    static List<JsonFeatureCollection> reorder(List<JsonFeatureCollection> jsonFeatureCollections, List<String> subset) {
        Map<String, JsonFeature> map = new LinkedHashMap<>();
        for (JsonFeatureCollection featureCollection : jsonFeatureCollections) {
            for (JsonFeature jsonFeature : featureCollection.getFeatures()) {
                String id = (String) jsonFeature.getProperty(JSON_ID_FIELD);
                if (!Helper.isEmpty(id))
                    map.put(Helper.toLowerCase(id), jsonFeature);
            }
        }
        if (map.isEmpty())
            throw new IllegalArgumentException("Input JsonFeatureCollection cannot be empty. Subset: " + subset + ", original.size:" + jsonFeatureCollections.size());

        List<JsonFeature> newCollection = new ArrayList<>();
        for (String val : subset) {
            JsonFeature jsonFeature = map.get(val);
            if (jsonFeature == null)
                throw new IllegalArgumentException("SpatialRule does not exist. ID: " + val);
            newCollection.add(jsonFeature);
        }
        JsonFeatureCollection coll = new JsonFeatureCollection();
        coll.getFeatures().addAll(newCollection);
        return Arrays.asList(coll);
    }

    public static void buildAndInjectCountrySpatialRules(GraphHopper graphHopper, Envelope maxBounds, List<JsonFeatureCollection> jsonFeatureCollections) {
        List<String> subset = new ArrayList<>();
        for (Country c : Country.values()) {
            if (c != Country.DEFAULT)
                subset.add(c.toString());
        }
        final SpatialRuleLookup index = SpatialRuleLookupBuilder.buildIndex(reorder(jsonFeatureCollections, subset),
                JSON_ID_FIELD, new CountriesSpatialRuleFactory(), maxBounds);
        logger.info("Set spatial rule lookup with {} rules", index.getRules().size());
        final TagParserFactory oldTPF = graphHopper.getTagParserFactory();
        graphHopper.setTagParserFactory(new TagParserFactory() {

            @Override
            public TagParser create(String name, PMap configuration) {
                if (name.equals(Country.KEY))
                    return new SpatialRuleParser(index, Country.create());

                return oldTPF.create(name, configuration);
            }
        });
    }
}
