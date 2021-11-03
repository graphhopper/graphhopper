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

package com.graphhopper.routing.ev;

import com.graphhopper.storage.IntsRef;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SymmetricIntEncodedValueTest {

    @Test
    void illegalBits() {
        assertThrows(IllegalArgumentException.class, () -> new SymmetricIntEncodedValue("test", 0, 1));
        assertThrows(IllegalArgumentException.class, () -> new SymmetricIntEncodedValue("test", 1, 1));
        assertThrows(IllegalArgumentException.class, () -> new SymmetricIntEncodedValue("test", 32, 1));
    }

    @Test
    void maxValue() {
        SymmetricIntEncodedValue enc = new SymmetricIntEncodedValue("test", 4, 1);
        IntsRef ref = new IntsRef(4);
        enc.init(new EncodedValue.InitializerConfig());
        // there are four bits, but one is needed for the sign, so our range is [-7, 7]
        assertTrue(assertThrows(IllegalArgumentException.class, () -> enc.setInt(false, ref, 8)).getMessage().contains("maxValue: 7"));
        assertTrue(assertThrows(IllegalArgumentException.class, () -> enc.setInt(false, ref, -8)).getMessage().contains("minValue: -7"));
        enc.setInt(false, ref, 5);
        assertEquals(5, enc.getInt(false, ref));
    }

    @Test
    void setValues() {
        SymmetricIntEncodedValue enc = new SymmetricIntEncodedValue("test", 4, 1);
        IntsRef ref = new IntsRef(4);
        enc.init(new EncodedValue.InitializerConfig());
        assertEquals(0, enc.getInt(false, ref));
        assertEquals(0, enc.getInt(true, ref));

        for (int i = 0; i < 8; i++) {
            enc.setInt(false, ref, i);
            assertEquals(i, enc.getInt(false, ref));
            assertEquals(-i, enc.getInt(true, ref));

            enc.setInt(false, ref, -i);
            assertEquals(-i, enc.getInt(false, ref));
            assertEquals(i, enc.getInt(true, ref));

            enc.setInt(true, ref, i);
            assertEquals(-i, enc.getInt(false, ref));
            assertEquals(i, enc.getInt(true, ref));

            enc.setInt(true, ref, -i);
            assertEquals(i, enc.getInt(false, ref));
            assertEquals(-i, enc.getInt(true, ref));
        }
    }

    @Test
    void factor() {
        SymmetricIntEncodedValue enc = new SymmetricIntEncodedValue("test", 5, 2);
        IntsRef ref = new IntsRef(4);
        enc.init(new EncodedValue.InitializerConfig());

        enc.setInt(false, ref, 0);
        assertEquals(0, enc.getInt(false, ref));
        // value 1 will be rounded down to 0
        enc.setInt(false, ref, 1);
        assertEquals(0, enc.getInt(false, ref));
        assertEquals(0, enc.getInt(true, ref));
        // value 2 works
        enc.setInt(false, ref, 2);
        assertEquals(2, enc.getInt(false, ref));
        assertEquals(-2, enc.getInt(true, ref));

        // max value is (2^(bits-1)-1)*factor = 15*2=30
        enc.setInt(false, ref, 30);
        assertEquals(30, enc.getInt(false, ref));
        assertEquals(-30, enc.getInt(true, ref));

        // max value exceeded, or smaller than min value
        assertThrows(IllegalArgumentException.class, () -> enc.setInt(false, ref, 31));
        assertThrows(IllegalArgumentException.class, () -> enc.setInt(false, ref, -31));
    }

}