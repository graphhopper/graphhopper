/*
 * Kotlin port of com.carrotsearch.hppc.IntObjectHashMap from HPPC 0.8.1 (Apache License 2.0,
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
 * A hash map of `int` to `Object`, implemented using open addressing with linear probing for
 * collision resolution — a 1:1 layout port of HPPC 0.8.1's `IntObjectHashMap`: same bit mixer,
 * load-factor math, resize thresholds, probing and iteration direction, and therefore
 * BIT-IDENTICAL ITERATION ORDER (given the same seed). GraphHopper relies on that order for
 * stored-graph reproducibility (pinned in HashPortOrderPinTest).
 *
 * Iteration-order contract (hppc asymmetry preserved):
 * - [forEach]/[forEachWhile] visit the empty key (0) FIRST, then slots in ascending order.
 * - [forEachInIteratorOrder]/[keysToArray]/[toString] visit slots in ascending order, then the
 *   empty key LAST (hppc's cursor-iterator order).
 */
@Suppress("UNCHECKED_CAST")
open class IntObjectHashMap<V> @JvmOverloads constructor(
    expectedElements: Int = HashPort.DEFAULT_EXPECTED_ELEMENTS,
    loadFactor: Double = HashPort.DEFAULT_LOAD_FACTOR,
    seed: Long = HashPort.DETERMINISTIC_SEED
) {
    /** The array holding keys. */
    @JvmField
    var keys: IntArray = EMPTY_INT_ARRAY

    /** The array holding values. */
    @JvmField
    var values: Array<Any?> = EMPTY_OBJECT_ARRAY

    /**
     * We perturb hash values with a container-unique seed to avoid problems with
     * nearly-sorted-by-hash values on iterations. Recomputed from [seed] on every buffer
     * (re)allocation, exactly like hppc's `HashOrderMixing.constant(seed)`.
     */
    @JvmField
    protected var keyMixer = 0

    /** The number of stored keys (assigned key slots), excluding the special "empty" key. */
    @JvmField
    protected var assigned = 0

    /** Mask for slot scans in [keys]. */
    @JvmField
    var mask = 0

    /** Expand (rehash) [keys] when [assigned] hits this value. */
    @JvmField
    protected var resizeAt = 0

    /** Special treatment for the "empty slot" key marker. */
    @JvmField
    var hasEmptyKey = false

    /** The load factor for [keys]. */
    @JvmField
    protected val loadFactor: Double

    /** The order-mixing seed, inlined replacement of hppc's HashOrderMixingStrategy. */
    @JvmField
    protected val seed: Long

    init {
        this.seed = seed
        this.loadFactor = verifyLoadFactor(loadFactor)
        ensureCapacity(expectedElements)
    }

    fun put(key: Int, value: V?): V? {
        val mask = this.mask
        if (key == 0) {
            hasEmptyKey = true
            val previousValue = values[mask + 1] as V?
            values[mask + 1] = value
            return previousValue
        } else {
            val keys = this.keys
            var slot = hashKey(key) and mask

            var existing = keys[slot]
            while (existing != 0) {
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

    /** Puts the value if `key` does not exist. Returns true if the value was placed. */
    fun putIfAbsent(key: Int, value: V?): Boolean {
        val keyIndex = indexOf(key)
        return if (!indexExists(keyIndex)) {
            indexInsert(keyIndex, key, value)
            true
        } else {
            false
        }
    }

    fun remove(key: Int): V? {
        val mask = this.mask
        if (key == 0) {
            hasEmptyKey = false
            val previousValue = values[mask + 1] as V?
            values[mask + 1] = null
            return previousValue
        } else {
            val keys = this.keys
            var slot = hashKey(key) and mask

            var existing = keys[slot]
            while (existing != 0) {
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

    /** Removes all entries matched by [predicate]; returns the number removed. */
    fun removeAll(predicate: (key: Int, value: V) -> Boolean): Int {
        val before = size()
        val mask = this.mask

        if (hasEmptyKey) {
            if (predicate(0, values[mask + 1] as V)) {
                hasEmptyKey = false
                values[mask + 1] = null
            }
        }

        val keys = this.keys
        val values = this.values
        var slot = 0
        while (slot <= mask) {
            val existing = keys[slot]
            if (existing != 0 && predicate(existing, values[slot] as V)) {
                // Shift, do not increment slot.
                shiftConflictingKeys(slot)
            } else {
                slot++
            }
        }

        return before - size()
    }

    fun get(key: Int): V? {
        if (key == 0) {
            return if (hasEmptyKey) values[mask + 1] as V? else null
        } else {
            val keys = this.keys
            val mask = this.mask
            var slot = hashKey(key) and mask

            var existing = keys[slot]
            while (existing != 0) {
                if (existing == key) {
                    return values[slot] as V?
                }
                slot = (slot + 1) and mask
                existing = keys[slot]
            }

            return null
        }
    }

    fun getOrDefault(key: Int, defaultValue: V?): V? {
        if (key == 0) {
            return if (hasEmptyKey) values[mask + 1] as V? else defaultValue
        } else {
            val keys = this.keys
            val mask = this.mask
            var slot = hashKey(key) and mask

            var existing = keys[slot]
            while (existing != 0) {
                if (existing == key) {
                    return values[slot] as V?
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

    fun indexGet(index: Int): V? = values[index] as V?

    fun indexReplace(index: Int, newValue: V?): V? {
        val previousValue = values[index] as V?
        values[index] = newValue
        return previousValue
    }

    fun indexInsert(index0: Int, key: Int, value: V?) {
        var index = index0.inv()
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
        values.fill(null)
    }

    fun release() {
        assigned = 0
        hasEmptyKey = false
        keys = EMPTY_INT_ARRAY
        values = EMPTY_OBJECT_ARRAY
        ensureCapacity(HashPort.DEFAULT_EXPECTED_ELEMENTS)
    }

    fun size(): Int = assigned + (if (hasEmptyKey) 1 else 0)

    fun isEmpty(): Boolean = size() == 0

    /**
     * Ensures this container can hold at least [expectedElements] keys without resizing.
     */
    fun ensureCapacity(expectedElements: Int) {
        if (expectedElements > resizeAt || keys === EMPTY_INT_ARRAY) {
            val prevKeys = this.keys
            val prevValues = this.values
            allocateBuffers(HashPort.minBufferSize(expectedElements, loadFactor))
            if (prevKeys !== EMPTY_INT_ARRAY && !isEmpty()) {
                rehash(prevKeys, prevValues)
            }
        }
    }

    /**
     * Applies [action] to each key/value pair in hppc's forEach (procedure) order:
     * the empty key (0) FIRST, then slots in ascending order.
     */
    inline fun forEach(action: (key: Int, value: V) -> Unit) {
        val keys = this.keys
        val values = this.values

        if (hasEmptyKey) {
            action(0, values[mask + 1] as V)
        }

        var slot = 0
        val max = this.mask
        while (slot <= max) {
            if (keys[slot] != 0) {
                action(keys[slot], values[slot] as V)
            }
            slot++
        }
    }

    /**
     * Applies [predicate] to each key/value pair in hppc's forEach (predicate) order — the empty
     * key (0) FIRST, then slots ascending — stopping when the predicate returns false.
     */
    inline fun forEachWhile(predicate: (key: Int, value: V) -> Boolean) {
        val keys = this.keys
        val values = this.values

        if (hasEmptyKey) {
            if (!predicate(0, values[mask + 1] as V)) {
                return
            }
        }

        var slot = 0
        val max = this.mask
        while (slot <= max) {
            if (keys[slot] != 0) {
                if (!predicate(keys[slot], values[slot] as V)) {
                    break
                }
            }
            slot++
        }
    }

    /**
     * Applies [action] to each key/value pair in hppc's cursor-iterator order (also the order of
     * hppc's keys()/values() views): slots in ascending order, then the empty key (0) LAST.
     */
    inline fun forEachInIteratorOrder(action: (key: Int, value: V) -> Unit) {
        val keys = this.keys
        val values = this.values
        val max = this.mask
        var slot = 0
        while (slot <= max) {
            val existing = keys[slot]
            if (existing != 0) {
                action(existing, values[slot] as V)
            }
            slot++
        }
        if (hasEmptyKey) {
            action(0, values[max + 1] as V)
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
        var h = if (hasEmptyKey) -0x21524111 /* 0xDEADBEEF */ else 0
        forEachInIteratorOrder { key, value ->
            h += HashPort.mix(key) + HashPort.mix(value)
        }
        return h
    }

    override fun equals(other: Any?): Boolean {
        return other != null &&
                javaClass == other.javaClass &&
                equalElements(other as IntObjectHashMap<*>)
    }

    /** True if all keys of [other] exist in this container with equal values. */
    protected fun equalElements(other: IntObjectHashMap<*>): Boolean {
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

    /**
     * Returns a hash code for the given key: the key's hash mixed with [keyMixer], exactly like
     * hppc's `BitMixer.mix(key, keyMixer)`.
     */
    protected open fun hashKey(key: Int): Int = HashPort.mix(key, keyMixer)

    /** Validates the load factor range and returns it. */
    protected fun verifyLoadFactor(loadFactor: Double): Double =
        HashPort.checkLoadFactor(loadFactor, HashPort.MIN_LOAD_FACTOR, HashPort.MAX_LOAD_FACTOR)

    /** Rehashes from old buffers to new buffers, in hppc's exact (descending) order. */
    protected fun rehash(fromKeys: IntArray, fromValues: Array<Any?>) {
        val keys = this.keys
        val values = this.values
        val mask = this.mask

        // Copy the zero element's slot, then rehash everything else.
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

    /**
     * Allocates new internal buffers. The key mixer for the new buffer size is computed BEFORE
     * expanding, from the seed — exactly hppc's allocateBuffers + HashOrderMixing.constant.
     */
    protected fun allocateBuffers(arraySize: Int) {
        val newKeyMixer = HashPort.constantKeyMixer(seed, arraySize)

        val emptyElementSlot = 1
        this.keys = IntArray(arraySize + emptyElementSlot)
        this.values = arrayOfNulls(arraySize + emptyElementSlot)

        this.resizeAt = HashPort.expandAtCount(arraySize, loadFactor)
        this.keyMixer = newKeyMixer
        this.mask = arraySize - 1
    }

    /**
     * Invoked when there is a new key/value pair to insert but there are not enough empty slots:
     * allocate new buffers, insert the pending pair into the OLD buffer at the free slot, then
     * rehash everything (hppc's exact resize sequence — this ordering is iteration-order
     * relevant).
     */
    protected fun allocateThenInsertThenRehash(slot: Int, pendingKey: Int, pendingValue: V?) {
        val prevKeys = this.keys
        val prevValues = this.values
        allocateBuffers(HashPort.nextBufferSize(mask + 1, size(), loadFactor))

        prevKeys[slot] = pendingKey
        prevValues[slot] = pendingValue

        rehash(prevKeys, prevValues)
    }

    /** Shifts all slot-conflicting keys and values allocated to (and including) `gapSlot`. */
    protected fun shiftConflictingKeys(gapSlot0: Int) {
        var gapSlot = gapSlot0
        val keys = this.keys
        val values = this.values
        val mask = this.mask

        // Perform shifts of conflicting keys to fill in the gap.
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
                // Entry at this position was originally at or before the gap slot.
                keys[gapSlot] = existing
                values[gapSlot] = values[slot]
                gapSlot = slot
                distance = 0
            }
        }

        // Mark the last found gap slot without a conflict as empty.
        keys[gapSlot] = 0
        values[gapSlot] = null
        assigned--
    }

    companion object {
        @JvmField
        val EMPTY_INT_ARRAY = IntArray(0)

        @JvmField
        val EMPTY_OBJECT_ARRAY = arrayOfNulls<Any?>(0)
    }
}
