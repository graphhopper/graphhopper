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

import com.carrotsearch.hppc.IntArrayList;
import com.graphhopper.Repeat;
import com.graphhopper.RepeatRule;
import com.graphhopper.routing.*;
import com.graphhopper.routing.util.CarFlagEncoder;
import com.graphhopper.routing.util.DefaultEdgeFilter;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.routing.util.TraversalMode;
import com.graphhopper.routing.weighting.ShortestWeighting;
import com.graphhopper.routing.weighting.TurnWeighting;
import com.graphhopper.routing.weighting.Weighting;
import com.graphhopper.storage.CHGraph;
import com.graphhopper.storage.GraphBuilder;
import com.graphhopper.storage.GraphHopperStorage;
import com.graphhopper.storage.TurnCostExtension;
import com.graphhopper.util.*;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

import static com.graphhopper.routing.ch.CHParameters.*;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

/**
 * Here we test if Contraction Hierarchies work with turn costs, i.e. we first contract the graph and then run
 * routing queries and check if the routing results are correct. We thus test the combination of
 * {@link EdgeBasedNodeContractor} and {@link DijkstraBidirectionEdgeCHNoSOD}. In most cases we either use a predefined
 * or random contraction order, so the hard to test and heuristic automatic search for an efficient contraction order
 * taking place  in {@link PrepareContractionHierarchies} is not covered, but this is ok, because the correctness
 * of CH should not depend on the contraction order.
 *
 * @see EdgeBasedNodeContractor where shortcut creation is tested independent from the routing query
 */
public class CHTurnCostTest {
    private static final Logger LOGGER = LoggerFactory.getLogger(CHTurnCostTest.class);
    private int maxCost;
    private CarFlagEncoder encoder;
    private Weighting weighting;
    private GraphHopperStorage graph;
    private TurnCostExtension turnCostExtension;
    private TurnWeighting turnWeighting;
    private CHGraph chGraph;

    @Rule
    public RepeatRule repeatRule = new RepeatRule();

    @Before
    public void init() {
        // its important to use @Before when using Repeat Rule!
        maxCost = 10;
        encoder = new CarFlagEncoder(5, 5, maxCost);
        EncodingManager encodingManager = EncodingManager.create(encoder);
        weighting = new ShortestWeighting(encoder);
        graph = new GraphBuilder(encodingManager).setCHGraph(weighting).setEdgeBasedCH(true).create();
        turnCostExtension = (TurnCostExtension) graph.getExtension();
        turnWeighting = new TurnWeighting(weighting, turnCostExtension);
        chGraph = graph.getGraph(CHGraph.class);
    }

    @Test
    @Repeat(times = 10)
    public void testFindPath_randomContractionOrder_linear() {
        // 2-1-0-3-4
        graph.edge(2, 1, 2, true);
        graph.edge(1, 0, 3, true);
        graph.edge(0, 3, 1, true);
        graph.edge(3, 4, 3, true);
        graph.freeze();

        addTurnCost(2, 1, 0, 2);
        addTurnCost(0, 3, 4, 4);

        final IntArrayList expectedPath = IntArrayList.from(2, 1, 0, 3, 4);
        final int expectedWeight = 15;

        int from = 2;
        int to = 4;

        checkPathUsingRandomContractionOrder(expectedPath, expectedWeight, from, to);
    }

    @Test
    public void testFindPath_randomContractionOrder_duplicate_edges() {
        //  /\    /<-3
        // 0  1--2
        //  \/    \->4
        graph.edge(0, 1, 5, true);
        graph.edge(0, 1, 6, true);
        graph.edge(1, 2, 2, true);
        graph.edge(3, 2, 3, false);
        graph.edge(2, 4, 3, false);
        addRestriction(3, 2, 4);
        graph.freeze();
        compareCHWithDijkstra(10, Arrays.asList(0, 1, 2, 3, 4));
    }

    @Test
    public void testFindPath_randomContractionOrder_double_duplicate_edges() {
        //  /\ /\   
        // 0  1  2--3
        //  \/ \/
        graph.edge(0, 1, 25.789000, true);
        graph.edge(0, 1, 26.016000, true);
        graph.edge(1, 2, 21.902000, true);
        graph.edge(1, 2, 21.862000, true);
        graph.edge(2, 3, 52.987000, true);
        graph.freeze();
        compareCHWithDijkstra(1000, Arrays.asList(0, 1, 2, 3));
    }

