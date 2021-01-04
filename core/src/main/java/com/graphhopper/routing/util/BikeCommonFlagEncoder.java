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
import com.graphhopper.routing.weighting.PriorityWeighting;
import com.graphhopper.storage.IntsRef;
import com.graphhopper.util.Helper;

import java.util.*;

import static com.graphhopper.routing.ev.RouteNetwork.*;
import static com.graphhopper.routing.util.EncodingManager.getKey;
import static com.graphhopper.routing.util.PriorityCode.*;

/**
 * Defines bit layout of bicycles (not motorcycles) for speed, access and relations (network).
 *
 * @author Peter Karich
 * @author Nop
 * @author ratrun
 */
abstract public class BikeCommonFlagEncoder extends AbstractFlagEncoder {

    protected static final int PUSHING_SECTION_SPEED = 4;
    // Pushing section highways are parts where you need to get off your bike and push it (German: Schiebestrecke)
    protected final HashSet<String> pushingSectionsHighways = new HashSet<>();
    protected final HashSet<String> oppositeLanes = new HashSet<>();
    protected final Set<String> preferHighwayTags = new HashSet<>();
    protected final Set<String> avoidHighwayTags = new HashSet<>();
    protected final Set<String> unpavedSurfaceTags = new HashSet<>();
    private final Map<String, Integer> trackTypeSpeeds = new HashMap<>();
    private final Map<String, Integer> surfaceSpeeds = new HashMap<>();
    private final Map<String, Integer> highwaySpeeds = new HashMap<>();
    protected boolean speedTwoDirections;
    DecimalEncodedValue priorityEnc;
    // Car speed limit which switches the preference from UNCHANGED to AVOID_IF_POSSIBLE
    private int avoidSpeedLimit;
    EnumEncodedValue<RouteNetwork> bikeRouteEnc;
    Map<RouteNetwork, Integer> routeMap = new HashMap<>();

    // This is the specific bicycle class
    private String classBicycleKey;

