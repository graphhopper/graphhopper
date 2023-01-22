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
import com.graphhopper.routing.ev.EnumEncodedValue;
import com.graphhopper.routing.ev.RoadAccess;
import com.graphhopper.routing.util.TransportationMode;
import com.graphhopper.routing.util.countryrules.CountryRule;
import com.graphhopper.storage.IntsRef;

import java.util.Arrays;
import java.util.List;

import static com.graphhopper.routing.ev.RoadAccess.YES;

public class OSMRoadAccessParser implements TagParser {
    protected final EnumEncodedValue<RoadAccess> roadAccessEnc;
    private final List<String> restrictions;

    public OSMRoadAccessParser(EnumEncodedValue<RoadAccess> roadAccessEnc, List<String> restrictions) {
        this.roadAccessEnc = roadAccessEnc;
        this.restrictions = restrictions;
    }

    @Override
    public void handleWayTags(IntsRef edgeFlags, ReaderWay readerWay, IntsRef relationFlags) {
        RoadAccess accessValue = YES;
        RoadAccess tmpAccessValue;
        for (String restriction : restrictions) {
            String tagValue = readerWay.getTag(restriction);
            if (tagValue != null) {
                String[] complex = tagValue.split(";");
                for (String simple : complex) {
                    tmpAccessValue = RoadAccess.find(simple);
                    if (tmpAccessValue != null && tmpAccessValue.ordinal() > accessValue.ordinal()) {
                        accessValue = tmpAccessValue;
                    }
                }
            }
        }

        CountryRule countryRule = readerWay.getTag("country_rule", null);
        if (countryRule != null)
            accessValue = countryRule.getAccess(readerWay, TransportationMode.CAR, accessValue);

        roadAccessEnc.setEnum(false, edgeFlags, accessValue);
    }

    public static List<String> toOSMRestrictions(TransportationMode mode) {
        switch (mode) {
            case FOOT:
                return Arrays.asList("foot", "access");
            case VEHICLE:
                return Arrays.asList("vehicle", "access");
            case BIKE:
                return Arrays.asList("bicycle", "vehicle", "access");
            case CAR:
                return Arrays.asList("motorcar", "motor_vehicle", "vehicle", "access");
            case MOTORCYCLE:
                return Arrays.asList("motorcycle", "motor_vehicle", "vehicle", "access");
            case HGV:
                return Arrays.asList("hgv", "motor_vehicle", "vehicle", "access");
            case PSV:
                return Arrays.asList("psv", "motor_vehicle", "vehicle", "access");
            case BUS:
                return Arrays.asList("bus", "psv", "motor_vehicle", "vehicle", "access");
            default:
                throw new IllegalArgumentException("Cannot convert TransportationMode " + mode + " to list of restrictions");
        }
    }
}
