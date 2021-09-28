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
import com.graphhopper.routing.DijkstraBidirectionCH;
import com.graphhopper.routing.Path;
import com.graphhopper.routing.ev.BooleanEncodedValue;
import com.graphhopper.routing.ev.EncodedValue;
import com.graphhopper.routing.ev.SimpleBooleanEncodedValue;
import com.graphhopper.routing.util.AllCHEdgesIterator;
import com.graphhopper.routing.util.CarFlagEncoder;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.routing.util.TraversalMode;
import com.graphhopper.routing.weighting.FastestWeighting;
import com.graphhopper.routing.weighting.ShortestWeighting;
import com.graphhopper.routing.weighting.Weighting;
import com.graphhopper.storage.*;
import com.graphhopper.util.CHEdgeIteratorState;
import com.graphhopper.util.EdgeIteratorState;
import com.graphhopper.util.PMap;
import org.junit.Test;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

import static org.junit.Assert.*;

public class NodeBasedNodeContractorTest {
    // TODO integrate this into CHGraphImpl somehow
    public final static BooleanEncodedValue SC_ACCESS = new SimpleBooleanEncodedValue("sc_access", true);

    static {
        SC_ACCESS.init(new EncodedValue.InitializerConfig());
    }

    private final CarFlagEncoder encoder = new CarFlagEncoder();
    private final EncodingManager encodingManager = EncodingManager.create(encoder);
    private final Weighting weighting = new ShortestWeighting(encoder);
    private final GraphHopperStorage graph = new GraphBuilder(encodingManager).setCHConfigs(CHConfig.nodeBased("profile", weighting)).create();
    private final CHGraph lg = graph.getCHGraph();
    private final PrepareCHGraph pg = PrepareCHGraph.nodeBased(lg, weighting);

    private NodeContractor createNodeContractor() {
        return createNodeContractor(pg);
    }

    private NodeContractor createNodeContractor(PrepareCHGraph chGraph) {
        NodeContractor nodeContractor = new NodeBasedNodeContractor(chGraph, new PMap());
        nodeContractor.initFromGraph();
        nodeContractor.prepareContraction();
        return nodeContractor;
    }

    private void createExampleGraph() {
        //5-1-----2
        //   \ __/|
        //    0   |
        //   /    |
        //  4-----3
        //
        graph.edge(0, 1, 1, true);
        graph.edge(0, 2, 1, true);
        graph.edge(0, 4, 3, true);
        graph.edge(1, 2, 3, true);
        graph.edge(2, 3, 1, true);
        graph.edge(4, 3, 2, true);
        graph.edge(5, 1, 2, true);
        graph.freeze();
    }

    @Test
    public void testShortestPathSkipNode() {
        createExampleGraph();
        final double normalDist = new Dijkstra(graph, weighting, TraversalMode.NODE_BASED).calcPath(4, 2).getDistance();
        NodeBasedWitnessPathSearcher algo = new NodeBasedWitnessPathSearcher(pg);

        setMaxLevelOnAllNodes();

        algo.ignoreNode(3);
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
        final double normalDist = new Dijkstra(graph, weighting, TraversalMode.NODE_BASED).calcPath(4, 2).getDistance();
        assertEquals(3, normalDist, 1e-5);
        NodeBasedWitnessPathSearcher algo = new NodeBasedWitnessPathSearcher(pg);

        setMaxLevelOnAllNodes();

        algo.ignoreNode(3);
        algo.setWeightLimit(10);
        int nodeEntry = algo.findEndNode(4, 2);
        assertEquals(4, algo.getWeight(nodeEntry), 1e-5);

        nodeEntry = algo.findEndNode(4, 1);
        assertEquals(4, algo.getWeight(nodeEntry), 1e-5);
    }

