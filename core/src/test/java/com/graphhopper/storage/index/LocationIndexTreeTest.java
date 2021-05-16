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
package com.graphhopper.storage.index;

import com.carrotsearch.hppc.IntArrayList;
import com.graphhopper.routing.ev.BooleanEncodedValue;
import com.graphhopper.routing.util.*;
import com.graphhopper.storage.*;
import com.graphhopper.util.*;
import com.graphhopper.util.shapes.BBox;
import com.graphhopper.util.shapes.GHPoint;
import org.junit.jupiter.api.Test;

import java.io.Closeable;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author Peter Karich
 */
public class LocationIndexTreeTest {
    protected final EncodingManager encodingManager = EncodingManager.create("car");

    public static void initSimpleGraph(Graph g, EncodingManager em) {
        //  6 |       4
        //  5 |
        //    |     6
        //  4 |              5
        //  3 |
        //  2 |    1
        //  1 |          3
        //  0 |    2
        // -1 | 0
        // ---|-------------------
        //    |-2 -1 0 1 2 3 4
        //
        NodeAccess na = g.getNodeAccess();
        na.setNode(0, -1, -2);
        na.setNode(1, 2, -1);
        na.setNode(2, 0, 1);
        na.setNode(3, 1, 2);
        na.setNode(4, 6, 1);
        na.setNode(5, 4, 4);
        na.setNode(6, 4.5, -0.5);
        List<EdgeIteratorState> list = Arrays.asList(g.edge(0, 1),
                g.edge(0, 2),
                g.edge(2, 3),
                g.edge(3, 4),
                g.edge(1, 4),
                g.edge(3, 5),
                // make sure 6 is connected
                g.edge(6, 4));
        for (FlagEncoder encoder : em.fetchEdgeEncoders()) {
            double speed = encoder.getMaxSpeed() / 2;
            GHUtility.setSpeed(speed, speed, encoder, list);
        }
    }

    private LocationIndexTree createIndexNoPrepare(Graph g, int resolution) {
        Directory dir = new RAMDirectory();
        LocationIndexTree tmpIDX = new LocationIndexTree(g, dir);
        tmpIDX.setResolution(resolution);
        return tmpIDX;
    }

    //  0------\
    // /|       \
    // |1----3-\|
    // |____/   4
    // 2-------/
    Graph createTestGraph(EncodingManager em) {
        FlagEncoder encoder = em.getEncoder("car");
        Graph graph = new GraphHopperStorage(new RAMDirectory(), em, false).create(100);
        NodeAccess na = graph.getNodeAccess();
        na.setNode(0, 0.5, -0.5);
        na.setNode(1, -0.5, -0.5);
        na.setNode(2, -1, -1);
        na.setNode(3, -0.4, 0.9);
        na.setNode(4, -0.6, 1.6);
        GHUtility.setSpeed(60, true, true, encoder, graph.edge(0, 1));
        GHUtility.setSpeed(60, true, true, encoder, graph.edge(0, 2));
        GHUtility.setSpeed(60, true, true, encoder, graph.edge(0, 4));
        GHUtility.setSpeed(60, true, true, encoder, graph.edge(1, 3));
        GHUtility.setSpeed(60, true, true, encoder, graph.edge(2, 3));
        GHUtility.setSpeed(60, true, true, encoder, graph.edge(2, 4));
        GHUtility.setSpeed(60, true, true, encoder, graph.edge(3, 4));
        return graph;
    }

