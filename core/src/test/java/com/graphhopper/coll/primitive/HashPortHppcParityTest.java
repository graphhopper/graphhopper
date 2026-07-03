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

import com.carrotsearch.hppc.HashOrderMixing;
import com.carrotsearch.hppc.HashOrderMixingStrategy;
import com.carrotsearch.hppc.cursors.IntCursor;
import com.carrotsearch.hppc.cursors.IntLongCursor;
import com.carrotsearch.hppc.cursors.IntObjectCursor;
import com.carrotsearch.hppc.cursors.LongCursor;
import com.carrotsearch.hppc.cursors.LongIntCursor;
import com.carrotsearch.hppc.cursors.LongLongCursor;
import com.carrotsearch.hppc.cursors.LongObjectCursor;
import com.carrotsearch.hppc.cursors.ObjectIntCursor;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * THE order-identity harvest for the hppc hash-container ports (plan batch H2): drives the hppc
 * 0.8.1 originals (with the deterministic constant order-mixing the GH* family uses) and the
 * com.graphhopper.coll.primitive ports through IDENTICAL insert/remove sequences (fixed seeds,
 * several sizes across resize boundaries, empty keys included) and asserts that the ITERATION
 * SEQUENCES ARE IDENTICAL element by element — in BOTH hppc iteration orders (forEach/procedure
 * order: empty key first; cursor-iterator order: empty key last), plus toString/toArray parity.
 *
 * This class is deleted in batch H8 together with the hppc dependency; the hppc-free absolute
 * sequence pins live in {@link HashPortOrderPinTest} and survive.
 */
public class HashPortHppcParityTest {

    private static final long GH_SEED = 123321123321123312L; // GHIntObjectHashMap.DETERMINISTIC
    private static final HashOrderMixingStrategy GH_MIXER = HashOrderMixing.constant(GH_SEED);

    // sizes crossing several resize boundaries (default: initial resizeAt is 6, then 12, 24, ...)
    private static final int[] SIZES = {5, 100, 2000, 30_000};

    // ==================== int-keyed ====================

    @Test
    public void intObjectHashMap() {
        for (int size : SIZES) {
            for (int keyRange : new int[]{50, Integer.MAX_VALUE}) {
                com.carrotsearch.hppc.IntObjectHashMap<Integer> hppc =
                        new com.carrotsearch.hppc.IntObjectHashMap<>(4, 0.75, GH_MIXER);
                IntObjectHashMap<Integer> port = new IntObjectHashMap<>();
                Random rnd = new Random(42 + size);

                for (int i = 0; i < size; i++) {
                    int key = nextKey(rnd, keyRange, i);
                    if (i % 5 == 4) {
                        assertEquals(hppc.remove(key), port.remove(key));
                    } else {
                        assertEquals(hppc.put(key, i), port.put(key, i));
                    }
                    if (i == size / 2) compareIntObject(hppc, port);
                }
                compareIntObject(hppc, port);

                // the ensureCapacity-driven rehash takes a different code path than put-growth
                hppc.ensureCapacity(4 * size);
                port.ensureCapacity(4 * size);
                compareIntObject(hppc, port);
            }
        }
    }

