/*
 *  Licensed to Peter Karich under one or more contributor license 
 *  agreements. See the NOTICE file distributed with this work for 
 *  additional information regarding copyright ownership.
 * 
 *  Peter Karich licenses this file to you under the Apache License, 
 *  Version 2.0 (the "License"); you may not use this file except 
 *  in compliance with the License. You may obtain a copy of the 
 *  License at
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

import com.graphhopper.coll.GHSortedCollection;
import com.graphhopper.routing.AStarBidirection;
import com.graphhopper.routing.DijkstraBidirectionRef;
import com.graphhopper.routing.DijkstraOneToMany;
import com.graphhopper.routing.PathBidirRef;
import com.graphhopper.routing.RoutingAlgorithm;
import com.graphhopper.routing.util.AbstractAlgoPreparation;
import com.graphhopper.routing.util.CarFlagEncoder;
import com.graphhopper.routing.util.DefaultEdgeFilter;
import com.graphhopper.routing.util.EdgeFilter;
import com.graphhopper.routing.util.LevelEdgeFilter;
import com.graphhopper.routing.util.VehicleEncoder;
import com.graphhopper.routing.util.ShortestCalc;
import com.graphhopper.routing.util.WeightCalculation;
import com.graphhopper.storage.DataAccess;
import com.graphhopper.storage.Graph;
import com.graphhopper.storage.LevelGraph;
import com.graphhopper.storage.LevelGraphStorage;
import com.graphhopper.storage.RAMDirectory;
import com.graphhopper.util.EdgeIterator;
import com.graphhopper.util.EdgeSkipIterator;
import com.graphhopper.util.Helper;
import com.graphhopper.util.StopWatch;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class prepares the graph for a bidirectional algorithm supporting
 * contraction hierarchies ie. an algorithm returned by createAlgo.
 *
 * There are several description of contraction hierarchies available. The
 * following is one of the more detailed:
 * http://web.cs.du.edu/~sturtevant/papers/highlevelpathfinding.pdf
 *
 * The only difference is that we use two skipped edges instead of one skipped
 * node for faster unpacking.
 *
 * @author Peter Karich
 */
public class PrepareContractionHierarchies extends AbstractAlgoPreparation<PrepareContractionHierarchies> {

    private final Logger logger = LoggerFactory.getLogger(getClass());
    // preparation dijkstra uses always shortest path as edges are rewritten - see doWork
    private final WeightCalculation shortestCalc = new ShortestCalc();
    private WeightCalculation prepareWeightCalc;
    private VehicleEncoder prepareEncoder;
    private EdgeFilter vehicleInFilter;
    private EdgeFilter vehicleOutFilter;
    private EdgeFilter vehicleAllFilter;
    private LevelGraph g;
    // the most important nodes comes last
    private GHSortedCollection sortedNodes;
    private PriorityNode refs[];
    private DataAccess originalEdges;
    // shortcut is one direction, speed is only involved while recalculating the endNode weights - see prepareEdges
    private int scOneDir;
    private int scBothDir;
    private Map<Shortcut, Shortcut> shortcuts = new HashMap<Shortcut, Shortcut>();
    private LevelEdgeFilterCH levelEdgeFilter;
    private DijkstraOneToMany algo;
    private int updateSize;
    private boolean removesHigher2LowerEdges = true;
    private long counter;
    private int newShortcuts;
    private long dijkstraCount;
    private Random rand = new Random(123);
    private StopWatch dijkstraSW = new StopWatch();

    public PrepareContractionHierarchies() {
        type(new ShortestCalc()).vehicle(new CarFlagEncoder());
        originalEdges = new RAMDirectory().findCreate("originalEdges");
        originalEdges.create(1000);        
    }

    @Override
    public PrepareContractionHierarchies graph(Graph g) {
        this.g = (LevelGraph) g;
        return this;
    }

    int scBothDir() {
        return scBothDir;
    }

