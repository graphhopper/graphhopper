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

import java.util.Locale;

import static com.graphhopper.util.Helper.UTF_CS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * @author Peter Karich
 */
public class HelperTest {

    @Test
    public void testElevation() {
        assertEquals(9034.1, Helper.uIntToEle(Helper.eleToUInt(9034.1)), .1);
        assertEquals(1234.5, Helper.uIntToEle(Helper.eleToUInt(1234.5)), .1);
        assertEquals(0, Helper.uIntToEle(Helper.eleToUInt(0)), .1);
        assertEquals(-432.3, Helper.uIntToEle(Helper.eleToUInt(-432.3)), .1);

        assertEquals(Double.MAX_VALUE, Helper.uIntToEle(Helper.eleToUInt(11_000)));
        assertEquals(Double.MAX_VALUE, Helper.uIntToEle(Helper.eleToUInt(Double.MAX_VALUE)));

        assertThrows(IllegalArgumentException.class, () -> Helper.eleToUInt(Double.NaN));
    }

    @Test
    public void testGetLocale() {
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
        // using negative values for decimalPlaces means we are rounding with precision > 1
        assertEquals(1040, Helper.round(1041.02, -1), 1.e-7);
        assertEquals(1000, Helper.round(1041.02, -2), 1.e-7);
    }

    @Test
    public void testKeepIn() {
        assertEquals(2, Helper.keepIn(2, 1, 4), 1e-2);
        assertEquals(3, Helper.keepIn(2, 3, 4), 1e-2);
        assertEquals(3, Helper.keepIn(-2, 3, 4), 1e-2);
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

    @Test
    public void testIssue2609() {
        String s = "";
        for (int i = 0; i < 128; i++) {
            s += "ä";
        }

        // all chars are 2 bytes so at 255 we cut the char into an invalid character and this is probably automatically
        // corrected leading to a longer string (or do chars have special marker bits to indicate their byte length?)
        assertEquals(257, new String(s.getBytes(UTF_CS), 0, 255, UTF_CS).getBytes(UTF_CS).length);

        // see this in action:
        byte[] bytes = "a".getBytes(UTF_CS);
        assertEquals(1, new String(bytes, 0, 1, UTF_CS).getBytes(UTF_CS).length);
        // force incorrect char:
        bytes[0] = -25;
        assertEquals(3, new String(bytes, 0, 1, UTF_CS).getBytes(UTF_CS).length);
    }

    @Test
    void degreeToInt() {
        int storedInt = 444_494_395;
        double lat = Helper.intToDegree(storedInt);
        assertEquals(44.4494395, lat);
        assertEquals(storedInt, Helper.degreeToInt(lat));
    }

    @Test
    void eleToInt() {
        int storedInt = 1145636;
        double ele = Helper.uIntToEle(storedInt);
        // converting to double is imprecise
        assertEquals(145.635986, ele, 1.e-6);
        // ... but converting back to int should yield the same value we started with!
        assertEquals(storedInt, Helper.eleToUInt(ele));
    }
}