    @Test
    public void testShortestPathLimit() {
        createExampleGraph();
        NodeBasedWitnessPathSearcher algo = new NodeBasedWitnessPathSearcher(pg);

        setMaxLevelOnAllNodes();

        algo.ignoreNode(0);
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

        graph.edge(0, 2, 2, true);
        graph.edge(10, 2, 2, true);
        graph.edge(11, 2, 2, true);
        // create a longer one directional edge => no longish one-dir shortcut should be created
        final EdgeIteratorState edge2to1bidirected = graph.edge(2, 1, 2, true);
        final EdgeIteratorState edge2to1directed = graph.edge(2, 1, 10, false);
        final EdgeIteratorState edge1to3 = graph.edge(1, 3, 2, true);
        graph.edge(3, 4, 2, true);
        graph.edge(3, 5, 2, true);
        graph.edge(3, 6, 2, true);
        graph.edge(3, 7, 2, true);
        graph.freeze();

        setMaxLevelOnAllNodes();

        // find all shortcuts if we contract node 1
        NodeContractor nodeContractor = createNodeContractor();
        nodeContractor.contractNode(1);
        checkShortcuts(
                expectedShortcut(3, 2, edge1to3, edge2to1bidirected, true, true),
                expectedShortcut(2, 3, edge2to1directed, edge1to3, true, false)
        );
    }

    @Test
    public void testFindShortcuts_Roundabout() {
        // 1 -- 3 -- 4 ---> 5 ---> 6 -- 7
        //            \           /
        //             <--- 8 <--- 
        final EdgeIteratorState iter1to3 = graph.edge(1, 3, 1, true);
        final EdgeIteratorState iter3to4 = graph.edge(3, 4, 1, true);
        final EdgeIteratorState iter4to5 = graph.edge(4, 5, 1, false);
        final EdgeIteratorState iter5to6 = graph.edge(5, 6, 1, false);
        final EdgeIteratorState iter6to8 = graph.edge(6, 8, 2, false);
        final EdgeIteratorState iter8to4 = graph.edge(8, 4, 1, false);
        graph.edge(6, 7, 1, true);
        graph.freeze();

        int sc1to4 = lg.shortcut(1, 4, PrepareEncoder.getScDirMask(), 2, iter1to3.getEdge(), iter3to4.getEdge());
        int sc4to6 = lg.shortcut(4, 6, PrepareEncoder.getScFwdDir(), 2, iter4to5.getEdge(), iter5to6.getEdge());
        int sc6to4 = lg.shortcut(6, 4, PrepareEncoder.getScFwdDir(), 3, iter6to8.getEdge(), iter8to4.getEdge());

        setMaxLevelOnAllNodes();

        lg.setLevel(3, 3);
        lg.setLevel(5, 5);
        lg.setLevel(7, 7);
        lg.setLevel(8, 8);

        Shortcut manualSc1 = expectedShortcut(1, 4, iter1to3, iter3to4, true, true);
        Shortcut manualSc2 = expectedShortcut(4, 6, iter4to5, iter5to6, true, false);
        Shortcut manualSc3 = expectedShortcut(6, 4, iter6to8, iter8to4, true, false);
        checkShortcuts(manualSc1, manualSc2, manualSc3);

        // after 'manual contraction' of nodes 3, 5, 8 the graph looks like:
        // 1 -- 4 -->-- 6 -- 7
        //       \      |
        //        --<----

        // contract node 4!
        NodeContractor nodeContractor = createNodeContractor();
        nodeContractor.contractNode(4);
        checkShortcuts(manualSc1, manualSc2, manualSc3,
                // there should be two different shortcuts for both directions!
                expectedShortcut(1, 6, lg.getEdgeIteratorState(sc1to4, 4), lg.getEdgeIteratorState(sc4to6, 6), true, false),
                expectedShortcut(6, 1, lg.getEdgeIteratorState(sc6to4, 4), lg.getEdgeIteratorState(sc1to4, 1), true, false)
        );
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
        final EdgeIteratorState edge1to2bidirected = graph.edge(1, 2, 1, true);
        final EdgeIteratorState edge1to2directed = graph.edge(1, 2, 1, false);
        final EdgeIteratorState edge2to3 = graph.edge(2, 3, 1, true);
        graph.freeze();
        setMaxLevelOnAllNodes();
        NodeContractor nodeContractor = createNodeContractor();
        nodeContractor.contractNode(2);
        checkShortcuts(
                expectedShortcut(3, 1, edge2to3, edge1to2bidirected, true, false),
                expectedShortcut(1, 3, edge1to2directed, edge2to3, true, false)
        );
    }

