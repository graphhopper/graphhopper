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
import com.graphhopper.routing.util.CarFlagEncoder;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.routing.util.FootFlagEncoder;
import com.graphhopper.storage.GHDirectory;
import com.graphhopper.storage.GraphHopperStorage;
import com.graphhopper.storage.index.LocationIndex;
import com.graphhopper.util.Helper;
import com.graphhopper.util.Parameters;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.File;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.Collections;

import static com.google.transit.realtime.GtfsRealtime.TripDescriptor.ScheduleRelationship.ADDED;
import static com.google.transit.realtime.GtfsRealtime.TripUpdate.StopTimeUpdate.ScheduleRelationship.SCHEDULED;
import static com.google.transit.realtime.GtfsRealtime.TripUpdate.StopTimeUpdate.ScheduleRelationship.SKIPPED;
import static com.graphhopper.reader.gtfs.GtfsHelper.time;
import static org.junit.Assert.*;

public class RealtimeIT {

    private static final String GRAPH_LOC = "target/RealtimeIT";
    private static final ZoneId zoneId = ZoneId.of("America/Los_Angeles");
    private static final String agencyId = "DTA";
    private static GraphHopperGtfs.Factory graphHopperFactory;
    private static GraphHopperStorage graphHopperStorage;
    private static LocationIndex locationIndex;

    @BeforeClass
    public static void init() {
        Helper.removeDir(new File(GRAPH_LOC));
        final PtFlagEncoder ptFlagEncoder = new PtFlagEncoder();
        EncodingManager encodingManager = EncodingManager.create(Arrays.asList(new CarFlagEncoder(), ptFlagEncoder, new FootFlagEncoder()), 8);
        GHDirectory directory = GraphHopperGtfs.createGHDirectory(GRAPH_LOC);
        GtfsStorage gtfsStorage = GraphHopperGtfs.createGtfsStorage();
        graphHopperStorage = GraphHopperGtfs.createOrLoad(directory, encodingManager, ptFlagEncoder, gtfsStorage, Collections.singleton("files/sample-feed.zip"), Collections.emptyList());
        locationIndex = GraphHopperGtfs.createOrLoadIndex(directory, graphHopperStorage);
        graphHopperStorage.close();
        locationIndex.close();
        // Re-load read only
        directory = GraphHopperGtfs.createGHDirectory(GRAPH_LOC);
        graphHopperStorage = GraphHopperGtfs.createOrLoad(directory, encodingManager, ptFlagEncoder, gtfsStorage, Collections.singleton("files/sample-feed.zip"), Collections.emptyList());
        locationIndex = GraphHopperGtfs.createOrLoadIndex(directory, graphHopperStorage);
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
        ghRequest.getHints().put(Parameters.PT.MAX_WALK_DISTANCE_PER_LEG, 30);

        // But the 6:00 departure of my line is going to skip my departure stop :-(
        final GtfsRealtime.FeedMessage.Builder feedMessageBuilder = GtfsRealtime.FeedMessage.newBuilder();
        feedMessageBuilder.setHeader(GtfsRealtime.FeedHeader.newBuilder()
                .setGtfsRealtimeVersion("1")
                .setTimestamp(ZonedDateTime.of(LocalDate.of(2007,1,1), LocalTime.of(0,0), zoneId).toEpochSecond()));
        feedMessageBuilder.addEntityBuilder()
                .setId("1")
                .getTripUpdateBuilder()
                .setTrip(GtfsRealtime.TripDescriptor.newBuilder().setTripId("CITY2").setStartTime("06:00:00"))
                .addStopTimeUpdateBuilder()
                .setStopSequence(3)
                .setScheduleRelationship(SKIPPED);

        GHResponse response = graphHopperFactory.createWith(feedMessageBuilder.build(), agencyId).route(ghRequest);

        PathWrapper possibleAlternative = response.getAll().stream().filter(a -> !a.isImpossible()).findFirst().get();
        assertFalse(((Trip.PtLeg) possibleAlternative.getLegs().get(0)).stops.get(0).departureCancelled);
        assertEquals("I have to wait half an hour for the next one (and ride 5 minutes)", time(0, 35), possibleAlternative.getTime(), 0.1);

        PathWrapper impossibleAlternative = response.getAll().stream().filter(a -> a.isImpossible()).findFirst().get();
        assertTrue(impossibleAlternative.isImpossible());
        assertTrue(((Trip.PtLeg) impossibleAlternative.getLegs().get(0)).stops.get(0).departureCancelled);
    }

