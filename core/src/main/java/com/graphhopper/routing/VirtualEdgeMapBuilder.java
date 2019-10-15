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

package com.graphhopper.routing;

import com.carrotsearch.hppc.IntArrayList;
import com.carrotsearch.hppc.IntObjectMap;
import com.carrotsearch.hppc.procedures.IntProcedure;
import com.graphhopper.coll.GHIntHashSet;
import com.graphhopper.util.EdgeExplorer;
import com.graphhopper.util.EdgeIteratorState;

import java.util.List;

import static com.graphhopper.routing.QueryGraph.*;

/**
 * Helper class for {@link QueryGraph} used to build a mapping between node ids and adjacent virtual edges as needed
 * for {@link QueryGraph}'s {@link EdgeExplorer}s.
 * todonow: naming & cleanup and do we need this class or can it just be a part of Virtual Edge Builder
 */
class VirtualEdgeMapBuilder {
    private final IntArrayList closestEdges;
    private final List<VirtualEdgeIteratorState> virtualEdges;
    private final IntObjectMap<VirtualGraphModification.RealNodeModification> realNodeModifications;
    private final int firstVirtualNodeId;

    // todonow: documentation, realnodemodificatinos are modified ...
    static void build(IntArrayList closestEdges, List<VirtualEdgeIteratorState> virtualEdges, int firstVirtualNodeId, IntObjectMap<VirtualGraphModification.RealNodeModification> realNodeModifications) {
        new VirtualEdgeMapBuilder(closestEdges, virtualEdges, firstVirtualNodeId, realNodeModifications).build();
    }

    private VirtualEdgeMapBuilder(IntArrayList closestEdges, List<VirtualEdgeIteratorState> virtualEdges, int firstVirtualNodeId, IntObjectMap<VirtualGraphModification.RealNodeModification> realNodeModifications) {
        this.closestEdges = closestEdges;
        this.virtualEdges = virtualEdges;
        this.firstVirtualNodeId = firstVirtualNodeId;
        if (!realNodeModifications.isEmpty()) {
            throw new IllegalArgumentException("real node modifications need to be empty");
        }
        this.realNodeModifications = realNodeModifications;
    }

    private void build() {
        final GHIntHashSet towerNodesToChange = new GHIntHashSet(getNumVirtualNodes());

        // todonow: update comments like this
        // 1. virtualEdges should also get fresh EdgeIterators on every createEdgeExplorer call!
        for (int i = 0; i < getNumVirtualNodes(); i++) {
            // replace edge list of neighboring tower nodes:
            // add virtual edges only and collect tower nodes where real edges will be added in step 2.
            //
            // base node
            EdgeIteratorState baseRevEdge = getVirtualEdge(i * 4 + VE_BASE_REV);
            int towerNode = baseRevEdge.getAdjNode();
            if (!isVirtualNode(towerNode)) {
                towerNodesToChange.add(towerNode);
                addVirtualEdges(true, towerNode, i);
            }

            // adj node
            EdgeIteratorState adjEdge = getVirtualEdge(i * 4 + VE_ADJ);
            towerNode = adjEdge.getAdjNode();
            if (!isVirtualNode(towerNode)) {
                towerNodesToChange.add(towerNode);
                addVirtualEdges(false, towerNode, i);
            }
        }

        // 2. the connected tower nodes from mainGraph need fresh EdgeIterators with possible fakes
        // where 'fresh' means independent of previous call and respecting the edgeFilter
        // -> setup fake iterators of detected tower nodes (virtual edges are already added)
        towerNodesToChange.forEach(new IntProcedure() {
            @Override
            public void apply(int value) {
                fillVirtualEdges(value);
            }
        });
    }

    /**
     * // todonow: wording - 'fake' is rather misleading here
     * Creates a fake edge iterator pointing to multiple edge states.
     */
    private void addVirtualEdges(boolean base, int node, int virtNode) {
        VirtualGraphModification.RealNodeModification realNodeModification = realNodeModifications().get(node);
        if (realNodeModification == null) {
            realNodeModification = new VirtualGraphModification.RealNodeModification(2, 2);
            realNodeModifications().put(node, realNodeModification);
        }
        EdgeIteratorState edge = base
                ? getVirtualEdge(virtNode * 4 + VE_BASE)
                : getVirtualEdge(virtNode * 4 + VE_ADJ_REV);
        realNodeModification.getAdditionalEdges().add(edge);
    }

    private void fillVirtualEdges(int towerNode) {
        if (isVirtualNode(towerNode))
            throw new IllegalStateException("Node should not be virtual:" + towerNode + ", " + realNodeModifications());

        VirtualGraphModification.RealNodeModification realNodeModification = realNodeModifications().get(towerNode);
        List<EdgeIteratorState> existingEdges = realNodeModification.getAdditionalEdges();
        IntArrayList removedEdges = realNodeModification.getRemovedEdges();
        for (EdgeIteratorState existingEdge : existingEdges) {
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

    private IntObjectMap<VirtualGraphModification.RealNodeModification> realNodeModifications() {
        return realNodeModifications;
    }

}