    @Test
    public void testContractNode_directed_shortcutRequired() {
        // 0 --> 1 --> 2
        final EdgeIteratorState edge1 = graph.edge(0, 1, 1, false);
        final EdgeIteratorState edge2 = graph.edge(1, 2, 2, false);
        graph.freeze();
        setMaxLevelOnAllNodes();
        createNodeContractor().contractNode(1);
        checkShortcuts(expectedShortcut(0, 2, edge1, edge2, true, false));
    }

    @Test
    public void testContractNode_directed_shortcutRequired_reverse() {
        // 0 <-- 1 <-- 2
        final EdgeIteratorState edge1 = graph.edge(2, 1, 1, false);
        final EdgeIteratorState edge2 = graph.edge(1, 0, 2, false);
        graph.freeze();
        setMaxLevelOnAllNodes();
        createNodeContractor().contractNode(1);
        checkShortcuts(expectedShortcut(2, 0, edge1, edge2, true, false));
    }

    @Test
    public void testContractNode_bidirected_shortcutsRequired() {
        // 0 -- 1 -- 2
        final EdgeIteratorState edge1 = graph.edge(0, 1, 1, true);
        final EdgeIteratorState edge2 = graph.edge(1, 2, 2, true);
        graph.freeze();
        setMaxLevelOnAllNodes();
        createNodeContractor().contractNode(1);
        checkShortcuts(expectedShortcut(2, 0, edge2, edge1, true, true));
    }

    @Test
    public void testContractNode_directed_withWitness() {
        // 0 --> 1 --> 2
        //  \_________/
        graph.edge(0, 1, 1, false);
        graph.edge(1, 2, 2, false);
        graph.edge(0, 2, 1, false);
        graph.freeze();
        setMaxLevelOnAllNodes();
        createNodeContractor().contractNode(1);
        checkNoShortcuts();
    }

    @Test
    public void testNodeContraction_shortcutDistanceRounding() {
        assertTrue("this test was constructed assuming we are using the ShortestWeighting", weighting instanceof ShortestWeighting);
        // 0 ------------> 4
        //  \             /
        //   1 --> 2 --> 3
        double[] distances = {4.019, 1.006, 1.004, 1.006, 1.004};
        graph.edge(0, 4, distances[0], false);
        EdgeIteratorState edge1 = graph.edge(0, 1, distances[1], false);
        EdgeIteratorState edge2 = graph.edge(1, 2, distances[2], false);
        EdgeIteratorState edge3 = graph.edge(2, 3, distances[3], false);
        EdgeIteratorState edge4 = graph.edge(3, 4, distances[4], false);
        graph.freeze();
        setMaxLevelOnAllNodes();

        // make sure that distances do not get changed in storage (they might get truncated)
        AllCHEdgesIterator iter = lg.getAllEdges();
        double[] storedDistances = new double[iter.length()];
        int count = 0;
        while (iter.next()) {
            storedDistances[count++] = iter.getDistance();
        }
        assertArrayEquals(distances, storedDistances, 1.e-6);

        // perform CH contraction
        contractInOrder(1, 3, 2, 0, 4);

        // first we compare dijkstra with CH to make sure they produce the same results
        int from = 0;
        int to = 4;
        Dijkstra dikstra = new Dijkstra(graph, weighting, TraversalMode.NODE_BASED);
        Path dijkstraPath = dikstra.calcPath(from, to);

        DijkstraBidirectionCH ch = new DijkstraBidirectionCH(new RoutingCHGraphImpl(lg, weighting));
        Path chPath = ch.calcPath(from, to);
        assertEquals(dijkstraPath.calcNodes(), chPath.calcNodes());
        assertEquals(dijkstraPath.getDistance(), chPath.getDistance(), 1.e-6);
        assertEquals(dijkstraPath.getWeight(), chPath.getWeight(), 1.e-6);

        // on a more detailed level we check that the right shortcuts were added
        // contracting nodes 1&3 will always introduce shortcuts, but contracting node 2 should not because going from
        // 0 to 4 directly via edge 4 is cheaper. however, if shortcut distances get truncated it appears as if going
        // via node 2 is better. here we check that this does not happen.
        checkShortcuts(
                expectedShortcut(0, 2, edge1, edge2, true, false),
                expectedShortcut(2, 4, edge3, edge4, true, false)
        );
    }

