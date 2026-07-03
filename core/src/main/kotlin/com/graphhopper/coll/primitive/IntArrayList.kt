/*
 * Kotlin port of com.carrotsearch.hppc.IntArrayList (and its AbstractIntCollection /
 * BoundedProportionalArraySizingStrategy behaviour) from HPPC 0.8.1 (Apache License 2.0,
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

import java.util.Arrays

/**
 * An array-backed, insertion-ordered list of ints — a behavioural 1:1 port of HPPC 0.8.1's
 * `IntArrayList`, including the public [buffer]/[elementsCount] fields (directly poked by hot-path
 * code such as [com.graphhopper.util.ArrayUtil]) and the exact buffer growth policy of HPPC's
 * `BoundedProportionalArraySizingStrategy` (asserted e.g. by ArrayUtilTest). Being a list, iteration
 * order equals insertion order, so it carries no stored-graph checksum risk.
 */
open class IntArrayList : IntIndexedContainer {

    /** Internal array for storing the list. May be larger than [size]. */
    @JvmField
    var buffer: IntArray = EMPTY_ARRAY

    /** Current number of elements stored in [buffer]. */
    @JvmField
    var elementsCount: Int = 0

    constructor() : this(DEFAULT_EXPECTED_ELEMENTS)

    constructor(expectedElements: Int) {
        ensureCapacity(expectedElements)
    }

    /** Creates a new list from the elements of another container in its iteration order. */
    constructor(container: IntContainer) : this(container.size()) {
        addAll(container)
    }

    override fun add(e1: Int) {
        ensureBufferSpace(1)
        buffer[elementsCount++] = e1
    }

    fun add(e1: Int, e2: Int) {
        ensureBufferSpace(2)
        buffer[elementsCount++] = e1
        buffer[elementsCount++] = e2
    }

    fun add(elements: IntArray, start: Int, length: Int) {
        ensureBufferSpace(length)
        System.arraycopy(elements, start, buffer, elementsCount, length)
        elementsCount += length
    }

    fun add(vararg elements: Int) {
        add(elements, 0, elements.size)
    }

    fun addAll(container: IntContainer): Int {
        val size = container.size()
        ensureBufferSpace(size)
        for (cursor in container)
            add(cursor.value)
        return size
    }

    fun addAll(iterable: Iterable<IntCursor>): Int {
        var size = 0
        for (cursor in iterable) {
            add(cursor.value)
            size++
        }
        return size
    }

    override fun insert(index: Int, e1: Int) {
        ensureBufferSpace(1)
        System.arraycopy(buffer, index, buffer, index + 1, elementsCount - index)
        buffer[index] = e1
        elementsCount++
    }

    override fun get(index: Int): Int = buffer[index]

    override fun set(index: Int, e1: Int): Int {
        val v = buffer[index]
        buffer[index] = e1
        return v
    }

    override fun remove(index: Int): Int {
        val v = buffer[index]
        if (index + 1 < elementsCount)
            System.arraycopy(buffer, index + 1, buffer, index, elementsCount - index - 1)
        elementsCount--
        buffer[elementsCount] = 0
        return v
    }

    override fun removeRange(fromIndex: Int, toIndex: Int) {
        System.arraycopy(buffer, toIndex, buffer, fromIndex, elementsCount - toIndex)
        val count = toIndex - fromIndex
        elementsCount -= count
        Arrays.fill(buffer, elementsCount, elementsCount + count, 0)
    }

    override fun removeFirst(e1: Int): Int {
        val index = indexOf(e1)
        if (index >= 0) remove(index)
        return index
    }

    override fun removeLast(e1: Int): Int {
        val index = lastIndexOf(e1)
        if (index >= 0) remove(index)
        return index
    }

    fun removeAll(e1: Int): Int {
        var to = 0
        for (from in 0 until elementsCount) {
            if (buffer[from] == e1) {
                buffer[from] = 0
                continue
            }
            if (to != from) {
                buffer[to] = buffer[from]
                buffer[from] = 0
            }
            to++
        }
        val deleted = elementsCount - to
        this.elementsCount = to
        return deleted
    }

    override fun contains(e: Int): Boolean = indexOf(e) >= 0

