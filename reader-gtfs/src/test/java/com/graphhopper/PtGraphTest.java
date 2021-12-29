package com.graphhopper;

import MyGame.Sample.Edge;
import com.google.flatbuffers.FlatBufferBuilder;
import com.google.transit.realtime.GtfsRealtime;
import com.graphhopper.gtfs.GtfsStorage;
import com.graphhopper.gtfs.PtEdgeAttributes;
import com.graphhopper.gtfs.PtGraph;
import com.graphhopper.storage.RAMDirectory;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.BitSet;

public class PtGraphTest {

    @Test
    public void testEdge() {
        PtGraph ptGraph = new PtGraph(new RAMDirectory(), 0);
        ptGraph.create(1);
        int edge1 = ptGraph.createEdge(12, 23, new PtEdgeAttributes(GtfsStorage.EdgeType.ENTER_PT, 123, new GtfsStorage.Validity(new BitSet(30), ZoneId.of("UTC"), LocalDate.now()), 7, null, 1, -1, null, null));
        int edge2 = ptGraph.createEdge(23, 5, new PtEdgeAttributes(GtfsStorage.EdgeType.ENTER_PT, 123, null, 7, null, 1, -1, null, null));
        int edge3 = ptGraph.createEdge(7, 23, new PtEdgeAttributes(GtfsStorage.EdgeType.ENTER_PT, 123, null, 7, null, 1, -1, null, null));

        System.out.println(ptGraph.edge(edge1));
        System.out.println(ptGraph.edge(edge2));
        System.out.println("---");

        ptGraph.edgesAround(12).forEach(e -> System.out.println(e));

        System.out.println("---");

        ptGraph.edgesAround(23).forEach(System.out::println);
        System.out.println("---");

        ptGraph.backEdgesAround(23).forEach(System.out::println);

    }


}
