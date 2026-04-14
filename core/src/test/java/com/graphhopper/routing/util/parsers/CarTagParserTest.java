/*
 *  Licensed to GraphHopper GmbH under one or more contributor
 *  license agreements. See the NOTICE file distributed with this work for
 *  additional information regarding copyright ownership.
 *
 *  GraphHopper GmbH licenses this file to you under the Apache License,
 *  Version 2.0 (the "License"); you may not use this file except in
 *  compliance with the License. You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package com.graphhopper.routing.util.parsers;

import com.graphhopper.reader.ReaderWay;
import com.graphhopper.routing.ev.*;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.routing.util.PriorityCode;
import com.graphhopper.routing.util.TransportationMode;
import com.graphhopper.util.PMap;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Peter Karich
 */
public class CarTagParserTest {
    private final EncodingManager em = createEncodingManager("car");
    private static final Set<String> CAR_RESTRICTIONS = Set.of(
            "no", "restricted", "military", "emergency",
            "agricultural", "forestry", "delivery", "unknown",
            "private", "permit", "service");
    private final ModeAccessParser parser = new ModeAccessParser(
            OSMRoadAccessParser.toOSMRestrictions(TransportationMode.CAR),
            em.getBooleanEncodedValue(VehicleAccess.key("car")), true,
            em.getBooleanEncodedValue(Roundabout.KEY), CAR_RESTRICTIONS, Set.of());
    final CarAverageSpeedParser speedParser = new CarAverageSpeedParser(em);
    private final BooleanEncodedValue roundaboutEnc = em.getBooleanEncodedValue(Roundabout.KEY);
    private final BooleanEncodedValue accessEnc = em.getBooleanEncodedValue(VehicleAccess.key("car"));
    private final DecimalEncodedValue avSpeedEnc = speedParser.getAverageSpeedEnc();

    private EncodingManager createEncodingManager(String carName) {
        return new EncodingManager.Builder()
                .add(VehicleAccess.create(carName))
                .add(VehicleSpeed.create(carName, 7, 2, true))
                .addTurnCostEncodedValue(TurnCost.create(carName, 1))
                .add(VehicleAccess.create("bike"))
                .add(VehicleSpeed.create("bike", 4, 2, false))
                .add(VehiclePriority.create("bike", 4, PriorityCode.getFactor(1), false))
                .add(RouteNetwork.create(BikeNetwork.KEY))
                .add(Smoothness.create())
                .add(Roundabout.create())
                .add(FerrySpeed.create())
                .build();
    }

    private boolean hasAccess(ReaderWay way) {
        EdgeIntAccess edgeIntAccess = ArrayEdgeIntAccess.createFromBytes(em.getBytesForFlags());
        parser.handleWayTags(0, edgeIntAccess, way, null);
        return accessEnc.getBool(false, 0, edgeIntAccess);
    }

    @Test
    public void testAccess() {
        ReaderWay way = new ReaderWay(1);
        way.setTag("highway", "service");
        assertTrue(hasAccess(way));
        way.setTag("access", "no");
        assertFalse(hasAccess(way));

        way.clearTags();
        way.setTag("highway", "track");
        assertTrue(hasAccess(way));

        way.setTag("motorcar", "no");
        assertFalse(hasAccess(way));

        way.clearTags();
        way.setTag("highway", "service");
        way.setTag("access", "delivery");
        assertFalse(hasAccess(way));

        way.clearTags();
        way.setTag("access", "yes");
        way.setTag("motor_vehicle", "no");
        assertFalse(hasAccess(way));

        way.clearTags();
        way.setTag("highway", "service");
        way.setTag("access", "yes");
        way.setTag("motor_vehicle", "no");
        assertFalse(hasAccess(way));

        way.clearTags();
        way.setTag("highway", "service");
        way.setTag("access", "no");
        way.setTag("motorcar", "yes");
        assertTrue(hasAccess(way));

        way.clearTags();
        way.setTag("highway", "service");
        way.setTag("access", "emergency");
        assertFalse(hasAccess(way));

        way.clearTags();
        way.setTag("highway", "service");
        way.setTag("motor_vehicle", "emergency");
        assertFalse(hasAccess(way));

        way.clearTags();
        way.setTag("highway", "service");
        way.setTag("access", "no");
        way.setTag("motor_vehicle", "unknown");
        assertFalse(hasAccess(way));

        way.clearTags();
        way.setTag("highway", "service");
        way.setTag("motor_vehicle", "service");
        assertFalse(hasAccess(way));

        way.clearTags();
        way.setTag("highway", "service");
        way.setTag("service", "emergency_access");
        assertFalse(hasAccess(way));

        way.clearTags();
        way.setTag("highway", "unclassified");
        way.setTag("motor_vehicle", "destination");
        assertTrue(hasAccess(way));

        way.clearTags();
        way.setTag("highway", "unclassified");
        way.setTag("motor_vehicle", "agricultural;destination;forestry");
        assertTrue(hasAccess(way));
    }

