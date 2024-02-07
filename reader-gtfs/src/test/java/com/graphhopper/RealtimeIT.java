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
import com.graphhopper.config.Profile;
import com.graphhopper.gtfs.GraphHopperGtfs;
import com.graphhopper.gtfs.PtRouter;
import com.graphhopper.gtfs.PtRouterImpl;
import com.graphhopper.gtfs.Request;
import com.graphhopper.util.Helper;
import com.graphhopper.util.TranslationMap;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.math.BigDecimal;
import java.time.*;
import java.util.Arrays;

import static com.google.transit.realtime.GtfsRealtime.TripDescriptor.ScheduleRelationship.ADDED;
import static com.google.transit.realtime.GtfsRealtime.TripUpdate.StopTimeUpdate.ScheduleRelationship.SCHEDULED;
import static com.google.transit.realtime.GtfsRealtime.TripUpdate.StopTimeUpdate.ScheduleRelationship.SKIPPED;
import static com.graphhopper.gtfs.GtfsHelper.time;
import static org.junit.jupiter.api.Assertions.*;

public class RealtimeIT {

    private static final String GRAPH_LOC = "target/RealtimeIT";
    private static final ZoneId zoneId = ZoneId.of("America/Los_Angeles");
    private static PtRouterImpl.Factory graphHopperFactory;
    private static GraphHopperGtfs graphHopperGtfs;

    @BeforeAll
    public static void init() {
        GraphHopperConfig ghConfig = new GraphHopperConfig();
        ghConfig.putObject("gtfs.file", "files/sample-feed");
        ghConfig.putObject("graph.location", GRAPH_LOC);
        ghConfig.putObject("import.osm.ignored_highways", "");
        ghConfig.setProfiles(Arrays.asList(
                new Profile("foot").setVehicle("foot").setCustomModel(Helper.createBaseModel("foot")),
                new Profile("car").setVehicle("car").setCustomModel(Helper.createBaseModel("car"))));
        Helper.removeDir(new File(GRAPH_LOC));
        graphHopperGtfs = new GraphHopperGtfs(ghConfig);
        graphHopperGtfs.init(ghConfig);
        graphHopperGtfs.importOrLoad();

        graphHopperGtfs.close();
        // Re-load read only
        graphHopperGtfs = new GraphHopperGtfs(ghConfig);
        graphHopperGtfs.init(ghConfig);
        graphHopperGtfs.importOrLoad();

        graphHopperFactory = new PtRouterImpl.Factory(ghConfig, new TranslationMap().doImport(), graphHopperGtfs.getBaseGraph(), graphHopperGtfs.getEncodingManager(), graphHopperGtfs.getLocationIndex(), graphHopperGtfs.getGtfsStorage());
    }

    @AfterAll
    public static void close() {
        graphHopperGtfs.close();
    }

    @Test
    public void testSkipDepartureStop() {
        final double FROM_LAT = 36.914893, FROM_LON = -116.76821; // NADAV stop
        final double TO_LAT = 36.914944, TO_LON = -116.761472; // NANAA stop
        Request ghRequest = new Request(
                FROM_LAT, FROM_LON,
                TO_LAT, TO_LON
        );

        // I want to go at 6:44
        ghRequest.setEarliestDepartureTime(LocalDateTime.of(2007, 1, 1, 6, 44).atZone(zoneId).toInstant());

        // But the 6:00 departure of my line is going to skip my departure stop :-(
        final GtfsRealtime.FeedMessage.Builder feedMessageBuilder = GtfsRealtime.FeedMessage.newBuilder();
        feedMessageBuilder.setHeader(GtfsRealtime.FeedHeader.newBuilder()
                .setGtfsRealtimeVersion("1")
                .setTimestamp(ZonedDateTime.of(LocalDate.of(2007, 1, 1), LocalTime.of(0, 0), zoneId).toEpochSecond()));
        feedMessageBuilder.addEntityBuilder()
                .setId("1")
                .getTripUpdateBuilder()
                .setTrip(GtfsRealtime.TripDescriptor.newBuilder().setTripId("CITY2").setStartTime("06:00:00"))
                .addStopTimeUpdateBuilder()
                .setStopSequence(3)
                .setScheduleRelationship(SKIPPED);

        GHResponse response = graphHopperFactory.createWith(feedMessageBuilder.build()).route(ghRequest);

        ResponsePath possibleAlternative = response.getAll().stream().filter(a -> !a.isImpossible()).findFirst().get();
        assertFalse(((Trip.PtLeg) possibleAlternative.getLegs().get(0)).stops.get(0).departureCancelled);
        assertEquals(time(0, 35), possibleAlternative.getTime(), 0.1, "I have to wait half an hour for the next one (and ride 5 minutes)");

        ResponsePath impossibleAlternative = response.getAll().stream().filter(a -> a.isImpossible()).findFirst().get();
        assertTrue(impossibleAlternative.isImpossible());
        assertTrue(((Trip.PtLeg) impossibleAlternative.getLegs().get(0)).stops.get(0).departureCancelled);
    }

