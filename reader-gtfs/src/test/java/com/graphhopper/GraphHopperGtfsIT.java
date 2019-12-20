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

import com.graphhopper.reader.gtfs.GraphHopperGtfs;
import com.graphhopper.reader.gtfs.PtRouteResource;
import com.graphhopper.reader.gtfs.Request;
import com.graphhopper.util.CmdArgs;
import com.graphhopper.util.Helper;
import com.graphhopper.util.Instruction;
import com.graphhopper.util.TranslationMap;
import org.junit.AfterClass;
import org.junit.Assume;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.File;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.graphhopper.reader.gtfs.GtfsHelper.time;
import static org.junit.Assert.*;

public class GraphHopperGtfsIT {

    private static final String GRAPH_LOC = "target/GraphHopperGtfsIT";
    private static PtRouteResource ptRouteResource;
    private static final ZoneId zoneId = ZoneId.of("America/Los_Angeles");
    private static GraphHopperGtfs graphHopperGtfs;

    @BeforeClass
    public static void init() {
        CmdArgs cmdArgs = new CmdArgs();
        cmdArgs.put("graph.flag_encoders", "car,foot");
        cmdArgs.put("graph.location", GRAPH_LOC);
        cmdArgs.put("gtfs.file", "files/sample-feed.zip");
        Helper.removeDir(new File(GRAPH_LOC));
        graphHopperGtfs = new GraphHopperGtfs(cmdArgs);
        graphHopperGtfs.init(cmdArgs);
        graphHopperGtfs.importOrLoad();
        ptRouteResource = PtRouteResource.createFactory(new TranslationMap().doImport(), graphHopperGtfs, graphHopperGtfs.getLocationIndex(), graphHopperGtfs.getGtfsStorage())
                .createWithoutRealtimeFeed();
    }

    @AfterClass
    public static void close() {
        graphHopperGtfs.close();
    }

    @Test
    public void testRoute1() {
        final double FROM_LAT = 36.914893, FROM_LON = -116.76821; // NADAV stop
        final double TO_LAT = 36.914944, TO_LON = -116.761472; // NANAA stop
        Request ghRequest = new Request(
                FROM_LAT, FROM_LON,
                TO_LAT, TO_LON
        );
        ghRequest.setEarliestDepartureTime(LocalDateTime.of(2007, 1, 1, 0, 0, 0).atZone(zoneId).toInstant());
        ghRequest.setIgnoreTransfers(true);
        GHResponse route = ptRouteResource.route(ghRequest);

        assertFalse(route.hasErrors());
        assertEquals(1, route.getAll().size());
        assertEquals("Expected travel time == scheduled arrival time", time(6, 49), route.getBest().getTime(), 0.1);
    }

    @Test
    public void testRoute1DoesNotGoAt654() {
        final double FROM_LAT = 36.914893, FROM_LON = -116.76821; // NADAV stop
        final double TO_LAT = 36.914944, TO_LON = -116.761472; // NANAA stop
        Request ghRequest = new Request(
                FROM_LAT, FROM_LON,
                TO_LAT, TO_LON
        );
        ghRequest.setEarliestDepartureTime(LocalDateTime.of(2007, 1, 1, 6, 54).atZone(zoneId).toInstant());
        GHResponse route = ptRouteResource.route(ghRequest);

        assertFalse(route.hasErrors());
        assertEquals(1, route.getAll().size());
        assertEquals("Expected travel time == scheduled arrival time", time(0, 25), route.getBest().getTime(), 0.1);
    }

    @Test
    public void testRoute1GoesAt744() {
        final double FROM_LAT = 36.914893, FROM_LON = -116.76821; // NADAV stop
        final double TO_LAT = 36.914944, TO_LON = -116.761472; // NANAA stop
        Request ghRequest = new Request(
                FROM_LAT, FROM_LON,
                TO_LAT, TO_LON
        );
        ghRequest.setEarliestDepartureTime(LocalDateTime.of(2007, 1, 1, 7, 44).atZone(zoneId).toInstant());
        ghRequest.setIgnoreTransfers(true);
        ghRequest.setBlockedRouteTypes(1); // Blocking trams shouldn't matter, this is a bus.

        GHResponse response = ptRouteResource.route(ghRequest);

        assertEquals(1, response.getAll().size());
        assertEquals("Expected travel time == scheduled arrival time", time(0, 5), response.getBest().getTime(), 0.1);
    }

