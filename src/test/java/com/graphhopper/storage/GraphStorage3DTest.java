/*
 *  Copyright 2012 Peter Karich
 * 
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
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
import com.graphhopper.util.DistanceCalc3D;
import static org.junit.Assert.*;
import org.junit.Test;

/**
 *
 * @author Peter Karich
 */
public class GraphStorage3DTest {

    @Test
    public void testGetHeight() {
        GraphStorage3D g = new GraphStorage3D(new RAMDirectory());
        g.createNew(10);
        g.setNode(0, 50, 20.00000, 100);
        g.setNode(1, 50, 20.00002, 100);

        g.setNode(2, 50, 20.00001, 200);
        g.setNode(3, 50, 20.00001, 50);
        g.setNode(4, 50, 20.00001, 5);

        DistanceCalc3D dist = new DistanceCalc3D();
        edge(g, dist, 0, 2);
        edge(g, dist, 0, 3);
        edge(g, dist, 0, 4);
        edge(g, dist, 1, 2);
        edge(g, dist, 1, 3);
        edge(g, dist, 1, 4);

        Path p = new DijkstraBidirection(g).calcPath(0, 1);
        assertEquals(3, p.location(1));
        assertEquals(0.1, p.distance(), 1e-4);
    }

    public static void edge(GraphStorage3D g, DistanceCalc3D dist, int from, int to) {
        double tmpDist = dist.calcDistKm(g.getLatitude(from), g.getLongitude(from), g.getHeight(from),
                g.getLatitude(to), g.getLongitude(to), g.getHeight(to));
        // System.out.println(from + "->" + to + " " + tmpDist);
        g.edge(from, to, tmpDist, true);
    }
}
