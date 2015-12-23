package com.graphhopper.reader.osm.conditional;

import java.util.Calendar;

/**
 * This class represents a parsed Date and the parse type.
 *
 * @author Robin Boldt
 */
public class ParsedCalendar
{
    public final ParseType parseType;
    public final Calendar parsedCalendar;

    public ParsedCalendar( ParseType parseType, Calendar parsedCalendar )
    {
        this.parseType = parseType;
        this.parsedCalendar = parsedCalendar;
    }

    public boolean yearless()
    {
        return parseType == ParseType.MONTH || parseType == ParseType.MONTH_DAY;
    }

    public boolean dayless()
    {
        return parseType == ParseType.MONTH || parseType == ParseType.YEAR_MONTH;
    }

    public Calendar getMax()
    {
        if (dayless())
        {
            parsedCalendar.set(Calendar.DAY_OF_MONTH, parsedCalendar.getActualMaximum(Calendar.DAY_OF_MONTH));
        }
        parsedCalendar.set(Calendar.HOUR_OF_DAY, parsedCalendar.getActualMaximum(Calendar.HOUR_OF_DAY));
        parsedCalendar.set(Calendar.MINUTE, parsedCalendar.getActualMaximum(Calendar.MINUTE));
        parsedCalendar.set(Calendar.SECOND, parsedCalendar.getActualMaximum(Calendar.SECOND));
        parsedCalendar.set(Calendar.MILLISECOND, parsedCalendar.getActualMaximum(Calendar.MILLISECOND));

        return parsedCalendar;
    }

    public Calendar getMin()
    {
        if (dayless())
        {
            parsedCalendar.set(Calendar.DAY_OF_MONTH, parsedCalendar.getActualMinimum(Calendar.DAY_OF_MONTH));
        }
        parsedCalendar.set(Calendar.HOUR_OF_DAY, parsedCalendar.getActualMinimum(Calendar.HOUR_OF_DAY));
        parsedCalendar.set(Calendar.MINUTE, parsedCalendar.getActualMinimum(Calendar.MINUTE));
        parsedCalendar.set(Calendar.SECOND, parsedCalendar.getActualMinimum(Calendar.SECOND));
        parsedCalendar.set(Calendar.MILLISECOND, parsedCalendar.getActualMinimum(Calendar.MILLISECOND));

        return parsedCalendar;
    }

    public enum ParseType
    {
        YEAR_MONTH_DAY,
        YEAR_MONTH,
        MONTH_DAY,
        MONTH
    }

}
