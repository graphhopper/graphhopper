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

package com.graphhopper.routing.ch;

import com.graphhopper.config.CHProfileConfig;
import com.graphhopper.config.LMProfileConfig;
import com.graphhopper.config.ProfileConfig;
import com.graphhopper.routing.ProfileResolver;
import com.graphhopper.routing.util.*;
import com.graphhopper.util.Parameters;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class CHProfileSelectorTest {

    private static final String MULTIPLE_MATCHES_ERROR = "There are multiple CH profiles matching your request. Use the `weighting`,`vehicle`,`turn_costs` and/or `u_turn_costs` parameters to be more specific";
    private static final String NO_MATCH_ERROR = "Cannot find matching profile that supports CH for your request";

    private ProfileConfig fastCar;
    private ProfileConfig fastCarEdge;
    private ProfileConfig fastCarEdge10;
    private ProfileConfig fastCarEdge30;
    private ProfileConfig fastCarEdge50;
    private ProfileConfig fastBike;
    private ProfileConfig fastBikeEdge40;
    private ProfileConfig shortCar;
    private ProfileConfig shortBike;
    private EncodingManager encodingManager;

    @BeforeEach
    public void setup() {
        FlagEncoder carEncoder = new CarFlagEncoder();
        FlagEncoder bikeEncoder = new BikeFlagEncoder();
        encodingManager = EncodingManager.create(carEncoder, bikeEncoder);
        fastCar = new ProfileConfig("fast_car").setWeighting("fastest").setVehicle("car").setTurnCosts(false);
        fastCarEdge = new ProfileConfig("fast_car_edge").setWeighting("fastest").setVehicle("car").setTurnCosts(true);
        fastCarEdge10 = new ProfileConfig("fast_car_edge10").setWeighting("fastest").setVehicle("car").setTurnCosts(true).putHint(Parameters.Routing.U_TURN_COSTS, 10);
        fastCarEdge30 = new ProfileConfig("fast_car_edge30").setWeighting("fastest").setVehicle("car").setTurnCosts(true).putHint(Parameters.Routing.U_TURN_COSTS, 30);
        fastCarEdge50 = new ProfileConfig("fast_car_edge50").setWeighting("fastest").setVehicle("car").setTurnCosts(true).putHint(Parameters.Routing.U_TURN_COSTS, 50);
        fastBike = new ProfileConfig("fast_bike").setWeighting("fastest").setVehicle("bike").setTurnCosts(false);
        fastBikeEdge40 = new ProfileConfig("fast_bike_edge40").setWeighting("fastest").setVehicle("bike").setTurnCosts(true).putHint(Parameters.Routing.U_TURN_COSTS, 40);
        shortCar = new ProfileConfig("short_car").setWeighting("shortest").setVehicle("car").setTurnCosts(false);
        shortBike = new ProfileConfig("short_bike").setWeighting("shortest").setVehicle("bike").setTurnCosts(false);
    }

    @Test
    public void onlyNodeBasedPresent() {
        List<ProfileConfig> profiles = Collections.singletonList(
                fastCar
        );
        List<CHProfileConfig> chProfiles = Collections.singletonList(
                new CHProfileConfig("fast_car")
        );
        assertCHProfileSelectionError(NO_MATCH_ERROR, profiles, chProfiles, true, null);
        assertCHProfileSelectionError(NO_MATCH_ERROR, profiles, chProfiles, true, 20);
        assertProfileFound(profiles.get(0), profiles, chProfiles, false, null);
        assertCHProfileSelectionError(NO_MATCH_ERROR, profiles, chProfiles, false, 20);
        assertProfileFound(profiles.get(0), profiles, chProfiles, null, null);
        assertCHProfileSelectionError(NO_MATCH_ERROR, profiles, chProfiles, null, 30);
        String error = assertCHProfileSelectionError(NO_MATCH_ERROR, profiles, chProfiles, "foot", "fastest", false, null);
        assertTrue(error.contains("requested:  fastest|foot|turn_costs=false|u_turn_costs=*"), error);
        assertTrue(error.contains("available: [fastest|car|turn_costs=false]"), error);
    }

    @Test
    public void onlyEdgeBasedPresent() {
        List<ProfileConfig> profiles = Collections.singletonList(fastCarEdge);
        List<CHProfileConfig> chProfiles = Collections.singletonList(new CHProfileConfig("fast_car_edge"));
        assertCHProfileSelectionError(NO_MATCH_ERROR, profiles, chProfiles, false, null);
        assertCHProfileSelectionError(NO_MATCH_ERROR, profiles, chProfiles, false, 20);
        assertProfileFound(profiles.get(0), profiles, chProfiles, true, null);
        assertProfileFound(profiles.get(0), profiles, chProfiles, null, null);
    }

    @Test
    public void edgeAndNodePresent() {
        List<ProfileConfig> profiles = Arrays.asList(
                fastCar,
                fastCarEdge
        );
        List<CHProfileConfig> chProfiles = Arrays.asList(
                new CHProfileConfig("fast_car"),
                new CHProfileConfig("fast_car_edge")
        );
        // in case edge-based is not specified we prefer the edge-based profile over the node-based one
        assertProfileFound(profiles.get(1), profiles, chProfiles, null, null);
        assertProfileFound(profiles.get(0), profiles, chProfiles, false, null);
        assertProfileFound(profiles.get(1), profiles, chProfiles, true, null);
    }

    @Test
    public void multipleEdgeBased() {
        List<ProfileConfig> profiles = Arrays.asList(
                fastCar,
                fastCarEdge30,
                fastCarEdge50
        );
        List<CHProfileConfig> chProfiles = Arrays.asList(
                new CHProfileConfig("fast_car"),
                new CHProfileConfig("fast_car_edge30"),
                new CHProfileConfig("fast_car_edge50")
        );
        // when no u-turns are specified we throw
        assertCHProfileSelectionError(MULTIPLE_MATCHES_ERROR, profiles, chProfiles, true, null);
        // when we request one that does not exist we throw
        assertCHProfileSelectionError(NO_MATCH_ERROR, profiles, chProfiles, true, 40);
        // when we request one that exists it works
        assertProfileFound(profiles.get(1), profiles, chProfiles, true, 30);

        // without specifying edge-based we also get an error
        assertProfileFound(profiles.get(1), profiles, chProfiles, null, 30);
        assertCHProfileSelectionError(NO_MATCH_ERROR, profiles, chProfiles, null, 40);
        String error = assertCHProfileSelectionError(MULTIPLE_MATCHES_ERROR, profiles, chProfiles, null, null);
        assertTrue(error.contains("requested:  fastest|car|turn_costs=*|u_turn_costs=*"), error);
        assertTrue(error.contains("matched:   [fastest|car|turn_costs=false, fastest|car|turn_costs=true|u_turn_costs=30, fastest|car|turn_costs=true|u_turn_costs=50]"), error);
        assertTrue(error.contains("available: [fastest|car|turn_costs=false, fastest|car|turn_costs=true|u_turn_costs=30, fastest|car|turn_costs=true|u_turn_costs=50]"), error);
    }

    @Test
    public void missingVehicleOrWeighting() {
        // when we do not set the weighting and/or the car but it can be derived from the profile the profile is returned
        List<ProfileConfig> profiles = Collections.singletonList(fastCar);
        List<CHProfileConfig> chProfiles = Collections.singletonList(new CHProfileConfig("fast_car"));
        assertProfileFound(profiles.get(0), profiles, chProfiles, "car", "fastest", null, null);
        assertProfileFound(profiles.get(0), profiles, chProfiles, "car", "", null, null);
        assertProfileFound(profiles.get(0), profiles, chProfiles, "", "fastest", null, null);
        assertProfileFound(profiles.get(0), profiles, chProfiles, "", "", null, null);
    }

    @Test
    public void missingVehicleOrWeighting_otherVehicleAndCar() {
        List<ProfileConfig> profiles = Collections.singletonList(shortBike);
        List<CHProfileConfig> chProfiles = Collections.singletonList(new CHProfileConfig("short_bike"));
        assertProfileFound(profiles.get(0), profiles, chProfiles, "bike", "shortest", null, null);
        assertProfileFound(profiles.get(0), profiles, chProfiles, "bike", "", null, null);
        assertProfileFound(profiles.get(0), profiles, chProfiles, "", "shortest", null, null);
        assertProfileFound(profiles.get(0), profiles, chProfiles, "", "", null, null);
    }


    @Test
    public void missingVehicleMultipleProfiles() {
        List<ProfileConfig> profiles = Arrays.asList(
                fastBike,
                shortBike,
                fastBikeEdge40
        );
        List<CHProfileConfig> chProfiles = Arrays.asList(
                new CHProfileConfig("fast_bike"),
                new CHProfileConfig("short_bike"),
                new CHProfileConfig("fast_bike_edge40")
        );
        // the vehicle is not given but only bike is used so its fine. note that we prefer edge-based because no edge_based parameter is specified
        assertProfileFound(profiles.get(2), profiles, chProfiles, "", "fastest", null, null);
        // if we do not specify the weighting its not clear what to return -> there is an error
        assertCHProfileSelectionError(MULTIPLE_MATCHES_ERROR, profiles, chProfiles, "", "", null, null);
        // if we do not specify the weighting but edge_based=true its clear what to return because for edge-based there is only one weighting
        assertProfileFound(profiles.get(2), profiles, chProfiles, "", "", true, null);
        // ... for edge_based=false this is an error because there are two node-based profiles
        assertCHProfileSelectionError(MULTIPLE_MATCHES_ERROR, profiles, chProfiles, "", "", false, null);
    }

    @Test
    public void missingWeightingMultipleProfiles() {
        List<ProfileConfig> profiles = Arrays.asList(
                fastBike,
                fastCarEdge10,
                fastBikeEdge40
        );
        List<CHProfileConfig> chProfiles = Arrays.asList(
                new CHProfileConfig("fast_bike"),
                new CHProfileConfig("fast_car_edge10"),
                new CHProfileConfig("fast_bike_edge40")
        );
        // the weighting is not given but only fastest is used so its fine. note that we prefer edge-based because no edge_based parameter is specified
        assertProfileFound(profiles.get(2), profiles, chProfiles, "bike", "", null, null);
        // if we do not specify the vehicle its not clear what to return -> there is an error
        assertCHProfileSelectionError(MULTIPLE_MATCHES_ERROR, profiles, chProfiles, "", "", null, null);
        // if we do not specify the vehicle but edge_based=false its clear what to return because for node-based there is only one weighting
        assertProfileFound(profiles.get(0), profiles, chProfiles, "", "", false, null);
        // ... for edge_based=true this is an error, because there are two edge_based profiles
        assertCHProfileSelectionError(MULTIPLE_MATCHES_ERROR, profiles, chProfiles, "", "", true, null);
        // ... we can however get a clear match if we specify the u-turn costs
        assertProfileFound(profiles.get(1), profiles, chProfiles, "", "", true, 10);
    }

    @Test
    public void multipleVehiclesMissingWeighting() {
        // this is a common use-case, there are multiple vehicles for one weighting
        EncodingManager em = EncodingManager.create("car,bike,motorcycle,bike2,foot");
        List<ProfileConfig> profiles = new ArrayList<>();
        List<CHProfileConfig> chProfiles = new ArrayList<>();
        for (FlagEncoder encoder : em.fetchEdgeEncoders()) {
            profiles.add(new ProfileConfig(encoder.toString()).setVehicle(encoder.toString()).setWeighting("short_fastest").setTurnCosts(false));
            chProfiles.add(new CHProfileConfig(encoder.toString()));
        }
        // we do not specify the weighting but this is ok, because there is only one in use
        String weighting = "";
        assertProfileFound(profiles.get(0), profiles, chProfiles, "car", weighting, null, null);
        assertProfileFound(profiles.get(1), profiles, chProfiles, "bike", weighting, null, null);
        assertProfileFound(profiles.get(2), profiles, chProfiles, "motorcycle", weighting, null, null);
        assertProfileFound(profiles.get(3), profiles, chProfiles, "bike2", weighting, null, null);
        assertProfileFound(profiles.get(4), profiles, chProfiles, "foot", weighting, null, null);
    }


    @Test
    public void missingVehicle_multiplePossibilities_throws() {
        List<ProfileConfig> profiles = Arrays.asList(
                fastBike,
                fastCar
        );
        List<CHProfileConfig> chProfiles = Arrays.asList(
                new CHProfileConfig("fast_bike"),
                new CHProfileConfig("fast_car")
        );
        assertCHProfileSelectionError(MULTIPLE_MATCHES_ERROR, profiles, chProfiles, "", "fastest", null, null);
    }

    @Test
    public void missingWeighting_multiplePossibilities_throws() {
        List<ProfileConfig> profiles = Arrays.asList(
                fastBike,
                shortBike
        );
        List<CHProfileConfig> chProfiles = Arrays.asList(
                new CHProfileConfig("fast_bike"),
                new CHProfileConfig("short_bike")
        );
        assertCHProfileSelectionError(MULTIPLE_MATCHES_ERROR, profiles, chProfiles, "bike", "", null, null);
    }

    private void assertProfileFound(ProfileConfig expectedProfile, List<ProfileConfig> profiles, List<CHProfileConfig> chProfiles, Boolean edgeBased, Integer uTurnCosts) {
        assertProfileFound(expectedProfile, profiles, chProfiles, "car", "fastest", edgeBased, uTurnCosts);
    }

    private void assertProfileFound(ProfileConfig expectedProfile, List<ProfileConfig> profiles, List<CHProfileConfig> chProfiles, String vehicle, String weighting, Boolean edgeBased, Integer uTurnCosts) {
        HintsMap hintsMap = createHintsMap(vehicle, weighting, edgeBased, uTurnCosts);
        try {
            ProfileConfig selectedProfile = new ProfileResolver(encodingManager, profiles, chProfiles, Collections.<LMProfileConfig>emptyList()).selectProfileCH(hintsMap);
            assertEquals(expectedProfile, selectedProfile);
        } catch (IllegalArgumentException e) {
            fail("no profile found\nexpected: " + expectedProfile + "\nerror: " + e.getMessage());
        }
    }

    private String assertCHProfileSelectionError(String expectedError, List<ProfileConfig> profiles, List<CHProfileConfig> chProfiles, Boolean edgeBased, Integer uTurnCosts) {
        return assertCHProfileSelectionError(expectedError, profiles, chProfiles, "car", "fastest", edgeBased, uTurnCosts);
    }

    private String assertCHProfileSelectionError(String expectedError, List<ProfileConfig> profiles, List<CHProfileConfig> chProfiles, String vehicle, String weighting, Boolean edgeBased, Integer uTurnCosts) {
        HintsMap hintsMap = createHintsMap(vehicle, weighting, edgeBased, uTurnCosts);
        try {
            new ProfileResolver(encodingManager, profiles, chProfiles, Collections.<LMProfileConfig>emptyList()).selectProfileCH(hintsMap);
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