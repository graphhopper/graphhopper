/*
 * Kotlin port of com.carrotsearch.hppc.LongArrayDeque from HPPC 0.8.1
 * (Apache License 2.0, https://github.com/carrotsearch/hppc), with the default
 * BoundedProportionalArraySizingStrategy growth policy inlined. See NOTICE.md.
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
 * An array-backed deque of longs — a 1:1 behavioral port of HPPC 0.8.1's `LongArrayDeque`
 * (head/tail wrap-around semantics and growth policy included). Hot during import (subnetwork
 * preparation DFS stacks), hence array-backed and allocation-free like the original.
 */
class LongArrayDeque @JvmOverloads constructor(expectedElements: Int = DEFAULT_EXPECTED_ELEMENTS) {
    /** Internal array for storing elements of the deque. */
    @JvmField
    var buffer: LongArray = EMPTY_ARRAY

    /**
     * The index of the element at the head of the deque or an arbitrary number equal to
     * [tail] if the deque is empty.
     */
    @JvmField
    var head: Int = 0

    /** The index at which the next element would be added to the tail of the deque. */
    @JvmField
    var tail: Int = 0

    init {
        ensureCapacity(expectedElements)
    }

    /** Inserts the specified element at the front of this deque. */
    fun addFirst(e1: Long) {
        var h = oneLeft(head, buffer.size)
        if (h == tail) {
            ensureBufferSpace(1)
            h = oneLeft(head, buffer.size)
        }
        head = h
        buffer[head] = e1
    }

    /** Inserts the specified element at the end of this deque. */
    fun addLast(e1: Long) {
        var t = oneRight(tail, buffer.size)
        if (head == t) {
            ensureBufferSpace(1)
            t = oneRight(tail, buffer.size)
        }
        buffer[tail] = e1
        tail = t
    }

    /** Retrieves and removes the first element of this deque. The deque must not be empty. */
    fun removeFirst(): Long {
        val result = buffer[head]
        buffer[head] = 0L
        head = oneRight(head, buffer.size)
        return result
    }

    /** Retrieves and removes the last element of this deque. The deque must not be empty. */
    fun removeLast(): Long {
        tail = oneLeft(tail, buffer.size)
        val result = buffer[tail]
        buffer[tail] = 0L
        return result
    }

    /** Retrieves, but does not remove, the first element of this deque. Must not be empty. */
    fun getFirst(): Long = buffer[head]

    /** Retrieves, but does not remove, the last element of this deque. Must not be empty. */
    fun getLast(): Long = buffer[oneLeft(tail, buffer.size)]

    fun size(): Int {
        return if (head <= tail) tail - head
        else tail - head + buffer.size
    }

    val isEmpty: Boolean
        get() = size() == 0

    /**
     * Removes all elements. The internal array buffers are not released as a result of this
     * call; see [release].
     */
    fun clear() {
        if (head < tail) {
            buffer.fill(0L, head, tail)
        } else {
            buffer.fill(0L, 0, tail)
            buffer.fill(0L, head, buffer.size)
        }
        head = 0
        tail = 0
    }

    /** Releases the internal buffers of this deque and reallocates the default buffer. */
    fun release() {
        head = 0
        tail = 0
        buffer = EMPTY_ARRAY
        ensureBufferSpace(0)
    }

    /**
     * Ensures this container can hold at least the given number of elements without resizing
     * its buffers.
     */
    fun ensureCapacity(expectedElements: Int) {
        ensureBufferSpace(expectedElements - size())
    }

    /** Copies the deque's elements (head first) into a new array. */
    fun toArray(): LongArray = toArray(LongArray(size()))

    /**
     * Copies elements of this deque to [target]. The content of the target array is filled from
     * index 0 (head of the queue) to index `size() - 1` (tail of the queue). It must be large
     * enough to hold all elements.
     */
    fun toArray(target: LongArray): LongArray {
        if (head < tail) {
            // The content is not wrapped around. Just copy.
            buffer.copyInto(target, 0, head, head + size())
        } else if (head > tail) {
            // The content is split: [head...buffer.size - 1][0, tail - 1]
            val rightCount = buffer.size - head
            buffer.copyInto(target, 0, head, buffer.size)
            buffer.copyInto(target, rightCount, 0, tail)
        }
        return target
    }

    /** Applies [action] to each element, from head to tail. */
    inline fun forEach(action: (Long) -> Unit) {
        val buf = buffer
        var i = head
        while (i != tail) {
            action(buf[i])
            i = if (i + 1 == buf.size) 0 else i + 1
        }
    }

    /**
     * Ensures the internal buffer has enough free slots to store [expectedAdditions],
     * growing with HPPC's default BoundedProportionalArraySizingStrategy
     * (minGrow=10, growRatio=1.5).
     */
    private fun ensureBufferSpace(expectedAdditions: Int) {
        val bufferLen = buffer.size
        val elementsCount = size()

        if (elementsCount + expectedAdditions >= bufferLen) {
            // deque invariant: there is always at least one empty slot.
            val newSize = grow(bufferLen, elementsCount + 1, expectedAdditions)

            val newBuffer = LongArray(newSize)
            if (bufferLen > 0) {
                toArray(newBuffer)
                tail = elementsCount
                head = 0
            }
            buffer = newBuffer
        }
    }

    companion object {
        private const val DEFAULT_EXPECTED_ELEMENTS = 4

        /** Maximum allocable array length (Integer.MAX_VALUE minus aligned array header + slack). */
        private const val MAX_ARRAY_LENGTH = Int.MAX_VALUE - 32

        private val EMPTY_ARRAY = LongArray(0)

        /**
         * HPPC's BoundedProportionalArraySizingStrategy.grow with the default settings:
         * grow by half the current buffer size, at least by 10.
         */
        private fun grow(currentBufferLength: Int, elementsCount: Int, expectedAdditions: Int): Int {
            // replicate Java's (long)((long) len * 0.5f): long is promoted to float for the multiply
            var growBy = (currentBufferLength.toLong() * 0.5f).toLong()
            growBy = maxOf(growBy, 10L)
            growBy = minOf(growBy, MAX_ARRAY_LENGTH.toLong())
            val growTo = minOf(MAX_ARRAY_LENGTH.toLong(), growBy + currentBufferLength)
            val newSize = maxOf((elementsCount + expectedAdditions).toLong(), growTo)

            if (newSize > MAX_ARRAY_LENGTH) {
                throw RuntimeException(
                    "Java array size exceeded (current length: $currentBufferLength, " +
                            "elements: $elementsCount, expected additions: $expectedAdditions)"
                )
            }
            return newSize.toInt()
        }

        /** Move one index to the left, wrapping around the buffer. */
        @PublishedApi
        internal fun oneLeft(index: Int, modulus: Int): Int =
            if (index >= 1) index - 1 else modulus - 1

        /** Move one index to the right, wrapping around the buffer. */
        @PublishedApi
        internal fun oneRight(index: Int, modulus: Int): Int =
            if (index + 1 == modulus) 0 else index + 1
    }
}
