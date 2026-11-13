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
package com.graphhopper.reader.dem;

import com.graphhopper.routing.ev.EnumEncodedValue;
import com.graphhopper.routing.ev.RoadClass;
import com.graphhopper.routing.ev.RoadEnvironment;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.storage.BaseGraph;
import com.graphhopper.storage.NodeAccess;
import com.graphhopper.util.DistanceCalcEarth;
import com.graphhopper.util.EdgeIteratorState;
import com.graphhopper.util.FetchMode;
import com.graphhopper.util.PointList;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

public class TowerNodeElevationSmoothingTest {
    // Dresden Hertha-Lindner-Str.: a straight road along a parallel with tower nodes every 12 m and a 0.9 m dip at
    // node 4, continued by longer edges on both sides so that the window is complete
    private static final double[] ELE = {109.8, 109.9, 110, 110, 109.64, 110.54, 110.86, 111.2, 111.5};
    private static final double[] LON = {12.99914, 12.99957, 13.0, 13.000171, 13.000342, 13.000513, 13.000684, 13.00114, 13.0016};
    private final EnumEncodedValue<RoadClass> rc = RoadClass.create();
    private final EnumEncodedValue<RoadEnvironment> env = RoadEnvironment.create();
    private final EncodingManager em = new EncodingManager.Builder().add(rc).add(env).build();

    private BaseGraph createGraph() {
        return new BaseGraph.Builder(em).set3D(true).create();
    }

    /**
     * @param connect false to set the nodes of the road only
     */
    private BaseGraph createDresdenRoad(boolean connect) {
        BaseGraph graph = createGraph();
        for (int i = 0; i < ELE.length; i++)
            graph.getNodeAccess().setNode(i, 51.0, LON[i], ELE[i]);
        for (int i = 1; connect && i < ELE.length; i++)
            graph.edge(i - 1, i).setDistance(12);
        return graph;
    }

    private void smooth(BaseGraph graph) {
        new TowerNodeElevationSmoothing(graph, rc, env, 30, 2).execute();
    }

    @Test
    public void smoothAlongRoadOnly() {
        BaseGraph graph = createDresdenRoad(true);
        NodeAccess na = graph.getNodeAccess();
        // steep driveway into a sunken courtyard at node 5, perpendicular to the road: must not influence the road
        na.setNode(9, 51.00018, LON[5], 104);
        graph.edge(5, 9).setDistance(20);
        // a dead end road with a pillar node
        na.setNode(10, 50.9996, LON[3], 112);
        graph.edge(3, 10).setDistance(45).setWayGeometry(new PointList(1, true) {{
            add(50.99995, LON[3], 111.5);
        }});
        // a copy (like for a via-way turn restriction) shares the geometry: it must be shifted only once
        EdgeIteratorState copy = graph.copyEdge(9, true);

        smooth(graph);

        // the dip is gone: the step between the neighbors is small now
        assertTrue(Math.abs(na.getEle(5) - na.getEle(4)) < 0.3, na.getEle(4) + " -> " + na.getEle(5));
        // the overall climb along the road is kept
        assertEquals(1.7, na.getEle(8) - na.getEle(0), 0.3);
        // the distance includes the new elevation (it was set to 12 m above)
        EdgeIteratorState edge45 = graph.getEdgeIteratorState(4, 5);
        assertEquals(DistanceCalcEarth.DIST_EARTH.calcDistance(edge45.fetchWayGeometry(FetchMode.ALL)), edge45.getDistance(), 1e-3);
        assertNotEquals(12, edge45.getDistance(), 1e-3);
        // the driveway and the dead end keep their elevation (no straight continuation at their ends)
        assertEquals(104, na.getEle(9), 1e-3);
        assertEquals(112, na.getEle(10), 1e-3);
        // the pillar of the dead end (5.6m away from node 3) was shifted with node 3: the edge is tilted, i.e. the
        // shift decreases linearly to the other tower node
        double shift3 = na.getEle(3) - 110;
        assertTrue(Math.abs(shift3) > 0.05, "node 3 should be shifted: " + shift3);
        PointList pillars = graph.getEdgeIteratorState(9, Integer.MIN_VALUE).fetchWayGeometry(FetchMode.PILLAR_ONLY);
        assertEquals(1, pillars.size());
        double walked = DistanceCalcEarth.DIST_EARTH.calcDist(51.0, LON[3], 50.99995, LON[3]);
        double length = DistanceCalcEarth.DIST_EARTH.calcDist(51.0, LON[3], 50.9996, LON[3]);
        // tolerance: pillar elevations are stored with a precision of 5cm
        assertEquals(shift3 * (1 - walked / length), pillars.getEle(0) - 111.5, 0.03);
        assertEquals(pillars, copy.fetchWayGeometry(FetchMode.PILLAR_ONLY));
        assertEquals(graph.getEdgeIteratorState(9, Integer.MIN_VALUE).getDistance(), copy.getDistance(), 1e-6);
    }

