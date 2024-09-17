package com.graphhopper.routing.util.parsers;

import com.graphhopper.reader.ReaderWay;
import com.graphhopper.routing.ev.*;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.routing.util.TransportationMode;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModeAccessParserTest {

    private final EncodingManager em = new EncodingManager.Builder().add(Roundabout.create()).add(BusAccess.create()).build();
    private final ModeAccessParser parser = new ModeAccessParser(TransportationMode.BUS,
            em.getBooleanEncodedValue(BusAccess.KEY), true,
            em.getBooleanEncodedValue(Roundabout.KEY), Set.of(), Set.of());
    private final BooleanEncodedValue busAccessEnc = em.getBooleanEncodedValue(BusAccess.KEY);

    @Test
    public void testAccess() {
        ReaderWay way = new ReaderWay(1);
        way.setTag("highway", "primary");
        EdgeIntAccess edgeIntAccess = ArrayEdgeIntAccess.createFromBytes(em.getBytesForFlags());
        int edgeId = 0;
        parser.handleWayTags(edgeId, edgeIntAccess, way, null);
        assertTrue(busAccessEnc.getBool(false, edgeId, edgeIntAccess));
        assertTrue(busAccessEnc.getBool(true, edgeId, edgeIntAccess));
    }

    @Test
    public void testPrivate() {
        ReaderWay way = new ReaderWay(1);
        way.setTag("highway", "primary");
        way.setTag("access", "private");
        EdgeIntAccess edgeIntAccess = ArrayEdgeIntAccess.createFromBytes(em.getBytesForFlags());
        int edgeId = 0;
        parser.handleWayTags(edgeId, edgeIntAccess, way, null);
        assertTrue(busAccessEnc.getBool(false, edgeId, edgeIntAccess));
        assertTrue(busAccessEnc.getBool(true, edgeId, edgeIntAccess));
    }

    @Test
    public void testOneway() {
        ReaderWay way = new ReaderWay(1);
        way.setTag("highway", "primary");
        way.setTag("oneway", "yes");

        int edgeId = 0;
        EdgeIntAccess edgeIntAccess = ArrayEdgeIntAccess.createFromBytes(em.getBytesForFlags());
        parser.handleWayTags(edgeId, edgeIntAccess, way, null);
        assertTrue(busAccessEnc.getBool(false, edgeId, edgeIntAccess));
        assertFalse(busAccessEnc.getBool(true, edgeId, edgeIntAccess));

        way.clearTags();
        way.setTag("highway", "tertiary");
        way.setTag("vehicle:forward", "no");
        edgeIntAccess = ArrayEdgeIntAccess.createFromBytes(em.getBytesForFlags());
        parser.handleWayTags(edgeId, edgeIntAccess, way, null);
        assertFalse(busAccessEnc.getBool(false, edgeId, edgeIntAccess));
        assertTrue(busAccessEnc.getBool(true, edgeId, edgeIntAccess));

        way.clearTags();
        way.setTag("highway", "tertiary");
        way.setTag("vehicle:backward", "no");
        edgeIntAccess = ArrayEdgeIntAccess.createFromBytes(em.getBytesForFlags());
        parser.handleWayTags(edgeId, edgeIntAccess, way, null);
        assertTrue(busAccessEnc.getBool(false, edgeId, edgeIntAccess));
        assertFalse(busAccessEnc.getBool(true, edgeId, edgeIntAccess));

        way.setTag("bus:backward", "yes");
        edgeIntAccess = ArrayEdgeIntAccess.createFromBytes(em.getBytesForFlags());
        parser.handleWayTags(edgeId, edgeIntAccess, way, null);
        assertTrue(busAccessEnc.getBool(false, edgeId, edgeIntAccess));
        assertTrue(busAccessEnc.getBool(true, edgeId, edgeIntAccess));

        way.clearTags();
        way.setTag("highway", "tertiary");
        way.setTag("vehicle:backward", "yes");
        way.setTag("bus:backward", "no");
        edgeIntAccess = ArrayEdgeIntAccess.createFromBytes(em.getBytesForFlags());
        parser.handleWayTags(edgeId, edgeIntAccess, way, null);
        assertTrue(busAccessEnc.getBool(false, edgeId, edgeIntAccess));
        assertFalse(busAccessEnc.getBool(true, edgeId, edgeIntAccess));
    }

    @Test
    public void testBusYes() {
        EdgeIntAccess access = new ArrayEdgeIntAccess(1);
        ReaderWay way = new ReaderWay(0);
        way.setTag("motor_vehicle", "no");
        way.setTag("highway", "tertiary");
        int edgeId = 0;
        parser.handleWayTags(edgeId, access, way, null);
        assertFalse(busAccessEnc.getBool(false, edgeId, access));

        access = new ArrayEdgeIntAccess(1);
        way.setTag("bus", "yes");
        parser.handleWayTags(edgeId, access, way, null);
        assertTrue(busAccessEnc.getBool(false, edgeId, access));

        access = new ArrayEdgeIntAccess(1);
        way = new ReaderWay(0);
        way.setTag("highway", "primary");
        way.setTag("oneway", "yes");
        way.setTag("oneway:bus", "no");
        parser.handleWayTags(edgeId, access, way, null);
        assertTrue(busAccessEnc.getBool(false, edgeId, access));
        assertTrue(busAccessEnc.getBool(true, edgeId, access));

        access = new ArrayEdgeIntAccess(1);
        way.setTag("oneway:psv", "no");
        way.setTag("oneway:bus", "yes");
        parser.handleWayTags(edgeId, access, way, null);
        assertTrue(busAccessEnc.getBool(false, edgeId, access));
        assertFalse(busAccessEnc.getBool(true, edgeId, access));
    }

    @Test
    public void testBusNo() {
        EdgeIntAccess access = new ArrayEdgeIntAccess(1);
        ReaderWay way = new ReaderWay(0);
        way.setTag("highway", "tertiary");
        int edgeId = 0;
        parser.handleWayTags(edgeId, access, way, null);
        assertTrue(busAccessEnc.getBool(false, edgeId, access));

        access = new ArrayEdgeIntAccess(1);
        way.setTag("bus", "no");
        parser.handleWayTags(edgeId, access, way, null);
        assertFalse(busAccessEnc.getBool(false, edgeId, access));
    }

    @Test
    public void testBusNodeAccess() {
        ReaderWay way = new ReaderWay(1);
        way.setTag("highway", "secondary");
        way.setTag("gh:barrier_edge", true);

        way.setTag("node_tags", List.of(Map.of("access", "no", "bus", "yes"), Map.of()));
        EdgeIntAccess access = new ArrayEdgeIntAccess(1);
        int edgeId = 0;
        parser.handleWayTags(edgeId, access, way, null);
        assertTrue(busAccessEnc.getBool(false, edgeId, access));

        way.setTag("node_tags", List.of(Map.of("access", "yes", "bus", "no")));
        access = new ArrayEdgeIntAccess(1);
        parser.handleWayTags(edgeId, access, way, null);
        assertFalse(busAccessEnc.getBool(false, edgeId, access));

        // ensure that allowing node tags (bus=yes) do not unblock the inaccessible way
        way.setTag("access", "no");
        way.setTag("node_tags", List.of(Map.of("bus", "yes"), Map.of()));
        access = new ArrayEdgeIntAccess(1);
        parser.handleWayTags(edgeId, access, way, null);
        assertFalse(busAccessEnc.getBool(false, edgeId, access));
    }

    @Test
    public void testBarrier() {
        ReaderWay way = new ReaderWay(1);
        way.setTag("highway", "secondary");
        way.setTag("gh:barrier_edge", true);

        way.setTag("node_tags", Arrays.asList(Map.of("barrier", "bollard"), Map.of()));
        EdgeIntAccess access = new ArrayEdgeIntAccess(1);
        int edgeId = 0;
        parser.handleWayTags(edgeId, access, way, null);
        assertFalse(busAccessEnc.getBool(false, edgeId, access));

        way.setTag("node_tags", Arrays.asList(Map.of("barrier", "gate"), Map.of()));
        access = new ArrayEdgeIntAccess(1);
        parser.handleWayTags(edgeId, access, way, null);
        assertTrue(busAccessEnc.getBool(false, edgeId, access));

        // this special mode ignores all barriers except kissing_gate
        BooleanEncodedValue tmpAccessEnc = new SimpleBooleanEncodedValue("tmp_access", true);
        EncodingManager tmpEM = new EncodingManager.Builder().add(tmpAccessEnc).add(Roundabout.create()).build();
        ModeAccessParser tmpParser = new ModeAccessParser(TransportationMode.CAR, tmpAccessEnc, true,
                tmpEM.getBooleanEncodedValue(Roundabout.KEY), Set.of(), Set.of("kissing_gate"));

        way = new ReaderWay(1);
        way.setTag("highway", "secondary");
        way.setTag("gh:barrier_edge", true);

        way.setTag("node_tags", List.of(Map.of("barrier", "bollard"), Map.of()));
        access = new ArrayEdgeIntAccess(1);
        tmpParser.handleWayTags(edgeId, access, way, null);
        assertTrue(tmpAccessEnc.getBool(false, edgeId, access));
    }

    @Test
    public void testPsvYes() {
        EdgeIntAccess access = new ArrayEdgeIntAccess(1);
        ReaderWay way = new ReaderWay(0);
        way.setTag("motor_vehicle", "no");
        way.setTag("highway", "tertiary");
        int edgeId = 0;
        parser.handleWayTags(edgeId, access, way, null);
        assertFalse(busAccessEnc.getBool(false, edgeId, access));

        access = new ArrayEdgeIntAccess(1);
        way.setTag("psv", "yes");
        parser.handleWayTags(edgeId, access, way, null);
        assertTrue(busAccessEnc.getBool(false, edgeId, access));

        access = new ArrayEdgeIntAccess(1);
        way.setTag("psv", "yes");
        parser.handleWayTags(edgeId, access, way, null);
        assertTrue(busAccessEnc.getBool(false, edgeId, access));
    }

    @Test
    public void testMotorcycleYes() {
        BooleanEncodedValue mcAccessEnc = new SimpleBooleanEncodedValue("motorcycle_access", true);
        EncodingManager mcEM = new EncodingManager.Builder().add(mcAccessEnc).add(Roundabout.create()).build();
        ModeAccessParser mcParser = new ModeAccessParser(TransportationMode.MOTORCYCLE, mcAccessEnc, true,
                mcEM.getBooleanEncodedValue(Roundabout.KEY), Set.of(), Set.of());

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

    @Test
    public void temporalAccess() {
        int edgeId = 0;
        ArrayEdgeIntAccess access = new ArrayEdgeIntAccess(1);
        ReaderWay way = new ReaderWay(1);
        way.setTag("highway", "primary");
        way.setTag("access:conditional", "no @ (May - June)");
        parser.handleWayTags(edgeId, access, way, null);
        assertTrue(busAccessEnc.getBool(false, edgeId, access));

        access = new ArrayEdgeIntAccess(1);
        way = new ReaderWay(1);
        way.setTag("highway", "primary");
        way.setTag("psv:conditional", "no @ (May - June)");
        parser.handleWayTags(edgeId, access, way, null);
        assertTrue(busAccessEnc.getBool(false, edgeId, access));

        access = new ArrayEdgeIntAccess(1);
        way = new ReaderWay(1);
        way.setTag("highway", "primary");
        way.setTag("psv", "no");
        way.setTag("access:conditional", "yes @ (May - June)");
        parser.handleWayTags(edgeId, access, way, null);
        assertFalse(busAccessEnc.getBool(false, edgeId, access));

        access = new ArrayEdgeIntAccess(1);
        way = new ReaderWay(1);
        way.setTag("highway", "primary");
        way.setTag("access", "no");
        way.setTag("psv:conditional", "yes @ (May - June)");
        parser.handleWayTags(edgeId, access, way, null);
        assertTrue(busAccessEnc.getBool(false, edgeId, access));
    }
}
