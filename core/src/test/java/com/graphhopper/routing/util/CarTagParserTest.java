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
package com.graphhopper.routing.util;

import com.graphhopper.reader.ReaderNode;
import com.graphhopper.reader.ReaderWay;
import com.graphhopper.reader.osm.conditional.DateRangeParser;
import com.graphhopper.routing.ev.*;
import com.graphhopper.storage.IntsRef;
import com.graphhopper.util.Helper;
import com.graphhopper.util.PMap;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.text.DateFormat;
import java.util.Arrays;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Peter Karich
 */
public class CarTagParserTest {
    private final EncodingManager em = createEncodingManager(getCarName());
    final CarTagParser parser = createParser(em, new PMap("block_fords=true"));
    private final BooleanEncodedValue roundaboutEnc = em.getBooleanEncodedValue(Roundabout.KEY);
    private final BooleanEncodedValue accessEnc = parser.getAccessEnc();
    private final DecimalEncodedValue avSpeedEnc = parser.getAverageSpeedEnc();

    protected String getCarName() {
        return "car";
    }

    private EncodingManager createEncodingManager(String carName) {
        return new EncodingManager.Builder()
                .add(VehicleAccess.create(carName))
                .add(VehicleSpeed.create(carName, 5, 5, true))
                .addTurnCostEncodedValue(TurnCost.create(carName, 1))
                .add(VehicleAccess.create("bike"))
                .add(VehicleSpeed.create("bike", 4, 2, false))
                .add(VehiclePriority.create("bike", 4, PriorityCode.getFactor(1), false))
                .add(new EnumEncodedValue<>(BikeNetwork.KEY, RouteNetwork.class))
                .add(new EnumEncodedValue<>(Smoothness.KEY, Smoothness.class))
                .build();
    }

    CarTagParser createParser(EncodedValueLookup lookup, PMap properties) {
        CarTagParser carTagParser = new CarTagParser(lookup, properties);
        carTagParser.init(new DateRangeParser());
        return carTagParser;
    }

    @Test
    public void testAccess() {
        ReaderWay way = new ReaderWay(1);
        assertTrue(parser.getAccess(way).canSkip());
        way.setTag("highway", "service");
        assertTrue(parser.getAccess(way).isWay());
        way.setTag("access", "no");
        assertTrue(parser.getAccess(way).canSkip());

        way.clearTags();
        way.setTag("highway", "track");
        assertTrue(parser.getAccess(way).isWay());

        way.setTag("motorcar", "no");
        assertTrue(parser.getAccess(way).canSkip());

        // for now allow grade1+2+3 for every country, see #253
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
        assertTrue(parser.getAccess(way).canSkip());
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
        assertTrue(parser.getAccess(way).canSkip());

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

        way.clearTags();
        way.setTag("highway", "service");
        way.setTag("motor_vehicle", "emergency");
        assertTrue(parser.getAccess(way).canSkip());

        DateFormat simpleDateFormat = Helper.createFormatter("yyyy MMM dd");

        way.clearTags();
        way.setTag("highway", "road");
        way.setTag("access:conditional", "no @ (" + simpleDateFormat.format(new Date().getTime()) + ")");
        assertTrue(parser.getAccess(way).canSkip());

        way.clearTags();
        way.setTag("highway", "road");
        way.setTag("access", "no");
        way.setTag("access:conditional", "yes @ (" + simpleDateFormat.format(new Date().getTime()) + ")");
        assertTrue(parser.getAccess(way).isWay());

        way.clearTags();
        way.setTag("highway", "service");
        way.setTag("service", "emergency_access");
        assertTrue(parser.getAccess(way).canSkip());
    }

    @Test
    public void testMilitaryAccess() {
        ReaderWay way = new ReaderWay(1);
        way.setTag("highway", "track");
        way.setTag("access", "military");
        assertTrue(parser.getAccess(way).canSkip());
    }

