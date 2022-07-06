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
import com.graphhopper.routing.ch.PrepareContractionHierarchies;
import com.graphhopper.routing.ev.*;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.routing.weighting.DefaultTurnCostProvider;
import com.graphhopper.routing.weighting.FastestWeighting;
import com.graphhopper.routing.weighting.TurnCostProvider;
import com.graphhopper.storage.*;
import com.graphhopper.util.EdgeIteratorState;
import com.graphhopper.util.GHUtility;
import com.graphhopper.util.PMap;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.graphhopper.util.EdgeIterator.ANY_EDGE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class AlternativeRouteEdgeCHTest {
    private final BooleanEncodedValue accessEnc = new SimpleBooleanEncodedValue("access", true);
    private final DecimalEncodedValue speedEnc = new DecimalEncodedValueImpl("speed", 5, 5, false);
    private final DecimalEncodedValue turnCostEnc = TurnCost.create("car", 1);
    private final EncodingManager em = EncodingManager.start().add(accessEnc).add(speedEnc).addTurnCostEncodedValue(turnCostEnc).build();

    public BaseGraph createTestGraph(EncodingManager tmpEM) {
        final BaseGraph graph = new BaseGraph.Builder(tmpEM).withTurnCosts(true).create();

        /*

           9      11
          /\     /  \
         1  2-3-4-10-12
         \   /   \
         5--6-7---8

         */

        // Make all edges the length of T, the distance around v than an s->v->t path
        // has to be locally-shortest to be considered.
        // So we get all three alternatives.

        GHUtility.setSpeed(60, true, true, accessEnc, speedEnc, graph.edge(5, 6).setDistance(10000));
        EdgeIteratorState e6_3 = GHUtility.setSpeed(60, true, true, accessEnc, speedEnc, graph.edge(6, 3).setDistance(10000));
        EdgeIteratorState e3_4 = GHUtility.setSpeed(60, true, true, accessEnc, speedEnc, graph.edge(3, 4).setDistance(10000));
        GHUtility.setSpeed(60, true, true, accessEnc, speedEnc, graph.edge(4, 10).setDistance(10000));

        GHUtility.setSpeed(60, true, true, accessEnc, speedEnc, graph.edge(6, 7).setDistance(10000));
        GHUtility.setSpeed(60, true, true, accessEnc, speedEnc, graph.edge(7, 8).setDistance(10000));
        GHUtility.setSpeed(60, true, true, accessEnc, speedEnc, graph.edge(8, 4).setDistance(10000));

        GHUtility.setSpeed(60, true, true, accessEnc, speedEnc, graph.edge(5, 1).setDistance(10000));
        GHUtility.setSpeed(60, true, true, accessEnc, speedEnc, graph.edge(1, 9).setDistance(10000));
        GHUtility.setSpeed(60, true, true, accessEnc, speedEnc, graph.edge(9, 2).setDistance(10000));
        GHUtility.setSpeed(60, true, true, accessEnc, speedEnc, graph.edge(2, 3).setDistance(10000));

        EdgeIteratorState e4_11 = GHUtility.setSpeed(60, true, true, accessEnc, speedEnc, graph.edge(4, 11).setDistance(9000));
        GHUtility.setSpeed(60, true, true, accessEnc, speedEnc, graph.edge(11, 12).setDistance(9000));
        GHUtility.setSpeed(60, true, true, accessEnc, speedEnc, graph.edge(12, 10).setDistance(10000));

        TurnCostStorage turnCostStorage = graph.getTurnCostStorage();
        turnCostStorage.set(turnCostEnc, e3_4.getEdge(), 4, e4_11.getEdge(), Double.POSITIVE_INFINITY);
        turnCostStorage.set(turnCostEnc, e6_3.getEdge(), 3, e3_4.getEdge(), Double.POSITIVE_INFINITY);

        graph.freeze();
        return graph;
    }

    private RoutingCHGraph prepareCH(BaseGraph graph) {
        TurnCostProvider turnCostProvider = new DefaultTurnCostProvider(turnCostEnc, graph.getTurnCostStorage());
        CHConfig chConfig = CHConfig.edgeBased("profile", new FastestWeighting(accessEnc, speedEnc, turnCostProvider));
        PrepareContractionHierarchies contractionHierarchies = PrepareContractionHierarchies.fromGraph(graph, chConfig);
        PrepareContractionHierarchies.Result res = contractionHierarchies.doWork();
        return RoutingCHGraphImpl.fromGraph(graph, res.getCHStorage(), res.getCHConfig());
    }

    @Test
    public void testAssumptions() {
        BaseGraph g = createTestGraph(em);
        TurnCostProvider turnCostProvider = new DefaultTurnCostProvider(turnCostEnc, g.getTurnCostStorage());
        CHConfig chConfig = CHConfig.edgeBased("profile", new FastestWeighting(accessEnc, speedEnc, turnCostProvider));
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

}
