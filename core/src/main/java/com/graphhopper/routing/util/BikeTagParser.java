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

import com.graphhopper.routing.ev.*;
import com.graphhopper.util.PMap;

/**
 * Specifies the settings for cycletouring/trekking
 *
 * @author ratrun
 * @author Peter Karich
 */
public class BikeTagParser extends BikeCommonTagParser {

    public BikeTagParser(EncodedValueLookup lookup, PMap properties) {
        this(
                lookup.getBooleanEncodedValue(VehicleAccess.key(properties.getString("name", "bike"))),
                lookup.getDecimalEncodedValue(VehicleSpeed.key(properties.getString("name", "bike"))),
                lookup.getDecimalEncodedValue(VehiclePriority.key(properties.getString("name", "bike"))),
                lookup.getEnumEncodedValue(BikeNetwork.KEY, RouteNetwork.class),
                lookup.getEnumEncodedValue(Smoothness.KEY, Smoothness.class),
                lookup.getBooleanEncodedValue(Roundabout.KEY)
        );
        blockPrivate(properties.getBool("block_private", true));
        blockFords(properties.getBool("block_fords", false));
    }

    public BikeTagParser(BooleanEncodedValue accessEnc, DecimalEncodedValue speedEnc, DecimalEncodedValue priorityEnc,
                         EnumEncodedValue<RouteNetwork> bikeRouteEnc, EnumEncodedValue<Smoothness> smoothnessEnc,
                         BooleanEncodedValue roundaboutEnc) {
        super(accessEnc, speedEnc, priorityEnc, bikeRouteEnc, smoothnessEnc, roundaboutEnc);
        addPushingSection("path");
        addPushingSection("footway");
        addPushingSection("pedestrian");
        addPushingSection("steps");
        addPushingSection("platform");

        avoidHighwayTags.add("trunk");
        avoidHighwayTags.add("trunk_link");
        avoidHighwayTags.add("primary");
        avoidHighwayTags.add("primary_link");
        avoidHighwayTags.add("secondary");
        avoidHighwayTags.add("secondary_link");

        // preferHighwayTags.add("road");
        preferHighwayTags.add("service");
        preferHighwayTags.add("tertiary");
        preferHighwayTags.add("tertiary_link");
        preferHighwayTags.add("residential");
        preferHighwayTags.add("unclassified");

        barriers.add("kissing_gate");
        barriers.add("stile");
        barriers.add("turnstile");

        setSpecificClassBicycle("touring");
    }
}
