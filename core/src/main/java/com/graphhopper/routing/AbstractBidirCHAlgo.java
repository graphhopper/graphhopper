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
import com.graphhopper.routing.ch.NodeBasedCHBidirPathExtractor;
import com.graphhopper.routing.util.TraversalMode;
import com.graphhopper.storage.*;

import java.util.PriorityQueue;

import static com.graphhopper.util.EdgeIterator.ANY_EDGE;

/**
 * Common subclass for bidirectional CH algorithms.
 * <p>
 *
 * @author Peter Karich
 * @author easbar
 * @see AbstractNonCHBidirAlgo for non-CH bidirectional algorithms
 */
public abstract class AbstractBidirCHAlgo extends AbstractBidirAlgo implements BidirRoutingAlgorithm {
    protected final RoutingCHGraph graph;
    protected RoutingCHEdgeExplorer allEdgeExplorer;
    protected RoutingCHEdgeExplorer inEdgeExplorer;
    protected RoutingCHEdgeExplorer outEdgeExplorer;
    protected CHEdgeFilter levelEdgeFilter;

    public AbstractBidirCHAlgo(RoutingCHGraph graph, TraversalMode tMode) {
        super(tMode);
        this.graph = graph;
        if (graph.hasTurnCosts() && !tMode.isEdgeBased())
            throw new IllegalStateException("Weightings supporting turn costs cannot be used with node-based traversal mode");
        this.nodeAccess = graph.getGraph().getNodeAccess();
        allEdgeExplorer = graph.createAllEdgeExplorer();
        outEdgeExplorer = graph.createOutEdgeExplorer();
        inEdgeExplorer = graph.createInEdgeExplorer();
        levelEdgeFilter = new CHLevelEdgeFilter(graph);
        int size = Math.min(Math.max(200, graph.getNodes() / 10), 150_000);
        initCollections(size);
    }

    @Override
    protected void initCollections(int size) {
        super.initCollections(Math.min(size, 2000));
    }

    /**
     * Creates a new entry of the shortest path tree (a {@link SPTEntry} or one of its subclasses) during a dijkstra
     * expansion.
     *
     * @param edge    the edge that is currently processed for the expansion
     * @param incEdge the id of the edge that is incoming to the node the edge is pointed at. usually this is the same as
     *                edge.getEdge(), but for edge-based CH and in case edge is a shortcut incEdge is the original edge
     *                that is incoming to the node
     * @param weight  the weight the shortest path three entry should carry
     * @param parent  the parent entry of in the shortest path tree
     * @param reverse true if we are currently looking at the backward search, false otherwise
     */
    protected abstract SPTEntry createEntry(RoutingCHEdgeIteratorState edge, int incEdge, double weight, SPTEntry parent, boolean reverse);

    protected BidirPathExtractor createPathExtractor(RoutingCHGraph graph) {
        return new NodeBasedCHBidirPathExtractor(graph);
    }

    @Override
    protected void postInitFrom() {
        if (fromOutEdge == ANY_EDGE) {
            fillEdgesFromUsingFilter(levelEdgeFilter);
        } else {
            // need to use a local reference here, because levelEdgeFilter is modified when calling fillEdgesFromUsingFilter
            final CHEdgeFilter tmpFilter = levelEdgeFilter;
            fillEdgesFromUsingFilter(new CHEdgeFilter() {
                @Override
                public boolean accept(RoutingCHEdgeIteratorState edgeState) {
                    return (tmpFilter == null || tmpFilter.accept(edgeState)) && edgeState.getOrigEdgeFirst() == fromOutEdge;
                }
            });
        }
    }

    @Override
    protected void postInitTo() {
        if (toInEdge == ANY_EDGE) {
            fillEdgesToUsingFilter(levelEdgeFilter);
        } else {
            final CHEdgeFilter tmpFilter = levelEdgeFilter;
            fillEdgesToUsingFilter(new CHEdgeFilter() {
                @Override
                public boolean accept(RoutingCHEdgeIteratorState edgeState) {
                    return (tmpFilter == null || tmpFilter.accept(edgeState)) && edgeState.getOrigEdgeLast() == toInEdge;
                }
            });
        }
    }

    /**
     * @param edgeFilter edge filter used to fill edges. the {@link #levelEdgeFilter} reference will be set to
     *                   edgeFilter by this method, so make sure edgeFilter does not use it directly.
     */
    protected void fillEdgesFromUsingFilter(CHEdgeFilter edgeFilter) {
        // we temporarily ignore the additionalEdgeFilter
        CHEdgeFilter tmpFilter = levelEdgeFilter;
        levelEdgeFilter = edgeFilter;
        finishedFrom = !fillEdgesFrom();
        levelEdgeFilter = tmpFilter;
    }

