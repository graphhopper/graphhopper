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

import com.graphhopper.reader.ReaderWay;
import com.graphhopper.routing.util.*;
import com.graphhopper.routing.weighting.FastestWeighting;
import com.graphhopper.routing.weighting.GenericWeighting;
import com.graphhopper.routing.weighting.ShortestWeighting;
import com.graphhopper.storage.*;
import com.graphhopper.util.*;
import org.junit.Test;

import java.util.*;

import static com.graphhopper.storage.AbstractGraphStorageTester.assertPList;
import static org.junit.Assert.*;

/**
 * @author Peter Karich
 */
public class PathTest {
    private final FlagEncoder encoder = new CarFlagEncoder();
    private final DataFlagEncoder dataFlagEncoder = new DataFlagEncoder();
    private final EncodingManager carManager = new EncodingManager(encoder);
    private final EncodingManager dataFlagManager = new EncodingManager(dataFlagEncoder);
    private final EncodingManager mixedEncoders = new EncodingManager(new CarFlagEncoder());
    private final TranslationMap trMap = TranslationMapTest.SINGLETON;
    private final Translation tr = trMap.getWithFallBack(Locale.US);
    private final RoundaboutGraph roundaboutGraph = new RoundaboutGraph();

    @Test
    public void testFound() {
        GraphHopperStorage g = new GraphBuilder(carManager).create();
        Path p = new Path(g, new FastestWeighting(encoder));
        assertFalse(p.isFound());
        assertEquals(0, p.getDistance(), 1e-7);
        assertEquals(0, p.calcNodes().size());
        g.close();
    }

    @Test
    public void testWayList() {
        GraphHopperStorage g = new GraphBuilder(carManager).create();
        NodeAccess na = g.getNodeAccess();
        na.setNode(0, 0.0, 0.1);
        na.setNode(1, 1.0, 0.1);
        na.setNode(2, 2.0, 0.1);

        EdgeIteratorState edge1 = g.edge(0, 1).setDistance(1000).setFlags(encoder.setProperties(10, true, true));
        edge1.setWayGeometry(Helper.createPointList(8, 1, 9, 1));
        EdgeIteratorState edge2 = g.edge(2, 1).setDistance(2000).setFlags(encoder.setProperties(50, true, true));
        edge2.setWayGeometry(Helper.createPointList(11, 1, 10, 1));

        Path path = new Path(g, new FastestWeighting(encoder));
        SPTEntry e1 = new SPTEntry(edge2.getEdge(), 2, 1);
        e1.parent = new SPTEntry(edge1.getEdge(), 1, 1);
        e1.parent.parent = new SPTEntry(-1, 0, 1);
        path.setSPTEntry(e1);
        path.extract();
        // 0-1-2
        assertPList(Helper.createPointList(0, 0.1, 8, 1, 9, 1, 1, 0.1, 10, 1, 11, 1, 2, 0.1), path.calcPoints());
        InstructionList instr = path.calcInstructions(tr);
        List<Map<String, Object>> res = instr.createJson();
        Map<String, Object> tmp = res.get(0);
        assertEquals(3000.0, tmp.get("distance"));
        assertEquals(504000L, tmp.get("time"));
        assertEquals("Continue", tmp.get("text"));
        assertEquals("[0, 6]", tmp.get("interval").toString());

        tmp = res.get(1);
        assertEquals(0.0, tmp.get("distance"));
        assertEquals(0L, tmp.get("time"));
        assertEquals("Finish!", tmp.get("text"));
        assertEquals("[6, 6]", tmp.get("interval").toString());
        int lastIndex = (Integer) ((List) res.get(res.size() - 1).get("interval")).get(0);
        assertEquals(path.calcPoints().size() - 1, lastIndex);

        // force minor change for instructions
        edge2.setName("2");
        na.setNode(3, 1.0, 1.0);
        EdgeIteratorState edge3 = g.edge(1, 3).setDistance(1000).setFlags(encoder.setProperties(10, true, true));

        path = new Path(g, new FastestWeighting(encoder));
        e1 = new SPTEntry(edge2.getEdge(), 2, 1);
        e1.parent = new SPTEntry(edge1.getEdge(), 1, 1);
        e1.parent.parent = new SPTEntry(-1, 0, 1);
        path.setSPTEntry(e1);
        path.extract();
        instr = path.calcInstructions(tr);
        res = instr.createJson();

        tmp = res.get(0);
        assertEquals(1000.0, tmp.get("distance"));
        assertEquals(360000L, tmp.get("time"));
        assertEquals("Continue", tmp.get("text"));
        assertEquals("[0, 3]", tmp.get("interval").toString());

        tmp = res.get(1);
        assertEquals(2000.0, tmp.get("distance"));
        assertEquals(144000L, tmp.get("time"));
        assertEquals("Turn sharp right onto 2", tmp.get("text"));
        assertEquals("[3, 6]", tmp.get("interval").toString());
        lastIndex = (Integer) ((List) res.get(res.size() - 1).get("interval")).get(0);
        assertEquals(path.calcPoints().size() - 1, lastIndex);

        // now reverse order
        path = new Path(g, new FastestWeighting(encoder));
        e1 = new SPTEntry(edge1.getEdge(), 0, 1);
        e1.parent = new SPTEntry(edge2.getEdge(), 1, 1);
        e1.parent.parent = new SPTEntry(-1, 2, 1);
        path.setSPTEntry(e1);
        path.extract();
        // 2-1-0
        assertPList(Helper.createPointList(2, 0.1, 11, 1, 10, 1, 1, 0.1, 9, 1, 8, 1, 0, 0.1), path.calcPoints());

        instr = path.calcInstructions(tr);
        res = instr.createJson();
        tmp = res.get(0);
        assertEquals(2000.0, tmp.get("distance"));
        assertEquals(144000L, tmp.get("time"));
        assertEquals("Continue onto 2", tmp.get("text"));
        assertEquals("[0, 3]", tmp.get("interval").toString());

        tmp = res.get(1);
        assertEquals(1000.0, tmp.get("distance"));
        assertEquals(360000L, tmp.get("time"));
        assertEquals("Turn sharp left", tmp.get("text"));
        assertEquals("[3, 6]", tmp.get("interval").toString());
        lastIndex = (Integer) ((List) res.get(res.size() - 1).get("interval")).get(0);
        assertEquals(path.calcPoints().size() - 1, lastIndex);
    }

