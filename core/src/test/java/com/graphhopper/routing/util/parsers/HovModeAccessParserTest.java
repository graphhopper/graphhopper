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

class HovModeAccessParserTest {

    private final EncodingManager em = new EncodingManager.Builder().add(Roundabout.create()).add(BusAccess.create()).build();
    private final ModeAccessParser parser = new ModeAccessParser(OSMRoadAccessParser.toOSMRestrictions(TransportationMode.HOV),
            em.getBooleanEncodedValue(BusAccess.KEY), true,
            em.getBooleanEncodedValue(Roundabout.KEY));
    private final BooleanEncodedValue hovAccessEnc = em.getBooleanEncodedValue(BusAccess.KEY);

    @Test
    public void testHov() {
        int edgeId = 0;

        // normal road: accessible
        EdgeIntAccess access = new ArrayEdgeIntAccess(1);
        ReaderWay way = new ReaderWay(0);
        way.setTag("highway", "primary");
        parser.handleWayTags(edgeId, access, way, null);
        assertTrue(hovAccessEnc.getBool(false, edgeId, access));

        // footway: blocked via implied motor_vehicle=no
        access = new ArrayEdgeIntAccess(1);
        way = new ReaderWay(0);
        way.setTag("highway", "footway");
        parser.handleWayTags(edgeId, access, way, null);
        assertFalse(hovAccessEnc.getBool(false, edgeId, access));

        // busway: blocked via implied access=no (hov is not bus)
        access = new ArrayEdgeIntAccess(1);
        way = new ReaderWay(0);
        way.setTag("highway", "busway");
        parser.handleWayTags(edgeId, access, way, null);
        assertFalse(hovAccessEnc.getBool(false, edgeId, access));

        // motor_vehicle=no but hov=designated: accessible
        access = new ArrayEdgeIntAccess(1);
        way = new ReaderWay(0);
        way.setTag("highway", "tertiary");
        way.setTag("motor_vehicle", "no");
        way.setTag("hov", "designated");
        parser.handleWayTags(edgeId, access, way, null);
        assertTrue(hovAccessEnc.getBool(false, edgeId, access));

        // bus_trap: blocked (hov vehicles are not buses)
        access = new ArrayEdgeIntAccess(1);
        way = new ReaderWay(0);
        way.setTag("highway", "residential");
        way.setTag("gh:barrier_edge", true);
        way.setTag("node_tags", List.of(Map.of("barrier", "bus_trap"), Map.of()));
        parser.handleWayTags(edgeId, access, way, null);
        assertFalse(hovAccessEnc.getBool(false, edgeId, access));
    }

}
