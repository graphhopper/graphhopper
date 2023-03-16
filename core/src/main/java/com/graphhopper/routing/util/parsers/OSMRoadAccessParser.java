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
import java.util.Collections;
import java.util.List;
import java.util.Map;

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

        List<Map<String, Object>> nodeTags = readerWay.getTag("node_tags", Collections.emptyList());
        // a barrier edge has the restriction in both nodes and the tags are the same
        if (readerWay.hasTag("gh:barrier_edge"))
            for (String restriction : restrictions) {
                Object value = nodeTags.get(0).get(restriction);
                if (value != null) accessValue = getRoadAccess((String) value, accessValue);
            }

        for (String restriction : restrictions) {
            accessValue = getRoadAccess(readerWay.getTag(restriction), accessValue);
        }

        CountryRule countryRule = readerWay.getTag("country_rule", null);
        if (countryRule != null)
            accessValue = countryRule.getAccess(readerWay, TransportationMode.CAR, accessValue);

        roadAccessEnc.setEnum(false, edgeFlags, accessValue);
    }

    private RoadAccess getRoadAccess(String tagValue, RoadAccess accessValue) {
        RoadAccess tmpAccessValue;
        if (tagValue != null) {
            String[] complex = tagValue.split(";");
            for (String simple : complex) {
                tmpAccessValue = RoadAccess.find(simple);
                if (tmpAccessValue != null && tmpAccessValue.ordinal() > accessValue.ordinal()) {
                    accessValue = tmpAccessValue;
                }
            }
        }
        return accessValue;
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
