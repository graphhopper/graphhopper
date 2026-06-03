
package com.graphhopper.routing.util.parsers;

import com.graphhopper.reader.ReaderWay;
import com.graphhopper.routing.ev.EdgeIntAccess;
import com.graphhopper.routing.ev.EnumEncodedValue;
import com.graphhopper.routing.ev.Hgv;
import com.graphhopper.routing.util.TransportationMode;
import com.graphhopper.storage.IntsRef;

import static com.graphhopper.routing.util.parsers.helpers.OSMValueExtractor.conditionalWeightToTons;

public class OSMHgvParser extends OSMRoadAccessParser<Hgv> {

    public OSMHgvParser(EnumEncodedValue<Hgv> hgvEnc) {
        super(hgvEnc, OSMRoadAccessParser.toOSMRestrictions(TransportationMode.HGV),
                (readerWay, accessValue) -> accessValue, Hgv::find);
    }

    @Override
    public void handleWayTags(int edgeId, EdgeIntAccess edgeIntAccess, ReaderWay way, IntsRef relationFlags) {
        String value = way.getTag("hgv:conditional", "");
        int index = value.indexOf("@");
        if (index > 0 && conditionalWeightToTons(value) == 3.5) {
            Hgv hgvValue = Hgv.find(value.substring(0, index).trim());
            if (hgvValue != Hgv.MISSING) {
                accessEnc.setEnum(false, edgeId, edgeIntAccess, hgvValue);
                return;
            }
        }
        super.handleWayTags(edgeId, edgeIntAccess, way, relationFlags); // non-conditional access
    }
}