    @Test
    public void testSnappedPointAndGeometry() {
        Graph graph = createTestGraph(encodingManager);
        LocationIndex index = createIndexNoPrepare(graph, 500000).prepareIndex();
        // query directly the tower node
        Snap res = index.findClosest(-0.4, 0.9, EdgeFilter.ALL_EDGES);
        assertTrue(res.isValid());
        assertEquals(new GHPoint(-0.4, 0.9), res.getSnappedPoint());
        res = index.findClosest(-0.6, 1.6, EdgeFilter.ALL_EDGES);
        assertTrue(res.isValid());
        assertEquals(new GHPoint(-0.6, 1.6), res.getSnappedPoint());

        // query the edge (1,3). The edge (0,4) has 27674 as distance
        res = index.findClosest(-0.2, 0.3, EdgeFilter.ALL_EDGES);
        assertTrue(res.isValid());
        assertEquals(26936, res.getQueryDistance(), 1);
        assertEquals(new GHPoint(-0.441624, 0.317259), res.getSnappedPoint());
    }

    @Test
    public void testBoundingBoxQuery2() {
        Graph graph = createTestGraph2();
        LocationIndexTree index = (LocationIndexTree) createIndexNoPrepare(graph, 500).prepareIndex();
        final HashSet<Integer> edges = new HashSet<>();
        index.query(graph.getBounds(), edges::add);
        // All edges (see testgraph2.jpg)
        assertEquals(edges.size(), graph.getEdges());
    }

    @Test
    public void testBoundingBoxQuery1() {
        Graph graph = createTestGraph2();
        LocationIndexTree index = (LocationIndexTree) createIndexNoPrepare(graph, 500).prepareIndex();
        final IntArrayList edges = new IntArrayList();
        BBox bbox = new BBox(11.57314, 11.57614, 49.94553, 49.94853);
        index.query(bbox, edges::add);
        // Also all edges (see testgraph2.jpg)
        assertEquals(edges.size(), graph.getEdges());
    }

    @Test
    public void testMoreReal() {
        FlagEncoder encoder = new CarFlagEncoder();
        Graph graph = new GraphHopperStorage(new RAMDirectory(), EncodingManager.create(encoder), false).create(100);
        NodeAccess na = graph.getNodeAccess();
        na.setNode(1, 51.2492152, 9.4317166);
        na.setNode(0, 52, 9);
        na.setNode(2, 51.2, 9.4);
        na.setNode(3, 49, 10);

        GHUtility.setSpeed(60, true, true, encoder, graph.edge(1, 0));
        GHUtility.setSpeed(60, true, true, encoder, graph.edge(0, 2));
        GHUtility.setSpeed(60, true, true, encoder, graph.edge(0, 3)).setWayGeometry(Helper.createPointList(51.21, 9.43));
        LocationIndex index = createIndexNoPrepare(graph, 500000).prepareIndex();
        assertEquals(1, findClosestEdge(index, 51.2, 9.4));
    }

    //    -1    0   1 1.5
    // --------------------
    // 1|         --A
    //  |    -0--/   \
    // 0|   / | B-\   \
    //  |  /  1/   3--4
    //  |  |/------/  /
    //-1|  2---------/
    //  |
    private Graph createTestGraphWithWayGeometry() {
        Graph graph = new GraphHopperStorage(new RAMDirectory(), encodingManager, false).create(100);
        FlagEncoder encoder = encodingManager.getEncoder("car");
        NodeAccess na = graph.getNodeAccess();
        na.setNode(0, 0.5, -0.5);
        na.setNode(1, -0.5, -0.5);
        na.setNode(2, -1, -1);
        na.setNode(3, -0.4, 0.9);
        na.setNode(4, -0.6, 1.6);
        GHUtility.setSpeed(60, true, true, encoder, graph.edge(0, 1));
        GHUtility.setSpeed(60, true, true, encoder, graph.edge(0, 2));
        // insert A and B, without this we would get 0 for 0,0
        GHUtility.setSpeed(60, true, true, encoder, graph.edge(0, 4)).setWayGeometry(Helper.createPointList(1, 1));
        GHUtility.setSpeed(60, true, true, encoder, graph.edge(1, 3)).setWayGeometry(Helper.createPointList(0, 0));
        GHUtility.setSpeed(60, true, true, encoder, graph.edge(2, 3));
        GHUtility.setSpeed(60, true, true, encoder, graph.edge(2, 4));
        GHUtility.setSpeed(60, true, true, encoder, graph.edge(3, 4));
        return graph;
    }