    int scOneDir() {
        return scOneDir;
    }

    public PrepareContractionHierarchies type(WeightCalculation weightCalc) {
        prepareWeightCalc = weightCalc;
        return this;
    }

    public PrepareContractionHierarchies vehicle(VehicleEncoder encoder) {
        this.prepareEncoder = encoder;
        vehicleInFilter = new DefaultEdgeFilter(encoder, true, false);
        vehicleOutFilter = new DefaultEdgeFilter(encoder, false, true);
        vehicleAllFilter = new DefaultEdgeFilter(encoder, true, true);
        scOneDir = encoder.flags(0, false);
        scBothDir = encoder.flags(0, true);
        return this;
    }

    public PrepareContractionHierarchies updateSize(int updateSize) {
        this.updateSize = updateSize;
        return this;
    }

    /**
     * Disconnect is very important to improve query time and preparation if
     * enabled. It will remove the edge going from the higher level node to the
     * currently contracted one. But the original graph is no longer available,
     * so it is only useful for bidirectional CH algorithms. Default is true.
     */
    public PrepareContractionHierarchies removeHigher2LowerEdges(boolean removeHigher2LowerEdges) {
        this.removesHigher2LowerEdges = removeHigher2LowerEdges;
        return this;
    }

    @Override
    public PrepareContractionHierarchies doWork() {
        super.doWork();
        initFromGraph();
        if (!prepareEdges())
            return this;

        if (!prepareNodes())
            return this;
        contractNodes();
        return this;
    }

    boolean prepareEdges() {
        // In CH the flags (speed) are ignored as calculating the new flags for a shortcut is often not possible.
        // Also several shortcuts would be necessary with the different modes (e.g. fastest and shortest)
        // So calculate the weight and store this as distance, then use only distance instead of getWeight
        EdgeIterator iter = g.getAllEdges();
        int c = 0;
        while (iter.next()) {
            c++;
            iter.distance(prepareWeightCalc.getWeight(iter.distance(), iter.flags()));
            setOrigEdgeCount(iter.edge(), 1);
        }
        return c > 0;
    }

    // TODO we can avoid node level if we store this into a temporary array and 
    // disconnect all edges which goes from higher to lower level
    // uninitialized nodes have a level of 0
    // TODO we could avoid the second storage for skippedEdge as we could store that info into linkB or A if it is disconnected
    boolean prepareNodes() {
        int len = g.nodes();
        for (int node = 0; node < len; node++) {
            refs[node] = new PriorityNode(node, 0);
        }

        for (int node = 0; node < len; node++) {
            PriorityNode wn = refs[node];
            wn.priority = calculatePriority(node);
            sortedNodes.insert(wn.node, wn.priority);
        }

        if (sortedNodes.isEmpty())
            return false;
        return true;
    }

