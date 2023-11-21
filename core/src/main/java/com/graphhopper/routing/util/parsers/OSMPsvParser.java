package com.graphhopper.routing.util.parsers;

import com.graphhopper.reader.ReaderWay;
import com.graphhopper.routing.ev.Bus;
import com.graphhopper.routing.ev.EdgeIntAccess;
import com.graphhopper.routing.ev.EnumEncodedValue;
import com.graphhopper.routing.ev.Psv;
import com.graphhopper.routing.util.TransportationMode;
import com.graphhopper.storage.IntsRef;

import java.util.Collections;
import java.util.List;
import java.util.Map;

public class OSMPsvParser implements TagParser {

    private final List<String> restrictions = OSMRoadAccessParser.toOSMRestrictions(TransportationMode.PSV);
    private final EnumEncodedValue<Psv> busEnc;

    public OSMPsvParser(EnumEncodedValue<Psv> busEnc) {
        this.busEnc = busEnc;
    }

    @Override
    public void handleWayTags(int edgeId, EdgeIntAccess edgeIntAccess, ReaderWay way, IntsRef intsRef) {
        // node tags are currently unsupported as a minority in comparison
        // List<Map<String, Object>> nodeTags = way.getTag("node_tags", Collections.emptyList());

        String firstValue = way.getFirstPriorityTag(restrictions);
        if (!firstValue.isEmpty())
            busEnc.setEnum(false, edgeId, edgeIntAccess, Psv.find(way.getTag("psv")));
    }
}
