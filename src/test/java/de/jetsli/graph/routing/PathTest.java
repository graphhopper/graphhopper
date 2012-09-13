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

import de.jetsli.graph.storage.Edge;
import gnu.trove.set.TIntSet;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 *
 * @author Peter Karich, info@jetsli.de
 */
public class PathTest {

    @Test public void testReverseOrder() {
        Path p = new Path();
        p.add(1);
        p.add(3);
        p.add(2);
        assertEquals(1, p.getFromLoc());

        p.reverseOrder();
        assertEquals(2, p.getFromLoc());
    }

    @Test public void testAnd() {
        Path p1 = new Path();
        p1.add(1);
        p1.add(2);
        p1.add(3);
        p1.add(4);

        Path p2 = new Path();
        p2.add(7);
        p2.add(2);
        p2.add(3);
        p2.add(11);

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
        p1.add(1);
        p1.add(2);

        assertFalse(p1.contains(3));
        assertTrue(p1.contains(2));
    }
}
