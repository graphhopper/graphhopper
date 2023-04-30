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

import org.junit.jupiter.api.Test;

import java.util.LinkedHashSet;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Peter Karich
 */
public class GHLongLongBTreeTest {

    @Test
    public void testThrowException_IfPutting_NoNumber() {
        GHLongLongBTree instance = new GHLongLongBTree(2, 4);
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> instance.put(1, -1));
        assertTrue(ex.getMessage().contains("Value cannot be no_number_value -1"));
    }

    @Test
    public void testEmptyValueIfMissing() {
        GHLongLongBTree instance = new GHLongLongBTree(2, 4);
        long key = 9485854858458484L;
        assertEquals(-1, instance.put(key, 21));
        assertEquals(21, instance.get(key));
        assertEquals(-1, instance.get(404));
    }

    @Test
    public void testTwoSplits() {
        GHLongLongBTree instance = new GHLongLongBTree(3, 4);
        instance.put(1, 2);
        instance.put(2, 4);
        instance.put(3, 6);

        assertEquals(1, instance.height());
        instance.put(4, 8);
        assertEquals(2, instance.height());

        instance.put(5, 10);
        instance.put(6, 12);
        instance.put(7, 14);
        instance.put(8, 16);
        instance.put(9, 18);

        assertEquals(2, instance.height());
        instance.put(10, 20);
        assertEquals(3, instance.height());

        assertEquals(3, instance.height());
        assertEquals(10, instance.getSize());
        assertEquals(0, instance.getMemoryUsage());

        check(instance, 1);
    }

    @Test
    public void testSplitAndOverwrite() {
        GHLongLongBTree instance = new GHLongLongBTree(3, 4);
        instance.put(1, 2);
        instance.put(2, 4);
        instance.put(3, 6);
        instance.put(2, 5);

        assertEquals(3, instance.getSize());
        assertEquals(1, instance.height());

        assertEquals(5, instance.get(2));
        assertEquals(6, instance.get(3));
    }

    void check(GHLongLongBTree instance, int from) {
        for (int i = from; i < instance.getSize(); i++) {
            assertEquals(i * 2L, instance.get(i), "idx:" + i);
        }
    }

    @Test
    public void testPut() {
        GHLongLongBTree instance = new GHLongLongBTree(3, 4);
        instance.put(2, 4);
        assertEquals(4, instance.get(2));

        instance.put(7, 14);
        assertEquals(4, instance.get(2));
        assertEquals(14, instance.get(7));

        instance.put(5, 10);
        instance.put(6, 12);
        instance.put(3, 6);
        instance.put(4, 8);
        instance.put(9, 18);
        instance.put(0, 0);
        instance.put(1, 2);
        instance.put(8, 16);

        check(instance, 0);

        instance.put(10, 20);
        instance.put(11, 22);

        assertEquals(12, instance.getSize());
        assertEquals(3, instance.height());

        assertEquals(12, instance.get(6));
        check(instance, 0);
    }

    @Test
    public void testUpdate() {
        GHLongLongBTree instance = new GHLongLongBTree(2, 4);
        long result = instance.put(100, 10);
        assertEquals(instance.getNoNumberValue(), result);

        result = instance.get(100);
        assertEquals(10, result);

        result = instance.put(100, 9);
        assertEquals(10, result);

        result = instance.get(100);
        assertEquals(9, result);
    }

    @Test
    public void testNegativeValues() {
        GHLongLongBTree instance = new GHLongLongBTree(2, 5);

        // negative => two's complement
        byte[] bytes = instance.fromLong(-3);
        assertEquals(-3, instance.toLong(bytes));

        instance.put(0, -3);
        instance.put(4, -2);
        instance.put(3, Integer.MIN_VALUE);
        instance.put(2, 2L * Integer.MIN_VALUE);
        instance.put(1, 4L * Integer.MIN_VALUE);

        assertEquals(-3, instance.get(0));
        assertEquals(-2, instance.get(4));
        assertEquals(4L * Integer.MIN_VALUE, instance.get(1));
        assertEquals(2L * Integer.MIN_VALUE, instance.get(2));
        assertEquals(Integer.MIN_VALUE, instance.get(3));
    }

    @Test
    public void testNegativeKey() {
        GHLongLongBTree instance = new GHLongLongBTree(2, 5);

        instance.put(-3, 0);
        instance.put(-2, 4);
        instance.put(Integer.MIN_VALUE, 3);
        instance.put(2L * Integer.MIN_VALUE, 2);
        instance.put(4L * Integer.MIN_VALUE, 1);

        assertEquals(0, instance.get(-3));
        assertEquals(4, instance.get(-2));
        assertEquals(1, instance.get(4L * Integer.MIN_VALUE));
        assertEquals(2, instance.get(2L * Integer.MIN_VALUE));
        assertEquals(3, instance.get(Integer.MIN_VALUE));
    }

    @Test
    public void testInternalFromToLong() {
        Random rand = new Random(0);
        for (int byteCnt = 4; byteCnt < 9; byteCnt++) {
            for (int i = 0; i < 1000; i++) {
                GHLongLongBTree instance = new GHLongLongBTree(2, byteCnt);
                long val = rand.nextLong(instance.getMaxValue());
                byte[] bytes = instance.fromLong(val);
                assertEquals(val, instance.toLong(bytes));
            }
        }
    }

    @Test
    public void testLargeValue() {
        GHLongLongBTree instance = new GHLongLongBTree(2, 5);
        for (int key = 0; key < 100; key++) {
            long val = 1L << 32 - 1;
            for (int i = 0; i < 8; i++) {
                instance.put(key, val);
                assertEquals(val, instance.get(key), "i:" + i + ", key:" + key + ", val:" + val);
                val *= 2;
            }
        }
    }

    @Test
    public void testRandom() {
        final long seed = System.nanoTime();
        Random rand = new Random(seed);
        final int size = 10_000;
        for (int bytesPerValue = 4; bytesPerValue <= 8; bytesPerValue++) {
            for (int j = 3; j < 12; j += 4) {
                GHLongLongBTree instance = new GHLongLongBTree(j, bytesPerValue);
                Set<Integer> addedValues = new LinkedHashSet<>(size);
                for (int i = 0; i < size; i++) {
                    int val = rand.nextInt();
                    addedValues.add(val);
                    try {
                        instance.put(val, val);
                    } catch (Exception ex) {
                        ex.printStackTrace();
                        fail(j + "| Problem with " + i + ", seed: " + seed + " " + ex);
                    }

                    assertEquals(addedValues.size(), instance.getSize(), j + "| Size not equal to set! In " + i + " added " + val);
                }
                int i = 0;
                for (int val : addedValues) {
                    assertEquals(val, instance.get(val), j + "| Problem with " + i);
                    i++;
                }
                instance.optimize();
                i = 0;
                for (int val : addedValues) {
                    assertEquals(val, instance.get(val), j + "| Problem with " + i);
                    i++;
                }
            }
        }
    }
}
