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

package com.graphhopper.core;

import com.graphhopper.core.GraphHopper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.graphhopper.config.CHProfile;
import com.graphhopper.config.LMProfile;
import com.graphhopper.config.Profile;
import com.graphhopper.jackson.Jackson;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

public class GraphHopperProfileTest {

    private static final String GH_LOCATION = "target/gh-profile-config-gh";

    @Test
    public void deserialize() throws IOException {
        ObjectMapper objectMapper = Jackson.newObjectMapper();
        String json = "{\"name\":\"my_car\",\"vehicle\":\"car\",\"weighting\":\"fastest\",\"turn_costs\":true,\"foo\":\"bar\",\"baz\":\"buzz\"}";
        Profile profile = objectMapper.readValue(json, Profile.class);
        assertEquals("my_car", profile.getName());
        assertEquals("car", profile.getVehicle());
        assertEquals("fastest", profile.getWeighting());
        assertTrue(profile.isTurnCosts());
        assertEquals(2, profile.getHints().toMap().size());
        assertEquals("bar", profile.getHints().getString("foo", ""));
        assertEquals("buzz", profile.getHints().getString("baz", ""));
    }

    @Test
    public void duplicateProfileName_error() {
        final GraphHopper hopper = createHopper();
        assertIllegalArgument(() -> hopper.setProfiles(
                new Profile("my_profile").setVehicle("car").setWeighting("fastest"),
                new Profile("your_profile").setVehicle("car").setWeighting("short_fastest"),
                new Profile("my_profile").setVehicle("car").setWeighting("shortest")
        ), "Profile names must be unique. Duplicate name: 'my_profile'");
    }

    @Test
    public void vehicleDoesNotExist_error() {
        final GraphHopper hopper = new GraphHopper();
        hopper.setGraphHopperLocation(GH_LOCATION).setStoreOnFlush(false).
                setProfiles(new Profile("profile").setVehicle("your_car"));
        assertIllegalArgument(hopper::importOrLoad, "entry in vehicle list not supported: your_car");
    }

    @Test
    public void vehicleDoesNotExist_error2() {
        final GraphHopper hopper = new GraphHopper().setGraphHopperLocation(GH_LOCATION).setStoreOnFlush(false).
                setProfiles(new Profile("profile").setVehicle("your_car"));
        assertIllegalArgument(hopper::importOrLoad, "entry in vehicle list not supported: your_car");
    }

    @Test
    public void oneVehicleTwoProfilesWithAndWithoutTC_noError() {
        final GraphHopper hopper = createHopper();
        hopper.setProfiles(
                new Profile("profile1").setVehicle("car").setTurnCosts(false),
                new Profile("profile2").setVehicle("car").setTurnCosts(true));
        hopper.load();
    }

    @Test
    public void oneVehicleTwoProfilesWithAndWithoutTC2_noError() {
        final GraphHopper hopper = createHopper();
        hopper.setProfiles(
                new Profile("profile2").setVehicle("car").setTurnCosts(true),
                new Profile("profile1").setVehicle("car").setTurnCosts(false));
        hopper.load();
    }

    @Test
    public void profileWithUnknownWeighting_error() {
        final GraphHopper hopper = createHopper();
        hopper.setProfiles(new Profile("profile").setVehicle("car").setWeighting("your_weighting"));
        assertIllegalArgument(hopper::importOrLoad,
                "Could not create weighting for profile: 'profile'",
                "Weighting 'your_weighting' not supported"
        );
    }

    @Test
    public void chProfileDoesNotExist_error() {
        final GraphHopper hopper = createHopper();
        hopper.setProfiles(new Profile("profile1").setVehicle("car"));
        hopper.getCHPreparationHandler().setCHProfiles(new CHProfile("other_profile"));
        assertIllegalArgument(hopper::importOrLoad, "CH profile references unknown profile 'other_profile'");
    }

    @Test
    public void duplicateCHProfile_error() {
        final GraphHopper hopper = createHopper();
        hopper.setProfiles(new Profile("profile").setVehicle("car"));
        hopper.getCHPreparationHandler().setCHProfiles(
                new CHProfile("profile"),
                new CHProfile("profile")
        );
        assertIllegalArgument(hopper::importOrLoad, "Duplicate CH reference to profile 'profile'");
    }

    @Test
    public void lmProfileDoesNotExist_error() {
        final GraphHopper hopper = createHopper();
        hopper.setProfiles(new Profile("profile1").setVehicle("car"));
        hopper.getLMPreparationHandler().setLMProfiles(new LMProfile("other_profile"));
        assertIllegalArgument(hopper::importOrLoad, "LM profile references unknown profile 'other_profile'");
    }

    @Test
    public void duplicateLMProfile_error() {
        final GraphHopper hopper = createHopper();
        hopper.setProfiles(new Profile("profile").setVehicle("car"));
        hopper.getLMPreparationHandler().setLMProfiles(
                new LMProfile("profile"),
                new LMProfile("profile")
        );
        assertIllegalArgument(hopper::importOrLoad, "Multiple LM profiles are using the same profile 'profile'");
    }

    @Test
    public void unknownLMPreparationProfile_error() {
        final GraphHopper hopper = createHopper();
        hopper.setProfiles(new Profile("profile").setVehicle("car"));
        hopper.getLMPreparationHandler().setLMProfiles(
                new LMProfile("profile").setPreparationProfile("xyz")
        );
        assertIllegalArgument(hopper::importOrLoad, "LM profile references unknown preparation profile 'xyz'");
    }

    @Test
    public void lmPreparationProfileChain_error() {
        final GraphHopper hopper = createHopper();
        hopper.setProfiles(
                new Profile("profile1").setVehicle("car"),
                new Profile("profile2").setVehicle("bike"),
                new Profile("profile3").setVehicle("foot")
        );
        hopper.getLMPreparationHandler().setLMProfiles(
                new LMProfile("profile1"),
                new LMProfile("profile2").setPreparationProfile("profile1"),
                new LMProfile("profile3").setPreparationProfile("profile2")
        );
        assertIllegalArgument(hopper::importOrLoad, "Cannot use 'profile2' as preparation_profile for LM profile 'profile3', because it uses another profile for preparation itself.");
    }

    @Test
    public void noLMProfileForPreparationProfile_error() {
        final GraphHopper hopper = createHopper();
        hopper.setProfiles(
                new Profile("profile1").setVehicle("car"),
                new Profile("profile2").setVehicle("bike"),
                new Profile("profile3").setVehicle("foot")
        );
        hopper.getLMPreparationHandler().setLMProfiles(
                new LMProfile("profile1").setPreparationProfile("profile2")
        );
        assertIllegalArgument(hopper::importOrLoad, "Unknown LM preparation profile 'profile2' in LM profile 'profile1' cannot be used as preparation_profile");
    }

    private GraphHopper createHopper() {
        final GraphHopper hopper = new GraphHopper();
        hopper.setGraphHopperLocation(GH_LOCATION);
        hopper.setStoreOnFlush(false);
        return hopper;
    }

    private static void assertIllegalArgument(Runnable runnable, String... messageParts) {
        try {
            runnable.run();
            fail("There should have been an error containing:\n\t" + Arrays.asList(messageParts));
        } catch (IllegalArgumentException e) {
            for (String messagePart : messageParts) {
                assertTrue(e.getMessage().contains(messagePart), "Unexpected error message:\n\t" + e.getMessage() + "\nExpected the message to contain:\n\t" + messagePart);
            }
        }
    }
}