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

import static com.graphhopper.routing.util.PriorityCode.*;

import java.util.HashSet;

import com.graphhopper.reader.Way;
import com.graphhopper.util.BitUtil;

/**
 * Defines bit layout for motorbikes
 * <p>
 * @author Peter Karich
 */
public class MotorcycleFlagEncoder extends CarFlagEncoder
{
    private EncodedDoubleValue reverseSpeedEncoder;
    private EncodedValue preferWayEncoder;
    private final HashSet<String> avoidSet = new HashSet<String>();
    private final HashSet<String> preferSet = new HashSet<String>();

    public MotorcycleFlagEncoder( String propertiesStr )
    {
        this((int) parseLong(propertiesStr, "speedBits", 5),
                parseDouble(propertiesStr, "speedFactor", 5),
                parseBoolean(propertiesStr, "turnCosts", false) ? 3 : 0);
        this.setBlockFords(parseBoolean(propertiesStr, "blockFords", true));
    }

    public MotorcycleFlagEncoder( int speedBits, double speedFactor, int maxTurnCosts )
    {
        super(speedBits, speedFactor, maxTurnCosts);
        restrictions.remove("motorcar");
        //  moped, mofa
        restrictions.add("motorcycle");

        trackTypeSpeedMap.clear();
        defaultSpeedMap.clear();

        trackTypeSpeedMap.put("grade1", 20); // paved
        trackTypeSpeedMap.put("grade2", 15); // now unpaved - gravel mixed with ...
        trackTypeSpeedMap.put("grade3", 10); // ... hard and soft materials
        trackTypeSpeedMap.put("grade4", 5); // ... some hard or compressed materials
        trackTypeSpeedMap.put("grade5", 5); // ... no hard materials. soil/sand/grass

        avoidSet.add("motorway");
        avoidSet.add("trunk");
        avoidSet.add("motorroad");
        preferSet.add("primary");
        preferSet.add("secondary");
        
        maxPossibleSpeed = 100;

        // autobahn
        defaultSpeedMap.put("motorway", 100);
        defaultSpeedMap.put("motorway_link", 70);
        defaultSpeedMap.put("motorroad", 90);
        // bundesstraße
        defaultSpeedMap.put("trunk", 80);
        defaultSpeedMap.put("trunk_link", 75);
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
    }

    /**
     * Define the place of the speedBits in the edge flags for car.
     */
    @Override
    public int defineWayBits( int index, int shift )
    {
        // first two bits are reserved for route handling in superclass
        shift = super.defineWayBits(index, shift);
        reverseSpeedEncoder = new EncodedDoubleValue("Reverse Speed", shift, speedBits, speedFactor, 
                                                     defaultSpeedMap.get("secondary"), maxPossibleSpeed);
        shift += reverseSpeedEncoder.getBits();

        preferWayEncoder = new EncodedValue("PreferWay", shift, 3, 1, 3, 7);
        shift += reverseSpeedEncoder.getBits();

        return shift;
    }

