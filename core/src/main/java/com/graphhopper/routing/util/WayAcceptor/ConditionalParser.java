package com.graphhopper.routing.util.WayAcceptor;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;

/**
 * Created by robin on 20/12/15.
 */
public class ConditionalParser
{

    static SimpleDateFormat yearMonthDayFormat = new SimpleDateFormat("yyyy MMM dd");
    static SimpleDateFormat monthDayFormat = new SimpleDateFormat("MMM dd");
    static SimpleDateFormat yearMonthFormat = new SimpleDateFormat("yyyy MMM");
    static SimpleDateFormat monthFormat = new SimpleDateFormat("MMM");

    /**
     * Possible Inputs are:
     *
     * 2014␣Oct␣20
     *
     * @param dateString
     * @param endDate True if this is part of a range and the range ends on that date
     * @return
     */
    public static Calendar parseDateString(String dateString, boolean endDate) throws ParseException
    {
        Calendar calendar = Calendar.getInstance();
        try
        {
            calendar.setTime(yearMonthDayFormat.parse(dateString));
        } catch (ParseException e1)
        {
            try
            {
                calendar.setTime(monthDayFormat.parse(dateString));
            }catch (ParseException e2){
                try
                {
                    calendar.setTime(yearMonthFormat.parse(dateString));
                    if (endDate)
                        calendar.set(Calendar.DAY_OF_MONTH, calendar.getActualMaximum(Calendar.DAY_OF_MONTH));
                }catch (ParseException e3){
                    calendar.setTime(monthFormat.parse(dateString));
                    if (endDate)
                        calendar.set(Calendar.DAY_OF_MONTH, calendar.getActualMaximum(Calendar.DAY_OF_MONTH));
                }
            }
        }
        if(endDate){
            calendar.set(Calendar.HOUR_OF_DAY, calendar.getActualMaximum(Calendar.HOUR_OF_DAY));
            calendar.set(Calendar.MINUTE, calendar.getActualMaximum(Calendar.MINUTE));
            calendar.set(Calendar.SECOND, calendar.getActualMaximum(Calendar.SECOND));
            calendar.set(Calendar.MILLISECOND, calendar.getActualMaximum(Calendar.MILLISECOND));
        }
        return calendar;
    }

    /**
     * Possible Inputs are:
     * 2015␣Sep␣1-2015␣Sep␣30
     *
     * @param dateRangeString
     * @return
     */
    public static DateRange parseDateRange(String dateRangeString) throws ParseException
    {
        if(dateRangeString == null || dateRangeString.isEmpty()){
            throw new IllegalArgumentException("Passing empty Strings is not allowed");
        }
        String[] dateArr = dateRangeString.split("-");
        if(dateArr.length > 2 || dateArr.length < 1){
            throw new IllegalArgumentException("Only Strings containing two Date separated by a '-' or a single Date are allowed");
        }
        Calendar from = parseDateString(dateArr[0], false);
        Calendar to;
        if(dateArr.length == 2){
            to = parseDateString(dateArr[1], true);
        }else{
            to = parseDateString(dateArr[0], true);
        }

        return new DateRange(from, to);
    }


}
