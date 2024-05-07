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

import com.carrotsearch.hppc.IntContainer;
import com.carrotsearch.hppc.cursors.IntCursor;
import com.graphhopper.coll.MinHeapWithUpdate;
import com.graphhopper.routing.util.TraversalMode;
import com.graphhopper.storage.*;
import com.graphhopper.util.Helper;
import com.graphhopper.util.PMap;
import com.graphhopper.util.StopWatch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Locale;
import java.util.Random;

import static com.graphhopper.routing.ch.CHParameters.*;
import static com.graphhopper.util.Helper.getMemInfo;
import static com.graphhopper.util.Helper.nf;

/**
 * This class prepares the graph for a bidirectional algorithm supporting contraction hierarchies
 * ie. an algorithm returned by createAlgo.
 * <p>
 * There are several descriptions of contraction hierarchies available. The following is one of the
 * more detailed: http://web.cs.du.edu/~sturtevant/papers/highlevelpathfinding.pdf
 * <p>
 * The only difference is that we use two skipped edges instead of one skipped node for faster
 * unpacking.
 * <p>
 *
 * @author Peter Karich
 */
public class PrepareContractionHierarchies {
    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final CHConfig chConfig;
    private final CHStorage chStore;
    private final CHStorageBuilder chBuilder;
    private final Random rand = new Random(123);
    private final StopWatch allSW = new StopWatch();
    private final StopWatch periodicUpdateSW = new StopWatch();
    private final StopWatch lazyUpdateSW = new StopWatch();
    private final StopWatch neighborUpdateSW = new StopWatch();
    private final StopWatch contractionSW = new StopWatch();
    private final Params params;
    private final BaseGraph graph;
    private NodeContractor nodeContractor;
    private final int nodes;
    private NodeOrderingProvider nodeOrderingProvider;
    private int maxLevel;
    // nodes with highest priority come last
    private MinHeapWithUpdate sortedNodes;
    private PMap pMap = new PMap();
    private int checkCounter;
    private boolean prepared = false;

    public static PrepareContractionHierarchies fromGraph(BaseGraph graph, CHConfig chConfig) {
        return new PrepareContractionHierarchies(graph.getBaseGraph(), chConfig);
    }

    private PrepareContractionHierarchies(BaseGraph graph, CHConfig chConfig) {
        if (!graph.isFrozen())
            throw new IllegalStateException("BaseGraph must be frozen before creating CHs");
        this.graph = graph;
        chStore = CHStorage.fromGraph(graph, chConfig);
        chBuilder = new CHStorageBuilder(chStore);
        this.chConfig = chConfig;
        params = Params.forTraversalMode(chConfig.getTraversalMode());
        nodes = graph.getNodes();
        if (chConfig.getTraversalMode().isEdgeBased()) {
            TurnCostStorage turnCostStorage = graph.getTurnCostStorage();
            if (turnCostStorage == null) {
                throw new IllegalArgumentException("For edge-based CH you need a turn cost storage");
            }
        }
    }

    public PrepareContractionHierarchies setParams(PMap pMap) {
        this.pMap = pMap;
        params.setPeriodicUpdatesPercentage(pMap.getInt(PERIODIC_UPDATES, params.getPeriodicUpdatesPercentage()));
        params.setLastNodesLazyUpdatePercentage(pMap.getInt(LAST_LAZY_NODES_UPDATES, params.getLastNodesLazyUpdatePercentage()));
        params.setNeighborUpdatePercentage(pMap.getInt(NEIGHBOR_UPDATES, params.getNeighborUpdatePercentage()));
        params.setMaxNeighborUpdates(pMap.getInt(NEIGHBOR_UPDATES_MAX, params.getMaxNeighborUpdates()));
        params.setNodesContractedPercentage(pMap.getInt(CONTRACTED_NODES, params.getNodesContractedPercentage()));
        params.setLogMessagesPercentage(pMap.getInt(LOG_MESSAGES, params.getLogMessagesPercentage()));
        return this;
    }