    @Override
    public long acceptWay( Way way )
    {
        String highwayValue = way.getTag("highway");
        if (highwayValue == null)
        {
            if (way.hasTag("route", ferries))
            {
                String motorcycleTag = way.getTag("motorcycle");
                if (motorcycleTag == null)
                    motorcycleTag = way.getTag("motor_vehicle");

                if (motorcycleTag == null && !way.hasTag("foot") && !way.hasTag("bicycle") || "yes".equals(motorcycleTag))
                    return acceptBit | ferryBit;
            }
            return 0;
        }

        if ("track".equals(highwayValue))
        {
            String tt = way.getTag("tracktype");
            if (tt != null && !tt.equals("grade1"))
                return 0;
        }

        if (!defaultSpeedMap.containsKey(highwayValue))
            return 0;

        if (way.hasTag("impassable", "yes") || way.hasTag("status", "impassable"))
            return 0;

        // do not drive street cars into fords
        boolean carsAllowed = way.hasTag(restrictions, intendedValues);
        if (isBlockFords() && ("ford".equals(highwayValue) || way.hasTag("ford")) && !carsAllowed)
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

        long encoded = 0;
        if (!isFerry(allowed))
        {
            encoded = setLong(encoded, PriorityWeighting.KEY, calcPriority(way, relationFlags));

            // get assumed speed from highway type
            double speed = getSpeed(way);
            speed = applyMaxSpeed(way, speed, true);

            double maxMCSpeed = parseSpeed(way.getTag("maxspeed:motorcycle"));
            if (maxMCSpeed > 0 && maxMCSpeed < speed)
                speed = maxMCSpeed * 0.9;

            // limit speed to max 30 km/h if bad surface
            if (speed > 30 && way.hasTag("surface", badSurfaceSpeedMap))
                speed = 30;

            boolean isRoundabout = way.hasTag("junction", "roundabout");
            if (isRoundabout)
                encoded = setBool(0, K_ROUNDABOUT, true);

            if (way.hasTag("oneway", oneways) || isRoundabout)
            {
                if (way.hasTag("oneway", "-1"))
                {
                    encoded = setReverseSpeed(encoded, speed);
                    encoded |= backwardBit;
                } else
                {
                    encoded = setSpeed(encoded, speed);
                    encoded |= forwardBit;
                }
            } else
            {
                encoded = setSpeed(encoded, speed);
                encoded = setReverseSpeed(encoded, speed);
                encoded |= directionBitMask;
            }

        } else
        {
            encoded = handleFerryTags(way, defaultSpeedMap.get("living_street"), defaultSpeedMap.get("service"), defaultSpeedMap.get("residential"));
            encoded |= directionBitMask;
        }

        return encoded;
    }

    @Override
    public double getReverseSpeed( long flags )
    {
        return reverseSpeedEncoder.getDoubleValue(flags);
    }

    @Override
    public long setReverseSpeed( long flags, double speed )
    {
        if (speed < 0)
            throw new IllegalArgumentException("Speed cannot be negative: " + speed + ", flags:" + BitUtil.LITTLE.toBitString(flags));

        if (speed > getMaxSpeed())
            speed = getMaxSpeed();

        return reverseSpeedEncoder.setDoubleValue(flags, speed);
    }

    @Override
    public long flagsDefault( boolean forward, boolean backward )
    {
        long flags = super.flagsDefault(forward, backward);
        if (backward)
            return reverseSpeedEncoder.setDefaultValue(flags);

        return flags;
    }

    @Override
    public long setProperties( double speed, boolean forward, boolean backward )
    {
        long flags = super.setProperties(speed, forward, backward);
        if (backward)
            return setReverseSpeed(flags, speed);

        return flags;
    }

    @Override
    public long reverseFlags( long flags )
    {
        // swap access
        flags = super.reverseFlags(flags);

        // swap speeds 
        double otherValue = reverseSpeedEncoder.getDoubleValue(flags);
        flags = setReverseSpeed(flags, speedEncoder.getDoubleValue(flags));
        return setSpeed(flags, otherValue);
    }

    @Override
    public double getDouble( long flags, int key )
    {
        switch (key)
        {
            case PriorityWeighting.KEY:
                double prio = preferWayEncoder.getValue(flags);
                if (prio == 0)
                    return (double) UNCHANGED.getValue() / BEST.getValue();

                return prio / BEST.getValue();
            default:
                return super.getDouble(flags, key);
        }
    }

    @Override
    public long getLong( long flags, int key )
    {
        switch (key)
        {
            case PriorityWeighting.KEY:
                return preferWayEncoder.getValue(flags);
            default:
                return super.getLong(flags, key);
        }
    }

    @Override
    public long setLong( long flags, int key, long value )
    {
        switch (key)
        {
            case PriorityWeighting.KEY:
                return preferWayEncoder.setValue(flags, value);
            default:
                return super.setLong(flags, key, value);
        }
    }

    private int calcPriority( Way way, long relationFlags )
    {
        String highway = way.getTag("highway", "");
        if (avoidSet.contains(highway))
        {
            return PriorityCode.AVOID_AT_ALL_COSTS.getValue();
        } else if (preferSet.contains(highway))
        {
            return PriorityCode.VERY_NICE.getValue();
        }
        return PriorityCode.UNCHANGED.getValue();
    }

    @Override
    public boolean supports( Class<?> feature )
    {
        if (super.supports(feature))
            return true;

        return PriorityWeighting.class.isAssignableFrom(feature);
    }

    @Override
    public String toString()
    {
        return "motorcycle";
    }
}
