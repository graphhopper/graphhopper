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
package com.graphhopper.util;

import org.junit.Test;
import static org.junit.Assert.*;

/**
 *
 * @author Peter Karich
 */
public class EdgeWrapperTest
{
    @Test
    public void testPut()
    {
        EdgeWrapper instance = new EdgeWrapper(5);
        int ref = instance.add(10, 100f, -1);
        assertEquals(100f, instance.getWeight(ref), 1e-4);
        assertEquals(-1, instance.getParent(ref));
        assertEquals(10, instance.getNode(ref));
    }

    @Test
    public void testClear()
    {
        EdgeWrapper instance = new EdgeWrapper(5);
        assertEquals(0, instance.add(1, 10, 10));
        instance.add(2, 20, 20);
        instance.add(3, 30, 30);
        instance.clear();
        assertEquals(0, instance.add(1, 10, 10));
    }
}
