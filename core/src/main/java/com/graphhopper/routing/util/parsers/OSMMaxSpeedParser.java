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
import com.graphhopper.routing.util.parsers.helpers.OSMValueExtractor;
import com.graphhopper.storage.IntsRef;

import java.io.Reader;

import static com.graphhopper.routing.ev.MaxSpeed.UNLIMITED_SIGN_SPEED;
import static com.graphhopper.routing.ev.MaxSpeed.UNSET_SPEED;

public class OSMMaxSpeedParser implements TagParser {

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
     * @return The maxspeed for the given way. It can be anything between 0 and {@link MaxSpeed.UNLIMITED_SIGN_SPEED},
     *         or {@link MaxSpeed.UNSET_SPEED} in case there is no valid maxspeed tagged for this way in this direction.
     */
    public static double parseMaxSpeed(ReaderWay way, boolean reverse) {
        double directedMaxSpeed = parseMaxSpeedTag(way, reverse ? "maxspeed:backward" : "maxspeed:forward");
        if (directedMaxSpeed != UNSET_SPEED)
            return directedMaxSpeed;
        else {
            return parseMaxSpeedTag(way, "maxspeed");
        }
    }

    private static double parseMaxSpeedTag(ReaderWay way, String tag) {
        double maxSpeed = OSMValueExtractor.stringToKmh(way.getTag(tag));
        if (maxSpeed != UNSET_SPEED && maxSpeed != OSMValueExtractor.MAXSPEED_NONE)
            // there is no actual use for maxspeeds above 150 so we simply truncate here
            return Math.min(UNLIMITED_SIGN_SPEED, maxSpeed);
        else if (maxSpeed == OSMValueExtractor.MAXSPEED_NONE && way.hasTag("highway", "motorway", "motorway_link", "trunk", "trunk_link"))
            return MaxSpeed.UNLIMITED_SIGN_SPEED;
        else
            return UNSET_SPEED;
    }

}
