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

import com.graphhopper.storage.DataAccess;

import java.util.function.LongUnaryOperator;

/**
 * Planet-safe interleaved Eytzinger long-&gt;long map. Same cache-optimal layout as
 * {@code InterleavedEytzingerLongLongMap} (keys and values interleaved in Eytzinger/BFS order, value
 * in the key's cache line), but backed by a {@link DataAccess} instead of a {@code long[]}:
 *
 * <ul>
 *   <li>long-addressed, so it is NOT limited to ~2^31 entries (Java array cap) - it scales to a full
 *       planet import;</li>
 *   <li>can live off-heap on disk (MMAP {@link DataAccess}), so it does not consume Java heap;</li>
 *   <li>built by a streaming, DESTRUCTIVE drain of the source B-tree
 *       ({@link GHLongLongBTree#drainSortedAndClear}) - no intermediate sorted {@code long[]} and the
 *       B-tree is freed subtree-by-subtree as it is consumed, so peak memory stays at roughly
 *       max(B-tree, this map) rather than their sum.</li>
 * </ul>
 *
 * <p>Layout: logical node {@code k} (1-indexed) stores its key at byte {@code 16*k} and its value at
 * {@code 16*k + 8}. The immutable key set has {@code n} entries; keys inserted afterwards (OSM pass2's
 * artificial split nodes) go to a small dynamic {@code overflow} map. The cost of planet-safety is
 * per-access {@link DataAccess} indirection (two int reads per long, segment lookup) instead of a raw
 * array index - measurably slower per lookup than the {@code long[]} version.
 */
public class DataAccessEytzingerLongLongMap implements LongLongMap {
    private final long emptyValue;
    private final long n;
    private final DataAccess da;
    private final LongLongMap overflow;

    // running slot during the streaming build (in-order sequence of Eytzinger slots)
    private long buildSlot;

    /**
     * @param da       created and sized to at least {@code 16*(n+1)} bytes; filled by this map
     * @param n        number of entries that will be streamed in via {@link #acceptSorted}
     * @param overflow dynamic map for keys inserted after the build
     */
    public DataAccessEytzingerLongLongMap(DataAccess da, long n, long emptyValue, LongLongMap overflow) {
        this.da = da;
        this.n = n;
        this.emptyValue = emptyValue;
        this.overflow = overflow;
        this.buildSlot = firstSlot();
    }

    // --- build (fed by GHLongLongBTree.drainSortedAndClear, ascending key order) ---

    /** Places the next sorted entry at the next Eytzinger slot. Call exactly {@code n} times. */
    public void acceptSorted(long key, long value) {
        setLong(16 * buildSlot, key);
        setLong(16 * buildSlot + 8, value);
        buildSlot = nextSlot(buildSlot);
    }

    // --- Eytzinger in-order slot walk over the implicit tree [1..n] (children of k: 2k, 2k+1) ---

    private long firstSlot() {
        long k = 1;
        while (2 * k <= n) k = 2 * k;
        return k;
    }

    private long nextSlot(long k) {
        if (2 * k + 1 <= n) {           // has right child -> leftmost of right subtree
            k = 2 * k + 1;
            while (2 * k <= n) k = 2 * k;
            return k;
        }
        while (k > 1 && (k & 1L) == 1L) // ascend while k is a right child
            k >>= 1;
        return k >> 1;                  // parent we came from the left (0 when done)
    }

    // --- DataAccess long access (two ints, little-endian) ---

    private long getLong(long bytePos) {
        return (da.getInt(bytePos) & 0xFFFFFFFFL) | ((long) da.getInt(bytePos + 4) << 32);
    }

    private void setLong(long bytePos, long v) {
        da.setInt(bytePos, (int) v);
        da.setInt(bytePos + 4, (int) (v >>> 32));
    }

    /** @return the 1-based node index of key, or -1 if absent */
    private long indexOf(long key) {
        long k = 1;
        while (k <= n) {
            long v = getLong(16 * k);
            if (key == v)
                return k;
            k = 2 * k + (key > v ? 1 : 0);
        }
        return -1;
    }

    @Override
    public long get(long key) {
        long k = indexOf(key);
        return k >= 0 ? getLong(16 * k + 8) : overflow.get(key);
    }

    @Override
    public long put(long key, long value) {
        long k = indexOf(key);
        if (k >= 0) {
            long old = getLong(16 * k + 8);
            setLong(16 * k + 8, value);
            return old;
        }
        return overflow.put(key, value);
    }

    @Override
    public long putOrCompute(long key, long valueIfAbsent, LongUnaryOperator computeIfPresent) {
        long k = indexOf(key);
        if (k >= 0) {
            long old = getLong(16 * k + 8);
            setLong(16 * k + 8, computeIfPresent.applyAsLong(old));
            return old;
        }
        return overflow.putOrCompute(key, valueIfAbsent, computeIfPresent);
    }

    @Override
    public long getSize() {
        return n + overflow.getSize();
    }

    @Override
    public long getMaxValue() {
        return Long.MAX_VALUE;
    }

    @Override
    public void optimize() {
        overflow.optimize();
    }

    @Override
    public int getMemoryUsage() {
        return (int) (da.getCapacity() / (1024 * 1024)) + overflow.getMemoryUsage();
    }

    @Override
    public void clear() {
        da.close();
        overflow.clear();
    }
}
