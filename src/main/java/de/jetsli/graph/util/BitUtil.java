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

    public static double toDouble(byte[] bytes) {
        return toDouble(bytes, 0);
    }

    public static double toDouble(byte[] bytes, int offset) {
        return Double.longBitsToDouble(toLong(bytes, offset));
    }

    public static byte[] fromDouble(double value) {
        byte[] bytes = new byte[8];
        fromDouble(bytes, value, 0);
        return bytes;
    }

    public static void fromDouble(byte[] bytes, double value) {
        fromDouble(bytes, value, 0);
    }

    public static void fromDouble(byte[] bytes, double value, int offset) {
        fromLong(bytes, Double.doubleToRawLongBits(value), offset);
    }

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
        return toLong(b, 0);
    }

    public static long toLong(byte[] b, int offset) {
        return ((long) toInt(b, offset) << 32) | (toInt(b, offset + 4) & 0xFFFFFFFFL);
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

    public static long fromBitString2Long(String str) {
        if (str.length() > 64)
            throw new UnsupportedOperationException("Strings needs to fit into long (8*8 bits) but length was " + str.length());
        byte[] res = fromBitString(str);
        if (res.length < 8) {
            byte[] newBytes = new byte[8];
            System.arraycopy(res, 0, newBytes, 8 - res.length, res.length);
            res = newBytes;
        }
        return toLong(res);
    }

    public static byte[] fromBitString(String str) {
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
