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

import com.graphhopper.routing.ev.DecimalEncodedValue;
import com.graphhopper.routing.ev.DecimalEncodedValueImpl;
import com.graphhopper.routing.ev.TurnCost;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.routing.weighting.SpeedWeighting;
import com.graphhopper.routing.weighting.Weighting;
import com.graphhopper.storage.*;
import com.graphhopper.util.EdgeIteratorState;
import com.graphhopper.util.GHUtility;
import com.graphhopper.util.PMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * In this test we mainly test if {@link EdgeBasedNodeContractorTest} inserts the correct shortcuts when certain
 * nodes are contracted.
 *
 * @see CHTurnCostTest where node contraction is tested in combination with the routing query
 */
public class EdgeBasedNodeContractorTest {
    private final int maxCost = 10;
    private DecimalEncodedValue speedEnc;
    private DecimalEncodedValue turnCostEnc;
    private BaseGraph graph;
    private Weighting weighting;
    private CHStorage chStore;
    private CHStorageBuilder chBuilder;

    private List<CHConfig> chConfigs;

    @BeforeEach
    public void setup() {
        initialize();
    }

    private void initialize() {
        speedEnc = new DecimalEncodedValueImpl("speed", 5, 5, true);
        turnCostEnc = TurnCost.create("car", maxCost);
        EncodingManager encodingManager = EncodingManager.start().add(speedEnc).addTurnCostEncodedValue(turnCostEnc).build();
        graph = new BaseGraph.Builder(encodingManager).withTurnCosts(true).create();
        chConfigs = Arrays.asList(
                CHConfig.edgeBased("p1", new SpeedWeighting(speedEnc, turnCostEnc, graph.getTurnCostStorage(), Double.POSITIVE_INFINITY)),
                CHConfig.edgeBased("p2", new SpeedWeighting(speedEnc, turnCostEnc, graph.getTurnCostStorage(), 60)),
                CHConfig.edgeBased("p3", new SpeedWeighting(speedEnc, turnCostEnc, graph.getTurnCostStorage(), 0))
        );
    }

    private void freeze() {
        graph.freeze();
        chStore = CHStorage.fromGraph(graph, chConfigs.get(0));
        chBuilder = new CHStorageBuilder(chStore);
        weighting = RoutingCHGraphImpl.fromGraph(graph, chStore, chConfigs.get(0)).getWeighting();
    }