    @Test
    public void testSemicolonRestrictions() {
        ReaderWay way = new ReaderWay(1);
        way.setTag("highway", "track");
        way.setTag("motor_vehicle", "agricultural");
        assertFalse(hasAccess(way));
        way.setTag("motor_vehicle", "agricultural;forestry");
        assertFalse(hasAccess(way));
        way.setTag("motor_vehicle", "forestry;agricultural");
        assertFalse(hasAccess(way));
        way.setTag("motor_vehicle", "forestry;agricultural;unknown");
        assertFalse(hasAccess(way));
        way.setTag("motor_vehicle", "yes;forestry;agricultural");
        assertTrue(hasAccess(way));
    }

    @Test
    public void testMilitaryAccess() {
        ReaderWay way = new ReaderWay(1);
        way.setTag("highway", "track");
        way.setTag("access", "military");
        assertFalse(hasAccess(way));
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

        // with block_private=false
        ModeAccessParser nonBlockingParser = new ModeAccessParser(
                OSMRoadAccessParser.toOSMRestrictions(TransportationMode.CAR),
                em.getBooleanEncodedValue(VehicleAccess.key("car")), true,
                em.getBooleanEncodedValue(Roundabout.KEY),
                Set.of("no", "restricted", "military", "emergency", "agricultural", "forestry", "delivery", "unknown"),
                Set.of());
        edgeIntAccess = ArrayEdgeIntAccess.createFromBytes(em.getBytesForFlags());
        nonBlockingParser.handleWayTags(edgeId, edgeIntAccess, way, null);
        assertTrue(accessEnc.getBool(false, edgeId, edgeIntAccess));

        way.setTag("highway", "primary");
        way.setTag("motor_vehicle", "permit");
        edgeIntAccess = ArrayEdgeIntAccess.createFromBytes(em.getBytesForFlags());
        nonBlockingParser.handleWayTags(edgeId, edgeIntAccess, way, null);
        assertTrue(accessEnc.getBool(false, edgeId, edgeIntAccess));
    }

    @Test
    public void testMaxSpeed() {
        ReaderWay way = new ReaderWay(1);
        way.setTag("highway", "trunk");
        way.setTag("maxspeed", "500");
        EdgeIntAccess edgeIntAccess = ArrayEdgeIntAccess.createFromBytes(em.getBytesForFlags());
        int edgeId = 0;
        speedParser.handleWayTags(edgeId, edgeIntAccess, way);
        assertEquals(136, avSpeedEnc.getDecimal(false, edgeId, edgeIntAccess), 1e-1);

        way = new ReaderWay(1);
        way.setTag("highway", "primary");
        way.setTag("maxspeed:backward", "10");
        way.setTag("maxspeed:forward", "20");
        edgeIntAccess = ArrayEdgeIntAccess.createFromBytes(em.getBytesForFlags());
        speedParser.handleWayTags(edgeId, edgeIntAccess, way);
        assertEquals(18, avSpeedEnc.getDecimal(false, edgeId, edgeIntAccess), 1e-1);
        assertEquals(10, avSpeedEnc.getDecimal(true, edgeId, edgeIntAccess), 1e-1);

        way = new ReaderWay(1);
        way.setTag("highway", "primary");
        way.setTag("maxspeed:forward", "20");
        edgeIntAccess = ArrayEdgeIntAccess.createFromBytes(em.getBytesForFlags());
        speedParser.handleWayTags(edgeId, edgeIntAccess, way);
        assertEquals(18, avSpeedEnc.getDecimal(false, edgeId, edgeIntAccess), 1e-1);

        way = new ReaderWay(1);
        way.setTag("highway", "primary");
        way.setTag("maxspeed:backward", "20");
        edgeIntAccess = ArrayEdgeIntAccess.createFromBytes(em.getBytesForFlags());
        speedParser.handleWayTags(edgeId, edgeIntAccess, way);
        assertEquals(66, avSpeedEnc.getDecimal(false, edgeId, edgeIntAccess), 1e-1);
        assertEquals(18, avSpeedEnc.getDecimal(true, edgeId, edgeIntAccess), 1e-1);

        way = new ReaderWay(1);
        way.setTag("highway", "motorway");
        way.setTag("maxspeed", "none");
        edgeIntAccess = ArrayEdgeIntAccess.createFromBytes(em.getBytesForFlags());
        speedParser.handleWayTags(edgeId, edgeIntAccess, way);
        assertEquals(136, avSpeedEnc.getDecimal(false, edgeId, edgeIntAccess), .1);

        way = new ReaderWay(1);
        way.setTag("highway", "motorway_link");
        way.setTag("maxspeed", "70 mph");
        edgeIntAccess = ArrayEdgeIntAccess.createFromBytes(em.getBytesForFlags());
        speedParser.handleWayTags(edgeId, edgeIntAccess, way);
        assertEquals(102, avSpeedEnc.getDecimal(true, edgeId, edgeIntAccess), 1e-1);
    }