    void contractNodes() {
        int level = 1;
        counter = 0;
        if (updateSize <= 0)
            updateSize = Math.max(10, sortedNodes.size() / 10);

        int updateCounter = 0;
        StopWatch updateSW = new StopWatch();

        // preparation takes longer but queries are slightly faster with preparation
        // => enable it but call not so often
        boolean periodicUpdate = true;

        // disable as preparation is slower and query time does not benefit
        boolean lazyUpdate = true;

        // Recompute priority of uncontracted neighbors.
        // Without neighborupdates preparation is faster but we need them
        // to slightly improve query time. Also if not applied too often it decreases the shortcut number.
        boolean neighborUpdate = true;
        while (!sortedNodes.isEmpty()) {
            if (counter % updateSize == 0) {
                // periodically update priorities of ALL nodes            
                if (periodicUpdate && updateCounter > 0 && updateCounter % 5 == 0) {
                    int len = g.nodes();
                    updateSW.start();
                    // TODO avoid to traverse all nodes -> via a new sortedNodes.iterator()
                    for (int node = 0; node < len; node++) {
                        PriorityNode pNode = refs[node];
                        if (g.getLevel(node) != 0)
                            continue;
                        int old = pNode.priority;
                        pNode.priority = calculatePriority(node);
                        sortedNodes.update(node, old, pNode.priority);
                    }
                    updateSW.stop();
                }
                updateCounter++;
                logger.info(counter + ", nodes: " + sortedNodes.size() + ", shortcuts:" + newShortcuts
                        + ", updateAllTime:" + updateSW.getSeconds()
                        + ", dijkstras:" + dijkstraCount
                        + ", dijkstraTime:" + dijkstraSW.getSeconds()
                        + ", " + updateCounter
                        + ", memory:" + Helper.memInfo());
            }

            counter++;
            PriorityNode wn = refs[sortedNodes.pollKey()];
            if (lazyUpdate) {
                wn.priority = calculatePriority(wn.node);
                if (!sortedNodes.isEmpty() && wn.priority > sortedNodes.peekValue()) {
                    // current node got more important => insert as new value and contract it later
                    sortedNodes.insert(wn.node, wn.priority);
                    continue;
                }
            }

            // contract!            
            newShortcuts += addShortcuts(wn.node);
            g.setLevel(wn.node, level);
            level++;

            EdgeIterator iter = g.getEdges(wn.node, vehicleAllFilter);
            while (iter.next()) {
                int nn = iter.adjNode();
                if (g.getLevel(nn) != 0)
                    // already contracted no update necessary
                    continue;

                if (neighborUpdate && rand.nextInt(50) < 5) {
                    PriorityNode neighborWn = refs[nn];
                    int oldPrio = neighborWn.priority;
                    neighborWn.priority = calculatePriority(nn);
                    if (neighborWn.priority != oldPrio)
                        sortedNodes.update(nn, oldPrio, neighborWn.priority);
                }

                if (removesHigher2LowerEdges)
                    ((LevelGraphStorage) g).disconnect(iter, EdgeIterator.NO_EDGE, false);
            }
        }

        logger.info("new shortcuts " + newShortcuts + ", " + prepareWeightCalc + ", " + prepareEncoder
                + ", removeHigher2LowerEdges:" + removesHigher2LowerEdges
                + ", dijkstras:" + dijkstraCount
                + ", dijkstraTime:" + dijkstraSW.getSeconds());
    }
    AddShortcutHandler addScHandler = new AddShortcutHandler();
    CalcShortcutHandler calcScHandler = new CalcShortcutHandler();

    interface ShortcutHandler {

        void foundShortcut(int u_fromNode, int w_toNode,
                double existingDirectWeight, EdgeIterator outgoingEdges,
                int skippedEdge1, int incomingEdgeOrigCount);

        int node();
    }

    class CalcShortcutHandler implements ShortcutHandler {

        int node;
        int originalEdgesCount;
        int shortcuts;

        public CalcShortcutHandler node(int n) {
            node = n;
            originalEdgesCount = 0;
            shortcuts = 0;
            return this;
        }

        @Override public int node() {
            return node;
        }

        @Override public void foundShortcut(int u_fromNode, int w_toNode,
                double existingDirectWeight, EdgeIterator outgoingEdges,
                int skippedEdge1, int incomingEdgeOrigCount) {
            shortcuts++;
            originalEdgesCount += incomingEdgeOrigCount + getOrigEdgeCount(outgoingEdges.edge());
        }
    }

    class AddShortcutHandler implements ShortcutHandler {

        int node;

        public AddShortcutHandler() {
        }

        @Override public int node() {
            return node;
        }

        public AddShortcutHandler node(int n) {
            shortcuts.clear();
            node = n;
            return this;
        }

