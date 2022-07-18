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
package com.graphhopper.routing;

import com.graphhopper.routing.ev.*;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.routing.util.TraversalMode;
import com.graphhopper.routing.weighting.FastestWeighting;
import com.graphhopper.routing.weighting.ShortestWeighting;
import com.graphhopper.routing.weighting.Weighting;
import com.graphhopper.storage.BaseGraph;
import com.graphhopper.storage.Graph;
import com.graphhopper.storage.NodeAccess;
import com.graphhopper.util.*;
import com.graphhopper.util.details.PathDetail;
import com.graphhopper.util.details.PathDetailsBuilderFactory;
import com.graphhopper.util.details.PathDetailsFromEdges;
import org.junit.jupiter.api.Test;

import java.util.*;

import static com.graphhopper.search.EdgeKVStorage.KeyValue.createKV;
import static com.graphhopper.storage.AbstractGraphStorageTester.assertPList;
import static com.graphhopper.util.Parameters.Details.*;
import com.graphhopper.search.EdgeKVStorage.KeyValue;
import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Peter Karich
 */
public class PathTest {
    private final BooleanEncodedValue carAccessEnc = new SimpleBooleanEncodedValue("access", true);
    private final DecimalEncodedValue carAvSpeedEnc = new DecimalEncodedValueImpl("speed", 5, 5, false);
    private final EncodingManager carManager = EncodingManager.start().add(carAccessEnc).add(carAvSpeedEnc).build();
    private final BooleanEncodedValue mixedCarAccessEnc = new SimpleBooleanEncodedValue("mixed_car_access", true);
    private final DecimalEncodedValue mixedCarSpeedEnc = new DecimalEncodedValueImpl("mixed_car_speed", 5, 5, false);
    private final BooleanEncodedValue mixedFootAccessEnc = new SimpleBooleanEncodedValue("mixed_foot_access", true);
    private final DecimalEncodedValue mixedFootSpeedEnc = new DecimalEncodedValueImpl("mixed_foot_speed", 4, 1, false);
    private final EncodingManager mixedEncodingManager = EncodingManager.start().add(mixedCarAccessEnc).add(mixedCarSpeedEnc).add(mixedFootAccessEnc).add(mixedFootSpeedEnc).build();
    private final TranslationMap trMap = TranslationMapTest.SINGLETON;
    private final Translation tr = trMap.getWithFallBack(Locale.US);
    private final RoundaboutGraph roundaboutGraph = new RoundaboutGraph();
    private final Graph pathDetailGraph = generatePathDetailsGraph();

    @Test
    public void testFound() {
        BaseGraph g = new BaseGraph.Builder(carManager).create();
        Path p = new Path(g);
        assertFalse(p.isFound());
        assertEquals(0, p.getDistance(), 1e-7);
        assertEquals(0, p.calcNodes().size());
    }

    @Test
    public void testWayList() {
        BaseGraph g = new BaseGraph.Builder(carManager).create();
        NodeAccess na = g.getNodeAccess();
        na.setNode(0, 0.0, 0.1);
        na.setNode(1, 1.0, 0.1);
        na.setNode(2, 2.0, 0.1);

        EdgeIteratorState edge1 = g.edge(0, 1).setDistance(1000).set(carAccessEnc, true, true).set(carAvSpeedEnc, 10.0);

        edge1.setWayGeometry(Helper.createPointList(8, 1, 9, 1));
        EdgeIteratorState edge2 = g.edge(2, 1).setDistance(2000).set(carAccessEnc, true, true).set(carAvSpeedEnc, 50.0);
        edge2.setWayGeometry(Helper.createPointList(11, 1, 10, 1));

        SPTEntry e1 = new SPTEntry(edge2.getEdge(), 2, 1, new SPTEntry(edge1.getEdge(), 1, 1, new SPTEntry(0, 1)));
        FastestWeighting weighting = new FastestWeighting(carAccessEnc, carAvSpeedEnc);
        Path path = extractPath(g, weighting, e1);
        // 0-1-2
        assertPList(Helper.createPointList(0, 0.1, 8, 1, 9, 1, 1, 0.1, 10, 1, 11, 1, 2, 0.1), path.calcPoints());
        InstructionList instr = InstructionsFromEdges.calcInstructions(path, path.graph, weighting, carManager, tr);
        Instruction tmp = instr.get(0);
        assertEquals(3000.0, tmp.getDistance(), 0.0);
        assertEquals(504000L, tmp.getTime());
        assertEquals("continue", tmp.getTurnDescription(tr));
        assertEquals(6, tmp.getLength());

        tmp = instr.get(1);
        assertEquals(0.0, tmp.getDistance(), 0.0);
        assertEquals(0L, tmp.getTime());
        assertEquals("arrive at destination", tmp.getTurnDescription(tr));
        assertEquals(0, tmp.getLength());

        int acc = 0;
        for (Instruction instruction : instr) {
            acc += instruction.getLength();
        }
        assertEquals(path.calcPoints().size() - 1, acc);

        // force minor change for instructions
        edge2.setKeyValues(createKV("name", "2"));
        na.setNode(3, 1.0, 1.0);
        g.edge(1, 3).setDistance(1000).set(carAccessEnc, true, true).set(carAvSpeedEnc, 10.0);

        e1 = new SPTEntry(edge2.getEdge(), 2, 1,
                new SPTEntry(edge1.getEdge(), 1, 1,
                        new SPTEntry(0, 1)
                )
        );
        path = extractPath(g, weighting, e1);
        instr = InstructionsFromEdges.calcInstructions(path, path.graph, weighting, carManager, tr);

        tmp = instr.get(0);
        assertEquals(1000.0, tmp.getDistance(), 0);
        assertEquals(360000L, tmp.getTime());
        assertEquals("continue", tmp.getTurnDescription(tr));
        assertEquals(3, tmp.getLength());

        tmp = instr.get(1);
        assertEquals(2000.0, tmp.getDistance(), 0);
        assertEquals(144000L, tmp.getTime());
        assertEquals("turn sharp right onto 2", tmp.getTurnDescription(tr));
        assertEquals(3, tmp.getLength());
        acc = 0;
        for (Instruction instruction : instr) {
            acc += instruction.getLength();
        }
        assertEquals(path.calcPoints().size() - 1, acc);

        // now reverse order
        e1 = new SPTEntry(edge1.getEdge(), 0, 1, new SPTEntry(edge2.getEdge(), 1, 1, new SPTEntry(2, 1)));
        path = extractPath(g, weighting, e1);
        // 2-1-0
        assertPList(Helper.createPointList(2, 0.1, 11, 1, 10, 1, 1, 0.1, 9, 1, 8, 1, 0, 0.1), path.calcPoints());

        instr = InstructionsFromEdges.calcInstructions(path, path.graph, weighting, carManager, tr);
        tmp = instr.get(0);
        assertEquals(2000.0, tmp.getDistance(), 0);
        assertEquals(144000L, tmp.getTime());
        assertEquals("continue onto 2", tmp.getTurnDescription(tr));
        assertEquals(3, tmp.getLength());

        tmp = instr.get(1);
        assertEquals(1000.0, tmp.getDistance(), 0);
        assertEquals(360000L, tmp.getTime());
        assertEquals("turn sharp left", tmp.getTurnDescription(tr));
        assertEquals(3, tmp.getLength());
        acc = 0;
        for (Instruction instruction : instr) {
            acc += instruction.getLength();
        }
        assertEquals(path.calcPoints().size() - 1, acc);
    }