    @Test
    @Repeat(times = 100)
    public void testFindPath_multipleInOutEdges_turnReplacementDifference() {
        //   0   3 - 4   8
        //    \ /     \ /
        // 1 - 5 - 6 - 7 - 9
        //    /         \
        //   2           10
        // When we contract node 6, 'normally' a shortcut would be expected using nodes 3 and 4, but this strongly depends
        // on the turn restrictions of the in/outcoming edges. Basically a shortcut is only needed if 5, 6, 7 is part of
        // the shortest path between an incoming source edge x-5 and an outgoing target edge 7-y. 
        // To cover all or at least as many as possible different cases we randomly apply some restrictions and compare
        // the resulting query with a standard Dijkstra search.
        // If this test fails use the logger output to generate code for further debugging.
        graph.edge(0, 5, 1, false);
        graph.edge(1, 5, 1, false);
        graph.edge(2, 5, 1, false);
        graph.edge(5, 3, 1, false);
        graph.edge(3, 4, 1, false);
        graph.edge(4, 7, 1, false);
        graph.edge(5, 6, 3, false);
        graph.edge(6, 7, 3, false);
        graph.edge(7, 8, 1, false);
        graph.edge(7, 9, 1, false);
        graph.edge(7, 10, 1, false);

        long seed = System.nanoTime();
        Random rnd = new Random(seed);
        LOGGER.info("Seed used to generate turn costs and restrictions: {}", seed);
        addRandomCost(2, 5, 3, rnd);
        addRandomCost(2, 5, 6, rnd);
        addRandomCost(4, 7, 10, rnd);
        addRandomCost(6, 7, 10, rnd);
        addRandomCostOrRestriction(0, 5, 3, rnd);
        addRandomCostOrRestriction(1, 5, 3, rnd);
        addRandomCostOrRestriction(0, 5, 6, rnd);
        addRandomCostOrRestriction(1, 5, 6, rnd);
        addRandomCostOrRestriction(4, 7, 8, rnd);
        addRandomCostOrRestriction(4, 7, 9, rnd);
        addRandomCostOrRestriction(6, 7, 8, rnd);
        addRandomCostOrRestriction(6, 7, 9, rnd);

        RoutingAlgorithmFactory factory = prepareCH(Arrays.asList(6, 0, 1, 2, 8, 9, 10, 5, 3, 4, 7));
        // run queries for all cases (target/source edge possibly restricted/has costs)
        compareCHQueryWithDijkstra(factory, 2, 10);
        compareCHQueryWithDijkstra(factory, 1, 10);
        compareCHQueryWithDijkstra(factory, 2, 9);
        compareCHQueryWithDijkstra(factory, 1, 9);
    }

    @Test
    public void testFindPath_multipleInOutEdges_turnReplacementDifference_bug1() {
        //       3 - 4
        //      /     \
        // 1 - 5 - 6 - 7 - 9
        //    /         \
        //   2           10
        graph.edge(1, 5, 1, false);
        graph.edge(2, 5, 1, false);
        graph.edge(5, 3, 1, false);
        graph.edge(3, 4, 1, false);
        graph.edge(4, 7, 1, false);
        graph.edge(5, 6, 3, false);
        graph.edge(6, 7, 3, false);
        graph.edge(7, 9, 1, false);
        graph.edge(7, 10, 1, false);

        addTurnCost(2, 5, 6, 4);
        addRestriction(1, 5, 6);
        addRestriction(4, 7, 9);

        RoutingAlgorithmFactory factory = prepareCH(Arrays.asList(6, 0, 1, 2, 8, 9, 10, 5, 3, 4, 7));
        compareCHQueryWithDijkstra(factory, 2, 9);
    }

    @Test
    public void testFindPath_duplicateEdge() {
        // 0 -> 1 -> 2 -> 3 -> 4
        //            \->/
        graph.edge(0, 1, 1, false);
        graph.edge(1, 2, 1, false);
        graph.edge(2, 3, 1, false);
        graph.edge(2, 3, 1, false);
        graph.edge(3, 4, 1, false);
        List<Integer> contractionOrder = Arrays.asList(2, 3, 0, 4, 1);
        compareCHWithDijkstra(100, contractionOrder);
    }

    @Test
    @Repeat(times = 10)
    public void testFindPath_randomContractionOrder_simpleLoop() {
        //      2
        //     /|
        //  0-4-3
        //    |
        //    1
        graph.edge(0, 4, 2, false);
        graph.edge(4, 3, 2, true);
        graph.edge(3, 2, 1, true);
        graph.edge(2, 4, 1, true);
        graph.edge(4, 1, 1, false);
        graph.freeze();

        // enforce loop (going counter-clockwise)
        addRestriction(0, 4, 1);
        addTurnCost(4, 2, 3, 4);
        addTurnCost(3, 2, 4, 2);

        final IntArrayList expectedPath = IntArrayList.from(0, 4, 3, 2, 4, 1);
        final int expectedWeight = 9;

        checkPathUsingRandomContractionOrder(expectedPath, expectedWeight, 0, 1);
    }