        @Override public void foundShortcut(int u_fromNode, int w_toNode,
                double existingDirectWeight, EdgeIterator outgoingEdges,
                int skippedEdge1, int incomingEdgeOrigCount) {

            // FOUND shortcut 
            // but be sure that it is the only shortcut in the collection 
            // and also in the graph for u->w. If existing AND identical length => update flags.
            // Hint: shortcuts are always one-way due to distinct level of every node but we don't
            // know yet the levels so we need to determine the correct direction or if both directions

            // minor improvement: if (shortcuts.containsKey(sc) 
            // then two shortcuts with the same nodes (u<->n.endNode) exists => check current shortcut against both

            Shortcut sc = new Shortcut(u_fromNode, w_toNode, existingDirectWeight);
            if (shortcuts.containsKey(sc))
                return;
            else {
                Shortcut tmpSc = new Shortcut(w_toNode, u_fromNode, existingDirectWeight);
                Shortcut tmpRetSc = shortcuts.get(tmpSc);
                if (tmpRetSc != null) {
                    tmpRetSc.flags = scBothDir;
                    return;
                }
            }

            shortcuts.put(sc, sc);
            sc.skippedEdge1 = skippedEdge1;
            sc.skippedEdge2 = outgoingEdges.edge();
            sc.originalEdges = incomingEdgeOrigCount + getOrigEdgeCount(outgoingEdges.edge());
        }
    }

    Set<Shortcut> testFindShortcuts(int node) {
        findShortcuts(addScHandler.node(node));
        return shortcuts.keySet();
    }

    /**
     * Calculates the priority of endNode v without changing the graph. Warning:
     * the calculated priority must NOT depend on priority(v) and therefor
     * findShortcuts should also not depend on the priority(v). Otherwise
     * updating the priority before contracting in contractNodes() could lead to
     * a slowishor even endless loop.
     */
    int calculatePriority(int v) {
        // set of shortcuts that would be added if endNode v would be contracted next.
        findShortcuts(calcScHandler.node(v));

//        System.out.println(v + "\t " + tmpShortcuts);
        // # huge influence: the bigger the less shortcuts gets created and the faster is the preparation
        //
        // every endNode has an 'original edge' number associated. initially it is r=1
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
        EdgeSkipIterator iter = g.getEdges(v, vehicleAllFilter);
        while (iter.next()) {
            degree++;
            if (iter.isShortcut())
                contractedNeighbors++;
        }

        // from shortcuts we can compute the edgeDifference
        // # low influence: with it the shortcut creation is slightly faster
        //
        // |shortcuts(v)| − |{(u, v) | v uncontracted}| − |{(v, w) | v uncontracted}|        
        // meanDegree is used instead of outDegree+inDegree as if one endNode is in both directions
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
        EdgeIterator incomingEdges = g.getEdges(sch.node(), vehicleInFilter);
        // collect outgoing nodes (goal-nodes) only once
        while (incomingEdges.next()) {
            int u_fromNode = incomingEdges.adjNode();
            // accept only uncontracted nodes
            if (g.getLevel(u_fromNode) != 0)
                continue;

            double v_u_weight = incomingEdges.distance();
            int skippedEdge1 = incomingEdges.edge();
            int incomingEdgeOrigCount = getOrigEdgeCount(skippedEdge1);
            // collect outgoing nodes (goal-nodes) only once
            EdgeIterator outgoingEdges = g.getEdges(sch.node(), vehicleOutFilter);
            // force fresh maps etc as this cannot be determined by from node alone (e.g. same from node but different avoidNode)
            algo.clear();
            while (outgoingEdges.next()) {
                int w_toNode = outgoingEdges.adjNode();
                // add only uncontracted nodes
                if (g.getLevel(w_toNode) != 0 || u_fromNode == w_toNode)
                    continue;

                double existingDirectWeight = v_u_weight + outgoingEdges.distance();
                algo.limit(existingDirectWeight).edgeFilter(levelEdgeFilter.avoidNode(sch.node()));

                dijkstraSW.start();
                dijkstraCount++;
                int endNode = algo.findEndNode(u_fromNode, w_toNode);
                dijkstraSW.stop();

                // compare end node as the limit could force dijkstra to finish earlier
                if (endNode == w_toNode && algo.weight(endNode) <= existingDirectWeight)
                    // FOUND witness path, so do not add shortcut
                    continue;

                sch.foundShortcut(u_fromNode, w_toNode, existingDirectWeight,
                        outgoingEdges, skippedEdge1, incomingEdgeOrigCount);
            }
        }
    }

