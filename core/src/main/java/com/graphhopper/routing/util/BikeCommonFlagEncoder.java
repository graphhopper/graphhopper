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

import com.graphhopper.reader.ReaderRelation;
import com.graphhopper.reader.ReaderWay;
import com.graphhopper.routing.profiles.*;
import com.graphhopper.routing.weighting.PriorityWeighting;
import com.graphhopper.storage.IntsRef;
import com.graphhopper.util.Helper;
import com.graphhopper.util.InstructionAnnotation;
import com.graphhopper.util.Translation;

import java.util.*;

import static com.graphhopper.routing.util.PriorityCode.*;

/**
 * Defines bit layout of bicycles (not motorcycles) for speed, access and relations (network).
 * <p>
 *
 * @author Peter Karich
 * @author Nop
 * @author ratrun
 */
abstract public class BikeCommonFlagEncoder extends AbstractFlagEncoder {
    /**
     * Reports whether this edge is unpaved.
     */
    public static final int K_UNPAVED = 100;
    protected static final int PUSHING_SECTION_SPEED = 4;
    // Pushing section heighways are parts where you need to get off your bike and push it (German: Schiebestrecke)
    protected final HashSet<String> pushingSectionsHighways = new HashSet<>();
    protected final HashSet<String> oppositeLanes = new HashSet<>();
    protected final Set<String> preferHighwayTags = new HashSet<>();
    protected final Set<String> avoidHighwayTags = new HashSet<>();
    protected final Set<String> unpavedSurfaceTags = new HashSet<>();
    private final Map<String, Integer> trackTypeSpeeds = new HashMap<>();
    private final Map<String, Integer> surfaceSpeeds = new HashMap<>();
    private final Set<String> roadValues = new HashSet<>();
    private final Map<String, Integer> highwaySpeeds = new HashMap<>();
    // convert network tag of bicycle routes into a way route code
    private final Map<String, Integer> bikeNetworkToCode = new HashMap<>();
    protected EncodedValueOld relationCodeEncoder;
    protected boolean speedTwoDirections;
    DecimalEncodedValue priorityWayEncoder;
    private BooleanEncodedValue unpavedEncoder;
    private IntEncodedValue wayTypeEncoder;
    // Car speed limit which switches the preference from UNCHANGED to AVOID_IF_POSSIBLE
    private int avoidSpeedLimit;

    // This is the specific bicycle class
    private String classBicycleKey;

    protected BikeCommonFlagEncoder(int speedBits, double speedFactor, int maxTurnCosts) {
        super(speedBits, speedFactor, maxTurnCosts);
        // strict set, usually vehicle and agricultural/forestry are ignored by cyclists
        restrictions.addAll(Arrays.asList("bicycle", "vehicle", "access"));
        restrictedValues.add("private");
        restrictedValues.add("no");
        restrictedValues.add("restricted");
        restrictedValues.add("military");
        restrictedValues.add("emergency");

        intendedValues.add("yes");
        intendedValues.add("designated");
        intendedValues.add("official");
        intendedValues.add("permissive");

        oppositeLanes.add("opposite");
        oppositeLanes.add("opposite_lane");
        oppositeLanes.add("opposite_track");

        setBlockByDefault(false);
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

        roadValues.add("living_street");
        roadValues.add("road");
        roadValues.add("service");
        roadValues.add("unclassified");
        roadValues.add("residential");
        roadValues.add("trunk");
        roadValues.add("trunk_link");
        roadValues.add("primary");
        roadValues.add("primary_link");
        roadValues.add("secondary");
        roadValues.add("secondary_link");
        roadValues.add("tertiary");
        roadValues.add("tertiary_link");

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

        setCyclingNetworkPreference("icn", BEST.getValue());
        setCyclingNetworkPreference("ncn", BEST.getValue());
        setCyclingNetworkPreference("rcn", VERY_NICE.getValue());
        setCyclingNetworkPreference("lcn", PREFER.getValue());
        setCyclingNetworkPreference("mtb", UNCHANGED.getValue());

        setCyclingNetworkPreference("deprecated", AVOID_AT_ALL_COSTS.getValue());

        speedDefault = highwaySpeeds.get("cycleway");
        setAvoidSpeedLimit(71);
    }

    @Override
    public int getVersion() {
        return 3;
    }

    @Override
    public void createEncodedValues(List<EncodedValue> registerNewEncodedValue, String prefix, int index) {
        // first two bits are reserved for route handling in superclass
        super.createEncodedValues(registerNewEncodedValue, prefix, index);
        registerNewEncodedValue.add(speedEncoder = new FactorizedDecimalEncodedValue(prefix + "average_speed", speedBits, speedFactor, speedTwoDirections));
        registerNewEncodedValue.add(unpavedEncoder = new SimpleBooleanEncodedValue(prefix + "paved", false));
        registerNewEncodedValue.add(wayTypeEncoder = new SimpleIntEncodedValue(prefix + "waytype", 2, false));
        registerNewEncodedValue.add(priorityWayEncoder = new FactorizedDecimalEncodedValue(prefix + "priority", 3, PriorityCode.getFactor(1), false));
    }

