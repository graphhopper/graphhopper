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
    protected static final int DEFAULT_REL_CODE = 4;
    protected static final int PUSHING_SECTION_SPEED = 4;
    // private int safeWayBit = 0;
    private int unpavedBit = 0;
    // Pushing section heighways are parts where you need to get off your bike and push it (German: Schiebestrecke)
    private final HashSet<String> pushingSections = new HashSet<String>();
    private final HashSet<String> oppositeLanes = new HashSet<String>();
    private final Set<String> unpavedSurfaceTags = new HashSet<String>();
    private final Map<String, Integer> trackTypeSpeed = new HashMap<String, Integer>();
    private final Map<String, Integer> surfaceSpeed = new HashMap<String, Integer>();
    private final Set<String> roadValues = new HashSet<String>();
    private final Map<String, Integer> highwaySpeed = new HashMap<String, Integer>();
    //Convert network tag of bicycle routes into a way route code stored in the wayMAP
    private final Map<String, Integer> bikeNetworkToCode = new HashMap<String, Integer>();
    protected EncodedValue relationCodeEncoder;
    private EncodedValue wayTypeEncoder;

    /**
     * Should be only instantied via EncodingManager
     */
    protected BikeFlagCommonEncoder()
    {
        this(4, 2);
    }

    protected BikeFlagCommonEncoder( int speedBits, double speedFactor )
    {
        super(speedBits, speedFactor);
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

        unpavedSurfaceTags.add("unpaved");
        unpavedSurfaceTags.add("gravel");
        unpavedSurfaceTags.add("ground");
        unpavedSurfaceTags.add("dirt");
        unpavedSurfaceTags.add("grass");
        unpavedSurfaceTags.add("compacted");
        unpavedSurfaceTags.add("earth");
        unpavedSurfaceTags.add("fine_gravel");
        unpavedSurfaceTags.add("grass_paver");
        unpavedSurfaceTags.add("ice");
        unpavedSurfaceTags.add("mud");
        unpavedSurfaceTags.add("salt");
        unpavedSurfaceTags.add("sand");
        unpavedSurfaceTags.add("wood");

        roadValues.add("living_street");
        roadValues.add("road");
        roadValues.add("service");
        roadValues.add("unclassified");
        roadValues.add("residential");
        roadValues.add("trunk");
        roadValues.add("trunk_link");
        roadValues.add("primary");
        roadValues.add("primary_link");
        roadValues.add("secondary");
        roadValues.add("secondary_link");
        roadValues.add("tertiary");
        roadValues.add("tertiary_link");

        setCyclingNetworkPreference("deprecated", RelationMapCode.AVOID_AT_ALL_COSTS.getValue());
    }

    @Override
    public int defineWayBits( int index, int shift )
    {
        // first two bits are reserved for route handling in superclass
        shift = super.defineWayBits(index, shift);
        speedEncoder = new EncodedDoubleValue("Speed", shift, speedBits, speedFactor, highwaySpeed.get("cycleway"), 30);
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

        if (!highwaySpeed.containsKey(highwayValue))
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
            Integer val = bikeNetworkToCode.get(relation.getTag("network"));
            if (val != null)
                code = val;
        }
        int oldCode = (int) relationCodeEncoder.getValue(oldRelationFlags);
        if (oldCode < code)
            return relationCodeEncoder.setValue(0, code);
        return oldRelationFlags;
    }

    // In case that the way belongs to a relation for which we do have a relation triggered weight change.    
    // FIXME: Re-write in case that there is a more generic way to influence the weighting (issue #124).
    // Here we boost or reduce the speed according to the relationWeightCode:
    int relationWeightCodeToSpeed( int highwaySpeed, int relationCode )
    {
        int speed;
        if (highwaySpeed < 15)
            // We know that our way belongs to a cycle route, so we are optimistic and assume 15km/h minimum,
            // irrespective of the tracktype and surface
            speed = 15;
        else
            speed = highwaySpeed;
        // Add or remove 4km/h per every relation weight boost point
        return speed + 4 * (relationCode - DEFAULT_REL_CODE);
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
            double speed;
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
                speed = speedEncoder.getMaxValue();
            else if (speed < 0)
                speed = 0;
            encoded = setSpeed(0, speed);

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
                    || unpavedSurfaceTags.contains(surfaceTag))
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
            if (way.hasTag("highway", roadValues))
                ourWayType = WayType.ROAD;

            encoded = wayTypeEncoder.setValue(encoded, ourWayType.getValue());

        } else
        {
            encoded = handleFerry(way,
                    highwaySpeed.get("living_street"),
                    highwaySpeed.get("track"),
                    highwaySpeed.get("primary"));
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
        int speed = PUSHING_SECTION_SPEED;

        String s = way.getTag("surface");
        if (!Helper.isEmpty(s))
        {
            Integer sInt = surfaceSpeed.get(s);
            if (sInt != null)
                speed = sInt;
        } else
        {
            String tt = way.getTag("tracktype");
            if (!Helper.isEmpty(tt))
            {
                Integer tInt = trackTypeSpeed.get(tt);
                if (tInt != null)
                    speed = tInt;
            } else
            {
                String highway = way.getTag("highway");
                if (!Helper.isEmpty(highway))
                {
                    Integer hwInt = highwaySpeed.get(highway);
                    if (hwInt != null)
                    {
                        if (way.getTag("service") == null)
                            speed = hwInt;
                        else
                            speed = highwaySpeed.get("living_street");
                    }
                }
            }
        }

        // Until now we assumed that the way is no pusing section
        // Now we check, but only in case that our speed is bigger compared to the PUSHING_SECTION_SPEED
        if ((speed > PUSHING_SECTION_SPEED)
                && (!way.hasTag("bicycle", intended) && way.hasTag("highway", pushingSections)))
        {
            if (way.hasTag("highway", "steps"))
                speed = PUSHING_SECTION_SPEED / 2;
            else
                speed = PUSHING_SECTION_SPEED;
        }

        return speed;
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

    public void setTrackTypeSpeed( String tracktype, int speed )
    {
        trackTypeSpeed.put(tracktype, speed);
    }

    public void setSurfaceSpeed( String surface, int speed )
    {
        surfaceSpeed.put(surface, speed);
    }

    public void setHighwaySpeed( String highway, int speed )
    {
        highwaySpeed.put(highway, speed);
    }

    public void setCyclingNetworkPreference( String network, int code )
    {
        bikeNetworkToCode.put(network, code);
    }

    public void setPushingSection( String highway )
    {
        pushingSections.add(highway);
    }
}
