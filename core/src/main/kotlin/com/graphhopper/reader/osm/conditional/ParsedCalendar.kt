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
 * This class represents a parsed Date and the parse type.
 *
 * @author Robin Boldt
 */
class ParsedCalendar(@JvmField val parseType: ParseType, @JvmField val parsedCalendar: Calendar) {

    val isYearless: Boolean
        get() = parseType == ParseType.MONTH || parseType == ParseType.MONTH_DAY

    val isDayless: Boolean
        get() = parseType == ParseType.MONTH || parseType == ParseType.YEAR_MONTH

    val isDayOnly: Boolean
        get() = parseType == ParseType.DAY

    fun getMax(): Calendar {
        if (isDayless) {
            parsedCalendar.set(Calendar.DAY_OF_MONTH, parsedCalendar.getActualMaximum(Calendar.DAY_OF_MONTH))
        }
        parsedCalendar.set(Calendar.HOUR_OF_DAY, parsedCalendar.getActualMaximum(Calendar.HOUR_OF_DAY))
        parsedCalendar.set(Calendar.MINUTE, parsedCalendar.getActualMaximum(Calendar.MINUTE))
        parsedCalendar.set(Calendar.SECOND, parsedCalendar.getActualMaximum(Calendar.SECOND))
        parsedCalendar.set(Calendar.MILLISECOND, parsedCalendar.getActualMaximum(Calendar.MILLISECOND))

        return parsedCalendar
    }

    fun getMin(): Calendar {
        if (isDayless) {
            parsedCalendar.set(Calendar.DAY_OF_MONTH, parsedCalendar.getActualMinimum(Calendar.DAY_OF_MONTH))
        }
        parsedCalendar.set(Calendar.HOUR_OF_DAY, parsedCalendar.getActualMinimum(Calendar.HOUR_OF_DAY))
        parsedCalendar.set(Calendar.MINUTE, parsedCalendar.getActualMinimum(Calendar.MINUTE))
        parsedCalendar.set(Calendar.SECOND, parsedCalendar.getActualMinimum(Calendar.SECOND))
        parsedCalendar.set(Calendar.MILLISECOND, parsedCalendar.getActualMinimum(Calendar.MILLISECOND))

        return parsedCalendar
    }

    override fun toString(): String =
        parseType.toString() + "; " + Helper.createFormatter().format(parsedCalendar.time)

    enum class ParseType {
        YEAR_MONTH_DAY,
        YEAR_MONTH,
        MONTH_DAY,
        MONTH,
        DAY
    }
}
