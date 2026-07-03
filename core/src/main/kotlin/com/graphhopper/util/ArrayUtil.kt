/*
 *  Licensed to GraphHopper GmbH under one or more contributor
 *  license agreements. See the NOTICE file distributed with this work for
 *  additional information regarding copyright ownership.
 *
 *  GraphHopper GmbH licenses this file to you under the Apache License,
 *  Version 2.0 (the "License"); you may not use this file except in
 *  compliance with the License. You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package com.graphhopper.util

import com.carrotsearch.hppc.BitSet
import com.carrotsearch.hppc.IntArrayList
import com.carrotsearch.hppc.IntIndexedContainer
import com.carrotsearch.hppc.LongArrayList
import com.carrotsearch.hppc.sorting.IndirectComparator
import com.carrotsearch.hppc.sorting.IndirectSort
import java.util.Arrays
import java.util.Random

object ArrayUtil {

    /**
     * Creates an IntArrayList of a given size where each element is set to the given value
     */
    @JvmStatic
    fun constant(size: Int, value: Int): IntArrayList {
        val result = IntArrayList(size)
        Arrays.fill(result.buffer, value)
        result.elementsCount = size
        return result
    }

    /**
     * Creates an IntArrayList filled with zeros
     */
    @JvmStatic
    fun zero(size: Int): IntArrayList {
        val result = IntArrayList(size)
        result.elementsCount = size
        return result
    }

    /**
     * Creates an IntArrayList filled with the integers 0,1,2,3,...,size-1
     */
    @JvmStatic
    fun iota(size: Int): IntArrayList = range(0, size)

    /**
     * Creates an IntArrayList filled with the integers [startIncl,endExcl[
     */
    @JvmStatic
    fun range(startIncl: Int, endExcl: Int): IntArrayList {
        val result = IntArrayList(endExcl - startIncl)
        result.elementsCount = endExcl - startIncl
        for (i in 0 until result.size())
            result.set(i, startIncl + i)
        return result
    }

    /**
     * Creates an IntArrayList filled with the integers [startIncl,endIncl]
     */
    @JvmStatic
    fun rangeClosed(startIncl: Int, endIncl: Int): IntArrayList = range(startIncl, endIncl + 1)

    /**
     * Creates an IntArrayList filled with a permutation of the numbers 0,1,2,...,size-1
     */
    @JvmStatic
    fun permutation(size: Int, rnd: Random): IntArrayList {
        val result = iota(size)
        shuffle(result, rnd)
        return result
    }

    @JvmStatic
    fun isPermutation(arr: IntArrayList): Boolean {
        val present = BitSet(arr.size().toLong())
        for (e in arr) {
            if (e.value >= arr.size() || e.value < 0)
                return false
            if (present.get(e.value.toLong()))
                return false
            present.set(e.value.toLong())
        }
        return true
    }

    /**
     * Reverses the order of the given list's elements in place and returns it
     */
    @JvmStatic
    fun reverse(list: IntArrayList): IntArrayList {
        val buffer = list.buffer
        var start = 0
        var end = list.size() - 1
        while (start < end) {
            // swap the values
            val tmp = buffer[start]
            buffer[start] = buffer[end]
            buffer[end] = tmp
            start++
            end--
        }
        return list
    }

    @JvmStatic
    fun reverse(list: LongArrayList): LongArrayList {
        val buffer = list.buffer
        var start = 0
        var end = list.size() - 1
        while (start < end) {
            // swap the values
            val tmp = buffer[start]
            buffer[start] = buffer[end]
            buffer[end] = tmp
            start++
            end--
        }
        return list
    }

    /**
     * Shuffles the elements of the given list in place and returns it
     */
    @JvmStatic
    fun shuffle(list: IntArrayList, random: Random): IntArrayList {
        val maxHalf = list.size() / 2
        for (x1 in 0 until maxHalf) {
            val x2 = random.nextInt(maxHalf) + maxHalf
            val tmp = list.buffer[x1]
            list.buffer[x1] = list.buffer[x2]
            list.buffer[x2] = tmp
        }
        return list
    }

    /**
     * Removes all duplicate elements of the given array in the range [0, end[ in place
     *
     * @return the size of the new range that contains no duplicates (smaller or equal to end).
     */
    @JvmStatic
    fun removeConsecutiveDuplicates(arr: IntArray, end: Int): Int {
        if (end < 0)
            throw IllegalArgumentException("end less than 0")
        if (end == 0)
            return 0
        var curr = 0
        for (i in 1 until end) {
            if (arr[i] != arr[curr])
                arr[++curr] = arr[i]
        }
        return curr + 1
    }

    /**
     * Creates a copy of the given list where all consecutive duplicates are removed
     */
    @JvmStatic
    fun withoutConsecutiveDuplicates(arr: IntIndexedContainer): IntIndexedContainer {
        val result = IntArrayList()
        if (arr.isEmpty)
            return result
        var prev = arr.get(0)
        result.add(prev)
        for (i in 1 until arr.size()) {
            val value = arr.get(i)
            if (value != prev)
                result.add(value)
            prev = value
        }
        return result
    }

    /**
     * Maps one array using another, i.e. every element arr[x] is replaced by map[arr[x]]
     */
    @JvmStatic
    fun transform(arr: IntIndexedContainer, map: IntIndexedContainer) {
        for (i in 0 until arr.size())
            arr.set(i, map.get(arr.get(i)))
    }

    @JvmStatic
    fun calcSortOrder(arr1: IntArrayList, arr2: IntArrayList): IntArray {
        if (arr1.elementsCount != arr2.elementsCount) {
            throw IllegalArgumentException("Arrays must have equal size")
        }
        return calcSortOrder(arr1.buffer, arr2.buffer, arr1.elementsCount)
    }

    /**
     * This method calculates the sort order of the first {@param length} element-pairs given by two arrays.
     * The order is chosen such that it sorts the element-pairs first by the first and second by the second array.
     * The input arrays are not manipulated by this method.
     *
     * @param length must not be larger than either of the two input array lengths.
     * @return an array x of length {@param length}. e.g. if this method returns x = {2, 0, 1} it means that that
     * the element-pair with index 2 comes first in the order and so on
     */
    @JvmStatic
    fun calcSortOrder(arr1: IntArray, arr2: IntArray, length: Int): IntArray {
        if (arr1.size < length || arr2.size < length)
            throw IllegalArgumentException("Arrays must not be shorter than given length")
        val comp = IndirectComparator { indexA, indexB ->
            val arr1cmp = arr1[indexA].compareTo(arr1[indexB])
            if (arr1cmp != 0) arr1cmp else arr2[indexA].compareTo(arr2[indexB])
        }
        return IndirectSort.mergesort(0, length, comp)
    }

    /**
     * Creates a copy of the given array such that it is ordered by the given order.
     * The order can be shorter or equal, but not longer than the array.
     */
    @JvmStatic
    fun applyOrder(arr: IntArray, order: IntArray): IntArray {
        if (order.size > arr.size)
            throw IllegalArgumentException("sort order must not be shorter than array")
        val result = IntArray(order.size)
        for (i in result.indices)
            result[i] = arr[order[i]]
        return result
    }

    /**
     * Creates a new array where each element represents the index position of this element in the given array
     * or is set to -1 if this element does not appear in the input array. None of the elements of the input array may
     * be equal or larger than the arrays length.
     */
    @JvmStatic
    fun invert(arr: IntArray): IntArray {
        val result = IntArray(arr.size)
        Arrays.fill(result, -1)
        for (i in arr.indices)
            result[arr[i]] = i
        return result
    }

    @JvmStatic
    fun invert(list: IntArrayList): IntArrayList {
        val result = IntArrayList(list.size())
        result.elementsCount = list.size()
        for (i in 0 until result.elementsCount)
            result.set(list.get(i), i)
        return result
    }

    @JvmStatic
    fun subList(list: IntArrayList, fromIndex: Int, toIndex: Int): IntArrayList {
        val result = IntArrayList(toIndex - fromIndex)
        for (i in fromIndex until toIndex)
            result.add(list.get(i))
        return result
    }

    /**
     * @param a sorted array
     * @param b sorted array
     * @return sorted array consisting of the elements of a and b, duplicates get removed
     */
    @JvmStatic
    fun merge(a: IntArray, b: IntArray): IntArray {
        if (a.size + b.size == 0)
            return intArrayOf()
        val result = IntArray(a.size + b.size)
        var size = 0
        var i = 0
        var j = 0
        while (i < a.size && j < b.size) {
            if (a[i] < b[j])
                result[size++] = a[i++]
            else
                result[size++] = b[j++]
        }
        if (i == a.size) {
            System.arraycopy(b, j, result, size, b.size - j)
            size += b.size - j
        } else {
            System.arraycopy(a, i, result, size, a.size - i)
            size += a.size - i
        }
        val sizeWithoutDuplicates = removeConsecutiveDuplicates(result, size)
        return Arrays.copyOf(result, sizeWithoutDuplicates)
    }

    @JvmStatic
    fun getLast(list: IntArrayList): Int {
        if (list.isEmpty)
            throw IllegalArgumentException("Cannot get last element of an empty list")
        return list.get(list.size() - 1)
    }

    @JvmStatic
    fun getLast(array: IntArray): Int {
        if (array.isEmpty())
            throw IllegalArgumentException("Cannot get last element of an empty array")
        return array[array.size - 1]
    }
}
