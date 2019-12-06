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
import com.graphhopper.routing.weighting.PriorityWeighting;
import com.graphhopper.storage.IntsRef;
import com.graphhopper.util.PMap;

import java.util.*;

import static com.graphhopper.routing.profiles.RouteNetwork.*;
import static com.graphhopper.routing.util.EncodingManager.getKey;
import static com.graphhopper.routing.util.PriorityCode.*;

/**
 * Defines bit layout for pedestrians (speed, access, surface, ...). Here we put a penalty on unsafe
 * roads only. If you wish to also prefer routes due to beauty like hiking routes use the
 * HikeFlagEncoder instead.
 * <p>
 *
 * @author Peter Karich
 * @author Nop
 * @author Karl Hübner
 */
public class FootFlagEncoder extends AbstractFlagEncoder {
    static final int SLOW_SPEED = 2;
    static final int MEAN_SPEED = 5;
    static final int FERRY_SPEED = 15;
    final Set<String> safeHighwayTags = new HashSet<>();
    final Set<String> allowedHighwayTags = new HashSet<>();
    final Set<String> avoidHighwayTags = new HashSet<>();
    final Set<String> allowedSacScale = new HashSet<>();
    protected HashSet<String> sidewalkValues = new HashSet<>(5);
    protected HashSet<String> sidewalksNoValues = new HashSet<>(5);
    protected boolean speedTwoDirections;
    private DecimalEncodedValue priorityWayEncoder;
    private EnumEncodedValue<RouteNetwork> footRouteEnc;
    Map<RouteNetwork, Integer> routeMap = new HashMap<>();

    /**
     * Should be only instantiated via EncodingManager
     */
    public FootFlagEncoder() {
        this(4, 1);
    }

    public FootFlagEncoder(PMap properties) {
        this((int) properties.getLong("speed_bits", 4),
                properties.getDouble("speed_factor", 1));
        this.setBlockFords(properties.getBool("block_fords", false));
        this.speedTwoDirections = properties.getBool("speed_two_directions", false);
    }

    public FootFlagEncoder(String propertiesStr) {
        this(new PMap(propertiesStr));
    }

    public FootFlagEncoder(int speedBits, double speedFactor) {
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

        sidewalksNoValues.add("no");
        sidewalksNoValues.add("none");
        // see #712
        sidewalksNoValues.add("separate");

        sidewalkValues.add("yes");
        sidewalkValues.add("both");
        sidewalkValues.add("left");
        sidewalkValues.add("right");

        setBlockByDefault(false);
        absoluteBarriers.add("fence");
        potentialBarriers.add("gate");
        potentialBarriers.add("cattle_grid");

        safeHighwayTags.add("footway");
        safeHighwayTags.add("path");
        safeHighwayTags.add("steps");
        safeHighwayTags.add("pedestrian");
        safeHighwayTags.add("living_street");
        safeHighwayTags.add("track");
        safeHighwayTags.add("residential");
        safeHighwayTags.add("service");
        safeHighwayTags.add("platform");

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

        routeMap.put(INTERNATIONAL, UNCHANGED.getValue());
        routeMap.put(NATIONAL, UNCHANGED.getValue());
        routeMap.put(REGIONAL, UNCHANGED.getValue());
        routeMap.put(LOCAL, UNCHANGED.getValue());

        allowedSacScale.add("hiking");
        allowedSacScale.add("mountain_hiking");
        allowedSacScale.add("demanding_mountain_hiking");

        maxPossibleSpeed = FERRY_SPEED;
        speedDefault = MEAN_SPEED;
    }

    @Override
    public int getVersion() {
        return 5;
    }

    @Override
    public void createEncodedValues(List<EncodedValue> registerNewEncodedValue, String prefix, int index) {
        // first two bits are reserved for route handling in superclass
        super.createEncodedValues(registerNewEncodedValue, prefix, index);
        // larger value required - ferries are faster than pedestrians
        registerNewEncodedValue.add(avgSpeedEnc = new UnsignedDecimalEncodedValue(getKey(prefix, "average_speed"), speedBits, speedFactor, speedTwoDirections));
        registerNewEncodedValue.add(priorityWayEncoder = new UnsignedDecimalEncodedValue(getKey(prefix, "priority"), 3, PriorityCode.getFactor(1), speedTwoDirections));

        footRouteEnc = getEnumEncodedValue(getKey("foot", EV_SUFFIX), RouteNetwork.class);
    }

