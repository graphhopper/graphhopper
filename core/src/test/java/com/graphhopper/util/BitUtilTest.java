/*
 *  Licensed to GraphHopper and Peter Karich under one or more contributor
 *  license agreements. See the NOTICE file distributed with this work for 
 *  additional information regarding copyright ownership.
 * 
 *  GraphHopper licenses this file to you under the Apache License, 
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

import org.junit.Test;
import static org.junit.Assert.*;

/**
 *
 * @author Peter Karich
 */
public class BitUtilTest
{
    @Test
    public void testToFloat()
    {
        byte[] bytes = BitUtil.fromFloat(Float.MAX_VALUE);
        assertEquals(Float.MAX_VALUE, BitUtil.toFloat(bytes), 1e-9);

        bytes = BitUtil.fromFloat(Float.MAX_VALUE / 3);
        assertEquals(Float.MAX_VALUE / 3, BitUtil.toFloat(bytes), 1e-9);
    }

    @Test
    public void testToDouble()
    {
        byte[] bytes = BitUtil.fromDouble(Double.MAX_VALUE);
        assertEquals(Double.MAX_VALUE, BitUtil.toDouble(bytes), 1e-9);

        bytes = BitUtil.fromDouble(Double.MAX_VALUE / 3);
        assertEquals(Double.MAX_VALUE / 3, BitUtil.toDouble(bytes), 1e-9);
    }

    @Test
    public void testToInt()
    {
        byte[] bytes = BitUtil.fromInt(Integer.MAX_VALUE);
        assertEquals(Integer.MAX_VALUE, BitUtil.toInt(bytes));

        bytes = BitUtil.fromInt(Integer.MAX_VALUE / 3);
        assertEquals(Integer.MAX_VALUE / 3, BitUtil.toInt(bytes));
    }

    @Test
    public void testToLong()
    {
        byte[] bytes = BitUtil.fromLong(Long.MAX_VALUE);
        assertEquals(Long.MAX_VALUE, BitUtil.toLong(bytes));

        bytes = BitUtil.fromLong(Long.MAX_VALUE / 7);
        assertEquals(Long.MAX_VALUE / 7, BitUtil.toLong(bytes));
    }

    @Test
    public void testToBitString()
    {
        assertEquals("0010101010101010101010101010101010101010101010101010101010101010", BitUtil.toBitString(Long.MAX_VALUE / 3));
        assertEquals("0111111111111111111111111111111111111111111111111111111111111111", BitUtil.toBitString(Long.MAX_VALUE));

        assertEquals("00101010101010101010101010101010", BitUtil.toBitString(BitUtil.fromInt(Integer.MAX_VALUE / 3)));

        assertEquals("10000000000000000000000000000000", BitUtil.toBitString((1L << 31), 32));
        assertEquals("00000000000000000000000000000001", BitUtil.toBitString(1, 32));
    }

    @Test
    public void testFromBitString()
    {
        String str = "011011100";
        assertEquals(str + "0000000", BitUtil.toBitString(BitUtil.fromBitString(str)));

        str = "01011110010111000000111111000111";
        assertEquals(str, BitUtil.toBitString(BitUtil.fromBitString(str)));

        str = "0101111001011100000011111100011";
        assertEquals(str + "0", BitUtil.toBitString(BitUtil.fromBitString(str)));
    }

    @Test
    public void testBitString2Long()
    {
        String str = "01000000000110000011100000011110";
        assertEquals("00000000000000000000000000000000" + str, BitUtil.toBitString(BitUtil.fromBitString2Long(str)));
    }

    @Test
    public void testReverse()
    {
        long ret = BitUtil.reverse(BitUtil.fromBitString2Long("0111000000000101"), 16);
        assertEquals("1010000000001110", BitUtil.toBitString(ret, 16));

        ret = BitUtil.reverse(BitUtil.fromBitString2Long("0111000000000101"), 8);
        assertEquals("0000000010100000", BitUtil.toBitString(ret, 16));

        ret = BitUtil.reversePart(BitUtil.fromBitString2Long("0111000000000101"), 8);
        assertEquals("0111000010100000", BitUtil.toBitString(ret, 16));
    }

    String toString( byte[] bytes )
    {
        return BitUtil.toBitString(bytes);
    }
}
