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
public class BigLongIntMapTest
{
    @Test
    public void testPut()
    {
        int segments = 10;
        BigLongIntMap instance = new BigLongIntMap(1000, segments, -1);
        assertEquals(-1, instance.put(Long.MAX_VALUE / 2, 123));
        assertEquals(123, instance.get(Long.MAX_VALUE / 2));
        assertEquals(1, instance.getSize());
        instance.clear();

        for (int i = 0; i < segments; i++)
        {
            assertEquals(-1, instance.put(Integer.MAX_VALUE * i, 123));
        }
        assertEquals(segments, instance.getSize());
        // assertEquals("1, 2, 0, ...", instance.toString());
    }
}
