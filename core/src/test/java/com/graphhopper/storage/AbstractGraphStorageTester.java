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
package com.graphhopper.storage;

import com.graphhopper.routing.profiles.BooleanEncodedValue;
import com.graphhopper.routing.profiles.DecimalEncodedValue;
import com.graphhopper.routing.profiles.TagParserFactory;
import com.graphhopper.routing.util.*;
import com.graphhopper.util.*;
import com.graphhopper.util.shapes.BBox;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static com.graphhopper.util.GHUtility.count;
import static org.junit.Assert.*;

/**
 * Abstract test class to be extended for implementations of the Graph interface. Graphs
 * implementing GraphStorage should extend GraphStorageTest instead.
 * <p>
 *
 * @author Peter Karich
 */
public abstract class AbstractGraphStorageTester {
    private final String locationParent = "./target/graphstorage";
    protected int defaultSize = 100;
    protected String defaultGraphLoc = "./target/graphstorage/default";
    protected EncodingManager encodingManager = new EncodingManager.Builder().addGlobalEncodedValues().addAllFlagEncoders("car,foot").build();
    protected CarFlagEncoder carEncoder = (CarFlagEncoder) encodingManager.getEncoder("car");
    protected BooleanEncodedValue carAccessEnc = encodingManager.getBooleanEncodedValue(TagParserFactory.Car.ACCESS);
    protected DecimalEncodedValue carAverageSpeedEnc = encodingManager.getDecimalEncodedValue(TagParserFactory.Car.AVERAGE_SPEED);
    protected BooleanEncodedValue footAccessEnc = encodingManager.getBooleanEncodedValue(TagParserFactory.Foot.ACCESS);
    protected DecimalEncodedValue footAverageSpeedEnc = encodingManager.getDecimalEncodedValue(TagParserFactory.Foot.AVERAGE_SPEED);
    protected GraphHopperStorage graph;
    EdgeFilter carOutFilter = new DefaultEdgeFilter(carAccessEnc, true, false);
    EdgeFilter carInFilter = new DefaultEdgeFilter(carAccessEnc, false, true);
    EdgeExplorer carOutExplorer;
    EdgeExplorer carInExplorer;
    EdgeExplorer carAllExplorer;

    public static void assertPList(PointList expected, PointList list) {
        assertEquals("size of point lists is not equal", expected.getSize(), list.getSize());
        for (int i = 0; i < expected.getSize(); i++) {
            assertEquals(expected.getLatitude(i), list.getLatitude(i), 1e-4);
            assertEquals(expected.getLongitude(i), list.getLongitude(i), 1e-4);
        }
    }

    public static int getIdOf(Graph g, double latitude) {
        int s = g.getNodes();
        NodeAccess na = g.getNodeAccess();
        for (int i = 0; i < s; i++) {
            if (Math.abs(na.getLatitude(i) - latitude) < 1e-4) {
                return i;
            }
        }
        return -1;
    }

    public static int getIdOf(Graph g, double latitude, double longitude) {
        int s = g.getNodes();
        NodeAccess na = g.getNodeAccess();
        for (int i = 0; i < s; i++) {
            if (Math.abs(na.getLatitude(i) - latitude) < 1e-4 && Math.abs(na.getLongitude(i) - longitude) < 1e-4) {
                return i;
            }
        }
        throw new IllegalArgumentException("did not find node with location " + (float) latitude + "," + (float) longitude);
    }

    protected GraphHopperStorage createGHStorage() {
        GraphHopperStorage g = createGHStorage(defaultGraphLoc, false);
        carOutExplorer = g.createEdgeExplorer(carOutFilter);
        carInExplorer = g.createEdgeExplorer(carInFilter);
        carAllExplorer = g.createEdgeExplorer();
        return g;
    }

    abstract GraphHopperStorage createGHStorage(String location, boolean is3D);

    protected final GraphHopperStorage newRAMGHStorage() {
        return new GraphHopperStorage(new RAMDirectory(), encodingManager, false, new GraphExtension.NoOpExtension());
    }

    @Before
    public void setUp() {
        Helper.removeDir(new File(locationParent));
    }

    @After
    public void tearDown() {
        Helper.close(graph);
        Helper.removeDir(new File(locationParent));
    }

    @Test
    public void testSetTooBigDistance_435() {
        graph = createGHStorage();

        double maxDist = EdgeAccess.MAX_DIST;
        EdgeIteratorState edge1 = graph.edge(0, 1).setDistance(maxDist);
        assertEquals(maxDist, edge1.getDistance(), 1);

        // max out should NOT lead to infinity as this leads fast to NaN!
        try {
            graph.edge(0, 2).setDistance(maxDist + 1);
        } catch (Exception ex) {
            assertTrue(ex.getMessage(), ex.getMessage().startsWith("Distance too large"));
        }
    }

    @Test
    public void testSetNodes() {
        graph = createGHStorage();
        NodeAccess na = graph.getNodeAccess();
        for (int i = 0; i < defaultSize * 2; i++) {
            na.setNode(i, 2 * i, 3 * i);
        }
        GHUtility.createEdge(graph, carAverageSpeedEnc, 60, carAccessEnc, defaultSize + 1, defaultSize + 2, true, 10d);
        GHUtility.createEdge(graph, carAverageSpeedEnc, 60, carAccessEnc, defaultSize + 1, defaultSize + 3, true, 10);
        assertEquals(2, GHUtility.count(carAllExplorer.setBaseNode(defaultSize + 1)));
    }

    @Test
    public void testPropertiesWithNoInit() {
        graph = createGHStorage();
        assertEquals(0, graph.edge(0, 1).getData().ints[0]);
        assertEquals(0, graph.edge(0, 2).getDistance(), 1e-6);
    }

    @Test
    public void testCreateLocation() {
        graph = createGHStorage();
        GHUtility.createEdge(graph, carAverageSpeedEnc, 50, carAccessEnc, 60, 1, true);
        assertEquals(1, count(carOutExplorer.setBaseNode(1)));

        GHUtility.createEdge(graph, carAverageSpeedEnc, 60, carAccessEnc, 1, 2, true, 100);
        assertEquals(2, count(carOutExplorer.setBaseNode(1)));
    }

    @Test
    public void testEdges() {
        graph = createGHStorage();
        GHUtility.createEdge(graph, carAverageSpeedEnc, 60, carAccessEnc, 2, 1, true, 12);
        assertEquals(1, count(carOutExplorer.setBaseNode(2)));

        GHUtility.createEdge(graph, carAverageSpeedEnc, 60, carAccessEnc, 2, 3, true, 12);
        assertEquals(1, count(carOutExplorer.setBaseNode(1)));
        assertEquals(2, count(carOutExplorer.setBaseNode(2)));
        assertEquals(1, count(carOutExplorer.setBaseNode(3)));
    }

