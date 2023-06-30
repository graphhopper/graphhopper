package com.graphhopper.routing.util.parsers;

import com.graphhopper.reader.ReaderWay;
import com.graphhopper.routing.ev.ArrayEdgeIntAccess;
import com.graphhopper.routing.ev.BooleanEncodedValue;
import com.graphhopper.routing.ev.TemporaryRestriction;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.storage.IntsRef;
import com.graphhopper.util.Helper;
import org.junit.jupiter.api.Test;

import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OSMTemporaryRestrictionParserTest {

    private final BooleanEncodedValue restricted = TemporaryRestriction.create();
    private final EncodingManager em = new EncodingManager.Builder().add(restricted).build();
    private final String today = Helper.createFormatter("yyyy MMM dd").format(new Date().getTime());
    private final OSMTemporaryRestrictionParser parser = new OSMTemporaryRestrictionParser(restricted, "");

    @Test
    public void testBasics() {
        ArrayEdgeIntAccess edgeIntAccess = new ArrayEdgeIntAccess(1);
        int edgeId = 0;
        assertFalse(restricted.getBool(false, edgeId, edgeIntAccess));

        ReaderWay way = new ReaderWay(0L);
        way.clearTags();
        way.setTag("highway", "road");
        way.setTag("access:conditional", "no @ (" + today + ")");
        parser.handleWayTags(edgeId, edgeIntAccess, way, IntsRef.EMPTY);
        assertTrue(restricted.getBool(false, edgeId, edgeIntAccess));

        edgeIntAccess = new ArrayEdgeIntAccess(1);
        way.setTag("access:conditional", "no @ ( 2023 Mar 23 )");
        parser.handleWayTags(edgeId, edgeIntAccess, way, IntsRef.EMPTY);
        assertFalse(restricted.getBool(false, edgeId, edgeIntAccess));

        edgeIntAccess = new ArrayEdgeIntAccess(1);
        way.setTag("access:conditional", "no @ ( 2023 Mar 23 - " + today + " )");
        parser.handleWayTags(edgeId, edgeIntAccess, way, IntsRef.EMPTY);
        assertTrue(restricted.getBool(false, edgeId, edgeIntAccess));

        edgeIntAccess = new ArrayEdgeIntAccess(1);
        way.clearTags();
        way.setTag("highway", "road");
        way.setTag("access", "no");
        way.setTag("access:conditional", "yes @ (" + today + ")");
        parser.handleWayTags(edgeId, edgeIntAccess, way, IntsRef.EMPTY);
        // yes is unsupported
        assertFalse(restricted.getBool(false, edgeId, edgeIntAccess));
    }
}
