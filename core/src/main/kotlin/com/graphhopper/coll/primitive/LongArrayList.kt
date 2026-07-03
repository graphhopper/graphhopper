/*
 * Kotlin port of com.carrotsearch.hppc.LongArrayList from HPPC 0.8.1 (Apache License 2.0,
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
 * An array-backed, insertion-ordered list of longs — a behavioural 1:1 port of HPPC 0.8.1's
 * `LongArrayList`. See [IntArrayList] for details; growth policy and public field layout are
 * identical.
 */
open class LongArrayList : LongIndexedContainer {

    @JvmField
    var buffer: LongArray = EMPTY_ARRAY

    @JvmField
    var elementsCount: Int = 0

    constructor() : this(DEFAULT_EXPECTED_ELEMENTS)

    constructor(expectedElements: Int) {
        ensureCapacity(expectedElements)
    }

    constructor(container: LongContainer) : this(container.size()) {
        addAll(container)
    }

    override fun add(e1: Long) {
        ensureBufferSpace(1)
        buffer[elementsCount++] = e1
    }

    fun add(e1: Long, e2: Long) {
        ensureBufferSpace(2)
        buffer[elementsCount++] = e1
        buffer[elementsCount++] = e2
    }

    fun add(elements: LongArray, start: Int, length: Int) {
        ensureBufferSpace(length)
        System.arraycopy(elements, start, buffer, elementsCount, length)
        elementsCount += length
    }

    fun add(vararg elements: Long) {
        add(elements, 0, elements.size)
    }

    fun addAll(container: LongContainer): Int {
        val size = container.size()
        ensureBufferSpace(size)
        for (cursor in container)
            add(cursor.value)
        return size
    }

    fun addAll(iterable: Iterable<LongCursor>): Int {
        var size = 0
        for (cursor in iterable) {
            add(cursor.value)
            size++
        }
        return size
    }

    override fun insert(index: Int, e1: Long) {
        ensureBufferSpace(1)
        System.arraycopy(buffer, index, buffer, index + 1, elementsCount - index)
        buffer[index] = e1
        elementsCount++
    }

    override fun get(index: Int): Long = buffer[index]

    override fun set(index: Int, e1: Long): Long {
        val v = buffer[index]
        buffer[index] = e1
        return v
    }

    override fun remove(index: Int): Long {
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

    override fun removeFirst(e1: Long): Int {
        val index = indexOf(e1)
        if (index >= 0) remove(index)
        return index
    }

    override fun removeLast(e1: Long): Int {
        val index = lastIndexOf(e1)
        if (index >= 0) remove(index)
        return index
    }

    override fun contains(e: Long): Boolean = indexOf(e) >= 0

    override fun indexOf(e1: Long): Int {
        for (i in 0 until elementsCount)
            if (buffer[i] == e1) return i
        return -1
    }

    override fun lastIndexOf(e1: Long): Int {
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
            val newSize = IntArrayList.grow(bufferLen, elementsCount, expectedAdditions)
            buffer = Arrays.copyOf(buffer, newSize)
        }
    }

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

    fun trimToSize() {
        if (size() != buffer.size)
            buffer = toArray()
    }

    fun clear() {
        Arrays.fill(buffer, 0, elementsCount, 0)
        this.elementsCount = 0
    }

    fun release() {
        this.buffer = EMPTY_ARRAY
        this.elementsCount = 0
    }

    override fun toArray(): LongArray = Arrays.copyOf(buffer, elementsCount)

    override fun hashCode(): Int {
        var h = 1
        val max = elementsCount
        for (i in 0 until max)
            h = 31 * h + HashPort.mix(buffer[i])
        return h
    }

    override fun equals(other: Any?): Boolean =
        other != null && javaClass == other.javaClass && equalElements(other as LongArrayList)

    protected fun equalElements(other: LongArrayList): Boolean {
        val max = size()
        if (other.size() != max) return false
        for (i in 0 until max)
            if (other.get(i) != get(i)) return false
        return true
    }

    override fun iterator(): Iterator<LongCursor> = ValueIterator(buffer, size())

    override fun toString(): String = Arrays.toString(toArray())

    private class ValueIterator(private val buffer: LongArray, private val size: Int) : Iterator<LongCursor> {
        private val cursor = LongCursor().also { it.index = -1 }

        override fun hasNext(): Boolean = cursor.index + 1 < size

        override fun next(): LongCursor {
            cursor.value = buffer[++cursor.index]
            return cursor
        }
    }

    companion object {
        @JvmField
        val EMPTY_ARRAY = LongArray(0)

        private const val DEFAULT_EXPECTED_ELEMENTS = 4

        @JvmStatic
        fun from(vararg elements: Long): LongArrayList {
            val list = LongArrayList(elements.size)
            list.add(*elements)
            return list
        }
    }
}
