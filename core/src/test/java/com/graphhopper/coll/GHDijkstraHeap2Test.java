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

import java.util.Arrays;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 *
 * @author Peter Karich
 */
public class GHDijkstraHeap2Test extends AbstractBinHeapTest
{
    @Override
    public BinHeapWrapper<Number, Integer> createHeap( int capacity )
    {
        return new GHDijkstraHeap2(capacity / 5);
    }

    @Test
    public void testDups()
    {
        BinHeapWrapper<Number, Integer> heap = createHeap(100);
        heap.insert(3, 4);
        heap.insert(3, 5);
        heap.insert(3, 6);
        heap.insert(4, 7);
        assertEquals(4, heap.size());
        List<Integer> list = Arrays.asList(6, 5, 4);
        assertTrue(list.contains(heap.pollElement()));
        assertTrue(list.contains(heap.pollElement()));
        assertTrue(list.contains(heap.pollElement()));
        assertEquals(7, heap.pollElement().intValue());
    }

    @Test
    public void testRemove()
    {
        GHDijkstraHeap2 heap = new GHDijkstraHeap2(5);

        heap.insert(4, 3);
        heap.insert(4, 4);
        heap.insert(4, 5);
        heap.insert(5, 6);

        heap.removeSorted(4, 4);
        assertEquals(5, heap.pollElement().intValue());
        assertEquals(3, heap.pollElement().intValue());
        assertEquals(6, heap.pollElement().intValue());

        try
        {
            heap.removeSorted(4, 6);
            assertTrue(false);
        } catch (Exception ex)
        {
        }
    }
}
