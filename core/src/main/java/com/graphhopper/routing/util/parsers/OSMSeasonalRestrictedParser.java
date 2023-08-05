package com.graphhopper.routing.util.parsers;

import com.graphhopper.reader.ReaderWay;
import com.graphhopper.reader.osm.conditional.ConditionalOSMTagInspector;
import com.graphhopper.reader.osm.conditional.ConditionalTagInspector;
import com.graphhopper.routing.ev.BooleanEncodedValue;
import com.graphhopper.routing.ev.EdgeIntAccess;
import com.graphhopper.storage.IntsRef;

import java.util.*;

/**
 * This parser scans different OSM conditional tags to identify if a way has only conditional access
 */
public class OSMSeasonalRestrictedParser implements TagParser {

    private final BooleanEncodedValue seasonalRestrictedEnc;
    private final ConditionalTagInspector acceptor = new ConditionalOSMTagInspector(getConditionalTags(), getSampleRestrictedValues(), new HashSet<>());

     /**
     * @param seasonalRestrictedEnc used to find out if way access is conditional
     */
    public OSMSeasonalRestrictedParser(BooleanEncodedValue seasonalRestrictedEnc) {
        this.seasonalRestrictedEnc = seasonalRestrictedEnc;
    }

    private static Set<String> getSampleRestrictedValues() {
        Set<String> restrictedValues = new HashSet<>();
        restrictedValues.add("no");
        restrictedValues.add("restricted");
        return restrictedValues;
    }

    private static List<String> getConditionalTags() {
        List<String> conditionalTags = new ArrayList<>();
        conditionalTags.add("vehicle");
        conditionalTags.add("access");
        return conditionalTags;
    }

    @Override
    public void handleWayTags(int edgeId, EdgeIntAccess edgeIntAccess, ReaderWay way, IntsRef relationFlags) {
        boolean isSeasonalRestricted = acceptor.isPermittedWayConditionallyRestricted(way);
        seasonalRestrictedEnc.setBool(false, edgeId, edgeIntAccess, isSeasonalRestricted);
    }
}
