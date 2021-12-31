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
import com.graphhopper.storage.*;
import com.graphhopper.util.EdgeExplorer;
import com.graphhopper.util.EdgeIteratorState;
import com.graphhopper.util.GHUtility;
import com.graphhopper.util.Helper;
import org.junit.jupiter.api.Test;

import java.text.DateFormat;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author don-philipe
 */
public class WheelchairFlagEncoderTest {
    private final EncodingManager encodingManager = EncodingManager.create("car,wheelchair");
    private final WheelchairFlagEncoder wheelchairEncoder = (WheelchairFlagEncoder) encodingManager.getEncoder("wheelchair");
    private final DecimalEncodedValue wheelchairAvSpeedEnc = wheelchairEncoder.getAverageSpeedEnc();
    private final BooleanEncodedValue wheelchairAccessEnc = wheelchairEncoder.getAccessEnc();
    private final DecimalEncodedValue carAvSpeedEnc = encodingManager.getEncoder("car").getAverageSpeedEnc();
    private final BooleanEncodedValue carAccessEnc = encodingManager.getEncoder("car").getAccessEnc();

    @Test
    public void testGetSpeed() {
        IntsRef fl = encodingManager.createEdgeFlags();
        wheelchairAccessEnc.setBool(false, fl, true);
        wheelchairAccessEnc.setBool(true, fl, true);
        wheelchairAvSpeedEnc.setDecimal(false, fl, 10);
        assertEquals(10, wheelchairAvSpeedEnc.getDecimal(false, fl), .1);
    }

    @Test
    public void testCombined() {
        Graph g = new GraphBuilder(encodingManager).create();
        FlagEncoder carEncoder = encodingManager.getEncoder("car");
        EdgeIteratorState edge = g.edge(0, 1);
        edge.set(wheelchairAvSpeedEnc, 10.0).set(wheelchairAccessEnc, true, true);
        edge.set(carAvSpeedEnc, 100.0).set(carAccessEnc, true, false);

        assertEquals(10, edge.get(wheelchairAvSpeedEnc), .1);
        assertTrue(edge.get(wheelchairAccessEnc));
        assertTrue(edge.getReverse(wheelchairAccessEnc));

        assertEquals(100, edge.get(carAvSpeedEnc), .1);
        assertTrue(edge.get(carAccessEnc));
        assertFalse(edge.getReverse(carAccessEnc));

        IntsRef raw = encodingManager.createEdgeFlags();
        wheelchairAvSpeedEnc.setDecimal(false, raw, 10);
        wheelchairAccessEnc.setBool(false, raw, true);
        wheelchairAccessEnc.setBool(true, raw, true);
        assertEquals(0, carAvSpeedEnc.getDecimal(false, raw), .1);
    }

    @Test
    public void testGraph() {
        Graph g = new GraphBuilder(encodingManager).create();
        g.edge(0, 1).setDistance(10).set(wheelchairAvSpeedEnc, 10.0).set(wheelchairAccessEnc, true, true);
        g.edge(0, 2).setDistance(10).set(wheelchairAvSpeedEnc, 5.0).set(wheelchairAccessEnc, true, true);
        g.edge(1, 3).setDistance(10).set(wheelchairAvSpeedEnc, 10.0).set(wheelchairAccessEnc, true, true);
        EdgeExplorer out = g.createEdgeExplorer(AccessFilter.outEdges(wheelchairEncoder.getAccessEnc()));
        assertEquals(GHUtility.asSet(1, 2), GHUtility.getNeighbors(out.setBaseNode(0)));
        assertEquals(GHUtility.asSet(0, 3), GHUtility.getNeighbors(out.setBaseNode(1)));
        assertEquals(GHUtility.asSet(0), GHUtility.getNeighbors(out.setBaseNode(2)));
    }

