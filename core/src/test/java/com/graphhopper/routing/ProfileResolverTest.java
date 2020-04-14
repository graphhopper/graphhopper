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

package com.graphhopper.routing;

import com.graphhopper.config.CHProfileConfig;
import com.graphhopper.config.LMProfileConfig;
import com.graphhopper.config.ProfileConfig;
import com.graphhopper.routing.ch.CHProfileSelectorTest;
import com.graphhopper.routing.lm.LMProfileSelectorTest;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.routing.util.HintsMap;
import com.graphhopper.util.Parameters;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;

/**
 * So far this test is only testing the profile selection in the absence of CH/LM profiles. For CH/LM profile selection
 *
 * @see CHProfileSelectorTest
 * @see LMProfileSelectorTest
 */
public class ProfileResolverTest {
    @Test
    public void defaultVehicle() {
        ProfileResolver profileResolver = new ProfileResolver(
                EncodingManager.create("car,foot,bike"),
                Arrays.asList(
                        new ProfileConfig("my_bike").setVehicle("bike"),
                        new ProfileConfig("your_car").setVehicle("car")
                ),
                Collections.<CHProfileConfig>emptyList(), Collections.<LMProfileConfig>emptyList());
        // without specifying the vehicle we get an error, because there are multiple matches
        assertMultiMatchError(profileResolver, new HintsMap(), "There are multiple profiles matching your request");
        // use vehicle to specify profile
        assertEquals("your_car", profileResolver.resolveProfile(new HintsMap().setVehicle("car")).getName());
        assertEquals("my_bike", profileResolver.resolveProfile(new HintsMap().setVehicle("bike")).getName());
    }

    @Test
    public void defaultWeighting() {
        ProfileResolver profileResolver = new ProfileResolver(
                EncodingManager.create("bike,car,foot"),
                Arrays.asList(
                        new ProfileConfig("fast_bike").setVehicle("bike").setWeighting("fastest"),
                        new ProfileConfig("short_bike").setVehicle("bike").setWeighting("shortest")
                ),
                Collections.<CHProfileConfig>emptyList(), Collections.<LMProfileConfig>emptyList());
        // without specifying the weighting we get an error, because there are multiple matches
        assertMultiMatchError(profileResolver, new HintsMap(), "There are multiple profiles matching your request");
        // use weighting to specify profile
        assertEquals("short_bike", profileResolver.resolveProfile(new HintsMap().setWeighting("shortest")).getName());
        assertEquals("fast_bike", profileResolver.resolveProfile(new HintsMap().setWeighting("fastest")).getName());
    }

    @Test
    public void missingProfiles() {
        ProfileResolver profileResolver = new ProfileResolver(
                EncodingManager.create("car,bike"),
                Arrays.asList(
                        new ProfileConfig("fast_bike").setVehicle("bike").setWeighting("fastest"),
                        new ProfileConfig("short_bike").setVehicle("bike").setWeighting("shortest")
                ),
                Collections.<CHProfileConfig>emptyList(), Collections.<LMProfileConfig>emptyList());
        // there is a car encoder but no associated profile
        assertProfileNotFound(profileResolver, new HintsMap().setVehicle("car"));
        // if we do not specify a vehicle or weighting we even have multiple matches
        assertMultiMatchError(profileResolver, new HintsMap(), "There are multiple profiles matching your request");
        // if we specify the weighting its clear which profile we want
        assertEquals("short_bike", profileResolver.resolveProfile(new HintsMap().setWeighting("shortest")).getName());
        // setting the vehicle to bike is not enough
        assertMultiMatchError(profileResolver, new HintsMap().setVehicle("bike"), "There are multiple profiles matching your request");
        // if we set the weighting as well it works
        assertEquals("fast_bike", profileResolver.resolveProfile(new HintsMap().setVehicle("bike").setWeighting("fastest")).getName());
        assertEquals("short_bike", profileResolver.resolveProfile(new HintsMap().setVehicle("bike").setWeighting("shortest")).getName());
    }

    @Test
    public void edgeBasedAndTurnCosts() {
        ProfileResolver profileResolver = new ProfileResolver(
                EncodingManager.create("foot"),
                Collections.singletonList(new ProfileConfig("profile").setVehicle("foot").setWeighting("fastest")),
                Collections.<CHProfileConfig>emptyList(), Collections.<LMProfileConfig>emptyList());

        assertProfileNotFound(profileResolver, new HintsMap().putObject(Parameters.Routing.EDGE_BASED, true));
        assertEquals("profile", profileResolver.resolveProfile(new HintsMap()).getName());
        assertEquals("profile", profileResolver.resolveProfile(new HintsMap().putObject(Parameters.Routing.EDGE_BASED, false)).getName());
    }

    @Test
    public void defaultVehicleAllAlgos() {
        final String profile1 = "foot_profile";
        final String profile2 = "car_profile";
        final String vehicle1 = "foot";
        final String vehicle2 = "car";
        final String weighting = "shortest";

        ProfileResolver profileResolver = new ProfileResolver(
                EncodingManager.create(vehicle1 + "," + vehicle2),
                Arrays.asList(
                        new ProfileConfig(profile1).setVehicle(vehicle1).setWeighting(weighting),
                        new ProfileConfig(profile2).setVehicle(vehicle2).setWeighting(weighting)
                ),
                Arrays.asList(new CHProfileConfig(profile1), new CHProfileConfig(profile2)),
                Arrays.asList(new LMProfileConfig(profile1), new LMProfileConfig(profile2))
        );
        // when we do not specify vehicle/weighting, we get an error because there are multiple matches
        HintsMap hints = new HintsMap();
        assertMultiMatchError(profileResolver, hints, "There are multiple CH profiles matching your request");
        assertMultiMatchError(profileResolver, hints.putObject(Parameters.CH.DISABLE, true), "There are multiple LM profiles matching your request");
        assertMultiMatchError(profileResolver, hints.putObject(Parameters.Landmark.DISABLE, true), "There are multiple profiles matching your request");

        // using the weighting is not enough, because its the same for both profiles
        hints = new HintsMap().setWeighting("shortest");
        assertMultiMatchError(profileResolver, hints, "There are multiple CH profiles matching your request");
        assertMultiMatchError(profileResolver, hints.putObject(Parameters.CH.DISABLE, true), "There are multiple LM profiles matching your request");
        assertMultiMatchError(profileResolver, hints.putObject(Parameters.Landmark.DISABLE, true), "There are multiple profiles matching your request");

        // using the vehicle to select one of the profiles works
        hints = new HintsMap().setVehicle(vehicle1);
        assertEquals(profile1, profileResolver.resolveProfile(hints).getName());
        assertEquals(profile1, profileResolver.resolveProfile(hints.putObject(Parameters.CH.DISABLE, true)).getName());
        assertEquals(profile1, profileResolver.resolveProfile(hints.putObject(Parameters.Landmark.DISABLE, true)).getName());
    }

    private void assertMultiMatchError(ProfileResolver profileResolver, HintsMap hints, String... expectedErrors) {
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

    private void assertProfileNotFound(ProfileResolver profileResolver, HintsMap hints) {
        try {
            profileResolver.resolveProfile(hints);
            fail();
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("Cannot find matching profile for your request"), e.getMessage());
        }
    }

}