    protected BikeCommonFlagEncoder(int speedBits, double speedFactor, int maxTurnCosts) {
        super(speedBits, speedFactor, maxTurnCosts);

        restrictedValues.add("no");
        restrictedValues.add("restricted");
        restrictedValues.add("military");
        restrictedValues.add("emergency");
        restrictedValues.add("private");

        intendedValues.add("yes");
        intendedValues.add("designated");
        intendedValues.add("official");
        intendedValues.add("permissive");

        oppositeLanes.add("opposite");
        oppositeLanes.add("opposite_lane");
        oppositeLanes.add("opposite_track");

        blockBarriersByDefault(false);
        potentialBarriers.add("gate");
        // potentialBarriers.add("lift_gate");
        potentialBarriers.add("swing_gate");
        potentialBarriers.add("cattle_grid");

        absoluteBarriers.add("fence");
        absoluteBarriers.add("stile");
        absoluteBarriers.add("turnstile");

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

        maxPossibleSpeed = 30;

        setTrackTypeSpeed("grade1", 18); // paved
        setTrackTypeSpeed("grade2", 12); // now unpaved ...
        setTrackTypeSpeed("grade3", 8);
        setTrackTypeSpeed("grade4", 6);
        setTrackTypeSpeed("grade5", 4); // like sand/grass     

        setSurfaceSpeed("paved", 18);
        setSurfaceSpeed("asphalt", 18);
        setSurfaceSpeed("cobblestone", 8);
        setSurfaceSpeed("cobblestone:flattened", 10);
        setSurfaceSpeed("sett", 10);
        setSurfaceSpeed("concrete", 18);
        setSurfaceSpeed("concrete:lanes", 16);
        setSurfaceSpeed("concrete:plates", 16);
        setSurfaceSpeed("paving_stones", 12);
        setSurfaceSpeed("paving_stones:30", 12);
        setSurfaceSpeed("unpaved", 14);
        setSurfaceSpeed("compacted", 16);
        setSurfaceSpeed("dirt", 10);
        setSurfaceSpeed("earth", 12);
        setSurfaceSpeed("fine_gravel", 18);
        setSurfaceSpeed("grass", 8);
        setSurfaceSpeed("grass_paver", 8);
        setSurfaceSpeed("gravel", 12);
        setSurfaceSpeed("ground", 12);
        setSurfaceSpeed("ice", PUSHING_SECTION_SPEED / 2);
        setSurfaceSpeed("metal", 10);
        setSurfaceSpeed("mud", 10);
        setSurfaceSpeed("pebblestone", 16);
        setSurfaceSpeed("salt", 6);
        setSurfaceSpeed("sand", 6);
        setSurfaceSpeed("wood", 6);

        setHighwaySpeed("living_street", 6);
        setHighwaySpeed("steps", PUSHING_SECTION_SPEED / 2);
        avoidHighwayTags.add("steps");

        final int CYCLEWAY_SPEED = 18;  // Make sure cycleway and path use same speed value, see #634
        setHighwaySpeed("cycleway", CYCLEWAY_SPEED);
        setHighwaySpeed("path", 10);
        setHighwaySpeed("footway", 6);
        setHighwaySpeed("platform", 6);
        setHighwaySpeed("pedestrian", 6);
        setHighwaySpeed("track", 12);
        setHighwaySpeed("service", 14);
        setHighwaySpeed("residential", 18);
        // no other highway applies:
        setHighwaySpeed("unclassified", 16);
        // unknown road:
        setHighwaySpeed("road", 12);

        setHighwaySpeed("trunk", 18);
        setHighwaySpeed("trunk_link", 18);
        setHighwaySpeed("primary", 18);
        setHighwaySpeed("primary_link", 18);
        setHighwaySpeed("secondary", 18);
        setHighwaySpeed("secondary_link", 18);
        setHighwaySpeed("tertiary", 18);
        setHighwaySpeed("tertiary_link", 18);

        // special case see tests and #191
        setHighwaySpeed("motorway", 18);
        setHighwaySpeed("motorway_link", 18);
        avoidHighwayTags.add("motorway");
        avoidHighwayTags.add("motorway_link");

        setHighwaySpeed("bridleway", 6);
        avoidHighwayTags.add("bridleway");

        routeMap.put(INTERNATIONAL, BEST.getValue());
        routeMap.put(NATIONAL, BEST.getValue());
        routeMap.put(REGIONAL, VERY_NICE.getValue());
        routeMap.put(LOCAL, PREFER.getValue());

        setAvoidSpeedLimit(71);
    }

    @Override
    public TransportationMode getTransportationMode() {
        return TransportationMode.BIKE;
    }

    @Override
    public int getVersion() {
        return 3;
    }

    @Override
    public void createEncodedValues(List<EncodedValue> registerNewEncodedValue, String prefix, int index) {
        // first two bits are reserved for route handling in superclass
        super.createEncodedValues(registerNewEncodedValue, prefix, index);
        registerNewEncodedValue.add(avgSpeedEnc = new UnsignedDecimalEncodedValue(getKey(prefix, "average_speed"), speedBits, speedFactor, speedTwoDirections));
        registerNewEncodedValue.add(priorityEnc = new UnsignedDecimalEncodedValue(getKey(prefix, "priority"), 3, PriorityCode.getFactor(1), false));

        bikeRouteEnc = getEnumEncodedValue(RouteNetwork.key("bike"), RouteNetwork.class);
    }

