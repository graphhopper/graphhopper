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

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import com.graphhopper.reader.OSMNode;
import com.graphhopper.reader.OSMReader;
import com.graphhopper.reader.OSMRelation;
import com.graphhopper.reader.OSMTurnRelation;
import com.graphhopper.reader.OSMTurnRelation.TurnCostTableEntry;
import com.graphhopper.reader.OSMWay;
import com.graphhopper.util.Helper;

/**
 * Defines bit layout for cars. (speed, access, ferries, ...)
 * <p>
 * @author Peter Karich
 * @author Nop
 */
public class CarFlagEncoder extends AbstractFlagEncoder
{
    /**
     * Should be only instantied via EncodingManager
     */
    protected CarFlagEncoder()
    {
        this(5, 5);
    }

    protected CarFlagEncoder( int speedBits, double speedFactor )
    {
        super(speedBits, speedFactor);
        restrictions = new String[] { "motorcar", "motor_vehicle", "vehicle", "access" };
        restrictedValues.add("private");
        restrictedValues.add("agricultural");
        restrictedValues.add("forestry");
        restrictedValues.add("no");
        restrictedValues.add("restricted");

        intended.add("yes");
        intended.add("permissive");

        potentialBarriers.add("gate");
        potentialBarriers.add("lift_gate");
        potentialBarriers.add("kissing_gate");
        potentialBarriers.add("swing_gate");

        absoluteBarriers.add("bollard");
        absoluteBarriers.add("stile");
        absoluteBarriers.add("turnstile");
        absoluteBarriers.add("cycle_barrier");
        absoluteBarriers.add("block");
    }

    /**
     * Define the place of speedBits in the flags variable for car.
     */
    @Override
    public int defineWayBits( int index, int shift )
    {
        // first two bits are reserved for route handling in superclass
        shift = super.defineWayBits(index, shift);
        speedEncoder = new EncodedDoubleValue("Speed", shift, speedBits, speedFactor, SPEED.get("secondary"), SPEED.get("motorway"));
        return shift + speedBits;
    }

    protected double getSpeed( OSMWay way )
    {
        String highwayValue = way.getTag("highway");
        Integer speed = SPEED.get(highwayValue);
        if (speed == null)
            throw new IllegalStateException("car, no speed found for:" + highwayValue);

        if (highwayValue.equals("track"))
        {
            String tt = way.getTag("tracktype");
            if (!Helper.isEmpty(tt))
            {
                Integer tInt = TRACKTYPE_SPEED.get(tt);
                if (tInt != null)
                    speed = tInt;
            }
        }

        return speed;
    }