    @Test
    public void testWayGeometry() {
        Graph g = createTestGraphWithWayGeometry();
        LocationIndex index = createIndexNoPrepare(g, 500000).prepareIndex();
        assertEquals(3, findClosestEdge(index, 0, 0));
        assertEquals(3, findClosestEdge(index, 0, 0.1));
        assertEquals(3, findClosestEdge(index, 0.1, 0.1));
        assertEquals(1, findClosestNode(index, -0.5, -0.5));
    }

    @Test
    public void testFindingWayGeometry() {
        Graph g = new GraphHopperStorage(new RAMDirectory(), encodingManager, false).create(100);
        FlagEncoder encoder = encodingManager.getEncoder("car");
        NodeAccess na = g.getNodeAccess();
        na.setNode(10, 51.2492152, 9.4317166);
        na.setNode(20, 52, 9);
        na.setNode(30, 51.2, 9.4);
        na.setNode(50, 49, 10);
        GHUtility.setSpeed(60, true, true, encoder, g.edge(20, 50)).setWayGeometry(Helper.createPointList(51.25, 9.43));
        GHUtility.setSpeed(60, true, true, encoder, g.edge(10, 20));
        GHUtility.setSpeed(60, true, true, encoder, g.edge(20, 30));

        LocationIndex index = createIndexNoPrepare(g, 2000).prepareIndex();
        assertEquals(0, findClosestEdge(index, 51.25, 9.43));
    }

    @Test
    public void testEdgeFilter() {
        Graph graph = createTestGraph(encodingManager);
        LocationIndexTree index = (LocationIndexTree) createIndexNoPrepare(graph, 500000).prepareIndex();

        assertEquals(1, index.findClosest(-.6, -.6, EdgeFilter.ALL_EDGES).getClosestNode());
        assertEquals(2, index.findClosest(-.6, -.6, iter -> iter.getBaseNode() == 2 || iter.getAdjNode() == 2).getClosestNode());
    }

    // see testgraph2.jpg
    Graph createTestGraph2() {
        FlagEncoder encoder = encodingManager.getEncoder("car");
        Graph graph = new GraphHopperStorage(new RAMDirectory(), encodingManager, false).create(100);
        NodeAccess na = graph.getNodeAccess();

        na.setNode(0, 49.94653, 11.57114);
        na.setNode(1, 49.94653, 11.57214);
        na.setNode(2, 49.94653, 11.57314);
        na.setNode(3, 49.94653, 11.57414);
        na.setNode(4, 49.94653, 11.57514);
        na.setNode(5, 49.94653, 11.57614);
        na.setNode(6, 49.94653, 11.57714);
        na.setNode(7, 49.94653, 11.57814);

        na.setNode(8, 49.94553, 11.57214);
        na.setNode(9, 49.94553, 11.57314);
        na.setNode(10, 49.94553, 11.57414);
        na.setNode(11, 49.94553, 11.57514);
        na.setNode(12, 49.94553, 11.57614);
        na.setNode(13, 49.94553, 11.57714);

        na.setNode(14, 49.94753, 11.57214);
        na.setNode(15, 49.94753, 11.57314);
        na.setNode(16, 49.94753, 11.57614);
        na.setNode(17, 49.94753, 11.57814);

        na.setNode(18, 49.94853, 11.57114);
        na.setNode(19, 49.94853, 11.57214);
        na.setNode(20, 49.94853, 11.57814);

        na.setNode(21, 49.94953, 11.57214);
        na.setNode(22, 49.94953, 11.57614);

        na.setNode(23, 49.95053, 11.57114);
        na.setNode(24, 49.95053, 11.57214);
        na.setNode(25, 49.95053, 11.57314);
        na.setNode(26, 49.95053, 11.57514);
        na.setNode(27, 49.95053, 11.57614);
        na.setNode(28, 49.95053, 11.57714);
        na.setNode(29, 49.95053, 11.57814);

        na.setNode(30, 49.95153, 11.57214);
        na.setNode(31, 49.95153, 11.57314);
        na.setNode(32, 49.95153, 11.57514);
        na.setNode(33, 49.95153, 11.57614);
        na.setNode(34, 49.95153, 11.57714);

        // to create correct bounds
        // bottom left
        na.setNode(100, 49.941, 11.56614);
        // top right
        na.setNode(101, 49.96053, 11.58814);

        GHUtility.setSpeed(60, 60, encoder,
                graph.edge(0, 1),
                graph.edge(1, 2),
                graph.edge(2, 3),
                graph.edge(3, 4),
                graph.edge(4, 5),
                graph.edge(6, 7),
                graph.edge(2, 8),
                graph.edge(2, 9),
                graph.edge(3, 10),
                graph.edge(4, 11),
                graph.edge(5, 12),
                graph.edge(6, 13),

                graph.edge(1, 14),
                graph.edge(2, 15),
                graph.edge(5, 16),
                graph.edge(14, 15),
                graph.edge(16, 17),
                graph.edge(16, 20),
                graph.edge(16, 25),

                graph.edge(18, 14),
                graph.edge(18, 19),
                graph.edge(18, 21),
                graph.edge(19, 21),
                graph.edge(21, 24),
                graph.edge(23, 24),
                graph.edge(24, 25),
                graph.edge(26, 27),
                graph.edge(27, 28),
                graph.edge(28, 29),

                graph.edge(24, 30),
                graph.edge(24, 31),
                graph.edge(26, 32),
                graph.edge(26, 22),
                graph.edge(27, 33),
                graph.edge(28, 34));
        return graph;
    }

