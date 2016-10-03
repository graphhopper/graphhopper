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
import com.graphhopper.routing.weighting.PriorityWeighting;
import com.graphhopper.util.BitUtil;
import com.graphhopper.util.EdgeIteratorState;
import com.graphhopper.util.PMap;

import java.util.HashSet;

import static com.graphhopper.routing.util.PriorityCode.BEST;

/**
 * Defines bit layout for cars with four wheel drive
 * <p>
 *
 * @author Peter Karich
 * @author boldtrn
 * @author zstadler
 */
public class Car4WDFlagEncoder extends CarFlagEncoder {
    private final HashSet<String> avoidSet = new HashSet<String>();
    private final HashSet<String> preferSet = new HashSet<String>();
    private EncodedDoubleValue reverseSpeedEncoder;
    private EncodedValue priorityWayEncoder;
    private EncodedValue curvatureEncoder;

    public Car4WDFlagEncoder() {
        this(5, 5, 0);
    }

    public Car4WDFlagEncoder(PMap properties) {
	super(properties);
    }

    public Car4WDFlagEncoder(String propertiesStr) {
        this(new PMap(propertiesStr));
    }

    public Car4WDFlagEncoder(int speedBits, double speedFactor, int maxTurnCosts) {
        super(speedBits, speedFactor, maxTurnCosts);

        avoidSet.add("motorway");
        avoidSet.add("trunk");
        avoidSet.add("motorroad");
        avoidSet.add("residential");

        preferSet.add("track");
        preferSet.add("unclassified");

        init();
    }

    @Override
    public int getVersion() {
        return 1;
    }

    /**
     * Define the place of the speedBits in the edge flags for car.
     */
    @Override
    public int defineWayBits(int index, int shift) {
        // first two bits are reserved for route handling in superclass
        shift = super.defineWayBits(index, shift);
        priorityWayEncoder = new EncodedValue("PreferWay", shift, 3, 1, 3, 7);
        shift += priorityWayEncoder.getBits();

        return shift;
    }

    @Override
    public long acceptWay(ReaderWay way) {
        // TODO: Ferries have conditionals, like opening hours or are closed during some time in the year
        String highwayValue = way.getTag("highway");
        if (highwayValue == null) {
            if (way.hasTag("route", ferries)) {
                String motorcarTag = way.getTag("motorcar");
                if (motorcarTag == null)
                    motorcarTag = way.getTag("motor_vehicle");

                if (motorcarTag == null && !way.hasTag("foot") && !way.hasTag("bicycle") || "yes".equals(motorcarTag))
                    return acceptBit | ferryBit;
            }
            return 0;
        }

        if (!defaultSpeedMap.containsKey(highwayValue))
            return 0;

        if (way.hasTag("impassable", "yes") || way.hasTag("status", "impassable"))
            return 0;

        // multiple restrictions needs special handling compared to foot and bike, see also motorcycle
        String firstValue = way.getFirstPriorityTag(restrictions);
        if (!firstValue.isEmpty()) {
            if (restrictedValues.contains(firstValue) && !getConditionalTagInspector().isRestrictedWayConditionallyPermitted(way))
                return 0;
            if (intendedValues.contains(firstValue))
                return acceptBit;
        }

        // do not drive street cars into fords
        if (isBlockFords() && ("ford".equals(highwayValue) || way.hasTag("ford")))
            return 0;

        if (getConditionalTagInspector().isPermittedWayConditionallyRestricted(way))
            return 0;
        else
            return acceptBit;
    }

    @Override
    public long handleWayTags(ReaderWay way, long allowed, long priorityFromRelation) {
        if (!isAccept(allowed))
            return 0;

        long flags = super.handleWayTags(way, allowed, priorityFromRelation);
        // relations are not yet stored -> see BikeCommonFlagEncoder.defineRelationBits how to do this
        flags = priorityWayEncoder.setValue(flags, handlePriority(way, priorityFromRelation));

        return flags;
    }

    @Override
    public double getDouble(long flags, int key) {
        switch (key) {
            case PriorityWeighting.KEY:
                return (double) priorityWayEncoder.getValue(flags) / BEST.getValue();
            default:
                return super.getDouble(flags, key);
        }
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
        double speed = this.getSpeed(edge.getFlags());
        double roadDistance = edge.getDistance();
        double beelineDistance = getBeelineDistance(way);
        double bendiness = beelineDistance / roadDistance;

        bendiness = discriminateSlowStreets(bendiness, speed);
        bendiness = increaseBendinessImpact(bendiness);
        bendiness = correctErrors(bendiness);
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

        return PriorityWeighting.class.isAssignableFrom(feature);
    }

    protected int convertToInt(double bendiness) {
        bendiness = bendiness * 10;
        return (int) bendiness;
    }

    @Override
    public String toString() {
        return "car4wd";
    }
}
