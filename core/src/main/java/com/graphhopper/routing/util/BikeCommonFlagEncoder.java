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
import com.graphhopper.routing.util.countryrules.CountryRule;
import com.graphhopper.routing.weighting.PenaltyWeighting;
import com.graphhopper.storage.IntsRef;
import com.graphhopper.util.Helper;

import java.util.*;
import java.util.stream.Stream;

import static com.graphhopper.routing.ev.RouteNetwork.*;
import static com.graphhopper.routing.ev.Cycleway.*;
import static com.graphhopper.routing.util.EncodingManager.getKey;
import static com.graphhopper.routing.util.PenaltyCode.*;

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
    protected final Set<Cycleway> withTrafficCyclewayTags = new HashSet<>();
    private final Map<String, Integer> trackTypeSpeeds = new HashMap<>();
    private final Map<String, Integer> surfaceSpeeds = new HashMap<>();
    protected static final double smoothnessFactorPushingSectionThreshold = 0.3d;
    private final Map<Smoothness, Double> smoothnessFactor = new HashMap<>();
    private final Map<String, Integer> highwaySpeeds = new HashMap<>();
    protected final boolean penaltyTwoDirections = true;
    protected boolean speedTwoDirections;
    protected boolean useFerries;
    protected final DecimalEncodedValue penaltyEnc;
    // Car speed limit which switches the preference from UNCHANGED to AVOID_IF_POSSIBLE
    private int avoidSpeedLimit;
    EnumEncodedValue<RouteNetwork> bikeRouteEnc;
    EnumEncodedValue<Smoothness> smoothnessEnc;
    Map<RouteNetwork, Double> routeMap = new HashMap<>();
    Map<Cycleway, Double> cyclewayMap = new EnumMap<>(Cycleway.class);
    Map<String, Double> highwayMap = new HashMap<>();

    // This is the specific bicycle class
    private String classBicycleKey;

    protected BikeCommonFlagEncoder(String name, int speedBits, double speedFactor, int maxTurnCosts,
            boolean speedTwoDirections, boolean useFerries) {
        super(name, speedBits, speedFactor, speedTwoDirections, maxTurnCosts);

        penaltyEnc = new DecimalEncodedValueImpl(getKey(name, "penalty"), 4, PenaltyCode.getFactor(1),
                penaltyTwoDirections);

        restrictedValues.add("agricultural");
        restrictedValues.add("forestry");
        restrictedValues.add("no");
        restrictedValues.add("restricted");
        restrictedValues.add("delivery");
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
        oppositeLanes.add("opposite_share_busway");

        barriers.add("fence");

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

        maxPossibleSpeed = avgSpeedEnc.getNextStorableValue(30);

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
        setHighwaySpeed("path", CYCLEWAY_SPEED);
        setHighwaySpeed("footway", 14);
        setHighwaySpeed("platform", 6);
        setHighwaySpeed("pedestrian", CYCLEWAY_SPEED);
        setHighwaySpeed("track", CYCLEWAY_SPEED);
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

        cyclewayMap.put(SEPARATE, REACH_DESTINATION.getValue());
        cyclewayMap.put(SHOULDER, UNCHANGED.getValue());
        cyclewayMap.put(SHARED_LANE, SLIGHT_PREFER.getValue());
        cyclewayMap.put(SHARE_BUSWAY, SLIGHT_PREFER.getValue());
        cyclewayMap.put(LANE, PREFER.getValue());
        cyclewayMap.put(TRACK, VERY_NICE.getValue());
        withTrafficCyclewayTags.add(SHARED_LANE);
        withTrafficCyclewayTags.add(SHOULDER);

        // Difficult to use ways
        highwayMap.put("bridleway", REACH_DESTINATION.getValue());
        highwayMap.put("steps", REACH_DESTINATION.getValue());
        highwayMap.put("platform", REACH_DESTINATION.getValue());
        // Busy ways
        highwayMap.put("motorway", REACH_DESTINATION.getValue());
        highwayMap.put("motorway_link", REACH_DESTINATION.getValue());
        highwayMap.put("trunk", REACH_DESTINATION.getValue());
        highwayMap.put("trunk_link", REACH_DESTINATION.getValue());
        highwayMap.put("primary", VERY_BAD.getValue());
        highwayMap.put("primary_link", VERY_BAD.getValue());
        highwayMap.put("secondary", AVOID_MORE.getValue());
        highwayMap.put("secondary_link", AVOID_MORE.getValue());
        highwayMap.put("tertiary", AVOID.getValue());
        highwayMap.put("tertiary_link", AVOID.getValue());
        // Pedestrian ways
        highwayMap.put("footway", UNCHANGED.getValue());
        highwayMap.put("path", SLIGHT_PREFER.getValue());
        highwayMap.put("pedestrian", VERY_NICE.getValue());
        // Quiet ways
        highwayMap.put("residential", SLIGHT_PREFER.getValue());
        highwayMap.put("service", SLIGHT_PREFER.getValue());
        highwayMap.put("unclassified", SLIGHT_PREFER.getValue());
        // Bike ways
        highwayMap.put("cycleway", BEST.getValue());

        setSmoothnessSpeedFactor(com.graphhopper.routing.ev.Smoothness.MISSING, 1.0d);
        setSmoothnessSpeedFactor(com.graphhopper.routing.ev.Smoothness.OTHER, 0.7d);

        setAvoidSpeedLimit(71);
    }

    @Override
    public TransportationMode getTransportationMode() {
        return TransportationMode.BIKE;
    }

    @Override
    public void createEncodedValues(List<EncodedValue> registerNewEncodedValue) {
        super.createEncodedValues(registerNewEncodedValue);
        registerNewEncodedValue.add(penaltyEnc);

        bikeRouteEnc = getEnumEncodedValue(RouteNetwork.key("bike"), RouteNetwork.class);
        smoothnessEnc = getEnumEncodedValue(Smoothness.KEY, Smoothness.class);
    }

    @Override
    public EncodingManager.Access getAccess(ReaderWay way) {
        String highwayValue = way.getTag("highway");
        if (highwayValue == null) {
            EncodingManager.Access access = EncodingManager.Access.CAN_SKIP;

            if (way.hasTag("route", ferries)) {
                // if bike is NOT explicitly tagged allow bike but only if foot is not specified either
                String bikeTag = way.getTag("bicycle");
                if (bikeTag == null && !way.hasTag("foot") || intendedValues.contains(bikeTag))
                    access = EncodingManager.Access.FERRY;
            }

            // special case not for all acceptedRailways, only platform
            if (way.hasTag("railway", "platform"))
                access = EncodingManager.Access.WAY;

            if (way.hasTag("man_made", "pier"))
                access = EncodingManager.Access.WAY;

            if (!access.canSkip()) {
                if (way.hasTag(restrictions, restrictedValues) && !getConditionalTagInspector().isRestrictedWayConditionallyPermitted(way))
                    return EncodingManager.Access.CAN_SKIP;
                return access;
            }

            return EncodingManager.Access.CAN_SKIP;
        }

        if (!highwaySpeeds.containsKey(highwayValue))
            return EncodingManager.Access.CAN_SKIP;

        String sacScale = way.getTag("sac_scale");
        if (sacScale != null) {
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
     * @param way   needed to retrieve tags
     * @param speed speed guessed e.g. from the road type or other tags
     * @return The assumed average speed.
     */
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
    public IntsRef handleWayTags(IntsRef edgeFlags, ReaderWay way) {
        EncodingManager.Access access = getAccess(way);
        if (access.canSkip())
            return edgeFlags;

        double wayTypeSpeed = getSpeed(way);
        if (!access.isFerry()) {
            wayTypeSpeed = applyMaxSpeed(way, wayTypeSpeed);
            Smoothness smoothness = smoothnessEnc.getEnum(false, edgeFlags);
            if (smoothness != Smoothness.MISSING) {
                // smoothness handling: Multiply speed with smoothnessFactor
                double smoothnessSpeedFactor = smoothnessFactor.get(smoothness);
                wayTypeSpeed = (smoothnessSpeedFactor <= smoothnessFactorPushingSectionThreshold) ?
                        PUSHING_SECTION_SPEED : Math.round(smoothnessSpeedFactor * wayTypeSpeed);
            }
            avgSpeedEnc.setDecimal(false, edgeFlags, wayTypeSpeed);
            if (avgSpeedEnc.isStoreTwoDirections())
                avgSpeedEnc.setDecimal(true, edgeFlags, wayTypeSpeed);
            handleAccess(edgeFlags, way);
        } else {
            handleFerrySpeedAndAccess(edgeFlags, way);
        }

        handlePenalty(edgeFlags, way, wayTypeSpeed);
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

    void handleFerrySpeedAndAccess(IntsRef edgeFlags, ReaderWay way) {
        double ferrySpeed = ferrySpeedCalc.getSpeed(way);
        avgSpeedEnc.setDecimal(false, edgeFlags, ferrySpeed);
        if (avgSpeedEnc.isStoreTwoDirections())
            avgSpeedEnc.setDecimal(true, edgeFlags, ferrySpeed);
        accessEnc.setBool(false, edgeFlags, useFerries);
        accessEnc.setBool(true, edgeFlags, useFerries);
    }

    /**
     * In this method we prefer cycleways or roads with designated bike access and
     * avoid big roads or roads with trams or pedestrian.
     *
     * Modifies penaltyEnc with a new penalty based on the tags in ReaderWay.
     */
    void handlePenalty(IntsRef edgeFlags, ReaderWay way, double wayTypeSpeed) {
        BidirectionalTreeMap<Double, Double> penaltyMap = new BidirectionalTreeMap<>();
        penaltyMap.put(0d, UNCHANGED.getValue());

        collect(edgeFlags, way, wayTypeSpeed, penaltyMap);

        penaltyEnc.setDecimal(false, edgeFlags, PenaltyCode.getValue(penaltyMap.lastEntry(false).getValue()));
        penaltyEnc.setDecimal(true, edgeFlags, PenaltyCode.getValue(penaltyMap.lastEntry(true).getValue()));
    }

    // Conversion of class value to penalty. See
    // http://wiki.openstreetmap.org/wiki/Class:bicycle
    private PenaltyCode convertClassValueToPenalty(String tagvalue) {
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
                return SLIGHT_AVOID;
            case -2:
                return AVOID;
            case -3:
                return AVOID_MORE;
            default:
                return UNCHANGED;
        }
    }

    /**
     * @param penaltyMap associate a weight with every penalty. This sorted map
     *                   allows subclasses to 'insert' more important penalties as
     *                   well as overwrite determined penalties.
     */
    void collect(IntsRef edgeFlags, ReaderWay way, double wayTypeSpeed,
            BidirectionalTreeMap<Double, Double> penaltyMap) {
        final double SPEED_KEY = 40d;
        final double HIGHWAY_KEY = 50d;
        final double CYCLE_INFRA_KEY = 100d;
        String service = way.getTag("service");
        String highway = way.getTag("highway");

        // Associate penalty with way speed
        double maxSpeed = getMaxSpeed(way);
        if (isValidSpeed(maxSpeed)) {
            if (maxSpeed <= avoidSpeedLimit) {
                penaltyMap.put(SPEED_KEY, PREFER.getValue());
            } else {
                penaltyMap.put(SPEED_KEY, AVOID.getValue());
            }
        }

        // Associate penalty with highway infrastructure
        Double highwayPenalty = highwayMap.get(highway);
        if (Objects.isNull(highwayPenalty)) {
            highwayPenalty = SLIGHT_AVOID.getValue();
        }
        penaltyMap.put(HIGHWAY_KEY, highwayPenalty);
        if (way.hasTag("tunnel", intendedValues)) {
            penaltyMap.put(HIGHWAY_KEY, AVOID_MORE.getValue());
        }
        if (pushingSectionsHighways.contains(highway)
                || "parking_aisle".equals(service)) {
            Double pushingSectionPenalty = SLIGHT_AVOID.getValue();
            if (way.hasTag("bicycle", "yes") || way.hasTag("bicycle", "permissive"))
                pushingSectionPenalty = PREFER.getValue();
            if (way.hasTag("bicycle", "designated") || way.hasTag("bicycle", "official"))
                pushingSectionPenalty = VERY_NICE.getValue();
            if (way.hasTag("foot", "yes")) {
                pushingSectionPenalty = Math.max(PenaltyCode.from(pushingSectionPenalty).tickUpBy(1).getValue(),
                        BAD.getValue());
                if (way.hasTag("segregated", "yes"))
                    pushingSectionPenalty = Math.min(PenaltyCode.from(pushingSectionPenalty).tickDownBy(1).getValue(),
                            BEST.getValue());
            }
            penaltyMap.put(HIGHWAY_KEY, pushingSectionPenalty);
        }
        if (way.hasTag("railway", "tram"))
            penaltyMap.put(HIGHWAY_KEY, AVOID_MORE.getValue());

        // Associate penalty with cycle infrastructure
        if (way.hasTag("bicycle", "designated", "official")) {
            penaltyMap.put(CYCLE_INFRA_KEY, VERY_NICE.getValue());
        }
        if (way.hasTag("bicycle", "use_sidepath")) {
            penaltyMap.put(CYCLE_INFRA_KEY, REACH_DESTINATION.getValue());
        }
        if (way.hasTag("cyclestreet", intendedValues)) {
            penaltyMap.put(CYCLE_INFRA_KEY, cyclewayMap.get(SHARED_LANE));
        }

        DrivingSide drivingSide = DrivingSide.find(way.getTag("driving_side"));
        CountryRule countryRule = way.getTag("country_rule", null);
        if (countryRule != null) {
            drivingSide = countryRule.getDrivingSide(way, drivingSide);
        }

        Cycleway cycleway = Cycleway.find(way.getFirstPriorityTag(Arrays.asList("cycleway", "cycleway:both"))),
                cyclewayForward = Cycleway.find(way.getTag("cycleway:" + drivingSide.toString())),
                cyclewayBackward = Cycleway.find(way.getTag("cycleway:" + DrivingSide.reverse(drivingSide).toString()));

        Double cyclewayPenalty = cyclewayMap.get(cycleway),
                cyclewayForwardPenalty = cyclewayMap.get(cyclewayForward),
                cyclewayBackwardPenalty = cyclewayMap.get(cyclewayBackward);

        if (withTrafficCyclewayTags.contains(cycleway))
            cyclewayPenalty = PenaltyCode.from(highwayPenalty).tickDownBy(1).getValue();
        if (withTrafficCyclewayTags.contains(cyclewayForward))
            cyclewayForwardPenalty = PenaltyCode.from(highwayPenalty).tickDownBy(1).getValue();
        if (withTrafficCyclewayTags.contains(cyclewayBackward))
            cyclewayBackwardPenalty = PenaltyCode.from(highwayPenalty).tickDownBy(1).getValue();

        if (Objects.nonNull(cyclewayPenalty)) {
            penaltyMap.put(CYCLE_INFRA_KEY, cyclewayPenalty);
        }
        if (isOneway(way) || roundaboutEnc.getBool(false, edgeFlags)) {
            // On oneway streets, any accessible infrastructure works
            Stream.of(cyclewayForwardPenalty, cyclewayBackwardPenalty)
                    .filter(Objects::nonNull)
                    .forEach(p -> penaltyMap.put(CYCLE_INFRA_KEY, p));
        } else {
            if (Objects.nonNull(cyclewayForwardPenalty)) {
                penaltyMap.put(false, CYCLE_INFRA_KEY, cyclewayForwardPenalty);
            }
            if (Objects.nonNull(cyclewayBackwardPenalty)) {
                penaltyMap.put(true, CYCLE_INFRA_KEY, cyclewayBackwardPenalty);
            }
        }

        String classBicycleValue = way.getTag(classBicycleKey);
        if (classBicycleValue != null) {
            // We assume that humans are better in classifying preferences compared to our
            // algorithm above
            penaltyMap.put(CYCLE_INFRA_KEY, convertClassValueToPenalty(classBicycleValue).getValue());
        } else {
            String classBicycle = way.getTag("class:bicycle");
            if (classBicycle != null)
                penaltyMap.put(CYCLE_INFRA_KEY, convertClassValueToPenalty(classBicycle).getValue());
        }
    }

    protected void handleAccess(IntsRef edgeFlags, ReaderWay way) {
        boolean isRoundabout = roundaboutEnc.getBool(false, edgeFlags);
        boolean isOnewayForCars = isRoundabout || way.hasTag("oneway", oneways);
        boolean isBackwardOnewayForCars = way.hasTag("oneway", "-1");
        boolean hasBikeExceptionToOneway = (
            way.hasTag("oneway:bicycle", "no")
            || way.hasTag("cycleway", oppositeLanes)
            || way.hasTag("cycleway:left:oneway", "-1", "no")
            || way.hasTag("cycleway:right:oneway", "-1", "no")
        );

        if (isOnewayForCars && !hasBikeExceptionToOneway) {
            accessEnc.setBool(isBackwardOnewayForCars, edgeFlags, true);
        } else {
            // Two-way for bikes
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

    void setSmoothnessSpeedFactor(Smoothness smoothness, double speedfactor) {
        smoothnessFactor.put(smoothness, speedfactor);
    }

    void addPushingSection(String highway) {
        pushingSectionsHighways.add(highway);
    }

    @Override
    public boolean supports(Class<?> feature) {
        if (super.supports(feature))
            return true;

        return PenaltyWeighting.class.isAssignableFrom(feature);
    }

    void setAvoidSpeedLimit(int limit) {
        avoidSpeedLimit = limit;
    }

    void setSpecificClassBicycle(String subkey) {
        classBicycleKey = "class:bicycle:" + subkey;
    }

    protected boolean isOneway(ReaderWay way) {
        return way.hasTag("oneway", oneways)
                || way.hasTag("oneway:bicycle", oneways)
                || way.hasTag("cycleway:left:oneway", oneways)
                || way.hasTag("cycleway:right:oneway", oneways)
                || way.hasTag("vehicle:backward", restrictedValues)
                || way.hasTag("vehicle:forward", restrictedValues)
                || way.hasTag("bicycle:backward", restrictedValues)
                || way.hasTag("bicycle:forward", restrictedValues);
    }
}
