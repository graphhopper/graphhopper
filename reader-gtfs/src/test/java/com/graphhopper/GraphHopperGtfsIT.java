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

import com.conveyal.gtfs.model.Stop;
import com.graphhopper.config.Profile;
import com.graphhopper.gtfs.*;
import com.graphhopper.util.Helper;
import com.graphhopper.util.Instruction;
import com.graphhopper.util.TranslationMap;
import org.assertj.core.util.Maps;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.graphhopper.gtfs.GtfsHelper.time;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

public class GraphHopperGtfsIT {

    private static final String GRAPH_LOC = "target/GraphHopperGtfsIT";
    private static PtRouter ptRouter;
    private static final ZoneId zoneId = ZoneId.of("America/Los_Angeles");
    private static GraphHopperGtfs graphHopperGtfs;

    @BeforeAll
    public static void init() {
        GraphHopperConfig ghConfig = new GraphHopperConfig();
        ghConfig.putObject("graph.location", GRAPH_LOC);
        ghConfig.putObject("gtfs.file", "files/sample-feed");
        ghConfig.setProfiles(Arrays.asList(
                new Profile("foot").setVehicle("foot").setWeighting("fastest"),
                new Profile("car").setVehicle("car").setWeighting("fastest")));
        Helper.removeDir(new File(GRAPH_LOC));
        graphHopperGtfs = new GraphHopperGtfs(ghConfig);
        graphHopperGtfs.init(ghConfig);
        graphHopperGtfs.importOrLoad();
        ptRouter = new PtRouterImpl.Factory(ghConfig, new TranslationMap().doImport(), graphHopperGtfs.getBaseGraph(), graphHopperGtfs.getEncodingManager(), graphHopperGtfs.getLocationIndex(), graphHopperGtfs.getGtfsStorage())
                .createWithoutRealtimeFeed();
    }

    @AfterAll
    public static void close() {
        graphHopperGtfs.close();
    }

    @Test
    public void testRoute1() {
        Request ghRequest = new Request(Arrays.asList(
                new GHStationLocation("NADAV"),
                new GHStationLocation("NANAA")),
                LocalDateTime.of(2007, 1, 1, 0, 0, 0).atZone(zoneId).toInstant());
        ghRequest.setIgnoreTransfers(true);
        GHResponse route = ptRouter.route(ghRequest);
        assertFalse(route.hasErrors());
        assertEquals(1, route.getAll().size());
        assertEquals(time(6, 49), route.getBest().getTime(), "Expected travel time == scheduled arrival time");
    }

    @Test
    public void testRoute1DoesNotGoAt654() {
        Request ghRequest = new Request(Arrays.asList(
                new GHStationLocation("NADAV"),
                new GHStationLocation("NANAA")),
                LocalDateTime.of(2007, 1, 1, 6, 54).atZone(zoneId).toInstant());
        GHResponse route = ptRouter.route(ghRequest);
        assertFalse(route.hasErrors());
        assertEquals(1, route.getAll().size());
        assertEquals(time(0, 25), route.getBest().getTime(), "Expected travel time == scheduled arrival time");
    }

    @Test
    public void testRoute1GoesAt744() {
        Request ghRequest = new Request(Arrays.asList(
                new GHStationLocation("NADAV"),
                new GHStationLocation("NANAA")),
                LocalDateTime.of(2007, 1, 1, 7, 44).atZone(zoneId).toInstant());
        ghRequest.setIgnoreTransfers(true);
        ghRequest.setBlockedRouteTypes(1); // Blocking trams shouldn't matter, this is a bus.
        GHResponse response = ptRouter.route(ghRequest);
        assertEquals(1, response.getAll().size());
        assertEquals(time(0, 5), response.getBest().getTime(), "Expected travel time == scheduled arrival time");
    }

    @Test
    public void testNoSolutionIfIDontLikeBusses() {
        Request ghRequest = new Request(Arrays.asList(
                new GHStationLocation("NADAV"),
                new GHStationLocation("NANAA")),
                LocalDateTime.of(2007, 1, 1, 7, 44).atZone(zoneId).toInstant());
        ghRequest.setBlockedRouteTypes(8);
        GHResponse response = ptRouter.route(ghRequest);
        assertTrue(response.getAll().isEmpty(), "When I block busses, there is no solution");
    }

