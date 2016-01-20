package com.graphhopper.reader.osm.conditional;

import java.text.SimpleDateFormat;
import java.util.Calendar;

/**
 * This class represents a date range and is able to determine if a given date is in that range.
 * <p>
 * @author Robin Boldt
 */
public class DateRange
{
    private Calendar from;
    private Calendar to;

    // Do not compare years
    boolean yearless = false;

    boolean dayOnly = false;

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

        if (from.isYearless() && to.isYearless())
        {
            yearless = true;
        }

        if (from.isDayOnly() && to.isDayOnly())
        {
            dayOnly = true;
        }

        if (fromCal.after(toCal))
        {
            if (!yearless && !dayOnly)
            {
                throw new IllegalArgumentException("From after to makes no sense, except for isYearless and isDayOnly DateRanges. From:" + from + " To:" + to);
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
        if (!yearless && !dayOnly)
            return date.after(from) && date.before(to);

        if (dayOnly)
        {
            if (reverse)
            {
                return (from.get(Calendar.DAY_OF_WEEK) <= date.get(Calendar.DAY_OF_WEEK) || date.get(Calendar.DAY_OF_WEEK) <= to.get(Calendar.DAY_OF_WEEK));
            } else
            {
                return (from.get(Calendar.DAY_OF_WEEK) <= date.get(Calendar.DAY_OF_WEEK) && date.get(Calendar.DAY_OF_WEEK) <= to.get(Calendar.DAY_OF_WEEK));
            }
        }

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
        int currMonth = date.get(Calendar.MONTH);
        if (from.get(Calendar.MONTH) < currMonth || currMonth < to.get(Calendar.MONTH))
            return true;
        if (from.get(Calendar.MONTH) == currMonth && to.get(Calendar.MONTH) == currMonth)
        {
            if (from.get(Calendar.DAY_OF_MONTH) < date.get(Calendar.DAY_OF_MONTH)
                    || date.get(Calendar.DAY_OF_MONTH) < to.get(Calendar.DAY_OF_MONTH))
                return true;
            else
                return false;
        }
        if (from.get(Calendar.MONTH) == currMonth)
        {
            if (from.get(Calendar.DAY_OF_MONTH) <= date.get(Calendar.DAY_OF_MONTH))
                return true;
            else
                return false;
        }
        if (to.get(Calendar.MONTH) == currMonth)
        {
            if (date.get(Calendar.DAY_OF_MONTH) <= to.get(Calendar.DAY_OF_MONTH))
                return true;
            else
                return false;
        }
        return false;
    }

    @Override
    public String toString()
    {
        SimpleDateFormat f = new SimpleDateFormat();
        return "yearless:" + yearless + ", dayOnly:" + dayOnly + ", reverse:" + reverse
                + ", from:" + f.format(from.getTime()) + ", to:" + f.format(to.getTime());
    }
}
