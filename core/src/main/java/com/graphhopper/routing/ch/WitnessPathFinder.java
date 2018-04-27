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

import com.graphhopper.routing.util.DefaultEdgeFilter;
import com.graphhopper.routing.weighting.TurnWeighting;
import com.graphhopper.storage.CHGraph;
import com.graphhopper.storage.GraphHopperStorage;
import com.graphhopper.util.CHEdgeIteratorState;
import com.graphhopper.util.EdgeExplorer;
import com.graphhopper.util.GHUtility;

import java.util.Arrays;

public abstract class WitnessPathFinder {
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
    protected final OnFlyStatisticsCalculator statisticsCalculator = new OnFlyStatisticsCalculator();
    protected final Stats stats = new Stats();

    protected int numOnOrigPath;
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

    public WitnessPathFinder(GraphHopperStorage graph, CHGraph chGraph, TurnWeighting turnWeighting) {
        this.graph = graph;
        this.chGraph = chGraph;
        this.turnWeighting = turnWeighting;
        DefaultEdgeFilter inEdgeFilter = new DefaultEdgeFilter(turnWeighting.getFlagEncoder(), true, false);
        DefaultEdgeFilter outEdgeFilter = new DefaultEdgeFilter(turnWeighting.getFlagEncoder(), false, true);
        outEdgeExplorer = chGraph.createEdgeExplorer(outEdgeFilter);
        origInEdgeExplorer = graph.createEdgeExplorer(inEdgeFilter);
        maxLevel = chGraph.getNodes();
    }

    public int init(int centerNode, int fromNode, int sourceEdge) {
        reset();
        this.sourceEdge = sourceEdge;
        this.fromNode = fromNode;
        this.centerNode = centerNode;
        setInitialEntries(centerNode, fromNode, sourceEdge);
        // if there is no entry that reaches the center node we can skip the entire search 
        // and do not need any start entries, because no shortcut will ever be required
        if (numOnOrigPath < 1) {
            reset();
            return 0;
        }
        searchCount++;
        int numEntries = getNumEntries();
        stats.onInitEntries(numEntries);
        return numEntries;
    }

    public abstract WitnessSearchEntry runSearch(int toNode, int targetEdge);

    public int getNumPolledEdges() {
        return numPolledEdges;
    }

    public String getStatusString() {
        return stats.toString();
    }

    public void resetStats() {
        stats.reset();
    }

    abstract void setInitialEntries(int centerNode, int fromNode, int sourceEdge);

    abstract void doReset();

    abstract int getNumEntries();

    private void reset() {
        readjustMaxSettledEdges();
        stats.onReset(numSettledEdges, maxSettledEdges);
        numSettledEdges = 0;
        numPolledEdges = 0;
        numOnOrigPath = 0;
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
        // todo: this is similar to some code in DijkstraBidirectionEdgeCHNoSOD and should be cleaned up, see comments there
        CHEdgeIteratorState eis = chGraph.getEdgeIteratorState(edge, adjNode);
        return GHUtility.createEdgeKey(eis.getBaseNode(), eis.getAdjNode(), eis.getEdge(), false);
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
