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
import com.graphhopper.routing.ch.CHRoutingAlgorithmFactory;
import com.graphhopper.routing.ch.PrepareContractionHierarchies;
import com.graphhopper.routing.ev.BooleanEncodedValue;
import com.graphhopper.routing.ev.DecimalEncodedValue;
import com.graphhopper.routing.ev.DecimalEncodedValueImpl;
import com.graphhopper.routing.ev.SimpleBooleanEncodedValue;
import com.graphhopper.routing.querygraph.QueryGraph;
import com.graphhopper.routing.querygraph.QueryRoutingCHGraph;
import com.graphhopper.routing.util.EdgeFilter;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.routing.util.TraversalMode;
import com.graphhopper.routing.weighting.FastestWeighting;
import com.graphhopper.routing.weighting.ShortestWeighting;
import com.graphhopper.routing.weighting.TurnCostProvider;
import com.graphhopper.routing.weighting.Weighting;
import com.graphhopper.storage.*;
import com.graphhopper.storage.index.LocationIndex;
import com.graphhopper.storage.index.LocationIndexTree;
import com.graphhopper.storage.index.Snap;
import com.graphhopper.util.*;
import com.graphhopper.util.shapes.BBox;
import com.graphhopper.util.shapes.GHPoint;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.ArgumentsProvider;
import org.junit.jupiter.params.provider.ArgumentsSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.stream.Stream;

import static com.graphhopper.routing.util.TraversalMode.EDGE_BASED;
import static com.graphhopper.routing.util.TraversalMode.NODE_BASED;
import static com.graphhopper.util.DistanceCalcEarth.DIST_EARTH;
import static com.graphhopper.util.GHUtility.updateDistancesFor;
import static com.graphhopper.util.Parameters.Algorithms.ASTAR_BI;
import static com.graphhopper.util.Parameters.Algorithms.DIJKSTRA_BI;
import static com.graphhopper.util.Parameters.Routing.ALGORITHM;
import static com.graphhopper.util.Parameters.Routing.MAX_VISITED_NODES;
import static org.junit.jupiter.api.Assertions.*;

/**
 * This test tests the different routing algorithms on small user-defined sample graphs. It tests node- and edge-based
 * algorithms, but does *not* use turn costs, because otherwise node- and edge-based implementations would not be
 * comparable. For edge-based traversal u-turns cannot happen, even with {@link TurnCostProvider#NO_TURN_COST_PROVIDER},
 * because as long as we do not apply turn restrictions we will never take a u-turn.
 * All tests should follow the same pattern:
 * <p>
 * - create a GH storage, you need to pass all the weightings used in this test to {@link Fixture#createGHStorage}, such that
 * the according CHGraphs can be created
 * - build the graph: add edges, nodes, set different edge properties etc.
 * - calculate a path using one of the {@link Fixture#calcPath} methods and compare it with the expectations
 * <p>
 * The tests are run for all algorithms specified in {@link FixtureProvider}}.
 *
 * @author Peter Karich
 * @author easbar
 * @see EdgeBasedRoutingAlgorithmTest for similar tests including turn costs
 */
public class RoutingAlgorithmTest {
    private static final Logger LOGGER = LoggerFactory.getLogger(RoutingAlgorithmTest.class);

    private static class Fixture {
        private final EncodingManager encodingManager;
        private final BooleanEncodedValue carAccessEnc;
        private final BooleanEncodedValue footAccessEnc;
        private final BooleanEncodedValue bike2AccessEnc;
        private final DecimalEncodedValue carSpeedEnc;
        private final DecimalEncodedValue footSpeedEnc;
        private final DecimalEncodedValue bike2SpeedEnc;
        private final PathCalculator pathCalculator;
        private final TraversalMode traversalMode;
        private final Weighting defaultWeighting;
        private final int defaultMaxVisitedNodes;

        public Fixture(PathCalculator pathCalculator, TraversalMode traversalMode) {
            this.pathCalculator = pathCalculator;
            this.traversalMode = traversalMode;
            carAccessEnc = new SimpleBooleanEncodedValue("car_access", true);
            footAccessEnc = new SimpleBooleanEncodedValue("foot_access", true);
            bike2AccessEnc = new SimpleBooleanEncodedValue("bike2_access", true);
            carSpeedEnc = new DecimalEncodedValueImpl("car_speed", 5, 5, false);
            footSpeedEnc = new DecimalEncodedValueImpl("foot_speed", 4, 1, false);
            bike2SpeedEnc = new DecimalEncodedValueImpl("bike2_speed", 4, 2, true);
            encodingManager = EncodingManager.start()
                    .add(carAccessEnc).add(carSpeedEnc)
                    .add(footAccessEnc).add(footSpeedEnc)
                    .add(bike2AccessEnc).add(bike2SpeedEnc).build();
            // most tests use the default weighting, but this can be chosen for each test separately
            defaultWeighting = new ShortestWeighting(carAccessEnc, carSpeedEnc);
            // most tests do not limit the number of visited nodes, but this can be chosen for each test separately
            defaultMaxVisitedNodes = Integer.MAX_VALUE;
        }

        @Override
        public String toString() {
            return pathCalculator + ", " + traversalMode;
        }

        /**
         * Creates a GH storage supporting the default weighting for CH
         */
        private BaseGraph createGHStorage() {
            return createGHStorage(false);
        }

        /**
         * Creates a GH storage supporting the given weightings for CH
         */
        private BaseGraph createGHStorage(boolean is3D) {
            return new BaseGraph.Builder(encodingManager).set3D(is3D)
                    // this test should never include turn costs, but we have to set it to true to be able to
                    // run edge-based algorithms
                    .withTurnCosts(traversalMode.isEdgeBased())
                    .create();
        }

        private Path calcPath(BaseGraph graph, int from, int to) {
            return calcPath(graph, defaultWeighting, from, to);
        }

        private Path calcPath(BaseGraph graph, Weighting weighting, int from, int to) {
            return calcPath(graph, weighting, defaultMaxVisitedNodes, from, to);
        }

        private Path calcPath(BaseGraph graph, Weighting weighting, int maxVisitedNodes, int from, int to) {
            return pathCalculator.calcPath(graph, weighting, traversalMode, maxVisitedNodes, from, to);
        }

        private Path calcPath(BaseGraph graph, GHPoint from, GHPoint to) {
            return calcPath(graph, defaultWeighting, from, to);
        }

        private Path calcPath(BaseGraph graph, Weighting weighting, GHPoint from, GHPoint to) {
            return pathCalculator.calcPath(graph, weighting, traversalMode, defaultMaxVisitedNodes, from, to);
        }

        private Path calcPath(BaseGraph graph, Weighting weighting, int fromNode1, int fromNode2, int toNode1, int toNode2) {
            // lookup two edges: fromNode1-fromNode2 and toNode1-toNode2
            Snap from = createSnapBetweenNodes(graph, fromNode1, fromNode2);
            Snap to = createSnapBetweenNodes(graph, toNode1, toNode2);
            return pathCalculator.calcPath(graph, weighting, traversalMode, defaultMaxVisitedNodes, from, to);
        }

        /**
         * Creates snaps on edge (node1-node2) very close to node1.
         */
        private Snap createSnapBetweenNodes(Graph graph, int node1, int node2) {
            EdgeIteratorState edge = GHUtility.getEdge(graph, node1, node2);
            if (edge == null)
                throw new IllegalStateException("edge not found? " + node1 + "-" + node2);

            NodeAccess na = graph.getNodeAccess();
            double lat = na.getLat(edge.getBaseNode());
            double lon = na.getLon(edge.getBaseNode());
            double latAdj = na.getLat(edge.getAdjNode());
            double lonAdj = na.getLon(edge.getAdjNode());
            // calculate query point near the base node but not directly on it!
            Snap res = new Snap(lat + (latAdj - lat) * .1, lon + (lonAdj - lon) * .1);
            res.setClosestNode(edge.getBaseNode());
            res.setClosestEdge(edge);
            res.setWayIndex(0);
            res.setSnappedPosition(Snap.Position.EDGE);
            res.calcSnappedPoint(DIST_EARTH);
            return res;
        }

