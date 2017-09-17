package com.graphhopper.storage;

import com.graphhopper.GraphHopper;
import com.graphhopper.reader.ReaderWay;
import com.graphhopper.routing.AbstractRoutingAlgorithmTester;
import com.graphhopper.routing.profiles.BooleanEncodedValue;
import com.graphhopper.routing.profiles.DecimalEncodedValue;
import com.graphhopper.routing.profiles.TagParserFactory;
import com.graphhopper.routing.util.CarFlagEncoder;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.util.GHUtility;
import com.graphhopper.util.Helper;
import org.junit.Test;

import java.io.File;
import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class GraphHopperStorageLMTest {
    @Test
    public void testLoad() {
        String defaultGraphLoc = "./target/ghstorage_lm";
        Helper.removeDir(new File(defaultGraphLoc));
        CarFlagEncoder carFlagEncoder = new CarFlagEncoder();
        EncodingManager encodingManager = new EncodingManager.Builder().addGlobalEncodedValues().addAll(carFlagEncoder).build();
        GraphHopperStorage graph = new GraphBuilder(encodingManager).setStore(true).
                setLocation(defaultGraphLoc).create();

        // 0-1
        ReaderWay way_0_1 = new ReaderWay(27l);
        way_0_1.setTag("highway", "primary");
        way_0_1.setTag("maxheight", "4.4");

        BooleanEncodedValue accessEnc = encodingManager.getBooleanEncodedValue(TagParserFactory.CAR_ACCESS);
        DecimalEncodedValue avSpeedEnc = encodingManager.getDecimalEncodedValue(TagParserFactory.CAR_AVERAGE_SPEED);
        GHUtility.createEdge(graph, avSpeedEnc, 60, accessEnc, 0, 1, true, 1);
        AbstractRoutingAlgorithmTester.updateDistancesFor(graph, 0, 0.00, 0.00);
        AbstractRoutingAlgorithmTester.updateDistancesFor(graph, 1, 0.01, 0.01);
        graph.getEdgeIteratorState(0, 1).setData(carFlagEncoder.handleWayTags(encodingManager.createIntsRef(), way_0_1, EncodingManager.Access.WAY, 0));

        // 1-2
        ReaderWay way_1_2 = new ReaderWay(28l);
        way_1_2.setTag("highway", "primary");
        way_1_2.setTag("maxweight", "45");

        GHUtility.createEdge(graph, avSpeedEnc, 60, accessEnc, 1, 2, true, 1);
        AbstractRoutingAlgorithmTester.updateDistancesFor(graph, 2, 0.02, 0.02);
        graph.getEdgeIteratorState(1, 2).setData(carFlagEncoder.handleWayTags(encodingManager.createIntsRef(), way_1_2, EncodingManager.Access.WAY, 0));

        graph.flush();
        graph.close();

        GraphHopper hopper = new GraphHopper().setGraphHopperLocation(defaultGraphLoc).setCHEnabled(false);
        hopper.getLMFactoryDecorator().setEnabled(true).setWeightingsAsStrings(Arrays.asList("fastest"));
        // does lm preparation
        hopper.importOrLoad();
        EncodingManager em = hopper.getEncodingManager();
        assertNotNull(em);
        assertEquals(1, em.fetchEdgeEncoders().size());
        assertEquals(16, hopper.getLMFactoryDecorator().getLandmarks());

        hopper = new GraphHopper().setGraphHopperLocation(defaultGraphLoc).setCHEnabled(false);
        hopper.getLMFactoryDecorator().setEnabled(true).setWeightingsAsStrings(Arrays.asList("fastest"));
        // just loads the LM data
        hopper.importOrLoad();
        assertEquals(1, em.fetchEdgeEncoders().size());
        assertEquals(16, hopper.getLMFactoryDecorator().getLandmarks());
    }
}
