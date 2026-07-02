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

/**
 * This class holds a signed decimal value and stores it as an integer value via a conversion factor and a certain
 * number of bits that determine the maximum value.
 */
class DecimalEncodedValueImpl : IntEncodedValueImpl, DecimalEncodedValue {
    // field names are part of the storage format (see IntEncodedValueImpl) — do not rename!
    private val factor: Double
    private val useMaximumAsInfinity: Boolean

    /**
     * @see DecimalEncodedValueImpl
     */
    constructor(name: String, bits: Int, factor: Double, storeTwoDirections: Boolean) :
            this(name, bits, 0.0, factor, false, storeTwoDirections, false)

    /**
     * @param name                   the key to identify this EncodedValue
     * @param bits                   the bits that should be reserved for storing the integer value. This determines the
     *                               maximum value.
     * @param minStorableValue       the minimum storable value. Use e.g. 0 if no negative values are needed.
     * @param factor                 the precision factor, i.e. store = (int) Math.round(value / factor)
     * @param negateReverseDirection true if the reverse direction should be always negative of the forward direction.
     *                               This is used to reduce space and store the value only once.
     * @param storeTwoDirections     true if forward and backward direction of the edge should get two independent values.
     * @param useMaximumAsInfinity   true if the maximum value should be treated as Double.Infinity
     */
    constructor(name: String, bits: Int, minStorableValue: Double, factor: Double,
                negateReverseDirection: Boolean, storeTwoDirections: Boolean, useMaximumAsInfinity: Boolean) :
            super(name, bits, Math.round(minStorableValue / factor).toInt(), negateReverseDirection, storeTwoDirections) {
        if (!negateReverseDirection && this.minStorableValue * factor != minStorableValue)
            throw IllegalArgumentException("minStorableValue $minStorableValue is not a multiple of the specified factor " +
                    "$factor (${this.minStorableValue * factor})")
        this.factor = factor
        this.useMaximumAsInfinity = useMaximumAsInfinity
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
                         @JsonProperty("bwd_mask") bwdMask: Int,
                         @JsonProperty("factor") factor: Double,
                         @JsonProperty("use_maximum_as_infinity") useMaximumAsInfinity: Boolean) :
            super(name, bits, minStorableValue, maxStorableValue, maxValue, negateReverseDirection, storeTwoDirections,
                    fwdDataIndex, bwdDataIndex, fwdShift, bwdShift, fwdMask, bwdMask) {
        // we need this constructor for Jackson
        this.factor = factor
        this.useMaximumAsInfinity = useMaximumAsInfinity
    }

    override fun setDecimal(reverse: Boolean, edgeId: Int, edgeIntAccess: EdgeIntAccess, value: Double) {
        @Suppress("NAME_SHADOWING") var value = value
        if (!isInitialized())
            throw IllegalStateException("Call init before using EncodedValue $name")
        if (useMaximumAsInfinity) {
            if (value.isInfinite()) {
                super.setInt(reverse, edgeId, edgeIntAccess, maxStorableValue)
                return
            } else if (value >= maxStorableValue * factor) { // equality is important as maxStorableValue is reserved for infinity
                super.uncheckedSet(reverse, edgeId, edgeIntAccess, maxStorableValue - 1)
                return
            }
        } else if (value.isInfinite())
            throw IllegalArgumentException("Value cannot be infinite if useMaximumAsInfinity is false")

        if (value.isNaN())
            throw IllegalArgumentException("NaN value for $name not allowed!")

        value /= factor
        if (value > maxStorableValue)
            throw IllegalArgumentException("$name value too large for encoding: $value, maxValue:$maxStorableValue, factor: $factor")
        if (value < minStorableValue)
            throw IllegalArgumentException("$name value too small for encoding $value, minValue:$minStorableValue, factor: $factor")

        super.uncheckedSet(reverse, edgeId, edgeIntAccess, Math.round(value).toInt())
    }

    override fun getDecimal(reverse: Boolean, edgeId: Int, edgeIntAccess: EdgeIntAccess): Double {
        val value = getInt(reverse, edgeId, edgeIntAccess)
        if (useMaximumAsInfinity && value == maxStorableValue)
            return Double.POSITIVE_INFINITY
        return value * factor
    }

    override fun getNextStorableValue(value: Double): Double {
        if (!useMaximumAsInfinity && value > maxStorableDecimal)
            throw IllegalArgumentException("$name: There is no next storable value for $value. max:$maxStorableDecimal")
        else if (useMaximumAsInfinity && value > (maxStorableValue - 1) * factor)
            return Double.POSITIVE_INFINITY
        else
            return factor * Math.ceil(value / factor).toInt()
    }

    override val smallestNonZeroValue: Double
        get() {
            if (minStorableValue != 0 || negateReverseDirection)
                throw IllegalStateException("getting the smallest non-zero value is not possible if minValue!=0 or negateReverseDirection")
            return factor
        }

    override val maxStorableDecimal: Double
        get() {
            if (useMaximumAsInfinity) return Double.POSITIVE_INFINITY
            return maxStorableValue * factor
        }

    override val minStorableDecimal: Double
        get() = minStorableValue * factor

    override val maxOrMaxStorableDecimal: Double
        get() {
            val maxOrMaxStorable = maxOrMaxStorableInt
            if (useMaximumAsInfinity && maxOrMaxStorable == maxStorableValue) return Double.POSITIVE_INFINITY
            return maxOrMaxStorable * factor
        }
}
