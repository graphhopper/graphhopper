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

import com.graphhopper.Repeat;
import com.graphhopper.RepeatRule;
import com.graphhopper.routing.profiles.BooleanEncodedValue;
import com.graphhopper.routing.util.AllCHEdgesIterator;
import com.graphhopper.routing.util.CarFlagEncoder;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.routing.weighting.ShortestWeighting;
import com.graphhopper.routing.weighting.TurnWeighting;
import com.graphhopper.routing.weighting.Weighting;
import com.graphhopper.storage.CHGraph;
import com.graphhopper.storage.GraphBuilder;
import com.graphhopper.storage.GraphHopperStorage;
import com.graphhopper.storage.TurnCostExtension;
import com.graphhopper.util.EdgeIterator;
import com.graphhopper.util.EdgeIteratorState;
import com.graphhopper.util.GHUtility;
import com.graphhopper.util.PMap;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

import static org.junit.Assert.*;

/**
 * In this test we mainly test if {@link EdgeBasedNodeContractorTest} inserts the correct shortcuts when certain
 * nodes are contracted.
 *
 * @see CHTurnCostTest where node contraction is tested in combination with the routing query
 */
public class EdgeBasedNodeContractorTest {
    private final int maxCost = 10;
    private CHGraph chGraph;
    private CarFlagEncoder encoder;
    private GraphHopperStorage graph;
    private TurnCostExtension turnCostExtension;
    private TurnWeighting turnWeighting;
    private TurnWeighting chTurnWeighting;

    @Rule
    public RepeatRule repeatRule = new RepeatRule();

    @Before
    public void setup() {
        // its important to use @Before when using RepeatRule!
        initialize();
    }

    private void initialize() {
        encoder = new CarFlagEncoder(5, 5, maxCost);
        EncodingManager encodingManager = EncodingManager.create(encoder);
        Weighting weighting = new ShortestWeighting(encoder);
        PreparationWeighting preparationWeighting = new PreparationWeighting(weighting);
        graph = new GraphBuilder(encodingManager).setCHGraph(weighting).setEdgeBasedCH(true).create();
        turnCostExtension = (TurnCostExtension) graph.getExtension();
        turnWeighting = new TurnWeighting(weighting, turnCostExtension);
        chTurnWeighting = new TurnWeighting(preparationWeighting, turnCostExtension);
        chGraph = graph.getGraph(CHGraph.class);
    }

    @Test
    public void testContractNodes_simpleLoop() {
        //     2-3
        //     | |
        //  6- 7-8
        //     |
        //     9
        graph.edge(6, 7, 2, false);
        final EdgeIteratorState edge7to8 = graph.edge(7, 8, 2, false);
        final EdgeIteratorState edge8to3 = graph.edge(8, 3, 1, false);
        final EdgeIteratorState edge3to2 = graph.edge(3, 2, 2, false);
        final EdgeIteratorState edge2to7 = graph.edge(2, 7, 1, false);
        graph.edge(7, 9, 1, false);
        graph.freeze();
        setMaxLevelOnAllNodes();

        addRestriction(6, 7, 9);
        addTurnCost(8, 3, 2, 2);

        contractNodes(5, 6, 3, 2, 9, 1, 8, 4, 7, 0);
        checkShortcuts(
                createShortcut(8, 2, edge8to3, edge3to2, 5),
                createShortcut(8, 7, edge8to3.getEdge(), edge2to7.getEdge(), 6, edge2to7.getEdge(), 6),
                createShortcut(7, 7, edge7to8.getEdge(), edge2to7.getEdge(), edge7to8.getEdge(), 7, 8)
        );
    }

    @Test
    public void testContractNodes_necessaryAlternative() {
        //      1
        //      |    can't go 1->6->3
        //      v
        // 2 -> 6 -> 3 -> 5 -> 4
        //      |    ^
        //      -> 0-|
        final EdgeIteratorState e6to0 = graph.edge(6, 0, 4, false);
        final EdgeIteratorState e0to3 = graph.edge(0, 3, 5, false);
        graph.edge(1, 6, 1, false);
        final EdgeIteratorState e6to3 = graph.edge(6, 3, 1, false);
        final EdgeIteratorState e3to5 = graph.edge(3, 5, 2, false);
        graph.edge(2, 6, 1, false);
        graph.edge(5, 4, 2, false);
        graph.freeze();
        setMaxLevelOnAllNodes();
        addRestriction(1, 6, 3);
        contractAllNodesInOrder();
        checkShortcuts(
                // from contracting node 0: need a shortcut because of turn restriction
                createShortcut(6, 3, e6to0, e0to3, 9),
                // from contracting node 3: two shortcuts:
                // 1) in case we come from 1->6 (cant turn left)
                // 2) in case we come from 2->6 (going via node 0 would be more expensive)
                createShortcut(6, 5, e6to0.getEdge(), e3to5.getEdge(), 7, e3to5.getEdge(), 11),
                createShortcut(6, 5, e6to3, e3to5, 3)
        );
    }

    @Test
    public void testContractNodes_alternativeNecessary_noUTurn() {
        //    /->0-->
        //   v       \
        //  4 <-----> 2 -> 3 -> 1
        EdgeIteratorState e0to4 = graph.edge(4, 0, 3, true);
        EdgeIteratorState e0to2 = graph.edge(0, 2, 5, false);
        EdgeIteratorState e2to3 = graph.edge(2, 3, 2, false);
        EdgeIteratorState e1to3 = graph.edge(3, 1, 2, false);
        EdgeIteratorState e2to4 = graph.edge(4, 2, 2, true);
        graph.freeze();

        setMaxLevelOnAllNodes();
        contractAllNodesInOrder();
        checkShortcuts(
                // from contraction of node 0
                createShortcut(4, 2, e0to4, e0to2, 8),
                // from contraction of node 2
                // It might look like it is always better to go directly from 4 to 2, but when we come from edge (2->4)
                // we may not do a u-turn at 4.
                createShortcut(4, 3, e0to4.getEdge(), e2to3.getEdge(), 5, e2to3.getEdge(), 10),
                createShortcut(4, 3, e2to4, e2to3, 4)
        );
    }

    @Test
    public void testContractNodes_bidirectionalLoop() {
        //  1   3
        //  |  /|
        //  0-4-6
        //    |
        //    5-2
        graph.edge(1, 0, 1, false);
        graph.edge(0, 4, 2, false);
        final EdgeIteratorState e4to6 = graph.edge(4, 6, 2, true);
        final EdgeIteratorState e3to6 = graph.edge(6, 3, 1, true);
        final EdgeIteratorState e3to4 = graph.edge(3, 4, 1, true);
        final EdgeIteratorState e4to5 = graph.edge(4, 5, 1, false);
        graph.edge(5, 2, 2, false);
        graph.freeze();

        // enforce loop (going counter-clockwise)
        addRestriction(0, 4, 5);
        addTurnCost(6, 3, 4, 2);
        addTurnCost(4, 3, 6, 4);
        setMaxLevelOnAllNodes();

        contractAllNodesInOrder();
        checkShortcuts(
                // from contraction of node 3
                createShortcut(4, 6, e3to4, e3to6, 6),
                createShortcut(6, 4, e3to6, e3to4, 4),
                // from contraction of node 4
                // two 'parallel' shortcuts to preserve shortest paths to 5 when coming from 4->6 and 3->6 !!
                createShortcut(6, 5, e3to6.getEdge(), e4to5.getEdge(), 8, e4to5.getEdge(), 5),
                createShortcut(6, 5, e4to6, e4to5, 3)
        );
    }

