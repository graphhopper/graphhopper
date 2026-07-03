/*
 * Kotlin port of com.carrotsearch.hppc.LongIntScatterMap from HPPC 0.8.1 (Apache License 2.0,
 * https://github.com/carrotsearch/hppc). See NOTICE.md.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.graphhopper.coll.primitive

/**
 * A scatter map of `long` to `int` — a 1:1 layout port of HPPC 0.8.1's `LongIntScatterMap`:
 * no per-instance order mixing, golden-ratio bit distribution (BitMixer.mixPhi), and therefore
 * bit-identical iteration order to hppc. Missing keys map to `0` (hppc semantics).
 * See [IntObjectHashMap] for the iteration-order contract.
 */
open class LongIntScatterMap @JvmOverloads constructor(
    expectedElements: Int = HashPort.DEFAULT_EXPECTED_ELEMENTS,
    loadFactor: Double = HashPort.DEFAULT_LOAD_FACTOR
) {
    /** The array holding keys. */
    @JvmField
    var keys: LongArray = LongObjectHashMap.EMPTY_LONG_ARRAY

    /** The array holding values. */
    @JvmField
    var values: IntArray = IntObjectHashMap.EMPTY_INT_ARRAY

    @JvmField
    protected var keyMixer = 0

    @JvmField
    protected var assigned = 0

    /** Mask for slot scans in [keys]. */
    @JvmField
    var mask = 0

    @JvmField
    protected var resizeAt = 0

    /** Special treatment for the "empty slot" key marker. */
    @JvmField
    var hasEmptyKey = false

    @JvmField
    protected val loadFactor: Double

    init {
        this.loadFactor = verifyLoadFactor(loadFactor)
        ensureCapacity(expectedElements)
    }

    fun put(key: Long, value: Int): Int {
        val mask = this.mask
        if (key == 0L) {
            hasEmptyKey = true
            val previousValue = values[mask + 1]
            values[mask + 1] = value
            return previousValue
        } else {
            val keys = this.keys
            var slot = hashKey(key) and mask

            var existing = keys[slot]
            while (existing != 0L) {
                if (existing == key) {
                    val previousValue = values[slot]
                    values[slot] = value
                    return previousValue
                }
                slot = (slot + 1) and mask
                existing = keys[slot]
            }

            if (assigned == resizeAt) {
                allocateThenInsertThenRehash(slot, key, value)
            } else {
                keys[slot] = key
                values[slot] = value
            }

            assigned++
            return 0
        }
    }

    /**
     * If `key` exists, `putValue` is inserted into the map, otherwise any existing value is
     * incremented by `incrementValue` (hppc Trove-inspired API).
     */
    fun putOrAdd(key: Long, putValue0: Int, incrementValue: Int): Int {
        var putValue = putValue0
        val keyIndex = indexOf(key)
        if (indexExists(keyIndex)) {
            putValue = values[keyIndex] + incrementValue
            indexReplace(keyIndex, putValue)
        } else {
            indexInsert(keyIndex, key, putValue)
        }
        return putValue
    }

    /**
     * Adds [incrementValue] to any existing value for the given [key] or inserts it if the key
     * did not previously exist.
     */
    fun addTo(key: Long, incrementValue: Int): Int = putOrAdd(key, incrementValue, incrementValue)

    fun remove(key: Long): Int {
        val mask = this.mask
        if (key == 0L) {
            hasEmptyKey = false
            val previousValue = values[mask + 1]
            values[mask + 1] = 0
            return previousValue
        } else {
            val keys = this.keys
            var slot = hashKey(key) and mask

            var existing = keys[slot]
            while (existing != 0L) {
                if (existing == key) {
                    val previousValue = values[slot]
                    shiftConflictingKeys(slot)
                    return previousValue
                }
                slot = (slot + 1) and mask
                existing = keys[slot]
            }

            return 0
        }
    }

    fun get(key: Long): Int {
        if (key == 0L) {
            return if (hasEmptyKey) values[mask + 1] else 0
        } else {
            val keys = this.keys
            val mask = this.mask
            var slot = hashKey(key) and mask

            var existing = keys[slot]
            while (existing != 0L) {
                if (existing == key) {
                    return values[slot]
                }
                slot = (slot + 1) and mask
                existing = keys[slot]
            }

            return 0
        }
    }

    fun getOrDefault(key: Long, defaultValue: Int): Int {
        if (key == 0L) {
            return if (hasEmptyKey) values[mask + 1] else defaultValue
        } else {
            val keys = this.keys
            val mask = this.mask
            var slot = hashKey(key) and mask

            var existing = keys[slot]
            while (existing != 0L) {
                if (existing == key) {
                    return values[slot]
                }
                slot = (slot + 1) and mask
                existing = keys[slot]
            }

            return defaultValue
        }
    }

    fun containsKey(key: Long): Boolean {
        if (key == 0L) {
            return hasEmptyKey
        } else {
            val keys = this.keys
            val mask = this.mask
            var slot = hashKey(key) and mask

            var existing = keys[slot]
            while (existing != 0L) {
                if (existing == key) {
                    return true
                }
                slot = (slot + 1) and mask
                existing = keys[slot]
            }

            return false
        }
    }

    fun indexOf(key: Long): Int {
        val mask = this.mask
        if (key == 0L) {
            return if (hasEmptyKey) mask + 1 else (mask + 1).inv()
        } else {
            val keys = this.keys
            var slot = hashKey(key) and mask

            var existing = keys[slot]
            while (existing != 0L) {
                if (existing == key) {
                    return slot
                }
                slot = (slot + 1) and mask
                existing = keys[slot]
            }

            return slot.inv()
        }
    }

    fun indexExists(index: Int): Boolean = index >= 0

    fun indexGet(index: Int): Int = values[index]

    fun indexReplace(index: Int, newValue: Int): Int {
        val previousValue = values[index]
        values[index] = newValue
        return previousValue
    }

    fun indexInsert(index0: Int, key: Long, value: Int) {
        val index = index0.inv()
        if (key == 0L) {
            values[index] = value
            hasEmptyKey = true
        } else {
            if (assigned == resizeAt) {
                allocateThenInsertThenRehash(index, key, value)
            } else {
                keys[index] = key
                values[index] = value
            }
            assigned++
        }
    }

    fun clear() {
        assigned = 0
        hasEmptyKey = false
        keys.fill(0L)
        // hppc parity: the values array is NOT cleared (stale slots are gated by keys[slot]==0)
    }

    fun release() {
        assigned = 0
        hasEmptyKey = false
        keys = LongObjectHashMap.EMPTY_LONG_ARRAY
        values = IntObjectHashMap.EMPTY_INT_ARRAY
        ensureCapacity(HashPort.DEFAULT_EXPECTED_ELEMENTS)
    }

    fun size(): Int = assigned + (if (hasEmptyKey) 1 else 0)

    fun isEmpty(): Boolean = size() == 0

    fun ensureCapacity(expectedElements: Int) {
        if (expectedElements > resizeAt || keys === LongObjectHashMap.EMPTY_LONG_ARRAY) {
            val prevKeys = this.keys
            val prevValues = this.values
            allocateBuffers(HashPort.minBufferSize(expectedElements, loadFactor))
            if (prevKeys !== LongObjectHashMap.EMPTY_LONG_ARRAY && !isEmpty()) {
                rehash(prevKeys, prevValues)
            }
        }
    }

    /** hppc forEach (procedure) order: the empty key (0) FIRST, then slots ascending. */
    inline fun forEach(action: (key: Long, value: Int) -> Unit) {
        val keys = this.keys
        val values = this.values

        if (hasEmptyKey) {
            action(0L, values[mask + 1])
        }

        var slot = 0
        val max = this.mask
        while (slot <= max) {
            if (keys[slot] != 0L) {
                action(keys[slot], values[slot])
            }
            slot++
        }
    }

    /** hppc forEach (predicate) order with early exit on false. */
    inline fun forEachWhile(predicate: (key: Long, value: Int) -> Boolean) {
        val keys = this.keys
        val values = this.values

        if (hasEmptyKey) {
            if (!predicate(0L, values[mask + 1])) {
                return
            }
        }

        var slot = 0
        val max = this.mask
        while (slot <= max) {
            if (keys[slot] != 0L) {
                if (!predicate(keys[slot], values[slot])) {
                    break
                }
            }
            slot++
        }
    }

    /** hppc cursor-iterator order: slots ascending, then the empty key (0) LAST. */
    inline fun forEachInIteratorOrder(action: (key: Long, value: Int) -> Unit) {
        val keys = this.keys
        val values = this.values
        val max = this.mask
        var slot = 0
        while (slot <= max) {
            val existing = keys[slot]
            if (existing != 0L) {
                action(existing, values[slot])
            }
            slot++
        }
        if (hasEmptyKey) {
            action(0L, values[max + 1])
        }
    }

    /** All keys, in hppc's `keys().toArray()` order (= iterator order, empty key last). */
    fun keysToArray(): LongArray {
        val result = LongArray(size())
        var i = 0
        forEachInIteratorOrder { key, _ -> result[i++] = key }
        return result
    }

    override fun hashCode(): Int {
        var h = if (hasEmptyKey) -0x21524111 else 0
        forEachInIteratorOrder { key, value ->
            h += HashPort.mix(key) + HashPort.mix(value)
        }
        return h
    }

    override fun equals(other: Any?): Boolean {
        return other != null &&
                javaClass == other.javaClass &&
                equalElements(other as LongIntScatterMap)
    }

    protected fun equalElements(other: LongIntScatterMap): Boolean {
        if (other.size() != size()) return false
        var equal = true
        other.forEachInIteratorOrder { key, value ->
            if (equal && !(containsKey(key) && get(key) == value)) equal = false
        }
        return equal
    }

    override fun toString(): String {
        val buffer = StringBuilder()
        buffer.append("[")
        var first = true
        forEachInIteratorOrder { key, value ->
            if (!first) buffer.append(", ")
            buffer.append(key)
            buffer.append("=>")
            buffer.append(value)
            first = false
        }
        buffer.append("]")
        return buffer.toString()
    }

    protected open fun hashKey(key: Long): Int = HashPort.mixPhi(key)

    protected fun verifyLoadFactor(loadFactor: Double): Double =
        HashPort.checkLoadFactor(loadFactor, HashPort.MIN_LOAD_FACTOR, HashPort.MAX_LOAD_FACTOR)

    protected fun rehash(fromKeys: LongArray, fromValues: IntArray) {
        val keys = this.keys
        val values = this.values
        val mask = this.mask

        var from = fromKeys.size - 1
        keys[keys.size - 1] = fromKeys[from]
        values[values.size - 1] = fromValues[from]
        while (--from >= 0) {
            val existing = fromKeys[from]
            if (existing != 0L) {
                var slot = hashKey(existing) and mask
                while (keys[slot] != 0L) {
                    slot = (slot + 1) and mask
                }
                keys[slot] = existing
                values[slot] = fromValues[from]
            }
        }
    }

    protected fun allocateBuffers(arraySize: Int) {
        // scatter map: HashOrderMixing.none() -> keyMixer stays 0; hashKey uses mixPhi
        val emptyElementSlot = 1
        this.keys = LongArray(arraySize + emptyElementSlot)
        this.values = IntArray(arraySize + emptyElementSlot)

        this.resizeAt = HashPort.expandAtCount(arraySize, loadFactor)
        this.mask = arraySize - 1
    }

    protected fun allocateThenInsertThenRehash(slot: Int, pendingKey: Long, pendingValue: Int) {
        val prevKeys = this.keys
        val prevValues = this.values
        allocateBuffers(HashPort.nextBufferSize(mask + 1, size(), loadFactor))

        prevKeys[slot] = pendingKey
        prevValues[slot] = pendingValue

        rehash(prevKeys, prevValues)
    }

    protected fun shiftConflictingKeys(gapSlot0: Int) {
        var gapSlot = gapSlot0
        val keys = this.keys
        val values = this.values
        val mask = this.mask

        var distance = 0
        while (true) {
            val slot = (gapSlot + (++distance)) and mask
            val existing = keys[slot]
            if (existing == 0L) {
                break
            }

            val idealSlot = hashKey(existing)
            val shift = (slot - idealSlot) and mask
            if (shift >= distance) {
                keys[gapSlot] = existing
                values[gapSlot] = values[slot]
                gapSlot = slot
                distance = 0
            }
        }

        keys[gapSlot] = 0L
        values[gapSlot] = 0
        assigned--
    }
}