    @Override
    public long acceptWay( OSMWay way )
    {
        String highwayValue = way.getTag("highway");
        if (highwayValue == null)
        {
            if (way.hasTag("route", ferries))
            {
                String motorcarTag = way.getTag("motorcar");
                if (motorcarTag == null)
                    motorcarTag = way.getTag("motor_vehicle");

                if (motorcarTag == null && !way.hasTag("foot") && !way.hasTag("bicycle") || "yes".equals(motorcarTag))
                    return acceptBit | ferryBit;
            }
            return 0;
        }

        if (!SPEED.containsKey(highwayValue))
            return 0;

        if (way.hasTag("impassable", "yes") || way.hasTag("status", "impassable"))
            return 0;

        // do not drive street cars into fords
        if ((way.hasTag("highway", "ford") || way.hasTag("ford")) && !way.hasTag(restrictions, intended))
            return 0;

        // check access restrictions
        if (way.hasTag(restrictions, restrictedValues))
            return 0;

        // do not drive cars over railways (sometimes incorrectly mapped!)
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
        if ((allowed & acceptBit) == 0)
            return 0;

        long encoded;
        if ((allowed & ferryBit) == 0)
        {
            // get assumed speed from highway type
            double speed = getSpeed(way);
            double maxspeed = parseSpeed(way.getTag("maxspeed"));
            // apply speed limit no matter of the road type
            if (maxspeed >= 0)
                // reduce speed limit to reflect average speed
                speed = maxspeed * 0.9;

            // limit speed to max 30 km/h if bad surface
            if (speed > 30 && way.hasTag("surface", BAD_SURFACE))
                speed = 30;

            encoded = setSpeed(0, speed);

            if (way.hasTag("oneway", oneways) || way.hasTag("junction", "roundabout"))
            {
                if (way.hasTag("oneway", "-1"))
                    encoded |= backwardBit;
                else
                    encoded |= forwardBit;
            } else
                encoded |= directionBitMask;

        } else
        {
            encoded = handleFerry(way, SPEED.get("living_street"), SPEED.get("service"), SPEED.get("residential"));
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

    @Override
    public String getWayInfo( OSMWay way )
    {
        String str = "";
        String highwayValue = way.getTag("highway");
        // for now only motorway links
        if ("motorway_link".equals(highwayValue))
        {
            String destination = way.getTag("destination");
            if (!Helper.isEmpty(destination))
            {
                int counter = 0;
                for (String d : destination.split(";"))
                {
                    if (d.trim().isEmpty())
                        continue;

                    if (counter > 0)
                        str += ", ";

                    str += d.trim();
                    counter++;
                }
            }
        }
        if (str.isEmpty())
            return str;
        // I18N
        if (str.contains(","))
            return "destinations: " + str;
        else
            return "destination: " + str;
    }

    @Override
    public Collection<TurnCostTableEntry> analyzeTurnRelation( OSMTurnRelation turnRelation, OSMReader osmReader )
    {
        if(edgeOutExplorer == null || edgeInExplorer == null) {
            edgeOutExplorer = osmReader.getGraphStorage().createEdgeExplorer(new DefaultEdgeFilter(this, false, true));
            edgeInExplorer = osmReader.getGraphStorage().createEdgeExplorer(new DefaultEdgeFilter(this, true, false));
        }
        return turnRelation.getRestrictionAsEntries(this, edgeOutExplorer, edgeInExplorer, osmReader);
    }

    @Override
    public int getPavementCode( long flags )
    {
        return 0;
    }

    @Override
    public int getWayTypeCode( long flags )
    {
        return 0;
    }

    @Override
    public String toString()
    {
        return "car";
    }

    private static final Map<String, Integer> TRACKTYPE_SPEED = new HashMap<String, Integer>();
    protected static final Set<String> BAD_SURFACE = new HashSet<String>();
    /**
     * A map which associates string to speed. Get some impression:
     * http://www.itoworld.com/map/124#fullscreen
     * http://wiki.openstreetmap.org/wiki/OSM_tags_for_routing/Maxspeed
     */
    private static final Map<String, Integer> SPEED = new HashMap<String, Integer>();

    static
    {

        TRACKTYPE_SPEED.put("grade1", 20); // paved
        TRACKTYPE_SPEED.put("grade2", 15); // now unpaved - gravel mixed with ...
        TRACKTYPE_SPEED.put("grade3", 10); // ... hard and soft materials
        TRACKTYPE_SPEED.put("grade4", 5); // ... some hard or compressed materials
        TRACKTYPE_SPEED.put("grade5", 5); // ... no hard materials. soil/sand/grass

        BAD_SURFACE.add("cobblestone");
        BAD_SURFACE.add("grass_paver");
        BAD_SURFACE.add("gravel");
        BAD_SURFACE.add("sand");
        BAD_SURFACE.add("paving_stones");
        BAD_SURFACE.add("dirt");
        BAD_SURFACE.add("ground");
        BAD_SURFACE.add("grass");

        // autobahn
        SPEED.put("motorway", 100);
        SPEED.put("motorway_link", 70);
        // bundesstraße
        SPEED.put("trunk", 70);
        SPEED.put("trunk_link", 65);
        // linking bigger town
        SPEED.put("primary", 65);
        SPEED.put("primary_link", 60);
        // linking towns + villages
        SPEED.put("secondary", 60);
        SPEED.put("secondary_link", 50);
        // streets without middle line separation
        SPEED.put("tertiary", 50);
        SPEED.put("tertiary_link", 40);
        SPEED.put("unclassified", 30);
        SPEED.put("residential", 30);
        // spielstraße
        SPEED.put("living_street", 5);
        SPEED.put("service", 20);
        // unknown road
        SPEED.put("road", 20);
        // forestry stuff
        SPEED.put("track", 15);
    }
}
