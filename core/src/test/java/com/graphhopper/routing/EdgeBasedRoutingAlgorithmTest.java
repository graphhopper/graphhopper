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
import com.graphhopper.routing.ev.EncodedValueLookup;
import com.graphhopper.routing.ev.TurnCost;
import com.graphhopper.routing.util.CarFlagEncoder;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.routing.util.FlagEncoder;
import com.graphhopper.routing.util.TraversalMode;
import com.graphhopper.routing.weighting.DefaultTurnCostProvider;
import com.graphhopper.routing.weighting.FastestWeighting;
import com.graphhopper.routing.weighting.Weighting;
import com.graphhopper.storage.Graph;
import com.graphhopper.storage.GraphBuilder;
import com.graphhopper.storage.GraphHopperStorage;
import com.graphhopper.storage.TurnCostStorage;
import com.graphhopper.util.GHUtility;
import com.graphhopper.util.Helper;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

import static com.graphhopper.routing.util.TraversalMode.EDGE_BASED;
import static com.graphhopper.util.GHUtility.getEdge;
import static com.graphhopper.util.Parameters.Algorithms.*;
import static org.junit.Assert.*;

/**
 * @author Peter Karich
 */
@RunWith(Parameterized.class)
public class EdgeBasedRoutingAlgorithmTest {
    private static final Logger LOGGER = LoggerFactory.getLogger(EdgeBasedRoutingAlgorithmTest.class);
    private final String algoStr;
    private FlagEncoder carEncoder;
    private TurnCostStorage tcs;

    public EdgeBasedRoutingAlgorithmTest(String algo) {
        this.algoStr = algo;
    }

    @Parameters(name = "{0}")
    public static Collection<Object[]> configs() {
        return Arrays.asList(new Object[][]{
                // todo: make this test run also for edge-based CH or otherwise make sure time calculation is tested also for edge-based CH (at the moment it will fail!)
                // todo: make this test run also for ALT or otherwise make sure time calculation is tested also for ALT (at the moment it will fail?!)
                {DIJKSTRA},
                {DIJKSTRA_BI},
                {ASTAR},
                {ASTAR_BI}
                // TODO { AlgorithmOptions.DIJKSTRA_ONE_TO_MANY }
        });
    }

    // 0---1
    // |   /
    // 2--3--4
    // |  |  |
    // 5--6--7
    public static void initGraph(Graph graph, FlagEncoder encoder) {
        GHUtility.setSpeed(60, true, true, encoder, graph.edge(0, 1).setDistance(3));
        GHUtility.setSpeed(60, true, true, encoder, graph.edge(0, 2).setDistance(1));
        GHUtility.setSpeed(60, true, true, encoder, graph.edge(1, 3).setDistance(1));
        GHUtility.setSpeed(60, true, true, encoder, graph.edge(2, 3).setDistance(1));
        GHUtility.setSpeed(60, true, true, encoder, graph.edge(3, 4).setDistance(1));
        GHUtility.setSpeed(60, true, true, encoder, graph.edge(2, 5).setDistance(0.5));
        GHUtility.setSpeed(60, true, true, encoder, graph.edge(3, 6).setDistance(1));
        GHUtility.setSpeed(60, true, true, encoder, graph.edge(4, 7).setDistance(1));
        GHUtility.setSpeed(60, true, true, encoder, graph.edge(5, 6).setDistance(1));
        GHUtility.setSpeed(60, true, true, encoder, graph.edge(6, 7).setDistance(1));
    }

    private EncodingManager createEncodingManager(boolean restrictedOnly) {
        carEncoder = new CarFlagEncoder(5, 5, restrictedOnly ? 1 : 3);
        return EncodingManager.create(carEncoder);
    }

    public Path calcPath(Graph g, int from, int to) {
        return createAlgo(g, createWeighting(), EDGE_BASED).calcPath(from, to);
    }

    public RoutingAlgorithm createAlgo(Graph g, Weighting weighting, TraversalMode traversalMode) {
        AlgorithmOptions opts = AlgorithmOptions.start()
                .traversalMode(traversalMode)
                .algorithm(algoStr)
                .build();
        return new RoutingAlgorithmFactorySimple().createAlgo(g, weighting, opts);
    }

    private GraphHopperStorage createStorage(EncodingManager em) {
        GraphHopperStorage ghStorage = new GraphBuilder(em).create();
        tcs = ghStorage.getTurnCostStorage();
        return ghStorage;
    }