    @Override
    public EncodingManager.Access getAccess(ReaderWay way) {
        String highwayValue = way.getTag("highway");
        if (highwayValue == null) {
            EncodingManager.Access accept = EncodingManager.Access.CAN_SKIP;

            if (way.hasTag("route", ferries)) {
                // if bike is NOT explicitly tagged allow bike but only if foot is not specified
                String bikeTag = way.getTag("bicycle");
                if (bikeTag == null && !way.hasTag("foot") || intendedValues.contains(bikeTag))
                    accept = EncodingManager.Access.FERRY;
            }

            // special case not for all acceptedRailways, only platform
            if (way.hasTag("railway", "platform"))
                accept = EncodingManager.Access.WAY;

            if (way.hasTag("man_made", "pier"))
                accept = EncodingManager.Access.WAY;

            if (!accept.canSkip()) {
                if (way.hasTag(restrictions, restrictedValues) && !getConditionalTagInspector().isRestrictedWayConditionallyPermitted(way))
                    return EncodingManager.Access.CAN_SKIP;
                return accept;
            }

            return EncodingManager.Access.CAN_SKIP;
        }

        if (!highwaySpeeds.containsKey(highwayValue))
            return EncodingManager.Access.CAN_SKIP;

        String sacScale = way.getTag("sac_scale");
        if (sacScale != null) {
            if ((way.hasTag("highway", "cycleway"))
                    && (way.hasTag("sac_scale", "hiking")))
                return EncodingManager.Access.WAY;
            if (!isSacScaleAllowed(sacScale))
                return EncodingManager.Access.CAN_SKIP;
        }

        // use the way if it is tagged for bikes
        if (way.hasTag("bicycle", intendedValues) ||
                way.hasTag("bicycle", "dismount") ||
                way.hasTag("highway", "cycleway"))
            return EncodingManager.Access.WAY;

        // accept only if explicitly tagged for bike usage
        if ("motorway".equals(highwayValue) || "motorway_link".equals(highwayValue) || "bridleway".equals(highwayValue))
            return EncodingManager.Access.CAN_SKIP;

        if (way.hasTag("motorroad", "yes"))
            return EncodingManager.Access.CAN_SKIP;

        // do not use fords with normal bikes, flagged fords are in included above
        if (isBlockFords() && (way.hasTag("highway", "ford") || way.hasTag("ford")))
            return EncodingManager.Access.CAN_SKIP;

        // check access restrictions
        if (way.hasTag(restrictions, restrictedValues) && !getConditionalTagInspector().isRestrictedWayConditionallyPermitted(way))
            return EncodingManager.Access.CAN_SKIP;

        if (getConditionalTagInspector().isPermittedWayConditionallyRestricted(way))
            return EncodingManager.Access.CAN_SKIP;
        else
            return EncodingManager.Access.WAY;
    }

    boolean isSacScaleAllowed(String sacScale) {
        // other scales are nearly impossible by an ordinary bike, see http://wiki.openstreetmap.org/wiki/Key:sac_scale
        return "hiking".equals(sacScale);
    }

    /**
     * Apply maxspeed: In contrast to the implementation of the AbstractFlagEncoder, we assume that
     * we can reach the maxspeed for bicycles in case that the road type speed is higher and not
     * just only 90%.
     *
     * @param way   needed to retrieve tags
     * @param speed speed guessed e.g. from the road type or other tags
     * @return The assumed average speed.
     */
    @Override
    protected double applyMaxSpeed(ReaderWay way, double speed) {
        double maxSpeed = getMaxSpeed(way);
        // We strictly obey speed limits, see #600
        if (isValidSpeed(maxSpeed) && speed > maxSpeed) {
            return maxSpeed;
        }
        if (isValidSpeed(speed) && speed > maxPossibleSpeed)
            return maxPossibleSpeed;
        return speed;
    }

