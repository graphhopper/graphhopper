package com.graphhopper.routing.weighting;

import com.graphhopper.routing.AbstractRoutingAlgorithmTester;
import com.graphhopper.routing.util.CarFlagEncoder;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.routing.util.FlagEncoder;
import com.graphhopper.storage.Graph;
import com.graphhopper.storage.GraphBuilder;
import com.graphhopper.storage.GraphEdgeIdFinder;
import com.graphhopper.util.EdgeIterator;
import com.graphhopper.util.EdgeIteratorState;
import com.graphhopper.util.shapes.Circle;
import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.assertEquals;

public class BlockAreaWeightingTest {

    private FlagEncoder encoder = new CarFlagEncoder();
    private EncodingManager em;
    private Graph graph;

    @Before
    public void setUp() {
        encoder = new CarFlagEncoder();
        em = EncodingManager.create(Arrays.asList(encoder), 8);
        graph = new GraphBuilder(em).create();
        // 0-1
        graph.edge(0, 1, 1, true);
        AbstractRoutingAlgorithmTester.updateDistancesFor(graph, 0, 0.00, 0.00);
        AbstractRoutingAlgorithmTester.updateDistancesFor(graph, 1, 0.01, 0.01);
    }

    @Test
    public void testBlockedById() {
        GraphEdgeIdFinder.BlockArea bArea = new GraphEdgeIdFinder.BlockArea(graph);
        EdgeIteratorState edge = graph.getEdgeIteratorState(0, 1);
        BlockAreaWeighting instance = new BlockAreaWeighting(new FastestWeighting(encoder), bArea);
        assertEquals(94.35, instance.calcWeight(edge, false, EdgeIterator.NO_EDGE), .01);

        bArea.add(0);
        instance = new BlockAreaWeighting(new FastestWeighting(encoder), bArea);
        assertEquals(Double.POSITIVE_INFINITY, instance.calcWeight(edge, false, EdgeIterator.NO_EDGE), .01);
    }

    @Test
    public void testBlockedByShape() {
        EdgeIteratorState edge = graph.getEdgeIteratorState(0, 1);
        GraphEdgeIdFinder.BlockArea bArea = new GraphEdgeIdFinder.BlockArea(graph);
        BlockAreaWeighting instance = new BlockAreaWeighting(new FastestWeighting(encoder), bArea);
        assertEquals(94.35, instance.calcWeight(edge, false, EdgeIterator.NO_EDGE), 0.01);

        bArea.add(new Circle(0.01, 0.01, 100));
        assertEquals(Double.POSITIVE_INFINITY, instance.calcWeight(edge, false, EdgeIterator.NO_EDGE), .01);

        bArea = new GraphEdgeIdFinder.BlockArea(graph);
        instance = new BlockAreaWeighting(new FastestWeighting(encoder), bArea);
        // Do not match 1,1 of edge
        bArea.add(new Circle(0.1, 0.1, 100));
        assertEquals(94.35, instance.calcWeight(edge, false, EdgeIterator.NO_EDGE), .01);
    }


    @Test
    public void testNullGraph() {
        // TODO is there an equivalent to check?
        // BlockAreaWeighting weighting = new BlockAreaWeighting(new FastestWeighting(encoder));
    }
}