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
package com.graphhopper.core.util;

/**
 * Conversion between "the memory" (integer/long/float/double/string) to bytes via BIG endianness.
 * <p>
 *
 * @author Peter Karich
 */
public class BitUtilBig extends BitUtil {
    BitUtilBig() {
    }

    @Override
    public final short toShort(byte[] b, int offset) {
        return (short) ((b[offset] & 0xFF) << 8 | (b[offset + 1] & 0xFF));
    }

    @Override
    public void fromShort(byte[] bytes, short value, int offset) {
        bytes[offset] = (byte) (value >> 8);
        bytes[offset + 1] = (byte) (value);
    }

    @Override
    public final int toInt(byte[] b, int offset) {
        return (b[offset] & 0xFF) << 24 | (b[++offset] & 0xFF) << 16
                | (b[++offset] & 0xFF) << 8 | (b[++offset] & 0xFF);
    }

    @Override
    public final void fromInt(byte[] bytes, int value, int offset) {
        bytes[offset] = (byte) (value >> 24);
        bytes[++offset] = (byte) (value >> 16);
        bytes[++offset] = (byte) (value >> 8);
        bytes[++offset] = (byte) (value);
    }

    @Override
    public final long toLong(int int0, int int1) {
        return ((long) int0 << 32) | (int1 & 0xFFFFFFFFL);
    }

    @Override
    public final long toLong(byte[] b, int offset) {
        return ((long) toInt(b, offset) << 32) | (toInt(b, offset + 4) & 0xFFFFFFFFL);
    }

    @Override
    public final void fromLong(byte[] bytes, long value, int offset) {
        bytes[offset] = (byte) (value >> 56);
        bytes[++offset] = (byte) (value >> 48);
        bytes[++offset] = (byte) (value >> 40);
        bytes[++offset] = (byte) (value >> 32);
        bytes[++offset] = (byte) (value >> 24);
        bytes[++offset] = (byte) (value >> 16);
        bytes[++offset] = (byte) (value >> 8);
        bytes[++offset] = (byte) (value);
    }

    @Override
    public byte[] fromBitString(String str) {
        // no need for performance or memory tuning ...        
        int strLen = str.length();
        int bLen = str.length() / 8;
        if (strLen % 8 != 0)
            bLen++;

        byte[] bytes = new byte[bLen];
        int charI = 0;
        for (int b = 0; b < bLen; b++) {
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

    @Override
    public String toBitString(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 8);
        byte lastBit = (byte) (1 << 7);
        for (byte b : bytes) {
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

    @Override
    public String toString() {
        return "big";
    }
}
