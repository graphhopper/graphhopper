package com.graphhopper.routing.util.parsers;

import com.graphhopper.reader.ReaderWay;
import com.graphhopper.routing.ev.*;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.routing.util.TransportationMode;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModeAccessParserTest {

    private final EncodingManager em = new EncodingManager.Builder().add(BusAccess.create()).build();
    private final ModeAccessParser parser = new ModeAccessParser(TransportationMode.BUS, em.getBooleanEncodedValue(BusAccess.KEY), em.getBooleanEncodedValue(Roundabout.KEY));
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

    @Test
    public void testBusYes() {
        EdgeIntAccess access = new ArrayEdgeIntAccess(1);
        ReaderWay way = new ReaderWay(0);
        way.setTag("motor_vehicle", "no");
        way.setTag("highway", "tertiary");
        int edgeId = 0;
        parser.handleWayTags(edgeId, access, way, null);
        assertFalse(accessEnc.getBool(false, edgeId, access));

        access = new ArrayEdgeIntAccess(1);
        way.setTag("bus", "yes");
        parser.handleWayTags(edgeId, access, way, null);
        assertTrue(accessEnc.getBool(false, edgeId, access));
    }

    @Test
    public void testBusNo() {
        EdgeIntAccess access = new ArrayEdgeIntAccess(1);
        ReaderWay way = new ReaderWay(0);
        way.setTag("highway", "tertiary");
        int edgeId = 0;
        parser.handleWayTags(edgeId, access, way, null);
        assertTrue(accessEnc.getBool(false, edgeId, access));

        access = new ArrayEdgeIntAccess(1);
        way.setTag("bus", "no");
        parser.handleWayTags(edgeId, access, way, null);
        assertFalse(accessEnc.getBool(false, edgeId, access));
    }

    @Test
    public void testBusNodeAccess() {
        ReaderWay way = new ReaderWay(1);
        way.setTag("highway", "secondary");
        way.setTag("access", "no");
        way.setTag("gh:barrier_edge", true);

        Map<String, Object> tags1 = new HashMap<>();
        tags1.put("bus", "yes");
        Map<String, Object> tags2 = new HashMap<>();
        tags2.put("bus", "designated");
        way.setTag("node_tags", Arrays.asList(tags1, tags2));

        EdgeIntAccess access = new ArrayEdgeIntAccess(1);
        int edgeId = 0;
        parser.handleWayTags(edgeId, access, way, null);
        assertTrue(accessEnc.getBool(false, edgeId, access));
    }

    @Test
    public void testPsvYes() {
        EdgeIntAccess access = new ArrayEdgeIntAccess(1);
        ReaderWay way = new ReaderWay(0);
        way.setTag("motor_vehicle", "no");
        way.setTag("highway", "tertiary");
        int edgeId = 0;
        parser.handleWayTags(edgeId, access, way, null);
        assertFalse(accessEnc.getBool(false, edgeId, access));

        access = new ArrayEdgeIntAccess(1);
        way.setTag("psv", "yes");
        parser.handleWayTags(edgeId, access, way, null);
        assertTrue(accessEnc.getBool(false, edgeId, access));

        access = new ArrayEdgeIntAccess(1);
        way.setTag("psv", "yes");
        parser.handleWayTags(edgeId, access, way, null);
        assertTrue(accessEnc.getBool(false, edgeId, access));
    }

    @Test
    public void testMotorcycleYes() {
        BooleanEncodedValue mcAccessEnc = new SimpleBooleanEncodedValue("motorcycle_access", true);
        EncodingManager mcEM = new EncodingManager.Builder().add(mcAccessEnc).build();
        ModeAccessParser mcParser = new ModeAccessParser(TransportationMode.MOTORCYCLE, mcAccessEnc, mcEM.getBooleanEncodedValue(Roundabout.KEY));

        int edgeId = 0;
        EdgeIntAccess access = new ArrayEdgeIntAccess(1);
        ReaderWay way = new ReaderWay(0);
        way.setTag("motor_vehicle", "no");
        way.setTag("highway", "tertiary");
        mcParser.handleWayTags(edgeId, access, way, null);
        assertFalse(mcAccessEnc.getBool(false, edgeId, access));

        access = new ArrayEdgeIntAccess(1);
        way.setTag("motorcycle", "yes");
        mcParser.handleWayTags(0, access, way, null);
        assertTrue(mcAccessEnc.getBool(false, edgeId, access));
    }
}
