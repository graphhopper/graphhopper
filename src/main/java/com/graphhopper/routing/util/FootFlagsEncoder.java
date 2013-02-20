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
public class FootFlagsEncoder implements FlagsEncoder {

    private static final Map<String, Integer> SPEED = new FootSpeed();
    private static final int MAX_SPEED = SPEED.get("max");
    private static final int FACTOR = 2;
    private static final int DEFAULT_SPEED_PART = SPEED.get("mean") / FACTOR;
    private static final int FORWARD = 1;
    private static final int BACKWARD = 2;
    
    // TODO NOW SHIFT 16 bits to avoid conflict with car and preserve bike

    @Override
    public boolean isForward(int flags) {
        return (flags & 1) == FORWARD;
    }

    @Override
    public boolean isBackward(int flags) {
        return (flags & 2) == BACKWARD;
    }

    public boolean isBoth(int flags) {
        return (flags & 3) == (FORWARD | BACKWARD);
    }

    @Override
    public boolean canBeOverwritten(int flags1, int flags2) {
        return isBoth(flags2) || (flags1 & 3) == (flags2 & 3);
    }

    @Override
    public int swapDirection(int flags) {
        if ((flags & 3) == 3)
            return flags;

        int speed = flags >>> 2;
        return (speed << 2) | (~flags) & 3;
    }

    public Integer getSpeed(String string) {
        return SPEED.get(string);
    }

    private int getSpeedPart(int flags) {
        int v = flags >>> 2;
        if (v == 0)
            v = DEFAULT_SPEED_PART;
        return v;
    }

    @Override
    public int getSpeed(int flags) {
        return getSpeedPart(flags) * FACTOR;
    }

    @Override
    public int getMaxSpeed() {
        return MAX_SPEED;
    }

    @Override
    public int flagsDefault(boolean bothDirections) {
        if (bothDirections)
            return DEFAULT_SPEED_PART << 2 | BACKWARD | FORWARD;
        return DEFAULT_SPEED_PART << 2 | FORWARD;
    }

    @Override
    public int flags(int speed, boolean bothDir) {
        int flags = speed / FACTOR;
        flags <<= 2;
        flags |= FORWARD;
        if (bothDir)
            flags |= BACKWARD;
        return flags;
    }

    @Override public String toString() {
        return "FOOT";
    }

    private static class FootSpeed extends HashMap<String, Integer> {

        {
            put("min", 2);
            put("slow", 4);
            put("mean", 6);
            put("fast", 10);
            put("max", 15);
        }
    }
}