    @Test
    public void testRMin() {
        Graph graph = createTestGraph(encodingManager);
        LocationIndexTree index = (LocationIndexTree) createIndexNoPrepare(graph, 50000).prepareIndex();
        DistanceCalc distCalc = new DistancePlaneProjection();
        double rmin2 = index.calculateRMin(0.05, -0.3, 1);
        double check2 = distCalc.calcDist(0.05, Math.abs(graph.getNodeAccess().getLat(0)), -0.3, -0.3);
        assertTrue((rmin2 - check2) < 0.0001);
    }

    @Test
    public void testSearchWithFilter_issue318() {
        CarFlagEncoder carEncoder = new CarFlagEncoder();
        BikeFlagEncoder bikeEncoder = new BikeFlagEncoder();

        EncodingManager tmpEM = EncodingManager.create(carEncoder, bikeEncoder);
        Graph graph = new GraphHopperStorage(new RAMDirectory(), tmpEM, false).create(100);
        NodeAccess na = graph.getNodeAccess();

        // distance from point to point is roughly 1 km
        int MAX = 5;
        for (int latIdx = 0; latIdx < MAX; latIdx++) {
            for (int lonIdx = 0; lonIdx < MAX; lonIdx++) {
                int index = lonIdx * 10 + latIdx;
                na.setNode(index, 0.01 * latIdx, 0.01 * lonIdx);
                if (latIdx < MAX - 1)
                    GHUtility.setSpeed(60, true, true, carEncoder, graph.edge(index, index + 1));

                if (lonIdx < MAX - 1)
                    GHUtility.setSpeed(60, true, true, carEncoder, graph.edge(index, index + 10));
            }
        }

        // reduce access for bike to two edges only
        AllEdgesIterator iter = graph.getAllEdges();
        BooleanEncodedValue accessEnc = bikeEncoder.getAccessEnc();
        while (iter.next()) {
            iter.set(accessEnc, false, false);
        }
        for (EdgeIteratorState edge : Arrays.asList(GHUtility.getEdge(graph, 0, 1), GHUtility.getEdge(graph, 1, 2))) {
            edge.set(accessEnc, true, true);
        }

        LocationIndexTree index = createIndexNoPrepare(graph, 500);
        index.prepareIndex();
        index.setMaxRegionSearch(8);

        EdgeFilter carFilter = AccessFilter.allEdges(carEncoder.getAccessEnc());
        Snap snap = index.findClosest(0.03, 0.03, carFilter);
        assertTrue(snap.isValid());
        assertEquals(33, snap.getClosestNode());

        EdgeFilter bikeFilter = AccessFilter.allEdges(bikeEncoder.getAccessEnc());
        snap = index.findClosest(0.03, 0.03, bikeFilter);
        assertTrue(snap.isValid());
        assertEquals(2, snap.getClosestNode());
    }

