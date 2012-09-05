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
package de.jetsli.graph.util;

import de.jetsli.graph.routing.util.EdgeFlags;
import de.jetsli.graph.storage.Graph;
import de.jetsli.graph.storage.MemoryGraphSafe;
import de.jetsli.graph.storage.PriorityGraphImpl;
import static org.junit.Assert.*;
import org.junit.Test;

/**
 *
 * @author Peter Karich
 */
public class EdgeUpdateIteratorTest {

    @Test
    public void testUpdateFlags() {
        Graph g = new PriorityGraphImpl(20);
        g.edge(0, 1, 12, EdgeFlags.create(10, true));
        g.edge(0, 2, 13, EdgeFlags.create(20, true));

        assertEquals(4, GraphUtility.countEdges(g));
        assertEquals(1, GraphUtility.count(g.getOutgoing(1)));
        EdgeSkipIterator iter = (EdgeSkipIterator) g.getEdges(0);
        assertTrue(iter.next());
        assertEquals(EdgeFlags.create(10, true), iter.flags());
        iter.flags(EdgeFlags.create(20, false));
        assertEquals(12, iter.distance(), 1e-4);
        iter.distance(10);
        assertEquals(10, iter.distance(), 1e-4);
        assertEquals(0, GraphUtility.count(g.getOutgoing(1)));
        iter = (EdgeSkipIterator) g.getEdges(0);
        assertTrue(iter.next());
        assertEquals(EdgeFlags.create(20, false), iter.flags());
        assertEquals(10, iter.distance(), 1e-4);
        assertEquals(3, GraphUtility.countEdges(g));
    }
}
