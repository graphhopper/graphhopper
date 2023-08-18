package com.graphhopper.routing.util.parsers;

import com.graphhopper.reader.ReaderWay;
import com.graphhopper.routing.ev.ArrayEdgeIntAccess;
import com.graphhopper.routing.ev.CarConditionalAccess;
import com.graphhopper.routing.ev.EnumEncodedValue;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.storage.IntsRef;
import com.graphhopper.util.Helper;
import org.junit.jupiter.api.Test;

import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OSMConditionalAccessParserTest {

    private final EnumEncodedValue<CarConditionalAccess> restricted = CarConditionalAccess.create();
    private final EncodingManager em = new EncodingManager.Builder().add(restricted).build();
    private final String today = "2023 May 17";
    private final OSMConditionalAccessParser parser = new OSMConditionalAccessParser(CarConditionalAccess.CONDITIONALS, restricted, "2023-05-17");

    @Test
    public void testBasics() {
        ArrayEdgeIntAccess edgeIntAccess = new ArrayEdgeIntAccess(1);
        int edgeId = 0;
        assertEquals(CarConditionalAccess.MISSING, restricted.getEnum(false, edgeId, edgeIntAccess));

        ReaderWay way = new ReaderWay(0L);
        way.clearTags();
        way.setTag("highway", "road");
        way.setTag("access:conditional", "no @ (" + today + ")");
        parser.handleWayTags(edgeId, edgeIntAccess, way, IntsRef.EMPTY);
        assertEquals(CarConditionalAccess.NO, restricted.getEnum(false, edgeId, edgeIntAccess));

        edgeIntAccess = new ArrayEdgeIntAccess(1);
        way.setTag("access:conditional", "no @ ( 2023 Mar 23 - " + today + " )");
        parser.handleWayTags(edgeId, edgeIntAccess, way, IntsRef.EMPTY);
        assertEquals(CarConditionalAccess.NO, restricted.getEnum(false, edgeId, edgeIntAccess));

        edgeIntAccess = new ArrayEdgeIntAccess(1);
        way.clearTags();
        way.setTag("highway", "road");
        way.setTag("access", "no");
        way.setTag("access:conditional", "yes @ (" + today + ")");
        parser.handleWayTags(edgeId, edgeIntAccess, way, IntsRef.EMPTY);
        assertEquals(CarConditionalAccess.YES, restricted.getEnum(false, edgeId, edgeIntAccess));

        // range does not match => missing
        edgeIntAccess = new ArrayEdgeIntAccess(1);
        way.setTag("access:conditional", "no @ ( 2023 Mar 23 )");
        parser.handleWayTags(edgeId, edgeIntAccess, way, IntsRef.EMPTY);
        assertEquals(CarConditionalAccess.MISSING, restricted.getEnum(false, edgeId, edgeIntAccess));

        // for now consider if seasonal range
        edgeIntAccess = new ArrayEdgeIntAccess(1);
        way.setTag("access:conditional", "no @ ( Mar 23 - Aug 23 )");
        parser.handleWayTags(edgeId, edgeIntAccess, way, IntsRef.EMPTY);
        assertEquals(CarConditionalAccess.NO, restricted.getEnum(false, edgeId, edgeIntAccess));

        edgeIntAccess = new ArrayEdgeIntAccess(1);
        way.setTag("access:conditional", "yes @ Apr-Nov");
        parser.handleWayTags(edgeId, edgeIntAccess, way, IntsRef.EMPTY);
        assertEquals(CarConditionalAccess.YES, restricted.getEnum(false, edgeId, edgeIntAccess));
    }
}
