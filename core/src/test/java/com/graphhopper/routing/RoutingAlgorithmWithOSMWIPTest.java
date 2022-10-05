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
package com.graphhopper.routing;

import com.graphhopper.GHRequest;
import com.graphhopper.GHResponse;
import com.graphhopper.GraphHopper;
import com.graphhopper.ResponsePath;
import com.graphhopper.config.CHProfile;
import com.graphhopper.config.LMProfile;
import com.graphhopper.config.Profile;
import com.graphhopper.util.DistanceCalc;
import com.graphhopper.util.DistanceCalcEarth;
import com.graphhopper.util.Helper;
import com.graphhopper.util.shapes.GHPoint;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;

import static com.graphhopper.util.Parameters.Algorithms.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Here we check the routes calculated by GraphHopper for different routing algorithms on real OSM data
 */
public class RoutingAlgorithmWithOSMWIPTest {

    private static final String DIR = "../core/files";

    // when creating GH instances make sure to use this as the GH location such that it will be cleaned between tests
    private static final String GH_LOCATION = "target/routing-algorithm-with-osm-test-gh";
    private final DistanceCalc distCalc = DistanceCalcEarth.DIST_EARTH;

    @BeforeEach
    @AfterEach
    public void setup() {
        Helper.removeDir(new File(GH_LOCATION));
    }
    
    @Test
    public void testViaWayTurnRestriction() {
        List<Query> list = new ArrayList<>();
        list.add(new Query(0.0506, 0.01, 0.0506, 0.014, 1200.907, 8));
        GraphHopper hopper = createHopper(DIR + "/test_way_restrictions.osm.xml",
                new Profile("car").setVehicle("car").setWeighting("fastest").setTurnCosts(true));
        hopper.importOrLoad();
        checkQueries(hopper, list);
    }

    private static class Query {
        private final List<ViaPoint> points = new ArrayList<>();

        public Query(double fromLat, double fromLon, double toLat, double toLon, double expectedDistance, int expectedPoints) {
            add(fromLat, fromLon, 0, 0);
            add(toLat, toLon, expectedDistance, expectedPoints);
        }

        public Query add(double lat, double lon, double dist, int locs) {
            points.add(new ViaPoint(lat, lon, dist, locs));
            return this;
        }

        public List<ViaPoint> getPoints() {
            return points;
        }

        @Override
        public String toString() {
            return points.toString();
        }
    }

    private static class ViaPoint {
        double lat, lon;
        int expectedPoints;
        double expectedDistance;

        public ViaPoint(double lat, double lon, double expectedDistance, int expectedPoints) {
            this.lat = lat;
            this.lon = lon;
            this.expectedPoints = expectedPoints;
            this.expectedDistance = expectedDistance;
        }

        @Override
        public String toString() {
            return lat + ", " + lon + ", expectedPoints:" + expectedPoints + ", expectedDistance:" + expectedDistance;
        }
    }

    /**
     * Creates a {@link GraphHopper} instance with some default settings for this test. The settings can
     * be adjusted before calling {@link GraphHopper#importOrLoad()}
     */
    private GraphHopper createHopper(String osmFile, Profile... profiles) {
        GraphHopper hopper = new GraphHopper().
                setStoreOnFlush(true).
                setOSMFile(osmFile).
                setProfiles(profiles).
                setGraphHopperLocation(GH_LOCATION);
        hopper.getRouterConfig().setSimplifyResponse(false);
        hopper.setMinNetworkSize(0);
        hopper.getReaderConfig().setMaxWayPointDistance(0);
        hopper.getLMPreparationHandler().setLMProfiles(new LMProfile(profiles[0].getName()));
        hopper.getCHPreparationHandler().setCHProfiles(new CHProfile(profiles[0].getName()));
        return hopper;
    }

    /**
     * Runs the given queries on the given GraphHopper instance and checks the expectations.
     * All queries will use the first profile.
     */
    private void checkQueries(GraphHopper hopper, List<Query> queries) {
        for (Function<Query, GHRequest> requestFactory : createRequestFactories()) {
            for (Query query : queries) {
                GHRequest request = requestFactory.apply(query);
                Profile profile = hopper.getProfiles().get(0);
                request.setProfile(profile.getName());
                GHResponse res = hopper.route(request);
                checkResponse(res, query);
                String expectedAlgo = request.getHints().getString("expected_algo", "no_expected_algo");
                // for edge-based routing we expect a slightly different algo name for CH
                if (profile.isTurnCosts())
                    expectedAlgo = expectedAlgo.replaceAll("\\|ch-routing", "|ch|edge_based|no_sod-routing");
                assertTrue(res.getBest().getDebugInfo().contains(expectedAlgo),
                        "Response does not contain expected algo string. Expected: '" + expectedAlgo +
                                "', got: '" + res.getBest().getDebugInfo() + "'");
            }
        }
    }

    private void checkResponse(GHResponse res, Query query) {
        assertFalse(res.hasErrors(), res.getErrors().toString());
        ResponsePath responsePath = res.getBest();
        assertFalse(responsePath.hasErrors(), responsePath.getErrors().toString());
        assertEquals(distCalc.calcDistance(responsePath.getPoints()), responsePath.getDistance(), 2,
                "responsePath.getDistance does not equal point list distance");
        assertEquals(query.getPoints().stream().mapToDouble(a -> a.expectedDistance).sum(), responsePath.getDistance(), 2, "unexpected distance");
        // We check the number of points to make sure we found the expected route.
        // There are real world instances where A-B-C is identical to A-C (in meter precision).
        assertEquals(query.getPoints().stream().mapToInt(a -> a.expectedPoints).sum(), responsePath.getPoints().size(), 1, "unexpected point list size");
    }

    private List<Function<Query, GHRequest>> createRequestFactories() {
        // here we setup different kinds of requests to calculate routes with different algorithms
        return Arrays.asList(
                // flex
                q -> createRequest(q).putHint("expected_algo", "dijkstra-routing")
                        .putHint("ch.disable", true).putHint("lm.disable", true).setAlgorithm(DIJKSTRA),
                q -> createRequest(q).putHint("expected_algo", "astar|beeline-routing")
                        .putHint("ch.disable", true).putHint("lm.disable", true).setAlgorithm(ASTAR),
                q -> createRequest(q).putHint("expected_algo", "dijkstrabi-routing")
                        .putHint("ch.disable", true).putHint("lm.disable", true).setAlgorithm(DIJKSTRA_BI),
                q -> createRequest(q).putHint("expected_algo", "astarbi|beeline-routing")
                        .putHint("ch.disable", true).putHint("lm.disable", true).setAlgorithm(ASTAR_BI)
                        .putHint(ASTAR_BI + ".approximation", "BeelineSimplification"),
                // LM
                q -> createRequest(q).putHint("expected_algo", "astarbi|landmarks-routing")
                        .putHint("ch.disable", true)
                        .setAlgorithm(ASTAR_BI).putHint(ASTAR_BI + ".approximation", "BeelineSimplification"),
                // CH
                q -> createRequest(q).putHint("expected_algo", "dijkstrabi|ch-routing")
                        .setAlgorithm(DIJKSTRA_BI),
                q -> createRequest(q).putHint("expected_algo", "astarbi|ch-routing")
                        .setAlgorithm(ASTAR_BI)
        );
    }

    private GHRequest createRequest(Query query) {
        GHRequest req = new GHRequest();
        for (ViaPoint assumption : query.points) {
            req.addPoint(new GHPoint(assumption.lat, assumption.lon));
        }
        return req;
    }

}