    @Test
    public void testContractNode_twoNormalEdges_noSourceEdgeToConnect() {
        // 1 --> 0 --> 2 --> 3
        graph.edge(1, 0, 3, false);
        graph.edge(0, 2, 5, false);
        graph.edge(2, 3, 1, false);
        graph.freeze();
        setMaxLevelOnAllNodes();
        contractNodes(0);
        // it looks like we need a shortcut from 1 to 2, but shortcuts are only introduced to maintain shortest paths
        // between original edges, so there should be no shortcuts here, because there is no original edge incoming
        // to node 1.
        checkShortcuts();
    }

    @Test
    public void testContractNode_twoNormalEdges_noTargetEdgeToConnect() {
        // 3 --> 1 --> 0 --> 2
        graph.edge(3, 1, 1, false);
        graph.edge(1, 0, 3, false);
        graph.edge(0, 2, 5, false);
        graph.freeze();
        setMaxLevelOnAllNodes();
        contractNodes(0);
        // it looks like we need a shortcut from 1 to 2, but shortcuts are only introduced to maintain shortest paths
        // between original edges, so there should be no shortcuts here, because there is no original edge outgoing
        // from node 2.
        checkShortcuts();
    }

    @Test
    public void testContractNode_twoNormalEdges_noEdgesToConnectBecauseOfTurnRestrictions() {
        // 0 --> 3 --> 2 --> 4 --> 1
        graph.edge(0, 3, 1, false);
        graph.edge(3, 2, 3, false);
        graph.edge(2, 4, 5, false);
        graph.edge(4, 1, 1, false);
        addRestriction(0, 3, 2);
        addRestriction(2, 4, 1);
        graph.freeze();
        setMaxLevelOnAllNodes();
        contractNodes(2);
        // It looks like we need a shortcut from 3 to 4, but due to the turn restrictions there should be none.
        checkShortcuts();
    }

    @Test
    public void testContractNode_twoNormalEdges_noTurncosts() {
        // 0 --> 3 --> 2 --> 4 --> 1
        graph.edge(0, 3, 1, false);
        final EdgeIteratorState e3to2 = graph.edge(3, 2, 3, false);
        final EdgeIteratorState e2to4 = graph.edge(2, 4, 5, false);
        graph.edge(4, 1, 1, false);
        graph.freeze();
        setMaxLevelOnAllNodes();
        EdgeBasedNodeContractor nodeContractor = createNodeContractor();
        contractNode(nodeContractor, 0, 0);
        contractNode(nodeContractor, 1, 1);
        // no shortcuts so far
        checkShortcuts();
        // contracting node 2 should yield a shortcut to preserve the shortest path from (1->2) to (3->4). note that
        // it does not matter that nodes 0 and 1 have lower level and are contracted already!
        contractNode(nodeContractor, 2, 2);
        checkShortcuts(createShortcut(3, 4, e3to2, e2to4, 8));
    }

    @Test
    public void testContractNode_twoNormalEdges_noShortcuts() {
        // 0 --> 1 --> 2 --> 3 --> 4
        graph.edge(0, 1, 1, false);
        graph.edge(1, 2, 3, false);
        graph.edge(2, 3, 5, false);
        graph.edge(3, 4, 1, false);
        graph.freeze();
        setMaxLevelOnAllNodes();
        contractAllNodesInOrder();
        // for each contraction the node levels are such that no shortcuts are introduced
        checkShortcuts();
    }

    @Test
    public void testContractNode_twoNormalEdges_noOutgoingEdges() {
        // 0 --> 1 --> 2 <-- 3 <-- 4
        graph.edge(0, 1, 1, false);
        graph.edge(1, 2, 3, false);
        graph.edge(3, 2, 5, false);
        graph.edge(4, 3, 1, false);
        graph.freeze();
        setMaxLevelOnAllNodes();
        contractNodes(2);
        checkShortcuts();
    }

    @Test
    public void testContractNode_twoNormalEdges_noIncomingEdges() {
        // 0 <-- 1 <-- 2 --> 3 --> 4
        graph.edge(1, 0, 1, false);
        graph.edge(2, 1, 3, false);
        graph.edge(2, 3, 5, false);
        graph.edge(3, 4, 1, false);
        graph.freeze();
        setMaxLevelOnAllNodes();
        contractNodes(2);
        checkShortcuts();
    }

    @Test
    public void testContractNode_duplicateOutgoingEdges_differentWeight() {
        // duplicate edges with different weight occur frequently, because there might be different ways between
        // the tower nodes

        // 0 -> 1 -> 2 -> 3 -> 4
        //            \->/
        graph.edge(0, 1, 1, false);
        graph.edge(1, 2, 1, false);
        graph.edge(2, 3, 2, false);
        graph.edge(2, 3, 1, false);
        graph.edge(3, 4, 1, false);
        graph.freeze();
        setMaxLevelOnAllNodes();
        contractNodes(2);
        // there should be only one shortcut
        checkShortcuts(
                createShortcut(1, 3, 1, 3, 1, 3, 2)
        );
    }

    @Test
    public void testContractNode_duplicateIncomingEdges_differentWeight() {
        // 0 -> 1 -> 2 -> 3 -> 4
        //       \->/
        graph.edge(0, 1, 1, false);
        graph.edge(1, 2, 2, false);
        graph.edge(1, 2, 1, false);
        graph.edge(2, 3, 1, false);
        graph.edge(3, 4, 1, false);
        graph.freeze();
        setMaxLevelOnAllNodes();
        contractNodes(2);
        checkShortcuts(
                createShortcut(1, 3, 2, 3, 2, 3, 2)
        );
    }

    @Test
    public void testContractNode_duplicateOutgoingEdges_sameWeight() {
        // there might be duplicates of edges with the same weight, for example here:
        // http://www.openstreetmap.org/#map=19/51.93569/10.5781
        // http://www.openstreetmap.org/way/446299649
        // this test makes sure that the necessary shortcuts are introduced nonetheless

        // 0 -> 1 -> 2 -> 3 -> 4
        //            \->/
        graph.edge(0, 1, 1, false);
        graph.edge(1, 2, 1, false);
        graph.edge(2, 3, 1, false);
        graph.edge(2, 3, 1, false);
        graph.edge(3, 4, 1, false);
        graph.freeze();
        setMaxLevelOnAllNodes();
        contractNodes(2);
        checkNumShortcuts(1);
    }

    @Test
    @Repeat(times = 10)
    public void testContractNode_duplicateIncomingEdges_sameWeight() {
        // 0 -> 1 -> 2 -> 3 -> 4
        //       \->/
        graph.edge(0, 1, 1, false);
        graph.edge(1, 2, 1, false);
        graph.edge(1, 2, 1, false);
        graph.edge(2, 3, 1, false);
        graph.edge(3, 4, 1, false);
        graph.freeze();
        setMaxLevelOnAllNodes();
        contractNodes(2);
        checkNumShortcuts(1);
    }

    @Test
    public void testContractNode_twoNormalEdges_withTurnCost() {
        // 0 --> 3 --> 2 --> 4 --> 1
        graph.edge(0, 3, 1, false);
        final EdgeIteratorState e3to2 = graph.edge(3, 2, 3, false);
        final EdgeIteratorState e2to4 = graph.edge(2, 4, 5, false);
        graph.edge(4, 1, 1, false);
        addTurnCost(3, 2, 4, 4);
        graph.freeze();
        setMaxLevelOnAllNodes();
        contractNodes(2);
        double weight = calcWeight(e3to2, e2to4);
        checkShortcuts(createShortcut(3, 4, e3to2, e2to4, weight));
    }

    @Test
    public void testContractNode_twoNormalEdges_withTurnRestriction() {
        // 0 --> 3 --> 2 --> 4 --> 1
        graph.edge(0, 3, 1, false);
        graph.edge(3, 2, 3, false);
        graph.edge(2, 4, 5, false);
        graph.edge(4, 1, 1, false);
        addRestriction(3, 2, 4);
        graph.freeze();
        setMaxLevelOnAllNodes();
        contractNodes(2);
        checkShortcuts();
    }

