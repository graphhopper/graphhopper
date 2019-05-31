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
import com.graphhopper.routing.ch.EdgeBasedPathCH;
import com.graphhopper.routing.util.DefaultEdgeFilter;
import com.graphhopper.routing.util.EdgeFilter;
import com.graphhopper.routing.util.TraversalMode;
import com.graphhopper.routing.weighting.TurnWeighting;
import com.graphhopper.storage.Graph;
import com.graphhopper.storage.SPTEntry;
import com.graphhopper.storage.TurnCostExtension;
import com.graphhopper.util.EdgeExplorer;
import com.graphhopper.util.EdgeIterator;
import com.graphhopper.util.EdgeIteratorState;
import com.graphhopper.util.GHUtility;

/**
 * @author easbar
 */
public abstract class AbstractBidirectionEdgeCHNoSOD extends AbstractBidirAlgo {
    private final EdgeExplorer innerInExplorer;
    private final EdgeExplorer innerOutExplorer;
    private final TurnWeighting turnWeighting;
    private final TurnCostExtension turnCostExtension;

    public AbstractBidirectionEdgeCHNoSOD(Graph graph, TurnWeighting weighting) {
        super(graph, weighting, TraversalMode.EDGE_BASED_2DIR);
        this.turnWeighting = weighting;
        // we need extra edge explorers, because they get called inside a loop that already iterates over edges
        // important: we have to use different filter ids, otherwise this will not work with QueryGraph's edge explorer
        // cache, see #1623.
        innerInExplorer = graph.createEdgeExplorer(DefaultEdgeFilter.inEdges(flagEncoder).setFilterId(1));
        innerOutExplorer = graph.createEdgeExplorer(DefaultEdgeFilter.outEdges(flagEncoder).setFilterId(1));
        if (!(graph.getExtension() instanceof TurnCostExtension)) {
            throw new IllegalArgumentException("edge-based CH algorithms require a turn cost extension");
        }
        turnCostExtension = (TurnCostExtension) graph.getExtension();
    }

    @Override
    protected void postInitFrom() {
        EdgeFilter filter = additionalEdgeFilter;
        setEdgeFilter(EdgeFilter.ALL_EDGES);
        fillEdgesFrom();
        setEdgeFilter(filter);
    }

    @Override
    protected void postInitTo() {
        EdgeFilter filter = additionalEdgeFilter;
        setEdgeFilter(EdgeFilter.ALL_EDGES);
        fillEdgesTo();
        setEdgeFilter(filter);
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
        return currFrom.weight >= bestPath.getWeight() && currTo.weight >= bestPath.getWeight();
    }

    @Override
    protected void updateBestPath(EdgeIteratorState edgeState, SPTEntry entry, int traversalId, boolean reverse) {
        // special case where the fwd/bwd search runs directly into the opposite node, for example if the highest level
        // node of the shortest path matches the source or target. in this case one of the searches does not contribute
        // anything to the shortest path.
        int oppositeNode = reverse ? from : to;
        if (edgeState.getAdjNode() == oppositeNode) {
            if (entry.getWeightOfVisitedPath() < bestPath.getWeight()) {
                bestPath.setSwitchToFrom(reverse);
                bestPath.setSPTEntry(entry);
                bestPath.setSPTEntryTo(new CHEntry(oppositeNode, 0));
                bestPath.setWeight(entry.getWeightOfVisitedPath());
                return;
            }
        }

        // todo: it would be sufficient (and maybe more efficient) to use an original edge explorer here ?
        EdgeIterator iter = reverse ?
                innerInExplorer.setBaseNode(edgeState.getAdjNode()) :
                innerOutExplorer.setBaseNode(edgeState.getAdjNode());

        // todo: for a-star it should be possible to skip bridge node check at the beginning of the search as long as
        // minimum source-target distance lies above total sum of fwd+bwd path candidates.
        while (iter.next()) {
            final int edgeId = getOrigEdgeId(iter, !reverse);
            final int prevOrNextOrigEdgeId = getOrigEdgeId(edgeState, reverse);
            if (!traversalMode.hasUTurnSupport() && turnCostExtension.isUTurn(edgeId, prevOrNextOrigEdgeId)) {
                continue;
            }
            int key = GHUtility.getEdgeKey(graph, edgeId, iter.getBaseNode(), !reverse);
            SPTEntry entryOther = bestWeightMapOther.get(key);
            if (entryOther == null) {
                continue;
            }

            double turnCostsAtBridgeNode = reverse ?
                    turnWeighting.calcTurnWeight(iter.getOrigEdgeLast(), iter.getBaseNode(), prevOrNextOrigEdgeId) :
                    turnWeighting.calcTurnWeight(prevOrNextOrigEdgeId, iter.getBaseNode(), iter.getOrigEdgeFirst());

            double newWeight = entry.getWeightOfVisitedPath() + entryOther.getWeightOfVisitedPath() + turnCostsAtBridgeNode;
            if (newWeight < bestPath.getWeight()) {
                bestPath.setSwitchToFrom(reverse);
                bestPath.setSPTEntry(entry);
                bestPath.setSPTEntryTo(entryOther);
                bestPath.setWeight(newWeight);
            }
        }
    }

    @Override
    protected Path createAndInitPath() {
        bestPath = new EdgeBasedPathCH(graph, graph.getBaseGraph(), weighting);
        return bestPath;
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
        if (incEdge == EdgeIterator.NO_EDGE)
            return true;
        final int prevOrNextEdgeId = getOrigEdgeId(edge, !reverse);
        if (!traversalMode.hasUTurnSupport() && turnCostExtension.isUTurn(incEdge, prevOrNextEdgeId))
            return false;

        return additionalEdgeFilter == null || additionalEdgeFilter.accept(edge);
    }

    @Override
    public String toString() {
        return getName() + "|" + weighting;
    }

}
