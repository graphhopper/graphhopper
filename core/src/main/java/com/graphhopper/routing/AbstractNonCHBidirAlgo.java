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

import com.carrotsearch.hppc.IntObjectMap;
import com.graphhopper.routing.util.EdgeFilter;
import com.graphhopper.routing.util.TraversalMode;
import com.graphhopper.routing.weighting.Weighting;
import com.graphhopper.storage.Graph;
import com.graphhopper.storage.NodeAccess;
import com.graphhopper.util.EdgeExplorer;
import com.graphhopper.util.EdgeIterator;
import com.graphhopper.util.EdgeIteratorState;
import com.graphhopper.util.GHUtility;

import java.util.PriorityQueue;

import static com.graphhopper.util.EdgeIterator.ANY_EDGE;

/**
 * Common subclass for bidirectional algorithms.
 *
 * @author Peter Karich
 * @author easbar
 * @see AbstractBidirCHAlgo for bidirectional CH algorithms
 */
public abstract class AbstractNonCHBidirAlgo extends AbstractBidirAlgo implements EdgeToEdgeRoutingAlgorithm {
    protected final Graph graph;
    protected final NodeAccess nodeAccess;
    protected final Weighting weighting;
    protected EdgeExplorer edgeExplorer;
    protected EdgeFilter additionalEdgeFilter;

    public AbstractNonCHBidirAlgo(Graph graph, Weighting weighting, TraversalMode tMode) {
        super(tMode);
        this.weighting = weighting;
        if (weighting.hasTurnCosts() && !tMode.isEdgeBased())
            throw new IllegalStateException("Weightings supporting turn costs cannot be used with node-based traversal mode");
        this.graph = graph;
        this.nodeAccess = graph.getNodeAccess();
        edgeExplorer = graph.createEdgeExplorer();
        int size = Math.min(Math.max(200, graph.getNodes() / 10), 150_000);
        initCollections(size);
    }

    /**
     * Creates a new entry of the shortest path tree (a {@link SPTEntry} or one of its subclasses) during a dijkstra
     * expansion.
     *
     * @param edge    the edge that is currently processed for the expansion
     * @param weight  the weight the shortest path three entry should carry
     * @param parent  the parent entry of in the shortest path tree
     * @param reverse true if we are currently looking at the backward search, false otherwise
     */
    protected abstract SPTEntry createEntry(EdgeIteratorState edge, double weight, SPTEntry parent, boolean reverse);

    protected DefaultBidirPathExtractor createPathExtractor(Graph graph, Weighting weighting) {
        return new DefaultBidirPathExtractor(graph, weighting);
    }

    protected void postInitFrom() {
        if (fromOutEdge == ANY_EDGE) {
            fillEdgesFrom();
        } else {
            fillEdgesFromUsingFilter(edgeState -> edgeState.getEdge() == fromOutEdge);
        }
    }

    protected void postInitTo() {
        if (toInEdge == ANY_EDGE) {
            fillEdgesTo();
        } else {
            fillEdgesToUsingFilter(edgeState -> edgeState.getEdge() == toInEdge);
        }
    }

    /**
     * @param edgeFilter edge filter used to filter edges during {@link #fillEdgesFrom()}
     */
    protected void fillEdgesFromUsingFilter(EdgeFilter edgeFilter) {
        additionalEdgeFilter = edgeFilter;
        finishedFrom = !fillEdgesFrom();
        additionalEdgeFilter = null;
    }

    /**
     * @see #fillEdgesFromUsingFilter(EdgeFilter)
     */
    protected void fillEdgesToUsingFilter(EdgeFilter edgeFilter) {
        additionalEdgeFilter = edgeFilter;
        finishedTo = !fillEdgesTo();
        additionalEdgeFilter = null;
    }

    @Override
    boolean fillEdgesFrom() {
        while (true) {
            if (pqOpenSetFrom.isEmpty())
                return false;
            currFrom = pqOpenSetFrom.poll();
            if (!currFrom.isDeleted())
                break;
        }
        visitedCountFrom++;
        if (fromEntryCanBeSkipped()) {
            return true;
        }
        if (fwdSearchCanBeStopped()) {
            return false;
        }
        bestWeightMapOther = bestWeightMapTo;
        fillEdges(currFrom, pqOpenSetFrom, bestWeightMapFrom, false);
        return true;
    }

