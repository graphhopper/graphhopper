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
 * misses per lookup than a binary layout, so it is markedly faster than a B-tree on a cold array.
 *
 * <p>Keys and values live in paged {@code long[][]} arrays (long-indexed), so the map is planet-scale
 * like {@link GHLongLongBTree} and not limited to {@code 2^31} entries. It is built by streaming a
 * sorted {@link SortedSource} - the caller never has to materialise sorted key/value arrays, which
 * keeps peak memory low. Immutable key set (values mutable in place); keys inserted afterwards go to a
 * small {@code overflow} map. All frozen keys are real (positive) OSM node ids, so
 * {@link Long#MAX_VALUE} is the empty-slot sentinel (compares greater than any real key).
 */
public class BlockedBTreeLongLongMap implements LongLongMap {
    static final int B = 8;                 // keys per block = one 64-byte cache line
    private static final long INF = Long.MAX_VALUE;
    // page size must be a multiple of B so a block never straddles a page boundary
    private static final int PAGE_BITS = 22;
    private static final int PAGE_SIZE = 1 << PAGE_BITS;
    private static final int PAGE_MASK = PAGE_SIZE - 1;

    /** Sorted (ascending key) stream of (key, value) pairs used to build the map. */
    public interface SortedSource {
        /** advance to the next pair; false when exhausted */
        boolean next();

        long key();

        long value();
    }

    private final long emptyValue;
    private final long n;
    private final long nblocks;
    private final long[][] bt;              // keys, block k at flat index [k*B .. k*B+B-1]
    private final long[][] bv;              // values, same layout
    private final LongLongMap overflow;

    public BlockedBTreeLongLongMap(long n, SortedSource src, long emptyValue, LongLongMap overflow) {
        this.emptyValue = emptyValue;
        this.n = n;
        this.overflow = overflow;
        this.nblocks = (n + B - 1) / B;
        long slots = nblocks * B;
        int np = (int) ((slots + PAGE_SIZE - 1) >>> PAGE_BITS);
        bt = new long[Math.max(1, np)][];
        bv = new long[Math.max(1, np)][];
        for (int i = 0; i < np; i++) {
            bt[i] = new long[PAGE_SIZE];
            bv[i] = new long[PAGE_SIZE];
        }
        build(0, src);
    }

    // in-order fill: an in-order walk of the implicit tree consumes src in ascending key order
    private void build(long k, SortedSource src) {
        if (k >= nblocks)
            return;
        long base = k * B;
        for (int i = 0; i < B; i++) {
            build(k * (B + 1) + i + 1, src);
            if (src.next()) {
                setBt(base + i, src.key());
                setBv(base + i, src.value());
            } else {
                setBt(base + i, INF);
            }
        }
        build(k * (B + 1) + B + 1, src);
    }

    private long getBt(long i) {
        return bt[(int) (i >>> PAGE_BITS)][(int) (i & PAGE_MASK)];
    }

    private void setBt(long i, long v) {
        bt[(int) (i >>> PAGE_BITS)][(int) (i & PAGE_MASK)] = v;
    }

    private long getBv(long i) {
        return bv[(int) (i >>> PAGE_BITS)][(int) (i & PAGE_MASK)];
    }

    private void setBv(long i, long v) {
        bv[(int) (i >>> PAGE_BITS)][(int) (i & PAGE_MASK)] = v;
    }

    /** @return the flat index of key, or -1 if absent */
    private long indexOf(long key) {
        long cand = -1;
        long k = 0;
        while (k < nblocks) {
            long base = k * B;
            // a block is page-aligned (PAGE_SIZE is a multiple of B), so one page deref covers it
            long[] page = bt[(int) (base >>> PAGE_BITS)];
            int off = (int) (base & PAGE_MASK);
            int i = 0;
            while (i < B && page[off + i] < key) i++;
            if (i < B) cand = base + i;      // smallest key >= key seen so far (lower_bound candidate)
            k = k * (B + 1) + i + 1;
        }
        return (cand >= 0 && getBt(cand) == key) ? cand : -1;
    }

    @Override
    public long get(long key) {
        long idx = indexOf(key);
        return idx >= 0 ? getBv(idx) : overflow.get(key);
    }

    @Override
    public long put(long key, long value) {
        long idx = indexOf(key);
        if (idx >= 0) {
            long old = getBv(idx);
            setBv(idx, value);
            return old;
        }
        return overflow.put(key, value);
    }

    @Override
    public long putOrCompute(long key, long valueIfAbsent, LongUnaryOperator computeIfPresent) {
        long idx = indexOf(key);
        if (idx >= 0) {
            long old = getBv(idx);
            setBv(idx, computeIfPresent.applyAsLong(old));
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
        return (int) (nblocks * B * 2 * 8L / (1024 * 1024)) + overflow.getMemoryUsage();
    }

    @Override
    public void clear() {
        overflow.clear();
    }
}
