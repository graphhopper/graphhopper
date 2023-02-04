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

import com.graphhopper.core.util.PMap;
import com.carrotsearch.hppc.IntArrayList;
import com.graphhopper.routing.Dijkstra;
import com.graphhopper.routing.DijkstraBidirectionEdgeCHNoSOD;
import com.graphhopper.routing.Path;
import com.graphhopper.routing.RoutingAlgorithm;
import com.graphhopper.routing.ev.*;
import com.graphhopper.routing.querygraph.QueryGraph;
import com.graphhopper.routing.querygraph.QueryRoutingCHGraph;
import com.graphhopper.routing.util.AccessFilter;
import com.graphhopper.routing.util.EdgeFilter;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.routing.util.TraversalMode;
import com.graphhopper.routing.weighting.DefaultTurnCostProvider;
import com.graphhopper.routing.weighting.ShortestWeighting;
import com.graphhopper.routing.weighting.Weighting;
import com.graphhopper.storage.*;
import com.graphhopper.storage.index.LocationIndexTree;
import com.graphhopper.storage.index.Snap;
import com.graphhopper.core.util.shapes.GHPoint;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

import static com.graphhopper.routing.ch.CHParameters.*;
import static com.graphhopper.routing.weighting.Weighting.INFINITE_U_TURN_COSTS;
import static com.graphhopper.util.GHUtility.updateDistancesFor;
import static com.graphhopper.core.util.Parameters.Algorithms.ASTAR_BI;
import static com.graphhopper.core.util.Parameters.Algorithms.DIJKSTRA_BI;
import static com.graphhopper.core.util.Parameters.Routing.ALGORITHM;
import com.graphhopper.util.ArrayUtil;
import com.graphhopper.util.DistancePlaneProjection;
import com.graphhopper.util.EdgeExplorer;
import com.graphhopper.util.EdgeIterator;
import com.graphhopper.util.EdgeIteratorState;
import com.graphhopper.util.GHUtility;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Here we test if Contraction Hierarchies work with turn costs, i.e. we first contract the graph and then run
 * routing queries and check if the routing results are correct. We thus test the combination of
 * {@link EdgeBasedNodeContractor} and {@link DijkstraBidirectionEdgeCHNoSOD}. In most cases we either use a predefined
 * or random contraction order, so the hard to test and heuristic automatic search for an efficient contraction order
 * taking place  in {@link PrepareContractionHierarchies} is not covered, but this is ok, because the correctness
 * of CH should not depend on the contraction order.
 *
 * @see EdgeBasedNodeContractor where shortcut creation is tested independent of the routing query
 */
public class CHTurnCostTest {
    private static final Logger LOGGER = LoggerFactory.getLogger(CHTurnCostTest.class);
    private int maxCost;
    private BooleanEncodedValue accessEnc;
    private DecimalEncodedValue speedEnc;
    private DecimalEncodedValue turnCostEnc;
    private EncodingManager encodingManager;
    private BaseGraph graph;
    private TurnCostStorage turnCostStorage;
    private List<CHConfig> chConfigs;
    private CHConfig chConfig;
    private RoutingCHGraph chGraph;
    private boolean checkStrict;

    @BeforeEach
    public void init() {
        maxCost = 10;
        accessEnc = new SimpleBooleanEncodedValue("access", true);
        speedEnc = new DecimalEncodedValueImpl("speed", 5, 5, false);
        turnCostEnc = TurnCost.create("car", maxCost);
        encodingManager = EncodingManager.start().add(accessEnc).add(speedEnc).addTurnCostEncodedValue(turnCostEnc).build();
        graph = new BaseGraph.Builder(encodingManager).withTurnCosts(true).build();
        turnCostStorage = graph.getTurnCostStorage();
        chConfigs = createCHConfigs();
        // the default CH profile with infinite u-turn costs, can be reset in tests that should run with finite u-turn
        // costs
        chConfig = chConfigs.get(0);
        checkStrict = true;
    }

    /**
     * Creates a list of distinct CHProfiles with different u-turn costs that can be used by the tests.
     * There is always a profile with infinite u-turn costs and one with u-turn-costs = 50.
     */
    private List<CHConfig> createCHConfigs() {
        Set<CHConfig> configs = new LinkedHashSet<>(5);
        // the first one is always the one with infinite u-turn costs
        configs.add(CHConfig.edgeBased("p0", new ShortestWeighting(accessEnc, speedEnc, new DefaultTurnCostProvider(turnCostEnc, turnCostStorage, INFINITE_U_TURN_COSTS))));
        // this one we also always add
        configs.add(CHConfig.edgeBased("p1", new ShortestWeighting(accessEnc, speedEnc, new DefaultTurnCostProvider(turnCostEnc, turnCostStorage, 0))));
        // ... and this one
        configs.add(CHConfig.edgeBased("p2", new ShortestWeighting(accessEnc, speedEnc, new DefaultTurnCostProvider(turnCostEnc, turnCostStorage, 50))));
        // add more (distinct) profiles
        long seed = System.nanoTime();
        Random rnd = new Random(seed);
        while (configs.size() < 6) {
            int uTurnCosts = 10 + rnd.nextInt(90);
            configs.add(CHConfig.edgeBased("p" + configs.size(), new ShortestWeighting(accessEnc, speedEnc, new DefaultTurnCostProvider(turnCostEnc, turnCostStorage, uTurnCosts))));
        }
        return new ArrayList<>(configs);
    }

    @RepeatedTest(10)
    public void testFindPath_randomContractionOrder_linear() {
        // 2-1-0-3-4
        GHUtility.setSpeed(60, true, true, accessEnc, speedEnc, graph.edge(2, 1).setDistance(2));
        GHUtility.setSpeed(60, true, true, accessEnc, speedEnc, graph.edge(1, 0).setDistance(3));
        GHUtility.setSpeed(60, true, true, accessEnc, speedEnc, graph.edge(0, 3).setDistance(1));
        GHUtility.setSpeed(60, true, true, accessEnc, speedEnc, graph.edge(3, 4).setDistance(3));
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
        GHUtility.setSpeed(60, true, true, accessEnc, speedEnc, graph.edge(0, 1).setDistance(5));
        GHUtility.setSpeed(60, true, true, accessEnc, speedEnc, graph.edge(0, 1).setDistance(6));
        GHUtility.setSpeed(60, true, true, accessEnc, speedEnc, graph.edge(1, 2).setDistance(2));
        GHUtility.setSpeed(60, true, false, accessEnc, speedEnc, graph.edge(3, 2).setDistance(3));
        GHUtility.setSpeed(60, true, false, accessEnc, speedEnc, graph.edge(2, 4).setDistance(3));
        setRestriction(3, 2, 4);
        graph.freeze();
        compareCHWithDijkstra(10, new int[]{0, 1, 2, 3, 4});
    }

    @Test
    public void testFindPath_randomContractionOrder_double_duplicate_edges() {
        //  /\ /\   
        // 0  1  2--3
        //  \/ \/
        GHUtility.setSpeed(60, true, true, accessEnc, speedEnc, graph.edge(0, 1).setDistance(25.789000));
        GHUtility.setSpeed(60, true, true, accessEnc, speedEnc, graph.edge(0, 1).setDistance(26.016000));
        GHUtility.setSpeed(60, true, true, accessEnc, speedEnc, graph.edge(1, 2).setDistance(21.902000));
        GHUtility.setSpeed(60, true, true, accessEnc, speedEnc, graph.edge(1, 2).setDistance(21.862000));
        GHUtility.setSpeed(60, true, true, accessEnc, speedEnc, graph.edge(2, 3).setDistance(52.987000));
        graph.freeze();
        compareCHWithDijkstra(1000, new int[]{0, 1, 2, 3});
    }

    @RepeatedTest(100)
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
        GHUtility.setSpeed(60, true, false, accessEnc, speedEnc, graph.edge(0, 5).setDistance(1));
        GHUtility.setSpeed(60, true, false, accessEnc, speedEnc, graph.edge(1, 5).setDistance(1));
        GHUtility.setSpeed(60, true, false, accessEnc, speedEnc, graph.edge(2, 5).setDistance(1));
        GHUtility.setSpeed(60, true, false, accessEnc, speedEnc, graph.edge(5, 3).setDistance(1));
        GHUtility.setSpeed(60, true, false, accessEnc, speedEnc, graph.edge(3, 4).setDistance(1));
        GHUtility.setSpeed(60, true, false, accessEnc, speedEnc, graph.edge(4, 7).setDistance(1));
        GHUtility.setSpeed(60, true, false, accessEnc, speedEnc, graph.edge(5, 6).setDistance(3));
        GHUtility.setSpeed(60, true, false, accessEnc, speedEnc, graph.edge(6, 7).setDistance(3));
        GHUtility.setSpeed(60, true, false, accessEnc, speedEnc, graph.edge(7, 8).setDistance(1));
        GHUtility.setSpeed(60, true, false, accessEnc, speedEnc, graph.edge(7, 9).setDistance(1));
        GHUtility.setSpeed(60, true, false, accessEnc, speedEnc, graph.edge(7, 10).setDistance(1));

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

