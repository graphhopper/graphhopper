package com.graphhopper.routing.util.parsers;

import com.graphhopper.reader.ReaderWay;
import com.graphhopper.routing.ev.BooleanEncodedValue;
import com.graphhopper.routing.ev.EdgeIntAccess;
import com.graphhopper.routing.util.FerrySpeedCalculator;
import com.graphhopper.storage.IntsRef;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.graphhopper.routing.util.parsers.OSMTemporalAccessParser.hasPermissiveTemporalRestriction;

public class ModeAccessParser implements TagParser {

    private static final Set<String> CAR_BARRIERS = Set.of("kissing_gate", "fence",
            "bollard", "stile", "turnstile", "cycle_barrier", "motorcycle_barrier", "block",
            "bus_trap", "sump_buster", "jersey_barrier");
    private static final Set<String> INTENDED = Set.of("yes", "designated", "official", "permissive", "private", "permit");
    private static final Set<String> ONEWAYS_FW = Set.of("yes", "true", "1");
    private final Set<String> restrictedValues;
    private final List<String> restrictionKeys;
    private final List<String> vehicleForward;
    private final List<String> vehicleBackward;
    private final List<String> ignoreOnewayKeys;
    private final BooleanEncodedValue accessEnc;
    private final BooleanEncodedValue roundaboutEnc;
    private final boolean skipEmergency;
    private final Set<String> barriers;

    public ModeAccessParser(List<String> restrictionKeys, BooleanEncodedValue accessEnc,
                            boolean skipEmergency, BooleanEncodedValue roundaboutEnc,
                            Set<String> restrictions, Set<String> barriers) {
        this.accessEnc = accessEnc;
        this.roundaboutEnc = roundaboutEnc;
        this.restrictionKeys = restrictionKeys;
        vehicleForward = restrictionKeys.stream().map(r -> r + ":forward").toList();
        vehicleBackward = restrictionKeys.stream().map(r -> r + ":backward").toList();
        ignoreOnewayKeys = restrictionKeys.stream().map(r -> "oneway:" + r).toList();
        restrictedValues = restrictions.isEmpty() ? Set.of("no", "restricted", "military", "emergency") : restrictions;
        this.barriers = barriers.isEmpty() ? CAR_BARRIERS : barriers;
        if (restrictedValues.contains(""))
            throw new IllegalArgumentException("restriction values cannot contain empty string");
        this.skipEmergency = skipEmergency;
    }

    @Override
    public void handleWayTags(int edgeId, EdgeIntAccess edgeIntAccess, ReaderWay way, IntsRef relationFlags) {
        String highwayValue = way.getTag("highway");
        if (skipEmergency && "service".equals(highwayValue) && "emergency_access".equals(way.getTag("service")))
            return;

        int firstIndex = way.getFirstIndex(restrictionKeys);
        String firstValue = firstIndex < 0 ? "" : way.getTag(restrictionKeys.get(firstIndex), "");
        if (restrictedValues.contains(firstValue) && !hasPermissiveTemporalRestriction(way, firstIndex, restrictionKeys, INTENDED))
            return;

        if (way.hasTag("gh:barrier_edge") && way.hasTag("node_tags")) {
            List<Map<String, Object>> nodeTags = way.getTag("node_tags", null);
            Map<String, Object> firstNodeTags = nodeTags.get(0);
            // a barrier edge has the restriction in both nodes and the tags are the same -> get(0)
            firstValue = getFirstPriorityNodeTag(firstNodeTags, restrictionKeys);
            String barrierValue = firstNodeTags.containsKey("barrier") ? (String) firstNodeTags.get("barrier") : "";
            if (restrictedValues.contains(firstValue) || barriers.contains(barrierValue)
                    || "yes".equals(firstNodeTags.get("locked")) && !INTENDED.contains(firstValue))
                return;
        }

        if (FerrySpeedCalculator.isFerry(way)) {
            boolean isCar = restrictionKeys.contains("motorcar");
            if (INTENDED.contains(firstValue)
                    // implied default is allowed only if foot and bicycle is not specified:
                    || isCar && firstValue.isEmpty() && !way.hasTag("foot") && !way.hasTag("bicycle")
                    // if hgv is allowed then smaller trucks and cars are allowed too even if not specified
                    || isCar && way.hasTag("hgv", "yes")) {
                accessEnc.setBool(false, edgeId, edgeIntAccess, true);
                accessEnc.setBool(true, edgeId, edgeIntAccess, true);
            }
        } else {
            boolean isRoundabout = roundaboutEnc.getBool(false, edgeId, edgeIntAccess);
            boolean ignoreOneway = "no".equals(way.getFirstValue(ignoreOnewayKeys));
            boolean isBwd = isBackwardOneway(way);
            if (!ignoreOneway && (isBwd || isRoundabout || isForwardOneway(way))) {
                accessEnc.setBool(isBwd, edgeId, edgeIntAccess, true);
            } else {
                accessEnc.setBool(false, edgeId, edgeIntAccess, true);
                accessEnc.setBool(true, edgeId, edgeIntAccess, true);
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
        return way.hasTag("oneway", ONEWAYS_FW) || "no".equals(way.getFirstValue(vehicleBackward));
    }
}
