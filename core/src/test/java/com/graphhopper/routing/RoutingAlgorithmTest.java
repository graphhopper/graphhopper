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

import com.carrotsearch.hppc.IntArrayList;
import com.carrotsearch.hppc.IntIndexedContainer;
import com.graphhopper.routing.ch.PrepareContractionHierarchies;
import com.graphhopper.routing.profiles.BooleanEncodedValue;
import com.graphhopper.routing.profiles.DecimalEncodedValue;
import com.graphhopper.routing.querygraph.QueryGraph;
import com.graphhopper.routing.util.*;
import com.graphhopper.routing.weighting.FastestWeighting;
import com.graphhopper.routing.weighting.ShortestWeighting;
import com.graphhopper.routing.weighting.TurnWeighting;
import com.graphhopper.routing.weighting.Weighting;
import com.graphhopper.storage.*;
import com.graphhopper.storage.index.LocationIndex;
import com.graphhopper.storage.index.LocationIndexTree;
import com.graphhopper.storage.index.QueryResult;
import com.graphhopper.util.DistanceCalcEarth;
import com.graphhopper.util.EdgeIteratorState;
import com.graphhopper.util.GHUtility;
import com.graphhopper.util.Helper;
import com.graphhopper.util.shapes.BBox;
import com.graphhopper.util.shapes.GHPoint;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.Collection;
import java.util.Random;

import static com.graphhopper.routing.util.TraversalMode.EDGE_BASED;
import static com.graphhopper.routing.util.TraversalMode.NODE_BASED;
import static com.graphhopper.util.GHUtility.updateDistancesFor;
import static com.graphhopper.util.Helper.DIST_EARTH;
import static com.graphhopper.util.Parameters.Algorithms.ASTAR_BI;
import static com.graphhopper.util.Parameters.Algorithms.DIJKSTRA_BI;
import static org.junit.Assert.*;

/**
 * This test tests the different routing algorithms on small user-defined sample graphs. It tests node- and edge-based
 * algorithms, but does *not* use turn costs, because otherwise node- and edge-based implementations would not be
 * comparable. For edge-based traversal u-turns cannot happen even when we are not using a {@link TurnWeighting}, because
 * as long as we do not apply turn restrictions we will never take a u-turn.. All tests should follow the same pattern:
 * <p>
 * - create a GH storage, you need to pass all the weightings used in this test to {@link #createGHStorage}, such that
 * the according CHGraphs can be created
 * - build the graph: add edges, nodes, set different edge properties etc.
 * - calculate a path using one of the {@link #calcPath} methods and compare it with the expectations
 * <p>
 * The tests are run for all algorithms added in {@link #configs()}}.
 *
 * @author Peter Karich
 * @author easbar
 * @see EdgeBasedRoutingAlgorithmTest for similar tests including turn costs
 */
@RunWith(Parameterized.class)
public class RoutingAlgorithmTest {
    private static final Logger LOGGER = LoggerFactory.getLogger(RoutingAlgorithmTest.class);
    private final EncodingManager encodingManager;
    private final FlagEncoder carEncoder;
    private final FlagEncoder footEncoder;
    private final FlagEncoder bike2Encoder;
    private final PathCalculator pathCalculator;
    private final TraversalMode traversalMode;
    private final Weighting defaultWeighting;
    private final int defaultMaxVisitedNodes;

    public RoutingAlgorithmTest(PathCalculator pathCalculator, TraversalMode traversalMode) {
        this.pathCalculator = pathCalculator;
        this.traversalMode = traversalMode;
        // vehicles used in this test
        encodingManager = EncodingManager.create("car,foot,bike2");
        carEncoder = encodingManager.getEncoder("car");
        footEncoder = encodingManager.getEncoder("foot");
        bike2Encoder = encodingManager.getEncoder("bike2");
        // most tests use the default weighting, but this can be chosen for each test separately
        defaultWeighting = new ShortestWeighting(carEncoder);
        // most tests do not limit the number of visited nodes, but this can be chosen for each test separately
        defaultMaxVisitedNodes = Integer.MAX_VALUE;
    }

    @Parameterized.Parameters(name = "{0}, {1}")
    public static Collection<Object[]> configs() {
        return Arrays.asList(new Object[][]{
                {new DijkstraCalculator(), NODE_BASED},
                {new DijkstraCalculator(), EDGE_BASED},
                {new BidirDijkstraCalculator(), NODE_BASED},
                {new BidirDijkstraCalculator(), EDGE_BASED},
                {new AStarCalculator(), NODE_BASED},
                {new AStarCalculator(), EDGE_BASED},
                {new BidirAStarCalculator(), NODE_BASED},
                {new BidirAStarCalculator(), EDGE_BASED},
                // so far only supports node-based
                {new DijkstraOneToManyCalculator(), NODE_BASED},
                {new CHAStarCalculator(), NODE_BASED},
                {new CHAStarCalculator(), EDGE_BASED},
                {new CHDijkstraCalculator(), NODE_BASED},
                {new CHDijkstraCalculator(), EDGE_BASED}
        });
    }

    @Test
    public void testCalcShortestPath() {
        GraphHopperStorage ghStorage = createGHStorage();
        initTestStorage(ghStorage);
        Path p = calcPath(ghStorage, 0, 7);
        assertEquals(p.toString(), nodes(0, 4, 5, 7), p.calcNodes());
        assertEquals(p.toString(), 62.1, p.getDistance(), .1);
    }

    @Test
    public void testCalcShortestPath_sourceEqualsTarget() {
        // 0-1-2
        GraphHopperStorage graph = createGHStorage();
        graph.edge(0, 1, 1, true);
        graph.edge(1, 2, 2, true);

        Path p = calcPath(graph, 0, 0);
        assertPathFromEqualsTo(p, 0);
    }

    @Test
    public void testSimpleAlternative() {
        // 0--2--1
        //    |  |
        //    3--4
        GraphHopperStorage graph = createGHStorage();
        graph.edge(0, 2, 9, true);
        graph.edge(2, 1, 2, true);
        graph.edge(2, 3, 11, true);
        graph.edge(3, 4, 6, true);
        graph.edge(4, 1, 9, true);
        Path p = calcPath(graph, 0, 4);
        assertEquals(p.toString(), 20, p.getDistance(), 1e-4);
        assertEquals(nodes(0, 2, 1, 4), p.calcNodes());
    }

    @Test
    public void testBidirectionalLinear() {
        //3--2--1--4--5
        GraphHopperStorage graph = createGHStorage();
        graph.edge(2, 1, 2, true);
        graph.edge(2, 3, 11, true);
        graph.edge(5, 4, 6, true);
        graph.edge(4, 1, 9, true);
        Path p = calcPath(graph, 3, 5);
        assertEquals(p.toString(), 28, p.getDistance(), 1e-4);
        assertEquals(nodes(3, 2, 1, 4, 5), p.calcNodes());
    }