    @Test
    public void testHeavyDelayWhereWeShouldTakeOtherTripInstead() {
        final double FROM_LAT = 36.914893, FROM_LON = -116.76821; // NADAV stop
        final double TO_LAT = 36.914944, TO_LON = -116.761472; // NANAA stop
        GHRequest ghRequest = new GHRequest(
                FROM_LAT, FROM_LON,
                TO_LAT, TO_LON
        );

        // I want to go at 6:44
        ghRequest.getHints().put(Parameters.PT.EARLIEST_DEPARTURE_TIME, LocalDateTime.of(2007,1,1,6,44).atZone(zoneId).toInstant());
        ghRequest.getHints().put(Parameters.PT.MAX_WALK_DISTANCE_PER_LEG, 30);

        // But the 6:00 departure of my line is going to be super-late :-(
        final GtfsRealtime.FeedMessage.Builder feedMessageBuilder = GtfsRealtime.FeedMessage.newBuilder();
        feedMessageBuilder.setHeader(GtfsRealtime.FeedHeader.newBuilder()
                .setGtfsRealtimeVersion("1")
                .setTimestamp(ZonedDateTime.of(LocalDate.of(2007,1,1), LocalTime.of(0,0), zoneId).toEpochSecond()));
        feedMessageBuilder.addEntityBuilder()
                .setId("1")
                .getTripUpdateBuilder()
                .setTrip(GtfsRealtime.TripDescriptor.newBuilder().setTripId("CITY2").setStartTime("06:00:00"))
                .addStopTimeUpdateBuilder()
                .setScheduleRelationship(SCHEDULED)
                .setStopSequence(3)
                .setArrival(GtfsRealtime.TripUpdate.StopTimeEvent.newBuilder().setDelay(3600).build());

        GHResponse response = graphHopperFactory.createWith(feedMessageBuilder.build(), agencyId).route(ghRequest);
        assertEquals(2, response.getAll().size());

        PathWrapper best = response.getBest();
        Trip.PtLeg bestPtLeg = (Trip.PtLeg) best.getLegs().get(0);
        assertEquals("It's better to wait half an hour for the next one (and ride 5 minutes).", LocalDateTime.parse("2007-01-01T07:19:00").atZone(zoneId).toInstant(), bestPtLeg.stops.get(bestPtLeg.stops.size()-1).plannedArrivalTime.toInstant());
        assertEquals("There is no predicted arrival time.", null, bestPtLeg.stops.get(bestPtLeg.stops.size()-1).predictedArrivalTime);

        PathWrapper impossibleAlternative = response.getAll().get(1);
        assertTrue(impossibleAlternative.isImpossible());
        Trip.PtLeg impossiblePtLeg = (Trip.PtLeg) impossibleAlternative.getLegs().get(0);
        assertEquals("The impossible alternative is my planned 5-minute-trip", LocalDateTime.parse("2007-01-01T06:49:00").atZone(zoneId).toInstant(), impossiblePtLeg.stops.get(impossiblePtLeg.stops.size()-1).plannedArrivalTime.toInstant());
        assertEquals("..which is very late today", LocalDateTime.parse("2007-01-01T07:49:00").atZone(zoneId).toInstant(), impossiblePtLeg.stops.get(impossiblePtLeg.stops.size()-1).predictedArrivalTime.toInstant());
    }

