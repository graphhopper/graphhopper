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

import com.graphhopper.routing.ev.*;
import com.graphhopper.routing.util.AccessFilter;
import com.graphhopper.routing.util.EdgeFilter;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.util.*;
import com.graphhopper.util.shapes.BBox;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.Map;

import static com.graphhopper.search.KVStorage.KValue;
import static com.graphhopper.util.Parameters.Details.STREET_NAME;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Abstract test class to be extended for implementations of the Graph interface. Graphs
 * implementing GraphStorage should extend {@link BaseGraphTest} instead.
 * <p>
 *
 * @author Peter Karich
 */
public abstract class AbstractGraphStorageTester {
    private final String locationParent = "./target/graphstorage";
    protected int defaultSize = 100;
    protected String defaultGraphLoc = "./target/graphstorage/default";
    protected BooleanEncodedValue carAccessEnc = new SimpleBooleanEncodedValue("car_access", true);
    protected DecimalEncodedValue carSpeedEnc = new DecimalEncodedValueImpl("car_speed", 5, 5, false);
    protected BooleanEncodedValue footAccessEnc = new SimpleBooleanEncodedValue("foot_access", true);
    protected DecimalEncodedValue footSpeedEnc = new DecimalEncodedValueImpl("foot_speed", 4, 1, true);
    protected EncodingManager encodingManager = createEncodingManager();

    protected EncodingManager createEncodingManager() {
        return new EncodingManager.Builder()
                .add(carAccessEnc).add(carSpeedEnc)
                .add(footAccessEnc).add(footSpeedEnc)
                .add(RoadClass.create())
                .build();
    }

    protected BaseGraph graph;
    EdgeFilter carOutFilter = AccessFilter.outEdges(carAccessEnc);
    EdgeFilter carInFilter = AccessFilter.inEdges(carAccessEnc);
    EdgeExplorer carOutExplorer;
    EdgeExplorer carInExplorer;
    EdgeExplorer carAllExplorer;

    public static void assertPList(PointList expected, PointList list) {
        assertEquals(expected.size(), list.size(), "size of point lists is not equal");
        for (int i = 0; i < expected.size(); i++) {
            assertEquals(expected.getLat(i), list.getLat(i), 1e-5);
            assertEquals(expected.getLon(i), list.getLon(i), 1e-5);
        }
    }

    public static int getIdOf(Graph g, double latitude) {
        int s = g.getNodes();
        NodeAccess na = g.getNodeAccess();
        for (int i = 0; i < s; i++) {
            if (Math.abs(na.getLat(i) - latitude) < 1e-5) {
                return i;
            }
        }
        return -1;
    }

    public static int getIdOf(Graph g, double latitude, double longitude) {
        int s = g.getNodes();
        NodeAccess na = g.getNodeAccess();
        for (int i = 0; i < s; i++) {
            if (Math.abs(na.getLat(i) - latitude) < 1e-5 && Math.abs(na.getLon(i) - longitude) < 1e-5) {
                return i;
            }
        }
        throw new IllegalArgumentException("did not find node with location " + (float) latitude + "," + (float) longitude);
    }

    protected BaseGraph createGHStorage() {
        BaseGraph g = createGHStorage(defaultGraphLoc, false);
        carOutExplorer = g.createEdgeExplorer(carOutFilter);
        carInExplorer = g.createEdgeExplorer(carInFilter);
        carAllExplorer = g.createEdgeExplorer();
        return g;
    }

    abstract BaseGraph createGHStorage(String location, boolean is3D);

    @BeforeEach
    public void setUp() {
        Helper.removeDir(new File(locationParent));
    }

    @AfterEach
    public void tearDown() {
        Helper.close(graph);
        Helper.removeDir(new File(locationParent));
    }

