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
import com.carrotsearch.hppc.cursors.IntCursor;
import com.graphhopper.routing.ev.*;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.routing.util.TraversalMode;
import com.graphhopper.routing.weighting.DefaultTurnCostProvider;
import com.graphhopper.routing.weighting.Weighting;
import com.graphhopper.routing.weighting.custom.CustomModelParser;
import com.graphhopper.storage.BaseGraph;
import com.graphhopper.storage.Graph;
import com.graphhopper.storage.TurnCostStorage;
import com.graphhopper.util.CustomModel;
import com.graphhopper.util.EdgeIteratorState;
import com.graphhopper.util.GHUtility;
import com.graphhopper.util.Helper;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.ArgumentsProvider;
import org.junit.jupiter.params.provider.ArgumentsSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.stream.Stream;

import static com.graphhopper.routing.util.TraversalMode.EDGE_BASED;
import static com.graphhopper.util.GHUtility.getEdge;
import static com.graphhopper.util.Parameters.Algorithms.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Peter Karich
 */
public class EdgeBasedRoutingAlgorithmTest {
    private static final Logger LOGGER = LoggerFactory.getLogger(EdgeBasedRoutingAlgorithmTest.class);
    private BooleanEncodedValue accessEnc;
    private DecimalEncodedValue speedEnc;
    private DecimalEncodedValue turnCostEnc;
    private TurnCostStorage tcs;

    private static class FixtureProvider implements ArgumentsProvider {
        @Override
        public Stream<? extends Arguments> provideArguments(ExtensionContext context) {
            return Stream.of(
                    // todo: make this test run also for edge-based CH or otherwise make sure time calculation is tested also for edge-based CH (at the moment it will fail!)
                    // todo: make this test run also for ALT or otherwise make sure time calculation is tested also for ALT (at the moment it will fail?!)
                    DIJKSTRA,
                    DIJKSTRA_BI,
                    ASTAR,
                    ASTAR_BI
                    // TODO { AlgorithmOptions.DIJKSTRA_ONE_TO_MANY }
            ).map(Arguments::of);
        }
    }

    // 0---1
    // |   /
    // 2--3--4
    // |  |  |
    // 5--6--7
    private void initGraph(Graph graph) {
        GHUtility.setSpeed(60, true, true, accessEnc, speedEnc, graph.edge(0, 1).setDistance(3));
        GHUtility.setSpeed(60, true, true, accessEnc, speedEnc, graph.edge(0, 2).setDistance(1));
        GHUtility.setSpeed(60, true, true, accessEnc, speedEnc, graph.edge(1, 3).setDistance(1));
        GHUtility.setSpeed(60, true, true, accessEnc, speedEnc, graph.edge(2, 3).setDistance(1));
        GHUtility.setSpeed(60, true, true, accessEnc, speedEnc, graph.edge(3, 4).setDistance(1));
        GHUtility.setSpeed(60, true, true, accessEnc, speedEnc, graph.edge(2, 5).setDistance(0.5));
        GHUtility.setSpeed(60, true, true, accessEnc, speedEnc, graph.edge(3, 6).setDistance(1));
        GHUtility.setSpeed(60, true, true, accessEnc, speedEnc, graph.edge(4, 7).setDistance(1));
        GHUtility.setSpeed(60, true, true, accessEnc, speedEnc, graph.edge(5, 6).setDistance(1));
        GHUtility.setSpeed(60, true, true, accessEnc, speedEnc, graph.edge(6, 7).setDistance(1));
    }

    private EncodingManager createEncodingManager(boolean restrictedOnly) {
        accessEnc = new SimpleBooleanEncodedValue("access", true);
        speedEnc = new DecimalEncodedValueImpl("speed", 5, 5, false);
        turnCostEnc = TurnCost.create("car", restrictedOnly ? 1 : 3);
        return EncodingManager.start().add(accessEnc).add(speedEnc).addTurnCostEncodedValue(turnCostEnc).build();
    }

