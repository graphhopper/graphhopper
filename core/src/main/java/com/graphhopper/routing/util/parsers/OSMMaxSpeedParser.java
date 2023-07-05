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
        carMaxSpeedEnc.setDecimal(false, edgeId, edgeIntAccess, getMaxSpeed(way, false));
        carMaxSpeedEnc.setDecimal(true, edgeId, edgeIntAccess, getMaxSpeed(way, true));
    }

    private double getMaxSpeed(ReaderWay way, boolean reverse) {
        final double maxSpeed = OSMValueExtractor.stringToKmh(way.getTag("maxspeed"));
        final double directedMaxSpeed = OSMValueExtractor.stringToKmh(way.getTag(reverse ? "maxspeed:backward" : "maxspeed:forward"));
        return isValidSpeed(directedMaxSpeed)
                ? Math.min(directedMaxSpeed, MaxSpeed.UNLIMITED_SIGN_SPEED)
                : isValidSpeed(maxSpeed)
                ? Math.min(maxSpeed, MaxSpeed.UNLIMITED_SIGN_SPEED)
                : UNSET_SPEED;
    }

    /**
     * @return <i>true</i> if the given speed is not {@link Double#NaN}
     */
    private boolean isValidSpeed(double speed) {
        return !Double.isNaN(speed);
    }
}