    @Test
    public void testFordAccess() {
        ReaderNode node = new ReaderNode(0, 0.0, 0.0);
        node.setTag("ford", "yes");

        ReaderWay way = new ReaderWay(1);
        way.setTag("highway", "unclassified");
        way.setTag("ford", "yes");

        // Node and way are initially blocking
        assertTrue(parser.isBlockFords());
        assertTrue(parser.getAccess(way).canSkip());
        assertTrue(parser.isBarrier(node));

        CarTagParser tmpParser = createParser(em, new PMap("block_fords=false"));
        assertTrue(tmpParser.getAccess(way).isWay());
        assertFalse(tmpParser.isBarrier(node));
    }

    @Test
    public void testOneway() {
        ReaderWay way = new ReaderWay(1);
        way.setTag("highway", "primary");
        IntsRef flags = parser.handleWayTags(em.createEdgeFlags(), way);
        assertTrue(accessEnc.getBool(false, flags));
        assertTrue(accessEnc.getBool(true, flags));
        way.setTag("oneway", "yes");
        flags = parser.handleWayTags(em.createEdgeFlags(), way);
        assertTrue(accessEnc.getBool(false, flags));
        assertFalse(accessEnc.getBool(true, flags));
        way.clearTags();

        way.setTag("highway", "tertiary");
        flags = parser.handleWayTags(em.createEdgeFlags(), way);
        assertTrue(accessEnc.getBool(false, flags));
        assertTrue(accessEnc.getBool(true, flags));
        way.clearTags();

        way.setTag("highway", "tertiary");
        way.setTag("vehicle:forward", "no");
        flags = parser.handleWayTags(em.createEdgeFlags(), way);
        assertFalse(accessEnc.getBool(false, flags));
        assertTrue(accessEnc.getBool(true, flags));
        way.clearTags();

        way.setTag("highway", "tertiary");
        way.setTag("vehicle:backward", "no");
        flags = parser.handleWayTags(em.createEdgeFlags(), way);
        assertTrue(accessEnc.getBool(false, flags));
        assertFalse(accessEnc.getBool(true, flags));
        way.clearTags();

        // This is no one way
        way.setTag("highway", "tertiary");
        way.setTag("vehicle:backward", "designated");
        flags = parser.handleWayTags(em.createEdgeFlags(), way);
        assertTrue(accessEnc.getBool(false, flags));
        assertTrue(accessEnc.getBool(true, flags));
        way.clearTags();
    }

    @Test
    public void testSetAccess() {
        IntsRef edgeFlags = em.createEdgeFlags();
        accessEnc.setBool(false, edgeFlags, true);
        accessEnc.setBool(true, edgeFlags, true);
        assertTrue(accessEnc.getBool(false, edgeFlags));
        assertTrue(accessEnc.getBool(true, edgeFlags));

        accessEnc.setBool(false, edgeFlags, true);
        accessEnc.setBool(true, edgeFlags, false);
        assertTrue(accessEnc.getBool(false, edgeFlags));
        assertFalse(accessEnc.getBool(true, edgeFlags));

        accessEnc.setBool(false, edgeFlags, false);
        accessEnc.setBool(true, edgeFlags, true);
        assertFalse(accessEnc.getBool(false, edgeFlags));
        assertTrue(accessEnc.getBool(true, edgeFlags));

        accessEnc.setBool(false, edgeFlags, false);
        accessEnc.setBool(true, edgeFlags, false);
        assertFalse(accessEnc.getBool(true, edgeFlags));
    }

    @Test
    public void testMaxSpeed() {
        ReaderWay way = new ReaderWay(1);
        way.setTag("highway", "trunk");
        way.setTag("maxspeed", "500");
        IntsRef edgeFlags = em.createEdgeFlags();
        parser.handleWayTags(edgeFlags, way);
        assertEquals(140, avSpeedEnc.getDecimal(false, edgeFlags), 1e-1);

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
        assertEquals(135, avSpeedEnc.getDecimal(false, edgeFlags), .1);

        way = new ReaderWay(1);
        way.setTag("highway", "motorway_link");
        way.setTag("maxspeed", "70 mph");
        IntsRef flags = parser.handleWayTags(em.createEdgeFlags(), way);
        assertEquals(100, avSpeedEnc.getDecimal(true, flags), 1e-1);
    }

