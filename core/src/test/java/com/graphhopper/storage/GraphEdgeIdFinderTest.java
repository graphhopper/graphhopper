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
import com.graphhopper.storage.index.LocationIndex;
import com.graphhopper.storage.index.LocationIndexTree;
import com.graphhopper.util.shapes.Circle;
import com.graphhopper.util.shapes.Shape;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;

/**
 * @author Peter Karich
 */
public class GraphEdgeIdFinderTest {

    @Test
    public void testParseStringHints() {
        FlagEncoder encoder = new CarFlagEncoder();
        EncodingManager em = EncodingManager.create(encoder);
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

        GraphEdgeIdFinder graphFinder = new GraphEdgeIdFinder(graph, locationIndex);
        GraphEdgeIdFinder.BlockArea blockArea = graphFinder.parseBlockArea("0.01,0.005,1", DefaultEdgeFilter.allEdges(encoder), 1000 * 1000);

        GHIntHashSet blockedEdges = new GHIntHashSet();
        blockedEdges.add(0);
        assertEquals(blockedEdges, blockArea.blockedEdges);
        List<Shape> blockedShapes = new ArrayList<>();
        assertEquals(blockedShapes, blockArea.blockedShapes);

        // big area converts into shapes
        graphFinder = new GraphEdgeIdFinder(graph, locationIndex);
        blockArea = graphFinder.parseBlockArea("0,0,1000", DefaultEdgeFilter.allEdges(encoder), 1000 * 1000);
        blockedEdges.clear();
        assertEquals(blockedEdges, blockArea.blockedEdges);
        blockedShapes.add(new Circle(0, 0, 1000));
        assertEquals(blockedShapes, blockArea.blockedShapes);
    }

    @Test
    public void testBlockAreasWithPolygon() {
        FlagEncoder encoder = new CarFlagEncoder();
        EncodingManager em = EncodingManager.create(encoder);
        GraphHopperStorage graph = new GraphBuilder(em).create();

        // 00-01-02-03
        // |  |
        // 04-05-06-07
        // |  |
        // 08-09-10-11
        graph.edge(0, 1, 1, true); // 0
        graph.edge(1, 2, 1, true); // 1
        graph.edge(2, 3, 1, true); // 2
        graph.edge(0, 4, 1, true); // 3
        graph.edge(1, 5, 1, true); // 4
        graph.edge(4, 5, 1, true); // 5
        graph.edge(5, 6, 1, true); // 6
        graph.edge(6, 7, 1, true); // 7
        graph.edge(4, 8, 1, true); // 8
        graph.edge(5, 9, 1, true); // 9
        graph.edge(8, 9, 1, true); // 10
        graph.edge(9, 10, 1, true); // 11
        graph.edge(10, 11, 1, true); // 12

        AbstractRoutingAlgorithmTester.updateDistancesFor(graph, 0, 2, 0);
        AbstractRoutingAlgorithmTester.updateDistancesFor(graph, 1, 2, 1);
        AbstractRoutingAlgorithmTester.updateDistancesFor(graph, 2, 2, 2);
        AbstractRoutingAlgorithmTester.updateDistancesFor(graph, 3, 2, 3);
        AbstractRoutingAlgorithmTester.updateDistancesFor(graph, 4, 1, 0);
        AbstractRoutingAlgorithmTester.updateDistancesFor(graph, 5, 1, 1);
        AbstractRoutingAlgorithmTester.updateDistancesFor(graph, 6, 1, 2);
        AbstractRoutingAlgorithmTester.updateDistancesFor(graph, 7, 1, 3);
        AbstractRoutingAlgorithmTester.updateDistancesFor(graph, 8, 0, 0);
        AbstractRoutingAlgorithmTester.updateDistancesFor(graph, 9, 0, 1);
        AbstractRoutingAlgorithmTester.updateDistancesFor(graph, 10, 0, 2);
        AbstractRoutingAlgorithmTester.updateDistancesFor(graph, 11, 0, 3);

        LocationIndex locationIndex = new LocationIndexTree(graph, new RAMDirectory())
                .prepareIndex();

        GraphEdgeIdFinder graphFinder = new GraphEdgeIdFinder(graph, locationIndex);
        GraphEdgeIdFinder.BlockArea blockArea = graphFinder.parseBlockArea("2,1, 0,2, 2,3", DefaultEdgeFilter.allEdges(encoder), 1000 * 1000);

        GHIntHashSet blockedEdges = new GHIntHashSet();
        blockedEdges.addAll(new int[]{1, 2, 6, 7});
        assertEquals(blockedEdges, blockArea.blockedEdges);

        blockArea = graphFinder.parseBlockArea("2,1, 1,3, 1,2, 0,1", DefaultEdgeFilter.allEdges(encoder), 1000 * 1000);

        blockedEdges = new GHIntHashSet();
        blockedEdges.addAll(new int[]{4, 9, 6, 7});
        assertEquals(blockedEdges, blockArea.blockedEdges);
    }
}
