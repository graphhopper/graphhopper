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

import com.graphhopper.json.GHJson;
import com.graphhopper.json.GHJsonFactory;
import com.graphhopper.reader.ReaderNode;
import com.graphhopper.reader.ReaderWay;
import com.graphhopper.routing.profiles.BooleanEncodedValue;
import com.graphhopper.routing.profiles.DecimalEncodedValue;
import com.graphhopper.routing.profiles.TagParserFactory;
import com.graphhopper.storage.Graph;
import com.graphhopper.storage.GraphBuilder;
import com.graphhopper.storage.IntsRef;
import com.graphhopper.util.EdgeExplorer;
import com.graphhopper.util.GHUtility;
import com.graphhopper.util.Helper;
import org.junit.Test;

import java.text.DateFormat;
import java.util.Date;

import static org.junit.Assert.*;

/**
 * @author Peter Karich
 */
public class FootFlagEncoderTest {
    protected GHJson json = new GHJsonFactory().create();
    private final EncodingManager encodingManager = new EncodingManager.Builder().addGlobalEncodedValues().
            addAllFlagEncoders("car,bike,foot").build();
    private final BooleanEncodedValue footAccessEnc = encodingManager.getBooleanEncodedValue(TagParserFactory.FOOT_ACCESS);
    private final DecimalEncodedValue footAverageSpeedEnc = encodingManager.getDecimalEncodedValue(TagParserFactory.FOOT_AVERAGE_SPEED);
    private final FootFlagEncoder footEncoder = (FootFlagEncoder) encodingManager.getEncoder("foot");

    @Test
    public void testGetSpeed() {
        IntsRef ints = encodingManager.createIntsRef();
        footAverageSpeedEnc.setDecimal(false, ints, 10d);
        assertEquals(10, footEncoder.getSpeed(ints), 1e-1);
    }

    @Test
    public void testReverse() {
        IntsRef ints = encodingManager.createIntsRef();
        footAverageSpeedEnc.setDecimal(false, ints, 15);
        footAverageSpeedEnc.setDecimal(true, ints, 10);
        // currently no different speed is implemented
        assertEquals(footEncoder.getReverseSpeed(ints), footEncoder.getSpeed(ints), 1e-1);
    }

    @Test
    public void testCombined() {
        FlagEncoder carEncoder = encodingManager.getEncoder("car");
        BooleanEncodedValue carAccessEnc = encodingManager.getBooleanEncodedValue(TagParserFactory.CAR_ACCESS);
        DecimalEncodedValue carAverageSpeedEnc = encodingManager.getDecimalEncodedValue(TagParserFactory.CAR_AVERAGE_SPEED);
        IntsRef ints = encodingManager.createIntsRef();
        carAccessEnc.setBool(false, ints, true);
        carAccessEnc.setBool(true, ints, false);
        carAverageSpeedEnc.setDecimal(false, ints, 100d);

        footAccessEnc.setBool(false, ints, true);
        footAccessEnc.setBool(true, ints, true);
        footAverageSpeedEnc.setDecimal(false, ints, 10d);
        assertEquals(10, footEncoder.getSpeed(ints), 1e-1);
        assertTrue(footAccessEnc.getBool(false, ints));
        assertTrue(footAccessEnc.getBool(true, ints));

        assertEquals(100, carEncoder.getSpeed(ints), 1e-1);
        assertTrue(carAccessEnc.getBool(false, ints));
        assertFalse(carAccessEnc.getBool(true, ints));

        ints = encodingManager.createIntsRef();
        footAccessEnc.setBool(false, ints, true);
        footAccessEnc.setBool(true, ints, true);
        footAverageSpeedEnc.setDecimal(false, ints, 10);
        assertEquals(0, carEncoder.getSpeed(ints), 1e-1);
    }

