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
package com.graphhopper.routing.util.parsers;

import com.graphhopper.reader.ReaderWay;
import com.graphhopper.routing.ev.DecimalEncodedValue;
import com.graphhopper.routing.ev.EdgeIntAccess;
import com.graphhopper.routing.ev.MaxSpeed;
import com.graphhopper.storage.IntsRef;
import com.graphhopper.util.DistanceCalcEarth;
import com.graphhopper.util.Helper;

import static com.graphhopper.routing.ev.MaxSpeed.MAXSPEED_150;
import static com.graphhopper.routing.ev.MaxSpeed.MAXSPEED_MISSING;

public class OSMMaxSpeedParser implements TagParser {

    /**
     * Special value to represent `maxspeed=none` internally, not exposed via the maxspeed encoded value
     */
    public static final double MAXSPEED_NONE = -1;

    private final DecimalEncodedValue carMaxSpeedEnc;

    public OSMMaxSpeedParser(DecimalEncodedValue carMaxSpeedEnc) {
        if (!carMaxSpeedEnc.isStoreTwoDirections())
            throw new IllegalArgumentException("EncodedValue for maxSpeed must be able to store two directions");

        this.carMaxSpeedEnc = carMaxSpeedEnc;
    }

    @Override
    public void handleWayTags(int edgeId, EdgeIntAccess edgeIntAccess, ReaderWay way, IntsRef relationFlags) {
        carMaxSpeedEnc.setDecimal(false, edgeId, edgeIntAccess, parseMaxSpeed(way, false));
        carMaxSpeedEnc.setDecimal(true, edgeId, edgeIntAccess, parseMaxSpeed(way, true));
    }

    /**
     * @return The maxspeed for the given way. It can be anything between 0 and {@link MaxSpeed.MAXSPEED_150},
     *         or {@link MaxSpeed.MAXSPEED_MISSING} in case there is no valid maxspeed tagged for this way in this direction.
     */
    public static double parseMaxSpeed(ReaderWay way, boolean reverse) {
        double directedMaxSpeed = parseMaxSpeedTag(way, reverse ? "maxspeed:backward" : "maxspeed:forward");
        if (directedMaxSpeed != MAXSPEED_MISSING)
            return directedMaxSpeed;
        else {
            return parseMaxSpeedTag(way, "maxspeed");
        }
    }

    private static double parseMaxSpeedTag(ReaderWay way, String tag) {
        double maxSpeed = parseMaxspeedString(way.getTag(tag));
        if (maxSpeed != MAXSPEED_MISSING && maxSpeed != MAXSPEED_NONE)
            // there is no actual use for maxspeeds above 150 so we simply truncate here
            return Math.min(MAXSPEED_150, maxSpeed);
        else if (maxSpeed == MAXSPEED_NONE && way.hasTag("highway", "motorway", "motorway_link", "trunk", "trunk_link", "primary"))
            // We ignore maxspeed=none with some exceptions where unlimited speed is actually allowed like on some
            // motorways, trunks and (very rarely) primary roads in Germany, or the Isle of Man. In other cases
            // maxspeed=none is only used because mappers have a false understanding of this tag.
            return MaxSpeed.MAXSPEED_150;
        else
            return MAXSPEED_MISSING;
    }

    /**
     ** @return the speed in km/h, or {@link MAXSPEED_MISSING} if the string is invalid, or {@link MAXSPEED_NONE} in case it equals 'none'
     */
    public static double parseMaxspeedString(String str) {
        if (Helper.isEmpty(str))
            return MAXSPEED_MISSING;

        if ("walk".equals(str.trim()))
            return 6;

        if ("none".equals(str.trim()))
            // Special case intended to be used when there is actually no speed limit and drivers
            // can go as fast as they want like on parts of the German Autobahn. However, in OSM
            // this is sometimes misused by mappers trying to indicate that there is no additional
            // sign apart from the general speed limit.
            return MAXSPEED_NONE;

        int mpInteger = str.indexOf("mp");
        int knotInteger = str.indexOf("knots");
        int kmInteger = str.indexOf("km");
        int kphInteger = str.indexOf("kph");

        double factor;
        if (mpInteger > 0) {
            str = str.substring(0, mpInteger).trim();
            factor = DistanceCalcEarth.KM_MILE;
        } else if (knotInteger > 0) {
            str = str.substring(0, knotInteger).trim();
            factor = 1.852; // see https://en.wikipedia.org/wiki/Knot_%28unit%29#Definitions
        } else {
            if (kmInteger > 0) {
                str = str.substring(0, kmInteger).trim();
            } else if (kphInteger > 0) {
                str = str.substring(0, kphInteger).trim();
            }
            factor = 1;
        }

        double value;
        try {
            value = Double.parseDouble(str) * factor;
        } catch (Exception ex) {
            return MAXSPEED_MISSING;
        }

        if (value < 4.8)
            // We consider maxspeed < 4.8km/h a bug in OSM data and act as if the tag wasn't there.
            // The limit is chosen such that maxspeed=3mph is still valid, because there actually are
            // some road signs using 3mph.
            // https://github.com/graphhopper/graphhopper/pull/3077#discussion_r1826842203
            return MAXSPEED_MISSING;

        return value;
    }
}