    // 0--1--2--3, the "cross boundary" edges are 1-2 and 5-6
    // |  |  |  |
    // 4--5--6--7
    @Test
    public void testCrossBoundaryNetwork_issue667() {
        FlagEncoder encoder = encodingManager.getEncoder("car");
        Graph graph = new GraphHopperStorage(new RAMDirectory(), encodingManager, false).create(100);
        NodeAccess na = graph.getNodeAccess();
        na.setNode(0, 0.1, 179.5);
        na.setNode(1, 0.1, 179.9);
        na.setNode(2, 0.1, -179.8);
        na.setNode(3, 0.1, -179.5);
        na.setNode(4, 0, 179.5);
        na.setNode(5, 0, 179.9);
        na.setNode(6, 0, -179.8);
        na.setNode(7, 0, -179.5);

        // just use 1 as distance which is incorrect but does not matter in this unit case
        GHUtility.setSpeed(60, 60, encoder,
                graph.edge(0, 1),
                graph.edge(0, 4),
                graph.edge(1, 5),
                graph.edge(4, 5),

                graph.edge(2, 3),
                graph.edge(2, 6),
                graph.edge(3, 7),
                graph.edge(6, 7));

        // as last edges: create cross boundary edges
        // See #667 where the recommendation is to adjust the import and introduce two pillar nodes 
        // where the connection is cross boundary and would be okay if ignored as real length is 0
        GHUtility.setSpeed(60, true, true, encoder, graph.edge(1, 2)).setWayGeometry(Helper.createPointList(0, 180, 0, -180));
        // but this unit test succeeds even without this adjusted import:
        GHUtility.setSpeed(60, true, true, encoder, graph.edge(5, 6));

        LocationIndexTree index = createIndexNoPrepare(graph, 500);
        index.prepareIndex();

        assertTrue(graph.getNodes() > 0);
        for (int i = 0; i < graph.getNodes(); i++) {
            Snap snap = index.findClosest(na.getLat(i), na.getLon(i), EdgeFilter.ALL_EDGES);
            assertEquals(i, snap.getClosestNode());
        }
    }

    private int findClosestNode(LocationIndex index, double lat, double lon) {
        Snap closest = index.findClosest(lat, lon, EdgeFilter.ALL_EDGES);
        assert closest.getSnappedPosition() == Snap.Position.TOWER;
        return closest.getClosestNode();
    }

    private int findClosestEdge(LocationIndex index, double lat, double lon) {
        return index.findClosest(lat, lon, EdgeFilter.ALL_EDGES).getClosestEdge().getEdge();
    }

    @Test
    public void testSimpleGraph() {
        EncodingManager em = EncodingManager.create("car");
        Graph g = new GraphHopperStorage(new RAMDirectory(), em, false).create(100);
        initSimpleGraph(g, em);

        LocationIndexTree idx = (LocationIndexTree) createIndexNoPrepare(g, 500000).prepareIndex();
        assertEquals(3, findClosestEdge(idx, 5, 2));
        assertEquals(3, findClosestEdge(idx, 1.5, 2));
        assertEquals(1, findClosestEdge(idx, -1, -1));
        assertEquals(4, findClosestEdge(idx, 4, 0));
        Helper.close((Closeable) g);
    }

