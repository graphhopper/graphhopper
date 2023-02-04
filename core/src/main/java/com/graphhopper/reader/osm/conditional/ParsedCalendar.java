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

import com.graphhopper.core.util.Helper;

import java.util.Calendar;

/**
 * This class represents a parsed Date and the parse type.
 * <p>
 *
 * @author Robin Boldt
 */
public class ParsedCalendar {
    public final ParseType parseType;
    public final Calendar parsedCalendar;

    public ParsedCalendar(ParseType parseType, Calendar parsedCalendar) {
        this.parseType = parseType;
        this.parsedCalendar = parsedCalendar;
    }

    public boolean isYearless() {
        return parseType == ParseType.MONTH || parseType == ParseType.MONTH_DAY;
    }

    public boolean isDayless() {
        return parseType == ParseType.MONTH || parseType == ParseType.YEAR_MONTH;
    }

    public boolean isDayOnly() {
        return parseType == ParseType.DAY;
    }

    public Calendar getMax() {
        if (isDayless()) {
            parsedCalendar.set(Calendar.DAY_OF_MONTH, parsedCalendar.getActualMaximum(Calendar.DAY_OF_MONTH));
        }
        parsedCalendar.set(Calendar.HOUR_OF_DAY, parsedCalendar.getActualMaximum(Calendar.HOUR_OF_DAY));
        parsedCalendar.set(Calendar.MINUTE, parsedCalendar.getActualMaximum(Calendar.MINUTE));
        parsedCalendar.set(Calendar.SECOND, parsedCalendar.getActualMaximum(Calendar.SECOND));
        parsedCalendar.set(Calendar.MILLISECOND, parsedCalendar.getActualMaximum(Calendar.MILLISECOND));

        return parsedCalendar;
    }

    public Calendar getMin() {
        if (isDayless()) {
            parsedCalendar.set(Calendar.DAY_OF_MONTH, parsedCalendar.getActualMinimum(Calendar.DAY_OF_MONTH));
        }
        parsedCalendar.set(Calendar.HOUR_OF_DAY, parsedCalendar.getActualMinimum(Calendar.HOUR_OF_DAY));
        parsedCalendar.set(Calendar.MINUTE, parsedCalendar.getActualMinimum(Calendar.MINUTE));
        parsedCalendar.set(Calendar.SECOND, parsedCalendar.getActualMinimum(Calendar.SECOND));
        parsedCalendar.set(Calendar.MILLISECOND, parsedCalendar.getActualMinimum(Calendar.MILLISECOND));

        return parsedCalendar;
    }

    @Override
    public String toString() {
        return parseType + "; " + Helper.createFormatter().format(parsedCalendar.getTime());
    }

    public enum ParseType {
        YEAR_MONTH_DAY,
        YEAR_MONTH,
        MONTH_DAY,
        MONTH,
        DAY
    }

}