    @Test
    public void testCanUseDelayedTripWhenIAmLateToo() {
        final double FROM_LAT = 36.914893, FROM_LON = -116.76821; // NADAV stop
        final double TO_LAT = 36.914944, TO_LON = -116.761472; // NANAA stop
        GHRequest ghRequest = new GHRequest(
                FROM_LAT, FROM_LON,
                TO_LAT, TO_LON
        );

        // I want to go at 6:44
        ghRequest.getHints().put(Parameters.PT.EARLIEST_DEPARTURE_TIME, LocalDateTime.of(2007,1,1,6,46).atZone(zoneId).toInstant());
        ghRequest.getHints().put(Parameters.PT.MAX_WALK_DISTANCE_PER_LEG, 30);

        // But the 6:00 departure of my line is going to be super-late :-(
        final GtfsRealtime.FeedMessage.Builder feedMessageBuilder = GtfsRealtime.FeedMessage.newBuilder();
        feedMessageBuilder.setHeader(GtfsRealtime.FeedHeader.newBuilder()
                .setGtfsRealtimeVersion("1")
                .setTimestamp(ZonedDateTime.of(LocalDate.of(2007,1,1), LocalTime.of(0,0), zoneId).toEpochSecond()));
        feedMessageBuilder.addEntityBuilder()
                .setId("1")
                .getTripUpdateBuilder()
                .setTrip(GtfsRealtime.TripDescriptor.newBuilder().setTripId("CITY2").setStartTime("06:00:00"))
                .addStopTimeUpdateBuilder()
                .setScheduleRelationship(SCHEDULED)
                .setStopSequence(3)
                .setArrival(GtfsRealtime.TripUpdate.StopTimeEvent.newBuilder().setDelay(120).build());

        GHResponse response = graphHopperFactory.createWith(feedMessageBuilder.build(), agencyId).route(ghRequest);

        assertEquals("I am two minutes late for my bus, but the bus is two minutes late, too, so I catch it!", time(0, 5), response.getBest().getTime(), 0.1);
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
        ghRequest.getHints().put(Parameters.PT.MAX_WALK_DISTANCE_PER_LEG, 30);

        // But the 6:00 departure of my line is going to skip my arrival stop :-(
        final GtfsRealtime.FeedMessage.Builder feedMessageBuilder = GtfsRealtime.FeedMessage.newBuilder();
        feedMessageBuilder.setHeader(GtfsRealtime.FeedHeader.newBuilder()
                .setGtfsRealtimeVersion("1")
                .setTimestamp(ZonedDateTime.of(LocalDate.of(2007,1,1), LocalTime.of(0,0), zoneId).toEpochSecond()));
        feedMessageBuilder.addEntityBuilder()
                .setId("1")
                .getTripUpdateBuilder()
                .setTrip(GtfsRealtime.TripDescriptor.newBuilder().setTripId("CITY2").setStartTime("06:00:00"))
                .addStopTimeUpdateBuilder()
                .setStopSequence(4)
                .setScheduleRelationship(SKIPPED);

        GHResponse response = graphHopperFactory.createWith(feedMessageBuilder.build(), agencyId).route(ghRequest);
        assertEquals(3, response.getAll().size());

        assertEquals("I have to continue to STAGECOACH and then go back one stop with the 07:00 bus.", time(0, 21), response.getBest().getTime(), 0.1);

        PathWrapper impossibleAlternative = response.getAll().get(2);
        assertTrue(impossibleAlternative.isImpossible());
        assertTrue(((Trip.PtLeg) impossibleAlternative.getLegs().get(0)).stops.get(1).arrivalCancelled);
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

        GHResponse response = graphHopperFactory.createWith(feedMessageBuilder.build(), agencyId).route(ghRequest);
        assertEquals(2, response.getAll().size());

        assertEquals("The 6:44 bus will not call at STAGECOACH, so I will be 30 min late at the airport.", time(1, 6), response.getBest().getTime(), 0.1);

        PathWrapper impossibleAlternative = response.getAll().get(1);
        assertTrue(impossibleAlternative.isImpossible());
        assertTrue(((Trip.PtLeg) impossibleAlternative.getLegs().get(0)).stops.get(2).departureCancelled);
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

        // Add a few more trips (but we only need the first one; add more because there used to be a bug with something like an index overflow)
        for (int i=0; i<100; i++){
            final GtfsRealtime.TripUpdate.Builder extraTripUpdate = feedMessageBuilder.addEntityBuilder()
                    .setId("2")
                    .getTripUpdateBuilder()
                    .setTrip(GtfsRealtime.TripDescriptor.newBuilder().setScheduleRelationship(ADDED).setTripId("EXTRA"+i).setRouteId("CITY").setStartTime("06:45:0"+i));
            extraTripUpdate
                    .addStopTimeUpdateBuilder()
                    .setStopSequence(1)
                    .setStopId("NADAV")
                    .setArrival(GtfsRealtime.TripUpdate.StopTimeEvent.newBuilder().setTime(LocalDateTime.of(2007,1,1,6,45).plusMinutes(i).atZone(zoneId).toEpochSecond()))
                    .setDeparture(GtfsRealtime.TripUpdate.StopTimeEvent.newBuilder().setTime(LocalDateTime.of(2007,1,1,6,45).plusMinutes(i).atZone(zoneId).toEpochSecond()));
            extraTripUpdate
                    .addStopTimeUpdateBuilder()
                    .setStopSequence(2)
                    .setStopId("BEATTY_AIRPORT")
                    .setArrival(GtfsRealtime.TripUpdate.StopTimeEvent.newBuilder().setTime(LocalDateTime.of(2007,1,1,7,15).plusMinutes(i).atZone(zoneId).toEpochSecond()))
                    .setDeparture(GtfsRealtime.TripUpdate.StopTimeEvent.newBuilder().setTime(LocalDateTime.of(2007,1,1,7,15).plusMinutes(i).atZone(zoneId).toEpochSecond()));

        }

        GHResponse response = graphHopperFactory.createWith(feedMessageBuilder.build(), agencyId).route(ghRequest);
        assertEquals(1, response.getAll().size());

        assertEquals("Luckily, there is an extra service directly from my stop to the airport, at 6:45, taking 30 minutes", time(0, 31), response.getBest().getTime(), 0.1);

        PathWrapper solution = response.getAll().get(0);
        Trip.PtLeg ptLeg = ((Trip.PtLeg) solution.getLegs().stream().filter(leg -> leg instanceof Trip.PtLeg).findFirst().get());
        assertEquals("EXTRA0", ptLeg.trip_id);
    }

