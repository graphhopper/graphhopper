/*
 * Copyright 2013 Thomas Buerli <tbuerli@student.ethz.ch>.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.graphhopper.routing.util;

/**
 *
 * @author Thomas Buerli <tbuerli@student.ethz.ch>
 */
public class PublicTransitFlagEncoder implements EdgePropertyEncoder {

    private final int FORWARD;
    private final int BACKWARD;
    private final int BOTH;
    private final int TRANSIT;
    private final int BOARDING;
    private final int ALIGN;

    public PublicTransitFlagEncoder() {

        FORWARD = 1;
        BACKWARD = 2;
        BOTH = 3;
        BOARDING = 1 << 2;
        ALIGN = 2 << 2;
        TRANSIT = 4 << 2;


    }

    @Override
    public int flags(int speed, boolean bothDir) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }
    
    public int flags(boolean bothDir) {
        if (bothDir)
            return BOTH;
        else
            return FORWARD;
    }

    /**
     * Flags which encodes a transit edge
     *
     * @param bothDir true if traveling is allowed in both direction
     * @return flags
     */
    public int getTransitFlags(boolean bothDir) {
        if (bothDir) {
            return (BOTH | TRANSIT);
        } else {
            return (FORWARD | TRANSIT);
        }
    }

    /**
     * Flags which encodes a boarding edge
     *
     * @param bothDir true if traveling is allowed in both direction
     * @return flags
     */
    public int getBoardingFlags(boolean bothDir) {
        if (bothDir) {
            return (BOTH | BOARDING);
        } else {
            return (FORWARD | BOARDING);
        }
    }

    /**
     * Flags which encodes a align edge
     *
     * @param bothDir true if traveling is allowed in both direction
     * @return flags
     */
    public int getAlignFlags(boolean bothDir) {
        if (bothDir) {
            return (BOTH | ALIGN);
        } else {
            return (FORWARD | ALIGN);
        }
    }

    @Override
    public int getSpeed(int flags) {
        return 1;
    }

    @Override
    public boolean isForward(int flags) {
        return (flags & FORWARD) != 0;
    }

    @Override
    public boolean isBackward(int flags) {
        return (flags & BACKWARD) != 0;
    }

    @Override
    public int getMaxSpeed() {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public boolean canBeOverwritten(int flags1, int flags2) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    boolean isTransit(int flags) {
        return (flags & TRANSIT) != 0;
    }

    boolean isBoarding(int flags) {
        return (flags & BOARDING) != 0;
    }

    boolean isAlight(int flags) {
        return (flags & ALIGN) != 0;
    }
}
