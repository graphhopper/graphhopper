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
import com.graphhopper.routing.ch.NodeOrderingProvider;
import com.graphhopper.routing.ch.PrepareContractionHierarchies;
import com.graphhopper.routing.ev.DecimalEncodedValue;
import com.graphhopper.routing.ev.DecimalEncodedValueImpl;
import com.graphhopper.routing.ev.TurnCost;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.routing.weighting.SpeedWeighting;
import com.graphhopper.storage.*;
import com.graphhopper.util.*;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.graphhopper.util.EdgeIterator.ANY_EDGE;
import static org.junit.jupiter.api.Assertions.*;

public class AlternativeRouteEdgeCHTest {
    private final DecimalEncodedValue speedEnc = new DecimalEncodedValueImpl("speed", 5, 5, false);
    private final DecimalEncodedValue turnCostEnc = TurnCost.create("car", 1);
    private final EncodingManager em = EncodingManager.start().add(speedEnc).addTurnCostEncodedValue(turnCostEnc).build();

    public BaseGraph createTestGraph(EncodingManager tmpEM) {
        final BaseGraph graph = new BaseGraph.Builder(tmpEM).withTurnCosts(true).create();

        /*

           9      11
          /\     /  \
         1  2-3-4-10-12
         \   /   \
         5--6-7---8

         */

        // Make all edges the length of T, the distance around v then an s->v->t path
        // has to be locally-shortest to be considered.
        // So we get all three alternatives.

        graph.edge(5, 6).setDistance(10000).set(speedEnc, 60);
        EdgeIteratorState e6_3 = graph.edge(6, 3).setDistance(10000).set(speedEnc, 60);
        EdgeIteratorState e3_4 = graph.edge(3, 4).setDistance(10000).set(speedEnc, 60);
        graph.edge(4, 10).setDistance(10000).set(speedEnc, 60);

        graph.edge(6, 7).setDistance(10000).set(speedEnc, 60);
        graph.edge(7, 8).setDistance(10000).set(speedEnc, 60);
        graph.edge(8, 4).setDistance(10000).set(speedEnc, 60);

        graph.edge(5, 1).setDistance(10000).set(speedEnc, 60);
        graph.edge(1, 9).setDistance(10000).set(speedEnc, 60);
        graph.edge(9, 2).setDistance(10000).set(speedEnc, 60);
        graph.edge(2, 3).setDistance(10000).set(speedEnc, 60);

        EdgeIteratorState e4_11 = graph.edge(4, 11).setDistance(9000).set(speedEnc, 60);
        graph.edge(11, 12).setDistance(9000).set(speedEnc, 60);
        graph.edge(12, 10).setDistance(10000).set(speedEnc, 60);

        TurnCostStorage turnCostStorage = graph.getTurnCostStorage();
        turnCostStorage.set(turnCostEnc, e3_4.getEdge(), 4, e4_11.getEdge(), Double.POSITIVE_INFINITY);
        turnCostStorage.set(turnCostEnc, e6_3.getEdge(), 3, e3_4.getEdge(), Double.POSITIVE_INFINITY);

        graph.freeze();
        return graph;
    }

    private RoutingCHGraph prepareCH(BaseGraph graph) {
        CHConfig chConfig = CHConfig.edgeBased("profile", new SpeedWeighting(speedEnc, turnCostEnc, graph.getTurnCostStorage(), Double.POSITIVE_INFINITY));
        PrepareContractionHierarchies contractionHierarchies = PrepareContractionHierarchies.fromGraph(graph, chConfig);
        PrepareContractionHierarchies.Result res = contractionHierarchies.doWork();
        return RoutingCHGraphImpl.fromGraph(graph, res.getCHStorage(), res.getCHConfig());
    }