    /**
     * similar to the previous test, but using the fastest weighting
     */
    @Test
    public void testNodeContraction_shortcutWeightRounding() {
        CarFlagEncoder encoder = new CarFlagEncoder();
        EncodingManager encodingManager = EncodingManager.create(encoder);
        Weighting weighting = new FastestWeighting(encoder);
        GraphHopperStorage graph = new GraphBuilder(encodingManager).setCHConfigs(CHConfig.nodeBased("p1", weighting)).create();
        CHGraph lg = graph.getCHGraph();
        PrepareCHGraph pg = PrepareCHGraph.nodeBased(lg, weighting);
        // 0 ------------> 4
        //  \             /
        //   1 --> 2 --> 3
        double fac = 60 / 3.6;
        double[] distances = {fac * 4.019, fac * 1.006, fac * 1.004, fac * 1.006, fac * 1.004};
        graph.edge(0, 4, distances[0], false);
        graph.edge(0, 1, distances[1], false);
        graph.edge(1, 2, distances[2], false);
        graph.edge(2, 3, distances[3], false);
        graph.edge(3, 4, distances[4], false);
        graph.freeze();
        setMaxLevelOnAllNodes(pg);

        // perform CH contraction
        contractInOrder(pg, 1, 3, 2, 0, 4);

        // first we compare dijkstra with CH to make sure they produce the same results
        int from = 0;
        int to = 4;
        Dijkstra dikstra = new Dijkstra(graph, weighting, TraversalMode.NODE_BASED);
        Path dijkstraPath = dikstra.calcPath(from, to);

        DijkstraBidirectionCH ch = new DijkstraBidirectionCH(new RoutingCHGraphImpl(lg, weighting));
        Path chPath = ch.calcPath(from, to);
        assertEquals(dijkstraPath.calcNodes(), chPath.calcNodes());
        assertEquals(dijkstraPath.getDistance(), chPath.getDistance(), 1.e-6);
        assertEquals(dijkstraPath.getWeight(), chPath.getWeight(), 1.e-6);
    }

    @Test
    public void testNodeContraction_preventUnnecessaryShortcutWithLoop() {
        // there should not be shortcuts where one of the skipped edges is a loop at the node to be contracted,
        // see also #1583
        CarFlagEncoder encoder = new CarFlagEncoder();
        EncodingManager encodingManager = EncodingManager.create(encoder);
        Weighting weighting = new FastestWeighting(encoder);
        GraphHopperStorage graph = new GraphBuilder(encodingManager).setCHConfigs(CHConfig.nodeBased("p1", weighting)).create();
        CHGraph lg = graph.getCHGraph();
        PrepareCHGraph pg = PrepareCHGraph.nodeBased(lg, weighting);
        // 0 - 1 - 2 - 3
        // o           o
        graph.edge(0, 1, 1, true);
        graph.edge(1, 2, 1, true);
        graph.edge(2, 3, 1, true);
        graph.edge(0, 0, 1, true);
        graph.edge(3, 3, 1, true);

        graph.freeze();
        setMaxLevelOnAllNodes(pg);
        NodeContractor nodeContractor = createNodeContractor(pg);
        nodeContractor.contractNode(0);
        nodeContractor.contractNode(3);
        checkNoShortcuts(pg);
    }

    private void contractInOrder(int... nodeIds) {
        contractInOrder(pg, nodeIds);
    }

    private void contractInOrder(PrepareCHGraph chGraph, int... nodeIds) {
        NodeContractor nodeContractor = createNodeContractor(chGraph);
        int level = 0;
        for (int n : nodeIds) {
            nodeContractor.contractNode(n);
            chGraph.setLevel(n, level);
            level++;
        }
    }