    @Test
    public void testExtraTripWorksOnlyOnSpecifiedDay() {
        final double FROM_LAT = 36.914893, FROM_LON = -116.76821; // NADAV stop
        final double TO_LAT = 36.868446, TO_LON = -116.784582; // BEATTY_AIRPORT stop
        GHRequest ghRequest = new GHRequest(
                FROM_LAT, FROM_LON,
                TO_LAT, TO_LON
        );

        // I want to go at 6:45, but tomorrow
        ghRequest.getHints().put(Parameters.PT.EARLIEST_DEPARTURE_TIME, LocalDateTime.of(2007,1,2,6,45).atZone(zoneId).toInstant());
        ghRequest.getHints().put(Parameters.PT.IGNORE_TRANSFERS, true);
        ghRequest.getHints().put(Parameters.PT.MAX_WALK_DISTANCE_PER_LEG, 30);

        final GtfsRealtime.FeedMessage.Builder feedMessageBuilder = GtfsRealtime.FeedMessage.newBuilder();
        feedMessageBuilder.setHeader(GtfsRealtime.FeedHeader.newBuilder()
                .setGtfsRealtimeVersion("1")
                .setTimestamp(ZonedDateTime.of(LocalDate.of(2007,1,1), LocalTime.of(0,0), zoneId).toEpochSecond()));

        // Today, there is an extra trip right at 6:45, but that doesn't concern me.
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


        GHResponse response = graphHopperFactory.createWith(feedMessageBuilder.build(), agencyId).route(ghRequest);
        assertEquals(1, response.getAll().size());

        assertEquals("There is an extra trip at 6:45 tomorrow, but that doesn't concern me today.", time(1, 5), response.getBest().getTime(), 0.1);
    }

    @Test
    public void testZeroDelay() {
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

        GHResponse responseWithoutRealtimeUpdate = graphHopperFactory.createWithoutRealtimeFeed().route(ghRequest);

        // The 6:00 departure of my line is going to be "late" by 0 minutes
        final GtfsRealtime.FeedMessage.Builder feedMessageBuilder = GtfsRealtime.FeedMessage.newBuilder();
        feedMessageBuilder.setHeader(header());
        feedMessageBuilder.addEntityBuilder()
                .setId("1")
                .getTripUpdateBuilder()
                .setTrip(GtfsRealtime.TripDescriptor.newBuilder().setTripId("CITY2").setStartTime("06:00:00"))
                .addStopTimeUpdateBuilder()
                .setStopSequence(5)
                .setScheduleRelationship(SCHEDULED)
                .setDeparture(GtfsRealtime.TripUpdate.StopTimeEvent.newBuilder().setDelay(0).build());

        GHResponse responseWithRealtimeUpdate = graphHopperFactory.createWith(feedMessageBuilder.build(), agencyId).route(ghRequest);
        assertEquals(1, responseWithRealtimeUpdate.getAll().size());

        Trip.PtLeg responseWithRealtimeUpdateBest = (Trip.PtLeg) responseWithRealtimeUpdate.getBest().getLegs().get(0);
        Trip.PtLeg responseWithoutRealtimeUpdateBest = (Trip.PtLeg) responseWithoutRealtimeUpdate.getBest().getLegs().get(0);
        assertEquals("My planned arrival time is correct.", LocalDateTime.parse("2007-01-01T06:49:00").atZone(zoneId).toInstant(), responseWithRealtimeUpdateBest.stops.get(responseWithRealtimeUpdateBest.stops.size()-1).plannedArrivalTime.toInstant());
        assertEquals("My expected arrival time is the same.", LocalDateTime.parse("2007-01-01T06:49:00").atZone(zoneId).toInstant(), responseWithRealtimeUpdateBest.stops.get(responseWithRealtimeUpdateBest.stops.size()-1).predictedArrivalTime.toInstant());
        assertEquals("The trip without realtime update does not have an expected arrival time.", null, responseWithoutRealtimeUpdateBest.stops.get(responseWithoutRealtimeUpdateBest.stops.size()-1).predictedArrivalTime);

//        assertEquals(responseWithoutRealtimeUpdateBest.toString(), responseWithRealtimeUpdateBest.toString());
    }

    @Test
    public void testDelayWithoutTransfer() {
        final double FROM_LAT = 36.914893, FROM_LON = -116.76821; // NADAV stop
        final double TO_LAT = 36.914944, TO_LON = -116.761472; // NANAA stop
        GHRequest ghRequest = new GHRequest(
                FROM_LAT, FROM_LON,
                TO_LAT, TO_LON
        );

        // I want to go at 6:44
        Instant initialTime = LocalDateTime.of(2007, 1, 1, 6, 44).atZone(zoneId).toInstant();
        ghRequest.getHints().put(Parameters.PT.EARLIEST_DEPARTURE_TIME, initialTime);
        ghRequest.getHints().put(Parameters.PT.IGNORE_TRANSFERS, true);
        ghRequest.getHints().put(Parameters.PT.MAX_WALK_DISTANCE_PER_LEG, 30);

        // The 6:00 departure of my line is going to be late by 3 minutes
        final GtfsRealtime.FeedMessage.Builder feedMessageBuilder = GtfsRealtime.FeedMessage.newBuilder();
        feedMessageBuilder.setHeader(header());
        feedMessageBuilder.addEntityBuilder()
                .setId("1")
                .getTripUpdateBuilder()
                .setTrip(GtfsRealtime.TripDescriptor.newBuilder().setTripId("CITY2").setStartTime("06:00:00"))
                .addStopTimeUpdateBuilder()
                .setStopSequence(4)
                .setScheduleRelationship(SCHEDULED)
                .setArrival(GtfsRealtime.TripUpdate.StopTimeEvent.newBuilder().setDelay(180).build());

        GHResponse response = graphHopperFactory.createWith(feedMessageBuilder.build(), agencyId).route(ghRequest);
        assertEquals(1, response.getAll().size());

        assertEquals("My line run is 3 minutes late.", time(0, 8), response.getBest().getLegs().get(response.getBest().getLegs().size() - 1).getArrivalTime().toInstant().toEpochMilli() - initialTime.toEpochMilli(), 0.1);
    }


