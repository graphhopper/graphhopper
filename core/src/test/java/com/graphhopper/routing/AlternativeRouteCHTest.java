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
import com.graphhopper.routing.ch.CHWeighting;
import com.graphhopper.routing.ch.PrepareContractionHierarchies;
import com.graphhopper.routing.util.*;
import com.graphhopper.routing.weighting.FastestWeighting;
import com.graphhopper.routing.weighting.Weighting;
import com.graphhopper.storage.*;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static com.graphhopper.util.GHUtility.updateDistancesFor;
import static org.junit.Assert.*;

public class AlternativeRouteCHTest {
    private final FlagEncoder carFE = new CarFlagEncoder();
    private final EncodingManager em = EncodingManager.create(carFE);
    private final Weighting weighting = new FastestWeighting(carFE);

    public GraphHopperStorage createTestGraph(boolean fullGraph, EncodingManager tmpEM) {
        GraphHopperStorage graph = new GraphHopperStorage(new RAMDirectory(), tmpEM, false);
        CHProfile chProfile = CHProfile.nodeBased(new FastestWeighting(carFE));
        graph.addCHGraph(chProfile);
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
        graph.freeze();

        PrepareContractionHierarchies contractionHierarchies = PrepareContractionHierarchies.fromGraphHopperStorage(graph, chProfile);
        contractionHierarchies.doWork();
        return graph;
    }

    @Test
    public void testCalcAlternatives() {
        GraphHopperStorage g = createTestGraph(true, em);
        AlternativeRouteCH altDijkstra = new AlternativeRouteCH(g.getCHGraph(), new CHWeighting(weighting));
        altDijkstra.setEdgeFilter(new LevelEdgeFilter(g.getCHGraph()));
        altDijkstra.setMaxShareFactor(10);
        altDijkstra.setMaxWeightFactor(10);
        List<AlternativeRouteCH.AlternativeInfo> pathInfos = altDijkstra.calcAlternatives(5, 4);
        checkAlternatives(pathInfos);
        assertEquals(2, pathInfos.size());

        DijkstraBidirectionRef dijkstra = new DijkstraBidirectionRef(g, new CHWeighting(weighting), TraversalMode.NODE_BASED);
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
    public void testCalcAlternatives2() {
        GraphHopperStorage g = createTestGraph(true, em);
        AlternativeRouteCH altDijkstra = new AlternativeRouteCH(g.getCHGraph(), new CHWeighting(weighting));
        altDijkstra.setEdgeFilter(new LevelEdgeFilter(g.getCHGraph()));
        altDijkstra.setMaxShareFactor(10);
        altDijkstra.setMaxWeightFactor(10);

        List<AlternativeRouteCH.AlternativeInfo> pathInfos = altDijkstra.calcAlternatives(5, 4);
        checkAlternatives(pathInfos);
        assertEquals(3, pathInfos.size());

        // result is sorted based on the plateau to full weight ratio
        assertEquals(IntArrayList.from(5, 6, 3, 4), pathInfos.get(0).getPath().calcNodes());
        assertEquals(IntArrayList.from(5, 6, 7, 8, 4), pathInfos.get(1).getPath().calcNodes());
        assertEquals(IntArrayList.from(5, 1, 9, 2, 3, 4), pathInfos.get(2).getPath().calcNodes());
        assertEquals(2416.0, pathInfos.get(2).getPath().getWeight(), .1);
    }

    private void checkAlternatives(List<AlternativeRouteCH.AlternativeInfo> alternativeInfos) {
        assertFalse("alternativeInfos should contain alternatives", alternativeInfos.isEmpty());
        AlternativeRouteCH.AlternativeInfo bestInfo = alternativeInfos.get(0);
        for (int i = 1; i < alternativeInfos.size(); i++) {
            AlternativeRouteCH.AlternativeInfo a = alternativeInfos.get(i);
            if (a.getPath().getWeight() < bestInfo.getPath().getWeight())
                fail("alternative is not longer -> " + a + " vs " + bestInfo);

        }
    }

    @Test
    public void testDisconnectedAreas() {
        Graph g = createTestGraph(true, em);

        // one single disconnected node
        updateDistancesFor(g, 20, 0.00, -0.01);

        Weighting weighting = new FastestWeighting(carFE);
        AlternativeBidirSearch altDijkstra = new AlternativeBidirSearch(g, weighting, TraversalMode.NODE_BASED, 1);
        Path path = altDijkstra.calcPath(1, 20);
        assertFalse(path.isFound());

        // make sure not the full graph is traversed!
        assertEquals(3, altDijkstra.getVisitedNodes());
    }
}
