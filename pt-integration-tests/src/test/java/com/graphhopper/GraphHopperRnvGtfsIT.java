package com.graphhopper;

import com.conveyal.gtfs.GTFSFeed;
import com.conveyal.gtfs.stats.FeedStats;
import com.graphhopper.reader.gtfs.GraphHopperGtfs;
import com.graphhopper.reader.gtfs.GtfsHelper;
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
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.HashMap;
import java.util.Optional;

import static com.graphhopper.reader.gtfs.GtfsHelper.time;
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
        assertArrivalTimeIs(graphHopper, FROM_LAT, FROM_LON, GTFS_START_DATE.atTime(15, 0),
                TO_LAT, TO_LON, GTFS_START_DATE.atTime(15, 17, 7));
    }

    @Test
    public void testRoute2() {
        final double FROM_LAT = 49.4048, FROM_LON = 8.6765; // 116006, HD Hauptbahnhof
        final double TO_LAT = 49.42799, TO_LON = 8.6833; // 113612, Hans-Thoma-Platz
        assertArrivalTimeIs(graphHopper, FROM_LAT, FROM_LON, GTFS_START_DATE.atTime(15, 7),
                TO_LAT, TO_LON, GTFS_START_DATE.atTime(15, 21, 7));
    }

    @Test
    public void testRouteAfterMidnight() {
        final double FROM_LAT = 49.4048, FROM_LON = 8.6765; // 116006, HD Hauptbahnhof
        final double TO_LAT = 49.42799, TO_LON = 8.6833; // 113612, Hans-Thoma-Platz
        assertArrivalTimeIs(graphHopper, FROM_LAT, FROM_LON, GTFS_START_DATE.plusDays(1).atTime(0, 20),
                TO_LAT, TO_LON, GTFS_START_DATE.plusDays(1).atTime(0, 45, 7));
    }

    @Test
    public void testRouteInSecondNight() {
        final double FROM_LAT = 49.4048, FROM_LON = 8.6765; // 116006, HD Hauptbahnhof
        final double TO_LAT = 49.42799, TO_LON = 8.6833; // 113612, Hans-Thoma-Platz
        assertArrivalTimeIs(graphHopper, FROM_LAT, FROM_LON, GTFS_START_DATE.plusDays(2).atTime(0, 20),
                TO_LAT, TO_LON, GTFS_START_DATE.plusDays(2).atTime(0, 45, 7));
    }

    @Test
    public void testTripWithWalk() {
        final double FROM_LAT = 49.49436, FROM_LON = 8.43372; // BASF
        final double TO_LAT = 49.47531, TO_LON = 8.48131; // Krappmuehlstr.
        // Transfer at e.g. Universitaet, where we have to walk to the next stop pole.
        // If we couldn't walk, we would arrive at least one connection later.
        assertArrivalTimeIs(graphHopper, FROM_LAT, FROM_LON, GTFS_START_DATE.atTime(19, 40),
                TO_LAT, TO_LON, GTFS_START_DATE.atTime(20, 16,33));
    }

    @Test
    public void testTripWithWalkRangeQuery() {
        final double FROM_LAT = 49.49436, FROM_LON = 8.43372; // BASF
        final double TO_LAT = 49.47531, TO_LON = 8.48131; // Krappmuehlstr.
        final LocalDateTime startTime = GTFS_START_DATE.atTime(19, 40);
        final LocalDateTime rangeEndTime = GTFS_START_DATE.atTime(20, 40);
        // Transfer at e.g. Universitaet, where we have to walk to the next stop pole.
        // If we couldn't walk, we would arrive at least one connection later.

        GHRequest request = new GHRequest(FROM_LAT, FROM_LON, TO_LAT, TO_LON);
        request.getHints().put(GraphHopperGtfs.EARLIEST_DEPARTURE_TIME_HINT, startTime.toString());
        request.getHints().put(GraphHopperGtfs.RANGE_QUERY_END_TIME, rangeEndTime.toString());
        GHResponse response = graphHopper.route(request);
        assertFalse(response.hasErrors());

        assertEquals("Number of solutions", 12, response.getAll().size());

        assertEquals(time(0, 36,33), response.getBest().getTime());
        assertNotEquals("Best solution doesn't use transit at all.", -1, response.getBest().getNumChanges());

        for (PathWrapper solution : response.getAll()) {
            getFirstPtLegDeparture(solution)
                    .ifPresent(firstPtLegDeparture -> {
                        assertTrue("First pt leg departure is after start time", !firstPtLegDeparture.isBefore(startTime));
                        assertTrue("First pt leg departure is not after query range end time", !firstPtLegDeparture.isAfter(rangeEndTime));
                    });
        }

        HashMap<Integer, LocalDateTime> departureTimePerNTransfers = new HashMap<>();
        for (PathWrapper solution : response.getAll()) {
            getFirstPtLegDeparture(solution)
                    .ifPresent(firstPtLegDeparture -> {
                        assertTrue("a route with a later arrival must have a later departure, too", firstPtLegDeparture.isAfter(departureTimePerNTransfers.getOrDefault(solution.getNumChanges(), LocalDateTime.MIN)));
                        departureTimePerNTransfers.put(solution.getNumChanges(), firstPtLegDeparture);
                    });
        }
    }

    @Test
    public void testRouteWithWalkingBeforeAndAfter() {
        final double FROM_LAT = 49.517846, FROM_LON = 8.474073; // Stolberger Straße
        final double TO_LAT = 49.45958, TO_LON = 8.479514; // Freiheitsplatz
        assertArrivalTimeIs(graphHopper, FROM_LAT, FROM_LON, GTFS_START_DATE.atTime(21, 36, 19),
                TO_LAT, TO_LON, GTFS_START_DATE.atTime(22, 21, 19));
    }

    @Test
    public void testWeekendRouteWhichFailsIfBusinessDayIsNotRolledOver() {
        final double FROM_LAT = 49.49058, FROM_LON = 8.37085; // Wilhelm-Tell-Str
        final double TO_LAT = 49.41947, TO_LON = 8.66979; // Pädagog. Hochschule
        assertArrivalTimeIs(graphHopper, FROM_LAT, FROM_LON, LocalDateTime.of(2016,11,6,0,42),
                TO_LAT, TO_LON, LocalDateTime.of(2016,11,6,2,47,28));
    }

    @Test
    public void testRouteWithVeryLongTravelTimeAccordingToBahnDe() {
        final double FROM_LAT = 49.442904, FROM_LON = 8.519059; // Sporwoerthplatz
        final double TO_LAT = 49.562158, TO_LON = 8.448643; // Fuellenweg
        assertArrivalTimeIs(graphHopper, FROM_LAT, FROM_LON, LocalDateTime.of(2016, 11, 1, 19, 28),
                TO_LAT, TO_LON, LocalDateTime.of(2016,11,1,21,52,23));
    }

    private void assertArrivalTimeIs(GraphHopperGtfs graphHopper, double from_lat, double from_lon, LocalDateTime earliestDepartureTime, double to_lat, double to_lon, LocalDateTime expectedArrivalTime) {
        GHRequest ghRequest = new GHRequest(
                from_lat, from_lon,
                to_lat, to_lon
        );
        ghRequest.getHints().put(GraphHopperGtfs.EARLIEST_DEPARTURE_TIME_HINT, earliestDepartureTime.toString());
        GHResponse route = graphHopper.route(ghRequest);
        assertFalse(route.hasErrors());
        assertEquals(expectedArrivalTime, earliestDepartureTime.plus(route.getBest().getTime(), ChronoUnit.MILLIS));
        assertNotEquals("Solution doesn't use transit at all.", -1, route.getBest().getNumChanges());

    }

    private Optional<LocalDateTime> getFirstPtLegDeparture(PathWrapper path) {
        return path.getLegs().stream()
                .filter(leg -> leg instanceof Trip.PtLeg)
                .findFirst()
                .map(ptleg -> ((Trip.PtLeg) ptleg).departureTime)
                .map(GtfsHelper::localDateTimeFromDate);
    }

}
