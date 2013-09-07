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

import java.util.HashSet;
import java.util.Set;

/**
 * @author Peter Karich
 * @author Nop
 */
public class FootFlagEncoder extends AbstractFlagEncoder
{
    private int safeWayBit = 0;
    protected HashSet<String> intended = new HashSet<String>();
    protected HashSet<String> sidewalks = new HashSet<String>();

    /**
     * Should be only instantied via EncodingManager
     */
    protected FootFlagEncoder()
    {
        restrictions = new String[]
        {
            "foot", "access"
        };
        restrictedValues.add("private");
        restrictedValues.add("no");
        restrictedValues.add("restricted");

        intended.add("yes");
        intended.add("designated");
        intended.add("official");
        intended.add("permissive");

        sidewalks.add("yes");
        sidewalks.add("both");
        sidewalks.add("left");
        sidewalks.add("right");

        potentialBarriers.add("gate");
        //potentialBarriers.add( "lift_gate" );   you can always pass them on foot
        potentialBarriers.add("swing_gate");

        acceptedRailways.add("station");
        acceptedRailways.add("platform");
    }

    @Override
    public int defineBits( int index, int shift )
    {
        // first two bits are reserved for route handling in superclass
        shift = super.defineBits(index, shift);

        // larger value required - ferries are faster than pedestrians
        speedEncoder = new EncodedValue("Speed", shift, 4, 1, MEAN, FERRY);
        shift += 4;

        safeWayBit = 1 << shift++;
        return shift;
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
    public int isAllowed( OSMWay way )
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
            if (!"hiking".equals(sacScale) && !"mountain_hiking".equals(sacScale))
                // other scales are too dangerous, see http://wiki.openstreetmap.org/wiki/Key:sac_scale
                return 0;
        }

        if (way.hasTag("sidewalk", sidewalks))
            return acceptBit;

        // no need to evaluate ferries or fords - already included here
        if (way.hasTag("foot", intended))
            return acceptBit;

        if (!allowedHighwayTags.contains(highwayValue))
            return 0;

        if (way.hasTag("motorroad", "yes"))
            return 0;

        // do not get our feet wet, "yes" is already included above
        if (way.hasTag("highway", "ford") || way.hasTag("ford"))
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
    public int handleWayTags( int allowed, OSMWay way )
    {
        if ((allowed & acceptBit) == 0)
            return 0;

        int encoded;
        if ((allowed & ferryBit) == 0)
        {
            String sacScale = way.getTag("sac_scale");
            if (sacScale != null)
            {
                if ("hiking".equals(sacScale))
                    encoded = speedEncoder.setValue(0, MEAN);
                else
                    encoded = speedEncoder.setValue(0, SLOW);
            } else
            {
                encoded = speedEncoder.setValue(0, MEAN);
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
            int durationInMinutes = parseDuration(way.getTag("duration"));
            if (durationInMinutes == 0)
            {
                // unknown speed -> put penalty on ferry transport
                encoded = speedEncoder.setValue(0, SLOW);
            } else if (durationInMinutes > 60)
            {
                // lengthy ferries should be faster than average hiking
                encoded = speedEncoder.setValue(0, FERRY);
            } else
            {
                encoded = speedEncoder.setValue(0, MEAN);
            }

            encoded |= directionBitMask;
        }

        return encoded;
    }

    static int parseDuration( String str )
    {
        if (str == null)
            return 0;

        int index = str.indexOf(":");
        if (index > 0)
        {
            int minutes = Integer.parseInt(str.substring(0, index)) * 60;
            minutes += Integer.parseInt(str.substring(index + 1));
            return minutes;
        } else
        {
            return 0;
        }
    }

    @Override
    public int analyzeNodeTags( OSMNode node )
    {

        // movable barriers block if they are not marked as passable
        if (node.hasTag("barrier", potentialBarriers)
                && !node.hasTag(restrictions, intended)
                && !node.hasTag("locked", "no"))
        {
            return directionBitMask;
        }

        if ((node.hasTag("highway", "ford") || node.hasTag("ford"))
                && !node.hasTag(restrictions, intended))
        {
            return directionBitMask;
        }

        return 0;
    }
    private final Set<String> safeHighwayTags = new HashSet<String>()
    {
        
        {
            add("footway");
            add("path");
            add("steps");
            add("pedestrian");
            add("living_street");
            add("track");
            add("residential");
            add("service");
        }
    };
    private final Set<String> allowedHighwayTags = new HashSet<String>()
    {
        
        {
            addAll(safeHighwayTags);
            add("trunk");
            add("trunk_link");
            add("primary");
            add("primary_link");
            add("secondary");
            add("secondary_link");
            add("tertiary");
            add("tertiary_link");
            add("unclassified");
            add("road");
            // disallowed in some countries
            //add("bridleway");
        }
    };
    static final int SLOW = 2;
    static final int MEAN = 5;
    static final int FERRY = 10;
}
