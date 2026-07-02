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
package com.graphhopper.reader.osm.conditional

import com.graphhopper.util.Helper
import java.util.Calendar

/**
 * This class represents a date range and is able to determine if a given date is in that range.
 *
 * @author Robin Boldt
 */
class DateRange(from: ParsedCalendar, to: ParsedCalendar) {
    private val from: Calendar
    private val to: Calendar

    // Do not compare years
    @JvmField
    internal var yearless = false

    @JvmField
    internal var dayOnly = false

    @JvmField
    internal var reverse = false

    init {
        val fromCal = from.parsedCalendar
        val toCal = to.parsedCalendar

        // This should never happen
        if (fromCal.get(Calendar.ERA) != toCal.get(Calendar.ERA)) {
            throw IllegalArgumentException("Different calendar eras are not allowed. From:$from To:$to")
        }

        if (from.isYearless && to.isYearless) {
            yearless = true
        }

        if (from.isDayOnly && to.isDayOnly) {
            dayOnly = true
        }

        if (fromCal.timeInMillis > toCal.timeInMillis) {
            if (!yearless && !dayOnly) {
                throw IllegalArgumentException("'from' after 'to' not allowed, except for isYearless and isDayOnly DateRanges. From:$from To:$to")
            } else {
                reverse = true
            }
        }

        this.from = from.getMin()
        this.to = to.getMax()
    }

    fun isInRange(date: Calendar): Boolean {
        if (!yearless && !dayOnly)
            return date.after(from) && date.before(to) || date == from || date == to

        if (dayOnly) {
            val currentDayOfWeek = date.get(Calendar.DAY_OF_WEEK)
            return if (reverse) {
                from.get(Calendar.DAY_OF_WEEK) <= currentDayOfWeek || currentDayOfWeek <= to.get(Calendar.DAY_OF_WEEK)
            } else {
                from.get(Calendar.DAY_OF_WEEK) <= currentDayOfWeek && currentDayOfWeek <= to.get(Calendar.DAY_OF_WEEK)
            }
        }

        return if (reverse)
            isInRangeYearlessReverse(date)
        else
            isInRangeYearless(date)
    }

    private fun isInRangeYearless(date: Calendar): Boolean {
        if (from.get(Calendar.MONTH) < date.get(Calendar.MONTH) && date.get(Calendar.MONTH) < to.get(Calendar.MONTH))
            return true
        if (from.get(Calendar.MONTH) == date.get(Calendar.MONTH) && to.get(Calendar.MONTH) == date.get(Calendar.MONTH)) {
            return from.get(Calendar.DAY_OF_MONTH) <= date.get(Calendar.DAY_OF_MONTH) && date.get(Calendar.DAY_OF_MONTH) <= to.get(Calendar.DAY_OF_MONTH)
        }
        if (from.get(Calendar.MONTH) == date.get(Calendar.MONTH)) {
            return from.get(Calendar.DAY_OF_MONTH) <= date.get(Calendar.DAY_OF_MONTH)
        }
        if (to.get(Calendar.MONTH) == date.get(Calendar.MONTH)) {
            return date.get(Calendar.DAY_OF_MONTH) <= to.get(Calendar.DAY_OF_MONTH)
        }
        return false
    }

    private fun isInRangeYearlessReverse(date: Calendar): Boolean {
        val currMonth = date.get(Calendar.MONTH)
        if (from.get(Calendar.MONTH) < currMonth || currMonth < to.get(Calendar.MONTH))
            return true
        if (from.get(Calendar.MONTH) == currMonth && to.get(Calendar.MONTH) == currMonth) {
            return from.get(Calendar.DAY_OF_MONTH) < date.get(Calendar.DAY_OF_MONTH)
                    || date.get(Calendar.DAY_OF_MONTH) < to.get(Calendar.DAY_OF_MONTH)
        }
        if (from.get(Calendar.MONTH) == currMonth) {
            return from.get(Calendar.DAY_OF_MONTH) <= date.get(Calendar.DAY_OF_MONTH)
        }
        if (to.get(Calendar.MONTH) == currMonth) {
            return date.get(Calendar.DAY_OF_MONTH) <= to.get(Calendar.DAY_OF_MONTH)
        }
        return false
    }

    override fun toString(): String {
        val f = Helper.createFormatter()
        return "yearless:" + yearless + ", dayOnly:" + dayOnly + ", reverse:" + reverse +
                ", from:" + f.format(from.time) + ", to:" + f.format(to.time)
    }
}