        private void compareWithRef(Weighting weighting, BaseGraph graph, PathCalculator refCalculator, GHPoint from, GHPoint to, long seed) {
            Path path = calcPath(graph, weighting, from, to);
            Path refPath = refCalculator.calcPath(graph, weighting, traversalMode, defaultMaxVisitedNodes, from, to);
            assertEquals(refPath.getWeight(), path.getWeight(), 1.e-1, "wrong weight, " + weighting + ", seed: " + seed);
            assertEquals(refPath.calcNodes(), path.calcNodes(), "wrong nodes, " + weighting + ", seed: " + seed);
            assertEquals(refPath.getDistance(), path.getDistance(), 1.e-1, "wrong distance, " + weighting + ", seed: " + seed);
            assertEquals(refPath.getTime(), path.getTime(), 100, "wrong time, " + weighting + ", seed: " + seed);
        }

        public void resetCH() {
            // ugly: we need to clear the ch graphs map when we use a new graph. currently we have to use this map because
            //       otherwise we get an error because of the already existing DataAccess in the DA map in Directory.
            if (pathCalculator instanceof CHCalculator) {
                ((CHCalculator) pathCalculator).routingCHGraphs.clear();
            }
        }
    }

    private static class FixtureProvider implements ArgumentsProvider {
        @Override
        public Stream<? extends Arguments> provideArguments(ExtensionContext context) throws Exception {
            return Stream.of(
                    new Fixture(new DijkstraCalculator(), NODE_BASED),
                    new Fixture(new DijkstraCalculator(), EDGE_BASED),
                    new Fixture(new BidirDijkstraCalculator(), NODE_BASED),
                    new Fixture(new BidirDijkstraCalculator(), EDGE_BASED),
                    new Fixture(new AStarCalculator(), NODE_BASED),
                    new Fixture(new AStarCalculator(), EDGE_BASED),
                    new Fixture(new BidirAStarCalculator(), NODE_BASED),
                    new Fixture(new BidirAStarCalculator(), EDGE_BASED),
                    // so far only supports node-based
                    new Fixture(new DijkstraOneToManyCalculator(), NODE_BASED),
                    new Fixture(new CHAStarCalculator(), NODE_BASED),
                    new Fixture(new CHAStarCalculator(), EDGE_BASED),
                    new Fixture(new CHDijkstraCalculator(), NODE_BASED),
                    new Fixture(new CHDijkstraCalculator(), EDGE_BASED)
            ).map(Arguments::of);
        }
    }

    @ParameterizedTest
    @ArgumentsSource(FixtureProvider.class)
    public void testCalcShortestPath(Fixture f) {
        BaseGraph graph = f.createGHStorage();
        initTestStorage(graph, f.carAccessEnc, f.carSpeedEnc);
        Path p = f.calcPath(graph, 0, 7);
        assertEquals(nodes(0, 4, 5, 7), p.calcNodes(), p.toString());
        assertEquals(62.1, p.getDistance(), .1, p.toString());
    }

    @ParameterizedTest
    @ArgumentsSource(FixtureProvider.class)
    public void testCalcShortestPath_sourceEqualsTarget(Fixture f) {
        // 0-1-2
        BaseGraph graph = f.createGHStorage();
        GHUtility.setSpeed(60, true, true, f.carAccessEnc, f.carSpeedEnc, graph.edge(0, 1).setDistance(1));
        GHUtility.setSpeed(60, true, true, f.carAccessEnc, f.carSpeedEnc, graph.edge(1, 2).setDistance(2));

        Path p = f.calcPath(graph, 0, 0);
        assertPathFromEqualsTo(p, 0);
    }

    @ParameterizedTest
    @ArgumentsSource(FixtureProvider.class)
    public void testSimpleAlternative(Fixture f) {
        // 0--2--1
        //    |  |
        //    3--4
        BaseGraph graph = f.createGHStorage();
        GHUtility.setSpeed(60, true, true, f.carAccessEnc, f.carSpeedEnc, graph.edge(0, 2).setDistance(9));
        GHUtility.setSpeed(60, true, true, f.carAccessEnc, f.carSpeedEnc, graph.edge(2, 1).setDistance(2));
        GHUtility.setSpeed(60, true, true, f.carAccessEnc, f.carSpeedEnc, graph.edge(2, 3).setDistance(11));
        GHUtility.setSpeed(60, true, true, f.carAccessEnc, f.carSpeedEnc, graph.edge(3, 4).setDistance(6));
        GHUtility.setSpeed(60, true, true, f.carAccessEnc, f.carSpeedEnc, graph.edge(4, 1).setDistance(9));
        Path p = f.calcPath(graph, 0, 4);
        assertEquals(20, p.getDistance(), 1e-4, p.toString());
        assertEquals(nodes(0, 2, 1, 4), p.calcNodes());
    }

    @ParameterizedTest
    @ArgumentsSource(FixtureProvider.class)
    public void testBidirectionalLinear(Fixture f) {
        //3--2--1--4--5
        BaseGraph graph = f.createGHStorage();
        GHUtility.setSpeed(60, true, true, f.carAccessEnc, f.carSpeedEnc, graph.edge(2, 1).setDistance(2));
        GHUtility.setSpeed(60, true, true, f.carAccessEnc, f.carSpeedEnc, graph.edge(2, 3).setDistance(11));
        GHUtility.setSpeed(60, true, true, f.carAccessEnc, f.carSpeedEnc, graph.edge(5, 4).setDistance(6));
        GHUtility.setSpeed(60, true, true, f.carAccessEnc, f.carSpeedEnc, graph.edge(4, 1).setDistance(9));
        Path p = f.calcPath(graph, 3, 5);
        assertEquals(28, p.getDistance(), 1e-4, p.toString());
        assertEquals(nodes(3, 2, 1, 4, 5), p.calcNodes());
    }

    // see calc-fastest-graph.svg
    @ParameterizedTest
    @ArgumentsSource(FixtureProvider.class)
    public void testCalcFastestPath(Fixture f) {
        FastestWeighting fastestWeighting = new FastestWeighting(f.carAccessEnc, f.carSpeedEnc);
        BaseGraph graph = f.createGHStorage(false);
        initDirectedAndDiffSpeed(graph, f.carAccessEnc, f.carSpeedEnc);

        Path p1 = f.calcPath(graph, f.defaultWeighting, 0, 3);
        assertEquals(nodes(0, 1, 5, 2, 3), p1.calcNodes());
        assertEquals(402.3, p1.getDistance(), .1, p1.toString());
        assertEquals(144830, p1.getTime(), p1.toString());

        Path p2 = f.calcPath(graph, fastestWeighting, 0, 3);
        assertEquals(nodes(0, 4, 6, 7, 5, 3), p2.calcNodes());
        assertEquals(1261.7, p2.getDistance(), 0.1, p2.toString());
        assertEquals(111441, p2.getTime(), p2.toString());
    }

