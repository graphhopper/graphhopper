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
import com.graphhopper.storage.IntsRef;

/**
 * This class holds a signed decimal value and stores it as an integer value via a conversion factor and a certain
 * number of bits that determine the maximum value.
 */
public final class DecimalEncodedValueImpl extends IntEncodedValueImpl implements DecimalEncodedValue {
    private final double factor;
    private final boolean defaultIsInfinity;
    private final boolean useMaximumAsInfinity;

    /**
     * @see #DecimalEncodedValueImpl(String, int, double, double, boolean, boolean, boolean, boolean)
     */
    public DecimalEncodedValueImpl(String name, int bits, double factor, boolean storeTwoDirections) {
        this(name, bits, factor, false, storeTwoDirections);
    }

    /**
     * @see #DecimalEncodedValueImpl(String, int, double, double, boolean, boolean, boolean, boolean)
     */
    public DecimalEncodedValueImpl(String name, int bits, double factor, boolean defaultIsInfinity, boolean storeTwoDirections) {
        this(name, bits, 0, factor, defaultIsInfinity, false, storeTwoDirections, false);
    }

    /**
     * @param name                   the key to identify this EncodedValue
     * @param bits                   the bits that should be reserved for storing the integer value. This determines the
     *                               maximum value.
     * @param minValue               the minimum value. Use e.g. 0 if no negative values are needed.
     * @param factor                 the precision factor, i.e. store = (int) Math.round(value / factor)
     * @param defaultIsInfinity      true if default should be Double.Infinity. False if 0 should be default.
     * @param negateReverseDirection true if the reverse direction should be always negative of the forward direction.
     *                               This is used to reduce space and store the value only once.
     * @param storeTwoDirections     true if forward and backward direction of the edge should get two independent values.
     * @param useMaximumAsInfinity   true if the maximum value should be treated as Double.Infinity
     */
    public DecimalEncodedValueImpl(String name, int bits, double minValue, double factor, boolean defaultIsInfinity,
                                   boolean negateReverseDirection, boolean storeTwoDirections, boolean useMaximumAsInfinity) {
        super(name, bits, (int) Math.round(minValue / factor), negateReverseDirection, storeTwoDirections);
        if (!negateReverseDirection && super.minValue * factor != minValue)
            throw new IllegalArgumentException("minValue " + minValue + " is not a multiple of the specified factor " + factor);
        this.factor = factor;
        this.defaultIsInfinity = defaultIsInfinity;
        this.useMaximumAsInfinity = useMaximumAsInfinity;
        if (useMaximumAsInfinity && defaultIsInfinity)
            throw new IllegalArgumentException("defaultIsInfinity and useMaximumAsInfinity cannot be both true");
        if (defaultIsInfinity && minValue < 0)
            throw new IllegalArgumentException("defaultIsInfinity cannot be true when minValue is negative");
    }

    @JsonCreator(mode = JsonCreator.Mode.PROPERTIES)
    DecimalEncodedValueImpl(@JsonProperty("name") String name,
                            @JsonProperty("bits") int bits,
                            @JsonProperty("min_value") int minValue,
                            @JsonProperty("max_value") int maxValue,
                            @JsonProperty("negate_reverse_direction") boolean negateReverseDirection,
                            @JsonProperty("store_two_directions") boolean storeTwoDirections,
                            @JsonProperty("factor") double factor,
                            @JsonProperty("default_is_infinity") boolean defaultIsInfinity,
                            @JsonProperty("use_maximum_as_infinity") boolean useMaximumAsInfinity) {
        // we need this constructor for Jackson
        super(name, bits, minValue, maxValue, negateReverseDirection, storeTwoDirections);
        this.factor = factor;
        this.defaultIsInfinity = defaultIsInfinity;
        this.useMaximumAsInfinity = useMaximumAsInfinity;
    }

    @Override
    public void setDecimal(boolean reverse, IntsRef ref, double value) {
        if (!isInitialized())
            throw new IllegalStateException("Call init before usage for EncodedValue " + toString());
        if (Double.isInfinite(value)) {
            if (useMaximumAsInfinity) {
                super.setInt(reverse, ref, maxValue);
                return;
            } else if (defaultIsInfinity) {
                super.setInt(reverse, ref, 0);
                return;
            }
            throw new IllegalArgumentException("Value cannot be infinite if useMaximumAsInfinity is false");
        }
        if (Double.isNaN(value))
            throw new IllegalArgumentException("NaN value for " + getName() + " not allowed!");

        value /= factor;
        if (value > maxValue)
            throw new IllegalArgumentException(getName() + " value too large for encoding: " + value + ", maxValue:" + maxValue + ", factor: " + factor);
        if (value < minValue)
            throw new IllegalArgumentException(getName() + " value too small for encoding " + value + ", minValue:" + minValue + ", factor: " + factor);

        super.uncheckedSet(reverse, ref, (int) Math.round(value));
    }

    @Override
    public double getDecimal(boolean reverse, IntsRef ref) {
        int value = getInt(reverse, ref);
        if (useMaximumAsInfinity && value == maxValue || defaultIsInfinity && value == 0)
            return Double.POSITIVE_INFINITY;
        return value * factor;
    }

    @Override
    public double getNextStorableValue(double value) {
        if (!useMaximumAsInfinity && value > getMaxDecimal())
            throw new IllegalArgumentException(getName() + ": There is no next storable value for " + value + ". max:" + getMaxDecimal());
        else if (useMaximumAsInfinity && value > getMaxDecimal())
            return Double.POSITIVE_INFINITY;
        else
            return (factor * (int) Math.ceil(value / factor));
    }

    @Override
    public double getMaxDecimal() {
        return maxValue * factor;
    }

    @Override
    public boolean equals(Object o) {
        if (!super.equals(o)) return false;
        DecimalEncodedValueImpl that = (DecimalEncodedValueImpl) o;
        return Double.compare(that.factor, factor) == 0 && useMaximumAsInfinity == that.useMaximumAsInfinity
                && defaultIsInfinity == that.defaultIsInfinity;
    }

    @Override
    public int getVersion() {
        int version = 31 * super.getVersion() + staticHashCode(factor);
        if (useMaximumAsInfinity) return 31 * version + 13;
        if (defaultIsInfinity) return 31 * version + 17;
        return version;
    }
}
