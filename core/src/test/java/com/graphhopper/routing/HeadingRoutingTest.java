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
import com.graphhopper.ResponsePath;
import com.graphhopper.config.Profile;
import com.graphhopper.routing.ev.Subnetwork;
import com.graphhopper.routing.util.CarFlagEncoder;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.routing.util.FlagEncoder;
import com.graphhopper.storage.BaseGraph;
import com.graphhopper.storage.Graph;
import com.graphhopper.storage.NodeAccess;
import com.graphhopper.storage.RAMDirectory;
import com.graphhopper.storage.index.LocationIndexTree;
import com.graphhopper.util.EdgeIteratorState;
import com.graphhopper.util.GHUtility;
import com.graphhopper.util.Parameters;
import com.graphhopper.util.TranslationMap;
import com.graphhopper.util.details.PathDetail;
import com.graphhopper.util.details.PathDetailsBuilderFactory;
import com.graphhopper.util.shapes.GHPoint;
import org.junit.jupiter.api.Test;

import java.util.*;

import static com.graphhopper.util.Parameters.Algorithms.DIJKSTRA;
import static org.junit.jupiter.api.Assertions.*;

/**
 * These tests were taken from GraphHopperAPITest. Really, they are low-level routing algorithm tests, but currently
 * heading is still implemented in Router. When heading is removed from QueryGraph with #1765 these tests should
 * be pushed further 'down' into EdgeBasedRoutingAlgorithmTest. We already have some high-level heading tests in
 * GraphHopperTest.
 */
class HeadingRoutingTest {

    @Test
    public void headingTest1() {
        // Test enforce start direction
        CarFlagEncoder carEncoder = new CarFlagEncoder();
        EncodingManager encodingManager = new EncodingManager.Builder().add(carEncoder).add(Subnetwork.create("profile")).build();
        BaseGraph graph = createSquareGraph(encodingManager);
        Router router = createRouter(graph, encodingManager);

        // Start in middle of edge 4-5
        GHPoint start = new GHPoint(0.0015, 0.002);
        // End at middle of edge 2-3
        GHPoint end = new GHPoint(0.002, 0.0005);

        GHRequest req = new GHRequest().
                setPoints(Arrays.asList(start, end)).
                setHeadings(Arrays.asList(180., Double.NaN)).
                setProfile("profile").
                setPathDetails(Collections.singletonList("edge_key"));
        GHResponse response = router.route(req);
        assertFalse(response.hasErrors(), response.getErrors().toString());
        assertArrayEquals(new int[]{4, 5, 8, 3, 2}, calcNodes(graph, response.getAll().get(0)));
    }

    @Test
    public void headingTest2() {
        // Test enforce south start direction and east end direction
        CarFlagEncoder carEncoder = new CarFlagEncoder();
        EncodingManager encodingManager = new EncodingManager.Builder().add(carEncoder).add(Subnetwork.create("profile")).build();
        BaseGraph graph = createSquareGraph(encodingManager);
        Router router = createRouter(graph, encodingManager);

        // Start in middle of edge 4-5
        GHPoint start = new GHPoint(0.0015, 0.002);
        // End at middle of edge 2-3
        GHPoint end = new GHPoint(0.002, 0.0005);

        GHRequest req = new GHRequest(start, end).
                setHeadings(Arrays.asList(180.0, 90.0)).
                setProfile("profile").
                setPathDetails(Collections.singletonList("edge_key"));
        GHResponse response = router.route(req);
        assertFalse(response.hasErrors());
        assertArrayEquals(new int[]{4, 5, 8, 1, 2, 3}, calcNodes(graph, response.getAll().get(0)));

        // Test uni-directional case
        req.setAlgorithm(DIJKSTRA);
        response = router.route(req);
        assertFalse(response.hasErrors(), response.getErrors().toString());
        assertArrayEquals(new int[]{4, 5, 8, 1, 2, 3}, calcNodes(graph, response.getAll().get(0)));
    }

