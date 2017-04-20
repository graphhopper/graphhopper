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
import com.graphhopper.reader.gtfs.GtfsStorage;
import com.graphhopper.reader.gtfs.PtFlagEncoder;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.storage.GHDirectory;
import com.graphhopper.storage.GraphHopperStorage;
import com.graphhopper.storage.index.LocationIndex;
import com.graphhopper.util.Helper;
import com.graphhopper.util.Instruction;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.File;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.graphhopper.reader.gtfs.GtfsHelper.time;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

public class GraphHopperGtfsIT {

    private static final String GRAPH_LOC = "target/GraphHopperGtfsIT";
    private static GraphHopperGtfs graphHopper;
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
        locationIndex = GraphHopperGtfs.createOrLoadIndex(directory, graphHopperStorage);
        graphHopper = GraphHopperGtfs.createFactory(ptFlagEncoder, GraphHopperGtfs.createTranslationMap(), graphHopperStorage, locationIndex, gtfsStorage)
                .createWithoutRealtimeFeed();
    }

    @AfterClass
    public static void close() {
        graphHopperStorage.close();
        locationIndex.close();
    }

    @Test
    public void testRoute1() {
        final double FROM_LAT = 36.914893, FROM_LON = -116.76821; // NADAV stop
        final double TO_LAT = 36.914944, TO_LON = -116.761472; // NANAA stop
        GHRequest ghRequest = new GHRequest(
                FROM_LAT, FROM_LON,
                TO_LAT, TO_LON
        );
        ghRequest.getHints().put(GraphHopperGtfs.EARLIEST_DEPARTURE_TIME_HINT, LocalDateTime.of(2007,1,1,0,0).toString());
        ghRequest.getHints().put(GraphHopperGtfs.MAX_WALK_DISTANCE_PER_LEG, 30);
        GHResponse route = graphHopper.route(ghRequest);

        assertFalse(route.hasErrors());
        assertEquals(1, route.getAll().size());
        assertEquals("Expected travel time == scheduled arrival time", time(6, 49), route.getBest().getTime(), 0.1);
    }

    @Test
    public void testRoute1DoesNotGoAt654() {
        final double FROM_LAT = 36.914893, FROM_LON = -116.76821; // NADAV stop
        final double TO_LAT = 36.914944, TO_LON = -116.761472; // NANAA stop
        GHRequest ghRequest = new GHRequest(
                FROM_LAT, FROM_LON,
                TO_LAT, TO_LON
        );
        ghRequest.getHints().put(GraphHopperGtfs.EARLIEST_DEPARTURE_TIME_HINT, LocalDateTime.of(2007,1,1,6,54).toString());
        ghRequest.getHints().put(GraphHopperGtfs.MAX_WALK_DISTANCE_PER_LEG, 30);
        GHResponse route = graphHopper.route(ghRequest);

        assertFalse(route.hasErrors());
        assertEquals(1, route.getAll().size());
        assertEquals("Expected travel time == scheduled arrival time", time(0, 25), route.getBest().getTime(), 0.1);
    }

    @Test
    public void testRoute1GoesAt744() {
        final double FROM_LAT = 36.914893, FROM_LON = -116.76821; // NADAV stop
        final double TO_LAT = 36.914944, TO_LON = -116.761472; // NANAA stop
        GHRequest ghRequest = new GHRequest(
                FROM_LAT, FROM_LON,
                TO_LAT, TO_LON
        );
        ghRequest.getHints().put(GraphHopperGtfs.EARLIEST_DEPARTURE_TIME_HINT, LocalDateTime.of(2007,1,1,7,44).toString());
        ghRequest.getHints().put(GraphHopperGtfs.IGNORE_TRANSFERS, "true");

        GHResponse response = graphHopper.route(ghRequest);

        assertEquals(1, response.getAll().size());
        assertEquals("Expected travel time == scheduled arrival time", time(0, 5), response.getBest().getTime(), 0.1);
    }



    @Test
    public void testRoute1ArriveBy() {
        final double FROM_LAT = 36.914893, FROM_LON = -116.76821; // NADAV stop
        final double TO_LAT = 36.914944, TO_LON = -116.761472; // NANAA stop
        GHRequest ghRequest = new GHRequest(
                FROM_LAT, FROM_LON,
                TO_LAT, TO_LON
        );
        ghRequest.getHints().put(GraphHopperGtfs.EARLIEST_DEPARTURE_TIME_HINT, LocalDateTime.of(2007,1,1,6, 49));
        ghRequest.getHints().put(GraphHopperGtfs.ARRIVE_BY, true);
        ghRequest.getHints().put(GraphHopperGtfs.MAX_WALK_DISTANCE_PER_LEG, 30);

        GHResponse route = graphHopper.route(ghRequest);

        assertFalse(route.hasErrors());
        assertEquals(1, route.getAll().size());
        assertEquals("Expected travel time == scheduled travel time", time(0, 5), route.getBest().getTime(), 0.1);

    }

    @Test
    public void testRoute1ProfileEarliestArrival() {
        final double FROM_LAT = 36.914893, FROM_LON = -116.76821; // NADAV stop
        final double TO_LAT = 36.914944, TO_LON = -116.761472; // NANAA stop
        GHRequest ghRequest = new GHRequest(
                FROM_LAT, FROM_LON,
                TO_LAT, TO_LON
        );
        ghRequest.getHints().put(GraphHopperGtfs.EARLIEST_DEPARTURE_TIME_HINT, LocalDateTime.of(2007,1,1,0,0));
        ghRequest.getHints().put(GraphHopperGtfs.RANGE_QUERY_END_TIME, LocalDateTime.of(2007,1,1,13,0));
        ghRequest.getHints().put(GraphHopperGtfs.IGNORE_TRANSFERS, "true");
        ghRequest.getHints().put(GraphHopperGtfs.MAX_WALK_DISTANCE_PER_LEG, 30);

        GHResponse response = graphHopper.route(ghRequest);
        List<LocalTime> actualDepartureTimes = response.getAll().stream()
                .map(path -> LocalTime.from(((Trip.PtLeg) path.getLegs().get(1)).departureTime.toInstant().atZone(ZoneId.systemDefault())))
                .collect(Collectors.toList());
        List<LocalTime> expectedDepartureTimes = Stream.of(
                "06:44", "07:14", "07:44", "08:14", "08:44", "08:54", "09:04", "09:14", "09:24", "09:34", "09:44", "09:54",
                "10:04", "10:14", "10:24", "10:34", "10:44", "11:14", "11:44", "12:14", "12:44")
                .map(LocalTime::parse)
                .collect(Collectors.toList());
        assertEquals(expectedDepartureTimes, actualDepartureTimes);
    }

    @Test
    public void testRoute1ProfileLatestDeparture() {
        final double FROM_LAT = 36.914893, FROM_LON = -116.76821; // NADAV stop
        final double TO_LAT = 36.914944, TO_LON = -116.761472; // NANAA stop
        GHRequest ghRequest = new GHRequest(
                FROM_LAT, FROM_LON,
                TO_LAT, TO_LON
        );
        ghRequest.getHints().put(GraphHopperGtfs.EARLIEST_DEPARTURE_TIME_HINT, LocalDateTime.of(2007,1,2,13,00));
        ghRequest.getHints().put(GraphHopperGtfs.ARRIVE_BY, "true");
        ghRequest.getHints().put(GraphHopperGtfs.RANGE_QUERY_END_TIME, LocalDateTime.of(2007,1,2,11,0));
        // TODO: Find the problem with 1.1.2007
        ghRequest.getHints().put(GraphHopperGtfs.IGNORE_TRANSFERS, "true");
        ghRequest.getHints().put(GraphHopperGtfs.MAX_WALK_DISTANCE_PER_LEG, 30);

        GHResponse response = graphHopper.route(ghRequest);
        List<LocalTime> actualDepartureTimes = response.getAll().stream()
                .map(path -> LocalTime.from(((Trip.PtLeg) path.getLegs().get(1)).departureTime.toInstant().atZone(ZoneId.systemDefault())))
                .collect(Collectors.toList());
        List<LocalTime> expectedDepartureTimes = Stream.of(
                "12:44", "12:14", "11:44", "11:14", "10:44")
                .map(LocalTime::parse)
                .collect(Collectors.toList());
        assertEquals(expectedDepartureTimes, actualDepartureTimes);
    }

    @Test
    public void testRoute2() {
        final double FROM_LAT = 36.914894, FROM_LON = -116.76821; // NADAV stop
        final double TO_LAT = 36.909489, TO_LON = -116.768242; // DADAN stop
        assertTravelTimeIs(graphHopper, FROM_LAT, FROM_LON, TO_LAT, TO_LON, time(6, 19));
    }

    @Test
    public void testRoute3() {
        final double FROM_LAT = 36.915682, FROM_LON = -116.751677; // STAGECOACH stop
        final double TO_LAT = 36.914944, TO_LON = -116.761472; // NANAA stop
        assertTravelTimeIs(graphHopper, FROM_LAT, FROM_LON, TO_LAT, TO_LON, time(6, 5));
    }

    @Test
    public void testRoute4() {
        final double FROM_LAT = 36.915682, FROM_LON = -116.751677; // STAGECOACH stop
        final double TO_LAT = 36.914894, TO_LON = -116.76821; // NADAV stop
        assertTravelTimeIs(graphHopper, FROM_LAT, FROM_LON, TO_LAT, TO_LON, time(6, 12));
    }

    @Test
    public void testRoute5() {
        final double FROM_LAT = 36.915682, FROM_LON = -116.751677; // STAGECOACH stop
        final double TO_LAT = 36.88108, TO_LON = -116.81797; // BULLFROG stop
        GHRequest ghRequest = new GHRequest(
                FROM_LAT, FROM_LON,
                TO_LAT, TO_LON
        );
        ghRequest.getHints().put(GraphHopperGtfs.EARLIEST_DEPARTURE_TIME_HINT, LocalDateTime.of(2007,1,1,0,0));
        ghRequest.getHints().put(GraphHopperGtfs.MAX_WALK_DISTANCE_PER_LEG, 30);
        GHResponse route = graphHopper.route(ghRequest);

        assertFalse(route.hasErrors());
        assertFalse(route.getAll().isEmpty());
        assertEquals("Expected travel time == scheduled travel time", time(8, 10), route.getBest().getTime(), 0.1);
        assertEquals("Using expected route", "STBA", (((Trip.PtLeg) route.getBest().getLegs().get(1)).tripId));
        assertEquals("Paid expected fare", 250, route.getBest().getFare().multiply(BigDecimal.valueOf(100)).intValue()); // Two legs, no transfers allowed. Need two 'p' tickets costing 125 cents each.
    }

    @Test
    public void testRoute6() {
        final double FROM_LAT = 36.7, FROM_LON = -116.5; // HASNOROUTES stop
        final double TO_LAT = 36.914894, TO_LON = -116.76821; // NADAV stop
        assertNoRoute(graphHopper, FROM_LAT, FROM_LON, TO_LAT, TO_LON);
    }

    @Test
    public void testRouteWithLaterDepartureTime() {
        final double FROM_LAT = 36.915682, FROM_LON = -116.751677; // STAGECOACH stop
        final double TO_LAT = 36.914894, TO_LON = -116.76821; // NADAV stop
        // Missed the bus at 10 by one minute, will have to use the 10:30 one.
        assertTravelTimeIs(graphHopper, FROM_LAT, FROM_LON, LocalDateTime.of(2007,1,1,10, 1), TO_LAT, TO_LON, time(0, 41));
    }

    @Test
    public void testWeekendRouteWorksOnlyOnWeekend() {
        final double FROM_LAT = 36.868446, FROM_LON = -116.784582; // BEATTY_AIRPORT stop
        final double TO_LAT = 36.641496, TO_LON = -116.40094; // AMV stop
        GHRequest ghRequest = new GHRequest(
                FROM_LAT, FROM_LON,
                TO_LAT, TO_LON
        );
        ghRequest.getHints().put(GraphHopperGtfs.EARLIEST_DEPARTURE_TIME_HINT, LocalDateTime.of(2007,1,1,0,0)); // Monday morning
        ghRequest.getHints().put(GraphHopperGtfs.MAX_WALK_DISTANCE_PER_LEG, 30);


        GHResponse route = graphHopper.route(ghRequest);
        Assert.assertTrue(route.getAll().isEmpty()); // No service on monday morning, and we cannot spend the night at stations yet

        GHRequest ghRequest1 = new GHRequest(
                FROM_LAT, FROM_LON,
                TO_LAT, TO_LON
        );
        ghRequest1.getHints().put(GraphHopperGtfs.EARLIEST_DEPARTURE_TIME_HINT, LocalDateTime.of(2007,1,6,0,0));
        ghRequest1.getHints().put(GraphHopperGtfs.MAX_WALK_DISTANCE_PER_LEG, 30);
        GHResponse route1 = graphHopper.route(ghRequest1);

        assertFalse(route1.hasErrors());
        assertFalse(route1.getAll().isEmpty());
        assertEquals("Expected travel time == scheduled travel time", time(9, 0), route1.getBest().getTime());
        assertEquals("Using expected trip", "AAMV1", (((Trip.PtLeg) route1.getBest().getLegs().get(1)).tripId));
        assertEquals("Paid expected fare", 525, route1.getBest().getFare().multiply(BigDecimal.valueOf(100)).intValue());

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
        ghRequest.getHints().put(GraphHopperGtfs.MAX_WALK_DISTANCE_PER_LEG, 30);
        GHResponse route = graphHopper.route(ghRequest);
        assertEquals("Only find one solution. If blocks wouldn't work, there would be two. (There is a slower alternative without transfer.)", 1, route.getAll().size());
        assertEquals("Expected travel time == scheduled travel time", time(1,20), route.getBest().getTime());
        assertEquals("Four legs: walk, pt, pt, walk, but the two pt legs are in one vehicle, so...", 4, route.getBest().getLegs().size());
        assertEquals("...one boarding instruction", 1, route.getBest().getInstructions().stream().filter(i -> i.getSign() == Instruction.PT_START_TRIP).count());
        assertEquals("...and one alighting instruction", 1, route.getBest().getInstructions().stream().filter(i -> i.getSign() == Instruction.PT_END_TRIP).count());
    }

    @Test
    public void testTransferRules() {
        final double FROM_LAT = 36.915682, FROM_LON = -116.751677; // STAGECOACH stop
        final double VIA_LAT = 36.868446, VIA_LON = -116.784582; // BEATTY_AIRPORT stop
        final double TO1_LAT = 36.641496, TO1_LON = -116.40094; // AMV stop
        final double TO2_LAT = 36.88108, TO2_LON = -116.81797; // BULLFROG stop

        GHRequest request = new GHRequest(
                FROM_LAT, FROM_LON,
                TO1_LAT, TO1_LON
        );
        request.getHints().put(GraphHopperGtfs.EARLIEST_DEPARTURE_TIME_HINT, LocalDateTime.of(2007,1,6,7,30));

        GHResponse response = graphHopper.route(request);
        assertEquals("Ignoring transfer rules (free walking): Will be there at 9.", time(1, 30), response.getBest().getTime());

        request = new GHRequest(
                FROM_LAT, FROM_LON,
                TO1_LAT, TO1_LON
        );
        request.getHints().put(GraphHopperGtfs.EARLIEST_DEPARTURE_TIME_HINT, LocalDateTime.of(2007,1,6,7,30));
        request.getHints().put(GraphHopperGtfs.MAX_TRANSFER_DISTANCE_PER_LEG, 0.0);

        response = graphHopper.route(request);
        assertEquals("Transfer rule: 11 minutes. Will miss connection, and be there at 14.", time(6, 30), response.getBest().getTime());

        request = new GHRequest(
                FROM_LAT, FROM_LON,
                TO2_LAT, TO2_LON
        );
        request.getHints().put(GraphHopperGtfs.EARLIEST_DEPARTURE_TIME_HINT, LocalDateTime.of(2007,1,6,7,30));

        response = graphHopper.route(request);
        assertEquals("Ignoring transfer rules (free walking): Will be there at 8:10.", time(0, 40), response.getBest().getTime());

        request = new GHRequest(
                FROM_LAT, FROM_LON,
                TO2_LAT, TO2_LON
        );
        request.getHints().put(GraphHopperGtfs.EARLIEST_DEPARTURE_TIME_HINT, LocalDateTime.of(2007,1,6,7,30));
        request.getHints().put(GraphHopperGtfs.MAX_TRANSFER_DISTANCE_PER_LEG, 0.0);

        response = graphHopper.route(request);
        assertEquals("Will still be there at 8:10 because there is a route-specific exception for this route.", time(0, 40), response.getBest().getTime());

        request = new GHRequest(
                TO2_LAT, TO2_LON,
                FROM_LAT, FROM_LON
        );
        request.getHints().put(GraphHopperGtfs.EARLIEST_DEPARTURE_TIME_HINT, LocalDateTime.of(2007,1,6,12,05));
        request.getHints().put(GraphHopperGtfs.MAX_TRANSFER_DISTANCE_PER_LEG, 0.0);

        response = graphHopper.route(request);
        assertEquals("Will take 1:15 because of a 'from route' exception with a longer transfer time.", time(1, 15), response.getBest().getTime());

    }


    private void assertTravelTimeIs(GraphHopperGtfs graphHopper, double from_lat, double from_lon, LocalDateTime earliestDepartureTime, double to_lat, double to_lon, int expectedTravelTime) {
        GHRequest ghRequest = new GHRequest(
                from_lat, from_lon,
                to_lat, to_lon
        );
        ghRequest.getHints().put(GraphHopperGtfs.EARLIEST_DEPARTURE_TIME_HINT, earliestDepartureTime);
        ghRequest.getHints().put(GraphHopperGtfs.MAX_WALK_DISTANCE_PER_LEG, 30);
        GHResponse route = graphHopper.route(ghRequest);

        assertFalse(route.hasErrors());
        assertFalse(route.getAll().isEmpty());
        assertEquals("Expected travel time == scheduled travel time", expectedTravelTime, route.getBest().getTime(), 0.1);
    }

    private void assertTravelTimeIs(GraphHopperGtfs graphHopper, double FROM_LAT, double FROM_LON, double TO_LAT, double TO_LON, int expectedWeight) {
        assertTravelTimeIs(graphHopper, FROM_LAT, FROM_LON, LocalDateTime.of(2007,1,1,0,0), TO_LAT, TO_LON, expectedWeight);
    }

    private void assertNoRoute(GraphHopperGtfs graphHopper, double from_lat, double from_lon, double to_lat, double to_lon) {
        GHRequest ghRequest = new GHRequest(
                from_lat, from_lon,
                to_lat, to_lon
        );
        ghRequest.getHints().put(GraphHopperGtfs.EARLIEST_DEPARTURE_TIME_HINT, LocalDateTime.of(2007,1,1,0,0).toString());
        ghRequest.getHints().put(GraphHopperGtfs.MAX_WALK_DISTANCE_PER_LEG, 30);

        GHResponse route = graphHopper.route(ghRequest);
        Assert.assertTrue(route.getAll().isEmpty());
    }

}
