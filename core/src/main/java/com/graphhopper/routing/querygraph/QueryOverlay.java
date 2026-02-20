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

import com.carrotsearch.hppc.*;
import com.carrotsearch.hppc.cursors.DoubleCursor;
import com.carrotsearch.hppc.cursors.IntObjectCursor;
import com.graphhopper.coll.GHIntObjectHashMap;
import com.graphhopper.routing.weighting.Weighting;
import com.graphhopper.storage.BaseGraph;
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

    public static IntDoubleMap calcAdjustedVirtualWeights(QueryOverlay queryOverlay, BaseGraph baseGraph, Weighting weighting) {
        return calcAdjustedVirtualWeights(queryOverlay.getVirtualEdges(), baseGraph, weighting);
    }

    static IntDoubleMap calcAdjustedVirtualWeights(List<VirtualEdgeIteratorState> virtualEdges, BaseGraph baseGraph, Weighting weighting) {
        IntDoubleScatterMap result = new IntDoubleScatterMap(virtualEdges.size());

        IntObjectMap<List<VirtualEdgeIteratorState>> virtualEdgesByOriginalKey = new IntObjectScatterMap<>();
        for (VirtualEdgeIteratorState v : virtualEdges) {
            List<VirtualEdgeIteratorState> edges = virtualEdgesByOriginalKey.get(v.getOriginalEdgeKey());
            if (edges == null) {
                edges = new ArrayList<>();
                virtualEdgesByOriginalKey.put(v.getOriginalEdgeKey(), edges);
            }
            // remove duplicates
            if (edges.isEmpty() || v != edges.get(edges.size() - 1))
                edges.add(v);
        }

        for (IntObjectCursor<List<VirtualEdgeIteratorState>> c : virtualEdgesByOriginalKey) {
            DoubleArrayList virtualWeights = new DoubleArrayList(c.value.size());
            boolean hasInfiniteVirtualEdge = false;
            for (VirtualEdgeIteratorState v : c.value) {
                double w = weighting.calcEdgeWeight(v, false);
                if (Double.isInfinite(w))
                    hasInfiniteVirtualEdge = true;
                else if (w < 0 || w % 1 != 0)
                    throw new IllegalArgumentException("weight must be non-negative whole number, got: " + w);
                virtualWeights.add(w);
            }
            EdgeIteratorState originalEdge = baseGraph.getEdgeIteratorStateForKey(c.key);
            double originalWeight = weighting.calcEdgeWeight(originalEdge, false);
            if (Double.isInfinite(originalWeight) || hasInfiniteVirtualEdge) {
                // we don't adjust anything
                for (int i = 0; i < c.value.size(); i++)
                    result.put(c.value.get(i).getEdgeKey(), virtualWeights.get(i));
                continue;
            } else if (originalWeight < 0 || originalWeight % 1 != 0)
                throw new IllegalArgumentException("weight must be non-negative whole number, got: " + originalWeight);

            // casting to long is safe since we checked weights are whole numbers
            LongArrayList virtualWeightsLong = new LongArrayList(virtualWeights.size());
            for (DoubleCursor vw : virtualWeights) virtualWeightsLong.add((long) vw.value);
            adjustValues(virtualWeightsLong, (long) originalWeight, 1);
            for (int i = 0; i < c.value.size(); i++)
                result.put(c.value.get(i).getEdgeKey(), virtualWeightsLong.get(i));
        }
        return result;
    }

    static void adjustValues(LongArrayList values, long target, long passes) {
        if (values.isEmpty()) return;
        if (target < 0) throw new IllegalArgumentException("target cannot be negative: " + target);
        long diff = target;
        for (int i = 0; i < values.size(); i++) diff -= values.get(i);
        if (diff == 0) return;
        // If the difference is too large we do nothing, since we only want to account for small rounding errors.
        // The more passes we allow the stronger corrections will be accepted.
        if (Math.abs(diff) > passes * values.size()) return;
        int sign = diff > 0 ? 1 : -1;
        // Doing several passes is the simplest way of doing this:
        // Adjust each value by units of one until we have reached the target.
        for (int pass = 0; pass < passes; pass++) {
            for (int i = 0; i < values.size(); i++) {
                // never go below zero
                if (sign < 0 && values.get(i) <= 0) continue;
                values.set(i, values.get(i) + sign);
                diff -= sign;
                if (diff == 0) return;
            }
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
