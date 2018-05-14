/*
 *  Licensed to GraphHopper GmbH under one or more contributor
 *  license agreements. See the NOTICE file distributed with this work for
 *  additional information regarding copyright ownership.
 *
 *  GraphHopper GmbH licenses this file to you under the Apache License,
 *  Version 2.0 (the "License"); you may not use this file except in
 *  compliance with the License. You may obtain a copy of the License at
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

import com.graphhopper.storage.DataAccess;
import com.graphhopper.storage.RAMDirectory;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * @author Peter Karich
 */
public class OSMIDMapTest {
    @Test
    public void testGet() {
        OSMIDMap map = new OSMIDMap(new RAMDirectory());
        map.put(9, 0);
        map.put(10, -50);
        map.put(11, 2);
        map.put(12, 3);
        map.put(20, 6);
        map.put(21, 5);
        map.put(31, 2);

        assertEquals(7, map.getSize());
        assertEquals(-1, map.get(8));
        assertEquals(0, map.get(9));
        assertEquals(-50, map.get(10));
        assertEquals(2, map.get(11));
        assertEquals(3, map.get(12));
        assertEquals(-1, map.get(13));
        assertEquals(-1, map.get(19));
        assertEquals(6, map.get(20));
        assertEquals(5, map.get(21));
        assertEquals(2, map.get(31));
        assertEquals(-1, map.get(32));

        for (int i = 0; i < 50; i++) {
            map.put(i + 50, i + 7);
        }
        assertEquals(57, map.getSize());
    }

    @Test
    public void testBinSearch() {
        DataAccess da = new RAMDirectory().find("");
        da.create(100);

        da.setInt(0 * 4, 1);
        da.setInt(1 * 4, 0);

        da.setInt(2 * 4, 5);
        da.setInt(3 * 4, 0);

        da.setInt(4 * 4, 100);
        da.setInt(5 * 4, 0);

        da.setInt(6 * 4, 300);
        da.setInt(7 * 4, 0);

        da.setInt(8 * 4, 333);
        da.setInt(9 * 4, 0);

        assertEquals(2, OSMIDMap.binarySearch(da, 0, 5, 100));
        assertEquals(3, OSMIDMap.binarySearch(da, 0, 5, 300));
        assertEquals(~3, OSMIDMap.binarySearch(da, 0, 5, 200));
        assertEquals(0, OSMIDMap.binarySearch(da, 0, 5, 1));
        assertEquals(1, OSMIDMap.binarySearch(da, 0, 5, 5));
    }

    @Test
    public void testGetLong() {
        OSMIDMap map = new OSMIDMap(new RAMDirectory());
        map.put(12, 0);
        map.put(Long.MAX_VALUE / 10, 1);
        map.put(Long.MAX_VALUE / 9, 2);
        map.put(Long.MAX_VALUE / 7, 3);

        assertEquals(1, map.get(Long.MAX_VALUE / 10));
        assertEquals(3, map.get(Long.MAX_VALUE / 7));
        assertEquals(-1, map.get(13));
    }

    @Test
    public void testGet2() {
        OSMIDMap map = new OSMIDMap(new RAMDirectory());
        map.put(9, 0);
        map.put(10, 1);
        map.put(11, 2);
        map.put(12, 3);
        map.put(13, 4);
        map.put(14, 5);
        map.put(16, 6);
        map.put(18, 7);
        map.put(19, 8);

        assertEquals(9, map.getSize());
        assertEquals(-1, map.get(8));
        assertEquals(0, map.get(9));
        assertEquals(1, map.get(10));
        assertEquals(2, map.get(11));
        assertEquals(3, map.get(12));
        assertEquals(4, map.get(13));
        assertEquals(5, map.get(14));
        assertEquals(6, map.get(16));
        assertEquals(-1, map.get(17));
        assertEquals(7, map.get(18));
        assertEquals(8, map.get(19));
    }

    @Test
    public void testUpdateOfLowerKeys() {
        OSMIDMap map = new OSMIDMap(new RAMDirectory());
        map.put(9, 0);
        map.put(10, 1);
        map.put(11, 2);
        map.put(9, 3);

        assertEquals(2, map.get(11));
        assertEquals(3, map.get(9));
    }
}
