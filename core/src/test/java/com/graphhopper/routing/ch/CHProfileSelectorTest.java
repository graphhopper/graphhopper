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

import com.graphhopper.routing.util.*;
import com.graphhopper.routing.weighting.FastestWeighting;
import com.graphhopper.routing.weighting.ShortFastestWeighting;
import com.graphhopper.routing.weighting.ShortestWeighting;
import com.graphhopper.routing.weighting.Weighting;
import com.graphhopper.storage.CHProfile;
import com.graphhopper.util.Parameters;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static com.graphhopper.routing.weighting.Weighting.INFINITE_U_TURN_COSTS;
import static org.junit.Assert.*;

public class CHProfileSelectorTest {

    private static final String MULTIPLE_MATCHES_ERROR = "There are multiple CH profiles matching your request. Use the `weighting`,`vehicle`,`edge_based` and/or `u_turn_costs` parameters to be more specific";
    private static final String NO_MATCH_ERROR = "Cannot find matching CH profile for your request";

    private Weighting weightingFastestCar;
    private Weighting weightingFastestBike;
    private Weighting weightingShortestCar;
    private Weighting weightingShortestBike;

    @Before
    public void setup() {
        FlagEncoder carEncoder = new CarFlagEncoder();
        FlagEncoder bikeEncoder = new BikeFlagEncoder();
        EncodingManager.create(carEncoder, bikeEncoder);
        weightingFastestCar = new FastestWeighting(carEncoder);
        weightingFastestBike = new FastestWeighting(bikeEncoder);
        weightingShortestCar = new ShortestWeighting(carEncoder);
        weightingShortestBike = new ShortestWeighting(bikeEncoder);
    }

    @Test
    public void onlyNodeBasedPresent() {
        List<CHProfile> chProfiles = Collections.singletonList(
                CHProfile.nodeBased(weightingFastestCar)
        );
        assertCHProfileSelectionError(NO_MATCH_ERROR, chProfiles, true, null);
        assertCHProfileSelectionError(NO_MATCH_ERROR, chProfiles, true, 20);
        assertProfileFound(chProfiles.get(0), chProfiles, false, null);
        assertCHProfileSelectionError(NO_MATCH_ERROR, chProfiles, false, 20);
        assertProfileFound(chProfiles.get(0), chProfiles, null, null);
        assertCHProfileSelectionError(NO_MATCH_ERROR, chProfiles, null, 30);
        assertCHProfileSelectionError(NO_MATCH_ERROR, chProfiles, "foot", "fastest", false, null);
    }

    @Test
    public void onlyEdgeBasedPresent() {
        List<CHProfile> chProfiles = Collections.singletonList(
                CHProfile.edgeBased(weightingFastestCar, INFINITE_U_TURN_COSTS)
        );
        assertCHProfileSelectionError(NO_MATCH_ERROR, chProfiles, false, null);
        assertCHProfileSelectionError(NO_MATCH_ERROR, chProfiles, false, 20);
        assertProfileFound(chProfiles.get(0), chProfiles, true, null);
        assertProfileFound(chProfiles.get(0), chProfiles, null, null);
    }

    @Test
    public void edgeAndNodePresent() {
        List<CHProfile> chProfiles = Arrays.asList(
                CHProfile.nodeBased(weightingFastestCar),
                CHProfile.edgeBased(weightingFastestCar, INFINITE_U_TURN_COSTS)
        );
        // in case edge-based is not specified we prefer the edge-based profile over the node-based one
        assertProfileFound(chProfiles.get(1), chProfiles, null, null);
        assertProfileFound(chProfiles.get(0), chProfiles, false, null);
        assertProfileFound(chProfiles.get(1), chProfiles, true, null);
    }