    @Test
    public void testRoute1ArriveBy() {
        Request ghRequest = new Request(Arrays.asList(
                new GHStationLocation("NADAV"),
                new GHStationLocation("NANAA")),
                LocalDateTime.of(2007, 1, 1, 6, 49).atZone(zoneId).toInstant());
        ghRequest.setArriveBy(true);
        GHResponse route = ptRouter.route(ghRequest);
        assertFalse(route.hasErrors());
        assertEquals(1, route.getAll().size());
        assertEquals(time(0, 5), route.getBest().getTime(), "Expected travel time == scheduled travel time");

    }

    @Test
    public void testRoute1ArriveBy2() {
        Request ghRequest = new Request(Arrays.asList(
                new GHStationLocation("NADAV"),
                new GHStationLocation("NANAA")),
                LocalDateTime.of(2007, 1, 1, 6, 50).atZone(zoneId).toInstant());
        // Tests that it also works when the query arrival time is not exactly the scheduled arrival time of the solution
        ghRequest.setArriveBy(true);
        GHResponse route = ptRouter.route(ghRequest);
        assertFalse(route.hasErrors());
        assertEquals(1, route.getAll().size());
        assertEquals(time(0, 6), route.getBest().getTime(), "Expected travel time == scheduled travel time");
    }

    @Test
    public void testRoute1ProfileEarliestArrival() {
        Request ghRequest = new Request(Arrays.asList(
                new GHStationLocation("NADAV"),
                new GHStationLocation("NANAA")),
                LocalDateTime.of(2007, 1, 1, 6, 0).atZone(zoneId).toInstant());
        ghRequest.setProfileQuery(true);
        ghRequest.setIgnoreTransfers(true);
        ghRequest.setLimitSolutions(Integer.MAX_VALUE);
        ghRequest.setMaxProfileDuration(Duration.ofHours(4));

        GHResponse response = ptRouter.route(ghRequest);
        List<LocalTime> actualDepartureTimes = response.getAll().stream()
                .map(path -> LocalTime.from(path.getLegs().get(0).getDepartureTime().toInstant().atZone(zoneId)))
                .collect(Collectors.toList());
        // If profile time window is 4 hours, then we should get these answers, not more and not less:
        // At 10:00 (end of profile time window), the departure at 10:04 is optimal.
        // This is hairy, it's easy to confuse this and 09:54 becomes the last option.
        List<LocalTime> expectedDepartureTimes = Stream.of(
                "06:44", "07:14", "07:44", "08:14", "08:44", "08:54", "09:04", "09:14", "09:24", "09:34", "09:44", "09:54", "10:04")
                .map(LocalTime::parse)
                .collect(Collectors.toList());
        assertEquals(expectedDepartureTimes, actualDepartureTimes);
    }

    @Test
    public void testRoute1ProfileOvernight() {
        Request ghRequest = new Request(Arrays.asList(
                new GHStationLocation("NADAV"),
                new GHStationLocation("NANAA")),
                LocalDateTime.of(2007, 1, 1, 23, 0).atZone(zoneId).toInstant());
        ghRequest.setProfileQuery(true);
        ghRequest.setMaxProfileDuration(Duration.ofHours(1));
        ghRequest.setIgnoreTransfers(true);

        GHResponse response = ptRouter.route(ghRequest);
        List<LocalTime> actualDepartureTimes = response.getAll().stream()
                .map(path -> LocalTime.from(path.getLegs().get(0).getDepartureTime().toInstant().atZone(zoneId)))
                .collect(Collectors.toList());
        // Find exactly the next departure, tomorrow. It departs outside the profile time window, but there is no
        // walk alternative, so this remains the best solution for the entire time window.
        List<LocalTime> expectedDepartureTimes = Stream.of(
                "06:44")
                .map(LocalTime::parse)
                .collect(Collectors.toList());
        assertEquals(expectedDepartureTimes, actualDepartureTimes);
    }

    @Test
    public void testRoute1ProfileLatestDeparture() {
        Request ghRequest = new Request(Arrays.asList(
                new GHStationLocation("NADAV"),
                new GHStationLocation("NANAA")),
                LocalDateTime.of(2007, 1, 1, 13, 0).atZone(zoneId).toInstant());
        ghRequest.setArriveBy(true);
        ghRequest.setProfileQuery(true);
        ghRequest.setIgnoreTransfers(true);
        ghRequest.setLimitSolutions(4);

        GHResponse response = ptRouter.route(ghRequest);
        List<LocalTime> actualDepartureTimes = response.getAll().stream()
                .map(path -> LocalTime.from(path.getLegs().get(0).getDepartureTime().toInstant().atZone(zoneId)))
                .collect(Collectors.toList());
        List<LocalTime> expectedDepartureTimes = Stream.of(
                "12:44", "12:14", "11:44", "11:14")
                .map(LocalTime::parse)
                .collect(Collectors.toList());
        assertEquals(expectedDepartureTimes, actualDepartureTimes);
    }

