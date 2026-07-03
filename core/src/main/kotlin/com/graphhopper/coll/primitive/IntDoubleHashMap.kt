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
package com.graphhopper.coll.primitive

import androidx.collection.MutableIntLongMap

/**
 * An int→double hash map: a thin shim over androidx.collection's [MutableIntLongMap] storing the
 * doubles' raw bit patterns (androidx.collection has no double-valued primitive maps). Replaces
 * hppc's IntDoubleHashMap/IntDoubleScatterMap at keyed-access-only call sites.
 *
 * NaN handling: values round-trip through [Double.toRawBits]/[Double.fromBits], so the exact bit
 * pattern of any NaN payload is preserved on get — same behavior as storing into a `double[]`
 * (hppc). Note that keyed lookups are unaffected by value bit patterns; only [containsValue]-style
 * queries would see NaN != NaN, and this shim deliberately offers none.
 *
 * Iteration order is androidx.collection's (deterministic per version, guarded by
 * AndroidxCollectionDeterminismTest) — only use where iteration order is provably unobserved.
 */
class IntDoubleHashMap @JvmOverloads constructor(expectedElements: Int = 6) {

    @PublishedApi
    internal val map = MutableIntLongMap(expectedElements)

    /** Associates [value] with [key], replacing any previous value. */
    fun put(key: Int, value: Double) {
        map.put(key, value.toRawBits())
    }

    /** Returns the value for [key], or `0.0` if the key is not present (hppc semantics). */
    fun get(key: Int): Double = Double.fromBits(map.getOrDefault(key, 0L))

    /** Returns the value for [key], or [defaultValue] if the key is not present. */
    fun getOrDefault(key: Int, defaultValue: Double): Double =
        Double.fromBits(map.getOrElse(key) { defaultValue.toRawBits() })

    /**
     * Adds [incrementValue] to the existing value of [key], or puts it as-is if the key is
     * absent (exact hppc `addTo` semantics, relevant for signed zero/NaN).
     */
    fun addTo(key: Int, incrementValue: Double): Double {
        val newValue = if (map.containsKey(key))
            Double.fromBits(map.getOrDefault(key, 0L)) + incrementValue
        else
            incrementValue
        map.put(key, newValue.toRawBits())
        return newValue
    }

    fun containsKey(key: Int): Boolean = map.containsKey(key)

    /** Removes the mapping for [key] if present. */
    fun remove(key: Int) {
        map.remove(key)
    }

    fun size(): Int = map.size

    fun isEmpty(): Boolean = map.isEmpty()

    fun clear() {
        map.clear()
    }

    /** Applies [action] to each key/value pair, in androidx.collection's iteration order. */
    inline fun forEach(action: (key: Int, value: Double) -> Unit) {
        map.forEach { key, value -> action(key, Double.fromBits(value)) }
    }

    override fun equals(other: Any?): Boolean =
        other is IntDoubleHashMap && map == other.map

    override fun hashCode(): Int = map.hashCode()

    override fun toString(): String {
        val sb = StringBuilder("[")
        var first = true
        map.forEach { key, value ->
            if (!first) sb.append(", ")
            sb.append(key).append("=>").append(Double.fromBits(value))
            first = false
        }
        return sb.append("]").toString()
    }
}
