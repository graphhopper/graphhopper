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

import de.jetsli.graph.storage.DistEntry;
import gnu.trove.set.TIntSet;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 *
 * @author Peter Karich, info@jetsli.de
 */
public class PathTest {

    @Test public void testAdd() {
        Path p = new Path();
        p.add(new DistEntry(1, 12));
        p.add(new DistEntry(1, 13));
        // Hmmh according to Dijkstra this is correct but according to intuition this is none sense.
        assertEquals(13, p.distance(), 1e-3);
    }

    @Test public void testReverseOrder() {
        Path p = new Path();
        DistEntry from = new DistEntry(1, 11);
        DistEntry to = new DistEntry(3, 12);
        p.add(from);
        p.add(new DistEntry(2, 2));
        p.add(to);
        // Hmmh see above comment
        assertEquals(12, p.distance(), 1e-3);
        assertEquals(1, p.getFromLoc());

        p.reverseOrder();
        assertEquals(11, p.distance(), 1e-3);
        assertEquals(3, p.getFromLoc());
    }
    
    @Test public void testAnd() {
        Path p1 = new Path();
        p1.add(new DistEntry(1, 12));
        p1.add(new DistEntry(2, 12));
        p1.add(new DistEntry(3, 12));
        p1.add(new DistEntry(4, 12));
        
        Path p2 = new Path();
        p2.add(new DistEntry(7, 12));
        p2.add(new DistEntry(2, 12));
        p2.add(new DistEntry(3, 12));
        p2.add(new DistEntry(11, 12));
        
        TIntSet set = p1.and(p2);
        assertEquals(2, set.size());
        assertTrue(set.contains(2));
        assertTrue(set.contains(3));
        
        set = p2.and(p1);
        assertEquals(2, set.size());
        assertTrue(set.contains(2));
        assertTrue(set.contains(3));
    }
    
    @Test public void testContains() {
        Path p1 = new Path();
        p1.add(new DistEntry(1, 12));
        p1.add(new DistEntry(2, 12));
        
        assertFalse(p1.contains(3));
        assertTrue(p1.contains(2));
    }
}
