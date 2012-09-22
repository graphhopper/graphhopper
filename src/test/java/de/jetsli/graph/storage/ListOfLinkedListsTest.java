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

import static org.junit.Assert.*;
import org.junit.Before;
import org.junit.Test;

/**
 *
 * @author Peter Karich
 */
public class ListOfLinkedListsTest {

    ListOfLinkedLists lll;

    @Before
    public void setUp() {
        lll = createLLL();
    }

    private ListOfLinkedLists createLLL() {
        RAMDataAccess refs = new RAMDataAccess("refs");
        RAMDataAccess entries = new RAMDataAccess("entries");
        return new ListOfLinkedLists(refs, entries, 10);
    }

    @Test
    public void testAdd() {
        assertEquals(10, lll.size());
        lll.add(1, 10);
        IntIterator iter = lll.getIterator(0);
        assertFalse(iter.next());
        iter = lll.getIterator(1);
        assertTrue(iter.next());
        assertEquals(10, iter.value());
        assertFalse(iter.next());
    }

    @Test
    public void testAddAutoEnsureSize() {
        lll.add(100, 10);
        IntIterator iter = lll.getIterator(100);
        assertTrue(iter.next());
        assertEquals(10, iter.value());
        assertFalse(iter.next());

        iter = lll.getIterator(200);
        assertFalse(iter.next());
    }

    // TODO
//    @Test
//    public void testAddMany() {
//        int[] integersPerBucket = {1, 2, 10};
//        for (int val : integersPerBucket) {
//            lll = createLLL().setIntegersPerBucket(val);
//            for (int i = 0; i < 400; i++) {
//                lll.add(0, i);
//                lll.add(i, i);
//            }
//            assertEquals(400, IntIterator.Helper.count(lll.getIterator(0)));
//        }
//    }

    @Test
    public void testAvoidDuplicates() {
        int[] integersPerBucket = {1, 2, 10};
        for (int val : integersPerBucket) {
            lll = createLLL().setIntegersPerBucket(val);
            lll.add(1, 10);
            lll.add(1, 20);
            lll.add(1, 10);
            lll.add(1, 10);
            IntIterator iter = lll.getIterator(1);
            assertTrue(val + "", iter.next());
            assertEquals(val + "", 10, iter.value());
            assertTrue(iter.next());
            assertEquals(val + "", 20, iter.value());
            assertFalse(val + "", iter.next());
        }
    }
}