    @Test
    public void stepsEndTheRoad() {
        BaseGraph graph = createGraph();
        NodeAccess na = graph.getNodeAccess();
        // Dresden Neustädter Markt at the Augustusbrücke: a footway crosses the road (even a bit straighter than the
        // road) and goes down steps on both sides. The road must win, the steps must not pull the junction down.
        double lat = 51, lon = 13, mLat = 1 / 111_320.0, mLon = 1 / 70_057.0;
        na.setNode(0, lat, lon, 112.85);
        // road to the west: 3 m, 40 m, then a bridge
        na.setNode(1, lat, lon - 3 * mLon, 112.53);
        na.setNode(2, lat, lon - 43 * mLon, 112.6);
        na.setNode(3, lat, lon - 163 * mLon, 113.6);
        // road to the east (slightly bent, so the footway is straighter)
        na.setNode(4, lat + 0.01 * mLat, lon + 24 * mLon, 112.6);
        na.setNode(5, lat + 0.02 * mLat, lon + 60 * mLon, 112.7);
        // footway to the north and to the south, each with steps down
        na.setNode(6, lat + 8 * mLat, lon, 112.8);
        na.setNode(7, lat + 24 * mLat, lon, 109.9);
        na.setNode(8, lat - 7 * mLat, lon, 111.9);
        na.setNode(9, lat - 22 * mLat, lon, 109.4);
        road(graph, 0, 1, RoadClass.UNCLASSIFIED);
        road(graph, 1, 2, RoadClass.UNCLASSIFIED);
        road(graph, 2, 3, RoadClass.UNCLASSIFIED).set(env, RoadEnvironment.BRIDGE);
        road(graph, 0, 4, RoadClass.UNCLASSIFIED);
        road(graph, 4, 5, RoadClass.UNCLASSIFIED);
        road(graph, 0, 6, RoadClass.FOOTWAY);
        road(graph, 6, 7, RoadClass.STEPS);
        road(graph, 0, 8, RoadClass.FOOTWAY);
        road(graph, 8, 9, RoadClass.STEPS);

        smooth(graph);

        // smoothed along the road: about 112.6 instead of 110.9 along the footway and steps
        assertEquals(112.6, na.getEle(0), 0.15);
        // the tower nodes of the bridge keep their (interpolated) elevation
        assertEquals(112.6, na.getEle(2), 1e-3);
        assertEquals(113.6, na.getEle(3), 1e-3);
    }

    @Test
    public void changeIsLimited() {
        BaseGraph graph = createGraph();
        NodeAccess na = graph.getNodeAccess();
        // straight road with tower nodes every 12 m and a 10 m spike at node 4: the mean would lower it by about 9 m
        for (int i = 0; i < 9; i++)
            na.setNode(i, 51.0, 13 + i * 12 / 70_057.0, i == 4 ? 110 : 100);
        for (int i = 1; i < 9; i++)
            graph.edge(i - 1, i).setDistance(12);

        smooth(graph);

        assertEquals(110 - TowerNodeElevationSmoothing.MAX_CHANGE, na.getEle(4), 0.01);
    }

    @Test
    public void parallelIsDeterministic() {
        BaseGraph original = grid(), single = grid(), parallel = grid();
        new TowerNodeElevationSmoothing(single, rc, env, 30, 1).execute();
        TowerNodeElevationSmoothing smoothing = new TowerNodeElevationSmoothing(parallel, rc, env, 30, 4);
        smoothing.chunkSize = 7;
        smoothing.execute();

        boolean changed = false;
        for (int node = 0; node < single.getNodes(); node++) {
            assertEquals(single.getNodeAccess().getEle(node), parallel.getNodeAccess().getEle(node), 1e-6, "node " + node);
            changed |= single.getNodeAccess().getEle(node) != original.getNodeAccess().getEle(node);
        }
        assertTrue(changed);
        for (int edge = 0; edge < single.getEdges(); edge++) {
            PointList a = single.getEdgeIteratorState(edge, Integer.MIN_VALUE).fetchWayGeometry(FetchMode.PILLAR_ONLY);
            PointList b = parallel.getEdgeIteratorState(edge, Integer.MIN_VALUE).fetchWayGeometry(FetchMode.PILLAR_ONLY);
            assertEquals(a, b, "edge " + edge);
        }
    }

