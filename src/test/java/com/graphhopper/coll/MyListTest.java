/*
 *  Copyright 2012 Peter Karich
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
package com.graphhopper.coll;

import org.junit.Test;
import static org.junit.Assert.*;

/**
 *
 * @author Peter Karich
 */
public class MyListTest {

    @Test
    public void testAdd() {
        MyList<Integer> instance = new MyList<Integer>();
        instance.add(0);
        instance.add(2);
        instance.add(1, 10);
        assertEquals("0,10,2", instance.toString());
        assertEquals(10, instance.remove(1).intValue());

        assertEquals("0,2", instance.toString());
    }

    @Test
    public void testBinSearch() {
        MyList<Integer> instance = new MyList<Integer>();
        instance.add(0);
        instance.add(2);
        instance.add(7);

        assertEquals(1, instance.binSearch(2));
        assertEquals(-(1 + 1), instance.binSearch(1));
        assertEquals(-(2 + 1), instance.binSearch(5));
        assertEquals(2, instance.binSearch(7));
        assertEquals(-(3 + 1), instance.binSearch(10));
    }
}