    @Test
    public void headingTest3() {
        CarFlagEncoder carEncoder = new CarFlagEncoder();
        EncodingManager encodingManager = new EncodingManager.Builder().add(carEncoder).add(Subnetwork.create("profile")).build();
        BaseGraph graph = createSquareGraph(encodingManager);
        Router router = createRouter(graph, encodingManager);

        // Start in middle of edge 4-5
        GHPoint start = new GHPoint(0.0015, 0.002);
        // Via Point between 8-7
        GHPoint via = new GHPoint(0.0005, 0.001);
        // End at middle of edge 2-3
        GHPoint end = new GHPoint(0.002, 0.0005);

        GHRequest req = new GHRequest().
                setPoints(Arrays.asList(start, via, end)).
                setHeadings(Arrays.asList(Double.NaN, 0., Double.NaN)).
                setProfile("profile").
                setPathDetails(Collections.singletonList("edge_key"));
        GHResponse response = router.route(req);
        assertFalse(response.hasErrors());
        assertArrayEquals(new int[]{4, 5, 6, 7, 8, 3, 2}, calcNodes(graph, response.getAll().get(0)));
    }

    @Test
    public void headingTest4() {
        // Test straight via routing
        CarFlagEncoder carEncoder = new CarFlagEncoder();
        EncodingManager encodingManager = new EncodingManager.Builder().add(carEncoder).add(Subnetwork.create("profile")).build();
        BaseGraph graph = createSquareGraph(encodingManager);
        Router router = createRouter(graph, encodingManager);

        // Start in middle of edge 4-5
        GHPoint start = new GHPoint(0.0015, 0.002);
        // End at middle of edge 2-3
        GHPoint end = new GHPoint(0.002, 0.0005);
        // Via Point between 8-3
        GHPoint via = new GHPoint(0.0015, 0.001);
        GHRequest req = new GHRequest().
                setPoints(Arrays.asList(start, via, end)).
                setProfile("profile").
                setPathDetails(Collections.singletonList("edge_key"));
        req.putHint(Parameters.Routing.PASS_THROUGH, true);
        GHResponse response = router.route(req);
        assertFalse(response.hasErrors());
        assertEquals(1, response.getAll().size());
        assertArrayEquals(new int[]{5, 4, 3, 8, 1, 2, 3}, calcNodes(graph, response.getAll().get(0)));
    }

    @Test
    public void headingTest5() {
        // Test independence of previous enforcement for subsequent paths
        CarFlagEncoder carEncoder = new CarFlagEncoder();
        EncodingManager encodingManager = new EncodingManager.Builder().add(carEncoder).add(Subnetwork.create("profile")).build();
        BaseGraph graph = createSquareGraph(encodingManager);
        Router router = createRouter(graph, encodingManager);

        // Start in middle of edge 4-5
        GHPoint start = new GHPoint(0.0015, 0.002);
        // End at middle of edge 2-3
        GHPoint end = new GHPoint(0.002, 0.0005);
        // First go south and than come from west to via-point at 7-6. Then go back over previously punished (11)-4 edge
        GHPoint via = new GHPoint(0.000, 0.0015);
        GHRequest req = new GHRequest().
                setPoints(Arrays.asList(start, via, end)).
                setHeadings(Arrays.asList(0., 90., Double.NaN)).
                setProfile("profile").
                setPathDetails(Collections.singletonList("edge_key"));
        req.putHint(Parameters.Routing.PASS_THROUGH, true);
        GHResponse response = router.route(req);
        assertFalse(response.hasErrors());
        assertArrayEquals(new int[]{5, 4, 3, 8, 7, 6, 5, 4, 3, 2}, calcNodes(graph, response.getAll().get(0)));
    }

