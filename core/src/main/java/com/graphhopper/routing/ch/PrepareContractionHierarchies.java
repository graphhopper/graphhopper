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

import com.carrotsearch.hppc.IntHashSet;
import com.carrotsearch.hppc.IntSet;
import com.graphhopper.coll.GHTreeMapComposed;
import com.graphhopper.routing.*;
import com.graphhopper.routing.util.AbstractAlgoPreparation;
import com.graphhopper.routing.util.LevelEdgeFilter;
import com.graphhopper.routing.util.TraversalMode;
import com.graphhopper.routing.weighting.TurnWeighting;
import com.graphhopper.routing.weighting.Weighting;
import com.graphhopper.storage.*;
import com.graphhopper.util.Helper;
import com.graphhopper.util.PMap;
import com.graphhopper.util.StopWatch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Locale;
import java.util.Random;

import static com.graphhopper.routing.ch.CHParameters.*;
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
public class PrepareContractionHierarchies extends AbstractAlgoPreparation {
    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final CHProfile chProfile;
    private final CHGraph chGraph;
    private final PrepareCHGraph prepareGraph;
    private final Random rand = new Random(123);
    private final IntSet updatedNeighbors;
    private final StopWatch allSW = new StopWatch();
    private final StopWatch periodicUpdateSW = new StopWatch();
    private final StopWatch lazyUpdateSW = new StopWatch();
    private final StopWatch neighborUpdateSW = new StopWatch();
    private final StopWatch contractionSW = new StopWatch();
    private final Params params;
    private final NodeContractor nodeContractor;
    private NodeOrderingProvider nodeOrderingProvider;
    private PrepareCHEdgeExplorer allEdgeExplorer;
    private PrepareCHEdgeExplorer disconnectExplorer;
    private int maxLevel;
    // nodes with highest priority come last
    private GHTreeMapComposed sortedNodes;
    private float[] oldPriorities;
    private PMap pMap = new PMap();
    private int checkCounter;

    public static PrepareContractionHierarchies fromGraphHopperStorage(GraphHopperStorage ghStorage, CHProfile chProfile) {
        return new PrepareContractionHierarchies(ghStorage, chProfile);
    }

    private PrepareContractionHierarchies(GraphHopperStorage ghStorage, CHProfile chProfile) {
        this.chGraph = ghStorage.getCHGraph(chProfile);
        this.chProfile = chProfile;
        params = Params.forTraversalMode(chProfile.getTraversalMode());
        updatedNeighbors = new IntHashSet(50);
        if (chProfile.getTraversalMode().isEdgeBased()) {
            TurnCostStorage turnCostStorage = chGraph.getTurnCostStorage();
            if (turnCostStorage == null) {
                throw new IllegalArgumentException("For edge-based CH you need a turn cost storage");
            }
            TurnWeighting turnWeighting = new TurnWeighting(chProfile.getWeighting(), turnCostStorage, chProfile.getUTurnCosts());
            prepareGraph = PrepareCHGraph.edgeBased(chGraph, chProfile.getWeighting(), turnWeighting);
            nodeContractor = new EdgeBasedNodeContractor(prepareGraph, pMap);
        } else {
            prepareGraph = PrepareCHGraph.nodeBased(chGraph, chProfile.getWeighting());
            nodeContractor = new NodeBasedNodeContractor(prepareGraph, pMap);
        }
    }

    public PrepareContractionHierarchies setParams(PMap pMap) {
        this.pMap = pMap;
        params.setPeriodicUpdatesPercentage(pMap.getInt(PERIODIC_UPDATES, params.getPeriodicUpdatesPercentage()));
        params.setLastNodesLazyUpdatePercentage(pMap.getInt(LAST_LAZY_NODES_UPDATES, params.getLastNodesLazyUpdatePercentage()));
        params.setNeighborUpdatePercentage(pMap.getInt(NEIGHBOR_UPDATES, params.getNeighborUpdatePercentage()));
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
        if (nodeOrderingProvider.getNumNodes() != prepareGraph.getNodes()) {
            throw new IllegalArgumentException(
                    "contraction order size (" + nodeOrderingProvider.getNumNodes() + ")" +
                            " must be equal to number of nodes in graph (" + prepareGraph.getNodes() + ").");
        }
        this.nodeOrderingProvider = nodeOrderingProvider;
        return this;
    }