    @Test
    public void testContractNode_twoNormalEdges_bidirectional() {
        // 0 -- 3 -- 2 -- 4 -- 1
        graph.edge(0, 3, 1, true);
        final EdgeIteratorState e3to2 = graph.edge(3, 2, 3, true);
        final EdgeIteratorState e2to4 = graph.edge(2, 4, 5, true);
        graph.edge(4, 1, 1, true);
        addTurnCost(e3to2, e2to4, 2, 4);
        addTurnCost(e2to4, e3to2, 2, 4);
        graph.freeze();
        setMaxLevelOnAllNodes();
        contractNodes(2);
        checkShortcuts(
                // note that for now we add a shortcut for each direction. using fwd/bwd flags would be more efficient,
                // but requires a more sophisticated way to determine the 'first' and 'last' original edges at various
                // places
                createShortcut(3, 4, e3to2, e2to4, 12),
                createShortcut(4, 3, e2to4, e3to2, 12)
        );
    }

    @Test
    public void testContractNode_twoNormalEdges_bidirectional_differentCosts() {
        // 0 -- 3 -- 2 -- 4 -- 1
        graph.edge(0, 3, 1, true);
        final EdgeIteratorState e2to3 = graph.edge(3, 2, 3, true);
        final EdgeIteratorState e2to4 = graph.edge(2, 4, 5, true);
        graph.edge(4, 1, 1, true);
        addTurnCost(e2to3, e2to4, 2, 4);
        addTurnCost(e2to4, e2to3, 2, 7);
        graph.freeze();
        setMaxLevelOnAllNodes();
        contractNodes(2);
        checkShortcuts(
                createShortcut(3, 4, e2to3, e2to4, 12),
                createShortcut(4, 3, e2to4, e2to3, 15)
        );
    }

    @Test
    public void testContractNode_multiple_bidirectional_linear() {
        // 3 -- 2 -- 1 -- 4
        graph.edge(3, 2, 2, true);
        graph.edge(2, 1, 3, true);
        graph.edge(1, 4, 6, true);
        graph.freeze();
        setMaxLevelOnAllNodes();

        contractNodes(1, 2);
        // no shortcuts needed
        checkShortcuts();
    }

    @Test
    public void testContractNode_twoNormalEdges_withTurnCost_andLoop() {
        runTestWithTurnCostAndLoop(false);
    }

    @Test
    public void testContractNode_twoNormalEdges_withTurnCost_andLoop_loopHelps() {
        runTestWithTurnCostAndLoop(true);
    }

    private void runTestWithTurnCostAndLoop(boolean loopHelps) {
        //            />\
        //            \ /
        // 0 --> 3 --> 2 --> 4 --> 1
        graph.edge(0, 3, 1, false);
        final EdgeIteratorState e3to2 = graph.edge(3, 2, 3, false);
        final EdgeIteratorState e2to2 = graph.edge(2, 2, 2, false);
        final EdgeIteratorState e2to4 = graph.edge(2, 4, 5, false);
        graph.edge(4, 1, 1, false);

        addTurnCost(e3to2, e2to2, 2, 2);
        addTurnCost(e2to2, e2to4, 2, 1);
        addTurnCost(e3to2, e2to4, 2, loopHelps ? 6 : 3);
        graph.freeze();
        setMaxLevelOnAllNodes();

        createNodeContractor().contractNode(2);
        if (loopHelps) {
            // it is better to take the loop at node 2, so we need to introduce two shortcuts where the second contains
            // the first (this is important for path unpacking)
            checkShortcuts(
                    createShortcut(3, 2, e3to2, e2to2, 7),
                    createShortcut(3, 4, e3to2.getEdge(), e2to4.getEdge(), 5, e2to4.getEdge(), 13));
        } else {
            // taking the loop would be worse, so the path is just 3-2-4 and we only need a single shortcut
            checkShortcuts(
                    createShortcut(3, 4, e3to2, e2to4, 11));
        }
    }

    @Test
    public void testContractNode_shortcutDoesNotSpanUTurn() {
        // 2 -> 7 -> 3 -> 5 -> 6
        //           |
        //     1 <-> 4
        final EdgeIteratorState e7to3 = graph.edge(7, 3, 1, false);
        final EdgeIteratorState e3to5 = graph.edge(3, 5, 1, false);
        final EdgeIteratorState e3to4 = graph.edge(3, 4, 2, true);
        graph.edge(2, 7, 1, false);
        graph.edge(5, 6, 1, false);
        graph.edge(1, 4, 1, true);
        graph.freeze();
        setMaxLevelOnAllNodes();
        addRestriction(7, 3, 5);
        contractNodes(3, 4);
        checkShortcuts(
                // from contracting node 3
                createShortcut(7, 4, e7to3, e3to4, 3),
                createShortcut(4, 5, e3to4, e3to5, 3)
                // important! no shortcut from 7 to 5 when contracting node 4, because it includes a u-turn
        );
    }

    @Test
    public void testContractNode_multiple_loops_directTurnIsBest() {
        // turning on any of the loops is restricted so we take the direct turn -> one extra shortcuts
        GraphWithTwoLoops g = new GraphWithTwoLoops(maxCost, maxCost, 1, 2, 3, 4);
        g.contractAndCheckShortcuts(
                createShortcut(7, 8, g.e7to6, g.e6to8, 11));
    }

    @Test
    public void testContractNode_multiple_loops_leftLoopIsBest() {
        // direct turn is restricted, so we take the left loop -> two extra shortcuts
        GraphWithTwoLoops g = new GraphWithTwoLoops(2, maxCost, 1, 2, 3, maxCost);
        g.contractAndCheckShortcuts(
                createShortcut(7, 6, g.e7to6.getEdge(), g.e1to6.getEdge(), g.e7to6.getEdge(), g.getScEdge(1), 12),
                createShortcut(7, 8, g.e7to6.getEdge(), g.e6to8.getEdge(), g.getScEdge(4), g.e6to8.getEdge(), 20)
        );
    }

    @Test
    public void testContractNode_multiple_loops_rightLoopIsBest() {
        // direct turn is restricted, going on left loop is expensive, so we take the right loop -> two extra shortcuts
        GraphWithTwoLoops g = new GraphWithTwoLoops(8, 1, 1, 2, 3, maxCost);
        g.contractAndCheckShortcuts(
                createShortcut(7, 6, g.e7to6.getEdge(), g.e3to6.getEdge(), g.e7to6.getEdge(), g.getScEdge(3), 12),
                createShortcut(7, 8, g.e7to6.getEdge(), g.e6to8.getEdge(), g.getScEdge(4), g.e6to8.getEdge(), 21)
        );
    }

    @Test
    public void testContractNode_multiple_loops_leftRightLoopIsBest() {
        // multiple turns are restricted, it is best to take the left and the right loop -> three extra shortcuts
        GraphWithTwoLoops g = new GraphWithTwoLoops(3, maxCost, 1, maxCost, 3, maxCost);
        g.contractAndCheckShortcuts(
                createShortcut(7, 6, g.e7to6.getEdge(), g.e1to6.getEdge(), g.e7to6.getEdge(), g.getScEdge(1), 13),
                createShortcut(7, 6, g.e7to6.getEdge(), g.e3to6.getEdge(), g.getScEdge(4), g.getScEdge(3), 24),
                createShortcut(7, 8, g.e7to6.getEdge(), g.e6to8.getEdge(), g.getScEdge(5), g.e6to8.getEdge(), 33)
        );
    }

