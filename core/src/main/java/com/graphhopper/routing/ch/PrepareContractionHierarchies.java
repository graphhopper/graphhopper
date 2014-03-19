/*
 *  Licensed to GraphHopper and Peter Karich under one or more contributor
 *  license agreements. See the NOTICE file distributed with this work for 
 *  additional information regarding copyright ownership.
 * 
 *  GraphHopper licenses this file to you under the Apache License, 
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
import com.graphhopper.routing.util.AbstractAlgoPreparation;
import com.graphhopper.routing.util.DefaultEdgeFilter;
import com.graphhopper.routing.util.LevelEdgeFilter;
import com.graphhopper.routing.util.FlagEncoder;
import com.graphhopper.routing.util.Weighting;
import com.graphhopper.routing.AStarBidirection;
import com.graphhopper.routing.DijkstraBidirectionRef;
import com.graphhopper.routing.DijkstraOneToMany;
import com.graphhopper.routing.RoutingAlgorithm;
import com.graphhopper.routing.util.*;
import com.graphhopper.storage.DataAccess;
import com.graphhopper.storage.DAType;
import com.graphhopper.storage.GHDirectory;
import com.graphhopper.storage.Graph;
import com.graphhopper.storage.LevelGraph;
import com.graphhopper.storage.LevelGraphStorage;
import com.graphhopper.util.*;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class prepares the graph for a bidirectional algorithm supporting contraction hierarchies
 * ie. an algorithm returned by createAlgo.
 * <p/>
 * There are several description of contraction hierarchies available. The following is one of the
 * more detailed: http://web.cs.du.edu/~sturtevant/papers/highlevelpathfinding.pdf
 * <p/>
 * The only difference is that we use two skipped edges instead of one skipped node for faster
 * unpacking.
 * <p/>
 * @author Peter Karich
 */
public class PrepareContractionHierarchies extends AbstractAlgoPreparation<PrepareContractionHierarchies>
{
    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final PreparationWeighting prepareWeighting;
    private final FlagEncoder prepareEncoder;
    private EdgeSkipExplorer vehicleInExplorer;
    private EdgeSkipExplorer vehicleOutExplorer;
    private EdgeSkipExplorer vehicleAllExplorer;
    private EdgeSkipExplorer vehicleAllTmpExplorer;
    private EdgeSkipExplorer calcPrioAllExplorer;
    private LevelGraph g;
    // the most important nodes comes last
    private GHTreeMapComposed sortedNodes;
    private int oldPriorities[];
    private final DataAccess originalEdges;
    private final Map<Shortcut, Shortcut> shortcuts = new HashMap<Shortcut, Shortcut>();
    private IgnoreNodeFilter ignoreNodeFilter;
    private DijkstraOneToMany algo;
    private boolean removesHigher2LowerEdges = true;
    private long counter;
    private int newShortcuts;
    private long dijkstraCount;
    private double meanDegree;
    private final Random rand = new Random(123);
    private StopWatch dijkstraSW = new StopWatch();
    private int periodicUpdatesPercentage = 20;
    private int lastNodesLazyUpdatePercentage = 10;
    private final StopWatch allSW = new StopWatch();
    private int neighborUpdatePercentage = 10;
    private int initialCollectionSize = 10000;
    private double logMessagesPercentage = 20;

    public PrepareContractionHierarchies( FlagEncoder encoder, Weighting weighting )
    {
        prepareEncoder = encoder;
        long scFwdDir = encoder.setAccess(0, true, false);

        // shortcuts store weight in flags where we assume bit 1 and 2 are used for access restriction
        if ((scFwdDir & PrepareEncoder.getScFwdDir()) == 0)
            throw new IllegalArgumentException("Currently only one vehicle is supported if you enable CH. "
                    + "It seems that you have imported more than one.");

        prepareWeighting = new PreparationWeighting(weighting);
        originalEdges = new GHDirectory("", DAType.RAM_INT).find("originalEdges");
        originalEdges.create(1000);
    }

    @Override
    public PrepareContractionHierarchies setGraph( Graph g )
    {
        this.g = (LevelGraph) g;
        return this;
    }