    @Test
    public void testSimpleGraph2() {
        EncodingManager em = EncodingManager.create("car");
        Graph g = new GraphHopperStorage(new RAMDirectory(), em, false).create(100);
        initSimpleGraph(g, em);

        LocationIndexTree idx = (LocationIndexTree) createIndexNoPrepare(g, 500000).prepareIndex();
        assertEquals(3, findClosestEdge(idx, 5, 2));
        assertEquals(3, findClosestEdge(idx, 1.5, 2));
        assertEquals(1, findClosestEdge(idx, -1, -1));
        assertEquals(6, findClosestNode(idx, 4.5, -0.5));
        assertEquals(3, findClosestEdge(idx, 4, 1));
        assertEquals(4, findClosestEdge(idx, 4, 0));
        assertEquals(6, findClosestNode(idx, 4, -2));
        assertEquals(5, findClosestEdge(idx, 3, 3));
        Helper.close((Closeable) g);
    }

    @Test
    public void testSinglePoints120() {
        Graph g = createSampleGraph(EncodingManager.create("car"));
        LocationIndexTree idx = (LocationIndexTree) createIndexNoPrepare(g, 500000).prepareIndex();

        assertEquals(3, findClosestEdge(idx, 1.637, 2.23));
        assertEquals(10, findClosestEdge(idx, 3.649, 1.375));
        assertEquals(9, findClosestNode(idx, 3.3, 2.2));
        assertEquals(6, findClosestNode(idx, 3.0, 1.5));

        assertEquals(15, findClosestEdge(idx, 3.8, 0));
        assertEquals(15, findClosestEdge(idx, 3.8466, 0.021));
        Helper.close((Closeable) g);
    }

    @Test
    public void testSinglePoints32() {
        Graph g = createSampleGraph(EncodingManager.create("car"));
        LocationIndexTree idx = (LocationIndexTree) createIndexNoPrepare(g, 500000).prepareIndex();

        assertEquals(10, findClosestEdge(idx, 3.649, 1.375));
        assertEquals(15, findClosestEdge(idx, 3.8465748, 0.021762699));
        assertEquals(4, findClosestEdge(idx, 2.485, 1.373));
        assertEquals(0, findClosestEdge(idx, 0.64628404, 0.53006625));
        Helper.close((Closeable) g);
    }

    @Test
    public void testNoErrorOnEdgeCase_lastIndex() {
        final EncodingManager encodingManager = EncodingManager.create("car");
        int locs = 10000;
        Graph g = new GraphHopperStorage(new RAMDirectory(), encodingManager, false).create(100);
        NodeAccess na = g.getNodeAccess();
        Random rand = new Random(12);
        for (int i = 0; i < locs; i++) {
            na.setNode(i, (float) rand.nextDouble() * 10 + 10, (float) rand.nextDouble() * 10 + 10);
        }
        createIndexNoPrepare(g, 200).prepareIndex();
        Helper.close((Closeable) g);
    }

