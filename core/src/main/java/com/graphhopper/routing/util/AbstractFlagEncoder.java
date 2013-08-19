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

import com.graphhopper.reader.OSMNode;
import com.graphhopper.reader.OSMWay;
import com.graphhopper.util.DistanceCalc;
import com.graphhopper.util.Helper;

import java.util.HashSet;

/**
 * Abstract class which handles flag decoding and encoding. Every encoder should be registered to a
 * EncodingManager to be usable.
 * <p/>
 * @author Peter Karich
 * @author Nop
 * @see EncodingManager
 */
public abstract class AbstractFlagEncoder implements FlagEncoder
{
    protected int forwardBit = 0;
    protected int backwardBit = 0;
    protected int directionBitMask = 0;
    protected EncodedValue speedEncoder;
    // bit to signal that way is accepted
    protected int acceptBit = 0;
    protected int ferryBit = 0;
    // restriction definitions
    protected String[] restrictions;
    protected HashSet<String> restrictedValues = new HashSet<String>(5);
    protected HashSet<String> ferries = new HashSet<String>(5);
    protected HashSet<String> oneways = new HashSet<String>(5);
    protected HashSet<String> acceptedRailways = new HashSet<String>(5);
    protected HashSet<String> absoluteBarriers = new HashSet<String>(5);
    protected HashSet<String> potentialBarriers = new HashSet<String>(5);

    public AbstractFlagEncoder()
    {
        oneways.add("yes");
        oneways.add("true");
        oneways.add("1");
        oneways.add("-1");

        ferries.add("shuttle_train");
        ferries.add("ferry");

        acceptedRailways.add("tram");
    }

    /**
     * @return the speed in km/h
     */
    static int parseSpeed( String str )
    {
        if (Helper.isEmpty(str))
        {
            return -1;
        }

        try
        {
            int val;
            // see https://en.wikipedia.org/wiki/Knot_%28unit%29#Definitions
            int mpInteger = str.indexOf("mp");
            if (mpInteger > 0)
            {
                str = str.substring(0, mpInteger).trim();
                val = Integer.parseInt(str);
                return (int) Math.round(val * DistanceCalc.KM_MILE);
            }

            int knotInteger = str.indexOf("knots");
            if (knotInteger > 0)
            {
                str = str.substring(0, knotInteger).trim();
                val = Integer.parseInt(str);
                return (int) Math.round(val * 1.852);
            }

            int kmInteger = str.indexOf("km");
            if (kmInteger > 0)
            {
                str = str.substring(0, kmInteger).trim();
            } else
            {
                kmInteger = str.indexOf("kph");
                if (kmInteger > 0)
                {
                    str = str.substring(0, kmInteger).trim();
                }
            }

            return Integer.parseInt(str);
        } catch (Exception ex)
        {
            return -1;
        }
    }

    /**
     * Define 2 reserved bits for routing and internal bits for parsing.
     * <p/>
     * @param index
     * @param shift bit offset for the first bit used by this encoder
     * @return incremented shift value pointing behind the last used bit
     */
    public int defineBits( int index, int shift )
    {
        // define the first 2 bits in flags for routing
        forwardBit = 1 << shift;
        backwardBit = 2 << shift;
        directionBitMask = 3 << shift;

        // define internal flags for parsing
        index *= 2;
        acceptBit = 1 << index;
        ferryBit = 2 << index;

        return shift + 2;
    }

    /**
     * Decide whether a way is routable for a given mode of travel
     * <p/>
     * @param way
     * @return the assigned bit of the mode of travel if it is accepted or 0 for not accepted
     */
    public abstract int isAllowed( OSMWay way );

    /**
     * Analyze properties of a way and create the routing flags
     * <p/>
     * @param allowed
     */
    public abstract int handleWayTags( int allowed, OSMWay way );

    /**
     * Parse tags on nodes, looking for barriers.
     * <p/>
     * @param node
     * @return
     */
    public abstract int analyzeNodeTags( OSMNode node );

    public boolean hasAccepted( int acceptedValue )
    {
        return (acceptedValue & acceptBit) > 0;
    }

    @Override
    public boolean isForward( int flags )
    {
        return (flags & forwardBit) != 0;
    }

    @Override
    public boolean isBackward( int flags )
    {
        return (flags & backwardBit) != 0;
    }

    public boolean isBoth( int flags )
    {
        return (flags & directionBitMask) == directionBitMask;
    }

    @Override
    public boolean canBeOverwritten( int flags1, int flags2 )
    {
        return isBoth(flags2) || (flags1 & directionBitMask) == (flags2 & directionBitMask);
    }

    public int swapDirection( int flags )
    {
        int dir = flags & directionBitMask;
        if (dir == directionBitMask || dir == 0)
        {
            return flags;
        }
        return flags ^ directionBitMask;
    }

    @Override
    public int getSpeed( int flags )
    {
        return speedEncoder.getValue(flags);
    }

    /**
     * @param bothDirections
     * @return
     */
    public int flagsDefault( boolean bothDirections )
    {
        int flags = speedEncoder.setDefaultValue(0);
        return flags | (bothDirections ? directionBitMask : forwardBit);
    }

    /**
     * @deprecated @param speed the speed in km/h
     * @param bothDirections
     * @return
     */
    @Override
    public int flags( int speed, boolean bothDirections )
    {
        int flags = speedEncoder.setValue(0, speed);
        return flags | (bothDirections ? directionBitMask : forwardBit);
    }

    @Override
    public int getMaxSpeed()
    {
        return speedEncoder.getDefaultMaxValue();
    }

    @Override
    public int hashCode()
    {
        int hash = 7;
        hash = 61 * hash + this.directionBitMask;
        hash = 61 * hash + this.toString().hashCode();
        return hash;
    }

    @Override
    public boolean equals( Object obj )
    {
        if (obj == null)
        {
            return false;
        }
        // only rely on the string
//        if (getClass() != obj.getClass())
//            return false;
        final AbstractFlagEncoder other = (AbstractFlagEncoder) obj;
        if (this.directionBitMask != other.directionBitMask)
        {
            return false;
        }
        return this.toString().equals(other.toString());
    }

    public String getWayInfo( OSMWay way )
    {
        return "";
    }
}
