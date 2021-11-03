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

import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.storage.IntsRef;
import com.graphhopper.util.Helper;

import java.util.Objects;

import static com.graphhopper.routing.ev.UnsignedIntEncodedValue.staticHashCode;

/**
 * Implementation of the IntEncodedValue via a limited number of bits. The sign of the value depends on the direction
 * of the edge.
 */
public class SymmetricIntEncodedValue implements IntEncodedValue {
    private final String name;
    private int dataIndex;
    private final int bits;
    private final int factor;
    private int maxValue;
    private int shift = -1;
    private int mask;

    public SymmetricIntEncodedValue(String name, int bits, int factor) {
        if (!EncodingManager.isValidEncodedValue(name))
            throw new IllegalArgumentException("EncodedValue name wasn't valid: " + name + ". Use lower case letters, underscore and numbers only.");
        if (bits <= 1)
            throw new IllegalArgumentException(name + ": bits cannot be one or less");
        if (bits > 31)
            throw new IllegalArgumentException(name + ": at the moment the number of reserved bits cannot be more than 31");
        if (factor < 1)
            throw new IllegalArgumentException(name + ": factor must be >= 1");
        this.name = name;
        this.bits = bits;
        this.factor = factor;
    }

    @Override
    public final int init(EncodedValue.InitializerConfig init) {
        if (isInitialized())
            throw new IllegalStateException("Cannot call init multiple times");

        init.next(bits);
        this.mask = init.bitMask;
        this.dataIndex = init.dataIndex;
        this.shift = init.shift;
        // we need one bit for the direction
        this.maxValue = ((1 << bits - 1) - 1) * factor;
        return bits;
    }

    boolean isInitialized() {
        return mask != 0;
    }

    @Override
    public final void setInt(boolean reverse, IntsRef ref, int value) {
        checkValue(value);
        uncheckedSet(reverse, ref, value);
    }

    private void checkValue(int value) {
        if (!isInitialized())
            throw new IllegalStateException("EncodedValue " + getName() + " not initialized");
        if (value > maxValue)
            throw new IllegalArgumentException(name + " value too large for encoding: " + value + ", maxValue: " + maxValue);
        if (value < -maxValue)
            throw new IllegalArgumentException(name + " value too small for encoding: " + value + ", minValue: " + -maxValue);
    }

    final void uncheckedSet(boolean reverse, IntsRef ref, int value) {
        value = value / factor;
        boolean directionFlag = value >= 0 == !reverse;
        if (value < 0)
            value = -value;
        value = value << 1;
        if (directionFlag)
            value |= 1;
        int flags = ref.ints[dataIndex + ref.offset];
        flags &= ~mask;
        ref.ints[dataIndex + ref.offset] = flags | (value << shift);
    }

    @Override
    public final int getInt(boolean reverse, IntsRef ref) {
        int flags = ref.ints[dataIndex + ref.offset];
        int val = (flags & mask) >>> shift;
        boolean directionFlag = (val & 1) == 1;
        val = val >> 1;
        return (directionFlag == !reverse ? val : -val) * factor;
    }

    @Override
    public int getMaxInt() {
        return maxValue;
    }

    @Override
    public final boolean isStoreTwoDirections() {
        return false;
    }

    @Override
    public final String getName() {
        return name;
    }

    @Override
    public final String toString() {
        return getName() + "|version=" + getVersion() + "|bits=" + bits + "|factor=" + factor + "|index=" + dataIndex + "|shift=" + shift;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SymmetricIntEncodedValue that = (SymmetricIntEncodedValue) o;
        return dataIndex == that.dataIndex &&
                bits == that.bits &&
                maxValue == that.maxValue &&
                shift == that.shift &&
                mask == that.mask &&
                factor == that.factor &&
                Objects.equals(name, that.name);
    }

    @Override
    public final int hashCode() {
        return getVersion();
    }

    @Override
    public int getVersion() {
        int val = Helper.staticHashCode(name);
        val = 31 * val + 1231;
        return staticHashCode(val, dataIndex, bits, maxValue, shift, mask, factor);
    }
}