    @Test
    public void testFindInstruction() {
        BaseGraph g = new BaseGraph.Builder(carManager).create();
        NodeAccess na = g.getNodeAccess();
        na.setNode(0, 0.0, 0.0);
        na.setNode(1, 5.0, 0.0);
        na.setNode(2, 5.0, 0.5);
        na.setNode(3, 10.0, 0.5);
        na.setNode(4, 7.5, 0.25);
        na.setNode(5, 5.0, 1.0);

        EdgeIteratorState edge1 = g.edge(0, 1).setDistance(1000).set(carAccessEnc, true, true).set(carAvSpeedEnc, 50.0);
        edge1.setWayGeometry(Helper.createPointList());
        edge1.setKeyValues(createKV("name", "Street 1"));
        EdgeIteratorState edge2 = g.edge(1, 2).setDistance(1000).set(carAccessEnc, true, true).set(carAvSpeedEnc, 50.0);
        edge2.setWayGeometry(Helper.createPointList());
        edge2.setKeyValues(createKV("name", "Street 2"));
        EdgeIteratorState edge3 = g.edge(2, 3).setDistance(1000).set(carAccessEnc, true, true).set(carAvSpeedEnc, 50.0);
        edge3.setWayGeometry(Helper.createPointList());
        edge3.setKeyValues(createKV("name", "Street 3"));
        EdgeIteratorState edge4 = g.edge(3, 4).setDistance(500).set(carAccessEnc, true, true).set(carAvSpeedEnc, 50.0);
        edge4.setWayGeometry(Helper.createPointList());
        edge4.setKeyValues(createKV("name", "Street 4"));

        g.edge(1, 5).setDistance(10000).set(carAccessEnc, true, true).set(carAvSpeedEnc, 50.0);
        g.edge(2, 5).setDistance(10000).set(carAccessEnc, true, true).set(carAvSpeedEnc, 50.0);
        g.edge(3, 5).setDistance(100000).set(carAccessEnc, true, true).set(carAvSpeedEnc, 50.0);

        SPTEntry e1 =
                new SPTEntry(edge4.getEdge(), 4, 1,
                        new SPTEntry(edge3.getEdge(), 3, 1,
                                new SPTEntry(edge2.getEdge(), 2, 1,
                                        new SPTEntry(edge1.getEdge(), 1, 1,
                                                new SPTEntry(0, 1)
                                        ))));
        FastestWeighting weighting = new FastestWeighting(carAccessEnc, carAvSpeedEnc);
        Path path = extractPath(g, weighting, e1);

        InstructionList il = InstructionsFromEdges.calcInstructions(path, path.graph, weighting, carManager, tr);
        assertEquals(5, il.size());
        assertEquals(Instruction.CONTINUE_ON_STREET, il.get(0).getSign());
        assertEquals(Instruction.TURN_RIGHT, il.get(1).getSign());
        assertEquals(Instruction.TURN_LEFT, il.get(2).getSign());
        assertEquals(Instruction.TURN_SHARP_LEFT, il.get(3).getSign());
        assertEquals(Instruction.FINISH, il.get(4).getSign());
    }

    /**
     * Test roundabout instructions for different profiles
     */
    @Test
    void testCalcInstructionsRoundabout() {
        calcInstructionsRoundabout(mixedCarAccessEnc, mixedCarSpeedEnc);
        calcInstructionsRoundabout(mixedFootAccessEnc, mixedFootSpeedEnc);
    }

    public void calcInstructionsRoundabout(BooleanEncodedValue accessEnc, DecimalEncodedValue speedEnc) {
        ShortestWeighting weighting = new ShortestWeighting(accessEnc, speedEnc);
        Path p = new Dijkstra(roundaboutGraph.g, weighting, TraversalMode.NODE_BASED)
                .calcPath(1, 8);
        assertTrue(p.isFound());
        assertEquals("[1, 2, 3, 4, 5, 8]", p.calcNodes().toString());
        InstructionList wayList = InstructionsFromEdges.calcInstructions(p, p.graph, weighting, mixedEncodingManager, tr);
        // Test instructions
        List<String> tmpList = getTurnDescriptions(wayList);
        assertEquals(Arrays.asList("continue onto MainStreet 1 2",
                        "At roundabout, take exit 3 onto 5-8",
                        "arrive at destination"),
                tmpList);
        // Test Radian
        double delta = roundaboutGraph.getAngle(1, 2, 5, 8);
        RoundaboutInstruction instr = (RoundaboutInstruction) wayList.get(1);
        assertEquals(delta, instr.getTurnAngle(), 0.01);

        // case of continuing a street through a roundabout
        p = new Dijkstra(roundaboutGraph.g, weighting, TraversalMode.NODE_BASED).
                calcPath(1, 7);
        wayList = InstructionsFromEdges.calcInstructions(p, p.graph, weighting, mixedEncodingManager, tr);
        tmpList = getTurnDescriptions(wayList);
        assertEquals(Arrays.asList("continue onto MainStreet 1 2",
                        "At roundabout, take exit 2 onto MainStreet 4 7",
                        "arrive at destination"),
                tmpList);
        // Test Radian
        delta = roundaboutGraph.getAngle(1, 2, 4, 7);
        instr = (RoundaboutInstruction) wayList.get(1);
        assertEquals(delta, instr.getTurnAngle(), 0.01);
    }

    @Test
    public void testCalcInstructionsRoundaboutBegin() {
        ShortestWeighting weighting = new ShortestWeighting(mixedCarAccessEnc, mixedCarSpeedEnc);
        Path p = new Dijkstra(roundaboutGraph.g, weighting, TraversalMode.NODE_BASED)
                .calcPath(2, 8);
        assertTrue(p.isFound());
        InstructionList wayList = InstructionsFromEdges.calcInstructions(p, p.graph, weighting, mixedEncodingManager, tr);
        List<String> tmpList = getTurnDescriptions(wayList);
        assertEquals(Arrays.asList("At roundabout, take exit 3 onto 5-8",
                        "arrive at destination"),
                tmpList);
    }

    @Test
    public void testCalcInstructionsRoundaboutDirectExit() {
        roundaboutGraph.inverse3to9();
        ShortestWeighting weighting = new ShortestWeighting(mixedCarAccessEnc, mixedCarSpeedEnc);
        Path p = new Dijkstra(roundaboutGraph.g, weighting, TraversalMode.NODE_BASED)
                .calcPath(6, 8);
        assertTrue(p.isFound());
        InstructionList wayList = InstructionsFromEdges.calcInstructions(p, p.graph, weighting, mixedEncodingManager, tr);
        List<String> tmpList = getTurnDescriptions(wayList);
        assertEquals(Arrays.asList("continue onto 3-6",
                        "At roundabout, take exit 3 onto 5-8",
                        "arrive at destination"),
                tmpList);
        roundaboutGraph.inverse3to9();
    }

    @Test
    public void testCalcAverageSpeedDetails() {
        ShortestWeighting weighting = new ShortestWeighting(carAccessEnc, carAvSpeedEnc);
        Path p = new Dijkstra(pathDetailGraph, weighting, TraversalMode.NODE_BASED).calcPath(1, 5);
        assertTrue(p.isFound());

        Map<String, List<PathDetail>> details = PathDetailsFromEdges.calcDetails(p, carManager, weighting,
                Arrays.asList(AVERAGE_SPEED), new PathDetailsBuilderFactory(), 0);
        assertTrue(details.size() == 1);

        List<PathDetail> averageSpeedDetails = details.get(AVERAGE_SPEED);
        assertEquals(4, averageSpeedDetails.size());
        assertEquals(45.0, averageSpeedDetails.get(0).getValue());
        assertEquals(90.0, averageSpeedDetails.get(1).getValue());
        assertEquals(10.0, averageSpeedDetails.get(2).getValue());
        assertEquals(45.0, averageSpeedDetails.get(3).getValue());

        assertEquals(0, averageSpeedDetails.get(0).getFirst());
        assertEquals(1, averageSpeedDetails.get(1).getFirst());
        assertEquals(2, averageSpeedDetails.get(2).getFirst());
        assertEquals(3, averageSpeedDetails.get(3).getFirst());
        assertEquals(4, averageSpeedDetails.get(3).getLast());
    }