    @Test
    public void testSetTooBigDistance_435() {
        graph = createGHStorage();

        double maxDist = BaseGraphNodesAndEdges.MAX_DIST;
        EdgeIteratorState edge1 = graph.edge(0, 1).setDistance(maxDist);
        assertEquals(maxDist, edge1.getDistance(), 1);

        // max out should NOT lead to infinity as this leads fast to NaN! -> we set dist to the maximum if its larger than desired
        EdgeIteratorState edge2 = graph.edge(0, 2).setDistance(maxDist + 1);
        assertEquals(maxDist, edge2.getDistance(), 1);
    }

    @Test
    public void testSetNodes() {
        graph = createGHStorage();
        NodeAccess na = graph.getNodeAccess();
        for (int i = 0; i < defaultSize * 2; i++) {
            na.setNode(i, 2 * i, 3 * i);
        }
        graph.edge(defaultSize + 1, defaultSize + 2).setDistance(10);
        graph.edge(defaultSize + 1, defaultSize + 3).setDistance(10);
        assertEquals(2, getCountAll(defaultSize + 1));
    }

    @Test
    public void testPropertiesWithNoInit() {
        graph = createGHStorage();
        assertEquals(0, graph.edge(0, 1).getFlags().ints[0]);
        assertEquals(0, graph.edge(0, 2).getDistance(), 1e-6);
    }

    @Test
    public void testCreateLocation() {
        graph = createGHStorage();
        graph.edge(3, 1).setDistance(50).set(carAccessEnc, true, true);
        assertEquals(1, getCountOut(1));

        graph.edge(1, 2).setDistance(100).set(carAccessEnc, true, true);
        assertEquals(2, getCountOut(1));
    }

    @Test
    public void testEdges() {
        graph = createGHStorage();
        graph.edge(2, 1).setDistance(12).set(carAccessEnc, true, true);
        assertEquals(1, getCountOut(2));

        graph.edge(2, 3).setDistance(12).set(carAccessEnc, true, true);
        assertEquals(1, getCountOut(1));
        assertEquals(2, getCountOut(2));
        assertEquals(1, getCountOut(3));
    }

    @Test
    public void testUnidirectional() {
        graph = createGHStorage();

        graph.edge(1, 2).setDistance(12).set(carAccessEnc, true, false);
        graph.edge(1, 11).setDistance(12).set(carAccessEnc, true, false);
        graph.edge(11, 1).setDistance(12).set(carAccessEnc, true, false);
        graph.edge(1, 12).setDistance(12).set(carAccessEnc, true, false);
        graph.edge(3, 2).setDistance(112).set(carAccessEnc, true, false);
        EdgeIterator i = carOutExplorer.setBaseNode(2);
        assertFalse(i.next());

        assertEquals(1, getCountIn(1));
        assertEquals(2, getCountIn(2));
        assertEquals(0, getCountIn(3));

        assertEquals(3, getCountOut(1));
        assertEquals(0, getCountOut(2));
        assertEquals(1, getCountOut(3));
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

        graph.edge(1, 2).setDistance(12).set(carAccessEnc, true, false);
        graph.edge(1, 11).setDistance(12).set(carAccessEnc, true, false);
        graph.edge(11, 1).setDistance(12).set(carAccessEnc, true, false);
        graph.edge(1, 12).setDistance(12).set(carAccessEnc, true, false);
        graph.edge(3, 2).setDistance(112).set(carAccessEnc, true, false);
        EdgeIterator i = carOutExplorer.setBaseNode(2);
        assertFalse(i.next());

        assertEquals(4, getCountAll(1));

        assertEquals(1, getCountIn(1));
        assertEquals(2, getCountIn(2));
        assertEquals(0, getCountIn(3));

        assertEquals(3, getCountOut(1));
        assertEquals(0, getCountOut(2));
        assertEquals(1, getCountOut(3));
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

        graph.edge(1, 2).setDistance(12).set(carAccessEnc, true, false);
        graph.edge(3, 2).setDistance(112).set(carAccessEnc, true, false);
        EdgeIterator i = carOutExplorer.setBaseNode(2);
        assertFalse(i.next());
        i = carOutExplorer.setBaseNode(3);
        assertTrue(i.next());
        assertEquals(2, i.getAdjNode());
        assertFalse(i.next());

        graph.edge(2, 3).setDistance(112).set(carAccessEnc, true, false);
        i = carOutExplorer.setBaseNode(2);
        assertTrue(i.next());
        assertEquals(3, i.getAdjNode());
        i = carOutExplorer.setBaseNode(3);
        i.next();
        assertEquals(2, i.getAdjNode());
        assertFalse(i.next());
    }

