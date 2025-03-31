package com.graphhopper.routing.util.parsers;

import com.graphhopper.reader.ReaderWay;
import com.graphhopper.routing.ev.EdgeIntAccess;
import com.graphhopper.routing.ev.EnumEncodedValue;
import com.graphhopper.routing.ev.MaxWeightExcept;
import com.graphhopper.routing.util.TransportationMode;
import com.graphhopper.routing.util.parsers.helpers.OSMValueExtractor;
import com.graphhopper.storage.IntsRef;

import java.util.List;

import static com.graphhopper.routing.util.parsers.helpers.OSMValueExtractor.stringToTons;

public class MaxWeightExceptParser implements TagParser {
    private final EnumEncodedValue<MaxWeightExcept> mweEnc;
    private static final List<String> MAX_WEIGHT_RESTRICTIONS = OSMMaxWeightParser.MAX_WEIGHT_TAGS.stream()
            .map(e -> e + ":conditional").toList();
    private static final List<String> HGV_RESTRICTIONS = OSMRoadAccessParser.toOSMRestrictions(TransportationMode.HGV).stream()
            .map(e -> e + ":conditional").toList();

    public MaxWeightExceptParser(EnumEncodedValue<MaxWeightExcept> mweEnc) {
        this.mweEnc = mweEnc;
    }

    public void handleWayTags(int edgeId, EdgeIntAccess edgeIntAccess, ReaderWay way, IntsRef relationFlags) {
        // tagging like maxweight:conditional=no/none @ destination/delivery/forestry/service
        String condValue = way.getFirstValue(MAX_WEIGHT_RESTRICTIONS);
        if (!condValue.isEmpty()) {
            String[] values = condValue.split("@");
            if (values.length == 2) {
                String key = values[0].trim();
                String value = values[1].trim();
                if ("no".equals(key) || "none".equals(key)) {
                    if (value.startsWith("(") && value.endsWith(")")) value = value.substring(1, value.length() - 1);
                    mweEnc.setEnum(false, edgeId, edgeIntAccess, MaxWeightExcept.find(value));
                    return;
                }
            }
        }

        // For tagging like vehicle:conditional=destination @ (weight>3.5) AND maxweight=3.5
        // For vehicle:conditional=no @ (weight>3.5) => NONE is used, which is consistent with max_weight being set to 3.5 in this case
        for (String restriction : HGV_RESTRICTIONS) {
            String value = way.getTag(restriction, "");
            int atIndex = value.indexOf("@");
            if (atIndex > 0) {
                double dec = OSMValueExtractor.conditionalWeightToTons(value);
                // set it only if the weight value is the same as in max_weight
                if (!Double.isNaN(dec)
                        && (stringToTons(way.getTag("maxweight", "")) == dec
                        || stringToTons(way.getTag("maxweightrating:hgv", "")) == dec
                        || stringToTons(way.getTag("maxgcweight", "")) == dec)) {
                    mweEnc.setEnum(false, edgeId, edgeIntAccess, MaxWeightExcept.find(value.substring(0, atIndex).trim()));
                    break;
                }
            }
        }
    }
}