    @Test
    public void testHeavyDelayWhereWeShouldTakeOtherTripInstead() {
        final double FROM_LAT = 36.914893, FROM_LON = -116.76821; // NADAV stop
        final double TO_LAT = 36.914944, TO_LON = -116.761472; // NANAA stop
        Request ghRequest = new Request(
                FROM_LAT, FROM_LON,
                TO_LAT, TO_LON
        );

        // I want to go at 6:44
        ghRequest.setEarliestDepartureTime(LocalDateTime.of(2007, 1, 1, 6, 44).atZone(zoneId).toInstant());

        // But the 6:00 departure of my line is going to be super-late :-(
        final GtfsRealtime.FeedMessage.Builder feedMessageBuilder = GtfsRealtime.FeedMessage.newBuilder();
        feedMessageBuilder.setHeader(GtfsRealtime.FeedHeader.newBuilder()
                .setGtfsRealtimeVersion("1")
                .setTimestamp(ZonedDateTime.of(LocalDate.of(2007, 1, 1), LocalTime.of(0, 0), zoneId).toEpochSecond()));
        feedMessageBuilder.addEntityBuilder()
                .setId("1")
                .getTripUpdateBuilder()
                .setTrip(GtfsRealtime.TripDescriptor.newBuilder().setTripId("CITY2").setStartTime("06:00:00"))
                .addStopTimeUpdateBuilder()
                .setScheduleRelationship(SCHEDULED)
                .setStopSequence(3)
                .setArrival(GtfsRealtime.TripUpdate.StopTimeEvent.newBuilder().setDelay(3600).build());

        GHResponse response = graphHopperFactory.createWith(feedMessageBuilder.build()).route(ghRequest);
        assertEquals(2, response.getAll().size());

        ResponsePath best = response.getBest();
        Trip.PtLeg bestPtLeg = (Trip.PtLeg) best.getLegs().get(0);
        assertEquals(LocalDateTime.parse("2007-01-01T07:19:00").atZone(zoneId).toInstant(), bestPtLeg.stops.get(bestPtLeg.stops.size() - 1).plannedArrivalTime.toInstant(), "It's better to wait half an hour for the next one (and ride 5 minutes).");
        assertNull(bestPtLeg.stops.get(bestPtLeg.stops.size() - 1).predictedArrivalTime, "There is no predicted arrival time.");

        ResponsePath impossibleAlternative = response.getAll().get(1);
        assertTrue(impossibleAlternative.isImpossible());
        Trip.PtLeg impossiblePtLeg = (Trip.PtLeg) impossibleAlternative.getLegs().get(0);
        assertEquals(LocalDateTime.parse("2007-01-01T06:49:00").atZone(zoneId).toInstant(), impossiblePtLeg.stops.get(impossiblePtLeg.stops.size() - 1).plannedArrivalTime.toInstant(), "The impossible alternative is my planned 5-minute-trip");
        assertEquals(LocalDateTime.parse("2007-01-01T07:49:00").atZone(zoneId).toInstant(), impossiblePtLeg.stops.get(impossiblePtLeg.stops.size() - 1).predictedArrivalTime.toInstant(), "..which is very late today");
    }

    @Test
    public void testCanUseDelayedTripWhenIAmLateToo() {
        final double FROM_LAT = 36.914893, FROM_LON = -116.76821; // NADAV stop
        final double TO_LAT = 36.914944, TO_LON = -116.761472; // NANAA stop
        Request ghRequest = new Request(
                FROM_LAT, FROM_LON,
                TO_LAT, TO_LON
        );

        // I want to go at 6:44
        ghRequest.setEarliestDepartureTime(LocalDateTime.of(2007, 1, 1, 6, 46).atZone(zoneId).toInstant());

        // But the 6:00 departure of my line is going to be super-late :-(
        final GtfsRealtime.FeedMessage.Builder feedMessageBuilder = GtfsRealtime.FeedMessage.newBuilder();
        feedMessageBuilder.setHeader(GtfsRealtime.FeedHeader.newBuilder()
                .setGtfsRealtimeVersion("1")
                .setTimestamp(ZonedDateTime.of(LocalDate.of(2007, 1, 1), LocalTime.of(0, 0), zoneId).toEpochSecond()));
        feedMessageBuilder.addEntityBuilder()
                .setId("1")
                .getTripUpdateBuilder()
                .setTrip(GtfsRealtime.TripDescriptor.newBuilder().setTripId("CITY2").setStartTime("06:00:00"))
                .addStopTimeUpdateBuilder()
                .setScheduleRelationship(SCHEDULED)
                .setStopSequence(3)
                .setArrival(GtfsRealtime.TripUpdate.StopTimeEvent.newBuilder().setDelay(120).build());

        GHResponse response = graphHopperFactory.createWith(feedMessageBuilder.build()).route(ghRequest);

        assertEquals(time(0, 5), response.getBest().getTime(), 0.1, "I am two minutes late for my bus, but the bus is two minutes late, too, so I catch it!");
    }