    @Test
    public void testAccess() {
        ReaderWay way = new ReaderWay(1);

        way.setTag("highway", "motorway");
        way.setTag("sidewalk", "yes");
        assertTrue(wheelchairEncoder.getAccess(way).isWay());
        way.setTag("sidewalk", "left");
        assertTrue(wheelchairEncoder.getAccess(way).isWay());

        way.setTag("sidewalk", "none");
        assertTrue(wheelchairEncoder.getAccess(way).canSkip());

        way.clearTags();
        way.setTag("highway", "tertiary");
        way.setTag("sidewalk", "left");
        way.setTag("access", "private");
        assertTrue(wheelchairEncoder.getAccess(way).canSkip());
        way.clearTags();

        way.setTag("highway", "pedestrian");
        assertTrue(wheelchairEncoder.getAccess(way).isWay());

        way.setTag("highway", "footway");
        assertTrue(wheelchairEncoder.getAccess(way).isWay());

        way.setTag("highway", "platform");
        assertTrue(wheelchairEncoder.getAccess(way).isWay());

        way.setTag("highway", "motorway");
        assertTrue(wheelchairEncoder.getAccess(way).canSkip());

        way.setTag("bicycle", "official");
        assertTrue(wheelchairEncoder.getAccess(way).canSkip());
        way.setTag("foot", "no");
        assertTrue(wheelchairEncoder.getAccess(way).canSkip());

        way.setTag("foot", "official");
        assertTrue(wheelchairEncoder.getAccess(way).isWay());

        way.clearTags();
        way.setTag("highway", "service");
        way.setTag("access", "no");
        assertTrue(wheelchairEncoder.getAccess(way).canSkip());
        way.setTag("foot", "yes");
        assertTrue(wheelchairEncoder.getAccess(way).isWay());

        way.clearTags();
        way.setTag("highway", "service");
        way.setTag("vehicle", "no");
        assertTrue(wheelchairEncoder.getAccess(way).isWay());
        way.setTag("foot", "no");
        assertTrue(wheelchairEncoder.getAccess(way).canSkip());

        way.clearTags();
        way.setTag("highway", "tertiary");
        way.setTag("motorroad", "yes");
        assertTrue(wheelchairEncoder.getAccess(way).canSkip());

        way.clearTags();
        way.setTag("highway", "cycleway");
        assertTrue(wheelchairEncoder.getAccess(way).isWay());
        way.setTag("foot", "no");
        assertTrue(wheelchairEncoder.getAccess(way).canSkip());
        way.setTag("access", "yes");
        assertTrue(wheelchairEncoder.getAccess(way).canSkip());

        way.clearTags();
        way.setTag("highway", "service");
        way.setTag("foot", "yes");
        way.setTag("access", "no");
        assertTrue(wheelchairEncoder.getAccess(way).isWay());

        way.clearTags();
        way.setTag("highway", "track");
        way.setTag("ford", "yes");
        assertTrue(wheelchairEncoder.getAccess(way).canSkip());

        way.clearTags();
        way.setTag("route", "ferry");
        assertTrue(wheelchairEncoder.getAccess(way).isFerry());
        way.setTag("foot", "no");
        assertTrue(wheelchairEncoder.getAccess(way).canSkip());

        // #1562, test if ferry route with foot
        way.clearTags();
        way.setTag("route", "ferry");
        way.setTag("foot", "yes");
        assertTrue(wheelchairEncoder.getAccess(way).isFerry());

        way.setTag("foot", "designated");
        assertTrue(wheelchairEncoder.getAccess(way).isFerry());

        way.setTag("foot", "official");
        assertTrue(wheelchairEncoder.getAccess(way).isFerry());

        way.setTag("foot", "permissive");
        assertTrue(wheelchairEncoder.getAccess(way).isFerry());

        way.setTag("foot", "no");
        assertTrue(wheelchairEncoder.getAccess(way).canSkip());

        way.setTag("foot", "designated");
        way.setTag("access", "private");
        assertTrue(wheelchairEncoder.getAccess(way).canSkip());

        DateFormat simpleDateFormat = Helper.createFormatter("yyyy MMM dd");

        way.clearTags();
        way.setTag("highway", "footway");
        way.setTag("access:conditional", "no @ (" + simpleDateFormat.format(new Date().getTime()) + ")");
        assertTrue(wheelchairEncoder.getAccess(way).canSkip());

        way.clearTags();
        way.setTag("highway", "footway");
        way.setTag("access", "no");
        way.setTag("access:conditional", "yes @ (" + simpleDateFormat.format(new Date().getTime()) + ")");
        assertTrue(wheelchairEncoder.getAccess(way).isWay());

        way.clearTags();
        way.setTag("highway", "steps");
        assertTrue(wheelchairEncoder.getAccess(way).canSkip());

        way.clearTags();
        // allow paths as they are used as generic path
        way.setTag("highway", "path");
        assertTrue(wheelchairEncoder.getAccess(way).isWay());

        way.clearTags();
        way.setTag("highway", "track");
        assertTrue(wheelchairEncoder.getAccess(way).canSkip());

        way.clearTags();
        way.setTag("sac_scale", "hiking");
        assertTrue(wheelchairEncoder.getAccess(way).canSkip());

        way.clearTags();
        way.setTag("highway", "footway");
        assertTrue(wheelchairEncoder.getAccess(way).isWay());
        way.setTag("incline", "up");
        assertTrue(wheelchairEncoder.getAccess(way).isWay());
        way.setTag("incline", "3%");
        assertTrue(wheelchairEncoder.getAccess(way).isWay());
        way.setTag("incline", "9.1%");
        assertTrue(wheelchairEncoder.getAccess(way).canSkip());
        way.setTag("incline", "1°");
        assertTrue(wheelchairEncoder.getAccess(way).isWay());
        way.setTag("incline", "5°");
        assertTrue(wheelchairEncoder.getAccess(way).canSkip());
        way.setTag("incline", "-4%");
        assertTrue(wheelchairEncoder.getAccess(way).isWay());
        way.setTag("incline", "-9%");
        assertTrue(wheelchairEncoder.getAccess(way).canSkip());
        way.setTag("incline", "-3°");
        assertTrue(wheelchairEncoder.getAccess(way).isWay());
        way.setTag("incline", "-6.5°");
        assertTrue(wheelchairEncoder.getAccess(way).canSkip());

        way.clearTags();
        way.setTag("highway", "footway");
        way.setTag("wheelchair", "no");
        assertTrue(wheelchairEncoder.getAccess(way).canSkip());
        way.setTag("wheelchair", "limited");
        assertTrue(wheelchairEncoder.getAccess(way).isWay());

        way.clearTags();
        way.setTag("highway", "footway");
        assertTrue(wheelchairEncoder.getAccess(way).isWay());
        way.setTag("kerb", "lowered");
        assertTrue(wheelchairEncoder.getAccess(way).isWay());
        way.setTag("kerb", "raised");
        assertTrue(wheelchairEncoder.getAccess(way).canSkip());
        way.setTag("kerb", "2cm");
        assertTrue(wheelchairEncoder.getAccess(way).isWay());
        way.setTag("kerb", "4cm");
        assertTrue(wheelchairEncoder.getAccess(way).canSkip());
        way.setTag("kerb", "20mm");
        assertTrue(wheelchairEncoder.getAccess(way).isWay());

        // highway tag required
        way.clearTags();
        way.setTag("wheelchair", "yes");
        assertTrue(wheelchairEncoder.getAccess(way).canSkip());
        way.setTag("highway", "footway");
        assertTrue(wheelchairEncoder.getAccess(way).isWay());
    }