    @Test
    public void testFindInstruction() {
        Graph g = new GraphBuilder(carManager).create();
        NodeAccess na = g.getNodeAccess();
        na.setNode(0, 0.0, 0.0);
        na.setNode(1, 5.0, 0.0);
        na.setNode(2, 5.0, 0.5);
        na.setNode(3, 10.0, 0.5);
        na.setNode(4, 7.5, 0.25);
        na.setNode(5, 5.0, 1.0);


        EdgeIteratorState edge1 = g.edge(0, 1).setDistance(1000).setFlags(encoder.setProperties(50, true, true));
        edge1.setWayGeometry(Helper.createPointList());
        edge1.setName("Street 1");
        EdgeIteratorState edge2 = g.edge(1, 2).setDistance(1000).setFlags(encoder.setProperties(50, true, true));
        edge2.setWayGeometry(Helper.createPointList());
        edge2.setName("Street 2");
        EdgeIteratorState edge3 = g.edge(2, 3).setDistance(1000).setFlags(encoder.setProperties(50, true, true));
        edge3.setWayGeometry(Helper.createPointList());
        edge3.setName("Street 3");
        EdgeIteratorState edge4 = g.edge(3, 4).setDistance(500).setFlags(encoder.setProperties(50, true, true));
        edge4.setWayGeometry(Helper.createPointList());
        edge4.setName("Street 4");

        g.edge(1, 5).setDistance(10000).setFlags(encoder.setProperties(50, true, true));
        g.edge(2, 5).setDistance(10000).setFlags(encoder.setProperties(50, true, true));
        g.edge(3, 5).setDistance(100000).setFlags(encoder.setProperties(50, true, true));

        Path path = new Path(g, new FastestWeighting(encoder));
        SPTEntry e1 = new SPTEntry(edge4.getEdge(), 4, 1);
        e1.parent = new SPTEntry(edge3.getEdge(), 3, 1);
        e1.parent.parent = new SPTEntry(edge2.getEdge(), 2, 1);
        e1.parent.parent.parent = new SPTEntry(edge1.getEdge(), 1, 1);
        e1.parent.parent.parent.parent = new SPTEntry(-1, 0, 1);
        path.setSPTEntry(e1);
        path.extract();

        InstructionList il = path.calcInstructions(tr);
        Instruction nextInstr0 = il.find(-0.001, 0.0, 1000);
        assertEquals(Instruction.CONTINUE_ON_STREET, nextInstr0.getSign());

        Instruction nextInstr1 = il.find(0.001, 0.001, 1000);
        assertEquals(Instruction.TURN_RIGHT, nextInstr1.getSign());

        Instruction nextInstr2 = il.find(5.0, 0.004, 1000);
        assertEquals(Instruction.TURN_LEFT, nextInstr2.getSign());

        Instruction nextInstr3 = il.find(9.99, 0.503, 1000);
        assertEquals(Instruction.TURN_SHARP_LEFT, nextInstr3.getSign());

        // a bit far away ...
        Instruction nextInstr4 = il.find(7.40, 0.25, 20000);
        assertEquals(Instruction.FINISH, nextInstr4.getSign());

        // too far away
        assertNull(il.find(50.8, 50.25, 1000));
    }

