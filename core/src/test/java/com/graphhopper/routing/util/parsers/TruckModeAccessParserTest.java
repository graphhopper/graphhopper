package com.graphhopper.routing.util.parsers;

import com.graphhopper.reader.ReaderWay;
import com.graphhopper.routing.ev.ArrayEdgeIntAccess;
import com.graphhopper.routing.ev.EdgeIntAccess;
import com.graphhopper.routing.ev.Roundabout;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.util.PMap;
import org.junit.jupiter.api.Test;

import static com.graphhopper.routing.util.parsers.TruckVehicles.backward;
import static com.graphhopper.routing.util.parsers.TruckVehicles.forward;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Ported from the matrix TruckAccessParserTest, the behaviour spec of car, small_truck, truck and taxi
 */
class TruckModeAccessParserTest {
    private final EncodingManager em = TruckVehicles.createEncodingManager();
    private final ModeAccessParser truckParser = TruckVehicles.create(em, "truck", new PMap());
    private final ModeAccessParser smallTruckParser = TruckVehicles.create(em, "small_truck", new PMap());
    private final ModeAccessParser carParser = TruckVehicles.create(em, "car", new PMap());
    private final ModeAccessParser taxiParser = TruckVehicles.create(em, "taxi", new PMap());

    @Test
    public void testRestrictions() {
        assertEquals("[hgv, goods, motor_vehicle, vehicle, access]", truckParser.getRestrictionKeys().toString());
        assertEquals("[taxi, psv, motorcar, motor_vehicle, vehicle, access]", taxiParser.getRestrictionKeys().toString());
        assertEquals("[motorcar, motor_vehicle, vehicle, access]", carParser.getRestrictionKeys().toString());
        assertEquals("truck_access", truckParser.toString());
    }

    @Test
    public void testTruckAcceptWay() {
        ReaderWay way = new ReaderWay(1);
        way.setTag("highway", "primary");
        assertTrue(forward(truckParser, way));

        way.setTag("maxweight", "16");
        assertFalse(forward(truckParser, way));
        way.setTag("maxweight", "40");
        assertTrue(forward(truckParser, way));

        way.setTag("motor_vehicle", "yes");
        way.setTag("maxweight", "1.5");
        assertFalse(forward(truckParser, way));

        way = new ReaderWay(1);
        way.setTag("highway", "primary");
        way.setTag("maxheight", "2");
        assertFalse(forward(truckParser, way));
        way.setTag("maxheight", "4");
        assertTrue(forward(truckParser, way));
        way.setTag("maxheight", "default");
        assertTrue(forward(truckParser, way));

        way.setTag("bridge", "yes");
        way.setTag("maxheight", "2");
        assertTrue(forward(truckParser, way));
        way.clearTags();

        way.setTag("highway", "primary");
        way.setTag("width", "2.94");
        assertFalse(forward(truckParser, way));
        // there is a small offset added as we need a small difference to maxwidth
        way.setTag("width", "3");
        assertTrue(forward(truckParser, way));
        way.clearTags();

        way.setTag("highway", "primary");
        way.setTag("maxwidth", "2");
        assertFalse(forward(truckParser, way));
        way.setTag("maxwidth", "3.5");
        assertTrue(forward(truckParser, way));
        way.setTag("maxwidth", "6'6\"");
        assertFalse(forward(truckParser, way));
        way.removeTag("maxwidth");

        way.setTag("maxlength", "10");
        assertFalse(forward(truckParser, way));
        way.setTag("maxlength", "15");
        assertTrue(forward(truckParser, way));
        way.removeTag("maxlength");

        way.setTag("maxaxleload", "6");
        assertFalse(forward(truckParser, way));
        way.setTag("maxaxleload", "10");
        assertTrue(forward(truckParser, way));
        way.removeTag("maxaxleload");

        assertTrue(forward(truckParser, way));
        way.setTag("hgv", "no");
        assertFalse(forward(truckParser, way));
        way.removeTag("hgv");

        way.clearTags();
        way.setTag("highway", "track");
        assertFalse(forward(truckParser, way));
        assertTrue(forward(smallTruckParser, way));
        assertTrue(forward(carParser, way));
        way.setTag("tracktype", "grade3");
        assertFalse(forward(truckParser, way));
        assertFalse(forward(smallTruckParser, way));
        assertFalse(forward(carParser, way));
        way.setTag("tracktype", "grade2");
        assertTrue(forward(carParser, way));
        assertFalse(forward(taxiParser, way));
    }

