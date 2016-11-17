package com.graphhopper.suite;

import static org.junit.Assert.assertFalse;

import java.io.File;
import java.io.IOException;
import java.text.MessageFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import com.graphhopper.GHRequest;
import com.graphhopper.GHResponse;
import com.graphhopper.reader.gtfs.GraphHopperGtfs;
import com.graphhopper.reader.gtfs.GtfsHelper;
import com.graphhopper.util.Helper;

public class GraphHopperRnvGtfsSuiteIT {

    private static final String GRAPH_LOC = "target/graphhopperIT-rnv-gtfs";
    private static GraphHopperGtfs graphHopper;

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
    public void checkTripQueries() throws IOException {
        final TripQueryCsvReader reader = new TripQueryCsvReader();
        final File tripQueriesFile = new File("files/rnv-trips-least-duration-01.csv");
        final List<TripQuery> tripQueries = reader.read(tripQueriesFile);

        for (TripQuery tripQuery : tripQueries) {
            // Transfers don't yet work so we only use trip queries with single leg
            if (tripQuery.getTripLegsCount() == 1) {
                GHRequest ghRequest = new GHRequest(tripQuery.getFromLat(), tripQuery.getFromLon(),
                                tripQuery.getToLat(), tripQuery.getToLon());
                final int earliestDepartureTime = GtfsHelper.time(tripQuery.getDateTime());
                ghRequest.getHints().put(GraphHopperGtfs.EARLIEST_DEPARTURE_TIME_HINT,
                                earliestDepartureTime);
                GHResponse route = graphHopper.route(ghRequest);
                assertFalse(route.hasErrors());
                System.out.println(MessageFormat.format("Routing from {0} to {1} at {2} (trip query id {3}).",
                                tripQuery.getFromName(), tripQuery.getToName(),
                                tripQuery.getDateTime(), tripQuery.getId()));
                final LocalDateTime expectedArrivalDateTime = tripQuery.getTripLastArrivalDateTime();
                System.out.println(MessageFormat.format("Expected arrival: {0}.", expectedArrivalDateTime));
                if (!route.getAll().isEmpty()) {
                    final LocalDate tripQueryDate = tripQuery.getTripLastArrivalDateTime().toLocalDate();
                    final LocalDateTime actualArrivalDateTime = tripQueryDate.atStartOfDay().plusSeconds(Math.round(route.getBest().getRouteWeight()));
                    System.out.println(MessageFormat.format("Actual arrival: {0}", actualArrivalDateTime));
                } else {
                    System.out.println("Path not found.");
                }
            }
        }
    }
}
