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
package com.graphhopper.countries;

import com.graphhopper.routing.util.spatialrules.*;
import org.junit.Test;

import java.io.*;

import static junit.framework.TestCase.assertFalse;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * @author Robin Boldt
 */
public class CountriesTest {

    // Test class name only and fqn
    private final String countries = "GermanySpatialRule,com.graphhopper.routing.util.spatialrules.countries.AustriaSpatialRule";

    @Test
    public void testIndex() {
        Reader reader = new InputStreamReader(CountriesTest.class.getResourceAsStream("countries.geo.json"));
        SpatialRuleLookup spatialRuleLookup = Countries.buildIndex(reader, countries);

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
