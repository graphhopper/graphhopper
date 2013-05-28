/*
 *  Licensed to Peter Karich under one or more contributor license 
 *  agreements. See the NOTICE file distributed with this work for 
 *  additional information regarding copyright ownership.
 * 
 *  Peter Karich licenses this file to you under the Apache License, 
 *  Version 2.0 (the "License"); you may not use this file except 
 *  in compliance with the License. You may obtain a copy of the 
 *  License at
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

import com.graphhopper.util.EdgeIterator;
import com.graphhopper.util.GHUtility;
import com.graphhopper.util.Helper;
import com.graphhopper.util.shapes.BBox;
import java.io.IOException;
import java.util.Arrays;
import static org.junit.Assert.*;
import org.junit.Test;

/**
 *
 * @author Peter Karich
 */
public class GraphStorageTest extends AbstractGraphTester {

    private GraphStorage gs;

    @Override
    public void setUp() {
        super.setUp();
        if (gs != null) {
            gs.close();
        }
    }

    @Override
    public GraphStorage createGraph(String location, int size) {
        // reduce segment size in order to test the case where multiple segments come into the game
        return newGraph(new RAMDirectory(location)).segmentSize(size / 2).create(size);
    }

    protected GraphStorage newGraph(Directory dir) {
        return new GraphStorage(dir);
    }

    protected GraphStorage createGraphStorage(Directory dir) {
        return newGraph(dir).create(defaultSize);
    }

    @Test
    public void testNoCreateCalled() throws IOException {
        gs = new GraphBuilder().build();
        try {
            gs.ensureNodeIndex(123);
            assertFalse("IllegalStateException should be raised", true);
        } catch (IllegalStateException ex) {
            assertTrue(true);
        } catch (Exception ex) {
            assertFalse("IllegalStateException should be raised", true);
        }
    }

    @Test
    public void testSave_and_fileFormat() throws IOException {
        GraphStorage graph = createGraphStorage(new RAMDirectory(defaultGraph, true));
        graph.setNode(0, 10, 10);
        graph.setNode(1, 11, 20);
        graph.setNode(2, 12, 12);

        graph.edge(0, 1, 100, true).wayGeometry(Helper.createPointList(1.5, 1, 2, 3));
        graph.edge(0, 2, 200, true).wayGeometry(Helper.createPointList(3.5, 4.5, 5, 6));
        graph.edge(9, 10, 200, true);
        graph.edge(9, 11, 200, true);
        graph.edge(1, 2, 120, false);

        checkGraph(graph);
        graph.flush();
        graph.close();

        graph = newGraph(new MMapDirectory(defaultGraph));
        assertTrue(graph.loadExisting());
        assertEquals(12, graph.nodes());
        checkGraph(graph);

        graph.edge(3, 4, 123, true).wayGeometry(Helper.createPointList(4.4, 5.5, 6.6, 7.7));
        checkGraph(graph);
        graph.close();
    }

    protected void checkGraph(Graph g) {
        assertEquals(new BBox(10, 20, 10, 12), g.bounds());
        assertEquals(10, g.getLatitude(0), 1e-2);
        assertEquals(10, g.getLongitude(0), 1e-2);
        assertEquals(2, GHUtility.count(g.getEdges(0, carOutFilter)));
        assertEquals(Arrays.asList(1, 2), GHUtility.neighbors(g.getEdges(0, carOutFilter)));

        EdgeIterator iter = g.getEdges(0, carOutFilter);
        assertTrue(iter.next());
        assertEquals(Helper.createPointList(1.5, 1, 2, 3), iter.wayGeometry());

        assertTrue(iter.next());
        assertEquals(Helper.createPointList(3.5, 4.5, 5, 6), iter.wayGeometry());

        assertEquals(11, g.getLatitude(1), 1e-2);
        assertEquals(20, g.getLongitude(1), 1e-2);
        assertEquals(2, GHUtility.count(g.getEdges(1, carOutFilter)));
        assertEquals(Arrays.asList(0, 2), GHUtility.neighbors(g.getEdges(1, carOutFilter)));

        assertEquals(12, g.getLatitude(2), 1e-2);
        assertEquals(12, g.getLongitude(2), 1e-2);
        assertEquals(1, GHUtility.count(g.getEdges(2, carOutFilter)));
        assertEquals(Arrays.asList(0), GHUtility.neighbors(g.getEdges(2, carOutFilter)));
    }

    @Test
    public void internalDisconnect() {
        gs = (GraphStorage) createGraph();
        EdgeIterator iter0 = gs.edge(0, 1, 10, true);
        EdgeIterator iter1 = gs.edge(1, 2, 10, true);
        gs.edge(0, 3, 10, true);

        assertEquals(Arrays.asList(1, 3), GHUtility.neighbors(gs.getEdges(0)));
        assertEquals(Arrays.asList(0, 2), GHUtility.neighbors(gs.getEdges(1)));
        // remove edge "1-2" but only from 1
        gs.internalEdgeDisconnect(iter1.edge(), (long) iter0.edge() * gs.edgeEntrySize, iter1.baseNode(), iter1.adjNode());
        assertEquals(Arrays.asList(0), GHUtility.neighbors(gs.getEdges(1)));
        // let 0 unchanged -> no side effects
        assertEquals(Arrays.asList(1, 3), GHUtility.neighbors(gs.getEdges(0)));
    }

    @Test
    public void testEnsureSize() {
        Directory dir = new RAMDirectory();
        gs = new GraphStorage(dir).create(defaultSize);
        int testIndex = dir.findCreate("edges").segmentSize() * 3;
        gs.edge(0, testIndex, 10, true);
    }
}