    @Test
    public void testHighwayAndImpassable() {
        ReaderWay way = new ReaderWay(1);
        way.setTag("highway", "footway");
        way.setTag("motor_vehicle", "yes");
        assertFalse(forward(carParser, way));

        way = new ReaderWay(1);
        way.setTag("highway", "primary");
        way.setTag("impassable", "yes");
        assertFalse(forward(carParser, way));
        way.setTag("impassable", "no");
        assertTrue(forward(carParser, way));
        way.setTag("status", "impassable");
        assertFalse(forward(carParser, way));

        way = new ReaderWay(1);
        way.setTag("highway", "service");
        way.setTag("service", "emergency_access");
        assertFalse(forward(carParser, way));

        // no oneway implied by motorway or motorway_link
        way = new ReaderWay(1);
        way.setTag("highway", "motorway_link");
        assertTrue(forward(carParser, way));
        assertTrue(backward(carParser, way));
        way.setTag("highway", "motorway");
        assertTrue(backward(carParser, way));
        way.setTag("oneway", "yes");
        assertFalse(backward(carParser, way));
    }

    @Test
    public void testLegacyOneway() {
        ReaderWay way = new ReaderWay(1);
        way.setTag("highway", "residential");
        way.setTag("vehicle:forward", "no");
        assertFalse(forward(carParser, way));
        assertTrue(backward(carParser, way));
        way.setTag("vehicle:backward", "no");
        // a closed forward direction opens the backward direction, even with oneway=yes
        assertTrue(backward(carParser, way));
        way.setTag("oneway", "yes");
        assertTrue(backward(carParser, way));
        way.removeTag("vehicle:forward");
        assertTrue(forward(carParser, way));
        assertFalse(backward(carParser, way));

        // only the restricted values of vehicle and motor_vehicle count
        way = new ReaderWay(1);
        way.setTag("highway", "track");
        way.setTag("motor_vehicle:forward", "agricultural");
        assertFalse(forward(carParser, way));
        assertTrue(backward(carParser, way));
        way.setTag("motor_vehicle:forward", "private");
        assertTrue(forward(TruckVehicles.create(em, "car", new PMap("block_private=false")), way));
        assertFalse(forward(carParser, way));
        way = new ReaderWay(1);
        way.setTag("highway", "residential");
        way.setTag("motorcar:forward", "no");
        assertTrue(forward(carParser, way));
        way.setTag("oneway:motor_vehicle", "yes");
        assertTrue(backward(carParser, way));

        // roundabouts are oneway, even with oneway=no
        way = new ReaderWay(1);
        way.setTag("highway", "residential");
        way.setTag("junction", "roundabout");
        way.setTag("oneway", "no");
        EdgeIntAccess access = new ArrayEdgeIntAccess(1);
        em.getBooleanEncodedValue(Roundabout.KEY).setBool(false, 0, access, true);
        carParser.handleWayTags(0, access, way, null);
        assertTrue(carParser.getAccessEnc().getBool(false, 0, access));
        assertFalse(carParser.getAccessEnc().getBool(true, 0, access));
    }

    @Test
    public void testAccess() {
        ReaderWay way = new ReaderWay(1);
        way.setTag("highway", "trunk");
        assertTrue(forward(truckParser, way));
        assertTrue(forward(carParser, way));

        way.setTag("access", "no");
        assertFalse(forward(truckParser, way));
        assertFalse(forward(carParser, way));

        way.setTag("hgv", "no");
        assertFalse(forward(truckParser, way));
        assertFalse(forward(carParser, way));

        way.removeTag("access");
        assertFalse(forward(truckParser, way));
        assertTrue(forward(carParser, way));
    }

    @Test
    public void testTruckUnknownDoesNotShadowAccess() {
        ReaderWay way = new ReaderWay(1);
        way.setTag("highway", "primary");
        way.setTag("access", "no");
        way.setTag("motor_vehicle", "somethingunexpected");
        assertFalse(forward(truckParser, way));
    }

