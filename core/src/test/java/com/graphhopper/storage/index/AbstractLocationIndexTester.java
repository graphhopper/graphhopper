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

import com.graphhopper.routing.profiles.BooleanEncodedValue;
import com.graphhopper.routing.profiles.DecimalEncodedValue;
import com.graphhopper.routing.util.*;
import com.graphhopper.storage.*;
import com.graphhopper.util.DistanceCalc;
import com.graphhopper.util.DistanceCalcEarth;
import com.graphhopper.util.EdgeIterator;
import com.graphhopper.util.Helper;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.Closeable;
import java.io.File;
import java.util.Random;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * @author Peter Karich
 */
public abstract class AbstractLocationIndexTester {
    String location = "./target/tmp/";
    LocationIndex idx;

    public abstract LocationIndex createIndex(Graph g, int resolution);

    GraphHopperStorage createGHStorage(EncodingManager encodingManager) {
        return AbstractLocationIndexTester.this.createGHStorage(new RAMDirectory(), encodingManager, false);
    }

    GraphHopperStorage createGHStorage(Directory dir, EncodingManager encodingManager, boolean is3D) {
        return new GraphHopperStorage(dir, encodingManager, is3D, new GraphExtension.NoOpExtension()).create(100);
    }

    protected int findID(LocationIndex index, double lat, double lon) {
        return index.findClosest(lat, lon, EdgeFilter.ALL_EDGES).getClosestNode();
    }

    public boolean hasEdgeSupport() {
        return false;
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
        Graph g = AbstractLocationIndexTester.this.createGHStorage(EncodingManager.create("car"));
        initSimpleGraph(g);

        idx = createIndex(g, -1);
        assertEquals(4, findID(idx, 5, 2));
        assertEquals(3, findID(idx, 1.5, 2));
        assertEquals(0, findID(idx, -1, -1));

        if (hasEdgeSupport()) // now get the edge 1-4 and not node 6
        {
            assertEquals(4, findID(idx, 4, 0));
        } else {
            assertEquals(6, findID(idx, 4, 0));
        }
        Helper.close((Closeable) g);
    }

    public void initSimpleGraph(Graph g) {
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
        g.edge(0, 1, 3.5, true);
        g.edge(0, 2, 2.5, true);
        g.edge(2, 3, 1, true);
        g.edge(3, 4, 3.2, true);
        g.edge(1, 4, 2.4, true);
        g.edge(3, 5, 1.5, true);
        // make sure 6 is connected
        g.edge(6, 4, 1.2, true);
    }

    @Test
    public void testSimpleGraph2() {
        Graph g = AbstractLocationIndexTester.this.createGHStorage(EncodingManager.create("car"));
        initSimpleGraph(g);

        idx = createIndex(g, -1);
        assertEquals(4, findID(idx, 5, 2));
        assertEquals(3, findID(idx, 1.5, 2));
        assertEquals(0, findID(idx, -1, -1));
        assertEquals(6, findID(idx, 4.5, -0.5));
        if (hasEdgeSupport()) {
            assertEquals(4, findID(idx, 4, 1));
            assertEquals(4, findID(idx, 4, 0));
        } else {
            assertEquals(6, findID(idx, 4, 1));
            assertEquals(6, findID(idx, 4, 0));
        }
        assertEquals(6, findID(idx, 4, -2));
        assertEquals(5, findID(idx, 3, 3));
        Helper.close((Closeable) g);
    }

    @Test
    public void testGrid() {
        Graph g = createSampleGraph(EncodingManager.create("car"));
        int locs = g.getNodes();

        idx = createIndex(g, -1);
        // if we would use less array entries then some points gets the same key so avoid that for this test
        // e.g. for 16 we get "expected 6 but was 9" i.e 6 was overwritten by node j9 which is a bit closer to the grid center        
        // go through every point of the graph if all points are reachable
        NodeAccess na = g.getNodeAccess();
        for (int i = 0; i < locs; i++) {
            double lat = na.getLatitude(i);
            double lon = na.getLongitude(i);
            assertEquals("nodeId:" + i + " " + (float) lat + "," + (float) lon, i, findID(idx, lat, lon));
        }

        // hit random lat,lon and compare result to full index
        Random rand = new Random(12);
        LocationIndex fullIndex;
        if (hasEdgeSupport())
            fullIndex = new Location2IDFullWithEdgesIndex(g);
        else
            fullIndex = new Location2IDFullIndex(g);

        DistanceCalc dist = new DistanceCalcEarth();
        for (int i = 0; i < 100; i++) {
            double lat = rand.nextDouble() * 5;
            double lon = rand.nextDouble() * 5;
            int fullId = findID(fullIndex, lat, lon);
            double fullLat = na.getLatitude(fullId);
            double fullLon = na.getLongitude(fullId);
            float fullDist = (float) dist.calcDist(lat, lon, fullLat, fullLon);
            int newId = findID(idx, lat, lon);
            double newLat = na.getLatitude(newId);
            double newLon = na.getLongitude(newId);
            float newDist = (float) dist.calcDist(lat, lon, newLat, newLon);

            if (testGridIgnore(i)) {
                continue;
            }

            assertTrue(i + " orig:" + (float) lat + "," + (float) lon
                            + " full:" + fullLat + "," + fullLon + " fullDist:" + fullDist
                            + " found:" + newLat + "," + newLon + " foundDist:" + newDist,
                    Math.abs(fullDist - newDist) < 50000);
        }
        fullIndex.close();
        Helper.close((Closeable) g);
    }