    @Test
    public void testUnidirectional() {
        graph = createGHStorage();

        GHUtility.createEdge(graph, carAverageSpeedEnc, 60, carAccessEnc, 1, 2, false, 12);
        GHUtility.createEdge(graph, carAverageSpeedEnc, 60, carAccessEnc, 1, 11, false, 12);
        GHUtility.createEdge(graph, carAverageSpeedEnc, 60, carAccessEnc, 11, 1, false, 12);
        GHUtility.createEdge(graph, carAverageSpeedEnc, 60, carAccessEnc, 1, 12, false, 12);
        GHUtility.createEdge(graph, carAverageSpeedEnc, 60, carAccessEnc, 3, 2, false, 112);
        EdgeIterator i = carOutExplorer.setBaseNode(2);
        assertFalse(i.next());

        assertEquals(1, GHUtility.count(carInExplorer.setBaseNode(1)));
        assertEquals(2, GHUtility.count(carInExplorer.setBaseNode(2)));
        assertEquals(0, GHUtility.count(carInExplorer.setBaseNode(3)));

        assertEquals(3, GHUtility.count(carOutExplorer.setBaseNode(1)));
        assertEquals(0, GHUtility.count(carOutExplorer.setBaseNode(2)));
        assertEquals(1, GHUtility.count(carOutExplorer.setBaseNode(3)));
        i = carOutExplorer.setBaseNode(3);
        i.next();
        assertEquals(2, i.getAdjNode());

        i = carOutExplorer.setBaseNode(1);
        assertTrue(i.next());
        assertEquals(12, i.getAdjNode());
        assertTrue(i.next());
        assertEquals(11, i.getAdjNode());
        assertTrue(i.next());
        assertEquals(2, i.getAdjNode());
        assertFalse(i.next());
    }

    @Test
    public void testUnidirectionalEdgeFilter() {
        graph = createGHStorage();

        GHUtility.createEdge(graph, carAverageSpeedEnc, 60, carAccessEnc, 1, 2, false, 12);
        GHUtility.createEdge(graph, carAverageSpeedEnc, 60, carAccessEnc, 1, 11, false, 12);
        GHUtility.createEdge(graph, carAverageSpeedEnc, 60, carAccessEnc, 11, 1, false, 12);
        GHUtility.createEdge(graph, carAverageSpeedEnc, 60, carAccessEnc, 1, 12, false, 12);
        GHUtility.createEdge(graph, carAverageSpeedEnc, 60, carAccessEnc, 3, 2, false, 112);
        EdgeIterator i = carOutExplorer.setBaseNode(2);
        assertFalse(i.next());

        assertEquals(4, GHUtility.count(carAllExplorer.setBaseNode(1)));

        assertEquals(1, GHUtility.count(carInExplorer.setBaseNode(1)));
        assertEquals(2, GHUtility.count(carInExplorer.setBaseNode(2)));
        assertEquals(0, GHUtility.count(carInExplorer.setBaseNode(3)));

        assertEquals(3, GHUtility.count(carOutExplorer.setBaseNode(1)));
        assertEquals(0, GHUtility.count(carOutExplorer.setBaseNode(2)));
        assertEquals(1, GHUtility.count(carOutExplorer.setBaseNode(3)));
        i = carOutExplorer.setBaseNode(3);
        i.next();
        assertEquals(2, i.getAdjNode());

        i = carOutExplorer.setBaseNode(1);
        assertTrue(i.next());
        assertEquals(12, i.getAdjNode());
        assertTrue(i.next());
        assertEquals(11, i.getAdjNode());
        assertTrue(i.next());
        assertEquals(2, i.getAdjNode());
        assertFalse(i.next());
    }

    @Test
    public void testUpdateUnidirectional() {
        graph = createGHStorage();

        GHUtility.createEdge(graph, carAverageSpeedEnc, 60, carAccessEnc, 1, 2, false, 12);
        GHUtility.createEdge(graph, carAverageSpeedEnc, 60, carAccessEnc, 3, 2, false, 112);
        EdgeIterator i = carOutExplorer.setBaseNode(2);
        assertFalse(i.next());
        i = carOutExplorer.setBaseNode(3);
        assertTrue(i.next());
        assertEquals(2, i.getAdjNode());
        assertFalse(i.next());

        GHUtility.createEdge(graph, carAverageSpeedEnc, 60, carAccessEnc, 2, 3, false, 112);
        i = carOutExplorer.setBaseNode(2);
        assertTrue(i.next());
        assertEquals(3, i.getAdjNode());
        i = carOutExplorer.setBaseNode(3);
        i.next();
        assertEquals(2, i.getAdjNode());
        assertFalse(i.next());
    }

    @Test
    public void testClone() {
        graph = createGHStorage();
        GHUtility.createEdge(graph, carAverageSpeedEnc, 60, carAccessEnc, 1, 2, true, 10);
        NodeAccess na = graph.getNodeAccess();
        na.setNode(0, 12, 23);
        na.setNode(1, 8, 13);
        na.setNode(2, 2, 10);
        na.setNode(3, 5, 9);
        GHUtility.createEdge(graph, carAverageSpeedEnc, 60, carAccessEnc, 1, 3, true, 10);

        Graph cloneGraph = graph.copyTo(AbstractGraphStorageTester.this.createGHStorage(locationParent + "/clone", false));
        assertEquals(graph.getNodes(), cloneGraph.getNodes());
        assertEquals(count(carOutExplorer.setBaseNode(1)), count(cloneGraph.createEdgeExplorer(carOutFilter).setBaseNode(1)));
        GHUtility.createEdge(graph, carAverageSpeedEnc, 60, carAccessEnc, 1, 4, true, 10);
        assertEquals(3, count(cloneGraph.createEdgeExplorer(carOutFilter).setBaseNode(1)));
        assertEquals(graph.getBounds(), cloneGraph.getBounds());
        Helper.close((Closeable) cloneGraph);
    }

    @Test
    public void testCopyProperties() {
        graph = createGHStorage();
        EdgeIteratorState edge = GHUtility.createEdge(graph, carAverageSpeedEnc, 60, carAccessEnc, 1, 3, false, 10).setName("testing").setWayGeometry(Helper.createPointList(1, 2));

        EdgeIteratorState newEdge = GHUtility.createEdge(graph, carAverageSpeedEnc, 60, carAccessEnc, 1, 3, false, 10);
        edge.copyPropertiesTo(newEdge);
        assertEquals(edge.getName(), newEdge.getName());
        assertEquals(edge.getDistance(), newEdge.getDistance(), 1e-7);
        assertEquals(edge.getData(), newEdge.getData());
        assertEquals(edge.fetchWayGeometry(0), newEdge.fetchWayGeometry(0));
    }

    @Test
    public void testGetLocations() {
        graph = createGHStorage();
        NodeAccess na = graph.getNodeAccess();
        na.setNode(0, 12, 23);
        na.setNode(1, 22, 23);
        assertEquals(2, graph.getNodes());

        GHUtility.createEdge(graph, carAverageSpeedEnc, 60, carAccessEnc, 0, 1, true, 10);
        assertEquals(2, graph.getNodes());

        GHUtility.createEdge(graph, carAverageSpeedEnc, 60, carAccessEnc, 0, 2, true, 10);
        assertEquals(3, graph.getNodes());
        Helper.close(graph);

        graph = createGHStorage();
        assertEquals(0, graph.getNodes());
    }

