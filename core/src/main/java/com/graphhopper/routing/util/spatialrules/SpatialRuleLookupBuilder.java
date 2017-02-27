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

import com.graphhopper.json.geo.Geometry;
import com.graphhopper.json.geo.JsonFeature;
import com.graphhopper.json.geo.JsonFeatureCollection;
import com.graphhopper.routing.util.spatialrules.countries.DefaultSpatialRule;
import com.graphhopper.util.shapes.BBox;

import java.util.*;

/**
 * Creates a SpatialRuleLookup for a certain set of predefined areas.
 *
 * @author Robin Boldt
 */
public class SpatialRuleLookupBuilder {

    public interface SpatialRuleFactory {
        /**
         * This method creates a SpatialRule out of the provided polygons indicating the 'border'.
         */
        SpatialRule createSpatialRule(String id, final List<Polygon> polygons);
    }

    public static class SpatialRuleListFactory implements SpatialRuleFactory {
        private final Map<String, SpatialRule> ruleMap;

        public SpatialRuleListFactory(SpatialRule... rules) {
            this(Arrays.asList(rules));
        }

        public SpatialRuleListFactory(List<SpatialRule> rules) {
            ruleMap = new HashMap<>(rules.size());
            for (SpatialRule rule : rules) {
                ruleMap.put(rule.getId(), rule);
            }
        }

        @Override
        public SpatialRule createSpatialRule(String id, final List<Polygon> polygons) {
            if (id == null)
                throw new IllegalArgumentException("ID cannot be null to find a SpatialRule");

            SpatialRule spatialRule = ruleMap.get(id);
            if (spatialRule != null) {
                spatialRule.setBorders(polygons);
                return spatialRule;
            }
            return SpatialRule.EMPTY;
        }
    }

    public static class SpatialRuleDefaultFactory implements SpatialRuleFactory {
        @Override
        public SpatialRule createSpatialRule(final String id, final List<Polygon> polygons) {
            return new DefaultSpatialRule() {
                @Override
                public String getId() {
                    return id;
                }
            }.setBorders(polygons);
        }
    }

    public SpatialRuleLookup build(List<SpatialRule> rules, JsonFeatureCollection jsonFeatureCollection,
                                   BBox bounds, double resolution, boolean exact) {
        return build("ISO_A3", new SpatialRuleListFactory(rules), jsonFeatureCollection, bounds, resolution, exact);
    }

    /**
     * This method connects the rules with the jsonFeatureCollection via their ISO_A3 property and the rules its
     * getId method.
     *
     * @param jsonProperty the key that should be used to fetch the ID that is passed to SpatialRuleFactory#createSpatialRule
     * @return the index or null if the specified bounds does not intersect with the calculated ones from the rules.
     */
    public SpatialRuleLookup build(String jsonProperty, SpatialRuleFactory ruleFactory, JsonFeatureCollection jsonFeatureCollection,
                                   BBox bounds, double resolution, boolean exact) {

        // TODO filter out polyons that don't intersect with the given BBox, will be implicitly done later anyway
        BBox polygonBounds = BBox.createInverse(false);
        List<SpatialRule> rules = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        int unknownCounter = 0;
        for (JsonFeature jsonFeature : jsonFeatureCollection.getFeatures()) {
            Geometry geometry = jsonFeature.getGeometry();
            if (!geometry.isPolygon())
                continue;

            List<Polygon> borders = geometry.asPolygon().getPolygons();
            String id = (String) jsonFeature.getProperty(jsonProperty);
            if (id == null || id.isEmpty()) {
                id = "_unknown_id_" + unknownCounter;
                unknownCounter++;
            }

            if (ids.contains(id))
                throw new RuntimeException("The id " + id + " was already used. Either leave the json property '" + jsonProperty + "' empty or use an unique id.");

            ids.add(id);
            SpatialRule spatialRule = ruleFactory.createSpatialRule(id, borders);
            if (spatialRule == SpatialRule.EMPTY)
                continue;

            rules.add(spatialRule);

            for (Polygon polygon : borders) {
                polygonBounds.update(polygon.getMinLat(), polygon.getMinLon());
                polygonBounds.update(polygon.getMaxLat(), polygon.getMaxLon());
            }
        }

        if (rules.isEmpty())
            return null;

        if (!polygonBounds.isValid()) {
            throw new IllegalStateException("No associated polygons found in JsonFeatureCollection for rules " + rules);
        }

        // Only create a SpatialRuleLookup if there are rules defined in the given bounds
        BBox calculatedBounds = polygonBounds.calculateIntersection(bounds);
        if (calculatedBounds == null)
            return null;

        SpatialRuleLookup spatialRuleLookup = new SpatialRuleLookupArray(calculatedBounds, resolution, exact);
        for (SpatialRule spatialRule : rules) {
            spatialRuleLookup.addRule(spatialRule);
        }
        return spatialRuleLookup;
    }
}