    /**
     * Introduces the necessary shortcuts for endNode v in the graph.
     */
    int addShortcuts(int v) {
        shortcuts.clear();
        findShortcuts(addScHandler.node(v));
        int tmpNewShortcuts = 0;
        for (Shortcut sc : shortcuts.keySet()) {
            boolean updatedInGraph = false;
            // check if we need to update some existing shortcut in the graph
            EdgeSkipIterator iter = g.getEdges(sc.from, vehicleOutFilter);
            while (iter.next()) {
                if (iter.isShortcut() && iter.adjNode() == sc.to
                        && prepareEncoder.canBeOverwritten(iter.flags(), sc.flags)
                        && iter.distance() > sc.distance) {
                    iter.flags(sc.flags);
                    iter.skippedEdges(sc.skippedEdge1, sc.skippedEdge2);
                    iter.distance(sc.distance);
                    setOrigEdgeCount(iter.edge(), sc.originalEdges);
                    updatedInGraph = true;
                    break;
                }
            }

            if (!updatedInGraph) {
                iter = g.edge(sc.from, sc.to, sc.distance, sc.flags);
                iter.skippedEdges(sc.skippedEdge1, sc.skippedEdge2);
                setOrigEdgeCount(iter.edge(), sc.originalEdges);
                tmpNewShortcuts++;
            }
        }
        return tmpNewShortcuts;
    }

    PrepareContractionHierarchies initFromGraph() {
        levelEdgeFilter = new LevelEdgeFilterCH(this.g);
        sortedNodes = new GHSortedCollection(g.nodes());
        refs = new PriorityNode[g.nodes()];
        algo = new DijkstraOneToMany(g, prepareEncoder);
        algo.type(shortestCalc);
        return this;
    }

    public int shortcuts() {
        return newShortcuts;
    }

    static class LevelEdgeFilterCH extends LevelEdgeFilter {

        int avoidNode;
        LevelGraph g;

        public LevelEdgeFilterCH(LevelGraph g) {
            super(g);
        }

        public LevelEdgeFilterCH avoidNode(int node) {
            this.avoidNode = node;
            return this;
        }

        @Override public final boolean accept(EdgeIterator iter) {
            if (!super.accept(iter))
                return false;
            // ignore if it is skipNode or a endNode already contracted
            int node = iter.adjNode();
            return avoidNode != node && graph.getLevel(node) == 0;
        }
    }

    private void setOrigEdgeCount(int index, int value) {
        originalEdges.ensureCapacity(index * 4 + 4);
        originalEdges.setInt(index, value);
    }

    private int getOrigEdgeCount(int index) {
        originalEdges.ensureCapacity(index * 4 + 4);
        return originalEdges.getInt(index);
    }

