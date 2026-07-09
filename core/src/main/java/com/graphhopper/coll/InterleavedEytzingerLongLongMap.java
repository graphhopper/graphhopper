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
 * Like {@link FrozenEytzingerLongLongMap} but keys and values are <b>interleaved</b> in a single
 * array in Eytzinger (BFS) order: {@code kv[2k]=key, kv[2k+1]=value}. Each key sits next to its own
 * value, so the matched value is already in the cache line loaded for the key - one fewer cache miss
 * per lookup than the two-parallel-array layout. Same 16 bytes/key footprint (one array of 2n longs
 * instead of two arrays of n). Microbench: ~20% faster get() than the two-array Eytzinger.
 *
 * <p>Immutable key set (values mutable in place); keys inserted after construction go to a small
 * {@code overflow} map. Built from a sorted snapshot, see {@link GHLongLongBTree#fillSorted}.
 * Int-indexed, capped at ~2^31 keys.
 */
public class InterleavedEytzingerLongLongMap implements LongLongMap {
    private final long emptyValue;
    private final int n;
    private final long[] kv;   // 1-indexed logical nodes: kv[2k]=key, kv[2k+1]=value
    private final LongLongMap overflow;

    private int buildPos;

    public InterleavedEytzingerLongLongMap(long[] sortedKeys, long[] sortedValues, long emptyValue, LongLongMap overflow) {
        this.emptyValue = emptyValue;
        this.n = sortedKeys.length;
        this.overflow = overflow;
        this.kv = new long[2 * (n + 1)];
        buildPos = 0;
        buildEytzinger(1, sortedKeys, sortedValues);
    }

    private void buildEytzinger(int k, long[] sortedKeys, long[] sortedValues) {
        if (k > n)
            return;
        buildEytzinger(2 * k, sortedKeys, sortedValues);
        kv[2 * k] = sortedKeys[buildPos];
        kv[2 * k + 1] = sortedValues[buildPos];
        buildPos++;
        buildEytzinger(2 * k + 1, sortedKeys, sortedValues);
    }

    /** @return the 1-based node index of key (value at {@code kv[2*idx+1]}), or -1 if absent */
    private int indexOf(long key) {
        int k = 1;
        while (k <= n) {
            long v = kv[2 * k];
            if (key == v)
                return k;
            k = 2 * k + (key > v ? 1 : 0);
        }
        return -1;
    }

    @Override
    public long get(long key) {
        int k = indexOf(key);
        return k >= 0 ? kv[2 * k + 1] : overflow.get(key);
    }

    @Override
    public long put(long key, long value) {
        int k = indexOf(key);
        if (k >= 0) {
            long old = kv[2 * k + 1];
            kv[2 * k + 1] = value;
            return old;
        }
        return overflow.put(key, value);
    }

    @Override
    public long putOrCompute(long key, long valueIfAbsent, LongUnaryOperator computeIfPresent) {
        int k = indexOf(key);
        if (k >= 0) {
            long old = kv[2 * k + 1];
            kv[2 * k + 1] = computeIfPresent.applyAsLong(old);
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
        return (int) (kv.length * 8L / (1024 * 1024)) + overflow.getMemoryUsage();
    }

    @Override
    public void clear() {
        overflow.clear();
    }
}
