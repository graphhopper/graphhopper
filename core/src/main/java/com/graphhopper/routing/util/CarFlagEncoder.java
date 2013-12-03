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
import com.graphhopper.util.Helper;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * @author Peter Karich
 * @author Nop
 */
public class CarFlagEncoder extends AbstractFlagEncoder
{
    private HashSet<String> intended = new HashSet<String>();

    /**
     * Should be only instantied via EncodingManager
     */
    protected CarFlagEncoder()
    {
        restrictions = new String[]
        {
            "motorcar", "motor_vehicle", "vehicle", "access"
        };
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
     * Define the encoding flags for car
     * <p/>
     * @param index
     * @param shift bit offset for the first bit used by this encoder
     * @return adjusted shift pointing behind the last used field
     */
    @Override
    public int defineBits( int index, int shift )
    {
        // first two bits are reserved for route handling in superclass
        shift = super.defineBits(index, shift);

        speedEncoder = new EncodedValue("Speed", shift, 5, 5, SPEED.get("secondary"), SPEED.get("motorway"));

        // speed used 5 bits
        return shift + 5;
    }

    int getSpeed( OSMWay way )
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
    public long isAllowed( OSMWay way )
    {
        String highwayValue = way.getTag("highway");
        if (highwayValue == null)
        {
            if (way.hasTag("route", ferries))
            {
                String motorcarTag = way.getTag("motorcar");
                if (motorcarTag == null)
                    motorcarTag = way.getTag("motor_vehicle");

                if (motorcarTag == null && !way.hasTag("foot") && !way.hasTag("bicycle")
                        || "yes".equals(motorcarTag))
                    return acceptBit | ferryBit;
            }
            return 0;
        }

        if (!SPEED.containsKey(highwayValue))
            return 0;

        if (way.hasTag("impassable", "yes") || way.hasTag("status", "impassable"))
            return 0;

        // do not drive street cars into fords
        if ((way.hasTag("highway", "ford") || way.hasTag("ford"))
                && !way.hasTag(restrictions, intended))
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
    public long handleWayTags( long allowed, OSMWay way )
    {
        if ((allowed & acceptBit) == 0)
            return 0;

        long encoded;
        if ((allowed & ferryBit) == 0)
        {
            // get assumed speed from highway type
            Integer speed = getSpeed(way);
            int maxspeed = parseSpeed(way.getTag("maxspeed"));
            // apply speed limit no matter of the road type
            if (maxspeed >= 0)
                // reduce speed limit to reflect average speed
                speed = Math.round(maxspeed * 0.9f);

            // limit speed to max 30 km/h if bad surface
            if (speed > 30 && way.hasTag("surface", BAD_SURFACE))
                speed = 30;

            if (speed > getMaxSpeed())
                speed = getMaxSpeed();

            encoded = speedEncoder.setValue(0, speed);

            // usually used with a node, this does not work as intended
            // if( "toll_booth".equals( osmProperties.get( "barrier" ) ) )
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
    public String toString()
    {
        return "car";
    }

    private static final Map<String, Integer> TRACKTYPE_SPEED = new HashMap<String, Integer>()
    {
        {
            put("grade1", 20); // paved
            put("grade2", 15); // now unpaved - gravel mixed with ...
            put("grade3", 10); // ... hard and soft materials
            put("grade4", 5); // ... some hard or compressed materials
            put("grade5", 5); // ... no hard materials. soil/sand/grass
        }
    };

    private static final Set<String> BAD_SURFACE = new HashSet<String>()
    {
        {
            add("cobblestone");
            add("grass_paver");
            add("gravel");
            add("sand");
            add("paving_stones");
            add("dirt");
            add("ground");
            add("grass");
        }
    };
    /**
     * A map which associates string to speed. Get some impression:
     * http://www.itoworld.com/map/124#fullscreen
     * http://wiki.openstreetmap.org/wiki/OSM_tags_for_routing/Maxspeed
     */
    private static final Map<String, Integer> SPEED = new HashMap<String, Integer>()
    {
        {
            // autobahn
            put("motorway", 100);
            put("motorway_link", 70);
            // bundesstraße
            put("trunk", 70);
            put("trunk_link", 65);
            // linking bigger town
            put("primary", 65);
            put("primary_link", 60);
            // linking towns + villages
            put("secondary", 60);
            put("secondary_link", 50);
            // streets without middle line separation
            put("tertiary", 50);
            put("tertiary_link", 40);
            put("unclassified", 30);
            put("residential", 30);
            // spielstraße
            put("living_street", 5);
            put("service", 20);
            // unknown road
            put("road", 20);
            // forestry stuff
            put("track", 15);
        }
    };
}
