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

package com.graphhopper.application;

import com.graphhopper.GHRequest;
import com.graphhopper.core.GraphHopper;
import com.graphhopper.ResponsePath;
import com.graphhopper.config.LMProfile;
import com.graphhopper.config.Profile;
import com.graphhopper.util.Helper;
import com.graphhopper.util.shapes.GHPoint;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.File;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.lessThan;
import static org.hamcrest.core.Is.is;


public class RoutingAdditivityTest {

    private static final String GH_LOCATION = "../target/routing-additivity-test-gh";
    private static GraphHopper graphHopper;

    @BeforeAll
    public static void setup() {
        Helper.removeDir(new File(GH_LOCATION));
        graphHopper = new GraphHopper();
        graphHopper.setOSMFile("../map-matching/files/leipzig_germany.osm.pbf");
        graphHopper.setGraphHopperLocation(GH_LOCATION);
        graphHopper.setProfiles(new Profile("my_profile").setVehicle("car").setWeighting("fastest"));
        graphHopper.getLMPreparationHandler().setLMProfiles(new LMProfile("my_profile"));
        graphHopper.importOrLoad();
    }

    @AfterAll
    public static void cleanup() {
        graphHopper = null;
    }

    @Test
    public void testBoundedAdditivityOfGraphhopperTravelTimes() {
        ResponsePath route1 = graphHopper.route(new GHRequest(
                new GHPoint(51.23, 12.18),
                new GHPoint(51.45, 12.59))
                .setProfile("my_profile")).getBest();

        // Re-route from snapped point to snapped point.
        // It's the only way to be sure.
        ResponsePath route2 = graphHopper.route(new GHRequest(
                route1.getWaypoints().get(0),
                route1.getWaypoints().get(1))
                .setProfile("my_profile")).getBest();

        assertThat(route1.getTime(), is(equalTo(route2.getTime())));

        long travelTime = 0L;
        for (int i = 0; i < route2.getPoints().size() - 1; i++) {
            ResponsePath segment = graphHopper.route(new GHRequest(
                    route2.getPoints().get(i),
                    route2.getPoints().get(i + 1))
                    .setProfile("my_profile")).getBest();
            travelTime += segment.getTime();
        }

        // Even though I route from node to node, and travel times are longs, not doubles,
        // I apparently don't get the exact result if I sum up the travel times between segments.
        // But it's within one second.
        assertThat(Math.abs(travelTime - route2.getTime()), is(lessThan(1000L)));
    }

}
