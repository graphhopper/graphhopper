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
import com.graphhopper.routing.ch.PreparationWeighting;
import com.graphhopper.routing.ch.PrepareEncoder;
import com.graphhopper.routing.util.CarFlagEncoder;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.routing.util.LevelEdgeFilter;
import com.graphhopper.routing.weighting.ShortestWeighting;
import com.graphhopper.routing.weighting.TurnWeighting;
import com.graphhopper.routing.weighting.Weighting;
import com.graphhopper.storage.CHGraph;
import com.graphhopper.storage.GraphBuilder;
import com.graphhopper.storage.GraphHopperStorage;
import com.graphhopper.storage.TurnCostExtension;
import com.graphhopper.util.CHEdgeIteratorState;
import com.graphhopper.util.EdgeIteratorState;
import com.graphhopper.util.GHUtility;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

/**
 * Tests the correctness of the contraction hierarchies query in the presence of turn costs.
 * The graph preparation is done manually here and the tests try to focus on border cases that have to be covered
 * by the query algorithm correctly.
 */
@RunWith(Parameterized.class)
public class CHQueryWithTurnCostsTest {
    private final int maxCost = 10;
    private final CarFlagEncoder encoder = new CarFlagEncoder(5, 5, maxCost);
    private final EncodingManager encodingManager = EncodingManager.create(encoder);
    private final Weighting weighting = new ShortestWeighting(encoder);
    private final GraphHopperStorage graph = new GraphBuilder(encodingManager).setCHGraph(weighting).setEdgeBasedCH(true).create();
    private final TurnCostExtension turnCostExtension = (TurnCostExtension) graph.getExtension();
    private final CHGraph chGraph = graph.getGraph(CHGraph.class);
    private String algoString;

    @Parameterized.Parameters(name = "{0}")
    public static Object[] parameters() {
        return new Object[]{"astar", "dijkstra"};
    }

    public CHQueryWithTurnCostsTest(String algoString) {
        this.algoString = algoString;
    }

    @Test
    public void testFindPathWithTurnCosts_bidirected_no_shortcuts_smallGraph() {
        // some special cases where from=to, or start and target edges are the same
        // 1 -- 0 -- 2
        graph.edge(1, 0, 3, true);
        graph.edge(0, 2, 5, true);
        addTurnCost(1, 0, 2, 3);
        graph.freeze();

        // contraction yields no shortcuts for edge based case (at least without u-turns).
        setLevelEqualToNodeIdForAllNodes();

        for (int i = 0; i < 3; ++i) {
            testPathCalculation(i, i, 0, IntArrayList.from(i));
        }
        testPathCalculation(1, 2, 11, IntArrayList.from(1, 0, 2));
        testPathCalculation(2, 1, 8, IntArrayList.from(2, 0, 1));
        testPathCalculation(0, 1, 3, IntArrayList.from(0, 1));
        testPathCalculation(0, 2, 5, IntArrayList.from(0, 2));
        testPathCalculation(1, 0, 3, IntArrayList.from(1, 0));
        testPathCalculation(2, 0, 5, IntArrayList.from(2, 0));
    }

    @Test
    public void testFindPathWithTurnCosts_bidirected_no_shortcuts() {
        // 0 -- 2 -- 4 -- 6 -- 5 -- 3 -- 1
        graph.edge(0, 2, 3, true);
        graph.edge(2, 4, 2, true);
        graph.edge(4, 6, 7, true);
        graph.edge(6, 5, 9, true);
        graph.edge(5, 3, 1, true);
        graph.edge(3, 1, 4, true);
        addTurnCost(0, 2, 4, 3);
        addTurnCost(4, 6, 5, 6);
        addTurnCost(5, 6, 4, 2);
        addTurnCost(5, 3, 1, 5);
        graph.freeze();

        // contraction yields no shortcuts
        setLevelEqualToNodeIdForAllNodes();

        testPathCalculation(0, 1, 40, IntArrayList.from(0, 2, 4, 6, 5, 3, 1));
        testPathCalculation(1, 0, 28, IntArrayList.from(1, 3, 5, 6, 4, 2, 0));
        testPathCalculation(4, 3, 23, IntArrayList.from(4, 6, 5, 3));
        testPathCalculation(0, 0, 0, IntArrayList.from(0));
        testPathCalculation(4, 4, 0, IntArrayList.from(4));
    }