    @Override
    boolean fillEdgesTo() {
        while (true) {
            if (pqOpenSetTo.isEmpty())
                return false;
            currTo = pqOpenSetTo.poll();
            if (!currTo.isDeleted())
                break;
        }
        visitedCountTo++;
        if (toEntryCanBeSkipped()) {
            return true;
        }
        if (bwdSearchCanBeStopped()) {
            return false;
        }
        bestWeightMapOther = bestWeightMapFrom;
        fillEdges(currTo, pqOpenSetTo, bestWeightMapTo, true);
        return true;
    }

    private void fillEdges(SPTEntry currEdge, PriorityQueue<SPTEntry> prioQueue, IntObjectMap<SPTEntry> bestWeightMap, boolean reverse) {
        EdgeIterator iter = edgeExplorer.setBaseNode(currEdge.adjNode);
        while (iter.next()) {
            if (!accept(iter, currEdge.edge))
                continue;

            final double weight = calcWeight(iter, currEdge, reverse);
            if (Double.isInfinite(weight)) {
                continue;
            }
            final int traversalId = traversalMode.createTraversalId(iter, reverse);
            SPTEntry entry = bestWeightMap.get(traversalId);
            if (entry == null) {
                entry = createEntry(iter, weight, currEdge, reverse);
                bestWeightMap.put(traversalId, entry);
                prioQueue.add(entry);
            } else if (entry.getWeightOfVisitedPath() > weight) {
                // flagging this entry, so it will be ignored when it is polled the next time
                entry.setDeleted();
                boolean isBestEntry = reverse ? (entry == bestBwdEntry) : (entry == bestFwdEntry);
                entry = createEntry(iter, weight, currEdge, reverse);
                bestWeightMap.put(traversalId, entry);
                prioQueue.add(entry);
                // if this is the best entry we need to update the best reference as well
                if (isBestEntry)
                    if (reverse)
                        bestBwdEntry = entry;
                    else
                        bestFwdEntry = entry;
            } else
                continue;

            if (updateBestPath) {
                // only needed for edge-based -> skip the calculation and use dummy value otherwise
                double edgeWeight = traversalMode.isEdgeBased() ? weighting.calcEdgeWeight(iter, reverse) : Double.POSITIVE_INFINITY;
                // todo: performance - if bestWeightMapOther.get(traversalId) == null, updateBestPath will exit early and we might
                // have calculated the edgeWeight unnecessarily
                updateBestPath(edgeWeight, entry, EdgeIterator.NO_EDGE, traversalId, reverse);
            }
        }
    }

    protected double calcWeight(EdgeIteratorState iter, SPTEntry currEdge, boolean reverse) {
        // note that for node-based routing the weights will be wrong in case the weighting is returning non-zero
        // turn weights, see discussion in #1960
        return GHUtility.calcWeightWithTurnWeightWithAccess(weighting, iter, reverse, currEdge.edge) + currEdge.getWeightOfVisitedPath();
    }

    @Override
    protected double getInEdgeWeight(SPTEntry entry) {
        return weighting.calcEdgeWeight(graph.getEdgeIteratorState(entry.edge, entry.adjNode), false);
    }

    @Override
    protected Path extractPath() {
        if (finished())
            return createPathExtractor(graph, weighting).extract(bestFwdEntry, bestBwdEntry, bestWeight);

        return createEmptyPath();
    }

    protected boolean accept(EdgeIteratorState iter, int prevOrNextEdgeId) {
        // for edge-based traversal we leave it for TurnWeighting to decide whether or not a u-turn is acceptable,
        // but for node-based traversal we exclude such a turn for performance reasons already here
        if (!traversalMode.isEdgeBased() && iter.getEdge() == prevOrNextEdgeId)
            return false;

        return additionalEdgeFilter == null || additionalEdgeFilter.accept(iter);
    }

    protected Path createEmptyPath() {
        return new Path(graph);
    }

    @Override
    public String toString() {
        return getName() + "|" + weighting;
    }

}
