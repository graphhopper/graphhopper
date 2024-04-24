package com.graphhopper.routing.util.parsers;

import com.graphhopper.reader.ReaderWay;
import com.graphhopper.routing.ev.BooleanEncodedValue;
import com.graphhopper.routing.ev.EdgeBytesAccess;
import com.graphhopper.routing.util.FerrySpeedCalculator;
import com.graphhopper.routing.util.TransportationMode;
import com.graphhopper.storage.BytesRef;

import java.util.*;

import static com.graphhopper.routing.util.parsers.OSMTemporalAccessParser.hasTemporalRestriction;

public class ModeAccessParser implements TagParser {

    private final static Set<String> onewaysForward = new HashSet<>(Arrays.asList("yes", "true", "1"));
    private final Set<String> restrictedValues;
    private final List<String> restrictionKeys;
    private final List<String> vehicleForward;
    private final List<String> vehicleBackward;
    private final List<String> ignoreOnewayKeys;
    private final BooleanEncodedValue accessEnc;
    private final BooleanEncodedValue roundaboutEnc;

    public ModeAccessParser(TransportationMode mode, BooleanEncodedValue accessEnc, BooleanEncodedValue roundaboutEnc, List<String> restrictions) {
        this.accessEnc = accessEnc;
        this.roundaboutEnc = roundaboutEnc;
        restrictionKeys = OSMRoadAccessParser.toOSMRestrictions(mode);
        vehicleForward = restrictionKeys.stream().map(r -> r + ":forward").toList();
        vehicleBackward = restrictionKeys.stream().map(r -> r + ":backward").toList();
        ignoreOnewayKeys = restrictionKeys.stream().map(r -> "oneway:" + r).toList();
        restrictedValues = new HashSet<>(restrictions.isEmpty() ? Arrays.asList("no", "restricted", "military", "emergency") : restrictions);
        if (restrictedValues.contains(""))
            throw new IllegalArgumentException("restriction values cannot contain empty string");
    }

    @Override
    public void handleWayTags(int edgeId, EdgeBytesAccess edgeAccess, ReaderWay way, BytesRef relationFlags) {
        int firstIndex = way.getFirstIndex(restrictionKeys);
        String firstValue = firstIndex < 0 ? "" : way.getTag(restrictionKeys.get(firstIndex), "");
        if (restrictedValues.contains(firstValue) && !hasTemporalRestriction(way, firstIndex, restrictionKeys))
            return;

        if (way.hasTag("gh:barrier_edge") && way.hasTag("node_tags")) {
            List<Map<String, Object>> nodeTags = way.getTag("node_tags", null);
            // a barrier edge has the restriction in both nodes and the tags are the same -> get(0)
            firstValue = getFirstPriorityNodeTag(nodeTags.get(0), restrictionKeys);
            if (restrictedValues.contains(firstValue))
                return;
        }

        if (FerrySpeedCalculator.isFerry(way)) {
            accessEnc.setBool(false, edgeId, edgeAccess, true);
            accessEnc.setBool(true, edgeId, edgeAccess, true);
        } else {
            boolean isRoundabout = roundaboutEnc.getBool(false, edgeId, edgeAccess);
            boolean ignoreOneway = "no".equals(way.getFirstValue(ignoreOnewayKeys));
            boolean isBwd = isBackwardOneway(way);
            if (!ignoreOneway && (isBwd || isRoundabout || isForwardOneway(way))) {
                accessEnc.setBool(isBwd, edgeId, edgeAccess, true);
            } else {
                accessEnc.setBool(false, edgeId, edgeAccess, true);
                accessEnc.setBool(true, edgeId, edgeAccess, true);
            }
        }
    }

    private static String getFirstPriorityNodeTag(Map<String, Object> nodeTags, List<String> restrictionKeys) {
        for (String key : restrictionKeys) {
            String val = (String) nodeTags.get(key);
            if (val != null) return val;
        }
        return "";
    }

    protected boolean isBackwardOneway(ReaderWay way) {
        // vehicle:forward=no is like oneway=-1
        return way.hasTag("oneway", "-1") || "no".equals(way.getFirstValue(vehicleForward));
    }

    protected boolean isForwardOneway(ReaderWay way) {
        // vehicle:backward=no is like oneway=yes
        return way.hasTag("oneway", onewaysForward) || "no".equals(way.getFirstValue(vehicleBackward));
    }
}
