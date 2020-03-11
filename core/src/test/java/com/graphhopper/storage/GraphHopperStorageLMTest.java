package com.graphhopper.storage;

import com.graphhopper.GraphHopper;
import com.graphhopper.config.LMProfileConfig;
import com.graphhopper.config.ProfileConfig;
import com.graphhopper.reader.ReaderWay;
import com.graphhopper.routing.util.CarFlagEncoder;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.routing.util.EncodingManager.Access;
import com.graphhopper.util.Helper;
import org.junit.Test;

import java.io.File;

import static com.graphhopper.util.GHUtility.updateDistancesFor;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class GraphHopperStorageLMTest {
    @Test
    public void testLoad() {
        String defaultGraphLoc = "./target/ghstorage_lm";
        Helper.removeDir(new File(defaultGraphLoc));
        CarFlagEncoder carFlagEncoder = new CarFlagEncoder();
        EncodingManager encodingManager = EncodingManager.create(carFlagEncoder);
        GraphHopperStorage graph = GraphBuilder.start(encodingManager).setRAM(defaultGraphLoc, true).create();

        // 0-1
        ReaderWay way_0_1 = new ReaderWay(27l);
        way_0_1.setTag("highway", "primary");
        way_0_1.setTag("maxheight", "4.4");

        graph.edge(0, 1, 1, true);
        updateDistancesFor(graph, 0, 0.00, 0.00);
        updateDistancesFor(graph, 1, 0.01, 0.01);
        graph.getEdgeIteratorState(0, 1).setFlags(
                carFlagEncoder.handleWayTags(encodingManager.createEdgeFlags(), way_0_1, Access.WAY));

        // 1-2
        ReaderWay way_1_2 = new ReaderWay(28l);
        way_1_2.setTag("highway", "primary");
        way_1_2.setTag("maxweight", "45");

        graph.edge(1, 2, 1, true);
        updateDistancesFor(graph, 2, 0.02, 0.02);
        graph.getEdgeIteratorState(1, 2).setFlags(
                carFlagEncoder.handleWayTags(encodingManager.createEdgeFlags(), way_1_2, Access.WAY));

        graph.flush();
        graph.close();

        GraphHopper hopper = new GraphHopper()
                .setGraphHopperLocation(defaultGraphLoc)
                .setProfiles(new ProfileConfig("my_profile").setVehicle("car").setWeighting("fastest"));
        hopper.getLMPreparationHandler().setLMProfileConfigs(new LMProfileConfig("my_profile"));
        // does lm preparation
        hopper.importOrLoad();
        EncodingManager em = hopper.getEncodingManager();
        assertNotNull(em);
        assertEquals(1, em.fetchEdgeEncoders().size());
        assertEquals(16, hopper.getLMPreparationHandler().getLandmarks());

        hopper = new GraphHopper()
                .setGraphHopperLocation(defaultGraphLoc)
                .setProfiles(new ProfileConfig("my_profile").setVehicle("car").setWeighting("fastest"));
        hopper.getLMPreparationHandler().setLMProfileConfigs(new LMProfileConfig("my_profile"));
        // just loads the LM data
        hopper.importOrLoad();
        assertEquals(1, em.fetchEdgeEncoders().size());
        assertEquals(16, hopper.getLMPreparationHandler().getLandmarks());
    }
}
