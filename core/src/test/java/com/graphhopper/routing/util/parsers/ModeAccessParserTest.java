package com.graphhopper.routing.util.parsers;

import com.graphhopper.reader.ReaderWay;
import com.graphhopper.routing.ev.*;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.routing.util.TransportationMode;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModeAccessParserTest {

    private final EncodingManager em = new EncodingManager.Builder().add(Roundabout.create()).add(BusAccess.create()).build();
    private final ModeAccessParser parser = new ModeAccessParser(TransportationMode.BUS, em.getBooleanEncodedValue(BusAccess.KEY), em.getBooleanEncodedValue(Roundabout.KEY), List.of());
    private final BooleanEncodedValue busAccessEnc = em.getBooleanEncodedValue(BusAccess.KEY);

    @Test
    public void testAccess() {
        ReaderWay way = new ReaderWay(1);
        way.setTag("highway", "primary");
        EdgeBytesAccess edgeAccess = new EdgeBytesAccessArray(em.getBytesForFlags());
        int edgeId = 0;
        parser.handleWayTags(edgeId, edgeAccess, way, null);
        assertTrue(busAccessEnc.getBool(false, edgeId, edgeAccess));
        assertTrue(busAccessEnc.getBool(true, edgeId, edgeAccess));
    }

    @Test
    public void testPrivate() {
        ReaderWay way = new ReaderWay(1);
        way.setTag("highway", "primary");
        way.setTag("access", "private");
        EdgeBytesAccess edgeAccess = new EdgeBytesAccessArray(em.getBytesForFlags());
        int edgeId = 0;
        parser.handleWayTags(edgeId, edgeAccess, way, null);
        assertTrue(busAccessEnc.getBool(false, edgeId, edgeAccess));
        assertTrue(busAccessEnc.getBool(true, edgeId, edgeAccess));
    }

    @Test
    public void testOneway() {
        ReaderWay way = new ReaderWay(1);
        way.setTag("highway", "primary");
        way.setTag("oneway", "yes");

        int edgeId = 0;
        EdgeBytesAccess edgeAccess = new EdgeBytesAccessArray(em.getBytesForFlags());
        parser.handleWayTags(edgeId, edgeAccess, way, null);
        assertTrue(busAccessEnc.getBool(false, edgeId, edgeAccess));
        assertFalse(busAccessEnc.getBool(true, edgeId, edgeAccess));

        way.clearTags();
        way.setTag("highway", "tertiary");
        way.setTag("vehicle:forward", "no");
        edgeAccess = new EdgeBytesAccessArray(em.getBytesForFlags());
        parser.handleWayTags(edgeId, edgeAccess, way, null);
        assertFalse(busAccessEnc.getBool(false, edgeId, edgeAccess));
        assertTrue(busAccessEnc.getBool(true, edgeId, edgeAccess));

        way.clearTags();
        way.setTag("highway", "tertiary");
        way.setTag("vehicle:backward", "no");
        edgeAccess = new EdgeBytesAccessArray(em.getBytesForFlags());
        parser.handleWayTags(edgeId, edgeAccess, way, null);
        assertTrue(busAccessEnc.getBool(false, edgeId, edgeAccess));
        assertFalse(busAccessEnc.getBool(true, edgeId, edgeAccess));

        way.setTag("bus:backward", "yes");
        edgeAccess = new EdgeBytesAccessArray(em.getBytesForFlags());
        parser.handleWayTags(edgeId, edgeAccess, way, null);
        assertTrue(busAccessEnc.getBool(false, edgeId, edgeAccess));
        assertTrue(busAccessEnc.getBool(true, edgeId, edgeAccess));

        way.clearTags();
        way.setTag("highway", "tertiary");
        way.setTag("vehicle:backward", "yes");
        way.setTag("bus:backward", "no");
        edgeAccess = new EdgeBytesAccessArray(em.getBytesForFlags());
        parser.handleWayTags(edgeId, edgeAccess, way, null);
        assertTrue(busAccessEnc.getBool(false, edgeId, edgeAccess));
        assertFalse(busAccessEnc.getBool(true, edgeId, edgeAccess));
    }

    @Test
    public void testBusYes() {
        EdgeBytesAccess access = new EdgeBytesAccessArray(4);
        ReaderWay way = new ReaderWay(0);
        way.setTag("motor_vehicle", "no");
        way.setTag("highway", "tertiary");
        int edgeId = 0;
        parser.handleWayTags(edgeId, access, way, null);
        assertFalse(busAccessEnc.getBool(false, edgeId, access));

        access = new EdgeBytesAccessArray(4);
        way.setTag("bus", "yes");
        parser.handleWayTags(edgeId, access, way, null);
        assertTrue(busAccessEnc.getBool(false, edgeId, access));

        access = new EdgeBytesAccessArray(4);
        way = new ReaderWay(0);
        way.setTag("highway", "primary");
        way.setTag("oneway", "yes");
        way.setTag("oneway:bus", "no");
        parser.handleWayTags(edgeId, access, way, null);
        assertTrue(busAccessEnc.getBool(false, edgeId, access));
        assertTrue(busAccessEnc.getBool(true, edgeId, access));

        access = new EdgeBytesAccessArray(4);
        way.setTag("oneway:psv", "no");
        way.setTag("oneway:bus", "yes");
        parser.handleWayTags(edgeId, access, way, null);
        assertTrue(busAccessEnc.getBool(false, edgeId, access));
        assertFalse(busAccessEnc.getBool(true, edgeId, access));
    }

    @Test
    public void testBusNo() {
        EdgeBytesAccess access = new EdgeBytesAccessArray(4);
        ReaderWay way = new ReaderWay(0);
        way.setTag("highway", "tertiary");
        int edgeId = 0;
        parser.handleWayTags(edgeId, access, way, null);
        assertTrue(busAccessEnc.getBool(false, edgeId, access));

        access = new EdgeBytesAccessArray(4);
        way.setTag("bus", "no");
        parser.handleWayTags(edgeId, access, way, null);
        assertFalse(busAccessEnc.getBool(false, edgeId, access));
    }

    @Test
    public void testBusNodeAccess() {
        ReaderWay way = new ReaderWay(1);
        way.setTag("highway", "secondary");
        way.setTag("gh:barrier_edge", true);

        Map<String, Object> nodeTags = new HashMap<>();
        nodeTags.put("access", "no");
        nodeTags.put("bus", "yes");
        way.setTag("node_tags", Arrays.asList(nodeTags, new HashMap<>()));
        EdgeBytesAccess access = new EdgeBytesAccessArray(4);
        int edgeId = 0;
        parser.handleWayTags(edgeId, access, way, null);
        assertTrue(busAccessEnc.getBool(false, edgeId, access));

        nodeTags = new HashMap<>();
        nodeTags.put("access", "yes");
        nodeTags.put("bus", "no");
        way.setTag("node_tags", Arrays.asList(nodeTags));
        access = new EdgeBytesAccessArray(4);
        parser.handleWayTags(edgeId, access, way, null);
        assertFalse(busAccessEnc.getBool(false, edgeId, access));

        // ensure that allowing node tags (bus=yes) do not unblock the inaccessible way
        way.setTag("access", "no");
        nodeTags = new HashMap<>();
        nodeTags.put("bus", "yes");
        way.setTag("node_tags", Arrays.asList(nodeTags, new HashMap<>()));
        access = new EdgeBytesAccessArray(4);
        parser.handleWayTags(edgeId, access, way, null);
        assertFalse(busAccessEnc.getBool(false, edgeId, access));
    }

    @Test
    public void testPsvYes() {
        EdgeBytesAccess access = new EdgeBytesAccessArray(4);
        ReaderWay way = new ReaderWay(0);
        way.setTag("motor_vehicle", "no");
        way.setTag("highway", "tertiary");
        int edgeId = 0;
        parser.handleWayTags(edgeId, access, way, null);
        assertFalse(busAccessEnc.getBool(false, edgeId, access));

        access = new EdgeBytesAccessArray(4);
        way.setTag("psv", "yes");
        parser.handleWayTags(edgeId, access, way, null);
        assertTrue(busAccessEnc.getBool(false, edgeId, access));

        access = new EdgeBytesAccessArray(4);
        way.setTag("psv", "yes");
        parser.handleWayTags(edgeId, access, way, null);
        assertTrue(busAccessEnc.getBool(false, edgeId, access));
    }

    @Test
    public void testMotorcycleYes() {
        BooleanEncodedValue mcAccessEnc = new SimpleBooleanEncodedValue("motorcycle_access", true);
        EncodingManager mcEM = new EncodingManager.Builder().add(mcAccessEnc).add(Roundabout.create()).build();
        ModeAccessParser mcParser = new ModeAccessParser(TransportationMode.MOTORCYCLE, mcAccessEnc, mcEM.getBooleanEncodedValue(Roundabout.KEY), List.of());

        int edgeId = 0;
        EdgeBytesAccess access = new EdgeBytesAccessArray(4);
        ReaderWay way = new ReaderWay(0);
        way.setTag("motor_vehicle", "no");
        way.setTag("highway", "tertiary");
        mcParser.handleWayTags(edgeId, access, way, null);
        assertFalse(mcAccessEnc.getBool(false, edgeId, access));

        access = new EdgeBytesAccessArray(4);
        way.setTag("motorcycle", "yes");
        mcParser.handleWayTags(0, access, way, null);
        assertTrue(mcAccessEnc.getBool(false, edgeId, access));
    }

    @Test
    public void temporalAccess() {
        int edgeId = 0;
        EdgeBytesAccessArray access = new EdgeBytesAccessArray(4);
        ReaderWay way = new ReaderWay(1);
        way.setTag("highway", "primary");
        way.setTag("access:conditional", "no @ (May - June)");
        parser.handleWayTags(edgeId, access, way, null);
        assertTrue(busAccessEnc.getBool(false, edgeId, access));

        access = new EdgeBytesAccessArray(4);
        way = new ReaderWay(1);
        way.setTag("highway", "primary");
        way.setTag("psv:conditional", "no @ (May - June)");
        parser.handleWayTags(edgeId, access, way, null);
        assertTrue(busAccessEnc.getBool(false, edgeId, access));

        access = new EdgeBytesAccessArray(4);
        way = new ReaderWay(1);
        way.setTag("highway", "primary");
        way.setTag("psv", "no");
        way.setTag("access:conditional", "yes @ (May - June)");
        parser.handleWayTags(edgeId, access, way, null);
        assertFalse(busAccessEnc.getBool(false, edgeId, access));

        access = new EdgeBytesAccessArray(4);
        way = new ReaderWay(1);
        way.setTag("highway", "primary");
        way.setTag("access", "no");
        way.setTag("psv:conditional", "yes @ (May - June)");
        parser.handleWayTags(edgeId, access, way, null);
        assertTrue(busAccessEnc.getBool(false, edgeId, access));
    }
}
