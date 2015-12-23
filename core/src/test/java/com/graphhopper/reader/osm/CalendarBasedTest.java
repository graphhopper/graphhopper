package com.graphhopper.reader.osm;

import java.util.Calendar;

/**
 * Base Test for calendar based tasks.
 *
 * @author Robin Boldt
 */
public abstract class CalendarBasedTest
{

    protected Calendar getCalendar( int year, int month, int day )
    {
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.YEAR, year);
        calendar.set(Calendar.MONTH, month);
        calendar.set(Calendar.DAY_OF_MONTH, day);
        return calendar;
    }

}