    @Test
    public void intObjectHashMap_bridgePathFinderVariant() {
        // BridgePathFinder pins its result map with IntObjectHashMap(16, 0.5, constant(123))
        com.carrotsearch.hppc.IntObjectHashMap<String> hppc =
                new com.carrotsearch.hppc.IntObjectHashMap<>(16, 0.5, HashOrderMixing.constant(123));
        IntObjectHashMap<String> port = new IntObjectHashMap<>(16, 0.5, 123);
        Random rnd = new Random(4711);
        for (int i = 0; i < 5000; i++) {
            int key = nextKey(rnd, 10_000, i);
            if (i % 4 == 3) {
                assertEquals(hppc.remove(key), port.remove(key));
            } else {
                assertEquals(hppc.put(key, "v" + i), port.put(key, "v" + i));
            }
        }
        List<String> expected = new ArrayList<>(), actual = new ArrayList<>();
        hppc.forEach((com.carrotsearch.hppc.procedures.IntObjectProcedure<String>) (k, v) -> expected.add(k + "=" + v));
        port.forEach((k, v) -> {
            actual.add(k + "=" + v);
            return kotlin.Unit.INSTANCE;
        });
        assertEquals(expected, actual);
        expected.clear();
        actual.clear();
        for (IntObjectCursor<String> c : hppc) expected.add(c.key + "=" + c.value);
        port.forEachInIteratorOrder((k, v) -> {
            actual.add(k + "=" + v);
            return kotlin.Unit.INSTANCE;
        });
        assertEquals(expected, actual);
    }

    private void compareIntObject(com.carrotsearch.hppc.IntObjectHashMap<Integer> hppc, IntObjectHashMap<Integer> port) {
        assertEquals(hppc.size(), port.size());
        List<String> expected = new ArrayList<>(), actual = new ArrayList<>();
        // procedure order (empty key first)
        hppc.forEach((com.carrotsearch.hppc.procedures.IntObjectProcedure<Integer>) (k, v) -> expected.add(k + "=" + v));
        port.forEach((k, v) -> {
            actual.add(k + "=" + v);
            return kotlin.Unit.INSTANCE;
        });
        assertEquals(expected, actual, "procedure-order (forEach) sequences differ");
        // cursor-iterator order (empty key last)
        expected.clear();
        actual.clear();
        for (IntObjectCursor<Integer> c : hppc) expected.add(c.key + "=" + c.value);
        port.forEachInIteratorOrder((k, v) -> {
            actual.add(k + "=" + v);
            return kotlin.Unit.INSTANCE;
        });
        assertEquals(expected, actual, "iterator-order sequences differ");
        assertArrayEquals(hppc.keys().toArray(), port.keysToArray());
        assertEquals(hppc.toString(), port.toString());
    }

    @Test
    public void intLongHashMap() {
        for (int size : SIZES) {
            com.carrotsearch.hppc.IntLongHashMap hppc = new com.carrotsearch.hppc.IntLongHashMap(4, 0.75, GH_MIXER);
            IntLongHashMap port = new IntLongHashMap();
            Random rnd = new Random(1 + size);

            for (int i = 0; i < size; i++) {
                int key = nextKey(rnd, 5000, i);
                switch (i % 7) {
                    case 4:
                        assertEquals(hppc.remove(key), port.remove(key));
                        break;
                    case 5:
                        assertEquals(hppc.addTo(key, i), port.addTo(key, i));
                        break;
                    case 6:
                        assertEquals(hppc.putOrAdd(key, i, 3), port.putOrAdd(key, i, 3));
                        break;
                    default:
                        assertEquals(hppc.put(key, i * 31L), port.put(key, i * 31L));
                }
                assertEquals(hppc.get(key), port.get(key));
            }

            assertEquals(hppc.size(), port.size());
            List<String> expected = new ArrayList<>(), actual = new ArrayList<>();
            hppc.forEach((com.carrotsearch.hppc.procedures.IntLongProcedure) (k, v) -> expected.add(k + "=" + v));
            port.forEach((k, v) -> {
                actual.add(k + "=" + v);
                return kotlin.Unit.INSTANCE;
            });
            assertEquals(expected, actual);
            expected.clear();
            actual.clear();
            for (IntLongCursor c : hppc) expected.add(c.key + "=" + c.value);
            port.forEachInIteratorOrder((k, v) -> {
                actual.add(k + "=" + v);
                return kotlin.Unit.INSTANCE;
            });
            assertEquals(expected, actual);
            assertArrayEquals(hppc.keys().toArray(), port.keysToArray());
            assertEquals(hppc.toString(), port.toString());
        }
    }

