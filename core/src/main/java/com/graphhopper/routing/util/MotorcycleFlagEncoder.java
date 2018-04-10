/*
 *  Licensed to GraphHopper GmbH under one or more contributor
 *  license agreements. See the NOTICE file distributed with this work for 
 *  additional information regarding copyright ownership.
 * 
 *  GraphHopper GmbH licenses this file to you under the Apache License, 
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

import com.graphhopper.reader.ReaderWay;
import com.graphhopper.routing.profiles.*;
import com.graphhopper.routing.profiles.tagparsers.TagParser;
import com.graphhopper.routing.weighting.CurvatureWeighting;
import com.graphhopper.routing.weighting.PriorityWeighting;
import com.graphhopper.storage.IntsRef;
import com.graphhopper.util.BitUtil;
import com.graphhopper.util.EdgeIteratorState;
import com.graphhopper.util.PMap;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

import static com.graphhopper.routing.profiles.TagParserFactory.ACCEPT_IF_HIGHWAY;

/**
 * Defines bit layout for motorbikes
 *
 * @author Peter Karich
 * @author boldtrn
 */
public class MotorcycleFlagEncoder extends CarFlagEncoder {
    private final HashSet<String> avoidSet = new HashSet<String>();
    private final HashSet<String> preferSet = new HashSet<String>();
    private DecimalEncodedValue priorityWayEnc;
    private DecimalEncodedValue curvatureEnc;

    public MotorcycleFlagEncoder() {
        this(5, 5, 0);
    }

    public MotorcycleFlagEncoder(PMap properties) {
        this(properties.getInt("speed_bits", 5),
                properties.getDouble("speed_factor", 5),
                properties.getBool("turn_costs", false) ? 1 : 0);
        this.setBlockFords(properties.getBool("block_fords", true));
    }

    public MotorcycleFlagEncoder(String propertiesStr) {
        this(new PMap(propertiesStr));
    }

    public MotorcycleFlagEncoder(int speedBits, double speedFactor, int maxTurnCosts) {
        super(speedBits, speedFactor, maxTurnCosts);

        setSpeedTwoDirections(true);

        restrictions.remove("motorcar");
        //  moped, mofa
        restrictions.add("motorcycle");

        absoluteBarriers.remove("bus_trap");
        absoluteBarriers.remove("sump_buster");

        trackTypeSpeedMap.clear();
        defaultSpeedMap.clear();

        trackTypeSpeedMap.put("grade1", 20d); // paved
        trackTypeSpeedMap.put("grade2", 15d); // now unpaved - gravel mixed with ...
        trackTypeSpeedMap.put("grade3", 10d); // ... hard and soft materials
        trackTypeSpeedMap.put("grade4", 5d); // ... some hard or compressed materials
        trackTypeSpeedMap.put("grade5", 5d); // ... no hard materials. soil/sand/grass

        avoidSet.add("motorway");
        avoidSet.add("trunk");
        avoidSet.add("motorroad");
        avoidSet.add("residential");

        preferSet.add("primary");
        preferSet.add("secondary");
        preferSet.add("tertiary");

        maxPossibleSpeed = 120;

        defaultSpeedMap.put("motorway", 100d);
        defaultSpeedMap.put("motorway_link", 70d);
        defaultSpeedMap.put("motorroad", 90d);
        defaultSpeedMap.put("trunk", 80d);
        defaultSpeedMap.put("trunk_link", 75d);
        defaultSpeedMap.put("primary", 65d);
        defaultSpeedMap.put("primary_link", 60d);
        defaultSpeedMap.put("secondary", 60d);
        defaultSpeedMap.put("secondary_link", 50d);
        defaultSpeedMap.put("tertiary", 50d);
        defaultSpeedMap.put("tertiary_link", 40d);
        defaultSpeedMap.put("unclassified", 30d);
        defaultSpeedMap.put("residential", 30d);
        defaultSpeedMap.put("living_street", 5d);
        defaultSpeedMap.put("service", 20d);
        defaultSpeedMap.put("road", 20d);
        defaultSpeedMap.put("track", 15d);

        init();
    }

    @Override
    public int getVersion() {
        return 3;
    }

