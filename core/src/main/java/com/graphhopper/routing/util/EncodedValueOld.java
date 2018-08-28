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
package com.graphhopper.routing.util;

/**
 * Encapsulates a bit-encoded value.
 *
 * @author Nop
 */
public class EncodedValueOld {
    protected final long shift;
    protected final long mask;
    protected final double factor;
    protected final long defaultValue;
    private final String name;
    private final long maxValue;
    private final boolean allowZero;
    private final int bits;

    /**
     * Define a bit-encoded value
     * <p>
     *
     * @param name         Description for debugging
     * @param shift        bit index of this value
     * @param bits         number of bits reserved
     * @param factor       scaling factor for stored values
     * @param defaultValue default value
     * @param maxValue     default maximum value
     */
    public EncodedValueOld(String name, int shift, int bits, double factor, long defaultValue, int maxValue) {
        this(name, shift, bits, factor, defaultValue, maxValue, true);
    }

    public EncodedValueOld(String name, int shift, int bits, double factor, long defaultValue, int maxValue, boolean allowZero) {
        this.name = name;
        this.shift = shift;
        this.factor = factor;
        this.defaultValue = defaultValue;
        this.bits = bits;
        long tmpMask = (1L << bits) - 1;
        this.maxValue = Math.min(maxValue, Math.round(tmpMask * factor));
        if (maxValue > this.maxValue)
            throw new IllegalStateException(name + " -> maxValue " + maxValue + " is too large for " + bits + " bits");

        double factorDivision = maxValue / factor;
        if (factorDivision != (int) factorDivision) {
            throw new IllegalStateException("MaxValue needs to be divisible by factor without remainder");
        }

        mask = tmpMask << shift;
        this.allowZero = allowZero;
    }

    protected void checkValue(long value) {
        if (value > maxValue)
            throw new IllegalArgumentException(name + " value too large for encoding: " + value + ", maxValue:" + maxValue);
        if (value < 0)
            throw new IllegalArgumentException("negative " + name + " value not allowed! " + value);
        if (!allowZero && value == 0)
            throw new IllegalArgumentException("zero " + name + " value not allowed! " + value);
    }

    public long setValue(long flags, long value) {
        // scale value
        value = Math.round(value / factor);
        checkValue((long) (value * factor));
        value <<= shift;

        // clear value bits
        flags &= ~mask;

        // set value
        return flags | value;
    }

    public String getName() {
        return name;
    }

    public long getValue(long flags) {
        // find value
        flags &= mask;
        flags >>>= shift;
        return Math.round(flags * factor);
    }

    public int getBits() {
        return bits;
    }

    public double getFactor() {
        return factor;
    }

    public long setDefaultValue(long flags) {
        return setValue(flags, defaultValue);
    }
}
