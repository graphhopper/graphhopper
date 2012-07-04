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
import de.jetsli.graph.storage.MMapGraph;
import de.jetsli.graph.util.MyIteratorable;
import de.jetsli.graph.util.XFirstSearch;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.*;
import static org.junit.Assert.*;

/**
 *
 * @author Peter Karich
 */
public class PrepareRoutingTest {

    Graph createSubnetworkTestGraph() {
        Graph g = new MMapGraph(20).createNew();
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

        map = instance.findSubnetworks();
        assertEquals(1, map.size());
        assertEquals(7, (int) map.get(0));
    }

    // TODO @Test
    public void testAddEdgeToSkip2DegreeNodes() {
        final Graph g = new MMapGraph(20);
        g.edge(0, 1, 1, true);
        g.edge(0, 2, 1, true);
        g.edge(1, 2, 1, true);
        g.edge(2, 3, 1, true);
        g.edge(1, 4, 1, true);
        g.edge(2, 9, 1, true);
        g.edge(2, 3, 1, true);
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

        PrepareRouting instance = new PrepareRouting(g);
        instance.addEdgesToSkip2DegreeNodes();
        g.optimize();

        // the resulting graph should have 5 nodes and 8 edges (weights in brackets):
        //
        // 4-(5)-14-(2)-3
        // |\     |    / \
        // | \   (1)  /  |
        // | (5)-9-(1)   |
        // |    /        |
        // 2-(1)         |
        // \--(1)---------
        //
        assertEquals(5, g.getNodes());

        final AtomicInteger edgesCounter = new AtomicInteger(0);
        new XFirstSearch() {

            @Override protected MyBitSet createBitSet(int size) {
                return new MyTBitSet(size);
            }

            @Override protected boolean goFurther(int nodeId) {
                if (MyIteratorable.count(g.getEdges(nodeId)) == 2)
                    throw new RuntimeException("should not happen");


                edgesCounter.incrementAndGet();
                return super.goFurther(nodeId);
            }
        }.start(g, 0, true);
        assertEquals(8, edgesCounter.get());
    }
}
