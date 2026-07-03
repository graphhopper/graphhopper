/*
 * Kotlin port of com.carrotsearch.hppc.sorting.IndirectSort from HPPC 0.8.1
 * (Apache License 2.0, https://github.com/carrotsearch/hppc). See NOTICE.md.
 * The mergesort is an exact port: it produces bit-identical index orders,
 * including for equal elements (the sort is stable).
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
 * Sorting routines that return an array of sorted indices implied by a given comparator rather
 * than move elements of whatever the comparator is using for comparisons.
 *
 * A practical use case for this class is when the index of an array is meaningful and one wants
 * to acquire the order of values in that array, without boxing.
 */
object IndirectSort {
    /** Minimum window length to apply insertion sort in merge sort. */
    private const val MIN_LENGTH_FOR_INSERTION_SORT = 30

    /**
     * Returns the order of elements between indices [start] and `start + length`, as indicated
     * by the given [comparator]. This routine uses merge sort. It is guaranteed to be stable.
     */
    @JvmStatic
    fun mergesort(start: Int, length: Int, comparator: IndirectComparator): IntArray {
        val src = createOrderArray(start, length)

        if (length > 1) {
            val dst = src.copyOf()
            topDownMergeSort(src, dst, 0, length, comparator)
            return dst
        }

        return src
    }

    /**
     * Performs a recursive, descending merge sort.
     *
     * @param fromIndex inclusive
     * @param toIndex exclusive
     */
    private fun topDownMergeSort(src: IntArray, dst: IntArray, fromIndex: Int, toIndex: Int, comp: IndirectComparator) {
        if (toIndex - fromIndex <= MIN_LENGTH_FOR_INSERTION_SORT) {
            insertionSort(fromIndex, toIndex - fromIndex, dst, comp)
            return
        }

        val mid = (fromIndex + toIndex) ushr 1
        topDownMergeSort(dst, src, fromIndex, mid, comp)
        topDownMergeSort(dst, src, mid, toIndex, comp)

        // Both splits of src are now sorted.
        if (comp.compare(src[mid - 1], src[mid]) <= 0) {
            // If the lowest element in the upper slice is larger than the highest element in
            // the lower slice, simply copy over, the data is fully sorted.
            src.copyInto(dst, fromIndex, fromIndex, toIndex)
        } else {
            // Run a manual merge.
            var i = fromIndex
            var j = mid
            var k = fromIndex
            while (k < toIndex) {
                if (j == toIndex || (i < mid && comp.compare(src[i], src[j]) <= 0)) {
                    dst[k] = src[i++]
                } else {
                    dst[k] = src[j++]
                }
                k++
            }
        }
    }

    /** Internal insertion sort for ints. */
    private fun insertionSort(off: Int, len: Int, order: IntArray, intComparator: IndirectComparator) {
        for (i in off + 1 until off + len) {
            val v = order[i]
            var j = i
            var t: Int
            while (j > off) {
                t = order[j - 1]
                if (intComparator.compare(t, v) <= 0) break
                order[j--] = t
            }
            order[j] = v
        }
    }

    /** Creates the initial order array. */
    private fun createOrderArray(start: Int, length: Int): IntArray {
        val order = IntArray(length)
        for (i in 0 until length) {
            order[i] = start + i
        }
        return order
    }
}