    // see calc-fastest-graph.svg
    @Test
    public void testCalcFastestPath() {
        FastestWeighting fastestWeighting = new FastestWeighting(carEncoder);
        GraphHopperStorage graph = createGHStorage(false, defaultWeighting, fastestWeighting);
        initDirectedAndDiffSpeed(graph, carEncoder);

        Path p1 = calcPath(graph, defaultWeighting, 0, 3);
        assertEquals(nodes(0, 1, 5, 2, 3), p1.calcNodes());
        assertEquals(p1.toString(), 402.3, p1.getDistance(), .1);
        assertEquals(p1.toString(), 144823, p1.getTime());

        Path p2 = calcPath(graph, fastestWeighting, 0, 3);
        assertEquals(nodes(0, 4, 6, 7, 5, 3), p2.calcNodes());
        assertEquals(p2.toString(), 1261.7, p2.getDistance(), 0.1);
        assertEquals(p2.toString(), 111442, p2.getTime());
    }

    // 0-1-2-3
    // |/|/ /|
    // 4-5-- |
    // |/ \--7
    // 6----/
    static void initDirectedAndDiffSpeed(Graph graph, FlagEncoder enc) {
        GHUtility.setProperties(graph.edge(0, 1), enc, 10, true, false);
        GHUtility.setProperties(graph.edge(0, 4), enc, 100, true, false);

        GHUtility.setProperties(graph.edge(1, 4), enc, 10, true, true);
        GHUtility.setProperties(graph.edge(1, 5), enc, 10, true, true);
        EdgeIteratorState edge12 = GHUtility.setProperties(graph.edge(1, 2), enc, 10, true, true);

        GHUtility.setProperties(graph.edge(5, 2), enc, 10, true, false);
        GHUtility.setProperties(graph.edge(2, 3), enc, 10, true, false);

        EdgeIteratorState edge53 = GHUtility.setProperties(graph.edge(5, 3), enc, 20, true, false);
        GHUtility.setProperties(graph.edge(3, 7), enc, 10, true, false);

        GHUtility.setProperties(graph.edge(4, 6), enc, 100, true, false);
        GHUtility.setProperties(graph.edge(5, 4), enc, 10, true, false);

        GHUtility.setProperties(graph.edge(5, 6), enc, 10, true, false);
        GHUtility.setProperties(graph.edge(7, 5), enc, 100, true, false);

        GHUtility.setProperties(graph.edge(6, 7), enc, 100, true, true);

        updateDistancesFor(graph, 0, 0.002, 0);
        updateDistancesFor(graph, 1, 0.002, 0.001);
        updateDistancesFor(graph, 2, 0.002, 0.002);
        updateDistancesFor(graph, 3, 0.002, 0.003);
        updateDistancesFor(graph, 4, 0.0015, 0);
        updateDistancesFor(graph, 5, 0.0015, 0.001);
        updateDistancesFor(graph, 6, 0, 0);
        updateDistancesFor(graph, 7, 0.001, 0.003);

        edge12.setDistance(edge12.getDistance() * 2);
        edge53.setDistance(edge53.getDistance() * 2);
    }

    @Test
    public void testCalcFootPath() {
        ShortestWeighting shortestWeighting = new ShortestWeighting(footEncoder);
        GraphHopperStorage graph = createGHStorage(false, shortestWeighting);
        initFootVsCar(carEncoder, footEncoder, graph);
        Path p1 = calcPath(graph, shortestWeighting, 0, 7);
        assertEquals(p1.toString(), 17000, p1.getDistance(), 1e-6);
        assertEquals(p1.toString(), 12240 * 1000, p1.getTime());
        assertEquals(nodes(0, 4, 5, 7), p1.calcNodes());
    }

    static void initFootVsCar(FlagEncoder carEncoder, FlagEncoder footEncoder, Graph graph) {
        EdgeIteratorState edge = graph.edge(0, 1).setDistance(7000);
        GHUtility.setProperties(edge, footEncoder, 5, true, true);
        GHUtility.setProperties(edge, carEncoder, 10, true, false);
        edge = graph.edge(0, 4).setDistance(5000);
        GHUtility.setProperties(edge, footEncoder, 5, true, true);
        GHUtility.setProperties(edge, carEncoder, 20, true, false);

        GHUtility.setProperties(graph.edge(1, 4).setDistance(7000), carEncoder, 10, true, true);
        GHUtility.setProperties(graph.edge(1, 5).setDistance(7000), carEncoder, 10, true, true);
        edge = graph.edge(1, 2).setDistance(20000);
        GHUtility.setProperties(edge, footEncoder, 5, true, true);
        GHUtility.setProperties(edge, carEncoder, 10, true, true);

        GHUtility.setProperties(graph.edge(5, 2).setDistance(5000), carEncoder, 10, true, false);
        edge = graph.edge(2, 3).setDistance(5000);
        GHUtility.setProperties(edge, footEncoder, 5, true, true);
        GHUtility.setProperties(edge, carEncoder, 10, true, false);

        GHUtility.setProperties(graph.edge(5, 3).setDistance(11000), carEncoder, 20, true, false);
        edge = graph.edge(3, 7).setDistance(7000);
        GHUtility.setProperties(edge, footEncoder, 5, true, true);
        GHUtility.setProperties(edge, carEncoder, 10, true, false);

        GHUtility.setProperties(graph.edge(4, 6).setDistance(5000), carEncoder, 20, true, false);
        edge = graph.edge(5, 4).setDistance(7000);
        GHUtility.setProperties(edge, footEncoder, 5, true, true);
        GHUtility.setProperties(edge, carEncoder, 10, true, false);

        GHUtility.setProperties(graph.edge(5, 6).setDistance(7000), carEncoder, 10, true, false);
        edge = graph.edge(7, 5).setDistance(5000);
        GHUtility.setProperties(edge, footEncoder, 5, true, true);
        GHUtility.setProperties(edge, carEncoder, 20, true, false);

        GHUtility.setProperties(graph.edge(6, 7).setDistance(5000), carEncoder, 20, true, true);
    }