    @Test
    public void testCopyTo() {
        graph = createGHStorage();
        initExampleGraph(graph);
        GraphHopperStorage gs = newRAMGHStorage();
        gs.setSegmentSize(8000);
        gs.create(10);
        try {
            graph.copyTo(gs);
            checkExampleGraph(gs);
        } catch (Exception ex) {
            ex.printStackTrace();
            assertTrue(ex.toString(), false);
        }

        try {
            Helper.close(graph);
            graph = createGHStorage();
            gs.copyTo(graph);
            checkExampleGraph(graph);
        } catch (Exception ex) {
            ex.printStackTrace();
            assertTrue(ex.toString(), false);
        }
        Helper.close(graph);
    }

    @Test
    public void testAddLocation() {
        graph = createGHStorage();
        initExampleGraph(graph);
        checkExampleGraph(graph);
    }

    protected void initExampleGraph(Graph g) {
        NodeAccess na = g.getNodeAccess();
        na.setNode(0, 12, 23);
        na.setNode(1, 38.33f, 135.3f);
        na.setNode(2, 6, 139);
        na.setNode(3, 78, 89);
        na.setNode(4, 2, 1);
        na.setNode(5, 7, 5);
        GHUtility.createEdge(graph, carAverageSpeedEnc, 60, carAccessEnc, 0, 1, true, 12);
        GHUtility.createEdge(graph, carAverageSpeedEnc, 60, carAccessEnc, 0, 2, true, 212);
        GHUtility.createEdge(graph, carAverageSpeedEnc, 60, carAccessEnc, 0, 3, true, 212);
        GHUtility.createEdge(graph, carAverageSpeedEnc, 60, carAccessEnc, 0, 4, true, 212);
        GHUtility.createEdge(graph, carAverageSpeedEnc, 60, carAccessEnc, 0, 5, true, 212);
    }

    private void checkExampleGraph(Graph graph) {
        NodeAccess na = graph.getNodeAccess();
        assertEquals(12f, na.getLatitude(0), 1e-6);
        assertEquals(23f, na.getLongitude(0), 1e-6);

        assertEquals(38.33f, na.getLatitude(1), 1e-6);
        assertEquals(135.3f, na.getLongitude(1), 1e-6);

        assertEquals(6, na.getLatitude(2), 1e-6);
        assertEquals(139, na.getLongitude(2), 1e-6);

        assertEquals(78, na.getLatitude(3), 1e-6);
        assertEquals(89, na.getLongitude(3), 1e-6);

        assertEquals(GHUtility.asSet(0), GHUtility.getNeighbors(carOutExplorer.setBaseNode((1))));
        assertEquals(GHUtility.asSet(5, 4, 3, 2, 1), GHUtility.getNeighbors(carOutExplorer.setBaseNode(0)));
        try {
            assertEquals(0, count(carOutExplorer.setBaseNode(6)));
            // for now return empty iterator
            // assertFalse(true);
        } catch (Exception ex) {
        }
    }

    @Test
    public void testDirectional() {
        graph = createGHStorage();
        GHUtility.createEdge(graph, carAverageSpeedEnc, 60, carAccessEnc, 1, 2, true, 12);
        GHUtility.createEdge(graph, carAverageSpeedEnc, 60, carAccessEnc, 2, 3, false, 12);
        GHUtility.createEdge(graph, carAverageSpeedEnc, 60, carAccessEnc, 3, 4, false, 12);
        GHUtility.createEdge(graph, carAverageSpeedEnc, 60, carAccessEnc, 3, 5, true, 12);
        GHUtility.createEdge(graph, carAverageSpeedEnc, 60, carAccessEnc, 6, 3, false, 12);

        assertEquals(1, count(carAllExplorer.setBaseNode(1)));
        assertEquals(1, count(carInExplorer.setBaseNode(1)));
        assertEquals(1, count(carOutExplorer.setBaseNode(1)));

        assertEquals(2, count(carAllExplorer.setBaseNode(2)));
        assertEquals(1, count(carInExplorer.setBaseNode(2)));
        assertEquals(2, count(carOutExplorer.setBaseNode(2)));

        assertEquals(4, count(carAllExplorer.setBaseNode(3)));
        assertEquals(3, count(carInExplorer.setBaseNode(3)));
        assertEquals(2, count(carOutExplorer.setBaseNode(3)));

        assertEquals(1, count(carAllExplorer.setBaseNode(4)));
        assertEquals(1, count(carInExplorer.setBaseNode(4)));
        assertEquals(0, count(carOutExplorer.setBaseNode(4)));

        assertEquals(1, count(carAllExplorer.setBaseNode(5)));
        assertEquals(1, count(carInExplorer.setBaseNode(5)));
        assertEquals(1, count(carOutExplorer.setBaseNode(5)));
    }

    @Test
    public void testDozendEdges() {
        graph = createGHStorage();
        GHUtility.createEdge(graph, carAverageSpeedEnc, 60, carAccessEnc, 1, 2, true, 12);
        assertEquals(1, count(carAllExplorer.setBaseNode(1)));

        GHUtility.createEdge(graph, carAverageSpeedEnc, 60, carAccessEnc, 1, 3, false, 13);
        assertEquals(2, count(carAllExplorer.setBaseNode(1)));

        GHUtility.createEdge(graph, carAverageSpeedEnc, 60, carAccessEnc, 1, 4, false, 14);
        assertEquals(3, count(carAllExplorer.setBaseNode(1)));

        GHUtility.createEdge(graph, carAverageSpeedEnc, 60, carAccessEnc, 1, 5, false, 15);
        assertEquals(4, count(carAllExplorer.setBaseNode(1)));

        GHUtility.createEdge(graph, carAverageSpeedEnc, 60, carAccessEnc, 1, 6, false, 16);
        assertEquals(5, count(carAllExplorer.setBaseNode(1)));

        GHUtility.createEdge(graph, carAverageSpeedEnc, 60, carAccessEnc, 1, 7, false, 16);
        assertEquals(6, count(carAllExplorer.setBaseNode(1)));

        GHUtility.createEdge(graph, carAverageSpeedEnc, 60, carAccessEnc, 1, 8, false, 16);
        assertEquals(7, count(carAllExplorer.setBaseNode(1)));

        GHUtility.createEdge(graph, carAverageSpeedEnc, 60, carAccessEnc, 1, 9, false, 16);
        assertEquals(8, count(carAllExplorer.setBaseNode(1)));
        assertEquals(8, count(carOutExplorer.setBaseNode(1)));

        assertEquals(1, count(carInExplorer.setBaseNode(1)));
        assertEquals(1, count(carInExplorer.setBaseNode(2)));
    }

    @Test
    public void testCheckFirstNode() {
        graph = createGHStorage();

        assertEquals(0, count(carAllExplorer.setBaseNode(1)));
        GHUtility.createEdge(graph, carAverageSpeedEnc, 60, carAccessEnc, 0, 1, true, 12);
        assertEquals(1, count(carAllExplorer.setBaseNode(1)));
    }