    /**
     * Define the place of the speedBits in the edge flags for car.
     */
    @Override
    public Map<String, TagParser> createTagParsers(String prefix) {
        Map<String, TagParser> map = new HashMap<>();
        map.put("roundabout", null);

        map.put(prefix + "average_speed", TagParserFactory.Car.createAverageSpeed(new DecimalEncodedValue(prefix + "average_speed", speedBits, 0, speedFactor, true),
                defaultSpeedMap));
        ReaderWayFilter filter = new ReaderWayFilter() {
            @Override
            public boolean accept(ReaderWay way) {
                return defaultSpeedMap.containsKey(way.getTag("highway"));
            }
        };
        map.put(prefix + "access", TagParserFactory.Car.createAccess(new BooleanEncodedValue(prefix + "access", true), filter));

        final DecimalEncodedValue priorityWayEnc = new DecimalEncodedValue(prefix + "priority", 3, 3, .15, false);
        map.put(priorityWayEnc.getName(), new TagParser() {
            @Override
            public String getName() {
                return priorityWayEnc.getName();
            }

            @Override
            public EncodedValue getEncodedValue() {
                return priorityWayEnc;
            }

            @Override
            public ReaderWayFilter getReadWayFilter() {
                return ACCEPT_IF_HIGHWAY;
            }

            @Override
            public void parse(IntsRef ints, ReaderWay way) {
// TODO NOW
            }
        });
        final DecimalEncodedValue curvatureEnc = new DecimalEncodedValue(TagParserFactory.CURVATURE, 4, 10, 1, false);
        map.put(curvatureEnc.getName(), new TagParser() {
            @Override
            public String getName() {
                return curvatureEnc.getName();
            }

            @Override
            public EncodedValue getEncodedValue() {
                return curvatureEnc;
            }

            @Override
            public ReaderWayFilter getReadWayFilter() {
                return ACCEPT_IF_HIGHWAY;
            }

            @Override
            public void parse(IntsRef ints, ReaderWay way) {
// TODO NOW
            }
        });
        return map;
    }

    @Override
    public void initEncodedValues(String prefix, int index) {
        super.initEncodedValues(prefix, index);
        priorityWayEnc = getDecimalEncodedValue(prefix + "priority");
        curvatureEnc = getDecimalEncodedValue("curvature");
    }

    @Override
    public double getReverseSpeed(IntsRef ints) {
        return averageSpeedEnc.getDecimal(true, ints);
    }


    @Override
    public IntsRef setReverseSpeed(IntsRef ints, double speed) {
        if (speed < 0 || Double.isNaN(speed))
            throw new IllegalArgumentException("Speed cannot be negative or NaN: " + speed
                    + ", flags:" + BitUtil.LITTLE.toBitString(ints));

        if (speed < speedFactor / 2) {
            averageSpeedEnc.setDecimal(true, ints, 0);
            accessEnc.setBool(true, ints, false);
        }

        if (speed > getMaxSpeed())
            speed = getMaxSpeed();

        averageSpeedEnc.setDecimal(true, ints, speed);
        return ints;
    }

    @Override
    public EncodingManager.Access getAccess(ReaderWay way) {
        String highwayValue = way.getTag("highway");
        String firstValue = way.getFirstPriorityTag(restrictions);
        if (highwayValue == null) {
            if (way.hasTag("route", ferries)) {
                if (restrictedValues.contains(firstValue))
                    return EncodingManager.Access.CAN_SKIP;
                if (intendedValues.contains(firstValue) ||
                        // implied default is allowed only if foot and bicycle is not specified:
                        firstValue.isEmpty() && !way.hasTag("foot") && !way.hasTag("bicycle"))
                    return EncodingManager.Access.FERRY;
            }
            return EncodingManager.Access.CAN_SKIP;
        }

        if ("track".equals(highwayValue)) {
            String tt = way.getTag("tracktype");
            if (tt != null && !tt.equals("grade1"))
                return EncodingManager.Access.CAN_SKIP;
        }

        if (!defaultSpeedMap.containsKey(highwayValue))
            return EncodingManager.Access.CAN_SKIP;

        if (way.hasTag("impassable", "yes") || way.hasTag("status", "impassable"))
            return EncodingManager.Access.CAN_SKIP;

        if (!firstValue.isEmpty()) {
            if (restrictedValues.contains(firstValue) && !getConditionalTagInspector().isRestrictedWayConditionallyPermitted(way))
                return EncodingManager.Access.CAN_SKIP;
            if (intendedValues.contains(firstValue))
                return EncodingManager.Access.WAY;
        }

        // do not drive street cars into fords
        if (isBlockFords() && ("ford".equals(highwayValue) || way.hasTag("ford")))
            return EncodingManager.Access.CAN_SKIP;

        if (getConditionalTagInspector().isPermittedWayConditionallyRestricted(way))
            return EncodingManager.Access.CAN_SKIP;
        else
            return EncodingManager.Access.WAY;
    }

