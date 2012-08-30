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

import de.jetsli.graph.util.EdgeFilter;
import de.jetsli.graph.util.EdgeIterator;
import de.jetsli.graph.util.GraphUtility;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 *
 * @author Peter Karich
 */
public class PriorityGraphImplTest extends MemoryGraphSafeTest {

    @Override
    PriorityGraph createGraph(int size) {
        return new PriorityGraphImpl(size);
    }

    @Test
    public void testPriosWhileDeleting() {
        PriorityGraph g = (PriorityGraph) createGraph(11);
        for (int i = 0; i < 20; i++) {
            g.setPriority(i, i);
        }
        g.markNodeDeleted(10);
        g.optimize();
        assertEquals(9, g.getPriority(9));
        assertEquals(19, g.getPriority(10));
        assertEquals(11, g.getPriority(11));
    }

    @Test
    public void testPrios() {
        PriorityGraph g = createGraph(20);
        assertEquals(Integer.MIN_VALUE, g.getPriority(10));

        g.setPriority(10, 100);
        assertEquals(100, g.getPriority(10));

        g.setPriority(30, 100);
        assertEquals(100, g.getPriority(30));
    }

    @Test
    public void testEdgeFilter() {
        final PriorityGraph g = createGraph(20);
        g.edge(0, 1, 10, true);
        g.edge(0, 2, 20, true);
        g.edge(2, 3, 30, true);
        g.edge(3, 4, 40, true);

        // shortcut
        g.edge(0, 4, 40, true);
        g.setPriority(0, 1);
        g.setPriority(4, 1);

        g.setEdgeFilter(new EdgeFilter() {
            @Override public boolean accept(int fromNode, EdgeIterator edge) {
                return g.getPriority(fromNode) <= g.getPriority(edge.node());
            }
        });
        EdgeIterator iter = g.getEdges(0);
        assertEquals(1, GraphUtility.count(iter));
        iter = g.getEdges(2);
        assertEquals(2, GraphUtility.count(iter));
    }
}
