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
import com.graphhopper.util.shapes.BBox;

import java.util.Collection;

/**
 * Crates a SpatialRuleLookup for every relevant Country
 *
 * @author Robin Boldt
 */
public class SpatialRuleLookupBuilder {

    /**
     * This method connects the rules with the jsonFeatureCollection via their ISO_A3 property and the rules its
     * getId method.
     *
     * @return the index or null if the specified bounds does not intersect with the calculated ones from the rules.
     */
    public SpatialRuleLookup build(Collection<SpatialRule> rules, JsonFeatureCollection jsonFeatureCollection,
                                   BBox bounds, double resolution, boolean exact) {

        // TODO filter out polyons that don't intersect with the given BBox, will be implicitly done later anyway
        for (SpatialRule spatialRule : rules) {
            for (JsonFeature jsonFeature : jsonFeatureCollection.getFeatures()) {
                if (spatialRule.getId().equals(jsonFeature.getProperty("ISO_A3"))) {
                    Geometry geometry = jsonFeature.getGeometry();
                    if (!geometry.isPolygon())
                        continue;
                    spatialRule.setBorders(geometry.asPolygon().getPolygons());
                    break;
                }
            }
        }

        BBox polygonBounds = BBox.createInverse(false);
        for (SpatialRule spatialRule : rules) {
            if (!spatialRule.getBorders().isEmpty()) {
                for (Polygon polygon : spatialRule.getBorders()) {
                    polygonBounds.update(polygon.getMinLat(), polygon.getMinLon());
                    polygonBounds.update(polygon.getMaxLat(), polygon.getMaxLon());
                }
            }
        }

        if (!polygonBounds.isValid()) {
            throw new IllegalStateException("No SpatialRules defined or Polygons defined");
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
