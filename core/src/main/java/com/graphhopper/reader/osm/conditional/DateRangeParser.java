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

import com.graphhopper.util.Helper;
import static com.graphhopper.util.Helper.createFormatter;
import java.text.DateFormat;
import java.text.ParseException;
import java.util.*;

/**
 * Parses a DateRange from OpenStreetMap. Currently only DateRanges that last at least one day are
 * supported. The Syntax is allowed inputs is described here:
 * http://wiki.openstreetmap.org/wiki/Key:opening_hours.
 * <p>
 * @author Robin Boldt
 */
public class DateRangeParser
{
    private static final DateFormat yearMonthDayFormat = createFormatter("yyyy MMM dd");
    private static final DateFormat monthDayFormat = createFormatter("MMM dd");
    private static final DateFormat monthDay2Format = createFormatter("dd.MM");
    private static final DateFormat yearMonthFormat = createFormatter("yyyy MMM");
    private static final DateFormat monthFormat = createFormatter("MMM");
    private static final List<String> dayNames = Arrays.asList(new String[]
    {
        "Su", "Mo", "Tu", "We", "Th", "Fr", "Sa"
    });

    static Calendar createCalendar()
    {
        // Use locale US as exception here (instead of UK) to match week order "Su-Sa" used in Calendar for day_of_week.
        // Inconsistent but we should not use US for other date handling stuff like strange default formatting, related to #647.
        return Calendar.getInstance(Helper.UTC, Locale.US);
    }

    public static ParsedCalendar parseDateString( String dateString ) throws ParseException
    {
        // Replace occurences of public holidays
        dateString = dateString.replaceAll("(,( )*)?(PH|SH)", "");
        dateString = dateString.trim();
        Calendar calendar = createCalendar();
        ParsedCalendar parsedCalendar;
        try
        {
            calendar.setTime(yearMonthDayFormat.parse(dateString));
            parsedCalendar = new ParsedCalendar(ParsedCalendar.ParseType.YEAR_MONTH_DAY, calendar);
        } catch (ParseException e1)
        {
            try
            {
                calendar.setTime(monthDayFormat.parse(dateString));
                parsedCalendar = new ParsedCalendar(ParsedCalendar.ParseType.MONTH_DAY, calendar);
            } catch (ParseException e2)
            {
                try
                {
                    calendar.setTime(monthDay2Format.parse(dateString));
                    parsedCalendar = new ParsedCalendar(ParsedCalendar.ParseType.MONTH_DAY, calendar);
                } catch (ParseException e3)
                {
                    try
                    {
                        calendar.setTime(yearMonthFormat.parse(dateString));
                        parsedCalendar = new ParsedCalendar(ParsedCalendar.ParseType.YEAR_MONTH, calendar);
                    } catch (ParseException e4)
                    {
                        try
                        {
                            calendar.setTime(monthFormat.parse(dateString));
                            parsedCalendar = new ParsedCalendar(ParsedCalendar.ParseType.MONTH, calendar);
                        } catch (ParseException e5)
                        {
                            int index = dayNames.indexOf(dateString);
                            if (index < 0)
                                throw new ParseException("Unparseable date: \"" + dateString + "\"", 0);

                            // Ranges from 1-7
                            calendar.set(Calendar.DAY_OF_WEEK, index + 1);
                            parsedCalendar = new ParsedCalendar(ParsedCalendar.ParseType.DAY, calendar);
                        }

                    }
                }
            }
        }
        return parsedCalendar;
    }

    public static DateRange parseDateRange( String dateRangeString ) throws ParseException
    {
        if (dateRangeString == null || dateRangeString.isEmpty())
            throw new IllegalArgumentException("Passing empty Strings is not allowed");

        String[] dateArr = dateRangeString.split("-");
        if (dateArr.length > 2 || dateArr.length < 1)
            throw new IllegalArgumentException("Only Strings containing two Date separated by a '-' or a single Date are allowed");

        ParsedCalendar from = parseDateString(dateArr[0]);
        ParsedCalendar to;
        if (dateArr.length == 2)
            to = parseDateString(dateArr[1]);
        else
            // faster and safe?
            // to = new ParsedCalendar(from.parseType, (Calendar) from.parsedCalendar.clone());
            to = parseDateString(dateArr[0]);

        return new DateRange(from, to);
    }

}
