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

    private final HashSet<String> restrictedValues = new HashSet<>();

    public ConditionalParserTest() {
        restrictedValues.add("private");
        restrictedValues.add("agricultural");
        restrictedValues.add("forestry");
        restrictedValues.add("no");
        restrictedValues.add("restricted");
        restrictedValues.add("delivery");
        restrictedValues.add("military");
        restrictedValues.add("emergency");
    }

    ConditionalParser createParser(Calendar date) {
        return new ConditionalParser(restrictedValues).addConditionalValueParser(new DateRangeParser(date));
    }

    @Test
    public void testParseConditional() throws ParseException {
        String str = "no @ (2015 Sep 1-2015 Sep 30)";
        assertFalse(createParser(getCalendar(2015, Calendar.AUGUST, 31)).checkCondition(str));
        assertTrue(createParser(getCalendar(2015, Calendar.SEPTEMBER, 30)).checkCondition(str));
    }

    @Test
    public void testParseAllowingCondition() throws ParseException {
        assertFalse(createParser(getCalendar(2015, Calendar.JANUARY, 12)).
                checkCondition("yes @ (2015 Sep 1-2015 Sep 30)"));
    }

    @Test
    public void testParsingOfLeading0() throws ParseException {
        assertTrue(createParser(getCalendar(2015, Calendar.DECEMBER, 2)).
                checkCondition("no @ (01.11. - 31.03.)"));

        assertTrue(createParser(getCalendar(2015, Calendar.DECEMBER, 2)).
                checkCondition("no @ (01.11 - 31.03)"));
    }

    @Test
    public void testGetRange() throws Exception {
        assertTrue(ConditionalParser.createNumberParser("weight", 11).checkCondition("weight > 10").isCheckPassed());
        assertFalse(ConditionalParser.createNumberParser("weight", 10).checkCondition("weight > 10").isCheckPassed());
        assertFalse(ConditionalParser.createNumberParser("weight", 9).checkCondition("weight > 10").isCheckPassed());
        assertFalse(ConditionalParser.createNumberParser("xy", 9).checkCondition("weight > 10").isValid());

        Set<String> set = new HashSet<>();
        set.add("no");
        ConditionalParser instance = new ConditionalParser(set).
                setConditionalValueParser(ConditionalParser.createNumberParser("weight", 11));
        assertTrue(instance.checkCondition("no @weight>10"));
        instance.setConditionalValueParser(ConditionalParser.createNumberParser("weight", 10));
        assertFalse(instance.checkCondition("no @weight>10"));
        instance.setConditionalValueParser(ConditionalParser.createNumberParser("weight", 9));
        assertFalse(instance.checkCondition("no @weight>10"));

        instance.setConditionalValueParser(ConditionalParser.createNumberParser("weight", 11));
        assertFalse(instance.checkCondition("no @ weight < 10"));
        instance.setConditionalValueParser(ConditionalParser.createNumberParser("weight", 10));
        assertFalse(instance.checkCondition("no @ weight < 10"));
        instance.setConditionalValueParser(ConditionalParser.createNumberParser("weight", 9));
        assertTrue(instance.checkCondition("no @ weight < 10"));

        // equals is ignored for now (not that bad for weight)
        instance.setConditionalValueParser(ConditionalParser.createNumberParser("weight", 11));
        assertFalse(instance.checkCondition("no @ weight <= 10"));
        instance.setConditionalValueParser(ConditionalParser.createNumberParser("weight", 10));
        assertFalse(instance.checkCondition("no @ weight <= 10"));
        instance.setConditionalValueParser(ConditionalParser.createNumberParser("weight", 9));
        assertTrue(instance.checkCondition("no @ weight <= 10"));

        instance.setConditionalValueParser(ConditionalParser.createNumberParser("weight", 11));
        assertFalse(instance.checkCondition("no @ weight<=10"));
        instance.setConditionalValueParser(ConditionalParser.createNumberParser("weight", 10));
        assertFalse(instance.checkCondition("no @ weight<=10"));
        instance.setConditionalValueParser(ConditionalParser.createNumberParser("weight", 9));
        assertTrue(instance.checkCondition("no @ weight<=10"));

        instance.setConditionalValueParser(ConditionalParser.createNumberParser("height", 1));
        assertFalse(instance.checkCondition("no @ height > 2"));
        instance.setConditionalValueParser(ConditionalParser.createNumberParser("height", 2));
        assertFalse(instance.checkCondition("no @ height > 2"));
        instance.setConditionalValueParser(ConditionalParser.createNumberParser("height", 3));
        assertTrue(instance.checkCondition("no @ height > 2"));

        // unit is allowed according to wiki
        instance.setConditionalValueParser(ConditionalParser.createNumberParser("height", 1));
        assertFalse(instance.checkCondition("no @ height > 2t"));
        instance.setConditionalValueParser(ConditionalParser.createNumberParser("height", 2));
        assertFalse(instance.checkCondition("no @ height > 2t"));
        instance.setConditionalValueParser(ConditionalParser.createNumberParser("height", 3));
        assertTrue(instance.checkCondition("no @ height > 2t"));
    }

    @Test
    public void parseNumber() {
        // TODO currently no unit conversation is done which can be required if a different one is passed in checkCondition
        assertEquals(3, ConditionalParser.parseNumber("3t"), .1);
        assertEquals(3.1, ConditionalParser.parseNumber("3.1 t"), .1);
        assertEquals(3, ConditionalParser.parseNumber("3 meters"), .1);
    }
}