    /**
     * Instead of heuristically determining a node ordering for the graph contraction it is also possible
     * to use a fixed ordering. For example this allows re-using a previously calculated node ordering.
     * This will speed up CH preparation, but might lead to slower queries.
     */
    public PrepareContractionHierarchies useFixedNodeOrdering(NodeOrderingProvider nodeOrderingProvider) {
        if (nodeOrderingProvider.getNumNodes() != nodes) {
            throw new IllegalArgumentException(
                    "contraction order size (" + nodeOrderingProvider.getNumNodes() + ")" +
                            " must be equal to number of nodes in graph (" + nodes + ").");
        }
        this.nodeOrderingProvider = nodeOrderingProvider;
        return this;
    }

    public Result doWork() {
        if (prepared)
            throw new IllegalStateException("Call doWork only once!");
        prepared = true;
        if (!graph.isFrozen()) {
            throw new IllegalStateException("Given BaseGraph has not been frozen yet");
        }
        if (chStore.getShortcuts() > 0) {
            throw new IllegalStateException("Given CHStore already contains shortcuts");
        }
        allSW.start();
        initFromGraph();
        runGraphContraction();
        allSW.stop();
        logFinalGraphStats();
        return new Result(
                chConfig, chStore,
                nodeContractor.getAddedShortcutsCount(),
                lazyUpdateSW.getCurrentSeconds(),
                periodicUpdateSW.getCurrentSeconds(),
                neighborUpdateSW.getCurrentSeconds(),
                allSW.getMillis()
        );
    }

    public boolean isPrepared() {
        return prepared;
    }

    private void logFinalGraphStats() {
        logger.info("shortcuts that exceed maximum weight: {}", chStore.getNumShortcutsExceedingWeight());
        logger.info("took: {}s, graph now - num edges: {}, num nodes: {}, num shortcuts: {}",
                (int) allSW.getSeconds(), nf(graph.getEdges()), nf(nodes), nf(chStore.getShortcuts()));
    }

    private void runGraphContraction() {
        if (nodes < 1)
            return;
        setMaxLevelOnAllNodes();
        if (nodeOrderingProvider != null) {
            contractNodesUsingFixedNodeOrdering();
        } else {
            contractNodesUsingHeuristicNodeOrdering();
        }
    }

    private boolean isEdgeBased() {
        return chConfig.isEdgeBased();
    }

    private void initFromGraph() {
        // todo: this whole chain of initFromGraph() methods is just needed because PrepareContractionHierarchies does
        // not simply prepare contraction hierarchies, but instead it also serves as some kind of 'container' to give
        // access to the preparations in the GraphHopper class. If this was not so we could make this a lot cleaner here,
        // declare variables final and would not need all these close() methods...
        CHPreparationGraph prepareGraph;
        if (chConfig.getTraversalMode().isEdgeBased()) {
            TurnCostStorage turnCostStorage = graph.getTurnCostStorage();
            if (turnCostStorage == null) {
                throw new IllegalArgumentException("For edge-based CH you need a turn cost storage");
            }
            logger.info("Creating CH prepare graph, {}", getMemInfo());
            CHPreparationGraph.TurnCostFunction turnCostFunction = CHPreparationGraph.buildTurnCostFunctionFromTurnCostStorage(graph, chConfig.getWeighting());
            prepareGraph = CHPreparationGraph.edgeBased(graph.getNodes(), graph.getEdges(), turnCostFunction);
            nodeContractor = new EdgeBasedNodeContractor(prepareGraph, chBuilder, pMap);
        } else {
            logger.info("Creating CH prepare graph, {}", getMemInfo());
            prepareGraph = CHPreparationGraph.nodeBased(graph.getNodes(), graph.getEdges());
            nodeContractor = new NodeBasedNodeContractor(prepareGraph, chBuilder, pMap);
        }
        maxLevel = nodes;
        // we need a memory-efficient priority queue with an efficient update method
        // TreeMap is not memory-efficient and PriorityQueue does not support an efficient update method
        // (and is not memory efficient either)
        sortedNodes = new MinHeapWithUpdate(prepareGraph.getNodes());
        logger.info("Building CH prepare graph, {}", getMemInfo());
        StopWatch sw = new StopWatch().start();
        CHPreparationGraph.buildFromGraph(prepareGraph, graph, chConfig.getWeighting());
        logger.info("Finished building CH prepare graph, took: {}s, {}", sw.stop().getSeconds(), getMemInfo());
        nodeContractor.initFromGraph();
    }

