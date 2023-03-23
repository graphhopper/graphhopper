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

import com.graphhopper.reader.ReaderNode;
import com.graphhopper.reader.ReaderWay;
import com.graphhopper.reader.osm.conditional.DateRangeParser;
import com.graphhopper.routing.ev.*;
import com.graphhopper.routing.util.AccessFilter;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.routing.util.PriorityCode;
import com.graphhopper.storage.BaseGraph;
import com.graphhopper.storage.NodeAccess;
import com.graphhopper.util.*;
import org.junit.jupiter.api.Test;

import java.text.DateFormat;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author don-philipe
 */
public class WheelchairTagParserTest {
    private final BooleanEncodedValue wheelchairAccessEnc;
    private final DecimalEncodedValue wheelchairAvSpeedEnc;
    private final DecimalEncodedValue wheelchairPriorityEnc;
    private final BooleanEncodedValue carAccessEnc;
    private final DecimalEncodedValue carAvSpeedEnc;
    private final EncodingManager encodingManager;
    private final WheelchairAccessParser accessParser;
    private final WheelchairAverageSpeedParser speedParser;
    private final WheelchairPriorityParser prioParser;

    public WheelchairTagParserTest() {
        wheelchairAccessEnc = VehicleAccess.create("wheelchair");
        wheelchairAvSpeedEnc = VehicleSpeed.create("wheelchair", 4, 1, true);
        wheelchairPriorityEnc = VehiclePriority.create("wheelchair", 4, PriorityCode.getFactor(1), false);
        carAccessEnc = VehicleAccess.create("car");
        carAvSpeedEnc = VehicleSpeed.create("car", 5, 5, false);
        encodingManager = EncodingManager.start()
                .add(wheelchairAccessEnc).add(wheelchairAvSpeedEnc).add(wheelchairPriorityEnc).add(RouteNetwork.create(FootNetwork.KEY))
                .add(carAccessEnc).add(carAvSpeedEnc)
                .build();
        accessParser = new WheelchairAccessParser(encodingManager, new PMap());
        accessParser.init(new DateRangeParser());
        speedParser = new WheelchairAverageSpeedParser(encodingManager, new PMap()) {
            @Override
            public void applyWayTags(ReaderWay way, int edgeId, EdgeIntAccess edgeIntAccess) {
                if (way.hasTag("point_list") && way.hasTag("edge_distance"))
                    super.applyWayTags(way, edgeId, edgeIntAccess);
            }
        };
        prioParser = new WheelchairPriorityParser(encodingManager, new PMap());
    }

    @Test
    public void testGetSpeed() {
        EdgeIntAccess edgeIntAccess = new ArrayEdgeIntAccess(encodingManager.getIntsForFlags());
        int edgeId = 0;
        wheelchairAccessEnc.setBool(false, edgeId, edgeIntAccess, true);
        wheelchairAccessEnc.setBool(true, edgeId, edgeIntAccess, true);
        wheelchairAvSpeedEnc.setDecimal(false, edgeId, edgeIntAccess, 10);
        assertEquals(10, wheelchairAvSpeedEnc.getDecimal(false, edgeId, edgeIntAccess), .1);
    }

    @Test
    public void testCombined() {
        BaseGraph g = new BaseGraph.Builder(encodingManager).create();
        EdgeIteratorState edge = g.edge(0, 1);
        edge.set(wheelchairAvSpeedEnc, 10.0).set(wheelchairAccessEnc, true, true);
        edge.set(carAvSpeedEnc, 100.0).set(carAccessEnc, true, false);

        assertEquals(10, edge.get(wheelchairAvSpeedEnc), .1);
        assertTrue(edge.get(wheelchairAccessEnc));
        assertTrue(edge.getReverse(wheelchairAccessEnc));

        assertEquals(100, edge.get(carAvSpeedEnc), .1);
        assertTrue(edge.get(carAccessEnc));
        assertFalse(edge.getReverse(carAccessEnc));

        EdgeIntAccess edgeIntAccess = new ArrayEdgeIntAccess(encodingManager.getIntsForFlags());
        int edgeId = 0;
        wheelchairAvSpeedEnc.setDecimal(false, edgeId, edgeIntAccess, 10);
        wheelchairAccessEnc.setBool(false, edgeId, edgeIntAccess, true);
        wheelchairAccessEnc.setBool(true, edgeId, edgeIntAccess, true);
        assertEquals(0, carAvSpeedEnc.getDecimal(false, edgeId, edgeIntAccess), .1);
    }

