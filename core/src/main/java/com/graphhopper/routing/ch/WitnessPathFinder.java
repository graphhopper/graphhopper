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
package com.graphhopper.routing.ch;

import com.carrotsearch.hppc.IntArrayList;
import com.carrotsearch.hppc.IntObjectHashMap;
import com.carrotsearch.hppc.IntObjectMap;
import com.graphhopper.apache.commons.collections.IntDoubleBinaryHeap;
import com.graphhopper.routing.util.DefaultEdgeFilter;
import com.graphhopper.routing.weighting.TurnWeighting;
import com.graphhopper.storage.CHGraph;
import com.graphhopper.storage.GraphHopperStorage;
import com.graphhopper.util.EdgeExplorer;
import com.graphhopper.util.EdgeIterator;
import com.graphhopper.util.EdgeIteratorState;
import com.graphhopper.util.GHUtility;

import java.util.Arrays;

import static com.graphhopper.util.EdgeIterator.NO_EDGE;
import static java.lang.Double.isInfinite;

/**
 * Helper class used to perform local witness path searches for graph preparation in edge-based Contraction Hierarchies.
 * <p>
 * Let x be a node to be contracted (the 'center node') and s and t neighboring un-contracted nodes of x that are
 * directly connected with x (via a normal edge or a shortcut). This class is used to examine the optimal path
 * between s and t in the graph of not yet contracted nodes. More precisely it looks at the minimal-weight-path from an
 * original edge incoming to s (the 'source edge') to an arbitrary original edge incoming to t where the turn-costs at t
 * onto a given original edge outgoing from t (the 'target edge') are also considered. This class is mainly used to
 * differentiate between the following two cases:
 * <p>
 * 1) The optimal path described above has finite weight and is a 'direct center node path': it only consists of
 * one edge from s to x, an arbitrary number of loops at x and one edge from x to t.
 * 2) The optimal path has infinite weight or it is not such a direct center node path, i.e. it includes edges from s
 * <p>
 * To find the optimal path an edge-based unidirectional Dijkstra algorithm is used that takes into account turn-costs.
 * The search can be initialized for a given source edge and node to be contracted x. Subsequent searches for different
 * target edges will keep on building the shortest path tree from previous searches. For the performance of edge-based
 * CH graph preparation it is crucial to limit the local witness path searches. However the search always needs to
 * find the best direct center node path if one exists. Therefore we may stop expanding edges when a certain limit is
 * exceeded, but even then we still need to expand edges that could possibly yield a direct center node path and we may
 * only stop this when it is guaranteed that no such direct path exists. Here we limit the maximum number of settled
 * edges during the search and determine this maximum number based on the statistics we collected during previous
 * searches.
 */
public class WitnessPathFinder {
    private static final int NO_NODE = -1;

    protected final GraphHopperStorage graph;
    protected final CHGraph chGraph;
    final TurnWeighting turnWeighting;
    protected final EdgeExplorer outEdgeExplorer;
    final EdgeExplorer origInEdgeExplorer;
    protected final int maxLevel;

    // search setup parameters
    int sourceEdge;
    protected int sourceNode;
    int centerNode;

    // best path properties
    double bestPathWeight;
    int bestPathIncEdge;
    boolean bestPathIsDirectCenterNodePath;

    // we allocate memory for all possible edge keys
    private double[] weights;
    private int[] edges;
    private int[] incEdges;
    private int[] parents;
    private int[] adjNodes;
    private boolean[] isDirectCenterNodePaths;

    // used to keep track of which entries have been written during the current search to be able to efficiently reset
    // the data structures for the next search
    private IntArrayList changedEdges;

    // used to store parent information of the initial search entries
    private IntObjectMap<WitnessSearchEntry> rootParents;

    // used to pick the next edge to be expanded during Dijkstra search
    private IntDoubleBinaryHeap heap;

    // used to limit searches
    int numSettledEdges;
    // todo: provide setter
    private final int minimumMaxSettledEdges = 100;
    int maxSettledEdges = minimumMaxSettledEdges;
    int numDirectCenterNodePaths;
    // Number of standard deviations above the mean of the distribution of observed number of settled edges in previous
    // searches where the maximum for the next searches is chosen. For a normal distribution for example sigmaFactor = 2
    // means that about 95% of all observations are included.
    // todo: make private and use setter
    public static double sigmaFactor = 3.0;
    // Used to keep track of the average number and distribution width of settled edges during the last searches. This
    // allows estimating a reasonable limit for the maximum number of settled edges for the next searches.
    private final OnFlyStatisticsCalculator statisticsCalculator = new OnFlyStatisticsCalculator();
    private final int statisticsResetInterval = 10_000;