    /**
     * The higher the values are the longer the preparation takes but the less shortcuts are
     * produced.
     * <p/>
     * @param periodicUpdates specifies how often periodic updates will happen. Use something less
     * than 10.
     */
    public PrepareContractionHierarchies setPeriodicUpdates( int periodicUpdates )
    {
        if (periodicUpdates < 0)
            throw new IllegalArgumentException("periodicUpdates has to be in [0, 100]. To disable it use 0");

        this.periodicUpdatesPercentage = periodicUpdates;
        return this;
    }

    /**
     * @param lazyUpdates specifies when lazy updates will happen, measured relative to all existing
     * nodes. 100 means always.
     */
    public PrepareContractionHierarchies setLazyUpdates( int lazyUpdates )
    {
        if (lazyUpdates < 0 || lazyUpdates > 100)
            throw new IllegalArgumentException("lazyUpdates has to be in [0, 100], to disable it use 0");

        this.lastNodesLazyUpdatePercentage = lazyUpdates;
        return this;
    }

    /**
     * @param neighborUpdates specifies how often neighbor updates will happen. 100 means always.
     */
    public PrepareContractionHierarchies setNeighborUpdates( int neighborUpdates )
    {
        if (neighborUpdates < 0 || neighborUpdates > 100)
            throw new IllegalArgumentException("neighborUpdates has to be in [0, 100], to disable it use 0");

        this.neighborUpdatePercentage = neighborUpdates;
        return this;
    }

    /**
     * Specifies how often a log message should be printed. Specify something around 20 (20% of the
     * start nodes).
     */
    public PrepareContractionHierarchies setLogMessages( double logMessages )
    {
        this.logMessagesPercentage = logMessages;
        return this;
    }

    /**
     * Disconnect is very important to improve query time and preparation if enabled. It will remove
     * the edge going from the higher level node to the currently contracted one. But the original
     * graph is no longer available, so it is only useful for bidirectional CH algorithms. Default
     * is true.
     */
    public PrepareContractionHierarchies setRemoveHigher2LowerEdges( boolean removeHigher2LowerEdges )
    {
        this.removesHigher2LowerEdges = removeHigher2LowerEdges;
        return this;
    }

    /**
     * While creating an algorithm out of this preparation class 10 000 nodes are assumed which can
     * be too high for your mobile application. E.g. A 500km query only traverses roughly 2000
     * nodes.
     */
    public void setInitialCollectionSize( int initialCollectionSize )
    {
        this.initialCollectionSize = initialCollectionSize;
    }

    @Override
    public PrepareContractionHierarchies doWork()
    {
        checkGraph();
        if (prepareEncoder == null)
            throw new IllegalStateException("No vehicle encoder set.");

        if (prepareWeighting == null)
            throw new IllegalStateException("No weight calculation set.");

        allSW.start();
        super.doWork();

        initFromGraph();
        if (!prepareEdges())
            return this;

        if (!prepareNodes())
            return this;

        contractNodes();
        return this;
    }

    boolean prepareEdges()
    {
        EdgeIterator iter = g.getAllEdges();
        int c = 0;
        while (iter.next())
        {
            c++;
            setOrigEdgeCount(iter.getEdge(), 1);
        }
        return c > 0;
    }

    // TODO we can avoid node level if we store this into a temporary array and 
    // disconnect all edges which goes from higher to lower level
    // uninitialized nodes have a level of 0
    // TODO we could avoid the second storage for skippedEdge as we could store that info into linkB or A if it is disconnected
    boolean prepareNodes()
    {
        int len = g.getNodes();

        for (int node = 0; node < len; node++)
        {
            int priority = oldPriorities[node] = calculatePriority(node);
            sortedNodes.insert(node, priority);
        }

        if (sortedNodes.isEmpty())
            return false;

        return true;
    }