    @Test
    public void testSkipArrivalStop() {
        final double FROM_LAT = 36.914893, FROM_LON = -116.76821; // NADAV stop
        final double TO_LAT = 36.914944, TO_LON = -116.761472; // NANAA stop
        Request ghRequest = new Request(
                FROM_LAT, FROM_LON,
                TO_LAT, TO_LON
        );

        // I want to go at 6:44
        ghRequest.setEarliestDepartureTime(LocalDateTime.of(2007, 1, 1, 6, 44).atZone(zoneId).toInstant());

        // But the 6:00 departure of my line is going to skip my arrival stop :-(
        final GtfsRealtime.FeedMessage.Builder feedMessageBuilder = GtfsRealtime.FeedMessage.newBuilder();
        feedMessageBuilder.setHeader(GtfsRealtime.FeedHeader.newBuilder()
                .setGtfsRealtimeVersion("1")
                .setTimestamp(ZonedDateTime.of(LocalDate.of(2007, 1, 1), LocalTime.of(0, 0), zoneId).toEpochSecond()));
        feedMessageBuilder.addEntityBuilder()
                .setId("1")
                .getTripUpdateBuilder()
                .setTrip(GtfsRealtime.TripDescriptor.newBuilder().setTripId("CITY2").setStartTime("06:00:00"))
                .addStopTimeUpdateBuilder()
                .setStopSequence(4)
                .setScheduleRelationship(SKIPPED);

        GHResponse response = graphHopperFactory.createWith(feedMessageBuilder.build()).route(ghRequest);
        assertEquals(3, response.getAll().size());

        assertEquals(time(0, 21), response.getBest().getTime(), 0.1, "I have to continue to STAGECOACH and then go back one stop with the 07:00 bus.");

        ResponsePath impossibleAlternative = response.getAll().get(2);
        assertTrue(impossibleAlternative.isImpossible());
        assertTrue(((Trip.PtLeg) impossibleAlternative.getLegs().get(0)).stops.get(1).arrivalCancelled);
    }

    @Test
    public void testSkipTransferStop() {
        final double FROM_LAT = 36.914893, FROM_LON = -116.76821; // NADAV stop
        final double TO_LAT = 36.868446, TO_LON = -116.784582; // BEATTY_AIRPORT stop
        Request ghRequest = new Request(
                FROM_LAT, FROM_LON,
                TO_LAT, TO_LON
        );

        // I want to go at 6:44
        ghRequest.setEarliestDepartureTime(LocalDateTime.of(2007, 1, 1, 6, 44).atZone(zoneId).toInstant());

        // But the 6:00 departure of my line is going to skip my transfer stop :-(
        final GtfsRealtime.FeedMessage.Builder feedMessageBuilder = GtfsRealtime.FeedMessage.newBuilder();
        feedMessageBuilder.setHeader(GtfsRealtime.FeedHeader.newBuilder()
                .setGtfsRealtimeVersion("1")
                .setTimestamp(ZonedDateTime.of(LocalDate.of(2007, 1, 1), LocalTime.of(0, 0), zoneId).toEpochSecond()));
        feedMessageBuilder.addEntityBuilder()
                .setId("1")
                .getTripUpdateBuilder()
                .setTrip(GtfsRealtime.TripDescriptor.newBuilder().setTripId("CITY2").setStartTime("06:00:00"))
                .addStopTimeUpdateBuilder()
                .setStopSequence(5)
                .setScheduleRelationship(SKIPPED);

        GHResponse response = graphHopperFactory.createWith(feedMessageBuilder.build()).route(ghRequest);
        assertEquals(2, response.getAll().size());

        assertEquals(time(1, 6), response.getBest().getTime(), 0.1, "The 6:44 bus will not call at STAGECOACH, so I will be 30 min late at the airport.");

        ResponsePath impossibleAlternative = response.getAll().get(1);
        assertTrue(impossibleAlternative.isImpossible());
        assertTrue(((Trip.PtLeg) impossibleAlternative.getLegs().get(0)).stops.get(2).departureCancelled);
    }