    @Test
    public void testNoSolutionIfIDontLikeBusses() {
        final double FROM_LAT = 36.914893, FROM_LON = -116.76821; // NADAV stop
        final double TO_LAT = 36.914944, TO_LON = -116.761472; // NANAA stop
        Request ghRequest = new Request(
                FROM_LAT, FROM_LON,
                TO_LAT, TO_LON
        );
        ghRequest.setEarliestDepartureTime(LocalDateTime.of(2007, 1, 1, 7, 44).atZone(zoneId).toInstant());
        ghRequest.setBlockedRouteTypes(8);

        GHResponse response = ptRouteResource.route(ghRequest);

        assertTrue("When I block busses, there is no solution", response.getAll().isEmpty());
    }

    @Test
    public void testRoute1ArriveBy() {
        final double FROM_LAT = 36.914893, FROM_LON = -116.76821; // NADAV stop
        final double TO_LAT = 36.914944, TO_LON = -116.761472; // NANAA stop
        Request ghRequest = new Request(
                FROM_LAT, FROM_LON,
                TO_LAT, TO_LON
        );
        ghRequest.setEarliestDepartureTime(LocalDateTime.of(2007, 1, 1, 6, 49).atZone(zoneId).toInstant());
        ghRequest.setArriveBy(true);

        GHResponse route = ptRouteResource.route(ghRequest);

        assertFalse(route.hasErrors());
        assertEquals(1, route.getAll().size());
        assertEquals("Expected travel time == scheduled travel time", time(0, 5), route.getBest().getTime(), 0.1);

    }

    @Test
    public void testRoute1ArriveBy2() {
        final double FROM_LAT = 36.914893, FROM_LON = -116.76821; // NADAV stop
        final double TO_LAT = 36.914944, TO_LON = -116.761472; // NANAA stop
        Request ghRequest = new Request(
                FROM_LAT, FROM_LON,
                TO_LAT, TO_LON
        );
        // Tests that it also works when the query arrival time is not exactly the scheduled arrival time of the solution
        ghRequest.setEarliestDepartureTime(LocalDateTime.of(2007, 1, 1, 6, 50).atZone(zoneId).toInstant());
        ghRequest.setArriveBy(true);

        GHResponse route = ptRouteResource.route(ghRequest);

        assertFalse(route.hasErrors());
        assertEquals(1, route.getAll().size());
        assertEquals("Expected travel time == scheduled travel time", time(0, 6), route.getBest().getTime(), 0.1);

    }


    @Test
    public void testRoute1ProfileEarliestArrival() {
        final double FROM_LAT = 36.914893, FROM_LON = -116.76821; // NADAV stop
        final double TO_LAT = 36.914944, TO_LON = -116.761472; // NANAA stop
        Request ghRequest = new Request(
                FROM_LAT, FROM_LON,
                TO_LAT, TO_LON
        );
        ghRequest.setEarliestDepartureTime(LocalDateTime.of(2007, 1, 1, 6, 0).atZone(zoneId).toInstant());
        ghRequest.setProfileQuery(true);
        ghRequest.setIgnoreTransfers(true);
        ghRequest.setLimitSolutions(Integer.MAX_VALUE);

        GHResponse response = ptRouteResource.route(ghRequest);
        List<LocalTime> actualDepartureTimes = response.getAll().stream()
                .map(path -> LocalTime.from(path.getLegs().get(0).getDepartureTime().toInstant().atZone(zoneId)))
                .collect(Collectors.toList());
        // If profile time window is 4 hours (default), then we should get these answers, not more and not less:
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
        final double FROM_LAT = 36.914893, FROM_LON = -116.76821; // NADAV stop
        final double TO_LAT = 36.914944, TO_LON = -116.761472; // NANAA stop
        Request ghRequest = new Request(
                FROM_LAT, FROM_LON,
                TO_LAT, TO_LON
        );
        ghRequest.setEarliestDepartureTime(LocalDateTime.of(2007, 1, 1, 23, 0).atZone(zoneId).toInstant());
        ghRequest.setProfileQuery(true);
        ghRequest.setIgnoreTransfers(true);

        GHResponse response = ptRouteResource.route(ghRequest);
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
        final double FROM_LAT = 36.914893, FROM_LON = -116.76821; // NADAV stop
        final double TO_LAT = 36.914944, TO_LON = -116.761472; // NANAA stop
        Request ghRequest = new Request(
                FROM_LAT, FROM_LON,
                TO_LAT, TO_LON
        );
        ghRequest.setEarliestDepartureTime(LocalDateTime.of(2007, 1, 1, 13, 0).atZone(zoneId).toInstant());
        ghRequest.setArriveBy(true);
        ghRequest.setProfileQuery(true);
        ghRequest.setIgnoreTransfers(true);
        ghRequest.setLimitSolutions(4);

        GHResponse response = ptRouteResource.route(ghRequest);
        List<LocalTime> actualDepartureTimes = response.getAll().stream()
                .map(path -> LocalTime.from(((Trip.PtLeg) path.getLegs().get(0)).getDepartureTime().toInstant().atZone(zoneId)))
                .collect(Collectors.toList());
        List<LocalTime> expectedDepartureTimes = Stream.of(
                "12:44", "12:14", "11:44", "11:14")
                .map(LocalTime::parse)
                .collect(Collectors.toList());
        assertEquals(expectedDepartureTimes, actualDepartureTimes);
    }

