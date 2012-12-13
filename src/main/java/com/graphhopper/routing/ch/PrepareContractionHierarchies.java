/*
 *  Copyright 2012 Peter Karich
 * 
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
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

import com.graphhopper.coll.MySortedCollection;
import com.graphhopper.routing.DijkstraBidirectionRef;
import com.graphhopper.routing.DijkstraSimple;
import com.graphhopper.routing.Path;
import com.graphhopper.routing.PathBidirRef;
import com.graphhopper.routing.RoutingAlgorithm;
import com.graphhopper.routing.util.AbstractAlgoPreparation;
import com.graphhopper.routing.util.CarStreetType;
import com.graphhopper.routing.util.EdgeLevelFilter;
import com.graphhopper.routing.util.ShortestCarCalc;
import com.graphhopper.routing.util.WeightCalculation;
import com.graphhopper.storage.EdgeEntry;
import com.graphhopper.storage.Graph;
import com.graphhopper.storage.LevelGraph;
import com.graphhopper.util.EdgeIterator;
import com.graphhopper.util.EdgeSkipIterator;
import com.graphhopper.util.EdgeWriteIterator;
import com.graphhopper.util.GraphUtility;
import com.graphhopper.util.Helper;
import com.graphhopper.util.NumHelper;
import com.graphhopper.util.StopWatch;
import gnu.trove.list.array.TIntArrayList;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class prepares the graph for a bidirectional algorithm supporting contraction hierarchies
 * ie. an algorithm returned by createAlgo.
 *
 * There are several description of contraction hierarchies available. The following is one of the
 * more detailed: http://web.cs.du.edu/~sturtevant/papers/highlevelpathfinding.pdf
 *
 * The only difference is that we use a skipped edgeId instead of skipped nodes for faster
 * unpacking.
 *
 * @author Peter Karich
 */
public class PrepareContractionHierarchies extends AbstractAlgoPreparation<PrepareContractionHierarchies> {

    private Logger logger = LoggerFactory.getLogger(getClass());
    private WeightCalculation prepareWeightCalc;
    private LevelGraph g;
    // the most important nodes comes last
    private MySortedCollection sortedNodes;
    private WeightedNode refs[];
    private TIntArrayList originalEdges;
    // shortcut is one direction, speed is only involved while recalculating the endNode weights - see prepareEdges
    static final int scOneDir = CarStreetType.flags(0, false);
    static final int scBothDir = CarStreetType.flags(0, true);
    private Map<Long, Shortcut> shortcuts = new HashMap<Long, Shortcut>();
    private EdgeLevelFilterCH edgeFilter;
    private OneToManyDijkstraCH algo;

    public PrepareContractionHierarchies() {
        prepareWeightCalc = ShortestCarCalc.DEFAULT;
    }

    @Override
    public PrepareContractionHierarchies setGraph(Graph g) {
        this.g = (LevelGraph) g;
        return this;
    }

    public PrepareContractionHierarchies setType(WeightCalculation weightCalc) {
        this.prepareWeightCalc = weightCalc;
        return this;
    }

    @Override
    public PrepareContractionHierarchies doWork() {
        super.doWork();
        initFromGraph();
        // TODO integrate PrepareRoutingShortcuts -> so avoid all nodes with negative level in the other methods        
        // in PrepareShortcuts level 0 and -1 is already used move that to level 1 and 2 so that level 0 stays as uncontracted
        if (!prepareEdges())
            return this;

        if (!prepareNodes())
            return this;
        contractNodes();
        return this;
    }

    boolean prepareEdges() {
        // in CH the flags will be ignored (calculating the new flags for the shortcuts is impossible)
        // also several shortcuts would be necessary with the different modes (e.g. fastest and shortest)
        // so calculate the weight and store this as distance, then use only distance instead of getWeight
        EdgeWriteIterator iter = g.getAllEdges();
        int c = 0;
        while (iter.next()) {
            c++;
            iter.distance(prepareWeightCalc.getWeight(iter.distance(), iter.flags()));
            setOrigEdges(iter.edge(), 1);
        }
        return c > 0;
    }

    boolean prepareNodes() {
        int len = g.getNodes();
        // minor idea: 1. sort nodes randomly and 2. pre-init with endNode degree
        for (int node = 0; node < len; node++) {
            refs[node] = new WeightedNode(node, 0);
        }

        for (int node = 0; node < len; node++) {
            WeightedNode wn = refs[node];
            wn.priority = calculatePriority(node);
            sortedNodes.insert(wn.node, wn.priority);
        }

        if (sortedNodes.isEmpty())
            return false;

        return true;
    }