    public Path calcPath(Graph g, EncodingManager em, int from, int to, String algoStr) {
        return createAlgo(g, createWeighting(em), algoStr, EDGE_BASED).calcPath(from, to);
    }

    public RoutingAlgorithm createAlgo(Graph g, Weighting weighting, String algoStr, TraversalMode traversalMode) {
        AlgorithmOptions opts = new AlgorithmOptions()
                .setTraversalMode(traversalMode)
                .setAlgorithm(algoStr);
        return new RoutingAlgorithmFactorySimple().createAlgo(g, weighting, opts);
    }

    private BaseGraph createStorage(EncodingManager em) {
        BaseGraph graph = new BaseGraph.Builder(em).withTurnCosts(true).create();
        tcs = graph.getTurnCostStorage();
        return graph;
    }

    private void initTurnRestrictions(BaseGraph g) {
        // only forward from 2-3 to 3-4 => limit 2,3->3,6 and 2,3->3,1
        setTurnRestriction(g, 2, 3, 6);
        setTurnRestriction(g, 2, 3, 1);

        // only right   from 5-2 to 2-3 => limit 5,2->2,0
        setTurnRestriction(g, 5, 2, 0);

        // only right   from 7-6 to 6-3 => limit 7,6->6,5
        setTurnRestriction(g, 7, 6, 5);

        // no 5-6 to 6-3
        setTurnRestriction(g, 5, 6, 3);
        // no 4-3 to 3-1
        setTurnRestriction(g, 4, 3, 1);
        // no 4-3 to 3-2
        setTurnRestriction(g, 4, 3, 2);

        // no u-turn at 6-7
        setTurnRestriction(g, 6, 7, 6);

        // no u-turn at 3-6
        setTurnRestriction(g, 3, 6, 3);
    }

    private Weighting createWeighting(EncodingManager em) {
        return createWeighting(em, Weighting.INFINITE_U_TURN_COSTS);
    }

    private Weighting createWeighting(EncodingManager em, int uTurnCosts) {
        return CustomModelParser.createWeighting(accessEnc, speedEnc, null, em,
                new DefaultTurnCostProvider(turnCostEnc, tcs, uTurnCosts), new CustomModel());
    }

    @ParameterizedTest
    @ArgumentsSource(FixtureProvider.class)
    public void testRandomGraph(String algoStr) {
        long seed = System.nanoTime();
        final int numQueries = 100;
        Random rnd = new Random(seed);
        EncodingManager em = createEncodingManager(false);
        BaseGraph g = createStorage(em);
        GHUtility.buildRandomGraph(g, rnd, 50, 2.2, true, true,
                accessEnc, speedEnc, null, 0.8, 0.8, 0.8);
        GHUtility.addRandomTurnCosts(g, seed, accessEnc, turnCostEnc, 3, tcs);
        g.freeze();
        int numPathsNotFound = 0;
        // todo: reduce redundancy with RandomCHRoutingTest
        List<String> strictViolations = new ArrayList<>();
        for (int i = 0; i < numQueries; i++) {
            int from = rnd.nextInt(g.getNodes());
            int to = rnd.nextInt(g.getNodes());
            Weighting w = createWeighting(em);
            RoutingAlgorithm refAlgo = new Dijkstra(g, w, EDGE_BASED);
            Path refPath = refAlgo.calcPath(from, to);
            double refWeight = refPath.getWeight();
            if (!refPath.isFound()) {
                numPathsNotFound++;
                continue;
            }

            Path path = calcPath(g, em, from, to, algoStr);
            if (!path.isFound()) {
                fail("path not found for " + from + "->" + to + ", expected weight: " + refWeight);
            }

            double weight = path.getWeight();
            if (Math.abs(refWeight - weight) > 1.e-2) {
                LOGGER.warn("expected: " + refPath.calcNodes());
                LOGGER.warn("given:    " + path.calcNodes());
                fail("wrong weight: " + from + "->" + to + ", dijkstra: " + refWeight + " vs. " + algoStr + ": " + path.getWeight());
            }
            if (Math.abs(path.getDistance() - refPath.getDistance()) > 1.e-1) {
                strictViolations.add("wrong distance " + from + "->" + to + ", expected: " + refPath.getDistance() + ", given: " + path.getDistance());
            }
            if (Math.abs(path.getTime() - refPath.getTime()) > 50) {
                strictViolations.add("wrong time " + from + "->" + to + ", expected: " + refPath.getTime() + ", given: " + path.getTime());
            }
        }
        if (numPathsNotFound > 0.9 * numQueries) {
            fail("Too many paths not found: " + numPathsNotFound + "/" + numQueries);
        }
        if (strictViolations.size() > 0.05 * numQueries) {
            fail("Too many strict violations: " + strictViolations.size() + "/" + numQueries + "\n" +
                    Helper.join("\n", strictViolations));
        }
    }

