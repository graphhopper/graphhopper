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
package com.graphhopper.routing.util.WayAcceptor;

import org.junit.Test;

import java.text.ParseException;
import java.util.Calendar;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * @author Robin boldt
 */
public class DaterangeParserTest extends CalendarBasedTest
{

    @Test
    public void testParseConditional() throws ParseException
    {
        assertSameDate(2014, Calendar.DECEMBER, 15, "2014 Dec 15");
        assertSameDate(2015, Calendar.MARCH, 2, "2015 Mar 2");
        assertSameDate(2015, Calendar.MARCH, 1, "2015 Mar");
        assertSameDate(1970, Calendar.MARCH, 31, "Mar 31");
        assertSameDate(1970, Calendar.DECEMBER, 1, "Dec");
    }

    @Test
    public void testParseSimpleDateRange() throws ParseException
    {
        DateRange dateRange = DaterangeParser.parseDateRange("2014 Aug 10-2014 Aug 14");
        assertFalse(dateRange.isInRange(getCalendar(2014, Calendar.AUGUST, 9)));
        assertTrue(dateRange.isInRange(getCalendar(2014, Calendar.AUGUST, 10)));
        assertTrue(dateRange.isInRange(getCalendar(2014, Calendar.AUGUST, 12)));
        assertTrue(dateRange.isInRange(getCalendar(2014, Calendar.AUGUST, 14)));
        assertFalse(dateRange.isInRange(getCalendar(2014, Calendar.AUGUST, 15)));
    }

    @Test
    public void testParseSimpleDateRangeWithoutYear() throws ParseException
    {
        DateRange dateRange = DaterangeParser.parseDateRange("Aug 10-Aug 14");
        assertFalse(dateRange.isInRange(getCalendar(2014, Calendar.AUGUST, 9)));
        assertTrue(dateRange.isInRange(getCalendar(2014, Calendar.AUGUST, 10)));
        assertTrue(dateRange.isInRange(getCalendar(2014, Calendar.AUGUST, 12)));
        assertTrue(dateRange.isInRange(getCalendar(2014, Calendar.AUGUST, 14)));
        assertFalse(dateRange.isInRange(getCalendar(2014, Calendar.AUGUST, 15)));
    }

    @Test
    public void testParseSimpleDateRangeWithoutYearAndDay() throws ParseException
    {
        DateRange dateRange = DaterangeParser.parseDateRange("Jul-Aug");
        assertFalse(dateRange.isInRange(getCalendar(2014, Calendar.JUNE, 9)));
        assertTrue(dateRange.isInRange(getCalendar(2014, Calendar.JULY, 10)));
        assertTrue(dateRange.isInRange(getCalendar(2014, Calendar.AUGUST, 12)));
        assertFalse(dateRange.isInRange(getCalendar(2014, Calendar.SEPTEMBER, 14)));
    }

    @Test
    public void testParseSimpleDateRangeWithoutYearAndDay2() throws ParseException
    {
        DateRange dateRange = DaterangeParser.parseDateRange("Mar-Sep");
        assertFalse(dateRange.isInRange(getCalendar(2014, Calendar.FEBRUARY, 25)));
        assertTrue(dateRange.isInRange(getCalendar(2014, Calendar.MARCH, 1)));
        assertTrue(dateRange.isInRange(getCalendar(2014, Calendar.SEPTEMBER, 30)));
        assertFalse(dateRange.isInRange(getCalendar(2014, Calendar.NOVEMBER, 1)));
    }

    @Test
    public void testParseReverseDateRange() throws ParseException
    {
        DateRange dateRange = DaterangeParser.parseDateRange("2014 Aug 14-2015 Mar 10");
        assertFalse(dateRange.isInRange(getCalendar(2014, Calendar.AUGUST, 13)));
        assertTrue(dateRange.isInRange(getCalendar(2014, Calendar.AUGUST, 14)));
        assertTrue(dateRange.isInRange(getCalendar(2015, Calendar.MARCH, 10)));
        assertFalse(dateRange.isInRange(getCalendar(2015, Calendar.MARCH, 11)));
    }

