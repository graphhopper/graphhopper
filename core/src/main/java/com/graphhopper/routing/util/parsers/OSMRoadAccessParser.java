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
import com.graphhopper.routing.ev.*;
import com.graphhopper.routing.util.TransportationMode;
import com.graphhopper.storage.IntsRef;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

public class OSMRoadAccessParser<T extends Enum> implements TagParser {
    protected final EnumEncodedValue<T> accessEnc;
    private final List<String> restrictions;
    private final Function<String, T> valueFinder;
    private final RoadAccessDefaultHandler<T> roadAccessDefaultHandler;

    public OSMRoadAccessParser(EnumEncodedValue<T> accessEnc, List<String> restrictions,
                               RoadAccessDefaultHandler<T> roadAccessDefaultHandler,
                               Function<String, T> valueFinder) {
        this.accessEnc = accessEnc;
        this.restrictions = restrictions;
        this.valueFinder = valueFinder;
        this.roadAccessDefaultHandler = roadAccessDefaultHandler;
    }

    @Override
    public void handleWayTags(int edgeId, EdgeIntAccess edgeIntAccess, ReaderWay readerWay, IntsRef relationFlags) {
        T accessValue = null;

        List<Map<String, Object>> nodeTags = readerWay.getTag("node_tags", Collections.emptyList());
        // a barrier edge has the restriction in both nodes and the tags are the same
        if (readerWay.hasTag("gh:barrier_edge"))
            for (String restriction : restrictions) {
                Object value = nodeTags.get(0).get(restriction);
                accessValue = getRoadAccess((String) value, accessValue);
                if (accessValue != null) break;
            }

        for (String restriction : restrictions) {
            accessValue = getRoadAccess(readerWay.getTag(restriction), accessValue);
            if (accessValue != null) break;
        }

        if (accessValue == null) {
            Country country = readerWay.getTag("country", Country.MISSING);
            accessValue = roadAccessDefaultHandler.getAccess(readerWay, country);
        }

        if (accessValue != null)
            accessEnc.setEnum(false, edgeId, edgeIntAccess, accessValue);
    }

    private T getRoadAccess(String tagValue, T accessValue) {
        T tmpAccessValue;
        if (tagValue != null) {
            String[] complex = tagValue.split(";");
            for (String simple : complex) {
                tmpAccessValue = valueFinder.apply(simple);
                if (tmpAccessValue == null) continue;
                if (accessValue == null || tmpAccessValue.ordinal() < accessValue.ordinal()) {
                    accessValue = tmpAccessValue;
                }
            }
        }
        return accessValue;
    }

    public interface RoadAccessDefaultHandler<T> {
        T getAccess(ReaderWay readerWay, Country country);
    }

    public static RoadAccessDefaultHandler<RoadAccess> CAR_HANDLER = (ReaderWay readerWay, Country country) -> {
        RoadClass roadClass = RoadClass.find(readerWay.getTag("highway", ""));
        return switch (country) {
            case AUT -> switch (roadClass) {
                case LIVING_STREET -> RoadAccess.DESTINATION;
                case TRACK -> RoadAccess.FORESTRY;
                case PATH, BRIDLEWAY, CYCLEWAY, FOOTWAY, PEDESTRIAN -> RoadAccess.NO;
                default -> RoadAccess.YES;
            };
            case DEU -> switch (roadClass) {
                case TRACK -> RoadAccess.DESTINATION;
                case PATH, BRIDLEWAY, CYCLEWAY, FOOTWAY, PEDESTRIAN -> RoadAccess.NO;
                default -> RoadAccess.YES;
            };
            case HUN -> {
                if (roadClass == RoadClass.LIVING_STREET) yield RoadAccess.DESTINATION;
                yield RoadAccess.YES;
            }
            default -> null;
        };
    };

    public static RoadAccessDefaultHandler<FootRoadAccess> FOOT_HANDLER = (readerWay, country) -> null;

    public static RoadAccessDefaultHandler<BikeRoadAccess> BIKE_HANDLER = (readerWay, country) -> null;

    public static List<String> toOSMRestrictions(TransportationMode mode) {
        return switch (mode) {
            case FOOT -> Arrays.asList("foot", "access");
            case VEHICLE -> Arrays.asList("vehicle", "access");
            case BIKE -> Arrays.asList("bicycle", "vehicle", "access");
            case CAR -> Arrays.asList("motorcar", "motor_vehicle", "vehicle", "access");
            case MOTORCYCLE -> Arrays.asList("motorcycle", "motor_vehicle", "vehicle", "access");
            case HGV -> Arrays.asList("hgv", "motor_vehicle", "vehicle", "access");
            case PSV -> Arrays.asList("psv", "motor_vehicle", "vehicle", "access");
            case BUS -> Arrays.asList("bus", "psv", "motor_vehicle", "vehicle", "access");
            case HOV -> Arrays.asList("hov", "motor_vehicle", "vehicle", "access");
            default ->
                    throw new IllegalArgumentException("Cannot convert TransportationMode " + mode + " to list of restrictions");
        };
    }
}
