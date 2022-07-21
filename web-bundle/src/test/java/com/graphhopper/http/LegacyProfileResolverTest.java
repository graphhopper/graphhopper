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

import com.graphhopper.config.CHProfile;
import com.graphhopper.config.LMProfile;
import com.graphhopper.config.Profile;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.util.PMap;
import com.graphhopper.util.Parameters;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * So far this test is only testing the profile selection in the absence of CH/LM profiles. For CH/LM profile selection
 *
 * @see CHLegacyProfileSelectorTest
 * @see LMLegacyProfileSelectorTest
 */
public class LegacyProfileResolverTest {
    @Test
    public void defaultVehicle() {
        LegacyProfileResolver profileResolver = new LegacyProfileResolver(
                EncodingManager.create("car,foot,bike"),
                Arrays.asList(
                        new Profile("my_bike").setVehicle("bike"),
                        new Profile("your_car").setVehicle("car")
                ),
                Collections.<CHProfile>emptyList(), Collections.<LMProfile>emptyList());
        // without specifying the vehicle we get an error, because there are multiple matches
        assertMultiMatchError(profileResolver, new PMap(), "There are multiple profiles matching your request");
        // use vehicle to specify profile
        assertEquals("your_car", profileResolver.resolveProfile(new PMap().putObject("vehicle", "car")).getName());
        assertEquals("my_bike", profileResolver.resolveProfile(new PMap().putObject("vehicle", "bike")).getName());
    }

    @Test
    public void defaultWeighting() {
        LegacyProfileResolver profileResolver = new LegacyProfileResolver(
                EncodingManager.create("bike,car,foot"),
                Arrays.asList(
                        new Profile("fast_bike").setVehicle("bike").setWeighting("fastest"),
                        new Profile("short_bike").setVehicle("bike").setWeighting("shortest")
                ),
                Collections.<CHProfile>emptyList(), Collections.<LMProfile>emptyList());
        // without specifying the weighting we get an error, because there are multiple matches
        assertMultiMatchError(profileResolver, new PMap(), "There are multiple profiles matching your request");
        // use weighting to specify profile
        assertEquals("short_bike", profileResolver.resolveProfile(new PMap().putObject("weighting", "shortest")).getName());
        assertEquals("fast_bike", profileResolver.resolveProfile(new PMap().putObject("weighting", "fastest")).getName());
    }

    @Test
    public void missingProfiles() {
        LegacyProfileResolver profileResolver = new LegacyProfileResolver(
                EncodingManager.create("car,bike"),
                Arrays.asList(
                        new Profile("fast_bike").setVehicle("bike").setWeighting("fastest"),
                        new Profile("short_bike").setVehicle("bike").setWeighting("shortest")
                ),
                Collections.<CHProfile>emptyList(), Collections.<LMProfile>emptyList());
        // there is a car encoder but no associated profile
        assertProfileNotFound(profileResolver, new PMap().putObject("vehicle", "car"));
        // if we do not specify a vehicle or weighting we even have multiple matches
        assertMultiMatchError(profileResolver, new PMap(), "There are multiple profiles matching your request");
        // if we specify the weighting its clear which profile we want
        assertEquals("short_bike", profileResolver.resolveProfile(new PMap().putObject("weighting", "shortest")).getName());
        // setting the vehicle to bike is not enough
        assertMultiMatchError(profileResolver, new PMap().putObject("vehicle", "bike"), "There are multiple profiles matching your request");
        // if we set the weighting as well it works
        assertEquals("fast_bike", profileResolver.resolveProfile(new PMap().putObject("vehicle", "bike").putObject("weighting", "fastest")).getName());
        assertEquals("short_bike", profileResolver.resolveProfile(new PMap().putObject("vehicle", "bike").putObject("weighting", "shortest")).getName());

        assertUnsupportedVehicle(profileResolver, "unknown", Arrays.asList("car", "bike"));
    }

