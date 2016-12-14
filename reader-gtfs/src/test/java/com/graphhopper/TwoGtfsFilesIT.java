package com.graphhopper;

import com.graphhopper.reader.gtfs.GraphHopperGtfs;
import com.graphhopper.reader.gtfs.GtfsStorage;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.storage.GHDirectory;
import com.graphhopper.storage.GraphHopperStorage;
import com.graphhopper.storage.index.LocationIndex;
import com.graphhopper.util.Helper;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.File;
import java.util.Arrays;

import static com.graphhopper.reader.gtfs.GtfsHelper.time;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

public class TwoGtfsFilesIT {

    private static final String GRAPH_LOC = "target/graphhopperIT-twogtfs";
    private static GraphHopperGtfs graphHopper;

    @BeforeClass
    public static void init() {
        Helper.removeDir(new File(GRAPH_LOC));
        GHDirectory directory = GraphHopperGtfs.createGHDirectory(GRAPH_LOC);

        EncodingManager encodingManager = GraphHopperGtfs.createEncodingManager();
        GtfsStorage gtfsStorage = GraphHopperGtfs.createGtfsStorage();
        GraphHopperStorage graphHopperStorage = GraphHopperGtfs.createOrLoad(directory, encodingManager, gtfsStorage, false, Arrays.asList("files/sample-feed.zip", "files/rnv.zip"));
        LocationIndex locationIndex = GraphHopperGtfs.createOrLoadIndex(directory, graphHopperStorage);

        graphHopper = new GraphHopperGtfs(encodingManager, GraphHopperGtfs.createTranslationMap(), graphHopperStorage, locationIndex, gtfsStorage);
    }

    @Test
    public void testRoute1() {
        final double FROM_LAT = 36.914893, FROM_LON = -116.76821; // NADAV stop
        final double TO_LAT = 36.914944, TO_LON = -116.761472; // NANAA stop
        int expectedWeight = time(6, 49);
        GHRequest ghRequest = new GHRequest(
                FROM_LAT, FROM_LON,
                TO_LAT, TO_LON
        );
        ghRequest.getHints().put(GraphHopperGtfs.EARLIEST_DEPARTURE_TIME_HINT, 0);
        GHResponse route = graphHopper.route(ghRequest);

        assertFalse(route.hasErrors());
        assertEquals(1, route.getAll().size());
        assertEquals("Expected weight == scheduled arrival time", expectedWeight, route.getBest().getRouteWeight(), 0.1);
        assertEquals("Expected travel time == scheduled arrival time", expectedWeight * 1000, route.getBest().getTime(), 0.1);
    }


}
