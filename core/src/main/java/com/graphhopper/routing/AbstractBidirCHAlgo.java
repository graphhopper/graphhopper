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
import com.graphhopper.util.GHUtility;

import java.util.PriorityQueue;
import java.util.function.Supplier;

import static com.graphhopper.util.EdgeIterator.ANY_EDGE;

/**
 * Common subclass for bidirectional CH algorithms.
 * <p>
 *
 * @author Peter Karich
 * @author easbar
 * @see AbstractNonCHBidirAlgo for non-CH bidirectional algorithms
 */
public abstract class AbstractBidirCHAlgo extends AbstractBidirAlgo implements EdgeToEdgeRoutingAlgorithm {
    protected final RoutingCHGraph graph;
    protected final NodeAccess nodeAccess;
    protected RoutingCHEdgeExplorer inEdgeExplorer;
    protected RoutingCHEdgeExplorer outEdgeExplorer;
    protected CHEdgeFilter levelEdgeFilter;
    private Supplier<BidirPathExtractor> pathExtractorSupplier;

    public AbstractBidirCHAlgo(RoutingCHGraph graph, TraversalMode tMode) {
        super(tMode);
        this.graph = graph;
        if (graph.hasTurnCosts() && !tMode.isEdgeBased())
            throw new IllegalStateException("Weightings supporting turn costs cannot be used with node-based traversal mode");
        this.nodeAccess = graph.getBaseGraph().getNodeAccess();
        outEdgeExplorer = graph.createOutEdgeExplorer();
        inEdgeExplorer = graph.createInEdgeExplorer();
        levelEdgeFilter = new CHLevelEdgeFilter(graph);
        pathExtractorSupplier = () -> new NodeBasedCHBidirPathExtractor(graph);
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
     * @param edge    the id of the edge that is currently processed for the expansion
     * @param adjNode the adjacent node of the edge
     * @param incEdge the id of the edge that is incoming to the node the edge is pointed at. usually this is the same as
     *                edge, but for edge-based CH and in case edge corresponds to a shortcut incEdge is the original edge
     *                that is incoming to the node
     * @param weight  the weight the shortest path three entry should carry
     * @param parent  the parent entry of in the shortest path tree
     * @param reverse true if we are currently looking at the backward search, false otherwise
     */
    protected abstract SPTEntry createEntry(int edge, int adjNode, int incEdge, double weight, SPTEntry parent, boolean reverse);

    @Override
    protected void postInitFrom() {
        if (fromOutEdge == ANY_EDGE) {
            fillEdgesFromUsingFilter(levelEdgeFilter);
        } else {
            // need to use a local reference here, because levelEdgeFilter is modified when calling fillEdgesFromUsingFilter
            final CHEdgeFilter tmpFilter = levelEdgeFilter;
            fillEdgesFromUsingFilter(edgeState -> (tmpFilter == null || tmpFilter.accept(edgeState)) && GHUtility.getEdgeFromEdgeKey(edgeState.getOrigEdgeKeyFirst()) == fromOutEdge);
        }
    }

    @Override
    protected void postInitTo() {
        if (toInEdge == ANY_EDGE) {
            fillEdgesToUsingFilter(levelEdgeFilter);
        } else {
            final CHEdgeFilter tmpFilter = levelEdgeFilter;
            fillEdgesToUsingFilter(edgeState -> (tmpFilter == null || tmpFilter.accept(edgeState)) && GHUtility.getEdgeFromEdgeKey(edgeState.getOrigEdgeKeyLast()) == toInEdge);
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
        if (System.nanoTime() > THREAD_CONTEXT.get())
            throw new IllegalArgumentException("route search timed out");
        // we need to finish BOTH searches for CH!
        if (finishedFrom && finishedTo)
            return true;

        // changed also the final finish condition for CH
        return currFrom.weight >= bestWeight && currTo.weight >= bestWeight;
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
        fillEdges(currFrom, pqOpenSetFrom, bestWeightMapFrom, outEdgeExplorer, false);
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
            final int origEdgeId = GHUtility.getEdgeFromEdgeKey(reverse ? iter.getOrigEdgeKeyFirst() : iter.getOrigEdgeKeyLast());
            final int traversalId = traversalMode.createTraversalId(iter, reverse);
            SPTEntry entry = bestWeightMap.get(traversalId);
            if (entry == null) {
                entry = createEntry(iter.getEdge(), iter.getAdjNode(), origEdgeId, weight, currEdge, reverse);
                bestWeightMap.put(traversalId, entry);
                prioQueue.add(entry);
            } else if (entry.getWeightOfVisitedPath() > weight) {
                // flagging this entry, so it will be ignored when it is polled the next time
                // this is faster than removing the entry from the queue and adding again, but for CH it does not really
                // make a difference overall.
                entry.setDeleted();
                boolean isBestEntry = reverse ? (entry == bestBwdEntry) : (entry == bestFwdEntry);
                entry = createEntry(iter.getEdge(), iter.getAdjNode(), origEdgeId, weight, currEdge, reverse);
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
                // use dummy value for edge weight as it is used for neither node- nor edge-based CH
                updateBestPath(Double.POSITIVE_INFINITY, entry, origEdgeId, traversalId, reverse);
            }
        }
    }

    protected double calcWeight(RoutingCHEdgeIteratorState edgeState, boolean reverse, int prevOrNextEdgeId) {
        double edgeWeight = edgeState.getWeight(reverse);
        final int origEdgeId = GHUtility.getEdgeFromEdgeKey(reverse ? edgeState.getOrigEdgeKeyLast() : edgeState.getOrigEdgeKeyFirst());
        double turnCosts = reverse
                ? graph.getTurnWeight(origEdgeId, edgeState.getBaseNode(), prevOrNextEdgeId)
                : graph.getTurnWeight(prevOrNextEdgeId, edgeState.getBaseNode(), origEdgeId);
        return edgeWeight + turnCosts;
    }

    protected void updateEntry(SPTEntry entry, int edge, int adjNode, int incEdge, double weight, SPTEntry parent, boolean reverse) {
        entry.edge = edge;
        entry.weight = weight;
        entry.parent = parent;
    }

    protected boolean accept(RoutingCHEdgeIteratorState edge, SPTEntry currEdge, boolean reverse) {
        // for edge-based traversal we leave it for TurnWeighting to decide whether or not a u-turn is acceptable,
        // but for node-based traversal we exclude such a turn for performance reasons already here
        if (!traversalMode.isEdgeBased() && edge.getEdge() == getIncomingEdge(currEdge))
            return false;

        return levelEdgeFilter == null || levelEdgeFilter.accept(edge);
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
            return createPathExtractor().extract(bestFwdEntry, bestBwdEntry, bestWeight);

        return createEmptyPath();
    }

    public void setPathExtractorSupplier(Supplier<BidirPathExtractor> pathExtractorSupplier) {
        this.pathExtractorSupplier = pathExtractorSupplier;
    }

    BidirPathExtractor createPathExtractor() {
        return pathExtractorSupplier.get();
    }

    protected Path createEmptyPath() {
        return new Path(graph.getBaseGraph());
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
            maxNodes = graph.getBaseGraph().getBaseGraph().getNodes();
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
