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

import java.util.function.LongUnaryOperator;

/**
 * A read-optimized long-&gt;long map with an immutable key set stored in
 * <a href="https://algorithmica.org/en/eytzinger">Eytzinger (BFS) layout</a>: the search tree is
 * flattened into a single array so the hot upper levels (indices 1, 2, 3, …) sit at the front and
 * stay cache-resident, while the descent is pointer-free index arithmetic. This makes lookups ~2x
 * faster than {@link GHLongLongBTree} <em>even under the memory pressure of OSM import</em> (measured),
 * where a flat hash map is no faster than the B-tree because its single big array is always cold.
 *
 * <p>Values are mutable (update-in-place by index); the key set is not. Keys inserted after
 * construction go to a small dynamic {@code overflow} map (during OSM import: the handful of
 * artificial split nodes created in pass2). Built once from a sorted key/value snapshot - see
 * {@link GHLongLongBTree#fillSorted}.
 *
 * <p>Like any int-indexed array structure it is capped at ~2^31 keys; {@link GHLongLongBTree} remains
 * the choice beyond that (planet-scale).
 */
public class FrozenEytzingerLongLongMap implements LongLongMap {
    private final long emptyValue;
    private final int n;
    private final long[] ek;   // keys in Eytzinger order, 1-indexed (index 0 unused)
    private final long[] ev;   // values, parallel to ek
    private final LongLongMap overflow;

    private int buildPos;

    /**
     * @param sortedKeys   keys in ascending order (length == number of entries)
     * @param sortedValues values parallel to sortedKeys
     * @param emptyValue   returned for absent keys (must match {@code overflow}'s empty value)
     * @param overflow     dynamic map for keys inserted after construction
     */
    public FrozenEytzingerLongLongMap(long[] sortedKeys, long[] sortedValues, long emptyValue, LongLongMap overflow) {
        this.emptyValue = emptyValue;
        this.n = sortedKeys.length;
        this.overflow = overflow;
        this.ek = new long[n + 1];
        this.ev = new long[n + 1];
        buildPos = 0;
        buildEytzinger(1, sortedKeys, sortedValues);
    }

    /** Fills ek/ev in Eytzinger order by consuming the sorted input in-order. */
    private void buildEytzinger(int k, long[] sortedKeys, long[] sortedValues) {
        if (k > n)
            return;
        buildEytzinger(2 * k, sortedKeys, sortedValues);
        ek[k] = sortedKeys[buildPos];
        ev[k] = sortedValues[buildPos];
        buildPos++;
        buildEytzinger(2 * k + 1, sortedKeys, sortedValues);
    }

    /** @return the 1-based Eytzinger index of key, or -1 if absent */
    private int indexOf(long key) {
        int k = 1;
        while (k <= n) {
            long v = ek[k];
            if (key == v)
                return k;
            // go right if key > v, else left - branchless child selection
            k = 2 * k + (key > v ? 1 : 0);
        }
        return -1;
    }

    @Override
    public long get(long key) {
        int k = indexOf(key);
        return k >= 0 ? ev[k] : overflow.get(key);
    }

    @Override
    public long put(long key, long value) {
        int k = indexOf(key);
        if (k >= 0) {
            long old = ev[k];
            ev[k] = value;
            return old;
        }
        return overflow.put(key, value);
    }

    @Override
    public long putOrCompute(long key, long valueIfAbsent, LongUnaryOperator computeIfPresent) {
        int k = indexOf(key);
        if (k >= 0) {
            long old = ev[k];
            ev[k] = computeIfPresent.applyAsLong(old);
            return old;
        }
        return overflow.putOrCompute(key, valueIfAbsent, computeIfPresent);
    }

    @Override
    public long getSize() {
        return (long) n + overflow.getSize();
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
        return (int) ((ek.length * 8L + ev.length * 8L) / (1024 * 1024)) + overflow.getMemoryUsage();
    }

    @Override
    public void clear() {
        overflow.clear();
    }
}