    @Test
    public void testGraph() {
        BaseGraph g = new BaseGraph.Builder(encodingManager).create();
        g.edge(0, 1).setDistance(10).set(wheelchairAvSpeedEnc, 10.0).set(wheelchairAccessEnc, true, true);
        g.edge(0, 2).setDistance(10).set(wheelchairAvSpeedEnc, 5.0).set(wheelchairAccessEnc, true, true);
        g.edge(1, 3).setDistance(10).set(wheelchairAvSpeedEnc, 10.0).set(wheelchairAccessEnc, true, true);
        EdgeExplorer out = g.createEdgeExplorer(AccessFilter.outEdges(accessParser.getAccessEnc()));
        assertEquals(GHUtility.asSet(1, 2), GHUtility.getNeighbors(out.setBaseNode(0)));
        assertEquals(GHUtility.asSet(0, 3), GHUtility.getNeighbors(out.setBaseNode(1)));
        assertEquals(GHUtility.asSet(0), GHUtility.getNeighbors(out.setBaseNode(2)));
    }

    @Test
    public void testAccess() {
        ReaderWay way = new ReaderWay(1);

        way.setTag("highway", "motorway");
        way.setTag("sidewalk", "yes");
        assertTrue(accessParser.getAccess(way).isWay());
        way.setTag("sidewalk", "left");
        assertTrue(accessParser.getAccess(way).isWay());

        way.setTag("sidewalk", "none");
        assertTrue(accessParser.getAccess(way).canSkip());

        way.clearTags();
        way.setTag("highway", "tertiary");
        way.setTag("sidewalk", "left");
        way.setTag("access", "private");
        assertTrue(accessParser.getAccess(way).canSkip());
        way.clearTags();

        way.setTag("highway", "pedestrian");
        assertTrue(accessParser.getAccess(way).isWay());

        way.setTag("highway", "footway");
        assertTrue(accessParser.getAccess(way).isWay());

        way.setTag("highway", "platform");
        assertTrue(accessParser.getAccess(way).isWay());

        way.setTag("highway", "motorway");
        assertTrue(accessParser.getAccess(way).canSkip());

        way.setTag("bicycle", "official");
        assertTrue(accessParser.getAccess(way).canSkip());
        way.setTag("foot", "no");
        assertTrue(accessParser.getAccess(way).canSkip());

        way.setTag("foot", "official");
        assertTrue(accessParser.getAccess(way).isWay());

        way.clearTags();
        way.setTag("highway", "service");
        way.setTag("access", "no");
        assertTrue(accessParser.getAccess(way).canSkip());
        way.setTag("foot", "yes");
        assertTrue(accessParser.getAccess(way).isWay());

        way.clearTags();
        way.setTag("highway", "service");
        way.setTag("vehicle", "no");
        assertTrue(accessParser.getAccess(way).isWay());
        way.setTag("foot", "no");
        assertTrue(accessParser.getAccess(way).canSkip());

        way.clearTags();
        way.setTag("highway", "tertiary");
        way.setTag("motorroad", "yes");
        assertTrue(accessParser.getAccess(way).canSkip());

        way.clearTags();
        way.setTag("highway", "cycleway");
        assertTrue(accessParser.getAccess(way).isWay());
        way.setTag("foot", "no");
        assertTrue(accessParser.getAccess(way).canSkip());
        way.setTag("access", "yes");
        assertTrue(accessParser.getAccess(way).canSkip());

        way.clearTags();
        way.setTag("highway", "service");
        way.setTag("foot", "yes");
        way.setTag("access", "no");
        assertTrue(accessParser.getAccess(way).isWay());

        way.clearTags();
        way.setTag("highway", "track");
        way.setTag("ford", "yes");
        assertTrue(accessParser.getAccess(way).canSkip());

        way.clearTags();
        way.setTag("route", "ferry");
        assertTrue(accessParser.getAccess(way).isFerry());
        way.setTag("foot", "no");
        assertTrue(accessParser.getAccess(way).canSkip());

        // #1562, test if ferry route with foot
        way.clearTags();
        way.setTag("route", "ferry");
        way.setTag("foot", "yes");
        assertTrue(accessParser.getAccess(way).isFerry());

        way.setTag("foot", "designated");
        assertTrue(accessParser.getAccess(way).isFerry());

        way.setTag("foot", "official");
        assertTrue(accessParser.getAccess(way).isFerry());

        way.setTag("foot", "permissive");
        assertTrue(accessParser.getAccess(way).isFerry());

        way.setTag("foot", "no");
        assertTrue(accessParser.getAccess(way).canSkip());

        way.setTag("foot", "designated");
        way.setTag("access", "private");
        assertTrue(accessParser.getAccess(way).canSkip());

        DateFormat simpleDateFormat = Helper.createFormatter("yyyy MMM dd");

        way.clearTags();
        way.setTag("highway", "footway");
        way.setTag("access:conditional", "no @ (" + simpleDateFormat.format(new Date().getTime()) + ")");
        assertTrue(accessParser.getAccess(way).canSkip());

        way.clearTags();
        way.setTag("highway", "footway");
        way.setTag("access", "no");
        way.setTag("access:conditional", "yes @ (" + simpleDateFormat.format(new Date().getTime()) + ")");
        assertTrue(accessParser.getAccess(way).isWay());

        way.clearTags();
        way.setTag("highway", "steps");
        assertTrue(accessParser.getAccess(way).canSkip());

        way.clearTags();
        // allow paths as they are used as generic path
        way.setTag("highway", "path");
        assertTrue(accessParser.getAccess(way).isWay());

        way.clearTags();
        way.setTag("highway", "track");
        assertTrue(accessParser.getAccess(way).canSkip());

        way.clearTags();
        way.setTag("sac_scale", "hiking");
        assertTrue(accessParser.getAccess(way).canSkip());

        way.clearTags();
        way.setTag("highway", "footway");
        assertTrue(accessParser.getAccess(way).isWay());
        way.setTag("incline", "up");
        assertTrue(accessParser.getAccess(way).isWay());
        way.setTag("incline", "3%");
        assertTrue(accessParser.getAccess(way).isWay());
        way.setTag("incline", "9.1%");
        assertTrue(accessParser.getAccess(way).canSkip());
        way.setTag("incline", "1°");
        assertTrue(accessParser.getAccess(way).isWay());
        way.setTag("incline", "5°");
        assertTrue(accessParser.getAccess(way).canSkip());
        way.setTag("incline", "-4%");
        assertTrue(accessParser.getAccess(way).isWay());
        way.setTag("incline", "-9%");
        assertTrue(accessParser.getAccess(way).canSkip());
        way.setTag("incline", "-3°");
        assertTrue(accessParser.getAccess(way).isWay());
        way.setTag("incline", "-6.5°");
        assertTrue(accessParser.getAccess(way).canSkip());

        way.clearTags();
        way.setTag("highway", "footway");
        way.setTag("wheelchair", "no");
        assertTrue(accessParser.getAccess(way).canSkip());
        way.setTag("wheelchair", "limited");
        assertTrue(accessParser.getAccess(way).isWay());

        way.clearTags();
        way.setTag("highway", "footway");
        assertTrue(accessParser.getAccess(way).isWay());
        way.setTag("kerb", "lowered");
        assertTrue(accessParser.getAccess(way).isWay());
        way.setTag("kerb", "raised");
        assertTrue(accessParser.getAccess(way).canSkip());
        way.setTag("kerb", "2cm");
        assertTrue(accessParser.getAccess(way).isWay());
        way.setTag("kerb", "4cm");
        assertTrue(accessParser.getAccess(way).canSkip());
        way.setTag("kerb", "20mm");
        assertTrue(accessParser.getAccess(way).isWay());

        // highway tag required
        way.clearTags();
        way.setTag("wheelchair", "yes");
        assertTrue(accessParser.getAccess(way).canSkip());
        way.setTag("highway", "footway");
        assertTrue(accessParser.getAccess(way).isWay());
    }

