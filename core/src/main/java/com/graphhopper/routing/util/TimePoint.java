package com.graphhopper.routing.util;

import ch.poole.openinghoursparser.*;

import java.time.ZonedDateTime;
import java.time.temporal.IsoFields;

/**
 * @author Andrzej Oles
 */
public class TimePoint {
    int year;
    int week;
    int month;
    int day;
    int weekday;
    int minutes;

    int nthWeek;
    int nthLastWeek;

    TimePoint(ZonedDateTime dateTime, boolean shift) {
        if (shift) {
            dateTime = dateTime.minusHours(24);
            minutes = TimeSpan.MAX_TIME;
        }
        else {
            minutes = 0;
        }
        year = dateTime.getYear();
        week = dateTime.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR);
        month = dateTime.getMonthValue() - 1;// match to ordinals from OpeningHoursParser
        day = dateTime.getDayOfMonth();
        weekday = dateTime.getDayOfWeek().getValue() - 1;// match to ordinals from OpeningHoursParser
        nthWeek = (day - 1) / 7 + 1;
        nthLastWeek = (day - dateTime.toLocalDate().lengthOfMonth()) / 7 - 1;
        minutes += dateTime.getHour() * 60 + dateTime.getMinute();
    }

    public int getYear() {
        return year;
    }

    public int getWeek() {
        return week;
    }

    public int getMonth() {
        return month;
    }

    public int getDay() {
        return day;
    }

    public int getWeekday() {
        return weekday;
    }

    public int getMinutes() {
        return minutes;
    }

    boolean isLessOrEqual(int a, int b) {
        return a <= b;
    }

    boolean isGreaterOrEqual(int a, int b) {
        return a >= b;
    }

    @FunctionalInterface
    interface BasicFunctionalInterface {
        boolean performTask(int a, int b);
    }

    public boolean isAfter(DateWithOffset date) {
        return compare(date, this::isGreaterOrEqual);
    }

    public boolean isBefore(DateWithOffset date) {
        return compare(date, this::isLessOrEqual);
    }

    public boolean inRange(DateWithOffset start, DateWithOffset end) {
        if (start.getYear() == end.getYear() ) {
            if ( start.getMonth().ordinal() > end.getMonth().ordinal() ||
                    (start.getMonth().ordinal() == end.getMonth().ordinal() && start.getDay() > end.getDay()) )
                return (isAfter(start) || isBefore(end));
        }
        return (isAfter(start) && isBefore(end));
    }

    public boolean atDate(DateWithOffset date) {
        int year = date.getYear();
        if (year != YearRange.UNDEFINED_YEAR && year != this.year)
            return false;

        Month month = date.getMonth();
        if (month != null && month.ordinal() != this.month)
            return false;

        int day = date.getDay();
        if (day != DateWithOffset.UNDEFINED_MONTH_DAY && day != this.day)
            return false;

        WeekDay nthWeekDay = date.getNthWeekDay();
        if (nthWeekDay != null) {
            if (nthWeekDay.ordinal() != weekday)
                return false;
            int nth = date.getNth();
            if (nth != (nth > 0 ? nthWeek : nthLastWeek))
                return false;
        }
        return true;
    }

    boolean compare(DateWithOffset date, BasicFunctionalInterface operator) {
        int year = date.getYear();
        if (year == YearRange.UNDEFINED_YEAR || year == this.year) {
            if (date.getMonth() == null)
                return true;
            int month = date.getMonth().ordinal();
            if (month == this.month) {
                int day = date.getDay();
                if (day == DateWithOffset.UNDEFINED_MONTH_DAY)
                    return true;
                else
                    return operator.performTask(this.day, day);
            }
            return operator.performTask(this.month, month);
        }
        return operator.performTask(this.year, year);
    }
}