    @Test
    public void testDelayFromBeginningWithoutTransfer() {
        final double FROM_LAT = 36.914893, FROM_LON = -116.76821; // NADAV stop
        final double TO_LAT = 36.914944, TO_LON = -116.761472; // NANAA stop
        GHRequest ghRequest = new GHRequest(
                FROM_LAT, FROM_LON,
                TO_LAT, TO_LON
        );

        // I want to go at 6:44
        Instant initialTime = LocalDateTime.of(2007, 1, 1, 6, 44).atZone(zoneId).toInstant();
        ghRequest.getHints().put(Parameters.PT.EARLIEST_DEPARTURE_TIME, initialTime);
        ghRequest.getHints().put(Parameters.PT.IGNORE_TRANSFERS, true);
        ghRequest.getHints().put(Parameters.PT.MAX_WALK_DISTANCE_PER_LEG, 30);

        // The 6:00 departure of my line is going to be "late" by 0 minutes
        final GtfsRealtime.FeedMessage.Builder feedMessageBuilder = GtfsRealtime.FeedMessage.newBuilder();
        feedMessageBuilder.setHeader(header());


        feedMessageBuilder.addEntityBuilder()
                .setId("1")
                .getTripUpdateBuilder()
                .setTrip(GtfsRealtime.TripDescriptor.newBuilder().setTripId("CITY2").setStartTime("06:00:00"))
                .addStopTimeUpdateBuilder()
                .setStopSequence(1)
                .setScheduleRelationship(SCHEDULED)
                .setDeparture(GtfsRealtime.TripUpdate.StopTimeEvent.newBuilder().setDelay(180).build());

        GHResponse response = graphHopperFactory.createWith(feedMessageBuilder.build(), agencyId).route(ghRequest);
        assertEquals(1, response.getAll().size());

        Trip.PtLeg ptLeg = ((Trip.PtLeg) response.getBest().getLegs().get(0));
        assertEquals("My line run is 3 minutes late.", LocalDateTime.parse("2007-01-01T06:52:00").atZone(zoneId).toInstant(), ptLeg.getArrivalTime().toInstant());
        assertEquals("It is still reporting its original, scheduled time.", LocalDateTime.parse("2007-01-01T06:49:00").atZone(zoneId).toInstant(), ptLeg.stops.get(ptLeg.stops.size()-1).plannedArrivalTime.toInstant());
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
        feedMessageBuilder.setHeader(header());
        feedMessageBuilder.addEntityBuilder()
                .setId("1")
                .getTripUpdateBuilder()
                .setTrip(GtfsRealtime.TripDescriptor.newBuilder().setTripId("AB1"))
                .addStopTimeUpdateBuilder()
                .setStopSequence(2)
                .setScheduleRelationship(SKIPPED);

        GHResponse response = graphHopperFactory.createWith(feedMessageBuilder.build(), agencyId).route(ghRequest);
        assertEquals("I can still use the AB1 trip", "AB1", (((Trip.PtLeg) response.getBest().getLegs().get(0)).trip_id));
        assertEquals("It takes", time(1,20), response.getBest().getTime());
    }

    @Test
    public void testBlockTripSkipsStop() {
        final GtfsRealtime.FeedMessage.Builder feedMessageBuilder = GtfsRealtime.FeedMessage.newBuilder();
        feedMessageBuilder.setHeader(GtfsRealtime.FeedHeader.newBuilder()
                .setGtfsRealtimeVersion("1")
                .setTimestamp(ZonedDateTime.of(LocalDate.of(2007,1,1), LocalTime.of(0,0), zoneId).toEpochSecond()));
        feedMessageBuilder.addEntityBuilder()
                .setId("1")
                .getTripUpdateBuilder()
                .setTrip(GtfsRealtime.TripDescriptor.newBuilder().setTripId("AB1"))
                .addStopTimeUpdateBuilder()
                .setStopSequence(2)
                .setScheduleRelationship(SKIPPED);

        final double FROM_LAT = 36.915682, FROM_LON = -116.751677; // STAGECOACH stop
        final double TO_LAT = 36.88108, TO_LON = -116.81797; // BULLFROG stop
        GHRequest ghRequest = new GHRequest(
                FROM_LAT, FROM_LON,
                TO_LAT, TO_LON
        );
        ghRequest.getHints().put(Parameters.PT.EARLIEST_DEPARTURE_TIME, LocalDateTime.of(2007,1,1,0,0).atZone(zoneId).toInstant());
        GHResponse route = graphHopperFactory.createWith(feedMessageBuilder.build(), agencyId).route(ghRequest);

        assertFalse(route.hasErrors());
        assertTrue(route.getAll().get(route.getAll().size()-1).isImpossible());

        // Note that my stop (BULLFROG), which is skipped, is a switch of "block legs", so even though it looks like I (impossibly) transfer there,
        // this is not a real transfer. The bus drives through BULLFROG without stopping.
        // Very untypical example, but seems correct.
        Trip.PtLeg ptLeg = (Trip.PtLeg) route.getBest().getLegs().get(3);
        assertEquals("I have to continue on AB1 which skips my stop, go all the way to the end, and ride back.", LocalDateTime.parse("2007-01-01T12:00:00").atZone(zoneId).toInstant(), ptLeg.stops.get(ptLeg.stops.size()-1).plannedArrivalTime.toInstant());
        assertEquals("Using expected route", "BFC2", ptLeg.trip_id);
    }