    @Override
    public IntsRef handleWayTags(IntsRef edgeFlags, ReaderWay way, EncodingManager.Access access) {
        if (access.canSkip())
            return edgeFlags;

        Integer priorityFromRelation = routeMap.get(bikeRouteEnc.getEnum(false, edgeFlags));
        double wayTypeSpeed = getSpeed(way);
        if (!access.isFerry()) {
            wayTypeSpeed = applyMaxSpeed(way, wayTypeSpeed);
            handleSpeed(edgeFlags, way, wayTypeSpeed);
        } else {
            double ferrySpeed = ferrySpeedCalc.getSpeed(way);
            handleSpeed(edgeFlags, way, ferrySpeed);
            accessEnc.setBool(false, edgeFlags, true);
            accessEnc.setBool(true, edgeFlags, true);
            priorityFromRelation = AVOID_IF_POSSIBLE.getValue();
        }

        priorityEnc.setDecimal(false, edgeFlags, PriorityCode.getFactor(handlePriority(way, wayTypeSpeed, priorityFromRelation)));
        return edgeFlags;
    }

    int getSpeed(ReaderWay way) {
        int speed = PUSHING_SECTION_SPEED;
        String highwayTag = way.getTag("highway");
        Integer highwaySpeed = highwaySpeeds.get(highwayTag);

        // Under certain conditions we need to increase the speed of pushing sections to the speed of a "highway=cycleway"
        if (way.hasTag("highway", pushingSectionsHighways)
                && ((way.hasTag("foot", "yes") && way.hasTag("segregated", "yes"))
                || (way.hasTag("bicycle", intendedValues))))
            highwaySpeed = getHighwaySpeed("cycleway");

        String s = way.getTag("surface");
        Integer surfaceSpeed = 0;
        if (!Helper.isEmpty(s)) {
            surfaceSpeed = surfaceSpeeds.get(s);
            if (surfaceSpeed != null) {
                speed = surfaceSpeed;
                // boost handling for good surfaces but avoid boosting if pushing section
                if (highwaySpeed != null && surfaceSpeed > highwaySpeed) {
                    if (pushingSectionsHighways.contains(highwayTag))
                        speed = highwaySpeed;
                    else
                        speed = surfaceSpeed;
                }
            }
        } else {
            String tt = way.getTag("tracktype");
            if (!Helper.isEmpty(tt)) {
                Integer tInt = trackTypeSpeeds.get(tt);
                if (tInt != null)
                    speed = tInt;
            } else if (highwaySpeed != null) {
                if (!way.hasTag("service"))
                    speed = highwaySpeed;
                else
                    speed = highwaySpeeds.get("living_street");
            }
        }

        // Until now we assumed that the way is no pushing section
        // Now we check that, but only in case that our speed computed so far is bigger compared to the PUSHING_SECTION_SPEED
        if (speed > PUSHING_SECTION_SPEED
                && (way.hasTag("highway", pushingSectionsHighways) || way.hasTag("bicycle", "dismount"))) {
            if (!way.hasTag("bicycle", intendedValues)) {
                // Here we set the speed for pushing sections and set speed for steps as even lower:
                speed = way.hasTag("highway", "steps") ? PUSHING_SECTION_SPEED / 2 : PUSHING_SECTION_SPEED;
            } else if (way.hasTag("bicycle", "designated") || way.hasTag("bicycle", "official") ||
                    way.hasTag("segregated", "yes") || way.hasTag("bicycle", "yes")) {
                // Here we handle the cases where the OSM tagging results in something similar to "highway=cycleway"
                if (way.hasTag("segregated", "yes"))
                    speed = highwaySpeeds.get("cycleway");
                else
                    speed = way.hasTag("bicycle", "yes") ? 10 : highwaySpeeds.get("cycleway");

                // overwrite our speed again in case we have a valid surface speed and if it is smaller as computed so far
                if ((surfaceSpeed > 0) && (surfaceSpeed < speed))
                    speed = surfaceSpeed;
            }
        }
        return speed;
    }

