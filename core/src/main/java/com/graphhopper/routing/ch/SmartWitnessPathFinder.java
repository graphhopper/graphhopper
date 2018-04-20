package com.graphhopper.routing.ch;

import com.carrotsearch.hppc.IntObjectMap;
import com.carrotsearch.hppc.cursors.IntObjectCursor;
import com.graphhopper.coll.GHIntObjectHashMap;
import com.graphhopper.routing.util.DefaultEdgeFilter;
import com.graphhopper.routing.weighting.TurnWeighting;
import com.graphhopper.storage.CHGraph;
import com.graphhopper.storage.GraphHopperStorage;
import com.graphhopper.util.CHEdgeIteratorState;
import com.graphhopper.util.EdgeExplorer;
import com.graphhopper.util.EdgeIterator;
import com.graphhopper.util.GHUtility;

import java.util.Arrays;
import java.util.PriorityQueue;

import static java.lang.Double.isInfinite;

public class SmartWitnessPathFinder {
    // for very dense graph a higher initial value is probably appropriate, the initial value does not play a big role
    // because this parameter will be adjusted automatically during the graph contraction
    public static int initialMaxSettledEdges = 10;
    // number of standard deviations above mean where distribution is truncated, for a normal distribution for
    // example sigmaFactor = 2 means about 95% of all observations are included
    public static double sigmaFactor = 3;

    private final GraphHopperStorage graph;
    private final CHGraph chGraph;
    private final TurnWeighting turnWeighting;
    private final EdgeExplorer outEdgeExplorer;
    private final EdgeExplorer origInEdgeExplorer;
    private final int maxLevel;
    private final OnFlyStatisticsCalculator statisticsCalculator = new OnFlyStatisticsCalculator();
    private final Stats stats = new Stats();

    private IntObjectMap<SmartWitnessSearchEntry> entries;
    private PriorityQueue<SmartWitnessSearchEntry> priorityQueue;
    private int numViaCenter;
    private int numSettledEdges;
    private int numPolledEdges;
    private int maxSettledEdges = initialMaxSettledEdges;
    private int centerNode;
    private int fromNode;
    private int sourceEdge;


    public SmartWitnessPathFinder(GraphHopperStorage graph, CHGraph chGraph, TurnWeighting turnWeighting) {
        this.graph = graph;
        this.chGraph = chGraph;
        this.turnWeighting = turnWeighting;
        DefaultEdgeFilter inEdgeFilter = new DefaultEdgeFilter(turnWeighting.getFlagEncoder(), true, false);
        DefaultEdgeFilter outEdgeFilter = new DefaultEdgeFilter(turnWeighting.getFlagEncoder(), false, true);
        outEdgeExplorer = chGraph.createEdgeExplorer(outEdgeFilter);
        origInEdgeExplorer = graph.createEdgeExplorer(inEdgeFilter);
        maxLevel = chGraph.getNodes();
        reset();
    }

    public void init(int centerNode, int fromNode, int sourceEdge) {
        reset();
        this.sourceEdge = sourceEdge;
        this.fromNode = fromNode;
        this.centerNode = centerNode;
        setInitialEntries(centerNode, fromNode, sourceEdge);
        // if there is no entry that reaches the center node we can skip the entire search 
        // and do not need any start entries, because no shortcut will ever be required
        if (numViaCenter < 1) {
            reset();
        }
        stats.onInitEntries(entries.size());
    }

