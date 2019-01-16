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
package com.graphhopper.routing;

import com.graphhopper.routing.util.CarFlagEncoder;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.routing.util.FlagEncoder;
import com.graphhopper.routing.util.TraversalMode;
import com.graphhopper.routing.weighting.FastestWeighting;
import com.graphhopper.routing.weighting.Weighting;
import com.graphhopper.storage.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import static com.graphhopper.routing.AbstractRoutingAlgorithmTester.updateDistancesFor;
import static com.graphhopper.util.Parameters.Algorithms.ALT_ROUTE;
import static org.junit.Assert.*;

@RunWith(Parameterized.class)
public class AlternativeRouteTest {
    private final FlagEncoder encoder = new CarFlagEncoder();
    private final EncodingManager manager = new EncodingManager(encoder);
    private final Weighting weighting = new FastestWeighting(encoder);
    private final TraversalMode tMode;

    public AlternativeRouteTest(TraversalMode tMode) {
        this.tMode = tMode;
    }

    /**
     * Runs the same test with each of the supported traversal modes
     */
    @Parameterized.Parameters(name = "{0}")
    public static Collection<Object[]> configs() {
        return Arrays.asList(new Object[][]{
                {TraversalMode.NODE_BASED},
                {TraversalMode.EDGE_BASED_2DIR}
        });
    }

    public GraphHopperStorage createTestGraph(boolean fullGraph, EncodingManager tmpEM) {
        GraphHopperStorage graph = new GraphHopperStorage(new RAMDirectory(), tmpEM, false, new GraphExtension.NoOpExtension());
        graph.create(1000);

        /* 9
         _/\
         1  2-3-4-10
         \   /   \
         5--6-7---8

         */
        graph.edge(1, 9, 1, true);
        graph.edge(9, 2, 1, true);
        if (fullGraph)
            graph.edge(2, 3, 1, true);
        graph.edge(3, 4, 1, true);
        graph.edge(4, 10, 1, true);

        graph.edge(5, 6, 1, true);

        graph.edge(6, 7, 1, true);
        graph.edge(7, 8, 1, true);

        if (fullGraph)
            graph.edge(1, 5, 2, true);
        graph.edge(6, 3, 1, true);
        graph.edge(4, 8, 1, true);

        updateDistancesFor(graph, 5, 0.00, 0.05);
        updateDistancesFor(graph, 6, 0.00, 0.10);
        updateDistancesFor(graph, 7, 0.00, 0.15);
        updateDistancesFor(graph, 8, 0.00, 0.25);

        updateDistancesFor(graph, 1, 0.05, 0.00);
        updateDistancesFor(graph, 9, 0.10, 0.05);
        updateDistancesFor(graph, 2, 0.05, 0.10);
        updateDistancesFor(graph, 3, 0.05, 0.15);
        updateDistancesFor(graph, 4, 0.05, 0.25);
        updateDistancesFor(graph, 10, 0.05, 0.30);
        return graph;
    }

    @Test
    public void GraphPartitionTest() {
        int areas = 8;
        GraphPartition partition = new GraphPartition(createTestGraph(true, manager), areas);
        partition.doWork();

        assertTrue(partition.getNodes(0).contains(0));

        assertTrue(partition.getNodes(1).contains(1));
        assertTrue(partition.getNodes(1).contains(9));

        assertTrue(partition.getNodes(2).contains(2));
        assertTrue(partition.getNodes(2).contains(3));

        assertTrue(partition.getNodes(3).contains(4));
        assertTrue(partition.getNodes(3).contains(10));

        assertTrue(partition.getNodes(4).contains(5));

        assertTrue(partition.getNodes(5).contains(6));

        assertTrue(partition.getNodes(6).contains(7));

        assertTrue(partition.getNodes(7).contains(8));

        for (int i = 0; i < areas; i++)
            for (int j : partition.getNodes(i))
                assertEquals(i, partition.getArea(j));
    }

    /**
     * check that AlternativeRoute.calcPath really is the shortest path
     */
    @Test
    public void compareShortestPath() {
        for (int from = 1; from <= 10; from++) {
            for (int to = from + 1; to <= 10; to++) {
                AlternativeRoute altRoute = new AlternativeRoute(createTestGraph(true, manager), weighting, tMode);
                Dijkstra dijkstra = new Dijkstra(createTestGraph(true, manager), weighting, tMode);
                assertEquals(altRoute.calcPath(from, to).calcNodes(), dijkstra.calcPath(from, to).calcNodes());
            }
        }
    }

