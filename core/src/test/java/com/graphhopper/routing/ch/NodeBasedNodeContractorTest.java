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
import com.graphhopper.util.GHUtility;
import com.graphhopper.util.PMap;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

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

    private NodeContractor createNodeContractor() {
        return createNodeContractor(lg);
    }

    private NodeContractor createNodeContractor(CHGraph chGraph) {
        CHPreparationGraph prepareGraph = CHPreparationGraph.nodeBased(chGraph.getNodes(), chGraph.getOriginalEdges());
        CHPreparationGraph.buildFromGraph(prepareGraph, chGraph.getBaseGraph(), chGraph.getCHConfig().getWeighting());
        NodeBasedNodeContractor.ShortcutHandler shortcutInserter = new NodeBasedShortcutInserter(chGraph);
        NodeContractor nodeContractor = new NodeBasedNodeContractor(prepareGraph, shortcutInserter, new PMap());
        nodeContractor.initFromGraph();
        nodeContractor.prepareContraction();
        return nodeContractor;
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    public void testDirectedGraph(boolean reverse) {
        //5 6 7
        // \|/
        //4-3_1<-\ 10
        //     \_|/
        //   0___2_11
        GHUtility.setSpeed(60, true, true, encoder, graph.edge(0, 2).setDistance(2));
        GHUtility.setSpeed(60, true, true, encoder, graph.edge(10, 2).setDistance(2));
        GHUtility.setSpeed(60, true, true, encoder, graph.edge(11, 2).setDistance(2));
        // create a longer one directional edge => no longish one-dir shortcut should be created
        final EdgeIteratorState edge2to1bidirected = GHUtility.setSpeed(60, true, true, encoder, graph.edge(2, 1).setDistance(2));
        final EdgeIteratorState edge2to1directed = GHUtility.setSpeed(60, true, false, encoder, graph.edge(2, 1).setDistance(10));
        final EdgeIteratorState edge1to3 = GHUtility.setSpeed(60, true, true, encoder, graph.edge(1, 3).setDistance(2));
        GHUtility.setSpeed(60, true, true, encoder, graph.edge(3, 4).setDistance(2));
        GHUtility.setSpeed(60, true, true, encoder, graph.edge(3, 5).setDistance(2));
        GHUtility.setSpeed(60, true, true, encoder, graph.edge(3, 6).setDistance(2));
        GHUtility.setSpeed(60, true, true, encoder, graph.edge(3, 7).setDistance(2));
        graph.freeze();

        setMaxLevelOnAllNodes();

        // find all shortcuts if we contract node 1 first, the order in which we contract nodes 2 and 3 decides
        // what kind of shortcut is added. in any case it uses the shorter bidirected edge instead of the longer
        // directed one
        if (reverse) {
            contractInOrder(1, 0, 11, 10, 4, 5, 6, 7, 3, 2);
            checkShortcuts(expectedShortcut(3, 2, edge1to3, edge2to1bidirected, true, true));
        } else {
            contractInOrder(1, 0, 11, 10, 4, 5, 6, 7, 2, 3);
            checkShortcuts(expectedShortcut(2, 3, edge2to1bidirected, edge1to3, true, true));
        }
    }

    @Test
    public void testFindShortcuts_Roundabout() {
        // 1 -- 3 -- 4 ---> 5 ---> 6 -- 7
        //            \           /
        //             <--- 8 <--- 
        final EdgeIteratorState iter1to3 = GHUtility.setSpeed(60, true, true, encoder, graph.edge(1, 3).setDistance(1));
        final EdgeIteratorState iter3to4 = GHUtility.setSpeed(60, true, true, encoder, graph.edge(3, 4).setDistance(1));
        final EdgeIteratorState iter4to5 = GHUtility.setSpeed(60, true, false, encoder, graph.edge(4, 5).setDistance(1));
        final EdgeIteratorState iter5to6 = GHUtility.setSpeed(60, true, false, encoder, graph.edge(5, 6).setDistance(1));
        final EdgeIteratorState iter6to8 = GHUtility.setSpeed(60, true, false, encoder, graph.edge(6, 8).setDistance(2));
        final EdgeIteratorState iter8to4 = GHUtility.setSpeed(60, true, false, encoder, graph.edge(8, 4).setDistance(1));
        GHUtility.setSpeed(60, true, true, encoder, graph.edge(6, 7).setDistance(1));
        graph.freeze();

        contractInOrder(3, 5, 7, 8, 4, 1, 6);
        // note: after contraction of nodes 3, 5, 8 the graph looks like this:
        // 1 -- 4 -->-- 6 -- 7
        //       \      |
        //        --<----

        checkShortcuts(
                expectedShortcut(4, 1, iter3to4, iter1to3, true, true),
                expectedShortcut(4, 6, iter8to4, iter6to8, false, true),
                expectedShortcut(4, 6, iter4to5, iter5to6, true, false),
                // there should be two different shortcuts for both directions!
                expectedShortcut(1, 6, lg.getEdgeIteratorState(8, 4), lg.getEdgeIteratorState(7, 6), true, false),
                expectedShortcut(1, 6, lg.getEdgeIteratorState(8, 1), lg.getEdgeIteratorState(9, 4), false, true)
        );
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    public void testShortcutMergeBug(boolean reverse) {
        // We refer to this real world situation http://www.openstreetmap.org/#map=19/52.71205/-1.77326
        // assume the following graph:
        //
        // ---1---->----2-----3
        //    \--------/
        //
        // where there are two roads from 1 to 2 and the directed road has a smaller weight. to get from 2 to 1 we
        // have to use the bidirectional edge despite the higher weight and therefore we need an extra shortcut for
        // this.
        final EdgeIteratorState edge1to2bidirected = GHUtility.setSpeed(60, true, true, encoder, graph.edge(1, 2).setDistance(2));
        final EdgeIteratorState edge1to2directed = GHUtility.setSpeed(60, true, false, encoder, graph.edge(1, 2).setDistance(1));
        final EdgeIteratorState edge2to3 = GHUtility.setSpeed(60, true, true, encoder, graph.edge(2, 3).setDistance(1));
        graph.freeze();
        setMaxLevelOnAllNodes();
        if (reverse) {
            contractInOrder(2, 1, 3);
            checkShortcuts(
                    expectedShortcut(1, 3, edge1to2directed, edge2to3, true, false),
                    expectedShortcut(1, 3, edge1to2bidirected, edge2to3, false, true)
            );
        } else {
            contractInOrder(2, 3, 1);
            checkShortcuts(
                    expectedShortcut(3, 1, edge2to3, edge1to2bidirected, true, false),
                    expectedShortcut(3, 1, edge2to3, edge1to2directed, false, true)
            );
        }
    }

    @Test
    public void testContractNode_directed_shortcutRequired() {
        // 0 --> 1 --> 2
        final EdgeIteratorState edge1 = GHUtility.setSpeed(60, true, false, encoder, graph.edge(0, 1).setDistance(1));
        final EdgeIteratorState edge2 = GHUtility.setSpeed(60, true, false, encoder, graph.edge(1, 2).setDistance(2));
        graph.freeze();
        setMaxLevelOnAllNodes();
        contractInOrder(1, 0, 2);
        checkShortcuts(expectedShortcut(0, 2, edge1, edge2, true, false));
    }

    @Test
    public void testContractNode_directed_shortcutRequired_reverse() {
        // 0 <-- 1 <-- 2
        final EdgeIteratorState edge1 = GHUtility.setSpeed(60, true, false, encoder, graph.edge(2, 1).setDistance(1));
        final EdgeIteratorState edge2 = GHUtility.setSpeed(60, true, false, encoder, graph.edge(1, 0).setDistance(2));
        graph.freeze();
        setMaxLevelOnAllNodes();
        contractInOrder(1, 2, 0);
        checkShortcuts(expectedShortcut(2, 0, edge1, edge2, true, false));
    }

    @Test
    public void testContractNode_bidirected_shortcutsRequired() {
        // 0 -- 1 -- 2
        final EdgeIteratorState edge1 = GHUtility.setSpeed(60, true, true, encoder, graph.edge(0, 1).setDistance(1));
        final EdgeIteratorState edge2 = GHUtility.setSpeed(60, true, true, encoder, graph.edge(1, 2).setDistance(2));
        graph.freeze();
        contractInOrder(1, 2, 0);
        checkShortcuts(expectedShortcut(2, 0, edge2, edge1, true, true));
    }

    @Test
    public void testContractNode_directed_withWitness() {
        // 0 --> 1 --> 2
        //  \_________/
        GHUtility.setSpeed(60, true, false, encoder, graph.edge(0, 1).setDistance(1));
        GHUtility.setSpeed(60, true, false, encoder, graph.edge(1, 2).setDistance(2));
        GHUtility.setSpeed(60, true, false, encoder, graph.edge(0, 2).setDistance(1));
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
        GHUtility.setSpeed(60, true, false, encoder, graph.edge(0, 4).setDistance(distances[0]));
        EdgeIteratorState edge1 = GHUtility.setSpeed(60, true, false, encoder, graph.edge(0, 1).setDistance(distances[1]));
        EdgeIteratorState edge2 = GHUtility.setSpeed(60, true, false, encoder, graph.edge(1, 2).setDistance(distances[2]));
        EdgeIteratorState edge3 = GHUtility.setSpeed(60, true, false, encoder, graph.edge(2, 3).setDistance(distances[3]));
        EdgeIteratorState edge4 = GHUtility.setSpeed(60, true, false, encoder, graph.edge(3, 4).setDistance(distances[4]));
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

        DijkstraBidirectionCH ch = new DijkstraBidirectionCH(new RoutingCHGraphImpl(lg));
        Path chPath = ch.calcPath(from, to);
        assertEquals(dijkstraPath.calcNodes(), chPath.calcNodes());
        assertEquals(dijkstraPath.getDistance(), chPath.getDistance(), 1.e-6);
        assertEquals(dijkstraPath.getWeight(), chPath.getWeight(), 1.e-6);

        // on a more detailed level we check that the right shortcuts were added
        // contracting nodes 1&3 will always introduce shortcuts, but contracting node 2 should not because going from
        // 0 to 4 directly via edge 4 is cheaper. however, if shortcut distances get truncated it appears as if going
        // via node 2 is better. here we check that this does not happen.
        checkShortcuts(
                expectedShortcut(2, 0, edge2, edge1, false, true),
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
        // 0 ------------> 4
        //  \             /
        //   1 --> 2 --> 3
        double fac = 60 / 3.6;
        double[] distances = {fac * 4.019, fac * 1.006, fac * 1.004, fac * 1.006, fac * 1.004};
        GHUtility.setSpeed(60, true, false, encoder, graph.edge(0, 4).setDistance(distances[0]));
        GHUtility.setSpeed(60, true, false, encoder, graph.edge(0, 1).setDistance(distances[1]));
        GHUtility.setSpeed(60, true, false, encoder, graph.edge(1, 2).setDistance(distances[2]));
        GHUtility.setSpeed(60, true, false, encoder, graph.edge(2, 3).setDistance(distances[3]));
        GHUtility.setSpeed(60, true, false, encoder, graph.edge(3, 4).setDistance(distances[4]));
        graph.freeze();
        setMaxLevelOnAllNodes(lg);

        // perform CH contraction
        contractInOrder(lg, 1, 3, 2, 0, 4);

        // first we compare dijkstra with CH to make sure they produce the same results
        int from = 0;
        int to = 4;
        Dijkstra dikstra = new Dijkstra(graph, weighting, TraversalMode.NODE_BASED);
        Path dijkstraPath = dikstra.calcPath(from, to);

        DijkstraBidirectionCH ch = new DijkstraBidirectionCH(new RoutingCHGraphImpl(lg));
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
        // 0 - 1 - 2 - 3
        // o           o
        GHUtility.setSpeed(60, true, true, encoder, graph.edge(0, 1).setDistance(1));
        GHUtility.setSpeed(60, true, true, encoder, graph.edge(1, 2).setDistance(1));
        GHUtility.setSpeed(60, true, true, encoder, graph.edge(2, 3).setDistance(1));
        GHUtility.setSpeed(60, true, true, encoder, graph.edge(0, 0).setDistance(1));
        GHUtility.setSpeed(60, true, true, encoder, graph.edge(3, 3).setDistance(1));

        graph.freeze();
        setMaxLevelOnAllNodes(lg);
        NodeContractor nodeContractor = createNodeContractor(lg);
        nodeContractor.contractNode(0);
        nodeContractor.contractNode(3);
        checkNoShortcuts(lg);
    }

    private void contractInOrder(int... nodeIds) {
        contractInOrder(lg, nodeIds);
    }

    private void contractInOrder(CHGraph chGraph, int... nodeIds) {
        setMaxLevelOnAllNodes();
        NodeContractor nodeContractor = createNodeContractor(chGraph);
        int level = 0;
        for (int n : nodeIds) {
            chGraph.setLevel(n, level);
            nodeContractor.contractNode(n);
            level++;
        }
        nodeContractor.finishContraction();
    }

    /**
     * Queries the ch graph and checks if the graph's shortcuts match the given expected shortcuts.
     */
    private void checkShortcuts(Shortcut... expectedShortcuts) {
        checkShortcuts(lg, expectedShortcuts);
    }

    private void checkShortcuts(CHGraph chGraph, Shortcut... expectedShortcuts) {
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
        checkShortcuts(lg);
    }

    private void checkNoShortcuts(CHGraph chGraph) {
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
        setMaxLevelOnAllNodes(lg);
    }

    private void setMaxLevelOnAllNodes(CHGraph chGraph) {
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