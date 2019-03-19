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
import com.graphhopper.storage.Graph;
import com.graphhopper.storage.GraphBuilder;
import com.graphhopper.storage.IntsRef;
import com.graphhopper.util.EdgeExplorer;
import com.graphhopper.util.EdgeIteratorState;
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
    private final EncodingManager encodingManager = EncodingManager.create("car,bike,foot");
    private final FootFlagEncoder footEncoder = (FootFlagEncoder) encodingManager.getEncoder("foot");
    private final DecimalEncodedValue footAvSpeedEnc = footEncoder.getAverageSpeedEnc();
    private final BooleanEncodedValue footAccessEnc = footEncoder.getAccessEnc();
    private final DecimalEncodedValue carAvSpeedEnc = encodingManager.getEncoder("car").getAverageSpeedEnc();
    private final BooleanEncodedValue carAccessEnc = encodingManager.getEncoder("car").getAccessEnc();

    @Test
    public void testGetSpeed() {
        IntsRef fl = encodingManager.createEdgeFlags();
        footAccessEnc.setBool(false, fl, true);
        footAccessEnc.setBool(true, fl, true);
        footAvSpeedEnc.setDecimal(false, fl, 10);
        assertEquals(10, footEncoder.getSpeed(fl), 1e-1);
    }

    @Test
    public void testBasics() {
        IntsRef edgeFlags = encodingManager.createEdgeFlags();
        footEncoder.flagsDefault(edgeFlags, true, true);
        assertEquals(FootFlagEncoder.MEAN_SPEED, footEncoder.getSpeed(edgeFlags), 1e-1);

        IntsRef ef1 = encodingManager.createEdgeFlags();
        footEncoder.flagsDefault(ef1, true, false);
        IntsRef ef2 = encodingManager.createEdgeFlags();
        footEncoder.flagsDefault(ef2, false, true);
        assertEquals(footAccessEnc.getBool(false, ef1), footAccessEnc.getBool(true, ef2));
        assertEquals(footEncoder.getSpeed(ef1), footEncoder.getSpeed(ef1), 1e-1);
    }

    @Test
    public void testCombined() {
        Graph g = new GraphBuilder(encodingManager).create();
        FlagEncoder carEncoder = encodingManager.getEncoder("car");
        EdgeIteratorState edge = g.edge(0, 1);
        edge.set(footAvSpeedEnc, 10.0).set(footAccessEnc, true).setReverse(footAccessEnc, true);
        edge.set(carAvSpeedEnc, 100.0).set(carAccessEnc, true).setReverse(carAccessEnc, false);

        assertEquals(10, edge.get(footAvSpeedEnc), 1e-1);
        assertTrue(edge.get(footAccessEnc));
        assertTrue(edge.getReverse(footAccessEnc));

        assertEquals(100, edge.get(carAvSpeedEnc), 1e-1);
        assertTrue(edge.get(carAccessEnc));
        assertFalse(edge.getReverse(carAccessEnc));

        IntsRef raw = encodingManager.createEdgeFlags();
        footAvSpeedEnc.setDecimal(false, raw, 10);
        footAccessEnc.setBool(false, raw, true);
        footAccessEnc.setBool(true, raw, true);
        assertEquals(0, carAvSpeedEnc.getDecimal(false, raw), 1e-1);
    }

    @Test
    public void testGraph() {
        Graph g = new GraphBuilder(encodingManager).create();
        g.edge(0, 1).setDistance(10).set(footAvSpeedEnc, 10.0).set(footAccessEnc, true).setReverse(footAccessEnc, true);
        g.edge(0, 2).setDistance(10).set(footAvSpeedEnc, 5.0).set(footAccessEnc, true).setReverse(footAccessEnc, true);
        g.edge(1, 3).setDistance(10).set(footAvSpeedEnc, 10.0).set(footAccessEnc, true).setReverse(footAccessEnc, true);
        EdgeExplorer out = g.createEdgeExplorer(DefaultEdgeFilter.outEdges(footEncoder));
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
        assertTrue(footEncoder.getAccess(way).canSkip());

        way.clearTags();
        way.setTag("highway", "tertiary");
        way.setTag("sidewalk", "left");
        way.setTag("access", "private");
        assertTrue(footEncoder.getAccess(way).canSkip());
        way.clearTags();

        way.setTag("highway", "pedestrian");
        assertTrue(footEncoder.getAccess(way).isWay());

        way.setTag("highway", "footway");
        assertTrue(footEncoder.getAccess(way).isWay());

        way.setTag("highway", "motorway");
        assertTrue(footEncoder.getAccess(way).canSkip());

        way.setTag("highway", "path");
        assertTrue(footEncoder.getAccess(way).isWay());

        way.setTag("bicycle", "official");
        assertTrue(footEncoder.getAccess(way).isWay());
        way.setTag("foot", "no");
        assertTrue(footEncoder.getAccess(way).canSkip());

        way.setTag("foot", "official");
        assertTrue(footEncoder.getAccess(way).isWay());

        way.clearTags();
        way.setTag("highway", "service");
        way.setTag("access", "no");
        assertTrue(footEncoder.getAccess(way).canSkip());
        way.setTag("foot", "yes");
        assertTrue(footEncoder.getAccess(way).isWay());

        way.clearTags();
        way.setTag("highway", "service");
        way.setTag("vehicle", "no");
        assertTrue(footEncoder.getAccess(way).isWay());
        way.setTag("foot", "no");
        assertTrue(footEncoder.getAccess(way).canSkip());

        way.clearTags();
        way.setTag("highway", "tertiary");
        way.setTag("motorroad", "yes");
        assertTrue(footEncoder.getAccess(way).canSkip());

        way.clearTags();
        way.setTag("highway", "cycleway");
        assertTrue(footEncoder.getAccess(way).isWay());
        way.setTag("foot", "no");
        assertTrue(footEncoder.getAccess(way).canSkip());
        way.setTag("access", "yes");
        assertTrue(footEncoder.getAccess(way).canSkip());

        way.clearTags();
        way.setTag("highway", "service");
        way.setTag("foot", "yes");
        way.setTag("access", "no");
        assertTrue(footEncoder.getAccess(way).isWay());

        way.clearTags();
        way.setTag("highway", "track");
        way.setTag("ford", "yes");
        assertTrue(footEncoder.getAccess(way).canSkip());
        way.setTag("foot", "yes");
        assertTrue(footEncoder.getAccess(way).isWay());

        way.clearTags();
        way.setTag("route", "ferry");
        assertTrue(footEncoder.getAccess(way).isFerry());
        way.setTag("foot", "no");
        assertTrue(footEncoder.getAccess(way).canSkip());

        // #1562, test if ferry route with foot
        way.clearTags();
        way.setTag("route", "ferry");
        way.setTag("foot", "yes");
        assertTrue(footEncoder.getAccess(way).isFerry());

        way.setTag("foot", "designated");
        assertTrue(footEncoder.getAccess(way).isFerry());

        way.setTag("foot", "official");
        assertTrue(footEncoder.getAccess(way).isFerry());

        way.setTag("foot", "permissive");
        assertTrue(footEncoder.getAccess(way).isFerry());

        way.setTag("foot", "no");
        assertTrue(footEncoder.getAccess(way).canSkip());

        way.setTag("foot", "designated");
        way.setTag("access", "private");
        assertTrue(footEncoder.getAccess(way).canSkip());

        DateFormat simpleDateFormat = Helper.createFormatter("yyyy MMM dd");

        way.clearTags();
        way.setTag("highway", "footway");
        way.setTag("access:conditional", "no @ (" + simpleDateFormat.format(new Date().getTime()) + ")");
        assertTrue(footEncoder.getAccess(way).canSkip());

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
        IntsRef flags = footEncoder.handleWayTags(encodingManager.createEdgeFlags(), way, footEncoder.getAccess(way), 0);
        assertNotEquals(0, flags.ints[0]);

        way.clearTags();
        way.setTag("highway", "track");
        way.setTag("railway", "platform");
        flags = footEncoder.handleWayTags(encodingManager.createEdgeFlags(), way, footEncoder.getAccess(way), 0);
        assertNotEquals(0, flags.ints[0]);

        way.clearTags();
        // only tram, no highway => no access
        way.setTag("railway", "tram");
        flags = footEncoder.handleWayTags(encodingManager.createEdgeFlags(), way, footEncoder.getAccess(way), 0);
        assertEquals(0, flags.ints[0]);
    }

    @Test
    public void testPier() {
        ReaderWay way = new ReaderWay(1);
        way.setTag("man_made", "pier");
        IntsRef flags = footEncoder.handleWayTags(encodingManager.createEdgeFlags(), way, footEncoder.getAccess(way), 0);
        assertNotEquals(0, flags.ints[0]);
    }

    @Test
    public void testFerrySpeed() {
        ReaderWay way = new ReaderWay(1);
        way.setTag("route", "ferry");
        // a bit longer than an hour
        way.setTag("duration:seconds", "4000");
        IntsRef flags = footEncoder.handleWayTags(encodingManager.createEdgeFlags(), way, footEncoder.getAccess(way), 0);
        assertTrue(footEncoder.getSpeed(flags) > footEncoder.getMaxSpeed());
        assertEquals(20, footEncoder.getSpeed(flags), .1);
    }

    @Test
    public void testMixSpeedAndSafe() {
        ReaderWay way = new ReaderWay(1);
        way.setTag("highway", "motorway");
        IntsRef flags = footEncoder.handleWayTags(encodingManager.createEdgeFlags(), way, footEncoder.getAccess(way), 0);
        assertEquals(0, flags.ints[0]);

        way.setTag("sidewalk", "yes");
        flags = footEncoder.handleWayTags(encodingManager.createEdgeFlags(), way, footEncoder.getAccess(way), 0);
        assertEquals(5, footEncoder.getSpeed(flags), 1e-1);

        way.clearTags();
        way.setTag("highway", "track");
        flags = footEncoder.handleWayTags(encodingManager.createEdgeFlags(), way, footEncoder.getAccess(way), 0);
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
        IntsRef flags = footEncoder.handleWayTags(encodingManager.createEdgeFlags(), way, footEncoder.getAccess(way), 0);
        assertEquals(FootFlagEncoder.MEAN_SPEED, footEncoder.getSpeed(flags), 1e-1);

        way.setTag("highway", "track");
        way.setTag("sac_scale", "mountain_hiking");
        flags = footEncoder.handleWayTags(encodingManager.createEdgeFlags(), way, footEncoder.getAccess(way), 0);
        assertEquals(FootFlagEncoder.SLOW_SPEED, footEncoder.getSpeed(flags), 1e-1);
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

    @Test
    public void testBlockByDefault() {
        FootFlagEncoder tmpFootEncoder = new FootFlagEncoder();
        EncodingManager.create(tmpFootEncoder);

        ReaderNode node = new ReaderNode(1, -1, -1);
        node.setTag("barrier", "gate");
        // potential barriers are no barrier by default
        assertTrue(tmpFootEncoder.handleNodeTags(node) == 0);
        node.setTag("access", "no");
        assertTrue(tmpFootEncoder.handleNodeTags(node) > 0);

        // absolute barriers always block
        node = new ReaderNode(1, -1, -1);
        node.setTag("barrier", "fence");
        assertTrue(tmpFootEncoder.handleNodeTags(node) > 0);
        node.setTag("barrier", "fence");
        node.setTag("access", "yes");
        assertTrue(tmpFootEncoder.handleNodeTags(node) > 0);

        // Now let's block potential barriers per default (if no other access tag exists)
        tmpFootEncoder.setBlockByDefault(true);

        node = new ReaderNode(1, -1, -1);
        node.setTag("barrier", "gate");
        assertTrue(tmpFootEncoder.handleNodeTags(node) > 0);
        node.setTag("access", "yes");
        assertTrue(tmpFootEncoder.handleNodeTags(node) == 0);

        node = new ReaderNode(1, -1, -1);
        node.setTag("barrier", "fence");
        assertTrue(tmpFootEncoder.handleNodeTags(node) > 0);

        // Let's stop block potential barriers to test if barrier:cattle_grid is non blocking
        tmpFootEncoder.setBlockByDefault(false);

        node = new ReaderNode(1, -1, -1);
        node.setTag("barrier", "cattle_grid");
        assertTrue(tmpFootEncoder.handleNodeTags(node) == 0);
    }
}
