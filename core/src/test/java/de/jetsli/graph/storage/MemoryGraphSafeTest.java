/*
 *  Copyright 2012 Peter Karich info@jetsli.de
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
package de.jetsli.graph.storage;

import java.io.IOException;
import org.junit.Test;
import static org.junit.Assert.*;
import static de.jetsli.graph.util.GraphUtility.*;
import de.jetsli.graph.util.*;
import java.io.File;

/**
 *
 * @author Peter Karich
 */
public class MemoryGraphSafeTest extends AbstractGraphTester {

    @Override
    Graph createGraph(int size) {
        return new MemoryGraphSafe(size);
    }

    @Test
    public void testCreateDuplicateEdges() {
        Graph graph = createGraph(10);
        graph.edge(2, 1, 12, true);
        graph.edge(2, 3, 12, true);
        graph.edge(2, 3, 12, true);
        assertEquals(3, count(graph.getOutgoing(2)));

        graph.edge(3, 2, 12, true);
        assertEquals(4, count(graph.getOutgoing(2)));
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
    public void testSave() throws IOException {
        String tmpDir = "/tmp/memory-graph-safe";
        Helper.deleteDir(new File(tmpDir));
        SaveableGraph graph = new MemoryGraphSafe(tmpDir, 3, 3);
        graph.setNode(0, 10, 10);
        graph.setNode(1, 11, 20);
        graph.setNode(2, 12, 12);

        graph.edge(0, 1, 100, true);
        graph.edge(0, 2, 200, true);
        graph.edge(1, 2, 120, false);

        checkGraph(graph);
        graph.close();

        graph = new MemoryGraphSafe(tmpDir, 1003, 103);
        // no need here assertTrue(mmgraph.loadExisting());
        assertEquals(3, graph.getNodes());
        assertEquals(3, graph.getNodes());
        checkGraph(graph);

        graph.edge(3, 4, 123, true);
        checkGraph(graph);
    }

    protected void checkGraph(Graph g) {
        assertEquals(10, g.getLatitude(0), 1e-2);
        assertEquals(10, g.getLongitude(0), 1e-2);
        assertEquals(2, count(g.getOutgoing(0)));
        assertTrue(contains(g.getOutgoing(0), 1, 2));

        assertEquals(11, g.getLatitude(1), 1e-2);
        assertEquals(20, g.getLongitude(1), 1e-2);
        assertEquals(2, count(g.getOutgoing(1)));
        assertTrue(contains(g.getOutgoing(1), 0, 2));

        assertEquals(12, g.getLatitude(2), 1e-2);
        assertEquals(12, g.getLongitude(2), 1e-2);
        assertEquals(1, count(g.getOutgoing(2)));
        assertTrue(contains(g.getOutgoing(2), 0));
    }

    @Test
    public void testEdgesSegments() throws IOException {
        MemoryGraphSafe g = new MemoryGraphSafe(1000);
        assertEquals(1, g.getSegments());
        assertEquals(1 << 13, g.getSegmentSize());
        // minus one because we create a node "i+1"        
        int max = 1024 * 10 - 1;
        for (int i = 0; i < max; i++) {
            g.edge(i, i + 1, i * 10, true);
        }

        assertEquals(1, GraphUtility.count(g.getEdges(0)));
        assertEquals(2, GraphUtility.count(g.getEdges(1)));
        assertEquals(2, GraphUtility.count(g.getEdges(10238)));
        assertEquals(1, GraphUtility.count(g.getEdges(10239)));
        assertEquals(1 << 13, g.getSegmentSize());
        assertEquals(8, g.getSegments());
    }
}
