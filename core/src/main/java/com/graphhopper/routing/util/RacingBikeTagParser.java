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
import com.graphhopper.routing.ev.*;
import com.graphhopper.util.PMap;

import java.util.TreeMap;

import static com.graphhopper.routing.ev.RouteNetwork.*;
import static com.graphhopper.routing.util.PriorityCode.*;

/**
 * Specifies the settings for race biking
 *
 * @author ratrun
 * @author Peter Karich
 */
public class RacingBikeTagParser extends BikeCommonTagParser {

    public RacingBikeTagParser(EncodedValueLookup lookup, PMap properties) {
        this(
                lookup.getBooleanEncodedValue(VehicleAccess.key("racingbike")),
                lookup.getDecimalEncodedValue(VehicleSpeed.key("racingbike")),
                lookup.getDecimalEncodedValue(VehiclePriority.key("racingbike")),
                lookup.getEnumEncodedValue(BikeNetwork.KEY, RouteNetwork.class),
                lookup.getEnumEncodedValue(Smoothness.KEY, Smoothness.class),
                lookup.getBooleanEncodedValue(Roundabout.KEY)
        );
        blockPrivate(properties.getBool("block_private", true));
        blockFords(properties.getBool("block_fords", false));
    }

    protected RacingBikeTagParser(BooleanEncodedValue accessEnc, DecimalEncodedValue speedEnc, DecimalEncodedValue priorityEnc,
                                  EnumEncodedValue<RouteNetwork> bikeRouteEnc, EnumEncodedValue<Smoothness> smoothnessEnc,
                                  BooleanEncodedValue roundaboutEnc) {
        super(accessEnc, speedEnc, priorityEnc, bikeRouteEnc, smoothnessEnc, roundaboutEnc);
        preferHighwayTags.add("road");
        preferHighwayTags.add("secondary");
        preferHighwayTags.add("secondary_link");
        preferHighwayTags.add("tertiary");
        preferHighwayTags.add("tertiary_link");
        preferHighwayTags.add("residential");

        setTrackTypeSpeed("grade1", 20); // paved
        setTrackTypeSpeed("grade2", 10); // now unpaved ...
        setTrackTypeSpeed("grade3", PUSHING_SECTION_SPEED);
        setTrackTypeSpeed("grade4", PUSHING_SECTION_SPEED);
        setTrackTypeSpeed("grade5", PUSHING_SECTION_SPEED);

        setSurfaceSpeed("paved", 20);
        setSurfaceSpeed("asphalt", 20);
        setSurfaceSpeed("concrete", 20);
        setSurfaceSpeed("concrete:lanes", 16);
        setSurfaceSpeed("concrete:plates", 16);
        setSurfaceSpeed("unpaved", MIN_SPEED);
        setSurfaceSpeed("compacted", MIN_SPEED);
        setSurfaceSpeed("dirt", MIN_SPEED);
        setSurfaceSpeed("earth", MIN_SPEED);
        setSurfaceSpeed("fine_gravel", PUSHING_SECTION_SPEED);
        setSurfaceSpeed("grass", MIN_SPEED);
        setSurfaceSpeed("grass_paver", MIN_SPEED);
        setSurfaceSpeed("gravel", MIN_SPEED);
        setSurfaceSpeed("ground", MIN_SPEED);
        setSurfaceSpeed("ice", MIN_SPEED);
        setSurfaceSpeed("metal", MIN_SPEED);
        setSurfaceSpeed("mud", MIN_SPEED);
        setSurfaceSpeed("pebblestone", PUSHING_SECTION_SPEED);
        setSurfaceSpeed("salt", MIN_SPEED);
        setSurfaceSpeed("sand", MIN_SPEED);
        setSurfaceSpeed("wood", MIN_SPEED);

        setHighwaySpeed("path", 8);
        setHighwaySpeed("footway", PUSHING_SECTION_SPEED);
        setHighwaySpeed("pedestrian", PUSHING_SECTION_SPEED);
        setHighwaySpeed("track", MIN_SPEED); // assume unpaved
        setHighwaySpeed("service", 12);
        setHighwaySpeed("unclassified", 16);
        setHighwaySpeed("residential", 16);

        setHighwaySpeed("trunk", 20);
        setHighwaySpeed("trunk_link", 20);
        setHighwaySpeed("primary", 20);
        setHighwaySpeed("primary_link", 20);
        setHighwaySpeed("secondary", 20);
        setHighwaySpeed("secondary_link", 20);
        setHighwaySpeed("tertiary", 20);
        setHighwaySpeed("tertiary_link", 20);

        addPushingSection("path");
        addPushingSection("footway");
        addPushingSection("platform");
        addPushingSection("pedestrian");
        addPushingSection("steps");

        // overwite map from BikeCommon
        setSmoothnessSpeedFactor(com.graphhopper.routing.ev.Smoothness.EXCELLENT, 1.2d);
        setSmoothnessSpeedFactor(com.graphhopper.routing.ev.Smoothness.VERY_BAD, 0.1);
        setSmoothnessSpeedFactor(com.graphhopper.routing.ev.Smoothness.HORRIBLE, 0.1);
        setSmoothnessSpeedFactor(com.graphhopper.routing.ev.Smoothness.VERY_HORRIBLE, 0.1);

        routeMap.put(INTERNATIONAL, BEST.getValue());
        routeMap.put(NATIONAL, BEST.getValue());
        routeMap.put(REGIONAL, VERY_NICE.getValue());
        routeMap.put(LOCAL, UNCHANGED.getValue());

        barriers.add("kissing_gate");
        barriers.add("stile");
        barriers.add("turnstile");

        setAvoidSpeedLimit(81);
        setSpecificClassBicycle("roadcycling");
    }

    @Override
    void collect(ReaderWay way, double wayTypeSpeed, TreeMap<Double, Integer> weightToPrioMap) {
        super.collect(way, wayTypeSpeed, weightToPrioMap);

        String highway = way.getTag("highway");
        if ("service".equals(highway)) {
            weightToPrioMap.put(40d, UNCHANGED.getValue());
        } else if ("track".equals(highway)) {
            String trackType = way.getTag("tracktype");
            if ("grade1".equals(trackType))
                weightToPrioMap.put(110d, PREFER.getValue());
            else if (trackType == null || trackType.startsWith("grade"))
                weightToPrioMap.put(110d, AVOID_MORE.getValue());
        }
    }
}