    @Test
    public void testSpeed() {
        // limit bigger than default road speed
        ReaderWay way = new ReaderWay(1);
        way.setTag("highway", "trunk");
        way.setTag("maxspeed", "110");
        EdgeIntAccess edgeIntAccess = ArrayEdgeIntAccess.createFromBytes(em.getBytesForFlags());
        int edgeId = 0;
        speedParser.handleWayTags(edgeId, edgeIntAccess, way);
        assertEquals(100, avSpeedEnc.getDecimal(false, edgeId, edgeIntAccess), 1e-1);

        way.clearTags();
        way.setTag("highway", "residential");
        way.setTag("surface", "cobblestone");
        edgeIntAccess = ArrayEdgeIntAccess.createFromBytes(em.getBytesForFlags());
        speedParser.handleWayTags(edgeId, edgeIntAccess, way);
        assertEquals(30, avSpeedEnc.getDecimal(false, edgeId, edgeIntAccess), 1e-1);

        way.clearTags();
        way.setTag("highway", "track");
        edgeIntAccess = ArrayEdgeIntAccess.createFromBytes(em.getBytesForFlags());
        speedParser.handleWayTags(edgeId, edgeIntAccess, way);
        assertEquals(16, avSpeedEnc.getDecimal(false, edgeId, edgeIntAccess), 1e-1);

        way.clearTags();
        way.setTag("highway", "track");
        way.setTag("tracktype", "grade1");
        edgeIntAccess = ArrayEdgeIntAccess.createFromBytes(em.getBytesForFlags());
        speedParser.handleWayTags(edgeId, edgeIntAccess, way);
        assertEquals(20, avSpeedEnc.getDecimal(false, edgeId, edgeIntAccess), 1e-1);

        way.clearTags();
        way.setTag("highway", "secondary");
        way.setTag("surface", "compacted");
        edgeIntAccess = ArrayEdgeIntAccess.createFromBytes(em.getBytesForFlags());
        speedParser.handleWayTags(edgeId, edgeIntAccess, way);

        assertEquals(30, avSpeedEnc.getDecimal(false, edgeId, edgeIntAccess), 1e-1);

        way.clearTags();
        way.setTag("highway", "secondary");
        way.setTag("motorroad", "yes"); // motorroad should not influence speed. only access for non-motor vehicles
        edgeIntAccess = ArrayEdgeIntAccess.createFromBytes(em.getBytesForFlags());
        speedParser.handleWayTags(edgeId, edgeIntAccess, way);

        assertEquals(60, avSpeedEnc.getDecimal(false, edgeId, edgeIntAccess), 1e-1);

        way.clearTags();
        way.setTag("highway", "motorway");
        way.setTag("motorroad", "yes");
        edgeIntAccess = ArrayEdgeIntAccess.createFromBytes(em.getBytesForFlags());
        speedParser.handleWayTags(edgeId, edgeIntAccess, way);
        assertEquals(100, avSpeedEnc.getDecimal(false, edgeId, edgeIntAccess), 1e-1);

        way.clearTags();
        way.setTag("highway", "motorway_link");
        way.setTag("motorroad", "yes");
        edgeIntAccess = ArrayEdgeIntAccess.createFromBytes(em.getBytesForFlags());
        speedParser.handleWayTags(edgeId, edgeIntAccess, way);
        assertEquals(70, avSpeedEnc.getDecimal(false, edgeId, edgeIntAccess), 1e-1);

        try {
            avSpeedEnc.setDecimal(false, edgeId, edgeIntAccess, -1);
            assertTrue(false);
        } catch (IllegalArgumentException ex) {
        }
    }

