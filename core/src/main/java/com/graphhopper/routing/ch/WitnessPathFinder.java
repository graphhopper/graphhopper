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

import static java.lang.Double.isInfinite;

public class WitnessPathFinder {
    // for very dense graph a higher initial value is probably appropriate, the initial value does not play a big role
    // because this parameter will be adjusted automatically during the graph contraction
    public static int initialMaxSettledEdges = 100;
    // number of standard deviations above mean where distribution is truncated, for a normal distribution for
    // example sigmaFactor = 2 means about 95% of all observations are included
    public static double sigmaFactor = 3.0;

    protected final GraphHopperStorage graph;
    protected final CHGraph chGraph;
    protected final TurnWeighting turnWeighting;
    protected final EdgeExplorer outEdgeExplorer;
    protected final EdgeExplorer origInEdgeExplorer;
    protected final int maxLevel;
    private final OnFlyStatisticsCalculator statisticsCalculator = new OnFlyStatisticsCalculator();
    protected final Stats stats = new Stats();

    protected int numDirectCenterNodePaths;
    protected int numSettledEdges;
    protected int numPolledEdges;
    protected int maxSettledEdges = initialMaxSettledEdges;
    protected int centerNode;
    protected int fromNode;
    protected int sourceEdge;
    protected double bestWeight;
    protected int resIncEdge;
    protected boolean resViaCenter;

    public static int searchCount;
    public static int pollCount;
    private double[] weights;
    private int[] edges;
    private int[] incEdges;
    private int[] parents;
    private int[] adjNodes;
    private boolean[] isDirectCenterNodePaths;
    private IntObjectMap<WitnessSearchEntry> rootParents;
    private IntDoubleBinaryHeap heap;
    private IntArrayList changedEdges;

    public WitnessPathFinder(GraphHopperStorage graph, CHGraph chGraph, TurnWeighting turnWeighting) {
        this.graph = graph;
        this.chGraph = chGraph;
        this.turnWeighting = turnWeighting;
        DefaultEdgeFilter inEdgeFilter = new DefaultEdgeFilter(turnWeighting.getFlagEncoder(), true, false);
        DefaultEdgeFilter outEdgeFilter = new DefaultEdgeFilter(turnWeighting.getFlagEncoder(), false, true);
        outEdgeExplorer = chGraph.createEdgeExplorer(outEdgeFilter);
        origInEdgeExplorer = graph.createEdgeExplorer(inEdgeFilter);
        maxLevel = chGraph.getNodes();
        initialize(graph);
    }

    protected void initialize(GraphHopperStorage graph) {
        final int numOriginalEdges = graph.getBaseGraph().getAllEdges().length();
        final int numEntries = 2 * numOriginalEdges;
        initStorage(numEntries);
        initCollections();
    }


    private void initCollections() {
        // todo: so far these initial capacities are purely guessed
        rootParents = new IntObjectHashMap<>(10);
        changedEdges = new IntArrayList(1000);
        heap = new IntDoubleBinaryHeap(1000);
    }

    private void initStorage(int numEntries) {
        weights = new double[numEntries];
        Arrays.fill(weights, Double.POSITIVE_INFINITY);

        edges = new int[numEntries];
        Arrays.fill(edges, EdgeIterator.NO_EDGE);

        incEdges = new int[numEntries];
        Arrays.fill(incEdges, EdgeIterator.NO_EDGE);

        parents = new int[numEntries];
        Arrays.fill(parents, -1);

        adjNodes = new int[numEntries];
        Arrays.fill(adjNodes, -1);

        isDirectCenterNodePaths = new boolean[numEntries];
        Arrays.fill(isDirectCenterNodePaths, false);
    }