    // see test-graph.svg !
    static void initTestStorage(Graph graph) {
        graph.edge(0, 1, 7, true);
        graph.edge(0, 4, 6, true);

        graph.edge(1, 4, 2, true);
        graph.edge(1, 5, 8, true);
        graph.edge(1, 2, 2, true);

        graph.edge(2, 5, 5, true);
        graph.edge(2, 3, 2, true);

        graph.edge(3, 5, 2, true);
        graph.edge(3, 7, 10, true);

        graph.edge(4, 6, 4, true);
        graph.edge(4, 5, 7, true);

        graph.edge(5, 6, 2, true);
        graph.edge(5, 7, 1, true);

        EdgeIteratorState edge6_7 = graph.edge(6, 7, 5, true);

        updateDistancesFor(graph, 0, 0.0010, 0.00001);
        updateDistancesFor(graph, 1, 0.0008, 0.0000);
        updateDistancesFor(graph, 2, 0.0005, 0.0001);
        updateDistancesFor(graph, 3, 0.0006, 0.0002);
        updateDistancesFor(graph, 4, 0.0009, 0.0001);
        updateDistancesFor(graph, 5, 0.0007, 0.0001);
        updateDistancesFor(graph, 6, 0.0009, 0.0002);
        updateDistancesFor(graph, 7, 0.0008, 0.0003);

        edge6_7.setDistance(5 * edge6_7.getDistance());
    }

    @Test
    public void testNoPathFound() {
        GraphHopperStorage graph = createGHStorage();
        graph.edge(100, 101);
        assertFalse(calcPath(graph, 0, 1).isFound());

        graph = createGHStorage();
        graph.edge(100, 101);

        // two disconnected areas
        // 0-1
        //
        // 7-5-6
        //  \|
        //   8
        graph.edge(0, 1, 7, true);
        graph.edge(5, 6, 2, true);
        graph.edge(5, 7, 1, true);
        graph.edge(5, 8, 1, true);
        graph.edge(7, 8, 1, true);
        assertFalse(calcPath(graph, 0, 5).isFound());

        // disconnected as directed graph
        // 2-0->1
        graph = createGHStorage();
        graph.edge(0, 1, 1, false);
        graph.edge(0, 2, 1, true);
        assertFalse(calcPath(graph, 1, 2).isFound());
        assertTrue(calcPath(graph, 2, 1).isFound());
    }

    @Test
    public void testWikipediaShortestPath() {
        GraphHopperStorage ghStorage = createWikipediaTestGraph();
        Path p = calcPath(ghStorage, 0, 4);
        assertEquals(p.toString(), nodes(0, 2, 5, 4), p.calcNodes());
        assertEquals(p.toString(), 20, p.getDistance(), 1e-4);
    }

    @Test
    public void testCalcIf1EdgeAway() {
        GraphHopperStorage graph = createGHStorage();
        initTestStorage(graph);
        Path p = calcPath(graph, 1, 2);
        assertEquals(nodes(1, 2), p.calcNodes());
        assertEquals(p.toString(), 35.1, p.getDistance(), .1);
    }

    // see wikipedia-graph.svg !
    private GraphHopperStorage createWikipediaTestGraph() {
        GraphHopperStorage graph = createGHStorage();
        graph.edge(0, 1, 7, true);
        graph.edge(0, 2, 9, true);
        graph.edge(0, 5, 14, true);
        graph.edge(1, 2, 10, true);
        graph.edge(1, 3, 15, true);
        graph.edge(2, 5, 2, true);
        graph.edge(2, 3, 11, true);
        graph.edge(3, 4, 6, true);
        graph.edge(4, 5, 9, true);
        return graph;
    }

    @Test
    public void testBidirectional() {
        GraphHopperStorage graph = createGHStorage();
        initBiGraph(graph);

        Path p = calcPath(graph, 0, 4);
        assertEquals(p.toString(), nodes(0, 7, 6, 8, 3, 4), p.calcNodes());
        assertEquals(p.toString(), 335.8, p.getDistance(), .1);

        p = calcPath(graph, 1, 2);
        // the other way around is even larger as 0-1 is already 11008.452
        assertEquals(p.toString(), nodes(1, 2), p.calcNodes());
        assertEquals(p.toString(), 10007.7, p.getDistance(), .1);
    }

    // 0-1-2-3-4
    // |     / |
    // |    8  |
    // \   /   |
    //  7-6----5
    public static void initBiGraph(Graph graph) {
        // distance will be overwritten in second step as we need to calculate it from lat,lon
        graph.edge(0, 1, 1, true);
        graph.edge(1, 2, 1, true);
        graph.edge(2, 3, 1, true);
        graph.edge(3, 4, 1, true);
        graph.edge(4, 5, 1, true);
        graph.edge(5, 6, 1, true);
        graph.edge(6, 7, 1, true);
        graph.edge(7, 0, 1, true);
        graph.edge(3, 8, 1, true);
        graph.edge(8, 6, 1, true);

        // we need lat,lon for edge precise queries because the distances of snapped point
        // to adjacent nodes is calculated from lat,lon of the necessary points
        updateDistancesFor(graph, 0, 0.001, 0);
        updateDistancesFor(graph, 1, 0.100, 0.0005);
        updateDistancesFor(graph, 2, 0.010, 0.0010);
        updateDistancesFor(graph, 3, 0.001, 0.0011);
        updateDistancesFor(graph, 4, 0.001, 0.00111);

        updateDistancesFor(graph, 8, 0.0005, 0.0011);

        updateDistancesFor(graph, 7, 0, 0);
        updateDistancesFor(graph, 6, 0, 0.001);
        updateDistancesFor(graph, 5, 0, 0.004);
    }

    @Test
    public void testCreateAlgoTwice() {
        // 0-1-2-3-4
        // |     / |
        // |    8  |
        // \   /   /
        //  7-6-5-/
        GraphHopperStorage graph = createGHStorage();
        graph.edge(0, 1, 1, true);
        graph.edge(1, 2, 1, true);
        graph.edge(2, 3, 1, true);
        graph.edge(3, 4, 1, true);
        graph.edge(4, 5, 1, true);
        graph.edge(5, 6, 1, true);
        graph.edge(6, 7, 1, true);
        graph.edge(7, 0, 1, true);
        graph.edge(3, 8, 1, true);
        graph.edge(8, 6, 1, true);

        // run the same query twice, this can be interesting because in the second call algorithms that pre-process
        // the graph might depend on the state of the graph after the first call 
        Path p1 = calcPath(graph, 0, 4);
        Path p2 = calcPath(graph, 0, 4);

        assertEquals(p1.calcNodes(), p2.calcNodes());
    }

    @Test
    public void testMaxVisitedNodes() {
        GraphHopperStorage graph = createGHStorage();
        initBiGraph(graph);

        Path p = calcPath(graph, 0, 4);
        assertTrue(p.isFound());
        int maxVisitedNodes = 3;
        p = calcPath(graph, defaultWeighting, maxVisitedNodes, 0, 4);
        assertFalse(p.isFound());
    }

