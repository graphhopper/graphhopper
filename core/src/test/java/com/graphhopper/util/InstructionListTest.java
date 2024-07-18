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
package com.graphhopper.util;

import com.carrotsearch.hppc.IntArrayList;
import com.graphhopper.json.Statement;
import com.graphhopper.routing.Dijkstra;
import com.graphhopper.routing.InstructionsFromEdges;
import com.graphhopper.routing.Path;
import com.graphhopper.routing.ev.*;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.routing.util.TraversalMode;
import com.graphhopper.routing.weighting.SpeedWeighting;
import com.graphhopper.routing.weighting.TurnCostProvider;
import com.graphhopper.routing.weighting.Weighting;
import com.graphhopper.routing.weighting.custom.CustomModelParser;
import com.graphhopper.search.KVStorage;
import com.graphhopper.storage.BaseGraph;
import com.graphhopper.storage.Graph;
import com.graphhopper.storage.NodeAccess;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.*;

import static com.graphhopper.json.Statement.If;
import static com.graphhopper.search.KVStorage.KValue;
import static com.graphhopper.util.Parameters.Details.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Peter Karich
 */
public class InstructionListTest {
    private static final TranslationMap trMap = TranslationMapTest.SINGLETON;
    private static final Translation usTR = trMap.getWithFallBack(Locale.US);
    private final TraversalMode tMode = TraversalMode.NODE_BASED;
    private EncodingManager carManager;
    private DecimalEncodedValue speedEnc;

    @BeforeEach
    public void setUp() {
        speedEnc = new DecimalEncodedValueImpl("speed", 5, 5, true);
        carManager = EncodingManager.start().add(speedEnc).add(Roundabout.create())
                .add(MaxSpeed.create()).add(RoadClass.create()).add(RoadClassLink.create()).build();
    }

    private static List<String> getTurnDescriptions(InstructionList instructionList) {
        return getTurnDescriptions(instructionList, usTR);
    }

    private static List<String> getTurnDescriptions(InstructionList instructionList, Translation tr) {
        List<String> list = new ArrayList<>();
        for (Instruction instruction : instructionList) {
            list.add(instruction.getTurnDescription(tr));
        }
        return list;
    }

    Graph createTestGraph() {
        BaseGraph g = new BaseGraph.Builder(carManager).create();
        // 0-1-2
        // | | |
        // 3-4-5  9-10
        // | | |  |
        // 6-7-8--*
        NodeAccess na = g.getNodeAccess();
        na.setNode(0, 1.2, 1.0);
        na.setNode(1, 1.2, 1.1);
        na.setNode(2, 1.2, 1.2);
        na.setNode(3, 1.1, 1.0);
        na.setNode(4, 1.1, 1.1);
        na.setNode(5, 1.1, 1.2);
        na.setNode(9, 1.1, 1.3);
        na.setNode(10, 1.1, 1.4);

        na.setNode(6, 1.0, 1.0);
        na.setNode(7, 1.0, 1.1);
        na.setNode(8, 1.0, 1.2);
        g.edge(0, 1).setDistance(10000).set(speedEnc, 60, 60).setKeyValues(Map.of(STREET_NAME, new KValue("0-1")));
        g.edge(1, 2).setDistance(11000).set(speedEnc, 60, 60).setKeyValues(Map.of(STREET_NAME, new KValue("1-2")));

        g.edge(0, 3).setDistance(11000).set(speedEnc, 60, 60);
        g.edge(1, 4).setDistance(10000).set(speedEnc, 60, 60).setKeyValues(Map.of(STREET_NAME, new KValue("1-4")));
        g.edge(2, 5).setDistance(11000).set(speedEnc, 60, 60).setKeyValues(Map.of(STREET_NAME, new KValue("5-2")));

        g.edge(3, 6).setDistance(11000).set(speedEnc, 60, 60).setKeyValues(Map.of(STREET_NAME, new KValue("3-6")));
        g.edge(4, 7).setDistance(10000).set(speedEnc, 60, 60).setKeyValues(Map.of(STREET_NAME, new KValue("4-7")));
        g.edge(5, 8).setDistance(10000).set(speedEnc, 60, 60).setKeyValues(Map.of(STREET_NAME, new KValue("5-8")));

        g.edge(6, 7).setDistance(11000).set(speedEnc, 60, 60).setKeyValues(Map.of(STREET_NAME, new KValue("6-7")));
        EdgeIteratorState iter = g.edge(7, 8).setDistance(10000).set(speedEnc, 60, 60);
        PointList list = new PointList();
        list.add(1.0, 1.15);
        list.add(1.0, 1.16);
        iter.setWayGeometry(list);
        iter.setKeyValues(Map.of(STREET_NAME, new KValue("7-8")));
        // missing edge name
        g.edge(9, 10).setDistance(10000).set(speedEnc, 60, 60);
        EdgeIteratorState iter2 = g.edge(8, 9).setDistance(20000).set(speedEnc, 60, 60);
        list.clear();
        list.add(1.0, 1.3);
        iter2.setKeyValues(Map.of(STREET_NAME, new KValue("8-9")));
        iter2.setWayGeometry(list);
        return g;
    }