    @Test
    public void testCalcAverageSpeedDetailsWithShortDistances_issue1848() {
        ShortestWeighting weighting = new ShortestWeighting(carAccessEnc, carAvSpeedEnc);
        Path p = new Dijkstra(pathDetailGraph, weighting, TraversalMode.NODE_BASED).calcPath(1, 6);
        assertTrue(p.isFound());
        Map<String, List<PathDetail>> details = PathDetailsFromEdges.calcDetails(p, carManager, weighting,
                Arrays.asList(AVERAGE_SPEED), new PathDetailsBuilderFactory(), 0);
        assertTrue(details.size() == 1);
        List<PathDetail> averageSpeedDetails = details.get(AVERAGE_SPEED);
        assertEquals(4, averageSpeedDetails.size());

        // reverse path includes 'null' value as first
        p = new Dijkstra(pathDetailGraph, weighting, TraversalMode.NODE_BASED).calcPath(6, 1);
        assertTrue(p.isFound());
        details = PathDetailsFromEdges.calcDetails(p, carManager, weighting,
                Arrays.asList(AVERAGE_SPEED), new PathDetailsBuilderFactory(), 0);
        assertTrue(details.size() == 1);
        averageSpeedDetails = details.get(AVERAGE_SPEED);
        assertEquals(5, averageSpeedDetails.size());
        assertNull(averageSpeedDetails.get(0).getValue());
    }

    @Test
    public void testCalcStreetNameDetails() {
        ShortestWeighting weighting = new ShortestWeighting(carAccessEnc, carAvSpeedEnc);
        Path p = new Dijkstra(pathDetailGraph, weighting, TraversalMode.NODE_BASED).calcPath(1, 5);
        assertTrue(p.isFound());

        Map<String, List<PathDetail>> details = PathDetailsFromEdges.calcDetails(p, carManager, weighting,
                Arrays.asList(STREET_NAME), new PathDetailsBuilderFactory(), 0);
        assertTrue(details.size() == 1);

        List<PathDetail> streetNameDetails = details.get(STREET_NAME);
        assertTrue(details.size() == 1);

        assertEquals(4, streetNameDetails.size());
        assertEquals("1-2", streetNameDetails.get(0).getValue());
        assertEquals("2-3", streetNameDetails.get(1).getValue());
        assertEquals("3-4", streetNameDetails.get(2).getValue());
        assertEquals("4-5", streetNameDetails.get(3).getValue());

        assertEquals(0, streetNameDetails.get(0).getFirst());
        assertEquals(1, streetNameDetails.get(1).getFirst());
        assertEquals(2, streetNameDetails.get(2).getFirst());
        assertEquals(3, streetNameDetails.get(3).getFirst());
        assertEquals(4, streetNameDetails.get(3).getLast());
    }

    @Test
    public void testCalcEdgeIdDetails() {
        ShortestWeighting weighting = new ShortestWeighting(carAccessEnc, carAvSpeedEnc);
        Path p = new Dijkstra(pathDetailGraph, weighting, TraversalMode.NODE_BASED).calcPath(1, 5);
        assertTrue(p.isFound());

        Map<String, List<PathDetail>> details = PathDetailsFromEdges.calcDetails(p, carManager, weighting,
                Arrays.asList(EDGE_ID), new PathDetailsBuilderFactory(), 0);
        assertTrue(details.size() == 1);

        List<PathDetail> edgeIdDetails = details.get(EDGE_ID);
        assertEquals(4, edgeIdDetails.size());
        assertEquals(0, edgeIdDetails.get(0).getValue());
        // This is out of order because we don't create the edges in order
        assertEquals(2, edgeIdDetails.get(1).getValue());
        assertEquals(3, edgeIdDetails.get(2).getValue());
        assertEquals(1, edgeIdDetails.get(3).getValue());

        assertEquals(0, edgeIdDetails.get(0).getFirst());
        assertEquals(1, edgeIdDetails.get(1).getFirst());
        assertEquals(2, edgeIdDetails.get(2).getFirst());
        assertEquals(3, edgeIdDetails.get(3).getFirst());
        assertEquals(4, edgeIdDetails.get(3).getLast());
    }

    @Test
    public void testCalcEdgeKeyDetailsForward() {
        ShortestWeighting weighting = new ShortestWeighting(carAccessEnc, carAvSpeedEnc);
        Path p = new Dijkstra(pathDetailGraph, weighting, TraversalMode.NODE_BASED).calcPath(1, 5);
        assertTrue(p.isFound());

        Map<String, List<PathDetail>> details = PathDetailsFromEdges.calcDetails(p, carManager, weighting,
                Arrays.asList(EDGE_KEY), new PathDetailsBuilderFactory(), 0);
        List<PathDetail> edgeKeyDetails = details.get(EDGE_KEY);

        assertEquals(4, edgeKeyDetails.size());
        assertEquals(0, edgeKeyDetails.get(0).getValue());
        assertEquals(4, edgeKeyDetails.get(1).getValue());
        assertEquals(6, edgeKeyDetails.get(2).getValue());
        assertEquals(2, edgeKeyDetails.get(3).getValue());
    }

    @Test
    public void testCalcEdgeKeyDetailsBackward() {
        ShortestWeighting weighting = new ShortestWeighting(carAccessEnc, carAvSpeedEnc);
        Path p = new Dijkstra(pathDetailGraph, weighting, TraversalMode.NODE_BASED).calcPath(5, 1);
        assertTrue(p.isFound());

        Map<String, List<PathDetail>> details = PathDetailsFromEdges.calcDetails(p, carManager, weighting,
                Arrays.asList(EDGE_KEY), new PathDetailsBuilderFactory(), 0);
        List<PathDetail> edgeKeyDetails = details.get(EDGE_KEY);

        assertEquals(4, edgeKeyDetails.size());
        assertEquals(3, edgeKeyDetails.get(0).getValue());
        assertEquals(7, edgeKeyDetails.get(1).getValue());
        assertEquals(5, edgeKeyDetails.get(2).getValue());
        assertEquals(1, edgeKeyDetails.get(3).getValue());
    }

    @Test
    public void testCalcTimeDetails() {
        ShortestWeighting weighting = new ShortestWeighting(carAccessEnc, carAvSpeedEnc);
        Path p = new Dijkstra(pathDetailGraph, weighting, TraversalMode.NODE_BASED).calcPath(1, 5);
        assertTrue(p.isFound());

        Map<String, List<PathDetail>> details = PathDetailsFromEdges.calcDetails(p, carManager, weighting,
                Arrays.asList(TIME), new PathDetailsBuilderFactory(), 0);
        assertTrue(details.size() == 1);

        List<PathDetail> timeDetails = details.get(TIME);
        assertEquals(4, timeDetails.size());
        assertEquals(400L, timeDetails.get(0).getValue());
        assertEquals(200L, timeDetails.get(1).getValue());
        assertEquals(3600L, timeDetails.get(2).getValue());
        assertEquals(400L, timeDetails.get(3).getValue());

        assertEquals(0, timeDetails.get(0).getFirst());
        assertEquals(1, timeDetails.get(1).getFirst());
        assertEquals(2, timeDetails.get(2).getFirst());
        assertEquals(3, timeDetails.get(3).getFirst());
        assertEquals(4, timeDetails.get(3).getLast());
    }

    @Test
    public void testCalcDistanceDetails() {
        ShortestWeighting weighting = new ShortestWeighting(carAccessEnc, carAvSpeedEnc);
        Path p = new Dijkstra(pathDetailGraph, weighting, TraversalMode.NODE_BASED).calcPath(1, 5);
        assertTrue(p.isFound());

        Map<String, List<PathDetail>> details = PathDetailsFromEdges.calcDetails(p, carManager, weighting,
                Arrays.asList(DISTANCE), new PathDetailsBuilderFactory(), 0);
        assertTrue(details.size() == 1);

        List<PathDetail> distanceDetails = details.get(DISTANCE);
        assertEquals(5D, distanceDetails.get(0).getValue());
        assertEquals(5D, distanceDetails.get(1).getValue());
        assertEquals(10D, distanceDetails.get(2).getValue());
        assertEquals(5D, distanceDetails.get(3).getValue());
    }