    // statistics to analyze performance
    int numPolledEdges;
    public static int searchCount;
    public static int pollCount;
    protected final Stats stats = new Stats();

    public WitnessPathFinder(GraphHopperStorage graph, CHGraph chGraph, TurnWeighting turnWeighting) {
        this.graph = graph;
        this.chGraph = chGraph;
        this.turnWeighting = turnWeighting;
        DefaultEdgeFilter inEdgeFilter = new DefaultEdgeFilter(turnWeighting.getFlagEncoder(), true, false);
        DefaultEdgeFilter outEdgeFilter = new DefaultEdgeFilter(turnWeighting.getFlagEncoder(), false, true);
        outEdgeExplorer = chGraph.createEdgeExplorer(outEdgeFilter);
        origInEdgeExplorer = graph.createEdgeExplorer(inEdgeFilter);
        maxLevel = chGraph.getNodes();
        setupSearcher(graph);
    }

    /**
     * Deletes the shortest path tree that has been found so far and initializes a new witness path search for a given
     * node to be contracted and search edge.
     *
     * @param centerNode the node to be contracted (x)
     * @param sourceNode the neighbor node from which the search starts (s)
     * @param sourceEdge the original edge incoming to s from which the search starts
     * @return the number of initial entries and always 0 if we can not directly reach the center node from the given
     * source edge, e.g. when turn costs at s do not allow this.
     */
    public int initSearch(int centerNode, int sourceNode, int sourceEdge) {
        reset();
        this.sourceEdge = sourceEdge;
        this.sourceNode = sourceNode;
        this.centerNode = centerNode;
        setInitialEntries(sourceNode, sourceEdge, centerNode);
        // if there is no entry that reaches the center node we won't need to search for any witnesses
        if (numDirectCenterNodePaths < 1) {
            reset();
            return 0;
        }
        searchCount++;
        int numEntries = getNumEntries();
        stats.onInitEntries(numEntries);
        return numEntries;
    }