    @Test
    public void testSpeed() {
        // limit bigger than default road speed
        ReaderWay way = new ReaderWay(1);
        way.setTag("highway", "trunk");
        way.setTag("maxspeed", "110");
        IntsRef edgeFlags = parser.handleWayTags(em.createEdgeFlags(), way);
        assertEquals(100, avSpeedEnc.getDecimal(false, edgeFlags), 1e-1);

        way.clearTags();
        way.setTag("highway", "residential");
        way.setTag("surface", "cobblestone");
        edgeFlags = parser.handleWayTags(em.createEdgeFlags(), way);
        assertEquals(30, avSpeedEnc.getDecimal(false, edgeFlags), 1e-1);

        way.clearTags();
        way.setTag("highway", "track");
        edgeFlags = parser.handleWayTags(em.createEdgeFlags(), way);
        assertEquals(15, avSpeedEnc.getDecimal(false, edgeFlags), 1e-1);

        way.clearTags();
        way.setTag("highway", "track");
        way.setTag("tracktype", "grade1");
        edgeFlags = parser.handleWayTags(em.createEdgeFlags(), way);
        assertEquals(20, avSpeedEnc.getDecimal(false, edgeFlags), 1e-1);

        way.clearTags();
        way.setTag("highway", "secondary");
        way.setTag("surface", "compacted");
        edgeFlags = parser.handleWayTags(em.createEdgeFlags(), way);
        assertEquals(30, avSpeedEnc.getDecimal(false, edgeFlags), 1e-1);

        way.clearTags();
        way.setTag("highway", "secondary");
        way.setTag("motorroad", "yes"); // motorroad should not influence speed. only access for non-motor vehicles
        edgeFlags = parser.handleWayTags(em.createEdgeFlags(), way);
        assertEquals(60, avSpeedEnc.getDecimal(false, edgeFlags), 1e-1);

        way.clearTags();
        way.setTag("highway", "motorway");
        way.setTag("motorroad", "yes");
        edgeFlags = parser.handleWayTags(em.createEdgeFlags(), way);
        assertEquals(100, avSpeedEnc.getDecimal(false, edgeFlags), 1e-1);

        way.clearTags();
        way.setTag("highway", "motorway_link");
        way.setTag("motorroad", "yes");
        edgeFlags = parser.handleWayTags(em.createEdgeFlags(), way);
        assertEquals(70, avSpeedEnc.getDecimal(false, edgeFlags), 1e-1);

        try {
            avSpeedEnc.setDecimal(false, em.createEdgeFlags(), -1);
            assertTrue(false);
        } catch (IllegalArgumentException ex) {
        }
    }

    @Test
    public void testSetSpeed() {
        IntsRef edgeFlags = em.createEdgeFlags();
        avSpeedEnc.setDecimal(false, edgeFlags, 10);
        assertEquals(10, avSpeedEnc.getDecimal(false, edgeFlags), 1e-1);
    }

    @Test
    public void testSetSpeed0_issue367() {
        IntsRef edgeFlags = em.createEdgeFlags();
        accessEnc.setBool(false, edgeFlags, true);
        accessEnc.setBool(true, edgeFlags, true);

        parser.setSpeed(false, edgeFlags, parser.avgSpeedEnc.getSmallestNonZeroValue() - 0.01);

        // one direction effects the other direction as one encoder for speed but this is not true for access
        assertEquals(0, avSpeedEnc.getDecimal(false, edgeFlags), .1);
        assertEquals(0, avSpeedEnc.getDecimal(true, edgeFlags), .1);
        assertFalse(accessEnc.getBool(false, edgeFlags));
        assertTrue(accessEnc.getBool(true, edgeFlags));

        // so always call this method with reverse=true too
        parser.setSpeed(true, edgeFlags, parser.avgSpeedEnc.getSmallestNonZeroValue() - 0.01);
        assertFalse(accessEnc.getBool(true, edgeFlags));
    }

