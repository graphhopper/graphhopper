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
package com.graphhopper.spatialrules;

import com.graphhopper.json.GHJsonBuilder;
import com.graphhopper.json.geo.JsonFeatureCollection;
import com.graphhopper.routing.util.spatialrules.AccessValue;
import com.graphhopper.routing.util.spatialrules.SpatialRuleLookup;
import com.graphhopper.routing.util.spatialrules.TransportationMode;
import org.junit.Test;

import java.io.InputStreamReader;
import java.io.Reader;

import static org.junit.Assert.assertEquals;

/**
 * @author Robin Boldt
 */
public class SpatialRuleLookupBuilderTest {

    @Test
    public void testIndex() {
        Reader reader = new InputStreamReader(CountriesSpatialRuleFactory.class.getResourceAsStream("countries.geo.json"));
        SpatialRuleLookup spatialRuleLookup = SpatialRuleLookupBuilder.buildIndex(new GHJsonBuilder().create().fromJson(reader, JsonFeatureCollection.class), "ISO_A3", new CountriesSpatialRuleFactory());

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

}
