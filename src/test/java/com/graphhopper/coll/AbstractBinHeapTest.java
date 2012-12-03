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

import com.graphhopper.storage.Edge;
import java.util.PriorityQueue;
import java.util.Random;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 *
 * @author Peter Karich
 */
public abstract class AbstractBinHeapTest {

    public abstract BinHeapWrapper<Integer, Number> createHeap(int capacity);

    @Test
    public void test0() {
        BinHeapWrapper<Integer, Number> binHeap = createHeap(100);
        binHeap.insert(123, 0);
        assertEquals(123, binHeap.peekKey().intValue());
        assertEquals(1, binHeap.size());

        binHeap.update(12, 0);
        assertEquals(12, binHeap.peekKey().intValue());
        assertEquals(1, binHeap.size());
    }

    @Test
    public void testBasic() {
        BinHeapWrapper<Integer, Number> binHeap = createHeap(100);
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
        BinHeapWrapper<Integer, Number> binHeap = createHeap(100);
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
    public void testRekey() {
        BinHeapWrapper<Integer, Number> binHeap = createHeap(100);
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
        BinHeapWrapper<Integer, Number> binHeap = createHeap(100);

        Random rand = new Random(1);
        int N = 1000;
        for (int i = 0; i < N; i++) {
            int val = rand.nextInt();
            binHeap.insert(val, i);
            juQueue.add(new Edge(-1, i, val));
        }

        assertEquals(juQueue.size(), binHeap.size());

        for (int i = 0; i < N; i++) {
            assertEquals(juQueue.poll().endNode, binHeap.pollElement(), 1e-5);
        }

        assertEquals(binHeap.size(), 0);
    }
}
