package com.graphhopper;

import com.google.transit.realtime.GtfsRealtime;
import com.graphhopper.reader.gtfs.GraphHopperGtfs;
import com.graphhopper.reader.gtfs.GtfsStorage;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.storage.GHDirectory;
import com.graphhopper.storage.GraphHopperStorage;
import com.graphhopper.util.Helper;
import com.graphhopper.util.Instruction;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.File;
import java.time.LocalDateTime;
import java.util.Collections;

import static com.google.transit.realtime.GtfsRealtime.TripUpdate.StopTimeUpdate.ScheduleRelationship.SKIPPED;
import static com.graphhopper.reader.gtfs.GtfsHelper.time;
import static org.junit.Assert.assertEquals;

public class RealtimeIT {

    private static final String GRAPH_LOC = "target/graphhopperIT-gtfs";
    private static GraphHopperGtfs.Factory graphHopperFactory;

    @BeforeClass
    public static void init() {
        Helper.removeDir(new File(GRAPH_LOC));
        EncodingManager encodingManager = GraphHopperGtfs.createEncodingManager();
        GtfsStorage gtfsStorage = GraphHopperGtfs.createGtfsStorage();
        GHDirectory directory = GraphHopperGtfs.createGHDirectory(GRAPH_LOC);
        GraphHopperStorage graphHopperStorage = GraphHopperGtfs.createOrLoad(directory, encodingManager, gtfsStorage, true, Collections.singleton("files/sample-feed.zip"), Collections.emptyList());
        graphHopperFactory = GraphHopperGtfs.createFactory(encodingManager, GraphHopperGtfs.createTranslationMap(), graphHopperStorage, GraphHopperGtfs.createOrLoadIndex(directory, graphHopperStorage), gtfsStorage);
    }


    @Test
    public void testSkipDepartureStop() {
        final double FROM_LAT = 36.914893, FROM_LON = -116.76821; // NADAV stop
        final double TO_LAT = 36.914944, TO_LON = -116.761472; // NANAA stop
        GHRequest ghRequest = new GHRequest(
                FROM_LAT, FROM_LON,
                TO_LAT, TO_LON
        );

        // I want to go at 6:44
        ghRequest.getHints().put(GraphHopperGtfs.EARLIEST_DEPARTURE_TIME_HINT, LocalDateTime.of(2007,1,1,6,44).toString());
        ghRequest.getHints().put(GraphHopperGtfs.IGNORE_TRANSFERS, "true");
        ghRequest.getHints().put(GraphHopperGtfs.MAX_WALK_DISTANCE_PER_LEG, 30);

        // But the 6:00 departure of my line is going to skip my departure stop :-(
        final GtfsRealtime.FeedMessage.Builder feedMessageBuilder = GtfsRealtime.FeedMessage.newBuilder();
        feedMessageBuilder.setHeader(GtfsRealtime.FeedHeader.newBuilder().setGtfsRealtimeVersion("wurst"));
        feedMessageBuilder.addEntityBuilder()
                .setId("pups")
                .getTripUpdateBuilder()
                .setTrip(GtfsRealtime.TripDescriptor.newBuilder().setTripId("CITY2").setStartTime("06:00:00"))
                .addStopTimeUpdateBuilder()
                .setStopSequence(3)
                .setScheduleRelationship(SKIPPED);

        GHResponse response = graphHopperFactory.createWith(feedMessageBuilder.build()).route(ghRequest);
        assertEquals(1, response.getAll().size());

        assertEquals("I have to wait half an hour for the next one (and ride 5 minutes)", time(0, 35), response.getBest().getTime(), 0.1);
    }

    @Test
    public void testSkipArrivalStop() {
        final double FROM_LAT = 36.914893, FROM_LON = -116.76821; // NADAV stop
        final double TO_LAT = 36.914944, TO_LON = -116.761472; // NANAA stop
        GHRequest ghRequest = new GHRequest(
                FROM_LAT, FROM_LON,
                TO_LAT, TO_LON
        );

        // I want to go at 6:44
        ghRequest.getHints().put(GraphHopperGtfs.EARLIEST_DEPARTURE_TIME_HINT, LocalDateTime.of(2007,1,1,6,44).toString());
        ghRequest.getHints().put(GraphHopperGtfs.IGNORE_TRANSFERS, "true");
        ghRequest.getHints().put(GraphHopperGtfs.MAX_WALK_DISTANCE_PER_LEG, 30);

        // But the 6:00 departure of my line is going to skip my arrival stop :-(
        final GtfsRealtime.FeedMessage.Builder feedMessageBuilder = GtfsRealtime.FeedMessage.newBuilder();
        feedMessageBuilder.setHeader(GtfsRealtime.FeedHeader.newBuilder().setGtfsRealtimeVersion("wurst"));
        feedMessageBuilder.addEntityBuilder()
                .setId("pups")
                .getTripUpdateBuilder()
                .setTrip(GtfsRealtime.TripDescriptor.newBuilder().setTripId("CITY2").setStartTime("06:00:00"))
                .addStopTimeUpdateBuilder()
                .setStopSequence(4)
                .setScheduleRelationship(SKIPPED);

        GHResponse response = graphHopperFactory.createWith(feedMessageBuilder.build()).route(ghRequest);
        assertEquals(1, response.getAll().size());

        assertEquals("I have to continue to STAGECOACH and then go back one stop with the 07:00 bus.", time(0, 21), response.getBest().getTime(), 0.1);
    }

