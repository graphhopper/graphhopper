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

/**
 * LITTLE endianness is default for GraphHopper and most microprocessors.
 *
 * @author Peter Karich
 */
public class BitUtil {
    public static final BitUtil LITTLE = new BitUtil();

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

    public final short toShort(byte[] b, int offset) {
        return (short) ((b[offset + 1] & 0xFF) << 8 | (b[offset] & 0xFF));
    }

    public final int toInt(byte[] b) {
        return toInt(b, 0);
    }

    public final int toInt(byte[] b, int offset) {
        return (b[offset + 3] & 0xFF) << 24 | (b[offset + 2] & 0xFF) << 16
                | (b[offset + 1] & 0xFF) << 8 | (b[offset] & 0xFF);
    }

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

    public void fromShort(byte[] bytes, short value, int offset) {
        bytes[offset + 1] = (byte) (value >>> 8);
        bytes[offset] = (byte) (value);
    }

    public final void fromInt(byte[] bytes, int value, int offset) {
        bytes[offset + 3] = (byte) (value >>> 24);
        bytes[offset + 2] = (byte) (value >>> 16);
        bytes[offset + 1] = (byte) (value >>> 8);
        bytes[offset] = (byte) (value);
    }

    public final long toLong(byte[] b) {
        return toLong(b, 0);
    }

    public final long toLong(int int0, int int1) {
        return ((long) int1 << 32) | (int0 & 0xFFFFFFFFL);
    }

    public final long toLong(byte[] b, int offset) {
        return ((long) toInt(b, offset + 4) << 32) | (toInt(b, offset) & 0xFFFFFFFFL);
    }

    public final byte[] fromLong(long value) {
        byte[] bytes = new byte[8];
        fromLong(bytes, value, 0);
        return bytes;
    }

    public final void fromLong(byte[] bytes, long value) {
        fromLong(bytes, value, 0);
    }

    public final void fromLong(byte[] bytes, long value, int offset) {
        bytes[offset + 7] = (byte) (value >> 56);
        bytes[offset + 6] = (byte) (value >> 48);
        bytes[offset + 5] = (byte) (value >> 40);
        bytes[offset + 4] = (byte) (value >> 32);
        bytes[offset + 3] = (byte) (value >> 24);
        bytes[offset + 2] = (byte) (value >> 16);
        bytes[offset + 1] = (byte) (value >> 8);
        bytes[offset] = (byte) (value);
    }

    public byte[] fromBitString(String str) {
        // no need for performance or memory tuning ...
        int strLen = str.length();
        int bLen = str.length() / 8;
        if (strLen % 8 != 0)
            bLen++;

        byte[] bytes = new byte[bLen];
        int charI = 0;
        for (int b = bLen - 1; b >= 0; b--) {
            byte res = 0;
            for (int i = 0; i < 8; i++) {
                res <<= 1;
                if (charI < strLen && str.charAt(charI) != '0')
                    res |= 1;

                charI++;
            }
            bytes[b] = res;
        }
        return bytes;
    }

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
    public String toBitString(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 8);
        byte lastBit = (byte) (1 << 7);
        for (int bIndex = bytes.length - 1; bIndex >= 0; bIndex--) {
            byte b = bytes[bIndex];
            for (int i = 0; i < 8; i++) {
                if ((b & lastBit) == 0)
                    sb.append('0');
                else
                    sb.append('1');

                b <<= 1;
            }
        }
        return sb.toString();
    }

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
