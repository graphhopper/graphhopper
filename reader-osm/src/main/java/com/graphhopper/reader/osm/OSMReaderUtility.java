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
package com.graphhopper.reader.osm;

import javax.xml.datatype.DatatypeFactory;
import javax.xml.datatype.Duration;
import java.util.Date;

/**
 * This class currently parses the duration tag only.
 *
 * @author ratrun
 */
public class OSMReaderUtility {
    // use a day somewhere within July 1970 which then makes two identical long months ala 31 days, see #588
    private final static Date STATIC_DATE = new Date((31 * 6) * 24 * 3600 * 1000);

    /**
     * Parser according to http://wiki.openstreetmap.org/wiki/Key:duration The value consists of a
     * string ala 'hh:mm', format for hours and minutes 'mm', 'hh:mm' or 'hh:mm:ss', or
     * alternatively ISO_8601 duration
     * <p>
     *
     * @return duration value in seconds
     */
    public static long parseDuration(String str) throws IllegalArgumentException {
        long seconds = 0;
        if (str == null)
            return 0;

        // Check for ISO_8601 format
        if (str.startsWith("P")) {
            // A common mistake is when the minutes format is intended but the month format is specified 
            // e.g. one month "P1M" is set, but on minute "PT1M" is meant.
            Duration dur;
            try {
                dur = DatatypeFactory.newInstance().newDuration(str);
                seconds = dur.getTimeInMillis(STATIC_DATE) / 1000;
            } catch (Exception ex) {
                throw new IllegalArgumentException("Cannot parse duration tag value: " + str, ex);
            }
            return seconds;
        }

        try {
            int index = str.indexOf(":");
            if (index > 0) {
                String hourStr = str.substring(0, index);
                String minStr = str.substring(index + 1);
                String secondsStr = "0";
                index = minStr.indexOf(":");
                if (index > 0) {
                    secondsStr = minStr.substring(index + 1, index + 3);
                    minStr = minStr.substring(0, index);
                }

                seconds += Integer.parseInt(hourStr) * 60L * 60;
                seconds += Integer.parseInt(minStr) * 60L;
                seconds += Integer.parseInt(secondsStr);
                return seconds;
            } else {
                // value contains minutes
                seconds = Integer.parseInt(str) * 60;
            }
        } catch (Exception ex) {
            throw new IllegalArgumentException("Cannot parse duration tag value: " + str, ex);
        }
        return seconds;
    }

}
