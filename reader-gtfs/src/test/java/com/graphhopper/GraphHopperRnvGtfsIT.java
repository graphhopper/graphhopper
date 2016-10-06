package com.graphhopper;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import java.io.File;

import org.junit.Test;

import com.graphhopper.reader.gtfs.GraphHopperGtfs;
import com.graphhopper.util.Helper;
import static com.graphhopper.reader.gtfs.GtfsHelper.time;
import org.junit.AfterClass;
import org.junit.BeforeClass;

public class GraphHopperRnvGtfsIT {

    private static final String GRAPH_LOC = "target/graphhopperIT-rnv-gtfs";
    private static GraphHopperGtfs graphHopper;

    @BeforeClass
    public static void init() {
        Helper.removeDir(new File(GRAPH_LOC));

        graphHopper = new GraphHopperGtfs();
        graphHopper.setGtfsFile("files/rnv.zip");
        graphHopper.setGraphHopperLocation(GRAPH_LOC);
        graphHopper.importOrLoad();
    }

    @AfterClass
    public static void tearDown() {
        if (graphHopper != null)
            graphHopper.close();
    }

    @Test
    public void testRoute1() {
        final double FROM_LAT = 49.4048, FROM_LON = 8.6765; // 116006, HD Hauptbahnhof
        final double TO_LAT = 49.42799, TO_LON = 8.6833; // 113612, Hans-Thoma-Platz
        assertRouteWeightIs(graphHopper, FROM_LAT, FROM_LON, time(15, 0),
                TO_LAT, TO_LON, time(15, 15));
    }

    @Test
    public void testRoute2() {
        final double FROM_LAT = 49.4048, FROM_LON = 8.6765; // 116006, HD Hauptbahnhof
        final double TO_LAT = 49.42799, TO_LON = 8.6833; // 113612, Hans-Thoma-Platz
        assertRouteWeightIs(graphHopper, FROM_LAT, FROM_LON, time(15, 7),
                TO_LAT, TO_LON, time(15, 21));
    }

    @Test
    public void testRouteAfterMidnight() {
        final double FROM_LAT = 49.4048, FROM_LON = 8.6765; // 116006, HD Hauptbahnhof
        final double TO_LAT = 49.42799, TO_LON = 8.6833; // 113612, Hans-Thoma-Platz
        assertRouteWeightIs(graphHopper, FROM_LAT, FROM_LON, time(24, 0),
                TO_LAT, TO_LON, time(24, 45));
    }

    @Test
    public void testTrip209701_2() {
        // 113311,Burgstr.,49.434352,8.682286,,0
        final double FROM_LAT = 49.434352, FROM_LON = 8.682286;
        // 113211,Biethsstr.,49.42987,8.68252,,0
        final double TO_LAT = 49.42987, TO_LON = 8.68252;
        assertRouteWeightIs(graphHopper, FROM_LAT, FROM_LON, time(7, 51),
                TO_LAT, TO_LON, time(7, 52));
    }

    @Test
    public void testTrip209701_10() {
        // 113311,Burgstr.,49.434352,8.682286,,0
        final double FROM_LAT = 49.434352, FROM_LON = 8.682286;
        // 119302,Stadtbücherei,49.4061,8.68601,,0
        final double TO_LAT = 49.4061, TO_LON = 8.68601;
        assertRouteWeightIs(graphHopper, FROM_LAT, FROM_LON, time(7, 51),
                TO_LAT, TO_LON, time(8, 6));
    }

    @Test
    public void testTrip209701_28() {
        // 113311,Burgstr.,49.434352,8.682286,,0
        final double FROM_LAT = 49.434352, FROM_LON = 8.682286;
        // 187901,Leimen Friedhof,49.34345,8.69311,,0
        final double TO_LAT = 49.34345, TO_LON = 8.69311;
        assertRouteWeightIs(graphHopper, FROM_LAT, FROM_LON, time(7, 51),
                TO_LAT, TO_LON, time(8, 29));
    }

    private void assertRouteWeightIs(GraphHopperGtfs graphHopper, double from_lat, double from_lon, int earliestDepartureTime, double to_lat, double to_lon, int expectedWeight) {
        GHRequest ghRequest = new GHRequest(
                from_lat, from_lon,
                to_lat, to_lon
        );
        ghRequest.getHints().put(GraphHopperGtfs.EARLIEST_DEPARTURE_TIME_HINT, earliestDepartureTime);
        GHResponse route = graphHopper.route(ghRequest);
        System.out.println(route);
        System.out.println(route.getBest());
        System.out.println(route.getBest().getDebugInfo());

        assertFalse(route.hasErrors());
        assertEquals("Expected weight == scheduled arrival time", expectedWeight, route.getBest().getRouteWeight(), 0.1);
    }
}
