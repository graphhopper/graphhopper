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
import com.graphhopper.util.GraphUtility;
import com.graphhopper.util.Helper;
import com.graphhopper.util.RawEdgeIterator;
import com.graphhopper.util.shapes.BBox;
import java.io.IOException;
import static org.junit.Assert.*;
import org.junit.Test;

/**
 *
 * @author Peter Karich
 */
public class GraphStorageTest extends AbstractGraphTester {

    @Override
    public GraphStorage createGraph(String location, int size) {
        // reduce segment size in order to test the case where multiple segments come into the game
        return newGraph(new RAMDirectory(location)).segmentSize(size / 2).createNew(size);
    }

    protected GraphStorage newGraph(Directory dir) {
        return new GraphStorage(dir);
    }

    protected GraphStorage createGraphStorage(Directory dir) {
        return newGraph(dir).createNew(defaultSize);
    }

    @Test
    public void testNoCreateCalled() throws IOException {
        GraphStorage gs = new GraphBuilder().build();
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

        graph.edge(0, 1, 100, true).wayGeometry(Helper.createPointList(1, 1, 2, 3));
        graph.edge(0, 2, 200, true);
        graph.edge(1, 2, 120, false);

        checkGraph(graph);
        graph.flush();

        graph = newGraph(new MMapDirectory(defaultGraph));
        assertTrue(graph.loadExisting());
        assertEquals(3, graph.nodes());
        assertEquals(3, graph.nodes());
        checkGraph(graph);

        graph.edge(3, 4, 123, true);
        checkGraph(graph);
    }

    protected void checkGraph(Graph g) {
        assertEquals(new BBox(10, 20, 10, 12), g.bounds());
        assertEquals(10, g.getLatitude(0), 1e-2);
        assertEquals(10, g.getLongitude(0), 1e-2);
        assertEquals(2, GraphUtility.count(g.getOutgoing(0)));
        assertTrue(GraphUtility.contains(g.getOutgoing(0), 1, 2));

        EdgeIterator iter = g.getOutgoing(0);
        assertTrue(iter.next());
        assertEquals(Helper.createPointList(1, 1, 2, 3), iter.wayGeometry());

        assertEquals(11, g.getLatitude(1), 1e-2);
        assertEquals(20, g.getLongitude(1), 1e-2);
        assertEquals(2, GraphUtility.count(g.getOutgoing(1)));
        assertTrue(GraphUtility.contains(g.getOutgoing(1), 0, 2));

        assertEquals(12, g.getLatitude(2), 1e-2);
        assertEquals(12, g.getLongitude(2), 1e-2);
        assertEquals(1, GraphUtility.count(g.getOutgoing(2)));
        assertTrue(GraphUtility.contains(g.getOutgoing(2), 0));
    }

    @Test
    public void testGetAllEdges() {
        Graph g = createGraph();
        g.edge(0, 1, 2, true);
        g.edge(3, 1, 1, false);
        g.edge(3, 2, 1, false);

        RawEdgeIterator iter = g.allEdges();
        assertTrue(iter.next());
        int edgeId = iter.edge();
        assertEquals(0, iter.nodeA());
        assertEquals(1, iter.nodeB());
        assertEquals(2, iter.distance(), 1e-6);

        assertTrue(iter.next());
        int edgeId2 = iter.edge();
        assertEquals(1, edgeId2 - edgeId);
        assertEquals(1, iter.nodeA());
        assertEquals(3, iter.nodeB());

        assertTrue(iter.next());
        assertEquals(2, iter.nodeA());
        assertEquals(3, iter.nodeB());

        assertFalse(iter.next());
    }
}
