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
import com.graphhopper.routing.util.DefaultEdgeFilter;
import com.graphhopper.routing.util.EdgeFilter;
import com.graphhopper.routing.util.TraversalMode;
import com.graphhopper.routing.weighting.TurnWeighting;
import com.graphhopper.routing.weighting.Weighting;
import com.graphhopper.storage.Graph;
import com.graphhopper.storage.SPTEntry;
import com.graphhopper.util.EdgeExplorer;
import com.graphhopper.util.EdgeIterator;
import com.graphhopper.util.EdgeIteratorState;
import com.graphhopper.util.GHUtility;

import static com.graphhopper.util.EdgeIterator.ANY_EDGE;

/**
 * @author easbar
 */
public abstract class AbstractBidirectionEdgeCHNoSOD extends AbstractBidirAlgo {
    private final EdgeExplorer innerInExplorer;
    private final EdgeExplorer innerOutExplorer;
    private final TurnWeighting turnWeighting;

    public AbstractBidirectionEdgeCHNoSOD(Graph graph, TurnWeighting weighting) {
        super(graph, weighting, TraversalMode.EDGE_BASED);
        this.turnWeighting = weighting;
        // the inner explorers will run on the base- (or query/base-)graph edges only
        Graph baseGraph = graph.getBaseGraph();
        // we need extra edge explorers, because they get called inside a loop that already iterates over edges
        // important: we have to use different filter ids for the edge explorers here than we use for the edge
        // explorers in the superclass, otherwise this will not work with QueryGraph's edge explorer cache, see #1623.
        innerInExplorer = baseGraph.createEdgeExplorer(DefaultEdgeFilter.inEdges(flagEncoder).setFilterId(1));
        innerOutExplorer = baseGraph.createEdgeExplorer(DefaultEdgeFilter.outEdges(flagEncoder).setFilterId(1));
    }

    @Override
    protected void postInitFrom() {
        // With CH the additionalEdgeFilter is the one that filters out edges leading or coming from higher rank nodes,
        // i.e. LevelEdgeFilter, For the first step though we need all edges, so we need to ignore this filter.
        // Using an arbitrary filter is not supported for CH anyway.
        if (fromOutEdge == ANY_EDGE) {
            fillEdgesFromUsingFilter(EdgeFilter.ALL_EDGES);
        } else {
            fillEdgesFromUsingFilter(new EdgeFilter() {
                @Override
                public boolean accept(EdgeIteratorState edgeState) {
                    return edgeState.getOrigEdgeFirst() == fromOutEdge;
                }
            });
        }
    }

    @Override
    protected void postInitTo() {
        if (toInEdge == ANY_EDGE) {
            fillEdgesToUsingFilter(EdgeFilter.ALL_EDGES);
        } else {
            fillEdgesToUsingFilter(new EdgeFilter() {
                @Override
                public boolean accept(EdgeIteratorState edgeState) {
                    return edgeState.getOrigEdgeLast() == toInEdge;
                }
            });
        }
    }

    @Override
    protected void initCollections(int size) {
        super.initCollections(Math.min(size, 2000));
    }

    @Override
    public boolean finished() {
        // we need to finish BOTH searches for CH!
        if (finishedFrom && finishedTo)
            return true;

        // changed also the final finish condition for CH
        return currFrom.weight >= bestWeight && currTo.weight >= bestWeight;
    }

    @Override
    protected void updateBestPath(EdgeIteratorState edgeState, SPTEntry entry, int traversalId, boolean reverse) {
        // special case where the fwd/bwd search runs directly into the opposite node, for example if the highest level
        // node of the shortest path matches the source or target. in this case one of the searches does not contribute
        // anything to the shortest path.
        int oppositeNode = reverse ? from : to;
        int oppositeEdge = reverse ? fromOutEdge : toInEdge;
        boolean oppositeEdgeRestricted = reverse ? (fromOutEdge != ANY_EDGE) : (toInEdge != ANY_EDGE);
        if (edgeState.getAdjNode() == oppositeNode && (!oppositeEdgeRestricted || getOrigEdgeId(edgeState, reverse) == oppositeEdge)) {
            if (entry.getWeightOfVisitedPath() < bestWeight) {
                bestFwdEntry = reverse ? new CHEntry(oppositeNode, 0) : entry;
                bestBwdEntry = reverse ? entry : new CHEntry(oppositeNode, 0);
                bestWeight = entry.getWeightOfVisitedPath();
                return;
            }
        }

        EdgeIterator iter = reverse ?
                innerInExplorer.setBaseNode(edgeState.getAdjNode()) :
                innerOutExplorer.setBaseNode(edgeState.getAdjNode());

        // todo: for a-star it should be possible to skip bridge node check at the beginning of the search as long as
        // minimum source-target distance lies above total sum of fwd+bwd path candidates.
        while (iter.next()) {
            final int edgeId = getOrigEdgeId(iter, !reverse);
            final int prevOrNextOrigEdgeId = getOrigEdgeId(edgeState, reverse);
            int key = GHUtility.createEdgeKey(graph.getOtherNode(edgeId, iter.getBaseNode()), iter.getBaseNode(), edgeId, !reverse);
            SPTEntry entryOther = bestWeightMapOther.get(key);
            if (entryOther == null) {
                continue;
            }

            double turnCostsAtBridgeNode = reverse ?
                    turnWeighting.calcTurnWeight(edgeId, iter.getBaseNode(), prevOrNextOrigEdgeId) :
                    turnWeighting.calcTurnWeight(prevOrNextOrigEdgeId, iter.getBaseNode(), edgeId);

            double newWeight = entry.getWeightOfVisitedPath() + entryOther.getWeightOfVisitedPath() + turnCostsAtBridgeNode;
            if (newWeight < bestWeight) {
                bestFwdEntry = reverse ? entryOther : entry;
                bestBwdEntry = reverse ? entry : entryOther;
                bestWeight = newWeight;
            }
        }
    }

    @Override
    protected BidirPathExtractor createPathExtractor(Graph graph, Weighting weighting) {
        return new EdgeBasedCHBidirPathExtractor(graph, graph.getBaseGraph(), weighting);
    }

    @Override
    protected int getOrigEdgeId(EdgeIteratorState edge, boolean reverse) {
        return reverse ? edge.getOrigEdgeFirst() : edge.getOrigEdgeLast();
    }

    @Override
    protected int getIncomingEdge(SPTEntry entry) {
        return ((CHEntry) entry).incEdge;
    }

    @Override
    protected int getTraversalId(EdgeIteratorState edge, int origEdgeId, boolean reverse) {
        int baseNode = graph.getOtherNode(origEdgeId, edge.getAdjNode());
        return GHUtility.createEdgeKey(baseNode, edge.getAdjNode(), origEdgeId, reverse);
    }

    @Override
    protected boolean accept(EdgeIteratorState edge, SPTEntry currEdge, boolean reverse) {
        final int incEdge = getIncomingEdge(currEdge);
        final int prevOrNextEdgeId = getOrigEdgeId(edge, !reverse);
        double turnWeight = reverse
                ? turnWeighting.calcTurnWeight(prevOrNextEdgeId, edge.getBaseNode(), incEdge)
                : turnWeighting.calcTurnWeight(incEdge, edge.getBaseNode(), prevOrNextEdgeId);
        if (Double.isInfinite(turnWeight)) {
            return false;
        }
        return additionalEdgeFilter == null || additionalEdgeFilter.accept(edge);
    }

    @Override
    public String toString() {
        return getName() + "|" + weighting;
    }

}
