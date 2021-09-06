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

import com.carrotsearch.hppc.IntArrayList;
import com.graphhopper.GHRequest;
import com.graphhopper.GHResponse;
import com.graphhopper.routing.AlgorithmOptions;
import com.graphhopper.routing.AlternativeRouteTest;
import com.graphhopper.routing.Path;
import com.graphhopper.routing.RoutingAlgorithmFactorySimple;
import com.graphhopper.routing.querygraph.QueryGraph;
import com.graphhopper.routing.util.*;
import com.graphhopper.routing.weighting.FastestWeighting;
import com.graphhopper.routing.weighting.Weighting;
import com.graphhopper.storage.Graph;
import com.graphhopper.storage.GraphBuilder;
import com.graphhopper.storage.GraphHopperStorage;
import com.graphhopper.storage.RAMDirectory;
import com.graphhopper.storage.index.LocationIndex;
import com.graphhopper.storage.index.LocationIndexTree;
import com.graphhopper.storage.index.QueryResult;
import com.graphhopper.util.Parameters;
import com.graphhopper.util.shapes.GHPoint;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static com.graphhopper.util.GHUtility.updateDistancesFor;
import static com.graphhopper.util.Parameters.Algorithms.DIJKSTRA_BI;
import static org.junit.Assert.assertEquals;

/**
 * @author Peter Karich
 */
public class RoundTripRoutingTemplateTest {
    private final FlagEncoder carFE = new CarFlagEncoder();
    private final EncodingManager em = EncodingManager.create(carFE);
    private final Weighting fastestWeighting = new FastestWeighting(carFE);
    // TODO private final TraversalMode tMode = TraversalMode.EDGE_BASED;
    private final TraversalMode tMode = TraversalMode.NODE_BASED;
    private final GHPoint ghPoint1 = new GHPoint(0, 0);
    private final GHPoint ghPoint2 = new GHPoint(1, 1);

    @Test(expected = IllegalArgumentException.class)
    public void lookup_throwsIfNumberOfGivenPointsNotOne() {
        RoundTripRoutingTemplate routingTemplate = new RoundTripRoutingTemplate(
                new GHRequest(Collections.singletonList(ghPoint1)), new GHResponse(), null, em, fastestWeighting, 1);
        routingTemplate.lookup(Arrays.asList(ghPoint1, ghPoint2));
    }

    @Test(expected = IllegalArgumentException.class)
    public void lookup_throwsIfNumberOfPointsInRequestNotOne() {
        RoundTripRoutingTemplate routingTemplate = new RoundTripRoutingTemplate(
                new GHRequest(Arrays.asList(ghPoint1, ghPoint2)), new GHResponse(), null, em, fastestWeighting, 1);
        routingTemplate.lookup(Collections.singletonList(ghPoint1));
    }

    @Test
    public void testLookupAndCalcPaths_simpleSquareGraph() {
        Graph g = createSquareGraph();
        // start at node 0 and head south, make sure the round trip is long enough to reach most southern node 6
        GHPoint start = new GHPoint(1, -1);
        double heading = 180.0;
        int numPoints = 2;
        double roundTripDistance = 670000;

        GHRequest ghRequest =
                new GHRequest(Collections.singletonList(start), Collections.singletonList(heading));
        ghRequest.putHint(Parameters.Algorithms.RoundTrip.POINTS, numPoints);
        ghRequest.putHint(Parameters.Algorithms.RoundTrip.DISTANCE, roundTripDistance);
        LocationIndex locationIndex = new LocationIndexTree(g, new RAMDirectory()).prepareIndex();
        RoundTripRoutingTemplate routingTemplate =
                new RoundTripRoutingTemplate(ghRequest, new GHResponse(), locationIndex, em, fastestWeighting, 1);
        List<QueryResult> stagePoints = routingTemplate.lookup(ghRequest.getPoints());
        assertEquals(3, stagePoints.size());
        assertEquals(0, stagePoints.get(0).getClosestNode());
        assertEquals(6, stagePoints.get(1).getClosestNode());
        assertEquals(0, stagePoints.get(2).getClosestNode());

        QueryGraph queryGraph = QueryGraph.create(g, stagePoints);
        List<Path> paths = routingTemplate.calcPaths(
                queryGraph, new RoutingAlgorithmFactorySimple(), new AlgorithmOptions(DIJKSTRA_BI, fastestWeighting, tMode));
        // make sure the resulting paths are connected and form a round trip starting and ending at the start node 0
        assertEquals(2, paths.size());
        assertEquals(IntArrayList.from(0, 7, 6, 5), paths.get(0).calcNodes());
        assertEquals(IntArrayList.from(5, 4, 3, 2, 1, 0), paths.get(1).calcNodes());
    }

