/*
 *  Licensed to GraphHopper GmbH under one or more contributor
 *  license agreements. See the NOTICE file distributed with this work for 
 *  additional information regarding copyright ownership.
 * 
 *  GraphHopper GmbH licenses this file to you under the Apache License, 
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

import java.text.DateFormat;
import java.util.Calendar;

/**
 * This class represents a date range and is able to determine if a given date is in that range.
 *
 * @author Robin Boldt
 */
public class DateRange {
    private final Calendar from;
    private final Calendar to;
    // Do not compare years
    boolean yearless = false;
    boolean dayOnly = false;
    boolean reverse = false;

    public DateRange(ParsedCalendar from, ParsedCalendar to) {
        Calendar fromCal = from.parsedCalendar;
        Calendar toCal = to.parsedCalendar;

        // This should never happen
        if (fromCal.get(Calendar.ERA) != toCal.get(Calendar.ERA)) {
            throw new IllegalArgumentException("Different calendar eras are not allowed. From:" + from + " To:" + to);
        }

        if (from.isYearless() && to.isYearless()) {
            yearless = true;
        }

        if (from.isDayOnly() && to.isDayOnly()) {
            dayOnly = true;
        }

        if (fromCal.getTimeInMillis() > toCal.getTimeInMillis()) {
            if (!yearless && !dayOnly) {
                throw new IllegalArgumentException("'from' after 'to' not allowed, except for isYearless and isDayOnly DateRanges. From:" + from + " To:" + to);
            } else {
                reverse = true;
            }
        }

        this.from = from.getMin();
        this.to = to.getMax();
    }

    public boolean isInRange(Calendar date) {
        if (!yearless && !dayOnly)
            return date.after(from) && date.before(to);

        if (dayOnly) {
            int currentDayOfWeek = date.get(Calendar.DAY_OF_WEEK);
            if (reverse) {
                return from.get(Calendar.DAY_OF_WEEK) <= currentDayOfWeek || currentDayOfWeek <= to.get(Calendar.DAY_OF_WEEK);
            } else {
                return from.get(Calendar.DAY_OF_WEEK) <= currentDayOfWeek && currentDayOfWeek <= to.get(Calendar.DAY_OF_WEEK);
            }
        }

        if (reverse)
            return isInRangeYearlessReverse(date);
        else
            return isInRangeYearless(date);
    }

    private boolean isInRangeYearless(Calendar date) {
        if (from.get(Calendar.MONTH) < date.get(Calendar.MONTH) && date.get(Calendar.MONTH) < to.get(Calendar.MONTH))
            return true;
        if (from.get(Calendar.MONTH) == date.get(Calendar.MONTH) && to.get(Calendar.MONTH) == date.get(Calendar.MONTH)) {
            if (from.get(Calendar.DAY_OF_MONTH) <= date.get(Calendar.DAY_OF_MONTH) && date.get(Calendar.DAY_OF_MONTH) <= to.get(Calendar.DAY_OF_MONTH))
                return true;
            else
                return false;
        }
        if (from.get(Calendar.MONTH) == date.get(Calendar.MONTH)) {
            if (from.get(Calendar.DAY_OF_MONTH) <= date.get(Calendar.DAY_OF_MONTH))
                return true;
            else
                return false;
        }
        if (to.get(Calendar.MONTH) == date.get(Calendar.MONTH)) {
            if (date.get(Calendar.DAY_OF_MONTH) <= to.get(Calendar.DAY_OF_MONTH))
                return true;
            else
                return false;
        }
        return false;
    }

    private boolean isInRangeYearlessReverse(Calendar date) {
        int currMonth = date.get(Calendar.MONTH);
        if (from.get(Calendar.MONTH) < currMonth || currMonth < to.get(Calendar.MONTH))
            return true;
        if (from.get(Calendar.MONTH) == currMonth && to.get(Calendar.MONTH) == currMonth) {
            if (from.get(Calendar.DAY_OF_MONTH) < date.get(Calendar.DAY_OF_MONTH)
                    || date.get(Calendar.DAY_OF_MONTH) < to.get(Calendar.DAY_OF_MONTH))
                return true;
            else
                return false;
        }
        if (from.get(Calendar.MONTH) == currMonth) {
            if (from.get(Calendar.DAY_OF_MONTH) <= date.get(Calendar.DAY_OF_MONTH))
                return true;
            else
                return false;
        }
        if (to.get(Calendar.MONTH) == currMonth) {
            if (date.get(Calendar.DAY_OF_MONTH) <= to.get(Calendar.DAY_OF_MONTH))
                return true;
            else
                return false;
        }
        return false;
    }

    @Override
    public String toString() {
        DateFormat f = Helper.createFormatter();
        return "yearless:" + yearless + ", dayOnly:" + dayOnly + ", reverse:" + reverse
                + ", from:" + f.format(from.getTime()) + ", to:" + f.format(to.getTime());
    }
}
