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

import com.graphhopper.core.util.BitUtil;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
}