    /**
     * case with one edge being not an exit
     */
    @Test
    public void testCalcInstructionsRoundabout2() {
        roundaboutGraph.inverse3to6();
        ShortestWeighting weighting = new ShortestWeighting(mixedCarAccessEnc, mixedCarSpeedEnc);
        Path p = new Dijkstra(roundaboutGraph.g, weighting, TraversalMode.NODE_BASED)
                .calcPath(1, 8);
        assertTrue(p.isFound());
        InstructionList wayList = InstructionsFromEdges.calcInstructions(p, p.graph, weighting, mixedEncodingManager, tr);
        List<String> tmpList = getTurnDescriptions(wayList);
        assertEquals(Arrays.asList("continue onto MainStreet 1 2",
                        "At roundabout, take exit 2 onto 5-8",
                        "arrive at destination"),
                tmpList);
        // Test Radian
        double delta = roundaboutGraph.getAngle(1, 2, 5, 8);
        RoundaboutInstruction instr = (RoundaboutInstruction) wayList.get(1);
        assertEquals(delta, instr.getTurnAngle(), 0.01);
        roundaboutGraph.inverse3to6();
    }

    @Test
    public void testCalcInstructionsRoundaboutIssue353() {
        final BaseGraph graph = new BaseGraph.Builder(carManager).create();
        final NodeAccess na = graph.getNodeAccess();

        //
        //          8
        //           \
        //            5
        //           /  \
        //  11- 1 - 2    4 - 7
        //      |     \  /
        //      10 -9 -3
        //       \    |
        //        --- 6
        na.setNode(1, 52.514, 13.348);
        na.setNode(2, 52.514, 13.349);
        na.setNode(3, 52.5135, 13.35);
        na.setNode(4, 52.514, 13.351);
        na.setNode(5, 52.5145, 13.351);
        na.setNode(6, 52.513, 13.35);
        na.setNode(7, 52.514, 13.352);
        na.setNode(8, 52.515, 13.351);

        na.setNode(9, 52.5135, 13.349);
        na.setNode(10, 52.5135, 13.348);
        na.setNode(11, 52.514, 13.347);

        GHUtility.setSpeed(60, true, false, carAccessEnc, carAvSpeedEnc, graph.edge(2, 1).setDistance(5)).setKeyValues(createKV("name", "MainStreet 2 1"));
        GHUtility.setSpeed(60, true, false, carAccessEnc, carAvSpeedEnc, graph.edge(1, 11).setDistance(5)).setKeyValues(createKV("name", "MainStreet 1 11"));

        // roundabout
        EdgeIteratorState tmpEdge;
        tmpEdge = GHUtility.setSpeed(60, true, false, carAccessEnc, carAvSpeedEnc, graph.edge(3, 9).setDistance(2)).setKeyValues(createKV("name", "3-9"));
        BooleanEncodedValue carManagerRoundabout = carManager.getBooleanEncodedValue(Roundabout.KEY);
        tmpEdge.set(carManagerRoundabout, true);
        tmpEdge = GHUtility.setSpeed(60, true, false, carAccessEnc, carAvSpeedEnc, graph.edge(9, 10).setDistance(2)).setKeyValues(createKV("name", "9-10"));
        tmpEdge.set(carManagerRoundabout, true);
        tmpEdge = GHUtility.setSpeed(60, true, false, carAccessEnc, carAvSpeedEnc, graph.edge(6, 10).setDistance(2)).setKeyValues(createKV("name", "6-10"));
        tmpEdge.set(carManagerRoundabout, true);
        tmpEdge = GHUtility.setSpeed(60, true, false, carAccessEnc, carAvSpeedEnc, graph.edge(10, 1).setDistance(2)).setKeyValues(createKV("name", "10-1"));
        tmpEdge.set(carManagerRoundabout, true);
        tmpEdge = GHUtility.setSpeed(60, true, false, carAccessEnc, carAvSpeedEnc, graph.edge(3, 2).setDistance(5)).setKeyValues(createKV("name", "2-3"));
        tmpEdge.set(carManagerRoundabout, true);
        tmpEdge = GHUtility.setSpeed(60, true, false, carAccessEnc, carAvSpeedEnc, graph.edge(4, 3).setDistance(5)).setKeyValues(createKV("name", "3-4"));
        tmpEdge.set(carManagerRoundabout, true);
        tmpEdge = GHUtility.setSpeed(60, true, false, carAccessEnc, carAvSpeedEnc, graph.edge(5, 4).setDistance(5)).setKeyValues(createKV("name", "4-5"));
        tmpEdge.set(carManagerRoundabout, true);
        tmpEdge = GHUtility.setSpeed(60, true, false, carAccessEnc, carAvSpeedEnc, graph.edge(2, 5).setDistance(5)).setKeyValues(createKV("name", "5-2"));
        tmpEdge.set(carManagerRoundabout, true);

        GHUtility.setSpeed(60, true, true, carAccessEnc, carAvSpeedEnc, graph.edge(4, 7).setDistance(5)).setKeyValues(createKV("name", "MainStreet 4 7"));
        GHUtility.setSpeed(60, true, true, carAccessEnc, carAvSpeedEnc, graph.edge(5, 8).setDistance(5)).setKeyValues(createKV("name", "5-8"));
        GHUtility.setSpeed(60, true, true, carAccessEnc, carAvSpeedEnc, graph.edge(3, 6).setDistance(5)).setKeyValues(createKV("name", "3-6"));

        ShortestWeighting weighting = new ShortestWeighting(carAccessEnc, carAvSpeedEnc);
        Path p = new Dijkstra(graph, weighting, TraversalMode.NODE_BASED)
                .calcPath(6, 11);
        assertTrue(p.isFound());
        InstructionList wayList = InstructionsFromEdges.calcInstructions(p, p.graph, weighting, carManager, tr);
        List<String> tmpList = getTurnDescriptions(wayList);
        assertEquals(Arrays.asList("At roundabout, take exit 1 onto MainStreet 1 11",
                        "arrive at destination"),
                tmpList);
    }

    @Test
    public void testCalcInstructionsRoundaboutClockwise() {
        roundaboutGraph.setRoundabout(true);
        ShortestWeighting weighting = new ShortestWeighting(mixedCarAccessEnc, mixedCarSpeedEnc);
        Path p = new Dijkstra(roundaboutGraph.g, weighting, TraversalMode.NODE_BASED)
                .calcPath(1, 8);
        assertTrue(p.isFound());
        InstructionList wayList = InstructionsFromEdges.calcInstructions(p, p.graph, weighting, mixedEncodingManager, tr);
        List<String> tmpList = getTurnDescriptions(wayList);
        assertEquals(Arrays.asList("continue onto MainStreet 1 2",
                        "At roundabout, take exit 1 onto 5-8",
                        "arrive at destination"),
                tmpList);
        // Test Radian
        double delta = roundaboutGraph.getAngle(1, 2, 5, 8);
        RoundaboutInstruction instr = (RoundaboutInstruction) wayList.get(1);
        assertEquals(delta, instr.getTurnAngle(), 0.01);
    }

    @Test
    public void testCalcInstructionsIgnoreContinue() {
        // Follow a couple of straight edges, including a name change
        ShortestWeighting weighting = new ShortestWeighting(mixedCarAccessEnc, mixedCarSpeedEnc);
        Path p = new Dijkstra(roundaboutGraph.g, weighting, TraversalMode.NODE_BASED)
                .calcPath(4, 11);
        assertTrue(p.isFound());
        InstructionList wayList = InstructionsFromEdges.calcInstructions(p, p.graph, weighting, mixedEncodingManager, tr);

        // Contain only start and finish instruction, no CONTINUE
        assertEquals(2, wayList.size());
    }

