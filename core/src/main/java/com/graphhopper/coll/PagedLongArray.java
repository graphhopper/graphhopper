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

import java.util.Arrays;

/**
 * A grow-on-append, long-indexed array of longs backed by fixed-size pages ({@code long[][]}).
 * Not limited to {@code 2^31} elements, so it scales to a full-planet import, while staying in the
 * Java heap (no per-element indirection beyond a page lookup). Used as the pass1 append buffer for the
 * OSM node map and, temporarily, as the radix-sort scratch buffer.
 */
public class PagedLongArray {
    // 2^22 longs = 32 MB per page; a multiple of the blocked-B-tree block size so callers can align
    static final int PAGE_BITS = 22;
    static final int PAGE_SIZE = 1 << PAGE_BITS;
    static final int PAGE_MASK = PAGE_SIZE - 1;

    private long[][] pages;
    private int pageCount;
    private long size;

    public PagedLongArray() {
        pages = new long[16][];
        pageCount = 0;
        size = 0;
    }

    /** Pre-allocates a buffer of exactly {@code n} elements (used as sort scratch). */
    public static PagedLongArray allocate(long n) {
        PagedLongArray a = new PagedLongArray();
        int np = (int) ((n + PAGE_SIZE - 1) >>> PAGE_BITS);
        a.pages = new long[Math.max(1, np)][];
        for (int i = 0; i < np; i++) a.pages[i] = new long[PAGE_SIZE];
        a.pageCount = np;
        a.size = n;
        return a;
    }

    public void add(long v) {
        int p = (int) (size >>> PAGE_BITS);
        if (p >= pageCount) {
            if (pageCount >= pages.length)
                pages = Arrays.copyOf(pages, pages.length * 2);
            pages[pageCount++] = new long[PAGE_SIZE];
        }
        pages[p][(int) (size & PAGE_MASK)] = v;
        size++;
    }

    public long get(long i) {
        return pages[(int) (i >>> PAGE_BITS)][(int) (i & PAGE_MASK)];
    }

    public void set(long i, long v) {
        pages[(int) (i >>> PAGE_BITS)][(int) (i & PAGE_MASK)] = v;
    }

    public long size() {
        return size;
    }

    public void release() {
        pages = null;
        pageCount = 0;
        size = 0;
    }

    /**
     * Stable LSD radix sort (unsigned, 8 bits per pass) of the first {@code m} elements of
     * {@code src}. Uses a second buffer of the same size as scratch, which is released before
     * returning, so the transient cost is {@code 2*m} longs. Returns the buffer that holds the sorted
     * data (either {@code src} or the scratch, depending on the number of passes) - the caller must use
     * the returned reference and drop the old one.
     */
    public static PagedLongArray radixSort(PagedLongArray src, long m) {
        if (m <= 1)
            return src;
        long orAll = 0;
        for (long i = 0; i < m; i++) orAll |= src.get(i);
        if (orAll == 0)
            return src; // all equal (or empty) -> already sorted

        PagedLongArray dst = allocate(m);
        long[] count = new long[257]; // 256 buckets, long counts (m can exceed 2^31 on a planet)
        for (int shift = 0; shift < 64 && (orAll >>> shift) != 0; shift += 8) {
            Arrays.fill(count, 0);
            for (long i = 0; i < m; i++)
                count[(int) ((src.get(i) >>> shift) & 0xFF) + 1]++;
            for (int b = 1; b < 257; b++)
                count[b] += count[b - 1];
            for (long i = 0; i < m; i++) {
                long v = src.get(i);
                int b = (int) ((v >>> shift) & 0xFF);
                dst.set(count[b]++, v);
            }
            PagedLongArray t = src;
            src = dst;
            dst = t;
        }
        dst.release();
        return src;
    }
}
