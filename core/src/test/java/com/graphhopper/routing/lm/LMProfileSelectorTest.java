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

package com.graphhopper.routing.lm;

import com.graphhopper.config.CHProfileConfig;
import com.graphhopper.config.LMProfileConfig;
import com.graphhopper.config.ProfileConfig;
import com.graphhopper.routing.ProfileResolver;
import com.graphhopper.routing.util.*;
import com.graphhopper.util.Parameters;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;


public class LMProfileSelectorTest {

    private static final String MULTIPLE_MATCHES_ERROR = "There are multiple LM profiles matching your request. Use the `weighting` and `vehicle` parameters to be more specific";
    private static final String NO_MATCH_ERROR = "Cannot find matching LM profile for your request";

    private EncodingManager encodingManager;
    private ProfileConfig fastCar;
    private ProfileConfig fastBike;

    @BeforeEach
    public void setup() {
        FlagEncoder carEncoder = new CarFlagEncoder();
        FlagEncoder bikeEncoder = new BikeFlagEncoder();
        encodingManager = EncodingManager.create(carEncoder, bikeEncoder);
        fastCar = new ProfileConfig("fast_car").setVehicle("car").setWeighting("fastest");
        fastBike = new ProfileConfig("fast_bike").setVehicle("bike").setWeighting("fastest");
    }

    @Test
    public void singleProfile() {
        List<ProfileConfig> profiles = Arrays.asList(
                fastCar
        );
        List<LMProfileConfig> lmProfiles = Arrays.asList(
                new LMProfileConfig("fast_car")
        );
        // as long as we do not request something that does not fit the existing profile we have a match
        assertProfileFound(profiles.get(0), profiles, lmProfiles, null, null, null, null);
        assertProfileFound(profiles.get(0), profiles, lmProfiles, "car", null, null, null);
        assertProfileFound(profiles.get(0), profiles, lmProfiles, null, "fastest", null, null);
        assertProfileFound(profiles.get(0), profiles, lmProfiles, "car", "fastest", null, null);

        // requesting edge_based or u_turn_costs should not lead to a non-match
        assertProfileFound(profiles.get(0), profiles, lmProfiles, null, null, true, null);
        assertProfileFound(profiles.get(0), profiles, lmProfiles, null, null, false, null);
        assertProfileFound(profiles.get(0), profiles, lmProfiles, null, null, null, 54);
        assertProfileFound(profiles.get(0), profiles, lmProfiles, null, null, true, 54);
        assertProfileFound(profiles.get(0), profiles, lmProfiles, "car", null, true, 54);
        assertProfileFound(profiles.get(0), profiles, lmProfiles, null, "fastest", true, 54);
        assertProfileFound(profiles.get(0), profiles, lmProfiles, "car", "fastest", true, null);
        assertProfileFound(profiles.get(0), profiles, lmProfiles, "car", "fastest", true, 54);

        // if we request something that does not fit we do not get a match
        String error = assertLMProfileSelectionError(NO_MATCH_ERROR, profiles, lmProfiles, "bike", null, null, null);
        assertTrue(error.contains("available: [fastest|car]"), error);
        assertLMProfileSelectionError(NO_MATCH_ERROR, profiles, lmProfiles, "car", "shortest", null, null);
        assertLMProfileSelectionError(NO_MATCH_ERROR, profiles, lmProfiles, null, "shortest", null, null);
        assertLMProfileSelectionError(NO_MATCH_ERROR, profiles, lmProfiles, "truck", "short_fastest", null, null);
    }

    @Test
    public void multipleProfiles() {
        List<ProfileConfig> profiles = Arrays.asList(
                fastCar,
                fastBike
        );
        List<LMProfileConfig> lmProfiles = Arrays.asList(
                new LMProfileConfig("fast_car"),
                new LMProfileConfig("fast_bike")
        );
        assertProfileFound(profiles.get(0), profiles, lmProfiles, "car", null, null, null);
        assertProfileFound(profiles.get(0), profiles, lmProfiles, "car", "fastest", null, null);
        assertProfileFound(profiles.get(1), profiles, lmProfiles, "bike", null, null, null);
        assertProfileFound(profiles.get(1), profiles, lmProfiles, "bike", "fastest", null, null);

        // not specific enough
        String error = assertLMProfileSelectionError(MULTIPLE_MATCHES_ERROR, profiles, lmProfiles, null, "fastest", null, null);
        assertTrue(error.contains("requested:  fastest|*"), error);
        assertTrue(error.contains("matched:   [fastest|car, fastest|bike]"), error);
        assertTrue(error.contains("available: [fastest|car, fastest|bike]"), error);
        assertLMProfileSelectionError(NO_MATCH_ERROR, profiles, lmProfiles, null, "shortest", null, null);

        // edge_based/u_turn_costs is set, but lm should not really care
        assertProfileFound(profiles.get(0), profiles, lmProfiles, "car", null, null, null);
        assertProfileFound(profiles.get(0), profiles, lmProfiles, "car", null, true, null);
        assertProfileFound(profiles.get(0), profiles, lmProfiles, "car", null, null, 54);
        assertProfileFound(profiles.get(0), profiles, lmProfiles, "car", null, false, 64);
    }


    private void assertProfileFound(ProfileConfig expectedProfile, List<ProfileConfig> profiles, List<LMProfileConfig> lmProfiles, String vehicle, String weighting, Boolean edgeBased, Integer uTurnCosts) {
        HintsMap hintsMap = createHintsMap(vehicle, weighting, edgeBased, uTurnCosts);
        try {
            ProfileConfig selectedProfile = new ProfileResolver(encodingManager, profiles, Collections.<CHProfileConfig>emptyList(), lmProfiles).selectProfileLM(hintsMap);
            assertEquals(expectedProfile, selectedProfile);
        } catch (IllegalArgumentException e) {
            fail("no profile found\nexpected: " + expectedProfile + "\nerror: " + e.getMessage());
        }
    }

    private String assertLMProfileSelectionError(String expectedError, List<ProfileConfig> profiles, List<LMProfileConfig> lmProfiles, String vehicle, String weighting, Boolean edgeBased, Integer uTurnCosts) {
        HintsMap hintsMap = createHintsMap(vehicle, weighting, edgeBased, uTurnCosts);
        try {
            new ProfileResolver(encodingManager, profiles, Collections.<CHProfileConfig>emptyList(), lmProfiles).selectProfileLM(hintsMap);
            fail("There should have been an error");
            return "";
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains(expectedError),
                    "There should have been an error message containing:\n'" + expectedError + "'\nbut was:\n'" + e.getMessage() + "'");
            return e.getMessage();
        }
    }

    private HintsMap createHintsMap(String vehicle, String weighting, Boolean edgeBased, Integer uTurnCosts) {
        HintsMap hintsMap = new HintsMap().setWeighting(weighting).setVehicle(vehicle);
        if (edgeBased != null) {
            hintsMap.put(Parameters.Routing.EDGE_BASED, edgeBased);
        }
        if (uTurnCosts != null) {
            hintsMap.put(Parameters.Routing.U_TURN_COSTS, uTurnCosts);
        }
        return hintsMap;
    }
}