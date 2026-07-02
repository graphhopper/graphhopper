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
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Pins behavior discovered during the Kotlin conversion, verified against the pre-migration
 * java implementation. See docs/pinned-behavior.md.
 */
public class UtilPinnedBehaviorTest {

    @Test
    public void toBitStringTruncatesShiftedBytes() {
        // the shift-and-narrow per bit must not leak into neighbor bytes; little-endian
        // ordering reverses the byte order
        assertEquals("0000111110101010", BitUtil.LITTLE.toBitString(new byte[]{(byte) 0xAA, 0x0F}));
    }

    @Test
    public void nanAzimuthThrows() {
        // Double.compare-based checks make NaN throw - a plain '<'/'>' comparison would
        // silently accept NaN
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> new AngleCalc().convertAzimuth2xaxisAngle(Double.NaN));
        assertEquals("Azimuth NaN must be in (0, 360)", e.getMessage());
    }

    @Test
    public void azimuthConversionReference() {
        assertEquals(0.0, new AngleCalc().convertAzimuth2xaxisAngle(90), 1e-12);
    }
}
