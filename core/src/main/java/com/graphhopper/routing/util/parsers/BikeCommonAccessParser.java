package com.graphhopper.routing.util.parsers;

import com.graphhopper.reader.ReaderWay;
import com.graphhopper.routing.ev.BooleanEncodedValue;
import com.graphhopper.routing.util.TransportationMode;
import com.graphhopper.routing.util.WayAccess;
import com.graphhopper.storage.IntsRef;

import java.util.*;

import static java.util.Collections.emptyMap;

public abstract class BikeCommonAccessParser extends AbstractAccessParser implements TagParser {

    protected final HashSet<String> oppositeLanes = new HashSet<>();
    private final Set<String> allowedHighways = new HashSet<>();
    private final BooleanEncodedValue roundaboutEnc;

    protected BikeCommonAccessParser(BooleanEncodedValue accessEnc, BooleanEncodedValue roundaboutEnc) {
        super(accessEnc, TransportationMode.BIKE);

        this.roundaboutEnc = roundaboutEnc;

        restrictedValues.add("agricultural");
        restrictedValues.add("forestry");
        restrictedValues.add("delivery");

        intendedValues.add("yes");
        intendedValues.add("designated");
        intendedValues.add("official");
        intendedValues.add("permissive");

        oppositeLanes.add("opposite");
        oppositeLanes.add("opposite_lane");
        oppositeLanes.add("opposite_track");

        barriers.add("fence");

        allowedHighways.addAll(Arrays.asList("living_street", "steps", "cycleway", "path", "footway", "platform",
                "pedestrian", "track", "service", "residential", "unclassified", "road", "bridleway",
                "motorway", "motorway_link", "trunk", "trunk_link",
                "primary", "primary_link", "secondary", "secondary_link", "tertiary", "tertiary_link"));
    }

    public WayAccess getAccess(ReaderWay way) {
        String highwayValue = way.getTag("highway");
        if (highwayValue == null) {
            WayAccess access = WayAccess.CAN_SKIP;

            if (way.hasTag("route", ferries)) {
                // if bike is NOT explicitly tagged allow bike but only if foot is not specified either
                String bikeTag = way.getTag("bicycle");
                if (bikeTag == null && !way.hasTag("foot") || intendedValues.contains(bikeTag))
                    access = WayAccess.FERRY;
            }

            // special case not for all acceptedRailways, only platform
            if (way.hasTag("railway", "platform"))
                access = WayAccess.WAY;

            if (way.hasTag("man_made", "pier"))
                access = WayAccess.WAY;

            if (!access.canSkip()) {
                if (way.hasTag(restrictions, restrictedValues) && !getConditionalTagInspector().isRestrictedWayConditionallyPermitted(way))
                    return WayAccess.CAN_SKIP;
                return access;
            }

            return WayAccess.CAN_SKIP;
        }

        if (!allowedHighways.contains(highwayValue))
            return WayAccess.CAN_SKIP;

        String sacScale = way.getTag("sac_scale");
        if (sacScale != null) {
            if (!isSacScaleAllowed(sacScale))
                return WayAccess.CAN_SKIP;
        }

        // use the way if it is tagged for bikes
        if (way.hasTag("bicycle", "dismount") || way.hasTag("highway", "cycleway"))
            return WayAccess.WAY;

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

        // accept only if explicitly tagged for bike usage
        if ("motorway".equals(highwayValue) || "motorway_link".equals(highwayValue) || "bridleway".equals(highwayValue))
            return WayAccess.CAN_SKIP;

        if (way.hasTag("motorroad", "yes"))
            return WayAccess.CAN_SKIP;

        if (isBlockFords() && ("ford".equals(highwayValue) || way.hasTag("ford")))
            return WayAccess.CAN_SKIP;

        if (permittedWayConditionallyRestricted)
            return WayAccess.CAN_SKIP;

        return WayAccess.WAY;
    }

    boolean isSacScaleAllowed(String sacScale) {
        // other scales are nearly impossible by an ordinary bike, see http://wiki.openstreetmap.org/wiki/Key:sac_scale
        return "hiking".equals(sacScale);
    }

    @Override
    public void handleWayTags(IntsRef edgeFlags, ReaderWay way) {
        WayAccess access = getAccess(way);
        if (access.canSkip())
            return;

        if (access.isFerry()) {
            accessEnc.setBool(false, edgeFlags, true);
            accessEnc.setBool(true, edgeFlags, true);
        } else {
            handleAccess(edgeFlags, way);
        }

        List<Map<String, Object>> nodeTags = way.getTag("node_tags", Collections.emptyList());
        handleNodeTags(edgeFlags, nodeTags);
    }

    protected void handleAccess(IntsRef edgeFlags, ReaderWay way) {
        // handle oneways. The value -1 means it is a oneway but for reverse direction of stored geometry.
        // The tagging oneway:bicycle=no or cycleway:right:oneway=no or cycleway:left:oneway=no lifts the generic oneway restriction of the way for bike
        boolean isOneway = way.hasTag("oneway", oneways) && !way.hasTag("oneway", "-1") && !way.hasTag("bicycle:backward", intendedValues)
                || way.hasTag("oneway", "-1") && !way.hasTag("bicycle:forward", intendedValues)
                || way.hasTag("oneway:bicycle", oneways)
                || way.hasTag("cycleway:left:oneway", oneways)
                || way.hasTag("cycleway:right:oneway", oneways)
                || way.hasTag("vehicle:backward", restrictedValues) && !way.hasTag("bicycle:forward", intendedValues)
                || way.hasTag("vehicle:forward", restrictedValues) && !way.hasTag("bicycle:backward", intendedValues)
                || way.hasTag("bicycle:forward", restrictedValues)
                || way.hasTag("bicycle:backward", restrictedValues);

        if ((isOneway || roundaboutEnc.getBool(false, edgeFlags))
                && !way.hasTag("oneway:bicycle", "no")
                && !way.hasTag("cycleway", oppositeLanes)
                && !way.hasTag("cycleway:left", oppositeLanes)
                && !way.hasTag("cycleway:right", oppositeLanes)
                && !way.hasTag("cycleway:left:oneway", "no")
                && !way.hasTag("cycleway:right:oneway", "no")) {
            boolean isBackward = way.hasTag("oneway", "-1")
                    || way.hasTag("oneway:bicycle", "-1")
                    || way.hasTag("cycleway:left:oneway", "-1")
                    || way.hasTag("cycleway:right:oneway", "-1")
                    || way.hasTag("vehicle:forward", restrictedValues)
                    || way.hasTag("bicycle:forward", restrictedValues);
            accessEnc.setBool(isBackward, edgeFlags, true);

        } else {
            accessEnc.setBool(false, edgeFlags, true);
            accessEnc.setBool(true, edgeFlags, true);
        }
    }
}