    @Test
    public void testGraph() {
        Graph g = new GraphBuilder(encodingManager, json).create();
        GHUtility.createEdge(g, footAverageSpeedEnc, 5, footAccessEnc, 0, 1, true, 10d);
        GHUtility.createEdge(g, footAverageSpeedEnc, 5, footAccessEnc, 0, 2, true, 10d);
        GHUtility.createEdge(g, footAverageSpeedEnc, 5, footAccessEnc, 1, 3, true, 10d);
        EdgeExplorer out = g.createEdgeExplorer(new DefaultEdgeFilter(encodingManager.getBooleanEncodedValue(TagParserFactory.FOOT_ACCESS),
                true, false));
        assertEquals(GHUtility.asSet(1, 2), GHUtility.getNeighbors(out.setBaseNode(0)));
        assertEquals(GHUtility.asSet(0, 3), GHUtility.getNeighbors(out.setBaseNode(1)));
        assertEquals(GHUtility.asSet(0), GHUtility.getNeighbors(out.setBaseNode(2)));
    }

    @Test
    public void testAccess() {
        ReaderWay way = new ReaderWay(1);

        way.setTag("highway", "motorway");
        way.setTag("sidewalk", "yes");
        assertTrue(footEncoder.getAccess(way).isWay());
        way.setTag("sidewalk", "left");
        assertTrue(footEncoder.getAccess(way).isWay());

        way.setTag("sidewalk", "none");
        assertFalse(footEncoder.getAccess(way).isWay());

        way.clearTags();
        way.setTag("highway", "tertiary");
        way.setTag("sidewalk", "left");
        way.setTag("access", "private");
        assertFalse(footEncoder.getAccess(way).isWay());
        way.clearTags();

        way.setTag("highway", "pedestrian");
        assertTrue(footEncoder.getAccess(way).isWay());

        way.setTag("highway", "footway");
        assertTrue(footEncoder.getAccess(way).isWay());

        way.setTag("highway", "motorway");
        assertFalse(footEncoder.getAccess(way).isWay());

        way.setTag("highway", "path");
        assertTrue(footEncoder.getAccess(way).isWay());

        way.setTag("bicycle", "official");
        assertTrue(footEncoder.getAccess(way).isWay());
        way.setTag("foot", "no");
        assertFalse(footEncoder.getAccess(way).isWay());

        way.setTag("foot", "official");
        assertTrue(footEncoder.getAccess(way).isWay());

        way.clearTags();
        way.setTag("highway", "service");
        way.setTag("access", "no");
        assertFalse(footEncoder.getAccess(way).isWay());
        way.setTag("foot", "yes");
        assertTrue(footEncoder.getAccess(way).isWay());

        way.clearTags();
        way.setTag("highway", "service");
        way.setTag("vehicle", "no");
        assertTrue(footEncoder.getAccess(way).isWay());
        way.setTag("foot", "no");
        assertFalse(footEncoder.getAccess(way).isWay());

        way.clearTags();
        way.setTag("highway", "tertiary");
        way.setTag("motorroad", "yes");
        assertFalse(footEncoder.getAccess(way).isWay());

        way.clearTags();
        way.setTag("highway", "cycleway");
        assertTrue(footEncoder.getAccess(way).isWay());
        way.setTag("foot", "no");
        assertFalse(footEncoder.getAccess(way).isWay());
        way.setTag("access", "yes");
        assertFalse(footEncoder.getAccess(way).isWay());

        way.clearTags();
        way.setTag("highway", "service");
        way.setTag("foot", "yes");
        way.setTag("access", "no");
        assertTrue(footEncoder.getAccess(way).isWay());

        way.clearTags();
        way.setTag("highway", "track");
        way.setTag("ford", "yes");
        assertFalse(footEncoder.getAccess(way).isWay());
        way.setTag("foot", "yes");
        assertTrue(footEncoder.getAccess(way).isWay());

        way.clearTags();
        way.setTag("route", "ferry");
        assertTrue(footEncoder.getAccess(way).isFerry());
        way.setTag("foot", "no");
        assertFalse(footEncoder.getAccess(way).isWay());

        DateFormat simpleDateFormat = Helper.createFormatter("yyyy MMM dd");

        way.clearTags();
        way.setTag("highway", "footway");
        way.setTag("access:conditional", "no @ (" + simpleDateFormat.format(new Date().getTime()) + ")");
        assertFalse(footEncoder.getAccess(way).isWay());

        way.clearTags();
        way.setTag("highway", "footway");
        way.setTag("access", "no");
        way.setTag("access:conditional", "yes @ (" + simpleDateFormat.format(new Date().getTime()) + ")");
        assertTrue(footEncoder.getAccess(way).isWay());
    }

