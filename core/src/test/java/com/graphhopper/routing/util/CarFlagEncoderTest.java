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
import com.graphhopper.routing.profiles.BooleanEncodedValue;
import com.graphhopper.routing.profiles.DecimalEncodedValue;
import com.graphhopper.routing.profiles.EncodedValue;
import com.graphhopper.storage.IntsRef;
import com.graphhopper.util.Helper;
import org.junit.Test;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import static org.junit.Assert.*;

/**
 * @author Peter Karich
 */
public class CarFlagEncoderTest {
    private final EncodingManager em = EncodingManager.create("car,bike,foot");
    private final CarFlagEncoder encoder = (CarFlagEncoder) em.getEncoder("car");
    private final BooleanEncodedValue roundaboutEnc = em.getBooleanEncodedValue(EncodingManager.ROUNDABOUT);
    private final DecimalEncodedValue avSpeedEnc = encoder.getAverageSpeedEnc();
    private final BooleanEncodedValue accessEnc = encoder.getAccessEnc();

    @Test
    public void testAccess() {
        ReaderWay way = new ReaderWay(1);
        assertTrue(encoder.getAccess(way).canSkip());
        way.setTag("highway", "service");
        assertTrue(encoder.getAccess(way).isWay());
        way.setTag("access", "no");
        assertTrue(encoder.getAccess(way).canSkip());

        way.clearTags();
        way.setTag("highway", "track");
        assertTrue(encoder.getAccess(way).isWay());

        way.setTag("motorcar", "no");
        assertTrue(encoder.getAccess(way).canSkip());

        // for now allow grade1+2+3 for every country, see #253
        way.clearTags();
        way.setTag("highway", "track");
        way.setTag("tracktype", "grade2");
        assertTrue(encoder.getAccess(way).isWay());
        way.setTag("tracktype", "grade4");
        assertTrue(encoder.getAccess(way).canSkip());

        way.clearTags();
        way.setTag("highway", "service");
        way.setTag("access", "delivery");
        assertTrue(encoder.getAccess(way).canSkip());

        way.clearTags();
        way.setTag("highway", "unclassified");
        way.setTag("ford", "yes");
        assertTrue(encoder.getAccess(way).canSkip());
        way.setTag("motorcar", "yes");
        assertTrue(encoder.getAccess(way).isWay());

        way.clearTags();
        way.setTag("route", "ferry");
        assertTrue(encoder.getAccess(way).isFerry());
        way.setTag("motorcar", "no");
        assertTrue(encoder.getAccess(way).canSkip());

        way.clearTags();
        way.setTag("route", "ferry");
        way.setTag("foot", "yes");
        assertTrue(encoder.getAccess(way).canSkip());

        way.clearTags();
        way.setTag("route", "ferry");
        way.setTag("access", "no");
        assertTrue(encoder.getAccess(way).canSkip());
        way.setTag("vehicle", "yes");
        assertTrue(encoder.getAccess(way).isFerry());

        way.clearTags();
        way.setTag("access", "yes");
        way.setTag("motor_vehicle", "no");
        assertTrue(encoder.getAccess(way).canSkip());

        way.clearTags();
        way.setTag("highway", "service");
        way.setTag("access", "yes");
        way.setTag("motor_vehicle", "no");
        assertTrue(encoder.getAccess(way).canSkip());

        way.clearTags();
        way.setTag("highway", "service");
        way.setTag("access", "no");
        way.setTag("motorcar", "yes");
        assertTrue(encoder.getAccess(way).isWay());

        way.clearTags();
        way.setTag("highway", "service");
        way.setTag("access", "emergency");
        assertTrue(encoder.getAccess(way).canSkip());

        way.clearTags();
        way.setTag("highway", "service");
        way.setTag("motor_vehicle", "emergency");
        assertTrue(encoder.getAccess(way).canSkip());

        DateFormat simpleDateFormat = Helper.createFormatter("yyyy MMM dd");

        way.clearTags();
        way.setTag("highway", "road");
        way.setTag("access:conditional", "no @ (" + simpleDateFormat.format(new Date().getTime()) + ")");
        assertTrue(encoder.getAccess(way).canSkip());

        way.clearTags();
        way.setTag("highway", "road");
        way.setTag("access", "no");
        way.setTag("access:conditional", "yes @ (" + simpleDateFormat.format(new Date().getTime()) + ")");
        assertTrue(encoder.getAccess(way).isWay());
    }

