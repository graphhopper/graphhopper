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

import com.graphhopper.reader.OSMNode;
import com.graphhopper.reader.OSMWay;

/**
 *
 * @author Thomas Buerli <tbuerli@student.ethz.ch>
 */
public class PublicTransitFlagEncoder extends AbstractFlagEncoder {

    private final int FORWARD;
    private final int BACKWARD;
    private final int BOTH;
    private final int TRANSIT;
    private final int BOARDING;
    private final int ALIGHT;
    private final int ENTRY;
    private final int EXIT;

    public PublicTransitFlagEncoder() {

        // 2 Bits
        FORWARD = 1;
        BACKWARD = 2;
        BOTH = 3;

        // 3 Bits
        BOARDING = 1 << 2;
        ALIGHT = 2 << 2;
        TRANSIT = 4 << 2;

        // 2 Bits
        ENTRY = 1 << 5;
        EXIT = 2 << 5;


    }

    @Override
    public int flags(int speed, boolean bothDir) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    public int flags(boolean bothDir) {
        if (bothDir) {
            return BOTH;
        } else {
            return FORWARD;
        }
    }

    /**
     * Flags which encodes a entry to a station. 
     *
     * @return flags
     */
    public int getEntryFlags() {
        return (ENTRY | TRANSIT | FORWARD);
    }

    public int getExitFlags() {
        return (FORWARD | EXIT);
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
     * Flags which encodes a alight edge
     *
     * @param bothDir true if traveling is allowed in both direction
     * @return flags
     */
    public int getAlightFlags(boolean bothDir) {
        if (bothDir) {
            return (BOTH | ALIGHT);
        } else {
            return (FORWARD | ALIGHT);
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

    public boolean isTransit(int flags) {
        return (flags & TRANSIT) != 0;
    }

    public boolean isBoarding(int flags) {
        return (flags & BOARDING) != 0;
    }

    public boolean isAlight(int flags) {
        return (flags & ALIGHT) != 0;
    }

    public boolean isEntry(int flags) {
        return (flags & ENTRY) != 0;
    }
    
    public boolean isExit(int flags) {
        return (flags & EXIT) != 0;
    }
    
    @Override
    public String toString()
    {
        return "PUBLIC";
    }

    @Override
    public int isAllowed( OSMWay way )
    {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public int handleWayTags( int allowed, OSMWay way )
    {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public int analyzeNodeTags( OSMNode node )
    {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }


}