    @Test
    public void testContractNode_multiple_loops_rightLeftLoopIsBest() {
        // multiple turns are restricted, it is best to take the right and the left loop -> three extra shortcuts
        GraphWithTwoLoops g = new GraphWithTwoLoops(maxCost, 5, 4, 2, maxCost, maxCost);
        g.contractAndCheckShortcuts(
                createShortcut(7, 6, g.e7to6.getEdge(), g.e3to6.getEdge(), g.e7to6.getEdge(), g.getScEdge(3), 16),
                createShortcut(7, 6, g.e7to6.getEdge(), g.e1to6.getEdge(), g.getScEdge(4), g.getScEdge(1), 25),
                createShortcut(7, 8, g.e7to6.getEdge(), g.e6to8.getEdge(), g.getScEdge(5), g.e6to8.getEdge(), 33)
        );
    }

    //    1 4 2
    //    |\|/|
    //    0-6-3
    //     /|\
    // 9--7 5 8--10
    private class GraphWithTwoLoops {
        final int centerNode = 6;
        final EdgeIteratorState e0to1 = graph.edge(0, 1, 3, false);
        final EdgeIteratorState e1to6 = graph.edge(1, 6, 2, false);
        final EdgeIteratorState e6to0 = graph.edge(6, 0, 4, false);
        final EdgeIteratorState e2to3 = graph.edge(2, 3, 2, false);
        final EdgeIteratorState e3to6 = graph.edge(3, 6, 7, false);
        final EdgeIteratorState e6to2 = graph.edge(6, 2, 1, false);
        final EdgeIteratorState e7to6 = graph.edge(7, 6, 1, false);
        final EdgeIteratorState e6to8 = graph.edge(6, 8, 6, false);
        final EdgeIteratorState e9to7 = graph.edge(9, 7, 2, false);
        final EdgeIteratorState e8to10 = graph.edge(8, 10, 3, false);
        // these two edges help to avoid loop avoidance for the left and right loops
        final EdgeIteratorState e4to6 = graph.edge(4, 6, 1, false);
        final EdgeIteratorState e5to6 = graph.edge(5, 6, 1, false);
        final int numEdges = 12;

        GraphWithTwoLoops(int turnCost70, int turnCost72, int turnCost12, int turnCost18, int turnCost38, int turnCost78) {
            addCostOrRestriction(e7to6, e6to0, centerNode, turnCost70);
            addCostOrRestriction(e7to6, e6to2, centerNode, turnCost72);
            addCostOrRestriction(e7to6, e6to8, centerNode, turnCost78);
            addCostOrRestriction(e1to6, e6to2, centerNode, turnCost12);
            addCostOrRestriction(e1to6, e6to8, centerNode, turnCost18);
            addCostOrRestriction(e3to6, e6to8, centerNode, turnCost38);
            // restrictions to make sure that no loop avoidance takes place when the left&right loops are contracted
            addRestriction(e4to6, e6to8, centerNode);
            addRestriction(e5to6, e6to2, centerNode);
            addRestriction(e4to6, e6to0, centerNode);

            graph.freeze();
            setMaxLevelOnAllNodes();
        }

        private void contractAndCheckShortcuts(Shortcut... shortcuts) {
            contractNodes(0, 1, 2, 3, 4, 5, 6);
            HashSet<Shortcut> expectedShortcuts = new HashSet<>();
            expectedShortcuts.addAll(Arrays.asList(
                    createShortcut(6, 1, e6to0, e0to1, 7),
                    createShortcut(6, 6, e6to0.getEdge(), e1to6.getEdge(), getScEdge(0), e1to6.getEdge(), 9),
                    createShortcut(6, 3, e6to2, e2to3, 3),
                    createShortcut(6, 6, e6to2.getEdge(), e3to6.getEdge(), getScEdge(2), e3to6.getEdge(), 10)
            ));
            expectedShortcuts.addAll(Arrays.asList(shortcuts));
            checkShortcuts(expectedShortcuts);
        }

        private int getScEdge(int shortcutId) {
            return numEdges + shortcutId;
        }
    }

    @Test
    public void testContractNode_detour_detourIsBetter() {
        // starting the detour by turning left at node 1 seems expensive but is still worth it because going straight
        // at node 2 when coming from node 1 is worse -> one shortcut required
        GraphWithDetour g = new GraphWithDetour(2, 9, 5, 1);
        contractNodes(0);
        checkShortcuts(
                createShortcut(1, 2, g.e1to0, g.e0to2, 7)
        );
    }

    @Test
    public void testContractNode_detour_detourIsWorse() {
        // starting the detour is cheap but going left at node 2 is expensive -> no shortcut
        GraphWithDetour g = new GraphWithDetour(4, 1, 1, 7);
        contractNodes(0);
        checkShortcuts();
    }

    //      0
    //     / \
    // 4--1---2--3
    private class GraphWithDetour {
        private final EdgeIteratorState e4to1 = graph.edge(4, 1, 2, false);
        private final EdgeIteratorState e1to0 = graph.edge(1, 0, 4, false);
        private final EdgeIteratorState e1to2 = graph.edge(1, 2, 3, false);
        private final EdgeIteratorState e0to2 = graph.edge(0, 2, 3, false);
        private final EdgeIteratorState e2to3 = graph.edge(2, 3, 2, false);

        GraphWithDetour(int turnCost42, int turnCost13, int turnCost40, int turnCost03) {
            addCostOrRestriction(e4to1, e1to2, 1, turnCost42);
            addCostOrRestriction(e4to1, e1to0, 1, turnCost40);
            addCostOrRestriction(e1to2, e2to3, 2, turnCost13);
            addCostOrRestriction(e0to2, e2to3, 2, turnCost03);
            graph.freeze();
            setMaxLevelOnAllNodes();
        }

    }

    @Test
    public void testContractNode_detour_multipleInOut_needsShortcut() {
        GraphWithDetourMultipleInOutEdges g = new GraphWithDetourMultipleInOutEdges(0, 0, 0, 1, 3);
        contractNodes(0);
        checkShortcuts(createShortcut(1, 4, g.e1to0, g.e0to4, 7));
    }

    @Test
    public void testContractNode_detour_multipleInOut_noShortcuts() {
        GraphWithDetourMultipleInOutEdges g = new GraphWithDetourMultipleInOutEdges(0, 0, 0, 0, 0);
        contractNodes(0);
        checkShortcuts();
    }

    @Test
    public void testContractNode_detour_multipleInOut_restrictedIn() {
        GraphWithDetourMultipleInOutEdges g = new GraphWithDetourMultipleInOutEdges(0, maxCost, 0, maxCost, 0);
        contractNodes(0);
        checkShortcuts();
    }

    // 5   3   7
    //  \ / \ /
    // 2-1-0-4-6
    private class GraphWithDetourMultipleInOutEdges {
        final EdgeIteratorState e5to1 = graph.edge(5, 1, 3, false);
        final EdgeIteratorState e2to1 = graph.edge(2, 1, 2, false);
        final EdgeIteratorState e1to3 = graph.edge(1, 3, 1, false);
        final EdgeIteratorState e3to4 = graph.edge(3, 4, 2, false);
        final EdgeIteratorState e1to0 = graph.edge(1, 0, 5, false);
        final EdgeIteratorState e0to4 = graph.edge(0, 4, 2, false);
        final EdgeIteratorState e4to6 = graph.edge(4, 6, 1, false);
        final EdgeIteratorState e4to7 = graph.edge(4, 7, 3, false);

        GraphWithDetourMultipleInOutEdges(int turnCost20, int turnCost50, int turnCost23, int turnCost53, int turnCost36) {
            addTurnCost(e1to3, e3to4, 3, 2);
            addCostOrRestriction(e2to1, e1to0, 1, turnCost20);
            addCostOrRestriction(e2to1, e1to3, 1, turnCost23);
            addCostOrRestriction(e5to1, e1to0, 1, turnCost50);
            addCostOrRestriction(e5to1, e1to3, 1, turnCost53);
            addCostOrRestriction(e3to4, e4to6, 4, turnCost36);
            graph.freeze();
            setMaxLevelOnAllNodes();
        }
    }