    public Graph createSampleGraph(EncodingManager encodingManager) {
        Graph graph = new GraphHopperStorage(new RAMDirectory(), encodingManager, false).create(100);
        // length does not matter here but lat,lon and outgoing edges do!

//
//   lat             /--------\
//    5   o-        p--------\ q
//          \  /-----\-----n | |
//    4       k    /--l--    m/
//           / \  j      \   |
//    3     |   g  \  h---i  /
//          |       \    /  /
//    2     e---------f--  /
//                   /  \-d
//    1        /--b--      \
//            |    \--------c
//    0       a
//
//   lon: 0   1   2   3   4   5
        int a0 = 0;
        NodeAccess na = graph.getNodeAccess();
        na.setNode(0, 0, 1.0001f);
        int b1 = 1;
        na.setNode(1, 1, 2);
        int c2 = 2;
        na.setNode(2, 0.5f, 4.5f);
        int d3 = 3;
        na.setNode(3, 1.5f, 3.8f);
        int e4 = 4;
        na.setNode(4, 2.01f, 0.5f);
        int f5 = 5;
        na.setNode(5, 2, 3);
        int g6 = 6;
        na.setNode(6, 3, 1.5f);
        int h7 = 7;
        na.setNode(7, 2.99f, 3.01f);
        int i8 = 8;
        na.setNode(8, 3, 4);
        int j9 = 9;
        na.setNode(9, 3.3f, 2.2f);
        int k10 = 10;
        na.setNode(10, 4, 1);
        int l11 = 11;
        na.setNode(11, 4.1f, 3);
        int m12 = 12;
        na.setNode(12, 4, 4.5f);
        int n13 = 13;
        na.setNode(13, 4.5f, 4.1f);
        int o14 = 14;
        na.setNode(14, 5, 0);
        int p15 = 15;
        na.setNode(15, 4.9f, 2.5f);
        int q16 = 16;
        na.setNode(16, 5, 5);
        // => 17 locations

        FlagEncoder encoder = encodingManager.getEncoder("car");
        GHUtility.setSpeed(60, true, true, encoder, graph.edge(a0, b1));
        GHUtility.setSpeed(60, true, true, encoder, graph.edge(c2, b1));
        GHUtility.setSpeed(60, true, true, encoder, graph.edge(c2, d3));
        GHUtility.setSpeed(60, true, true, encoder, graph.edge(f5, b1));
        GHUtility.setSpeed(60, true, true, encoder, graph.edge(e4, f5));
        GHUtility.setSpeed(60, true, true, encoder, graph.edge(m12, d3));
        GHUtility.setSpeed(60, true, true, encoder, graph.edge(e4, k10));
        GHUtility.setSpeed(60, true, true, encoder, graph.edge(f5, d3));
        GHUtility.setSpeed(60, true, true, encoder, graph.edge(f5, i8));
        GHUtility.setSpeed(60, true, true, encoder, graph.edge(f5, j9));
        GHUtility.setSpeed(60, true, true, encoder, graph.edge(k10, g6));
        GHUtility.setSpeed(60, true, true, encoder, graph.edge(j9, l11));
        GHUtility.setSpeed(60, true, true, encoder, graph.edge(i8, l11));
        GHUtility.setSpeed(60, true, true, encoder, graph.edge(i8, h7));
        GHUtility.setSpeed(60, true, true, encoder, graph.edge(k10, n13));
        GHUtility.setSpeed(60, true, true, encoder, graph.edge(k10, o14));
        GHUtility.setSpeed(60, true, true, encoder, graph.edge(l11, p15));
        GHUtility.setSpeed(60, true, true, encoder, graph.edge(m12, p15));
        GHUtility.setSpeed(60, true, true, encoder, graph.edge(q16, p15));
        GHUtility.setSpeed(60, true, true, encoder, graph.edge(q16, m12));
        return graph;
    }

    @Test
    public void testDifferentVehicles() {
        final EncodingManager encodingManager = EncodingManager.create("car,foot");
        Graph g = new GraphHopperStorage(new RAMDirectory(), encodingManager, false).create(100);
        initSimpleGraph(g, encodingManager);
        LocationIndexTree idx = (LocationIndexTree) createIndexNoPrepare(g, 500000).prepareIndex();
        assertEquals(0, findClosestEdge(idx, 1, -1));

        // now make all edges from node 1 accessible for CAR only
        EdgeIterator iter = g.createEdgeExplorer().setBaseNode(1);
        FlagEncoder encoder = encodingManager.getEncoder("foot");
        BooleanEncodedValue accessEnc = encoder.getAccessEnc();
        while (iter.next()) {
            iter.set(accessEnc, false, false);
        }

        idx = (LocationIndexTree) createIndexNoPrepare(g, 500000).prepareIndex();
        FootFlagEncoder footEncoder = (FootFlagEncoder) encodingManager.getEncoder("foot");
        assertEquals(2, idx.findClosest(1, -1, AccessFilter.allEdges(footEncoder.getAccessEnc())).getClosestNode());
        Helper.close((Closeable) g);
    }
}
