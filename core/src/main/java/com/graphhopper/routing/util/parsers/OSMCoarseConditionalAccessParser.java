package com.graphhopper.routing.util.parsers;

import com.graphhopper.reader.ReaderWay;
import com.graphhopper.reader.osm.conditional.ConditionalOSMTagInspector;
import com.graphhopper.reader.osm.conditional.DateRangeParser;
import com.graphhopper.routing.ev.CoarseConditionalAccess;
import com.graphhopper.routing.ev.EdgeIntAccess;
import com.graphhopper.routing.ev.EnumEncodedValue;
import com.graphhopper.routing.util.TransportationMode;
import com.graphhopper.storage.IntsRef;
import com.graphhopper.util.Helper;

import java.util.*;

public class OSMCoarseConditionalAccessParser implements TagParser {

    private static final Set<String> RESTRICTED = new HashSet<>(Arrays.asList("no", "restricted", "private", "permit"));
    private static final Set<String> INTENDED = new HashSet<>(Arrays.asList("yes", "designated", "permissive"));
    private static final List<String> RESTRICTIONS = OSMRoadAccessParser.toOSMRestrictions(TransportationMode.CAR);

    private final EnumEncodedValue<CoarseConditionalAccess> restrictedEnc;
    private final ConditionalOSMTagInspector conditionalOSMTagInspector;

    public OSMCoarseConditionalAccessParser(EnumEncodedValue<CoarseConditionalAccess> restrictedEnc, String dateRangeParserString) {
        this.restrictedEnc = restrictedEnc;
        if (dateRangeParserString.isEmpty())
            dateRangeParserString = Helper.createFormatter("yyyy-MM-dd").format(new Date().getTime());
        conditionalOSMTagInspector = new ConditionalOSMTagInspector(Collections.singletonList(DateRangeParser.createInstance(dateRangeParserString)),
                RESTRICTIONS, RESTRICTED, INTENDED, false);
    }

    @Override
    public void handleWayTags(int edgeId, EdgeIntAccess edgeIntAccess, ReaderWay way, IntsRef relationFlags) {
        if (conditionalOSMTagInspector.isPermittedWayConditionallyRestricted(way))
            restrictedEnc.setEnum(false, edgeId, edgeIntAccess, CoarseConditionalAccess.NO);
        else if (conditionalOSMTagInspector.isRestrictedWayConditionallyPermitted(way))
            restrictedEnc.setEnum(false, edgeId, edgeIntAccess, CoarseConditionalAccess.YES);
    }
}
