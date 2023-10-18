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

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author Peter Karich
 */
public class BitUtilLittleTest extends AbstractBitUtilTester {
    @Override
    BitUtil getBitUtil() {
        return BitUtil.LITTLE;
    }

    @Test
    public void testToBitString() {
        assertEquals("0010101010101010101010101010101010101010101010101010101010101010", bitUtil.toBitString(Long.MAX_VALUE / 3));
        assertEquals("0111111111111111111111111111111111111111111111111111111111111111", bitUtil.toBitString(Long.MAX_VALUE));

        assertEquals("00101010101010101010101010101010", bitUtil.toBitString(bitUtil.fromInt(Integer.MAX_VALUE / 3)));

        assertEquals("10000000000000000000000000000000", bitUtil.toBitString(1L << 63, 32));
        assertEquals("00000000000000000000000000000001", bitUtil.toBitString((1L << 32), 32));
    }

    @Test
    public void testFromBitString() {
        String str = "001110110";
        assertEquals(str + "0000000", bitUtil.toBitString(bitUtil.fromBitString(str)));

        str = "01011110010111000000111111000111";
        assertEquals(str, bitUtil.toBitString(bitUtil.fromBitString(str)));

        str = "0101111001011100000011111100011";
        assertEquals(str + "0", bitUtil.toBitString(bitUtil.fromBitString(str)));
    }

    @Test
    public void testCountBitValue() {
        assertEquals(1, BitUtil.countBitValue(1));
        assertEquals(2, BitUtil.countBitValue(2));
        assertEquals(2, BitUtil.countBitValue(3));
        assertEquals(3, BitUtil.countBitValue(4));
        assertEquals(3, BitUtil.countBitValue(7));
        assertEquals(4, BitUtil.countBitValue(8));
        assertEquals(5, BitUtil.countBitValue(20));
    }

    @Test
    public void testUnsignedConversions() {
        long l = BitUtil.toUnsignedLong(-1);
        assertEquals(4294967295L, l);
        assertEquals(-1, BitUtil.toSignedInt(l));

        int intVal = Integer.MAX_VALUE;
        long maxInt = (long) intVal;
        assertEquals(intVal, BitUtil.toSignedInt(maxInt));

        intVal++;
        maxInt = BitUtil.toUnsignedLong(intVal);
        assertEquals(intVal, BitUtil.toSignedInt(maxInt));

        intVal++;
        maxInt = BitUtil.toUnsignedLong(intVal);
        assertEquals(intVal, BitUtil.toSignedInt(maxInt));

        assertEquals(0xFFFFffffL, (1L << 32) - 1);
        assertTrue(0xFFFFffffL > 0L);
    }
}
