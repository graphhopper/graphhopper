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

import com.graphhopper.jackson.Jackson;
import com.graphhopper.json.geo.JsonFeatureCollection;
import com.graphhopper.util.shapes.BBox;
import org.junit.Assert;
import org.junit.Test;

import java.io.FileReader;
import java.io.IOException;

import static junit.framework.TestCase.assertFalse;
import static junit.framework.TestCase.assertTrue;
import static org.junit.Assert.assertEquals;

/**
 * @author Robin Boldt
 */
public class SpatialRuleLookupBuilderTest {

    private static final String COUNTRIES_FILE = "../core/files/spatialrules/countries.geo.json";

    @Test
    public void testIndex() throws IOException {
        final FileReader reader = new FileReader(COUNTRIES_FILE);
        SpatialRuleLookup spatialRuleLookup = SpatialRuleLookupBuilder.buildIndex(Jackson.newObjectMapper().readValue(reader, JsonFeatureCollection.class), "ISO_A3", new CountriesSpatialRuleFactory());

        // Berlin
        assertEquals(SpatialRule.Access.CONDITIONAL, spatialRuleLookup.lookupRule(52.5243700, 13.4105300).getAccess("track", TransportationMode.MOTOR_VEHICLE, SpatialRule.Access.YES));
        assertEquals(SpatialRule.Access.YES, spatialRuleLookup.lookupRule(52.5243700, 13.4105300).getAccess("primary", TransportationMode.MOTOR_VEHICLE, SpatialRule.Access.YES));

        // Paris -> empty rule
        assertEquals(SpatialRule.Access.YES, spatialRuleLookup.lookupRule(48.864716, 2.349014).getAccess("track", TransportationMode.MOTOR_VEHICLE, SpatialRule.Access.YES));
        assertEquals(SpatialRule.Access.YES, spatialRuleLookup.lookupRule(48.864716, 2.349014).getAccess("primary", TransportationMode.MOTOR_VEHICLE, SpatialRule.Access.YES));

        // Vienna
        assertEquals(SpatialRule.Access.YES, spatialRuleLookup.lookupRule(48.210033, 16.363449).getAccess("track", TransportationMode.MOTOR_VEHICLE, SpatialRule.Access.YES));
        assertEquals(SpatialRule.Access.YES, spatialRuleLookup.lookupRule(48.210033, 16.363449).getAccess("primary", TransportationMode.MOTOR_VEHICLE, SpatialRule.Access.YES));
        assertEquals(SpatialRule.Access.CONDITIONAL, spatialRuleLookup.lookupRule(48.210033, 16.363449).getAccess("living_street", TransportationMode.MOTOR_VEHICLE, SpatialRule.Access.YES));
    }

    @Test
    public void testBounds() throws IOException {
        final FileReader reader = new FileReader(COUNTRIES_FILE);
        SpatialRuleLookup spatialRuleLookup = SpatialRuleLookupBuilder.buildIndex(Jackson.newObjectMapper().readValue(reader, JsonFeatureCollection.class), "ISO_A3", new CountriesSpatialRuleFactory(), .1, new BBox(-180, 180, -90, 90));
        BBox almostWorldWide = new BBox(-179, 179, -89, 89);

        // Might fail if a polygon is defined outside the above coordinates
        assertTrue("BBox seems to be not contracted", almostWorldWide.contains(spatialRuleLookup.getBounds()));
    }

    @Test
    public void testIntersection() throws IOException {
        /*
         We are creating a BBox smaller than Germany. We have the German Spatial rule activated by default.
         So the BBox should not contain a Point lying somewhere close in Germany.
        */
        final FileReader reader = new FileReader(COUNTRIES_FILE);
        SpatialRuleLookup spatialRuleLookup = SpatialRuleLookupBuilder.buildIndex(Jackson.newObjectMapper().readValue(reader, JsonFeatureCollection.class), "ISO_A3", new CountriesSpatialRuleFactory(), .1, new BBox(9, 10, 51, 52));
        assertFalse("BBox seems to be incorrectly contracted", spatialRuleLookup.getBounds().contains(49.9, 8.9));
    }

    @Test
    public void testNoIntersection() throws IOException {
        final FileReader reader = new FileReader(COUNTRIES_FILE);
        SpatialRuleLookup spatialRuleLookup = SpatialRuleLookupBuilder.buildIndex(Jackson.newObjectMapper().readValue(reader, JsonFeatureCollection.class), "ISO_A3", new CountriesSpatialRuleFactory(), .1, new BBox(-180, -179, -90, -89));
        assertEquals(SpatialRuleLookup.EMPTY, spatialRuleLookup);
    }

}
