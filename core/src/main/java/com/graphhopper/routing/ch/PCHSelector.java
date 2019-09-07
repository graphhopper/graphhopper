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
import com.graphhopper.util.Parameters;

import java.util.ArrayList;
import java.util.List;

/**
 * This class is used to determine the appropriate CH preparation given (or not given) some (request) parameters
 */
//todonow: add tests
//todonow: for better testability make this class only deal with CHProfiles
public class PCHSelector {
    private final List<PrepareContractionHierarchies> preparations;
    private HintsMap weightingMap;
    private IntObjectMap<PrepareContractionHierarchies> edgeBasedPCHsByUTurnCosts;
    private PrepareContractionHierarchies nodeBasedPCH;
    private List<String> entriesStrs;

    private PCHSelector(List<PrepareContractionHierarchies> preparations) {
        this.preparations = preparations;
    }

    /**
     * @param preparations the CH preparations to choose from
     * @param weightingMap a map used to specify the weighting that shall be used
     * @param edgeBased    whether or not edge-based CH shall be used or null if not specified explicitly
     * @param uTurnCosts   specifies which value the u-turn costs of the CH preparation should have, or null
     */
    public static PrepareContractionHierarchies select(
            List<PrepareContractionHierarchies> preparations, HintsMap weightingMap, Boolean edgeBased, Integer uTurnCosts)
            throws NoSuchCHPreparationException {
        return new PCHSelector(preparations).select(weightingMap, edgeBased, uTurnCosts);
    }