    // 0-1-2-3
    // |/|/ /|
    // 4-5-- |
    // |/ \--7
    // 6----/
    static void initDirectedAndDiffSpeed(Graph graph, BooleanEncodedValue accessEnc, DecimalEncodedValue speedEnc) {
        GHUtility.setSpeed(10, true, false, accessEnc, speedEnc, graph.edge(0, 1));
        GHUtility.setSpeed(100, true, false, accessEnc, speedEnc, graph.edge(0, 4));

        GHUtility.setSpeed(10, true, true, accessEnc, speedEnc, graph.edge(1, 4));
        GHUtility.setSpeed(10, true, true, accessEnc, speedEnc, graph.edge(1, 5));
        EdgeIteratorState edge12 = GHUtility.setSpeed(10, true, true, accessEnc, speedEnc, graph.edge(1, 2));

        GHUtility.setSpeed(10, true, false, accessEnc, speedEnc, graph.edge(5, 2));
        GHUtility.setSpeed(10, true, false, accessEnc, speedEnc, graph.edge(2, 3));

        EdgeIteratorState edge53 = GHUtility.setSpeed(20, true, false, accessEnc, speedEnc, graph.edge(5, 3));
        GHUtility.setSpeed(10, true, false, accessEnc, speedEnc, graph.edge(3, 7));

        GHUtility.setSpeed(100, true, false, accessEnc, speedEnc, graph.edge(4, 6));
        GHUtility.setSpeed(10, true, false, accessEnc, speedEnc, graph.edge(5, 4));

        GHUtility.setSpeed(10, true, false, accessEnc, speedEnc, graph.edge(5, 6));
        GHUtility.setSpeed(100, true, false, accessEnc, speedEnc, graph.edge(7, 5));

        GHUtility.setSpeed(100, true, true, accessEnc, speedEnc, graph.edge(6, 7));

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

    @ParameterizedTest
    @ArgumentsSource(FixtureProvider.class)
    public void testCalcFootPath(Fixture f) {
        ShortestWeighting shortestWeighting = new ShortestWeighting(f.footAccessEnc, f.footSpeedEnc);
        BaseGraph graph = f.createGHStorage(false);
        initFootVsCar(f.carAccessEnc, f.carSpeedEnc, f.footAccessEnc, f.footSpeedEnc, graph);
        Path p1 = f.calcPath(graph, shortestWeighting, 0, 7);
        assertEquals(17000, p1.getDistance(), 1e-6, p1.toString());
        assertEquals(12240 * 1000, p1.getTime(), p1.toString());
        assertEquals(nodes(0, 4, 5, 7), p1.calcNodes());
    }

    static void initFootVsCar(BooleanEncodedValue carAccessEnc, DecimalEncodedValue carSpeedEnc,
                              BooleanEncodedValue footAccessEnc, DecimalEncodedValue footSpeedEnc, Graph graph) {
        EdgeIteratorState edge = graph.edge(0, 1).setDistance(7000);
        GHUtility.setSpeed(5, true, true, footAccessEnc, footSpeedEnc, edge);
        GHUtility.setSpeed(10, true, false, carAccessEnc, carSpeedEnc, edge);
        edge = graph.edge(0, 4).setDistance(5000);
        GHUtility.setSpeed(5, true, true, footAccessEnc, footSpeedEnc, edge);
        GHUtility.setSpeed(20, true, false, carAccessEnc, carSpeedEnc, edge);

        GHUtility.setSpeed(10, true, true, carAccessEnc, carSpeedEnc, graph.edge(1, 4).setDistance(7000));
        GHUtility.setSpeed(10, true, true, carAccessEnc, carSpeedEnc, graph.edge(1, 5).setDistance(7000));
        edge = graph.edge(1, 2).setDistance(20000);
        GHUtility.setSpeed(5, true, true, footAccessEnc, footSpeedEnc, edge);
        GHUtility.setSpeed(10, true, true, carAccessEnc, carSpeedEnc, edge);

        GHUtility.setSpeed(10, true, false, carAccessEnc, carSpeedEnc, graph.edge(5, 2).setDistance(5000));
        edge = graph.edge(2, 3).setDistance(5000);
        GHUtility.setSpeed(5, true, true, footAccessEnc, footSpeedEnc, edge);
        GHUtility.setSpeed(10, true, false, carAccessEnc, carSpeedEnc, edge);

        GHUtility.setSpeed(20, true, false, carAccessEnc, carSpeedEnc, graph.edge(5, 3).setDistance(11000));
        edge = graph.edge(3, 7).setDistance(7000);
        GHUtility.setSpeed(5, true, true, footAccessEnc, footSpeedEnc, edge);
        GHUtility.setSpeed(10, true, false, carAccessEnc, carSpeedEnc, edge);

        GHUtility.setSpeed(20, true, false, carAccessEnc, carSpeedEnc, graph.edge(4, 6).setDistance(5000));
        edge = graph.edge(5, 4).setDistance(7000);
        GHUtility.setSpeed(5, true, true, footAccessEnc, footSpeedEnc, edge);
        GHUtility.setSpeed(10, true, false, carAccessEnc, carSpeedEnc, edge);

        GHUtility.setSpeed(10, true, false, carAccessEnc, carSpeedEnc, graph.edge(5, 6).setDistance(7000));
        edge = graph.edge(7, 5).setDistance(5000);
        GHUtility.setSpeed(5, true, true, footAccessEnc, footSpeedEnc, edge);
        GHUtility.setSpeed(20, true, false, carAccessEnc, carSpeedEnc, edge);

        GHUtility.setSpeed(20, true, true, carAccessEnc, carSpeedEnc, graph.edge(6, 7).setDistance(5000));
    }

    // see test-graph.svg !
    static void initTestStorage(Graph graph, BooleanEncodedValue accessEnc, DecimalEncodedValue speedEnc) {
        GHUtility.setSpeed(60, true, true, accessEnc, speedEnc, graph.edge(0, 1).setDistance(7));
        GHUtility.setSpeed(60, true, true, accessEnc, speedEnc, graph.edge(0, 4).setDistance(6));

        GHUtility.setSpeed(60, true, true, accessEnc, speedEnc, graph.edge(1, 4).setDistance(2));
        GHUtility.setSpeed(60, true, true, accessEnc, speedEnc, graph.edge(1, 5).setDistance(8));
        GHUtility.setSpeed(60, true, true, accessEnc, speedEnc, graph.edge(1, 2).setDistance(2));

        GHUtility.setSpeed(60, true, true, accessEnc, speedEnc, graph.edge(2, 5).setDistance(5));
        GHUtility.setSpeed(60, true, true, accessEnc, speedEnc, graph.edge(2, 3).setDistance(2));

        GHUtility.setSpeed(60, true, true, accessEnc, speedEnc, graph.edge(3, 5).setDistance(2));
        GHUtility.setSpeed(60, true, true, accessEnc, speedEnc, graph.edge(3, 7).setDistance(10));

        GHUtility.setSpeed(60, true, true, accessEnc, speedEnc, graph.edge(4, 6).setDistance(4));
        GHUtility.setSpeed(60, true, true, accessEnc, speedEnc, graph.edge(4, 5).setDistance(7));

        GHUtility.setSpeed(60, true, true, accessEnc, speedEnc, graph.edge(5, 6).setDistance(2));
        GHUtility.setSpeed(60, true, true, accessEnc, speedEnc, graph.edge(5, 7).setDistance(1));

        EdgeIteratorState edge6_7 = GHUtility.setSpeed(60, true, true, accessEnc, speedEnc, graph.edge(6, 7).setDistance(5));

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

    @ParameterizedTest
    @ArgumentsSource(FixtureProvider.class)
    public void testNoPathFound(Fixture f) {
        BaseGraph graph = f.createGHStorage();
        graph.edge(100, 101);
        assertFalse(f.calcPath(graph, 0, 1).isFound());

        graph = f.createGHStorage();
        graph.edge(100, 101);

        // two disconnected areas
        // 0-1
        //
        // 7-5-6
        //  \|
        //   8
        GHUtility.setSpeed(60, true, true, f.carAccessEnc, f.carSpeedEnc, graph.edge(0, 1).setDistance(7));
        GHUtility.setSpeed(60, true, true, f.carAccessEnc, f.carSpeedEnc, graph.edge(5, 6).setDistance(2));
        GHUtility.setSpeed(60, true, true, f.carAccessEnc, f.carSpeedEnc, graph.edge(5, 7).setDistance(1));
        GHUtility.setSpeed(60, true, true, f.carAccessEnc, f.carSpeedEnc, graph.edge(5, 8).setDistance(1));
        GHUtility.setSpeed(60, true, true, f.carAccessEnc, f.carSpeedEnc, graph.edge(7, 8).setDistance(1));
        assertFalse(f.calcPath(graph, 0, 5).isFound());

        // disconnected as directed graph
        // 2-0->1
        graph = f.createGHStorage();
        GHUtility.setSpeed(60, true, false, f.carAccessEnc, f.carSpeedEnc, graph.edge(0, 1).setDistance(1));
        GHUtility.setSpeed(60, true, true, f.carAccessEnc, f.carSpeedEnc, graph.edge(0, 2).setDistance(1));
        f.resetCH();
        assertFalse(f.calcPath(graph, 1, 2).isFound());
        assertTrue(f.calcPath(graph, 2, 1).isFound());
    }

    @ParameterizedTest
    @ArgumentsSource(FixtureProvider.class)
    public void testWikipediaShortestPath(Fixture f) {
        BaseGraph graph = f.createGHStorage();
        initWikipediaTestGraph(graph, f.carAccessEnc, f.carSpeedEnc);
        Path p = f.calcPath(graph, 0, 4);
        assertEquals(nodes(0, 2, 5, 4), p.calcNodes(), p.toString());
        assertEquals(20, p.getDistance(), 1e-4, p.toString());
    }

    @ParameterizedTest
    @ArgumentsSource(FixtureProvider.class)
    public void testCalcIf1EdgeAway(Fixture f) {
        BaseGraph graph = f.createGHStorage();
        initTestStorage(graph, f.carAccessEnc, f.carSpeedEnc);
        Path p = f.calcPath(graph, 1, 2);
        assertEquals(nodes(1, 2), p.calcNodes());
        assertEquals(35.1, p.getDistance(), .1, p.toString());
    }

    // see wikipedia-graph.svg !
    private void initWikipediaTestGraph(Graph graph, BooleanEncodedValue accessEnc, DecimalEncodedValue speedEnc) {
        GHUtility.setSpeed(60, true, true, accessEnc, speedEnc, graph.edge(0, 1).setDistance(7));
        GHUtility.setSpeed(60, true, true, accessEnc, speedEnc, graph.edge(0, 2).setDistance(9));
        GHUtility.setSpeed(60, true, true, accessEnc, speedEnc, graph.edge(0, 5).setDistance(14));
        GHUtility.setSpeed(60, true, true, accessEnc, speedEnc, graph.edge(1, 2).setDistance(10));
        GHUtility.setSpeed(60, true, true, accessEnc, speedEnc, graph.edge(1, 3).setDistance(15));
        GHUtility.setSpeed(60, true, true, accessEnc, speedEnc, graph.edge(2, 5).setDistance(2));
        GHUtility.setSpeed(60, true, true, accessEnc, speedEnc, graph.edge(2, 3).setDistance(11));
        GHUtility.setSpeed(60, true, true, accessEnc, speedEnc, graph.edge(3, 4).setDistance(6));
        GHUtility.setSpeed(60, true, true, accessEnc, speedEnc, graph.edge(4, 5).setDistance(9));
    }

    @ParameterizedTest
    @ArgumentsSource(FixtureProvider.class)
    public void testBidirectional(Fixture f) {
        BaseGraph graph = f.createGHStorage();
        initBiGraph(graph, f.carAccessEnc, f.carSpeedEnc);

        Path p = f.calcPath(graph, 0, 4);
        assertEquals(nodes(0, 7, 6, 8, 3, 4), p.calcNodes(), p.toString());
        assertEquals(335.8, p.getDistance(), .1, p.toString());

        p = f.calcPath(graph, 1, 2);
        // the other way around is even larger as 0-1 is already 11008.452
        assertEquals(nodes(1, 2), p.calcNodes(), p.toString());
        assertEquals(10007.7, p.getDistance(), .1, p.toString());
    }

    // 0-1-2-3-4
    // |     / |
    // |    8  |
    // \   /   |
    //  7-6----5
    public static void initBiGraph(Graph graph, BooleanEncodedValue accessEnc, DecimalEncodedValue speedEnc) {
        // distance will be overwritten in second step as we need to calculate it from lat,lon
        GHUtility.setSpeed(60, true, true, accessEnc, speedEnc, graph.edge(0, 1).setDistance(1));
        GHUtility.setSpeed(60, true, true, accessEnc, speedEnc, graph.edge(1, 2).setDistance(1));
        GHUtility.setSpeed(60, true, true, accessEnc, speedEnc, graph.edge(2, 3).setDistance(1));
        GHUtility.setSpeed(60, true, true, accessEnc, speedEnc, graph.edge(3, 4).setDistance(1));
        GHUtility.setSpeed(60, true, true, accessEnc, speedEnc, graph.edge(4, 5).setDistance(1));
        GHUtility.setSpeed(60, true, true, accessEnc, speedEnc, graph.edge(5, 6).setDistance(1));
        GHUtility.setSpeed(60, true, true, accessEnc, speedEnc, graph.edge(6, 7).setDistance(1));
        GHUtility.setSpeed(60, true, true, accessEnc, speedEnc, graph.edge(7, 0).setDistance(1));
        GHUtility.setSpeed(60, true, true, accessEnc, speedEnc, graph.edge(3, 8).setDistance(1));
        GHUtility.setSpeed(60, true, true, accessEnc, speedEnc, graph.edge(8, 6).setDistance(1));

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

    @ParameterizedTest
    @ArgumentsSource(FixtureProvider.class)
    public void testCreateAlgoTwice(Fixture f) {
        // 0-1-2-3-4
        // |     / |
        // |    8  |
        // \   /   /
        //  7-6-5-/
        BaseGraph graph = f.createGHStorage();
        GHUtility.setSpeed(60, true, true, f.carAccessEnc, f.carSpeedEnc, graph.edge(0, 1).setDistance(1));
        GHUtility.setSpeed(60, true, true, f.carAccessEnc, f.carSpeedEnc, graph.edge(1, 2).setDistance(1));
        GHUtility.setSpeed(60, true, true, f.carAccessEnc, f.carSpeedEnc, graph.edge(2, 3).setDistance(1));
        GHUtility.setSpeed(60, true, true, f.carAccessEnc, f.carSpeedEnc, graph.edge(3, 4).setDistance(1));
        GHUtility.setSpeed(60, true, true, f.carAccessEnc, f.carSpeedEnc, graph.edge(4, 5).setDistance(1));
        GHUtility.setSpeed(60, true, true, f.carAccessEnc, f.carSpeedEnc, graph.edge(5, 6).setDistance(1));
        GHUtility.setSpeed(60, true, true, f.carAccessEnc, f.carSpeedEnc, graph.edge(6, 7).setDistance(1));
        GHUtility.setSpeed(60, true, true, f.carAccessEnc, f.carSpeedEnc, graph.edge(7, 0).setDistance(1));
        GHUtility.setSpeed(60, true, true, f.carAccessEnc, f.carSpeedEnc, graph.edge(3, 8).setDistance(1));
        GHUtility.setSpeed(60, true, true, f.carAccessEnc, f.carSpeedEnc, graph.edge(8, 6).setDistance(1));

        // run the same query twice, this can be interesting because in the second call algorithms that pre-process
        // the graph might depend on the state of the graph after the first call 
        Path p1 = f.calcPath(graph, 0, 4);
        Path p2 = f.calcPath(graph, 0, 4);

        assertEquals(p1.calcNodes(), p2.calcNodes());
    }

    @ParameterizedTest
    @ArgumentsSource(FixtureProvider.class)
    public void testMaxVisitedNodes(Fixture f) {
        BaseGraph graph = f.createGHStorage();
        initBiGraph(graph, f.carAccessEnc, f.carSpeedEnc);

        Path p = f.calcPath(graph, 0, 4);
        assertTrue(p.isFound());
        int maxVisitedNodes = 3;
        p = f.calcPath(graph, f.defaultWeighting, maxVisitedNodes, 0, 4);
        assertFalse(p.isFound());
    }

    @ParameterizedTest
    @ArgumentsSource(FixtureProvider.class)
    public void testBidirectional2(Fixture f) {
        BaseGraph graph = f.createGHStorage();
        initBidirGraphManualDistances(graph, f.carAccessEnc, f.carSpeedEnc);
        Path p = f.calcPath(graph, 0, 4);
        assertEquals(40, p.getDistance(), 1e-4, p.toString());
        assertEquals(5, p.calcNodes().size(), p.toString());
        assertEquals(nodes(0, 7, 6, 5, 4), p.calcNodes());
    }

    private void initBidirGraphManualDistances(BaseGraph graph, BooleanEncodedValue accessEnc, DecimalEncodedValue speedEnc) {
        // 0-1-2-3-4
        // |     / |
        // |    8  |
        // \   /   /
        //  7-6-5-/
        GHUtility.setSpeed(60, true, true, accessEnc, speedEnc, graph.edge(0, 1).setDistance(100));
        GHUtility.setSpeed(60, true, true, accessEnc, speedEnc, graph.edge(1, 2).setDistance(1));
        GHUtility.setSpeed(60, true, true, accessEnc, speedEnc, graph.edge(2, 3).setDistance(1));
        GHUtility.setSpeed(60, true, true, accessEnc, speedEnc, graph.edge(3, 4).setDistance(1));
        GHUtility.setSpeed(60, true, true, accessEnc, speedEnc, graph.edge(4, 5).setDistance(20));
        GHUtility.setSpeed(60, true, true, accessEnc, speedEnc, graph.edge(5, 6).setDistance(10));
        GHUtility.setSpeed(60, true, true, accessEnc, speedEnc, graph.edge(6, 7).setDistance(5));
        GHUtility.setSpeed(60, true, true, accessEnc, speedEnc, graph.edge(7, 0).setDistance(5));
        GHUtility.setSpeed(60, true, true, accessEnc, speedEnc, graph.edge(3, 8).setDistance(20));
        GHUtility.setSpeed(60, true, true, accessEnc, speedEnc, graph.edge(8, 6).setDistance(20));
    }

    @ParameterizedTest
    @ArgumentsSource(FixtureProvider.class)
    public void testRekeyBugOfIntBinHeap(Fixture f) {
        // using Dijkstra + IntBinHeap then rekey loops endlessly
        BaseGraph matrixGraph = f.createGHStorage();
        initMatrixALikeGraph(matrixGraph, f.carAccessEnc, f.carSpeedEnc);
        Path p = f.calcPath(matrixGraph, 36, 91);
        assertEquals(12, p.calcNodes().size());

        IntIndexedContainer nodes = p.calcNodes();
        if (!nodes(36, 46, 56, 66, 76, 86, 85, 84, 94, 93, 92, 91).equals(nodes)
                && !nodes(36, 46, 56, 66, 76, 86, 85, 84, 83, 82, 92, 91).equals(nodes)) {
            fail("wrong locations: " + nodes.toString());
        }
        assertEquals(66f, p.getDistance(), 1e-3);

        testBug1(f, matrixGraph);
        testCorrectWeight(f, matrixGraph);
    }

    private static void initMatrixALikeGraph(BaseGraph tmpGraph, BooleanEncodedValue accessEnc, DecimalEncodedValue speedEnc) {
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

                    GHUtility.setSpeed(60, true, true, accessEnc, speedEnc, tmpGraph.edge(matrix[w][h], matrix[w][h - 1]).setDistance(dist));
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
                    GHUtility.setSpeed(60, true, true, accessEnc, speedEnc, tmpGraph.edge(matrix[w][h], matrix[w - 1][h]).setDistance(dist));
                }
                if (print)
                    System.out.print("(" + matrix[w][h] + ")\t");
            }
            if (print)
                System.out.println();
        }
    }

