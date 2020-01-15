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
import com.graphhopper.routing.Dijkstra;
import com.graphhopper.routing.InstructionsFromEdges;
import com.graphhopper.routing.Path;
import com.graphhopper.routing.profiles.EnumEncodedValue;
import com.graphhopper.routing.profiles.RoadAccess;
import com.graphhopper.routing.profiles.RoadEnvironment;
import com.graphhopper.routing.util.CarFlagEncoder;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.routing.util.FlagEncoder;
import com.graphhopper.routing.util.TraversalMode;
import com.graphhopper.routing.weighting.FastestWeighting;
import com.graphhopper.routing.weighting.ShortestWeighting;
import com.graphhopper.routing.weighting.Weighting;
import com.graphhopper.storage.Graph;
import com.graphhopper.storage.GraphBuilder;
import com.graphhopper.storage.NodeAccess;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import static org.junit.Assert.assertEquals;

/**
 * @author Peter Karich
 */
public class InstructionListTest {
    private final TranslationMap trMap = TranslationMapTest.SINGLETON;
    private final Translation usTR = trMap.getWithFallBack(Locale.US);
    private final TraversalMode tMode = TraversalMode.NODE_BASED;
    private EncodingManager carManager;
    private FlagEncoder carEncoder;

    @Before
    public void setUp() {
        carEncoder = new CarFlagEncoder();
        carManager = EncodingManager.create(carEncoder);
    }

    private List<String> getTurnDescriptions(InstructionList instructionList) {
        return getTurnDescriptions(instructionList, usTR);
    }

    private List<String> getTurnDescriptions(InstructionList instructionList, Translation tr) {
        List<String> list = new ArrayList<>();
        for (Instruction instruction : instructionList) {
            list.add(instruction.getTurnDescription(tr));
        }
        return list;
    }

    Graph createTestGraph() {
        Graph g = new GraphBuilder(carManager).create();
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
        g.edge(0, 1, 10000, true).setName("0-1");
        g.edge(1, 2, 11000, true).setName("1-2");

        g.edge(0, 3, 11000, true);
        g.edge(1, 4, 10000, true).setName("1-4");
        g.edge(2, 5, 11000, true).setName("5-2");

        g.edge(3, 6, 11000, true).setName("3-6");
        g.edge(4, 7, 10000, true).setName("4-7");
        g.edge(5, 8, 10000, true).setName("5-8");

        g.edge(6, 7, 11000, true).setName("6-7");
        EdgeIteratorState iter = g.edge(7, 8, 10000, true);
        PointList list = new PointList();
        list.add(1.0, 1.15);
        list.add(1.0, 1.16);
        iter.setWayGeometry(list);
        iter.setName("7-8");
        // missing edge name
        g.edge(9, 10, 10000, true);
        EdgeIteratorState iter2 = g.edge(8, 9, 20000, true);
        list.clear();
        list.add(1.0, 1.3);
        iter2.setName("8-9");
        iter2.setWayGeometry(list);
        return g;
    }

    @Test
    public void testWayList() {
        Graph g = createTestGraph();

        ShortestWeighting weighting = new ShortestWeighting(carEncoder);
        Path p = new Dijkstra(g, weighting, TraversalMode.NODE_BASED).calcPath(0, 10);
        InstructionList wayList = InstructionsFromEdges.calcInstructions(p, g, weighting, carManager, usTR);
        List<String> tmpList = getTurnDescriptions(wayList);
        assertEquals(Arrays.asList("continue onto 0-1", "turn right onto 1-4", "turn left onto 7-8", "arrive at destination"),
                tmpList);

        wayList = InstructionsFromEdges.calcInstructions(p, g, weighting, carManager, trMap.getWithFallBack(Locale.GERMAN));
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

        wayList = InstructionsFromEdges.calcInstructions(p, g, weighting, carManager, usTR);
        tmpList = getTurnDescriptions(wayList);
        assertEquals(Arrays.asList("continue onto 6-7", "turn left onto 5-8", "arrive at destination"),
                tmpList);

        compare(Arrays.asList(asL(1d, 1d), asL(1d, 1.2), asL(1.2, 1.2)),
                createStartPoints(wayList));

        // special case of identical start and end
        p = new Dijkstra(g, weighting, TraversalMode.NODE_BASED).calcPath(0, 0);
        wayList = InstructionsFromEdges.calcInstructions(p, g, weighting, carManager, usTR);
        assertEquals(1, wayList.size());
        assertEquals("arrive at destination", wayList.get(0).getTurnDescription(usTR));
    }

