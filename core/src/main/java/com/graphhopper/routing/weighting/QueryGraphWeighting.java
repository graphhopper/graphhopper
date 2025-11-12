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
import com.graphhopper.routing.querygraph.QueryGraph;
import com.graphhopper.routing.querygraph.VirtualEdgeIterator;
import com.graphhopper.routing.querygraph.VirtualEdgeIteratorState;
import com.graphhopper.storage.BaseGraph;
import com.graphhopper.util.EdgeExplorer;
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
    public double calcMinWeightPerKm() {
        return weighting.calcMinWeightPerKm();
    }

    @Override
    public double calcEdgeWeight(EdgeIteratorState edgeState, boolean reverse) {
        if (isVirtualEdge(edgeState.getEdge()) && !edgeState.get(EdgeIteratorState.UNFAVORED_EDGE)) {
            if (edgeState instanceof VirtualEdgeIteratorState v)
                return v.getWeight(reverse);
            else if (edgeState instanceof VirtualEdgeIterator v)
                return v.getWeight(reverse);
            else
                throw new IllegalStateException("Unexpected virtual edge state: " + edgeState);
        }
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
        return getMinWeightAndOriginalEdges(inEdge, viaNode, outEdge).minTurnWeight;
    }

    private Result getMinWeightAndOriginalEdges(int inEdge, int viaNode, int outEdge) {
        // to calculate the actual turn costs or detect u-turns we need to look at the original edge of each virtual
        // edge, see #1593
        Result result = new Result();
        if (isVirtualEdge(inEdge) && isVirtualEdge(outEdge)) {
            EdgeExplorer innerExplorer = graph.createEdgeExplorer();
            graph.forEdgeAndCopiesOfEdge(graph.createEdgeExplorer(), viaNode, getOriginalEdge(inEdge), p -> {
                graph.forEdgeAndCopiesOfEdge(innerExplorer, viaNode, getOriginalEdge(outEdge), q -> {
                    double w = weighting.calcTurnWeight(p, viaNode, q);
                    if (w < result.minTurnWeight) {
                        result.origInEdge = p;
                        result.origOutEdge = q;
                        result.minTurnWeight = w;
                    }
                });
            });
        } else if (isVirtualEdge(inEdge)) {
            graph.forEdgeAndCopiesOfEdge(graph.createEdgeExplorer(), viaNode, getOriginalEdge(inEdge), e -> {
                double w = weighting.calcTurnWeight(e, viaNode, outEdge);
                if (w < result.minTurnWeight) {
                    result.origInEdge = e;
                    result.origOutEdge = outEdge;
                    result.minTurnWeight = w;
                }
            });
        } else if (isVirtualEdge(outEdge)) {
            graph.forEdgeAndCopiesOfEdge(graph.createEdgeExplorer(), viaNode, getOriginalEdge(outEdge), e -> {
                double w = weighting.calcTurnWeight(inEdge, viaNode, e);
                if (w < result.minTurnWeight) {
                    result.origInEdge = inEdge;
                    result.origOutEdge = e;
                    result.minTurnWeight = w;
                }
            });
        } else {
            result.origInEdge = inEdge;
            result.origOutEdge = outEdge;
            result.minTurnWeight = weighting.calcTurnWeight(inEdge, viaNode, outEdge);
        }
        return result;
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
        if (isVirtualNode(viaNode))
            // see calcTurnWeight
            return 0;
        else {
            // we want the turn time given by the actual weighting for the edges with minimum weight
            // (the same ones that would be selected when routing)
            Result result = getMinWeightAndOriginalEdges(inEdge, viaNode, outEdge);
            return weighting.calcTurnMillis(result.origInEdge, viaNode, result.origOutEdge);
        }
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

    private static class Result {
        int origInEdge = -1;
        int origOutEdge = -1;
        double minTurnWeight = Double.POSITIVE_INFINITY;
    }
}