    @Test
    public void testPrivate() {
        ReaderWay way = new ReaderWay(1);
        way.setTag("highway", "service");
        way.setTag("access", "private");
        assertFalse(forward(truckParser, way));

        ModeAccessParser truckPermParser = TruckVehicles.create(em, "truck", new PMap("block_private=false"));
        assertTrue(forward(truckPermParser, way));
    }

    @Test
    public void testMotorcar() {
        ReaderWay way = new ReaderWay(1);

        // see #30
        way.clearTags();
        way.setTag("route", "ferry");
        way.setTag("motorcar", "yes");
        way.setTag("foot", "yes");
        way.setTag("hgv", "yes");
        assertTrue(forward(truckParser, way));
        assertTrue(forward(smallTruckParser, way));

        way.clearTags();
        way.setTag("route", "ferry");
        assertTrue(forward(truckParser, way));
        assertTrue(forward(taxiParser, way));
        way.setTag("foot", "yes");
        assertFalse(forward(truckParser, way));
        way.setTag("motor_vehicle", "no");
        way.setTag("hgv", "yes");
        assertFalse(forward(carParser, way));
        assertTrue(forward(truckParser, way));

        way.clearTags();
        way.setTag("highway", "service");
        way.setTag("motorcar", "no");
        assertTrue(forward(truckParser, way));
        assertFalse(forward(carParser, way));

        way.clearTags();
        way.setTag("highway", "service");
        way.setTag("motor_vehicle", "no");
        assertFalse(forward(truckParser, way));
        assertFalse(forward(carParser, way));

        way.clearTags();
        way.setTag("highway", "service");
        way.setTag("access", "no");
        way.setTag("motorcar", "yes");
        assertFalse(forward(truckParser, way));
        assertTrue(forward(carParser, way));

        way.clearTags();
        way.setTag("highway", "service");
        way.setTag("goods", "yes");
        way.setTag("motorcar", "no");
        assertFalse(forward(carParser, way));
        assertTrue(forward(smallTruckParser, way));
        assertTrue(forward(truckParser, way));
    }

    @Test
    public void testSmallTruckWidth() {
        ReaderWay way = new ReaderWay(1);
        way.setTag("highway", "primary");
        assertTrue(forward(smallTruckParser, way));

        way.setTag("maxwidth", "6'6\"");
        assertFalse(forward(smallTruckParser, way));
        way.removeTag("maxwidth");

        assertTrue(forward(smallTruckParser, way));
        way.setTag("hgv", "no");
        assertTrue(forward(smallTruckParser, way));
    }

    @Test
    public void testConditionalWeight() {
        ReaderWay way = new ReaderWay(1);
        way.setTag("highway", "primary");
        way.setTag("access", "yes");
        assertTrue(forward(truckParser, way));

        way.setTag("access:conditional", "no @ weight > 7");
        assertFalse(forward(truckParser, way));
        assertTrue(forward(carParser, way));

        way.setTag("access:conditional", "no @ weight < 7");
        assertTrue(forward(truckParser, way));
        assertFalse(forward(carParser, way));

        way = new ReaderWay(1);
        way.setTag("highway", "primary");
        way.setTag("access", "no");
        assertFalse(forward(truckParser, way));
        assertFalse(forward(carParser, way));

        way.setTag("access:conditional", "yes @ weight > 7");
        assertTrue(forward(truckParser, way));
        assertFalse(forward(carParser, way));

        way.setTag("access:conditional", "yes @ weight < 7");
        assertFalse(forward(truckParser, way));
        assertTrue(forward(carParser, way));

        way = new ReaderWay(1);
        way.setTag("highway", "primary");
        way.setTag("access", "no");
        assertFalse(forward(truckParser, way));
        way.setTag("access:conditional", "yes @ (weight>7)");
        assertTrue(forward(truckParser, way));
        assertFalse(forward(carParser, way));

        // height and width conditions, multiple conditions are ignored
        way = new ReaderWay(1);
        way.setTag("highway", "primary");
        way.setTag("hgv:conditional", "no @ (height > 3.5)");
        assertFalse(forward(truckParser, way));
        way.setTag("hgv:conditional", "no @ (width >= 2.7)");
        assertTrue(forward(truckParser, way));
        way.setTag("hgv:conditional", "no @ (width > 2.5)");
        assertFalse(forward(truckParser, way));
        way.setTag("hgv:conditional", "no @ (weight > 7.5); yes @ (Mo-Fr 06:00-10:00)");
        assertTrue(forward(truckParser, way));
    }

