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

import com.graphhopper.routing.Dijkstra;
import com.graphhopper.routing.DijkstraOneToMany;
import com.graphhopper.routing.util.AllCHEdgesIterator;
import com.graphhopper.routing.util.CarFlagEncoder;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.routing.util.TraversalMode;
import com.graphhopper.routing.weighting.ShortestWeighting;
import com.graphhopper.routing.weighting.Weighting;
import com.graphhopper.storage.*;
import com.graphhopper.util.*;
import org.junit.Before;
import org.junit.Test;

import java.util.*;

import static org.junit.Assert.*;

public class NodeContractorTest {
    private final CarFlagEncoder encoder = new CarFlagEncoder();
    private final EncodingManager encodingManager = new EncodingManager(encoder);
    private final Weighting weighting = new ShortestWeighting(encoder);
    private final GraphHopperStorage g = new GraphBuilder(encodingManager).setCHGraph(weighting).create();
    private final CHGraph lg = g.getGraph(CHGraph.class);
    private final TraversalMode tMode = TraversalMode.NODE_BASED;
    private Directory dir;

    @Before
    public void setUp() {
        dir = new GHDirectory("", DAType.RAM_INT);
    }

    private NodeContractor createNodeContractor() {
        NodeContractor nodeContractor = new NodeContractor(dir, g, lg, weighting, tMode);
        nodeContractor.initFromGraph();
        return nodeContractor;
    }

    private void createExampleGraph() {
        //5-1-----2
        //   \ __/|
        //    0   |
        //   /    |
        //  4-----3
        //
        g.edge(0, 1, 1, true);
        g.edge(0, 2, 1, true);
        g.edge(0, 4, 3, true);
        g.edge(1, 2, 3, true);
        g.edge(2, 3, 1, true);
        g.edge(4, 3, 2, true);
        g.edge(5, 1, 2, true);
        g.freeze();
    }

    @Test
    public void testShortestPathSkipNode() {
        createExampleGraph();
        final double normalDist = new Dijkstra(g, weighting, tMode).calcPath(4, 2).getDistance();
        DijkstraOneToMany algo = new DijkstraOneToMany(g, weighting, tMode);
        CHGraph lg = g.getGraph(CHGraph.class);

        setMaxLevelOnAllNodes();

        algo.setEdgeFilter(new NodeContractor.IgnoreNodeFilter(lg, g.getNodes() + 1).setAvoidNode(3));
        algo.setWeightLimit(100);
        int nodeEntry = algo.findEndNode(4, 2);
        assertTrue(algo.getWeight(nodeEntry) > normalDist);

        algo.clear();
        algo.setMaxVisitedNodes(1);
        nodeEntry = algo.findEndNode(4, 2);
        assertEquals(-1, nodeEntry);
    }

    @Test
    public void testShortestPathSkipNode2() {
        createExampleGraph();
        final double normalDist = new Dijkstra(g, weighting, tMode).calcPath(4, 2).getDistance();
        assertEquals(3, normalDist, 1e-5);
        DijkstraOneToMany algo = new DijkstraOneToMany(g, weighting, tMode);

        setMaxLevelOnAllNodes();

        algo.setEdgeFilter(new NodeContractor.IgnoreNodeFilter(lg, g.getNodes() + 1).setAvoidNode(3));
        algo.setWeightLimit(10);
        int nodeEntry = algo.findEndNode(4, 2);
        assertEquals(4, algo.getWeight(nodeEntry), 1e-5);

        nodeEntry = algo.findEndNode(4, 1);
        assertEquals(4, algo.getWeight(nodeEntry), 1e-5);
    }

    @Test
    public void testShortestPathLimit() {
        createExampleGraph();
        DijkstraOneToMany algo = new DijkstraOneToMany(g, weighting, tMode);

        setMaxLevelOnAllNodes();

        algo.setEdgeFilter(new NodeContractor.IgnoreNodeFilter(lg, g.getNodes() + 1).setAvoidNode(0));
        algo.setWeightLimit(2);
        int endNode = algo.findEndNode(4, 1);
        // did not reach endNode
        assertNotEquals(1, endNode);
    }