    @Test
    public void testRoute2() {
        assertTravelTimeIs(ptRouter, "NADAV", "DADAN", time(6, 19));
    }

    @Test
    public void testRoute3() {
        assertTravelTimeIs(ptRouter, "STAGECOACH", "NANAA", time(6, 5));
    }

    @Test
    public void testRoute4() {
        assertTravelTimeIs(ptRouter, "STAGECOACH", "NADAV", time(6, 12));
    }

    @Test
    public void testRoute5() {
        Request ghRequest = new Request(Arrays.asList(
                new GHStationLocation("STAGECOACH"),
                new GHStationLocation("BULLFROG")),
                LocalDateTime.of(2007, 1, 1, 0, 0).atZone(zoneId).toInstant());
        GHResponse route = ptRouter.route(ghRequest);

        assertFalse(route.hasErrors(), route.toString());
        assertFalse(route.getAll().isEmpty());
        assertEquals(time(8, 10), route.getBest().getTime(), "Expected travel time == scheduled travel time");
        assertEquals("STBA", (((Trip.PtLeg) route.getBest().getLegs().get(0)).trip_id), "Using expected route");
        assertEquals("AB1", (((Trip.PtLeg) route.getBest().getLegs().get(1)).trip_id), "Using expected route");
        assertEquals(250, route.getBest().getFare().multiply(BigDecimal.valueOf(100)).intValue(), "Paid expected fare"); // Two legs, no transfers allowed. Need two 'p' tickets costing 125 cents each.
    }

    @Test
    public void testRoute5Arrival() {
        Request ghRequest = new Request(Arrays.asList(
                new GHStationLocation("STAGECOACH"),
                new GHStationLocation("BULLFROG")),
                LocalDateTime.of(2007, 1, 1, 8, 10).atZone(zoneId).toInstant());
        ghRequest.setArriveBy(true);
        GHResponse route = ptRouter.route(ghRequest);

        assertFalse(route.hasErrors());
        assertFalse(route.getAll().isEmpty());
        assertEquals("STBA", (((Trip.PtLeg) route.getBest().getLegs().get(0)).trip_id), "Using expected route");
        assertEquals("AB1", (((Trip.PtLeg) route.getBest().getLegs().get(1)).trip_id), "Using expected route");
        assertEquals(250, route.getBest().getFare().multiply(BigDecimal.valueOf(100)).intValue(), "Paid expected fare"); // Two legs, no transfers allowed. Need two 'p' tickets costing 125 cents each.
    }

    @Test
    public void testRouteFromNowhere() {
        Request ghRequest = new Request(Arrays.asList(
                new GHStationLocation("HASNOROUTES"),
                new GHStationLocation("NADAV")),
                LocalDateTime.of(2007, 1, 1, 0, 0).atZone(zoneId).toInstant());
        GHResponse route = ptRouter.route(ghRequest);
        assertTrue(route.getAll().isEmpty());
    }

    @Test
    public void testRouteToNowhere() {
        Request ghRequest = new Request(Arrays.asList(
                new GHStationLocation("NADAV"),
                new GHStationLocation("HASNOROUTES")),
                LocalDateTime.of(2007, 1, 1, 6, 0).atZone(zoneId).toInstant());
        ghRequest.setIgnoreTransfers(true);
        GHResponse route = ptRouter.route(ghRequest);
        assertTrue(route.getAll().isEmpty());
    }

    @Test
    public void testRouteWithLaterDepartureTime() {
        Request ghRequest = new Request(Arrays.asList(
                new GHStationLocation("STAGECOACH"),
                new GHStationLocation("NADAV")),
                LocalDateTime.of(2007, 1, 1, 10, 1).atZone(zoneId).toInstant());
        GHResponse route = ptRouter.route(ghRequest);

        assertFalse(route.hasErrors());
        assertFalse(route.getAll().isEmpty());
        assertEquals(time(0, 41), route.getBest().getTime(), "Expected travel time == scheduled travel time");
    }

