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
 * Static (implicit) B-tree long-&gt;long map - the "B-tree layout" from Khuong &amp; Morin,
 * "Array Layouts for Comparison-Based Searching" (arXiv:1509.05053). Keys are packed into
 * cache-line-sized blocks of {@link #B} keys ({@code B*8 = 64} bytes = one cache line), and the
 * blocks form an implicit {@code (B+1)}-ary search tree in BFS order: block {@code k}'s children are
 * blocks {@code (B+1)*k+1 .. (B+1)*k+(B+1)}. A lookup loads exactly one cache line per level
 * ({@code log_{B+1}(n)} levels) and scans the {@code B} keys within it in L1 - far fewer cold cache
 * misses per lookup than a binary layout (which touches one line per binary level). On a cold,
 * planet-sized array that miss count is the dominant cost, so it is markedly faster than a B-tree.
 *
 * <p>Values live in a parallel array {@link #bv} with the same block layout, fetched once after the
 * key is located. Immutable key set (values mutable in place); keys inserted afterwards go to a small
 * {@code overflow} map. Built from sorted (key, value) arrays. All frozen
 * keys are real (positive) OSM node ids, so {@link Long#MAX_VALUE} is used as the empty-slot sentinel.
 * Int-indexed (keys array ~n longs), capped at ~2^31 keys.
 */
public class BlockedBTreeLongLongMap implements LongLongMap {
    static final int B = 8;                 // keys per block = one 64-byte cache line
    private static final long INF = Long.MAX_VALUE;

    private final long emptyValue;
    private final int n;
    private final int nblocks;
    private final long[] bt;                // keys, block k at bt[k*B .. k*B+B-1]
    private final long[] bv;                // values, same layout
    private final LongLongMap overflow;

    private int buildT;                     // running sorted index during build

    public BlockedBTreeLongLongMap(long[] sortedKeys, long[] sortedValues, long emptyValue, LongLongMap overflow) {
        this.emptyValue = emptyValue;
        this.n = sortedKeys.length;
        this.overflow = overflow;
        this.nblocks = (n + B - 1) / B;
        this.bt = new long[nblocks * B];
        this.bv = new long[nblocks * B];
        buildT = 0;
        build(0, sortedKeys, sortedValues);
    }

    // in-order fill: an in-order walk of the implicit tree yields ascending key order
    private void build(int k, long[] sortedKeys, long[] sortedValues) {
        if (k >= nblocks)
            return;
        int base = k * B;
        for (int i = 0; i < B; i++) {
            build(child(k, i), sortedKeys, sortedValues);
            if (buildT < n) {
                bt[base + i] = sortedKeys[buildT];
                bv[base + i] = sortedValues[buildT];
                buildT++;
            } else {
                bt[base + i] = INF;
            }
        }
        build(child(k, B), sortedKeys, sortedValues);
    }

    private static int child(int k, int i) {
        return k * (B + 1) + i + 1;
    }

    /** @return the flat index of key in {@link #bt}/{@link #bv}, or -1 if absent */
    private int indexOf(long key) {
        int cand = -1;
        int k = 0;
        while (k < nblocks) {
            int base = k * B;
            int i = 0;
            while (i < B && bt[base + i] < key) i++;
            if (i < B) cand = base + i;      // smallest key >= key seen so far (lower_bound candidate)
            k = k * (B + 1) + i + 1;
        }
        return (cand >= 0 && bt[cand] == key) ? cand : -1;
    }

    @Override
    public long get(long key) {
        int idx = indexOf(key);
        return idx >= 0 ? bv[idx] : overflow.get(key);
    }

    @Override
    public long put(long key, long value) {
        int idx = indexOf(key);
        if (idx >= 0) {
            long old = bv[idx];
            bv[idx] = value;
            return old;
        }
        return overflow.put(key, value);
    }

    @Override
    public long putOrCompute(long key, long valueIfAbsent, LongUnaryOperator computeIfPresent) {
        int idx = indexOf(key);
        if (idx >= 0) {
            long old = bv[idx];
            bv[idx] = computeIfPresent.applyAsLong(old);
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
        return (int) ((bt.length + bv.length) * 8L / (1024 * 1024)) + overflow.getMemoryUsage();
    }

    @Override
    public void clear() {
        overflow.clear();
    }
}
