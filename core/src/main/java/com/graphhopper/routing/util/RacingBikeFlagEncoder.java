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
import com.graphhopper.util.PMap;

import java.util.TreeMap;

import static com.graphhopper.routing.util.PriorityCode.*;

/**
 * Specifies the settings for race biking
 * <p>
 *
 * @author ratrun
 * @author Peter Karich
 */
public class RacingBikeFlagEncoder extends BikeCommonFlagEncoder {
    public RacingBikeFlagEncoder() {
        this(4, 2, 0);
    }

    public RacingBikeFlagEncoder(PMap properties) {
        this(
                (int) properties.getLong("speed_bits", 4),
                properties.getDouble("speed_factor", 2),
                properties.getBool("turn_costs", false) ? 1 : 0
        );
        this.properties = properties;
        this.setBlockFords(properties.getBool("block_fords", true));
    }

    public RacingBikeFlagEncoder(String propertiesStr) {
        this(new PMap(propertiesStr));
    }

    public RacingBikeFlagEncoder(int speedBits, double speedFactor, int maxTurnCosts) {
        super(speedBits, speedFactor, maxTurnCosts);
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
        setSurfaceSpeed("cobblestone", 10);
        setSurfaceSpeed("cobblestone:flattened", 10);
        setSurfaceSpeed("sett", 10);
        setSurfaceSpeed("concrete", 20);
        setSurfaceSpeed("concrete:lanes", 16);
        setSurfaceSpeed("concrete:plates", 16);
        setSurfaceSpeed("paving_stones", 10);
        setSurfaceSpeed("paving_stones:30", 10);
        setSurfaceSpeed("unpaved", PUSHING_SECTION_SPEED / 2);
        setSurfaceSpeed("compacted", PUSHING_SECTION_SPEED / 2);
        setSurfaceSpeed("dirt", PUSHING_SECTION_SPEED / 2);
        setSurfaceSpeed("earth", PUSHING_SECTION_SPEED / 2);
        setSurfaceSpeed("fine_gravel", PUSHING_SECTION_SPEED);
        setSurfaceSpeed("grass", PUSHING_SECTION_SPEED / 2);
        setSurfaceSpeed("grass_paver", PUSHING_SECTION_SPEED / 2);
        setSurfaceSpeed("gravel", PUSHING_SECTION_SPEED / 2);
        setSurfaceSpeed("ground", PUSHING_SECTION_SPEED / 2);
        setSurfaceSpeed("ice", PUSHING_SECTION_SPEED / 2);
        setSurfaceSpeed("metal", PUSHING_SECTION_SPEED / 2);
        setSurfaceSpeed("mud", PUSHING_SECTION_SPEED / 2);
        setSurfaceSpeed("pebblestone", PUSHING_SECTION_SPEED);
        setSurfaceSpeed("salt", PUSHING_SECTION_SPEED / 2);
        setSurfaceSpeed("sand", PUSHING_SECTION_SPEED / 2);
        setSurfaceSpeed("wood", PUSHING_SECTION_SPEED / 2);

        setHighwaySpeed("cycleway", 18);
        setHighwaySpeed("path", 8);
        setHighwaySpeed("footway", 6);
        setHighwaySpeed("pedestrian", 6);
        setHighwaySpeed("road", 12);
        setHighwaySpeed("track", PUSHING_SECTION_SPEED / 2); // assume unpaved
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
        addPushingSection("pedestrian");
        addPushingSection("steps");

        setCyclingNetworkPreference("icn", PriorityCode.BEST.getValue());
        setCyclingNetworkPreference("ncn", PriorityCode.BEST.getValue());
        setCyclingNetworkPreference("rcn", PriorityCode.VERY_NICE.getValue());
        setCyclingNetworkPreference("lcn", PriorityCode.UNCHANGED.getValue());
        setCyclingNetworkPreference("mtb", PriorityCode.UNCHANGED.getValue());

        absoluteBarriers.add("kissing_gate");

        setAvoidSpeedLimit(81);
        setSpecificClassBicycle("roadcycling");

        init();
    }

    @Override
    public int getVersion() {
        return 2;
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
                weightToPrioMap.put(110d, AVOID_AT_ALL_COSTS.getValue());
        }
    }

    @Override
    boolean isPushingSection(ReaderWay way) {
        String highway = way.getTag("highway");
        String trackType = way.getTag("tracktype");
        return way.hasTag("highway", pushingSectionsHighways)
                || way.hasTag("railway", "platform")
                || way.hasTag("bicycle", "dismount")
                || "track".equals(highway) && trackType != null && !"grade1".equals(trackType);
    }

    @Override
    boolean isSacScaleAllowed(String sacScale) {
        // for racing bike it is only allowed if empty
        return false;
    }

    @Override
    public String toString() {
        return "racingbike";
    }
}