    @Test
    public void testCalcInstructionsIgnoreTurnIfNoAlternative() {
        // The street turns left, but there is not turn
        ShortestWeighting weighting = new ShortestWeighting(mixedCarAccessEnc, mixedCarSpeedEnc);
        Path p = new Dijkstra(roundaboutGraph.g, weighting, TraversalMode.NODE_BASED)
                .calcPath(10, 12);
        assertTrue(p.isFound());
        InstructionList wayList = InstructionsFromEdges.calcInstructions(p, p.graph, weighting, mixedEncodingManager, tr);

        // Contain only start and finish instruction
        assertEquals(2, wayList.size());
    }

    @Test
    public void testCalcInstructionForForkWithSameName() {
        final BaseGraph graph = new BaseGraph.Builder(carManager).create();
        final NodeAccess na = graph.getNodeAccess();

        // Actual example: point=48.982618%2C13.122021&point=48.982336%2C13.121002
        // 1-2 & 2-4 have the same Street name, but other from that, it would be hard to see the difference
        // We have to enforce a turn instruction here
        //      3
        //        \
        //          2   --  1
        //        /
        //      4
        na.setNode(1, 48.982618, 13.122021);
        na.setNode(2, 48.982565, 13.121597);
        na.setNode(3, 48.982611, 13.121012);
        na.setNode(4, 48.982336, 13.121002);

        GHUtility.setSpeed(60, true, true, carAccessEnc, carAvSpeedEnc, graph.edge(1, 2).setDistance(5)).setKeyValues(createKV("name", "Regener Weg"));
        GHUtility.setSpeed(60, true, true, carAccessEnc, carAvSpeedEnc, graph.edge(2, 4).setDistance(5)).setKeyValues(createKV("name", "Regener Weg"));
        GHUtility.setSpeed(60, true, true, carAccessEnc, carAvSpeedEnc, graph.edge(2, 3).setDistance(5));

        ShortestWeighting weighting = new ShortestWeighting(carAccessEnc, carAvSpeedEnc);
        Path p = new Dijkstra(graph, weighting, TraversalMode.NODE_BASED)
                .calcPath(1, 4);
        assertTrue(p.isFound());
        InstructionList wayList = InstructionsFromEdges.calcInstructions(p, p.graph, weighting, carManager, tr);

        assertEquals(3, wayList.size());
        assertEquals(-7, wayList.get(1).getSign());
    }

    @Test
    public void testCalcInstructionForMotorwayFork() {
        final BaseGraph graph = new BaseGraph.Builder(carManager).create();
        final NodeAccess na = graph.getNodeAccess();

        // Actual example: point=48.909071%2C8.647136&point=48.908789%2C8.649244
        // 1-2 & 2-4 is a motorway, 2-3 is a motorway_link
        // We should skip the instruction here
        //      1 ---- 2 ---- 4
        //              \
        //               3
        na.setNode(1, 48.909071, 8.647136);
        na.setNode(2, 48.908962, 8.647978);
        na.setNode(3, 48.908867, 8.648155);
        na.setNode(4, 48.908789, 8.649244);

        EnumEncodedValue<RoadClass> roadClassEnc = carManager.getEnumEncodedValue(RoadClass.KEY, RoadClass.class);
        BooleanEncodedValue roadClassLinkEnc = carManager.getBooleanEncodedValue(RoadClassLink.KEY);

        GHUtility.setSpeed(60, true, true, carAccessEnc, carAvSpeedEnc, graph.edge(1, 2).setDistance(5)).setKeyValues(createKV("name", "A 8")).set(roadClassEnc, RoadClass.MOTORWAY).set(roadClassLinkEnc, false);
        GHUtility.setSpeed(60, true, true, carAccessEnc, carAvSpeedEnc, graph.edge(2, 4).setDistance(5)).setKeyValues(createKV("name", "A 8")).set(roadClassEnc, RoadClass.MOTORWAY).set(roadClassLinkEnc, false);
        GHUtility.setSpeed(60, true, true, carAccessEnc, carAvSpeedEnc, graph.edge(2, 3).setDistance(5)).set(roadClassEnc, RoadClass.MOTORWAY).set(roadClassLinkEnc, true);

        ShortestWeighting weighting = new ShortestWeighting(carAccessEnc, carAvSpeedEnc);
        Path p = new Dijkstra(graph, weighting, TraversalMode.NODE_BASED)
                .calcPath(1, 4);
        assertTrue(p.isFound());
        InstructionList wayList = InstructionsFromEdges.calcInstructions(p, p.graph, weighting, carManager, tr);

        assertEquals(2, wayList.size());
    }

    @Test
    public void testCalcInstructionsEnterMotorway() {
        final BaseGraph graph = new BaseGraph.Builder(carManager).create();
        final NodeAccess na = graph.getNodeAccess();

        // Actual example: point=48.630533%2C9.459416&point=48.630544%2C9.459829
        // 1 -2 -3 is a motorway and tagged as oneway
        //   1 ->- 2 ->- 3
        //        /
        //      4
        na.setNode(1, 48.630647, 9.459041);
        na.setNode(2, 48.630586, 9.459604);
        na.setNode(3, 48.630558, 9.459851);
        na.setNode(4, 48.63054, 9.459406);

        GHUtility.setSpeed(60, true, false, carAccessEnc, carAvSpeedEnc, graph.edge(1, 2).setDistance(5)).setKeyValues(createKV("name", "A 8"));
        GHUtility.setSpeed(60, true, false, carAccessEnc, carAvSpeedEnc, graph.edge(2, 3).setDistance(5)).setKeyValues(createKV("name", "A 8"));
        GHUtility.setSpeed(60, true, false, carAccessEnc, carAvSpeedEnc, graph.edge(4, 2).setDistance(5)).setKeyValues(createKV("name", "A 8"));

        ShortestWeighting weighting = new ShortestWeighting(carAccessEnc, carAvSpeedEnc);
        Path p = new Dijkstra(graph, weighting, TraversalMode.NODE_BASED)
                .calcPath(4, 3);
        assertTrue(p.isFound());
        InstructionList wayList = InstructionsFromEdges.calcInstructions(p, p.graph, weighting, carManager, tr);

        // no turn instruction for entering the highway
        assertEquals(2, wayList.size());
    }

    @Test
    public void testCalcInstructionsMotorwayJunction() {
        final BaseGraph g = new BaseGraph.Builder(carManager).create();
        final NodeAccess na = g.getNodeAccess();

        // Actual example: point=48.70672%2C9.164266&point=48.706805%2C9.162995
        // A typical motorway junction, when following 1-2-3, there should be a keep right at 2
        //             -- 4
        //          /
        //   1 -- 2 -- 3
        na.setNode(1, 48.70672, 9.164266);
        na.setNode(2, 48.706741, 9.163719);
        na.setNode(3, 48.706805, 9.162995);
        na.setNode(4, 48.706705, 9.16329);

        GHUtility.setSpeed(60, true, false, carAccessEnc, carAvSpeedEnc, g.edge(1, 2).setDistance(5)).setKeyValues(createKV("name", "A 8"));
        GHUtility.setSpeed(60, true, false, carAccessEnc, carAvSpeedEnc, g.edge(2, 3).setDistance(5)).setKeyValues(createKV("name", "A 8"));
        GHUtility.setSpeed(60, true, false, carAccessEnc, carAvSpeedEnc, g.edge(2, 4).setDistance(5)).setKeyValues(createKV("name", "A 8"));

        ShortestWeighting weighting = new ShortestWeighting(carAccessEnc, carAvSpeedEnc);
        Path p = new Dijkstra(g, weighting, TraversalMode.NODE_BASED)
                .calcPath(1, 3);
        assertTrue(p.isFound());
        InstructionList wayList = InstructionsFromEdges.calcInstructions(p, p.graph, weighting, carManager, tr);

        assertEquals(3, wayList.size());
        // TODO this should be a keep_right
        assertEquals(0, wayList.get(1).getSign());
    }

