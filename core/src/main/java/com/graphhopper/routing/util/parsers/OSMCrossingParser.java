package com.graphhopper.routing.util.parsers;

import com.graphhopper.reader.ReaderWay;
import com.graphhopper.routing.ev.Crossing;
import com.graphhopper.routing.ev.EnumEncodedValue;
import com.graphhopper.storage.IntsRef;
import com.graphhopper.util.Helper;

import java.util.List;
import java.util.Map;

public class OSMCrossingParser implements TagParser {
    protected final EnumEncodedValue<Crossing> crossingEnc;

    public OSMCrossingParser(EnumEncodedValue<Crossing> crossingEnc) {
        this.crossingEnc = crossingEnc;
    }

    @Override
    public IntsRef handleWayTags(IntsRef edgeFlags, ReaderWay readerWay, IntsRef relationFlags) {
        List<Map<String, Object>> nodeTags = readerWay.getTag("node_tags", null);
        if (nodeTags == null)
            return edgeFlags;

        for (int i = 0; i < nodeTags.size(); i++) {
            Map<String, Object> tags = nodeTags.get(i);
            if ("crossing".equals(tags.get("railway")) || "level_crossing".equals(tags.get("railway"))) {
                // use special value if with/without barriers? (contains crossing:barrier)
                crossingEnc.setEnum(false, edgeFlags, Crossing.RAILWAY);
                return edgeFlags;
            }
            String crossingValue = (String) tags.get("crossing");
            // some crossing values like "no" do not require highway=crossing and sometimes no crossing value exists although highway=crossing
            if (Helper.isEmpty(crossingValue) && ("crossing".equals(tags.get("highway")) || "crossing".equals(tags.get("footway")) || "crossing".equals(tags.get("cycleway")))) {
                crossingEnc.setEnum(false, edgeFlags, Crossing.UNMARKED);
                // next node could have more specific Crossing value
                continue;
            }
            Crossing crossing = Crossing.find(crossingValue);
            if (crossing != Crossing.MISSING) {
                crossingEnc.setEnum(false, edgeFlags, crossing);
                return edgeFlags;
            }
        }
        return edgeFlags;
    }
}
