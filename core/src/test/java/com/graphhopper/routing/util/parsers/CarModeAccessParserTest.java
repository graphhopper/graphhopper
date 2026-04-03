package com.graphhopper.routing.util.parsers;

import com.graphhopper.reader.ReaderWay;
import com.graphhopper.routing.ev.*;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.routing.util.TransportationMode;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CarModeAccessParserTest {

    private final EncodingManager em = new EncodingManager.Builder().add(Roundabout.create()).add(new SimpleBooleanEncodedValue("car_access", true)).build();
    private final ModeAccessParser parser = new ModeAccessParser(OSMRoadAccessParser.toOSMRestrictions(TransportationMode.CAR),
            em.getBooleanEncodedValue("car_access"), true,
            em.getBooleanEncodedValue(Roundabout.KEY), Set.of(), Set.of());
    private final BooleanEncodedValue carAccessEnc = em.getBooleanEncodedValue("car_access");

    @Test
    public void testAccess() {
        ReaderWay way = new ReaderWay(1);
        way.setTag("highway", "primary");
        EdgeIntAccess edgeIntAccess = ArrayEdgeIntAccess.createFromBytes(em.getBytesForFlags());
        int edgeId = 0;
        parser.handleWayTags(edgeId, edgeIntAccess, way, null);
        assertTrue(carAccessEnc.getBool(false, edgeId, edgeIntAccess));
        assertTrue(carAccessEnc.getBool(true, edgeId, edgeIntAccess));
    }

    @Test
    public void testPrivate() {
        ReaderWay way = new ReaderWay(1);
        way.setTag("highway", "primary");
        way.setTag("access", "private");
        EdgeIntAccess edgeIntAccess = ArrayEdgeIntAccess.createFromBytes(em.getBytesForFlags());
        int edgeId = 0;
        parser.handleWayTags(edgeId, edgeIntAccess, way, null);
        assertTrue(carAccessEnc.getBool(false, edgeId, edgeIntAccess));
        assertTrue(carAccessEnc.getBool(true, edgeId, edgeIntAccess));
    }

    @Test
    public void testOneway() {
        ReaderWay way = new ReaderWay(1);
        way.setTag("highway", "primary");
        way.setTag("oneway", "yes");

        int edgeId = 0;
        EdgeIntAccess edgeIntAccess = ArrayEdgeIntAccess.createFromBytes(em.getBytesForFlags());
        parser.handleWayTags(edgeId, edgeIntAccess, way, null);
        assertTrue(carAccessEnc.getBool(false, edgeId, edgeIntAccess));
        assertFalse(carAccessEnc.getBool(true, edgeId, edgeIntAccess));

        way.clearTags();
        way.setTag("highway", "tertiary");
        way.setTag("vehicle:forward", "no");
        edgeIntAccess = ArrayEdgeIntAccess.createFromBytes(em.getBytesForFlags());
        parser.handleWayTags(edgeId, edgeIntAccess, way, null);
        assertFalse(carAccessEnc.getBool(false, edgeId, edgeIntAccess));
        assertTrue(carAccessEnc.getBool(true, edgeId, edgeIntAccess));

        way.clearTags();
        way.setTag("highway", "tertiary");
        way.setTag("vehicle:backward", "no");
        edgeIntAccess = ArrayEdgeIntAccess.createFromBytes(em.getBytesForFlags());
        parser.handleWayTags(edgeId, edgeIntAccess, way, null);
        assertTrue(carAccessEnc.getBool(false, edgeId, edgeIntAccess));
        assertFalse(carAccessEnc.getBool(true, edgeId, edgeIntAccess));
    }

    @Test
    public void testBusDesignated() {
        EdgeIntAccess access = new ArrayEdgeIntAccess(1);
        ReaderWay way = new ReaderWay(0);
        way.setTag("highway", "tertiary");
        int edgeId = 0;
        parser.handleWayTags(edgeId, access, way, null);
        assertTrue(carAccessEnc.getBool(false, edgeId, access));

        access = new ArrayEdgeIntAccess(1);
        way.setTag("bus", "designated");
        parser.handleWayTags(edgeId, access, way, null);
        assertTrue(carAccessEnc.getBool(false, edgeId, access));
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
        assertFalse(carAccessEnc.getBool(false, edgeId, access));

        way.setTag("node_tags", Arrays.asList(Map.of("barrier", "gate"), Map.of()));
        access = new ArrayEdgeIntAccess(1);
        parser.handleWayTags(edgeId, access, way, null);
        assertTrue(carAccessEnc.getBool(false, edgeId, access));

        // this special mode ignores all barriers except kissing_gate
        BooleanEncodedValue tmpAccessEnc = new SimpleBooleanEncodedValue("tmp_access", true);
        EncodingManager tmpEM = new EncodingManager.Builder().add(tmpAccessEnc).add(Roundabout.create()).build();
        ModeAccessParser tmpParser = new ModeAccessParser(OSMRoadAccessParser.toOSMRestrictions(TransportationMode.CAR),
                tmpAccessEnc, true,
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
    public void testBarrierWithBusYes() {
        ReaderWay way = new ReaderWay(1);
        way.setTag("highway", "secondary");
        way.setTag("gh:barrier_edge", true);

        way.setTag("node_tags", List.of(Map.of("barrier", "bollard", "access", "no", "motor_vehicle", "permit", "bus", "yes"), Map.of()));
        EdgeIntAccess access = new ArrayEdgeIntAccess(1);
        int edgeId = 0;
        parser.handleWayTags(edgeId, access, way, null);
        assertTrue(carAccessEnc.getBool(false, edgeId, access));
    }

    @Test
    public void testBarrierBusYesDoesNotOverrideWayRestriction() {
        // bus=yes on a barrier node must not unblock a way that itself restricts bus access
        ReaderWay way = new ReaderWay(1);
        way.setTag("highway", "secondary");
        way.setTag("bus", "no");
        way.setTag("gh:barrier_edge", true);
        way.setTag("node_tags", List.of(Map.of("barrier", "bollard", "bus", "yes"), Map.of()));
        EdgeIntAccess access = new ArrayEdgeIntAccess(1);
        int edgeId = 0;
        parser.handleWayTags(edgeId, access, way, null);
        assertFalse(carAccessEnc.getBool(false, edgeId, access));
    }

    @Test
    public void testPsvYes() {
        EdgeIntAccess access = new ArrayEdgeIntAccess(1);
        ReaderWay way = new ReaderWay(0);
        way.setTag("motor_vehicle", "no");
        way.setTag("highway", "tertiary");
        int edgeId = 0;
        parser.handleWayTags(edgeId, access, way, null);
        assertFalse(carAccessEnc.getBool(false, edgeId, access));

        access = new ArrayEdgeIntAccess(1);
        way.setTag("psv", "yes");
        parser.handleWayTags(edgeId, access, way, null);
        assertFalse(carAccessEnc.getBool(false, edgeId, access));
    }

    @Test
    public void testMotorcycleYes() {
        BooleanEncodedValue mcAccessEnc = new SimpleBooleanEncodedValue("motorcycle_access", true);
        EncodingManager mcEM = new EncodingManager.Builder().add(mcAccessEnc).add(Roundabout.create()).build();
        ModeAccessParser mcParser = new ModeAccessParser(OSMRoadAccessParser.toOSMRestrictions(TransportationMode.MOTORCYCLE),
                mcAccessEnc, true,
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
    public void testHov() {
        BooleanEncodedValue hovAccessEnc = new SimpleBooleanEncodedValue("hov_access", true);
        EncodingManager hovEM = new EncodingManager.Builder().add(hovAccessEnc).add(Roundabout.create()).build();
        ModeAccessParser hovParser = new ModeAccessParser(OSMRoadAccessParser.toOSMRestrictions(TransportationMode.HOV),
                hovAccessEnc, true,
                hovEM.getBooleanEncodedValue(Roundabout.KEY), Set.of(), Set.of());

        int edgeId = 0;

        // normal road: accessible
        EdgeIntAccess access = new ArrayEdgeIntAccess(1);
        ReaderWay way = new ReaderWay(0);
        way.setTag("highway", "primary");
        hovParser.handleWayTags(edgeId, access, way, null);
        assertTrue(hovAccessEnc.getBool(false, edgeId, access));

        // footway: blocked via implied motor_vehicle=no
        access = new ArrayEdgeIntAccess(1);
        way = new ReaderWay(0);
        way.setTag("highway", "footway");
        hovParser.handleWayTags(edgeId, access, way, null);
        assertFalse(hovAccessEnc.getBool(false, edgeId, access));

        // busway: blocked via implied access=no (hov is not bus)
        access = new ArrayEdgeIntAccess(1);
        way = new ReaderWay(0);
        way.setTag("highway", "busway");
        hovParser.handleWayTags(edgeId, access, way, null);
        assertFalse(hovAccessEnc.getBool(false, edgeId, access));

        // motor_vehicle=no but hov=designated: accessible
        access = new ArrayEdgeIntAccess(1);
        way = new ReaderWay(0);
        way.setTag("highway", "tertiary");
        way.setTag("motor_vehicle", "no");
        way.setTag("hov", "designated");
        hovParser.handleWayTags(edgeId, access, way, null);
        assertTrue(hovAccessEnc.getBool(false, edgeId, access));

        // bus_trap: blocked (hov vehicles are not buses)
        access = new ArrayEdgeIntAccess(1);
        way = new ReaderWay(0);
        way.setTag("highway", "residential");
        way.setTag("gh:barrier_edge", true);
        way.setTag("node_tags", List.of(Map.of("barrier", "bus_trap"), Map.of()));
        hovParser.handleWayTags(edgeId, access, way, null);
        assertFalse(hovAccessEnc.getBool(false, edgeId, access));
    }

    @Test
    public void temporalAccess() {
        int edgeId = 0;
        ArrayEdgeIntAccess access = new ArrayEdgeIntAccess(1);
        ReaderWay way = new ReaderWay(1);
        way.setTag("highway", "primary");
        way.setTag("access:conditional", "no @ (May - June)");
        parser.handleWayTags(edgeId, access, way, null);
        assertTrue(carAccessEnc.getBool(false, edgeId, access));

        access = new ArrayEdgeIntAccess(1);
        way = new ReaderWay(1);
        way.setTag("highway", "primary");
        way.setTag("motor_vehicle:conditional", "no @ (May - June)");
        parser.handleWayTags(edgeId, access, way, null);
        assertTrue(carAccessEnc.getBool(false, edgeId, access));

        access = new ArrayEdgeIntAccess(1);
        way = new ReaderWay(1);
        way.setTag("highway", "primary");
        way.setTag("motor_vehicle", "no");
        way.setTag("access:conditional", "yes @ (May - June)");
        parser.handleWayTags(edgeId, access, way, null);
        assertFalse(carAccessEnc.getBool(false, edgeId, access));

        access = new ArrayEdgeIntAccess(1);
        way = new ReaderWay(1);
        way.setTag("highway", "primary");
        way.setTag("access", "no");
        way.setTag("motor_vehicle:conditional", "yes @ (May - June)");
        parser.handleWayTags(edgeId, access, way, null);
        assertTrue(carAccessEnc.getBool(false, edgeId, access));
    }

    @Test
    void testPedestrianAccess() {
        int edgeId = 0;
        ArrayEdgeIntAccess access = new ArrayEdgeIntAccess(1);
        ReaderWay way = new ReaderWay(1);
        way.setTag("highway", "pedestrian");
        parser.handleWayTags(edgeId, access, way, null);
        assertFalse(carAccessEnc.getBool(false, edgeId, access));

        way.clearTags();
        access = new ArrayEdgeIntAccess(1);
        way.setTag("highway", "pedestrian");
        way.setTag("motor_vehicle", "no");
        parser.handleWayTags(edgeId, access, way, null);
        assertFalse(carAccessEnc.getBool(false, edgeId, access));

        way.clearTags();
        access = new ArrayEdgeIntAccess(1);
        way.setTag("highway", "pedestrian");
        way.setTag("motor_vehicle", "yes");
        parser.handleWayTags(edgeId, access, way, null);
        assertTrue(carAccessEnc.getBool(false, edgeId, access));

        way.clearTags();
        access = new ArrayEdgeIntAccess(1);
        way.setTag("highway", "pedestrian");
        way.setTag("motor_vehicle:conditional", "yes @ ( 8:00 - 10:00 )");
        parser.handleWayTags(edgeId, access, way, null);
        assertFalse(carAccessEnc.getBool(false, edgeId, access));
    }

}