    @Test
    public void testPier() {
        ReaderWay way = new ReaderWay(1);
        way.setTag("man_made", "pier");
        EdgeIntAccess edgeIntAccess = new ArrayEdgeIntAccess(encodingManager.getIntsForFlags());
        int edgeId = 0;
        accessParser.handleWayTags(edgeId, edgeIntAccess, way, null);
        speedParser.handleWayTags(edgeId, edgeIntAccess, way, null);
        assertTrue(wheelchairAccessEnc.getBool(false, edgeId, edgeIntAccess));
        assertTrue(wheelchairAccessEnc.getBool(true, edgeId, edgeIntAccess));
        assertEquals(5, wheelchairAvSpeedEnc.getDecimal(false, edgeId, edgeIntAccess));
    }

    @Test
    public void testMixSpeedAndSafe() {
        ReaderWay way = new ReaderWay(1);
        way.setTag("highway", "motorway");
        EdgeIntAccess edgeIntAccess = new ArrayEdgeIntAccess(encodingManager.getIntsForFlags());
        int edgeId = 0;
        accessParser.handleWayTags(edgeId, edgeIntAccess, way, null);
        assertFalse(wheelchairAccessEnc.getBool(false, edgeId, edgeIntAccess));
        assertFalse(wheelchairAccessEnc.getBool(true, edgeId, edgeIntAccess));

        way.setTag("sidewalk", "yes");
        edgeIntAccess = new ArrayEdgeIntAccess(encodingManager.getIntsForFlags());
        accessParser.handleWayTags(edgeId, edgeIntAccess, way, null);
        speedParser.handleWayTags(edgeId, edgeIntAccess, way, null);
        assertTrue(wheelchairAccessEnc.getBool(false, edgeId, edgeIntAccess));
        assertTrue(wheelchairAccessEnc.getBool(true, edgeId, edgeIntAccess));
        assertEquals(5, wheelchairAvSpeedEnc.getDecimal(false, edgeId, edgeIntAccess), .1);

        way.clearTags();
        way.setTag("highway", "track");
        edgeIntAccess = new ArrayEdgeIntAccess(encodingManager.getIntsForFlags());
        accessParser.handleWayTags(edgeId, edgeIntAccess, way, null);
        assertFalse(wheelchairAccessEnc.getBool(false, edgeId, edgeIntAccess));
        assertFalse(wheelchairAccessEnc.getBool(true, edgeId, edgeIntAccess));
    }