    private void setMaxLevelOnAllNodes() {
        chBuilder.setLevelForAllNodes(maxLevel);
    }

    private void updatePrioritiesOfRemainingNodes() {
        periodicUpdateSW.start();
        sortedNodes.clear();
        for (int node = 0; node < nodes; node++) {
            if (isContracted(node))
                continue;
            float priority = calculatePriority(node);
            sortedNodes.push(node, priority);
        }
        periodicUpdateSW.stop();
    }

    private void contractNodesUsingHeuristicNodeOrdering() {
        StopWatch sw = new StopWatch().start();
        logger.info("Building initial queue of nodes to be contracted: {} nodes, {}", nodes, getMemInfo());
        // note that we update the priorities before preparing the node contractor. this does not make much sense,
        // but has always been like that and changing it would possibly require retuning the contraction parameters
        updatePrioritiesOfRemainingNodes();
        logger.info("Finished building queue, took: {}s, {}", sw.stop().getSeconds(), getMemInfo());
        final int initSize = sortedNodes.size();
        int level = 0;
        checkCounter = 0;
        final long logSize = params.getLogMessagesPercentage() == 0
                ? Long.MAX_VALUE
                : Math.round(Math.max(10, initSize * (params.getLogMessagesPercentage() / 100d)));

        // specifies after how many contracted nodes the queue of remaining nodes is rebuilt. this takes time but the
        // more often we do this the more up-to-date the node priorities will be
        // todo: instead of using a fixed interval size maybe try adjusting it depending on the number of remaining
        // nodes ?
        final long periodicUpdatesCount = params.getPeriodicUpdatesPercentage() == 0
                ? Long.MAX_VALUE
                : Math.round(Math.max(10, initSize * (params.getPeriodicUpdatesPercentage() / 100d)));
        int updateCounter = 0;

        // enable lazy updates for last x percentage of nodes. lazy updates make preparation slower but potentially
        // keep node priorities more up to date, possibly resulting in a better preparation.
        final long lastNodesLazyUpdates = Math.round(initSize * (params.getLastNodesLazyUpdatePercentage() / 100d));

        // according to paper "Polynomial-time Construction of Contraction Hierarchies for Multi-criteria Objectives" by Funke and Storandt
        // we don't need to wait for all nodes to be contracted
        final long nodesToAvoidContract = Math.round(initSize * ((100 - params.getNodesContractedPercentage()) / 100d));

        // Recompute priority of (the given percentage of) uncontracted neighbors. Doing neighbor updates takes additional
        // time during preparation but keeps node priorities more up to date. this potentially improves query time and
        // reduces number of shortcuts.
        final boolean neighborUpdate = (params.getNeighborUpdatePercentage() != 0);

        while (!sortedNodes.isEmpty()) {
            stopIfInterrupted();
            // periodically update priorities of ALL nodes
            if (checkCounter > 0 && checkCounter % periodicUpdatesCount == 0) {
                updatePrioritiesOfRemainingNodes();
                updateCounter++;
                if (sortedNodes.isEmpty())
                    throw new IllegalStateException("Cannot prepare as no unprepared nodes where found. Called preparation twice?");
            }

            if (checkCounter % logSize == 0) {
                logHeuristicStats(updateCounter);
            }

            checkCounter++;
            int polledNode = sortedNodes.poll();

            if (!sortedNodes.isEmpty() && sortedNodes.size() < lastNodesLazyUpdates) {
                lazyUpdateSW.start();
                float priority = calculatePriority(polledNode);
                if (priority > sortedNodes.peekValue()) {
                    // current node got more important => insert as new value and contract it later
                    sortedNodes.push(polledNode, priority);
                    lazyUpdateSW.stop();
                    continue;
                }
                lazyUpdateSW.stop();
            }

            // contract node v!
            IntContainer neighbors = contractNode(polledNode, level);
            level++;

            if (sortedNodes.size() < nodesToAvoidContract)
                // skipped nodes are already set to maxLevel
                break;

            int neighborCount = 0;
            // there might be multiple edges going to the same neighbor nodes -> only calculate priority once per node
            for (IntCursor neighbor : neighbors) {
                if (neighborUpdate && (params.getMaxNeighborUpdates() < 0 || neighborCount < params.getMaxNeighborUpdates()) && rand.nextInt(100) < params.getNeighborUpdatePercentage()) {
                    neighborCount++;
                    neighborUpdateSW.start();
                    float priority = calculatePriority(neighbor.value);
                    sortedNodes.update(neighbor.value, priority);
                    neighborUpdateSW.stop();
                }
            }
        }

        nodeContractor.finishContraction();

        logHeuristicStats(updateCounter);

        logger.info(
                "new shortcuts: " + nf(nodeContractor.getAddedShortcutsCount())
                        + ", initSize:" + nf(initSize)
                        + ", " + chConfig.getWeighting()
                        + ", periodic:" + params.getPeriodicUpdatesPercentage()
                        + ", lazy:" + params.getLastNodesLazyUpdatePercentage()
                        + ", neighbor:" + params.getNeighborUpdatePercentage()
                        + ", " + getTimesAsString()
                        + ", lazy-overhead: " + (int) (100 * ((checkCounter / (double) initSize) - 1)) + "%"
                        + ", " + Helper.getMemInfo());

        // Preparation works only once so we can release temporary data.
        // The preparation object itself has to be intact to create the algorithm.
        _close();
    }