    // our simple index has only one node per tile => problems if multiple subnetworks
    boolean testGridIgnore(int i) {
        return false;
    }

    @Test
    public void testSinglePoints120() {
        Graph g = createSampleGraph(EncodingManager.create("car"));
        idx = createIndex(g, -1);

        assertEquals(1, findID(idx, 1.637, 2.23));
        assertEquals(10, findID(idx, 3.649, 1.375));
        assertEquals(9, findID(idx, 3.3, 2.2));
        assertEquals(6, findID(idx, 3.0, 1.5));

        assertEquals(10, findID(idx, 3.8, 0));
        assertEquals(10, findID(idx, 3.8466, 0.021));
        Helper.close((Closeable) g);
    }

    @Test
    public void testSinglePoints32() {
        Graph g = createSampleGraph(EncodingManager.create("car"));
        idx = createIndex(g, -1);

        // 10 or 6
        assertEquals(10, findID(idx, 3.649, 1.375));
        assertEquals(10, findID(idx, 3.8465748, 0.021762699));
        if (hasEdgeSupport()) {
            assertEquals(4, findID(idx, 2.485, 1.373));
        } else {
            assertEquals(6, findID(idx, 2.485, 1.373));
        }
        assertEquals(0, findID(idx, 0.64628404, 0.53006625));
        Helper.close((Closeable) g);
    }

    @Test
    public void testNoErrorOnEdgeCase_lastIndex() {
        final EncodingManager encodingManager = EncodingManager.create("car");
        int locs = 10000;
        Graph g = AbstractLocationIndexTester.this.createGHStorage(new MMapDirectory(location), encodingManager, false);
        NodeAccess na = g.getNodeAccess();
        Random rand = new Random(12);
        for (int i = 0; i < locs; i++) {
            na.setNode(i, (float) rand.nextDouble() * 10 + 10, (float) rand.nextDouble() * 10 + 10);
        }
        idx = createIndex(g, 200);
        Helper.close((Closeable) g);
    }

    public Graph createSampleGraph(EncodingManager encodingManager) {
        Graph graph = AbstractLocationIndexTester.this.createGHStorage(encodingManager);
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

        graph.edge(a0, b1, 1, true);
        graph.edge(c2, b1, 1, true);
        graph.edge(c2, d3, 1, true);
        graph.edge(f5, b1, 1, true);
        graph.edge(e4, f5, 1, true);
        graph.edge(m12, d3, 1, true);
        graph.edge(e4, k10, 1, true);
        graph.edge(f5, d3, 1, true);
        graph.edge(f5, i8, 1, true);
        graph.edge(f5, j9, 1, true);
        graph.edge(k10, g6, 1, true);
        graph.edge(j9, l11, 1, true);
        graph.edge(i8, l11, 1, true);
        graph.edge(i8, h7, 1, true);
        graph.edge(k10, n13, 1, true);
        graph.edge(k10, o14, 1, true);
        graph.edge(l11, p15, 1, true);
        graph.edge(m12, p15, 1, true);
        graph.edge(q16, p15, 1, true);
        graph.edge(q16, m12, 1, true);
        return graph;
    }

    @Test
    public void testDifferentVehicles() {
        final EncodingManager encodingManager = EncodingManager.create("car,foot");
        Graph g = AbstractLocationIndexTester.this.createGHStorage(encodingManager);
        initSimpleGraph(g);
        idx = createIndex(g, -1);
        assertEquals(1, findID(idx, 1, -1));

        // now make all edges from node 1 accessible for CAR only
        EdgeIterator iter = g.createEdgeExplorer().setBaseNode(1);
        FlagEncoder encoder = encodingManager.getEncoder("foot");
        BooleanEncodedValue accessEnc = encoder.getAccessEnc();
        while (iter.next()) {
            iter.set(accessEnc, false).setReverse(accessEnc, false);
        }
        idx.close();

        idx = createIndex(g, -1);
        FootFlagEncoder footEncoder = (FootFlagEncoder) encodingManager.getEncoder("foot");
        assertEquals(2, idx.findClosest(1, -1, DefaultEdgeFilter.allEdges(footEncoder)).getClosestNode());
        Helper.close((Closeable) g);
    }
}
