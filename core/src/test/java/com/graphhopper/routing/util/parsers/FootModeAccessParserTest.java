package com.graphhopper.routing.util.parsers;

import com.graphhopper.reader.ReaderWay;
import com.graphhopper.routing.ev.*;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.routing.util.TransportationMode;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FootModeAccessParserTest {

    private final EncodingManager em = new EncodingManager.Builder().add(Roundabout.create()).add(new SimpleBooleanEncodedValue("foot_access", true)).build();
    private final ModeAccessParser parser = new ModeAccessParser(OSMRoadAccessParser.toOSMRestrictions(TransportationMode.FOOT),
            em.getBooleanEncodedValue("foot_access"), false,
            em.getBooleanEncodedValue(Roundabout.KEY));
    private final BooleanEncodedValue footAccessEnc = em.getBooleanEncodedValue("foot_access");

    @Test
    void testFootwayAndPath() {
        // footway implies motor_vehicle=no, but foot keys are [foot, access] — neither is restricted
        int edgeId = 0;
        for (String hw : List.of("footway", "path", "pedestrian", "bridleway")) {
            ArrayEdgeIntAccess access = new ArrayEdgeIntAccess(1);
            ReaderWay way = new ReaderWay(1);
            way.setTag("highway", hw);
            parser.handleWayTags(edgeId, access, way, null);
            assertTrue(footAccessEnc.getBool(false, edgeId, access), "foot should be allowed on " + hw);
        }
    }

    @Test
    void testSteps() {
        // steps implies foot=designated — accessible for foot
        int edgeId = 0;
        ArrayEdgeIntAccess access = new ArrayEdgeIntAccess(1);
        ReaderWay way = new ReaderWay(1);
        way.setTag("highway", "steps");
        parser.handleWayTags(edgeId, access, way, null);
        assertTrue(footAccessEnc.getBool(false, edgeId, access));

        // explicit foot=no overrides the implied foot=designated
        access = new ArrayEdgeIntAccess(1);
        way.setTag("foot", "no");
        parser.handleWayTags(edgeId, access, way, null);
        assertFalse(footAccessEnc.getBool(false, edgeId, access));
    }

    @Test
    void testBusway() {
        // busway implies access=no, bus=designated — foot blocked (access=no hits foot's keys)
        int edgeId = 0;
        ArrayEdgeIntAccess access = new ArrayEdgeIntAccess(1);
        ReaderWay way = new ReaderWay(1);
        way.setTag("highway", "busway");
        parser.handleWayTags(edgeId, access, way, null);
        assertFalse(footAccessEnc.getBool(false, edgeId, access));
    }

    @Test
    void testCycleway() {
        // cycleway implies foot=no — blocked for foot by default
        int edgeId = 0;
        ArrayEdgeIntAccess access = new ArrayEdgeIntAccess(1);
        ReaderWay way = new ReaderWay(1);
        way.setTag("highway", "cycleway");
        parser.handleWayTags(edgeId, access, way, null);
        assertFalse(footAccessEnc.getBool(false, edgeId, access));

        // explicit foot=yes overrides
        access = new ArrayEdgeIntAccess(1);
        way.setTag("foot", "yes");
        parser.handleWayTags(edgeId, access, way, null);
        assertTrue(footAccessEnc.getBool(false, edgeId, access));
    }

    @Test
    void testExplicitFootNo() {
        int edgeId = 0;
        ArrayEdgeIntAccess access = new ArrayEdgeIntAccess(1);
        ReaderWay way = new ReaderWay(1);
        way.setTag("highway", "residential");
        way.setTag("foot", "no");
        parser.handleWayTags(edgeId, access, way, null);
        assertFalse(footAccessEnc.getBool(false, edgeId, access));
    }

    @Test
    void testBollard() {
        // bollard implies motor_vehicle=no, foot=yes — foot walks through
        int edgeId = 0;
        ArrayEdgeIntAccess access = new ArrayEdgeIntAccess(1);
        ReaderWay way = new ReaderWay(1);
        way.setTag("highway", "residential");
        way.setTag("gh:barrier_edge", true);
        way.setTag("node_tags", List.of(Map.of("barrier", "bollard"), Map.of()));
        parser.handleWayTags(edgeId, access, way, null);
        assertTrue(footAccessEnc.getBool(false, edgeId, access));
    }

    @Test
    void testKissingGate() {
        // kissing_gate implies vehicle=no, foot=yes — foot passes, vehicles don't
        int edgeId = 0;
        ArrayEdgeIntAccess access = new ArrayEdgeIntAccess(1);
        ReaderWay way = new ReaderWay(1);
        way.setTag("highway", "footway");
        way.setTag("gh:barrier_edge", true);
        way.setTag("node_tags", List.of(Map.of("barrier", "kissing_gate"), Map.of()));
        parser.handleWayTags(edgeId, access, way, null);
        assertTrue(footAccessEnc.getBool(false, edgeId, access));
    }

    @Test
    void testFence() {
        // fence implies access=no — blocks everyone including foot
        int edgeId = 0;
        ArrayEdgeIntAccess access = new ArrayEdgeIntAccess(1);
        ReaderWay way = new ReaderWay(1);
        way.setTag("highway", "footway");
        way.setTag("gh:barrier_edge", true);
        way.setTag("node_tags", List.of(Map.of("barrier", "fence"), Map.of()));
        parser.handleWayTags(edgeId, access, way, null);
        assertFalse(footAccessEnc.getBool(false, edgeId, access));

        // explicit foot=yes on the node overrides the implied access=no
        access = new ArrayEdgeIntAccess(1);
        way.setTag("node_tags", List.of(Map.of("barrier", "fence", "foot", "yes"), Map.of()));
        parser.handleWayTags(edgeId, access, way, null);
        assertTrue(footAccessEnc.getBool(false, edgeId, access));
    }

    @Test
    void testBusTrap() {
        // bus_trap implies motor_vehicle=no, bus=yes, foot=yes — foot passes
        int edgeId = 0;
        ArrayEdgeIntAccess access = new ArrayEdgeIntAccess(1);
        ReaderWay way = new ReaderWay(1);
        way.setTag("highway", "residential");
        way.setTag("gh:barrier_edge", true);
        way.setTag("node_tags", List.of(Map.of("barrier", "bus_trap"), Map.of()));
        parser.handleWayTags(edgeId, access, way, null);
        assertTrue(footAccessEnc.getBool(false, edgeId, access));
    }

    @Test
    void testGate() {
        // gate has no defaults — no restriction found, foot passes
        int edgeId = 0;
        ArrayEdgeIntAccess access = new ArrayEdgeIntAccess(1);
        ReaderWay way = new ReaderWay(1);
        way.setTag("highway", "footway");
        way.setTag("gh:barrier_edge", true);
        way.setTag("node_tags", List.of(Map.of("barrier", "gate"), Map.of()));
        parser.handleWayTags(edgeId, access, way, null);
        assertTrue(footAccessEnc.getBool(false, edgeId, access));
    }


    @Test
    public void motorwayImpliesOneway() {
        int edgeId = 0;
        ArrayEdgeIntAccess access = new ArrayEdgeIntAccess(1);
        ReaderWay way = new ReaderWay(1);
        way.setTag("highway", "motorway");
        parser.handleWayTags(edgeId, access, way, null);
        assertFalse(footAccessEnc.getBool(false, edgeId, access));
        assertFalse(footAccessEnc.getBool(true, edgeId, access));
    }

}