    @Test
    public void testPier() {
        ReaderWay way = new ReaderWay(1);
        way.setTag("man_made", "pier");
        IntsRef flags = wheelchairEncoder.handleWayTags(encodingManager.createEdgeFlags(), way);
        assertFalse(flags.isEmpty());
    }

    @Test
    public void testMixSpeedAndSafe() {
        ReaderWay way = new ReaderWay(1);
        way.setTag("highway", "motorway");
        IntsRef flags = wheelchairEncoder.handleWayTags(encodingManager.createEdgeFlags(), way);
        assertTrue(flags.isEmpty());

        way.setTag("sidewalk", "yes");
        flags = wheelchairEncoder.handleWayTags(encodingManager.createEdgeFlags(), way);
        assertEquals(5, wheelchairAvSpeedEnc.getDecimal(false, flags), .1);

        way.clearTags();
        way.setTag("highway", "track");
        flags = wheelchairEncoder.handleWayTags(encodingManager.createEdgeFlags(), way);
        assertEquals(0, wheelchairAvSpeedEnc.getDecimal(false, flags), .1);
    }

    @Test
    public void testPriority() {
        ReaderWay way = new ReaderWay(1);
        way.setTag("highway", "cycleway");
        assertEquals(PriorityCode.UNCHANGED.getValue(), wheelchairEncoder.handlePriority(way, null));
        way.setTag("highway", "primary");
        assertEquals(PriorityCode.AVOID.getValue(), wheelchairEncoder.handlePriority(way, null));
        way.setTag("highway", "secondary");
        assertEquals(PriorityCode.AVOID.getValue(), wheelchairEncoder.handlePriority(way, null));

        way.setTag("highway", "track");
        way.setTag("bicycle", "official");
        assertEquals(PriorityCode.SLIGHT_AVOID.getValue(), wheelchairEncoder.handlePriority(way, null));

        way.setTag("highway", "track");
        way.setTag("bicycle", "designated");
        assertEquals(PriorityCode.SLIGHT_AVOID.getValue(), wheelchairEncoder.handlePriority(way, null));

        way.setTag("highway", "cycleway");
        way.setTag("bicycle", "designated");
        way.setTag("foot", "designated");
        assertEquals(PriorityCode.PREFER.getValue(), wheelchairEncoder.handlePriority(way, null));

        way.clearTags();
        way.setTag("highway", "primary");
        way.setTag("sidewalk", "yes");
        assertEquals(PriorityCode.UNCHANGED.getValue(), wheelchairEncoder.handlePriority(way, null));

        way.clearTags();
        way.setTag("highway", "cycleway");
        way.setTag("sidewalk", "no");
        assertEquals(PriorityCode.UNCHANGED.getValue(), wheelchairEncoder.handlePriority(way, null));

        way.clearTags();
        way.setTag("highway", "road");
        way.setTag("bicycle", "official");
        way.setTag("sidewalk", "no");
        assertEquals(PriorityCode.SLIGHT_AVOID.getValue(), wheelchairEncoder.handlePriority(way, null));

        way.clearTags();
        way.setTag("highway", "trunk");
        way.setTag("sidewalk", "no");
        assertEquals(PriorityCode.AVOID.getValue(), wheelchairEncoder.handlePriority(way, null));
        way.setTag("sidewalk", "none");
        assertEquals(PriorityCode.AVOID.getValue(), wheelchairEncoder.handlePriority(way, null));

        way.clearTags();
        way.setTag("highway", "residential");
        way.setTag("sidewalk", "yes");
        assertEquals(PriorityCode.PREFER.getValue(), wheelchairEncoder.handlePriority(way, null));

        way.clearTags();
        way.setTag("highway", "footway");
        assertEquals(PriorityCode.PREFER.getValue(), wheelchairEncoder.handlePriority(way, null));
        way.setTag("wheelchair", "designated");
        assertEquals(PriorityCode.VERY_NICE.getValue(), wheelchairEncoder.handlePriority(way, null));

        way.clearTags();
        way.setTag("highway", "footway");
        assertEquals(PriorityCode.PREFER.getValue(), wheelchairEncoder.handlePriority(way, null));
        way.setTag("wheelchair", "limited");
        assertEquals(PriorityCode.AVOID.getValue(), wheelchairEncoder.handlePriority(way, null));
    }

