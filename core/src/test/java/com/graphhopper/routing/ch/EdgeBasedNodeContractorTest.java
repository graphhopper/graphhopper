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

import com.graphhopper.routing.ev.BooleanEncodedValue;
import com.graphhopper.routing.ev.EncodedValueLookup;
import com.graphhopper.routing.ev.TurnCost;
import com.graphhopper.routing.util.AllCHEdgesIterator;
import com.graphhopper.routing.util.CarFlagEncoder;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.routing.weighting.Weighting;
import com.graphhopper.storage.CHConfig;
import com.graphhopper.storage.CHGraph;
import com.graphhopper.storage.GraphBuilder;
import com.graphhopper.storage.GraphHopperStorage;
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
    private CHGraph chGraph;
    private CarFlagEncoder encoder;
    private GraphHopperStorage graph;
    private Weighting weighting;

    private List<CHConfig> chConfigs;

    @BeforeEach
    public void setup() {
        initialize();
    }

    private void initialize() {
        encoder = new CarFlagEncoder(5, 5, maxCost);
        EncodingManager encodingManager = EncodingManager.create(encoder);
        graph = new GraphBuilder(encodingManager)
                .setCHConfigStrings(
                        "p1|car|shortest|edge",
                        "p2|car|shortest|edge|60"
                )
                .create();
        chConfigs = graph.getCHConfigs();
        chGraph = graph.getCHGraph(chConfigs.get(0).getName());
        weighting = chGraph.getCHConfig().getWeighting();
    }

    @Test
    public void testContractNodes_simpleLoop() {
        //     2-3
        //     | |
        //  6- 7-8
        //     |
        //     9
        GHUtility.setSpeed(60, true, false, encoder, graph.edge(6, 7).setDistance(2));
        final EdgeIteratorState edge7to8 = GHUtility.setSpeed(60, true, false, encoder, graph.edge(7, 8).setDistance(2));
        final EdgeIteratorState edge8to3 = GHUtility.setSpeed(60, true, false, encoder, graph.edge(8, 3).setDistance(1));
        final EdgeIteratorState edge3to2 = GHUtility.setSpeed(60, true, false, encoder, graph.edge(3, 2).setDistance(2));
        final EdgeIteratorState edge2to7 = GHUtility.setSpeed(60, true, false, encoder, graph.edge(2, 7).setDistance(1));
        GHUtility.setSpeed(60, true, false, encoder, graph.edge(7, 9).setDistance(1));
        graph.freeze();
        setMaxLevelOnAllNodes();

        setRestriction(6, 7, 9);
        setTurnCost(8, 3, 2, 2);

        contractNodes(5, 6, 3, 2, 9, 1, 8, 4, 7, 0);
        checkShortcuts(
                createShortcut(2, 8, edge8to3, edge3to2, 5, false, true),
                createShortcut(8, 7, edge8to3.getEdge(), edge2to7.getEdge(), 6, edge2to7.getEdge(), 6, true, false),
                createShortcut(7, 7, edge7to8.getEdge(), edge2to7.getEdge(), edge7to8.getEdge(), 7, 8, true, false)
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
        final EdgeIteratorState e6to0 = GHUtility.setSpeed(60, true, false, encoder, graph.edge(6, 0).setDistance(4));
        final EdgeIteratorState e0to3 = GHUtility.setSpeed(60, true, false, encoder, graph.edge(0, 3).setDistance(5));
        GHUtility.setSpeed(60, true, false, encoder, graph.edge(1, 6).setDistance(1));
        final EdgeIteratorState e6to3 = GHUtility.setSpeed(60, true, false, encoder, graph.edge(6, 3).setDistance(1));
        final EdgeIteratorState e3to5 = GHUtility.setSpeed(60, true, false, encoder, graph.edge(3, 5).setDistance(2));
        GHUtility.setSpeed(60, true, false, encoder, graph.edge(2, 6).setDistance(1));
        GHUtility.setSpeed(60, true, false, encoder, graph.edge(5, 4).setDistance(2));
        graph.freeze();
        setMaxLevelOnAllNodes();
        setRestriction(1, 6, 3);
        contractAllNodesInOrder();
        checkShortcuts(
                // from contracting node 0: need a shortcut because of turn restriction
                createShortcut(3, 6, e6to0, e0to3, 9, false, true),
                // from contracting node 3: two shortcuts:
                // 1) in case we come from 1->6 (cant turn left)
                // 2) in case we come from 2->6 (going via node 0 would be more expensive)
                createShortcut(5, 6, e6to0.getEdge(), e3to5.getEdge(), 7, e3to5.getEdge(), 11, false, true),
                createShortcut(5, 6, e6to3, e3to5, 3, false, true)
        );
    }

    @Test
    public void testContractNodes_alternativeNecessary_noUTurn() {
        //    /->0-->
        //   v       \
        //  4 <-----> 2 -> 3 -> 1
        EdgeIteratorState e0to4 = GHUtility.setSpeed(60, true, true, encoder, graph.edge(4, 0).setDistance(3));
        EdgeIteratorState e0to2 = GHUtility.setSpeed(60, true, false, encoder, graph.edge(0, 2).setDistance(5));
        EdgeIteratorState e2to3 = GHUtility.setSpeed(60, true, false, encoder, graph.edge(2, 3).setDistance(2));
        EdgeIteratorState e1to3 = GHUtility.setSpeed(60, true, false, encoder, graph.edge(3, 1).setDistance(2));
        EdgeIteratorState e2to4 = GHUtility.setSpeed(60, true, true, encoder, graph.edge(4, 2).setDistance(2));
        graph.freeze();

        setMaxLevelOnAllNodes();
        contractAllNodesInOrder();
        checkShortcuts(
                // from contraction of node 0
                createShortcut(2, 4, e0to4, e0to2, 8, false, true),
                // from contraction of node 2
                // It might look like it is always better to go directly from 4 to 2, but when we come from edge (2->4)
                // we may not do a u-turn at 4.
                createShortcut(3, 4, e0to4.getEdge(), e2to3.getEdge(), 5, e2to3.getEdge(), 10, false, true),
                createShortcut(3, 4, e2to4, e2to3, 4, false, true)
        );
    }

    @Test
    public void testContractNodes_bidirectionalLoop() {
        //  1   3
        //  |  /|
        //  0-4-6
        //    |
        //    5-2
        GHUtility.setSpeed(60, true, false, encoder, graph.edge(1, 0).setDistance(1));
        GHUtility.setSpeed(60, true, false, encoder, graph.edge(0, 4).setDistance(2));
        final EdgeIteratorState e4to6 = GHUtility.setSpeed(60, true, true, encoder, graph.edge(4, 6).setDistance(2));
        final EdgeIteratorState e3to6 = GHUtility.setSpeed(60, true, true, encoder, graph.edge(6, 3).setDistance(1));
        final EdgeIteratorState e3to4 = GHUtility.setSpeed(60, true, true, encoder, graph.edge(3, 4).setDistance(1));
        final EdgeIteratorState e4to5 = GHUtility.setSpeed(60, true, false, encoder, graph.edge(4, 5).setDistance(1));
        GHUtility.setSpeed(60, true, false, encoder, graph.edge(5, 2).setDistance(2));
        graph.freeze();

        // enforce loop (going counter-clockwise)
        setRestriction(0, 4, 5);
        setTurnCost(6, 3, 4, 2);
        setTurnCost(4, 3, 6, 4);
        setMaxLevelOnAllNodes();

        contractAllNodesInOrder();
        checkShortcuts(
                // from contraction of node 3
                createShortcut(4, 6, e3to4, e3to6, 6, true, false),
                createShortcut(4, 6, e3to6, e3to4, 4, false, true),
                // from contraction of node 4
                // two 'parallel' shortcuts to preserve shortest paths to 5 when coming from 4->6 and 3->6 !!
                createShortcut(5, 6, e3to6.getEdge(), e4to5.getEdge(), 8, e4to5.getEdge(), 5, false, true),
                createShortcut(5, 6, e4to6, e4to5, 3, false, true)
        );
    }

    @Test
    public void testContractNode_twoNormalEdges_noSourceEdgeToConnect() {
        // 1 --> 0 --> 2 --> 3
        GHUtility.setSpeed(60, true, false, encoder, graph.edge(1, 0).setDistance(3));
        GHUtility.setSpeed(60, true, false, encoder, graph.edge(0, 2).setDistance(5));
        GHUtility.setSpeed(60, true, false, encoder, graph.edge(2, 3).setDistance(1));
        graph.freeze();
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
        GHUtility.setSpeed(60, true, false, encoder, graph.edge(3, 1).setDistance(1));
        GHUtility.setSpeed(60, true, false, encoder, graph.edge(1, 0).setDistance(3));
        GHUtility.setSpeed(60, true, false, encoder, graph.edge(0, 2).setDistance(5));
        graph.freeze();
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
        GHUtility.setSpeed(60, true, false, encoder, graph.edge(0, 3).setDistance(1));
        GHUtility.setSpeed(60, true, false, encoder, graph.edge(3, 2).setDistance(3));
        GHUtility.setSpeed(60, true, false, encoder, graph.edge(2, 4).setDistance(5));
        GHUtility.setSpeed(60, true, false, encoder, graph.edge(4, 1).setDistance(1));
        setRestriction(0, 3, 2);
        setRestriction(2, 4, 1);
        graph.freeze();
        setMaxLevelOnAllNodes();
        contractNodes(2, 0, 3, 4, 1);
        // It looks like we need a shortcut from 3 to 4, but due to the turn restrictions there should be none.
        checkShortcuts();
    }

    @Test
    public void testContractNode_twoNormalEdges_noTurncosts() {
        // 0 --> 3 --> 2 --> 4 --> 1
        GHUtility.setSpeed(60, true, false, encoder, graph.edge(0, 3).setDistance(1));
        final EdgeIteratorState e3to2 = GHUtility.setSpeed(60, true, false, encoder, graph.edge(3, 2).setDistance(3));
        final EdgeIteratorState e2to4 = GHUtility.setSpeed(60, true, false, encoder, graph.edge(2, 4).setDistance(5));
        GHUtility.setSpeed(60, true, false, encoder, graph.edge(4, 1).setDistance(1));
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
        contractNode(nodeContractor, 3, 3);
        contractNode(nodeContractor, 4, 4);
        nodeContractor.finishContraction();
        checkShortcuts(createShortcut(3, 4, e3to2, e2to4, 8));
    }

    @Test
    public void testContractNode_twoNormalEdges_noShortcuts() {
        // 0 --> 1 --> 2 --> 3 --> 4
        GHUtility.setSpeed(60, true, false, encoder, graph.edge(0, 1).setDistance(1));
        GHUtility.setSpeed(60, true, false, encoder, graph.edge(1, 2).setDistance(3));
        GHUtility.setSpeed(60, true, false, encoder, graph.edge(2, 3).setDistance(5));
        GHUtility.setSpeed(60, true, false, encoder, graph.edge(3, 4).setDistance(1));
        graph.freeze();
        setMaxLevelOnAllNodes();
        contractAllNodesInOrder();
        // for each contraction the node levels are such that no shortcuts are introduced
        checkShortcuts();
    }

    @Test
    public void testContractNode_twoNormalEdges_noOutgoingEdges() {
        // 0 --> 1 --> 2 <-- 3 <-- 4
        GHUtility.setSpeed(60, true, false, encoder, graph.edge(0, 1).setDistance(1));
        GHUtility.setSpeed(60, true, false, encoder, graph.edge(1, 2).setDistance(3));
        GHUtility.setSpeed(60, true, false, encoder, graph.edge(3, 2).setDistance(5));
        GHUtility.setSpeed(60, true, false, encoder, graph.edge(4, 3).setDistance(1));
        graph.freeze();
        setMaxLevelOnAllNodes();
        contractNodes(2, 0, 4, 1, 3);
        checkShortcuts();
    }

    @Test
    public void testContractNode_twoNormalEdges_noIncomingEdges() {
        // 0 <-- 1 <-- 2 --> 3 --> 4
        GHUtility.setSpeed(60, true, false, encoder, graph.edge(1, 0).setDistance(1));
        GHUtility.setSpeed(60, true, false, encoder, graph.edge(2, 1).setDistance(3));
        GHUtility.setSpeed(60, true, false, encoder, graph.edge(2, 3).setDistance(5));
        GHUtility.setSpeed(60, true, false, encoder, graph.edge(3, 4).setDistance(1));
        graph.freeze();
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
        GHUtility.setSpeed(60, true, false, encoder, graph.edge(0, 1).setDistance(1));
        GHUtility.setSpeed(60, true, false, encoder, graph.edge(1, 2).setDistance(1));
        GHUtility.setSpeed(60, true, false, encoder, graph.edge(2, 3).setDistance(2));
        GHUtility.setSpeed(60, true, false, encoder, graph.edge(2, 3).setDistance(1));
        GHUtility.setSpeed(60, true, false, encoder, graph.edge(3, 4).setDistance(1));
        graph.freeze();
        setMaxLevelOnAllNodes();
        contractNodes(2, 0, 4, 1, 3);
        // there should be only one shortcut
        checkShortcuts(
                createShortcut(1, 3, 1, 3, 1, 3, 2)
        );
    }

    @Test
    public void testContractNode_duplicateIncomingEdges_differentWeight() {
        // 0 -> 1 -> 2 -> 3 -> 4
        //       \->/
        GHUtility.setSpeed(60, true, false, encoder, graph.edge(0, 1).setDistance(1));
        GHUtility.setSpeed(60, true, false, encoder, graph.edge(1, 2).setDistance(2));
        GHUtility.setSpeed(60, true, false, encoder, graph.edge(1, 2).setDistance(1));
        GHUtility.setSpeed(60, true, false, encoder, graph.edge(2, 3).setDistance(1));
        GHUtility.setSpeed(60, true, false, encoder, graph.edge(3, 4).setDistance(1));
        graph.freeze();
        setMaxLevelOnAllNodes();
        contractNodes(2, 0, 4, 1, 3);
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
        GHUtility.setSpeed(60, true, false, encoder, graph.edge(0, 1).setDistance(1));
        GHUtility.setSpeed(60, true, false, encoder, graph.edge(1, 2).setDistance(1));
        GHUtility.setSpeed(60, true, false, encoder, graph.edge(2, 3).setDistance(1));
        GHUtility.setSpeed(60, true, false, encoder, graph.edge(2, 3).setDistance(1));
        GHUtility.setSpeed(60, true, false, encoder, graph.edge(3, 4).setDistance(1));
        graph.freeze();
        setMaxLevelOnAllNodes();
        contractNodes(2, 0, 4, 1, 3);
        checkNumShortcuts(1);
    }

    @RepeatedTest(10)
    public void testContractNode_duplicateIncomingEdges_sameWeight() {
        // 0 -> 1 -> 2 -> 3 -> 4
        //       \->/
        GHUtility.setSpeed(60, true, false, encoder, graph.edge(0, 1).setDistance(1));
        GHUtility.setSpeed(60, true, false, encoder, graph.edge(1, 2).setDistance(1));
        GHUtility.setSpeed(60, true, false, encoder, graph.edge(1, 2).setDistance(1));
        GHUtility.setSpeed(60, true, false, encoder, graph.edge(2, 3).setDistance(1));
        GHUtility.setSpeed(60, true, false, encoder, graph.edge(3, 4).setDistance(1));
        graph.freeze();
        setMaxLevelOnAllNodes();
        contractNodes(2, 0, 4, 1, 3);
        checkNumShortcuts(1);
    }

    @Test
    public void testContractNode_twoNormalEdges_withTurnCost() {
        // 0 --> 3 --> 2 --> 4 --> 1
        GHUtility.setSpeed(60, true, false, encoder, graph.edge(0, 3).setDistance(1));
        final EdgeIteratorState e3to2 = GHUtility.setSpeed(60, true, false, encoder, graph.edge(3, 2).setDistance(3));
        final EdgeIteratorState e2to4 = GHUtility.setSpeed(60, true, false, encoder, graph.edge(2, 4).setDistance(5));
        GHUtility.setSpeed(60, true, false, encoder, graph.edge(4, 1).setDistance(1));
        setTurnCost(3, 2, 4, 4);
        graph.freeze();
        setMaxLevelOnAllNodes();
        contractNodes(2, 0, 1, 3, 4);
        checkShortcuts(createShortcut(3, 4, e3to2, e2to4, 12));
    }

    @Test
    public void testContractNode_twoNormalEdges_withTurnRestriction() {
        // 0 --> 3 --> 2 --> 4 --> 1
        GHUtility.setSpeed(60, true, false, encoder, graph.edge(0, 3).setDistance(1));
        GHUtility.setSpeed(60, true, false, encoder, graph.edge(3, 2).setDistance(3));
        GHUtility.setSpeed(60, true, false, encoder, graph.edge(2, 4).setDistance(5));
        GHUtility.setSpeed(60, true, false, encoder, graph.edge(4, 1).setDistance(1));
        setRestriction(3, 2, 4);
        graph.freeze();
        setMaxLevelOnAllNodes();
        contractNodes(2, 0, 1, 3, 4);
        checkShortcuts();
    }

    @Test
    public void testContractNode_twoNormalEdges_bidirectional() {
        // 0 -- 3 -- 2 -- 4 -- 1
        GHUtility.setSpeed(60, true, true, encoder, graph.edge(0, 3).setDistance(1));
        final EdgeIteratorState e3to2 = GHUtility.setSpeed(60, true, true, encoder, graph.edge(3, 2).setDistance(3));
        final EdgeIteratorState e2to4 = GHUtility.setSpeed(60, true, true, encoder, graph.edge(2, 4).setDistance(5));
        GHUtility.setSpeed(60, true, true, encoder, graph.edge(4, 1).setDistance(1));
        setTurnCost(e3to2, e2to4, 2, 4);
        setTurnCost(e2to4, e3to2, 2, 4);
        graph.freeze();
        setMaxLevelOnAllNodes();
        contractNodes(2, 0, 1, 3, 4);
        checkShortcuts(
                // note that for now we add a shortcut for each direction. using fwd/bwd flags would be more efficient,
                // but requires a more sophisticated way to determine the 'first' and 'last' original edges at various
                // places
                createShortcut(3, 4, e3to2, e2to4, 12, true, false),
                createShortcut(3, 4, e2to4, e3to2, 12, false, true)
        );
    }

    @Test
    public void testContractNode_twoNormalEdges_bidirectional_differentCosts() {
        // 0 -- 3 -- 2 -- 4 -- 1
        GHUtility.setSpeed(60, true, true, encoder, graph.edge(0, 3).setDistance(1));
        final EdgeIteratorState e2to3 = GHUtility.setSpeed(60, true, true, encoder, graph.edge(3, 2).setDistance(3));
        final EdgeIteratorState e2to4 = GHUtility.setSpeed(60, true, true, encoder, graph.edge(2, 4).setDistance(5));
        GHUtility.setSpeed(60, true, true, encoder, graph.edge(4, 1).setDistance(1));
        setTurnCost(e2to3, e2to4, 2, 4);
        setTurnCost(e2to4, e2to3, 2, 7);
        graph.freeze();
        setMaxLevelOnAllNodes();
        contractNodes(2, 0, 1, 3, 4);
        checkShortcuts(
                createShortcut(3, 4, e2to3, e2to4, 12, true, false),
                createShortcut(3, 4, e2to4, e2to3, 15, false, true)
        );
    }

    @Test
    public void testContractNode_multiple_bidirectional_linear() {
        // 3 -- 2 -- 1 -- 4
        GHUtility.setSpeed(60, true, true, encoder, graph.edge(3, 2).setDistance(2));
        GHUtility.setSpeed(60, true, true, encoder, graph.edge(2, 1).setDistance(3));
        GHUtility.setSpeed(60, true, true, encoder, graph.edge(1, 4).setDistance(6));
        graph.freeze();
        setMaxLevelOnAllNodes();

        contractNodes(1, 2, 3, 4);
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
        GHUtility.setSpeed(60, true, false, encoder, graph.edge(0, 3).setDistance(1));
        final EdgeIteratorState e3to2 = GHUtility.setSpeed(60, true, false, encoder, graph.edge(3, 2).setDistance(3));
        final EdgeIteratorState e2to2 = GHUtility.setSpeed(60, true, false, encoder, graph.edge(2, 2).setDistance(2));
        final EdgeIteratorState e2to4 = GHUtility.setSpeed(60, true, false, encoder, graph.edge(2, 4).setDistance(5));
        GHUtility.setSpeed(60, true, false, encoder, graph.edge(4, 1).setDistance(1));

        setTurnCost(e3to2, e2to2, 2, 2);
        setTurnCost(e2to2, e2to4, 2, 1);
        setTurnCost(e3to2, e2to4, 2, loopHelps ? 6 : 3);
        graph.freeze();
        setMaxLevelOnAllNodes();

        contractNodes(2, 0, 1, 3, 4);
        if (loopHelps) {
            // it is better to take the loop at node 2, so we need to introduce two shortcuts where the second contains
            // the first (this is important for path unpacking)
            checkShortcuts(
                    createShortcut(2, 3, e3to2, e2to2, 7, false, true),
                    createShortcut(3, 4, e3to2.getEdge(), e2to4.getEdge(), 5, e2to4.getEdge(), 13, true, false));
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
        final EdgeIteratorState e7to3 = GHUtility.setSpeed(60, true, false, encoder, graph.edge(7, 3).setDistance(1));
        final EdgeIteratorState e3to5 = GHUtility.setSpeed(60, true, false, encoder, graph.edge(3, 5).setDistance(1));
        final EdgeIteratorState e3to4 = GHUtility.setSpeed(60, true, true, encoder, graph.edge(3, 4).setDistance(2));
        GHUtility.setSpeed(60, true, false, encoder, graph.edge(2, 7).setDistance(1));
        GHUtility.setSpeed(60, true, false, encoder, graph.edge(5, 6).setDistance(1));
        GHUtility.setSpeed(60, true, true, encoder, graph.edge(1, 4).setDistance(1));
        graph.freeze();
        setMaxLevelOnAllNodes();
        setRestriction(7, 3, 5);
        contractNodes(3, 4, 2, 6, 7, 5, 1);
        checkShortcuts(
                // from contracting node 3
                createShortcut(4, 7, e7to3, e3to4, 3, false, true),
                createShortcut(4, 5, e3to4, e3to5, 3, true, false)
                // important! no shortcut from 7 to 5 when contracting node 4, because it includes a u-turn
        );
    }

    @Test
    public void testContractNode_multiple_loops_directTurnIsBest() {
        // turning on any of the loops is restricted so we take the direct turn -> one extra shortcuts
        GraphWithTwoLoops g = new GraphWithTwoLoops(maxCost, maxCost, 1, 2, 3, 4);
        g.contractAndCheckShortcuts(
                createShortcut(7, 8, g.e7to6, g.e6to8, 11, true, false));
    }

    @Test
    public void testContractNode_multiple_loops_leftLoopIsBest() {
        // direct turn is restricted, so we take the left loop -> two extra shortcuts
        GraphWithTwoLoops g = new GraphWithTwoLoops(2, maxCost, 1, 2, 3, maxCost);
        g.contractAndCheckShortcuts(
                createShortcut(6, 7, g.e7to6.getEdge(), g.e1to6.getEdge(), g.e7to6.getEdge(), g.getScEdge(3), 12, false, true),
                createShortcut(7, 8, g.e7to6.getEdge(), g.e6to8.getEdge(), g.getScEdge(4), g.e6to8.getEdge(), 20, true, false)
        );
    }

    @Test
    public void testContractNode_multiple_loops_rightLoopIsBest() {
        // direct turn is restricted, going on left loop is expensive, so we take the right loop -> two extra shortcuts
        GraphWithTwoLoops g = new GraphWithTwoLoops(8, 1, 1, 2, 3, maxCost);
        g.contractAndCheckShortcuts(
                createShortcut(6, 7, g.e7to6.getEdge(), g.e3to6.getEdge(), g.e7to6.getEdge(), g.getScEdge(2), 12, false, true),
                createShortcut(7, 8, g.e7to6.getEdge(), g.e6to8.getEdge(), g.getScEdge(4), g.e6to8.getEdge(), 21, true, false)
        );
    }

    @Test
    public void testContractNode_multiple_loops_leftRightLoopIsBest() {
        // multiple turns are restricted, it is best to take the left and the right loop -> three extra shortcuts
        GraphWithTwoLoops g = new GraphWithTwoLoops(3, maxCost, 1, maxCost, 3, maxCost);
        g.contractAndCheckShortcuts(
                createShortcut(6, 7, g.e7to6.getEdge(), g.e1to6.getEdge(), g.e7to6.getEdge(), g.getScEdge(3), 13, false, true),
                createShortcut(6, 7, g.e7to6.getEdge(), g.e3to6.getEdge(), g.getScEdge(5), g.getScEdge(2), 24, false, true),
                createShortcut(7, 8, g.e7to6.getEdge(), g.e6to8.getEdge(), g.getScEdge(4), g.e6to8.getEdge(), 33, true, false)
        );
    }

    @Test
    public void testContractNode_multiple_loops_rightLeftLoopIsBest() {
        // multiple turns are restricted, it is best to take the right and the left loop -> three extra shortcuts
        GraphWithTwoLoops g = new GraphWithTwoLoops(maxCost, 5, 4, 2, maxCost, maxCost);
        g.contractAndCheckShortcuts(
                createShortcut(6, 7, g.e7to6.getEdge(), g.e3to6.getEdge(), g.e7to6.getEdge(), g.getScEdge(2), 16, false, true),
                createShortcut(6, 7, g.e7to6.getEdge(), g.e1to6.getEdge(), g.getScEdge(5), g.getScEdge(3), 25, false, true),
                createShortcut(7, 8, g.e7to6.getEdge(), g.e6to8.getEdge(), g.getScEdge(4), g.e6to8.getEdge(), 33, true, false)
        );
    }

    //    1 4 2
    //    |\|/|
    //    0-6-3
    //     /|\
    // 9--7 5 8--10
    private class GraphWithTwoLoops {
        final int centerNode = 6;
        final EdgeIteratorState e0to1 = GHUtility.setSpeed(60, true, false, encoder, graph.edge(0, 1).setDistance(3));
        final EdgeIteratorState e1to6 = GHUtility.setSpeed(60, true, false, encoder, graph.edge(1, 6).setDistance(2));
        final EdgeIteratorState e6to0 = GHUtility.setSpeed(60, true, false, encoder, graph.edge(6, 0).setDistance(4));
        final EdgeIteratorState e2to3 = GHUtility.setSpeed(60, true, false, encoder, graph.edge(2, 3).setDistance(2));
        final EdgeIteratorState e3to6 = GHUtility.setSpeed(60, true, false, encoder, graph.edge(3, 6).setDistance(7));
        final EdgeIteratorState e6to2 = GHUtility.setSpeed(60, true, false, encoder, graph.edge(6, 2).setDistance(1));
        final EdgeIteratorState e7to6 = GHUtility.setSpeed(60, true, false, encoder, graph.edge(7, 6).setDistance(1));
        final EdgeIteratorState e6to8 = GHUtility.setSpeed(60, true, false, encoder, graph.edge(6, 8).setDistance(6));
        final EdgeIteratorState e9to7 = GHUtility.setSpeed(60, true, false, encoder, graph.edge(9, 7).setDistance(2));
        final EdgeIteratorState e8to10 = GHUtility.setSpeed(60, true, false, encoder, graph.edge(8, 10).setDistance(3));
        // these two edges help to avoid loop avoidance for the left and right loops
        final EdgeIteratorState e4to6 = GHUtility.setSpeed(60, true, false, encoder, graph.edge(4, 6).setDistance(1));
        final EdgeIteratorState e5to6 = GHUtility.setSpeed(60, true, false, encoder, graph.edge(5, 6).setDistance(1));
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

            graph.freeze();
            setMaxLevelOnAllNodes();
        }

        private void contractAndCheckShortcuts(Shortcut... shortcuts) {
            contractNodes(0, 1, 2, 3, 4, 5, 6, 9, 10, 7, 8);
            HashSet<Shortcut> expectedShortcuts = new HashSet<>();
            expectedShortcuts.addAll(Arrays.asList(
                    createShortcut(1, 6, e6to0, e0to1, 7, false, true),
                    createShortcut(6, 6, e6to0.getEdge(), e1to6.getEdge(), getScEdge(0), e1to6.getEdge(), 9, true, false),
                    createShortcut(3, 6, e6to2, e2to3, 3, false, true),
                    createShortcut(6, 6, e6to2.getEdge(), e3to6.getEdge(), getScEdge(1), e3to6.getEdge(), 10, true, false)
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
                createShortcut(1, 2, g.e1to0, g.e0to2, 7)
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
        private final EdgeIteratorState e4to1 = GHUtility.setSpeed(60, true, false, encoder, graph.edge(4, 1).setDistance(2));
        private final EdgeIteratorState e1to0 = GHUtility.setSpeed(60, true, false, encoder, graph.edge(1, 0).setDistance(4));
        private final EdgeIteratorState e1to2 = GHUtility.setSpeed(60, true, false, encoder, graph.edge(1, 2).setDistance(3));
        private final EdgeIteratorState e0to2 = GHUtility.setSpeed(60, true, false, encoder, graph.edge(0, 2).setDistance(3));
        private final EdgeIteratorState e2to3 = GHUtility.setSpeed(60, true, false, encoder, graph.edge(2, 3).setDistance(2));

        GraphWithDetour(int turnCost42, int turnCost13, int turnCost40, int turnCost03) {
            setTurnCost(e4to1, e1to2, 1, turnCost42);
            setTurnCost(e4to1, e1to0, 1, turnCost40);
            setTurnCost(e1to2, e2to3, 2, turnCost13);
            setTurnCost(e0to2, e2to3, 2, turnCost03);
            graph.freeze();
            setMaxLevelOnAllNodes();
        }

    }

    @Test
    public void testContractNode_detour_multipleInOut_needsShortcut() {
        GraphWithDetourMultipleInOutEdges g = new GraphWithDetourMultipleInOutEdges(0, 0, 0, 1, 3);
        contractNodes(0, 2, 5, 6, 7, 1, 3, 4);
        checkShortcuts(createShortcut(1, 4, g.e1to0, g.e0to4, 7));
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
        final EdgeIteratorState e5to1 = GHUtility.setSpeed(60, true, false, encoder, graph.edge(5, 1).setDistance(3));
        final EdgeIteratorState e2to1 = GHUtility.setSpeed(60, true, false, encoder, graph.edge(2, 1).setDistance(2));
        final EdgeIteratorState e1to3 = GHUtility.setSpeed(60, true, false, encoder, graph.edge(1, 3).setDistance(1));
        final EdgeIteratorState e3to4 = GHUtility.setSpeed(60, true, false, encoder, graph.edge(3, 4).setDistance(2));
        final EdgeIteratorState e1to0 = GHUtility.setSpeed(60, true, false, encoder, graph.edge(1, 0).setDistance(5));
        final EdgeIteratorState e0to4 = GHUtility.setSpeed(60, true, false, encoder, graph.edge(0, 4).setDistance(2));
        final EdgeIteratorState e4to6 = GHUtility.setSpeed(60, true, false, encoder, graph.edge(4, 6).setDistance(1));
        final EdgeIteratorState e4to7 = GHUtility.setSpeed(60, true, false, encoder, graph.edge(4, 7).setDistance(3));

        GraphWithDetourMultipleInOutEdges(int turnCost20, int turnCost50, int turnCost23, int turnCost53, int turnCost36) {
            setTurnCost(e1to3, e3to4, 3, 2);
            setTurnCost(e2to1, e1to0, 1, turnCost20);
            setTurnCost(e2to1, e1to3, 1, turnCost23);
            setTurnCost(e5to1, e1to0, 1, turnCost50);
            setTurnCost(e5to1, e1to3, 1, turnCost53);
            setTurnCost(e3to4, e4to6, 4, turnCost36);
            graph.freeze();
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
                createShortcut(1, 2, g.e2to0, g.e0to1, 3, false, true),
                createShortcut(2, 2, g.e2to0.getEdge(), g.e1to2.getEdge(), numEdges, g.e1to2.getEdge(), 4, true, false)
        );
    }

    @Test
    public void testContractNode_loopAvoidance_loopAvoidable() {
        // turning from 3 via 2 to 4 is cheap, it is better to go straight 3-2-4, no loop shortcut necessary
        GraphWithLoop g = new GraphWithLoop(3);
        contractNodes(0, 1, 3, 4, 5, 2);
        checkShortcuts(
                createShortcut(1, 2, g.e2to0, g.e0to1, 3, false, true)
        );
    }

    //   0 - 1
    //    \ /
    // 3 - 2 - 4
    //     |
    //     5
    private class GraphWithLoop {
        final EdgeIteratorState e0to1 = GHUtility.setSpeed(60, true, false, encoder, graph.edge(0, 1).setDistance(2));
        final EdgeIteratorState e1to2 = GHUtility.setSpeed(60, true, false, encoder, graph.edge(1, 2).setDistance(1));
        final EdgeIteratorState e2to0 = GHUtility.setSpeed(60, true, false, encoder, graph.edge(2, 0).setDistance(1));
        final EdgeIteratorState e3to2 = GHUtility.setSpeed(60, true, false, encoder, graph.edge(3, 2).setDistance(3));
        final EdgeIteratorState e2to4 = GHUtility.setSpeed(60, true, false, encoder, graph.edge(2, 4).setDistance(5));
        final EdgeIteratorState e5to2 = GHUtility.setSpeed(60, true, false, encoder, graph.edge(5, 2).setDistance(2));

        GraphWithLoop(int turnCost34) {
            setTurnCost(e3to2, e2to4, 2, turnCost34);
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
        GHUtility.setSpeed(60, true, false, encoder, graph.edge(0, 1).setDistance(1));
        GHUtility.setSpeed(60, true, false, encoder, graph.edge(1, 2).setDistance(1));
        GHUtility.setSpeed(60, true, false, encoder, graph.edge(2, 3).setDistance(5));
        GHUtility.setSpeed(60, true, false, encoder, graph.edge(3, 4).setDistance(1));
        GHUtility.setSpeed(60, true, false, encoder, graph.edge(1, 5).setDistance(1));
        GHUtility.setSpeed(60, true, false, encoder, graph.edge(5, 9).setDistance(1));
        GHUtility.setSpeed(60, true, false, encoder, graph.edge(9, 3).setDistance(1));
        GHUtility.setSpeed(60, true, false, encoder, graph.edge(2, 7).setDistance(6));
        GHUtility.setSpeed(60, true, false, encoder, graph.edge(9, 7).setDistance(1));
        GHUtility.setSpeed(60, true, false, encoder, graph.edge(7, 10).setDistance(1));
        graph.freeze();
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
        GHUtility.setSpeed(60, true, false, encoder, graph.edge(0, 1).setDistance(1));
        GHUtility.setSpeed(60, true, false, encoder, graph.edge(1, 2).setDistance(1));
        GHUtility.setSpeed(60, true, false, encoder, graph.edge(1, 5).setDistance(1));
        EdgeIteratorState e2to3 = GHUtility.setSpeed(60, true, false, encoder, graph.edge(2, 3).setDistance(1));
        EdgeIteratorState e3to4 = GHUtility.setSpeed(60, true, false, encoder, graph.edge(3, 4).setDistance(1));
        GHUtility.setSpeed(60, true, false, encoder, graph.edge(4, 5).setDistance(1));
        EdgeIteratorState e5to3 = GHUtility.setSpeed(60, true, false, encoder, graph.edge(5, 3).setDistance(1));
        graph.freeze();
        setMaxLevelOnAllNodes();
        contractNodes(3, 2, 0, 1, 5, 4);
        // when contracting node 2 there is a witness (1-5-3-4) and no shortcut from 1 to 4 should be introduced.
        // what might be tricky here is that both the original path and the witness path have equal weight!
        // so we have to make sure that the equal weight witness is not rejected to update the currently best
        // path, or (depending on the implementation-specific edge traversal order) the original path does *not*
        // update/overwrite the already found witness path.
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
        GHUtility.setSpeed(60, true, false, encoder, graph.edge(0, 1).setDistance(1));
        GHUtility.setSpeed(60, true, false, encoder, graph.edge(1, 2).setDistance(1));
        GHUtility.setSpeed(60, true, false, encoder, graph.edge(1, 3).setDistance(1));
        GHUtility.setSpeed(60, true, false, encoder, graph.edge(1, 4).setDistance(1));
        GHUtility.setSpeed(60, true, true, encoder, graph.edge(2, 5).setDistance(1)); // bidirectional
        GHUtility.setSpeed(60, true, false, encoder, graph.edge(3, 5).setDistance(1));
        GHUtility.setSpeed(60, true, true, encoder, graph.edge(4, 5).setDistance(1)); // bidirectional
        GHUtility.setSpeed(60, true, false, encoder, graph.edge(5, 6).setDistance(1));
        graph.freeze();
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
        GHUtility.setSpeed(60, true, false, encoder, graph.edge(0, 1).setDistance(1));
        GHUtility.setSpeed(60, true, true, encoder, graph.edge(1, 2).setDistance(1)); // bidirectional
        GHUtility.setSpeed(60, true, false, encoder, graph.edge(1, 3).setDistance(1));
        GHUtility.setSpeed(60, true, true, encoder, graph.edge(1, 4).setDistance(1)); // bidirectional
        GHUtility.setSpeed(60, true, false, encoder, graph.edge(2, 5).setDistance(1));
        GHUtility.setSpeed(60, true, false, encoder, graph.edge(3, 5).setDistance(1));
        GHUtility.setSpeed(60, true, false, encoder, graph.edge(4, 5).setDistance(1));
        GHUtility.setSpeed(60, true, false, encoder, graph.edge(5, 6).setDistance(1));
        graph.freeze();
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
        GHUtility.setSpeed(60, true, false, encoder, graph.edge(0, 1).setDistance(1));
        GHUtility.setSpeed(60, true, edge1to2bidirectional, encoder, graph.edge(1, 2).setDistance(1));
        GHUtility.setSpeed(60, true, false, encoder, graph.edge(2, 3).setDistance(1));
        GHUtility.setSpeed(60, true, false, encoder, graph.edge(3, 4).setDistance(1));
        GHUtility.setSpeed(60, true, true, encoder, graph.edge(1, 5).setDistance(1));
        GHUtility.setSpeed(60, true, false, encoder, graph.edge(5, 3).setDistance(1));
        graph.freeze();
        setMaxLevelOnAllNodes();
        contractNodes(2, 0, 1, 5, 4, 3);
        // we might come from (5->1) so we still need a way back to (3->4) -> we need a shortcut
        Shortcut expectedShortcuts = createShortcut(1, 3, 1, 2, 1, 2, 2);
        checkShortcuts(expectedShortcuts);
    }

    @Test
    public void testContractNode_bidirectional_edge_at_fromNode_going_to_node() {
        // 0 -> 1 <-> 5
        //      v     v
        //      2 --> 3 -> 4
        GHUtility.setSpeed(60, true, false, encoder, graph.edge(0, 1).setDistance(1));
        GHUtility.setSpeed(60, true, false, encoder, graph.edge(1, 2).setDistance(1));
        GHUtility.setSpeed(60, true, false, encoder, graph.edge(2, 3).setDistance(1));
        GHUtility.setSpeed(60, true, false, encoder, graph.edge(3, 4).setDistance(1));
        GHUtility.setSpeed(60, true, true, encoder, graph.edge(1, 5).setDistance(1));
        GHUtility.setSpeed(60, true, false, encoder, graph.edge(5, 3).setDistance(1));
        graph.freeze();
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
        GHUtility.setSpeed(60, true, false, encoder, graph.edge(0, 1).setDistance(1));
        GHUtility.setSpeed(60, true, false, encoder, graph.edge(1, 2).setDistance(1));
        GHUtility.setSpeed(60, true, false, encoder, graph.edge(2, 3).setDistance(1));
        GHUtility.setSpeed(60, true, false, encoder, graph.edge(3, 4).setDistance(1));
        GHUtility.setSpeed(60, true, false, encoder, graph.edge(4, 5).setDistance(1));
        GHUtility.setSpeed(60, true, false, encoder, graph.edge(5, 6).setDistance(1));
        GHUtility.setSpeed(60, true, false, encoder, graph.edge(6, 7).setDistance(1));
        GHUtility.setSpeed(60, true, false, encoder, graph.edge(7, 8).setDistance(1));
        GHUtility.setSpeed(60, true, false, encoder, graph.edge(2, 9).setDistance(1));
        GHUtility.setSpeed(60, true, false, encoder, graph.edge(9, 6).setDistance(1));
        GHUtility.setSpeed(60, true, false, encoder, graph.edge(10, 1).setDistance(1));
        GHUtility.setSpeed(60, true, false, encoder, graph.edge(7, 11).setDistance(1));
        graph.freeze();
        setMaxLevelOnAllNodes();
        contractNodes(2, 6, 3, 5, 4, 0, 8, 10, 11, 1, 7, 9);
        // note that the shortcut edge ids depend on the insertion order which might change when changing the implementation
        checkShortcuts(
                createShortcut(3, 1, 1, 2, 1, 2, 2, false, true),
                createShortcut(1, 9, 1, 8, 1, 8, 2, true, false),
                createShortcut(5, 7, 5, 6, 5, 6, 2, true, false),
                createShortcut(7, 9, 9, 6, 9, 6, 2, false, true),
                createShortcut(4, 1, 1, 3, 12, 3, 3, false, true),
                createShortcut(4, 7, 4, 6, 4, 13, 3, true, false)
        );
    }

    @Test
    public void testNodeContraction_witnessBetterBecauseOfTurnCostAtTargetNode() {
        // when we contract node 2 we should not stop searching for witnesses when edge 2->3 is settled, because then we miss
        // the witness path via 5 that is found later, but still has less weight because of the turn costs at node 3
        // 0 -> 1 -> 2 -> 3 -> 4
        //       \       /         
        //        -- 5 ->   
        GHUtility.setSpeed(60, true, false, encoder, graph.edge(0, 1).setDistance(1));
        GHUtility.setSpeed(60, true, false, encoder, graph.edge(1, 2).setDistance(1));
        GHUtility.setSpeed(60, true, false, encoder, graph.edge(2, 3).setDistance(1));
        GHUtility.setSpeed(60, true, false, encoder, graph.edge(3, 4).setDistance(1));
        GHUtility.setSpeed(60, true, false, encoder, graph.edge(1, 5).setDistance(3));
        GHUtility.setSpeed(60, true, false, encoder, graph.edge(5, 3).setDistance(1));
        setTurnCost(2, 3, 4, 5);
        setTurnCost(5, 3, 4, 2);
        graph.freeze();
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
        GHUtility.setSpeed(60, true, false, encoder, graph.edge(0, 1).setDistance(1));
        GHUtility.setSpeed(60, true, false, encoder, graph.edge(1, 2).setDistance(1));
        GHUtility.setSpeed(60, true, false, encoder, graph.edge(2, 3).setDistance(1));
        GHUtility.setSpeed(60, true, false, encoder, graph.edge(3, 4).setDistance(1));
        GHUtility.setSpeed(60, true, false, encoder, graph.edge(1, 3).setDistance(4));
        GHUtility.setSpeed(60, true, false, encoder, graph.edge(4, 5).setDistance(1));

        graph.freeze();
        setMaxLevelOnAllNodes();
        contractNodes(3, 0, 5, 1, 4, 2);
        checkShortcuts(
                createShortcut(4, 2, 2, 3, 2, 3, 2, false, true)
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
        GHUtility.setSpeed(60, true, false, encoder, graph.edge(0, 1).setDistance(1));
        GHUtility.setSpeed(60, true, false, encoder, graph.edge(1, 2).setDistance(1));
        GHUtility.setSpeed(60, true, false, encoder, graph.edge(2, 3).setDistance(1));
        GHUtility.setSpeed(60, true, false, encoder, graph.edge(3, 4).setDistance(1));
        GHUtility.setSpeed(60, true, false, encoder, graph.edge(4, 5).setDistance(1));
        GHUtility.setSpeed(60, true, false, encoder, graph.edge(2, 4).setDistance(4));

        graph.freeze();
        setMaxLevelOnAllNodes();
        contractNodes(2, 0, 5, 1, 4, 3);
        checkShortcuts(
                createShortcut(1, 3, 1, 2, 1, 2, 2)
        );
    }

    @Test
    public void testNodeContraction_parallelEdges_onlyOneLoopShortcutNeeded() {
        // 0 -- 1 -- 2
        //  \--/
        EdgeIteratorState edge0 = GHUtility.setSpeed(60, true, true, encoder, graph.edge(0, 1).setDistance(2));
        EdgeIteratorState edge1 = GHUtility.setSpeed(60, true, true, encoder, graph.edge(1, 0).setDistance(4));
        GHUtility.setSpeed(60, true, true, encoder, graph.edge(1, 2).setDistance(5));
        setTurnCost(edge0, edge1, 0, 1);
        setTurnCost(edge1, edge0, 0, 2);
        graph.freeze();
        setMaxLevelOnAllNodes();
        contractNodes(0, 2, 1);
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
        GHUtility.setSpeed(60, true, true, encoder, graph.edge(1, 3).setDistance(47));
        GHUtility.setSpeed(60, true, true, encoder, graph.edge(2, 4).setDistance(19));
        EdgeIteratorState e2 = GHUtility.setSpeed(60, true, true, encoder, graph.edge(2, 5).setDistance(38));
        EdgeIteratorState e3 = GHUtility.setSpeed(60, true, true, encoder, graph.edge(2, 5).setDistance(57)); // note there is a duplicate edge here (with different weight)
        GHUtility.setSpeed(60, true, true, encoder, graph.edge(3, 4).setDistance(10));
        EdgeIteratorState e5 = GHUtility.setSpeed(60, true, true, encoder, graph.edge(4, 5).setDistance(56));

        setTurnCost(e3, e2, 5, 4);
        setTurnCost(e2, e3, 5, 5);
        setTurnCost(e5, e3, 5, 3);
        setTurnCost(e3, e5, 5, 2);
        setTurnCost(e2, e5, 5, 2);
        setTurnCost(e5, e2, 5, 1);
        graph.freeze();
        setMaxLevelOnAllNodes();
        contractNodes(4, 5, 1, 3, 2);
        // note that the shortcut edge ids depend on the insertion order which might change when changing the implementation
        checkNumShortcuts(11);
        checkShortcuts(
                // from node 4 contraction
                createShortcut(5, 3, 5, 4, 5, 4, 66, true, false),
                createShortcut(5, 3, 4, 5, 4, 5, 66, false, true),
                createShortcut(3, 2, 1, 4, 1, 4, 29, false, true),
                createShortcut(3, 2, 4, 1, 4, 1, 29, true, false),
                createShortcut(5, 2, 1, 5, 1, 5, 75, false, true),
                createShortcut(5, 2, 5, 1, 5, 1, 75, true, false),
                // from node 5 contraction
                createShortcut(2, 2, 3, 2, 3, 2, 99, true, false),
                createShortcut(2, 2, 3, 1, 3, 6, 134, true, false),
                createShortcut(2, 2, 1, 2, 8, 2, 114, true, false),
                createShortcut(3, 2, 2, 4, 2, 7, 106, false, true),
                createShortcut(3, 2, 4, 2, 9, 2, 105, true, false)
        );
    }

    @Test
    public void testNodeContraction_tripleConnection() {
        GHUtility.setSpeed(60, true, true, encoder, graph.edge(0, 1).setDistance(1.0));
        GHUtility.setSpeed(60, true, true, encoder, graph.edge(0, 1).setDistance(2.0));
        GHUtility.setSpeed(60, true, true, encoder, graph.edge(0, 1).setDistance(3.5));
        graph.freeze();
        setMaxLevelOnAllNodes();
        contractNodes(1, 0);
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
        GHUtility.setSpeed(60, true, false, encoder, graph.edge(0, 1).setDistance(1));
        GHUtility.setSpeed(60, true, false, encoder, graph.edge(1, 2).setDistance(1));
        GHUtility.setSpeed(60, true, false, encoder, graph.edge(2, 1).setDistance(1));
        GHUtility.setSpeed(60, true, false, encoder, graph.edge(1, 3).setDistance(1));
        graph.freeze();
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
        GHUtility.setSpeed(60, true, false, encoder, graph.edge(0, 4).setDistance(2));
        GHUtility.setSpeed(60, true, true, encoder, graph.edge(4, 3).setDistance(2));
        GHUtility.setSpeed(60, true, true, encoder, graph.edge(3, 2).setDistance(1));
        GHUtility.setSpeed(60, true, true, encoder, graph.edge(2, 4).setDistance(1));
        GHUtility.setSpeed(60, true, false, encoder, graph.edge(4, 1).setDistance(1));
        graph.freeze();
        setMaxLevelOnAllNodes();

        // enforce loop (going counter-clockwise)
        setRestriction(0, 4, 1);
        setTurnCost(4, 2, 3, 4);
        setTurnCost(3, 2, 4, 2);
        contractNodes(2, 0, 1, 4, 3);
        checkShortcuts(
                createShortcut(4, 3, 3, 2, 3, 2, 6, true, false),
                createShortcut(4, 3, 2, 3, 2, 3, 4, false, true)
        );
    }

    @Test
    public void testFindPath_finiteUTurnCost() {
        chGraph = graph.getCHGraph(chConfigs.get(1).getName());
        weighting = chGraph.getCHConfig().getWeighting();
        // turning to 1 at node 3 when coming from 0 is forbidden, but taking the full loop 3-4-2-3 is very
        // expensive, so the best solution is to go straight to 4 and take a u-turn there
        //   1
        //   |
        // 0-3-4
        //   |/
        //   2
        GHUtility.setSpeed(60, true, false, encoder, graph.edge(0, 3).setDistance(100));
        GHUtility.setSpeed(60, true, true, encoder, graph.edge(3, 4).setDistance(100));
        GHUtility.setSpeed(60, true, false, encoder, graph.edge(4, 2).setDistance(500));
        GHUtility.setSpeed(60, true, false, encoder, graph.edge(2, 3).setDistance(200));
        GHUtility.setSpeed(60, true, false, encoder, graph.edge(3, 1).setDistance(100));
        graph.freeze();
        setMaxLevelOnAllNodes();
        setRestriction(0, 3, 1);
        contractNodes(4, 0, 1, 2, 3);
        checkShortcuts(
                createShortcut(2, 3, 1, 2, 1, 2, 600, false, true),
                createShortcut(3, 3, 1, 1, 1, 1, 260, true, false)
        );
    }

    @Test
    public void testNodeContraction_turnRestrictionAndLoop() {
        //  /\    /<-3
        // 0  1--2
        //  \/    \->4
        GHUtility.setSpeed(60, true, true, encoder, graph.edge(0, 1).setDistance(5));
        GHUtility.setSpeed(60, true, true, encoder, graph.edge(0, 1).setDistance(6));
        GHUtility.setSpeed(60, true, true, encoder, graph.edge(1, 2).setDistance(2));
        GHUtility.setSpeed(60, true, false, encoder, graph.edge(3, 2).setDistance(3));
        GHUtility.setSpeed(60, true, false, encoder, graph.edge(2, 4).setDistance(3));
        setRestriction(3, 2, 4);
        graph.freeze();
        setMaxLevelOnAllNodes();
        contractNodes(0, 3, 4, 2, 1);
        checkNumShortcuts(1);
    }

    @Test
    public void testNodeContraction_forwardLoopNeedsToBeRecognizedAsIncoming() {
        //     ---
        //     \ /
        // 0 -- 1 -- 2 -- 3 -- 4
        EdgeIteratorState edge0 = GHUtility.setSpeed(60, true, true, encoder, graph.edge(0, 1).setDistance(1));
        EdgeIteratorState edge1 = GHUtility.setSpeed(60, true, false, encoder, graph.edge(1, 1).setDistance(1));
        EdgeIteratorState edge2 = GHUtility.setSpeed(60, true, true, encoder, graph.edge(1, 2).setDistance(1));
        EdgeIteratorState edge3 = GHUtility.setSpeed(60, true, false, encoder, graph.edge(2, 3).setDistance(1));
        EdgeIteratorState edge4 = GHUtility.setSpeed(60, true, false, encoder, graph.edge(3, 4).setDistance(1));
        setRestriction(edge0, edge2, 1);
        graph.freeze();
        setMaxLevelOnAllNodes();
        contractNodes(2, 0, 4, 1, 3);
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
        GHUtility.setSpeed(60, true, false, encoder, graph.edge(0, 1).setDistance(51.401));
        GHUtility.setSpeed(60, true, false, encoder, graph.edge(1, 2).setDistance(70.041));
        GHUtility.setSpeed(60, true, false, encoder, graph.edge(2, 3).setDistance(75.806));
        GHUtility.setSpeed(60, true, false, encoder, graph.edge(3, 4).setDistance(05.003));
        graph.freeze();
        setMaxLevelOnAllNodes();
        contractNodes(2, 0, 1, 3, 4);
        checkShortcuts(
                createShortcut(1, 3, 1, 2, 1, 2, 145.847)
        );
    }

    @Test
    public void testNodeContraction_zeroWeightLoop_loopOnly() {
        // zero weight loops are quite a headache..., also see #1355
        //                  /|
        // 0 -> 1 -> 2 -> 3 --
        GHUtility.setSpeed(60, true, false, encoder, graph.edge(0, 1).setDistance(1));
        GHUtility.setSpeed(60, true, false, encoder, graph.edge(1, 2).setDistance(1));
        GHUtility.setSpeed(60, true, false, encoder, graph.edge(2, 3).setDistance(1));
        GHUtility.setSpeed(60, true, false, encoder, graph.edge(3, 3).setDistance(0));
        graph.freeze();
        setMaxLevelOnAllNodes();
        contractNodes(2, 0, 1, 3);
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
        GHUtility.setSpeed(60, true, false, encoder, graph.edge(0, 1).setDistance(1));
        GHUtility.setSpeed(60, true, false, encoder, graph.edge(1, 2).setDistance(1));
        GHUtility.setSpeed(60, true, false, encoder, graph.edge(2, 3).setDistance(1));
        GHUtility.setSpeed(60, true, false, encoder, graph.edge(3, 3).setDistance(0));
        GHUtility.setSpeed(60, true, false, encoder, graph.edge(3, 4).setDistance(1));
        graph.freeze();
        setMaxLevelOnAllNodes();
        contractNodes(2, 0, 1, 4, 3);
        checkShortcuts(
                createShortcut(1, 3, 1, 2, 1, 2, 2)
        );
    }

    @Test
    public void testNodeContraction_zeroWeightLoop_twoLoops() {
        //                  /|
        // 0 -> 1 -> 2 -> 3 --
        //                  \|
        GHUtility.setSpeed(60, true, false, encoder, graph.edge(0, 1).setDistance(1));
        GHUtility.setSpeed(60, true, false, encoder, graph.edge(1, 2).setDistance(1));
        GHUtility.setSpeed(60, true, false, encoder, graph.edge(2, 3).setDistance(1));
        GHUtility.setSpeed(60, true, false, encoder, graph.edge(3, 3).setDistance(0));
        GHUtility.setSpeed(60, true, false, encoder, graph.edge(3, 3).setDistance(0));
        graph.freeze();
        setMaxLevelOnAllNodes();
        contractNodes(2, 0, 1, 3);
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
        GHUtility.setSpeed(60, true, false, encoder, graph.edge(0, 1).setDistance(1));
        GHUtility.setSpeed(60, true, false, encoder, graph.edge(1, 2).setDistance(1));
        GHUtility.setSpeed(60, true, false, encoder, graph.edge(2, 3).setDistance(1));
        GHUtility.setSpeed(60, true, false, encoder, graph.edge(3, 3).setDistance(0));
        GHUtility.setSpeed(60, true, false, encoder, graph.edge(3, 3).setDistance(0));
        GHUtility.setSpeed(60, true, false, encoder, graph.edge(3, 4).setDistance(1));
        graph.freeze();
        setMaxLevelOnAllNodes();
        contractNodes(2, 0, 1, 4, 3);
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
        GHUtility.setSpeed(60, true, false, encoder, graph.edge(0, 1).setDistance(1));
        GHUtility.setSpeed(60, true, false, encoder, graph.edge(1, 2).setDistance(1));
        GHUtility.setSpeed(60, true, false, encoder, graph.edge(3, 3).setDistance(0));
        GHUtility.setSpeed(60, true, false, encoder, graph.edge(3, 3).setDistance(0));
        GHUtility.setSpeed(60, true, false, encoder, graph.edge(2, 3).setDistance(1));
        GHUtility.setSpeed(60, true, false, encoder, graph.edge(3, 4).setDistance(1));
        graph.freeze();
        setMaxLevelOnAllNodes();
        contractNodes(2, 0, 1, 4, 3);
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
        GHUtility.setSpeed(60, true, false, encoder, graph.edge(3, 3).setDistance(0));
        GHUtility.setSpeed(60, true, false, encoder, graph.edge(0, 1).setDistance(1));
        GHUtility.setSpeed(60, true, false, encoder, graph.edge(3, 3).setDistance(0));
        GHUtility.setSpeed(60, true, false, encoder, graph.edge(1, 2).setDistance(1));
        GHUtility.setSpeed(60, true, false, encoder, graph.edge(3, 3).setDistance(0));
        GHUtility.setSpeed(60, true, false, encoder, graph.edge(2, 3).setDistance(1));
        GHUtility.setSpeed(60, true, false, encoder, graph.edge(3, 3).setDistance(0));
        GHUtility.setSpeed(60, true, false, encoder, graph.edge(3, 3).setDistance(0));
        GHUtility.setSpeed(60, true, false, encoder, graph.edge(3, 4).setDistance(1));
        GHUtility.setSpeed(60, true, false, encoder, graph.edge(3, 3).setDistance(0));
        graph.freeze();
        setMaxLevelOnAllNodes();
        contractNodes(2, 0, 1, 4, 3);
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
        GHUtility.setSpeed(60, true, false, encoder, graph.edge(0, 1).setDistance(1));
        GHUtility.setSpeed(60, true, false, encoder, graph.edge(1, 2).setDistance(1));
        EdgeIteratorState edge2 = GHUtility.setSpeed(60, true, false, encoder, graph.edge(2, 3).setDistance(1));
        EdgeIteratorState edge3 = GHUtility.setSpeed(60, true, false, encoder, graph.edge(3, 3).setDistance(0));
        EdgeIteratorState edge4 = GHUtility.setSpeed(60, true, false, encoder, graph.edge(3, 4).setDistance(1));
        // add a few more loops to make this test more difficult to pass
        GHUtility.setSpeed(60, true, false, encoder, graph.edge(3, 3).setDistance(0));
        GHUtility.setSpeed(60, true, false, encoder, graph.edge(3, 3).setDistance(0));
        // we have to use the zero weight loop so it may not be excluded
        setTurnCost(edge2, edge3, 3, 5);
        setRestriction(edge2, edge4, 3);
        graph.freeze();
        setMaxLevelOnAllNodes();
        contractNodes(2, 0, 4, 1, 3);
        checkNumShortcuts(1);
    }

    @Test
    public void testNodeContraction_numPolledEdges() {
        GHUtility.setSpeed(60, true, false, encoder, graph.edge(3, 2).setDistance(71.203000));
        GHUtility.setSpeed(60, true, false, encoder, graph.edge(0, 3).setDistance(79.003000));
        GHUtility.setSpeed(60, true, false, encoder, graph.edge(2, 0).setDistance(21.328000));
        GHUtility.setSpeed(60, true, false, encoder, graph.edge(2, 4).setDistance(16.499000));
        GHUtility.setSpeed(60, true, false, encoder, graph.edge(4, 2).setDistance(16.487000));
        GHUtility.setSpeed(60, true, false, encoder, graph.edge(6, 1).setDistance(55.603000));
        GHUtility.setSpeed(60, true, false, encoder, graph.edge(2, 1).setDistance(33.453000));
        GHUtility.setSpeed(60, true, false, encoder, graph.edge(4, 5).setDistance(29.665000));
        graph.freeze();
        setMaxLevelOnAllNodes();
        EdgeBasedNodeContractor nodeContractor = createNodeContractor();
        nodeContractor.contractNode(0);
        assertTrue(nodeContractor.getNumPolledEdges() > 0, "no polled edges, something is wrong");
        assertTrue(nodeContractor.getNumPolledEdges() <= 8, "too many edges polled: " + nodeContractor.getNumPolledEdges());
    }

    private void contractNode(NodeContractor nodeContractor, int node, int level) {
        chGraph.setLevel(node, level);
        nodeContractor.contractNode(node);
    }

    private void contractAllNodesInOrder() {
        EdgeBasedNodeContractor nodeContractor = createNodeContractor();
        for (int node = 0; node < graph.getNodes(); ++node) {
            chGraph.setLevel(node, node);
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
            chGraph.setLevel(nodes[i], i);
            nodeContractor.contractNode(nodes[i]);
        }
        nodeContractor.finishContraction();
    }

    private EdgeBasedNodeContractor createNodeContractor() {
        CHPreparationGraph.TurnCostFunction turnCostFunction = CHPreparationGraph.buildTurnCostFunctionFromTurnCostStorage(graph, weighting);
        CHPreparationGraph prepareGraph = CHPreparationGraph.edgeBased(graph.getNodes(), graph.getEdges(), turnCostFunction);
        CHPreparationGraph.buildFromGraph(prepareGraph, graph, weighting);
        EdgeBasedNodeContractor.ShortcutHandler shortcutInserter = new EdgeBasedShortcutInserter(chGraph);
        EdgeBasedNodeContractor nodeContractor = new EdgeBasedNodeContractor(prepareGraph, shortcutInserter, new PMap());
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
        graph.getTurnCostStorage().set(((EncodedValueLookup) encoder).getDecimalEncodedValue(TurnCost.key("car")), inEdge.getEdge(), viaNode, outEdge.getEdge(), cost1);
    }

    private EdgeIteratorState getEdge(int from, int to) {
        return GHUtility.getEdge(graph, from, to);
    }

    private Shortcut createShortcut(int from, int to, EdgeIteratorState edge1, EdgeIteratorState edge2, double weight) {
        return createShortcut(from, to, edge1, edge2, weight, true, false);
    }

    private Shortcut createShortcut(int from, int to, EdgeIteratorState edge1, EdgeIteratorState edge2, double weight, boolean fwd, boolean bwd) {
        return createShortcut(from, to, edge1.getEdge(), edge2.getEdge(), edge1.getEdge(), edge2.getEdge(), weight, fwd, bwd);
    }

    private Shortcut createShortcut(int from, int to, int firstOrigEdge, int lastOrigEdge, int skipEdge1, int skipEdge2, double weight) {
        return createShortcut(from, to, firstOrigEdge, lastOrigEdge, skipEdge1, skipEdge2, weight, true, false);
    }

    private Shortcut createShortcut(int from, int to, int firstOrigEdge, int lastOrigEdge, int skipEdge1, int skipEdge2, double weight, boolean fwd, boolean bwd) {
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
