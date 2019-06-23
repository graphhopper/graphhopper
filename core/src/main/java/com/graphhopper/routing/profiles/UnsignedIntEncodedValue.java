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

import java.util.Locale;
import java.util.Objects;

/**
 * Implementation of the IntEncodedValue via a limited number of bits and without a sign. It introduces handling
 * of "backward"- and "forward"-edge information.
 */
public class UnsignedIntEncodedValue implements IntEncodedValue {

    private final String name;

    /**
     * There are multiple int values possible per edge. Here we specify the index into this integer array.
     */
    protected int fwdDataIndex;
    protected int bwdDataIndex;
    final int bits;
    int maxValue;
    int fwdShift = -1;
    int bwdShift = -1;
    int fwdMask;
    int bwdMask;
    boolean storeTwoDirections;

    /**
     * This constructor reserves the specified number of bits in the underlying data structure or twice the amount if
     * storeTwoDirections is true.
     *
     * @param storeTwoDirections if true this EncodedValue can store different values for the forward and backward
     *                           direction.
     */
    public UnsignedIntEncodedValue(String name, int bits, boolean storeTwoDirections) {
        if (!name.toLowerCase(Locale.ROOT).equals(name))
            throw new IllegalArgumentException("EncodedValue name must be lower case but was " + name);
        if (bits <= 0)
            throw new IllegalArgumentException(name + ": bits cannot be zero or negative");
        if (bits > 31)
            throw new IllegalArgumentException(name + ": at the moment the number of reserved bits cannot be more than 31");
        this.bits = bits;
        this.name = name;
        this.storeTwoDirections = storeTwoDirections;
    }

    @Override
    public final int init(EncodedValue.InitializerConfig init) {
        if (isInitialized())
            throw new IllegalStateException("Cannot call init multiple times");

        init.next(bits);
        this.fwdMask = init.bitMask;
        this.fwdDataIndex = init.dataIndex;
        this.fwdShift = init.shift;
        if (storeTwoDirections) {
            init.next(bits);
            this.bwdMask = init.bitMask;
            this.bwdDataIndex = init.dataIndex;
            this.bwdShift = init.shift;
        }

        this.maxValue = (1 << bits) - 1;
        return storeTwoDirections ? 2 * bits : bits;
    }

    boolean isInitialized() {
        return fwdMask != 0;
    }

    private void checkValue(int value) {
        if (!isInitialized())
            throw new IllegalStateException("EncodedValue " + getName() + " not initialized");
        if (value > maxValue)
            throw new IllegalArgumentException(name + " value too large for encoding: " + value + ", maxValue:" + maxValue);
        if (value < 0)
            throw new IllegalArgumentException("negative value for " + name + " not allowed! " + value);
    }

    @Override
    public final void setInt(boolean reverse, IntsRef ref, int value) {
        checkValue(value);
        uncheckedSet(reverse, ref, value);
    }

    final void uncheckedSet(boolean reverse, IntsRef ref, int value) {
        if (reverse && !storeTwoDirections)
            throw new IllegalArgumentException(getName() + ": value for reverse direction would overwrite forward direction. Enable storeTwoDirections for this EncodedValue or don't use setReverse");

        if (reverse) {
            int flags = ref.ints[bwdDataIndex + ref.offset];
            // clear value bits
            flags &= ~bwdMask;
            value <<= bwdShift;
            // set value
            ref.ints[bwdDataIndex + ref.offset] = flags | value;
        } else {
            int flags = ref.ints[fwdDataIndex + ref.offset];
            flags &= ~fwdMask;
            value <<= fwdShift;
            ref.ints[fwdDataIndex + ref.offset] = flags | value;
        }
    }

    @Override
    public final int getInt(boolean reverse, IntsRef ref) {
        int flags;
        // if we do not store both directions ignore reverse == true for convenient reading
        if (reverse && storeTwoDirections) {
            flags = ref.ints[bwdDataIndex + ref.offset];
            return (flags & bwdMask) >>> bwdShift;
        } else {
            flags = ref.ints[fwdDataIndex + ref.offset];
            return (flags & fwdMask) >>> fwdShift;
        }
    }

    @Override
    public int getMaxInt() {
        return maxValue;
    }

    @Override
    public final boolean isStoreTwoDirections() {
        return storeTwoDirections;
    }

    @Override
    public final String getName() {
        return name;
    }

    @Override
    public final String toString() {
        return getName() + "|version=" + getVersion() + "|bits=" + bits + "|index=" + fwdDataIndex + "|shift=" + fwdShift + "|store_both_directions=" + storeTwoDirections;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        UnsignedIntEncodedValue that = (UnsignedIntEncodedValue) o;
        return fwdDataIndex == that.fwdDataIndex &&
                bwdDataIndex == that.bwdDataIndex &&
                bits == that.bits &&
                maxValue == that.maxValue &&
                fwdShift == that.fwdShift &&
                bwdShift == that.bwdShift &&
                fwdMask == that.fwdMask &&
                bwdMask == that.bwdMask &&
                storeTwoDirections == that.storeTwoDirections &&
                Objects.equals(name, that.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, fwdDataIndex, bwdDataIndex, bits, maxValue, fwdShift, bwdShift, fwdMask, bwdMask, storeTwoDirections);
    }

    @Override
    public int getVersion() {
        return hashCode();
    }
}