    @Test
    public void testParseReverseDateRangeWithoutYear() throws ParseException
    {
        DateRange dateRange = DaterangeParser.parseDateRange("Aug 14-Aug 10");
        assertTrue(dateRange.isInRange(getCalendar(2014, Calendar.JANUARY, 9)));
        assertTrue(dateRange.isInRange(getCalendar(2014, Calendar.AUGUST, 9)));
        assertFalse(dateRange.isInRange(getCalendar(2014, Calendar.AUGUST, 10)));
        assertFalse(dateRange.isInRange(getCalendar(2014, Calendar.AUGUST, 12)));
        assertFalse(dateRange.isInRange(getCalendar(2014, Calendar.AUGUST, 14)));
        assertTrue(dateRange.isInRange(getCalendar(2014, Calendar.AUGUST, 15)));
        assertTrue(dateRange.isInRange(getCalendar(2014, Calendar.SEPTEMBER, 15)));
    }

    @Test
    public void testParseReverseDateRangeWithoutYearAndDay() throws ParseException
    {
        DateRange dateRange = DaterangeParser.parseDateRange("Sep-Mar");
        assertFalse(dateRange.isInRange(getCalendar(2014, Calendar.AUGUST, 31)));
        assertTrue(dateRange.isInRange(getCalendar(2014, Calendar.SEPTEMBER, 1)));
        assertTrue(dateRange.isInRange(getCalendar(2014, Calendar.DECEMBER, 24)));
        assertTrue(dateRange.isInRange(getCalendar(2014, Calendar.JANUARY, 24)));
        assertTrue(dateRange.isInRange(getCalendar(2014, Calendar.MARCH, 31)));
        assertFalse(dateRange.isInRange(getCalendar(2014, Calendar.APRIL, 1)));
    }

    @Test
    public void testParseSingleDateRange() throws ParseException
    {
        DateRange dateRange = DaterangeParser.parseDateRange("2014 Sep 1");
        assertFalse(dateRange.isInRange(getCalendar(2014, Calendar.AUGUST, 31)));
        assertTrue(dateRange.isInRange(getCalendar(2014, Calendar.SEPTEMBER, 1)));
        assertFalse(dateRange.isInRange(getCalendar(2014, Calendar.SEPTEMBER, 30)));
        assertFalse(dateRange.isInRange(getCalendar(2014, Calendar.OCTOBER, 1)));
        assertFalse(dateRange.isInRange(getCalendar(2015, Calendar.SEPTEMBER, 1)));
    }

    @Test
    public void testParseSingleDateRangeWithoutDay() throws ParseException
    {
        DateRange dateRange = DaterangeParser.parseDateRange("2014 Sep");
        assertFalse(dateRange.isInRange(getCalendar(2014, Calendar.AUGUST, 31)));
        assertTrue(dateRange.isInRange(getCalendar(2014, Calendar.SEPTEMBER, 1)));
        assertTrue(dateRange.isInRange(getCalendar(2014, Calendar.SEPTEMBER, 30)));
        assertFalse(dateRange.isInRange(getCalendar(2014, Calendar.OCTOBER, 1)));
        assertFalse(dateRange.isInRange(getCalendar(2015, Calendar.SEPTEMBER, 1)));
    }

    @Test
    public void testParseSingleDateRangeWithoutYearAndDay() throws ParseException
    {
        DateRange dateRange = DaterangeParser.parseDateRange("Sep");
        assertFalse(dateRange.isInRange(getCalendar(2014, Calendar.AUGUST, 31)));
        assertTrue(dateRange.isInRange(getCalendar(2014, Calendar.SEPTEMBER, 1)));
        assertTrue(dateRange.isInRange(getCalendar(2014, Calendar.SEPTEMBER, 30)));
        assertFalse(dateRange.isInRange(getCalendar(2014, Calendar.OCTOBER, 1)));
    }

    private void assertSameDate( int year, int month, int day, String dateString) throws ParseException
    {
        Calendar expected = getCalendar(year, month, day);
        ParsedCalendar actualParsed = DaterangeParser.parseDateString(dateString);
        Calendar actual = actualParsed.parsedCalendar;
        assertEquals(expected.get(Calendar.YEAR), actual.get(Calendar.YEAR));
        assertEquals(expected.get(Calendar.MONTH), actual.get(Calendar.MONTH));
        assertEquals(expected.get(Calendar.DAY_OF_MONTH), actual.get(Calendar.DAY_OF_MONTH));
    }


}