    @Test
    public void testMilitaryAccess() {
        ReaderWay way = new ReaderWay(1);
        way.setTag("highway", "track");
        way.setTag("access", "military");
        assertTrue(encoder.getAccess(way).canSkip());
    }

    @Test
    public void testFordAccess() {
        ReaderNode node = new ReaderNode(0, 0.0, 0.0);
        node.setTag("ford", "yes");

        ReaderWay way = new ReaderWay(1);
        way.setTag("highway", "unclassified");
        way.setTag("ford", "yes");

        // Node and way are initially blocking
        assertTrue(encoder.isBlockFords());
        assertTrue(encoder.getAccess(way).canSkip());
        assertTrue(encoder.handleNodeTags(node) > 0);

        try {
            // Now they are passable
            encoder.setBlockFords(false);
            assertTrue(encoder.getAccess(way).isWay());
            assertFalse(encoder.handleNodeTags(node) > 0);
        } finally {
            encoder.setBlockFords(true);
        }
    }

    @Test
    public void testOneway() {
        ReaderWay way = new ReaderWay(1);
        way.setTag("highway", "primary");
        IntsRef flags = encoder.handleWayTags(em.createEdgeFlags(), way, encoder.getAccess(way), 0);
        assertTrue(accessEnc.getBool(false, flags));
        assertTrue(accessEnc.getBool(true, flags));
        way.setTag("oneway", "yes");
        flags = encoder.handleWayTags(em.createEdgeFlags(), way, encoder.getAccess(way), 0);
        assertTrue(accessEnc.getBool(false, flags));
        assertFalse(accessEnc.getBool(true, flags));
        way.clearTags();

        way.setTag("highway", "tertiary");
        flags = encoder.handleWayTags(em.createEdgeFlags(), way, encoder.getAccess(way), 0);
        assertTrue(accessEnc.getBool(false, flags));
        assertTrue(accessEnc.getBool(true, flags));
        way.clearTags();

        way.setTag("highway", "tertiary");
        way.setTag("vehicle:forward", "no");
        flags = encoder.handleWayTags(em.createEdgeFlags(), way, encoder.getAccess(way), 0);
        assertFalse(accessEnc.getBool(false, flags));
        assertTrue(accessEnc.getBool(true, flags));
        way.clearTags();

        way.setTag("highway", "tertiary");
        way.setTag("vehicle:backward", "no");
        flags = encoder.handleWayTags(em.createEdgeFlags(), way, encoder.getAccess(way), 0);
        assertTrue(accessEnc.getBool(false, flags));
        assertFalse(accessEnc.getBool(true, flags));
        way.clearTags();
    }


    @Test
    public void testDestinationTag() {
        ReaderWay way = new ReaderWay(1);
        way.setTag("highway", "secondary");
        assertEquals(60, encoder.getSpeed(way), 1e-1);

        way.setTag("vehicle", "destination");
        IntsRef flags = encoder.handleWayTags(em.createEdgeFlags(), way, encoder.getAccess(way), 0);
        assertEquals(5, avSpeedEnc.getDecimal(false, flags), 1e-1);
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

        encoder.flagsDefault(edgeFlags, true, true);
        assertTrue(accessEnc.getBool(false, edgeFlags));
        assertTrue(accessEnc.getBool(true, edgeFlags));

        encoder.flagsDefault(edgeFlags, true, false);
        assertTrue(accessEnc.getBool(false, edgeFlags));
        assertFalse(accessEnc.getBool(true, edgeFlags));

        encoder.flagsDefault(edgeFlags, true, true);
        // disable access
        accessEnc.setBool(false, edgeFlags, false);
        accessEnc.setBool(true, edgeFlags, false);
        assertFalse(accessEnc.getBool(false, edgeFlags));
        accessEnc.setBool(false, edgeFlags, false);
        accessEnc.setBool(true, edgeFlags, false);
        assertFalse(accessEnc.getBool(true, edgeFlags));
    }