    @Test
    public void testRoute2() {
        final double FROM_LAT = 36.914894, FROM_LON = -116.76821; // NADAV stop
        final double TO_LAT = 36.909489, TO_LON = -116.768242; // DADAN stop
        assertTravelTimeIs(ptRouteResource, FROM_LAT, FROM_LON, TO_LAT, TO_LON, time(6, 19));
    }

    @Test
    public void testRoute3() {
        final double FROM_LAT = 36.915682, FROM_LON = -116.751677; // STAGECOACH stop
        final double TO_LAT = 36.914944, TO_LON = -116.761472; // NANAA stop
        assertTravelTimeIs(ptRouteResource, FROM_LAT, FROM_LON, TO_LAT, TO_LON, time(6, 5));
    }

    @Test
    public void testRoute4() {
        final double FROM_LAT = 36.915682, FROM_LON = -116.751677; // STAGECOACH stop
        final double TO_LAT = 36.914894, TO_LON = -116.76821; // NADAV stop
        assertTravelTimeIs(ptRouteResource, FROM_LAT, FROM_LON, TO_LAT, TO_LON, time(6, 12));
    }

    @Test
    public void testRoute5() {
        final double FROM_LAT = 36.915682, FROM_LON = -116.751677; // STAGECOACH stop
        final double TO_LAT = 36.88108, TO_LON = -116.81797; // BULLFROG stop
        Request ghRequest = new Request(
                FROM_LAT, FROM_LON,
                TO_LAT, TO_LON
        );
        ghRequest.setEarliestDepartureTime(LocalDateTime.of(2007, 1, 1, 0, 0).atZone(zoneId).toInstant());
        GHResponse route = ptRouteResource.route(ghRequest);

        assertFalse(route.toString(), route.hasErrors());
        assertFalse(route.getAll().isEmpty());
        assertEquals("Expected travel time == scheduled travel time", time(8, 10), route.getBest().getTime(), 0.1);
        assertEquals("Using expected route", "STBA", (((Trip.PtLeg) route.getBest().getLegs().get(0)).trip_id));
        assertEquals("Using expected route", "AB1", (((Trip.PtLeg) route.getBest().getLegs().get(1)).trip_id));
        assertEquals("Paid expected fare", 250, route.getBest().getFare().multiply(BigDecimal.valueOf(100)).intValue()); // Two legs, no transfers allowed. Need two 'p' tickets costing 125 cents each.
    }

