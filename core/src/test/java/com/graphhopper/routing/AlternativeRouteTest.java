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

import com.graphhopper.routing.ar.GraphPartition;
import com.graphhopper.routing.ar.PrepareAlternativeRoute;
import com.graphhopper.routing.ch.PrepareContractionHierarchies;
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

import java.util.*;

import static com.graphhopper.routing.AbstractRoutingAlgorithmTester.updateDistancesFor;
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
        List<Weighting> chWeightings = new ArrayList<>(1);
        chWeightings.add(weighting);
        GraphHopperStorage graph = new GraphHopperStorage(chWeightings, new RAMDirectory(), tmpEM, false, new GraphExtension.NoOpExtension());
        graph.create(1000);

        /*

           8
         _/\
         0  1-2-3-9
         \   /   \
         4--5-6---7

         */

        graph.edge(0, 8, 1, true);
        graph.edge(8, 1, 1, true);
        if (fullGraph)
            graph.edge(1, 2, 1, true);
        graph.edge(2, 3, 1, true);
        graph.edge(3, 9, 1, true);

        graph.edge(4, 5, 1, true);

        graph.edge(5, 6, 1, true);
        graph.edge(6, 7, 1, true);

        if (fullGraph)
            graph.edge(0, 4, 2, true);
        graph.edge(5, 2, 1, true);
        graph.edge(3, 7, 1, true);

        updateDistancesFor(graph, 4, 0.00, 0.05);
        updateDistancesFor(graph, 5, 0.00, 0.10);
        updateDistancesFor(graph, 6, 0.00, 0.15);
        updateDistancesFor(graph, 7, 0.00, 0.25);

        updateDistancesFor(graph, 0, 0.05, 0.00);
        updateDistancesFor(graph, 8, 0.10, 0.05);
        updateDistancesFor(graph, 1, 0.05, 0.10);
        updateDistancesFor(graph, 2, 0.05, 0.15);
        updateDistancesFor(graph, 3, 0.05, 0.25);
        updateDistancesFor(graph, 9, 0.05, 0.30);
        return graph;
    }

    public PrepareAlternativeRoute prepareAR(GraphHopperStorage graph, int areas, boolean CH) {
        GraphPartition partition = new GraphPartition(graph, areas);
        partition.doWork();
        RoutingAlgorithmFactory algoFactory;
        if (CH) {
            CHGraph chGraph = graph.getGraph(CHGraph.class);
            PrepareContractionHierarchies pch = new PrepareContractionHierarchies(graph, chGraph, tMode);
            pch.doWork();
            algoFactory = pch;
        } else {
            algoFactory = new RoutingAlgorithmFactorySimple();
        }
        PrepareAlternativeRoute par = new PrepareAlternativeRoute(graph, weighting, partition, algoFactory);
        par.doWork();
        return par;
    }

    public RoutingAlgorithm createAlgo(GraphHopperStorage graph, boolean CH, PrepareAlternativeRoute par) {
        AlgorithmOptions opts = new AlgorithmOptions("alternative_route", weighting, tMode);
        RoutingAlgorithm baseAlgo;
        if (CH) {
            CHGraph chGraph = graph.getGraph(CHGraph.class);
            PrepareContractionHierarchies pch = new PrepareContractionHierarchies(graph, chGraph, tMode);
            pch.doWork();
            baseAlgo = pch.createAlgo(graph.getGraph(CHGraph.class), opts);
        } else {
            baseAlgo = new RoutingAlgorithmFactorySimple().createAlgo(graph, opts);
        }
        return par.getDecoratedAlgorithm(baseAlgo);
    }

    public RoutingAlgorithm createAlgo(GraphHopperStorage graph, boolean CH) {
        AlgorithmOptions opts = new AlgorithmOptions("alternative_route", weighting, tMode);
        if (CH) {
            CHGraph chGraph = graph.getGraph(CHGraph.class);
            PrepareContractionHierarchies pch = new PrepareContractionHierarchies(graph, chGraph, tMode);
            pch.doWork();
            return pch.createAlgo(graph.getGraph(CHGraph.class), opts);
        }
        return new RoutingAlgorithmFactorySimple().createAlgo(graph, opts);
    }

    @Test
    public void GraphPartitionTest5() {
        int areas = 5;
        GraphPartition partition = new GraphPartition(createTestGraph(true, manager), areas);
        partition.doWork();

        //check if each area contains the expected nodes
        assertTrue(partition.getNodes(0).contains(0));
        assertTrue(partition.getNodes(0).contains(8));

        assertTrue(partition.getNodes(1).contains(1));
        assertTrue(partition.getNodes(1).contains(2));

        assertTrue(partition.getNodes(2).contains(3));
        assertTrue(partition.getNodes(2).contains(9));

        assertTrue(partition.getNodes(3).contains(4));
        assertTrue(partition.getNodes(3).contains(5));

        assertTrue(partition.getNodes(4).contains(6));
        assertTrue(partition.getNodes(4).contains(7));

        //check if getArea(node) and getNodes(area) return the same result
        for (int i = 0; i < areas; i++)
            for (int j = 0; j < partition.getNodes(i).size(); j++)
                assertEquals(i, partition.getArea(partition.getNodes(i).get(j)));

        //areas 0 <-> 2, 0 <-> 4, 1 <-> 4 and 2 <-> 3 should not be directly connected
        assertFalse(partition.isDirectlyConnected(0, 2) || partition.isDirectlyConnected(2, 0));
        assertFalse(partition.isDirectlyConnected(0, 4) || partition.isDirectlyConnected(4, 0));
        assertFalse(partition.isDirectlyConnected(1, 4) || partition.isDirectlyConnected(4, 1));
        assertFalse(partition.isDirectlyConnected(2, 3) || partition.isDirectlyConnected(3, 2));
    }

    @Test
    public void GraphPartitionTest10() {
        int areas = 10;
        GraphPartition partition = new GraphPartition(createTestGraph(true, manager), areas);
        partition.doWork();

        //for areas = 10 each node should have its own area
        assertTrue(partition.getNodes(0).contains(0));
        assertTrue(partition.getNodes(1).contains(1));
        assertTrue(partition.getNodes(2).contains(2));
        assertTrue(partition.getNodes(3).contains(3));
        assertTrue(partition.getNodes(4).contains(4));
        assertTrue(partition.getNodes(5).contains(5));
        assertTrue(partition.getNodes(6).contains(6));
        assertTrue(partition.getNodes(7).contains(7));
        assertTrue(partition.getNodes(8).contains(8));
        assertTrue(partition.getNodes(9).contains(9));

        //check if getArea(node) and getNodes(area) return the same result
        for (int i = 0; i < areas; i++)
            for (int j = 0; j < partition.getNodes(i).size(); j++)
                assertEquals(i, partition.getArea(partition.getNodes(i).get(j)));
    }

    /**
     * check that AlternativeRoute.calcPath really is the shortest path
     */
    @Test
    public void compareShortestPath() {
        for (int from = 0; from < 10; from++) {
            for (int to = from + 1; to < 10; to++) {
                AlternativeRoute altRoute = (AlternativeRoute) createAlgo(createTestGraph(true, manager), false);
                Dijkstra dijkstra = new Dijkstra(createTestGraph(true, manager), weighting, tMode);
                assertEquals(altRoute.calcPath(from, to).calcNodes(), dijkstra.calcPath(from, to).calcNodes());
            }
        }
    }

    /**
     * check that each alternative is different to the shortest path and to each other
     */
    @Test
    public void compareAlternatives() {
        for (int from = 0; from < 10; from++) {
            for (int to = from + 1; to < 10; to++) {
                AlternativeRoute altRoute = (AlternativeRoute) createAlgo(createTestGraph(true, manager), false);
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
     * check that the shortest path from node 0 to 2 contains node 8 and the best alternative node 4
     */
    @Test
    public void testNoPrepare_0_2() {
        AlternativeRoute altRoute = (AlternativeRoute) createAlgo(createTestGraph(true, manager), false);
        List<Path> paths = altRoute.calcPaths(0, 2);
        assertEquals(2, paths.size());
        assertTrue(paths.get(0).calcNodes().contains(8));
        assertTrue(paths.get(1).calcNodes().contains(4));

        assertFalse(altRoute.isAdvancedAlgo());
    }

    /**
     * check that the shortest path when using CH from node 0 to 2 contains node 8 and the best alternative node 4
     */
    @Test
    public void testNoPrepareCH_0_2() {
        if (tMode.isEdgeBased())
            return;

        AlternativeRouteCH altRoute = (AlternativeRouteCH) createAlgo(createTestGraph(true, manager), true);
        List<Path> paths = altRoute.calcPaths(0, 2);
        assertEquals(2, paths.size());
        assertTrue(paths.get(0).calcNodes().contains(8));
        assertTrue(paths.get(1).calcNodes().contains(4));

        assertFalse(altRoute.isAdvancedAlgo());
    }

    /**
     * check that the prepared algorithm has the same output as the unprepared one
     */
    @Test
    public void testPrepareBoth_0_2() {
        if (tMode.isEdgeBased())
            return;

        GraphHopperStorage graph = createTestGraph(true, manager);
        PrepareAlternativeRoute prepare = prepareAR(graph, 10, true);

        AlternativeRoute altRoute = (AlternativeRoute) createAlgo(graph, false, prepare);
        List<Path> paths = altRoute.calcPaths(0, 2);

        AlternativeRouteCH altRouteCH = (AlternativeRouteCH) createAlgo(graph, true, prepare);
        List<Path> pathsCH = altRouteCH.calcPaths(0, 2);

        AlternativeRouteCH altRouteNoPrepare = (AlternativeRouteCH) createAlgo(graph, true);
        List<Path> pathsNoPrepare = altRouteNoPrepare.calcPaths(0, 2);

        assertEquals(2, paths.size());
        assertEquals(2, pathsCH.size());
        assertEquals(2, pathsNoPrepare.size());

        for (int i = 0; i < paths.size(); i++) {
            assertEquals(pathsNoPrepare.get(i).calcNodes(), paths.get(i).calcNodes());
            assertEquals(pathsNoPrepare.get(i).calcNodes(), pathsCH.get(i).calcNodes());
        }

        assertTrue(altRoute.isAdvancedAlgo());
        assertTrue(altRouteCH.isAdvancedAlgo());
        assertFalse(altRouteNoPrepare.isAdvancedAlgo());
    }

    /**
     * check that the prepared algorithm has the same output as the unprepared one. In this case the areas of both
     * nodes are directly connected to each other. This means that the prepared algorithm will be using the unprepared
     * one (AlternativeRoute.isAdvAlgo() == false)
     */
    @Test
    public void testPrepareCH_directlyConnected_0_2() {
        if (tMode.isEdgeBased())
            return;

        GraphHopperStorage graph = createTestGraph(true, manager);
        PrepareAlternativeRoute prepare = prepareAR(graph, 5, true);

        AlternativeRouteCH altRouteCH = (AlternativeRouteCH) createAlgo(graph, true, prepare);
        List<Path> pathsCH = altRouteCH.calcPaths(0, 2);

        AlternativeRouteCH altRouteNoPrepare = (AlternativeRouteCH) createAlgo(graph, true);
        List<Path> pathsNoPrepare = altRouteNoPrepare.calcPaths(0, 2);

        assertEquals(2, pathsCH.size());
        assertEquals(2, pathsNoPrepare.size());

        for (int i = 0; i < pathsCH.size(); i++) {
            assertEquals(pathsNoPrepare.get(i).calcNodes(), pathsCH.get(i).calcNodes());
        }

        assertFalse(altRouteCH.isAdvancedAlgo());
        assertFalse(altRouteNoPrepare.isAdvancedAlgo());
    }

    /**
     * check that only a small part of the graph is traversed if the nodes lie in disconnected areas
     */
    @Test
    public void testDisconnectedAreas() {
        GraphHopperStorage graph = createTestGraph(true, manager);

        // one single disconnected node
        updateDistancesFor(graph, 10, 0.00, -0.01);

        AlternativeRoute altRoute = (AlternativeRoute) createAlgo(graph, false);
        Path path = altRoute.calcPath(0, 10);
        assertFalse(path.isFound());

        // make sure not the full graph is traversed!
        assertEquals(3, altRoute.getVisitedNodes());
    }
}
