package com.graphhopper.storage;

import com.graphhopper.GraphHopper;
import com.graphhopper.reader.ReaderWay;
import com.graphhopper.routing.AbstractRoutingAlgorithmTester;
import com.graphhopper.routing.profiles.BooleanEncodedValue;
import com.graphhopper.routing.profiles.DecimalEncodedValue;
import com.graphhopper.routing.util.DataFlagEncoder;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.routing.util.FlagEncoder;
import com.graphhopper.util.GHUtility;
import com.graphhopper.util.Helper;
import com.graphhopper.util.PMap;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.util.Arrays;

import static org.junit.Assert.*;

/**
 * Created by tuan on 20/01/17.
 */
public class GraphHopperStorageForDataFlagEncoderTest {
    private final String locationParent = "./target/graphstorage";
    private String defaultGraphLoc = "./target/graphstorage/default";
    private GraphHopperStorage graph;

    private final PMap properties;
    private final DataFlagEncoder encoder;
    private final EncodingManager encodingManager;
    private final BooleanEncodedValue accessEnc;
    private final DecimalEncodedValue avSpeedEnc;

    public GraphHopperStorageForDataFlagEncoderTest() {
        properties = new PMap();
        properties.put("store_height", true);
        properties.put("store_weight", true);
        properties.put("store_width", false);
        encoder = new DataFlagEncoder(properties);
        encodingManager = new EncodingManager.Builder().addGlobalEncodedValues().addAll(Arrays.asList(encoder), 8).build();
        accessEnc = encodingManager.getBooleanEncodedValue("generic." + "access");
        avSpeedEnc = encodingManager.getDecimalEncodedValue("generic." + "average_speed");

    }

    @Before
    public void setUp() {
        Helper.removeDir(new File(locationParent));
    }

    @After
    public void tearDown() {
        Helper.close(graph);
        Helper.removeDir(new File(locationParent));
    }

    @Test
    public void testStorageProperties() {
        graph = new GraphBuilder(encodingManager).setStore(true).setLocation(defaultGraphLoc).create();

        // 0-1
        ReaderWay way_0_1 = new ReaderWay(27l);
        way_0_1.setTag("highway", "primary");
        way_0_1.setTag("maxheight", "4.4");

        GHUtility.createEdge(graph, avSpeedEnc, 60, accessEnc, 0, 1, true, 1);
        AbstractRoutingAlgorithmTester.updateDistancesFor(graph, 0, 0.00, 0.00);
        AbstractRoutingAlgorithmTester.updateDistancesFor(graph, 1, 0.01, 0.01);
        graph.getEdgeIteratorState(0, 1).setData(encoder.handleWayTags(encodingManager.createIntsRef(), way_0_1, EncodingManager.Access.WAY, 0));

        // 1-2
        ReaderWay way_1_2 = new ReaderWay(28l);
        way_1_2.setTag("highway", "primary");
        way_1_2.setTag("maxweight", "45");

        GHUtility.createEdge(graph, avSpeedEnc, 60, accessEnc, 1, 2, true, 1);
        AbstractRoutingAlgorithmTester.updateDistancesFor(graph, 2, 0.02, 0.02);
        graph.getEdgeIteratorState(1, 2).setData(encoder.handleWayTags(encodingManager.createIntsRef(), way_1_2, EncodingManager.Access.WAY, 0));

        // 2-0
        ReaderWay way_2_0 = new ReaderWay(29l);
        way_2_0.setTag("highway", "primary");
        way_2_0.setTag("maxwidth", "5");

        GHUtility.createEdge(graph, avSpeedEnc, 60, accessEnc, 2, 0, true, 1);
        graph.getEdgeIteratorState(2, 0).setData(encoder.handleWayTags(encodingManager.createIntsRef(), way_2_0, EncodingManager.Access.WAY, 0));

        graph.flush();
        graph.close();

        GraphHopper hopper = new GraphHopper().setGraphHopperLocation(defaultGraphLoc).setCHEnabled(false).importOrLoad();
        EncodingManager em = hopper.getEncodingManager();
        assertNotNull(em);
        assertEquals(1, em.fetchEdgeEncoders().size());

        FlagEncoder flagEncoder = em.fetchEdgeEncoders().get(0);
        assertTrue(flagEncoder instanceof DataFlagEncoder);

        DataFlagEncoder dataFlagEncoder = (DataFlagEncoder) flagEncoder;
        assertTrue(dataFlagEncoder.isStoreHeight());
        assertTrue(dataFlagEncoder.isStoreWeight());
        assertFalse(dataFlagEncoder.isStoreWidth());
    }
}