    @Test
    public void multipleEdgeBased() {
        List<CHProfile> chProfiles = Arrays.asList(
                CHProfile.nodeBased(weightingFastestCar),
                CHProfile.edgeBased(weightingFastestCar, 30),
                CHProfile.edgeBased(weightingFastestCar, 50)
        );
        // when no u-turns are specified we throw
        assertCHProfileSelectionError(MULTIPLE_MATCHES_ERROR, chProfiles, true, null);
        // when we request one that does not exist we throw
        assertCHProfileSelectionError(NO_MATCH_ERROR, chProfiles, true, 40);
        // when we request one that exists it works
        assertProfileFound(chProfiles.get(1), chProfiles, true, 30);

        // without specifying edge-based
        assertProfileFound(chProfiles.get(1), chProfiles, null, 30);
        assertCHProfileSelectionError(NO_MATCH_ERROR, chProfiles, null, 40);
        assertCHProfileSelectionError(MULTIPLE_MATCHES_ERROR, chProfiles, null, null);
    }

    @Test
    public void missingVehicleOrWeighting() {
        // when we do not set the weighting and/or the car but it can be derived from the profile the profile is returned
        List<CHProfile> chProfiles = Collections.singletonList(CHProfile.nodeBased(weightingFastestCar));
        assertProfileFound(chProfiles.get(0), chProfiles, "car", "fastest", null, null);
        assertProfileFound(chProfiles.get(0), chProfiles, "car", "", null, null);
        assertProfileFound(chProfiles.get(0), chProfiles, "", "fastest", null, null);
        assertProfileFound(chProfiles.get(0), chProfiles, "", "", null, null);
    }

    @Test
    public void missingVehicleOrWeighting_otherVehicleAndCar() {
        List<CHProfile> chProfiles = Collections.singletonList(CHProfile.nodeBased(weightingShortestBike));
        assertProfileFound(chProfiles.get(0), chProfiles, "bike", "shortest", null, null);
        assertProfileFound(chProfiles.get(0), chProfiles, "bike", "", null, null);
        assertProfileFound(chProfiles.get(0), chProfiles, "", "shortest", null, null);
        assertProfileFound(chProfiles.get(0), chProfiles, "", "", null, null);
    }


    @Test
    public void missingVehicleMultipleProfiles() {
        List<CHProfile> chProfiles = Arrays.asList(
                CHProfile.nodeBased(weightingFastestBike),
                CHProfile.nodeBased(weightingShortestBike),
                CHProfile.edgeBased(weightingFastestBike, 40)
        );
        // the vehicle is not given but only bike is used so its fine. note that we prefer edge-based because no edge_based parameter is specified
        assertProfileFound(chProfiles.get(2), chProfiles, "", "fastest", null, null);
        // if we do not specify the weighting its not clear what to return -> there is an error
        assertCHProfileSelectionError(MULTIPLE_MATCHES_ERROR, chProfiles, "", "", null, null);
        // if we do not specify the weighting but edge_based=true its clear what to return because for edge-based there is only one weighting
        assertProfileFound(chProfiles.get(2), chProfiles, "", "", true, null);
        // ... for edge_based=false this is an error because there are two node-based profiles
        assertCHProfileSelectionError(MULTIPLE_MATCHES_ERROR, chProfiles, "", "", false, null);
    }

    @Test
    public void missingWeightingMultipleProfiles() {
        List<CHProfile> chProfiles = Arrays.asList(
                CHProfile.nodeBased(weightingFastestBike),
                CHProfile.edgeBased(weightingFastestCar, 10),
                CHProfile.edgeBased(weightingFastestBike, 40)
        );
        // the weighting is not given but only fastest is used so its fine. note that we prefer edge-based because no edge_based parameter is specified
        assertProfileFound(chProfiles.get(2), chProfiles, "bike", "", null, null);
        // if we do not specify the vehicle its not clear what to return -> there is an error
        assertCHProfileSelectionError(MULTIPLE_MATCHES_ERROR, chProfiles, "", "", null, null);
        // if we do not specify the vehicle but edge_based=false its clear what to return because for node-based there is only one weighting
        assertProfileFound(chProfiles.get(0), chProfiles, "", "", false, null);
        // ... for edge_based=true this is an error, because there are two edge_based profiles
        assertCHProfileSelectionError(MULTIPLE_MATCHES_ERROR, chProfiles, "", "", true, null);
        // .. we can however get a clear match if we specify the u-turn costs
        assertProfileFound(chProfiles.get(1), chProfiles, "", "", true, 10);
    }