    @Test
    public void testWeekendRouteWorksOnlyOnWeekend() {
        Request ghRequest = new Request(Arrays.asList(
                new GHStationLocation("BEATTY_AIRPORT"),
                new GHStationLocation("AMV")),
                LocalDateTime.of(2007, 1, 1, 0, 0).atZone(zoneId).toInstant());

        GHResponse route = ptRouter.route(ghRequest);
        assertFalse(route.getAll().isEmpty());
        // On Mondays, there is only a complicated evening trip.
        assertEquals(time(22, 0), route.getBest().getTime(), "Expected travel time == scheduled travel time");

        ghRequest = new Request(Arrays.asList(
                new GHStationLocation("BEATTY_AIRPORT"),
                new GHStationLocation("AMV")),
                LocalDateTime.of(2007, 1, 6, 0, 0).atZone(zoneId).toInstant());
        route = ptRouter.route(ghRequest);
        assertFalse(route.getAll().isEmpty());
        assertEquals(time(9, 0), route.getBest().getTime(), "Expected travel time == scheduled travel time");
        assertEquals("AAMV1", (((Trip.PtLeg) route.getBest().getLegs().get(0)).trip_id), "Using expected trip");
        assertEquals(525, route.getBest().getFare().multiply(BigDecimal.valueOf(100)).intValue(), "Paid expected fare");
    }

    @Test
    public void testBlockTrips() {
        Request ghRequest = new Request(Arrays.asList(
                new GHStationLocation("BEATTY_AIRPORT"),
                new GHStationLocation("FUR_CREEK_RES")),
                LocalDateTime.of(2007, 1, 1, 8, 0).atZone(zoneId).toInstant());
        GHResponse response = ptRouter.route(ghRequest);
        assertEquals(1, response.getAll().size(), "Only find one solution. If blocks wouldn't work, there would be two. (There is a slower alternative without transfer.)");
        assertEquals(time(1, 20), response.getBest().getTime(), "Expected travel time == scheduled travel time");
        assertEquals(2, response.getBest().getLegs().size(), "Two legs: pt, pt, but the two pt legs are in one vehicle, so...");
        assertEquals(1, response.getBest().getInstructions().stream().filter(i -> i.getSign() == Instruction.PT_START_TRIP).count(), "Two legs: pt, pt, but the two pt legs are in one vehicle, so...");
        assertEquals(1, response.getBest().getInstructions().stream().filter(i -> i.getSign() == Instruction.PT_END_TRIP).count(), "...and one alighting instruction");
        assertThat(response.getHints().getInt("visited_nodes.sum", Integer.MAX_VALUE)).isLessThanOrEqualTo(200);
    }

    @Test
    public void testVeryShortProfileQuery() {
        Request ghRequest = new Request(Arrays.asList(
                new GHStationLocation("BEATTY_AIRPORT"),
                new GHStationLocation("FUR_CREEK_RES")),
                LocalDateTime.of(2007, 1, 1, 8, 0).atZone(zoneId).toInstant());
        ghRequest.setProfileQuery(true);
        ghRequest.setMaxProfileDuration(Duration.ofSeconds(1));
        GHResponse response = ptRouter.route(ghRequest);
        assertEquals(time(1, 20), response.getAll().get(0).getTime(), "Expected travel time == scheduled travel time");
        assertEquals(time(7, 20), response.getAll().get(1).getTime(), "Expected travel time == scheduled travel time");
        assertThat(response.getHints().getInt("visited_nodes.sum", Integer.MAX_VALUE)).isLessThanOrEqualTo(904);
    }

    @Test
    public void testBlockRouteTypes() {
        Request ghRequest = new Request(Arrays.asList(
                new GHStationLocation("AMV"),
                new GHStationLocation("FUR_CREEK_RES")),
                LocalDateTime.of(2007, 1, 7, 9, 0).atZone(zoneId).toInstant());
        GHResponse response = ptRouter.route(ghRequest);
        ResponsePath mondayTrip = response.getBest();
        assertEquals("AB", ((Trip.PtLeg) mondayTrip.getLegs().get(1)).route_id);

        ghRequest = new Request(Arrays.asList(
                new GHStationLocation("BEATTY_AIRPORT"),
                new GHStationLocation("FUR_CREEK_RES")),
                LocalDateTime.of(2007, 1, 7, 9, 0).atZone(zoneId).toInstant());
        ghRequest.setBlockedRouteTypes(4);
        response = ptRouter.route(ghRequest);
        mondayTrip = response.getBest();
        assertNotEquals("AB", ((Trip.PtLeg) mondayTrip.getLegs().get(0)).route_id);

        ghRequest = new Request(Arrays.asList(
                new GHStationLocation("AMV"),
                new GHStationLocation("FUR_CREEK_RES")),
                LocalDateTime.of(2007, 1, 7, 9, 0).atZone(zoneId).toInstant());
        ghRequest.setBlockedRouteTypes(4);
        response = ptRouter.route(ghRequest);
        mondayTrip = response.getBest();
        assertNotEquals("AB", ((Trip.PtLeg) mondayTrip.getLegs().get(1)).route_id);
    }

