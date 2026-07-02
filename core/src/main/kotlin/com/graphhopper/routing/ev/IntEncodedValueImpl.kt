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
package com.graphhopper.routing.ev

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty
import javax.lang.model.SourceVersion

/**
 * Implementation of the IntEncodedValue via a certain number of bits (that determines the maximum value) and
 * a minimum value (default is 0).
 * With storeTwoDirections = true it can store separate values for forward and reverse edge direction e.g. for one speed
 * value per direction of an edge.
 * With negateReverseDirection = true it supports negating the value for the reverse direction without storing a separate
 * value e.g. to store an elevation slope which is negative for the reverse direction but has otherwise the same value
 * and is used to save storage space.
 */
open class IntEncodedValueImpl : IntEncodedValue {
    // the JSON of this class is stored in the graph and the field names (snake_cased by the serializer)
    // are part of the storage format — do not rename any field!
    final override val name: String
    private val storeTwoDirections: Boolean

    @JvmField
    internal val bits: Int

    @JvmField
    internal val negateReverseDirection: Boolean

    @JvmField
    internal val minStorableValue: Int

    @JvmField
    internal val maxStorableValue: Int

    @JvmField
    internal var maxValue: Int

    /**
     * There are multiple int values possible per edge. Here we specify the index into this integer array.
     */
    private var fwdDataIndex = 0
    private var bwdDataIndex = 0

    @JvmField
    internal var fwdShift = -1

    @JvmField
    internal var bwdShift = -1

    @JvmField
    internal var fwdMask = 0

    @JvmField
    internal var bwdMask = 0

    /**
     * @see IntEncodedValueImpl
     */
    constructor(name: String, bits: Int, storeTwoDirections: Boolean) : this(name, bits, 0, false, storeTwoDirections)

    /**
     * This creates an EncodedValue to store an integer value with up to the specified bits.
     *
     * @param name                   the key to identify this EncodedValue
     * @param bits                   the bits that should be reserved for storing the value. This determines the
     *                               maximum value.
     * @param minStorableValue       the minimum value. Use e.g. 0 if no negative values are needed.
     * @param negateReverseDirection true if the reverse direction should be always negative of the forward direction.
     *                               This is used to reduce space and store the value only once. If this option is used
     *                               you cannot use storeTwoDirections or a minValue different to 0.
     * @param storeTwoDirections     true if forward and backward direction of the edge should get two independent values.
     */
    constructor(name: String, bits: Int, minStorableValue: Int, negateReverseDirection: Boolean, storeTwoDirections: Boolean) {
        if (!isValidEncodedValue(name))
            throw IllegalArgumentException("EncodedValue name wasn't valid: $name. Use lower case letters, underscore and numbers only.")
        if (bits <= 0)
            throw IllegalArgumentException("$name: bits cannot be zero or negative")
        if (bits > 31)
            throw IllegalArgumentException("$name: at the moment the number of reserved bits cannot be more than 31")
        if (negateReverseDirection && (minStorableValue != 0 || storeTwoDirections))
            throw IllegalArgumentException("$name: negating value for reverse direction only works for minValue == 0 " +
                    "and !storeTwoDirections but was minValue=$minStorableValue, storeTwoDirections=$storeTwoDirections")
        this.name = name
        this.storeTwoDirections = storeTwoDirections
        val max = (1 shl bits) - 1
        // negateReverseDirection: store the negative value only once, but for that we need the same range as maxValue for negative values
        this.minStorableValue = if (negateReverseDirection) -max else minStorableValue
        this.maxStorableValue = max + minStorableValue
        if (minStorableValue == Int.MIN_VALUE)
            // we do not allow this because we use this value to represent maxValue = untouched, i.e. no value has been set yet
            throw IllegalArgumentException("${Int.MIN_VALUE} is not allowed for minValue")
        this.maxValue = Int.MIN_VALUE
        // negateReverseDirection: we need twice the integer range, i.e. 1 more bit
        this.bits = if (negateReverseDirection) bits + 1 else bits
        this.negateReverseDirection = negateReverseDirection
    }

    @JsonCreator(mode = JsonCreator.Mode.PROPERTIES)
    internal constructor(@JsonProperty("name") name: String,
                         @JsonProperty("bits") bits: Int,
                         @JsonProperty("min_storable_value") minStorableValue: Int,
                         @JsonProperty("max_storable_value") maxStorableValue: Int,
                         @JsonProperty("max_value") maxValue: Int,
                         @JsonProperty("negate_reverse_direction") negateReverseDirection: Boolean,
                         @JsonProperty("store_two_directions") storeTwoDirections: Boolean,
                         @JsonProperty("fwd_data_index") fwdDataIndex: Int,
                         @JsonProperty("bwd_data_index") bwdDataIndex: Int,
                         @JsonProperty("fwd_shift") fwdShift: Int,
                         @JsonProperty("bwd_shift") bwdShift: Int,
                         @JsonProperty("fwd_mask") fwdMask: Int,
                         @JsonProperty("bwd_mask") bwdMask: Int
    ) {
        // we need this constructor for Jackson
        this.name = name
        this.storeTwoDirections = storeTwoDirections
        this.bits = bits
        this.negateReverseDirection = negateReverseDirection
        this.minStorableValue = minStorableValue
        this.maxStorableValue = maxStorableValue
        this.maxValue = maxValue
        this.fwdDataIndex = fwdDataIndex
        this.bwdDataIndex = bwdDataIndex
        this.fwdShift = fwdShift
        this.bwdShift = bwdShift
        this.fwdMask = fwdMask
        this.bwdMask = bwdMask
    }