    @Override
    public int defineRelationBits(int index, int shift) {
        relationCodeEncoder = new EncodedValueOld("RelationCode", shift, 3, 1, 0, 7);
        return shift + relationCodeEncoder.getBits();
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
        if ("motorway".equals(highwayValue) || "motorway_link".equals(highwayValue))
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

    @Override
    public long handleRelationTags(long oldRelationFlags, ReaderRelation relation) {
        int code = 0;
        if (relation.hasTag("route", "bicycle")) {
            Integer val = bikeNetworkToCode.get(relation.getTag("network"));
            if (val != null)
                code = val;
            else
                code = PriorityCode.PREFER.getValue();  // Assume priority of network "lcn" as bicycle route default
        } else if (relation.hasTag("route", "ferry")) {
            code = AVOID_IF_POSSIBLE.getValue();
        }

        int oldCode = (int) relationCodeEncoder.getValue(oldRelationFlags);
        if (oldCode < code)
            return relationCodeEncoder.setValue(0, code);
        return oldRelationFlags;
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
        if (maxSpeed >= 0) {
            // We strictly obey speed limits, see #600
            if (speed > maxSpeed)
                return maxSpeed;
        }
        if (speed > maxPossibleSpeed)
            return maxPossibleSpeed;
        return speed;
    }

    @Override
    public IntsRef handleWayTags(IntsRef edgeFlags, ReaderWay way, EncodingManager.Access access, long relationFlags) {
        if (access.canSkip())
            return edgeFlags;

        double wayTypeSpeed = getSpeed(way);
        if (!access.isFerry()) {
            wayTypeSpeed = applyMaxSpeed(way, wayTypeSpeed);
            handleSpeed(edgeFlags, way, wayTypeSpeed);
            handleBikeRelated(edgeFlags, way, relationFlags > UNCHANGED.getValue());
        } else {
            double ferrySpeed = getFerrySpeed(way);
            handleSpeed(edgeFlags, way, ferrySpeed);
            accessEnc.setBool(false, edgeFlags, true);
            accessEnc.setBool(true, edgeFlags, true);
        }
        int priorityFromRelation = 0;
        if (relationFlags != 0)
            priorityFromRelation = (int) relationCodeEncoder.getValue(relationFlags);

        priorityWayEncoder.setDecimal(false, edgeFlags, PriorityCode.getFactor(handlePriority(way, wayTypeSpeed, priorityFromRelation)));
        return edgeFlags;
    }

    int getSpeed(ReaderWay way) {
        int speed = PUSHING_SECTION_SPEED;
        String highwayTag = way.getTag("highway");
        Integer highwaySpeed = highwaySpeeds.get(highwayTag);

        // Under certain conditions we need to increase the speed of pushing sections to the speed of a "highway=cycleway"
        if (way.hasTag("highway", pushingSectionsHighways)
                && ((way.hasTag("foot", "yes") && way.hasTag("segregated", "yes"))
                || way.hasTag("bicycle", "designated") || way.hasTag("bicycle", "official")))
            highwaySpeed = getHighwaySpeed("cycleway");

        String s = way.getTag("surface");
        if (!Helper.isEmpty(s)) {
            Integer surfaceSpeed = surfaceSpeeds.get(s);
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
        // Now we check that, but only in case that our speed is bigger compared to the PUSHING_SECTION_SPEED
        if (speed > PUSHING_SECTION_SPEED
                && (way.hasTag("highway", pushingSectionsHighways) || way.hasTag("bicycle", "dismount"))) {
            if (!way.hasTag("bicycle", intendedValues)) {
                // Here we set the speed for pushing sections and set speed for steps as even lower:
                if (way.hasTag("highway", "steps"))
                    speed = PUSHING_SECTION_SPEED / 2;
                else
                    speed = PUSHING_SECTION_SPEED;
            } else if (way.hasTag("bicycle", "designated") || way.hasTag("bicycle", "official")) {
                // Here we handle the cases where the OSM tagging results in something similar to "highway=cycleway"
                speed = highwaySpeeds.get("cycleway");
            } else {
                speed = PUSHING_SECTION_SPEED;
            }
            // Increase speed in case of segregated
            if (speed <= PUSHING_SECTION_SPEED && way.hasTag("segregated", "yes"))
                speed = PUSHING_SECTION_SPEED * 2;
        }
        return speed;
    }

    @Override
    public InstructionAnnotation getAnnotation(IntsRef edgeFlags, Translation tr) {
        int paveType = 0; // paved
        if (unpavedEncoder.getBool(false, edgeFlags))
            paveType = 1; // unpaved

        int wayType = wayTypeEncoder.getInt(false, edgeFlags);
        String wayName = getWayName(paveType, wayType, tr);
        return new InstructionAnnotation(0, wayName);
    }

    String getWayName(int pavementType, int wayType, Translation tr) {
        String pavementName = "";
        if (pavementType == 1)
            pavementName = tr.tr("unpaved");

        String wayTypeName = "";
        switch (wayType) {
            case 0:
                wayTypeName = "";
                break;
            case 1:
                wayTypeName = tr.tr("off_bike");
                break;
            case 2:
                wayTypeName = tr.tr("cycleway");
                break;
            case 3:
                wayTypeName = tr.tr("small_way");
                break;
        }

        if (pavementName.isEmpty()) {
            if (wayType == 0 || wayType == 3)
                return "";
            return wayTypeName;
        } else if (wayTypeName.isEmpty())
            return pavementName;
        else
            return wayTypeName + ", " + pavementName;
    }

    /**
     * In this method we prefer cycleways or roads with designated bike access and avoid big roads
     * or roads with trams or pedestrian.
     *
     * @return new priority based on priorityFromRelation and on the tags in ReaderWay.
     */
    protected int handlePriority(ReaderWay way, double wayTypeSpeed, int priorityFromRelation) {
        TreeMap<Double, Integer> weightToPrioMap = new TreeMap<>();
        if (priorityFromRelation == 0)
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
        if (preferHighwayTags.contains(highway) || maxSpeed > 0 && maxSpeed <= 30) {
            if (maxSpeed < avoidSpeedLimit) {
                weightToPrioMap.put(40d, PREFER.getValue());
                if (way.hasTag("tunnel", intendedValues))
                    weightToPrioMap.put(40d, UNCHANGED.getValue());
            }
        } else if (avoidHighwayTags.contains(highway)
                || maxSpeed >= avoidSpeedLimit && !"track".equals(highway)) {
            weightToPrioMap.put(50d, REACH_DEST.getValue());
            if (way.hasTag("tunnel", intendedValues))
                weightToPrioMap.put(50d, AVOID_AT_ALL_COSTS.getValue());
        }

        if (pushingSectionsHighways.contains(highway)
                || way.hasTag("bicycle", "use_sidepath")
                || "parking_aisle".equals(service)) {
            int pushingSectionPrio = AVOID_IF_POSSIBLE.getValue();
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

    /**
     * Handle surface and wayType encoding
     */
    void handleBikeRelated(IntsRef edgeFlags, ReaderWay way, boolean partOfCycleRelation) {
        String surfaceTag = way.getTag("surface");
        String highway = way.getTag("highway");
        String trackType = way.getTag("tracktype");

        // Populate unpavedBit
        if ("track".equals(highway) && (trackType == null || !"grade1".equals(trackType))
                || "path".equals(highway) && surfaceTag == null
                || unpavedSurfaceTags.contains(surfaceTag)) {
            unpavedEncoder.setBool(false, edgeFlags, true);
        }

        WayType wayType;
        if (roadValues.contains(highway))
            wayType = WayType.ROAD;
        else
            wayType = WayType.OTHER_SMALL_WAY;

        boolean isPushingSection = isPushingSection(way);
        if (isPushingSection && !partOfCycleRelation || "steps".equals(highway))
            wayType = WayType.PUSHING_SECTION;

        if (way.hasTag("bicycle", intendedValues)) {
            if (isPushingSection && !way.hasTag("bicycle", "designated"))
                wayType = WayType.OTHER_SMALL_WAY;
            else if (wayType == WayType.OTHER_SMALL_WAY || wayType == WayType.PUSHING_SECTION)
                wayType = WayType.CYCLEWAY;
        } else if ("cycleway".equals(highway))
            wayType = WayType.CYCLEWAY;

        wayTypeEncoder.setInt(false, edgeFlags, wayType.getValue());
    }

    boolean isPushingSection(ReaderWay way) {
        return way.hasTag("highway", pushingSectionsHighways) || way.hasTag("railway", "platform") || way.hasTag("bicycle", "dismount");
    }

    protected void handleSpeed(IntsRef edgeFlags, ReaderWay way, double speed) {
        speedEncoder.setDecimal(false, edgeFlags, speed);

        // handle oneways        
        boolean isOneway = way.hasTag("oneway", oneways)
                || way.hasTag("oneway:bicycle", oneways)
                || way.hasTag("vehicle:backward")
                || way.hasTag("vehicle:forward")
                || way.hasTag("bicycle:forward");

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

    protected void setHighwaySpeed(String highway, int speed) {
        highwaySpeeds.put(highway, speed);
    }

    protected int getHighwaySpeed(String key) {
        return highwaySpeeds.get(key);
    }

    void setTrackTypeSpeed(String tracktype, int speed) {
        trackTypeSpeeds.put(tracktype, speed);
    }

    void setSurfaceSpeed(String surface, int speed) {
        surfaceSpeeds.put(surface, speed);
    }

    void setCyclingNetworkPreference(String network, int code) {
        bikeNetworkToCode.put(network, code);
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

    public void setAvoidSpeedLimit(int limit) {
        avoidSpeedLimit = limit;
    }

    protected void setSpecificClassBicycle(String subkey) {
        classBicycleKey = "class:bicycle:" + subkey;
    }

    private enum WayType {
        ROAD(0),
        PUSHING_SECTION(1),
        CYCLEWAY(2),
        OTHER_SMALL_WAY(3);

        private final int value;

        private WayType(int value) {
            this.value = value;
        }

        public int getValue() {
            return value;
        }
    }
}
