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
package com.graphhopper.swl;

import com.graphhopper.GHRequest;
import com.graphhopper.GHResponse;
import com.graphhopper.GraphHopper;
import com.graphhopper.http.GraphHopperManaged;
import com.graphhopper.jackson.Jackson;
import com.graphhopper.util.CmdArgs;
import com.graphhopper.util.details.PathDetail;
import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static org.hamcrest.Matchers.contains;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;

/**
 * Integration test for time-dependent edge weights.
 * Salvaged from a client project, that's why it uses a graph that's binary-committed to the repository.
 * TODO: Change that
 */
public class TDNetworkIT {
    private GraphHopper graphHopper;

    @Before
    public void setUp() {
        String graphFile = "files/swl-andorra-r5-export";
        CmdArgs configuration = new CmdArgs();
        configuration.put("r5.link_speed_file", "files/r5_predicted_tt.csv");
        configuration.put("graph.location", graphFile);
        configuration.put("routing.ch.disabling_allowed", true);
        GraphHopperManaged graphHopperService = new GraphHopperManaged(configuration, Jackson.newObjectMapper());
        graphHopperService.start();
        this.graphHopper = graphHopperService.getGraphHopper();
    }

    @Test
    public void testAndorraCar() {
        GHRequest request = new GHRequest(42.56819, 1.603231, 42.571034, 1.520662);
        request.setPathDetails(Arrays.asList("time", "edge_key"));
        GHResponse route = graphHopper.route(request);

        final int EXPECTED_LINKS_IN_PATH = 52;
        final long EXPECTED_TOTAL_TRAVEL_TIME = 1277122;

        assertEquals(21474.0, route.getBest().getDistance(), 0.1);
        assertEquals(EXPECTED_TOTAL_TRAVEL_TIME, route.getBest().getTime());

        List<PathDetail> time = route.getBest().getPathDetails().get("time");
        List<PathDetail> edgeIds = route.getBest().getPathDetails().get("edge_key");

        assertEquals(EXPECTED_LINKS_IN_PATH, time.size());
        assertEquals(EXPECTED_LINKS_IN_PATH, edgeIds.size());

        // Assert that corresponding elements in the two sequences of path details
        // describe the same intervals, i.e. the 'times' are link travel times.
        for (int i=0; i<EXPECTED_LINKS_IN_PATH; i++) {
            assertEquals(time.get(i).getFirst(), edgeIds.get(i).getFirst());
            assertEquals(time.get(i).getLast(), edgeIds.get(i).getLast());
        }

        assertEquals(EXPECTED_TOTAL_TRAVEL_TIME, sumTimes(time));
    }

    @Test
    public void testAndorraBike() {
        GHRequest request = new GHRequest(42.56819, 1.603231, 42.571034, 1.520662);
        request.setPathDetails(Arrays.asList("time", "edge_key"));
        request.setVehicle("bike");
        GHResponse route = graphHopper.route(request);

        final int EXPECTED_LINKS_IN_PATH = 52;
        final long EXPECTED_TOTAL_TRAVEL_TIME = 4294779;

        assertEquals(21474.0, route.getBest().getDistance(), 0.1);
        assertEquals(EXPECTED_TOTAL_TRAVEL_TIME, route.getBest().getTime());

        List<PathDetail> time = route.getBest().getPathDetails().get("time");
        List<PathDetail> edgeIds = route.getBest().getPathDetails().get("edge_key");

        assertEquals(EXPECTED_LINKS_IN_PATH, time.size());
        assertEquals(EXPECTED_LINKS_IN_PATH, edgeIds.size());

        // Assert that corresponding elements in the two sequences of path details
        // describe the same intervals, i.e. the 'times' are link travel times.
        for (int i=0; i<EXPECTED_LINKS_IN_PATH; i++) {
            assertEquals(time.get(i).getFirst(), edgeIds.get(i).getFirst());
            assertEquals(time.get(i).getLast(), edgeIds.get(i).getLast());
        }

        assertEquals(EXPECTED_TOTAL_TRAVEL_TIME, sumTimes(time));
    }

