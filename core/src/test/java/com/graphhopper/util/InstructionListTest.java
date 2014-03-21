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
package com.graphhopper.util;

import com.graphhopper.reader.OSMWay;
import com.graphhopper.routing.Dijkstra;
import com.graphhopper.routing.Path;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.routing.util.ShortestWeighting;
import com.graphhopper.storage.Graph;
import com.graphhopper.storage.GraphBuilder;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 *
 * @author Peter Karich
 */
public class InstructionListTest
{
    TranslationMap trMap = TranslationMapTest.SINGLETON;

    @Test
    public void testWayList()
    {
        EncodingManager carManager = new EncodingManager("CAR");
        Graph g = new GraphBuilder(carManager).create();
        // 0-1-2
        // | | |
        // 3-4-5  9-10
        // | | |  |
        // 6-7-8--*
        g.setNode(0, 1.2, 1.0);
        g.setNode(1, 1.2, 1.1);
        g.setNode(2, 1.2, 1.2);
        g.setNode(3, 1.1, 1.0);
        g.setNode(4, 1.1, 1.1);
        g.setNode(5, 1.1, 1.2);
        g.setNode(9, 1.1, 1.3);
        g.setNode(10, 1.1, 1.4);

        g.setNode(6, 1.0, 1.0);
        g.setNode(7, 1.0, 1.1);
        g.setNode(8, 1.0, 1.2);
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

        Path p = new Dijkstra(g, carManager.getEncoder("CAR"), new ShortestWeighting()).calcPath(0, 10);
        InstructionList wayList = p.calcInstructions();
        List<String> tmpList = pick("text", wayList.createJson(trMap.getWithFallBack(Locale.CANADA)));
        assertEquals(Arrays.asList("Continue onto 0-1", "Turn right onto 1-4", "Continue onto 4-7",
                "Turn left onto 7-8", "Continue onto 8-9", "Turn right onto road", "Finish!"),
                tmpList);

        tmpList = pick("text", wayList.createJson(trMap.getWithFallBack(Locale.GERMAN)));
        assertEquals(Arrays.asList("Geradeaus auf 0-1", "Rechts abbiegen auf 1-4", "Geradeaus auf 4-7",
                "Links abbiegen auf 7-8", "Geradeaus auf 8-9", "Rechts abbiegen auf Strasse", "Ziel erreicht!"),
                tmpList);

        assertEquals(70000.0, sumDistances(wayList), 1e-1);
        List<String> distStrings = wayList.createDistances(trMap.get("de"), false);
        assertEquals(Arrays.asList("10.0 km", "10.0 km", "10.0 km", "10.0 km", "20.0 km", "10.0 km", "0 m"), distStrings);
        distStrings = wayList.createDistances(trMap.get("en_US"), true);
        assertEquals(Arrays.asList("6.21 mi", "6.21 mi", "6.21 mi", "6.21 mi", "12.43 mi", "6.21 mi", "0 ft"), distStrings);
        List<GPXEntry> gpxes = wayList.createGPXList();
        assertEquals(10, gpxes.size());
        // check order of tower nodes        
        assertEquals(1, gpxes.get(0).getLon(), 1e-6);
        assertEquals(1.4, gpxes.get(gpxes.size() - 1).getLon(), 1e-6);

        // check order of pillar nodes        
        assertEquals(1.15, gpxes.get(4).getLon(), 1e-6);
        assertEquals(1.16, gpxes.get(5).getLon(), 1e-6);
        assertEquals(1.16, gpxes.get(5).getLon(), 1e-6);
                
        compare(Arrays.asList(asL(1.2d, 1.0d), asL(1.2d, 1.1), asL(1.1d, 1.1), asL(1.0, 1.1),
                asL(1.0, 1.2), asL(1.1, 1.3), asL(1.1, 1.4)),
                wayList.createStartPoints());

        p = new Dijkstra(g, carManager.getEncoder("CAR"), new ShortestWeighting()).calcPath(6, 2);
        assertEquals(42000, p.getDistance(), 1e-2);
        assertEquals(Helper.createTList(6, 7, 8, 5, 2), p.calcNodes());
        wayList = p.calcInstructions();
        tmpList = pick("text", wayList.createJson(trMap.getWithFallBack(Locale.CANADA)));
        assertEquals(Arrays.asList("Continue onto 6-7", "Continue onto 7-8", "Turn left onto 5-8", "Continue onto 5-2", "Finish!"),
                tmpList);

        // assertEquals(Arrays.asList(0, 1, 4, 5, 6), wayList.createPointIndices());
        // tmpList = createList(p.calcPoints(), wayList.createPointIndices());
        compare(Arrays.asList(asL(1d, 1d), asL(1d, 1.1), asL(1d, 1.2), asL(1.1, 1.2), asL(1.2, 1.2)),
                wayList.createStartPoints());
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

    List<List<Double>> createList( PointList pl, List<Integer> integs )
    {
        List<List<Double>> list = new ArrayList<List<Double>>();
        for (int i : integs)
        {
            List<Double> entryList = new ArrayList<Double>(2);
            entryList.add(pl.getLatitude(i));
            entryList.add(pl.getLongitude(i));
            list.add(entryList);
        }
        return list;
    }

    void compare( List<List<Double>> expected, List<List<Double>> was )
    {
        for (int i = 0; i < expected.size(); i++)
        {
            List<Double> e = expected.get(i);
            List<Double> wasE = was.get(i);
            for (int j = 0; j < e.size(); j++)
            {
                assertEquals("at " + j + " value " + e + " vs " + wasE, e.get(j), wasE.get(j), 1e-5d);
            }
        }
    }

    List<Double> asL( Double... list )
    {
        return Arrays.asList(list);
    }

    double sumDistances( InstructionList il )
    {
        double val = 0;
        for (Instruction i : il)
        {
            val += i.getDistance();
        }
        return val;
    }

    @Test
    public void testWayList2()
    {
        EncodingManager carManager = new EncodingManager("CAR");
        Graph g = new GraphBuilder(carManager).create();
        //   2
        //    \.  5
        //      \/
        //      4
        //     /
        //    3
        g.setNode(2, 10.3, 10.15);
        g.setNode(3, 10.0, 10.08);
        g.setNode(4, 10.1, 10.10);
        g.setNode(5, 10.2, 10.13);
        g.edge(3, 4, 100, true).setName("3-4");
        g.edge(4, 5, 100, true).setName("4-5");

        EdgeIteratorState iter = g.edge(2, 4, 100, true);
        iter.setName("2-4");
        PointList list = new PointList();
        list.add(10.20, 10.05);
        iter.setWayGeometry(list);

        Path p = new Dijkstra(g, carManager.getEncoder("CAR"), new ShortestWeighting()).calcPath(2, 3);
        InstructionList wayList = p.calcInstructions();
        List<String> tmpList = pick("text", wayList.createJson(trMap.getWithFallBack(Locale.CANADA)));
        assertEquals(Arrays.asList("Continue onto 2-4", "Turn slight right onto 3-4", "Finish!"),
                tmpList);

        p = new Dijkstra(g, carManager.getEncoder("CAR"), new ShortestWeighting()).calcPath(3, 5);
        wayList = p.calcInstructions();
        tmpList = pick("text", wayList.createJson(trMap.getWithFallBack(Locale.CANADA)));
        assertEquals(Arrays.asList("Continue onto 3-4", "Continue onto 4-5", "Finish!"),
                tmpList);
    }

    // problem: we normally don't want instructions if streetname stays but here it is suboptimal:
    @Test
    public void testNoInstructionIfSameStreet()
    {
        EncodingManager carManager = new EncodingManager("CAR");
        Graph g = new GraphBuilder(carManager).create();
        //   2
        //    \.  5
        //      \/
        //      4
        //     /
        //    3
        g.setNode(2, 10.3, 10.15);
        g.setNode(3, 10.0, 10.05);
        g.setNode(4, 10.1, 10.10);
        g.setNode(5, 10.2, 10.15);
        g.edge(3, 4, 100, true).setName("street");
        g.edge(4, 5, 100, true).setName("4-5");

        EdgeIteratorState iter = g.edge(2, 4, 100, true);
        iter.setName("street");
        PointList list = new PointList();
        list.add(10.20, 10.05);
        iter.setWayGeometry(list);

        Path p = new Dijkstra(g, carManager.getEncoder("CAR"), new ShortestWeighting()).calcPath(2, 3);
        InstructionList wayList = p.calcInstructions();
        List<String> tmpList = pick("text", wayList.createJson(trMap.getWithFallBack(Locale.CANADA)));
        assertEquals(Arrays.asList("Continue onto street", "Finish!"), tmpList);
    }

    @Test
    public void testInstructionsWithTimeAndPlace()
    {
        EncodingManager carManager = new EncodingManager("CAR");
        Graph g = new GraphBuilder(carManager).create();
        //   4-5
        //   |
        //   3-2
        //     |
        //     1
        g.setNode(1, 15.0, 10);
        g.setNode(2, 15.1, 10);
        g.setNode(3, 15.1, 9.9);
        g.setNode(4, 15.2, 9.9);
        g.setNode(5, 15.2, 10);

        g.edge(1, 2, 7000, true).setName("1-2").setFlags(flagsForSpeed(carManager, 70));
        g.edge(2, 3, 8000, true).setName("2-3").setFlags(flagsForSpeed(carManager, 80));
        g.edge(3, 4, 9000, true).setName("3-4").setFlags(flagsForSpeed(carManager, 90));
        g.edge(4, 5, 10000, true).setName("4-5").setFlags(flagsForSpeed(carManager, 100));

        Path p = new Dijkstra(g, carManager.getEncoder("CAR"), new ShortestWeighting()).calcPath(1, 5);
        InstructionList wayList = p.calcInstructions();
        assertEquals(5, wayList.size());

        List<GPXEntry> gpxList = wayList.createGPXList();
        assertEquals(34000, p.getDistance(), 1e-1);
        assertEquals(34000, sumDistances(wayList), 1e-1);
        assertEquals(5, gpxList.size());
        assertEquals(1604120, p.getMillis());
        assertEquals(1604120, gpxList.get(gpxList.size() - 1).getMillis());

        assertEquals(Instruction.CONTINUE_ON_STREET, wayList.get(0).getSign());
        assertEquals(15, wayList.get(0).getFirstLat(), 1e-3);
        assertEquals(10, wayList.get(0).getFirstLon(), 1e-3);

        assertEquals(Instruction.TURN_LEFT, wayList.get(1).getSign());
        assertEquals(15.1, wayList.get(1).getFirstLat(), 1e-3);
        assertEquals(10, wayList.get(1).getFirstLon(), 1e-3);

        assertEquals(Instruction.TURN_RIGHT, wayList.get(2).getSign());
        assertEquals(15.1, wayList.get(2).getFirstLat(), 1e-3);
        assertEquals(9.9, wayList.get(2).getFirstLon(), 1e-3);

        assertEquals(Instruction.TURN_RIGHT, wayList.get(3).getSign());
        assertEquals(15.2, wayList.get(3).getFirstLat(), 1e-3);
        assertEquals(9.9, wayList.get(3).getFirstLon(), 1e-3);

        String gpxStr = wayList.createGPX("test", 0, "GMT+1");
        assertTrue(gpxStr, gpxStr.contains("<trkpt lat=\"15.0\" lon=\"10.0\"><time>1970-01-01T01:00:00+01:00</time>"));
        assertTrue(gpxStr, gpxStr.contains("<extensions>") && gpxStr.contains("</extensions>"));
        assertTrue(gpxStr, gpxStr.contains("<rtept lat=\"15.1\" lon=\"10.0\">"));
        assertTrue(gpxStr, gpxStr.contains("<distance>8000</distance>"));
        assertTrue(gpxStr, gpxStr.contains("<desc>left 2-3</desc>"));

        // assertTrue(gpxStr, gpxStr.contains("<direction>W</direction>"));
        // assertTrue(gpxStr, gpxStr.contains("<turn-angle>-90</turn-angle>"));
        // assertTrue(gpxStr, gpxStr.contains("<azimuth>270</azimuth/>"));
    }

    @Test
    public void testCreateGPX()
    {
        InstructionList instructions = new InstructionList();
        PointList pl = new PointList();
        pl.add(49.942576, 11.580384);
        pl.add(49.941858, 11.582422);
        instructions.add(new Instruction(Instruction.CONTINUE_ON_STREET, "temp", 0, 0, pl).setDistance(240).setTime(15000));

        pl = new PointList();
        pl.add(49.941575, 11.583501);
        instructions.add(new Instruction(Instruction.TURN_LEFT, "temp2", 0, 0, pl).setDistance(25).setTime(4000));

        pl = new PointList();
        pl.add(49.941389, 11.584311);
        instructions.add(new Instruction(Instruction.TURN_LEFT, "temp2", 0, 0, pl).setDistance(25).setTime(3000));
        instructions.add(new FinishInstruction(49.941029, 11.584514));

        List<GPXEntry> result = instructions.createGPXList();
        assertEquals(5, result.size());

        assertEquals(0, result.get(0).getMillis());
        assertEquals(10391, result.get(1).getMillis());
        assertEquals(15000, result.get(2).getMillis());
        assertEquals(19000, result.get(3).getMillis());
        assertEquals(22000, result.get(4).getMillis());
    }

    private long flagsForSpeed( EncodingManager encodingManager, int speedKmPerHour )
    {
        OSMWay way = new OSMWay(1);
        way.setTag("highway", "motorway");
        way.setTag("maxspeed", String.format("%d km/h", speedKmPerHour));
        return encodingManager.handleWayTags(way, 1, 0);
    }

    @Test
    public void testEmptyList()
    {
        EncodingManager carManager = new EncodingManager("CAR");
        Graph g = new GraphBuilder(carManager).create();
        Path p = new Dijkstra(g, carManager.getSingle(), new ShortestWeighting()).calcPath(0, 1);
        InstructionList il = p.calcInstructions();
        assertEquals(0, il.size());
        assertEquals(0, il.createStartPoints().size());
    }

    @Test
    public void testRound()
    {
        assertEquals(100.94, InstructionList.round(100.94, 2), 1e-7);
        assertEquals(100.9, InstructionList.round(100.94, 1), 1e-7);
        assertEquals(101.0, InstructionList.round(100.95, 1), 1e-7);
    }
}
