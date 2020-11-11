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
import com.graphhopper.apache.commons.collections.IntFloatBinaryHeap;
import com.graphhopper.util.EdgeIterator;
import com.graphhopper.util.GHUtility;
import com.graphhopper.util.PMap;

import java.util.Arrays;
import java.util.Locale;

import static com.graphhopper.routing.ch.CHParameters.*;
import static com.graphhopper.util.EdgeIterator.NO_EDGE;
import static java.lang.Double.isInfinite;

/**
 * Helper class used to perform local witness path searches for graph preparation in edge-based Contraction Hierarchies.
 * <p>
 * (source edge) -- s -- x -- t -- (target edge)
 * Let x be a node to be contracted (the 'center node') and s and t neighboring un-contracted nodes of x that are
 * directly connected with x (via a normal edge or a shortcut). This class is used to examine the optimal path
 * between s and t in the graph of not yet contracted nodes. More precisely it looks at the minimal-weight-path from an
 * original edge incoming to s (the 'source edge') to an arbitrary original edge incoming to t where the turn-costs at t
 * onto a given original edge outgoing from t (the 'target edge') are also considered. This class is mainly used to
 * differentiate between the following two cases:
 * <p>
 * 1) The optimal path described above has finite weight and only consists of one edge from s to x, an arbitrary number
 * of loops at x, and one edge from x to t. This is called a 'bridge-path' here.
 * 2) The optimal path has infinite weight or it includes an edge from s to another node than x or an edge from another
 * node than x to t. This is called a 'witness-path'.
 * <p>
 * To find the optimal path an edge-based unidirectional Dijkstra algorithm is used that takes into account turn-costs.
 * The search can be initialized for a given source edge and node to be contracted x. Subsequent searches for different
 * target edges will keep on building the shortest path tree from previous searches. For the performance of edge-based
 * CH graph preparation it is crucial to limit the local witness path searches. However the search always needs to at
 * least find the best bridge-path if one exists. Therefore we may stop expanding edges when a certain amount of settled
 * edges is exceeded, but even then we still need to expand edges that could possibly yield a bridge-path and we may
 * only stop this when it is guaranteed that no bridge-path exists. Here we limit the maximum number of settled
 * edges during the search and determine this maximum number based on the statistics we collected during previous
 * searches.
 *
 * @author easbar
 */
public class EdgeBasedWitnessPathSearcher {
    private static final int NO_NODE = -1;
    private static final double MAX_ZERO_WEIGHT_LOOP = 1.e-3;

    private final CHPreparationGraph prepareGraph;
    private PrepareGraphEdgeExplorer outEdgeExplorer;
    private PrepareGraphOrigEdgeExplorer origInEdgeExplorer;

    // general parameters affecting the number of found witnesses and the search time
    private final Params params = new Params();

    // variables of the current search
    private int sourceEdge;
    private int sourceNode;
    private int centerNode;
    private double bestPathWeight;
    private int bestPathIncKey;
    private boolean bestPathIsBridgePath;
    private int numPathsToCenter;
    private int numSettledEdges;
    private int numPolledEdges;

    // data structures used to build the shortest path tree
    // we allocate memory for all possible edge keys and keep track which ones have been discovered so far
    private double[] weights;
    private int[] prepareEdges;
    private int[] parents;
    private int[] adjNodesAndIsPathToCenters;
    private IntObjectMap<PrepareCHEntry> initialEntryParents;
    private IntArrayList changedEdges;
    private IntFloatBinaryHeap dijkstraHeap;

    // we keep track of the average number and distribution width of settled edges during the last searches to estimate
    // an appropriate maximum of settled edges for the next searches
    private int maxSettledEdges;
    private final OnFlyStatisticsCalculator settledEdgesStats = new OnFlyStatisticsCalculator();

    // statistics to analyze performance
    private final Stats currentBatchStats = new Stats();
    private final Stats totalStats = new Stats();

