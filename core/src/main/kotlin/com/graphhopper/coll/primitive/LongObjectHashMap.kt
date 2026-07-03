/*
 * Kotlin port of com.carrotsearch.hppc.LongObjectHashMap from HPPC 0.8.1 (Apache License 2.0,
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
 * A hash map of `long` to `Object` — a 1:1 layout port of HPPC 0.8.1's `LongObjectHashMap` with
 * bit-identical iteration order (given the same seed). See [IntObjectHashMap] for the
 * iteration-order contract.
 */
@Suppress("UNCHECKED_CAST")
open class LongObjectHashMap<V> @JvmOverloads constructor(
    expectedElements: Int = HashPort.DEFAULT_EXPECTED_ELEMENTS,
    loadFactor: Double = HashPort.DEFAULT_LOAD_FACTOR,
    seed: Long = HashPort.DETERMINISTIC_SEED
) {
    /** The array holding keys. */
    @JvmField
    var keys: LongArray = EMPTY_LONG_ARRAY

    /** The array holding values. */
    @JvmField
    var values: Array<Any?> = IntObjectHashMap.EMPTY_OBJECT_ARRAY

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

    fun put(key: Long, value: V?): V? {
        val mask = this.mask
        if (key == 0L) {
            hasEmptyKey = true
            val previousValue = values[mask + 1] as V?
            values[mask + 1] = value
            return previousValue
        } else {
            val keys = this.keys
            var slot = hashKey(key) and mask

            var existing = keys[slot]
            while (existing != 0L) {
                if (existing == key) {
                    val previousValue = values[slot] as V?
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
            return null
        }
    }

    fun putIfAbsent(key: Long, value: V?): Boolean {
        val keyIndex = indexOf(key)
        return if (!indexExists(keyIndex)) {
            indexInsert(keyIndex, key, value)
            true
        } else {
            false
        }
    }

    fun remove(key: Long): V? {
        val mask = this.mask
        if (key == 0L) {
            hasEmptyKey = false
            val previousValue = values[mask + 1] as V?
            values[mask + 1] = null
            return previousValue
        } else {
            val keys = this.keys
            var slot = hashKey(key) and mask

            var existing = keys[slot]
            while (existing != 0L) {
                if (existing == key) {
                    val previousValue = values[slot] as V?
                    shiftConflictingKeys(slot)
                    return previousValue
                }
                slot = (slot + 1) and mask
                existing = keys[slot]
            }

            return null
        }
    }

    fun removeAll(predicate: (key: Long, value: V) -> Boolean): Int {
        val before = size()
        val mask = this.mask

        if (hasEmptyKey) {
            if (predicate(0L, values[mask + 1] as V)) {
                hasEmptyKey = false
                values[mask + 1] = null
            }
        }

        val keys = this.keys
        val values = this.values
        var slot = 0
        while (slot <= mask) {
            val existing = keys[slot]
            if (existing != 0L && predicate(existing, values[slot] as V)) {
                shiftConflictingKeys(slot)
            } else {
                slot++
            }
        }

        return before - size()
    }

    fun get(key: Long): V? {
        if (key == 0L) {
            return if (hasEmptyKey) values[mask + 1] as V? else null
        } else {
            val keys = this.keys
            val mask = this.mask
            var slot = hashKey(key) and mask

            var existing = keys[slot]
            while (existing != 0L) {
                if (existing == key) {
                    return values[slot] as V?
                }
                slot = (slot + 1) and mask
                existing = keys[slot]
            }

            return null
        }
    }

    fun getOrDefault(key: Long, defaultValue: V?): V? {
        if (key == 0L) {
            return if (hasEmptyKey) values[mask + 1] as V? else defaultValue
        } else {
            val keys = this.keys
            val mask = this.mask
            var slot = hashKey(key) and mask

            var existing = keys[slot]
            while (existing != 0L) {
                if (existing == key) {
                    return values[slot] as V?
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

    fun indexGet(index: Int): V? = values[index] as V?

    fun indexReplace(index: Int, newValue: V?): V? {
        val previousValue = values[index] as V?
        values[index] = newValue
        return previousValue
    }

    fun indexInsert(index0: Int, key: Long, value: V?) {
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
        values.fill(null)
    }

    fun release() {
        assigned = 0
        hasEmptyKey = false
        keys = EMPTY_LONG_ARRAY
        values = IntObjectHashMap.EMPTY_OBJECT_ARRAY
        ensureCapacity(HashPort.DEFAULT_EXPECTED_ELEMENTS)
    }

    fun size(): Int = assigned + (if (hasEmptyKey) 1 else 0)

    fun isEmpty(): Boolean = size() == 0

    fun ensureCapacity(expectedElements: Int) {
        if (expectedElements > resizeAt || keys === EMPTY_LONG_ARRAY) {
            val prevKeys = this.keys
            val prevValues = this.values
            allocateBuffers(HashPort.minBufferSize(expectedElements, loadFactor))
            if (prevKeys !== EMPTY_LONG_ARRAY && !isEmpty()) {
                rehash(prevKeys, prevValues)
            }
        }
    }

    /** hppc forEach (procedure) order: the empty key (0) FIRST, then slots ascending. */
    inline fun forEach(action: (key: Long, value: V) -> Unit) {
        val keys = this.keys
        val values = this.values

        if (hasEmptyKey) {
            action(0L, values[mask + 1] as V)
        }

        var slot = 0
        val max = this.mask
        while (slot <= max) {
            if (keys[slot] != 0L) {
                action(keys[slot], values[slot] as V)
            }
            slot++
        }
    }

    /** hppc forEach (predicate) order with early exit on false. */
    inline fun forEachWhile(predicate: (key: Long, value: V) -> Boolean) {
        val keys = this.keys
        val values = this.values

        if (hasEmptyKey) {
            if (!predicate(0L, values[mask + 1] as V)) {
                return
            }
        }

        var slot = 0
        val max = this.mask
        while (slot <= max) {
            if (keys[slot] != 0L) {
                if (!predicate(keys[slot], values[slot] as V)) {
                    break
                }
            }
            slot++
        }
    }

    /** hppc cursor-iterator order: slots ascending, then the empty key (0) LAST. */
    inline fun forEachInIteratorOrder(action: (key: Long, value: V) -> Unit) {
        val keys = this.keys
        val values = this.values
        val max = this.mask
        var slot = 0
        while (slot <= max) {
            val existing = keys[slot]
            if (existing != 0L) {
                action(existing, values[slot] as V)
            }
            slot++
        }
        if (hasEmptyKey) {
            action(0L, values[max + 1] as V)
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
                equalElements(other as LongObjectHashMap<*>)
    }

    protected fun equalElements(other: LongObjectHashMap<*>): Boolean {
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

    protected open fun hashKey(key: Long): Int = HashPort.mix(key, keyMixer)

    protected fun verifyLoadFactor(loadFactor: Double): Double =
        HashPort.checkLoadFactor(loadFactor, HashPort.MIN_LOAD_FACTOR, HashPort.MAX_LOAD_FACTOR)

    protected fun rehash(fromKeys: LongArray, fromValues: Array<Any?>) {
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
        val newKeyMixer = HashPort.constantKeyMixer(seed, arraySize)

        val emptyElementSlot = 1
        this.keys = LongArray(arraySize + emptyElementSlot)
        this.values = arrayOfNulls(arraySize + emptyElementSlot)

        this.resizeAt = HashPort.expandAtCount(arraySize, loadFactor)
        this.keyMixer = newKeyMixer
        this.mask = arraySize - 1
    }

    protected fun allocateThenInsertThenRehash(slot: Int, pendingKey: Long, pendingValue: V?) {
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
        values[gapSlot] = null
        assigned--
    }

    companion object {
        @JvmField
        val EMPTY_LONG_ARRAY = LongArray(0)
    }
}
