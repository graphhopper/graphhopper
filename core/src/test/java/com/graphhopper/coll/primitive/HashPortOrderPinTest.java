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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * ABSOLUTE iteration-order pins for the hppc hash-container ports. All expected literals below
 * were HARVESTED FROM LIVE HPPC 0.8.1 (with `HashOrderMixing.constant(123321123321123312L)` as
 * used by the GH* family, and `constant(123)` for the BridgePathFinder variant) before hppc's
 * removal — they are the durable proof that the ports iterate in hppc's exact order. The
 * element-by-element differential against live hppc lives in {@link HashPortHppcParityTest}
 * (deleted with the hppc dependency in batch H8); THIS test must never be regenerated from the
 * ports themselves — its literals pin the stored-graph-relevant hash order forever.
 *
 * Each scripted sequence crosses at least one resize boundary (initial resizeAt is 6 for the
 * default capacity) and ends with the empty key (0 / null) PRESENT so that the hppc order
 * asymmetry stays pinned: forEach visits the empty key FIRST, iterator order visits it LAST.
 * The LCG sequences additionally cross several resize boundaries (~260-290 live entries) with
 * interleaved removals (shift-on-remove backward compaction).
 */
public class HashPortOrderPinTest {

    // the scripted op sequence (shared with the harvest generator)
    private static final int[] KEYS = {1, 2, 3, 0, 42, -1, 7, 8, 9, 10, 11, 12, -13, 64, 100, 5};
    private static final int[] REMOVES = {3, 0, 11, 100};
    private static final int[] READDS = {200, 3, 0};
    private static final String[] SKEYS = {"a", "b", "c", null, "forty-two", "minus", "g", "h", "i", "j", "k", "l", "m", "n", "o", "p"};
    private static final String[] SREMOVES = {"c", null, "k", "o"};
    private static final String[] SREADDS = {"zz", "c", null};

    // ==================== literals harvested from hppc 0.8.1 ====================

    private static final String INT_OBJECT_FOREACH = "0=18,42=4,5=15,64=13,3=17,9=8,10=9,2=1,-1=5,7=6,1=0,8=7,-13=12,200=16,12=11";
    private static final String INT_OBJECT_ITER = "42=4,5=15,64=13,3=17,9=8,10=9,2=1,-1=5,7=6,1=0,8=7,-13=12,200=16,12=11,0=18";
    private static final String INT_LONG_FOREACH = "0=18,42=4,5=15,64=13,3=17,9=8,10=9,2=1,-1=5,7=6,1=0,8=7,-13=12,200=16,12=11";
    private static final String INT_LONG_ITER = "42=4,5=15,64=13,3=17,9=8,10=9,2=1,-1=5,7=6,1=0,8=7,-13=12,200=16,12=11,0=18";
    private static final String INT_HASH_SET_FOREACH = "0,42,5,64,3,9,10,2,-1,7,1,8,-13,200,12";
    private static final String INT_HASH_SET_ITER = "42,5,64,3,9,10,2,-1,7,1,8,-13,200,12,0";
    private static final int[] INT_HASH_SET_TO_ARRAY = {0, 42, 5, 64, 3, 9, 10, 2, -1, 7, 1, 8, -13, 200, 12};
    private static final String INT_SCATTER_SET_FOREACH = "0,42,5,7,3,1,-1,10,9,8,-13,12,2,64,200";
    private static final String INT_SCATTER_SET_ITER = "42,5,7,3,1,-1,10,9,8,-13,12,2,64,200,0";
    private static final String LONG_OBJECT_FOREACH = "0=20,3=19,5=15,9=8,-13=12,8=7,200=18,10=9,1=0,42=4,64=13,-1=5,-2199023255552=17,12=11,2=1,7=6";
    private static final String LONG_OBJECT_ITER = "3=19,5=15,9=8,-13=12,8=7,200=18,10=9,1=0,42=4,64=13,-1=5,-2199023255552=17,12=11,2=1,7=6,0=20";
    private static final String LONG_LONG_FOREACH = "0=20,3=19,5=15,9=8,-13=12,8=7,200=18,10=9,1=0,42=4,64=13,-1=5,-2199023255552=17,12=11,2=1,7=6";
    private static final String LONG_LONG_ITER = "3=19,5=15,9=8,-13=12,8=7,200=18,10=9,1=0,42=4,64=13,-1=5,-2199023255552=17,12=11,2=1,7=6,0=20";
    private static final String LONG_HASH_SET_FOREACH = "0,3,5,9,-13,8,200,10,1,42,64,-1,-2199023255552,12,2,7";
    private static final String LONG_HASH_SET_ITER = "3,5,9,-13,8,200,10,1,42,64,-1,-2199023255552,12,2,7,0";
    private static final String LONG_SCATTER_SET_FOREACH = "0,-2199023255552,7,8,200,10,1,-1,12,3,5,9,2,-13,42,64";
    private static final String LONG_SCATTER_SET_ITER = "-2199023255552,7,8,200,10,1,-1,12,3,5,9,2,-13,42,64,0";
    private static final String LONG_INT_SCATTER_FOREACH = "0=20,-2199023255552=17,7=6,8=7,200=18,10=9,1=0,-1=5,12=11,3=19,5=15,9=8,2=1,-13=12,42=4,64=13";
    private static final String LONG_INT_SCATTER_ITER = "-2199023255552=17,7=6,8=7,200=18,10=9,1=0,-1=5,12=11,3=19,5=15,9=8,2=1,-13=12,42=4,64=13,0=20";
    private static final String OBJECT_INT_FOREACH = "null=18,b=1,n=13,forty-two=4,p=15,j=9,a=0,h=7,minus=5,c=17,l=11,g=6,i=8,zz=16,m=12";
    private static final String OBJECT_INT_ITER = "b=1,n=13,forty-two=4,p=15,j=9,a=0,h=7,minus=5,c=17,l=11,g=6,i=8,zz=16,m=12,null=18";
    private static final String BRIDGE_SEED123_FOREACH = "0=18,1=0,200=16,7=6,8=7,12=11,3=17,2=1,42=4,64=13,-1=5,-13=12,9=8,10=9,5=15";
    private static final String BRIDGE_SEED123_ITER = "1=0,200=16,7=6,8=7,12=11,3=17,2=1,42=4,64=13,-1=5,-13=12,9=8,10=9,5=15,0=18";

