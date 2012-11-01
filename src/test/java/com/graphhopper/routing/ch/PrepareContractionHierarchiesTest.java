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

import com.graphhopper.routing.ch.PrepareContractionHierarchies;
import com.graphhopper.routing.DijkstraSimple;
import com.graphhopper.routing.Path;
import com.graphhopper.routing.RoutingAlgorithm;
import com.graphhopper.routing.ch.PrepareContractionHierarchies.NodeCH;
import com.graphhopper.routing.ch.PrepareContractionHierarchies.Shortcut;
import com.graphhopper.routing.util.FastestCalc;
import com.graphhopper.routing.util.PrepareSimpleShortcutsTest;
import com.graphhopper.storage.Graph;
import com.graphhopper.storage.LevelGraph;
import com.graphhopper.storage.LevelGraphStorage;
import com.graphhopper.storage.RAMDirectory;
import com.graphhopper.util.EdgeSkipIterator;
import com.graphhopper.util.GraphUtility;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import static org.junit.Assert.*;
import org.junit.Test;

/**
 *
 * @author Peter Karich
 */
public class PrepareContractionHierarchiesTest {

    LevelGraph createGraph() {
        LevelGraphStorage g = new LevelGraphStorage(new RAMDirectory());
        g.createNew(10);
        return g;
    }

    LevelGraph createExampleGraph() {
        LevelGraph g = createGraph();

        //5-1-----2
        //   \ __/|
        //    0   |
        //   /    |
        //  4-----3
        //
        g.edge(0, 1, 1, true);
        g.edge(0, 2, 1, true);
        g.edge(0, 4, 3, true);
        g.edge(1, 2, 2, true);
        g.edge(2, 3, 1, true);
        g.edge(4, 3, 2, true);
        g.edge(5, 1, 2, true);
        return g;
    }

    List<NodeCH> createGoals(int... gNodes) {
        List<NodeCH> goals = new ArrayList<NodeCH>();
        for (int i = 0; i < gNodes.length; i++) {
            NodeCH n = new NodeCH();
            n.endNode = gNodes[i];
            goals.add(n);
        }
        return goals;
    }

    @Test
    public void testShortestPathSkipNode() {
        LevelGraph g = createExampleGraph();
        double normalDist = new DijkstraSimple(g).calcPath(4, 2).distance();
        PrepareContractionHierarchies.OneToManyDijkstraCH algo = new PrepareContractionHierarchies.OneToManyDijkstraCH(g)
                .setFilter(new PrepareContractionHierarchies.EdgeLevelFilterCH(g).setSkipNode(3));
        List<NodeCH> gs = createGoals(2);
        algo.clear().setLimit(10).calcPath(4, gs);
        Path p = algo.extractPath(gs.get(0).entry);
        assertTrue(p.distance() > normalDist);
    }

    @Test
    public void testShortestPathSkipNode2() {
        LevelGraph g = createExampleGraph();
        double normalDist = new DijkstraSimple(g).calcPath(4, 2).distance();
        PrepareContractionHierarchies.OneToManyDijkstraCH algo = new PrepareContractionHierarchies.OneToManyDijkstraCH(g).
                setFilter(new PrepareContractionHierarchies.EdgeLevelFilterCH(g).setSkipNode(3));
        List<NodeCH> gs = createGoals(1, 2);
        algo.clear().setLimit(10).calcPath(4, gs);
        Path p = algo.extractPath(gs.get(1).entry);
        assertTrue(p.distance() > normalDist);
    }

    @Test
    public void testShortestPathLimit() {
        LevelGraph g = createExampleGraph();
        PrepareContractionHierarchies.OneToManyDijkstraCH algo = new PrepareContractionHierarchies.OneToManyDijkstraCH(g)
                .setFilter(new PrepareContractionHierarchies.EdgeLevelFilterCH(g).setSkipNode(0));
        List<NodeCH> gs = createGoals(1);
        algo.clear().setLimit(2).calcPath(4, gs);
        assertNull(gs.get(0).entry);
    }

