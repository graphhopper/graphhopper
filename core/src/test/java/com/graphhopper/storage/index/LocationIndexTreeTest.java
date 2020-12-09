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
import com.graphhopper.coll.GHIntHashSet;
import com.graphhopper.routing.ev.BooleanEncodedValue;
import com.graphhopper.routing.util.*;
import com.graphhopper.storage.Directory;
import com.graphhopper.storage.Graph;
import com.graphhopper.storage.NodeAccess;
import com.graphhopper.storage.RAMDirectory;
import com.graphhopper.util.*;
import com.graphhopper.util.shapes.BBox;
import com.graphhopper.util.shapes.GHPoint;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.*;

/**
 * @author Peter Karich
 */
public class LocationIndexTreeTest extends AbstractLocationIndexTester {
    protected final EncodingManager encodingManager = EncodingManager.create("car");

    @Override
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

    @Override
    public boolean hasEdgeSupport() {
        return true;
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
    public void testQuery() {
        Graph graph = createTestGraph2();
        LocationIndexTree index = createIndex(graph, 500);
        final ArrayList set = new ArrayList();
        index.query(new BBox(11.57314, 11.57614, 49.94553, 49.94853), new LocationIndex.Visitor() {
            @Override
            public void onNode(int nodeId) {
                set.add(nodeId);
            }
        });
        assertEquals(17, set.size());
        assertTrue(set.containsAll(Arrays.asList(2, 3, 4, 5, 6)));
        assertFalse(set.containsAll(Arrays.asList(17, 18, 25, 30)));
    }

    @Test
    public void testInMemIndex() {
        Graph graph = createTestGraph(encodingManager);
        LocationIndexTree index = createIndexNoPrepare(graph, 50000);
        index.prepareAlgo();
        LocationIndexTree.InMemConstructionIndex inMemIndex = index.getPrepareInMemIndex(EdgeFilter.ALL_EDGES);
        assertEquals(IntArrayList.from(new int[]{4, 4}), index.getEntries());

        assertEquals(4, inMemIndex.getEntriesOf(0).size());
        assertEquals(10, inMemIndex.getEntriesOf(1).size());
        assertEquals(0, inMemIndex.getEntriesOf(2).size());
        // [LEAF 0 {} {0, 2}, LEAF 2 {} {0, 1}, LEAF 1 {} {2}, LEAF 3 {} {1}, LEAF 8 {} {0}, LEAF 10 {} {0}, LEAF 9 {} {0}, LEAF 4 {} {2}, LEAF 6 {} {0, 1, 2, 3}, LEAF 5 {} {0, 2, 3}, LEAF 7 {} {1, 2, 3}, LEAF 13 {} {1}]        
        // System.out.println(inMemIndex.getLayer(2));

        index.dataAccess.create(10);
        inMemIndex.store(inMemIndex.root, LocationIndexTree.START_POINTER);
        // [LEAF 0 {2} {},    LEAF 2 {1} {},    LEAF 1 {2} {}, LEAF 3 {1} {}, LEAF 8 {0} {}, LEAF 10 {0} {}, LEAF 9 {0} {}, LEAF 4 {2} {}, LEAF 6 {0, 3} {},       LEAF 5 {0, 2, 3} {}, LEAF 7 {1, 2, 3} {}, LEAF 13 {1} {}]
        // System.out.println(inMemIndex.getLayer(2));

        GHIntHashSet set = new GHIntHashSet();
        set.add(0);

        GHIntHashSet foundIds = new GHIntHashSet();
        index.findNetworkEntries(0.5, -0.5, foundIds, 0);
        assertEquals(set, foundIds);

        set.add(1);
        set.add(2);
        foundIds.clear();
        index.findNetworkEntries(-0.5, -0.9, foundIds, 0);
        index.findNetworkEntries(-0.5, -0.9, foundIds, 1);
        assertEquals(set, foundIds);
        assertEquals(2, findID(index, -0.5, -0.9));

        // The optimization if(dist > normedHalf) => feed nodeA or nodeB
        // although this reduces chance of nodes outside of the tile
        // in practice it even increases file size!?
        // Is this due to the CHGraph disconnect problem?
//        set.clear();
//        set.add(4);
//        assertEquals(set, index.findNetworkEntries(-0.7, 1.5));
//        
//        set.clear();
//        set.add(4);
//        assertEquals(set, index.findNetworkEntries(-0.5, 0.5));
    }

    @Test
    public void testInMemIndex2() {
        Graph graph = createTestGraph2();
        LocationIndexTree index = createIndexNoPrepare(graph, 500);
        index.prepareAlgo();
        LocationIndexTree.InMemConstructionIndex inMemIndex = index.getPrepareInMemIndex(EdgeFilter.ALL_EDGES);
        assertEquals(IntArrayList.from(new int[]{4, 4}), index.getEntries());
        assertEquals(3, inMemIndex.getEntriesOf(0).size());
        assertEquals(5, inMemIndex.getEntriesOf(1).size());
        assertEquals(0, inMemIndex.getEntriesOf(2).size());

        index.dataAccess.create(10);
        inMemIndex.store(inMemIndex.root, LocationIndexTree.START_POINTER);

        // 0
        assertEquals(2L, index.keyAlgo.encode(49.94653, 11.57114));
        // 1
        assertEquals(3L, index.keyAlgo.encode(49.94653, 11.57214));
        // 28
        assertEquals(6L, index.keyAlgo.encode(49.95053, 11.57714));
        // 29
        assertEquals(6L, index.keyAlgo.encode(49.95053, 11.57814));
        // 8
        assertEquals(1L, index.keyAlgo.encode(49.94553, 11.57214));
        // 34
        assertEquals(12L, index.keyAlgo.encode(49.95153, 11.57714));

        // Query near point 25 (49.95053, 11.57314).
        // If we would have a perfect compaction (takes a lot longer) we would
        // get only 0 or any node in the lefter greater subgraph.
        // The other subnetwork is already perfect {26}.
        // For compaction see: https://github.com/graphhopper/graphhopper/blob/5594f7f9d98d932f365557dc37b4b2d3b7abf698/core/src/main/java/com/graphhopper/storage/index/Location2NodesNtree.java#L277
        GHIntHashSet set = new GHIntHashSet();
        set.addAll(28, 27, 26, 24, 23, 21, 19, 18, 16, 14, 6, 5, 4, 3, 2, 1, 0);

        GHIntHashSet foundIds = new GHIntHashSet();
        index.findNetworkEntries(49.950, 11.5732, foundIds, 0);
        assertEquals(set, foundIds);
    }

    @Test
    public void testInMemIndex3() {
        Graph graph = createTestGraph(encodingManager);
        LocationIndexTree index = createIndexNoPrepare(graph, 10000);
        index.prepareAlgo();
        LocationIndexTree.InMemConstructionIndex inMemIndex = index.getPrepareInMemIndex(EdgeFilter.ALL_EDGES);
        assertEquals(IntArrayList.from(new int[]{16, 4, 4}), index.getEntries());

        assertEquals(13, inMemIndex.getEntriesOf(0).size());
        assertEquals(33, inMemIndex.getEntriesOf(1).size());
        assertEquals(69, inMemIndex.getEntriesOf(2).size());
        assertEquals(0, inMemIndex.getEntriesOf(3).size());

        index.dataAccess.create(1024);
        inMemIndex.store(inMemIndex.root, LocationIndexTree.START_POINTER);
        assertEquals(1 << 20, index.getCapacity());

        Snap res = index.findClosest(-.5, -.5, EdgeFilter.ALL_EDGES);
        assertEquals(1, res.getClosestNode());
    }

    @Test
    public void testReverseSpatialKey() {
        LocationIndexTree index = createIndex(createTestGraph(encodingManager), 200);
        assertEquals(IntArrayList.from(new int[]{16, 16, 16, 16, 4, 4}), index.getEntries());

        // 10111110111110101010
        String str44 = "00000000000000000000000000000000000000000000";
        assertEquals(str44 + "01010101111101111101", BitUtil.BIG.toBitString(index.createReverseKey(1.7, 0.099)));
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
        assertEquals(2, findID(index, 51.2, 9.4));
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
        assertEquals(1, findID(index, 0, 0));
        assertEquals(1, findID(index, 0, 0.1));
        assertEquals(1, findID(index, 0.1, 0.1));
        assertEquals(1, findID(index, -0.5, -0.5));
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
        assertEquals(20, findID(index, 51.25, 9.43));
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
        na.setNode(8, 49.94553, 11.57214);
        na.setNode(9, 49.94553, 11.57314);
        na.setNode(10, 49.94553, 11.57414);
        na.setNode(11, 49.94553, 11.57514);
        na.setNode(12, 49.94553, 11.57614);
        na.setNode(13, 49.94553, 11.57714);

        na.setNode(0, 49.94653, 11.57114);
        na.setNode(1, 49.94653, 11.57214);
        na.setNode(2, 49.94653, 11.57314);
        na.setNode(3, 49.94653, 11.57414);
        na.setNode(4, 49.94653, 11.57514);
        na.setNode(5, 49.94653, 11.57614);
        na.setNode(6, 49.94653, 11.57714);
        na.setNode(7, 49.94653, 11.57814);

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

        double rmin = index.calculateRMin(0.05, -0.3);
        double check = distCalc.calcDist(0.05, Math.abs(graph.getNodeAccess().getLon(2)) - index.getDeltaLon(), -0.3, -0.3);

        assertTrue((rmin - check) < 0.0001);

        double rmin2 = index.calculateRMin(0.05, -0.3, 1);
        double check2 = distCalc.calcDist(0.05, Math.abs(graph.getNodeAccess().getLat(0)), -0.3, -0.3);

        assertTrue((rmin2 - check2) < 0.0001);

        GHIntHashSet points = new GHIntHashSet();
        assertEquals(Double.MAX_VALUE, index.calcMinDistance(0.05, -0.3, points), 1e-1);

        points.add(0);
        points.add(1);
        assertEquals(54757.03, index.calcMinDistance(0.05, -0.3, points), 1e-1);

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

    // 0---1---2
    // |   |   |
    // |10 |   |
    // | | |   |
    // 3-9-4---5
    // |   |   |
    // 6---7---8
    @Test
    public void testFindNClosest() {
        Graph graph = createGHStorage(new RAMDirectory(), encodingManager, false);
        NodeAccess na = graph.getNodeAccess();
        na.setNode(0, 0.0010, 0.0000);
        na.setNode(1, 0.0010, 0.0005);
        na.setNode(2, 0.0010, 0.0010);
        na.setNode(3, 0.0005, 0.0000);
        na.setNode(4, 0.0005, 0.0005);
        na.setNode(5, 0.0005, 0.0010);
        na.setNode(6, 0.0000, 0.0000);
        na.setNode(7, 0.0000, 0.0005);
        na.setNode(8, 0.0000, 0.0010);
        na.setNode(9, 0.0005, 0.0002);
        na.setNode(10, 0.0007, 0.0002);
        graph.edge(0, 1);
        graph.edge(1, 2);
        graph.edge(0, 3);
        graph.edge(1, 4);
        graph.edge(2, 5);
        graph.edge(3, 9);
        graph.edge(9, 4);
        EdgeIteratorState edge4_5 = graph.edge(4, 5);
        graph.edge(10, 9);
        graph.edge(3, 6);
        EdgeIteratorState edge4_7 = graph.edge(4, 7);
        graph.edge(5, 8);
        graph.edge(6, 7);
        graph.edge(7, 8);

        LocationIndexTree index = createIndexNoPrepare(graph, 500);
        index.prepareIndex();

        // query node 4 => get at least 4-5, 4-7
        List<Snap> result = index.findNClosest(0.0004, 0.0006, EdgeFilter.ALL_EDGES, 15);
        List<Integer> ids = new ArrayList<>();
        for (Snap snap : result) {
            ids.add(snap.getClosestEdge().getEdge());
        }
        Collections.sort(ids);
        assertEquals("edge ids do not match",
                Arrays.asList(edge4_5.getEdge(), edge4_7.getEdge()), ids);
    }

}
