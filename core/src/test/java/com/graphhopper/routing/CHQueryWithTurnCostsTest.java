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
import com.graphhopper.routing.ch.PrepareEncoder;
import com.graphhopper.routing.ev.EncodedValueLookup;
import com.graphhopper.routing.ev.TurnCost;
import com.graphhopper.routing.util.CarFlagEncoder;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.routing.util.FlagEncoder;
import com.graphhopper.storage.CHGraph;
import com.graphhopper.storage.GraphBuilder;
import com.graphhopper.storage.GraphHopperStorage;
import com.graphhopper.storage.RoutingCHGraphImpl;
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
    private final FlagEncoder encoder = new CarFlagEncoder(5, 5, maxCost).setSpeedTwoDirections(true);
    private final EncodingManager encodingManager = EncodingManager.create(encoder);
    private final GraphHopperStorage graph;
    private final CHGraph chGraph;
    private final String algoString;

    @Parameterized.Parameters(name = "{0}")
    public static Object[] parameters() {
        return new Object[]{"astar", "dijkstra"};
    }

    public CHQueryWithTurnCostsTest(String algoString) {
        this.algoString = algoString;
        graph = new GraphBuilder(encodingManager)
                .setCHConfigStrings("profile|car|shortest|edge")
                .create();
        chGraph = graph.getCHGraph();
    }

    @Test
    public void testFindPathWithTurnCosts_bidirected_no_shortcuts_smallGraph() {
        // some special cases where from=to, or start and target edges are the same
        // 1 -- 0 -- 2
        GHUtility.setSpeed(60, true, true, encoder, graph.edge(1, 0).setDistance(3));
        GHUtility.setSpeed(60, true, true, encoder, graph.edge(0, 2).setDistance(5));
        setTurnCost(1, 0, 2, 3);
        graph.freeze();

        // contraction yields no shortcuts for edge based case (at least without u-turns).
        setLevelEqualToNodeIdForAllNodes();

        for (int i = 0; i < 3; ++i) {
            testPathCalculation(i, i, 0, IntArrayList.from(i));
        }
        testPathCalculation(1, 2, 8, IntArrayList.from(1, 0, 2), 3);
        testPathCalculation(2, 1, 8, IntArrayList.from(2, 0, 1));
        testPathCalculation(0, 1, 3, IntArrayList.from(0, 1));
        testPathCalculation(0, 2, 5, IntArrayList.from(0, 2));
        testPathCalculation(1, 0, 3, IntArrayList.from(1, 0));
        testPathCalculation(2, 0, 5, IntArrayList.from(2, 0));
    }

    @Test
    public void testFindPathWithTurnCosts_bidirected_no_shortcuts() {
        // 0 -- 2 -- 4 -- 6 -- 5 -- 3 -- 1
        GHUtility.setSpeed(60, true, true, encoder, graph.edge(0, 2).setDistance(3));
        GHUtility.setSpeed(60, true, true, encoder, graph.edge(2, 4).setDistance(2));
        GHUtility.setSpeed(60, true, true, encoder, graph.edge(4, 6).setDistance(7));
        GHUtility.setSpeed(60, true, true, encoder, graph.edge(6, 5).setDistance(9));
        GHUtility.setSpeed(60, true, true, encoder, graph.edge(5, 3).setDistance(1));
        GHUtility.setSpeed(60, true, true, encoder, graph.edge(3, 1).setDistance(4));
        setTurnCost(0, 2, 4, 3);
        setTurnCost(4, 6, 5, 6);
        setTurnCost(5, 6, 4, 2);
        setTurnCost(5, 3, 1, 5);
        graph.freeze();

        // contraction yields no shortcuts
        setLevelEqualToNodeIdForAllNodes();

        // note that we are using the shortest weighting but turn cost times are included whatsoever, see #1590
        testPathCalculation(0, 1, 26, IntArrayList.from(0, 2, 4, 6, 5, 3, 1), 14);
        testPathCalculation(1, 0, 26, IntArrayList.from(1, 3, 5, 6, 4, 2, 0), 2);
        testPathCalculation(4, 3, 17, IntArrayList.from(4, 6, 5, 3), 6);
        testPathCalculation(0, 0, 0, IntArrayList.from(0));
        testPathCalculation(4, 4, 0, IntArrayList.from(4));

        // also check if distance and times (including turn costs) are calculated correctly
        Path path = createAlgo().calcPath(0, 1);
        assertEquals("wrong weight", 40, path.getWeight(), 1.e-3);
        assertEquals("wrong distance", 26, path.getDistance(), 1.e-3);
        double weightPerMeter = 0.06;
        assertEquals("wrong time", (26 * weightPerMeter + 14) * 1000, path.getTime(), 1.e-3);
    }

    @Test
    public void testFindPathWithTurnCosts_loopShortcutBwdSearch() {
        // the loop shortcut 4-4 will be encountered during the bwd search
        //             3
        //            / \
        //           1   2
        //            \ /
        // 0 - 7 - 8 - 4 - 6 - 5
        GHUtility.setSpeed(60, true, false, encoder, graph.edge(0, 7).setDistance(1));
        GHUtility.setSpeed(60, true, false, encoder, graph.edge(7, 8).setDistance(1));
        GHUtility.setSpeed(60, true, false, encoder, graph.edge(8, 4).setDistance(1));
        GHUtility.setSpeed(60, true, false, encoder, graph.edge(4, 1).setDistance(1));
        GHUtility.setSpeed(60, true, false, encoder, graph.edge(1, 3).setDistance(1));
        GHUtility.setSpeed(60, true, false, encoder, graph.edge(3, 2).setDistance(1));
        GHUtility.setSpeed(60, true, false, encoder, graph.edge(2, 4).setDistance(1));
        GHUtility.setSpeed(60, true, false, encoder, graph.edge(4, 6).setDistance(1));
        GHUtility.setSpeed(60, true, false, encoder, graph.edge(6, 5).setDistance(1));
        setRestriction(8, 4, 6);
        setRestriction(8, 4, 2);
        setRestriction(1, 4, 6);

        graph.freeze();

        setLevelEqualToNodeIdForAllNodes();
        // from contracting nodes 1&2
        addShortcut(3, 4, 3, 4, 3, 4, 2, true);
        addShortcut(3, 4, 5, 6, 5, 6, 2, false);
        // from contracting node 3
        addShortcut(4, 4, 3, 6, 9, 10, 4, false);
        // from contracting node 4
        addShortcut(4, 8, 2, 6, 2, 11, 5, true);
        addShortcut(6, 8, 2, 7, 12, 7, 6, true);

        testPathCalculation(0, 5, 9, IntArrayList.from(0, 7, 8, 4, 1, 3, 2, 4, 6, 5));
    }

    @Test
    public void testFindPathWithTurnCosts_loopShortcutFwdSearch() {
        // the loop shortcut 4-4 will be encountered during the fwd search
        //         3
        //        / \
        //       1   2
        //        \ /
        // 5 - 6 - 4 - 7 - 8 - 0
        GHUtility.setSpeed(60, 0, encoder,
                graph.edge(5, 6).setDistance(1),
                graph.edge(6, 4).setDistance(1),
                graph.edge(4, 1).setDistance(1),
                graph.edge(1, 3).setDistance(1),
                graph.edge(3, 2).setDistance(1),
                graph.edge(2, 4).setDistance(1),
                graph.edge(4, 7).setDistance(1),
                graph.edge(7, 8).setDistance(1),
                graph.edge(8, 0).setDistance(1));
        setRestriction(6, 4, 7);
        setRestriction(6, 4, 2);
        setRestriction(1, 4, 7);
        graph.freeze();

        setLevelEqualToNodeIdForAllNodes();
        // from contracting nodes 1&2
        addShortcut(3, 4, 2, 3, 2, 3, 2, true);
        addShortcut(3, 4, 4, 5, 4, 5, 2, false);
        // from contracting node 3
        addShortcut(4, 4, 2, 5, 9, 10, 4, false);
        // from contracting node 4
        addShortcut(4, 6, 1, 5, 1, 11, 5, true);
        addShortcut(6, 7, 1, 6, 12, 6, 6, false);

        testPathCalculation(5, 0, 9, IntArrayList.from(5, 6, 4, 1, 3, 2, 4, 7, 8, 0));
    }

    @Test
    public void testFindPathWithTurnCosts_directed_single_shortcut() {
        //    2     3
        //   /5\   /1\
        //  /   \2/   \
        // 1     0     4
        GHUtility.setSpeed(60, true, false, encoder, graph.edge(1, 2).setDistance(4));
        GHUtility.setSpeed(60, true, false, encoder, graph.edge(2, 0).setDistance(2));
        GHUtility.setSpeed(60, true, false, encoder, graph.edge(0, 3).setDistance(3));
        GHUtility.setSpeed(60, true, false, encoder, graph.edge(3, 4).setDistance(2));
        setTurnCost(1, 2, 0, 5);
        setTurnCost(2, 0, 3, 2);
        setTurnCost(0, 3, 4, 1);
        graph.freeze();

        // only when node 0 is contracted a shortcut is added
        setLevelEqualToNodeIdForAllNodes();
        addShortcut(2, 3, 1, 2, 1, 2, 7, false);

        // when we are searching a path to the highest level node, the backward search will not expand any edges
        testPathCalculation(1, 4, 11, IntArrayList.from(1, 2, 0, 3, 4), 8);
        testPathCalculation(2, 4, 7, IntArrayList.from(2, 0, 3, 4), 3);
        testPathCalculation(0, 4, 5, IntArrayList.from(0, 3, 4), 1);

        // when we search a path to or start the search from a low level node both forward and backward searches run
        testPathCalculation(1, 0, 6, IntArrayList.from(1, 2, 0), 5);
        testPathCalculation(0, 4, 5, IntArrayList.from(0, 3, 4), 1);
    }

    @Test
    public void testFindPathWithTurnCosts_directed_single_shortcut_fwdSearchStopsQuickly() {
        //     0
        //    / \
        // 1-2-s-3-4
        GHUtility.setSpeed(60, true, false, encoder, graph.edge(1, 2).setDistance(2));
        GHUtility.setSpeed(60, true, false, encoder, graph.edge(2, 0).setDistance(3));
        GHUtility.setSpeed(60, true, false, encoder, graph.edge(0, 3).setDistance(1));
        GHUtility.setSpeed(60, true, false, encoder, graph.edge(3, 4).setDistance(3));
        graph.freeze();

        setTurnCost(1, 2, 0, 2);
        setTurnCost(0, 3, 4, 4);

        setLevelEqualToNodeIdForAllNodes();
        // from contracting node 0
        addShortcut(2, 3, 1, 2, 1, 2, 4, false);

        testPathCalculation(1, 4, 9, IntArrayList.from(1, 2, 0, 3, 4), 6);
    }

    @Test
    public void testFindPathWithTurnCosts_directed_two_shortcuts() {
        //    3     0
        //   /5\   /1\
        //  /   \2/   \
        // 2     1     4
        GHUtility.setSpeed(60, true, false, encoder, graph.edge(2, 3).setDistance(4));
        GHUtility.setSpeed(60, true, false, encoder, graph.edge(3, 1).setDistance(2));
        GHUtility.setSpeed(60, true, false, encoder, graph.edge(1, 0).setDistance(3));
        GHUtility.setSpeed(60, true, false, encoder, graph.edge(0, 4).setDistance(2));
        setTurnCost(2, 3, 1, 5);
        setTurnCost(3, 1, 0, 2);
        setTurnCost(1, 0, 4, 1);
        graph.freeze();

        setLevelEqualToNodeIdForAllNodes();
        // contraction of node 0 and 1 each yield a single shortcut
        addShortcut(1, 4, 2, 3, 2, 3, 6, false);
        addShortcut(3, 4, 1, 3, 1, 4, 10, false);

        // the turn costs have to be accounted for also when the shortcuts are used
        testPathCalculation(2, 4, 11, IntArrayList.from(2, 3, 1, 0, 4), 8);
        testPathCalculation(1, 4, 5, IntArrayList.from(1, 0, 4), 1);
        testPathCalculation(2, 0, 9, IntArrayList.from(2, 3, 1, 0), 7);
        testPathCalculation(3, 4, 7, IntArrayList.from(3, 1, 0, 4), 3);
        testPathCalculation(2, 1, 6, IntArrayList.from(2, 3, 1), 5);
    }

    @Test
    public void testFindPath_directConnectionIsNotTheBestPath() {
        // this case is interesting because there is an expensive edge going from the source to the target directly
        // 0 --------\
        // |         |
        // v         v
        // 2 -> 3 -> 1
        GHUtility.setSpeed(60, true, false, encoder, graph.edge(0, 2).setDistance(3));
        GHUtility.setSpeed(60, true, false, encoder, graph.edge(2, 3).setDistance(2));
        GHUtility.setSpeed(60, true, false, encoder, graph.edge(3, 1).setDistance(9));
        GHUtility.setSpeed(60, true, false, encoder, graph.edge(0, 1).setDistance(50));
        setTurnCost(2, 3, 1, 4);
        graph.freeze();

        // no shortcuts here
        setLevelEqualToNodeIdForAllNodes();
        testPathCalculation(0, 1, 14, IntArrayList.from(0, 2, 3, 1), 4);
    }

    @Test
    public void testFindPath_upwardSearchRunsIntoTarget() {
        // this case is interesting because one possible path runs from 0 to 4 directly (the backward search does not
        // contribute anything in this case), but this path is not as good as the one via node 5
        // 0 -> 1 -> 5
        //      |    |
        //      v    v
        //      3 -> 4 -> 2
        GHUtility.setSpeed(60, 0, encoder,
                graph.edge(0, 1).setDistance(9),
                graph.edge(1, 5).setDistance(2),
                graph.edge(1, 3).setDistance(2),
                graph.edge(3, 4).setDistance(4),
                graph.edge(5, 4).setDistance(6),
                graph.edge(4, 2).setDistance(3));
        setTurnCost(1, 3, 4, 3);
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
        GHUtility.setSpeed(60, true, false, encoder, graph.edge(1, 0).setDistance(9));
        GHUtility.setSpeed(60, true, false, encoder, graph.edge(2, 0).setDistance(14));
        GHUtility.setSpeed(60, true, false, encoder, graph.edge(2, 1).setDistance(2));
        GHUtility.setSpeed(60, true, false, encoder, graph.edge(3, 2).setDistance(9));
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
        GHUtility.setSpeed(60, true, true, encoder, graph.edge(0, 1).setDistance(9));
        GHUtility.setSpeed(60, true, false, encoder, graph.edge(0, 3).setDistance(14));
        GHUtility.setSpeed(60, true, false, encoder, graph.edge(3, 2).setDistance(9));
        graph.freeze();
        setLevelEqualToNodeIdForAllNodes();
        addShortcut(1, 3, 0, 1, 0, 1, 23, false);
        testPathCalculation(0, 2, 23, IntArrayList.from(0, 3, 2));
    }

    @Test
    public void testFindPathWithTurnCosts_fwdBwdSearchesMeetWithUTurn() {
        //       3
        //       |
        // 0 --- 2 --- 1
        GHUtility.setSpeed(60, true, false, encoder, graph.edge(0, 2).setDistance(1));
        GHUtility.setSpeed(60, true, true, encoder, graph.edge(2, 3).setDistance(2));
        GHUtility.setSpeed(60, true, false, encoder, graph.edge(2, 1).setDistance(3));
        setRestriction(0, 2, 1);
        setTurnCost(0, 2, 3, 5);
        setTurnCost(2, 3, 2, 4);
        setTurnCost(3, 2, 1, 7);
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
        // because we cannot do a shortcut at node A. The optimization to not check the node levels in CHLevelEdgeFilter
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
        GHUtility.setSpeed(60, true, false, encoder, graph.edge(1, nodeA).setDistance(4));
        GHUtility.setSpeed(60, true, false, encoder, graph.edge(0, 3).setDistance(4));
        GHUtility.setSpeed(60, true, false, encoder, graph.edge(nodeB, 2).setDistance(1));
        final EdgeIteratorState e3toB = GHUtility.setSpeed(60, true, false, encoder, graph.edge(3, nodeB).setDistance(2));
        final EdgeIteratorState e3toA = GHUtility.setSpeed(60, true, true, encoder, graph.edge(3, nodeA).setDistance(1));
        graph.freeze();
        setRestriction(0, 3, nodeB);

        // one shortcut when contracting node 3
        setLevelEqualToNodeIdForAllNodes();
        if (toLowerLevelNode) {
            addShortcut(nodeB, nodeA, e3toA.getEdge(), e3toB.getEdge(), e3toA.getEdge(), e3toB.getEdge(), 2, true);
        } else {
            addShortcut(nodeA, nodeB, e3toA.getEdge(), e3toB.getEdge(), e3toA.getEdge(), e3toB.getEdge(), 2, false);
        }

        // without u-turns the only 'possible' path 0-3-A-3-B-2 is forbidden
        testPathCalculation(0, 2, -1, IntArrayList.from());
    }

    @Test
    public void testFindPathWithTurnCosts_loop() {
        //       3\
        //       |/
        // 0 --- 2 --- 1
        final EdgeIteratorState edge1 = GHUtility.setSpeed(60, true, false, encoder, graph.edge(0, 2).setDistance(4));
        final EdgeIteratorState edge2 = GHUtility.setSpeed(60, true, true, encoder, graph.edge(2, 3).setDistance(1));
        final EdgeIteratorState edge3 = GHUtility.setSpeed(60, true, false, encoder, graph.edge(3, 2).setDistance(7));
        final EdgeIteratorState edge4 = GHUtility.setSpeed(60, true, false, encoder, graph.edge(2, 1).setDistance(3));
        // need to specify edges explicitly because there are two edges between nodes 2 and 3
        setRestriction(edge1, edge4, 2);
        setTurnCost(edge1, edge2, 2, 3);
        graph.freeze();

        // no shortcuts
        setLevelEqualToNodeIdForAllNodes();

        // without u-turns we need to take the loop
        testPathCalculation(0, 1, 15, IntArrayList.from(0, 2, 3, 2, 1), 3);

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
        GHUtility.setSpeed(60, 0, encoder,
                graph.edge(0, 2).setDistance(1),
                graph.edge(0, 3).setDistance(3),
                graph.edge(0, 4).setDistance(2),
                graph.edge(2, 1).setDistance(1),
                graph.edge(3, 1).setDistance(2),
                graph.edge(4, 1).setDistance(6));
        setTurnCost(0, 2, 1, 9);
        setTurnCost(0, 3, 1, 2);
        setTurnCost(0, 4, 1, 1);
        graph.freeze();

        // contraction yields no shortcuts
        setLevelEqualToNodeIdForAllNodes();

        // going via 2, 3 and 4 is possible, but we want the shortest path taking into account turn costs also at
        // the bridge node
        testPathCalculation(0, 1, 5, IntArrayList.from(0, 3, 1), 2);
    }

    @Test
    public void testFindPath_loopIsRecognizedAsIncomingEdge() {
        //     ---
        //     \ /
        // 0 -- 3 -- 2 -- 1
        EdgeIteratorState edge0 = GHUtility.setSpeed(60, true, true, encoder, graph.edge(0, 3).setDistance(1));
        EdgeIteratorState edge1 = GHUtility.setSpeed(60, true, false, encoder, graph.edge(3, 3).setDistance(1));
        EdgeIteratorState edge2 = GHUtility.setSpeed(60, true, true, encoder, graph.edge(3, 2).setDistance(1));
        EdgeIteratorState edge3 = GHUtility.setSpeed(60, true, false, encoder, graph.edge(2, 1).setDistance(1));
        setRestriction(edge0, edge2, 3);
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
        EdgeIteratorState edge0 = GHUtility.setSpeed(60, true, true, encoder, graph.edge(3, 4).setDistance(1));
        EdgeIteratorState edge1 = GHUtility.setSpeed(60, true, true, encoder, graph.edge(4, 2).setDistance(1));
        EdgeIteratorState edge2 = GHUtility.setSpeed(60, true, false, encoder, graph.edge(2, 0).setDistance(1));
        EdgeIteratorState edge3 = GHUtility.setSpeed(60, true, false, encoder, graph.edge(0, 2).setDistance(1));
        EdgeIteratorState edge4 = GHUtility.setSpeed(60, true, false, encoder, graph.edge(2, 1).setDistance(1));
        setRestriction(edge1, edge4, 2);
        graph.freeze();

        setLevelEqualToNodeIdForAllNodes();
        // contracting node 0 yields (the only) shortcut - and its a loop
        addShortcut(2, 2, edge2.getEdge(), edge3.getEdge(), edge2.getEdge(), edge3.getEdge(), 2, false);

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
        GHUtility.setSpeed(60, true, false, encoder, graph.edge(3, 4).setDistance(2));
        GHUtility.setSpeed(60, true, true, encoder, graph.edge(4, 0).setDistance(1));
        GHUtility.setSpeed(60, true, false, encoder, graph.edge(0, 1).setDistance(3));
        GHUtility.setSpeed(60, true, true, encoder, graph.edge(4, 1).setDistance(5));
        GHUtility.setSpeed(60, true, false, encoder, graph.edge(4, 2).setDistance(4));
        setRestriction(3, 4, 2);
        graph.freeze();

        setLevelEqualToNodeIdForAllNodes();
        // contracting node 0
        addShortcut(1, 4, 1, 2, 1, 2, 4, true);
        // contracting node 1
        addShortcut(4, 4, 1, 3, 5, 3, 9, false);

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
        GHUtility.setSpeed(60, 0, encoder,
                graph.edge(4, nodeA).setDistance(1),
                graph.edge(nodeA, 5).setDistance(2),
                graph.edge(5, 2).setDistance(2),
                graph.edge(2, 3).setDistance(1),
                graph.edge(3, 1).setDistance(2),
                graph.edge(1, 5).setDistance(1),
                graph.edge(5, nodeB).setDistance(1),
                graph.edge(nodeB, 7).setDistance(2));
        setRestriction(nodeA, 5, nodeB);
        graph.freeze();
        setLevelEqualToNodeIdForAllNodes();
        addShortcut(3, 5, 4, 5, 4, 5, 3, false);
        addShortcut(3, 5, 2, 3, 2, 3, 3, true);
        addShortcut(5, 5, 2, 5, 9, 8, 6, false);

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
        final EdgeIteratorState e0to1 = GHUtility.setSpeed(60, true, true, encoder, graph.edge(0, 1).setDistance(2));
        final EdgeIteratorState e1to6 = GHUtility.setSpeed(60, true, true, encoder, graph.edge(1, 6).setDistance(1));
        final EdgeIteratorState e0to6 = GHUtility.setSpeed(60, true, true, encoder, graph.edge(0, 6).setDistance(4));
        final EdgeIteratorState e2to6 = GHUtility.setSpeed(60, true, true, encoder, graph.edge(2, 6).setDistance(5));
        final EdgeIteratorState e2to3 = GHUtility.setSpeed(60, true, true, encoder, graph.edge(2, 3).setDistance(3));
        final EdgeIteratorState e3to6 = GHUtility.setSpeed(60, true, true, encoder, graph.edge(3, 6).setDistance(2));
        final EdgeIteratorState e6to7 = GHUtility.setSpeed(60, true, true, encoder, graph.edge(7, 6).setDistance(1));
        final EdgeIteratorState e4to7 = GHUtility.setSpeed(60, true, true, encoder, graph.edge(7, 4).setDistance(3));
        final EdgeIteratorState e5to7 = GHUtility.setSpeed(60, true, true, encoder, graph.edge(7, 5).setDistance(2));

        setRestriction(e6to7, e1to6, 6);
        setRestriction(e6to7, e2to6, 6);
        setRestriction(e6to7, e3to6, 6);
        setRestriction(e1to6, e3to6, 6);
        setRestriction(e1to6, e6to7, 6);
        setRestriction(e1to6, e0to6, 6);

        setRestriction(e4to7, e5to7, 7);
        setRestriction(e5to7, e4to7, 7);
        graph.freeze();

        setLevelEqualToNodeIdForAllNodes();
        // contracting node 0,1,2,3
        addShortcut(1, 6, 2, 0, 2, 0, 6, true);
        addShortcut(3, 6, 3, 4, 3, 4, 8, true);
        addShortcut(6, 6, 2, 1, 9, 1, 7, false);
        addShortcut(6, 6, 3, 5, 10, 5, 10, false);
        // contracting node 4 and 5 yields no shortcuts
        // contracting node 6 --> three shortcuts to account for double loop (we nest shortcuts inside each other)
        addShortcut(6, 7, 6, 1, 6, 11, 8, true);
        addShortcut(6, 7, 6, 5, 13, 12, 18, true);
        addShortcut(7, 7, 6, 6, 14, 6, 19, false);

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
        GHUtility.setSpeed(60, true, false, encoder, graph.edge(0, 1).setDistance(2));
        GHUtility.setSpeed(60, true, true, encoder, graph.edge(1, 5).setDistance(1));
        GHUtility.setSpeed(60, true, false, encoder, graph.edge(5, 0).setDistance(1));
        GHUtility.setSpeed(60, true, false, encoder, graph.edge(5, 4).setDistance(5));
        GHUtility.setSpeed(60, true, true, encoder, graph.edge(5, 6).setDistance(3));
        GHUtility.setSpeed(60, true, true, encoder, graph.edge(6, 4).setDistance(4));

        GHUtility.setSpeed(60, true, false, encoder, graph.edge(3, 6).setDistance(3));
        GHUtility.setSpeed(60, true, false, encoder, graph.edge(6, 2).setDistance(4));
        setRestriction(3, 6, 2);
        graph.freeze();

        setLevelEqualToNodeIdForAllNodes();
        // contracting node 0
        addShortcut(1, 5, 2, 0, 2, 0, 3, true);
        // contracting node 1
        addShortcut(5, 5, 2, 1, 8, 1, 4, false);
        // contracting node 2 & 3 does not yield any shortcuts
        // contracting node 4
        addShortcut(5, 6, 3, 5, 3, 5, 9, false);
        // contracting node 5 --> two shortcuts to account for loop (we nest shortcuts inside each other)
        addShortcut(5, 6, 4, 1, 4, 9, 7, true);
        addShortcut(6, 6, 4, 4, 11, 4, 10, false);
        // contracting node 6 --> no more shortcuts


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
        testPathCalculation(from, to, expectedWeight, expectedNodes, 0);
    }

    private void testPathCalculation(int from, int to, int expectedEdgeWeight, IntArrayList expectedNodes, int expectedTurnCost) {
        int expectedWeight = expectedEdgeWeight + expectedTurnCost;
        int expectedDistance = expectedEdgeWeight;
        int expectedTime = expectedEdgeWeight * 60 + expectedTurnCost * 1000;
        AbstractBidirectionEdgeCHNoSOD algo = createAlgo();
        Path path = algo.calcPath(from, to);
        if (expectedWeight < 0) {
            assertFalse(String.format(Locale.ROOT, "Unexpected path from %d to %d.", from, to), path.isFound());
        } else {
            if (expectedNodes != null) {
                assertEquals(String.format(Locale.ROOT, "Unexpected path from %d to %d", from, to), expectedNodes, path.calcNodes());
            }
            assertEquals(String.format(Locale.ROOT, "Unexpected path weight from %d to %d", from, to), expectedWeight, path.getWeight(), 1.e-6);
            assertEquals(String.format(Locale.ROOT, "Unexpected path distance from %d to %d", from, to), expectedDistance, path.getDistance(), 1.e-6);
            assertEquals(String.format(Locale.ROOT, "Unexpected path time from %d to %d", from, to), expectedTime, path.getTime());
        }
    }

    private AbstractBidirectionEdgeCHNoSOD createAlgo() {
        return "astar".equals(algoString) ?
                new AStarBidirectionEdgeCHNoSOD(new RoutingCHGraphImpl(chGraph)) :
                new DijkstraBidirectionEdgeCHNoSOD(new RoutingCHGraphImpl(chGraph));
    }

    private void addShortcut(int from, int to, int firstOrigEdge, int lastOrigEdge, int skipped1, int skipped2, double weight, boolean reverse) {
        int flags = reverse ? PrepareEncoder.getScBwdDir() : PrepareEncoder.getScFwdDir();
        chGraph.shortcutEdgeBased(from, to, flags, weight, skipped1, skipped2, firstOrigEdge, lastOrigEdge);
    }

    private void setLevelEqualToNodeIdForAllNodes() {
        for (int node = 0; node < chGraph.getNodes(); ++node) {
            chGraph.setLevel(node, node);
        }
    }

    private void setTurnCost(int from, int via, int to, double cost) {
        setTurnCost(getEdge(from, via), getEdge(via, to), via, cost);
    }

    private void setTurnCost(EdgeIteratorState edge1, EdgeIteratorState edge2, int viaNode, double costs) {
        graph.getTurnCostStorage().set(((EncodedValueLookup) encodingManager).getDecimalEncodedValue(TurnCost.key(encoder.toString())), edge1.getEdge(), viaNode, edge2.getEdge(), costs);
    }

    private void setRestriction(int from, int via, int to) {
        setTurnCost(getEdge(from, via), getEdge(via, to), via, Double.POSITIVE_INFINITY);
    }

    private void setRestriction(EdgeIteratorState edge1, EdgeIteratorState edge2, int viaNode) {
        setTurnCost(edge1, edge2, viaNode, Double.POSITIVE_INFINITY);
    }

    private EdgeIteratorState getEdge(int from, int to) {
        return GHUtility.getEdge(graph, from, to);
    }
}