    @Test
    @Repeat(times = 10)
    public void testFindPath_randomContractionOrder_singleDirectedLoop() {
        //  3 1-2
        //  | | |
        //  7-5-0
        //    |
        //    6-4
        graph.edge(3, 7, 1, false);
        graph.edge(7, 5, 2, false);
        graph.edge(5, 0, 2, false);
        graph.edge(0, 2, 1, false);
        graph.edge(2, 1, 2, false);
        graph.edge(1, 5, 1, false);
        graph.edge(5, 6, 1, false);
        graph.edge(6, 4, 2, false);
        graph.freeze();

        addRestriction(7, 5, 6);
        addTurnCost(0, 2, 1, 2);

        final IntArrayList expectedPath = IntArrayList.from(3, 7, 5, 0, 2, 1, 5, 6, 4);
        final int roadCosts = 12;
        final int turnCosts = 2;

        checkPathUsingRandomContractionOrder(expectedPath, roadCosts + turnCosts, 3, 4);
    }

    @Test
    @Repeat(times = 10)
    public void testFindPath_randomContractionOrder_singleLoop() {
        //  0   4
        //  |  /|
        //  1-2-3
        //    |
        //    5-6
        graph.edge(0, 1, 1, false);
        graph.edge(1, 2, 2, false);
        graph.edge(2, 3, 2, true);
        graph.edge(3, 4, 1, true);
        graph.edge(4, 2, 1, true);
        graph.edge(2, 5, 1, false);
        graph.edge(5, 6, 2, false);
        graph.freeze();

        // enforce loop (going counter-clockwise)
        addRestriction(1, 2, 5);
        addTurnCost(3, 4, 2, 2);
        addTurnCost(2, 4, 3, 4);

        final IntArrayList expectedPath = IntArrayList.from(0, 1, 2, 3, 4, 2, 5, 6);
        final int roadCosts = 10;
        final int turnCosts = 2;

        checkPathUsingRandomContractionOrder(expectedPath, roadCosts + turnCosts, 0, 6);
    }

    @Test
    @Repeat(times = 10)
    public void testFindPath_randomContractionOrder_singleLoopWithNoise() {
        //  0~15~16~17              solid lines: paths contributing to shortest path from 0 to 14
        //  |        {              wiggly lines: extra paths to make it more complicated
        //  1~ 2- 3~ 4
        //  |  |  |  {
        //  6- 7- 8  9
        //  }  |  }  }
        // 11~12-13-14

        graph.edge(0, 1, 1, true);
        graph.edge(1, 6, 1, true);
        graph.edge(6, 7, 2, true);
        graph.edge(7, 8, 2, true);
        graph.edge(8, 3, 1, true);
        graph.edge(3, 2, 2, true);
        graph.edge(2, 7, 1, true);
        graph.edge(7, 12, 1, true);
        graph.edge(12, 13, 2, true);
        graph.edge(13, 14, 2, true);

        // some more edges to make it more complicated -> potentially find more bugs
        graph.edge(1, 2, 8, true);
        graph.edge(6, 11, 3, true);
        graph.edge(11, 12, 50, true);
        graph.edge(8, 13, 1, true);
        graph.edge(0, 15, 1, true);
        graph.edge(15, 16, 2, true);
        graph.edge(16, 17, 3, true);
        graph.edge(17, 4, 2, true);
        graph.edge(3, 4, 2, true);
        graph.edge(4, 9, 1, true);
        graph.edge(9, 14, 2, true);
        graph.freeze();

        // enforce loop (going counter-clockwise)
        addRestriction(6, 7, 12);
        addTurnCost(8, 3, 2, 2);
        addTurnCost(2, 3, 8, 4);

        // make alternative paths not worth it
        addTurnCost(1, 2, 7, 3);
        addTurnCost(7, 8, 13, 8);
        addTurnCost(8, 13, 14, 7);
        addTurnCost(16, 17, 4, 4);
        addTurnCost(4, 9, 14, 3);
        addTurnCost(3, 4, 9, 3);

        final IntArrayList expectedPath = IntArrayList.from(0, 1, 6, 7, 8, 3, 2, 7, 12, 13, 14);
        final int roadCosts = 15;
        final int turnCosts = 2;

        checkPathUsingRandomContractionOrder(expectedPath, roadCosts + turnCosts, 0, 14);
    }