    @Test
    public void intHashSet() {
        for (int size : SIZES) {
            com.carrotsearch.hppc.IntHashSet hppc = new com.carrotsearch.hppc.IntHashSet(4, 0.75, GH_MIXER);
            IntHashSet port = new IntHashSet();
            driveIntSet(hppc, port, new Random(7 + size), size);
        }
        // GH-wrapper-style capacity 10
        com.carrotsearch.hppc.IntHashSet hppc = new com.carrotsearch.hppc.IntHashSet(10, 0.75, GH_MIXER);
        IntHashSet port = new IntHashSet(10);
        driveIntSet(hppc, port, new Random(99), 5000);
    }

    @Test
    public void intScatterSet() {
        for (int size : SIZES) {
            com.carrotsearch.hppc.IntScatterSet hppc = new com.carrotsearch.hppc.IntScatterSet();
            IntScatterSet port = new IntScatterSet();
            driveIntSet(hppc, port, new Random(13 + size), size);
        }
    }

    private void driveIntSet(com.carrotsearch.hppc.IntHashSet hppc, IntHashSet port, Random rnd, int size) {
        for (int i = 0; i < size; i++) {
            int key = nextKey(rnd, 5000, i);
            if (i % 5 == 4) {
                assertEquals(hppc.remove(key), port.remove(key));
            } else {
                assertEquals(hppc.add(key), port.add(key));
            }
            assertEquals(hppc.contains(key), port.contains(key));
        }
        assertEquals(hppc.size(), port.size());
        assertArrayEquals(hppc.toArray(), port.toArray(), "set toArray order differs");
        List<Integer> expected = new ArrayList<>(), actual = new ArrayList<>();
        hppc.forEach((com.carrotsearch.hppc.procedures.IntProcedure) expected::add);
        port.forEach(k -> {
            actual.add(k);
            return kotlin.Unit.INSTANCE;
        });
        assertEquals(expected, actual, "set procedure-order sequences differ");
        expected.clear();
        actual.clear();
        for (IntCursor c : hppc) expected.add(c.value);
        port.forEachInIteratorOrder(k -> {
            actual.add(k);
            return kotlin.Unit.INSTANCE;
        });
        assertEquals(expected, actual, "set iterator-order sequences differ");
        assertEquals(hppc.toString(), port.toString());
    }

    // ==================== long-keyed ====================

    @Test
    public void longObjectHashMap() {
        for (int size : SIZES) {
            com.carrotsearch.hppc.LongObjectHashMap<Integer> hppc =
                    new com.carrotsearch.hppc.LongObjectHashMap<>(4, 0.75, GH_MIXER);
            LongObjectHashMap<Integer> port = new LongObjectHashMap<>();
            Random rnd = new Random(3 + size);

            for (int i = 0; i < size; i++) {
                long key = nextLongKey(rnd, i);
                if (i % 5 == 4) {
                    assertEquals(hppc.remove(key), port.remove(key));
                } else {
                    assertEquals(hppc.put(key, i), port.put(key, i));
                }
                assertEquals(hppc.get(key), port.get(key));
            }

            assertEquals(hppc.size(), port.size());
            List<String> expected = new ArrayList<>(), actual = new ArrayList<>();
            hppc.forEach((com.carrotsearch.hppc.procedures.LongObjectProcedure<Integer>) (k, v) -> expected.add(k + "=" + v));
            port.forEach((k, v) -> {
                actual.add(k + "=" + v);
                return kotlin.Unit.INSTANCE;
            });
            assertEquals(expected, actual);
            expected.clear();
            actual.clear();
            for (LongObjectCursor<Integer> c : hppc) expected.add(c.key + "=" + c.value);
            port.forEachInIteratorOrder((k, v) -> {
                actual.add(k + "=" + v);
                return kotlin.Unit.INSTANCE;
            });
            assertEquals(expected, actual);
            assertArrayEquals(hppc.keys().toArray(), port.keysToArray());
            assertEquals(hppc.toString(), port.toString());
        }
    }

