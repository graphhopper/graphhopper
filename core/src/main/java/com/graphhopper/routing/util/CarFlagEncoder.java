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

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import com.graphhopper.reader.Relation;
import com.graphhopper.reader.Way;
import com.graphhopper.util.Helper;

/**
 * Defines bit layout for cars. (speed, access, ferries, ...)
 * <p>
 * @author Peter Karich
 * @author Nop
 */
public class CarFlagEncoder extends AbstractFlagEncoder
{
    protected final Map<String, Integer> trackTypeSpeedMap = new HashMap<>();
    protected final Set<String> badSurfaceSpeedMap = new HashSet<>();
    /**
     * A map which associates string to speed. Get some impression:
     * http://www.itoworld.com/map/124#fullscreen
     * http://wiki.openstreetmap.org/wiki/OSM_tags_for_routing/Maxspeed
     */
    protected final Map<String, Integer> defaultSpeedMap = new HashMap<>();

    protected final Map<String, String> environmentMaxSpeedMap = new HashMap<>();

    /**
     * 1mph = 1.60934kph
     */
    public static final int SEVENTY_MPH_IN_KPH = 112;
    public static final int SIXTY_MPH_IN_KPH = 96;
    public static final int THIRTY_MPH_IN_KPH = 48;

    /**
     * Should be only instantied via EncodingManager
     */
    public CarFlagEncoder()
    {
        this(5, 5, 0);
    }

    public CarFlagEncoder( String propertiesStr )
    {
        this((int) parseLong(propertiesStr, "speedBits", 5),
                parseDouble(propertiesStr, "speedFactor", 5),
                parseBoolean(propertiesStr, "turnCosts", false) ? 3 : 0);
        this.setBlockFords(parseBoolean(propertiesStr, "blockFords", true));
    }

