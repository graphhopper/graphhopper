package com.graphhopper.routing.util;

import com.graphhopper.reader.ReaderNode;
import com.graphhopper.reader.ReaderWay;
import com.graphhopper.reader.osm.conditional.DateRangeParser;
import com.graphhopper.routing.ev.*;
import com.graphhopper.storage.IntsRef;
import com.graphhopper.util.PMap;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class BusTagParserTest {
    private static final String BUS_NAME = "bus";

    private final EncodingManager em = EncodingManager.create(BUS_NAME);
    private final BusTagParser parser = createParser(em);
    private final DecimalEncodedValue avSpeedEnc = parser.getAverageSpeedEnc();

    private EncodingManager createEncodingManager(String busName) {
        return new EncodingManager.Builder()
                .add(VehicleAccess.create(busName))
                .add(VehicleSpeed.create(busName, 5, 5, true))
                .addTurnCostEncodedValue(TurnCost.create(busName, 1))
                .add(VehicleAccess.create("bike"))
                .add(VehicleSpeed.create("bike", 4, 2, false))
                .add(VehiclePriority.create("bike", 4, PriorityCode.getFactor(1), false))
                .add(new EnumEncodedValue<>(BikeNetwork.KEY, RouteNetwork.class))
                .add(new EnumEncodedValue<>(Smoothness.KEY, Smoothness.class))
                .build();
    }

    private BusTagParser createParser(EncodedValueLookup lookup) {
        BusTagParser busTagParser = new BusTagParser(lookup, new PMap());
        busTagParser.init(new DateRangeParser());
        return busTagParser;
    }

    @Test
    public void testAccess() {
        ReaderWay way = new ReaderWay(1);
        assertTrue(parser.getAccess(way).canSkip());
        way.setTag("highway", "service");
        assertTrue(parser.getAccess(way).isWay());
        way.setTag("access", "no");
        assertFalse(parser.getAccess(way).canSkip());

        way.clearTags();
        way.setTag("highway", "track");
        assertTrue(parser.getAccess(way).isWay());

        way.setTag("motorcar", "no");
        assertFalse(parser.getAccess(way).canSkip());

        way.clearTags();
        way.setTag("highway", "track");
        way.setTag("tracktype", "grade2");
        assertTrue(parser.getAccess(way).isWay());
        way.setTag("tracktype", "grade4");
        assertTrue(parser.getAccess(way).canSkip());

        way.clearTags();
        way.setTag("highway", "service");
        way.setTag("access", "delivery");
        assertTrue(parser.getAccess(way).canSkip());

        way.clearTags();
        way.setTag("highway", "unclassified");
        way.setTag("ford", "yes");
        assertFalse(parser.getAccess(way).canSkip());
        way.setTag("motorcar", "yes");
        assertTrue(parser.getAccess(way).isWay());

        way.clearTags();
        way.setTag("access", "yes");
        way.setTag("motor_vehicle", "no");
        assertTrue(parser.getAccess(way).canSkip());

        way.clearTags();
        way.setTag("highway", "service");
        way.setTag("access", "yes");
        way.setTag("motor_vehicle", "no");
        assertFalse(parser.getAccess(way).canSkip());

        way.clearTags();
        way.setTag("highway", "track");
        way.setTag("motor_vehicle", "agricultural");
        assertTrue(parser.getAccess(way).canSkip());
        way.setTag("motor_vehicle", "agricultural;forestry");
        assertTrue(parser.getAccess(way).canSkip());
        way.setTag("motor_vehicle", "forestry;agricultural");
        assertTrue(parser.getAccess(way).canSkip());
        way.setTag("motor_vehicle", "forestry;agricultural;unknown");
        assertTrue(parser.getAccess(way).canSkip());
        way.setTag("motor_vehicle", "yes;forestry;agricultural");
        assertTrue(parser.getAccess(way).isWay());

        way.clearTags();
        way.setTag("highway", "service");
        way.setTag("access", "no");
        way.setTag("motorcar", "yes");
        assertTrue(parser.getAccess(way).isWay());

        way.clearTags();
        way.setTag("highway", "service");
        way.setTag("access", "emergency");
        assertTrue(parser.getAccess(way).canSkip());
        way.setTag("access", "private");
        assertFalse(parser.getAccess(way).canSkip());

        way.clearTags();
        way.setTag("highway", "service");
        way.setTag("motor_vehicle", "emergency");
        assertTrue(parser.getAccess(way).canSkip());

        way.clearTags();
        way.setTag("highway", "service");
        way.setTag("service", "emergency_access");
        assertTrue(parser.getAccess(way).canSkip());
    }

    @Test
    public void testBarrierAccess() {
        ReaderNode node = new ReaderNode(1, -1, -1);
        node.setTag("barrier", "lift_gate");
        node.setTag("access", "yes");
        assertFalse(parser.isBarrier(node));

        node = new ReaderNode(1, -1, -1);
        node.setTag("barrier", "lift_gate");
        node.setTag("bicycle", "yes");
        assertFalse(parser.isBarrier(node));

        node = new ReaderNode(1, -1, -1);
        node.setTag("barrier", "lift_gate");
        node.setTag("access", "no");
        node.setTag("motorcar", "yes");
        assertFalse(parser.isBarrier(node));

        node = new ReaderNode(1, -1, -1);
        node.setTag("barrier", "bollard");
        assertTrue(parser.isBarrier(node));

        node = new ReaderNode(1, -1, -1);
        node.setTag("barrier", "cattle_grid");
        assertFalse(parser.isBarrier(node));

        node = new ReaderNode(1, -1, -1);
        node.setTag("barrier", "bus_trap");
        assertFalse(parser.isBarrier(node));

        node = new ReaderNode(1, -1, -1);
        node.setTag("barrier", "sump_buster");
        assertFalse(parser.isBarrier(node));
    }

    @Test
    public void testMaxSpeed() {
        ReaderWay way = new ReaderWay(1);
        way.setTag("highway", "trunk");
        way.setTag("maxspeed", "500");
        IntsRef edgeFlags = em.createEdgeFlags();
        parser.handleWayTags(edgeFlags, way);
        assertEquals(60, avSpeedEnc.getDecimal(false, edgeFlags), 1e-1);

        way = new ReaderWay(1);
        way.setTag("highway", "primary");
        way.setTag("maxspeed:backward", "10");
        way.setTag("maxspeed:forward", "20");
        edgeFlags = parser.handleWayTags(edgeFlags, way);
        assertEquals(10, avSpeedEnc.getDecimal(false, edgeFlags), 1e-1);

        way = new ReaderWay(1);
        way.setTag("highway", "primary");
        way.setTag("maxspeed:forward", "20");
        edgeFlags = parser.handleWayTags(edgeFlags, way);
        assertEquals(20, avSpeedEnc.getDecimal(false, edgeFlags), 1e-1);

        way = new ReaderWay(1);
        way.setTag("highway", "primary");
        way.setTag("maxspeed:backward", "20");
        edgeFlags = parser.handleWayTags(edgeFlags, way);
        assertEquals(20, avSpeedEnc.getDecimal(false, edgeFlags), 1e-1);

        way = new ReaderWay(1);
        way.setTag("highway", "motorway");
        way.setTag("maxspeed", "none");
        edgeFlags = parser.handleWayTags(edgeFlags, way);
        assertEquals(60, avSpeedEnc.getDecimal(false, edgeFlags), .1);

        way = new ReaderWay(1);
        way.setTag("highway", "motorway_link");
        way.setTag("maxspeed", "70 mph");
        IntsRef flags = parser.handleWayTags(em.createEdgeFlags(), way);
        assertEquals(60, avSpeedEnc.getDecimal(true, flags), 1e-1);
    }
}