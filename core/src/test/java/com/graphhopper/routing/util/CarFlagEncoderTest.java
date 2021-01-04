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
import com.graphhopper.routing.ev.BooleanEncodedValue;
import com.graphhopper.routing.ev.DecimalEncodedValue;
import com.graphhopper.routing.ev.EncodedValue;
import com.graphhopper.routing.ev.Roundabout;
import com.graphhopper.routing.weighting.FastestWeighting;
import com.graphhopper.storage.IntsRef;
import com.graphhopper.util.GHUtility;
import com.graphhopper.util.Helper;
import com.graphhopper.util.PMap;
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
    final CarFlagEncoder encoder = createEncoder();
    private final EncodingManager em = new EncodingManager.Builder().
            add(encoder).
            add(new BikeFlagEncoder()).add(new FootFlagEncoder()).build();

    private final BooleanEncodedValue roundaboutEnc = em.getBooleanEncodedValue(Roundabout.KEY);
    private final DecimalEncodedValue avSpeedEnc = encoder.getAverageSpeedEnc();
    private final BooleanEncodedValue accessEnc = encoder.getAccessEnc();

    CarFlagEncoder createEncoder() {
        return new CarFlagEncoder(new PMap("speed_two_directions=true|block_fords=true"));
    }

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

        CarFlagEncoder tmpEncoder = new CarFlagEncoder(new PMap("block_fords=false"));
        EncodingManager.create(tmpEncoder);
        assertTrue(tmpEncoder.getAccess(way).isWay());
        assertFalse(tmpEncoder.handleNodeTags(node) > 0);
    }

    @Test
    public void testOneway() {
        ReaderWay way = new ReaderWay(1);
        way.setTag("highway", "primary");
        IntsRef flags = encoder.handleWayTags(em.createEdgeFlags(), way, encoder.getAccess(way));
        assertTrue(accessEnc.getBool(false, flags));
        assertTrue(accessEnc.getBool(true, flags));
        way.setTag("oneway", "yes");
        flags = encoder.handleWayTags(em.createEdgeFlags(), way, encoder.getAccess(way));
        assertTrue(accessEnc.getBool(false, flags));
        assertFalse(accessEnc.getBool(true, flags));
        way.clearTags();

        way.setTag("highway", "tertiary");
        flags = encoder.handleWayTags(em.createEdgeFlags(), way, encoder.getAccess(way));
        assertTrue(accessEnc.getBool(false, flags));
        assertTrue(accessEnc.getBool(true, flags));
        way.clearTags();

        way.setTag("highway", "tertiary");
        way.setTag("vehicle:forward", "no");
        flags = encoder.handleWayTags(em.createEdgeFlags(), way, encoder.getAccess(way));
        assertFalse(accessEnc.getBool(false, flags));
        assertTrue(accessEnc.getBool(true, flags));
        way.clearTags();

        way.setTag("highway", "tertiary");
        way.setTag("vehicle:backward", "no");
        flags = encoder.handleWayTags(em.createEdgeFlags(), way, encoder.getAccess(way));
        assertTrue(accessEnc.getBool(false, flags));
        assertFalse(accessEnc.getBool(true, flags));
        way.clearTags();
    }

    @Test
    public void testDestinationTag() {
        IntsRef relFlags = em.createRelationFlags();

        FastestWeighting weighting = new FastestWeighting(encoder);
        FastestWeighting bikeWeighting = new FastestWeighting(em.getEncoder("bike"));

        ReaderWay way = new ReaderWay(1);
        way.setTag("highway", "secondary");
        EncodingManager.AcceptWay acceptWay = new EncodingManager.AcceptWay();
        assertTrue(em.acceptWay(way, acceptWay));
        IntsRef edgeFlags = em.handleWayTags(way, acceptWay, relFlags);
        assertEquals(60, weighting.calcEdgeWeight(GHUtility.createMockedEdgeIteratorState(1000, edgeFlags), false), 0.1);
        assertEquals(200, bikeWeighting.calcEdgeWeight(GHUtility.createMockedEdgeIteratorState(1000, edgeFlags), false), 0.1);

        // no change for bike!
        way.setTag("motor_vehicle", "destination");
        edgeFlags = em.handleWayTags(way, acceptWay, relFlags);
        assertEquals(600, weighting.calcEdgeWeight(GHUtility.createMockedEdgeIteratorState(1000, edgeFlags), false), 0.1);
        assertEquals(200, bikeWeighting.calcEdgeWeight(GHUtility.createMockedEdgeIteratorState(1000, edgeFlags), false), 0.1);

        way = new ReaderWay(1);
        way.setTag("highway", "secondary");
        way.setTag("vehicle", "destination");
        edgeFlags = em.handleWayTags(way, acceptWay, relFlags);
        assertEquals(600, weighting.calcEdgeWeight(GHUtility.createMockedEdgeIteratorState(1000, edgeFlags), false), 0.1);
        assertEquals(200, bikeWeighting.calcEdgeWeight(GHUtility.createMockedEdgeIteratorState(1000, edgeFlags), false), 0.1);
    }

    @Test
    public void testPrivateTag() {
        // allow private access
        FlagEncoder carEncoder = new CarFlagEncoder(new PMap("block_private=false"));
        FlagEncoder bikeEncoder = new BikeFlagEncoder(new PMap("block_private=false"));
        EncodingManager em = new EncodingManager.Builder().add(carEncoder).add(bikeEncoder).build();

        FastestWeighting weighting = new FastestWeighting(carEncoder);
        FastestWeighting bikeWeighting = new FastestWeighting(bikeEncoder);

        ReaderWay way = new ReaderWay(1);
        way.setTag("highway", "secondary");

        EncodingManager.AcceptWay acceptWay = new EncodingManager.AcceptWay();
        assertTrue(em.acceptWay(way, acceptWay));
        IntsRef edgeFlags = em.handleWayTags(way, acceptWay, em.createRelationFlags());

        assertEquals(60, weighting.calcEdgeWeight(GHUtility.createMockedEdgeIteratorState(1000, edgeFlags), false), 0.1);
        assertEquals(200, bikeWeighting.calcEdgeWeight(GHUtility.createMockedEdgeIteratorState(1000, edgeFlags), false), 0.1);

        way.setTag("highway", "secondary");
        way.setTag("access", "private");
        acceptWay = new EncodingManager.AcceptWay();
        assertTrue(em.acceptWay(way, acceptWay));
        edgeFlags = em.handleWayTags(way, acceptWay, em.createRelationFlags());

        assertEquals(600, weighting.calcEdgeWeight(GHUtility.createMockedEdgeIteratorState(1000, edgeFlags), false), 0.1);
        // private should influence bike only slightly
        assertEquals(240, bikeWeighting.calcEdgeWeight(GHUtility.createMockedEdgeIteratorState(1000, edgeFlags), false), 0.1);
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
        EncodingManager.AcceptWay allowed = new EncodingManager.AcceptWay();
        for (FlagEncoder encoder : em.fetchEdgeEncoders())
            allowed.put(encoder.toString(), EncodingManager.Access.WAY);
        IntsRef relFlags = em.createRelationFlags();
        IntsRef edgeFlags = em.handleWayTags(way, allowed, relFlags);
        assertEquals(140, avSpeedEnc.getDecimal(false, edgeFlags), 1e-1);

        way = new ReaderWay(1);
        way.setTag("highway", "primary");
        way.setTag("maxspeed:backward", "10");
        way.setTag("maxspeed:forward", "20");
        edgeFlags = em.handleWayTags(way, allowed, relFlags);
        assertEquals(10, avSpeedEnc.getDecimal(false, edgeFlags), 1e-1);

        way = new ReaderWay(1);
        way.setTag("highway", "primary");
        way.setTag("maxspeed:forward", "20");
        edgeFlags = em.handleWayTags(way, allowed, relFlags);
        assertEquals(20, avSpeedEnc.getDecimal(false, edgeFlags), 1e-1);

        way = new ReaderWay(1);
        way.setTag("highway", "primary");
        way.setTag("maxspeed:backward", "20");
        edgeFlags = em.handleWayTags(way, allowed, relFlags);
        assertEquals(20, avSpeedEnc.getDecimal(false, edgeFlags), 1e-1);

        way = new ReaderWay(1);
        way.setTag("highway", "motorway");
        way.setTag("maxspeed", "none");
        edgeFlags = em.handleWayTags(way, allowed, relFlags);
        assertEquals(135, avSpeedEnc.getDecimal(false, edgeFlags), .1);
    }

    @Test
    public void testSpeed() {
        // limit bigger than default road speed
        ReaderWay way = new ReaderWay(1);
        way.setTag("highway", "trunk");
        way.setTag("maxspeed", "110");
        EncodingManager.Access allowed = encoder.getAccess(way);
        IntsRef edgeFlags = encoder.handleWayTags(em.createEdgeFlags(), way, allowed);
        assertEquals(100, avSpeedEnc.getDecimal(false, edgeFlags), 1e-1);

        way.clearTags();
        way.setTag("highway", "residential");
        way.setTag("surface", "cobblestone");
        allowed = encoder.getAccess(way);
        edgeFlags = encoder.handleWayTags(em.createEdgeFlags(), way, allowed);
        assertEquals(30, avSpeedEnc.getDecimal(false, edgeFlags), 1e-1);

        way.clearTags();
        way.setTag("highway", "track");
        allowed = encoder.getAccess(way);
        edgeFlags = encoder.handleWayTags(em.createEdgeFlags(), way, allowed);
        assertEquals(15, avSpeedEnc.getDecimal(false, edgeFlags), 1e-1);

        way.clearTags();
        way.setTag("highway", "track");
        way.setTag("tracktype", "grade1");
        allowed = encoder.getAccess(way);
        edgeFlags = encoder.handleWayTags(em.createEdgeFlags(), way, allowed);
        assertEquals(20, avSpeedEnc.getDecimal(false, edgeFlags), 1e-1);

        way.clearTags();
        way.setTag("highway", "secondary");
        way.setTag("surface", "compacted");
        allowed = encoder.getAccess(way);
        edgeFlags = encoder.handleWayTags(em.createEdgeFlags(), way, allowed);
        assertEquals(30, avSpeedEnc.getDecimal(false, edgeFlags), 1e-1);

        way.clearTags();
        way.setTag("highway", "secondary");
        way.setTag("motorroad", "yes");
        allowed = encoder.getAccess(way);
        edgeFlags = encoder.handleWayTags(em.createEdgeFlags(), way, allowed);
        assertEquals(90, avSpeedEnc.getDecimal(false, edgeFlags), 1e-1);

        way.clearTags();
        way.setTag("highway", "motorway");
        way.setTag("motorroad", "yes"); // this tag should be ignored
        allowed = encoder.getAccess(way);
        edgeFlags = encoder.handleWayTags(em.createEdgeFlags(), way, allowed);
        assertEquals(100, avSpeedEnc.getDecimal(false, edgeFlags), 1e-1);

        way.clearTags();
        way.setTag("highway", "motorway_link");
        way.setTag("motorroad", "yes"); // this tag should be ignored
        allowed = encoder.getAccess(way);
        edgeFlags = encoder.handleWayTags(em.createEdgeFlags(), way, allowed);
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
        edgeFlags = encoder.handleWayTags(em.createEdgeFlags(), way, EncodingManager.Access.WAY);
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
    }

    @Test
    public void testFerry() {
        ReaderWay way = new ReaderWay(1);
        way.setTag("route", "shuttle_train");
        way.setTag("motorcar", "yes");
        way.setTag("bicycle", "no");
        // Provide the duration value in seconds:
        way.setTag("duration:seconds", Long.toString(35 * 60));
        way.setTag("estimated_distance", 50000);
        // accept
        assertTrue(encoder.getAccess(way).isFerry());
        // calculate speed from estimated_distance and duration
        assertEquals(61, encoder.ferrySpeedCalc.getSpeed(way), 1e-1);

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
        assertEquals(2.5, encoder.ferrySpeedCalc.getSpeed(way), 1e-1);

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
        assertEquals(2.5, encoder.ferrySpeedCalc.getSpeed(way), 1e-1);

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
        way.setTag("foot", "designated");
        way.setTag("motor_vehicle", "designated");
        assertTrue(encoder.getAccess(way).isFerry());

        way.clearTags();
        way.setTag("route", "ferry");
        way.setTag("access", "no");
        assertTrue(encoder.getAccess(way).canSkip());
        way.setTag("vehicle", "yes");
        assertTrue(encoder.getAccess(way).isFerry());
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

        CarFlagEncoder tmpEncoder = new CarFlagEncoder(new PMap("block_barriers=false"));
        EncodingManager.create(tmpEncoder);

        // Test if cattle_grid is not blocking
        node = new ReaderNode(1, -1, -1);
        node.setTag("barrier", "cattle_grid");
        assertTrue(tmpEncoder.handleNodeTags(node) == 0);
    }

    @Test
    public void testMaxValue() {
        CarFlagEncoder instance = new CarFlagEncoder(10, 0.5, 0);
        EncodingManager em = EncodingManager.create(instance);
        DecimalEncodedValue avSpeedEnc = em.getDecimalEncodedValue(EncodingManager.getKey(instance, "average_speed"));
        ReaderWay way = new ReaderWay(1);
        way.setTag("highway", "motorway_link");
        way.setTag("maxspeed", "60 mph");
        IntsRef edgeFlags = instance.handleWayTags(em.createEdgeFlags(), way, EncodingManager.Access.WAY);

        // double speed = AbstractFlagEncoder.parseSpeed("60 mph");
        // => 96.56 * 0.9 => 86.9
        assertEquals(86.9, avSpeedEnc.getDecimal(false, edgeFlags), 1e-1);
        assertEquals(86.9, avSpeedEnc.getDecimal(true, edgeFlags), 1e-1);

        // test that maxPossibleValue  is not exceeded
        way = new ReaderWay(2);
        way.setTag("highway", "motorway_link");
        way.setTag("maxspeed", "70 mph");
        edgeFlags = instance.handleWayTags(em.createEdgeFlags(), way, EncodingManager.Access.WAY);
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
        IntsRef edgeFlags = em.handleWayTags(way, map, em.createRelationFlags());
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
        EncodingManager.create(lowFactorCar);
        List<EncodedValue> list = new ArrayList<>();
        lowFactorCar.setEncodedValueLookup(em);
        lowFactorCar.createEncodedValues(list, "car", 0);
        assertEquals(2.5, encoder.ferrySpeedCalc.getSpeed(way), .1);
        assertEquals(.5, lowFactorCar.ferrySpeedCalc.getSpeed(way), .1);
    }
}
