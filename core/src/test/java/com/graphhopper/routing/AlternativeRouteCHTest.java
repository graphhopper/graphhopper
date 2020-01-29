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
import com.graphhopper.routing.util.CarFlagEncoder;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.routing.util.FlagEncoder;
import com.graphhopper.routing.weighting.FastestWeighting;
import com.graphhopper.routing.weighting.Weighting;
import com.graphhopper.storage.CHProfile;
import com.graphhopper.storage.GraphHopperStorage;
import com.graphhopper.storage.RAMDirectory;
import com.graphhopper.storage.RoutingCHGraphImpl;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;

public class AlternativeRouteCHTest {
    private final FlagEncoder carFE = new CarFlagEncoder();
    private final EncodingManager em = EncodingManager.create(carFE);
    private final Weighting weighting = new FastestWeighting(carFE);

    public GraphHopperStorage createTestGraph(EncodingManager tmpEM) {
        final GraphHopperStorage graph = new GraphHopperStorage(new RAMDirectory(), tmpEM, false);
        CHProfile chProfile = CHProfile.nodeBased(new FastestWeighting(carFE));
        graph.addCHGraph(chProfile);
        graph.create(1000);

        /*

           9
          /\
         1  2-3-4-10
         \   /   \
         5--6-7---8
        
         */

        // Make all edges the length of T, the distance around v than an s->v->t path
        // has to be locally-shortest to be considered.
        // So we get all three alternatives.

        graph.edge(5, 6, AlternativeRouteCH.T, true);
        graph.edge(6, 3, AlternativeRouteCH.T, true);
        graph.edge(3, 4, AlternativeRouteCH.T, true);
        graph.edge(4, 10, AlternativeRouteCH.T, true);

        graph.edge(6, 7, AlternativeRouteCH.T, true);
        graph.edge(7, 8, AlternativeRouteCH.T, true);
        graph.edge(8, 4, AlternativeRouteCH.T, true);

        graph.edge(5, 1, AlternativeRouteCH.T, true);
        graph.edge(1, 9, AlternativeRouteCH.T, true);
        graph.edge(9, 2, AlternativeRouteCH.T, true);
        graph.edge(2, 3, AlternativeRouteCH.T, true);

        graph.freeze();

        // Carefully construct the CH so that the forward tree and the backward tree
        // meet on all three possible paths from 5 to 10
        final List<Integer> nodeOrdering = Arrays.asList(0, 10, 4, 3, 2, 5, 1, 6, 7, 8, 9);
        PrepareContractionHierarchies contractionHierarchies = PrepareContractionHierarchies.fromGraphHopperStorage(graph, chProfile);
        contractionHierarchies.useFixedNodeOrdering(new NodeOrderingProvider() {
            @Override
            public int getNodeIdForLevel(int level) {
                return nodeOrdering.get(level);
            }

            @Override
            public int getNumNodes() {
                return nodeOrdering.size();
            }
        });
        contractionHierarchies.doWork();
        return graph;
    }

    @Test
    public void testCalcAlternatives() {
        GraphHopperStorage g = createTestGraph(em);
        AlternativeRouteCH altDijkstra = new AlternativeRouteCH(new RoutingCHGraphImpl(g.getCHGraph(), weighting));
        altDijkstra.setMaxShareFactor(0.9);
        altDijkstra.setMaxWeightFactor(10);
        List<AlternativeRouteCH.AlternativeInfo> pathInfos = altDijkstra.calcAlternatives(5, 10);
        assertEquals(3, pathInfos.size());
    }

}