    @Test
    public void testRoute5Arrival() {
        final double FROM_LAT = 36.915682, FROM_LON = -116.751677; // STAGECOACH stop
        final double TO_LAT = 36.88108, TO_LON = -116.81797; // BULLFROG stop
        Request ghRequest = new Request(
                FROM_LAT, FROM_LON,
                TO_LAT, TO_LON
        );
        ghRequest.setEarliestDepartureTime(LocalDateTime.of(2007, 1, 1, 8, 10).atZone(zoneId).toInstant());
        ghRequest.setArriveBy(true);
        GHResponse route = ptRouteResource.route(ghRequest);

        assertFalse(route.hasErrors());
        assertFalse(route.getAll().isEmpty());
        assertEquals("Using expected route", "STBA", (((Trip.PtLeg) route.getBest().getLegs().get(0)).trip_id));
        assertEquals("Using expected route", "AB1", (((Trip.PtLeg) route.getBest().getLegs().get(1)).trip_id));
        assertEquals("Paid expected fare", 250, route.getBest().getFare().multiply(BigDecimal.valueOf(100)).intValue()); // Two legs, no transfers allowed. Need two 'p' tickets costing 125 cents each.
    }

    @Test
    public void testRoute6() {
        final double FROM_LAT = 36.7, FROM_LON = -116.5; // HASNOROUTES stop
        final double TO_LAT = 36.914894, TO_LON = -116.76821; // NADAV stop
        assertNoRoute(ptRouteResource, FROM_LAT, FROM_LON, TO_LAT, TO_LON);
    }

    @Test
    public void testRouteWithLaterDepartureTime() {
        final double FROM_LAT = 36.915682, FROM_LON = -116.751677; // STAGECOACH stop
        final double TO_LAT = 36.914894, TO_LON = -116.76821; // NADAV stop
        // Missed the bus at 10 by one minute, will have to use the 10:30 one.
        Request ghRequest = new Request(
                FROM_LAT, FROM_LON,
                TO_LAT, TO_LON
        );
        ghRequest.setEarliestDepartureTime(LocalDateTime.of(2007, 1, 1, 10, 1).atZone(zoneId).toInstant());
        GHResponse route = ptRouteResource.route(ghRequest);

        assertFalse(route.hasErrors());
        assertFalse(route.getAll().isEmpty());
        assertEquals("Expected travel time == scheduled travel time", time(0, 41), route.getBest().getTime(), 0.1);
    }

    @Test
    public void testWeekendRouteWorksOnlyOnWeekend() {
        final double FROM_LAT = 36.868446, FROM_LON = -116.784582; // BEATTY_AIRPORT stop
        final double TO_LAT = 36.641496, TO_LON = -116.40094; // AMV stop
        Request ghRequest = new Request(
                FROM_LAT, FROM_LON,
                TO_LAT, TO_LON
        );
        ghRequest.setEarliestDepartureTime(LocalDateTime.of(2007, 1, 1, 0, 0).atZone(zoneId).toInstant()); // Monday morning


        GHResponse route = ptRouteResource.route(ghRequest);
        assertFalse(route.getAll().isEmpty());
        // On Mondays, there is only a complicated evening trip.
        assertEquals("Expected travel time == scheduled travel time", time(22, 0), route.getBest().getTime());

        ghRequest = new Request(
                FROM_LAT, FROM_LON,
                TO_LAT, TO_LON
        );
        ghRequest.setEarliestDepartureTime(LocalDateTime.of(2007, 1, 6, 0, 0).atZone(zoneId).toInstant());
        route = ptRouteResource.route(ghRequest);
        assertFalse(route.getAll().isEmpty());
        assertEquals("Expected travel time == scheduled travel time", time(9, 0), route.getBest().getTime());
        assertEquals("Using expected trip", "AAMV1", (((Trip.PtLeg) route.getBest().getLegs().get(0)).trip_id));
        assertEquals("Paid expected fare", 525, route.getBest().getFare().multiply(BigDecimal.valueOf(100)).intValue());

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
        GHResponse response = ptRouteResource.route(ghRequest);
        assertEquals("Only find one solution. If blocks wouldn't work, there would be two. (There is a slower alternative without transfer.)", 1, response.getAll().size());
        assertEquals("Expected travel time == scheduled travel time", time(1, 20), response.getBest().getTime());
        assertEquals("Two legs: pt, pt, but the two pt legs are in one vehicle, so...", 2, response.getBest().getLegs().size());
        assertEquals("...one boarding instruction", 1, response.getBest().getInstructions().stream().filter(i -> i.getSign() == Instruction.PT_START_TRIP).count());
        assertEquals("...and one alighting instruction", 1, response.getBest().getInstructions().stream().filter(i -> i.getSign() == Instruction.PT_END_TRIP).count());
    }

