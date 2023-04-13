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

import com.conveyal.gtfs.GTFSFeed;
import com.graphhopper.config.Profile;
import com.graphhopper.gtfs.*;
import com.graphhopper.util.Helper;
import com.graphhopper.util.TranslationMap;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.io.ParseException;
import org.locationtech.jts.io.WKTReader;

import java.io.File;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static com.graphhopper.gtfs.GtfsHelper.time;
import static com.graphhopper.util.Parameters.Details.EDGE_KEY;
import static org.junit.jupiter.api.Assertions.*;

public class AnotherAgencyIT {

    private static final String GRAPH_LOC = "target/AnotherAgencyIT";
    private static PtRouter ptRouter;
    private static final ZoneId zoneId = ZoneId.of("America/Los_Angeles");
    private static GraphHopperGtfs graphHopperGtfs;

    @BeforeAll
    public static void init() {
        GraphHopperConfig ghConfig = new GraphHopperConfig();
        ghConfig.putObject("graph.location", GRAPH_LOC);
        ghConfig.putObject("import.osm.ignored_highways", "");
        ghConfig.putObject("datareader.file", "files/beatty.osm");
        ghConfig.putObject("gtfs.file", "files/sample-feed,files/another-sample-feed");
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
        Request ghRequest = new Request(
                Arrays.asList(
                        new GHStationLocation("JUSTICE_COURT"),
                        new GHStationLocation("MUSEUM")
                ),
                LocalDateTime.of(2007, 1, 1, 8, 30, 0).atZone(zoneId).toInstant()
        );
        ghRequest.setIgnoreTransfers(true);
        ghRequest.setWalkSpeedKmH(0.005); // Prevent walk solution
        GHResponse route = ptRouter.route(ghRequest);

        assertFalse(route.hasErrors());
        assertEquals(1, route.getAll().size());
        ResponsePath transitSolution = route.getBest();
        assertEquals(time(1, 30), transitSolution.getTime(), "Expected total travel time == scheduled travel time + wait time");
    }

    @Test
    public void testRoute2() {
        Request ghRequest = new Request(
                Arrays.asList(
                        new GHStationLocation("JUSTICE_COURT"),
                        new GHStationLocation("AIRPORT")
                ),
                LocalDateTime.of(2007, 1, 1, 8, 30, 0).atZone(zoneId).toInstant()
        );
        ghRequest.setIgnoreTransfers(true);
        ghRequest.setWalkSpeedKmH(0.005); // Prevent walk solution
        GHResponse route = ptRouter.route(ghRequest);

        assertFalse(route.hasErrors());
        assertEquals(1, route.getAll().size());
        ResponsePath transitSolution = route.getBest();
        assertEquals(2, transitSolution.getLegs().size());
        Trip.PtLeg ptLeg1 = (Trip.PtLeg) transitSolution.getLegs().get(0);
        assertEquals("COURT2MUSEUM", ptLeg1.route_id);
        assertEquals("MUSEUM1", ptLeg1.trip_id);
        assertEquals("JUSTICE_COURT", ptLeg1.stops.get(0).stop_id);
        assertEquals("MUSEUM", ptLeg1.stops.get(1).stop_id);

        Trip.PtLeg ptLeg2 = (Trip.PtLeg) transitSolution.getLegs().get(1);
        assertEquals("MUSEUM2AIRPORT", ptLeg2.route_id);
        assertEquals("MUSEUMAIRPORT1", ptLeg2.trip_id);
        assertEquals("NEXT_TO_MUSEUM", ptLeg2.stops.get(0).stop_id);
        assertEquals("AIRPORT", ptLeg2.stops.get(1).stop_id);

        assertEquals(time(2, 10), transitSolution.getTime(), "Expected total travel time == scheduled travel time + wait time");
    }

    @Test
    public void testTransferBetweenFeeds() {
        Request ghRequest = new Request(
                Arrays.asList(
                        new GHStationLocation("NEXT_TO_MUSEUM"),
                        new GHStationLocation("BULLFROG")
                ),
                LocalDateTime.of(2007, 1, 1, 10, 0, 0).atZone(zoneId).toInstant()
        );
        ghRequest.setIgnoreTransfers(true);
        ghRequest.setWalkSpeedKmH(0.005); // Prevent walk solution
        ResponsePath transitSolution = ptRouter.route(ghRequest).getBest();
        List<Trip.Leg> ptLegs = transitSolution.getLegs().stream().filter(l -> l instanceof Trip.PtLeg).collect(Collectors.toList());
        assertEquals("NEXT_TO_MUSEUM,AIRPORT", ((Trip.PtLeg) ptLegs.get(0)).stops.stream().map(s -> s.stop_id).collect(Collectors.joining(",")));
        assertEquals("BEATTY_AIRPORT,BULLFROG", ((Trip.PtLeg) ptLegs.get(1)).stops.stream().map(s -> s.stop_id).collect(Collectors.joining(",")));
        Instant arrivalTime = Instant.ofEpochMilli(transitSolution.getLegs().get(1).getArrivalTime().getTime());
        assertEquals("14:10", LocalDateTime.ofInstant(arrivalTime, zoneId).toLocalTime().toString());
        assertEquals(15_000_000, Duration.between(ghRequest.getEarliestDepartureTime(), arrivalTime).toMillis());
        assertEquals(1.5E7, transitSolution.getRouteWeight());
    }

