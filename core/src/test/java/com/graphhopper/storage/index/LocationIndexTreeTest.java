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

import com.graphhopper.routing.ev.BooleanEncodedValue;
import com.graphhopper.routing.util.*;
import com.graphhopper.storage.*;
import com.graphhopper.util.*;
import com.graphhopper.util.shapes.BBox;
import com.graphhopper.util.shapes.GHPoint;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.Closeable;
import java.io.File;
import java.util.*;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * @author Peter Karich
 */
public class LocationIndexTreeTest {
    protected final EncodingManager encodingManager = EncodingManager.create("car");
    String location = "./target/tmp/";
    LocationIndex idx;

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
        List<EdgeIteratorState> list = Arrays.asList(g.edge(0, 1).setDistance(3.5),
                g.edge(0, 2).setDistance(2.5),
                g.edge(2, 3).setDistance(1),
                g.edge(3, 4).setDistance(3.2),
                g.edge(1, 4).setDistance(2.4),
                g.edge(3, 5).setDistance(1.5),
                // make sure 6 is connected
                g.edge(6, 4).setDistance(1.2));
        for (FlagEncoder encoder : em.fetchEdgeEncoders()) {
            double speed = encoder.getMaxSpeed() / 2;
            GHUtility.setSpeed(speed, speed, encoder, list);
        }
    }

    public LocationIndexTree createIndex(Graph g, int resolution) {
        if (resolution < 0)
            resolution = 500000;
        return (LocationIndexTree) createIndexNoPrepare(g, resolution).prepareIndex();
    }

    private LocationIndexTree createIndexNoPrepare(Graph g, int resolution) {
        Directory dir = new RAMDirectory(location);
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
        Graph graph = createGHStorage(new RAMDirectory(), em, false);
        NodeAccess na = graph.getNodeAccess();
        na.setNode(0, 0.5, -0.5);
        na.setNode(1, -0.5, -0.5);
        na.setNode(2, -1, -1);
        na.setNode(3, -0.4, 0.9);
        na.setNode(4, -0.6, 1.6);
        GHUtility.setSpeed(60, true, true, encoder, graph.edge(0, 1).setDistance(1));
        GHUtility.setSpeed(60, true, true, encoder, graph.edge(0, 2).setDistance(1));
        GHUtility.setSpeed(60, true, true, encoder, graph.edge(0, 4).setDistance(1));
        GHUtility.setSpeed(60, true, true, encoder, graph.edge(1, 3).setDistance(1));
        GHUtility.setSpeed(60, true, true, encoder, graph.edge(2, 3).setDistance(1));
        GHUtility.setSpeed(60, true, true, encoder, graph.edge(2, 4).setDistance(1));
        GHUtility.setSpeed(60, true, true, encoder, graph.edge(3, 4).setDistance(1));
        return graph;
    }

    @Test
    public void testSnappedPointAndGeometry() {
        Graph graph = createTestGraph(encodingManager);
        LocationIndex index = createIndex(graph, -1);
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
        LocationIndexTree index = createIndex(graph, 500);
        final HashSet<Integer> edges = new HashSet<>();
        index.query(graph.getBounds(), new LocationIndex.Visitor() {
            @Override
            public void onEdge(int edgeId) {
                edges.add(edgeId);
            }
        });
        // All edges (see testgraph2.jpg)
        assertEquals(edges.size(), graph.getEdges());
    }

    @Test
    public void testBoundingBoxQuery1() {
        Graph graph = createTestGraph2();
        LocationIndexTree index = createIndex(graph, 500);
        final ArrayList edges = new ArrayList();
        BBox bbox = new BBox(11.57314, 11.57614, 49.94553, 49.94853);
        index.query(bbox, new LocationIndex.Visitor() {
            @Override
            public void onEdge(int edgeId) {
                edges.add(edgeId);
            }
        });
        // Also all edges (see testgraph2.jpg)
        assertEquals(edges.size(), graph.getEdges());
    }

    @Test
    public void testMoreReal() {
        FlagEncoder encoder = new CarFlagEncoder();
        Graph graph = createGHStorage(EncodingManager.create(encoder));
        NodeAccess na = graph.getNodeAccess();
        na.setNode(1, 51.2492152, 9.4317166);
        na.setNode(0, 52, 9);
        na.setNode(2, 51.2, 9.4);
        na.setNode(3, 49, 10);

        GHUtility.setSpeed(60, true, true, encoder, graph.edge(1, 0).setDistance(1000));
        GHUtility.setSpeed(60, true, true, encoder, graph.edge(0, 2).setDistance(1000));
        GHUtility.setSpeed(60, true, true, encoder, graph.edge(0, 3).setDistance(1000)).setWayGeometry(Helper.createPointList(51.21, 9.43));
        LocationIndex index = createIndex(graph, -1);
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
        Graph graph = createGHStorage(encodingManager);
        FlagEncoder encoder = encodingManager.getEncoder("car");
        NodeAccess na = graph.getNodeAccess();
        na.setNode(0, 0.5, -0.5);
        na.setNode(1, -0.5, -0.5);
        na.setNode(2, -1, -1);
        na.setNode(3, -0.4, 0.9);
        na.setNode(4, -0.6, 1.6);
        GHUtility.setSpeed(60, true, true, encoder, graph.edge(0, 1).setDistance(1));
        GHUtility.setSpeed(60, true, true, encoder, graph.edge(0, 2).setDistance(1));
        // insert A and B, without this we would get 0 for 0,0
        GHUtility.setSpeed(60, true, true, encoder, graph.edge(0, 4).setDistance(1)).setWayGeometry(Helper.createPointList(1, 1));
        GHUtility.setSpeed(60, true, true, encoder, graph.edge(1, 3).setDistance(1)).setWayGeometry(Helper.createPointList(0, 0));
        GHUtility.setSpeed(60, true, true, encoder, graph.edge(2, 3).setDistance(1));
        GHUtility.setSpeed(60, true, true, encoder, graph.edge(2, 4).setDistance(1));
        GHUtility.setSpeed(60, true, true, encoder, graph.edge(3, 4).setDistance(1));
        return graph;
    }

    @Test
    public void testWayGeometry() {
        Graph g = createTestGraphWithWayGeometry();
        LocationIndex index = createIndex(g, -1);
        assertEquals(3, findClosestEdge(index, 0, 0));
        assertEquals(3, findClosestEdge(index, 0, 0.1));
        assertEquals(3, findClosestEdge(index, 0.1, 0.1));
        assertEquals(1, findClosestNode(index, -0.5, -0.5));
    }

    @Test
    public void testFindingWayGeometry() {
        Graph g = createGHStorage(encodingManager);
        FlagEncoder encoder = encodingManager.getEncoder("car");
        NodeAccess na = g.getNodeAccess();
        na.setNode(10, 51.2492152, 9.4317166);
        na.setNode(20, 52, 9);
        na.setNode(30, 51.2, 9.4);
        na.setNode(50, 49, 10);
        GHUtility.setSpeed(60, true, true, encoder, g.edge(20, 50).setDistance(1)).setWayGeometry(Helper.createPointList(51.25, 9.43));
        GHUtility.setSpeed(60, true, true, encoder, g.edge(10, 20).setDistance(1));
        GHUtility.setSpeed(60, true, true, encoder, g.edge(20, 30).setDistance(1));

        LocationIndex index = createIndex(g, 2000);
        assertEquals(0, findClosestEdge(index, 51.25, 9.43));
    }

    @Test
    public void testEdgeFilter() {
        Graph graph = createTestGraph(encodingManager);
        LocationIndexTree index = createIndex(graph, -1);

        assertEquals(1, index.findClosest(-.6, -.6, EdgeFilter.ALL_EDGES).getClosestNode());
        assertEquals(2, index.findClosest(-.6, -.6, new EdgeFilter() {
            @Override
            public boolean accept(EdgeIteratorState iter) {
                return iter.getBaseNode() == 2 || iter.getAdjNode() == 2;
            }
        }).getClosestNode());
    }

    // see testgraph2.jpg
    Graph createTestGraph2() {
        FlagEncoder encoder = encodingManager.getEncoder("car");
        Graph graph = createGHStorage(new RAMDirectory(), encodingManager, false);
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
                graph.edge(0, 1).setDistance(10),
                graph.edge(1, 2).setDistance(10),
                graph.edge(2, 3).setDistance(10),
                graph.edge(3, 4).setDistance(10),
                graph.edge(4, 5).setDistance(10),
                graph.edge(6, 7).setDistance(10),
                graph.edge(2, 8).setDistance(10),
                graph.edge(2, 9).setDistance(10),
                graph.edge(3, 10).setDistance(10),
                graph.edge(4, 11).setDistance(10),
                graph.edge(5, 12).setDistance(10),
                graph.edge(6, 13).setDistance(10),

                graph.edge(1, 14).setDistance(10),
                graph.edge(2, 15).setDistance(10),
                graph.edge(5, 16).setDistance(10),
                graph.edge(14, 15).setDistance(10),
                graph.edge(16, 17).setDistance(10),
                graph.edge(16, 20).setDistance(10),
                graph.edge(16, 25).setDistance(10),

                graph.edge(18, 14).setDistance(10),
                graph.edge(18, 19).setDistance(10),
                graph.edge(18, 21).setDistance(10),
                graph.edge(19, 21).setDistance(10),
                graph.edge(21, 24).setDistance(10),
                graph.edge(23, 24).setDistance(10),
                graph.edge(24, 25).setDistance(10),
                graph.edge(26, 27).setDistance(10),
                graph.edge(27, 28).setDistance(10),
                graph.edge(28, 29).setDistance(10),

                graph.edge(24, 30).setDistance(10),
                graph.edge(24, 31).setDistance(10),
                graph.edge(26, 32).setDistance(10),
                graph.edge(26, 22).setDistance(10),
                graph.edge(27, 33).setDistance(10),
                graph.edge(28, 34).setDistance(10));
        return graph;
    }

    @Test
    public void testRMin() {
        Graph graph = createTestGraph(encodingManager);
        LocationIndexTree index = createIndex(graph, 50000);

        //query: 0.05 | -0.3
        DistanceCalc distCalc = new DistancePlaneProjection();

        double rmin = index.calculateRMin(0.05, -0.3, 0);
        double check = distCalc.calcDist(0.05, Math.abs(graph.getNodeAccess().getLon(2)) - index.getDeltaLon(), -0.3, -0.3);

        assertTrue((rmin - check) < 0.0001);

        double rmin2 = index.calculateRMin(0.05, -0.3, 1);
        double check2 = distCalc.calcDist(0.05, Math.abs(graph.getNodeAccess().getLat(0)), -0.3, -0.3);

        assertTrue((rmin2 - check2) < 0.0001);

        /*GraphVisualizer gv = new GraphVisualizer(graph, index.getDeltaLat(), index.getDeltaLon(), index.getCenter(0, 0).lat, index.getCenter(0, 0).lon);
         try {
         Thread.sleep(4000);
         } catch(InterruptedException ie) {}*/
    }

    @Test
    public void testSearchWithFilter_issue318() {
        CarFlagEncoder carEncoder = new CarFlagEncoder();
        BikeFlagEncoder bikeEncoder = new BikeFlagEncoder();

        EncodingManager tmpEM = EncodingManager.create(carEncoder, bikeEncoder);
        Graph graph = createGHStorage(new RAMDirectory(), tmpEM, false);
        NodeAccess na = graph.getNodeAccess();

        // distance from point to point is roughly 1 km
        int MAX = 5;
        for (int latIdx = 0; latIdx < MAX; latIdx++) {
            for (int lonIdx = 0; lonIdx < MAX; lonIdx++) {
                int index = lonIdx * 10 + latIdx;
                na.setNode(index, 0.01 * latIdx, 0.01 * lonIdx);
                if (latIdx < MAX - 1)
                    GHUtility.setSpeed(60, true, true, carEncoder, graph.edge(index, index + 1).setDistance(1000));

                if (lonIdx < MAX - 1)
                    GHUtility.setSpeed(60, true, true, carEncoder, graph.edge(index, index + 10).setDistance(1000));
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

        EdgeFilter carFilter = DefaultEdgeFilter.allEdges(carEncoder);
        Snap snap = index.findClosest(0.03, 0.03, carFilter);
        assertTrue(snap.isValid());
        assertEquals(33, snap.getClosestNode());

        EdgeFilter bikeFilter = DefaultEdgeFilter.allEdges(bikeEncoder);
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
        Graph graph = createGHStorage(new RAMDirectory(), encodingManager, false);
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
                graph.edge(0, 1).setDistance(1),
                graph.edge(0, 4).setDistance(1),
                graph.edge(1, 5).setDistance(1),
                graph.edge(4, 5).setDistance(1),

                graph.edge(2, 3).setDistance(1),
                graph.edge(2, 6).setDistance(1),
                graph.edge(3, 7).setDistance(1),
                graph.edge(6, 7).setDistance(1));

        // as last edges: create cross boundary edges
        // See #667 where the recommendation is to adjust the import and introduce two pillar nodes 
        // where the connection is cross boundary and would be okay if ignored as real length is 0
        GHUtility.setSpeed(60, true, true, encoder, graph.edge(1, 2).setDistance(1)).setWayGeometry(Helper.createPointList(0, 180, 0, -180));
        // but this unit test succeeds even without this adjusted import:
        GHUtility.setSpeed(60, true, true, encoder, graph.edge(5, 6).setDistance(1));

        LocationIndexTree index = createIndexNoPrepare(graph, 500);
        index.prepareIndex();

        assertTrue(graph.getNodes() > 0);
        for (int i = 0; i < graph.getNodes(); i++) {
            Snap snap = index.findClosest(na.getLat(i), na.getLon(i), EdgeFilter.ALL_EDGES);
            assertEquals(i, snap.getClosestNode());
        }
    }

    GraphHopperStorage createGHStorage(EncodingManager encodingManager) {
        return LocationIndexTreeTest.this.createGHStorage(new RAMDirectory(), encodingManager, false);
    }

    GraphHopperStorage createGHStorage(Directory dir, EncodingManager encodingManager, boolean is3D) {
        return new GraphHopperStorage(dir, encodingManager, is3D).create(100);
    }

    private int findClosestNode(LocationIndex index, double lat, double lon) {
        Snap closest = index.findClosest(lat, lon, EdgeFilter.ALL_EDGES);
        assert closest.getSnappedPosition() == Snap.Position.TOWER;
        return closest.getClosestNode();
    }

    private int findClosestEdge(LocationIndex index, double lat, double lon) {
        return index.findClosest(lat, lon, EdgeFilter.ALL_EDGES).getClosestEdge().getEdge();
    }

    @Before
    public void setUp() {
        Helper.removeDir(new File(location));
    }

    @After
    public void tearDown() {
        if (idx != null)
            idx.close();
        Helper.removeDir(new File(location));
    }

    @Test
    public void testSimpleGraph() {
        EncodingManager em = EncodingManager.create("car");
        Graph g = this.createGHStorage(em);
        initSimpleGraph(g, em);

        idx = createIndex(g, -1);
        assertEquals(3, findClosestEdge(idx, 5, 2));
        assertEquals(3, findClosestEdge(idx, 1.5, 2));
        assertEquals(1, findClosestEdge(idx, -1, -1));
        assertEquals(4, findClosestEdge(idx, 4, 0));
        Helper.close((Closeable) g);
    }

    @Test
    public void testSimpleGraph2() {
        EncodingManager em = EncodingManager.create("car");
        Graph g = this.createGHStorage(em);
        initSimpleGraph(g, em);

        idx = createIndex(g, -1);
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
        idx = createIndex(g, -1);

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
        idx = createIndex(g, -1);

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
        Graph g = LocationIndexTreeTest.this.createGHStorage(new MMapDirectory(location), encodingManager, false);
        NodeAccess na = g.getNodeAccess();
        Random rand = new Random(12);
        for (int i = 0; i < locs; i++) {
            na.setNode(i, (float) rand.nextDouble() * 10 + 10, (float) rand.nextDouble() * 10 + 10);
        }
        idx = createIndex(g, 200);
        Helper.close((Closeable) g);
    }

    public Graph createSampleGraph(EncodingManager encodingManager) {
        Graph graph = LocationIndexTreeTest.this.createGHStorage(encodingManager);
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
        GHUtility.setSpeed(60, true, true, encoder, graph.edge(a0, b1).setDistance(1));
        GHUtility.setSpeed(60, true, true, encoder, graph.edge(c2, b1).setDistance(1));
        GHUtility.setSpeed(60, true, true, encoder, graph.edge(c2, d3).setDistance(1));
        GHUtility.setSpeed(60, true, true, encoder, graph.edge(f5, b1).setDistance(1));
        GHUtility.setSpeed(60, true, true, encoder, graph.edge(e4, f5).setDistance(1));
        GHUtility.setSpeed(60, true, true, encoder, graph.edge(m12, d3).setDistance(1));
        GHUtility.setSpeed(60, true, true, encoder, graph.edge(e4, k10).setDistance(1));
        GHUtility.setSpeed(60, true, true, encoder, graph.edge(f5, d3).setDistance(1));
        GHUtility.setSpeed(60, true, true, encoder, graph.edge(f5, i8).setDistance(1));
        GHUtility.setSpeed(60, true, true, encoder, graph.edge(f5, j9).setDistance(1));
        GHUtility.setSpeed(60, true, true, encoder, graph.edge(k10, g6).setDistance(1));
        GHUtility.setSpeed(60, true, true, encoder, graph.edge(j9, l11).setDistance(1));
        GHUtility.setSpeed(60, true, true, encoder, graph.edge(i8, l11).setDistance(1));
        GHUtility.setSpeed(60, true, true, encoder, graph.edge(i8, h7).setDistance(1));
        GHUtility.setSpeed(60, true, true, encoder, graph.edge(k10, n13).setDistance(1));
        GHUtility.setSpeed(60, true, true, encoder, graph.edge(k10, o14).setDistance(1));
        GHUtility.setSpeed(60, true, true, encoder, graph.edge(l11, p15).setDistance(1));
        GHUtility.setSpeed(60, true, true, encoder, graph.edge(m12, p15).setDistance(1));
        GHUtility.setSpeed(60, true, true, encoder, graph.edge(q16, p15).setDistance(1));
        GHUtility.setSpeed(60, true, true, encoder, graph.edge(q16, m12).setDistance(1));
        return graph;
    }

    @Test
    public void testDifferentVehicles() {
        final EncodingManager encodingManager = EncodingManager.create("car,foot");
        Graph g = LocationIndexTreeTest.this.createGHStorage(encodingManager);
        initSimpleGraph(g, encodingManager);
        idx = createIndex(g, -1);
        assertEquals(0, findClosestEdge(idx, 1, -1));

        // now make all edges from node 1 accessible for CAR only
        EdgeIterator iter = g.createEdgeExplorer().setBaseNode(1);
        FlagEncoder encoder = encodingManager.getEncoder("foot");
        BooleanEncodedValue accessEnc = encoder.getAccessEnc();
        while (iter.next()) {
            iter.set(accessEnc, false, false);
        }
        idx.close();

        idx = createIndex(g, -1);
        FootFlagEncoder footEncoder = (FootFlagEncoder) encodingManager.getEncoder("foot");
        assertEquals(2, idx.findClosest(1, -1, DefaultEdgeFilter.allEdges(footEncoder)).getClosestNode());
        Helper.close((Closeable) g);
    }
}
