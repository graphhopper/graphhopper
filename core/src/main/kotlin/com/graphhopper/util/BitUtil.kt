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
package com.graphhopper.util

/**
 * LITTLE endianness is default for GraphHopper and most microprocessors.
 *
 * @author Peter Karich
 */
class BitUtil {
    fun toDouble(bytes: ByteArray): Double = toDouble(bytes, 0)

    fun toDouble(bytes: ByteArray, offset: Int): Double = Double.fromBits(toLong(bytes, offset))

    fun fromDouble(value: Double): ByteArray {
        val bytes = ByteArray(8)
        fromDouble(bytes, value, 0)
        return bytes
    }

    fun fromDouble(bytes: ByteArray, value: Double) {
        fromDouble(bytes, value, 0)
    }

    fun fromDouble(bytes: ByteArray, value: Double, offset: Int) {
        fromLong(bytes, value.toRawBits(), offset)
    }

    fun toFloat(bytes: ByteArray): Float = toFloat(bytes, 0)

    fun toFloat(bytes: ByteArray, offset: Int): Float = Float.fromBits(toInt(bytes, offset))

    fun fromFloat(value: Float): ByteArray {
        val bytes = ByteArray(4)
        fromFloat(bytes, value, 0)
        return bytes
    }

    fun fromFloat(bytes: ByteArray, value: Float) {
        fromFloat(bytes, value, 0)
    }

    fun fromFloat(bytes: ByteArray, value: Float, offset: Int) {
        fromInt(bytes, value.toRawBits(), offset)
    }

    fun toShort(b: ByteArray): Short = toShort(b, 0)

    fun toShort(b: ByteArray, offset: Int): Short =
        ((b[offset + 1].toInt() and 0xFF shl 8) or (b[offset].toInt() and 0xFF)).toShort()

    fun toInt(b: ByteArray): Int = toInt(b, 0)

    fun toInt(b: ByteArray, offset: Int): Int =
        (b[offset + 3].toInt() and 0xFF shl 24) or (b[offset + 2].toInt() and 0xFF shl 16) or
                (b[offset + 1].toInt() and 0xFF shl 8) or (b[offset].toInt() and 0xFF)

    fun toUInt3(b: ByteArray, offset: Int): Int =
        (b[offset + 2].toInt() and 0xFF shl 16) or (b[offset + 1].toInt() and 0xFF shl 8) or (b[offset].toInt() and 0xFF)

    fun fromInt(value: Int): ByteArray {
        val bytes = ByteArray(4)
        fromInt(bytes, value, 0)
        return bytes
    }

    fun fromInt(bytes: ByteArray, value: Int) {
        fromInt(bytes, value, 0)
    }

    fun fromShort(value: Short): ByteArray {
        val bytes = ByteArray(4)
        fromShort(bytes, value, 0)
        return bytes
    }

    fun fromShort(bytes: ByteArray, value: Short) {
        fromShort(bytes, value, 0)
    }

    fun fromShort(bytes: ByteArray, value: Short, offset: Int) {
        bytes[offset + 1] = (value.toInt() ushr 8).toByte()
        bytes[offset] = value.toByte()
    }

    fun fromInt(bytes: ByteArray, value: Int, offset: Int) {
        bytes[offset + 3] = (value ushr 24).toByte()
        bytes[offset + 2] = (value ushr 16).toByte()
        bytes[offset + 1] = (value ushr 8).toByte()
        bytes[offset] = value.toByte()
    }

    /**
     * Note, currently value with higher bits set (like for a negative value) won't throw an exception at this level.
     */
    fun fromUInt3(bytes: ByteArray, value: Int, offset: Int) {
        bytes[offset + 2] = (value ushr 16).toByte()
        bytes[offset + 1] = (value ushr 8).toByte()
        bytes[offset] = value.toByte()
    }

    /**
     * See the counterpart [fromLong]
     */
    fun toLong(b: ByteArray): Long = toLong(b, 0)