    private static final int LCG_INT_OBJECT_SIZE = 262;
    private static final long LCG_INT_OBJECT_FOREACH_HASH = 6142627420710363101L;
    private static final long LCG_INT_OBJECT_ITER_HASH = -8801847651701964387L;
    private static final String LCG_INT_OBJECT_ITER_FIRST10 = "52=350,385=363,-151=106,408=290,147=287,256=286,-99=108,12=332,-146=186,47=262";
    private static final int LCG_LONG_LONG_SIZE = 284;
    private static final long LCG_LONG_LONG_FOREACH_HASH = 6688633561558794700L;
    private static final long LCG_LONG_LONG_ITER_HASH = -4548053824245071668L;
    private static final int LCG_INT_SET_SIZE = 262;
    private static final long LCG_INT_SET_FOREACH_HASH = -1269900019999157062L;
    private static final long LCG_INT_SET_ITER_HASH = -2473412472554765690L;
    private static final int LCG_OBJECT_INT_SIZE = 264;
    private static final long LCG_OBJECT_INT_FOREACH_HASH = 457887268500500901L;
    private static final long LCG_OBJECT_INT_ITER_HASH = 2923278523624931429L;

    // ==================== scripted sequence pins ====================

    @Test
    public void intObjectHashMap() {
        IntObjectHashMap<Integer> m = new IntObjectHashMap<>();
        int vi = 0;
        for (int k : KEYS) m.put(k, vi++);
        for (int k : REMOVES) m.remove(k);
        for (int k : READDS) m.put(k, vi++);
        List<String> fe = new ArrayList<>(), it = new ArrayList<>();
        m.forEach((k, v) -> {
            fe.add(k + "=" + v);
            return kotlin.Unit.INSTANCE;
        });
        m.forEachInIteratorOrder((k, v) -> {
            it.add(k + "=" + v);
            return kotlin.Unit.INSTANCE;
        });
        assertEquals(INT_OBJECT_FOREACH, String.join(",", fe));
        assertEquals(INT_OBJECT_ITER, String.join(",", it));
        assertEquals("[" + INT_OBJECT_ITER.replace(",", ", ").replace("=", "=>") + "]", m.toString());
    }

