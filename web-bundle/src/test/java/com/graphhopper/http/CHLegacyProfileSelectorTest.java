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
import com.graphhopper.routing.ev.BooleanEncodedValue;
import com.graphhopper.routing.ev.DecimalEncodedValue;
import com.graphhopper.routing.ev.VehicleAccess;
import com.graphhopper.routing.ev.VehicleSpeed;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.util.PMap;
import com.graphhopper.util.Parameters;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class CHLegacyProfileSelectorTest {

    private static final String MULTIPLE_MATCHES_ERROR = "There are multiple CH profiles matching your request. Use the `weighting`,`vehicle`,`turn_costs` and/or `u_turn_costs` parameters to be more specific";
    private static final String NO_MATCH_ERROR = "Cannot find matching profile that supports CH for your request";

    private Profile fastCar;
    private Profile fastCarEdge;
    private Profile fastCarEdge10;
    private Profile fastCarEdge30;
    private Profile fastCarEdge50;
    private Profile fastBike;
    private Profile fastBikeEdge40;
    private Profile shortCar;
    private Profile shortBike;
    private EncodingManager encodingManager;

    @BeforeEach
    public void setup() {
        BooleanEncodedValue carAccessEnc = VehicleAccess.create("car");
        DecimalEncodedValue carSpeedEnc = VehicleSpeed.create("car", 5, 5, false);
        BooleanEncodedValue bikeAccessEnc = VehicleAccess.create("bike");
        DecimalEncodedValue bikeSpeedEnc = VehicleSpeed.create("bike", 4, 2, false);
        encodingManager = EncodingManager.start()
                .add(carAccessEnc).add(carSpeedEnc)
                .add(bikeAccessEnc).add(bikeSpeedEnc)
                .build();
        fastCar = new Profile("fast_car").setWeighting("fastest").setVehicle("car").setTurnCosts(false);
        fastCarEdge = new Profile("fast_car_edge").setWeighting("fastest").setVehicle("car").setTurnCosts(true);
        fastCarEdge10 = new Profile("fast_car_edge10").setWeighting("fastest").setVehicle("car").setTurnCosts(true).putHint(Parameters.Routing.U_TURN_COSTS, 10);
        fastCarEdge30 = new Profile("fast_car_edge30").setWeighting("fastest").setVehicle("car").setTurnCosts(true).putHint(Parameters.Routing.U_TURN_COSTS, 30);
        fastCarEdge50 = new Profile("fast_car_edge50").setWeighting("fastest").setVehicle("car").setTurnCosts(true).putHint(Parameters.Routing.U_TURN_COSTS, 50);
        fastBike = new Profile("fast_bike").setWeighting("fastest").setVehicle("bike").setTurnCosts(false);
        fastBikeEdge40 = new Profile("fast_bike_edge40").setWeighting("fastest").setVehicle("bike").setTurnCosts(true).putHint(Parameters.Routing.U_TURN_COSTS, 40);
        shortCar = new Profile("short_car").setWeighting("shortest").setVehicle("car").setTurnCosts(false);
        shortBike = new Profile("short_bike").setWeighting("shortest").setVehicle("bike").setTurnCosts(false);
    }

    @Test
    public void onlyNodeBasedPresent() {
        List<Profile> profiles = Collections.singletonList(
                fastCar
        );
        List<CHProfile> chProfiles = Collections.singletonList(
                new CHProfile("fast_car")
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
        List<Profile> profiles = Collections.singletonList(fastCarEdge);
        List<CHProfile> chProfiles = Collections.singletonList(new CHProfile("fast_car_edge"));
        assertCHProfileSelectionError(NO_MATCH_ERROR, profiles, chProfiles, false, null);
        assertCHProfileSelectionError(NO_MATCH_ERROR, profiles, chProfiles, false, 20);
        assertProfileFound(profiles.get(0), profiles, chProfiles, true, null);
        assertProfileFound(profiles.get(0), profiles, chProfiles, null, null);
    }

    @Test
    public void edgeAndNodePresent() {
        List<Profile> profiles = Arrays.asList(
                fastCar,
                fastCarEdge
        );
        List<CHProfile> chProfiles = Arrays.asList(
                new CHProfile("fast_car"),
                new CHProfile("fast_car_edge")
        );
        // in case edge-based is not specified we prefer the edge-based profile over the node-based one
        assertProfileFound(profiles.get(1), profiles, chProfiles, null, null);
        assertProfileFound(profiles.get(0), profiles, chProfiles, false, null);
        assertProfileFound(profiles.get(1), profiles, chProfiles, true, null);
    }

    @Test
    public void multipleEdgeBased() {
        List<Profile> profiles = Arrays.asList(
                fastCar,
                fastCarEdge30,
                fastCarEdge50
        );
        List<CHProfile> chProfiles = Arrays.asList(
                new CHProfile("fast_car"),
                new CHProfile("fast_car_edge30"),
                new CHProfile("fast_car_edge50")
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
        List<Profile> profiles = Collections.singletonList(fastCar);
        List<CHProfile> chProfiles = Collections.singletonList(new CHProfile("fast_car"));
        assertProfileFound(profiles.get(0), profiles, chProfiles, "car", "fastest", null, null);
        assertProfileFound(profiles.get(0), profiles, chProfiles, "car", null, null, null);
        assertProfileFound(profiles.get(0), profiles, chProfiles, null, "fastest", null, null);
        assertProfileFound(profiles.get(0), profiles, chProfiles, null, null, null, null);
    }

    @Test
    public void missingVehicleOrWeighting_otherVehicleAndCar() {
        List<Profile> profiles = Collections.singletonList(shortBike);
        List<CHProfile> chProfiles = Collections.singletonList(new CHProfile("short_bike"));
        assertProfileFound(profiles.get(0), profiles, chProfiles, "bike", "shortest", null, null);
        assertProfileFound(profiles.get(0), profiles, chProfiles, "bike", null, null, null);
        assertProfileFound(profiles.get(0), profiles, chProfiles, null, "shortest", null, null);
        assertProfileFound(profiles.get(0), profiles, chProfiles, null, null, null, null);
    }


    @Test
    public void missingVehicleMultipleProfiles() {
        List<Profile> profiles = Arrays.asList(
                fastBike,
                shortBike,
                fastBikeEdge40
        );
        List<CHProfile> chProfiles = Arrays.asList(
                new CHProfile("fast_bike"),
                new CHProfile("short_bike"),
                new CHProfile("fast_bike_edge40")
        );
        // the vehicle is not given but only bike is used so its fine. note that we prefer edge-based because no edge_based parameter is specified
        assertProfileFound(profiles.get(2), profiles, chProfiles, null, "fastest", null, null);
        // if we do not specify the weighting its not clear what to return -> there is an error
        assertCHProfileSelectionError(MULTIPLE_MATCHES_ERROR, profiles, chProfiles, null, null, null, null);
        // if we do not specify the weighting but edge_based=true its clear what to return because for edge-based there is only one weighting
        assertProfileFound(profiles.get(2), profiles, chProfiles, null, null, true, null);
        // ... for edge_based=false this is an error because there are two node-based profiles
        assertCHProfileSelectionError(MULTIPLE_MATCHES_ERROR, profiles, chProfiles, null, null, false, null);
    }

    @Test
    public void missingWeightingMultipleProfiles() {
        List<Profile> profiles = Arrays.asList(
                fastBike,
                fastCarEdge10,
                fastBikeEdge40
        );
        List<CHProfile> chProfiles = Arrays.asList(
                new CHProfile("fast_bike"),
                new CHProfile("fast_car_edge10"),
                new CHProfile("fast_bike_edge40")
        );
        // the weighting is not given but only fastest is used so its fine. note that we prefer edge-based because no edge_based parameter is specified
        assertProfileFound(profiles.get(2), profiles, chProfiles, "bike", null, null, null);
        // if we do not specify the vehicle its not clear what to return -> there is an error
        assertCHProfileSelectionError(MULTIPLE_MATCHES_ERROR, profiles, chProfiles, null, null, null, null);
        // if we do not specify the vehicle but edge_based=false its clear what to return because for node-based there is only one weighting
        assertProfileFound(profiles.get(0), profiles, chProfiles, null, null, false, null);
        // ... for edge_based=true this is an error, because there are two edge_based profiles
        assertCHProfileSelectionError(MULTIPLE_MATCHES_ERROR, profiles, chProfiles, null, null, true, null);
        // ... we can however get a clear match if we specify the u-turn costs
        assertProfileFound(profiles.get(1), profiles, chProfiles, null, null, true, 10);
    }

    @Test
    public void multipleVehiclesMissingWeighting() {
        // this is a common use-case, there are multiple vehicles for one weighting
        List<Profile> profiles = new ArrayList<>();
        List<CHProfile> chProfiles = new ArrayList<>();
        for (String vehicle : Arrays.asList("car", "bike", "motorcycle", "bike2", "foot")) {
            profiles.add(new Profile(vehicle).setVehicle(vehicle).setWeighting("short_fastest").setTurnCosts(false));
            chProfiles.add(new CHProfile(vehicle));
        }
        // we do not specify the weighting but this is ok, because there is only one in use
        String weighting = null;
        assertProfileFound(profiles.get(0), profiles, chProfiles, "car", weighting, null, null);
        assertProfileFound(profiles.get(1), profiles, chProfiles, "bike", weighting, null, null);
        assertProfileFound(profiles.get(2), profiles, chProfiles, "motorcycle", weighting, null, null);
        assertProfileFound(profiles.get(3), profiles, chProfiles, "bike2", weighting, null, null);
        assertProfileFound(profiles.get(4), profiles, chProfiles, "foot", weighting, null, null);
    }


    @Test
    public void missingVehicle_multiplePossibilities_throws() {
        List<Profile> profiles = Arrays.asList(
                fastBike,
                fastCar
        );
        List<CHProfile> chProfiles = Arrays.asList(
                new CHProfile("fast_bike"),
                new CHProfile("fast_car")
        );
        assertCHProfileSelectionError(MULTIPLE_MATCHES_ERROR, profiles, chProfiles, null, "fastest", null, null);
    }

    @Test
    public void missingWeighting_multiplePossibilities_throws() {
        List<Profile> profiles = Arrays.asList(
                fastBike,
                shortBike
        );
        List<CHProfile> chProfiles = Arrays.asList(
                new CHProfile("fast_bike"),
                new CHProfile("short_bike")
        );
        assertCHProfileSelectionError(MULTIPLE_MATCHES_ERROR, profiles, chProfiles, "bike", null, null, null);
    }

    private void assertProfileFound(Profile expectedProfile, List<Profile> profiles, List<CHProfile> chProfiles, Boolean edgeBased, Integer uTurnCosts) {
        assertProfileFound(expectedProfile, profiles, chProfiles, "car", "fastest", edgeBased, uTurnCosts);
    }

    private void assertProfileFound(Profile expectedProfile, List<Profile> profiles, List<CHProfile> chProfiles, String vehicle, String weighting, Boolean edgeBased, Integer uTurnCosts) {
        PMap hintsMap = createHintsMap(vehicle, weighting, edgeBased, uTurnCosts);
        try {
            Profile selectedProfile = new LegacyProfileResolver(encodingManager, profiles, chProfiles, Collections.<LMProfile>emptyList()).selectProfileCH(hintsMap);
            assertEquals(expectedProfile, selectedProfile);
        } catch (IllegalArgumentException e) {
            fail("no profile found\nexpected: " + expectedProfile + "\nerror: " + e.getMessage());
        }
    }

    private String assertCHProfileSelectionError(String expectedError, List<Profile> profiles, List<CHProfile> chProfiles, Boolean edgeBased, Integer uTurnCosts) {
        return assertCHProfileSelectionError(expectedError, profiles, chProfiles, "car", "fastest", edgeBased, uTurnCosts);
    }

    private String assertCHProfileSelectionError(String expectedError, List<Profile> profiles, List<CHProfile> chProfiles, String vehicle, String weighting, Boolean edgeBased, Integer uTurnCosts) {
        PMap hintsMap = createHintsMap(vehicle, weighting, edgeBased, uTurnCosts);
        try {
            new LegacyProfileResolver(encodingManager, profiles, chProfiles, Collections.<LMProfile>emptyList()).selectProfileCH(hintsMap);
            fail("There should have been an error");
            return "";
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains(expectedError),
                    "There should have been an error message containing:\n'" + expectedError + "'\nbut was:\n'" + e.getMessage() + "'");
            return e.getMessage();
        }
    }

    private PMap createHintsMap(String vehicle, String weighting, Boolean edgeBased, Integer uTurnCosts) {
        PMap hintsMap = new PMap();
        if (weighting != null)
            hintsMap.putObject("weighting", weighting);
        if (vehicle != null)
            hintsMap.putObject("vehicle", vehicle);
        if (edgeBased != null)
            hintsMap.putObject(Parameters.Routing.EDGE_BASED, edgeBased);
        if (uTurnCosts != null)
            hintsMap.putObject(Parameters.Routing.U_TURN_COSTS, uTurnCosts);
        return hintsMap;
    }
}