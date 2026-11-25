package com.graphhopper.routing.util.parsers;

import com.graphhopper.reader.ReaderWay;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.util.PMap;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static com.graphhopper.routing.util.parsers.TruckVehicles.forward;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Ported from the matrix ScooterTagParserTest
 */
class ScooterModeAccessParserTest {
    private final EncodingManager em = TruckVehicles.createEncodingManager();
    private final ModeAccessParser parser = TruckVehicles.create(em, "scooter", new PMap());

    @Test
    public void testAccess() {
        ReaderWay way = new ReaderWay(1);
        way.setTag("highway", "primary");
        assertTrue(forward(parser, way));

        way.clearTags();
        way.setTag("highway", "primary");
        way.setTag("motorcycle", "no");
        assertFalse(forward(parser, way));

        way.clearTags();
        way.setTag("highway", "primary");
        way.setTag("moped", "no");
        assertFalse(forward(parser, way));

        way.clearTags();
        way.setTag("highway", "primary");
        way.setTag("mofa", "no");
        assertTrue(forward(parser, way));

        way.clearTags();
        way.setTag("highway", "primary");
        way.setTag("motorroad", "yes");
        assertFalse(forward(parser, way));

        way.clearTags();
        way.setTag("highway", "trunk");
        way.setTag("minspeed", "40 mph");
        assertFalse(forward(parser, way));
        way.setTag("minspeed", "30");
        assertTrue(forward(parser, way));

        way.clearTags();
        way.setTag("highway", "trunk");
        way.setTag("motorroad", "yes");
        way.setTag("moped", "yes");
        assertTrue(forward(parser, way));

        way.clearTags();
        way.setTag("highway", "motorway");
        assertFalse(forward(parser, way));
        way.setTag("motorcycle", "yes");
        assertFalse(forward(parser, way));
        way.setTag("moped", "yes");
        assertTrue(forward(parser, way));

        // Rather unrealistic tagging, but make sure that we prioritize moped
        way.clearTags();
        way.setTag("highway", "unclassified");
        way.setTag("access", "no");
        way.setTag("motor_vehicle", "no");
        way.setTag("motorcycle", "no");
        way.setTag("moped", "yes");
        assertTrue(forward(parser, way));

        way.clearTags();
        way.setTag("highway", "pedestrian");
        assertFalse(forward(parser, way));

        way.clearTags();
        way.setTag("highway", "pedestrian");
        way.setTag("moped", "yes");
        assertTrue(forward(parser, way));
        way.setTag("moped", "unknown");
        assertFalse(forward(parser, way));

        // only moped opens it, goods (the first key) does not
        way.clearTags();
        way.setTag("highway", "pedestrian");
        way.setTag("goods", "yes");
        assertFalse(forward(parser, way));
        way.setTag("highway", "residential");
        way.setTag("motor_vehicle", "no");
        assertTrue(forward(parser, way));

        way.clearTags();
        way.setTag("highway", "cycleway");
        way.setTag("moped", "yes");
        assertTrue(forward(parser, way));

        way.clearTags();
        way.setTag("highway", "cycleway");
        way.setTag("moped", "dismount");
        assertTrue(forward(parser, way));

        // footways stay blocked, even with moped=yes
        way.clearTags();
        way.setTag("highway", "footway");
        way.setTag("moped", "yes");
        assertFalse(forward(parser, way));
    }

    @Test
    public void testFerry() {
        ReaderWay way = new ReaderWay(1);
        way.setTag("route", "ferry");
        way.setTag("moped", "yes");
        assertTrue(forward(parser, way));

        way.setTag("motor_vehicle", "no");
        assertTrue(forward(parser, way));
    }

    @Test
    public void testBarrier() {
        ReaderWay way = new ReaderWay(1);
        way.setTag("highway", "residential");
        way.setTag("gh:barrier_edge", true);
        way.setTag("node_tags", List.of(Map.of("barrier", "bollard"), Map.of()));
        assertTrue(forward(parser, way));
        way.setTag("node_tags", List.of(Map.of("barrier", "bollard", "access", "no"), Map.of()));
        assertFalse(forward(parser, way));
        way.setTag("node_tags", List.of(Map.of("barrier", "cycle_barrier"), Map.of()));
        assertFalse(forward(parser, way));
    }
}