    @Test
    public void testFindPathWithTurnCosts_directed_single_shortcut() {
        //    2     3
        //   /5\   /1\
        //  /   \2/   \
        // 1     0     4
        graph.edge(1, 2, 4, false);
        graph.edge(2, 0, 2, false);
        graph.edge(0, 3, 3, false);
        graph.edge(3, 4, 2, false);
        addTurnCost(1, 2, 0, 5);
        addTurnCost(2, 0, 3, 2);
        addTurnCost(0, 3, 4, 1);
        graph.freeze();

        // only when node 0 is contracted a shortcut is added
        addShortcut(2, 3, 1, 2, 1, 2, 7);
        setLevelEqualToNodeIdForAllNodes();

        // when we are searching a path to the highest level node, the backward search will not expand any edges
        testPathCalculation(1, 4, 19, IntArrayList.from(1, 2, 0, 3, 4));
        testPathCalculation(2, 4, 10, IntArrayList.from(2, 0, 3, 4));
        testPathCalculation(0, 4, 6, IntArrayList.from(0, 3, 4));

        // when we search a path to or start the search from a low level node both forward and backward searches run
        testPathCalculation(1, 0, 11, IntArrayList.from(1, 2, 0));
        testPathCalculation(0, 4, 6, IntArrayList.from(0, 3, 4));
    }

    @Test
    public void testFindPathWithTurnCosts_directed_single_shortcut_fwdSearchStopsQuickly() {
        //     0
        //    / \
        // 1-3-s-2-4
        graph.edge(1, 3, 2, false);
        graph.edge(3, 0, 3, false);
        graph.edge(0, 2, 1, false);
        graph.edge(2, 4, 3, false);
        graph.freeze();

        addTurnCost(1, 3, 0, 2);
        addTurnCost(0, 2, 4, 4);

        // from contracting node 0
        addShortcut(3, 2, 1, 2, 1, 2, 4);
        setLevelEqualToNodeIdForAllNodes();

        testPathCalculation(1, 4, 15, IntArrayList.from(1, 3, 0, 2, 4));
    }

    @Test
    public void testFindPathWithTurnCosts_directed_two_shortcuts() {
        //    3     0
        //   /5\   /1\
        //  /   \2/   \
        // 2     1     4
        graph.edge(2, 3, 4, false);
        graph.edge(3, 1, 2, false);
        graph.edge(1, 0, 3, false);
        graph.edge(0, 4, 2, false);
        addTurnCost(2, 3, 1, 5);
        addTurnCost(3, 1, 0, 2);
        addTurnCost(1, 0, 4, 1);
        graph.freeze();

        // contraction of node 0 and 1 each yield a single shortcut
        addShortcut(1, 4, 2, 3, 2, 3, 6);
        addShortcut(3, 4, 1, 3, 1, 4, 10);
        setLevelEqualToNodeIdForAllNodes();

        // the turn costs have to be accounted for also when the shortcuts are used
        testPathCalculation(2, 4, 19, IntArrayList.from(2, 3, 1, 0, 4));
        testPathCalculation(1, 4, 6, IntArrayList.from(1, 0, 4));
        testPathCalculation(2, 0, 16, IntArrayList.from(2, 3, 1, 0));
        testPathCalculation(3, 4, 10, IntArrayList.from(3, 1, 0, 4));
        testPathCalculation(2, 1, 11, IntArrayList.from(2, 3, 1));
    }

