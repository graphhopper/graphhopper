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

    public DateRange( Calendar from, Calendar to )
    {
        this.from = from;
        this.to = to;

        // This should never happen
        if (from.get(Calendar.ERA) != to.get(Calendar.ERA))
        {
            throw new IllegalArgumentException("Different ERAs are not allowed. From:" + from + " To:" + to);
        }

        // If we parse a String that does not contain a year. The year will be set to 1970
        if (from.get(Calendar.YEAR) == 1970 && to.get(Calendar.YEAR) == 1970)
        {
            yearless = true;
        }

        if (from.after(to))
        {
            if (!yearless)
            {
                logger.warn("Just created a DateRange that makes no sense. From:" + from + " To:" + to + ". We are just reversing from and to.");
                Calendar temp = from;
                this.from = to;
                this.to = temp;
            } else
            {
                reverse = true;
            }
        }
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
            if (from.get(Calendar.DAY_OF_MONTH) < date.get(Calendar.DAY_OF_MONTH))
                return true;
            else
                return false;
        }
        if (to.get(Calendar.MONTH) == date.get(Calendar.MONTH))
        {
            if (date.get(Calendar.DAY_OF_MONTH) < to.get(Calendar.DAY_OF_MONTH))
                return true;
            else
                return false;
        }
        return false;
    }


}