    void contractNodes() {
        int level = 1;
        int newShortcuts = 0;
        final int updateSize = Math.max(10, sortedNodes.size() / 10);
        int counter = 0;
        int updateCounter = 0;
        StopWatch sw = new StopWatch();
        // no update all => 600k shortcuts and 3min
        while (!sortedNodes.isEmpty()) {
            if (counter % updateSize == 0) {
                // periodically update priorities of ALL nodes            
                if (updateCounter > 0 && updateCounter % 2 == 0) {
                    int len = g.getNodes();
                    sw.start();
                    // TODO avoid to traverse all nodes -> via a new sortedNodes.iterator()
                    for (int node = 0; node < len; node++) {
                        WeightedNode wNode = refs[node];
                        if (g.getLevel(node) != 0)
                            continue;
                        int old = wNode.priority;
                        wNode.priority = calculatePriority(node);
                        sortedNodes.update(node, old, wNode.priority);
                    }
                    sw.stop();
                }
                updateCounter++;
                logger.info(counter + ", nodes: " + sortedNodes.size() + ", shortcuts:" + newShortcuts
                        + ", updateAllTime:" + sw.getSeconds() + ", " + updateCounter
                        + ", memory:" + Helper.getMemInfo());
            }

            counter++;
            WeightedNode wn = refs[sortedNodes.pollKey()];

            // update priority of current endNode via simulating 'addShortcuts'
            wn.priority = calculatePriority(wn.node);
            if (!sortedNodes.isEmpty() && wn.priority > sortedNodes.peekValue()) {
                // endNode got more important => insert as new value and contract it later
                sortedNodes.insert(wn.node, wn.priority);
                continue;
            }

            // contract!            
            newShortcuts += addShortcuts(wn.node);
            g.setLevel(wn.node, level);
            level++;

            // recompute priority of uncontracted neighbors
            EdgeIterator iter = g.getEdges(wn.node);
            while (iter.next()) {
                if (g.getLevel(iter.node()) != 0)
                    // already contracted no update necessary
                    continue;

                int nn = iter.node();
                WeightedNode neighborWn = refs[nn];
                int tmpOld = neighborWn.priority;
                neighborWn.priority = calculatePriority(nn);
                if (neighborWn.priority != tmpOld) {
                    sortedNodes.update(nn, tmpOld, neighborWn.priority);
                }
            }
        }
        logger.info("new shortcuts " + newShortcuts + ", " + prepareWeightCalc + ", prioNodeCollection:" + sortedNodes);
        // System.out.println("new shortcuts " + newShortcuts);
    }

    /**
     * Calculates the priority of endNode v without changing the graph. Warning: the calculated
     * priority must NOT depend on priority(v) and therefor findShortcuts should also not depend on
     * the priority(v). Otherwise updating the priority before contracting in contractNodes() could
     * lead to a slowishor even endless loop.
     */
    int calculatePriority(int v) {
        // set of shortcuts that would be added if endNode v would be contracted next.
        Collection<Shortcut> tmpShortcuts = findShortcuts(v);
        // from shortcuts we can compute the edgeDifference

        // # low influence: with it the shortcut creation is slightly faster
        //
        // |shortcuts(v)| − |{(u, v) | v uncontracted}| − |{(v, w) | v uncontracted}|        
        // meanDegree is used instead of outDegree+inDegree as if one endNode is in both directions
        // only one bucket memory is used. Additionally one shortcut could also stand for two directions.
        int degree = GraphUtility.count(g.getEdges(v));
        int edgeDifference = tmpShortcuts.size() - degree;

        // # huge influence: the bigger the less shortcuts gets created and the faster is the preparation
        //
        // every endNode has an 'original edge' number associated. initially it is r=1
        // when a new shortcut is introduced then r of the associated edges is summed up:
        // r(u,w)=r(u,v)+r(v,w) now we can define
        // originalEdgesCount = σ(v) := sum_{ (u,w) ∈ shortcuts(v) } of r(u, w)
        int originalEdgesCount = 0;
        for (Shortcut sc : tmpShortcuts) {
            originalEdgesCount += sc.originalEdges;
        }

        // # lowest influence on preparation speed or shortcut creation count 
        // (but according to paper should speed up queries)
        //
        // number of already contracted neighbors of v
        int contractedNeighbors = 0;
        EdgeSkipIterator iter = g.getEdges(v);
        while (iter.next()) {
            if (iter.skippedEdge() >= 0)
                contractedNeighbors++;
        }

        // unterfranken example
        // 10, 50, 1 => 180s preparation, q 3.3ms
        //  2,  4, 1 => 200s preparation, q 3.0ms
        // according to the paper do a simple linear combination of the properties to get the priority
        return 10 * edgeDifference + 50 * originalEdgesCount + contractedNeighbors;
    }