    @Test
    public void testHeadingWithSnapFilter() {
        CarFlagEncoder carEncoder = new CarFlagEncoder();
        EncodingManager encodingManager = new EncodingManager.Builder().add(carEncoder).add(Subnetwork.create("profile")).build();
        BaseGraph graph = createSquareGraphWithTunnel(encodingManager);
        Router router = createRouter(graph, encodingManager);
        // Start at 8 (slightly north to make it independent on some edge ordering and always use 8-3 or 3-8 as fallback)
        GHPoint start = new GHPoint(0.0011, 0.001);
        // End at middle of edge 2-3
        GHPoint end = new GHPoint(0.002, 0.0005);

        // no heading
        GHRequest req = new GHRequest().
                setPoints(Arrays.asList(start, end)).
                setProfile("profile").
                setPathDetails(Collections.singletonList("edge_key"));
        req.putHint(Parameters.Routing.PASS_THROUGH, true);
        GHResponse response = router.route(req);
        assertFalse(response.hasErrors());
        assertArrayEquals(new int[]{8, 3, 2}, calcNodes(graph, response.getAll().get(0)));

        // same start + end but heading=0, parallel to 3-8-7
        req = new GHRequest().
                setPoints(Arrays.asList(start, end)).
                setHeadings(Arrays.asList(0.)).
                setProfile("profile").
                setPathDetails(Collections.singletonList("edge_key"));
        req.putHint(Parameters.Routing.PASS_THROUGH, true);
        response = router.route(req);
        assertFalse(response.hasErrors());
        assertArrayEquals(new int[]{8, 3, 2}, calcNodes(graph, response.getAll().get(0)));

        // heading=90 parallel to 1->5
        req = new GHRequest().
                setPoints(Arrays.asList(start, end)).
                setHeadings(Arrays.asList(90., Double.NaN)).
                setProfile("profile").
                setPathDetails(Collections.singletonList("edge_key"));
        req.putHint(Parameters.Routing.PASS_THROUGH, true);
        response = router.route(req);
        assertFalse(response.hasErrors());
        assertArrayEquals(new int[]{1, 5, 4, 3, 2}, calcNodes(graph, response.getAll().get(0)));

        for (double angle = 0; angle < 360; angle += 10) {
            // Ignore angles nearly parallel to 1->5. I.e. it should fallback to results with 8-3.. or 3-8..
            if (angle >= 60 && angle <= 120) continue;

            req = new GHRequest().
                    setPoints(Arrays.asList(start, end)).
                    setHeadings(Arrays.asList(angle, Double.NaN)).
                    setProfile("profile").
                    setPathDetails(Collections.singletonList("edge_key"));
            req.putHint(Parameters.Routing.PASS_THROUGH, true);
            response = router.route(req);
            assertFalse(response.hasErrors());

            int[] expectedNodes = (angle >= 130 && angle <= 250) ? new int[]{3, 8, 7, 0, 1, 2, 3} : new int[]{8, 3, 2};
            // System.out.println(Arrays.toString(calcNodes(graph, response.getAll().get(0))) + " angle:" + angle);
            assertArrayEquals(expectedNodes, calcNodes(graph, response.getAll().get(0)), "angle: " + angle);
        }
    }