    /**
     * Some ways are okay but not separate for pedestrians.
     */
    @Override
    public EncodingManager.Access getAccess(ReaderWay way) {
        String highwayValue = way.getTag("highway");
        if (highwayValue == null) {
            EncodingManager.Access acceptPotentially = EncodingManager.Access.CAN_SKIP;

            if (way.hasTag("route", ferries)) {
                String footTag = way.getTag("foot");
                if (footTag == null || intendedValues.contains(footTag))
                    acceptPotentially = EncodingManager.Access.FERRY;
            }

            // special case not for all acceptedRailways, only platform
            if (way.hasTag("railway", "platform"))
                acceptPotentially = EncodingManager.Access.WAY;

            if (way.hasTag("man_made", "pier"))
                acceptPotentially = EncodingManager.Access.WAY;

            if (!acceptPotentially.canSkip()) {
                if (way.hasTag(restrictions, restrictedValues) && !getConditionalTagInspector().isRestrictedWayConditionallyPermitted(way))
                    return EncodingManager.Access.CAN_SKIP;
                return acceptPotentially;
            }

            return EncodingManager.Access.CAN_SKIP;
        }

        // other scales are too dangerous, see http://wiki.openstreetmap.org/wiki/Key:sac_scale
        if (way.getTag("sac_scale") != null && !way.hasTag("sac_scale", allowedSacScale))
            return EncodingManager.Access.CAN_SKIP;

        // no need to evaluate ferries or fords - already included here
        if (way.hasTag("foot", intendedValues))
            return EncodingManager.Access.WAY;

        // check access restrictions
        if (way.hasTag(restrictions, restrictedValues) && !getConditionalTagInspector().isRestrictedWayConditionallyPermitted(way))
            return EncodingManager.Access.CAN_SKIP;

        if (way.hasTag("sidewalk", sidewalkValues))
            return EncodingManager.Access.WAY;

        if (!allowedHighwayTags.contains(highwayValue))
            return EncodingManager.Access.CAN_SKIP;

        if (way.hasTag("motorroad", "yes"))
            return EncodingManager.Access.CAN_SKIP;

        // do not get our feet wet, "yes" is already included above
        if (isBlockFords() && (way.hasTag("highway", "ford") || way.hasTag("ford")))
            return EncodingManager.Access.CAN_SKIP;

        if (getConditionalTagInspector().isPermittedWayConditionallyRestricted(way))
            return EncodingManager.Access.CAN_SKIP;

        return EncodingManager.Access.WAY;
    }

    @Override
    public IntsRef handleWayTags(IntsRef edgeFlags, ReaderWay way, EncodingManager.Access access) {
        if (access.canSkip())
            return edgeFlags;

        Integer priorityFromRelation = routeMap.get(footRouteEnc.getEnum(false, edgeFlags));
        accessEnc.setBool(false, edgeFlags, true);
        accessEnc.setBool(true, edgeFlags, true);
        if (!access.isFerry()) {
            String sacScale = way.getTag("sac_scale");
            if (sacScale != null) {
                setSpeed(edgeFlags, true, true, "hiking".equals(sacScale) ? MEAN_SPEED : SLOW_SPEED);
            } else {
                setSpeed(edgeFlags, true, true, way.hasTag("highway", "steps") ? MEAN_SPEED - 2 : MEAN_SPEED);
            }
        } else {
            priorityFromRelation = PriorityCode.AVOID_IF_POSSIBLE.getValue();
            double ferrySpeed = getFerrySpeed(way);
            setSpeed(edgeFlags, true, true, ferrySpeed);
        }

        priorityWayEncoder.setDecimal(false, edgeFlags, PriorityCode.getFactor(handlePriority(way, priorityFromRelation)));
        return edgeFlags;
    }

    void setSpeed(IntsRef edgeFlags, boolean fwd, boolean bwd, double speed) {
        if (speed > getMaxSpeed())
            speed = getMaxSpeed();
        if (fwd)
            avgSpeedEnc.setDecimal(false, edgeFlags, speed);
        if (bwd && speedTwoDirections)
            avgSpeedEnc.setDecimal(true, edgeFlags, speed);
    }

    int handlePriority(ReaderWay way, Integer priorityFromRelation) {
        TreeMap<Double, Integer> weightToPrioMap = new TreeMap<>();
        if (priorityFromRelation == null)
            weightToPrioMap.put(0d, UNCHANGED.getValue());
        else
            weightToPrioMap.put(110d, priorityFromRelation);

        collect(way, weightToPrioMap);

        // pick priority with biggest order value
        return weightToPrioMap.lastEntry().getValue();
    }

    /**
     * @param weightToPrioMap associate a weight with every priority. This sorted map allows
     *                        subclasses to 'insert' more important priorities as well as overwrite determined priorities.
     */
    void collect(ReaderWay way, TreeMap<Double, Integer> weightToPrioMap) {
        String highway = way.getTag("highway");
        if (way.hasTag("foot", "designated"))
            weightToPrioMap.put(100d, PREFER.getValue());

        double maxSpeed = getMaxSpeed(way);
        if (safeHighwayTags.contains(highway) || maxSpeed > 0 && maxSpeed <= 20) {
            weightToPrioMap.put(40d, PREFER.getValue());
            if (way.hasTag("tunnel", intendedValues)) {
                if (way.hasTag("sidewalk", sidewalksNoValues))
                    weightToPrioMap.put(40d, AVOID_IF_POSSIBLE.getValue());
                else
                    weightToPrioMap.put(40d, UNCHANGED.getValue());
            }
        } else if (maxSpeed > 50 || avoidHighwayTags.contains(highway)) {
            if (!way.hasTag("sidewalk", sidewalkValues))
                weightToPrioMap.put(45d, AVOID_IF_POSSIBLE.getValue());
        }

        if (way.hasTag("bicycle", "official") || way.hasTag("bicycle", "designated"))
            weightToPrioMap.put(44d, AVOID_IF_POSSIBLE.getValue());
    }

    @Override
    public boolean supports(Class<?> feature) {
        if (super.supports(feature))
            return true;

        return PriorityWeighting.class.isAssignableFrom(feature);
    }

    /*
     * This method is a current hack, to allow ferries to be actually faster than our current storable maxSpeed.
     */
    @Override
    double getSpeed(boolean reverse, IntsRef edgeFlags) {
        double speed = super.getSpeed(reverse, edgeFlags);
        if (speed == getMaxSpeed()) {
            // We cannot be sure if it was a long or a short trip
            return SHORT_TRIP_FERRY_SPEED;
        }
        return speed;
    }

    @Override
    public String toString() {
        return "foot";
    }
}
