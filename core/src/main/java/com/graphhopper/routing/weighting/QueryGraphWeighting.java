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

package com.graphhopper.routing.weighting;

import com.carrotsearch.hppc.IntArrayList;
import com.graphhopper.routing.ev.EdgeIntAccess;
import com.graphhopper.routing.querygraph.QueryGraph;
import com.graphhopper.storage.BaseGraph;
import com.graphhopper.util.EdgeIterator;
import com.graphhopper.util.EdgeIteratorState;

/**
 * Whenever a {@link QueryGraph} is used for shortest path calculations including turn costs we need to wrap the
 * {@link Weighting} we want to use with this class. Otherwise turn costs at virtual nodes and/or including virtual
 * edges will not be calculated correctly.
 */
public class QueryGraphWeighting implements Weighting {
    private final BaseGraph graph;
    private final Weighting weighting;
    private final int firstVirtualNodeId;
    private final int firstVirtualEdgeId;
    private final IntArrayList closestEdges;

    public QueryGraphWeighting(BaseGraph graph, Weighting weighting, IntArrayList closestEdges) {
        this.graph = graph;
        this.weighting = weighting;
        this.firstVirtualNodeId = graph.getNodes();
        this.firstVirtualEdgeId = graph.getEdges();
        this.closestEdges = closestEdges;
    }

    @Override
    public double calcMinWeightPerDistance() {
        return weighting.calcMinWeightPerDistance();
    }

    @Override
    public double calcEdgeWeight(EdgeIteratorState edgeState, boolean reverse) {
        return weighting.calcEdgeWeight(edgeState, reverse);
    }

    @Override
    public double calcTurnWeight(int inEdge, int viaNode, int outEdge) {
        if (!EdgeIterator.Edge.isValid(inEdge) || !EdgeIterator.Edge.isValid(outEdge)) {
            return 0;
        }
        if (isVirtualNode(viaNode)) {
            if (isUTurn(inEdge, outEdge)) {
                // do not allow u-turns at virtual nodes, otherwise the route depends on whether or not there are
                // virtual via nodes, see #1672. note since we are turning between virtual edges here we need to compare
                // the *virtual* edge ids (the orig edge would always be the same for all virtual edges at a virtual
                // node), see #1593
                return Double.POSITIVE_INFINITY;
            } else {
                return 0;
            }
        }
        // to calculate the actual turn costs or detect u-turns we need to look at the original edge of each virtual
        // edge, see #1593
        if (isVirtualEdge(inEdge) && isVirtualEdge(outEdge)) {
            EdgeIteratorState inEdgeState = graph.getEdgeIteratorState(getOriginalEdge(inEdge), Integer.MIN_VALUE);
            EdgeIteratorState outEdgeState = graph.getEdgeIteratorState(getOriginalEdge(outEdge), Integer.MIN_VALUE);
            var minTurnWeight = new Object() { double value = Double.POSITIVE_INFINITY; };
            graph.forEdgeAndCopiesOfEdge(graph.createEdgeExplorer(), inEdgeState, p -> {
                graph.forEdgeAndCopiesOfEdge(graph.createEdgeExplorer(), outEdgeState, q -> {
                    minTurnWeight.value = Math.min(minTurnWeight.value, weighting.calcTurnWeight(p.getEdge(), viaNode, q.getEdge()));
                });
            });
            return minTurnWeight.value;
        } else if (isVirtualEdge(inEdge)) {
            EdgeIteratorState inEdgeState = graph.getEdgeIteratorState(getOriginalEdge(inEdge), Integer.MIN_VALUE);
            var minTurnWeight = new Object() { double value = Double.POSITIVE_INFINITY; };
            graph.forEdgeAndCopiesOfEdge(graph.createEdgeExplorer(), inEdgeState, p -> {
                minTurnWeight.value = Math.min(minTurnWeight.value, weighting.calcTurnWeight(p.getEdge(), viaNode, outEdge));
            });
            return minTurnWeight.value;
        } else if (isVirtualEdge(outEdge)) {
            EdgeIteratorState outEdgeState = graph.getEdgeIteratorState(getOriginalEdge(outEdge), Integer.MIN_VALUE);
            var minTurnWeight = new Object() { double value = Double.POSITIVE_INFINITY; };
            graph.forEdgeAndCopiesOfEdge(graph.createEdgeExplorer(), outEdgeState, p -> {
                minTurnWeight.value = Math.min(minTurnWeight.value, weighting.calcTurnWeight(inEdge, viaNode, p.getEdge()));
            });
            return minTurnWeight.value;
        } else {
            return weighting.calcTurnWeight(inEdge, viaNode, outEdge);
        }
    }

    public double calcWeight(double distance, int edgeKey, int nodeVia, int prevOrNextEdgeId, boolean reverse, EdgeIntAccess edgeIntAccess) {
        return weighting.calcWeight(distance, edgeKey, nodeVia, prevOrNextEdgeId, reverse, edgeIntAccess);
    }

    private boolean isUTurn(int inEdge, int outEdge) {
        return inEdge == outEdge;
    }

    @Override
    public long calcEdgeMillis(EdgeIteratorState edgeState, boolean reverse) {
        return weighting.calcEdgeMillis(edgeState, reverse);
    }

    @Override
    public long calcTurnMillis(int inEdge, int viaNode, int outEdge) {
        // todo: here we do not allow calculating turn weights that aren't turn times, also see #1590
        return (long) (1000 * calcTurnWeight(inEdge, viaNode, outEdge));
    }

    @Override
    public boolean hasTurnCosts() {
        return weighting.hasTurnCosts();
    }

    @Override
    public String getName() {
        return weighting.getName();
    }

    @Override
    public String toString() {
        return getName();
    }

    private int getOriginalEdge(int edge) {
        return closestEdges.get((edge - firstVirtualEdgeId) / 2);
    }

    private boolean isVirtualNode(int node) {
        return node >= firstVirtualNodeId;
    }

    private boolean isVirtualEdge(int edge) {
        return edge >= firstVirtualEdgeId;
    }
}