    @Test
    public void testWayList() {
        Graph g = createTestGraph();

        SpeedWeighting weighting = new SpeedWeighting(speedEnc);
        Path p = new Dijkstra(g, weighting, TraversalMode.NODE_BASED).calcPath(0, 10);
        InstructionList wayList = InstructionsFromEdges.calcInstructions(p, g, weighting, carManager, usTR, false);
        List<String> tmpList = getTurnDescriptions(wayList);
        assertEquals(Arrays.asList("continue onto 0-1", "turn right onto 1-4", "turn left onto 7-8", "arrive at destination"),
                tmpList);

        wayList = InstructionsFromEdges.calcInstructions(p, g, weighting, carManager, trMap.getWithFallBack(Locale.GERMAN), false);
        tmpList = getTurnDescriptions(wayList, trMap.getWithFallBack(Locale.GERMAN));
        assertEquals(Arrays.asList("dem Straßenverlauf von 0-1 folgen", "rechts abbiegen auf 1-4", "links abbiegen auf 7-8", "Ziel erreicht"),
                tmpList);

        assertEquals(70000.0, sumDistances(wayList), 1e-1);

        PointList points = p.calcPoints();
        assertEquals(10, points.size());
        // check order of tower nodes
        assertEquals(1, points.getLon(0), 1e-6);
        assertEquals(1.4, points.getLon(points.size() - 1), 1e-6);

        // check order of pillar nodes
        assertEquals(1.15, points.getLon(4), 1e-6);
        assertEquals(1.16, points.getLon(5), 1e-6);

        compare(Arrays.asList(asL(1.2d, 1.0d), asL(1.2d, 1.1), asL(1.0, 1.1), asL(1.1, 1.4)),
                createStartPoints(wayList));

        p = new Dijkstra(g, weighting, TraversalMode.NODE_BASED).calcPath(6, 2);
        assertEquals(42000, p.getDistance(), 1e-2);
        assertEquals(IntArrayList.from(6, 7, 8, 5, 2), p.calcNodes());

        wayList = InstructionsFromEdges.calcInstructions(p, g, weighting, carManager, usTR, false);
        tmpList = getTurnDescriptions(wayList);
        assertEquals(Arrays.asList("continue onto 6-7", "turn left onto 5-8", "arrive at destination"),
                tmpList);

        compare(Arrays.asList(asL(1d, 1d), asL(1d, 1.2), asL(1.2, 1.2)),
                createStartPoints(wayList));

        // special case of identical start and end
        p = new Dijkstra(g, weighting, TraversalMode.NODE_BASED).calcPath(0, 0);
        wayList = InstructionsFromEdges.calcInstructions(p, g, weighting, carManager, usTR, false);
        assertEquals(1, wayList.size());
        assertEquals("arrive at destination", wayList.get(0).getTurnDescription(usTR));
    }

    @Test
    public void testWayList2() {
        BaseGraph g = new BaseGraph.Builder(carManager).create();
        //   2
        //    \.  5
        //      \/
        //      4
        //     /
        //    3
        NodeAccess na = g.getNodeAccess();
        na.setNode(2, 10.3, 10.15);
        na.setNode(3, 10.0, 10.08);
        na.setNode(4, 10.1, 10.10);
        na.setNode(5, 10.2, 10.13);
        g.edge(3, 4).setDistance(100).set(speedEnc, 60, 60).setKeyValues(Map.of(STREET_NAME, new KValue("3-4")));
        g.edge(4, 5).setDistance(100).set(speedEnc, 60, 60).setKeyValues(Map.of(STREET_NAME, new KValue("4-5")));

        EdgeIteratorState iter = g.edge(2, 4).setDistance(100).set(speedEnc, 60, 60);
        iter.setKeyValues(Map.of(STREET_NAME, new KValue("2-4")));
        PointList list = new PointList();
        list.add(10.20, 10.05);
        iter.setWayGeometry(list);

        SpeedWeighting weighting = new SpeedWeighting(speedEnc);
        Path p = new Dijkstra(g, weighting, tMode).calcPath(2, 3);

        InstructionList wayList = InstructionsFromEdges.calcInstructions(p, g, weighting, carManager, usTR, false);
        List<String> tmpList = getTurnDescriptions(wayList);
        assertEquals(Arrays.asList("continue onto 2-4", "turn slight right onto 3-4", "arrive at destination"),
                tmpList);

        p = new Dijkstra(g, weighting, tMode).calcPath(3, 5);
        wayList = InstructionsFromEdges.calcInstructions(p, g, weighting, carManager, usTR, false);
        tmpList = getTurnDescriptions(wayList);
        assertEquals(Arrays.asList("continue onto 3-4", "keep right onto 4-5", "arrive at destination"),
                tmpList);
    }

    // problem: we normally don't want instructions if streetname stays but here it is suboptimal:
    @Test
    public void testNoInstructionIfSameStreet() {
        BaseGraph g = new BaseGraph.Builder(carManager).create();
        //   2
        //    \.  5
        //      \/
        //      4
        //     /
        //    3
        NodeAccess na = g.getNodeAccess();
        na.setNode(2, 10.3, 10.15);
        na.setNode(3, 10.0, 10.05);
        na.setNode(4, 10.1, 10.10);
        na.setNode(5, 10.2, 10.15);
        g.edge(3, 4).setDistance(100).set(speedEnc, 60, 60).setKeyValues(Map.of(STREET_NAME, new KValue("street")));
        g.edge(4, 5).setDistance(100).set(speedEnc, 60, 60).setKeyValues(Map.of(STREET_NAME, new KValue("4-5")));

        EdgeIteratorState iter = g.edge(2, 4).setDistance(100).set(speedEnc, 60, 60);
        iter.setKeyValues(Map.of(STREET_NAME, new KValue("street")));
        PointList list = new PointList();
        list.add(10.20, 10.05);
        iter.setWayGeometry(list);

        Weighting weighting = new SpeedWeighting(speedEnc);
        Path p = new Dijkstra(g, weighting, tMode).calcPath(2, 3);
        InstructionList wayList = InstructionsFromEdges.calcInstructions(p, g, weighting, carManager, usTR, false);
        List<String> tmpList = getTurnDescriptions(wayList);
        assertEquals(Arrays.asList("continue onto street", "turn right onto street", "arrive at destination"), tmpList);
    }