    /**
     * Queries the ch graph and checks if the graph's shortcuts match the given expected shortcuts.
     */
    private void checkShortcuts(Shortcut... expectedShortcuts) {
        checkShortcuts(pg, expectedShortcuts);
    }

    private void checkShortcuts(PrepareCHGraph chGraph, Shortcut... expectedShortcuts) {
        Set<Shortcut> expected = setOf(expectedShortcuts);
        if (expected.size() != expectedShortcuts.length) {
            fail("was given duplicate shortcuts");
        }
        AllCHEdgesIterator iter = chGraph.getAllEdges();
        Set<Shortcut> given = new HashSet<>();
        while (iter.next()) {
            if (iter.isShortcut()) {
                given.add(new Shortcut(
                        iter.getBaseNode(), iter.getAdjNode(), iter.getWeight(),
                        iter.get(SC_ACCESS), iter.getReverse(SC_ACCESS),
                        iter.getSkippedEdge1(), iter.getSkippedEdge2()));
            }
        }
        assertEquals(expected, given);
    }

    private void checkNoShortcuts() {
        checkShortcuts(pg);
    }

    private void checkNoShortcuts(PrepareCHGraph chGraph) {
        checkShortcuts(chGraph);
    }

    private Shortcut expectedShortcut(int baseNode, int adjNode, EdgeIteratorState edge1, EdgeIteratorState edge2,
                                      boolean fwd, boolean bwd) {
        //todo: weight calculation might have to be adjusted for different encoders/weightings/reverse speed
        double weight1 = getWeight(edge1);
        double weight2 = getWeight(edge2);
        return new Shortcut(baseNode, adjNode, weight1 + weight2, fwd, bwd, edge1.getEdge(), edge2.getEdge());
    }

    private double getWeight(EdgeIteratorState edge) {
        if (edge instanceof CHEdgeIteratorState) {
            return ((CHEdgeIteratorState) edge).getWeight();
        } else {
            return weighting.calcEdgeWeight(edge, false);
        }
    }

    private Set<Shortcut> setOf(Shortcut... shortcuts) {
        return new HashSet<>(Arrays.asList(shortcuts));
    }

    private void setMaxLevelOnAllNodes() {
        setMaxLevelOnAllNodes(pg);
    }

    private void setMaxLevelOnAllNodes(PrepareCHGraph chGraph) {
        int nodes = chGraph.getNodes();
        for (int node = 0; node < nodes; node++) {
            chGraph.setLevel(node, nodes);
        }
    }

    private static class Shortcut {
        int baseNode;
        int adjNode;
        double weight;
        boolean fwd;
        boolean bwd;
        int skipEdge1;
        int skipEdge2;

        Shortcut(int baseNode, int adjNode, double weight, boolean fwd, boolean bwd, int skipEdge1, int skipEdge2) {
            this.baseNode = baseNode;
            this.adjNode = adjNode;
            this.weight = weight;
            this.fwd = fwd;
            this.bwd = bwd;
            this.skipEdge1 = skipEdge1;
            this.skipEdge2 = skipEdge2;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (obj == null || getClass() != obj.getClass()) return false;
            Shortcut shortcut = (Shortcut) obj;
            return baseNode == shortcut.baseNode &&
                    adjNode == shortcut.adjNode &&
                    Double.compare(shortcut.weight, weight) == 0 &&
                    fwd == shortcut.fwd &&
                    bwd == shortcut.bwd &&
                    skipEdge1 == shortcut.skipEdge1 &&
                    skipEdge2 == shortcut.skipEdge2;
        }

        @Override
        public int hashCode() {
            return Objects.hash(baseNode, adjNode, weight, fwd, bwd, skipEdge1, skipEdge2);
        }

        @Override
        public String toString() {
            return "Shortcut{" +
                    "baseNode=" + baseNode +
                    ", adjNode=" + adjNode +
                    ", weight=" + weight +
                    ", fwd=" + fwd +
                    ", bwd=" + bwd +
                    ", skipEdge1=" + skipEdge1 +
                    ", skipEdge2=" + skipEdge2 +
                    '}';
        }
    }
}