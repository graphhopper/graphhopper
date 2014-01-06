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

import com.graphhopper.reader.OSMNode;
import com.graphhopper.reader.OSMWay;
import com.graphhopper.reader.OSMRelation;
import com.graphhopper.util.Helper;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Defines bit layout of bicycles (not motorbikes) for speed, access and relations (network).
 * <p/>
 * @author Peter Karich
 * @author Nop
 * @author ratrun
 */
public class BikeFlagCommonEncoder extends AbstractFlagEncoder
{
    private static final int DEFAULT_REL_CODE = 4;
    public static final int PUSHING_SECTION_SPEED = 4;
    // private int safeWayBit = 0;
    private int unpavedBit = 0;
    // Pushing section heighways are parts where you need to get off your bike and push it (German: Schiebestrecke)
    private final HashSet<String> pushingSections = new HashSet<String>();
    private final HashSet<String> intended = new HashSet<String>();
    private final HashSet<String> oppositeLanes = new HashSet<String>();
    protected EncodedValue relationCodeEncoder;
    private EncodedValue wayTypeEncoder;

    /**
     * Should be only instantied via EncodingManager
     */
    protected BikeFlagCommonEncoder()
    {
        super(4, 2);
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

        // With a bike one usually can pass all those barriers:
        // potentialBarriers.add("gate");
        // potentialBarriers.add("lift_gate");
        // potentialBarriers.add("swing_gate");
        // potentialBarriers.add("cycle_barrier");
        // potentialBarriers.add("block");
        absoluteBarriers.add("kissing_gate");
        absoluteBarriers.add("stile");
        absoluteBarriers.add("turnstile");
        // very dangerous
        // acceptedRailways.remove("tram");

/*        
        SAFE_HIGHWAY_TAGS.add("cycleway");
        SAFE_HIGHWAY_TAGS.add("path");
        SAFE_HIGHWAY_TAGS.add("footway");
        SAFE_HIGHWAY_TAGS.add("pedestrian");
        SAFE_HIGHWAY_TAGS.add("living_street");
        SAFE_HIGHWAY_TAGS.add("track");
        SAFE_HIGHWAY_TAGS.add("service");
        SAFE_HIGHWAY_TAGS.add("unclassified");
        SAFE_HIGHWAY_TAGS.add("residential");
        SAFE_HIGHWAY_TAGS.add("steps");
*/
        UNPAVED_SURFACE_TAGS.add("unpaved");
        UNPAVED_SURFACE_TAGS.add("gravel");
        UNPAVED_SURFACE_TAGS.add("ground");
        UNPAVED_SURFACE_TAGS.add("dirt");
        UNPAVED_SURFACE_TAGS.add("paving_stones");
        UNPAVED_SURFACE_TAGS.add("grass");
        UNPAVED_SURFACE_TAGS.add("cobblestone");

        ROAD.add("living_street");
        ROAD.add("road");
        ROAD.add("service");
        ROAD.add("unclassified");
        ROAD.add("residential");
        ROAD.add("trunk");
        ROAD.add("trunk_link");
        ROAD.add("primary");
        ROAD.add("primary_link");
        ROAD.add("secondary");
        ROAD.add("secondary_link");
        ROAD.add("tertiary");
        ROAD.add("tertiary_link");

        setCyclingNetworkPreference("deprecated", RelationMapCode.AVOID_AT_ALL_COSTS.getValue());        
    }
    
    @Override
    public int defineWayBits( int index, int shift )
    {
        // first two bits are reserved for route handling in superclass
        shift = super.defineWayBits(index, shift);
        speedEncoder = new EncodedValue("Speed", shift, speedBits, speedFactor, HIGHWAY_SPEED.get("cycleway"), 30);
        shift += speedBits;

        //safeWayBit = 1 << shift++;
        unpavedBit = 1 << shift++;
        // 2 bits
        wayTypeEncoder = new EncodedValue("WayType", shift, 2, 1, 0, 3);
        return shift + 2;
    }

    @Override
    public int defineRelationBits( int index, int shift )
    {
        relationCodeEncoder = new EncodedValue("RelationCode", shift, 3, 1, 0, 7);
        return shift + 3;
    }
    
    @Override
    public long acceptWay( OSMWay way )
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
    public long handleRelationTags( OSMRelation relation, long oldRelationFlags )
    {
        int code = RelationMapCode.UNCHANGED.getValue();
        if (relation.hasTag("route", "bicycle"))
        {
            Integer val = BIKE_NETWORK_TO_CODE.get(relation.getTag("network"));
            if (val != null)
                code = val;
        }
        int oldCode = (int) relationCodeEncoder.getValue(oldRelationFlags);
        if (oldCode < code)
            return relationCodeEncoder.setValue(0, code);
        return oldRelationFlags;
    }

    // In case that the way belongs to a relation for which we do have a relation triggered weight change.    
    // FIXME: Re-write in case that there is a more geneic way to influence the weighting (issue #124).
    // Here we boost or reduce the speed according to the relationWeightCode:
    int relationWeightCodeToSpeed( int highwaySpeed, int relationCode )
    {
        int speed;
        if (highwaySpeed < 15)
            // We know that our way belongs to a cycle route, so we assume 15km/h minimum
            speed = 15;
        else
            speed = highwaySpeed;
        // Add or remove 3km/h per every relation weight boost point
        return speed + 3 * (relationCode - DEFAULT_REL_CODE);
    }