    @Test
    public void testRoundabout() {
        IntsRef edgeFlags = em.createEdgeFlags();
        accessEnc.setBool(false, edgeFlags, true);
        accessEnc.setBool(true, edgeFlags, true);
        roundaboutEnc.setBool(false, edgeFlags, true);
        assertTrue(roundaboutEnc.getBool(false, edgeFlags));
        assertTrue(accessEnc.getBool(false, edgeFlags));
        assertTrue(accessEnc.getBool(true, edgeFlags));

        roundaboutEnc.setBool(false, edgeFlags, false);
        assertFalse(roundaboutEnc.getBool(false, edgeFlags));
        assertTrue(accessEnc.getBool(false, edgeFlags));
        assertTrue(accessEnc.getBool(true, edgeFlags));

        ReaderWay way = new ReaderWay(1);
        way.setTag("highway", "motorway");
        edgeFlags = parser.handleWayTags(em.createEdgeFlags(), way);
        assertTrue(accessEnc.getBool(false, edgeFlags));
        assertTrue(accessEnc.getBool(true, edgeFlags));
        assertFalse(roundaboutEnc.getBool(false, edgeFlags));
    }

    @Test
    public void testRailway() {
        ReaderWay way = new ReaderWay(1);
        way.setTag("highway", "secondary");
        way.setTag("railway", "rail");
        assertTrue(parser.getAccess(way).isWay());

        way.clearTags();
        way.setTag("highway", "path");
        way.setTag("railway", "abandoned");
        assertTrue(parser.getAccess(way).canSkip());

        way.setTag("highway", "track");
        assertTrue(parser.getAccess(way).isWay());

        // this is fully okay as sometimes old rails are on the road
        way.setTag("highway", "primary");
        way.setTag("railway", "historic");
        assertTrue(parser.getAccess(way).isWay());

        way.setTag("motorcar", "no");
        assertTrue(parser.getAccess(way).canSkip());

        way = new ReaderWay(1);
        way.setTag("highway", "secondary");
        way.setTag("railway", "tram");
        // but allow tram to be on the same way
        assertTrue(parser.getAccess(way).isWay());
    }

