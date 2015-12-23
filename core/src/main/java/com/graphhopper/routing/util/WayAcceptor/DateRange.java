package com.graphhopper.routing.util.WayAcceptor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Calendar;

/**
 * @author Robin Boldt
 */
public class DateRange
{

    private static final Logger logger = LoggerFactory.getLogger(DateRange.class);

    private Calendar from;
    private Calendar to;

    // Do not compare years
    boolean yearless = false;

    boolean reverse = false;

    // TODO Gets to complex? Create Factory?
    public DateRange( ParsedCalendar from, ParsedCalendar to )
    {
        Calendar fromCal = from.parsedCalendar;
        Calendar toCal = to.parsedCalendar;

        // This should never happen
        if (fromCal.get(Calendar.ERA) != fromCal.get(Calendar.ERA))
        {
            throw new IllegalArgumentException("Different ERAs are not allowed. From:" + from + " To:" + to);
        }

        if (from.yearless() && to.yearless())
        {
            yearless = true;
        }

        if (fromCal.after(toCal))
        {
            if (!yearless)
            {
                throw new IllegalArgumentException("From after to makes no sense, except for yearless DateRanges. From:" + from + " To:" + to);
            } else
            {
                reverse = true;
            }
        }

        this.from = from.getMin();
        this.to = to.getMax();

    }

    public boolean isInRange( Calendar date )
    {
        if (!yearless)
            return date.after(from) && date.before(to);

        if (reverse)
            return isInRangeYearlessReverse(date);
        else
            return isInRangeYearless(date);
    }

    private boolean isInRangeYearless( Calendar date )
    {
        if (from.get(Calendar.MONTH) < date.get(Calendar.MONTH) && date.get(Calendar.MONTH) < to.get(Calendar.MONTH))
            return true;
        if (from.get(Calendar.MONTH) == date.get(Calendar.MONTH) && to.get(Calendar.MONTH) == date.get(Calendar.MONTH))
        {
            if (from.get(Calendar.DAY_OF_MONTH) <= date.get(Calendar.DAY_OF_MONTH) && date.get(Calendar.DAY_OF_MONTH) <= to.get(Calendar.DAY_OF_MONTH))
                return true;
            else
                return false;
        }
        if (from.get(Calendar.MONTH) == date.get(Calendar.MONTH))
        {
            if (from.get(Calendar.DAY_OF_MONTH) <= date.get(Calendar.DAY_OF_MONTH))
                return true;
            else
                return false;
        }
        if (to.get(Calendar.MONTH) == date.get(Calendar.MONTH))
        {
            if (date.get(Calendar.DAY_OF_MONTH) <= to.get(Calendar.DAY_OF_MONTH))
                return true;
            else
                return false;
        }
        return false;
    }

    private boolean isInRangeYearlessReverse( Calendar date )
    {
        if (from.get(Calendar.MONTH) < date.get(Calendar.MONTH) || date.get(Calendar.MONTH) < to.get(Calendar.MONTH))
            return true;
        if (from.get(Calendar.MONTH) == date.get(Calendar.MONTH) && to.get(Calendar.MONTH) == date.get(Calendar.MONTH))
        {
            if (from.get(Calendar.DAY_OF_MONTH) < date.get(Calendar.DAY_OF_MONTH) || date.get(Calendar.DAY_OF_MONTH) < to.get(Calendar.DAY_OF_MONTH))
                return true;
            else
                return false;
        }
        if (from.get(Calendar.MONTH) == date.get(Calendar.MONTH))
        {
            if (from.get(Calendar.DAY_OF_MONTH) <= date.get(Calendar.DAY_OF_MONTH))
                return true;
            else
                return false;
        }
        if (to.get(Calendar.MONTH) == date.get(Calendar.MONTH))
        {
            if (date.get(Calendar.DAY_OF_MONTH) >= to.get(Calendar.DAY_OF_MONTH))
                return true;
            else
                return false;
        }
        return false;
    }


}
