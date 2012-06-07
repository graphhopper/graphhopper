/*
 *  Copyright 2012 Peter Karich info@jetsli.de
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
package de.jetsli.graph.storage;

import de.jetsli.graph.reader.CalcDistance;
import java.util.Random;
import org.junit.*;
import static org.junit.Assert.*;

/**
 *
 * @author Peter Karich
 */
public class Location2IDQuadtreeTest {

    @Test
    public void testSinglePoints() {
        Graph g = createSampleGraph();
        Location2IDIndex idx = new Location2IDQuadtree(g).prepareIndex(8);
        assertEquals(1, idx.findID(1.637, 2.23));
    }

    @Test
    public void testGrid() {
        Graph g = createSampleGraph();
        int locs = g.getLocations();

        Location2IDQuadtree memoryEfficientIndex = new Location2IDQuadtree(g);
        memoryEfficientIndex.prepareIndex(8);
        // go through every point of the graph if all points are reachable
        for (int i = 0; i < locs; i++) {
            double lat = g.getLatitude(i);
            double lon = g.getLongitude(i);
            //System.out.println(key + " " + BitUtil.toBitString(key) + " " + lat + "," + lon);
            //System.out.println(i + " -> " + (float) lat + "\t," + (float) lon);
            assertEquals("id:" + i + " " + (float) lat + "," + (float) lon,
                    i, memoryEfficientIndex.findID(lat, lon));
        }

        // hit random lat,lon and compare result to full index
        Random rand = new Random(12);
        Location2IDIndex fullIndex = new Location2IDFullIndex(g);
        CalcDistance dist = new CalcDistance();
        for (int i = 0; i < 1000; i++) {
            double lat = rand.nextDouble() * 5;
            double lon = rand.nextDouble() * 5;
            int fullId = fullIndex.findID(lat, lon);
            double fullLat = g.getLatitude(fullId);
            double fullLon = g.getLongitude(fullId);
            float fullDist = (float) dist.calcDistKm(lat, lon, fullLat, fullLon);
            int newId = memoryEfficientIndex.findID(lat, lon);
            double newLat = g.getLatitude(newId);
            double newLon = g.getLongitude(newId);
            float newDist = (float) dist.calcDistKm(lat, lon, newLat, newLon);
            assertTrue(i + " orig:" + (float) lat + "," + (float) lon
                    + " full:" + fullLat + "," + fullLon + " dist:" + fullDist
                    + " new:" + newLat + "," + newLon + " dist:" + newDist,
                    Math.abs(fullDist - newDist) < 50);
        }
    }

    @Test
    public void testNoErrorOnEdgeCase_lastIndex() {
        int locs = 10000;
        Graph g = new MMapGraph(locs).createNew();
        Random rand = new Random(12);
        for (int i = 0; i < locs; i++) {
            g.addLocation((float) rand.nextDouble() * 10 + 10, (float) rand.nextDouble() * 10 + 10);
        }
        Location2IDIndex idx = new Location2IDQuadtree(g);
        idx.prepareIndex(200);
    }

    public static Graph createSampleGraph() {
        MMapGraph graph = new MMapGraph(100).createNew();
        // length does not matter here but lat,lon and outgoing edges do!
        // 0
        int a = graph.addLocation(0, 1.0001f);
        int b = graph.addLocation(1, 2);
        int c = graph.addLocation(0.5f, 4.5f);
        int d = graph.addLocation(1.5f, 3.8f);
        int e = graph.addLocation(2.01f, 0.5f);
        int f = graph.addLocation(2, 3);
        int g = graph.addLocation(3, 1.5f);
        // 7
        int h = graph.addLocation(2.99f, 3.01f);
        int i = graph.addLocation(3, 4);
        int j = graph.addLocation(3.3f, 2.2f);
        int k = graph.addLocation(4, 1);
        int l = graph.addLocation(4.1f, 3);
        int m = graph.addLocation(4, 4.5f);
        int n = graph.addLocation(4.5f, 4.1f);
        int o = graph.addLocation(5, 0);
        // 15
        int p = graph.addLocation(4.9f, 2.5f);
        int q = graph.addLocation(5, 5);
        // => 17 locations

        graph.edge(a, b, 1, true);
        graph.edge(c, b, 1, true);
        graph.edge(c, d, 1, true);
        graph.edge(f, b, 1, true);
        graph.edge(e, f, 1, true);
        graph.edge(m, d, 1, true);
        graph.edge(e, k, 1, true);
        graph.edge(f, d, 1, true);
        graph.edge(f, i, 1, true);
        graph.edge(f, j, 1, true);
        graph.edge(k, g, 1, true);
        graph.edge(j, l, 1, true);
        graph.edge(i, l, 1, true);
        graph.edge(i, h, 1, true);
        graph.edge(k, n, 1, true);
        graph.edge(k, o, 1, true);
        graph.edge(l, p, 1, true);
        graph.edge(m, p, 1, true);
        graph.edge(q, p, 1, true);
        graph.edge(q, m, 1, true);
        return graph;
    }
}