    /**
     * Test roundabout instructions for different profiles
     */
    @Test
    public void testCalcInstructionsRoundabout() {
        for (FlagEncoder encoder : mixedEncoders.fetchEdgeEncoders()) {
            Path p = new Dijkstra(roundaboutGraph.g, new ShortestWeighting(encoder), TraversalMode.NODE_BASED)
                    .calcPath(1, 8);
            assertTrue(p.isFound());
            InstructionList wayList = p.calcInstructions(tr);
            // Test instructions
            List<String> tmpList = pick("text", wayList.createJson());
            assertEquals(Arrays.asList("Continue onto MainStreet 1 2",
                    "At roundabout, take exit 3 onto 5-8",
                    "Finish!"),
                    tmpList);
            // Test Radian
            double delta = roundaboutGraph.getAngle(1, 2, 5, 8);
            RoundaboutInstruction instr = (RoundaboutInstruction) wayList.get(1);
            assertEquals(delta, instr.getTurnAngle(), 0.01);

            // case of continuing a street through a roundabout
            p = new Dijkstra(roundaboutGraph.g, new ShortestWeighting(encoder), TraversalMode.NODE_BASED).
                    calcPath(1, 7);
            wayList = p.calcInstructions(tr);
            tmpList = pick("text", wayList.createJson());
            assertEquals(Arrays.asList("Continue onto MainStreet 1 2",
                    "At roundabout, take exit 2 onto MainStreet 4 7",
                    "Finish!"),
                    tmpList);
            // Test Radian
            delta = roundaboutGraph.getAngle(1, 2, 4, 7);
            instr = (RoundaboutInstruction) wayList.get(1);
            assertEquals(delta, instr.getTurnAngle(), 0.01);
        }
    }

    /**
     * case starting in Roundabout
     */
    @Test
    public void testCalcInstructionsRoundaboutBegin() {
        Path p = new Dijkstra(roundaboutGraph.g, new ShortestWeighting(encoder), TraversalMode.NODE_BASED)
                .calcPath(2, 8);
        assertTrue(p.isFound());
        InstructionList wayList = p.calcInstructions(tr);
        List<String> tmpList = pick("text", wayList.createJson());
        assertEquals(Arrays.asList("At roundabout, take exit 3 onto 5-8",
                "Finish!"),
                tmpList);
    }

