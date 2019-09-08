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

import com.carrotsearch.hppc.IntObjectHashMap;
import com.carrotsearch.hppc.IntObjectMap;
import com.graphhopper.routing.util.HintsMap;
import com.graphhopper.storage.CHProfile;
import com.graphhopper.util.Parameters;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * This class is used to determine the appropriate CH profile given (or not given) some (request) parameters
 */
public class CHProfileSelector {
    private final List<CHProfile> chProfiles;
    private HintsMap weightingMap;
    private IntObjectMap<CHProfile> edgeBasedCHProfilesByUTurnCosts;
    private CHProfile nodeBasedCHProfile;
    private List<String> entriesStrs;

    private CHProfileSelector(List<CHProfile> chProfiles) {
        this.chProfiles = chProfiles;
    }

    /**
     * @param chProfiles   the CH profiles to choose from
     * @param weightingMap a map used to specify the weighting that shall be used
     * @param edgeBased    whether or not edge-based CH shall be used or null if not specified explicitly
     * @param uTurnCosts   specifies which value the u-turn costs of the CH profile should have, or null
     * @throws CHProfileSelectionException if no CH profile could be selected for the given parameters
     */
    public static CHProfile select(
            List<CHProfile> chProfiles, HintsMap weightingMap, Boolean edgeBased, Integer uTurnCosts) {
        return new CHProfileSelector(chProfiles).select(weightingMap, edgeBased, uTurnCosts);
    }

    private CHProfile select(HintsMap weightingMap, Boolean edgeBased, Integer uTurnCosts) {
        this.weightingMap = weightingMap;
        findCHProfilesMatchingWeighting();

        if (!foundCHProfilesMatchingWeighting()) {
            throw new CHProfileSelectionException("Cannot find CH profile for weighting map " + weightingMap + " in entries: " + entriesStrs + ".");
        }

        if (edgeBased != null && uTurnCosts != null) {
            return selectUsingEdgeBasedAndUTurnCosts(edgeBased, uTurnCosts);
        } else if (edgeBased != null) {
            return selectUsingEdgeBased(edgeBased);
        } else if (uTurnCosts != null) {
            return selectUsingUTurnCosts(uTurnCosts);
        } else {
            return selectUsingWeightingOnly();
        }
    }

    private CHProfile selectUsingEdgeBasedAndUTurnCosts(boolean edgeBased, int uTurnCosts) {
        if (edgeBased) {
            CHProfile edgeBasedCHProfile = edgeBasedCHProfilesByUTurnCosts.get(uTurnCosts);
            if (edgeBasedCHProfile != null) {
                return edgeBasedCHProfile;
            } else if (!edgeBasedCHProfilesByUTurnCosts.isEmpty()) {
                return throwFoundEdgeBasedButNotForRequestedUTurnCosts(uTurnCosts);
            } else {
                return throwRequestedEdgeBasedButOnlyFoundNodeBased();
            }
        } else {
            if (nodeBasedCHProfile != null) {
                return nodeBasedCHProfile;
            } else {
                return throwRequestedNodeBasedButOnlyFoundEdgeBased();
            }
        }
    }

    private CHProfile selectUsingEdgeBased(boolean edgeBased) {
        if (edgeBased) {
            // u-turn costs were not specified, so either there is only one edge-based profile and we take it
            // or we throw an error
            if (edgeBasedCHProfilesByUTurnCosts.size() == 1) {
                return edgeBasedCHProfilesByUTurnCosts.iterator().next().value;
            } else if (edgeBasedCHProfilesByUTurnCosts.isEmpty()) {
                return throwRequestedEdgeBasedButOnlyFoundNodeBased();
            } else {
                return throwFoundEdgeBasedButUnclearWhichOneToTake();
            }
        } else {
            if (nodeBasedCHProfile != null) {
                return nodeBasedCHProfile;
            } else {
                return throwRequestedNodeBasedButOnlyFoundEdgeBased();
            }
        }
    }