    @Test
    public void testAssumptions() {
        BaseGraph g = createTestGraph(em);
        CHConfig chConfig = CHConfig.edgeBased("profile", new SpeedWeighting(speedEnc, turnCostEnc, g.getTurnCostStorage(), Double.POSITIVE_INFINITY));
        CHStorage chStorage = CHStorage.fromGraph(g, chConfig);
        RoutingCHGraph chGraph = RoutingCHGraphImpl.fromGraph(g, chStorage, chConfig);
        DijkstraBidirectionEdgeCHNoSOD router = new DijkstraBidirectionEdgeCHNoSOD(chGraph);
        Path path = router.calcPath(5, 10);
        assertTrue(path.isFound());
        assertEquals(IntArrayList.from(5, 6, 7, 8, 4, 10), path.calcNodes());
        assertEquals(50000.0, path.getDistance(), 0.0);
        // 6 -> 3 -> 4 is forbidden

        g = createTestGraph(em);
        RoutingCHGraph routingCHGraph = prepareCH(g);
        router = new DijkstraBidirectionEdgeCHNoSOD(routingCHGraph);
        path = router.calcPath(5, 3, ANY_EDGE, GHUtility.getEdge(g, 2, 3).getEdge());
        assertEquals(IntArrayList.from(5, 1, 9, 2, 3), path.calcNodes());
        assertEquals(40000.0, path.getDistance(), 0.0);
        // We can specifically route to the desired in-edge at node 3
        // which does not lie upstream of the turn-restriction.
    }

    @Test
    public void testCalcAlternatives() {
        BaseGraph g = createTestGraph(em);
        PMap hints = new PMap();
        hints.putObject("alternative_route.max_weight_factor", 4);
        hints.putObject("alternative_route.local_optimality_factor", 0.5);
        hints.putObject("alternative_route.max_paths", 4);
        RoutingCHGraph routingCHGraph = prepareCH(g);
        AlternativeRouteEdgeCH altDijkstra = new AlternativeRouteEdgeCH(routingCHGraph, hints);
        List<AlternativeRouteEdgeCH.AlternativeInfo> pathInfos = altDijkstra.calcAlternatives(5, 10);
        assertEquals(2, pathInfos.size());
        assertEquals(IntArrayList.from(5, 6, 7, 8, 4, 10), pathInfos.get(0).path.calcNodes());
        assertEquals(IntArrayList.from(5, 1, 9, 2, 3, 4, 10), pathInfos.get(1).path.calcNodes());
        // 3 -> 4 -> 11 is forbidden
        // 6 -> 3 -> 4 is forbidden
    }

    @Test
    public void testCalcOtherAlternatives() {
        BaseGraph g = createTestGraph(em);
        PMap hints = new PMap();
        hints.putObject("alternative_route.max_weight_factor", 4);
        hints.putObject("alternative_route.local_optimality_factor", 0.5);
        hints.putObject("alternative_route.max_paths", 4);
        RoutingCHGraph routingCHGraph = prepareCH(g);
        AlternativeRouteEdgeCH altDijkstra = new AlternativeRouteEdgeCH(routingCHGraph, hints);
        List<AlternativeRouteEdgeCH.AlternativeInfo> pathInfos = altDijkstra.calcAlternatives(10, 5);
        assertEquals(2, pathInfos.size());
        assertEquals(IntArrayList.from(10, 4, 3, 6, 5), pathInfos.get(0).path.calcNodes());
        assertEquals(IntArrayList.from(10, 12, 11, 4, 3, 6, 5), pathInfos.get(1).path.calcNodes());
        // The shortest path works (no restrictions on the way back
    }