    @Override
    public RoutingAlgorithm createAlgo() {
        // do not change weight within DijkstraBidirectionRef => so use ShortestCalc
        DijkstraBidirectionRef dijkstra = new DijkstraBidirectionRef(g, prepareEncoder) {
            @Override protected void initCollections(int nodes) {
                // algorithm with CH does not need that much memory pre allocated
                super.initCollections(Math.min(10000, nodes));
            }

            @Override public boolean checkFinishCondition() {
                // changed finish condition for CH
                if (currFrom == null)
                    return currTo.weight >= shortest.weight();
                else if (currTo == null)
                    return currFrom.weight >= shortest.weight();
                return currFrom.weight >= shortest.weight() && currTo.weight >= shortest.weight();
            }

            @Override public RoutingAlgorithm type(WeightCalculation wc) {
                // allow only initial configuration
                if (super.weightCalc != null)
                    throw new IllegalStateException("You'll need to change weightCalculation of preparation instead of algorithm!");
                return super.type(wc);
            }

            @Override protected PathBidirRef createPath() {
                // CH changes the distance in prepareEdges to the weight
                // now we need to transform it back to the real distance
                WeightCalculation wc = createWeightCalculation();
                return new Path4CH(graph, flagEncoder, wc);
            }

            @Override public String name() {
                return "dijkstraCH";
            }
        };
        if (!removesHigher2LowerEdges)
            dijkstra.edgeFilter(new LevelEdgeFilter(g));
        return dijkstra;
    }

    public RoutingAlgorithm createAStar() {
        AStarBidirection astar = new AStarBidirection(g, prepareEncoder) {
            @Override protected void initCollections(int nodes) {
                // algorithm with CH does not need that much memory pre allocated
                super.initCollections(Math.min(10000, nodes));
            }

            @Override public boolean checkFinishCondition() {
                // changed finish condition for CH
                double tmpWeight = shortest.weight() * approximationFactor;
                if (currFrom == null)
                    return currTo.weight >= tmpWeight;
                else if (currTo == null)
                    return currFrom.weight >= tmpWeight;
                return currFrom.weight >= tmpWeight && currTo.weight >= tmpWeight;
            }

            @Override public RoutingAlgorithm type(WeightCalculation wc) {
                // allow only initial configuration
                if (weightCalc != null)
                    throw new IllegalStateException("You'll need to change weightCalculation of preparation instead of algorithm!");
                return super.type(wc);
            }

            @Override protected PathBidirRef createPath() {
                // CH changes the distance in prepareEdges to the weight
                // now we need to transform it back to the real distance
                WeightCalculation wc = createWeightCalculation();
                return new Path4CH(graph, flagEncoder, wc);
            }

            @Override public String name() {
                return "astarCH";
            }
        };
        if (!removesHigher2LowerEdges)
            astar.edgeFilter(new LevelEdgeFilter(g));
        return astar;
    }

    WeightCalculation createWeightCalculation() {
        return new WeightCalculation() {
            @Override public String toString() {
                return "INVERSE";
            }

            @Override public double getMinWeight(double distance) {
                throw new IllegalStateException("getMinWeight not supported yet");
            }

            @Override public double getWeight(double distance, int flags) {
                return distance;
            }

            @Override public double revertWeight(double weight, int flags) {
                return prepareWeightCalc.revertWeight(weight, flags);
            }
        };
    }

    private static class PriorityNode {

        int node;
        int priority;

        public PriorityNode(int node, int priority) {
            this.node = node;
            this.priority = priority;
        }

        @Override public String toString() {
            return node + " (" + priority + ")";
        }
    }

    class Shortcut {

        int from;
        int to;
        int skippedEdge1;
        int skippedEdge2;
        double distance;
        int originalEdges;
        int flags = scOneDir;

        public Shortcut(int from, int to, double dist) {
            this.from = from;
            this.to = to;
            this.distance = dist;
        }

        @Override
        public int hashCode() {
            int hash = 5;
            hash = 23 * hash + from;
            hash = 23 * hash + to;
            return 23 * hash
                    + (int) (Double.doubleToLongBits(this.distance) ^ (Double.doubleToLongBits(this.distance) >>> 32));
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == null || getClass() != obj.getClass())
                return false;
            final Shortcut other = (Shortcut) obj;
            if (this.from != other.from || this.to != other.to)
                return false;

            return Double.doubleToLongBits(this.distance) == Double.doubleToLongBits(other.distance);
        }

        @Override public String toString() {
            return from + "->" + to + ", dist:" + distance;
        }
    }
}
