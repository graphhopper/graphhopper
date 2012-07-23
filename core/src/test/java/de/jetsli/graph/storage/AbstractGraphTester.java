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

import de.jetsli.graph.util.EdgeIdIterator;
import de.jetsli.graph.util.GraphUtility;
import org.junit.Test;
import static org.junit.Assert.*;
import static de.jetsli.graph.util.GraphUtility.*;
import java.util.Arrays;

/**
 *
 * @author Peter Karich, info@jetsli.de
 */
public abstract class AbstractGraphTester {

    abstract Graph createGraph(int size);

    @Test public void testCreateLocation() {
        Graph graph = createGraph(4);
        graph.edge(3, 1, 50, true);
        assertEquals(1, count(graph.getOutgoing(1)));

        graph.edge(1, 2, 100, true);
        assertEquals(2, count(graph.getOutgoing(1)));
    }

    @Test public void testEdges() {
        Graph graph = createGraph(5);

        graph.edge(2, 1, 12, true);
        assertEquals(1, count(graph.getOutgoing(2)));

        graph.edge(2, 3, 12, true);
        assertEquals(1, count(graph.getOutgoing(1)));
        assertEquals(2, count(graph.getOutgoing(2)));
        assertEquals(1, count(graph.getOutgoing(3)));
    }

    @Test public void testUnidirectional() {
        Graph g = createGraph(14);

        g.edge(1, 2, 12, false);
        g.edge(1, 11, 12, false);
        g.edge(11, 1, 12, false);
        g.edge(1, 12, 12, false);
        g.edge(3, 2, 112, false);
        EdgeIdIterator i = g.getOutgoing(2);
        assertFalse(i.next());

        i = g.getOutgoing(3);
        assertTrue(i.next());
        assertEquals(2, i.nodeId());
        assertFalse(i.next());

        i = g.getOutgoing(1);
        assertTrue(i.next());
        assertEquals(2, i.nodeId());
        assertTrue(i.next());
        assertEquals(11, i.nodeId());
        assertTrue(i.next());
        assertEquals(12, i.nodeId());
        assertFalse(i.next());
    }

    @Test public void testUpdateUnidirectional() {
        Graph g = createGraph(4);

        g.edge(1, 2, 12, false);
        g.edge(3, 2, 112, false);
        EdgeIdIterator i = g.getOutgoing(2);
        assertFalse(i.next());
        i = g.getOutgoing(3);
        assertTrue(i.next());
        assertEquals(2, i.nodeId());
        assertFalse(i.next());

        g.edge(2, 3, 112, false);
        i = g.getOutgoing(2);
        assertTrue(i.next());
        assertEquals(3, i.nodeId());
        i = g.getOutgoing(3);
        i.next();
        assertEquals(2, i.nodeId());
        assertFalse(i.next());
    }

    @Test
    public void testClone() {
        Graph g = createGraph(11);
        g.edge(1, 2, 10, true);
        g.setNode(0, 12, 23);
        g.edge(1, 3, 10, true);
        Graph clone = g.clone();
        assertEquals(g.getNodes(), clone.getNodes());
        assertEquals(count(g.getOutgoing(1)), count(clone.getOutgoing(1)));
        clone.edge(1, 4, 10, true);
        assertEquals(3, count(clone.getOutgoing(1)));
    }

    @Test
    public void testGetLocations() {
        Graph g = createGraph(11);
        g.setNode(0, 12, 23);
        g.setNode(1, 22, 23);
        assertEquals(2, g.getNodes());

        g.edge(0, 1, 10, true);
        assertEquals(2, g.getNodes());

        g.edge(0, 2, 10, true);
        assertEquals(3, g.getNodes());

        g = createGraph(11);
        assertEquals(0, g.getNodes());
    }

    @Test
    public void testAddLocation() {
        Graph g = createGraph(11);
        g.setNode(0, 12, 23);
        g.setNode(1, 38.33f, 235.3f);
        g.setNode(2, 6, 2339);
        g.setNode(3, 78, 89);
        g.setNode(4, 2, 1);
        g.setNode(5, 7, 5);
        g.edge(0, 1, 12, true);
        g.edge(0, 2, 212, true);
        g.edge(0, 3, 212, true);
        g.edge(0, 4, 212, true);
        g.edge(0, 5, 212, true);

        assertEquals(12f, g.getLatitude(0), 1e-9);
        assertEquals(23f, g.getLongitude(0), 1e-9);

        assertEquals(38.33f, g.getLatitude(1), 1e-9);
        assertEquals(235.3f, g.getLongitude(1), 1e-9);

        assertEquals(6, g.getLatitude(2), 1e-9);
        assertEquals(2339, g.getLongitude(2), 1e-9);

        assertEquals(78, g.getLatitude(3), 1e-9);
        assertEquals(89, g.getLongitude(3), 1e-9);

        assertEquals(1, count(g.getOutgoing(1)));
        assertEquals(5, count(g.getOutgoing(0)));
        try {
            assertEquals(0, count(g.getOutgoing(6)));
            // for now return empty iterator
            // assertFalse(true);
        } catch (Exception ex) {
        }
    }

