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

import com.graphhopper.routing.ch.PrepareContractionHierarchies;
import com.graphhopper.routing.ev.DecimalEncodedValue;
import com.graphhopper.routing.ev.TurnCost;
import com.graphhopper.routing.util.CarFlagEncoder;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.routing.util.FlagEncoder;
import com.graphhopper.routing.weighting.DefaultTurnCostProvider;
import com.graphhopper.routing.weighting.FastestWeighting;
import com.graphhopper.routing.weighting.TurnCostProvider;
import com.graphhopper.storage.*;
import com.graphhopper.util.EdgeIteratorState;
import com.graphhopper.util.GHUtility;
import com.graphhopper.util.PMap;
import org.junit.Test;

import java.util.List;

import static com.graphhopper.util.EdgeIterator.ANY_EDGE;
import static org.junit.Assert.*;

public class AlternativeRouteEdgeCHTest {
    private final FlagEncoder carFE = new CarFlagEncoder(new PMap().putObject("turn_costs", true));
    private final EncodingManager em = EncodingManager.create(carFE);

    public GraphHopperStorage createTestGraph(EncodingManager tmpEM) {
        final GraphHopperStorage graph = new GraphHopperStorage(new RAMDirectory(), tmpEM, false, true);
        TurnCostProvider turnCostProvider = new DefaultTurnCostProvider(carFE, graph.getTurnCostStorage());
        CHConfig chConfig = CHConfig.edgeBased("profile", new FastestWeighting(carFE, turnCostProvider));
        graph.addCHGraph(chConfig);
        graph.create(1000);

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

        graph.edge(5, 6, 10000, true);
        EdgeIteratorState e6_3 = graph.edge(6, 3, 10000, true);
        EdgeIteratorState e3_4 = graph.edge(3, 4, 10000, true);
        graph.edge(4, 10, 10000, true);

        graph.edge(6, 7, 10000, true);
        graph.edge(7, 8, 10000, true);
        graph.edge(8, 4, 10000, true);

        graph.edge(5, 1, 10000, true);
        graph.edge(1, 9, 10000, true);
        graph.edge(9, 2, 10000, true);
        graph.edge(2, 3, 10000, true);

        EdgeIteratorState e4_11 = graph.edge(4, 11, 9000, true);
        graph.edge(11, 12, 9000, true);
        graph.edge(12, 10, 10000, true);

        TurnCostStorage turnCostStorage = graph.getTurnCostStorage();
        DecimalEncodedValue carTurnCost = em.getDecimalEncodedValue(TurnCost.key(carFE.toString()));
        turnCostStorage.set(carTurnCost, e3_4.getEdge(), 4, e4_11.getEdge(), Double.POSITIVE_INFINITY);
        turnCostStorage.set(carTurnCost, e6_3.getEdge(), 3, e3_4.getEdge(), Double.POSITIVE_INFINITY);

        graph.freeze();

        PrepareContractionHierarchies contractionHierarchies = PrepareContractionHierarchies.fromGraphHopperStorage(graph, chConfig);
        contractionHierarchies.doWork();
        return graph;
    }


    @Test
    public void testAssumptions() {
        GraphHopperStorage g = createTestGraph(em);
        TurnCostProvider turnCostProvider = new DefaultTurnCostProvider(carFE, g.getTurnCostStorage());
        FastestWeighting weighting = new FastestWeighting(carFE, turnCostProvider);
        DijkstraBidirectionEdgeCHNoSOD router = new DijkstraBidirectionEdgeCHNoSOD(new RoutingCHGraphImpl(g.getCHGraph(), weighting));
        Path path = router.calcPath(5, 10);
        assertTrue(path.isFound());
        assertArrayEquals(new int[]{5, 6, 7, 8, 4, 10}, path.calcNodes().toArray());
        assertEquals(50000.0, path.getDistance(), 0.0);
        // 6 -> 3 -> 4 is forbidden

        router = new DijkstraBidirectionEdgeCHNoSOD(new RoutingCHGraphImpl(g.getCHGraph(), weighting));
        path = router.calcPath(5, 3, ANY_EDGE, GHUtility.getEdge(g, 2, 3).getEdge());
        assertArrayEquals(new int[]{5, 1, 9, 2, 3}, path.calcNodes().toArray());
        assertEquals(40000.0, path.getDistance(), 0.0);
        // We can specifically route to the desired in-edge at node 3
        // which does not lie upstream of the turn-restriction.
    }

    @Test
    public void testCalcAlternatives() {
        GraphHopperStorage g = createTestGraph(em);
        TurnCostProvider turnCostProvider = new DefaultTurnCostProvider(carFE, g.getTurnCostStorage());
        FastestWeighting weighting = new FastestWeighting(carFE, turnCostProvider);
        PMap hints = new PMap();
        hints.putObject("alternative_route.max_weight_factor", 4);
        hints.putObject("alternative_route.local_optimality_factor", 0.5);
        hints.putObject("alternative_route.max_paths", 4);
        AlternativeRouteEdgeCH altDijkstra = new AlternativeRouteEdgeCH(new RoutingCHGraphImpl(g.getCHGraph(), weighting), hints);
        List<AlternativeRouteEdgeCH.AlternativeInfo> pathInfos = altDijkstra.calcAlternatives(5, 10);
        assertEquals(2, pathInfos.size());
        assertArrayEquals(new int[]{5, 6, 7, 8, 4, 10}, pathInfos.get(0).path.calcNodes().toArray());
        assertArrayEquals(new int[]{5, 1, 9, 2, 3, 4, 10}, pathInfos.get(1).path.calcNodes().toArray());
        // 3 -> 4 -> 11 is forbidden
        // 6 -> 3 -> 4 is forbidden
    }

    @Test
    public void testCalcOtherAlternatives() {
        GraphHopperStorage g = createTestGraph(em);
        TurnCostProvider turnCostProvider = new DefaultTurnCostProvider(carFE, g.getTurnCostStorage());
        FastestWeighting weighting = new FastestWeighting(carFE, turnCostProvider);
        PMap hints = new PMap();
        hints.putObject("alternative_route.max_weight_factor", 4);
        hints.putObject("alternative_route.local_optimality_factor", 0.5);
        hints.putObject("alternative_route.max_paths", 4);
        AlternativeRouteEdgeCH altDijkstra = new AlternativeRouteEdgeCH(new RoutingCHGraphImpl(g.getCHGraph(), weighting), hints);
        List<AlternativeRouteEdgeCH.AlternativeInfo> pathInfos = altDijkstra.calcAlternatives(10, 5);
        assertEquals(2, pathInfos.size());
        assertArrayEquals(new int[]{10, 4, 3, 6, 5}, pathInfos.get(0).path.calcNodes().toArray());
        assertArrayEquals(new int[]{10, 4, 3, 2, 9, 1, 5}, pathInfos.get(1).path.calcNodes().toArray());
        // The shortest path works (no restrictions on the way back
    }

}