    @Test
    @Repeat(times = 10)
    public void testFindPath_randomContractionOrder_complicatedGraphAndPath() {
        // In this test we try to find a rather complicated shortest path including a double loop and two p-turns
        // with several turn restrictions and turn costs.

        //  0              solid lines: paths contributing to shortest path from 0 to 26
        //  |              wiggly lines: extra paths to make it more complicated
        //  1~ 2- 3<~4- 5
        //   \ |  |  |  |
        //  6->7->8~ 9-10
        //  |  |\    |
        // 11-12 13-14~15~27
        //     {  {  |     }
        // 16-17-18-19-20~28
        //  |  {  {  |  |  }
        // 21-22-23-24 25-26

        // first we add all edges that contribute to the shortest path, verticals: cost=1, horizontals: cost=2
        graph.edge(0, 1, 1, true);
        graph.edge(1, 7, 3, true);
        graph.edge(7, 8, 2, false);
        graph.edge(8, 3, 1, true);
        graph.edge(3, 2, 2, true);
        graph.edge(2, 7, 1, true);
        graph.edge(7, 12, 1, true);
        graph.edge(12, 11, 2, true);
        graph.edge(11, 6, 1, true);
        graph.edge(6, 7, 2, false);
        graph.edge(7, 13, 3, true);
        graph.edge(13, 14, 2, true);
        graph.edge(14, 9, 1, true);
        graph.edge(9, 4, 1, true);
        graph.edge(4, 5, 2, true);
        graph.edge(5, 10, 1, true);
        graph.edge(10, 9, 2, true);
        graph.edge(14, 19, 1, true);
        graph.edge(19, 18, 2, true);
        graph.edge(18, 17, 2, true);
        graph.edge(17, 16, 2, true);
        graph.edge(16, 21, 1, true);
        graph.edge(21, 22, 2, true);
        graph.edge(22, 23, 2, true);
        graph.edge(23, 24, 2, true);
        graph.edge(24, 19, 1, true);
        graph.edge(19, 20, 2, true);
        graph.edge(20, 25, 1, true);
        graph.edge(25, 26, 2, true);

        //some more edges to make it more complicated -> potentially find more bugs
        graph.edge(1, 2, 1, true);
        graph.edge(4, 3, 1, false);
        graph.edge(8, 9, 75, true);
        graph.edge(17, 22, 9, true);
        graph.edge(18, 23, 15, true);
        graph.edge(12, 17, 50, true);
        graph.edge(13, 18, 80, true);
        graph.edge(14, 15, 3, true);
        graph.edge(15, 27, 2, true);
        graph.edge(27, 28, 100, true);
        graph.edge(28, 26, 1, true);
        graph.edge(20, 28, 1, true);
        graph.freeze();

        // enforce figure of eight curve at node 7
        addRestriction(1, 7, 13);
        addTurnCost(1, 7, 12, 7);
        addTurnCost(2, 7, 13, 7);

        // enforce p-loop at the top right (going counter-clockwise)
        addRestriction(13, 14, 19);
        addTurnCost(4, 5, 10, 3);
        addTurnCost(10, 5, 4, 2);

        // enforce big p-loop at bottom left (going clockwise)
        addRestriction(14, 19, 20);
        addTurnCost(17, 16, 21, 3);

        // make some alternative paths not worth it
        addTurnCost(1, 2, 7, 8);
        addTurnCost(20, 28, 26, 3);

        // add some more turn costs on the shortest path
        addTurnCost(7, 13, 14, 2);

        // expected costs of the shortest path
        final IntArrayList expectedPath = IntArrayList.from(
                0, 1, 7, 8, 3, 2, 7, 12, 11, 6, 7, 13, 14, 9, 10, 5, 4, 9, 14, 19, 24, 23, 22, 21, 16, 17, 18, 19, 20, 25, 26);
        final int roadCosts = 49;
        final int turnCosts = 4;

        checkPathUsingRandomContractionOrder(expectedPath, roadCosts + turnCosts, 0, 26);
    }

    @Test
    public void testFindPath_pTurn_uTurnAtContractedNode() {
        // when contracting node 4 we need a loop shortcut at node 6
        //           2- 3
        //           |  |
        //           4- 0
        //           |
        //     5 ->  6 -> 1
        graph.edge(5, 6, 1, false);
        graph.edge(6, 1, 1, false);
        graph.edge(6, 4, 1, true);
        graph.edge(4, 0, 1, false);
        graph.edge(0, 3, 1, false);
        graph.edge(3, 2, 1, false);
        graph.edge(2, 4, 1, false);
        graph.freeze();
        addRestriction(5, 6, 1);

        final IntArrayList expectedPath = IntArrayList.from(5, 6, 4, 0, 3, 2, 4, 6, 1);
        checkPath(expectedPath, 8, 5, 1, Arrays.asList(0, 1, 2, 3, 4, 5, 6));
    }