    @Test
    public void testWayList2() {
        Graph g = new GraphBuilder(carManager).create();
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
        g.edge(3, 4, 100, true).setName("3-4");
        g.edge(4, 5, 100, true).setName("4-5");

        EdgeIteratorState iter = g.edge(2, 4, 100, true);
        iter.setName("2-4");
        PointList list = new PointList();
        list.add(10.20, 10.05);
        iter.setWayGeometry(list);

        ShortestWeighting weighting = new ShortestWeighting(carEncoder);
        Path p = new Dijkstra(g, weighting, tMode).calcPath(2, 3);

        InstructionList wayList = InstructionsFromEdges.calcInstructions(p, g, weighting, carManager, usTR);
        List<String> tmpList = getTurnDescriptions(wayList);
        assertEquals(Arrays.asList("continue onto 2-4", "turn slight right onto 3-4", "arrive at destination"),
                tmpList);

        p = new Dijkstra(g, weighting, tMode).calcPath(3, 5);
        wayList = InstructionsFromEdges.calcInstructions(p, g, weighting, carManager, usTR);
        tmpList = getTurnDescriptions(wayList);
        assertEquals(Arrays.asList("continue onto 3-4", "keep right onto 4-5", "arrive at destination"),
                tmpList);
    }

    // TODO is this problem fixed with the new instructions?
    // problem: we normally don't want instructions if streetname stays but here it is suboptimal:
    @Test
    public void testNoInstructionIfSameStreet() {
        Graph g = new GraphBuilder(carManager).create();
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
        g.edge(3, 4, 100, true).setName("street");
        g.edge(4, 5, 100, true).setName("4-5");

        EdgeIteratorState iter = g.edge(2, 4, 100, true);
        iter.setName("street");
        PointList list = new PointList();
        list.add(10.20, 10.05);
        iter.setWayGeometry(list);

        ShortestWeighting weighting = new ShortestWeighting(carEncoder);
        Path p = new Dijkstra(g, weighting, tMode).calcPath(2, 3);
        InstructionList wayList = InstructionsFromEdges.calcInstructions(p, g, weighting, carManager, usTR);
        List<String> tmpList = getTurnDescriptions(wayList);
        assertEquals(Arrays.asList("continue onto street", "turn right onto street", "arrive at destination"), tmpList);
    }

    @Test
    public void testNoInstructionIfSlightTurnAndAlternativeIsSharp() {
        Graph g = new GraphBuilder(carManager).create();
        // Real World Example: https://graphhopper.com/maps/?point=51.734514%2C9.225571&point=51.734643%2C9.22541
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
        g.edge(1, 2, 10, true);
        g.edge(2, 3, 10, true);
        g.edge(2, 4, 10, true);

        ShortestWeighting weighting = new ShortestWeighting(carEncoder);
        Path p = new Dijkstra(g, weighting, tMode).calcPath(1, 3);
        InstructionList wayList = InstructionsFromEdges.calcInstructions(p, g, weighting, carManager, usTR);
        List<String> tmpList = getTurnDescriptions(wayList);
        assertEquals(Arrays.asList("continue", "arrive at destination"), tmpList);
    }

    @Test
    public void testNoInstructionIfSlightTurnAndAlternativeIsSharp2() {
        Graph g = new GraphBuilder(carManager).create();
        // Real World Example: https://graphhopper.com/maps/?point=48.748493%2C9.322455&point=48.748776%2C9.321889
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
        g.edge(1, 2, 10, true);
        g.edge(2, 3, 10, true);
        g.edge(2, 4, 10, true);

        ShortestWeighting weighting = new ShortestWeighting(carEncoder);
        Path p = new Dijkstra(g, weighting, tMode).calcPath(1, 3);
        InstructionList wayList = InstructionsFromEdges.calcInstructions(p, g, weighting, carManager, usTR);
        List<String> tmpList = getTurnDescriptions(wayList);
        assertEquals(Arrays.asList("continue", "arrive at destination"), tmpList);
    }