    @Test
    public void testFindPath_directConnectionIsNotTheBestPath() {
        // this case is interesting because there is an expensive edge going from the source to the target directly
        // 0 --------\
        // |         |
        // v         v
        // 2 -> 3 -> 1
        graph.edge(0, 2, 3, false);
        graph.edge(2, 3, 2, false);
        graph.edge(3, 1, 9, false);
        graph.edge(0, 1, 50, false);
        addTurnCost(2, 3, 1, 4);
        graph.freeze();

        // no shortcuts here
        setLevelEqualToNodeIdForAllNodes();
        testPathCalculation(0, 1, 18, IntArrayList.from(0, 2, 3, 1));
    }

    @Test
    public void testFindPath_upwardSearchRunsIntoTarget() {
        // this case is interesting because one possible path runs from 0 to 4 directly (the backward search does not
        // contribute anything in this case), but this path is not as good as the one via node 5
        // 0 -> 1 -> 5
        //      |    |
        //      v    v
        //      3 -> 4 -> 2
        graph.edge(0, 1, 9, false);
        graph.edge(1, 5, 2, false);
        graph.edge(1, 3, 2, false);
        graph.edge(3, 4, 4, false);
        graph.edge(5, 4, 6, false);
        graph.edge(4, 2, 3, false);
        addTurnCost(1, 3, 4, 3);
        graph.freeze();

        // no shortcuts here
        setLevelEqualToNodeIdForAllNodes();
        testPathCalculation(0, 4, 17, IntArrayList.from(0, 1, 5, 4));
    }

    @Test
    public void testFindPath_downwardSearchRunsIntoTarget() {
        // 0 <- 1
        //  \   ^
        //   \  |
        //    <-2<-3
        graph.edge(1, 0, 9, false);
        graph.edge(2, 0, 14, false);
        graph.edge(2, 1, 2, false);
        graph.edge(3, 2, 9, false);
        graph.freeze();

        //no shortcuts
        setLevelEqualToNodeIdForAllNodes();
        testPathCalculation(3, 0, 20, IntArrayList.from(3, 2, 1, 0));
    }

    @Test
    public void testFindPath_incomingShortcut() {
        // this test covers the case where an original edge and a shortcut have the same traversal id
        // 0 -- 1
        // | __/
        // v/
        // 3 -> 2
        graph.edge(0, 1, 9, true);
        graph.edge(0, 3, 14, false);
        graph.edge(3, 2, 9, false);
        graph.freeze();
        addShortcut(1, 3, 0, 1, 0, 1, 23);
        setLevelEqualToNodeIdForAllNodes();
        testPathCalculation(0, 2, 23, IntArrayList.from(0, 3, 2));
    }

    @Test
    public void testFindPathWithTurnCosts_fwdBwdSearchesMeetWithUTurn() {
        //       3
        //       |
        // 0 --- 2 --- 1
        graph.edge(0, 2, 1, false);
        graph.edge(2, 3, 2, true);
        graph.edge(2, 1, 3, false);
        addRestriction(0, 2, 1);
        addTurnCost(0, 2, 3, 5);
        addTurnCost(2, 3, 2, 4);
        addTurnCost(3, 2, 1, 7);
        graph.freeze();

        // contraction yields no shortcuts
        setLevelEqualToNodeIdForAllNodes();

        // without u-turns no path can be found
        testPathCalculation(0, 1, -1, IntArrayList.from());
    }

    @Test
    public void testFindPath_doNotMakeUTurn() {
        // in this case there should be no u-turn at node A, but in principal it would be ok to take a shortcut from
        // A to B
        checkUTurnNotBeingUsed(false);
    }

    @Test
    public void testFindPath_doNotMakeUTurn_toLowerLevelNode() {
        // in this case it would be forbidden to take the shortcut from A to B because B has lower level than A and
        // because we can not do a shortcut at node A. The optimization to not check the node levels in LevelEdgeFilter
        // that relies on shortcuts to lower level nodes being disconnected can 'hide' a u-turn bug here.
        checkUTurnNotBeingUsed(true);
    }