    override fun indexOf(e1: Int): Int {
        for (i in 0 until elementsCount)
            if (buffer[i] == e1) return i
        return -1
    }

    override fun lastIndexOf(e1: Int): Int {
        for (i in elementsCount - 1 downTo 0)
            if (buffer[i] == e1) return i
        return -1
    }

    override val isEmpty: Boolean
        get() = elementsCount == 0

    fun ensureCapacity(expectedElements: Int) {
        val bufferLen = buffer.size
        if (expectedElements > bufferLen)
            ensureBufferSpace(expectedElements - size())
    }

    protected fun ensureBufferSpace(expectedAdditions: Int) {
        val bufferLen = buffer.size
        if (elementsCount + expectedAdditions > bufferLen) {
            val newSize = grow(bufferLen, elementsCount, expectedAdditions)
            buffer = Arrays.copyOf(buffer, newSize)
        }
    }

    /**
     * Truncate or expand the list to the new size. Truncated values are reset to zero; expanded
     * elements are initialized with zero.
     */
    fun resize(newSize: Int) {
        if (newSize <= buffer.size) {
            if (newSize < elementsCount)
                Arrays.fill(buffer, newSize, elementsCount, 0)
            else
                Arrays.fill(buffer, elementsCount, newSize, 0)
        } else {
            ensureCapacity(newSize)
        }
        this.elementsCount = newSize
    }

    override fun size(): Int = elementsCount

    /** Trim the internal buffer to the current size. */
    fun trimToSize() {
        if (size() != buffer.size)
            buffer = toArray()
    }

    /** Sets the number of stored elements to zero and zero-fills the used part of the buffer. */
    fun clear() {
        Arrays.fill(buffer, 0, elementsCount, 0)
        this.elementsCount = 0
    }

    /** Sets the number of stored elements to zero and releases the internal storage array. */
    fun release() {
        this.buffer = EMPTY_ARRAY
        this.elementsCount = 0
    }

    override fun toArray(): IntArray = Arrays.copyOf(buffer, elementsCount)

    override fun hashCode(): Int {
        var h = 1
        val max = elementsCount
        for (i in 0 until max)
            h = 31 * h + HashPort.mix(buffer[i])
        return h
    }

    override fun equals(other: Any?): Boolean =
        other != null && javaClass == other.javaClass && equalElements(other as IntArrayList)

    protected fun equalElements(other: IntArrayList): Boolean {
        val max = size()
        if (other.size() != max) return false
        for (i in 0 until max)
            if (other.get(i) != get(i)) return false
        return true
    }

    override fun iterator(): Iterator<IntCursor> = ValueIterator(buffer, size())

    override fun toString(): String = Arrays.toString(toArray())

    private class ValueIterator(private val buffer: IntArray, private val size: Int) : Iterator<IntCursor> {
        private val cursor = IntCursor().also { it.index = -1 }

        override fun hasNext(): Boolean = cursor.index + 1 < size

        override fun next(): IntCursor {
            cursor.value = buffer[++cursor.index]
            return cursor
        }
    }

    companion object {
        @JvmField
        val EMPTY_ARRAY = IntArray(0)

        private const val DEFAULT_EXPECTED_ELEMENTS = 4
        private const val MAX_ARRAY_LENGTH = Int.MAX_VALUE - 32

        /** HPPC BoundedProportionalArraySizingStrategy.grow (min grow 10, ratio 1.5). */
        internal fun grow(currentBufferLength: Int, elementsCount: Int, expectedAdditions: Int): Int {
            var growBy = (currentBufferLength.toLong() * 0.5f).toLong()
            growBy = maxOf(growBy, 10L)
            growBy = minOf(growBy, MAX_ARRAY_LENGTH.toLong())
            val growTo = minOf(MAX_ARRAY_LENGTH.toLong(), growBy + currentBufferLength)
            val newSize = maxOf(elementsCount.toLong() + expectedAdditions, growTo)
            if (newSize > MAX_ARRAY_LENGTH)
                throw RuntimeException("Java array size exceeded (current length: $currentBufferLength)")
            return newSize.toInt()
        }

        /** Create a list from a variable number of arguments. */
        @JvmStatic
        fun from(vararg elements: Int): IntArrayList {
            val list = IntArrayList(elements.size)
            list.add(*elements)
            return list
        }
    }
}