    private void contractNodesUsingFixedNodeOrdering() {
        final int nodesToContract = nodeOrderingProvider.getNumNodes();
        final int logSize = Math.max(10, (int) (params.getLogMessagesPercentage() / 100.0 * nodesToContract));
        StopWatch stopWatch = new StopWatch();
        stopWatch.start();
        for (int i = 0; i < nodesToContract; ++i) {
            stopIfInterrupted();
            int node = nodeOrderingProvider.getNodeIdForLevel(i);
            contractNode(node, i);
            if (i % logSize == 0) {
                stopWatch.stop();
                logFixedNodeOrderingStats(i, logSize, stopWatch);
                stopWatch.start();
            }
        }
        nodeContractor.finishContraction();
    }

    private void stopIfInterrupted() {
        if (Thread.currentThread().isInterrupted()) {
            throw new RuntimeException("Thread was interrupted");
        }
    }

    private IntContainer contractNode(int node, int level) {
        if (isContracted(node))
            throw new IllegalArgumentException("Node " + node + " was contracted already");
        contractionSW.start();
        chBuilder.setLevel(node, level);
        IntContainer neighbors = nodeContractor.contractNode(node);
        contractionSW.stop();
        return neighbors;
    }

    private boolean isContracted(int node) {
        return chStore.getLevel(chStore.toNodePointer(node)) != maxLevel;
    }

    private void logHeuristicStats(int updateCounter) {
        logger.info(String.format(Locale.ROOT,
                "%s, nodes: %10s, shortcuts: %10s, updates: %2d, checked-nodes: %10s, %s, %s, %s",
                (isEdgeBased() ? "edge" : "node"),
                nf(sortedNodes.size()),
                nf(nodeContractor.getAddedShortcutsCount()),
                updateCounter,
                nf(checkCounter),
                getTimesAsString(),
                nodeContractor.getStatisticsString(),
                Helper.getMemInfo()));
    }

    private void logFixedNodeOrderingStats(int nodesContracted, int logSize, StopWatch stopWatch) {
        logger.info(String.format(Locale.ROOT,
                "nodes: %10s / %10s (%6.2f%%), shortcuts: %10s, speed = %6.2f nodes/ms, %s, %s",
                nf(nodesContracted),
                nf(nodes),
                (100.0 * nodesContracted / nodes),
                nf(nodeContractor.getAddedShortcutsCount()),
                nodesContracted == 0 ? 0 : logSize / (double) stopWatch.getMillis(),
                nodeContractor.getStatisticsString(),
                Helper.getMemInfo())
        );
    }

