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

import java.util.concurrent.locks.ReentrantLock;
import java.util.function.LongUnaryOperator;

/**
 * A concurrent wrapper around multiple {@link GHLongLongBTree} instances using lock striping.
 * Keys are distributed across stripes using a hash function, allowing parallel access to
 * different stripes.
 * <p>
 * This is useful for parallelizing operations like OSM import where many threads need to
 * update a shared map concurrently.
 */
public class StripedLongLongBTree implements LongLongMap {
    private final int numStripes;
    private final int stripeMask;
    private final GHLongLongBTree[] stripes;
    private final ReentrantLock[] locks;
    private final long emptyValue;

    /**
     * @param numStripes      number of stripes (will be rounded up to next power of 2)
     * @param maxLeafEntries  max entries per B-tree leaf node
     * @param bytesPerValue   bytes per value (1-8)
     * @param emptyValue      the value returned for missing keys
     */
    public StripedLongLongBTree(int numStripes, int maxLeafEntries, int bytesPerValue, long emptyValue) {
        if (numStripes < 1) {
            throw new IllegalArgumentException("numStripes must be at least 1");
        }
        // Round up to power of 2 for fast modulo via bitwise AND
        this.numStripes = numStripes == 1 ? 1 : Integer.highestOneBit(numStripes - 1) << 1;
        this.stripeMask = this.numStripes - 1;
        this.emptyValue = emptyValue;

        this.stripes = new GHLongLongBTree[this.numStripes];
        this.locks = new ReentrantLock[this.numStripes];
        for (int i = 0; i < this.numStripes; i++) {
            stripes[i] = new GHLongLongBTree(maxLeafEntries, bytesPerValue, emptyValue);
            locks[i] = new ReentrantLock();
        }
    }

    /**
     * Compute the stripe index for a given key.
     * Uses multiplicative hashing to spread sequential keys across stripes.
     */
    private int stripeFor(long key) {
        // Multiplicative hash using golden ratio constant
        // This spreads sequential OSM IDs well across stripes
        long hash = key * 0x9E3779B97F4A7C15L;
        return (int) (hash >>> (64 - Integer.bitCount(stripeMask))) & stripeMask;
    }

    @Override
    public long put(long key, long value) {
        int stripe = stripeFor(key);
        locks[stripe].lock();
        try {
            return stripes[stripe].put(key, value);
        } finally {
            locks[stripe].unlock();
        }
    }

    @Override
    public long putOrCompute(long key, long valueIfAbsent, LongUnaryOperator computeIfPresent) {
        int stripe = stripeFor(key);
        locks[stripe].lock();
        try {
            return stripes[stripe].putOrCompute(key, valueIfAbsent, computeIfPresent);
        } finally {
            locks[stripe].unlock();
        }
    }

    @Override
    public long get(long key) {
        int stripe = stripeFor(key);
        locks[stripe].lock();
        try {
            return stripes[stripe].get(key);
        } finally {
            locks[stripe].unlock();
        }
    }

    @Override
    public long getSize() {
        long size = 0;
        for (int i = 0; i < numStripes; i++) {
            locks[i].lock();
            try {
                size += stripes[i].getSize();
            } finally {
                locks[i].unlock();
            }
        }
        return size;
    }

    @Override
    public long getMaxValue() {
        // All stripes have the same maxValue
        return stripes[0].getMaxValue();
    }

    @Override
    public void optimize() {
        for (int i = 0; i < numStripes; i++) {
            locks[i].lock();
            try {
                stripes[i].optimize();
            } finally {
                locks[i].unlock();
            }
        }
    }

    @Override
    public int getMemoryUsage() {
        int usage = 0;
        for (int i = 0; i < numStripes; i++) {
            locks[i].lock();
            try {
                usage += stripes[i].getMemoryUsage();
            } finally {
                locks[i].unlock();
            }
        }
        return usage;
    }

    @Override
    public void clear() {
        for (int i = 0; i < numStripes; i++) {
            locks[i].lock();
            try {
                stripes[i].clear();
            } finally {
                locks[i].unlock();
            }
        }
    }

    /**
     * @return the number of stripes (always a power of 2)
     */
    public int getNumStripes() {
        return numStripes;
    }

    /**
     * @return the empty value used for missing keys
     */
    public long getEmptyValue() {
        return emptyValue;
    }
}