    /**
     * Runs a witness path search for a given target edge. Results of previous searches (the shortest path tree) are
     * reused and the previous search is extended if necessary. Note that you need to call
     * {@link #initSearch(int, int, int)} before calling this method to initialize the search.
     *
     * @param targetNode the neighbor node where the node should end (t)
     * @param targetEdge the original edge outgoing from t where the search ends
     * @return the leaf shortest path tree entry ending in an edge incoming in t if a 'direct center node path'
     * (see above) has been found to be the optimal path or null if the optimal path is no such direct path (in this case
     * either the optimal path is a 'witness' or no finite weight path starting with the search edge and leading to
     * the target edge could be found at all).
     */
    public WitnessSearchEntry runSearch(int targetNode, int targetEdge) {
        // if source and target are equal we already have a candidate for the best path: a simple turn from the source
        // to the target edge
        bestPathWeight = sourceNode == targetNode
                ? calcTurnWeight(sourceEdge, sourceNode, targetEdge)
                : Double.POSITIVE_INFINITY;
        bestPathIncEdge = NO_EDGE;
        bestPathIsDirectCenterNodePath = false;

        // check if we can already reach the target from the shortest path tree we discovered so far
        EdgeIterator inIter = origInEdgeExplorer.setBaseNode(targetNode);
        while (inIter.next()) {
            final int incEdge = inIter.getLastOrigEdge();
            final int edgeKey = getEdgeKey(incEdge, targetNode);
            if (edges[edgeKey] != NO_EDGE) {
                updateBestPath(targetNode, targetEdge, edgeKey);
            }
        }

        // run dijkstra to find the optimal path
        while (!heap.isEmpty()) {
            if (numDirectCenterNodePaths < 1 && (!bestPathIsDirectCenterNodePath || isInfinite(bestPathWeight))) {
                // we have not found a connection to the target edge yet and there are no entries on the heap anymore 
                // that could yield a direct center node path
                break;
            }
            final int currKey = heap.peek_element();
            if (weights[currKey] > bestPathWeight) {
                // just reaching this edge is more expensive than the best path found so far including the turn costs
                // to reach the target edge -> we can stop
                // important: we only peeked so far, so we keep the entry for future searches
                break;
            }
            heap.poll_element();
            numPolledEdges++;
            pollCount++;

            if (isDirectCenterNodePaths[currKey]) {
                numDirectCenterNodePaths--;
            }

            // after a certain amount of edges has been settled we only expand entries that might yield a direct center
            // node path
            if (numSettledEdges > maxSettledEdges && !isDirectCenterNodePaths[currKey]) {
                continue;
            }

            EdgeIterator iter = outEdgeExplorer.setBaseNode(adjNodes[currKey]);
            while (iter.next()) {
                if (isContracted(iter.getAdjNode())) {
                    continue;
                }
                // do not allow u-turns
                if (iter.getFirstOrigEdge() == incEdges[currKey]) {
                    continue;
                }
                double weight = turnWeighting.calcWeight(iter, false, incEdges[currKey]) + weights[currKey];
                if (isInfinite(weight)) {
                    continue;
                }
                boolean isDirectCenterNodePath = isDirectCenterNodePaths[currKey] && iter.getAdjNode() == centerNode;

                // dijkstra expansion: add or update current entries
                int key = getEdgeKey(iter.getLastOrigEdge(), iter.getAdjNode());
                if (edges[key] == NO_EDGE) {
                    setEntry(key, iter, weight, currKey, isDirectCenterNodePath);
                    changedEdges.add(key);
                    heap.insert_(weight, key);
                    updateBestPath(targetNode, targetEdge, key);
                } else if (weight < weights[key]) {
                    updateEntry(key, iter, weight, currKey, isDirectCenterNodePath);
                    heap.update_(weight, key);
                    updateBestPath(targetNode, targetEdge, key);
                }
            }
            numSettledEdges++;
            // do not keep searching after to node has been expanded first time, should speed up contraction a bit but
            // leads to less witnesses being found.
//            if (adjNodes[currKey] == targetNode) {
//                break;
//            }
        }

        if (bestPathIsDirectCenterNodePath) {
            int edgeKey = getEdgeKey(bestPathIncEdge, targetNode);
            WitnessSearchEntry result = getEntryForKey(edgeKey);
            // prepend all parents up to root
            WitnessSearchEntry entry = result;
            while (parents[edgeKey] >= 0) {
                edgeKey = parents[edgeKey];
                WitnessSearchEntry parent = getEntryForKey(edgeKey);
                entry.parent = parent;
                entry = parent;
            }
            entry.parent = rootParents.get(parents[edgeKey]);
            return result;
        } else {
            return null;
        }
    }

    public int getNumPolledEdges() {
        return numPolledEdges;
    }

    public String getStatusString() {
        return stats.toString();
    }

    public void resetStats() {
        stats.reset();
    }

    protected void setupSearcher(GraphHopperStorage graph) {
        final int numOriginalEdges = graph.getBaseGraph().getAllEdges().length();
        final int numEntries = 2 * numOriginalEdges;
        initStorage(numEntries);
        initCollections();
    }

    private void initStorage(int numEntries) {
        weights = new double[numEntries];
        Arrays.fill(weights, Double.POSITIVE_INFINITY);

        edges = new int[numEntries];
        Arrays.fill(edges, NO_EDGE);

        incEdges = new int[numEntries];
        Arrays.fill(incEdges, NO_EDGE);

        parents = new int[numEntries];
        Arrays.fill(parents, NO_NODE);

        adjNodes = new int[numEntries];
        Arrays.fill(adjNodes, NO_NODE);

        isDirectCenterNodePaths = new boolean[numEntries];
        Arrays.fill(isDirectCenterNodePaths, false);
    }

    private void initCollections() {
        rootParents = new IntObjectHashMap<>(10);
        changedEdges = new IntArrayList(1000);
        heap = new IntDoubleBinaryHeap(1000);
    }

