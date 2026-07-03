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

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

public class IntDoubleHashMapTest {

    @Test
    public void testBasics() {
        IntDoubleHashMap map = new IntDoubleHashMap();
        assertTrue(map.isEmpty());
        map.put(4, 2.5);
        map.put(-1, -0.25);
        map.put(0, 123.75);
        assertEquals(3, map.size());
        assertEquals(2.5, map.get(4));
        assertEquals(-0.25, map.get(-1));
        assertEquals(123.75, map.get(0));
        assertTrue(map.containsKey(0));
        assertFalse(map.containsKey(5));
        // hppc semantics: missing key -> 0.0
        assertEquals(0.0, map.get(5));
        assertEquals(7.5, map.getOrDefault(5, 7.5));
        assertEquals(2.5, map.getOrDefault(4, 7.5));

        map.put(4, 9.0);
        assertEquals(9.0, map.get(4));
        assertEquals(3, map.size());

        map.remove(4);
        assertFalse(map.containsKey(4));
        assertEquals(2, map.size());
        map.clear();
        assertTrue(map.isEmpty());
    }

    @Test
    public void testNaNRawBitsPreserved() {
        IntDoubleHashMap map = new IntDoubleHashMap();
        long nanPayload = Double.doubleToRawLongBits(Double.NaN) | 0x123L;
        double customNaN = Double.longBitsToDouble(nanPayload);
        map.put(1, customNaN);
        map.put(2, Double.NaN);
        // exact raw bit patterns round-trip (same as storing in a double[])
        assertEquals(nanPayload, Double.doubleToRawLongBits(map.get(1)));
        assertEquals(Double.doubleToRawLongBits(Double.NaN), Double.doubleToRawLongBits(map.get(2)));
        assertTrue(map.containsKey(1));
        // keyed access is unaffected by NaN values
        assertEquals(2, map.size());
    }

    @Test
    public void testInfinitiesAndSignedZero() {
        IntDoubleHashMap map = new IntDoubleHashMap();
        map.put(1, Double.POSITIVE_INFINITY);
        map.put(2, Double.NEGATIVE_INFINITY);
        map.put(3, -0.0);
        assertEquals(Double.POSITIVE_INFINITY, map.get(1));
        assertEquals(Double.NEGATIVE_INFINITY, map.get(2));
        assertEquals(Double.doubleToRawLongBits(-0.0), Double.doubleToRawLongBits(map.get(3)));
        // addTo puts the increment as-is for absent keys
        assertEquals(-0.0, map.addTo(4, -0.0));
        assertEquals(Double.doubleToRawLongBits(-0.0), Double.doubleToRawLongBits(map.get(4)));
        assertEquals(3.5, map.addTo(4, 3.5));
        assertEquals(3.5, map.get(4));
    }

    @Test
    public void testForEachAndRandomizedAgainstJavaUtil() {
        Random rnd = new Random(7);
        IntDoubleHashMap map = new IntDoubleHashMap();
        Map<Integer, Double> reference = new HashMap<>();
        for (int i = 0; i < 50_000; i++) {
            int key = rnd.nextInt(5000) - 2500;
            int op = rnd.nextInt(4);
            if (op < 2) {
                double value = rnd.nextDouble();
                map.put(key, value);
                reference.put(key, value);
            } else if (op == 2) {
                map.remove(key);
                reference.remove(key);
            } else {
                assertEquals(reference.containsKey(key), map.containsKey(key));
                assertEquals(reference.getOrDefault(key, 0.0), map.get(key));
            }
        }
        assertEquals(reference.size(), map.size());

        Map<Integer, Double> collected = new HashMap<>();
        map.forEach((k, v) -> {
            collected.put(k, v);
            return kotlin.Unit.INSTANCE;
        });
        assertEquals(reference, collected);
    }
}