    @Test
    public void testHeadingWithSnapFilter2() {
        CarFlagEncoder carEncoder = new CarFlagEncoder();
        EncodingManager encodingManager = new EncodingManager.Builder().add(carEncoder).add(Subnetwork.create("profile")).build();
        BaseGraph graph = createSquareGraphWithTunnel(encodingManager);
        Router router = createRouter(graph, encodingManager);
        // Start at 8 (slightly east to snap to edge 1->5 per default)
        GHPoint start = new GHPoint(0.001, 0.0011);
        // End at middle of edge 2-3
        GHPoint end = new GHPoint(0.002, 0.0005);

        GHRequest req = new GHRequest().
                setPoints(Arrays.asList(start, end)).
                setProfile("profile").
                setHeadings(Arrays.asList(0.)).
                setPathDetails(Collections.singletonList("edge_key"));
        req.putHint(Parameters.Routing.PASS_THROUGH, true);
        GHResponse response = router.route(req);
        assertFalse(response.hasErrors());
        assertArrayEquals(new int[]{8, 3, 2}, calcNodes(graph, response.getAll().get(0)));

        req = new GHRequest().
                setPoints(Arrays.asList(start, end)).
                setProfile("profile").
                setHeadings(Arrays.asList(180.)).
                setPathDetails(Collections.singletonList("edge_key"));
        req.putHint(Parameters.Routing.PASS_THROUGH, true);
        response = router.route(req);
        assertFalse(response.hasErrors());
        assertArrayEquals(new int[]{8, 3, 2}, calcNodes(graph, response.getAll().get(0)));
    }

    @Test
    public void headingTest6() {
        // Test if snaps at tower nodes are ignored
        CarFlagEncoder carEncoder = new CarFlagEncoder();
        EncodingManager encodingManager = new EncodingManager.Builder().add(carEncoder).add(Subnetwork.create("profile")).build();
        BaseGraph graph = createSquareGraph(encodingManager);
        Router router = createRouter(graph, encodingManager);

        // QueryPoints directly on TowerNodes
        GHPoint start = new GHPoint(0, 0);
        GHPoint via = new GHPoint(0.002, 0.000);
        GHPoint end = new GHPoint(0.002, 0.002);

        GHRequest req = new GHRequest().
                setPoints(Arrays.asList(start, via, end)).
                setHeadings(Arrays.asList(90., 270., 270.)).
                setProfile("profile").
                setPathDetails(Collections.singletonList("edge_key"));
        GHResponse response = router.route(req);
        assertFalse(response.hasErrors());
        assertArrayEquals(new int[]{0, 1, 2, 3, 4}, calcNodes(graph, response.getAll().get(0)));
    }

    private Router createRouter(BaseGraph graph, EncodingManager encodingManager) {
        LocationIndexTree locationIndex = new LocationIndexTree(graph, new RAMDirectory());
        locationIndex.prepareIndex();
        Map<String, Profile> profilesByName = new HashMap<>();
        profilesByName.put("profile", new Profile("profile").setVehicle("car").setWeighting("fastest"));
        return new Router(graph.getBaseGraph(), encodingManager, locationIndex, profilesByName, new PathDetailsBuilderFactory(), new TranslationMap().doImport(), new RouterConfig(),
                new DefaultWeightingFactory(graph.getBaseGraph(), encodingManager), Collections.emptyMap(), Collections.emptyMap());
    }

    private BaseGraph createSquareGraph(EncodingManager encodingManager) {
        BaseGraph g = new BaseGraph.Builder(encodingManager).create();
        FlagEncoder carEncoder = encodingManager.getEncoder("car");

        // 2---3---4
        // |   |   |
        // 1---8---5
        // |   |   |
        // 0---7---6
        NodeAccess na = g.getNodeAccess();
        na.setNode(0, 0.000, 0.000);
        na.setNode(1, 0.001, 0.000);
        na.setNode(2, 0.002, 0.000);
        na.setNode(3, 0.002, 0.001);
        na.setNode(4, 0.002, 0.002);
        na.setNode(5, 0.001, 0.002);
        na.setNode(6, 0.000, 0.002);
        na.setNode(7, 0.000, 0.001);
        na.setNode(8, 0.001, 0.001);

        GHUtility.setSpeed(60, true, true, carEncoder, g.edge(0, 1).setDistance(100));
        GHUtility.setSpeed(60, true, true, carEncoder, g.edge(1, 2).setDistance(100));
        GHUtility.setSpeed(60, true, true, carEncoder, g.edge(2, 3).setDistance(100));
        GHUtility.setSpeed(60, true, true, carEncoder, g.edge(3, 4).setDistance(100));
        GHUtility.setSpeed(60, true, true, carEncoder, g.edge(4, 5).setDistance(100));
        GHUtility.setSpeed(60, true, true, carEncoder, g.edge(5, 6).setDistance(100));
        GHUtility.setSpeed(60, true, true, carEncoder, g.edge(6, 7).setDistance(100));
        GHUtility.setSpeed(60, true, true, carEncoder, g.edge(7, 0).setDistance(100));

        GHUtility.setSpeed(60, true, true, carEncoder, g.edge(1, 8).setDistance(110));
        GHUtility.setSpeed(60, true, true, carEncoder, g.edge(3, 8).setDistance(110));
        GHUtility.setSpeed(60, true, true, carEncoder, g.edge(5, 8).setDistance(110));
        GHUtility.setSpeed(60, true, true, carEncoder, g.edge(7, 8).setDistance(110));

        return g;
    }