    @Test
    public void testConditional() {
        PMap map = new PMap();
        map.putObject("block_private", false);
        ModeAccessParser tmpTruckParser = TruckVehicles.create(em, "truck", map);
        ReaderWay way = new ReaderWay(1);
        way.setTag("highway", "primary");
        way.setTag("access", "private");
        assertTrue(forward(tmpTruckParser, way));

        // allow maxweight exception for private access
        way = new ReaderWay(1);
        way.setTag("highway", "primary");
        way.setTag("maxweight", "7.5");
        assertFalse(forward(tmpTruckParser, way));
        // ignore maxweight restriction
        way.setTag("maxweight:conditional", "none @ private");
        assertTrue(forward(tmpTruckParser, way));
        // destination is always allowed
        way.setTag("maxweight:conditional", "none @ destination");
        assertTrue(forward(truckParser, way));

        // allow maxweight exception for delivery access but not for private
        map = new PMap();
        map.putObject("block_delivery", false);
        tmpTruckParser = TruckVehicles.create(em, "truck", map);
        way = new ReaderWay(1);
        way.setTag("highway", "primary");
        way.setTag("maxweight", "7.5");
        assertFalse(forward(tmpTruckParser, way));
        way.setTag("maxweight:conditional", "none @ delivery");
        assertTrue(forward(tmpTruckParser, way));
        way.setTag("maxweight:conditional", "none @ private");
        assertFalse(forward(tmpTruckParser, way));

        // a format that would work without special handling:
        way = new ReaderWay(1);
        way.setTag("highway", "primary");
        way.setTag("maxweight", "7.5");
        assertFalse(forward(tmpTruckParser, way));
        way.setTag("access:conditional", "delivery @ (weight > 7.5)");
        assertTrue(forward(tmpTruckParser, way));
        // just check that if weight is too big it won't work
        way.setTag("access:conditional", "delivery @ (weight > 50)");
        assertFalse(forward(tmpTruckParser, way));
        // and only if delivery is allowed
        way.setTag("access:conditional", "delivery @ (weight > 7.5)");
        assertFalse(forward(truckParser, way));

        way = new ReaderWay(1);
        way.setTag("highway", "primary");
        way.setTag("motor_vehicle", "no");
        assertFalse(forward(tmpTruckParser, way));
        way.setTag("motor_vehicle:conditional", "yes @ (21:00-9:00)");
        assertTrue(forward(tmpTruckParser, way));
    }

    @Test
    public void noBlockMilitary() {
        PMap map = new PMap();
        map.putObject("block_military", false);
        ModeAccessParser tmpCarParser = TruckVehicles.create(em, "car", map);
        ReaderWay way = new ReaderWay(1);
        way.setTag("highway", "primary");
        assertTrue(forward(tmpCarParser, way));

        way.setTag("access", "military");
        assertTrue(forward(tmpCarParser, way));
        assertFalse(forward(carParser, way));
    }

    @Test
    public void testDeliveryPedestrian() {
        PMap map = new PMap();
        map.putObject("block_delivery", false);
        ModeAccessParser tmpCarParser = TruckVehicles.create(em, "car", map);

        ReaderWay way = new ReaderWay(1);
        way.setTag("highway", "pedestrian");
        assertFalse(forward(tmpCarParser, way));
        way.setTag("motor_vehicle", "delivery");
        assertTrue(forward(tmpCarParser, way));
        assertFalse(forward(carParser, way));

        way = new ReaderWay(1);
        way.setTag("highway", "pedestrian");
        way.setTag("access:conditional", "delivery @ (Mo-Sa 06:00-12:00)");
        assertTrue(forward(tmpCarParser, way));
        assertFalse(forward(carParser, way));

        // any allowed value opens a pedestrian street, an unknown one does not
        way = new ReaderWay(1);
        way.setTag("highway", "pedestrian");
        way.setTag("vehicle", "destination");
        assertTrue(forward(carParser, way));
        way.setTag("vehicle", "unknown");
        assertFalse(forward(carParser, way));
    }