    @Test
    public void testBidirectional2() {
        GraphHopperStorage graph = createGHStorage();
        initBidirGraphManualDistances(graph);
        Path p = calcPath(graph, 0, 4);
        assertEquals(p.toString(), 40, p.getDistance(), 1e-4);
        assertEquals(p.toString(), 5, p.calcNodes().size());
        assertEquals(nodes(0, 7, 6, 5, 4), p.calcNodes());
    }

    private void initBidirGraphManualDistances(GraphHopperStorage graph) {
        // 0-1-2-3-4
        // |     / |
        // |    8  |
        // \   /   /
        //  7-6-5-/
        graph.edge(0, 1, 100, true);
        graph.edge(1, 2, 1, true);
        graph.edge(2, 3, 1, true);
        graph.edge(3, 4, 1, true);
        graph.edge(4, 5, 20, true);
        graph.edge(5, 6, 10, true);
        graph.edge(6, 7, 5, true);
        graph.edge(7, 0, 5, true);
        graph.edge(3, 8, 20, true);
        graph.edge(8, 6, 20, true);
    }

    @Test
    public void testRekeyBugOfIntBinHeap() {
        // using Dijkstra + IntBinHeap then rekey loops endlessly
        GraphHopperStorage matrixGraph = createGHStorage();
        initMatrixALikeGraph(matrixGraph);
        Path p = calcPath(matrixGraph, 36, 91);
        assertEquals(12, p.calcNodes().size());

        IntIndexedContainer nodes = p.calcNodes();
        if (!nodes(36, 46, 56, 66, 76, 86, 85, 84, 94, 93, 92, 91).equals(nodes)
                && !nodes(36, 46, 56, 66, 76, 86, 85, 84, 83, 82, 92, 91).equals(nodes)) {
            fail("wrong locations: " + nodes.toString());
        }
        assertEquals(66f, p.getDistance(), 1e-3);

        testBug1(matrixGraph);
        testCorrectWeight(matrixGraph);
    }

    private static void initMatrixALikeGraph(GraphHopperStorage tmpGraph) {
        int WIDTH = 10;
        int HEIGHT = 15;
        int[][] matrix = new int[WIDTH][HEIGHT];
        int counter = 0;
        Random rand = new Random(12);
        final boolean print = false;
        for (int h = 0; h < HEIGHT; h++) {
            if (print) {
                for (int w = 0; w < WIDTH; w++) {
                    System.out.print(" |\t           ");
                }
                System.out.println();
            }

            for (int w = 0; w < WIDTH; w++) {
                matrix[w][h] = counter++;
                if (h > 0) {
                    float dist = 5 + Math.abs(rand.nextInt(5));
                    if (print)
                        System.out.print(" " + (int) dist + "\t           ");

                    tmpGraph.edge(matrix[w][h], matrix[w][h - 1], dist, true);
                }
            }
            if (print) {
                System.out.println();
                if (h > 0) {
                    for (int w = 0; w < WIDTH; w++) {
                        System.out.print(" |\t           ");
                    }
                    System.out.println();
                }
            }

            for (int w = 0; w < WIDTH; w++) {
                if (w > 0) {
                    float dist = 5 + Math.abs(rand.nextInt(5));
                    if (print)
                        System.out.print("-- " + (int) dist + "\t-- ");
                    tmpGraph.edge(matrix[w][h], matrix[w - 1][h], dist, true);
                }
                if (print)
                    System.out.print("(" + matrix[w][h] + ")\t");
            }
            if (print)
                System.out.println();
        }
    }

    private void testBug1(GraphHopperStorage g) {
        Path p = calcPath(g, 34, 36);
        assertEquals(nodes(34, 35, 36), p.calcNodes());
        assertEquals(3, p.calcNodes().size());
        assertEquals(17, p.getDistance(), 1e-5);
    }

    private void testCorrectWeight(GraphHopperStorage g) {
        Path p = calcPath(g, 45, 72);
        assertEquals(nodes(45, 44, 54, 64, 74, 73, 72), p.calcNodes());
        assertEquals(38f, p.getDistance(), 1e-3);
    }

    @Test
    public void testCannotCalculateSP() {
        // 0->1->2
        GraphHopperStorage graph = createGHStorage();
        graph.edge(0, 1, 1, false);
        graph.edge(1, 2, 1, false);
        Path p = calcPath(graph, 0, 2);
        assertEquals(p.toString(), 3, p.calcNodes().size());
    }

    @Test
    public void testDirectedGraphBug1() {
        GraphHopperStorage graph = createGHStorage();
        graph.edge(0, 1, 3, false);
        graph.edge(1, 2, 2.99, false);

        graph.edge(0, 3, 2, false);
        graph.edge(3, 4, 3, false);
        graph.edge(4, 2, 1, false);

        Path p = calcPath(graph, 0, 2);
        assertEquals(p.toString(), nodes(0, 1, 2), p.calcNodes());
        assertEquals(p.toString(), 5.99, p.getDistance(), 1e-4);
    }

    @Test
    public void testDirectedGraphBug2() {
        // 0->1->2
        //    | /
        //    3<
        GraphHopperStorage graph = createGHStorage();
        graph.edge(0, 1, 1, false);
        graph.edge(1, 2, 1, false);
        graph.edge(2, 3, 1, false);
        graph.edge(3, 1, 4, true);

        Path p = calcPath(graph, 0, 3);
        assertEquals(nodes(0, 1, 2, 3), p.calcNodes());
    }

    // a-b-0-c-1
    // |   |  _/\
    // |  /  /  |
    // d-2--3-e-4
    @Test
    public void testWithCoordinates() {
        Weighting weighting = new ShortestWeighting(carEncoder);
        GraphHopperStorage graph = createGHStorage(false, weighting);
        graph.edge(0, 1, 2, true).setWayGeometry(Helper.createPointList(1.5, 1));
        graph.edge(2, 3, 2, true).setWayGeometry(Helper.createPointList(0, 1.5));
        graph.edge(3, 4, 2, true).setWayGeometry(Helper.createPointList(0, 2));

        // duplicate but the second edge is longer
        graph.edge(0, 2, 1.2, true);
        graph.edge(0, 2, 1.5, true).setWayGeometry(Helper.createPointList(0.5, 0));

        graph.edge(1, 3, 1.3, true).setWayGeometry(Helper.createPointList(0.5, 1.5));
        graph.edge(1, 4, 1, true);

        updateDistancesFor(graph, 0, 1, 0.6);
        updateDistancesFor(graph, 1, 1, 1.5);
        updateDistancesFor(graph, 2, 0, 0);
        updateDistancesFor(graph, 3, 0, 1);
        updateDistancesFor(graph, 4, 0, 2);

        Path p = calcPath(graph, weighting, 4, 0);
        assertEquals(nodes(4, 1, 0), p.calcNodes());
        assertEquals(Helper.createPointList(0, 2, 1, 1.5, 1.5, 1, 1, 0.6), p.calcPoints());
        assertEquals(274128, p.calcPoints().calcDistance(new DistanceCalcEarth()), 1);

        p = calcPath(graph, weighting, 2, 1);
        assertEquals(nodes(2, 0, 1), p.calcNodes());
        assertEquals(Helper.createPointList(0, 0, 1, 0.6, 1.5, 1, 1, 1.5), p.calcPoints());
        assertEquals(279482, p.calcPoints().calcDistance(new DistanceCalcEarth()), 1);
    }

