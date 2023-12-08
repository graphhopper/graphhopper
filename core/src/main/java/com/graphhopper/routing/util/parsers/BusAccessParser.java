package com.graphhopper.routing.util.parsers;

import com.graphhopper.reader.ReaderWay;
import com.graphhopper.routing.ev.*;
import com.graphhopper.routing.util.FerrySpeedCalculator;
import com.graphhopper.routing.util.TransportationMode;
import com.graphhopper.storage.IntsRef;
import com.graphhopper.util.PMap;

import java.util.*;

public class BusAccessParser implements TagParser {

    private final List<String> restrictions = OSMRoadAccessParser.toOSMRestrictions(TransportationMode.BUS);
    private final List<String> restrictedValues = new ArrayList<>(5);
    private final BooleanEncodedValue accessEnc;
    private final BooleanEncodedValue roundaboutEnc;
    private final List<String> vehicleForward;
    private final List<String> vehicleBackward;
    private final Set<String> onewaysForward = new HashSet<>(Arrays.asList("yes", "true", "1"));

    public BusAccessParser(EncodedValueLookup lookup) {
        this(
                lookup.getBooleanEncodedValue(BusAccess.KEY),
                lookup.getBooleanEncodedValue(Roundabout.KEY)
        );
    }

    public BusAccessParser(BooleanEncodedValue accessEnc,
                           BooleanEncodedValue roundaboutEnc) {
        this.accessEnc = accessEnc;
        this.roundaboutEnc = roundaboutEnc;

        vehicleForward = restrictions.stream().map(r -> r + ":forward").toList();
        vehicleBackward = restrictions.stream().map(r -> r + ":backward").toList();

        restrictedValues.add("no");
        restrictedValues.add("restricted");
        restrictedValues.add("military");
        restrictedValues.add("emergency");
        restrictedValues.add("private");
        restrictedValues.add("permit");
    }

    @Override
    public void handleWayTags(int edgeId, EdgeIntAccess edgeIntAccess, ReaderWay way, IntsRef relationFlags) {
        String firstValue = way.getFirstPriorityTag(restrictions);
        if (FerrySpeedCalculator.isFerry(way)) {
            if (restrictedValues.contains(firstValue))
                return;
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
