package com.graphhopper.reader.osm.conditional;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;

/**
 * Parses a DateRange. Currently only DateRanges that last at least one day are supported.
 * The Syntax is allowed inputs is described here: http://wiki.openstreetmap.org/wiki/Key:opening_hours.
 *
 * @author Robin Boldt
 */
public class DateRangeParser
{

    static SimpleDateFormat yearMonthDayFormat = new SimpleDateFormat("yyyy MMM dd");
    static SimpleDateFormat monthDayFormat = new SimpleDateFormat("MMM dd");
    static SimpleDateFormat yearMonthFormat = new SimpleDateFormat("yyyy MMM");
    static SimpleDateFormat monthFormat = new SimpleDateFormat("MMM");

    public static ParsedCalendar parseDateString( String dateString ) throws ParseException
    {
        dateString = dateString.trim();
        Calendar calendar = Calendar.getInstance();
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
                    calendar.setTime(yearMonthFormat.parse(dateString));
                    parsedCalendar = new ParsedCalendar(ParsedCalendar.ParseType.YEAR_MONTH, calendar);
                } catch (ParseException e3)
                {
                    calendar.setTime(monthFormat.parse(dateString));
                    parsedCalendar = new ParsedCalendar(ParsedCalendar.ParseType.MONTH, calendar);
                }
            }
        }
        return parsedCalendar;
    }

    public static DateRange parseDateRange( String dateRangeString ) throws ParseException
    {
        if (dateRangeString == null || dateRangeString.isEmpty())
        {
            throw new IllegalArgumentException("Passing empty Strings is not allowed");
        }
        String[] dateArr = dateRangeString.split("-");
        if (dateArr.length > 2 || dateArr.length < 1)
        {
            throw new IllegalArgumentException("Only Strings containing two Date separated by a '-' or a single Date are allowed");
        }
        ParsedCalendar from = parseDateString(dateArr[0]);
        ParsedCalendar to;
        if (dateArr.length == 2)
        {
            to = parseDateString(dateArr[1]);
        } else
        {
            to = parseDateString(dateArr[0]);
        }

        return new DateRange(from, to);
    }


}
