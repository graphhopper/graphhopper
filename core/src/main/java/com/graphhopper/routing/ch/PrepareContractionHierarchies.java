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

import com.graphhopper.coll.GHTreeMapComposed;
import com.graphhopper.routing.*;
import com.graphhopper.routing.util.*;
import com.graphhopper.routing.weighting.Weighting;
import com.graphhopper.storage.CHGraph;
import com.graphhopper.storage.Graph;
import com.graphhopper.storage.GraphHopperStorage;
import com.graphhopper.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Locale;
import java.util.Random;

import static com.graphhopper.routing.ch.CHParameters.*;
import static com.graphhopper.util.Helper.nf;
import static com.graphhopper.util.Parameters.Algorithms.ASTAR_BI;
import static com.graphhopper.util.Parameters.Algorithms.DIJKSTRA_BI;

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
public class PrepareContractionHierarchies extends AbstractAlgoPreparation implements RoutingAlgorithmFactory {
    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final PreparationWeighting prepareWeighting;
    private final Weighting weighting;
    private final TraversalMode traversalMode;
    private final CHGraph prepareGraph;
    private final Random rand = new Random(123);
    private final StopWatch allSW = new StopWatch();
    private final StopWatch periodicUpdateSW = new StopWatch();
    private final StopWatch lazyUpdateSW = new StopWatch();
    private final StopWatch neighborUpdateSW = new StopWatch();
    private final StopWatch contractionSW = new StopWatch();
    private final Params params;
    private NodeContractor nodeContractor;
    private CHEdgeExplorer vehicleAllExplorer;
    private CHEdgeExplorer vehicleAllTmpExplorer;
    private int maxLevel;
    // nodes with highest priority come last
    private GHTreeMapComposed sortedNodes;
    private float[] oldPriorities;
    private PMap pMap = new PMap();
    private int initSize;
    private int checkCounter;

    public PrepareContractionHierarchies(CHGraph chGraph, Weighting weighting, TraversalMode traversalMode) {
        this.prepareGraph = chGraph;
        this.traversalMode = traversalMode;
        this.weighting = weighting;
        prepareWeighting = new PreparationWeighting(weighting);
        this.params = Params.forTraversalMode(traversalMode);
    }

