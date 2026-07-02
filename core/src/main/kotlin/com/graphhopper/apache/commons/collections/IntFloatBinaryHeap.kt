/*
 *  Copyright 2001-2004 The Apache Software Foundation
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package com.graphhopper.apache.commons.collections

import java.util.Arrays
import java.util.NoSuchElementException

/**
 * This class is a partial copy of the class org.apache.commons.collections.BinaryHeap for
 * just the min heap and primitive, sorted float keys and associated int elements.
 * <p>
 * The library can be found here: https://commons.apache.org/proper/commons-collections/
 */
class IntFloatBinaryHeap @JvmOverloads constructor(initialCapacity: Int = 1000) {
    var size: Int = 0
        private set
    private var elements: IntArray
    private var keys: FloatArray

    init {
        //+1 as element 0 is noop
        elements = IntArray(initialCapacity + 1)
        keys = FloatArray(initialCapacity + 1)
        // make minimum to avoid zero array check in while loop
        keys[0] = Float.NEGATIVE_INFINITY
    }

    private fun isFull(): Boolean {
        //+1 as element 0 is noop
        return elements.size == size + 1
    }

    fun update(key: Double, element: Int) {
        var i = 1
        // we have no clue about the element order, so we need to search the full array
        while (i <= size) {
            if (elements[i] == element)
                break
            i++
        }

        if (i > size)
            return

        if (key > keys[i]) {
            keys[i] = key.toFloat()
            percolateDownMinHeap(i)
        } else {
            keys[i] = key.toFloat()
            percolateUpMinHeap(i)
        }
    }

    fun insert(key: Double, element: Int) {
        if (isFull()) {
            ensureCapacity(elements.size * GROW_FACTOR)
        }

        size++
        elements[size] = element
        keys[size] = key.toFloat()
        percolateUpMinHeap(size)
    }

    fun peekElement(): Int {
        if (isEmpty()) {
            throw NoSuchElementException("Heap is empty. Cannot peek element.")
        } else {
            return elements[1]
        }
    }

    fun peekKey(): Float {
        if (isEmpty())
            throw NoSuchElementException("Heap is empty. Cannot peek key.")
        else
            return keys[1]
    }

    fun poll(): Int {
        val result = peekElement()
        elements[1] = elements[size]
        keys[1] = keys[size]
        size--

        if (size != 0)
            percolateDownMinHeap(1)

        return result
    }

    /**
     * Percolates element down heap from the array position given by the index.
     */
    internal fun percolateDownMinHeap(index: Int) {
        val element = elements[index]
        val key = keys[index]
        var hole = index

        while (hole * 2 <= size) {
            var child = hole * 2

            // if we have a right child and that child can not be percolated
            // up then move onto other child
            if (child != size && keys[child + 1] < keys[child]) {
                child++
            }

            // if we found resting place of bubble then terminate search
            if (keys[child] >= key) {
                break
            }

            elements[hole] = elements[child]
            keys[hole] = keys[child]
            hole = child
        }

        elements[hole] = element
        keys[hole] = key
    }

    internal fun percolateUpMinHeap(index: Int) {
        var hole = index
        val element = elements[hole]
        val key = keys[hole]
        // parent == hole/2
        while (key < keys[hole / 2]) {
            val next = hole / 2
            elements[hole] = elements[next]
            keys[hole] = keys[next]
            hole = next
        }
        elements[hole] = element
        keys[hole] = key
    }

    fun isEmpty(): Boolean {
        return size == 0
    }

    fun clear() {
        trimTo(0)
    }

    internal fun trimTo(toSize: Int) {
        this.size = toSize
        val from = toSize + 1
        // necessary as we currently do not init arrays when inserting
        Arrays.fill(elements, from, size + 1, 0)
    }

    fun ensureCapacity(capacity: Int) {
        if (capacity < size) {
            throw IllegalStateException("IntFloatBinaryHeap contains too many elements to fit in new capacity.")
        }

        elements = Arrays.copyOf(elements, capacity + 1)
        keys = Arrays.copyOf(keys, capacity + 1)
    }

    fun getCapacity(): Long {
        return elements.size.toLong()
    }

    fun getMemoryUsage(): Long {
        return elements.size * 4L + keys.size * 4L
    }

    companion object {
        private const val GROW_FACTOR = 2
    }
}