    @Test
    public void testDirectedGraph() {
        //5 6 7
        // \|/
        //4-3_1<-\ 10
        //     \_|/
        //   0___2_11

        g.edge(0, 2, 2, true);
        g.edge(10, 2, 2, true);
        g.edge(11, 2, 2, true);
        // create a longer one directional edge => no longish one-dir shortcut should be created
        g.edge(2, 1, 2, true);
        g.edge(2, 1, 10, false);

        g.edge(1, 3, 2, true);
        g.edge(3, 4, 2, true);
        g.edge(3, 5, 2, true);
        g.edge(3, 6, 2, true);
        g.edge(3, 7, 2, true);
        g.freeze();

        setMaxLevelOnAllNodes();

        // find all shortcuts if we contract node 1
        NodeContractor nodeContractor = new NodeContractor(dir, g, lg, weighting, tMode);
        nodeContractor.initFromGraph();
        Collection<NodeContractor.Shortcut> scs = nodeContractor.testFindShortcuts(1);
        assertEquals(2, scs.size());
        Iterator<NodeContractor.Shortcut> iter = scs.iterator();
        NodeContractor.Shortcut sc1 = iter.next();
        NodeContractor.Shortcut sc2 = iter.next();
        if (sc1.weight > sc2.weight) {
            NodeContractor.Shortcut tmp = sc1;
            sc1 = sc2;
            sc2 = tmp;
        }

        // both dirs
        assertTrue(sc1.toString(), sc1.from == 3 && sc1.to == 2);
        assertTrue(sc1.toString(), encoder.isForward(sc1.flags) && encoder.isBackward(sc1.flags));

        // directed
        assertTrue(sc2.toString(), sc2.from == 2 && sc2.to == 3);
        assertTrue(sc2.toString(), encoder.isForward(sc2.flags));

        assertEquals(sc1.toString(), 4, sc1.weight, 1e-4);
        assertEquals(sc2.toString(), 12, sc2.weight, 1e-4);
    }

    @Test
    public void testFindShortcuts_Roundabout() {
        EdgeIteratorState iter1to3 = g.edge(1, 3, 1, true);
        EdgeIteratorState iter3to4 = g.edge(3, 4, 1, true);
        EdgeIteratorState iter4to5 = g.edge(4, 5, 1, false);
        EdgeIteratorState iter5to6 = g.edge(5, 6, 1, false);
        EdgeIteratorState iter6to8 = g.edge(6, 8, 2, false);
        EdgeIteratorState iter8to4 = g.edge(8, 4, 1, false);
        g.edge(6, 7, 1, true);
        g.freeze();

        CHEdgeIteratorState tmp = lg.shortcut(1, 4);
        tmp.setFlags(PrepareEncoder.getScDirMask());
        tmp.setWeight(2);
        tmp.setSkippedEdges(iter1to3.getEdge(), iter3to4.getEdge());
        long f = PrepareEncoder.getScFwdDir();
        tmp = lg.shortcut(4, 6);
        tmp.setFlags(f);
        tmp.setWeight(2);
        tmp.setSkippedEdges(iter4to5.getEdge(), iter5to6.getEdge());
        tmp = lg.shortcut(6, 4);
        tmp.setFlags(f);
        tmp.setWeight(3);
        tmp.setSkippedEdges(iter6to8.getEdge(), iter8to4.getEdge());

        setMaxLevelOnAllNodes();

        lg.setLevel(3, 3);
        lg.setLevel(5, 5);
        lg.setLevel(7, 7);
        lg.setLevel(8, 8);

        // after 'manual contraction' the graph looks like:
        // 1 -- 4 -->-- 6 -- 7
        //       \      |
        //        --<----

        // contract node 4!
        NodeContractor nodeContractor = createNodeContractor();
        Collection<NodeContractor.Shortcut> sc = nodeContractor.testFindShortcuts(4);

        // there should be two different shortcuts for both directions!
        assertEquals(2, sc.size());
        Iterator<NodeContractor.Shortcut> iter = sc.iterator();
        NodeContractor.Shortcut sc1 = iter.next();
        NodeContractor.Shortcut sc2 = iter.next();
        if (sc1.from > sc2.from) {
            NodeContractor.Shortcut tmpSc = sc1;
            sc1 = sc2;
            sc2 = tmpSc;
        }

        assertEquals("1->6, weight:4.0 (7,8)", sc1.toString());
        assertEquals("6->1, weight:5.0 (9,7)", sc2.toString());
    }


    @Test
    public void testShortcutMergeBug() {
        // We refer to this real world situation http://www.openstreetmap.org/#map=19/52.71205/-1.77326
        // assume the following graph:
        //
        // ---1---->----2-----3
        //    \--------/
        //
        // where there are two roads from 1 to 2 and the directed road has a smaller weight
        // leading to two shortcuts sc1 (unidir) and sc2 (bidir) where the second should NOT be rejected due to the larger weight
        g.edge(1, 2, 1, true);
        g.edge(1, 2, 1, false);
        g.edge(2, 3, 1, true);
        g.freeze();

        // order is important here
        NodeContractor.Shortcut sc1 = new NodeContractor.Shortcut(1, 3, 6.81620625, 121.18);
        NodeContractor.Shortcut sc2 = new NodeContractor.Shortcut(1, 3, 6.82048125, 121.25);
        sc2.flags = PrepareEncoder.getScDirMask();
        List<NodeContractor.Shortcut> list = Arrays.asList(sc1, sc2);
        NodeContractor nodeContractor = createNodeContractor();
        assertEquals(2, nodeContractor.addShortcuts(list));
    }