    @ParameterizedTest
    @ArgumentsSource(FixtureProvider.class)
    public void testBasicTurnRestriction(String algoStr) {
        EncodingManager em = createEncodingManager(true);
        BaseGraph g = createStorage(em);
        initGraph(g);
        initTurnRestrictions(g);
        Path p = calcPath(g, em, 5, 1, algoStr);
        assertEquals(IntArrayList.from(5, 2, 3, 4, 7, 6, 3, 1), p.calcNodes());

        // test 7-6-5 and reverse
        p = calcPath(g, em, 5, 7, algoStr);
        assertEquals(IntArrayList.from(5, 6, 7), p.calcNodes());

        p = calcPath(g, em, 7, 5, algoStr);
        assertEquals(IntArrayList.from(7, 6, 3, 2, 5), p.calcNodes());
    }

    @ParameterizedTest
    @ArgumentsSource(FixtureProvider.class)
    public void testLoop_issue1592(String algoStr) {
        EncodingManager em = createEncodingManager(true);
        BaseGraph graph = createStorage(em);
        // 0-6
        //  \ \
        //   4-3
        //   |
        //   1o
        GHUtility.setSpeed(60, true, true, accessEnc, speedEnc, graph.edge(0, 6).setDistance(10));
        GHUtility.setSpeed(60, true, true, accessEnc, speedEnc, graph.edge(6, 3).setDistance(10));
        GHUtility.setSpeed(60, true, true, accessEnc, speedEnc, graph.edge(0, 4).setDistance(1));
        GHUtility.setSpeed(60, true, true, accessEnc, speedEnc, graph.edge(4, 1).setDistance(1));
        GHUtility.setSpeed(60, true, true, accessEnc, speedEnc, graph.edge(4, 3).setDistance(1));
        GHUtility.setSpeed(60, true, true, accessEnc, speedEnc, graph.edge(1, 1).setDistance(10));
        setTurnRestriction(graph, 0, 4, 3);

        Path p = calcPath(graph, em, 0, 3, algoStr);
        assertEquals(14, p.getDistance(), 1.e-3);
        assertEquals(IntArrayList.from(0, 4, 1, 1, 4, 3), p.calcNodes());
    }

    @ParameterizedTest
    @ArgumentsSource(FixtureProvider.class)
    public void testTurnCosts_timeCalculation(String algoStr) {
        // 0 - 1 - 2 - 3 - 4
        EncodingManager em = createEncodingManager(false);
        BaseGraph graph = createStorage(em);
        final int distance = 100;
        final int turnCosts = 2;
        GHUtility.setSpeed(60, true, true, accessEnc, speedEnc, graph.edge(0, 1).setDistance(distance));
        GHUtility.setSpeed(60, true, true, accessEnc, speedEnc, graph.edge(1, 2).setDistance(distance));
        GHUtility.setSpeed(60, true, true, accessEnc, speedEnc, graph.edge(2, 3).setDistance(distance));
        GHUtility.setSpeed(60, true, true, accessEnc, speedEnc, graph.edge(3, 4).setDistance(distance));
        setTurnCost(graph, turnCosts, 1, 2, 3);

        {
            // simple case where turn cost is encountered during forward search
            Path p14 = calcPath(graph, em, 1, 4, algoStr);
            assertDistTimeWeight(p14, 3, distance, 6, turnCosts);
            assertEquals(20, p14.getWeight(), 1.e-6);
            assertEquals(20000, p14.getTime());
        }

        {
            // this test is more involved for bidir algos: the turn costs have to be taken into account also at the
            // node where fwd and bwd searches meet
            Path p04 = calcPath(graph, em, 0, 4, algoStr);
            assertDistTimeWeight(p04, 4, distance, 6, turnCosts);
            assertEquals(26, p04.getWeight(), 1.e-6);
            assertEquals(26000, p04.getTime());
        }
    }

