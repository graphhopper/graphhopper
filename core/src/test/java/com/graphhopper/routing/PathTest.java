/*
 *  Licensed to GraphHopper and Peter Karich under one or more contributor
 *  license agreements. See the NOTICE file distributed with this work for 
 *  additional information regarding copyright ownership.
 * 
 *  GraphHopper licenses this file to you under the Apache License, 
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

import com.graphhopper.routing.util.*;
import com.graphhopper.storage.*;
import com.graphhopper.util.Helper;

import static com.graphhopper.storage.AbstractGraphStorageTester.*;

import com.graphhopper.util.EdgeIteratorState;
import com.graphhopper.util.Instruction;
import com.graphhopper.util.InstructionList;
import com.graphhopper.storage.EdgeEntry;
import com.graphhopper.util.*;

import java.util.*;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * @author Peter Karich
 */
public class PathTest
{
    private final FlagEncoder encoder = new CarFlagEncoder();
    private final EncodingManager carManager = new EncodingManager(encoder);
    private final EncodingManager mixedEncoders = new EncodingManager(
            new CarFlagEncoder(), new FootFlagEncoder(), new BikeFlagEncoder());
    private final TranslationMap trMap = TranslationMapTest.SINGLETON;
    private final Translation tr = trMap.getWithFallBack(Locale.US);
    private final AngleCalc ac = new AngleCalc();
    private final RoundaboutGraph roundaboutGraph = new RoundaboutGraph();

    @Test
    public void testFound()
    {
        GraphStorage g = new GraphBuilder(carManager).create();
        Path p = new Path(g, encoder);
        assertFalse(p.isFound());
        assertEquals(0, p.getDistance(), 1e-7);
        assertEquals(0, p.calcNodes().size());
        g.close();
    }

    @Test
    public void testTime()
    {
        FlagEncoder tmpEnc = new Bike2WeightFlagEncoder();
        GraphStorage g = new GraphBuilder(new EncodingManager(tmpEnc)).create();
        Path p = new Path(g, tmpEnc);
        long flags = tmpEnc.setSpeed(tmpEnc.setReverseSpeed(tmpEnc.setAccess(0, true, true), 10), 15);
        assertEquals(375 * 60 * 1000, p.calcMillis(100000, flags, false));
        assertEquals(600 * 60 * 1000, p.calcMillis(100000, flags, true));

        g.close();
    }

