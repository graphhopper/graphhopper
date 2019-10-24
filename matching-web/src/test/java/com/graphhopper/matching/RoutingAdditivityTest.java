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

package com.graphhopper.matching;

import com.graphhopper.GHRequest;
import com.graphhopper.GraphHopper;
import com.graphhopper.PathWrapper;
import com.graphhopper.reader.osm.GraphHopperOSM;
import com.graphhopper.routing.util.CarFlagEncoder;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.util.shapes.GHPoint;
import org.junit.Before;
import org.junit.Test;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.lessThan;
import static org.hamcrest.core.Is.is;


public class RoutingAdditivityTest {

    private GraphHopper graphHopper;

    @Before
    public void setup() {
        CarFlagEncoder encoder = new CarFlagEncoder();
        graphHopper = new GraphHopperOSM();
        graphHopper.setDataReaderFile("../map-data/leipzig_germany.osm.pbf");
        graphHopper.setGraphHopperLocation("../target/mapmatchingtest-ch");
        graphHopper.setEncodingManager(EncodingManager.create(encoder));
        graphHopper.getCHFactoryDecorator().setDisablingAllowed(true);
        graphHopper.importOrLoad();
    }


    @Test
    public void testBoundedAdditivityOfGraphhopperTravelTimes() {
        PathWrapper route1 = graphHopper.route(new GHRequest(
                new GHPoint(51.23, 12.18),
                new GHPoint(51.45, 12.59))
                .setWeighting("fastest")).getBest();

        // Re-route from snapped point to snapped point.
        // It's the only way to be sure.
        PathWrapper route2 = graphHopper.route(new GHRequest(
                route1.getWaypoints().get(0),
                route1.getWaypoints().get(1))
                .setWeighting("fastest")).getBest();

        assertThat(route1.getTime(), is(equalTo(route2.getTime())));

        long travelTime = 0L;
        for (int i = 0; i < route2.getPoints().size()-1; i++) {
            PathWrapper segment = graphHopper.route(new GHRequest(
                    route2.getPoints().get(i),
                    route2.getPoints().get(i + 1))
                    .setWeighting("fastest")).getBest();
            travelTime += segment.getTime();
        }

        // Even though I route from node to node, and travel times are longs, not doubles,
        // I apparently don't get the exact result if I sum up the travel times between segments.
        // But it's within one second.
        assertThat(Math.abs(travelTime - route2.getTime()), is(lessThan(1000L)));
    }

}
