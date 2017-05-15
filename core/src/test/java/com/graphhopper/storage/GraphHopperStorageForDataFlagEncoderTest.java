package com.graphhopper.storage;

import com.graphhopper.GraphHopper;
import com.graphhopper.reader.ReaderWay;
import com.graphhopper.routing.AbstractRoutingAlgorithmTester;
import com.graphhopper.routing.util.DataFlagEncoder;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.routing.util.FlagEncoder;
import com.graphhopper.util.Helper;
import com.graphhopper.util.PMap;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.Closeable;
import java.io.File;
import java.util.Arrays;

import static org.junit.Assert.*;

/**
 * Created by tuan on 20/01/17.
 */
public class GraphHopperStorageForDataFlagEncoderTest {
    private final String locationParent = "./target/graphstorage";
    private int defaultSize = 100;
    private String defaultGraphLoc = "./target/graphstorage/default";
    private GraphHopperStorage graph;

    private final PMap properties;
    private final DataFlagEncoder encoder;
    private final EncodingManager encodingManager;

    public GraphHopperStorageForDataFlagEncoderTest() {
        properties = new PMap();
        properties.put("store_height", true);
        properties.put("store_weight", true);
        properties.put("store_width", false);
        encoder = new DataFlagEncoder(properties);
        encodingManager = new EncodingManager(Arrays.asList(encoder), 8);
    }

    @Before
    public void setUp() {
        Helper.removeDir(new File(locationParent));
    }

    @After
    public void tearDown() {
        Helper.close((Closeable) graph);
        Helper.removeDir(new File(locationParent));
    }


    @Test
    public void testStorageProperties() {
        graph = new GraphBuilder(encodingManager).setStore(true).setLocation(defaultGraphLoc).create();

        // 0-1
        ReaderWay way_0_1 = new ReaderWay(27l);
        way_0_1.setTag("highway", "primary");
        way_0_1.setTag("maxheight", "4.4");

        graph.edge(0, 1, 1, true);
        AbstractRoutingAlgorithmTester.updateDistancesFor(graph, 0, 0.00, 0.00);
        AbstractRoutingAlgorithmTester.updateDistancesFor(graph, 1, 0.01, 0.01);
        graph.getEdgeIteratorState(0, 1).setFlags(encoder.handleWayTags(way_0_1, 1, 0));

        // 1-2
        ReaderWay way_1_2 = new ReaderWay(28l);
        way_1_2.setTag("highway", "primary");
        way_1_2.setTag("maxweight", "45");

        graph.edge(1, 2, 1, true);
        AbstractRoutingAlgorithmTester.updateDistancesFor(graph, 2, 0.02, 0.02);
        graph.getEdgeIteratorState(1, 2).setFlags(encoder.handleWayTags(way_1_2, 1, 0));

        // 2-0
        ReaderWay way_2_0 = new ReaderWay(29l);
        way_2_0.setTag("highway", "primary");
        way_2_0.setTag("maxwidth", "5");

        graph.edge(2, 0, 1, true);
        graph.getEdgeIteratorState(2, 0).setFlags(encoder.handleWayTags(way_2_0, 1, 0));

        graph.flush();
        graph.close();

        GraphHopper hopper = new GraphHopper().setGraphHopperLocation(defaultGraphLoc).setCHEnabled(false).importOrLoad();
        EncodingManager em = hopper.getEncodingManager();
        assertNotNull(em);
        assertEquals(1, em.fetchEdgeEncoders().size());

        FlagEncoder flagEncoder = em.fetchEdgeEncoders().get(0);
        assertTrue(flagEncoder instanceof DataFlagEncoder);

        DataFlagEncoder dataFlagEncoder = (DataFlagEncoder)flagEncoder;
        assertTrue(dataFlagEncoder.isStoreHeight());
        assertTrue(dataFlagEncoder.isStoreWeight());
        assertFalse(dataFlagEncoder.isStoreWidth());
    }
}