    @Test
    public void testMaxSpeed() {
        ReaderWay way = new ReaderWay(1);
        way.setTag("highway", "trunk");
        way.setTag("maxspeed", "500");
        EncodingManager.Access allowed = encoder.getAccess(way);
        IntsRef edgeFlags = encoder.handleWayTags(em.createEdgeFlags(), way, allowed, 0);
        assertEquals(140, avSpeedEnc.getDecimal(false, edgeFlags), 1e-1);

        way = new ReaderWay(1);
        way.setTag("highway", "primary");
        way.setTag("maxspeed:backward", "10");
        way.setTag("maxspeed:forward", "20");
        edgeFlags = encoder.handleWayTags(em.createEdgeFlags(), way, encoder.getAccess(way), 0);
        assertEquals(10, avSpeedEnc.getDecimal(false, edgeFlags), 1e-1);

        way = new ReaderWay(1);
        way.setTag("highway", "primary");
        way.setTag("maxspeed:forward", "20");
        edgeFlags = encoder.handleWayTags(em.createEdgeFlags(), way, encoder.getAccess(way), 0);
        assertEquals(20, avSpeedEnc.getDecimal(false, edgeFlags), 1e-1);

        way = new ReaderWay(1);
        way.setTag("highway", "primary");
        way.setTag("maxspeed:backward", "20");
        edgeFlags = encoder.handleWayTags(em.createEdgeFlags(), way, encoder.getAccess(way), 0);
        assertEquals(20, avSpeedEnc.getDecimal(false, edgeFlags), 1e-1);

        way = new ReaderWay(1);
        way.setTag("highway", "motorway");
        way.setTag("maxspeed", "none");
        edgeFlags = encoder.handleWayTags(em.createEdgeFlags(), way, encoder.getAccess(way), 0);
        assertEquals(125, avSpeedEnc.getDecimal(false, edgeFlags), .1);
    }

    @Test
    public void testSpeed() {
        // limit bigger than default road speed
        ReaderWay way = new ReaderWay(1);
        way.setTag("highway", "trunk");
        way.setTag("maxspeed", "110");
        EncodingManager.Access allowed = encoder.getAccess(way);
        IntsRef edgeFlags = encoder.handleWayTags(em.createEdgeFlags(), way, allowed, 0);
        assertEquals(100, avSpeedEnc.getDecimal(false, edgeFlags), 1e-1);

        way.clearTags();
        way.setTag("highway", "residential");
        way.setTag("surface", "cobblestone");
        allowed = encoder.getAccess(way);
        edgeFlags = encoder.handleWayTags(em.createEdgeFlags(), way, allowed, 0);
        assertEquals(30, avSpeedEnc.getDecimal(false, edgeFlags), 1e-1);

        way.clearTags();
        way.setTag("highway", "track");
        allowed = encoder.getAccess(way);
        edgeFlags = encoder.handleWayTags(em.createEdgeFlags(), way, allowed, 0);
        assertEquals(15, avSpeedEnc.getDecimal(false, edgeFlags), 1e-1);

        way.clearTags();
        way.setTag("highway", "track");
        way.setTag("tracktype", "grade1");
        allowed = encoder.getAccess(way);
        edgeFlags = encoder.handleWayTags(em.createEdgeFlags(), way, allowed, 0);
        assertEquals(20, avSpeedEnc.getDecimal(false, edgeFlags), 1e-1);

        way.clearTags();
        way.setTag("highway", "secondary");
        way.setTag("surface", "compacted");
        allowed = encoder.getAccess(way);
        edgeFlags = encoder.handleWayTags(em.createEdgeFlags(), way, allowed, 0);
        assertEquals(30, avSpeedEnc.getDecimal(false, edgeFlags), 1e-1);

        way.clearTags();
        way.setTag("highway", "secondary");
        way.setTag("motorroad", "yes");
        allowed = encoder.getAccess(way);
        edgeFlags = encoder.handleWayTags(em.createEdgeFlags(), way, allowed, 0);
        assertEquals(90, avSpeedEnc.getDecimal(false, edgeFlags), 1e-1);

        way.clearTags();
        way.setTag("highway", "motorway");
        way.setTag("motorroad", "yes"); // this tag should be ignored
        allowed = encoder.getAccess(way);
        edgeFlags = encoder.handleWayTags(em.createEdgeFlags(), way, allowed, 0);
        assertEquals(100, avSpeedEnc.getDecimal(false, edgeFlags), 1e-1);

        way.clearTags();
        way.setTag("highway", "motorway_link");
        way.setTag("motorroad", "yes"); // this tag should be ignored
        allowed = encoder.getAccess(way);
        edgeFlags = encoder.handleWayTags(em.createEdgeFlags(), way, allowed, 0);
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

        encoder.setSpeed(false, edgeFlags, encoder.speedFactor * 0.49);

        // one direction effects the other direction as one encoder for speed but this is not true for access
        assertEquals(0, avSpeedEnc.getDecimal(false, edgeFlags), .1);
        assertEquals(0, avSpeedEnc.getDecimal(true, edgeFlags), .1);
        assertFalse(accessEnc.getBool(false, edgeFlags));
        assertTrue(accessEnc.getBool(true, edgeFlags));

        // so always call this method with reverse=true too
        encoder.setSpeed(true, edgeFlags, encoder.speedFactor * 0.49);
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
        edgeFlags = encoder.handleWayTags(em.createEdgeFlags(), way, EncodingManager.Access.WAY, 0);
        assertTrue(accessEnc.getBool(false, edgeFlags));
        assertTrue(accessEnc.getBool(true, edgeFlags));
        assertFalse(roundaboutEnc.getBool(false, edgeFlags));
    }