    @Test
    public void longLongHashMap() {
        for (int size : SIZES) {
            com.carrotsearch.hppc.LongLongHashMap hppc = new com.carrotsearch.hppc.LongLongHashMap(4, 0.75, GH_MIXER);
            LongLongHashMap port = new LongLongHashMap();
            Random rnd = new Random(17 + size);

            for (int i = 0; i < size; i++) {
                long key = nextLongKey(rnd, i);
                if (i % 5 == 4) {
                    assertEquals(hppc.remove(key), port.remove(key));
                } else if (i % 7 == 6) {
                    assertEquals(hppc.addTo(key, i), port.addTo(key, i));
                } else {
                    assertEquals(hppc.put(key, i * 37L), port.put(key, i * 37L));
                }
                assertEquals(hppc.get(key), port.get(key));
            }

            assertEquals(hppc.size(), port.size());
            List<String> expected = new ArrayList<>(), actual = new ArrayList<>();
            hppc.forEach((com.carrotsearch.hppc.procedures.LongLongProcedure) (k, v) -> expected.add(k + "=" + v));
            port.forEach((k, v) -> {
                actual.add(k + "=" + v);
                return kotlin.Unit.INSTANCE;
            });
            assertEquals(expected, actual);
            expected.clear();
            actual.clear();
            for (LongLongCursor c : hppc) expected.add(c.key + "=" + c.value);
            port.forEachInIteratorOrder((k, v) -> {
                actual.add(k + "=" + v);
                return kotlin.Unit.INSTANCE;
            });
            assertEquals(expected, actual);
            assertArrayEquals(hppc.keys().toArray(), port.keysToArray());
            assertEquals(hppc.toString(), port.toString());
        }
    }

    @Test
    public void longHashSetAndScatterSet() {
        for (int size : SIZES) {
            com.carrotsearch.hppc.LongHashSet hppcHash = new com.carrotsearch.hppc.LongHashSet(4, 0.75, GH_MIXER);
            LongHashSet portHash = new LongHashSet();
            driveLongSet(hppcHash, portHash, new Random(23 + size), size);

            com.carrotsearch.hppc.LongScatterSet hppcScatter = new com.carrotsearch.hppc.LongScatterSet();
            LongScatterSet portScatter = new LongScatterSet();
            driveLongSet(hppcScatter, portScatter, new Random(29 + size), size);
        }
    }

    private void driveLongSet(com.carrotsearch.hppc.LongHashSet hppc, LongHashSet port, Random rnd, int size) {
        for (int i = 0; i < size; i++) {
            long key = nextLongKey(rnd, i);
            if (i % 5 == 4) {
                assertEquals(hppc.remove(key), port.remove(key));
            } else {
                assertEquals(hppc.add(key), port.add(key));
            }
            assertEquals(hppc.contains(key), port.contains(key));
        }
        assertEquals(hppc.size(), port.size());
        assertArrayEquals(hppc.toArray(), port.toArray());
        List<Long> expected = new ArrayList<>(), actual = new ArrayList<>();
        hppc.forEach((com.carrotsearch.hppc.procedures.LongProcedure) expected::add);
        port.forEach(k -> {
            actual.add(k);
            return kotlin.Unit.INSTANCE;
        });
        assertEquals(expected, actual);
        expected.clear();
        actual.clear();
        for (LongCursor c : hppc) expected.add(c.value);
        port.forEachInIteratorOrder(k -> {
            actual.add(k);
            return kotlin.Unit.INSTANCE;
        });
        assertEquals(expected, actual);
        assertEquals(hppc.toString(), port.toString());
    }