    @Test
    public void testCalcInstructionsOntoOneway() {
        final BaseGraph g = new BaseGraph.Builder(carManager).create();
        final NodeAccess na = g.getNodeAccess();

        // Actual example: point=-33.824566%2C151.187834&point=-33.82441%2C151.188231
        // 1 -2 -3 is a oneway
        //   1 ->- 2 ->- 3
        //         |
        //         4
        na.setNode(1, -33.824245, 151.187866);
        na.setNode(2, -33.824335, 151.188017);
        na.setNode(3, -33.824415, 151.188177);
        na.setNode(4, -33.824437, 151.187925);

        GHUtility.setSpeed(60, true, false, carAccessEnc, carAvSpeedEnc, g.edge(1, 2).setDistance(5)).setKeyValues(createKV("name", "Pacific Highway"));
        GHUtility.setSpeed(60, true, false, carAccessEnc, carAvSpeedEnc, g.edge(2, 3).setDistance(5)).setKeyValues(createKV("name", "Pacific Highway"));
        GHUtility.setSpeed(60, true, true, carAccessEnc, carAvSpeedEnc, g.edge(4, 2).setDistance(5)).setKeyValues(createKV("name", "Greenwich Road"));

        ShortestWeighting weighting = new ShortestWeighting(carAccessEnc, carAvSpeedEnc);
        Path p = new Dijkstra(g, weighting, TraversalMode.NODE_BASED)
                .calcPath(4, 3);
        assertTrue(p.isFound());
        InstructionList wayList = InstructionsFromEdges.calcInstructions(p, p.graph, weighting, carManager, tr);

        assertEquals(3, wayList.size());
        assertEquals(2, wayList.get(1).getSign());
    }

    @Test
    public void testCalcInstructionIssue1047() {
        final BaseGraph g = new BaseGraph.Builder(carManager).create();
        final NodeAccess na = g.getNodeAccess();

        // Actual example: point=51.367105%2C14.491246&point=51.369048%2C14.483092
        // 1-2 & 2-3 is a road that is turning right, 2-4 is a that is branching off.
        // When driving 1-2-4, we should create an instruction notifying the user to continue straight instead of turning and following the road
        // When driving 1-2-3, we should create an instruction as well
        //
        //      1 ---- 2 ---- 4
        //             |
        //             3
        na.setNode(1, 51.367544, 14.488209);
        na.setNode(2, 51.368046, 14.486525);
        na.setNode(3, 51.36875, 14.487019);
        na.setNode(4, 51.368428, 14.485173);

        EnumEncodedValue<RoadClass> roadClassEnc = carManager.getEnumEncodedValue(RoadClass.KEY, RoadClass.class);
        BooleanEncodedValue roadClassLinkEnc = carManager.getBooleanEncodedValue(RoadClassLink.KEY);

        GHUtility.setSpeed(60, true, true, carAccessEnc, carAvSpeedEnc, g.edge(1, 2).setDistance(5)).setKeyValues(createKV("name", "B 156")).set(roadClassEnc, RoadClass.PRIMARY).set(roadClassLinkEnc, false);
        GHUtility.setSpeed(60, true, true, carAccessEnc, carAvSpeedEnc, g.edge(2, 4).setDistance(5)).setKeyValues(createKV("name", "S 108")).set(roadClassEnc, RoadClass.SECONDARY).set(roadClassLinkEnc, false);
        GHUtility.setSpeed(60, true, true, carAccessEnc, carAvSpeedEnc, g.edge(2, 3).setDistance(5)).setKeyValues(createKV("name", "B 156")).set(roadClassEnc, RoadClass.PRIMARY).set(roadClassLinkEnc, false);

        ShortestWeighting weighting = new ShortestWeighting(carAccessEnc, carAvSpeedEnc);
        Path p = new Dijkstra(g, weighting, TraversalMode.NODE_BASED)
                .calcPath(1, 4);
        assertTrue(p.isFound());
        InstructionList wayList = InstructionsFromEdges.calcInstructions(p, p.graph, weighting, carManager, tr);

        assertEquals(3, wayList.size());

        p = new Dijkstra(g, weighting, TraversalMode.NODE_BASED)
                .calcPath(1, 3);
        assertTrue(p.isFound());
        wayList = InstructionsFromEdges.calcInstructions(p, p.graph, weighting, carManager, tr);

        assertEquals(3, wayList.size());
    }

    @Test
    public void testCalcInstructionContinueLeavingStreet() {
        final BaseGraph g = new BaseGraph.Builder(carManager).create();
        final NodeAccess na = g.getNodeAccess();

        // When leaving the current street via a Continue, we should show it
        //       3
        //        \
        //     4 - 2   --  1
        na.setNode(1, 48.982618, 13.122021);
        na.setNode(2, 48.982565, 13.121597);
        na.setNode(3, 48.982611, 13.121012);
        na.setNode(4, 48.982565, 13.121002);

        GHUtility.setSpeed(60, true, true, carAccessEnc, carAvSpeedEnc, g.edge(1, 2).setDistance(5)).setKeyValues(createKV("name", "Regener Weg"));
        GHUtility.setSpeed(60, true, true, carAccessEnc, carAvSpeedEnc, g.edge(2, 4).setDistance(5));
        GHUtility.setSpeed(60, true, true, carAccessEnc, carAvSpeedEnc, g.edge(2, 3).setDistance(5)).setKeyValues(createKV("name", "Regener Weg"));

        ShortestWeighting weighting = new ShortestWeighting(carAccessEnc, carAvSpeedEnc);
        Path p = new Dijkstra(g, weighting, TraversalMode.NODE_BASED)
                .calcPath(1, 4);
        assertTrue(p.isFound());
        InstructionList wayList = InstructionsFromEdges.calcInstructions(p, p.graph, weighting, carManager, tr);

        assertEquals(3, wayList.size());
        assertEquals(-7, wayList.get(1).getSign());
    }

    @Test
    public void testCalcInstructionSlightTurn() {
        final BaseGraph g = new BaseGraph.Builder(carManager).create();
        final NodeAccess na = g.getNodeAccess();

        // Real Situation: point=48.411927%2C15.599197&point=48.412094%2C15.598816
        // When reaching this Crossing, you cannot know if you should turn left or right
        // Google Maps and Bing show a turn, OSRM does not
        //  1 ---2--- 3
        //       \
        //        4
        na.setNode(1, 48.412094, 15.598816);
        na.setNode(2, 48.412055, 15.599068);
        na.setNode(3, 48.412034, 15.599411);
        na.setNode(4, 48.411927, 15.599197);

        GHUtility.setSpeed(60, true, true, carAccessEnc, carAvSpeedEnc, g.edge(1, 2).setDistance(5)).setKeyValues(createKV("name", "Stöhrgasse"));
        GHUtility.setSpeed(60, true, true, carAccessEnc, carAvSpeedEnc, g.edge(2, 3).setDistance(5));
        GHUtility.setSpeed(60, true, true, carAccessEnc, carAvSpeedEnc, g.edge(2, 4).setDistance(5)).setKeyValues(createKV("name", "Stöhrgasse"));

        ShortestWeighting weighting = new ShortestWeighting(carAccessEnc, carAvSpeedEnc);
        Path p = new Dijkstra(g, weighting, TraversalMode.NODE_BASED)
                .calcPath(4, 1);
        assertTrue(p.isFound());
        InstructionList wayList = InstructionsFromEdges.calcInstructions(p, p.graph, weighting, carManager, tr);

        assertEquals(3, wayList.size());
        assertEquals(-1, wayList.get(1).getSign());
    }

