package com.graphhopper;

import com.graphhopper.gtfs.GtfsStorage;
import com.graphhopper.gtfs.PtEdgeAttributes;
import com.graphhopper.gtfs.PtGraph;
import com.graphhopper.storage.RAMDirectory;
import org.junit.jupiter.api.Test;

public class PtGraphTest {

    @Test
    public void testEdge() {
        PtGraph ptGraph = new PtGraph(new RAMDirectory(), 0);
        int edge1 = ptGraph.createEdge(12, 23, new PtEdgeAttributes(GtfsStorage.EdgeType.ENTER_PT, 123, null, 7, null, 1));
        int edge2 = ptGraph.createEdge(23, 5, new PtEdgeAttributes(GtfsStorage.EdgeType.ENTER_PT, 123, null, 7, null, 1));
        int edge3 = ptGraph.createEdge(7, 23, new PtEdgeAttributes(GtfsStorage.EdgeType.ENTER_PT, 123, null, 7, null, 1));

        System.out.println(ptGraph.edge(edge1));
        System.out.println(ptGraph.edge(edge2));
        System.out.println("---");

        ptGraph.edgesAround(12).forEach(System.out::println);

        System.out.println("---");

        ptGraph.edgesAround(23).forEach(System.out::println);
        System.out.println("---");

        ptGraph.backEdgesAround(23).forEach(System.out::println);

    }

}
