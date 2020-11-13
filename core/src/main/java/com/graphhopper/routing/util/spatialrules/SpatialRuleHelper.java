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

import com.graphhopper.json.geo.JsonFeature;
import com.graphhopper.json.geo.JsonFeatureCollection;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.geom.util.PolygonExtracter;

import static com.graphhopper.util.Helper.toLowerCase;

import java.util.*;
import java.util.Map.Entry;

/**
 * Helper class to build the spatial rules. This is kind of an ugly plugin
 * mechanism to avoid requiring a jackson dependency on the core.
 *
 * @author Robin Boldt
 */
public class SpatialRuleHelper {

    private static final GeometryFactory FAC = new GeometryFactory();
    private static final Envelope WORLD_BBOX = new Envelope(-180, 180, -90, 90);

    private SpatialRuleHelper() {
    }

    /**
     * Extracts the borders from the given {@link JsonFeatureCollection} List using
     * {@link #getBorders(List, String, Envelope)} and attempts to create a {@link SpatialRule} for
     * each ID with the provided {@link SpatialRuleFactory}.
     * 
     * @param jsonFeatureCollections
     *            a List of {@link JsonFeatureCollection} describing borders of spatial rules
     * @param idField
     *            the property of the {@link JsonFeature} to be used as the ID when creating the
     *            rules
     * @param factory
     *            the {@link SpatialRuleFactory} used to create the rules
     * @param maxBounds
     *            the generated {@link SpatialRule SpatialRules} are guaranteed to be within the
     *            {@link Envelope}. Only parts of the border {@link Polygon Polygons} within the boundingbox
     *            are retained.
     * @return a (possibly empty) List of {@link SpatialRule SpatialRules}
     * @see SpatialRuleLookup
     * @see SpatialRule
     */
    public static List<SpatialRule> buildSpatialRules(
                    List<JsonFeatureCollection> jsonFeatureCollections, String idField,
                    SpatialRuleFactory factory, Envelope maxBounds) {
        Map<String, List<Polygon>> borderMap = getBorders(jsonFeatureCollections, idField,
                        maxBounds);

        List<SpatialRule> rules = new ArrayList<>();
        for (Entry<String, List<Polygon>> entry : borderMap.entrySet()) {
            SpatialRule rule = factory.createSpatialRule(entry.getKey(), entry.getValue());
            if (rule != null) {
                rules.add(rule);
            }
        }

        return rules;
    }

    /**
     * @see #buildSpatialRules(List, String, SpatialRuleFactory, Envelope)
     */
    public static List<SpatialRule> buildSpatialRules(
                    List<JsonFeatureCollection> jsonFeatureCollections, String idField,
                    SpatialRuleFactory factory) {
        return buildSpatialRules(jsonFeatureCollections, idField, factory, WORLD_BBOX);
    }

    /**
     * Partitions the border polygons retrieved from the given
     * {@link JsonFeatureCollection} List by the specified ID field while
     * excluding areas outside of the provided boundingbox.
     * 
     *
     * @param jsonFeatureCollections
     * @param jsonIdField
     * @param maxBBox
     * @return the borders partitioned by the selected ID field
     */
    protected static Map<String, List<Polygon>> getBorders(
                    List<JsonFeatureCollection> jsonFeatureCollections, String jsonIdField,
                    Envelope maxBBox) {
        Geometry bboxGeometry = FAC.toGeometry(maxBBox);

        Map<String, List<Polygon>> borderMap = new HashMap<>();
        for (JsonFeatureCollection featureCollection : jsonFeatureCollections) {
            for (JsonFeature jsonFeature : featureCollection.getFeatures()) {
                String id = getId(jsonFeature, jsonIdField);
                if (borderMap.containsKey(id)) {
                    throw new IllegalArgumentException("Duplicate JsonFacture with ID " + id);
                }
                List<Polygon> borders = intersections(jsonFeature.getGeometry(), id, bboxGeometry);

                if (!borders.isEmpty()) {
                    borderMap.put(id, borders);
                }
            }
        }

        return borderMap;
    }
    
    private static List<Polygon> intersections(Geometry jsonGeometry, String id, Geometry bboxGeometry) {
        List<Polygon> borders = new ArrayList<>();
        for (int i = 0; i < jsonGeometry.getNumGeometries(); i++) {
            Geometry poly = jsonGeometry.getGeometryN(i);
            if (!(poly instanceof Polygon)) {
                throw new IllegalArgumentException("Geometry for " + id + " (" + i
                                + ") not supported " + poly.getClass().getSimpleName());
            }
            Geometry intersection = bboxGeometry.intersection(poly);
            if (!intersection.isEmpty()) {
                PolygonExtracter.getPolygons(intersection, borders);
            }
        }
        return borders;
    }
    
    private static String getId(JsonFeature jsonFeature, String jsonIdField) {
        String id;
        if (jsonIdField.isEmpty() || "id".equalsIgnoreCase(jsonIdField)) {
            id = jsonFeature.getId();
        } else {
            id = (String) jsonFeature.getProperty(jsonIdField);
        }
        if (id == null || id.isEmpty()) {
            throw new IllegalArgumentException("ID cannot be empty for JsonFeature");
        }
        return toLowerCase(id);
    }
}
