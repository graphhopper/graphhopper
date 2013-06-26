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

import com.graphhopper.routing.util.CarFlagEncoder;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.routing.util.FlagEncoder;
import com.graphhopper.storage.Graph;
import com.graphhopper.storage.GraphBuilder;
import com.graphhopper.util.EdgeIterator;
import com.graphhopper.util.PointList;
import com.graphhopper.util.WayList;
import java.util.Arrays;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 *
 * @author Peter Karich
 */
public class PathTest
{
    EncodingManager carManager = new EncodingManager("CAR");
    @Test
    public void testFound()
    {
        Path p = new Path(null, null);
        assertFalse(p.isFound());
        assertEquals(0, p.getDistance(), 1e-7);
        assertEquals(0, p.calcNodes().size());
    }

    @Test
    public void testTime()
    {
        FlagEncoder encoder = carManager.getEncoder("CAR");
        Path p = new Path(null, encoder);
        p.calcTime(100000, encoder.flags(100, true));
        assertEquals(60 * 60, p.getTime());
    }

    @Test
    public void testWayList() {
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
        g.edge(1, 2, 110, true);

        g.edge(0, 3, 110, true);
        g.edge(1, 4, 100, true).setName("1-4");
        g.edge(2, 5, 110, true);

        g.edge(3, 6, 100, true);
        g.edge(4, 7, 100, true).setName("4-7");
        g.edge(5, 8, 100, true);

        g.edge(6, 7, 110, true);
        EdgeIterator iter = g.edge(7, 8, 100, true);
        PointList list = new PointList();
        list.add(1.0, 1.15);
        list.add(1.0, 1.16);
        iter.setWayGeometry(list);
        iter.setName("7-8");
        // missing edge name => Unknown
        g.edge(9, 10, 100, true);
        EdgeIterator iter2 = g.edge(8, 9, 100, true);
        list.clear();
        list.add(1.0, 1.3);
        iter2.setName("8-9");
        iter2.setWayGeometry(list);

        Path p = new Dijkstra(g, carManager.getEncoder("CAR")).calcPath(0, 10);
        WayList wayList = p.calcWays();
        assertEquals(Arrays.asList("Continue onto 0-1", "Turn right onto 1-4",
                "Continue onto 4-7", "Turn left onto 7-8", "Continue onto 8-9",
                "Turn right onto unknown street"),
                wayList.createInstructions("en"));
    }
}