    private void testBug1(Fixture f, BaseGraph g) {
        Path p = f.calcPath(g, 34, 36);
        assertEquals(nodes(34, 35, 36), p.calcNodes());
        assertEquals(3, p.calcNodes().size());
        assertEquals(17, p.getDistance(), 1e-5);
    }

    private void testCorrectWeight(Fixture f, BaseGraph g) {
        Path p = f.calcPath(g, 45, 72);
        assertEquals(nodes(45, 44, 54, 64, 74, 73, 72), p.calcNodes());
        assertEquals(38f, p.getDistance(), 1e-3);
    }

    @ParameterizedTest
    @ArgumentsSource(FixtureProvider.class)
    public void testCannotCalculateSP(Fixture f) {
        // 0->1->2
        BaseGraph graph = f.createGHStorage();
        GHUtility.setSpeed(60, true, false, f.carAccessEnc, f.carSpeedEnc, graph.edge(0, 1).setDistance(1));
        GHUtility.setSpeed(60, true, false, f.carAccessEnc, f.carSpeedEnc, graph.edge(1, 2).setDistance(1));
        Path p = f.calcPath(graph, 0, 2);
        assertEquals(3, p.calcNodes().size(), p.toString());
    }

    @ParameterizedTest
    @ArgumentsSource(FixtureProvider.class)
    public void testDirectedGraphBug1(Fixture f) {
        BaseGraph graph = f.createGHStorage();
        GHUtility.setSpeed(60, true, false, f.carAccessEnc, f.carSpeedEnc, graph.edge(0, 1).setDistance(3));
        GHUtility.setSpeed(60, true, false, f.carAccessEnc, f.carSpeedEnc, graph.edge(1, 2).setDistance(2.99));

        GHUtility.setSpeed(60, true, false, f.carAccessEnc, f.carSpeedEnc, graph.edge(0, 3).setDistance(2));
        GHUtility.setSpeed(60, true, false, f.carAccessEnc, f.carSpeedEnc, graph.edge(3, 4).setDistance(3));
        GHUtility.setSpeed(60, true, false, f.carAccessEnc, f.carSpeedEnc, graph.edge(4, 2).setDistance(1));

        Path p = f.calcPath(graph, 0, 2);
        assertEquals(nodes(0, 1, 2), p.calcNodes(), p.toString());
        assertEquals(5.99, p.getDistance(), 1e-4, p.toString());
    }