    @Test
    public void intLongHashMap() {
        IntLongHashMap m = new IntLongHashMap();
        int vi = 0;
        for (int k : KEYS) m.put(k, vi++);
        for (int k : REMOVES) m.remove(k);
        for (int k : READDS) m.put(k, vi++);
        List<String> fe = new ArrayList<>(), it = new ArrayList<>();
        m.forEach((k, v) -> {
            fe.add(k + "=" + v);
            return kotlin.Unit.INSTANCE;
        });
        m.forEachInIteratorOrder((k, v) -> {
            it.add(k + "=" + v);
            return kotlin.Unit.INSTANCE;
        });
        assertEquals(INT_LONG_FOREACH, String.join(",", fe));
        assertEquals(INT_LONG_ITER, String.join(",", it));
    }

    @Test
    public void intHashSet() {
        IntHashSet s = new IntHashSet();
        for (int k : KEYS) s.add(k);
        for (int k : REMOVES) s.remove(k);
        for (int k : READDS) s.add(k);
        List<String> fe = new ArrayList<>(), it = new ArrayList<>();
        s.forEach(k -> {
            fe.add(String.valueOf(k));
            return kotlin.Unit.INSTANCE;
        });
        s.forEachInIteratorOrder(k -> {
            it.add(String.valueOf(k));
            return kotlin.Unit.INSTANCE;
        });
        assertEquals(INT_HASH_SET_FOREACH, String.join(",", fe));
        assertEquals(INT_HASH_SET_ITER, String.join(",", it));
        assertArrayEquals(INT_HASH_SET_TO_ARRAY, s.toArray());
    }

    @Test
    public void intScatterSet() {
        IntScatterSet s = new IntScatterSet();
        for (int k : KEYS) s.add(k);
        for (int k : REMOVES) s.remove(k);
        for (int k : READDS) s.add(k);
        List<String> fe = new ArrayList<>(), it = new ArrayList<>();
        s.forEach(k -> {
            fe.add(String.valueOf(k));
            return kotlin.Unit.INSTANCE;
        });
        s.forEachInIteratorOrder(k -> {
            it.add(String.valueOf(k));
            return kotlin.Unit.INSTANCE;
        });
        assertEquals(INT_SCATTER_SET_FOREACH, String.join(",", fe));
        assertEquals(INT_SCATTER_SET_ITER, String.join(",", it));
    }

    @Test
    public void longObjectHashMap() {
        LongObjectHashMap<Integer> m = new LongObjectHashMap<>();
        int vi = 0;
        for (long k : longKeys()) m.put(k, vi++);
        for (int k : REMOVES) m.remove(k);
        m.remove(1099511627776L);
        for (int k : READDS) m.put(k, vi++);
        List<String> fe = new ArrayList<>(), it = new ArrayList<>();
        m.forEach((k, v) -> {
            fe.add(k + "=" + v);
            return kotlin.Unit.INSTANCE;
        });
        m.forEachInIteratorOrder((k, v) -> {
            it.add(k + "=" + v);
            return kotlin.Unit.INSTANCE;
        });
        assertEquals(LONG_OBJECT_FOREACH, String.join(",", fe));
        assertEquals(LONG_OBJECT_ITER, String.join(",", it));
    }

    @Test
    public void longLongHashMap() {
        LongLongHashMap m = new LongLongHashMap();
        int vi = 0;
        for (long k : longKeys()) m.put(k, vi++);
        for (int k : REMOVES) m.remove(k);
        m.remove(1099511627776L);
        for (int k : READDS) m.put(k, vi++);
        List<String> fe = new ArrayList<>(), it = new ArrayList<>();
        m.forEach((k, v) -> {
            fe.add(k + "=" + v);
            return kotlin.Unit.INSTANCE;
        });
        m.forEachInIteratorOrder((k, v) -> {
            it.add(k + "=" + v);
            return kotlin.Unit.INSTANCE;
        });
        assertEquals(LONG_LONG_FOREACH, String.join(",", fe));
        assertEquals(LONG_LONG_ITER, String.join(",", it));
    }

    @Test
    public void longHashSet() {
        LongHashSet s = new LongHashSet();
        for (long k : longKeys()) s.add(k);
        for (int k : REMOVES) s.remove(k);
        s.remove(1099511627776L);
        for (int k : READDS) s.add(k);
        List<String> fe = new ArrayList<>(), it = new ArrayList<>();
        s.forEach(k -> {
            fe.add(String.valueOf(k));
            return kotlin.Unit.INSTANCE;
        });
        s.forEachInIteratorOrder(k -> {
            it.add(String.valueOf(k));
            return kotlin.Unit.INSTANCE;
        });
        assertEquals(LONG_HASH_SET_FOREACH, String.join(",", fe));
        assertEquals(LONG_HASH_SET_ITER, String.join(",", it));
    }

