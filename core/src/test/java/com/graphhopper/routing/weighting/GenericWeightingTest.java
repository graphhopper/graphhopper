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
package com.graphhopper.routing.weighting;

import com.graphhopper.coll.GHIntHashSet;
import com.graphhopper.reader.ReaderWay;
import com.graphhopper.routing.AbstractRoutingAlgorithmTester;
import com.graphhopper.routing.util.*;
import com.graphhopper.storage.*;

import static com.graphhopper.storage.GraphEdgeIdFinder.BLOCKED_EDGES;
import static com.graphhopper.storage.GraphEdgeIdFinder.BLOCKED_SHAPES;

import com.graphhopper.util.*;
import com.graphhopper.util.shapes.Circle;
import com.graphhopper.util.shapes.Shape;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;

/**
 * @author Peter Karich
 */
public class GenericWeightingTest {
    private final DataFlagEncoder encoder = new DataFlagEncoder();
    private final EncodingManager em = new EncodingManager(encoder);
    private Graph graph;

    private final double edgeWeight = 566111;

    @Before
    public void setUp() {
        ReaderWay way = new ReaderWay(27l);
        way.setTag("highway", "primary");
        way.setTag("maxspeed", "10");

        graph = new GraphBuilder(em).create();
        // 0-1
        graph.edge(0, 1, 1, true);
        AbstractRoutingAlgorithmTester.updateDistancesFor(graph, 0, 0.00, 0.00);
        AbstractRoutingAlgorithmTester.updateDistancesFor(graph, 1, 0.01, 0.01);
        graph.getEdgeIteratorState(0, 1).setFlags(encoder.handleWayTags(way, 1, 0));

    }

    @Test
    public void testBlockedById() {
        EdgeIteratorState edge = graph.getEdgeIteratorState(0, 1);
        ConfigMap cMap = encoder.readStringMap(new PMap());
        Weighting instance = new GenericWeighting(encoder, cMap);
        assertEquals(edgeWeight, instance.calcWeight(edge, false, EdgeIterator.NO_EDGE), 1e-8);

        GHIntHashSet blockedEdges = new GHIntHashSet(1);
        cMap.put(BLOCKED_EDGES, blockedEdges);
        blockedEdges.add(0);
        instance = new GenericWeighting(encoder, cMap);
        assertEquals(Double.POSITIVE_INFINITY, instance.calcWeight(edge, false, EdgeIterator.NO_EDGE), 1e-8);
    }

    @Test
    public void testBlockedByShape() {
        EdgeIteratorState edge = graph.getEdgeIteratorState(0, 1);
        ConfigMap cMap = encoder.readStringMap(new PMap());
        GenericWeighting instance = new GenericWeighting(encoder, cMap);
        assertEquals(edgeWeight, instance.calcWeight(edge, false, EdgeIterator.NO_EDGE), 1e-8);

        List<Shape> shapes = new ArrayList<>(1);
        shapes.add(new Circle(0.01, 0.01, 100));
        cMap.put(BLOCKED_SHAPES, shapes);
        instance = new GenericWeighting(encoder, cMap);
        instance.setGraph(graph);
        assertEquals(Double.POSITIVE_INFINITY, instance.calcWeight(edge, false, EdgeIterator.NO_EDGE), 1e-8);

        shapes.clear();
        // Do not match 1,1 of edge
        shapes.add(new Circle(0.1, 0.1, 100));
        cMap.put(BLOCKED_SHAPES, shapes);
        instance = new GenericWeighting(encoder, cMap);
        instance.setGraph(graph);
        assertEquals(edgeWeight, instance.calcWeight(edge, false, EdgeIterator.NO_EDGE), 1e-8);
    }

    @Test
    public void testCalcTime() {
        ConfigMap cMap = encoder.readStringMap(new PMap());
        GenericWeighting weighting = new GenericWeighting(encoder, cMap);
        EdgeIteratorState edge = graph.getEdgeIteratorState(0, 1);
        assertEquals(edgeWeight, weighting.calcMillis(edge, false, EdgeIterator.NO_EDGE), .1);
    }

    @Test
    public void testNullGraph() {
        ConfigMap cMap = encoder.readStringMap(new PMap());
        GenericWeighting weighting = new GenericWeighting(encoder, cMap);
        weighting.setGraph(null);
    }
}
