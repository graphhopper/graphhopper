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
import com.graphhopper.routing.ev.DecimalEncodedValue;
import com.graphhopper.routing.ev.DecimalEncodedValueImpl;
import com.graphhopper.routing.ev.TurnCost;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.routing.util.TraversalMode;
import com.graphhopper.routing.weighting.SpeedWeighting;
import com.graphhopper.routing.weighting.Weighting;
import com.graphhopper.storage.BaseGraph;
import com.graphhopper.storage.Graph;
import com.graphhopper.util.PMap;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.ArgumentsProvider;
import org.junit.jupiter.params.provider.ArgumentsSource;

import java.util.List;
import java.util.stream.Stream;

import static com.graphhopper.util.GHUtility.updateDistancesFor;
import static org.junit.jupiter.api.Assertions.*;

public class AlternativeRouteTest {

    private static final class Fixture {
        final Weighting weighting;
        final TraversalMode traversalMode;
        final BaseGraph graph;
        final DecimalEncodedValue speedEnc;
        final DecimalEncodedValue turnCostEnc;

        public Fixture(TraversalMode tMode) {
            this.traversalMode = tMode;
            speedEnc = new DecimalEncodedValueImpl("speed", 5, 5, true);
            turnCostEnc = TurnCost.create("car", 1);

            EncodingManager em = EncodingManager.start().add(speedEnc).add(turnCostEnc).build();
            graph = new BaseGraph.Builder(em).withTurnCosts(true).create();
            weighting = tMode.isEdgeBased()
                    ? new SpeedWeighting(speedEnc, turnCostEnc, graph.getTurnCostStorage(), Double.POSITIVE_INFINITY)
                    : new SpeedWeighting(speedEnc);
        }

        @Override
        public String toString() {
            return traversalMode.toString();
        }
    }

    private static class FixtureProvider implements ArgumentsProvider {
        @Override
        public Stream<? extends Arguments> provideArguments(ExtensionContext context) {
            return Stream.of(
                    new Fixture(TraversalMode.NODE_BASED),
                    new Fixture(TraversalMode.EDGE_BASED)
            ).map(Arguments::of);
        }
    }

    public static void initTestGraph(Graph graph, DecimalEncodedValue speedEnc) {
        /* 9
         _/\
         1  2-3-4-10
         \   /   \
         5--6-7---8
        
         */
        graph.edge(1, 9).setDistance(1).set(speedEnc, 60, 60);
        graph.edge(9, 2).setDistance(1).set(speedEnc, 60, 60);
        graph.edge(2, 3).setDistance(1).set(speedEnc, 60, 60);
        graph.edge(3, 4).setDistance(1).set(speedEnc, 60, 60);
        graph.edge(4, 10).setDistance(1).set(speedEnc, 60, 60);
        graph.edge(5, 6).setDistance(1).set(speedEnc, 60, 60);
        graph.edge(6, 7).setDistance(1).set(speedEnc, 60, 60);
        graph.edge(7, 8).setDistance(1).set(speedEnc, 60, 60);
        graph.edge(1, 5).setDistance(2).set(speedEnc, 60, 60);
        graph.edge(6, 3).setDistance(1).set(speedEnc, 60, 60);
        graph.edge(4, 8).setDistance(1).set(speedEnc, 60, 60);

        updateDistancesFor(graph, 5, 0.00, 0.05);
        updateDistancesFor(graph, 6, 0.00, 0.10);
        updateDistancesFor(graph, 7, 0.00, 0.15);
        updateDistancesFor(graph, 8, 0.00, 0.25);

        updateDistancesFor(graph, 1, 0.05, 0.00);
        updateDistancesFor(graph, 9, 0.10, 0.05);
        updateDistancesFor(graph, 2, 0.05, 0.10);
        updateDistancesFor(graph, 3, 0.05, 0.15);
        updateDistancesFor(graph, 4, 0.05, 0.25);
        updateDistancesFor(graph, 10, 0.05, 0.30);
    }