    final override fun init(init: EncodedValue.InitializerConfig): Int {
        if (isInitialized())
            throw IllegalStateException("Cannot call init multiple times")

        init.next(bits)
        this.fwdMask = init.bitMask
        this.fwdDataIndex = init.dataIndex
        this.fwdShift = init.shift
        if (storeTwoDirections) {
            init.next(bits)
            this.bwdMask = init.bitMask
            this.bwdDataIndex = init.dataIndex
            this.bwdShift = init.shift
        }

        return if (storeTwoDirections) 2 * bits else bits
    }

    internal fun isInitialized(): Boolean = fwdMask != 0

    final override fun setInt(reverse: Boolean, edgeId: Int, edgeIntAccess: EdgeIntAccess, value: Int) {
        checkValue(value)
        uncheckedSet(reverse, edgeId, edgeIntAccess, value)
    }

    private fun checkValue(value: Int) {
        if (!isInitialized())
            throw IllegalStateException("EncodedValue $name not initialized")
        if (value > maxStorableValue)
            throw IllegalArgumentException("$name value too large for encoding: $value, maxValue:$maxStorableValue")
        if (value < minStorableValue)
            throw IllegalArgumentException("$name value too small for encoding $value, minValue:$minStorableValue")
    }

    internal fun uncheckedSet(reverse: Boolean, edgeId: Int, edgeIntAccess: EdgeIntAccess, value: Int) {
        @Suppress("NAME_SHADOWING") var reverse = reverse
        @Suppress("NAME_SHADOWING") var value = value
        if (negateReverseDirection) {
            if (reverse) {
                reverse = false
                value = -value
            }
        } else if (reverse && !storeTwoDirections)
            throw IllegalArgumentException("$name: value for reverse direction would overwrite forward direction. Enable storeTwoDirections for this EncodedValue or don't use setReverse")

        maxValue = Math.max(maxValue, value)

        value -= minStorableValue
        if (reverse) {
            var flags = edgeIntAccess.getInt(edgeId, bwdDataIndex)
            // clear value bits
            flags = flags and bwdMask.inv()
            edgeIntAccess.setInt(edgeId, bwdDataIndex, flags or (value shl bwdShift))
        } else {
            var flags = edgeIntAccess.getInt(edgeId, fwdDataIndex)
            flags = flags and fwdMask.inv()
            edgeIntAccess.setInt(edgeId, fwdDataIndex, flags or (value shl fwdShift))
        }
    }

    final override fun getInt(reverse: Boolean, edgeId: Int, edgeIntAccess: EdgeIntAccess): Int {
        assert(fwdShift >= 0) { "incorrect shift $fwdShift for $name" }
        assert(bits > 0) { "incorrect bits $bits for $name" }

        val flags: Int
        // if we do not store both directions ignore reverse == true for convenient reading
        if (storeTwoDirections && reverse) {
            flags = edgeIntAccess.getInt(edgeId, bwdDataIndex)
            return minStorableValue + ((flags and bwdMask) ushr bwdShift)
        } else {
            flags = edgeIntAccess.getInt(edgeId, fwdDataIndex)
            if (negateReverseDirection && reverse)
                return -(minStorableValue + ((flags and fwdMask) ushr fwdShift))
            return minStorableValue + ((flags and fwdMask) ushr fwdShift)
        }
    }

    override val maxStorableInt: Int
        get() = maxStorableValue

    override val minStorableInt: Int
        get() = minStorableValue

    override val maxOrMaxStorableInt: Int
        get() = if (maxValue == Int.MIN_VALUE) maxStorableInt else maxValue

    final override val isStoreTwoDirections: Boolean
        get() = storeTwoDirections

    final override fun toString(): String = name

    companion object {
        @JvmStatic
        @JvmName("isValidEncodedValue")
        internal fun isValidEncodedValue(name: String): Boolean {
            if (name.length < 2 || name.startsWith("in_") || name.startsWith("backward_")
                    || !isLowerLetter(name[0]) || SourceVersion.isKeyword(name))
                return false

            var underscoreCount = 0
            for (i in 1 until name.length) {
                val c = name[i]
                if (c == '_') {
                    if (underscoreCount > 0) return false
                    underscoreCount++
                } else if (!isLowerLetter(c) && !Character.isDigit(c)) {
                    return false
                } else {
                    underscoreCount = 0
                }
            }
            return true
        }

        private fun isLowerLetter(c: Char): Boolean = c in 'a'..'z'
    }
}
