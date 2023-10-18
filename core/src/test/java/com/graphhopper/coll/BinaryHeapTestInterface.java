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

import com.carrotsearch.hppc.IntArrayList;
import com.carrotsearch.hppc.IntHashSet;
import com.carrotsearch.hppc.IntSet;
import org.junit.jupiter.api.Test;

import java.util.PriorityQueue;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

public interface BinaryHeapTestInterface {

    void create(int capacity);

    int size();

    boolean isEmpty();

    void push(int id, float val);

    int peekId();

    float peekVal();

    void update(int id, float val);

    int poll();

    void clear();

    @Test
    default void testSize() {
        create(10);
        assertEquals(0, size());
        assertTrue(isEmpty());
        push(9, 3.6f);
        push(5, 2.3f);
        push(3, 2.3f);
        assertEquals(3, size());
        assertFalse(isEmpty());
    }

    @Test
    default void testClear() {
        create(5);
        assertTrue(isEmpty());
        push(3, 1.2f);
        push(4, 0.3f);
        assertEquals(2, size());
        clear();
        assertTrue(isEmpty());

        push(4, 6.3f);
        push(1, 2.1f);
        assertEquals(2, size());
        assertEquals(1, peekId());
        assertEquals(2.1f, peekVal());
        assertEquals(1, poll());
        assertEquals(4, poll());
        assertTrue(isEmpty());
    }

    @Test
    default void testPeek() {
        create(5);
        push(4, -1.6f);
        push(2, 1.3f);
        push(1, -5.1f);
        push(3, 0.4f);
        assertEquals(1, peekId());
        assertEquals(-5.1f, peekVal(), 1.e-6);
    }

    @Test
    default void pushAndPoll() {
        create(10);
        push(9, 3.6f);
        push(5, 2.3f);
        push(3, 2.3f);
        assertEquals(3, size());
        poll();
        assertEquals(2, size());
        poll();
        poll();
        assertTrue(isEmpty());
    }

    @Test
    default void pollSorted() {
        create(10);
        push(9, 3.6f);
        push(5, 2.1f);
        push(3, 2.3f);
        push(8, 5.7f);
        push(7, 2.2f);
        IntArrayList polled = new IntArrayList();
        while (!isEmpty()) {
            polled.add(poll());
        }
        assertEquals(IntArrayList.from(5, 7, 3, 9, 8), polled);
    }

    @Test
    default void update() {
        create(10);
        push(9, 3.6f);
        push(5, 2.1f);
        push(3, 2.3f);
        update(3, 0.1f);
        assertEquals(3, peekId());
        update(3, 10.f);
        assertEquals(5, peekId());
        update(9, -1.3f);
        assertEquals(9, peekId());
        assertEquals(-1.3f, peekVal(), 1.e-6);
        IntArrayList polled = new IntArrayList();
        while (!isEmpty()) {
            polled.add(poll());
        }
        assertEquals(IntArrayList.from(9, 5, 3), polled);
    }

    @Test
    default void randomPushsThenPolls() {
        final long seed = System.nanoTime();
        Random rnd = new Random(seed);
        int size = 1 + rnd.nextInt(100);
        PriorityQueue<Entry> pq = new PriorityQueue<>(size);
        create(size);
        IntSet set = new IntHashSet();
        while (pq.size() < size) {
            int id = rnd.nextInt(size);
            if (!set.add(id))
                continue;
            float val = 100 * rnd.nextFloat();
            pq.add(new Entry(id, val));
            push(id, val);
        }
        while (!pq.isEmpty()) {
            Entry entry = pq.poll();
            assertEquals(entry.val, peekVal());
            assertEquals(entry.id, poll());
            assertEquals(pq.size(), size());
        }
    }

    @Test
    default void randomPushsAndPolls() {
        final long seed = System.nanoTime();
        Random rnd = new Random(seed);
        int size = 1 + rnd.nextInt(100);
        PriorityQueue<Entry> pq = new PriorityQueue<>(size);
        create(size);
        IntSet set = new IntHashSet();
        int pushCount = 0;
        for (int i = 0; i < 1000; i++) {
            boolean push = pq.isEmpty() || (rnd.nextBoolean());
            if (push) {
                int id = rnd.nextInt(size);
                if (!set.add(id))
                    continue;
                float val = 100 * rnd.nextFloat();
                pq.add(new Entry(id, val));
                push(id, val);
                pushCount++;
            } else {
                Entry entry = pq.poll();
                assertEquals(entry.val, peekVal());
                assertEquals(entry.id, poll());
                assertEquals(pq.size(), size());
                set.removeAll(entry.id);
            }
        }
        assertTrue(pushCount > 0);
    }

    class Entry implements Comparable<Entry> {
        int id;
        float val;

        public Entry(int id, float val) {
            this.id = id;
            this.val = val;
        }

        @Override
        public int compareTo(Entry o) {
            return Float.compare(val, o.val);
        }
    }

}