    private CHProfile selectUsingUTurnCosts(int uTurnCosts) {
        // no edge_based parameter was set, we determine the CH profile based on what is there (and prefer edge-based
        // if we can choose)
        CHProfile edgeBasedPCH = edgeBasedCHProfilesByUTurnCosts.get(uTurnCosts);
        if (edgeBasedPCH != null) {
            return edgeBasedPCH;
        } else if (!edgeBasedCHProfilesByUTurnCosts.isEmpty()) {
            return throwFoundEdgeBasedButNotForRequestedUTurnCosts(uTurnCosts);
        } else {
            return nodeBasedCHProfile;
        }
    }

    private CHProfile selectUsingWeightingOnly() {
        if (edgeBasedCHProfilesByUTurnCosts.size() == 1) {
            return edgeBasedCHProfilesByUTurnCosts.iterator().next().value;
        } else if (!edgeBasedCHProfilesByUTurnCosts.isEmpty()) {
            return throwFoundEdgeBasedButUnclearWhichOneToTake();
        } else {
            return nodeBasedCHProfile;
        }
    }

    private void findCHProfilesMatchingWeighting() {
        entriesStrs = new ArrayList<>();
        edgeBasedCHProfilesByUTurnCosts = new IntObjectHashMap<>(3);
        nodeBasedCHProfile = null;
        for (CHProfile p : chProfiles) {
            boolean weightingMatches = p.getWeighting().matches(weightingMap);
            if (weightingMatches) {
                if (p.isEdgeBased()) {
                    edgeBasedCHProfilesByUTurnCosts.put(p.getUTurnCostsInt(), p);
                } else {
                    nodeBasedCHProfile = p;
                }
            }
            entriesStrs.add(p.toString());
        }
    }

    private boolean foundCHProfilesMatchingWeighting() {
        return !edgeBasedCHProfilesByUTurnCosts.isEmpty() || nodeBasedCHProfile != null;
    }

    private CHProfile throwFoundEdgeBasedButUnclearWhichOneToTake() {
        int[] availableUTurnCosts = edgeBasedCHProfilesByUTurnCosts.keys().toArray();
        Arrays.sort(availableUTurnCosts);
        throw new CHProfileSelectionException("Found matching edge-based CH profiles for multiple values of u-turn costs: " + Arrays.toString(availableUTurnCosts) +
                ". You need to specify which one to use using the `" + Parameters.Routing.U_TURN_COSTS + "' parameter");
    }

    private CHProfile throwRequestedNodeBasedButOnlyFoundEdgeBased() {
        throw new CHProfileSelectionException("Found " + edgeBasedCHProfilesByUTurnCosts.size() + " edge-based CH profile(s) for weighting map " + weightingMap
                + ", but requested node-based CH. You either need to configure a node-based CH profile or set the '" + Parameters.Routing.EDGE_BASED + "' " +
                "request parameter to 'true' (was 'false'). all entries: " + entriesStrs);
    }

    private CHProfile throwRequestedEdgeBasedButOnlyFoundNodeBased() {
        throw new CHProfileSelectionException("Found a node-based CH profile for weighting map " + weightingMap + ", but requested edge-based CH. " +
                "You either need to configure an edge-based CH profile or set the '" + Parameters.Routing.EDGE_BASED + "' " +
                "request parameter to 'false' (was 'true'). all entries: " + entriesStrs);
    }

    private CHProfile throwFoundEdgeBasedButNotForRequestedUTurnCosts(int uTurnCosts) {
        int[] availableUTurnCosts = edgeBasedCHProfilesByUTurnCosts.keys().toArray();
        Arrays.sort(availableUTurnCosts);
        throw new CHProfileSelectionException("Found edge-based CH profiles for weighting map " + weightingMap + " but none for requested u-turn costs: " +
                uTurnCosts + ", available: " + Arrays.toString(availableUTurnCosts) + ". You need to configure an edge-based CH profile for this value of u-turn costs or" +
                " choose another value using the '" + Parameters.Routing.U_TURN_COSTS + "' request parameter.");
    }
}