    @Test
    public void testContractNode_loopAvoidance_loopNecessary() {
        // turning from 3 via 2 to 4 is costly, it is better to take the 2-1-0-2 loop so a loop shortcut is required
        GraphWithLoop g = new GraphWithLoop(7);
        contractNodes(0, 1);
        final int numEdges = 6;
        checkShortcuts(
                createShortcut(2, 1, g.e2to0, g.e0to1, 3),
                createShortcut(2, 2, g.e2to0.getEdge(), g.e1to2.getEdge(), numEdges, g.e1to2.getEdge(), 4)
        );
    }

    @Test
    public void testContractNode_loopAvoidance_loopAvoidable() {
        // turning from 3 via 2 to 4 is cheap, it is better to go straight 3-2-4, no loop shortcut necessary
        GraphWithLoop g = new GraphWithLoop(3);
        contractNodes(0, 1);
        checkShortcuts(
                createShortcut(2, 1, g.e2to0, g.e0to1, 3)
        );
    }

    //   0 - 1
    //    \ /
    // 3 - 2 - 4
    //     |
    //     5
    private class GraphWithLoop {
        final EdgeIteratorState e0to1 = graph.edge(0, 1, 2, false);
        final EdgeIteratorState e1to2 = graph.edge(1, 2, 1, false);
        final EdgeIteratorState e2to0 = graph.edge(2, 0, 1, false);
        final EdgeIteratorState e3to2 = graph.edge(3, 2, 3, false);
        final EdgeIteratorState e2to4 = graph.edge(2, 4, 5, false);
        final EdgeIteratorState e5to2 = graph.edge(5, 2, 2, false);

        GraphWithLoop(int turnCost34) {
            addCostOrRestriction(e3to2, e2to4, 2, turnCost34);
            graph.freeze();
            setMaxLevelOnAllNodes();
        }
    }

    @Test
    public void testContractNode_witnessPathsAreFound() {
        //         2 ----- 7 - 10 
        //       / |       |
        // 0 - 1   3 - 4   |
        //     |   |      /     
        //     5 - 9 ---- 
        graph.edge(0, 1, 1, false);
        graph.edge(1, 2, 1, false);
        graph.edge(2, 3, 5, false);
        graph.edge(3, 4, 1, false);
        graph.edge(1, 5, 1, false);
        graph.edge(5, 9, 1, false);
        graph.edge(9, 3, 1, false);
        graph.edge(2, 7, 6, false);
        graph.edge(9, 7, 1, false);
        graph.edge(7, 10, 1, false);
        graph.freeze();
        setMaxLevelOnAllNodes();
        contractNodes(2);
        checkShortcuts();
    }

    @Test
    @Repeat(times = 10)
    public void testContractNode_noUnnecessaryShortcut_witnessPathOfEqualWeight() {
        // this test runs repeatedly because it might pass/fail by chance (because path lengths are equal)

        // 0 -> 1 -> 5
        //      v    v 
        //      2 -> 3 -> 4 -> 5
        graph.edge(0, 1, 1, false);
        graph.edge(1, 2, 1, false);
        graph.edge(1, 5, 1, false);
        EdgeIteratorState e2to3 = graph.edge(2, 3, 1, false);
        EdgeIteratorState e3to4 = graph.edge(3, 4, 1, false);
        graph.edge(4, 5, 1, false);
        EdgeIteratorState e5to3 = graph.edge(5, 3, 1, false);
        graph.freeze();
        setMaxLevelOnAllNodes();
        contractNodes(3, 2);
        // when contracting node 2 there is a witness (1-5-3-4) and no shortcut from 1 to 4 should be introduced.
        // what might be tricky here is that both the original path and the witness path have equal weight!
        checkShortcuts(
                createShortcut(2, 4, e2to3, e3to4, 2),
                createShortcut(5, 4, e5to3, e3to4, 2)
        );
    }

    @Test
    public void testContractNode_noUnnecessaryShortcut_differentWitnessesForDifferentOutEdges() {
        //         /--> 2 ---\
        //        /           \
        // 0 --> 1 ---> 3 ---> 5 --> 6 
        //        \           /
        //         \--> 4 ---/   
        graph.edge(0, 1, 1, false);
        graph.edge(1, 2, 1, false);
        graph.edge(1, 3, 1, false);
        graph.edge(1, 4, 1, false);
        graph.edge(2, 5, 1, true); // bidirectional
        graph.edge(3, 5, 1, false);
        graph.edge(4, 5, 1, true); // bidirectional
        graph.edge(5, 6, 1, false);
        graph.freeze();
        setMaxLevelOnAllNodes();
        contractNodes(3);

        // We do not need a shortcut here! we can only access node 1 from node 0 and at node 5 we can either go to 
        // node 2,4 or 6. To get to node 6 we can either take the northern witness via 2 or the southern one via 4.
        // to get to node 2 we need to take the witness via node 4 and vice versa. the interesting part here is that
        // we use a different witness depending on the target edge and even more that the witness paths itself yield
        // outgoing edges that need to be witnessed because edges 2->5 and 4->5 are bidirectional like the majority
        // of edges in road networks.
        checkShortcuts();
    }

    @Test
    public void testContractNode_noUnnecessaryShortcut_differentInitialEntriesForDifferentInEdges() {
        // this test shows a (quite realistic) example where the aggressive search finds a witness where the turn
        // replacement search described in the turn-cost CH article by Gaisberger/Vettel does not.

        //         /--- 2 ->-\
        //        /           \
        // 0 --> 1 ---> 3 ---> 5 --> 6 
        //        \           /
        //         \--- 4 ->-/   
        graph.edge(0, 1, 1, false);
        graph.edge(1, 2, 1, true); // bidirectional
        graph.edge(1, 3, 1, false);
        graph.edge(1, 4, 1, true); // bidirectional
        graph.edge(2, 5, 1, false);
        graph.edge(3, 5, 1, false);
        graph.edge(4, 5, 1, false);
        graph.edge(5, 6, 1, false);
        graph.freeze();
        setMaxLevelOnAllNodes();
        contractNodes(3);

        // We do not need a shortcut here! node 1 can be reached from nodes 0, 2 and 4 and from the target node 5 we can
        // only reach node 6. so coming into node 1 from node 0 we can either go north or south via nodes 2/4 to reach
        // the edge 5->6. If we come from node 2 we can take the southern witness via 4 and vice versa.
        // 
        // This is an example of an unnecessary shortcut introduced by the turn replacement algorithm, because the 
        // out turn replacement difference for the potential witnesses would be infinite at node 1. 
        // Note that this happens basically whenever there is a bidirectional edge (and u-turns are forbidden) !
        checkShortcuts();
    }

    @Test
    public void testContractNode_bidirectional_edge_at_fromNode() {
        int nodeToContract = 2;
        // we might come from (5->1) so we still need a way back to (3->4) -> we need a shortcut
        Shortcut expectedShortcuts = createShortcut(1, 3, 1, 2, 1, 2, 2);
        runTestWithBidirectionalEdgeAtFromNode(nodeToContract, false, expectedShortcuts);
    }

    @Test
    public void testContractNode_bidirectional_edge_at_fromNode_is() {
        int nodeToContract = 2;
        // we might come from (5->1) so we still need a way back to (3->4) -> we need a shortcut
        Shortcut expectedShortcuts = createShortcut(1, 3, 1, 2, 1, 2, 2);
        runTestWithBidirectionalEdgeAtFromNode(nodeToContract, true, expectedShortcuts);
    }

    @Test
    public void testContractNode_bidirectional_edge_at_fromNode_going_to_node() {
        int nodeToContract = 5;
        // wherever we come from we can always go via node 2 -> no shortcut needed
        Shortcut[] expectedShortcuts = new Shortcut[0];
        runTestWithBidirectionalEdgeAtFromNode(nodeToContract, false, expectedShortcuts);
    }

