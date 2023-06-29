package com.graphhopper.routing.util.parsers;

import com.graphhopper.reader.ReaderWay;
import com.graphhopper.routing.ev.ArrayEdgeIntAccess;
import com.graphhopper.routing.ev.ConditionalAccess;
import com.graphhopper.routing.ev.EnumEncodedValue;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.routing.util.TransportationMode;
import com.graphhopper.storage.IntsRef;
import com.graphhopper.util.Helper;
import org.junit.jupiter.api.Test;

import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OSMConditionalAccessParserTest {

    private final EnumEncodedValue<ConditionalAccess> restricted = ConditionalAccess.create();
    private final EncodingManager em = new EncodingManager.Builder().add(restricted).build();
    private final String today = Helper.createFormatter("yyyy MMM dd").format(new Date().getTime());
    private final String todayForDRParser = Helper.createFormatter("yyyy-MM-dd").format(new Date().getTime());
    private final OSMConditionalAccessParser parser = new OSMConditionalAccessParser(
            OSMRoadAccessParser.toOSMRestrictions(TransportationMode.CAR), restricted, todayForDRParser);

    @Test
    public void testBasics() {
        ArrayEdgeIntAccess edgeIntAccess = new ArrayEdgeIntAccess(1);
        int edgeId = 0;
        assertEquals(ConditionalAccess.MISSING, restricted.getEnum(false, edgeId, edgeIntAccess));

        ReaderWay way = new ReaderWay(0L);
        way.clearTags();
        way.setTag("highway", "road");
        way.setTag("access:conditional", "no @ (" + today + ")");

        parser.handleWayTags(edgeId, edgeIntAccess, way, IntsRef.EMPTY);
        assertEquals(ConditionalAccess.NO, restricted.getEnum(false, edgeId, edgeIntAccess));

        way.clearTags();
        way.setTag("highway", "road");
        way.setTag("access", "no");
        way.setTag("access:conditional", "yes @ (" + today + ")");
        parser.handleWayTags(edgeId, edgeIntAccess, way, IntsRef.EMPTY);
        assertEquals(ConditionalAccess.YES, restricted.getEnum(false, edgeId, edgeIntAccess));
    }
}