    @Test
    public void testWayList()
    {
        GraphStorage g = new GraphBuilder(carManager).create();
        NodeAccess na = g.getNodeAccess();
        na.setNode(0, 0.0, 0.1);
        na.setNode(1, 1.0, 0.1);
        na.setNode(2, 2.0, 0.1);

        EdgeIteratorState edge1 = g.edge(0, 1).setDistance(1000).setFlags(encoder.setProperties(10, true, true));
        edge1.setWayGeometry(Helper.createPointList(8, 1, 9, 1));
        EdgeIteratorState edge2 = g.edge(2, 1).setDistance(2000).setFlags(encoder.setProperties(50, true, true));
        edge2.setWayGeometry(Helper.createPointList(11, 1, 10, 1));

        Path path = new Path(g, encoder);
        EdgeEntry e1 = new EdgeEntry(edge2.getEdge(), 2, 1);
        e1.parent = new EdgeEntry(edge1.getEdge(), 1, 1);
        e1.parent.parent = new EdgeEntry(-1, 0, 1);
        path.setEdgeEntry(e1);
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
        path = new Path(g, encoder);
        e1 = new EdgeEntry(edge2.getEdge(), 2, 1);
        e1.parent = new EdgeEntry(edge1.getEdge(), 1, 1);
        e1.parent.parent = new EdgeEntry(-1, 0, 1);
        path.setEdgeEntry(e1);
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
        path = new Path(g, encoder);
        e1 = new EdgeEntry(edge1.getEdge(), 0, 1);
        e1.parent = new EdgeEntry(edge2.getEdge(), 1, 1);
        e1.parent.parent = new EdgeEntry(-1, 2, 1);
        path.setEdgeEntry(e1);
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
    public void testFindInstruction()
    {
        Graph g = new GraphBuilder(carManager).create();
        NodeAccess na = g.getNodeAccess();
        na.setNode(0, 0.0, 0.0);
        na.setNode(1, 5.0, 0.0);
        na.setNode(2, 5.0, 0.5);
        na.setNode(3, 10.0, 0.5);
        na.setNode(4, 7.5, 0.25);

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

        Path path = new Path(g, encoder);
        EdgeEntry e1 = new EdgeEntry(edge4.getEdge(), 4, 1);
        e1.parent = new EdgeEntry(edge3.getEdge(), 3, 1);
        e1.parent.parent = new EdgeEntry(edge2.getEdge(), 2, 1);
        e1.parent.parent.parent = new EdgeEntry(edge1.getEdge(), 1, 1);
        e1.parent.parent.parent.parent = new EdgeEntry(-1, 0, 1);
        path.setEdgeEntry(e1);
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

    private class RoundaboutGraph
    {
        private EdgeIteratorState edge3to6, edge3to9;
        boolean clockwise = false;
        final public Graph g = new GraphBuilder(mixedEncoders).create();
        final public NodeAccess na = g.getNodeAccess();
        List<EdgeIteratorState> roundaboutEdges = new LinkedList<EdgeIteratorState>();

        private RoundaboutGraph()
        {
            //                          
            //      8
            //       \
            //         5
            //       /  \
            //  1 - 2    4 - 7
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


            EdgeIteratorState tmpEdge;
            tmpEdge = g.edge(1, 2, 5, true).setName("MainStreet");

            // roundabout
            tmpEdge = g.edge(3, 2, 5, false).setName("2-3");
            roundaboutEdges.add(tmpEdge.detach(false));
            tmpEdge = g.edge(4, 3, 5, false).setName("3-4");
            roundaboutEdges.add(tmpEdge.detach(false));
            tmpEdge = g.edge(5, 4, 5, false).setName("4-5");
            roundaboutEdges.add(tmpEdge.detach(false));
            tmpEdge = g.edge(2, 5, 5, false).setName("5-2");
            roundaboutEdges.add(tmpEdge.detach(false));

            tmpEdge = g.edge(4, 7, 5, true).setName("MainStreet");
            tmpEdge = g.edge(5, 8, 5, true).setName("5-8");

            tmpEdge = g.edge(3, 6, 5, true).setName("3-6");
            edge3to6 = tmpEdge.detach(false);

            tmpEdge = g.edge(3, 9, 5, false).setName("3-9");
            edge3to9 = tmpEdge.detach(false);

            setRoundabout(clockwise);
            inverse3to9();

        }

        public void setRoundabout( boolean clockwise )
        {
            for (FlagEncoder encoder : mixedEncoders.fetchEdgeEncoders())
            {
                for (EdgeIteratorState edge : roundaboutEdges)
                {
                    edge.setFlags(encoder.setAccess(edge.getFlags(), clockwise, !clockwise));
                    edge.setFlags(encoder.setBool(edge.getFlags(), encoder.K_ROUNDABOUT, true));
                }
            }
            this.clockwise = clockwise;
        }

        public void inverse3to9()
        {
            for (FlagEncoder encoder : mixedEncoders.fetchEdgeEncoders())
            {
                long flags = edge3to9.getFlags();
                edge3to9.setFlags(encoder.setAccess(flags, !encoder.isForward(flags), false));
            }
        }

        public void inverse3to6()
        {
            for (FlagEncoder encoder : mixedEncoders.fetchEdgeEncoders())
            {
                long flags = edge3to6.getFlags();
                edge3to6.setFlags(encoder.setAccess(flags, !encoder.isForward(flags), true));
            }
        }


        private double getAngle( int n1, int n2, int n3, int n4 )
        {
            double inOrientation = ac.calcOrientation(na.getLat(n1), na.getLon(n1), na.getLat(n2), na.getLon(n2));
            double outOrientation = ac.calcOrientation(na.getLat(n3), na.getLon(n3), na.getLat(n4), na.getLon(n4));
            outOrientation = ac.alignOrientation(inOrientation, outOrientation);
            double delta = (inOrientation - outOrientation);
            delta = clockwise ? (Math.PI + delta) : -1 * (Math.PI - delta);
            return delta;
        }
    }

    /**
     * Test roundabout instructions for different profiles
     */
    @Test
    public void testCalcInstructionsRoundabout()
    {
        for (FlagEncoder encoder : mixedEncoders.fetchEdgeEncoders())
        {
            Path p = new Dijkstra(roundaboutGraph.g, encoder, new ShortestWeighting(), TraversalMode.NODE_BASED)
                    .calcPath(1, 8);
            InstructionList wayList = p.calcInstructions(tr);
            // Test instructions
            List<String> tmpList = pick("text", wayList.createJson());
            assertEquals(Arrays.asList("Continue onto MainStreet",
                            "At roundabout, take exit 3 onto 5-8",
                            "Finish!"),
                    tmpList);
            // Test Radian
            double delta = roundaboutGraph.getAngle(1, 2, 5, 8);
            RoundaboutInstruction instr = (RoundaboutInstruction) wayList.get(1);
            assertEquals(delta, instr.getRadian(), 0.01);

            // case of continuing a street through a roundabout
            p = new Dijkstra(roundaboutGraph.g, encoder, new ShortestWeighting(), TraversalMode.NODE_BASED).calcPath(1, 7);
            wayList = p.calcInstructions(tr);
            tmpList = pick("text", wayList.createJson());
            assertEquals(Arrays.asList("Continue onto MainStreet",
                            "At roundabout, take exit 2 onto MainStreet",
                            "Finish!"),
                    tmpList);
            // Test Radian
            delta = roundaboutGraph.getAngle(1, 2, 4, 7);
            instr = (RoundaboutInstruction) wayList.get(1);
            assertEquals(delta, instr.getRadian(), 0.01);
        }
    }

    /**
     * case starting in Roundabout
     */
    @Test
    public void testCalcInstructionsRoundaboutBegin()
    {
        Path p = new Dijkstra(roundaboutGraph.g, encoder, new ShortestWeighting(), TraversalMode.NODE_BASED)
                .calcPath(2, 8);
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
    public void testCalcInstructionsRoundaboutDirectExit()
    {
        roundaboutGraph.inverse3to9();
        Path p = new Dijkstra(roundaboutGraph.g, encoder, new ShortestWeighting(), TraversalMode.NODE_BASED)
                .calcPath(6, 8);
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
    public void testCalcInstructionsRoundabout2()
    {
        roundaboutGraph.inverse3to6();
        Path p = new Dijkstra(roundaboutGraph.g, encoder, new ShortestWeighting(), TraversalMode.NODE_BASED)
                .calcPath(1, 8);
        InstructionList wayList = p.calcInstructions(tr);
        List<String> tmpList = pick("text", wayList.createJson());
        assertEquals(Arrays.asList("Continue onto MainStreet",
                        "At roundabout, take exit 2 onto 5-8",
                        "Finish!"),
                tmpList);
        // Test Radian
        double delta = roundaboutGraph.getAngle(1, 2, 5, 8);
        RoundaboutInstruction instr = (RoundaboutInstruction) wayList.get(1);
        assertEquals(delta, instr.getRadian(), 0.01);
        roundaboutGraph.inverse3to6();

    }


    /**
     * see https://github.com/graphhopper/graphhopper/issues/353
     */
    @Test
    public void testCalcInstructionsRoundaboutIssue353()
    {
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

        // Sidelane
        na.setNode(9, 52.5135, 13.349);
        na.setNode(10, 52.5135, 13.348);
        na.setNode(11, 52.514, 13.347);


        EdgeIteratorState tmpEdge;
        tmpEdge = g.edge(2, 1, 5, false).setName("MainStreet");
        tmpEdge = g.edge(1, 11, 5, false).setName("MainStreet");


        // roundabout
        tmpEdge = g.edge(3, 9, 2, false).setName("3-9");
        tmpEdge.setFlags(encoder.setBool(tmpEdge.getFlags(), encoder.K_ROUNDABOUT, true));
        tmpEdge = g.edge(9, 10, 2, false).setName("9-10");
        tmpEdge.setFlags(encoder.setBool(tmpEdge.getFlags(), encoder.K_ROUNDABOUT, true));
        tmpEdge = g.edge(6, 10, 2, false).setName("6-10");
        tmpEdge.setFlags(encoder.setBool(tmpEdge.getFlags(), encoder.K_ROUNDABOUT, true));
        tmpEdge = g.edge(10, 1, 2, false).setName("10-1");
        tmpEdge.setFlags(encoder.setBool(tmpEdge.getFlags(), encoder.K_ROUNDABOUT, true));
        tmpEdge = g.edge(3, 2, 5, false).setName("2-3");
        tmpEdge.setFlags(encoder.setBool(tmpEdge.getFlags(), encoder.K_ROUNDABOUT, true));
        tmpEdge = g.edge(4, 3, 5, false).setName("3-4");
        tmpEdge.setFlags(encoder.setBool(tmpEdge.getFlags(), encoder.K_ROUNDABOUT, true));
        tmpEdge = g.edge(5, 4, 5, false).setName("4-5");
        tmpEdge.setFlags(encoder.setBool(tmpEdge.getFlags(), encoder.K_ROUNDABOUT, true));
        tmpEdge = g.edge(2, 5, 5, false).setName("5-2");
        tmpEdge.setFlags(encoder.setBool(tmpEdge.getFlags(), encoder.K_ROUNDABOUT, true));

        tmpEdge = g.edge(4, 7, 5, true).setName("MainStreet");
        tmpEdge = g.edge(5, 8, 5, true).setName("5-8");
        tmpEdge = g.edge(3, 6, 5, true).setName("3-6");


        Path p = new Dijkstra(g, encoder, new ShortestWeighting(), TraversalMode.NODE_BASED)
                .calcPath(6, 11);
        InstructionList wayList = p.calcInstructions(tr);
        List<String> tmpList = pick("text", wayList.createJson());
        assertEquals(Arrays.asList("At roundabout, take exit 1 onto MainStreet",
                        "Finish!"),
                tmpList);
    }

    /**
     * clockwise roundabout
     */
    @Test
    public void testCalcInstructionsRoundaboutClockwise()
    {

        roundaboutGraph.setRoundabout(true);
        Path p = new Dijkstra(roundaboutGraph.g, encoder, new ShortestWeighting(), TraversalMode.NODE_BASED)
                .calcPath(1, 8);
        InstructionList wayList = p.calcInstructions(tr);
        List<String> tmpList = pick("text", wayList.createJson());
        assertEquals(Arrays.asList("Continue onto MainStreet",
                        "At roundabout, take exit 1 onto 5-8",
                        "Finish!"),
                tmpList);
        // Test Radian
        double delta = roundaboutGraph.getAngle(1, 2, 5, 8);
        RoundaboutInstruction instr = (RoundaboutInstruction) wayList.get(1);
        assertEquals(delta, instr.getRadian(), 0.01);
    }

    List<String> pick( String key, List<Map<String, Object>> instructionJson )
    {
        List<String> list = new ArrayList<String>();

        for (Map<String, Object> json : instructionJson)
        {
            list.add(json.get(key).toString());
        }
        return list;
    }

}
