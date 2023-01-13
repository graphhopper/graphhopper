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

import com.graphhopper.storage.RoutingCHEdgeIteratorState;
import com.graphhopper.core.util.EdgeIteratorState;
import com.graphhopper.core.util.GHUtility;

/**
 * @author Peter Karich
 */
public enum TraversalMode {
    NODE_BASED(false),
    EDGE_BASED(true);

    private final boolean edgeBased;

    TraversalMode(boolean edgeBased) {
        this.edgeBased = edgeBased;
    }

    /**
     * Returns the identifier to access the map of the shortest path tree according to the traversal
     * mode. E.g. returning the adjacent node id in node-based behavior whilst returning the edge id
     * in edge-based behavior
     * <p>
     *
     * @param edgeState the current {@link EdgeIteratorState}
     * @param reverse   <code>true</code>, if traversal in backward direction. Will be true only for
     *                  backward searches in bidirectional algorithms.
     * @return the identifier to access the shortest path tree
     */
    public final int createTraversalId(EdgeIteratorState edgeState, boolean reverse) {
        if (edgeBased)
            return reverse ? edgeState.getReverseEdgeKey() : edgeState.getEdgeKey();
        return edgeState.getAdjNode();
    }

    public final int createTraversalId(RoutingCHEdgeIteratorState chEdgeState, boolean reverse) {
        if (edgeBased) {
            int key = reverse ? chEdgeState.getOrigEdgeKeyFirst() : chEdgeState.getOrigEdgeKeyLast();
            // For reverse traversal we need to revert the edge key, but not for loops and not for shortcuts.
            // Why not for shortcuts? Because of our definition of the first/last edge keys: they do not depend on the
            // 'state' of the edge state, but are defined in terms of the direction of the (always directed) shortcut.
            if (reverse && !chEdgeState.isShortcut() && chEdgeState.getBaseNode() != chEdgeState.getAdjNode())
                key = GHUtility.reverseEdgeKey(key);
            return key;
        }
        return chEdgeState.getAdjNode();
    }

    public boolean isEdgeBased() {
        return edgeBased;
    }

}