    @Test
    public void longScatterSet() {
        LongScatterSet s = new LongScatterSet();
        for (long k : longKeys()) s.add(k);
        for (int k : REMOVES) s.remove(k);
        s.remove(1099511627776L);
        for (int k : READDS) s.add(k);
        List<String> fe = new ArrayList<>(), it = new ArrayList<>();
        s.forEach(k -> {
            fe.add(String.valueOf(k));
            return kotlin.Unit.INSTANCE;
        });
        s.forEachInIteratorOrder(k -> {
            it.add(String.valueOf(k));
            return kotlin.Unit.INSTANCE;
        });
        assertEquals(LONG_SCATTER_SET_FOREACH, String.join(",", fe));
        assertEquals(LONG_SCATTER_SET_ITER, String.join(",", it));
    }

    @Test
    public void longIntScatterMap() {
        LongIntScatterMap m = new LongIntScatterMap();
        int vi = 0;
        for (long k : longKeys()) m.put(k, vi++);
        for (int k : REMOVES) m.remove(k);
        m.remove(1099511627776L);
        for (int k : READDS) m.put(k, vi++);
        List<String> fe = new ArrayList<>(), it = new ArrayList<>();
        m.forEach((k, v) -> {
            fe.add(k + "=" + v);
            return kotlin.Unit.INSTANCE;
        });
        m.forEachInIteratorOrder((k, v) -> {
            it.add(k + "=" + v);
            return kotlin.Unit.INSTANCE;
        });
        assertEquals(LONG_INT_SCATTER_FOREACH, String.join(",", fe));
        assertEquals(LONG_INT_SCATTER_ITER, String.join(",", it));
    }

    @Test
    public void objectIntHashMap() {
        ObjectIntHashMap<String> m = new ObjectIntHashMap<>();
        int vi = 0;
        for (String k : SKEYS) m.put(k, vi++);
        for (String k : SREMOVES) m.remove(k);
        for (String k : SREADDS) m.put(k, vi++);
        List<String> fe = new ArrayList<>(), it = new ArrayList<>();
        m.forEach((k, v) -> {
            fe.add(k + "=" + v);
            return kotlin.Unit.INSTANCE;
        });
        m.forEachInIteratorOrder((k, v) -> {
            it.add(k + "=" + v);
            return kotlin.Unit.INSTANCE;
        });
        assertEquals(OBJECT_INT_FOREACH, String.join(",", fe));
        assertEquals(OBJECT_INT_ITER, String.join(",", it));
    }

    @Test
    public void bridgePathFinderSeed123Variant() {
        // BridgePathFinder pins its result map order with IntObjectHashMap(16, 0.5, constant(123))
        IntObjectHashMap<Integer> m = new IntObjectHashMap<>(16, 0.5, 123);
        int vi = 0;
        for (int k : KEYS) m.put(k, vi++);
        for (int k : REMOVES) m.remove(k);
        for (int k : READDS) m.put(k, vi++);
        List<String> fe = new ArrayList<>(), it = new ArrayList<>();
        m.forEach((k, v) -> {
            fe.add(k + "=" + v);
            return kotlin.Unit.INSTANCE;
        });
        m.forEachInIteratorOrder((k, v) -> {
            it.add(k + "=" + v);
            return kotlin.Unit.INSTANCE;
        });
        assertEquals(BRIDGE_SEED123_FOREACH, String.join(",", fe));
        assertEquals(BRIDGE_SEED123_ITER, String.join(",", it));
    }

    // ==================== LCG sequence pins (multiple resizes + shift-on-remove) ====================

    @Test
    public void lcgIntObjectHashMap() {
        IntObjectHashMap<Integer> m = new IntObjectHashMap<>();
        long s = 20260703L;
        for (int i = 0; i < 400; i++) {
            s = s * 6364136223846793005L + 1442695040888963407L;
            int r = (int) (s >>> 33) % 1000;
            int key = (i % 40 == 38) ? 0 : r - 500;
            if (i % 5 == 4) m.remove(key);
            else m.put(key, i);
        }
        assertEquals(LCG_INT_OBJECT_SIZE, m.size());
        long[] h = {0, 0};
        List<String> first = new ArrayList<>();
        m.forEach((k, v) -> {
            h[0] = (h[0] * 31 + k) * 31 + v;
            return kotlin.Unit.INSTANCE;
        });
        m.forEachInIteratorOrder((k, v) -> {
            h[1] = (h[1] * 31 + k) * 31 + v;
            if (first.size() < 10) first.add(k + "=" + v);
            return kotlin.Unit.INSTANCE;
        });
        assertEquals(LCG_INT_OBJECT_FOREACH_HASH, h[0]);
        assertEquals(LCG_INT_OBJECT_ITER_HASH, h[1]);
        assertEquals(LCG_INT_OBJECT_ITER_FIRST10, String.join(",", first));
    }

