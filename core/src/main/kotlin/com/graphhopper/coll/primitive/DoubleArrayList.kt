/*
 * Kotlin port of com.carrotsearch.hppc.DoubleArrayList from HPPC 0.8.1 (Apache License 2.0,
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
 * An array-backed, insertion-ordered list of doubles — a behavioural 1:1 port of HPPC 0.8.1's
 * `DoubleArrayList`. Element equality (and the ArrayUtilTest-style buffer growth) match HPPC:
 * equality compares `Double.doubleToLongBits`, and the growth policy is shared with [IntArrayList].
 */
open class DoubleArrayList : Iterable<DoubleCursor> {

    @JvmField
    var buffer: DoubleArray = EMPTY_ARRAY

    @JvmField
    var elementsCount: Int = 0

    constructor() : this(DEFAULT_EXPECTED_ELEMENTS)

    constructor(expectedElements: Int) {
        ensureCapacity(expectedElements)
    }

    fun add(e1: Double) {
        ensureBufferSpace(1)
        buffer[elementsCount++] = e1
    }

    fun add(e1: Double, e2: Double) {
        ensureBufferSpace(2)
        buffer[elementsCount++] = e1
        buffer[elementsCount++] = e2
    }

    fun add(elements: DoubleArray, start: Int, length: Int) {
        ensureBufferSpace(length)
        System.arraycopy(elements, start, buffer, elementsCount, length)
        elementsCount += length
    }

    fun add(vararg elements: Double) {
        add(elements, 0, elements.size)
    }

    fun insert(index: Int, e1: Double) {
        ensureBufferSpace(1)
        System.arraycopy(buffer, index, buffer, index + 1, elementsCount - index)
        buffer[index] = e1
        elementsCount++
    }

    fun get(index: Int): Double = buffer[index]

    fun set(index: Int, e1: Double): Double {
        val v = buffer[index]
        buffer[index] = e1
        return v
    }

    fun remove(index: Int): Double {
        val v = buffer[index]
        if (index + 1 < elementsCount)
            System.arraycopy(buffer, index + 1, buffer, index, elementsCount - index - 1)
        elementsCount--
        buffer[elementsCount] = 0.0
        return v
    }

    fun contains(e: Double): Boolean = indexOf(e) >= 0

    fun indexOf(e1: Double): Int {
        for (i in 0 until elementsCount)
            if (buffer[i].toBits() == e1.toBits()) return i
        return -1
    }

    val isEmpty: Boolean
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
                Arrays.fill(buffer, newSize, elementsCount, 0.0)
            else
                Arrays.fill(buffer, elementsCount, newSize, 0.0)
        } else {
            ensureCapacity(newSize)
        }
        this.elementsCount = newSize
    }

    fun size(): Int = elementsCount

    fun trimToSize() {
        if (size() != buffer.size)
            buffer = toArray()
    }

    fun clear() {
        Arrays.fill(buffer, 0, elementsCount, 0.0)
        this.elementsCount = 0
    }

    fun release() {
        this.buffer = EMPTY_ARRAY
        this.elementsCount = 0
    }

    fun toArray(): DoubleArray = Arrays.copyOf(buffer, elementsCount)

    override fun hashCode(): Int {
        var h = 1
        val max = elementsCount
        for (i in 0 until max)
            h = 31 * h + HashPort.mix64(buffer[i].toBits()).toInt()
        return h
    }

    override fun equals(other: Any?): Boolean =
        other != null && javaClass == other.javaClass && equalElements(other as DoubleArrayList)

    protected fun equalElements(other: DoubleArrayList): Boolean {
        val max = size()
        if (other.size() != max) return false
        for (i in 0 until max)
            if (other.get(i).toBits() != get(i).toBits()) return false
        return true
    }

    override fun iterator(): Iterator<DoubleCursor> = ValueIterator(buffer, size())

    override fun toString(): String = Arrays.toString(toArray())

    private class ValueIterator(private val buffer: DoubleArray, private val size: Int) : Iterator<DoubleCursor> {
        private val cursor = DoubleCursor().also { it.index = -1 }

        override fun hasNext(): Boolean = cursor.index + 1 < size

        override fun next(): DoubleCursor {
            cursor.value = buffer[++cursor.index]
            return cursor
        }
    }

    companion object {
        @JvmField
        val EMPTY_ARRAY = DoubleArray(0)

        private const val DEFAULT_EXPECTED_ELEMENTS = 4

        @JvmStatic
        fun from(vararg elements: Double): DoubleArrayList {
            val list = DoubleArrayList(elements.size)
            list.add(*elements)
            return list
        }
    }
}