    @Test
    public void testSetSpeed() {
        EdgeIntAccess edgeIntAccess = ArrayEdgeIntAccess.createFromBytes(em.getBytesForFlags());
        int edgeId = 0;
        avSpeedEnc.setDecimal(false, edgeId, edgeIntAccess, 10);
        assertEquals(10, avSpeedEnc.getDecimal(false, edgeId, edgeIntAccess), 1e-1);
    }

    @Test
    public void testSetSpeed0_issue367_issue1234() {
        EdgeIntAccess edgeIntAccess = ArrayEdgeIntAccess.createFromBytes(em.getBytesForFlags());
        int edgeId = 0;
        accessEnc.setBool(false, edgeId, edgeIntAccess, true);
        accessEnc.setBool(true, edgeId, edgeIntAccess, true);
        speedParser.setSpeed(false, edgeId, edgeIntAccess, 30);
        speedParser.setSpeed(true, edgeId, edgeIntAccess, 40);

        // exception for very low speed values
        assertThrows(IllegalArgumentException.class, () -> speedParser.setSpeed(false, edgeId, edgeIntAccess, 0.09));

        // this is independent from the speed
        assertTrue(accessEnc.getBool(false, edgeId, edgeIntAccess));

        // and does not affect the reverse direction:
        assertEquals(40, avSpeedEnc.getDecimal(true, edgeId, edgeIntAccess), .1);
        assertTrue(accessEnc.getBool(true, edgeId, edgeIntAccess));

        // for low speed values (and low precision of the EncodedValue) it can happen that the speed is increased:
        speedParser.setSpeed(false, edgeId, edgeIntAccess, 1);
        assertEquals(avSpeedEnc.getSmallestNonZeroValue(), avSpeedEnc.getDecimal(false, edgeId, edgeIntAccess), .1);

        assertTrue(accessEnc.getBool(true, edgeId, edgeIntAccess));
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
        way.setTag("highway", "motorway");
        edgeIntAccess = ArrayEdgeIntAccess.createFromBytes(em.getBytesForFlags());
        parser.handleWayTags(edgeId, edgeIntAccess, way, null);
        assertTrue(accessEnc.getBool(false, edgeId, edgeIntAccess));
        assertTrue(accessEnc.getBool(true, edgeId, edgeIntAccess));
        assertFalse(roundaboutEnc.getBool(false, edgeId, edgeIntAccess));
    }

    @Test
    public void testRailway() {
        ReaderWay way = new ReaderWay(1);
        way.setTag("highway", "secondary");
        way.setTag("railway", "rail");
        assertTrue(hasAccess(way));

        way.clearTags();
        way.setTag("highway", "path");
        way.setTag("railway", "abandoned");
        // ModeAccessParser blocks path via motor_vehicle=no default
        assertFalse(hasAccess(way));

        way.setTag("highway", "track");
        assertTrue(hasAccess(way));

        // this is fully okay as sometimes old rails are on the road
        way.setTag("highway", "primary");
        way.setTag("railway", "historic");
        assertTrue(hasAccess(way));

        way.setTag("motorcar", "no");
        assertFalse(hasAccess(way));

        way = new ReaderWay(1);
        way.setTag("highway", "secondary");
        way.setTag("railway", "tram");
        // but allow tram to be on the same way
        assertTrue(hasAccess(way));
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
        assertTrue(hasAccess(way));

        // test for very short and slow 0.5km/h still realistic ferry
        way = new ReaderWay(1);
        way.setTag("route", "ferry");
        way.setTag("motorcar", "yes");
        way.setTag("way_distance_2d", 100.0);
        way.setTag("duration_in_seconds", 12.0);
        assertTrue(hasAccess(way));

        // test for missing duration
        way = new ReaderWay(1);
        way.setTag("route", "ferry");
        way.setTag("motorcar", "yes");
        way.setTag("edge_distance", 100.0);
        assertTrue(hasAccess(way));

        way.clearTags();
        way.setTag("route", "ferry");
        assertTrue(hasAccess(way));
        way.setTag("motorcar", "no");
        assertFalse(hasAccess(way));

        way.clearTags();
        way.setTag("route", "ferry");
        way.setTag("foot", "yes");
        assertFalse(hasAccess(way));

        way.clearTags();
        way.setTag("route", "ferry");
        way.setTag("foot", "designated");
        way.setTag("motor_vehicle", "designated");
        assertTrue(hasAccess(way));

        way.clearTags();
        way.setTag("route", "ferry");
        way.setTag("access", "no");
        assertFalse(hasAccess(way));
        way.setTag("vehicle", "yes");
        assertTrue(hasAccess(way));

        // issue #1432
        way.clearTags();
        way.setTag("route", "ferry");
        way.setTag("oneway", "yes");
        EdgeIntAccess edgeIntAccess = ArrayEdgeIntAccess.createFromBytes(em.getBytesForFlags());
        int edgeId = 0;
        parser.handleWayTags(edgeId, edgeIntAccess, way, null);
        assertTrue(accessEnc.getBool(false, edgeId, edgeIntAccess));
        assertFalse(accessEnc.getBool(true, edgeId, edgeIntAccess));

        // speed for ferry is moved out of the encoded value, i.e. it is 0
        edgeIntAccess = ArrayEdgeIntAccess.createFromBytes(em.getBytesForFlags());
        parser.handleWayTags(edgeId, edgeIntAccess, way, null);
        assertEquals(0, avSpeedEnc.getDecimal(false, edgeId, edgeIntAccess));
    }

