package com.graphhopper.routing.util.parsers;

import com.graphhopper.reader.ReaderWay;
import com.graphhopper.routing.ev.ArrayEdgeIntAccess;
import com.graphhopper.routing.ev.BooleanEncodedValue;
import com.graphhopper.routing.ev.BusAccess;
import com.graphhopper.routing.ev.EdgeIntAccess;
import com.graphhopper.routing.util.EncodingManager;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BusAccessParserTest {

    private final EncodingManager em = new EncodingManager.Builder().add(BusAccess.create()).build();
    private final BusAccessParser parser = new BusAccessParser(em);
    private final BooleanEncodedValue accessEnc = em.getBooleanEncodedValue(BusAccess.KEY);

    @Test
    public void testAccess() {
        ReaderWay way = new ReaderWay(1);
        way.setTag("highway", "primary");
        EdgeIntAccess edgeIntAccess = new ArrayEdgeIntAccess(em.getIntsForFlags());
        int edgeId = 0;
        parser.handleWayTags(edgeId, edgeIntAccess, way, null);
        assertTrue(accessEnc.getBool(false, edgeId, edgeIntAccess));
        assertTrue(accessEnc.getBool(true, edgeId, edgeIntAccess));
    }

    @Test
    public void testOneway() {
        ReaderWay way = new ReaderWay(1);
        way.setTag("highway", "primary");
        way.setTag("oneway", "yes");

        int edgeId = 0;
        EdgeIntAccess edgeIntAccess = new ArrayEdgeIntAccess(em.getIntsForFlags());
        parser.handleWayTags(edgeId, edgeIntAccess, way, null);
        assertTrue(accessEnc.getBool(false, edgeId, edgeIntAccess));
        assertFalse(accessEnc.getBool(true, edgeId, edgeIntAccess));

        way.clearTags();
        way.setTag("highway", "tertiary");
        way.setTag("vehicle:forward", "no");
        edgeIntAccess = new ArrayEdgeIntAccess(em.getIntsForFlags());
        parser.handleWayTags(edgeId, edgeIntAccess, way, null);
        assertFalse(accessEnc.getBool(false, edgeId, edgeIntAccess));
        assertTrue(accessEnc.getBool(true, edgeId, edgeIntAccess));

        way.clearTags();
        way.setTag("highway", "tertiary");
        way.setTag("vehicle:backward", "no");
        edgeIntAccess = new ArrayEdgeIntAccess(em.getIntsForFlags());
        parser.handleWayTags(edgeId, edgeIntAccess, way, null);
        assertTrue(accessEnc.getBool(false, edgeId, edgeIntAccess));
        assertFalse(accessEnc.getBool(true, edgeId, edgeIntAccess));
    }

}