    @Test
    public void testMissedTransferBecauseOfDelay() {
        final double FROM_LAT = 36.914893, FROM_LON = -116.76821; // NADAV stop
        final double TO_LAT = 36.868446, TO_LON = -116.784582; // BEATTY_AIRPORT stop
        GHRequest ghRequest = new GHRequest(
                FROM_LAT, FROM_LON,
                TO_LAT, TO_LON
        );

        // I want to go at 6:44
        ghRequest.getHints().put(Parameters.PT.EARLIEST_DEPARTURE_TIME, LocalDateTime.of(2007,1,1,6,44).atZone(zoneId).toInstant());
        ghRequest.getHints().put(Parameters.PT.MAX_WALK_DISTANCE_PER_LEG, 30);

        // But the 6:00 departure of my line is going to be 5 minutes late at my transfer stop :-(
        final GtfsRealtime.FeedMessage.Builder feedMessageBuilder = GtfsRealtime.FeedMessage.newBuilder();
        feedMessageBuilder.setHeader(header());
        feedMessageBuilder.addEntityBuilder()
                .setId("1")
                .getTripUpdateBuilder()
                .setTrip(GtfsRealtime.TripDescriptor.newBuilder().setTripId("CITY2").setStartTime("06:00:00"))
                .addStopTimeUpdateBuilder()
                .setStopSequence(5)
                .setScheduleRelationship(SCHEDULED)
                .setArrival(GtfsRealtime.TripUpdate.StopTimeEvent.newBuilder().setDelay(300).build());

        GHResponse response = graphHopperFactory.createWith(feedMessageBuilder.build(), agencyId).route(ghRequest);
        assertEquals(2, response.getAll().size());

        assertEquals("The 6:44 bus will be late at STAGECOACH, so I will be 30 min late at the airport.", time(1, 6), response.getBest().getTime(), 0.1);

        PathWrapper impossibleAlternative = response.getAll().get(1);
        assertTrue(impossibleAlternative.isImpossible());
        Trip.Stop delayedStop = ((Trip.PtLeg) impossibleAlternative.getLegs().get(0)).stops.get(2);
        assertEquals("Five minutes late", 300, Duration.between(delayedStop.plannedArrivalTime.toInstant(), delayedStop.predictedArrivalTime.toInstant()).getSeconds());
    }

    @Test
    public void testMissedTransferButExtraTripOnFirstLeg() {
        final double FROM_LAT = 36.914893, FROM_LON = -116.76821; // NADAV stop
        final double TO_LAT = 36.868446, TO_LON = -116.784582; // BEATTY_AIRPORT stop
        GHRequest ghRequest = new GHRequest(
                FROM_LAT, FROM_LON,
                TO_LAT, TO_LON
        );

        // I want to go at 6:44
        ghRequest.getHints().put(Parameters.PT.EARLIEST_DEPARTURE_TIME, LocalDateTime.of(2007,1,1,6,44).atZone(zoneId).toInstant());
        ghRequest.getHints().put(Parameters.PT.MAX_WALK_DISTANCE_PER_LEG, 30);

        // But the 6:00 departure of my line is going to be 5 minutes late at my transfer stop :-(
        final GtfsRealtime.FeedMessage.Builder feedMessageBuilder = GtfsRealtime.FeedMessage.newBuilder();
        feedMessageBuilder.setHeader(header());
        feedMessageBuilder.addEntityBuilder()
                .setId("1")
                .getTripUpdateBuilder()
                .setTrip(GtfsRealtime.TripDescriptor.newBuilder().setTripId("CITY2").setStartTime("06:00:00"))
                .addStopTimeUpdateBuilder()
                .setStopSequence(5)
                .setScheduleRelationship(SCHEDULED)
                .setArrival(GtfsRealtime.TripUpdate.StopTimeEvent.newBuilder().setDelay(300).build());

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
                .setStopId("STAGECOACH")
                .setArrival(GtfsRealtime.TripUpdate.StopTimeEvent.newBuilder().setTime(LocalDateTime.of(2007,1,1,6,46).atZone(zoneId).toEpochSecond()))
                .setDeparture(GtfsRealtime.TripUpdate.StopTimeEvent.newBuilder().setTime(LocalDateTime.of(2007,1,1,6,46).atZone(zoneId).toEpochSecond()));

        GHResponse response = graphHopperFactory.createWith(feedMessageBuilder.build(), agencyId).route(ghRequest);
//        assertEquals(2, response.getAll().size());

        assertEquals("The 6:44 bus will be late at STAGECOACH, but I won't be late because there's an extra trip.", time(0, 36), response.getBest().getTime(), 0.1);
    }