    @Test
    public void testCopyProperties() {
        graph = createGHStorage();
        EdgeIteratorState edge = graph.edge(1, 3).setDistance(10).set(carAccessEnc, true, false).
                setKeyValues(Map.of(STREET_NAME, new KValue("testing"))).setWayGeometry(Helper.createPointList(1, 2));

        EdgeIteratorState newEdge = graph.edge(1, 3).setDistance(10).set(carAccessEnc, true, false);
        newEdge.copyPropertiesFrom(edge);
        assertEquals(edge.getName(), newEdge.getName());
        assertEquals(edge.getDistance(), newEdge.getDistance(), 1e-7);
        assertEquals(edge.getFlags(), newEdge.getFlags());
        assertEquals(edge.fetchWayGeometry(FetchMode.PILLAR_ONLY), newEdge.fetchWayGeometry(FetchMode.PILLAR_ONLY));
    }

    @Test
    public void testGetLocations() {
        graph = createGHStorage();
        NodeAccess na = graph.getNodeAccess();
        na.setNode(0, 12, 23);
        na.setNode(1, 22, 23);
        assertEquals(2, graph.getNodes());

        graph.edge(0, 1).setDistance(10).set(carAccessEnc, true, true);
        assertEquals(2, graph.getNodes());

        graph.edge(0, 2).setDistance(10).set(carAccessEnc, true, true);
        assertEquals(3, graph.getNodes());
        Helper.close(graph);

        graph = createGHStorage();
        assertEquals(0, graph.getNodes());
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
        g.edge(0, 1).setDistance((12)).set(carAccessEnc, true, true);
        g.edge(0, 2).setDistance((212)).set(carAccessEnc, true, true);
        g.edge(0, 3).setDistance((212)).set(carAccessEnc, true, true);
        g.edge(0, 4).setDistance((212)).set(carAccessEnc, true, true);
        g.edge(0, 5).setDistance((212)).set(carAccessEnc, true, true);
    }

    private void checkExampleGraph(Graph graph) {
        NodeAccess na = graph.getNodeAccess();
        assertEquals(12f, na.getLat(0), 1e-6);
        assertEquals(23f, na.getLon(0), 1e-6);

        assertEquals(38.33f, na.getLat(1), 1e-6);
        assertEquals(135.3f, na.getLon(1), 1e-6);

        assertEquals(6, na.getLat(2), 1e-6);
        assertEquals(139, na.getLon(2), 1e-6);

        assertEquals(78, na.getLat(3), 1e-6);
        assertEquals(89, na.getLon(3), 1e-6);

        assertEquals(GHUtility.asSet(0), GHUtility.getNeighbors(carOutExplorer.setBaseNode((1))));
        assertEquals(GHUtility.asSet(5, 4, 3, 2, 1), GHUtility.getNeighbors(carOutExplorer.setBaseNode(0)));
        try {
            assertEquals(0, getCountOut(6));
            // for now return empty iterator
            // assertFalse(true);
        } catch (Exception ex) {
        }
    }

