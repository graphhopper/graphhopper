package com.graphhopper.routing.util.parsers;

import com.graphhopper.reader.ReaderWay;
import com.graphhopper.routing.ev.ArrayEdgeIntAccess;
import com.graphhopper.routing.ev.CoarseConditionalAccess;
import com.graphhopper.routing.ev.EnumEncodedValue;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.routing.util.TransportationMode;
import com.graphhopper.storage.IntsRef;
import com.graphhopper.util.Helper;
import org.junit.jupiter.api.Test;

import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OSMCoarseConditionalAccessParserTest {

    private final EnumEncodedValue<CoarseConditionalAccess> restricted = CoarseConditionalAccess.create();
    private final EncodingManager em = new EncodingManager.Builder().add(restricted).build();
    private final String today = Helper.createFormatter("yyyy MMM dd").format(new Date().getTime());
    private final OSMCoarseConditionalAccessParser parser = new OSMCoarseConditionalAccessParser(restricted, "");

    @Test
    public void testBasics() {
        ArrayEdgeIntAccess edgeIntAccess = new ArrayEdgeIntAccess(1);
        int edgeId = 0;
        assertEquals(CoarseConditionalAccess.NO_MATCH, restricted.getEnum(false, edgeId, edgeIntAccess));

        ReaderWay way = new ReaderWay(0L);
        way.clearTags();
        way.setTag("highway", "road");
        way.setTag("access:conditional", "no @ (" + today + ")");
        parser.handleWayTags(edgeId, edgeIntAccess, way, IntsRef.EMPTY);
        assertEquals(CoarseConditionalAccess.NO, restricted.getEnum(false, edgeId, edgeIntAccess));

        edgeIntAccess = new ArrayEdgeIntAccess(1);
        way.setTag("access:conditional", "no @ ( 2023 Mar 23 )");
        parser.handleWayTags(edgeId, edgeIntAccess, way, IntsRef.EMPTY);
        assertEquals(CoarseConditionalAccess.NO_MATCH, restricted.getEnum(false, edgeId, edgeIntAccess));

        edgeIntAccess = new ArrayEdgeIntAccess(1);
        way.clearTags();
        way.setTag("highway", "road");
        way.setTag("access", "no");
        way.setTag("access:conditional", "yes @ (" + today + ")");
        parser.handleWayTags(edgeId, edgeIntAccess, way, IntsRef.EMPTY);
        assertEquals(CoarseConditionalAccess.YES, restricted.getEnum(false, edgeId, edgeIntAccess));
    }
}