    @Test
    public void testPriority() {
        ReaderWay way = new ReaderWay(1);
        way.setTag("highway", "cycleway");
        assertEquals(PriorityCode.UNCHANGED.getValue(), prioParser.handlePriority(way, null));
        way.setTag("highway", "primary");
        assertEquals(PriorityCode.AVOID.getValue(), prioParser.handlePriority(way, null));
        way.setTag("highway", "secondary");
        assertEquals(PriorityCode.AVOID.getValue(), prioParser.handlePriority(way, null));

        way.setTag("highway", "track");
        way.setTag("bicycle", "official");
        assertEquals(PriorityCode.SLIGHT_AVOID.getValue(), prioParser.handlePriority(way, null));

        way.setTag("highway", "track");
        way.setTag("bicycle", "designated");
        assertEquals(PriorityCode.SLIGHT_AVOID.getValue(), prioParser.handlePriority(way, null));

        way.setTag("highway", "cycleway");
        way.setTag("bicycle", "designated");
        way.setTag("foot", "designated");
        assertEquals(PriorityCode.PREFER.getValue(), prioParser.handlePriority(way, null));

        way.clearTags();
        way.setTag("highway", "primary");
        way.setTag("sidewalk", "yes");
        assertEquals(PriorityCode.SLIGHT_AVOID.getValue(), prioParser.handlePriority(way, null));

        way.clearTags();
        way.setTag("highway", "cycleway");
        way.setTag("sidewalk", "no");
        assertEquals(PriorityCode.AVOID.getValue(), prioParser.handlePriority(way, null));

        way.clearTags();
        way.setTag("highway", "road");
        way.setTag("bicycle", "official");
        way.setTag("sidewalk", "no");
        assertEquals(PriorityCode.SLIGHT_AVOID.getValue(), prioParser.handlePriority(way, null));

        way.clearTags();
        way.setTag("highway", "trunk");
        way.setTag("sidewalk", "no");
        assertEquals(PriorityCode.VERY_BAD.getValue(), prioParser.handlePriority(way, null));
        way.setTag("sidewalk", "none");
        assertEquals(PriorityCode.VERY_BAD.getValue(), prioParser.handlePriority(way, null));

        way.clearTags();
        way.setTag("highway", "residential");
        way.setTag("sidewalk", "yes");
        assertEquals(PriorityCode.PREFER.getValue(), prioParser.handlePriority(way, null));

        way.clearTags();
        way.setTag("highway", "footway");
        assertEquals(PriorityCode.PREFER.getValue(), prioParser.handlePriority(way, null));
        way.setTag("wheelchair", "designated");
        assertEquals(PriorityCode.VERY_NICE.getValue(), prioParser.handlePriority(way, null));

        way.clearTags();
        way.setTag("highway", "footway");
        assertEquals(PriorityCode.PREFER.getValue(), prioParser.handlePriority(way, null));
        way.setTag("wheelchair", "limited");
        assertEquals(PriorityCode.AVOID.getValue(), prioParser.handlePriority(way, null));
    }