    @Test
    public void testCalcIfEmptyWay() {
        GraphHopperStorage graph = createGHStorage();
        initTestStorage(graph);
        Path p = calcPath(graph, 0, 0);
        assertPathFromEqualsTo(p, 0);
    }

    @Test
    public void testViaEdges_FromEqualsTo() {
        GraphHopperStorage ghStorage = createGHStorage();
        initTestStorage(ghStorage);
        // identical tower nodes
        Path p = calcPath(ghStorage, new GHPoint(0.001, 0.000), new GHPoint(0.001, 0.000));
        assertPathFromEqualsTo(p, 0);

        // identical query points on edge
        p = calcPath(ghStorage, defaultWeighting, 0, 1, 0, 1);
        assertPathFromEqualsTo(p, 8);

        // very close
        p = calcPath(ghStorage, new GHPoint(0.00092, 0), new GHPoint(0.00091, 0));
        assertEquals(nodes(8, 9), p.calcNodes());
        assertEquals(p.toString(), 1.11, p.getDistance(), .1);
    }

    @Test
    public void testViaEdges_BiGraph() {
        GraphHopperStorage graph = createGHStorage();
        initBiGraph(graph);

        // 0-7 to 4-3
        Path p = calcPath(graph, new GHPoint(0.0009, 0), new GHPoint(0.001, 0.001105));
        assertEquals(p.toString(), nodes(10, 7, 6, 8, 3, 9), p.calcNodes());
        assertEquals(p.toString(), 324.11, p.getDistance(), 0.01);

        // 0-1 to 2-3
        p = calcPath(graph, new GHPoint(0.001, 0.0001), new GHPoint(0.010, 0.0011));
        assertEquals(p.toString(), nodes(0, 7, 6, 8, 3, 9), p.calcNodes());
        assertEquals(p.toString(), 1335.35, p.getDistance(), 0.01);
    }

    @Test
    public void testViaEdges_WithCoordinates() {
        GraphHopperStorage ghStorage = createGHStorage();
        initTestStorage(ghStorage);
        Path p = calcPath(ghStorage, defaultWeighting, 0, 1, 2, 3);
        assertEquals(nodes(8, 1, 2, 9), p.calcNodes());
        assertEquals(p.toString(), 56.7, p.getDistance(), .1);
    }

    @Test
    public void testViaEdges_SpecialCases() {
        GraphHopperStorage graph = createGHStorage();
        // 0->1\
        // |    2
        // 4<-3/
        graph.edge(0, 1, 7, false);
        graph.edge(1, 2, 7, true);
        graph.edge(2, 3, 7, true);
        graph.edge(3, 4, 7, false);
        graph.edge(4, 0, 7, true);

        updateDistancesFor(graph, 4, 0, 0);
        updateDistancesFor(graph, 0, 0.00010, 0);
        updateDistancesFor(graph, 1, 0.00010, 0.0001);
        updateDistancesFor(graph, 2, 0.00005, 0.00015);
        updateDistancesFor(graph, 3, 0, 0.0001);

        // 0-1 to 3-4
        Path p = calcPath(graph, new GHPoint(0.00010, 0.00001), new GHPoint(0, 0.00009));
        assertEquals(nodes(5, 1, 2, 3, 6), p.calcNodes());
        assertEquals(p.toString(), 26.81, p.getDistance(), .1);

        // overlapping edges: 2-3 and 3-2
        p = calcPath(graph, new GHPoint(0.000049, 0.00014), new GHPoint(0.00001, 0.0001));
        assertEquals(nodes(5, 6), p.calcNodes());
        assertEquals(p.toString(), 6.2, p.getDistance(), .1);

        // 'from' and 'to' edge share one node '2': 1-2 to 3-2
        p = calcPath(graph, new GHPoint(0.00009, 0.00011), new GHPoint(0.00001, 0.00011));
        assertEquals(p.toString(), nodes(6, 2, 5), p.calcNodes());
        assertEquals(p.toString(), 12.57, p.getDistance(), .1);
    }

    @Test
    public void testQueryGraphAndFastest() {
        Weighting weighting = new FastestWeighting(carEncoder);
        GraphHopperStorage graph = createGHStorage(false, weighting);
        initDirectedAndDiffSpeed(graph, carEncoder);
        Path p = calcPath(graph, weighting, new GHPoint(0.002, 0.0005), new GHPoint(0.0017, 0.0031));
        assertEquals(nodes(8, 1, 5, 3, 9), p.calcNodes());
        assertEquals(602.98, p.getDistance(), 1e-1);
    }

    /**
     * Creates query result on edge (node1-node2) very close to node1.
     */
    private QueryResult createQRBetweenNodes(Graph graph, int node1, int node2) {
        EdgeIteratorState edge = GHUtility.getEdge(graph, node1, node2);
        if (edge == null)
            throw new IllegalStateException("edge not found? " + node1 + "-" + node2);

        NodeAccess na = graph.getNodeAccess();
        double lat = na.getLatitude(edge.getBaseNode());
        double lon = na.getLongitude(edge.getBaseNode());
        double latAdj = na.getLatitude(edge.getAdjNode());
        double lonAdj = na.getLongitude(edge.getAdjNode());
        // calculate query point near the base node but not directly on it!
        QueryResult res = new QueryResult(lat + (latAdj - lat) * .1, lon + (lonAdj - lon) * .1);
        res.setClosestNode(edge.getBaseNode());
        res.setClosestEdge(edge);
        res.setWayIndex(0);
        res.setSnappedPosition(QueryResult.Position.EDGE);
        res.calcSnappedPoint(DIST_EARTH);
        return res;
    }

    @Test
    public void testTwoWeightsPerEdge() {
        FastestWeighting fastestWeighting = new FastestWeighting(bike2Encoder);
        GraphHopperStorage graph = createGHStorage(true, fastestWeighting);
        initEleGraph(graph);
        // force the other path
        GHUtility.setProperties(GHUtility.getEdge(graph, 0, 3), bike2Encoder, 10, false, true);

        // for two weights per edge it happened that Path (and also the Weighting) read the wrong side
        // of the speed and read 0 => infinity weight => overflow of millis => negative millis!
        Path p = calcPath(graph, fastestWeighting, 0, 10);
        assertEquals(85124371, p.getTime());
        assertEquals(425622, p.getDistance(), 1);
        assertEquals(85124.4, p.getWeight(), 1);
    }

