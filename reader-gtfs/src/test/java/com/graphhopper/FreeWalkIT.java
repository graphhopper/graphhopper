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

import com.graphhopper.config.Profile;
import com.graphhopper.gtfs.*;
import com.graphhopper.util.Helper;
import com.graphhopper.util.TranslationMap;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static com.graphhopper.gtfs.GtfsHelper.time;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

public class FreeWalkIT {

    private static final String GRAPH_LOC = "target/FreeWalkIT";
    private static PtRouter ptRouter;
    private static final ZoneId zoneId = ZoneId.of("America/Los_Angeles");
    private static GraphHopperGtfs graphHopperGtfs;

    @BeforeAll
    public static void init() {
        GraphHopperConfig ghConfig = new GraphHopperConfig();
        ghConfig.putObject("graph.location", GRAPH_LOC);
        ghConfig.putObject("datareader.file", "files/beatty.osm");
        ghConfig.putObject("gtfs.file", "files/sample-feed.zip,files/another-sample-feed.zip");
        ghConfig.putObject("gtfs.max_transfer_interpolation_walk_time_seconds", 0);
        // TODO: This setting vv is currently "dead", as in production it switches to PtRouterFreeWalkImpl, but
        // TODO: here it is instantiated directly. Refactor by having only one Router but two Solvers, similar
        // TODO: to the street router.
        ghConfig.putObject("gtfs.free_walk", true);
        ghConfig.setProfiles(Arrays.asList(
                new Profile("foot").setVehicle("foot").setWeighting("fastest"),
                new Profile("car").setVehicle("car").setWeighting("fastest")));
        Helper.removeDir(new File(GRAPH_LOC));
        graphHopperGtfs = new GraphHopperGtfs(ghConfig);
        graphHopperGtfs.init(ghConfig);
        graphHopperGtfs.importOrLoad();
        ptRouter = PtRouterFreeWalkImpl.createFactory(ghConfig, new TranslationMap().doImport(), graphHopperGtfs, graphHopperGtfs.getLocationIndex(), graphHopperGtfs.getGtfsStorage())
                .createWithoutRealtimeFeed();
    }

    @AfterAll
    public static void close() {
        graphHopperGtfs.close();
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
        GHResponse route = ptRouter.route(ghRequest);

        assertFalse(route.hasErrors());
        assertEquals(1, route.getAll().size());
        ResponsePath transitSolution = route.getBest();
        Trip.PtLeg firstLeg = ((Trip.PtLeg) transitSolution.getLegs().get(0));
        Trip.WalkLeg transferLeg = ((Trip.WalkLeg) transitSolution.getLegs().get(1));
        Trip.PtLeg secondLeg = ((Trip.PtLeg) transitSolution.getLegs().get(2));
        assertEquals("JUSTICE_COURT,MUSEUM", firstLeg.stops.stream().map(s -> s.stop_id).collect(Collectors.joining(",")));
        assertEquals("EMSI,DADAN", secondLeg.stops.stream().map(s -> s.stop_id).collect(Collectors.joining(",")));
        assertEquals(LocalDateTime.parse("2007-01-01T10:00:00").atZone(zoneId).toInstant(), transferLeg.getDepartureTime().toInstant());
        assertEquals(LocalDateTime.parse("2007-01-01T10:08:06.660").atZone(zoneId).toInstant(), transferLeg.getArrivalTime().toInstant());

        assertEquals(4500000L, transitSolution.getTime());
        assertEquals(4500000.0, transitSolution.getRouteWeight());
        assertEquals(time(1, 15), transitSolution.getTime(), "Expected total travel time == scheduled travel time + wait time");
    }

}