    @Test
    public void conditionalHGV() {
        ReaderWay way = new ReaderWay(1);
        way.setTag("highway", "service");
        way.setTag("access", "yes");
        assertTrue(forward(truckParser, way));
        way.setTag("access:conditional", "no @ hgv");
        assertFalse(forward(truckParser, way));
        assertTrue(forward(carParser, way));

        // none lifts a restriction like yes, also with a limit key as condition
        way.setTag("access", "no");
        way.setTag("access:conditional", "none @ (maxweight)");
        assertTrue(forward(truckParser, way));
        way.setTag("maxweight", "24");
        assertTrue(forward(truckParser, way));
        way.setTag("access:conditional", "none @ (maxspeed)");
        assertFalse(forward(truckParser, way));
    }

    @Test
    public void conditionalPerKey() {
        // more specific plain value wins over more generic conditional
        ReaderWay way = new ReaderWay(1);
        way.setTag("highway", "primary");
        way.setTag("hgv", "yes");
        way.setTag("access:conditional", "no @ (weight > 7.5)");
        assertTrue(forward(truckParser, way));
        way.setTag("hgv", "no");
        way.setTag("access:conditional", "yes @ (weight > 7.5)");
        assertFalse(forward(truckParser, way));

        // more specific conditional wins over more generic plain value, but only if it applies
        way = new ReaderWay(1);
        way.setTag("highway", "primary");
        way.setTag("access", "no");
        way.setTag("hgv:conditional", "yes @ (weight > 7.5)");
        assertTrue(forward(truckParser, way));
        way.setTag("hgv:conditional", "yes @ (weight < 7.5)");
        assertFalse(forward(truckParser, way));
        way.setTag("access", "yes");
        way.setTag("hgv:conditional", "no @ (weight > 7.5)");
        assertFalse(forward(truckParser, way));

        // maxweight:conditional only lifts maxweight, not access
        PMap map = new PMap();
        map.putObject("block_private", false);
        ModeAccessParser tmpTruckParser = TruckVehicles.create(em, "truck", map);
        way = new ReaderWay(1);
        way.setTag("highway", "primary");
        way.setTag("access", "no");
        way.setTag("maxweight:conditional", "none @ private");
        assertFalse(forward(tmpTruckParser, way));
    }

    @Test
    public void temporalAccess() {
        ReaderWay way = new ReaderWay(1);
        way.setTag("highway", "primary");
        way.setTag("access:conditional", "no @ (May - June)");
        assertTrue(forward(truckParser, way));

        way = new ReaderWay(1);
        way.setTag("highway", "primary");
        way.setTag("motorcar:conditional", "no @ (May - June)");
        assertTrue(forward(truckParser, way));

        way = new ReaderWay(1);
        way.setTag("highway", "primary");
        way.setTag("vehicle", "no");
        way.setTag("access:conditional", "yes @ (May - June)");
        assertFalse(forward(truckParser, way));

        // truck should ignore unrelated conditional access restrictions of e.g. bicycle or motorcar
        way = new ReaderWay(1);
        way.setTag("highway", "primary");
        way.setTag("vehicle", "no");
        way.setTag("bicycle:conditional", "yes @ (May - June)");
        assertFalse(forward(truckParser, way));

        way = new ReaderWay(1);
        way.setTag("highway", "primary");
        way.setTag("motorcar", "no");
        way.setTag("access:conditional", "yes @ (May - June)");
        assertFalse(forward(carParser, way));
        assertTrue(forward(truckParser, way));

        // Ignore access restriction if there is a *higher* priority temporal restriction that *could* lift it.
        // But this is independent of the date!
        way = new ReaderWay(1);
        way.setTag("highway", "primary");
        way.setTag("access", "no");
        way.setTag("vehicle:conditional", "yes @ (May - June)");
        assertTrue(forward(truckParser, way));
    }