    @Test
    public void multipleVehiclesMissingWeighting() {
        // this is a common use-case, there are multiple vehicles for one weighting
        EncodingManager em = EncodingManager.create("car,bike,motorcycle,bike2,foot");
        List<CHProfile> chProfiles = new ArrayList<>();
        for (FlagEncoder encoder : em.fetchEdgeEncoders()) {
            chProfiles.add(CHProfile.nodeBased(new ShortFastestWeighting(encoder, 1)));
        }
        // we do not specify the weighting but this is ok, because there is only one in use
        String weighting = "";
        assertProfileFound(chProfiles.get(0), chProfiles, "car", weighting, null, null);
        assertProfileFound(chProfiles.get(1), chProfiles, "bike", weighting, null, null);
        assertProfileFound(chProfiles.get(2), chProfiles, "motorcycle", weighting, null, null);
        assertProfileFound(chProfiles.get(3), chProfiles, "bike2", weighting, null, null);
        assertProfileFound(chProfiles.get(4), chProfiles, "foot", weighting, null, null);
    }


    @Test
    public void missingVehicle_multiplePossibilities_throws() {
        List<CHProfile> chProfiles = Arrays.asList(
                CHProfile.nodeBased(weightingFastestBike), CHProfile.nodeBased(weightingFastestCar)
        );
        assertCHProfileSelectionError(MULTIPLE_MATCHES_ERROR, chProfiles, "", "fastest", null, null);
    }

    @Test
    public void missingWeighting_multiplePossibilities_throws() {
        List<CHProfile> chProfiles = Arrays.asList(
                CHProfile.nodeBased(weightingFastestBike), CHProfile.nodeBased(weightingShortestBike)
        );
        assertCHProfileSelectionError(MULTIPLE_MATCHES_ERROR, chProfiles, "bike", "", null, null);
    }

    private void assertProfileFound(CHProfile expectedProfile, List<CHProfile> profiles, Boolean edgeBased, Integer uTurnCosts) {
        assertProfileFound(expectedProfile, profiles, "car", "fastest", edgeBased, uTurnCosts);
    }

    private void assertProfileFound(CHProfile expectedProfile, List<CHProfile> profiles, String vehicle, String weighting, Boolean edgeBased, Integer uTurnCosts) {
        HintsMap hintsMap = createHintsMap(vehicle, weighting, edgeBased, uTurnCosts);
        try {
            CHProfile selectedProfile = CHProfileSelector.select(profiles, hintsMap);
            assertEquals(expectedProfile, selectedProfile);
        } catch (CHProfileSelectionException e) {
            fail("no profile found\nexpected: " + expectedProfile + "\nerror: " + e.getMessage());
        }
    }

    private void assertCHProfileSelectionError(String expectedError, List<CHProfile> profiles, Boolean edgeBased, Integer uTurnCosts) {
        assertCHProfileSelectionError(expectedError, profiles, "car", "fastest", edgeBased, uTurnCosts);
    }

    private void assertCHProfileSelectionError(String expectedError, List<CHProfile> profiles, String vehicle, String weighting, Boolean edgeBased, Integer uTurnCosts) {
        HintsMap hintsMap = createHintsMap(vehicle, weighting, edgeBased, uTurnCosts);
        try {
            CHProfileSelector.select(profiles, hintsMap);
            fail("There should have been an error");
        } catch (CHProfileSelectionException e) {
            assertTrue("There should have been an error message containing:\n'" + expectedError + "'\nbut was:\n'" + e.getMessage() + "'",
                    e.getMessage().contains(expectedError));
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