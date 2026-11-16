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


import com.carrotsearch.hppc.IntDoubleMap;
import com.carrotsearch.hppc.IntIntMap;
import com.carrotsearch.hppc.IntIntScatterMap;
import com.carrotsearch.hppc.IntLongMap;
import com.graphhopper.routing.querygraph.QueryGraph;
import com.graphhopper.routing.querygraph.VirtualEdgeIterator;
import com.graphhopper.routing.querygraph.VirtualEdgeIteratorState;
import com.graphhopper.storage.BaseGraph;

import com.graphhopper.util.EdgeIterator;
import com.graphhopper.util.EdgeIteratorState;
import com.graphhopper.util.GHUtility;

import java.util.List;
/**
 * Whenever a {@link QueryGraph} is used for shortest path calculations including turn costs we need to wrap the
 * {@link Weighting} we want to use with this class. Otherwise turn costs at virtual nodes and/or including virtual
 * edges will not be calculated correctly.
 */
public class QueryGraphWeighting implements Weighting {

    private final Weighting weighting;
    private final int firstVirtualNodeId;
    private final int firstVirtualEdgeId;
    private final IntIntMap originalEdgesByVirtualEdge;
    private final IntDoubleMap virtualWeightsByEdgeKey;
    private final IntLongMap virtualTimesByEdgeKey;

    public QueryGraphWeighting(
            BaseGraph graph,
            Weighting weighting,
            List<VirtualEdgeIteratorState> virtualEdges,
            IntDoubleMap virtualWeightsByEdgeKey,
            IntLongMap virtualTimesByEdgeKey
    ) {
        this.weighting = weighting;
        this.firstVirtualNodeId = graph.getNodes();
        this.firstVirtualEdgeId = graph.getEdges();

        this.originalEdgesByVirtualEdge =
                new IntIntScatterMap(virtualEdges.size());

        for (VirtualEdgeIteratorState edge : virtualEdges) {
            originalEdgesByVirtualEdge.put(
                    edge.getEdge(),
                    GHUtility.getEdgeFromEdgeKey(edge.getOriginalEdgeKey())
            );
        }

        this.virtualWeightsByEdgeKey = virtualWeightsByEdgeKey;
        this.virtualTimesByEdgeKey = virtualTimesByEdgeKey;
    }

    @Override
    public double calcMinWeightPerDistance() {
        return weighting.calcMinWeightPerDistance();
    }

    @Override
    public double calcEdgeWeight(EdgeIteratorState edgeState, boolean reverse) {
        if (isVirtualEdge(edgeState.getEdge()) && !edgeState.get(EdgeIteratorState.UNFAVORED_EDGE)) {
            if (edgeState instanceof VirtualEdgeIteratorState v)
                return virtualWeightsByEdgeKey.get(reverse ? v.getReverseEdgeKey() : v.getEdgeKey());
            else if (edgeState instanceof VirtualEdgeIterator v)
                return virtualWeightsByEdgeKey.get(reverse ? v.getReverseEdgeKey() : v.getEdgeKey());
            else
                throw new IllegalStateException("Unexpected virtual edge state: " + edgeState);
        }
        return weighting.calcEdgeWeight(edgeState, reverse);
    }

    @Override
    public double calcTurnWeight(int inEdge, int viaNode, int outEdge) {
        if (!EdgeIterator.Edge.isValid(inEdge) ||
                !EdgeIterator.Edge.isValid(outEdge)) {
            return 0;
        }

        if (isVirtualNode(viaNode)) {
            if (isUTurn(inEdge, outEdge)) {
                return Double.POSITIVE_INFINITY;
            }

            if (isVirtualEdge(inEdge) && isVirtualEdge(outEdge)
                    && getOriginalEdge(inEdge) != getOriginalEdge(outEdge)) {
                return Double.POSITIVE_INFINITY;
            }

            return 0;
        }

        return resolveTurn(inEdge, viaNode, outEdge).turnWeight;
    }

    private Result resolveTurn(
            int inEdge,
            int viaNode,
            int outEdge
    ) {
        Result result = new Result();

        result.origInEdge = isVirtualEdge(inEdge)
                ? getOriginalEdge(inEdge)
                : inEdge;

        result.origOutEdge = isVirtualEdge(outEdge)
                ? getOriginalEdge(outEdge)
                : outEdge;

        result.turnWeight = weighting.calcTurnWeight(
                result.origInEdge,
                viaNode,
                result.origOutEdge
        );

        return result;
    }

    private boolean isUTurn(int inEdge, int outEdge) {
        return inEdge == outEdge;
    }

    @Override
    public long calcEdgeMillis(EdgeIteratorState edgeState, boolean reverse) {
        if (isVirtualEdge(edgeState.getEdge()) && !edgeState.get(EdgeIteratorState.UNFAVORED_EDGE)) {
            if (edgeState instanceof VirtualEdgeIteratorState v)
                return virtualTimesByEdgeKey.get(reverse ? v.getReverseEdgeKey() : v.getEdgeKey());
            else if (edgeState instanceof VirtualEdgeIterator v)
                return virtualTimesByEdgeKey.get(reverse ? v.getReverseEdgeKey() : v.getEdgeKey());
            else
                throw new IllegalStateException("Unexpected virtual edge state: " + edgeState);
        }
        return weighting.calcEdgeMillis(edgeState, reverse);
    }

    @Override
    // Do not allow switching between different original edges at a virtual node.
    public long calcTurnMillis(int inEdge, int viaNode, int outEdge) {
        if (isVirtualNode(viaNode))
            // see calcTurnWeight
            return 0;
        else {
            // Use the same original edges as calcTurnWeight to calculate the turn time.
            Result result = resolveTurn(inEdge, viaNode, outEdge);
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
        if (!originalEdgesByVirtualEdge.containsKey(edge)) {
            throw new IllegalArgumentException(
                    "Unknown virtual edge: " + edge
            );
        }

        return originalEdgesByVirtualEdge.get(edge);
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
        double turnWeight = Double.POSITIVE_INFINITY;
    }
}
