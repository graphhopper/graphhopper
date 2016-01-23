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
package com.graphhopper.reader.osm.conditional;

import com.graphhopper.reader.OSMWay;
import org.junit.Before;
import org.junit.Test;

import java.text.ParseException;
import java.util.Calendar;
import java.util.HashSet;

import static org.junit.Assert.*;

/**
 * @author Robin Boldt
 */
public class ConditionalParserTest extends CalendarBasedTest
{

    ConditionalParser parser;

    @Before
    public void setup()
    {
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
    public void testParseConditional() throws ParseException
    {
        DateRange dateRange = parser.getDateRange("no @ (2015 Sep 1-2015 Sep 30)");
        assertFalse(dateRange.isInRange(getCalendar(2015, Calendar.AUGUST, 31)));
        assertTrue(dateRange.isInRange(getCalendar(2015, Calendar.SEPTEMBER, 30)));
    }

    @Test
    public void testParseAllowingCondition() throws ParseException
    {
        DateRange dateRange = parser.getDateRange("yes @ (2015 Sep 1-2015 Sep 30)");
        assertNull(dateRange);
    }

    @Test
    public void testParsingOfLeading0() throws ParseException
    {
        DateRange dateRange = parser.getDateRange("no @ (01.11. - 31.03.)");
        assertTrue(dateRange.isInRange(getCalendar(2015, Calendar.DECEMBER, 2)));
        
        dateRange = parser.getDateRange("no @ (01.11 - 31.03)");
        assertTrue(dateRange.isInRange(getCalendar(2015, Calendar.DECEMBER, 2)));
    }
}