    protected void setInitialEntries(int sourceNode, int sourceEdge, int centerNode) {
        EdgeIterator outIter = outEdgeExplorer.setBaseNode(sourceNode);
        while (outIter.next()) {
            if (isContracted(outIter.getAdjNode())) {
                continue;
            }
            double turnWeight = calcTurnWeight(sourceEdge, sourceNode, outIter.getFirstOrigEdge());
            if (isInfinite(turnWeight)) {
                continue;
            }
            double edgeWeight = turnWeighting.calcWeight(outIter, false, NO_EDGE);
            double weight = turnWeight + edgeWeight;
            boolean isDirectCenterNodePath = outIter.getAdjNode() == centerNode;
            int incEdge = outIter.getLastOrigEdge();
            int adjNode = outIter.getAdjNode();
            int key = getEdgeKey(incEdge, adjNode);
            int parentKey = -key - 1;
            WitnessSearchEntry parent = new WitnessSearchEntry(
                    NO_EDGE,
                    outIter.getFirstOrigEdge(),
                    sourceNode, turnWeight, false);
            if (edges[key] == NO_EDGE) {
                // add new initial entry
                edges[key] = outIter.getEdge();
                incEdges[key] = incEdge;
                adjNodes[key] = adjNode;
                weights[key] = weight;
                parents[key] = parentKey;
                isDirectCenterNodePaths[key] = isDirectCenterNodePath;
                rootParents.put(parentKey, parent);
                changedEdges.add(key);
            } else if (weight < weights[key]) {
                // update existing entry, there may be entries with the same adjNode and last original edge,
                // but we only need the one with the lowest weight
                edges[key] = outIter.getEdge();
                weights[key] = weight;
                parents[key] = parentKey;
                isDirectCenterNodePaths[key] = isDirectCenterNodePath;
                rootParents.put(parentKey, parent);
            }
        }

        // now that we know which entries are actually needed we add them to the heap
        for (int i = 0; i < changedEdges.size(); ++i) {
            int key = changedEdges.get(i);
            if (isDirectCenterNodePaths[key]) {
                numDirectCenterNodePaths++;
            }
            heap.insert_(weights[key], key);
        }
    }

    private void reset() {
        updateSettledEdgeStatistics();
        stats.onReset(numSettledEdges, maxSettledEdges);
        numSettledEdges = 0;
        numPolledEdges = 0;
        numDirectCenterNodePaths = 0;
        doReset();
    }

    private void updateSettledEdgeStatistics() {
        // we use the statistics of settled edges of a batch of previous witness path searches to dynamically 
        // approximate the number of settled edges in the next batch
        statisticsCalculator.addObservation(numSettledEdges);
        if (statisticsCalculator.getCount() == statisticsResetInterval) {
            maxSettledEdges = Math.max(
                    minimumMaxSettledEdges,
                    (int) (statisticsCalculator.getMean() +
                            sigmaFactor * Math.sqrt(statisticsCalculator.getVariance()))
            );
            stats.onStatCalcReset(statisticsCalculator);
            statisticsCalculator.reset();
        }
    }

    void doReset() {
        for (int i = 0; i < changedEdges.size(); ++i) {
            resetEntry(changedEdges.get(i));
        }
        changedEdges.elementsCount = 0;
        rootParents.clear();
        heap.clear();
    }

    private void updateBestPath(int targetNode, int targetEdge, int edgeKey) {
        // whenever we hit the target node we update the best path
        if (adjNodes[edgeKey] == targetNode) {
            double totalWeight = weights[edgeKey] + calcTurnWeight(incEdges[edgeKey], targetNode, targetEdge);
            // there is a path to the target so we know that there must be some parent. therefore a negative parent key
            // means that the parent is a root parent (a parent of an initial entry) and we did not go via the center
            // node.
            boolean isDirectCenterNodePath = parents[edgeKey] >= 0 && isDirectCenterNodePaths[parents[edgeKey]];
            // in case of equal weights we always prefer a witness path over a direct center node path
            double tolerance = isDirectCenterNodePath ? 0 : 1.e-6;
            if (totalWeight - tolerance < bestPathWeight) {
                bestPathWeight = totalWeight;
                bestPathIncEdge = incEdges[edgeKey];
                bestPathIsDirectCenterNodePath = isDirectCenterNodePath;
            }
        }
    }

    private void setEntry(int key, EdgeIteratorState edge, double weight, int parent, boolean isDirectCenterNodePath) {
        edges[key] = edge.getEdge();
        incEdges[key] = edge.getLastOrigEdge();
        adjNodes[key] = edge.getAdjNode();
        weights[key] = weight;
        parents[key] = parent;
        if (isDirectCenterNodePath) {
            isDirectCenterNodePaths[key] = true;
            numDirectCenterNodePaths++;
        }
    }

