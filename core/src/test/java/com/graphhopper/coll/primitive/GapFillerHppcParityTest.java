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

import com.carrotsearch.hppc.BitSetIterator;
import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.graphhopper.coll.GrowableBitSet;
import com.graphhopper.coll.GrowableBitSetIterator;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Differential tests proving the H1 gap-filler ports behave EXACTLY like their live hppc 0.8.1
 * counterparts, including internal buffer layout where it is observable (Jackson field
 * serialization, public buffer/head/tail fields).
 *
 * NOTE: this class is deliberately the only H1 test depending on com.carrotsearch.hppc — it gets
 * DELETED in batch H8 when hppc is removed from core. The standalone semantic pins live in
 * GrowableBitSetTest, IndirectSortTest, LongArrayDequeTest and IntDoubleHashMapTest.
 */
public class GapFillerHppcParityTest {

    @Test
    public void growableBitSet_matchesHppcBitSet() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        mapper.setVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.NONE);
        mapper.setVisibility(PropertyAccessor.FIELD, JsonAutoDetect.Visibility.ANY);

        for (long seed : new long[]{1, 42, 987654321}) {
            Random rnd = new Random(seed);
            com.carrotsearch.hppc.BitSet hppc = new com.carrotsearch.hppc.BitSet();
            GrowableBitSet port = new GrowableBitSet();

            for (int i = 0; i < 5000; i++) {
                int op = rnd.nextInt(8);
                long index = rnd.nextInt(1 << 16);
                if (op < 3) {
                    hppc.set(index);
                    port.set(index);
                } else if (op == 3) {
                    long end = index + rnd.nextInt(500);
                    hppc.set(index, end);
                    port.set(index, end);
                } else if (op == 4) {
                    hppc.clear(index);
                    port.clear(index);
                } else if (op == 5) {
                    long end = index + rnd.nextInt(500);
                    hppc.clear(index, end);
                    port.clear(index, end);
                } else if (op == 6) {
                    hppc.flip(index);
                    port.flip(index);
                } else {
                    assertEquals(hppc.get(index), port.get(index));
                    // getAndSet does not expand, it is only valid below the current capacity
                    if (index < hppc.capacity() && index < port.capacity())
                        assertEquals(hppc.getAndSet(index), port.getAndSet(index));
                }
            }

            assertEquals(hppc.cardinality(), port.cardinality());
            assertEquals(hppc.length(), port.length());
            assertEquals(hppc.wlen, port.wlen);
            assertEquals(hppc.bits.length, port.bits.length, "internal growth policy must match");
            assertArrayEquals(hppc.bits, port.bits);
            assertEquals(hppc.toString(), port.toString());

            // identical set-bit sequences via both iteration styles
            BitSetIterator hppcIter = hppc.iterator();
            GrowableBitSetIterator portIter = port.iterator();
            int expected;
            do {
                expected = hppcIter.nextSetBit();
                assertEquals(expected, portIter.nextSetBit());
            } while (expected >= 0);

            long hppcBit = hppc.nextSetBit(0L), portBit = port.nextSetBit(0L);
            while (hppcBit >= 0 || portBit >= 0) {
                assertEquals(hppcBit, portBit);
                hppcBit = hppc.nextSetBit(hppcBit + 1);
                portBit = port.nextSetBit(portBit + 1);
            }

            // identical Jackson FIELD serialization (= stored-graph format of
            // ExternalBooleanEncodedValue once it switches to GrowableBitSet)
            assertEquals(mapper.writeValueAsString(hppc), mapper.writeValueAsString(port));
        }
    }

    @Test
    public void indirectSort_matchesHppcMergesort() {
        Random rnd = new Random(31415);
        // cover the insertion-sort window (<= 30), the merge path and both around the boundary
        for (int length : new int[]{0, 1, 2, 29, 30, 31, 64, 1000, 30_000}) {
            for (int keyRange : new int[]{2, 10, Integer.MAX_VALUE}) {
                int[] values = new int[length];
                for (int i = 0; i < length; i++)
                    values[i] = rnd.nextInt(keyRange) - keyRange / 2;

                int[] expected = com.carrotsearch.hppc.sorting.IndirectSort.mergesort(0, length,
                        new com.carrotsearch.hppc.sorting.IndirectComparator.AscendingIntComparator(values));
                int[] actual = IndirectSort.mergesort(0, length,
                        new IndirectComparator.AscendingIntComparator(values));
                // element-wise: proves identical (stable) order also for equal keys
                assertArrayEquals(expected, actual);

                if (length > 2) {
                    int start = rnd.nextInt(length / 2);
                    int subLength = rnd.nextInt(length - start);
                    expected = com.carrotsearch.hppc.sorting.IndirectSort.mergesort(start, subLength,
                            new com.carrotsearch.hppc.sorting.IndirectComparator.AscendingIntComparator(values));
                    actual = IndirectSort.mergesort(start, subLength,
                            new IndirectComparator.AscendingIntComparator(values));
                    assertArrayEquals(expected, actual);
                }
            }
        }
    }

    @Test
    public void longArrayDeque_matchesHppcIncludingBufferLayout() {
        for (long seed : new long[]{7, 12345}) {
            Random rnd = new Random(seed);
            com.carrotsearch.hppc.LongArrayDeque hppc = new com.carrotsearch.hppc.LongArrayDeque();
            LongArrayDeque port = new LongArrayDeque();

            for (int i = 0; i < 200_000; i++) {
                int op = rnd.nextInt(8);
                long value = rnd.nextLong();
                if (op <= 1) {
                    hppc.addFirst(value);
                    port.addFirst(value);
                } else if (op <= 4) {
                    hppc.addLast(value);
                    port.addLast(value);
                } else if (op == 5 && !hppc.isEmpty()) {
                    assertEquals(hppc.removeFirst(), port.removeFirst());
                } else if (op == 6 && !hppc.isEmpty()) {
                    assertEquals(hppc.removeLast(), port.removeLast());
                } else if (!hppc.isEmpty()) {
                    assertEquals(hppc.getFirst(), port.getFirst());
                    assertEquals(hppc.getLast(), port.getLast());
                }
                assertEquals(hppc.size(), port.size());
            }

            // exact internal layout parity: same growth policy, same head/tail positions
            assertEquals(hppc.buffer.length, port.buffer.length, "growth policy must match");
            assertEquals(hppc.head, port.head);
            assertEquals(hppc.tail, port.tail);
            assertArrayEquals(hppc.toArray(), port.toArray());

            hppc.clear();
            port.clear();
            assertEquals(hppc.size(), port.size());
        }
    }

    @Test
    public void longArrayDeque_expectedElementsConstructorParity() {
        for (int expected : new int[]{0, 1, 4, 10, 100}) {
            com.carrotsearch.hppc.LongArrayDeque hppc = new com.carrotsearch.hppc.LongArrayDeque(expected);
            LongArrayDeque port = new LongArrayDeque(expected);
            assertEquals(hppc.buffer.length, port.buffer.length, "expected=" + expected);
        }
    }
}