    @Test
    public void testFerry() {
        ReaderWay way = new ReaderWay(1);
        way.setTag("route", "shuttle_train");
        way.setTag("motorcar", "yes");
        way.setTag("bicycle", "no");
        // Provide the duration value in seconds:
        way.setTag("way_distance", 50000.0);
        way.setTag("speed_from_duration", 50 / (35.0 / 60));
        way.setTag("duration:seconds", 35L * 60);
        // accept
        assertTrue(parser.getAccess(way).isFerry());
        IntsRef edgeFlags = em.createEdgeFlags();
        // calculate speed from tags: speed_from_duration * 1.4 (+ rounded using the speed factor)
        parser.handleWayTags(edgeFlags, way);
        assertEquals(60, parser.getAverageSpeedEnc().getDecimal(false, edgeFlags));

        //Test for very short and slow 0.5km/h still realistic ferry
        way = new ReaderWay(1);
        way.setTag("route", "ferry");
        way.setTag("motorcar", "yes");
        // Provide the duration of 12 minutes in seconds:
        way.setTag("duration:seconds", 12L * 60);
        way.setTag("way_distance", 100.0);
        way.setTag("speed_from_duration", 0.1 / (12.0 / 60));
        // accept
        assertTrue(parser.getAccess(way).isFerry());
        // We can't store 0.5km/h, but we expect the lowest possible speed (5km/h)
        edgeFlags = em.createEdgeFlags();
        parser.handleWayTags(edgeFlags, way);
        assertEquals(5, parser.getAverageSpeedEnc().getDecimal(false, edgeFlags));

        edgeFlags = em.createEdgeFlags();
        avSpeedEnc.setDecimal(false, edgeFlags, 2.5);
        assertEquals(5, avSpeedEnc.getDecimal(false, edgeFlags), 1e-1);

        //Test for missing duration
        way = new ReaderWay(1);
        way.setTag("route", "ferry");
        way.setTag("motorcar", "yes");
        way.setTag("edge_distance", 100.0);
        // accept
        assertTrue(parser.getAccess(way).isFerry());
        parser.handleWayTags(edgeFlags, way);
        // We use the unknown speed
        assertEquals(5, parser.getAverageSpeedEnc().getDecimal(false, edgeFlags));

        way.clearTags();
        way.setTag("route", "ferry");
        assertTrue(parser.getAccess(way).isFerry());
        way.setTag("motorcar", "no");
        assertTrue(parser.getAccess(way).canSkip());

        way.clearTags();
        way.setTag("route", "ferry");
        way.setTag("foot", "yes");
        assertTrue(parser.getAccess(way).canSkip());

        way.clearTags();
        way.setTag("route", "ferry");
        way.setTag("foot", "designated");
        way.setTag("motor_vehicle", "designated");
        assertTrue(parser.getAccess(way).isFerry());

        way.clearTags();
        way.setTag("route", "ferry");
        way.setTag("access", "no");
        assertTrue(parser.getAccess(way).canSkip());
        way.setTag("vehicle", "yes");
        assertTrue(parser.getAccess(way).isFerry());
    }

    @Test
    public void testBarrierAccess() {
        ReaderNode node = new ReaderNode(1, -1, -1);
        node.setTag("barrier", "lift_gate");
        node.setTag("access", "yes");
        // no barrier!
        assertFalse(parser.isBarrier(node));

        node = new ReaderNode(1, -1, -1);
        node.setTag("barrier", "lift_gate");
        node.setTag("bicycle", "yes");
        // no barrier!
        assertFalse(parser.isBarrier(node));

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
        assertFalse(parser.isBarrier(node));

        node = new ReaderNode(1, -1, -1);
        node.setTag("barrier", "bollard");
        // barrier!
        assertTrue(parser.isBarrier(node));

        // Test if cattle_grid is not blocking
        node = new ReaderNode(1, -1, -1);
        node.setTag("barrier", "cattle_grid");
        assertFalse(parser.isBarrier(node));
    }

    @Test
    public void testChainBarrier() {
        // by default allow access through the gate for bike & foot!
        ReaderNode node = new ReaderNode(1, -1, -1);
        node.setTag("barrier", "chain");
        assertFalse(parser.isBarrier(node));
        node.setTag("motor_vehicle", "no");
        assertTrue(parser.isBarrier(node));
        node.setTag("motor_vehicle", "yes");
        assertFalse(parser.isBarrier(node));
    }

    @Test
    public void testMaxValue() {
        DecimalEncodedValueImpl smallFactorSpeedEnc = new DecimalEncodedValueImpl(getCarName() + "_average_speed", 10, 0.5, false);
        EncodingManager em = new EncodingManager.Builder()
                .add(new SimpleBooleanEncodedValue(getCarName() + "_access", true))
                .add(smallFactorSpeedEnc)
                .addTurnCostEncodedValue(TurnCost.create(getCarName(), 1))
                .build();
        CarTagParser parser = createParser(em, new PMap());
        ReaderWay way = new ReaderWay(1);
        way.setTag("highway", "motorway_link");
        way.setTag("maxspeed", "60 mph");
        IntsRef edgeFlags = parser.handleWayTags(em.createEdgeFlags(), way);

        // double speed = AbstractFlagEncoder.parseSpeed("60 mph");
        // => 96.56 * 0.9 => 86.9
        assertEquals(86.9, smallFactorSpeedEnc.getDecimal(false, edgeFlags), 1e-1);
        assertEquals(86.9, smallFactorSpeedEnc.getDecimal(true, edgeFlags), 1e-1);

        // test that maxPossibleValue  is not exceeded
        way = new ReaderWay(2);
        way.setTag("highway", "motorway_link");
        way.setTag("maxspeed", "70 mph");
        edgeFlags = parser.handleWayTags(em.createEdgeFlags(), way);
        assertEquals(101.5, smallFactorSpeedEnc.getDecimal(false, edgeFlags), .1);
    }

