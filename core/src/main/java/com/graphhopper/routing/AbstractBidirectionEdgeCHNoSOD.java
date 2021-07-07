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

import com.graphhopper.routing.ch.CHEntry;
import com.graphhopper.routing.ch.EdgeBasedCHBidirPathExtractor;
import com.graphhopper.routing.util.TraversalMode;
import com.graphhopper.storage.RoutingCHEdgeIteratorState;
import com.graphhopper.storage.RoutingCHGraph;
import com.graphhopper.util.EdgeExplorer;
import com.graphhopper.util.EdgeIterator;
import com.graphhopper.util.GHUtility;

import java.util.function.IntToDoubleFunction;

/**
 * @author easbar
 */
public abstract class AbstractBidirectionEdgeCHNoSOD extends AbstractBidirCHAlgo {
    private final EdgeExplorer innerExplorer;
    private boolean levelFilterEnabled = true;

    public AbstractBidirectionEdgeCHNoSOD(RoutingCHGraph graph) {
        super(graph, TraversalMode.EDGE_BASED);
        if (!graph.isEdgeBased()) {
            throw new IllegalArgumentException("Edge-based CH algorithms only work with edge-based CH graphs");
        }
        // the inner explorer will run on the base-(or base-query-)graph edges only.
        // we need an extra edge explorer, because it is called inside a loop that already iterates over edges
        // note that we do not need to filter edges with the inner explorer, because inaccessible edges won't be added
        // to bestWeightMapOther in the first place
        innerExplorer = graph.getBaseGraph().createEdgeExplorer();
        setPathExtractorSupplier(() -> new EdgeBasedCHBidirPathExtractor(graph));
    }

    @Override
    protected void postInitFrom() {
        // We use the levelEdgeFilter to filter out edges leading or coming from lower rank nodes.
        // For the first step though we need all edges, so we need to disable this filter.
        levelFilterEnabled = false;
        super.postInitFrom();
        levelFilterEnabled = true;
    }

    @Override
    protected void postInitTo() {
        levelFilterEnabled = false;
        super.postInitTo();
        levelFilterEnabled = true;
    }

    @Override
    protected void updateBestPath(double edgeWeight, SPTEntry entry, int origEdgeId, int traversalId, boolean reverse) {
        assert Double.isInfinite(edgeWeight) : "edge-based CH does not use pre-calculated edge weight";
        // special case where the fwd/bwd search runs directly into the opposite node, for example if the highest level
        // node of the shortest path matches the source or target. in this case one of the searches does not contribute
        // anything to the shortest path.
        int oppositeNode = reverse ? from : to;
        IntToDoubleFunction calcOppositeEdgePenalty = reverse ? calcFromEdgePenalty : calcToEdgePenalty;
        if (entry.adjNode == oppositeNode) {
            double penalty = calcOppositeEdgePenalty == null ? 0 : calcOppositeEdgePenalty.applyAsDouble(origEdgeId);
            if (Double.isFinite(penalty)) {
                if (entry.getWeightOfVisitedPath() + penalty < bestWeight) {
                    bestFwdEntry = reverse ? new CHEntry(oppositeNode, 0) : entry;
                    bestBwdEntry = reverse ? entry : new CHEntry(oppositeNode, 0);
                    bestWeight = entry.getWeightOfVisitedPath() + penalty;
                    return;
                }
            }
        }

        // todo: for a-star it should be possible to skip bridge node check at the beginning of the search as long as
        // the minimum source-target distance lies above total sum of fwd+bwd path candidates.
        EdgeIterator iter = innerExplorer.setBaseNode(entry.adjNode);
        while (iter.next()) {
            final int edgeId = iter.getEdge();
            int key = GHUtility.createEdgeKey(iter.getAdjNode(), iter.getBaseNode(), edgeId, !reverse);
            SPTEntry entryOther = bestWeightMapOther.get(key);
            if (entryOther == null) {
                continue;
            }

            double turnCostsAtBridgeNode = reverse ?
                    graph.getTurnWeight(edgeId, iter.getBaseNode(), origEdgeId) :
                    graph.getTurnWeight(origEdgeId, iter.getBaseNode(), edgeId);

            double newWeight = entry.getWeightOfVisitedPath() + entryOther.getWeightOfVisitedPath() + turnCostsAtBridgeNode;
            if (newWeight < bestWeight) {
                bestFwdEntry = reverse ? entryOther : entry;
                bestBwdEntry = reverse ? entry : entryOther;
                bestWeight = newWeight;
            }
        }
    }

    @Override
    protected int getOrigEdgeId(RoutingCHEdgeIteratorState edge, boolean reverse) {
        return reverse ? edge.getOrigEdgeFirst() : edge.getOrigEdgeLast();
    }

    @Override
    protected int getIncomingEdge(SPTEntry entry) {
        return ((CHEntry) entry).incEdge;
    }

    @Override
    protected int getTraversalId(RoutingCHEdgeIteratorState edge, int origEdgeId, boolean reverse) {
        int baseNode = getOtherNode(origEdgeId, edge.getAdjNode());
        return GHUtility.createEdgeKey(baseNode, edge.getAdjNode(), origEdgeId, reverse);
    }

    @Override
    protected double getEdgePenalty(RoutingCHEdgeIteratorState edge, SPTEntry currEdge, boolean reverse) {
        if (levelFilterEnabled && !levelEdgeFilter.accept(edge))
            return Double.POSITIVE_INFINITY;
        if (reverse)
            return toEdgePenaltyEnabled ? calcToEdgePenalty.applyAsDouble(edge.getOrigEdgeLast()) : 0;
        else
            return fromEdgePenaltyEnabled ? calcFromEdgePenalty.applyAsDouble(edge.getOrigEdgeFirst()) : 0;
    }
}
