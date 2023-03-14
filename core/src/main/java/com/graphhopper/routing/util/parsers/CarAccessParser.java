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
package com.graphhopper.routing.util.parsers;

import com.graphhopper.reader.ReaderWay;
import com.graphhopper.routing.ev.BooleanEncodedValue;
import com.graphhopper.routing.ev.EncodedValueLookup;
import com.graphhopper.routing.ev.Roundabout;
import com.graphhopper.routing.ev.VehicleAccess;
import com.graphhopper.routing.util.TransportationMode;
import com.graphhopper.routing.util.WayAccess;
import com.graphhopper.storage.IntsRef;
import com.graphhopper.util.PMap;

import java.util.*;

public class CarAccessParser extends AbstractAccessParser implements TagParser {

    protected final Set<String> trackTypeValues = new HashSet<>();
    protected final Set<String> highwayValues = new HashSet<>();
    protected final BooleanEncodedValue roundaboutEnc;

    public CarAccessParser(EncodedValueLookup lookup, PMap properties) {
        this(
                lookup.getBooleanEncodedValue(VehicleAccess.key(properties.getString("name", "car"))),
                lookup.getBooleanEncodedValue(Roundabout.KEY),
                properties,
                TransportationMode.CAR
        );
    }

    public CarAccessParser(BooleanEncodedValue accessEnc,
                           BooleanEncodedValue roundaboutEnc, PMap properties,
                           TransportationMode transportationMode) {
        super(accessEnc, transportationMode);
        this.roundaboutEnc = roundaboutEnc;
        restrictedValues.add("agricultural");
        restrictedValues.add("forestry");
        restrictedValues.add("delivery");

        blockPrivate(properties.getBool("block_private", true));
        blockFords(properties.getBool("block_fords", false));

        intendedValues.add("yes");
        intendedValues.add("designated");
        intendedValues.add("permissive");

        barriers.add("kissing_gate");
        barriers.add("fence");
        barriers.add("bollard");
        barriers.add("stile");
        barriers.add("turnstile");
        barriers.add("cycle_barrier");
        barriers.add("motorcycle_barrier");
        barriers.add("block");
        barriers.add("bus_trap");
        barriers.add("sump_buster");
        barriers.add("jersey_barrier");

        highwayValues.addAll(Arrays.asList("motorway", "motorway_link", "trunk", "trunk_link",
                "primary", "primary_link", "secondary", "secondary_link", "tertiary", "tertiary_link",
                "unclassified", "residential", "living_street", "service", "road", "track"));

        trackTypeValues.addAll(Arrays.asList("grade1", "grade2", "grade3", null));
    }

    public WayAccess getAccess(ReaderWay way) {
        // TODO: Ferries have conditionals, like opening hours or are closed during some time in the year
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

        if ("track".equals(highwayValue) && !trackTypeValues.contains(way.getTag("tracktype")))
            return WayAccess.CAN_SKIP;

        if (!highwayValues.contains(highwayValue))
            return WayAccess.CAN_SKIP;

        if (way.hasTag("impassable", "yes") || way.hasTag("status", "impassable"))
            return WayAccess.CAN_SKIP;

        // multiple restrictions needs special handling, see also motorcycle
        boolean permittedWayConditionallyRestricted = getConditionalTagInspector().isPermittedWayConditionallyRestricted(way);
        boolean restrictedWayConditionallyPermitted = getConditionalTagInspector().isRestrictedWayConditionallyPermitted(way);
        if (!firstValue.isEmpty()) {
            String[] restrict = firstValue.split(";");
            for (String value : restrict) {
                if (restrictedValues.contains(value) && !restrictedWayConditionallyPermitted)
                    return WayAccess.CAN_SKIP;
                if (intendedValues.contains(value) && !permittedWayConditionallyRestricted)
                    return WayAccess.WAY;
            }
        }

        if (isBlockFords() && ("ford".equals(highwayValue) || way.hasTag("ford")))
            return WayAccess.CAN_SKIP;

        if (permittedWayConditionallyRestricted)
            return WayAccess.CAN_SKIP;

        return WayAccess.WAY;
    }

    @Override
    public void handleWayTags(IntsRef edgeFlags, ReaderWay way) {
        WayAccess access = getAccess(way);
        if (access.canSkip())
            return;

        if (!access.isFerry()) {
            boolean isRoundabout = roundaboutEnc.getBool(false, edgeFlags);
            if (isOneway(way) || isRoundabout) {
                if (isForwardOneway(way))
                    accessEnc.setBool(false, edgeFlags, true);
                if (isBackwardOneway(way))
                    accessEnc.setBool(true, edgeFlags, true);
            } else {
                accessEnc.setBool(false, edgeFlags, true);
                accessEnc.setBool(true, edgeFlags, true);
            }

        } else {
            accessEnc.setBool(false, edgeFlags, true);
            accessEnc.setBool(true, edgeFlags, true);
        }

        if (way.hasTag("gh:barrier_edge")) {
            List<Map<String, Object>> nodeTags = way.getTag("node_tags", Collections.emptyList());
            handleNodeTags(edgeFlags, nodeTags.get(0));
        }
    }

    /**
     * make sure that isOneway is called before
     */
    protected boolean isBackwardOneway(ReaderWay way) {
        return way.hasTag("oneway", "-1")
                || way.hasTag("vehicle:forward", restrictedValues)
                || way.hasTag("motor_vehicle:forward", restrictedValues);
    }

    /**
     * make sure that isOneway is called before
     */
    protected boolean isForwardOneway(ReaderWay way) {
        return !way.hasTag("oneway", "-1")
                && !way.hasTag("vehicle:forward", restrictedValues)
                && !way.hasTag("motor_vehicle:forward", restrictedValues);
    }

    protected boolean isOneway(ReaderWay way) {
        return way.hasTag("oneway", oneways)
                || way.hasTag("vehicle:backward", restrictedValues)
                || way.hasTag("vehicle:forward", restrictedValues)
                || way.hasTag("motor_vehicle:backward", restrictedValues)
                || way.hasTag("motor_vehicle:forward", restrictedValues);
    }
}