    @Test
    public void testDeleteNodeForUnidir() {
        graph = createGHStorage();
        NodeAccess na = graph.getNodeAccess();
        na.setNode(10, 10, 1);
        na.setNode(6, 6, 1);
        na.setNode(20, 20, 1);
        na.setNode(21, 21, 1);

        GHUtility.createEdge(graph, carAverageSpeedEnc, 60, carAccessEnc, 10, 20, false, 10);
        GHUtility.createEdge(graph, carAverageSpeedEnc, 60, carAccessEnc, 21, 6, false, 10);

        graph.markNodeRemoved(0);
        graph.markNodeRemoved(7);
        assertEquals(22, graph.getNodes());
        graph.optimize();
        assertEquals(20, graph.getNodes());

        assertEquals(1, GHUtility.count(carInExplorer.setBaseNode(getIdOf(graph, 20))));
        assertEquals(0, GHUtility.count(carOutExplorer.setBaseNode(getIdOf(graph, 20))));

        assertEquals(1, GHUtility.count(carOutExplorer.setBaseNode(getIdOf(graph, 10))));
        assertEquals(0, GHUtility.count(carInExplorer.setBaseNode(getIdOf(graph, 10))));

        assertEquals(1, GHUtility.count(carInExplorer.setBaseNode(getIdOf(graph, 6))));
        assertEquals(0, GHUtility.count(carOutExplorer.setBaseNode(getIdOf(graph, 6))));

        assertEquals(1, GHUtility.count(carOutExplorer.setBaseNode(getIdOf(graph, 21))));
        assertEquals(0, GHUtility.count(carInExplorer.setBaseNode(getIdOf(graph, 21))));
    }

    @Test
    public void testComplexDeleteNode() {
        testDeleteNodes(21);
    }

    @Test
    public void testComplexDeleteNode2() {
        testDeleteNodes(6);
    }

    public void testDeleteNodes(int fillToSize) {
        graph = createGHStorage();
        NodeAccess na = graph.getNodeAccess();
        na.setNode(0, 12, 23);
        na.setNode(1, 38.33f, 135.3f);
        na.setNode(2, 3, 3);
        na.setNode(3, 78, 89);
        na.setNode(4, 2, 1);
        na.setNode(5, 2.5f, 1);

        int deleted = 2;
        for (int i = 6; i < fillToSize; i++) {
            na.setNode(i, i * 1.5, i * 1.6);
            if (i % 3 == 0) {
                graph.markNodeRemoved(i);
                deleted++;
            } else {
                // connect to
                // ... a deleted node
                GHUtility.createEdge(graph, carAverageSpeedEnc, 60, carAccessEnc, i, 0, true, 10 * i);
                // ... a non-deleted and non-moved node
                GHUtility.createEdge(graph, carAverageSpeedEnc, 60, carAccessEnc, i, 2, true, 10 * i);
                // ... a moved node
                GHUtility.createEdge(graph, carAverageSpeedEnc, 60, carAccessEnc, i, fillToSize - 1, true, 10 * i);
            }
        }

        GHUtility.createEdge(graph, carAverageSpeedEnc, 60, carAccessEnc, 0, 1, true, 10);
        GHUtility.createEdge(graph, carAverageSpeedEnc, 60, carAccessEnc, 0, 3, false, 20);
        GHUtility.createEdge(graph, carAverageSpeedEnc, 60, carAccessEnc, 3, 5, true, 20);
        GHUtility.createEdge(graph, carAverageSpeedEnc, 60, carAccessEnc, 1, 5, false, 20);

        graph.markNodeRemoved(0);
        graph.markNodeRemoved(2);
        // no deletion happend
        assertEquals(fillToSize, graph.getNodes());

        assertEquals(Arrays.<String>asList(), GHUtility.getProblems(graph));

        // now actually perform deletion
        graph.optimize();

        assertEquals(Arrays.<String>asList(), GHUtility.getProblems(graph));

        assertEquals(fillToSize - deleted, graph.getNodes());
        int id1 = getIdOf(graph, 38.33f);
        assertEquals(135.3f, na.getLongitude(id1), 1e-4);
        assertTrue(containsLatitude(graph, carAllExplorer.setBaseNode(id1), 2.5));
        assertFalse(containsLatitude(graph, carAllExplorer.setBaseNode(id1), 12));

        int id3 = getIdOf(graph, 78);
        assertEquals(89, na.getLongitude(id3), 1e-4);
        assertTrue(containsLatitude(graph, carAllExplorer.setBaseNode(id3), 2.5));
        assertFalse(containsLatitude(graph, carAllExplorer.setBaseNode(id3), 12));
    }

    public boolean containsLatitude(Graph g, EdgeIterator iter, double latitude) {
        NodeAccess na = g.getNodeAccess();
        while (iter.next()) {
            if (Math.abs(na.getLatitude(iter.getAdjNode()) - latitude) < 1e-4)
                return true;
        }
        return false;
    }

    @Test
    public void testSimpleDelete() {
        graph = createGHStorage();
        NodeAccess na = graph.getNodeAccess();
        na.setNode(0, 12, 23);
        na.setNode(1, 38.33f, 135.3f);
        na.setNode(2, 3, 3);
        na.setNode(3, 78, 89);

        GHUtility.createEdge(graph, carAverageSpeedEnc, 60, carAccessEnc, 3, 0, true, 21);
        GHUtility.createEdge(graph, carAverageSpeedEnc, 60, carAccessEnc, 5, 0, true, 22);
        GHUtility.createEdge(graph, carAverageSpeedEnc, 60, carAccessEnc, 5, 3, true, 23);

        graph.markNodeRemoved(0);
        graph.markNodeRemoved(3);

        assertEquals(6, graph.getNodes());
        assertEquals(Arrays.<String>asList(), GHUtility.getProblems(graph));

        // now actually perform deletion
        graph.optimize();

        assertEquals(4, graph.getNodes());
        assertEquals(Arrays.<String>asList(), GHUtility.getProblems(graph));
        // shouldn't change anything
        graph.optimize();
        assertEquals(4, graph.getNodes());
        assertEquals(Arrays.<String>asList(), GHUtility.getProblems(graph));
    }

