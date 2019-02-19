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
import com.graphhopper.routing.weighting.FastestWeighting;
import com.graphhopper.routing.weighting.Weighting;
import com.graphhopper.storage.Graph;
import com.graphhopper.storage.GraphExtension;
import com.graphhopper.storage.GraphHopperStorage;
import com.graphhopper.storage.RAMDirectory;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import static com.graphhopper.routing.AbstractRoutingAlgorithmTester.updateDistancesFor;
import static org.junit.Assert.*;

@RunWith(Parameterized.class)
public class AlternativeRouteTest {
    private final FlagEncoder carFE = new CarFlagEncoder();
    private final EncodingManager em = EncodingManager.create(carFE);
    private final TraversalMode traversalMode;

    public AlternativeRouteTest(TraversalMode tMode) {
        this.traversalMode = tMode;
    }

    /**
     * Runs the same test with each of the supported traversal modes
     */
    @Parameterized.Parameters(name = "{0}")
    public static Collection<Object[]> configs() {
        return Arrays.asList(new Object[][]{
                {TraversalMode.NODE_BASED},
                {TraversalMode.EDGE_BASED_2DIR}
        });
    }

    public GraphHopperStorage createTestGraph(boolean fullGraph, EncodingManager tmpEM) {
        GraphHopperStorage graph = new GraphHopperStorage(new RAMDirectory(), tmpEM, false, new GraphExtension.NoOpExtension());
        graph.create(1000);

        /* 9
         _/\
         1  2-3-4-10
         \   /   \
         5--6-7---8
        
         */
        graph.edge(1, 9, 1, true);
        graph.edge(9, 2, 1, true);
        if (fullGraph)
            graph.edge(2, 3, 1, true);
        graph.edge(3, 4, 1, true);
        graph.edge(4, 10, 1, true);

        graph.edge(5, 6, 1, true);

        graph.edge(6, 7, 1, true);
        graph.edge(7, 8, 1, true);

        if (fullGraph)
            graph.edge(1, 5, 2, true);
        graph.edge(6, 3, 1, true);
        graph.edge(4, 8, 1, true);

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
        return graph;
    }

    @Test
    public void testCalcAlternatives() throws Exception {
        Weighting weighting = new FastestWeighting(carFE);
        GraphHopperStorage g = createTestGraph(true, em);
        AlternativeRoute altDijkstra = new AlternativeRoute(g, weighting, traversalMode);
        altDijkstra.setMaxShareFactor(0.5);
        altDijkstra.setMaxWeightFactor(2);
        List<AlternativeRoute.AlternativeInfo> pathInfos = altDijkstra.calcAlternatives(5, 4);
        checkAlternatives(pathInfos);
        assertEquals(2, pathInfos.size());

        DijkstraBidirectionRef dijkstra = new DijkstraBidirectionRef(g, weighting, traversalMode);
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

    @Test
    public void testCalcAlternatives2() throws Exception {
        Weighting weighting = new FastestWeighting(carFE);
        Graph g = createTestGraph(true, em);
        AlternativeRoute altDijkstra = new AlternativeRoute(g, weighting, traversalMode);
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

    void checkAlternatives(List<AlternativeRoute.AlternativeInfo> alternativeInfos) {
        assertFalse("alternativeInfos should contain alternatives", alternativeInfos.isEmpty());
        AlternativeRoute.AlternativeInfo bestInfo = alternativeInfos.get(0);
        for (int i = 1; i < alternativeInfos.size(); i++) {
            AlternativeRoute.AlternativeInfo a = alternativeInfos.get(i);
            if (a.getPath().getWeight() < bestInfo.getPath().getWeight())
                assertTrue("alternative is not longer -> " + a + " vs " + bestInfo, false);

            if (a.getShareWeight() > bestInfo.getPath().getWeight()
                    || a.getShareWeight() > a.getPath().getWeight())
                assertTrue("share or sortby incorrect -> " + a + " vs " + bestInfo, false);
        }
    }

    @Test
    public void testDisconnectedAreas() {
        Graph g = createTestGraph(true, em);

        // one single disconnected node
        updateDistancesFor(g, 20, 0.00, -0.01);

        Weighting weighting = new FastestWeighting(carFE);
        AlternativeBidirSearch altDijkstra = new AlternativeBidirSearch(g, weighting, traversalMode, 1);
        Path path = altDijkstra.calcPath(1, 20);
        assertFalse(path.isFound());

        // make sure not the full graph is traversed!
        assertEquals(3, altDijkstra.getVisitedNodes());
    }
}