    @Test
    public void edgeBasedAndTurnCosts() {
        LegacyProfileResolver profileResolver = new LegacyProfileResolver(
                EncodingManager.create("foot"),
                Collections.singletonList(new Profile("profile").setVehicle("foot").setWeighting("fastest")),
                Collections.emptyList(), Collections.emptyList());

        assertProfileNotFound(profileResolver, new PMap().putObject(Parameters.Routing.EDGE_BASED, true));
        assertEquals("profile", profileResolver.resolveProfile(new PMap()).getName());
        assertEquals("profile", profileResolver.resolveProfile(new PMap().putObject(Parameters.Routing.EDGE_BASED, false)).getName());
    }

    @Test
    public void defaultVehicleAllAlgos() {
        final String profile1 = "foot_profile";
        final String profile2 = "car_profile";
        final String vehicle1 = "foot";
        final String vehicle2 = "car";
        final String weighting = "shortest";

        LegacyProfileResolver profileResolver = new LegacyProfileResolver(
                EncodingManager.create(vehicle1 + "," + vehicle2),
                Arrays.asList(
                        new Profile(profile1).setVehicle(vehicle1).setWeighting(weighting),
                        new Profile(profile2).setVehicle(vehicle2).setWeighting(weighting)
                ),
                Arrays.asList(new CHProfile(profile1), new CHProfile(profile2)),
                Arrays.asList(new LMProfile(profile1), new LMProfile(profile2))
        );
        // when we do not specify vehicle/weighting, we get an error because there are multiple matches
        PMap hints = new PMap();
        assertMultiMatchError(profileResolver, hints, "There are multiple CH profiles matching your request");
        assertMultiMatchError(profileResolver, hints.putObject(Parameters.CH.DISABLE, true), "There are multiple LM profiles matching your request");
        assertMultiMatchError(profileResolver, hints.putObject(Parameters.Landmark.DISABLE, true), "There are multiple profiles matching your request");

        // using the weighting is not enough, because its the same for both profiles
        hints = new PMap().putObject("weighting", "shortest");
        assertMultiMatchError(profileResolver, hints, "There are multiple CH profiles matching your request");
        assertMultiMatchError(profileResolver, hints.putObject(Parameters.CH.DISABLE, true), "There are multiple LM profiles matching your request");
        assertMultiMatchError(profileResolver, hints.putObject(Parameters.Landmark.DISABLE, true), "There are multiple profiles matching your request");

        // using the vehicle to select one of the profiles works
        hints = new PMap().putObject("vehicle", vehicle1);
        assertEquals(profile1, profileResolver.resolveProfile(hints).getName());
        assertEquals(profile1, profileResolver.resolveProfile(hints.putObject(Parameters.CH.DISABLE, true)).getName());
        assertEquals(profile1, profileResolver.resolveProfile(hints.putObject(Parameters.Landmark.DISABLE, true)).getName());
    }

    private void assertMultiMatchError(LegacyProfileResolver profileResolver, PMap hints, String... expectedErrors) {
        if (expectedErrors.length == 0) {
            throw new IllegalArgumentException("there must be at least one expected error");
        }
        try {
            profileResolver.resolveProfile(hints);
            fail();
        } catch (IllegalArgumentException e) {
            for (String expectedError : expectedErrors) {
                assertTrue(e.getMessage().contains(expectedError), e.getMessage());
            }
        }
    }

    private void assertProfileNotFound(LegacyProfileResolver profileResolver, PMap hints) {
        try {
            profileResolver.resolveProfile(hints);
            fail();
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("Cannot find matching profile for your request"), e.getMessage());
        }
    }

    private void assertUnsupportedVehicle(LegacyProfileResolver profileResolver, String vehicle, List<String> supported) {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> profileResolver.resolveProfile(new PMap().putObject("vehicle", vehicle)));
        assertTrue(e.getMessage().contains("Vehicle not supported: `" + vehicle + "`. Supported are: `" + String.join(",", supported) + "`"), e.getMessage());
    }

}