    public SmartWitnessSearchEntry runSearch(int toNode, int targetEdge) {
        // todo: write a test for this case where it becomes clear
        double bestWeight = fromNode == toNode
                ? calcTurnWeight(sourceEdge, fromNode, targetEdge)
                : Double.POSITIVE_INFINITY;
        SmartWitnessSearchEntry result = new SmartWitnessSearchEntry(
                EdgeIterator.NO_EDGE,
                EdgeIterator.NO_EDGE,
                toNode, bestWeight, false, false);

        // check if we can already reach the target from the shortest path tree we discovered so far
        EdgeIterator inIter = origInEdgeExplorer.setBaseNode(toNode);
        while (inIter.next()) {
            final int incEdge = inIter.getLastOrigEdge();
            final int edgeKey = getEdgeKey(incEdge, toNode);
            SmartWitnessSearchEntry entry = entries.get(edgeKey);
            if (entry == null) {
                continue;
            }
            double totalWeight = entry.weight + calcTurnWeight(incEdge, toNode, targetEdge);
            if (totalWeight < result.weight) {
                result.weight = totalWeight;
                result.edge = inIter.getEdge();
                result.incEdge = incEdge;
                result.onOrigPath = entry.onOrigPath;
                result.parent = entry.parent;
            }
        }

        // run dijkstra to find the optimal path
        while (!priorityQueue.isEmpty()) {
            if (numViaCenter < 1 && (!result.onOrigPath || isInfinite(result.weight))) {
                // we have not found a connection to the target edge yet and there are no entries
                // in the priority queue anymore that are part of the direct path via the center node
                // -> we will not need a shortcut
                break;
            }
            SmartWitnessSearchEntry entry = priorityQueue.peek();
            if (entry.weight > result.weight) {
                // just reaching this edge is more expensive than the best path found so far including the turn costs
                // to reach the targetOutEdge -> we can stop
                // important: we only peeked so far, so we keep the entry for future searches
                break;
            }
            priorityQueue.poll();
            numPolledEdges++;

            if (entry.viaCenter) {
                numViaCenter--;
            }

            // after a certain amount of edges has been settled we no longer expand entries
            // that are not on a path via the center node
            if (numSettledEdges > maxSettledEdges && !entry.viaCenter) {
                continue;
            }

            EdgeIterator iter = outEdgeExplorer.setBaseNode(entry.adjNode);
            while (iter.next()) {
                if (isContracted(iter.getAdjNode())) {
                    continue;
                }
                // do not allow u-turns
                if (iter.getFirstOrigEdge() == entry.incEdge) {
                    continue;
                }
                double weight = turnWeighting.calcWeight(iter, false, entry.incEdge) + entry.weight;
                if (isInfinite(weight)) {
                    continue;
                }
                boolean viaCenter = entry.viaCenter && iter.getAdjNode() == centerNode;
                boolean onOrigPath = entry.onOrigPath && iter.getBaseNode() == centerNode;

                // when we hit the target node we update the best path
                if (iter.getAdjNode() == toNode) {
                    double turnWeight = calcTurnWeight(iter.getLastOrigEdge(), toNode, targetEdge);
                    // when in doubt prefer a witness path over an original path
                    double tolerance = onOrigPath ? 0 : 1.e-6;
                    if (weight + turnWeight - tolerance < result.weight) {
                        result.weight = weight + turnWeight;
                        result.edge = iter.getEdge();
                        result.incEdge = iter.getLastOrigEdge();
                        result.onOrigPath = onOrigPath;
                        result.parent = entry;
                    }
                }

                // dijkstra expansion: add or update current entries
                int key = getEdgeKey(iter.getLastOrigEdge(), iter.getAdjNode());
                int index = entries.indexOf(key);
                if (index < 0) {
                    SmartWitnessSearchEntry newEntry = new SmartWitnessSearchEntry(
                            iter.getEdge(),
                            iter.getLastOrigEdge(),
                            iter.getAdjNode(),
                            weight,
                            onOrigPath,
                            viaCenter
                    );
                    newEntry.parent = entry;
                    if (viaCenter) {
                        numViaCenter++;
                    }
                    entries.indexInsert(index, key, newEntry);
                    priorityQueue.add(newEntry);
                } else {
                    SmartWitnessSearchEntry existingEntry = entries.indexGet(index);
                    if (weight < existingEntry.weight) {
                        priorityQueue.remove(existingEntry);
                        existingEntry.edge = iter.getEdge();
                        existingEntry.incEdge = iter.getLastOrigEdge();
                        existingEntry.weight = weight;
                        existingEntry.parent = entry;
                        entry.onOrigPath = onOrigPath;
                        if (viaCenter) {
                            if (!existingEntry.viaCenter) {
                                numViaCenter++;
                            }
                        } else {
                            if (existingEntry.viaCenter) {
                                numViaCenter--;
                            }
                        }
                        entry.viaCenter = viaCenter;
                        priorityQueue.add(existingEntry);
                    }
                }
            }
            numSettledEdges++;
        }

        if (result.onOrigPath) {
            // the best path we could find is an original path so we return it
            // (note that this path may contain loops at the center node)
            int edgeKey = getEdgeKey(result.incEdge, result.adjNode);
            return entries.get(edgeKey);
        } else {
            return null;
        }
    }

