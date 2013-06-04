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
import com.graphhopper.util.Helper;

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
            addAll(saveHighwayTags);
            add("trunk");
            add("primary");
            add("secondary");
            add("tertiary");
            add("unclassified");
            add("residential");
        }
    };
    private HashSet<String> intended = new HashSet<String>();
    private HashSet<String> oppositeLanes = new HashSet<String>();

    public BikeFlagEncoder() {
        super(8, 2, HIGHWAY_SPEED.get("cycleway"), HIGHWAY_SPEED.get("road"));

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

    @Override public String toString() {
        return "BIKE";
    }

    /**
     * Separate ways for pedestrians.
     *
     * @param way
     */
    @Override
    public int isAllowed(OSMWay way) {
        String highwayValue = way.getTag("highway");
        if (highwayValue == null) {
            if (way.hasTag("route", ferries)
                    && way.hasTag("bicycle", "yes"))
                return acceptBit | ferryBit;

            return 0;
        } else {
            if (!allowedHighwayTags.contains(highwayValue))
                return 0;

            if (way.hasTag("bicycle", intended))
                return acceptBit;

            if (way.hasTag("motorroad", "yes"))
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
            // http://wiki.openstreetmap.org/wiki/Cycleway
            // http://wiki.openstreetmap.org/wiki/Map_Features#Cycleway
            int speed = getSpeed(way);
            if ((way.hasTag("oneway", oneways) || way.hasTag("junction", "roundabout"))
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

    int getSpeed(OSMWay way) {
        String s = way.getTag("surface");
        if (!Helper.isEmpty(s)) {
            Integer sInt = SURFACE_SPEED.get(s);
            if (sInt != null)
                return sInt;
        }
        String tt = way.getTag("tracktype");
        if (!Helper.isEmpty(tt)) {
            Integer tInt = TRACKTYPE_SPEED.get(tt);
            if (tInt != null)
                return tInt;
        }
        String highway = way.getTag("highway");
        if (!Helper.isEmpty(tt)) {
            Integer hwInt = HIGHWAY_SPEED.get(highway);
            if (hwInt != null)
                return hwInt;
        }
        return 10;
    }
    private static final Map<String, Integer> TRACKTYPE_SPEED = new HashMap<String, Integer>() {
        {
            put("grade1", 16); // paved
            put("grade2", 12); // now unpaved ...
            put("grade3", 12);
            put("grade4", 10);
            put("grade5", 8); // like sand/grass            
        }
    };
    private static final Map<String, Integer> SURFACE_SPEED = new HashMap<String, Integer>() {
        {
            put("asphalt", 18);
            put("concrete", 18);
            put("paved", 16);
            put("unpaved", 12);
            put("gravel", 12);
            put("ground", 12);
            put("dirt", 10);
            put("paving_stones", 8);
            put("grass", 8);
        }
    };
    private static final Map<String, Integer> HIGHWAY_SPEED = new HashMap<String, Integer>() {
        {
            put("living_street", 6);
            put("bridleway", 10);

            put("cycleway", 14);
            put("path", 10);
            put("road", 10);
            put("track", 10);

            put("trunk", 18);
            put("primary", 18);
            put("secondary", 18);
            put("tertiary", 18);
            put("unclassified", 10);
            put("residential", 10);
        }
    };
}