    @Test
    public void testBarrierAccess() {
        // by default allow access through the gate for bike & foot!
        ReaderNode node = new ReaderNode(1, -1, -1);
        node.setTag("barrier", "gate");
        // no barrier!
        assertFalse(accessParser.isBarrier(node));

        node = new ReaderNode(1, -1, -1);
        node.setTag("barrier", "gate");
        node.setTag("access", "yes");
        // no barrier!
        assertFalse(accessParser.isBarrier(node));

        node = new ReaderNode(1, -1, -1);
        node.setTag("barrier", "gate");
        node.setTag("access", "no");
        // barrier!
        assertTrue(accessParser.isBarrier(node));

        node.setTag("bicycle", "yes");
        // no barrier!?
        // assertTrue(wheelchairEncoder.handleNodeTags(node) == false);

        node = new ReaderNode(1, -1, -1);
        node.setTag("barrier", "gate");
        node.setTag("access", "no");
        node.setTag("foot", "yes");
        // no barrier!
        assertFalse(accessParser.isBarrier(node));

        node.setTag("locked", "yes");
        // barrier!
        assertTrue(accessParser.isBarrier(node));
    }

    @Test
    public void testBlockByDefault() {
        ReaderNode node = new ReaderNode(1, -1, -1);
        node.setTag("barrier", "gate");
        // passByDefaultBarriers are no barrier by default
        assertFalse(accessParser.isBarrier(node));
        node.setTag("access", "no");
        assertTrue(accessParser.isBarrier(node));

        // these barriers block
        node = new ReaderNode(1, -1, -1);
        node.setTag("barrier", "fence");
        assertTrue(accessParser.isBarrier(node));
        node.setTag("barrier", "wall");
        assertTrue(accessParser.isBarrier(node));
        node.setTag("barrier", "handrail");
        assertTrue(accessParser.isBarrier(node));
        node.setTag("barrier", "turnstile");
        assertTrue(accessParser.isBarrier(node));
        // Explictly allowed access is allowed
        node.setTag("barrier", "fence");
        node.setTag("access", "yes");
        assertFalse(accessParser.isBarrier(node));

        node = new ReaderNode(1, -1, -1);
        node.setTag("barrier", "gate");
        node.setTag("access", "yes");
        assertFalse(accessParser.isBarrier(node));

        node = new ReaderNode(1, -1, -1);
        node.setTag("barrier", "kerb");
        assertFalse(accessParser.isBarrier(node));
        node.setTag("wheelchair", "yes");
        assertFalse(accessParser.isBarrier(node));

        node = new ReaderNode(1, -1, -1);
        node.setTag("barrier", "fence");
        assertTrue(accessParser.isBarrier(node));
    }

    @Test
    public void testSurfaces() {
        ReaderWay way = new ReaderWay(1);

        way.setTag("highway", "footway");
        assertTrue(accessParser.getAccess(way).isWay());

        way.setTag("surface", "cobblestone");
        assertTrue(accessParser.getAccess(way).canSkip());
        way.setTag("surface", "sand");
        assertTrue(accessParser.getAccess(way).canSkip());
        way.setTag("surface", "gravel");
        assertTrue(accessParser.getAccess(way).canSkip());

        way.setTag("surface", "asphalt");
        assertTrue(accessParser.getAccess(way).isWay());

        way.clearTags();
        way.setTag("highway", "service");
        assertTrue(accessParser.getAccess(way).isWay());

        way.setTag("surface", "sand");
        assertTrue(accessParser.getAccess(way).canSkip());

        way.setTag("sidewalk", "left");
        assertTrue(accessParser.getAccess(way).isWay());

        way.setTag("sidewalk:left:surface", "cobblestone");
        assertTrue(accessParser.getAccess(way).canSkip());
    }

