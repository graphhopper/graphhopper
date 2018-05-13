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
import com.graphhopper.routing.ch.Path4CH;
import com.graphhopper.routing.util.DefaultEdgeFilter;
import com.graphhopper.routing.util.EdgeFilter;
import com.graphhopper.routing.util.TraversalMode;
import com.graphhopper.routing.weighting.TurnWeighting;
import com.graphhopper.storage.Graph;
import com.graphhopper.storage.SPTEntry;
import com.graphhopper.util.EdgeExplorer;
import com.graphhopper.util.EdgeIterator;
import com.graphhopper.util.EdgeIteratorState;
import com.graphhopper.util.GHUtility;

public abstract class AbstractBidirectionEdgeCHNoSOD extends AbstractBidirAlgo {
    private int from;
    private int to;
    private final EdgeExplorer innerInExplorer;
    private final EdgeExplorer innerOutExplorer;
    private final TurnWeighting turnWeighting;

    public AbstractBidirectionEdgeCHNoSOD(Graph graph, TurnWeighting weighting) {
        super(graph, weighting, TraversalMode.EDGE_BASED_2DIR);
        this.turnWeighting = weighting;
        // we need extra edge explorers, because they get called inside a loop that already iterates over edges
        innerInExplorer = graph.createEdgeExplorer(new DefaultEdgeFilter(flagEncoder, true, false));
        innerOutExplorer = graph.createEdgeExplorer(new DefaultEdgeFilter(flagEncoder, false, true));
    }

    @Override
    void init(int from, double fromWeight, int to, double toWeight) {
        this.from = from;
        this.to = to;
        currFrom = createStartEntry(from, fromWeight, false);
        currTo = createStartEntry(to, toWeight, true);
        pqOpenSetFrom.add(currFrom);
        pqOpenSetTo.add(currTo);
        if (from == to) {
            // special case of identical start and end
            setFinished();
            return;
        }
        EdgeFilter filter = additionalEdgeFilter;
        setEdgeFilter(EdgeFilter.ALL_EDGES);
        fillEdgesFrom();
        fillEdgesTo();
        setEdgeFilter(filter);
    }

    private void setFinished() {
        bestPath.sptEntry = currFrom;
        bestPath.edgeTo = currTo;
        bestPath.setWeight(0);
        finishedFrom = true;
        finishedTo = true;
    }

    @Override
    public Path calcPath(int from, int to) {
        this.from = from;
        this.to = to;
        return super.calcPath(from, to);
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
                bestPath.setSPTEntryTo(new SPTEntry(EdgeIterator.NO_EDGE, oppositeNode, 0));
                bestPath.setWeight(entry.getWeightOfVisitedPath());
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
            if (!traversalMode.hasUTurnSupport() && edgeId == prevOrNextOrigEdgeId) {
                continue;
            }
            int key = GHUtility.getEdgeKey(graph, edgeId, iter.getBaseNode(), !reverse);
            SPTEntry entryOther = bestWeightMapOther.get(key);
            if (entryOther == null) {
                continue;
            }

            double turnCostsAtBridgeNode = reverse ?
                    turnWeighting.calcTurnWeight(iter.getLastOrigEdge(), iter.getBaseNode(), prevOrNextOrigEdgeId) :
                    turnWeighting.calcTurnWeight(prevOrNextOrigEdgeId, iter.getBaseNode(), iter.getFirstOrigEdge());

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
        bestPath = new Path4CH(graph, graph.getBaseGraph(), weighting);
        return bestPath;
    }

    @Override
    protected int getOrigEdgeId(EdgeIteratorState edge, boolean reverse) {
        return reverse ? edge.getFirstOrigEdge() : edge.getLastOrigEdge();
    }

    @Override
    protected int getTraversalId(EdgeIteratorState edge, int origEdgeId, boolean reverse) {
        EdgeIteratorState iterState = graph.getEdgeIteratorState(origEdgeId, edge.getAdjNode());
        return GHUtility.createEdgeKey(iterState.getBaseNode(), iterState.getAdjNode(), iterState.getEdge(), reverse);
    }

    @Override
    protected double calcWeight(EdgeIteratorState edge, SPTEntry currEdge, boolean reverse) {
        return weighting.calcWeight(edge, reverse, ((CHEntry) currEdge).incEdge) + currEdge.getWeightOfVisitedPath();
    }

    @Override
    protected boolean accept(EdgeIteratorState edge, SPTEntry currEdge, boolean reverse) {
        int edgeId = getOrigEdgeId(edge, !reverse);
        if (!traversalMode.hasUTurnSupport() && edgeId == ((CHEntry) currEdge).incEdge)
            return false;

        return additionalEdgeFilter == null || additionalEdgeFilter.accept(edge);
    }

    @Override
    public String toString() {
        return getName() + "|" + weighting;
    }

}