    private BaseGraph createSquareGraphWithTunnel(EncodingManager encodingManager) {
        BaseGraph g = new BaseGraph.Builder(encodingManager).create();
        FlagEncoder carEncoder = encodingManager.getEncoder("car");

        // 2----3---4
        // |    |   |
        // 1->- 8 >-5 (edge 1->5 is not connected to 8)
        // |    |   |
        // 0----7---6
        NodeAccess na = g.getNodeAccess();
        na.setNode(0, 0.000, 0.000);
        na.setNode(1, 0.001, 0.000);
        na.setNode(2, 0.002, 0.000);
        na.setNode(3, 0.002, 0.001);
        na.setNode(4, 0.002, 0.002);
        na.setNode(5, 0.001, 0.002);
        na.setNode(6, 0.000, 0.002);
        na.setNode(7, 0.000, 0.001);
        na.setNode(8, 0.001, 0.001);

        GHUtility.setSpeed(60, true, true, carEncoder, g.edge(0, 1).setDistance(100));
        GHUtility.setSpeed(60, true, true, carEncoder, g.edge(1, 2).setDistance(100));
        GHUtility.setSpeed(60, true, true, carEncoder, g.edge(2, 3).setDistance(100));
        GHUtility.setSpeed(60, true, true, carEncoder, g.edge(3, 4).setDistance(100));
        GHUtility.setSpeed(60, true, true, carEncoder, g.edge(4, 5).setDistance(100));
        GHUtility.setSpeed(60, true, true, carEncoder, g.edge(5, 6).setDistance(100));
        GHUtility.setSpeed(60, true, true, carEncoder, g.edge(6, 7).setDistance(100));
        GHUtility.setSpeed(60, true, true, carEncoder, g.edge(7, 0).setDistance(100));

        GHUtility.setSpeed(60, true, false, carEncoder, g.edge(1, 5).setDistance(110));
        GHUtility.setSpeed(60, true, true, carEncoder, g.edge(3, 8).setDistance(110));
        GHUtility.setSpeed(60, true, true, carEncoder, g.edge(7, 8).setDistance(110));

        return g;
    }

    private int[] calcNodes(Graph graph, ResponsePath responsePath) {
        List<PathDetail> edgeKeys = responsePath.getPathDetails().get("edge_key");
        int[] result = new int[edgeKeys.size() + 1];
        for (int i = 0; i < edgeKeys.size(); i++) {
            int edgeKey = (int) edgeKeys.get(i).getValue();
            int edgeId = edgeKey / 2;
            EdgeIteratorState edgeIteratorState = graph.getEdgeIteratorState(edgeId, Integer.MIN_VALUE);
            result[i] = edgeKey % 2 == 0 ? edgeIteratorState.getBaseNode() : edgeIteratorState.getAdjNode();
            if (i == edgeKeys.size() - 1)
                result[edgeKeys.size()] = edgeKey % 2 == 0 ? edgeIteratorState.getAdjNode() : edgeIteratorState.getBaseNode();
        }
        return result;
    }
}