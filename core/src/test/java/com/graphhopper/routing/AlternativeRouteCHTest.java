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

import com.graphhopper.routing.ch.NodeOrderingProvider;
import com.graphhopper.routing.ch.PrepareContractionHierarchies;
import com.graphhopper.routing.ev.BooleanEncodedValue;
import com.graphhopper.routing.ev.DecimalEncodedValue;
import com.graphhopper.routing.ev.DecimalEncodedValueImpl;
import com.graphhopper.routing.ev.SimpleBooleanEncodedValue;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.routing.weighting.FastestWeighting;
import com.graphhopper.storage.BaseGraph;
import com.graphhopper.storage.CHConfig;
import com.graphhopper.storage.RoutingCHGraph;
import com.graphhopper.storage.RoutingCHGraphImpl;
import com.graphhopper.util.GHUtility;
import com.graphhopper.core.util.PMap;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class AlternativeRouteCHTest {
    private final BooleanEncodedValue accessEnc = new SimpleBooleanEncodedValue("access", true);
    private final DecimalEncodedValue speedEnc = new DecimalEncodedValueImpl("speed", 5, 5, false);
    private final EncodingManager em = EncodingManager.start().add(accessEnc).add(speedEnc).build();

    public BaseGraph createTestGraph(EncodingManager tmpEM) {
        final BaseGraph graph = new BaseGraph.Builder(tmpEM).create();

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

        GHUtility.setSpeed(60, 60, accessEnc, speedEnc,
                graph.edge(5, 6).setDistance(10000),
                graph.edge(6, 3).setDistance(10000),
                graph.edge(3, 4).setDistance(10000),
                graph.edge(4, 10).setDistance(10000),
                graph.edge(6, 7).setDistance(10000),
                graph.edge(7, 8).setDistance(10000),
                graph.edge(8, 4).setDistance(10000),
                graph.edge(5, 1).setDistance(10000),
                graph.edge(1, 9).setDistance(10000),
                graph.edge(9, 2).setDistance(10000),
                graph.edge(2, 3).setDistance(10000),
                graph.edge(4, 11).setDistance(9000),
                graph.edge(11, 12).setDistance(9000),
                graph.edge(12, 10).setDistance(10000));

        graph.freeze();
        return graph;
    }

    private RoutingCHGraph prepareCH(BaseGraph graph) {
        // Carefully construct the CH so that the forward tree and the backward tree
        // meet on all four possible paths from 5 to 10
        // 5 ---> 11 will be reachable via shortcuts, as 11 is on shortest path 5 --> 12
        final int[] nodeOrdering = new int[]{0, 10, 12, 4, 3, 2, 5, 1, 6, 7, 8, 9, 11};
        CHConfig chConfig = CHConfig.nodeBased("p", new FastestWeighting(accessEnc, speedEnc));
        PrepareContractionHierarchies contractionHierarchies = PrepareContractionHierarchies.fromGraph(graph, chConfig);
        contractionHierarchies.useFixedNodeOrdering(NodeOrderingProvider.fromArray(nodeOrdering));
        PrepareContractionHierarchies.Result res = contractionHierarchies.doWork();
        return RoutingCHGraphImpl.fromGraph(graph, res.getCHStorage(), res.getCHConfig());
    }

    @Test
    public void testCalcAlternatives() {
        BaseGraph g = createTestGraph(em);
        PMap hints = new PMap();
        hints.putObject("alternative_route.max_weight_factor", 2.3);
        hints.putObject("alternative_route.local_optimality_factor", 0.5);
        hints.putObject("alternative_route.max_paths", 4);
        RoutingCHGraph routingCHGraph = prepareCH(g);
        AlternativeRouteCH altDijkstra = new AlternativeRouteCH(routingCHGraph, hints);
        List<AlternativeRouteCH.AlternativeInfo> pathInfos = altDijkstra.calcAlternatives(5, 10);
        assertEquals(3, pathInfos.size());
        // 4 -> 11 -> 12 is shorter than 4 -> 10 -> 12 (11 is an admissible via node), BUT
        // 4 -> 11 -> 12 -> 10 is too long compared to 4 -> 10
    }

    @Test
    public void testRelaxMaximumStretch() {
        BaseGraph g = createTestGraph(em);
        PMap hints = new PMap();
        hints.putObject("alternative_route.max_weight_factor", 4);
        hints.putObject("alternative_route.local_optimality_factor", 0.5);
        hints.putObject("alternative_route.max_paths", 4);
        RoutingCHGraph routingCHGraph = prepareCH(g);
        AlternativeRouteCH altDijkstra = new AlternativeRouteCH(routingCHGraph, hints);
        List<AlternativeRouteCH.AlternativeInfo> pathInfos = altDijkstra.calcAlternatives(5, 10);
        assertEquals(4, pathInfos.size());
        // 4 -> 11 -> 12 is shorter than 4 -> 10 -> 12 (11 is an admissible via node), AND
        // 4 -> 11 -> 12 -> 10 is not too long compared to 4 -> 10
    }

}