    @Test
    public void testBarrierAccess() {
        // by default allow access through the gate for bike & foot!
        ReaderNode node = new ReaderNode(1, -1, -1);
        node.setTag("barrier", "gate");
        // no barrier!
        assertFalse(wheelchairEncoder.isBarrier(node));

        node = new ReaderNode(1, -1, -1);
        node.setTag("barrier", "gate");
        node.setTag("access", "yes");
        // no barrier!
        assertFalse(wheelchairEncoder.isBarrier(node));

        node = new ReaderNode(1, -1, -1);
        node.setTag("barrier", "gate");
        node.setTag("access", "no");
        // barrier!
        assertTrue(wheelchairEncoder.isBarrier(node));

        node.setTag("bicycle", "yes");
        // no barrier!?
        // assertTrue(wheelchairEncoder.handleNodeTags(node) == false);

        node = new ReaderNode(1, -1, -1);
        node.setTag("barrier", "gate");
        node.setTag("access", "no");
        node.setTag("foot", "yes");
        // no barrier!
        assertFalse(wheelchairEncoder.isBarrier(node));

        node.setTag("locked", "yes");
        // barrier!
        assertTrue(wheelchairEncoder.isBarrier(node));
    }

    @Test
    public void testBlockByDefault() {
        WheelchairFlagEncoder tmpWheelchairEncoder = new WheelchairFlagEncoder();
        EncodingManager.create(tmpWheelchairEncoder);

        ReaderNode node = new ReaderNode(1, -1, -1);
        node.setTag("barrier", "gate");
        // passByDefaultBarriers are no barrier by default
        assertFalse(tmpWheelchairEncoder.isBarrier(node));
        node.setTag("access", "no");
        assertTrue(tmpWheelchairEncoder.isBarrier(node));

        // these barriers block
        node = new ReaderNode(1, -1, -1);
        node.setTag("barrier", "fence");
        assertTrue(tmpWheelchairEncoder.isBarrier(node));
        node.setTag("barrier", "wall");
        assertTrue(tmpWheelchairEncoder.isBarrier(node));
        node.setTag("barrier", "handrail");
        assertTrue(tmpWheelchairEncoder.isBarrier(node));
        node.setTag("barrier", "turnstile");
        assertTrue(tmpWheelchairEncoder.isBarrier(node));
        // Explictly allowed access is allowed
        node.setTag("barrier", "fence");
        node.setTag("access", "yes");
        assertFalse(tmpWheelchairEncoder.isBarrier(node));

        node = new ReaderNode(1, -1, -1);
        node.setTag("barrier", "gate");
        node.setTag("access", "yes");
        assertFalse(tmpWheelchairEncoder.isBarrier(node));

        node = new ReaderNode(1, -1, -1);
        node.setTag("barrier", "kerb");
        assertFalse(tmpWheelchairEncoder.isBarrier(node));
        node.setTag("wheelchair", "yes");
        assertFalse(tmpWheelchairEncoder.isBarrier(node));

        node = new ReaderNode(1, -1, -1);
        node.setTag("barrier", "fence");
        assertTrue(tmpWheelchairEncoder.isBarrier(node));
    }