    private void runTestWithBidirectionalEdgeAtFromNode(int nodeToContract, boolean edge1to2bidirectional, Shortcut... expectedShortcuts) {
        // 0 -> 1 <-> 5
        //      v     v 
        //      2 --> 3 -> 4
        graph.edge(0, 1, 1, false);
        graph.edge(1, 2, 1, edge1to2bidirectional);
        graph.edge(2, 3, 1, false);
        graph.edge(3, 4, 1, false);
        graph.edge(1, 5, 1, true);
        graph.edge(5, 3, 1, false);
        graph.freeze();
        setMaxLevelOnAllNodes();
        contractNodes(nodeToContract);
        checkShortcuts(expectedShortcuts);
    }

    @Test
    public void testNodeContraction_directWitness() {
        // 0 -> 1 -> 2 -> 3 -> 4 -> 5 -> 6 -> 7 -> 8
        //     /      \                 /      \
        //10 ->        ------> 9 ------>        -> 11
        graph.edge(0, 1, 1, false);
        graph.edge(1, 2, 1, false);
        graph.edge(2, 3, 1, false);
        graph.edge(3, 4, 1, false);
        graph.edge(4, 5, 1, false);
        graph.edge(5, 6, 1, false);
        graph.edge(6, 7, 1, false);
        graph.edge(7, 8, 1, false);
        graph.edge(2, 9, 1, false);
        graph.edge(9, 6, 1, false);
        graph.edge(10, 1, 1, false);
        graph.edge(7, 11, 1, false);
        graph.freeze();
        setMaxLevelOnAllNodes();
        contractNodes(2, 6, 3, 5, 4);
        // note that the shortcut edge ids depend on the insertion order which might change when changing the implementation
        checkShortcuts(
                createShortcut(1, 3, 1, 2, 1, 2, 2),
                createShortcut(1, 9, 1, 8, 1, 8, 2),
                createShortcut(5, 7, 5, 6, 5, 6, 2),
                createShortcut(9, 7, 9, 6, 9, 6, 2),
                createShortcut(1, 4, 1, 3, 13, 3, 3),
                createShortcut(4, 7, 4, 6, 4, 15, 3)
        );
    }

    @Test
    public void testNodeContraction_witnessBetterBecauseOfTurnCostAtTargetNode() {
        // when we contract node 2 we should not stop searching for witnesses when edge 2->3 is settled, because then we miss
        // the witness path via 5 that is found later, but still has less weight because of the turn costs at node 3
        // 0 -> 1 -> 2 -> 3 -> 4
        //       \       /         
        //        -- 5 ->   
        graph.edge(0, 1, 1, false);
        graph.edge(1, 2, 1, false);
        graph.edge(2, 3, 1, false);
        graph.edge(3, 4, 1, false);
        graph.edge(1, 5, 3, false);
        graph.edge(5, 3, 1, false);
        addTurnCost(2, 3, 4, 5);
        addTurnCost(5, 3, 4, 2);
        graph.freeze();
        setMaxLevelOnAllNodes();
        contractNodes(2);
        checkShortcuts();
    }

    @Test
    public void testNodeContraction_letShortcutsWitnessEachOther_twoIn() {
        // coming from (0->1) it is best to go via node 2 to reach (5->6)
        // when contracting node 3 adding the shortcut 2->3->4 is therefore enough and we do not need an 
        // additional shortcut 1->3->4. while this seems obvious it requires that the 1->3->4 witness search is 
        // somehow 'aware' of the fact that the shortcut 2->3->4 will be introduced anyway.

        // 0 -> 1 -> 2 -> 3 -> 4 -> 5
        //       \        |
        //        ------->|
        graph.edge(0, 1, 1, false);
        graph.edge(1, 2, 1, false);
        graph.edge(2, 3, 1, false);
        graph.edge(3, 4, 1, false);
        graph.edge(1, 3, 4, false);
        graph.edge(4, 5, 1, false);

        graph.freeze();
        setMaxLevelOnAllNodes();
        contractNodes(3);
        checkShortcuts(
                createShortcut(2, 4, 2, 3, 2, 3, 2)
        );
    }

    @Test
    public void testNodeContraction_letShortcutsWitnessEachOther_twoOut() {
        // coming from (0->1) it is best to go via node 3 to reach (4->5)
        // when contracting node 2 adding the shortcut 1->2->3 is therefore enough and we do not need an 
        // additional shortcut 1->2->4.

        // 0 -> 1 -> 2 -> 3 -> 4 -> 5
        //           |        / 
        //           ------->
        graph.edge(0, 1, 1, false);
        graph.edge(1, 2, 1, false);
        graph.edge(2, 3, 1, false);
        graph.edge(3, 4, 1, false);
        graph.edge(4, 5, 1, false);
        graph.edge(2, 4, 4, false);

        graph.freeze();
        setMaxLevelOnAllNodes();
        contractNodes(2);
        checkShortcuts(
                createShortcut(1, 3, 1, 2, 1, 2, 2)
        );
    }

    @Test
    public void testNodeContraction_parallelEdges_onlyOneLoopShortcutNeeded() {
        // 0 -- 1 -- 2
        //  \--/
        EdgeIteratorState edge0 = graph.edge(0, 1, 2, true);
        EdgeIteratorState edge1 = graph.edge(1, 0, 4, true);
        graph.edge(1, 2, 5, true);
        addTurnCost(edge0, edge1, 0, 1);
        addTurnCost(edge1, edge0, 0, 2);
        graph.freeze();
        setMaxLevelOnAllNodes();
        contractNodes(0);
        // it is sufficient to be able to travel the 1-0-1 loop in one (the cheaper) direction
        checkShortcuts(
                createShortcut(1, 1, 0, 1, 0, 1, 7)
        );
    }

    @Test
    public void testNodeContraction_duplicateEdge_severalLoops() {
        // 5 -- 4 -- 3 -- 1
        // |\   |
        // | \  /        
        // -- 2 
        graph.edge(1, 3, 47, true);
        graph.edge(2, 4, 19, true);
        EdgeIteratorState e2 = graph.edge(2, 5, 38, true);
        EdgeIteratorState e3 = graph.edge(2, 5, 57, true); // note there is a duplicate edge here (with different weight) 
        graph.edge(3, 4, 10, true);
        EdgeIteratorState e5 = graph.edge(4, 5, 56, true);

        addTurnCost(e3, e2, 5, 4);
        addTurnCost(e2, e3, 5, 5);
        addTurnCost(e5, e3, 5, 3);
        addTurnCost(e3, e5, 5, 2);
        addTurnCost(e2, e5, 5, 2);
        addTurnCost(e5, e2, 5, 1);
        graph.freeze();
        setMaxLevelOnAllNodes();
        contractNodes(4, 5);
        // note that the shortcut edge ids depend on the insertion order which might change when changing the implementation
        checkNumShortcuts(11);
        checkShortcuts(
                // from node 4 contraction
                createShortcut(5, 3, 5, 4, 5, 4, 66),
                createShortcut(3, 5, 4, 5, 4, 5, 66),
                createShortcut(2, 3, 1, 4, 1, 4, 29),
                createShortcut(3, 2, 4, 1, 4, 1, 29),
                createShortcut(2, 5, 1, 5, 1, 5, 75),
                createShortcut(5, 2, 5, 1, 5, 1, 75),
                // from node 5 contraction
                createShortcut(2, 2, 3, 2, 3, 2, 99),
                createShortcut(2, 2, 3, 1, 3, 7, 134),
                createShortcut(2, 2, 1, 2, 10, 2, 114),
                createShortcut(2, 3, 2, 4, 2, 6, 106),
                createShortcut(3, 2, 4, 2, 8, 2, 105)
        );
    }

