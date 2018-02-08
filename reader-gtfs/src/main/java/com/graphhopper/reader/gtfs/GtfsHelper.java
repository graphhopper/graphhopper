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
package com.graphhopper.reader.gtfs;

import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.Locale;

/**
 * Generic helper for GTFS routines.
 * 
 * @author Alexey Valikov
 *
 */
public class GtfsHelper {

    private GtfsHelper() {
    }

    private static final int SECONDS_IN_MINUTE = 60;
    private static final int MINUTES_IN_HOUR = 60;
    private static final int HOURS_IN_DAY = 24;
    private static final int SECONDS_IN_HOUR = SECONDS_IN_MINUTE * MINUTES_IN_HOUR;

    public static int time(int hours, int minutes, int seconds) {
        return (hours * SECONDS_IN_HOUR + minutes * SECONDS_IN_MINUTE + seconds) * 1000;
    }

    public static int time(int hours, int minutes) {
        return time(hours, minutes, 0);
    }
    
    public static int time(LocalDateTime localDateTime) {
        return time(localDateTime.getHour(), localDateTime.getMinute(), 0);
    }

    public static LocalDateTime localDateTimeFromDate(Date date) {
        return LocalDateTime.parse(new SimpleDateFormat("YYYY-MM-dd'T'HH:mm", Locale.ROOT).format(date));
    }
}
