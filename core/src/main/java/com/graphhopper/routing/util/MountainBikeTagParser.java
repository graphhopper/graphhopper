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
 * Specifies the settings for mountain biking
 * <p>
 *
 * @author ratrun
 * @author Peter Karich
 */
public class MountainBikeTagParser extends BikeCommonTagParser {

    public MountainBikeTagParser(EncodedValueLookup lookup, PMap properties) {
        this(
                lookup.getBooleanEncodedValue(VehicleAccess.key("mtb")),
                lookup.getDecimalEncodedValue(VehicleSpeed.key("mtb")),
                lookup.getDecimalEncodedValue(VehiclePriority.key("mtb")),
                lookup.getEnumEncodedValue(BikeNetwork.KEY, RouteNetwork.class),
                lookup.getEnumEncodedValue(Smoothness.KEY, Smoothness.class),
                lookup.getBooleanEncodedValue(Roundabout.KEY),
                lookup.hasEncodedValue(TurnCost.key("mtb")) ? lookup.getDecimalEncodedValue(TurnCost.key("mtb")) : null
        );
        blockPrivate(properties.getBool("block_private", true));
        blockFords(properties.getBool("block_fords", false));
    }

    protected MountainBikeTagParser(BooleanEncodedValue accessEnc, DecimalEncodedValue speedEnc, DecimalEncodedValue priorityEnc,
                                    EnumEncodedValue<RouteNetwork> bikeRouteEnc, EnumEncodedValue<Smoothness> smoothnessEnc,
                                    BooleanEncodedValue roundaboutEnc,
                                    DecimalEncodedValue turnCostEnc) {
        super(accessEnc, speedEnc, priorityEnc, bikeRouteEnc, smoothnessEnc, "mtb", roundaboutEnc, turnCostEnc);
        setTrackTypeSpeed("grade1", 18); // paved
        setTrackTypeSpeed("grade2", 16); // now unpaved ...
        setTrackTypeSpeed("grade3", 12);
        setTrackTypeSpeed("grade4", 8);
        setTrackTypeSpeed("grade5", 6); // like sand/grass     

        setSurfaceSpeed("concrete", 14);
        setSurfaceSpeed("concrete:lanes", 16);
        setSurfaceSpeed("concrete:plates", 16);
        setSurfaceSpeed("dirt", 14);
        setSurfaceSpeed("earth", 14);
        setSurfaceSpeed("fine_gravel", 18);
        setSurfaceSpeed("grass", 14);
        setSurfaceSpeed("grass_paver", 14);
        setSurfaceSpeed("gravel", 16);
        setSurfaceSpeed("ground", 16);
        setSurfaceSpeed("ice", MIN_SPEED);
        setSurfaceSpeed("metal", 10);
        setSurfaceSpeed("mud", 12);
        setSurfaceSpeed("salt", 12);
        setSurfaceSpeed("sand", 10);
        setSurfaceSpeed("wood", 10);

        setHighwaySpeed("living_street", PUSHING_SECTION_SPEED);
        setHighwaySpeed("steps", PUSHING_SECTION_SPEED);

        setHighwaySpeed("path", 18);
        setHighwaySpeed("footway", PUSHING_SECTION_SPEED);
        setHighwaySpeed("pedestrian", PUSHING_SECTION_SPEED);
        setHighwaySpeed("track", 18);
        setHighwaySpeed("residential", 16);

        addPushingSection("footway");
        addPushingSection("platform");
        addPushingSection("pedestrian");
        addPushingSection("steps");

        routeMap.put(INTERNATIONAL, PREFER.getValue());
        routeMap.put(NATIONAL, PREFER.getValue());
        routeMap.put(REGIONAL, PREFER.getValue());
        routeMap.put(LOCAL, BEST.getValue());

        avoidHighwayTags.add("primary");
        avoidHighwayTags.add("primary_link");
        avoidHighwayTags.add("secondary");
        avoidHighwayTags.add("secondary_link");

        preferHighwayTags.add("road");
        preferHighwayTags.add("track");
        preferHighwayTags.add("path");
        preferHighwayTags.add("service");
        preferHighwayTags.add("tertiary");
        preferHighwayTags.add("tertiary_link");
        preferHighwayTags.add("residential");
        preferHighwayTags.add("unclassified");

        setSpecificClassBicycle("mtb");
    }

    @Override
    void collect(ReaderWay way, double wayTypeSpeed, TreeMap<Double, Integer> weightToPrioMap) {
        super.collect(way, wayTypeSpeed, weightToPrioMap);

        String highway = way.getTag("highway");
        if ("track".equals(highway)) {
            String trackType = way.getTag("tracktype");
            if ("grade1".equals(trackType))
                weightToPrioMap.put(50d, UNCHANGED.getValue());
            else if (trackType == null)
                weightToPrioMap.put(90d, PREFER.getValue());
            else if (trackType.startsWith("grade"))
                weightToPrioMap.put(100d, VERY_NICE.getValue());
        }
    }

    @Override
    boolean isSacScaleAllowed(String sacScale) {
        // other scales are too dangerous even for MTB, see http://wiki.openstreetmap.org/wiki/Key:sac_scale
        return "hiking".equals(sacScale) || "mountain_hiking".equals(sacScale)
                || "demanding_mountain_hiking".equals(sacScale) || "alpine_hiking".equals(sacScale);
    }
}