    @Test
    public void testAddShortcuts() {
        LevelGraph g = createExampleGraph();
        int old = GraphUtility.count(g.getAllEdges());
        PrepareContractionHierarchies prepare = new PrepareContractionHierarchies().setGraph(g);
        prepare.doWork();
        assertEquals(old, GraphUtility.count(g.getAllEdges()));
    }

    @Test
    public void testMoreComplexGraph() {
        LevelGraph g = PrepareSimpleShortcutsTest.createShortcutsGraph();
        int old = GraphUtility.count(g.getAllEdges());
        PrepareContractionHierarchies prepare = new PrepareContractionHierarchies().setGraph(g);
        prepare.doWork();
        assertEquals(old + 6, GraphUtility.count(g.getAllEdges()));
    }

    @Test
    public void testDirectedGraph() {
        LevelGraph g = createGraph();
        g.edge(5, 4, 3, false);
        g.edge(4, 5, 10, false);
        g.edge(2, 4, 1, false);
        g.edge(5, 2, 1, false);
        g.edge(3, 5, 1, false);
        g.edge(4, 3, 1, false);
        int old = GraphUtility.count(g.getAllEdges());
        PrepareContractionHierarchies prepare = new PrepareContractionHierarchies().setGraph(g);
        prepare.doWork();
        // PrepareSimpleShortcutsTest.printEdges(g);
        assertEquals(old + 2, GraphUtility.count(g.getAllEdges()));
        RoutingAlgorithm algo = prepare.createAlgo();
        Path p = algo.clear().calcPath(4, 2);
        assertEquals(3, p.distance(), 1e-6);
        assertEquals(Arrays.asList(4, 3, 5, 2), p.toNodeList());
    }

    @Test
    public void testDirectedGraph2() {
        LevelGraph g = createGraph();
        PrepareSimpleShortcutsTest.initDirected2(g);
        int old = GraphUtility.count(g.getAllEdges());
        PrepareContractionHierarchies prepare = new PrepareContractionHierarchies().setGraph(g);
        prepare.doWork();
        // PrepareSimpleShortcutsTest.printEdges(g);
        assertEquals(old + 14, GraphUtility.count(g.getAllEdges()));
        RoutingAlgorithm algo = prepare.createAlgo();

        Path p = algo.clear().calcPath(0, 10);
        assertEquals(10, p.distance(), 1e-6);
        assertEquals(Arrays.asList(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10), p.toNodeList());
    }

    void initRoundaboutGraph(Graph g) {
        //              roundabout:
        //16-0-9-10--11   12<-13
        //    \       \  /      \
        //    17       \|        7-8-..
        // -15-1--2--3--4       /     /
        //     /         \-5->6/     /
        //  -14            \________/

        g.edge(16, 0, 1, true);
        g.edge(0, 9, 1, true);
        g.edge(0, 17, 1, true);
        g.edge(9, 10, 1, true);
        g.edge(10, 11, 1, true);
        g.edge(11, 28, 1, true);
        g.edge(28, 29, 1, true);
        g.edge(29, 30, 1, true);
        g.edge(30, 31, 1, true);
        g.edge(31, 4, 1, true);

        g.edge(17, 1, 1, true);
        g.edge(15, 1, 1, true);
        g.edge(14, 1, 1, true);
        g.edge(14, 18, 1, true);
        g.edge(18, 19, 1, true);
        g.edge(19, 20, 1, true);
        g.edge(20, 15, 1, true);
        g.edge(19, 21, 1, true);
        g.edge(21, 16, 1, true);
        g.edge(1, 2, 1, true);
        g.edge(2, 3, 1, true);
        g.edge(3, 4, 1, true);

        g.edge(4, 5, 1, false);
        g.edge(5, 6, 1, false);
        g.edge(6, 7, 1, false);
        g.edge(7, 13, 1, false);
        g.edge(13, 12, 1, false);
        g.edge(12, 4, 1, false);

        g.edge(7, 8, 1, true);
        g.edge(8, 22, 1, true);
        g.edge(22, 23, 1, true);
        g.edge(23, 24, 1, true);
        g.edge(24, 25, 1, true);
        g.edge(25, 27, 1, true);
        g.edge(27, 5, 1, true);
        g.edge(25, 26, 1, false);
        g.edge(26, 25, 1, false);
    }