    void contractNodes()
    {
        meanDegree = g.getAllEdges().getMaxId() / g.getNodes();
        int level = 1;
        counter = 0;
        int initSize = sortedNodes.getSize();
        int logSize = (int) Math.round(Math.max(10, sortedNodes.getSize() / 100 * logMessagesPercentage));
        if (logMessagesPercentage == 0)
            logSize = Integer.MAX_VALUE;

        // preparation takes longer but queries are slightly faster with preparation
        // => enable it but call not so often
        boolean periodicUpdate = true;
        StopWatch periodSW = new StopWatch();
        int updateCounter = 0;
        int periodicUpdatesCount = Math.max(10, sortedNodes.getSize() / 100 * periodicUpdatesPercentage);
        if (periodicUpdatesPercentage == 0)
            periodicUpdate = false;

        // disable as preparation is slower and query time does not benefit
        int lastNodesLazyUpdates = lastNodesLazyUpdatePercentage == 0
                ? 0
                : sortedNodes.getSize() / 100 * lastNodesLazyUpdatePercentage;
        StopWatch lazySW = new StopWatch();

        // Recompute priority of uncontracted neighbors.
        // Without neighborupdates preparation is faster but we need them
        // to slightly improve query time. Also if not applied too often it decreases the shortcut number.
        boolean neighborUpdate = true;
        if (neighborUpdatePercentage == 0)
            neighborUpdate = false;

        StopWatch neighborSW = new StopWatch();
        LevelGraphStorage lg = ((LevelGraphStorage) g);
        while (!sortedNodes.isEmpty())
        {
            // periodically update priorities of ALL nodes            
            if (periodicUpdate && counter > 0 && counter % periodicUpdatesCount == 0)
            {
                periodSW.start();
                sortedNodes.clear();
                int len = g.getNodes();
                for (int node = 0; node < len; node++)
                {
                    if (g.getLevel(node) != 0)
                        continue;

                    int priority = oldPriorities[node] = calculatePriority(node);
                    sortedNodes.insert(node, priority);
                }
                periodSW.stop();
                updateCounter++;
                if (sortedNodes.isEmpty())
                    throw new IllegalStateException("Cannot prepare as no unprepared nodes where found. Called preparation twice?");
            }

            if (counter % logSize == 0)
            {
                // TODO necessary?
                System.gc();
                logger.info(Helper.nf(counter) + ", updates:" + updateCounter
                        + ", nodes: " + Helper.nf(sortedNodes.getSize())
                        + ", shortcuts:" + Helper.nf(newShortcuts)
                        + ", dijkstras:" + Helper.nf(dijkstraCount)
                        + ", t(dijk):" + (int) dijkstraSW.getSeconds()
                        + ", t(period):" + (int) periodSW.getSeconds()
                        + ", t(lazy):" + (int) lazySW.getSeconds()
                        + ", t(neighbor):" + (int) neighborSW.getSeconds()
                        + ", meanDegree:" + (long) meanDegree
                        + ", algo:" + algo.getMemoryUsageAsString()
                        + ", " + Helper.getMemInfo());
                dijkstraSW = new StopWatch();
                periodSW = new StopWatch();
                lazySW = new StopWatch();
                neighborSW = new StopWatch();
            }

            counter++;
            int polledNode = sortedNodes.pollKey();
            if (sortedNodes.getSize() < lastNodesLazyUpdates)
            {
                lazySW.start();
                int priority = oldPriorities[polledNode] = calculatePriority(polledNode);
                if (!sortedNodes.isEmpty() && priority > sortedNodes.peekValue())
                {
                    // current node got more important => insert as new value and contract it later
                    sortedNodes.insert(polledNode, priority);
                    lazySW.stop();
                    continue;
                }
                lazySW.stop();
            }

            // contract!            
            newShortcuts += addShortcuts(polledNode);
            g.setLevel(polledNode, level);
            level++;

            EdgeSkipIterator iter = vehicleAllExplorer.setBaseNode(polledNode);
            while (iter.next())
            {
                int nn = iter.getAdjNode();
                if (g.getLevel(nn) != 0)
                    // already contracted no update necessary
                    continue;

                if (neighborUpdate && rand.nextInt(100) < neighborUpdatePercentage)
                {
                    neighborSW.start();
                    int oldPrio = oldPriorities[nn];
                    int priority = oldPriorities[nn] = calculatePriority(nn);
                    if (priority != oldPrio)
                        sortedNodes.update(nn, oldPrio, priority);

                    neighborSW.stop();
                }

                if (removesHigher2LowerEdges)
                    lg.disconnect(vehicleAllTmpExplorer, iter);
            }
        }

        // Preparation works only once so we can release temporary data.
        // The preparation object itself has to be intact to create the algorithm.
        close();
        logger.info("took:" + (int) allSW.stop().getSeconds()
                + ", new shortcuts: " + newShortcuts
                + ", " + prepareWeighting
                + ", " + prepareEncoder
                + ", removeHigher2LowerEdges:" + removesHigher2LowerEdges
                + ", dijkstras:" + dijkstraCount
                + ", t(dijk):" + (int) dijkstraSW.getSeconds()
                + ", t(period):" + (int) periodSW.getSeconds()
                + ", t(lazy):" + (int) lazySW.getSeconds()
                + ", t(neighbor):" + (int) neighborSW.getSeconds()
                + ", meanDegree:" + (long) meanDegree
                + ", initSize:" + initSize
                + ", periodic:" + periodicUpdatesPercentage
                + ", lazy:" + lastNodesLazyUpdatePercentage
                + ", neighbor:" + neighborUpdatePercentage);
    }