    @Test
    public void testBarrierAccess() {
        ReaderWay way = new ReaderWay(1);
        way.setTag("highway", "secondary");
        way.setTag("gh:barrier_edge", true);

        // lift_gate with access=yes => no barrier
        way.setTag("node_tags", List.of(Map.of("barrier", "lift_gate", "access", "yes"), Map.of()));
        assertTrue(hasAccess(way));

        // lift_gate with access=no, motorcar=yes => no barrier
        way.setTag("node_tags", List.of(Map.of("barrier", "lift_gate", "access", (Object) "no", "motorcar", "yes"), Map.of()));
        assertTrue(hasAccess(way));

        // bollard => barrier!
        way.setTag("node_tags", List.of(Map.of("barrier", "bollard"), Map.of()));
        assertFalse(hasAccess(way));

        // cattle_grid is not a barrier
        way.setTag("node_tags", List.of(Map.of("barrier", "cattle_grid"), Map.of()));
        assertTrue(hasAccess(way));
    }

    @Test
    public void testChainBarrier() {
        ReaderWay way = new ReaderWay(1);
        way.setTag("highway", "secondary");
        way.setTag("gh:barrier_edge", true);

        // chain is not in the CAR_BARRIERS set => not blocked
        way.setTag("node_tags", List.of(Map.of("barrier", "chain"), Map.of()));
        assertTrue(hasAccess(way));

        // chain with motor_vehicle=no => blocked
        way.setTag("node_tags", List.of(Map.of("barrier", "chain", "motor_vehicle", "no"), Map.of()));
        assertFalse(hasAccess(way));

        // chain with motor_vehicle=yes => not blocked
        way.setTag("node_tags", List.of(Map.of("barrier", "chain", "motor_vehicle", "yes"), Map.of()));
        assertTrue(hasAccess(way));
    }

    @Test
    public void testMaxValue() {
        DecimalEncodedValueImpl smallFactorSpeedEnc = new DecimalEncodedValueImpl("car_average_speed", 10, 0.5, true);
        EncodingManager em = new EncodingManager.Builder()
                .add(new SimpleBooleanEncodedValue("car_access", true))
                .add(smallFactorSpeedEnc)
                .add(FerrySpeed.create())
                .addTurnCostEncodedValue(TurnCost.create("car", 1))
                .build();
        CarAverageSpeedParser speedParser = new CarAverageSpeedParser(em);
        ReaderWay way = new ReaderWay(1);
        way.setTag("highway", "motorway_link");
        way.setTag("maxspeed", "60 mph");
        EdgeIntAccess edgeIntAccess = ArrayEdgeIntAccess.createFromBytes(em.getBytesForFlags());
        int edgeId = 0;
        speedParser.handleWayTags(edgeId, edgeIntAccess, way);

        // double speed = AbstractFlagEncoder.parseSpeed("60 mph");
        // => 96.56 * 0.9 => 86.9
        assertEquals(86.9, smallFactorSpeedEnc.getDecimal(false, edgeId, edgeIntAccess), 1e-1);
        assertEquals(86.9, smallFactorSpeedEnc.getDecimal(true, edgeId, edgeIntAccess), 1e-1);

        // test that maxPossibleValue  is not exceeded
        way = new ReaderWay(2);
        way.setTag("highway", "motorway_link");
        way.setTag("maxspeed", "70 mph");
        edgeIntAccess = ArrayEdgeIntAccess.createFromBytes(em.getBytesForFlags());
        speedParser.handleWayTags(edgeId, edgeIntAccess, way);
        assertEquals(101.5, smallFactorSpeedEnc.getDecimal(false, edgeId, edgeIntAccess), .1);
    }

