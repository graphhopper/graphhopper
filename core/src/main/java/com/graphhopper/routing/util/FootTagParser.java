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
import com.graphhopper.storage.IntsRef;
import com.graphhopper.util.PMap;

import java.util.*;

import static com.graphhopper.routing.ev.RouteNetwork.*;
import static com.graphhopper.routing.util.PriorityCode.*;

/**
 * Defines bit layout for pedestrians (speed, access, surface, ...). Here we put a penalty on unsafe
 * roads only. If you wish to also prefer routes due to beauty like hiking routes use the
 * HikeTagParser instead.
 * <p>
 *
 * @author Peter Karich
 * @author Nop
 * @author Karl Hübner
 */
public class FootTagParser extends VehicleTagParser {
    static final int SLOW_SPEED = 2;
    static final int MEAN_SPEED = 5;
    // larger value required - ferries are faster than pedestrians
    static final int FERRY_SPEED = 15;
    final Set<String> safeHighwayTags = new HashSet<>();
    final Set<String> allowedHighwayTags = new HashSet<>();
    final Set<String> avoidHighwayTags = new HashSet<>();
    final Set<String> allowedSacScale = new HashSet<>();
    protected HashSet<String> sidewalkValues = new HashSet<>(5);
    protected HashSet<String> sidewalksNoValues = new HashSet<>(5);
    protected final DecimalEncodedValue priorityWayEncoder;
    protected EnumEncodedValue<RouteNetwork> footRouteEnc;
    protected Map<RouteNetwork, Integer> routeMap = new HashMap<>();

    public FootTagParser(EncodedValueLookup lookup, PMap properties) {
        this(
                lookup.getBooleanEncodedValue(VehicleAccess.key(properties.getString("name", "foot"))),
                lookup.getDecimalEncodedValue(VehicleSpeed.key(properties.getString("name", "foot"))),
                lookup.getDecimalEncodedValue(VehiclePriority.key(properties.getString("name", "foot"))),
                lookup.getEnumEncodedValue(FootNetwork.KEY, RouteNetwork.class)
        );
        blockPrivate(properties.getBool("block_private", true));
        blockFords(properties.getBool("block_fords", false));
    }

    protected FootTagParser(BooleanEncodedValue accessEnc, DecimalEncodedValue speedEnc, DecimalEncodedValue priorityEnc,
                            EnumEncodedValue<RouteNetwork> footRouteEnc) {
        super(accessEnc, speedEnc, null, TransportationMode.FOOT, speedEnc.getNextStorableValue(FERRY_SPEED));
        this.footRouteEnc = footRouteEnc;
        priorityWayEncoder = priorityEnc;

        restrictedValues.add("no");
        restrictedValues.add("restricted");
        restrictedValues.add("military");
        restrictedValues.add("emergency");
        restrictedValues.add("private");

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

        barriers.add("fence");

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
    }

    /**
     * Some ways are okay but not separate for pedestrians.
     */
    public WayAccess getAccess(ReaderWay way) {
        String highwayValue = way.getTag("highway");
        if (highwayValue == null) {
            WayAccess acceptPotentially = WayAccess.CAN_SKIP;

            if (way.hasTag("route", ferries)) {
                String footTag = way.getTag("foot");
                if (footTag == null || intendedValues.contains(footTag))
                    acceptPotentially = WayAccess.FERRY;
            }

            // special case not for all acceptedRailways, only platform
            if (way.hasTag("railway", "platform"))
                acceptPotentially = WayAccess.WAY;

            if (way.hasTag("man_made", "pier"))
                acceptPotentially = WayAccess.WAY;

            if (!acceptPotentially.canSkip()) {
                if (way.hasTag(restrictions, restrictedValues) && !getConditionalTagInspector().isRestrictedWayConditionallyPermitted(way))
                    return WayAccess.CAN_SKIP;
                return acceptPotentially;
            }

            return WayAccess.CAN_SKIP;
        }

        // other scales are too dangerous, see http://wiki.openstreetmap.org/wiki/Key:sac_scale
        if (way.getTag("sac_scale") != null && !way.hasTag("sac_scale", allowedSacScale))
            return WayAccess.CAN_SKIP;

        // no need to evaluate ferries or fords - already included here
        if (way.hasTag("foot", intendedValues))
            return WayAccess.WAY;

        // check access restrictions
        if (way.hasTag(restrictions, restrictedValues) && !getConditionalTagInspector().isRestrictedWayConditionallyPermitted(way))
            return WayAccess.CAN_SKIP;

        if (way.hasTag("sidewalk", sidewalkValues))
            return WayAccess.WAY;

        if (!allowedHighwayTags.contains(highwayValue))
            return WayAccess.CAN_SKIP;

        if (way.hasTag("motorroad", "yes"))
            return WayAccess.CAN_SKIP;

        // do not get our feet wet, "yes" is already included above
        if (isBlockFords() && (way.hasTag("highway", "ford") || way.hasTag("ford")))
            return WayAccess.CAN_SKIP;

        if (getConditionalTagInspector().isPermittedWayConditionallyRestricted(way))
            return WayAccess.CAN_SKIP;

        return WayAccess.WAY;
    }

