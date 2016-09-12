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
package com.graphhopper.reader.osm.conditional;

import org.junit.Before;
import org.junit.Test;

import java.text.ParseException;
import java.util.Calendar;
import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.*;

/**
 * @author Robin Boldt
 */
public class ConditionalParserTest extends CalendarBasedTest {
    ConditionalParser parser;

    @Before
    public void setup() {
        HashSet<String> restrictedValues = new HashSet<String>();
        restrictedValues.add("private");
        restrictedValues.add("agricultural");
        restrictedValues.add("forestry");
        restrictedValues.add("no");
        restrictedValues.add("restricted");
        restrictedValues.add("delivery");
        restrictedValues.add("military");
        restrictedValues.add("emergency");

        parser = new ConditionalParser(restrictedValues);
    }

    @Test
    public void testParseConditional() throws ParseException {
        ValueRange dateRange = parser.getRange("no @ (2015 Sep 1-2015 Sep 30)");
        assertFalse(dateRange.isInRange(getCalendar(2015, Calendar.AUGUST, 31)));
        assertTrue(dateRange.isInRange(getCalendar(2015, Calendar.SEPTEMBER, 30)));
    }

    @Test
    public void testParseAllowingCondition() throws ParseException {
        ValueRange dateRange = parser.getRange("yes @ (2015 Sep 1-2015 Sep 30)");
        assertNull(dateRange);
    }

    @Test
    public void testParsingOfLeading0() throws ParseException {
        ValueRange dateRange = parser.getRange("no @ (01.11. - 31.03.)");
        assertTrue(dateRange.isInRange(getCalendar(2015, Calendar.DECEMBER, 2)));

        dateRange = parser.getRange("no @ (01.11 - 31.03)");
        assertTrue(dateRange.isInRange(getCalendar(2015, Calendar.DECEMBER, 2)));
    }

    @Test
    public void testGetRange() throws Exception {
        Set<String> set = new HashSet<>();
        set.add("no");
        ConditionalParser instance = new ConditionalParser(set);

        ValueRange result = instance.getRange("no @ weight > 10");
        assertEquals("weight", result.getKey());
        assertTrue(result.isInRange(11));
        assertFalse(result.isInRange(10));
        assertFalse(result.isInRange(9));

        result = instance.getRange("no @weight>10");
        assertTrue(result.isInRange(11));
        assertFalse(result.isInRange(10));
        assertFalse(result.isInRange(9));

        result = instance.getRange("no @ weight < 10");
        assertFalse(result.isInRange(11));
        assertFalse(result.isInRange(10));
        assertTrue(result.isInRange(9));

        // equals is ignored for now (not that bad for weight)
        result = instance.getRange("no @ weight <= 10");
        assertFalse(result.isInRange(11));
        assertFalse(result.isInRange(10));
        assertTrue(result.isInRange(9));

        result = instance.getRange("no @ weight<=10");
        assertFalse(result.isInRange(11));
        assertFalse(result.isInRange(10));
        assertTrue(result.isInRange(9));

        result = instance.getRange("no @ height > 2");
        assertEquals("height", result.getKey());
        assertFalse(result.isInRange(1));
        assertFalse(result.isInRange(2));
        assertTrue(result.isInRange(3));

        // unit is allowed according to wiki :/
        result = instance.getRange("no @ height > 2t");
        assertEquals("height", result.getKey());
        assertFalse(result.isInRange(1));
        assertFalse(result.isInRange(2));
        assertTrue(result.isInRange(3));
    }

    @Test
    public void parseNumber() {
        // TODO currently no unit conversation is done which can be required if a different one is passed in isInRange        
        Set<String> set = new HashSet<>();
        ConditionalParser p = new ConditionalParser(set);
        assertEquals(3, p.parseNumber("3t"), .1);
        assertEquals(3.1, p.parseNumber("3.1 t"), .1);
        assertEquals(3, p.parseNumber("3 meters"), .1);
    }
}