    /**
     * A 20x20 grid of roads 12 m apart with noisy elevations on a slope, every third edge with a pillar node.
     */
    private BaseGraph grid() {
        BaseGraph graph = createGraph();
        NodeAccess na = graph.getNodeAccess();
        Random rand = new Random(42);
        int n = 20;
        double mLat = 1 / 111_320.0, mLon = 1 / 70_057.0;
        for (int y = 0; y < n; y++)
            for (int x = 0; x < n; x++)
                na.setNode(y * n + x, 51 + 12 * y * mLat, 13 + 12 * x * mLon, 100 + 0.5 * x + rand.nextGaussian() * 0.5);
        for (int y = 0; y < n; y++)
            for (int x = 0; x < n; x++) {
                int node = y * n + x;
                if (x + 1 < n) connect(graph, node, node + 1);
                if (y + 1 < n) connect(graph, node, node + n);
            }
        return graph;
    }

    private static void connect(BaseGraph graph, int from, int to) {
        NodeAccess na = graph.getNodeAccess();
        EdgeIteratorState edge = graph.edge(from, to).setDistance(12);
        if (edge.getEdge() % 3 == 0) {
            PointList pillar = new PointList(1, true);
            pillar.add((na.getLat(from) + na.getLat(to)) / 2, (na.getLon(from) + na.getLon(to)) / 2,
                    (na.getEle(from) + na.getEle(to)) / 2 + 0.3);
            edge.setWayGeometry(pillar);
        }
    }

    private EdgeIteratorState road(BaseGraph graph, int from, int to, RoadClass roadClass) {
        NodeAccess na = graph.getNodeAccess();
        return graph.edge(from, to).set(rc, roadClass).setDistance(DistanceCalcEarth.DIST_EARTH.calcDist(
                na.getLat(from), na.getLon(from), na.getLat(to), na.getLon(to)));
    }

    @Test
    public void shortEdgeNextToKeptNode() {
        BaseGraph graph = createDresdenRoad(true);
        NodeAccess na = graph.getNodeAccess();
        // 30 m flat dead end at the dip node 4 with a pillar in the middle. Its end node 9 is not smoothed
        double mLat = 1 / 111_320.0;
        na.setNode(9, 51.0 - 30 * mLat, LON[4], 109.64);
        graph.edge(4, 9).setDistance(30).setWayGeometry(new PointList(1, true) {{
            add(51.0 - 15 * mLat, LON[4], 109.64);
        }});
        smooth(graph);
        double shift4 = na.getEle(4) - 109.64;
        double pillar = graph.getEdgeIteratorState(8, Integer.MIN_VALUE).fetchWayGeometry(FetchMode.PILLAR_ONLY).getEle(0) - 109.64;
        // the dead end keeps its elevation and the edge was flat, so node 4 can only move until the edge has the allowed
        // slope (unlimited it moves 0.5 m). For max_slope this is reduced for the rounding of the pillar: the slope at
        // the end node is measured over 15 + 15 / 2 m
        assertEquals(109.64, na.getEle(9), 1e-3);
        double allowed = TowerNodeElevationSmoothing.MAX_STEEPER - TowerNodeElevationSmoothing.PILLAR_ROUNDING / 22.5;
        assertEquals(allowed * 30, shift4, 0.003);
        // the edge is tilted: the pillar in the middle gets half of the change (stored with a precision of 5 cm)
        assertEquals(shift4 / 2, pillar, 0.026);
    }