    @Override
    public void handleWayTags(IntsRef edgeFlags, ReaderWay way) {
        WayAccess access = getAccess(way);
        if (access.canSkip())
            return;

        Integer priorityFromRelation = routeMap.get(footRouteEnc.getEnum(false, edgeFlags));
        if (way.hasTag("oneway:foot", oneways) || way.hasTag("foot:backward") || way.hasTag("foot:forward")
                || way.hasTag("oneway", oneways) && way.hasTag("highway", "steps") // outdated mapping style
        ) {
            boolean reverse = way.hasTag("oneway:foot", "-1") || way.hasTag("foot:backward", "yes") || way.hasTag("foot:forward", "no");
            accessEnc.setBool(reverse, edgeFlags, true);
        } else {
            accessEnc.setBool(false, edgeFlags, true);
            accessEnc.setBool(true, edgeFlags, true);
        }

        if (!access.isFerry()) {
            String sacScale = way.getTag("sac_scale");
            if (sacScale != null) {
                setSpeed(edgeFlags, true, true, "hiking".equals(sacScale) ? MEAN_SPEED : SLOW_SPEED);
            } else {
                setSpeed(edgeFlags, true, true, way.hasTag("highway", "steps") ? MEAN_SPEED - 2 : MEAN_SPEED);
            }
        } else {
            priorityFromRelation = PriorityCode.SLIGHT_AVOID.getValue();
            double ferrySpeed = ferrySpeedCalc.getSpeed(way);
            setSpeed(edgeFlags, true, true, ferrySpeed);
        }

        priorityWayEncoder.setDecimal(false, edgeFlags, PriorityCode.getValue(handlePriority(way, priorityFromRelation)));
    }

    void setSpeed(IntsRef edgeFlags, boolean fwd, boolean bwd, double speed) {
        if (speed > getMaxSpeed())
            speed = getMaxSpeed();
        if (fwd)
            avgSpeedEnc.setDecimal(false, edgeFlags, speed);
        if (bwd && avgSpeedEnc.isStoreTwoDirections())
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

        double maxSpeed = Math.max(getMaxSpeed(way, false), getMaxSpeed(way, true));
        if (safeHighwayTags.contains(highway) || (isValidSpeed(maxSpeed) && maxSpeed <= 20)) {
            weightToPrioMap.put(40d, PREFER.getValue());
            if (way.hasTag("tunnel", intendedValues)) {
                if (way.hasTag("sidewalk", sidewalksNoValues))
                    weightToPrioMap.put(40d, SLIGHT_AVOID.getValue());
                else
                    weightToPrioMap.put(40d, UNCHANGED.getValue());
            }
        } else if ((isValidSpeed(maxSpeed) && maxSpeed > 50) || avoidHighwayTags.contains(highway)) {
            if (!way.hasTag("sidewalk", sidewalkValues))
                weightToPrioMap.put(45d, AVOID.getValue());
        }

        if (way.hasTag("bicycle", "official") || way.hasTag("bicycle", "designated"))
            weightToPrioMap.put(44d, SLIGHT_AVOID.getValue());
    }
}
