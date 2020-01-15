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
import com.graphhopper.storage.*;
import com.graphhopper.util.GHUtility;

import static com.graphhopper.util.EdgeIterator.ANY_EDGE;

/**
 * @author easbar
 */
public abstract class AbstractBidirectionEdgeCHNoSOD extends AbstractBidirCHAlgo {
    private final RoutingCHEdgeExplorer innerInExplorer;
    private final RoutingCHEdgeExplorer innerOutExplorer;

    public AbstractBidirectionEdgeCHNoSOD(RoutingCHGraph graph) {
        super(graph, TraversalMode.EDGE_BASED);
        // the inner explorers will run on the base-(or base-query-)graph edges only
        // we need extra edge explorers, because they get called inside a loop that already iterates over edges
        innerInExplorer = graph.createOriginalInEdgeExplorer();
        innerOutExplorer = graph.createOriginalOutEdgeExplorer();
    }

    @Override
    protected void postInitFrom() {
        // With CH the additionalEdgeFilter is the one that filters out edges leading or coming from higher rank nodes,
        // i.e. LevelEdgeFilter, For the first step though we need all edges, so we need to ignore this filter.
        // Using an arbitrary filter is not supported for CH anyway.
        if (fromOutEdge == ANY_EDGE) {
            fillEdgesFromUsingFilter(CHEdgeFilter.ALL_EDGES);
        } else {
            fillEdgesFromUsingFilter(new CHEdgeFilter() {
                @Override
                public boolean accept(RoutingCHEdgeIteratorState edgeState) {
                    return edgeState.getOrigEdgeFirst() == fromOutEdge;
                }
            });
        }
    }

    @Override
    protected void postInitTo() {
        if (toInEdge == ANY_EDGE) {
            fillEdgesToUsingFilter(CHEdgeFilter.ALL_EDGES);
        } else {
            fillEdgesToUsingFilter(new CHEdgeFilter() {
                @Override
                public boolean accept(RoutingCHEdgeIteratorState edgeState) {
                    return edgeState.getOrigEdgeLast() == toInEdge;
                }
            });
        }
    }

    @Override
    protected void updateBestPath(double edgeWeight, SPTEntry entry, int origEdgeId, int traversalId, boolean reverse) {
        assert Double.isInfinite(edgeWeight) : "edge-based CH does not use pre-calculated edge weight";
        // special case where the fwd/bwd search runs directly into the opposite node, for example if the highest level
        // node of the shortest path matches the source or target. in this case one of the searches does not contribute
        // anything to the shortest path.
        int oppositeNode = reverse ? from : to;
        int oppositeEdge = reverse ? fromOutEdge : toInEdge;
        boolean oppositeEdgeRestricted = reverse ? (fromOutEdge != ANY_EDGE) : (toInEdge != ANY_EDGE);
        if (entry.adjNode == oppositeNode && (!oppositeEdgeRestricted || origEdgeId == oppositeEdge)) {
            if (entry.getWeightOfVisitedPath() < bestWeight) {
                bestFwdEntry = reverse ? new CHEntry(oppositeNode, 0) : entry;
                bestBwdEntry = reverse ? entry : new CHEntry(oppositeNode, 0);
                bestWeight = entry.getWeightOfVisitedPath();
                return;
            }
        }

        RoutingCHEdgeIterator iter = reverse ?
                innerInExplorer.setBaseNode(entry.adjNode) :
                innerOutExplorer.setBaseNode(entry.adjNode);

        // todo: for a-star it should be possible to skip bridge node check at the beginning of the search as long as
        // minimum source-target distance lies above total sum of fwd+bwd path candidates.
        while (iter.next()) {
            final int edgeId = getOrigEdgeId(iter, !reverse);
            int key = GHUtility.createEdgeKey(getOtherNode(edgeId, iter.getBaseNode()), iter.getBaseNode(), edgeId, !reverse);
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
    protected BidirPathExtractor createPathExtractor(RoutingCHGraph graph) {
        return new EdgeBasedCHBidirPathExtractor(graph);
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
    protected boolean accept(RoutingCHEdgeIteratorState edge, SPTEntry currEdge, boolean reverse) {
        final int incEdge = getIncomingEdge(currEdge);
        final int prevOrNextEdgeId = getOrigEdgeId(edge, !reverse);
        double turnWeight = reverse
                ? graph.getTurnWeight(prevOrNextEdgeId, edge.getBaseNode(), incEdge)
                : graph.getTurnWeight(incEdge, edge.getBaseNode(), prevOrNextEdgeId);
        if (Double.isInfinite(turnWeight)) {
            return false;
        }
        return levelEdgeFilter == null || levelEdgeFilter.accept(edge);
    }

}