    /**
     * check that each alternaive is different to the shortest path and to each other
     */
    @Test
    public void compareAlternatives() {
        for (int from = 1; from <= 10; from++) {
            for (int to = from + 1; to <= 10; to++) {
                AlternativeRoute altRoute = new AlternativeRoute(createTestGraph(true, manager), weighting, tMode);
                List<Path> paths = altRoute.calcPaths(from, to);
                if (paths.size() == 2) {
                    assertNotEquals(paths.get(0).calcNodes(), paths.get(1).calcNodes());
                    assertTrue(paths.get(0).getWeight() <= paths.get(1).getWeight());
                } else if (paths.size() == 3) {
                    assertNotEquals(paths.get(0).calcNodes(), paths.get(1).calcNodes());
                    assertTrue(paths.get(0).getWeight() <= paths.get(1).getWeight());
                    assertNotEquals(paths.get(0).calcNodes(), paths.get(2).calcNodes());
                    assertTrue(paths.get(0).getWeight() <= paths.get(2).getWeight());
                    assertNotEquals(paths.get(1).calcNodes(), paths.get(2).calcNodes());
                }
            }
        }
    }

    /**
     * check that the shortest path contains node 9 and the best alterntive node 5 (the edgebased traversalmode finds
     * another alternative running over node 8)
     */
    @Test
    public void testNoPrepare_1_10() {
        AlternativeRoute altRoute = new AlternativeRoute(createTestGraph(true, manager), weighting, tMode);
        List<Path> paths = altRoute.calcPaths(1, 10);
        assertTrue(paths.size() > 1);
        assertTrue(paths.get(0).calcNodes().contains(9));
        assertTrue(paths.get(1).calcNodes().contains(5));
        if (paths.size() == 3)
            assertTrue(paths.get(2).calcNodes().contains(8));

        assertFalse(altRoute.isLongAlgo());
    }

    /**
     * check that the prepared algorithm has the same output as the unprepared one
     */
    @Test
    public void testPrepare_1_10() {
        Graph graph = createTestGraph(true, manager);
        PrepareAlternativeRoute prepare = new PrepareAlternativeRoute(graph, weighting, tMode);
        prepare.doWork();
        assertTrue(prepare.getViaNodes().get(1, 10).contains(7));

        AlternativeRoute altRoute = (AlternativeRoute) prepare.createAlgo(graph, new AlgorithmOptions(ALT_ROUTE, weighting, tMode));
        List<Path> paths = altRoute.calcPaths(1, 10);
        assertTrue(paths.size() > 1);
        assertTrue(paths.get(0).calcNodes().contains(9));
        assertTrue(paths.get(1).calcNodes().contains(5));
        if (paths.size() == 3)
            assertTrue(paths.get(2).calcNodes().contains(8));

        AlternativeRoute altRoute2 = new AlternativeRoute(createTestGraph(true, manager), weighting, tMode);
        List<Path> paths2 = altRoute2.calcPaths(1, 10);
        for (int i = 0; i < paths.size(); i++) {
            assertEquals(paths.get(i).calcNodes(), paths2.get(i).calcNodes());
        }

        assertTrue(altRoute.isLongAlgo());
    }

    /**
     * check that the prepared algorithm has the same output as the unprepared one. In this case the areas of both
     * nodes are directly connected to each other. This means that the prepared algorithm will be using the unprepared
     * one (longAlgorithm == false)
     */
    @Test
    public void testPrepare_1_3() {
        Graph graph = createTestGraph(true, manager);
        PrepareAlternativeRoute prepare = new PrepareAlternativeRoute(graph, weighting, tMode);
        prepare.doWork();
        assertEquals(null, prepare.getViaNodes().get(1, 3));

        AlternativeRoute altRoute = (AlternativeRoute) prepare.createAlgo(graph, new AlgorithmOptions(ALT_ROUTE, weighting, tMode));
        List<Path> paths = altRoute.calcPaths(1, 3);
        assertTrue(paths.size() > 1);
        assertTrue(paths.get(0).calcNodes().contains(9));
        assertTrue(paths.get(1).calcNodes().contains(5));

        AlternativeRoute altRoute2 = new AlternativeRoute(createTestGraph(true, manager), weighting, tMode);
        List<Path> paths2 = altRoute2.calcPaths(1, 3);
        for (int i = 0; i < paths.size(); i++) {
            assertEquals(paths.get(i).calcNodes(), paths2.get(i).calcNodes());
        }

        assertFalse(altRoute.isLongAlgo());
    }

    /**
     * check that only a small part of the graph is traversed if the nodes lie in disconnected areas
     */
    @Test
    public void testDisconnectedAreas() {
        Graph graph = createTestGraph(true, manager);

        // one single disconnected node
        updateDistancesFor(graph, 20, 0.00, -0.01);

        AlternativeRoute altRoute = new AlternativeRoute(createTestGraph(true, manager), weighting, tMode);
        Path path = altRoute.calcPath(1, 20);
        assertFalse(path.isFound());

        // make sure not the full graph is traversed!
        assertEquals(3, altRoute.getVisitedNodes());
    }
}