    public CarFlagEncoder( int speedBits, double speedFactor, int maxTurnCosts )
    {
        super(speedBits, speedFactor, maxTurnCosts);
        restrictions.addAll(Arrays.asList("motorcar", "motor_vehicle", "vehicle", "access"));

        restrictedValues.add("private");
        restrictedValues.add("agricultural");
        restrictedValues.add("forestry");
        restrictedValues.add("no");
        restrictedValues.add("restricted");
        restrictedValues.add("delivery");
        restrictedValues.add("military");

        intendedValues.add("yes");
        intendedValues.add("permissive");

        potentialBarriers.add("gate");
        potentialBarriers.add("lift_gate");
        potentialBarriers.add("kissing_gate");
        potentialBarriers.add("swing_gate");

        absoluteBarriers.add("bollard");
        absoluteBarriers.add("stile");
        absoluteBarriers.add("turnstile");
        absoluteBarriers.add("cycle_barrier");
        absoluteBarriers.add("motorcycle_barrier");
        absoluteBarriers.add("block");

        trackTypeSpeedMap.put("grade1", 20); // paved
        trackTypeSpeedMap.put("grade2", 15); // now unpaved - gravel mixed with ...
        trackTypeSpeedMap.put("grade3", 10); // ... hard and soft materials
        trackTypeSpeedMap.put("grade4", 5); // ... some hard or compressed materials
        trackTypeSpeedMap.put("grade5", 5); // ... no hard materials. soil/sand/grass

        badSurfaceSpeedMap.add("cobblestone");
        badSurfaceSpeedMap.add("grass_paver");
        badSurfaceSpeedMap.add("gravel");
        badSurfaceSpeedMap.add("sand");
        badSurfaceSpeedMap.add("paving_stones");
        badSurfaceSpeedMap.add("dirt");
        badSurfaceSpeedMap.add("ground");
        badSurfaceSpeedMap.add("grass");

        maxPossibleSpeed = 100;
        
        // autobahn
        defaultSpeedMap.put("motorway", 100);
        defaultSpeedMap.put("motorway_link", 70);
        defaultSpeedMap.put("motorroad", 90);
        // bundesstraße
        defaultSpeedMap.put("trunk", 70);
        defaultSpeedMap.put("trunk_link", 65);
        // linking bigger town
        defaultSpeedMap.put("primary", 65);
        defaultSpeedMap.put("primary_link", 60);
        // linking towns + villages
        defaultSpeedMap.put("secondary", 60);
        defaultSpeedMap.put("secondary_link", 50);
        // streets without middle line separation
        defaultSpeedMap.put("tertiary", 50);
        defaultSpeedMap.put("tertiary_link", 40);
        defaultSpeedMap.put("unclassified", 30);
        defaultSpeedMap.put("residential", 30);
        // spielstraße
        defaultSpeedMap.put("living_street", 5);
        defaultSpeedMap.put("service", 20);
        // unknown road
        defaultSpeedMap.put("road", 20);
        // forestry stuff
        defaultSpeedMap.put("track", 15);

        defaultSpeedMap.put("Motorway", SEVENTY_MPH_IN_KPH);
        defaultSpeedMap.put("A Road", SIXTY_MPH_IN_KPH);
        defaultSpeedMap.put("B Road", THIRTY_MPH_IN_KPH);
        defaultSpeedMap.put("Minor Road", THIRTY_MPH_IN_KPH);
        defaultSpeedMap.put("Local Street", THIRTY_MPH_IN_KPH);
        //        defaultSpeedMap.put("Alley", 35);
        defaultSpeedMap.put("A Road-Single Carriageway", SIXTY_MPH_IN_KPH);
        defaultSpeedMap.put("B Road", THIRTY_MPH_IN_KPH);
        defaultSpeedMap.put("Minor Road", THIRTY_MPH_IN_KPH);
        defaultSpeedMap.put("Local Street", THIRTY_MPH_IN_KPH);
        //        defaultSpeedMap.put("Alley", 35);
        defaultSpeedMap.put("Motorway", SEVENTY_MPH_IN_KPH);
        defaultSpeedMap.put("A Road", SIXTY_MPH_IN_KPH);
        defaultSpeedMap.put("B Road", THIRTY_MPH_IN_KPH);
        defaultSpeedMap.put("Minor Road", THIRTY_MPH_IN_KPH);
        defaultSpeedMap.put("Local Street", THIRTY_MPH_IN_KPH);
        //        defaultSpeedMap.put("Alley", 35);
        defaultSpeedMap.put("A Road-Dual Carriageway", SIXTY_MPH_IN_KPH);
        defaultSpeedMap.put("B Road", THIRTY_MPH_IN_KPH);
        defaultSpeedMap.put("Minor Road", THIRTY_MPH_IN_KPH);
        defaultSpeedMap.put("Local Street", THIRTY_MPH_IN_KPH);
        //        defaultSpeedMap.put("Alley", 35);
        defaultSpeedMap.put("Motorway-Slip Road", SEVENTY_MPH_IN_KPH);
        defaultSpeedMap.put("A Road-Slip Road", SIXTY_MPH_IN_KPH);
        defaultSpeedMap.put("B Road", THIRTY_MPH_IN_KPH);
        defaultSpeedMap.put("Minor Road", THIRTY_MPH_IN_KPH);
        defaultSpeedMap.put("Local Street", THIRTY_MPH_IN_KPH);
        //        defaultSpeedMap.put("Alley", 35);
        defaultSpeedMap.put("Motorway-Roundabout", SEVENTY_MPH_IN_KPH);
        defaultSpeedMap.put("A Road-Roundabout", SIXTY_MPH_IN_KPH);
        defaultSpeedMap.put("B Road-Roundabout", THIRTY_MPH_IN_KPH);
        defaultSpeedMap.put("Minor Road-Roundabout", THIRTY_MPH_IN_KPH);
        defaultSpeedMap.put("Local Street-Roundabout", THIRTY_MPH_IN_KPH);

        // You can not drive down a pedestrianised street
        defaultSpeedMap.put("Pedestrianised Street", 0);
        // Private Road - Publicly Accessible are NOT traversible
        // defaultSpeedMap.put("Private Road - Restricted Access", 0);
        // Private Road - Publicly Accessible are NOT traversible
        // defaultSpeedMap.put("Private Road - Publicly Accessible", 10);

        //        defaultSpeedMap.put("Alley", 0);

        // osgb:type
        vehicleQualifierTypeExclusions.add("Buses");
        vehicleQualifierTypeExclusions.add("Coaches");
        vehicleQualifierTypeExclusions.add("Mopeds");
        vehicleQualifierTypeExclusions.add("Motor Cycles");
        vehicleQualifierTypeExclusions.add("HGV's");
        vehicleQualifierTypeExclusions.add("LGV's");
        vehicleQualifierTypeExclusions.add("Towed Caravans");
        vehicleQualifierTypeExclusions.add("Cycles");
        vehicleQualifierTypeExclusions.add("Tracked Vehicles");
        // osgb:use
        vehicleQualifierTypeExclusions.add("Taxi");
        vehicleQualifierTypeExclusions.add("Taxis"); // Added from analysing the actual data
        vehicleQualifierTypeExclusions.add("School Bus");
        vehicleQualifierTypeExclusions.add("Patron");
        vehicleQualifierTypeExclusions.add("Access");
        vehicleQualifierTypeExclusions.add("Resident");
        vehicleQualifierTypeExclusions.add("Emergency Vehicle");
        vehicleQualifierTypeExclusions.add("Public Transport");
        vehicleQualifierTypeExclusions.add("Authorised Vehicle");
        vehicleQualifierTypeExclusions.add("Local Bus");
        vehicleQualifierTypeExclusions.add("Local Buses"); // Added from analysing the actual data
        vehicleQualifierTypeExclusions.add("Escorted Traffic");
        vehicleQualifierTypeExclusions.add("Loading And Unloading"); // Added from analysing the actual data



        vehicleQualifierTypeInclusions.add("Motor Vehicles");
        vehicleQualifierTypeInclusions.add("All Vehicles");


        //        environmentMaxSpeedMap.put("Rural", 60);
        environmentMaxSpeedMap.put("Urban:Single Carriageway", ""+THIRTY_MPH_IN_KPH);


    }

