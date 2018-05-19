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

import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * @author Peter Karich
 */
public abstract class AbstractBitUtilTester {
    protected BitUtil bitUtil = getBitUtil();

    abstract BitUtil getBitUtil();

    @Test
    public void testToFloat() {
        byte[] bytes = bitUtil.fromFloat(Float.MAX_VALUE);
        assertEquals(Float.MAX_VALUE, bitUtil.toFloat(bytes), 1e-9);

        bytes = bitUtil.fromFloat(Float.MAX_VALUE / 3);
        assertEquals(Float.MAX_VALUE / 3, bitUtil.toFloat(bytes), 1e-9);
    }

    @Test
    public void testToDouble() {
        byte[] bytes = bitUtil.fromDouble(Double.MAX_VALUE);
        assertEquals(Double.MAX_VALUE, bitUtil.toDouble(bytes), 1e-9);

        bytes = bitUtil.fromDouble(Double.MAX_VALUE / 3);
        assertEquals(Double.MAX_VALUE / 3, bitUtil.toDouble(bytes), 1e-9);
    }

    @Test
    public void testToInt() {
        byte[] bytes = bitUtil.fromInt(Integer.MAX_VALUE);
        assertEquals(Integer.MAX_VALUE, bitUtil.toInt(bytes));

        bytes = bitUtil.fromInt(Integer.MAX_VALUE / 3);
        assertEquals(Integer.MAX_VALUE / 3, bitUtil.toInt(bytes));
    }

    @Test
    public void testToShort() {
        byte[] bytes = bitUtil.fromShort(Short.MAX_VALUE);
        assertEquals(Short.MAX_VALUE, bitUtil.toShort(bytes));

        bytes = bitUtil.fromShort((short) (Short.MAX_VALUE / 3));
        assertEquals(Short.MAX_VALUE / 3, bitUtil.toShort(bytes));

        bytes = bitUtil.fromShort((short) -123);
        assertEquals(-123, bitUtil.toShort(bytes));

        bytes = bitUtil.fromShort((short) (0xFF | 0xFF));
        assertEquals(0xFF | 0xFF, bitUtil.toShort(bytes));
    }

    @Test
    public void testToLong() {
        byte[] bytes = bitUtil.fromLong(Long.MAX_VALUE);
        assertEquals(Long.MAX_VALUE, bitUtil.toLong(bytes));

        bytes = bitUtil.fromLong(Long.MAX_VALUE / 7);
        assertEquals(Long.MAX_VALUE / 7, bitUtil.toLong(bytes));
    }

    @Test
    public void testToLastBitString() {
        assertEquals("1", bitUtil.toLastBitString(1L, 1));
        assertEquals("01", bitUtil.toLastBitString(1L, 2));
        assertEquals("001", bitUtil.toLastBitString(1L, 3));
        assertEquals("010", bitUtil.toLastBitString(2L, 3));
        assertEquals("011", bitUtil.toLastBitString(3L, 3));
    }

    @Test
    public void testBitString2Long() {
        String str = "01000000000110000011100000011110";
        assertEquals(str + "00000000000000000000000000000000", bitUtil.toBitString(bitUtil.fromBitString2Long(str)));
        assertEquals("1000000000000000000000000000000000000000000000000000000000000000", bitUtil.toBitString(1L << 63));
    }

    @Test
    public void testReverse() {
        String str48 = "000000000000000000000000000000000000000000000000";
        long ret = bitUtil.reverse(bitUtil.fromBitString2Long(str48 + "0111000000000101"), 16);
        assertEquals(str48 + "1010000000001110", bitUtil.toBitString(ret, 64));

        ret = bitUtil.reverse(bitUtil.fromBitString2Long(str48 + "0111000000000101"), 8);
        assertEquals(str48 + "0000000010100000", bitUtil.toBitString(ret, 64));

//        ret = BitUtil.BIG.reversePart(bitUtil.fromBitString2Long("0111000000000101"), 8);
//        assertEquals("0111000010100000", bitUtil.toBitString(ret, 16));
    }
}