    @Test
    public void testRailway() {
        ReaderWay way = new ReaderWay(1);
        way.setTag("highway", "secondary");
        way.setTag("railway", "rail");
        assertTrue(encoder.getAccess(way).isWay());

        way.clearTags();
        way.setTag("highway", "path");
        way.setTag("railway", "abandoned");
        assertTrue(encoder.getAccess(way).canSkip());

        way.setTag("highway", "track");
        assertTrue(encoder.getAccess(way).isWay());

        // this is fully okay as sometimes old rails are on the road
        way.setTag("highway", "primary");
        way.setTag("railway", "historic");
        assertTrue(encoder.getAccess(way).isWay());

        way.setTag("motorcar", "no");
        assertTrue(encoder.getAccess(way).canSkip());

        way = new ReaderWay(1);
        way.setTag("highway", "secondary");
        way.setTag("railway", "tram");
        // but allow tram to be on the same way
        assertTrue(encoder.getAccess(way).isWay());

        way = new ReaderWay(1);
        way.setTag("route", "shuttle_train");
        way.setTag("motorcar", "yes");
        way.setTag("bicycle", "no");
        // Provide the duration value in seconds:
        way.setTag("duration:seconds", Long.toString(35 * 60));
        way.setTag("estimated_distance", 50000);
        // accept
        assertTrue(encoder.getAccess(way).isFerry());
        // calculate speed from estimated_distance and duration
        assertEquals(61, encoder.getFerrySpeed(way), 1e-1);

        //Test for very short and slow 0.5km/h still realisitic ferry
        way = new ReaderWay(1);
        way.setTag("route", "ferry");
        way.setTag("motorcar", "yes");
        // Provide the duration of 12 minutes in seconds:
        way.setTag("duration:seconds", Long.toString(12 * 60));
        way.setTag("estimated_distance", 100);
        // accept
        assertTrue(encoder.getAccess(way).isFerry());
        // We can't store 0.5km/h, but we expect the lowest possible speed (5km/h)
        assertEquals(2.5, encoder.getFerrySpeed(way), 1e-1);

        IntsRef edgeFlags = em.createEdgeFlags();
        avSpeedEnc.setDecimal(false, edgeFlags, 2.5);
        assertEquals(5, avSpeedEnc.getDecimal(false, edgeFlags), 1e-1);

        //Test for an unrealisitic long duration
        way = new ReaderWay(1);
        way.setTag("route", "ferry");
        way.setTag("motorcar", "yes");
        // Provide the duration of 2 months in seconds:
        way.setTag("duration:seconds", Long.toString(87900 * 60));
        way.setTag("estimated_distance", 100);
        // accept
        assertTrue(encoder.getAccess(way).isFerry());
        // We have ignored the unrealisitc long duration and take the unknown speed
        assertEquals(2.5, encoder.getFerrySpeed(way), 1e-1);
    }

