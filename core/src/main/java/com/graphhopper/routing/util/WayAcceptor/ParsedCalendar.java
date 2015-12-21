package com.graphhopper.routing.util.WayAcceptor;

import java.util.Calendar;

/**
 * @author Robin Boldt
 */
public class ParsedCalendar
{
    ParseType parseType;
    Calendar parsedCalendar;


    private enum ParseType{
        YEAR_MONTH_DAY,
        YEAR_MONTH,
        MONTH_DAY,
        MONTH
    }

}