    Map<Long, Shortcut> getShortcuts() {
        return shortcuts;
    }

    PrepareContractionHierarchies initFromGraph() {
        originalEdges = new TIntArrayList(g.getNodes() / 2, -1);
        edgeFilter = new EdgeLevelFilterCH(this.g);
        sortedNodes = new MySortedCollection(g.getNodes());
        refs = new WeightedNode[g.getNodes()];
        return this;
    }

    static class EdgeLevelFilterCH extends EdgeLevelFilter {

        int skipNode;

        public EdgeLevelFilterCH(LevelGraph g) {
            super(g);
        }

        public EdgeLevelFilterCH setSkipNode(int skipNode) {
            this.skipNode = skipNode;
            return this;
        }

        @Override public boolean accept() {
            // ignore if it is skipNode or a endNode already contracted
            return skipNode != node() && graph.getLevel(node()) == 0;
        }
    }

    /**
     * Finds shortcuts, does not change the underlying graph.
     */
    Collection<Shortcut> findShortcuts(int v) {
        // we can use distance instead of weight, see prepareEdges where distance is overwritten by weight!
        List<NodeCH> goalNodes = new ArrayList<NodeCH>();
        shortcuts.clear();
        EdgeWriteIterator iter1 = g.getIncoming(v);
        // TODO PERFORMANCE collect outgoing nodes (goalnodes) only once and just skip u
        while (iter1.next()) {
            int u = iter1.node();
            int lu = g.getLevel(u);
            if (lu != 0)
                continue;

            double v_u_weight = iter1.distance();

            // one-to-many shortest path
            goalNodes.clear();
            EdgeWriteIterator iter2 = g.getOutgoing(v);
            double maxWeight = 0;
            while (iter2.next()) {
                int w = iter2.node();
                int lw = g.getLevel(w);
                if (w == u || lw != 0)
                    continue;

                NodeCH n = new NodeCH();
                n.endNode = w;
                n.originalEdges = getOrigEdges(iter2.edge());
                n.distance = v_u_weight + iter2.distance();
                goalNodes.add(n);

                if (maxWeight < n.distance)
                    maxWeight = n.distance;
            }

            if (goalNodes.isEmpty())
                continue;

            // TODO instead of a weight-limit we could use a hop-limit 
            // and successively increasing it when mean-degree of graph increases
            algo = new OneToManyDijkstraCH(g).setFilter(edgeFilter.setSkipNode(v));
            algo.setLimit(maxWeight).calcPath(u, goalNodes);
            internalFindShortcuts(goalNodes, u, iter1.edge());
        }
        return shortcuts.values();
    }

    void internalFindShortcuts(List<NodeCH> goalNodes, int u, int uEdgeId) {
        int uOrigEdge = getOrigEdges(uEdgeId);
        for (NodeCH n : goalNodes) {
            if (n.entry != null) {
                Path path = algo.extractPath(n.entry);
                if (path.found() && path.weight() <= n.distance) {
                    // FOUND witness path, so do not add shortcut
                    continue;
                }
            }

            // FOUND shortcut but be sure that it is the only shortcut in the collection 
            // and also in the graph for u->w. If existing AND identical length => update flags
            // Hint: shortcuts are always one-way due to distinct level of every endNode but we don't
            // know yet the levels so we need to determine the correct direction or if both directions
            long edgeId = (long) u * refs.length + n.endNode;
            Shortcut sc = shortcuts.get(edgeId);
            if (sc == null)
                sc = shortcuts.get((long) n.endNode * refs.length + u);

            // minor improvement: if (shortcuts.containsKey((long) n.endNode * refs.length + u)) 
            // then two shortcuts with the same nodes (u<->n.endNode) exists => check current shortcut against both
            if (sc == null || !NumHelper.equals(sc.distance, n.distance)) {
                sc = new Shortcut(u, n.endNode, n.distance);
                shortcuts.put(edgeId, sc);
                sc.edge = uEdgeId;
                sc.originalEdges = uOrigEdge + n.originalEdges;
            } else {
                // the shortcut already exists in the current collection (different direction)
                // but has identical length so change the flags!
                sc.flags = scBothDir;
            }
        }
    }

