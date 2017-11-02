/*
 *  Licensed to GraphHopper GmbH under one or more contributor
 *  license agreements. See the NOTICE file distributed with this work for
 *  additional information regarding copyright ownership.
 *
 *  GraphHopper GmbH licenses this file to you under the Apache License,
 *  Version 2.0 (the "License"); you may not use this file except in
 *  compliance with the License. You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package com.graphhopper;

import com.google.transit.realtime.GtfsRealtime;
import com.graphhopper.reader.gtfs.GraphHopperGtfs;
import com.graphhopper.reader.gtfs.GtfsStorage;
import com.graphhopper.reader.gtfs.PtFlagEncoder;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.storage.GHDirectory;
import com.graphhopper.storage.GraphHopperStorage;
import com.graphhopper.storage.index.LocationIndex;
import com.graphhopper.util.Helper;
import com.graphhopper.util.Parameters;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.File;
import java.time.*;
import java.util.Arrays;
import java.util.Collections;

import static com.google.transit.realtime.GtfsRealtime.TripDescriptor.ScheduleRelationship.ADDED;
import static com.google.transit.realtime.GtfsRealtime.TripUpdate.StopTimeUpdate.ScheduleRelationship.SKIPPED;
import static com.graphhopper.reader.gtfs.GtfsHelper.time;
import static org.junit.Assert.assertEquals;

public class RealtimeIT {

    private static final String GRAPH_LOC = "target/RealtimeIT";
    private static final ZoneId zoneId = ZoneId.of("America/Los_Angeles");
    private static GraphHopperGtfs.Factory graphHopperFactory;
    private static GraphHopperStorage graphHopperStorage;
    private static LocationIndex locationIndex;

    @BeforeClass
    public static void init() {
        Helper.removeDir(new File(GRAPH_LOC));
        final PtFlagEncoder ptFlagEncoder = new PtFlagEncoder();
        EncodingManager encodingManager = new EncodingManager(Arrays.asList(ptFlagEncoder), 8);
        GHDirectory directory = GraphHopperGtfs.createGHDirectory(GRAPH_LOC);
        GtfsStorage gtfsStorage = GraphHopperGtfs.createGtfsStorage();
        graphHopperStorage = GraphHopperGtfs.createOrLoad(directory, encodingManager, ptFlagEncoder, gtfsStorage, true, Collections.singleton("files/sample-feed.zip"), Collections.emptyList());
        locationIndex = GraphHopperGtfs.createOrLoadIndex(directory, graphHopperStorage, ptFlagEncoder);
        graphHopperFactory = GraphHopperGtfs.createFactory(ptFlagEncoder, GraphHopperGtfs.createTranslationMap(), graphHopperStorage, locationIndex, gtfsStorage);
    }

    @AfterClass
    public static void close() {
        graphHopperStorage.close();
        locationIndex.close();
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
        ghRequest.getHints().put(Parameters.PT.EARLIEST_DEPARTURE_TIME, LocalDateTime.of(2007,1,1,6,44).atZone(zoneId).toInstant());
        ghRequest.getHints().put(Parameters.PT.IGNORE_TRANSFERS, true);
        ghRequest.getHints().put(Parameters.PT.MAX_WALK_DISTANCE_PER_LEG, 30);

        // But the 6:00 departure of my line is going to skip my departure stop :-(
        final GtfsRealtime.FeedMessage.Builder feedMessageBuilder = GtfsRealtime.FeedMessage.newBuilder();
        feedMessageBuilder.setHeader(GtfsRealtime.FeedHeader.newBuilder().setGtfsRealtimeVersion("1"));
        feedMessageBuilder.addEntityBuilder()
                .setId("1")
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
        ghRequest.getHints().put(Parameters.PT.EARLIEST_DEPARTURE_TIME, LocalDateTime.of(2007,1,1,6,44).atZone(zoneId).toInstant());
        ghRequest.getHints().put(Parameters.PT.IGNORE_TRANSFERS, true);
        ghRequest.getHints().put(Parameters.PT.MAX_WALK_DISTANCE_PER_LEG, 30);

        // But the 6:00 departure of my line is going to skip my arrival stop :-(
        final GtfsRealtime.FeedMessage.Builder feedMessageBuilder = GtfsRealtime.FeedMessage.newBuilder();
        feedMessageBuilder.setHeader(GtfsRealtime.FeedHeader.newBuilder().setGtfsRealtimeVersion("1"));
        feedMessageBuilder.addEntityBuilder()
                .setId("1")
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
        ghRequest.getHints().put(Parameters.PT.EARLIEST_DEPARTURE_TIME, LocalDateTime.of(2007,1,1,6,44).atZone(zoneId).toInstant());
        ghRequest.getHints().put(Parameters.PT.IGNORE_TRANSFERS, true);
        ghRequest.getHints().put(Parameters.PT.MAX_WALK_DISTANCE_PER_LEG, 30);

        // But the 6:00 departure of my line is going to skip my transfer stop :-(
        final GtfsRealtime.FeedMessage.Builder feedMessageBuilder = GtfsRealtime.FeedMessage.newBuilder();
        feedMessageBuilder.setHeader(GtfsRealtime.FeedHeader.newBuilder().setGtfsRealtimeVersion("1"));
        feedMessageBuilder.addEntityBuilder()
                .setId("1")
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
    public void testExtraTrip() {
        final double FROM_LAT = 36.914893, FROM_LON = -116.76821; // NADAV stop
        final double TO_LAT = 36.868446, TO_LON = -116.784582; // BEATTY_AIRPORT stop
        GHRequest ghRequest = new GHRequest(
                FROM_LAT, FROM_LON,
                TO_LAT, TO_LON
        );

        // I want to go at 6:44
        ghRequest.getHints().put(Parameters.PT.EARLIEST_DEPARTURE_TIME, LocalDateTime.of(2007,1,1,6,44).atZone(zoneId).toInstant());
        ghRequest.getHints().put(Parameters.PT.IGNORE_TRANSFERS, true);
        ghRequest.getHints().put(Parameters.PT.MAX_WALK_DISTANCE_PER_LEG, 30);

        // But the 6:00 departure of my line is going to skip my transfer stop :-(
        final GtfsRealtime.FeedMessage.Builder feedMessageBuilder = GtfsRealtime.FeedMessage.newBuilder();
        feedMessageBuilder.setHeader(GtfsRealtime.FeedHeader.newBuilder()
                .setGtfsRealtimeVersion("1")
                .setTimestamp(ZonedDateTime.of(LocalDate.of(2007,1,1), LocalTime.of(0,0), zoneId).toEpochSecond()));


        feedMessageBuilder.addEntityBuilder()
                .setId("1")
                .getTripUpdateBuilder()
                .setTrip(GtfsRealtime.TripDescriptor.newBuilder().setTripId("CITY2").setStartTime("06:00:00"))
                .addStopTimeUpdateBuilder()
                .setStopSequence(5)
                .setScheduleRelationship(SKIPPED);

        final GtfsRealtime.TripUpdate.Builder extraTripUpdate = feedMessageBuilder.addEntityBuilder()
                .setId("2")
                .getTripUpdateBuilder()
                .setTrip(GtfsRealtime.TripDescriptor.newBuilder().setScheduleRelationship(ADDED).setTripId("EXTRA").setRouteId("CITY").setStartTime("06:45:00"));
        extraTripUpdate
                .addStopTimeUpdateBuilder()
                .setStopSequence(1)
                .setStopId("NADAV")
                .setArrival(GtfsRealtime.TripUpdate.StopTimeEvent.newBuilder().setTime(LocalDateTime.of(2007,1,1,6,45).atZone(zoneId).toEpochSecond()))
                .setDeparture(GtfsRealtime.TripUpdate.StopTimeEvent.newBuilder().setTime(LocalDateTime.of(2007,1,1,6,45).atZone(zoneId).toEpochSecond()));
        extraTripUpdate
                .addStopTimeUpdateBuilder()
                .setStopSequence(2)
                .setStopId("BEATTY_AIRPORT")
                .setArrival(GtfsRealtime.TripUpdate.StopTimeEvent.newBuilder().setTime(LocalDateTime.of(2007,1,1,7,15).atZone(zoneId).toEpochSecond()))
                .setDeparture(GtfsRealtime.TripUpdate.StopTimeEvent.newBuilder().setTime(LocalDateTime.of(2007,1,1,7,15).atZone(zoneId).toEpochSecond()));

        GHResponse response = graphHopperFactory.createWith(feedMessageBuilder.build()).route(ghRequest);
        assertEquals(1, response.getAll().size());

        assertEquals("Luckily, there is an extra service directly from my stop to the airport, at 6:45, taking 30 minutes", time(0, 31), response.getBest().getTime(), 0.1);
    }


    @Test
    public void testBlockTrips() {
        final double FROM_LAT = 36.868446, FROM_LON = -116.784582; // BEATTY_AIRPORT stop
        final double TO_LAT = 36.425288, TO_LON = -117.133162; // FUR_CREEK_RES stop
        GHRequest ghRequest = new GHRequest(
                FROM_LAT, FROM_LON,
                TO_LAT, TO_LON
        );
        ghRequest.getHints().put(Parameters.PT.EARLIEST_DEPARTURE_TIME, LocalDateTime.of(2007,1,1,8,0).atZone(zoneId).toInstant());
        ghRequest.getHints().put(Parameters.PT.IGNORE_TRANSFERS, true);
        ghRequest.getHints().put(Parameters.PT.MAX_WALK_DISTANCE_PER_LEG, 30);

        // My line does not stop at Bullfrog today. If this was a real transfer, I would not be
        // able to change lines there. But it is not a real transfer, so I can go on as planned.
        final GtfsRealtime.FeedMessage.Builder feedMessageBuilder = GtfsRealtime.FeedMessage.newBuilder();
        feedMessageBuilder.setHeader(GtfsRealtime.FeedHeader.newBuilder().setGtfsRealtimeVersion("1"));
        feedMessageBuilder.addEntityBuilder()
                .setId("1")
                .getTripUpdateBuilder()
                .setTrip(GtfsRealtime.TripDescriptor.newBuilder().setTripId("AB1").setStartTime("00:00:00"))
                .addStopTimeUpdateBuilder()
                .setStopSequence(2)
                .setScheduleRelationship(SKIPPED);

        GHResponse response = graphHopperFactory.createWith(feedMessageBuilder.build()).route(ghRequest);
        assertEquals("I can still use the AB1 trip", "AB1", (((Trip.PtLeg) response.getBest().getLegs().get(0)).trip_id));
        assertEquals("It takes", time(1,20), response.getBest().getTime());
    }


}