    public EdgeBasedWitnessPathSearcher(CHPreparationGraph prepareGraph, PMap pMap) {
        this.prepareGraph = prepareGraph;
        extractParams(pMap);

        outEdgeExplorer = prepareGraph.createOutEdgeExplorer();
        origInEdgeExplorer = prepareGraph.createInOrigEdgeExplorer();

        maxSettledEdges = params.minimumMaxSettledEdges;
        initStorage(2 * prepareGraph.getOriginalEdges());
        initCollections();
    }

    private void extractParams(PMap pMap) {
        params.sigmaFactor = pMap.getDouble(SIGMA_FACTOR, params.sigmaFactor);
        params.minimumMaxSettledEdges = pMap.getInt(MIN_MAX_SETTLED_EDGES, params.minimumMaxSettledEdges);
        params.settledEdgeStatsResetInterval = pMap.getInt(SETTLED_EDGES_RESET_INTERVAL, params.settledEdgeStatsResetInterval);
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
        if (numPathsToCenter < 1) {
            reset();
            return 0;
        }
        currentBatchStats.numSearches++;
        currentBatchStats.maxNumSettledEdges += maxSettledEdges;
        totalStats.numSearches++;
        totalStats.maxNumSettledEdges += maxSettledEdges;
        return dijkstraHeap.getSize();
    }

    /**
     * Runs a witness path search for a given target edge. Results of previous searches (the shortest path tree) are
     * reused and the previous search is extended if necessary. Note that you need to call
     * {@link #initSearch(int, int, int)} before calling this method to initialize the search.
     *
     * @param targetNode the neighbor node that should be reached by the path (t)
     * @param targetEdge the original edge outgoing from t where the search ends
     * @return the leaf shortest path tree entry (including all ancestor entries) ending in an edge incoming in t if a
     * 'bridge-path' (see above) has been found to be the optimal path or null if the optimal path is either a witness
     * path or no finite weight path starting with the search edge and leading to the target edge could be found at all.
     */
    public PrepareCHEntry runSearch(int targetNode, int targetEdge) {
        // if source and target are equal we already have a candidate for the best path: a simple turn from the source
        // to the target edge
        bestPathWeight = sourceNode == targetNode
                ? calcTurnWeight(sourceEdge, sourceNode, targetEdge)
                : Double.POSITIVE_INFINITY;
        bestPathIncKey = NO_EDGE;
        bestPathIsBridgePath = false;

        // check if we can already reach the target from the shortest path tree we discovered so far
        PrepareGraphOrigEdgeIterator inIter = origInEdgeExplorer.setBaseNode(targetNode);
        while (inIter.next()) {
            final int edgeKey = GHUtility.reverseEdgeKey(inIter.getOrigEdgeKeyLast());
            if (EdgeIterator.Edge.isValid(prepareEdges[edgeKey])) {
                boolean isZeroWeightLoop = parents[edgeKey] >= 0 && targetNode == getAdjNode(parents[edgeKey]) &&
                        weights[edgeKey] - weights[parents[edgeKey]] <= MAX_ZERO_WEIGHT_LOOP;
                if (!isZeroWeightLoop) {
                    // we may not update the best path if we are dealing with a zero weight loop here, because when a
                    // zero weight loop updates the best path to be no longer a bridge path we cannot trust that there
                    // will be a shortcut leading to the zero weight loop in case there are multiple zero weight loops.
                    updateBestPath(targetNode, targetEdge, edgeKey);
                }
            }
        }

        // run dijkstra to find the optimal path
        while (!dijkstraHeap.isEmpty()) {
            if (numPathsToCenter < 1 && (!bestPathIsBridgePath || isInfinite(bestPathWeight))) {
                // we have not found a connection to the target edge yet and there are no entries on the heap anymore 
                // that could yield a bridge-path
                break;
            }
            final int currKey = dijkstraHeap.peekElement();
            if (weights[currKey] > bestPathWeight) {
                // just reaching this edge is more expensive than the best path found so far including the turn costs
                // to reach the target edge -> we can stop
                // important: we only peeked so far, so we keep the entry for future searches
                break;
            }
            dijkstraHeap.poll();
            numPolledEdges++;
            currentBatchStats.numPolledEdges++;
            totalStats.numPolledEdges++;

            if (isPathToCenter(currKey)) {
                numPathsToCenter--;
            }

            // after a certain amount of edges has been settled we only expand entries that might yield a bridge-path
            if (numSettledEdges > maxSettledEdges && !isPathToCenter(currKey)) {
                continue;
            }

            final int fromNode = getAdjNode(currKey);
            PrepareGraphEdgeIterator iter = outEdgeExplorer.setBaseNode(fromNode);
            while (iter.next()) {
                double edgeWeight = iter.getWeight() + calcTurnWeight(GHUtility.getEdgeFromEdgeKey(currKey),
                        iter.getBaseNode(), GHUtility.getEdgeFromEdgeKey(iter.getOrigEdgeKeyFirst()));
                double weight = edgeWeight + weights[currKey];
                if (isInfinite(weight)) {
                    continue;
                }
                boolean isPathToCenter = isPathToCenter(currKey) && iter.getAdjNode() == centerNode;
                boolean isZeroWeightLoop = fromNode == targetNode && edgeWeight <= MAX_ZERO_WEIGHT_LOOP;

                // dijkstra expansion: add or update current entries
                int key = iter.getOrigEdgeKeyLast();
                if (!EdgeIterator.Edge.isValid(prepareEdges[key])) {
                    setEntry(key, iter, weight, currKey, isPathToCenter);
                    changedEdges.add(key);
                    dijkstraHeap.insert(weight, key);
                    if (!isZeroWeightLoop) {
                        updateBestPath(targetNode, targetEdge, key);
                    }
                } else if (weight < weights[key]
                        // special case of a witness path with equal weight -> rather take this than the bridge path
                        || (weight == weights[key] && iter.getAdjNode() == targetNode && !isPathToCenter(currKey))) {
                    updateEntry(key, iter, weight, currKey, isPathToCenter);
                    dijkstraHeap.update(weight, key);
                    if (!isZeroWeightLoop) {
                        updateBestPath(targetNode, targetEdge, key);
                    }
                }
            }
            numSettledEdges++;
            currentBatchStats.numSettledEdges++;
            totalStats.numSettledEdges++;
            // do not keep searching after target node has been expanded first time, should speed up contraction a bit but
            // leads to less witnesses being found.
//            if (adjNodes[currKey] == targetNode) {
//                break;
//            }
        }

        if (bestPathIsBridgePath) {
            int edgeKey = bestPathIncKey;
            PrepareCHEntry result = getEntryForKey(edgeKey);
            // prepend all ancestors
            PrepareCHEntry entry = result;
            while (parents[edgeKey] >= 0) {
                edgeKey = parents[edgeKey];
                PrepareCHEntry parent = getEntryForKey(edgeKey);
                entry.parent = parent;
                entry = parent;
            }
            entry.parent = initialEntryParents.get(parents[edgeKey]);
            return result;
        } else {
            return null;
        }
    }

