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
import com.graphhopper.util.TranslationMap;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.File;
import java.time.LocalDateTime;
import java.time.ZoneId;

import static com.graphhopper.reader.gtfs.GtfsHelper.time;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

public class ExtendedRouteTypeIT {

    private static final String GRAPH_LOC = "target/ExtendedRouteType";
    private static PtRouteResource ptRouteResource;
    private static final ZoneId zoneId = ZoneId.of("America/Los_Angeles");
    private static GraphHopperGtfs graphHopperGtfs;

    @BeforeClass
    public static void init() {
        CmdArgs cmdArgs = new CmdArgs();
        cmdArgs.put("graph.flag_encoders", "car,foot");
        cmdArgs.put("graph.location", GRAPH_LOC);
        cmdArgs.put("gtfs.file", "files/another-sample-feed-extended-route-type.zip");
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
        final double FROM_LAT = 36.9010208, FROM_LON = -116.7659466;
        final double TO_LAT =  36.9059371, TO_LON = -116.7618071;
        Request ghRequest = new Request(
                FROM_LAT, FROM_LON,
                TO_LAT, TO_LON
        );
        ghRequest.setEarliestDepartureTime(LocalDateTime.of(2007,1,1,9,0,0).atZone(zoneId).toInstant());
        ghRequest.setIgnoreTransfers(true);
        GHResponse route = ptRouteResource.route(ghRequest);

        assertFalse(route.hasErrors());
        assertEquals(1, route.getAll().size());
        assertEquals("Expected travel time == scheduled arrival time", time(1, 0), route.getBest().getTime(), 0.1);
    }

}
