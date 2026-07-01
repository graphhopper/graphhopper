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

    record WeightsAndTimes(IntDoubleMap weights, IntLongMap times) {
    }

    public static WeightsAndTimes calcAdjustedVirtualWeightsAndTimes(QueryOverlay queryOverlay, BaseGraph baseGraph, Weighting weighting) {
        return calcAdjustedVirtualWeightsAndTimes(queryOverlay.getVirtualEdges(), baseGraph, weighting);
    }

    static WeightsAndTimes calcAdjustedVirtualWeightsAndTimes(List<VirtualEdgeIteratorState> virtualEdges, BaseGraph baseGraph, Weighting weighting) {
        IntDoubleScatterMap weights = new IntDoubleScatterMap(virtualEdges.size());
        IntLongScatterMap times = new IntLongScatterMap(virtualEdges.size());

        IntObjectMap<List<VirtualEdgeIteratorState>> virtualEdgesByOriginalKey = new IntObjectScatterMap<>();
        IntSet edgesSet = new IntScatterSet();
        for (VirtualEdgeIteratorState v : virtualEdges) {
            List<VirtualEdgeIteratorState> edges = virtualEdgesByOriginalKey.get(v.getOriginalEdgeKey());
            if (edges == null) {
                edges = new ArrayList<>();
                virtualEdgesByOriginalKey.put(v.getOriginalEdgeKey(), edges);
            }
            // remove duplicates
            if (edges.isEmpty() || edgesSet.add(v.getEdgeKey()))
                edges.add(v);
        }

        for (IntObjectCursor<List<VirtualEdgeIteratorState>> c : virtualEdgesByOriginalKey) {
            DoubleArrayList virtualWeights = new DoubleArrayList(c.value.size());
            LongArrayList virtualTimes = new LongArrayList(c.value.size());
            boolean hasInfiniteVirtualEdge = false;
            for (VirtualEdgeIteratorState v : c.value) {
                double w = weighting.calcEdgeWeight(v, false);
                if (Double.isInfinite(w))
                    hasInfiniteVirtualEdge = true;
                else if (w < 0 || w % 1 != 0)
                    throw new IllegalArgumentException("weight must be non-negative whole number, got: " + w);
                virtualWeights.add(w);

                long t = weighting.calcEdgeMillis(v, false);
                virtualTimes.add(t);
            }
            EdgeIteratorState originalEdge = baseGraph.getEdgeIteratorStateForKey(c.key);
            double originalWeight = weighting.calcEdgeWeight(originalEdge, false);
            long originalTime = weighting.calcEdgeMillis(originalEdge, false);

            if (Double.isInfinite(originalWeight) || hasInfiniteVirtualEdge) {
                // we don't adjust anything
                for (int i = 0; i < c.value.size(); i++) {
                    weights.put(c.value.get(i).getEdgeKey(), virtualWeights.get(i));
                    times.put(c.value.get(i).getEdgeKey(), virtualTimes.get(i));
                }
                continue;
            } else if (originalWeight < 0 || originalWeight % 1 != 0)
                throw new IllegalArgumentException("weight must be non-negative whole number, got: " + originalWeight);

            // casting to long is safe since we checked weights are whole numbers
            LongArrayList virtualWeightsLong = new LongArrayList(virtualWeights.size());
            for (DoubleCursor vw : virtualWeights) virtualWeightsLong.add((long) vw.value);

            // We do not adjust the weights if the difference is more than rounding errors.
            // For example, when we snap onto an edge only partially covered by an avoided area,
            // only one of the virtual edges might intersect the area. In this case we do not want to
            // penalize the virtual edges that are outside the area. This means that the sum of the
            // virtual edges' weights does not equal the weight of the original edge.
            adjustValues(virtualWeightsLong, (long) originalWeight, 1);
            adjustValues(virtualTimes, originalTime, 20);
            for (int i = 0; i < c.value.size(); i++) {
                weights.put(c.value.get(i).getEdgeKey(), virtualWeightsLong.get(i));
                times.put(c.value.get(i).getEdgeKey(), virtualTimes.get(i));
            }
        }
        return new WeightsAndTimes(weights, times);
    }

    /**
     * Adjusts values so they sum to target, changing each by at most maxPerElement.
     * The first element is kept >= 1 to avoid zero-weight virtual edges at tower nodes.
     * Zero-weight virtual edges at tower node introduce unique path ambiguity.
     * If the target is unreachable within these constraints, values are left untouched.
     */
    static void adjustValues(LongArrayList values, long target, long maxPerElement) {
        if (values.isEmpty()) return;
        if (target < 0) throw new IllegalArgumentException("target cannot be negative: " + target);
        if (maxPerElement < 0)
            throw new IllegalArgumentException("maxPerElement cannot be negative: " + maxPerElement);
        // If the target is zero we do nothing, because we would have to set all values zero, but we want to keep the zeroth >= 1 -> not our problem
        if (target == 0) return;
        long minTarget = 0, maxTarget = 0, diff = target;
        for (int i = 0; i < values.size(); i++) {
            diff -= values.get(i);
            long floor = (i == 0) ? 1 : 0;
            minTarget += Math.max(floor, values.get(i) - maxPerElement);
            maxTarget += values.get(i) + maxPerElement;
        }
        if (diff == 0) return;
        // Check if the target is reachable given maxPerElement, no element must be negative, and the first must be at least one.
        // If not, we leave the array untouched since we only want to account for small numerical errors.
        if (target < minTarget || target > maxTarget) return;
        int sign = diff > 0 ? 1 : -1;
        for (int i = 0; i < values.size(); i++) {
            long adjustment = sign * Math.min(Math.abs(diff), maxPerElement);
            // The first element must stay > 0: a zero-weight first virtual edge (leaving the
            // tower node) introduces unique path ambiguity.
            long floor = (i == 0) ? 1 : 0;
            if (values.get(i) + adjustment < floor) adjustment = floor - values.get(i);
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