    @Test
    public void testFindPath_pTurn_uTurnAtContractedNode_twoShortcutsInAndOut() {
        //           2- 3
        //           |  |
        //           4- 0
        //           |
        //           1
        //           |
        //     5 ->  6 -> 7
        graph.edge(5, 6, 1, false);
        graph.edge(6, 7, 1, false);
        graph.edge(6, 1, 1, true);
        graph.edge(1, 4, 1, true);
        graph.edge(4, 0, 1, false);
        graph.edge(0, 3, 1, false);
        graph.edge(3, 2, 1, false);
        graph.edge(2, 4, 1, false);
        graph.freeze();
        addRestriction(5, 6, 7);

        final IntArrayList expectedPath = IntArrayList.from(5, 6, 1, 4, 0, 3, 2, 4, 1, 6, 7);
        checkPath(expectedPath, 10, 5, 7, Arrays.asList(0, 1, 2, 3, 4, 5, 6, 7));
    }

    @Test
    @Repeat(times = 10)
    public void testFindPath_highlyConnectedGraph_compareWithDijkstra() {
        // In this test we use a random contraction order and run many random routing queries. The results are checked
        // by comparing them to the results of a standard dijkstra search.
        // If a test fails use the debug output to generate the graph creation code for further debugging!

        // 0 - 1 - 2  example for size=3
        // | x | x |
        // 3 - 4 - 5
        // | x | x |
        // 6 - 7 - 8
        // for large sizes contraction takes very long because there are so many edges
        final int size = 4;
        final int maxDist = 4;
        final int numQueries = 1000;
        long seed = System.nanoTime();
        LOGGER.info("Seed used to generate graph: {}", seed);
        final Random rnd = new Random(seed);

        int edgeCounter = 0;
        // horizontal edges
        for (int i = 0; i < size; ++i) {
            for (int j = 0; j < size - 1; ++j) {
                final int from = i * size + j;
                final int to = from + 1;
                final double dist = nextDist(maxDist, rnd);
                graph.edge(from, to, dist, true);
                LOGGER.trace("final EdgeIteratorState edge{} = graph.edge({},{},{},true);", edgeCounter++, from, to, dist);
            }
        }
        // vertical edges
        for (int i = 0; i < size - 1; ++i) {
            for (int j = 0; j < size; ++j) {
                final int from = i * size + j;
                final int to = from + size;
                double dist = nextDist(maxDist, rnd);
                graph.edge(from, to, dist, true);
                LOGGER.trace("final EdgeIteratorState edge{} = graph.edge({},{},{},true);", edgeCounter++, from, to, dist);
            }
        }
        // diagonal edges
        for (int i = 0; i < size - 1; ++i) {
            for (int j = 0; j < size; ++j) {
                final int from = i * size + j;
                if (j < size - 1) {
                    final double dist = nextDist(maxDist, rnd);
                    final int to = from + size + 1;
                    graph.edge(from, to, dist, true);
                    LOGGER.trace("final EdgeIteratorState edge{} = graph.edge({},{},{},true);", edgeCounter++, from, to, dist);
                }
                if (j > 0) {
                    final double dist = nextDist(maxDist, rnd);
                    final int to = from + size - 1;
                    graph.edge(from, to, dist, true);
                    LOGGER.trace("final EdgeIteratorState edge{} = graph.edge({},{},{},true);", edgeCounter++, from, to, dist);
                }
            }
        }
        graph.freeze();
        EdgeExplorer inExplorer = graph.createEdgeExplorer(DefaultEdgeFilter.inEdges(encoder));
        EdgeExplorer outExplorer = graph.createEdgeExplorer(DefaultEdgeFilter.outEdges(encoder));

        // add turn costs or restrictions
        for (int node = 0; node < size * size; ++node) {
            EdgeIterator inIter = inExplorer.setBaseNode(node);
            while (inIter.next()) {
                EdgeIterator outIter = outExplorer.setBaseNode(node);
                while (outIter.next()) {
                    // do not modify u-turn costs
                    if (inIter.getEdge() == outIter.getEdge()) {
                        continue;
                    }
                    int cost = nextCost(rnd);
                    addCostOrRestriction(inIter, outIter, node, cost);
                }
            }
        }

        List<Integer> contractionOrder = getRandomIntegerSequence(chGraph.getNodes(), rnd);
        compareCHWithDijkstra(numQueries, contractionOrder);
    }

    @Test
    public void testFindPath_bug() {
        graph.edge(1, 2, 18.364000, false);
        graph.edge(1, 4, 29.814000, true);
        graph.edge(0, 2, 14.554000, true);
        graph.edge(1, 4, 29.819000, true);
        graph.edge(1, 3, 29.271000, true);
        addRestriction(3, 1, 2);
        graph.freeze();

        List<Integer> contractionOrder = Arrays.asList(1, 0, 3, 2, 4);
        compareCHWithDijkstra(100, contractionOrder);
    }

