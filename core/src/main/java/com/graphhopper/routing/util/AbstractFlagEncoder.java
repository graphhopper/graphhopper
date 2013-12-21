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
import com.graphhopper.reader.OSMRelation;
import com.graphhopper.util.DistanceCalcEarth;
import com.graphhopper.util.Helper;

import java.util.HashSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Abstract class which handles flag decoding and encoding. Every encoder should be registered to a
 * EncodingManager to be usable. Although the flag is of type long only the int-portition is
 * currently stored.
 * <p/>
 * @author Peter Karich
 * @author Nop
 * @see EncodingManager
 */
public abstract class AbstractFlagEncoder implements FlagEncoder
{
    private final static Logger logger = LoggerFactory.getLogger(AbstractFlagEncoder.class);
    protected long forwardBit = 0;
    protected long backwardBit = 0;
    protected long directionBitMask = 0;
    protected EncodedValue speedEncoder;
    // bit to signal that way is accepted
    protected long acceptBit = 0;
    protected long ferryBit = 0;
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
                return (int) Math.round(val * DistanceCalcEarth.KM_MILE);
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
     * Analyze the properties of a relation and create the routing flags for the second read step
     * <p/>
     */
    public abstract int handleRelationTags( OSMRelation relation );
    
    /**
     * Decide whether a way is routable for a given mode of travel
     * <p/>
     * @param way
     * @return the assigned bit of the mode of travel if it is accepted or 0 for not accepted
     */
    public abstract long isAllowed( OSMWay way );
    
    /**
     * Analyze properties of a way and create the routing flags
     * <p/>
     * @param allowed
     */
    public abstract long handleWayTags( long allowed, OSMWay way, int relationweigth);

    /**
     * Parse tags on nodes, looking for barriers.
     */
    public abstract long analyzeNodeTags( OSMNode node );

    public boolean hasAccepted( int acceptedValue )
    {
        return (acceptedValue & acceptBit) > 0;
    }

    @Override
    public boolean isForward( long flags )
    {
        return (flags & forwardBit) != 0;
    }

    @Override
    public boolean isBackward( long flags )
    {
        return (flags & backwardBit) != 0;
    }

    public boolean isBoth( long flags )
    {
        return (flags & directionBitMask) == directionBitMask;
    }

    @Override
    public boolean canBeOverwritten( long flags1, long flags2 )
    {
        return isBoth(flags2) || (flags1 & directionBitMask) == (flags2 & directionBitMask);
    }

    @Override
    public int getPavementCode(long flags)
    {
        return -1;
    }
    
    @Override
    public int getWayTypeCode(long flags)
    {
        return -1;
    }
    
    public long swapDirection( long flags )
    {
        long dir = flags & directionBitMask;
        if (dir == directionBitMask || dir == 0)
            return flags;

        return flags ^ directionBitMask;
    }

    @Override
    public int getSpeed( long flags )
    {
        return (int) speedEncoder.getValue(flags);
    }

    /**
     * Sets default flags with specified access.
     */
    public long flagsDefault( boolean forward, boolean backward )
    {
        long flags = speedEncoder.setDefaultValue(0);
        return setAccess(flags, forward, backward);
    }

    @Override
    public long setAccess( long flags, boolean forward, boolean backward )
    {
        return flags | (forward ? forwardBit : 0) | (backward ? backwardBit : 0);
    }

    @Override
    public long setSpeed( long flags, int speed )
    {
        return speedEncoder.setValue(flags, speed);
    }

    @Override
    public long setProperties( int speed, boolean forward, boolean backward )
    {
        return setAccess(setSpeed(0, speed), forward, backward);
    }

    @Override
    public int getMaxSpeed()
    {
        return (int) speedEncoder.getDefaultMaxValue();
    }

    @Override
    public int hashCode()
    {
        int hash = 7;
        hash = 61 * hash + (int) this.directionBitMask;
        hash = 61 * hash + this.toString().hashCode();
        return hash;
    }

    @Override
    public boolean equals( Object obj )
    {
        if (obj == null)
            return false;

        // only rely on the string
//        if (getClass() != obj.getClass())
//            return false;
        final AbstractFlagEncoder other = (AbstractFlagEncoder) obj;
        if (this.directionBitMask != other.directionBitMask)
            return false;

        return this.toString().equals(other.toString());
    }

    public String getWayInfo( OSMWay way )
    {
        return "";
    }

    /**
     * This method parses a string ala "00:00" (hours and minutes) or "0:00:00" (days, hours and
     * minutes).
     * <p/>
     * @return duration value in minutes
     */
    protected static int parseDuration( String str )
    {
        if (str == null)
            return 0;

        int index = str.indexOf(":");
        if (index > 0)
        {
            try
            {
                String hourStr = str.substring(0, index);
                String minStr = str.substring(index + 1);
                index = minStr.indexOf(":");
                int minutes = 0;
                if (index > 0)
                {
                    // string contains hours too
                    String dayStr = hourStr;
                    hourStr = minStr.substring(0, index);
                    minStr = minStr.substring(index + 1);
                    minutes = Integer.parseInt(dayStr) * 60 * 24;
                }

                minutes += Integer.parseInt(hourStr) * 60;
                minutes += Integer.parseInt(minStr);
                return minutes;
            } catch (Exception ex)
            {
                logger.error("Cannot parse " + str + " using 0 minutes");
            }
        }
        return 0;
    }

    /**
     * Special handling for ferry ways.
     */
    protected long handleFerry( OSMWay way, int unknownSpeed, int shortTripsSpeed, int longTripsSpeed )
    {
        // to hours
        double durationInHours = parseDuration(way.getTag("duration")) / 60d;
        if (durationInHours > 0)
            try
            {   // to km
                double estimatedLength = Double.parseDouble(way.getTag("estimated_distance")) / 1000;

                // If duration AND distance is available we can calculate the speed more precisely
                // and set both speed to the same value. Factor 1.4 slower because of waiting time!
                shortTripsSpeed = (int) Math.round(estimatedLength / durationInHours / 1.4);
                if (shortTripsSpeed > getMaxSpeed())
                    shortTripsSpeed = getMaxSpeed();
                longTripsSpeed = shortTripsSpeed;
            } catch (Exception ex)
            {
            }

        if (durationInHours == 0)
        {
            // unknown speed -> put penalty on ferry transport
            return speedEncoder.setValue(0, unknownSpeed);
        } else if (durationInHours > 1)
        {
            // lengthy ferries should be faster than short trip ferry
            return speedEncoder.setValue(0, longTripsSpeed);
        } else
        {
            return speedEncoder.setValue(0, shortTripsSpeed);
        }
    }
}
