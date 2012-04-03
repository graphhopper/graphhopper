/*
 *  Copyright 2012 Peter Karich info@jetsli.de
 * 
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 * 
 *       http://www.apache.org/licenses/LICENSE-2.0
 * 
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package de.jetsli.graph.util;

/**
 *
 * @author Peter Karich, info@jetsli.de
 */
public class BitUtil {

    public static float toFloat(byte[] bytes) {
        return toFloat(bytes, 0);
    }

    public static float toFloat(byte[] bytes, int offset) {
        return Float.intBitsToFloat(toInt(bytes, offset));
    }

    public static byte[] fromFloat(float value) {
        byte[] bytes = new byte[4];
        fromFloat(bytes, value, 0);
        return bytes;
    }

    public static void fromFloat(byte[] bytes, float value) {
        fromFloat(bytes, value, 0);
    }

    public static void fromFloat(byte[] bytes, float value, int offset) {
        fromInt(bytes, Float.floatToRawIntBits(value), offset);
    }

    public static int toInt(byte[] b) {
        return toInt(b, 0);
    }

    public static int toInt(byte[] b, int offset) {
        return (b[offset] & 0xFF) << 24 | (b[++offset] & 0xFF) << 16 | (b[++offset] & 0xFF) << 8 | (b[++offset] & 0xFF);
    }
    
    public static int toIntLittle(byte[] b, int offset) {
        return (b[offset] & 0xFF) << 24 | (b[++offset] & 0xFF) << 16 | (b[++offset] & 0xFF) << 8 | (b[++offset] & 0xFF);
    }

    public static byte[] fromInt(int value) {
        byte[] bytes = new byte[4];
        fromInt(bytes, value, 0);
        return bytes;
    }

    public static void fromInt(byte[] bytes, int value) {
        fromInt(bytes, value, 0);
    }

    public static void fromInt(byte[] bytes, int value, int offset) {
        bytes[offset] = (byte) (value >>> 24);
        bytes[++offset] = (byte) (value >>> 16);
        bytes[++offset] = (byte) (value >>> 8);
        bytes[++offset] = (byte) (value);
    }

    public static long toLong(byte[] b) {
        return ((long) toInt(b, 0) << 32) | (toInt(b, 1) & 0xFFFFFFFFL);
    }

    public static byte[] fromLong(long value) {
        byte[] bytes = new byte[8];
        fromLong(bytes, value, 0);
        return bytes;
    }

    public static void fromLong(byte[] bytes, long value) {
        fromLong(bytes, value, 0);
    }

    public static void fromLong(byte[] bytes, long value, int offset) {
        bytes[offset] = (byte) (value >>> 56);
        bytes[++offset] = (byte) (value >>> 48);
        bytes[++offset] = (byte) (value >>> 40);
        bytes[++offset] = (byte) (value >>> 32);
        bytes[++offset] = (byte) (value >>> 24);
        bytes[++offset] = (byte) (value >>> 16);
        bytes[++offset] = (byte) (value >>> 8);
        bytes[++offset] = (byte) (value);
    }

    public static String toBitString(long value) {
        return toBitString(value, 64);
    }
    
    public static String toBitString(long value, int bits) {
        StringBuilder sb = new StringBuilder(bits);
        long lastBit = 1L << (bits - 1);
        for (int i = 0; i < bits; i++) {
            if ((value & lastBit) == 0)
                sb.append('0');
            else
                sb.append('1');
            value <<= 1;
        }
        return sb.toString();
    }

    public static String toBitString(byte[] bytes) {
        StringBuilder sb = new StringBuilder(64);
        long lastBit = 1L << 63;
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
}
