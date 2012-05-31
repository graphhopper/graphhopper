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

import de.jetsli.graph.reader.CalcDistance;
import java.util.Iterator;
import org.junit.Test;
import static org.junit.Assert.*;
import static de.jetsli.graph.util.MyIteratorable.*;

/**
 *
 * @author Peter Karich, info@jetsli.de
 */
public abstract class AbstractGraphTester {

    abstract Graph createGraph(int size);

    @Test public void testCreateLocation() {
        Graph graph = createGraph(4);
        graph.edge(3, 1, 50, true);
        graph.edge(1, 2, 100, true);

        assertEquals(2, count(graph.getOutgoing(1)));
    }

    @Test public void testEdges() {
        Graph graph = createGraph(5);

        graph.edge(2, 1, 12, true);
        assertEquals(1, count(graph.getOutgoing(2)));

        graph.edge(2, 3, 12, true);
        assertEquals(2, count(graph.getOutgoing(2)));

        graph.edge(2, 3, 12, true);
        assertEquals(2, count(graph.getOutgoing(2)));

        graph.edge(3, 2, 12, true);
        assertEquals(2, count(graph.getOutgoing(2)));

//        assertEquals(0, count(graph.getOutgoing(a1.id())));        
        assertEquals(1, count(graph.getOutgoing(1)));
        assertEquals(2, count(graph.getOutgoing(2)));
        assertEquals(1, count(graph.getOutgoing(3)));
    }

    // assume the following behaviour which allows the graph to stored bidirections more efficient
    @Test public void testOverwriteWillResultInSymetricUpdateOfEdgeWeight() {
        Graph g = createGraph(3);

        g.edge(1, 2, 12, true);
        DistEntry de = g.getOutgoing(2).iterator().next();
        assertEquals(12, de.distance, 1e-7);
        de = g.getOutgoing(1).iterator().next();
        assertEquals(12, de.distance, 1e-7);
        g.edge(1, 2, 11, false);
        de = g.getOutgoing(2).iterator().next();
        assertEquals(1, de.node);
        assertEquals(11, de.distance, 1e-7);
        de = g.getOutgoing(1).iterator().next();
        assertEquals(2, de.node);
        assertEquals(11, de.distance, 1e-7);
        g.edge(1, 2, 13, true);
        de = g.getOutgoing(2).iterator().next();
        assertEquals(1, de.node);
        assertEquals(13, de.distance, 1e-7);
        de = g.getOutgoing(1).iterator().next();
        assertEquals(2, de.node);
        assertEquals(13, de.distance, 1e-7);
    }

    @Test public void testUnidirectional() {
        Graph g = createGraph(14);

        g.edge(1, 2, 12, false);
        g.edge(1, 11, 12, false);
        g.edge(11, 1, 12, false);
        g.edge(1, 12, 12, false);
        g.edge(3, 2, 112, false);
        Iterator<DistEntry> i = g.getOutgoing(2).iterator();
        assertFalse(i.hasNext());

        i = g.getOutgoing(3).iterator();
        assertEquals(2, i.next().node);
        assertFalse(i.hasNext());

        i = g.getOutgoing(1).iterator();
        assertEquals(2, i.next().node);
        assertEquals(11, i.next().node);
        assertEquals(12, i.next().node);
        assertFalse(i.hasNext());
    }

    @Test public void testUpdateUnidirectional() {
        Graph g = createGraph(4);

        g.edge(1, 2, 12, false);
        g.edge(3, 2, 112, false);
        Iterator<DistEntry> i = g.getOutgoing(2).iterator();
        assertFalse(i.hasNext());
        i = g.getOutgoing(3).iterator();
        assertEquals(2, i.next().node);
        assertFalse(i.hasNext());

        g.edge(2, 3, 112, false);
        i = g.getOutgoing(2).iterator();
        assertTrue(i.hasNext());
        assertEquals(3, i.next().node);
        i = g.getOutgoing(3).iterator();
        assertEquals(2, i.next().node);
        assertFalse(i.hasNext());
    }

    @Test
    public void testClone() {
        Graph g = createGraph(11);
        g.edge(1, 2, 10, true);
        g.addLocation(12, 23);
        Graph clone = g.clone();
        assertEquals(g.getLocations(), clone.getLocations());
        assertEquals(count(g.getOutgoing(1)), count(clone.getOutgoing(1)));
    }

    @Test
    public void testGetLocations() {
        Graph g = createGraph(11);
        g.addLocation(12, 23);
        g.addLocation(22, 23);
        assertEquals(2, g.getLocations());

        g.edge(0, 1, 10, true);
        assertEquals(2, g.getLocations());

        g.edge(0, 2, 10, true);
        assertEquals(3, g.getLocations());

        g = createGraph(11);
        assertEquals(0, g.getLocations());
    }

    @Test
    public void testAddLocation() {
        Graph g = createGraph(11);
        assertEquals(0, g.addLocation(12, 23));
        assertEquals(1, g.addLocation(38.33f, 235.3f));
        assertEquals(2, g.addLocation(6, 2339));
        assertEquals(3, g.addLocation(78, 89));
        assertEquals(4, g.addLocation(2, 1));
        assertEquals(5, g.addLocation(7, 5));
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
    public void testGetNodeId() {
        Graph g = createGraph(11);
        assertEquals(0, g.addLocation(12, 23));
        assertEquals(1, g.addLocation(38.33f, 235.3f));
        assertEquals(2, g.addLocation(3, 3));
        assertEquals(3, g.addLocation(78, 89));
        assertEquals(4, g.addLocation(2, 1));
        assertEquals(5, g.addLocation(2.5f, 1));
        
        assertEquals(2, g.getNodeId(6, 2, 0));
        assertEquals(5, g.getNodeId(2.4f, 1, 0));
        assertEquals(-1, g.getNodeId(2.4f, 1, 1));
    }
}
