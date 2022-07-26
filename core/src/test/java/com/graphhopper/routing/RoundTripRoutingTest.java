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
import com.graphhopper.routing.ev.BooleanEncodedValue;
import com.graphhopper.routing.ev.DecimalEncodedValue;
import com.graphhopper.routing.ev.DecimalEncodedValueImpl;
import com.graphhopper.routing.ev.SimpleBooleanEncodedValue;
import com.graphhopper.routing.querygraph.QueryGraph;
import com.graphhopper.routing.util.EdgeFilter;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.routing.util.FiniteWeightFilter;
import com.graphhopper.routing.util.TraversalMode;
import com.graphhopper.routing.weighting.FastestWeighting;
import com.graphhopper.routing.weighting.Weighting;
import com.graphhopper.storage.BaseGraph;
import com.graphhopper.storage.RAMDirectory;
import com.graphhopper.storage.index.LocationIndex;
import com.graphhopper.storage.index.LocationIndexTree;
import com.graphhopper.storage.index.Snap;
import com.graphhopper.util.GHUtility;
import com.graphhopper.util.PMap;
import com.graphhopper.util.Parameters;
import com.graphhopper.util.shapes.GHPoint;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static com.graphhopper.util.GHUtility.updateDistancesFor;
import static com.graphhopper.util.Parameters.Algorithms.DIJKSTRA_BI;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * @author Peter Karich
 */
public class RoundTripRoutingTest {
    private final BooleanEncodedValue accessEnc = new SimpleBooleanEncodedValue("access", true);
    private final DecimalEncodedValue speedEnc = new DecimalEncodedValueImpl("speed", 5, 5, false);
    private final EncodingManager em = EncodingManager.start().add(accessEnc).add(speedEnc).build();
    private final Weighting fastestWeighting = new FastestWeighting(accessEnc, speedEnc);
    // TODO private final TraversalMode tMode = TraversalMode.EDGE_BASED;
    private final TraversalMode tMode = TraversalMode.NODE_BASED;
    private final GHPoint ghPoint1 = new GHPoint(0, 0);
    private final GHPoint ghPoint2 = new GHPoint(1, 1);

    @Test
    public void lookup_throwsIfNumberOfPointsNotOne() {
        assertThrows(IllegalArgumentException.class, () -> RoundTripRouting.lookup(Arrays.asList(ghPoint1, ghPoint2),
                new FiniteWeightFilter(fastestWeighting), null, new RoundTripRouting.Params()));
    }

    @Test
    public void testLookupAndCalcPaths_simpleSquareGraph() {
        BaseGraph g = createSquareGraph();
        // start at node 0 and head south, make sure the round trip is long enough to reach most southern node 6
        GHPoint start = new GHPoint(1, -1);
        double heading = 180.0;
        int numPoints = 2;
        double roundTripDistance = 670000;

        PMap hints = new PMap();
        hints.putObject(Parameters.Algorithms.RoundTrip.POINTS, numPoints);
        hints.putObject(Parameters.Algorithms.RoundTrip.DISTANCE, roundTripDistance);
        LocationIndex locationIndex = new LocationIndexTree(g, new RAMDirectory()).prepareIndex();
        List<Snap> stagePoints = RoundTripRouting.lookup(Collections.singletonList(start),
                new FiniteWeightFilter(fastestWeighting), locationIndex,
                new RoundTripRouting.Params(hints, heading, 3));
        assertEquals(3, stagePoints.size());
        assertEquals(0, stagePoints.get(0).getClosestNode());
        assertEquals(6, stagePoints.get(1).getClosestNode());
        assertEquals(0, stagePoints.get(2).getClosestNode());

        QueryGraph queryGraph = QueryGraph.create(g, stagePoints);
        List<Path> paths = RoundTripRouting.calcPaths(stagePoints, new FlexiblePathCalculator(queryGraph,
                new RoutingAlgorithmFactorySimple(), fastestWeighting, new AlgorithmOptions().setAlgorithm(DIJKSTRA_BI).setTraversalMode(tMode))).paths;
        // make sure the resulting paths are connected and form a round trip starting and ending at the start node 0
        assertEquals(2, paths.size());
        assertEquals(IntArrayList.from(0, 7, 6, 5), paths.get(0).calcNodes());
        assertEquals(IntArrayList.from(5, 4, 3, 2, 1, 0), paths.get(1).calcNodes());
    }

    @Test
    public void testCalcRoundTrip() {
        BaseGraph g = createTestGraph();

        LocationIndex locationIndex = new LocationIndexTree(g, new RAMDirectory()).prepareIndex();
        Snap snap4 = locationIndex.findClosest(0.05, 0.25, EdgeFilter.ALL_EDGES);
        assertEquals(4, snap4.getClosestNode());
        Snap snap5 = locationIndex.findClosest(0.00, 0.05, EdgeFilter.ALL_EDGES);
        assertEquals(5, snap5.getClosestNode());
        Snap snap6 = locationIndex.findClosest(0.00, 0.10, EdgeFilter.ALL_EDGES);
        assertEquals(6, snap6.getClosestNode());

        QueryGraph qGraph = QueryGraph.create(g, Arrays.asList(snap4, snap5));

        FlexiblePathCalculator pathCalculator = new FlexiblePathCalculator(
                qGraph, new RoutingAlgorithmFactorySimple(), fastestWeighting, new AlgorithmOptions().setAlgorithm(DIJKSTRA_BI).setTraversalMode(tMode));
        List<Path> paths = RoundTripRouting.calcPaths(Arrays.asList(snap5, snap4, snap5), pathCalculator).paths;
        assertEquals(2, paths.size());
        assertEquals(IntArrayList.from(5, 6, 3), paths.get(0).calcNodes());
        assertEquals(IntArrayList.from(3, 2, 9, 1, 5), paths.get(1).calcNodes());

        qGraph = QueryGraph.create(g, Arrays.asList(snap4, snap6));
        pathCalculator = new FlexiblePathCalculator(
                qGraph, new RoutingAlgorithmFactorySimple(), fastestWeighting, new AlgorithmOptions().setAlgorithm(DIJKSTRA_BI).setTraversalMode(tMode));
        paths = RoundTripRouting.calcPaths(Arrays.asList(snap6, snap4, snap6), pathCalculator).paths;
        assertEquals(2, paths.size());
        assertEquals(IntArrayList.from(6, 3), paths.get(0).calcNodes());
        assertEquals(IntArrayList.from(3, 4, 8, 7, 6), paths.get(1).calcNodes());
    }

    private BaseGraph createTestGraph() {
        BaseGraph graph = new BaseGraph.Builder(em).withTurnCosts(true).create();
        AlternativeRouteTest.initTestGraph(graph, accessEnc, speedEnc);
        return graph;
    }

    private BaseGraph createSquareGraph() {
        // simple square
        //  1 | 0 1 2      
        //  0 | 7   3
        // -1 | 6 5 4 
        // ---|------
        //    |-1 0 1
        BaseGraph graph = new BaseGraph.Builder(em).create();
        for (int i = 0; i < 8; ++i) {
            GHUtility.setSpeed(60, true, true, accessEnc, speedEnc, graph.edge(i, (i + 1) % 8).setDistance(1));
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