    @Test
    public void testSurfaces() {
        ReaderWay way = new ReaderWay(1);

        way.setTag("highway", "footway");
        assertTrue(wheelchairEncoder.getAccess(way).isWay());

        way.setTag("surface", "cobblestone");
        assertTrue(wheelchairEncoder.getAccess(way).canSkip());
        way.setTag("surface", "sand");
        assertTrue(wheelchairEncoder.getAccess(way).canSkip());
        way.setTag("surface", "gravel");
        assertTrue(wheelchairEncoder.getAccess(way).canSkip());

        way.setTag("surface", "asphalt");
        assertTrue(wheelchairEncoder.getAccess(way).isWay());

        way.clearTags();
        way.setTag("highway", "service");
        assertTrue(wheelchairEncoder.getAccess(way).isWay());

        way.setTag("surface", "sand");
        assertTrue(wheelchairEncoder.getAccess(way).canSkip());

        way.setTag("sidewalk", "left");
        assertTrue(wheelchairEncoder.getAccess(way).isWay());

        way.setTag("sidewalk:left:surface", "cobblestone");
        assertTrue(wheelchairEncoder.getAccess(way).canSkip());
    }

    @Test
    public void testSmoothness() {
        ReaderWay way = new ReaderWay(1);

        way.setTag("highway", "residential");
        assertTrue(wheelchairEncoder.getAccess(way).isWay());

        way.setTag("smoothness", "bad");
        assertTrue(wheelchairEncoder.getAccess(way).canSkip());

        way.setTag("sidewalk", "both");
        assertTrue(wheelchairEncoder.getAccess(way).isWay());

        way.setTag("sidewalk:both:smoothness", "horrible");
        assertTrue(wheelchairEncoder.getAccess(way).canSkip());
    }

