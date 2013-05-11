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

import java.util.Map;

/**
 * @author Peter Karich
 */
public abstract class AbstractFlagEncoder implements EdgePropertyEncoder {

    /**
     * This variable converts the stored value to the speed in km/h or does the
     * opposite.
     */
    protected final int factor;
    private final int FORWARD;
    private final int BACKWARD;
    private final int BOTH;
    private final int speedShift;
    private final int defaultSpeedPart;
    private final int maxSpeed;
    private final int flagWindow;

    public AbstractFlagEncoder(int shift, int factor, int defaultSpeed, int maxSpeed) {
        this.factor = factor;
        this.defaultSpeedPart = defaultSpeed / factor;
        this.maxSpeed = maxSpeed;
        this.flagWindow = ((1 << (shift + 8)) - 1);
        // not necessary as we right shift 
        // flagWindow -= ((1 << shift) - 1);
        speedShift = shift + 2;
        FORWARD = 1 << shift;
        BACKWARD = 2 << shift;
        BOTH = 3 << shift;
    }

    public abstract boolean isAllowed(Map<String, Object> map);
    
    protected boolean isAllowed(String accessValue) {
        return !"no".equals(accessValue);
    }

    @Override
    public boolean isForward(int flags) {
        return (flags & FORWARD) != 0;
    }

    @Override
    public boolean isBackward(int flags) {
        return (flags & BACKWARD) != 0;
    }

    public boolean isBoth(int flags) {
        return (flags & BOTH) == BOTH;
    }

    @Override
    public boolean canBeOverwritten(int flags1, int flags2) {
        return isBoth(flags2) || (flags1 & BOTH) == (flags2 & BOTH);
    }

    public int swapDirection(int flags) {
        int dir = flags & BOTH;
        if (dir == BOTH || dir == 0)
            return flags;
        return flags ^ BOTH;
    }

    protected int getSpeedPart(int flags) {
        return (flags & flagWindow) >>> speedShift;
    }

    @Override
    public int getSpeed(int flags) {
        return getSpeedPart(flags) * factor;
    }

    public int flagsDefault(boolean bothDirections) {
        if (bothDirections)
            return defaultSpeedPart << speedShift | BOTH;
        return defaultSpeedPart << speedShift | FORWARD;
    }

    @Override
    public int flags(int speed, boolean bothDir) {
        int flags = speed / factor;
        if (bothDir)
            return flags << speedShift | BOTH;
        return flags << speedShift | FORWARD;
    }

    @Override
    public int getMaxSpeed() {
        return maxSpeed;
    }
}
