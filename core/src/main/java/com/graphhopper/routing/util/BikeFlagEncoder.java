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
import com.graphhopper.util.Helper;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Use this FlagEncoder for bicycle support (not motorbikes).
 * <p/>
 * @author Peter Karich
 * @author Nop
 */
public class BikeFlagEncoder extends AbstractFlagEncoder
{
    private int safeWayBit = 0;
    private HashSet<String> intended = new HashSet<String>();
    private HashSet<String> oppositeLanes = new HashSet<String>();

    /**
     * Should be only instantied via EncodingManager
     */
    protected BikeFlagEncoder()
    {
        // strict set, usually vehicle and agricultural/forestry are ignored by cyclists
        restrictions = new String[]
        {
            "bicycle", "access"
        };
        restrictedValues.add("private");
        restrictedValues.add("no");
        restrictedValues.add("restricted");

        intended.add("yes");
        intended.add("designated");
        intended.add("official");
        intended.add("permissive");

        oppositeLanes.add("opposite");
        oppositeLanes.add("opposite_lane");
        oppositeLanes.add("opposite_track");
   
        potentialBarriers.add("gate");
        potentialBarriers.add("lift_gate");
        potentialBarriers.add("swing_gate");
        potentialBarriers.add("cycle_barrier");
        potentialBarriers.add("block");

        absoluteBarriers.add("kissing_gate");
        absoluteBarriers.add("stile");
        absoluteBarriers.add("turnstile");

        // very dangerous
        // acceptedRailways.remove("tram");
    }

    @Override
    public int defineBits( int index, int shift )
    {
        // first two bits are reserved for route handling in superclass
        shift = super.defineBits(index, shift);

        speedEncoder = new EncodedValue("Speed", shift, 4, 2, HIGHWAY_SPEED.get("cycleway"), HIGHWAY_SPEED.get("primary"));
        shift += 4;

        safeWayBit = 1 << shift++;

        return shift;
    }

    @Override
    public String toString()
    {
        return "bike";
    }

