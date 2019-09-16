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

import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.routing.util.FlagEncoder;
import com.graphhopper.routing.util.HintsMap;
import com.graphhopper.routing.weighting.FastestWeighting;
import com.graphhopper.routing.weighting.Weighting;
import com.graphhopper.storage.CHProfile;
import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static com.graphhopper.routing.weighting.TurnWeighting.INFINITE_U_TURN_COSTS;
import static org.junit.Assert.*;

public class CHProfileSelectorTest {

    private Weighting fastestWeighting;

    @Before
    public void setup() {
        EncodingManager em = EncodingManager.create("car");
        FlagEncoder carEncoder = em.fetchEdgeEncoders().iterator().next();
        fastestWeighting = new FastestWeighting(carEncoder);
    }

    @Test
    public void onlyNodeBasedPresent() {
        List<CHProfile> chProfiles = Collections.singletonList(
                CHProfile.nodeBased(fastestWeighting)
        );
        assertCHProfileSelectionError("Found a node-based CH profile for weighting map {weighting=fastest, vehicle=car}, but requested edge-based CH", chProfiles, true, null);
        assertCHProfileSelectionError("Found a node-based CH profile for weighting map {weighting=fastest, vehicle=car}, but requested edge-based CH", chProfiles, true, 20);
        assertProfileFound(chProfiles.get(0), chProfiles, false, null);
        assertProfileFound(chProfiles.get(0), chProfiles, false, 20);
        assertProfileFound(chProfiles.get(0), chProfiles, null, null);
        assertProfileFound(chProfiles.get(0), chProfiles, null, 30);
        assertCHProfileSelectionError("Cannot find CH profile for weighting map", chProfiles, "foot", "fastest", false, null);
    }

    @Test
    public void onlyEdgeBasedPresent() {
        List<CHProfile> chProfiles = Collections.singletonList(
                CHProfile.edgeBased(fastestWeighting, INFINITE_U_TURN_COSTS)
        );
        assertCHProfileSelectionError("Found 1 edge-based CH profile(s) for weighting map {weighting=fastest, vehicle=car}, but requested node-based CH", chProfiles, false, null);
        assertCHProfileSelectionError("Found 1 edge-based CH profile(s) for weighting map {weighting=fastest, vehicle=car}, but requested node-based CH", chProfiles, false, 20);
        assertProfileFound(chProfiles.get(0), chProfiles, true, null);
        assertProfileFound(chProfiles.get(0), chProfiles, null, null);
    }

    @Test
    public void edgeAndNodePresent() {
        List<CHProfile> chProfiles = Arrays.asList(
                CHProfile.nodeBased(fastestWeighting),
                CHProfile.edgeBased(fastestWeighting, INFINITE_U_TURN_COSTS)
        );
        // in case edge-based is not specified we prefer the edge-based profile over the node-based one
        assertProfileFound(chProfiles.get(1), chProfiles, null, null);
        assertProfileFound(chProfiles.get(0), chProfiles, false, null);
        assertProfileFound(chProfiles.get(1), chProfiles, true, null);
    }

    @Test
    public void multipleEdgeBased() {
        List<CHProfile> chProfiles = Arrays.asList(
                CHProfile.nodeBased(fastestWeighting),
                CHProfile.edgeBased(fastestWeighting, 30),
                CHProfile.edgeBased(fastestWeighting, 50)
        );
        // when no u-turns are specified we throw
        assertCHProfileSelectionError("Found matching edge-based CH profiles for multiple values of u-turn costs: [30, 50].",
                chProfiles, true, null);
        // when we request one that does not exist we throw
        assertCHProfileSelectionError("but none for requested u-turn costs: 40, available: [30, 50]", chProfiles, true, 40);
        // when we request one that exists it works
        assertProfileFound(chProfiles.get(1), chProfiles, true, 30);

        // without specifying edge-based
        assertProfileFound(chProfiles.get(1), chProfiles, null, 30);
        assertCHProfileSelectionError("but none for requested u-turn costs: 40, available: [30, 50", chProfiles, null, 40);
        assertCHProfileSelectionError("Found matching edge-based CH profiles for multiple values of u-turn costs: [30, 50].", chProfiles, null, null);
    }

    private void assertProfileFound(CHProfile expectedProfile, List<CHProfile> profiles, Boolean edgeBased, Integer uTurnCosts) {
        assertProfileFound(expectedProfile, profiles, "car", "fastest", edgeBased, uTurnCosts);
    }

    private void assertProfileFound(CHProfile expectedProfile, List<CHProfile> profiles, String vehicle, String weighting, Boolean edgeBased, Integer uTurnCosts) {
        HintsMap weightingMap = new HintsMap().setWeighting(weighting).setVehicle(vehicle);
        try {
            CHProfile selectedProfile = CHProfileSelector.select(profiles, weightingMap, edgeBased, uTurnCosts);
            assertEquals(expectedProfile, selectedProfile);
        } catch (CHProfileSelectionException e) {
            fail("no profile found, but expected: " + expectedProfile + ", error: " + e.getMessage());
        }
    }

    private void assertCHProfileSelectionError(String expectedError, List<CHProfile> profiles, Boolean edgeBased, Integer uTurnCosts) {
        assertCHProfileSelectionError(expectedError, profiles, "car", "fastest", edgeBased, uTurnCosts);
    }

    private void assertCHProfileSelectionError(String expectedError, List<CHProfile> profiles, String vehicle, String weighting, Boolean edgeBased, Integer uTurnCosts) {
        HintsMap weightingMap = new HintsMap().setWeighting(weighting).setVehicle(vehicle);
        try {
            CHProfileSelector.select(profiles, weightingMap, edgeBased, uTurnCosts);
            fail("There should have been an error");
        } catch (CHProfileSelectionException e) {
            assertTrue("There should have been an error message containing '" + expectedError + "', but was: '" + e.getMessage() + "'",
                    e.getMessage().contains(expectedError));
        }
    }
}