    @Test
    public void testFindPath_bug2() {
        graph.edge(0, 3, 24.001000, true);
        graph.edge(0, 1, 6.087000, true);
        graph.edge(0, 1, 6.067000, true);
        graph.edge(2, 3, 46.631000, true);
        graph.edge(2, 4, 46.184000, true);
        graph.freeze();

        List<Integer> contractionOrder = Arrays.asList(1, 0, 3, 2, 4);
        compareCHWithDijkstra(1000, contractionOrder);
    }

    @Test
    public void testFindPath_loopsMustAlwaysBeAccepted() {
        //     ---
        //     \ /
        // 0 -- 1 -- 2 -- 3
        EdgeIteratorState edge0 = graph.edge(0, 1, 1, true);
        EdgeIteratorState edge1 = graph.edge(1, 1, 1, false);
        EdgeIteratorState edge2 = graph.edge(1, 2, 1, true);
        EdgeIteratorState edge3 = graph.edge(2, 3, 1, false);
        addTurnCost(edge0, edge1, 1, 1);
        addRestriction(edge0, edge2, 1);
        graph.freeze();
        final IntArrayList expectedPath = IntArrayList.from(0, 1, 1, 2, 3);
        checkPath(expectedPath, 5, 0, 3, Arrays.asList(0, 2, 1, 3));
    }

    @Test
    public void testFindPath_compareWithDijkstra_zeroWeightLoops_random() {
        graph.edge(5, 3, 21.329000, false);
        graph.edge(4, 5, 29.126000, false);
        graph.edge(1, 0, 38.865000, false);
        graph.edge(1, 4, 80.005000, false);
        graph.edge(3, 1, 91.023000, false);
        // add loops with zero weight ...
        graph.edge(1, 1, 0.000000, false);
        graph.edge(1, 1, 0.000000, false);
        graph.freeze();
        automaticCompareCHWithDijkstra(100);
    }

    @Test
    public void testFindPath_compareWithDijkstra_zeroWeightLoops() {
        //                  /|
        // 0 -> 1 -> 2 -> 3 --
        //                | \|
        //                4
        graph.edge(0, 1, 1, false);
        graph.edge(1, 2, 1, false);
        graph.edge(2, 3, 1, false);
        graph.edge(3, 3, 0, false);
        graph.edge(3, 3, 0, false);
        graph.edge(3, 4, 1, false);
        graph.freeze();
        IntArrayList expectedPath = IntArrayList.from(0, 1, 2, 3, 4);
        List<Integer> contractionOrder = Arrays.asList(2, 0, 4, 1, 3);
        checkPath(expectedPath, 4, 0, 4, contractionOrder);
    }

    @Test
    public void testFindPath_compareWithDijkstra_zeroWeightLoops_withTurnRestriction() {
        //                  /|
        // 0 -> 1 -> 2 -> 3 --
        //                | \|
        //                4
        graph.edge(0, 1, 1, false);
        graph.edge(1, 2, 1, false);
        EdgeIteratorState edge2 = graph.edge(2, 3, 1, false);
        EdgeIteratorState edge3 = graph.edge(3, 3, 0, false);
        EdgeIteratorState edge4 = graph.edge(3, 3, 0, false);
        EdgeIteratorState edge5 = graph.edge(3, 4, 1, false);
        addTurnCost(edge2, edge3, 3, 5);
        addTurnCost(edge2, edge4, 3, 4);
        addTurnCost(edge3, edge4, 3, 2);
        addRestriction(edge2, edge5, 3);
        graph.freeze();
        IntArrayList expectedPath = IntArrayList.from(0, 1, 2, 3, 3, 4);
        List<Integer> contractionOrder = Arrays.asList(2, 0, 4, 1, 3);
        checkPath(expectedPath, 8, 0, 4, contractionOrder);
    }

    /**
     * This test runs on a random graph with random turn costs and a predefined (but random) contraction order.
     * It often produces exotic conditions that are hard to anticipate beforehand.
     * when it fails use {@link GHUtility#printGraphForUnitTest} to extract the graph and reproduce the error.
     */
    @Repeat(times = 10)
    @Test
    public void testFindPath_random_compareWithDijkstra() {
        long seed = System.nanoTime();
        LOGGER.info("Seed used to generate graph: {}", seed);
        final Random rnd = new Random(seed);
        // for larger graphs preparation takes much longer the higher the degree is!
        GHUtility.buildRandomGraph(graph, seed, 20, 3.0, true, true, 0.9);
        GHUtility.addRandomTurnCosts(graph, seed, encoder, maxCost, turnCostExtension);
        graph.freeze();
        List<Integer> contractionOrder = getRandomIntegerSequence(chGraph.getNodes(), rnd);
        compareCHWithDijkstra(100, contractionOrder);
    }

