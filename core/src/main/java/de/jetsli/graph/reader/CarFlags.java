/*
 *  Copyright 2012 Peter Karich info@jetsli.de
 * 
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 * 
 *       http://www.apache.org/licenses/LICENSE-2.0
 * 
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package de.jetsli.graph.reader;

import java.util.HashMap;
import java.util.Map;

/**
 * @author Peter Karich
 */
public class CarFlags {

    static final CarSpeed CAR_SPEED = new CarSpeed();
    static final int DEFAULT_SPEED = CAR_SPEED.get("secondary");
    public static final int MAX_SPEED = CAR_SPEED.get("motorway");
    public static final int FORWARD = 1;
    public static final int BACKWARD = 2;
    private int flags;

    /**
     * for debugging and testing purposes
     */
    public CarFlags(int flags) {
        this.flags = flags;
    }

    public boolean isMotorway() {
        return getSpeedPart() == CAR_SPEED.get("motorway");
    }

    public boolean isService() {
        return getSpeedPart() == CAR_SPEED.get("service");
    }

    public boolean isForward() {
        return (flags & 1) != 0;
    }

    public boolean isBackward() {
        return (flags & 2) != 0;
    }

    public int getSpeedPart() {
        return getSpeedPart(flags);
    }

    public int getSpeed() {
        return getSpeedPart() * 10;
    }

    public int getRaw() {
        return flags;
    }

    public static int create(boolean bothDirections) {
        if (bothDirections)
            return DEFAULT_SPEED << 2 | BACKWARD | FORWARD;
        return DEFAULT_SPEED << 2 | FORWARD;
    }

    public static boolean isForward(int flags) {
        return (flags & 1) == FORWARD;
    }

    public static boolean isBackward(int flags) {
        return (flags & 2) == BACKWARD;
    }

    /**
     * returns the flags with an opposite direction if not both ways
     */
    public static int swapDirection(int flags) {
        if ((flags & 3) == 3)
            return flags;

        int speed = flags >>> 2;
        return (speed << 2) | (~flags) & 3;
    }

    public static int getSpeedPart(int flags) {
        int v = flags >>> 2;
        if (v == 0)
            v = DEFAULT_SPEED;
        return v;
    }
    
    public static int getSpeed(int flags) {
        return getSpeedPart(flags) * 10;
    }

    public static int create(int speed, boolean bothDir) {
        int flags = speed / 10;
        flags <<= 2;
        flags |= FORWARD;
        if (bothDir)
            flags |= BACKWARD;
        return flags;
    }

    public static int create(Map<String, Object> properties) {
        Integer integ = (Integer) properties.get("car");
        if (integ != null) {
            integ *= 10;
            if (!"yes".equals(properties.get("oneway")))
                return create(integ, true);
            else
                return create(integ, false);
        }
        return 0;
    }

    @Override
    public String toString() {
        int speed = getSpeedPart();
        return "speed:" + speed + ", backwards:" + ((flags & 1) != 0) + ", forwards:" + ((flags & 2) != 0);
    }
    // used from http://wiki.openstreetmap.org/wiki/OpenRouteService#Used_OSM_Tags_for_Routing
    // http://wiki.openstreetmap.org/wiki/Map_Features#Highway

    /**
     * A map which associates string to integer. With this integer one can put the speed profile
     * into 1 byte
     */
    static class CarSpeed extends HashMap<String, Integer> {

        {
            // autobahn
            put("motorway", 11); // 11 * 10 = 110
            put("motorway_link", 9);
            // bundesstraße
            put("trunk", 9);
            put("trunk_link", 7);
            // linking bigger town
            put("primary", 7);
            put("primary_link", 6);
            // linking smaller towns + villages
            put("secondary", 6);
            put("secondary_link", 5);
            // streets without middle line separation
            put("tertiary", 5);
            put("tertiary_link", 4);
            put("unclassified", 5);
            put("residential", 4);
            // spielstraße
            put("living_street", 1);
            put("service", 3);
            // unknown road
            put("road", 3);
            // forestry stuff
            put("track", 2);
        }
    }
}
