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
            add("road");
        }
    };
    private static final Map<String, Integer> SPEED = new FootSpeed();
    private HashSet<String> intended = new HashSet<String>();

    public BikeFlagEncoder() {
        super(8, 2, SPEED.get("mean"), SPEED.get("max"));

        // strict set, usually vehicle and agricultural/forestry are ignored by cyclists
        restrictions = new String[]{"bicycle", "access"};
        restricted.add("private");
        restricted.add("no");
        restricted.add("restricted");
        intended.add("yes");
        intended.add("designated");
        intended.add("official");
        intended.add("permissive");
    }

    public Integer getSpeed(String string) {
        return SPEED.get(string);
    }

    @Override public String toString() {
        return "BIKE";
    }

    /**
     * Separate ways for pedestrians.
     */
    @Override
    public boolean isAllowed(Map<String, String> osmProperties) {
        String highwayValue = osmProperties.get("highway");
        if (!allowedHighwayTags.contains(highwayValue))
            return false;

        if (hasTag("bicycle", intended, osmProperties))
            return true;

        if (hasTag("motorroad", "yes", osmProperties))
            return false;

        return super.isAllowed(osmProperties);
    }

    /**
     * Some ways are okay but not separate for pedestrians.
     */
    public boolean isSaveHighway(String highwayValue) {
        return saveHighwayTags.contains(highwayValue);
    }

    public boolean isOpposite(String cycleway) {
        return "opposite".equals(cycleway) || "opposite_lane".equals(cycleway)
                || "opposite_track".equals(cycleway);
    }

    private static class FootSpeed extends HashMap<String, Integer> {

        {
            put("min", 4);
            put("slow", 10);
            put("mean", 16);
            put("fast", 20);
            put("max", 26);
        }
    }
}
