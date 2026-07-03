/*
 * Kotlin port of com.carrotsearch.hppc.cursors.{Int,Long,Double}Cursor from HPPC 0.8.1
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
 * A cursor over a collection of ints — a 1:1 port of HPPC's `IntCursor`. The container returns the
 * SAME cursor instance on every iteration step (fields updated in place) to avoid boxing.
 */
class IntCursor {
    /** The current value's index in the container this cursor belongs to. */
    @JvmField
    var index: Int = 0

    /** The current value. */
    @JvmField
    var value: Int = 0

    override fun toString(): String = "[cursor, index: $index, value: $value]"
}

/** A cursor over a collection of longs — a 1:1 port of HPPC's `LongCursor`. */
class LongCursor {
    @JvmField
    var index: Int = 0

    @JvmField
    var value: Long = 0

    override fun toString(): String = "[cursor, index: $index, value: $value]"
}

/** A cursor over a collection of doubles — a 1:1 port of HPPC's `DoubleCursor`. */
class DoubleCursor {
    @JvmField
    var index: Int = 0

    @JvmField
    var value: Double = 0.0

    override fun toString(): String = "[cursor, index: $index, value: $value]"
}