    @Test
    public void testSkipTransferStop() {
        final double FROM_LAT = 36.914893, FROM_LON = -116.76821; // NADAV stop
        final double TO_LAT = 36.868446, TO_LON = -116.784582; // BEATTY_AIRPORT stop
        GHRequest ghRequest = new GHRequest(
                FROM_LAT, FROM_LON,
                TO_LAT, TO_LON
        );

        // I want to go at 6:44
        ghRequest.getHints().put(GraphHopperGtfs.EARLIEST_DEPARTURE_TIME_HINT, LocalDateTime.of(2007,1,1,6,44).toString());
        ghRequest.getHints().put(GraphHopperGtfs.IGNORE_TRANSFERS, "true");
        ghRequest.getHints().put(GraphHopperGtfs.MAX_WALK_DISTANCE_PER_LEG, 30);

        // But the 6:00 departure of my line is going to skip my transfer stop :-(
        final GtfsRealtime.FeedMessage.Builder feedMessageBuilder = GtfsRealtime.FeedMessage.newBuilder();
        feedMessageBuilder.setHeader(GtfsRealtime.FeedHeader.newBuilder().setGtfsRealtimeVersion("wurst"));
        feedMessageBuilder.addEntityBuilder()
                .setId("pups")
                .getTripUpdateBuilder()
                .setTrip(GtfsRealtime.TripDescriptor.newBuilder().setTripId("CITY2").setStartTime("06:00:00"))
                .addStopTimeUpdateBuilder()
                .setStopSequence(5)
                .setScheduleRelationship(SKIPPED);

        GHResponse response = graphHopperFactory.createWith(feedMessageBuilder.build()).route(ghRequest);
        assertEquals(1, response.getAll().size());

        assertEquals("The 6:44 bus will not call at STAGECOACH, so I will be 30 min late at the airport.", time(1, 6), response.getBest().getTime(), 0.1);
    }

    @Test
    public void testBlockTrips() {
        final double FROM_LAT = 36.868446, FROM_LON = -116.784582; // BEATTY_AIRPORT stop
        final double TO_LAT = 36.425288, TO_LON = -117.133162; // FUR_CREEK_RES stop
        GHRequest ghRequest = new GHRequest(
                FROM_LAT, FROM_LON,
                TO_LAT, TO_LON
        );
        ghRequest.getHints().put(GraphHopperGtfs.EARLIEST_DEPARTURE_TIME_HINT, LocalDateTime.of(2007,1,1,8,0));
        ghRequest.getHints().put(GraphHopperGtfs.IGNORE_TRANSFERS, "true");
        ghRequest.getHints().put(GraphHopperGtfs.MAX_WALK_DISTANCE_PER_LEG, 30);

        // My line does not stop at Bullfrog today. If this was a real transfer, I would not be
        // able to change lines there. But it is not a real transfer, so I can go on as planned.
        final GtfsRealtime.FeedMessage.Builder feedMessageBuilder = GtfsRealtime.FeedMessage.newBuilder();
        feedMessageBuilder.setHeader(GtfsRealtime.FeedHeader.newBuilder().setGtfsRealtimeVersion("wurst"));
        feedMessageBuilder.addEntityBuilder()
                .setId("pups")
                .getTripUpdateBuilder()
                .setTrip(GtfsRealtime.TripDescriptor.newBuilder().setTripId("AB1").setStartTime("00:00:00"))
                .addStopTimeUpdateBuilder()
                .setStopSequence(2)
                .setScheduleRelationship(SKIPPED);

        GHResponse response = graphHopperFactory.createWith(feedMessageBuilder.build()).route(ghRequest);
        assertEquals("I can still use the AB1 trip", "AB1", (((Trip.PtLeg) response.getBest().getLegs().get(1)).tripId));
        assertEquals("It takes", time(1,20), response.getBest().getTime());
    }


}