    /**
     * same as {@link #testFindPath_random_compareWithDijkstra()}, but using automatic node priority calculation
     */
    @Repeat(times = 10)
    @Test
    public void testFindPath_heuristic_compareWithDijkstra() {
        long seed = System.nanoTime();
        LOGGER.info("Seed used to generate graph: {}", seed);
        GHUtility.buildRandomGraph(graph, seed, 20, 3.0, true, true, 0.9);
        GHUtility.addRandomTurnCosts(graph, seed, encoder, maxCost, turnCostExtension);
        graph.freeze();
        automaticCompareCHWithDijkstra(100);
    }

    private int nextCost(Random rnd) {
        // choose bound above max cost such that turn restrictions are likely
        return rnd.nextInt(3 * maxCost);
    }

    private double nextDist(int maxDist, Random rnd) {
        return rnd.nextDouble() * maxDist;
    }

    private void checkPathUsingRandomContractionOrder(IntArrayList expectedPath, int expectedWeight, int from, int to) {
        List<Integer> contractionOrder = getRandomIntegerSequence(chGraph.getNodes());
        checkPath(expectedPath, expectedWeight, from, to, contractionOrder);
    }

    private void checkPath(IntArrayList expectedPath, int expectedWeight, int from, int to, List<Integer> contractionOrder) {
        checkPathUsingDijkstra(expectedPath, expectedWeight, from, to);
        checkPathUsingCH(expectedPath, expectedWeight, from, to, contractionOrder);
    }

    private void checkPathUsingDijkstra(IntArrayList expectedPath, int expectedWeight, int from, int to) {
        Path dijkstraPath = findPathUsingDijkstra(from, to);
        assertEquals("Normal Dijkstra did not find expected path.", expectedPath, dijkstraPath.calcNodes());
        assertEquals("Normal Dijkstra did not calculate expected weight.", expectedWeight, dijkstraPath.getWeight(), 1.e-6);
    }

    private void checkPathUsingCH(IntArrayList expectedPath, int expectedWeight, int from, int to, List<Integer> contractionOrder) {
        Path chPath = findPathUsingCH(from, to, contractionOrder);
        assertEquals("Contraction Hierarchies did not find expected path. contraction order=" + contractionOrder, expectedPath, chPath.calcNodes());
        assertEquals("Contraction Hierarchies did not calculate expected weight.", expectedWeight, chPath.getWeight(), 1.e-6);
    }

    private Path findPathUsingDijkstra(int from, int to) {
        Dijkstra dijkstra = new Dijkstra(graph, turnWeighting, TraversalMode.EDGE_BASED_2DIR);
        return dijkstra.calcPath(from, to);
    }

    private Path findPathUsingCH(int from, int to, List<Integer> contractionOrder) {
        RoutingAlgorithmFactory routingAlgorithmFactory = prepareCH(contractionOrder);
        RoutingAlgorithm chAlgo = routingAlgorithmFactory.createAlgo(chGraph, AlgorithmOptions.start().build());
        return chAlgo.calcPath(from, to);
    }

    private RoutingAlgorithmFactory prepareCH(final List<Integer> contractionOrder) {
        LOGGER.debug("Calculating CH with contraction order {}", contractionOrder);
        graph.freeze();
        NodeOrderingProvider nodeOrderingProvider = new NodeOrderingProvider() {
            @Override
            public int getNodeIdForLevel(int level) {
                return contractionOrder.get(level);
            }

            @Override
            public int getNumNodes() {
                return contractionOrder.size();
            }
        };
        PrepareContractionHierarchies ch = new PrepareContractionHierarchies(chGraph, weighting, TraversalMode.EDGE_BASED_2DIR)
                .useFixedNodeOrdering(nodeOrderingProvider);
        ch.doWork();
        return ch;
    }

    private RoutingAlgorithmFactory automaticPrepareCH() {
        PMap pMap = new PMap();
        pMap.put(PERIODIC_UPDATES, 20);
        pMap.put(LAST_LAZY_NODES_UPDATES, 100);
        pMap.put(NEIGHBOR_UPDATES, 4);
        pMap.put(LOG_MESSAGES, 10);
        PrepareContractionHierarchies ch = new PrepareContractionHierarchies(
                chGraph, weighting, TraversalMode.EDGE_BASED_2DIR);
        ch.setParams(pMap);
        ch.doWork();
        return ch;
    }

