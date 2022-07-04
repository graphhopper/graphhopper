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
import com.graphhopper.routing.ev.*;
import com.graphhopper.routing.util.parsers.helpers.OSMValueExtractor;
import com.graphhopper.storage.IntsRef;
import com.graphhopper.util.DistanceCalcEarth;
import com.graphhopper.util.EdgeIteratorState;
import com.graphhopper.util.PMap;
import com.graphhopper.util.PointList;

import java.util.HashSet;

import static com.graphhopper.routing.util.EncodingManager.getKey;

/**
 * Defines bit layout for motorbikes
 * <p>
 *
 * @author Peter Karich
 * @author boldtrn
 */
public class MotorcycleTagParser extends CarTagParser {
    public static final double MOTOR_CYCLE_MAX_SPEED = 120;
    private final HashSet<String> avoidSet = new HashSet<>();
    private final HashSet<String> preferSet = new HashSet<>();
    private final DecimalEncodedValue priorityWayEncoder;
    private final DecimalEncodedValue curvatureEncoder;

    public MotorcycleTagParser(EncodedValueLookup lookup, PMap properties) {
        this(
                lookup.getBooleanEncodedValue(VehicleAccess.key("motorcycle")),
                lookup.getDecimalEncodedValue(VehicleSpeed.key("motorcycle")),
                lookup.hasEncodedValue(TurnCost.key("motorcycle")) ? lookup.getDecimalEncodedValue(TurnCost.key("motorcycle")) : null,
                lookup.getBooleanEncodedValue(Roundabout.KEY),
                lookup.getDecimalEncodedValue(VehiclePriority.key("motorcycle")),
                lookup.getDecimalEncodedValue(getKey("motorcycle", "curvature")),
                new PMap(properties).putObject("name", "motorcycle"),
                TransportationMode.MOTORCYCLE
        );
    }

