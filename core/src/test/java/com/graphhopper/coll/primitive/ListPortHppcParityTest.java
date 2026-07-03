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

import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Differential test proving the {@link IntArrayList}/{@link LongArrayList}/{@link DoubleArrayList}
 * ports behave bit-identically to their live HPPC counterparts: same buffer-growth policy (so the
 * public {@code buffer.length} matches at every step), same iteration order, same
 * {@code from}/{@code toArray}/{@code toString}/{@code hashCode}/{@code equals}. It exercises HPPC
 * directly and therefore must be removed together with the HPPC dependency (batch H8).
 */
public class ListPortHppcParityTest {

    @Test
    void intArrayList_growthAndOps_matchHppc() {
        for (int cap : new int[]{0, 1, 4, 10, 15, 100}) {
            assertEquals(new com.carrotsearch.hppc.IntArrayList(cap).buffer.length,
                    new IntArrayList(cap).buffer.length, "ctor(" + cap + ") capacity");
        }
        // default ctor capacity
        assertEquals(new com.carrotsearch.hppc.IntArrayList().buffer.length,
                new IntArrayList().buffer.length, "default ctor capacity");

        Random rnd = new Random(42);
        com.carrotsearch.hppc.IntArrayList ref = new com.carrotsearch.hppc.IntArrayList();
        IntArrayList port = new IntArrayList();
        for (int i = 0; i < 5000; i++) {
            int v = rnd.nextInt();
            ref.add(v);
            port.add(v);
            assertEquals(ref.buffer.length, port.buffer.length, "buffer length at step " + i);
            assertEquals(ref.elementsCount, port.elementsCount);
        }
        assertArrayEquals(ref.toArray(), port.toArray());
        assertEquals(ref.toString(), port.toString());
        assertEquals(ref.hashCode(), port.hashCode(), "hashCode");

        // from()
        com.carrotsearch.hppc.IntArrayList refFrom = com.carrotsearch.hppc.IntArrayList.from(9, 8, 7, 6);
        IntArrayList portFrom = IntArrayList.from(9, 8, 7, 6);
        assertArrayEquals(refFrom.toArray(), portFrom.toArray());
        assertEquals(refFrom.buffer.length, portFrom.buffer.length);
        assertEquals(refFrom.hashCode(), portFrom.hashCode());
        assertEquals(refFrom.toString(), portFrom.toString());

        // insert / set / remove / indexOf / contains parity
        refFrom.insert(1, 99);
        portFrom.insert(1, 99);
        refFrom.set(0, -5);
        portFrom.set(0, -5);
        assertEquals(refFrom.remove(2), portFrom.remove(2));
        assertEquals(refFrom.indexOf(7), portFrom.indexOf(7));
        assertEquals(refFrom.contains(8), portFrom.contains(8));
        assertArrayEquals(refFrom.toArray(), portFrom.toArray());

        // iteration order
        int idx = 0;
        int[] expected = port.toArray();
        for (IntCursor c : port)
            assertEquals(expected[idx++], c.value);
        assertEquals(expected.length, idx);

        // equals is content + class based
        assertEquals(IntArrayList.from(1, 2, 3), IntArrayList.from(1, 2, 3));
        assertNotEquals(IntArrayList.from(1, 2, 3), IntArrayList.from(1, 2));
    }

    @Test
    void longArrayList_growthAndOps_matchHppc() {
        Random rnd = new Random(7);
        com.carrotsearch.hppc.LongArrayList ref = new com.carrotsearch.hppc.LongArrayList();
        LongArrayList port = new LongArrayList();
        for (int i = 0; i < 3000; i++) {
            long v = rnd.nextLong();
            ref.add(v);
            port.add(v);
            assertEquals(ref.buffer.length, port.buffer.length, "buffer length at step " + i);
        }
        assertArrayEquals(ref.toArray(), port.toArray());
        assertEquals(ref.toString(), port.toString());
        assertEquals(ref.hashCode(), port.hashCode());
        assertEquals(com.carrotsearch.hppc.LongArrayList.from(5L, 4L).hashCode(),
                LongArrayList.from(5L, 4L).hashCode());
    }

    @Test
    void doubleArrayList_growthAndOps_matchHppc() {
        Random rnd = new Random(11);
        com.carrotsearch.hppc.DoubleArrayList ref = new com.carrotsearch.hppc.DoubleArrayList();
        DoubleArrayList port = new DoubleArrayList();
        for (int i = 0; i < 3000; i++) {
            double v = rnd.nextDouble() * 1000;
            ref.add(v);
            port.add(v);
            assertEquals(ref.buffer.length, port.buffer.length, "buffer length at step " + i);
        }
        assertArrayEquals(ref.toArray(), port.toArray());
        assertEquals(ref.toString(), port.toString());
        assertEquals(ref.hashCode(), port.hashCode());
        assertEquals(DoubleArrayList.from(1.0, 2.0), DoubleArrayList.from(1.0, 2.0));
    }
}