    @Test public void testDirectional() {
        Graph g = createGraph(11);
        g.edge(1, 2, 12, true);
        g.edge(2, 3, 12, false);
        g.edge(3, 4, 12, false);
        g.edge(3, 5, 12, true);
        g.edge(6, 3, 12, false);

        assertEquals(1, count(g.getEdges(1)));
        assertEquals(1, count(g.getIncoming(1)));
        assertEquals(1, count(g.getOutgoing(1)));

        assertEquals(2, count(g.getEdges(2)));
        assertEquals(1, count(g.getIncoming(2)));
        assertEquals(2, count(g.getOutgoing(2)));

        assertEquals(4, count(g.getEdges(3)));
        assertEquals(3, count(g.getIncoming(3)));
        assertEquals(2, count(g.getOutgoing(3)));

        assertEquals(1, count(g.getEdges(4)));
        assertEquals(1, count(g.getIncoming(4)));
        assertEquals(0, count(g.getOutgoing(4)));

        assertEquals(1, count(g.getEdges(5)));
        assertEquals(1, count(g.getIncoming(5)));
        assertEquals(1, count(g.getOutgoing(5)));
    }

    @Test public void testDozendEdges() {
        Graph g = createGraph(11);
        g.edge(1, 2, 12, true);
        assertEquals(1, count(g.getEdges(1)));

        g.edge(1, 3, 13, false);
        assertEquals(2, count(g.getEdges(1)));

        g.edge(1, 4, 14, false);
        assertEquals(3, count(g.getEdges(1)));

        g.edge(1, 5, 15, false);
        assertEquals(4, count(g.getEdges(1)));

        g.edge(1, 6, 16, false);
        assertEquals(5, count(g.getEdges(1)));

        g.edge(1, 7, 16, false);
        assertEquals(6, count(g.getEdges(1)));

        g.edge(1, 8, 16, false);
        assertEquals(7, count(g.getEdges(1)));

        g.edge(1, 9, 16, false);
        assertEquals(8, count(g.getEdges(1)));
        assertEquals(8, count(g.getOutgoing(1)));
        assertEquals(1, count(g.getIncoming(1)));
        assertEquals(1, count(g.getIncoming(2)));
    }

    @Test
    public void testCheckFirstNode() {
        Graph g = createGraph(2);
        assertEquals(0, count(g.getEdges(1)));
        g.edge(0, 1, 12, true);
        assertEquals(1, count(g.getEdges(1)));
    }

    @Test
    public void testDeleteNode() {
        testDeleteNodes(21);
        testDeleteNodes(6);
    }

    public void testDeleteNodes(int fillToSize) {
        Graph g = createGraph(11);
        g.setNode(0, 12, 23);
        g.setNode(1, 38.33f, 135.3f);
        g.setNode(2, 3, 3);
        g.setNode(3, 78, 89);
        g.setNode(4, 2, 1);
        g.setNode(5, 2.5f, 1);

        int deleted = 2;
        for (int i = 6; i < fillToSize; i++) {
            g.setNode(i, i * 1.5, i * 1.6);
            if (i % 3 == 0) {
                g.markNodeDeleted(i);
                deleted++;
            } else {
                // connect to
                // ... a deleted node
                g.edge(i, 0, 10 * i, true);
                // ... a non-deleted and non-moved node
                g.edge(i, 2, 10 * i, true);
                // ... a moved node
                g.edge(i, fillToSize - 1, 10 * i, true);
            }
        }

        g.edge(0, 1, 10, true);
        g.edge(0, 3, 20, false);
        g.edge(3, 5, 20, true);
        g.edge(1, 5, 20, false);

        g.markNodeDeleted(0);
        g.markNodeDeleted(2);
        // no deletion happend
        assertEquals(fillToSize, g.getNodes());

        assertEquals(Arrays.asList(), GraphUtility.getProblems(g));

        // now actually perform deletion
        g.optimize();

        assertEquals(Arrays.asList(), GraphUtility.getProblems(g));

        assertEquals(fillToSize - deleted, g.getNodes());
        int id1 = getIdOf(g, 38.33f);
        assertEquals(135.3f, g.getLongitude(id1), 1e-4);
        assertTrue(containsLatitude(g, g.getEdges(id1), 2.5));
        assertFalse(containsLatitude(g, g.getEdges(id1), 12));

        int id3 = getIdOf(g, 78);
        assertEquals(89, g.getLongitude(id3), 1e-4);
        assertTrue(containsLatitude(g, g.getEdges(id3), 2.5));
        assertFalse(containsLatitude(g, g.getEdges(id3), 12));
    }

    public boolean containsLatitude(Graph g, EdgeIdIterator iter, double latitude) {
        while (iter.next()) {
            if (Math.abs(g.getLatitude(iter.nodeId()) - latitude) < 1e-4)
                return true;
        }
        return false;
    }

    public int getIdOf(Graph g, double latitude) {
        int s = g.getNodes();
        for (int i = 0; i < s; i++) {
            if (Math.abs(g.getLatitude(i) - latitude) < 1e-4)
                return i;
        }
        return -1;
    }

    @Test
    public void testTestSimpleDelete() {
        Graph g = createGraph(11);
        g.setNode(0, 12, 23);
        g.setNode(1, 38.33f, 135.3f);
        g.setNode(2, 3, 3);
        g.setNode(3, 78, 89);

        g.edge(3, 0, 20, true);
        g.edge(5, 0, 20, true);
        g.edge(5, 3, 20, true);

        g.markNodeDeleted(0);
        g.markNodeDeleted(3);

        assertEquals(6, g.getNodes());
        assertEquals(Arrays.asList(), GraphUtility.getProblems(g));

        // now actually perform deletion
        g.optimize();

        assertEquals(4, g.getNodes());
        assertEquals(Arrays.asList(), GraphUtility.getProblems(g));
        // shouldn't change anything
        g.optimize();
        assertEquals(4, g.getNodes());
        assertEquals(Arrays.asList(), GraphUtility.getProblems(g));
    }
}
