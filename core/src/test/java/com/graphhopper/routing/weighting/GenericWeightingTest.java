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
import com.graphhopper.routing.util.spatialrules.*;
import com.graphhopper.routing.util.spatialrules.countries.GermanySpatialRule;
import com.graphhopper.storage.*;

import static com.graphhopper.storage.GraphEdgeIdFinder.BLOCKED_EDGES;
import static com.graphhopper.storage.GraphEdgeIdFinder.BLOCKED_SHAPES;

import com.graphhopper.util.*;
import com.graphhopper.util.shapes.BBox;
import com.graphhopper.util.shapes.Circle;
import com.graphhopper.util.shapes.GHPoint;
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
    public void testSpatial() {
        DataFlagEncoder encoder = new DataFlagEncoder();
        EncodingManager em = new EncodingManager(encoder);
        Graph graph;

        SpatialRuleLookup lookup = new SpatialRuleLookupArray(new BBox(0, 1, 0, 1), 1, false);
        SpatialRule germanRule = new GermanySpatialRule();
        germanRule.addBorder(new Polygon(new double[]{0, 0, 1, 1}, new double[]{0, 1, 1, 0}));
        lookup.addRule(germanRule);
        encoder.setSpatialRuleLookup(lookup);

        ReaderWay way = new ReaderWay(27l);
        way.setTag("highway", "track");
        way.setTag("estimated_center", new GHPoint(0.005, 0.005));

        ReaderWay way2 = new ReaderWay(28l);
        way2.setTag("highway", "track");
        way2.setTag("estimated_center", new GHPoint(-0.005, -0.005));

        graph = new GraphBuilder(em).create();
        EdgeIteratorState e1 = graph.edge(0, 1, 1, true);
        EdgeIteratorState e2 = graph.edge(0, 2, 1, true);
        AbstractRoutingAlgorithmTester.updateDistancesFor(graph, 0, 0.00, 0.00);
        AbstractRoutingAlgorithmTester.updateDistancesFor(graph, 1, 0.01, 0.01);
        AbstractRoutingAlgorithmTester.updateDistancesFor(graph, 2, -0.01, -0.01);
        graph.getEdgeIteratorState(e1.getEdge(), e1.getAdjNode()).setFlags(encoder.handleWayTags(way, 1, 0));
        graph.getEdgeIteratorState(e2.getEdge(), e2.getAdjNode()).setFlags(encoder.handleWayTags(way2, 1, 0));

        ConfigMap cMap = encoder.readStringMap(new PMap());
        GenericWeighting weighting = new GenericWeighting(encoder, cMap);
        EdgeIteratorState edge1 = graph.getEdgeIteratorState(e1.getEdge(), e1.getAdjNode());
        EdgeIteratorState edge2 = graph.getEdgeIteratorState(e2.getEdge(), e2.getAdjNode());
        // * 10 is the "eventuallAccessiblePenalty" of the GenericWeighting
        assertEquals(weighting.calcWeight(edge2, false, EdgeIterator.NO_EDGE) * 10, weighting.calcWeight(edge1, false, EdgeIterator.NO_EDGE), .1);
    }

    @Test
    public void testNullGraph() {
        ConfigMap cMap = encoder.readStringMap(new PMap());
        GenericWeighting weighting = new GenericWeighting(encoder, cMap);
        weighting.setGraph(null);
    }
}