    @Test
    public void testBlockWithComplicatedValidityIntersections() {
        final double FROM_LAT = 36.868446, FROM_LON = -116.784582; // BEATTY_AIRPORT stop
        final double TO_LAT = 36.641496, TO_LON = -116.40094; // AMV stop
        Request ghRequest = new Request(
                FROM_LAT, FROM_LON,
                TO_LAT, TO_LON
        );
        ghRequest.setEarliestDepartureTime(LocalDateTime.of(2007, 1, 1, 18, 0).atZone(zoneId).toInstant());
        GHResponse response = ptRouteResource.route(ghRequest);
        PathWrapper mondayTrip = response.getBest();
        assertEquals("Monday trip has no transfers", 0, mondayTrip.getNumChanges());
        assertEquals("Monday trip has 3 legs", 3, mondayTrip.getLegs().size());
        assertEquals("FUNNY_BLOCK_AB1", (((Trip.PtLeg) mondayTrip.getLegs().get(0)).trip_id));
        assertEquals("FUNNY_BLOCK_BFC1", (((Trip.PtLeg) mondayTrip.getLegs().get(1)).trip_id));
        assertEquals("FUNNY_BLOCK_FCAMV1", (((Trip.PtLeg) mondayTrip.getLegs().get(2)).trip_id));

        ghRequest.setEarliestDepartureTime(LocalDateTime.of(2007, 1, 7, 18, 0).atZone(zoneId).toInstant());
        response = ptRouteResource.route(ghRequest);
        PathWrapper sundayTrip = response.getBest();
        assertEquals("Sunday trip has no transfers", 0, sundayTrip.getNumChanges());
        assertEquals("Sunday trip has 2 legs", 2, sundayTrip.getLegs().size());
        assertEquals("FUNNY_BLOCK_AB1", (((Trip.PtLeg) sundayTrip.getLegs().get(0)).trip_id));
        // On Sundays, the second trip of the block does not run. Here, it's okay if in the response
        // it looks like we are teleporting -- this case is unlikely, but only revenue trips should be
        // included in the response. The test case still demonstrates that these mechanics are working
        // correctly, so I'm not sure we need a more realistic one. The more realistic case would
        // have a _different_ revenue trip here in the middle instead of _none_.
        assertEquals("FUNNY_BLOCK_FCAMV1", (((Trip.PtLeg) sundayTrip.getLegs().get(1)).trip_id));
    }

    @Test
    public void testTransferRules() {
        final double FROM_LAT = 36.915682, FROM_LON = -116.751677; // STAGECOACH stop
        final double TO1_LAT = 36.641496, TO1_LON = -116.40094; // AMV stop
        final double TO2_LAT = 36.88108, TO2_LON = -116.81797; // BULLFROG stop

        Request request = new Request(
                FROM_LAT, FROM_LON,
                TO1_LAT, TO1_LON
        );
        request.setEarliestDepartureTime(LocalDateTime.of(2007, 1, 6, 7, 30).atZone(zoneId).toInstant());

        GHResponse response = ptRouteResource.route(request);
        assertEquals("Transfer rule: 11 minutes. Will miss connection, and be there at 14.", time(6, 30), response.getBest().getTime());

        request = new Request(
                FROM_LAT, FROM_LON,
                TO2_LAT, TO2_LON
        );
        request.setEarliestDepartureTime(LocalDateTime.of(2007, 1, 6, 7, 30).atZone(zoneId).toInstant());

        response = ptRouteResource.route(request);
        assertEquals("Will still be there at 8:10 because there is a route-specific exception for this route.", time(0, 40), response.getBest().getTime());

        request = new Request(
                TO2_LAT, TO2_LON,
                FROM_LAT, FROM_LON
        );
        request.setEarliestDepartureTime(LocalDateTime.of(2007, 1, 6, 12, 5).atZone(zoneId).toInstant());

        response = ptRouteResource.route(request);
        assertEquals("Will take 1:15 because of a 'from route' exception with a longer transfer time.", time(1, 15), response.getBest().getTime());
    }