    @Test
    public void barrierIsLookedThrough() {
        BaseGraph graph = createDresdenRoad(false);
        NodeAccess na = graph.getNodeAccess();
        // a barrier at the dip node 4 like OSMReader creates it: a copy of the node and a zero-length edge
        na.setNode(9, 51.0, LON[4], ELE[4]);
        for (int i = 1; i < ELE.length; i++)
            graph.edge(i == 5 ? 9 : i - 1, i).setDistance(12);
        graph.edge(4, 9).setDistance(0.001);
        smooth(graph);
        // both barrier nodes are smoothed like without the barrier and have the same elevation
        assertTrue(na.getEle(4) - 109.64 > 0.3, "" + na.getEle(4));
        assertEquals(na.getEle(4), na.getEle(9), 1e-6);
        // the neighbors see the road through the barrier as well
        assertTrue(Math.abs(na.getEle(4) - na.getEle(3)) < 0.3, na.getEle(3) + " -> " + na.getEle(4));
    }

    @Test
    public void radiusMustNotBeSmallerThanStep() {
        assertThrows(IllegalArgumentException.class, () -> new TowerNodeElevationSmoothing(createGraph(), rc, env, 5, 1));
    }

    @Test
    public void crossingOfSteepPathDoesNotMakeTheRoadSteeper() {
        BaseGraph graph = createGraph();
        NodeAccess na = graph.getNodeAccess();
        // Zurich 47.404233,8.562065: a footway goes steeply down the hill (convex profile) and crosses a road along the
        // hill at node 0. The footway is a bit straighter, so node 0 is smoothed along it and its mean is 1.2 m lower,
        // while node 7 on the road is smoothed along the road
        double lat = 47, lon = 8, mLat = 1 / 111_320.0, mLon = 1 / 75_920.0;
        na.setNode(0, lat, lon, 450.33);
        double[] north = {450.96, 451.06, 452.12}, south = {447.8, 446.24, 445.51};
        for (int i = 0; i < 3; i++) {
            na.setNode(1 + i, lat + 10 * (i + 1) * mLat, lon, north[i]);
            na.setNode(4 + i, lat - 10 * (i + 1) * mLat, lon, south[i]);
        }
        // the footway edges first: for the same heading change the first pair wins
        graph.edge(0, 1).setDistance(10);
        graph.edge(0, 4).setDistance(10);
        for (int i = 0; i < 2; i++) {
            graph.edge(1 + i, 2 + i).setDistance(10);
            graph.edge(4 + i, 5 + i).setDistance(10);
        }
        na.setNode(7, lat, lon + 7.7 * mLon, 450.71);
        na.setNode(8, lat, lon + 77.7 * mLon, 450.97);
        na.setNode(9, lat, lon - 50 * mLon, 448.99);
        graph.edge(0, 7).setDistance(7.7);
        graph.edge(7, 8).setDistance(70);
        graph.edge(0, 9).setDistance(50);
        smooth(graph);
        // the 7.7 m road edge had 0.38 m (5 %) and must not get steeper than allowed (without the limit it gets 1.3 m)
        assertTrue(Math.abs(na.getEle(7) - na.getEle(0)) <= 0.38 + TowerNodeElevationSmoothing.MAX_STEEPER * 7.7 + 1e-3, na.getEle(0) + " " + na.getEle(7));
    }

    @Test
    public void noEdgeGetsSteeperThanAllowed() {
        BaseGraph original = grid(), smoothed = grid();
        smooth(smoothed);
        double sumBefore = 0, sumAfter = 0;
        for (int edge = 0; edge < original.getEdges(); edge++) {
            double[] before = slopes(original.getEdgeIteratorState(edge, Integer.MIN_VALUE).fetchWayGeometry(FetchMode.ALL));
            double[] after = slopes(smoothed.getEdgeIteratorState(edge, Integer.MIN_VALUE).fetchWayGeometry(FetchMode.ALL));
            // average_slope depends on the tower nodes only, which are stored with a precision of 1 mm
            assertTrue(after[0] <= before[0] + TowerNodeElevationSmoothing.MAX_STEEPER + 1e-3, "average_slope of edge " + edge + ": " + before[0] + " -> " + after[0]);
            // max_slope: less than one step of the stored value, even with the rounding of the pillar (5 cm)
            assertTrue(after[1] < before[1] + 0.01, "max_slope of edge " + edge + ": " + before[1] + " -> " + after[1]);
            sumBefore += before[1];
            sumAfter += after[1];
        }
        // but the noise is smoothed
        assertTrue(sumAfter < 0.8 * sumBefore, sumBefore + " -> " + sumAfter);
    }

