/*
 *  Licensed to Peter Karich under one or more contributor license
 *  agreements. See the NOTICE file distributed with this work for
 *  additional information regarding copyright ownership.
 *
 *  Peter Karich licenses this file to you under the Apache License,
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
 */
public class BikeFlagEncoder extends AbstractFlagEncoder {

    private final Set<String> saveHighwayTags = new HashSet<String>() {
        {
            add("cycleway");
            add("path");
            add("road");
            add("living_street");
            add("track");
            // disallowed in some countries?
            add("bridleway");
        }
    };
    private final Set<String> allowedHighwayTags = new HashSet<String>() {
        {
            addAll( saveHighwayTags );
            add("trunk");
            add("primary");
            add("secondary");
            add("tertiary");
            add("unclassified");
            add("residential");
        }
    };
    private static final Map<String, Integer> SPEED = new BikeSpeed();
    private HashSet<String> intended = new HashSet<String>();
    private HashSet<String> oppositeLanes = new HashSet<String>();

    public BikeFlagEncoder() {
        super(8, 2, SPEED.get("cycleway"), SPEED.get("road"));

        // strict set, usually vehicle and agricultural/forestry are ignored by cyclists
        restrictions = new String[]{"bicycle", "access"};
        restrictedValues.add("private");
        restrictedValues.add("no");
        restrictedValues.add("restricted");

        intended.add("yes");
        intended.add("designated");
        intended.add("official");
        intended.add("permissive");

        oppositeLanes.add("opposite");
        oppositeLanes.add("opposite_lane");
        oppositeLanes.add("opposite_track");
    }

    public int getSpeed(String string) {
        // TODO use surface for speed!
        Integer speed = SPEED.get(string);
        if (speed == null)
            throw new IllegalStateException("bike, no speed found for:" + string);
        return speed;
    }

    @Override public String toString() {
        return "BIKE";
    }

    /**
     * Separate ways for pedestrians.
     * @param way
     */
    @Override
    public int isAllowed(OSMWay way ) {
        String highwayValue = way.getTag("highway");
        if (highwayValue == null) {
            if (way.hasTag("route", ferries )
                    && way.hasTag("bicycle", "yes" ))
                return acceptBit | ferryBit;

            return 0;
        } else {
            if (!allowedHighwayTags.contains(highwayValue))
                return 0;

            if (way.hasTag("bicycle", intended ))
                return acceptBit;

            if (way.hasTag("motorroad", "yes" ))
                return 0;

            // check access restrictions
            if (way.hasTag(restrictions, restrictedValues ))
                return 0;

            return acceptBit;
        }
    }

    @Override
    public int handleWayTags(int allowed, OSMWay way) {
        if ((allowed & acceptBit) == 0)
            return 0;

        int encoded = 0;
        if ((allowed & ferryBit) == 0) {
            // http://wiki.openstreetmap.org/wiki/Cycleway
            // http://wiki.openstreetmap.org/wiki/Map_Features#Cycleway
            String highwayValue = way.getTag("highway");
            int speed = getSpeed(highwayValue);
            if (way.hasTag("oneway", oneways )
                    && !way.hasTag("oneway:bicycle", "no")
                    && !way.hasTag("cycleway", oppositeLanes)) {

                encoded = flags(speed, false);
                if (way.hasTag("oneway", "-1"))
                    encoded = swapDirection(encoded);
            } else
                encoded = flags(speed, true);
        } else {
            // TODO read duration and calculate speed 00:30 for ferry
            encoded = flags(10, true);
        }
        return encoded;
    }

    /**
     * Some ways are okay but not separate for pedestrians.
     */
    public boolean isSaveHighway(String highwayValue) {
        return saveHighwayTags.contains(highwayValue);
    }

    private static class BikeSpeed extends HashMap<String, Integer> {

        {
            put("living_street", 5);
            put("bridleway", 10);

            put("cycleway", 10);
            put("path", 10);
            put("road", 10);
            put("track", 10);

            put("trunk", 20);
            put("primary", 20);
            put("secondary", 20);
            put("tertiary", 20);
            put("unclassified", 10);
            put("residential", 10);
        }
    }
}
