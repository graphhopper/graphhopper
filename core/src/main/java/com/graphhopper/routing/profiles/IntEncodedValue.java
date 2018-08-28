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

/**
 * This class defines where to store an unsigned integer. It is important to note that: 1. the range of the integer is
 * highly limited (unlike the Java 32bit integer values) so that the storeable part of it fits into the
 * specified number of bits (using the internal shift value) and 2. the 'raw' default value is always 0.
 * <p>
 * To illustrate why the default is always 0 and how you can still use other defaults imagine the storage engine
 * creates a new entry. Either the engine knows the higher level logic or we assume the default value is 0 and
 * map this value to the real value on every retrieval request.
 * <p>
 * How could you then implement e.g. a 'priority' value going from [-3, 3] that maps to [0,7] but should
 * have a default value of 3 instead of 0? Either you waste space and map this to [1,7], which means that 0 and 3 both
 * refer to the same 0 value (currently the preferred method due to its simplicity) or you could create a
 * MappedIntEncodedValue class that holds an array or a Map with the raw integers similarly to what StringEncodedValue does:
 * {0: 0, 1: -3, 2: -2, 3: -1, 4: 1, 5: 2, 6: 3}
 */
public class IntEncodedValue implements EncodedValue {

    private final String name;
    private final String classType;

    /**
     * There are multiple int values possible per edge. Here we specify the index into this integer array.
     */
    protected int fwdDataIndex;
    protected int bwdDataIndex;
    final int bits;
    // we need a long here as Java ints are signed
    long maxValue;
    int fwdShift = -1;
    int bwdShift = -1;
    int fwdMask;
    int bwdMask;
    int defaultValue;
    boolean storeBothDirections;

    IntEncodedValue() {
        bits = 0;
        name = "";
        classType = getClass().getName();
    }

    public IntEncodedValue(String name, int bits) {
        this(name, bits, 0, false);
    }

    /**
     * This constructor reserves the specified number of bits in the underlying data structure or twice the amount if
     * store2DirectedValues is true.
     *
     * @param defaultValue        defines which value to return if the 'raw' integer value is 0.
     * @param storeBothDirections if true the encoded value can be different for the forward and backward
     *                            direction of an edge.
     */
    public IntEncodedValue(String name, int bits, int defaultValue, boolean storeBothDirections) {
        if (!name.toLowerCase(Locale.ROOT).equals(name))
            throw new IllegalArgumentException("EncodedValue name must be lower case but was " + name);
        if (bits <= 0)
            throw new IllegalArgumentException(name + ": bits cannot be zero or negative");
        if (bits > 32)
            throw new IllegalArgumentException(name + ": at the moment bits cannot be >32");
        this.bits = bits;
        this.name = name;
        this.classType = getClass().getSimpleName();
        this.defaultValue = defaultValue;
        this.storeBothDirections = storeBothDirections;
    }

    @Override
    public final int init(EncodedValue.InitializerConfig init) {
        if (isInitialized())
            throw new IllegalStateException("Cannot call init multiple times");

        init.next(bits);
        this.fwdMask = init.bitMask;
        this.fwdDataIndex = init.dataIndex;
        this.fwdShift = init.shift;
        if (storeBothDirections) {
            init.next(bits);
            this.bwdMask = init.bitMask;
            this.bwdDataIndex = init.dataIndex;
            this.bwdShift = init.shift;
        }

        this.maxValue = (1L << bits) - 1;
        return storeBothDirections ? 2 * bits : bits;
    }

    private boolean isInitialized() {
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

    /**
     * This method stores the specified integer value in the specified IntsRef.
     */
    public final void setInt(boolean reverse, IntsRef ref, int value) {
        checkValue(value);
        uncheckedSet(reverse, ref, value);
    }

    final void uncheckedSet(boolean reverse, IntsRef ref, int value) {
        if (storeBothDirections && reverse) {
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

    /**
     * This method restores the integer value from the specified 'flags' taken from the storage.
     */
    public final int getInt(boolean reverse, IntsRef ref) {
        int flags;
        if (reverse && storeBothDirections) {
            flags = ref.ints[bwdDataIndex + ref.offset];
            flags &= bwdMask;
            flags >>>= bwdShift;
        } else {
            flags = ref.ints[fwdDataIndex + ref.offset];
            flags &= fwdMask;
            flags >>>= fwdShift;
        }
        // return the integer value
        if (flags == 0)
            return defaultValue;
        return flags;
    }

    @Override
    public final String getName() {
        return name;
    }

    @Override
    public final String toString() {
        return getName() + "|bits=" + bits + "|fwd_shift=" + fwdShift + "|store_both_directions=" + storeBothDirections;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        IntEncodedValue that = (IntEncodedValue) o;

        if (fwdDataIndex != that.fwdDataIndex) return false;
        if (bwdDataIndex != that.bwdDataIndex) return false;
        if (bits != that.bits) return false;
        if (fwdShift != that.fwdShift) return false;
        if (bwdShift != that.bwdShift) return false;
        if (defaultValue != that.defaultValue) return false;
        if (storeBothDirections != that.storeBothDirections) return false;
        if (!name.equals(that.name)) return false;
        return classType.equals(that.classType);
    }

    @Override
    public int hashCode() {
        int result = name.hashCode();
        result = 31 * result + classType.hashCode();
        result = 31 * result + fwdDataIndex;
        result = 31 * result + bwdDataIndex;
        result = 31 * result + bits;
        result = 31 * result + fwdShift;
        result = 31 * result + bwdShift;
        result = 31 * result + defaultValue;
        result = 31 * result + (storeBothDirections ? 1 : 0);
        return result;
    }
}