    private PrepareContractionHierarchies select(HintsMap weightingMap, Boolean edgeBased, Integer uTurnCosts) throws NoSuchCHPreparationException {
        this.weightingMap = weightingMap;
        findPCHsMatchingWeighting();

        if (!foundPCHsMatchingWeighting()) {
            throw new IllegalArgumentException("Cannot find CH RoutingAlgorithmFactory for weighting map " + weightingMap + " in entries: " + entriesStrs + ".");
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

    private PrepareContractionHierarchies selectUsingEdgeBasedAndUTurnCosts(boolean edgeBased, int uTurnCosts) throws NoSuchCHPreparationException {
        if (edgeBased) {
            PrepareContractionHierarchies edgeBasedPCH = edgeBasedPCHsByUTurnCosts.get(uTurnCosts);
            if (edgeBasedPCH != null) {
                return edgeBasedPCH;
            } else if (!edgeBasedPCHsByUTurnCosts.isEmpty()) {
                return throwFoundEdgeBasedButNotForRequestedUTurnCosts(uTurnCosts);
            } else {
                return throwRequestedEdgeBasedButOnlyFoundNodeBased();
            }
        } else {
            if (nodeBasedPCH != null) {
                return nodeBasedPCH;
            } else {
                return throwRequestedNodeBasedButOnlyFoundEdgeBased();
            }
        }
    }

    private PrepareContractionHierarchies selectUsingEdgeBased(boolean edgeBased) throws NoSuchCHPreparationException {
        if (edgeBased) {
            // u-turn costs were not specified, so either there is only one edge-based preparation and we take it
            // or we throw an error
            if (edgeBasedPCHsByUTurnCosts.size() == 1) {
                return edgeBasedPCHsByUTurnCosts.iterator().next().value;
            } else if (edgeBasedPCHsByUTurnCosts.isEmpty()) {
                return throwRequestedEdgeBasedButOnlyFoundNodeBased();
            } else {
                return throwFoundEdgeBasedButUnclearWhichOneToTake();
            }
        } else {
            if (nodeBasedPCH != null) {
                return nodeBasedPCH;
            } else {
                return throwRequestedNodeBasedButOnlyFoundEdgeBased();
            }
        }
    }

    private PrepareContractionHierarchies selectUsingUTurnCosts(int uTurnCosts) throws NoSuchCHPreparationException {
        // no edge_based parameter was set, we determine the CH preparation based on what is there (and prefer edge-based
        // if we can choose)
        PrepareContractionHierarchies edgeBasedPCH = edgeBasedPCHsByUTurnCosts.get(uTurnCosts);
        if (edgeBasedPCH != null) {
            return edgeBasedPCH;
        } else if (!edgeBasedPCHsByUTurnCosts.isEmpty()) {
            return throwFoundEdgeBasedButNotForRequestedUTurnCosts(uTurnCosts);
        } else {
            return nodeBasedPCH;
        }
    }

    private PrepareContractionHierarchies selectUsingWeightingOnly() throws NoSuchCHPreparationException {
        if (edgeBasedPCHsByUTurnCosts.size() == 1) {
            return edgeBasedPCHsByUTurnCosts.iterator().next().value;
        } else if (!edgeBasedPCHsByUTurnCosts.isEmpty()) {
            return throwFoundEdgeBasedButUnclearWhichOneToTake();
        } else {
            return nodeBasedPCH;
        }
    }

    private void findPCHsMatchingWeighting() {
        entriesStrs = new ArrayList<>();
        edgeBasedPCHsByUTurnCosts = new IntObjectHashMap<>(3);
        nodeBasedPCH = null;
        for (PrepareContractionHierarchies p : preparations) {
            boolean weightingMatches = p.getCHProfile().getWeighting().matches(weightingMap);
            if (weightingMatches) {
                if (p.isEdgeBased()) {
                    edgeBasedPCHsByUTurnCosts.put(p.getCHProfile().getUTurnCostsInt(), p);
                } else {
                    nodeBasedPCH = p;
                }
            }
            entriesStrs.add(p.getCHProfile().toString());
        }
    }

    private boolean foundPCHsMatchingWeighting() {
        return !edgeBasedPCHsByUTurnCosts.isEmpty() || nodeBasedPCH != null;
    }

    private PrepareContractionHierarchies throwFoundEdgeBasedButUnclearWhichOneToTake() throws NoSuchCHPreparationException {
        throw new NoSuchCHPreparationException("Found matching edge-based CH preparations for multiple values of u-turn costs: " + edgeBasedPCHsByUTurnCosts.keys() +
                ". You need to specify which one to use using the `" + Parameters.Routing.UTURN_COSTS + "' parameter");
    }

    private PrepareContractionHierarchies throwRequestedNodeBasedButOnlyFoundEdgeBased() throws NoSuchCHPreparationException {
        throw new NoSuchCHPreparationException("Found " + edgeBasedPCHsByUTurnCosts.size() + " edge-based CH preparation(s) for weighting map " + weightingMap
                + ", but requested node-based CH. You either need to configure node-based CH preparation or set the '" + Parameters.Routing.EDGE_BASED + "' " +
                "request parameter to 'true' (was 'false'). all entries: " + entriesStrs);
    }

    private PrepareContractionHierarchies throwRequestedEdgeBasedButOnlyFoundNodeBased() throws NoSuchCHPreparationException {
        throw new NoSuchCHPreparationException("Found a node-based CH preparation for weighting map " + weightingMap + ", but requested edge-based CH. " +
                "You either need to configure edge-based CH preparation or set the '" + Parameters.Routing.EDGE_BASED + "' " +
                "request parameter to 'false' (was 'true'). all entries: " + entriesStrs);
    }

    private PrepareContractionHierarchies throwFoundEdgeBasedButNotForRequestedUTurnCosts(int uTurnCosts) throws NoSuchCHPreparationException {
        throw new NoSuchCHPreparationException("Found edge-based CH preparations for weighting map " + weightingMap + " but none for requested u-turn costs: " +
                uTurnCosts + ", available: " + edgeBasedPCHsByUTurnCosts.keys() + ". You need to configure edge-based CH preparation for this value of u-turn costs or" +
                " choose another value using the '" + Parameters.Routing.UTURN_COSTS + "' request parameter.");
    }
}
