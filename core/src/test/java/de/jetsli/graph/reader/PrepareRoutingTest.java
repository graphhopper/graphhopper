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
package de.jetsli.graph.reader;

import de.jetsli.graph.coll.MyBitSet;
import de.jetsli.graph.coll.MyTBitSet;
import de.jetsli.graph.storage.Graph;
import de.jetsli.graph.storage.MemoryGraphSafe;
import de.jetsli.graph.util.EdgeIterator;
import de.jetsli.graph.util.GraphUtility;
import de.jetsli.graph.util.XFirstSearch;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.*;
import static org.junit.Assert.*;

/**
 *
 * @author Peter Karich
 */
public class PrepareRoutingTest {

    Graph createGraph(int size) {
        return new MemoryGraphSafe(size);
    }

    Graph createSubnetworkTestGraph() {
        Graph g = createGraph(20);
        // big network
        g.edge(1, 2, 1, true);
        g.edge(1, 4, 1, false);
        g.edge(1, 8, 1, true);
        g.edge(2, 4, 1, true);
        g.edge(8, 4, 1, false);
        g.edge(8, 11, 1, true);
        g.edge(12, 11, 1, true);
        g.edge(9, 12, 1, false);

        // large network
        g.edge(0, 13, 1, true);
        g.edge(0, 3, 1, true);
        g.edge(0, 7, 1, true);
        g.edge(3, 7, 1, true);
        g.edge(3, 5, 1, true);
        g.edge(13, 5, 1, true);

        // small network
        g.edge(6, 14, 1, true);
        g.edge(10, 14, 1, true);
        return g;
    }

    @Test
    public void testFindSubnetworks() {
        Graph g = createSubnetworkTestGraph();
        PrepareRouting instance = new PrepareRouting(g);
        Map<Integer, Integer> map = instance.findSubnetworks();

        assertEquals(3, map.size());
        // start is at 0 => large network
        assertEquals(5, (int) map.get(0));
        // next smallest and unvisited node is 1 => big network
        assertEquals(7, (int) map.get(1));
        assertEquals(3, (int) map.get(6));
    }

    @Test
    public void testKeepLargestNetworks() {
        Graph g = createSubnetworkTestGraph();
        PrepareRouting instance = new PrepareRouting(g);
        Map<Integer, Integer> map = instance.findSubnetworks();
        instance.keepLargestNetwork(map);
        g.optimize();

        assertEquals(7, g.getNodes());
        assertEquals(Arrays.asList(), GraphUtility.getProblems(g));
        map = instance.findSubnetworks();
        assertEquals(1, map.size());
        assertEquals(7, (int) map.get(0));
    }

    @Test
    public void testSimpleShortcuts() {
        Graph g = createGraph(20);
        // 1
        // 0-2-4-5
        // 3
        g.edge(0, 1, 1, true);
        g.edge(0, 2, 2, true);
        g.edge(0, 3, 3, true);
        g.edge(2, 4, 4, true);
        g.edge(4, 5, 5, true);

        // assert additional 0, 5
        assertFalse(GraphUtility.contains(g.getEdges(0), 5));
        assertFalse(GraphUtility.contains(g.getEdges(5), 0));
        new PrepareRouting(g).createShortcuts();
        assertTrue(GraphUtility.contains(g.getEdges(0), 5));
        EdgeIterator iter = GraphUtility.until(g.getEdges(0), 5);
        assertEquals(11, iter.distance(), 1e-5);
        assertEquals(12, GraphUtility.countEdges(g));

        // 1
        // 0->2->4->5
        // 3
        g = createGraph(20);
        g.edge(0, 1, 1, false);
        g.edge(0, 2, 2, false);
        g.edge(0, 3, 3, false);
        g.edge(2, 4, 4, false);
        g.edge(4, 5, 5, false);
        assertDirected0_5(g);
        assertEquals(6, GraphUtility.countEdges(g));

        g = createGraph(20);
        g.edge(0, 1, 1, false);
        g.edge(0, 2, 2, false);
        g.edge(0, 3, 3, false);
        g.edge(2, 4, 4, false);
        g.edge(4, 5, 5, false);
        g.edge(6, 5, 6, false);
        assertDirected0_5(g);
        assertEquals(7, GraphUtility.countEdges(g));
    }

    void assertDirected0_5(Graph g) {
        // assert 0->5 but not 5->0
        assertFalse(GraphUtility.contains(g.getEdges(0), 5));
        assertFalse(GraphUtility.contains(g.getEdges(5), 0));
        new PrepareRouting(g).createShortcuts();
        assertTrue(GraphUtility.contains(g.getOutgoing(0), 5));
        assertFalse(GraphUtility.contains(g.getOutgoing(5), 0));
    }

    // TODO @Test
    public void testShortcutUnpacking() {
        // store skipped first node along with the shortcut to unpack short cut!!
    }

    // TODO @Test
    public void testChangeExistingShortcut() {
        //
        // |     |
        // 0-1-2-3
        // |     |
        // 7-8-9-10
        // |     |
        //
        // => 0-3 shortcut exists => 7-10 reduces existing shortcut 
    }

    // prepare-routing.svg
    @Test
    public void testIntroduceShortcuts() {
        final Graph g = createGraph(20);
        g.edge(0, 1, 1, true);
        g.edge(0, 2, 1, true);
        g.edge(1, 2, 1, true);
        g.edge(2, 3, 1, true);
        g.edge(1, 4, 1, true);
        g.edge(2, 9, 1, true);
        g.edge(9, 3, 1, true);
        g.edge(10, 3, 1, true);
        g.edge(4, 5, 1, true);
        g.edge(5, 6, 1, true);
        g.edge(6, 7, 1, true);
        g.edge(7, 8, 1, true);
        g.edge(8, 9, 1, true);
        g.edge(4, 11, 1, true);
        g.edge(9, 14, 1, true);
        g.edge(10, 14, 1, true);
        g.edge(11, 12, 1, true);
        g.edge(12, 15, 1, true);
        g.edge(12, 13, 1, true);
        g.edge(13, 16, 1, true);
        g.edge(15, 16, 2, true);
        g.edge(14, 16, 1, true);

        new PrepareRouting(g).createShortcuts();
        g.optimize();

        assertEquals(22 * 2 + 4 * 2, GraphUtility.countEdges(g));

        assertTrue(GraphUtility.contains(g.getOutgoing(12), 16));
        EdgeIterator iter = GraphUtility.until(g.getOutgoing(12), 16);
        //TODO assertEquals(2, iter.distance(), 1e-4);

        assertTrue(GraphUtility.contains(g.getOutgoing(0), 1));
        iter = GraphUtility.until(g.getOutgoing(0), 1);
        assertEquals(1, iter.distance(), 1e-4);

        assertTrue(GraphUtility.contains(g.getOutgoing(2), 9));
        iter = GraphUtility.until(g.getOutgoing(2), 9);
        assertEquals(1, iter.distance(), 1e-4);

        assertTrue(GraphUtility.contains(g.getOutgoing(4), 9));
        iter = GraphUtility.until(g.getOutgoing(4), 9);
        assertEquals(5, iter.distance(), 1e-4);
    }
}
