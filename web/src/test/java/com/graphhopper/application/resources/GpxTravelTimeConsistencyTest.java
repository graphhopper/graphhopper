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

package com.graphhopper.application.resources;

import com.graphhopper.GHRequest;
import com.graphhopper.GraphHopper;
import com.graphhopper.ResponsePath;
import com.graphhopper.config.Profile;
import com.graphhopper.gpx.GpxConversions;
import com.graphhopper.routing.weighting.custom.CustomProfile;
import com.graphhopper.util.Helper;
import com.graphhopper.util.shapes.GHPoint;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;


public class GpxTravelTimeConsistencyTest {

    public static final String DIR = "../core/files";
    private static final String graphFileFoot = "target/gpxtraveltimeconsistency-it";
    private static final String osmFile = DIR + "/monaco.osm.gz";
    private static GraphHopper hopper;

    @BeforeAll
    public static void beforeClass() {
        Helper.removeDir(new File(graphFileFoot));
        hopper = new GraphHopper().
                setOSMFile(osmFile).
                setProfiles(new CustomProfile("profile").setVehicle("foot")).
                setStoreOnFlush(true).
                setGraphHopperLocation(graphFileFoot).
                importOrLoad();
    }

    @Test
    public void testGPXListTravelTimeConsistency() {
        GHPoint routeStart = new GHPoint(43.727687, 7.418737);
        GHPoint routeEnd = new GHPoint(43.74958, 7.436566);
        GHRequest request = new GHRequest(routeStart, routeEnd);
        request.setProfile("profile");
        ResponsePath path = hopper.route(request).getBest();
        List<GpxConversions.GPXEntry> gpxList = GpxConversions.createGPXList(path.getInstructions());
        for (GpxConversions.GPXEntry entry : gpxList) {
            if (entry.getTime() != null) {
                GHRequest requestForWaypoint = new GHRequest(routeStart, entry.getPoint());
                requestForWaypoint.setProfile("profile");
                ResponsePath partialPath = hopper.route(requestForWaypoint).getBest();
                assertEquals(partialPath.getTime(), entry.getTime().longValue(), "GPXListEntry timeStamp is expected to be the same as route duration.");
            }
        }
    }

}
