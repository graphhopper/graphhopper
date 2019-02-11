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
package com.graphhopper.routing.util;

import com.graphhopper.util.EdgeIteratorState;
import com.graphhopper.util.GHUtility;

import java.util.Arrays;

import static com.graphhopper.util.Helper.toUpperCase;

/**
 * Defines how the graph can be traversed while Dijkstra or similar RoutingAlgorithm is in progress.
 * Different options define how precise turn restrictions and costs are taken into account, but
 * still all are without via-way support. BTW: this would not be done at runtime, this would be a
 * pre-processing step to avoid performance penalties.
 * <p>
 *
 * @author Peter Karich
 */
public enum TraversalMode {
    /**
     * The simplest traversal mode but without turn restrictions or cost support.
     */
    NODE_BASED(false, 1, false),
    /**
     * Strictly not recommended as it could lead to 'route not found' for bidirectional algorithms.
     * An edged-based traversal mode with basic turn restriction and cost support, including the
     * most scenarios. But without certain turn restrictions and without u-turns. As fast as node
     * based.
     */
    EDGE_BASED_1DIR(true, 1, false),
    /**
     * The bidirectional edged-based traversal mode with turn restriction and cost support. Without
     * u-turn support. 2 times slower than node based.
     */
    EDGE_BASED_2DIR(true, 2, false),
    /**
     * Not recommended as it leads to strange routes that outsmart the turn costs. The most feature
     * rich edged-based traversal mode with turn restriction and cost support, including u-turns. 4
     * times slower than node based.
     */
    EDGE_BASED_2DIR_UTURN(true, 2, true);

    private final boolean edgeBased;
    private final int noOfStates;
    private final boolean uTurnSupport;

    TraversalMode(boolean edgeBased, int noOfStates, boolean uTurnSupport) {
        this.edgeBased = edgeBased;
        this.noOfStates = noOfStates;
        this.uTurnSupport = uTurnSupport;

        if (noOfStates != 1 && noOfStates != 2)
            throw new IllegalArgumentException("Currently only 1 or 2 states allowed");
    }

    public static TraversalMode fromString(String name) {
        try {
            return valueOf(toUpperCase(name));
        } catch (Exception ex) {
            throw new IllegalArgumentException("TraversalMode " + name + " not supported. "
                    + "Supported are: " + Arrays.asList(TraversalMode.values()));
        }
    }

    /**
     * Returns the identifier to access the map of the shortest path tree according to the traversal
     * mode. E.g. returning the adjacent node id in node-based behavior whilst returning the edge id
     * in edge-based behavior
     * <p>
     *
     * @param iterState the current {@link EdgeIteratorState}
     * @param reverse   <code>true</code>, if traversal in backward direction. Will be true only for
     *                  backward searches in bidirectional algorithms.
     * @return the identifier to access the shortest path tree
     */
    public final int createTraversalId(EdgeIteratorState iterState, boolean reverse) {
        if (edgeBased) {
            if (noOfStates == 1)
                return iterState.getEdge();

            return GHUtility.createEdgeKey(iterState.getBaseNode(), iterState.getAdjNode(), iterState.getEdge(), reverse);
        }

        return iterState.getAdjNode();
    }

    /**
     * If you have an EdgeIteratorState the other createTraversalId is preferred!
     */
    public final int createTraversalId(int baseNode, int adjNode, int edgeId, boolean reverse) {
        if (edgeBased) {
            if (noOfStates == 1)
                return edgeId;

            return GHUtility.createEdgeKey(baseNode, adjNode, edgeId, reverse);
        }

        return adjNode;
    }

    public int reverseEdgeKey(int edgeKey) {
        if (edgeBased && noOfStates > 1)
            return GHUtility.reverseEdgeKey(edgeKey);
        return edgeKey;
    }

    public int getNoOfStates() {
        return noOfStates;
    }

    public boolean isEdgeBased() {
        return edgeBased;
    }

    public final boolean hasUTurnSupport() {
        return uTurnSupport;
    }
}