    @Test
    public void testSmoothness() {
        ReaderWay way = new ReaderWay(1);

        way.setTag("highway", "residential");
        assertTrue(accessParser.getAccess(way).isWay());

        way.setTag("smoothness", "bad");
        assertTrue(accessParser.getAccess(way).canSkip());

        way.setTag("sidewalk", "both");
        assertTrue(accessParser.getAccess(way).isWay());

        way.setTag("sidewalk:both:smoothness", "horrible");
        assertTrue(accessParser.getAccess(way).canSkip());
    }

    @Test
    public void testApplyWayTags() {
        BaseGraph graph = new BaseGraph.Builder(encodingManager).set3D(true).create();
        NodeAccess na = graph.getNodeAccess();
        // incline of 5% over all
        na.setNode(0, 51.1, 12.0010, 50);
        na.setNode(1, 51.1, 12.0015, 55);
        EdgeIteratorState edge01 = graph.edge(0, 1).setWayGeometry(Helper.createPointList3D(51.1, 12.0011, 49, 51.1, 12.0015, 55));
        edge01.setDistance(100);
        GHUtility.setSpeed(5, 5, wheelchairAccessEnc, wheelchairAvSpeedEnc, edge01);

        // incline of 10% & shorter edge
        na.setNode(2, 51.2, 12.1010, 50);
        na.setNode(3, 51.2, 12.1015, 60);
        EdgeIteratorState edge23 = graph.edge(2, 3).setWayGeometry(Helper.createPointList3D(51.2, 12.1011, 49, 51.2, 12.1015, 55));
        edge23.setDistance(30);
        GHUtility.setSpeed(5, 5, wheelchairAccessEnc, wheelchairAvSpeedEnc, edge23);

        // incline of 10% & longer edge
        na.setNode(4, 51.2, 12.101, 50);
        na.setNode(5, 51.2, 12.102, 60);
        EdgeIteratorState edge45 = graph.edge(2, 3).setWayGeometry(Helper.createPointList3D(51.2, 12.1011, 49, 51.2, 12.1015, 55));
        edge45.setDistance(100);
        GHUtility.setSpeed(5, 5, wheelchairAccessEnc, wheelchairAvSpeedEnc, edge45);


        ReaderWay way1 = new ReaderWay(1);
        way1.setTag("point_list", edge01.fetchWayGeometry(FetchMode.ALL));
        way1.setTag("edge_distance", edge01.getDistance());
        EdgeIntAccess edgeIntAccess = graph.createEdgeIntAccess();
        speedParser.applyWayTags(way1, edge01.getEdge(), edgeIntAccess);

        assertTrue(edge01.get(wheelchairAccessEnc));
        assertTrue(edge01.getReverse(wheelchairAccessEnc));
        assertEquals(2, edge01.get(wheelchairAvSpeedEnc), 0);
        assertEquals(2, edge01.get(speedParser.getAverageSpeedEnc()), 0);
        assertEquals(5, edge01.getReverse(speedParser.getAverageSpeedEnc()), 0);

        ReaderWay way2 = new ReaderWay(2);
        way2.setTag("point_list", edge23.fetchWayGeometry(FetchMode.ALL));
        way2.setTag("edge_distance", edge23.getDistance());
        speedParser.applyWayTags(way2, edge23.getEdge(), edgeIntAccess);

        assertTrue(edge23.get(wheelchairAccessEnc));
        assertTrue(edge23.getReverse(wheelchairAccessEnc));

        assertEquals(2, edge23.get(speedParser.getAverageSpeedEnc()), 0);
        assertEquals(2, edge23.getReverse(speedParser.getAverageSpeedEnc()), 0);

        // only exclude longer edges with too large incline:
        ReaderWay way3 = new ReaderWay(3);
        way3.setTag("point_list", edge45.fetchWayGeometry(FetchMode.ALL));
        way3.setTag("edge_distance", edge45.getDistance());
        speedParser.handleWayTags(edge45.getEdge(), edgeIntAccess, way3, null);
        speedParser.applyWayTags(way3, edge45.getEdge(), edgeIntAccess);

        assertEquals(0, edge45.get(wheelchairAvSpeedEnc), 0.1);
        assertEquals(0, edge45.getReverse(wheelchairAvSpeedEnc), 0.1);
    }
}
