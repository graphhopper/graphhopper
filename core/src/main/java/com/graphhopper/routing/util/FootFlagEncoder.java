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

import com.graphhopper.reader.OSMRelation;
import com.graphhopper.reader.OSMWay;
import com.graphhopper.reader.osm.conditional.ConditionalTagsInspector;
import com.graphhopper.util.PMap;

import java.util.*;

import static com.graphhopper.routing.util.PriorityCode.*;

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
    private EncodedValue priorityWayEncoder;
    private EncodedValue relationCodeEncoder;
    protected HashSet<String> sidewalks = new HashSet<String>();
    private final Set<String> safeHighwayTags = new HashSet<String>();
    private final Set<String> allowedHighwayTags = new HashSet<String>();
    private final Set<String> avoidHighwayTags = new HashSet<String>();
    // convert network tag of hiking routes into a way route code
    private final Map<String, Integer> hikingNetworkToCode = new HashMap<String, Integer>();

    /**
     * Should be only instantiated via EncodingManager
     */
    public FootFlagEncoder()
    {
        this(4, 1);
    }

    public FootFlagEncoder( PMap properties )
    {
        this(
                (int) properties.getLong("speedBits", 4),
                properties.getDouble("speedFactor", 1)
        );
        this.properties = properties;
        this.setBlockFords(properties.getBool("blockFords", true));
    }

    public FootFlagEncoder( String propertiesStr )
    {
        this(new PMap(propertiesStr));
    }

    public FootFlagEncoder( int speedBits, double speedFactor )
    {
        super(speedBits, speedFactor, 0);
        restrictions.addAll(Arrays.asList("foot", "access"));
        restrictedValues.add("private");
        restrictedValues.add("no");
        restrictedValues.add("restricted");
        restrictedValues.add("military");
        restrictedValues.add("emergency");

        intendedValues.add("yes");
        intendedValues.add("designated");
        intendedValues.add("official");
        intendedValues.add("permissive");

        sidewalks.add("yes");
        sidewalks.add("both");
        sidewalks.add("left");
        sidewalks.add("right");

        setBlockByDefault(false);
        potentialBarriers.add("gate");

        acceptedRailways.add("platform");

        safeHighwayTags.add("footway");
        safeHighwayTags.add("path");
        safeHighwayTags.add("steps");
        safeHighwayTags.add("pedestrian");
        safeHighwayTags.add("living_street");
        safeHighwayTags.add("track");
        safeHighwayTags.add("residential");
        safeHighwayTags.add("service");

        avoidHighwayTags.add("trunk");
        avoidHighwayTags.add("trunk_link");
        avoidHighwayTags.add("primary");
        avoidHighwayTags.add("primary_link");
        avoidHighwayTags.add("secondary");
        avoidHighwayTags.add("secondary_link");
        avoidHighwayTags.add("tertiary");
        avoidHighwayTags.add("tertiary_link");

        // for now no explicit avoiding #257
        //avoidHighwayTags.add("cycleway"); 
        allowedHighwayTags.addAll(safeHighwayTags);
        allowedHighwayTags.addAll(avoidHighwayTags);
        allowedHighwayTags.add("cycleway");
        allowedHighwayTags.add("unclassified");
        allowedHighwayTags.add("road");
        // disallowed in some countries
        //allowedHighwayTags.add("bridleway");

        hikingNetworkToCode.put("iwn", BEST.getValue());
        hikingNetworkToCode.put("nwn", BEST.getValue());
        hikingNetworkToCode.put("rwn", VERY_NICE.getValue());
        hikingNetworkToCode.put("lwn", VERY_NICE.getValue());

        maxPossibleSpeed = FERRY_SPEED;

        conditionalTagsInspector = new ConditionalTagsInspector(restrictions, restrictedValues, intendedValues);
    }

    @Override
    public int getVersion()
    {
        return 1;
    }

    @Override
    public int defineWayBits( int index, int shift )
    {
        // first two bits are reserved for route handling in superclass
        shift = super.defineWayBits(index, shift);
        // larger value required - ferries are faster than pedestrians
        speedEncoder = new EncodedDoubleValue("Speed", shift, speedBits, speedFactor, MEAN_SPEED, maxPossibleSpeed);
        shift += speedEncoder.getBits();

        priorityWayEncoder = new EncodedValue("PreferWay", shift, 3, 1, 0, 7);
        shift += priorityWayEncoder.getBits();
        return shift;
    }

    @Override
    public int defineRelationBits( int index, int shift )
    {
        relationCodeEncoder = new EncodedValue("RelationCode", shift, 3, 1, 0, 7);
        return shift + relationCodeEncoder.getBits();
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

    /**
     * Some ways are okay but not separate for pedestrians.
     * <p>
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

            // special case not for all acceptedRailways, only platform
            if (way.hasTag("railway", "platform"))
                return acceptBit;

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
        if (isBlockFords() && (way.hasTag("highway", "ford") || way.hasTag("ford")))
            return 0;

        // check access restrictions
        if (way.hasTag(restrictions, restrictedValues) && !conditionalTagsInspector.isRestrictedWayConditionallyPermitted(way))
            return 0;

        // do not accept railways (sometimes incorrectly mapped!)
        if (way.hasTag("railway") && !way.hasTag("railway", acceptedRailways))
            return 0;

        if (conditionalTagsInspector.isPermittedWayConditionallyRestricted(way))
            return 0;
        else
            return acceptBit;
    }

    @Override
    public long handleRelationTags( OSMRelation relation, long oldRelationFlags )
    {
        int code = 0;
        if (relation.hasTag("route", "hiking") || relation.hasTag("route", "foot"))
        {
            Integer val = hikingNetworkToCode.get(relation.getTag("network"));
            if (val != null)
                code = val;
        } else if (relation.hasTag("route", "ferry"))
        {
            code = PriorityCode.AVOID_IF_POSSIBLE.getValue();
        }

        int oldCode = (int) relationCodeEncoder.getValue(oldRelationFlags);
        if (oldCode < code)
            return relationCodeEncoder.setValue(0, code);
        return oldRelationFlags;
    }

    @Override
    public long handleWayTags( OSMWay way, long allowed, long relationFlags )
    {
        if (!isAccept(allowed))
            return 0;

        long encoded = 0;
        if (!isFerry(allowed))
        {
            String sacScale = way.getTag("sac_scale");
            if (sacScale != null)
            {
                if ("hiking".equals(sacScale))
                    encoded = speedEncoder.setDoubleValue(encoded, MEAN_SPEED);
                else
                    encoded = speedEncoder.setDoubleValue(encoded, SLOW_SPEED);
            } else
            {
                encoded = speedEncoder.setDoubleValue(encoded, MEAN_SPEED);
            }
            encoded |= directionBitMask;

            boolean isRoundabout = way.hasTag("junction", "roundabout");
            if (isRoundabout)
                encoded = setBool(encoded, K_ROUNDABOUT, true);

        } else
        {
            encoded = encoded | handleFerryTags(way, SLOW_SPEED, MEAN_SPEED, FERRY_SPEED);
            encoded |= directionBitMask;
        }

        int priorityFromRelation = 0;
        if (relationFlags != 0)
            priorityFromRelation = (int) relationCodeEncoder.getValue(relationFlags);

        encoded = priorityWayEncoder.setValue(encoded, handlePriority(way, priorityFromRelation));
        return encoded;
    }

    @Override
    public double getDouble( long flags, int key )
    {
        switch (key)
        {
            case PriorityWeighting.KEY:
                return (double) priorityWayEncoder.getValue(flags) / BEST.getValue();
            default:
                return super.getDouble(flags, key);
        }
    }

    protected int handlePriority( OSMWay way, int priorityFromRelation )
    {
        TreeMap<Double, Integer> weightToPrioMap = new TreeMap<Double, Integer>();
        if (priorityFromRelation == 0)
            weightToPrioMap.put(0d, UNCHANGED.getValue());
        else
            weightToPrioMap.put(110d, priorityFromRelation);

        collect(way, weightToPrioMap);

        // pick priority with biggest order value
        return weightToPrioMap.lastEntry().getValue();
    }

    /**
     * @param weightToPrioMap associate a weight with every priority. This sorted map allows
     * subclasses to 'insert' more important priorities as well as overwrite determined priorities.
     */
    void collect( OSMWay way, TreeMap<Double, Integer> weightToPrioMap )
    {
        String highway = way.getTag("highway");
        if (way.hasTag("foot", "designated"))
            weightToPrioMap.put(100d, PREFER.getValue());

        double maxSpeed = getMaxSpeed(way);
        if (safeHighwayTags.contains(highway) || maxSpeed > 0 && maxSpeed <= 20)
        {
            weightToPrioMap.put(40d, PREFER.getValue());
            if (way.hasTag("tunnel", intendedValues))
            {
                if (way.hasTag("sidewalk", "no"))
                    weightToPrioMap.put(40d, REACH_DEST.getValue());
                else
                    weightToPrioMap.put(40d, UNCHANGED.getValue());
            }
        } else if (maxSpeed > 50 || avoidHighwayTags.contains(highway))
        {
            if (way.hasTag("sidewalk", "no"))
                weightToPrioMap.put(45d, WORST.getValue());
            else
                weightToPrioMap.put(45d, REACH_DEST.getValue());
        }

        if (way.hasTag("bicycle", "official") || way.hasTag("bicycle", "designated"))
            weightToPrioMap.put(44d, AVOID_IF_POSSIBLE.getValue());
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
        return "foot";
    }
}
