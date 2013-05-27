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
import java.util.Map;

/**
 * @author Peter Karich
 */
public class CarFlagEncoder extends AbstractFlagEncoder {

    private static final Map<String, Integer> SPEED = new CarSpeed();

    public CarFlagEncoder() {
        super(0, 2, SPEED.get("secondary"), SPEED.get("motorway"));

        restrictions = new String[]{"motorcar", "motor_vehicle", "vehicle", "access"};
        restricted.add("private");
        restricted.add("agricultural");
        restricted.add("forestry");
        restricted.add("no");
        restricted.add("restricted");
    }

    public boolean isMotorway(int flags) {
        return getSpeedPart(flags) * factor == SPEED.get("motorway");
    }

    public boolean isService(int flags) {
        return getSpeedPart(flags) * factor == SPEED.get("service");
    }

    public int getSpeed(String string) {
        Integer speed = SPEED.get(string);
        if (speed == null)
            throw new IllegalStateException("car, no speed found for:" + string);
        return speed;
    }

    @Override
    public boolean isAllowed(Map<String, String> osmProperties) {
        String highwayValue = osmProperties.get("highway");
        if (!SPEED.containsKey(highwayValue))
            return false;

        return super.isAllowed(osmProperties);
    }

    @Override public String toString() {
        return "CAR";
    }

    /**
     * A map which associates string to speed. Get some impression:
     * http://www.itoworld.com/map/124#fullscreen
     * http://wiki.openstreetmap.org/wiki/OSM_tags_for_routing/Maxspeed
     */
    private static class CarSpeed extends HashMap<String, Integer> {

        {
            // autobahn
            put("motorway", 100);
            put("motorway_link", 70);
            // bundesstraße
            put("trunk", 70);
            put("trunk_link", 65);
            // linking bigger town
            put("primary", 65);
            put("primary_link", 60);
            // linking towns + villages
            put("secondary", 60);
            put("secondary_link", 50);
            // streets without middle line separation
            put("tertiary", 50);
            put("tertiary_link", 40);
            put("unclassified", 30);
            put("residential", 30);
            // spielstraße
            put("living_street", 10);
            put("service", 20);
            // unknown road
            put("road", 20);
            // forestry stuff
            put("track", 20);
        }
    }
}
