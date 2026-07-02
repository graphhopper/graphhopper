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
package com.graphhopper.routing.util.parsers

import com.graphhopper.reader.ReaderWay
import com.graphhopper.routing.ev.DecimalEncodedValue
import com.graphhopper.routing.ev.EdgeIntAccess
import com.graphhopper.routing.ev.MaxSpeed
import com.graphhopper.routing.ev.MaxSpeed.MAXSPEED_150
import com.graphhopper.routing.ev.MaxSpeed.MAXSPEED_MISSING
import com.graphhopper.storage.IntsRef
import com.graphhopper.util.DistanceCalcEarth
import com.graphhopper.util.Helper
import kotlin.math.min

class OSMMaxSpeedParser(private val carMaxSpeedEnc: DecimalEncodedValue) : TagParser {

    init {
        if (!carMaxSpeedEnc.isStoreTwoDirections)
            throw IllegalArgumentException("EncodedValue for maxSpeed must be able to store two directions")
    }

    override fun handleWayTags(edgeId: Int, edgeIntAccess: EdgeIntAccess, way: ReaderWay, relationFlags: IntsRef?) {
        carMaxSpeedEnc.setDecimal(false, edgeId, edgeIntAccess, parseMaxSpeed(way, false))
        carMaxSpeedEnc.setDecimal(true, edgeId, edgeIntAccess, parseMaxSpeed(way, true))
    }

    companion object {
        /**
         * Special value to represent `maxspeed=none` internally, not exposed via the maxspeed encoded value
         */
        const val MAXSPEED_NONE: Double = -1.0

        /**
         * @return The maxspeed for the given way. It can be anything between 0 and [MaxSpeed.MAXSPEED_150],
         *         or [MaxSpeed.MAXSPEED_MISSING] in case there is no valid maxspeed tagged for this way in this direction.
         */
        @JvmStatic
        fun parseMaxSpeed(way: ReaderWay, reverse: Boolean): Double {
            val directedMaxSpeed = parseMaxSpeedTag(way, if (reverse) "maxspeed:backward" else "maxspeed:forward")
            return if (directedMaxSpeed != MAXSPEED_MISSING)
                directedMaxSpeed
            else
                parseMaxSpeedTag(way, "maxspeed")
        }

        private fun parseMaxSpeedTag(way: ReaderWay, tag: String): Double {
            val maxSpeed = parseMaxspeedString(way.getTag(tag))
            return if (maxSpeed != MAXSPEED_MISSING && maxSpeed != MAXSPEED_NONE)
                // there is no actual use for maxspeeds above 150 so we simply truncate here
                min(MAXSPEED_150, maxSpeed)
            else if (maxSpeed == MAXSPEED_NONE && way.hasTag("highway", "motorway", "motorway_link", "trunk", "trunk_link", "primary"))
                // We ignore maxspeed=none with some exceptions where unlimited speed is actually allowed like on some
                // motorways, trunks and (very rarely) primary roads in Germany, or the Isle of Man. In other cases
                // maxspeed=none is only used because mappers have a false understanding of this tag.
                MAXSPEED_150
            else
                MAXSPEED_MISSING
        }

        /**
         * @return the speed in km/h, or [MaxSpeed.MAXSPEED_MISSING] if the string is invalid, or [MAXSPEED_NONE] in case it equals 'none'
         */
        @JvmStatic
        fun parseMaxspeedString(str: String?): Double {
            if (Helper.isEmpty(str))
                return MAXSPEED_MISSING
            var s = str!!

            if ("walk" == s.trim())
                return 6.0

            if ("none" == s.trim())
                // Special case intended to be used when there is actually no speed limit and drivers
                // can go as fast as they want like on parts of the German Autobahn. However, in OSM
                // this is sometimes misused by mappers trying to indicate that there is no additional
                // sign apart from the general speed limit.
                return MAXSPEED_NONE

            val mpInteger = s.indexOf("mp")
            val knotInteger = s.indexOf("knots")
            val kmInteger = s.indexOf("km")
            val kphInteger = s.indexOf("kph")

            val factor: Double
            if (mpInteger > 0) {
                s = s.substring(0, mpInteger).trim()
                factor = DistanceCalcEarth.KM_MILE
            } else if (knotInteger > 0) {
                s = s.substring(0, knotInteger).trim()
                factor = 1.852 // see https://en.wikipedia.org/wiki/Knot_%28unit%29#Definitions
            } else {
                if (kmInteger > 0) {
                    s = s.substring(0, kmInteger).trim()
                } else if (kphInteger > 0) {
                    s = s.substring(0, kphInteger).trim()
                }
                factor = 1.0
            }

            val value: Double
            try {
                value = s.toDouble() * factor
            } catch (ex: Exception) {
                return MAXSPEED_MISSING
            }

            if (value < 4.8)
                // We consider maxspeed < 4.8km/h a bug in OSM data and act as if the tag wasn't there.
                // The limit is chosen such that maxspeed=3mph is still valid, because there actually are
                // some road signs using 3mph.
                // https://github.com/graphhopper/graphhopper/pull/3077#discussion_r1826842203
                return MAXSPEED_MISSING

            return value
        }
    }
}
