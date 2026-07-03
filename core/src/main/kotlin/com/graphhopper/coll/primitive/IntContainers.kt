/*
 * Kotlin port of com.carrotsearch.hppc.{IntContainer,IntIndexedContainer} from HPPC 0.8.1
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
 * A generic container holding ints — the read-only surface shared by the list ports. Mirrors the
 * subset of HPPC's `IntContainer` actually used in GraphHopper. `size()` stays a method and
 * `isEmpty` a property to preserve the exact Kotlin call sites written against HPPC.
 */
interface IntContainer : Iterable<IntCursor> {
    fun size(): Int
    val isEmpty: Boolean
    fun contains(e: Int): Boolean
    fun toArray(): IntArray
    override fun iterator(): Iterator<IntCursor>
}

/**
 * An indexed container providing random access to ints by zero-based index — mirrors the used
 * subset of HPPC's `IntIndexedContainer`.
 */
interface IntIndexedContainer : IntContainer {
    fun get(index: Int): Int
    fun set(index: Int, e1: Int): Int
    fun add(e1: Int)
    fun insert(index: Int, e1: Int)
    fun indexOf(e1: Int): Int
    fun lastIndexOf(e1: Int): Int
    fun removeFirst(e1: Int): Int
    fun removeLast(e1: Int): Int
    fun remove(index: Int): Int
    fun removeRange(fromIndex: Int, toIndex: Int)
}
