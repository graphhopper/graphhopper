/*
 * Kotlin port of com.carrotsearch.hppc.BitSet from HPPC 0.8.1 (Apache License 2.0,
 * https://github.com/carrotsearch/hppc), which itself was repackaged from
 * org.apache.lucene.util.OpenBitSet (Apache Lucene, svn rev. 1479633).
 * See NOTICE.md. Original license header:
 *
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
package com.graphhopper.coll

/**
 * An "open", growable bit set that allows direct access to the array of words storing the bits —
 * a 1:1 behavioral port of HPPC 0.8.1's `BitSet` (semantics, growth policy and the exact field
 * layout).
 *
 * CRITICAL — storage format: the field names and types (`bits: LongArray`, `wlen: Int`, in this
 * declaration order, and NO other instance fields) are part of GraphHopper's stored-graph format.
 * [com.graphhopper.routing.ev.ExternalBooleanEncodedValue] is FIELD-serialized by Jackson into
 * graph metadata as `{"bits":[...],"wlen":N}` (pinned in EncodedValueSerializerTest). Do not
 * rename fields, reorder them, or add instance fields to this class.
 */
class GrowableBitSet {
    /** Internal representation of bits in this bit set. */
    @JvmField
    var bits: LongArray

    /** The number of words (longs) used in the [bits] array. */
    @JvmField
    var wlen: Int

    /** Constructs a bit set with the default capacity of 64 bits. */
    constructor() : this(DEFAULT_NUM_BITS)

    /** Constructs a bit set large enough to hold [numBits]. */
    constructor(numBits: Long) {
        bits = LongArray(bits2words(numBits))
        wlen = bits.size
    }

    /**
     * Constructs a bit set from an existing LongArray. Bit index 0 is the least significant bit
     * of `bits[0]`. [numWords] is the number of elements that contain set bits (non-zero longs);
     * it must be `<= bits.size`, and any existing words at position `>= numWords` must be zero.
     */
    constructor(bits: LongArray, numWords: Int) {
        this.bits = bits
        this.wlen = numWords
    }

    /**
     * Returns an iterator over all set bits of this bitset, mirroring HPPC's `BitSetIterator`
     * usage pattern: `for (i = it.nextSetBit(); i >= 0; i = it.nextSetBit())`.
     */
    fun iterator(): GrowableBitSetIterator = GrowableBitSetIterator(bits, wlen)

    /** The current capacity in bits (1 greater than the index of the last bit). */
    fun capacity(): Long = (bits.size shl 6).toLong()

    /**
     * The current capacity of this set (included for compatibility with `java.util.BitSet.size`).
     * This is NOT equal to [cardinality].
     */
    fun size(): Long = capacity()

    /** The "logical size": the index of the highest set bit plus one. */
    fun length(): Long {
        trimTrailingZeros()
        if (wlen == 0) return 0
        return ((wlen - 1).toLong() shl 6) + (64 - bits[wlen - 1].countLeadingZeroBits())
    }

    /** Returns true if there are no set bits. */
    fun isEmpty(): Boolean = cardinality() == 0L

    /** Returns true or false for the specified bit [index]. */
    fun get(index: Int): Boolean {
        val i = index shr 6 // div 64
        // signed shift keeps a negative index negative and forces an
        // ArrayIndexOutOfBoundsException, removing the need for an explicit check.
        if (i >= bits.size) return false
        val bit = index and 0x3f
        val bitmask = 1L shl bit
        return (bits[i] and bitmask) != 0L
    }

    /** Returns true or false for the specified bit [index]. */
    fun get(index: Long): Boolean {
        val i = (index shr 6).toInt()
        if (i >= bits.size) return false
        val bit = index.toInt() and 0x3f
        val bitmask = 1L shl bit
        return (bits[i] and bitmask) != 0L
    }

    /** Sets a bit, expanding the set size if necessary. */
    fun set(index: Long) {
        val wordNum = expandingWordNum(index)
        val bit = index.toInt() and 0x3f
        val bitmask = 1L shl bit
        bits[wordNum] = bits[wordNum] or bitmask
    }

    /**
     * Sets a range of bits, expanding the set size if necessary.
     *
     * @param startIndex lower index
     * @param endIndex one-past the last bit to set
     */
    fun set(startIndex: Long, endIndex: Long) {
        if (endIndex <= startIndex) return

        val startWord = (startIndex shr 6).toInt()
        // since endIndex is one past the end, this is the index of the last word to be changed.
        val endWord = expandingWordNum(endIndex - 1)

        val startmask = -1L shl startIndex.toInt() // shift distance is taken mod 64, like in Java
        val endmask = -1L ushr (-endIndex).toInt() // 64-(endIndex&0x3f) is the same as -endIndex due to wrap

        if (startWord == endWord) {
            bits[startWord] = bits[startWord] or (startmask and endmask)
            return
        }

        bits[startWord] = bits[startWord] or startmask
        bits.fill(-1L, startWord + 1, endWord)
        bits[endWord] = bits[endWord] or endmask
    }