    @Test
    public void testMissedTransferButExtraTripOnSecondLeg() {
        final double FROM_LAT = 36.914893, FROM_LON = -116.76821; // NADAV stop
        final double TO_LAT = 36.868446, TO_LON = -116.784582; // BEATTY_AIRPORT stop
        GHRequest ghRequest = new GHRequest(
                FROM_LAT, FROM_LON,
                TO_LAT, TO_LON
        );

        // I want to go at 6:44
        ghRequest.getHints().put(Parameters.PT.EARLIEST_DEPARTURE_TIME, LocalDateTime.of(2007,1,1,6,44).atZone(zoneId).toInstant());
        ghRequest.getHints().put(Parameters.PT.MAX_WALK_DISTANCE_PER_LEG, 30);

        // But the 6:00 departure of my line is going to be 5 minutes late at my transfer stop :-(
        final GtfsRealtime.FeedMessage.Builder feedMessageBuilder = GtfsRealtime.FeedMessage.newBuilder();
        feedMessageBuilder.setHeader(header());
        feedMessageBuilder.addEntityBuilder()
                .setId("1")
                .getTripUpdateBuilder()
                .setTrip(GtfsRealtime.TripDescriptor.newBuilder().setTripId("CITY2").setStartTime("06:00:00"))
                .addStopTimeUpdateBuilder()
                .setStopSequence(5)
                .setScheduleRelationship(SCHEDULED)
                .setArrival(GtfsRealtime.TripUpdate.StopTimeEvent.newBuilder().setDelay(300).build());

        final GtfsRealtime.TripUpdate.Builder extraTripUpdate = feedMessageBuilder.addEntityBuilder()
                .setId("2")
                .getTripUpdateBuilder()
                .setTrip(GtfsRealtime.TripDescriptor.newBuilder().setScheduleRelationship(ADDED).setTripId("EXTRA").setRouteId("STBA").setStartTime("06:45:00"));
        extraTripUpdate
                .addStopTimeUpdateBuilder()
                .setStopSequence(1)
                .setStopId("STAGECOACH")
                .setArrival(GtfsRealtime.TripUpdate.StopTimeEvent.newBuilder().setTime(LocalDateTime.of(2007,1,1,7,15).atZone(zoneId).toEpochSecond()))
                .setDeparture(GtfsRealtime.TripUpdate.StopTimeEvent.newBuilder().setTime(LocalDateTime.of(2007,1,1,7,15).atZone(zoneId).toEpochSecond()));
        extraTripUpdate
                .addStopTimeUpdateBuilder()
                .setStopSequence(2)
                .setStopId("BEATTY_AIRPORT")
                .setArrival(GtfsRealtime.TripUpdate.StopTimeEvent.newBuilder().setTime(LocalDateTime.of(2007,1,1,7,20).atZone(zoneId).toEpochSecond()))
                .setDeparture(GtfsRealtime.TripUpdate.StopTimeEvent.newBuilder().setTime(LocalDateTime.of(2007,1,1,7,20).atZone(zoneId).toEpochSecond()));

        GHResponse response = graphHopperFactory.createWith(feedMessageBuilder.build(), agencyId).route(ghRequest);
//        assertEquals(2, response.getAll().size());

        assertEquals("The 6:44 bus will be late at STAGECOACH, but I won't be late because there's an extra trip.", time(0, 36), response.getBest().getTime(), 0.1);
    }
    // TODO: Similar case, but where I need a new transfer edge for it to work
    // TODO: Similar case, but where the departure of the second leg is later than all other departures on that day.