    private void setAdjNodeAndPathToCenter(int key, int adjNode, boolean isPathToCenter) {
        adjNodesAndIsPathToCenters[key] = (adjNode << 1) + (isPathToCenter ? 1 : 0);
    }

    private int getAdjNode(int key) {
        return (adjNodesAndIsPathToCenters[key] >> 1);
    }

    private void setPathToCenter(int key, boolean isPathToCenter) {
        if (isPathToCenter)
            adjNodesAndIsPathToCenters[key] |= 0b1;
        else
            adjNodesAndIsPathToCenters[key] &= ~0b1;
    }

    private boolean isPathToCenter(int key) {
        return (adjNodesAndIsPathToCenters[key] & 0b01) == 0b01;
    }

    public String getStatisticsString() {
        return "last batch: " + currentBatchStats.toString() + " total: " + totalStats.toString();
    }

    public long getNumPolledEdges() {
        return numPolledEdges;
    }


    public long getTotalNumSearches() {
        return totalStats.numSearches;
    }

    public void resetStats() {
        currentBatchStats.reset();
    }

    public void close() {
        prepareGraph.close();
        outEdgeExplorer = null;
        origInEdgeExplorer = null;
        weights = null;
        prepareEdges = null;
        parents = null;
        adjNodesAndIsPathToCenters = null;
        initialEntryParents = null;
        changedEdges.release();
        dijkstraHeap = null;
    }

