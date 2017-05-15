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
import com.graphhopper.routing.weighting.AbstractWeighting;
import com.graphhopper.routing.weighting.Weighting;
import com.graphhopper.storage.*;
import com.graphhopper.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

import static com.graphhopper.util.Parameters.Algorithms.ASTAR_BI;
import static com.graphhopper.util.Parameters.Algorithms.DIJKSTRA_BI;

/**
 * This class prepares the graph for a bidirectional algorithm supporting contraction hierarchies
 * ie. an algorithm returned by createAlgo.
 * <p>
 * There are several description of contraction hierarchies available. The following is one of the
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
    private final TraversalMode traversalMode;
    private final LevelEdgeFilter levelFilter;
    private final GraphHopperStorage ghStorage;
    private final CHGraphImpl prepareGraph;
    private final DataAccess originalEdges;
    private final Map<Shortcut, Shortcut> shortcuts = new HashMap<Shortcut, Shortcut>();
    private final Random rand = new Random(123);
    private final StopWatch allSW = new StopWatch();
    AddShortcutHandler addScHandler = new AddShortcutHandler();
    CalcShortcutHandler calcScHandler = new CalcShortcutHandler();
    private CHEdgeExplorer vehicleInExplorer;
    private CHEdgeExplorer vehicleOutExplorer;
    private CHEdgeExplorer vehicleAllExplorer;
    private CHEdgeExplorer vehicleAllTmpExplorer;
    private CHEdgeExplorer calcPrioAllExplorer;
    private int maxLevel;
    // the most important nodes comes last
    private GHTreeMapComposed sortedNodes;
    private int oldPriorities[];
    private IgnoreNodeFilter ignoreNodeFilter;
    private DijkstraOneToMany prepareAlgo;
    private long counter;
    private int newShortcuts;
    private long dijkstraCount;
    private double meanDegree;
    private StopWatch dijkstraSW = new StopWatch();
    private int periodicUpdatesPercentage = 20;
    private int lastNodesLazyUpdatePercentage = 10;
    private int neighborUpdatePercentage = 20;
    private double nodesContractedPercentage = 100;
    private double logMessagesPercentage = 20;
    private double dijkstraTime;
    private double periodTime;
    private double lazyTime;
    private double neighborTime;
    private int maxEdgesCount;

    public PrepareContractionHierarchies(Directory dir, GraphHopperStorage ghStorage, CHGraph chGraph,
                                         Weighting weighting, TraversalMode traversalMode) {
        this.ghStorage = ghStorage;
        this.prepareGraph = (CHGraphImpl) chGraph;
        this.traversalMode = traversalMode;
        levelFilter = new LevelEdgeFilter(prepareGraph);

        prepareWeighting = new PreparationWeighting(weighting);
        originalEdges = dir.find("original_edges_" + AbstractWeighting.weightingToFileName(weighting));
        originalEdges.create(1000);
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
    public void doWork() {
        if (prepareWeighting == null)
            throw new IllegalStateException("No weight calculation set.");

        allSW.start();
        super.doWork();

        initFromGraph();
        if (!prepareNodes())
            return;

        contractNodes();
    }

    boolean prepareNodes() {
        int nodes = prepareGraph.getNodes();
        for (int node = 0; node < nodes; node++) {
            prepareGraph.setLevel(node, maxLevel);
        }

        for (int node = 0; node < nodes; node++) {
            int priority = oldPriorities[node] = calculatePriority(node);
            sortedNodes.insert(node, priority);
        }

        if (sortedNodes.isEmpty())
            return false;

        return true;
    }

    void contractNodes() {
        meanDegree = prepareGraph.getAllEdges().getMaxId() / prepareGraph.getNodes();
        int level = 1;
        counter = 0;
        int initSize = sortedNodes.getSize();
        long logSize = Math.round(Math.max(10, sortedNodes.getSize() / 100 * logMessagesPercentage));
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
        long nodesToAvoidContract = Math.round((100 - nodesContractedPercentage) / 100 * sortedNodes.getSize());
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

                    int priority = oldPriorities[node] = calculatePriority(node);
                    sortedNodes.insert(node, priority);
                }
                periodSW.stop();
                updateCounter++;
                if (sortedNodes.isEmpty())
                    throw new IllegalStateException("Cannot prepare as no unprepared nodes where found. Called preparation twice?");
            }

            if (counter % logSize == 0) {
                dijkstraTime += dijkstraSW.getSeconds();
                periodTime += periodSW.getSeconds();
                lazyTime += lazySW.getSeconds();
                neighborTime += neighborSW.getSeconds();

                logger.info(Helper.nf(counter) + ", updates:" + updateCounter
                        + ", nodes: " + Helper.nf(sortedNodes.getSize())
                        + ", shortcuts:" + Helper.nf(newShortcuts)
                        + ", dijkstras:" + Helper.nf(dijkstraCount)
                        + ", " + getTimesAsString()
                        + ", meanDegree:" + (long) meanDegree
                        + ", algo:" + prepareAlgo.getMemoryUsageAsString()
                        + ", " + Helper.getMemInfo());

                dijkstraSW = new StopWatch();
                periodSW = new StopWatch();
                lazySW = new StopWatch();
                neighborSW = new StopWatch();
            }

            counter++;
            int polledNode = sortedNodes.pollKey();

            if (!sortedNodes.isEmpty() && sortedNodes.getSize() < lastNodesLazyUpdates) {
                lazySW.start();
                int priority = oldPriorities[polledNode] = calculatePriority(polledNode);
                if (priority > sortedNodes.peekValue()) {
                    // current node got more important => insert as new value and contract it later
                    sortedNodes.insert(polledNode, priority);
                    lazySW.stop();
                    continue;
                }
                lazySW.stop();
            }

            // contract node v!
            shortcuts.clear();
            findShortcuts(addScHandler.setNode(polledNode));
            newShortcuts += addShortcuts(shortcuts.keySet());
            prepareGraph.setLevel(polledNode, level);
            level++;

            if (sortedNodes.getSize() < nodesToAvoidContract)
                // skipped nodes are already set to maxLevel
                break;

            CHEdgeIterator iter = vehicleAllExplorer.setBaseNode(polledNode);
            while (iter.next()) {

                if(Thread.currentThread().isInterrupted()){
                    throw new RuntimeException("Thread was interrupted");
                }

                int nn = iter.getAdjNode();
                if (prepareGraph.getLevel(nn) != maxLevel)
                    continue;

                if (neighborUpdate && rand.nextInt(100) < neighborUpdatePercentage) {
                    neighborSW.start();
                    int oldPrio = oldPriorities[nn];
                    int priority = oldPriorities[nn] = calculatePriority(nn);
                    if (priority != oldPrio)
                        sortedNodes.update(nn, oldPrio, priority);

                    neighborSW.stop();
                }

                prepareGraph.disconnect(vehicleAllTmpExplorer, iter);
            }
        }

        // Preparation works only once so we can release temporary data.
        // The preparation object itself has to be intact to create the algorithm.
        close();

        dijkstraTime += dijkstraSW.getSeconds();
        periodTime += periodSW.getSeconds();
        lazyTime += lazySW.getSeconds();
        neighborTime += neighborSW.getSeconds();
        logger.info("took:" + (int) allSW.stop().getSeconds()
                + ", new shortcuts: " + Helper.nf(newShortcuts)
                + ", " + prepareWeighting
                + ", dijkstras:" + dijkstraCount
                + ", " + getTimesAsString()
                + ", meanDegree:" + (long) meanDegree
                + ", initSize:" + initSize
                + ", periodic:" + periodicUpdatesPercentage
                + ", lazy:" + lastNodesLazyUpdatePercentage
                + ", neighbor:" + neighborUpdatePercentage
                + ", " + Helper.getMemInfo());
    }

    public long getDijkstraCount() {
        return dijkstraCount;
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

    public void close() {
        prepareAlgo.close();
        originalEdges.close();
        sortedNodes = null;
        oldPriorities = null;
    }

    private String getTimesAsString() {
        return "t(dijk):" + Helper.round2(dijkstraTime)
                + ", t(period):" + Helper.round2(periodTime)
                + ", t(lazy):" + Helper.round2(lazyTime)
                + ", t(neighbor):" + Helper.round2(neighborTime);
    }

    Set<Shortcut> testFindShortcuts(int node) {
        findShortcuts(addScHandler.setNode(node));
        return shortcuts.keySet();
    }

    /**
     * Calculates the priority of adjNode v without changing the graph. Warning: the calculated
     * priority must NOT depend on priority(v) and therefor findShortcuts should also not depend on
     * the priority(v). Otherwise updating the priority before contracting in contractNodes() could
     * lead to a slowish or even endless loop.
     */
    int calculatePriority(int v) {
        // set of shortcuts that would be added if adjNode v would be contracted next.
        findShortcuts(calcScHandler.setNode(v));

//        System.out.println(v + "\t " + tmpShortcuts);
        // # huge influence: the bigger the less shortcuts gets created and the faster is the preparation
        //
        // every adjNode has an 'original edge' number associated. initially it is r=1
        // when a new shortcut is introduced then r of the associated edges is summed up:
        // r(u,w)=r(u,v)+r(v,w) now we can define
        // originalEdgesCount = σ(v) := sum_{ (u,w) ∈ shortcuts(v) } of r(u, w)
        int originalEdgesCount = calcScHandler.originalEdgesCount;
//        for (Shortcut sc : tmpShortcuts) {
//            originalEdgesCount += sc.originalEdges;
//        }

        // # lowest influence on preparation speed or shortcut creation count
        // (but according to paper should speed up queries)
        //
        // number of already contracted neighbors of v
        int contractedNeighbors = 0;
        int degree = 0;
        CHEdgeIterator iter = calcPrioAllExplorer.setBaseNode(v);
        while (iter.next()) {
            degree++;
            if (iter.isShortcut())
                contractedNeighbors++;
        }

        // from shortcuts we can compute the edgeDifference
        // # low influence: with it the shortcut creation is slightly faster
        //
        // |shortcuts(v)| − |{(u, v) | v uncontracted}| − |{(v, w) | v uncontracted}|
        // meanDegree is used instead of outDegree+inDegree as if one adjNode is in both directions
        // only one bucket memory is used. Additionally one shortcut could also stand for two directions.
        int edgeDifference = calcScHandler.shortcuts - degree;

        // according to the paper do a simple linear combination of the properties to get the priority.
        // this is the current optimum for unterfranken:
        return 10 * edgeDifference + originalEdgesCount + contractedNeighbors;
    }

    /**
     * Finds shortcuts, does not change the underlying graph.
     */
    void findShortcuts(ShortcutHandler sch) {
        long tmpDegreeCounter = 0;
        EdgeIterator incomingEdges = vehicleInExplorer.setBaseNode(sch.getNode());
        // collect outgoing nodes (goal-nodes) only once
        while (incomingEdges.next()) {
            int u_fromNode = incomingEdges.getAdjNode();
            // accept only uncontracted nodes
            if (prepareGraph.getLevel(u_fromNode) != maxLevel)
                continue;

            double v_u_dist = incomingEdges.getDistance();
            double v_u_weight = prepareWeighting.calcWeight(incomingEdges, true, EdgeIterator.NO_EDGE);
            int skippedEdge1 = incomingEdges.getEdge();
            int incomingEdgeOrigCount = getOrigEdgeCount(skippedEdge1);
            // collect outgoing nodes (goal-nodes) only once
            EdgeIterator outgoingEdges = vehicleOutExplorer.setBaseNode(sch.getNode());
            // force fresh maps etc as this cannot be determined by from node alone (e.g. same from node but different avoidNode)
            prepareAlgo.clear();
            tmpDegreeCounter++;
            while (outgoingEdges.next()) {
                int w_toNode = outgoingEdges.getAdjNode();
                // add only uncontracted nodes
                if (prepareGraph.getLevel(w_toNode) != maxLevel || u_fromNode == w_toNode)
                    continue;

                // Limit weight as ferries or forbidden edges can increase local search too much.
                // If we decrease the correct weight we only explore less and introduce more shortcuts.
                // I.e. no change to accuracy is made.
                double existingDirectWeight = v_u_weight + prepareWeighting.calcWeight(outgoingEdges, false, incomingEdges.getEdge());
                if (Double.isNaN(existingDirectWeight))
                    throw new IllegalStateException("Weighting should never return NaN values"
                            + ", in:" + getCoords(incomingEdges, prepareGraph) + ", out:" + getCoords(outgoingEdges, prepareGraph)
                            + ", dist:" + outgoingEdges.getDistance());

                if (Double.isInfinite(existingDirectWeight))
                    continue;

                double existingDistSum = v_u_dist + outgoingEdges.getDistance();
                prepareAlgo.setWeightLimit(existingDirectWeight);
                prepareAlgo.setMaxVisitedNodes((int) meanDegree * 100);
                prepareAlgo.setEdgeFilter(ignoreNodeFilter.setAvoidNode(sch.getNode()));

                dijkstraSW.start();
                dijkstraCount++;
                int endNode = prepareAlgo.findEndNode(u_fromNode, w_toNode);
                dijkstraSW.stop();

                // compare end node as the limit could force dijkstra to finish earlier
                if (endNode == w_toNode && prepareAlgo.getWeight(endNode) <= existingDirectWeight)
                    // FOUND witness path, so do not add shortcut
                    continue;

                sch.foundShortcut(u_fromNode, w_toNode,
                        existingDirectWeight, existingDistSum,
                        outgoingEdges,
                        skippedEdge1, incomingEdgeOrigCount);
            }
        }
        if (sch instanceof AddShortcutHandler) {
            // sliding mean value when using "*2" => slower changes
            meanDegree = (meanDegree * 2 + tmpDegreeCounter) / 3;
            // meanDegree = (meanDegree + tmpDegreeCounter) / 2;
        }
    }

    /**
     * Introduces the necessary shortcuts for adjNode v in the graph.
     */
    int addShortcuts(Collection<Shortcut> tmpShortcuts) {
        int tmpNewShortcuts = 0;
        NEXT_SC:
        for (Shortcut sc : tmpShortcuts) {
            boolean updatedInGraph = false;
            // check if we need to update some existing shortcut in the graph
            CHEdgeIterator iter = vehicleOutExplorer.setBaseNode(sc.from);
            while (iter.next()) {
                if (iter.isShortcut() && iter.getAdjNode() == sc.to) {
                    int status = iter.getMergeStatus(sc.flags);
                    if (status == 0)
                        continue;

                    if (sc.weight >= prepareWeighting.calcWeight(iter, false, EdgeIterator.NO_EDGE)) {
                        // special case if a bidirectional shortcut has worse weight and still has to be added as otherwise the opposite direction would be missing
                        // see testShortcutMergeBug
                        if (status == 2)
                            break;

                        continue NEXT_SC;
                    }

                    if (iter.getEdge() == sc.skippedEdge1 || iter.getEdge() == sc.skippedEdge2) {
                        throw new IllegalStateException("Shortcut cannot update itself! " + iter.getEdge()
                                + ", skipEdge1:" + sc.skippedEdge1 + ", skipEdge2:" + sc.skippedEdge2
                                + ", edge " + iter + ":" + getCoords(iter, prepareGraph)
                                + ", sc:" + sc
                                + ", skippedEdge1: " + getCoords(prepareGraph.getEdgeIteratorState(sc.skippedEdge1, sc.from), prepareGraph)
                                + ", skippedEdge2: " + getCoords(prepareGraph.getEdgeIteratorState(sc.skippedEdge2, sc.to), prepareGraph)
                                + ", neighbors:" + GHUtility.getNeighbors(iter));
                    }

                    // note: flags overwrite weight => call first
                    iter.setFlags(sc.flags);
                    iter.setWeight(sc.weight);
                    iter.setDistance(sc.dist);
                    iter.setSkippedEdges(sc.skippedEdge1, sc.skippedEdge2);
                    setOrigEdgeCount(iter.getEdge(), sc.originalEdges);
                    updatedInGraph = true;
                    break;
                }
            }

            if (!updatedInGraph) {
                CHEdgeIteratorState edgeState = prepareGraph.shortcut(sc.from, sc.to);
                // note: flags overwrite weight => call first
                edgeState.setFlags(sc.flags);
                edgeState.setWeight(sc.weight);
                edgeState.setDistance(sc.dist);
                edgeState.setSkippedEdges(sc.skippedEdge1, sc.skippedEdge2);
                setOrigEdgeCount(edgeState.getEdge(), sc.originalEdges);
                tmpNewShortcuts++;
            }
        }
        return tmpNewShortcuts;
    }

    String getCoords(EdgeIteratorState e, Graph g) {
        NodeAccess na = g.getNodeAccess();
        int base = e.getBaseNode();
        int adj = e.getAdjNode();
        return base + "->" + adj + " (" + e.getEdge() + "); "
                + na.getLat(base) + "," + na.getLon(base) + " -> " + na.getLat(adj) + "," + na.getLon(adj);
    }

    PrepareContractionHierarchies initFromGraph() {
        ghStorage.freeze();
        maxEdgesCount = ghStorage.getAllEdges().getMaxId();
        FlagEncoder prepareFlagEncoder = prepareWeighting.getFlagEncoder();
        vehicleInExplorer = prepareGraph.createEdgeExplorer(new DefaultEdgeFilter(prepareFlagEncoder, true, false));
        vehicleOutExplorer = prepareGraph.createEdgeExplorer(new DefaultEdgeFilter(prepareFlagEncoder, false, true));
        final EdgeFilter allFilter = new DefaultEdgeFilter(prepareFlagEncoder, true, true);

        // filter by vehicle and level number
        final EdgeFilter accessWithLevelFilter = new LevelEdgeFilter(prepareGraph) {
            @Override
            public final boolean accept(EdgeIteratorState edgeState) {
                if (!super.accept(edgeState))
                    return false;

                return allFilter.accept(edgeState);
            }
        };

        maxLevel = prepareGraph.getNodes() + 1;
        ignoreNodeFilter = new IgnoreNodeFilter(prepareGraph, maxLevel);
        vehicleAllExplorer = prepareGraph.createEdgeExplorer(allFilter);
        vehicleAllTmpExplorer = prepareGraph.createEdgeExplorer(allFilter);
        calcPrioAllExplorer = prepareGraph.createEdgeExplorer(accessWithLevelFilter);

        // Use an alternative to PriorityQueue as it has some advantages:
        //   1. Gets automatically smaller if less entries are stored => less total RAM used.
        //      Important because Graph is increasing until the end.
        //   2. is slightly faster
        //   but we need the additional oldPriorities array to keep the old value which is necessary for the update method
        sortedNodes = new GHTreeMapComposed();
        oldPriorities = new int[prepareGraph.getNodes()];
        prepareAlgo = new DijkstraOneToMany(prepareGraph, prepareWeighting, traversalMode);
        return this;
    }

    public int getShortcuts() {
        return newShortcuts;
    }

    private void setOrigEdgeCount(int edgeId, int value) {
        edgeId -= maxEdgesCount;
        if (edgeId < 0) {
            // ignore setting as every normal edge has original edge count of 1
            if (value != 1)
                throw new IllegalStateException("Trying to set original edge count for normal edge to a value = " + value
                        + ", edge:" + (edgeId + maxEdgesCount) + ", max:" + maxEdgesCount + ", graph.max:" + ghStorage.getAllEdges().getMaxId());
            return;
        }

        long tmp = (long) edgeId * 4;
        originalEdges.ensureCapacity(tmp + 4);
        originalEdges.setInt(tmp, value);
    }

    private int getOrigEdgeCount(int edgeId) {
        edgeId -= maxEdgesCount;
        if (edgeId < 0)
            return 1;

        long tmp = (long) edgeId * 4;
        originalEdges.ensureCapacity(tmp + 4);
        return originalEdges.getInt(tmp);
    }

    @Override
    public RoutingAlgorithm createAlgo(Graph graph, AlgorithmOptions opts) {
        AbstractBidirAlgo algo;
        if (ASTAR_BI.equals(opts.getAlgorithm())) {
            AStarBidirection tmpAlgo = new AStarBidirectionCH(graph, prepareWeighting, traversalMode);
            tmpAlgo.setApproximation(RoutingAlgorithmFactorySimple.getApproximation(ASTAR_BI, opts, graph.getNodeAccess()));
            algo = tmpAlgo;

        } else if (DIJKSTRA_BI.equals(opts.getAlgorithm())) {
            algo = new DijkstraBidirectionCH(graph, prepareWeighting, traversalMode);
        } else {
            throw new IllegalArgumentException("Algorithm " + opts.getAlgorithm() + " not supported for Contraction Hierarchies. Try with ch.disable=true");
        }

        algo.setMaxVisitedNodes(opts.getMaxVisitedNodes());
        algo.setEdgeFilter(levelFilter);
        return algo;
    }

    public static class AStarBidirectionCH extends AStarBidirection {
        public AStarBidirectionCH(Graph graph, Weighting weighting, TraversalMode traversalMode) {
            super(graph, weighting, traversalMode);
        }

        @Override
        protected void initCollections(int size) {
            super.initCollections(Math.min(size, 2000));
        }

        @Override
        protected boolean finished() {
            // we need to finish BOTH searches for CH!
            if (finishedFrom && finishedTo)
                return true;

            // changed finish condition for CH
            return currFrom.weight >= bestPath.getWeight() && currTo.weight >= bestPath.getWeight();
        }

        @Override
        protected Path createAndInitPath() {
            bestPath = new Path4CH(graph, graph.getBaseGraph(), weighting);
            return bestPath;
        }

        @Override
        public String getName() {
            return "astarbi|ch";
        }

        @Override
        public String toString() {
            return getName() + "|" + weighting;
        }
    }

    public static class DijkstraBidirectionCH extends DijkstraBidirectionRef {
        public DijkstraBidirectionCH(Graph graph, Weighting weighting, TraversalMode traversalMode) {
            super(graph, weighting, traversalMode);
        }

        @Override
        protected void initCollections(int size) {
            super.initCollections(Math.min(size, 2000));
        }

        @Override
        public boolean finished() {
            // we need to finish BOTH searches for CH!
            if (finishedFrom && finishedTo)
                return true;

            // changed also the final finish condition for CH
            return currFrom.weight >= bestPath.getWeight() && currTo.weight >= bestPath.getWeight();
        }

        @Override
        protected Path createAndInitPath() {
            bestPath = new Path4CH(graph, graph.getBaseGraph(), weighting);
            return bestPath;
        }

        @Override
        public String getName() {
            return "dijkstrabi|ch";
        }

        @Override
        public String toString() {
            return getName() + "|" + weighting;
        }
    }

    @Override
    public String toString() {
        return "prepare|dijkstrabi|ch";
    }

    interface ShortcutHandler {
        void foundShortcut(int u_fromNode, int w_toNode,
                           double existingDirectWeight, double distance,
                           EdgeIterator outgoingEdges,
                           int skippedEdge1, int incomingEdgeOrigCount);

        int getNode();
    }

    static class IgnoreNodeFilter implements EdgeFilter {
        int avoidNode;
        int maxLevel;
        CHGraph graph;

        public IgnoreNodeFilter(CHGraph g, int maxLevel) {
            this.graph = g;
            this.maxLevel = maxLevel;
        }

        public IgnoreNodeFilter setAvoidNode(int node) {
            this.avoidNode = node;
            return this;
        }

        @Override
        public final boolean accept(EdgeIteratorState iter) {
            // ignore if it is skipNode or adjNode is already contracted
            int node = iter.getAdjNode();
            return avoidNode != node && graph.getLevel(node) == maxLevel;
        }
    }

    static class Shortcut {
        int from;
        int to;
        int skippedEdge1;
        int skippedEdge2;
        double dist;
        double weight;
        int originalEdges;
        long flags = PrepareEncoder.getScFwdDir();

        public Shortcut(int from, int to, double weight, double dist) {
            this.from = from;
            this.to = to;
            this.weight = weight;
            this.dist = dist;
        }

        @Override
        public int hashCode() {
            int hash = 5;
            hash = 23 * hash + from;
            hash = 23 * hash + to;
            return 23 * hash
                    + (int) (Double.doubleToLongBits(this.weight) ^ (Double.doubleToLongBits(this.weight) >>> 32));
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == null || getClass() != obj.getClass())
                return false;

            final Shortcut other = (Shortcut) obj;
            if (this.from != other.from || this.to != other.to)
                return false;

            return Double.doubleToLongBits(this.weight) == Double.doubleToLongBits(other.weight);
        }

        @Override
        public String toString() {
            String str;
            if (flags == PrepareEncoder.getScDirMask())
                str = from + "<->";
            else
                str = from + "->";

            return str + to + ", weight:" + weight + " (" + skippedEdge1 + "," + skippedEdge2 + ")";
        }
    }

    class CalcShortcutHandler implements ShortcutHandler {
        int node;
        int originalEdgesCount;
        int shortcuts;

        @Override
        public int getNode() {
            return node;
        }

        public CalcShortcutHandler setNode(int n) {
            node = n;
            originalEdgesCount = 0;
            shortcuts = 0;
            return this;
        }

        @Override
        public void foundShortcut(int u_fromNode, int w_toNode,
                                  double existingDirectWeight, double distance,
                                  EdgeIterator outgoingEdges,
                                  int skippedEdge1, int incomingEdgeOrigCount) {
            shortcuts++;
            originalEdgesCount += incomingEdgeOrigCount + getOrigEdgeCount(outgoingEdges.getEdge());
        }
    }

    class AddShortcutHandler implements ShortcutHandler {
        int node;

        public AddShortcutHandler() {
        }

        @Override
        public int getNode() {
            return node;
        }

        public AddShortcutHandler setNode(int n) {
            shortcuts.clear();
            node = n;
            return this;
        }

        @Override
        public void foundShortcut(int u_fromNode, int w_toNode,
                                  double existingDirectWeight, double existingDistSum,
                                  EdgeIterator outgoingEdges,
                                  int skippedEdge1, int incomingEdgeOrigCount) {
            // FOUND shortcut
            // but be sure that it is the only shortcut in the collection
            // and also in the graph for u->w. If existing AND identical weight => update setProperties.
            // Hint: shortcuts are always one-way due to distinct level of every node but we don't
            // know yet the levels so we need to determine the correct direction or if both directions
            Shortcut sc = new Shortcut(u_fromNode, w_toNode, existingDirectWeight, existingDistSum);
            if (shortcuts.containsKey(sc))
                return;

            Shortcut tmpSc = new Shortcut(w_toNode, u_fromNode, existingDirectWeight, existingDistSum);
            Shortcut tmpRetSc = shortcuts.get(tmpSc);
            if (tmpRetSc != null) {
                // overwrite flags only if skipped edges are identical
                if (tmpRetSc.skippedEdge2 == skippedEdge1 && tmpRetSc.skippedEdge1 == outgoingEdges.getEdge()) {
                    tmpRetSc.flags = PrepareEncoder.getScDirMask();
                    return;
                }
            }

            Shortcut old = shortcuts.put(sc, sc);
            if (old != null)
                throw new IllegalStateException("Shortcut did not exist (" + sc + ") but was overwriting another one? " + old);

            sc.skippedEdge1 = skippedEdge1;
            sc.skippedEdge2 = outgoingEdges.getEdge();
            sc.originalEdges = incomingEdgeOrigCount + getOrigEdgeCount(outgoingEdges.getEdge());
        }
    }
}
