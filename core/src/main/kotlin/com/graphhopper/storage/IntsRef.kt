/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.graphhopper.storage

/**
 * Idea and most of the code is from Lucene. But the variables are final, except for the array content.
 */
class IntsRef : Comparable<IntsRef> {
    /**
     * The contents of the IntsRef. Cannot be `null`.
     */
    @JvmField
    val ints: IntArray

    /**
     * Offset of first valid integer.
     */
    @JvmField
    val offset: Int

    /**
     * Length of used ints.
     */
    @JvmField
    val length: Int

    /**
     * Create a IntsRef pointing to a new int array of size `capacity` leading to capacity*32 bits.
     * Offset will be zero and length will be the capacity.
     */
    constructor(capacity: Int) : this(capacity, true)

    private constructor(capacity: Int, checked: Boolean) {
        if (checked && capacity == 0)
            throw IllegalArgumentException("Use instance EMPTY instead of capacity 0")
        ints = IntArray(capacity)
        length = capacity
        offset = 0
    }

    /**
     * This instance will directly reference ints w/o making a copy.
     * ints should not be null.
     */
    constructor(ints: IntArray, offset: Int, length: Int) {
        this.ints = ints
        this.offset = offset
        this.length = length
        assert(isValid())
    }

    override fun hashCode(): Int {
        val prime = 31
        var result = 0
        val end = offset + length
        for (i in offset until end) {
            result = prime * result + ints[i]
        }
        return result
    }

    override fun equals(other: Any?): Boolean {
        if (other == null) {
            return false
        }
        if (other is IntsRef) {
            return this.intsEquals(other)
        }
        return false
    }

    fun intsEquals(other: IntsRef): Boolean {
        if (length == other.length) {
            var otherUpto = other.offset
            val otherInts = other.ints
            val end = offset + length
            var upto = offset
            while (upto < end) {
                if (ints[upto] != otherInts[otherUpto]) {
                    return false
                }
                upto++
                otherUpto++
            }
            return true
        } else {
            return false
        }
    }

    /**
     * Signed int order comparison
     */
    override fun compareTo(other: IntsRef): Int {
        if (this === other) return 0
        val aInts = this.ints
        var aUpto = this.offset
        val bInts = other.ints
        var bUpto = other.offset
        val aStop = aUpto + minOf(this.length, other.length)
        while (aUpto < aStop) {
            val aInt = aInts[aUpto++]
            val bInt = bInts[bUpto++]
            if (aInt > bInt) {
                return 1
            } else if (aInt < bInt) {
                return -1
            }
        }
        // One is a prefix of the other, or, they are equal:
        return this.length - other.length
    }

    /**
     * Performs internal consistency checks.
     * Always returns true (or throws IllegalStateException)
     */
    fun isValid(): Boolean {
        if (length < 0) {
            throw IllegalStateException("length is negative: $length")
        }
        if (length > ints.size) {
            throw IllegalStateException("length is out of bounds: " + length + ",ints.length=" + ints.size)
        }
        if (offset < 0) {
            throw IllegalStateException("offset is negative: $offset")
        }
        if (offset > ints.size) {
            throw IllegalStateException("offset out of bounds: " + offset + ",ints.length=" + ints.size)
        }
        if (offset + length < 0) {
            throw IllegalStateException("offset+length is negative: offset=$offset,length=$length")
        }
        if (offset + length > ints.size) {
            throw IllegalStateException("offset+length out of bounds: offset=" + offset + ",length=" + length + ",ints.length=" + ints.size)
        }
        return true
    }

    override fun toString(): String {
        val sb = StringBuilder()
        sb.append('[')
        val end = offset + length
        for (i in offset until end) {
            if (i > offset) {
                sb.append(' ')
            }
            sb.append(Integer.toHexString(ints[i]))
        }
        sb.append(']')
        return sb.toString()
    }

    val isEmpty: Boolean
        get() {
            for (i in ints.indices) {
                if (ints[i] != 0)
                    return false
            }
            return true
        }

    companion object {
        /**
         * An IntsRef with an array of size 0.
         */
        @JvmField
        val EMPTY: IntsRef = IntsRef(0, false)

        /**
         * Creates a new IntsRef that points to a copy of the ints from `other`.
         *
         * The returned IntsRef will have a length of other.length and an offset of zero.
         */
        @JvmStatic
        fun deepCopyOf(other: IntsRef): IntsRef {
            return IntsRef(other.ints.copyOfRange(other.offset, other.offset + other.length), 0, other.length)
        }
    }
}
