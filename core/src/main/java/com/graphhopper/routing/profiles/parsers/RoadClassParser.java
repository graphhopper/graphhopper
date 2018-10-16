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
package com.graphhopper.routing.profiles.parsers;

import com.graphhopper.reader.ReaderWay;
import com.graphhopper.routing.profiles.EnumEncodedValue;
import com.graphhopper.routing.profiles.RoadClass;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.storage.IntsRef;

import java.util.Arrays;
import java.util.List;

/**
 * Stores the class of the road like motorway or primary. Previously called "highway" in DataFlagEncoder.
 */
public class RoadClassParser extends AbstractTagParser {

    private static final List<String> FERRIES = Arrays.asList("shuttle_train", "ferry");

    private final EnumEncodedValue enc;

    public RoadClassParser() {
        super(EncodingManager.ROAD_CLASS);
        enc = RoadClass.create();
    }

    public EnumEncodedValue<RoadClass> getEnc() {
        return enc;
    }

    @Override
    public IntsRef handleWayTags(IntsRef edgeFlags, ReaderWay way, long allowed, long relationFlags) {
        int hwValue = getHighwayValue(way);
        if (hwValue == 0)
            return edgeFlags;

        enc.setEnum(false, edgeFlags, enc.getEnums()[hwValue]);
        return edgeFlags;
    }

    /**
     * This method converts the specified way into a storable integer value.
     *
     * @return 0 for default
     */
    public int getHighwayValue(ReaderWay way) {
        String highwayValue = way.getTag("highway");
        int hwValue = enc.indexOf(highwayValue);
        if (way.hasTag("impassable", "yes") || way.hasTag("status", "impassable")) {
            hwValue = 0;
        } else if (hwValue == 0) {
            if (way.hasTag("route", FERRIES)) {
                String ferryValue = way.getFirstPriorityTag(FERRIES);
                if (ferryValue == null)
                    ferryValue = "service";
                hwValue = enc.indexOf(ferryValue);
            }
        }
        return hwValue;
    }
}