    @Test
    public void testSimpleDelete2() {
        graph = createGHStorage();
        NodeAccess na = graph.getNodeAccess();
        assertEquals(-1, getIdOf(graph, 12));
        na.setNode(9, 9, 1);
        assertEquals(-1, getIdOf(graph, 12));

        na.setNode(11, 11, 1);
        na.setNode(12, 12, 1);

        // mini subnetwork which gets completely removed:
        GHUtility.createEdge(graph, carAverageSpeedEnc, 60, carAccessEnc, 5, 10, true, 510);
        graph.markNodeRemoved(5);
        graph.markNodeRemoved(10);

        PointList pl = new PointList();
        pl.add(1, 2, Double.NaN);
        pl.add(1, 3, Double.NaN);
        GHUtility.createEdge(graph, carAverageSpeedEnc, 60, carAccessEnc, 9, 11, true, 911).setWayGeometry(pl);
        GHUtility.createEdge(graph, carAverageSpeedEnc, 60, carAccessEnc, 9, 12, true, 912).setWayGeometry(pl);

        assertEquals(13, graph.getNodes());
        assertEquals(Arrays.<String>asList(), GHUtility.getProblems(graph));

        // perform deletion
        graph.optimize();

        assertEquals(11, graph.getNodes());
        assertEquals(Arrays.<String>asList(), GHUtility.getProblems(graph));

        int id11 = getIdOf(graph, 11); // is now 10
        int id12 = getIdOf(graph, 12); // is now 5
        int id9 = getIdOf(graph, 9); // is now 9
        assertEquals(GHUtility.asSet(id12, id11), GHUtility.getNeighbors(carAllExplorer.setBaseNode(id9)));
        assertEquals(GHUtility.asSet(id9), GHUtility.getNeighbors(carAllExplorer.setBaseNode(id11)));
        assertEquals(GHUtility.asSet(id9), GHUtility.getNeighbors(carAllExplorer.setBaseNode(id12)));

        EdgeIterator iter = carAllExplorer.setBaseNode(id9);
        assertTrue(iter.next());
        assertEquals(id12, iter.getAdjNode());
        assertEquals(2, iter.fetchWayGeometry(0).getLongitude(0), 1e-7);

        assertTrue(iter.next());
        assertEquals(id11, iter.getAdjNode());
        assertEquals(2, iter.fetchWayGeometry(0).getLongitude(0), 1e-7);
    }

    @Test
    public void testSimpleDelete3() {
        graph = createGHStorage();
        NodeAccess na = graph.getNodeAccess();
        na.setNode(7, 7, 1);
        na.setNode(8, 8, 1);
        na.setNode(9, 9, 1);
        na.setNode(11, 11, 1);

        // mini subnetwork which gets completely removed:
        GHUtility.createEdge(graph, carAverageSpeedEnc, 60, carAccessEnc, 5, 10, true, 510);
        graph.markNodeRemoved(3);
        graph.markNodeRemoved(4);
        graph.markNodeRemoved(5);
        graph.markNodeRemoved(10);

        GHUtility.createEdge(graph, carAverageSpeedEnc, 60, carAccessEnc, 9, 11, true, 911);
        GHUtility.createEdge(graph, carAverageSpeedEnc, 60, carAccessEnc, 7, 9, true, 78);
        GHUtility.createEdge(graph, carAverageSpeedEnc, 60, carAccessEnc, 8, 9, true, 89);

        // perform deletion
        graph.optimize();

        assertEquals(Arrays.<String>asList(), GHUtility.getProblems(graph));

        assertEquals(3, GHUtility.count(carAllExplorer.setBaseNode(getIdOf(graph, 9))));
        assertEquals(1, GHUtility.count(carAllExplorer.setBaseNode(getIdOf(graph, 7))));
        assertEquals(1, GHUtility.count(carAllExplorer.setBaseNode(getIdOf(graph, 8))));
        assertEquals(1, GHUtility.count(carAllExplorer.setBaseNode(getIdOf(graph, 11))));
    }

    @Test
    public void testDeleteAndOptimize() {
        graph = createGHStorage();
        NodeAccess na = graph.getNodeAccess();
        na.setNode(20, 10, 10);
        na.setNode(21, 10, 11);
        graph.markNodeRemoved(20);
        graph.optimize();
        assertEquals(11, na.getLongitude(20), 1e-5);
    }

    @Test
    public void testBounds() {
        graph = createGHStorage();
        BBox b = graph.getBounds();
        assertEquals(BBox.createInverse(false).maxLat, b.maxLat, 1e-6);

        NodeAccess na = graph.getNodeAccess();
        na.setNode(0, 10, 20);
        assertEquals(10, b.maxLat, 1e-6);
        assertEquals(20, b.maxLon, 1e-6);

        na.setNode(0, 15, -15);
        assertEquals(15, b.maxLat, 1e-6);
        assertEquals(20, b.maxLon, 1e-6);
        assertEquals(10, b.minLat, 1e-6);
        assertEquals(-15, b.minLon, 1e-6);
    }

    @Test
    public void testFlags() {
        graph = createGHStorage();
        EdgeIteratorState edge = GHUtility.createEdge(graph, carAverageSpeedEnc, 60, carAccessEnc, 0, 1, true, 10d);
        edge.set(carAverageSpeedEnc, 100d);
        edge = GHUtility.createEdge(graph, carAverageSpeedEnc, 60, carAccessEnc, 2, 3, false, 10d);
        edge.set(carAverageSpeedEnc, 10d);

        EdgeIterator iter = carAllExplorer.setBaseNode(0);
        assertTrue(iter.next());
        assertEquals(100d, iter.get(carAverageSpeedEnc), .1);
        assertTrue(iter.get(carAccessEnc));
        assertTrue(iter.getReverse(carAccessEnc));

        iter = carAllExplorer.setBaseNode(2);
        assertTrue(iter.next());
        assertEquals(10d, iter.get(carAverageSpeedEnc), .1);
        assertTrue(iter.get(carAccessEnc));
        assertFalse(iter.getReverse(carAccessEnc));

        try {
            GHUtility.createEdge(graph, carAverageSpeedEnc, 60, carAccessEnc, 0, 1, true, -1d);
            assertTrue(false);
        } catch (IllegalArgumentException ex) {
        }
    }

    @Test
    public void testEdgeProperties() {
        graph = createGHStorage();
        EdgeIteratorState iter1 = GHUtility.createEdge(graph, carAverageSpeedEnc, 60, carAccessEnc, 0, 1, true, 10);
        EdgeIteratorState iter2 = GHUtility.createEdge(graph, carAverageSpeedEnc, 60, carAccessEnc, 0, 2, true, 20);

        int edgeId = iter1.getEdge();
        EdgeIteratorState iter = graph.getEdgeIteratorState(edgeId, 0);
        assertEquals(10, iter.getDistance(), 1e-5);

        edgeId = iter2.getEdge();
        iter = graph.getEdgeIteratorState(edgeId, 0);
        assertEquals(2, iter.getBaseNode());
        assertEquals(0, iter.getAdjNode());
        assertEquals(20, iter.getDistance(), 1e-5);

        iter = graph.getEdgeIteratorState(edgeId, 2);
        assertEquals(0, iter.getBaseNode());
        assertEquals(2, iter.getAdjNode());
        assertEquals(20, iter.getDistance(), 1e-5);

        iter = graph.getEdgeIteratorState(edgeId, Integer.MIN_VALUE);
        assertFalse(iter == null);
        assertEquals(0, iter.getBaseNode());
        assertEquals(2, iter.getAdjNode());
        iter = graph.getEdgeIteratorState(edgeId, 1);
        assertTrue(iter == null);

        // delete
        graph.markNodeRemoved(1);
        graph.optimize();

        // throw exception if accessing deleted edge
        try {
            graph.getEdgeIteratorState(iter1.getEdge(), -1);
            assertTrue(false);
        } catch (Exception ex) {
        }
    }