    /**
     * Introduces the necessary shortcuts for endNode v in the graph.
     */
    int addShortcuts(int v) {
        Collection<Shortcut> foundShortcuts = findShortcuts(v);
//        System.out.println("contract:" + refs[v] + ", scs:" + shortcuts);
        int newShorts = 0;
        for (Shortcut sc : foundShortcuts) {
            boolean updatedInGraph = false;
            EdgeSkipIterator iter = g.getOutgoing(sc.from);
            while (iter.next()) {
                if (Path4CH.isValidEdge(iter.skippedEdge())
                        && iter.node() == sc.to
                        && CarStreetType.canBeOverwritten(iter.flags(), sc.flags)
                        && iter.distance() > sc.distance) {
                    iter.flags(sc.flags);
                    iter.skippedEdge(sc.edge);
                    iter.distance(sc.distance);
                    setOrigEdges(iter.edge(), sc.originalEdges);
                    updatedInGraph = true;
                    break;
                }
            }

            if (!updatedInGraph) {
                iter = g.shortcut(sc.from, sc.to, sc.distance, sc.flags, sc.edge);
                setOrigEdges(iter.edge(), sc.originalEdges);
                newShorts++;
            }
        }
        return newShorts;
    }

    private void setOrigEdges(int index, int value) {
        originalEdges.ensureCapacity(index + 1);
        originalEdges.setQuick(index, value);
    }

    private int getOrigEdges(int index) {
        originalEdges.ensureCapacity(index + 1);
        return originalEdges.getQuick(index);
    }

    @Override
    public DijkstraBidirectionRef createAlgo() {
        // do not change weight within DijkstraBidirectionRef => so use ShortestCalc
        DijkstraBidirectionRef dijkstra = new DijkstraBidirectionRef(g) {
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

            @Override public RoutingAlgorithm setType(WeightCalculation wc) {
                throw new IllegalStateException("You'll need to change weightCalculation of preparation instead of algorithm!");
            }

            @Override protected PathBidirRef createPath() {
                // CH changes the distance in prepareEdges to the weight
                // now we need to transform it back to the real distance
                WeightCalculation wc = new WeightCalculation() {
                    @Override public String toString() {
                        return "INVERSE";
                    }

                    @Override public double getMinWeight(double distance) {
                        throw new IllegalStateException("getMinWeight not supported yet");
                    }

                    @Override public double getWeight(double distance, int flags) {
                        return distance;
                    }

                    @Override public long getTime(double distance, int flags) {
                        return prepareWeightCalc.getTime(revert(distance, flags), flags);
                    }

                    @Override public double revert(double weight, int flags) {
                        return prepareWeightCalc.revert(weight, flags);
                    }
                };
                return new Path4CH(graph, wc);
            }

            @Override public String toString() {
                return "DijkstraCH";
            }
        };
        dijkstra.setEdgeFilter(new EdgeLevelFilter(g));
        return dijkstra;
    }

    // we need to use DijkstraSimple as AStar or DijkstraBidirection cannot be efficiently used with multiple goals
    static class OneToManyDijkstraCH extends DijkstraSimple {

        EdgeLevelFilter filter;
        double limit;
        Collection<NodeCH> goals;

        public OneToManyDijkstraCH(Graph graph) {
            super(graph);
            setType(ShortestCarCalc.DEFAULT);
        }

        public OneToManyDijkstraCH setFilter(EdgeLevelFilter filter) {
            this.filter = filter;
            return this;
        }

        @Override
        protected final EdgeIterator getNeighbors(int neighborNode) {
            return filter.doFilter(super.getNeighbors(neighborNode));
        }

        OneToManyDijkstraCH setLimit(double weight) {
            limit = weight;
            return this;
        }

        @Override public OneToManyDijkstraCH clear() {
            super.clear();
            return this;
        }

        @Override public Path calcPath(int from, int to) {
            throw new IllegalArgumentException("call the other calcPath instead");
        }

        Path calcPath(int from, Collection<NodeCH> goals) {
            this.goals = goals;
            return super.calcPath(from, -1);
        }

        @Override public boolean finished(EdgeEntry curr, int _ignoreTo) {
            if (curr.weight > limit)
                return true;

            int found = 0;
            for (NodeCH n : goals) {
                if (n.endNode == curr.endNode) {
                    n.entry = curr;
                    found++;
                } else if (n.entry != null) {
                    found++;
                }
            }
            return found == goals.size();
        }
    }

    private static class WeightedNode {

        int node;
        int priority;

        public WeightedNode(int node, int priority) {
            this.node = node;
            this.priority = priority;
        }

        @Override public String toString() {
            return node + " (" + priority + ")";
        }
    }

    static class Shortcut {

        int from;
        int to;
        int edge;
        double distance;
        int originalEdges;
        int flags = scOneDir;

        public Shortcut(int from, int to, double dist) {
            this.from = from;
            this.to = to;
            this.distance = dist;
        }

        @Override public String toString() {
            return from + "->" + to + ", dist:" + distance;
        }
    }

    static class NodeCH {

        int endNode;
        int originalEdges;
        EdgeEntry entry;
        double distance;

        @Override public String toString() {
            return "" + endNode;
        }
    }
}
