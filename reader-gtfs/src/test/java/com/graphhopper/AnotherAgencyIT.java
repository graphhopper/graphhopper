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

import com.graphhopper.gtfs.*;
import com.graphhopper.util.Helper;
import com.graphhopper.util.TranslationMap;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.File;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Arrays;

import static com.graphhopper.gtfs.GtfsHelper.time;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

public class AnotherAgencyIT {

    private static final String GRAPH_LOC = "target/AnotherAgencyIT";
    private static PtRouter ptRouter;
    private static final ZoneId zoneId = ZoneId.of("America/Los_Angeles");
    private static GraphHopperGtfs graphHopperGtfs;

    @BeforeClass
    public static void init() {
        GraphHopperConfig ghConfig = new GraphHopperConfig();
        ghConfig.putObject("graph.flag_encoders", "car,foot");
        ghConfig.putObject("graph.location", GRAPH_LOC);
        ghConfig.putObject("datareader.file", "files/beatty.osm");
        ghConfig.putObject("gtfs.file", "files/sample-feed.zip,files/another-sample-feed.zip");
        Helper.removeDir(new File(GRAPH_LOC));
        graphHopperGtfs = new GraphHopperGtfs(ghConfig);
        graphHopperGtfs.init(ghConfig);
        graphHopperGtfs.importOrLoad();
        ptRouter = PtRouterImpl.createFactory(new TranslationMap().doImport(), graphHopperGtfs, graphHopperGtfs.getLocationIndex(), graphHopperGtfs.getGtfsStorage())
                .createWithoutRealtimeFeed();
    }

    @AfterClass
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
        assertEquals("Expected total travel time == scheduled travel time + wait time", time(1, 30), transitSolution.getTime());
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

        assertEquals("Expected total travel time == scheduled travel time + wait time", time(2, 10), transitSolution.getTime());
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
        ResponsePath route = ptRouter.route(ghRequest).getBest();
        LocalTime arrivalTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(route.getLegs().get(1).getArrivalTime().getTime()), zoneId).toLocalTime();
        assertEquals("14:10", arrivalTime.toString());
    }

}
