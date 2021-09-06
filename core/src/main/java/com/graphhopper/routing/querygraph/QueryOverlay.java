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
import com.graphhopper.coll.GHIntObjectHashMap;
import com.graphhopper.util.EdgeIteratorState;
import com.graphhopper.util.PointList;

import java.util.ArrayList;
import java.util.List;

/**
 * This class holds the data that is necessary to add additional nodes and edges to an existing graph, as it is needed
 * when we want to start/end a route at a location that is in between the actual nodes of the graph (virtual nodes+edges).
 */
class QueryOverlay {
    // stores the coordinates of the additional/virtual nodes
    private final PointList virtualNodes;
    // stores the closest edge id for each virtual node
    private final IntArrayList closestEdges;
    // stores the virtual edges, for every virtual node there are four such edges: base-snap, snap-base, snap-adj, adj-snap.
    private final List<VirtualEdgeIteratorState> virtualEdges;
    // stores the changes that need to be done to the real nodes
    private final IntObjectMap<EdgeChanges> edgeChangesAtRealNodes;

    QueryOverlay(int numVirtualNodes, boolean is3D) {
        this.virtualNodes = new PointList(numVirtualNodes, is3D);
        this.virtualEdges = new ArrayList<>(numVirtualNodes * 2);
        this.closestEdges = new IntArrayList(numVirtualNodes);
        edgeChangesAtRealNodes = new GHIntObjectHashMap<>(numVirtualNodes * 3);
    }

    int getNumVirtualEdges() {
        return virtualEdges.size();
    }

    void addVirtualEdge(VirtualEdgeIteratorState virtualEdge) {
        virtualEdges.add(virtualEdge);
    }

    VirtualEdgeIteratorState getVirtualEdge(int edgeId) {
        return virtualEdges.get(edgeId);
    }

    List<VirtualEdgeIteratorState> getVirtualEdges() {
        return virtualEdges;
    }

    IntObjectMap<EdgeChanges> getEdgeChangesAtRealNodes() {
        return edgeChangesAtRealNodes;
    }

    PointList getVirtualNodes() {
        return virtualNodes;
    }

    IntArrayList getClosestEdges() {
        return closestEdges;
    }

    static class EdgeChanges {
        private final List<EdgeIteratorState> additionalEdges;
        private final IntArrayList removedEdges;

        EdgeChanges(int expectedNumAdditionalEdges, int expectedNumRemovedEdges) {
            additionalEdges = new ArrayList<>(expectedNumAdditionalEdges);
            removedEdges = new IntArrayList(expectedNumRemovedEdges);
        }

        List<EdgeIteratorState> getAdditionalEdges() {
            return additionalEdges;
        }

        IntArrayList getRemovedEdges() {
            return removedEdges;
        }
    }
}
