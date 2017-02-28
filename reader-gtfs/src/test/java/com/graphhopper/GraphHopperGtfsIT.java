package com.graphhopper;

import com.graphhopper.reader.gtfs.GraphHopperGtfs;
import com.graphhopper.util.Helper;
import org.junit.Assert;
import org.junit.Test;

import java.io.File;
import java.math.BigDecimal;
import java.time.LocalDateTime;

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
        graphHopper = GraphHopperGtfs.createGraphHopperGtfs(GRAPH_LOC, "files/sample-feed.zip", false);
    }

    @AfterClass
    public static void tearDown() {
//        if (graphHopper != null)
//            graphHopper.close();
    }

    @Test
    public void testRoute1() {
        final double FROM_LAT = 36.914893, FROM_LON = -116.76821; // NADAV stop
        final double TO_LAT = 36.914944, TO_LON = -116.761472; // NANAA stop
        GHRequest ghRequest = new GHRequest(
                FROM_LAT, FROM_LON,
                TO_LAT, TO_LON
        );
        ghRequest.getHints().put(GraphHopperGtfs.EARLIEST_DEPARTURE_TIME_HINT, LocalDateTime.of(2007,1,1,0,0).toString());
        GHResponse route = graphHopper.route(ghRequest);

        assertFalse(route.hasErrors());
        assertEquals(1, route.getAll().size());
        assertEquals("Expected travel time == scheduled arrival time", time(6, 49), route.getBest().getTime(), 0.1);
    }

    @Test
    public void testRoute1ArriveBy() {
        final double FROM_LAT = 36.914893, FROM_LON = -116.76821; // NADAV stop
        final double TO_LAT = 36.914944, TO_LON = -116.761472; // NANAA stop
        GHRequest ghRequest = new GHRequest(
                FROM_LAT, FROM_LON,
                TO_LAT, TO_LON
        );
        ghRequest.getHints().put(GraphHopperGtfs.EARLIEST_DEPARTURE_TIME_HINT, LocalDateTime.of(2007,1,1,6, 49));
        ghRequest.getHints().put(GraphHopperGtfs.ARRIVE_BY, true);

        GHResponse route = graphHopper.route(ghRequest);

        assertFalse(route.hasErrors());
        assertEquals(1, route.getAll().size());
        assertEquals("Expected travel time == scheduled travel time", time(0, 5), route.getBest().getTime(), 0.1);

    }

    @Test
    public void testRoute1Profile() {
        final double FROM_LAT = 36.914893, FROM_LON = -116.76821; // NADAV stop
        final double TO_LAT = 36.914944, TO_LON = -116.761472; // NANAA stop
        GHRequest ghRequest = new GHRequest(
                FROM_LAT, FROM_LON,
                TO_LAT, TO_LON
        );
        ghRequest.getHints().put(GraphHopperGtfs.EARLIEST_DEPARTURE_TIME_HINT, LocalDateTime.of(2007,1,1,0,0));
        ghRequest.getHints().put(GraphHopperGtfs.RANGE_QUERY_END_TIME, LocalDateTime.of(2007,1,1,13,0));
        ghRequest.getHints().put(GraphHopperGtfs.IGNORE_TRANSFERS, "true");

        GHResponse route = graphHopper.route(ghRequest);

        assertFalse(route.hasErrors());
        assertEquals(24, route.getAll().size());
        assertEquals("Expected travel time == scheduled arrival time", time(6, 49), route.getBest().getTime(), 0.1);
    }

    @Test
    public void testRoute2() {
        final double FROM_LAT = 36.914894, FROM_LON = -116.76821; // NADAV stop
        final double TO_LAT = 36.909489, TO_LON = -116.768242; // DADAN stop
        assertTravelTimeIs(graphHopper, FROM_LAT, FROM_LON, TO_LAT, TO_LON, time(6, 19));
    }

    @Test
    public void testRoute3() {
        final double FROM_LAT = 36.915682, FROM_LON = -116.751677; // STAGECOACH stop
        final double TO_LAT = 36.914944, TO_LON = -116.761472; // NANAA stop
        assertTravelTimeIs(graphHopper, FROM_LAT, FROM_LON, TO_LAT, TO_LON, time(6, 5));
    }

    @Test
    public void testRoute4() {
        final double FROM_LAT = 36.915682, FROM_LON = -116.751677; // STAGECOACH stop
        final double TO_LAT = 36.914894, TO_LON = -116.76821; // NADAV stop
        assertTravelTimeIs(graphHopper, FROM_LAT, FROM_LON, TO_LAT, TO_LON, time(6, 12));
    }

    @Test
    public void testRoute5() {
        final double FROM_LAT = 36.915682, FROM_LON = -116.751677; // STAGECOACH stop
        final double TO_LAT = 36.88108, TO_LON = -116.81797; // BULLFROG stop
        GHRequest ghRequest = new GHRequest(
                FROM_LAT, FROM_LON,
                TO_LAT, TO_LON
        );
        ghRequest.getHints().put(GraphHopperGtfs.EARLIEST_DEPARTURE_TIME_HINT, LocalDateTime.of(2007,1,1,0,0));
        GHResponse route = graphHopper.route(ghRequest);

        assertFalse(route.hasErrors());
        assertFalse(route.getAll().isEmpty());
        assertEquals("Expected travel time == scheduled travel time", time(8, 10), route.getBest().getTime(), 0.1);
        assertEquals("Using expected route", "STBA", (((Trip.PtLeg) route.getBest().getLegs().get(1)).routeId));
        assertEquals("Paid expected fare", 250, route.getBest().getFare().multiply(BigDecimal.valueOf(100)).intValue()); // Two legs, no transfers allowed. Need two 'p' tickets costing 125 cents each.
    }

    @Test
    public void testRoute6() {
        final double FROM_LAT = 36.88108, FROM_LON = -116.81797; // BULLFROG stop
        final double TO_LAT = 36.914894, TO_LON = -116.76821; // NADAV stop
        assertNoRoute(graphHopper, FROM_LAT, FROM_LON, TO_LAT, TO_LON);
    }

    @Test
    public void testRouteWithLaterDepartureTime() {
        final double FROM_LAT = 36.915682, FROM_LON = -116.751677; // STAGECOACH stop
        final double TO_LAT = 36.914894, TO_LON = -116.76821; // NADAV stop
        // Missed the bus at 10 by one minute, will have to use the 10:30 one.
        assertTravelTimeIs(graphHopper, FROM_LAT, FROM_LON, LocalDateTime.of(2007,1,1,10, 1), TO_LAT, TO_LON, time(0, 41));
    }

    @Test
    public void testWeekendRouteWorksOnlyOnWeekend() {
        final double FROM_LAT = 36.868446, FROM_LON = -116.784582; // BEATTY_AIRPORT stop
        final double TO_LAT = 36.641496, TO_LON = -116.40094; // AMV stop
        GHRequest ghRequest = new GHRequest(
                FROM_LAT, FROM_LON,
                TO_LAT, TO_LON
        );
        ghRequest.getHints().put(GraphHopperGtfs.EARLIEST_DEPARTURE_TIME_HINT, LocalDateTime.of(2007,1,1,0,0)); // Monday morning

        GHResponse route = graphHopper.route(ghRequest);
        Assert.assertTrue(route.getAll().isEmpty()); // No service on monday morning, and we cannot spend the night at stations yet

        GHRequest ghRequest1 = new GHRequest(
                FROM_LAT, FROM_LON,
                TO_LAT, TO_LON
        );
        ghRequest1.getHints().put(GraphHopperGtfs.EARLIEST_DEPARTURE_TIME_HINT, LocalDateTime.of(2007,1,6,0,0));
        GHResponse route1 = graphHopper.route(ghRequest1);

        assertFalse(route1.hasErrors());
        assertFalse(route1.getAll().isEmpty());
        assertEquals("Expected travel time == scheduled travel time", time(9, 0), route1.getBest().getTime(), 0.1);
        assertEquals("Using expected route", "AAMV", (((Trip.PtLeg) route1.getBest().getLegs().get(1)).routeId));
        assertEquals("Paid expected fare", 525, route1.getBest().getFare().multiply(BigDecimal.valueOf(100)).intValue());

    }

    private void assertTravelTimeIs(GraphHopperGtfs graphHopper, double from_lat, double from_lon, LocalDateTime earliestDepartureTime, double to_lat, double to_lon, int expectedTravelTime) {
        GHRequest ghRequest = new GHRequest(
                from_lat, from_lon,
                to_lat, to_lon
        );
        ghRequest.getHints().put(GraphHopperGtfs.EARLIEST_DEPARTURE_TIME_HINT, earliestDepartureTime);
        GHResponse route = graphHopper.route(ghRequest);

        assertFalse(route.hasErrors());
        assertFalse(route.getAll().isEmpty());
        assertEquals("Expected travel time == scheduled travel time", expectedTravelTime, route.getBest().getTime(), 0.1);
    }

    private void assertTravelTimeIs(GraphHopperGtfs graphHopper, double FROM_LAT, double FROM_LON, double TO_LAT, double TO_LON, int expectedWeight) {
        assertTravelTimeIs(graphHopper, FROM_LAT, FROM_LON, LocalDateTime.of(2007,1,1,0,0), TO_LAT, TO_LON, expectedWeight);
    }

    private void assertNoRoute(GraphHopperGtfs graphHopper, double from_lat, double from_lon, double to_lat, double to_lon) {
        GHRequest ghRequest = new GHRequest(
                from_lat, from_lon,
                to_lat, to_lon
        );
        ghRequest.getHints().put(GraphHopperGtfs.EARLIEST_DEPARTURE_TIME_HINT, LocalDateTime.of(2007,1,1,0,0).toString());

        GHResponse route = graphHopper.route(ghRequest);
        Assert.assertTrue(route.getAll().isEmpty());
    }

}
