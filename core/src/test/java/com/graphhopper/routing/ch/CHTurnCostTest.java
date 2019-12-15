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
import com.graphhopper.routing.querygraph.QueryGraph;
import com.graphhopper.routing.util.*;
import com.graphhopper.routing.weighting.ShortestWeighting;
import com.graphhopper.routing.weighting.TurnWeighting;
import com.graphhopper.routing.weighting.Weighting;
import com.graphhopper.storage.*;
import com.graphhopper.storage.index.LocationIndexTree;
import com.graphhopper.storage.index.QueryResult;
import com.graphhopper.util.*;
import com.graphhopper.util.shapes.GHPoint;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

import static com.graphhopper.routing.ch.CHParameters.*;
import static com.graphhopper.routing.weighting.TurnWeighting.INFINITE_U_TURN_COSTS;
import static com.graphhopper.util.GHUtility.updateDistancesFor;
import static org.junit.Assert.*;

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
    private EncodingManager encodingManager;
    private Weighting weighting;
    private GraphHopperStorage graph;
    private TurnCostStorage turnCostStorage;
    private List<CHProfile> chProfiles;
    private CHProfile chProfile;
    private CHGraph chGraph;
    private boolean checkStrict;

    @Rule
    public RepeatRule repeatRule = new RepeatRule();

    @Before
    public void init() {
        // its important to use @Before when using Repeat Rule!
        maxCost = 10;
        encoder = new CarFlagEncoder(5, 5, maxCost);
        encodingManager = EncodingManager.create(encoder);
        weighting = new ShortestWeighting(encoder);
        chProfiles = createCHProfiles();
        graph = new GraphBuilder(encodingManager).setCHProfiles(chProfiles).create();
        // the default CH profile with infinite u-turn costs, can be reset in tests that should run with finite u-turn
        // costs
        chProfile = CHProfile.edgeBased(weighting, INFINITE_U_TURN_COSTS);
        turnCostStorage = graph.getTurnCostStorage();
        checkStrict = true;
    }

    /**
     * Creates a list of distinct CHProfiles with different u-turn costs that can be used by the tests.
     * There is always a profile with infinite u-turn costs and one with u-turn-costs = 50.
     */
    private List<CHProfile> createCHProfiles() {
        Set<CHProfile> profileSet = new HashSet<>(25);
        // the first one is always the one with infinite u-turn costs
        profileSet.add(CHProfile.edgeBased(weighting, INFINITE_U_TURN_COSTS));
        // this one we also always add
        profileSet.add(CHProfile.edgeBased(weighting, 50));
        // add more (distinct) profiles
        long seed = System.nanoTime();
        Random rnd = new Random(seed);
        while (profileSet.size() < 5) {
            profileSet.add(CHProfile.edgeBased(weighting, 10 + rnd.nextInt(90)));
        }
        return new ArrayList<>(profileSet);
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
        setTurnCost(2, 1, 0, 2);
        setTurnCost(0, 3, 4, 4);
        checkPathUsingRandomContractionOrder(IntArrayList.from(2, 1, 0, 3, 4), 9, 6, 2, 4);
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
        setRestriction(3, 2, 4);
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
        setRandomCost(2, 5, 3, rnd);
        setRandomCost(2, 5, 6, rnd);
        setRandomCost(4, 7, 10, rnd);
        setRandomCost(6, 7, 10, rnd);
        setRandomCostOrRestriction(0, 5, 3, rnd);
        setRandomCostOrRestriction(1, 5, 3, rnd);
        setRandomCostOrRestriction(0, 5, 6, rnd);
        setRandomCostOrRestriction(1, 5, 6, rnd);
        setRandomCostOrRestriction(4, 7, 8, rnd);
        setRandomCostOrRestriction(4, 7, 9, rnd);
        setRandomCostOrRestriction(6, 7, 8, rnd);
        setRandomCostOrRestriction(6, 7, 9, rnd);

        RoutingAlgorithmFactory factory = prepareCH(Arrays.asList(6, 0, 1, 2, 8, 9, 10, 5, 3, 4, 7));
        // run queries for all cases (target/source edge possibly restricted/has costs)
        checkStrict = false;
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

        setTurnCost(2, 5, 6, 4);
        setRestriction(1, 5, 6);
        setRestriction(4, 7, 9);

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
        setRestriction(0, 4, 1);
        setTurnCost(4, 2, 3, 4);
        setTurnCost(3, 2, 4, 2);

        checkPathUsingRandomContractionOrder(IntArrayList.from(0, 4, 3, 2, 4, 1), 7, 2, 0, 1);
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

        setRestriction(7, 5, 6);
        setTurnCost(0, 2, 1, 2);

        final IntArrayList expectedPath = IntArrayList.from(3, 7, 5, 0, 2, 1, 5, 6, 4);
        final int roadCosts = 12;
        final int turnCosts = 2;

        checkPathUsingRandomContractionOrder(expectedPath, roadCosts, turnCosts, 3, 4);
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
        setRestriction(1, 2, 5);
        setTurnCost(3, 4, 2, 2);
        setTurnCost(2, 4, 3, 4);

        final IntArrayList expectedPath = IntArrayList.from(0, 1, 2, 3, 4, 2, 5, 6);
        final int roadCosts = 10;
        final int turnCosts = 2;

        checkPathUsingRandomContractionOrder(expectedPath, roadCosts, turnCosts, 0, 6);
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
        setRestriction(6, 7, 12);
        setTurnCost(8, 3, 2, 2);
        setTurnCost(2, 3, 8, 4);

        // make alternative paths not worth it
        setTurnCost(1, 2, 7, 3);
        setTurnCost(7, 8, 13, 8);
        setTurnCost(8, 13, 14, 7);
        setTurnCost(16, 17, 4, 4);
        setTurnCost(4, 9, 14, 3);
        setTurnCost(3, 4, 9, 3);

        final IntArrayList expectedPath = IntArrayList.from(0, 1, 6, 7, 8, 3, 2, 7, 12, 13, 14);
        final int roadCosts = 15;
        final int turnCosts = 2;

        checkPathUsingRandomContractionOrder(expectedPath, roadCosts, turnCosts, 0, 14);
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
        setRestriction(1, 7, 13);
        setTurnCost(1, 7, 12, 7);
        setTurnCost(2, 7, 13, 7);

        // enforce p-loop at the top right (going counter-clockwise)
        setRestriction(13, 14, 19);
        setTurnCost(4, 5, 10, 3);
        setTurnCost(10, 5, 4, 2);

        // enforce big p-loop at bottom left (going clockwise)
        setRestriction(14, 19, 20);
        setTurnCost(17, 16, 21, 3);

        // make some alternative paths not worth it
        setTurnCost(1, 2, 7, 8);
        setTurnCost(20, 28, 26, 3);

        // add some more turn costs on the shortest path
        setTurnCost(7, 13, 14, 2);

        // expected costs of the shortest path
        final IntArrayList expectedPath = IntArrayList.from(
                0, 1, 7, 8, 3, 2, 7, 12, 11, 6, 7, 13, 14, 9, 10, 5, 4, 9, 14, 19, 24, 23, 22, 21, 16, 17, 18, 19, 20, 25, 26);
        final int roadCosts = 49;
        final int turnCosts = 4;

        checkPathUsingRandomContractionOrder(expectedPath, roadCosts, turnCosts, 0, 26);
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
        setRestriction(5, 6, 1);

        final IntArrayList expectedPath = IntArrayList.from(5, 6, 4, 0, 3, 2, 4, 6, 1);
        checkPath(expectedPath, 8, 0, 5, 1, Arrays.asList(0, 1, 2, 3, 4, 5, 6));
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
        setRestriction(5, 6, 7);

        final IntArrayList expectedPath = IntArrayList.from(5, 6, 1, 4, 0, 3, 2, 4, 1, 6, 7);
        checkPath(expectedPath, 10, 0, 5, 7, Arrays.asList(0, 1, 2, 3, 4, 5, 6, 7));
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
                    setCostOrRestriction(inIter, outIter, node, cost);
                }
            }
        }

        List<Integer> contractionOrder = getRandomIntegerSequence(graph.getNodes(), rnd);
        checkStrict = false;
        compareCHWithDijkstra(numQueries, contractionOrder);
    }

    @Test
    public void testFindPath_bug() {
        graph.edge(1, 2, 18.364000, false);
        graph.edge(1, 4, 29.814000, true);
        graph.edge(0, 2, 14.554000, true);
        graph.edge(1, 4, 29.819000, true);
        graph.edge(1, 3, 29.271000, true);
        setRestriction(3, 1, 2);
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
    public void testFindPath_loop() {
        //             3
        //            / \
        //           1   2
        //            \ /
        // 0 - 7 - 8 - 4 - 6 - 5
        graph.edge(0, 7, 1, false);
        graph.edge(7, 8, 1, false);
        graph.edge(8, 4, 1, false);
        graph.edge(4, 1, 1, false);
        graph.edge(1, 3, 1, false);
        graph.edge(3, 2, 1, false);
        graph.edge(2, 4, 1, false);
        graph.edge(4, 6, 1, false);
        graph.edge(6, 5, 1, false);
        setRestriction(8, 4, 6);
        graph.freeze();

        RoutingAlgorithmFactory factory = prepareCH(Arrays.asList(0, 1, 2, 3, 4, 5, 6, 7, 8));
        compareCHQueryWithDijkstra(factory, 0, 5);
    }

    @Test
    public void testFindPath_finiteUTurnCost() {
        // turning to 1 at node 3 when coming from 0 is forbidden, but taking the full loop 3-4-2-3 is very
        // expensive, so the best solution is to go straight to 4 and take a u-turn there
        //   1
        //   |
        // 0-3-4
        //   |/
        //   2
        graph.edge(0, 3, 100, false);
        graph.edge(3, 4, 100, true);
        graph.edge(4, 2, 500, false);
        graph.edge(2, 3, 200, false);
        graph.edge(3, 1, 100, false);
        setRestriction(0, 3, 1);
        graph.freeze();
        chProfile = CHProfile.edgeBased(weighting, 50);
        RoutingAlgorithmFactory pch = prepareCH(Arrays.asList(4, 0, 2, 3, 1));
        Path path = pch.createAlgo(chGraph, AlgorithmOptions.start().build()).calcPath(0, 1);
        assertEquals(IntArrayList.from(0, 3, 4, 3, 1), path.calcNodes());
        compareCHQueryWithDijkstra(pch, 0, 1);
    }

    @Test
    public void testFindPath_calcTurnCostTime() {
        // here there will be a shortcut from 1 to 4 and when the path is unpacked it is important that
        // the turn costs are included at node 1 even though the unpacked original edge 1-0 might be in the
        // reverted state
        // 2-1--3
        //   |  |
        //   0->4
        EdgeIteratorState edge0 = graph.edge(1, 2, 1, true);
        EdgeIteratorState edge1 = graph.edge(0, 4, 1, false);
        EdgeIteratorState edge2 = graph.edge(4, 3, 1, true);
        EdgeIteratorState edge3 = graph.edge(1, 3, 1, true);
        EdgeIteratorState edge4 = graph.edge(1, 0, 1, true);
        setTurnCost(edge0, edge4, 1, 8);
        setRestriction(edge0, edge3, 1);
        graph.freeze();
        checkPath(IntArrayList.from(2, 1, 0, 4), 3, 8, 2, 4, Arrays.asList(2, 0, 1, 3, 4));
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
        setTurnCost(edge0, edge1, 1, 1);
        setRestriction(edge0, edge2, 1);
        graph.freeze();
        final IntArrayList expectedPath = IntArrayList.from(0, 1, 1, 2, 3);
        checkPath(expectedPath, 4, 1, 0, 3, Arrays.asList(0, 2, 1, 3));
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
        checkPath(expectedPath, 4, 0, 0, 4, contractionOrder);
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
        setTurnCost(edge2, edge3, 3, 5);
        setTurnCost(edge2, edge4, 3, 4);
        setTurnCost(edge3, edge4, 3, 2);
        setRestriction(edge2, edge5, 3);
        graph.freeze();
        IntArrayList expectedPath = IntArrayList.from(0, 1, 2, 3, 3, 4);
        List<Integer> contractionOrder = Arrays.asList(2, 0, 4, 1, 3);
        checkPath(expectedPath, 4, 4, 0, 4, contractionOrder);
    }

    @Test
    public void testFindPath_oneWayLoop() {
        //     o
        // 0-1-2-3-4
        graph.edge(0, 1, 1, false);
        graph.edge(1, 2, 1, false);
        graph.edge(2, 2, 1, false);
        graph.edge(2, 3, 1, false);
        graph.edge(3, 4, 1, false);
        setRestriction(1, 2, 3);
        graph.freeze();
        RoutingAlgorithmFactory pch = automaticPrepareCH();
        compareCHQueryWithDijkstra(pch, 0, 3);
        compareCHQueryWithDijkstra(pch, 1, 4);
        automaticCompareCHWithDijkstra(100);
    }

    @Test
    public void testFindPath_loopEdge() {
        // 1-0
        // | |
        // 4-2o
        graph.edge(1, 0, 802.964000, false);
        graph.edge(1, 4, 615.195000, true);
        graph.edge(2, 2, 181.788000, true);
        graph.edge(0, 2, 191.996000, true);
        graph.edge(2, 4, 527.821000, false);
        setRestriction(0, 2, 4);
        setTurnCost(0, 2, 2, 3);
        setTurnCost(2, 2, 4, 4);
        graph.freeze();
        RoutingAlgorithmFactory pch = automaticPrepareCH();
        compareCHQueryWithDijkstra(pch, 0, 4);
    }

    @Test
    public void test_issue1593_full() {
        //      6   5
        //   1<-x-4-x-3
        //  ||    |
        //  |x7   x8
        //  ||   /
        //   2---
        NodeAccess na = graph.getNodeAccess();
        na.setNode(0, 49.407117, 9.701306);
        na.setNode(1, 49.406914, 9.703393);
        na.setNode(2, 49.404004, 9.709110);
        na.setNode(3, 49.400160, 9.708787);
        na.setNode(4, 49.400883, 9.706347);
        EdgeIteratorState edge0 = graph.edge(4, 3, 194.063000, true);
        EdgeIteratorState edge1 = graph.edge(1, 2, 525.106000, true);
        EdgeIteratorState edge2 = graph.edge(1, 2, 525.106000, true);
        EdgeIteratorState edge3 = graph.edge(4, 1, 703.778000, false);
        EdgeIteratorState edge4 = graph.edge(2, 4, 400.509000, true);
        // cannot go 4-2-1 and 1-2-4 (at least when using edge1, there is still edge2!)
        setRestriction(edge4, edge1, 2);
        setRestriction(edge1, edge4, 2);
        // cannot go 3-4-1
        setRestriction(edge0, edge3, 4);
        graph.freeze();
        LocationIndexTree index = new LocationIndexTree(graph, new RAMDirectory());
        index.prepareIndex();
        List<GHPoint> points = Arrays.asList(
                // 8 (on edge4)
                new GHPoint(49.401669187194116, 9.706821649608745),
                // 5 (on edge0)
                new GHPoint(49.40056349818417, 9.70767186472369),
                // 7 (on edge2)
                new GHPoint(49.406580835146556, 9.704665738628218),
                // 6 (on edge3)
                new GHPoint(49.40107534698834, 9.702248694088528)
        );

        List<QueryResult> queryResults = new ArrayList<>(points.size());
        for (GHPoint point : points) {
            queryResults.add(index.findClosest(point.getLat(), point.getLon(), EdgeFilter.ALL_EDGES));
        }

        RoutingAlgorithmFactory pch = automaticPrepareCH();
        QueryGraph queryGraph = QueryGraph.lookup(chGraph, queryResults);
        RoutingAlgorithm chAlgo = pch.createAlgo(queryGraph, AlgorithmOptions.start()
                .traversalMode(TraversalMode.EDGE_BASED)
                .build());
        Path path = chAlgo.calcPath(5, 6);
        // there should not be a path from 5 to 6, because first we cannot go directly 5-4-6, so we need to go left
        // to 8. then at 2 we cannot go on edge 1 because of another turn restriction, but we can go on edge 2 so we
        // travel via the virtual node 7 to node 1. From there we cannot go to 6 because of the one-way so we go back
        // to node 2 (no u-turn because of the duplicate edge) on edge1. And this is were the journey ends: we cannot
        // go to 8 because of the turn restriction from edge1 to edge4 -> there should not be a path!
        assertFalse("there should not be a path, but found: " + path.calcNodes(), path.isFound());
    }

    @Test
    public void test_issue_1593_simple() {
        // 1
        // |
        // 3-0-x-5-4
        // |
        // 2
        NodeAccess na = graph.getNodeAccess();
        na.setNode(1, 0.2, 0.0);
        na.setNode(3, 0.1, 0.0);
        na.setNode(2, 0.0, 0.0);
        na.setNode(0, 0.1, 0.1);
        na.setNode(5, 0.1, 0.2);
        na.setNode(4, 0.1, 0.3);
        EdgeIteratorState edge0 = graph.edge(3, 1, 10, true);
        EdgeIteratorState edge1 = graph.edge(2, 3, 10, true);
        graph.edge(3, 0, 10, true);
        graph.edge(0, 5, 10, true);
        graph.edge(5, 4, 10, true);
        // cannot go, 2-3-1
        setRestriction(edge1, edge0, 3);
        graph.freeze();
        RoutingAlgorithmFactory pch = prepareCH(Arrays.asList(0, 1, 2, 3, 4, 5));
        assertEquals(5, chGraph.getOriginalEdges());
        assertEquals("expected two shortcuts: 3->5 and 5->3", 7, chGraph.getEdges());
        // there should be no path from 2 to 1, because of the turn restriction and because u-turns are not allowed
        assertFalse(findPathUsingDijkstra(2, 1).isFound());
        compareCHQueryWithDijkstra(pch, 2, 1);

        // we have to pay attention when there are virtual nodes: turning from the shortcut 3-5 onto the
        // virtual edge 5-x should be forbidden.
        LocationIndexTree index = new LocationIndexTree(graph, new RAMDirectory());
        index.prepareIndex();
        QueryResult qr = index.findClosest(0.1, 0.15, EdgeFilter.ALL_EDGES);
        QueryGraph queryGraph = QueryGraph.lookup(chGraph, qr);
        assertEquals("expected one virtual node", 1, queryGraph.getNodes() - chGraph.getNodes());
        RoutingAlgorithm chAlgo = pch.createAlgo(queryGraph, AlgorithmOptions.start()
                .traversalMode(TraversalMode.EDGE_BASED)
                .build());
        Path path = chAlgo.calcPath(2, 1);
        assertFalse("no path should be found, but found " + path.calcNodes(), path.isFound());
    }

    @Test
    public void testRouteViaVirtualNode() {
        //   3
        // 0-x-1-2
        graph.edge(0, 1, 0, false);
        graph.edge(1, 2, 0, false);
        updateDistancesFor(graph, 0, 0.00, 0.00);
        updateDistancesFor(graph, 1, 0.02, 0.02);
        updateDistancesFor(graph, 2, 0.03, 0.03);
        graph.freeze();
        RoutingAlgorithmFactory pch = automaticPrepareCH();
        LocationIndexTree index = new LocationIndexTree(graph, new RAMDirectory());
        index.prepareIndex();
        QueryResult qr = index.findClosest(0.01, 0.01, EdgeFilter.ALL_EDGES);
        QueryGraph queryGraph = QueryGraph.lookup(chGraph, qr);
        assertEquals(3, qr.getClosestNode());
        assertEquals(0, qr.getClosestEdge().getEdge());
        RoutingAlgorithm chAlgo = pch.createAlgo(queryGraph, AlgorithmOptions.start()
                .traversalMode(TraversalMode.EDGE_BASED)
                .build());
        Path path = chAlgo.calcPath(0, 2);
        assertTrue("it should be possible to route via a virtual node, but no path found", path.isFound());
        assertEquals(IntArrayList.from(0, 3, 1, 2), path.calcNodes());
        assertEquals(Helper.DIST_PLANE.calcDist(0.00, 0.00, 0.03, 0.03), path.getDistance(), 1.e-1);
    }

    @Test
    public void testRouteViaVirtualNode_withAlternative() {
        //   3
        // 0-x-1
        //  \  |
        //   \-2
        graph.edge(0, 1, 1, true);
        graph.edge(1, 2, 1, true);
        graph.edge(2, 0, 1, true);
        updateDistancesFor(graph, 0, 0.01, 0.00);
        updateDistancesFor(graph, 1, 0.01, 0.02);
        updateDistancesFor(graph, 2, 0.00, 0.02);
        graph.freeze();
        RoutingAlgorithmFactory pch = automaticPrepareCH();
        LocationIndexTree index = new LocationIndexTree(graph, new RAMDirectory());
        index.prepareIndex();
        QueryResult qr = index.findClosest(0.01, 0.01, EdgeFilter.ALL_EDGES);
        QueryGraph queryGraph = QueryGraph.lookup(chGraph, qr);
        assertEquals(3, qr.getClosestNode());
        assertEquals(0, qr.getClosestEdge().getEdge());
        RoutingAlgorithm chAlgo = pch.createAlgo(queryGraph, AlgorithmOptions.start()
                .traversalMode(TraversalMode.EDGE_BASED)
                .build());
        Path path = chAlgo.calcPath(1, 0);
        assertEquals(IntArrayList.from(1, 3, 0), path.calcNodes());
    }

    @Test
    public void testFiniteUTurnCost_virtualViaNode() {
        // if there is an extra virtual node it can be possible to do a u-turn that otherwise would not be possible
        // and so there can be a difference between CH and non-CH... therefore u-turns at virtual nodes are forbidden
        // 4->3->2->1-x-0
        //          |
        //          5->6
        graph.edge(4, 3, 0, false);
        graph.edge(3, 2, 0, false);
        graph.edge(2, 1, 0, false);
        graph.edge(1, 0, 0, true);
        graph.edge(1, 5, 0, false);
        graph.edge(5, 6, 0, false);
        updateDistancesFor(graph, 4, 0.1, 0.0);
        updateDistancesFor(graph, 3, 0.1, 0.1);
        updateDistancesFor(graph, 2, 0.1, 0.2);
        updateDistancesFor(graph, 1, 0.1, 0.3);
        updateDistancesFor(graph, 0, 0.1, 0.4);
        updateDistancesFor(graph, 5, 0.0, 0.3);
        updateDistancesFor(graph, 6, 0.0, 0.4);
        // not allowed to turn right at node 1 -> we have to take a u-turn at node 0 (or at the virtual node...)
        setRestriction(2, 1, 5);
        graph.freeze();
        chProfile = CHProfile.edgeBased(weighting, 50);
        RoutingAlgorithmFactory pch = prepareCH(Arrays.asList(0, 1, 2, 3, 4, 5, 6));
        LocationIndexTree index = new LocationIndexTree(graph, new RAMDirectory());
        index.prepareIndex();
        GHPoint virtualPoint = new GHPoint(0.1, 0.35);
        QueryResult qr = index.findClosest(virtualPoint.lat, virtualPoint.lon, EdgeFilter.ALL_EDGES);
        QueryGraph chQueryGraph = QueryGraph.lookup(chGraph, qr);
        assertEquals(3, qr.getClosestEdge().getEdge());
        RoutingAlgorithm chAlgo = pch.createAlgo(chQueryGraph, AlgorithmOptions.start()
                .traversalMode(TraversalMode.EDGE_BASED)
                .build());
        Path path = chAlgo.calcPath(4, 6);
        assertTrue(path.isFound());
        assertEquals(IntArrayList.from(4, 3, 2, 1, 0, 1, 5, 6), path.calcNodes());

        QueryResult qr2 = index.findClosest(virtualPoint.lat, virtualPoint.lon, EdgeFilter.ALL_EDGES);
        QueryGraph queryGraph = QueryGraph.lookup(graph, qr2);
        assertEquals(3, qr2.getClosestEdge().getEdge());
        Dijkstra dijkstra = new Dijkstra(queryGraph, new TurnWeighting(weighting, queryGraph.getTurnCostStorage(), chGraph.getCHProfile().getUTurnCosts()), TraversalMode.EDGE_BASED);
        Path dijkstraPath = dijkstra.calcPath(4, 6);
        assertEquals(IntArrayList.from(4, 3, 2, 1, 7, 0, 7, 1, 5, 6), dijkstraPath.calcNodes());
        assertEquals(dijkstraPath.getWeight(), path.getWeight(), 1.e-3);
        assertEquals(dijkstraPath.getDistance(), path.getDistance(), 1.e-3);
        assertEquals(dijkstraPath.getTime(), path.getTime(), 1.e-3);
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
        LOGGER.info("Seed for testFindPath_random_compareWithDijkstra: {}", seed);
        compareWithDijkstraOnRandomGraph(seed);
    }

    @Repeat(times = 10)
    @Test
    public void testFindPath_random_compareWithDijkstra_finiteUTurnCost() {
        long seed = System.nanoTime();
        LOGGER.info("Seed for testFindPath_random_compareWithDijkstra_finiteUTurnCost: {}", seed);
        chProfile = chProfiles.get(1 + new Random(seed).nextInt(chProfiles.size() - 1));
        LOGGER.info("U-turn-costs: " + chProfile.getUTurnCostsInt());
        compareWithDijkstraOnRandomGraph(seed);
    }

    private void compareWithDijkstraOnRandomGraph(long seed) {
        final Random rnd = new Random(seed);
        // for larger graphs preparation takes much longer the higher the degree is!
        GHUtility.buildRandomGraph(graph, rnd, 20, 3.0, true, true, encoder.getAverageSpeedEnc(), 0.7, 0.9, 0.8);
        GHUtility.addRandomTurnCosts(graph, seed, encodingManager, encoder, maxCost, turnCostStorage);
        graph.freeze();
        checkStrict = false;
        List<Integer> contractionOrder = getRandomIntegerSequence(graph.getNodes(), rnd);
        compareCHWithDijkstra(100, contractionOrder);
    }

    /**
     * same as {@link #testFindPath_random_compareWithDijkstra()}, but using automatic node priority calculation
     */
    @Repeat(times = 10)
    @Test
    public void testFindPath_heuristic_compareWithDijkstra() {
        long seed = System.nanoTime();
        LOGGER.info("Seed for testFindPath_heuristic_compareWithDijkstra: {}", seed);
        compareWithDijkstraOnRandomGraph_heuristic(seed);
    }

    @Repeat(times = 10)
    @Test
    public void testFindPath_heuristic_compareWithDijkstra_finiteUTurnCost() {
        long seed = System.nanoTime();
        LOGGER.info("Seed for testFindPath_heuristic_compareWithDijkstra_finiteUTurnCost: {}", seed);
        chProfile = chProfiles.get(1 + new Random(seed).nextInt(chProfiles.size() - 1));
        LOGGER.info("U-turn-costs: " + chProfile.getUTurnCostsInt());
        compareWithDijkstraOnRandomGraph_heuristic(seed);
    }

    private void compareWithDijkstraOnRandomGraph_heuristic(long seed) {
        GHUtility.buildRandomGraph(graph, new Random(seed), 20, 3.0, true, true, encoder.getAverageSpeedEnc(), 0.7, 0.9, 0.8);
        GHUtility.addRandomTurnCosts(graph, seed, encodingManager, encoder, maxCost, turnCostStorage);
        graph.freeze();
        checkStrict = false;
        automaticCompareCHWithDijkstra(100);
    }

    private int nextCost(Random rnd) {
        // choose bound above max cost such that turn restrictions are likely
        return rnd.nextInt(3 * maxCost);
    }

    private double nextDist(int maxDist, Random rnd) {
        return rnd.nextDouble() * maxDist;
    }

    private void checkPathUsingRandomContractionOrder(IntArrayList expectedPath, int expectedWeight, int expectedTurnCosts, int from, int to) {
        List<Integer> contractionOrder = getRandomIntegerSequence(graph.getNodes());
        checkPath(expectedPath, expectedWeight, expectedTurnCosts, from, to, contractionOrder);
    }

    private void checkPath(IntArrayList expectedPath, int expectedEdgeWeight, int expectedTurnCosts, int from, int to, List<Integer> contractionOrder) {
        checkPathUsingDijkstra(expectedPath, expectedEdgeWeight, expectedTurnCosts, from, to);
        checkPathUsingCH(expectedPath, expectedEdgeWeight, expectedTurnCosts, from, to, contractionOrder);
    }

    private void checkPathUsingDijkstra(IntArrayList expectedPath, int expectedEdgeWeight, int expectedTurnCosts, int from, int to) {
        Path dijkstraPath = findPathUsingDijkstra(from, to);
        int expectedWeight = expectedEdgeWeight + expectedTurnCosts;
        int expectedDistance = expectedEdgeWeight;
        int expectedTime = expectedEdgeWeight * 60 + expectedTurnCosts * 1000;
        assertEquals("Normal Dijkstra did not find expected path.", expectedPath, dijkstraPath.calcNodes());
        assertEquals("Normal Dijkstra did not calculate expected weight.", expectedWeight, dijkstraPath.getWeight(), 1.e-6);
        assertEquals("Normal Dijkstra did not calculate expected distance.", expectedDistance, dijkstraPath.getDistance(), 1.e-6);
        assertEquals("Normal Dijkstra did not calculate expected time.", expectedTime, dijkstraPath.getTime(), 1.e-6);
    }

    private void checkPathUsingCH(IntArrayList expectedPath, int expectedEdgeWeight, int expectedTurnCosts, int from, int to, List<Integer> contractionOrder) {
        Path chPath = findPathUsingCH(from, to, contractionOrder);
        int expectedWeight = expectedEdgeWeight + expectedTurnCosts;
        int expectedDistance = expectedEdgeWeight;
        int expectedTime = expectedEdgeWeight * 60 + expectedTurnCosts * 1000;
        assertEquals("Contraction Hierarchies did not find expected path. contraction order=" + contractionOrder, expectedPath, chPath.calcNodes());
        assertEquals("Contraction Hierarchies did not calculate expected weight.", expectedWeight, chPath.getWeight(), 1.e-6);
        assertEquals("Contraction Hierarchies did not calculate expected distance.", expectedDistance, chPath.getDistance(), 1.e-6);
        assertEquals("Contraction Hierarchies did not calculate expected time.", expectedTime, chPath.getTime(), 1.e-6);
    }

    private Path findPathUsingDijkstra(int from, int to) {
        Dijkstra dijkstra = new Dijkstra(graph, new TurnWeighting(weighting, turnCostStorage, chProfile.getUTurnCosts()), TraversalMode.EDGE_BASED);
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
        PrepareContractionHierarchies ch = PrepareContractionHierarchies.fromGraphHopperStorage(graph, chProfile)
                .useFixedNodeOrdering(nodeOrderingProvider);
        ch.doWork();
        chGraph = graph.getCHGraph(chProfile);
        return ch.getRoutingAlgorithmFactory();
    }

    private RoutingAlgorithmFactory automaticPrepareCH() {
        PMap pMap = new PMap();
        pMap.put(PERIODIC_UPDATES, 20);
        pMap.put(LAST_LAZY_NODES_UPDATES, 100);
        pMap.put(NEIGHBOR_UPDATES, 4);
        pMap.put(LOG_MESSAGES, 10);
        PrepareContractionHierarchies ch = PrepareContractionHierarchies.fromGraphHopperStorage(graph, chProfile);
        ch.setParams(pMap);
        ch.doWork();
        chGraph = graph.getCHGraph(chProfile);
        return ch.getRoutingAlgorithmFactory();
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
        boolean algosDisagree = Math.abs(dijkstraPath.getWeight() - chPath.getWeight()) > 1.e-2;
        if (checkStrict) {
            algosDisagree = algosDisagree
                    || Math.abs(dijkstraPath.getDistance() - chPath.getDistance()) > 1.e-2
                    || Math.abs(dijkstraPath.getTime() - chPath.getTime()) > 1;
        }
        if (algosDisagree) {
            System.out.println("Graph that produced error:");
            GHUtility.printGraphForUnitTest(graph, encoder);
            fail("Dijkstra and CH did not find equal shortest paths for route from " + from + " to " + to + "\n" +
                    " dijkstra: weight: " + dijkstraPath.getWeight() + ", distance: " + dijkstraPath.getDistance() +
                    ", time: " + dijkstraPath.getTime() + ", nodes: " + dijkstraPath.calcNodes() + "\n" +
                    "       ch: weight: " + chPath.getWeight() + ", distance: " + chPath.getDistance() +
                    ", time: " + chPath.getTime() + ", nodes: " + chPath.calcNodes());
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

    private void setRandomCostOrRestriction(int from, int via, int to, Random rnd) {
        final double chance = 0.7;
        if (rnd.nextDouble() < chance) {
            setRestriction(from, via, to);
            LOGGER.trace("setRestriction({}, {}, {});", from, via, to);
        } else {
            setRandomCost(from, via, to, rnd);
        }
    }

    private void setRandomCost(int from, int via, int to, Random rnd) {
        int cost = (int) (rnd.nextDouble() * maxCost / 2);
        setTurnCost(from, via, to, cost);
        LOGGER.trace("setTurnCost({}, {}, {}, {});", from, via, to, cost);
    }

    private void setRestriction(int from, int via, int to) {
        setRestriction(getEdge(from, via), getEdge(via, to), via);
    }

    private void setRestriction(EdgeIteratorState inEdge, EdgeIteratorState outEdge, int viaNode) {
        graph.getTurnCostStorage().setExpensive("car", encodingManager, inEdge.getEdge(), viaNode, outEdge.getEdge(), Double.POSITIVE_INFINITY);
    }

    private void setTurnCost(int from, int via, int to, double cost) {
        setTurnCost(getEdge(from, via), getEdge(via, to), via, cost);
    }

    private void setTurnCost(EdgeIteratorState inEdge, EdgeIteratorState outEdge, int viaNode, double costs) {
        graph.getTurnCostStorage().setExpensive("car", encodingManager, inEdge.getEdge(), viaNode, outEdge.getEdge(), costs);
    }

    private void setCostOrRestriction(EdgeIteratorState inEdge, EdgeIteratorState outEdge, int viaNode, int cost) {
        if (cost >= maxCost) {
            setRestriction(inEdge, outEdge, viaNode);
            LOGGER.trace("setRestriction(edge{}, edge{}, {});", inEdge.getEdge(), outEdge.getEdge(), viaNode);
        } else {
            setTurnCost(inEdge, outEdge, viaNode, cost);
            LOGGER.trace("setTurnCost(edge{}, edge{}, {}, {});", inEdge.getEdge(), outEdge.getEdge(), viaNode, cost);
        }
    }

    private EdgeIteratorState getEdge(int from, int to) {
        return GHUtility.getEdge(graph, from, to);
    }
}
