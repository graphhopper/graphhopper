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
package com.graphhopper.routing.profiles;

import com.graphhopper.storage.IntsRef;

import java.util.Objects;

/**
 * This class holds a decimal value and stores it as an unsigned integer value via a conversion factor and a maximum number
 * of bits.
 */
public final class UnsignedDecimalEncodedValue extends UnsignedIntEncodedValue implements DecimalEncodedValue {
    private final double factor;
    private final double defaultValue;

    public UnsignedDecimalEncodedValue(String name, int bits, double factor, boolean storeTwoDirections) {
        this(name, bits, factor, 0, storeTwoDirections);
    }

    /**
     * @param name               the key to identify this EncodedValue
     * @param bits               the bits that should be reserved for the storage
     * @param factor             the precision factor, i.e. store = (int) Math.round(value / factor)
     * @param defaultValue       the value that should be returned if the stored value is 0.
     * @param storeTwoDirections true if forward and backward direction of the edge should get two independent values.
     */
    public UnsignedDecimalEncodedValue(String name, int bits, double factor, double defaultValue, boolean storeTwoDirections) {
        super(name, bits, storeTwoDirections);
        this.factor = factor;
        this.defaultValue = defaultValue;
    }

    private int toInt(double val) {
        return (int) Math.round(val / factor);
    }

    @Override
    public final void setDecimal(boolean reverse, IntsRef ints, double value) {
        if (!isInitialized())
            throw new IllegalStateException("Call init before usage for EncodedValue " + toString());
        if (value == defaultValue)
            value = 0;
        if (value > maxValue * factor)
            throw new IllegalArgumentException(getName() + " value " + value + " too large for encoding. maxValue:" + maxValue * factor);
        if (value < 0)
            throw new IllegalArgumentException("Negative value for " + getName() + " not allowed! " + value);
        if (Double.isNaN(value))
            throw new IllegalArgumentException("NaN value for " + getName() + " not allowed!");

        super.setInt(reverse, ints, toInt(value));
    }

    @Override
    public final double getDecimal(boolean reverse, IntsRef ref) {
        int value = getInt(reverse, ref);
        if (value == 0)
            return defaultValue;
        return value * factor;
    }

    @Override
    public double getMaxDecimal() {
        return maxValue * factor;
    }

    @Override
    public boolean equals(Object o) {
        if (!super.equals(o)) return false;
        UnsignedDecimalEncodedValue that = (UnsignedDecimalEncodedValue) o;
        return Double.compare(that.factor, factor) == 0;
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), factor);
    }

    @Override
    public int getVersion() {
        return hashCode();
    }
}
