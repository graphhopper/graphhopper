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
import com.graphhopper.storage.index.QueryResult;
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
    private final IntObjectMap<List<EdgeIteratorState>> additionalEdges;
    // todonow: document
    // todonow: maybe use a single map ? are the two nodes (mostly/always) acting on the same nodes ? <- removed edges
    // are on real nodes only!
    private final IntObjectMap<IntArrayList> removedEdges;
    /**
     * // todonow: move this comment ?
     * Store lat,lon of virtual tower nodes.
     */
    private final PointList virtualNodes;
    private final List<QueryResult> queryResults;

    public VirtualGraphModification(int numQueryResults, boolean is3D) {
        this.virtualNodes = new PointList(numQueryResults, is3D);
        this.virtualEdges = new ArrayList<>(numQueryResults * 2);
        this.queryResults = new ArrayList<>(numQueryResults);
        this.additionalEdges = new GHIntObjectHashMap<>(numQueryResults * 3);
        this.removedEdges = new GHIntObjectHashMap<>(numQueryResults * 3);
    }

    public List<VirtualEdgeIteratorState> getVirtualEdges() {
        return virtualEdges;
    }

    public IntObjectMap<List<EdgeIteratorState>> getAdditionalEdges() {
        return additionalEdges;
    }

    public IntObjectMap<IntArrayList> getRemovedEdges() {
        return removedEdges;
    }

    public PointList getVirtualNodes() {
        return virtualNodes;
    }

    public List<QueryResult> getQueryResults() {
        return queryResults;
    }
}