    @Override
    public IntsRef handleWayTags(IntsRef ints, ReaderWay way, EncodingManager.Access allowed, long priorityFromRelation) {
        if (allowed.canSkip())
            return ints;

        if (allowed.isFerry()) {
            double ferrySpeed = getFerrySpeed(way, defaultSpeedMap.get("living_street"), defaultSpeedMap.get("service"), defaultSpeedMap.get("residential"));
            setSpeed(ints, ferrySpeed);
            setReverseSpeed(ints, ferrySpeed);
            accessEnc.setBool(false, ints, true);
            accessEnc.setBool(true, ints, true);

        } else {
            // get estimated speed from highway type
            double speed = getSpeed(way);
            speed = applyMaxSpeed(way, speed);

            double maxMCSpeed = parseSpeed(way.getTag("maxspeed:motorcycle"));
            if (maxMCSpeed > 0 && maxMCSpeed < speed)
                speed = maxMCSpeed * 0.9;

            // limit speed to max 30 km/h if bad surface
            if (speed > 30 && way.hasTag("surface", badSurfaceSpeedMap))
                speed = 30;

            boolean isRoundabout = way.hasTag("junction", "roundabout");

            if (way.hasTag("oneway", oneways) || isRoundabout) {
                if (way.hasTag("oneway", "-1")) {
                    setReverseSpeed(ints, speed);
                    accessEnc.setBool(true, ints, true);
                } else {
                    setSpeed(ints, speed);
                    accessEnc.setBool(false, ints, true);
                }
            } else {
                setSpeed(ints, speed);
                setReverseSpeed(ints, speed);
                accessEnc.setBool(false, ints, true);
                accessEnc.setBool(true, ints, true);
            }
        }

        // relations are not yet stored -> see BikeCommonFlagEncoder.defineRelationBits how to do this
        priorityWayEnc.setInt(false, ints, handlePriority(way, priorityFromRelation));

        // Set the curvature to the Maximum
        curvatureEnc.setDecimal(false, ints, 10);
        return ints;
    }

    private int handlePriority(ReaderWay way, long relationFlags) {
        String highway = way.getTag("highway", "");
        if (avoidSet.contains(highway)) {
            return PriorityCode.WORST.getValue();
        } else if (preferSet.contains(highway)) {
            return PriorityCode.BEST.getValue();
        }

        return PriorityCode.UNCHANGED.getValue();
    }

    @Override
    public void applyWayTags(ReaderWay way, EdgeIteratorState edge) {
        IntsRef ints = edge.getData();
        double speed = getSpeed(ints);
        double roadDistance = edge.getDistance();
        double beelineDistance = getBeelineDistance(way);
        double bendiness = beelineDistance / roadDistance;

        bendiness = discriminateSlowStreets(bendiness, speed);
        bendiness = increaseBendinessImpact(bendiness);
        bendiness = correctErrors(bendiness);

        curvatureEnc.setDecimal(false, ints, convertToInt(bendiness));
        edge.setData(ints);
    }

    private double getBeelineDistance(ReaderWay way) {
        return way.getTag("estimated_distance", Double.POSITIVE_INFINITY);
    }

    /**
     * Streets that slow are not fun and probably in a town.
     */
    protected double discriminateSlowStreets(double bendiness, double speed) {
        if (speed < 51) {
            return 1;
        }
        return bendiness;
    }

    /**
     * A really small bendiness or a bendiness greater than 1 indicates an error in the calculation.
     * Just ignore them. We use bendiness greater 1.2 since the beelineDistance is only
     * approximated, therefore it can happen on straight roads, that the beeline is longer than the
     * road.
     */
    protected double correctErrors(double bendiness) {
        if (bendiness < 0.01 || bendiness > 1) {
            return 1;
        }
        return bendiness;
    }

    /**
     * A good bendiness should become a greater impact. A bendiness close to 1 should not be
     * changed.
     */
    protected double increaseBendinessImpact(double bendiness) {
        return (Math.pow(bendiness, 2));
    }

    @Override
    public boolean supports(Class<?> feature) {
        if (super.supports(feature))
            return true;

        if (CurvatureWeighting.class.isAssignableFrom(feature)) {
            return true;
        }

        return PriorityWeighting.class.isAssignableFrom(feature);
    }

    protected int convertToInt(double bendiness) {
        bendiness = bendiness * 10;
        return (int) bendiness;
    }

    @Override
    public String toString() {
        return "motorcycle";
    }
}