    /**
     * In this method we prefer cycleways or roads with designated bike access and avoid big roads
     * or roads with trams or pedestrian.
     *
     * @return new priority based on priorityFromRelation and on the tags in ReaderWay.
     */
    int handlePriority(ReaderWay way, double wayTypeSpeed, Integer priorityFromRelation) {
        TreeMap<Double, Integer> weightToPrioMap = new TreeMap<>();
        if (priorityFromRelation == null)
            weightToPrioMap.put(0d, UNCHANGED.getValue());
        else
            weightToPrioMap.put(110d, priorityFromRelation);

        collect(way, wayTypeSpeed, weightToPrioMap);

        // pick priority with biggest order value
        return weightToPrioMap.lastEntry().getValue();
    }

    // Conversion of class value to priority. See http://wiki.openstreetmap.org/wiki/Class:bicycle
    private PriorityCode convertClassValueToPriority(String tagvalue) {
        int classvalue;
        try {
            classvalue = Integer.parseInt(tagvalue);
        } catch (NumberFormatException e) {
            return UNCHANGED;
        }

        switch (classvalue) {
            case 3:
                return BEST;
            case 2:
                return VERY_NICE;
            case 1:
                return PREFER;
            case 0:
                return UNCHANGED;
            case -1:
                return AVOID_IF_POSSIBLE;
            case -2:
                return REACH_DEST;
            case -3:
                return AVOID_AT_ALL_COSTS;
            default:
                return UNCHANGED;
        }
    }

    /**
     * @param weightToPrioMap associate a weight with every priority. This sorted map allows
     *                        subclasses to 'insert' more important priorities as well as overwrite determined priorities.
     */
    void collect(ReaderWay way, double wayTypeSpeed, TreeMap<Double, Integer> weightToPrioMap) {
        String service = way.getTag("service");
        String highway = way.getTag("highway");
        if (way.hasTag("bicycle", "designated") || way.hasTag("bicycle", "official")) {
            if ("path".equals(highway))
                weightToPrioMap.put(100d, VERY_NICE.getValue());
            else
                weightToPrioMap.put(100d, PREFER.getValue());
        }

        if ("cycleway".equals(highway)) {
            if (way.hasTag("foot", intendedValues) && !way.hasTag("segregated", "yes"))
                weightToPrioMap.put(100d, PREFER.getValue());
            else
                weightToPrioMap.put(100d, VERY_NICE.getValue());
        }

        double maxSpeed = getMaxSpeed(way);
        if (preferHighwayTags.contains(highway) || (isValidSpeed(maxSpeed) && maxSpeed <= 30)) {
            if (!isValidSpeed(maxSpeed) || maxSpeed < avoidSpeedLimit) {
                weightToPrioMap.put(40d, PREFER.getValue());
                if (way.hasTag("tunnel", intendedValues))
                    weightToPrioMap.put(40d, UNCHANGED.getValue());
            }
        } else if (avoidHighwayTags.contains(highway)
                || isValidSpeed(maxSpeed) && maxSpeed >= avoidSpeedLimit && !"track".equals(highway)) {
            weightToPrioMap.put(50d, REACH_DEST.getValue());
            if (way.hasTag("tunnel", intendedValues))
                weightToPrioMap.put(50d, AVOID_AT_ALL_COSTS.getValue());
        }

        String cycleway = way.getFirstPriorityTag(Arrays.asList("cycleway", "cycleway:left", "cycleway:right"));
        if (Arrays.asList("lane", "shared_lane", "share_busway", "shoulder").contains(cycleway)) {
            weightToPrioMap.put(100d, UNCHANGED.getValue());
        } else if ("track".equals(cycleway)) {
            weightToPrioMap.put(100d, PREFER.getValue());
        }

        if (pushingSectionsHighways.contains(highway)
                || "parking_aisle".equals(service)) {
            int pushingSectionPrio = AVOID_IF_POSSIBLE.getValue();
            if (way.hasTag("bicycle", "use_sidepath")) {
                pushingSectionPrio = PREFER.getValue();
            }
            if (way.hasTag("bicycle", "yes") || way.hasTag("bicycle", "permissive"))
                pushingSectionPrio = PREFER.getValue();
            if (way.hasTag("bicycle", "designated") || way.hasTag("bicycle", "official"))
                pushingSectionPrio = VERY_NICE.getValue();
            if (way.hasTag("foot", "yes")) {
                pushingSectionPrio = Math.max(pushingSectionPrio - 1, WORST.getValue());
                if (way.hasTag("segregated", "yes"))
                    pushingSectionPrio = Math.min(pushingSectionPrio + 1, BEST.getValue());
            }
            weightToPrioMap.put(100d, pushingSectionPrio);
        }

        if (way.hasTag("railway", "tram"))
            weightToPrioMap.put(50d, AVOID_AT_ALL_COSTS.getValue());

        String classBicycleValue = way.getTag(classBicycleKey);
        if (classBicycleValue != null) {
            // We assume that humans are better in classifying preferences compared to our algorithm above -> weight = 100
            weightToPrioMap.put(100d, convertClassValueToPriority(classBicycleValue).getValue());
        } else {
            String classBicycle = way.getTag("class:bicycle");
            if (classBicycle != null)
                weightToPrioMap.put(100d, convertClassValueToPriority(classBicycle).getValue());
        }

        // Increase the priority for scenic routes or in case that maxspeed limits our average speed as compensation. See #630
        if (way.hasTag("scenic", "yes") || maxSpeed > 0 && maxSpeed < wayTypeSpeed) {
            if (weightToPrioMap.lastEntry().getValue() < BEST.getValue())
                // Increase the prio by one step
                weightToPrioMap.put(110d, weightToPrioMap.lastEntry().getValue() + 1);
        }
    }