    @Test
    public void testNoInstructionIfSlightTurnAndAlternativeIsSharp() {
        BaseGraph g = new BaseGraph.Builder(carManager).create();
        // real world example: https://graphhopper.com/maps/?point=51.734514%2C9.225571&point=51.734643%2C9.22541
        // https://github.com/graphhopper/graphhopper/issues/1441
        // From 1 to 3
        //
        //       3
        //       |
        //       2
        //      /\
        //     4  1

        NodeAccess na = g.getNodeAccess();
        na.setNode(1, 51.734514, 9.225571);
        na.setNode(2, 51.73458, 9.225442);
        na.setNode(3, 51.734643, 9.22541);
        na.setNode(4, 51.734451, 9.225436);
        g.edge(1, 2).setDistance(10).set(speedEnc, 60, 60);
        g.edge(2, 3).setDistance(10).set(speedEnc, 60, 60);
        g.edge(2, 4).setDistance(10).set(speedEnc, 60, 60);

        Weighting weighting = new SpeedWeighting(speedEnc);
        Path p = new Dijkstra(g, weighting, tMode).calcPath(1, 3);
        InstructionList wayList = InstructionsFromEdges.calcInstructions(p, g, weighting, carManager, usTR, false);
        List<String> tmpList = getTurnDescriptions(wayList);
        assertEquals(Arrays.asList("continue", "arrive at destination"), tmpList);
    }

    @Test
    public void testNoInstructionIfSlightTurnAndAlternativeIsSharp2() {
        BaseGraph g = new BaseGraph.Builder(carManager).create();
        // real world example: https://graphhopper.com/maps/?point=48.748493%2C9.322455&point=48.748776%2C9.321889
        // https://github.com/graphhopper/graphhopper/issues/1441
        // From 1 to 3
        //
        //       3
        //         \
        //          2--- 1
        //           \
        //            4

        NodeAccess na = g.getNodeAccess();
        na.setNode(1, 48.748493, 9.322455);
        na.setNode(2, 48.748577, 9.322152);
        na.setNode(3, 48.748776, 9.321889);
        na.setNode(4, 48.74847, 9.322299);
        g.edge(1, 2).setDistance(10).set(speedEnc, 60, 60);
        g.edge(2, 3).setDistance(10).set(speedEnc, 60, 60);
        g.edge(2, 4).setDistance(10).set(speedEnc, 60, 60);

        Weighting weighting = new SpeedWeighting(speedEnc);
        Path p = new Dijkstra(g, weighting, tMode).calcPath(1, 3);
        InstructionList wayList = InstructionsFromEdges.calcInstructions(p, g, weighting, carManager, usTR, false);
        List<String> tmpList = getTurnDescriptions(wayList);
        assertEquals(Arrays.asList("continue", "arrive at destination"), tmpList);
    }

    @Test
    public void testNoInstructionIfSlightTurnAndAlternativeIsSharp3() {
        DecimalEncodedValue speedEnc = new DecimalEncodedValueImpl("speed", 4, 2, true);
        EncodingManager tmpEM = new EncodingManager.Builder().add(speedEnc).add(RoadClass.create())
                .add(RoadClassLink.create()).add(Roundabout.create()).add(MaxSpeed.create()).build();
        EnumEncodedValue<RoadClass> rcEV = tmpEM.getEnumEncodedValue(RoadClass.KEY, RoadClass.class);
        BaseGraph g = new BaseGraph.Builder(tmpEM).create();
        // real world example: https://graphhopper.com/maps/?point=48.411549,15.599567&point=48.411663%2C15.600527&profile=bike
        // From 1 to 3

        //          3
        //         /
        // 1 ---- 2
        //        \
        //         4

        NodeAccess na = g.getNodeAccess();
        na.setNode(1, 48.411392, 15.599713);
        na.setNode(2, 48.411457, 15.600410);
        na.setNode(3, 48.411610, 15.600409);
        na.setNode(4, 48.411322, 15.600459);

        g.edge(1, 2).setDistance(20).set(speedEnc, 18, 18);
        g.edge(2, 3).setDistance(20).set(speedEnc, 18, 18);
        g.edge(2, 4).setDistance(20).set(speedEnc, 4, 4);

        g.edge(1, 2).set(rcEV, RoadClass.RESIDENTIAL).setKeyValues(Map.of(STREET_NAME, new KValue("pfarr")));
        g.edge(2, 3).set(rcEV, RoadClass.RESIDENTIAL).setKeyValues(Map.of(STREET_NAME, new KValue("pfarr")));
        g.edge(2, 4).set(rcEV, RoadClass.PEDESTRIAN).setKeyValues(Map.of(STREET_NAME, new KValue("markt")));

        Weighting weighting = new SpeedWeighting(speedEnc);
        Path p = new Dijkstra(g, weighting, tMode).calcPath(1, 3);
        InstructionList wayList = InstructionsFromEdges.calcInstructions(p, g, weighting, tmpEM, usTR, false);
        List<String> tmpList = getTurnDescriptions(wayList);
        assertEquals(Arrays.asList("continue", "turn left", "arrive at destination"), tmpList);
        assertEquals(3, wayList.size());
        assertEquals(20, wayList.get(1).getDistance());
    }

