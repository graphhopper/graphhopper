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
import com.graphhopper.jackson.ProfileMixIn;
import com.graphhopper.routing.util.EncodingManager;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

public class GraphHopperProfileTest {

    private static final String GH_LOCATION = "target/gh-profile-config-gh";

    @Test
    public void deserialize() throws IOException {
        ObjectMapper objectMapper = Jackson.newObjectMapper()
                .addMixIn(Profile.class, ProfileMixIn.class);
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
        final GraphHopper hopper = createHopper(EncodingManager.create("car"));
        assertIllegalArgument(new Runnable() {
            @Override
            public void run() {
                hopper.setProfiles(
                        new Profile("my_profile").setVehicle("car").setWeighting("fastest"),
                        new Profile("your_profile").setVehicle("car").setWeighting("short_fastest"),
                        new Profile("my_profile").setVehicle("car").setWeighting("shortest")
                );
            }
        }, "Profile names must be unique. Duplicate name: 'my_profile'");
    }

    @Test
    public void vehicleDoesNotExist_error() {
        final GraphHopper hopper = createHopper(EncodingManager.create("car"));
        hopper.setProfiles(new Profile("profile").setVehicle("your_car"));
        assertIllegalArgument(new Runnable() {
            @Override
            public void run() {
                hopper.load(GH_LOCATION);
            }
        }, "Unknown vehicle 'your_car' in profile: name=profile");
    }

    @Test
    public void vehicleWithoutTurnCostSupport_error() {
        final GraphHopper hopper = createHopper(EncodingManager.create("car"));
        hopper.setProfiles(new Profile("profile").setVehicle("car").setTurnCosts(true));
        assertIllegalArgument(new Runnable() {
            @Override
            public void run() {
                hopper.load(GH_LOCATION);
            }
        }, "The profile 'profile' was configured with 'turn_costs=true', but the corresponding vehicle 'car' does not support turn costs");
    }

    @Test
    public void profileWithUnknownWeighting_error() {
        final GraphHopper hopper = createHopper(EncodingManager.create("car"));
        hopper.setProfiles(new Profile("profile").setVehicle("car").setWeighting("your_weighting"));
        assertIllegalArgument(new Runnable() {
                                  @Override
                                  public void run() {
                                      hopper.load(GH_LOCATION);
                                  }
                              },
                "Could not create weighting for profile: 'profile'",
                "Weighting 'your_weighting' not supported"
        );
    }

    @Test
    public void chProfileDoesNotExist_error() {
        final GraphHopper hopper = createHopper(EncodingManager.create("car"));
        hopper.setProfiles(new Profile("profile1").setVehicle("car"));
        hopper.getCHPreparationHandler().setCHProfiles(new CHProfile("other_profile"));
        assertIllegalArgument(new Runnable() {
            @Override
            public void run() {
                hopper.load(GH_LOCATION);
            }
        }, "CH profile references unknown profile 'other_profile'");
    }

    @Test
    public void duplicateCHProfile_error() {
        final GraphHopper hopper = createHopper(EncodingManager.create("car"));
        hopper.setProfiles(new Profile("profile").setVehicle("car"));
        hopper.getCHPreparationHandler().setCHProfiles(
                new CHProfile("profile"),
                new CHProfile("profile")
        );
        assertIllegalArgument(new Runnable() {
            @Override
            public void run() {
                hopper.load(GH_LOCATION);
            }
        }, "Duplicate CH reference to profile 'profile'");
    }

    @Test
    public void lmProfileDoesNotExist_error() {
        final GraphHopper hopper = createHopper(EncodingManager.create("car"));
        hopper.setProfiles(new Profile("profile1").setVehicle("car"));
        hopper.getLMPreparationHandler().setLMProfiles(new LMProfile("other_profile"));
        assertIllegalArgument(new Runnable() {
            @Override
            public void run() {
                hopper.load(GH_LOCATION);
            }
        }, "LM profile references unknown profile 'other_profile'");
    }

    @Test
    public void duplicateLMProfile_error() {
        final GraphHopper hopper = createHopper(EncodingManager.create("car"));
        hopper.setProfiles(new Profile("profile").setVehicle("car"));
        hopper.getLMPreparationHandler().setLMProfiles(
                new LMProfile("profile"),
                new LMProfile("profile")
        );
        assertIllegalArgument(new Runnable() {
            @Override
            public void run() {
                hopper.load(GH_LOCATION);
            }
        }, "Multiple LM profiles are using the same profile 'profile'");
    }

    @Test
    public void unknownLMPreparationProfile_error() {
        final GraphHopper hopper = createHopper(EncodingManager.create("car"));
        hopper.setProfiles(new Profile("profile").setVehicle("car"));
        hopper.getLMPreparationHandler().setLMProfiles(
                new LMProfile("profile").setPreparationProfile("xyz")
        );
        assertIllegalArgument(new Runnable() {
            @Override
            public void run() {
                hopper.load(GH_LOCATION);
            }
        }, "LM profile references unknown preparation profile 'xyz'");
    }

    @Test
    public void lmPreparationProfileChain_error() {
        final GraphHopper hopper = createHopper(EncodingManager.create("car,bike,foot"));
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
        assertIllegalArgument(new Runnable() {
            @Override
            public void run() {
                hopper.load(GH_LOCATION);
            }
        }, "Cannot use 'profile2' as preparation_profile for LM profile 'profile3', because it uses another profile for preparation itself.");
    }

    @Test
    public void noLMProfileForPreparationProfile_error() {
        final GraphHopper hopper = createHopper(EncodingManager.create("car,bike,foot"));
        hopper.setProfiles(
                new Profile("profile1").setVehicle("car"),
                new Profile("profile2").setVehicle("bike"),
                new Profile("profile3").setVehicle("foot")
        );
        hopper.getLMPreparationHandler().setLMProfiles(
                new LMProfile("profile1").setPreparationProfile("profile2")
        );
        assertIllegalArgument(new Runnable() {
            @Override
            public void run() {
                hopper.load(GH_LOCATION);
            }
        }, "Unknown LM preparation profile 'profile2' in LM profile 'profile1' cannot be used as preparation_profile");
    }

    private GraphHopper createHopper(EncodingManager encodingManager) {
        final GraphHopper hopper = new GraphHopper();
        hopper.setGraphHopperLocation(GH_LOCATION);
        hopper.setStoreOnFlush(false);
        hopper.setEncodingManager(encodingManager);
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