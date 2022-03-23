package com.graphhopper.routing.weighting;

import com.graphhopper.coll.GHIntHashSet;
import com.graphhopper.routing.querygraph.QueryGraph;
import com.graphhopper.routing.util.CarFlagEncoder;
import com.graphhopper.routing.util.EdgeFilter;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.storage.BaseGraph;
import com.graphhopper.storage.GraphEdgeIdFinder;
import com.graphhopper.storage.index.LocationIndex;
import com.graphhopper.storage.index.LocationIndexTree;
import com.graphhopper.storage.index.Snap;
import com.graphhopper.util.EdgeIterator;
import com.graphhopper.util.EdgeIteratorState;
import com.graphhopper.util.GHUtility;
import com.graphhopper.util.shapes.Circle;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.graphhopper.util.GHUtility.updateDistancesFor;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class BlockAreaWeightingTest {

    private CarFlagEncoder encoder = new CarFlagEncoder();
    private EncodingManager em;
    private BaseGraph graph;

    @BeforeEach
    public void setUp() {
        encoder = new CarFlagEncoder();
        em = EncodingManager.create(encoder);
        graph = new BaseGraph.Builder(em).create();
        // 0-1
        GHUtility.setSpeed(60, true, true, encoder, graph.edge(0, 1).setDistance(1));
        updateDistancesFor(graph, 0, 0.00, 0.00);
        updateDistancesFor(graph, 1, 0.01, 0.01);
    }

    @Test
    public void testBlockedById() {
        GraphEdgeIdFinder.BlockArea bArea = new GraphEdgeIdFinder.BlockArea(graph);
        EdgeIteratorState edge = graph.getEdgeIteratorState(0, 1);
        BlockAreaWeighting instance = new BlockAreaWeighting(new FastestWeighting(encoder), bArea);
        assertEquals(94.35, instance.calcEdgeWeight(edge, false), .01);

        GHIntHashSet set = new GHIntHashSet();
        set.add(0);
        bArea.add(null, set);
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
        GHIntHashSet set = new GHIntHashSet();
        set.add(0);
        bArea.add(new Circle(0.0025, 0.0025, 1), set);

        LocationIndex index = new LocationIndexTree(graph, graph.getDirectory()).prepareIndex();
        Snap snap = index.findClosest(0.005, 0.005, EdgeFilter.ALL_EDGES);
        QueryGraph queryGraph = QueryGraph.create(graph, snap);

        BlockAreaWeighting instance = new BlockAreaWeighting(new FastestWeighting(encoder), bArea);
        EdgeIterator iter = queryGraph.createEdgeExplorer().setBaseNode(snap.getClosestNode());
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