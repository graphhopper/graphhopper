package com.graphhopper.routing.util.parsers;

import com.graphhopper.reader.ReaderWay;
import com.graphhopper.reader.osm.conditional.ConditionalOSMTagInspector;
import com.graphhopper.reader.osm.conditional.DateRangeParser;
import com.graphhopper.routing.ev.ConditionalAccess;
import com.graphhopper.routing.ev.EdgeIntAccess;
import com.graphhopper.routing.ev.EnumEncodedValue;
import com.graphhopper.storage.IntsRef;

import java.util.*;

public class OSMConditionalAccessParser implements TagParser {

    private static final Set<String> RESTRICTED = new HashSet<>(Arrays.asList("no", "restricted", "private", "permit"));
    private static final Set<String> INTENDED = new HashSet<>(Arrays.asList("yes", "designated", "permissive"));

    private final EnumEncodedValue<ConditionalAccess> restrictedEnc;
    private final ConditionalOSMTagInspector conditionalOSMTagInspector;

    public OSMConditionalAccessParser(List<String> restrictions, EnumEncodedValue<ConditionalAccess> restrictedEnc, String dateRangeParserString) {
        this.restrictedEnc = restrictedEnc;
        conditionalOSMTagInspector = new ConditionalOSMTagInspector(Collections.singletonList(DateRangeParser.createInstance(dateRangeParserString)),
                restrictions, RESTRICTED, INTENDED, false);
    }

    @Override
    public void handleWayTags(int edgeId, EdgeIntAccess edgeIntAccess, ReaderWay way, IntsRef relationFlags) {
        if (conditionalOSMTagInspector.isPermittedWayConditionallyRestricted(way))
            restrictedEnc.setEnum(false, edgeId, edgeIntAccess, ConditionalAccess.NO);
        else if (conditionalOSMTagInspector.isRestrictedWayConditionallyPermitted(way))
            restrictedEnc.setEnum(false, edgeId, edgeIntAccess, ConditionalAccess.YES);
    }
}