    @Test
    public void testCombination() {
        ReaderWay way = new ReaderWay(123);
        way.setTag("highway", "cycleway");
        way.setTag("sac_scale", "hiking");

        // cycleway blocked for car via motor_vehicle=no default
        assertFalse(hasAccess(way));

        BikeAccessParser bikeParser = new BikeAccessParser(em, new PMap());
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
    public void testApplyBadSurfaceSpeed() {
        ReaderWay way = new ReaderWay(1);
        way.setTag("highway", "secondary");
        way.setTag("surface", "unpaved");
        assertEquals(30, speedParser.applyBadSurfaceSpeed(way, 90), 1e-1);
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
        // private is in INTENDED set but also in the car-specific restricted values, so it lifts the block
        assertTrue(accessEnc.getBool(false, edgeId, access));
    }

    @ParameterizedTest
    @ValueSource(strings = {"footway", "cycleway", "steps", "pedestrian", "path", "bridleway"})
    void blocked_highways_for_car(String highway) {
        ReaderWay way = new ReaderWay(1);
        way.setTag("highway", highway);
        assertFalse(hasAccess(way));
    }

    @Test
    void nonHighwaysFallbackSpeed_issue2845() {
        ReaderWay way = new ReaderWay(1);
        way.setTag("man_made", "pier");
        EdgeIntAccess edgeIntAccess = ArrayEdgeIntAccess.createFromBytes(em.getBytesForFlags());
        speedParser.handleWayTags(0, edgeIntAccess, way);
        assertEquals(10, avSpeedEnc.getDecimal(false, 0, edgeIntAccess), 1e-1);

        way.clearTags();
        way.setTag("railway", "platform");
        speedParser.handleWayTags(0, edgeIntAccess = ArrayEdgeIntAccess.createFromBytes(em.getBytesForFlags()), way);
        assertEquals(10, avSpeedEnc.getDecimal(false, 0, edgeIntAccess), 1e-1);

        way.clearTags();
        way.setTag("route", "ski");
        speedParser.handleWayTags(0, edgeIntAccess = ArrayEdgeIntAccess.createFromBytes(em.getBytesForFlags()), way);
        assertEquals(10, avSpeedEnc.getDecimal(false, 0, edgeIntAccess), 1e-1);

        way.clearTags();
        way.setTag("highway", "abandoned");
        speedParser.handleWayTags(0, edgeIntAccess = ArrayEdgeIntAccess.createFromBytes(em.getBytesForFlags()), way);
        assertEquals(10, avSpeedEnc.getDecimal(false, 0, edgeIntAccess), 1e-1);

        way.clearTags();
        way.setTag("highway", "construction");
        way.setTag("maxspeed", "100");
        speedParser.handleWayTags(0, edgeIntAccess = ArrayEdgeIntAccess.createFromBytes(em.getBytesForFlags()), way);
        // unknown highways can be quite fast in combination with maxspeed!?
        assertEquals(90, avSpeedEnc.getDecimal(false, 0, edgeIntAccess), 1e-1);
    }

    @Test
    void testPedestrianAccess() {
        // pedestrian is blocked via motor_vehicle=no default
        ReaderWay way = new ReaderWay(1);
        way.setTag("highway", "pedestrian");
        assertFalse(hasAccess(way));

        // explicit motorcar=destination overrides the default
        way.clearTags();
        way.setTag("highway", "pedestrian");
        way.setTag("motorcar", "destination");
        assertTrue(hasAccess(way));

        way.clearTags();
        way.setTag("highway", "pedestrian");
        way.setTag("motor_vehicle", "destination");
        assertTrue(hasAccess(way));

        way.clearTags();
        way.setTag("highway", "pedestrian");
        way.setTag("motor_vehicle:conditional", "destination @ ( 8:00 - 10:00 )");
        // temporal restriction with an intended value should lift the block
        assertTrue(hasAccess(way));
    }
}