    @Test
    void turnRestrictionAtConnectingNode() {
        final BaseGraph graph = new BaseGraph.Builder(em).withTurnCosts(true).create();
        TurnCostStorage tcs = graph.getTurnCostStorage();
        NodeAccess na = graph.getNodeAccess();
        na.setNode(3, 45.0, 10.0);
        na.setNode(2, 45.0, 10.1);
        na.setNode(1, 44.9, 10.1);
        // 3-2
        //  \|
        //   1
        graph.edge(2, 3).setDistance(500).set(speedEnc, 60); // edgeId=0
        graph.edge(2, 1).setDistance(1000).set(speedEnc, 60); // edgeId=1
        graph.edge(3, 1).setDistance(500).set(speedEnc, 60); // edgeId=2
        // turn restriction at node 3, which must be respected by our glued-together s->u->v->t path
        tcs.set(turnCostEnc, 0, 3, 2, Double.POSITIVE_INFINITY);
        graph.freeze();
        CHConfig chConfig = CHConfig.edgeBased("profile", new SpeedWeighting(speedEnc, turnCostEnc, graph.getTurnCostStorage(), Double.POSITIVE_INFINITY));
        PrepareContractionHierarchies contractionHierarchies = PrepareContractionHierarchies.fromGraph(graph, chConfig);
        contractionHierarchies.useFixedNodeOrdering(NodeOrderingProvider.fromArray(3, 0, 2, 1));
        PrepareContractionHierarchies.Result res = contractionHierarchies.doWork();
        RoutingCHGraph routingCHGraph = RoutingCHGraphImpl.fromGraph(graph, res.getCHStorage(), res.getCHConfig());
        final int s = 2;
        final int t = 1;
        DijkstraBidirectionEdgeCHNoSOD dijkstra = new DijkstraBidirectionEdgeCHNoSOD(routingCHGraph);
        Path singlePath = dijkstra.calcPath(s, t);
        PMap hints = new PMap();
        AlternativeRouteEdgeCH altDijkstra = new AlternativeRouteEdgeCH(routingCHGraph, hints);
        List<AlternativeRouteEdgeCH.AlternativeInfo> pathInfos = altDijkstra.calcAlternatives(s, t);
        AlternativeRouteEdgeCH.AlternativeInfo best = pathInfos.get(0);
        assertEquals(singlePath.getWeight(), best.path.getWeight());
        assertEquals(singlePath.calcNodes(), best.path.calcNodes());
        for (int j = 1; j < pathInfos.size(); j++) {
            assertTrue(pathInfos.get(j).path.getWeight() >= best.path.getWeight(), "alternatives must not have lower weight than best path");
            assertEquals(IntArrayList.from(s, t), pathInfos.get(j).path.calcNodes(), "alternatives must start/end at start/end node");
        }
    }

    @Test
    void distanceTimeAndWeight() {
        final BaseGraph graph = new BaseGraph.Builder(em).withTurnCosts(true).create();
        //   0-1-2-3---|
        //        \    |
        //         5-6-7-8
        graph.edge(0, 1).setDistance(500).set(speedEnc, 60);
        graph.edge(1, 2).setDistance(500).set(speedEnc, 60);
        graph.edge(2, 3).setDistance(500).set(speedEnc, 60);
        graph.edge(2, 5).setDistance(950).set(speedEnc, 60);
        graph.edge(3, 7).setDistance(1500).set(speedEnc, 60);
        graph.edge(5, 6).setDistance(500).set(speedEnc, 60);
        graph.edge(6, 7).setDistance(500).set(speedEnc, 60);
        graph.edge(7, 8).setDistance(500).set(speedEnc, 60);
        graph.freeze();
        CHConfig chConfig = CHConfig.edgeBased("profile", new SpeedWeighting(speedEnc, turnCostEnc, graph.getTurnCostStorage(), Double.POSITIVE_INFINITY));
        PrepareContractionHierarchies contractionHierarchies = PrepareContractionHierarchies.fromGraph(graph, chConfig);
        contractionHierarchies.useFixedNodeOrdering(NodeOrderingProvider.fromArray(7, 6, 0, 2, 4, 5, 1, 3, 8));
        PrepareContractionHierarchies.Result res = contractionHierarchies.doWork();
        RoutingCHGraph routingCHGraph = RoutingCHGraphImpl.fromGraph(graph, res.getCHStorage(), res.getCHConfig());
        final int s = 0;
        final int t = 8;
        PMap hints = new PMap();
        AlternativeRouteEdgeCH altDijkstra = new AlternativeRouteEdgeCH(routingCHGraph, hints);
        List<AlternativeRouteEdgeCH.AlternativeInfo> pathInfos = altDijkstra.calcAlternatives(s, t);
        AlternativeRouteEdgeCH.AlternativeInfo best = pathInfos.get(0);
        assertEquals(3450, best.path.getDistance());
        assertEquals(57.500, best.path.getWeight(), 1.e-3);
        assertEquals(57498, best.path.getTime(), 10);
        assertEquals(IntArrayList.from(0, 1, 2, 5, 6, 7, 8), best.path.calcNodes());
        assertTrue(pathInfos.size() > 1, "the graph, contraction order and alternative route algorithm must be such that " +
                "there is at least one alternative path, otherwise this test makes no sense");
        for (int j = 1; j < pathInfos.size(); j++) {
            Path alternative = pathInfos.get(j).path;
            assertEquals(3500, alternative.getDistance());
            assertEquals(58.3333, alternative.getWeight(), 1.e-3);
            assertEquals(58333, alternative.getTime(), 1);
            assertEquals(IntArrayList.from(0, 1, 2, 3, 7, 8), alternative.calcNodes());
        }
    }

}