    @Test
    public void testDirectional() {
        graph = createGHStorage();
        graph.edge(1, 2).setDistance(12).set(carAccessEnc, true, true);
        graph.edge(2, 3).setDistance(12).set(carAccessEnc, true, false);
        graph.edge(3, 4).setDistance(12).set(carAccessEnc, true, false);
        graph.edge(3, 5).setDistance(12).set(carAccessEnc, true, true);
        graph.edge(6, 3).setDistance(12).set(carAccessEnc, true, false);

        assertEquals(1, getCountAll(1));
        assertEquals(1, getCountIn(1));
        assertEquals(1, getCountOut(1));

        assertEquals(2, getCountAll(2));
        assertEquals(1, getCountIn(2));
        assertEquals(2, getCountOut(2));

        assertEquals(4, getCountAll(3));
        assertEquals(3, getCountIn(3));
        assertEquals(2, getCountOut(3));

        assertEquals(1, getCountAll(4));
        assertEquals(1, getCountIn(4));
        assertEquals(0, getCountOut(4));

        assertEquals(1, getCountAll(5));
        assertEquals(1, getCountIn(5));
        assertEquals(1, getCountOut(5));
    }

    @Test
    public void testDozendEdges() {
        graph = createGHStorage();
        graph.edge(1, 2).setDistance(12).set(carAccessEnc, true, true);
        int nn = 1;
        assertEquals(1, getCountAll(nn));

        graph.edge(1, 3).setDistance(13).set(carAccessEnc, true, false);
        assertEquals(2, getCountAll(1));

        graph.edge(1, 4).setDistance(14).set(carAccessEnc, true, false);
        assertEquals(3, getCountAll(1));

        graph.edge(1, 5).setDistance(15).set(carAccessEnc, true, false);
        assertEquals(4, getCountAll(1));

        graph.edge(1, 6).setDistance(16).set(carAccessEnc, true, false);
        assertEquals(5, getCountAll(1));

        graph.edge(1, 7).setDistance(16).set(carAccessEnc, true, false);
        assertEquals(6, getCountAll(1));

        graph.edge(1, 8).setDistance(16).set(carAccessEnc, true, false);
        assertEquals(7, getCountAll(1));

        graph.edge(1, 9).setDistance(16).set(carAccessEnc, true, false);
        assertEquals(8, getCountAll(1));
        assertEquals(8, getCountOut(1));

        assertEquals(1, getCountIn(1));
        assertEquals(1, getCountIn(2));
    }

    @Test
    public void testCheckFirstNode() {
        graph = createGHStorage();
        // no nodes added yet
        assertThrows(IllegalArgumentException.class, () -> getCountAll(1));
        graph.getNodeAccess().setNode(1, 0, 0);
        assertEquals(0, getCountAll(1));
        graph.edge(0, 1).setDistance(12).set(carAccessEnc, true, true);
        assertEquals(1, getCountAll(1));
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
        graph.edge(0, 1).set(carAccessEnc, true, true).setDistance(10)
                .set(carSpeedEnc, 100);
        graph.edge(2, 3).set(carAccessEnc, true, false).setDistance(10)
                .set(carSpeedEnc, 10);

        EdgeIterator iter = carAllExplorer.setBaseNode(0);
        assertTrue(iter.next());
        assertEquals(100, iter.get(carSpeedEnc), 1);
        assertTrue(iter.get(carAccessEnc));
        assertTrue(iter.getReverse(carAccessEnc));

        iter = carAllExplorer.setBaseNode(2);
        assertTrue(iter.next());
        assertEquals(10, iter.get(carSpeedEnc), 1);
        assertTrue(iter.get(carAccessEnc));
        assertFalse(iter.getReverse(carAccessEnc));

        try {
            graph.edge(0, 1).setDistance(-1);
            fail();
        } catch (IllegalArgumentException ex) {
        }
    }

    @Test
    public void testEdgeProperties() {
        graph = createGHStorage();
        EdgeIteratorState iter1 = graph.edge(0, 1).setDistance(10).set(carAccessEnc, true, true);
        EdgeIteratorState iter2 = graph.edge(0, 2).setDistance(20).set(carAccessEnc, true, true);

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
        assertNotNull(iter);
        assertEquals(0, iter.getBaseNode());
        assertEquals(2, iter.getAdjNode());
        iter = graph.getEdgeIteratorState(edgeId, 1);
        assertNull(iter);
    }