    @Test
    public void test0SpeedButUnblocked_Issue242() {
        GraphHopperStorage graph = createGHStorage();
        EdgeIteratorState edge01 = graph.edge(0, 1).setDistance(10);
        EdgeIteratorState edge12 = graph.edge(1, 2).setDistance(10);
        BooleanEncodedValue carAccessEnc = carEncoder.getAccessEnc();
        DecimalEncodedValue carAvSpeedEnc = carEncoder.getAverageSpeedEnc();
        edge01.set(carAvSpeedEnc, 0.0).set(carAccessEnc, true).setReverse(carAccessEnc, true);
        edge01.setFlags(edge01.getFlags());

        edge12.set(carAvSpeedEnc, 0.0).set(carAccessEnc, true).setReverse(carAccessEnc, true);
        edge12.setFlags(edge12.getFlags());

        try {
            calcPath(graph, 0, 2);
            fail("there should have been an exception");
        } catch (Exception ex) {
            assertTrue(ex.getMessage(), ex.getMessage().startsWith("Speed cannot be 0"));
        }
    }

    @Test
    public void testTwoWeightsPerEdge2() {
        // other direction should be different!
        Weighting fakeWeighting = new Weighting() {
            private final Weighting tmpW = new FastestWeighting(carEncoder);

            @Override
            public FlagEncoder getFlagEncoder() {
                return carEncoder;
            }

            @Override
            public double getMinWeight(double distance) {
                return 0.8 * distance;
            }

            @Override
            public double calcWeight(EdgeIteratorState edgeState, boolean reverse, int prevOrNextEdgeId) {
                int adj = edgeState.getAdjNode();
                int base = edgeState.getBaseNode();
                if (reverse) {
                    int tmp = base;
                    base = adj;
                    adj = tmp;
                }

                // a 'hill' at node 6
                if (adj == 6)
                    return 3 * edgeState.getDistance();
                else if (base == 6)
                    return edgeState.getDistance() * 0.9;
                else if (adj == 4)
                    return 2 * edgeState.getDistance();

                return edgeState.getDistance() * 0.8;
            }

            @Override
            public long calcMillis(EdgeIteratorState edgeState, boolean reverse, int prevOrNextEdgeId) {
                return tmpW.calcMillis(edgeState, reverse, prevOrNextEdgeId);
            }

            @Override
            public boolean matches(HintsMap map) {
                throw new UnsupportedOperationException("Not supported");
            }

            @Override
            public String getName() {
                return "custom";
            }
        };

        GraphHopperStorage graph = createGHStorage(true, defaultWeighting);
        initEleGraph(graph);
        Path p = calcPath(graph, 0, 10);
        // GHUtility.printEdgeInfo(graph, carEncoder);
        assertEquals(nodes(0, 4, 6, 10), p.calcNodes());

        graph = createGHStorage(true, fakeWeighting);
        initEleGraph(graph);
        p = calcPath(graph, fakeWeighting, 3, 0, 10, 9);
        assertEquals(nodes(12, 0, 1, 2, 11, 7, 10, 13), p.calcNodes());
        assertEquals(37009621, p.getTime());
        assertEquals(616827, p.getDistance(), 1);
        assertEquals(493462, p.getWeight(), 1);
    }

    @Test
    public void testRandomGraph() {
        // todo: use speed both directions
        DecimalEncodedValue speedEnc = carEncoder.getAverageSpeedEnc();
        FastestWeighting fastestWeighting = new FastestWeighting(carEncoder);
        GraphHopperStorage graph = createGHStorage(false, fastestWeighting);
        final long seed = System.nanoTime();
        LOGGER.info("testRandomGraph - using seed: " + seed);
        Random rnd = new Random(seed);
        // we're not including loops otherwise duplicate nodes in path might fail the test
        GHUtility.buildRandomGraph(graph, rnd, 10, 2.0, false, true, speedEnc, 0.7, 0.7, 0.7);
        final PathCalculator refCalculator = new DijkstraCalculator();
        int numRuns = 100;
        for (int i = 0; i < numRuns; i++) {
            BBox bounds = graph.getBounds();
            double latFrom = bounds.minLat + rnd.nextDouble() * (bounds.maxLat - bounds.minLat);
            double lonFrom = bounds.minLon + rnd.nextDouble() * (bounds.maxLon - bounds.minLon);
            double latTo = bounds.minLat + rnd.nextDouble() * (bounds.maxLat - bounds.minLat);
            double lonTo = bounds.minLon + rnd.nextDouble() * (bounds.maxLon - bounds.minLon);
            compareWithRef(fastestWeighting, graph, refCalculator, new GHPoint(latFrom, lonFrom), new GHPoint(latTo, lonTo), seed);
        }
    }

    private void compareWithRef(Weighting weighting, GraphHopperStorage graph, PathCalculator refCalculator, GHPoint from, GHPoint to, long seed) {
        Path path = calcPath(graph, weighting, from, to);
        Path refPath = refCalculator.calcPath(graph, weighting, traversalMode, defaultMaxVisitedNodes, from, to);
        assertEquals("wrong weight, " + weighting + ", seed: " + seed, refPath.getWeight(), path.getWeight(), 1.e-1);
        assertEquals("wrong nodes, " + weighting + ", seed: " + seed, refPath.calcNodes(), path.calcNodes());
        assertEquals("wrong distance, " + weighting + ", seed: " + seed, refPath.getDistance(), path.getDistance(), 1.e-1);
        assertEquals("wrong time, " + weighting + ", seed: " + seed, refPath.getTime(), path.getTime(), 100);
    }

