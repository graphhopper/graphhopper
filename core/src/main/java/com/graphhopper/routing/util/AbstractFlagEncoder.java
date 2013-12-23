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
    private long nodeBitMask;
    private long wayBitMask;
    private long relBitMask;
    protected long forwardBit = 0;
    protected long backwardBit = 0;
    protected long directionBitMask = 0;
    protected EncodedValue speedEncoder;
    // bit to signal that way is accepted
    protected long acceptBit = 0;
    protected long ferryBit = 0;
    // restriction definitions
    protected String[] restrictions;
    protected HashSet<String> intended = new HashSet<String>();
    protected HashSet<String> restrictedValues = new HashSet<String>(5);
    protected HashSet<String> ferries = new HashSet<String>(5);
    protected HashSet<String> oneways = new HashSet<String>(5);
    protected HashSet<String> acceptedRailways = new HashSet<String>(5);
    protected HashSet<String> absoluteBarriers = new HashSet<String>(5);
    protected HashSet<String> potentialBarriers = new HashSet<String>(5);
    protected int speedBits;
    protected int speedFactor;

    public AbstractFlagEncoder( int speedBits, int speedFactor )
    {
        this.speedBits = speedBits;
        this.speedFactor = speedFactor;
        oneways.add("yes");
        oneways.add("true");
        oneways.add("1");
        oneways.add("-1");

        ferries.add("shuttle_train");
        ferries.add("ferry");

        acceptedRailways.add("tram");
    }

    public void setSpeedFactor( int factor )
    {
        this.speedFactor = factor;
    }

    public void setSpeedBits( int bits )
    {
        this.speedBits = bits;
    }

    /**
     * Defines the bits for the node flags, which are currently used for barriers only.
     * <p>
     * @return incremented shift value pointing behind the last used bit
     */
    public int defineNodeBits( int index, int shift )
    {
        return shift;
    }

    /**
     * Defines bits used for edge flags used for access, speed etc.
     * <p/>
     * @param index
     * @param shift bit offset for the first bit used by this encoder
     * @return incremented shift value pointing behind the last used bit
     */
    public int defineWayBits( int index, int shift )
    {
        // define the first 2 speedBits in flags for routing
        forwardBit = 1 << shift;
        backwardBit = 2 << shift;
        directionBitMask = 3 << shift;

        // define internal flags for parsing
        index *= 2;
        acceptBit = 1 << index;
        ferryBit = 2 << index;

        // forward and backward bit:
        return shift + 2;
    }

    /**
     * Defines the bits which are used for relation flags.
     * <p>
     * @return incremented shift value pointing behind the last used bit
     */
    public int defineRelationBits( int index, int shift )
    {
        return shift;
    }

    /**
     * Analyze the properties of a relation and create the routing flags for the second read step
     * <p/>
     */
    public abstract long handleRelationTags( OSMRelation relation, long oldRelationFlags );

    /**
     * Decide whether a way is routable for a given mode of travel
     * <p/>
     * @return the encoded value to indicate if this encoder allows travel or not.
     */
    public abstract long acceptWay( OSMWay way );

    /**
     * Analyze properties of a way and create the routing flags
     * <p/>
     * @param acceptWay return value from acceptWay
     */
    public abstract long handleWayTags( OSMWay way, long allowed, long relationFlags );

    /**
     * Parse tags on nodes. Node tags can add to speed (like traffic_signals) where the value is
     * strict negative or blocks access (like a barrier), then the value is strict positive.
     */
    public long analyzeNodeTags( OSMNode node )
    {
        // movable barriers block if they are not marked as passable
        if (node.hasTag("barrier", potentialBarriers)
                && !node.hasTag(restrictions, intended)
                && !node.hasTag("locked", "no"))
            return directionBitMask;

        if ((node.hasTag("highway", "ford") || node.hasTag("ford"))
                && !node.hasTag(restrictions, intended))
            return directionBitMask;

        return 0;
    }

    public long applyNodeFlags( long wayFlags, long nodeFlags )
    {
        return nodeFlags | wayFlags;
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
    public int getPavementCode( long flags )
    {
        return -1;
    }

    @Override
    public int getWayTypeCode( long flags )
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
        return (int) speedEncoder.getMaxValue();
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
            {
                Double estimatedLength = way.getInternalTag("estimated_distance", null);
                if (estimatedLength != null)
                {
                    // to km
                    estimatedLength /= 1000;
                    // If duration AND distance is available we can calculate the speed more precisely
                    // and set both speed to the same value. Factor 1.4 slower because of waiting time!
                    shortTripsSpeed = (int) Math.round(estimatedLength / durationInHours / 1.4);
                    if (shortTripsSpeed > getMaxSpeed())
                        shortTripsSpeed = getMaxSpeed();
                    longTripsSpeed = shortTripsSpeed;
                }
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

    void setWayBitMask( int usedBits, int shift )
    {
        wayBitMask = (1L << usedBits) - 1;
        wayBitMask <<= shift;
    }

    long getWayBitMask()
    {
        return wayBitMask;
    }

    void setRelBitMask( int usedBits, int shift )
    {
        relBitMask = (1L << usedBits) - 1;
        relBitMask <<= shift;
    }

    void setNodeBitMask( int usedBits, int shift )
    {
        nodeBitMask = (1L << usedBits) - 1;
        nodeBitMask <<= shift;
    }

    long getNodeBitMask()
    {
        return nodeBitMask;
    }
}