    @Test
    public void testExtraTrip() {
        final double FROM_LAT = 36.914893, FROM_LON = -116.76821; // NADAV stop
        final double TO_LAT = 36.868446, TO_LON = -116.784582; // BEATTY_AIRPORT stop
        Request ghRequest = new Request(
                FROM_LAT, FROM_LON,
                TO_LAT, TO_LON
        );

        // I want to go at 6:44
        ghRequest.setEarliestDepartureTime(LocalDateTime.of(2007, 1, 1, 6, 44).atZone(zoneId).toInstant());
        ghRequest.setIgnoreTransfers(true);

        // But the 6:00 departure of my line is going to skip my transfer stop :-(
        final GtfsRealtime.FeedMessage.Builder feedMessageBuilder = GtfsRealtime.FeedMessage.newBuilder();
        feedMessageBuilder.setHeader(GtfsRealtime.FeedHeader.newBuilder()
                .setGtfsRealtimeVersion("1")
                .setTimestamp(ZonedDateTime.of(LocalDate.of(2007, 1, 1), LocalTime.of(0, 0), zoneId).toEpochSecond()));


        feedMessageBuilder.addEntityBuilder()
                .setId("1")
                .getTripUpdateBuilder()
                .setTrip(GtfsRealtime.TripDescriptor.newBuilder().setTripId("CITY2").setStartTime("06:00:00"))
                .addStopTimeUpdateBuilder()
                .setStopSequence(5)
                .setScheduleRelationship(SKIPPED);

        // Add a few more trips (but we only need the first one; add more because there used to be a bug with something like an index overflow)
        for (int i = 0; i < 1; i++) {
            final GtfsRealtime.TripUpdate.Builder extraTripUpdate = feedMessageBuilder.addEntityBuilder()
                    .setId("2")
                    .getTripUpdateBuilder()
                    .setTrip(GtfsRealtime.TripDescriptor.newBuilder().setScheduleRelationship(ADDED).setTripId("EXTRA" + i).setRouteId("CITY").setStartTime("06:45:0" + i));
            extraTripUpdate
                    .addStopTimeUpdateBuilder()
                    .setStopSequence(1)
                    .setStopId("NADAV")
                    .setArrival(GtfsRealtime.TripUpdate.StopTimeEvent.newBuilder().setTime(LocalDateTime.of(2007, 1, 1, 6, 45).plusMinutes(i).atZone(zoneId).toEpochSecond()))
                    .setDeparture(GtfsRealtime.TripUpdate.StopTimeEvent.newBuilder().setTime(LocalDateTime.of(2007, 1, 1, 6, 45).plusMinutes(i).atZone(zoneId).toEpochSecond()));
            extraTripUpdate
                    .addStopTimeUpdateBuilder()
                    .setStopSequence(2)
                    .setStopId("BEATTY_AIRPORT")
                    .setArrival(GtfsRealtime.TripUpdate.StopTimeEvent.newBuilder().setTime(LocalDateTime.of(2007, 1, 1, 7, 15).plusMinutes(i).atZone(zoneId).toEpochSecond()))
                    .setDeparture(GtfsRealtime.TripUpdate.StopTimeEvent.newBuilder().setTime(LocalDateTime.of(2007, 1, 1, 7, 15).plusMinutes(i).atZone(zoneId).toEpochSecond()));

        }

        GHResponse response = graphHopperFactory.createWith(feedMessageBuilder.build()).route(ghRequest);
        assertEquals(1, response.getAll().size());

        assertEquals(time(0, 31), response.getBest().getTime(), 0.1, "Luckily, there is an extra service directly from my stop to the airport, at 6:45, taking 30 minutes");

        ResponsePath solution = response.getAll().get(0);
        Trip.PtLeg ptLeg = ((Trip.PtLeg) solution.getLegs().stream().filter(leg -> leg instanceof Trip.PtLeg).findFirst().get());
        assertEquals("EXTRA0", ptLeg.trip_id);
    }