    @Test
    public void testMultipleVehicles_issue548() {
        FastestWeighting footWeighting = new FastestWeighting(footEncoder);
        FastestWeighting carWeighting = new FastestWeighting(carEncoder);

        GraphHopperStorage ghStorage = createGHStorage(false, footWeighting, carWeighting);
        initFootVsCar(carEncoder, footEncoder, ghStorage);

        // normal path would be 0-4-6-7 for car:
        Path carPath1 = calcPath(ghStorage, carWeighting, 0, 7);
        assertEquals(nodes(0, 4, 6, 7), carPath1.calcNodes());
        assertEquals(carPath1.toString(), 15000, carPath1.getDistance(), 1e-6);
        // ... and 0-4-5-7 for foot
        Path footPath1 = calcPath(ghStorage, footWeighting, 0, 7);
        assertEquals(nodes(0, 4, 5, 7), footPath1.calcNodes());
        assertEquals(footPath1.toString(), 17000, footPath1.getDistance(), 1e-6);

        // ... but now we block 4-6 for car, note that we have to recreate the storage otherwise CH graphs won't be
        // refreshed
        ghStorage = createGHStorage(false, footWeighting, carWeighting);
        initFootVsCar(carEncoder, footEncoder, ghStorage);
        GHUtility.setProperties(GHUtility.getEdge(ghStorage, 4, 6), carEncoder, 20, false, false);

        // ... car needs to take another way
        Path carPath2 = calcPath(ghStorage, carWeighting, 0, 7);
        assertEquals(nodes(0, 1, 5, 6, 7), carPath2.calcNodes());
        assertEquals(carPath2.toString(), 26000, carPath2.getDistance(), 1e-6);
        // ... for foot it stays the same
        Path footPath2 = calcPath(ghStorage, footWeighting, 0, 7);
        assertEquals(nodes(0, 4, 5, 7), footPath2.calcNodes());
        assertEquals(footPath2.toString(), 17000, footPath2.getDistance(), 1e-6);
    }

    // 0-1-2
    // |\| |
    // 3 4-11
    // | | |
    // 5-6-7
    // | |\|
    // 8-9-10
    private void initEleGraph(Graph g) {
        g.edge(0, 1, 10, true);
        g.edge(0, 4, 12, true);
        g.edge(0, 3, 5, true);
        g.edge(1, 2, 10, true);
        g.edge(1, 4, 5, true);
        g.edge(3, 5, 5, false);
        g.edge(5, 6, 10, true);
        g.edge(5, 8, 10, true);
        g.edge(6, 4, 5, true);
        g.edge(6, 7, 10, true);
        g.edge(6, 10, 12, true);
        g.edge(6, 9, 12, true);
        g.edge(2, 11, 5, false);
        g.edge(4, 11, 10, true);
        g.edge(7, 11, 5, true);
        g.edge(7, 10, 5, true);
        g.edge(8, 9, 10, false);
        g.edge(9, 8, 9, false);
        g.edge(10, 9, 10, false);
        updateDistancesFor(g, 0, 3, 0);
        updateDistancesFor(g, 3, 2.5, 0);
        updateDistancesFor(g, 5, 1, 0);
        updateDistancesFor(g, 8, 0, 0);
        updateDistancesFor(g, 1, 3, 1);
        updateDistancesFor(g, 4, 2, 1);
        updateDistancesFor(g, 6, 1, 1);
        updateDistancesFor(g, 9, 0, 1);
        updateDistancesFor(g, 2, 3, 2);
        updateDistancesFor(g, 11, 2, 2);
        updateDistancesFor(g, 7, 1, 2);
        updateDistancesFor(g, 10, 0, 2);
    }

    /**
     * Creates a GH storage supporting the default weighting for CH
     */
    private GraphHopperStorage createGHStorage() {
        return createGHStorage(false, defaultWeighting);
    }

    /**
     * Creates a GH storage supporting the given weightings for CH
     */
    private GraphHopperStorage createGHStorage(boolean is3D, Weighting... weightings) {
        CHProfile[] chProfiles = new CHProfile[weightings.length];
        for (int i = 0; i < weightings.length; i++) {
            chProfiles[i] = new CHProfile(weightings[i], traversalMode, TurnWeighting.INFINITE_U_TURN_COSTS);
        }
        return new GraphBuilder(encodingManager).set3D(is3D)
                .setCHProfiles(chProfiles)
                // this test should never include turn costs, but we have to set it to true to be able to
                // run edge-based algorithms
                .withTurnCosts(traversalMode.isEdgeBased())
                .create();
    }

    private Path calcPath(GraphHopperStorage ghStorage, int from, int to) {
        return calcPath(ghStorage, defaultWeighting, from, to);
    }

    private Path calcPath(GraphHopperStorage ghStorage, Weighting weighting, int from, int to) {
        return calcPath(ghStorage, weighting, defaultMaxVisitedNodes, from, to);
    }

    private Path calcPath(GraphHopperStorage ghStorage, Weighting weighting, int maxVisitedNodes, int from, int to) {
        return pathCalculator.calcPath(ghStorage, weighting, traversalMode, maxVisitedNodes, from, to);
    }

    private Path calcPath(GraphHopperStorage ghStorage, GHPoint from, GHPoint to) {
        return calcPath(ghStorage, defaultWeighting, from, to);
    }

    private Path calcPath(GraphHopperStorage ghStorage, Weighting weighting, GHPoint from, GHPoint to) {
        return pathCalculator.calcPath(ghStorage, weighting, traversalMode, defaultMaxVisitedNodes, from, to);
    }

    private Path calcPath(GraphHopperStorage ghStorage, Weighting weighting, int fromNode1, int fromNode2, int toNode1, int toNode2) {
        // lookup two edges: fromNode1-fromNode2 and toNode1-toNode2
        QueryResult from = createQRBetweenNodes(ghStorage, fromNode1, fromNode2);
        QueryResult to = createQRBetweenNodes(ghStorage, toNode1, toNode2);
        return pathCalculator.calcPath(ghStorage, weighting, traversalMode, defaultMaxVisitedNodes, from, to);
    }

    private void assertPathFromEqualsTo(Path p, int node) {
        assertTrue(p.isFound());
        assertEquals(p.toString(), nodes(node), p.calcNodes());
        assertEquals(p.toString(), 1, p.calcPoints().size());
        assertEquals(p.toString(), 0, p.calcEdges().size());
        assertEquals(p.toString(), 0, p.getWeight(), 1e-4);
        assertEquals(p.toString(), 0, p.getDistance(), 1e-4);
        assertEquals(p.toString(), 0, p.getTime(), 1e-4);
    }

    private static IntArrayList nodes(int... nodes) {
        return IntArrayList.from(nodes);
    }

    /**
     * Implement this interface to run the test cases with different routing algorithms
     */
    private interface PathCalculator {
        Path calcPath(GraphHopperStorage graph, Weighting weighting, TraversalMode traversalMode, int maxVisitedNodes, int from, int to);

        Path calcPath(GraphHopperStorage graph, Weighting weighting, TraversalMode traversalMode, int maxVisitedNodes, GHPoint from, GHPoint to);

        Path calcPath(GraphHopperStorage graph, Weighting weighting, TraversalMode traversalMode, int maxVisitedNodes, QueryResult from, QueryResult to);
    }

    private static abstract class SimpleCalculator implements PathCalculator {
        @Override
        public Path calcPath(GraphHopperStorage graph, Weighting weighting, TraversalMode traversalMode, int maxVisitedNodes, int from, int to) {
            RoutingAlgorithm algo = createAlgo(graph, weighting, traversalMode);
            algo.setMaxVisitedNodes(maxVisitedNodes);
            return algo.calcPath(from, to);
        }

