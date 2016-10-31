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
package com.graphhopper.routing.template;

import com.graphhopper.GHRequest;
import com.graphhopper.GHResponse;
import com.graphhopper.routing.*;
import com.graphhopper.routing.util.*;
import com.graphhopper.routing.weighting.FastestWeighting;
import com.graphhopper.routing.weighting.Weighting;
import com.graphhopper.storage.Graph;
import com.graphhopper.storage.RAMDirectory;
import com.graphhopper.storage.index.LocationIndex;
import com.graphhopper.storage.index.LocationIndexTree;
import com.graphhopper.storage.index.QueryResult;
import com.graphhopper.util.Helper;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static com.graphhopper.util.Parameters.Algorithms.DIJKSTRA_BI;
import static org.junit.Assert.assertEquals;

/**
 * @author Peter Karich
 */
public class RoundTripRoutingTemplateTest {
    private final FlagEncoder carFE = new CarFlagEncoder();
    private final EncodingManager em = new EncodingManager(carFE);
    // TODO private final TraversalMode tMode = TraversalMode.EDGE_BASED_2DIR;
    private final TraversalMode tMode = TraversalMode.NODE_BASED;

    @Test
    public void testCalcRoundTrip() throws Exception {
        Weighting weighting = new FastestWeighting(carFE);
        Graph g = createTestGraph(true);

        RoundTripRoutingTemplate rTripRouting = new RoundTripRoutingTemplate(new GHRequest(), new GHResponse(), null, 1);

        LocationIndex locationIndex = new LocationIndexTree(g, new RAMDirectory()).prepareIndex();
        QueryResult qr4 = locationIndex.findClosest(0.05, 0.25, EdgeFilter.ALL_EDGES);
        assertEquals(4, qr4.getClosestNode());
        QueryResult qr5 = locationIndex.findClosest(0.00, 0.05, EdgeFilter.ALL_EDGES);
        assertEquals(5, qr5.getClosestNode());
        QueryResult qr6 = locationIndex.findClosest(0.00, 0.10, EdgeFilter.ALL_EDGES);
        assertEquals(6, qr6.getClosestNode());

        QueryGraph qGraph = new QueryGraph(g);
        qGraph.lookup(qr4, qr5);
        rTripRouting.setQueryResults(Arrays.asList(qr5, qr4, qr5));
        List<Path> paths = rTripRouting.calcPaths(qGraph, new RoutingAlgorithmFactorySimple(), new AlgorithmOptions(DIJKSTRA_BI, weighting, tMode));
        assertEquals(2, paths.size());
        assertEquals(Helper.createTList(5, 6, 3, 4), paths.get(0).calcNodes());
        assertEquals(Helper.createTList(4, 8, 7, 6, 5), paths.get(1).calcNodes());

        qGraph = new QueryGraph(g);
        qGraph.lookup(qr4, qr6);
        rTripRouting.setQueryResults(Arrays.asList(qr6, qr4, qr6));
        paths = rTripRouting.calcPaths(qGraph, new RoutingAlgorithmFactorySimple(), new AlgorithmOptions(DIJKSTRA_BI, weighting, tMode));
        assertEquals(2, paths.size());
        assertEquals(Helper.createTList(6, 3, 4), paths.get(0).calcNodes());
        assertEquals(Helper.createTList(4, 8, 7, 6), paths.get(1).calcNodes());
    }

    private Graph createTestGraph(boolean fullGraph) {
        return new AlternativeRouteTest(tMode).createTestGraph(fullGraph, em);
    }
}