    @Test
    public void testApplyWayTags() {
        GraphHopperStorage graph = new GraphBuilder(encodingManager).set3D(true).create();
        NodeAccess na = graph.getNodeAccess();
        // incline of 5% over all
        na.setNode(0, 51.1, 12.0010, 50);
        na.setNode(1, 51.1, 12.0015, 55);
        EdgeIteratorState edge01 = graph.edge(0, 1).setWayGeometry(Helper.createPointList3D(51.1, 12.0011, 49, 51.1, 12.0015, 55));
        edge01.setDistance(100);
        GHUtility.setSpeed(5, 5, wheelchairEncoder, edge01);

        // incline of 10% & shorter edge
        na.setNode(2, 51.2, 12.1010, 50);
        na.setNode(3, 51.2, 12.1015, 60);
        EdgeIteratorState edge23 = graph.edge(2, 3).setWayGeometry(Helper.createPointList3D(51.2, 12.1011, 49, 51.2, 12.1015, 55));
        edge23.setDistance(30);
        GHUtility.setSpeed(5, 5, wheelchairEncoder, edge23);

        // incline of 10% & longer edge
        na.setNode(4, 51.2, 12.101, 50);
        na.setNode(5, 51.2, 12.102, 60);
        EdgeIteratorState edge45 = graph.edge(2, 3).setWayGeometry(Helper.createPointList3D(51.2, 12.1011, 49, 51.2, 12.1015, 55));
        edge45.setDistance(100);
        GHUtility.setSpeed(5, 5, wheelchairEncoder, edge45);


        wheelchairEncoder.applyWayTags(new ReaderWay(1), edge01);

        assertTrue(edge01.get(wheelchairAccessEnc));
        assertTrue(edge01.getReverse(wheelchairAccessEnc));
        assertEquals(2, edge01.get(wheelchairEncoder.getAverageSpeedEnc()), 0);
        assertEquals(5, edge01.getReverse(wheelchairEncoder.getAverageSpeedEnc()), 0);


        wheelchairEncoder.applyWayTags(new ReaderWay(2), edge23);

        assertTrue(edge23.get(wheelchairAccessEnc));
        assertTrue(edge23.getReverse(wheelchairAccessEnc));
        assertEquals(2, edge23.get(wheelchairEncoder.getAverageSpeedEnc()), 0);
        assertEquals(2, edge23.getReverse(wheelchairEncoder.getAverageSpeedEnc()), 0);


        // only exclude longer edges with too large incline:
        wheelchairEncoder.applyWayTags(new ReaderWay(3), edge45);

        assertFalse(edge45.get(wheelchairAccessEnc));
        assertFalse(edge45.getReverse(wheelchairAccessEnc));
    }
}