    private void updateEntry(int key, EdgeIteratorState edge, double weight, int currKey, boolean isDirectCenterNodePath) {
        edges[key] = edge.getEdge();
        weights[key] = weight;
        parents[key] = currKey;
        if (isDirectCenterNodePath) {
            if (!isDirectCenterNodePaths[key]) {
                numDirectCenterNodePaths++;
            }
        } else {
            if (isDirectCenterNodePaths[key]) {
                numDirectCenterNodePaths--;
            }
        }
        isDirectCenterNodePaths[key] = isDirectCenterNodePath;
    }

    private void resetEntry(int key) {
        weights[key] = Double.POSITIVE_INFINITY;
        edges[key] = NO_EDGE;
        incEdges[key] = NO_EDGE;
        parents[key] = NO_NODE;
        adjNodes[key] = NO_NODE;
        isDirectCenterNodePaths[key] = false;
    }

    int getNumEntries() {
        return heap.getSize();
    }

    private WitnessSearchEntry getEntryForKey(int edgeKey) {
        return new WitnessSearchEntry(edges[edgeKey], incEdges[edgeKey], adjNodes[edgeKey], weights[edgeKey], isDirectCenterNodePaths[edgeKey]);
    }

    int getEdgeKey(int edge, int adjNode) {
        return GHUtility.getEdgeKey(chGraph, edge, adjNode, false);
    }

    double calcTurnWeight(int inEdge, int viaNode, int outEdge) {
        if (inEdge == outEdge) {
            return Double.POSITIVE_INFINITY;
        }
        return turnWeighting.calcTurnWeight(inEdge, viaNode, outEdge);
    }

    boolean isContracted(int node) {
        return chGraph.getLevel(node) != maxLevel;
    }

    static class Stats {
        // helps to analyze how many edges get settled during a search typically, can be reused when stable
        private final long[] settledEdgesStats = new long[20];
        private long totalNumResets;
        private long totalNumStatCalcResets;
        private long totalNumInitialEntries;
        private long totalNumSettledEdges;
        private long totalMaxSettledEdges;
        private long totalMeanSettledEdges;
        private long totalStdDeviationSettledEdges;

        @Override
        public String toString() {
            return String.format("settled edges stats (since last reset) - " +
                            " limit-exhaustion: %5.1f %%, (avg-settled: %5.1f, avg-max: %5.1f, avg-mean: %5.1f, avg-sigma: %5.1f, sigma-factor: %.1f)," +
                            " avg-initial entries: %5.1f, settled edges distribution: %s",
                    divideOrZero(totalNumSettledEdges, totalMaxSettledEdges) * 100,
                    divideOrZero(totalNumSettledEdges, totalNumResets),
                    divideOrZero(totalMaxSettledEdges, totalNumResets),
                    divideOrZero(totalMeanSettledEdges, totalNumStatCalcResets),
                    divideOrZero(totalStdDeviationSettledEdges, totalNumStatCalcResets),
                    sigmaFactor,
                    divideOrZero(totalNumInitialEntries, totalNumResets),
                    Arrays.toString(settledEdgesStats));
        }

        private double divideOrZero(long a, long b) {
            return b == 0 ? 0 : 1.0 * a / b;
        }

        void reset() {
            Arrays.fill(settledEdgesStats, 0);
            totalNumResets = 0;
            totalNumStatCalcResets = 0;
            totalNumInitialEntries = 0;
            totalNumSettledEdges = 0;
            totalMaxSettledEdges = 0;
            totalMeanSettledEdges = 0;
            totalStdDeviationSettledEdges = 0;
        }

        void onInitEntries(int numInitialEntries) {
            totalNumInitialEntries += numInitialEntries;
        }

        void onReset(int numSettledEdges, int maxSettledEdges) {
            int bucket = numSettledEdges / 10;
            if (bucket >= settledEdgesStats.length) {
                bucket = settledEdgesStats.length - 1;
            }

            settledEdgesStats[bucket]++;
            totalNumResets++;
            totalNumSettledEdges += numSettledEdges;
            totalMaxSettledEdges += maxSettledEdges;
        }

        void onStatCalcReset(OnFlyStatisticsCalculator statisticsCalculator) {
            totalNumStatCalcResets++;
            totalMeanSettledEdges += statisticsCalculator.getMean();
            totalStdDeviationSettledEdges += (long) Math.sqrt(statisticsCalculator.getVariance());
        }
    }
}