    @Test
    public void testAndorraFoot() {
        GHRequest request = new GHRequest(42.56819, 1.603231, 42.571034, 1.520662);
        request.setPathDetails(Arrays.asList("time", "edge_key"));
        request.setVehicle("foot");
        GHResponse route = graphHopper.route(request);

        final int EXPECTED_LINKS_IN_PATH = 45;
        final long EXPECTED_TOTAL_TRAVEL_TIME = 11768195;

        assertEquals(16344.7, route.getBest().getDistance(), 0.1);
        assertEquals(EXPECTED_TOTAL_TRAVEL_TIME, route.getBest().getTime());

        List<PathDetail> time = route.getBest().getPathDetails().get("time");
        List<PathDetail> edgeIds = route.getBest().getPathDetails().get("edge_key");

        assertEquals(EXPECTED_LINKS_IN_PATH, time.size());
        assertEquals(EXPECTED_LINKS_IN_PATH, edgeIds.size());

        // Assert that corresponding elements in the two sequences of path details
        // describe the same intervals, i.e. the 'times' are link travel times.
        for (int i=0; i<EXPECTED_LINKS_IN_PATH; i++) {
            assertEquals(time.get(i).getFirst(), edgeIds.get(i).getFirst());
            assertEquals(time.get(i).getLast(), edgeIds.get(i).getLast());
        }

        assertEquals(EXPECTED_TOTAL_TRAVEL_TIME, sumTimes(time));
    }

    @Test
    public void testAndorraTD() {
        GHRequest request = new GHRequest(42.56819, 1.603231, 42.571034, 1.520662);
        request.setAlgorithm("dijkstra");
        request.setPathDetails(Arrays.asList("edge_key"));
        request.getHints().put("ch.disable", true);
        request.setWeighting("td");
        request.getHints().put("departure_time", 58*60);
        GHResponse route = graphHopper.route(request);
        List<PathDetail> edgeIds = route.getBest().getPathDetails().get("edge_key");
        final long EXPECTED_TOTAL_TRAVEL_TIME = 1292346;

        List<Integer> actualEdgeIds = edgeIds.stream().map(pd -> ((Integer) pd.getValue())).collect(Collectors.toList());
        assertThat(actualEdgeIds, contains(4340, 31, 32, 39, 1036, 1030, 1603, 1601, 1599, 71, 69, 4315, 1589, 1587, 1585, 1583, 1581, 1579, 1577, 1575, 1551, 1549, 1547, 1545, 2371, 3439, 3437, 3391, 1381, 1379, 1377, 581, 579, 577, 575, 573, 571, 569, 558, 560, 562, 552, 566, 540, 550, 2506, 1916, 1816, 1814, 1720, 1722, 1724));

        assertEquals(EXPECTED_TOTAL_TRAVEL_TIME, route.getBest().getTime());
    }

    @Test
    public void testAndorraTDLater() {
        GHRequest request = new GHRequest(42.56819, 1.603231, 42.571034, 1.520662);
        request.setAlgorithm("dijkstra");
        request.setPathDetails(Arrays.asList("edge_key"));
        request.getHints().put("ch.disable", true);
        request.setWeighting("td");
        request.getHints().put("departure_time", 8*60*60);
        GHResponse route = graphHopper.route(request);
        List<PathDetail> edgeIds = route.getBest().getPathDetails().get("edge_key");

        // During the morning peak, we choose a different route (and it is slower)
        final long EXPECTED_TOTAL_TRAVEL_TIME = 1386129;
        List<Integer> actualEdgeIds = edgeIds.stream().map(pd -> ((Integer) pd.getValue())).collect(Collectors.toList());
        assertThat(actualEdgeIds, contains(4340, 31, 32, 34, 1038, 1040, 1018, 1023, 1061, 1059, 1029, 5609, 1601, 1599, 71, 69, 4315, 1589, 1587, 1585, 1583, 1581, 1579, 1577, 1575, 1551, 1549, 1547, 1545, 2371, 3439, 3437, 3391, 1381, 1379, 1377, 581, 579, 577, 575, 573, 571, 569, 558, 560, 562, 552, 566, 540, 550, 2506, 1916, 1816, 1814, 1720, 1722, 1724));

        assertEquals(EXPECTED_TOTAL_TRAVEL_TIME, route.getBest().getTime());
    }


    private long sumTimes(List<PathDetail> time) {
        long sum = 0;
        for (PathDetail pathDetail : time) {
            sum += (long) pathDetail.getValue();
        }
        return sum;
    }

}
