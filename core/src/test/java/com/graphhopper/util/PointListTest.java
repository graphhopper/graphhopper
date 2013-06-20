/*
 *  Licensed to GraphHopper and Peter Karich under one or more contributor
 *  license agreements. See the NOTICE file distributed with this work for 
 *  additional information regarding copyright ownership.
 * 
 *  GraphHopper licenses this file to you under the Apache License, 
 *  Version 2.0 (the "License"); you may not use this file except 
 *  in compliance with the License. You may obtain a copy of the 
 *  License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.graphhopper.util;

import org.junit.Test;
import static org.junit.Assert.*;

/**
 * @author Peter Karich
 */
public class PointListTest
{
    @Test
    public void testReverse()
    {
        PointList instance = new PointList();
        instance.add(1, 1);
        instance.reverse();
        assertEquals(1, instance.longitude(0), 1e-7);

        instance = new PointList();
        instance.add(1, 1);
        instance.add(2, 2);
        instance.reverse();
        assertEquals(2, instance.longitude(0), 1e-7);
        assertEquals(1, instance.longitude(1), 1e-7);
    }
}