    @ParameterizedTest
    @ArgumentsSource(FixtureProvider.class)
    public void testDirectedGraphBug2(Fixture f) {
        // 0->1->2
        //    | /
        //    3<
        BaseGraph graph = f.createGHStorage();
        GHUtility.setSpeed(60, true, false, f.carAccessEnc, f.carSpeedEnc, graph.edge(0, 1).setDistance(1));
        GHUtility.setSpeed(60, true, false, f.carAccessEnc, f.carSpeedEnc, graph.edge(1, 2).setDistance(1));
        GHUtility.setSpeed(60, true, false, f.carAccessEnc, f.carSpeedEnc, graph.edge(2, 3).setDistance(1));
        GHUtility.setSpeed(60, true, true, f.carAccessEnc, f.carSpeedEnc, graph.edge(3, 1).setDistance(4));

        Path p = f.calcPath(graph, 0, 3);
        assertEquals(nodes(0, 1, 2, 3), p.calcNodes());
    }

    // a-b-0-c-1
    // |   |  _/\
    // |  /  /  |
    // d-2--3-e-4
    @ParameterizedTest
    @ArgumentsSource(FixtureProvider.class)
    public void testWithCoordinates(Fixture f) {
        Weighting weighting = new ShortestWeighting(f.carAccessEnc, f.carSpeedEnc);
        BaseGraph graph = f.createGHStorage(false);
        GHUtility.setSpeed(60, true, true, f.carAccessEnc, f.carSpeedEnc, graph.edge(0, 1).setDistance(2)).
                setWayGeometry(Helper.createPointList(1.5, 1));
        GHUtility.setSpeed(60, true, true, f.carAccessEnc, f.carSpeedEnc, graph.edge(2, 3).setDistance(2)).
                setWayGeometry(Helper.createPointList(0, 1.5));
        GHUtility.setSpeed(60, true, true, f.carAccessEnc, f.carSpeedEnc, graph.edge(3, 4).setDistance(2)).
                setWayGeometry(Helper.createPointList(0, 2));

        // duplicate but the second edge is longer
        GHUtility.setSpeed(60, true, true, f.carAccessEnc, f.carSpeedEnc, graph.edge(0, 2).setDistance(1.2));
        GHUtility.setSpeed(60, true, true, f.carAccessEnc, f.carSpeedEnc, graph.edge(0, 2).setDistance(1.5)).
                setWayGeometry(Helper.createPointList(0.5, 0));

        GHUtility.setSpeed(60, true, true, f.carAccessEnc, f.carSpeedEnc, graph.edge(1, 3).setDistance(1.3)).
                setWayGeometry(Helper.createPointList(0.5, 1.5));
        GHUtility.setSpeed(60, true, true, f.carAccessEnc, f.carSpeedEnc, graph.edge(1, 4).setDistance(1));

        updateDistancesFor(graph, 0, 1, 0.6);
        updateDistancesFor(graph, 1, 1, 1.5);
        updateDistancesFor(graph, 2, 0, 0);
        updateDistancesFor(graph, 3, 0, 1);
        updateDistancesFor(graph, 4, 0, 2);

        Path p = f.calcPath(graph, weighting, 4, 0);
        assertEquals(nodes(4, 1, 0), p.calcNodes());
        assertEquals(Helper.createPointList(0, 2, 1, 1.5, 1.5, 1, 1, 0.6), p.calcPoints());
        assertEquals(274128, new DistanceCalcEarth().calcDistance(p.calcPoints()), 1);

        p = f.calcPath(graph, weighting, 2, 1);
        assertEquals(nodes(2, 0, 1), p.calcNodes());
        assertEquals(Helper.createPointList(0, 0, 1, 0.6, 1.5, 1, 1, 1.5), p.calcPoints());
        assertEquals(279482, new DistanceCalcEarth().calcDistance(p.calcPoints()), 1);
    }