    @Test
    public void testNodeContraction_tripleConnection() {
        graph.edge(0, 1, 1.0, true);
        graph.edge(0, 1, 2.0, true);
        graph.edge(0, 1, 3.5, true);
        graph.freeze();
        setMaxLevelOnAllNodes();
        contractNodes(1);
        checkShortcuts(
                createShortcut(0, 0, 1, 2, 1, 2, 5.5),
                createShortcut(0, 0, 0, 2, 0, 2, 4.5),
                createShortcut(0, 0, 0, 1, 0, 1, 3.0)
        );
    }

    @Test
    public void testNodeContraction_fromAndToNodesEqual() {
        // 0 -> 1 -> 3
        //     / \
        //    v   ^
        //     \ /
        //      2
        graph.edge(0, 1, 1, false);
        graph.edge(1, 2, 1, false);
        graph.edge(2, 1, 1, false);
        graph.edge(1, 3, 1, false);
        graph.freeze();
        setMaxLevelOnAllNodes();
        contractNodes(2);
        checkShortcuts();
    }

    @Test
    public void testNodeContraction_node_in_loop() {
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
        setMaxLevelOnAllNodes();

        // enforce loop (going counter-clockwise)
        addRestriction(0, 4, 1);
        addTurnCost(4, 2, 3, 4);
        addTurnCost(3, 2, 4, 2);
        contractNodes(2);
        checkShortcuts(
                createShortcut(4, 3, 3, 2, 3, 2, 6),
                createShortcut(3, 4, 2, 3, 2, 3, 4)
        );
    }

    @Test
    public void testNodeContraction_turnRestrictionAndLoop() {
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
        setMaxLevelOnAllNodes();
        contractNodes(0);
        checkNumShortcuts(1);
    }

    @Test
    public void testNodeContraction_forwardLoopNeedsToBeRecognizedAsIncoming() {
        //     ---
        //     \ /
        // 0 -- 1 -- 2 -- 3 -- 4
        EdgeIteratorState edge0 = graph.edge(0, 1, 1, true);
        EdgeIteratorState edge1 = graph.edge(1, 1, 1, false);
        EdgeIteratorState edge2 = graph.edge(1, 2, 1, true);
        EdgeIteratorState edge3 = graph.edge(2, 3, 1, false);
        EdgeIteratorState edge4 = graph.edge(3, 4, 1, false);
        addRestriction(edge0, edge2, 1);
        graph.freeze();
        setMaxLevelOnAllNodes();
        contractNodes(2);
        checkShortcuts(
                // we need a shortcut going from 1 to 3, but this is not entirely trivial, because it is crucial that
                // the loop at node 1 is recognized as an incoming edge at node 1 although it is only 'unidirectional',
                // i.e. it has only a fwd flag
                createShortcut(1, 3, edge2, edge3, 2)
        );
    }

    @Test
    public void testNodeContraction_minorWeightDeviation() {
        // 0 -> 1 -> 2 -> 3 -> 4
        graph.edge(0, 1, 51.401, false);
        graph.edge(1, 2, 70.041, false);
        graph.edge(2, 3, 75.806, false);
        graph.edge(3, 4, 05.003, false);
        graph.freeze();
        setMaxLevelOnAllNodes();
        contractNodes(2);
        checkShortcuts(
                createShortcut(1, 3, 1, 2, 1, 2, 145.847)
        );
    }

    @Test
    public void testNodeContraction_zeroWeightLoop_loopOnly() {
        // zero weight loops are quite a headache..., also see #1355
        //                  /|
        // 0 -> 1 -> 2 -> 3 --
        graph.edge(0, 1, 1, false);
        graph.edge(1, 2, 1, false);
        graph.edge(2, 3, 1, false);
        graph.edge(3, 3, 0, false);
        graph.freeze();
        setMaxLevelOnAllNodes();
        contractNodes(2);
        checkShortcuts(
                createShortcut(1, 3, 1, 2, 1, 2, 2)
        );
    }

    @Test
    public void testNodeContraction_zeroWeightLoop_loopAndEdge() {
        //                  /|
        // 0 -> 1 -> 2 -> 3 --
        //                |
        //                4
        graph.edge(0, 1, 1, false);
        graph.edge(1, 2, 1, false);
        graph.edge(2, 3, 1, false);
        graph.edge(3, 3, 0, false);
        graph.edge(3, 4, 1, false);
        graph.freeze();
        setMaxLevelOnAllNodes();
        contractNodes(2);
        checkShortcuts(
                createShortcut(1, 3, 1, 2, 1, 2, 2)
        );
    }

    @Test
    public void testNodeContraction_zeroWeightLoop_twoLoops() {
        //                  /|
        // 0 -> 1 -> 2 -> 3 --
        //                  \|
        graph.edge(0, 1, 1, false);
        graph.edge(1, 2, 1, false);
        graph.edge(2, 3, 1, false);
        graph.edge(3, 3, 0, false);
        graph.edge(3, 3, 0, false);
        graph.freeze();
        setMaxLevelOnAllNodes();
        contractNodes(2);
        checkShortcuts(
                createShortcut(1, 3, 1, 2, 1, 2, 2)
        );
    }

    @Test
    public void testNodeContraction_zeroWeightLoop_twoLoopsAndEdge_edgeFirst() {
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
        setMaxLevelOnAllNodes();
        contractNodes(2);
        checkShortcuts(
                createShortcut(1, 3, 1, 2, 1, 2, 2)
        );
    }

    @Test
    public void testNodeContraction_zeroWeightLoop_twoLoopsAndEdge_loopsFirst() {
        //                  /|
        // 0 -> 1 -> 2 -> 3 --
        //                | \|
        //                4
        graph.edge(0, 1, 1, false);
        graph.edge(1, 2, 1, false);
        graph.edge(3, 3, 0, false);
        graph.edge(3, 3, 0, false);
        graph.edge(2, 3, 1, false);
        graph.edge(3, 4, 1, false);
        graph.freeze();
        setMaxLevelOnAllNodes();
        contractNodes(2);
        checkShortcuts(
                createShortcut(1, 3, 1, 4, 1, 4, 2)
        );
    }

    @Test
    public void testNodeContraction_zeroWeightLoop_manyLoops() {
        //                  /| many
        // 0 -> 1 -> 2 -> 3 --
        //                | 
        //                4
        graph.edge(3, 3, 0, false);
        graph.edge(0, 1, 1, false);
        graph.edge(3, 3, 0, false);
        graph.edge(1, 2, 1, false);
        graph.edge(3, 3, 0, false);
        graph.edge(2, 3, 1, false);
        graph.edge(3, 3, 0, false);
        graph.edge(3, 3, 0, false);
        graph.edge(3, 4, 1, false);
        graph.edge(3, 3, 0, false);
        graph.freeze();
        setMaxLevelOnAllNodes();
        contractNodes(2);
        checkShortcuts(
                createShortcut(1, 3, 3, 5, 3, 5, 2)
        );
    }

    @Test
    public void testNodeContraction_zeroWeightLoop_twoLoopsAndEdge_withTurnRestriction() {
        //                  /|
        // 0 -> 1 -> 2 -> 3 --
        //                | 
        //                4
        graph.edge(0, 1, 1, false);
        graph.edge(1, 2, 1, false);
        EdgeIteratorState edge2 = graph.edge(2, 3, 1, false);
        EdgeIteratorState edge3 = graph.edge(3, 3, 0, false);
        EdgeIteratorState edge4 = graph.edge(3, 4, 1, false);
        // add a few more loops to make this test more difficult to pass
        graph.edge(3, 3, 0, false);
        graph.edge(3, 3, 0, false);
        // we have to use the zero weight loop so it may not be excluded
        addTurnCost(edge2, edge3, 3, 5);
        addRestriction(edge2, edge4, 3);
        graph.freeze();
        setMaxLevelOnAllNodes();
        contractNodes(2);
        checkNumShortcuts(1);
    }

