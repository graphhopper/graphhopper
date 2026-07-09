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
package com.graphhopper.coll

import java.util.function.LongUnaryOperator

/**
 * Kotlin port of the interleaved-Eytzinger node map: keys and values are interleaved in a single
 * [LongArray] in Eytzinger (BFS) order ([kv]`[2k]=key, `[kv]`[2k+1]=value`), so the matched value is
 * already in the cache line loaded for the key. Same 16 bytes/key footprint. Immutable key set
 * (values mutable in place); keys inserted after construction go to a small [overflow] map. Built
 * from a sorted snapshot (see `GHLongLongBTree.fillSorted`). Int-indexed, capped at ~2^31 keys.
 */
class InterleavedEytzingerLongLongMap(
    sortedKeys: LongArray,
    sortedValues: LongArray,
    private val emptyValue: Long,
    private val overflow: LongLongMap
) : LongLongMap {
    private val n: Int = sortedKeys.size
    private val kv: LongArray = LongArray(2 * (n + 1))   // 1-indexed nodes: kv[2k]=key, kv[2k+1]=value
    private var buildPos = 0

    init {
        buildEytzinger(1, sortedKeys, sortedValues)
    }

    private fun buildEytzinger(k: Int, sortedKeys: LongArray, sortedValues: LongArray) {
        if (k > n) return
        buildEytzinger(2 * k, sortedKeys, sortedValues)
        kv[2 * k] = sortedKeys[buildPos]
        kv[2 * k + 1] = sortedValues[buildPos]
        buildPos++
        buildEytzinger(2 * k + 1, sortedKeys, sortedValues)
    }

    /** @return the 1-based node index of key (value at kv[2*idx+1]), or -1 if absent */
    private fun indexOf(key: Long): Int {
        var k = 1
        while (k <= n) {
            val v = kv[2 * k]
            if (key == v) return k
            k = 2 * k + (if (key > v) 1 else 0)
        }
        return -1
    }

    override fun get(key: Long): Long {
        val k = indexOf(key)
        return if (k >= 0) kv[2 * k + 1] else overflow.get(key)
    }

    override fun put(key: Long, value: Long): Long {
        val k = indexOf(key)
        if (k >= 0) {
            val old = kv[2 * k + 1]
            kv[2 * k + 1] = value
            return old
        }
        return overflow.put(key, value)
    }

    override fun putOrCompute(key: Long, valueIfAbsent: Long, computeIfPresent: LongUnaryOperator): Long {
        val k = indexOf(key)
        if (k >= 0) {
            val old = kv[2 * k + 1]
            kv[2 * k + 1] = computeIfPresent.applyAsLong(old)
            return old
        }
        return overflow.putOrCompute(key, valueIfAbsent, computeIfPresent)
    }

    override val size: Long get() = n.toLong() + overflow.size

    override val maxValue: Long get() = Long.MAX_VALUE

    override fun optimize() = overflow.optimize()

    override val memoryUsage: Int get() = (kv.size * 8L / (1024 * 1024)).toInt() + overflow.memoryUsage

    override fun clear() = overflow.clear()
}
