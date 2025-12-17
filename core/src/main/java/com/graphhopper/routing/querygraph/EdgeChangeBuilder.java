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

import com.carrotsearch.hppc.IntArrayList;
import com.carrotsearch.hppc.IntObjectMap;
import com.carrotsearch.hppc.procedures.IntProcedure;
import com.graphhopper.coll.GHIntHashSet;
import com.graphhopper.util.EdgeIteratorState;

import java.util.List;

import static com.graphhopper.routing.querygraph.QueryGraph.*;

/**
 * Helper class for {@link QueryOverlayBuilder}
 *
 * @see #build(IntArrayList, List, int, IntObjectMap)
 */
class EdgeChangeBuilder {
    private final IntArrayList closestEdges;
    private final List<VirtualEdgeIteratorState> virtualEdges;
    private final IntObjectMap<QueryOverlay.EdgeChanges> edgeChangesAtRealNodes;
    private final int firstVirtualNodeId;

    /**
     * Builds a mapping between real node ids and the set of changes for their adjacent edges.
     *
     * @param edgeChangesAtRealNodes output parameter, you need to pass an empty & modifiable map and the results will
     *                               be added to it
     */
    static void build(IntArrayList closestEdges, List<VirtualEdgeIteratorState> virtualEdges, int firstVirtualNodeId, IntObjectMap<QueryOverlay.EdgeChanges> edgeChangesAtRealNodes) {
        new EdgeChangeBuilder(closestEdges, virtualEdges, firstVirtualNodeId, edgeChangesAtRealNodes).build();
    }

    private EdgeChangeBuilder(IntArrayList closestEdges, List<VirtualEdgeIteratorState> virtualEdges, int firstVirtualNodeId, IntObjectMap<QueryOverlay.EdgeChanges> edgeChangesAtRealNodes) {
        this.closestEdges = closestEdges;
        this.virtualEdges = virtualEdges;
        this.firstVirtualNodeId = firstVirtualNodeId;
        if (!edgeChangesAtRealNodes.isEmpty()) {
            throw new IllegalArgumentException("real node modifications need to be empty");
        }
        this.edgeChangesAtRealNodes = edgeChangesAtRealNodes;
    }

    private void build() {
        final GHIntHashSet towerNodesToChange = new GHIntHashSet(getNumVirtualNodes());

        // 1. for every real node adjacent to a virtual one we collect the virtual edges, also build a set of
        //    these adjacent real nodes so we can use them in the next step
        for (int i = 0; i < getNumVirtualNodes(); i++) {
            // base node
            VirtualEdgeIteratorState baseRevEdge = getVirtualEdge(i * 4 + SNAP_BASE);
            int towerNode = baseRevEdge.getAdjNode();
            if (!isVirtualNode(towerNode)) {
                towerNodesToChange.add(towerNode);
                addVirtualEdges(true, towerNode, i);
            }

            // adj node
            VirtualEdgeIteratorState adjEdge = getVirtualEdge(i * 4 + SNAP_ADJ);
            towerNode = adjEdge.getAdjNode();
            if (!isVirtualNode(towerNode)) {
                towerNodesToChange.add(towerNode);
                addVirtualEdges(false, towerNode, i);
            }
        }

        // 2. build the list of removed edges for all real nodes adjacent to virtual ones
        towerNodesToChange.forEach(new IntProcedure() {
            @Override
            public void apply(int value) {
                addRemovedEdges(value);
            }
        });
    }

    /**
     * Adds the virtual edges adjacent to the real tower nodes
     */
    private void addVirtualEdges(boolean base, int node, int virtNode) {
        QueryOverlay.EdgeChanges edgeChanges = edgeChangesAtRealNodes.get(node);
        if (edgeChanges == null) {
            edgeChanges = new QueryOverlay.EdgeChanges(2, 2);
            edgeChangesAtRealNodes.put(node, edgeChanges);
        }
        VirtualEdgeIteratorState edge = base
                ? getVirtualEdge(virtNode * 4 + BASE_SNAP)
                : getVirtualEdge(virtNode * 4 + ADJ_SNAP);
        edgeChanges.getAdditionalEdges().add(edge);
    }

    /**
     * Adds the ids of the removed edges at the real tower nodes. We need to do this such that we cannot 'skip'
     * virtual nodes by just using the original edges and also to prevent u-turns at the real nodes adjacent to the
     * virtual ones.
     */
    private void addRemovedEdges(int towerNode) {
        if (isVirtualNode(towerNode))
            throw new IllegalStateException("Node should not be virtual:" + towerNode + ", " + edgeChangesAtRealNodes);

        QueryOverlay.EdgeChanges edgeChanges = edgeChangesAtRealNodes.get(towerNode);
        List<VirtualEdgeIteratorState> existingEdges = edgeChanges.getAdditionalEdges();
        IntArrayList removedEdges = edgeChanges.getRemovedEdges();
        for (VirtualEdgeIteratorState existingEdge : existingEdges) {
            removedEdges.add(getClosestEdge(existingEdge.getAdjNode()));
        }
    }

    private boolean isVirtualNode(int nodeId) {
        return nodeId >= firstVirtualNodeId;
    }

    private int getNumVirtualNodes() {
        return closestEdges.size();
    }

    private int getClosestEdge(int node) {
        return closestEdges.get(node - firstVirtualNodeId);
    }

    private VirtualEdgeIteratorState getVirtualEdge(int virtualEdgeId) {
        return virtualEdges.get(virtualEdgeId);
    }

}
