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

import static com.graphhopper.routing.util.PriorityCode.AVOID_AT_ALL_COSTS;
import static com.graphhopper.routing.util.PriorityCode.AVOID_IF_POSSIBLE;
import static com.graphhopper.routing.util.PriorityCode.BEST;
import static com.graphhopper.routing.util.PriorityCode.PREFER;
import static com.graphhopper.routing.util.PriorityCode.REACH_DEST;
import static com.graphhopper.routing.util.PriorityCode.UNCHANGED;
import static com.graphhopper.routing.util.PriorityCode.VERY_NICE;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import com.graphhopper.reader.Relation;
import com.graphhopper.reader.Way;

/**
 * Defines bit layout for pedestrians (speed, access, surface, ...).
 * <p>
 * 
 * @author Peter Karich
 * @author Nop
 * @author Karl Hübner
 */
public class FootFlagEncoder extends AbstractFlagEncoder {
    static final int SLOW_SPEED = 2;
    static final int MEAN_SPEED = 5;
    static final int FERRY_SPEED = 10;
    private EncodedValue preferWayEncoder;
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
    public FootFlagEncoder() {
        this(4, 1);
    }

    public FootFlagEncoder(String propertiesStr) {
        this((int) parseLong(propertiesStr, "speedBits", 4), parseDouble(propertiesStr, "speedFactor", 1));
        this.setBlockFords(parseBoolean(propertiesStr, "blockFords", true));
    }

    public FootFlagEncoder(int speedBits, double speedFactor) {
        super(speedBits, speedFactor, 0);
        restrictions.addAll(Arrays.asList("foot", "access"));
        restrictedValues.add("private");
        restrictedValues.add("no");
        restrictedValues.add("restricted");
        restrictedValues.add("military");

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
        avoidHighwayTags.add("tertiary");
        avoidHighwayTags.add("tertiary_link");
        // for now no explicit avoiding #257
        // avoidHighwayTags.add("cycleway");

        allowedHighwayTags.addAll(safeHighwayTags);
        allowedHighwayTags.addAll(avoidHighwayTags);
        allowedHighwayTags.add("cycleway");
        allowedHighwayTags.add("secondary");
        allowedHighwayTags.add("secondary_link");
        allowedHighwayTags.add("unclassified");
        allowedHighwayTags.add("road");
        // disallowed in some countries
        // allowedHighwayTags.add("bridleway");

        // International Walking Network
        hikingNetworkToCode.put("iwn", BEST.getValue());
        // National Walking Network
        hikingNetworkToCode.put("nwn", BEST.getValue());
        // Regional Walking Network
        hikingNetworkToCode.put("rwn", VERY_NICE.getValue());
        // Local Walking Network
        hikingNetworkToCode.put("lwn", VERY_NICE.getValue());

        maxPossibleSpeed = FERRY_SPEED;
    }

    @Override
    public int defineWayBits(int index, int shift) {
        // first two bits are reserved for route handling in superclass
        shift = super.defineWayBits(index, shift);
        // larger value required - ferries are faster than pedestrians
        speedEncoder = new EncodedDoubleValue("Speed", shift, speedBits, speedFactor, MEAN_SPEED, maxPossibleSpeed);
        shift += speedEncoder.getBits();

        preferWayEncoder = new EncodedValue("PreferWay", shift, 3, 1, 0, 7);
        shift += preferWayEncoder.getBits();
        return shift;
    }

    @Override
    public int defineRelationBits(int index, int shift) {
        relationCodeEncoder = new EncodedValue("RelationCode", shift, 3, 1, 0, 7);
        return shift + relationCodeEncoder.getBits();
    }

    /**
     * Foot flag encoder does not provide any turn cost / restrictions
     */
    @Override
    public int defineTurnBits(int index, int shift) {
        return shift;
    }

    /**
     * Foot flag encoder does not provide any turn cost / restrictions
     * <p>
     * 
     * @return <code>false</code>
     */
    @Override
    public boolean isTurnRestricted(long flag) {
        return false;
    }

    /**
     * Foot flag encoder does not provide any turn cost / restrictions
     * <p>
     * 
     * @return 0
     */
    @Override
    public double getTurnCost(long flag) {
        return 0;
    }

    @Override
    public long getTurnFlags(boolean restricted, double costs) {
        return 0;
    }

