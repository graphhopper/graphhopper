package com.graphhopper;

import com.conveyal.gtfs.GTFSFeed;
import com.conveyal.gtfs.stats.FeedStats;
import com.graphhopper.reader.gtfs.GraphHopperGtfs;
import com.graphhopper.reader.gtfs.GtfsStorage;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.storage.GHDirectory;
import com.graphhopper.storage.GraphHopperStorage;
import com.graphhopper.storage.index.LocationIndex;
import com.graphhopper.util.Helper;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.awt.geom.Rectangle2D;
import java.io.File;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashMap;

import static org.junit.Assert.*;

public class GraphHopperRnvGtfsIT {

    private static final String GRAPH_LOC = "target/graphhopperIT-rnv-gtfs";
    private static GraphHopperGtfs graphHopper;

    private static final LocalDate GTFS_START_DATE = LocalDate.of(2016, 10, 22);

    @BeforeClass
    public static void init() {
        Helper.removeDir(new File(GRAPH_LOC));
        EncodingManager encodingManager = GraphHopperGtfs.createEncodingManager();
        GtfsStorage gtfsStorage = GraphHopperGtfs.createGtfsStorage();
        GHDirectory directory = GraphHopperGtfs.createGHDirectory(GRAPH_LOC);
        GraphHopperStorage graphHopperStorage = GraphHopperGtfs.createOrLoad(directory, encodingManager, gtfsStorage, false, Collections.singleton("files/rnv.zip"), Collections.singleton("files/rnv.osm.pbf"));
        LocationIndex locationIndex = GraphHopperGtfs.createOrLoadIndex(directory, graphHopperStorage);
        graphHopper = new GraphHopperGtfs(encodingManager, GraphHopperGtfs.createTranslationMap(), graphHopperStorage, locationIndex, gtfsStorage);
    }

    @AfterClass
    public static void tearDown() {
//        if (graphHopper != null)
//            graphHopper.close();
    }

    @Test
    public void testOSMDataFileBounds() {
        GTFSFeed rnv = GTFSFeed.fromFile("files/rnv.zip");
        FeedStats feedStats = rnv.calculateStats();
        Rectangle2D bounds = feedStats.getBounds();
        System.out.printf("(%f,%f,%f,%f)\n", bounds.getMinY(), bounds.getMinX(), bounds.getMaxY(), bounds.getMaxX());
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
                TO_LAT, TO_LON, GTFS_START_DATE.atTime(20, 16,39));
    }

    @Test
    public void testTripWithWalkRangeQuery() {
        final double FROM_LAT = 49.49436, FROM_LON = 8.43372; // BASF
        final double TO_LAT = 49.47531, TO_LON = 8.48131; // Krappmuehlstr.
        final long startTime = getSeconds(GTFS_START_DATE.atTime(19, 40));
        final long rangeEndTime = getSeconds(GTFS_START_DATE.atTime(20, 40));
        // Transfer at e.g. Universitaet, where we have to walk to the next stop pole.
        // If we couldn't walk, we would arrive at least one connection later.

        GHRequest request = new GHRequest(FROM_LAT, FROM_LON, TO_LAT, TO_LON);
        request.getHints().put(GraphHopperGtfs.EARLIEST_DEPARTURE_TIME_HINT, startTime);
        request.getHints().put(GraphHopperGtfs.RANGE_QUERY_END_TIME, rangeEndTime);
        GHResponse response = graphHopper.route(request);
        assertFalse(response.hasErrors());

        assertEquals("Number of solutions", 12, response.getAll().size());

        assertEquals(GTFS_START_DATE.atTime(20, 16,39), GTFS_START_DATE.atStartOfDay().plusSeconds((long) response.getBest().getRouteWeight()));
        assertNotEquals("Best solution doesn't use transit at all.", -1, response.getBest().getNumChanges());

        for (PathWrapper solution : response.getAll()) {
            assertTrue("First pt leg departure is after start time", solution.getFirstPtLegDeparture() >= startTime);
            if (solution.getNumChanges() != -1) {
                assertTrue("First pt leg departure is not after query range end time", solution.getFirstPtLegDeparture() <= rangeEndTime);
            }
        }

        HashMap<Integer, Long> departureTimePerNTransfers = new HashMap<>();
        for (PathWrapper solution : response.getAll()) {
            assertTrue("a route with a later arrival must have a later departure, too", solution.getFirstPtLegDeparture() > departureTimePerNTransfers.getOrDefault(solution.getNumChanges(), Long.MIN_VALUE));
            departureTimePerNTransfers.put(solution.getNumChanges(), solution.getFirstPtLegDeparture());
        }

    }

    @Test
    public void testRouteWithWalkingBeforeAndAfter() {
        final double FROM_LAT = 49.517846, FROM_LON = 8.474073; // Stolberger Straße
        final double TO_LAT = 49.45958, TO_LON = 8.479514; // Freiheitsplatz
        assertRouteWeightIs(graphHopper, FROM_LAT, FROM_LON, GTFS_START_DATE.atTime(21, 36),
                TO_LAT, TO_LON, GTFS_START_DATE.atTime(22, 21));
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
                TO_LAT, TO_LON, LocalDateTime.of(2016,11,1,21,52,37));
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