    /**
     * case with one node being containig already exit
     */
    @Test
    public void testCalcInstructionsRoundaboutDirectExit() {
        roundaboutGraph.inverse3to9();
        Path p = new Dijkstra(roundaboutGraph.g, new ShortestWeighting(encoder), TraversalMode.NODE_BASED)
                .calcPath(6, 8);
        assertTrue(p.isFound());
        InstructionList wayList = p.calcInstructions(tr);
        List<String> tmpList = pick("text", wayList.createJson());
        assertEquals(Arrays.asList("Continue onto 3-6",
                "At roundabout, take exit 3 onto 5-8",
                "Finish!"),
                tmpList);
        roundaboutGraph.inverse3to9();
    }

    /**
     * case with one edge being not an exit
     */
    @Test
    public void testCalcInstructionsRoundabout2() {
        roundaboutGraph.inverse3to6();
        Path p = new Dijkstra(roundaboutGraph.g, new ShortestWeighting(encoder), TraversalMode.NODE_BASED)
                .calcPath(1, 8);
        assertTrue(p.isFound());
        InstructionList wayList = p.calcInstructions(tr);
        List<String> tmpList = pick("text", wayList.createJson());
        assertEquals(Arrays.asList("Continue onto MainStreet 1 2",
                "At roundabout, take exit 2 onto 5-8",
                "Finish!"),
                tmpList);
        // Test Radian
        double delta = roundaboutGraph.getAngle(1, 2, 5, 8);
        RoundaboutInstruction instr = (RoundaboutInstruction) wayList.get(1);
        assertEquals(delta, instr.getTurnAngle(), 0.01);
        roundaboutGraph.inverse3to6();
    }

    @Test
    public void testCalcInstructionsRoundaboutIssue353() {
        final Graph g = new GraphBuilder(carManager).create();
        final NodeAccess na = g.getNodeAccess();

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

        g.edge(2, 1, 5, false).setName("MainStreet 2 1");
        g.edge(1, 11, 5, false).setName("MainStreet 1 11");

        // roundabout
        EdgeIteratorState tmpEdge;
        tmpEdge = g.edge(3, 9, 2, false).setName("3-9");
        tmpEdge.setFlags(encoder.setBool(tmpEdge.getFlags(), FlagEncoder.K_ROUNDABOUT, true));
        tmpEdge = g.edge(9, 10, 2, false).setName("9-10");
        tmpEdge.setFlags(encoder.setBool(tmpEdge.getFlags(), FlagEncoder.K_ROUNDABOUT, true));
        tmpEdge = g.edge(6, 10, 2, false).setName("6-10");
        tmpEdge.setFlags(encoder.setBool(tmpEdge.getFlags(), FlagEncoder.K_ROUNDABOUT, true));
        tmpEdge = g.edge(10, 1, 2, false).setName("10-1");
        tmpEdge.setFlags(encoder.setBool(tmpEdge.getFlags(), FlagEncoder.K_ROUNDABOUT, true));
        tmpEdge = g.edge(3, 2, 5, false).setName("2-3");
        tmpEdge.setFlags(encoder.setBool(tmpEdge.getFlags(), FlagEncoder.K_ROUNDABOUT, true));
        tmpEdge = g.edge(4, 3, 5, false).setName("3-4");
        tmpEdge.setFlags(encoder.setBool(tmpEdge.getFlags(), FlagEncoder.K_ROUNDABOUT, true));
        tmpEdge = g.edge(5, 4, 5, false).setName("4-5");
        tmpEdge.setFlags(encoder.setBool(tmpEdge.getFlags(), FlagEncoder.K_ROUNDABOUT, true));
        tmpEdge = g.edge(2, 5, 5, false).setName("5-2");
        tmpEdge.setFlags(encoder.setBool(tmpEdge.getFlags(), FlagEncoder.K_ROUNDABOUT, true));

        g.edge(4, 7, 5, true).setName("MainStreet 4 7");
        g.edge(5, 8, 5, true).setName("5-8");
        g.edge(3, 6, 5, true).setName("3-6");

        Path p = new Dijkstra(g, new ShortestWeighting(encoder), TraversalMode.NODE_BASED)
                .calcPath(6, 11);
        assertTrue(p.isFound());
        InstructionList wayList = p.calcInstructions(tr);
        List<String> tmpList = pick("text", wayList.createJson());
        assertEquals(Arrays.asList("At roundabout, take exit 1 onto MainStreet 1 11",
                "Finish!"),
                tmpList);
    }