    @Test
    public void testExtraTripWorksOnlyOnSpecifiedDay() {
        final double FROM_LAT = 36.914893, FROM_LON = -116.76821; // NADAV stop
        final double TO_LAT = 36.868446, TO_LON = -116.784582; // BEATTY_AIRPORT stop
        Request ghRequest = new Request(
                FROM_LAT, FROM_LON,
                TO_LAT, TO_LON
        );

        // I want to go at 6:45, but tomorrow
        ghRequest.setEarliestDepartureTime(LocalDateTime.of(2007, 1, 2, 6, 45).atZone(zoneId).toInstant());
        ghRequest.setIgnoreTransfers(true);

        final GtfsRealtime.FeedMessage.Builder feedMessageBuilder = GtfsRealtime.FeedMessage.newBuilder();
        feedMessageBuilder.setHeader(GtfsRealtime.FeedHeader.newBuilder()
                .setGtfsRealtimeVersion("1")
                .setTimestamp(ZonedDateTime.of(LocalDate.of(2007, 1, 1), LocalTime.of(0, 0), zoneId).toEpochSecond()));

        // Today, there is an extra trip right at 6:45, but that doesn't concern me.
        final GtfsRealtime.TripUpdate.Builder extraTripUpdate = feedMessageBuilder.addEntityBuilder()
                .setId("2")
                .getTripUpdateBuilder()
                .setTrip(GtfsRealtime.TripDescriptor.newBuilder().setScheduleRelationship(ADDED).setTripId("EXTRA").setRouteId("CITY").setStartTime("06:45:00"));
        extraTripUpdate
                .addStopTimeUpdateBuilder()
                .setStopSequence(1)
                .setStopId("NADAV")
                .setArrival(GtfsRealtime.TripUpdate.StopTimeEvent.newBuilder().setTime(LocalDateTime.of(2007, 1, 1, 6, 45).atZone(zoneId).toEpochSecond()))
                .setDeparture(GtfsRealtime.TripUpdate.StopTimeEvent.newBuilder().setTime(LocalDateTime.of(2007, 1, 1, 6, 45).atZone(zoneId).toEpochSecond()));
        extraTripUpdate
                .addStopTimeUpdateBuilder()
                .setStopSequence(2)
                .setStopId("BEATTY_AIRPORT")
                .setArrival(GtfsRealtime.TripUpdate.StopTimeEvent.newBuilder().setTime(LocalDateTime.of(2007, 1, 1, 7, 15).atZone(zoneId).toEpochSecond()))
                .setDeparture(GtfsRealtime.TripUpdate.StopTimeEvent.newBuilder().setTime(LocalDateTime.of(2007, 1, 1, 7, 15).atZone(zoneId).toEpochSecond()));


        GHResponse response = graphHopperFactory.createWith(feedMessageBuilder.build()).route(ghRequest);
        assertEquals(1, response.getAll().size());

        assertEquals(time(1, 5), response.getBest().getTime(), 0.1, "There is an extra trip at 6:45 tomorrow, but that doesn't concern me today.");
    }

    @Test
    public void testZeroDelay() {
        final double FROM_LAT = 36.914893, FROM_LON = -116.76821; // NADAV stop
        final double TO_LAT = 36.914944, TO_LON = -116.761472; // NANAA stop
        Request ghRequest = new Request(
                FROM_LAT, FROM_LON,
                TO_LAT, TO_LON
        );

        // I want to go at 6:44
        ghRequest.setEarliestDepartureTime(LocalDateTime.of(2007, 1, 1, 6, 44).atZone(zoneId).toInstant());
        ghRequest.setIgnoreTransfers(true);

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

        GHResponse responseWithRealtimeUpdate = graphHopperFactory.createWith(feedMessageBuilder.build()).route(ghRequest);
        assertEquals(1, responseWithRealtimeUpdate.getAll().size());

        Trip.PtLeg responseWithRealtimeUpdateBest = (Trip.PtLeg) responseWithRealtimeUpdate.getBest().getLegs().get(0);
        Trip.PtLeg responseWithoutRealtimeUpdateBest = (Trip.PtLeg) responseWithoutRealtimeUpdate.getBest().getLegs().get(0);
        assertEquals(LocalDateTime.parse("2007-01-01T06:49:00").atZone(zoneId).toInstant(), responseWithRealtimeUpdateBest.stops.get(responseWithRealtimeUpdateBest.stops.size() - 1).plannedArrivalTime.toInstant(), "My planned arrival time is correct.");
        assertEquals(LocalDateTime.parse("2007-01-01T06:49:00").atZone(zoneId).toInstant(), responseWithRealtimeUpdateBest.stops.get(responseWithRealtimeUpdateBest.stops.size() - 1).predictedArrivalTime.toInstant(), "My expected arrival time is the same.");
        assertNull(responseWithoutRealtimeUpdateBest.stops.get(responseWithoutRealtimeUpdateBest.stops.size() - 1).predictedArrivalTime, "The trip without realtime update does not have an expected arrival time.");

//        assertEquals(responseWithoutRealtimeUpdateBest.toString(), responseWithRealtimeUpdateBest.toString());
    }

    @Test
    public void testDelayWithoutTransfer() {
        final double FROM_LAT = 36.914893, FROM_LON = -116.76821; // NADAV stop
        final double TO_LAT = 36.914944, TO_LON = -116.761472; // NANAA stop
        Request ghRequest = new Request(
                FROM_LAT, FROM_LON,
                TO_LAT, TO_LON
        );

        // I want to go at 6:44
        Instant initialTime = LocalDateTime.of(2007, 1, 1, 6, 44).atZone(zoneId).toInstant();
        ghRequest.setEarliestDepartureTime(initialTime);
        ghRequest.setIgnoreTransfers(true);

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

        GHResponse response = graphHopperFactory.createWith(feedMessageBuilder.build()).route(ghRequest);
        assertEquals(1, response.getAll().size());

        assertEquals(time(0, 8), response.getBest().getLegs().get(response.getBest().getLegs().size() - 1).getArrivalTime().toInstant().toEpochMilli() - initialTime.toEpochMilli(), 0.1, "My line run is 3 minutes late.");
    }


