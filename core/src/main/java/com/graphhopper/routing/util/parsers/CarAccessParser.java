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
import com.graphhopper.routing.ev.*;
import com.graphhopper.routing.util.FerrySpeedCalculator;
import com.graphhopper.routing.util.TransportationMode;
import com.graphhopper.routing.util.WayAccess;
import com.graphhopper.util.PMap;

import java.util.*;

public class CarAccessParser extends AbstractAccessParser implements TagParser {

    protected final Set<String> trackTypeValues = new HashSet<>();
    protected final Set<String> highwayValues = new HashSet<>();
    protected final BooleanEncodedValue roundaboutEnc;
    protected final List<String> vehicleForward;
    protected final List<String> vehicleBackward;
    private final Set<String> onewaysForward = new HashSet<>(Arrays.asList("yes", "true", "1"));

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

        vehicleForward = restrictions.stream().map(r -> r + ":forward").toList();
        vehicleBackward = restrictions.stream().map(r -> r + ":backward").toList();

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
            if (FerrySpeedCalculator.isFerry(way)) {
                if (restrictedValues.contains(firstValue))
                    return WayAccess.CAN_SKIP;
                if (intendedValues.contains(firstValue) ||
                        // implied default is allowed only if foot and bicycle is not specified:
                        firstValue.isEmpty() && !way.hasTag("foot") && !way.hasTag("bicycle") ||
                        // if hgv is allowed than smaller trucks and cars are allowed too
                        way.hasTag("hgv", "yes"))
                    return WayAccess.FERRY;
            }
            return WayAccess.CAN_SKIP;
        }

        if ("service".equals(highwayValue) && "emergency_access".equals(way.getTag("service")))
            return WayAccess.CAN_SKIP;

        if ("track".equals(highwayValue) && !trackTypeValues.contains(way.getTag("tracktype")))
            return WayAccess.CAN_SKIP;

        if (!highwayValues.contains(highwayValue))
            return WayAccess.CAN_SKIP;

        if (way.hasTag("impassable", "yes") || way.hasTag("status", "impassable"))
            return WayAccess.CAN_SKIP;

        // multiple restrictions needs special handling
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
    public void handleWayTags(int edgeId, EdgeIntAccess edgeIntAccess, ReaderWay way) {
        WayAccess access = getAccess(way);
        if (access.canSkip())
            return;

        if (!access.isFerry()) {
            boolean isRoundabout = roundaboutEnc.getBool(false, edgeId, edgeIntAccess);
            boolean isBwd = isBackwardOneway(way);
            if (isBwd || isRoundabout || isForwardOneway(way)) {
                accessEnc.setBool(isBwd, edgeId, edgeIntAccess, true);
            } else {
                accessEnc.setBool(false, edgeId, edgeIntAccess, true);
                accessEnc.setBool(true, edgeId, edgeIntAccess, true);
            }

        } else {
            accessEnc.setBool(false, edgeId, edgeIntAccess, true);
            accessEnc.setBool(true, edgeId, edgeIntAccess, true);
        }

        if (way.hasTag("gh:barrier_edge")) {
            List<Map<String, Object>> nodeTags = way.getTag("node_tags", Collections.emptyList());
            handleBarrierEdge(edgeId, edgeIntAccess, nodeTags.get(0));
        }
    }

    protected boolean isBackwardOneway(ReaderWay way) {
        return way.hasTag("oneway", "-1") || vehicleForward.stream().anyMatch(s -> way.hasTag(s, restrictedValues));
    }

    protected boolean isForwardOneway(ReaderWay way) {
        return way.hasTag("oneway", onewaysForward) || vehicleBackward.stream().anyMatch(s -> way.hasTag(s, restrictedValues));
    }
}