    private void assertDistTimeWeight(Path path, int numEdges, double distPerEdge, double weightPerEdge, int turnCost) {
        assertEquals(numEdges * distPerEdge, path.getDistance(), 1.e-6, "wrong distance");
        assertEquals(numEdges * weightPerEdge + turnCost, path.getWeight(), 1.e-6, "wrong weight");
        assertEquals(1000 * (numEdges * weightPerEdge + turnCost), path.getTime(), 1.e-6, "wrong time");
    }

    private void blockNode3(BaseGraph g) {
        // Totally block this node (all 9 turn restrictions)
        setTurnRestriction(g, 2, 3, 1);
        setTurnRestriction(g, 2, 3, 4);
        setTurnRestriction(g, 4, 3, 1);
        setTurnRestriction(g, 4, 3, 2);
        setTurnRestriction(g, 6, 3, 1);
        setTurnRestriction(g, 6, 3, 4);
        setTurnRestriction(g, 1, 3, 6);
        setTurnRestriction(g, 1, 3, 2);
        setTurnRestriction(g, 1, 3, 4);
    }

    @ParameterizedTest
    @ArgumentsSource(FixtureProvider.class)
    public void testBlockANode(String algoStr) {
        EncodingManager em = createEncodingManager(true);
        BaseGraph g = createStorage(em);
        initGraph(g);
        blockNode3(g);
        for (int i = 0; i <= 7; i++) {
            if (i == 3) continue;
            for (int j = 0; j <= 7; j++) {
                if (j == 3) continue;
                Path p = calcPath(g, em, i, j, algoStr);
                assertTrue(p.isFound()); // We can go from everywhere to everywhere else without using node 3
                for (IntCursor node : p.calcNodes()) {
                    assertNotEquals(3, node.value, p.calcNodes().toString());
                }
            }
        }
    }

    @ParameterizedTest
    @ArgumentsSource(FixtureProvider.class)
    public void testUTurns(String algoStr) {
        EncodingManager em = createEncodingManager(true);
        BaseGraph g = createStorage(em);
        initGraph(g);

        // force u-turn at node 3 by using finite u-turn costs
        getEdge(g, 3, 6).setDistance(0.1);
        getEdge(g, 3, 2).setDistance(864);
        getEdge(g, 1, 0).setDistance(864);

        setTurnRestriction(g, 7, 6, 5);
        setTurnRestriction(g, 4, 3, 6);
        Path p = createAlgo(g, createWeighting(em, 50), algoStr, EDGE_BASED).calcPath(7, 5);

        assertEquals(2 + 2 * 0.1, p.getDistance(), 1.e-6);
        assertEquals(2.2 * 0.06 + 50, p.getWeight(), 1.e-6);
        assertEquals((2.2 * 0.06 + 50) * 1000, p.getTime(), 1.e-6);
        assertEquals(IntArrayList.from(7, 6, 3, 6, 5), p.calcNodes());

        // with default infinite u-turn costs we need to take an expensive detour
        p = calcPath(g, em, 7, 5, algoStr);
        assertEquals(1.1 + 864 + 0.5, p.getDistance(), 1.e-6);
        assertEquals(865.6 * 0.06, p.getWeight(), 1.e-6);
        assertEquals(IntArrayList.from(7, 6, 3, 2, 5), p.calcNodes());

        // no more u-turn 6-3-6 -> now we have to take the expensive roads even with finite u-turn costs
        setTurnRestriction(g, 6, 3, 6);
        p = createAlgo(g, createWeighting(em, 100), algoStr, EDGE_BASED).calcPath(7, 5);

        assertEquals(1.1 + 864 + 0.5, p.getDistance(), 1.e-6);
        assertEquals(865.6 * 0.06, p.getWeight(), 1.e-6);
        assertEquals(IntArrayList.from(7, 6, 3, 2, 5), p.calcNodes());
    }