    public int init(int centerNode, int fromNode, int sourceEdge) {
        reset();
        this.sourceEdge = sourceEdge;
        this.fromNode = fromNode;
        this.centerNode = centerNode;
        setInitialEntries(centerNode, fromNode, sourceEdge);
        // if there is no entry that reaches the center node we can skip the entire search 
        // and do not need any start entries, because no shortcut will ever be required
        if (numDirectCenterNodePaths < 1) {
            reset();
            return 0;
        }
        searchCount++;
        int numEntries = getNumEntries();
        stats.onInitEntries(numEntries);
        return numEntries;
    }

    protected void setInitialEntries(int centerNode, int fromNode, int sourceEdge) {
        EdgeIterator outIter = outEdgeExplorer.setBaseNode(fromNode);
        while (outIter.next()) {
            if (isContracted(outIter.getAdjNode())) {
                continue;
            }
            double turnWeight = calcTurnWeight(sourceEdge, fromNode, outIter.getFirstOrigEdge());
            if (isInfinite(turnWeight)) {
                continue;
            }
            double edgeWeight = turnWeighting.calcWeight(outIter, false, EdgeIterator.NO_EDGE);
            double weight = turnWeight + edgeWeight;
            boolean isDirectCenterNodePath = outIter.getAdjNode() == centerNode;
            int incEdge = outIter.getLastOrigEdge();
            int adjNode = outIter.getAdjNode();
            int key = getEdgeKey(incEdge, adjNode);
            int parentKey = -key - 1;
            WitnessSearchEntry parent = new WitnessSearchEntry(
                    EdgeIterator.NO_EDGE,
                    outIter.getFirstOrigEdge(),
                    fromNode, turnWeight, false);
            if (edges[key] == -1) {
                edges[key] = outIter.getEdge();
                incEdges[key] = incEdge;
                adjNodes[key] = adjNode;
                weights[key] = weight;
                parents[key] = parentKey;
                isDirectCenterNodePaths[key] = isDirectCenterNodePath;
                rootParents.put(parentKey, parent);
                changedEdges.add(key);
            } else if (weight < weights[key]) {
                // there may be entries with the same adjNode and last original edge, but we only need the one with
                // the lowest weight
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

    public WitnessSearchEntry runSearch(int toNode, int targetEdge) {
        // todo: write a test for this case to make it clear
        bestWeight = fromNode == toNode
                ? calcTurnWeight(sourceEdge, fromNode, targetEdge)
                : Double.POSITIVE_INFINITY;
        resIncEdge = EdgeIterator.NO_EDGE;
        resViaCenter = false;

        // check if we can already reach the target from the shortest path tree we discovered so far
        EdgeIterator inIter = origInEdgeExplorer.setBaseNode(toNode);
        while (inIter.next()) {
            final int incEdge = inIter.getLastOrigEdge();
            final int edgeKey = getEdgeKey(incEdge, toNode);
            if (edges[edgeKey] == -1) {
                continue;
            }
            updateBestPath(toNode, targetEdge, edgeKey);
        }

        // run dijkstra to find the optimal path
        while (!heap.isEmpty()) {
            if (numDirectCenterNodePaths < 1 && (!resViaCenter || isInfinite(bestWeight))) {
                // we have not found a connection to the target edge yet and there are no entries
                // in the priority queue anymore that are part of the direct path via the center node
                // -> we will not need a shortcut
                break;
            }
            final int currKey = heap.peek_element();
            if (weights[currKey] > bestWeight) {
                // just reaching this edge is more expensive than the best path found so far including the turn costs
                // to reach the targetOutEdge -> we can stop
                // important: we only peeked so far, so we keep the entry for future searches
                break;
            }
            heap.poll_element();
            numPolledEdges++;
            pollCount++;

            if (isDirectCenterNodePaths[currKey]) {
                numDirectCenterNodePaths--;
            }

            // after a certain amount of edges has been settled we no longer expand entries
            // that are not on a path via the center node
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
                if (edges[key] == -1) {
                    setEntry(key, iter, weight, currKey, isDirectCenterNodePath);
                    changedEdges.add(key);
                    heap.insert_(weight, key);
                    updateBestPath(toNode, targetEdge, key);
                } else if (weight < weights[key]) {
                    updateEntry(key, iter, weight, currKey, isDirectCenterNodePath);
                    heap.update_(weight, key);
                    updateBestPath(toNode, targetEdge, key);
                }
            }
            numSettledEdges++;
            // do not keep searching after to node has been expanded first time, should speed contraction up a bit but finds less witnesses.
//            if (adjNodes[currKey] == toNode) {
//                break;
//            }
        }

        if (resViaCenter) {
            // the best path we could find is an original path so we return it
            // (note that this path may contain loops at the center node)
            int edgeKey = getEdgeKey(resIncEdge, toNode);
            WitnessSearchEntry result = getEntryForKey(edgeKey);
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

    private WitnessSearchEntry getEntryForKey(int edgeKey) {
        return new WitnessSearchEntry(edges[edgeKey], incEdges[edgeKey], adjNodes[edgeKey], weights[edgeKey], isDirectCenterNodePaths[edgeKey]);
    }

    private void setEntry(int key, EdgeIteratorState iter, double weight, int parent, boolean isDirectCenterNodePath) {
        edges[key] = iter.getEdge();
        incEdges[key] = iter.getLastOrigEdge();
        adjNodes[key] = iter.getAdjNode();
        weights[key] = weight;
        parents[key] = parent;
        if (isDirectCenterNodePath) {
            isDirectCenterNodePaths[key] = true;
            numDirectCenterNodePaths++;
        }
    }

    private void updateEntry(int key, EdgeIteratorState iter, double weight, int currKey, boolean isDirectCenterNodePath) {
        edges[key] = iter.getEdge();
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

    private void updateBestPath(int toNode, int targetEdge, int edgeKey) {
        // whenever we hit the target node we update the best path
        if (adjNodes[edgeKey] == toNode) {
            double totalWeight = weights[edgeKey] + calcTurnWeight(incEdges[edgeKey], toNode, targetEdge);
            // we know that there must be some parent so a negative parent key is a real
            // key in the root parents collection --> in this case we did not go via the center
            boolean viaCenter = parents[edgeKey] >= 0 && isDirectCenterNodePaths[parents[edgeKey]];
            // when in doubt prefer a witness path over an original path
            double tolerance = viaCenter ? 0 : 1.e-6;
            if (totalWeight - tolerance < bestWeight) {
                bestWeight = totalWeight;
                resIncEdge = incEdges[edgeKey];
                resViaCenter = viaCenter;
            }
        }
    }

    private void resetEntry(int key) {
        weights[key] = Double.POSITIVE_INFINITY;
        edges[key] = EdgeIterator.NO_EDGE;
        incEdges[key] = EdgeIterator.NO_EDGE;
        parents[key] = -1;
        adjNodes[key] = -1;
        isDirectCenterNodePaths[key] = false;
    }

    void doReset() {
        for (int i = 0; i < changedEdges.size(); ++i) {
            resetEntry(changedEdges.get(i));
        }
        rootParents.clear();
        changedEdges.elementsCount = 0;
        heap.clear();
    }

    int getNumEntries() {
        return heap.getSize();
    }

    private void reset() {
        readjustMaxSettledEdges();
        stats.onReset(numSettledEdges, maxSettledEdges);
        numSettledEdges = 0;
        numPolledEdges = 0;
        numDirectCenterNodePaths = 0;
        doReset();
    }

    private void readjustMaxSettledEdges() {
        // we use the statistics of settled edges of a batch of previous witness path searches to dynamically 
        // approximate the number of settled edges in the next batch
        statisticsCalculator.addObservation(numSettledEdges);
        if (statisticsCalculator.getCount() == 10_000) {
            maxSettledEdges = Math.max(
                    initialMaxSettledEdges,
                    (int) (statisticsCalculator.getMean() +
                            sigmaFactor * Math.sqrt(statisticsCalculator.getVariance()))
            );
            stats.onStatCalcReset(statisticsCalculator);
            statisticsCalculator.reset();
        }
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
