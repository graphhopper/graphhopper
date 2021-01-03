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
import com.graphhopper.routing.ev.BooleanEncodedValue;
import com.graphhopper.routing.ev.DecimalEncodedValue;
import com.graphhopper.storage.Graph;
import com.graphhopper.storage.GraphBuilder;
import com.graphhopper.storage.IntsRef;
import com.graphhopper.util.*;
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
    private final DecimalEncodedValue footAvgSpeedEnc = footEncoder.getAverageSpeedEnc();
    private final BooleanEncodedValue footAccessEnc = footEncoder.getAccessEnc();
    private final DecimalEncodedValue carAvSpeedEnc = encodingManager.getEncoder("car").getAverageSpeedEnc();
    private final BooleanEncodedValue carAccessEnc = encodingManager.getEncoder("car").getAccessEnc();

    @Test
    public void testGetSpeed() {
        IntsRef fl = encodingManager.createEdgeFlags();
        footAccessEnc.setBool(false, fl, true);
        footAccessEnc.setBool(true, fl, true);
        footAvgSpeedEnc.setDecimal(false, fl, 10);
        assertEquals(10, footAvgSpeedEnc.getDecimal(false, fl), 1e-1);
    }

    @Test
    public void testSteps() {
        ReaderWay way = new ReaderWay(1);
        way.setTag("highway", "service");
        IntsRef flags = footEncoder.handleWayTags(encodingManager.createEdgeFlags(), way, footEncoder.getAccess(way));
        assertEquals(FootFlagEncoder.MEAN_SPEED, footAvgSpeedEnc.getDecimal(false, flags), 1e-1);

        way.setTag("highway", "steps");
        flags = footEncoder.handleWayTags(encodingManager.createEdgeFlags(), way, footEncoder.getAccess(way));
        assertTrue(FootFlagEncoder.MEAN_SPEED > footAvgSpeedEnc.getDecimal(false, flags));
    }

    @Test
    public void testCombined() {
        Graph g = new GraphBuilder(encodingManager).create();
        EdgeIteratorState edge = g.edge(0, 1);
        edge.set(footAvgSpeedEnc, 10.0).set(footAccessEnc, true, true);
        edge.set(carAvSpeedEnc, 100.0).set(carAccessEnc, true, false);

        assertEquals(10, edge.get(footAvgSpeedEnc), 1e-1);
        assertTrue(edge.get(footAccessEnc));
        assertTrue(edge.getReverse(footAccessEnc));

        assertEquals(100, edge.get(carAvSpeedEnc), 1e-1);
        assertTrue(edge.get(carAccessEnc));
        assertFalse(edge.getReverse(carAccessEnc));

        IntsRef raw = encodingManager.createEdgeFlags();
        footAvgSpeedEnc.setDecimal(false, raw, 10);
        footAccessEnc.setBool(false, raw, true);
        footAccessEnc.setBool(true, raw, true);
        assertEquals(0, carAvSpeedEnc.getDecimal(false, raw), 1e-1);
    }

    @Test
    public void testGraph() {
        Graph g = new GraphBuilder(encodingManager).create();
        g.edge(0, 1).setDistance(10).set(footAvgSpeedEnc, 10.0).set(footAccessEnc, true, true);
        g.edge(0, 2).setDistance(10).set(footAvgSpeedEnc, 5.0).set(footAccessEnc, true, true);
        g.edge(1, 3).setDistance(10).set(footAvgSpeedEnc, 10.0).set(footAccessEnc, true, true);
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

        way.setTag("highway", "platform");
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
        IntsRef flags = footEncoder.handleWayTags(encodingManager.createEdgeFlags(), way, footEncoder.getAccess(way));
        assertFalse(flags.isEmpty());

        way.clearTags();
        way.setTag("highway", "track");
        way.setTag("railway", "platform");
        flags = footEncoder.handleWayTags(encodingManager.createEdgeFlags(), way, footEncoder.getAccess(way));
        assertFalse(flags.isEmpty());

        way.clearTags();
        // only tram, no highway => no access
        way.setTag("railway", "tram");
        flags = footEncoder.handleWayTags(encodingManager.createEdgeFlags(), way, footEncoder.getAccess(way));
        assertTrue(flags.isEmpty());
    }

    @Test
    public void testPier() {
        ReaderWay way = new ReaderWay(1);
        way.setTag("man_made", "pier");
        IntsRef flags = footEncoder.handleWayTags(encodingManager.createEdgeFlags(), way, footEncoder.getAccess(way));
        assertFalse(flags.isEmpty());
    }

    @Test
    public void testFerrySpeed() {
        ReaderWay way = new ReaderWay(1);
        way.setTag("route", "ferry");
        // a bit longer than an hour
        way.setTag("duration:seconds", "4000");
        assertEquals(30, footEncoder.ferrySpeedCalc.getSpeed(way), .1);
        IntsRef flags = footEncoder.handleWayTags(encodingManager.createEdgeFlags(), way, footEncoder.getAccess(way));
        assertEquals(15, footAvgSpeedEnc.getDecimal(false, flags), .1);
    }

    @Test
    public void testMixSpeedAndSafe() {
        ReaderWay way = new ReaderWay(1);
        way.setTag("highway", "motorway");
        IntsRef flags = footEncoder.handleWayTags(encodingManager.createEdgeFlags(), way, footEncoder.getAccess(way));
        assertEquals(0, flags.ints[0]);

        way.setTag("sidewalk", "yes");
        flags = footEncoder.handleWayTags(encodingManager.createEdgeFlags(), way, footEncoder.getAccess(way));
        assertEquals(5, footAvgSpeedEnc.getDecimal(false, flags), 1e-1);

        way.clearTags();
        way.setTag("highway", "track");
        flags = footEncoder.handleWayTags(encodingManager.createEdgeFlags(), way, footEncoder.getAccess(way));
        assertEquals(5, footAvgSpeedEnc.getDecimal(false, flags), 1e-1);
    }

    @Test
    public void testPriority() {
        ReaderWay way = new ReaderWay(1);
        way.setTag("highway", "cycleway");
        assertEquals(PriorityCode.UNCHANGED.getValue(), footEncoder.handlePriority(way, null));

        way.setTag("highway", "primary");
        assertEquals(PriorityCode.AVOID_IF_POSSIBLE.getValue(), footEncoder.handlePriority(way, null));

        way.setTag("highway", "track");
        way.setTag("bicycle", "official");
        assertEquals(PriorityCode.AVOID_IF_POSSIBLE.getValue(), footEncoder.handlePriority(way, null));

        way.setTag("highway", "track");
        way.setTag("bicycle", "designated");
        assertEquals(PriorityCode.AVOID_IF_POSSIBLE.getValue(), footEncoder.handlePriority(way, null));

        way.setTag("highway", "cycleway");
        way.setTag("bicycle", "designated");
        way.setTag("foot", "designated");
        assertEquals(PriorityCode.PREFER.getValue(), footEncoder.handlePriority(way, null));

        way.clearTags();
        way.setTag("highway", "primary");
        way.setTag("sidewalk", "yes");
        assertEquals(PriorityCode.UNCHANGED.getValue(), footEncoder.handlePriority(way, null));

        way.clearTags();
        way.setTag("highway", "cycleway");
        way.setTag("sidewalk", "no");
        assertEquals(PriorityCode.UNCHANGED.getValue(), footEncoder.handlePriority(way, null));

        way.clearTags();
        way.setTag("highway", "road");
        way.setTag("bicycle", "official");
        way.setTag("sidewalk", "no");
        assertEquals(PriorityCode.AVOID_IF_POSSIBLE.getValue(), footEncoder.handlePriority(way, null));

        way.clearTags();
        way.setTag("highway", "trunk");
        way.setTag("sidewalk", "no");
        assertEquals(PriorityCode.AVOID_IF_POSSIBLE.getValue(), footEncoder.handlePriority(way, null));
        way.setTag("sidewalk", "none");
        assertEquals(PriorityCode.AVOID_IF_POSSIBLE.getValue(), footEncoder.handlePriority(way, null));

        way.clearTags();
        way.setTag("highway", "residential");
        way.setTag("sidewalk", "yes");
        assertEquals(PriorityCode.PREFER.getValue(), footEncoder.handlePriority(way, null));
    }

    @Test
    public void testSlowHiking() {
        ReaderWay way = new ReaderWay(1);
        way.setTag("highway", "track");
        way.setTag("sac_scale", "hiking");
        IntsRef flags = footEncoder.handleWayTags(encodingManager.createEdgeFlags(), way, footEncoder.getAccess(way));
        assertEquals(FootFlagEncoder.MEAN_SPEED, footAvgSpeedEnc.getDecimal(false, flags), 1e-1);

        way.setTag("highway", "track");
        way.setTag("sac_scale", "mountain_hiking");
        flags = footEncoder.handleWayTags(encodingManager.createEdgeFlags(), way, footEncoder.getAccess(way));
        assertEquals(FootFlagEncoder.SLOW_SPEED, footAvgSpeedEnc.getDecimal(false, flags), 1e-1);
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
        // by default do not block access due to fords!
        ReaderNode node = new ReaderNode(1, -1, -1);
        node.setTag("ford", "no");
        assertTrue(footEncoder.handleNodeTags(node) == 0);

        node = new ReaderNode(1, -1, -1);
        node.setTag("ford", "yes");
        // no barrier!
        assertTrue(footEncoder.handleNodeTags(node) == 0);

        // barrier!
        node.setTag("foot", "no");
        assertTrue(footEncoder.handleNodeTags(node) > 0);

        FootFlagEncoder tmpEncoder = new FootFlagEncoder(new PMap("block_fords=true"));
        EncodingManager.create(tmpEncoder);
        node = new ReaderNode(1, -1, -1);
        node.setTag("ford", "no");
        assertTrue(tmpEncoder.handleNodeTags(node) == 0);

        node = new ReaderNode(1, -1, -1);
        node.setTag("ford", "yes");
        assertTrue(tmpEncoder.handleNodeTags(node) != 0);
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

        // block potential barriers per default (if no other access tag exists)
        tmpFootEncoder = new FootFlagEncoder(new PMap("block_barriers=true"));
        EncodingManager.create(tmpFootEncoder);
        node = new ReaderNode(1, -1, -1);
        node.setTag("barrier", "gate");
        assertTrue(tmpFootEncoder.handleNodeTags(node) > 0);
        node.setTag("access", "yes");
        assertTrue(tmpFootEncoder.handleNodeTags(node) == 0);

        node = new ReaderNode(1, -1, -1);
        node.setTag("barrier", "fence");
        assertTrue(tmpFootEncoder.handleNodeTags(node) > 0);

        // don't block potential barriers: barrier:cattle_grid should not block here
        tmpFootEncoder = new FootFlagEncoder();
        EncodingManager.create(tmpFootEncoder);
        node = new ReaderNode(1, -1, -1);
        node.setTag("barrier", "cattle_grid");
        assertTrue(tmpFootEncoder.handleNodeTags(node) == 0);
    }
}
