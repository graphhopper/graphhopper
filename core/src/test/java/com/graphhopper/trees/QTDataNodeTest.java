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
package com.graphhopper.trees;

import org.junit.*;
import static org.junit.Assert.*;

/**
 *
 * @author Peter Karich
 */
public class QTDataNodeTest
{
    @Test
    public void testGetMemoryUsageInBytes()
    {
        QTDataNode<Integer> dn = new QTDataNode<Integer>(8);
        dn.keys[1] = 111;
        // dn.values[1] = (Integer) 222;
        assertEquals(0, dn.count());
        dn.add(1, 1);
        assertEquals(1, dn.count());
    }

    @Test
    public void testAddDuplicates()
    {
        QTDataNode<String> dn = new QTDataNode<String>(4);
        dn.add(1, "test1");
        assertEquals(1, dn.count());
        dn.add(1, "test5");
        dn.add(1, "test2");
        assertEquals(3, dn.count());
    }

    @Test
    public void testNodeAdd()
    {
        QTDataNode<String> dn = new QTDataNode<String>(2);
        assertFalse(dn.add(1, "test1"));
        assertFalse(dn.add(5, "test5"));
        assertTrue(dn.add(2, "test2"));
    }

    @Test
    public void testNodeRemove()
    {
        QTDataNode<String> dn = new QTDataNode<String>(4);
        dn.add(1, "test1");
        dn.add(5, "test5");
        dn.add(2, "test2");
        dn.add(3, "test3");

        assertEquals(1, dn.remove(3));
        assertEquals(1, dn.remove(5));

        assertNull(dn.getValue(5));
        assertNull(dn.getValue(3));
        assertEquals("test1", dn.getValue(1));
        assertEquals("test2", dn.getValue(2));
    }

    @Test
    public void testNodeRemoveWithDuplicates()
    {
        QTDataNode<String> dn = new QTDataNode<String>(4);
        dn.add(1, "test1");
        dn.add(1, "test5");

        assertEquals(2, dn.remove(1));
    }
}
