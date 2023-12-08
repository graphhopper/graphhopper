package com.graphhopper.routing.util.parsers;

import com.graphhopper.reader.ReaderWay;
import com.graphhopper.routing.ev.BooleanEncodedValue;
import com.graphhopper.routing.ev.EdgeIntAccess;
import com.graphhopper.routing.util.FerrySpeedCalculator;
import com.graphhopper.routing.util.TransportationMode;
import com.graphhopper.storage.IntsRef;

import java.util.*;

public class ModeAccessParser implements TagParser {

    private final List<String> restrictionKeys;
    private final List<String> restrictedValues = new ArrayList<>();
    private final List<String> intendedValues = new ArrayList<>();
    private final BooleanEncodedValue accessEnc;
    private final BooleanEncodedValue roundaboutEnc;
    private final List<String> vehicleForward;
    private final List<String> vehicleBackward;
    private final Set<String> onewaysForward = new HashSet<>(Arrays.asList("yes", "true", "1"));

    public ModeAccessParser(TransportationMode mode, BooleanEncodedValue accessEnc, BooleanEncodedValue roundaboutEnc) {
        this.accessEnc = accessEnc;
        this.roundaboutEnc = roundaboutEnc;
        restrictionKeys = OSMRoadAccessParser.toOSMRestrictions(mode);

        vehicleForward = restrictionKeys.stream().map(r -> r + ":forward").toList();
        vehicleBackward = restrictionKeys.stream().map(r -> r + ":backward").toList();

        restrictedValues.add("no");
        restrictedValues.add("restricted");
        restrictedValues.add("military");
        restrictedValues.add("emergency");
        restrictedValues.add("private");
        restrictedValues.add("permit");

        intendedValues.add("yes");
        intendedValues.add("designated");
        intendedValues.add("permissive");
    }

    @Override
    public void handleWayTags(int edgeId, EdgeIntAccess edgeIntAccess, ReaderWay way, IntsRef relationFlags) {
        List<Map<String, Object>> nodeTags = way.getTag("node_tags", Collections.emptyList());
        // a barrier edge has the restriction in both nodes and the tags are the same
        if (way.hasTag("gh:barrier_edge")) {
            for (String restriction : restrictionKeys) {
                String firstValue = (String) nodeTags.get(0).get(restriction);
                if (restrictedValues.contains(firstValue))
                    return;
                if (intendedValues.contains(firstValue)) {
                    accessEnc.setBool(false, edgeId, edgeIntAccess, true);
                    accessEnc.setBool(true, edgeId, edgeIntAccess, true);
                }
            }
        }

        String firstValue = way.getFirstPriorityTag(restrictionKeys);
        if (restrictedValues.contains(firstValue))
            return;
        if (FerrySpeedCalculator.isFerry(way)) {
            accessEnc.setBool(false, edgeId, edgeIntAccess, true);
            accessEnc.setBool(true, edgeId, edgeIntAccess, true);
        } else {
            boolean isRoundabout = roundaboutEnc.getBool(false, edgeId, edgeIntAccess);
            boolean isBwd = isBackwardOneway(way);
            if (isBwd || isRoundabout || isForwardOneway(way)) {
                accessEnc.setBool(isBwd, edgeId, edgeIntAccess, true);
            } else {
                accessEnc.setBool(false, edgeId, edgeIntAccess, true);
                accessEnc.setBool(true, edgeId, edgeIntAccess, true);
            }
        }
    }

    protected boolean isBackwardOneway(ReaderWay way) {
        return way.hasTag("oneway", "-1") || vehicleForward.stream().anyMatch(s -> way.hasTag(s, restrictedValues));
    }

    protected boolean isForwardOneway(ReaderWay way) {
        return way.hasTag("oneway", onewaysForward) || vehicleBackward.stream().anyMatch(s -> way.hasTag(s, restrictedValues));
    }
}