    @ParameterizedTest
    @ArgumentsSource(FixtureProvider.class)
    public void testCalcAlternatives(Fixture f) {
        initTestGraph(f.graph, f.speedEnc);
        PMap hints = new PMap().
                putObject("alternative_route.max_share_factor", 0.5).
                putObject("alternative_route.max_weight_factor", 2).
                putObject("alternative_route.max_exploration_factor", 1.3);
        AlternativeRoute altDijkstra = new AlternativeRoute(f.graph, f.weighting, f.traversalMode, hints);
        List<AlternativeRoute.AlternativeInfo> pathInfos = altDijkstra.calcAlternatives(5, 4);
        checkAlternatives(pathInfos);
        assertEquals(2, pathInfos.size());

        DijkstraBidirectionRef dijkstra = new DijkstraBidirectionRef(f.graph, f.weighting, f.traversalMode);
        Path bestPath = dijkstra.calcPath(5, 4);

        Path bestAlt = pathInfos.get(0).getPath();
        Path secondAlt = pathInfos.get(1).getPath();

        assertEquals(bestPath.calcNodes(), bestAlt.calcNodes());
        assertEquals(bestPath.getWeight(), bestAlt.getWeight(), 1e-3);

        assertEquals(IntArrayList.from(5, 6, 3, 4), bestAlt.calcNodes());

        // Note: here plateau is longer, even longer than optimum, but path is longer
        // so which alternative is better? longer plateau.weight with bigger path.weight or smaller path.weight with smaller plateau.weight
        // assertEquals(IntArrayList.from(5, 1, 9, 2, 3, 4), secondAlt.calcNodes());
        assertEquals(IntArrayList.from(5, 6, 7, 8, 4), secondAlt.calcNodes());
        assertEquals(463.3, secondAlt.getWeight(), .1);
    }

    @ParameterizedTest
    @ArgumentsSource(FixtureProvider.class)
    public void testCalcAlternatives2(Fixture f) {
        initTestGraph(f.graph, f.speedEnc);
        PMap hints = new PMap().putObject("alternative_route.max_paths", 3).
                putObject("alternative_route.max_share_factor", 0.7).
                putObject("alternative_route.min_plateau_factor", 0.15).
                putObject("alternative_route.max_weight_factor", 2).
                putObject("alternative_route.max_exploration_factor", 1.8);
        AlternativeRoute altDijkstra = new AlternativeRoute(f.graph, f.weighting, f.traversalMode, hints);
        List<AlternativeRoute.AlternativeInfo> pathInfos = altDijkstra.calcAlternatives(5, 4);
        checkAlternatives(pathInfos);
        assertEquals(3, pathInfos.size());

        // result is sorted based on the plateau to full weight ratio
        assertEquals(IntArrayList.from(5, 6, 3, 4), pathInfos.get(0).getPath().calcNodes());
        assertEquals(IntArrayList.from(5, 6, 7, 8, 4), pathInfos.get(1).getPath().calcNodes());
        assertEquals(IntArrayList.from(5, 1, 9, 2, 3, 4), pathInfos.get(2).getPath().calcNodes());
        assertEquals(671.1, pathInfos.get(2).getPath().getWeight(), .1);
    }

    private void checkAlternatives(List<AlternativeRoute.AlternativeInfo> alternativeInfos) {
        assertFalse(alternativeInfos.isEmpty(), "alternativeInfos should contain alternatives");
        AlternativeRoute.AlternativeInfo bestInfo = alternativeInfos.get(0);
        for (int i = 1; i < alternativeInfos.size(); i++) {
            AlternativeRoute.AlternativeInfo a = alternativeInfos.get(i);
            if (a.getPath().getWeight() < bestInfo.getPath().getWeight())
                fail("alternative is not longer -> " + a + " vs " + bestInfo);

            if (a.getShareWeight() > bestInfo.getPath().getWeight()
                    || a.getShareWeight() > a.getPath().getWeight())
                fail("share or sortby incorrect -> " + a + " vs " + bestInfo);
        }
    }

    @ParameterizedTest
    @ArgumentsSource(FixtureProvider.class)
    public void testDisconnectedAreas(Fixture f) {
        initTestGraph(f.graph, f.speedEnc);

        // one single disconnected node
        updateDistancesFor(f.graph, 20, 0.00, -0.01);

        PMap hints = new PMap().putObject("alternative_route.max_exploration_factor", 1);
        AlternativeRoute altDijkstra = new AlternativeRoute(f.graph, f.weighting, f.traversalMode, hints);
        Path path = altDijkstra.calcPath(1, 20);
        assertFalse(path.isFound());

        // make sure not the full graph is traversed!
        assertEquals(3, altDijkstra.getVisitedNodes());
    }
}