    @Test
    public void pillarNodesAreSmoothedAcrossTowerNodes() {
        // flat straight road: tower nodes every 60 m, pillar nodes every 12 m. The pillar node 12 m behind the tower
        // node 2 is 0.6 m too high and 50 m before it there is a real step of 1 m in the road (a ramp of 8 %)
        BaseGraph graph = createGraph();
        NodeAccess na = graph.getNodeAccess();
        double mLon = 1 / 70_057.0;
        for (int i = 0; i < 5; i++) {
            na.setNode(i, 51.0, 13 + i * 60 * mLon, i < 2 ? 99 : 100);
            if (i == 0) continue;
            PointList pillars = new PointList(4, true);
            for (int k = 1; k < 5; k++)
                pillars.add(51.0, 13 + ((i - 1) * 60 + k * 12) * mLon, i == 3 && k == 1 ? 100.6 : i < 2 || i == 2 && k < 2 ? 99 : 100);
            graph.edge(i - 1, i).setDistance(60).setWayGeometry(pillars);
        }
        double[] before = slopes(graph.getEdgeIteratorState(1, Integer.MIN_VALUE).fetchWayGeometry(FetchMode.ALL));

        TowerNodeElevationSmoothing smoothing = new TowerNodeElevationSmoothing(graph, rc, env, 30, 2);
        smoothing.smoothPillars = true;
        smoothing.execute();

        // the bump is smoothed although it is next to a tower node: a mean spreads it to a hump of 0.3 m, which
        // includes the tower node and the pillar nodes around
        PointList edge2 = graph.getEdgeIteratorState(2, Integer.MIN_VALUE).fetchWayGeometry(FetchMode.ALL);
        assertEquals(100.3, edge2.getEle(1), 0.03);
        assertEquals(100.24, na.getEle(2), 0.03);
        assertEquals(100.25, edge2.getEle(2), 0.03);
        // the ramp is smoothed too, but the edge is not steeper than before
        double[] after = slopes(graph.getEdgeIteratorState(1, Integer.MIN_VALUE).fetchWayGeometry(FetchMode.ALL));
        assertTrue(after[1] <= before[1] + 1e-3, before[1] + " -> " + after[1]);
        assertEquals(before[0], after[0], TowerNodeElevationSmoothing.MAX_STEEPER);
    }

    /**
     * @return |average_slope| and |max_slope| like SlopeCalculator
     */
    private static double[] slopes(PointList pl) {
        double length = DistanceCalcEarth.calcDistance(pl, false), prevDist = 0;
        double avg = Math.abs(pl.getEle(pl.size() - 1) - pl.getEle(0)) / length, max = avg;
        for (int i = 1; i < pl.size(); i++) {
            double dist = DistanceCalcEarth.DIST_EARTH.calcDist(pl.getLat(i - 1), pl.getLon(i - 1), pl.getLat(i), pl.getLon(i));
            if (i > 1 && prevDist > 2)
                max = Math.max(max, Math.abs(pl.getEle(i) - (pl.getEle(i - 1) + pl.getEle(i - 2)) / 2) / (dist + prevDist / 2));
            prevDist = dist;
        }
        return new double[]{avg, max};
    }

    @Test
    public void steepSideEdgeDoesNotHoldBackTheRoad() {
        // Dresden Hertha-Lindner-Str.: the node 4 of the road is 0.9 m too low because of the courtyard next to it,
        // which is reached by a short ramp at this node. Lifting the node makes the ramp steeper.
        for (double courtyard : new double[]{107.84, 109.04}) {
            BaseGraph graph = createDresdenRoad(true);
            NodeAccess na = graph.getNodeAccess();
            for (int edge = 0; edge < graph.getEdges(); edge++)
                graph.getEdgeIteratorState(edge, Integer.MIN_VALUE).set(rc, RoadClass.TERTIARY);
            na.setNode(9, 51.0 - 6 / 111_320.0, LON[4], courtyard);
            road(graph, 4, 9, RoadClass.SERVICE);
            smooth(graph);
            double step = Math.abs(na.getEle(5) - na.getEle(4));
            if (courtyard < 108)
                // a ramp of 30 %: the node follows the road and the dip is gone (before 0.9 m on 12 m)
                assertTrue(step < 0.3, na.getEle(4) + " -> " + na.getEle(5));
            else
                // a ramp of 10 % must not get steeper than allowed, so the dip stays
                assertEquals(109.64 + TowerNodeElevationSmoothing.MAX_STEEPER * 6, na.getEle(4), 0.003);
            assertEquals(courtyard, na.getEle(9), 1e-3);
        }
    }
}
