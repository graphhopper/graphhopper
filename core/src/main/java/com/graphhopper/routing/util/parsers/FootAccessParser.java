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
import com.graphhopper.routing.util.TransportationMode;
import com.graphhopper.routing.util.WayAccess;
import com.graphhopper.util.PMap;

import java.util.*;

import static com.graphhopper.routing.ev.RouteNetwork.*;
import static com.graphhopper.routing.util.PriorityCode.UNCHANGED;

public class FootAccessParser extends AbstractAccessParser implements TagParser {

    final Set<String> safeHighwayTags = new HashSet<>();
    final Set<String> allowedHighwayTags = new HashSet<>();
    final Set<String> avoidHighwayTags = new HashSet<>();
    final Set<String> allowedSacScale = new HashSet<>();
    protected HashSet<String> sidewalkValues = new HashSet<>(5);
    protected Map<RouteNetwork, Integer> routeMap = new HashMap<>();

    public FootAccessParser(EncodedValueLookup lookup, PMap properties) {
        this(lookup.getBooleanEncodedValue(VehicleAccess.key(properties.getString("name", "foot"))));
        blockPrivate(properties.getBool("block_private", true));
        blockFords(properties.getBool("block_fords", false));
    }

    protected FootAccessParser(BooleanEncodedValue accessEnc) {
        super(accessEnc, TransportationMode.FOOT);

        intendedValues.add("yes");
        intendedValues.add("designated");
        intendedValues.add("official");
        intendedValues.add("permissive");

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

        boolean permittedWayConditionallyRestricted = getConditionalTagInspector().isPermittedWayConditionallyRestricted(way);
        boolean restrictedWayConditionallyPermitted = getConditionalTagInspector().isRestrictedWayConditionallyPermitted(way);
        String firstValue = way.getFirstPriorityTag(restrictions);
        if (!firstValue.isEmpty()) {
            String[] restrict = firstValue.split(";");
            for (String value : restrict) {
                if (restrictedValues.contains(value) && !restrictedWayConditionallyPermitted)
                    return WayAccess.CAN_SKIP;
                if (intendedValues.contains(value) && !permittedWayConditionallyRestricted)
                    return WayAccess.WAY;
            }
        }

        if (way.hasTag("sidewalk", sidewalkValues))
            return WayAccess.WAY;

        if (!allowedHighwayTags.contains(highwayValue))
            return WayAccess.CAN_SKIP;

        if (way.hasTag("motorroad", "yes"))
            return WayAccess.CAN_SKIP;

        if (isBlockFords() && ("ford".equals(highwayValue) || way.hasTag("ford")))
            return WayAccess.CAN_SKIP;

        if (permittedWayConditionallyRestricted)
            return WayAccess.CAN_SKIP;

        return WayAccess.WAY;
    }

    @Override
    public void handleWayTags(int edgeId, IntAccess intAccess, ReaderWay way) {
        WayAccess access = getAccess(way);
        if (access.canSkip())
            return;

        if (way.hasTag("oneway:foot", oneways) || way.hasTag("foot:backward") || way.hasTag("foot:forward")
                || way.hasTag("oneway", oneways) && way.hasTag("highway", "steps") // outdated mapping style
        ) {
            boolean reverse = way.hasTag("oneway:foot", "-1") || way.hasTag("foot:backward", "yes") || way.hasTag("foot:forward", "no");
            accessEnc.setBool(reverse, edgeId, intAccess, true);
        } else {
            accessEnc.setBool(false, edgeId, intAccess, true);
            accessEnc.setBool(true, edgeId, intAccess, true);
        }

        if (way.hasTag("gh:barrier_edge")) {
            List<Map<String, Object>> nodeTags = way.getTag("node_tags", Collections.emptyList());
            handleBarrierEdge(edgeId, intAccess, nodeTags.get(0));
        }
    }
}
