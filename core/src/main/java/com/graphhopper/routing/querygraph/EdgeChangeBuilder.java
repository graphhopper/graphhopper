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

package com.graphhopper.routing.querygraph;


import com.carrotsearch.hppc.IntObjectMap;
import com.graphhopper.util.GHUtility;


import java.util.List;



/**
 * Helper class for {@link QueryOverlayBuilder}
 *
 * @see #build(List, int, IntObjectMap)
 */
class EdgeChangeBuilder {

    private final List<VirtualEdgeIteratorState> virtualEdges;
    private final IntObjectMap<QueryOverlay.EdgeChanges> edgeChangesAtRealNodes;
    private final int firstVirtualNodeId;

    /**
     * Builds a mapping between real node ids and the changes to their adjacent edges.
     *
     * @param edgeChangesAtRealNodes an empty, modifiable map to receive the changes
     */
    static void build(
            List<VirtualEdgeIteratorState> virtualEdges,
            int firstVirtualNodeId,
            IntObjectMap<QueryOverlay.EdgeChanges> edgeChangesAtRealNodes
    ) {
        new EdgeChangeBuilder(
                virtualEdges,
                firstVirtualNodeId,
                edgeChangesAtRealNodes
        ).build();
    }

    private EdgeChangeBuilder(
            List<VirtualEdgeIteratorState> virtualEdges,
            int firstVirtualNodeId,
            IntObjectMap<QueryOverlay.EdgeChanges> edgeChangesAtRealNodes
    ) {
        this.virtualEdges = virtualEdges;
        this.firstVirtualNodeId = firstVirtualNodeId;

        if (!edgeChangesAtRealNodes.isEmpty()) {
            throw new IllegalArgumentException(
                    "real node modifications need to be empty"
            );
        }

        this.edgeChangesAtRealNodes = edgeChangesAtRealNodes;
    }

    private void build() {
        for (VirtualEdgeIteratorState edge : virtualEdges) {
            int node = edge.getBaseNode();

            if (isVirtualNode(node)) {
                continue;
            }

            QueryOverlay.EdgeChanges edgeChanges =
                    edgeChangesAtRealNodes.get(node);

            if (edgeChanges == null) {
                edgeChanges = new QueryOverlay.EdgeChanges(2, 2);
                edgeChangesAtRealNodes.put(node, edgeChanges);
            }

            edgeChanges.getAdditionalEdges().add(edge);

            int originalEdge = GHUtility.getEdgeFromEdgeKey(
                    edge.getOriginalEdgeKey()
            );

            // Prevent routing along the unsplit original edge and bypassing virtual nodes.
            edgeChanges.getRemovedEdges().add(originalEdge);
        }
    }

    private boolean isVirtualNode(int nodeId) {
        return nodeId >= firstVirtualNodeId;
    }
}
