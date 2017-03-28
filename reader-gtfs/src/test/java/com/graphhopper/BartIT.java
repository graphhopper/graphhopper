package com.graphhopper;

import com.graphhopper.reader.gtfs.GraphHopperGtfs;
import com.graphhopper.util.Helper;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.File;
import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.Assert.assertEquals;

public class BartIT {

    private static final String GRAPH_LOC = "target/bartIT";

    private static GraphHopperGtfs graphHopper;

    @BeforeClass
    public static void init() {
        Helper.removeDir(new File(GRAPH_LOC));
        graphHopper = GraphHopperGtfs.createGraphHopperGtfs(GRAPH_LOC, "files/bart.zip", false);
    }

    @Test
    public void testFare() {
        final double FROM_LAT = 37.724293, FROM_LON = -122.162647; // SANL stop
        final double TO_LAT = 37.852898, TO_LON = -122.2699356; // ASHB stop
        GHRequest request = new GHRequest(
                FROM_LAT, FROM_LON,
                TO_LAT, TO_LON
        );
        request.getHints().put(GraphHopperGtfs.EARLIEST_DEPARTURE_TIME_HINT, LocalDateTime.of(2017,3,28,13,1).toString());
        GHResponse response = graphHopper.route(request);
        assertEquals("Paid expected fare", 105, response.getBest().getFare().multiply(BigDecimal.valueOf(100)).intValue());
    }


}