    private void initStorage(int numEntries) {
        weights = new double[numEntries];
        Arrays.fill(weights, Double.POSITIVE_INFINITY);

        prepareEdges = new int[numEntries];
        Arrays.fill(prepareEdges, NO_EDGE);

        parents = new int[numEntries];
        Arrays.fill(parents, NO_NODE);

        adjNodesAndIsPathToCenters = new int[numEntries];
        // need bit shift, see getAdjNode(int)
        Arrays.fill(adjNodesAndIsPathToCenters, NO_NODE << 1);
    }

    private void initCollections() {
        initialEntryParents = new IntObjectHashMap<>(10);
        changedEdges = new IntArrayList(1000);
        dijkstraHeap = new IntFloatBinaryHeap(1000);
    }

    private void setInitialEntries(int sourceNode, int sourceEdge, int centerNode) {
        PrepareGraphEdgeIterator outIter = outEdgeExplorer.setBaseNode(sourceNode);
        while (outIter.next()) {
            double turnWeight = calcTurnWeight(sourceEdge, sourceNode, GHUtility.getEdgeFromEdgeKey(outIter.getOrigEdgeKeyFirst()));
            if (isInfinite(turnWeight)) {
                continue;
            }
            double edgeWeight = outIter.getWeight();
            double weight = turnWeight + edgeWeight;
            boolean isPathToCenter = outIter.getAdjNode() == centerNode;
            int key = outIter.getOrigEdgeKeyLast();
            int adjNode = outIter.getAdjNode();
            int parentKey = -key - 1;
            // note that we 'misuse' the parent also to store initial turncost and the first original edge key of this
            // initial entry
            PrepareCHEntry parent = new PrepareCHEntry(
                    NO_EDGE,
                    outIter.getOrigEdgeKeyFirst(),
                    sourceNode, turnWeight);
            if (!EdgeIterator.Edge.isValid(prepareEdges[key])) {
                // add new initial entry
                prepareEdges[key] = outIter.getPrepareEdge();
                weights[key] = weight;
                parents[key] = parentKey;
                setAdjNodeAndPathToCenter(key, adjNode, isPathToCenter);
                initialEntryParents.put(parentKey, parent);
                changedEdges.add(key);
            } else if (weight < weights[key]) {
                // update existing entry, there may be entries with the same adjNode and last original edge,
                // but we only need the one with the lowest weight
                prepareEdges[key] = outIter.getPrepareEdge();
                weights[key] = weight;
                parents[key] = parentKey;
                setPathToCenter(key, isPathToCenter);
                initialEntryParents.put(parentKey, parent);
            }
        }

        // now that we know which entries are actually needed we add them to the heap
        for (int i = 0; i < changedEdges.size(); ++i) {
            int key = changedEdges.get(i);
            if (isPathToCenter(key)) {
                numPathsToCenter++;
            }
            dijkstraHeap.insert(weights[key], key);
        }
    }

    private void reset() {
        updateMaxSettledEdges();
        numSettledEdges = 0;
        numPolledEdges = 0;
        numPathsToCenter = 0;
        resetShortestPathTree();
    }

    private void updateMaxSettledEdges() {
        // we use the statistics of settled edges of a batch of previous witness path searches to dynamically 
        // approximate the number of settled edges in the next batch
        settledEdgesStats.addObservation(numSettledEdges);
        if (settledEdgesStats.getCount() == params.settledEdgeStatsResetInterval) {
            maxSettledEdges = Math.max(
                    params.minimumMaxSettledEdges,
                    (int) (settledEdgesStats.getMean() +
                            params.sigmaFactor * Math.sqrt(settledEdgesStats.getVariance()))
            );
            settledEdgesStats.reset();
        }
    }

    private void resetShortestPathTree() {
        for (int i = 0; i < changedEdges.size(); ++i) {
            resetEntry(changedEdges.get(i));
        }
        changedEdges.elementsCount = 0;
        initialEntryParents.clear();
        dijkstraHeap.clear();
    }