    @Test
    public void testNodeContraction_numPolledEdges() {
        graph.edge(3, 2, 71.203000, false);
        graph.edge(0, 3, 79.003000, false);
        graph.edge(2, 0, 21.328000, false);
        graph.edge(2, 4, 16.499000, false);
        graph.edge(4, 2, 16.487000, false);
        graph.edge(6, 1, 55.603000, false);
        graph.edge(2, 1, 33.453000, false);
        graph.edge(4, 5, 29.665000, false);
        graph.freeze();
        setMaxLevelOnAllNodes();
        EdgeBasedNodeContractor nodeContractor = createNodeContractor();
        nodeContractor.contractNode(0);
        assertTrue("too many edges polled: " + nodeContractor.getNumPolledEdges(),
                nodeContractor.getNumPolledEdges() <= 8);
    }

    private void contractNode(NodeContractor nodeContractor, int node, int level) {
        nodeContractor.contractNode(node);
        chGraph.setLevel(node, level);
    }

    private void contractAllNodesInOrder() {
        EdgeBasedNodeContractor nodeContractor = createNodeContractor();
        for (int node = 0; node < graph.getNodes(); ++node) {
            nodeContractor.contractNode(node);
            chGraph.setLevel(node, node);
        }
    }

    /**
     * contracts the given nodes and sets the node levels in order.
     * this method may only be called once per test !
     */
    private void contractNodes(int... nodes) {
        EdgeBasedNodeContractor nodeContractor = createNodeContractor();
        for (int i = 0; i < nodes.length; ++i) {
            nodeContractor.contractNode(nodes[i]);
            chGraph.setLevel(nodes[i], i);
        }
    }

    private EdgeBasedNodeContractor createNodeContractor() {
        EdgeBasedNodeContractor nodeContractor = new EdgeBasedNodeContractor(chGraph, chTurnWeighting, new PMap());
        nodeContractor.initFromGraph();
        return nodeContractor;
    }

    private double calcWeight(EdgeIteratorState edge1, EdgeIteratorState edge2) {
        return turnWeighting.calcWeight(edge1, false, EdgeIterator.NO_EDGE) +
                turnWeighting.calcWeight(edge2, false, edge1.getEdge());
    }

    private void addRestriction(EdgeIteratorState inEdge, EdgeIteratorState outEdge, int viaNode) {
        turnCostExtension.addTurnInfo(inEdge.getEdge(), viaNode, outEdge.getEdge(), encoder.getTurnFlags(true, 0));
    }

    private void addRestriction(int from, int via, int to) {
        addRestriction(getEdge(from, via), getEdge(via, to), via);
    }

    private void addTurnCost(EdgeIteratorState inEdge, EdgeIteratorState outEdge, int viaNode, double cost) {
        turnCostExtension.addTurnInfo(inEdge.getEdge(), viaNode, outEdge.getEdge(), encoder.getTurnFlags(false, cost));
    }

    private void addTurnCost(int from, int via, int to, int cost) {
        addTurnCost(getEdge(from, via), getEdge(via, to), via, cost);
    }

    private void addCostOrRestriction(EdgeIteratorState inEdge, EdgeIteratorState outEdge, int viaNode, int cost) {
        if (cost >= maxCost) {
            addRestriction(inEdge, outEdge, viaNode);
        } else {
            addTurnCost(inEdge, outEdge, viaNode, cost);
        }
    }

    private EdgeIteratorState getEdge(int from, int to) {
        return GHUtility.getEdge(graph, from, to);
    }

    private Shortcut createShortcut(int from, int to, EdgeIteratorState edge1, EdgeIteratorState edge2, double weight) {
        return createShortcut(from, to, edge1.getEdge(), edge2.getEdge(), edge1.getEdge(), edge2.getEdge(), weight);
    }

    private Shortcut createShortcut(int from, int to, int firstOrigEdge, int lastOrigEdge, int skipEdge1, int skipEdge2, double weight) {
        boolean fwd = true;
        boolean bwd = false;
        return new Shortcut(from, to, firstOrigEdge, lastOrigEdge, skipEdge1, skipEdge2, weight, fwd, bwd);
    }

    /**
     * Queries the ch graph and checks if the graph's shortcuts match the given expected shortcuts.
     */
    private void checkShortcuts(Shortcut... expectedShortcuts) {
        Set<Shortcut> expected = setOf(expectedShortcuts);
        if (expected.size() != expectedShortcuts.length) {
            fail("was given duplicate shortcuts");
        }
        checkShortcuts(expected);
    }

    private void checkShortcuts(Set<Shortcut> expected) {
        assertEquals(expected, getCurrentShortcuts());
    }

    private void checkNumShortcuts(int expected) {
        assertEquals(expected, getCurrentShortcuts().size());
    }

    private Set<Shortcut> getCurrentShortcuts() {
        Set<Shortcut> shortcuts = new HashSet<>();
        AllCHEdgesIterator iter = chGraph.getAllEdges();
        while (iter.next()) {
            if (iter.isShortcut()) {
                BooleanEncodedValue accessEnc = encoder.getAccessEnc();
                shortcuts.add(new Shortcut(
                        iter.getBaseNode(), iter.getAdjNode(),
                        iter.getOrigEdgeFirst(), iter.getOrigEdgeLast(), iter.getSkippedEdge1(), iter.getSkippedEdge2(), iter.getWeight(),
                        iter.get(accessEnc), iter.getReverse(accessEnc)
                ));
            }
        }
        return shortcuts;
    }

    private Set<Shortcut> setOf(Shortcut... shortcuts) {
        return new HashSet<>(Arrays.asList(shortcuts));
    }

    private void setMaxLevelOnAllNodes() {
        int nodes = chGraph.getNodes();
        for (int node = 0; node < nodes; node++) {
            chGraph.setLevel(node, nodes);
        }
    }

    private static class Shortcut {
        int baseNode;
        int adjNode;
        int firstOrigEdge;
        int lastOrigEdge;
        double weight;
        boolean fwd;
        boolean bwd;
        int skipEdge1;
        int skipEdge2;

        public Shortcut(int baseNode, int adjNode, int firstOrigEdge, int lastOrigEdge, int skipEdge1, int skipEdge2, double weight,
                        boolean fwd, boolean bwd) {
            this.baseNode = baseNode;
            this.adjNode = adjNode;
            this.firstOrigEdge = firstOrigEdge;
            this.lastOrigEdge = lastOrigEdge;
            this.weight = weight;
            this.fwd = fwd;
            this.bwd = bwd;
            this.skipEdge1 = skipEdge1;
            this.skipEdge2 = skipEdge2;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Shortcut shortcut = (Shortcut) o;
            return baseNode == shortcut.baseNode &&
                    adjNode == shortcut.adjNode &&
                    firstOrigEdge == shortcut.firstOrigEdge &&
                    lastOrigEdge == shortcut.lastOrigEdge &&
                    Double.compare(shortcut.weight, weight) == 0 &&
                    fwd == shortcut.fwd &&
                    bwd == shortcut.bwd &&
                    skipEdge1 == shortcut.skipEdge1 &&
                    skipEdge2 == shortcut.skipEdge2;
        }

        @Override
        public int hashCode() {
            return Objects.hash(baseNode, adjNode, firstOrigEdge, lastOrigEdge, weight, fwd, bwd,
                    skipEdge1, skipEdge2);
        }

        @Override
        public String toString() {
            return "Shortcut{" +
                    "baseNode=" + baseNode +
                    ", adjNode=" + adjNode +
                    ", firstOrigEdge=" + firstOrigEdge +
                    ", lastOrigEdge=" + lastOrigEdge +
                    ", weight=" + weight +
                    ", fwd=" + fwd +
                    ", bwd=" + bwd +
                    ", skipEdge1=" + skipEdge1 +
                    ", skipEdge2=" + skipEdge2 +
                    '}';
        }
    }

}