    @Test
    public void testContractNode_directed_shortcutRequired() {
        // 0 --> 1 --> 2
        final EdgeIteratorState edge1 = g.edge(0, 1, 1, false);
        final EdgeIteratorState edge2 = g.edge(1, 2, 2, false);
        g.freeze();
        setMaxLevelOnAllNodes();
        createNodeContractor().contractNode(1);
        checkShortcuts(expectedShortcut(0, 2, edge1, edge2, true, false));
    }

    @Test
    public void testContractNode_bidirected_shortcutsRequired() {
        // 0 -- 1 -- 2
        final EdgeIteratorState edge1 = g.edge(0, 1, 1, true);
        final EdgeIteratorState edge2 = g.edge(1, 2, 2, true);
        g.freeze();
        setMaxLevelOnAllNodes();
        createNodeContractor().contractNode(1);
        checkShortcuts(expectedShortcut(0, 2, edge2, edge1, true, true));
    }

    @Test
    public void testContractNode_directed_withWitness() {
        // 0 --> 1 --> 2
        //  \_________/
        g.edge(0, 1, 1, false);
        g.edge(1, 2, 2, false);
        g.edge(0, 2, 1, false);
        g.freeze();
        setMaxLevelOnAllNodes();
        createNodeContractor().contractNode(1);
        checkNoShortcuts();
    }

    /**
     * Queries the ch graph and checks if the graph's shortcuts match the given expected shortcuts.
     */
    private void checkShortcuts(Shortcut... expectedShortcuts) {
        Set<Shortcut> expected = setOf(expectedShortcuts);
        if (expected.size() != expectedShortcuts.length) {
            fail("was given duplicate shortcuts");
        }
        AllCHEdgesIterator iter = lg.getAllEdges();
        Set<Shortcut> given = new HashSet<>();
        while (iter.next()) {
            if (iter.isShortcut()) {
                given.add(new Shortcut(
                        iter.getBaseNode(), iter.getAdjNode(), iter.getWeight(), iter.getDistance(),
                        iter.isForward(encoder), iter.isBackward(encoder),
                        iter.getSkippedEdge1(), iter.getSkippedEdge2()));
            }
        }
        assertEquals(expected, given);
    }

    private void checkNoShortcuts() {
        checkShortcuts();
    }

    private Shortcut expectedShortcut(int baseNode, int adjNode, EdgeIteratorState edge1, EdgeIteratorState edge2,
                                      boolean fwd, boolean bwd) {
        //todo: weight calculation might have to be adjusted for different encoders/weightings/reverse speed
        double weight = weighting.calcWeight(edge1, false, EdgeIterator.NO_EDGE) +
                weighting.calcWeight(edge2, false, EdgeIterator.NO_EDGE);
        double distance = edge1.getDistance() + edge2.getDistance();
        return new Shortcut(baseNode, adjNode, weight, distance, fwd, bwd, edge1.getEdge(), edge2.getEdge());
    }

    private Set<Shortcut> setOf(Shortcut... shortcuts) {
        Set<Shortcut> result = new HashSet<>();
        result.addAll(Arrays.asList(shortcuts));
        return result;
    }

    private void setMaxLevelOnAllNodes() {
        int nodes = lg.getNodes();
        for (int node = 0; node < nodes; node++) {
            lg.setLevel(node, nodes + 1);
        }
    }

    private static class Shortcut {
        int baseNode;
        int adjNode;
        double weight;
        double distance;
        boolean fwd;
        boolean bwd;
        int skipEdge1;
        int skipEdge2;

        Shortcut(int baseNode, int adjNode, double weight, double distance, boolean fwd, boolean bwd, int skipEdge1, int skipEdge2) {
            this.baseNode = baseNode;
            this.adjNode = adjNode;
            this.weight = weight;
            this.distance = distance;
            this.fwd = fwd;
            this.bwd = bwd;
            this.skipEdge1 = skipEdge1;
            this.skipEdge2 = skipEdge2;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Shortcut shortcut = (Shortcut) o;
            return baseNode == shortcut.baseNode &&
                    adjNode == shortcut.adjNode &&
                    Double.compare(shortcut.weight, weight) == 0 &&
                    Double.compare(shortcut.distance, distance) == 0 &&
                    fwd == shortcut.fwd &&
                    bwd == shortcut.bwd &&
                    skipEdge1 == shortcut.skipEdge1 &&
                    skipEdge2 == shortcut.skipEdge2;
        }

        @Override
        public int hashCode() {
            return Objects.hash(baseNode, adjNode, weight, distance, fwd, bwd, skipEdge1, skipEdge2);
        }

        @Override
        public String toString() {
            return "Shortcut{" +
                    "baseNode=" + baseNode +
                    ", adjNode=" + adjNode +
                    ", weight=" + weight +
                    ", distance=" + distance +
                    ", fwd=" + fwd +
                    ", bwd=" + bwd +
                    ", skipEdge1=" + skipEdge1 +
                    ", skipEdge2=" + skipEdge2 +
                    '}';
        }
    }
}