    @Test
    public void testBarrierAccess() {
        ReaderNode node = new ReaderNode(1, -1, -1);
        node.setTag("barrier", "lift_gate");
        node.setTag("access", "yes");
        // no barrier!
        assertTrue(encoder.handleNodeTags(node) == 0);

        node = new ReaderNode(1, -1, -1);
        node.setTag("barrier", "lift_gate");
        node.setTag("bicycle", "yes");
        // barrier!
        assertTrue(encoder.handleNodeTags(node) > 0);

        node = new ReaderNode(1, -1, -1);
        node.setTag("barrier", "lift_gate");
        node.setTag("access", "yes");
        node.setTag("bicycle", "yes");
        // should this be a barrier for motorcars too?
        // assertTrue(encoder.handleNodeTags(node) > 0);

        node = new ReaderNode(1, -1, -1);
        node.setTag("barrier", "lift_gate");
        node.setTag("access", "no");
        node.setTag("motorcar", "yes");
        // no barrier!
        assertTrue(encoder.handleNodeTags(node) == 0);

        node = new ReaderNode(1, -1, -1);
        node.setTag("barrier", "bollard");
        // barrier!
        assertTrue(encoder.handleNodeTags(node) > 0);

        // ignore other access tags for absolute barriers!
        node.setTag("motorcar", "yes");
        // still barrier!
        assertTrue(encoder.handleNodeTags(node) > 0);

        encoder.setBlockByDefault(false);

        // Test if cattle_grid is not blocking
        node = new ReaderNode(1, -1, -1);
        node.setTag("barrier", "cattle_grid");
        assertTrue(encoder.handleNodeTags(node) == 0);
    }

    @Test
    public void testTurnFlagEncoding_noCosts() {
        FlagEncoder tmpEnc = new CarFlagEncoder(8, 5, 0);
        EncodingManager em = EncodingManager.create(tmpEnc);

        long flags_r0 = tmpEnc.getTurnFlags(true, 0);
        long flags_0 = tmpEnc.getTurnFlags(false, 0);

        long flags_r20 = tmpEnc.getTurnFlags(true, 0);
        long flags_20 = tmpEnc.getTurnFlags(false, 20);

        assertEquals(0, tmpEnc.getTurnCost(flags_r0), 1e-1);
        assertEquals(0, tmpEnc.getTurnCost(flags_0), 1e-1);

        assertEquals(0, tmpEnc.getTurnCost(flags_r20), 1e-1);
        assertEquals(0, tmpEnc.getTurnCost(flags_20), 1e-1);

        assertFalse(tmpEnc.isTurnRestricted(flags_r0));
        assertFalse(tmpEnc.isTurnRestricted(flags_0));

        assertFalse(tmpEnc.isTurnRestricted(flags_r20));
        assertFalse(tmpEnc.isTurnRestricted(flags_20));
    }

    @Test
    public void testTurnFlagEncoding_withCosts() {
        FlagEncoder tmpEncoder = new CarFlagEncoder(8, 5, 127);
        EncodingManager em = EncodingManager.create(tmpEncoder);

        long flags_r0 = tmpEncoder.getTurnFlags(true, 0);
        long flags_0 = tmpEncoder.getTurnFlags(false, 0);
        assertTrue(Double.isInfinite(tmpEncoder.getTurnCost(flags_r0)));
        assertEquals(0, tmpEncoder.getTurnCost(flags_0), 1e-1);
        assertTrue(tmpEncoder.isTurnRestricted(flags_r0));
        assertFalse(tmpEncoder.isTurnRestricted(flags_0));

        long flags_r20 = tmpEncoder.getTurnFlags(true, 0);
        long flags_20 = tmpEncoder.getTurnFlags(false, 20);
        assertTrue(Double.isInfinite(tmpEncoder.getTurnCost(flags_r20)));
        assertEquals(20, tmpEncoder.getTurnCost(flags_20), 1e-1);
        assertTrue(tmpEncoder.isTurnRestricted(flags_r20));
        assertFalse(tmpEncoder.isTurnRestricted(flags_20));

        long flags_r220 = tmpEncoder.getTurnFlags(true, 0);
        try {
            tmpEncoder.getTurnFlags(false, 220);
            assertTrue(false);
        } catch (Exception ex) {
        }
        assertTrue(Double.isInfinite(tmpEncoder.getTurnCost(flags_r220)));
        assertTrue(tmpEncoder.isTurnRestricted(flags_r220));
    }

