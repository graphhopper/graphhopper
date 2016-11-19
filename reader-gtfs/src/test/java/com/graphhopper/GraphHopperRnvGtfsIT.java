package com.graphhopper;

import com.graphhopper.reader.gtfs.GraphHopperGtfs;
import com.graphhopper.util.Helper;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.File;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.junit.Assert.*;

public class GraphHopperRnvGtfsIT {

    private static final String GRAPH_LOC = "target/graphhopperIT-rnv-gtfs";
    private static GraphHopperGtfs graphHopper;

    private static final LocalDate GTFS_START_DATE = LocalDate.of(2016, 10, 22);

    @BeforeClass
    public static void init() {
        Helper.removeDir(new File(GRAPH_LOC));

        graphHopper = new GraphHopperGtfs();
        graphHopper.setGtfsFile("files/rnv.zip");
        graphHopper.setCreateWalkNetwork(true);
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
        assertRouteWeightIs(graphHopper, FROM_LAT, FROM_LON, GTFS_START_DATE.atTime(15, 0),
                TO_LAT, TO_LON, GTFS_START_DATE.atTime(15, 17));
    }

    @Test
    public void testRoute2() {
        final double FROM_LAT = 49.4048, FROM_LON = 8.6765; // 116006, HD Hauptbahnhof
        final double TO_LAT = 49.42799, TO_LON = 8.6833; // 113612, Hans-Thoma-Platz
        assertRouteWeightIs(graphHopper, FROM_LAT, FROM_LON, GTFS_START_DATE.atTime(15, 7),
                TO_LAT, TO_LON, GTFS_START_DATE.atTime(15, 21));
    }

    @Test
    public void testRouteAfterMidnight() {
        final double FROM_LAT = 49.4048, FROM_LON = 8.6765; // 116006, HD Hauptbahnhof
        final double TO_LAT = 49.42799, TO_LON = 8.6833; // 113612, Hans-Thoma-Platz
        assertRouteWeightIs(graphHopper, FROM_LAT, FROM_LON, GTFS_START_DATE.plusDays(1).atTime(0, 20),
                TO_LAT, TO_LON, GTFS_START_DATE.plusDays(1).atTime(0, 45));
    }

    @Test
    public void testRouteInSecondNight() {
        final double FROM_LAT = 49.4048, FROM_LON = 8.6765; // 116006, HD Hauptbahnhof
        final double TO_LAT = 49.42799, TO_LON = 8.6833; // 113612, Hans-Thoma-Platz
        assertRouteWeightIs(graphHopper, FROM_LAT, FROM_LON, GTFS_START_DATE.plusDays(2).atTime(0, 20),
                TO_LAT, TO_LON, GTFS_START_DATE.plusDays(2).atTime(0, 45));
    }

    @Test
    public void testTripWithWalk() {
        final double FROM_LAT = 49.49436, FROM_LON = 8.43372; // BASF
        final double TO_LAT = 49.47531, TO_LON = 8.48131; // Krappmuehlstr.
        // Transfer at e.g. Universitaet, where we have to walk to the next stop pole.
        // If we couldn't walk, we would arrive at least one connection later.
        assertRouteWeightIs(graphHopper, FROM_LAT, FROM_LON, GTFS_START_DATE.atTime(19, 40),
                TO_LAT, TO_LON, GTFS_START_DATE.atTime(20, 8));
    }

    @Test
    public void testRouteWithWalkingBeforeAndAfter() {
        final double FROM_LAT = 49.517846, FROM_LON = 8.474073; // Stolberger Straße
        final double TO_LAT = 49.45958, TO_LON = 8.479514; // Freiheitsplatz
        assertRouteWeightIs(graphHopper, FROM_LAT, FROM_LON, GTFS_START_DATE.atTime(21, 36),
                TO_LAT, TO_LON, GTFS_START_DATE.atTime(22, 21, 43));
    }

    @Test
    public void testWeekendRouteWhichFailsIfBusinessDayIsNotRolledOver() {
        final double FROM_LAT = 49.49058, FROM_LON = 8.37085; // Wilhelm-Tell-Str
        final double TO_LAT = 49.41947, TO_LON = 8.66979; // Pädagog. Hochschule
        assertRouteWeightIs(graphHopper, FROM_LAT, FROM_LON, LocalDateTime.of(2016,11,6,0,42),
                TO_LAT, TO_LON, LocalDateTime.of(2016,11,6,2,46));
    }

    @Test
    public void testRouteWithVeryLongTravelTimeAccordingToBahnDe() {
        final double FROM_LAT = 49.442904, FROM_LON = 8.519059; // Sporwoerthplatz
        final double TO_LAT = 49.562158, TO_LON = 8.448643; // Fuellenweg
        assertRouteWeightIs(graphHopper, FROM_LAT, FROM_LON, LocalDateTime.of(2016, 11, 1, 19, 28),
                TO_LAT, TO_LON, LocalDateTime.of(2016,11,1,20,51,2));
    }

    private void assertRouteWeightIs(GraphHopperGtfs graphHopper, double from_lat, double from_lon, LocalDateTime earliestDepartureTime, double to_lat, double to_lon, LocalDateTime expectedArrivalTime) {
        GHRequest ghRequest = new GHRequest(
                from_lat, from_lon,
                to_lat, to_lon
        );
        ghRequest.getHints().put(GraphHopperGtfs.EARLIEST_DEPARTURE_TIME_HINT, getSeconds(earliestDepartureTime));
        GHResponse route = graphHopper.route(ghRequest);
        assertFalse(route.hasErrors());
        assertEquals(expectedArrivalTime, GTFS_START_DATE.atStartOfDay().plusSeconds((long) route.getBest().getRouteWeight()));
        assertNotEquals("Solution doesn't use transit at all.", -1, route.getBest().getNumChanges());

    }

    private long getSeconds(LocalDateTime dateTime) {
        return Duration.between(GTFS_START_DATE.atStartOfDay(), dateTime).getSeconds();
    }

}