    private void checkUTurnNotBeingUsed(boolean toLowerLevelNode) {
        //           A <- 1
        //           |
        // 2 <- B <- 3 <- 0
        int nodeA = 4;
        int nodeB = 5;
        if (toLowerLevelNode) {
            int tmp = nodeA;
            nodeA = nodeB;
            nodeB = tmp;
        }
        graph.edge(1, nodeA, 4, false);
        graph.edge(0, 3, 4, false);
        graph.edge(nodeB, 2, 1, false);
        final EdgeIteratorState e3toB = graph.edge(3, nodeB, 2, false);
        final EdgeIteratorState e3toA = graph.edge(3, nodeA, 1, true);
        graph.freeze();
        addRestriction(0, 3, nodeB);

        // one shortcut when contracting node 3
        addShortcut(nodeA, nodeB, e3toA.getEdge(), e3toB.getEdge(), e3toA.getEdge(), e3toB.getEdge(), 2);
        setLevelEqualToNodeIdForAllNodes();

        // without u-turns the only 'possible' path 0-3-A-3-B-2 is forbidden
        testPathCalculation(0, 2, -1, IntArrayList.from());
    }

    @Test
    public void testFindPathWithTurnCosts_loop() {
        //       3\
        //       |/
        // 0 --- 2 --- 1
        final EdgeIteratorState edge1 = graph.edge(0, 2, 4, false);
        final EdgeIteratorState edge2 = graph.edge(2, 3, 1, true);
        final EdgeIteratorState edge3 = graph.edge(3, 2, 7, false);
        final EdgeIteratorState edge4 = graph.edge(2, 1, 3, false);
        // need to specify edges explicitly because there are two edges between nodes 2 and 3
        addRestriction(edge1, edge4, 2);
        addTurnCost(edge1, edge2, 2, 3);
        graph.freeze();

        // no shortcuts
        setLevelEqualToNodeIdForAllNodes();

        // without u-turns we need to take the loop
        testPathCalculation(0, 1, 18, IntArrayList.from(0, 2, 3, 2, 1));

        // additional check
        testPathCalculation(3, 1, 4, IntArrayList.from(3, 2, 1));
    }

    @Test
    public void testFindPathWithTurnCosts_multiple_bridge_nodes() {
        //   --- 2 ---
        //  /         \
        // 0 --- 3 --- 1
        //  \         /
        //   --- 4 ---
        graph.edge(0, 2, 1, false);
        graph.edge(0, 3, 3, false);
        graph.edge(0, 4, 2, false);
        graph.edge(2, 1, 1, false);
        graph.edge(3, 1, 2, false);
        graph.edge(4, 1, 6, false);
        addTurnCost(0, 2, 1, 9);
        addTurnCost(0, 3, 1, 2);
        addTurnCost(0, 4, 1, 1);
        graph.freeze();

        // contraction yields no shortcuts
        setLevelEqualToNodeIdForAllNodes();

        // going via 2, 3 and 4 is possible, but we want the shortest path taking into account turn costs also at
        // the bridge node
        testPathCalculation(0, 1, 7, IntArrayList.from(0, 3, 1));
    }

    @Test
    public void testFindPath_loopIsRecognizedAsIncomingEdge() {
        //     ---
        //     \ /
        // 0 -- 3 -- 2 -- 1
        EdgeIteratorState edge0 = graph.edge(0, 3, 1, true);
        EdgeIteratorState edge1 = graph.edge(3, 3, 1, false);
        EdgeIteratorState edge2 = graph.edge(3, 2, 1, true);
        EdgeIteratorState edge3 = graph.edge(2, 1, 1, false);
        addRestriction(edge0, edge2, 3);
        graph.freeze();

        // contraction yields no shortcuts
        setLevelEqualToNodeIdForAllNodes();

        // node 3 is the bridge node where both forward and backward searches meet. since there is a turn restriction
        // at node 3 we cannot go from 0 to 2 directly, but we need to take the loop at 3 first. when the backward 
        // search arrives at 3 it checks if 3 could be reached by the forward search and therefore its crucial that
        // the ('forward') loop at 3 is recognized as an incoming edge at node 3
        testPathCalculation(0, 1, 4, IntArrayList.from(0, 3, 3, 2, 1));
    }

