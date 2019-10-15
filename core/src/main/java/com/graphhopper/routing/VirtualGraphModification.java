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
import com.graphhopper.coll.GHIntObjectHashMap;
import com.graphhopper.util.EdgeIteratorState;
import com.graphhopper.util.PointList;

import java.util.ArrayList;
import java.util.List;

// todonow: not happy with this name. this class has the data we need to add the right virtual nodes/and edges
// but compared to QueryGraph it simply provides this data instead of using it to implement the Graph interface
public class VirtualGraphModification {
    // For every virtual node there are 4 edges: base-snap, snap-base, snap-adj, adj-snap.
    // todonow: clarify comment: different virtual edges appear consecutively
    private final List<VirtualEdgeIteratorState> virtualEdges;
    // todonow: document
    private final IntObjectMap<RealNodeModification> realNodeModifications;
    /**
     * // todonow: move this comment ?
     * Store lat,lon of virtual tower nodes.
     */
    private final PointList virtualNodes;
    private final IntArrayList closestEdges;

    public VirtualGraphModification(int numVirtualNodes, boolean is3D) {
        this.virtualNodes = new PointList(numVirtualNodes, is3D);
        this.virtualEdges = new ArrayList<>(numVirtualNodes * 2);
        this.closestEdges = new IntArrayList(numVirtualNodes);
        realNodeModifications = new GHIntObjectHashMap<>(numVirtualNodes * 3);
    }

    public List<VirtualEdgeIteratorState> getVirtualEdges() {
        return virtualEdges;
    }

    public IntObjectMap<RealNodeModification> getRealNodeModifications() {
        return realNodeModifications;
    }

    public PointList getVirtualNodes() {
        return virtualNodes;
    }

    public IntArrayList getClosestEdges() {
        return closestEdges;
    }

    public static class RealNodeModification {
        private final List<EdgeIteratorState> additionalEdges;
        private final IntArrayList removedEdges;

        RealNodeModification(int expectedNumAdditionalEdges, int expectedNumRemovedEdges) {
            additionalEdges = new ArrayList<>(expectedNumAdditionalEdges);
            removedEdges = new IntArrayList(expectedNumRemovedEdges);
        }

        public List<EdgeIteratorState> getAdditionalEdges() {
            return additionalEdges;
        }

        public IntArrayList getRemovedEdges() {
            return removedEdges;
        }
    }
}