    @Test
    public void testInstructionIfTurn() {
        DecimalEncodedValue speedEnc = new DecimalEncodedValueImpl("speed", 4, 2, true);
        EncodingManager tmpEM = new EncodingManager.Builder().add(speedEnc).add(RoadClass.create()).add(RoadClassLink.create()).add(Roundabout.create()).add(MaxSpeed.create()).build();
        EnumEncodedValue<RoadClass> rcEV = tmpEM.getEnumEncodedValue(RoadClass.KEY, RoadClass.class);
        BaseGraph g = new BaseGraph.Builder(tmpEM).create();
        // real world example: https://graphhopper.com/maps/?point=48.412169%2C15.604888&point=48.412251%2C15.60543&profile=bike
        // From 1 to 4

        //      3
        //       \
        //      - 2
        //  1_ /   \
        //          4

        NodeAccess na = g.getNodeAccess();
        na.setNode(1, 48.412169, 15.604888);
        na.setNode(2, 48.412411, 15.605189);
        na.setNode(3, 48.412614, 15.604872);
        na.setNode(4, 48.412148, 15.605543);

        g.edge(1, 2).setDistance(20).set(speedEnc, 18, 18)
                .set(rcEV, RoadClass.RESIDENTIAL);
        g.edge(2, 3).setDistance(20).set(speedEnc, 18, 18)
                .set(rcEV, RoadClass.SECONDARY);
        g.edge(2, 4).setDistance(20).set(speedEnc, 18, 18)
                .set(rcEV, RoadClass.SECONDARY);

        Weighting weighting = new SpeedWeighting(speedEnc);
        Path p = new Dijkstra(g, weighting, tMode).calcPath(1, 4);
        InstructionList wayList = InstructionsFromEdges.calcInstructions(p, g, weighting, tmpEM, usTR, false);
        List<String> tmpList = getTurnDescriptions(wayList);
        assertEquals(Arrays.asList("continue", "turn right", "arrive at destination"), tmpList);
        assertEquals(3, wayList.size());
        assertEquals(20, wayList.get(1).getDistance());
    }

    @Test
    public void testInstructionIfSlightTurn() {
        DecimalEncodedValue speedEnc = new DecimalEncodedValueImpl("speed", 4, 1, false);
        EncodingManager tmpEM = new EncodingManager.Builder().add(speedEnc)
                .add(Roundabout.create()).add(RoadClass.create()).add(RoadClassLink.create()).add(MaxSpeed.create()).build();
        BaseGraph g = new BaseGraph.Builder(tmpEM).create();
        // real world example: https://graphhopper.com/maps/?point=43.729379,7.417697&point=43.729798,7.417263&profile=foot
        // From 4 to 3 and 4 to 1

        //    1  3
        //     \ \
        //      \2
        //        \
        //         .
        //          \
        //           4

        NodeAccess na = g.getNodeAccess();
        na.setNode(1, 43.72977, 7.417209);
        na.setNode(2, 43.7297585, 7.4173079);
        na.setNode(3, 43.729821, 7.41725);
        na.setNode(4, 43.729476, 7.417633);

        // default is priority=0 so set it to 1
        g.edge(1, 2).setDistance(20).set(speedEnc, 5).
                setKeyValues(Map.of(STREET_NAME, new KValue("myroad")));
        g.edge(2, 3).setDistance(20).set(speedEnc, 5).
                setKeyValues(Map.of(STREET_NAME, new KValue("myroad")));
        PointList pointList = new PointList();
        pointList.add(43.729627, 7.41749);
        g.edge(2, 4).setDistance(20).set(speedEnc, 5).
                setKeyValues(Map.of(STREET_NAME, new KValue("myroad"))).setWayGeometry(pointList);

        Weighting weighting = new SpeedWeighting(speedEnc);
        Path p = new Dijkstra(g, weighting, tMode).calcPath(4, 3);
        assertTrue(p.isFound());
        assertEquals(IntArrayList.from(4, 2, 3), p.calcNodes());
        InstructionList wayList = InstructionsFromEdges.calcInstructions(p, g, weighting, tmpEM, usTR, false);
        List<String> tmpList = getTurnDescriptions(wayList);
        assertEquals(Arrays.asList("continue onto myroad", "keep right onto myroad", "arrive at destination"), tmpList);
        assertEquals(3, wayList.size());
        assertEquals(20, wayList.get(1).getDistance());

        p = new Dijkstra(g, weighting, tMode).calcPath(4, 1);
        assertEquals(IntArrayList.from(4, 2, 1), p.calcNodes());
        wayList = InstructionsFromEdges.calcInstructions(p, g, weighting, tmpEM, usTR, false);
        tmpList = getTurnDescriptions(wayList);
        assertEquals(Arrays.asList("continue onto myroad", "keep left onto myroad", "arrive at destination"), tmpList);
        assertEquals(3, wayList.size());
        assertEquals(20, wayList.get(1).getDistance());
    }

    @Test
    public void testInstructionWithHighlyCustomProfileWithRoadsBase() {
        BooleanEncodedValue roadsAccessEnc = new SimpleBooleanEncodedValue("access", true);
        DecimalEncodedValue roadsSpeedEnc = new DecimalEncodedValueImpl("speed", 7, 2, true);
        EncodingManager tmpEM = EncodingManager.start().add(roadsAccessEnc).add(roadsSpeedEnc)
                .add(RoadClass.create()).add(Roundabout.create()).add(RoadClassLink.create()).add(MaxSpeed.create()).build();
        EnumEncodedValue<RoadClass> rcEV = tmpEM.getEnumEncodedValue(RoadClass.KEY, RoadClass.class);
        BaseGraph g = new BaseGraph.Builder(tmpEM).create();
        // real world example: https://graphhopper.com/maps/?point=55.691214%2C12.57065&point=55.689957%2C12.570387
        // From 3 to 4
        //
        //       3
        //         \
        //          2--- 1
        //          | \
        //          5  4

        NodeAccess na = g.getNodeAccess();
        na.setNode(1, 55.690951, 12.571127);
        na.setNode(2, 55.69109, 12.5708);
        na.setNode(3, 55.691214, 12.57065);
        na.setNode(4, 55.690849, 12.571004);
        na.setNode(5, 55.690864, 12.570886);

        g.edge(3, 2).setDistance(10).set(roadsSpeedEnc, 50, 50).set(roadsAccessEnc, true, true);
        g.edge(2, 4).setDistance(10).set(roadsSpeedEnc, 40, 40).set(roadsAccessEnc, true, true);
        g.edge(2, 1).setDistance(10).set(roadsSpeedEnc, 40, 40).set(roadsAccessEnc, true, true);
        g.edge(2, 5).setDistance(10).set(roadsSpeedEnc, 10, 10).set(roadsAccessEnc, true, true).set(rcEV, RoadClass.PEDESTRIAN);

        CustomModel customModel = new CustomModel();
        customModel.addToSpeed(If("true", Statement.Op.LIMIT, "speed"));
        customModel.addToPriority(If("road_class == PEDESTRIAN", Statement.Op.MULTIPLY, "0"));
        Weighting weighting = CustomModelParser.createWeighting(tmpEM, TurnCostProvider.NO_TURN_COST_PROVIDER, customModel);
        Path p = new Dijkstra(g, weighting, tMode).calcPath(3, 4);
        assertEquals(IntArrayList.from(3, 2, 4), p.calcNodes());
        InstructionList wayList = InstructionsFromEdges.calcInstructions(p, g, weighting, tmpEM, usTR, false);
        List<String> tmpList = getTurnDescriptions(wayList);
        assertEquals(Arrays.asList("continue", "keep right", "arrive at destination"), tmpList);
    }

