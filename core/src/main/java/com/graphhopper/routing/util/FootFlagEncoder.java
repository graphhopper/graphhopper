/*
 *  Licensed to GraphHopper and Peter Karich under one or more contributor license
 *  agreements. See the NOTICE file distributed with this work for
 *  additional information regarding copyright ownership.
 *
 *  GraphHopper licenses this file to you under the Apache License,
 *  Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License. You may obtain a copy of the
 *  License at
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

import com.graphhopper.reader.OSMWay;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * @author Peter Karich
 * @author Nop
 */
public class FootFlagEncoder extends AbstractFlagEncoder {

    private int safeWayBit = 0;
    protected HashSet<String> intended = new HashSet<String>();
    protected HashSet<String> sidewalks = new HashSet<String>();

    private FootFlagEncoder() {
    }

    public FootFlagEncoder(EncodingManager manager) {
        super(manager);
        restrictions = new String[]{"foot", "access"};
        restrictedValues.add("private");
        restrictedValues.add("no");
        restrictedValues.add("restricted");

        intended.add("yes");
        intended.add("designated");
        intended.add("official");
        intended.add("permissive");

        sidewalks.add("yes");
        sidewalks.add("both");
        sidewalks.add("left");
        sidewalks.add("right");

        ferries.add("shuttle_train");
        ferries.add("ferry");
    }

    @Override
    public int defineBits(int index, int shift) {
        // first two bits are reserved for route handling in superclass
        shift = super.defineBits(index, shift);

        // larger value required - ferries are faster than pedestrians
        speedEncoder = new EncodedValue("Speed", shift, 4, 1, SPEED.get("mean"), SPEED.get("max"));
        shift += 4;

        safeWayBit = 1 << shift++;

        return shift;
    }

    public Integer getSpeed(String string) {
        Integer speed = SPEED.get(string);
        if (speed == null)
            throw new IllegalStateException("foot, no speed found for:" + string);
        return speed;
    }

    @Override public String toString() {
        return "FOOT";
    }

    /**
     * Some ways are okay but not separate for pedestrians.
     *
     * @param way
     */
    @Override
    public int isAllowed(OSMWay way) {
        String highwayValue = way.getTag("highway");
        if (highwayValue == null) {
            if (way.hasTag("route", ferries)) {
                if (!way.hasTag("foot", "no"))
                    return acceptBit | ferryBit;
            }
            return 0;
        } else {
            if (way.hasTag("sidewalk", sidewalks))
                return acceptBit;

            // no need to evaluate ferries - already included here
            if (way.hasTag("foot", intended))
                return acceptBit;

            if (!allowedHighwayTags.contains(highwayValue))
                return 0;

            if (way.hasTag("motorroad", "yes"))
                return 0;

            if (way.hasTag("bicycle", "official"))
                return 0;
            // check access restrictions
            if (way.hasTag(restrictions, restrictedValues))
                return 0;

            return acceptBit;
        }
    }

    @Override
    public int handleWayTags(int allowed, OSMWay way) {
        if ((allowed & acceptBit) == 0)
            return 0;

        int encoded;
        if ((allowed & ferryBit) == 0) {
            encoded = speedEncoder.setDefaultValue(0);
            encoded |= directionBitMask;

            // mark safe ways or ways with cycle lanes
            if (safeHighwayTags.contains(way.getTag("highway"))
                    || way.hasTag("sidewalk", sidewalks))
                encoded |= safeWayBit;

        } else {
            // TODO read duration and calculate speed 00:30 for ferry            
            encoded = speedEncoder.setValue(0, 10);
            encoded |= directionBitMask;
        }

        return encoded;
    }

    /**
     * Separate ways for pedestrians.
     */
    public boolean isSaveHighway(String highwayValue) {
        return safeHighwayTags.contains(highwayValue);
    }
    private final Set<String> safeHighwayTags = new HashSet<String>() {
        {
            add("footway");
            add("path");
            add("steps");
            add("pedestrian");
            add("living_street");
            add("track");
            add("residential");
            add("service");
        }
    };
    private final Set<String> allowedHighwayTags = new HashSet<String>() {
        {
            addAll(safeHighwayTags);
            add("trunk");
            add("trunk_link");
            add("primary");
            add("primary_link");
            add("secondary");
            add("secondary_link");
            add("tertiary");
            add("tertiary_link");
            add("unclassified");
            add("road");
            // disallowed in some countries
            //add("bridleway");
        }
    };
    private static final Map<String, Integer> SPEED = new HashMap<String, Integer>() {
        {
            put("min", 2);
            put("slow", 4);
            put("mean", 5);
            put("fast", 6);
            put("max", 7);
        }
    };
}
