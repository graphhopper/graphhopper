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
package com.graphhopper.http;

import com.graphhopper.json.GHJson;
import com.graphhopper.json.GHJsonBuilder;
import com.graphhopper.json.geo.Geometry;
import com.graphhopper.json.geo.JsonFeature;
import com.graphhopper.json.geo.JsonFeatureCollection;
import com.graphhopper.routing.util.spatialrules.SpatialRule;
import com.graphhopper.routing.util.spatialrules.SpatialRuleLookup;
import com.graphhopper.routing.util.spatialrules.SpatialRuleLookupArray;
import com.graphhopper.routing.util.spatialrules.countries.AustriaSpatialRule;
import com.graphhopper.routing.util.spatialrules.countries.GermanySpatialRule;
import com.graphhopper.util.shapes.BBox;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;

/**
 * Crates a SpatialRuleLookup for every relevant Country
 *
 * @author Robin Boldt
 */
public class SpatialRuleLookupBuilder {

    private final static BBox DEFAULT_BOUNDS = new BBox(-180, 180, -90, 90);
    private final static double DEFAULT_RESOLUTION = .1;
    private final static boolean DEFAULT_EXACT = true;

    private final static SpatialRule[] rules = new SpatialRule[]{
            new AustriaSpatialRule(),
            new GermanySpatialRule()
    };

    public static SpatialRuleLookup build() {
        return SpatialRuleLookupBuilder.build(DEFAULT_BOUNDS, DEFAULT_RESOLUTION, DEFAULT_EXACT);
    }

    public static SpatialRuleLookup build(BBox bounds, double resolution, boolean exact) {
        SpatialRuleLookup spatialRuleLookup = new SpatialRuleLookupArray(bounds, resolution, exact);
        try {
            GHJson ghJson = new GHJsonBuilder().create();
            JsonFeatureCollection jsonFeatureCollection = ghJson.fromJson(new FileReader(new File(SpatialRuleLookupBuilder.class.getResource("countries.geo.json").getFile())), JsonFeatureCollection.class);

            // TODO find outer Border of all used features and create SpatialRuleLookupArray onlyo for these Bounds

            for (SpatialRule spatialRule : rules) {
                for (JsonFeature jsonFeature : jsonFeatureCollection.getFeatures()) {
                    if (spatialRule.getCountryIsoA3Name().equals(jsonFeature.getProperty("ISO_A3"))) {
                        Geometry geometry = jsonFeature.getGeometry();
                        if (!geometry.isPolygon())
                            continue;
                        spatialRule.setBorders(geometry.asPolygon().getPolygons());
                        spatialRuleLookup.addRule(spatialRule);
                        break;
                    }
                }
            }
        } catch (FileNotFoundException e) {
            throw new RuntimeException(e);
        }

        return spatialRuleLookup;
    }

}
