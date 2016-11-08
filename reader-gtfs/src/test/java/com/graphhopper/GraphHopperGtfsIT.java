package com.graphhopper;

import com.graphhopper.reader.gtfs.GraphHopperGtfs;
import com.graphhopper.util.Helper;
import org.junit.Assert;
import org.junit.Test;

import java.io.File;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static com.graphhopper.reader.gtfs.GtfsHelper.time;
import org.junit.AfterClass;
import org.junit.BeforeClass;

public class GraphHopperGtfsIT {

    private static final String GRAPH_LOC = "target/graphhopperIT-gtfs";
    private static GraphHopperGtfs graphHopper;

    @BeforeClass
    public static void init() {
        Helper.removeDir(new File(GRAPH_LOC));

        graphHopper = new GraphHopperGtfs();
        graphHopper.setGtfsFile("files/sample-feed.zip");
        graphHopper.setGraphHopperLocation(GRAPH_LOC);
        graphHopper.importOrLoad();
    }

    @AfterClass
    public static void tearDown() {
        if (graphHopper != null)
            graphHopper.close();
    }

    @Test
    public void testFromMeToMyself() {
        final double FROM_LAT = 36.914893, FROM_LON = -116.76821; // NADAV stop
        final double TO_LAT = 36.914893, TO_LON = -116.76821; // NADAV stop
        assertRouteWeightIs(graphHopper, FROM_LAT, FROM_LON, TO_LAT, TO_LON, time(0, 0));
    }

    @Test
    public void testRoute1() {
        final double FROM_LAT = 36.914893, FROM_LON = -116.76821; // NADAV stop
        final double TO_LAT = 36.914944, TO_LON = -116.761472; // NANAA stop
        assertRouteWeightIs(graphHopper, FROM_LAT, FROM_LON, TO_LAT, TO_LON, time(6, 49));
    }

    @Test
    public void testRoute2() {
        final double FROM_LAT = 36.914894, FROM_LON = -116.76821; // NADAV stop
        final double TO_LAT = 36.909489, TO_LON = -116.768242; // DADAN stop
        assertRouteWeightIs(graphHopper, FROM_LAT, FROM_LON, TO_LAT, TO_LON, time(6, 19));
    }

    @Test
    public void testRoute3() {
        final double FROM_LAT = 36.915682, FROM_LON = -116.751677; // STAGECOACH stop
        final double TO_LAT = 36.914944, TO_LON = -116.761472; // NANAA stop
        assertRouteWeightIs(graphHopper, FROM_LAT, FROM_LON, TO_LAT, TO_LON, time(6, 5));
    }

    @Test
    public void testRoute4() {
        final double FROM_LAT = 36.915682, FROM_LON = -116.751677; // STAGECOACH stop
        final double TO_LAT = 36.914894, TO_LON = -116.76821; // NADAV stop
        assertRouteWeightIs(graphHopper, FROM_LAT, FROM_LON, TO_LAT, TO_LON, time(6, 12));
    }

    @Test
    public void testRoute5() {
        final double FROM_LAT = 36.915682, FROM_LON = -116.751677; // STAGECOACH stop
        final double TO_LAT = 36.88108, TO_LON = -116.81797; // BULLFROG stop
        assertRouteWeightIs(graphHopper, FROM_LAT, FROM_LON, TO_LAT, TO_LON, time(8, 10));
    }

    @Test
    public void testRoute6() {
        final double FROM_LAT = 36.88108, FROM_LON = -116.81797; // BULLFROG stop
        final double TO_LAT = 36.914894, TO_LON = -116.76821; // NADAV stop
        assertNoRoute(graphHopper, FROM_LAT, FROM_LON, TO_LAT, TO_LON);
    }

    @Test
    public void testRoute7() {
        final double FROM_LAT = 36.915682, FROM_LON = -116.751677; // STAGECOACH stop
        final double TO_LAT = 36.914894, TO_LON = -116.76821; // NADAV stop
        // Missed the bus at 10 by one minute, will have to use the 10:30 one.
        assertRouteWeightIs(graphHopper, FROM_LAT, FROM_LON, time(10, 1), TO_LAT, TO_LON, time(10, 42));
    }

    @Test
    public void testWeekendRouteWorksOnlyOnWeekend() {
        final double FROM_LAT = 36.868446, FROM_LON = -116.784582; // BEATTY_AIRPORT stop
        final double TO_LAT = 36.641496, TO_LON = -116.40094; // AMV stop
        GHRequest ghRequest = new GHRequest(
                FROM_LAT, FROM_LON,
                TO_LAT, TO_LON
        );
        ghRequest.getHints().put(GraphHopperGtfs.EARLIEST_DEPARTURE_TIME_HINT, time(0, 0)); // Monday morning

        GHResponse route = graphHopper.route(ghRequest);
        Assert.assertTrue(route.getAll().isEmpty()); // No service on monday morning, and we cannot spend the night at stations yet

        assertRouteWeightIs(graphHopper, FROM_LAT, FROM_LON, time(5 * 24, 0), TO_LAT, TO_LON, time(5 * 24 + 9, 0)); // Saturday morning

    }

    private void assertRouteWeightIs(GraphHopperGtfs graphHopper, double from_lat, double from_lon, int earliestDepartureTime, double to_lat, double to_lon, int expectedWeight) {
        GHRequest ghRequest = new GHRequest(
                from_lat, from_lon,
                to_lat, to_lon
        );
        ghRequest.getHints().put(GraphHopperGtfs.EARLIEST_DEPARTURE_TIME_HINT, earliestDepartureTime);
        GHResponse route = graphHopper.route(ghRequest);

        assertFalse(route.hasErrors());
        assertFalse(route.getAll().isEmpty());
        assertEquals("Expected weight == scheduled arrival time", expectedWeight, route.getBest().getRouteWeight(), 0.1);
        assertEquals("Expected travel time == scheduled arrival time", expectedWeight * 1000, route.getBest().getTime(), 0.1);
    }

    private void assertRouteWeightIs(GraphHopperGtfs graphHopper, double FROM_LAT, double FROM_LON, double TO_LAT, double TO_LON, int expectedWeight) {
        assertRouteWeightIs(graphHopper, FROM_LAT, FROM_LON, 0, TO_LAT, TO_LON, expectedWeight);
    }

    private void assertNoRoute(GraphHopperGtfs graphHopper, double from_lat, double from_lon, double to_lat, double to_lon) {
        GHRequest ghRequest = new GHRequest(
                from_lat, from_lon,
                to_lat, to_lon
        );

        GHResponse route = graphHopper.route(ghRequest);
        Assert.assertTrue(route.getAll().isEmpty());
    }

}