    @Test
    public void testFindPath_shortcutLoopIsRecognizedAsIncomingEdge() {
        //          -0-
        //          \ /
        // 3 -- 4 -- 2 -- 1
        EdgeIteratorState edge0 = graph.edge(3, 4, 1, true);
        EdgeIteratorState edge1 = graph.edge(4, 2, 1, true);
        EdgeIteratorState edge2 = graph.edge(2, 0, 1, false);
        EdgeIteratorState edge3 = graph.edge(0, 2, 1, false);
        EdgeIteratorState edge4 = graph.edge(2, 1, 1, false);
        addRestriction(edge1, edge4, 2);
        graph.freeze();

        // contracting node 0 yields (the only) shortcut - and its a loop
        addShortcut(2, 2, edge2.getEdge(), edge3.getEdge(), edge2.getEdge(), edge3.getEdge(), 2);
        setLevelEqualToNodeIdForAllNodes();

        // node 2 is the bridge node where the forward and backward searches meet (highest level). since there is a turn restriction
        // at node 2 we cannot go from 4 to 1 directly, but we need to take the loop at 2 first. when the backward
        // search arrives at 2 it is crucial that the ('forward') loop-shortcut at 2 is recognized as an incoming edge
        // at node 2, otherwise the backward search ends at node 2. the forward search can never reach node 2 at all,
        // because it never goes to a lower level. so when the backward search does not see the 'forward' loop shortcut
        // no path between 3 and 1 will be found even though there is one.
        testPathCalculation(3, 1, 5, IntArrayList.from(3, 4, 2, 0, 2, 1));
    }

    @Test
    public void testFindPathWithTurnRestriction_single_loop() {
        //     0
        //     | \
        //     |  >
        // 3-> 4---1
        //     |
        //     v  no right turn at 4 when coming from 3!
        //     2
        graph.edge(3, 4, 2, false);
        graph.edge(4, 0, 1, true);
        graph.edge(0, 1, 3, false);
        graph.edge(4, 1, 5, true);
        graph.edge(4, 2, 4, false);
        addRestriction(3, 4, 2);
        graph.freeze();

        // contracting node 0
        addShortcut(4, 1, 1, 2, 1, 2, 4);
        // contracting node 1
        addShortcut(4, 4, 1, 3, 5, 3, 9);
        setLevelEqualToNodeIdForAllNodes();

        testPathCalculation(3, 2, 15, IntArrayList.from(3, 4, 0, 1, 4, 2));
    }

    @Test
    public void testFindPath_singleLoopInFwdSearch() {
        runTestWithSingleLoop(true);
    }

    @Test
    public void testFindPath_singleLoopInBwdSearch() {
        runTestWithSingleLoop(false);
    }

    private void runTestWithSingleLoop(boolean loopInFwdSearch) {
        // because we set the node levels equal to the node ids, depending on the size relation between node A and B
        // either the fwd search or the bwd search will explore the loop at node 5.
        // in any case it is important that the fwd/bwd search unpacks the loop shortcut at node 5 correctly
        int nodeA = 0;
        int nodeB = 6;
        if (!loopInFwdSearch) {
            int tmp = nodeA;
            nodeA = nodeB;
            nodeB = tmp;
        }
        //  4 1<-3
        //  | |  |
        //  A-5->2
        //    |
        //    B-7
        graph.edge(4, nodeA, 1, false);
        graph.edge(nodeA, 5, 2, false);
        graph.edge(5, 2, 2, false);
        graph.edge(2, 3, 1, false);
        graph.edge(3, 1, 2, false);
        graph.edge(1, 5, 1, false);
        graph.edge(5, nodeB, 1, false);
        graph.edge(nodeB, 7, 2, false);
        addRestriction(nodeA, 5, nodeB);
        graph.freeze();
        addShortcut(3, 5, 4, 5, 4, 5, 3);
        addShortcut(5, 3, 2, 3, 2, 3, 3);
        addShortcut(5, 5, 2, 5, 9, 8, 6);
        setLevelEqualToNodeIdForAllNodes();

        testPathCalculation(4, 7, 12, IntArrayList.from(4, nodeA, 5, 2, 3, 1, 5, nodeB, 7));
    }