    private void automaticCompareCHWithDijkstra(int numQueries) {
        long seed = System.nanoTime();
        LOGGER.info("Seed used to create random routing queries: {}", seed);
        final Random rnd = new Random(seed);
        RoutingAlgorithmFactory factory = automaticPrepareCH();
        for (int i = 0; i < numQueries; ++i) {
            compareCHQueryWithDijkstra(factory, rnd.nextInt(graph.getNodes()), rnd.nextInt(graph.getNodes()));
        }
    }

    private void compareCHWithDijkstra(int numQueries, List<Integer> contractionOrder) {
        long seed = System.nanoTime();
        LOGGER.info("Seed used to create random routing queries: {}", seed);
        final Random rnd = new Random(seed);
        RoutingAlgorithmFactory factory = prepareCH(contractionOrder);
        for (int i = 0; i < numQueries; ++i) {
            compareCHQueryWithDijkstra(factory, rnd.nextInt(graph.getNodes()), rnd.nextInt(graph.getNodes()));
        }
    }

    private void compareCHQueryWithDijkstra(RoutingAlgorithmFactory factory, int from, int to) {
        Path dijkstraPath = findPathUsingDijkstra(from, to);
        RoutingAlgorithm chAlgo = factory.createAlgo(chGraph, AlgorithmOptions.start().build());
        Path chPath = chAlgo.calcPath(from, to);
        // todo: for increased precision some tests fail. this is because the weight is truncated, not rounded
        // when storing shortcut edges. 
        boolean algosDisagree = Math.abs(dijkstraPath.getWeight() - chPath.getWeight()) > 1.e-2;
        if (algosDisagree) {
            System.out.println("Graph that produced error:");
            GHUtility.printGraphForUnitTest(graph, encoder);
            fail("Dijkstra and CH did not find equal shortest paths for route from " + from + " to " + to + "\n" +
                    " dijkstra: weight: " + dijkstraPath.getWeight() + ", nodes: " + dijkstraPath.calcNodes() + "\n" +
                    "       ch: weight: " + chPath.getWeight() + ", nodes: " + chPath.calcNodes());
        }
    }

    private List<Integer> getRandomIntegerSequence(int nodes) {
        return getRandomIntegerSequence(nodes, new Random());
    }

    private List<Integer> getRandomIntegerSequence(int nodes, Random rnd) {
        List<Integer> contractionOrder = new ArrayList<>(nodes);
        for (int i = 0; i < nodes; ++i) {
            contractionOrder.add(i);
        }
        Collections.shuffle(contractionOrder, rnd);
        return contractionOrder;
    }

    private void addRandomCostOrRestriction(int from, int via, int to, Random rnd) {
        final double chance = 0.7;
        if (rnd.nextDouble() < chance) {
            addRestriction(from, via, to);
            LOGGER.trace("addRestriction({}, {}, {});", from, via, to);
        } else {
            addRandomCost(from, via, to, rnd);
        }
    }

    private void addRandomCost(int from, int via, int to, Random rnd) {
        int cost = (int) (rnd.nextDouble() * maxCost / 2);
        addTurnCost(from, via, to, cost);
        LOGGER.trace("addTurnCost({}, {}, {}, {});", from, via, to, cost);
    }

    private void addRestriction(int from, int via, int to) {
        addRestriction(getEdge(from, via), getEdge(via, to), via);
    }

    private void addRestriction(EdgeIteratorState inEdge, EdgeIteratorState outEdge, int viaNode) {
        turnCostExtension.addTurnInfo(inEdge.getEdge(), viaNode, outEdge.getEdge(), encoder.getTurnFlags(true, 0));
    }

    private void addTurnCost(int from, int via, int to, int cost) {
        addTurnCost(getEdge(from, via), getEdge(via, to), via, cost);
    }

    private void addTurnCost(EdgeIteratorState inEdge, EdgeIteratorState outEdge, int viaNode, double costs) {
        turnCostExtension.addTurnInfo(inEdge.getEdge(), viaNode, outEdge.getEdge(), encoder.getTurnFlags(false, costs));
    }

    private void addCostOrRestriction(EdgeIteratorState inEdge, EdgeIteratorState outEdge, int viaNode, int cost) {
        if (cost >= maxCost) {
            addRestriction(inEdge, outEdge, viaNode);
            LOGGER.trace("addRestriction(edge{}, edge{}, {});", inEdge.getEdge(), outEdge.getEdge(), viaNode);
        } else {
            addTurnCost(inEdge, outEdge, viaNode, cost);
            LOGGER.trace("addTurnCost(edge{}, edge{}, {}, {});", inEdge.getEdge(), outEdge.getEdge(), viaNode, cost);
        }
    }

    private EdgeIteratorState getEdge(int from, int to) {
        return GHUtility.getEdge(graph, from, to);
    }
}