    protected void handleSpeed(IntsRef edgeFlags, ReaderWay way, double speed) {
        avgSpeedEnc.setDecimal(false, edgeFlags, speed);

        // handle oneways
        boolean isOneway = way.hasTag("oneway", oneways)
                || way.hasTag("oneway:bicycle", oneways)
                || way.hasTag("vehicle:backward")
                || way.hasTag("vehicle:forward")
                || way.hasTag("bicycle:forward") && (way.hasTag("bicycle:forward", "yes") || way.hasTag("bicycle:forward", "no"));

        if ((isOneway || roundaboutEnc.getBool(false, edgeFlags))
                && !way.hasTag("oneway:bicycle", "no")
                && !way.hasTag("bicycle:backward")
                && !way.hasTag("cycleway", oppositeLanes)
                && !way.hasTag("cycleway:left", oppositeLanes)
                && !way.hasTag("cycleway:right", oppositeLanes)) {
            boolean isBackward = way.hasTag("oneway", "-1")
                    || way.hasTag("oneway:bicycle", "-1")
                    || way.hasTag("vehicle:forward", "no")
                    || way.hasTag("bicycle:forward", "no");
            if (isBackward)
                accessEnc.setBool(true, edgeFlags, true);
            else
                accessEnc.setBool(false, edgeFlags, true);

        } else {
            accessEnc.setBool(false, edgeFlags, true);
            accessEnc.setBool(true, edgeFlags, true);
        }
    }

    void setHighwaySpeed(String highway, int speed) {
        highwaySpeeds.put(highway, speed);
    }

    int getHighwaySpeed(String key) {
        return highwaySpeeds.get(key);
    }

    void setTrackTypeSpeed(String tracktype, int speed) {
        trackTypeSpeeds.put(tracktype, speed);
    }

    void setSurfaceSpeed(String surface, int speed) {
        surfaceSpeeds.put(surface, speed);
    }

    void addPushingSection(String highway) {
        pushingSectionsHighways.add(highway);
    }

    @Override
    public boolean supports(Class<?> feature) {
        if (super.supports(feature))
            return true;

        return PriorityWeighting.class.isAssignableFrom(feature);
    }

    void setAvoidSpeedLimit(int limit) {
        avoidSpeedLimit = limit;
    }

    void setSpecificClassBicycle(String subkey) {
        classBicycleKey = "class:bicycle:" + subkey;
    }
}