    @Test
    public void testEmptyList() {
        BaseGraph g = new BaseGraph.Builder(carManager).create();
        g.getNodeAccess().setNode(1, 0, 0);
        Weighting weighting = new SpeedWeighting(speedEnc);
        Path p = new Dijkstra(g, weighting, tMode).calcPath(0, 1);
        InstructionList il = InstructionsFromEdges.calcInstructions(p, g, weighting, carManager, usTR, false);
        assertEquals(0, il.size());
    }

    @Test
    public void testFind() {
        BaseGraph g = new BaseGraph.Builder(carManager).create();
        //   n-4-5   (n: pillar node)
        //   |
        // 7-3-2-6
        //     |
        //     1
        NodeAccess na = g.getNodeAccess();
        na.setNode(1, 15.0, 10);
        na.setNode(2, 15.1, 10);
        na.setNode(3, 15.1, 9.9);
        PointList waypoint = new PointList();
        waypoint.add(15.2, 9.9);
        na.setNode(4, 15.2, 10);
        na.setNode(5, 15.2, 10.1);
        na.setNode(6, 15.1, 10.1);
        na.setNode(7, 15.1, 9.8);

        g.edge(1, 2).setDistance(10000).set(speedEnc, 60, 60).setKeyValues(Map.of(STREET_NAME, new KValue("1-2")));
        g.edge(2, 3).setDistance(10000).set(speedEnc, 60, 60).setKeyValues(Map.of(STREET_NAME, new KValue("2-3")));
        g.edge(2, 6).setDistance(10000).set(speedEnc, 60, 60).setKeyValues(Map.of(STREET_NAME, new KValue("2-6")));
        g.edge(3, 4).setDistance(10000).set(speedEnc, 60, 60).setKeyValues(Map.of(STREET_NAME, new KValue("3-4"))).setWayGeometry(waypoint);
        g.edge(3, 7).setDistance(10000).set(speedEnc, 60, 60).setKeyValues(Map.of(STREET_NAME, new KValue("3-7")));
        g.edge(4, 5).setDistance(10000).set(speedEnc, 60, 60).setKeyValues(Map.of(STREET_NAME, new KValue("4-5")));

        Weighting weighting = new SpeedWeighting(speedEnc);
        Path p = new Dijkstra(g, weighting, tMode).calcPath(1, 5);
        assertEquals(IntArrayList.from(1, 2, 3, 4, 5), p.calcNodes());
        InstructionList wayList = InstructionsFromEdges.calcInstructions(p, g, weighting, carManager, usTR, false);

        // query on first edge, get instruction for second edge
        assertEquals("2-3", Instructions.find(wayList, 15.05, 10, 1000).getName());

        // query east of first edge, get instruction for second edge
        assertEquals("2-3", Instructions.find(wayList, 15.05, 10.001, 1000).getName());

        // query south-west of node 3, get instruction for third edge
        assertEquals("3-4", Instructions.find(wayList, 15.099, 9.9, 1000).getName());

        // too far away
        assertNull(Instructions.find(wayList, 50.8, 50.25, 1000));
    }

    @Test
    public void getLanesInfoSingleLaneNone() {
        Graph g = createTestGraph();
        Weighting weighting = new SpeedWeighting(speedEnc);
        Path p = new Dijkstra(g, weighting, tMode).calcPath(1, 5);
        InstructionList wayList = InstructionsFromEdges.calcInstructions(p, g, weighting, carManager, usTR, false);
        assertTrue(wayList.get(0).getInstructionDetails().isEmpty());
    }