    @Test
    public void longIntScatterMap() {
        for (int size : SIZES) {
            com.carrotsearch.hppc.LongIntScatterMap hppc = new com.carrotsearch.hppc.LongIntScatterMap();
            LongIntScatterMap port = new LongIntScatterMap();
            Random rnd = new Random(31 + size);

            for (int i = 0; i < size; i++) {
                long key = nextLongKey(rnd, i);
                if (i % 5 == 4) {
                    assertEquals(hppc.remove(key), port.remove(key));
                } else {
                    assertEquals(hppc.put(key, i), port.put(key, i));
                }
                assertEquals(hppc.get(key), port.get(key));
                // indexOf-family parity (used by RestrictionSetter/WayToEdgesMap)
                int hppcIndex = hppc.indexOf(key), portIndex = port.indexOf(key);
                assertEquals(hppcIndex >= 0, portIndex >= 0);
                if (hppcIndex >= 0)
                    assertEquals(hppc.indexGet(hppcIndex), port.indexGet(portIndex));
            }

            assertEquals(hppc.size(), port.size());
            List<String> expected = new ArrayList<>(), actual = new ArrayList<>();
            hppc.forEach((com.carrotsearch.hppc.procedures.LongIntProcedure) (k, v) -> expected.add(k + "=" + v));
            port.forEach((k, v) -> {
                actual.add(k + "=" + v);
                return kotlin.Unit.INSTANCE;
            });
            assertEquals(expected, actual);
            expected.clear();
            actual.clear();
            for (LongIntCursor c : hppc) expected.add(c.key + "=" + c.value);
            port.forEachInIteratorOrder((k, v) -> {
                actual.add(k + "=" + v);
                return kotlin.Unit.INSTANCE;
            });
            assertEquals(expected, actual);
            assertEquals(hppc.toString(), port.toString());
        }
    }

    // ==================== object-keyed ====================

    @Test
    public void objectIntHashMap() {
        for (int size : SIZES) {
            com.carrotsearch.hppc.ObjectIntHashMap<String> hppc =
                    new com.carrotsearch.hppc.ObjectIntHashMap<>(4, 0.75, GH_MIXER);
            ObjectIntHashMap<String> port = new ObjectIntHashMap<>();
            Random rnd = new Random(37 + size);

            for (int i = 0; i < size; i++) {
                // String.hashCode is stable, so hash order is reproducible like in GH usage
                String key = i % 41 == 0 ? null : "key" + nextKey(rnd, 5000, i);
                if (i % 5 == 4) {
                    assertEquals(hppc.remove(key), port.remove(key));
                } else {
                    assertEquals(hppc.put(key, i), port.put(key, i));
                }
                assertEquals(hppc.get(key), port.get(key));
                assertEquals(hppc.getOrDefault(key, -7), port.getOrDefault(key, -7));
            }

            assertEquals(hppc.size(), port.size());
            List<String> expected = new ArrayList<>(), actual = new ArrayList<>();
            hppc.forEach((com.carrotsearch.hppc.procedures.ObjectIntProcedure<String>) (k, v) -> expected.add(k + "=" + v));
            port.forEach((k, v) -> {
                actual.add(k + "=" + v);
                return kotlin.Unit.INSTANCE;
            });
            assertEquals(expected, actual);
            expected.clear();
            actual.clear();
            for (ObjectIntCursor<String> c : hppc) expected.add(c.key + "=" + c.value);
            port.forEachInIteratorOrder((k, v) -> {
                actual.add(k + "=" + v);
                return kotlin.Unit.INSTANCE;
            });
            assertEquals(expected, actual);
            assertEquals(hppc.toString(), port.toString());
        }
    }

    // ==================== helpers ====================

    /** every ~40th key is the empty key (0), the rest random in +/- keyRange. */
    private static int nextKey(Random rnd, int keyRange, int i) {
        if (i % 40 == 39) return 0;
        int key = rnd.nextInt(keyRange) - keyRange / 2;
        return key;
    }

    private static long nextLongKey(Random rnd, int i) {
        if (i % 40 == 39) return 0L;
        // mix small (collision-prone after masking) and full-range keys
        return i % 2 == 0 ? rnd.nextInt(5000) - 2500 : rnd.nextLong();
    }
}