    /**
     * Separate ways for pedestrians.
     * <p/>
     * @param way
     */
    @Override
    public long isAllowed( OSMWay way )
    {
        String highwayValue = way.getTag("highway");
        if (highwayValue == null)
        {

            if (way.hasTag("route", ferries))
            {
                // if bike is NOT explictly tagged allow bike but only if foot is not specified
                String bikeTag = way.getTag("bicycle");
                if (bikeTag == null && !way.hasTag("foot") || "yes".equals(bikeTag))
                    return acceptBit | ferryBit;
            }
            return 0;
        }

        if (!HIGHWAY_SPEED.containsKey(highwayValue))
            return 0;

        // use the way if it is tagged for bikes
        if (way.hasTag("bicycle", intended))
            return acceptBit;

        // avoid paths that are not tagged for bikes.
        if (way.hasTag("highway", "path"))
            return 0;

        if (way.hasTag("motorroad", "yes"))
            return 0;

        // do not use fords with normal bikes, flagged fords are in included above
        if (way.hasTag("highway", "ford") || way.hasTag("ford"))
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
    public int handleRelationTags( OSMRelation relation )
    {
        if (relation.hasTag("route", "bicycle"))
        {
           try
           {
             if (relation.getTag("network").isEmpty() )
             {
               return relationMapCode.UNCHANGED.getValue();
             }
             else
             {
               return (BIKE_NETWORK_TO_CODE.get(relation.getTag("network")));
             }
           }
           catch ( Exception ex)
                   {
                       return relationMapCode.UNCHANGED.getValue();
                   }
        }
        else
        {
           if (relation.hasTag("route", "mtb"))
               return BIKE_NETWORK_TO_CODE.get("mtb");
           else
               return relationMapCode.UNCHANGED.getValue();
        }
    }
    
    @Override
    public long handleWayTags( long allowed, OSMWay way, int relationcode)
    {
        if ((allowed & acceptBit) == 0)
            return 0;

        long encoded;
        if ((allowed & ferryBit) == 0)
        {
            
            // set speed
            // FIXME Rewrite necessary after decision for other weighting than speed!
            // Currently there is only speed, so we increase it.
            int speed;
            // relationcode = 0 : This happens for e.g. ways with a bus or hiking relation
            if ((relationcode == -1) || (relationcode == 0))
            {
                // In case that the way does not belong to a relation:
                speed=getSpeed(way);
            }
            else
            {
                // In case that the way belongs to a relation for which we do have a weight change:
                int boostpercent;
                boostpercent=100 + (relationcode-4)*33;
                speed=getSpeed(way);
                if (speed<20) 
                    speed=20;
                speed=speed*(boostpercent/100);
            }
            
            encoded = speedEncoder.setValue(0, speed);

            // handle oneways
            if ((way.hasTag("oneway", oneways) || way.hasTag("junction", "roundabout"))
                    && !way.hasTag("oneway:bicycle", "no")
                    && !way.hasTag("cycleway", oppositeLanes))
            {

                if (way.hasTag("oneway", "-1"))
                {
                    encoded |= backwardBit;
                } else
                {
                    encoded |= forwardBit;
                }
            } else
            {
                encoded |= directionBitMask;
            }

            // mark safe ways or ways with cycle lanes
            if (safeHighwayTags.contains(way.getTag("highway"))
                    || way.hasTag("cycleway"))
            {
                encoded |= safeWayBit;
            }

        } else
        {
            encoded = handleFerry(way,
                    HIGHWAY_SPEED.get("living_street"),
                    HIGHWAY_SPEED.get("track"),
                    HIGHWAY_SPEED.get("primary"));
            encoded |= directionBitMask;
        }
        return encoded;
    }

    @Override
    public long analyzeNodeTags( OSMNode node )
    {

        // absolute barriers always block
        if (node.hasTag("barrier", absoluteBarriers))
        {
            return directionBitMask;
        }

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

    int getSpeed( OSMWay way )
    {
        String s = way.getTag("surface");
        if (!Helper.isEmpty(s))
        {
            Integer sInt = SURFACE_SPEED.get(s);
            if (sInt != null)
            {
                return sInt;
            }
        }
        String tt = way.getTag("tracktype");
        if (!Helper.isEmpty(tt))
        {
            Integer tInt = TRACKTYPE_SPEED.get(tt);
            if (tInt != null)
                return tInt;            
        }
        String highway = way.getTag("highway");
        if (!Helper.isEmpty(highway))
        {
            Integer hwInt = HIGHWAY_SPEED.get(highway);
            if (hwInt != null)
            {
                return hwInt;
            }
        }
        return 10;
    }
    private final Set<String> safeHighwayTags = new HashSet<String>()
    {
        {
            add("cycleway");
            add("path");
            add("living_street");
            add("track");
            add("service");
            add("unclassified");
            add("residential");
        }
    };
    
    private static final Map<String, Integer> TRACKTYPE_SPEED = new HashMap<String, Integer>()
    {
        {
            put("grade1", 16); // paved
            put("grade2", 12); // now unpaved ...
            put("grade3", 12);
            put("grade4", 10);
            put("grade5", 8); // like sand/grass            
        }
    };
    
    private static final Map<String, Integer> SURFACE_SPEED = new HashMap<String, Integer>()
    {   
        {
            put("asphalt", 20);
            put("concrete", 20);
            put("paved", 18);
            put("unpaved", 15);
            put("gravel", 12);
            put("ground", 12);
            put("dirt", 10);
            put("paving_stones", 8);
            put("grass", 8);
            put("cobblestone", 6);
        }
    };
    
    private static final Map<String, Integer> HIGHWAY_SPEED = new HashMap<String, Integer>()
    {
        {
            put("living_street", 15);
            put("steps", 4);

            put("cycleway", 20);
            put("path", 18);
            put("footway", 15);
            put("road", 10);
            put("track", 20);
            put("service", 20);
            put("unclassified", 25);
            put("residential", 20);

            put("trunk", 16);
            put("trunk_link", 16);
            put("primary", 18);
            put("primary_link", 16);
            put("secondary", 18);
            put("secondary_link", 16);
            put("tertiary", 18);
            put("tertiary_link", 16);
        }
    };
    
    private enum relationMapCode
    {
       /* Inspired by http://wiki.openstreetmap.org/wiki/Class:bicycle
       "-3" = Avoid at all cost. 
       "-2" = Only use to reach your destination, not well suited. 
       "-1" = Better take another way 
       "0" = as well as other ways around. 
          Try to to avoid using 0 but decide on -1 or +1. 
          class:bicycle shall only be used as an additional key. 
       "1" = Prefer 
       "2" = Very Nice way to cycle 
       "3" = This way is so nice, it pays out to make a detour also if this means taking 
             many unsuitable ways to get here. Outstanding for its intended usage class.
        */
        //We can't store negative numbers into our map, therefore we add 3
        AVOID_AT_ALL_COSTS(1), REACH_DEST(2), AVOID_IF_POSSIBLE(3), UNCHANGED(4) , PREFER(5), VERY_NICE(6), OUTSTANDING_NICE(7);
        
        private final int value;
        private relationMapCode(int value) {
           this.value = value;
        }

        public int getValue() {
           return value;
        }
    };
    
    //Convert bicycle route network tag into way route code stored in MAP
    private static final Map<String, Integer> BIKE_NETWORK_TO_CODE = new HashMap<String, Integer>()
    {
        {
            put("icn", relationMapCode.VERY_NICE.getValue());
            put("ncn", relationMapCode.VERY_NICE.getValue());
            put("rcn", relationMapCode.PREFER.getValue());
            put("lcn", relationMapCode.UNCHANGED.getValue());
            put("mtb", relationMapCode.UNCHANGED.getValue());
            put("deprecated", relationMapCode.AVOID_AT_ALL_COSTS.getValue());
        }
    };    
}
