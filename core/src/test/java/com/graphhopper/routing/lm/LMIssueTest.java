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

package com.graphhopper.routing.lm;

import com.graphhopper.routing.*;
import com.graphhopper.routing.ev.*;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.routing.weighting.FastestWeighting;
import com.graphhopper.routing.weighting.Weighting;
import com.graphhopper.storage.BaseGraph;
import com.graphhopper.storage.Directory;
import com.graphhopper.storage.NodeAccess;
import com.graphhopper.storage.RAMDirectory;
import com.graphhopper.util.GHUtility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static com.graphhopper.routing.util.TraversalMode.NODE_BASED;
import static com.graphhopper.core.util.Parameters.Algorithms.ASTAR;
import static com.graphhopper.core.util.Parameters.Algorithms.ASTAR_BI;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class LMIssueTest {
    private Directory dir;
    private BaseGraph graph;
    private BooleanEncodedValue accessEnc;
    private DecimalEncodedValue speedEnc;
    private Weighting weighting;
    private LandmarkStorage lm;
    private EncodingManager encodingManager;

    private enum Algo {
        DIJKSTRA,
        ASTAR_BIDIR,
        ASTAR_UNIDIR,
        LM_BIDIR,
        LM_UNIDIR,
        PERFECT_ASTAR
    }

    @BeforeEach
    public void init() {
        dir = new RAMDirectory();
        accessEnc = new SimpleBooleanEncodedValue("access", true);
        speedEnc = new DecimalEncodedValueImpl("speed", 5, 5, false);
        DecimalEncodedValue turnCostEnc = TurnCost.create("car", 1);
        encodingManager = new EncodingManager.Builder().add(accessEnc).add(speedEnc).addTurnCostEncodedValue(turnCostEnc).add(Subnetwork.create("car")).build();
        graph = new BaseGraph.Builder(encodingManager)
                .withTurnCosts(true)
                .setDir(dir)
                .create();
        weighting = new FastestWeighting(accessEnc, speedEnc);
    }

    private void preProcessGraph() {
        graph.freeze();
        PrepareLandmarks prepare = new PrepareLandmarks(dir, graph, encodingManager, new LMConfig("car", weighting), 16);
        prepare.setMaximumWeight(10000);
        prepare.doWork();
        lm = prepare.getLandmarkStorage();
    }

    private RoutingAlgorithm createAlgo(Algo algo) {
        switch (algo) {
            case DIJKSTRA:
                return new Dijkstra(graph, weighting, NODE_BASED);
            case ASTAR_UNIDIR:
                return new AStar(graph, weighting, NODE_BASED);
            case ASTAR_BIDIR:
                return new AStarBidirection(graph, weighting, NODE_BASED);
            case LM_BIDIR:
                return new LMRoutingAlgorithmFactory(lm).createAlgo(graph, weighting, new AlgorithmOptions().setAlgorithm(ASTAR_BI).setTraversalMode(NODE_BASED));
            case LM_UNIDIR:
                return new LMRoutingAlgorithmFactory(lm).createAlgo(graph, weighting, new AlgorithmOptions().setAlgorithm(ASTAR).setTraversalMode(NODE_BASED));
            case PERFECT_ASTAR:
                AStarBidirection perfectastarbi = new AStarBidirection(graph, weighting, NODE_BASED);
                perfectastarbi.setApproximation(new PerfectApproximator(graph, weighting, NODE_BASED, false));
                return perfectastarbi;
            default:
                throw new IllegalArgumentException("unknown algo " + algo);
        }
    }

    @ParameterizedTest
    @EnumSource
    public void lm_problem_to_node_of_fallback_approximator(Algo algo) {
        // Before #1745 this test used to fail for LM, because when the distance was approximated for the start node 0
        // the LMApproximator used the fall back approximator for which the to node was never set. This in turn meant
        // that the to coordinates were zero and a way too large approximation was returned.
        // Eventually the best path was not updated correctly because the spt entry of the fwd search already had a way
        // too large weight.

        //   ---<---
        //   |     |
        //   | 4   |
        //   |/  \ 0
        //   1   | |
        //     \ | |
        //       3 |
        // 2 --<----
        NodeAccess na = graph.getNodeAccess();
        na.setNode(0, 49.405150, 9.709054);
        na.setNode(1, 49.403705, 9.700517);
        na.setNode(2, 49.400112, 9.700209);
        na.setNode(3, 49.403009, 9.708364);
        na.setNode(4, 49.409021, 9.703622);
        // 30s
        GHUtility.setSpeed(60, true, true, accessEnc, speedEnc, graph.edge(4, 3).setDistance(1000)).set(speedEnc, 120);
        GHUtility.setSpeed(60, true, false, accessEnc, speedEnc, graph.edge(0, 2).setDistance(1000)).set(speedEnc, 120);
        // 360s
        GHUtility.setSpeed(60, true, true, accessEnc, speedEnc, graph.edge(1, 3).setDistance(1000)).set(speedEnc, 10);
        // 80s
        GHUtility.setSpeed(60, true, false, accessEnc, speedEnc, graph.edge(0, 1).setDistance(1000)).set(speedEnc, 45);
        GHUtility.setSpeed(60, true, true, accessEnc, speedEnc, graph.edge(1, 4).setDistance(1000)).set(speedEnc, 45);
        preProcessGraph();

        int source = 0;
        int target = 3;
        Path refPath = new DijkstraBidirectionRef(graph, weighting, NODE_BASED).calcPath(source, target);
        Path path = createAlgo(algo).calcPath(source, target);
        assertEquals(refPath.getWeight(), path.getWeight(), 1.e-2);
        assertEquals(refPath.getDistance(), path.getDistance(), 1.e-1);
        assertEquals(refPath.getTime(), path.getTime(), 50);
        assertEquals(refPath.calcNodes(), path.calcNodes());
    }

    @ParameterizedTest
    @EnumSource
    public void lm_issue2(Algo algo) {
        // Before #1745 This would fail for LM, because an underrun of 'delta' would not be treated correctly,
        // and the remaining weight would be over-approximated

        //                    ---
        //                  /     \
        // 0 - 1 - 5 - 6 - 9 - 4 - 0
        //          \     /
        //            ->-
        NodeAccess na = graph.getNodeAccess();
        na.setNode(0, 49.406987, 9.709767);
        na.setNode(1, 49.403612, 9.702953);
        na.setNode(2, 49.409755, 9.706517);
        na.setNode(3, 49.409021, 9.708649);
        na.setNode(4, 49.400674, 9.700906);
        na.setNode(5, 49.408735, 9.709486);
        na.setNode(6, 49.406402, 9.700937);
        na.setNode(7, 49.406965, 9.702660);
        na.setNode(8, 49.405227, 9.702863);
        na.setNode(9, 49.409411, 9.709085);
        GHUtility.setSpeed(112, true, true, accessEnc, speedEnc, graph.edge(0, 1).setDistance(623.197000));
        GHUtility.setSpeed(13, true, true, accessEnc, speedEnc, graph.edge(5, 1).setDistance(741.414000));
        GHUtility.setSpeed(35, true, true, accessEnc, speedEnc, graph.edge(9, 4).setDistance(1140.835000));
        GHUtility.setSpeed(18, true, true, accessEnc, speedEnc, graph.edge(5, 6).setDistance(670.689000));
        GHUtility.setSpeed(88, true, false, accessEnc, speedEnc, graph.edge(5, 9).setDistance(80.731000));
        GHUtility.setSpeed(82, true, true, accessEnc, speedEnc, graph.edge(0, 9).setDistance(273.948000));
        GHUtility.setSpeed(60, true, true, accessEnc, speedEnc, graph.edge(4, 0).setDistance(956.552000));
        preProcessGraph();

        int source = 5;
        int target = 4;
        Path refPath = new DijkstraBidirectionRef(graph, weighting, NODE_BASED).calcPath(source, target);
        Path path = createAlgo(algo).calcPath(source, target);
        assertEquals(refPath.getWeight(), path.getWeight(), 1.e-2);
        assertEquals(refPath.getDistance(), path.getDistance(), 1.e-1);
        assertEquals(refPath.getTime(), path.getTime(), 50);
        assertEquals(refPath.calcNodes(), path.calcNodes());
    }

}
