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
        way.setTag("moped", "destination;delivery");
        assertTrue(forward(parser, way));
        way.setTag("moped", "private");
        assertFalse(forward(parser, way));

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

        // the implied moped=no is lifted by a temporal conditional of moped only
        way.clearTags();
        way.setTag("highway", "pedestrian");
        way.setTag("motor_vehicle:conditional", "yes @ (06:00-10:00)");
        assertFalse(forward(parser, way));
        way.setTag("moped:conditional", "yes @ (06:00-10:00)");
        assertTrue(forward(parser, way));

        // goods is not a restriction key
        way.clearTags();
        way.setTag("highway", "residential");
        way.setTag("goods", "no");
        assertTrue(forward(parser, way));
        way.setTag("goods", "yes");
        way.setTag("motor_vehicle", "no");
        assertFalse(forward(parser, way));

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

        // without access tags open, unless foot or bicycle is tagged
        way.clearTags();
        way.setTag("route", "ferry");
        assertTrue(forward(parser, way));
        way.setTag("foot", "yes");
        assertFalse(forward(parser, way));
        // a ferry for cars or trucks takes scooters too, unless restricted
        way.setTag("motorcar", "yes");
        assertTrue(forward(parser, way));
        assertFalse(forward(TruckVehicles.create(em, "truck", new PMap()), way));
        way.removeTag("motorcar");
        way.setTag("hgv", "yes");
        assertTrue(forward(parser, way));
        way.setTag("motorcar", "no");
        assertFalse(forward(parser, way));
        way.setTag("motorcar", "yes");
        way.setTag("motor_vehicle", "no");
        assertFalse(forward(parser, way));
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