    /**
     * @see #fillEdgesFromUsingFilter(CHEdgeFilter)
     */
    protected void fillEdgesToUsingFilter(CHEdgeFilter edgeFilter) {
        // we temporarily ignore the additionalEdgeFilter
        CHEdgeFilter tmpFilter = levelEdgeFilter;
        levelEdgeFilter = edgeFilter;
        finishedTo = !fillEdgesTo();
        levelEdgeFilter = tmpFilter;
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
    boolean fillEdgesFrom() {
        if (pqOpenSetFrom.isEmpty()) {
            return false;
        }
        currFrom = pqOpenSetFrom.poll();
        visitedCountFrom++;
        if (fromEntryCanBeSkipped()) {
            return true;
        }
        if (fwdSearchCanBeStopped()) {
            return false;
        }
        bestWeightMapOther = bestWeightMapTo;
        fillEdges(currFrom, pqOpenSetFrom, bestWeightMapFrom, outEdgeExplorer, false);
        return true;
    }

    @Override
    boolean fillEdgesTo() {
        if (pqOpenSetTo.isEmpty()) {
            return false;
        }
        currTo = pqOpenSetTo.poll();
        visitedCountTo++;
        if (toEntryCanBeSkipped()) {
            return true;
        }
        if (bwdSearchCanBeStopped()) {
            return false;
        }
        bestWeightMapOther = bestWeightMapFrom;
        fillEdges(currTo, pqOpenSetTo, bestWeightMapTo, inEdgeExplorer, true);
        return true;
    }

    private void fillEdges(SPTEntry currEdge, PriorityQueue<SPTEntry> prioQueue,
                           IntObjectMap<SPTEntry> bestWeightMap, RoutingCHEdgeExplorer explorer, boolean reverse) {
        RoutingCHEdgeIterator iter = explorer.setBaseNode(currEdge.adjNode);
        while (iter.next()) {
            if (!accept(iter, currEdge, reverse))
                continue;

            final double weight = calcWeight(iter, currEdge, reverse);
            if (Double.isInfinite(weight)) {
                continue;
            }
            final int origEdgeId = getOrigEdgeId(iter, reverse);
            final int traversalId = getTraversalId(iter, origEdgeId, reverse);
            SPTEntry entry = bestWeightMap.get(traversalId);
            if (entry == null) {
                entry = createEntry(iter, origEdgeId, weight, currEdge, reverse);
                bestWeightMap.put(traversalId, entry);
                prioQueue.add(entry);
            } else if (entry.getWeightOfVisitedPath() > weight) {
                prioQueue.remove(entry);
                updateEntry(entry, iter, origEdgeId, weight, currEdge, reverse);
                prioQueue.add(entry);
            } else
                continue;

            if (updateBestPath) {
                // only needed for edge-based -> skip the calculation and use dummy value otherwise
                updateBestPath(Double.POSITIVE_INFINITY, entry, origEdgeId, traversalId, reverse);
            }
        }
    }

    protected double calcWeight(RoutingCHEdgeIteratorState edgeState, boolean reverse, int prevOrNextEdgeId) {
        double edgeWeight = edgeState.getWeight(reverse);
        final int origEdgeId = reverse ? edgeState.getOrigEdgeLast() : edgeState.getOrigEdgeFirst();
        double turnCosts = reverse
                ? graph.getTurnWeight(origEdgeId, edgeState.getBaseNode(), prevOrNextEdgeId)
                : graph.getTurnWeight(prevOrNextEdgeId, edgeState.getBaseNode(), origEdgeId);
        return edgeWeight + turnCosts;
    }

    protected void updateEntry(SPTEntry entry, RoutingCHEdgeIteratorState edge, int edgeId, double weight, SPTEntry parent, boolean reverse) {
        entry.edge = edge.getEdge();
        entry.weight = weight;
        entry.parent = parent;
    }

    protected boolean accept(RoutingCHEdgeIteratorState edge, SPTEntry currEdge, boolean reverse) {
        return accept(edge, getIncomingEdge(currEdge));
    }

    protected int getOrigEdgeId(RoutingCHEdgeIteratorState edge, boolean reverse) {
        return edge.getEdge();
    }

    protected int getTraversalId(RoutingCHEdgeIteratorState edge, int origEdgeId, boolean reverse) {
        return getTraversalId(edge, reverse);
    }

    protected int getTraversalId(RoutingCHEdgeIteratorState edge, boolean reverse) {
        return traversalMode.createTraversalId(edge.getBaseNode(), edge.getAdjNode(), edge.getEdge(), reverse);
    }

    @Override
    protected int getOtherNode(int edge, int node) {
        return graph.getOtherNode(edge, node);
    }

    protected double calcWeight(RoutingCHEdgeIteratorState iter, SPTEntry currEdge, boolean reverse) {
        return calcWeight(iter, reverse, getIncomingEdge(currEdge)) + currEdge.getWeightOfVisitedPath();
    }

    @Override
    protected double getInEdgeWeight(SPTEntry entry) {
        return graph.getEdgeIteratorState(getIncomingEdge(entry), entry.adjNode).getWeight(false);
    }

    @Override
    protected Path extractPath() {
        if (finished())
            return createPathExtractor(graph).extract(bestFwdEntry, bestBwdEntry, bestWeight);

        return createEmptyPath();
    }

    protected boolean accept(RoutingCHEdgeIteratorState iter, int prevOrNextEdgeId) {
        // for edge-based traversal we leave it for TurnWeighting to decide whether or not a u-turn is acceptable,
        // but for node-based traversal we exclude such a turn for performance reasons already here
        if (!traversalMode.isEdgeBased() && iter.getEdge() == prevOrNextEdgeId)
            return false;

        return levelEdgeFilter == null || levelEdgeFilter.accept(iter);
    }

    protected Path createEmptyPath() {
        return new Path(graph.getGraph());
    }

    @Override
    public String toString() {
        return getName() + "|" + graph.getWeighting();
    }

    private static class CHLevelEdgeFilter implements CHEdgeFilter {
        private final RoutingCHGraph graph;
        private final int maxNodes;

        public CHLevelEdgeFilter(RoutingCHGraph graph) {
            this.graph = graph;
            maxNodes = graph.getBaseGraph().getNodes();
        }

        @Override
        public boolean accept(RoutingCHEdgeIteratorState edgeState) {
            int base = edgeState.getBaseNode();
            int adj = edgeState.getAdjNode();
            // always accept virtual edges, see #288
            if (base >= maxNodes || adj >= maxNodes)
                return true;

            // minor performance improvement: shortcuts in wrong direction are disconnected, so no need to exclude them
            if (edgeState.isShortcut())
                return true;

            return graph.getLevel(base) <= graph.getLevel(adj);
        }
    }
}
