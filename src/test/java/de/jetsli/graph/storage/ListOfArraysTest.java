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

import gnu.trove.list.array.TIntArrayList;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 *
 * @author Peter Karich
 */
public class ListOfArraysTest {

    private ListOfArrays la;

    ListOfArrays createLA() {
        RAMDirectory dir = new RAMDirectory("test");
        return new ListOfArrays(dir, "la", 10);
    }

    @Before
    public void setUp() {
        la = createLA();
    }

    @Test
    public void testAdd() {
        assertEquals(0, la.size());
        TIntArrayList list = new TIntArrayList();
        list.add(2);
        list.add(5);
        la.add(list);

        IntIterator iter = la.getIterator(0);
        assertTrue(iter.next());
        assertEquals(2, iter.value());
        assertTrue(iter.next());
        assertEquals(5, iter.value());
        assertFalse(iter.next());
    }

    @Test
    public void testMoreAdd() {
        TIntArrayList list = new TIntArrayList();
        list.add(2);
        list.add(5);
        la.add(list);
        list.clear();
        for (int i = 0; i < 200; i++) {
            list.add(i);
        }
        la.add(list);
        IntIterator iter = la.getIterator(1);
        int i = 0;
        while (iter.next()) {
            assertEquals(i, iter.value());
            i++;
        }
        assertEquals(200, i);
        assertFalse(iter.next());
    }

    @Test
    public void testSetReferences() {
        TIntArrayList list = new TIntArrayList();
        list.add(2);
        list.add(5);
        la.add(list);
        int oldCap = la.capacity();
        la.setSameReference(0, 1);
        assertEquals(oldCap, la.capacity());

        IntIterator iter = la.getIterator(1);
        assertTrue(iter.next());
        assertEquals(2, iter.value());
        assertTrue(iter.next());
        assertEquals(5, iter.value());
        assertFalse(iter.next());
    }
}
