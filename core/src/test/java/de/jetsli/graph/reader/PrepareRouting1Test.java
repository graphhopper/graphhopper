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
import de.jetsli.graph.storage.MemoryGraphSafe;
import de.jetsli.graph.util.EdgeIterator;
import de.jetsli.graph.util.GraphUtility;
import java.util.Arrays;
import java.util.Map;
import org.junit.*;
import static org.junit.Assert.*;

/**
 *
 * @author Peter Karich
 */
public class PrepareRouting1Test {

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
        PrepareRouting1 instance = new PrepareRouting1(g);
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
        PrepareRouting1 instance = new PrepareRouting1(g);
        Map<Integer, Integer> map = instance.findSubnetworks();
        instance.keepLargestNetwork(map);
        g.optimize();

        assertEquals(7, g.getNodes());
        assertEquals(Arrays.asList(), GraphUtility.getProblems(g));
        map = instance.findSubnetworks();
        assertEquals(1, map.size());
        assertEquals(7, (int) map.get(0));
    }
}