    private fun expandingWordNum(index: Long): Int {
        val wordNum = (index shr 6).toInt()
        if (wordNum >= wlen) {
            ensureCapacity(index + 1)
            wlen = wordNum + 1
        }
        return wordNum
    }

    /** Clears all bits. */
    fun clear() {
        bits.fill(0L)
        this.wlen = 0
    }

    /** Clears a bit, allowing access beyond the current set size without changing the size. */
    fun clear(index: Long) {
        val wordNum = (index shr 6).toInt()
        if (wordNum >= wlen) return
        val bit = index.toInt() and 0x3f
        val bitmask = 1L shl bit
        bits[wordNum] = bits[wordNum] and bitmask.inv()
    }

    /**
     * Clears a range of bits. Clearing past the end does not change the size of the set.
     *
     * @param startIndex lower index
     * @param endIndex one-past the last bit to clear
     */
    fun clear(startIndex: Int, endIndex: Int) {
        if (endIndex <= startIndex) return

        val startWord = startIndex shr 6
        if (startWord >= wlen) return

        // since endIndex is one past the end, this is the index of the last word to be changed.
        val endWord = (endIndex - 1) shr 6

        // invert masks since we are clearing
        val startmask = (-1L shl startIndex).inv()
        val endmask = (-1L ushr -endIndex).inv()

        if (startWord == endWord) {
            bits[startWord] = bits[startWord] and (startmask or endmask)
            return
        }

        bits[startWord] = bits[startWord] and startmask

        val middle = minOf(wlen, endWord)
        bits.fill(0L, startWord + 1, middle)
        if (endWord < wlen) {
            bits[endWord] = bits[endWord] and endmask
        }
    }

    /**
     * Clears a range of bits. Clearing past the end does not change the size of the set.
     *
     * @param startIndex lower index
     * @param endIndex one-past the last bit to clear
     */
    fun clear(startIndex: Long, endIndex: Long) {
        if (endIndex <= startIndex) return

        val startWord = (startIndex shr 6).toInt()
        if (startWord >= wlen) return

        val endWord = ((endIndex - 1) shr 6).toInt()

        val startmask = (-1L shl startIndex.toInt()).inv()
        val endmask = (-1L ushr (-endIndex).toInt()).inv()

        if (startWord == endWord) {
            bits[startWord] = bits[startWord] and (startmask or endmask)
            return
        }

        bits[startWord] = bits[startWord] and startmask

        val middle = minOf(wlen, endWord)
        bits.fill(0L, startWord + 1, middle)
        if (endWord < wlen) {
            bits[endWord] = bits[endWord] and endmask
        }
    }

    /**
     * Sets a bit and returns the previous value. The [index] should be less than the bit set size.
     */
    fun getAndSet(index: Int): Boolean {
        val wordNum = index shr 6
        val bit = index and 0x3f
        val bitmask = 1L shl bit
        val v = (bits[wordNum] and bitmask) != 0L
        bits[wordNum] = bits[wordNum] or bitmask
        return v
    }

    /**
     * Sets a bit and returns the previous value. The [index] should be less than the bit set size.
     */
    fun getAndSet(index: Long): Boolean {
        val wordNum = (index shr 6).toInt()
        val bit = index.toInt() and 0x3f
        val bitmask = 1L shl bit
        val v = (bits[wordNum] and bitmask) != 0L
        bits[wordNum] = bits[wordNum] or bitmask
        return v
    }

    /** Flips a bit, expanding the set size if necessary. */
    fun flip(index: Long) {
        val wordNum = expandingWordNum(index)
        val bit = index.toInt() and 0x3f
        val bitmask = 1L shl bit
        bits[wordNum] = bits[wordNum] xor bitmask
    }

    /**
     * Flips a bit and returns the resulting bit value. The [index] should be less than the bit
     * set size.
     */
    fun flipAndGet(index: Int): Boolean {
        val wordNum = index shr 6
        val bit = index and 0x3f
        val bitmask = 1L shl bit
        bits[wordNum] = bits[wordNum] xor bitmask
        return (bits[wordNum] and bitmask) != 0L
    }

    /**
     * Flips a bit and returns the resulting bit value. The [index] should be less than the bit
     * set size.
     */
    fun flipAndGet(index: Long): Boolean {
        val wordNum = (index shr 6).toInt()
        val bit = index.toInt() and 0x3f
        val bitmask = 1L shl bit
        bits[wordNum] = bits[wordNum] xor bitmask
        return (bits[wordNum] and bitmask) != 0L
    }