    @Test
    public void testDelayFromBeginningWithoutTransfer() {
        final double FROM_LAT = 36.914893, FROM_LON = -116.76821; // NADAV stop
        final double TO_LAT = 36.914944, TO_LON = -116.761472; // NANAA stop
        Request ghRequest = new Request(
                FROM_LAT, FROM_LON,
                TO_LAT, TO_LON
        );

        // I want to go at 6:44
        Instant initialTime = LocalDateTime.of(2007, 1, 1, 6, 44).atZone(zoneId).toInstant();
        ghRequest.setEarliestDepartureTime(initialTime);
        ghRequest.setIgnoreTransfers(true);

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

        GHResponse response = graphHopperFactory.createWith(feedMessageBuilder.build()).route(ghRequest);
        assertEquals(1, response.getAll().size());

        Trip.PtLeg ptLeg = ((Trip.PtLeg) response.getBest().getLegs().get(0));
        assertEquals(LocalDateTime.parse("2007-01-01T06:52:00").atZone(zoneId).toInstant(), ptLeg.getArrivalTime().toInstant(), "My line run is 3 minutes late.");
        assertEquals(LocalDateTime.parse("2007-01-01T06:49:00").atZone(zoneId).toInstant(), ptLeg.stops.get(ptLeg.stops.size() - 1).plannedArrivalTime.toInstant(), "It is still reporting its original, scheduled time.");
    }

    @Test
    public void testBlockTrips() {
        final double FROM_LAT = 36.868446, FROM_LON = -116.784582; // BEATTY_AIRPORT stop
        final double TO_LAT = 36.425288, TO_LON = -117.133162; // FUR_CREEK_RES stop
        Request ghRequest = new Request(
                FROM_LAT, FROM_LON,
                TO_LAT, TO_LON
        );
        ghRequest.setEarliestDepartureTime(LocalDateTime.of(2007, 1, 1, 8, 0).atZone(zoneId).toInstant());
        ghRequest.setIgnoreTransfers(true);

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

        GHResponse response = graphHopperFactory.createWith(feedMessageBuilder.build()).route(ghRequest);
        assertEquals("AB1", (((Trip.PtLeg) response.getBest().getLegs().get(0)).trip_id), "I can still use the AB1 trip");
        assertEquals(time(1, 20), response.getBest().getTime(), "It takes");
    }

    @Test
    public void testBlockTripSkipsStop() {
        final GtfsRealtime.FeedMessage.Builder feedMessageBuilder = GtfsRealtime.FeedMessage.newBuilder();
        feedMessageBuilder.setHeader(GtfsRealtime.FeedHeader.newBuilder()
                .setGtfsRealtimeVersion("1")
                .setTimestamp(ZonedDateTime.of(LocalDate.of(2007, 1, 1), LocalTime.of(0, 0), zoneId).toEpochSecond()));
        feedMessageBuilder.addEntityBuilder()
                .setId("1")
                .getTripUpdateBuilder()
                .setTrip(GtfsRealtime.TripDescriptor.newBuilder().setTripId("AB1"))
                .addStopTimeUpdateBuilder()
                .setStopSequence(2)
                .setScheduleRelationship(SKIPPED);

        final double FROM_LAT = 36.915682, FROM_LON = -116.751677; // STAGECOACH stop
        final double TO_LAT = 36.88108, TO_LON = -116.81797; // BULLFROG stop
        Request ghRequest = new Request(
                FROM_LAT, FROM_LON,
                TO_LAT, TO_LON
        );
        ghRequest.setEarliestDepartureTime(LocalDateTime.of(2007, 1, 1, 0, 0).atZone(zoneId).toInstant());
        GHResponse route = graphHopperFactory.createWith(feedMessageBuilder.build()).route(ghRequest);

        assertFalse(route.hasErrors());
        assertTrue(route.getAll().get(route.getAll().size() - 1).isImpossible());

        // Note that my stop (BULLFROG), which is skipped, is a switch of "block legs", so even though it looks like I (impossibly) transfer there,
        // this is not a real transfer. The bus drives through BULLFROG without stopping.
        // Very untypical example, but seems correct.
        Trip.PtLeg ptLeg = (Trip.PtLeg) route.getBest().getLegs().get(3);
        assertEquals(LocalDateTime.parse("2007-01-01T12:00:00").atZone(zoneId).toInstant(), ptLeg.stops.get(ptLeg.stops.size() - 1).plannedArrivalTime.toInstant(), "I have to continue on AB1 which skips my stop, go all the way to the end, and ride back.");
        assertEquals("BFC2", ptLeg.trip_id, "Using expected route");
    }