    @Test
    public void testRailPlatformIssue366() {
        ReaderWay way = new ReaderWay(1);
        way.setTag("railway", "platform");
        IntsRef flags = footEncoder.handleWayTags(encodingManager.createIntsRef(), way, footEncoder.getAccess(way), 0);
        assertNotEquals(0, flags);

        way.clearTags();
        way.setTag("highway", "track");
        way.setTag("railway", "platform");
        flags = footEncoder.handleWayTags(encodingManager.createIntsRef(), way, footEncoder.getAccess(way), 0);
        assertNotEquals(0, flags);

        way.clearTags();
        // only tram, no highway => no access
        way.setTag("railway", "tram");
        flags = footEncoder.handleWayTags(encodingManager.createIntsRef(), way, footEncoder.getAccess(way), 0);
        assertFalse(footAccessEnc.getBool(false, flags));
    }

    @Test
    public void testMixSpeedAndSafe() {
        ReaderWay way = new ReaderWay(1);
        way.setTag("highway", "motorway");
        way.setTag("sidewalk", "yes");
        IntsRef flags = encodingManager.handleWayTags(encodingManager.createIntsRef(), way, new EncodingManager.AcceptWay(), 0);
        assertEquals(5, footEncoder.getSpeed(flags), 1e-1);

        way.clearTags();
        way.setTag("highway", "track");
        flags = encodingManager.handleWayTags(encodingManager.createIntsRef(), way, new EncodingManager.AcceptWay(), 0);
        assertEquals(5, footEncoder.getSpeed(flags), 1e-1);
    }

    @Test
    public void testPriority() {
        ReaderWay way = new ReaderWay(1);
        way.setTag("highway", "cycleway");
        assertEquals(PriorityCode.UNCHANGED.getValue(), footEncoder.handlePriority(way, 0));

        way.setTag("highway", "primary");
        assertEquals(PriorityCode.AVOID_IF_POSSIBLE.getValue(), footEncoder.handlePriority(way, 0));

        way.setTag("highway", "track");
        way.setTag("bicycle", "official");
        assertEquals(PriorityCode.AVOID_IF_POSSIBLE.getValue(), footEncoder.handlePriority(way, 0));

        way.setTag("highway", "track");
        way.setTag("bicycle", "designated");
        assertEquals(PriorityCode.AVOID_IF_POSSIBLE.getValue(), footEncoder.handlePriority(way, 0));

        way.setTag("highway", "cycleway");
        way.setTag("bicycle", "designated");
        way.setTag("foot", "designated");
        assertEquals(PriorityCode.PREFER.getValue(), footEncoder.handlePriority(way, 0));

        way.clearTags();
        way.setTag("highway", "primary");
        way.setTag("sidewalk", "yes");
        assertEquals(PriorityCode.UNCHANGED.getValue(), footEncoder.handlePriority(way, 0));

        way.clearTags();
        way.setTag("highway", "cycleway");
        way.setTag("sidewalk", "no");
        assertEquals(PriorityCode.UNCHANGED.getValue(), footEncoder.handlePriority(way, 0));

        way.clearTags();
        way.setTag("highway", "road");
        way.setTag("bicycle", "official");
        way.setTag("sidewalk", "no");
        assertEquals(PriorityCode.AVOID_IF_POSSIBLE.getValue(), footEncoder.handlePriority(way, 0));

        way.clearTags();
        way.setTag("highway", "trunk");
        way.setTag("sidewalk", "no");
        assertEquals(PriorityCode.AVOID_IF_POSSIBLE.getValue(), footEncoder.handlePriority(way, 0));
        way.setTag("sidewalk", "none");
        assertEquals(PriorityCode.AVOID_IF_POSSIBLE.getValue(), footEncoder.handlePriority(way, 0));

        way.clearTags();
        way.setTag("highway", "residential");
        way.setTag("sidewalk", "yes");
        assertEquals(PriorityCode.PREFER.getValue(), footEncoder.handlePriority(way, 0));
    }

