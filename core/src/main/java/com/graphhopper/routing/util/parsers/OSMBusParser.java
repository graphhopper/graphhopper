package com.graphhopper.routing.util.parsers;

import com.graphhopper.reader.ReaderWay;
import com.graphhopper.routing.ev.Bus;
import com.graphhopper.routing.ev.EdgeIntAccess;
import com.graphhopper.routing.ev.EnumEncodedValue;
import com.graphhopper.routing.util.TransportationMode;
import com.graphhopper.storage.IntsRef;

import java.util.Collections;
import java.util.List;
import java.util.Map;

public class OSMBusParser implements TagParser {

    private final List<String> restrictions = OSMRoadAccessParser.toOSMRestrictions(TransportationMode.BUS);
    private final EnumEncodedValue<Bus> busEnc;

    public OSMBusParser(EnumEncodedValue<Bus> busEnc) {
        this.busEnc = busEnc;
    }

    @Override
    public void handleWayTags(int edgeId, EdgeIntAccess edgeIntAccess, ReaderWay way, IntsRef intsRef) {
        List<Map<String, Object>> nodeTags = way.getTag("node_tags", Collections.emptyList());
        // a barrier edge has the restriction in both nodes and the tags are the same
        if (way.hasTag("gh:barrier_edge")) {
            Bus val = Bus.MISSING;
            for (String restriction : restrictions) {
                Object firstValue = nodeTags.get(0).get(restriction);
                if (firstValue != null) {
                    Bus tmpVal = Bus.find((String) nodeTags.get(0).get("bus"));
                    if (tmpVal.ordinal() > val.ordinal()) val = tmpVal;
                }
            }
            busEnc.setEnum(false, edgeId, edgeIntAccess, val);
            return;
        }

        if (way.hasTag("highway", "busway")) {
            busEnc.setEnum(false, edgeId, edgeIntAccess, Bus.DESIGNATED);
        } else {
            String firstValue = way.getFirstPriorityTag(restrictions);
            if (!firstValue.isEmpty())
                busEnc.setEnum(false, edgeId, edgeIntAccess, Bus.find(way.getTag("bus")));
        }
    }
}