        prepareCH(6, 0, 1, 2, 8, 9, 10, 5, 3, 4, 7);
        // run queries for all cases (target/source edge possibly restricted/has costs)
        checkStrict = false;
        compareCHQueryWithDijkstra(2, 10);
        compareCHQueryWithDijkstra(1, 10);
        compareCHQueryWithDijkstra(2, 9);
        compareCHQueryWithDijkstra(1, 9);
    }

    @Test
    public void testFindPath_multipleInOutEdges_turnReplacementDifference_bug1() {
        //       3 - 4
        //      /     \
        // 1 - 5 - 6 - 7 - 9
        //    /         \
        //   2           10
        GHUtility.setSpeed(60, true, false, accessEnc, speedEnc, graph.edge(1, 5).setDistance(1));
        GHUtility.setSpeed(60, true, false, accessEnc, speedEnc, graph.edge(2, 5).setDistance(1));
        GHUtility.setSpeed(60, true, false, accessEnc, speedEnc, graph.edge(5, 3).setDistance(1));
        GHUtility.setSpeed(60, true, false, accessEnc, speedEnc, graph.edge(3, 4).setDistance(1));
        GHUtility.setSpeed(60, true, false, accessEnc, speedEnc, graph.edge(4, 7).setDistance(1));
        GHUtility.setSpeed(60, true, false, accessEnc, speedEnc, graph.edge(5, 6).setDistance(3));
        GHUtility.setSpeed(60, true, false, accessEnc, speedEnc, graph.edge(6, 7).setDistance(3));
        GHUtility.setSpeed(60, true, false, accessEnc, speedEnc, graph.edge(7, 9).setDistance(1));
        GHUtility.setSpeed(60, true, false, accessEnc, speedEnc, graph.edge(7, 10).setDistance(1));

        setTurnCost(2, 5, 6, 4);
        setRestriction(1, 5, 6);
        setRestriction(4, 7, 9);

        prepareCH(6, 0, 1, 2, 8, 9, 10, 5, 3, 4, 7);
        compareCHQueryWithDijkstra(2, 9);
    }

    @Test
    public void testFindPath_duplicateEdge() {
        // 0 -> 1 -> 2 -> 3 -> 4
        //            \->/
        GHUtility.setSpeed(60, true, false, accessEnc, speedEnc, graph.edge(0, 1).setDistance(1));
        GHUtility.setSpeed(60, true, false, accessEnc, speedEnc, graph.edge(1, 2).setDistance(1));
        GHUtility.setSpeed(60, true, false, accessEnc, speedEnc, graph.edge(2, 3).setDistance(1));
        GHUtility.setSpeed(60, true, false, accessEnc, speedEnc, graph.edge(2, 3).setDistance(1));
        GHUtility.setSpeed(60, true, false, accessEnc, speedEnc, graph.edge(3, 4).setDistance(1));
        compareCHWithDijkstra(100, new int[]{2, 3, 0, 4, 1});
    }

    @Test
    public void testFindPath_chain() {
        // 0   2   4   6   8
        //  \ / \ / \ / \ /
        //   1   3   5   7
        GHUtility.setSpeed(60, true, false, accessEnc, speedEnc, graph.edge(0, 1).setDistance(1));
        GHUtility.setSpeed(60, true, false, accessEnc, speedEnc, graph.edge(1, 2).setDistance(1));
        GHUtility.setSpeed(60, true, false, accessEnc, speedEnc, graph.edge(2, 3).setDistance(1));
        GHUtility.setSpeed(60, true, false, accessEnc, speedEnc, graph.edge(3, 4).setDistance(1));
        GHUtility.setSpeed(60, true, false, accessEnc, speedEnc, graph.edge(4, 5).setDistance(1));
        GHUtility.setSpeed(60, true, false, accessEnc, speedEnc, graph.edge(5, 6).setDistance(1));
        GHUtility.setSpeed(60, true, false, accessEnc, speedEnc, graph.edge(6, 7).setDistance(1));
        GHUtility.setSpeed(60, true, false, accessEnc, speedEnc, graph.edge(7, 8).setDistance(1));
        graph.freeze();
        setTurnCost(1, 2, 3, 4);
        setTurnCost(3, 4, 5, 2);
        setTurnCost(5, 6, 7, 3);

        // we contract the graph such that only a few shortcuts are created and that the fwd/bwd searches for the
        // 0-8 query meet at node 4 (make sure we include all three cases where turn cost times might come to play:
        // fwd/bwd search and meeting point)
        checkPathUsingCH(ArrayUtil.iota(9), 8, 9, 0, 8, new int[]{1, 3, 5, 7, 0, 8, 2, 6, 4});
    }

    @Test
    public void testFindPath_bidir_chain() {
        //   5 3 2 1 4    turn costs ->
        // 0-1-2-3-4-5-6
        //   0 1 4 2 3    turn costs <-
        EdgeIteratorState edge0 = GHUtility.setSpeed(60, true, true, accessEnc, speedEnc, graph.edge(0, 1).setDistance(1));
        EdgeIteratorState edge1 = GHUtility.setSpeed(60, true, true, accessEnc, speedEnc, graph.edge(1, 2).setDistance(1));
        EdgeIteratorState edge2 = GHUtility.setSpeed(60, true, true, accessEnc, speedEnc, graph.edge(2, 3).setDistance(1));
        EdgeIteratorState edge3 = GHUtility.setSpeed(60, true, true, accessEnc, speedEnc, graph.edge(3, 4).setDistance(1));
        EdgeIteratorState edge4 = GHUtility.setSpeed(60, true, true, accessEnc, speedEnc, graph.edge(4, 5).setDistance(1));
        EdgeIteratorState edge5 = GHUtility.setSpeed(60, true, true, accessEnc, speedEnc, graph.edge(5, 6).setDistance(1));
        graph.freeze();

        // turn costs ->
        setTurnCost(edge0, edge1, 1, 5);
        setTurnCost(edge1, edge2, 2, 3);
        setTurnCost(edge2, edge3, 3, 2);
        setTurnCost(edge3, edge4, 4, 1);
        setTurnCost(edge4, edge5, 5, 4);
        // turn costs <-
        setTurnCost(edge5, edge4, 5, 3);
        setTurnCost(edge4, edge3, 4, 2);
        setTurnCost(edge3, edge2, 3, 4);
        setTurnCost(edge2, edge1, 2, 1);
        setTurnCost(edge1, edge0, 1, 0);

        prepareCH(1, 3, 5, 2, 4, 0, 6);

        Path pathFwd = createAlgo().calcPath(0, 6);
        assertEquals(IntArrayList.from(0, 1, 2, 3, 4, 5, 6), pathFwd.calcNodes());
        assertEquals(6 + 15, pathFwd.getWeight(), 1.e-6);

        Path pathBwd = createAlgo().calcPath(6, 0);
        assertEquals(IntArrayList.from(6, 5, 4, 3, 2, 1, 0), pathBwd.calcNodes());
        assertEquals(6 + 10, pathBwd.getWeight(), 1.e-6);
    }


    @RepeatedTest(10)
    public void testFindPath_randomContractionOrder_simpleLoop() {
        //      2
        //     /|
        //  0-4-3
        //    |
        //    1
        GHUtility.setSpeed(60, true, false, accessEnc, speedEnc, graph.edge(0, 4).setDistance(2));
        GHUtility.setSpeed(60, true, true, accessEnc, speedEnc, graph.edge(4, 3).setDistance(2));
        GHUtility.setSpeed(60, true, true, accessEnc, speedEnc, graph.edge(3, 2).setDistance(1));
        GHUtility.setSpeed(60, true, true, accessEnc, speedEnc, graph.edge(2, 4).setDistance(1));
        GHUtility.setSpeed(60, true, false, accessEnc, speedEnc, graph.edge(4, 1).setDistance(1));
        graph.freeze();

        // enforce loop (going counter-clockwise)
        setRestriction(0, 4, 1);
        setTurnCost(4, 2, 3, 4);
        setTurnCost(3, 2, 4, 2);

        checkPathUsingRandomContractionOrder(IntArrayList.from(0, 4, 3, 2, 4, 1), 7, 2, 0, 1);
    }

    @RepeatedTest(10)
    public void testFindPath_randomContractionOrder_singleDirectedLoop() {
        //  3 1-2
        //  | | |
        //  7-5-0
        //    |
        //    6-4
        GHUtility.setSpeed(60, true, false, accessEnc, speedEnc, graph.edge(3, 7).setDistance(1));
        GHUtility.setSpeed(60, true, false, accessEnc, speedEnc, graph.edge(7, 5).setDistance(2));
        GHUtility.setSpeed(60, true, false, accessEnc, speedEnc, graph.edge(5, 0).setDistance(2));
        GHUtility.setSpeed(60, true, false, accessEnc, speedEnc, graph.edge(0, 2).setDistance(1));
        GHUtility.setSpeed(60, true, false, accessEnc, speedEnc, graph.edge(2, 1).setDistance(2));
        GHUtility.setSpeed(60, true, false, accessEnc, speedEnc, graph.edge(1, 5).setDistance(1));
        GHUtility.setSpeed(60, true, false, accessEnc, speedEnc, graph.edge(5, 6).setDistance(1));
        GHUtility.setSpeed(60, true, false, accessEnc, speedEnc, graph.edge(6, 4).setDistance(2));
        graph.freeze();

        setRestriction(7, 5, 6);
        setTurnCost(0, 2, 1, 2);

        final IntArrayList expectedPath = IntArrayList.from(3, 7, 5, 0, 2, 1, 5, 6, 4);
        final int roadCosts = 12;
        final int turnCosts = 2;

        checkPathUsingRandomContractionOrder(expectedPath, roadCosts, turnCosts, 3, 4);
    }

    @RepeatedTest(10)
    public void testFindPath_randomContractionOrder_singleLoop() {
        //  0   4
        //  |  /|
        //  1-2-3
        //    |
        //    5-6
        GHUtility.setSpeed(60, true, false, accessEnc, speedEnc, graph.edge(0, 1).setDistance(1));
        GHUtility.setSpeed(60, true, false, accessEnc, speedEnc, graph.edge(1, 2).setDistance(2));
        GHUtility.setSpeed(60, true, true, accessEnc, speedEnc, graph.edge(2, 3).setDistance(2));
        GHUtility.setSpeed(60, true, true, accessEnc, speedEnc, graph.edge(3, 4).setDistance(1));
        GHUtility.setSpeed(60, true, true, accessEnc, speedEnc, graph.edge(4, 2).setDistance(1));
        GHUtility.setSpeed(60, true, false, accessEnc, speedEnc, graph.edge(2, 5).setDistance(1));
        GHUtility.setSpeed(60, true, false, accessEnc, speedEnc, graph.edge(5, 6).setDistance(2));
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

    @RepeatedTest(10)
    public void testFindPath_randomContractionOrder_singleLoopWithNoise() {
        //  0~15~16~17              solid lines: paths contributing to shortest path from 0 to 14
        //  |        {              wiggly lines: extra paths to make it more complicated
        //  1~ 2- 3~ 4
        //  |  |  |  {
        //  6- 7- 8  9
        //  }  |  }  }
        // 11~12-13-14

        GHUtility.setSpeed(60, true, true, accessEnc, speedEnc, graph.edge(0, 1).setDistance(1));
        GHUtility.setSpeed(60, true, true, accessEnc, speedEnc, graph.edge(1, 6).setDistance(1));
        GHUtility.setSpeed(60, true, true, accessEnc, speedEnc, graph.edge(6, 7).setDistance(2));
        GHUtility.setSpeed(60, true, true, accessEnc, speedEnc, graph.edge(7, 8).setDistance(2));
        GHUtility.setSpeed(60, true, true, accessEnc, speedEnc, graph.edge(8, 3).setDistance(1));
        GHUtility.setSpeed(60, true, true, accessEnc, speedEnc, graph.edge(3, 2).setDistance(2));
        GHUtility.setSpeed(60, true, true, accessEnc, speedEnc, graph.edge(2, 7).setDistance(1));
        GHUtility.setSpeed(60, true, true, accessEnc, speedEnc, graph.edge(7, 12).setDistance(1));
        GHUtility.setSpeed(60, true, true, accessEnc, speedEnc, graph.edge(12, 13).setDistance(2));
        GHUtility.setSpeed(60, true, true, accessEnc, speedEnc, graph.edge(13, 14).setDistance(2));

        // some more edges to make it more complicated -> potentially find more bugs
        GHUtility.setSpeed(60, true, true, accessEnc, speedEnc, graph.edge(1, 2).setDistance(8));
        GHUtility.setSpeed(60, true, true, accessEnc, speedEnc, graph.edge(6, 11).setDistance(3));
        GHUtility.setSpeed(60, true, true, accessEnc, speedEnc, graph.edge(11, 12).setDistance(50));
        GHUtility.setSpeed(60, true, true, accessEnc, speedEnc, graph.edge(8, 13).setDistance(1));
        GHUtility.setSpeed(60, true, true, accessEnc, speedEnc, graph.edge(0, 15).setDistance(1));
        GHUtility.setSpeed(60, true, true, accessEnc, speedEnc, graph.edge(15, 16).setDistance(2));
        GHUtility.setSpeed(60, true, true, accessEnc, speedEnc, graph.edge(16, 17).setDistance(3));
        GHUtility.setSpeed(60, true, true, accessEnc, speedEnc, graph.edge(17, 4).setDistance(2));
        GHUtility.setSpeed(60, true, true, accessEnc, speedEnc, graph.edge(3, 4).setDistance(2));
        GHUtility.setSpeed(60, true, true, accessEnc, speedEnc, graph.edge(4, 9).setDistance(1));
        GHUtility.setSpeed(60, true, true, accessEnc, speedEnc, graph.edge(9, 14).setDistance(2));
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

    @RepeatedTest(10)
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
        GHUtility.setSpeed(60, true, true, accessEnc, speedEnc, graph.edge(0, 1).setDistance(1));
        GHUtility.setSpeed(60, true, true, accessEnc, speedEnc, graph.edge(1, 7).setDistance(3));
        GHUtility.setSpeed(60, true, false, accessEnc, speedEnc, graph.edge(7, 8).setDistance(2));
        GHUtility.setSpeed(60, true, true, accessEnc, speedEnc, graph.edge(8, 3).setDistance(1));
        GHUtility.setSpeed(60, true, true, accessEnc, speedEnc, graph.edge(3, 2).setDistance(2));
        GHUtility.setSpeed(60, true, true, accessEnc, speedEnc, graph.edge(2, 7).setDistance(1));
        GHUtility.setSpeed(60, true, true, accessEnc, speedEnc, graph.edge(7, 12).setDistance(1));
        GHUtility.setSpeed(60, true, true, accessEnc, speedEnc, graph.edge(12, 11).setDistance(2));
        GHUtility.setSpeed(60, true, true, accessEnc, speedEnc, graph.edge(11, 6).setDistance(1));
        GHUtility.setSpeed(60, true, false, accessEnc, speedEnc, graph.edge(6, 7).setDistance(2));
        GHUtility.setSpeed(60, true, true, accessEnc, speedEnc, graph.edge(7, 13).setDistance(3));
        GHUtility.setSpeed(60, true, true, accessEnc, speedEnc, graph.edge(13, 14).setDistance(2));
        GHUtility.setSpeed(60, true, true, accessEnc, speedEnc, graph.edge(14, 9).setDistance(1));
        GHUtility.setSpeed(60, true, true, accessEnc, speedEnc, graph.edge(9, 4).setDistance(1));
        GHUtility.setSpeed(60, true, true, accessEnc, speedEnc, graph.edge(4, 5).setDistance(2));
        GHUtility.setSpeed(60, true, true, accessEnc, speedEnc, graph.edge(5, 10).setDistance(1));
        GHUtility.setSpeed(60, true, true, accessEnc, speedEnc, graph.edge(10, 9).setDistance(2));
        GHUtility.setSpeed(60, true, true, accessEnc, speedEnc, graph.edge(14, 19).setDistance(1));
        GHUtility.setSpeed(60, true, true, accessEnc, speedEnc, graph.edge(19, 18).setDistance(2));
        GHUtility.setSpeed(60, true, true, accessEnc, speedEnc, graph.edge(18, 17).setDistance(2));
        GHUtility.setSpeed(60, true, true, accessEnc, speedEnc, graph.edge(17, 16).setDistance(2));
        GHUtility.setSpeed(60, true, true, accessEnc, speedEnc, graph.edge(16, 21).setDistance(1));
        GHUtility.setSpeed(60, true, true, accessEnc, speedEnc, graph.edge(21, 22).setDistance(2));
        GHUtility.setSpeed(60, true, true, accessEnc, speedEnc, graph.edge(22, 23).setDistance(2));
        GHUtility.setSpeed(60, true, true, accessEnc, speedEnc, graph.edge(23, 24).setDistance(2));
        GHUtility.setSpeed(60, true, true, accessEnc, speedEnc, graph.edge(24, 19).setDistance(1));
        GHUtility.setSpeed(60, true, true, accessEnc, speedEnc, graph.edge(19, 20).setDistance(2));
        GHUtility.setSpeed(60, true, true, accessEnc, speedEnc, graph.edge(20, 25).setDistance(1));
        GHUtility.setSpeed(60, true, true, accessEnc, speedEnc, graph.edge(25, 26).setDistance(2));

        //some more edges to make it more complicated -> potentially find more bugs
        GHUtility.setSpeed(60, true, true, accessEnc, speedEnc, graph.edge(1, 2).setDistance(1));
        GHUtility.setSpeed(60, true, false, accessEnc, speedEnc, graph.edge(4, 3).setDistance(1));
        GHUtility.setSpeed(60, true, true, accessEnc, speedEnc, graph.edge(8, 9).setDistance(75));
        GHUtility.setSpeed(60, true, true, accessEnc, speedEnc, graph.edge(17, 22).setDistance(9));
        GHUtility.setSpeed(60, true, true, accessEnc, speedEnc, graph.edge(18, 23).setDistance(15));
        GHUtility.setSpeed(60, true, true, accessEnc, speedEnc, graph.edge(12, 17).setDistance(50));
        GHUtility.setSpeed(60, true, true, accessEnc, speedEnc, graph.edge(13, 18).setDistance(80));
        GHUtility.setSpeed(60, true, true, accessEnc, speedEnc, graph.edge(14, 15).setDistance(3));
        GHUtility.setSpeed(60, true, true, accessEnc, speedEnc, graph.edge(15, 27).setDistance(2));
        GHUtility.setSpeed(60, true, true, accessEnc, speedEnc, graph.edge(27, 28).setDistance(100));
        GHUtility.setSpeed(60, true, true, accessEnc, speedEnc, graph.edge(28, 26).setDistance(1));
        GHUtility.setSpeed(60, true, true, accessEnc, speedEnc, graph.edge(20, 28).setDistance(1));
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
        GHUtility.setSpeed(60, true, false, accessEnc, speedEnc, graph.edge(5, 6).setDistance(1));
        GHUtility.setSpeed(60, true, false, accessEnc, speedEnc, graph.edge(6, 1).setDistance(1));
        GHUtility.setSpeed(60, true, true, accessEnc, speedEnc, graph.edge(6, 4).setDistance(1));
        GHUtility.setSpeed(60, true, false, accessEnc, speedEnc, graph.edge(4, 0).setDistance(1));
        GHUtility.setSpeed(60, true, false, accessEnc, speedEnc, graph.edge(0, 3).setDistance(1));
        GHUtility.setSpeed(60, true, false, accessEnc, speedEnc, graph.edge(3, 2).setDistance(1));
        GHUtility.setSpeed(60, true, false, accessEnc, speedEnc, graph.edge(2, 4).setDistance(1));
        graph.freeze();
        setRestriction(5, 6, 1);

        final IntArrayList expectedPath = IntArrayList.from(5, 6, 4, 0, 3, 2, 4, 6, 1);
        checkPath(expectedPath, 8, 0, 5, 1, new int[]{0, 1, 2, 3, 4, 5, 6});
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
        GHUtility.setSpeed(60, true, false, accessEnc, speedEnc, graph.edge(5, 6).setDistance(1));
        GHUtility.setSpeed(60, true, false, accessEnc, speedEnc, graph.edge(6, 7).setDistance(1));
        GHUtility.setSpeed(60, true, true, accessEnc, speedEnc, graph.edge(6, 1).setDistance(1));
        GHUtility.setSpeed(60, true, true, accessEnc, speedEnc, graph.edge(1, 4).setDistance(1));
        GHUtility.setSpeed(60, true, false, accessEnc, speedEnc, graph.edge(4, 0).setDistance(1));
        GHUtility.setSpeed(60, true, false, accessEnc, speedEnc, graph.edge(0, 3).setDistance(1));
        GHUtility.setSpeed(60, true, false, accessEnc, speedEnc, graph.edge(3, 2).setDistance(1));
        GHUtility.setSpeed(60, true, false, accessEnc, speedEnc, graph.edge(2, 4).setDistance(1));
        graph.freeze();
        setRestriction(5, 6, 7);

        final IntArrayList expectedPath = IntArrayList.from(5, 6, 1, 4, 0, 3, 2, 4, 1, 6, 7);
        checkPath(expectedPath, 10, 0, 5, 7, new int[]{0, 1, 2, 3, 4, 5, 6, 7});
    }

    @RepeatedTest(10)
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
                GHUtility.setSpeed(60, true, true, accessEnc, speedEnc, graph.edge(from, to).setDistance(dist));
                LOGGER.trace("final EdgeIteratorState edge{} = graph.edge({},{},{},true);", edgeCounter++, from, to, dist);
            }
        }
        // vertical edges
        for (int i = 0; i < size - 1; ++i) {
            for (int j = 0; j < size; ++j) {
                final int from = i * size + j;
                final int to = from + size;
                double dist = nextDist(maxDist, rnd);
                GHUtility.setSpeed(60, true, true, accessEnc, speedEnc, graph.edge(from, to).setDistance(dist));
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
                    GHUtility.setSpeed(60, true, true, accessEnc, speedEnc, graph.edge(from, to).setDistance(dist));
                    LOGGER.trace("final EdgeIteratorState edge{} = graph.edge({},{},{},true);", edgeCounter++, from, to, dist);
                }
                if (j > 0) {
                    final double dist = nextDist(maxDist, rnd);
                    final int to = from + size - 1;
                    GHUtility.setSpeed(60, true, true, accessEnc, speedEnc, graph.edge(from, to).setDistance(dist));
                    LOGGER.trace("final EdgeIteratorState edge{} = graph.edge({},{},{},true);", edgeCounter++, from, to, dist);
                }
            }
        }
        graph.freeze();
        EdgeExplorer inExplorer = graph.createEdgeExplorer(AccessFilter.inEdges(accessEnc));
        EdgeExplorer outExplorer = graph.createEdgeExplorer(AccessFilter.outEdges(accessEnc));

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

        IntArrayList contractionOrder = getRandomIntegerSequence(graph.getNodes(), rnd);
        checkStrict = false;
        compareCHWithDijkstra(numQueries, contractionOrder.toArray());
    }

    @Test
    public void testFindPath_bug() {
        GHUtility.setSpeed(60, true, false, accessEnc, speedEnc, graph.edge(1, 2).setDistance(18.364000));
        GHUtility.setSpeed(60, true, true, accessEnc, speedEnc, graph.edge(1, 4).setDistance(29.814000));
        GHUtility.setSpeed(60, true, true, accessEnc, speedEnc, graph.edge(0, 2).setDistance(14.554000));
        GHUtility.setSpeed(60, true, true, accessEnc, speedEnc, graph.edge(1, 4).setDistance(29.819000));
        GHUtility.setSpeed(60, true, true, accessEnc, speedEnc, graph.edge(1, 3).setDistance(29.271000));
        setRestriction(3, 1, 2);
        graph.freeze();

        compareCHWithDijkstra(100, new int[]{1, 0, 3, 2, 4});
    }

    @Test
    public void testFindPath_bug2() {
        // 1 = 0 - 3 - 2 - 4
        GHUtility.setSpeed(60, true, true, accessEnc, speedEnc, graph.edge(0, 3).setDistance(24.001000));
        GHUtility.setSpeed(60, true, true, accessEnc, speedEnc, graph.edge(0, 1).setDistance(6.087000));
        GHUtility.setSpeed(60, true, true, accessEnc, speedEnc, graph.edge(0, 1).setDistance(6.067000));
        GHUtility.setSpeed(60, true, true, accessEnc, speedEnc, graph.edge(2, 3).setDistance(46.631000));
        GHUtility.setSpeed(60, true, true, accessEnc, speedEnc, graph.edge(2, 4).setDistance(46.184000));
        graph.freeze();

        compareCHWithDijkstra(1000, new int[]{1, 0, 3, 2, 4});
    }

    @Test
    public void testFindPath_loop() {
        //             3
        //            / \
        //           1   2
        //            \ /
        // 0 - 7 - 8 - 4 - 6 - 5
        GHUtility.setSpeed(60, true, false, accessEnc, speedEnc, graph.edge(0, 7).setDistance(1));
        GHUtility.setSpeed(60, true, false, accessEnc, speedEnc, graph.edge(7, 8).setDistance(1));
        GHUtility.setSpeed(60, true, false, accessEnc, speedEnc, graph.edge(8, 4).setDistance(1));
        GHUtility.setSpeed(60, true, false, accessEnc, speedEnc, graph.edge(4, 1).setDistance(1));
        GHUtility.setSpeed(60, true, false, accessEnc, speedEnc, graph.edge(1, 3).setDistance(1));
        GHUtility.setSpeed(60, true, false, accessEnc, speedEnc, graph.edge(3, 2).setDistance(1));
        GHUtility.setSpeed(60, true, false, accessEnc, speedEnc, graph.edge(2, 4).setDistance(1));
        GHUtility.setSpeed(60, true, false, accessEnc, speedEnc, graph.edge(4, 6).setDistance(1));
        GHUtility.setSpeed(60, true, false, accessEnc, speedEnc, graph.edge(6, 5).setDistance(1));
        setRestriction(8, 4, 6);
        graph.freeze();

        prepareCH(0, 1, 2, 3, 4, 5, 6, 7, 8);
        compareCHQueryWithDijkstra(0, 5);
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
        GHUtility.setSpeed(60, true, false, accessEnc, speedEnc, graph.edge(0, 3).setDistance(100));
        GHUtility.setSpeed(60, true, true, accessEnc, speedEnc, graph.edge(3, 4).setDistance(100));
        GHUtility.setSpeed(60, true, false, accessEnc, speedEnc, graph.edge(4, 2).setDistance(500));
        GHUtility.setSpeed(60, true, false, accessEnc, speedEnc, graph.edge(2, 3).setDistance(200));
        GHUtility.setSpeed(60, true, false, accessEnc, speedEnc, graph.edge(3, 1).setDistance(100));
        setRestriction(0, 3, 1);
        graph.freeze();
        chConfig = chConfigs.get(2);
        prepareCH(4, 0, 2, 3, 1);
        Path path = createAlgo().calcPath(0, 1);
        assertEquals(IntArrayList.from(0, 3, 4, 3, 1), path.calcNodes());
        compareCHQueryWithDijkstra(0, 1);
    }

    @Test
    public void testFindPath_calcTurnCostTime() {
        // here there will be a shortcut from 1 to 4 and when the path is unpacked it is important that
        // the turn costs are included at node 1 even though the unpacked original edge 1-0 might be in the
        // reverted state
        // 2-1--3
        //   |  |
        //   0->4
        EdgeIteratorState edge0 = GHUtility.setSpeed(60, true, true, accessEnc, speedEnc, graph.edge(1, 2).setDistance(1));
        EdgeIteratorState edge1 = GHUtility.setSpeed(60, true, false, accessEnc, speedEnc, graph.edge(0, 4).setDistance(1));
        EdgeIteratorState edge2 = GHUtility.setSpeed(60, true, true, accessEnc, speedEnc, graph.edge(4, 3).setDistance(1));
        EdgeIteratorState edge3 = GHUtility.setSpeed(60, true, true, accessEnc, speedEnc, graph.edge(1, 3).setDistance(1));
        EdgeIteratorState edge4 = GHUtility.setSpeed(60, true, true, accessEnc, speedEnc, graph.edge(1, 0).setDistance(1));
        setTurnCost(edge0, edge4, 1, 8);
        setRestriction(edge0, edge3, 1);
        graph.freeze();
        checkPath(IntArrayList.from(2, 1, 0, 4), 3, 8, 2, 4, new int[]{2, 0, 1, 3, 4});
    }

    @Test
    public void testFindPath_loopsMustAlwaysBeAccepted() {
        //     ---
        //     \ /
        // 0 -- 1 -- 2 -- 3
        EdgeIteratorState edge0 = GHUtility.setSpeed(60, true, true, accessEnc, speedEnc, graph.edge(0, 1).setDistance(1));
        EdgeIteratorState edge1 = GHUtility.setSpeed(60, true, false, accessEnc, speedEnc, graph.edge(1, 1).setDistance(1));
        EdgeIteratorState edge2 = GHUtility.setSpeed(60, true, true, accessEnc, speedEnc, graph.edge(1, 2).setDistance(1));
        EdgeIteratorState edge3 = GHUtility.setSpeed(60, true, false, accessEnc, speedEnc, graph.edge(2, 3).setDistance(1));
        setTurnCost(edge0, edge1, 1, 1);
        setRestriction(edge0, edge2, 1);
        graph.freeze();
        final IntArrayList expectedPath = IntArrayList.from(0, 1, 1, 2, 3);
        checkPath(expectedPath, 4, 1, 0, 3, new int[]{0, 2, 1, 3});
    }

    @Test
    public void testFindPath_compareWithDijkstra_zeroWeightLoops_random() {
        GHUtility.setSpeed(60, true, false, accessEnc, speedEnc, graph.edge(5, 3).setDistance(21.329000));
        GHUtility.setSpeed(60, true, false, accessEnc, speedEnc, graph.edge(4, 5).setDistance(29.126000));
        GHUtility.setSpeed(60, true, false, accessEnc, speedEnc, graph.edge(1, 0).setDistance(38.865000));
        GHUtility.setSpeed(60, true, false, accessEnc, speedEnc, graph.edge(1, 4).setDistance(80.005000));
        GHUtility.setSpeed(60, true, false, accessEnc, speedEnc, graph.edge(3, 1).setDistance(91.023000));
        // add loops with zero weight ...
        GHUtility.setSpeed(60, true, false, accessEnc, speedEnc, graph.edge(1, 1).setDistance(0.000000));
        GHUtility.setSpeed(60, true, false, accessEnc, speedEnc, graph.edge(1, 1).setDistance(0.000000));
        graph.freeze();
        automaticCompareCHWithDijkstra(100);
    }

    @Test
    public void testFindPath_compareWithDijkstra_zeroWeightLoops() {
        //                  /|
        // 0 -> 1 -> 2 -> 3 --
        //                | \|
        //                4
        GHUtility.setSpeed(60, true, false, accessEnc, speedEnc, graph.edge(0, 1).setDistance(1));
        GHUtility.setSpeed(60, true, false, accessEnc, speedEnc, graph.edge(1, 2).setDistance(1));
        GHUtility.setSpeed(60, true, false, accessEnc, speedEnc, graph.edge(2, 3).setDistance(1));
        GHUtility.setSpeed(60, true, false, accessEnc, speedEnc, graph.edge(3, 3).setDistance(0));
        GHUtility.setSpeed(60, true, false, accessEnc, speedEnc, graph.edge(3, 3).setDistance(0));
        GHUtility.setSpeed(60, true, false, accessEnc, speedEnc, graph.edge(3, 4).setDistance(1));
        graph.freeze();
        IntArrayList expectedPath = IntArrayList.from(0, 1, 2, 3, 4);
        checkPath(expectedPath, 4, 0, 0, 4, new int[]{2, 0, 4, 1, 3});
    }

    @Test
    void anotherDoubleZeroWeightLoop() {
        // taken from a random graph test. this one failed in a feature branch when the others did not.
        //         1 - 4 - 6 - 7
        //       /
        // 0 - 5 - 2
        //    oo
        // note there are two (directed) zero weight loops at node 5!
        GHUtility.setSpeed(60.000000, 60.000000, accessEnc, speedEnc, graph.edge(5, 1).setDistance(263.944000)); // edgeId=0
        GHUtility.setSpeed(120.000000, 120.000000, accessEnc, speedEnc, graph.edge(5, 2).setDistance(315.026000)); // edgeId=1
        GHUtility.setSpeed(40.000000, 40.000000, accessEnc, speedEnc, graph.edge(1, 4).setDistance(157.012000)); // edgeId=2
        GHUtility.setSpeed(45.000000, 45.000000, accessEnc, speedEnc, graph.edge(5, 0).setDistance(513.913000)); // edgeId=3
        GHUtility.setSpeed(15.000000, 15.000000, accessEnc, speedEnc, graph.edge(6, 4).setDistance(678.992000)); // edgeId=4
        GHUtility.setSpeed(60.000000, 0.000000, accessEnc, speedEnc, graph.edge(5, 5).setDistance(0.000000)); // edgeId=5
        GHUtility.setSpeed(40.000000, 40.000000, accessEnc, speedEnc, graph.edge(6, 7).setDistance(890.261000)); // edgeId=6
        GHUtility.setSpeed(90.000000, 0.000000, accessEnc, speedEnc, graph.edge(5, 5).setDistance(0.000000)); // edgeId=7
        graph.freeze();
        automaticCompareCHWithDijkstra(100);
    }

    @Test
    public void testFindPath_compareWithDijkstra_zeroWeightLoops_withTurnRestriction() {
        //                  /|
        // 0 -> 1 -> 2 -> 3 --
        //                | \|
        //                4
        GHUtility.setSpeed(60, true, false, accessEnc, speedEnc, graph.edge(0, 1).setDistance(1));
        GHUtility.setSpeed(60, true, false, accessEnc, speedEnc, graph.edge(1, 2).setDistance(1));
        EdgeIteratorState edge2 = GHUtility.setSpeed(60, true, false, accessEnc, speedEnc, graph.edge(2, 3).setDistance(1));
        EdgeIteratorState edge3 = GHUtility.setSpeed(60, true, false, accessEnc, speedEnc, graph.edge(3, 3).setDistance(0));
        EdgeIteratorState edge4 = GHUtility.setSpeed(60, true, false, accessEnc, speedEnc, graph.edge(3, 3).setDistance(0));
        EdgeIteratorState edge5 = GHUtility.setSpeed(60, true, false, accessEnc, speedEnc, graph.edge(3, 4).setDistance(1));
        setTurnCost(edge2, edge3, 3, 5);
        setTurnCost(edge2, edge4, 3, 4);
        setTurnCost(edge3, edge4, 3, 2);
        setRestriction(edge2, edge5, 3);
        graph.freeze();
        IntArrayList expectedPath = IntArrayList.from(0, 1, 2, 3, 3, 4);
        checkPath(expectedPath, 4, 4, 0, 4, new int[]{2, 0, 4, 1, 3});
    }

    @Test
    public void testFindPath_oneWayLoop() {
        //     o
        // 0-1-2-3-4
        GHUtility.setSpeed(60, true, false, accessEnc, speedEnc, graph.edge(0, 1).setDistance(1));
        GHUtility.setSpeed(60, true, false, accessEnc, speedEnc, graph.edge(1, 2).setDistance(1));
        GHUtility.setSpeed(60, true, false, accessEnc, speedEnc, graph.edge(2, 2).setDistance(1));
        GHUtility.setSpeed(60, true, false, accessEnc, speedEnc, graph.edge(2, 3).setDistance(1));
        GHUtility.setSpeed(60, true, false, accessEnc, speedEnc, graph.edge(3, 4).setDistance(1));
        setRestriction(1, 2, 3);
        graph.freeze();
        automaticPrepareCH();
        compareCHQueryWithDijkstra(0, 3);
        compareCHQueryWithDijkstra(1, 4);
        final Random rnd = new Random(System.nanoTime());
        for (int i = 0; i < 100; ++i) {
            compareCHQueryWithDijkstra(rnd.nextInt(graph.getNodes()), rnd.nextInt(graph.getNodes()));
        }
    }

    @Test
    public void testFindPath_loopEdge() {
        // 1-0
        // | |
        // 4-2o
        GHUtility.setSpeed(60, true, false, accessEnc, speedEnc, graph.edge(1, 0).setDistance(802.964000));
        GHUtility.setSpeed(60, true, true, accessEnc, speedEnc, graph.edge(1, 4).setDistance(615.195000));
        GHUtility.setSpeed(60, true, true, accessEnc, speedEnc, graph.edge(2, 2).setDistance(181.788000));
        GHUtility.setSpeed(60, true, true, accessEnc, speedEnc, graph.edge(0, 2).setDistance(191.996000));
        GHUtility.setSpeed(60, true, false, accessEnc, speedEnc, graph.edge(2, 4).setDistance(527.821000));
        setRestriction(0, 2, 4);
        setTurnCost(0, 2, 2, 3);
        setTurnCost(2, 2, 4, 4);
        graph.freeze();
        automaticPrepareCH();
        compareCHQueryWithDijkstra(0, 4);
    }

    @ParameterizedTest
    @ValueSource(strings = {DIJKSTRA_BI, ASTAR_BI})
    public void test_issue1593_full(String algo) {
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
        EdgeIteratorState edge0 = GHUtility.setSpeed(60, true, true, accessEnc, speedEnc, graph.edge(4, 3).setDistance(194.063000));
        EdgeIteratorState edge1 = GHUtility.setSpeed(60, true, true, accessEnc, speedEnc, graph.edge(1, 2).setDistance(525.106000));
        EdgeIteratorState edge2 = GHUtility.setSpeed(60, true, true, accessEnc, speedEnc, graph.edge(1, 2).setDistance(525.106000));
        EdgeIteratorState edge3 = GHUtility.setSpeed(60, true, false, accessEnc, speedEnc, graph.edge(4, 1).setDistance(703.778000));
        EdgeIteratorState edge4 = GHUtility.setSpeed(60, true, true, accessEnc, speedEnc, graph.edge(2, 4).setDistance(400.509000));
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

        List<Snap> snaps = new ArrayList<>(points.size());
        for (GHPoint point : points) {
            snaps.add(index.findClosest(point.getLat(), point.getLon(), EdgeFilter.ALL_EDGES));
        }

        automaticPrepareCH();
        QueryGraph queryGraph = QueryGraph.create(graph, snaps);
        RoutingAlgorithm chAlgo = new CHRoutingAlgorithmFactory(chGraph, queryGraph).createAlgo(new PMap().putObject(ALGORITHM, algo));
        Path path = chAlgo.calcPath(5, 6);
        // there should not be a path from 5 to 6, because first we cannot go directly 5-4-6, so we need to go left
        // to 8. then at 2 we cannot go on edge 1 because of another turn restriction, but we can go on edge 2 so we
        // travel via the virtual node 7 to node 1. From there we cannot go to 6 because of the one-way so we go back
        // to node 2 (no u-turn because of the duplicate edge) on edge1. And this is were the journey ends: we cannot
        // go to 8 because of the turn restriction from edge1 to edge4 -> there should not be a path!
        assertFalse(path.isFound(), "there should not be a path, but found: " + path.calcNodes());
    }

    @ParameterizedTest
    @ValueSource(strings = {DIJKSTRA_BI, ASTAR_BI})
    public void test_issue_1593_simple(String algo) {
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
        EdgeIteratorState edge0 = GHUtility.setSpeed(60, true, true, accessEnc, speedEnc, graph.edge(3, 1).setDistance(10));
        EdgeIteratorState edge1 = GHUtility.setSpeed(60, true, true, accessEnc, speedEnc, graph.edge(2, 3).setDistance(10));
        GHUtility.setSpeed(60, true, true, accessEnc, speedEnc, graph.edge(3, 0).setDistance(10));
        GHUtility.setSpeed(60, true, true, accessEnc, speedEnc, graph.edge(0, 5).setDistance(10));
        GHUtility.setSpeed(60, true, true, accessEnc, speedEnc, graph.edge(5, 4).setDistance(10));
        // cannot go, 2-3-1
        setRestriction(edge1, edge0, 3);
        graph.freeze();
        prepareCH(0, 1, 2, 3, 4, 5);
        assertEquals(5, chGraph.getBaseGraph().getEdges());
        assertEquals(7, chGraph.getEdges(), "expected two shortcuts: 3->5 and 5->3");
        // there should be no path from 2 to 1, because of the turn restriction and because u-turns are not allowed
        assertFalse(findPathUsingDijkstra(2, 1).isFound());
        compareCHQueryWithDijkstra(2, 1);

        // we have to pay attention when there are virtual nodes: turning from the shortcut 3-5 onto the
        // virtual edge 5-x should be forbidden.
        LocationIndexTree index = new LocationIndexTree(graph, new RAMDirectory());
        index.prepareIndex();
        Snap snap = index.findClosest(0.1, 0.15, EdgeFilter.ALL_EDGES);
        QueryGraph queryGraph = QueryGraph.create(graph, snap);
        assertEquals(1, queryGraph.getNodes() - chGraph.getNodes(), "expected one virtual node");
        QueryRoutingCHGraph routingCHGraph = new QueryRoutingCHGraph(chGraph, queryGraph);
        RoutingAlgorithm chAlgo = new CHRoutingAlgorithmFactory(routingCHGraph).createAlgo(new PMap().putObject(ALGORITHM, algo));
        Path path = chAlgo.calcPath(2, 1);
        assertFalse(path.isFound(), "no path should be found, but found " + path.calcNodes());
    }

    @ParameterizedTest
    @ValueSource(strings = {DIJKSTRA_BI, ASTAR_BI})
    public void testRouteViaVirtualNode(String algo) {
        //   3
        // 0-x-1-2
        GHUtility.setSpeed(60, true, false, accessEnc, speedEnc, graph.edge(0, 1).setDistance(0));
        GHUtility.setSpeed(60, true, false, accessEnc, speedEnc, graph.edge(1, 2).setDistance(0));
        updateDistancesFor(graph, 0, 0.00, 0.00);
        updateDistancesFor(graph, 1, 0.02, 0.02);
        updateDistancesFor(graph, 2, 0.03, 0.03);
        graph.freeze();
        automaticPrepareCH();
        LocationIndexTree index = new LocationIndexTree(graph, new RAMDirectory());
        index.prepareIndex();
        Snap snap = index.findClosest(0.01, 0.01, EdgeFilter.ALL_EDGES);
        QueryGraph queryGraph = QueryGraph.create(graph, snap);
        assertEquals(3, snap.getClosestNode());
        assertEquals(0, snap.getClosestEdge().getEdge());
        RoutingAlgorithm chAlgo = new CHRoutingAlgorithmFactory(chGraph, queryGraph).createAlgo(new PMap().putObject(ALGORITHM, algo));
        Path path = chAlgo.calcPath(0, 2);
        assertTrue(path.isFound(), "it should be possible to route via a virtual node, but no path found");
        assertEquals(IntArrayList.from(0, 3, 1, 2), path.calcNodes());
        assertEquals(DistancePlaneProjection.DIST_PLANE.calcDist(0.00, 0.00, 0.03, 0.03), path.getDistance(), 1.e-1);
    }

    @ParameterizedTest
    @ValueSource(strings = {DIJKSTRA_BI, ASTAR_BI})
    public void testRouteViaVirtualNode_withAlternative(String algo) {
        //   3
        // 0-x-1
        //  \  |
        //   \-2
        GHUtility.setSpeed(60, true, true, accessEnc, speedEnc, graph.edge(0, 1).setDistance(1));
        GHUtility.setSpeed(60, true, true, accessEnc, speedEnc, graph.edge(1, 2).setDistance(1));
        GHUtility.setSpeed(60, true, true, accessEnc, speedEnc, graph.edge(2, 0).setDistance(1));
        updateDistancesFor(graph, 0, 0.01, 0.00);
        updateDistancesFor(graph, 1, 0.01, 0.02);
        updateDistancesFor(graph, 2, 0.00, 0.02);
        graph.freeze();
        automaticPrepareCH();
        LocationIndexTree index = new LocationIndexTree(graph, new RAMDirectory());
        index.prepareIndex();
        Snap snap = index.findClosest(0.01, 0.01, EdgeFilter.ALL_EDGES);
        QueryGraph queryGraph = QueryGraph.create(graph, snap);
        assertEquals(3, snap.getClosestNode());
        assertEquals(0, snap.getClosestEdge().getEdge());
        QueryRoutingCHGraph routingCHGraph = new QueryRoutingCHGraph(chGraph, queryGraph);
        RoutingAlgorithm chAlgo = new CHRoutingAlgorithmFactory(routingCHGraph).createAlgo(new PMap().putObject(ALGORITHM, algo));
        Path path = chAlgo.calcPath(1, 0);
        assertEquals(IntArrayList.from(1, 3, 0), path.calcNodes());
    }

    @ParameterizedTest
    @ValueSource(strings = {DIJKSTRA_BI, ASTAR_BI})
    public void testFiniteUTurnCost_virtualViaNode(String algo) {
        // if there is an extra virtual node it can be possible to do a u-turn that otherwise would not be possible
        // and so there can be a difference between CH and non-CH... therefore u-turns at virtual nodes are forbidden
        // 4->3->2->1-x-0
        //          |
        //          5->6
        GHUtility.setSpeed(60, true, false, accessEnc, speedEnc, graph.edge(4, 3).setDistance(0));
        GHUtility.setSpeed(60, true, false, accessEnc, speedEnc, graph.edge(3, 2).setDistance(0));
        GHUtility.setSpeed(60, true, false, accessEnc, speedEnc, graph.edge(2, 1).setDistance(0));
        GHUtility.setSpeed(60, true, true, accessEnc, speedEnc, graph.edge(1, 0).setDistance(0));
        GHUtility.setSpeed(60, true, false, accessEnc, speedEnc, graph.edge(1, 5).setDistance(0));
        GHUtility.setSpeed(60, true, false, accessEnc, speedEnc, graph.edge(5, 6).setDistance(0));
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
        chConfig = chConfigs.get(2);
        prepareCH(0, 1, 2, 3, 4, 5, 6);
        LocationIndexTree index = new LocationIndexTree(graph, new RAMDirectory());
        index.prepareIndex();
        GHPoint virtualPoint = new GHPoint(0.1, 0.35);
        Snap snap = index.findClosest(virtualPoint.lat, virtualPoint.lon, EdgeFilter.ALL_EDGES);
        QueryGraph chQueryGraph = QueryGraph.create(graph, snap);
        assertEquals(3, snap.getClosestEdge().getEdge());
        QueryRoutingCHGraph routingCHGraph = new QueryRoutingCHGraph(chGraph, chQueryGraph);
        RoutingAlgorithm chAlgo = new CHRoutingAlgorithmFactory(routingCHGraph).createAlgo(new PMap().putObject(ALGORITHM, algo));
        Path path = chAlgo.calcPath(4, 6);
        assertTrue(path.isFound());
        assertEquals(IntArrayList.from(4, 3, 2, 1, 0, 1, 5, 6), path.calcNodes());

        Snap snap2 = index.findClosest(virtualPoint.lat, virtualPoint.lon, EdgeFilter.ALL_EDGES);
        QueryGraph queryGraph = QueryGraph.create(graph, snap2);
        assertEquals(3, snap2.getClosestEdge().getEdge());
        Weighting w = queryGraph.wrapWeighting(chConfig.getWeighting());
        Dijkstra dijkstra = new Dijkstra(queryGraph, w, TraversalMode.EDGE_BASED);
        Path dijkstraPath = dijkstra.calcPath(4, 6);
        assertEquals(IntArrayList.from(4, 3, 2, 1, 7, 0, 7, 1, 5, 6), dijkstraPath.calcNodes());
        assertEquals(dijkstraPath.getWeight(), path.getWeight(), 1.e-2);
        assertEquals(dijkstraPath.getDistance(), path.getDistance(), 1.e-2);
        assertEquals(dijkstraPath.getTime(), path.getTime(), 5);
    }

    @ParameterizedTest
    @ValueSource(strings = {DIJKSTRA_BI, ASTAR_BI})
    public void test_astar_issue2061(String algo) {
        // here the direct path 0-2-3-4-5 is clearly the shortest, however there was a bug in the a-star(-like)
        // algo: first the non-optimal path 0-1-5 is found, but before we find the actual shortest path we explore
        // node 6 during the forward search. the path 0-6-x-5 cannot possibly be the shortest path because 0-6-5
        // is already worse than 0-1-5, even if there was a beeline link from 6 to 5. the problem was that then we
        // cancelled the entire fwd search instead of simply stalling node 6.
        //       |-------1-|
        // 7-6---0---2-3-4-5
        graph.edge(0, 1).set(accessEnc, true).set(speedEnc, 60);
        graph.edge(1, 5).set(accessEnc, true).set(speedEnc, 60);
        graph.edge(0, 2).set(accessEnc, true).set(speedEnc, 60);
        graph.edge(2, 3).set(accessEnc, true).set(speedEnc, 60);
        graph.edge(3, 4).set(accessEnc, true).set(speedEnc, 60);
        graph.edge(4, 5).set(accessEnc, true).set(speedEnc, 60);
        graph.edge(0, 6).set(accessEnc, true).set(speedEnc, 60);
        graph.edge(6, 7).set(accessEnc, true).set(speedEnc, 60);
        updateDistancesFor(graph, 0, 46.5, 9.7);
        updateDistancesFor(graph, 1, 46.9, 9.8);
        updateDistancesFor(graph, 2, 46.7, 9.7);
        updateDistancesFor(graph, 4, 46.9, 9.7);
        updateDistancesFor(graph, 3, 46.8, 9.7);
        updateDistancesFor(graph, 5, 47.0, 9.7);
        updateDistancesFor(graph, 6, 46.3, 9.7);
        updateDistancesFor(graph, 7, 46.2, 9.7);
        graph.freeze();
        automaticPrepareCH();
        RoutingAlgorithm chAlgo = new CHRoutingAlgorithmFactory(chGraph).createAlgo(new PMap().putObject(ALGORITHM, algo));
        Path path = chAlgo.calcPath(0, 5);
        assertEquals(IntArrayList.from(0, 2, 3, 4, 5), path.calcNodes());
    }

    @Test
    void testZeroUTurnCosts_atBarrier_issue2564() {
        // lvl: 0 3 2 4 5 1
        //  nd: 0-1-2-3-4-5
        GHUtility.setSpeed(60, 60, accessEnc, speedEnc, graph.edge(0, 1).setDistance(100));
        // the original bug was sometimes hidden depending on the exact distance, so we use these odd numbers here
        GHUtility.setSpeed(60, 60, accessEnc, speedEnc, graph.edge(1, 2).setDistance(7.336));
        GHUtility.setSpeed(60, 60, accessEnc, speedEnc, graph.edge(2, 3).setDistance(10.161));
        // a zero distance edge (like a passable barrier)!
        GHUtility.setSpeed(60, 60, accessEnc, speedEnc, graph.edge(3, 4).setDistance(0));
        GHUtility.setSpeed(60, 60, accessEnc, speedEnc, graph.edge(4, 5).setDistance(100));
        graph.freeze();
        // u-turn costs are zero!
        chConfig = chConfigs.get(1);
        // contracting node 2 is supposed to create the 1-3 shortcut for both directions, but before fixing #2564
        // we accepted 1-2-3-4-3 as a witness path and thus no path was found
        prepareCH(0, 5, 2, 1, 3, 4);
        compareCHQueryWithDijkstra(0, 5);
    }

    @Test
    void testBestFwdBwdEntryUpdate() {
        // 2-3
        // | |
        // 0-4-1
        GHUtility.setSpeed(60, 60, accessEnc, speedEnc, graph.edge(2, 0).setDistance(800.22));
        GHUtility.setSpeed(60, 60, accessEnc, speedEnc, graph.edge(3, 4).setDistance(478.84));
        GHUtility.setSpeed(60, 60, accessEnc, speedEnc, graph.edge(0, 4).setDistance(547.08));
        GHUtility.setSpeed(60, 60, accessEnc, speedEnc, graph.edge(4, 1).setDistance(288.95));
        GHUtility.setSpeed(60, 60, accessEnc, speedEnc, graph.edge(2, 3).setDistance(90));
        graph.freeze();
        prepareCH(1, 3, 0, 2, 4);
        compareCHQueryWithDijkstra(1, 2);
    }

    @Test
    void testEdgeKeyBug() {
        // 1 - 2 - 0 - 4
        //          \ /
        //           3
        GHUtility.setSpeed(60, 60, accessEnc, speedEnc, graph.edge(0, 3).setDistance(100)); // edgeId=0
        GHUtility.setSpeed(60, 60, accessEnc, speedEnc, graph.edge(4, 3).setDistance(100)); // edgeId=1
        GHUtility.setSpeed(60, 60, accessEnc, speedEnc, graph.edge(0, 4).setDistance(100)); // edgeId=2
        GHUtility.setSpeed(60, 60, accessEnc, speedEnc, graph.edge(1, 2).setDistance(100)); // edgeId=3
        GHUtility.setSpeed(60, 60, accessEnc, speedEnc, graph.edge(0, 2).setDistance(100)); // edgeId=4
        graph.freeze();
        prepareCH(2, 0, 1, 3, 4);
        assertEquals(2, chGraph.getShortcuts());
        RoutingCHEdgeIteratorState chEdge = chGraph.getEdgeIteratorState(6, 4);
        assertEquals(3, chEdge.getBaseNode());
        assertEquals(4, chEdge.getAdjNode());
        assertEquals(2, chEdge.getSkippedEdge1());
        assertEquals(0, chEdge.getSkippedEdge2());
        // the first edge is 4-0 (edge 2 against the storage direction) -> key is 2*2+1=5
        assertEquals(5, chEdge.getOrigEdgeKeyFirst());
        // the second is 0-3 (edge 0 in storage direction) -> key is 2*0=0
        assertEquals(0, chEdge.getOrigEdgeKeyLast());
        compareCHQueryWithDijkstra(1, 3);
    }

    /**
     * This test runs on a random graph with random turn costs and a predefined (but random) contraction order.
     * It often produces exotic conditions that are hard to anticipate beforehand.
     * when it fails use {@link GHUtility#printGraphForUnitTest} to extract the graph and reproduce the error.
     */
    @RepeatedTest(10)
    public void testFindPath_random_compareWithDijkstra() {
        long seed = System.nanoTime();
        LOGGER.info("Seed for testFindPath_random_compareWithDijkstra: {}", seed);
        compareWithDijkstraOnRandomGraph(seed);
    }

    @RepeatedTest(10)
    public void testFindPath_random_compareWithDijkstra_finiteUTurnCost() {
        long seed = System.nanoTime();
        LOGGER.info("Seed for testFindPath_random_compareWithDijkstra_finiteUTurnCost: {}, using weighting: {}", seed, chConfig.getWeighting());
        chConfig = chConfigs.get(2 + new Random(seed).nextInt(chConfigs.size() - 2));
        compareWithDijkstraOnRandomGraph(seed);
    }

    @RepeatedTest(10)
    public void testFindPath_random_compareWithDijkstra_zeroUTurnCost() {
        long seed = System.nanoTime();
        LOGGER.info("Seed for testFindPath_random_compareWithDijkstra_zeroUTurnCost: {}, using weighting: {}", seed, chConfig.getWeighting());
        chConfig = chConfigs.get(1);
        compareWithDijkstraOnRandomGraph(seed);
    }

    private void compareWithDijkstraOnRandomGraph(long seed) {
        final Random rnd = new Random(seed);
        // for larger graphs preparation takes much longer the higher the degree is!
        GHUtility.buildRandomGraph(graph, rnd, 20, 3.0, true, true,
                accessEnc, speedEnc, null, 0.7, 0.9, 0.8);
        GHUtility.addRandomTurnCosts(graph, seed, accessEnc, turnCostEnc, maxCost, turnCostStorage);
        graph.freeze();
        checkStrict = false;
        IntArrayList contractionOrder = getRandomIntegerSequence(graph.getNodes(), rnd);
        compareCHWithDijkstra(100, contractionOrder.toArray());
    }

    /**
     * same as {@link #testFindPath_random_compareWithDijkstra()}, but using automatic node priority calculation
     */
    @RepeatedTest(10)
    public void testFindPath_heuristic_compareWithDijkstra() {
        long seed = System.nanoTime();
        LOGGER.info("Seed for testFindPath_heuristic_compareWithDijkstra: {}", seed);
        compareWithDijkstraOnRandomGraph_heuristic(seed);
    }

    @RepeatedTest(10)
    public void testFindPath_heuristic_compareWithDijkstra_finiteUTurnCost() {
        long seed = System.nanoTime();
        LOGGER.info("Seed for testFindPath_heuristic_compareWithDijkstra_finiteUTurnCost: {}, using weighting: {}", seed, chConfig.getWeighting());
        chConfig = chConfigs.get(2 + new Random(seed).nextInt(chConfigs.size() - 2));
        compareWithDijkstraOnRandomGraph_heuristic(seed);
    }

    private void compareWithDijkstraOnRandomGraph_heuristic(long seed) {
        GHUtility.buildRandomGraph(graph, new Random(seed), 20, 3.0, true, true,
                accessEnc, speedEnc, null, 0.7, 0.9, 0.8);
        GHUtility.addRandomTurnCosts(graph, seed, accessEnc, turnCostEnc, maxCost, turnCostStorage);
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
        IntArrayList contractionOrder = getRandomIntegerSequence(graph.getNodes(), new Random());
        checkPath(expectedPath, expectedWeight, expectedTurnCosts, from, to, contractionOrder.toArray());
    }

    private void checkPath(IntArrayList expectedPath, int expectedEdgeWeight, int expectedTurnCosts, int from, int to, int[] contractionOrder) {
        checkPathUsingDijkstra(expectedPath, expectedEdgeWeight, expectedTurnCosts, from, to);
        checkPathUsingCH(expectedPath, expectedEdgeWeight, expectedTurnCosts, from, to, contractionOrder);
    }

    private void checkPathUsingDijkstra(IntArrayList expectedPath, int expectedEdgeWeight, int expectedTurnCosts, int from, int to) {
        Path dijkstraPath = findPathUsingDijkstra(from, to);
        int expectedWeight = expectedEdgeWeight + expectedTurnCosts;
        int expectedDistance = expectedEdgeWeight;
        int expectedTime = expectedEdgeWeight * 60 + expectedTurnCosts * 1000;
        assertEquals(expectedPath, dijkstraPath.calcNodes(), "Normal Dijkstra did not find expected path.");
        assertEquals(expectedWeight, dijkstraPath.getWeight(), 1.e-6, "Normal Dijkstra did not calculate expected weight.");
        assertEquals(expectedDistance, dijkstraPath.getDistance(), 1.e-6, "Normal Dijkstra did not calculate expected distance.");
        assertEquals(expectedTime, dijkstraPath.getTime(), 2, "Normal Dijkstra did not calculate expected time.");
    }

    private void checkPathUsingCH(IntArrayList expectedPath, int expectedEdgeWeight, int expectedTurnCosts, int from, int to, int[] contractionOrder) {
        Path chPath = findPathUsingCH(from, to, contractionOrder);
        int expectedWeight = expectedEdgeWeight + expectedTurnCosts;
        int expectedDistance = expectedEdgeWeight;
        int expectedTime = expectedEdgeWeight * 60 + expectedTurnCosts * 1000;
        assertEquals(expectedPath, chPath.calcNodes(), "Contraction Hierarchies did not find expected path. contraction order=" + Arrays.toString(contractionOrder));
        assertEquals(expectedWeight, chPath.getWeight(), 1.e-6, "Contraction Hierarchies did not calculate expected weight.");
        assertEquals(expectedDistance, chPath.getDistance(), 1.e-6, "Contraction Hierarchies did not calculate expected distance.");
        assertEquals(expectedTime, chPath.getTime(), 2, "Contraction Hierarchies did not calculate expected time.");
    }

    private Path findPathUsingDijkstra(int from, int to) {
        Weighting w = graph.wrapWeighting(chConfig.getWeighting());
        Dijkstra dijkstra = new Dijkstra(graph, w, TraversalMode.EDGE_BASED);
        return dijkstra.calcPath(from, to);
    }

    private Path findPathUsingCH(int from, int to, int[] contractionOrder) {
        prepareCH(contractionOrder);
        RoutingAlgorithm chAlgo = createAlgo();
        return chAlgo.calcPath(from, to);
    }

    private void prepareCH(int... contractionOrder) {
        LOGGER.debug("Calculating CH with contraction order {}", contractionOrder);
        if (!graph.isFrozen())
            graph.freeze();
        NodeOrderingProvider nodeOrderingProvider = NodeOrderingProvider.fromArray(contractionOrder);
        PrepareContractionHierarchies ch = PrepareContractionHierarchies.fromGraph(graph, chConfig)
                .useFixedNodeOrdering(nodeOrderingProvider);
        PrepareContractionHierarchies.Result res = ch.doWork();
        chGraph = RoutingCHGraphImpl.fromGraph(graph, res.getCHStorage(), res.getCHConfig());
    }

    private void automaticPrepareCH() {
        PMap pMap = new PMap();
        pMap.putObject(PERIODIC_UPDATES, 20);
        pMap.putObject(LAST_LAZY_NODES_UPDATES, 100);
        pMap.putObject(NEIGHBOR_UPDATES, 4);
        pMap.putObject(LOG_MESSAGES, 10);
        PrepareContractionHierarchies ch = PrepareContractionHierarchies.fromGraph(graph, chConfig);
        ch.setParams(pMap);
        PrepareContractionHierarchies.Result res = ch.doWork();
        chGraph = RoutingCHGraphImpl.fromGraph(graph, res.getCHStorage(), res.getCHConfig());
    }

    private void automaticCompareCHWithDijkstra(int numQueries) {
        long seed = System.nanoTime();
        LOGGER.info("Seed used to create random routing queries: {}", seed);
        final Random rnd = new Random(seed);
        automaticPrepareCH();
        for (int i = 0; i < numQueries; ++i) {
            compareCHQueryWithDijkstra(rnd.nextInt(graph.getNodes()), rnd.nextInt(graph.getNodes()));
        }
    }

    private void compareCHWithDijkstra(int numQueries, int[] contractionOrder) {
        long seed = System.nanoTime();
        LOGGER.info("Seed used to create random routing queries: {}", seed);
        final Random rnd = new Random(seed);
        prepareCH(contractionOrder);
        for (int i = 0; i < numQueries; ++i) {
            compareCHQueryWithDijkstra(rnd.nextInt(graph.getNodes()), rnd.nextInt(graph.getNodes()));
        }
    }

    private void compareCHQueryWithDijkstra(int from, int to) {
        Path dijkstraPath = findPathUsingDijkstra(from, to);
        RoutingAlgorithm chAlgo = createAlgo();
        Path chPath = chAlgo.calcPath(from, to);
        boolean algosDisagree = Math.abs(dijkstraPath.getWeight() - chPath.getWeight()) > 1.e-2;
        if (checkStrict) {
            algosDisagree = algosDisagree
                    || Math.abs(dijkstraPath.getDistance() - chPath.getDistance()) > 1.e-2
                    || Math.abs(dijkstraPath.getTime() - chPath.getTime()) > 1;
        }
        if (algosDisagree) {
            System.out.println("Graph that produced error:");
            GHUtility.printGraphForUnitTest(graph, accessEnc, speedEnc);
            fail("Dijkstra and CH did not find equal shortest paths for route from " + from + " to " + to + "\n" +
                    " dijkstra: weight: " + dijkstraPath.getWeight() + ", distance: " + dijkstraPath.getDistance() +
                    ", time: " + dijkstraPath.getTime() + ", nodes: " + dijkstraPath.calcNodes() + "\n" +
                    "       ch: weight: " + chPath.getWeight() + ", distance: " + chPath.getDistance() +
                    ", time: " + chPath.getTime() + ", nodes: " + chPath.calcNodes());
        }
    }

    private RoutingAlgorithm createAlgo() {
        // use dijkstra since we do not have coordinates in most tests
        return new CHRoutingAlgorithmFactory(chGraph).createAlgo(new PMap().putObject(ALGORITHM, DIJKSTRA_BI));
    }

    private IntArrayList getRandomIntegerSequence(int nodes, Random rnd) {
        return ArrayUtil.shuffle(ArrayUtil.iota(nodes), rnd);
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
        graph.getTurnCostStorage().set(((EncodedValueLookup) encodingManager).getDecimalEncodedValue(TurnCost.key("car")), inEdge.getEdge(), viaNode, outEdge.getEdge(), Double.POSITIVE_INFINITY);
    }

    private void setTurnCost(int from, int via, int to, double cost) {
        setTurnCost(getEdge(from, via), getEdge(via, to), via, cost);
    }

    private void setTurnCost(EdgeIteratorState inEdge, EdgeIteratorState outEdge, int viaNode, double costs) {
        graph.getTurnCostStorage().set(((EncodedValueLookup) encodingManager).getDecimalEncodedValue(TurnCost.key("car")), inEdge.getEdge(), viaNode, outEdge.getEdge(), costs);
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