    public String getStatusString() {
        return stats.toString();
    }

    public void resetStats() {
        stats.reset();
    }


    private void setInitialEntries(int centerNode, int fromNode, int sourceEdge) {
        EdgeIterator outIter = outEdgeExplorer.setBaseNode(fromNode);
        while (outIter.next()) {
            if (isContracted(outIter.getAdjNode())) {
                continue;
            }
            double turnWeight = calcTurnWeight(sourceEdge, fromNode, outIter.getFirstOrigEdge());
            if (isInfinite(turnWeight)) {
                continue;
            }
            double weight = turnWeighting.calcWeight(outIter, false, EdgeIterator.NO_EDGE);
            boolean viaCenter = outIter.getAdjNode() == centerNode;
            SmartWitnessSearchEntry entry = new SmartWitnessSearchEntry(
                    outIter.getEdge(),
                    outIter.getLastOrigEdge(),
                    outIter.getAdjNode(), turnWeight + weight, viaCenter, viaCenter);
            entry.parent = new SmartWitnessSearchEntry(
                    EdgeIterator.NO_EDGE,
                    outIter.getFirstOrigEdge(),
                    fromNode, turnWeight, false, false);
            addOrUpdateInitialEntry(entry);
        }

        // now that we know which entries are actually needed we add them to the priority queue
        for (IntObjectCursor<SmartWitnessSearchEntry> e : entries) {
            if (e.value.viaCenter) {
                numViaCenter++;
            }
            priorityQueue.add(e.value);
        }
    }

    private void addOrUpdateInitialEntry(SmartWitnessSearchEntry entry) {
        int edgeKey = getEdgeKey(entry.incEdge, entry.adjNode);
        int index = entries.indexOf(edgeKey);
        if (index < 0) {
            entries.indexInsert(index, edgeKey, entry);
        } else {
            // there may be entries with the same adjNode and last original edge, but we only need the one with
            // the lowest weight
            SmartWitnessSearchEntry currEntry = entries.indexGet(index);
            if (entry.weight < currEntry.weight) {
                entries.indexReplace(index, entry);
            }
        }
    }

    private void reset() {
        readjustMaxSettledEdges();
        stats.onReset(numSettledEdges, maxSettledEdges);
        numSettledEdges = 0;
        numViaCenter = 0;
        initCollections();
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

    private void initCollections() {
        // todo: tune initial collection sizes
        int size = Math.min(Math.max(200, graph.getNodes() / 10), 2000);
        priorityQueue = new PriorityQueue<>(size);
        entries = new GHIntObjectHashMap<>(size);
    }

    private int getEdgeKey(int edge, int adjNode) {
        // todo: this is similar to some code in DijkstraBidirectionEdgeCHNoSOD and should be cleaned up, see comments there
        CHEdgeIteratorState eis = chGraph.getEdgeIteratorState(edge, adjNode);
        return GHUtility.createEdgeKey(eis.getBaseNode(), eis.getAdjNode(), eis.getEdge(), false);
    }

    private double calcTurnWeight(int inEdge, int viaNode, int outEdge) {
        if (inEdge == outEdge) {
            return Double.POSITIVE_INFINITY;
        }
        return turnWeighting.calcTurnWeight(inEdge, viaNode, outEdge);
    }

    private boolean isContracted(int node) {
        return chGraph.getLevel(node) != maxLevel;
    }

    private static class Stats {
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