    /**
     * clockwise roundabout
     */
    @Test
    public void testCalcInstructionsRoundaboutClockwise() {
        roundaboutGraph.setRoundabout(true);
        Path p = new Dijkstra(roundaboutGraph.g, new ShortestWeighting(encoder), TraversalMode.NODE_BASED)
                .calcPath(1, 8);
        assertTrue(p.isFound());
        InstructionList wayList = p.calcInstructions(tr);
        List<String> tmpList = pick("text", wayList.createJson());
        assertEquals(Arrays.asList("Continue onto MainStreet 1 2",
                "At roundabout, take exit 1 onto 5-8",
                "Finish!"),
                tmpList);
        // Test Radian
        double delta = roundaboutGraph.getAngle(1, 2, 5, 8);
        RoundaboutInstruction instr = (RoundaboutInstruction) wayList.get(1);
        assertEquals(delta, instr.getTurnAngle(), 0.01);
    }

    List<String> pick(String key, List<Map<String, Object>> instructionJson) {
        List<String> list = new ArrayList<String>();

        for (Map<String, Object> json : instructionJson) {
            list.add(json.get(key).toString());
        }
        return list;
    }

    @Test
    public void testCalcInstructionsIgnoreContinue() {
        // Follow a couple of straight edges, including a name change
        Path p = new Dijkstra(roundaboutGraph.g, new ShortestWeighting(encoder), TraversalMode.NODE_BASED)
                .calcPath(4, 11);
        assertTrue(p.isFound());
        InstructionList wayList = p.calcInstructions(tr);

        // Contain only start and finish instruction, no CONTINUE
        assertEquals(2, wayList.size());
    }

    @Test
    public void testCalcInstructionsIgnoreTurnIfNoAlternative() {
        // The street turns left, but there is not turn
        Path p = new Dijkstra(roundaboutGraph.g, new ShortestWeighting(encoder), TraversalMode.NODE_BASED)
                .calcPath(10, 12);
        assertTrue(p.isFound());
        InstructionList wayList = p.calcInstructions(tr);

        // Contain only start and finish instruction
        assertEquals(2, wayList.size());
    }

    @Test
    public void testCalcInstructionForForkWithSameName() {
        final Graph g = new GraphBuilder(carManager).create();
        final NodeAccess na = g.getNodeAccess();

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

        g.edge(1, 2, 5, true).setName("Regener Weg");
        g.edge(2, 4, 5, true).setName("Regener Weg");
        g.edge(2, 3, 5, true);

        Path p = new Dijkstra(g, new ShortestWeighting(encoder), TraversalMode.NODE_BASED)
                .calcPath(1, 4);
        assertTrue(p.isFound());
        InstructionList wayList = p.calcInstructions(tr);

        assertEquals(3, wayList.size());
        assertEquals(-1, wayList.get(1).getSign());
    }

    @Test
    public void testCalcInstructionContinueLeavingStreet() {
        final Graph g = new GraphBuilder(carManager).create();
        final NodeAccess na = g.getNodeAccess();

        // When leaving the current street via a Continue, we should show it
        //       3
        //        \
        //     4 - 2   --  1
        na.setNode(1, 48.982618, 13.122021);
        na.setNode(2, 48.982565, 13.121597);
        na.setNode(3, 48.982611, 13.121012);
        na.setNode(4, 48.982565, 13.121002);

        g.edge(1, 2, 5, true).setName("Regener Weg");
        g.edge(2, 4, 5, true);
        g.edge(2, 3, 5, true).setName("Regener Weg");

        Path p = new Dijkstra(g, new ShortestWeighting(encoder), TraversalMode.NODE_BASED)
                .calcPath(1, 4);
        assertTrue(p.isFound());
        InstructionList wayList = p.calcInstructions(tr);

        assertEquals(3, wayList.size());
        assertEquals(-1, wayList.get(1).getSign());
    }