    private void updateBestPath(int targetNode, int targetEdge, int edgeKey) {
        // whenever we hit the target node we update the best path *if* it allows turning onto the target edge
        // less costly than the currently best path
        if (getAdjNode(edgeKey) == targetNode) {
            double totalWeight = weights[edgeKey] + calcTurnWeight(GHUtility.getEdgeFromEdgeKey(edgeKey), targetNode, targetEdge);
            // there is a path to the target so we know that there must be some parent. therefore a negative parent key
            // means that the parent is a root parent (a parent of an initial entry) and we did not go via the center
            // node.
            boolean isBridgePath = parents[edgeKey] >= 0 && isPathToCenter(parents[edgeKey]);
            // in case of equal weights we always prefer a witness path over a bridge-path
            double tolerance = isBridgePath ? 0 : 1.e-6;
            if (totalWeight - tolerance < bestPathWeight) {
                bestPathWeight = totalWeight;
                bestPathIncKey = edgeKey;
                bestPathIsBridgePath = isBridgePath;
            }
        }
    }

    private void setEntry(int key, PrepareGraphEdgeIterator edge, double weight, int parent, boolean isPathToCenter) {
        prepareEdges[key] = edge.getPrepareEdge();
        weights[key] = weight;
        parents[key] = parent;
        setAdjNodeAndPathToCenter(key, edge.getAdjNode(), isPathToCenter);
        if (isPathToCenter)
            numPathsToCenter++;
    }

    private void updateEntry(int key, PrepareGraphEdgeIterator edge, double weight, int parent, boolean isPathToCenter) {
        prepareEdges[key] = edge.getPrepareEdge();
        weights[key] = weight;
        parents[key] = parent;
        if (isPathToCenter) {
            if (!isPathToCenter(key)) {
                numPathsToCenter++;
            }
        } else {
            if (isPathToCenter(key)) {
                numPathsToCenter--;
            }
        }
        setPathToCenter(key, isPathToCenter);
    }

    private void resetEntry(int key) {
        weights[key] = Double.POSITIVE_INFINITY;
        prepareEdges[key] = NO_EDGE;
        parents[key] = NO_NODE;
        setAdjNodeAndPathToCenter(key, NO_NODE, false);
    }

    private PrepareCHEntry getEntryForKey(int edgeKey) {
        return new PrepareCHEntry(prepareEdges[edgeKey], edgeKey, getAdjNode(edgeKey), weights[edgeKey]);
    }

    private double calcTurnWeight(int inEdge, int viaNode, int outEdge) {
        return prepareGraph.getTurnWeight(inEdge, viaNode, outEdge);
    }

    static class Params {
        /**
         * Determines the maximum number of settled edges for the next search based on the mean number of settled edges and
         * the fluctuation in the previous searches. The higher this number the longer the search will last and the more
         * witness paths will be found. Assuming a normal distribution for example sigmaFactor = 2 means that about 95% of
         * the searches will be within the limit.
         */
        private double sigmaFactor = 3.0;
        private int minimumMaxSettledEdges = 100;
        private int settledEdgeStatsResetInterval = 10_000;
    }

    static class Stats {
        private long numSearches;
        private long numPolledEdges;
        private long numSettledEdges;
        private long maxNumSettledEdges;

        @Override
        public String toString() {
            return String.format(Locale.ROOT,
                    "limit-exhaustion: %s %%, avg-settled: %s, avg-max-settled: %s, avg-polled-edges: %s",
                    quotient(numSettledEdges * 100, maxNumSettledEdges),
                    quotient(numSettledEdges, numSearches),
                    quotient(maxNumSettledEdges, numSearches),
                    quotient(numPolledEdges, numSearches));
        }

        private String quotient(long a, long b) {
            return b == 0 ? "NaN" : String.format(Locale.ROOT, "%5.1f", a / ((double) b));
        }

        void reset() {
            numSearches = 0;
            numPolledEdges = 0;
            numSettledEdges = 0;
            maxNumSettledEdges = 0;
        }
    }
}
