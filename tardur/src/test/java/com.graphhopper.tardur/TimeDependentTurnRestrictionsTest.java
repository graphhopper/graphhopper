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

package com.graphhopper.tardur;

import com.graphhopper.GHRequest;
import com.graphhopper.GHResponse;
import com.graphhopper.GraphHopper;
import com.graphhopper.GraphHopperConfig;
import com.graphhopper.jackson.Jackson;
import com.graphhopper.util.PointList;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class TimeDependentTurnRestrictionsTest {

    @Test
    public void testTimeDependentTurnRestrictions() throws Throwable {
        GraphHopperConfig config = new GraphHopperConfig();
        config.put("datareader.file", "files/test-timedependent-turn-restrictions.xml");
        config.put("graph.flag_encoders", "car");
        config.put("graph.location", "wurst");
        config.put("routing.ch.disabling_allowed", true);
        config.put("graph.encoded_values","conditional");
        TardurGraphHopperManaged tardurGraphHopperManaged = new TardurGraphHopperManaged(config, Jackson.newObjectMapper());
        tardurGraphHopperManaged.start();
        GraphHopper graphHopper = tardurGraphHopperManaged.getGraphHopper();
        GHRequest request = new GHRequest(52, 12, 50, 10);
        request.setAlgorithm("astar");
        request.putHint("block_property", "conditional");
        request.putHint("ch.disable", true);
        request.putHint("pt.earliest_departure_time", "2020-03-04T21:00:00.000Z");
        GHResponse response = graphHopper.route(request);
        for (Throwable error : response.getErrors()) {
            throw error;
        }
        PointList expected1 = new PointList();
        expected1.add(52.0,12.0);
        expected1.add(50.0,12.0);
        expected1.add(50.0,10.0);
        assertEquals(expected1, response.getBest().getPoints());

        request.putHint("pt.earliest_departure_time", "2020-03-04T10:00:00.000Z");
        response = graphHopper.route(request);
        for (Throwable error : response.getErrors()) {
            throw error;
        }
        PointList expected2 = new PointList();
        expected2.add(52.0,12.0);
        expected2.add(50.0,12.0);
        expected2.add(51.0,11.0);
        expected2.add(50.0,10.0);
        assertEquals(expected2, response.getBest().getPoints());
    }

}
