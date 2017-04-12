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
import com.graphhopper.routing.util.DataFlagEncoder;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.storage.Graph;
import com.graphhopper.storage.GraphBuilder;
import com.graphhopper.util.ConfigMap;
import com.graphhopper.util.EdgeIterator;
import com.graphhopper.util.EdgeIteratorState;
import com.graphhopper.util.PMap;
import com.graphhopper.util.shapes.Circle;
import com.graphhopper.util.shapes.Shape;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static com.graphhopper.storage.GraphEdgeIdFinder.BLOCKED_EDGES;
import static com.graphhopper.storage.GraphEdgeIdFinder.BLOCKED_SHAPES;
import static org.junit.Assert.assertEquals;

/**
 * @author Fedor Ermishin
 */
public class BlockedWeightingTest {
    private final DataFlagEncoder encoder;
    private final EncodingManager em;
    private Graph graph;

    private double edgeDistance;

    public BlockedWeightingTest() {
        PMap properties = new PMap();
        properties.put("store_height", true);
        properties.put("store_weight", true);
        properties.put("store_width", true);
        encoder = new DataFlagEncoder(properties);
        em = new EncodingManager(Arrays.asList(encoder), 8);
    }

    @Before
    public void setUp() {
        ReaderWay way = new ReaderWay(27L);
        way.setTag("highway", "primary");
        way.setTag("maxspeed", "10");
        way.setTag("maxheight", "4.4");

        graph = new GraphBuilder(em).create();
        // 0-1
        graph.edge(0, 1, 1, true);
        AbstractRoutingAlgorithmTester.updateDistancesFor(graph, 0, 0.00, 0.00);
        AbstractRoutingAlgorithmTester.updateDistancesFor(graph, 1, 0.01, 0.01);
        graph.getEdgeIteratorState(0, 1).setFlags(encoder.handleWayTags(way, 1, 0));

        edgeDistance = graph.getEdgeIteratorState(0, 1).getDistance();
    }

    @Test
    public void testBlockedById() {
        EdgeIteratorState edge = graph.getEdgeIteratorState(0, 1);
        ConfigMap cMap = encoder.readStringMap(new PMap());
        Weighting baseWeighting = new ShortestWeighting(encoder);
        Weighting instance = new BlockedWeighting(baseWeighting, cMap);
        assertEquals(edgeDistance, instance.calcWeight(edge, false, EdgeIterator.NO_EDGE), 1e-8);

        GHIntHashSet blockedEdges = new GHIntHashSet(1);
        cMap.put(BLOCKED_EDGES, blockedEdges);
        blockedEdges.add(0);
        baseWeighting = new ShortestWeighting(encoder);
        instance = new BlockedWeighting(baseWeighting, cMap);
        assertEquals(Double.POSITIVE_INFINITY, instance.calcWeight(edge, false, EdgeIterator.NO_EDGE), 1e-8);
    }

    @Test
    public void testBlockedByShape() {
        EdgeIteratorState edge = graph.getEdgeIteratorState(0, 1);
        ConfigMap cMap = encoder.readStringMap(new PMap());
        Weighting baseWeighting = new ShortestWeighting(encoder);
        BlockedWeighting instance = new BlockedWeighting(baseWeighting, cMap);
        assertEquals(edgeDistance, instance.calcWeight(edge, false, EdgeIterator.NO_EDGE), 1e-8);

        List<Shape> shapes = new ArrayList<>(1);
        shapes.add(new Circle(0.01, 0.01, 100));
        cMap.put(BLOCKED_SHAPES, shapes);
        baseWeighting = new ShortestWeighting(encoder);
        instance = new BlockedWeighting(baseWeighting, cMap);
        instance.setGraph(graph);
        assertEquals(Double.POSITIVE_INFINITY, instance.calcWeight(edge, false, EdgeIterator.NO_EDGE), 1e-8);

        shapes.clear();
        // Do not match 1,1 of edge
        shapes.add(new Circle(0.1, 0.1, 100));
        cMap.put(BLOCKED_SHAPES, shapes);
        baseWeighting = new ShortestWeighting(encoder);
        instance = new BlockedWeighting(baseWeighting, cMap);
        instance.setGraph(graph);
        assertEquals(edgeDistance, instance.calcWeight(edge, false, EdgeIterator.NO_EDGE), 1e-8);
    }

    @Test
    public void testNullGraph() {
        ConfigMap cMap = encoder.readStringMap(new PMap());
        Weighting baseWeighting = new ShortestWeighting(encoder);
        BlockedWeighting weighting = new BlockedWeighting(baseWeighting, cMap);
        weighting.setGraph(null);
    }
}