    @Test
    public void testCreateDuplicateEdges() {
        graph = createGHStorage();
        graph.edge(2, 1).setDistance(12).set(carAccessEnc, true, true);
        graph.edge(2, 3).setDistance(12).set(carAccessEnc, true, true);
        graph.edge(2, 3).setDistance(13).set(carAccessEnc, true, false);
        assertEquals(3, getCountOut(2));

        // no exception
        graph.getEdgeIteratorState(1, 3);

        // raise exception
        try {
            graph.getEdgeIteratorState(4, 3);
            fail();
        } catch (Exception ex) {
        }
        try {
            graph.getEdgeIteratorState(-1, 3);
            fail();
        } catch (Exception ex) {
        }

        EdgeIterator iter = carOutExplorer.setBaseNode(2);
        assertTrue(iter.next());
        EdgeIteratorState oneIter = graph.getEdgeIteratorState(iter.getEdge(), 3);
        assertEquals(13, oneIter.getDistance(), 1e-6);
        assertEquals(2, oneIter.getBaseNode());
        assertTrue(oneIter.get(carAccessEnc));
        assertFalse(oneIter.getReverse(carAccessEnc));

        oneIter = graph.getEdgeIteratorState(iter.getEdge(), 2);
        assertEquals(13, oneIter.getDistance(), 1e-6);
        assertEquals(3, oneIter.getBaseNode());
        assertFalse(oneIter.get(carAccessEnc));
        assertTrue(oneIter.getReverse(carAccessEnc));

        graph.edge(3, 2).setDistance(14).set(carAccessEnc, true, true);
        assertEquals(4, getCountOut(2));
    }

    @Test
    public void testEdgeReturn() {
        graph = createGHStorage();
        EdgeIteratorState iter = graph.edge(4, 10).setDistance(100);
        iter.set(carAccessEnc, true, false);
        assertEquals(4, iter.getBaseNode());
        assertEquals(10, iter.getAdjNode());
        iter = graph.edge(14, 10).setDistance(100);
        iter.set(carAccessEnc, true, false);
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
        EdgeIteratorState edge = graph.edge(0, 4).setDistance(100).setWayGeometry(pointList);
        edge.set(carAccessEnc, true, false);
        pointList = Helper.createPointList(1, 5, 1, 6, 1, 7, 1, 8, 1, 9);
        edge = graph.edge(4, 10).setDistance(100).setWayGeometry(pointList);
        edge.set(carAccessEnc, true, false);
        pointList = Helper.createPointList(1, 13, 1, 12, 1, 11);
        edge = graph.edge(14, 0).setDistance(100).setWayGeometry(pointList);
        edge.set(carAccessEnc, true, false);

        EdgeIterator iter = carAllExplorer.setBaseNode(0);
        assertTrue(iter.next());
        assertEquals(14, iter.getAdjNode());
        assertPList(Helper.createPointList(1, 11, 1, 12, 1, 13.0), iter.fetchWayGeometry(FetchMode.PILLAR_ONLY));
        assertPList(Helper.createPointList(0.01, 0.01, 1, 11, 1, 12, 1, 13.0), iter.fetchWayGeometry(FetchMode.BASE_AND_PILLAR));
        assertPList(Helper.createPointList(1, 11, 1, 12, 1, 13.0, 0.14, 0.14), iter.fetchWayGeometry(FetchMode.PILLAR_AND_ADJ));
        assertPList(Helper.createPointList(0.01, 0.01, 1, 11, 1, 12, 1, 13.0, 0.14, 0.14), iter.fetchWayGeometry(FetchMode.ALL));

        assertTrue(iter.next());
        assertEquals(4, iter.getAdjNode());
        assertPList(Helper.createPointList(1, 1, 1, 2, 1, 3), iter.fetchWayGeometry(FetchMode.PILLAR_ONLY));
        assertPList(Helper.createPointList(0.01, 0.01, 1, 1, 1, 2, 1, 3), iter.fetchWayGeometry(FetchMode.BASE_AND_PILLAR));
        assertPList(Helper.createPointList(1, 1, 1, 2, 1, 3, 0.4, 0.4), iter.fetchWayGeometry(FetchMode.PILLAR_AND_ADJ));
        assertPList(Helper.createPointList(0.01, 0.01, 1, 1, 1, 2, 1, 3, 0.4, 0.4), iter.fetchWayGeometry(FetchMode.ALL));

        assertFalse(iter.next());

        iter = carOutExplorer.setBaseNode(0);
        assertTrue(iter.next());
        assertEquals(4, iter.getAdjNode());
        assertPList(Helper.createPointList(1, 1, 1, 2, 1, 3), iter.fetchWayGeometry(FetchMode.PILLAR_ONLY));
        assertFalse(iter.next());

        iter = carInExplorer.setBaseNode(10);
        assertTrue(iter.next());
        assertEquals(4, iter.getAdjNode());
        assertPList(Helper.createPointList(1, 9, 1, 8, 1, 7, 1, 6, 1, 5), iter.fetchWayGeometry(FetchMode.PILLAR_ONLY));
        assertPList(Helper.createPointList(0.99, 0.99, 1, 9, 1, 8, 1, 7, 1, 6, 1, 5), iter.fetchWayGeometry(FetchMode.BASE_AND_PILLAR));
        assertPList(Helper.createPointList(1, 9, 1, 8, 1, 7, 1, 6, 1, 5, 0.4, 0.4), iter.fetchWayGeometry(FetchMode.PILLAR_AND_ADJ));
        assertPList(Helper.createPointList(0.99, 0.99, 1, 9, 1, 8, 1, 7, 1, 6, 1, 5, 0.4, 0.4), iter.fetchWayGeometry(FetchMode.ALL));
        assertFalse(iter.next());
    }

