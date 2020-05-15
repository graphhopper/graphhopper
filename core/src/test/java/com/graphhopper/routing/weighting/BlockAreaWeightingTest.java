package com.graphhopper.routing.weighting;

import com.graphhopper.coll.GHIntHashSet;
import com.graphhopper.routing.querygraph.QueryGraph;
import com.graphhopper.routing.util.CarFlagEncoder;
import com.graphhopper.routing.util.EdgeFilter;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.routing.util.FlagEncoder;
import com.graphhopper.storage.GraphBuilder;
import com.graphhopper.storage.GraphEdgeIdFinder;
import com.graphhopper.storage.GraphHopperStorage;
import com.graphhopper.storage.index.LocationIndex;
import com.graphhopper.storage.index.LocationIndexTree;
import com.graphhopper.storage.index.QueryResult;
import com.graphhopper.util.EdgeIterator;
import com.graphhopper.util.EdgeIteratorState;
import com.graphhopper.util.shapes.Circle;
import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;

import static com.graphhopper.util.GHUtility.updateDistancesFor;
import static org.junit.Assert.assertEquals;

public class BlockAreaWeightingTest {

    private FlagEncoder encoder = new CarFlagEncoder();
    private EncodingManager em;
    private GraphHopperStorage graph;

    @Before
    public void setUp() {
        encoder = new CarFlagEncoder();
        em = EncodingManager.create(Arrays.asList(encoder));
        graph = new GraphBuilder(em).create();
        // 0-1
        graph.edge(0, 1, 1, true);
        updateDistancesFor(graph, 0, 0.00, 0.00);
        updateDistancesFor(graph, 1, 0.01, 0.01);
    }

    @Test
    public void testBlockedById() {
        GraphEdgeIdFinder.BlockArea bArea = new GraphEdgeIdFinder.BlockArea(graph);
        EdgeIteratorState edge = graph.getEdgeIteratorState(0, 1);
        BlockAreaWeighting instance = new BlockAreaWeighting(new FastestWeighting(encoder), bArea);
        assertEquals(94.35, instance.calcEdgeWeight(edge, false), .01);

        GHIntHashSet set = bArea.add(null);
        set.add(0);
        instance = new BlockAreaWeighting(new FastestWeighting(encoder), bArea);
        assertEquals(Double.POSITIVE_INFINITY, instance.calcEdgeWeight(edge, false), .01);
    }

    @Test
    public void testBlockedByShape() {
        EdgeIteratorState edge = graph.getEdgeIteratorState(0, 1);
        GraphEdgeIdFinder.BlockArea bArea = new GraphEdgeIdFinder.BlockArea(graph);
        BlockAreaWeighting instance = new BlockAreaWeighting(new FastestWeighting(encoder), bArea);
        assertEquals(94.35, instance.calcEdgeWeight(edge, false), 0.01);

        bArea.add(new Circle(0.01, 0.01, 100));
        assertEquals(Double.POSITIVE_INFINITY, instance.calcEdgeWeight(edge, false), .01);

        bArea = new GraphEdgeIdFinder.BlockArea(graph);
        instance = new BlockAreaWeighting(new FastestWeighting(encoder), bArea);
        // Do not match 1,1 of edge
        bArea.add(new Circle(0.1, 0.1, 100));
        assertEquals(94.35, instance.calcEdgeWeight(edge, false), .01);
    }

    @Test
    public void testBlockVirtualEdges_QueryGraph() {
        GraphEdgeIdFinder.BlockArea bArea = new GraphEdgeIdFinder.BlockArea(graph);
        // add base graph edge to fill caches and trigger edgeId cache search (without virtual edges)
        GHIntHashSet set = bArea.add(new Circle(0.0025, 0.0025, 1));
        set.add(0);

        LocationIndex index = new LocationIndexTree(graph, graph.getDirectory()).prepareIndex();
        QueryResult qr = index.findClosest(0.005, 0.005, EdgeFilter.ALL_EDGES);
        QueryGraph queryGraph = QueryGraph.create(graph, qr);

        BlockAreaWeighting instance = new BlockAreaWeighting(new FastestWeighting(encoder), bArea);
        EdgeIterator iter = queryGraph.createEdgeExplorer().setBaseNode(qr.getClosestNode());
        int blockedEdges = 0, totalEdges = 0;
        while (iter.next()) {
            if (Double.isInfinite(instance.calcEdgeWeight(iter, false)))
                blockedEdges++;
            totalEdges++;
        }
        assertEquals(1, blockedEdges);
        assertEquals(2, totalEdges);
    }
}