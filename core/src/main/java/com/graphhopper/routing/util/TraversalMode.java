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
    NODE_BASED(false),
    /**
     * The bidirectional edged-based traversal mode with turn restriction and cost support. Without
     * u-turn support. 2 times slower than node based.
     */
    EDGE_BASED_2DIR(true);

    private final boolean edgeBased;

    TraversalMode(boolean edgeBased) {
        this.edgeBased = edgeBased;
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
        return createTraversalId(iterState.getBaseNode(), iterState.getAdjNode(), iterState.getEdge(), reverse);
    }

    /**
     * If you have an EdgeIteratorState the other createTraversalId is preferred!
     */
    public final int createTraversalId(int baseNode, int adjNode, int edgeId, boolean reverse) {
        if (edgeBased) {
            return GHUtility.createEdgeKey(baseNode, adjNode, edgeId, reverse);
        }
        return adjNode;
    }

    public int reverseEdgeKey(int edgeKey) {
        if (edgeBased)
            return GHUtility.reverseEdgeKey(edgeKey);
        return edgeKey;
    }

    public boolean isEdgeBased() {
        return edgeBased;
    }

}
