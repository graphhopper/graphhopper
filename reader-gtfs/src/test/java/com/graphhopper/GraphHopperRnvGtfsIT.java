package com.graphhopper;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import java.io.File;

import org.junit.Before;
import org.junit.Test;

import com.graphhopper.reader.gtfs.GraphHopperGtfs;
import com.graphhopper.util.Helper;
import static com.graphhopper.reader.gtfs.GtfsHelper.time;

public class GraphHopperRnvGtfsIT {
    
    private static final String graphFileFoot = "target/graphhopperIT-foot";
    private GraphHopperGtfs graphHopper;
    
    @Before
    public void init() {
        Helper.removeDir(new File(graphFileFoot));

        graphHopper = new GraphHopperGtfs();
        graphHopper.setCHEnabled(false);
        graphHopper.setGtfsFile("files/rnv.zip");
        graphHopper.setGraphHopperLocation(graphFileFoot);
        graphHopper.importOrLoad();
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
    public void testRoute4() {
        // 556311,Heddesheim Bahnhof,49.504812,8.594176,,0
        final double FROM_LAT = 49.504812, FROM_LON = 8.594176;
//      187901,Leimen Friedhof,49.34345,8.69311,,0
        final double TO_LAT = 49.34345, TO_LON = 8.69311;
        // TODO should be 09:17
        assertRouteWeightIs(graphHopper, FROM_LAT, FROM_LON, time(7, 50),
                        TO_LAT, TO_LON, time(9, 29));
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

    private void assertRouteWeightIs(GraphHopperGtfs graphHopper, double FROM_LAT, double FROM_LON, double TO_LAT, double TO_LON, int expectedWeight) {
        assertRouteWeightIs(graphHopper, FROM_LAT, FROM_LON, 0, TO_LAT, TO_LON, expectedWeight);
    }


}