    @Test
    public void testUTurnLeft() {
        final BaseGraph g = new BaseGraph.Builder(carManager).create();
        final NodeAccess na = g.getNodeAccess();

        // Real Situation: point=48.402116%2C9.994367&point=48.402198%2C9.99507
        //       7
        //       |
        //  4----5----6
        //       |
        //  1----2----3
        na.setNode(1, 48.402116, 9.994367);
        na.setNode(2, 48.402198, 9.99507);
        na.setNode(3, 48.402344, 9.996266);
        na.setNode(4, 48.402191, 9.994351);
        na.setNode(5, 48.402298, 9.995053);
        na.setNode(6, 48.402422, 9.996067);
        na.setNode(7, 48.402604, 9.994962);

        GHUtility.setSpeed(60, 0, carAccessEnc, carAvSpeedEnc,
                g.edge(1, 2).setDistance(5).setKeyValues(createKV("name", "Olgastraße")),
                g.edge(2, 3).setDistance(5).setKeyValues(createKV("name", "Olgastraße")),
                g.edge(6, 5).setDistance(5).setKeyValues(createKV("name", "Olgastraße")),
                g.edge(5, 4).setDistance(5).setKeyValues(createKV("name", "Olgastraße")));
        GHUtility.setSpeed(60, 60, carAccessEnc, carAvSpeedEnc,
                g.edge(2, 5).setDistance(5).setKeyValues(createKV("name", "Neithardtstraße")),
                g.edge(5, 7).setDistance(5).setKeyValues(createKV("name", "Neithardtstraße")));

        ShortestWeighting weighting = new ShortestWeighting(carAccessEnc, carAvSpeedEnc);
        Path p = new Dijkstra(g, weighting, TraversalMode.NODE_BASED)
                .calcPath(1, 4);
        assertTrue(p.isFound());
        InstructionList wayList = InstructionsFromEdges.calcInstructions(p, p.graph, weighting, carManager, tr);

        assertEquals(3, wayList.size());
        assertEquals(Instruction.U_TURN_LEFT, wayList.get(1).getSign());
    }

    @Test
    public void testUTurnRight() {
        final BaseGraph g = new BaseGraph.Builder(carManager).create();
        final NodeAccess na = g.getNodeAccess();

        // Real Situation: point=-33.885758,151.181472&point=-33.885692,151.181445
        //       7
        //       |
        //  4----5----6
        //       |
        //  3----2----1
        na.setNode(1, -33.885758, 151.181472);
        na.setNode(2, -33.885852, 151.180968);
        na.setNode(3, -33.885968, 151.180501);
        na.setNode(4, -33.885883, 151.180442);
        na.setNode(5, -33.885772, 151.180941);
        na.setNode(6, -33.885692, 151.181445);
        na.setNode(7, -33.885692, 151.181445);

        GHUtility.setSpeed(60, 0, carAccessEnc, carAvSpeedEnc,
                g.edge(1, 2).setDistance(5).setKeyValues(createKV("name", "Parramatta Road")),
                g.edge(2, 3).setDistance(5).setKeyValues(createKV("name", "Parramatta Road")),
                g.edge(4, 5).setDistance(5).setKeyValues(createKV("name", "Parramatta Road")),
                g.edge(5, 6).setDistance(5).setKeyValues(createKV("name", "Parramatta Road")));
        GHUtility.setSpeed(60, 60, carAccessEnc, carAvSpeedEnc,
                g.edge(2, 5).setDistance(5).setKeyValues(createKV("name", "Larkin Street")),
                g.edge(5, 7).setDistance(5).setKeyValues(createKV("name", "Larkin Street")));

        ShortestWeighting weighting = new ShortestWeighting(carAccessEnc, carAvSpeedEnc);
        Path p = new Dijkstra(g, weighting, TraversalMode.NODE_BASED)
                .calcPath(1, 6);
        assertTrue(p.isFound());
        InstructionList wayList = InstructionsFromEdges.calcInstructions(p, p.graph, weighting, carManager, tr);

        assertEquals(3, wayList.size());
        assertEquals(Instruction.U_TURN_RIGHT, wayList.get(1).getSign());
    }

    @Test
    public void testCalcInstructionsForTurn() {
        // The street turns left, but there is not turn
        ShortestWeighting weighting = new ShortestWeighting(mixedCarAccessEnc, mixedCarSpeedEnc);
        Path p = new Dijkstra(roundaboutGraph.g, weighting, TraversalMode.NODE_BASED)
                .calcPath(11, 13);
        assertTrue(p.isFound());
        InstructionList wayList = InstructionsFromEdges.calcInstructions(p, p.graph, weighting, mixedEncodingManager, tr);

        // Contain start, turn, and finish instruction
        assertEquals(3, wayList.size());
        // Assert turn right
        assertEquals(2, wayList.get(1).getSign());
    }

    @Test
    public void testCalcInstructionsForSlightTurnWithOtherSlightTurn() {
        // Test for a fork with two slight turns. Since there are two slight turns, show the turn instruction
        ShortestWeighting weighting = new ShortestWeighting(mixedCarAccessEnc, mixedCarSpeedEnc);
        Path p = new Dijkstra(roundaboutGraph.g, weighting, TraversalMode.NODE_BASED)
                .calcPath(12, 16);
        assertTrue(p.isFound());
        InstructionList wayList = InstructionsFromEdges.calcInstructions(p, p.graph, weighting, mixedEncodingManager, tr);

        // Contain start, turn, and finish instruction
        assertEquals(3, wayList.size());
        // Assert turn right
        assertEquals(7, wayList.get(1).getSign());
    }

    @Test
    public void testCalcInstructionsForSlightTurnOntoDifferentStreet() {
        final BaseGraph g = new BaseGraph.Builder(carManager).create();
        final NodeAccess na = g.getNodeAccess();

        // Actual example: point=48.76445%2C8.679054&point=48.764152%2C8.678722
        //      1
        //     /
        // 2 - 3 - 4
        //
        na.setNode(1, 48.76423, 8.679103);
        na.setNode(2, 48.76417, 8.678647);
        na.setNode(3, 48.764149, 8.678926);
        na.setNode(4, 48.764085, 8.679183);

        GHUtility.setSpeed(60, true, true, carAccessEnc, carAvSpeedEnc, g.edge(1, 3).setDistance(5)).setKeyValues(createKV("name", "Talstraße, K 4313"));
        GHUtility.setSpeed(60, true, true, carAccessEnc, carAvSpeedEnc, g.edge(2, 3).setDistance(5)).setKeyValues(createKV("name", "Calmbacher Straße, K 4312"));
        GHUtility.setSpeed(60, true, true, carAccessEnc, carAvSpeedEnc, g.edge(3, 4).setDistance(5)).setKeyValues(createKV("name", "Calmbacher Straße, K 4312"));

        ShortestWeighting weighting = new ShortestWeighting(carAccessEnc, carAvSpeedEnc);
        Path p = new Dijkstra(g, weighting, TraversalMode.NODE_BASED)
                .calcPath(1, 2);
        assertTrue(p.isFound());
        InstructionList wayList = InstructionsFromEdges.calcInstructions(p, p.graph, weighting, carManager, tr);

        assertEquals(3, wayList.size());
        assertEquals(Instruction.TURN_SLIGHT_RIGHT, wayList.get(1).getSign());
    }

    @Test
    public void testIgnoreInstructionsForSlightTurnWithOtherTurn() {
        // Test for a fork with one sligh turn and one actual turn. We are going along the slight turn. No turn instruction needed in this case
        ShortestWeighting weighting = new ShortestWeighting(mixedCarAccessEnc, mixedCarSpeedEnc);
        Path p = new Dijkstra(roundaboutGraph.g, weighting, TraversalMode.NODE_BASED)
                .calcPath(16, 19);
        assertTrue(p.isFound());
        InstructionList wayList = InstructionsFromEdges.calcInstructions(p, p.graph, weighting, mixedEncodingManager, tr);

        // Contain start, and finish instruction
        assertEquals(2, wayList.size());
    }

    List<String> getTurnDescriptions(InstructionList instructionJson) {
        List<String> list = new ArrayList<>();
        for (Instruction instruction : instructionJson) {
            list.add(instruction.getTurnDescription(tr));
        }
        return list;
    }