    @Test
    public void testFindPathWithTurnRestriction_double_loop() {
        //   1
        //   |\  at 6 we can only take the next left turn (can not skip a turn or go right)
        //   | \
        //   0--6--2
        //     / \ |
        //     |  \|
        // 4---7   3
        //     |
        //     |  no right turn at 7 when coming from 4 and no left turn at 7 when coming from 5!
        //     5
        final EdgeIteratorState e0to1 = graph.edge(0, 1, 2, true);
        final EdgeIteratorState e1to6 = graph.edge(1, 6, 1, true);
        final EdgeIteratorState e0to6 = graph.edge(0, 6, 4, true);
        final EdgeIteratorState e2to6 = graph.edge(2, 6, 5, true);
        final EdgeIteratorState e2to3 = graph.edge(2, 3, 3, true);
        final EdgeIteratorState e3to6 = graph.edge(3, 6, 2, true);
        final EdgeIteratorState e6to7 = graph.edge(7, 6, 1, true);
        final EdgeIteratorState e4to7 = graph.edge(7, 4, 3, true);
        final EdgeIteratorState e5to7 = graph.edge(7, 5, 2, true);

        addRestriction(e6to7, e1to6, 6);
        addRestriction(e6to7, e2to6, 6);
        addRestriction(e6to7, e3to6, 6);
        addRestriction(e1to6, e3to6, 6);
        addRestriction(e1to6, e6to7, 6);
        addRestriction(e1to6, e0to6, 6);

        addRestriction(e4to7, e5to7, 7);
        addRestriction(e5to7, e4to7, 7);
        graph.freeze();

        // contracting node 0 and 1
        addShortcut(6, 1, 2, 0, 2, 0, 6);
        addShortcut(6, 6, 2, 1, 9, 1, 7);
        // contracting node 2 and 3
        addShortcut(6, 3, 3, 4, 3, 4, 8);
        addShortcut(6, 6, 3, 5, 11, 5, 10);
        // contracting node 4 and 5 yields no shortcuts
        // contracting node 6 --> three shortcuts to account for double loop (we nest shortcuts inside each other)
        addShortcut(7, 6, 6, 1, 6, 10, 8);
        addShortcut(7, 6, 6, 5, 13, 12, 18);
        addShortcut(7, 7, 6, 6, 14, 6, 19);
        setLevelEqualToNodeIdForAllNodes();

        testPathCalculation(4, 5, 24, IntArrayList.from(4, 7, 6, 0, 1, 6, 2, 3, 6, 7, 5));
        testPathCalculation(5, 4, 24, IntArrayList.from(5, 7, 6, 0, 1, 6, 2, 3, 6, 7, 4));
    }

    @Test
    public void testFindPathWithTurnRestriction_two_different_loops() {
        // 1
        // | \
        // ^  \
        // |   |
        // 0<- 5
        //     | \
        //     |  >
        // 3-> 6---4
        //     |
        //     v  no right turn at 6 when coming from 3!
        //     2
        graph.edge(0, 1, 2, false);
        graph.edge(1, 5, 1, true);
        graph.edge(5, 0, 1, false);
        graph.edge(5, 4, 5, false);
        graph.edge(5, 6, 3, true);
        graph.edge(6, 4, 4, true);

        graph.edge(3, 6, 3, false);
        graph.edge(6, 2, 4, false);
        addRestriction(3, 6, 2);
        graph.freeze();

        // contracting node 0
        addShortcut(5, 1, 2, 0, 2, 0, 3);
        // contracting node 1
        addShortcut(5, 5, 2, 1, 8, 1, 4);
        // contracting node 2 & 3 does not yield any shortcuts
        // contracting node 4
        addShortcut(5, 6, 3, 5, 3, 5, 9);
        // contracting node 5 --> two shortcuts to account for loop (we nest shortcuts inside each other)
        addShortcut(6, 5, 4, 1, 4, 9, 7);
        addShortcut(6, 6, 4, 4, 11, 4, 10);
        // contracting node 6 --> no more shortcuts

        setLevelEqualToNodeIdForAllNodes();

        List<List<Integer>> distMatrix = Arrays.asList(
                // -1 if no path is expected
                Arrays.asList(0, 2, 10, -1, 8, 3, 6),
                Arrays.asList(2, 0, 8, -1, 6, 1, 4),
                Arrays.asList(-1, -1, 0, -1, -1, -1, -1),
                Arrays.asList(7, 7, 17, 0, 7, 6, 3),
                Arrays.asList(8, 8, 8, -1, 0, 7, 4),
                Arrays.asList(1, 1, 7, -1, 5, 0, 3),
                Arrays.asList(4, 4, 4, -1, 4, 3, 0));

        for (int i = 0; i < distMatrix.size(); ++i) {
            for (int j = 0; j < distMatrix.get(i).size(); ++j) {
                testPathCalculation(i, j, distMatrix.get(i).get(j), null);
            }
        }
    }