    @Test
    public void testMissedTransferBecauseOfDelay() {
        final double FROM_LAT = 36.914893, FROM_LON = -116.76821; // NADAV stop
        final double TO_LAT = 36.868446, TO_LON = -116.784582; // BEATTY_AIRPORT stop
        Request ghRequest = new Request(
                FROM_LAT, FROM_LON,
                TO_LAT, TO_LON
        );

        // I want to go at 6:44
        ghRequest.setEarliestDepartureTime(LocalDateTime.of(2007, 1, 1, 6, 44).atZone(zoneId).toInstant());

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

        GHResponse response = graphHopperFactory.createWith(feedMessageBuilder.build()).route(ghRequest);
        assertEquals(2, response.getAll().size());

        assertEquals(time(1, 6), response.getBest().getTime(), 0.1, "The 6:44 bus will be late at STAGECOACH, so I will be 30 min late at the airport.");

        ResponsePath impossibleAlternative = response.getAll().get(1);
        assertTrue(impossibleAlternative.isImpossible());
        Trip.Stop delayedStop = ((Trip.PtLeg) impossibleAlternative.getLegs().get(0)).stops.get(2);
        assertEquals(300, Duration.between(delayedStop.plannedArrivalTime.toInstant(), delayedStop.predictedArrivalTime.toInstant()).getSeconds(), "Five minutes late");
    }

    @Test
    public void testMissedTransferButExtraTripOnFirstLeg() {
        final double FROM_LAT = 36.914893, FROM_LON = -116.76821; // NADAV stop
        final double TO_LAT = 36.868446, TO_LON = -116.784582; // BEATTY_AIRPORT stop
        Request ghRequest = new Request(
                FROM_LAT, FROM_LON,
                TO_LAT, TO_LON
        );

        // I want to go at 6:44
        ghRequest.setEarliestDepartureTime(LocalDateTime.of(2007, 1, 1, 6, 44).atZone(zoneId).toInstant());

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
                .setArrival(GtfsRealtime.TripUpdate.StopTimeEvent.newBuilder().setTime(LocalDateTime.of(2007, 1, 1, 6, 45).atZone(zoneId).toEpochSecond()))
                .setDeparture(GtfsRealtime.TripUpdate.StopTimeEvent.newBuilder().setTime(LocalDateTime.of(2007, 1, 1, 6, 45).atZone(zoneId).toEpochSecond()));
        extraTripUpdate
                .addStopTimeUpdateBuilder()
                .setStopSequence(2)
                .setStopId("STAGECOACH")
                .setArrival(GtfsRealtime.TripUpdate.StopTimeEvent.newBuilder().setTime(LocalDateTime.of(2007, 1, 1, 6, 46).atZone(zoneId).toEpochSecond()))
                .setDeparture(GtfsRealtime.TripUpdate.StopTimeEvent.newBuilder().setTime(LocalDateTime.of(2007, 1, 1, 6, 46).atZone(zoneId).toEpochSecond()));

        GHResponse response = graphHopperFactory.createWith(feedMessageBuilder.build()).route(ghRequest);
//        assertEquals(2, response.getAll().size());

        assertEquals(time(0, 36), response.getBest().getTime(), 0.1, "The 6:44 bus will be late at STAGECOACH, but I won't be late because there's an extra trip.");
    }

    @Test
    public void testMissedTransferButExtraTripOnSecondLeg() {
        final double FROM_LAT = 36.914893, FROM_LON = -116.76821; // NADAV stop
        final double TO_LAT = 36.868446, TO_LON = -116.784582; // BEATTY_AIRPORT stop
        Request ghRequest = new Request(
                FROM_LAT, FROM_LON,
                TO_LAT, TO_LON
        );

        // I want to go at 6:44
        ghRequest.setEarliestDepartureTime(LocalDateTime.of(2007, 1, 1, 6, 44).atZone(zoneId).toInstant());

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
                .setArrival(GtfsRealtime.TripUpdate.StopTimeEvent.newBuilder().setTime(LocalDateTime.of(2007, 1, 1, 7, 15).atZone(zoneId).toEpochSecond()))
                .setDeparture(GtfsRealtime.TripUpdate.StopTimeEvent.newBuilder().setTime(LocalDateTime.of(2007, 1, 1, 7, 15).atZone(zoneId).toEpochSecond()));
        extraTripUpdate
                .addStopTimeUpdateBuilder()
                .setStopSequence(2)
                .setStopId("BEATTY_AIRPORT")
                .setArrival(GtfsRealtime.TripUpdate.StopTimeEvent.newBuilder().setTime(LocalDateTime.of(2007, 1, 1, 7, 20).atZone(zoneId).toEpochSecond()))
                .setDeparture(GtfsRealtime.TripUpdate.StopTimeEvent.newBuilder().setTime(LocalDateTime.of(2007, 1, 1, 7, 20).atZone(zoneId).toEpochSecond()));

        GHResponse response = graphHopperFactory.createWith(feedMessageBuilder.build()).route(ghRequest);
//        assertEquals(2, response.getAll().size());

        assertEquals(time(0, 36), response.getBest().getTime(), 0.1, "The 6:44 bus will be late at STAGECOACH, but I won't be late because there's an extra trip.");
    }
    // TODO: Similar case, but where I need a new transfer edge for it to work
    // TODO: Similar case, but where the departure of the second leg is later than all other departures on that day.