        @Override
        public Path calcPath(GraphHopperStorage graph, Weighting weighting, TraversalMode traversalMode, int maxVisitedNodes, GHPoint from, GHPoint to) {
            LocationIndexTree index = new LocationIndexTree(graph, new RAMDirectory());
            index.prepareIndex();
            QueryResult fromQR = index.findClosest(from.getLat(), from.getLon(), EdgeFilter.ALL_EDGES);
            QueryResult toQR = index.findClosest(to.getLat(), to.getLon(), EdgeFilter.ALL_EDGES);
            return calcPath(graph, weighting, traversalMode, maxVisitedNodes, fromQR, toQR);
        }

        @Override
        public Path calcPath(GraphHopperStorage graph, Weighting weighting, TraversalMode traversalMode, int maxVisitedNodes, QueryResult from, QueryResult to) {
            QueryGraph queryGraph = QueryGraph.lookup(graph, from, to);
            RoutingAlgorithm algo = createAlgo(queryGraph, weighting, traversalMode);
            algo.setMaxVisitedNodes(maxVisitedNodes);
            return algo.calcPath(from.getClosestNode(), to.getClosestNode());
        }

        abstract RoutingAlgorithm createAlgo(Graph graph, Weighting weighting, TraversalMode traversalMode);
    }

    private static class DijkstraCalculator extends SimpleCalculator {
        @Override
        RoutingAlgorithm createAlgo(Graph graph, Weighting weighting, TraversalMode traversalMode) {
            return new Dijkstra(graph, weighting, traversalMode);
        }

        @Override
        public String toString() {
            return "DIJKSTRA";
        }
    }

    private static class BidirDijkstraCalculator extends SimpleCalculator {
        @Override
        RoutingAlgorithm createAlgo(Graph graph, Weighting weighting, TraversalMode traversalMode) {
            return new DijkstraBidirectionRef(graph, weighting, traversalMode);
        }

        @Override
        public String toString() {
            return "DIJKSTRA_BIDIR";
        }
    }

    private static class AStarCalculator extends SimpleCalculator {
        @Override
        RoutingAlgorithm createAlgo(Graph graph, Weighting weighting, TraversalMode traversalMode) {
            return new AStar(graph, weighting, traversalMode);
        }

        @Override
        public String toString() {
            return "ASTAR";
        }
    }

    private static class BidirAStarCalculator extends SimpleCalculator {
        @Override
        RoutingAlgorithm createAlgo(Graph graph, Weighting weighting, TraversalMode traversalMode) {
            return new AStarBidirection(graph, weighting, traversalMode);
        }

        @Override
        public String toString() {
            return "ASTAR_BIDIR";
        }
    }

    private static class DijkstraOneToManyCalculator extends SimpleCalculator {
        @Override
        RoutingAlgorithm createAlgo(Graph graph, Weighting weighting, TraversalMode traversalMode) {
            return new DijkstraOneToMany(graph, weighting, traversalMode);
        }

        @Override
        public String toString() {
            return "DIJKSTRA_ONE_TO_MANY";
        }
    }

    private static abstract class CHCalculator implements PathCalculator {
        @Override
        public Path calcPath(GraphHopperStorage graph, Weighting weighting, TraversalMode traversalMode, int maxVisitedNodes, int from, int to) {
            CHProfile chProfile = new CHProfile(weighting, traversalMode, TurnWeighting.INFINITE_U_TURN_COSTS);
            PrepareContractionHierarchies pch = PrepareContractionHierarchies.fromGraphHopperStorage(graph, chProfile);
            CHGraph chGraph = graph.getCHGraph(chProfile);
            if (chGraph.getEdges() == chGraph.getOriginalEdges()) {
                graph.freeze();
                pch.doWork();
            }
            RoutingAlgorithmFactory algoFactory = pch.getRoutingAlgorithmFactory();
            AlgorithmOptions opts = AlgorithmOptions.start()
                    .algorithm(getAlgorithm())
                    .weighting(weighting).traversalMode(traversalMode).maxVisitedNodes(maxVisitedNodes).build();
            RoutingAlgorithm algo = algoFactory.createAlgo(chGraph, opts);
            return algo.calcPath(from, to);
        }

        @Override
        public Path calcPath(GraphHopperStorage graph, Weighting weighting, TraversalMode traversalMode, int maxVisitedNodes, GHPoint from, GHPoint to) {
            LocationIndexTree locationIndex = new LocationIndexTree(graph, new RAMDirectory());
            LocationIndex index = locationIndex.prepareIndex();
            QueryResult fromQR = index.findClosest(from.getLat(), from.getLon(), EdgeFilter.ALL_EDGES);
            QueryResult toQR = index.findClosest(to.getLat(), to.getLon(), EdgeFilter.ALL_EDGES);
            return calcPath(graph, weighting, traversalMode, maxVisitedNodes, fromQR, toQR);
        }

        @Override
        public Path calcPath(GraphHopperStorage graph, Weighting weighting, TraversalMode traversalMode, int maxVisitedNodes, QueryResult from, QueryResult to) {
            CHProfile chProfile = new CHProfile(weighting, traversalMode, TurnWeighting.INFINITE_U_TURN_COSTS);
            PrepareContractionHierarchies pch = PrepareContractionHierarchies.fromGraphHopperStorage(graph, chProfile);
            CHGraph chGraph = graph.getCHGraph(chProfile);
            if (chGraph.getEdges() == chGraph.getOriginalEdges()) {
                graph.freeze();
                pch.doWork();
            }
            QueryGraph queryGraph = QueryGraph.lookup(chGraph, from, to);
            RoutingAlgorithmFactory algoFactory = pch.getRoutingAlgorithmFactory();
            AlgorithmOptions opts = AlgorithmOptions.start()
                    .algorithm(getAlgorithm())
                    .weighting(weighting).traversalMode(traversalMode).maxVisitedNodes(maxVisitedNodes).build();
            RoutingAlgorithm algo = algoFactory.createAlgo(queryGraph, opts);
            return algo.calcPath(from.getClosestNode(), to.getClosestNode());

        }

        abstract String getAlgorithm();
    }

    private static class CHAStarCalculator extends CHCalculator {
        @Override
        String getAlgorithm() {
            return ASTAR_BI;
        }

        @Override
        public String toString() {
            return "CH_ASTAR";
        }
    }

    private static class CHDijkstraCalculator extends CHCalculator {
        @Override
        String getAlgorithm() {
            return DIJKSTRA_BI;
        }

        @Override
        public String toString() {
            return "CH_DIJKSTRA";
        }
    }
}