    @Test
    public void testCalcRoundTrip() {
        Graph g = createTestGraph();

        RoundTripRoutingTemplate rTripRouting =
                new RoundTripRoutingTemplate(new GHRequest(), new GHResponse(), null, em, fastestWeighting, 1);

        LocationIndex locationIndex = new LocationIndexTree(g, new RAMDirectory()).prepareIndex();
        QueryResult qr4 = locationIndex.findClosest(0.05, 0.25, EdgeFilter.ALL_EDGES);
        assertEquals(4, qr4.getClosestNode());
        QueryResult qr5 = locationIndex.findClosest(0.00, 0.05, EdgeFilter.ALL_EDGES);
        assertEquals(5, qr5.getClosestNode());
        QueryResult qr6 = locationIndex.findClosest(0.00, 0.10, EdgeFilter.ALL_EDGES);
        assertEquals(6, qr6.getClosestNode());

        QueryGraph qGraph = QueryGraph.create(g, Arrays.asList(qr4, qr5));
        rTripRouting.setQueryResults(Arrays.asList(qr5, qr4, qr5));
        List<Path> paths = rTripRouting.calcPaths(qGraph, new RoutingAlgorithmFactorySimple(),
                new AlgorithmOptions(DIJKSTRA_BI, fastestWeighting, tMode));
        assertEquals(2, paths.size());
        assertEquals(IntArrayList.from(5, 6, 3, 4), paths.get(0).calcNodes());
        assertEquals(IntArrayList.from(4, 8, 7, 6, 5), paths.get(1).calcNodes());

        qGraph = QueryGraph.create(g, Arrays.asList(qr4, qr6));
        rTripRouting.setQueryResults(Arrays.asList(qr6, qr4, qr6));
        paths = rTripRouting.calcPaths(qGraph, new RoutingAlgorithmFactorySimple(),
                new AlgorithmOptions(DIJKSTRA_BI, fastestWeighting, tMode));
        assertEquals(2, paths.size());
        assertEquals(IntArrayList.from(6, 3, 4), paths.get(0).calcNodes());
        assertEquals(IntArrayList.from(4, 8, 7, 6), paths.get(1).calcNodes());
    }

    private Graph createTestGraph() {
        Graph graph = new GraphHopperStorage(new RAMDirectory(), em, false, true).create(1000);
        AlternativeRouteTest.initTestGraph(graph);
        return graph;
    }

    private Graph createSquareGraph() {
        // simple square
        //  1 | 0 1 2      
        //  0 | 7   3
        // -1 | 6 5 4 
        // ---|------
        //    |-1 0 1
        GraphHopperStorage graph = new GraphBuilder(em).create();
        for (int i = 0; i < 8; ++i) {
            graph.edge(i, (i + 1) % 8, 1, true);
        }
        updateDistancesFor(graph, 0, 1, -1);
        updateDistancesFor(graph, 1, 1, 0);
        updateDistancesFor(graph, 2, 1, 1);
        updateDistancesFor(graph, 3, 0, 1);
        updateDistancesFor(graph, 4, -1, 1);
        updateDistancesFor(graph, 5, -1, 0);
        updateDistancesFor(graph, 6, -1, -1);
        updateDistancesFor(graph, 7, 0, -1);
        return graph;
    }
}
