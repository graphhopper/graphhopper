
package com.graphhopper.routing.util.parsers;

import com.graphhopper.reader.ReaderWay;
import com.graphhopper.routing.ev.EdgeIntAccess;
import com.graphhopper.routing.ev.EnumEncodedValue;
import com.graphhopper.routing.ev.Hgv;
import com.graphhopper.storage.IntsRef;

import static com.graphhopper.routing.util.parsers.helpers.OSMValueExtractor.conditionalWeightToTons;

public class OSMHgvParser implements TagParser {
    EnumEncodedValue<Hgv> hgvEnc;

    public OSMHgvParser(EnumEncodedValue<Hgv> hgvEnc) {
        this.hgvEnc = hgvEnc;
    }

    @Override
    public void handleWayTags(int edgeId, EdgeIntAccess edgeIntAccess, ReaderWay way, IntsRef relationFlags) {
        String value = way.getTag("hgv:conditional", "");
        int index = value.indexOf("@");
        Hgv hgvValue = index > 0 && conditionalWeightToTons(value) == 3.5 ? Hgv.find(value.substring(0, index).trim()) : Hgv.find(way.getTag("hgv"));
        hgvEnc.setEnum(false, edgeId, edgeIntAccess, hgvValue);
    }

    @Override
    public String getName() {
        return hgvEnc.getName();
    }
}