    @Test
    public void testContractNodes_simpleLoop() {
        //     2-3
        //     | |
        //  6- 7-8
        //     |
        //     9
        graph.edge(6, 7).setDistance(20).set(speedEnc, 10, 0);
        final EdgeIteratorState edge7to8 = graph.edge(7, 8).setDistance(20).set(speedEnc, 10, 0);
        final EdgeIteratorState edge8to3 = graph.edge(8, 3).setDistance(10).set(speedEnc, 10, 0);
        final EdgeIteratorState edge3to2 = graph.edge(3, 2).setDistance(20).set(speedEnc, 10, 0);
        final EdgeIteratorState edge2to7 = graph.edge(2, 7).setDistance(10).set(speedEnc, 10, 0);
        graph.edge(7, 9).setDistance(10).set(speedEnc, 10, 0);
        freeze();
        setMaxLevelOnAllNodes();

        setRestriction(6, 7, 9);
        setTurnCost(8, 3, 2, 2);

        contractNodes(5, 6, 3, 2, 9, 1, 8, 4, 7, 0);
        checkShortcuts(
                createShortcut(2, 8, edge8to3, edge3to2, 50, false, true),
                createShortcut(8, 7, edge8to3.getEdgeKey(), edge2to7.getEdgeKey(), 6, edge2to7.getEdge(), 60, true, false),
                createShortcut(7, 7, edge7to8.getEdgeKey(), edge2to7.getEdgeKey(), edge7to8.getEdge(), 7, 80, true, false)
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
        final EdgeIteratorState e6to0 = graph.edge(6, 0).setDistance(40).set(speedEnc, 10, 0);
        final EdgeIteratorState e0to3 = graph.edge(0, 3).setDistance(50).set(speedEnc, 10, 0);
        graph.edge(1, 6).setDistance(10).set(speedEnc, 10, 0);
        final EdgeIteratorState e6to3 = graph.edge(6, 3).setDistance(10).set(speedEnc, 10, 0);
        final EdgeIteratorState e3to5 = graph.edge(3, 5).setDistance(20).set(speedEnc, 10, 0);
        graph.edge(2, 6).setDistance(10).set(speedEnc, 10, 0);
        graph.edge(5, 4).setDistance(20).set(speedEnc, 10, 0);
        freeze();
        setMaxLevelOnAllNodes();
        setRestriction(1, 6, 3);
        contractAllNodesInOrder();
        checkShortcuts(
                // from contracting node 0: need a shortcut because of turn restriction
                createShortcut(3, 6, e6to0, e0to3, 90, false, true),
                // from contracting node 3: two shortcuts:
                // 1) in case we come from 1->6 (cant turn left)
                // 2) in case we come from 2->6 (going via node 0 would be more expensive)
                createShortcut(5, 6, e6to0.getEdgeKey(), e3to5.getEdgeKey(), 7, e3to5.getEdge(), 110, false, true),
                createShortcut(5, 6, e6to3, e3to5, 30, false, true)
        );
    }

    @Test
    public void testContractNodes_alternativeNecessary_noUTurn() {
        //    /->0-->
        //   v       \
        //  4 <-----> 2 -> 3 -> 1
        EdgeIteratorState e0to4 = graph.edge(4, 0).setDistance(30).set(speedEnc, 10, 10);
        EdgeIteratorState e0to2 = graph.edge(0, 2).setDistance(50).set(speedEnc, 10, 0);
        EdgeIteratorState e2to3 = graph.edge(2, 3).setDistance(20).set(speedEnc, 10, 0);
        EdgeIteratorState e1to3 = graph.edge(3, 1).setDistance(20).set(speedEnc, 10, 0);
        EdgeIteratorState e2to4 = graph.edge(4, 2).setDistance(20).set(speedEnc, 10, 10);
        freeze();

        setMaxLevelOnAllNodes();
        contractAllNodesInOrder();
        checkShortcuts(
                // from contraction of node 0
                createShortcut(2, 4, e0to4, e0to2, 80, false, true),
                // from contraction of node 2
                // It might look like it is always better to go directly from 4 to 2, but when we come from edge (2->4)
                // we may not do a u-turn at 4.
                createShortcut(3, 4, e0to4.getEdgeKey(), e2to3.getEdgeKey(), 5, e2to3.getEdge(), 100, false, true),
                createShortcut(3, 4, e2to4, e2to3, 40, false, true)
        );
    }

    @Test
    public void testContractNodes_bidirectionalLoop() {
        //  1   3
        //  |  /|
        //  0-4-6
        //    |
        //    5-2
        graph.edge(1, 0).setDistance(10).set(speedEnc, 10, 0);
        graph.edge(0, 4).setDistance(20).set(speedEnc, 10, 0);
        final EdgeIteratorState e4to6 = graph.edge(4, 6).setDistance(20).set(speedEnc, 10, 10);
        final EdgeIteratorState e6to3 = graph.edge(6, 3).setDistance(10).set(speedEnc, 10, 10);
        final EdgeIteratorState e3to4 = graph.edge(3, 4).setDistance(10).set(speedEnc, 10, 10);
        final EdgeIteratorState e4to5 = graph.edge(4, 5).setDistance(10).set(speedEnc, 10, 0);
        graph.edge(5, 2).setDistance(20).set(speedEnc, 10, 0);
        freeze();

        // enforce loop (going counter-clockwise)
        setRestriction(0, 4, 5);
        setTurnCost(6, 3, 4, 2);
        setTurnCost(4, 3, 6, 4);
        setMaxLevelOnAllNodes();

        contractAllNodesInOrder();
        checkShortcuts(
                // from contraction of node 3
                createShortcut(4, 6, e3to4.detach(true), e6to3.detach(true), 60, true, false),
                createShortcut(4, 6, e6to3, e3to4, 40, false, true),
                // from contraction of node 4
                // two 'parallel' shortcuts to preserve shortest paths to 5 when coming from 4->6 and 3->6 !!
                createShortcut(5, 6, e6to3.getEdgeKey(), e4to5.getEdgeKey(), 8, e4to5.getEdge(), 50, false, true),
                createShortcut(5, 6, e4to6.detach(true), e4to5, 30, false, true)
        );
    }

    @Test
    public void testContractNode_twoNormalEdges_noSourceEdgeToConnect() {
        // 1 --> 0 --> 2 --> 3
        graph.edge(1, 0).setDistance(30).set(speedEnc, 10, 0);
        graph.edge(0, 2).setDistance(50).set(speedEnc, 10, 0);
        graph.edge(2, 3).setDistance(10).set(speedEnc, 10, 0);
        freeze();
        setMaxLevelOnAllNodes();
        contractNodes(0, 3, 1, 2);
        // it looks like we need a shortcut from 1 to 2, but shortcuts are only introduced to maintain shortest paths
        // between original edges, so there should be no shortcuts here, because there is no original edge incoming
        // to node 1.
        checkShortcuts();
    }

    @Test
    public void testContractNode_twoNormalEdges_noTargetEdgeToConnect() {
        // 3 --> 1 --> 0 --> 2
        graph.edge(3, 1).setDistance(10).set(speedEnc, 10, 0);
        graph.edge(1, 0).setDistance(30).set(speedEnc, 10, 0);
        graph.edge(0, 2).setDistance(50).set(speedEnc, 10, 0);
        freeze();
        setMaxLevelOnAllNodes();
        contractNodes(0, 3, 1, 2);
        // it looks like we need a shortcut from 1 to 2, but shortcuts are only introduced to maintain shortest paths
        // between original edges, so there should be no shortcuts here, because there is no original edge outgoing
        // from node 2.
        checkShortcuts();
    }

    @Test
    public void testContractNode_twoNormalEdges_noEdgesToConnectBecauseOfTurnRestrictions() {
        // 0 --> 3 --> 2 --> 4 --> 1
        graph.edge(0, 3).setDistance(10).set(speedEnc, 10, 0);
        graph.edge(3, 2).setDistance(30).set(speedEnc, 10, 0);
        graph.edge(2, 4).setDistance(50).set(speedEnc, 10, 0);
        graph.edge(4, 1).setDistance(10).set(speedEnc, 10, 0);
        setRestriction(0, 3, 2);
        setRestriction(2, 4, 1);
        freeze();
        setMaxLevelOnAllNodes();
        contractNodes(2, 0, 3, 4, 1);
        // It looks like we need a shortcut from 3 to 4, but due to the turn restrictions there should be none.
        checkShortcuts();
    }

    @Test
    public void testContractNode_twoNormalEdges_noTurncosts() {
        // 0 --> 3 --> 2 --> 4 --> 1
        graph.edge(0, 3).setDistance(10).set(speedEnc, 10, 0);
        final EdgeIteratorState e3to2 = graph.edge(3, 2).setDistance(30).set(speedEnc, 10, 0);
        final EdgeIteratorState e2to4 = graph.edge(2, 4).setDistance(50).set(speedEnc, 10, 0);
        graph.edge(4, 1).setDistance(10).set(speedEnc, 10, 0);
        freeze();
        setMaxLevelOnAllNodes();
        EdgeBasedNodeContractor nodeContractor = createNodeContractor();
        contractNode(nodeContractor, 0, 0);
        contractNode(nodeContractor, 1, 1);
        // no shortcuts so far
        checkShortcuts();
        // contracting node 2 should yield a shortcut to preserve the shortest path from (1->2) to (3->4). note that
        // it does not matter that nodes 0 and 1 have lower level and are contracted already!
        contractNode(nodeContractor, 2, 2);
        contractNode(nodeContractor, 3, 3);
        contractNode(nodeContractor, 4, 4);
        nodeContractor.finishContraction();
        checkShortcuts(createShortcut(3, 4, e3to2, e2to4, 80));
    }

    @Test
    public void testContractNode_twoNormalEdges_noShortcuts() {
        // 0 --> 1 --> 2 --> 3 --> 4
        graph.edge(0, 1).setDistance(10).set(speedEnc, 10, 0);
        graph.edge(1, 2).setDistance(30).set(speedEnc, 10, 0);
        graph.edge(2, 3).setDistance(50).set(speedEnc, 10, 0);
        graph.edge(3, 4).setDistance(10).set(speedEnc, 10, 0);
        freeze();
        setMaxLevelOnAllNodes();
        contractAllNodesInOrder();
        // for each contraction the node levels are such that no shortcuts are introduced
        checkShortcuts();
    }

    @Test
    public void testContractNode_twoNormalEdges_noOutgoingEdges() {
        // 0 --> 1 --> 2 <-- 3 <-- 4
        graph.edge(0, 1).setDistance(10).set(speedEnc, 10, 0);
        graph.edge(1, 2).setDistance(30).set(speedEnc, 10, 0);
        graph.edge(3, 2).setDistance(50).set(speedEnc, 10, 0);
        graph.edge(4, 3).setDistance(10).set(speedEnc, 10, 0);
        freeze();
        setMaxLevelOnAllNodes();
        contractNodes(2, 0, 4, 1, 3);
        checkShortcuts();
    }

    @Test
    public void testContractNode_twoNormalEdges_noIncomingEdges() {
        // 0 <-- 1 <-- 2 --> 3 --> 4
        graph.edge(1, 0).setDistance(10).set(speedEnc, 10, 0);
        graph.edge(2, 1).setDistance(30).set(speedEnc, 10, 0);
        graph.edge(2, 3).setDistance(50).set(speedEnc, 10, 0);
        graph.edge(3, 4).setDistance(10).set(speedEnc, 10, 0);
        freeze();
        setMaxLevelOnAllNodes();
        contractNodes(2, 0, 4, 1, 3);
        checkShortcuts();
    }

    @Test
    public void testContractNode_duplicateOutgoingEdges_differentWeight() {
        // duplicate edges with different weight occur frequently, because there might be different ways between
        // the tower nodes

        // 0 -> 1 -> 2 -> 3 -> 4
        //            \->/
        graph.edge(0, 1).setDistance(10).set(speedEnc, 10, 0);
        graph.edge(1, 2).setDistance(10).set(speedEnc, 10, 0);
        graph.edge(2, 3).setDistance(20).set(speedEnc, 10, 0);
        graph.edge(2, 3).setDistance(10).set(speedEnc, 10, 0);
        graph.edge(3, 4).setDistance(10).set(speedEnc, 10, 0);
        freeze();
        setMaxLevelOnAllNodes();
        contractNodes(2, 0, 4, 1, 3);
        // there should be only one shortcut
        checkShortcuts(
                createShortcut(1, 3, 2, 6, 1, 3, 20)
        );
    }

    @Test
    public void testContractNode_duplicateIncomingEdges_differentWeight() {
        // 0 -> 1 -> 2 -> 3 -> 4
        //       \->/
        graph.edge(0, 1).setDistance(10).set(speedEnc, 10, 0);
        graph.edge(1, 2).setDistance(20).set(speedEnc, 10, 0);
        graph.edge(1, 2).setDistance(10).set(speedEnc, 10, 0);
        graph.edge(2, 3).setDistance(10).set(speedEnc, 10, 0);
        graph.edge(3, 4).setDistance(10).set(speedEnc, 10, 0);
        freeze();
        setMaxLevelOnAllNodes();
        contractNodes(2, 0, 4, 1, 3);
        checkShortcuts(
                createShortcut(1, 3, 4, 6, 2, 3, 20)
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
        graph.edge(0, 1).setDistance(10).set(speedEnc, 10, 0);
        graph.edge(1, 2).setDistance(10).set(speedEnc, 10, 0);
        graph.edge(2, 3).setDistance(10).set(speedEnc, 10, 0);
        graph.edge(2, 3).setDistance(10).set(speedEnc, 10, 0);
        graph.edge(3, 4).setDistance(10).set(speedEnc, 10, 0);
        freeze();
        setMaxLevelOnAllNodes();
        contractNodes(2, 0, 4, 1, 3);
        checkNumShortcuts(1);
    }

    @RepeatedTest(10)
    public void testContractNode_duplicateIncomingEdges_sameWeight() {
        // 0 -> 1 -> 2 -> 3 -> 4
        //       \->/
        graph.edge(0, 1).setDistance(10).set(speedEnc, 10, 0);
        graph.edge(1, 2).setDistance(10).set(speedEnc, 10, 0);
        graph.edge(1, 2).setDistance(10).set(speedEnc, 10, 0);
        graph.edge(2, 3).setDistance(10).set(speedEnc, 10, 0);
        graph.edge(3, 4).setDistance(10).set(speedEnc, 10, 0);
        freeze();
        setMaxLevelOnAllNodes();
        contractNodes(2, 0, 4, 1, 3);
        checkNumShortcuts(1);
    }

    @Test
    public void testContractNode_twoNormalEdges_withTurnCost() {
        // 0 --> 3 --> 2 --> 4 --> 1
        graph.edge(0, 3).setDistance(10).set(speedEnc, 10, 0);
        final EdgeIteratorState e3to2 = graph.edge(3, 2).setDistance(30).set(speedEnc, 10, 0);
        final EdgeIteratorState e2to4 = graph.edge(2, 4).setDistance(50).set(speedEnc, 10, 0);
        graph.edge(4, 1).setDistance(10).set(speedEnc, 10, 0);
        setTurnCost(3, 2, 4, 4);
        freeze();
        setMaxLevelOnAllNodes();
        contractNodes(2, 0, 1, 3, 4);
        checkShortcuts(createShortcut(3, 4, e3to2, e2to4, 120));
    }

    @Test
    public void testContractNode_twoNormalEdges_withTurnRestriction() {
        // 0 --> 3 --> 2 --> 4 --> 1
        graph.edge(0, 3).setDistance(10).set(speedEnc, 10, 0);
        graph.edge(3, 2).setDistance(30).set(speedEnc, 10, 0);
        graph.edge(2, 4).setDistance(50).set(speedEnc, 10, 0);
        graph.edge(4, 1).setDistance(10).set(speedEnc, 10, 0);
        setRestriction(3, 2, 4);
        freeze();
        setMaxLevelOnAllNodes();
        contractNodes(2, 0, 1, 3, 4);
        checkShortcuts();
    }

    @Test
    public void testContractNode_twoNormalEdges_bidirectional() {
        // 0 -- 3 -- 2 -- 4 -- 1
        graph.edge(0, 3).setDistance(10).set(speedEnc, 10, 10);
        final EdgeIteratorState e3to2 = graph.edge(3, 2).setDistance(30).set(speedEnc, 10, 10);
        final EdgeIteratorState e2to4 = graph.edge(2, 4).setDistance(50).set(speedEnc, 10, 10);
        graph.edge(4, 1).setDistance(10).set(speedEnc, 10, 10);
        setTurnCost(e3to2, e2to4, 2, 4);
        setTurnCost(e2to4, e3to2, 2, 4);
        freeze();
        setMaxLevelOnAllNodes();
        contractNodes(2, 0, 1, 3, 4);
        checkShortcuts(
                // note that for now we add a shortcut for each direction. using fwd/bwd flags would be more efficient,
                // but requires a more sophisticated way to determine the 'first' and 'last' original edges at various
                // places
                createShortcut(3, 4, 2, 4, 1, 2, 120, true, false),
                createShortcut(3, 4, 5, 3, 2, 1, 120, false, true)
        );
    }

    @Test
    public void testContractNode_twoNormalEdges_bidirectional_differentCosts() {
        // 0 -- 3 -- 2 -- 4 -- 1
        graph.edge(0, 3).setDistance(10).set(speedEnc, 10, 10);
        final EdgeIteratorState e3to2 = graph.edge(3, 2).setDistance(30).set(speedEnc, 10, 10);
        final EdgeIteratorState e2to4 = graph.edge(2, 4).setDistance(50).set(speedEnc, 10, 10);
        graph.edge(4, 1).setDistance(10).set(speedEnc, 10, 10);
        setTurnCost(e3to2, e2to4, 2, 4);
        setTurnCost(e2to4, e3to2, 2, 7);
        freeze();
        setMaxLevelOnAllNodes();
        contractNodes(2, 0, 1, 3, 4);
        checkShortcuts(
                createShortcut(3, 4, e3to2, e2to4, 120, true, false),
                createShortcut(3, 4, e2to4.detach(true), e3to2.detach(true), 150, false, true)
        );
    }

    @Test
    public void testContractNode_multiple_bidirectional_linear() {
        // 3 -- 2 -- 1 -- 4
        graph.edge(3, 2).setDistance(20).set(speedEnc, 10, 10);
        graph.edge(2, 1).setDistance(30).set(speedEnc, 10, 10);
        graph.edge(1, 4).setDistance(60).set(speedEnc, 10, 10);
        freeze();
        setMaxLevelOnAllNodes();

        contractNodes(1, 2, 3, 4);
        // no shortcuts needed
        checkShortcuts();
    }

    @Test
    public void testContractNode_shortcutDoesNotSpanUTurn() {
        // 2 -> 7 -> 3 -> 5 -> 6
        //           |
        //     1 <-> 4
        final EdgeIteratorState e7to3 = graph.edge(7, 3).setDistance(10).set(speedEnc, 10, 0);
        final EdgeIteratorState e3to5 = graph.edge(3, 5).setDistance(10).set(speedEnc, 10, 0);
        final EdgeIteratorState e3to4 = graph.edge(3, 4).setDistance(20).set(speedEnc, 10, 10);
        graph.edge(2, 7).setDistance(10).set(speedEnc, 10, 0);
        graph.edge(5, 6).setDistance(10).set(speedEnc, 10, 0);
        graph.edge(1, 4).setDistance(10).set(speedEnc, 10, 10);
        freeze();
        setMaxLevelOnAllNodes();
        setRestriction(7, 3, 5);
        contractNodes(3, 4, 2, 6, 7, 5, 1);
        checkShortcuts(
                // from contracting node 3
                createShortcut(4, 7, e7to3, e3to4, 30, false, true),
                createShortcut(4, 5, e3to4.detach(true), e3to5, 30, true, false)
                // important! no shortcut from 7 to 5 when contracting node 4, because it includes a u-turn
        );
    }

    @Test
    public void testContractNode_multiple_loops_directTurnIsBest() {
        // turning on any of the loops is restricted so we take the direct turn -> one extra shortcuts
        GraphWithTwoLoops g = new GraphWithTwoLoops(maxCost, maxCost, 1, 2, 3, 4);
        g.contractAndCheckShortcuts(
                createShortcut(7, 8, g.e7to6, g.e6to8, 110, true, false));
    }

    @Test
    public void testContractNode_multiple_loops_leftLoopIsBest() {
        // direct turn is restricted, so we take the left loop -> two extra shortcuts
        GraphWithTwoLoops g = new GraphWithTwoLoops(2, maxCost, 1, 2, 3, maxCost);
        g.contractAndCheckShortcuts(
                createShortcut(6, 7, g.e7to6.getEdgeKey(), g.e1to6.getEdgeKey(), g.e7to6.getEdge(), g.getScEdge(3), 120, false, true),
                createShortcut(7, 8, g.e7to6.getEdgeKey(), g.e6to8.getEdgeKey(), g.getScEdge(4), g.e6to8.getEdge(), 200, true, false)
        );
    }

    @Test
    public void testContractNode_multiple_loops_rightLoopIsBest() {
        // direct turn is restricted, going on left loop is expensive, so we take the right loop -> two extra shortcuts
        GraphWithTwoLoops g = new GraphWithTwoLoops(8, 1, 1, 2, 3, maxCost);
        g.contractAndCheckShortcuts(
                createShortcut(6, 7, g.e7to6.getEdgeKey(), g.e3to6.getEdgeKey(), g.e7to6.getEdge(), g.getScEdge(2), 120, false, true),
                createShortcut(7, 8, g.e7to6.getEdgeKey(), g.e6to8.getEdgeKey(), g.getScEdge(4), g.e6to8.getEdge(), 210, true, false)
        );
    }

    @Test
    public void testContractNode_multiple_loops_leftRightLoopIsBest() {
        // multiple turns are restricted, it is best to take the left and the right loop -> three extra shortcuts
        GraphWithTwoLoops g = new GraphWithTwoLoops(3, maxCost, 1, maxCost, 3, maxCost);
        g.contractAndCheckShortcuts(
                createShortcut(6, 7, g.e7to6.getEdgeKey(), g.e1to6.getEdgeKey(), g.e7to6.getEdge(), g.getScEdge(3), 130, false, true),
                createShortcut(6, 7, g.e7to6.getEdgeKey(), g.e3to6.getEdgeKey(), g.getScEdge(5), g.getScEdge(2), 240, false, true),
                createShortcut(7, 8, g.e7to6.getEdgeKey(), g.e6to8.getEdgeKey(), g.getScEdge(4), g.e6to8.getEdge(), 330, true, false)
        );
    }

    @Test
    public void testContractNode_multiple_loops_rightLeftLoopIsBest() {
        // multiple turns are restricted, it is best to take the right and the left loop -> three extra shortcuts
        GraphWithTwoLoops g = new GraphWithTwoLoops(maxCost, 5, 4, 2, maxCost, maxCost);
        g.contractAndCheckShortcuts(
                createShortcut(6, 7, g.e7to6.getEdgeKey(), g.e3to6.getEdgeKey(), g.e7to6.getEdge(), g.getScEdge(2), 160, false, true),
                createShortcut(6, 7, g.e7to6.getEdgeKey(), g.e1to6.getEdgeKey(), g.getScEdge(5), g.getScEdge(3), 250, false, true),
                createShortcut(7, 8, g.e7to6.getEdgeKey(), g.e6to8.getEdgeKey(), g.getScEdge(4), g.e6to8.getEdge(), 330, true, false)
        );
    }

    //    1 4 2
    //    |\|/|
    //    0-6-3
    //     /|\
    // 9--7 5 8--10
    private class GraphWithTwoLoops {
        final int centerNode = 6;
        final EdgeIteratorState e0to1 = graph.edge(0, 1).setDistance(30).set(speedEnc, 10, 0);
        final EdgeIteratorState e1to6 = graph.edge(1, 6).setDistance(20).set(speedEnc, 10, 0);
        final EdgeIteratorState e6to0 = graph.edge(6, 0).setDistance(40).set(speedEnc, 10, 0);
        final EdgeIteratorState e2to3 = graph.edge(2, 3).setDistance(20).set(speedEnc, 10, 0);
        final EdgeIteratorState e3to6 = graph.edge(3, 6).setDistance(70).set(speedEnc, 10, 0);
        final EdgeIteratorState e6to2 = graph.edge(6, 2).setDistance(10).set(speedEnc, 10, 0);
        final EdgeIteratorState e7to6 = graph.edge(7, 6).setDistance(10).set(speedEnc, 10, 0);
        final EdgeIteratorState e6to8 = graph.edge(6, 8).setDistance(60).set(speedEnc, 10, 0);
        final EdgeIteratorState e9to7 = graph.edge(9, 7).setDistance(20).set(speedEnc, 10, 0);
        final EdgeIteratorState e8to10 = graph.edge(8, 10).setDistance(30).set(speedEnc, 10, 0);
        // these two edges help to avoid loop avoidance for the left and right loops
        final EdgeIteratorState e4to6 = graph.edge(4, 6).setDistance(10).set(speedEnc, 10, 0);
        final EdgeIteratorState e5to6 = graph.edge(5, 6).setDistance(10).set(speedEnc, 10, 0);
        final int numEdges = 12;

        GraphWithTwoLoops(int turnCost70, int turnCost72, int turnCost12, int turnCost18, int turnCost38, int turnCost78) {
            setTurnCost(e7to6, e6to0, centerNode, turnCost70);
            setTurnCost(e7to6, e6to2, centerNode, turnCost72);
            setTurnCost(e7to6, e6to8, centerNode, turnCost78);
            setTurnCost(e1to6, e6to2, centerNode, turnCost12);
            setTurnCost(e1to6, e6to8, centerNode, turnCost18);
            setTurnCost(e3to6, e6to8, centerNode, turnCost38);
            // restrictions to make sure that no loop avoidance takes place when the left&right loops are contracted
            setRestriction(e4to6, e6to8, centerNode);
            setRestriction(e5to6, e6to2, centerNode);
            setRestriction(e4to6, e6to0, centerNode);

            freeze();
            setMaxLevelOnAllNodes();
        }

        private void contractAndCheckShortcuts(Shortcut... shortcuts) {
            contractNodes(0, 1, 2, 3, 4, 5, 6, 9, 10, 7, 8);
            HashSet<Shortcut> expectedShortcuts = new HashSet<>();
            expectedShortcuts.addAll(Arrays.asList(
                    createShortcut(1, 6, e6to0, e0to1, 70, false, true),
                    createShortcut(6, 6, e6to0.getEdgeKey(), e1to6.getEdgeKey(), getScEdge(0), e1to6.getEdge(), 90, true, false),
                    createShortcut(3, 6, e6to2, e2to3, 30, false, true),
                    createShortcut(6, 6, e6to2.getEdgeKey(), e3to6.getEdgeKey(), getScEdge(1), e3to6.getEdge(), 100, true, false)
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
        contractNodes(0, 4, 3, 1, 2);
        checkShortcuts(
                createShortcut(1, 2, g.e1to0, g.e0to2, 70)
        );
    }

    @Test
    public void testContractNode_detour_detourIsWorse() {
        // starting the detour is cheap but going left at node 2 is expensive -> no shortcut
        GraphWithDetour g = new GraphWithDetour(4, 1, 1, 7);
        contractNodes(0, 4, 3, 1, 2);
        checkShortcuts();
    }

    //      0
    //     / \
    // 4--1---2--3
    private class GraphWithDetour {
        private final EdgeIteratorState e4to1 = graph.edge(4, 1).setDistance(20).set(speedEnc, 10, 0);
        private final EdgeIteratorState e1to0 = graph.edge(1, 0).setDistance(40).set(speedEnc, 10, 0);
        private final EdgeIteratorState e1to2 = graph.edge(1, 2).setDistance(30).set(speedEnc, 10, 0);
        private final EdgeIteratorState e0to2 = graph.edge(0, 2).setDistance(30).set(speedEnc, 10, 0);
        private final EdgeIteratorState e2to3 = graph.edge(2, 3).setDistance(20).set(speedEnc, 10, 0);

        GraphWithDetour(int turnCost42, int turnCost13, int turnCost40, int turnCost03) {
            setTurnCost(e4to1, e1to2, 1, turnCost42);
            setTurnCost(e4to1, e1to0, 1, turnCost40);
            setTurnCost(e1to2, e2to3, 2, turnCost13);
            setTurnCost(e0to2, e2to3, 2, turnCost03);
            freeze();
            setMaxLevelOnAllNodes();
        }

    }

    @Test
    public void testContractNode_detour_multipleInOut_needsShortcut() {
        GraphWithDetourMultipleInOutEdges g = new GraphWithDetourMultipleInOutEdges(0, 0, 0, 1, 3);
        contractNodes(0, 2, 5, 6, 7, 1, 3, 4);
        checkShortcuts(createShortcut(1, 4, g.e1to0, g.e0to4, 70));
    }

    @Test
    public void testContractNode_detour_multipleInOut_noShortcuts() {
        GraphWithDetourMultipleInOutEdges g = new GraphWithDetourMultipleInOutEdges(0, 0, 0, 0, 0);
        contractNodes(0, 2, 5, 6, 7, 1, 3, 4);
        checkShortcuts();
    }

    @Test
    public void testContractNode_detour_multipleInOut_restrictedIn() {
        GraphWithDetourMultipleInOutEdges g = new GraphWithDetourMultipleInOutEdges(0, maxCost, 0, maxCost, 0);
        contractNodes(0, 2, 5, 6, 7, 1, 3, 4);
        checkShortcuts();
    }

    // 5   3   7
    //  \ / \ /
    // 2-1-0-4-6
    private class GraphWithDetourMultipleInOutEdges {
        final EdgeIteratorState e5to1 = graph.edge(5, 1).setDistance(30).set(speedEnc, 10, 0);
        final EdgeIteratorState e2to1 = graph.edge(2, 1).setDistance(20).set(speedEnc, 10, 0);
        final EdgeIteratorState e1to3 = graph.edge(1, 3).setDistance(10).set(speedEnc, 10, 0);
        final EdgeIteratorState e3to4 = graph.edge(3, 4).setDistance(20).set(speedEnc, 10, 0);
        final EdgeIteratorState e1to0 = graph.edge(1, 0).setDistance(50).set(speedEnc, 10, 0);
        final EdgeIteratorState e0to4 = graph.edge(0, 4).setDistance(20).set(speedEnc, 10, 0);
        final EdgeIteratorState e4to6 = graph.edge(4, 6).setDistance(10).set(speedEnc, 10, 0);
        final EdgeIteratorState e4to7 = graph.edge(4, 7).setDistance(30).set(speedEnc, 10, 0);

        GraphWithDetourMultipleInOutEdges(int turnCost20, int turnCost50, int turnCost23, int turnCost53, int turnCost36) {
            setTurnCost(e1to3, e3to4, 3, 2);
            setTurnCost(e2to1, e1to0, 1, turnCost20);
            setTurnCost(e2to1, e1to3, 1, turnCost23);
            setTurnCost(e5to1, e1to0, 1, turnCost50);
            setTurnCost(e5to1, e1to3, 1, turnCost53);
            setTurnCost(e3to4, e4to6, 4, turnCost36);
            freeze();
            setMaxLevelOnAllNodes();
        }
    }

    @Test
    public void testContractNode_loopAvoidance_loopNecessary() {
        // turning from 3 via 2 to 4 is costly, it is better to take the 2-1-0-2 loop so a loop shortcut is required
        GraphWithLoop g = new GraphWithLoop(7);
        contractNodes(0, 1, 3, 4, 5, 2);
        final int numEdges = 6;
        checkShortcuts(
                createShortcut(1, 2, g.e2to0, g.e0to1, 30, false, true),
                createShortcut(2, 2, g.e2to0.getEdgeKey(), g.e1to2.getEdgeKey(), numEdges, g.e1to2.getEdge(), 40, true, false)
        );
    }

    @Test
    public void testContractNode_loopAvoidance_loopAvoidable() {
        // turning from 3 via 2 to 4 is cheap, it is better to go straight 3-2-4, no loop shortcut necessary
        GraphWithLoop g = new GraphWithLoop(3);
        contractNodes(0, 1, 3, 4, 5, 2);
        checkShortcuts(
                createShortcut(1, 2, g.e2to0, g.e0to1, 30, false, true)
        );
    }

    //   0 - 1
    //    \ /
    // 3 - 2 - 4
    //     |
    //     5
    private class GraphWithLoop {
        final EdgeIteratorState e0to1 = graph.edge(0, 1).setDistance(20).set(speedEnc, 10, 0);
        final EdgeIteratorState e1to2 = graph.edge(1, 2).setDistance(10).set(speedEnc, 10, 0);
        final EdgeIteratorState e2to0 = graph.edge(2, 0).setDistance(10).set(speedEnc, 10, 0);
        final EdgeIteratorState e3to2 = graph.edge(3, 2).setDistance(30).set(speedEnc, 10, 0);
        final EdgeIteratorState e2to4 = graph.edge(2, 4).setDistance(50).set(speedEnc, 10, 0);
        final EdgeIteratorState e5to2 = graph.edge(5, 2).setDistance(20).set(speedEnc, 10, 0);

        GraphWithLoop(int turnCost34) {
            setTurnCost(e3to2, e2to4, 2, turnCost34);
            freeze();
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
        graph.edge(0, 1).setDistance(10).set(speedEnc, 10, 0);
        graph.edge(1, 2).setDistance(10).set(speedEnc, 10, 0);
        graph.edge(2, 3).setDistance(50).set(speedEnc, 10, 0);
        graph.edge(3, 4).setDistance(10).set(speedEnc, 10, 0);
        graph.edge(1, 5).setDistance(10).set(speedEnc, 10, 0);
        graph.edge(5, 9).setDistance(10).set(speedEnc, 10, 0);
        graph.edge(9, 3).setDistance(10).set(speedEnc, 10, 0);
        graph.edge(2, 7).setDistance(60).set(speedEnc, 10, 0);
        graph.edge(9, 7).setDistance(10).set(speedEnc, 10, 0);
        graph.edge(7, 10).setDistance(10).set(speedEnc, 10, 0);
        freeze();
        setMaxLevelOnAllNodes();
        contractNodes(2, 0, 10, 4, 1, 5, 7, 9, 3);
        checkShortcuts();
    }

    @RepeatedTest(10)
    public void testContractNode_noUnnecessaryShortcut_witnessPathOfEqualWeight() {
        // this test runs repeatedly because it might pass/fail by chance (because path lengths are equal)

        // 0 -> 1 -> 5 <_
        //      v    v   \
        //      2 -> 3 -> 4
        graph.edge(0, 1).setDistance(10).set(speedEnc, 10, 0);
        graph.edge(1, 2).setDistance(10).set(speedEnc, 10, 0);
        graph.edge(1, 5).setDistance(10).set(speedEnc, 10, 0);
        EdgeIteratorState e2to3 = graph.edge(2, 3).setDistance(10).set(speedEnc, 10, 0);
        EdgeIteratorState e3to4 = graph.edge(3, 4).setDistance(10).set(speedEnc, 10, 0);
        graph.edge(4, 5).setDistance(10).set(speedEnc, 10, 0);
        EdgeIteratorState e5to3 = graph.edge(5, 3).setDistance(10).set(speedEnc, 10, 0);
        freeze();
        setMaxLevelOnAllNodes();
        contractNodes(3, 2, 0, 1, 5, 4);
        // when contracting node 2 there is a witness (1-5-3-4) and no shortcut from 1 to 4 should be introduced.
        // what might be tricky here is that both the original path and the witness path have equal weight!
        // so we have to make sure that the equal weight witness is not rejected to update the currently best
        // path, or (depending on the implementation-specific edge traversal order) the original path does *not*
        // update/overwrite the already found witness path.
        checkShortcuts(
                createShortcut(2, 4, e2to3, e3to4, 20),
                createShortcut(5, 4, e5to3, e3to4, 20)
        );
    }

    @Test
    public void testContractNode_noUnnecessaryShortcut_differentWitnessesForDifferentOutEdges() {
        //         /--> 2 ---\
        //        /           \
        // 0 --> 1 ---> 3 ---> 5 --> 6 
        //        \           /
        //         \--> 4 ---/   
        graph.edge(0, 1).setDistance(10).set(speedEnc, 10, 0);
        graph.edge(1, 2).setDistance(10).set(speedEnc, 10, 0);
        graph.edge(1, 3).setDistance(10).set(speedEnc, 10, 0);
        graph.edge(1, 4).setDistance(10).set(speedEnc, 10, 0);
        // bidirectional
        graph.edge(2, 5).setDistance(10).set(speedEnc, 10, 10);
        graph.edge(3, 5).setDistance(10).set(speedEnc, 10, 0);
        // bidirectional
        graph.edge(4, 5).setDistance(10).set(speedEnc, 10, 10);
        graph.edge(5, 6).setDistance(10).set(speedEnc, 10, 0);
        freeze();
        setMaxLevelOnAllNodes();
        contractNodes(3, 0, 6, 1, 2, 5, 4);

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
        graph.edge(0, 1).setDistance(10).set(speedEnc, 10, 0);
        // bidirectional
        graph.edge(1, 2).setDistance(10).set(speedEnc, 10, 10);
        graph.edge(1, 3).setDistance(10).set(speedEnc, 10, 0);
        // bidirectional
        graph.edge(1, 4).setDistance(10).set(speedEnc, 10, 10);
        graph.edge(2, 5).setDistance(10).set(speedEnc, 10, 0);
        graph.edge(3, 5).setDistance(10).set(speedEnc, 10, 0);
        graph.edge(4, 5).setDistance(10).set(speedEnc, 10, 0);
        graph.edge(5, 6).setDistance(10).set(speedEnc, 10, 0);
        freeze();
        setMaxLevelOnAllNodes();
        contractNodes(3, 0, 6, 1, 2, 5, 4);

        // We do not need a shortcut here! node 1 can be reached from nodes 0, 2 and 4 and from the target node 5 we can
        // only reach node 6. so coming into node 1 from node 0 we can either go north or south via nodes 2/4 to reach
        // the edge 5->6. If we come from node 2 we can take the southern witness via 4 and vice versa.
        // 
        // This is an example of an unnecessary shortcut introduced by the turn replacement algorithm, because the 
        // out turn replacement difference for the potential witnesses would be infinite at node 1. 
        // Note that this happens basically whenever there is a bidirectional edge (and u-turns are forbidden) !
        checkShortcuts();
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    public void testContractNode_bidirectional_edge_at_fromNode(boolean edge1to2bidirectional) {
        // 0 -> 1 <-> 5
        //      v     v
        //      2 --> 3 -> 4
        graph.edge(0, 1).setDistance(10).set(speedEnc, 10, 0);
        graph.edge(1, 2).setDistance(10).set(speedEnc, 10, edge1to2bidirectional ? 10 : 0);
        graph.edge(2, 3).setDistance(10).set(speedEnc, 10, 0);
        graph.edge(3, 4).setDistance(10).set(speedEnc, 10, 0);
        graph.edge(1, 5).setDistance(10).set(speedEnc, 10, 10);
        graph.edge(5, 3).setDistance(10).set(speedEnc, 10, 0);
        freeze();
        setMaxLevelOnAllNodes();
        contractNodes(2, 0, 1, 5, 4, 3);
        // we might come from (5->1) so we still need a way back to (3->4) -> we need a shortcut
        Shortcut expectedShortcuts = createShortcut(1, 3, 2, 4, 1, 2, 20);
        checkShortcuts(expectedShortcuts);
    }

    @Test
    public void testContractNode_bidirectional_edge_at_fromNode_going_to_node() {
        // 0 -> 1 <-> 5
        //      v     v
        //      2 --> 3 -> 4
        graph.edge(0, 1).setDistance(10).set(speedEnc, 10, 0);
        graph.edge(1, 2).setDistance(10).set(speedEnc, 10, 0);
        graph.edge(2, 3).setDistance(10).set(speedEnc, 10, 0);
        graph.edge(3, 4).setDistance(10).set(speedEnc, 10, 0);
        graph.edge(1, 5).setDistance(10).set(speedEnc, 10, 10);
        graph.edge(5, 3).setDistance(10).set(speedEnc, 10, 0);
        freeze();
        setMaxLevelOnAllNodes();
        contractNodes(5, 0, 4, 1, 2, 3);
        // wherever we come from we can always go via node 2 -> no shortcut needed
        checkShortcuts();
    }

    @Test
    public void testNodeContraction_directWitness() {
        // 0 -> 1 -> 2 -> 3 -> 4 -> 5 -> 6 -> 7 -> 8
        //     /      \                 /      \
        //10 ->        ------> 9 ------>        -> 11
        graph.edge(0, 1).setDistance(10).set(speedEnc, 10, 0);
        graph.edge(1, 2).setDistance(10).set(speedEnc, 10, 0);
        graph.edge(2, 3).setDistance(10).set(speedEnc, 10, 0);
        graph.edge(3, 4).setDistance(10).set(speedEnc, 10, 0);
        graph.edge(4, 5).setDistance(10).set(speedEnc, 10, 0);
        graph.edge(5, 6).setDistance(10).set(speedEnc, 10, 0);
        graph.edge(6, 7).setDistance(10).set(speedEnc, 10, 0);
        graph.edge(7, 8).setDistance(10).set(speedEnc, 10, 0);
        graph.edge(2, 9).setDistance(10).set(speedEnc, 10, 0);
        graph.edge(9, 6).setDistance(10).set(speedEnc, 10, 0);
        graph.edge(10, 1).setDistance(10).set(speedEnc, 10, 0);
        graph.edge(7, 11).setDistance(10).set(speedEnc, 10, 0);
        freeze();
        setMaxLevelOnAllNodes();
        contractNodes(2, 6, 3, 5, 4, 0, 8, 10, 11, 1, 7, 9);
        // note that the shortcut edge ids depend on the insertion order which might change when changing the implementation
        checkShortcuts(
                createShortcut(3, 1, 2, 4, 1, 2, 20, false, true),
                createShortcut(1, 9, 2, 16, 1, 8, 20, true, false),
                createShortcut(5, 7, 10, 12, 5, 6, 20, true, false),
                createShortcut(7, 9, 18, 12, 9, 6, 20, false, true),
                createShortcut(4, 1, 2, 6, 12, 3, 30, false, true),
                createShortcut(4, 7, 8, 12, 4, 13, 30, true, false)
        );
    }

    @Test
    public void testNodeContraction_witnessBetterBecauseOfTurnCostAtTargetNode() {
        // when we contract node 2 we should not stop searching for witnesses when edge 2->3 is settled, because then we miss
        // the witness path via 5 that is found later, but still has less weight because of the turn costs at node 3
        // 0 -> 1 -> 2 -> 3 -> 4
        //       \       /         
        //        -- 5 ->   
        graph.edge(0, 1).setDistance(10).set(speedEnc, 10, 0);
        graph.edge(1, 2).setDistance(10).set(speedEnc, 10, 0);
        graph.edge(2, 3).setDistance(10).set(speedEnc, 10, 0);
        graph.edge(3, 4).setDistance(10).set(speedEnc, 10, 0);
        graph.edge(1, 5).setDistance(30).set(speedEnc, 10, 0);
        graph.edge(5, 3).setDistance(10).set(speedEnc, 10, 0);
        setTurnCost(2, 3, 4, 5);
        setTurnCost(5, 3, 4, 2);
        freeze();
        setMaxLevelOnAllNodes();
        contractNodes(2, 0, 4, 1, 3, 5);
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
        graph.edge(0, 1).setDistance(10).set(speedEnc, 10, 0);
        graph.edge(1, 2).setDistance(10).set(speedEnc, 10, 0);
        graph.edge(2, 3).setDistance(10).set(speedEnc, 10, 0);
        graph.edge(3, 4).setDistance(10).set(speedEnc, 10, 0);
        graph.edge(1, 3).setDistance(40).set(speedEnc, 10, 0);
        graph.edge(4, 5).setDistance(10).set(speedEnc, 10, 0);

        freeze();
        setMaxLevelOnAllNodes();
        contractNodes(3, 0, 5, 1, 4, 2);
        checkShortcuts(
                createShortcut(4, 2, 4, 6, 2, 3, 20, false, true)
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
        graph.edge(0, 1).setDistance(10).set(speedEnc, 10, 0);
        graph.edge(1, 2).setDistance(10).set(speedEnc, 10, 0);
        graph.edge(2, 3).setDistance(10).set(speedEnc, 10, 0);
        graph.edge(3, 4).setDistance(10).set(speedEnc, 10, 0);
        graph.edge(4, 5).setDistance(10).set(speedEnc, 10, 0);
        graph.edge(2, 4).setDistance(40).set(speedEnc, 10, 0);

        freeze();
        setMaxLevelOnAllNodes();
        contractNodes(2, 0, 5, 1, 4, 3);
        checkShortcuts(
                createShortcut(1, 3, 2, 4, 1, 2, 20)
        );
    }

    @Test
    public void testNodeContraction_parallelEdges_onlyOneLoopShortcutNeeded() {
        //  /--\
        // 0 -- 1 -- 2
        EdgeIteratorState edge0 = graph.edge(0, 1).setDistance(20).set(speedEnc, 10, 10);
        EdgeIteratorState edge1 = graph.edge(1, 0).setDistance(40).set(speedEnc, 10, 10);
        graph.edge(1, 2).setDistance(50).set(speedEnc, 10, 10);
        setTurnCost(edge0, edge1, 0, 1);
        setTurnCost(edge1, edge0, 0, 2);
        freeze();
        setMaxLevelOnAllNodes();
        contractNodes(0, 2, 1);
        // it is sufficient to be able to travel the 1-0-1 loop in one (the cheaper) direction
        checkShortcuts(
                createShortcut(1, 1, 1, 3, 0, 1, 70)
        );
    }

    @Test
    public void testNodeContraction_duplicateEdge_severalLoops() {
        // 5 -- 4 -- 3 -- 1
        // |\   |
        // | \  /        
        // -- 2 
        graph.edge(1, 3).setDistance(470).set(speedEnc, 10, 10);
        graph.edge(2, 4).setDistance(190).set(speedEnc, 10, 10);
        EdgeIteratorState e2 = graph.edge(2, 5).setDistance(380).set(speedEnc, 10, 10);
        EdgeIteratorState e3 = graph.edge(2, 5).setDistance(570).set(speedEnc, 10, 10); // note there is a duplicate edge here (with different weight)
        graph.edge(3, 4).setDistance(100).set(speedEnc, 10, 10);
        EdgeIteratorState e5 = graph.edge(4, 5).setDistance(560).set(speedEnc, 10, 10);

        setTurnCost(e3, e2, 5, 4);
        setTurnCost(e2, e3, 5, 5);
        setTurnCost(e5, e3, 5, 3);
        setTurnCost(e3, e5, 5, 2);
        setTurnCost(e2, e5, 5, 2);
        setTurnCost(e5, e2, 5, 1);
        freeze();
        setMaxLevelOnAllNodes();
        contractNodes(4, 5, 1, 3, 2);
        // note that the shortcut edge ids depend on the insertion order which might change when changing the implementation
        checkNumShortcuts(11);
        checkShortcuts(
                // from node 4 contraction
                createShortcut(5, 3, 11, 9, 5, 4, 660, true, false),
                createShortcut(5, 3, 8, 10, 4, 5, 660, false, true),
                createShortcut(3, 2, 2, 9, 1, 4, 290, false, true),
                createShortcut(3, 2, 8, 3, 4, 1, 290, true, false),
                createShortcut(5, 2, 2, 10, 1, 5, 750, false, true),
                createShortcut(5, 2, 11, 3, 5, 1, 750, true, false),
                // from node 5 contraction
                createShortcut(2, 2, 6, 5, 3, 2, 990, true, false),
                createShortcut(2, 2, 6, 3, 3, 6, 1340, true, false),
                createShortcut(2, 2, 2, 5, 8, 2, 1140, true, false),
                createShortcut(3, 2, 4, 9, 2, 7, 1060, false, true),
                createShortcut(3, 2, 8, 5, 9, 2, 1050, true, false)
        );
    }

    @Test
    public void testNodeContraction_tripleConnection() {
        graph.edge(0, 1).setDistance(10.0).set(speedEnc, 10, 10);
        graph.edge(0, 1).setDistance(20.0).set(speedEnc, 10, 10);
        graph.edge(0, 1).setDistance(35.0).set(speedEnc, 10, 10);
        freeze();
        setMaxLevelOnAllNodes();
        contractNodes(1, 0);
        checkShortcuts(
                createShortcut(0, 0, 2, 5, 1, 2, 55),
                createShortcut(0, 0, 0, 5, 0, 2, 45),
                createShortcut(0, 0, 0, 3, 0, 1, 30)
        );
    }

    @Test
    public void testNodeContraction_fromAndToNodesEqual() {
        // 0 -> 1 -> 3
        //     / \
        //    v   ^
        //     \ /
        //      2
        graph.edge(0, 1).setDistance(10).set(speedEnc, 10, 0);
        graph.edge(1, 2).setDistance(10).set(speedEnc, 10, 0);
        graph.edge(2, 1).setDistance(10).set(speedEnc, 10, 0);
        graph.edge(1, 3).setDistance(10).set(speedEnc, 10, 0);
        freeze();
        setMaxLevelOnAllNodes();
        contractNodes(2, 0, 1, 3);
        checkShortcuts();
    }

    @Test
    public void testNodeContraction_node_in_loop() {
        //      2
        //     /|
        //  0-4-3
        //    |
        //    1
        graph.edge(0, 4).setDistance(20).set(speedEnc, 10, 0);
        graph.edge(4, 3).setDistance(20).set(speedEnc, 10, 10);
        graph.edge(3, 2).setDistance(10).set(speedEnc, 10, 10);
        graph.edge(2, 4).setDistance(10).set(speedEnc, 10, 10);
        graph.edge(4, 1).setDistance(10).set(speedEnc, 10, 0);
        freeze();
        setMaxLevelOnAllNodes();

        // enforce loop (going counter-clockwise)
        setRestriction(0, 4, 1);
        setTurnCost(4, 2, 3, 4);
        setTurnCost(3, 2, 4, 2);
        contractNodes(2, 0, 1, 4, 3);
        checkShortcuts(
                createShortcut(4, 3, 7, 5, 3, 2, 60, true, false),
                createShortcut(4, 3, 4, 6, 2, 3, 40, false, true)
        );
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
        graph.edge(0, 3).setDistance(1000).set(speedEnc, 10, 0);
        graph.edge(3, 4).setDistance(1000).set(speedEnc, 10, 10);
        graph.edge(4, 2).setDistance(5000).set(speedEnc, 10, 0);
        graph.edge(2, 3).setDistance(2000).set(speedEnc, 10, 0);
        graph.edge(3, 1).setDistance(1000).set(speedEnc, 10, 0);
        freeze();
        chStore = CHStorage.fromGraph(graph, chConfigs.get(1));
        chBuilder = new CHStorageBuilder(chStore);
        weighting = RoutingCHGraphImpl.fromGraph(graph, chStore, chConfigs.get(1)).getWeighting();
        setMaxLevelOnAllNodes();
        setRestriction(0, 3, 1);
        contractNodes(4, 0, 1, 2, 3);
        checkShortcuts(
                createShortcut(2, 3, 2, 4, 1, 2, 6000, false, true),
                createShortcut(3, 3, 2, 3, 1, 1, 2600, true, false)
        );
    }

    @Test
    public void testNodeContraction_turnRestrictionAndLoop() {
        //  /\    /<-3
        // 0  1--2
        //  \/    \->4
        graph.edge(0, 1).setDistance(50).set(speedEnc, 10, 10);
        graph.edge(0, 1).setDistance(60).set(speedEnc, 10, 10);
        graph.edge(1, 2).setDistance(20).set(speedEnc, 10, 10);
        graph.edge(3, 2).setDistance(30).set(speedEnc, 10, 0);
        graph.edge(2, 4).setDistance(30).set(speedEnc, 10, 0);
        setRestriction(3, 2, 4);
        freeze();
        setMaxLevelOnAllNodes();
        contractNodes(0, 3, 4, 2, 1);
        checkNumShortcuts(1);
    }

    @Test
    public void testNodeContraction_minorWeightDeviation() {
        // 0 -> 1 -> 2 -> 3 -> 4
        graph.edge(0, 1).setDistance(514.01).set(speedEnc, 10, 0);
        graph.edge(1, 2).setDistance(700.41).set(speedEnc, 10, 0);
        graph.edge(2, 3).setDistance(758.06).set(speedEnc, 10, 0);
        graph.edge(3, 4).setDistance(050.03).set(speedEnc, 10, 0);
        freeze();
        setMaxLevelOnAllNodes();
        contractNodes(2, 0, 1, 3, 4);
        checkShortcuts(
                createShortcut(1, 3, 2, 4, 1, 2, 1458)
        );
    }

    @Test
    public void testNodeContraction_numPolledEdges() {
        //           1<-6
        //           |
        // 0 -> 3 -> 2 <-> 4 -> 5
        //  \---<----|
        graph.edge(3, 2).setDistance(710.203000).set(speedEnc, 10, 0);
        graph.edge(0, 3).setDistance(790.003000).set(speedEnc, 10, 0);
        graph.edge(2, 0).setDistance(210.328000).set(speedEnc, 10, 0);
        graph.edge(2, 4).setDistance(160.499000).set(speedEnc, 10, 0);
        graph.edge(4, 2).setDistance(160.487000).set(speedEnc, 10, 0);
        graph.edge(6, 1).setDistance(550.603000).set(speedEnc, 10, 0);
        graph.edge(2, 1).setDistance(330.453000).set(speedEnc, 10, 0);
        graph.edge(4, 5).setDistance(290.665000).set(speedEnc, 10, 0);
        freeze();
        setMaxLevelOnAllNodes();
        EdgeBasedNodeContractor nodeContractor = createNodeContractor();
        nodeContractor.contractNode(0);
        assertTrue(nodeContractor.getNumPolledEdges() > 0, "no polled edges, something is wrong");
        assertTrue(nodeContractor.getNumPolledEdges() <= 8, "too many edges polled: " + nodeContractor.getNumPolledEdges());
    }

    @Test
    void issue_2564() {
        // 0-1-2-3-4-5
        graph.edge(0, 1).setDistance(1000).set(speedEnc, 10, 10);
        graph.edge(1, 2).setDistance(73.36).set(speedEnc, 10, 10);
        graph.edge(2, 3).setDistance(101.61).set(speedEnc, 10, 10);
        graph.edge(3, 4).setDistance(0).set(speedEnc, 10, 10);
        graph.edge(4, 5).setDistance(1000).set(speedEnc, 10, 10);
        freeze();
        chStore = CHStorage.fromGraph(graph, chConfigs.get(2));
        chBuilder = new CHStorageBuilder(chStore);
        weighting = chConfigs.get(2).getWeighting();
        setMaxLevelOnAllNodes();
        contractNodes(0, 5, 2, 1, 3, 4);
        checkShortcuts(
                createShortcut(1, 3, 2, 4, 1, 2, 175, true, false),
                createShortcut(1, 3, 5, 3, 2, 1, 175, false, true)
        );
    }

    private void contractNode(NodeContractor nodeContractor, int node, int level) {
        chBuilder.setLevel(node, level);
        nodeContractor.contractNode(node);
    }

    private void contractAllNodesInOrder() {
        EdgeBasedNodeContractor nodeContractor = createNodeContractor();
        for (int node = 0; node < graph.getNodes(); ++node) {
            chBuilder.setLevel(node, node);
            nodeContractor.contractNode(node);
        }
        nodeContractor.finishContraction();
    }

    /**
     * contracts the given nodes and sets the node levels in order.
     * this method may only be called once per test !
     */
    private void contractNodes(int... nodes) {
        EdgeBasedNodeContractor nodeContractor = createNodeContractor();
        for (int i = 0; i < nodes.length; ++i) {
            chBuilder.setLevel(nodes[i], i);
            nodeContractor.contractNode(nodes[i]);
        }
        nodeContractor.finishContraction();
    }

    private EdgeBasedNodeContractor createNodeContractor() {
        CHPreparationGraph.TurnCostFunction turnCostFunction = CHPreparationGraph.buildTurnCostFunctionFromTurnCostStorage(graph, weighting);
        CHPreparationGraph prepareGraph = CHPreparationGraph.edgeBased(graph.getNodes(), graph.getEdges(), turnCostFunction);
        CHPreparationGraph.buildFromGraph(prepareGraph, graph, weighting);
        EdgeBasedNodeContractor nodeContractor = new EdgeBasedNodeContractor(prepareGraph, chBuilder, new PMap());
        nodeContractor.initFromGraph();
        return nodeContractor;
    }

    private void setRestriction(EdgeIteratorState inEdge, EdgeIteratorState outEdge, int viaNode) {
        setTurnCost(inEdge, outEdge, viaNode, Double.POSITIVE_INFINITY);
    }

    private void setRestriction(int from, int via, int to) {
        setTurnCost(getEdge(from, via), getEdge(via, to), via, Double.POSITIVE_INFINITY);
    }

    private void setTurnCost(int from, int via, int to, double cost) {
        setTurnCost(getEdge(from, via), getEdge(via, to), via, cost);
    }

    private void setTurnCost(EdgeIteratorState inEdge, EdgeIteratorState outEdge, int viaNode, double cost) {
        double cost1 = cost >= maxCost ? Double.POSITIVE_INFINITY : cost;
        graph.getTurnCostStorage().set(turnCostEnc, inEdge.getEdge(), viaNode, outEdge.getEdge(), cost1);
    }

    private EdgeIteratorState getEdge(int from, int to) {
        return GHUtility.getEdge(graph, from, to);
    }

    private Shortcut createShortcut(int from, int to, EdgeIteratorState edge1, EdgeIteratorState edge2, double weight) {
        return createShortcut(from, to, edge1, edge2, weight, true, false);
    }

    private Shortcut createShortcut(int from, int to, EdgeIteratorState edge1, EdgeIteratorState edge2, double weight, boolean fwd, boolean bwd) {
        return createShortcut(from, to, edge1.getEdgeKey(), edge2.getEdgeKey(), edge1.getEdge(), edge2.getEdge(), weight, fwd, bwd);
    }

    private Shortcut createShortcut(int from, int to, int firstOrigEdgeKey, int lastOrigEdgeKey, int skipEdge1, int skipEdge2, double weight) {
        return createShortcut(from, to, firstOrigEdgeKey, lastOrigEdgeKey, skipEdge1, skipEdge2, weight, true, false);
    }

    private Shortcut createShortcut(int from, int to, int firstOrigEdgeKey, int lastOrigEdgeKey, int skipEdge1, int skipEdge2, double weight, boolean fwd, boolean bwd) {
        return new Shortcut(from, to, firstOrigEdgeKey, lastOrigEdgeKey, skipEdge1, skipEdge2, weight, fwd, bwd);
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
        for (int i = 0; i < chStore.getShortcuts(); i++) {
            long ptr = chStore.toShortcutPointer(i);
            shortcuts.add(new Shortcut(
                    chStore.getNodeA(ptr), chStore.getNodeB(ptr),
                    chStore.getOrigEdgeKeyFirst(ptr), chStore.getOrigEdgeKeyLast(ptr),
                    chStore.getSkippedEdge1(ptr), chStore.getSkippedEdge2(ptr),
                    chStore.getWeight(ptr),
                    chStore.getFwdAccess(ptr), chStore.getBwdAccess(ptr)
            ));
        }
        return shortcuts;
    }

    private Set<Shortcut> setOf(Shortcut... shortcuts) {
        return new HashSet<>(Arrays.asList(shortcuts));
    }

    private void setMaxLevelOnAllNodes() {
        chBuilder.setLevelForAllNodes(chStore.getNodes());
    }

    private static class Shortcut {
        int baseNode;
        int adjNode;
        int firstOrigEdgeKey;
        int lastOrigEdgeKey;
        double weight;
        boolean fwd;
        boolean bwd;
        int skipEdge1;
        int skipEdge2;

        public Shortcut(int baseNode, int adjNode, int firstOrigEdgeKey, int lastOrigEdgeKey, int skipEdge1, int skipEdge2, double weight,
                        boolean fwd, boolean bwd) {
            this.baseNode = baseNode;
            this.adjNode = adjNode;
            this.firstOrigEdgeKey = firstOrigEdgeKey;
            this.lastOrigEdgeKey = lastOrigEdgeKey;
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
                    firstOrigEdgeKey == shortcut.firstOrigEdgeKey &&
                    lastOrigEdgeKey == shortcut.lastOrigEdgeKey &&
                    Double.compare(shortcut.weight, weight) == 0 &&
                    fwd == shortcut.fwd &&
                    bwd == shortcut.bwd &&
                    skipEdge1 == shortcut.skipEdge1 &&
                    skipEdge2 == shortcut.skipEdge2;
        }

        @Override
        public int hashCode() {
            return Objects.hash(baseNode, adjNode, firstOrigEdgeKey, lastOrigEdgeKey, weight, fwd, bwd,
                    skipEdge1, skipEdge2);
        }

        @Override
        public String toString() {
            return "Shortcut{" +
                    "baseNode=" + baseNode +
                    ", adjNode=" + adjNode +
                    ", firstOrigEdgeKey=" + firstOrigEdgeKey +
                    ", lastOrigEdgeKey=" + lastOrigEdgeKey +
                    ", weight=" + weight +
                    ", fwd=" + fwd +
                    ", bwd=" + bwd +
                    ", skipEdge1=" + skipEdge1 +
                    ", skipEdge2=" + skipEdge2 +
                    '}';
        }
    }

}
