package com.graphhopper.routing.util.parsers;

import com.graphhopper.reader.ReaderWay;
import com.graphhopper.routing.ev.DecimalEncodedValue;
import com.graphhopper.routing.ev.EncodedValueLookup;
import com.graphhopper.routing.ev.VehiclePriority;
import com.graphhopper.routing.util.PriorityCode;
import com.graphhopper.storage.IntsRef;
import com.graphhopper.util.PMap;

import java.util.HashSet;

public class MotorcyclePriorityParser implements TagParser {

    private final HashSet<String> avoidSet = new HashSet<>();
    private final HashSet<String> preferSet = new HashSet<>();
    private final DecimalEncodedValue priorityWayEncoder;

    public MotorcyclePriorityParser(EncodedValueLookup lookup, PMap properties) {
        this(lookup.getDecimalEncodedValue(VehiclePriority.key("motorcycle")));
    }

    public MotorcyclePriorityParser(DecimalEncodedValue priorityWayEncoder) {
        this.priorityWayEncoder = priorityWayEncoder;

        avoidSet.add("motorway");
        avoidSet.add("trunk");
        avoidSet.add("residential");

        preferSet.add("primary");
        preferSet.add("secondary");
        preferSet.add("tertiary");
    }

    @Override
    public IntsRef handleWayTags(IntsRef edgeFlags, ReaderWay way, IntsRef relationFlags) {
        priorityWayEncoder.setDecimal(false, edgeFlags, PriorityCode.getValue(handlePriority(way)));
        return edgeFlags;
    }

    private int handlePriority(ReaderWay way) {
        String highway = way.getTag("highway", "");
        if (avoidSet.contains(highway) || way.hasTag("motorroad", "yes")) {
            return PriorityCode.BAD.getValue();
        } else if (preferSet.contains(highway)) {
            return PriorityCode.BEST.getValue();
        }

        return PriorityCode.UNCHANGED.getValue();
    }
}
