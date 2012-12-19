/*
 *  Copyright 2012 Peter Karich 
 * 
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
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
import com.graphhopper.util.shapes.BBox;
import java.io.File;
import java.io.IOException;
import static org.junit.Assert.*;
import org.junit.Test;

/**
 *
 * @author Peter Karich
 */
public class GraphStorageTest extends AbstractGraphTester {

    @Override
    public GraphStorage createGraph(int size) {
        return newGraph(new RAMDirectory(location)).setSegmentSize(size / 2).createNew(size);
    }

    public GraphStorage newGraph(Directory dir) {
        // reduce segment size in order to test the case where multiple segments come into the game
        return new GraphStorage(dir);
    }

    @Test
    public void testSave_and_fileFormat() throws IOException {
        Helper.deleteDir(new File(location));
        GraphStorage graph = newGraph(new RAMDirectory(location, true)).createNew(10);
        graph.setNode(0, 10, 10);
        graph.setNode(1, 11, 20);
        graph.setNode(2, 12, 12);

        graph.edge(0, 1, 100, true);
        graph.edge(0, 2, 200, true);
        graph.edge(1, 2, 120, false);

        checkGraph(graph);
        graph.flush();

        graph = newGraph(new MMapDirectory(location));
        assertTrue(graph.loadExisting());
        assertEquals(3, graph.getNodes());
        assertEquals(3, graph.getNodes());
        checkGraph(graph);

        graph.edge(3, 4, 123, true);
        checkGraph(graph);
    }

    protected void checkGraph(Graph g) {
        assertEquals(new BBox(10, 20, 10, 12), g.getBounds());
        assertEquals(10, g.getLatitude(0), 1e-2);
        assertEquals(10, g.getLongitude(0), 1e-2);
        assertEquals(2, GraphUtility.count(g.getOutgoing(0)));
        assertTrue(GraphUtility.contains(g.getOutgoing(0), 1, 2));

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
        GraphStorage g = newGraph(new RAMDirectory()).createNew(10);
        g.edge(0, 1, 2, true);
        g.edge(3, 1, 1, false);
        g.edge(3, 2, 1, false);

        EdgeIterator iter = g.getAllEdges();
        assertTrue(iter.next());
        int edgeId = iter.edge();
        assertEquals(0, iter.baseNode());
        assertEquals(1, iter.node());
        assertEquals(2, iter.distance(), 1e-6);

        assertTrue(iter.next());
        int edgeId2 = iter.edge();
        assertEquals(1, edgeId2 - edgeId);
        assertEquals(1, iter.baseNode());
        assertEquals(3, iter.node());

        assertTrue(iter.next());
        assertEquals(2, iter.baseNode());
        assertEquals(3, iter.node());

        assertFalse(iter.next());
    }
}
