/*
 * Kotlin port of com.carrotsearch.hppc.sorting.IndirectComparator from HPPC 0.8.1
 * (Apache License 2.0, https://github.com/carrotsearch/hppc). See NOTICE.md.
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
 * Compares values at two given indices and returns the result of their comparison consistent
 * with `java.util.Comparator`'s contract.
 *
 * Beware of the `return (int - int)` idiom, it is usually broken if arbitrary numbers can
 * appear on input. Use regular comparison operations - they are very fast anyway.
 */
fun interface IndirectComparator {
    fun compare(indexA: Int, indexB: Int): Int

    /** A natural-order comparator for an int array. */
    class AscendingIntComparator(private val array: IntArray) : IndirectComparator {
        override fun compare(indexA: Int, indexB: Int): Int {
            val a = array[indexA]
            val b = array[indexB]
            return if (a < b) -1 else if (a > b) 1 else 0
        }
    }
}
