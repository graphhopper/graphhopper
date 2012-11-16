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

import com.graphhopper.coll.OSMIDMap;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 *
 * @author Peter Karich
 */
public class OSMIDMapTest {

    @Test
    public void testGet() {
        OSMIDMap map = new OSMIDMap();
        map.put(10, 1);
        map.put(11, 2);
        map.put(12, 3);
        map.put(20, 4);
        map.put(21, 5);
        map.put(31, 6);

        assertEquals(1, map.get(10));
        assertEquals(2, map.get(11));
        assertEquals(3, map.get(12));
        assertEquals(4, map.get(20));
        assertEquals(5, map.get(21));
        assertEquals(6, map.get(31));
    }
}
