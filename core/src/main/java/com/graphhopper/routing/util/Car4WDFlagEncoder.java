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
package com.graphhopper.routing.util;

import com.graphhopper.reader.ReaderWay;
import com.graphhopper.util.StringConfigMap;

/**
 * Defines bit layout for cars with four wheel drive
 *
 * @author zstadler
 */
public class Car4WDFlagEncoder extends CarFlagEncoder {

    public Car4WDFlagEncoder() {
        this(5, 5, 0);
    }

    public Car4WDFlagEncoder(StringConfigMap properties) {
        super(properties);
    }

    public Car4WDFlagEncoder(String propertiesStr) {
        this(StringConfigMap.create(propertiesStr));
    }

    public Car4WDFlagEncoder(int speedBits, double speedFactor, int maxTurnCosts) {
        super(speedBits, speedFactor, maxTurnCosts);

        init();

        trackTypeSpeedMap.put("grade4", 5); // ... some hard or compressed materials
        trackTypeSpeedMap.put("grade5", 5); // ... no hard materials. soil/sand/grass
    }

    @Override
    public int getVersion() {
        return 1;
    }

    @Override
    public long acceptWay(ReaderWay way) {
        // TODO: Ferries have conditionals, like opening hours or are closed during some time in the year
        String highwayValue = way.getTag("highway");
        String firstValue = way.getFirstPriorityTag(restrictions);
        if (highwayValue == null) {
            if (way.hasTag("route", ferries)) {
                if (restrictedValues.contains(firstValue))
                    return 0;
                if (intendedValues.contains(firstValue) ||
                        // implied default is allowed only if foot and bicycle is not specified:
                        firstValue.isEmpty() && !way.hasTag("foot") && !way.hasTag("bicycle"))
                    return acceptBit | ferryBit;
            }
            return 0;
        }

        if (!defaultSpeedMap.containsKey(highwayValue))
            return 0;

        if (way.hasTag("impassable", "yes") || way.hasTag("status", "impassable"))
            return 0;

        // multiple restrictions needs special handling compared to foot and bike, see also motorcycle
        if (!firstValue.isEmpty()) {
            if (restrictedValues.contains(firstValue) && !getConditionalTagInspector().isRestrictedWayConditionallyPermitted(way))
                return 0;
            if (intendedValues.contains(firstValue))
                return acceptBit;
        }

        // do not drive street cars into fords
        if (isBlockFords() && ("ford".equals(highwayValue) || way.hasTag("ford")))
            return 0;

        if (getConditionalTagInspector().isPermittedWayConditionallyRestricted(way))
            return 0;
        else
            return acceptBit;
    }

    @Override
    public String toString() {
        return "car4wd";
    }
}
