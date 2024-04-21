package com.graphhopper.routing.util.parsers;

import com.graphhopper.reader.ReaderWay;
import com.graphhopper.routing.ev.BooleanEncodedValue;
import com.graphhopper.routing.ev.EdgeIntAccess;
import com.graphhopper.routing.util.FerrySpeedCalculator;
import com.graphhopper.routing.util.TransportationMode;
import com.graphhopper.routing.util.WayAccess;

import java.util.*;

import static com.graphhopper.routing.util.parsers.OSMTemporalAccessParser.hasTemporalRestriction;

public abstract class BikeCommonAccessParser extends AbstractAccessParser implements TagParser {

    private static final Set<String> OPP_LANES = new HashSet<>(Arrays.asList("opposite", "opposite_lane", "opposite_track"));
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

            if (FerrySpeedCalculator.isFerry(way)) {
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
                if (way.hasTag(restrictionKeys, restrictedValues))
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
        if (way.hasTag("bicycle", "dismount") || (way.hasTag("highway", "cycleway") && !way.hasTag("access", "no")))
            return WayAccess.WAY;

        int firstIndex = way.getFirstIndex(restrictionKeys);
        if (firstIndex >= 0) {
            String firstValue = way.getTag(restrictionKeys.get(firstIndex), "");
            String[] restrict = firstValue.split(";");
            for (String value : restrict) {
                if (restrictedValues.contains(value) && !hasTemporalRestriction(way, firstIndex, restrictionKeys))
                    return WayAccess.CAN_SKIP;
                if (intendedValues.contains(value))
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

        return WayAccess.WAY;
    }

    boolean isSacScaleAllowed(String sacScale) {
        // other scales are nearly impossible by an ordinary bike, see http://wiki.openstreetmap.org/wiki/Key:sac_scale
        return "hiking".equals(sacScale);
    }

    @Override
    public void handleWayTags(int edgeId, EdgeIntAccess edgeIntAccess, ReaderWay way) {
        WayAccess access = getAccess(way);
        if (access.canSkip())
            return;

        if (access.isFerry()) {
            accessEnc.setBool(false, edgeId, edgeIntAccess, true);
            accessEnc.setBool(true, edgeId, edgeIntAccess, true);
        } else {
            handleAccess(edgeId, edgeIntAccess, way);
        }

        if (way.hasTag("gh:barrier_edge")) {
            List<Map<String, Object>> nodeTags = way.getTag("node_tags", Collections.emptyList());
            handleBarrierEdge(edgeId, edgeIntAccess, nodeTags.get(0));
        }
    }

    protected void handleAccess(int edgeId, EdgeIntAccess edgeIntAccess, ReaderWay way) {
        // handle oneways. The value -1 means it is a oneway but for reverse direction of stored geometry.
        // The tagging oneway:bicycle=no or cycleway:right:oneway=no or cycleway:left:oneway=no lifts the generic oneway restriction of the way for bike
        boolean isOneway = way.hasTag("oneway", ONEWAYS) && !way.hasTag("oneway", "-1") && !way.hasTag("bicycle:backward", INTENDED)
                || way.hasTag("oneway", "-1") && !way.hasTag("bicycle:forward", INTENDED)
                || way.hasTag("oneway:bicycle", ONEWAYS)
                || way.hasTag("cycleway:left:oneway", ONEWAYS)
                || way.hasTag("cycleway:right:oneway", ONEWAYS)
                || way.hasTag("vehicle:backward", restrictedValues) && !way.hasTag("bicycle:forward", INTENDED)
                || way.hasTag("vehicle:forward", restrictedValues) && !way.hasTag("bicycle:backward", INTENDED)
                || way.hasTag("bicycle:forward", restrictedValues)
                || way.hasTag("bicycle:backward", restrictedValues);

        if ((isOneway || roundaboutEnc.getBool(false, edgeId, edgeIntAccess))
                && !way.hasTag("oneway:bicycle", "no")
                && !(way.hasTag("cycleway:both") && !way.hasTag("cycleway:both", "no"))
                && !way.hasTag("cycleway", OPP_LANES)
                && !way.hasTag("cycleway:left", OPP_LANES)
                && !way.hasTag("cycleway:right", OPP_LANES)
                && !way.hasTag("cycleway:left:oneway", "no")
                && !way.hasTag("cycleway:right:oneway", "no")) {
            boolean isBackward = way.hasTag("oneway", "-1")
                    || way.hasTag("oneway:bicycle", "-1")
                    || way.hasTag("cycleway:left:oneway", "-1")
                    || way.hasTag("cycleway:right:oneway", "-1")
                    || way.hasTag("vehicle:forward", restrictedValues)
                    || way.hasTag("bicycle:forward", restrictedValues);
            accessEnc.setBool(isBackward, edgeId, edgeIntAccess, true);

        } else {
            accessEnc.setBool(true, edgeId, edgeIntAccess, true);
            accessEnc.setBool(false, edgeId, edgeIntAccess, true);
        }
    }
}