    @Test
    public void testMissedTransferBecauseOfDelayBackwards() {
        final double FROM_LAT = 36.914893, FROM_LON = -116.76821; // NADAV stop
        final double TO_LAT = 36.868446, TO_LON = -116.784582; // BEATTY_AIRPORT stop
        Request ghRequest = new Request(
                FROM_LAT, FROM_LON,
                TO_LAT, TO_LON
        );

        // I want to be there at 7:20
        ghRequest.setEarliestDepartureTime(LocalDateTime.of(2007, 1, 1, 8, 20).atZone(zoneId).toInstant());
        ghRequest.setArriveBy(true);

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

        PtRouter graphHopper = graphHopperFactory.createWith(feedMessageBuilder.build());
        GHResponse response = graphHopper.route(ghRequest);
        assertEquals(2, response.getAll().size());

        assertEquals(time(1, 6), response.getBest().getTime(), 0.1, "The 7:44 bus will not call at STAGECOACH, so I will be 30 min late at the airport.");

        ResponsePath impossibleAlternative = response.getAll().get(1);
        assertTrue(impossibleAlternative.isImpossible());
        Trip.Stop delayedStop = ((Trip.PtLeg) impossibleAlternative.getLegs().get(0)).stops.get(2);
        assertEquals(300, Duration.between(delayedStop.plannedArrivalTime.toInstant(), delayedStop.predictedArrivalTime.toInstant()).getSeconds(), "Five minutes late");

        // But when I ask about tomorrow, it works as planned
        ghRequest.setEarliestDepartureTime(LocalDateTime.of(2007, 1, 2, 8, 20).atZone(zoneId).toInstant());
        response = graphHopper.route(ghRequest);
        assertEquals(1, response.getAll().size());

        Trip.Stop notDelayedStop = ((Trip.PtLeg) response.getBest().getLegs().get(0)).stops.get(2);
        assertNull(notDelayedStop.predictedArrivalTime, "Not late");

    }

    @Test
    public void testDelayAtEndForNonFrequencyBasedTrip() {
        final double FROM_LAT = 36.915682, FROM_LON = -116.751677; // STAGECOACH stop
        final double TO_LAT = 36.88108, TO_LON = -116.81797; // BULLFROG stop
        Request ghRequest = new Request(
                FROM_LAT, FROM_LON,
                TO_LAT, TO_LON
        );
        ghRequest.setEarliestDepartureTime(LocalDateTime.of(2007, 1, 1, 0, 0).atZone(zoneId).toInstant());

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

        PtRouter graphHopper = graphHopperFactory.createWith(feedMessageBuilder.build());
        GHResponse route = graphHopper.route(ghRequest);

        assertFalse(route.hasErrors());
        assertFalse(route.getAll().isEmpty());
        assertEquals(time(8, 15), route.getBest().getTime(), 0.1, "Travel time == predicted travel time");
        assertEquals("STBA", (((Trip.PtLeg) route.getBest().getLegs().get(0)).trip_id), "Using expected route");
        assertEquals("AB1", (((Trip.PtLeg) route.getBest().getLegs().get(1)).trip_id), "Using expected route");
        assertEquals(LocalTime.parse("08:15"), LocalTime.from(((Trip.PtLeg) route.getBest().getLegs().get(1)).stops.get(1).predictedArrivalTime.toInstant().atZone(zoneId)), "Delay at destination");
        assertEquals(250, route.getBest().getFare().multiply(BigDecimal.valueOf(100)).intValue(), "Paid expected fare"); // Two legs, no transfers allowed. Need two 'p' tickets costing 125 cents each.
    }


    public GtfsRealtime.FeedHeader.Builder header() {
        return GtfsRealtime.FeedHeader.newBuilder()
                .setGtfsRealtimeVersion("1")
                .setTimestamp(LocalDateTime.of(2007, 1, 1, 0, 0).atZone(zoneId).toInstant().toEpochMilli() / 1000);
    }


}
