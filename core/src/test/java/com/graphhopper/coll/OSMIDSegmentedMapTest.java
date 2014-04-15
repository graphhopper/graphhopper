/*
 *  Licensed to GraphHopper and Peter Karich under one or more contributor
 *  license agreements. See the NOTICE file distributed with this work for 
 *  additional information regarding copyright ownership.
 * 
 *  GraphHopper licenses this file to you under the Apache License, 
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

import org.junit.Test;
import static org.junit.Assert.*;

/**
 *
 * @author Peter Karich
 */
public class OSMIDSegmentedMapTest
{

    @Test
    public void testZeroKey()
    {
        OSMIDSegmentedMap map = new OSMIDSegmentedMap();
        map.write(0);
        assertEquals(1, map.getSize());
        assertEquals(0, map.get(0));
        assertEquals(-1, map.get(1));
    }

    @Test
    public void testGet()
    {
        OSMIDSegmentedMap map = new OSMIDSegmentedMap();
        map.write(9);
        map.write(10);
        map.write(11);
        map.write(12);
        map.write(20);
        map.write(21);
        map.write(31);

        assertEquals(7, map.getSize());
        assertEquals(-1, map.get(8));
        assertEquals(0, map.get(9));
        assertEquals(1, map.get(10));
        assertEquals(2, map.get(11));
        assertEquals(3, map.get(12));
        assertEquals(-1, map.get(13));
        assertEquals(-1, map.get(19));
        assertEquals(4, map.get(20));
        assertEquals(5, map.get(21));
        assertEquals(6, map.get(31));
        assertEquals(-1, map.get(32));

        for (int i = 0; i < 200; i++)
        {
            map.write(i + 50);
        }
        assertEquals(207, map.getSize());
        assertEquals(-1, map.get(49));
        assertEquals(7, map.get(50));
    }

    @Test
    public void testGet2()
    {
        OSMIDSegmentedMap map = new OSMIDSegmentedMap();
        map.write(9);
        map.write(10);
        map.write(11);
        map.write(12);
        map.write(13);
        map.write(14);
        map.write(16);
        map.write(18);
        map.write(19);

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
}