    @Test
    public void getBasicLanesInfo() {
        BaseGraph g = new BaseGraph.Builder(carManager).create();
        // 0-1-2
        // | | |
        // 3-4-5
        NodeAccess na = g.getNodeAccess();
        na.setNode(0, 1.2, 1.0);
        na.setNode(1, 1.2, 1.1);
        na.setNode(2, 1.17, 1.2);
        na.setNode(3, 1.1, 1.0);
        na.setNode(4, 1.1, 1.1);
        na.setNode(5, 1.1, 1.2);
        g.edge(0, 1).setDistance(10000).set(speedEnc, 60, 60).setKeyValues(Map.of(STREET_NAME, new KValue("0-1"), TURN_LANES, new KValue("continue|right")));
        g.edge(1, 2).setDistance(10000).set(speedEnc, 60, 60).setKeyValues(Map.of(STREET_NAME, new KValue("1-2")));
        g.edge(0, 3).setDistance(11000).set(speedEnc, 60, 60).setKeyValues(Map.of(STREET_NAME, new KValue("0-3")));
        g.edge(1, 4).setDistance(10000).set(speedEnc, 60, 60).setKeyValues(Map.of(STREET_NAME, new KValue("1-4")));
        g.edge(2, 5).setDistance(10000).set(speedEnc, 60, 60).setKeyValues(Map.of(STREET_NAME, new KValue("2-5")));
        g.edge(3, 4).setDistance(10000).set(speedEnc, 60, 60).setKeyValues(Map.of(STREET_NAME, new KValue("3-4"), TURN_LANES, new KValue("continue;left|continue|right"), TURN_LANES_VEHICLE_ACCESS, new KValue("yes|no|yes")));
        Map<String, KValue> kv = Map.of(STREET_NAME, new KValue("4-5"), TURN_LANES, new KValue("left|continue|right", null));
        g.edge(4, 5).setDistance(10000).set(speedEnc, 60, 60).setKeyValues(kv);

        Weighting weighting = new SpeedWeighting(speedEnc);
        Path p = new Dijkstra(g, weighting, tMode).calcPath(0, 4);
        InstructionList wayList = InstructionsFromEdges.calcInstructions(p, g, weighting, carManager, usTR, true);
        assertEquals(3, wayList.size());
        assertEquals("continue onto 0-1", wayList.get(0).getTurnDescription(usTR));
        assertEquals("[]", wayList.get(0).getInstructionDetails().toString());
        assertEquals("turn right onto 1-4", wayList.get(1).getTurnDescription(usTR));
        assertEquals("[{beforeTurn=10000.0, lanes=[{directions:[continue], valid:false}, {directions:[right], valid:true}]}]", wayList.get(1).getInstructionDetails().toString());
        assertEquals("[]", wayList.get(2).getInstructionDetails().toString());

        p = new Dijkstra(g, weighting, tMode).calcPath(0, 2);
        wayList = InstructionsFromEdges.calcInstructions(p, g, weighting, carManager, usTR, true);
        assertEquals(2, wayList.size());
        assertEquals("continue onto 0-1", wayList.get(0).getTurnDescription(usTR));
        assertEquals("[]", wayList.get(0).getInstructionDetails().toString());
        // TODO using a lane in the middle should create a new "continue" instruction and turn lane info
        //  but forcing a new instruction in these cases will make problems when a way of a junction has its lanes spread along multiple edges
        assertEquals("arrive at destination", wayList.get(1).getTurnDescription(usTR));
        assertEquals("[]", wayList.get(1).getInstructionDetails().toString());

        // vehicle access restricts usage of middle lane e.g. http://localhost:3000/?point=52.515454%2C13.385814&point=52.516705%2C13.386264
        p = new Dijkstra(g, weighting, tMode).calcPath(3, 2);
        wayList = InstructionsFromEdges.calcInstructions(p, g, weighting, carManager, usTR, true);
        assertEquals(4, wayList.size());
        assertEquals("continue onto 3-4", wayList.get(0).getTurnDescription(usTR));
        assertEquals("[]", wayList.get(0).getInstructionDetails().toString());
        assertEquals("turn left onto 1-4", wayList.get(1).getTurnDescription(usTR));
        assertEquals("[{beforeTurn=10000.0, lanes=[{directions:[continue, left], valid:true}, {directions:[continue], valid:false}, {directions:[right], valid:false}]}]", wayList.get(1).getInstructionDetails().toString());
        assertEquals("[]", wayList.get(2).getInstructionDetails().toString());

        p = new Dijkstra(g, weighting, tMode).calcPath(3, 4);
        wayList = InstructionsFromEdges.calcInstructions(p, g, weighting, carManager, usTR, true);
        assertEquals(2, wayList.size());
        assertEquals("continue onto 3-4", wayList.get(0).getTurnDescription(usTR));
        assertEquals("[]", wayList.get(0).getInstructionDetails().toString()); // no turn, hence no lanes?
        assertEquals("[]", wayList.get(1).getInstructionDetails().toString());

        p = new Dijkstra(g, weighting, tMode).calcPath(5, 3);
        wayList = InstructionsFromEdges.calcInstructions(p, g, weighting, carManager, usTR, true);
        assertEquals(2, wayList.size());
        assertEquals("continue onto 4-5", wayList.get(0).getTurnDescription(usTR));
        assertEquals("[]", wayList.get(0).getInstructionDetails().toString()); // different direction => no turn lanes info
        assertEquals("[]", wayList.get(1).getInstructionDetails().toString());
    }

