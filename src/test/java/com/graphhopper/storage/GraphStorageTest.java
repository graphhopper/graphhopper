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
    public Graph createGraph(int size) {
        return new GraphStorage(new RAMDirectory("graphstorage")).createNew(size);
    }

    @Test
    public void testCreateDuplicateEdges() {
        Graph graph = createGraph(10);
        graph.edge(2, 1, 12, true);
        graph.edge(2, 3, 12, true);
        graph.edge(2, 3, 12, true);
        assertEquals(3, GraphUtility.count(graph.getOutgoing(2)));

        graph.edge(3, 2, 12, true);
        assertEquals(4, GraphUtility.count(graph.getOutgoing(2)));
    }

    @Test
    public void testIdenticalNodes() {
        Graph g = createGraph(2);
        g.edge(0, 0, 100, true);
        assertEquals(1, GraphUtility.count(g.getEdges(0)));

        g = createGraph(2);
        g.edge(0, 0, 100, false);
        g.edge(0, 0, 100, false);
        assertEquals(2, GraphUtility.count(g.getEdges(0)));
    }

    @Test
    public void testSave_and_fileFormat() throws IOException {
        String tmpDir = "./target/tmp/";
        Helper.deleteDir(new File(tmpDir));
        GraphStorage graph = new GraphStorage(new RAMDirectory(tmpDir, true)).createNew(10);
        graph.setNode(0, 10, 10);
        graph.setNode(1, 11, 20);
        graph.setNode(2, 12, 12);

        graph.edge(0, 1, 100, true);
        graph.edge(0, 2, 200, true);
        graph.edge(1, 2, 120, false);

        checkGraph(graph);
        graph.flush();

        graph = new GraphStorage(new MMapDirectory(tmpDir));
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
        GraphStorage g = new GraphStorage(new RAMDirectory()).createNew(10);
        g.edge(0, 1, 2, true);
        g.edge(3, 1, 1, false);
        g.edge(3, 2, 1, false);

        EdgeIterator iter = g.getAllEdges();
        assertTrue(iter.next());
        assertEquals(0, iter.fromNode());
        assertEquals(1, iter.node());
        assertEquals(2, iter.distance(), 1e-6);

        assertTrue(iter.next());
        assertEquals(1, iter.fromNode());
        assertEquals(3, iter.node());

        assertTrue(iter.next());
        assertEquals(2, iter.fromNode());
        assertEquals(3, iter.node());

        assertFalse(iter.next());
    }
}