    @Test
    public void testCalcInstructionSlightTurn() {
        final Graph g = new GraphBuilder(carManager).create();
        final NodeAccess na = g.getNodeAccess();

        // Real Situation: point=48.411927%2C15.599197&point=48.412094%2C15.598816
        // When reaching this Crossing, you cannot know if you should turn left or right
        // Google Maps and Bing show a turn, OSRM does not
        //  1 ---2--- 3
        //       \
        //        4
        na.setNode(1, 48.412094,15.598816);
        na.setNode(2, 48.412055,15.599068);
        na.setNode(3, 48.412034,15.599411);
        na.setNode(4, 48.411927,15.599197);

        g.edge(1, 2, 5, true).setName("Stöhrgasse");
        g.edge(2, 3, 5, true);
        g.edge(2, 4, 5, true).setName("Stöhrgasse");

        Path p = new Dijkstra(g, new ShortestWeighting(encoder), TraversalMode.NODE_BASED)
                .calcPath(4, 1);
        assertTrue(p.isFound());
        InstructionList wayList = p.calcInstructions(tr);

        assertEquals(3, wayList.size());
        assertEquals(-1, wayList.get(1).getSign());
    }

    @Test
    public void testCalcInstructionsForTurn() {
        // The street turns left, but there is not turn
        Path p = new Dijkstra(roundaboutGraph.g, new ShortestWeighting(encoder), TraversalMode.NODE_BASED)
                .calcPath(11, 13);
        assertTrue(p.isFound());
        InstructionList wayList = p.calcInstructions(tr);

        // Contain start, turn, and finish instruction
        assertEquals(3, wayList.size());
        // Assert turn right
        assertEquals(2, wayList.get(1).getSign());
    }

    @Test
    public void testCalcInstructionsForDataFlagEncoder() {
        final Graph g = new GraphBuilder(dataFlagManager).create();
        final NodeAccess na = g.getNodeAccess();

        na.setNode(1, 48.982618, 13.122021);
        na.setNode(2, 48.982565, 13.121597);
        na.setNode(3, 48.982611, 13.121012);
        na.setNode(4, 48.982336, 13.121002);

        ReaderWay w = new ReaderWay(1);
        w.setTag("highway", "tertiary");

        g.edge(1, 2, 5, true).setFlags(dataFlagEncoder.handleWayTags(w,1,0));
        g.edge(2, 4, 5, true).setFlags(dataFlagEncoder.handleWayTags(w,1,0));
        g.edge(2, 3, 5, true).setFlags(dataFlagEncoder.handleWayTags(w,1,0));

        ConfigMap cMap = dataFlagEncoder.readStringMap(new PMap());
        Path p = new Dijkstra(g, new GenericWeighting(dataFlagEncoder, cMap), TraversalMode.NODE_BASED).calcPath(1, 3);
        assertTrue(p.isFound());
        InstructionList wayList = p.calcInstructions(tr);
        assertEquals(3, wayList.size());
    }

    @Test
    public void testCalcInstructionsForSlightTurnWithOtherSlightTurn() {
        // Test for a fork with two sligh turns. Since there are two sligh turns, show the turn instruction
        Path p = new Dijkstra(roundaboutGraph.g, new ShortestWeighting(encoder), TraversalMode.NODE_BASED)
                .calcPath(12, 16);
        assertTrue(p.isFound());
        InstructionList wayList = p.calcInstructions(tr);

        // Contain start, turn, and finish instruction
        assertEquals(3, wayList.size());
        // Assert turn right
        assertEquals(1, wayList.get(1).getSign());
    }

    @Test
    public void testCalcInstructionsForSlightTurnOntoDifferentStreet() {
        final Graph g = new GraphBuilder(carManager).create();
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

        g.edge(1, 3, 5, true).setName("Talstraße, K 4313");
        g.edge(2, 3, 5, true).setName("Calmbacher Straße, K 4312");
        g.edge(3, 4, 5, true).setName("Calmbacher Straße, K 4312");

        Path p = new Dijkstra(g, new ShortestWeighting(encoder), TraversalMode.NODE_BASED)
                .calcPath(1, 2);
        assertTrue(p.isFound());
        InstructionList wayList = p.calcInstructions(tr);

        assertEquals(3, wayList.size());
        assertEquals(Instruction.TURN_SLIGHT_RIGHT, wayList.get(1).getSign());
    }