    private void assertTravelTimeIs(PtRouteResource graphHopper, double FROM_LAT, double FROM_LON, double TO_LAT, double TO_LON, int expectedWeight) {
        Request ghRequest = new Request(
                FROM_LAT, FROM_LON,
                TO_LAT, TO_LON
        );
        ghRequest.setEarliestDepartureTime(LocalDateTime.of(2007, 1, 1, 0, 0).atZone(zoneId).toInstant());
        GHResponse route = graphHopper.route(ghRequest);

        assertFalse(route.hasErrors());
        assertFalse(route.getAll().isEmpty());
        assertEquals("Expected travel time == scheduled travel time", expectedWeight, route.getBest().getTime(), 0.1);
    }

    private void assertNoRoute(PtRouteResource graphHopper, double from_lat, double from_lon, double to_lat, double to_lon) {
        Request ghRequest = new Request(
                from_lat, from_lon,
                to_lat, to_lon
        );
        ghRequest.setEarliestDepartureTime(LocalDateTime.of(2007, 1, 1, 0, 0).atZone(zoneId).toInstant());

        GHResponse route = graphHopper.route(ghRequest);
        assertTrue(route.getAll().isEmpty());
    }

    @Test
    public void testTransferByArrival() {
        final double FROM_LAT = 36.914893, FROM_LON = -116.76821; // NADAV stop
        final double TO_LAT = 36.868446, TO_LON = -116.784582; // BEATTY_AIRPORT stop
        Request ghRequest = new Request(
                FROM_LAT, FROM_LON,
                TO_LAT, TO_LON
        );

        // I want to be there at 7:20
        ghRequest.setEarliestDepartureTime(LocalDateTime.of(2007, 1, 1, 7, 20).atZone(zoneId).toInstant());
        ghRequest.setArriveBy(true);

        GHResponse response = ptRouteResource.route(ghRequest);

        Trip.PtLeg lastLeg = ((Trip.PtLeg) response.getBest().getLegs().get(response.getBest().getLegs().size() - 1));
        Trip.Stop lastStop = lastLeg.stops.get(lastLeg.stops.size() - 1);
        assertEquals("Arrive at 7:20", LocalDateTime.parse("2007-01-01T07:20:00").atZone(zoneId).toInstant(), lastStop.plannedArrivalTime.toInstant());
    }

    @Test
    public void testCustomObjectiveFunction() {
        Request ghRequest = new Request(
                36.868446, -116.784582,  // BEATTY_AIRPORT stop
                36.425288, -117.133162       // FUR_CREEK_RES stop
        );
        ghRequest.setEarliestDepartureTime(LocalDateTime.of(2007, 1, 1, 14, 0, 0).atZone(zoneId).toInstant());

        GHResponse response = ptRouteResource.route(ghRequest);

        PathWrapper solutionWithTransfer = response.getAll().get(0);
        PathWrapper solutionWithoutTransfer = response.getAll().get(1);

        Assume.assumeTrue("First solution has one transfer", solutionWithTransfer.getNumChanges() == 1);
        Assume.assumeTrue("Second solution has no transfers", solutionWithoutTransfer.getNumChanges() == 0);
        Assume.assumeTrue("With transfers is faster than without", solutionWithTransfer.getTime() < solutionWithoutTransfer.getTime());

        // If one transfer is worth beta_transfers milliseconds of travel time savings
        // to me, I will be indifferent when choosing between the two routes.
        // Wiggle it by epsilon, and I should prefer one over the other.
        double betaTransfers = solutionWithoutTransfer.getTime() - solutionWithTransfer.getTime();

        ghRequest.setIgnoreTransfers(true);
        // Well, not actually ignore them, but don't do multi-criteria search

        ghRequest.setBetaTransfers(betaTransfers - 10);
        response = ptRouteResource.route(ghRequest);

        assertEquals("Get exactly one solution", 1, response.getAll().size());
        assertEquals("Prefer solution with transfers when I give the smaller beta", solutionWithTransfer.getTime(), response.getBest().getTime());

        ghRequest.setBetaTransfers(betaTransfers + 10);

        response = ptRouteResource.route(ghRequest);

        assertEquals("Get exactly one solution", 1, response.getAll().size());
        assertEquals("Prefer solution without transfers when I give the higher beta", solutionWithoutTransfer.getTime(), response.getBest().getTime());
    }

}
