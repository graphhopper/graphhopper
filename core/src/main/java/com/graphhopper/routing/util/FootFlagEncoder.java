/*
 *  Licensed to GraphHopper and Peter Karich under one or more contributor
 *  license agreements. See the NOTICE file distributed with this work for 
 *  additional information regarding copyright ownership.
 *
 *  GraphHopper licenses this file to you under the Apache License, 
 *  Version 2.0 (the "License"); you may not use this file except in 
 *  compliance with the License. You may obtain a copy of the License at
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

import java.util.HashSet;
import java.util.Set;

import com.graphhopper.reader.OSMRelation;
import com.graphhopper.reader.OSMWay;
import java.util.*;

/**
 * Defines bit layout for pedestrians (speed, access, surface, ...).
 * <p>
 * @author Peter Karich
 * @author Nop
 * @author Karl Hübner
 */
public class FootFlagEncoder extends AbstractFlagEncoder
{
    static final int SLOW_SPEED = 2;
    static final int MEAN_SPEED = 5;
    static final int FERRY_SPEED = 10;
    private long safeWayBit = 0;
    protected HashSet<String> sidewalks = new HashSet<String>();
    private final Set<String> safeHighwayTags = new HashSet<String>();
    private final Set<String> allowedHighwayTags = new HashSet<String>();

    /**
     * Should be only instantiated via EncodingManager
     */
    public FootFlagEncoder()
    {
        this(4, 1);
    }

    public FootFlagEncoder( String propertiesStr )
    {
        this((int) parseLong(propertiesStr, "speedBits", 4),
                parseDouble(propertiesStr, "speedFactor", 1));
    }

    public FootFlagEncoder( int speedBits, double speedFactor )
    {
        super(speedBits, speedFactor, 0);
        restrictions = new ArrayList<String>(Arrays.asList("foot", "access"));
        restrictedValues.add("private");
        restrictedValues.add("no");
        restrictedValues.add("restricted");

        intendedValues.add("yes");
        intendedValues.add("designated");
        intendedValues.add("official");
        intendedValues.add("permissive");

        sidewalks.add("yes");
        sidewalks.add("both");
        sidewalks.add("left");
        sidewalks.add("right");

        blockByDefault = false;
        potentialBarriers.add("gate");

        acceptedRailways.add("station");
        acceptedRailways.add("platform");

        safeHighwayTags.add("footway");
        safeHighwayTags.add("path");
        safeHighwayTags.add("steps");
        safeHighwayTags.add("pedestrian");
        safeHighwayTags.add("living_street");
        safeHighwayTags.add("track");
        safeHighwayTags.add("residential");
        safeHighwayTags.add("service");

        allowedHighwayTags.addAll(safeHighwayTags);
        allowedHighwayTags.add("trunk");
        allowedHighwayTags.add("trunk_link");
        allowedHighwayTags.add("primary");
        allowedHighwayTags.add("primary_link");
        allowedHighwayTags.add("secondary");
        allowedHighwayTags.add("secondary_link");
        allowedHighwayTags.add("tertiary");
        allowedHighwayTags.add("tertiary_link");
        allowedHighwayTags.add("unclassified");
        allowedHighwayTags.add("road");
        // disallowed in some countries
        //allowedHighwayTags.add("bridleway");
    }

    @Override
    public int defineWayBits( int index, int shift )
    {
        // first two bits are reserved for route handling in superclass
        shift = super.defineWayBits(index, shift);
        // larger value required - ferries are faster than pedestrians
        speedEncoder = new EncodedDoubleValue("Speed", shift, speedBits, speedFactor, MEAN_SPEED, FERRY_SPEED);
        shift += speedBits;

        safeWayBit = 1L << shift++;
        return shift;
    }

    /**
     * Foot flag encoder does not provide any turn cost / restrictions
     */
    @Override
    public int defineTurnBits( int index, int shift )
    {
        return shift;
    }

    /**
     * Foot flag encoder does not provide any turn cost / restrictions
     * <p>
     * @return <code>false</code>
     */
    @Override
    public boolean isTurnRestricted( long flag )
    {
        return false;
    }

    /**
     * Foot flag encoder does not provide any turn cost / restrictions
     * <p>
     * @return 0
     */
    @Override
    public double getTurnCost( long flag )
    {
        return 0;
    }

    @Override
    public long getTurnFlags( boolean restricted, double costs )
    {
        return 0;
    }

    @Override
    public String toString()
    {
        return "foot";
    }

    /**
     * Some ways are okay but not separate for pedestrians.
     * <p/>
     * @param way
     */
    @Override
    public long acceptWay( OSMWay way )
    {
        String highwayValue = way.getTag("highway");
        if (highwayValue == null)
        {
            if (way.hasTag("route", ferries))
            {
                String footTag = way.getTag("foot");
                if (footTag == null || "yes".equals(footTag))
                    return acceptBit | ferryBit;
            }
            return 0;
        }

        String sacScale = way.getTag("sac_scale");
        if (sacScale != null)
        {
            if (!"hiking".equals(sacScale) && !"mountain_hiking".equals(sacScale)
                    && !"demanding_mountain_hiking".equals(sacScale) && !"alpine_hiking".equals(sacScale))
                // other scales are too dangerous, see http://wiki.openstreetmap.org/wiki/Key:sac_scale
                return 0;
        }

        if (way.hasTag("sidewalk", sidewalks))
            return acceptBit;

        // no need to evaluate ferries or fords - already included here
        if (way.hasTag("foot", intendedValues))
            return acceptBit;

        if (!allowedHighwayTags.contains(highwayValue))
            return 0;

        if (way.hasTag("motorroad", "yes"))
            return 0;

        // do not get our feet wet, "yes" is already included above
        if (blockFords && (way.hasTag("highway", "ford") || way.hasTag("ford")))
            return 0;

        if (way.hasTag("bicycle", "official"))
            return 0;

        // check access restrictions
        if (way.hasTag(restrictions, restrictedValues))
            return 0;

        // do not accept railways (sometimes incorrectly mapped!)
        if (way.hasTag("railway") && !way.hasTag("railway", acceptedRailways))
            return 0;

        return acceptBit;
    }

    @Override
    public long handleRelationTags( OSMRelation relation, long oldRelationFlags )
    {
        return oldRelationFlags;
    }

    @Override
    public long handleWayTags( OSMWay way, long allowed, long relationCode )
    {
        if (!isAccept(allowed))
            return 0;

        long encoded;
        if (!isFerry(allowed))
        {
            String sacScale = way.getTag("sac_scale");
            if (sacScale != null)
            {
                if ("hiking".equals(sacScale))
                    encoded = speedEncoder.setDoubleValue(0, MEAN_SPEED);
                else
                    encoded = speedEncoder.setDoubleValue(0, SLOW_SPEED);
            } else
            {
                encoded = speedEncoder.setDoubleValue(0, MEAN_SPEED);
            }
            encoded |= directionBitMask;

            // mark safe ways or ways with cycle lanes
            if (safeHighwayTags.contains(way.getTag("highway"))
                    || way.hasTag("sidewalk", sidewalks))
            {
                encoded |= safeWayBit;
            }

        } else
        {
            encoded = handleFerryTags(way, SLOW_SPEED, MEAN_SPEED, FERRY_SPEED);
            encoded |= directionBitMask;
        }

        return encoded;
    }
}