    @Test
    public void testIgnoreInstructionsForSlightTurnWithOtherTurn() {
        // Test for a fork with one sligh turn and one actual turn. We are going along the slight turn. No turn instruction needed in this case
        Path p = new Dijkstra(roundaboutGraph.g, new ShortestWeighting(encoder), TraversalMode.NODE_BASED)
                .calcPath(16, 19);
        assertTrue(p.isFound());
        InstructionList wayList = p.calcInstructions(tr);

        // Contain start, and finish instruction
        assertEquals(2, wayList.size());
    }

    private class RoundaboutGraph {
        final public Graph g = new GraphBuilder(mixedEncoders).create();
        final public NodeAccess na = g.getNodeAccess();
        private final EdgeIteratorState edge3to6, edge3to9;
        boolean clockwise = false;
        List<EdgeIteratorState> roundaboutEdges = new LinkedList<EdgeIteratorState>();

        private RoundaboutGraph() {
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

            g.edge(1, 2, 5, true).setName("MainStreet 1 2");

            // roundabout
            roundaboutEdges.add(g.edge(3, 2, 5, false).setName("2-3"));
            roundaboutEdges.add(g.edge(4, 3, 5, false).setName("3-4"));
            roundaboutEdges.add(g.edge(5, 4, 5, false).setName("4-5"));
            roundaboutEdges.add(g.edge(2, 5, 5, false).setName("5-2"));

            g.edge(4, 7, 5, true).setName("MainStreet 4 7");
            g.edge(5, 8, 5, true).setName("5-8");

            edge3to6 = g.edge(3, 6, 5, true).setName("3-6");
            edge3to9 = g.edge(3, 9, 5, false).setName("3-9");

            // Don't set names
            g.edge(7, 10, 5, true);
            g.edge(10, 11, 5, true);
            g.edge(11, 12, 5, true);
            g.edge(12, 13, 5, true);
            g.edge(12, 14, 5, true);
            g.edge(13, 15, 5, true);
            g.edge(13, 16, 5, true);
            g.edge(16, 17, 5, true);
            g.edge(17, 18, 5, true);
            g.edge(17, 19, 5, true);


            setRoundabout(clockwise);
            inverse3to9();

        }

        public void setRoundabout(boolean clockwise) {
            for (FlagEncoder encoder : mixedEncoders.fetchEdgeEncoders()) {
                for (EdgeIteratorState edge : roundaboutEdges) {
                    edge.setFlags(encoder.setAccess(edge.getFlags(), clockwise, !clockwise));
                    edge.setFlags(encoder.setBool(edge.getFlags(), FlagEncoder.K_ROUNDABOUT, true));
                }
            }
            this.clockwise = clockwise;
        }

        public void inverse3to9() {
            for (FlagEncoder encoder : mixedEncoders.fetchEdgeEncoders()) {
                long flags = edge3to9.getFlags();
                edge3to9.setFlags(encoder.setAccess(flags, !edge3to9.isForward(encoder), false));
            }
        }

        public void inverse3to6() {
            for (FlagEncoder encoder : mixedEncoders.fetchEdgeEncoders()) {
                long flags = edge3to6.getFlags();
                edge3to6.setFlags(encoder.setAccess(flags, !edge3to6.isForward(encoder), true));
            }
        }

        private double getAngle(int n1, int n2, int n3, int n4) {
            double inOrientation = Helper.ANGLE_CALC.calcOrientation(na.getLat(n1), na.getLon(n1), na.getLat(n2), na.getLon(n2));
            double outOrientation = Helper.ANGLE_CALC.calcOrientation(na.getLat(n3), na.getLon(n3), na.getLat(n4), na.getLon(n4));
            outOrientation = Helper.ANGLE_CALC.alignOrientation(inOrientation, outOrientation);
            double delta = (inOrientation - outOrientation);
            delta = clockwise ? (Math.PI + delta) : -1 * (Math.PI - delta);
            return delta;
        }
    }

}
