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
package com.graphhopper.storage;

import com.graphhopper.coll.GHIntHashSet;
import com.graphhopper.routing.AbstractRoutingAlgorithmTester;
import com.graphhopper.routing.util.CarFlagEncoder;
import com.graphhopper.routing.util.DefaultEdgeFilter;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.routing.util.FlagEncoder;
import com.graphhopper.routing.util.HintsMap;

import static com.graphhopper.storage.GraphEdgeIdFinder.BLOCKED_EDGES;
import static com.graphhopper.storage.GraphEdgeIdFinder.BLOCKED_SHAPES;

import com.graphhopper.storage.index.LocationIndex;
import com.graphhopper.storage.index.LocationIndexTree;
import com.graphhopper.util.ConfigMap;
import com.graphhopper.util.Parameters;
import com.graphhopper.util.shapes.Circle;
import com.graphhopper.util.shapes.Shape;

import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * @author Peter Karich
 */
public class GraphEdgeIdFinderTest {

    @Test
    public void testParseStringHints() {
        FlagEncoder encoder = new CarFlagEncoder();
        EncodingManager em = new EncodingManager(encoder);
        GraphHopperStorage graph = new GraphBuilder(em).create();
        // 0-1-2
        // | |
        // 3-4
        graph.edge(0, 1, 1, true);
        graph.edge(1, 2, 1, true);
        graph.edge(3, 4, 1, true);
        graph.edge(0, 3, 1, true);
        graph.edge(1, 4, 1, true);
        AbstractRoutingAlgorithmTester.updateDistancesFor(graph, 0, 0.01, 0.00);
        AbstractRoutingAlgorithmTester.updateDistancesFor(graph, 1, 0.01, 0.01);
        AbstractRoutingAlgorithmTester.updateDistancesFor(graph, 2, 0.01, 0.02);
        AbstractRoutingAlgorithmTester.updateDistancesFor(graph, 3, 0.00, 0.00);
        AbstractRoutingAlgorithmTester.updateDistancesFor(graph, 4, 0.00, 0.01);

        LocationIndex locationIndex = new LocationIndexTree(graph, new RAMDirectory())
                .prepareIndex();

        HintsMap hints = new HintsMap();
        hints.put(Parameters.Routing.BLOCK_AREA, "0.01,0.005,1");

        ConfigMap cMap = new ConfigMap();
        GraphEdgeIdFinder graphFinder = new GraphEdgeIdFinder(graph, locationIndex);
        ConfigMap result = graphFinder.parseStringHints(cMap, hints, new DefaultEdgeFilter(encoder));

        GHIntHashSet blockedEdges = new GHIntHashSet();
        blockedEdges.add(0);
        assertEquals(blockedEdges, result.get(BLOCKED_EDGES, new GHIntHashSet()));
        List<Shape> blockedShapes = new ArrayList<>();
        assertEquals(blockedShapes, result.get(BLOCKED_SHAPES, new ArrayList<>()));

        // big area converts into shapes
        hints.put(Parameters.Routing.BLOCK_AREA, "0,0,1000");
        result = graphFinder.parseStringHints(cMap, hints, new DefaultEdgeFilter(encoder));
        blockedEdges.clear();
        assertEquals(blockedEdges, result.get(BLOCKED_EDGES, new GHIntHashSet()));
        blockedShapes.add(new Circle(0, 0, 1000));
        assertEquals(blockedShapes, result.get(BLOCKED_SHAPES, new ArrayList<>()));
    }
}
