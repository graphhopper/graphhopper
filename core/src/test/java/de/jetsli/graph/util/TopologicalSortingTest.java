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

import de.jetsli.graph.storage.MemoryGraph;
import gnu.trove.list.array.TIntArrayList;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 *
 * @author Peter Karich, info@jetsli.de
 */
public class TopologicalSortingTest {
    
    @Test
    public void testSort() {       
        MemoryGraph g = new MemoryGraph();                
        g.edge(7, 11, 1, false);
        g.edge(7, 8, 1, false);
        g.edge(5, 11, 1, false);
        g.edge(3, 0, 1, false);
        g.edge(3, 8, 1, false);
        g.edge(3, 10, 1, false);
        g.edge(11, 2, 1, false);
        g.edge(11, 9, 1, false);
        g.edge(11, 10, 1, false);
        g.edge(8, 9, 1, false);
        
        TopologicalSorting ts = new TopologicalSorting();
        TIntArrayList list = ts.sort(g);
        // 9 nodes, 10 edges
        assertEquals(9, list.size());
                
        for (int i = 1; i < list.size(); i++) {            
            checkOrder(g, list, i);        
        }
    }
    
    @Test
    public void testSort2() {
        MemoryGraph g = new MemoryGraph();        
        g.edge(1, 2, 1, false);
        g.edge(7, 2, 1, false);
        g.edge(2, 0, 1, false);
        g.edge(2, 8, 1, false);
        g.edge(0, 3, 1, false);
        g.edge(3, 4, 1, false);
        g.edge(0, 6, 1, false);
        g.edge(5, 0, 1, false);
        g.edge(5, 2, 1, false);
        g.edge(5, 8, 1, false);
        g.edge(5, 6, 1, false);
        g.edge(8, 9, 1, false);
        g.edge(9, 6, 1, false);
        g.edge(6, 4, 1, false);        
        
        TopologicalSorting ts = new TopologicalSorting();
        TIntArrayList list = ts.sort(g);
        assertEquals(g.getNodes(), list.size());
                
        for (int i = 1; i < list.size(); i++) {            
            checkOrder(g, list, i);        
        }
    }
    
    @Test
    public void testSortWithCycle() {
        MemoryGraph g = new MemoryGraph();
        g.edge(0, 1, 1, false);
        g.edge(1, 2, 1, false);
        g.edge(2, 0, 1, false);
        
        TopologicalSorting ts = new TopologicalSorting();
        try {
            ts.sort(g);        
            assertFalse(true);
        } catch(Exception ex) {
        }
    }

    private void checkOrder(MemoryGraph g, final TIntArrayList res, final int i) {
        final int prev = res.get(i - 1);
        final int curr = res.get(i);
        XFirstSearch search = new XFirstSearch() {
            
            @Override
            public boolean goFurther(int v) {
                assertNotSame("search starting from " + curr + " should not visit its previous entry " + prev + ", set:" + res, prev, v);
                return super.goFurther(v);
            }            
        };
        search.start(g, curr, false);                
    }
}