    @Test
    public void testEmptyList() {
        Graph g = new GraphBuilder(carManager).create();
        ShortestWeighting weighting = new ShortestWeighting(carEncoder);
        Path p = new Dijkstra(g, weighting, tMode).calcPath(0, 1);
        InstructionList il = InstructionsFromEdges.calcInstructions(p, g, weighting, carManager, usTR);
        assertEquals(0, il.size());
    }

    @Test
    public void testFind() {
        Graph g = new GraphBuilder(carManager).create();
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

        g.edge(1, 2, 10000, true).setName("1-2");
        g.edge(2, 3, 10000, true).setName("2-3");
        g.edge(2, 6, 10000, true).setName("2-6");
        g.edge(3, 4, 10000, true).setName("3-4").setWayGeometry(waypoint);
        g.edge(3, 7, 10000, true).setName("3-7");
        g.edge(4, 5, 10000, true).setName("4-5");

        ShortestWeighting weighting = new ShortestWeighting(carEncoder);
        Path p = new Dijkstra(g, weighting, tMode).calcPath(1, 5);
        InstructionList wayList = InstructionsFromEdges.calcInstructions(p, g, weighting, carManager, usTR);

        // query on first edge, get instruction for second edge
        assertEquals("2-3", wayList.find(15.05, 10, 1000).getName());

        // query east of first edge, get instruction for second edge
        assertEquals("2-3", wayList.find(15.05, 10.001, 1000).getName());

        // query south-west of node 3, get instruction for third edge
        assertEquals("3-4", wayList.find(15.099, 9.9, 1000).getName());
    }

    @Test
    public void simpleAnnotations() {
        Graph graph = createTestGraph();

        EnumEncodedValue<RoadEnvironment> roadEnvEnc = carManager.getEnumEncodedValue(RoadEnvironment.KEY, RoadEnvironment.class);
        EnumEncodedValue<RoadAccess> roadAccessEnc = carManager.getEnumEncodedValue(RoadAccess.KEY, RoadAccess.class);
        EdgeIteratorState edge1 = GHUtility.getEdge(graph, 7, 8);
        edge1.set(roadEnvEnc, RoadEnvironment.FERRY);

        EdgeIteratorState edge2 = GHUtility.getEdge(graph, 8, 9);
        edge2.set(roadAccessEnc, RoadAccess.PRIVATE);

        Weighting weighting = new FastestWeighting(carEncoder);
        final InstructionList ways = new InstructionList(usTR);
        InstructionsFromEdges instrFromEdges = new InstructionsFromEdges(graph, weighting, carManager, usTR, ways);
        instrFromEdges.next(edge1, 0, EdgeIterator.NO_EDGE);
        instrFromEdges.next(edge2, 1, edge1.getEdge());
        instrFromEdges.finish();

        assertEquals("take the ferry", ways.get(0).getAnnotation().getMessage());
        assertEquals("private road", ways.get(1).getAnnotation().getMessage());
    }

    private void compare(List<List<Double>> expected, List<List<Double>> actual) {
        for (int i = 0; i < expected.size(); i++) {
            List<Double> e = expected.get(i);
            List<Double> wasE = actual.get(i);
            for (int j = 0; j < e.size(); j++) {
                assertEquals("at index " + i + " value index " + j + " and value " + e + " vs " + wasE + "\n" + "Expected: " + expected + "\n" + "Actual: " + actual
                        , e.get(j), wasE.get(j), 1e-5d);
            }
        }
    }

    private List<Double> asL(Double... list) {
        return Arrays.asList(list);
    }

    private static List<List<Double>> createStartPoints(List<Instruction> instructions) {
        List<List<Double>> res = new ArrayList<>(instructions.size());
        for (Instruction instruction : instructions) {
            res.add(Arrays.asList(instruction.getPoints().getLatitude(0), instruction.getPoints().getLongitude(0)));
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