    @Test
    public void testPenalizeRouteTypes() {
        // Baseline
        Request ghRequest = new Request(Arrays.asList(
                new GHStationLocation("AMV"),
                new GHStationLocation("FUR_CREEK_RES")),
                LocalDateTime.of(2007, 1, 7, 9, 0).atZone(zoneId).toInstant());
        GHResponse response = ptRouter.route(ghRequest);
        ResponsePath mondayTrip = response.getBest();
        assertEquals("AB", ((Trip.PtLeg) mondayTrip.getLegs().get(1)).route_id);
        assertEquals(22800000.0, mondayTrip.getRouteWeight());

        // Boarding and transferring out of disliked route type, penalty is applied, but not high enough to divert
        ghRequest = new Request(Arrays.asList(
                new GHStationLocation("BEATTY_AIRPORT"),
                new GHStationLocation("FUR_CREEK_RES")),
                LocalDateTime.of(2007, 1, 7, 9, 0).atZone(zoneId).toInstant());
        ghRequest.setBoardingPenaltiesByRouteType(Maps.newHashMap(2, 100000L));
        response = ptRouter.route(ghRequest);
        mondayTrip = response.getBest();
        assertEquals("AB", ((Trip.PtLeg) mondayTrip.getLegs().get(0)).route_id);
        assertEquals(22900000.0, mondayTrip.getRouteWeight());

        // Baseline when getting off at BULLFROG (no transfer)
        ghRequest = new Request(Arrays.asList(
                new GHStationLocation("BEATTY_AIRPORT"),
                new GHStationLocation("BULLFROG")),
                LocalDateTime.of(2007, 1, 7, 9, 0).atZone(zoneId).toInstant());
        response = ptRouter.route(ghRequest);
        mondayTrip = response.getBest();
        assertEquals("AB", ((Trip.PtLeg) mondayTrip.getLegs().get(0)).route_id);
        assertEquals(18600000.0, mondayTrip.getRouteWeight());

        // Board and exit disliked route type directly. Penalty applied, not high enough to divert
        ghRequest = new Request(Arrays.asList(
                new GHStationLocation("BEATTY_AIRPORT"),
                new GHStationLocation("BULLFROG")),
                LocalDateTime.of(2007, 1, 7, 9, 0).atZone(zoneId).toInstant());
        ghRequest.setBoardingPenaltiesByRouteType(Maps.newHashMap(2, 100000L));
        response = ptRouter.route(ghRequest);
        mondayTrip = response.getBest();
        assertEquals("AB", ((Trip.PtLeg) mondayTrip.getLegs().get(0)).route_id);
        assertEquals(18700000.0, mondayTrip.getRouteWeight());

        // Would board disliked route type and then transfer, penalty is high, so we divert
        ghRequest = new Request(Arrays.asList(
                new GHStationLocation("BEATTY_AIRPORT"),
                new GHStationLocation("FUR_CREEK_RES")),
                LocalDateTime.of(2007, 1, 7, 9, 0).atZone(zoneId).toInstant());
        ghRequest.setBoardingPenaltiesByRouteType(Maps.newHashMap(2, 1000000L));
        response = ptRouter.route(ghRequest);
        mondayTrip = response.getBest();
        assertNotEquals("AB", ((Trip.PtLeg) mondayTrip.getLegs().get(0)).route_id);

        // Would transfer in and out, penalty is high, so we divert
        ghRequest = new Request(Arrays.asList(
                new GHStationLocation("AMV"),
                new GHStationLocation("FUR_CREEK_RES")),
                LocalDateTime.of(2007, 1, 7, 9, 0).atZone(zoneId).toInstant());
        ghRequest.setBoardingPenaltiesByRouteType(Maps.newHashMap(2, 1000000L));
        response = ptRouter.route(ghRequest);
        mondayTrip = response.getBest();
        assertNotEquals("AB", ((Trip.PtLeg) mondayTrip.getLegs().get(1)).route_id);

        // Transferring in and out, penalty is applied, but not high enough to divert
        ghRequest = new Request(Arrays.asList(
                new GHStationLocation("AMV"),
                new GHStationLocation("FUR_CREEK_RES")),
                LocalDateTime.of(2007, 1, 7, 9, 0).atZone(zoneId).toInstant());
        ghRequest.setBoardingPenaltiesByRouteType(Maps.newHashMap(2, 100000L));
        response = ptRouter.route(ghRequest);
        mondayTrip = response.getBest();
        assertEquals("AB", ((Trip.PtLeg) mondayTrip.getLegs().get(1)).route_id);
        assertEquals(22900000.0, mondayTrip.getRouteWeight());
    }