    @Test
    public void testCreateDuplicateEdges() {
        graph = createGHStorage();
        GHUtility.createEdge(graph, carAverageSpeedEnc, 60, carAccessEnc, 2, 1, true, 12);
        GHUtility.createEdge(graph, carAverageSpeedEnc, 60, carAccessEnc, 2, 3, true, 12);
        GHUtility.createEdge(graph, carAverageSpeedEnc, 60, carAccessEnc, 2, 3, false, 13);
        assertEquals(3, GHUtility.count(carOutExplorer.setBaseNode(2)));

        // no exception
        graph.getEdgeIteratorState(1, 3);

        // raise exception
        try {
            graph.getEdgeIteratorState(4, 3);
            assertTrue(false);
        } catch (Exception ex) {
        }
        try {
            graph.getEdgeIteratorState(-1, 3);
            assertTrue(false);
        } catch (Exception ex) {
        }

        EdgeIterator iter = carOutExplorer.setBaseNode(2);
        assertTrue(iter.next());
        EdgeIteratorState oneIter = graph.getEdgeIteratorState(iter.getEdge(), 3);
        assertEquals(13, oneIter.getDistance(), 1e-6);
        assertEquals(2, oneIter.getBaseNode());
        assertTrue(carAccessEnc.getBool(false, oneIter.getData()));
        assertFalse(carAccessEnc.getBool(true, oneIter.getData()));

        oneIter = graph.getEdgeIteratorState(iter.getEdge(), 2);
        assertEquals(13, oneIter.getDistance(), 1e-6);
        assertEquals(3, oneIter.getBaseNode());
        assertFalse(carAccessEnc.getBool(false, oneIter.getData()));
        assertTrue(carAccessEnc.getBool(true, oneIter.getData()));

        GHUtility.createEdge(graph, carAverageSpeedEnc, 60, carAccessEnc, 3, 2, true, 14);
        assertEquals(4, GHUtility.count(carOutExplorer.setBaseNode(2)));
    }

    @Test
    public void testIdenticalNodes() {
        graph = createGHStorage();
        GHUtility.createEdge(graph, carAverageSpeedEnc, 60, carAccessEnc, 0, 0, true, 100);
        assertEquals(1, GHUtility.count(carAllExplorer.setBaseNode(0)));
    }

    @Test
    public void testIdenticalNodes2() {
        graph = createGHStorage();
        GHUtility.createEdge(graph, carAverageSpeedEnc, 60, carAccessEnc, 0, 0, false, 100);
        GHUtility.createEdge(graph, carAverageSpeedEnc, 60, carAccessEnc, 0, 0, false, 100);
        assertEquals(2, GHUtility.count(carAllExplorer.setBaseNode(0)));
    }

    @Test
    public void testEdgeReturn() {
        graph = createGHStorage();
        EdgeIteratorState iter = GHUtility.createEdge(graph, carAverageSpeedEnc, 60, carAccessEnc, 4, 10, false, 100d);
        iter.set(carAverageSpeedEnc, 10d);
        assertEquals(4, iter.getBaseNode());
        assertEquals(10, iter.getAdjNode());
        iter = GHUtility.createEdge(graph, carAverageSpeedEnc, 60, carAccessEnc, 14, 10, false, 100d);
        iter.set(carAverageSpeedEnc, 10d);
        assertEquals(14, iter.getBaseNode());
        assertEquals(10, iter.getAdjNode());
    }

    @Test
    public void testPillarNodes() {
        graph = createGHStorage();
        NodeAccess na = graph.getNodeAccess();
        na.setNode(0, 0.01, 0.01);
        na.setNode(4, 0.4, 0.4);
        na.setNode(14, 0.14, 0.14);
        na.setNode(10, 0.99, 0.99);

        PointList pointList = Helper.createPointList(1, 1, 1, 2, 1, 3);
        EdgeIteratorState edge = GHUtility.createEdge(graph, carAverageSpeedEnc, 60, carAccessEnc, 0, 4, false, 100d).setWayGeometry(pointList);
        edge.set(carAverageSpeedEnc, 10d);
        pointList = Helper.createPointList(1, 5, 1, 6, 1, 7, 1, 8, 1, 9);
        edge = GHUtility.createEdge(graph, carAverageSpeedEnc, 60, carAccessEnc, 4, 10, false, 100d).setWayGeometry(pointList);
        edge.set(carAverageSpeedEnc, 10d);
        pointList = Helper.createPointList(1, 13, 1, 12, 1, 11);
        edge = GHUtility.createEdge(graph, carAverageSpeedEnc, 60, carAccessEnc, 14, 0, false, 100d).setWayGeometry(pointList);
        edge.set(carAverageSpeedEnc, 10d);

        EdgeIterator iter = carAllExplorer.setBaseNode(0);
        assertTrue(iter.next());
        assertEquals(14, iter.getAdjNode());
        assertPList(Helper.createPointList(1, 11, 1, 12, 1, 13.0), iter.fetchWayGeometry(0));
        assertPList(Helper.createPointList(0.01, 0.01, 1, 11, 1, 12, 1, 13.0), iter.fetchWayGeometry(1));
        assertPList(Helper.createPointList(1, 11, 1, 12, 1, 13.0, 0.14, 0.14), iter.fetchWayGeometry(2));
        assertPList(Helper.createPointList(0.01, 0.01, 1, 11, 1, 12, 1, 13.0, 0.14, 0.14), iter.fetchWayGeometry(3));

        assertTrue(iter.next());
        assertEquals(4, iter.getAdjNode());
        assertPList(Helper.createPointList(1, 1, 1, 2, 1, 3), iter.fetchWayGeometry(0));
        assertPList(Helper.createPointList(0.01, 0.01, 1, 1, 1, 2, 1, 3), iter.fetchWayGeometry(1));
        assertPList(Helper.createPointList(1, 1, 1, 2, 1, 3, 0.4, 0.4), iter.fetchWayGeometry(2));
        assertPList(Helper.createPointList(0.01, 0.01, 1, 1, 1, 2, 1, 3, 0.4, 0.4), iter.fetchWayGeometry(3));

        assertFalse(iter.next());

        iter = carOutExplorer.setBaseNode(0);
        assertTrue(iter.next());
        assertEquals(4, iter.getAdjNode());
        assertPList(Helper.createPointList(1, 1, 1, 2, 1, 3), iter.fetchWayGeometry(0));
        assertFalse(iter.next());

        iter = carInExplorer.setBaseNode(10);
        assertTrue(iter.next());
        assertEquals(4, iter.getAdjNode());
        assertPList(Helper.createPointList(1, 9, 1, 8, 1, 7, 1, 6, 1, 5), iter.fetchWayGeometry(0));
        assertPList(Helper.createPointList(0.99, 0.99, 1, 9, 1, 8, 1, 7, 1, 6, 1, 5), iter.fetchWayGeometry(1));
        assertPList(Helper.createPointList(1, 9, 1, 8, 1, 7, 1, 6, 1, 5, 0.4, 0.4), iter.fetchWayGeometry(2));
        assertPList(Helper.createPointList(0.99, 0.99, 1, 9, 1, 8, 1, 7, 1, 6, 1, 5, 0.4, 0.4), iter.fetchWayGeometry(3));
        assertFalse(iter.next());
    }