    @Override
    public long handleWayTags( OSMWay way, long allowed, long relationFlags )
    {
        if ((allowed & acceptBit) == 0)
            return 0;

        long encoded;
        if ((allowed & ferryBit) == 0)
        {
            // set speed
            // FIXME Rewrite necessary after decision #124 for other weighting than speed!
            // Currently there is only speed, so we increase it.
            int speed;
            if (relationFlags == 0)
            {
                // In case that the way does not belong to a relation
                speed = getSpeed(way);
            } else
            {
                speed = relationWeightCodeToSpeed(getSpeed(way), (int) relationCodeEncoder.getValue(relationFlags));
            }

            // Make sure that we do not exceed the limits:
            if (speed > speedEncoder.getMaxValue())
                speed = (int) speedEncoder.getMaxValue();
            else if (speed < 0)
                speed = 0;
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

            String highway = way.getTag("highway");

            /*            
            // mark safe ways or ways with cycle lanes
            if (SAFE_HIGHWAY_TAGS.contains(highway) || way.hasTag("cycleway"))
            {
                encoded |= safeWayBit;
            }
            */

            // mark unpaved bit
            String surfaceTag = way.getTag("surface");
            String trackType = way.getTag("tracktype");
            if ("track".equals(highway) && trackType == null
                    || ("track".equals(highway) && !"grade1".equals(trackType))
                    || (surfaceTag == null && way.hasTag("highway", "path"))
                    || UNPAVED_SURFACE_TAGS.contains(surfaceTag))
            {
                encoded |= unpavedBit;
            }

            // Populate bits at wayTypeMask with wayType            
            WayType ourWayType = WayType.OTHER_SMALL_WAY;
            if (way.hasTag("highway", pushingSections))
                ourWayType = WayType.PUSHING_SECTION;
            if ((way.hasTag("bicycle", intended) && way.hasTag("highway", pushingSections))
                    || ("cycleway".equals(way.getTag("highway"))))
                ourWayType = WayType.CYCLEWAY;
            if (way.hasTag("highway", ROAD))
                ourWayType = WayType.ROAD;

            encoded = wayTypeEncoder.setValue(encoded, ourWayType.getValue());

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
            return directionBitMask;

        return super.analyzeNodeTags(node);
    }

    int getSpeed( OSMWay way )
    {
        if (!way.hasTag("bicycle", intended) && way.hasTag("highway", pushingSections))
            if (way.hasTag("highway", "steps"))
                return PUSHING_SECTION_SPEED / 2;
            else
                return PUSHING_SECTION_SPEED;

        String s = way.getTag("surface");
        if (!Helper.isEmpty(s))
        {
            Integer sInt = SURFACE_SPEED.get(s);
            if (sInt != null)
                return sInt;
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
            if (hwInt != null) {
                if (way.getTag("service") == null)
                   return hwInt;
                else
                   return HIGHWAY_SPEED.get("living_street");
            }
        }
        return 10;
    }

    @Override
    public int getPavementCode( long flags )
    {
        if ((flags & unpavedBit) != 0)
            return 1; // unpaved
        else
            return 0; // paved
    }

    @Override
    public int getWayTypeCode( long flags )
    {
        return (int) wayTypeEncoder.getValue(flags);
    }

    public enum RelationMapCode
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
        //We can't store negative numbers into our map, therefore we add 
        //unspecifiedRelationWeight=4 to the schema from above
        AVOID_AT_ALL_COSTS(1),
        REACH_DEST(2),
        AVOID_IF_POSSIBLE(3),
        UNCHANGED(DEFAULT_REL_CODE),
        PREFER(5),
        VERY_NICE(6),
        OUTSTANDING_NICE(7);

        private final int value;

        private RelationMapCode( int value )
        {
            this.value = value;
        }

        public int getValue()
        {
            return value;
        }
    };

    private enum WayType
    {
        ROAD(0),
        PUSHING_SECTION(1),
        CYCLEWAY(2),
        OTHER_SMALL_WAY(3);

        private final int value;

        private WayType( int value )
        {
            this.value = value;
        }

        public int getValue()
        {
            return value;
        }

    };

    private final Set<String> SAFE_HIGHWAY_TAGS = new HashSet<String>();
    private final Set<String> UNPAVED_SURFACE_TAGS = new HashSet<String>();
    private final Map<String, Integer> TRACKTYPE_SPEED = new HashMap<String, Integer>();
    private final Map<String, Integer> SURFACE_SPEED = new HashMap<String, Integer>();
    private final Set<String> ROAD = new HashSet<String>();
    private final Map<String, Integer> HIGHWAY_SPEED = new HashMap<String, Integer>();
    //Convert network tag of bicycle routes into a way route code stored in the wayMAP
    private final Map<String, Integer> BIKE_NETWORK_TO_CODE = new HashMap<String, Integer>();
    
    public void setTrackTypeSpeed(String tracktype, int speed)
    {
       TRACKTYPE_SPEED.put(tracktype, speed); 
    }
    
    public void setSurfaceSpeed(String surface,int speed)
    {
       SURFACE_SPEED.put(surface,speed);
    }

    public void setHighwaySpeed(String highway,int speed)
    {
       HIGHWAY_SPEED.put(highway,speed);
    }
    
    public void setCyclingNetworkPreference(String network,int code)
    {
        BIKE_NETWORK_TO_CODE.put(network,code);
    }
    
    public void setPushingSection(String highway)
    {
        pushingSections.add(highway);
    }
    
}
