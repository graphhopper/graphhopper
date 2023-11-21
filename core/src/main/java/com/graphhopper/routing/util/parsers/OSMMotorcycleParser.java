package com.graphhopper.routing.util.parsers;

import com.graphhopper.reader.ReaderWay;
import com.graphhopper.routing.ev.EdgeIntAccess;
import com.graphhopper.routing.ev.EnumEncodedValue;
import com.graphhopper.routing.ev.Motorcycle;
import com.graphhopper.routing.util.TransportationMode;
import com.graphhopper.storage.IntsRef;

import java.util.List;

public class OSMMotorcycleParser implements TagParser {

    private final List<String> restrictions = OSMRoadAccessParser.toOSMRestrictions(TransportationMode.MOTORCYCLE);
    private final EnumEncodedValue<Motorcycle> motorcycleEnc;

    public OSMMotorcycleParser(EnumEncodedValue<Motorcycle> motorcycleEnc) {
        this.motorcycleEnc = motorcycleEnc;
    }

    @Override
    public void handleWayTags(int edgeId, EdgeIntAccess edgeIntAccess, ReaderWay way, IntsRef relationFlags) {
        // node tags are currently unsupported as a minority in comparison
        // List<Map<String, Object>> nodeTags = way.getTag("node_tags", Collections.emptyList());

        String firstValue = way.getFirstPriorityTag(restrictions);
        if (!firstValue.isEmpty())
            motorcycleEnc.setEnum(false, edgeId, edgeIntAccess, Motorcycle.find(way.getTag("motorcycle")));
    }
}
