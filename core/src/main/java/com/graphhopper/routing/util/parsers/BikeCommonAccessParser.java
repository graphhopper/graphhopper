package com.graphhopper.routing.util.parsers;

import com.graphhopper.reader.ReaderWay;
import com.graphhopper.routing.ev.BooleanEncodedValue;
import com.graphhopper.routing.util.TransportationMode;
import com.graphhopper.routing.util.WayAccess;
import com.graphhopper.storage.IntsRef;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static java.util.Collections.emptyMap;

public abstract class BikeCommonAccessParser extends AbstractAccessParser implements TagParser {

    private final Set<String> allowedHighways = new HashSet<>();

    protected BikeCommonAccessParser(BooleanEncodedValue accessEnc) {
        super(accessEnc, TransportationMode.BIKE);

        restrictedValues.addAll(Arrays.asList("agricultural", "forestry", "no", "restricted", "delivery", "military", "emergency", "private"));

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
        if (way.hasTag("bicycle", intendedValues) ||
                way.hasTag("bicycle", "dismount") ||
                way.hasTag("highway", "cycleway"))
            return WayAccess.WAY;

        // accept only if explicitly tagged for bike usage
        if ("motorway".equals(highwayValue) || "motorway_link".equals(highwayValue) || "bridleway".equals(highwayValue))
            return WayAccess.CAN_SKIP;

        if (way.hasTag("motorroad", "yes"))
            return WayAccess.CAN_SKIP;

        // do not use fords with normal bikes, flagged fords are in included above
        if (isBlockFords() && (way.hasTag("highway", "ford") || way.hasTag("ford")))
            return WayAccess.CAN_SKIP;

        // check access restrictions
        boolean notRestrictedWayConditionallyPermitted = !getConditionalTagInspector().isRestrictedWayConditionallyPermitted(way);
        for (String restriction : restrictions) {
            String complexAccess = way.getTag(restriction);
            if (complexAccess != null) {
                String[] simpleAccess = complexAccess.split(";");
                for (String access : simpleAccess) {
                    if (restrictedValues.contains(access) && notRestrictedWayConditionallyPermitted)
                        return WayAccess.CAN_SKIP;
                }
            }
        }

        if (getConditionalTagInspector().isPermittedWayConditionallyRestricted(way))
            return WayAccess.CAN_SKIP;
        else
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

        accessEnc.setBool(false, edgeFlags, true);
        accessEnc.setBool(true, edgeFlags, true);

        Map<String, Object> nodeTags = way.getTag("node_tags", emptyMap());
        handleNodeTags(edgeFlags, nodeTags);
    }
}
