/*
 * Kotlin port of com.carrotsearch.hppc.IntHashSet from HPPC 0.8.1 (Apache License 2.0,
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
 * A hash set of `int`s, implemented using open addressing with linear probing for collision
 * resolution — a 1:1 layout port of HPPC 0.8.1's `IntHashSet` with BIT-IDENTICAL ITERATION
 * ORDER (given the same seed).
 *
 * Iteration-order contract (hppc asymmetry preserved):
 * - [forEach]/[forEachWhile]/[toArray] visit the empty key (0) FIRST, then slots ascending.
 * - [forEachInIteratorOrder]/[toString] visit slots ascending, then the empty key LAST
 *   (hppc's cursor-iterator order).
 */
open class IntHashSet @JvmOverloads constructor(
    expectedElements: Int = HashPort.DEFAULT_EXPECTED_ELEMENTS,
    loadFactor: Double = HashPort.DEFAULT_LOAD_FACTOR,
    seed: Long = HashPort.DETERMINISTIC_SEED
) {
    /** The hash array holding keys. */
    @JvmField
    var keys: IntArray = IntObjectHashMap.EMPTY_INT_ARRAY

    @JvmField
    protected var assigned = 0

    /** Mask for slot scans in [keys]. */
    @JvmField
    var mask = 0

    @JvmField
    protected var keyMixer = 0

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

    /** Adds [key] to the set; returns true if it was not already present. */
    fun add(key: Int): Boolean {
        if (key == 0) {
            val added = !hasEmptyKey
            hasEmptyKey = true
            return added
        } else {
            val keys = this.keys
            val mask = this.mask
            var slot = hashKey(key) and mask

            var existing = keys[slot]
            while (existing != 0) {
                if (existing == key) {
                    return false
                }
                slot = (slot + 1) and mask
                existing = keys[slot]
            }

            if (assigned == resizeAt) {
                allocateThenInsertThenRehash(slot, key)
            } else {
                keys[slot] = key
            }

            assigned++
            return true
        }
    }

    /**
     * Adds all elements from the given array/vararg; returns how many were actually added.
     */
    fun addAll(vararg elements: Int): Int {
        ensureCapacity(elements.size)
        var count = 0
        for (e in elements) {
            if (add(e)) count++
        }
        return count
    }

    /**
     * Adds all elements of [container] — mirrors hppc's `addAll(IntContainer)`:
     * `ensureCapacity(container.size())` first, then adds in the container's iterator order
     * (slots ascending, empty key last). Returns how many were actually added.
     */
    fun addAll(container: IntHashSet): Int {
        ensureCapacity(container.size())
        var count = 0
        container.forEachInIteratorOrder { e ->
            if (add(e)) count++
        }
        return count
    }

    /**
     * All keys, in hppc's `IntHashSet.toArray()` order: the empty key (0) FIRST, then slots in
     * ascending order.
     */
    fun toArray(): IntArray {
        val cloned = IntArray(size())
        var j = 0
        if (hasEmptyKey) {
            cloned[j++] = 0
        }

        val keys = this.keys
        for (slot in 0..mask) {
            val existing = keys[slot]
            if (existing != 0) {
                cloned[j++] = existing
            }
        }

        return cloned
    }

    /** Removes [key] from the set; returns true if it was present. */
    fun remove(key: Int): Boolean {
        if (key == 0) {
            val hadEmptyKey = hasEmptyKey
            hasEmptyKey = false
            return hadEmptyKey
        } else {
            val keys = this.keys
            val mask = this.mask
            var slot = hashKey(key) and mask

            var existing = keys[slot]
            while (existing != 0) {
                if (existing == key) {
                    shiftConflictingKeys(slot)
                    return true
                }
                slot = (slot + 1) and mask
                existing = keys[slot]
            }
            return false
        }
    }

    /** An alias for [remove], returning the number of removed elements (0 or 1). */
    fun removeAll(key: Int): Int = if (remove(key)) 1 else 0

    /** Removes all keys matched by [predicate]; returns the number removed. */
    fun removeAll(predicate: (key: Int) -> Boolean): Int {
        val before = size()

        if (hasEmptyKey) {
            if (predicate(0)) {
                hasEmptyKey = false
            }
        }

        val keys = this.keys
        val max = this.mask
        var slot = 0
        while (slot <= max) {
            val existing = keys[slot]
            if (existing != 0 && predicate(existing)) {
                // Repeat the check for the same slot (shifted).
                shiftConflictingKeys(slot)
            } else {
                slot++
            }
        }

        return before - size()
    }

    fun contains(key: Int): Boolean {
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

    fun clear() {
        assigned = 0
        hasEmptyKey = false
        keys.fill(0)
    }

    fun release() {
        assigned = 0
        hasEmptyKey = false
        keys = IntObjectHashMap.EMPTY_INT_ARRAY
        ensureCapacity(HashPort.DEFAULT_EXPECTED_ELEMENTS)
    }

    fun isEmpty(): Boolean = size() == 0

    fun ensureCapacity(expectedElements: Int) {
        if (expectedElements > resizeAt || keys === IntObjectHashMap.EMPTY_INT_ARRAY) {
            val prevKeys = this.keys
            allocateBuffers(HashPort.minBufferSize(expectedElements, loadFactor))
            if (prevKeys !== IntObjectHashMap.EMPTY_INT_ARRAY && !isEmpty()) {
                rehash(prevKeys)
            }
        }
    }

    fun size(): Int = assigned + (if (hasEmptyKey) 1 else 0)

    /** hppc forEach (procedure) order: the empty key (0) FIRST, then slots ascending. */
    inline fun forEach(action: (key: Int) -> Unit) {
        if (hasEmptyKey) {
            action(0)
        }

        val keys = this.keys
        val max = this.mask
        var slot = 0
        while (slot <= max) {
            val existing = keys[slot]
            if (existing != 0) {
                action(existing)
            }
            slot++
        }
    }

    /** hppc forEach (predicate) order with early exit on false. */
    inline fun forEachWhile(predicate: (key: Int) -> Boolean) {
        if (hasEmptyKey) {
            if (!predicate(0)) {
                return
            }
        }

        val keys = this.keys
        val max = this.mask
        var slot = 0
        while (slot <= max) {
            val existing = keys[slot]
            if (existing != 0) {
                if (!predicate(existing)) {
                    break
                }
            }
            slot++
        }
    }

    /**
     * The first key in hppc's cursor-iterator order (slots ascending, then the empty key last) —
     * the equivalent of hppc's `iterator().next().value`, including the
     * [NoSuchElementException] on an empty set.
     */
    fun firstInIteratorOrder(): Int {
        val keys = this.keys
        for (slot in 0..mask) {
            val existing = keys[slot]
            if (existing != 0) return existing
        }
        if (hasEmptyKey) return 0
        throw NoSuchElementException("set is empty")
    }

    /** hppc cursor-iterator order: slots ascending, then the empty key (0) LAST. */
    inline fun forEachInIteratorOrder(action: (key: Int) -> Unit) {
        val keys = this.keys
        val max = this.mask
        var slot = 0
        while (slot <= max) {
            val existing = keys[slot]
            if (existing != 0) {
                action(existing)
            }
            slot++
        }
        if (hasEmptyKey) {
            action(0)
        }
    }

    override fun hashCode(): Int {
        var h = if (hasEmptyKey) -0x21524111 /* 0xDEADBEEF */ else 0
        val keys = this.keys
        for (slot in mask downTo 0) {
            val existing = keys[slot]
            if (existing != 0) {
                h += HashPort.mix(existing)
            }
        }
        return h
    }

    override fun equals(other: Any?): Boolean {
        return other != null &&
                javaClass == other.javaClass &&
                sameKeys(other as IntHashSet)
    }

    /** True if all keys of the other container exist in this container. */
    private fun sameKeys(other: IntHashSet): Boolean {
        if (other.size() != size()) return false
        var same = true
        other.forEachInIteratorOrder { key -> if (same && !contains(key)) same = false }
        return same
    }

    override fun toString(): String {
        val buffer = StringBuilder()
        buffer.append("[")
        var first = true
        forEachInIteratorOrder { key ->
            if (!first) buffer.append(", ")
            buffer.append(key)
            first = false
        }
        buffer.append("]")
        return buffer.toString()
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

    fun indexGet(index: Int): Int = keys[index]

    fun indexReplace(index: Int, equivalentKey: Int): Int {
        val previousValue = keys[index]
        keys[index] = equivalentKey
        return previousValue
    }

    fun indexInsert(index0: Int, key: Int) {
        val index = index0.inv()
        if (key == 0) {
            hasEmptyKey = true
        } else {
            if (assigned == resizeAt) {
                allocateThenInsertThenRehash(index, key)
            } else {
                keys[index] = key
            }
            assigned++
        }
    }

    protected open fun hashKey(key: Int): Int = HashPort.mix(key, keyMixer)

    protected fun verifyLoadFactor(loadFactor: Double): Double =
        HashPort.checkLoadFactor(loadFactor, HashPort.MIN_LOAD_FACTOR, HashPort.MAX_LOAD_FACTOR)

    /**
     * Rehashes from old buffers to new buffers, in hppc's exact (descending) order — note sets
     * skip the trailing empty-key slot, unlike the maps.
     */
    protected fun rehash(fromKeys: IntArray) {
        val keys = this.keys
        val mask = this.mask
        for (i in fromKeys.size - 2 downTo 0) {
            val existing = fromKeys[i]
            if (existing != 0) {
                var slot = hashKey(existing) and mask
                while (keys[slot] != 0) {
                    slot = (slot + 1) and mask
                }
                keys[slot] = existing
            }
        }
    }

    protected fun allocateBuffers(arraySize: Int) {
        val newKeyMixer = HashPort.constantKeyMixer(seed, arraySize)

        val emptyElementSlot = 1
        this.keys = IntArray(arraySize + emptyElementSlot)

        this.resizeAt = HashPort.expandAtCount(arraySize, loadFactor)
        this.keyMixer = newKeyMixer
        this.mask = arraySize - 1
    }

    protected fun allocateThenInsertThenRehash(slot: Int, pendingKey: Int) {
        val prevKeys = this.keys
        allocateBuffers(HashPort.nextBufferSize(mask + 1, size(), loadFactor))

        prevKeys[slot] = pendingKey

        rehash(prevKeys)
    }

    protected fun shiftConflictingKeys(gapSlot0: Int) {
        var gapSlot = gapSlot0
        val keys = this.keys
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
                gapSlot = slot
                distance = 0
            }
        }

        keys[gapSlot] = 0
        assigned--
    }

    companion object {
        /** Creates a set from a variable number of arguments (hppc `IntHashSet.from`). */
        @JvmStatic
        fun from(vararg elements: Int): IntHashSet {
            val set = IntHashSet(elements.size)
            set.addAll(*elements)
            return set
        }
    }
}
