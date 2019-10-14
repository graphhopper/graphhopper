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
import com.graphhopper.coll.GHIntObjectHashMap;
import com.graphhopper.routing.util.EdgeFilter;
import com.graphhopper.storage.index.QueryResult;
import com.graphhopper.util.EdgeExplorer;
import com.graphhopper.util.EdgeIterator;
import com.graphhopper.util.EdgeIteratorState;

import java.util.List;

import static com.graphhopper.routing.QueryGraph.*;

/**
 * Helper class for {@link QueryGraph} used to build a mapping between node ids and adjacent virtual edges as needed
 * for {@link QueryGraph}'s {@link EdgeExplorer}s.
 */
class VirtualEdgeMapBuilder {
    private final List<VirtualEdgeIteratorState> virtualEdges;
    private final List<QueryResult> queryResults;
    private final EdgeExplorer mainExplorer;
    private final EdgeFilter edgeFilter;
    private final int firstVirtualNodeId;
    private final IntObjectMap<VirtualEdgeIterator> node2EdgeMap;

    /**
     * Builds a map mapping node ids to the adjacent virtual edges
     */
    static IntObjectMap<VirtualEdgeIterator> buildMap(List<VirtualEdgeIteratorState> virtualEdges, List<QueryResult> queryResults, EdgeExplorer mainExplorer, EdgeFilter edgeFilter, int firstVirtualNodeId) {
        return new VirtualEdgeMapBuilder(virtualEdges, queryResults, mainExplorer, edgeFilter, firstVirtualNodeId).create();
    }

    private VirtualEdgeMapBuilder(List<VirtualEdgeIteratorState> virtualEdges, List<QueryResult> queryResults,
                                  EdgeExplorer mainExplorer, EdgeFilter edgeFilter, int firstVirtualNodeId) {
        this.virtualEdges = virtualEdges;
        this.queryResults = queryResults;
        this.mainExplorer = mainExplorer;
        this.edgeFilter = edgeFilter;
        this.firstVirtualNodeId = firstVirtualNodeId;
        node2EdgeMap = new GHIntObjectHashMap<>(queryResults.size() * 3);
    }

    private IntObjectMap<VirtualEdgeIterator> create() {
        final GHIntHashSet towerNodesToChange = new GHIntHashSet(queryResults.size());

        // 1. virtualEdges should also get fresh EdgeIterators on every createEdgeExplorer call!
        for (int i = 0; i < queryResults.size(); i++) {
            // create outgoing edges
            VirtualEdgeIterator virtEdgeIter = new VirtualEdgeIterator(2);
            EdgeIteratorState baseRevEdge = virtualEdges.get(i * 4 + VE_BASE_REV);
            if (edgeFilter.accept(baseRevEdge))
                virtEdgeIter.add(baseRevEdge);
            EdgeIteratorState adjEdge = virtualEdges.get(i * 4 + VE_ADJ);
            if (edgeFilter.accept(adjEdge))
                virtEdgeIter.add(adjEdge);

            int virtNode = firstVirtualNodeId + i;
            node2EdgeMap.put(virtNode, virtEdgeIter);

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
        return node2EdgeMap;
    }

    /**
     * Creates a fake edge iterator pointing to multiple edge states.
     */
    private void addVirtualEdges(boolean base, int node, int virtNode) {
        VirtualEdgeIterator existingIter = node2EdgeMap.get(node);
        if (existingIter == null) {
            existingIter = new VirtualEdgeIterator(10);
            node2EdgeMap.put(node, existingIter);
        }
        EdgeIteratorState edge = base
                ? virtualEdges.get(virtNode * 4 + VE_BASE)
                : virtualEdges.get(virtNode * 4 + VE_ADJ_REV);
        if (edgeFilter.accept(edge))
            existingIter.add(edge);
    }

    private void fillVirtualEdges(int towerNode) {
        if (isVirtualNode(towerNode))
            throw new IllegalStateException("Node should not be virtual:" + towerNode + ", " + node2EdgeMap);

        VirtualEdgeIterator vIter = node2EdgeMap.get(towerNode);
        IntArrayList ignoreEdges = new IntArrayList(vIter.count() * 2);
        while (vIter.next()) {
            EdgeIteratorState edge = queryResults.get(vIter.getAdjNode() - firstVirtualNodeId).getClosestEdge();
            ignoreEdges.add(edge.getEdge());
        }
        vIter.reset();
        EdgeIterator iter = mainExplorer.setBaseNode(towerNode);
        while (iter.next()) {
            if (!ignoreEdges.contains(iter.getEdge()))
                vIter.add(iter.detach(false));
        }
    }

    private boolean isVirtualNode(int nodeId) {
        return nodeId >= firstVirtualNodeId;
    }
}
