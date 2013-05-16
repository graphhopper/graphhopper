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
public class FootFlagEncoder extends AbstractFlagEncoder {

    private final Set<String> saveHighwayTags = new HashSet<String>() {
        {
            add("footway");
            add("path");
            add("steps");
            add("pedestrian");
            add("foot");
            add("living_street");
            add("track");
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
            add("service");
            // disallowed in some countries?
            add("bridleway");
        }
    };
    private static final Map<String, Integer> SPEED = new FootSpeed();

    public FootFlagEncoder() {
        super(16, 1, SPEED.get("mean"), SPEED.get("max"));
    }

    public Integer getSpeed(String string) {
        return SPEED.get(string);
    }

    @Override public String toString() {
        return "FOOT";
    }

    /**
     * Some ways are okay but not separate for pedestrians.
     */
    @Override
    public boolean isAllowed(Map<String, String> osmProperties) {
        String sidewalkValue = osmProperties.get("sidewalk");
        if ("yes".equals(sidewalkValue) || "both".equals(sidewalkValue)
                || "left".equals(sidewalkValue) || "right".equals(sidewalkValue))
            return true;
        String footValue = osmProperties.get("foot");
        if ("yes".equals(footValue))
            return true;
        String highwayValue = osmProperties.get("highway");
        if (!allowedHighwayTags.contains(highwayValue))
            return false;
        String accessValue = osmProperties.get("access");
        return super.isAllowed(accessValue);
    }

    /**
     * Separate ways for pedestrians.
     */
    public boolean isSaveHighway(String highwayValue) {
        return saveHighwayTags.contains(highwayValue);
    }

    private static class FootSpeed extends HashMap<String, Integer> {

        {
            put("min", 1);
            put("slow", 3);
            put("mean", 5);
            put("fast", 10);
            put("max", 15);
        }
    }
}
