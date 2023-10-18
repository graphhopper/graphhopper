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
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Arrays;

import static com.graphhopper.gtfs.GtfsHelper.time;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

public class ExtendedRouteTypeIT {

    private static final String GRAPH_LOC = "target/ExtendedRouteType";
    private static PtRouter ptRouter;
    private static final ZoneId zoneId = ZoneId.of("America/Los_Angeles");
    private static GraphHopperGtfs graphHopperGtfs;

    @BeforeAll
    public static void init() {
        GraphHopperConfig ghConfig = new GraphHopperConfig();
        ghConfig.putObject("graph.location", GRAPH_LOC);
        ghConfig.putObject("gtfs.file", "files/another-sample-feed-extended-route-type.zip");
        ghConfig.putObject("import.osm.ignored_highways", "");
        ghConfig.setProfiles(Arrays.asList(
                new Profile("foot").setVehicle("foot"),
                new Profile("car").setVehicle("car")));
        Helper.removeDir(new File(GRAPH_LOC));
        graphHopperGtfs = new GraphHopperGtfs(ghConfig);
        graphHopperGtfs.init(ghConfig);
        graphHopperGtfs.importOrLoad();
        ptRouter = new PtRouterImpl.Factory(ghConfig, new TranslationMap().doImport(), graphHopperGtfs.getBaseGraph(), graphHopperGtfs.getEncodingManager(), graphHopperGtfs.getLocationIndex(), graphHopperGtfs.getGtfsStorage()).createWithoutRealtimeFeed();
    }

    @AfterAll
    public static void close() {
        graphHopperGtfs.close();
    }

    @Test
    public void testRoute1() {
        final double FROM_LAT = 36.9010208, FROM_LON = -116.7659466;
        final double TO_LAT = 36.9059371, TO_LON = -116.7618071;
        Request ghRequest = new Request(
                FROM_LAT, FROM_LON,
                TO_LAT, TO_LON
        );
        ghRequest.setEarliestDepartureTime(LocalDateTime.of(2007, 1, 1, 9, 0, 0).atZone(zoneId).toInstant());
        ghRequest.setIgnoreTransfers(true);
        GHResponse route = ptRouter.route(ghRequest);

        assertFalse(route.hasErrors());
        assertEquals(1, route.getAll().size());
        assertEquals(time(1, 0), route.getBest().getTime(), 0.1, "Expected travel time == scheduled arrival time");
    }

}