    @Test
    public void testBlockWithComplicatedValidityIntersections() {
        Request ghRequest = new Request(Arrays.asList(
                new GHStationLocation("BEATTY_AIRPORT"),
                new GHStationLocation("AMV")),
                LocalDateTime.of(2007, 1, 1, 18, 0).atZone(zoneId).toInstant());
        GHResponse response = ptRouter.route(ghRequest);
        ResponsePath mondayTrip = response.getBest();
        assertEquals(0, mondayTrip.getNumChanges(), "Monday trip has no transfers");
        assertEquals(3, mondayTrip.getLegs().size(), "Monday trip has 3 legs");
        assertEquals("FUNNY_BLOCK_AB1", (((Trip.PtLeg) mondayTrip.getLegs().get(0)).trip_id));
        assertEquals("FUNNY_BLOCK_BFC1", (((Trip.PtLeg) mondayTrip.getLegs().get(1)).trip_id));
        assertEquals("FUNNY_BLOCK_FCAMV1", (((Trip.PtLeg) mondayTrip.getLegs().get(2)).trip_id));
        assertTrue((((Trip.PtLeg) mondayTrip.getLegs().get(1)).isInSameVehicleAsPrevious));
        assertTrue((((Trip.PtLeg) mondayTrip.getLegs().get(2)).isInSameVehicleAsPrevious));

        ghRequest.setEarliestDepartureTime(LocalDateTime.of(2007, 1, 7, 18, 0).atZone(zoneId).toInstant());
        response = ptRouter.route(ghRequest);
        ResponsePath sundayTrip = response.getBest();
        assertEquals(0, sundayTrip.getNumChanges(), "Sunday trip has no transfers");
        assertEquals(2, sundayTrip.getLegs().size(), "Sunday trip has 2 legs");
        assertEquals("FUNNY_BLOCK_AB1", (((Trip.PtLeg) sundayTrip.getLegs().get(0)).trip_id));
        // On Sundays, the second trip of the block does not run. Here, it's okay if in the response
        // it looks like we are teleporting -- this case is unlikely, but only revenue trips should be
        // included in the response. The test case still demonstrates that these mechanics are working
        // correctly, so I'm not sure we need a more realistic one. The more realistic case would
        // have a _different_ revenue trip here in the middle instead of _none_.
        assertEquals("FUNNY_BLOCK_FCAMV1", (((Trip.PtLeg) sundayTrip.getLegs().get(1)).trip_id));
        assertTrue((((Trip.PtLeg) sundayTrip.getLegs().get(1)).isInSameVehicleAsPrevious));
    }

    @Test
    public void testTransferRules() {
        Request request = new Request(Arrays.asList(
                new GHStationLocation("STAGECOACH"),
                new GHStationLocation("AMV")),
                LocalDateTime.of(2007, 1, 6, 7, 30).atZone(zoneId).toInstant());

        GHResponse response = ptRouter.route(request);
        assertEquals(time(6, 30), response.getBest().getTime(), "Transfer rule: 11 minutes. Will miss connection, and be there at 14.");

        request = new Request(Arrays.asList(
                new GHStationLocation("STAGECOACH"),
                new GHStationLocation("BULLFROG")),
                LocalDateTime.of(2007, 1, 6, 7, 30).atZone(zoneId).toInstant());

        response = ptRouter.route(request);
        assertEquals(time(0, 40), response.getBest().getTime(), "Will still be there at 8:10 because there is a route-specific exception for this route.");

        request = new Request(Arrays.asList(
                new GHStationLocation("BULLFROG"),
                new GHStationLocation("STAGECOACH")),
                LocalDateTime.of(2007, 1, 6, 12, 5).atZone(zoneId).toInstant());
        request.setEarliestDepartureTime(LocalDateTime.of(2007, 1, 6, 12, 5).atZone(zoneId).toInstant());

        response = ptRouter.route(request);
        assertEquals(time(1, 15), response.getBest().getTime(), "Will take 1:15 because of a 'from route' exception with a longer transfer time.");
    }