    @Test
    public void testFootMix() {
        graph = createGHStorage();
        GHUtility.createEdge(graph, footAverageSpeedEnc, 5, footAccessEnc, 0, 1, true, 10d).set(footAverageSpeedEnc, 10d);
        GHUtility.createEdge(graph, carAverageSpeedEnc, 60, carAccessEnc, 0, 2, true, 10d).set(carAverageSpeedEnc, 10d);
        EdgeIteratorState edge = graph.edge(0, 3).setDistance(10);
        GHUtility.setAccess(edge, footAccessEnc, true, true).set(footAverageSpeedEnc, 10d);
        GHUtility.setAccess(edge, carAccessEnc, true, true).set(carAverageSpeedEnc, 10d);
        EdgeExplorer footOutExplorer = graph.createEdgeExplorer(new DefaultEdgeFilter(footAccessEnc, false, true));
        assertEquals(GHUtility.asSet(3, 1), GHUtility.getNeighbors(footOutExplorer.setBaseNode(0)));
        assertEquals(GHUtility.asSet(3, 2), GHUtility.getNeighbors(carOutExplorer.setBaseNode(0)));
    }

    @Test
    public void testGetAllEdges() {
        graph = createGHStorage();
        GHUtility.createEdge(graph, carAverageSpeedEnc, 60, carAccessEnc, 0, 1, true, 2);
        GHUtility.createEdge(graph, carAverageSpeedEnc, 60, carAccessEnc, 3, 1, false, 1);
        GHUtility.createEdge(graph, carAverageSpeedEnc, 60, carAccessEnc, 3, 2, false, 1);

        EdgeIterator iter = graph.getAllEdges();
        assertTrue(iter.next());
        int edgeId = iter.getEdge();
        assertEquals(0, iter.getBaseNode());
        assertEquals(1, iter.getAdjNode());
        assertEquals(2, iter.getDistance(), 1e-6);

        assertTrue(iter.next());
        int edgeId2 = iter.getEdge();
        assertEquals(1, edgeId2 - edgeId);
        assertEquals(3, iter.getBaseNode());
        assertEquals(1, iter.getAdjNode());

        assertTrue(iter.next());
        assertEquals(3, iter.getBaseNode());
        assertEquals(2, iter.getAdjNode());

        assertFalse(iter.next());
    }

    @Test
    public void testGetAllEdgesWithDelete() {
        graph = createGHStorage();
        NodeAccess na = graph.getNodeAccess();
        na.setNode(0, 0, 5);
        na.setNode(1, 1, 5);
        na.setNode(2, 2, 5);
        na.setNode(3, 3, 5);
        GHUtility.createEdge(graph, carAverageSpeedEnc, 60, carAccessEnc, 0, 1, true, 1);
        GHUtility.createEdge(graph, carAverageSpeedEnc, 60, carAccessEnc, 0, 2, true, 1);
        GHUtility.createEdge(graph, carAverageSpeedEnc, 60, carAccessEnc, 1, 2, true, 1);
        GHUtility.createEdge(graph, carAverageSpeedEnc, 60, carAccessEnc, 2, 3, true, 1);
        AllEdgesIterator iter = graph.getAllEdges();
        assertEquals(4, GHUtility.count(iter));
        assertEquals(4, iter.getMaxId());

        // delete
        graph.markNodeRemoved(1);
        graph.optimize();
        iter = graph.getAllEdges();
        assertEquals(2, GHUtility.count(iter));
        assertEquals(4, iter.getMaxId());

        iter = graph.getAllEdges();
        assertTrue(iter.next());
        EdgeIteratorState eState = iter.detach(false);
        assertEquals(iter.toString(), eState.toString());
        assertTrue(iter.next());
        assertNotEquals(iter.toString(), eState.toString());

        EdgeIteratorState eState2 = iter.detach(true);
        assertEquals(iter.getAdjNode(), eState2.getBaseNode());
        assertEquals(iter.getBaseNode(), eState2.getAdjNode());
        assertFalse(iter.next());
    }

    @Test
    public void testNameIndex() {
        graph = createGHStorage();
        EdgeIteratorState iter1 = GHUtility.createEdge(graph, carAverageSpeedEnc, 60, carAccessEnc, 0, 1, true, 10);
        iter1.setName("named street1");

        EdgeIteratorState iter2 = GHUtility.createEdge(graph, carAverageSpeedEnc, 60, carAccessEnc, 0, 1, true, 10);
        iter2.setName("named street2");

        assertEquals("named street1", graph.getEdgeIteratorState(iter1.getEdge(), iter1.getAdjNode()).getName());
        assertEquals("named street2", graph.getEdgeIteratorState(iter2.getEdge(), iter2.getAdjNode()).getName());
    }

    @Test
    public void test8BytesFlags() {
        Directory dir = new RAMDirectory();
        List<FlagEncoder> list = new ArrayList<>();
        list.add(new TmpCarFlagEncoder(29, 0.001, 0) {
            @Override
            public String toString() {
                return "car2";
            }
        });
        list.add(new TmpCarFlagEncoder(29, 0.001, 0));
        EncodingManager manager = new EncodingManager.Builder().addGlobalEncodedValues().addAll(list, 8).build();
        graph = new GraphHopperStorage(dir, manager, false, new GraphExtension.NoOpExtension()).create(defaultSize);

        EdgeIteratorState edge = graph.edge(0, 1);
        IntsRef ints = manager.createIntsRef();
        ints.ints[0] = BitUtil.LITTLE.getIntHigh(Long.MAX_VALUE / 3);
        ints.ints[1] = BitUtil.LITTLE.getIntLow(Long.MAX_VALUE / 3);
        edge.setData(ints);
        ints = edge.getData();
        // System.out.println(BitUtil.LITTLE.toBitString(Long.MAX_VALUE / 3) + "\n" + BitUtil.LITTLE.toBitString(edge.getFlags()));
        long lng = BitUtil.LITTLE.toLong(ints.ints[0], ints.ints[1]);
        assertEquals(Long.MAX_VALUE / 3, lng);
        graph.close();

        graph = new GraphHopperStorage(dir, manager, false, new GraphExtension.NoOpExtension()).create(defaultSize);

        edge = graph.edge(0, 1);
        BooleanEncodedValue car2AccessEnc = manager.getBooleanEncodedValue("car2.access");
        edge.set(car2AccessEnc, true);
        edge.setReverse(car2AccessEnc, true);
        edge.set(manager.getDecimalEncodedValue("car2.average_speed"), 99.123);
        assertEquals(99.123, list.get(0).getSpeed(edge.getData()), 1e-3);
        EdgeIteratorState edgeIter = GHUtility.getEdge(graph, 1, 0);

        assertEquals(99.123, list.get(0).getSpeed(edgeIter.getData()), 1e-3);
        assertTrue(edgeIter.get(car2AccessEnc));
        assertTrue(edgeIter.getReverse(car2AccessEnc));
        edge = graph.edge(2, 3);
        BooleanEncodedValue tmpCarAccessEnc = manager.getBooleanEncodedValue("car.access");
        edge.set(tmpCarAccessEnc, true);
        edge.set(manager.getDecimalEncodedValue(TagParserFactory.Car.AVERAGE_SPEED), 44.123);
        assertEquals(44.123, list.get(1).getSpeed(edge.getData()), 1e-3);

        edgeIter = GHUtility.getEdge(graph, 3, 2);
        ints = edgeIter.getData();
        assertEquals(44.123, list.get(1).getSpeed(ints), 1e-3);
        assertEquals(44.123, list.get(1).getReverseSpeed(ints), 1e-3);
        assertFalse(edgeIter.get(tmpCarAccessEnc));
        assertTrue(edgeIter.getReverse(tmpCarAccessEnc));
    }

