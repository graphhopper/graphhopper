/*
 * Kotlin port of com.carrotsearch.hppc.{LongContainer,LongIndexedContainer} from HPPC 0.8.1
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

/** A generic container holding longs — mirrors the used subset of HPPC's `LongContainer`. */
interface LongContainer : Iterable<LongCursor> {
    fun size(): Int
    val isEmpty: Boolean
    fun contains(e: Long): Boolean
    fun toArray(): LongArray
    override fun iterator(): Iterator<LongCursor>
}

/** An indexed container of longs — mirrors the used subset of HPPC's `LongIndexedContainer`. */
interface LongIndexedContainer : LongContainer {
    fun get(index: Int): Long
    fun set(index: Int, e1: Long): Long
    fun add(e1: Long)
    fun insert(index: Int, e1: Long)
    fun indexOf(e1: Long): Int
    fun lastIndexOf(e1: Long): Int
    fun removeFirst(e1: Long): Int
    fun removeLast(e1: Long): Int
    fun remove(index: Int): Long
    fun removeRange(fromIndex: Int, toIndex: Int)
}
