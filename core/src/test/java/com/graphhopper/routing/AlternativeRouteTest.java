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
import com.graphhopper.routing.AlternativeRoute.AlternativeBidirSearch;
import com.graphhopper.routing.util.CarFlagEncoder;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.routing.util.FlagEncoder;
import com.graphhopper.routing.util.TraversalMode;
import com.graphhopper.routing.weighting.DefaultTurnCostProvider;
import com.graphhopper.routing.weighting.FastestWeighting;
import com.graphhopper.routing.weighting.TurnCostProvider;
import com.graphhopper.routing.weighting.Weighting;
import com.graphhopper.storage.BaseGraph;
import com.graphhopper.storage.Graph;
import com.graphhopper.util.GHUtility;
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
        final CarFlagEncoder carFE;

        public Fixture(TraversalMode tMode) {
            this.traversalMode = tMode;
            carFE = new CarFlagEncoder();
            EncodingManager em = EncodingManager.create(carFE);
            graph = new BaseGraph.Builder(em).withTurnCosts(true).create();
            TurnCostProvider turnCostProvider = tMode.isEdgeBased()
                    ? new DefaultTurnCostProvider(carFE, graph.getTurnCostStorage())
                    : TurnCostProvider.NO_TURN_COST_PROVIDER;
            weighting = new FastestWeighting(carFE, turnCostProvider);
        }

        @Override
        public String toString() {
            return traversalMode.toString();
        }
    }

    private static class FixtureProvider implements ArgumentsProvider {
        @Override
        public Stream<? extends Arguments> provideArguments(ExtensionContext context) throws Exception {
            return Stream.of(
                    new Fixture(TraversalMode.NODE_BASED),
                    new Fixture(TraversalMode.EDGE_BASED)
            ).map(Arguments::of);
        }
    }

    public static void initTestGraph(Graph graph, FlagEncoder encoder) {
        /* 9
         _/\
         1  2-3-4-10
         \   /   \
         5--6-7---8
        
         */
        GHUtility.setSpeed(60, 60, encoder,
                graph.edge(1, 9).setDistance(1),
                graph.edge(9, 2).setDistance(1),
                graph.edge(2, 3).setDistance(1),
                graph.edge(3, 4).setDistance(1),
                graph.edge(4, 10).setDistance(1),
                graph.edge(5, 6).setDistance(1),
                graph.edge(6, 7).setDistance(1),
                graph.edge(7, 8).setDistance(1),
                graph.edge(1, 5).setDistance(2),
                graph.edge(6, 3).setDistance(1),
                graph.edge(4, 8).setDistance(1));

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
        initTestGraph(f.graph, f.carFE);
        AlternativeRoute altDijkstra = new AlternativeRoute(f.graph, f.weighting, f.traversalMode);
        altDijkstra.setMaxShareFactor(0.5);
        altDijkstra.setMaxWeightFactor(2);
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
        assertEquals(1667.9, secondAlt.getWeight(), .1);
    }

    @ParameterizedTest
    @ArgumentsSource(FixtureProvider.class)
    public void testCalcAlternatives2(Fixture f) {
        initTestGraph(f.graph, f.carFE);
        AlternativeRoute altDijkstra = new AlternativeRoute(f.graph, f.weighting, f.traversalMode);
        altDijkstra.setMaxPaths(3);
        altDijkstra.setMaxShareFactor(0.7);
        altDijkstra.setMinPlateauFactor(0.15);
        altDijkstra.setMaxWeightFactor(2);
        // edge based traversal requires a bit more exploration than the default of 1
        altDijkstra.setMaxExplorationFactor(1.2);

        List<AlternativeRoute.AlternativeInfo> pathInfos = altDijkstra.calcAlternatives(5, 4);
        checkAlternatives(pathInfos);
        assertEquals(3, pathInfos.size());

        // result is sorted based on the plateau to full weight ratio
        assertEquals(IntArrayList.from(5, 6, 3, 4), pathInfos.get(0).getPath().calcNodes());
        assertEquals(IntArrayList.from(5, 6, 7, 8, 4), pathInfos.get(1).getPath().calcNodes());
        assertEquals(IntArrayList.from(5, 1, 9, 2, 3, 4), pathInfos.get(2).getPath().calcNodes());
        assertEquals(2416.0, pathInfos.get(2).getPath().getWeight(), .1);
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
    public void testDisconnectedAreas(Fixture p) {
        initTestGraph(p.graph, p.carFE);

        // one single disconnected node
        updateDistancesFor(p.graph, 20, 0.00, -0.01);

        AlternativeBidirSearch altDijkstra = new AlternativeBidirSearch(p.graph, p.weighting, p.traversalMode, 1);
        Path path = altDijkstra.calcPath(1, 20);
        assertFalse(path.isFound());

        // make sure not the full graph is traversed!
        assertEquals(3, altDijkstra.getVisitedNodes());
    }
}