    @ParameterizedTest
    @ArgumentsSource(FixtureProvider.class)
    public void uTurnCostAtMeetingNode(String algoStr) {
        //           3
        //           |
        // 0 -> 1 -> 2 -> 4 -> 5
        EncodingManager em = createEncodingManager(false);
        BaseGraph g = createStorage(em);
        GHUtility.setSpeed(60, true, false, accessEnc, speedEnc, g.edge(0, 1).setDistance(10));
        GHUtility.setSpeed(60, true, false, accessEnc, speedEnc, g.edge(1, 2).setDistance(10));
        GHUtility.setSpeed(60, true, true, accessEnc, speedEnc, g.edge(2, 3).setDistance(10));
        GHUtility.setSpeed(60, true, false, accessEnc, speedEnc, g.edge(2, 4).setDistance(10));
        GHUtility.setSpeed(60, true, false, accessEnc, speedEnc, g.edge(4, 5).setDistance(10));

        // cannot go straight at node 2
        setTurnRestriction(g, 1, 2, 4);

        // with default/infinite u-turn costs there is no shortest path
        {
            Path path = calcPath(g, em, 0, 5, algoStr);
            assertFalse(path.isFound());
        }

        // with finite u-turn costs it is possible, the u-turn costs should be included
        // here we make sure the default u-turn time is also included at the meeting node for bidir algos
        {
            Path path = createAlgo(g, createWeighting(em, 67), algoStr, EDGE_BASED).calcPath(0, 5);
            assertEquals(60, path.getDistance(), 1.e-6);
            assertEquals(60 * 0.06 + 67, path.getWeight(), 1.e-6);
            assertEquals((36 + 670) * 100, path.getTime(), 1.e-6);
        }
    }

    @ParameterizedTest
    @ArgumentsSource(FixtureProvider.class)
    public void testBasicTurnCosts(String algoStr) {
        EncodingManager em = createEncodingManager(false);
        BaseGraph g = createStorage(em);
        initGraph(g);
        Path p = calcPath(g, em, 5, 1, algoStr);

        // no restriction and costs
        assertEquals(IntArrayList.from(5, 2, 3, 1), p.calcNodes());

        // now introduce some turn costs
        getEdge(g, 5, 6).setDistance(2);
        setTurnCost(g, 2, 5, 2, 3);

        p = calcPath(g, em, 5, 1, algoStr);
        assertEquals(IntArrayList.from(5, 6, 3, 1), p.calcNodes());
    }

    @ParameterizedTest
    @ArgumentsSource(FixtureProvider.class)
    public void testTurnCostsBug_991(String algoStr) {
        final BaseGraph g = createStorage(createEncodingManager(false));
        initGraph(g);

        setTurnCost(g, 2, 5, 2, 3);
        setTurnCost(g, 2, 2, 0, 1);
        setTurnCost(g, 2, 5, 6, 3);
        setTurnCost(g, 1, 6, 7, 4);

        Weighting weighting = new InternalShortestWeighting(accessEnc, speedEnc, new DefaultTurnCostProvider(turnCostEnc, tcs) {
            @Override
            public double calcTurnWeight(int edgeFrom, int nodeVia, int edgeTo) {
                if (edgeFrom >= 0)
                    assertNotNull(g.getEdgeIteratorState(edgeFrom, nodeVia), "edge " + edgeFrom + " to " + nodeVia + " does not exist");
                if (edgeTo >= 0)
                    assertNotNull(g.getEdgeIteratorState(edgeTo, nodeVia), "edge " + edgeTo + " to " + nodeVia + " does not exist");
                return super.calcTurnWeight(edgeFrom, nodeVia, edgeTo);
            }
        }) {
            @Override
            public double calcEdgeWeight(EdgeIteratorState edgeState, boolean reverse) {
                double speed = reverse ? edgeState.getReverse(speedEnc) : edgeState.get(speedEnc);
                return edgeState.getDistance() / speed * 3.6;
            }
        };
        Path p = createAlgo(g, weighting, algoStr, EDGE_BASED).calcPath(5, 1);
        assertEquals(IntArrayList.from(5, 6, 7, 4, 3, 1), p.calcNodes());
        assertEquals(5 * 0.06 + 1, p.getWeight(), 1.e-6);
        assertEquals(1300, p.getTime(), .1);
    }