    /**
     * Define the place of the speedBits in the edge flags for car.
     */
    @Override
    public int defineWayBits( int index, int shift )
    {
        // first two bits are reserved for route handling in superclass
        shift = super.defineWayBits(index, shift);
        speedEncoder = new EncodedDoubleValue("Speed", shift, speedBits, speedFactor, defaultSpeedMap.get("secondary"), 
                                              maxPossibleSpeed);
        return shift + speedEncoder.getBits();
    }

    protected double getSpeed( Way way )
    {
        String highwayValue = way.getTag("highway");
        Integer speed = defaultSpeedMap.get(highwayValue);
        if (speed == null)
            throw new IllegalStateException(toString() + ", no speed found for: " + highwayValue + ", tags: " + way);

        if (highwayValue.equals("track"))
        {
            String tt = way.getTag("tracktype");
            if (!Helper.isEmpty(tt))
            {
                Integer tInt = trackTypeSpeedMap.get(tt);
                if (tInt != null)
                    speed = tInt;
            }
        }

        return speed;
    }

    @Override
    public long acceptWay( Way way )
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

        if ("track".equals(highwayValue))
        {
            String tt = way.getTag("tracktype");
            if (tt != null && !tt.equals("grade1") && !tt.equals("grade2") && !tt.equals("grade3"))
                return 0;
        }

        // absolute barriers always block
        if (way.hasTag("barrier", absoluteBarriers))
            return 0;

        // movable barriers block always for cars
        if (way.hasTag("barrier", potentialBarriers))
            return 0;


        if (!defaultSpeedMap.containsKey(highwayValue))
            return 0;

        if (way.hasTag("impassable", "yes") || way.hasTag("status", "impassable"))
            return 0;

        // do not drive street cars into fords
        boolean carsAllowed = way.hasTag(restrictions, intendedValues);
        boolean hasFordFlag = way.hasTag("ford");
        if (isBlockFords() && ("ford".equals(highwayValue) || hasFordFlag) && !carsAllowed)
            return 0;

        // check access restrictions
        if (way.hasTag(restrictions, restrictedValues) && !carsAllowed)
            return 0;

        // do not drive cars over railways (sometimes incorrectly mapped!)
        if (way.hasTag("railway") && !way.hasTag("railway", acceptedRailways))
            return 0;

        return acceptBit;
    }

    @Override
    public long handleWayTags( Way way, long allowed, long relationFlags )
    {
        if (!isAccept(allowed))
            return 0;

        long encoded;
        if (!isFerry(allowed))
        {
            // get assumed speed from highway type
            double speed = getSpeed(way);

            // set the max speed tag for the environment
            String environment = way.getTag("environment");
            if (!Helper.isEmpty(environment))
            {
                String maxSpeed = environmentMaxSpeedMap.get(environment);
                if (!Helper.isEmpty(maxSpeed)) {
                    //                    System.out.println(">>>>>>>>>>>>>>> Set max speed to " + maxSpeed);
                    way.setTag("maxspeed", maxSpeed);
                }
            }

            speed = applyMaxSpeed(way, speed, true);

            // limit speed to max 30 km/h if bad surface
            if (speed > 30 && way.hasTag("surface", badSurfaceSpeedMap))
                speed = 30;

            encoded = setSpeed(0, speed);

            boolean isRoundabout = way.hasTag("junction", "roundabout");
            if (isRoundabout)
                encoded = setBool(encoded, K_ROUNDABOUT, true);

            boolean isOneway = way.hasTag("oneway", oneways)
                    || way.hasTag("vehicle:backward")
                    || way.hasTag("vehicle:forward")
                    || way.hasTag("motor_vehicle:backward")
                    || way.hasTag("motor_vehicle:forward");

            if (isOneway || isRoundabout)
            {
                boolean isBackward = way.hasTag("oneway", "-1")
                        || way.hasTag("vehicle:forward", "no")
                        || way.hasTag("motor_vehicle:forward", "no");
                if (isBackward)
                    encoded |= backwardBit;
                else
                    encoded |= forwardBit;
            } else
                encoded |= directionBitMask;

        } else
        {
            encoded = handleFerryTags(way, defaultSpeedMap.get("living_street"), defaultSpeedMap.get("service"), defaultSpeedMap.get("residential"));
            encoded |= directionBitMask;
        }
        return encoded;
    }

    public String getWayInfo(Way way )
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

    @Override
    public long handleRelationTags(Relation relation, long oldRelationFlags) {
        return oldRelationFlags;
    }
}
