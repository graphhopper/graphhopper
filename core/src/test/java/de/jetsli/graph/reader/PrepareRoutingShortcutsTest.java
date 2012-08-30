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

import de.jetsli.graph.storage.Graph;
import de.jetsli.graph.storage.PriorityGraph;
import de.jetsli.graph.storage.PriorityGraphImpl;
import de.jetsli.graph.util.EdgeIterator;
import de.jetsli.graph.util.GraphUtility;
import org.junit.*;
import static org.junit.Assert.*;

/**
 *
 * @author Peter Karich
 */
public class PrepareRoutingShortcutsTest {

    PriorityGraph createGraph(int size) {
        return new PriorityGraphImpl(size);
    }

    @Test
    public void testSimpleShortcuts() {
        PriorityGraph g = createGraph(20);
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
        new PrepareRoutingShortcuts(g).doWork();
        assertTrue(GraphUtility.contains(g.getEdges(0), 5));
        EdgeIterator iter = GraphUtility.until(g.getEdges(0), 5);
        assertEquals(11, iter.distance(), 1e-5);
        // TODO the shortcut 0-4 is introduced as 5 gets a second edge from the 0-5 shortcut        
//        assertEquals((5 + 1) * 2, GraphUtility.countEdges(g));

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
        assertEquals(5 + 1, GraphUtility.countEdges(g));

        g = createGraph(20);
        g.edge(0, 1, 1, false);
        g.edge(0, 2, 2, false);
        g.edge(0, 3, 3, false);
        g.edge(2, 4, 4, false);
        g.edge(4, 5, 5, false);
        g.edge(6, 5, 6, false);
        assertDirected0_5(g);
        assertEquals(6 + 1, GraphUtility.countEdges(g));
    }

    void assertDirected0_5(PriorityGraph g) {
        // assert 0->5 but not 5->0
        assertFalse(GraphUtility.contains(g.getEdges(0), 5));
        assertFalse(GraphUtility.contains(g.getEdges(5), 0));
        new PrepareRoutingShortcuts(g).doWork();
        assertTrue(GraphUtility.contains(g.getOutgoing(0), 5));
        assertFalse(GraphUtility.contains(g.getOutgoing(5), 0));
    }

    // TODO @Test
    public void testShortcutUnpacking() {
        // store skipped first node along with the shortcut to unpack short cut!!
    }


    @Test
    public void testChangeExistingShortcut() {
        PriorityGraph g = createGraph(20);
        initBiGraph(g);
        
        new PrepareRoutingShortcuts(g).doWork();
        assertEquals(10 * 2 + 1 * 2, GraphUtility.countEdges(g));
        EdgeIterator iter = GraphUtility.until(g.getEdges(6), 3);
        assertEquals(40, iter.distance(), 1e-4);
    }

    // 0-1-2-3-4
    // |     / |
    // |    8  |
    // \   /   /
    //  7-6-5-/
    void initBiGraph(Graph graph) {
        graph.edge(0, 1, 100, true);
        graph.edge(1, 2, 1, true);
        graph.edge(2, 3, 1, true);
        graph.edge(3, 4, 1, true);
        graph.edge(4, 5, 25, true);
        graph.edge(5, 6, 25, true);
        graph.edge(6, 7, 5, true);
        graph.edge(7, 0, 5, true);
        graph.edge(3, 8, 20, true);
        graph.edge(8, 6, 20, true);
    }
    
    @Test
    public void testMultiTypeShortcuts() {
        PriorityGraph g = createGraph(20);
        g.edge(0, 1, 10, EdgeFlags.create(30, true));
        g.edge(1, 2, 10, EdgeFlags.create(30, true));
        g.edge(2, 3, 10, EdgeFlags.create(30, true));
        g.edge(0, 4, 20, EdgeFlags.create(120, true));
        g.edge(4, 3, 20, EdgeFlags.create(120, true));
        new PrepareRoutingShortcuts(g).doWork();

        assertEquals(5 * 2 + 2 * 2, GraphUtility.countEdges(g));

        EdgeIterator iter = GraphUtility.until(g.getEdges(0), 3);
        assertEquals(30, iter.distance(), 1e-4);

        iter = GraphUtility.until(iter, 3);
        assertEquals(40, iter.distance(), 1e-4);
    }

    // prepare-routing.svg
    @Test
    public void testIntroduceShortcuts() {
        final PriorityGraph g = createGraph(20);
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

        new PrepareRoutingShortcuts(g).doWork();
        assertEquals(22 * 2 + 4 * 2, GraphUtility.countEdges(g));

        assertTrue(GraphUtility.contains(g.getOutgoing(12), 16));
        EdgeIterator iter = GraphUtility.until(g.getOutgoing(12), 16);
        assertEquals(2, iter.distance(), 1e-4);

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
