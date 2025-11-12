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
import com.carrotsearch.hppc.IntObjectHashMap;
import com.carrotsearch.hppc.IntObjectMap;
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

    void adjustVirtualWeights(BaseGraph baseGraph, Weighting weighting) {
        IntObjectMap<List<VirtualEdgeIteratorState>> virtualEdgesByOriginalKey = new IntObjectHashMap<>();
        for (VirtualEdgeIteratorState v : virtualEdges) {
            List<VirtualEdgeIteratorState> edges = virtualEdgesByOriginalKey.get(v.getOriginalEdgeKey());
            if (edges == null) {
                edges = new ArrayList<>();
                virtualEdgesByOriginalKey.put(v.getOriginalEdgeKey(), edges);
            }
            if (edges.isEmpty() || v != edges.get(edges.size() - 1))
                edges.add(v);
        }
        for (IntObjectCursor<List<VirtualEdgeIteratorState>> c : virtualEdgesByOriginalKey) {
            EdgeIteratorState edgeState = baseGraph.getEdgeIteratorStateForKey(c.key);
            double fullWeightFwd = weighting.calcEdgeWeight(edgeState, false);
            double fullWeightBwd = weighting.calcEdgeWeight(edgeState, true);
            adjustWeights(c.value, fullWeightFwd, edgeState.getDistance(), false);
            adjustWeights(c.value, fullWeightBwd, edgeState.getDistance(), true);
        }

//        for (VirtualEdgeIteratorState v : virtualEdges) {
//            double wFwd = weighting.calcEdgeWeight(v, false);
//            double wwFwd = v.getWeight(false);
//            double wBwd = weighting.calcEdgeWeight(v, true);
//            double wwBwd = v.getWeight(true);
//            if (Math.abs(wFwd - wwFwd) > 1)
//                throw new IllegalArgumentException(wFwd + " vs " + wwFwd);
//            if (Math.abs(wBwd - wwBwd) > 1)
//                throw new IllegalArgumentException(wBwd + " vs " + wwBwd);
//        }
    }

    static void adjustWeights(List<VirtualEdgeIteratorState> virtualEdges, double fullWeight, double fullDistance, boolean reverse) {
        if (Double.isInfinite(fullWeight)) {
            for (VirtualEdgeIteratorState v : virtualEdges)
                v.setWeight(fullWeight, reverse);
            return;
        } else if (fullWeight % 1 != 0)
            throw new IllegalStateException("QueryGraph requires edge weights to be whole numbers (or infinite), but got: " + fullWeight);

        for (VirtualEdgeIteratorState v : virtualEdges) {
            double weight = fullWeight * v.getDistance() / fullDistance;
            weight = Math.round(weight);
            v.setWeight(weight, reverse);
        }
        double sum = 0;
        for (VirtualEdgeIteratorState v : virtualEdges)
            sum += v.getWeight(reverse);
        double difference = fullWeight - sum;
        // todonow: is it even necessary to round here?
        int units = (int) Math.round(difference);
        int baseIncrement = units / virtualEdges.size();
        int remainder = units % virtualEdges.size();
        int leftOver = 0;
        for (int i = 0; i < virtualEdges.size(); i++) {
            VirtualEdgeIteratorState v = virtualEdges.get(i);
            int adjustment = baseIncrement + (i < Math.abs(remainder) ? Integer.signum(remainder) : 0);
            double newWeight = v.getWeight(reverse) + adjustment;
            if (newWeight >= 0)
                v.setWeight(newWeight, reverse);
            else
                leftOver += adjustment;
        }
        for (VirtualEdgeIteratorState v : virtualEdges) {
            double newWeight = v.getWeight(reverse) + leftOver;
            if (newWeight >= 0) {
                v.setWeight(newWeight, reverse);
                leftOver = 0;
                break;
            }
        }
        if (leftOver > 0)
            throw new IllegalStateException("Could not distribute weight difference");

        // verify
        sum = 0;
        for (VirtualEdgeIteratorState v : virtualEdges)
            sum += v.getWeight(reverse);
        if (fullWeight != sum)
            throw new IllegalStateException();
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
