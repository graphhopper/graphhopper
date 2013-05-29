/*
 *  Licensed to GraphHopper and Peter Karich under one or more contributor license 
 *  agreements. See the NOTICE file distributed with this work for 
 *  additional information regarding copyright ownership.
 * 
 *  GraphHopper licenses this file to you under the Apache License, 
 *  Version 2.0 (the "License"); you may not use this file except 
 *  in compliance with the License. You may obtain a copy of the 
 *  License at
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

import com.graphhopper.storage.Edge;
import com.graphhopper.util.EdgeIterator;
import java.util.PriorityQueue;
import java.util.Random;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 *
 * @author Peter Karich
 */
public abstract class AbstractBinHeapTest {

    public abstract BinHeapWrapper<Number, Integer> createHeap(int capacity);

    @Test
    public void test0() {
        BinHeapWrapper<Number, Integer> binHeap = createHeap(100);
        binHeap.insert(123, 0);
        assertEquals(123, binHeap.peekKey().intValue());
        assertEquals(1, binHeap.size());

        binHeap.update(12, 0);
        assertEquals(12, binHeap.peekKey().intValue());
        assertEquals(1, binHeap.size());
    }

    @Test
    public void testBasic() {
        BinHeapWrapper<Number, Integer> binHeap = createHeap(100);
        binHeap.insert(20, 1);
        binHeap.insert(123, 2);
        binHeap.insert(120, 3);
        binHeap.insert(130, 4);

        assertEquals(1, (int) binHeap.pollElement());
        assertEquals(3, (int) binHeap.pollElement());
        assertEquals(2, (int) binHeap.pollElement());
        assertEquals(1, (int) binHeap.size());
    }

    @Test
    public void testClear() {
        BinHeapWrapper<Number, Integer> binHeap = createHeap(100);
        binHeap.insert(20, 1);
        binHeap.insert(123, 2);
        assertEquals(2, binHeap.size());
        binHeap.clear();

        assertEquals(0, binHeap.size());
        binHeap.insert(123, 2);
        assertEquals(1, binHeap.size());
        assertEquals(2, (int) binHeap.pollElement());
    }

    @Test
    public void testSpreading() {
        BinHeapWrapper<Number, Integer> binHeap = createHeap(100);
        binHeap.insert(100, 101);
        binHeap.insert(49, 51);
        binHeap.insert(71, 71);
        binHeap.insert(29, 31);
        for (int i = 0; i < 20; i++) {
            binHeap.insert(i * 10, i * 11);
        }
        binHeap.insert(59, 61);
        binHeap.insert(160, 161);

        assertEquals(26, binHeap.size());
        assertEquals(0, binHeap.pollElement().intValue());
        assertEquals(11, binHeap.pollElement().intValue());
        assertEquals(22, binHeap.pollElement().intValue());
        assertEquals(31, binHeap.pollElement().intValue());
        assertEquals(33, binHeap.pollElement().intValue());
        assertEquals(44, binHeap.pollElement().intValue());
        assertEquals(51, binHeap.pollElement().intValue());
        assertEquals(55, binHeap.pollElement().intValue());
        assertEquals(61, binHeap.pollElement().intValue());
        assertEquals(66, binHeap.pollElement().intValue());
        assertEquals(77, binHeap.pollElement().intValue());
        assertEquals(15, binHeap.size());
    }

    @Test
    public void testRekey() {
        BinHeapWrapper<Number, Integer> binHeap = createHeap(100);
        binHeap.insert(20, 1);
        binHeap.insert(123, 2);
        binHeap.update(100, 2);
        binHeap.insert(120, 3);
        binHeap.insert(130, 4);

        assertEquals(1, (int) binHeap.pollElement());
        assertEquals(2, (int) binHeap.pollElement());
        assertEquals(2, binHeap.size());
    }

    @Test
    public void testSize() {
        PriorityQueue<Edge> juQueue = new PriorityQueue<Edge>(100);
        BinHeapWrapper<Number, Integer> binHeap = createHeap(100);

        Random rand = new Random(1);
        int N = 1000;
        for (int i = 0; i < N; i++) {
            int val = rand.nextInt();
            binHeap.insert(val, i);
            juQueue.add(new Edge(EdgeIterator.NO_EDGE, i, val));
        }

        assertEquals(juQueue.size(), binHeap.size());

        for (int i = 0; i < N; i++) {
            assertEquals(juQueue.poll().endNode, binHeap.pollElement(), 1e-5);
        }

        assertEquals(binHeap.size(), 0);
    }
}