    @ParameterizedTest
    @ArgumentsSource(FixtureProvider.class)
    public void testCalcIfEmptyWay(Fixture f) {
        BaseGraph graph = f.createGHStorage();
        initTestStorage(graph, f.carAccessEnc, f.carSpeedEnc);
        Path p = f.calcPath(graph, 0, 0);
        assertPathFromEqualsTo(p, 0);
    }

    @ParameterizedTest
    @ArgumentsSource(FixtureProvider.class)
    public void testViaEdges_FromEqualsTo(Fixture f) {
        BaseGraph graph = f.createGHStorage();
        initTestStorage(graph, f.carAccessEnc, f.carSpeedEnc);
        // identical tower nodes
        Path p = f.calcPath(graph, new GHPoint(0.001, 0.000), new GHPoint(0.001, 0.000));
        assertPathFromEqualsTo(p, 0);

        // identical query points on edge
        p = f.calcPath(graph, f.defaultWeighting, 0, 1, 0, 1);
        assertPathFromEqualsTo(p, 8);

        // very close
        p = f.calcPath(graph, new GHPoint(0.00092, 0), new GHPoint(0.00091, 0));
        assertEquals(nodes(8, 9), p.calcNodes());
        assertEquals(1.11, p.getDistance(), .1, p.toString());
    }

    @ParameterizedTest
    @ArgumentsSource(FixtureProvider.class)
    public void testViaEdges_BiGraph(Fixture f) {
        BaseGraph graph = f.createGHStorage();
        initBiGraph(graph, f.carAccessEnc, f.carSpeedEnc);

        // 0-7 to 4-3
        Path p = f.calcPath(graph, new GHPoint(0.0009, 0), new GHPoint(0.001, 0.001105));
        assertEquals(nodes(10, 7, 6, 8, 3, 9), p.calcNodes(), p.toString());
        assertEquals(324.12, p.getDistance(), 0.01, p.toString());

        // 0-1 to 2-3
        p = f.calcPath(graph, new GHPoint(0.001, 0.0001), new GHPoint(0.010, 0.0011));
        assertEquals(nodes(0, 7, 6, 8, 3, 9), p.calcNodes(), p.toString());
        assertEquals(1335.38, p.getDistance(), 0.01, p.toString());
    }

    @ParameterizedTest
    @ArgumentsSource(FixtureProvider.class)
    public void testViaEdges_WithCoordinates(Fixture f) {
        BaseGraph graph = f.createGHStorage();
        initTestStorage(graph, f.carAccessEnc, f.carSpeedEnc);
        Path p = f.calcPath(graph, f.defaultWeighting, 0, 1, 2, 3);
        assertEquals(nodes(8, 1, 2, 9), p.calcNodes());
        assertEquals(56.7, p.getDistance(), .1, p.toString());
    }

    @ParameterizedTest
    @ArgumentsSource(FixtureProvider.class)
    public void testViaEdges_SpecialCases(Fixture f) {
        BaseGraph graph = f.createGHStorage();
        // 0->1\
        // |    2
        // 4<-3/
        GHUtility.setSpeed(60, true, false, f.carAccessEnc, f.carSpeedEnc, graph.edge(0, 1).setDistance(7));
        GHUtility.setSpeed(60, true, true, f.carAccessEnc, f.carSpeedEnc, graph.edge(1, 2).setDistance(7));
        GHUtility.setSpeed(60, true, true, f.carAccessEnc, f.carSpeedEnc, graph.edge(2, 3).setDistance(7));
        GHUtility.setSpeed(60, true, false, f.carAccessEnc, f.carSpeedEnc, graph.edge(3, 4).setDistance(7));
        GHUtility.setSpeed(60, true, true, f.carAccessEnc, f.carSpeedEnc, graph.edge(4, 0).setDistance(7));

        updateDistancesFor(graph, 4, 0, 0);
        updateDistancesFor(graph, 0, 0.00010, 0);
        updateDistancesFor(graph, 1, 0.00010, 0.0001);
        updateDistancesFor(graph, 2, 0.00005, 0.00015);
        updateDistancesFor(graph, 3, 0, 0.0001);

        // 0-1 to 3-4
        Path p = f.calcPath(graph, new GHPoint(0.00010, 0.00001), new GHPoint(0, 0.00009));
        assertEquals(nodes(5, 1, 2, 3, 6), p.calcNodes());
        assertEquals(26.81, p.getDistance(), .1, p.toString());

        // overlapping edges: 2-3 and 3-2
        p = f.calcPath(graph, new GHPoint(0.000049, 0.00014), new GHPoint(0.00001, 0.0001));
        assertEquals(nodes(5, 6), p.calcNodes());
        assertEquals(6.2, p.getDistance(), .1, p.toString());

        // 'from' and 'to' edge share one node '2': 1-2 to 3-2
        p = f.calcPath(graph, new GHPoint(0.00009, 0.00011), new GHPoint(0.00001, 0.00011));
        assertEquals(nodes(6, 2, 5), p.calcNodes(), p.toString());
        assertEquals(12.57, p.getDistance(), .1, p.toString());
    }

    @ParameterizedTest
    @ArgumentsSource(FixtureProvider.class)
    public void testQueryGraphAndFastest(Fixture f) {
        Weighting weighting = new FastestWeighting(f.carAccessEnc, f.carSpeedEnc);
        BaseGraph graph = f.createGHStorage(false);
        initDirectedAndDiffSpeed(graph, f.carAccessEnc, f.carSpeedEnc);
        Path p = f.calcPath(graph, weighting, new GHPoint(0.002, 0.0005), new GHPoint(0.0017, 0.0031));
        assertEquals(nodes(8, 1, 5, 3, 9), p.calcNodes());
        assertEquals(602.98, p.getDistance(), 1e-1);
    }

    @ParameterizedTest
    @ArgumentsSource(FixtureProvider.class)
    public void testTwoWeightsPerEdge(Fixture f) {
        FastestWeighting fastestWeighting = new FastestWeighting(f.bike2AccessEnc, f.bike2SpeedEnc);
        BaseGraph graph = f.createGHStorage(true);
        initEleGraph(graph, 18, f.bike2AccessEnc, f.bike2SpeedEnc);
        // force the other path
        GHUtility.setSpeed(10, false, true, f.bike2AccessEnc, f.bike2SpeedEnc, GHUtility.getEdge(graph, 0, 3));

        // for two weights per edge it happened that Path (and also the Weighting) read the wrong side
        // of the speed and read 0 => infinity weight => overflow of millis => negative millis!
        Path p = f.calcPath(graph, fastestWeighting, 0, 10);
        assertEquals(85124371, p.getTime());
        assertEquals(425622, p.getDistance(), 1);
        assertEquals(85124.4, p.getWeight(), 1);
    }