    public MotorcycleTagParser(BooleanEncodedValue accessEnc, DecimalEncodedValue speedEnc, DecimalEncodedValue turnCostEnc,
                               BooleanEncodedValue roundaboutEnc,
                               DecimalEncodedValue priorityWayEncoder, DecimalEncodedValue curvatureEnc, PMap properties, TransportationMode transportationMode) {
        super(accessEnc, speedEnc, turnCostEnc, roundaboutEnc, new PMap(properties).putObject("name", "motorcycle"), transportationMode, speedEnc.getNextStorableValue(MOTOR_CYCLE_MAX_SPEED));
        this.priorityWayEncoder = priorityWayEncoder;
        this.curvatureEncoder = curvatureEnc;

        barriers.remove("bus_trap");
        barriers.remove("sump_buster");

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
        avoidSet.add("residential");

        preferSet.add("primary");
        preferSet.add("secondary");
        preferSet.add("tertiary");

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

    @Override
    public WayAccess getAccess(ReaderWay way) {
        String highwayValue = way.getTag("highway");
        String firstValue = way.getFirstPriorityTag(restrictions);
        if (highwayValue == null) {
            if (way.hasTag("route", ferries)) {
                if (restrictedValues.contains(firstValue))
                    return WayAccess.CAN_SKIP;
                if (intendedValues.contains(firstValue) ||
                        // implied default is allowed only if foot and bicycle is not specified:
                        firstValue.isEmpty() && !way.hasTag("foot") && !way.hasTag("bicycle"))
                    return WayAccess.FERRY;
            }
            return WayAccess.CAN_SKIP;
        }

        if ("service".equals(highwayValue) && "emergency_access".equals(way.getTag("service"))) {
            return WayAccess.CAN_SKIP;
        }

        if ("track".equals(highwayValue)) {
            String tt = way.getTag("tracktype");
            if (tt != null && !tt.equals("grade1"))
                return WayAccess.CAN_SKIP;
        }

        if (!defaultSpeedMap.containsKey(highwayValue))
            return WayAccess.CAN_SKIP;

        if (way.hasTag("impassable", "yes") || way.hasTag("status", "impassable"))
            return WayAccess.CAN_SKIP;

        if (!firstValue.isEmpty()) {
            if (restrictedValues.contains(firstValue) && !getConditionalTagInspector().isRestrictedWayConditionallyPermitted(way))
                return WayAccess.CAN_SKIP;
            if (intendedValues.contains(firstValue))
                return WayAccess.WAY;
        }

        // do not drive street cars into fords
        if (isBlockFords() && ("ford".equals(highwayValue) || way.hasTag("ford")))
            return WayAccess.CAN_SKIP;

        if (getConditionalTagInspector().isPermittedWayConditionallyRestricted(way))
            return WayAccess.CAN_SKIP;
        else
            return WayAccess.WAY;
    }

    @Override
    public IntsRef handleWayTags(IntsRef edgeFlags, ReaderWay way) {
        WayAccess access = getAccess(way);
        if (access.canSkip())
            return edgeFlags;

        if (!access.isFerry()) {
            // get assumed speed from highway type
            double speed = getSpeed(way);
            speed = applyMaxSpeed(way, speed);

            double maxMCSpeed = OSMValueExtractor.stringToKmh(way.getTag("maxspeed:motorcycle"));
            if (isValidSpeed(maxMCSpeed) && maxMCSpeed < speed)
                speed = maxMCSpeed * 0.9;

            // limit speed to max 30 km/h if bad surface
            if (isValidSpeed(speed) && speed > 30 && way.hasTag("surface", badSurfaceSpeedMap))
                speed = 30;

            boolean isRoundabout = roundaboutEnc.getBool(false, edgeFlags);
            if (way.hasTag("oneway", oneways) || isRoundabout) {
                if (way.hasTag("oneway", "-1")) {
                    accessEnc.setBool(true, edgeFlags, true);
                    setSpeed(true, edgeFlags, speed);
                } else {
                    accessEnc.setBool(false, edgeFlags, true);
                    setSpeed(false, edgeFlags, speed);
                }
            } else {
                accessEnc.setBool(false, edgeFlags, true);
                accessEnc.setBool(true, edgeFlags, true);
                setSpeed(false, edgeFlags, speed);
                setSpeed(true, edgeFlags, speed);
            }

        } else {
            accessEnc.setBool(false, edgeFlags, true);
            accessEnc.setBool(true, edgeFlags, true);
            double ferrySpeed = ferrySpeedCalc.getSpeed(way);
            setSpeed(false, edgeFlags, ferrySpeed);
            setSpeed(true, edgeFlags, ferrySpeed);
        }

        priorityWayEncoder.setDecimal(false, edgeFlags, PriorityCode.getValue(handlePriority(way)));
        curvatureEncoder.setDecimal(false, edgeFlags, 10.0 / 7.0);
        return edgeFlags;
    }

    private int handlePriority(ReaderWay way) {
        String highway = way.getTag("highway", "");
        if (avoidSet.contains(highway)) {
            return PriorityCode.BAD.getValue();
        } else if (preferSet.contains(highway)) {
            return PriorityCode.BEST.getValue();
        }

        return PriorityCode.UNCHANGED.getValue();
    }

    @Override
    public void applyWayTags(ReaderWay way, EdgeIteratorState edge) {
        double speed = edge.get(avgSpeedEnc);
        double roadDistance = edge.getDistance();
        double beelineDistance = getBeelineDistance(way);
        double bendiness = beelineDistance / roadDistance;

        bendiness = discriminateSlowStreets(bendiness, speed);
        bendiness = increaseBendinessImpact(bendiness);
        bendiness = correctErrors(bendiness);

        edge.set(curvatureEncoder, bendiness);
    }

    private double getBeelineDistance(ReaderWay way) {
        PointList pointList = way.getTag("point_list", null);
        if (pointList == null)
            throw new IllegalStateException("The artificial 'point_list' tag is missing for way: " + way.getId());
        if (pointList.size() < 2)
            throw new IllegalStateException("The artificial 'point_list' tag contained less than two points for way: " + way.getId());
        return DistanceCalcEarth.DIST_EARTH.calcDist(pointList.getLat(0), pointList.getLon(0), pointList.getLat(pointList.size() - 1), pointList.getLon(pointList.size() - 1));
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

}