    private Graph generatePathDetailsGraph() {
        final BaseGraph graph = new BaseGraph.Builder(carManager).create();
        final NodeAccess na = graph.getNodeAccess();

        na.setNode(1, 52.514, 13.348);
        na.setNode(2, 52.514, 13.349);
        na.setNode(3, 52.514, 13.350);
        na.setNode(4, 52.515, 13.349);
        na.setNode(5, 52.516, 13.3452);
        na.setNode(6, 52.516, 13.344);

        GHUtility.setSpeed(45, true, true, carAccessEnc, carAvSpeedEnc, graph.edge(1, 2).setDistance(5)).setKeyValues(createKV("name", "1-2"));
        GHUtility.setSpeed(45, true, true, carAccessEnc, carAvSpeedEnc, graph.edge(4, 5).setDistance(5)).setKeyValues(createKV("name", "4-5"));
        GHUtility.setSpeed(90, true, true, carAccessEnc, carAvSpeedEnc, graph.edge(2, 3).setDistance(5)).setKeyValues(createKV("name", "2-3"));
        GHUtility.setSpeed(9, true, true, carAccessEnc, carAvSpeedEnc, graph.edge(3, 4).setDistance(10)).setKeyValues(createKV("name", "3-4"));
        GHUtility.setSpeed(9, true, true, carAccessEnc, carAvSpeedEnc, graph.edge(5, 6).setDistance(0.001)).setKeyValues(createKV("name", "3-4"));
        return graph;
    }

    private class RoundaboutGraph {
        final BaseGraph g;
        final NodeAccess na;
        final EdgeIteratorState edge3to6, edge3to9;
        boolean clockwise = false;
        List<EdgeIteratorState> roundaboutEdges = new LinkedList<>();

        private RoundaboutGraph() {
            g = new BaseGraph.Builder(mixedEncodingManager).create();
            na = g.getNodeAccess();
            //                                       18
            //      8                 14              |
            //       \                 |      / 16 - 17
            //         5              12 - 13          \-- 19
            //       /  \              |      \ 15
            //  1 - 2    4 - 7 - 10 - 11
            //       \  /
            //        3
            //        | \
            //        6 [ 9 ] edge 9 is turned off in default mode

            na.setNode(1, 52.514, 13.348);
            na.setNode(2, 52.514, 13.349);
            na.setNode(3, 52.5135, 13.35);
            na.setNode(4, 52.514, 13.351);
            na.setNode(5, 52.5145, 13.351);
            na.setNode(6, 52.513, 13.35);
            na.setNode(7, 52.514, 13.352);
            na.setNode(8, 52.515, 13.351);
            na.setNode(9, 52.513, 13.351);
            na.setNode(10, 52.514, 13.353);
            na.setNode(11, 52.514, 13.354);
            na.setNode(12, 52.515, 13.354);
            na.setNode(13, 52.515, 13.355);
            na.setNode(14, 52.516, 13.354);
            na.setNode(15, 52.516, 13.360);
            na.setNode(16, 52.514, 13.360);
            na.setNode(17, 52.514, 13.361);
            na.setNode(18, 52.513, 13.361);
            na.setNode(19, 52.515, 13.368);

            // roundabout
            roundaboutEdges.add(g.edge(3, 2).setDistance(5).setKeyValues(createKV("name", "2-3")));
            roundaboutEdges.add(g.edge(4, 3).setDistance(5).setKeyValues(createKV("name", "3-4")));
            roundaboutEdges.add(g.edge(5, 4).setDistance(5).setKeyValues(createKV("name", "4-5")));
            roundaboutEdges.add(g.edge(2, 5).setDistance(5).setKeyValues(createKV("name", "5-2")));

            List<EdgeIteratorState> bothDir = new ArrayList<>();
            List<EdgeIteratorState> oneDir = new ArrayList<>(roundaboutEdges);

            bothDir.add(g.edge(1, 2).setDistance(5).setKeyValues(createKV("name", "MainStreet 1 2")));
            bothDir.add(g.edge(4, 7).setDistance(5).setKeyValues(createKV("name", "MainStreet 4 7")));
            bothDir.add(g.edge(5, 8).setDistance(5).setKeyValues(createKV("name", "5-8")));

            bothDir.add(edge3to6 = g.edge(3, 6).setDistance(5).setKeyValues(createKV("name", "3-6")));
            oneDir.add(edge3to9 = g.edge(3, 9).setDistance(5).setKeyValues(createKV("name", "3-9")));

            bothDir.add(g.edge(7, 10).setDistance(5));
            bothDir.add(g.edge(10, 11).setDistance(5));
            bothDir.add(g.edge(11, 12).setDistance(5));
            bothDir.add(g.edge(12, 13).setDistance(5));
            bothDir.add(g.edge(12, 14).setDistance(5));
            bothDir.add(g.edge(13, 15).setDistance(5));
            bothDir.add(g.edge(13, 16).setDistance(5));
            bothDir.add(g.edge(16, 17).setDistance(5));
            bothDir.add(g.edge(17, 18).setDistance(5));
            bothDir.add(g.edge(17, 19).setDistance(5));

            for (EdgeIteratorState edge : bothDir) {
                GHUtility.setSpeed(70, 70, mixedCarAccessEnc, mixedCarSpeedEnc, edge);
                GHUtility.setSpeed(7, 7, mixedFootAccessEnc, mixedFootSpeedEnc, edge);
            }
            for (EdgeIteratorState edge : oneDir) {
                GHUtility.setSpeed(70, 0, mixedCarAccessEnc, mixedCarSpeedEnc, edge);
                GHUtility.setSpeed(7, 0, mixedFootAccessEnc, mixedFootSpeedEnc, edge);
            }
            setRoundabout(clockwise);
            inverse3to9();
        }

        public void setRoundabout(boolean clockwise) {
            BooleanEncodedValue mixedRoundabout = mixedEncodingManager.getBooleanEncodedValue(Roundabout.KEY);
            for (EdgeIteratorState edge : roundaboutEdges) {
                edge.set(mixedCarAccessEnc, clockwise).setReverse(mixedCarAccessEnc, !clockwise);
                edge.set(mixedFootAccessEnc, clockwise).setReverse(mixedFootAccessEnc, !clockwise);
                edge.set(mixedRoundabout, true);
            }
            this.clockwise = clockwise;
        }

        public void inverse3to9() {
            edge3to9.set(mixedCarAccessEnc, !edge3to9.get(mixedCarAccessEnc)).setReverse(mixedCarAccessEnc, false);
            edge3to9.set(mixedFootAccessEnc, !edge3to9.get(mixedFootAccessEnc)).setReverse(mixedFootAccessEnc, false);
        }

        public void inverse3to6() {
            edge3to6.set(mixedCarAccessEnc, !edge3to6.get(mixedCarAccessEnc)).setReverse(mixedCarAccessEnc, true);
            edge3to6.set(mixedFootAccessEnc, !edge3to6.get(mixedFootAccessEnc)).setReverse(mixedFootAccessEnc, true);
        }

        private double getAngle(int n1, int n2, int n3, int n4) {
            double inOrientation = AngleCalc.ANGLE_CALC.calcOrientation(na.getLat(n1), na.getLon(n1), na.getLat(n2), na.getLon(n2));
            double outOrientation = AngleCalc.ANGLE_CALC.calcOrientation(na.getLat(n3), na.getLon(n3), na.getLat(n4), na.getLon(n4));
            outOrientation = AngleCalc.ANGLE_CALC.alignOrientation(inOrientation, outOrientation);
            double delta = (inOrientation - outOrientation);
            delta = clockwise ? (Math.PI + delta) : -1 * (Math.PI - delta);
            return delta;
        }
    }

    private static Path extractPath(Graph graph, Weighting weighting, SPTEntry sptEntry) {
        return PathExtractor.extractPath(graph, weighting, sptEntry);
    }

}