    @Test
    public void testSetToMaxSpeed() {
        ReaderWay way = new ReaderWay(12);
        way.setTag("maxspeed", "90");
        assertEquals(90, VehicleTagParser.getMaxSpeed(way), 1e-2);
    }

    @Test
    public void testCombination() {
        ReaderWay way = new ReaderWay(123);
        way.setTag("highway", "cycleway");
        way.setTag("sac_scale", "hiking");

        BikeTagParser bikeParser = new BikeTagParser(em, new PMap());
        bikeParser.init(new DateRangeParser());
        assertEquals(WayAccess.CAN_SKIP, parser.getAccess(way));
        assertNotEquals(WayAccess.CAN_SKIP, bikeParser.getAccess(way));
        IntsRef edgeFlags = em.createEdgeFlags();
        parser.handleWayTags(edgeFlags, way);
        bikeParser.handleWayTags(edgeFlags, way);
        assertFalse(accessEnc.getBool(true, edgeFlags));
        assertFalse(accessEnc.getBool(false, edgeFlags));
        BooleanEncodedValue bikeAccessEnc = bikeParser.getAccessEnc();
        assertTrue(bikeAccessEnc.getBool(true, edgeFlags));
        assertTrue(bikeAccessEnc.getBool(false, edgeFlags));
    }

    @Test
    public void testApplyBadSurfaceSpeed() {
        ReaderWay way = new ReaderWay(1);
        way.setTag("highway", "secondary");
        way.setTag("surface", "unpaved");
        assertEquals(30, parser.applyBadSurfaceSpeed(way, 90), 1e-1);
    }

    @Test
    public void testIssue_1256() {
        ReaderWay way = new ReaderWay(1);
        way.setTag("route", "ferry");
        way.setTag("edge_distance", 257.0);

        // default is 5km/h minimum speed for car
        IntsRef edgeFlags = em.createEdgeFlags();
        parser.handleWayTags(edgeFlags, way);
        assertEquals(5, parser.getAverageSpeedEnc().getDecimal(false, edgeFlags), .1);

        // for a smaller speed factor the minimum speed is also smaller
        DecimalEncodedValueImpl lowFactorSpeedEnc = new DecimalEncodedValueImpl(getCarName() + "_average_speed", 10, 1, false);
        EncodingManager lowFactorEm = new EncodingManager.Builder()
                .add(new SimpleBooleanEncodedValue(getCarName() + "_access", true))
                .add(lowFactorSpeedEnc)
                .addTurnCostEncodedValue(TurnCost.create(getCarName(), 1))
                .build();
        edgeFlags = lowFactorEm.createEdgeFlags();
        createParser(lowFactorEm, new PMap()).handleWayTags(edgeFlags, way);
        assertEquals(1, lowFactorSpeedEnc.getDecimal(false, edgeFlags), .1);
    }

    @ParameterizedTest
    @ValueSource(strings = {"mofa", "moped", "motorcar", "motor_vehicle", "motorcycle"})
    void footway_etc_not_allowed_despite_vehicle_yes(String vehicle) {
        // these highways are blocked, even when we set one of the vehicles to yes
        for (String highway : Arrays.asList("footway", "cycleway", "steps", "pedestrian")) {
            ReaderWay way = new ReaderWay(1);
            way.setTag("highway", highway);
            way.setTag(vehicle, "yes");
            assertEquals(WayAccess.CAN_SKIP, parser.getAccess(way));
        }
    }
}