    /**
     * Flips a range of bits, expanding the set size if necessary.
     *
     * @param startIndex lower index
     * @param endIndex one-past the last bit to flip
     */
    fun flip(startIndex: Long, endIndex: Long) {
        if (endIndex <= startIndex) return
        val startWord = (startIndex shr 6).toInt()
        val endWord = expandingWordNum(endIndex - 1)

        val startmask = -1L shl startIndex.toInt()
        val endmask = -1L ushr (-endIndex).toInt()

        if (startWord == endWord) {
            bits[startWord] = bits[startWord] xor (startmask and endmask)
            return
        }

        bits[startWord] = bits[startWord] xor startmask
        for (i in startWord + 1 until endWord) {
            bits[i] = bits[i].inv()
        }
        bits[endWord] = bits[endWord] xor endmask
    }

    /** The number of set bits. */
    fun cardinality(): Long {
        var popCount = 0L
        for (i in 0 until wlen) {
            popCount += bits[i].countOneBits()
        }
        return popCount
    }

    /**
     * Returns the index of the first set bit starting at [index], inclusive. -1 is returned if
     * there are no more set bits.
     */
    fun nextSetBit(index: Int): Int {
        var i = index shr 6
        if (i >= wlen) return -1
        val subIndex = index and 0x3f // index within the word
        var word = bits[i] shr subIndex // skip all the bits to the right of index

        if (word != 0L) {
            return (i shl 6) + subIndex + word.countTrailingZeroBits()
        }

        while (++i < wlen) {
            word = bits[i]
            if (word != 0L) return (i shl 6) + word.countTrailingZeroBits()
        }

        return -1
    }

    /**
     * Returns the index of the first set bit starting at [index], inclusive. -1 is returned if
     * there are no more set bits.
     */
    fun nextSetBit(index: Long): Long {
        var i = (index ushr 6).toInt()
        if (i >= wlen) return -1
        val subIndex = index.toInt() and 0x3f
        var word = bits[i] ushr subIndex

        if (word != 0L) {
            return (i.toLong() shl 6) + (subIndex + word.countTrailingZeroBits())
        }

        while (++i < wlen) {
            word = bits[i]
            if (word != 0L) return (i.toLong() shl 6) + word.countTrailingZeroBits()
        }

        return -1
    }

    /** this = this AND other */
    fun intersect(other: GrowableBitSet) {
        val newLen = minOf(this.wlen, other.wlen)
        val thisArr = this.bits
        val otherArr = other.bits
        // testing against zero can be more efficient
        var pos = newLen
        while (--pos >= 0) {
            thisArr[pos] = thisArr[pos] and otherArr[pos]
        }
        if (this.wlen > newLen) {
            // fill zeros from the new shorter length to the old length
            bits.fill(0L, newLen, this.wlen)
        }
        this.wlen = newLen
    }

    /** this = this OR other */
    fun union(other: GrowableBitSet) {
        val newLen = maxOf(wlen, other.wlen)
        ensureCapacityWords(newLen)

        val thisArr = this.bits
        val otherArr = other.bits
        var pos = minOf(wlen, other.wlen)
        while (--pos >= 0) {
            thisArr[pos] = thisArr[pos] or otherArr[pos]
        }
        if (this.wlen < newLen) {
            otherArr.copyInto(thisArr, this.wlen, this.wlen, newLen)
        }
        this.wlen = newLen
    }

    /** Remove all elements set in other: this = this AND_NOT other */
    fun remove(other: GrowableBitSet) {
        var idx = minOf(wlen, other.wlen)
        val thisArr = this.bits
        val otherArr = other.bits
        while (--idx >= 0) {
            thisArr[idx] = thisArr[idx] and otherArr[idx].inv()
        }
    }

    /** this = this XOR other */
    fun xor(other: GrowableBitSet) {
        val newLen = maxOf(wlen, other.wlen)
        ensureCapacityWords(newLen)

        val thisArr = this.bits
        val otherArr = other.bits
        var pos = minOf(wlen, other.wlen)
        while (--pos >= 0) {
            thisArr[pos] = thisArr[pos] xor otherArr[pos]
        }
        if (this.wlen < newLen) {
            otherArr.copyInto(thisArr, this.wlen, this.wlen, newLen)
        }
        this.wlen = newLen
    }

    // some java.util.BitSet compatibility methods

    /** @see intersect */
    fun and(other: GrowableBitSet) = intersect(other)

    /** @see union */
    fun or(other: GrowableBitSet) = union(other)