    public CHConfig getCHConfig() {
        return chConfig;
    }

    private String getTimesAsString() {
        float totalTime = allSW.getCurrentSeconds();
        float periodicUpdateTime = periodicUpdateSW.getCurrentSeconds();
        float lazyUpdateTime = lazyUpdateSW.getCurrentSeconds();
        float neighborUpdateTime = neighborUpdateSW.getCurrentSeconds();
        float contractionTime = contractionSW.getCurrentSeconds();
        float otherTime = totalTime - (periodicUpdateTime + lazyUpdateTime + neighborUpdateTime + contractionTime);
        // dijkstra time is included in the others
        float dijkstraTime = nodeContractor.getDijkstraSeconds();
        return String.format(Locale.ROOT,
                "t(total): %6.2f,  t(period): %6.2f, t(lazy): %6.2f, t(neighbor): %6.2f, t(contr): %6.2f, t(other) : %6.2f, dijkstra-ratio: %6.2f%%",
                totalTime, periodicUpdateTime, lazyUpdateTime, neighborUpdateTime, contractionTime, otherTime, dijkstraTime / totalTime * 100);
    }

    public long getTotalPrepareTime() {
        return allSW.getMillis();
    }

    private float calculatePriority(int node) {
        if (isContracted(node))
            throw new IllegalArgumentException("Priority should only be calculated for not yet contracted nodes");
        return nodeContractor.calculatePriority(node);
    }

    @Override
    public String toString() {
        return chConfig.isEdgeBased() ? "prepare|dijkstrabi|edge|ch" : "prepare|dijkstrabi|ch";
    }

    private void _close() {
        nodeContractor.close();
        sortedNodes = null;
    }

    void flush() {
        chStore.flush();
    }

    void close() {
        chStore.close();
    }

    public static class Result {
        private final CHConfig chConfig;
        private final CHStorage chStorage;
        private final long shortcuts;
        private final double lazyTime;
        private final double periodTime;
        private final double neighborTime;
        private final long totalPrepareTime;

        private Result(CHConfig chConfig, CHStorage chStorage, long shortcuts, double lazyTime, double periodTime, double neighborTime, long totalPrepareTime) {
            this.chStorage = chStorage;
            this.shortcuts = shortcuts;
            this.lazyTime = lazyTime;
            this.periodTime = periodTime;
            this.neighborTime = neighborTime;
            this.totalPrepareTime = totalPrepareTime;
            this.chConfig = chConfig;
        }

        public CHConfig getCHConfig() {
            return chConfig;
        }

        public CHStorage getCHStorage() {
            return chStorage;
        }

        public long getShortcuts() {
            return shortcuts;
        }

        public double getLazyTime() {
            return lazyTime;
        }

        public double getPeriodTime() {
            return periodTime;
        }

        public double getNeighborTime() {
            return neighborTime;
        }

        public long getTotalPrepareTime() {
            return totalPrepareTime;
        }
    }

    private static class Params {
        /**
         * Specifies after how many contracted nodes a full refresh of the queue of remaining/not contracted nodes
         * is performed. For example for a graph with 1000 nodes a value of 20 means that a full refresh is performed
         * after every 200 nodes (20% of the number of nodes of the graph). The more of these updates are performed
         * the longer the preparation will take, but the more up-to-date the node priorities will be. Higher values
         * here mean fewer updates!
         */
        private int periodicUpdatesPercentage;
        /**
         * Specifies the fraction of nodes for which lazy updates will be performed. For example a value of 20 means
         * that lazy updates will be performed for the last 20% of all nodes. A value of 100 means lazy updates will
         * be performed for all nodes. Higher values here lead to a longer preparation time, but the node priorities
         * will be more up-to-date (potentially leading to a better preparation (less shortcuts/faster queries)).
         */
        private int lastNodesLazyUpdatePercentage;
        /**
         * Specifies the probability that the priority of a given neighbor of a contracted node will be updated after
         * the node was contracted. For example a value of 20 means that on average 20% of the neighbor nodes will be
         * updated / each neighbor will be updated with a chance of 20%. Higher values here lead to longer preparation
         * times, but the node priorities will be more up-to-date.
         */
        private int neighborUpdatePercentage;
        /**
         * Specifies the maximum number of neighbor updates per contracted node. For example for the foot profile we
         * see a large number of neighbor updates that can be limited with this setting. -1 means unlimited.
         */
        private int maxNeighborUpdates;
        /**
         * Defines how many nodes (percentage) should be contracted. A value of 20 means only the first 20% of all nodes
         * will be contracted. Higher values here mean longer preparation times, but faster queries (because the
         * graph will be fully contracted).
         */
        private int nodesContractedPercentage;
        /**
         * Specifies how often a log message should be printed.
         *
         * @see #periodicUpdatesPercentage
         */
        private int logMessagesPercentage;