    @ParameterizedTest
    @ArgumentsSource(FixtureProvider.class)
    public void testLoopEdge(String algoStr) {
        //   o
        // 3-2-4
        //  \|
        //   0
        EncodingManager em = createEncodingManager(false);
        final BaseGraph graph = createStorage(em);
        GHUtility.setSpeed(60, true, false, accessEnc, speedEnc, graph.edge(3, 2).setDistance(188));
        GHUtility.setSpeed(60, true, true, accessEnc, speedEnc, graph.edge(3, 0).setDistance(182));
        GHUtility.setSpeed(60, true, true, accessEnc, speedEnc, graph.edge(4, 2).setDistance(690));
        GHUtility.setSpeed(60, true, false, accessEnc, speedEnc, graph.edge(2, 2).setDistance(121));
        GHUtility.setSpeed(60, true, true, accessEnc, speedEnc, graph.edge(2, 0).setDistance(132));
        setTurnRestriction(graph, 2, 2, 0);
        setTurnRestriction(graph, 3, 2, 4);

        Path p = calcPath(graph, em, 3, 4, algoStr);
        assertEquals(IntArrayList.from(3, 2, 2, 4), p.calcNodes());
        assertEquals(999, p.getDistance(), 1.e-3);
    }

    @ParameterizedTest
    @ArgumentsSource(FixtureProvider.class)
    public void testDoubleLoopPTurn(String algoStr) {
        // we cannot go 1-4-5, but taking the loop at 4 is cheaper than taking the one at 3
        //  0-1
        //    |
        // o3-4o
        //    |
        //    5
        EncodingManager em = createEncodingManager(false);
        final BaseGraph graph = createStorage(em);
        GHUtility.setSpeed(60, true, true, accessEnc, speedEnc, graph.edge(0, 1).setDistance(1));
        GHUtility.setSpeed(60, true, true, accessEnc, speedEnc, graph.edge(3, 4).setDistance(2));
        GHUtility.setSpeed(60, true, true, accessEnc, speedEnc, graph.edge(4, 4).setDistance(4));
        GHUtility.setSpeed(60, true, true, accessEnc, speedEnc, graph.edge(3, 3).setDistance(1));
        GHUtility.setSpeed(60, true, true, accessEnc, speedEnc, graph.edge(1, 4).setDistance(5));
        GHUtility.setSpeed(60, true, true, accessEnc, speedEnc, graph.edge(5, 4).setDistance(1));
        setTurnRestriction(graph, 1, 4, 5);

        Path p = calcPath(graph, em, 0, 5, algoStr);
        assertEquals(IntArrayList.from(0, 1, 4, 4, 5), p.calcNodes());
        assertEquals(11, p.getDistance(), 1.e-3);
        assertEquals(11 * 0.06, p.getWeight(), 1.e-3);
        assertEquals(11 * 0.06 * 1000, p.getTime(), 1.e-3);
    }

    private void setTurnRestriction(BaseGraph g, int from, int via, int to) {
        setTurnCost(g, Double.POSITIVE_INFINITY, from, via, to);
    }

    private void setTurnCost(BaseGraph g, double cost, int from, int via, int to) {
        g.getTurnCostStorage().set(turnCostEnc, getEdge(g, from, via).getEdge(), via, getEdge(g, via, to).getEdge(), cost);
    }
}
