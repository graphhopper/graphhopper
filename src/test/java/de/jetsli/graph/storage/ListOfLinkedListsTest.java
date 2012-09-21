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

import gnu.trove.iterator.TIntIntIterator;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 *
 * @author Peter Karich
 */
public class ListOfLinkedListsTest {

    @Test
    public void testSomeMethod() {
        RAMDataAccess refs = new RAMDataAccess("refs");
        RAMDataAccess entries = new RAMDataAccess("entries");
        ListOfLinkedLists lll = new ListOfLinkedLists(refs, entries);

        lll.add(1, 10);
        IntIterator iter = lll.getEntries(0);
        assertFalse(iter.next());
        iter = lll.getEntries(1);
        assertTrue(iter.next());
        assertEquals(10, iter.value());
    }
}