        static Params forTraversalMode(TraversalMode traversalMode) {
            // Lower values for the neighbor update percentage (and/or max neighbor updates) yield a slower
            // preparation but possibly fewer shortcuts and a slightly better query time.
            if (traversalMode.isEdgeBased()) {
                return new Params(0, 100, 50, 3, 100, 5);
            } else {
                return new Params(0, 100, 100, 2, 100, 20);
            }
        }

        private Params(int periodicUpdatesPercentage, int lastNodesLazyUpdatePercentage, int neighborUpdatePercentage, int maxNeighborUpdates,
                       int nodesContractedPercentage, int logMessagesPercentage) {
            setPeriodicUpdatesPercentage(periodicUpdatesPercentage);
            setLastNodesLazyUpdatePercentage(lastNodesLazyUpdatePercentage);
            setNeighborUpdatePercentage(neighborUpdatePercentage);
            setMaxNeighborUpdates(maxNeighborUpdates);
            setNodesContractedPercentage(nodesContractedPercentage);
            setLogMessagesPercentage(logMessagesPercentage);
        }

        int getPeriodicUpdatesPercentage() {
            return periodicUpdatesPercentage;
        }

        void setPeriodicUpdatesPercentage(int periodicUpdatesPercentage) {
            checkPercentage(PERIODIC_UPDATES, periodicUpdatesPercentage);
            this.periodicUpdatesPercentage = periodicUpdatesPercentage;
        }

        int getLastNodesLazyUpdatePercentage() {
            return lastNodesLazyUpdatePercentage;
        }

        void setLastNodesLazyUpdatePercentage(int lastNodesLazyUpdatePercentage) {
            checkPercentage(LAST_LAZY_NODES_UPDATES, lastNodesLazyUpdatePercentage);
            this.lastNodesLazyUpdatePercentage = lastNodesLazyUpdatePercentage;
        }

        int getNeighborUpdatePercentage() {
            return neighborUpdatePercentage;
        }

        void setNeighborUpdatePercentage(int neighborUpdatePercentage) {
            checkPercentage(NEIGHBOR_UPDATES, neighborUpdatePercentage);
            this.neighborUpdatePercentage = neighborUpdatePercentage;
        }

        int getMaxNeighborUpdates() {
            return maxNeighborUpdates;
        }

        void setMaxNeighborUpdates(int maxNeighborUpdates) {
            this.maxNeighborUpdates = maxNeighborUpdates;
        }

        int getNodesContractedPercentage() {
            return nodesContractedPercentage;
        }

        void setNodesContractedPercentage(int nodesContractedPercentage) {
            checkPercentage(CONTRACTED_NODES, nodesContractedPercentage);
            this.nodesContractedPercentage = nodesContractedPercentage;
        }

        int getLogMessagesPercentage() {
            return logMessagesPercentage;
        }

        void setLogMessagesPercentage(int logMessagesPercentage) {
            checkPercentage(LOG_MESSAGES, logMessagesPercentage);
            this.logMessagesPercentage = logMessagesPercentage;
        }

        private void checkPercentage(String name, int value) {
            if (value < 0 || value > 100) {
                throw new IllegalArgumentException(name + " has to be in [0, 100], to disable it use 0");
            }
        }
    }
}
