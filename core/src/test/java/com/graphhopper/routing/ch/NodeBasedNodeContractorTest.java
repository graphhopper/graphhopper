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
import com.graphhopper.routing.DijkstraOneToMany;
import com.graphhopper.routing.Path;
import com.graphhopper.routing.profiles.BooleanEncodedValue;
import com.graphhopper.routing.profiles.EncodedValue;
import com.graphhopper.routing.profiles.SimpleBooleanEncodedValue;
import com.graphhopper.routing.util.*;
import com.graphhopper.routing.weighting.FastestWeighting;
import com.graphhopper.routing.weighting.ShortestWeighting;
import com.graphhopper.routing.weighting.Weighting;
import com.graphhopper.storage.CHGraph;
import com.graphhopper.storage.GraphBuilder;
import com.graphhopper.storage.GraphHopperStorage;
import com.graphhopper.util.CHEdgeIteratorState;
import com.graphhopper.util.EdgeIterator;
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
    private final GraphHopperStorage graph = new GraphBuilder(encodingManager).setCHGraph(weighting).create();
    private final CHGraph lg = graph.getGraph(CHGraph.class);
    private final TraversalMode traversalMode = TraversalMode.NODE_BASED;

    private NodeContractor createNodeContractor() {
        return createNodeContractor(lg, weighting);
    }

    private NodeContractor createNodeContractor(CHGraph chGraph, Weighting weighting) {
        NodeContractor nodeContractor = new NodeBasedNodeContractor(chGraph, weighting, new PMap());
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
        final double normalDist = new Dijkstra(graph, weighting, traversalMode).calcPath(4, 2).getDistance();
        DijkstraOneToMany algo = new DijkstraOneToMany(graph, weighting, traversalMode);

        setMaxLevelOnAllNodes();

        algo.setEdgeFilter(createIgnoreNodeFilter(3));
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
        final double normalDist = new Dijkstra(graph, weighting, traversalMode).calcPath(4, 2).getDistance();
        assertEquals(3, normalDist, 1e-5);
        DijkstraOneToMany algo = new DijkstraOneToMany(graph, weighting, traversalMode);

        setMaxLevelOnAllNodes();

        algo.setEdgeFilter(createIgnoreNodeFilter(3));
        algo.setWeightLimit(10);
        int nodeEntry = algo.findEndNode(4, 2);
        assertEquals(4, algo.getWeight(nodeEntry), 1e-5);

        nodeEntry = algo.findEndNode(4, 1);
        assertEquals(4, algo.getWeight(nodeEntry), 1e-5);
    }

    @Test
    public void testShortestPathLimit() {
        createExampleGraph();
        DijkstraOneToMany algo = new DijkstraOneToMany(graph, weighting, traversalMode);

        setMaxLevelOnAllNodes();

        algo.setEdgeFilter(createIgnoreNodeFilter(0));
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

        CHEdgeIteratorState sc1to4 = lg.shortcut(1, 4);
        sc1to4.setFlagsAndWeight(PrepareEncoder.getScDirMask(), 2);
        sc1to4.setDistance(2);
        sc1to4.setSkippedEdges(iter1to3.getEdge(), iter3to4.getEdge());

        CHEdgeIteratorState sc4to6 = lg.shortcut(4, 6);
        sc4to6.setFlagsAndWeight(PrepareEncoder.getScFwdDir(), 2);
        sc4to6.setDistance(2);
        sc4to6.setSkippedEdges(iter4to5.getEdge(), iter5to6.getEdge());

        CHEdgeIteratorState sc6to4 = lg.shortcut(6, 4);
        sc6to4.setFlagsAndWeight(PrepareEncoder.getScFwdDir(), 3);
        sc6to4.setDistance(3);
        sc6to4.setSkippedEdges(iter6to8.getEdge(), iter8to4.getEdge());

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
                expectedShortcut(1, 6, sc1to4, sc4to6, true, false),
                expectedShortcut(6, 1, sc6to4, sc1to4, true, false)
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

        DijkstraBidirectionCH ch = new DijkstraBidirectionCH(lg, new PreparationWeighting(weighting));
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
        GraphHopperStorage graph = new GraphBuilder(encodingManager).setCHGraph(weighting).create();
        CHGraph lg = graph.getGraph(CHGraph.class);
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
        setMaxLevelOnAllNodes(lg);

        // perform CH contraction
        contractInOrder(lg, weighting, 1, 3, 2, 0, 4);

        // first we compare dijkstra with CH to make sure they produce the same results
        int from = 0;
        int to = 4;
        Dijkstra dikstra = new Dijkstra(graph, weighting, TraversalMode.NODE_BASED);
        Path dijkstraPath = dikstra.calcPath(from, to);

        DijkstraBidirectionCH ch = new DijkstraBidirectionCH(lg, new PreparationWeighting(weighting));
        Path chPath = ch.calcPath(from, to);
        assertEquals(dijkstraPath.calcNodes(), chPath.calcNodes());
        assertEquals(dijkstraPath.getDistance(), chPath.getDistance(), 1.e-6);
        assertEquals(dijkstraPath.getWeight(), chPath.getWeight(), 1.e-6);
    }

    private void contractInOrder(int... nodeIds) {
        contractInOrder(lg, weighting, nodeIds);
    }

    private void contractInOrder(CHGraph chGraph, Weighting weighting, int... nodeIds) {
        NodeContractor nodeContractor = createNodeContractor(chGraph, weighting);
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
                        iter.get(SC_ACCESS), iter.getReverse(SC_ACCESS),
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

    private IgnoreNodeFilter createIgnoreNodeFilter(int node) {
        return new IgnoreNodeFilter(lg, graph.getNodes()).setAvoidNode(node);
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
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (obj == null || getClass() != obj.getClass()) return false;
            Shortcut shortcut = (Shortcut) obj;
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