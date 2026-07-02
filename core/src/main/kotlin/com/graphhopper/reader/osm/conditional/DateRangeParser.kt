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

import com.graphhopper.reader.osm.conditional.ConditionalValueParser.ConditionState
import com.graphhopper.util.Helper
import com.graphhopper.util.Helper.createFormatter
import java.text.DateFormat
import java.text.DateFormatSymbols
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * Parses a DateRange from OpenStreetMap. Currently only DateRanges that last at least one day are
 * supported. The Syntax is allowed inputs is described here:
 * http://wiki.openstreetmap.org/wiki/Key:opening_hours.
 *
 * @author Robin Boldt
 */
class DateRangeParser(private val date: Calendar) : ConditionalValueParser {

    internal constructor() : this(createCalendar())

    @Throws(ParseException::class)
    override fun checkCondition(conditionalValue: String): ConditionState {
        val dr = getRange(conditionalValue) ?: return ConditionState.INVALID

        return if (dr.isInRange(date))
            ConditionState.TRUE
        else
            ConditionState.FALSE
    }

    companion object {
        private val YEAR_MONTH_DAY_DF: DateFormat = create3CharMonthFormatter("yyyy MMM dd")
        private val MONTH_DAY_DF: DateFormat = create3CharMonthFormatter("MMM dd")
        private val MONTH_DAY2_DF: DateFormat = createFormatter("dd.MM")
        private val YEAR_MONTH_DF: DateFormat = create3CharMonthFormatter("yyyy MMM")
        private val MONTH_DF: DateFormat = create3CharMonthFormatter("MMM")
        private val DAY_NAMES = listOf("Su", "Mo", "Tu", "We", "Th", "Fr", "Sa")

        @JvmStatic
        fun createCalendar(): Calendar {
            // Use locale US as exception here (instead of UK) to match week order "Su-Sa" used in Calendar for day_of_week.
            // Inconsistent but we should not use US for other date handling stuff like strange default formatting, related to #647.
            return Calendar.getInstance(Helper.UTC, Locale.US)
        }

        @JvmStatic
        @JvmName("parseDateString")
        @Throws(ParseException::class)
        internal fun parseDateString(dateString: String): ParsedCalendar {
            // Replace occurrences of public holidays
            var str = dateString.replace("(,( )*)?(PH|SH)".toRegex(), "")
            str = str.trim()
            val calendar = createCalendar()
            var parsedCalendar: ParsedCalendar
            try {
                calendar.time = YEAR_MONTH_DAY_DF.parse(str)
                parsedCalendar = ParsedCalendar(ParsedCalendar.ParseType.YEAR_MONTH_DAY, calendar)
            } catch (e1: ParseException) {
                try {
                    calendar.time = MONTH_DAY_DF.parse(str)
                    parsedCalendar = ParsedCalendar(ParsedCalendar.ParseType.MONTH_DAY, calendar)
                } catch (e2: ParseException) {
                    try {
                        calendar.time = MONTH_DAY2_DF.parse(str)
                        parsedCalendar = ParsedCalendar(ParsedCalendar.ParseType.MONTH_DAY, calendar)
                    } catch (e3: ParseException) {
                        try {
                            calendar.time = YEAR_MONTH_DF.parse(str)
                            parsedCalendar = ParsedCalendar(ParsedCalendar.ParseType.YEAR_MONTH, calendar)
                        } catch (e4: ParseException) {
                            try {
                                calendar.time = MONTH_DF.parse(str)
                                parsedCalendar = ParsedCalendar(ParsedCalendar.ParseType.MONTH, calendar)
                            } catch (e5: ParseException) {
                                val index = DAY_NAMES.indexOf(str)
                                if (index < 0)
                                    throw ParseException("Unparsable date: \"$str\"", 0)

                                // Ranges from 1-7
                                calendar.set(Calendar.DAY_OF_WEEK, index + 1)
                                parsedCalendar = ParsedCalendar(ParsedCalendar.ParseType.DAY, calendar)
                            }
                        }
                    }
                }
            }
            return parsedCalendar
        }

        @JvmStatic
        @Throws(ParseException::class)
        fun getRange(dateRangeString: String?): DateRange? {
            if (dateRangeString == null || dateRangeString.isEmpty())
                return null

            // like Java's String.split: trailing empty strings are removed
            val dateArr = dateRangeString.split("-").dropLastWhile { it.isEmpty() }
            if (dateArr.size > 2 || dateArr.size < 1)
                return null
            // throw new IllegalArgumentException("Only Strings containing two Date separated by a '-' or a single Date are allowed");

            val from = parseDateString(dateArr[0])
            val to = if (dateArr.size == 2)
                parseDateString(dateArr[1])
            else
            // faster and safe?
            // new ParsedCalendar(from.parseType, (Calendar) from.parsedCalendar.clone())
                parseDateString(dateArr[0])

            return try {
                DateRange(from, to)
            } catch (ex: IllegalArgumentException) {
                null
            }
        }

        @JvmStatic
        fun createInstance(day: String): DateRangeParser {
            val calendar = createCalendar()
            try {
                if (!day.isEmpty())
                    calendar.time = Helper.createFormatter("yyyy-MM-dd").parse(day)
            } catch (e: ParseException) {
                throw IllegalArgumentException(e)
            }
            return DateRangeParser(calendar)
        }

        private fun create3CharMonthFormatter(pattern: String): SimpleDateFormat {
            val formatSymbols = DateFormatSymbols(Locale.ENGLISH)
            formatSymbols.shortMonths = arrayOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
            val df = SimpleDateFormat(pattern, formatSymbols)
            df.timeZone = Helper.UTC
            return df
        }
    }
}