    public void close()
    {
        algo.close();
        originalEdges.close();
        sortedNodes = null;
        oldPriorities = null;
    }
    AddShortcutHandler addScHandler = new AddShortcutHandler();
    CalcShortcutHandler calcScHandler = new CalcShortcutHandler();

    interface ShortcutHandler
    {
        void foundShortcut( int u_fromNode, int w_toNode,
                double existingDirectWeight, double distance,
                EdgeIterator outgoingEdges,
                int skippedEdge1, int incomingEdgeOrigCount );

        int getNode();
    }

    class CalcShortcutHandler implements ShortcutHandler
    {
        int node;
        int originalEdgesCount;
        int shortcuts;

        public CalcShortcutHandler setNode( int n )
        {
            node = n;
            originalEdgesCount = 0;
            shortcuts = 0;
            return this;
        }

        @Override
        public int getNode()
        {
            return node;
        }

        @Override
        public void foundShortcut( int u_fromNode, int w_toNode,
                double existingDirectWeight, double distance,
                EdgeIterator outgoingEdges,
                int skippedEdge1, int incomingEdgeOrigCount )
        {
            shortcuts++;
            originalEdgesCount += incomingEdgeOrigCount + getOrigEdgeCount(outgoingEdges.getEdge());
        }
    }

    class AddShortcutHandler implements ShortcutHandler
    {
        int node;

        public AddShortcutHandler()
        {
        }

        @Override
        public int getNode()
        {
            return node;
        }

        public AddShortcutHandler setNode( int n )
        {
            shortcuts.clear();
            node = n;
            return this;
        }

        @Override
        public void foundShortcut( int u_fromNode, int w_toNode,
                double existingDirectWeight, double existingDistSum,
                EdgeIterator outgoingEdges,
                int skippedEdge1, int incomingEdgeOrigCount )
        {
            // FOUND shortcut 
            // but be sure that it is the only shortcut in the collection 
            // and also in the graph for u->w. If existing AND identical weight => update setProperties.
            // Hint: shortcuts are always one-way due to distinct level of every node but we don't
            // know yet the levels so we need to determine the correct direction or if both directions
            // minor improvement: if (shortcuts.containsKey(sc) 
            // then two shortcuts with the same nodes (u<->n.adjNode) exists => check current shortcut against both
            Shortcut sc = new Shortcut(u_fromNode, w_toNode, existingDirectWeight, existingDistSum);
            if (shortcuts.containsKey(sc))
            {
                return;
            } else
            {
                Shortcut tmpSc = new Shortcut(w_toNode, u_fromNode, existingDirectWeight, existingDistSum);
                Shortcut tmpRetSc = shortcuts.get(tmpSc);
                if (tmpRetSc != null)
                {
                    tmpRetSc.flags = PrepareEncoder.getScDirMask();
                    return;
                }
            }

            shortcuts.put(sc, sc);
            sc.skippedEdge1 = skippedEdge1;
            sc.skippedEdge2 = outgoingEdges.getEdge();
            sc.originalEdges = incomingEdgeOrigCount + getOrigEdgeCount(outgoingEdges.getEdge());
        }
    }

    Set<Shortcut> testFindShortcuts( int node )
    {
        findShortcuts(addScHandler.setNode(node));
        return shortcuts.keySet();
    }