    private void testPathCalculation(int from, int to, int expectedWeight, IntArrayList expectedNodes) {
        AbstractBidirectionEdgeCHNoSOD algo = createAlgo();
        Path path = algo.calcPath(from, to);
        if (expectedWeight < 0) {
            assertFalse(String.format(Locale.ROOT, "Unexpected path from %d to %d.", from, to), path.isFound());
        } else {
            if (expectedNodes != null) {
                assertEquals(String.format(Locale.ROOT, "Unexpected path from %d to %d", from, to), expectedNodes, path.calcNodes());
            }
            assertEquals(String.format(Locale.ROOT, "Unexpected path weight from %d to %d", from, to), expectedWeight, path.getWeight(), 1.e-6);
        }
    }

    private AbstractBidirectionEdgeCHNoSOD createAlgo() {
        TurnWeighting chTurnWeighting = new TurnWeighting(new PreparationWeighting(weighting), turnCostExtension);
        chTurnWeighting.setDefaultUTurnCost(0);
        AbstractBidirectionEdgeCHNoSOD algo = "astar".equals(algoString) ?
                new AStarBidirectionEdgeCHNoSOD(chGraph, chTurnWeighting) :
                new DijkstraBidirectionEdgeCHNoSOD(chGraph, chTurnWeighting);
        algo.setEdgeFilter(new LevelEdgeFilter(chGraph));
        return algo;
    }

    private void addShortcut(int from, int to, int firstOrigEdge, int lastOrigEdge, int skipped1, int skipped2, double weight) {
        CHEdgeIteratorState shortcut = chGraph.shortcut(from, to);
        // we need to set flags first because they overwrite weight etc
        shortcut.setFlagsAndWeight(PrepareEncoder.getScFwdDir(), weight);
        shortcut.setFirstAndLastOrigEdges(firstOrigEdge, lastOrigEdge).setSkippedEdges(skipped1, skipped2);
    }

    private void setLevelEqualToNodeIdForAllNodes() {
        for (int node = 0; node < chGraph.getNodes(); ++node) {
            chGraph.setLevel(node, node);
        }
    }

    private void addTurnCost(EdgeIteratorState edge1, EdgeIteratorState edge2, int viaNode, double costs) {
        turnCostExtension.addTurnInfo(edge1.getEdge(), viaNode, edge2.getEdge(), encoder.getTurnFlags(false, costs));
    }

    private void addTurnCost(int from, int via, int to, int cost) {
        addTurnCost(getEdge(from, via), getEdge(via, to), via, cost);
    }

    private void addRestriction(int from, int via, int to) {
        addRestriction(getEdge(from, via), getEdge(via, to), via);
    }

    private void addRestriction(EdgeIteratorState edge1, EdgeIteratorState edge2, int viaNode) {
        turnCostExtension.addTurnInfo(edge1.getEdge(), viaNode, edge2.getEdge(), encoder.getTurnFlags(true, 0));
    }

    private EdgeIteratorState getEdge(int from, int to) {
        return GHUtility.getEdge(graph, from, to);
    }

}