    public static PrepareContractionHierarchies fromGraphHopperStorage(GraphHopperStorage ghStorage, Weighting weighting, TraversalMode traversalMode) {
        return new PrepareContractionHierarchies(ghStorage.getGraph(CHGraph.class, weighting), weighting, traversalMode);
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

    @Override
    public void doSpecificWork() {
        allSW.start();
        initFromGraph();
        runGraphContraction();

        logger.info("took:" + (int) allSW.stop().getSeconds() + "s "
                + ", new shortcuts: " + nf(nodeContractor.getAddedShortcutsCount())
                + ", initSize:" + nf(initSize)
                + ", " + prepareWeighting
                + ", periodic:" + params.getPeriodicUpdatesPercentage()
                + ", lazy:" + params.getLastNodesLazyUpdatePercentage()
                + ", neighbor:" + params.getNeighborUpdatePercentage()
                + ", " + getTimesAsString()
                + ", lazy-overhead: " + (int) (100 * ((checkCounter / (double) initSize) - 1)) + "%"
                + ", " + Helper.getMemInfo());

        int edgeCount = prepareGraph.getOriginalEdges();
        logger.info("graph now - num edges: {}, num nodes: {}, num shortcuts: {}",
                nf(edgeCount), nf(prepareGraph.getNodes()), nf(prepareGraph.getEdges() - edgeCount));
    }

    protected void runGraphContraction() {
        if (!prepareNodes())
            return;
        contractNodes();
    }

    @Override
    public RoutingAlgorithm createAlgo(Graph graph, AlgorithmOptions opts) {
        AbstractBidirAlgo algo = doCreateAlgo(graph, opts);
        algo.setEdgeFilter(new LevelEdgeFilter(prepareGraph));
        algo.setMaxVisitedNodes(opts.getMaxVisitedNodes());
        return algo;
    }

    private AbstractBidirAlgo doCreateAlgo(Graph graph, AlgorithmOptions opts) {
        if (ASTAR_BI.equals(opts.getAlgorithm())) {
            return new AStarBidirectionCH(graph, prepareWeighting, traversalMode)
                    .setApproximation(RoutingAlgorithmFactorySimple.getApproximation(ASTAR_BI, opts, graph.getNodeAccess()));
        } else if (DIJKSTRA_BI.equals(opts.getAlgorithm())) {
            if (opts.getHints().getBool("stall_on_demand", true)) {
                return new DijkstraBidirectionCH(graph, prepareWeighting, traversalMode);
            } else {
                return new DijkstraBidirectionCHNoSOD(graph, prepareWeighting, traversalMode);
            }
        } else {
            throw new IllegalArgumentException("Algorithm " + opts.getAlgorithm() + " not supported for Contraction Hierarchies. Try with ch.disable=true");
        }
    }

    private void initFromGraph() {
        FlagEncoder prepareFlagEncoder = prepareWeighting.getFlagEncoder();
        final EdgeFilter allFilter = DefaultEdgeFilter.allEdges(prepareFlagEncoder);
        maxLevel = prepareGraph.getNodes();
        vehicleAllExplorer = prepareGraph.createEdgeExplorer(allFilter);
        vehicleAllTmpExplorer = prepareGraph.createEdgeExplorer(allFilter);

        // Use an alternative to PriorityQueue as it has some advantages:
        //   1. Gets automatically smaller if less entries are stored => less total RAM used.
        //      Important because Graph is increasing until the end.
        //   2. is slightly faster
        //   but we need the additional oldPriorities array to keep the old value which is necessary for the update method
        sortedNodes = new GHTreeMapComposed();
        oldPriorities = new float[prepareGraph.getNodes()];
        nodeContractor = new NodeBasedNodeContractor(prepareGraph, weighting, pMap);
        nodeContractor.initFromGraph();
    }

    private boolean prepareNodes() {
        int nodes = prepareGraph.getNodes();
        for (int node = 0; node < nodes; node++) {
            prepareGraph.setLevel(node, maxLevel);
        }
        periodicUpdateSW.start();
        for (int node = 0; node < nodes; node++) {
            float priority = oldPriorities[node] = calculatePriority(node);
            sortedNodes.insert(node, priority);
        }
        periodicUpdateSW.stop();

        return !sortedNodes.isEmpty();
    }

    private void contractNodes() {
        nodeContractor.prepareContraction();
        initSize = sortedNodes.getSize();
        int level = 0;
        checkCounter = 0;
        long logSize = Math.round(Math.max(10, initSize / 100d * params.getLogMessagesPercentage()));
        if (params.getLogMessagesPercentage() == 0)
            logSize = Integer.MAX_VALUE;

        // preparation takes longer but queries are slightly faster with preparation
        // => enable it but call not so often
        boolean periodicUpdate = true;
        int updateCounter = 0;
        long periodicUpdatesCount = Math.round(Math.max(10, sortedNodes.getSize() / 100d * params.getPeriodicUpdatesPercentage()));
        if (params.getPeriodicUpdatesPercentage() == 0)
            periodicUpdate = false;

        // disable lazy updates for last x percentage of nodes as preparation is then a lot slower
        // and query time does not really benefit
        long lastNodesLazyUpdates = Math.round(sortedNodes.getSize() / 100d * params.getLastNodesLazyUpdatePercentage());

        // according to paper "Polynomial-time Construction of Contraction Hierarchies for Multi-criteria Objectives" by Funke and Storandt
        // we don't need to wait for all nodes to be contracted
        long nodesToAvoidContract = Math.round((100 - params.getNodesContractedPercentage()) / 100d * sortedNodes.getSize());

        // Recompute priority of uncontracted neighbors.
        // Without neighbor updates preparation is faster but we need them
        // to slightly improve query time. Also if not applied too often it decreases the shortcut number.
        boolean neighborUpdate = true;
        if (params.getNeighborUpdatePercentage() == 0)
            neighborUpdate = false;

        while (!sortedNodes.isEmpty()) {
            // periodically update priorities of ALL nodes
            if (periodicUpdate && checkCounter > 0 && checkCounter % periodicUpdatesCount == 0) {
                periodicUpdateSW.start();
                sortedNodes.clear();
                for (int node = 0; node < prepareGraph.getNodes(); node++) {
                    if (prepareGraph.getLevel(node) != maxLevel)
                        continue;

                    float priority = oldPriorities[node] = calculatePriority(node);
                    sortedNodes.insert(node, priority);
                }
                periodicUpdateSW.stop();
                updateCounter++;
                if (sortedNodes.isEmpty())
                    throw new IllegalStateException("Cannot prepare as no unprepared nodes where found. Called preparation twice?");
            }

            if (checkCounter % logSize == 0) {
                logStats(updateCounter);
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
            contractionSW.start();
            nodeContractor.contractNode(polledNode);
            prepareGraph.setLevel(polledNode, level);
            level++;
            contractionSW.stop();

            if (sortedNodes.getSize() < nodesToAvoidContract)
                // skipped nodes are already set to maxLevel
                break;

            CHEdgeIterator iter = vehicleAllExplorer.setBaseNode(polledNode);
            while (iter.next()) {

                if (Thread.currentThread().isInterrupted()) {
                    throw new RuntimeException("Thread was interrupted");
                }

                int nn = iter.getAdjNode();
                if (prepareGraph.getLevel(nn) != maxLevel)
                    continue;

                if (neighborUpdate && rand.nextInt(100) < params.getNeighborUpdatePercentage()) {
                    neighborUpdateSW.start();
                    float oldPrio = oldPriorities[nn];
                    float priority = oldPriorities[nn] = calculatePriority(nn);
                    if (priority != oldPrio)
                        sortedNodes.update(nn, oldPrio, priority);

                    neighborUpdateSW.stop();
                }

                prepareGraph.disconnect(vehicleAllTmpExplorer, iter);
            }
        }

        logStats(updateCounter);

        // Preparation works only once so we can release temporary data.
        // The preparation object itself has to be intact to create the algorithm.
        close();
    }

    private void close() {
        nodeContractor.close();
        sortedNodes = null;
        oldPriorities = null;
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
        return weighting;
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
                "t(total): %6.2f,  t(period): %6.2f, t(lazy): %6.2f, t(neighbor): %6.2f, t(contr): %6.2f, t(other) : %6.2f, t(dijk): %6.2f",
                totalTime, periodicUpdateTime, lazyUpdateTime, neighborUpdateTime, contractionTime, otherTime, dijkstraTime);
    }

    private float calculatePriority(int node) {
        return nodeContractor.calculatePriority(node);
    }

    @Override
    public String toString() {
        return "prepare|dijkstrabi|ch";
    }

    private void logStats(int updateCounter) {
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

    private static class Params {
        /**
         * Specifies how often periodic updates will happen. The higher the value the longer the preparation takes
         * but the less shortcuts are produced.
         */
        private int periodicUpdatesPercentage;
        /**
         * Specifies when lazy updates will happen, measured relative to all existing nodes. 100 means always.
         */
        private int lastNodesLazyUpdatePercentage;
        /**
         * Specifies how often neighbor updates will happen. 100 means always.
         */
        private int neighborUpdatePercentage;
        /**
         * Defines how many nodes (percentage) should be contracted. Less nodes means slower query but
         * faster contraction.
         */
        private int nodesContractedPercentage;
        /**
         * Specifies how often a log message should be printed. Specify something around 20 (20% of the
         * start nodes).
         */
        private int logMessagesPercentage;

        static Params forTraversalMode(TraversalMode traversalMode) {
            if (traversalMode.isEdgeBased()) {
                throw new IllegalArgumentException("Contraction Hierarchies are not supported for edge-based traversal yet");
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