    @Test
    public void testFootMix() {
        graph = createGHStorage();
        graph.edge(0, 1).setDistance((10)).set(footAccessEnc, true, true);
        graph.edge(0, 2).setDistance((10)).set(carAccessEnc, true, true);
        EdgeIteratorState edge = graph.edge(0, 3).setDistance(10);
        edge.set(footAccessEnc, true, true);
        edge.set(carAccessEnc, true, true);
        EdgeExplorer footOutExplorer = graph.createEdgeExplorer(AccessFilter.outEdges(footAccessEnc));
        assertEquals(GHUtility.asSet(3, 1), GHUtility.getNeighbors(footOutExplorer.setBaseNode(0)));
        assertEquals(GHUtility.asSet(3, 2), GHUtility.getNeighbors(carOutExplorer.setBaseNode(0)));
    }

    @Test
    public void testGetAllEdges() {
        graph = createGHStorage();
        graph.edge(0, 1).setDistance(2).set(carAccessEnc, true, true);
        graph.edge(3, 1).setDistance(1).set(carAccessEnc, true, false);
        graph.edge(3, 2).setDistance(1).set(carAccessEnc, true, false);

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
    public void testKVStorage() {
        graph = createGHStorage();
        EdgeIteratorState iter1 = graph.edge(0, 1).setDistance(10).set(carAccessEnc, true, true);
        iter1.setKeyValues(Map.of(STREET_NAME, new KValue("named street1")));

        EdgeIteratorState iter2 = graph.edge(0, 1).setDistance(10).set(carAccessEnc, true, true);
        iter2.setKeyValues(Map.of(STREET_NAME, new KValue("named street2")));

        assertEquals(graph.getEdgeIteratorState(iter1.getEdge(), iter1.getAdjNode()).getName(), "named street1");
        assertEquals(graph.getEdgeIteratorState(iter2.getEdge(), iter2.getAdjNode()).getName(), "named street2");
    }

    @Test
    public void test8AndMoreBytesForEdgeFlags() {
        BooleanEncodedValue access0Enc = new SimpleBooleanEncodedValue("car0_access", true);
        DecimalEncodedValue speed0Enc = new DecimalEncodedValueImpl("car0_speed", 29, 0.001, false);
        BooleanEncodedValue access1Enc = new SimpleBooleanEncodedValue("car1_access", true);
        DecimalEncodedValue speed1Enc = new DecimalEncodedValueImpl("car1_speed", 29, 0.001, false);

        EncodingManager manager = EncodingManager.start()
                .add(access0Enc).add(speed0Enc)
                .add(access1Enc).add(speed1Enc)
                .build();
        graph = new BaseGraph.Builder(manager).create();

        EdgeIteratorState edge = graph.edge(0, 1);
        IntsRef intsRef = manager.createEdgeFlags();
        intsRef.ints[0] = Integer.MAX_VALUE / 3;
        edge.setFlags(intsRef);
        assertEquals(Integer.MAX_VALUE / 3, intsRef.ints[0]);
        graph.close();

        graph = new BaseGraph.Builder(manager).create();


        edge = graph.edge(0, 1);
        GHUtility.setSpeed(99.123, true, true, access0Enc, speed0Enc, edge);
        assertEquals(99.123, edge.get(speed0Enc), 1e-3);
        EdgeIteratorState edgeIter = GHUtility.getEdge(graph, 1, 0);
        assertEquals(99.123, edgeIter.get(speed0Enc), 1e-3);
        assertTrue(edgeIter.get(access0Enc));
        assertTrue(edgeIter.getReverse(access0Enc));
        edge = graph.edge(2, 3);
        GHUtility.setSpeed(44.123, true, false, access1Enc, speed1Enc, edge);
        assertEquals(44.123, edge.get(speed1Enc), 1e-3);

        edgeIter = GHUtility.getEdge(graph, 3, 2);
        assertEquals(44.123, edgeIter.get(speed1Enc), 1e-3);
        assertEquals(44.123, edgeIter.getReverse(speed1Enc), 1e-3);
        assertFalse(edgeIter.get(access1Enc));
        assertTrue(edgeIter.getReverse(access1Enc));

        manager = EncodingManager.start()
                .add(new SimpleBooleanEncodedValue("car0_access", true)).add(new DecimalEncodedValueImpl("car0_speed", 29, 0.001, false))
                .add(new SimpleBooleanEncodedValue("car1_access", true)).add(new DecimalEncodedValueImpl("car1_speed", 29, 0.001, false))
                .add(new SimpleBooleanEncodedValue("car2_access", true)).add(new DecimalEncodedValueImpl("car2_speed", 30, 0.001, false))
                .build();
        graph = new BaseGraph.Builder(manager).create();
        edgeIter = graph.edge(0, 1).set(access0Enc, true, false);
        assertTrue(edgeIter.get(access0Enc));
        assertFalse(edgeIter.getReverse(access0Enc));
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
        assertEquals(Helper.createPointList3D(10, 27, 72, 11, 20, 1), GHUtility.getEdge(graph, 0, 1).fetchWayGeometry(FetchMode.PILLAR_ONLY));
        assertEquals(Helper.createPointList3D(10, 20, -10, 10, 27, 72, 11, 20, 1, 11, 2, 100), GHUtility.getEdge(graph, 0, 1).fetchWayGeometry(FetchMode.ALL));
        assertEquals(Helper.createPointList3D(11, 2, 100, 11, 20, 1, 10, 27, 72, 10, 20, -10), GHUtility.getEdge(graph, 1, 0).fetchWayGeometry(FetchMode.ALL));
    }

    @Test
    public void testDontGrowOnUpdate() {
        graph = createGHStorage(defaultGraphLoc, true);
        NodeAccess na = graph.getNodeAccess();
        assertTrue(na.is3D());
        na.setNode(0, 10, 10, 0);
        na.setNode(1, 11, 20, 1);
        na.setNode(2, 12, 12, 0.4);

        EdgeIteratorState iter2 = graph.edge(0, 1).setDistance(100).set(carAccessEnc, true, true);
        final BaseGraph baseGraph = graph.getBaseGraph();
        assertEquals(1, baseGraph.getMaxGeoRef());
        iter2.setWayGeometry(Helper.createPointList3D(1, 2, 3, 3, 4, 5, 5, 6, 7, 7, 8, 9));
        assertEquals(1 + 2 + 4 * 11, baseGraph.getMaxGeoRef());
        iter2.setWayGeometry(Helper.createPointList3D(1, 2, 3, 3, 4, 5, 5, 6, 7));
        assertEquals(1 + 2 + 4 * 11, baseGraph.getMaxGeoRef());
        iter2.setWayGeometry(Helper.createPointList3D(1, 2, 3, 3, 4, 5));
        assertEquals(1 + 2 + 4 * 11, baseGraph.getMaxGeoRef());
        iter2.setWayGeometry(Helper.createPointList3D(1, 2, 3));
        assertEquals(1 + 2 + 4 * 11, baseGraph.getMaxGeoRef());
        assertThrows(IllegalStateException.class, () -> iter2.setWayGeometry(Helper.createPointList3D(1.5, 1, 0, 2, 3, 0)));
        assertEquals(1 + 2 + 4 * 11, baseGraph.getMaxGeoRef());
        EdgeIteratorState iter1 = graph.edge(0, 2).setDistance(200).set(carAccessEnc, true, true);
        iter1.setWayGeometry(Helper.createPointList3D(3.5, 4.5, 0, 5, 6, 0));
        assertEquals(1 + 2 + 4 * 11 + (2 + 2 * 11), baseGraph.getMaxGeoRef());
    }

    @Test
    public void testDetachEdge() {
        graph = createGHStorage();
        graph.edge(0, 1).setDistance(2).set(carAccessEnc, true, true);
        graph.edge(0, 2).setDistance(2).set(carAccessEnc, true, true).setWayGeometry(Helper.createPointList(1, 2, 3, 4)).set(carAccessEnc, true, false);
        graph.edge(1, 2).setDistance(2).set(carAccessEnc, true, true);

        EdgeIterator iter = graph.createEdgeExplorer().setBaseNode(0);
        try {
            // currently not possible to detach without next, without introducing a new property inside EdgeIteratorImpl
            iter.detach(false);
            fail();
        } catch (Exception ex) {
        }

        iter.next();
        EdgeIteratorState edgeState02 = iter.detach(false);
        assertEquals(2, iter.getAdjNode());
        assertEquals(1, edgeState02.fetchWayGeometry(FetchMode.PILLAR_ONLY).getLat(0), 1e-1);
        assertEquals(2, edgeState02.getAdjNode());
        assertTrue(edgeState02.get(carAccessEnc));

        EdgeIteratorState edgeState20 = iter.detach(true);
        assertEquals(0, edgeState20.getAdjNode());
        assertEquals(2, edgeState20.getBaseNode());
        assertEquals(3, edgeState20.fetchWayGeometry(FetchMode.PILLAR_ONLY).getLat(0), 1e-1);
        assertFalse(edgeState20.get(carAccessEnc));
        assertEquals(GHUtility.getEdge(graph, 0, 2).getFlags(), edgeState02.getFlags());
        assertEquals(GHUtility.getEdge(graph, 2, 0).getFlags(), edgeState20.getFlags());

        iter.next();
        assertEquals(1, iter.getAdjNode());
        assertEquals(2, edgeState02.getAdjNode());
        assertEquals(2, edgeState20.getBaseNode());

        assertEquals(0, iter.fetchWayGeometry(FetchMode.PILLAR_ONLY).size());
        assertEquals(1, edgeState02.fetchWayGeometry(FetchMode.PILLAR_ONLY).getLat(0), 1e-1);
        assertEquals(3, edgeState20.fetchWayGeometry(FetchMode.PILLAR_ONLY).getLat(0), 1e-1);
    }

    private int getCountOut(int node) {
        return GHUtility.count(carOutExplorer.setBaseNode(node));
    }

    private int getCountIn(int node) {
        return GHUtility.count(carInExplorer.setBaseNode(node));
    }

    private int getCountAll(int node) {
        return GHUtility.count(carAllExplorer.setBaseNode(node));
    }
}