    @Test
    public void testSlowHiking() {
        ReaderWay way = new ReaderWay(1);
        way.setTag("highway", "track");
        way.setTag("sac_scale", "hiking");
        IntsRef flags = encodingManager.handleWayTags(encodingManager.createIntsRef(), way, new EncodingManager.AcceptWay(), 0);
        assertEquals(TagParserFactory.Foot.MEAN_SPEED, footEncoder.getSpeed(flags), 1e-1);

        way.setTag("highway", "track");
        way.setTag("sac_scale", "mountain_hiking");
        flags = encodingManager.handleWayTags(encodingManager.createIntsRef(), way, new EncodingManager.AcceptWay(), 0);
        assertEquals(TagParserFactory.Foot.SLOW_SPEED, footEncoder.getSpeed(flags), 1e-1);
    }

    @Test
    public void testTurnFlagEncoding_noCostsAndRestrictions() {
        long flags_r0 = footEncoder.getTurnFlags(true, 0);
        long flags_0 = footEncoder.getTurnFlags(false, 0);

        long flags_r20 = footEncoder.getTurnFlags(true, 20);
        long flags_20 = footEncoder.getTurnFlags(false, 20);

        assertEquals(0, footEncoder.getTurnCost(flags_r0), 1e-1);
        assertEquals(0, footEncoder.getTurnCost(flags_0), 1e-1);

        assertEquals(0, footEncoder.getTurnCost(flags_r20), 1e-1);
        assertEquals(0, footEncoder.getTurnCost(flags_20), 1e-1);

        assertFalse(footEncoder.isTurnRestricted(flags_r0));
        assertFalse(footEncoder.isTurnRestricted(flags_0));

        assertFalse(footEncoder.isTurnRestricted(flags_r20));
        assertFalse(footEncoder.isTurnRestricted(flags_20));
    }

    @Test
    public void testBarrierAccess() {
        // by default allow access through the gate for bike & foot!
        ReaderNode node = new ReaderNode(1, -1, -1);
        node.setTag("barrier", "gate");
        // no barrier!
        assertTrue(footEncoder.handleNodeTags(node) == 0);

        node = new ReaderNode(1, -1, -1);
        node.setTag("barrier", "gate");
        node.setTag("access", "yes");
        // no barrier!
        assertTrue(footEncoder.handleNodeTags(node) == 0);

        node = new ReaderNode(1, -1, -1);
        node.setTag("barrier", "gate");
        node.setTag("access", "no");
        // barrier!
        assertTrue(footEncoder.handleNodeTags(node) > 0);

        node.setTag("bicycle", "yes");
        // no barrier!?
        // assertTrue(footEncoder.handleNodeTags(node) == 0);

        node = new ReaderNode(1, -1, -1);
        node.setTag("barrier", "gate");
        node.setTag("access", "no");
        node.setTag("foot", "yes");
        // no barrier!
        assertTrue(footEncoder.handleNodeTags(node) == 0);

        node.setTag("locked", "yes");
        // barrier!
        assertTrue(footEncoder.handleNodeTags(node) > 0);
    }

    @Test
    public void handleWayTagsRoundabout() {
        ReaderWay way = new ReaderWay(1);
        way.setTag("junction", "roundabout");
        way.setTag("highway", "tertiary");
        IntsRef flags = encodingManager.handleWayTags(encodingManager.createIntsRef(), way, new EncodingManager.AcceptWay(), 0);
        BooleanEncodedValue roundabout = encodingManager.getBooleanEncodedValue(TagParserFactory.ROUNDABOUT);
        assertTrue(roundabout.getBool(false, flags));
        assertTrue(roundabout.getBool(true, flags));
    }

    @Test
    public void testFord() {
        // by default deny access through fords!
        ReaderNode node = new ReaderNode(1, -1, -1);
        node.setTag("ford", "no");
        assertTrue(footEncoder.handleNodeTags(node) == 0);

        node = new ReaderNode(1, -1, -1);
        node.setTag("ford", "yes");
        assertTrue(footEncoder.handleNodeTags(node) > 0);

        node.setTag("foot", "yes");
        // no barrier!
        assertTrue(footEncoder.handleNodeTags(node) == 0);

        // Now let's allow fords for foot
        footEncoder.setBlockFords(Boolean.FALSE);

        node = new ReaderNode(1, -1, -1);
        node.setTag("ford", "no");
        assertTrue(footEncoder.handleNodeTags(node) == 0);

        node = new ReaderNode(1, -1, -1);
        node.setTag("ford", "yes");
        assertTrue(footEncoder.handleNodeTags(node) == 0);
    }
}
