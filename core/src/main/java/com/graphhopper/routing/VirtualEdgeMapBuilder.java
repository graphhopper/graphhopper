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
import com.graphhopper.storage.index.QueryResult;
import com.graphhopper.util.EdgeExplorer;
import com.graphhopper.util.EdgeIteratorState;

import java.util.ArrayList;
import java.util.List;

import static com.graphhopper.routing.QueryGraph.*;

/**
 * Helper class for {@link QueryGraph} used to build a mapping between node ids and adjacent virtual edges as needed
 * for {@link QueryGraph}'s {@link EdgeExplorer}s.
 * todonow: naming & cleanup and do we need this class or can it just be a part of Virtual Edge Builder
 */
class VirtualEdgeMapBuilder {
    private final VirtualGraphModification graphModification;
    private final int firstVirtualNodeId;

    static void build(VirtualGraphModification graphModification, int firstVirtualNodeId) {
        new VirtualEdgeMapBuilder(graphModification, firstVirtualNodeId).build();
    }

    private VirtualEdgeMapBuilder(VirtualGraphModification graphModification, int firstVirtualNodeId) {
        this.graphModification = graphModification;
        this.firstVirtualNodeId = firstVirtualNodeId;
    }

    private void build() {
        final GHIntHashSet towerNodesToChange = new GHIntHashSet(getNumQueryResults());

        // todonow: update comments like this
        // 1. virtualEdges should also get fresh EdgeIterators on every createEdgeExplorer call!
        for (int i = 0; i < getNumQueryResults(); i++) {
            // create outgoing edges
            List<EdgeIteratorState> vEdges = new ArrayList<>(2);
            EdgeIteratorState baseRevEdge = getVirtualEdge(i * 4 + VE_BASE_REV);
            vEdges.add(baseRevEdge);
            EdgeIteratorState adjEdge = getVirtualEdge(i * 4 + VE_ADJ);
            vEdges.add(adjEdge);

            int virtNode = firstVirtualNodeId + i;
            additionalEdges().put(virtNode, vEdges);

            // replace edge list of neighboring tower nodes:
            // add virtual edges only and collect tower nodes where real edges will be added in step 2.
            //
            // base node
            int towerNode = baseRevEdge.getAdjNode();
            if (!isVirtualNode(towerNode)) {
                towerNodesToChange.add(towerNode);
                addVirtualEdges(true, towerNode, i);
            }

            // adj node
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
        List<EdgeIteratorState> existingEdges = additionalEdges().get(node);
        if (existingEdges == null) {
            existingEdges = new ArrayList<>(10);
            additionalEdges().put(node, existingEdges);
        }
        EdgeIteratorState edge = base
                ? getVirtualEdge(virtNode * 4 + VE_BASE)
                : getVirtualEdge(virtNode * 4 + VE_ADJ_REV);
        existingEdges.add(edge);
    }

    private void fillVirtualEdges(int towerNode) {
        if (isVirtualNode(towerNode))
            throw new IllegalStateException("Node should not be virtual:" + towerNode + ", " + additionalEdges());

        List<EdgeIteratorState> existingEdges = additionalEdges().get(towerNode);
        IntArrayList ignoreEdges = new IntArrayList(existingEdges.size() * 2);
        for (EdgeIteratorState existingEdge : existingEdges) {
            EdgeIteratorState edge = getQueryResult(existingEdge.getAdjNode() - firstVirtualNodeId).getClosestEdge();
            ignoreEdges.add(edge.getEdge());
        }
        removedEdges().put(towerNode, ignoreEdges);
    }

    private boolean isVirtualNode(int nodeId) {
        return nodeId >= firstVirtualNodeId;
    }

    private int getNumQueryResults() {
        return graphModification.getQueryResults().size();
    }

    private QueryResult getQueryResult(int nodeId) {
        return graphModification.getQueryResults().get(nodeId);
    }

    private VirtualEdgeIteratorState getVirtualEdge(int virtualEdgeId) {
        return graphModification.getVirtualEdges().get(virtualEdgeId);
    }

    private IntObjectMap<List<EdgeIteratorState>> additionalEdges() {
        return graphModification.getAdditionalEdges();
    }

    private IntObjectMap<IntArrayList> removedEdges() {
        return graphModification.getRemovedEdges();
    }
}