    /** @see remove */
    fun andNot(other: GrowableBitSet) = remove(other)

    /** Returns true if the sets have any elements in common. */
    fun intersects(other: GrowableBitSet): Boolean {
        var pos = minOf(this.wlen, other.wlen)
        val thisArr = this.bits
        val otherArr = other.bits
        while (--pos >= 0) {
            if ((thisArr[pos] and otherArr[pos]) != 0L) return true
        }
        return false
    }

    /**
     * Expand the LongArray with the size given as a number of words (64 bit longs).
     * [wlen] is unchanged by this call.
     */
    fun ensureCapacityWords(numWords: Int) {
        if (bits.size < numWords) {
            bits = grow(bits, numWords)
        }
    }

    /**
     * Ensure that the LongArray is big enough to hold [numBits], expanding it if necessary.
     * [wlen] is unchanged by this call.
     */
    fun ensureCapacity(numBits: Long) {
        ensureCapacityWords(bits2words(numBits))
    }

    /** Lowers [wlen], the number of words in use, by checking for trailing zero words. */
    fun trimTrailingZeros() {
        var idx = wlen - 1
        while (idx >= 0 && bits[idx] == 0L) idx--
        wlen = idx + 1
    }

    /** Returns true if both sets have the same bits set. */
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is GrowableBitSet) return false

        // make a the larger set.
        val a: GrowableBitSet
        val b: GrowableBitSet
        if (other.wlen > this.wlen) {
            a = other
            b = this
        } else {
            a = this
            b = other
        }

        // check for any set bits out of the range of b
        for (i in a.wlen - 1 downTo b.wlen) {
            if (a.bits[i] != 0L) return false
        }

        for (i in b.wlen - 1 downTo 0) {
            if (a.bits[i] != b.bits[i]) return false
        }

        return true
    }

    override fun hashCode(): Int {
        // Start with a zero hash and use a mix that results in zero if the input is zero.
        // This effectively truncates trailing zeros without an explicit check.
        var h = 0L
        for (i in bits.size - 1 downTo 0) {
            h = h xor bits[i]
            h = (h shl 1) or (h ushr 63) // rotate left
        }
        // fold leftmost bits into right and add a constant to prevent
        // empty sets from returning 0, which is too common.
        return ((h shr 32) xor h).toInt() + 0x98761234.toInt()
    }

    override fun toString(): String {
        var bit = nextSetBit(0L)
        if (bit < 0) return "{}"

        val builder = StringBuilder()
        builder.append("{")
        builder.append(bit)
        while (true) {
            bit = nextSetBit(bit + 1)
            if (bit < 0) break
            builder.append(", ")
            builder.append(bit)
        }
        builder.append("}")
        return builder.toString()
    }

    companion object {
        /** The initial default number of bits. */
        private const val DEFAULT_NUM_BITS = 64L

        @JvmStatic
        fun grow(array: LongArray, minSize: Int): LongArray {
            if (array.size < minSize) {
                val newArray = LongArray(getNextSize(minSize))
                array.copyInto(newArray, 0, 0, array.size)
                return newArray
            }
            return array
        }

        /**
         * Over-allocates proportional to the target size, making room for additional growth.
         * The growth pattern is: 0, 4, 8, 16, 25, 35, 46, 58, 72, 88, ...
         */
        @JvmStatic
        fun getNextSize(targetSize: Int): Int =
            (targetSize shr 3) + (if (targetSize < 9) 3 else 6) + targetSize

        /** Returns the number of 64 bit words it would take to hold [numBits]. */
        @JvmStatic
        fun bits2words(numBits: Long): Int = (((numBits - 1) ushr 6) + 1).toInt()
    }
}

/**
 * An iterator over the set bits of a [GrowableBitSet], mirroring the usage pattern of HPPC's
 * `BitSetIterator`: `for (int i = it.nextSetBit(); i >= 0; i = it.nextSetBit())`. It yields
 * exactly the ascending sequence of set-bit indices in `[0, numWords * 64)`.
 */
class GrowableBitSetIterator(private val arr: LongArray, private val words: Int) {

    constructor(bitSet: GrowableBitSet) : this(bitSet.bits, bitSet.wlen)

    private var i = -1
    private var word = 0L

    /** Returns the next set bit, or [NO_MORE] if the iteration is exhausted. */
    fun nextSetBit(): Int {
        while (word == 0L) {
            if (++i >= words) return NO_MORE
            word = arr[i]
        }
        val bitIndex = word.countTrailingZeroBits()
        word = word and (word - 1) // clear the lowest set bit
        return (i shl 6) + bitIndex
    }

    companion object {
        const val NO_MORE = -1
    }
}
