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

import com.carrotsearch.hppc.IntObjectMap;
import com.graphhopper.routing.util.DefaultEdgeFilter;
import com.graphhopper.routing.util.TraversalMode;
import com.graphhopper.routing.weighting.Weighting;
import com.graphhopper.storage.CHGraph;
import com.graphhopper.util.EdgeExplorer;
import com.graphhopper.util.EdgeIteratorState;
import com.graphhopper.util.GHUtility;

import java.util.Arrays;

public abstract class LegacyWitnessPathFinder {
    // for very dense graph a higher initial value is probably appropriate, the initial value does not play a big role
    // because this parameter will be adjusted automatically during the graph contraction
    public static int initialMaxSettledEdges = 10;
    // number of standard deviations above mean where distribution is truncated, for a normal distribution for
    // example sigmaFactor = 2 means about 95% of all observations are included
    public static double sigmaFactor = 3;
    protected final CHGraph graph;
    protected final Weighting weighting;
    protected final TraversalMode traversalMode;
    protected final int maxLevel;
    protected final EdgeExplorer outEdgeExplorer;
    protected int numOnOrigPath;
    protected int avoidNode = Integer.MAX_VALUE;
    protected int maxSettledEdges = initialMaxSettledEdges;
    protected int numSettledEdges;
    protected int numEntriesPolled;
    private final OnFlyStatisticsCalculator statisticsCalculator = new OnFlyStatisticsCalculator();
    private final Stats stats = new Stats();
    public static int searchCount = 0;
    public static int pollCount = 0;

    public LegacyWitnessPathFinder(CHGraph graph, Weighting weighting, TraversalMode traversalMode, int maxLevel) {
        if (traversalMode != TraversalMode.EDGE_BASED_2DIR) {
            throw new IllegalArgumentException("Traversal mode " + traversalMode + "not supported");
        }
        this.graph = graph;
        this.weighting = weighting;
        this.traversalMode = traversalMode;
        this.maxLevel = maxLevel;
        outEdgeExplorer = graph.createEdgeExplorer(new DefaultEdgeFilter(weighting.getFlagEncoder(), false, true));
    }

    public void setInitialEntries(IntObjectMap<WitnessSearchEntry> initialEntries) {
        searchCount++;
        reset();
        initEntries(initialEntries);
        stats.onInitEntries(initialEntries.size());
        if (numOnOrigPath != 1) {
            throw new IllegalStateException("There should be exactly one initial entry with onOrigPath = true, but given: " + numOnOrigPath);
        }
    }

    protected abstract void initEntries(IntObjectMap<WitnessSearchEntry> initialEntries);

    public abstract WitnessSearchEntry getFoundEntry(int edge, int adjNode);

    public abstract WitnessSearchEntry getFoundEntryNoParents(int edge, int adjNode);

    public abstract void findTarget(int targetEdge, int targetNode);

    public String getStatusString() {
        return stats.toString();
    }

    public void resetStats() {
        stats.reset();
    }

    public int getNumEntriesPolled() {
        return numEntriesPolled;
    }

    private void reset() {
        readjustMaxSettledEdges();
        stats.onReset(numSettledEdges, maxSettledEdges);
        numSettledEdges = 0;
        numEntriesPolled = 0;
        numOnOrigPath = 0;
        avoidNode = Integer.MAX_VALUE;
        doReset();
    }

    private void readjustMaxSettledEdges() {
        // we use the statistics of settled edges of a batch of previous witness path searches to dynamically 
        // approximate the number of settled edges in the next batch
        statisticsCalculator.addObservation(numSettledEdges);
        if (statisticsCalculator.getCount() == 10_000) {
            maxSettledEdges = Math.max(initialMaxSettledEdges, (int) (statisticsCalculator.getMean() + sigmaFactor * Math.sqrt(statisticsCalculator.getVariance())));
            stats.onStatCalcReset(statisticsCalculator);
            statisticsCalculator.reset();
        }
    }

    protected abstract void doReset();

    protected int getEdgeKey(int edge, int adjNode) {
        // todo: we should check if calculating the edge key this way affects performance, this method is probably run
        // millions of times
        // todo: this is similar to some code in DijkstraBidirectionEdgeCHNoSOD and should be cleaned up, see comments there
        EdgeIteratorState eis = graph.getEdgeIteratorState(edge, adjNode);
        return GHUtility.createEdgeKey(eis.getBaseNode(), eis.getAdjNode(), eis.getEdge(), false);
    }

    protected boolean isContracted(int node) {
        return graph.getLevel(node) != maxLevel;
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

        public void onInitEntries(int numInitialEntries) {
            totalNumInitialEntries += numInitialEntries;
        }

        public void onReset(int numSettledEdges, int maxSettledEdges) {
            int bucket = numSettledEdges / 10;
            if (bucket >= settledEdgesStats.length) {
                bucket = settledEdgesStats.length - 1;
            }

            settledEdgesStats[bucket]++;
            totalNumResets++;
            totalNumSettledEdges += numSettledEdges;
            totalMaxSettledEdges += maxSettledEdges;
        }

        public void onStatCalcReset(OnFlyStatisticsCalculator statisticsCalculator) {
            totalNumStatCalcResets++;
            totalMeanSettledEdges += statisticsCalculator.getMean();
            totalStdDeviationSettledEdges += (long) Math.sqrt(statisticsCalculator.getVariance());
        }
    }
}