    @Test
    public void lcgLongLongHashMap() {
        LongLongHashMap m = new LongLongHashMap();
        long s = 20260703L;
        for (int i = 0; i < 400; i++) {
            s = s * 6364136223846793005L + 1442695040888963407L;
            int r = (int) (s >>> 33) % 1000;
            long key = (i % 40 == 38) ? 0L : (i % 2 == 0 ? r - 500 : (r - 500) * 2862933555777941757L);
            if (i % 5 == 4) m.remove(key);
            else m.put(key, i);
        }
        assertEquals(LCG_LONG_LONG_SIZE, m.size());
        long[] h = {0, 0};
        m.forEach((k, v) -> {
            h[0] = (h[0] * 31 + Long.hashCode(k)) * 31 + v;
            return kotlin.Unit.INSTANCE;
        });
        m.forEachInIteratorOrder((k, v) -> {
            h[1] = (h[1] * 31 + Long.hashCode(k)) * 31 + v;
            return kotlin.Unit.INSTANCE;
        });
        assertEquals(LCG_LONG_LONG_FOREACH_HASH, h[0]);
        assertEquals(LCG_LONG_LONG_ITER_HASH, h[1]);
    }

    @Test
    public void lcgIntHashSet() {
        IntHashSet set = new IntHashSet(10); // GH-wrapper default capacity
        long s = 20260703L;
        for (int i = 0; i < 400; i++) {
            s = s * 6364136223846793005L + 1442695040888963407L;
            int r = (int) (s >>> 33) % 1000;
            int key = (i % 40 == 38) ? 0 : r - 500;
            if (i % 5 == 4) set.remove(key);
            else set.add(key);
        }
        assertEquals(LCG_INT_SET_SIZE, set.size());
        long[] h = {0, 0};
        set.forEach(k -> {
            h[0] = h[0] * 31 + k;
            return kotlin.Unit.INSTANCE;
        });
        set.forEachInIteratorOrder(k -> {
            h[1] = h[1] * 31 + k;
            return kotlin.Unit.INSTANCE;
        });
        assertEquals(LCG_INT_SET_FOREACH_HASH, h[0]);
        assertEquals(LCG_INT_SET_ITER_HASH, h[1]);
    }

    @Test
    public void lcgObjectIntHashMap() {
        ObjectIntHashMap<String> m = new ObjectIntHashMap<>();
        long s = 20260703L;
        for (int i = 0; i < 400; i++) {
            s = s * 6364136223846793005L + 1442695040888963407L;
            int r = (int) (s >>> 33) % 1000;
            String key = (i % 41 == 1) ? null : "k" + (r - 500);
            if (i % 5 == 4) m.remove(key);
            else m.put(key, i);
        }
        assertEquals(LCG_OBJECT_INT_SIZE, m.size());
        long[] h = {0, 0};
        m.forEach((k, v) -> {
            h[0] = (h[0] * 31 + (k == null ? 0 : k.hashCode())) * 31 + v;
            return kotlin.Unit.INSTANCE;
        });
        m.forEachInIteratorOrder((k, v) -> {
            h[1] = (h[1] * 31 + (k == null ? 0 : k.hashCode())) * 31 + v;
            return kotlin.Unit.INSTANCE;
        });
        assertEquals(LCG_OBJECT_INT_FOREACH_HASH, h[0]);
        assertEquals(LCG_OBJECT_INT_ITER_HASH, h[1]);
    }

    private static long[] longKeys() {
        long[] result = new long[KEYS.length + 2];
        for (int i = 0; i < KEYS.length; i++) result[i] = KEYS[i];
        result[KEYS.length] = 1099511627776L;      // 1L << 40
        result[KEYS.length + 1] = -2199023255552L; // -(1L << 41)
        return result;
    }
}