    @Test
    public void testMissedTransferBecauseOfDelayBackwards() {
        final double FROM_LAT = 36.914893, FROM_LON = -116.76821; // NADAV stop
        final double TO_LAT = 36.868446, TO_LON = -116.784582; // BEATTY_AIRPORT stop
        GHRequest ghRequest = new GHRequest(
                FROM_LAT, FROM_LON,
                TO_LAT, TO_LON
        );

        // I want to be there at 7:20
        ghRequest.getHints().put(Parameters.PT.EARLIEST_DEPARTURE_TIME, LocalDateTime.of(2007,1,1,8,20).atZone(zoneId).toInstant());
        ghRequest.getHints().put(Parameters.PT.ARRIVE_BY, true);
        ghRequest.getHints().put(Parameters.PT.MAX_WALK_DISTANCE_PER_LEG, 30);

        // But the 6:00 departure of my line is going to skip my transfer stop :-(
        final GtfsRealtime.FeedMessage.Builder feedMessageBuilder = GtfsRealtime.FeedMessage.newBuilder();
        feedMessageBuilder.setHeader(header());
        feedMessageBuilder.addEntityBuilder()
                .setId("1")
                .getTripUpdateBuilder()
                .setTrip(GtfsRealtime.TripDescriptor.newBuilder().setTripId("CITY2").setStartTime("07:00:00"))
                .addStopTimeUpdateBuilder()
                .setStopSequence(5)
                .setScheduleRelationship(SCHEDULED)
                .setArrival(GtfsRealtime.TripUpdate.StopTimeEvent.newBuilder().setDelay(300).build());

        GraphHopperGtfs graphHopper = graphHopperFactory.createWith(feedMessageBuilder.build(), agencyId);
        GHResponse response = graphHopper.route(ghRequest);
        assertEquals(2, response.getAll().size());

        assertEquals("The 7:44 bus will not call at STAGECOACH, so I will be 30 min late at the airport.", time(1, 6), response.getBest().getTime(), 0.1);

        PathWrapper impossibleAlternative = response.getAll().get(1);
        assertTrue(impossibleAlternative.isImpossible());
        Trip.Stop delayedStop = ((Trip.PtLeg) impossibleAlternative.getLegs().get(0)).stops.get(2);
        assertEquals("Five minutes late", 300, Duration.between(delayedStop.plannedArrivalTime.toInstant(), delayedStop.predictedArrivalTime.toInstant()).getSeconds());

        // But when I ask about tomorrow, it works as planned
        ghRequest.getHints().put(Parameters.PT.EARLIEST_DEPARTURE_TIME, LocalDateTime.of(2007,1,2,8,20).atZone(zoneId).toInstant());
        response = graphHopper.route(ghRequest);
        assertEquals(1, response.getAll().size());

        Trip.Stop notDelayedStop = ((Trip.PtLeg) response.getBest().getLegs().get(0)).stops.get(2);
        assertNull("Not late", notDelayedStop.predictedArrivalTime);

    }

    @Test
    public void testDelayAtEndForNonFrequencyBasedTrip() {
        final double FROM_LAT = 36.915682, FROM_LON = -116.751677; // STAGECOACH stop
        final double TO_LAT = 36.88108, TO_LON = -116.81797; // BULLFROG stop
        GHRequest ghRequest = new GHRequest(
                FROM_LAT, FROM_LON,
                TO_LAT, TO_LON
        );
        ghRequest.getHints().put(Parameters.PT.EARLIEST_DEPARTURE_TIME, LocalDateTime.of(2007,1,1,0,0).atZone(zoneId).toInstant());

        final GtfsRealtime.FeedMessage.Builder feedMessageBuilder = GtfsRealtime.FeedMessage.newBuilder();
        feedMessageBuilder.setHeader(header());
        feedMessageBuilder.addEntityBuilder()
                .setId("1")
                .getTripUpdateBuilder()
                .setTrip(GtfsRealtime.TripDescriptor.newBuilder().setTripId("AB1"))
                .addStopTimeUpdateBuilder()
                .setStopSequence(2)
                .setScheduleRelationship(SCHEDULED)
                .setArrival(GtfsRealtime.TripUpdate.StopTimeEvent.newBuilder().setDelay(300).build());

        GraphHopperGtfs graphHopper = graphHopperFactory.createWith(feedMessageBuilder.build(), agencyId);
        GHResponse route = graphHopper.route(ghRequest);

        assertFalse(route.hasErrors());
        assertFalse(route.getAll().isEmpty());
        assertEquals("Travel time == predicted travel time", time(8, 15), route.getBest().getTime(), 0.1);
        assertEquals("Using expected route", "STBA", (((Trip.PtLeg) route.getBest().getLegs().get(0)).trip_id));
        assertEquals("Using expected route", "AB1", (((Trip.PtLeg) route.getBest().getLegs().get(1)).trip_id));
        assertEquals("Delay at destination", LocalTime.parse("08:15"), LocalTime.from(((Trip.PtLeg) route.getBest().getLegs().get(1)).stops.get(1).predictedArrivalTime.toInstant().atZone(zoneId)));
        assertEquals("Paid expected fare", 250, route.getBest().getFare().multiply(BigDecimal.valueOf(100)).intValue()); // Two legs, no transfers allowed. Need two 'p' tickets costing 125 cents each.
    }


    public GtfsRealtime.FeedHeader.Builder header() {
        return GtfsRealtime.FeedHeader.newBuilder()
                .setGtfsRealtimeVersion("1")
                .setTimestamp(LocalDateTime.of(2007,1,1,0,0).atZone(zoneId).toInstant().toEpochMilli() / 1000);
    }


}