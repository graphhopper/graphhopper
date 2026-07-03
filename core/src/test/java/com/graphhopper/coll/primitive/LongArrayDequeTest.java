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
package com.graphhopper.coll.primitive;

import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

public class LongArrayDequeTest {

    @Test
    public void testStackUsage() {
        // the usage pattern of TarjanSCC/EdgeBasedTarjanSCC dfs stacks
        LongArrayDeque deque = new LongArrayDeque();
        assertTrue(deque.isEmpty());
        deque.addLast(3L);
        deque.addLast(-9L);
        deque.addLast(Long.MAX_VALUE);
        assertEquals(3, deque.size());
        assertEquals(Long.MAX_VALUE, deque.removeLast());
        assertEquals(-9L, deque.removeLast());
        assertFalse(deque.isEmpty());
        assertEquals(3L, deque.removeLast());
        assertTrue(deque.isEmpty());
    }

    @Test
    public void testAddRemoveBothEnds() {
        LongArrayDeque deque = new LongArrayDeque(4);
        deque.addFirst(2);
        deque.addLast(3);
        deque.addFirst(1);
        deque.addLast(4);
        assertEquals(1, deque.getFirst());
        assertEquals(4, deque.getLast());
        assertArrayEquals(new long[]{1, 2, 3, 4}, deque.toArray());
        assertEquals(1, deque.removeFirst());
        assertEquals(4, deque.removeLast());
        assertArrayEquals(new long[]{2, 3}, deque.toArray());
    }

    @Test
    public void testGrowthKeepsOrderAcrossWrapAround() {
        LongArrayDeque deque = new LongArrayDeque(4);
        // force head/tail wrap-around before growth
        for (int i = 0; i < 1000; i++) {
            deque.addLast(i);
            if (i % 3 == 0)
                assertEquals(deque.getFirst(), deque.removeFirst());
        }
        int size = deque.size();
        long[] array = deque.toArray();
        assertEquals(size, array.length);
        for (int i = 1; i < array.length; i++)
            assertEquals(array[i - 1] + 1, array[i]);
    }

    @Test
    public void testRandomizedAgainstJavaUtil() {
        Random rnd = new Random(42);
        LongArrayDeque deque = new LongArrayDeque();
        Deque<Long> reference = new ArrayDeque<>();
        for (int i = 0; i < 100_000; i++) {
            int op = rnd.nextInt(6);
            long value = rnd.nextLong();
            if (op == 0) {
                deque.addFirst(value);
                reference.addFirst(value);
            } else if (op == 1 || op == 2) {
                deque.addLast(value);
                reference.addLast(value);
            } else if (op == 3 && !reference.isEmpty()) {
                assertEquals(reference.removeFirst(), deque.removeFirst());
            } else if (op == 4 && !reference.isEmpty()) {
                assertEquals(reference.removeLast(), deque.removeLast());
            } else if (!reference.isEmpty()) {
                assertEquals(reference.getFirst(), deque.getFirst());
                assertEquals(reference.getLast(), deque.getLast());
            }
            assertEquals(reference.size(), deque.size());
        }
        long[] array = deque.toArray();
        int i = 0;
        for (long expected : reference)
            assertEquals(expected, array[i++]);
    }

    @Test
    public void testClearAndForEach() {
        LongArrayDeque deque = new LongArrayDeque(4);
        for (int i = 0; i < 10; i++)
            deque.addLast(i);
        deque.removeFirst();
        StringBuilder sb = new StringBuilder();
        deque.forEach(v -> {
            sb.append(v).append(",");
            return kotlin.Unit.INSTANCE;
        });
        assertEquals("1,2,3,4,5,6,7,8,9,", sb.toString());

        deque.clear();
        assertTrue(deque.isEmpty());
        assertEquals(0, deque.size());
        deque.addLast(5);
        assertEquals(5, deque.getFirst());
        assertEquals(5, deque.getLast());
    }
}