    @Test
    public void testMaxValue() {
        CarFlagEncoder instance = new CarFlagEncoder(10, 0.5, 0);
        EncodingManager em = EncodingManager.create(instance);
        DecimalEncodedValue avSpeedEnc = em.getDecimalEncodedValue(EncodingManager.getKey(instance, "average_speed"));
        ReaderWay way = new ReaderWay(1);
        way.setTag("highway", "motorway_link");
        way.setTag("maxspeed", "60 mph");
        IntsRef edgeFlags = instance.handleWayTags(em.createEdgeFlags(), way, EncodingManager.Access.WAY, 0);

        // double speed = AbstractFlagEncoder.parseSpeed("60 mph");
        // => 96.56 * 0.9 => 86.9
        assertEquals(86.9, avSpeedEnc.getDecimal(false, edgeFlags), 1e-1);
        assertEquals(86.9, avSpeedEnc.getDecimal(true, edgeFlags), 1e-1);

        // test that maxPossibleValue  is not exceeded
        way = new ReaderWay(2);
        way.setTag("highway", "motorway_link");
        way.setTag("maxspeed", "70 mph");
        edgeFlags = instance.handleWayTags(em.createEdgeFlags(), way, EncodingManager.Access.WAY, 0);
        assertEquals(101.5, avSpeedEnc.getDecimal(false, edgeFlags), .1);
    }

    @Test
    public void testRegisterOnlyOnceAllowed() {
        CarFlagEncoder instance = new CarFlagEncoder(10, 0.5, 0);
        EncodingManager tmpEM = EncodingManager.create(instance);
        try {
            tmpEM = EncodingManager.create(instance);
            assertTrue(false);
        } catch (IllegalStateException ex) {
        }
    }

    @Test
    public void testSetToMaxSpeed() {
        ReaderWay way = new ReaderWay(12);
        way.setTag("maxspeed", "90");
        assertEquals(90, encoder.getMaxSpeed(way), 1e-2);
    }

    @Test
    public void testCombination() {
        ReaderWay way = new ReaderWay(123);
        way.setTag("highway", "cycleway");
        way.setTag("sac_scale", "hiking");

        EncodingManager.AcceptWay map = new EncodingManager.AcceptWay();
        assertTrue(em.acceptWay(way, map));
        IntsRef edgeFlags = em.handleWayTags(way, map, 0);
        assertFalse(accessEnc.getBool(true, edgeFlags));
        assertFalse(accessEnc.getBool(false, edgeFlags));
        BooleanEncodedValue bikeAccessEnc = em.getEncoder("bike").getAccessEnc();
        assertTrue(bikeAccessEnc.getBool(true, edgeFlags));
        assertTrue(bikeAccessEnc.getBool(false, edgeFlags));
    }

    @Test
    public void testApplyBadSurfaceSpeed() {
        ReaderWay way = new ReaderWay(1);
        way.setTag("highway", "secondary");
        way.setTag("surface", "unpaved");
        assertEquals(30, encoder.applyBadSurfaceSpeed(way, 90), 1e-1);
    }

    @Test
    public void testIssue_1256() {
        ReaderWay way = new ReaderWay(1);
        way.setTag("route", "ferry");
        way.setTag("estimated_distance", 257);

        CarFlagEncoder lowFactorCar = new CarFlagEncoder(10, 1, 0);
        List<EncodedValue> list = new ArrayList<>();
        lowFactorCar.setEncodedValueLookup(em);
        lowFactorCar.createEncodedValues(list, "car", 0);
        assertEquals(2.5, encoder.getFerrySpeed(way), .1);
        assertEquals(.5, lowFactorCar.getFerrySpeed(way), .1);
    }
}