    @ParameterizedTest
    @ArgumentsSource(FixtureProvider.class)
    public void test0SpeedButUnblocked_Issue242(Fixture f) {
        BaseGraph graph = f.createGHStorage();
        EdgeIteratorState edge01 = graph.edge(0, 1).setDistance(10);
        EdgeIteratorState edge12 = graph.edge(1, 2).setDistance(10);
        edge01.set(f.carSpeedEnc, 0.0).set(f.carAccessEnc, true, true);
        edge01.setFlags(edge01.getFlags());

        edge12.set(f.carSpeedEnc, 0.0).set(f.carAccessEnc, true, true);
        edge12.setFlags(edge12.getFlags());

        try {
            f.calcPath(graph, 0, 2);
            fail("there should have been an exception");
        } catch (Exception ex) {
            assertTrue(ex.getMessage().startsWith("Speed cannot be 0"), ex.getMessage());
        }
    }

    @ParameterizedTest
    @ArgumentsSource(FixtureProvider.class)
    public void testTwoWeightsPerEdge2(Fixture f) {
        // other direction should be different!
        Weighting fakeWeighting = new Weighting() {
            private final Weighting tmpW = new FastestWeighting(f.carAccessEnc, f.carSpeedEnc);

            @Override
            public double getMinWeight(double distance) {
                return 0.8 * distance;
            }

            public boolean edgeHasNoAccess(EdgeIteratorState edgeState, boolean reverse) {
                return tmpW.edgeHasNoAccess(edgeState, reverse);
            }

            @Override
            public final double calcEdgeWeight(EdgeIteratorState edgeState, boolean reverse) {
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
            public final long calcEdgeMillis(EdgeIteratorState edgeState, boolean reverse) {
                return tmpW.calcEdgeMillis(edgeState, reverse);
            }

            @Override
            public double calcTurnWeight(int inEdge, int viaNode, int outEdge) {
                return tmpW.calcTurnWeight(inEdge, viaNode, outEdge);
            }

            @Override
            public long calcTurnMillis(int inEdge, int viaNode, int outEdge) {
                return tmpW.calcTurnMillis(inEdge, viaNode, outEdge);
            }

            @Override
            public boolean hasTurnCosts() {
                return tmpW.hasTurnCosts();
            }

            @Override
            public String getName() {
                return "custom";
            }

            @Override
            public String toString() {
                return getName();
            }
        };

        BaseGraph graph = f.createGHStorage(true);
        initEleGraph(graph, 60, f.carAccessEnc, f.carSpeedEnc);
        Path p = f.calcPath(graph, 0, 10);
        assertEquals(nodes(0, 4, 6, 10), p.calcNodes());

        graph = f.createGHStorage(true);
        initEleGraph(graph, 60, f.carAccessEnc, f.carSpeedEnc);
        p = f.calcPath(graph, fakeWeighting, 3, 0, 10, 9);
        assertEquals(nodes(12, 0, 1, 2, 11, 7, 10, 13), p.calcNodes());
        assertEquals(37009625, p.getTime());
        assertEquals(616827, p.getDistance(), 1);
        assertEquals(493462, p.getWeight(), 1);
    }

    @ParameterizedTest
    @ArgumentsSource(FixtureProvider.class)
    public void testRandomGraph(Fixture f) {
        // todo: use speed both directions
        FastestWeighting fastestWeighting = new FastestWeighting(f.carAccessEnc, f.carSpeedEnc);
        BaseGraph graph = f.createGHStorage(false);
        final long seed = System.nanoTime();
        LOGGER.info("testRandomGraph - using seed: " + seed);
        Random rnd = new Random(seed);
        // we're not including loops otherwise duplicate nodes in path might fail the test
        GHUtility.buildRandomGraph(graph, rnd, 10, 2.0, false, true,
                f.carAccessEnc, f.carSpeedEnc, null, 0.7, 0.7, 0.7);
        final PathCalculator refCalculator = new DijkstraCalculator();
        int numRuns = 100;
        for (int i = 0; i < numRuns; i++) {
            BBox bounds = graph.getBounds();
            double latFrom = bounds.minLat + rnd.nextDouble() * (bounds.maxLat - bounds.minLat);
            double lonFrom = bounds.minLon + rnd.nextDouble() * (bounds.maxLon - bounds.minLon);
            double latTo = bounds.minLat + rnd.nextDouble() * (bounds.maxLat - bounds.minLat);
            double lonTo = bounds.minLon + rnd.nextDouble() * (bounds.maxLon - bounds.minLon);
            f.compareWithRef(fastestWeighting, graph, refCalculator, new GHPoint(latFrom, lonFrom), new GHPoint(latTo, lonTo), seed);
        }
    }

    @ParameterizedTest
    @ArgumentsSource(FixtureProvider.class)
    public void testMultipleVehicles_issue548(Fixture f) {
        FastestWeighting footWeighting = new FastestWeighting(f.footAccessEnc, f.footSpeedEnc);
        FastestWeighting carWeighting = new FastestWeighting(f.carAccessEnc, f.carSpeedEnc);

        BaseGraph graph = f.createGHStorage(false);
        initFootVsCar(f.carAccessEnc, f.carSpeedEnc, f.footAccessEnc, f.footSpeedEnc, graph);

        // normal path would be 0-4-6-7 for car:
        Path carPath1 = f.calcPath(graph, carWeighting, 0, 7);
        assertEquals(nodes(0, 4, 6, 7), carPath1.calcNodes());
        assertEquals(15000, carPath1.getDistance(), 1e-6, carPath1.toString());
        // ... and 0-4-5-7 for foot
        Path footPath1 = f.calcPath(graph, footWeighting, 0, 7);
        assertEquals(nodes(0, 4, 5, 7), footPath1.calcNodes());
        assertEquals(17000, footPath1.getDistance(), 1e-6, footPath1.toString());

        // ... but now we block 4-6 for car. note that we have to recreate the storage to create a new directory so
        //     we can create new CHs :(
        graph = f.createGHStorage(false);
        initFootVsCar(f.carAccessEnc, f.carSpeedEnc, f.footAccessEnc, f.footSpeedEnc, graph);
        GHUtility.setSpeed(20, false, false, f.carAccessEnc, f.carSpeedEnc, GHUtility.getEdge(graph, 4, 6));
        f.resetCH();

        // ... car needs to take another way
        Path carPath2 = f.calcPath(graph, carWeighting, 0, 7);
        assertEquals(nodes(0, 1, 5, 6, 7), carPath2.calcNodes());
        assertEquals(26000, carPath2.getDistance(), 1e-6, carPath2.toString());
        // ... for foot it stays the same
        Path footPath2 = f.calcPath(graph, footWeighting, 0, 7);
        assertEquals(nodes(0, 4, 5, 7), footPath2.calcNodes());
        assertEquals(17000, footPath2.getDistance(), 1e-6, footPath2.toString());
    }

    // 0-1-2
    // |\| |
    // 3 4-11
    // | | |
    // 5-6-7
    // | |\|
    // 8-9-10
    private void initEleGraph(Graph graph, double s, BooleanEncodedValue accessEnc, DecimalEncodedValue speedEnc) {
        GHUtility.setSpeed(s, true, true, accessEnc, speedEnc, graph.edge(0, 1).setDistance(10));
        GHUtility.setSpeed(s, true, true, accessEnc, speedEnc, graph.edge(0, 4).setDistance(12));
        GHUtility.setSpeed(s, true, true, accessEnc, speedEnc, graph.edge(0, 3).setDistance(5));
        GHUtility.setSpeed(s, true, true, accessEnc, speedEnc, graph.edge(1, 2).setDistance(10));
        GHUtility.setSpeed(s, true, true, accessEnc, speedEnc, graph.edge(1, 4).setDistance(5));
        GHUtility.setSpeed(s, true, false, accessEnc, speedEnc, graph.edge(3, 5).setDistance(5));
        GHUtility.setSpeed(s, true, true, accessEnc, speedEnc, graph.edge(5, 6).setDistance(10));
        GHUtility.setSpeed(s, true, true, accessEnc, speedEnc, graph.edge(5, 8).setDistance(10));
        GHUtility.setSpeed(s, true, true, accessEnc, speedEnc, graph.edge(6, 4).setDistance(5));
        GHUtility.setSpeed(s, true, true, accessEnc, speedEnc, graph.edge(6, 7).setDistance(10));
        GHUtility.setSpeed(s, true, true, accessEnc, speedEnc, graph.edge(6, 10).setDistance(12));
        GHUtility.setSpeed(s, true, true, accessEnc, speedEnc, graph.edge(6, 9).setDistance(12));
        GHUtility.setSpeed(s, true, false, accessEnc, speedEnc, graph.edge(2, 11).setDistance(5));
        GHUtility.setSpeed(s, true, true, accessEnc, speedEnc, graph.edge(4, 11).setDistance(10));
        GHUtility.setSpeed(s, true, true, accessEnc, speedEnc, graph.edge(7, 11).setDistance(5));
        GHUtility.setSpeed(s, true, true, accessEnc, speedEnc, graph.edge(7, 10).setDistance(5));
        GHUtility.setSpeed(s, true, false, accessEnc, speedEnc, graph.edge(8, 9).setDistance(10));
        GHUtility.setSpeed(s, true, false, accessEnc, speedEnc, graph.edge(9, 8).setDistance(9));
        GHUtility.setSpeed(s, true, false, accessEnc, speedEnc, graph.edge(10, 9).setDistance(10));
        updateDistancesFor(graph, 0, 3, 0);
        updateDistancesFor(graph, 3, 2.5, 0);
        updateDistancesFor(graph, 5, 1, 0);
        updateDistancesFor(graph, 8, 0, 0);
        updateDistancesFor(graph, 1, 3, 1);
        updateDistancesFor(graph, 4, 2, 1);
        updateDistancesFor(graph, 6, 1, 1);
        updateDistancesFor(graph, 9, 0, 1);
        updateDistancesFor(graph, 2, 3, 2);
        updateDistancesFor(graph, 11, 2, 2);
        updateDistancesFor(graph, 7, 1, 2);
        updateDistancesFor(graph, 10, 0, 2);
    }

    private static String getCHGraphName(Weighting weighting) {
        return weighting.getName() + "_" + weighting.hashCode();
    }

    private static void assertPathFromEqualsTo(Path p, int node) {
        assertTrue(p.isFound());
        assertEquals(nodes(node), p.calcNodes(), p.toString());
        assertEquals(1, p.calcPoints().size(), p.toString());
        assertEquals(0, p.calcEdges().size(), p.toString());
        assertEquals(0, p.getWeight(), 1e-4, p.toString());
        assertEquals(0, p.getDistance(), 1e-4, p.toString());
        assertEquals(0, p.getTime(), 1e-4, p.toString());
    }

    private static IntArrayList nodes(int... nodes) {
        return IntArrayList.from(nodes);
    }

    /**
     * Implement this interface to run the test cases with different routing algorithms
     */
    private interface PathCalculator {
        Path calcPath(BaseGraph graph, Weighting weighting, TraversalMode traversalMode, int maxVisitedNodes, int from, int to);

        Path calcPath(BaseGraph graph, Weighting weighting, TraversalMode traversalMode, int maxVisitedNodes, GHPoint from, GHPoint to);

        Path calcPath(BaseGraph graph, Weighting weighting, TraversalMode traversalMode, int maxVisitedNodes, Snap from, Snap to);
    }

    private static abstract class SimpleCalculator implements PathCalculator {
        @Override
        public Path calcPath(BaseGraph graph, Weighting weighting, TraversalMode traversalMode, int maxVisitedNodes, int from, int to) {
            RoutingAlgorithm algo = createAlgo(graph, weighting, traversalMode);
            algo.setMaxVisitedNodes(maxVisitedNodes);
            return algo.calcPath(from, to);
        }

        @Override
        public Path calcPath(BaseGraph graph, Weighting weighting, TraversalMode traversalMode, int maxVisitedNodes, GHPoint from, GHPoint to) {
            LocationIndexTree index = new LocationIndexTree(graph, new RAMDirectory());
            index.prepareIndex();
            Snap fromSnap = index.findClosest(from.getLat(), from.getLon(), EdgeFilter.ALL_EDGES);
            Snap toSnap = index.findClosest(to.getLat(), to.getLon(), EdgeFilter.ALL_EDGES);
            return calcPath(graph, weighting, traversalMode, maxVisitedNodes, fromSnap, toSnap);
        }

        @Override
        public Path calcPath(BaseGraph graph, Weighting weighting, TraversalMode traversalMode, int maxVisitedNodes, Snap from, Snap to) {
            QueryGraph queryGraph = QueryGraph.create(graph, from, to);
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
        private final Map<String, RoutingCHGraph> routingCHGraphs = new HashMap<>();

        @Override
        public Path calcPath(BaseGraph graph, Weighting weighting, TraversalMode traversalMode, int maxVisitedNodes, int from, int to) {
            String chGraphName = getCHGraphName(weighting) + (traversalMode.isEdgeBased() ? "_edge" : "_node");
            RoutingCHGraph routingCHGraph = routingCHGraphs.computeIfAbsent(chGraphName, name -> {
                if (!graph.isFrozen())
                    graph.freeze();
                CHConfig chConfig = new CHConfig(name, weighting, traversalMode.isEdgeBased());
                PrepareContractionHierarchies pch = PrepareContractionHierarchies.fromGraph(graph, chConfig);
                PrepareContractionHierarchies.Result res = pch.doWork();
                return RoutingCHGraphImpl.fromGraph(graph, res.getCHStorage(), res.getCHConfig());
            });
            RoutingAlgorithm algo = new CHRoutingAlgorithmFactory(routingCHGraph).createAlgo(new PMap()
                    .putObject(ALGORITHM, getAlgorithm())
                    .putObject(MAX_VISITED_NODES, maxVisitedNodes)
            );
            return algo.calcPath(from, to);
        }

        @Override
        public Path calcPath(BaseGraph graph, Weighting weighting, TraversalMode traversalMode, int maxVisitedNodes, GHPoint from, GHPoint to) {
            LocationIndexTree locationIndex = new LocationIndexTree(graph, new RAMDirectory());
            LocationIndex index = locationIndex.prepareIndex();
            Snap fromSnap = index.findClosest(from.getLat(), from.getLon(), EdgeFilter.ALL_EDGES);
            Snap toSnap = index.findClosest(to.getLat(), to.getLon(), EdgeFilter.ALL_EDGES);
            return calcPath(graph, weighting, traversalMode, maxVisitedNodes, fromSnap, toSnap);
        }

        @Override
        public Path calcPath(BaseGraph graph, Weighting weighting, TraversalMode traversalMode, int maxVisitedNodes, Snap from, Snap to) {
            String chGraphName = getCHGraphName(weighting) + (traversalMode.isEdgeBased() ? "_edge" : "_node");
            RoutingCHGraph routingCHGraph = routingCHGraphs.computeIfAbsent(chGraphName, name -> {
                graph.freeze();
                CHConfig chConfig = new CHConfig(name, weighting, traversalMode.isEdgeBased());
                PrepareContractionHierarchies pch = PrepareContractionHierarchies.fromGraph(graph, chConfig);
                PrepareContractionHierarchies.Result res = pch.doWork();
                return RoutingCHGraphImpl.fromGraph(graph, res.getCHStorage(), res.getCHConfig());
            });
            QueryGraph queryGraph = QueryGraph.create(graph, Arrays.asList(from, to));
            QueryRoutingCHGraph queryRoutingCHGraph = new QueryRoutingCHGraph(routingCHGraph, queryGraph);
            RoutingAlgorithm algo = new CHRoutingAlgorithmFactory(queryRoutingCHGraph).createAlgo(new PMap()
                    .putObject(ALGORITHM, getAlgorithm())
                    .putObject(MAX_VISITED_NODES, maxVisitedNodes));
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