    private void initTurnRestrictions(GraphHopperStorage g) {
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

    private Weighting createWeighting() {
        return createWeighting(carEncoder, Weighting.INFINITE_U_TURN_COSTS);
    }

    private Weighting createWeighting(FlagEncoder encoder, int uTurnCosts) {
        return new FastestWeighting(encoder, new DefaultTurnCostProvider(encoder, tcs, uTurnCosts));
    }

    @Test
    public void testRandomGraph() {
        long seed = System.nanoTime();
        final int numQueries = 100;
        Random rnd = new Random(seed);
        EncodingManager em = createEncodingManager(false);
        GraphHopperStorage g = createStorage(em);
        GHUtility.buildRandomGraph(g, rnd, 50, 2.2, true, true,
                carEncoder.getAccessEnc(), carEncoder.getAverageSpeedEnc(), null, 0.8, 0.8, 0.8);
        GHUtility.addRandomTurnCosts(g, seed, em, carEncoder, 3, tcs);
        g.freeze();
        int numPathsNotFound = 0;
        // todo: reduce redundancy with RandomCHRoutingTest
        List<String> strictViolations = new ArrayList<>();
        for (int i = 0; i < numQueries; i++) {
            int from = rnd.nextInt(g.getNodes());
            int to = rnd.nextInt(g.getNodes());
            Weighting w = createWeighting();
            RoutingAlgorithm refAlgo = new Dijkstra(g, w, EDGE_BASED);
            Path refPath = refAlgo.calcPath(from, to);
            double refWeight = refPath.getWeight();
            if (!refPath.isFound()) {
                numPathsNotFound++;
                continue;
            }

            Path path = calcPath(g, from, to);
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

    @Test
    public void testBasicTurnRestriction() {
        GraphHopperStorage g = createStorage(createEncodingManager(true));
        initGraph(g, carEncoder);
        initTurnRestrictions(g);
        Path p = calcPath(g, 5, 1);
        assertEquals(IntArrayList.from(5, 2, 3, 4, 7, 6, 3, 1), p.calcNodes());

        // test 7-6-5 and reverse
        p = calcPath(g, 5, 7);
        assertEquals(IntArrayList.from(5, 6, 7), p.calcNodes());

        p = calcPath(g, 7, 5);
        assertEquals(IntArrayList.from(7, 6, 3, 2, 5), p.calcNodes());
    }

    @Test
    public void testLoop_issue1592() {
        GraphHopperStorage graph = createStorage(createEncodingManager(true));
        // 0-6
        //  \ \
        //   4-3
        //   |
        //   1o
        GHUtility.setSpeed(60, true, true, carEncoder, graph.edge(0, 6).setDistance(10));
        GHUtility.setSpeed(60, true, true, carEncoder, graph.edge(6, 3).setDistance(10));
        GHUtility.setSpeed(60, true, true, carEncoder, graph.edge(0, 4).setDistance(1));
        GHUtility.setSpeed(60, true, true, carEncoder, graph.edge(4, 1).setDistance(1));
        GHUtility.setSpeed(60, true, true, carEncoder, graph.edge(4, 3).setDistance(1));
        GHUtility.setSpeed(60, true, true, carEncoder, graph.edge(1, 1).setDistance(10));
        setTurnRestriction(graph, 0, 4, 3);

        Path p = calcPath(graph, 0, 3);
        assertEquals(14, p.getDistance(), 1.e-3);
        assertEquals(IntArrayList.from(0, 4, 1, 1, 4, 3), p.calcNodes());
    }

    @Test
    public void testTurnCosts_timeCalculation() {
        // 0 - 1 - 2 - 3 - 4
        GraphHopperStorage graph = createStorage(createEncodingManager(false));
        final int distance = 100;
        final int turnCosts = 2;
        GHUtility.setSpeed(60, true, true, carEncoder, graph.edge(0, 1).setDistance(distance));
        GHUtility.setSpeed(60, true, true, carEncoder, graph.edge(1, 2).setDistance(distance));
        GHUtility.setSpeed(60, true, true, carEncoder, graph.edge(2, 3).setDistance(distance));
        GHUtility.setSpeed(60, true, true, carEncoder, graph.edge(3, 4).setDistance(distance));
        setTurnCost(graph, turnCosts, 1, 2, 3);

        {
            // simple case where turn cost is encountered during forward search
            Path p14 = calcPath(graph, 1, 4);
            assertDistTimeWeight(p14, 3, distance, 6, turnCosts);
            assertEquals(20, p14.getWeight(), 1.e-6);
            assertEquals(20000, p14.getTime());
        }

        {
            // this test is more involved for bidir algos: the turn costs have to be taken into account also at the
            // node where fwd and bwd searches meet
            Path p04 = calcPath(graph, 0, 4);
            assertDistTimeWeight(p04, 4, distance, 6, turnCosts);
            assertEquals(26, p04.getWeight(), 1.e-6);
            assertEquals(26000, p04.getTime());
        }
    }

    private void assertDistTimeWeight(Path path, int numEdges, double distPerEdge, double weightPerEdge, int turnCost) {
        assertEquals("wrong distance", numEdges * distPerEdge, path.getDistance(), 1.e-6);
        assertEquals("wrong weight", numEdges * weightPerEdge + turnCost, path.getWeight(), 1.e-6);
        assertEquals("wrong time", 1000 * (numEdges * weightPerEdge + turnCost), path.getTime(), 1.e-6);
    }

    private void blockNode3(GraphHopperStorage g) {
        // Totally block this node (all 9 turn relations)
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

    @Test
    public void testBlockANode() {
        GraphHopperStorage g = createStorage(createEncodingManager(true));
        initGraph(g, carEncoder);
        blockNode3(g);
        for (int i = 0; i <= 7; i++) {
            if (i == 3) continue;
            for (int j = 0; j <= 7; j++) {
                if (j == 3) continue;
                Path p = calcPath(g, i, j);
                assertTrue(p.isFound()); // We can go from everywhere to everywhere else without using node 3
                for (IntCursor node : p.calcNodes()) {
                    assertNotEquals(p.calcNodes().toString(), 3, node.value);
                }
            }
        }
    }

    @Test
    public void testUTurns() {
        GraphHopperStorage g = createStorage(createEncodingManager(true));
        initGraph(g, carEncoder);

        // force u-turn at node 3 by using finite u-turn costs
        getEdge(g, 3, 6).setDistance(0.1);
        getEdge(g, 3, 2).setDistance(864);
        getEdge(g, 1, 0).setDistance(864);

        setTurnRestriction(g, 7, 6, 5);
        setTurnRestriction(g, 4, 3, 6);
        Path p = createAlgo(g, createWeighting(carEncoder, 50), EDGE_BASED).calcPath(7, 5);

        assertEquals(2 + 2 * 0.1, p.getDistance(), 1.e-6);
        assertEquals(2.2 * 0.06 + 50, p.getWeight(), 1.e-6);
        assertEquals((2.2 * 0.06 + 50) * 1000, p.getTime(), 1.e-6);
        assertEquals(IntArrayList.from(7, 6, 3, 6, 5), p.calcNodes());

        // with default infinite u-turn costs we need to take an expensive detour
        p = calcPath(g, 7, 5);
        assertEquals(1.1 + 864 + 0.5, p.getDistance(), 1.e-6);
        assertEquals(865.6 * 0.06, p.getWeight(), 1.e-6);
        assertEquals(IntArrayList.from(7, 6, 3, 2, 5), p.calcNodes());

        // no more u-turn 6-3-6 -> now we have to take the expensive roads even with finite u-turn costs
        setTurnRestriction(g, 6, 3, 6);
        p = createAlgo(g, createWeighting(carEncoder, 100), EDGE_BASED).calcPath(7, 5);

        assertEquals(1.1 + 864 + 0.5, p.getDistance(), 1.e-6);
        assertEquals(865.6 * 0.06, p.getWeight(), 1.e-6);
        assertEquals(IntArrayList.from(7, 6, 3, 2, 5), p.calcNodes());
    }

    @Test
    public void uTurnCostAtMeetingNode() {
        //           3
        //           |
        // 0 -> 1 -> 2 -> 4 -> 5
        GraphHopperStorage g = createStorage(createEncodingManager(false));
        GHUtility.setSpeed(60, true, false, carEncoder, g.edge(0, 1).setDistance(10));
        GHUtility.setSpeed(60, true, false, carEncoder, g.edge(1, 2).setDistance(10));
        GHUtility.setSpeed(60, true, true, carEncoder, g.edge(2, 3).setDistance(10));
        GHUtility.setSpeed(60, true, false, carEncoder, g.edge(2, 4).setDistance(10));
        GHUtility.setSpeed(60, true, false, carEncoder, g.edge(4, 5).setDistance(10));

        // cannot go straight at node 2
        setTurnRestriction(g, 1, 2, 4);

        // with default/infinite u-turn costs there is no shortest path
        {
            Path path = calcPath(g, 0, 5);
            assertFalse(path.isFound());
        }

        // with finite u-turn costs it is possible, the u-turn costs should be included
        // here we make sure the default u-turn time is also included at the meeting node for bidir algos
        {
            Path path = createAlgo(g, createWeighting(carEncoder, 67), EDGE_BASED).calcPath(0, 5);
            assertEquals(60, path.getDistance(), 1.e-6);
            assertEquals(60 * 0.06 + 67, path.getWeight(), 1.e-6);
            assertEquals((36 + 670) * 100, path.getTime(), 1.e-6);
        }
    }

    @Test
    public void testBasicTurnCosts() {
        GraphHopperStorage g = createStorage(createEncodingManager(false));
        initGraph(g, carEncoder);
        Path p = calcPath(g, 5, 1);

        // no restriction and costs
        assertEquals(IntArrayList.from(5, 2, 3, 1), p.calcNodes());

        // now introduce some turn costs
        getEdge(g, 5, 6).setDistance(2);
        setTurnCost(g, 2, 5, 2, 3);

        p = calcPath(g, 5, 1);
        assertEquals(IntArrayList.from(5, 6, 3, 1), p.calcNodes());
    }

    @Test
    public void testTurnCostsBug_991() {
        final GraphHopperStorage g = createStorage(createEncodingManager(false));
        initGraph(g, carEncoder);

        setTurnCost(g, 2, 5, 2, 3);
        setTurnCost(g, 2, 2, 0, 1);
        setTurnCost(g, 2, 5, 6, 3);
        setTurnCost(g, 1, 6, 7, 4);

        FastestWeighting weighting = new FastestWeighting(carEncoder, new DefaultTurnCostProvider(carEncoder, tcs) {
            @Override
            public double calcTurnWeight(int edgeFrom, int nodeVia, int edgeTo) {
                if (edgeFrom >= 0)
                    assertNotNull("edge " + edgeFrom + " to " + nodeVia + " does not exist", g.getEdgeIteratorState(edgeFrom, nodeVia));
                if (edgeTo >= 0)
                    assertNotNull("edge " + edgeTo + " to " + nodeVia + " does not exist", g.getEdgeIteratorState(edgeTo, nodeVia));
                return super.calcTurnWeight(edgeFrom, nodeVia, edgeTo);
            }
        });
        Path p = createAlgo(g, weighting, EDGE_BASED).calcPath(5, 1);
        assertEquals(IntArrayList.from(5, 6, 7, 4, 3, 1), p.calcNodes());
        assertEquals(5 * 0.06 + 1, p.getWeight(), 1.e-6);
        assertEquals(1300, p.getTime(), .1);
    }

    @Test
    public void testLoopEdge() {
        //   o
        // 3-2-4
        //  \|
        //   0
        final GraphHopperStorage graph = createStorage(createEncodingManager(false));
        GHUtility.setSpeed(60, true, false, carEncoder, graph.edge(3, 2).setDistance(188));
        GHUtility.setSpeed(60, true, true, carEncoder, graph.edge(3, 0).setDistance(182));
        GHUtility.setSpeed(60, true, true, carEncoder, graph.edge(4, 2).setDistance(690));
        GHUtility.setSpeed(60, true, false, carEncoder, graph.edge(2, 2).setDistance(121));
        GHUtility.setSpeed(60, true, true, carEncoder, graph.edge(2, 0).setDistance(132));
        setTurnRestriction(graph, 2, 2, 0);
        setTurnRestriction(graph, 3, 2, 4);

        Path p = calcPath(graph, 3, 4);
        assertEquals(IntArrayList.from(3, 2, 2, 4), p.calcNodes());
        assertEquals(999, p.getDistance(), 1.e-3);
    }

    @Test
    public void testDoubleLoopPTurn() {
        // we cannot go 1-4-5, but taking the loop at 4 is cheaper than taking the one at 3
        //  0-1
        //    |
        // o3-4o
        //    |
        //    5
        final GraphHopperStorage graph = createStorage(createEncodingManager(false));
        GHUtility.setSpeed(60, true, true, carEncoder, graph.edge(0, 1).setDistance(1));
        GHUtility.setSpeed(60, true, true, carEncoder, graph.edge(3, 4).setDistance(2));
        GHUtility.setSpeed(60, true, true, carEncoder, graph.edge(4, 4).setDistance(4));
        GHUtility.setSpeed(60, true, true, carEncoder, graph.edge(3, 3).setDistance(1));
        GHUtility.setSpeed(60, true, true, carEncoder, graph.edge(1, 4).setDistance(5));
        GHUtility.setSpeed(60, true, true, carEncoder, graph.edge(5, 4).setDistance(1));
        setTurnRestriction(graph, 1, 4, 5);

        Path p = calcPath(graph, 0, 5);
        assertEquals(IntArrayList.from(0, 1, 4, 4, 5), p.calcNodes());
        assertEquals(11, p.getDistance(), 1.e-3);
        assertEquals(11 * 0.06, p.getWeight(), 1.e-3);
        assertEquals(11 * 0.06 * 1000, p.getTime(), 1.e-3);
    }

    private void setTurnRestriction(GraphHopperStorage g, int from, int via, int to) {
        setTurnCost(g, Double.POSITIVE_INFINITY, from, via, to);
    }

    private void setTurnCost(GraphHopperStorage g, double cost, int from, int via, int to) {
        g.getTurnCostStorage().set(((EncodedValueLookup) g.getEncodingManager()).getDecimalEncodedValue(TurnCost.key(carEncoder.toString())), getEdge(g, from, via).getEdge(), via, getEdge(g, via, to).getEdge(), cost);
    }
}
