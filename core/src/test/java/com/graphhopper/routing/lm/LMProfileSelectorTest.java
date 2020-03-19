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

    private static final String MULTIPLE_MATCHES_ERROR = "There are multiple LM profiles matching your request. Use the `weighting`, `vehicle` and `turn_costs` parameters to be more specific";
    private static final String NO_MATCH_ERROR = "Cannot find matching LM profile for your request";

    private EncodingManager encodingManager;
    private ProfileConfig fastCar;
    private ProfileConfig fastCarEdge;
    private ProfileConfig fastBike;
    private ProfileConfig shortBikeEdge;

    @BeforeEach
    public void setup() {
        FlagEncoder carEncoder = new CarFlagEncoder();
        FlagEncoder bikeEncoder = new BikeFlagEncoder();
        encodingManager = EncodingManager.create(carEncoder, bikeEncoder);
        fastCar = new ProfileConfig("fast_car").setVehicle("car").setWeighting("fastest").setTurnCosts(false);
        fastCarEdge = new ProfileConfig("fast_car_edge").setVehicle("car").setWeighting("fastest").setTurnCosts(true);
        fastBike = new ProfileConfig("fast_bike").setVehicle("bike").setWeighting("fastest").setTurnCosts(false);
        shortBikeEdge = new ProfileConfig("short_bike_edge").setVehicle("bike").setWeighting("shortest").setTurnCosts(true);
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
        assertProfileFound(profiles.get(0), profiles, lmProfiles, "car", "fastest", false, null);

        // requesting edge_based when the profile is not edge_based leads to a non-match
        assertLMProfileSelectionError(NO_MATCH_ERROR, profiles, lmProfiles, null, null, true, null);
        assertLMProfileSelectionError(NO_MATCH_ERROR, profiles, lmProfiles, "car", null, true, null);
        assertLMProfileSelectionError(NO_MATCH_ERROR, profiles, lmProfiles, null, "fastest", true, null);
        assertLMProfileSelectionError(NO_MATCH_ERROR, profiles, lmProfiles, "car", "fastest", true, null);

        // requesting u_turn_costs should not lead to a non-match
        assertProfileFound(profiles.get(0), profiles, lmProfiles, null, null, null, 54);
        assertProfileFound(profiles.get(0), profiles, lmProfiles, null, null, false, 54);
        assertProfileFound(profiles.get(0), profiles, lmProfiles, "car", null, false, 54);
        assertProfileFound(profiles.get(0), profiles, lmProfiles, null, "fastest", false, 54);
        assertProfileFound(profiles.get(0), profiles, lmProfiles, "car", "fastest", false, 54);

        // if we request something that does not fit we do not get a match
        String error = assertLMProfileSelectionError(NO_MATCH_ERROR, profiles, lmProfiles, "bike", null, null, null);
        assertTrue(error.contains("requested:  *|bike|turn_costs=*"), error);
        assertTrue(error.contains("available: [fastest|car|turn_costs=false]"), error);
        assertLMProfileSelectionError(NO_MATCH_ERROR, profiles, lmProfiles, "car", "shortest", null, null);
        assertLMProfileSelectionError(NO_MATCH_ERROR, profiles, lmProfiles, "car", null, true, null);
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
        assertTrue(error.contains("requested:  fastest|*|turn_costs=*"), error);
        assertTrue(error.contains("matched:   [fastest|car|turn_costs=false, fastest|bike|turn_costs=false]"), error);
        assertTrue(error.contains("available: [fastest|car|turn_costs=false, fastest|bike|turn_costs=false]"), error);
        assertLMProfileSelectionError(NO_MATCH_ERROR, profiles, lmProfiles, null, "shortest", null, null);

        // u_turn_costs is set, but lm should not really care
        assertProfileFound(profiles.get(0), profiles, lmProfiles, "car", null, null, 54);
        assertProfileFound(profiles.get(0), profiles, lmProfiles, "car", null, false, 64);
    }

    @Test
    public void withAndWithoutTurnCosts() {
        List<ProfileConfig> profiles = Arrays.asList(
                fastCar,
                fastBike,
                fastCarEdge,
                shortBikeEdge
        );
        List<LMProfileConfig> lmProfiles = Arrays.asList(
                new LMProfileConfig("fast_car"),
                new LMProfileConfig("fast_bike"),
                new LMProfileConfig("fast_car_edge"),
                new LMProfileConfig("short_bike_edge")
        );
        // edge_based can be used to select between otherwise identical profiles
        assertProfileFound(profiles.get(0), profiles, lmProfiles, "car", null, false, null);
        assertProfileFound(profiles.get(2), profiles, lmProfiles, "car", null, true, null);

        // in case there are two matching profiles and they are only different by turn_costs=true/false the one with
        // turn costs is preferred (just like for CH)
        assertProfileFound(profiles.get(2), profiles, lmProfiles, "car", null, null, null);

        // not being specific enough leads to multiple matching profiles error
        assertLMProfileSelectionError(MULTIPLE_MATCHES_ERROR, profiles, lmProfiles, null, "fastest", null, null);

        // we get an error if we request turn_costs that are not supported
        assertLMProfileSelectionError(NO_MATCH_ERROR, profiles, lmProfiles, "bike", "fastest", true, null);
        assertLMProfileSelectionError(NO_MATCH_ERROR, profiles, lmProfiles, "bike", "shortest", false, null);
        assertLMProfileSelectionError(NO_MATCH_ERROR, profiles, lmProfiles, null, "shortest", false, null);

        // vehicle&weighting are derived from the available profiles in case they are not given
        assertProfileFound(profiles.get(0), profiles, lmProfiles, "car", null, false, null);
        assertProfileFound(profiles.get(1), profiles, lmProfiles, "bike", null, false, null);
        assertProfileFound(profiles.get(2), profiles, lmProfiles, null, "fastest", true, null);
        assertProfileFound(profiles.get(3), profiles, lmProfiles, null, "shortest", true, null);
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
            hintsMap.putObject(Parameters.Routing.EDGE_BASED, edgeBased);
        }
        if (uTurnCosts != null) {
            hintsMap.putObject(Parameters.Routing.U_TURN_COSTS, uTurnCosts);
        }
        return hintsMap;
    }
}