    @Override
    public void doSpecificWork() {
        if (!prepareGraph.isReadyForContraction()) {
            throw new IllegalStateException("Given CHGraph has not been frozen yet");
        }
        if (prepareGraph.getEdges() > prepareGraph.getOriginalEdges()) {
            throw new IllegalStateException("Given CHGraph has been contracted already");
        }
        allSW.start();
        initFromGraph();
        runGraphContraction();
        allSW.stop();
        logFinalGraphStats();
    }

    private void logFinalGraphStats() {
        int edgeCount = prepareGraph.getOriginalEdges();
        logger.info("took: {}s, graph now - num edges: {}, num nodes: {}, num shortcuts: {}",
                (int) allSW.getSeconds(), nf(edgeCount), nf(prepareGraph.getNodes()), nf(prepareGraph.getEdges() - edgeCount));
    }

    private void runGraphContraction() {
        if (prepareGraph.getNodes() < 1)
            return;
        setMaxLevelOnAllNodes();
        if (nodeOrderingProvider != null) {
            contractNodesUsingFixedNodeOrdering();
        } else {
            contractNodesUsingHeuristicNodeOrdering();
        }
    }

    public boolean isEdgeBased() {
        return chProfile.isEdgeBased();
    }

    private void initFromGraph() {
        maxLevel = prepareGraph.getNodes();
        allEdgeExplorer = prepareGraph.createAllEdgeExplorer();
        disconnectExplorer = prepareGraph.createAllEdgeExplorer();

        // Use an alternative to PriorityQueue as it has some advantages:
        //   1. Gets automatically smaller if less entries are stored => less total RAM used.
        //      Important because Graph is increasing until the end.
        //   2. is slightly faster
        //   but we need the additional oldPriorities array to keep the old value which is necessary for the update method
        sortedNodes = new GHTreeMapComposed();
        oldPriorities = new float[prepareGraph.getNodes()];
        nodeContractor.initFromGraph();
    }

    private void setMaxLevelOnAllNodes() {
        final int nodes = prepareGraph.getNodes();
        for (int node = 0; node < nodes; node++) {
            prepareGraph.setLevel(node, maxLevel);
        }
    }

    private void updatePrioritiesOfRemainingNodes() {
        periodicUpdateSW.start();
        sortedNodes.clear();
        final int nodes = prepareGraph.getNodes();
        for (int node = 0; node < nodes; node++) {
            if (prepareGraph.getLevel(node) != maxLevel)
                continue;
            float priority = oldPriorities[node] = calculatePriority(node);
            sortedNodes.insert(node, priority);
        }
        periodicUpdateSW.stop();
    }

    private void contractNodesUsingHeuristicNodeOrdering() {
        // note that we update the priorities before preparing the node contractor. this does not make much sense,
        // but has always been like that and changing it would possibly require retuning the contraction parameters
        updatePrioritiesOfRemainingNodes();
        nodeContractor.prepareContraction();
        final int initSize = sortedNodes.getSize();
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
            int polledNode = sortedNodes.pollKey();

            if (!sortedNodes.isEmpty() && sortedNodes.getSize() < lastNodesLazyUpdates) {
                lazyUpdateSW.start();
                float priority = oldPriorities[polledNode] = calculatePriority(polledNode);
                if (priority > sortedNodes.peekValue()) {
                    // current node got more important => insert as new value and contract it later
                    sortedNodes.insert(polledNode, priority);
                    lazyUpdateSW.stop();
                    continue;
                }
                lazyUpdateSW.stop();
            }

            // contract node v!
            contractNode(polledNode, level);
            level++;

            if (sortedNodes.getSize() < nodesToAvoidContract)
                // skipped nodes are already set to maxLevel
                break;

            // there might be multiple edges going to the same neighbor nodes -> only calculate priority once per node
            updatedNeighbors.clear();
            PrepareCHEdgeIterator iter = allEdgeExplorer.setBaseNode(polledNode);
            while (iter.next()) {
                int nn = iter.getAdjNode();
                if (prepareGraph.getLevel(nn) != maxLevel)
                    continue;

                if (neighborUpdate && !updatedNeighbors.contains(nn) && rand.nextInt(100) < params.getNeighborUpdatePercentage()) {
                    neighborUpdateSW.start();
                    float oldPrio = oldPriorities[nn];
                    float priority = oldPriorities[nn] = calculatePriority(nn);
                    if (priority != oldPrio) {
                        sortedNodes.update(nn, oldPrio, priority);
                        updatedNeighbors.add(nn);
                    }
                    neighborUpdateSW.stop();
                }

                prepareGraph.disconnect(disconnectExplorer, iter);
            }
        }

