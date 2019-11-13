package com.graphhopper.storage;

import com.graphhopper.GraphHopper;
import com.graphhopper.reader.ReaderWay;
import com.graphhopper.routing.util.DataFlagEncoder;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.routing.util.EncodingManager.Access;
import com.graphhopper.routing.util.FlagEncoder;
import com.graphhopper.routing.util.parsers.OSMMaxHeightParser;
import com.graphhopper.routing.util.parsers.OSMMaxWeightParser;
import com.graphhopper.routing.util.parsers.OSMMaxWidthParser;
import com.graphhopper.util.GHUtility;
import com.graphhopper.util.Helper;
import com.graphhopper.util.PMap;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;

import static com.graphhopper.util.GHUtility.updateDistancesFor;
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

    public GraphHopperStorageForDataFlagEncoderTest() {
        properties = new PMap();
        encoder = new DataFlagEncoder(properties);
        encodingManager = GHUtility.addDefaultEncodedValues(new EncodingManager.Builder()).
                add(new OSMMaxWidthParser()).add(new OSMMaxHeightParser()).add(new OSMMaxWeightParser()).add(encoder).build();
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
        graph = new GraphBuilder(encodingManager).setRAM(defaultGraphLoc, true).create();

        // 0-1
        ReaderWay way_0_1 = new ReaderWay(27l);
        way_0_1.setTag("highway", "primary");
        way_0_1.setTag("maxheight", "4.4");

        graph.edge(0, 1, 1, true);
        updateDistancesFor(graph, 0, 0.00, 0.00);
        updateDistancesFor(graph, 1, 0.01, 0.01);
        EncodingManager.AcceptWay map = new EncodingManager.AcceptWay().put(encoder.toString(), Access.WAY);
        graph.getEdgeIteratorState(0, 1).setFlags(encodingManager.handleWayTags(way_0_1, map, 0));

        // 1-2
        ReaderWay way_1_2 = new ReaderWay(28l);
        way_1_2.setTag("highway", "primary");
        way_1_2.setTag("maxweight", "45");

        graph.edge(1, 2, 1, true);
        updateDistancesFor(graph, 2, 0.02, 0.02);
        graph.getEdgeIteratorState(1, 2).setFlags(encodingManager.handleWayTags(way_1_2, map, 0));

        // 2-0
        ReaderWay way_2_0 = new ReaderWay(29l);
        way_2_0.setTag("highway", "primary");
        way_2_0.setTag("maxwidth", "5");

        graph.edge(2, 0, 1, true);
        graph.getEdgeIteratorState(2, 0).setFlags(encodingManager.handleWayTags(way_2_0, map, 0));

        graph.flush();
        graph.close();

        GraphHopper hopper = new GraphHopper().setGraphHopperLocation(defaultGraphLoc).setCHEnabled(false).importOrLoad();
        EncodingManager em = hopper.getEncodingManager();
        assertNotNull(em);
        assertEquals(1, em.fetchEdgeEncoders().size());

        FlagEncoder flagEncoder = em.fetchEdgeEncoders().get(0);
        assertTrue(flagEncoder instanceof DataFlagEncoder);
    }
}
