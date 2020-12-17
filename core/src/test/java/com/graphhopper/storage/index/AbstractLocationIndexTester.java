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
import com.graphhopper.util.EdgeIterator;
import com.graphhopper.util.EdgeIteratorState;
import com.graphhopper.util.GHUtility;
import com.graphhopper.util.Helper;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.Closeable;
import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

import static org.junit.Assert.assertEquals;

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
        return new GraphHopperStorage(dir, encodingManager, is3D).create(100);
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
        EncodingManager em = EncodingManager.create("car");
        Graph g = AbstractLocationIndexTester.this.createGHStorage(em);
        initSimpleGraph(g, em);

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

    @Test
    public void testSimpleGraph2() {
        EncodingManager em = EncodingManager.create("car");
        Graph g = AbstractLocationIndexTester.this.createGHStorage(em);
        initSimpleGraph(g, em);

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
        Graph g = AbstractLocationIndexTester.this.createGHStorage(encodingManager);
        initSimpleGraph(g, encodingManager);
        idx = createIndex(g, -1);
        assertEquals(1, findID(idx, 1, -1));

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
