/*
 * Kotlin port of com.carrotsearch.hppc.ObjectIntHashMap from HPPC 0.8.1 (Apache License 2.0,
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
 * A hash map of `Object` to `int` — a 1:1 layout port of HPPC 0.8.1's `ObjectIntHashMap` with
 * bit-identical iteration order (given the same seed and keys with stable hashCode, e.g.
 * String). The `null` key plays hppc's "empty slot" role. Missing keys map to `0`.
 * See [IntObjectHashMap] for the iteration-order contract.
 */
@Suppress("UNCHECKED_CAST")
open class ObjectIntHashMap<K> @JvmOverloads constructor(
    expectedElements: Int = HashPort.DEFAULT_EXPECTED_ELEMENTS,
    loadFactor: Double = HashPort.DEFAULT_LOAD_FACTOR,
    seed: Long = HashPort.DETERMINISTIC_SEED
) {
    /** The array holding keys. */
    @JvmField
    var keys: Array<Any?> = IntObjectHashMap.EMPTY_OBJECT_ARRAY

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

    /** Special treatment for the "empty slot" (null) key marker. */
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

    fun put(key: K?, value: Int): Int {
        val mask = this.mask
        if (key == null) {
            hasEmptyKey = true
            val previousValue = values[mask + 1]
            values[mask + 1] = value
            return previousValue
        } else {
            val keys = this.keys
            var slot = hashKey(key) and mask

            var existing = keys[slot]
            while (existing != null) {
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

    /** hppc Trove-inspired putOrAdd. */
    fun putOrAdd(key: K?, putValue0: Int, incrementValue: Int): Int {
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

    fun addTo(key: K?, incrementValue: Int): Int = putOrAdd(key, incrementValue, incrementValue)

    fun remove(key: K?): Int {
        val mask = this.mask
        if (key == null) {
            hasEmptyKey = false
            val previousValue = values[mask + 1]
            values[mask + 1] = 0
            return previousValue
        } else {
            val keys = this.keys
            var slot = hashKey(key) and mask

            var existing = keys[slot]
            while (existing != null) {
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

    fun get(key: K?): Int {
        if (key == null) {
            return if (hasEmptyKey) values[mask + 1] else 0
        } else {
            val keys = this.keys
            val mask = this.mask
            var slot = hashKey(key) and mask

            var existing = keys[slot]
            while (existing != null) {
                if (existing == key) {
                    return values[slot]
                }
                slot = (slot + 1) and mask
                existing = keys[slot]
            }

            return 0
        }
    }

    fun getOrDefault(key: K?, defaultValue: Int): Int {
        if (key == null) {
            return if (hasEmptyKey) values[mask + 1] else defaultValue
        } else {
            val keys = this.keys
            val mask = this.mask
            var slot = hashKey(key) and mask

            var existing = keys[slot]
            while (existing != null) {
                if (existing == key) {
                    return values[slot]
                }
                slot = (slot + 1) and mask
                existing = keys[slot]
            }

            return defaultValue
        }
    }

    fun containsKey(key: K?): Boolean {
        if (key == null) {
            return hasEmptyKey
        } else {
            val keys = this.keys
            val mask = this.mask
            var slot = hashKey(key) and mask

            var existing = keys[slot]
            while (existing != null) {
                if (existing == key) {
                    return true
                }
                slot = (slot + 1) and mask
                existing = keys[slot]
            }

            return false
        }
    }

    fun indexOf(key: K?): Int {
        val mask = this.mask
        if (key == null) {
            return if (hasEmptyKey) mask + 1 else (mask + 1).inv()
        } else {
            val keys = this.keys
            var slot = hashKey(key) and mask

            var existing = keys[slot]
            while (existing != null) {
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

    fun indexInsert(index0: Int, key: K?, value: Int) {
        val index = index0.inv()
        if (key == null) {
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
        keys.fill(null)
        // hppc parity: the values array is NOT cleared (stale slots are gated by keys[slot]==null)
    }

    fun release() {
        assigned = 0
        hasEmptyKey = false
        keys = IntObjectHashMap.EMPTY_OBJECT_ARRAY
        values = IntObjectHashMap.EMPTY_INT_ARRAY
        ensureCapacity(HashPort.DEFAULT_EXPECTED_ELEMENTS)
    }

    fun size(): Int = assigned + (if (hasEmptyKey) 1 else 0)

    fun isEmpty(): Boolean = size() == 0

    fun ensureCapacity(expectedElements: Int) {
        if (expectedElements > resizeAt || keys === IntObjectHashMap.EMPTY_OBJECT_ARRAY) {
            val prevKeys = this.keys
            val prevValues = this.values
            allocateBuffers(HashPort.minBufferSize(expectedElements, loadFactor))
            if (prevKeys !== IntObjectHashMap.EMPTY_OBJECT_ARRAY && !isEmpty()) {
                rehash(prevKeys, prevValues)
            }
        }
    }

    /** hppc forEach (procedure) order: the empty (null) key FIRST, then slots ascending. */
    inline fun forEach(action: (key: K?, value: Int) -> Unit) {
        val keys = this.keys
        val values = this.values

        if (hasEmptyKey) {
            action(null, values[mask + 1])
        }

        var slot = 0
        val max = this.mask
        while (slot <= max) {
            val existing = keys[slot]
            if (existing != null) {
                action(existing as K, values[slot])
            }
            slot++
        }
    }

    /** hppc forEach (predicate) order with early exit on false. */
    inline fun forEachWhile(predicate: (key: K?, value: Int) -> Boolean) {
        val keys = this.keys
        val values = this.values

        if (hasEmptyKey) {
            if (!predicate(null, values[mask + 1])) {
                return
            }
        }

        var slot = 0
        val max = this.mask
        while (slot <= max) {
            val existing = keys[slot]
            if (existing != null) {
                if (!predicate(existing as K, values[slot])) {
                    break
                }
            }
            slot++
        }
    }

    /** hppc cursor-iterator order: slots ascending, then the empty (null) key LAST. */
    inline fun forEachInIteratorOrder(action: (key: K?, value: Int) -> Unit) {
        val keys = this.keys
        val values = this.values
        val max = this.mask
        var slot = 0
        while (slot <= max) {
            val existing = keys[slot]
            if (existing != null) {
                action(existing as K, values[slot])
            }
            slot++
        }
        if (hasEmptyKey) {
            action(null, values[max + 1])
        }
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
                equalElements(other as ObjectIntHashMap<*>)
    }

    protected fun equalElements(other: ObjectIntHashMap<*>): Boolean {
        if (other.size() != size()) return false
        var equal = true
        other.forEachInIteratorOrder { key, value ->
            @Suppress("UNCHECKED_CAST")
            val k = key as K?
            if (equal && !(containsKey(k) && get(k) == value)) equal = false
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

    /**
     * Returns a hash code for the given key: `BitMixer.mix(key.hashCode(), keyMixer)`, exactly
     * like hppc (which requires stable hashCode implementations for order reproducibility).
     */
    protected open fun hashKey(key: K): Int = HashPort.mix(key, keyMixer)

    protected fun verifyLoadFactor(loadFactor: Double): Double =
        HashPort.checkLoadFactor(loadFactor, HashPort.MIN_LOAD_FACTOR, HashPort.MAX_LOAD_FACTOR)

    protected fun rehash(fromKeys: Array<Any?>, fromValues: IntArray) {
        val keys = this.keys
        val values = this.values
        val mask = this.mask

        var from = fromKeys.size - 1
        keys[keys.size - 1] = fromKeys[from]
        values[values.size - 1] = fromValues[from]
        while (--from >= 0) {
            val existing = fromKeys[from]
            if (existing != null) {
                var slot = hashKey(existing as K) and mask
                while (keys[slot] != null) {
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
        this.keys = arrayOfNulls(arraySize + emptyElementSlot)
        this.values = IntArray(arraySize + emptyElementSlot)

        this.resizeAt = HashPort.expandAtCount(arraySize, loadFactor)
        this.keyMixer = newKeyMixer
        this.mask = arraySize - 1
    }

    protected fun allocateThenInsertThenRehash(slot: Int, pendingKey: K, pendingValue: Int) {
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
            val existing = keys[slot] ?: break

            val idealSlot = hashKey(existing as K)
            val shift = (slot - idealSlot) and mask
            if (shift >= distance) {
                keys[gapSlot] = existing
                values[gapSlot] = values[slot]
                gapSlot = slot
                distance = 0
            }
        }

        keys[gapSlot] = null
        values[gapSlot] = 0
        assigned--
    }
}
