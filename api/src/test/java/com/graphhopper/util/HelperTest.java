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

import java.util.Locale;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * @author Peter Karich
 */
public class HelperTest {

    @Test
    public void testCountBitValue() throws Exception {
        assertEquals(1, Helper.countBitValue(1));
        assertEquals(2, Helper.countBitValue(2));
        assertEquals(2, Helper.countBitValue(3));
        assertEquals(3, Helper.countBitValue(4));
        assertEquals(3, Helper.countBitValue(7));
        assertEquals(4, Helper.countBitValue(8));
        assertEquals(5, Helper.countBitValue(20));
    }

    @Test
    public void testGetLocale() throws Exception {
        assertEquals(Locale.GERMAN, Helper.getLocale("de"));
        assertEquals(Locale.GERMANY, Helper.getLocale("de_DE"));
        assertEquals(Locale.GERMANY, Helper.getLocale("de-DE"));
        assertEquals(Locale.ENGLISH, Helper.getLocale("en"));
        assertEquals(Locale.US, Helper.getLocale("en_US"));
        assertEquals(Locale.US, Helper.getLocale("en_US.UTF-8"));
    }

    @Test
    public void testRound() {
        assertEquals(100.94, Helper.round(100.94, 2), 1e-7);
        assertEquals(100.9, Helper.round(100.94, 1), 1e-7);
        assertEquals(101.0, Helper.round(100.95, 1), 1e-7);
    }

    @Test
    public void testKeepIn() {
        assertEquals(2, Helper.keepIn(2, 1, 4), 1e-2);
        assertEquals(3, Helper.keepIn(2, 3, 4), 1e-2);
        assertEquals(3, Helper.keepIn(-2, 3, 4), 1e-2);
    }

    @Test
    public void testUnsignedConversions() {
        long l = Helper.toUnsignedLong(-1);
        assertEquals(4294967295L, l);
        assertEquals(-1, Helper.toSignedInt(l));

        int intVal = Integer.MAX_VALUE;
        long maxInt = (long) intVal;
        assertEquals(intVal, Helper.toSignedInt(maxInt));

        intVal++;
        maxInt = Helper.toUnsignedLong(intVal);
        assertEquals(intVal, Helper.toSignedInt(maxInt));

        intVal++;
        maxInt = Helper.toUnsignedLong(intVal);
        assertEquals(intVal, Helper.toSignedInt(maxInt));

        assertEquals(0xFFFFffffL, (1L << 32) - 1);
        assertTrue(0xFFFFffffL > 0L);
    }

    @Test
    public void testCamelCaseToUnderscore() {
        assertEquals("test_case", Helper.camelCaseToUnderScore("testCase"));
        assertEquals("test_case_t_b_d", Helper.camelCaseToUnderScore("testCaseTBD"));
        assertEquals("_test_case", Helper.camelCaseToUnderScore("TestCase"));

        assertEquals("_test_case", Helper.camelCaseToUnderScore("_test_case"));
    }

    @Test
    public void testUnderscoreToCamelCase() {
        assertEquals("testCase", Helper.underScoreToCamelCase("test_case"));
        assertEquals("testCaseTBD", Helper.underScoreToCamelCase("test_case_t_b_d"));
        assertEquals("TestCase_", Helper.underScoreToCamelCase("_test_case_"));
    }
}
