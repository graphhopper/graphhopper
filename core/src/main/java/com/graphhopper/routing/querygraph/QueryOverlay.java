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
import com.carrotsearch.hppc.LongArrayList;
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

    /**
     * Adjusts values so they sum to target, changing each by at most maxPerElement.
     * If the target is unreachable within these constraints, values are left untouched.
     */
    static void adjustValues(LongArrayList values, long target, long maxPerElement) {
        if (values.isEmpty()) return;
        if (target < 0) throw new IllegalArgumentException("target cannot be negative: " + target);
        if (maxPerElement < 0)
            throw new IllegalArgumentException("maxPerElement cannot be negative: " + maxPerElement);
        if (target == 0) return;
        long minTarget = 0, maxTarget = 0, diff = target;
        for (int i = 0; i < values.size(); i++) {
            diff -= values.get(i);
            minTarget += Math.max(0, values.get(i) - maxPerElement);
            maxTarget += values.get(i) + maxPerElement;
        }
        if (diff == 0) return;
        // Check if the target is reachable given maxPerElement, no element must be negative.
        // If not, we leave the array untouched since we only want to account for small numerical errors.
        if (target < minTarget || target > maxTarget) return;
        int sign = diff > 0 ? 1 : -1;
        for (int i = 0; i < values.size(); i++) {
            long adjustment = sign * Math.min(Math.abs(diff), maxPerElement);
            if (values.get(i) + adjustment < 0) adjustment = -values.get(i);
            values.set(i, values.get(i) + adjustment);
            diff -= adjustment;
        }
    }

    static class EdgeChanges {
        private final List<VirtualEdgeIteratorState> additionalEdges;
        private final IntArrayList removedEdges;

        EdgeChanges(int expectedNumAdditionalEdges, int expectedNumRemovedEdges) {
            additionalEdges = new ArrayList<>(expectedNumAdditionalEdges);
            removedEdges = new IntArrayList(expectedNumRemovedEdges);
        }

        List<VirtualEdgeIteratorState> getAdditionalEdges() {
            return additionalEdges;
        }

        IntArrayList getRemovedEdges() {
            return removedEdges;
        }
    }
}
