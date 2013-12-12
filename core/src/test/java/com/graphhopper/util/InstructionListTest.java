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
import gnu.trove.list.TDoubleList;
import gnu.trove.procedure.TDoubleProcedure;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
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
        g.edge(0, 1, 100, true).setName("0-1");
        g.edge(1, 2, 110, true).setName("1-2");

        g.edge(0, 3, 110, true);
        g.edge(1, 4, 100, true).setName("1-4");
        g.edge(2, 5, 110, true).setName("5-2");

        g.edge(3, 6, 110, true).setName("3-6");
        g.edge(4, 7, 100, true).setName("4-7");
        g.edge(5, 8, 100, true).setName("5-8");

        g.edge(6, 7, 110, true).setName("6-7");
        EdgeIteratorState iter = g.edge(7, 8, 100, true);
        PointList list = new PointList();
        list.add(1.0, 1.15);
        list.add(1.0, 1.16);
        iter.setWayGeometry(list);
        iter.setName("7-8");
        // missing edge name => Unknown
        g.edge(9, 10, 100, true);
        EdgeIteratorState iter2 = g.edge(8, 9, 100, true);
        list.clear();
        list.add(1.0, 1.3);
        iter2.setName("8-9");
        iter2.setWayGeometry(list);

        Path p = new Dijkstra(g, carManager.getEncoder("CAR"), new ShortestWeighting()).calcPath(0, 10);
        InstructionList wayList = p.calcInstructions();
        assertEquals(Arrays.asList("Continue onto 0-1", "Turn right onto 1-4", "Continue onto 4-7",
                "Turn left onto 7-8", "Continue onto 8-9", "Turn right"),
                wayList.createDescription(trMap.getWithFallBack(Locale.CANADA)));

        assertEquals(Arrays.asList("Geradeaus auf 0-1", "Rechts abbiegen auf 1-4", "Geradeaus auf 4-7",
                "Links abbiegen auf 7-8", "Geradeaus auf 8-9", "Rechts abbiegen"),
                wayList.createDescription(trMap.getWithFallBack(Locale.GERMAN)));

        TDoubleList distList = wayList.getDistances();
        final DoubleRef dr = new DoubleRef(0);
        distList.forEach(new TDoubleProcedure()
        {
            @Override
            public boolean execute( double value )
            {
                dr.val += value;
                return true;
            }
        });
        assertEquals(p.getDistance(), dr.val, 1e-7);

        List<String> distStrings = wayList.createDistances(trMap.get("de"), false);
        assertEquals(Arrays.asList("100 m", "100 m", "100 m", "100 m", "100 m", "100 m"), distStrings);

        distStrings = wayList.createDistances(trMap.get("en_US"), true);
        assertEquals(Arrays.asList("328 ft", "328 ft", "328 ft", "328 ft", "328 ft", "328 ft"), distStrings);

        p = new Dijkstra(g, carManager.getEncoder("CAR"), new ShortestWeighting()).calcPath(6, 2);
        assertEquals(420, p.getDistance(), 1e-2);
        wayList = p.calcInstructions();
        assertEquals(Arrays.asList("Continue onto 6-7", "Continue onto 7-8", "Turn left onto 5-8", "Continue onto 5-2"),
                wayList.createDescription(trMap.getWithFallBack(Locale.CANADA)));
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
        assertEquals(Arrays.asList("Continue onto 2-4", "Turn slight right onto 3-4"),
                wayList.createDescription(trMap.getWithFallBack(Locale.CANADA)));

        p = new Dijkstra(g, carManager.getEncoder("CAR"), new ShortestWeighting()).calcPath(3, 5);
        wayList = p.calcInstructions();
        assertEquals(Arrays.asList("Continue onto 3-4", "Continue onto 4-5"),
                wayList.createDescription(trMap.getWithFallBack(Locale.CANADA)));
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
        assertEquals(Arrays.asList("Continue onto street"), wayList.createDescription(trMap.getWithFallBack(Locale.CANADA)));
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
        g.setNode(1, 15, 10);
        g.setNode(2, 16, 10);
        g.setNode(3, 16, 9);
        g.setNode(4, 17, 9);
        g.setNode(5, 17, 10);

        g.edge(1, 2, 1000, true).setName("1-2").setFlags(flagsForSpeed(carManager, 70));
        g.edge(2, 3, 1000, true).setName("2-3").setFlags(flagsForSpeed(carManager, 80));
        g.edge(3, 4, 1000, true).setName("3-4").setFlags(flagsForSpeed(carManager, 90));
        g.edge(4, 5, 1000, true).setName("4-5").setFlags(flagsForSpeed(carManager, 100));

        Path p = new Dijkstra(g, carManager.getEncoder("CAR"), new ShortestWeighting()).calcPath(1, 5);
        InstructionList wayList = p.calcInstructions();
        assertEquals(4, wayList.size());

        long sumOfTimes = 0;
        for (Instruction instruction : wayList)
        {
            sumOfTimes += instruction.getMillis();
        }
        assertEquals(p.getMillis(), sumOfTimes);

        assertEquals(Instruction.CONTINUE_ON_STREET, wayList.get(0).getIndication());
        assertEquals(15, wayList.get(0).getLat(), 1e-3);
        assertEquals(10, wayList.get(0).getLon(), 1e-3);

        assertEquals(Instruction.TURN_LEFT, wayList.get(1).getIndication());
        assertEquals(16, wayList.get(1).getLat(), 1e-3);
        assertEquals(10, wayList.get(1).getLon(), 1e-3);

        assertEquals(Instruction.TURN_RIGHT, wayList.get(2).getIndication());
        assertEquals(16, wayList.get(2).getLat(), 1e-3);
        assertEquals(9, wayList.get(2).getLon(), 1e-3);

        assertEquals(Instruction.TURN_RIGHT, wayList.get(3).getIndication());
        assertEquals(17, wayList.get(3).getLat(), 1e-3);
        assertEquals(9, wayList.get(3).getLon(), 1e-3);
    }

    private long flagsForSpeed( EncodingManager encodingManager, int speedKmPerHour )
    {
        OSMWay way = new OSMWay(1);
        way.setTag("highway", "motorway");
        way.setTag("maxspeed", String.format("%d km/h", speedKmPerHour));
        return encodingManager.handleWayTags(1, way);
    }
}