    @Test
    public void getMergeMultipleEdgesOfSameJunction() {
        // e.g. http://localhost:3000/?point=52.519159%2C13.452253&point=52.516092%2C13.45156
        BaseGraph g = new BaseGraph.Builder(carManager).create();
        // 0-1-2-3     10
        //     | |     |
        //     5-6--7--8--9
        //       |  |     |
        //      11-12-13-14-15
        NodeAccess na = g.getNodeAccess();
        na.setNode(0, 1.2, 1.0);
        na.setNode(1, 1.2, 1.1);
        na.setNode(2, 1.2, 1.2);
        na.setNode(3, 1.2, 1.3);
        na.setNode(5, 1.1, 1.2);
        na.setNode(6, 1.1, 1.3);
        na.setNode(7, 1.1, 1.4);
        na.setNode(8, 1.1, 1.5);
        na.setNode(9, 1.1, 1.6);
        na.setNode(10, 1.2, 1.5);
        na.setNode(11, 1.0, 1.3);
        na.setNode(12, 1.0, 1.4);
        na.setNode(13, 1.0, 1.5);
        na.setNode(14, 1.0, 1.6);
        na.setNode(15, 1.0, 1.7);

        g.edge(0, 1).setDistance(10000).set(speedEnc, 60, 60).setKeyValues(Map.of(STREET_NAME, new KValue("0-1"), TURN_LANES,new KValue( "continue|continue;right")));
        g.edge(1, 2).setDistance(10000).set(speedEnc, 60, 60).setKeyValues(Map.of(STREET_NAME,new KValue( "1-2"), TURN_LANES, new KValue("continue|right")));
        g.edge(2, 3).setDistance(10000).set(speedEnc, 60, 60).setKeyValues(Map.of(STREET_NAME,new KValue( "2-3")));
        g.edge(2, 5).setDistance(10000).set(speedEnc, 60, 60).setKeyValues(Map.of(STREET_NAME,new KValue( "2-5")));
        g.edge(3, 6).setDistance(10000).set(speedEnc, 60, 60).setKeyValues(Map.of(STREET_NAME,new KValue( "3-6"), TURN_LANES,new KValue( "continue|right")));
        g.edge(5, 6).setDistance(10000).set(speedEnc, 60, 60).setKeyValues(Map.of(STREET_NAME,new KValue( "5-6")));
        g.edge(6, 7).setDistance(10000).set(speedEnc, 60, 60).setKeyValues(Map.of(STREET_NAME,new KValue( "6-7"), TURN_LANES, new KValue("continue|right")));
        g.edge(7, 8).setDistance(10000).set(speedEnc, 60, 60).setKeyValues(Map.of(STREET_NAME,new KValue( "7-8"), TURN_LANES, new KValue("left|continue")));
        g.edge(8, 9).setDistance(10000).set(speedEnc, 60, 60).setKeyValues(Map.of(STREET_NAME,new KValue( "8-9")));

        g.edge(8, 10).setDistance(10000).set(speedEnc, 60, 60).setKeyValues(Map.of(STREET_NAME,new KValue( "8-10")));
        g.edge(6, 11).setDistance(10000).set(speedEnc, 60, 60).setKeyValues(Map.of(STREET_NAME,new KValue( "6-11")));
        g.edge(7, 12).setDistance(10000).set(speedEnc, 60, 60).setKeyValues(Map.of(STREET_NAME, new KValue("7-12")));
        g.edge(9, 14).setDistance(10000).set(speedEnc, 60, 60).setKeyValues(Map.of(STREET_NAME, new KValue("9-14")));

        g.edge(11, 12).setDistance(10000).set(speedEnc, 60, 60).setKeyValues(Map.of(STREET_NAME,new KValue( "11-12"), TURN_LANES,new KValue( "left|continue")));
        g.edge(12, 13).setDistance(10000).set(speedEnc, 60, 60).setKeyValues(Map.of(STREET_NAME, new KValue("12-13"), TURN_LANES, new KValue("left|continue")));
        g.edge(13, 14).setDistance(10000).set(speedEnc, 60, 60).setKeyValues(Map.of(STREET_NAME, new KValue("13-14"), TURN_LANES, new KValue("left|continue")));
        g.edge(14, 15).setDistance(10000).set(speedEnc, 60, 60).setKeyValues(Map.of(STREET_NAME, new KValue("14-15")));

        Weighting weighting = new SpeedWeighting(speedEnc);
        Path p = new Dijkstra(g, weighting, tMode).calcPath(0, 5);
        InstructionList wayList = InstructionsFromEdges.calcInstructions(p, g, weighting, carManager, usTR, true);
        assertEquals(3, wayList.size());
        assertEquals("continue onto 0-1", wayList.get(0).getTurnDescription(usTR));
        assertEquals("[]", wayList.get(0).getInstructionDetails().toString());
        assertEquals("turn right onto 2-5", wayList.get(1).getTurnDescription(usTR));
        assertEquals("[{beforeTurn=20000.0, lanes=[{directions:[continue], valid:false}, {directions:[continue, right], valid:true}]}, {beforeTurn=10000.0, lanes=[{directions:[continue], valid:false}, {directions:[right], valid:true}]}]", wayList.get(1).getInstructionDetails().toString());
        assertEquals("[]", wayList.get(2).getInstructionDetails().toString());

        // search backward from turn to keep lane infos
        // similar in real world: http://localhost:3000/?point=52.453383%2C13.457341&point=52.454789%2C13.461292&profile=car
        p = new Dijkstra(g, weighting, tMode).calcPath(6, 10);
        wayList = InstructionsFromEdges.calcInstructions(p, g, weighting, carManager, usTR, true);
        assertEquals(3, wayList.size());
        assertEquals("continue onto 6-7", wayList.get(0).getTurnDescription(usTR));
        assertEquals("[]", wayList.get(0).getInstructionDetails().toString());
        assertEquals("turn left onto 8-10", wayList.get(1).getTurnDescription(usTR));
        assertEquals("[{beforeTurn=20000.0, lanes=[{directions:[continue], valid:true}, {directions:[right], valid:false}]}, {beforeTurn=10000.0, lanes=[{directions:[left], valid:true}, {directions:[continue], valid:false}]}]", wayList.get(1).getInstructionDetails().toString());
        assertEquals("[]", wayList.get(2).getInstructionDetails().toString());

        // an ambiguous left turn lane comes directly before the correct left turn -> beforeTurn should not be too early (i.e. 30 000 vs 20 000)
        p = new Dijkstra(g, weighting, tMode).calcPath(11, 9);
        wayList = InstructionsFromEdges.calcInstructions(p, g, weighting, carManager, usTR, true);
        assertEquals(3, wayList.size());
        assertEquals("continue onto 11-12", wayList.get(0).getTurnDescription(usTR));
        assertEquals("[]", wayList.get(0).getInstructionDetails().toString());
        assertEquals("turn left onto 9-14", wayList.get(1).getTurnDescription(usTR));
        assertEquals("[{beforeTurn=20000.0, lanes=[{directions:[left], valid:true}, {directions:[continue], valid:false}]}]", wayList.get(1).getInstructionDetails().toString());
        assertEquals("[]", wayList.get(2).getInstructionDetails().toString());
    }

    // TODO better lane guidance: do not mark the "left+continue" lane of "left+continue|continue|right" as active
    //  when the next turn is a right turn and very close and would require two lane changes
    // TODO no matching lanes: "slight left" turn but lanes are none|none|slight_right => https://www.openstreetmap.org/way/215226484
    // TODO no arrow is on the road => lane gets "none" => no lane is valid for right turn
    //  http://localhost:3000/?point=52.515176%252C13.460514&point=52.51728%252C13.453813