    @Test
    public void testWalkTransferBetweenFeeds() {
        Request ghRequest = new Request(
                Arrays.asList(
                        new GHStationLocation("JUSTICE_COURT"),
                        new GHStationLocation("DADAN")
                ),
                LocalDateTime.of(2007, 1, 1, 9, 0, 0).atZone(zoneId).toInstant()
        );
        ghRequest.setIgnoreTransfers(true);
        ghRequest.setWalkSpeedKmH(0.5); // Prevent walk solution
        ghRequest.setPathDetails(Arrays.asList(EDGE_KEY));
        GHResponse route = ptRouter.route(ghRequest);

        assertFalse(route.hasErrors());
        assertEquals(1, route.getAll().size());

        ResponsePath transitSolution = route.getBest();
        assertEquals(4500000L, transitSolution.getTime());
        assertEquals(4500000.0, transitSolution.getRouteWeight());
        assertEquals(time(1, 15), transitSolution.getTime(), "Expected total travel time == scheduled travel time + wait time");

        assertEquals("JUSTICE_COURT,MUSEUM", ((Trip.PtLeg) transitSolution.getLegs().get(0)).stops.stream().map(s -> s.stop_id).collect(Collectors.joining(",")));
        Instant walkDepartureTime = Instant.ofEpochMilli(transitSolution.getLegs().get(1).getDepartureTime().getTime());
        assertEquals("10:00", LocalDateTime.ofInstant(walkDepartureTime, zoneId).toLocalTime().toString());
        assertEquals(readWktLineString("LINESTRING (-116.76164 36.906093, -116.761812 36.905928, -116.76217 36.905659)"), transitSolution.getLegs().get(1).geometry);
        Instant walkArrivalTime = Instant.ofEpochMilli(transitSolution.getLegs().get(1).getArrivalTime().getTime());
        assertEquals("10:08:06.670", LocalDateTime.ofInstant(walkArrivalTime, zoneId).toLocalTime().toString());
        assertEquals("EMSI,DADAN", ((Trip.PtLeg) transitSolution.getLegs().get(2)).stops.stream().map(s -> s.stop_id).collect(Collectors.joining(",")));
    }

    @Test
    public void testMuseumToEmsi() {
        Request ghRequest = new Request(
                Arrays.asList(
                        new GHStationLocation("MUSEUM"),
                        new GHStationLocation("EMSI")
                ),
                LocalDateTime.of(2007, 1, 1, 9, 0, 0).atZone(zoneId).toInstant()
        );
        ghRequest.setWalkSpeedKmH(0.5);
        ghRequest.setIgnoreTransfers(true);
        GHResponse route = ptRouter.route(ghRequest);
        ResponsePath walkRoute = route.getBest();
        assertEquals(1, walkRoute.getLegs().size());
        assertEquals(486670, walkRoute.getTime()); // < 10 min, so the transfer in test above works ^^
        assertEquals(readWktLineString("LINESTRING (-116.76164 36.906093, -116.761812 36.905928, -116.76217 36.905659)"), walkRoute.getLegs().get(0).geometry);
        assertFalse(route.hasErrors());
    }

    @Test
    public void testBoardingsAtAirport() {
        GTFSFeed gtfs_1 = graphHopperGtfs.getGtfsStorage().getGtfsFeeds().get("gtfs_1");
        RealtimeFeed.findAllBoardings(graphHopperGtfs.getGtfsStorage(), new GtfsStorage.FeedIdWithStopId("gtfs_1", "AIRPORT"))
                .forEach(e -> assertNotNull(gtfs_1.trips.get(e.getAttrs().tripDescriptor.getTripId()), "I only get trips from this feed here."));
    }

    private LineString readWktLineString(String wkt) {
        WKTReader wktReader = new WKTReader();
        LineString expectedGeometry = null;
        try {
            expectedGeometry = (LineString) wktReader.read(wkt);
        } catch (ParseException e) {
            e.printStackTrace();
        }
        return expectedGeometry;
    }

}
