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

package com.graphhopper;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.graphhopper.config.CHProfile;
import com.graphhopper.config.LMProfile;
import com.graphhopper.config.Profile;
import com.graphhopper.jackson.Jackson;
import com.graphhopper.util.Helper;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class GraphHopperProfileTest {

    private static final String GH_LOCATION = "target/gh-profile-config-gh";

    @Test
    public void deserialize() throws IOException {
        ObjectMapper objectMapper = Jackson.newObjectMapper();
        String json = "{\"name\":\"my_car\",\"weighting\":\"custom\",\"turn_costs\":{\"restrictions\":[\"motorcar\"]},\"foo\":\"bar\",\"baz\":\"buzz\"}";
        Profile profile = objectMapper.readValue(json, Profile.class);
        assertEquals("my_car", profile.getName());
        assertEquals(List.of("motorcar"), profile.getTurnCostsConfig().getRestrictions());
        assertEquals("custom", profile.getWeighting());
        assertTrue(profile.hasTurnCosts());
        assertEquals(2, profile.getHints().toMap().size());
        assertEquals("bar", profile.getHints().getString("foo", ""));
        assertEquals("buzz", profile.getHints().getString("baz", ""));
    }

    @Test
    public void duplicateProfileName_error() {
        final GraphHopper hopper = createHopper();
        assertIllegalArgument(() -> hopper.setProfiles(
                new Profile("my_profile"),
                new Profile("your_profile"),
                new Profile("my_profile")
        ), "Profile names must be unique. Duplicate name: 'my_profile'");
    }

    @Test
    public void profileWithUnknownWeighting_error() {
        final GraphHopper hopper = createHopper();
        hopper.setProfiles(new Profile("profile").setWeighting("your_weighting"));
        assertIllegalArgument(hopper::importOrLoad,
                "Could not create weighting for profile: 'profile'",
                "Weighting 'your_weighting' not supported"
        );
    }

    @Test
    public void chProfileDoesNotExist_error() {
        final GraphHopper hopper = createHopper();
        hopper.setProfiles(Profile.createTestProfile("profile1"));
        hopper.getCHPreparationHandler().setCHProfiles(new CHProfile("other_profile"));
        assertIllegalArgument(hopper::importOrLoad, "CH profile references unknown profile 'other_profile'");
    }

    @Test
    public void duplicateCHProfile_error() {
        final GraphHopper hopper = createHopper();
        hopper.setProfiles(Profile.createTestProfile("profile"));
        hopper.getCHPreparationHandler().setCHProfiles(
                new CHProfile("profile"),
                new CHProfile("profile")
        );
        assertIllegalArgument(hopper::importOrLoad, "Duplicate CH reference to profile 'profile'");
    }

    @Test
    public void lmProfileDoesNotExist_error() {
        final GraphHopper hopper = createHopper();
        hopper.setProfiles(Profile.createTestProfile("profile1"));
        hopper.getLMPreparationHandler().setLMProfiles(new LMProfile("other_profile"));
        assertIllegalArgument(hopper::importOrLoad, "LM profile references unknown profile 'other_profile'");
    }

    @Test
    public void duplicateLMProfile_error() {
        final GraphHopper hopper = createHopper();
        hopper.setProfiles(Profile.createTestProfile("profile"));
        hopper.getLMPreparationHandler().setLMProfiles(
                new LMProfile("profile"),
                new LMProfile("profile")
        );
        assertIllegalArgument(hopper::importOrLoad, "Multiple LM profiles are using the same profile 'profile'");
    }

    @Test
    public void unknownLMPreparationProfile_error() {
        final GraphHopper hopper = createHopper();
        hopper.setProfiles(Profile.createTestProfile("profile"));
        hopper.getLMPreparationHandler().setLMProfiles(
                new LMProfile("profile").setPreparationProfile("xyz")
        );
        assertIllegalArgument(hopper::importOrLoad, "LM profile references unknown preparation profile 'xyz'");
    }

    @Test
    public void lmPreparationProfileChain_error() {
        final GraphHopper hopper = createHopper();
        hopper.setProfiles(
                Profile.createTestProfile("profile1"),
                Profile.createTestProfile("profile2"),
                Profile.createTestProfile("profile3")
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
                Profile.createTestProfile("profile1"),
                Profile.createTestProfile("profile2"),
                Profile.createTestProfile("profile3")
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
