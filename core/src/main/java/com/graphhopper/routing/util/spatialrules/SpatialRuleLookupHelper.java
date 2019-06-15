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
import com.graphhopper.json.geo.JsonFeatureCollection;
import com.graphhopper.routing.profiles.Country;
import com.graphhopper.routing.util.parsers.TagParserFactory;
import com.graphhopper.routing.util.parsers.SpatialRuleParser;
import com.graphhopper.routing.util.parsers.TagParser;
import com.graphhopper.util.PMap;
import com.graphhopper.util.shapes.BBox;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Helper class to build the spatial rule index. This is kind of an ugly plugin mechanism to avoid requiring a
 * jackson dependency on the core.
 *
 * @author Robin Boldt
 */
public class SpatialRuleLookupHelper {

    private static final Logger logger = LoggerFactory.getLogger(SpatialRuleLookupHelper.class);

    public static void buildAndInjectSpatialRuleIntoGH(GraphHopper graphHopper, BBox maxBounds, JsonFeatureCollection jsonFeatureCollection) {
        final SpatialRuleLookup index = SpatialRuleLookupBuilder.buildIndex(jsonFeatureCollection, "ISO_A3", new CountriesSpatialRuleFactory(), .1, maxBounds);
        logger.info("Set spatial rule lookup with " + index.size() + " rules");
        final TagParserFactory oldTPF = graphHopper.getTagParserFactory();
        graphHopper.setTagParserFactory(new TagParserFactory() {

            @Override
            public TagParser create(String name, PMap configuration) {
                if (name.equals(Country.KEY))
                    return new SpatialRuleParser(index);

                return oldTPF.create(name, configuration);
            }
        });
    }
}