    /**
     * Some ways are okay but not separate for pedestrians.
     * <p/>
     * 
     * @param way
     */
    @Override
    public long acceptWay(Way way) {
        String highwayValue = way.getTag("highway");
        if (highwayValue == null) {
            if (way.hasTag("route", ferries)) {
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
        if (sacScale != null) {
            if (!"hiking".equals(sacScale) && !"mountain_hiking".equals(sacScale)
                    && !"demanding_mountain_hiking".equals(sacScale) && !"alpine_hiking".equals(sacScale))
                // other scales are too dangerous, see
                // http://wiki.openstreetmap.org/wiki/Key:sac_scale
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
        if (way.hasTag(restrictions, restrictedValues))
            return 0;

        // do not accept railways (sometimes incorrectly mapped!)
        if (way.hasTag("railway") && !way.hasTag("railway", acceptedRailways))
            return 0;

        return acceptBit;
    }

    @Override
    public long handleRelationTags(Relation relation, long oldRelationFlags) {
        int code = 0;
        if (relation.hasTag("route", "hiking") || relation.hasTag("route", "foot")) {
            Integer val = hikingNetworkToCode.get(relation.getTag("network"));
            if (val != null)
                code = val;
        } else if (relation.hasTag("route", "ferry")) {
            code = PriorityCode.AVOID_IF_POSSIBLE.getValue();
        }

        int oldCode = (int) relationCodeEncoder.getValue(oldRelationFlags);
        if (oldCode < code)
            return relationCodeEncoder.setValue(0, code);
        return oldRelationFlags;
    }

    @Override
    public long handleWayTags(Way way, long allowed, long relationFlags) {
        if (!isAccept(allowed))
            return 0;

        long encoded;
        if (!isFerry(allowed)) {
            String sacScale = way.getTag("sac_scale");
            if (sacScale != null) {
                if ("hiking".equals(sacScale))
                    encoded = speedEncoder.setDoubleValue(0, MEAN_SPEED);
                else
                    encoded = speedEncoder.setDoubleValue(0, SLOW_SPEED);
            } else {
                encoded = speedEncoder.setDoubleValue(0, MEAN_SPEED);
            }
            encoded |= directionBitMask;

            int priorityFromRelation = 0;
            if (relationFlags != 0)
                priorityFromRelation = (int) relationCodeEncoder.getValue(relationFlags);

            encoded = setLong(encoded, PriorityWeighting.KEY, handlePriority(way, priorityFromRelation));

            boolean isRoundabout = way.hasTag("junction", "roundabout");
            if (isRoundabout) {
                encoded = setBool(encoded, K_ROUNDABOUT, true);
            }

        } else {
            encoded = handleFerryTags(way, SLOW_SPEED, MEAN_SPEED, FERRY_SPEED);
            encoded |= directionBitMask;
        }
        long anno = super.handleWayTagsDecorators(way);

        return encoded |= anno;
    }

    @Override
    public double getDouble(long flags, int key) {
        switch (key) {
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
    public long getLong(long flags, int key) {
        switch (key) {
        case PriorityWeighting.KEY:
            return preferWayEncoder.getValue(flags);
        default:
            return super.getLong(flags, key);
        }
    }

    @Override
    public long setLong(long flags, int key, long value) {
        switch (key) {
        case PriorityWeighting.KEY:
            return preferWayEncoder.setValue(flags, value);
        default:
            return super.setLong(flags, key, value);
        }
    }

    protected int handlePriority(Way way, int priorityFromRelation) {
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
     * @param weightToPrioMap
     *            associate a weight with every priority. This sorted map allows
     *            subclasses to 'insert' more important priorities as well as
     *            overwrite determined priorities.
     */
    void collect(Way way, TreeMap<Double, Integer> weightToPrioMap) {
        String highway = way.getTag("highway");
        if (way.hasTag("foot", "designated"))
            weightToPrioMap.put(100d, PREFER.getValue());

        double maxSpeed = getMaxSpeed(way);
        if (safeHighwayTags.contains(highway) || maxSpeed > 0 && maxSpeed <= 20) {
            weightToPrioMap.put(40d, PREFER.getValue());
            if (way.hasTag("tunnel", intendedValues))
                weightToPrioMap.put(40d, UNCHANGED.getValue());
        }

        if (way.hasTag("bicycle", "official") || way.hasTag("bicycle", "designated")) {
            weightToPrioMap.put(44d, AVOID_IF_POSSIBLE.getValue());
        }

        if (way.hasTag("sidewalk", sidewalks)) {
            weightToPrioMap.put(45d, PREFER.getValue());
        }

        if (avoidHighwayTags.contains(highway) || maxSpeed > 50) {
            weightToPrioMap.put(50d, REACH_DEST.getValue());

            if (way.hasTag("tunnel", intendedValues))
                weightToPrioMap.put(50d, AVOID_AT_ALL_COSTS.getValue());
        }
    }

    @Override
    public boolean supports(Class<?> feature) {
        if (super.supports(feature))
            return true;

        return PriorityWeighting.class.isAssignableFrom(feature);
    }

    @Override
    public String toString() {
        return "foot";
    }
}
