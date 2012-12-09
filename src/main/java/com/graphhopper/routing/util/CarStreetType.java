/*
 *  Copyright 2012 Peter Karich 
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
package com.graphhopper.routing.util;

import java.util.HashMap;
import java.util.Map;

/**
 * @author Peter Karich
 */
public class CarStreetType {

    public static final Map<String, Integer> SPEED = new CarSpeed();
    public static final int MAX_SPEED = SPEED.get("motorway");
    public static final int FACTOR = 2;
    public static final int DEFAULT_SPEED = SPEED.get("secondary");
    private static final int FORWARD = 1;
    private static final int BACKWARD = 2;
    private int flags;

    /**
     * for debugging and testing purposes
     */
    public CarStreetType(int flags) {
        this.flags = flags;
    }

    public int getRaw() {
        return flags;
    }

    public boolean isMotorway() {
        return getSpeedPart() == SPEED.get("motorway");
    }

    public boolean isService() {
        return getSpeedPart() == SPEED.get("service");
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

    @Override
    public String toString() {
        int speed = getSpeed();
        return "speed:" + speed + ", backwards:" + ((flags & BACKWARD) != 0) + ", forwards:" + ((flags & FORWARD) != 0);
    }

    public static boolean isForward(int flags) {
        return (flags & 1) == FORWARD;
    }

    public static boolean isBackward(int flags) {
        return (flags & 2) == BACKWARD;
    }

    public static boolean isBoth(int flags) {
        return (flags & 3) == (FORWARD | BACKWARD);
    }

    /**
     * Returns true if flags1 can be overwritten by flags2 without restricting or changing the
     * directions of flags1.
     */
    //        \  flags2:
    // flags1  \ -> | <- | <->
    // ->         t | f  | t
    // <-         f | t  | t
    // <->        f | f  | t
    public static boolean canBeOverwritten(int flags1, int flags2) {
        return isBoth(flags2) || (flags1 & 3) == (flags2 & 3);
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
        return getSpeedPart(flags) * FACTOR;
    }

    public static int flagsDefault(boolean bothDirections) {
        if (bothDirections)
            return DEFAULT_SPEED << 2 | BACKWARD | FORWARD;
        return DEFAULT_SPEED << 2 | FORWARD;
    }

    public static int flags(int speed, boolean bothDir) {
        int flags = speed / FACTOR;
        flags <<= 2;
        flags |= FORWARD;
        if (bothDir)
            flags |= BACKWARD;
        return flags;
    }

    /**
     * A map which associates string to speed. The speed is calculated from the integer with FACTOR.
     * I.e. only 1 byte is necessary. Get some impression:
     * http://www.itoworld.com/map/124#fullscreen
     * http://wiki.openstreetmap.org/wiki/OSM_tags_for_routing/Maxspeed
     */
    private static class CarSpeed extends HashMap<String, Integer> {

        {
            // autobahn
            put("motorway", 100 / FACTOR);
            put("motorway_link", 70 / FACTOR);
            // bundesstraße
            put("trunk", 70 / FACTOR);
            put("trunk_link", 65 / FACTOR);
            // linking bigger town
            put("primary", 65 / FACTOR);
            put("primary_link", 60 / FACTOR);
            // linking towns + villages
            put("secondary", 60 / FACTOR);
            put("secondary_link", 50 / FACTOR);
            // streets without middle line separation
            put("tertiary", 50 / FACTOR);
            put("tertiary_link", 40 / FACTOR);
            put("unclassified", 30 / FACTOR);
            put("residential", 30 / FACTOR);
            // spielstraße
            put("living_street", 10 / FACTOR);
            put("service", 20 / FACTOR);
            // unknown road
            put("road", 20 / FACTOR);
            // forestry stuff
            put("track", 20 / FACTOR);
        }
    }
}