    @Test
    public void testSplitWays() {
        DecimalEncodedValue roadsSpeedEnc = new DecimalEncodedValueImpl("speed", 7, 2, true);
        EncodingManager tmpEM = EncodingManager.start().add(roadsSpeedEnc).
                add(RoadClass.create()).add(Roundabout.create()).add(RoadClassLink.create()).
                add(MaxSpeed.create()).add(Lanes.create()).build();
        IntEncodedValue lanesEnc = tmpEM.getIntEncodedValue(Lanes.KEY);
        BaseGraph g = new BaseGraph.Builder(tmpEM).create();
        // real world example: https://graphhopper.com/maps/?point=43.626238%2C-79.715268&point=43.624647%2C-79.713204&profile=car
        //
        //       1   3
        //         \ |
        //          2
        //           \
        //            4

        NodeAccess na = g.getNodeAccess();
        na.setNode(1, 43.626246, -79.71522);
        na.setNode(2, 43.625503, -79.714228);
        na.setNode(3, 43.626285, -79.714974);
        na.setNode(4, 43.625129, -79.713692);

        PointList list = new PointList();
        list.add(43.62549, -79.714292);
        g.edge(1, 2).setKeyValues(Map.of(STREET_NAME, new KValue("main"))).setWayGeometry(list).
                setDistance(110).set(roadsSpeedEnc, 50, 0).set(lanesEnc, 2);
        g.edge(2, 3).setKeyValues(Map.of(STREET_NAME, new KValue("main"))).
                setDistance(110).set(roadsSpeedEnc, 50, 0).set(lanesEnc, 3);
        g.edge(2, 4).setKeyValues(Map.of(STREET_NAME, new KValue("main"))).
                setDistance(80).set(roadsSpeedEnc, 50, 50).set(lanesEnc, 5);

        Weighting weighting = new SpeedWeighting(roadsSpeedEnc);
        Path p = new Dijkstra(g, weighting, tMode).calcPath(1, 4);
        InstructionList wayList = InstructionsFromEdges.calcInstructions(p, g, weighting, tmpEM, usTR, false);
        List<String> tmpList = getTurnDescriptions(wayList);
        assertEquals(Arrays.asList("continue onto main", "arrive at destination"), tmpList);

        // Other roads should not influence instructions. Example: https://www.openstreetmap.org/node/392106581
        na.setNode(5, 43.625666, -79.714048);
        g.edge(2, 5).setDistance(80).set(roadsSpeedEnc, 50, 50).set(lanesEnc, 5);

        p = new Dijkstra(g, weighting, tMode).calcPath(1, 4);
        wayList = InstructionsFromEdges.calcInstructions(p, g, weighting, tmpEM, usTR, false);
        tmpList = getTurnDescriptions(wayList);
        assertEquals(Arrays.asList("continue onto main", "arrive at destination"), tmpList);
    }

    @Test
    public void testNotSplitWays() {
        DecimalEncodedValue roadsSpeedEnc = new DecimalEncodedValueImpl("speed", 7, 2, true);
        EncodingManager tmpEM = EncodingManager.start().add(roadsSpeedEnc).
                add(RoadClass.create()).add(Roundabout.create()).add(RoadClassLink.create()).
                add(MaxSpeed.create()).add(Lanes.create()).build();
        IntEncodedValue lanesEnc = tmpEM.getIntEncodedValue(Lanes.KEY);
        BaseGraph g = new BaseGraph.Builder(tmpEM).create();
        // real world example: https://graphhopper.com/maps/?point=51.425484%2C14.223298&point=51.42523%2C14.222864&profile=car
        //           3
        //           |
        //        1-2-4

        NodeAccess na = g.getNodeAccess();
        na.setNode(1, 51.42523, 14.222864);
        na.setNode(2, 51.425256, 14.22325);
        na.setNode(3, 51.425397, 14.223266);
        na.setNode(4, 51.425273, 14.223427);

        g.edge(1, 2).setKeyValues(Map.of(STREET_NAME, new KValue("dresdener"))).
                setDistance(110).set(roadsSpeedEnc, 50, 50).set(lanesEnc, 2);
        g.edge(2, 3).setKeyValues(Map.of(STREET_NAME, new KValue("dresdener"))).
                setDistance(110).set(roadsSpeedEnc, 50, 50).set(lanesEnc, 3);
        g.edge(2, 4).setKeyValues(Map.of(STREET_NAME, new KValue("main"))).
                setDistance(80).set(roadsSpeedEnc, 50, 50).set(lanesEnc, 5);

        Weighting weighting = new SpeedWeighting(roadsSpeedEnc);
        Path p = new Dijkstra(g, weighting, tMode).calcPath(3, 1);
        InstructionList wayList = InstructionsFromEdges.calcInstructions(p, g, weighting, tmpEM, usTR, false);
        List<String> tmpList = getTurnDescriptions(wayList);
        assertEquals(Arrays.asList("continue onto dresdener", "turn right onto dresdener", "arrive at destination"), tmpList);
    }

    private void compare(List<List<Double>> expected, List<List<Double>> actual) {
        for (int i = 0; i < expected.size(); i++) {
            List<Double> e = expected.get(i);
            List<Double> wasE = actual.get(i);
            for (int j = 0; j < e.size(); j++) {
                assertEquals(e.get(j), wasE.get(j), 1e-5d, "at index " + i + " value index " + j + " and value " + e + " vs " + wasE + "\n" + "Expected: " + expected + "\n" + "Actual: " + actual
                );
            }
        }
    }

    private List<Double> asL(Double... list) {
        return Arrays.asList(list);
    }

    private static List<List<Double>> createStartPoints(List<Instruction> instructions) {
        List<List<Double>> res = new ArrayList<>(instructions.size());
        for (Instruction instruction : instructions) {
            res.add(Arrays.asList(instruction.getPoints().getLat(0), instruction.getPoints().getLon(0)));
        }
        return res;
    }

    private double sumDistances(InstructionList il) {
        double val = 0;
        for (Instruction i : il) {
            val += i.getDistance();
        }
        return val;
    }

}
