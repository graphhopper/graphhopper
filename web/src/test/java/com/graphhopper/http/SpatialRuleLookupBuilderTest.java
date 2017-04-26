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

import com.graphhopper.routing.util.spatialrules.*;
import com.graphhopper.util.shapes.BBox;
import org.junit.Test;

import java.io.*;

import static junit.framework.TestCase.assertFalse;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * @author Robin Boldt
 */
public class SpatialRuleLookupBuilderTest {

    @Test
    public void testIndex() {
        Reader reader = new InputStreamReader(SpatialRuleLookupBuilderTest.class.getResourceAsStream("countries.geo.json"));
        SpatialRuleLookup spatialRuleLookup = GraphHopperModule.buildSpatialRuleLookup(reader, new BBox(-180, 180, -90, 90));

        // Berlin
        assertEquals(AccessValue.EVENTUALLY_ACCESSIBLE, spatialRuleLookup.lookupRule(52.5243700, 13.4105300).getAccessValue("track", TransportationMode.MOTOR_VEHICLE, AccessValue.ACCESSIBLE));
        assertEquals(AccessValue.ACCESSIBLE, spatialRuleLookup.lookupRule(52.5243700, 13.4105300).getAccessValue("primary", TransportationMode.MOTOR_VEHICLE, AccessValue.ACCESSIBLE));

        // Paris -> empty rule
        assertEquals(AccessValue.ACCESSIBLE, spatialRuleLookup.lookupRule(48.864716, 2.349014).getAccessValue("track", TransportationMode.MOTOR_VEHICLE, AccessValue.ACCESSIBLE));
        assertEquals(AccessValue.ACCESSIBLE, spatialRuleLookup.lookupRule(48.864716, 2.349014).getAccessValue("primary", TransportationMode.MOTOR_VEHICLE, AccessValue.ACCESSIBLE));

        // Vienna
        assertEquals(AccessValue.ACCESSIBLE, spatialRuleLookup.lookupRule(48.210033, 16.363449).getAccessValue("track", TransportationMode.MOTOR_VEHICLE, AccessValue.ACCESSIBLE));
        assertEquals(AccessValue.ACCESSIBLE, spatialRuleLookup.lookupRule(48.210033, 16.363449).getAccessValue("primary", TransportationMode.MOTOR_VEHICLE, AccessValue.ACCESSIBLE));
        assertEquals(AccessValue.EVENTUALLY_ACCESSIBLE, spatialRuleLookup.lookupRule(48.210033, 16.363449).getAccessValue("living_street", TransportationMode.MOTOR_VEHICLE, AccessValue.ACCESSIBLE));
    }

    @Test
    public void testBounds() {
        Reader reader = new InputStreamReader(SpatialRuleLookupBuilderTest.class.getResourceAsStream("countries.geo.json"));
        SpatialRuleLookup spatialRuleLookup = GraphHopperModule.buildSpatialRuleLookup(reader, new BBox(-180, 180, -90, 90));
        BBox almostWorldWide = new BBox(-179, 179, -89, 89);

        // Might fail if a polygon is defined outside the above coordinates
        assertTrue("BBox seems to be not contracted", almostWorldWide.contains(spatialRuleLookup.getBounds()));
    }

    @Test
    public void testIntersection() {
        /*
            We are creating a BBox smaller than Germany. We have the German Spatial rule activated by default.
            So the BBox should not contain a Point lying somewhere close in Germany.
         */
        Reader reader = new InputStreamReader(SpatialRuleLookupBuilderTest.class.getResourceAsStream("countries.geo.json"));
        SpatialRuleLookup spatialRuleLookup = GraphHopperModule.buildSpatialRuleLookup(reader, new BBox(9, 10, 51, 52));
        assertFalse("BBox seems to be incorrectly contracted", spatialRuleLookup.getBounds().contains(49.9, 8.9));
    }

    @Test
    public void testNoIntersection() {
        Reader reader = new InputStreamReader(SpatialRuleLookupBuilderTest.class.getResourceAsStream("countries.geo.json"));
        SpatialRuleLookup spatialRuleLookup = GraphHopperModule.buildSpatialRuleLookup(reader, new BBox(-180, -179, -90, -89));
        assertNull(spatialRuleLookup);
    }
}