    /**
     * Calculates the priority of adjNode v without changing the graph. Warning: the calculated
     * priority must NOT depend on priority(v) and therefor findShortcuts should also not depend on
     * the priority(v). Otherwise updating the priority before contracting in contractNodes() could
     * lead to a slowishor even endless loop.
     */
    int calculatePriority( int v )
    {
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
        EdgeSkipIterator iter = calcPrioAllExplorer.setBaseNode(v);
        while (iter.next())
        {
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
    void findShortcuts( ShortcutHandler sch )
    {
        long tmpDegreeCounter = 0;
        EdgeIterator incomingEdges = vehicleInExplorer.setBaseNode(sch.getNode());
        // collect outgoing nodes (goal-nodes) only once
        while (incomingEdges.next())
        {
            int u_fromNode = incomingEdges.getAdjNode();
            // accept only uncontracted nodes
            if (g.getLevel(u_fromNode) != 0)
                continue;

            double v_u_dist = incomingEdges.getDistance();
            double v_u_weight = prepareWeighting.calcWeight(incomingEdges, true);
            int skippedEdge1 = incomingEdges.getEdge();
            int incomingEdgeOrigCount = getOrigEdgeCount(skippedEdge1);
            // collect outgoing nodes (goal-nodes) only once
            EdgeIterator outgoingEdges = vehicleOutExplorer.setBaseNode(sch.getNode());
            // force fresh maps etc as this cannot be determined by from node alone (e.g. same from node but different avoidNode)
            algo.clear();
            tmpDegreeCounter++;
            while (outgoingEdges.next())
            {
                int w_toNode = outgoingEdges.getAdjNode();
                // add only uncontracted nodes
                if (g.getLevel(w_toNode) != 0 || u_fromNode == w_toNode)
                    continue;

                // Limit weight as ferries or forbidden edges can increase local search too much.
                // If we decrease the correct weight we only explore less and introduce more shortcuts.
                // I.e. no change to accuracy is made.
                double existingDirectWeight = v_u_weight + prepareWeighting.calcWeight(outgoingEdges, false);
                if (Double.isNaN(existingDirectWeight))
                    throw new IllegalStateException("Weighting should never return NaN values"
                            + ", in:" + getCoords(incomingEdges, g) + ", out:" + getCoords(outgoingEdges, g)
                            + ", dist:" + outgoingEdges.getDistance() + ", speed:" + prepareEncoder.getSpeed(outgoingEdges.getFlags()));

                if (existingDirectWeight >= Double.MAX_VALUE)
                    continue;
                double existingDistSum = v_u_dist + outgoingEdges.getDistance();
                algo.setLimitWeight(existingDirectWeight)
                        .setLimitVisitedNodes((int) meanDegree * 100)
                        .setEdgeFilter(ignoreNodeFilter.setAvoidNode(sch.getNode()));

                dijkstraSW.start();
                dijkstraCount++;
                int endNode = algo.findEndNode(u_fromNode, w_toNode);
                dijkstraSW.stop();

                // compare end node as the limit could force dijkstra to finish earlier
                if (endNode == w_toNode && algo.getWeight(endNode) <= existingDirectWeight)
                    // FOUND witness path, so do not add shortcut                
                    continue;

                sch.foundShortcut(u_fromNode, w_toNode,
                        existingDirectWeight, existingDistSum,
                        outgoingEdges,
                        skippedEdge1, incomingEdgeOrigCount);
            }
        }
        if (sch instanceof AddShortcutHandler)
        {
            // sliding mean value when using "*2" => slower changes
            meanDegree = (meanDegree * 2 + tmpDegreeCounter) / 3;
            // meanDegree = (meanDegree + tmpDegreeCounter) / 2;
        }
    }

    /**
     * Introduces the necessary shortcuts for adjNode v in the graph.
     */
    int addShortcuts( int v )
    {
        shortcuts.clear();
        findShortcuts(addScHandler.setNode(v));
        int tmpNewShortcuts = 0;
        NEXT_SC:
        for (Shortcut sc : shortcuts.keySet())
        {
            boolean updatedInGraph = false;
            // check if we need to update some existing shortcut in the graph
            EdgeSkipIterator iter = vehicleOutExplorer.setBaseNode(sc.from);
            while (iter.next())
            {
                if (iter.isShortcut() && iter.getAdjNode() == sc.to
                        && prepareEncoder.canBeOverwritten(iter.getFlags(), sc.flags))
                {
                    if (sc.weight >= prepareWeighting.calcWeight(iter, false))
                        continue NEXT_SC;

                    if (iter.getEdge() == sc.skippedEdge1 || iter.getEdge() == sc.skippedEdge2)
                    {
                        throw new IllegalStateException("Shortcut cannot update itself! " + iter.getEdge()
                                + ", skipEdge1:" + sc.skippedEdge1 + ", skipEdge2:" + sc.skippedEdge2
                                + ", edge " + iter + ":" + getCoords(iter, g)
                                + ", sc:" + sc
                                + ", skippedEdge1: " + getCoords(g.getEdgeProps(sc.skippedEdge1, sc.from), g)
                                + ", skippedEdge2: " + getCoords(g.getEdgeProps(sc.skippedEdge2, sc.to), g)
                                + ", neighbors:" + GHUtility.getNeighbors(iter));
                    }

                    // note: flags overwrite weight => call them first
                    iter.setFlags(sc.flags);
                    iter.setSkippedEdges(sc.skippedEdge1, sc.skippedEdge2);
                    iter.setDistance(sc.dist);
                    iter.setWeight(sc.weight);
                    setOrigEdgeCount(iter.getEdge(), sc.originalEdges);
                    updatedInGraph = true;
                    break;
                }
            }

            if (!updatedInGraph)
            {
                EdgeSkipIterState edgeState = g.shortcut(sc.from, sc.to);
                // note: flags overwrite weight => call them first
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

    String getCoords( EdgeIteratorState e, Graph g )
    {
        int base = e.getBaseNode();
        int adj = e.getAdjNode();
        return base + "->" + adj + " (" + e.getEdge() + "); "
                + g.getLatitude(base) + "," + g.getLongitude(base) + " -> " + g.getLatitude(adj) + "," + g.getLongitude(adj);
    }

    PrepareContractionHierarchies initFromGraph()
    {
        checkGraph();
        vehicleInExplorer = g.createEdgeExplorer(new DefaultEdgeFilter(prepareEncoder, true, false));
        vehicleOutExplorer = g.createEdgeExplorer(new DefaultEdgeFilter(prepareEncoder, false, true));
        vehicleAllExplorer = g.createEdgeExplorer(new DefaultEdgeFilter(prepareEncoder, true, true));
        vehicleAllTmpExplorer = g.createEdgeExplorer(new DefaultEdgeFilter(prepareEncoder, true, true));
        calcPrioAllExplorer = g.createEdgeExplorer(new DefaultEdgeFilter(prepareEncoder, true, true));
        ignoreNodeFilter = new IgnoreNodeFilter(g);
        // Use an alternative to PriorityQueue as it has some advantages: 
        //   1. Gets automatically smaller if less entries are stored => less total RAM used (as Graph is increasing until the end)
        //   2. is slightly faster
        //   but we need additional priorities array to keep old value which is necessary for update method
        sortedNodes = new GHTreeMapComposed();
        oldPriorities = new int[g.getNodes()];
        algo = new DijkstraOneToMany(g, prepareEncoder, prepareWeighting);
        return this;
    }

    public int getShortcuts()
    {
        return newShortcuts;
    }

    static class IgnoreNodeFilter implements EdgeFilter
    {
        int avoidNode;
        LevelGraph graph;

        public IgnoreNodeFilter( LevelGraph g )
        {
            this.graph = g;
        }

        public IgnoreNodeFilter setAvoidNode( int node )
        {
            this.avoidNode = node;
            return this;
        }

        @Override
        public final boolean accept( EdgeIteratorState iter )
        {
            // ignore if it is skipNode or a adjNode already contracted
            int node = iter.getAdjNode();
            return avoidNode != node && graph.getLevel(node) == 0;
        }
    }

    private void setOrigEdgeCount( int index, int value )
    {
        long tmp = (long) index * 4;
        originalEdges.incCapacity(tmp + 4);
        originalEdges.setInt(tmp, value);
    }

    private int getOrigEdgeCount( int index )
    {
        // TODO possible memory usage improvement: avoid storing the value 1 for normal edges (does not change)!
        long tmp = (long) index * 4;
        originalEdges.incCapacity(tmp + 4);
        return originalEdges.getInt(tmp);
    }

    @Override
    public RoutingAlgorithm createAlgo()
    {
        checkGraph();
        // do not change weight within DijkstraBidirectionRef => so use ShortestWeighting
        DijkstraBidirectionRef dijkstrabi = new DijkstraBidirectionRef(g, prepareEncoder, prepareWeighting)
        {
            @Override
            protected void initCollections( int nodes )
            {
                // algorithm with CH does not need that much memory pre allocated
                super.initCollections(Math.min(initialCollectionSize, nodes));
            }

            @Override
            public boolean finished()
            {
                // we need to finish BOTH searches for CH!
                if (finishedFrom && finishedTo)
                    return true;

                // changed also the final finish condition for CH                
                return currFrom.weight >= bestPath.getWeight() && currTo.weight >= bestPath.getWeight();
            }

            @Override
            public void initPath()
            {
                bestPath = new Path4CH(graph, flagEncoder);
            }

            @Override
            public String getName()
            {
                return "dijkstrabiCH";
            }

            @Override
            public String toString()
            {
                return getName() + "|" + prepareWeighting;
            }
        };

        if (!removesHigher2LowerEdges)
            dijkstrabi.setEdgeFilter(new LevelEdgeFilter(g));

        return dijkstrabi;
    }

    public AStarBidirection createAStar()
    {
        checkGraph();
        AStarBidirection astar = new AStarBidirection(g, prepareEncoder, prepareWeighting)
        {
            @Override
            protected void initCollections( int nodes )
            {
                // algorithm with CH does not need that much memory pre allocated
                super.initCollections(Math.min(initialCollectionSize, nodes));
            }

            @Override
            protected boolean finished()
            {
                // we need to finish BOTH searches for CH!
                if (finishedFrom && finishedTo)
                    return true;

                // changed finish condition for CH
                double tmpWeight = bestPath.getWeight() * approximationFactor;
                return currFrom.weight >= tmpWeight && currTo.weight >= tmpWeight;
            }

            @Override
            protected void initPath()
            {
                bestPath = new Path4CH(graph, flagEncoder);
            }

            @Override
            public String getName()
            {
                return "astarbiCH";
            }

            @Override
            public String toString()
            {
                return getName() + "|" + prepareWeighting;
            }
        };

        if (!removesHigher2LowerEdges)
            astar.setEdgeFilter(new LevelEdgeFilter(g));

        return astar;
    }

    private void checkGraph()
    {
        if (g == null)
            throw new NullPointerException("setGraph before usage");
    }

    private static class PriorityNode implements Comparable<PriorityNode>
    {
        int node;
        int priority;

        public PriorityNode( int node, int priority )
        {
            this.node = node;
            this.priority = priority;
        }

        @Override
        public String toString()
        {
            return node + " (" + priority + ")";
        }

        @Override
        public int compareTo( PriorityNode o )
        {
            return priority - o.priority;
        }
    }

    class Shortcut
    {
        int from;
        int to;
        int skippedEdge1;
        int skippedEdge2;
        double dist;
        double weight;
        int originalEdges;
        long flags = PrepareEncoder.getScFwdDir();

        public Shortcut( int from, int to, double weight, double dist )
        {
            this.from = from;
            this.to = to;
            this.weight = weight;
            this.dist = dist;
        }

        @Override
        public int hashCode()
        {
            int hash = 5;
            hash = 23 * hash + from;
            hash = 23 * hash + to;
            return 23 * hash
                    + (int) (Double.doubleToLongBits(this.weight) ^ (Double.doubleToLongBits(this.weight) >>> 32));
        }

        @Override
        public boolean equals( Object obj )
        {
            if (obj == null || getClass() != obj.getClass())
                return false;

            final Shortcut other = (Shortcut) obj;
            if (this.from != other.from || this.to != other.to)
                return false;

            return Double.doubleToLongBits(this.weight) == Double.doubleToLongBits(other.weight);
        }

        @Override
        public String toString()
        {
            String str;
            if (flags == PrepareEncoder.getScDirMask())
                str = from + "<->";
            else
                str = from + "->";

            return str + to + ", weight:" + weight + " (" + skippedEdge1 + "," + skippedEdge2 + ")";
        }
    }
}
