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
package com.graphhopper.routing.util;

import com.graphhopper.routing.DijkstraSimple;
import com.graphhopper.routing.Path;
import com.graphhopper.storage.EdgeEntry;
import com.graphhopper.storage.Graph;
import com.graphhopper.storage.LevelGraph;
import com.graphhopper.storage.LevelGraphStorage;
import com.graphhopper.util.EdgeIterator;
import com.graphhopper.util.EdgeSkipIterator;
import com.graphhopper.util.GraphUtility;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.PriorityQueue;

/**
 * This class prepares the graphs to be used from PrepareContractionHierarchies algorithm.
 *
 * There are several description of contraction hierarchies available only but this is one of the
 * more detailed: http://web.cs.du.edu/~sturtevant/papers/highlevelpathfinding.pdf
 *
 * @author Peter Karich
 */
public class PrepareContractionHierarchies {

    private WeightCalculation weightCalc;
    private LevelGraph g;
    // the most important nodes comes last
    private PriorityQueue<WeightedNode> sortedNodes;
    private WeightedNode refs[];
    // shortcut is in one direction, speed is ignored - see prepareEdges
    private int scFlags = CarStreetType.flags(0, false);

    public PrepareContractionHierarchies(LevelGraph g) {
        this.g = g;
        sortedNodes = new PriorityQueue<WeightedNode>(g.getNodes(), new Comparator<WeightedNode>() {
            @Override public int compare(WeightedNode o1, WeightedNode o2) {
                return o1.priority - o2.priority;
            }
        });
        refs = new WeightedNode[g.getNodes()];
        weightCalc = FastestCalc.DEFAULT;
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

    private static class Shortcut {

        int from;
        int to;
        double distance;
        boolean update = false;
        int originalEdges;

        public Shortcut(int from, int to, double dist) {
            this.from = from;
            this.to = to;
            this.distance = dist;
        }

        public Shortcut originalEdges(int oe) {
            originalEdges = oe;
            return this;
        }

        public Shortcut update(boolean update) {
            this.update = update;
            return this;
        }

        @Override public String toString() {
            return from + "->" + to + ", dist:" + distance + ",update:" + update;
        }
    }

    public void doWork() {
        // TODO integrate -> so avoid all nodes with negative level in the other methods
        // new PrepareRoutingShortcuts(g).doWork();        
        prepareEdges();
        prepareNodes();
        contractNodes();
    }

    void prepareEdges() {
        // in CH the flags will be ignored (calculating the new flags for the shortcuts is impossible)
        // also several shortcuts would be necessary with the different modes (e.g. fastest and shortest)
        // so calculate the weight and store this as distance, then use only distance instead of getWeight
        EdgeSkipIterator iter = g.getAllEdges();
        while (iter.next()) {
            iter.distance(weightCalc.getWeight(iter));
            iter.originalEdges(1);
        }
    }

    void prepareNodes() {
        int len = g.getNodes();

        // minor idea: 1. sort nodes randomly and 2. pre-init with node degree
        for (int node = 0; node < len; node++) {
            refs[node] = new WeightedNode(node, 0);
        }

        for (int node = 0; node < len; node++) {
            WeightedNode wn = refs[node];
            wn.priority = calculatePriority(node, 0);
            System.out.println(wn);
            sortedNodes.add(wn);
        }
        System.out.println("-----------");
    }

    void contractNodes() {
        // in PrepareShortcuts level 0 and -1 is already used
        int level = 1;
        while (!sortedNodes.isEmpty()) {
            WeightedNode wn = sortedNodes.poll();

            // update priority of current node via simulating 'addShortcuts'
            wn.priority = calculatePriority(wn.node, wn.priority);

            // recompute priority of uncontracted neighbors
            EdgeIterator iter = g.getEdges(wn.node);
            while (iter.next()) {
                if (g.getLevel(iter.node()) != 0)
                    // already contracted no update necessary
                    continue;

                WeightedNode neighborWn = refs[iter.node()];
                int prio = calculatePriority(iter.node(), neighborWn.priority);
                neighborWn.priority = prio;

                // TODO
//                if (!sortedNodes.remove(neighborWn))
//                    throw new IllegalStateException("couldn't find element:" + neighborWn);
//
//                sortedNodes.add(neighborWn);
                System.out.println("neighbor:" + neighborWn);
            }

            // do we really want to contract?
            // TODO how to avoid endless loops and revert adding shortcuts?
            // endless loops occur if we enable updating the queue with the neighbors
            if (!sortedNodes.isEmpty() && wn.priority > sortedNodes.peek().priority) {
                // node got more important => contract it later
                System.out.println("later:" + wn + " minEl:" + sortedNodes.peek());
                sortedNodes.add(wn);
                continue;
            }

            // contract!            
            addShortcuts(wn.node, wn.priority);
            g.setLevel(wn.node, level);
            level++;
        }
    }

    /**
     * Calculates the priority of node v without changing the graph.
     */
    int calculatePriority(int v, int oldPrio) {
        // set of shortcuts that would be added if node v would be contracted next.
        Collection<Shortcut> shortcuts = findShortcuts(v, oldPrio);
        // from shortcuts we can compute the edgeDifference
        // |shortcuts(v)| − |{(u, v) | v uncontracted}| − |{(v, w) | v uncontracted}|        
        // meanDegree is used instead of outDegree+inDegree as if one edge is in both directions
        // only one bucket memory is used. Additionally one shortcut could also stand for two directions.
        int degree = GraphUtility.count(g.getEdges(v));
        int edgeDifference = shortcuts.size() - degree;

        // every edge has an 'original edge' number associated. initially it is r=1
        // when a new shortcut is introduced then r of the associated edges is summed up:
        // r(u,w)=r(u,v)+r(v,w) now we can define
        // originalEdges = σ(v) := sum_{ (u,w) ∈ shortcuts(v) } of r(u, w)
        int originalEdges = 0;
        for (Shortcut sc : shortcuts) {
            originalEdges += sc.originalEdges;
        }

        // number of already contracted neighbors of v
        int contractedNeighbors = 0;
        EdgeSkipIterator iter = g.getEdges(v);
        while (iter.next()) {
            if (iter.skippedNode() >= 0)
                contractedNeighbors++;
        }

        // according to the paper do a simple linear combination of the properties to get the priority
        return 2 * edgeDifference + 10 * originalEdges + contractedNeighbors;
    }

    Collection<Shortcut> findShortcuts(int v, int prio) {
        // do NOT use weight use distance. see prepareEdges where distance is overwritten by weight!
        LocalShortestPathCH algo = new LocalShortestPathCH(g, v);
        Map<Long, Shortcut> shortcuts = new HashMap<Long, Shortcut>();
        EdgeSkipIterator iter1 = g.getIncoming(v);
        while (iter1.next()) {
            int u = iter1.node();
            if (refs[u].priority < prio)
                continue;

            double v_u_weight = iter1.distance();
            EdgeSkipIterator iter2 = g.getOutgoing(v);
            while (iter2.next()) {
                int w = iter2.node();
                if (w == u || refs[w].priority < prio)
                    continue;

                double u_v_w_weight = v_u_weight + iter2.distance();
                // TODO ignore also all already contracted nodes
                Path p = algo.clear().setLimit(u_v_w_weight).calcPath(u, w);
                if (p != null && p.weight() <= u_v_w_weight) {
                    // FOUND witness path => do not add shortcut
                    continue;
                }

                // FOUND shortcut but be sure that it is the only shortcut in the collection 
                // and also in the graph for u->w. If existing => update it
                long edge = u + w;
                Shortcut sc = shortcuts.get(edge);
                if (sc == null) {
                    sc = new Shortcut(u, w, u_v_w_weight);
                    sc.originalEdges = iter1.originalEdges() + iter2.originalEdges();
                    shortcuts.put(edge, sc);

                    // determine if a shortcut already exists in the graph
                    EdgeSkipIterator tmpIter = g.getOutgoing(u);
                    while (tmpIter.next()) {
                        if (tmpIter.node() != w || tmpIter.skippedNode() < 0)
                            continue;

                        if (tmpIter.distance() > u_v_w_weight)
                            sc.update(true);
                    }
                } else {
                    // TODO direction could be incorrect! u->w is not always the same as w->u
                    // a shortcut exists in the current collection
                    if (sc.distance > u_v_w_weight) {
                        sc.originalEdges = iter1.originalEdges() + iter2.originalEdges();
                        sc.distance = u_v_w_weight;
                    }
                }
            }
        }
        return shortcuts.values();
    }

    public static class LocalShortestPathCH extends DijkstraSimple {

        int skipNode;
        double limit;

        public LocalShortestPathCH(Graph graph, int v) {
            super(graph);
            setType(ShortestCalc.DEFAULT);
            skipNode = v;
        }

        LocalShortestPathCH setLimit(double weight) {
            limit = weight;
            return this;
        }

        @Override public LocalShortestPathCH clear() {
            super.clear();
            visited.add(skipNode);
            return this;
        }

        @Override public boolean finished(EdgeEntry curr, int to) {
            return super.finished(curr, to) || curr.weight > limit;
        }
    }

    /**
     * Introduces the necessary shortcuts for node v in the graph.
     */
    void addShortcuts(int v, int prio) {
        Collection<Shortcut> shortcuts = findShortcuts(v, prio);
        System.out.println("contract:" + v + " (" + prio + "), scs:" + shortcuts);
        for (Shortcut sc : shortcuts) {
            if (sc.update)
                // TODO how to update the shortcut?
                sc = sc;
            else
                g.shortcut(sc.from, sc.to, sc.distance, scFlags, v);
        }
    }
}
