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
package com.graphhopper.routing.ev;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * This class holds a signed decimal value and stores it as an integer value via a conversion factor and a certain
 * number of bits that determine the maximum value.
 */
public final class DecimalEncodedValueImpl extends IntEncodedValueImpl implements DecimalEncodedValue {
    private final double factor;
    private final boolean useMaximumAsInfinity;

    /**
     * @see #DecimalEncodedValueImpl(String, int, double, double, boolean, boolean, boolean)
     */
    public DecimalEncodedValueImpl(String name, int bits, double factor, boolean storeTwoDirections) {
        this(name, bits, 0, factor, false, storeTwoDirections, false);
    }

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
    public DecimalEncodedValueImpl(String name, int bits, double minStorableValue, double factor,
                                   boolean negateReverseDirection, boolean storeTwoDirections, boolean useMaximumAsInfinity) {
        super(name, bits, (int) Math.round(minStorableValue / factor), negateReverseDirection, storeTwoDirections);
        if (!negateReverseDirection && super.minStorableValue * factor != minStorableValue)
            throw new IllegalArgumentException("minStorableValue " + minStorableValue + " is not a multiple of the specified factor "
                    + factor + " (" + super.minStorableValue * factor + ")");
        this.factor = factor;
        this.useMaximumAsInfinity = useMaximumAsInfinity;
    }

    @JsonCreator(mode = JsonCreator.Mode.PROPERTIES)
    DecimalEncodedValueImpl(@JsonProperty("name") String name,
                            @JsonProperty("bits") int bits,
                            @JsonProperty("min_storable_value") int minStorableValue,
                            @JsonProperty("max_storable_value") int maxStorableValue,
                            @JsonProperty("max_value") int maxValue,
                            @JsonProperty("negate_reverse_direction") boolean negateReverseDirection,
                            @JsonProperty("store_two_directions") boolean storeTwoDirections,
                            @JsonProperty("fwd_data_index") int fwdDataIndex,
                            @JsonProperty("bwd_data_index") int bwdDataIndex,
                            @JsonProperty("fwd_shift") int fwdShift,
                            @JsonProperty("bwd_shift") int bwdShift,
                            @JsonProperty("fwd_mask") int fwdMask,
                            @JsonProperty("bwd_mask") int bwdMask,
                            @JsonProperty("factor") double factor,
                            @JsonProperty("use_maximum_as_infinity") boolean useMaximumAsInfinity) {
        // we need this constructor for Jackson
        super(name, bits, minStorableValue, maxStorableValue, maxValue, negateReverseDirection, storeTwoDirections, fwdDataIndex, bwdDataIndex, fwdShift, bwdShift, fwdMask, bwdMask);
        this.factor = factor;
        this.useMaximumAsInfinity = useMaximumAsInfinity;
    }

    @Override
    public void setDecimal(boolean reverse, int edgeId, EdgeIntAccess edgeIntAccess, double value) {
        if (!isInitialized())
            throw new IllegalStateException("Call init before using EncodedValue " + getName());
        if (useMaximumAsInfinity) {
            if (Double.isInfinite(value)) {
                super.setInt(reverse, edgeId, edgeIntAccess, maxStorableValue);
                return;
            } else if (value >= maxStorableValue * factor) { // equality is important as maxStorableValue is reserved for infinity
                super.uncheckedSet(reverse, edgeId, edgeIntAccess, maxStorableValue - 1);
                return;
            }
        } else if (Double.isInfinite(value))
            throw new IllegalArgumentException("Value cannot be infinite if useMaximumAsInfinity is false");

        if (Double.isNaN(value))
            throw new IllegalArgumentException("NaN value for " + getName() + " not allowed!");

        value /= factor;
        if (value > maxStorableValue)
            throw new IllegalArgumentException(getName() + " value too large for encoding: " + value + ", maxValue:" + maxStorableValue + ", factor: " + factor);
        if (value < minStorableValue)
            throw new IllegalArgumentException(getName() + " value too small for encoding " + value + ", minValue:" + minStorableValue + ", factor: " + factor);

        super.uncheckedSet(reverse, edgeId, edgeIntAccess, (int) Math.round(value));
    }

    @Override
    public double getDecimal(boolean reverse, int edgeId, EdgeIntAccess edgeIntAccess) {
        int value = getInt(reverse, edgeId, edgeIntAccess);
        if (useMaximumAsInfinity && value == maxStorableValue)
            return Double.POSITIVE_INFINITY;
        return value * factor;
    }

    @Override
    public double getNextStorableValue(double value) {
        if (!useMaximumAsInfinity && value > getMaxStorableDecimal())
            throw new IllegalArgumentException(getName() + ": There is no next storable value for " + value + ". max:" + getMaxStorableDecimal());
        else if (useMaximumAsInfinity && value > (maxStorableValue - 1) * factor)
            return Double.POSITIVE_INFINITY;
        else
            return (factor * (int) Math.ceil(value / factor));
    }

    @Override
    public double getSmallestNonZeroValue() {
        if (minStorableValue != 0 || negateReverseDirection)
            throw new IllegalStateException("getting the smallest non-zero value is not possible if minValue!=0 or negateReverseDirection");
        return factor;
    }

    @Override
    public double getMaxStorableDecimal() {
        if (useMaximumAsInfinity) return Double.POSITIVE_INFINITY;
        return maxStorableValue * factor;
    }

    @Override
    public double getMinStorableDecimal() {
        return minStorableValue * factor;
    }

    @Override
    public double getMaxOrMaxStorableDecimal() {
        int maxOrMaxStorable = getMaxOrMaxStorableInt();
        if (useMaximumAsInfinity && maxOrMaxStorable == maxStorableValue) return Double.POSITIVE_INFINITY;
        return maxOrMaxStorable * factor;
    }
}