    @Test
    public void testMixedTag() {
        ReaderWay way = new ReaderWay(0L);
        way.setTag("highway", "track");
        way.setTag("motor_vehicle", "agricultural");
        assertFalse(forward(carParser, way));
        way.setTag("motor_vehicle", "agricultural;forestry");
        assertFalse(forward(carParser, way));
        way.setTag("motor_vehicle", "forestry;agricultural");
        assertFalse(forward(carParser, way));
        way.setTag("motor_vehicle", "forestry;agricultural;unknown");
        assertFalse(forward(carParser, way));
        way.setTag("motor_vehicle", "yes;forestry;agricultural");
        assertTrue(forward(carParser, way));
        way.setTag("motor_vehicle", "unknown;strange");
        assertTrue(forward(carParser, way));
    }

    @Test
    public void testBarrier() {
        ReaderWay way = new ReaderWay(1);
        way.setTag("highway", "secondary");
        way.setTag("gh:barrier_edge", true);
        way.setTag("node_tags", java.util.List.of(java.util.Map.of("barrier", "bollard"), java.util.Map.of()));
        assertFalse(forward(carParser, way));
        way.setTag("node_tags", java.util.List.of(java.util.Map.of("barrier", "bollard", "motor_vehicle", "yes"), java.util.Map.of()));
        assertTrue(forward(carParser, way));
        way.setTag("node_tags", java.util.List.of(java.util.Map.of("barrier", "gate"), java.util.Map.of()));
        assertTrue(forward(carParser, way));
        way.setTag("node_tags", java.util.List.of(java.util.Map.of("barrier", "gate", "locked", "yes"), java.util.Map.of()));
        assertFalse(forward(carParser, way));
        way.setTag("node_tags", java.util.List.of(java.util.Map.of("barrier", "log"), java.util.Map.of()));
        assertTrue(forward(carParser, way));
        // unknown values hide the less specific keys on nodes
        way.setTag("node_tags", java.util.List.of(java.util.Map.of("barrier", "gate", "access", "private", "motor_vehicle", "customers"), java.util.Map.of()));
        assertTrue(forward(carParser, way));
        way.setTag("node_tags", java.util.List.of(java.util.Map.of("barrier", "bollard", "access", "yes", "motor_vehicle", "customers"), java.util.Map.of()));
        assertFalse(forward(carParser, way));
        way.setTag("node_tags", java.util.List.of(java.util.Map.of("barrier", "gate", "access", "destination", "locked", "yes"), java.util.Map.of()));
        assertFalse(forward(carParser, way));
    }

    @Test
    public void testTaxi() {
        ReaderWay way = new ReaderWay(1);
        way.setTag("highway", "primary");
        assertTrue(forward(taxiParser, way));
        way.setTag("hgv", "no");
        assertTrue(forward(taxiParser, way));

        way = new ReaderWay(1);
        way.setTag("highway", "primary");
        way.setTag("psv", "no");
        assertFalse(forward(taxiParser, way));

        way = new ReaderWay(1);
        way.setTag("highway", "primary");
        way.setTag("access", "no");
        assertFalse(forward(taxiParser, way));
        way.setTag("taxi", "yes");
        assertTrue(forward(taxiParser, way));
    }

    @Test
    public void testTaxiOneway() {
        ReaderWay way = new ReaderWay(1);
        way.setTag("highway", "primary");
        way.setTag("oneway", "yes");
        assertTrue(forward(taxiParser, way));
        assertFalse(backward(taxiParser, way));

        way.setTag("psv", "opposite_lane");
        assertTrue(forward(taxiParser, way));
        assertTrue(backward(taxiParser, way));

        way = new ReaderWay(1);
        way.setTag("highway", "primary");
        way.setTag("oneway", "yes");
        way.setTag("lanes:psv:backward", "1");
        assertTrue(forward(taxiParser, way));
        assertTrue(backward(taxiParser, way));
        assertFalse(backward(carParser, way));

        way = new ReaderWay(1);
        way.setTag("highway", "primary");
        way.setTag("oneway", "yes");
        way.setTag("oneway:taxi", "no");
        assertTrue(backward(taxiParser, way));

        way = new ReaderWay(1);
        way.setTag("highway", "primary");
        way.setTag("oneway", "yes");
        way.setTag("motor_vehicle:backward", "no");
        way.setTag("motor_vehicle", "destination");
        way.setTag("psv:backward", "yes");
        assertTrue(forward(taxiParser, way));
        assertTrue(backward(taxiParser, way));
    }
}