    fun toLong(intLow: Int, intHigh: Int): Long =
        (intHigh.toLong() shl 32) or (intLow.toLong() and 0xFFFF_FFFFL)

    fun toLong(b: ByteArray, offset: Int): Long =
        (toInt(b, offset + 4).toLong() shl 32) or (toInt(b, offset).toLong() and 0xFFFF_FFFFL)

    fun fromLong(value: Long): ByteArray {
        val bytes = ByteArray(8)
        fromLong(bytes, value, 0)
        return bytes
    }

    fun fromLong(bytes: ByteArray, value: Long) {
        fromLong(bytes, value, 0)
    }

    fun fromLong(bytes: ByteArray, value: Long, offset: Int) {
        bytes[offset + 7] = (value shr 56).toByte()
        bytes[offset + 6] = (value shr 48).toByte()
        bytes[offset + 5] = (value shr 40).toByte()
        bytes[offset + 4] = (value shr 32).toByte()
        bytes[offset + 3] = (value shr 24).toByte()
        bytes[offset + 2] = (value shr 16).toByte()
        bytes[offset + 1] = (value shr 8).toByte()
        bytes[offset] = value.toByte()
    }

    fun fromBitString(str: String): ByteArray {
        // no need for performance or memory tuning ...
        val strLen = str.length
        var bLen = str.length / 8
        if (strLen % 8 != 0)
            bLen++

        val bytes = ByteArray(bLen)
        var charI = 0
        for (b in bLen - 1 downTo 0) {
            var res = 0
            for (i in 0 until 8) {
                res = res shl 1
                if (charI < strLen && str[charI] != '0')
                    res = res or 1

                charI++
            }
            bytes[b] = res.toByte()
        }
        return bytes
    }

    /**
     * Similar to Long.toBinaryString
     */
    fun toBitString(value: Long): String = toBitString(value, 64)

    fun toLastBitString(value: Long, bits: Int): String {
        val sb = StringBuilder(bits)
        val lastBit = 1L shl (bits - 1)
        var v = value
        for (i in 0 until bits) {
            if (v and lastBit == 0L)
                sb.append('0')
            else
                sb.append('1')

            v = v shl 1
        }
        return sb.toString()
    }

    /**
     * Higher order bits comes first in the returned string.
     *
     * @param bits how many bits should be returned.
     */
    fun toBitString(value: Long, bits: Int): String {
        val sb = StringBuilder(bits)
        val lastBit = 1L shl 63
        var v = value
        for (i in 0 until bits) {
            if (v and lastBit == 0L)
                sb.append('0')
            else
                sb.append('1')

            v = v shl 1
        }
        return sb.toString()
    }

    /**
     * Higher order bits comes first in the returned string.
     */
    fun toBitString(bytes: ByteArray): String {
        val sb = StringBuilder(bytes.size * 8)
        val lastBit = (1 shl 7).toByte()
        for (bIndex in bytes.size - 1 downTo 0) {
            var b = bytes[bIndex]
            for (i in 0 until 8) {
                if (b.toInt() and lastBit.toInt() == 0)
                    sb.append('0')
                else
                    sb.append('1')

                b = (b.toInt() shl 1).toByte()
            }
        }
        return sb.toString()
    }

    fun getIntLow(longValue: Long): Int = (longValue and 0xFFFF_FFFFL).toInt()

    fun getIntHigh(longValue: Long): Int = (longValue shr 32).toInt()

    companion object {
        @JvmField
        val LITTLE = BitUtil()

        @JvmStatic
        fun countBitValue(maxTurnCosts: Int): Int {
            if (maxTurnCosts < 0)
                throw IllegalArgumentException("maxTurnCosts cannot be negative $maxTurnCosts")

            var v = maxTurnCosts
            var counter = 0
            while (v > 0) {
                v = v shr 1
                counter++
            }
            return counter
        }

        /**
         * Converts the specified long into a signed int ('reverse' method for Integer.toUnsignedLong).
         */
        @JvmStatic
        fun toSignedInt(x: Long): Int = x.toInt()
    }
}
