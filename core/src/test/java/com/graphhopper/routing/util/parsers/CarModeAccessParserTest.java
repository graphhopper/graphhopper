package com.graphhopper.routing.util.parsers;

import com.graphhopper.reader.ReaderNode;
import com.graphhopper.reader.ReaderWay;
import com.graphhopper.routing.ev.*;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.routing.util.WayAccess;
import com.graphhopper.storage.IntsRef;
import com.graphhopper.util.PMap;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class CarModeAccessParserTest {

    private final EncodingManager em = new EncodingManager.Builder().add(Roundabout.create())
            .add(VehicleAccess.create("car")).add(VehicleAccess.create("bike")).build();
    private final ModeAccessParser parser = createParser(em, new PMap());
    private final BooleanEncodedValue roundaboutEnc = em.getBooleanEncodedValue(Roundabout.KEY);
    private final BooleanEncodedValue accessEnc = parser.getAccessEnc();

    ModeAccessParser createParser(EncodedValueLookup lookup, PMap properties) {
        return ModeAccessParser.createCar(lookup, properties);
    }

    private boolean isAccessible(ReaderWay way) {
        EdgeIntAccess edgeIntAccess = ArrayEdgeIntAccess.createFromBytes(em.getBytesForFlags());
        parser.handleWayTags(0, edgeIntAccess, way, null);
        return accessEnc.getBool(false, 0, edgeIntAccess) || accessEnc.getBool(true, 0, edgeIntAccess);
    }

    private boolean isBarrier(ReaderNode node) {
        ReaderWay way = new ReaderWay(1);
        way.setTag("highway", "primary");
        way.setTag("gh:barrier_edge", true);
        way.setTag("node_tags", List.of(node.getTags()));
        return !isAccessible(way);
    }

    @Test
    public void testAccess() {
        ReaderWay way = new ReaderWay(1);
        assertFalse(isAccessible(way));
        way.setTag("highway", "service");
        assertTrue(isAccessible(way));
        way.setTag("access", "no");
        assertFalse(isAccessible(way));

        way.clearTags();
        way.setTag("highway", "track");
        assertTrue(isAccessible(way));

        way.setTag("motorcar", "no");
        assertFalse(isAccessible(way));

        // the tracktype is not considered here, car.json blocks grade4+5
        way.clearTags();
        way.setTag("highway", "track");
        way.setTag("tracktype", "grade4");
        assertTrue(isAccessible(way));

        way.clearTags();
        way.setTag("highway", "service");
        way.setTag("access", "delivery");
        assertFalse(isAccessible(way));

        way.clearTags();
        way.setTag("access", "yes");
        way.setTag("motor_vehicle", "no");
        assertFalse(isAccessible(way));

        way.clearTags();
        way.setTag("highway", "service");
        way.setTag("access", "yes");
        way.setTag("motor_vehicle", "no");
        assertFalse(isAccessible(way));

        way.clearTags();
        way.setTag("highway", "track");
        way.setTag("motor_vehicle", "agricultural");
        assertFalse(isAccessible(way));
        way.setTag("motor_vehicle", "agricultural;forestry");
        assertFalse(isAccessible(way));
        way.setTag("motor_vehicle", "forestry;agricultural");
        assertFalse(isAccessible(way));
        way.setTag("motor_vehicle", "forestry;agricultural;unknown");
        assertFalse(isAccessible(way));
        way.setTag("motor_vehicle", "yes;forestry;agricultural");
        assertTrue(isAccessible(way));

        way.clearTags();
        way.setTag("highway", "service");
        way.setTag("access", "no");
        way.setTag("motorcar", "yes");
        assertTrue(isAccessible(way));

        way.clearTags();
        way.setTag("highway", "service");
        way.setTag("motor_vehicle", "service");
        assertFalse(isAccessible(way));

        way.clearTags();
        way.setTag("highway", "service");
        way.setTag("access", "emergency");
        assertFalse(isAccessible(way));

        way.clearTags();
        way.setTag("highway", "service");
        way.setTag("motor_vehicle", "emergency");
        assertFalse(isAccessible(way));

        way.clearTags();
        way.setTag("highway", "service");
        way.setTag("service", "emergency_access");
        assertFalse(isAccessible(way));

        way.clearTags();
        way.setTag("highway", "unclassified");
        way.setTag("motor_vehicle", "agricultural;destination;forestry");
        assertTrue(isAccessible(way));
    }

    @Test
    public void testUnknownValueDoesNotShadowAccess() {
        // an unrecognized value on a more specific key must defer to access, not shadow it
        ReaderWay way = new ReaderWay(1);
        way.setTag("highway", "service");
        way.setTag("access", "no");
        way.setTag("motor_vehicle", "unknownvalue");
        assertFalse(isAccessible(way));

        // but without any restriction it stays allowed
        way.removeTag("access");
        assertTrue(isAccessible(way));

        // also for highway=pedestrian, which requires an explicitly allowed value
        way.setTag("highway", "pedestrian");
        way.setTag("motorcar", "unknownvalue");
        way.setTag("motor_vehicle", "yes");
        assertTrue(isAccessible(way));

        // and for ferries the implied default still applies
        way.clearTags();
        way.setTag("route", "ferry");
        way.setTag("motorcar", "unknownvalue");
        assertTrue(isAccessible(way));
    }

    @Test
    public void testMilitaryAccess() {
        ReaderWay way = new ReaderWay(1);
        way.setTag("highway", "track");
        way.setTag("access", "military");
        assertFalse(isAccessible(way));
    }

    @Test
    public void testOneway() {
        ReaderWay way = new ReaderWay(1);
        way.setTag("highway", "primary");
        EdgeIntAccess edgeIntAccess = ArrayEdgeIntAccess.createFromBytes(em.getBytesForFlags());
        int edgeId = 0;
        parser.handleWayTags(edgeId, edgeIntAccess, way, null);
        assertTrue(accessEnc.getBool(false, edgeId, edgeIntAccess));
        assertTrue(accessEnc.getBool(true, edgeId, edgeIntAccess));
        way.setTag("oneway", "yes");
        edgeIntAccess = ArrayEdgeIntAccess.createFromBytes(em.getBytesForFlags());
        parser.handleWayTags(edgeId, edgeIntAccess, way, null);
        assertTrue(accessEnc.getBool(false, edgeId, edgeIntAccess));
        assertFalse(accessEnc.getBool(true, edgeId, edgeIntAccess));
        way.clearTags();

        way.setTag("highway", "tertiary");

        edgeIntAccess = ArrayEdgeIntAccess.createFromBytes(em.getBytesForFlags());
        parser.handleWayTags(edgeId, edgeIntAccess, way, null);
        assertTrue(accessEnc.getBool(false, edgeId, edgeIntAccess));
        assertTrue(accessEnc.getBool(true, edgeId, edgeIntAccess));
        way.clearTags();

        way.setTag("highway", "tertiary");
        way.setTag("vehicle:forward", "no");

        edgeIntAccess = ArrayEdgeIntAccess.createFromBytes(em.getBytesForFlags());
        parser.handleWayTags(edgeId, edgeIntAccess, way, null);
        assertFalse(accessEnc.getBool(false, edgeId, edgeIntAccess));
        assertTrue(accessEnc.getBool(true, edgeId, edgeIntAccess));
        way.clearTags();

        way.setTag("highway", "tertiary");
        way.setTag("vehicle:backward", "no");
        edgeIntAccess = ArrayEdgeIntAccess.createFromBytes(em.getBytesForFlags());
        parser.handleWayTags(edgeId, edgeIntAccess, way, null);
        assertTrue(accessEnc.getBool(false, edgeId, edgeIntAccess));
        assertFalse(accessEnc.getBool(true, edgeId, edgeIntAccess));
        way.clearTags();

        // This is no one way
        way.setTag("highway", "tertiary");
        way.setTag("vehicle:backward", "designated");
        edgeIntAccess = ArrayEdgeIntAccess.createFromBytes(em.getBytesForFlags());
        parser.handleWayTags(edgeId, edgeIntAccess, way, null);
        assertTrue(accessEnc.getBool(false, edgeId, edgeIntAccess));
        assertTrue(accessEnc.getBool(true, edgeId, edgeIntAccess));
        way.clearTags();
    }

    @Test
    public void shouldBlockPrivate() {
        ReaderWay way = new ReaderWay(1);
        way.setTag("highway", "primary");
        way.setTag("access", "private");
        EdgeIntAccess edgeIntAccess = ArrayEdgeIntAccess.createFromBytes(em.getBytesForFlags());
        int edgeId = 0;
        parser.handleWayTags(edgeId, edgeIntAccess, way, null);
        assertFalse(accessEnc.getBool(false, edgeId, edgeIntAccess));
        assertFalse(accessEnc.getBool(true, edgeId, edgeIntAccess));

        final ModeAccessParser parser = createParser(em, new PMap("block_private=false"));
        edgeIntAccess = ArrayEdgeIntAccess.createFromBytes(em.getBytesForFlags());
        parser.handleWayTags(edgeId, edgeIntAccess, way, null);
        assertTrue(parser.getAccessEnc().getBool(false, edgeId, edgeIntAccess));

        way.setTag("highway", "primary");
        way.setTag("motor_vehicle", "permit"); // currently handled like "private", see #2712
        edgeIntAccess = ArrayEdgeIntAccess.createFromBytes(em.getBytesForFlags());
        parser.handleWayTags(edgeId, edgeIntAccess, way, null);
        assertTrue(parser.getAccessEnc().getBool(false, edgeId, edgeIntAccess));
    }

    @Test
    public void testRoundabout() {
        EdgeIntAccess edgeIntAccess = ArrayEdgeIntAccess.createFromBytes(em.getBytesForFlags());
        int edgeId = 0;
        accessEnc.setBool(false, edgeId, edgeIntAccess, true);
        accessEnc.setBool(true, edgeId, edgeIntAccess, true);
        roundaboutEnc.setBool(false, edgeId, edgeIntAccess, true);
        assertTrue(roundaboutEnc.getBool(false, edgeId, edgeIntAccess));
        assertTrue(accessEnc.getBool(false, edgeId, edgeIntAccess));
        assertTrue(accessEnc.getBool(true, edgeId, edgeIntAccess));

        roundaboutEnc.setBool(false, edgeId, edgeIntAccess, false);
        assertFalse(roundaboutEnc.getBool(false, edgeId, edgeIntAccess));
        assertTrue(accessEnc.getBool(false, edgeId, edgeIntAccess));
        assertTrue(accessEnc.getBool(true, edgeId, edgeIntAccess));

        ReaderWay way = new ReaderWay(1);
        way.setTag("highway", "primary");
        edgeIntAccess = ArrayEdgeIntAccess.createFromBytes(em.getBytesForFlags());
        parser.handleWayTags(edgeId, edgeIntAccess, way, null);
        assertTrue(accessEnc.getBool(false, edgeId, edgeIntAccess));
        assertTrue(accessEnc.getBool(true, edgeId, edgeIntAccess));
        assertFalse(roundaboutEnc.getBool(false, edgeId, edgeIntAccess));

        // a motorway is oneway unless tagged otherwise
        way.setTag("highway", "motorway");
        edgeIntAccess = ArrayEdgeIntAccess.createFromBytes(em.getBytesForFlags());
        parser.handleWayTags(edgeId, edgeIntAccess, way, null);
        assertTrue(accessEnc.getBool(false, edgeId, edgeIntAccess));
        assertFalse(accessEnc.getBool(true, edgeId, edgeIntAccess));
        way.setTag("oneway", "no");
        edgeIntAccess = ArrayEdgeIntAccess.createFromBytes(em.getBytesForFlags());
        parser.handleWayTags(edgeId, edgeIntAccess, way, null);
        assertTrue(accessEnc.getBool(true, edgeId, edgeIntAccess));
    }

    @Test
    public void testRailway() {
        ReaderWay way = new ReaderWay(1);
        way.setTag("highway", "secondary");
        way.setTag("railway", "rail");
        assertTrue(isAccessible(way));

        way.clearTags();
        way.setTag("highway", "path");
        way.setTag("railway", "abandoned");
        assertFalse(isAccessible(way));

        way.setTag("highway", "track");
        assertTrue(isAccessible(way));

        // this is fully okay as sometimes old rails are on the road
        way.setTag("highway", "primary");
        way.setTag("railway", "historic");
        assertTrue(isAccessible(way));

        way.setTag("motorcar", "no");
        assertFalse(isAccessible(way));

        way = new ReaderWay(1);
        way.setTag("highway", "secondary");
        way.setTag("railway", "tram");
        // but allow tram to be on the same way
        assertTrue(isAccessible(way));
    }

    @Test
    public void testFerry() {
        ReaderWay way = new ReaderWay(1);
        way.setTag("route", "shuttle_train");
        way.setTag("motorcar", "yes");
        way.setTag("bicycle", "no");
        // Provide the duration value in seconds:
        way.setTag("way_distance_2d", 50000.0);
        way.setTag("duration_in_seconds", 35.0);
        assertTrue(isAccessible(way));

        // test for very short and slow 0.5km/h still realistic ferry
        way = new ReaderWay(1);
        way.setTag("route", "ferry");
        way.setTag("motorcar", "yes");
        way.setTag("way_distance_2d", 100.0);
        way.setTag("duration_in_seconds", 12.0);
        assertTrue(isAccessible(way));

        // test for missing duration
        way = new ReaderWay(1);
        way.setTag("route", "ferry");
        way.setTag("motorcar", "yes");
        way.setTag("edge_distance", 100.0);
        assertTrue(isAccessible(way));

        way.clearTags();
        way.setTag("route", "ferry");
        assertTrue(isAccessible(way));
        way.setTag("motorcar", "no");
        assertFalse(isAccessible(way));

        way.clearTags();
        way.setTag("route", "ferry");
        way.setTag("foot", "yes");
        assertFalse(isAccessible(way));

        way.clearTags();
        way.setTag("route", "ferry");
        way.setTag("foot", "designated");
        way.setTag("motor_vehicle", "designated");
        assertTrue(isAccessible(way));

        way.clearTags();
        way.setTag("route", "ferry");
        way.setTag("access", "no");
        assertFalse(isAccessible(way));
        way.setTag("vehicle", "yes");
        assertTrue(isAccessible(way));

        // issue #1432
        way.clearTags();
        way.setTag("route", "ferry");
        way.setTag("oneway", "yes");
        EdgeIntAccess edgeIntAccess = ArrayEdgeIntAccess.createFromBytes(em.getBytesForFlags());
        int edgeId = 0;
        parser.handleWayTags(edgeId, edgeIntAccess, way, null);
        assertTrue(accessEnc.getBool(false, edgeId, edgeIntAccess));
        assertFalse(accessEnc.getBool(true, edgeId, edgeIntAccess));
    }

    @Test
    public void testBarrierAccess() {
        ReaderNode node = new ReaderNode(1, -1, -1);
        node.setTag("barrier", "lift_gate");
        node.setTag("access", "yes");
        // no barrier!
        assertFalse(isBarrier(node));

        node = new ReaderNode(1, -1, -1);
        node.setTag("barrier", "lift_gate");
        node.setTag("bicycle", "yes");
        // no barrier!
        assertFalse(isBarrier(node));

        node = new ReaderNode(1, -1, -1);
        node.setTag("barrier", "lift_gate");
        node.setTag("access", "yes");
        node.setTag("bicycle", "yes");
        // should this be a barrier for motorcars too?
        // assertTrue(encoder.handleNodeTags(node) == true);

        node = new ReaderNode(1, -1, -1);
        node.setTag("barrier", "lift_gate");
        node.setTag("access", "no");
        node.setTag("motorcar", "yes");
        // no barrier!
        assertFalse(isBarrier(node));

        node = new ReaderNode(1, -1, -1);
        node.setTag("barrier", "bollard");
        // barrier!
        assertTrue(isBarrier(node));

        // Test if cattle_grid is not blocking
        node = new ReaderNode(1, -1, -1);
        node.setTag("barrier", "cattle_grid");
        assertFalse(isBarrier(node));

        node = new ReaderNode(1, -1, -1);
        node.setTag("barrier", "gate");
        assertFalse(isBarrier(node));
    }

    @Test
    public void testChainBarrier() {
        // by default allow access through the gate for bike & foot!
        ReaderNode node = new ReaderNode(1, -1, -1);
        node.setTag("barrier", "chain");
        assertFalse(isBarrier(node));
        node.setTag("motor_vehicle", "no");
        assertTrue(isBarrier(node));
        node.setTag("motor_vehicle", "yes");
        assertFalse(isBarrier(node));
    }

    @Test
    public void testCombination() {
        ReaderWay way = new ReaderWay(123);
        way.setTag("highway", "cycleway");
        way.setTag("sac_scale", "hiking");

        BikeAccessParser bikeParser = new BikeAccessParser(em, new PMap());
        assertFalse(isAccessible(way));
        assertNotEquals(WayAccess.CAN_SKIP, bikeParser.getAccess(way));
        EdgeIntAccess edgeIntAccess = ArrayEdgeIntAccess.createFromBytes(em.getBytesForFlags());
        int edgeId = 0;
        parser.handleWayTags(edgeId, edgeIntAccess, way, null);
        bikeParser.handleWayTags(edgeId, edgeIntAccess, way);
        assertFalse(accessEnc.getBool(true, edgeId, edgeIntAccess));
        assertFalse(accessEnc.getBool(false, edgeId, edgeIntAccess));
        BooleanEncodedValue bikeAccessEnc = bikeParser.getAccessEnc();
        assertTrue(bikeAccessEnc.getBool(true, edgeId, edgeIntAccess));
        assertTrue(bikeAccessEnc.getBool(false, edgeId, edgeIntAccess));
    }

    @Test
    public void temporalAccess() {
        int edgeId = 0;
        ArrayEdgeIntAccess access = new ArrayEdgeIntAccess(1);
        ReaderWay way = new ReaderWay(1);
        way.setTag("highway", "primary");
        way.setTag("access:conditional", "no @ (May - June)");
        parser.handleWayTags(edgeId, access, way, null);
        assertTrue(accessEnc.getBool(false, edgeId, access));

        access = new ArrayEdgeIntAccess(1);
        way = new ReaderWay(1);
        way.setTag("highway", "primary");
        way.setTag("motorcar:conditional", "no @ (May - June)");
        parser.handleWayTags(edgeId, access, way, null);
        assertTrue(accessEnc.getBool(false, edgeId, access));

        access = new ArrayEdgeIntAccess(1);
        way = new ReaderWay(1);
        way.setTag("highway", "primary");
        way.setTag("motorcar", "no");
        way.setTag("access:conditional", "yes @ (May - June)");
        parser.handleWayTags(edgeId, access, way, null);
        assertFalse(accessEnc.getBool(false, edgeId, access));

        // car should ignore unrelated conditional access restrictions of e.g. bicycle
        access = new ArrayEdgeIntAccess(1);
        way = new ReaderWay(1);
        way.setTag("highway", "primary");
        way.setTag("vehicle", "no");
        way.setTag("bicycle:conditional", "yes @ (May - June)");
        parser.handleWayTags(edgeId, access, way, null);
        assertFalse(accessEnc.getBool(false, edgeId, access));

        // Ignore access restriction if there is a *higher* priority temporal restriction that *could* lift it.
        // But this is independent on the date!
        access = new ArrayEdgeIntAccess(1);
        way = new ReaderWay(1);
        way.setTag("highway", "primary");
        way.setTag("access", "no");
        way.setTag("motorcar:conditional", "yes @ (May - June)");
        parser.handleWayTags(edgeId, access, way, null);
        assertTrue(accessEnc.getBool(false, edgeId, access));

        // Open access even if we can't parse the conditional
        access = new ArrayEdgeIntAccess(1);
        way = new ReaderWay(1);
        way.setTag("highway", "primary");
        way.setTag("access", "no");
        way.setTag("motorcar:conditional", "yes @ (10:00 - 11:00)");
        parser.handleWayTags(edgeId, access, way, null);
        assertTrue(accessEnc.getBool(false, edgeId, access));

        // ... but don't do the same for non-intended values
        access = new ArrayEdgeIntAccess(1);
        way = new ReaderWay(1);
        way.setTag("highway", "primary");
        way.setTag("access", "no");
        way.setTag("motorcar:conditional", "private @ (10:00 - 11:00)");
        parser.handleWayTags(edgeId, access, way, null);
        assertFalse(accessEnc.getBool(false, edgeId, access));
    }

    @ParameterizedTest
    @ValueSource(strings = {"mofa", "moped", "motorcar", "motor_vehicle", "motorcycle"})
    void footway_etc_not_allowed_despite_vehicle_yes(String vehicle) {
        // these highways are blocked, even when we set one of the vehicles to yes
        for (String highway : Arrays.asList("footway", "cycleway", "steps")) {
            ReaderWay way = new ReaderWay(1);
            way.setTag("highway", highway);
            way.setTag(vehicle, "yes");
            assertFalse(isAccessible(way));
        }
    }

    @Test
    void testPedestrianAccess() {
        ReaderWay way = new ReaderWay(1);
        way.setTag("highway", "pedestrian");
        assertFalse(isAccessible(way));

        way.clearTags();
        way.setTag("highway", "pedestrian");
        way.setTag("motor_vehicle", "no");
        assertFalse(isAccessible(way));

        way.clearTags();
        way.setTag("highway", "pedestrian");
        way.setTag("motor_vehicle", "unknown");
        assertFalse(isAccessible(way));

        way.clearTags();
        way.setTag("highway", "pedestrian");
        way.setTag("motor_vehicle", "destination");
        assertTrue(isAccessible(way));

        way.clearTags();
        way.setTag("highway", "pedestrian");
        way.setTag("motorcar", "no");
        way.setTag("motor_vehicle", "destination");
        assertFalse(isAccessible(way));

        way.clearTags();
        way.setTag("highway", "pedestrian");
        way.setTag("motor_vehicle:conditional", "destination @ ( 8:00 - 10:00 )");
        assertTrue(isAccessible(way));

        way.clearTags();
        way.setTag("highway", "pedestrian");
        way.setTag("access", "no");
        way.setTag("motor_vehicle:conditional", "destination @ ( 8:00 - 10:00 )");
        assertTrue(isAccessible(way));
    }

    @Test
    public void testBusDesignated() {
        EdgeIntAccess access = new ArrayEdgeIntAccess(1);
        ReaderWay way = new ReaderWay(0);
        way.setTag("highway", "tertiary");
        int edgeId = 0;
        parser.handleWayTags(edgeId, access, way, null);
        assertTrue(accessEnc.getBool(false, edgeId, access));

        access = new ArrayEdgeIntAccess(1);
        way.setTag("bus", "designated");
        parser.handleWayTags(edgeId, access, way, null);
        assertTrue(accessEnc.getBool(false, edgeId, access));
    }

    @Test
    public void testBarrierWithBusYes() {
        ReaderWay way = new ReaderWay(1);
        way.setTag("highway", "secondary");
        way.setTag("gh:barrier_edge", true);

        // motor_vehicle=permit is restricted — car is blocked even though bus=yes
        way.setTag("node_tags", List.of(Map.of("barrier", "bollard", "access", "no", "motor_vehicle", "permit", "bus", "yes"), Map.of()));
        EdgeIntAccess access = new ArrayEdgeIntAccess(1);
        int edgeId = 0;
        parser.handleWayTags(edgeId, access, way, null);
        assertFalse(accessEnc.getBool(false, edgeId, access));
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
        assertFalse(accessEnc.getBool(false, edgeId, access));
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
        assertFalse(accessEnc.getBool(false, edgeId, access));
    }

    @Test
    public void testMotorcycleYes() {
        int edgeId = 0;
        EdgeIntAccess access = new ArrayEdgeIntAccess(1);
        ReaderWay way = new ReaderWay(0);
        way.setTag("motor_vehicle", "no");
        way.setTag("highway", "tertiary");
        parser.handleWayTags(edgeId, access, way, null);
        assertFalse(accessEnc.getBool(false, edgeId, access));

        access = new ArrayEdgeIntAccess(1);
        way.setTag("motorcycle", "yes");
        parser.handleWayTags(0, access, way, null);
        assertFalse(accessEnc.getBool(false, edgeId, access));
    }

    @Test
    void testConstruction() {
        int edgeId = 0;
        ArrayEdgeIntAccess access = new ArrayEdgeIntAccess(1);
        ReaderWay way = new ReaderWay(1);
        way.setTag("highway", "construction");
        parser.handleWayTags(edgeId, access, way, null);
        assertFalse(accessEnc.getBool(false, edgeId, access));
    }

    // car_access and road_access are conceptually one determination, split into a boolean (open/closed) and a
    // qualification (YES/DESTINATION/DELIVERY/PRIVATE/...). The following tests ensure they stay in sync.

    // Default configuration: delivery is restricted, private is restricted
    static Stream<Arguments> defaultConfig() {
        return Stream.of(
                //        highway        extra tags                                                               access  road_access
                Arguments.of("residential", "",                                                                   true,  RoadAccess.YES),
                Arguments.of("residential", "motor_vehicle:conditional=no @ (06:00-11:00)",                       true,  RoadAccess.YES),
                Arguments.of("residential", "motor_vehicle:conditional=destination @ (06:00-11:00)",              true,  RoadAccess.YES),
                Arguments.of("residential", "motor_vehicle=destination",                                          true,  RoadAccess.DESTINATION),
                Arguments.of("residential", "motorcar=designated",                                                true,  RoadAccess.DESIGNATED),
                Arguments.of("residential", "access=discouraged",                                                 true,  RoadAccess.DISCOURAGED),
                Arguments.of("residential", "motor_vehicle=private",                                              false, RoadAccess.PRIVATE),
                Arguments.of("residential", "motor_vehicle=delivery",                                             false, RoadAccess.DELIVERY),
                Arguments.of("residential", "motor_vehicle=no",                                                   false, RoadAccess.NO),
                Arguments.of("residential", "motor_vehicle=no|motor_vehicle:conditional=yes @ (06:00-11:00)", true, RoadAccess.YES),
                Arguments.of("residential", "motor_vehicle=no|motor_vehicle:conditional=yes @ (May - June)", true, RoadAccess.YES),
                Arguments.of("residential", "motor_vehicle=no|motor_vehicle:conditional=yes @ pupsaffe", false, RoadAccess.NO),
                Arguments.of("residential", "motor_vehicle=no|motor_vehicle:conditional=delivery @ (06:00-11:00)", false, RoadAccess.DELIVERY),
                Arguments.of("pedestrian",  "",                                                                   false, RoadAccess.NO),
                Arguments.of("pedestrian",  "motor_vehicle:conditional=delivery @ (Mo-Fr 06:00-11:00)",           false, RoadAccess.DELIVERY)
        );
    }

    // block_private=false: private/permit/service move from restricted to allowed
    static Stream<Arguments> blockPrivateFalse() {
        return Stream.of(
                //        highway        extra tags                                                               access  road_access
                Arguments.of("residential", "",                                                                   true,  RoadAccess.YES),
                Arguments.of("residential", "motor_vehicle=destination",                                          true,  RoadAccess.DESTINATION),
                Arguments.of("residential", "motor_vehicle=private",                                              true,  RoadAccess.PRIVATE),
                Arguments.of("residential", "motor_vehicle=delivery",                                             false, RoadAccess.DELIVERY),
                Arguments.of("pedestrian",  "motor_vehicle=private",                                              true,  RoadAccess.PRIVATE),
                Arguments.of("pedestrian",  "motor_vehicle:conditional=delivery @ (Mo-Fr 06:00-11:00)",           false, RoadAccess.DELIVERY)
        );
    }

    // allow=delivery: delivery moves from restricted to intended
    static Stream<Arguments> allowDelivery() {
        return Stream.of(
                //        highway        extra tags                                                               access  road_access
                Arguments.of("residential", "",                                                                   true,  RoadAccess.YES),
                Arguments.of("residential", "motor_vehicle=delivery",                                             true,  RoadAccess.DELIVERY),
                Arguments.of("residential", "motor_vehicle=no",                                                   false, RoadAccess.NO),
                Arguments.of("residential", "motor_vehicle=no|motor_vehicle:conditional=delivery @ (06:00-11:00)", true,  RoadAccess.DELIVERY),
                Arguments.of("pedestrian",  "",                                                                   false, RoadAccess.NO),
                Arguments.of("pedestrian",  "motor_vehicle:conditional=delivery @ (Mo-Fr 06:00-11:00)",           true,  RoadAccess.DELIVERY),
                Arguments.of("pedestrian",  "motor_vehicle:conditional=delivery @ (May - June)",                  true, RoadAccess.DELIVERY),
                Arguments.of("pedestrian",  "motor_vehicle:conditional=delivery @ pupsaffe",                  false, RoadAccess.NO),
                Arguments.of("pedestrian",  "motor_vehicle:conditional=delivery @ (Mo-Fr 06:00-11:00,19:00-21:00; Sa 06:00-09:30)",                  true, RoadAccess.DELIVERY),
                Arguments.of("pedestrian",  "motor_vehicle=destination",                                          true,  RoadAccess.DESTINATION)
        );
    }

    @ParameterizedTest(name = "default: highway={0} {1} -> car_access={2}, road_access={3}")
    @MethodSource("defaultConfig")
    void testDefault(String highway, String extraTags, boolean expectedAccess, RoadAccess expectedRoadAccess) {
        Fixture f = new Fixture();
        TagParser accessParser = ModeAccessParser.createCar(f.em, new PMap());
        f.check(accessParser, highway, extraTags, expectedAccess, expectedRoadAccess);
    }

    @ParameterizedTest(name = "block_private=false: highway={0} {1} -> car_access={2}, road_access={3}")
    @MethodSource("blockPrivateFalse")
    void testBlockPrivateFalse(String highway, String extraTags, boolean expectedAccess, RoadAccess expectedRoadAccess) {
        Fixture f = new Fixture();
        TagParser accessParser = ModeAccessParser.createCar(f.em, new PMap("block_private=false"));
        f.check(accessParser, highway, extraTags, expectedAccess, expectedRoadAccess);
    }

    @ParameterizedTest(name = "allow=delivery: highway={0} {1} -> car_access={2}, road_access={3}")
    @MethodSource("allowDelivery")
    void testAllowDelivery(String highway, String extraTags, boolean expectedAccess, RoadAccess expectedRoadAccess) {
        Fixture f = new Fixture();
        TagParser accessParser = ModeAccessParser.createCar(f.em, new PMap("allow=delivery"));
        f.check(accessParser, highway, extraTags, expectedAccess, expectedRoadAccess);
    }

    private static class Fixture {
        final BooleanEncodedValue carAccessEnc = VehicleAccess.create("car");
        final BooleanEncodedValue roundaboutEnc = Roundabout.create();
        final EnumEncodedValue<RoadAccess> roadAccessEnc = RoadAccess.create();

        final EncodingManager em;

        Fixture() {
            em = new EncodingManager.Builder().add(carAccessEnc).add(roundaboutEnc).add(roadAccessEnc).build();
        }

        void check(TagParser accessParser, String highway, String extraTags,
                    boolean expectedAccess, RoadAccess expectedRoadAccess) {
            OSMRoadAccessParser<RoadAccess> roadAccessParser = OSMRoadAccessParser.forCar(roadAccessEnc);

            ReaderWay way = new ReaderWay(1L);
            way.setTag("highway", highway);
            for (String tag : extraTags.split("\\|")) {
                if (tag.isEmpty()) continue;
                String[] kv = tag.split("=", 2);
                way.setTag(kv[0].trim(), kv[1].trim());
            }

            ArrayEdgeIntAccess edgeIntAccess = new ArrayEdgeIntAccess(1);
            int edgeId = 0;
            IntsRef relFlags = new IntsRef(1);
            accessParser.handleWayTags(edgeId, edgeIntAccess, way, relFlags);
            roadAccessParser.handleWayTags(edgeId, edgeIntAccess, way, relFlags);

            assertEquals(expectedAccess, carAccessEnc.getBool(false, edgeId, edgeIntAccess), "car_access");
            assertEquals(expectedRoadAccess, roadAccessEnc.getEnum(false, edgeId, edgeIntAccess), "road_access");
        }
    }
}
