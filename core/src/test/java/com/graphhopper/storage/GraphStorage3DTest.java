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
package com.graphhopper.storage;

import com.graphhopper.routing.DijkstraBidirection;
import com.graphhopper.routing.Path;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.routing.util.ShortestWeighting;
import com.graphhopper.util.DistanceCalc3D;
import com.graphhopper.util.Helper;
import static org.junit.Assert.*;
import org.junit.Test;

/**
 *
 * @author Peter Karich
 */
public class GraphStorage3DTest
{
    final EncodingManager encodingManager = new EncodingManager("CAR");

    @Test
    public void testGetHeight()
    {
        GraphStorage3D g = new GraphStorage3D(new RAMDirectory(), encodingManager).create(100);
        g.setNode(0, 50, 20000.00, 100);
        g.setNode(1, 50, 20000.02, 100);

        g.setNode(2, 50, 20000.01, 200);
        g.setNode(3, 50, 20000.01, 50);
        g.setNode(4, 50, 20000.01, 5);

        DistanceCalc3D dist = new DistanceCalc3D();
        edge(g, dist, 0, 2);
        edge(g, dist, 0, 3);
        edge(g, dist, 0, 4);
        edge(g, dist, 1, 2);
        edge(g, dist, 1, 3);
        edge(g, dist, 1, 4);

        Path p = new DijkstraBidirection(g, encodingManager.getEncoder("CAR"), new ShortestWeighting()).calcPath(0, 1);
        assertEquals(Helper.createTList(0, 3, 1), p.calcNodes());
        assertEquals(100, p.getDistance(), .1);
    }

    public static void edge( GraphStorage3D g, DistanceCalc3D dist, int from, int to )
    {
        double tmpDist = dist.calcDist(g.getLatitude(from), g.getLongitude(from), g.getHeight(from),
                g.getLatitude(to), g.getLongitude(to), g.getHeight(to));
        // System.out.println(from + "->" + to + " " + tmpDist);
        g.edge(from, to, tmpDist, true);
    }
}