    @Test
    public void testRoundaboutUnpacking() {
        LevelGraph g = createGraph();
        initRoundaboutGraph(g);
        int old = GraphUtility.count(g.getAllEdges());
        PrepareContractionHierarchies prepare = new PrepareContractionHierarchies().setGraph(g);
        prepare.doWork();
        // PrepareSimpleShortcutsTest.printEdges(g);
        assertEquals(old + 20, GraphUtility.count(g.getAllEdges()));
        RoutingAlgorithm algo = prepare.createAlgo();
        Path p = algo.clear().calcPath(4, 7);
        assertEquals(Arrays.asList(4, 5, 6, 7), p.toNodeList());
    }

    @Test
    public void testFindShortcuts_Roundabout() {
        LevelGraphStorage g = (LevelGraphStorage) createGraph();
        EdgeSkipIterator iter = g.newEdge(1, 3, 1, true);
        g.newEdge(3, 4, 1, true);
        EdgeSkipIterator iter2 = g.newEdge(4, 5, 1, false);
        g.newEdge(5, 6, 1, false);
        g.newEdge(6, 7, 1, true);
        EdgeSkipIterator iter3 = g.newEdge(6, 8, 2, false);
        g.newEdge(8, 4, 1, false);
        g.setLevel(3, 3);
        g.setLevel(5, 5);
        g.setLevel(7, 7);
        g.setLevel(8, 8);

        g.shortcut(1, 4, 2, PrepareContractionHierarchies.scBothDir, iter.edge());
        int f = PrepareContractionHierarchies.scOneDir;
        g.shortcut(4, 6, 2, f, iter2.edge());
        g.shortcut(6, 4, 3, f, iter3.edge());

        PrepareContractionHierarchies prepare = new PrepareContractionHierarchies().setGraph(g).initFromGraph();
        // there should be two different shortcuts for both directions!
        Collection<Shortcut> sc = prepare.findShortcuts(4);
        assertEquals(2, sc.size());
    }

    void initUnpackingGraph(LevelGraphStorage g) {
        g.edge(10, 0, 1, false);
        EdgeSkipIterator iter = g.newEdge(0, 1, 1, false);
        g.edge(1, 2, 1, false);
        g.edge(2, 3, 1, false);
        g.edge(3, 4, 1, false);
        g.edge(4, 5, 1, false);
        g.edge(5, 6, 1, false);
        int f = PrepareContractionHierarchies.scOneDir;
        iter = g.shortcut(0, 2, 2, f, iter.edge());
        iter = g.shortcut(0, 3, 3, f, iter.edge());
        iter = g.shortcut(0, 4, 4, f, iter.edge());
        iter = g.shortcut(0, 5, 5, f, iter.edge());
        g.shortcut(0, 6, 6, f, iter.edge());
        g.setLevel(0, 10);
        g.setLevel(6, 9);
        g.setLevel(5, 8);
        g.setLevel(4, 7);
        g.setLevel(3, 6);
        g.setLevel(2, 5);
        g.setLevel(1, 4);
        g.setLevel(10, 3);
    }

    @Test
    public void testUnpackingOrder() {
        LevelGraphStorage g = (LevelGraphStorage) createGraph();
        initUnpackingGraph(g);
        PrepareContractionHierarchies prepare = new PrepareContractionHierarchies().setGraph(g);
        RoutingAlgorithm algo = prepare.createAlgo();
        Path p = algo.calcPath(10, 6);
        assertEquals(7, p.distance(), 1e-5);
        assertEquals(Arrays.asList(10, 0, 1, 2, 3, 4, 5, 6), p.toNodeList());
    }

    @Test
    public void testUnpackingOrder_Fastest() {
        LevelGraphStorage g = (LevelGraphStorage) createGraph();
        initUnpackingGraph(g);
        PrepareContractionHierarchies prepare = new PrepareContractionHierarchies().
                setGraph(g).setType(FastestCalc.DEFAULT);
        RoutingAlgorithm algo = prepare.createAlgo();
        Path p = algo.calcPath(10, 6);
        assertEquals(7, p.distance(), 1e-5);
        assertEquals(Arrays.asList(10, 0, 1, 2, 3, 4, 5, 6), p.toNodeList());
    }
}