    private void assertTravelTimeIs(PtRouter graphHopper, String from, String to, int expectedWeight) {
        Request ghRequest = new Request(Arrays.asList(
                new GHStationLocation(from),
                new GHStationLocation(to)),
                LocalDateTime.of(2007, 1, 1, 0, 0).atZone(zoneId).toInstant());
        GHResponse route = graphHopper.route(ghRequest);
        assertFalse(route.hasErrors());
        assertFalse(route.getAll().isEmpty());
        assertEquals(expectedWeight, route.getBest().getTime(), "Expected travel time == scheduled travel time");
    }

    @Test
    public void testTransferByArrival() {
        Request ghRequest = new Request(Arrays.asList(
                new GHStationLocation("NADAV"),
                new GHStationLocation("BEATTY_AIRPORT")),
                LocalDateTime.of(2007, 1, 1, 7, 20).atZone(zoneId).toInstant());

        // I want to be there at 7:20
        ghRequest.setArriveBy(true);

        GHResponse response = ptRouter.route(ghRequest);

        Trip.PtLeg lastLeg = ((Trip.PtLeg) response.getBest().getLegs().get(response.getBest().getLegs().size() - 1));
        Trip.Stop lastStop = lastLeg.stops.get(lastLeg.stops.size() - 1);
        assertEquals(LocalDateTime.parse("2007-01-01T07:20:00").atZone(zoneId).toInstant(), lastStop.plannedArrivalTime.toInstant(), "Arrive at 7:20");
    }

    @Test
    public void testCustomObjectiveFunction() {
        Request ghRequest = new Request(Arrays.asList(
                new GHStationLocation("BEATTY_AIRPORT"),
                new GHStationLocation("FUR_CREEK_RES")),
                LocalDateTime.of(2007, 1, 1, 14, 0, 0).atZone(zoneId).toInstant());

        GHResponse response = ptRouter.route(ghRequest);

        ResponsePath solutionWithTransfer = response.getAll().get(0);
        ResponsePath solutionWithoutTransfer = response.getAll().get(1);

        assumeTrue(solutionWithTransfer.getNumChanges() == 1, "First solution has one transfer");
        assumeTrue(solutionWithoutTransfer.getNumChanges() == 0, "Second solution has no transfers");
        assumeTrue(solutionWithTransfer.getTime() < solutionWithoutTransfer.getTime(), "With transfers is faster than without");

        // If one transfer is worth beta_transfers milliseconds of travel time savings
        // to me, I will be indifferent when choosing between the two routes.
        // Wiggle it by epsilon, and I should prefer one over the other.
        double betaTransfers = solutionWithoutTransfer.getTime() - solutionWithTransfer.getTime();

        ghRequest.setIgnoreTransfers(true);
        // Well, not actually ignore them, but don't do multi-criteria search

        ghRequest.setBetaTransfers(betaTransfers - 10);
        response = ptRouter.route(ghRequest);

        assertEquals(1, response.getAll().size(), "Get exactly one solution");
        assertEquals(solutionWithTransfer.getTime(), response.getBest().getTime(), "Prefer solution with transfers when I give the smaller beta");

        ghRequest.setBetaTransfers(betaTransfers + 10);

        response = ptRouter.route(ghRequest);

        assertEquals(1, response.getAll().size(), "Get exactly one solution");
        assertEquals(solutionWithoutTransfer.getTime(), response.getBest().getTime(), "Prefer solution without transfers when I give the higher beta");
    }

    @Test
    public void testBoardingArea() {
        Stop boardingArea = graphHopperGtfs.getGtfsStorage().getGtfsFeeds().values().iterator().next().stops.get("BOARDING_AREA");
        assertEquals(4, boardingArea.location_type, "Boarding area can be read (doesn't do anything though)");
    }

}
