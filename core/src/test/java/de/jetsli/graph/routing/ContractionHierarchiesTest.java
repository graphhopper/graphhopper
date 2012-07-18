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
package de.jetsli.graph.routing;

import static de.jetsli.graph.util.MyIteratorable.*;
import de.jetsli.graph.storage.Graph;
import de.jetsli.graph.storage.MemoryGraphSafe;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 *
 * @author Peter Karich, info@jetsli.de
 */
public class ContractionHierarchiesTest {
//extends AbstractDijkstraTester {
//    
//    @Override
//    public Dijkstra createDijkstra(Graph g) {
//        return new DijkstraBidirection(new ContractionHierarchies().contract(g));
//        // TODO pathFromCH.locations() <= path.locations()
//    }    
    
    @Test
    public void testSuperSimpleContract() {
        Graph g = new MemoryGraphSafe(3);

        g.edge(0, 1, 3, false);
        g.edge(1, 2, 4, false);

//        g = new ContractionHierarchies().contract(g);
//        assertEquals(2, count(g.getOutgoing(0)));
//        assertEquals(1, count(g.getIncoming(1)));
//
//        TIntHashSet set = new TIntHashSet();
//        for (DistEntry e : g.getOutgoing(0)) {
//            set.add(e.node);
//        }
//        assertTrue(set.contains(2));
//
//        set = new TIntHashSet();
//        for (DistEntry e : g.getIncoming(2)) {
//            set.add(e.node);
//        }
//        assertTrue(set.contains(0));
    }
    
    @Test
    public void testIntroduceShortcut0_2() {
        Graph g = new MemoryGraphSafe(5);
        g.edge(0, 3, 2, false);
        g.edge(3, 4, 3, false);
        g.edge(4, 2, 1, false);

        g = new ContractionHierarchies().contract(g);        
//        assertEquals(3, count(g.getOutgoing(0)));
//        assertEquals(0, count(g.getIncoming(0)));
//        assertEquals(1, count(g.getOutgoing(3)));
//        assertEquals(1, count(g.getIncoming(3)));
//        assertEquals(1, count(g.getOutgoing(4)));
//        assertEquals(2, count(g.getIncoming(4)));
//        assertEquals(0, count(g.getOutgoing(2)));
//        assertEquals(2, count(g.getIncoming(2)));
    }

    //@Test
    public void DoNotIntroduceShortCut0_2() {
        Graph g = new MemoryGraphSafe(5);        
        g.edge(0, 1, 3, false);
        g.edge(1, 2, 4, false);

        g.edge(0, 3, 2, false);
        g.edge(3, 4, 3, false);
        g.edge(4, 2, 2, false);

        g = new ContractionHierarchies().contract(g);
        assertEquals(2, count(g.getOutgoing(0)));
        assertEquals(0, count(g.getIncoming(0)));
        assertEquals(1, count(g.getOutgoing(1)));
        assertEquals(1, count(g.getIncoming(1)));
        assertEquals(2, count(g.getOutgoing(3)));
        assertEquals(1, count(g.getIncoming(3)));
        assertEquals(0, count(g.getOutgoing(2)));
        assertEquals(3, count(g.getIncoming(2)));
        assertEquals(1, count(g.getOutgoing(4)));
        assertEquals(1, count(g.getIncoming(4)));
    }
}
