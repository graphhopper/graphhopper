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
package com.graphhopper.util;

import com.graphhopper.storage.IntsRef;

import java.nio.ByteOrder;

/**
 * Examples for BIG endianness (default for Java and computer network).
 * <pre>
 * 0=&gt;0100 0001
 * 1=&gt;1110 1011
 * 2=&gt;...
 * </pre> LITTLE endianness is default for GraphHopper and most microprocessors.
 *
 * @author Peter Karich
 */
public abstract class BitUtil {
    /**
     * Default for GraphHopper
     */
    public static final BitUtil LITTLE = new BitUtilLittle();
    /**
     * BIG endianness. Little is the default for GraphHopper.
     */
    public static final BitUtil BIG = new BitUtilBig();

    public static BitUtil get(ByteOrder order) {
        if (order.equals(ByteOrder.BIG_ENDIAN))
            return BitUtil.BIG;
        else
            return BitUtil.LITTLE;
    }

    public final double toDouble(byte[] bytes) {
        return toDouble(bytes, 0);
    }

    public final double toDouble(byte[] bytes, int offset) {
        return Double.longBitsToDouble(toLong(bytes, offset));
    }

    public final byte[] fromDouble(double value) {
        byte[] bytes = new byte[8];
        fromDouble(bytes, value, 0);
        return bytes;
    }

    public final void fromDouble(byte[] bytes, double value) {
        fromDouble(bytes, value, 0);
    }

    public final void fromDouble(byte[] bytes, double value, int offset) {
        fromLong(bytes, Double.doubleToRawLongBits(value), offset);
    }

    public final float toFloat(byte[] bytes) {
        return toFloat(bytes, 0);
    }

    public final float toFloat(byte[] bytes, int offset) {
        return Float.intBitsToFloat(toInt(bytes, offset));
    }

    public final byte[] fromFloat(float value) {
        byte[] bytes = new byte[4];
        fromFloat(bytes, value, 0);
        return bytes;
    }

    public final void fromFloat(byte[] bytes, float value) {
        fromFloat(bytes, value, 0);
    }

    public final void fromFloat(byte[] bytes, float value, int offset) {
        fromInt(bytes, Float.floatToRawIntBits(value), offset);
    }

    public final short toShort(byte[] b) {
        return toShort(b, 0);
    }

    public abstract short toShort(byte[] b, int offset);

    public final int toInt(byte[] b) {
        return toInt(b, 0);
    }

    public abstract int toInt(byte[] b, int offset);

    public final byte[] fromInt(int value) {
        byte[] bytes = new byte[4];
        fromInt(bytes, value, 0);
        return bytes;
    }

    public final void fromInt(byte[] bytes, int value) {
        fromInt(bytes, value, 0);
    }

    public final byte[] fromShort(short value) {
        byte[] bytes = new byte[4];
        fromShort(bytes, value, 0);
        return bytes;
    }

    public final void fromShort(byte[] bytes, short value) {
        fromShort(bytes, value, 0);
    }

    public abstract void fromShort(byte[] bytes, short value, int offset);

    public abstract void fromInt(byte[] bytes, int value, int offset);

    public final long toLong(byte[] b) {
        return toLong(b, 0);
    }

    public abstract long toLong(int high, int low);

    public abstract long toLong(byte[] b, int offset);

    public final byte[] fromLong(long value) {
        byte[] bytes = new byte[8];
        fromLong(bytes, value, 0);
        return bytes;
    }

    public final void fromLong(byte[] bytes, long value) {
        fromLong(bytes, value, 0);
    }

    public abstract void fromLong(byte[] bytes, long value, int offset);

    public abstract byte[] fromBitString(String str);

    public final String toBitString(IntsRef intsRef) {
        StringBuilder str = new StringBuilder();
        for (int ints : intsRef.ints) {
            str.append(toBitString(ints, 32));
        }
        return str.toString();
    }

    /**
     * Similar to Long.toBinaryString
     */
    public final String toBitString(long value) {
        return toBitString(value, 64);
    }

    public String toLastBitString(long value, int bits) {
        StringBuilder sb = new StringBuilder(bits);
        long lastBit = 1L << bits - 1;
        for (int i = 0; i < bits; i++) {
            if ((value & lastBit) == 0)
                sb.append('0');
            else
                sb.append('1');

            value <<= 1;
        }
        return sb.toString();
    }

    /**
     * Higher order bits comes first in the returned string.
     * <p>
     *
     * @param bits how many bits should be returned.
     */
    public String toBitString(long value, int bits) {
        StringBuilder sb = new StringBuilder(bits);
        long lastBit = 1L << 63;
        for (int i = 0; i < bits; i++) {
            if ((value & lastBit) == 0)
                sb.append('0');
            else
                sb.append('1');

            value <<= 1;
        }
        return sb.toString();
    }

    /**
     * Higher order bits comes first in the returned string.
     */
    public abstract String toBitString(byte[] bytes);

    public final int getIntLow(long longValue) {
        return (int) (longValue & 0xFFFFFFFFL);
    }

    public final int getIntHigh(long longValue) {
        return (int) (longValue >> 32);
    }

    public final long combineIntsToLong(int intLow, int intHigh) {
        return ((long) intHigh << 32) | (intLow & 0xFFFFFFFFL);
    }

}
