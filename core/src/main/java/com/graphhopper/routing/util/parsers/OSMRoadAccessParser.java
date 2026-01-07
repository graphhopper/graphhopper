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

    @FunctionalInterface
    public interface RoadAccessDefaultHandler<T> {
        T getAccess(ReaderWay readerWay, Country country);
    }

    public static RoadClass getRoadClass(ReaderWay readerWay) {
        String hw = readerWay.getTag("highway", "");
        return RoadClass.find(hw.endsWith("_link") ? hw.substring(0, hw.length() - 5) : hw);
    }

    public static RoadAccessDefaultHandler<RoadAccess> CAR_HANDLER = (ReaderWay readerWay, Country country) -> {
        RoadClass roadClass = getRoadClass(readerWay);
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

    // Based on https://wiki.openstreetmap.org/wiki/OSM_tags_for_routing/Access_restrictions
    // The motorroad tag is handled in FootAccessParser and BikeCommonAccessParser via always skipping.
    // See https://wiki.openstreetmap.org/wiki/Tag:motorroad%3Dyes
    public static RoadAccessDefaultHandler<FootRoadAccess> FOOT_HANDLER = (readerWay, country) -> {
        RoadClass roadClass = getRoadClass(readerWay);
        switch (country) {
            case AUT, CHE, HRV, SVK, FRA -> {
                if (roadClass == RoadClass.TRUNK || roadClass == RoadClass.BRIDLEWAY)
                    return FootRoadAccess.NO;
            }
            case BEL -> {
                if (roadClass == RoadClass.TRUNK /* foot=no implied for highway=trunk without motorroad=yes? */ || roadClass == RoadClass.BUSWAY)
                    return FootRoadAccess.NO;
                else if (roadClass == RoadClass.CYCLEWAY)
                    return FootRoadAccess.YES;
            }
            case BLR, RUS, DEU, ESP, UKR -> {
                if (roadClass == RoadClass.BRIDLEWAY) return FootRoadAccess.NO;
            }
            case BRA -> {
                if (roadClass == RoadClass.BUSWAY) return FootRoadAccess.NO;
            }
            case CHN -> {
                if (roadClass == RoadClass.CYCLEWAY) return FootRoadAccess.YES;
                else if (roadClass == RoadClass.BRIDLEWAY) return FootRoadAccess.NO;
            }
            case DNK -> {
                if (roadClass == RoadClass.TRUNK || roadClass == RoadClass.BRIDLEWAY)
                    return FootRoadAccess.NO;
                else if (roadClass == RoadClass.CYCLEWAY)
                    return FootRoadAccess.YES;
            }
            case FIN -> {
                if (roadClass == RoadClass.BRIDLEWAY) return FootRoadAccess.NO;
                else if (roadClass == RoadClass.CYCLEWAY) return FootRoadAccess.YES;
            }
            case GBR, GRC, ISL, PHL, THA, USA, NOR -> {
                if (roadClass == RoadClass.CYCLEWAY) return FootRoadAccess.YES;
            }
            case HUN -> {
                if (roadClass == RoadClass.TRUNK || roadClass == RoadClass.BRIDLEWAY)
                    return FootRoadAccess.NO;
                else if (roadClass == RoadClass.CYCLEWAY)
                    return FootRoadAccess.YES;
            }
            case NLD -> {
                if (roadClass == RoadClass.BUSWAY
                        || roadClass == RoadClass.BRIDLEWAY) return FootRoadAccess.NO;
                else if (roadClass == RoadClass.CYCLEWAY) return FootRoadAccess.YES;
            }
            case OMN -> {
                if (roadClass == RoadClass.CYCLEWAY) return FootRoadAccess.DESIGNATED;
            }
            case SWE -> {
                if (roadClass == RoadClass.BUSWAY) return FootRoadAccess.NO;
                else if (roadClass == RoadClass.CYCLEWAY) return FootRoadAccess.YES;
            }
        }
        return null;
    };

    public static RoadAccessDefaultHandler<BikeRoadAccess> BIKE_HANDLER = (ReaderWay readerWay, Country country) -> {
        RoadClass roadClass = getRoadClass(readerWay);
        switch (country) {
            case AUT, HRV -> {
                if (roadClass == RoadClass.TRUNK || roadClass == RoadClass.BRIDLEWAY)
                    return BikeRoadAccess.NO;
            }
            case BEL -> {
                if (roadClass == RoadClass.TRUNK /* bicycle=no implied for highway=trunk without motorroad=yes? */
                        || roadClass == RoadClass.BUSWAY
                        || roadClass == RoadClass.BRIDLEWAY
                        || roadClass == RoadClass.FOOTWAY) return BikeRoadAccess.NO;
                else if (roadClass == RoadClass.PEDESTRIAN) return BikeRoadAccess.YES;
            }
            case BLR -> {
                if (roadClass == RoadClass.BRIDLEWAY) return BikeRoadAccess.NO;
                else if (roadClass == RoadClass.PEDESTRIAN) return BikeRoadAccess.DESIGNATED;
                else if (roadClass == RoadClass.FOOTWAY) return BikeRoadAccess.YES;
            }
            case BRA -> {
                if (roadClass == RoadClass.BUSWAY) return BikeRoadAccess.NO;
            }
            case CHE, DNK, SVK -> {
                if (roadClass == RoadClass.TRUNK
                        || roadClass == RoadClass.BRIDLEWAY
                        || roadClass == RoadClass.PEDESTRIAN
                        || roadClass == RoadClass.FOOTWAY) return BikeRoadAccess.NO;
            }
            case CHN -> {
                if (roadClass == RoadClass.BRIDLEWAY) return BikeRoadAccess.NO;
                else if (roadClass == RoadClass.PEDESTRIAN) return BikeRoadAccess.YES;
            }
            case DEU, TUR, RUS, UKR -> {
                if (roadClass == RoadClass.BRIDLEWAY) return BikeRoadAccess.NO;
            }
            case ESP -> {
                if (roadClass == RoadClass.BRIDLEWAY) return BikeRoadAccess.NO;
                else if (roadClass == RoadClass.PEDESTRIAN) return BikeRoadAccess.YES;
            }
            case FIN -> {
                if (roadClass == RoadClass.FOOTWAY || roadClass == RoadClass.BRIDLEWAY)
                    return BikeRoadAccess.NO;
                else if (roadClass == RoadClass.PEDESTRIAN) return BikeRoadAccess.YES;
            }
            case FRA -> {
                if (roadClass == RoadClass.TRUNK
                        || roadClass == RoadClass.BRIDLEWAY) return BikeRoadAccess.NO;
                else if (roadClass == RoadClass.PEDESTRIAN) return BikeRoadAccess.YES;
            }
            case GRC, GBR, HKG, IRL -> {
                if (roadClass == RoadClass.PEDESTRIAN
                        || roadClass == RoadClass.FOOTWAY) return BikeRoadAccess.NO;
            }
            case HUN -> {
                if (roadClass == RoadClass.TRUNK
                        || roadClass == RoadClass.BRIDLEWAY
                        || roadClass == RoadClass.PEDESTRIAN) return BikeRoadAccess.NO;
            }
            case ISL, NOR -> {
                if (roadClass == RoadClass.PEDESTRIAN
                        || roadClass == RoadClass.FOOTWAY) return BikeRoadAccess.YES;
            }
            case ITA -> {
                if (roadClass == RoadClass.FOOTWAY) return BikeRoadAccess.NO;
                else if (roadClass == RoadClass.PEDESTRIAN) return BikeRoadAccess.YES;
            }
            case NLD -> {
                if (roadClass == RoadClass.BUSWAY
                        || roadClass == RoadClass.BRIDLEWAY) return BikeRoadAccess.NO;
            }
            case OMN -> {
                if (roadClass == RoadClass.MOTORWAY) return BikeRoadAccess.YES;
                else if (roadClass == RoadClass.PEDESTRIAN || roadClass == RoadClass.FOOTWAY)
                    return BikeRoadAccess.NO;
            }
            case PHL, THA, USA, SWE -> {
                if (roadClass == RoadClass.PEDESTRIAN) return BikeRoadAccess.YES;
            }
        }
        return null;
    };

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

    public static OSMRoadAccessParser<RoadAccess> forCar(EnumEncodedValue<RoadAccess> roadAccessEnc) {
        return new OSMRoadAccessParser<>(roadAccessEnc, toOSMRestrictions(TransportationMode.CAR), CAR_HANDLER, RoadAccess::find);
    }

    public static OSMRoadAccessParser<BikeRoadAccess> forBike(EnumEncodedValue<BikeRoadAccess> roadAccessEnc) {
        return new OSMRoadAccessParser<>(roadAccessEnc, toOSMRestrictions(TransportationMode.BIKE), BIKE_HANDLER, BikeRoadAccess::find);
    }

    public static OSMRoadAccessParser<FootRoadAccess> forFoot(EnumEncodedValue<FootRoadAccess> roadAccessEnc) {
        return new OSMRoadAccessParser<>(roadAccessEnc, toOSMRestrictions(TransportationMode.FOOT), FOOT_HANDLER, FootRoadAccess::find);
    }
}
