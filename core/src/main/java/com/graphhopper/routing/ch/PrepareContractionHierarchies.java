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
import com.graphhopper.routing.util.*;
import com.graphhopper.routing.weighting.TurnWeighting;
import com.graphhopper.routing.weighting.Weighting;
import com.graphhopper.storage.*;
import com.graphhopper.util.CHEdgeExplorer;
import com.graphhopper.util.CHEdgeIterator;
import com.graphhopper.util.Helper;
import com.graphhopper.util.StopWatch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Random;

import static com.graphhopper.routing.util.TraversalMode.EDGE_BASED_2DIR;
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
    protected final Logger logger = LoggerFactory.getLogger(getClass());
    private final Directory dir;
    private final PreparationWeighting prepareWeighting;
    private final Weighting weighting;
    private final TraversalMode traversalMode;
    private final GraphHopperStorage ghStorage;
    protected final CHGraphImpl prepareGraph;
    private final Random rand = new Random(123);
    private final StopWatch allSW = new StopWatch();
    protected NodeContractor nodeContractor;
    private CHEdgeExplorer vehicleAllExplorer;
    private CHEdgeExplorer vehicleAllTmpExplorer;
    private int maxLevel;
    // nodes with highest priority come last
    private GHTreeMapComposed sortedNodes;
    private float oldPriorities[];
    private double meanDegree;
    private int periodicUpdatesPercentage = 20;
    private int lastNodesLazyUpdatePercentage = 10;
    private int neighborUpdatePercentage = 20;
    protected double nodesContractedPercentage = 100;
    protected double logMessagesPercentage = 20;
    private double dijkstraTime;
    private double periodTime;
    private double lazyTime;
    private double neighborTime;
    private int initSize;

    public PrepareContractionHierarchies(Directory dir, GraphHopperStorage ghStorage, CHGraph chGraph,
                                         Weighting weighting, TraversalMode traversalMode) {
        this.dir = dir;
        this.ghStorage = ghStorage;
        this.prepareGraph = (CHGraphImpl) chGraph;
        this.traversalMode = traversalMode;
        this.weighting = weighting;
        prepareWeighting = new PreparationWeighting(weighting);
    }

    /**
     * The higher the values are the longer the preparation takes but the less shortcuts are
     * produced.
     * <p>
     *
     * @param periodicUpdates specifies how often periodic updates will happen. Use something less
     *                        than 10.
     */
    public PrepareContractionHierarchies setPeriodicUpdates(int periodicUpdates) {
        if (periodicUpdates < 0)
            return this;
        if (periodicUpdates > 100)
            throw new IllegalArgumentException("periodicUpdates has to be in [0, 100], to disable it use 0");

        this.periodicUpdatesPercentage = periodicUpdates;
        return this;
    }

    /**
     * @param lazyUpdates specifies when lazy updates will happen, measured relative to all existing
     *                    nodes. 100 means always.
     */
    public PrepareContractionHierarchies setLazyUpdates(int lazyUpdates) {
        if (lazyUpdates < 0)
            return this;

        if (lazyUpdates > 100)
            throw new IllegalArgumentException("lazyUpdates has to be in [0, 100], to disable it use 0");

        this.lastNodesLazyUpdatePercentage = lazyUpdates;
        return this;
    }

    /**
     * @param neighborUpdates specifies how often neighbor updates will happen. 100 means always.
     */
    public PrepareContractionHierarchies setNeighborUpdates(int neighborUpdates) {
        if (neighborUpdates < 0)
            return this;

        if (neighborUpdates > 100)
            throw new IllegalArgumentException("neighborUpdates has to be in [0, 100], to disable it use 0");

        this.neighborUpdatePercentage = neighborUpdates;
        return this;
    }

    /**
     * Specifies how often a log message should be printed. Specify something around 20 (20% of the
     * start nodes).
     */
    public PrepareContractionHierarchies setLogMessages(double logMessages) {
        if (logMessages >= 0)
            this.logMessagesPercentage = logMessages;
        return this;
    }

    /**
     * Define how many nodes (percentage) should be contracted. Less nodes means slower query but
     * faster contraction duration.
     */
    public PrepareContractionHierarchies setContractedNodes(double nodesContracted) {
        if (nodesContracted < 0)
            return this;

        if (nodesContracted > 100)
            throw new IllegalArgumentException("setNodesContracted can be 100% maximum");

        this.nodesContractedPercentage = nodesContracted;
        return this;
    }

    @Override
    public void doSpecificWork() {
        allSW.start();
        initFromGraph();
        runGraphContraction();

        logger.info("took:" + (int) allSW.stop().getSeconds() + "s "
                + ", new shortcuts: " + nf(nodeContractor.getAddedShortcutsCount())
                + ", " + prepareWeighting
                + ", dijkstras:" + nf(nodeContractor.getDijkstraCount())
                + ", " + getTimesAsString()
                + ", meanDegree:" + (long) meanDegree
                + ", initSize:" + nf(initSize)
                + ", periodic:" + periodicUpdatesPercentage
                + ", lazy:" + lastNodesLazyUpdatePercentage
                + ", neighbor:" + neighborUpdatePercentage
                + ", " + Helper.getMemInfo());

        int edgeCount = ghStorage.getAllEdges().length();
        logger.info("graph now - num edges: {}, num nodes: {}, num shortcuts: {}",
                nf(edgeCount), nf(ghStorage.getNodes()), nf(prepareGraph.getAllEdges().length() - edgeCount));
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
        if (traversalMode.isEdgeBased()) {
            return createAlgoEdgeBased(graph, opts);
        } else {
            return createAlgoNodeBased(graph, opts);
        }
    }

    private AbstractBidirAlgo createAlgoEdgeBased(Graph graph, AlgorithmOptions opts) {
        if (ASTAR_BI.equals(opts.getAlgorithm())) {
            return new AStarBidirectionEdgeCHNoSOD(graph, createTurnWeightingForEdgeBased(graph), EDGE_BASED_2DIR)
                    .setApproximation(RoutingAlgorithmFactorySimple.getApproximation(ASTAR_BI, opts, graph.getNodeAccess()));
        } else if (DIJKSTRA_BI.equals(opts.getAlgorithm())) {
            return new DijkstraBidirectionEdgeCHNoSOD(graph, createTurnWeightingForEdgeBased(graph), EDGE_BASED_2DIR);
        } else {
            throw new IllegalArgumentException("Algorithm " + opts.getAlgorithm() + " not supported for edge-based Contraction Hierarchies. Try with ch.disable=true");
        }
    }

    private AbstractBidirAlgo createAlgoNodeBased(Graph graph, AlgorithmOptions opts) {
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
            throw new IllegalArgumentException("Algorithm " + opts.getAlgorithm() + " not supported for node-based Contraction Hierarchies. Try with ch.disable=true");
        }
    }

    public boolean isEdgeBased() {
        return traversalMode.isEdgeBased();
    }

    private void initFromGraph() {
        ghStorage.freeze();
        FlagEncoder prepareFlagEncoder = prepareWeighting.getFlagEncoder();
        final EdgeFilter allFilter = new DefaultEdgeFilter(prepareFlagEncoder, true, true);
        maxLevel = prepareGraph.getNodes() + 1;
        vehicleAllExplorer = prepareGraph.createEdgeExplorer(allFilter);
        vehicleAllTmpExplorer = prepareGraph.createEdgeExplorer(allFilter);


        // Use an alternative to PriorityQueue as it has some advantages:
        //   1. Gets automatically smaller if less entries are stored => less total RAM used.
        //      Important because Graph is increasing until the end.
        //   2. is slightly faster
        //   but we need the additional oldPriorities array to keep the old value which is necessary for the update method
        sortedNodes = new GHTreeMapComposed();
        oldPriorities = new float[prepareGraph.getNodes()];
        nodeContractor = createNodeContractor(ghStorage, traversalMode);
        nodeContractor.initFromGraph();
    }

    private boolean prepareNodes() {
        // todo: this is probably not the best way to check if the graph has been prepared already, but we need
        // some kind of check like this, to make the tests in AbstractAlgorithmTester work where the preparation
        // sometimes is started twice on the same graph. without this check the shortcuts remain from the first
        // preparation, but we reset the node levels, which leads to problems.
        if (prepareGraph.getAllEdges().length() > ghStorage.getAllEdges().length()) {
            return false;
        }
        int nodes = prepareGraph.getNodes();
        for (int node = 0; node < nodes; node++) {
            prepareGraph.setLevel(node, maxLevel);
        }

        for (int node = 0; node < nodes; node++) {
            float priority = oldPriorities[node] = calculatePriority(node);
            sortedNodes.insert(node, priority);
        }

        // todo: is sortedNodes ever empty ? not sure what this check is good for ? if the graph was already prepared        
        // it is too late here anyway because we have already reset the node levels (?!)
        return !sortedNodes.isEmpty();
    }

    private void contractNodes() {
        // meanDegree is the number of edges / number of nodes ratio of the graph, not really the average degree, because
        // each edge can exist in both directions
        // todo: initializing meanDegree here instead of in initFromGraph() means that in the first round of calculating
        // node priorities all shortcut searches are cancelled immediately and all possible shortcuts are counted because
        // no witness path can be found. this is not really what we want, but changing it requires re-optimizing the
        // graph contraction parameters, because it affects the node contraction order.
        meanDegree = prepareGraph.getAllEdges().length() / prepareGraph.getNodes();
        initSize = sortedNodes.getSize();
        // todo: why do we start counting levels with 1 ??
        int level = 1;
        long counter = 0;
        long logSize = Math.round(Math.max(10, initSize / 100d * logMessagesPercentage));
        if (logMessagesPercentage == 0)
            logSize = Integer.MAX_VALUE;

        // preparation takes longer but queries are slightly faster with preparation
        // => enable it but call not so often
        boolean periodicUpdate = true;
        StopWatch periodSW = new StopWatch();
        int updateCounter = 0;
        long periodicUpdatesCount = Math.round(Math.max(10, sortedNodes.getSize() / 100d * periodicUpdatesPercentage));
        if (periodicUpdatesPercentage == 0)
            periodicUpdate = false;

        // disable lazy updates for last x percentage of nodes as preparation is then a lot slower
        // and query time does not really benefit
        long lastNodesLazyUpdates = Math.round(sortedNodes.getSize() / 100d * lastNodesLazyUpdatePercentage);

        // according to paper "Polynomial-time Construction of Contraction Hierarchies for Multi-criteria Objectives" by Funke and Storandt
        // we don't need to wait for all nodes to be contracted
        long nodesToAvoidContract = Math.round((100 - nodesContractedPercentage) / 100d * sortedNodes.getSize());
        StopWatch lazySW = new StopWatch();

        // Recompute priority of uncontracted neighbors.
        // Without neighbor updates preparation is faster but we need them
        // to slightly improve query time. Also if not applied too often it decreases the shortcut number.
        boolean neighborUpdate = true;
        if (neighborUpdatePercentage == 0)
            neighborUpdate = false;

        StopWatch neighborSW = new StopWatch();
        while (!sortedNodes.isEmpty()) {
            // periodically update priorities of ALL nodes
            if (periodicUpdate && counter > 0 && counter % periodicUpdatesCount == 0) {
                periodSW.start();
                sortedNodes.clear();
                int len = prepareGraph.getNodes();
                for (int node = 0; node < len; node++) {
                    if (prepareGraph.getLevel(node) != maxLevel)
                        continue;

                    float priority = oldPriorities[node] = calculatePriority(node);
                    sortedNodes.insert(node, priority);
                }
                periodSW.stop();
                updateCounter++;
                if (sortedNodes.isEmpty())
                    throw new IllegalStateException("Cannot prepare as no unprepared nodes where found. Called preparation twice?");
            }

            if (counter % logSize == 0) {
                dijkstraTime += nodeContractor.getDijkstraSeconds();
                periodTime += periodSW.getSeconds();
                lazyTime += lazySW.getSeconds();
                neighborTime += neighborSW.getSeconds();

                logStats(counter, updateCounter);

                nodeContractor.resetDijkstraTime();
                periodSW = new StopWatch();
                lazySW = new StopWatch();
                neighborSW = new StopWatch();
            }

            counter++;
            int polledNode = sortedNodes.pollKey();

            if (!sortedNodes.isEmpty() && sortedNodes.getSize() < lastNodesLazyUpdates) {
                lazySW.start();
                float priority = oldPriorities[polledNode] = calculatePriority(polledNode);
                if (priority > sortedNodes.peekValue()) {
                    // current node got more important => insert as new value and contract it later
                    sortedNodes.insert(polledNode, priority);
                    lazySW.stop();
                    continue;
                }
                lazySW.stop();
            }

            // contract node v!
            nodeContractor.setMaxVisitedNodes(getMaxVisitedNodesEstimate());
            long degree = nodeContractor.contractNode(polledNode);
            // put weight factor on meanDegree instead of taking the average => meanDegree is more stable
            meanDegree = (meanDegree * 2 + degree) / 3;
            prepareGraph.setLevel(polledNode, level);
            level++;

            if (sortedNodes.getSize() < nodesToAvoidContract)
                // skipped nodes are already set to maxLevel
                break;

            // there might be multiple edges going to the same neighbor nodes -> only calculate priority once per node
            IntSet updatedNeighors = new IntHashSet(10);
            CHEdgeIterator iter = vehicleAllExplorer.setBaseNode(polledNode);
            while (iter.next()) {

                if (Thread.currentThread().isInterrupted()) {
                    throw new RuntimeException("Thread was interrupted");
                }

                int nn = iter.getAdjNode();
                if (prepareGraph.getLevel(nn) != maxLevel)
                    continue;

                if (neighborUpdate && !updatedNeighors.contains(nn) && rand.nextInt(100) < neighborUpdatePercentage) {
                    neighborSW.start();
                    float oldPrio = oldPriorities[nn];
                    float priority = oldPriorities[nn] = calculatePriority(nn);
                    if (Float.compare(oldPrio, priority) != 0) {
                        sortedNodes.update(nn, oldPrio, priority);
                        updatedNeighors.add(nn);
                    }

                    neighborSW.stop();
                }

                // todo: does this work for edge-based case ? loop-helper shortcuts are probably not removed,
                // but does it really matter ?
                prepareGraph.disconnect(vehicleAllTmpExplorer, iter);
            }
        }

        dijkstraTime += nodeContractor.getDijkstraSeconds();
        periodTime += periodSW.getSeconds();
        lazyTime += lazySW.getSeconds();
        neighborTime += neighborSW.getSeconds();
        logStats(counter, updateCounter);

        // Preparation works only once so we can release temporary data.
        // The preparation object itself has to be intact to create the algorithm.
        close();
    }

    public void close() {
        nodeContractor.close();
        sortedNodes = null;
        oldPriorities = null;
    }

    public long getDijkstraCount() {
        return nodeContractor.getDijkstraCount();
    }

    public int getShortcuts() {
        return nodeContractor.getAddedShortcutsCount();
    }

    public double getLazyTime() {
        return lazyTime;
    }

    public double getPeriodTime() {
        return periodTime;
    }

    public double getDijkstraTime() {
        return dijkstraTime;
    }

    public double getNeighborTime() {
        return neighborTime;
    }

    public Weighting getWeighting() {
        return prepareGraph.getWeighting();
    }

    private String getTimesAsString() {
        return String.format(
                "t(dijk): %6.2f, t(period): %6.2f, t(lazy): %6.2f, t(neighbor): %6.2f",
                dijkstraTime, periodTime, lazyTime, neighborTime);
    }

    private float calculatePriority(int node) {
        nodeContractor.setMaxVisitedNodes(getMaxVisitedNodesEstimate());
        return nodeContractor.calculatePriority(node);
    }

    private int getMaxVisitedNodesEstimate() {
        // todo: we return 0 here if meanDegree is < 1, which is not really what we want, but changing this changes
        // the node contraction order and requires re-optimizing the parameters of the graph contraction
        return (int) meanDegree * 100;
    }

    @Override
    public String toString() {
        return traversalMode.isEdgeBased() ? "prepare|dijkstrabi|edge|ch" : "prepare|dijkstrabi|ch";
    }

    private NodeContractor createNodeContractor(Graph graph, TraversalMode traversalMode) {
        if (traversalMode.isEdgeBased()) {
            TurnWeighting chTurnWeighting = createTurnWeightingForEdgeBased(graph);
            // todo: shall we support TraversalMode.EDGE_BASED_2DIR_UTURN ?
            return new EdgeBasedNodeContractor(dir, ghStorage, prepareGraph, chTurnWeighting, EDGE_BASED_2DIR);
        } else {
            return new NodeBasedNodeContractor(dir, ghStorage, prepareGraph, weighting);
        }
    }

    private TurnWeighting createTurnWeightingForEdgeBased(Graph graph) {
        // important: do not simply take the extension from ghStorage, because we need the wrapped extension from
        // query graph!
        GraphExtension extension = graph.getExtension();
        if (!(extension instanceof TurnCostExtension)) {
            // todo: can we allow edge-based CH without turn costs ? does this make any sense (not really (?))
            throw new IllegalArgumentException("For edge-based CH you need a turn cost extension");
        }
        TurnCostExtension turnCostExtension = (TurnCostExtension) extension;
        return new TurnWeighting(new PreparationWeighting(weighting), turnCostExtension);
    }

    private void logStats(long counter, int updateCounter) {
        logger.info(String.format("%10s, updates: %2d, nodes: %10s, shortcuts: %10s, dijkstras: %10s, %s, meanDegree: %2d, %s, %s",
                nf(counter), updateCounter, nf(sortedNodes.getSize()),
                nf(nodeContractor.getAddedShortcutsCount()), nf(nodeContractor.getDijkstraCount()),
                getTimesAsString(), (long) meanDegree, nodeContractor.getPrepareAlgoMemoryUsage(),
                Helper.getMemInfo()));
    }

}