        logHeuristicStats(updateCounter);

        logger.info(
                "new shortcuts: " + nf(nodeContractor.getAddedShortcutsCount())
                        + ", initSize:" + nf(initSize)
                        + ", " + chProfile.getWeighting()
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
        nodeContractor.prepareContraction();
        final int nodesToContract = nodeOrderingProvider.getNumNodes();
        final int logSize = Math.max(10, (int) (params.getLogMessagesPercentage() / 100.0 * nodesToContract));
        StopWatch stopWatch = new StopWatch();
        stopWatch.start();
        for (int i = 0; i < nodesToContract; ++i) {
            stopIfInterrupted();
            int node = nodeOrderingProvider.getNodeIdForLevel(i);
            contractNode(node, i);

            // disconnect neighbors
            PrepareCHEdgeIterator iter = allEdgeExplorer.setBaseNode(node);
            while (iter.next()) {
                if (prepareGraph.getLevel(iter.getAdjNode()) != maxLevel)
                    continue;
                prepareGraph.disconnect(disconnectExplorer, iter);
            }
            if (i % logSize == 0) {
                stopWatch.stop();
                logFixedNodeOrderingStats(i, logSize, stopWatch);
                stopWatch.start();
            }
        }
    }

    private void stopIfInterrupted() {
        if (Thread.currentThread().isInterrupted()) {
            throw new RuntimeException("Thread was interrupted");
        }
    }

    private void contractNode(int node, int level) {
        contractionSW.start();
        nodeContractor.contractNode(node);
        prepareGraph.setLevel(node, level);
        contractionSW.stop();
    }

    private void logHeuristicStats(int updateCounter) {
        logger.info(String.format(Locale.ROOT,
                "nodes: %10s, shortcuts: %10s, updates: %2d, checked-nodes: %10s, %s, %s, %s",
                nf(sortedNodes.getSize()),
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
                nf(prepareGraph.getNodes()),
                (100.0 * nodesContracted / prepareGraph.getNodes()),
                nf(nodeContractor.getAddedShortcutsCount()),
                nodesContracted == 0 ? 0 : logSize / (double) stopWatch.getMillis(),
                nodeContractor.getStatisticsString(),
                Helper.getMemInfo())
        );
    }

    public long getDijkstraCount() {
        return nodeContractor.getDijkstraCount();
    }

    public long getShortcuts() {
        return nodeContractor.getAddedShortcutsCount();
    }

    public double getLazyTime() {
        return lazyUpdateSW.getCurrentSeconds();
    }

    public double getPeriodTime() {
        return periodicUpdateSW.getCurrentSeconds();
    }

    public double getNeighborTime() {
        return neighborUpdateSW.getCurrentSeconds();
    }

    public Weighting getWeighting() {
        return chProfile.getWeighting();
    }

    public CHProfile getCHProfile() {
        return chProfile;
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
        return nodeContractor.calculatePriority(node);
    }

    @Override
    public String toString() {
        return chProfile.isEdgeBased() ? "prepare|dijkstrabi|edge|ch" : "prepare|dijkstrabi|ch";
    }

    private void _close() {
        nodeContractor.close();
        sortedNodes = null;
        oldPriorities = null;
    }

    void close() {
        CHGraphImpl cg = (CHGraphImpl) chGraph;
        cg.flush();
        cg.close();
    }

    public RoutingAlgorithmFactory getRoutingAlgorithmFactory() {
        return new CHRoutingAlgorithmFactory(chGraph);
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
            if (traversalMode.isEdgeBased()) {
                // todo: optimize
                return new Params(0, 100, 0, 100, 5);
            } else {
                return new Params(20, 10, 20, 100, 20);
            }
        }

        private Params(int periodicUpdatesPercentage, int lastNodesLazyUpdatePercentage, int neighborUpdatePercentage,
                       int nodesContractedPercentage, int logMessagesPercentage) {
            setPeriodicUpdatesPercentage(periodicUpdatesPercentage);
            setLastNodesLazyUpdatePercentage(lastNodesLazyUpdatePercentage);
            setNeighborUpdatePercentage(neighborUpdatePercentage);
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
