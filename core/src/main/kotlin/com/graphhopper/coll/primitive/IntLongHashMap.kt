/*
 * Kotlin port of com.carrotsearch.hppc.IntLongHashMap from HPPC 0.8.1 (Apache License 2.0,
 * https://github.com/carrotsearch/hppc), with HashOrderMixing.constant(seed) inlined as the
 * `seed` constructor parameter. See NOTICE.md.
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
 * A hash map of `int` to `long` — a 1:1 layout port of HPPC 0.8.1's `IntLongHashMap` with
 * bit-identical iteration order (given the same seed). Missing keys map to `0L` (hppc
 * semantics). See [IntObjectHashMap] for the iteration-order contract.
 */
open class IntLongHashMap @JvmOverloads constructor(
    expectedElements: Int = HashPort.DEFAULT_EXPECTED_ELEMENTS,
    loadFactor: Double = HashPort.DEFAULT_LOAD_FACTOR,
    seed: Long = HashPort.DETERMINISTIC_SEED
) {
    /** The array holding keys. */
    @JvmField
    var keys: IntArray = IntObjectHashMap.EMPTY_INT_ARRAY

    /** The array holding values. */
    @JvmField
    var values: LongArray = LongObjectHashMap.EMPTY_LONG_ARRAY

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

    @JvmField
    protected val seed: Long

    init {
        this.seed = seed
        this.loadFactor = verifyLoadFactor(loadFactor)
        ensureCapacity(expectedElements)
    }

    fun put(key: Int, value: Long): Long {
        val mask = this.mask
        if (key == 0) {
            hasEmptyKey = true
            val previousValue = values[mask + 1]
            values[mask + 1] = value
            return previousValue
        } else {
            val keys = this.keys
            var slot = hashKey(key) and mask

            var existing = keys[slot]
            while (existing != 0) {
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
            return 0L
        }
    }

    /**
     * If `key` exists, `putValue` is inserted into the map, otherwise any existing value is
     * incremented by `incrementValue` (hppc Trove-inspired API).
     */
    fun putOrAdd(key: Int, putValue0: Long, incrementValue: Long): Long {
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
    fun addTo(key: Int, incrementValue: Long): Long = putOrAdd(key, incrementValue, incrementValue)

    fun remove(key: Int): Long {
        val mask = this.mask
        if (key == 0) {
            hasEmptyKey = false
            val previousValue = values[mask + 1]
            values[mask + 1] = 0L
            return previousValue
        } else {
            val keys = this.keys
            var slot = hashKey(key) and mask

            var existing = keys[slot]
            while (existing != 0) {
                if (existing == key) {
                    val previousValue = values[slot]
                    shiftConflictingKeys(slot)
                    return previousValue
                }
                slot = (slot + 1) and mask
                existing = keys[slot]
            }

            return 0L
        }
    }

    fun get(key: Int): Long {
        if (key == 0) {
            return if (hasEmptyKey) values[mask + 1] else 0L
        } else {
            val keys = this.keys
            val mask = this.mask
            var slot = hashKey(key) and mask

            var existing = keys[slot]
            while (existing != 0) {
                if (existing == key) {
                    return values[slot]
                }
                slot = (slot + 1) and mask
                existing = keys[slot]
            }

            return 0L
        }
    }

    fun getOrDefault(key: Int, defaultValue: Long): Long {
        if (key == 0) {
            return if (hasEmptyKey) values[mask + 1] else defaultValue
        } else {
            val keys = this.keys
            val mask = this.mask
            var slot = hashKey(key) and mask

            var existing = keys[slot]
            while (existing != 0) {
                if (existing == key) {
                    return values[slot]
                }
                slot = (slot + 1) and mask
                existing = keys[slot]
            }

            return defaultValue
        }
    }

    fun containsKey(key: Int): Boolean {
        if (key == 0) {
            return hasEmptyKey
        } else {
            val keys = this.keys
            val mask = this.mask
            var slot = hashKey(key) and mask

            var existing = keys[slot]
            while (existing != 0) {
                if (existing == key) {
                    return true
                }
                slot = (slot + 1) and mask
                existing = keys[slot]
            }

            return false
        }
    }

    fun indexOf(key: Int): Int {
        val mask = this.mask
        if (key == 0) {
            return if (hasEmptyKey) mask + 1 else (mask + 1).inv()
        } else {
            val keys = this.keys
            var slot = hashKey(key) and mask

            var existing = keys[slot]
            while (existing != 0) {
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

    fun indexGet(index: Int): Long = values[index]

    fun indexReplace(index: Int, newValue: Long): Long {
        val previousValue = values[index]
        values[index] = newValue
        return previousValue
    }

    fun indexInsert(index0: Int, key: Int, value: Long) {
        val index = index0.inv()
        if (key == 0) {
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
        keys.fill(0)
        // hppc parity: the values array is NOT cleared (stale slots are gated by keys[slot]==0)
    }

    fun release() {
        assigned = 0
        hasEmptyKey = false
        keys = IntObjectHashMap.EMPTY_INT_ARRAY
        values = LongObjectHashMap.EMPTY_LONG_ARRAY
        ensureCapacity(HashPort.DEFAULT_EXPECTED_ELEMENTS)
    }

    fun size(): Int = assigned + (if (hasEmptyKey) 1 else 0)

    fun isEmpty(): Boolean = size() == 0

    fun ensureCapacity(expectedElements: Int) {
        if (expectedElements > resizeAt || keys === IntObjectHashMap.EMPTY_INT_ARRAY) {
            val prevKeys = this.keys
            val prevValues = this.values
            allocateBuffers(HashPort.minBufferSize(expectedElements, loadFactor))
            if (prevKeys !== IntObjectHashMap.EMPTY_INT_ARRAY && !isEmpty()) {
                rehash(prevKeys, prevValues)
            }
        }
    }

    /** hppc forEach (procedure) order: the empty key (0) FIRST, then slots ascending. */
    inline fun forEach(action: (key: Int, value: Long) -> Unit) {
        val keys = this.keys
        val values = this.values

        if (hasEmptyKey) {
            action(0, values[mask + 1])
        }

        var slot = 0
        val max = this.mask
        while (slot <= max) {
            if (keys[slot] != 0) {
                action(keys[slot], values[slot])
            }
            slot++
        }
    }

    /** hppc forEach (predicate) order with early exit on false. */
    inline fun forEachWhile(predicate: (key: Int, value: Long) -> Boolean) {
        val keys = this.keys
        val values = this.values

        if (hasEmptyKey) {
            if (!predicate(0, values[mask + 1])) {
                return
            }
        }

        var slot = 0
        val max = this.mask
        while (slot <= max) {
            if (keys[slot] != 0) {
                if (!predicate(keys[slot], values[slot])) {
                    break
                }
            }
            slot++
        }
    }

    /** hppc cursor-iterator order: slots ascending, then the empty key (0) LAST. */
    inline fun forEachInIteratorOrder(action: (key: Int, value: Long) -> Unit) {
        val keys = this.keys
        val values = this.values
        val max = this.mask
        var slot = 0
        while (slot <= max) {
            val existing = keys[slot]
            if (existing != 0) {
                action(existing, values[slot])
            }
            slot++
        }
        if (hasEmptyKey) {
            action(0, values[max + 1])
        }
    }

    /** All keys, in hppc's `keys().toArray()` order (= iterator order, empty key last). */
    fun keysToArray(): IntArray {
        val result = IntArray(size())
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
                equalElements(other as IntLongHashMap)
    }

    protected fun equalElements(other: IntLongHashMap): Boolean {
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

    protected open fun hashKey(key: Int): Int = HashPort.mix(key, keyMixer)

    protected fun verifyLoadFactor(loadFactor: Double): Double =
        HashPort.checkLoadFactor(loadFactor, HashPort.MIN_LOAD_FACTOR, HashPort.MAX_LOAD_FACTOR)

    protected fun rehash(fromKeys: IntArray, fromValues: LongArray) {
        val keys = this.keys
        val values = this.values
        val mask = this.mask

        var from = fromKeys.size - 1
        keys[keys.size - 1] = fromKeys[from]
        values[values.size - 1] = fromValues[from]
        while (--from >= 0) {
            val existing = fromKeys[from]
            if (existing != 0) {
                var slot = hashKey(existing) and mask
                while (keys[slot] != 0) {
                    slot = (slot + 1) and mask
                }
                keys[slot] = existing
                values[slot] = fromValues[from]
            }
        }
    }

    protected fun allocateBuffers(arraySize: Int) {
        val newKeyMixer = HashPort.constantKeyMixer(seed, arraySize)

        val emptyElementSlot = 1
        this.keys = IntArray(arraySize + emptyElementSlot)
        this.values = LongArray(arraySize + emptyElementSlot)

        this.resizeAt = HashPort.expandAtCount(arraySize, loadFactor)
        this.keyMixer = newKeyMixer
        this.mask = arraySize - 1
    }

    protected fun allocateThenInsertThenRehash(slot: Int, pendingKey: Int, pendingValue: Long) {
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
            if (existing == 0) {
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

        keys[gapSlot] = 0
        values[gapSlot] = 0L
        assigned--
    }
}