    @Test
    public void testEnabledElevation() {
        graph = createGHStorage(defaultGraphLoc, true);
        NodeAccess na = graph.getNodeAccess();
        assertTrue(na.is3D());
        na.setNode(0, 10, 20, -10);
        na.setNode(1, 11, 2, 100);
        assertEquals(-10, na.getEle(0), 1e-1);
        assertEquals(100, na.getEle(1), 1e-1);

        graph.edge(0, 1).setWayGeometry(Helper.createPointList3D(10, 27, 72, 11, 20, 1));
        assertEquals(Helper.createPointList3D(10, 27, 72, 11, 20, 1), GHUtility.getEdge(graph, 0, 1).fetchWayGeometry(0));
        assertEquals(Helper.createPointList3D(10, 20, -10, 10, 27, 72, 11, 20, 1, 11, 2, 100), GHUtility.getEdge(graph, 0, 1).fetchWayGeometry(3));
        assertEquals(Helper.createPointList3D(11, 2, 100, 11, 20, 1, 10, 27, 72, 10, 20, -10), GHUtility.getEdge(graph, 1, 0).fetchWayGeometry(3));
    }

    @Test
    public void testDontGrowOnUpdate() throws IOException {
        graph = createGHStorage(defaultGraphLoc, true);
        NodeAccess na = graph.getNodeAccess();
        assertTrue(na.is3D());
        na.setNode(0, 10, 10, 0);
        na.setNode(1, 11, 20, 1);
        na.setNode(2, 12, 12, 0.4);

        EdgeIteratorState iter2 = GHUtility.createEdge(graph, carAverageSpeedEnc, 60, carAccessEnc, 0, 1, true, 100);
        final BaseGraph baseGraph = (BaseGraph) graph.getBaseGraph();
        assertEquals(4, baseGraph.getMaxGeoRef());
        iter2.setWayGeometry(Helper.createPointList3D(1, 2, 3, 3, 4, 5, 5, 6, 7, 7, 8, 9));
        assertEquals(4 + (1 + 12), baseGraph.getMaxGeoRef());
        iter2.setWayGeometry(Helper.createPointList3D(1, 2, 3, 3, 4, 5, 5, 6, 7));
        assertEquals(4 + (1 + 12), baseGraph.getMaxGeoRef());
        iter2.setWayGeometry(Helper.createPointList3D(1, 2, 3, 3, 4, 5));
        assertEquals(4 + (1 + 12), baseGraph.getMaxGeoRef());
        iter2.setWayGeometry(Helper.createPointList3D(1, 2, 3));
        assertEquals(4 + (1 + 12), baseGraph.getMaxGeoRef());
        iter2.setWayGeometry(Helper.createPointList3D(1.5, 1, 0, 2, 3, 0));
        assertEquals(4 + (1 + 12) + (1 + 6), baseGraph.getMaxGeoRef());
        EdgeIteratorState iter1 = GHUtility.createEdge(graph, carAverageSpeedEnc, 60, carAccessEnc, 0, 2, true, 200);
        iter1.setWayGeometry(Helper.createPointList3D(3.5, 4.5, 0, 5, 6, 0));
        assertEquals(4 + (1 + 12) + (1 + 6) + (1 + 6), baseGraph.getMaxGeoRef());
    }

    @Test
    public void testDetachEdge() {
        graph = createGHStorage();
        GHUtility.createEdge(graph, carAverageSpeedEnc, 60, carAccessEnc, 0, 1, true, 2d);
        GHUtility.createEdge(graph, carAverageSpeedEnc, 60, carAccessEnc, 0, 2, false, 2d).
                setWayGeometry(Helper.createPointList(1, 2, 3, 4)).
                set(carAverageSpeedEnc, 10d);
        GHUtility.createEdge(graph, carAverageSpeedEnc, 60, carAccessEnc, 1, 2, true, 2d);

        EdgeIterator iter = graph.createEdgeExplorer().setBaseNode(0);
        try {
            // currently not possible to detach without next, without introducing a new property inside EdgeIterable
            iter.detach(false);
            assertTrue(false);
        } catch (Exception ex) {
        }

        iter.next();
        EdgeIteratorState edgeState02 = iter.detach(false);
        assertEquals(2, iter.getAdjNode());
        assertEquals(1, edgeState02.fetchWayGeometry(0).getLatitude(0), 1e-1);
        assertEquals(2, edgeState02.getAdjNode());
        assertTrue(edgeState02.get(carAccessEnc));

        EdgeIteratorState edgeState20 = iter.detach(true);
        assertEquals(0, edgeState20.getAdjNode());
        assertEquals(2, edgeState20.getBaseNode());
        assertEquals(3, edgeState20.fetchWayGeometry(0).getLatitude(0), 1e-1);
        assertFalse(edgeState20.get(carAccessEnc));
        assertEquals(GHUtility.getEdge(graph, 0, 2).getData(), edgeState02.getData());
        assertEquals(GHUtility.getEdge(graph, 2, 0).getData(), edgeState20.getData());

        iter.next();
        assertEquals(1, iter.getAdjNode());
        assertEquals(2, edgeState02.getAdjNode());
        assertEquals(2, edgeState20.getBaseNode());

        assertEquals(0, iter.fetchWayGeometry(0).size());
        assertEquals(1, edgeState02.fetchWayGeometry(0).getLatitude(0), 1e-1);
        assertEquals(3, edgeState20.fetchWayGeometry(0).getLatitude(0), 1e-1);

        // #162 a directed self referencing edge should be able to reverse its state too
        GHUtility.createEdge(graph, carAverageSpeedEnc, 60, carAccessEnc, 3, 3, false, 2).set(carAverageSpeedEnc, 10d);
        EdgeIterator edgeState33 = graph.createEdgeExplorer().setBaseNode(3);
        edgeState33.next();
        assertEquals(3, edgeState33.getBaseNode());
        assertEquals(3, edgeState33.getAdjNode());
        assertEquals(edgeState02.getData(), edgeState33.detach(false).getData());
        assertEquals(edgeState20.getData(), edgeState33.detach(true).getData());
    }

    static class TmpCarFlagEncoder extends CarFlagEncoder {
        public TmpCarFlagEncoder(int speedBits, double speedFactor, int maxTurnCosts) {
            super(speedBits, speedFactor, maxTurnCosts);
        }
    }
}
