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

import de.jetsli.graph.util.CalcDistance;
import java.util.Random;
import org.junit.*;
import static org.junit.Assert.*;

/**
 *
 * @author Peter Karich
 */
public class Location2IDQuadtreeTest {

    @Test
    public void testSinglePoints8() {
        Graph g = createSampleGraph();
        Location2IDQuadtree idx = new Location2IDQuadtree(g);
        idx.prepareIndex(8);
        // maxWidth is ~555km and with size==8 it will be exanded to 4*4 array => maxRasterWidth==555/4
        assertTrue(idx.getMaxRasterWidthKm() + "", idx.getMaxRasterWidthKm() < 140);
        assertEquals(1, idx.findID(1.637, 2.23));
        assertEquals(9, idx.findID(3.649, 1.375));
        assertEquals(9, idx.findID(3.3, 2.2));
        assertEquals(9, idx.findID(3.0, 1.5));
    }

    @Test
    public void testGrid() {
        Graph g = createSampleGraph();
        int locs = g.getLocations();

        Location2IDQuadtree memoryEfficientIndex = new Location2IDQuadtree(g);
        // if we would use less array entries then some points gets the same key so avoid that for this test
        // e.g. for 16 we get "expected 6 but was 9" i.e 6 was overwritten by node j9 which is a bit closer to the grid center
        memoryEfficientIndex.prepareIndex(32);
        // go through every point of the graph if all points are reachable
        for (int i = 0; i < locs; i++) {
            double lat = g.getLatitude(i);
            double lon = g.getLongitude(i);
            //System.out.println(key + " " + BitUtil.toBitString(key) + " " + lat + "," + lon);
            //System.out.println(i + " -> " + (float) lat + "\t," + (float) lon);
            assertEquals("nodeId:" + i + " " + (float) lat + "," + (float) lon,
                    i, memoryEfficientIndex.findID(lat, lon));
        }

        // hit random lat,lon and compare result to full index
        Random rand = new Random(12);
        Location2IDIndex fullIndex = new Location2IDFullIndex(g);
        CalcDistance dist = new CalcDistance();
        for (int i = 0; i < 100; i++) {
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

            // conceptual limitation see testSinglePoints32            
            if (i == 20 || i == 50)
                continue;

            assertTrue(i + " orig:" + (float) lat + "," + (float) lon
                    + " full:" + fullLat + "," + fullLon + " fullDist:" + fullDist
                    + " found:" + newLat + "," + newLon + " foundDist:" + newDist,
                    Math.abs(fullDist - newDist) < 50);
        }
    }

    @Test
    public void testSinglePoints32() {
        Graph g = createSampleGraph();
        Location2IDQuadtree idx = new Location2IDQuadtree(g);
        idx.prepareIndex(32);
        // 10 or 6
        assertEquals(10, idx.findID(3.649, 1.375));

        assertEquals(10, idx.findID(3.8465748, 0.021762699));

        // conceptual limitation for empty area and blind alley situations
        // see testGrid iteration => (i)
        // (20) we do not reach the 'hidden' (but more correct/close) node g6 instead we'll get e4
        // assertEquals(6, idx.findID(2.485, 1.373));

        // (50) we get 4 instead
        // assertEquals(0, idx.findID(0.64628404, 0.53006625));
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

//        
//   lat             /--------\
//    5   o-        p--------\ q
//          \  /-----\-----n | |
//    4       k    /--l--    m/                 
//           / \  j      \   |              
//    3     |   g  \  h---i  /             
//          |       \    /  /             
//    2     e---------f--  /
//                   /  \-d
//    1        /--b--      \               
//            |    \--------c
//    0       a                  
//        
//   lon: 0   1   2   3   4   5

        int a0 = graph.addLocation(0, 1.0001f);
        int b1 = graph.addLocation(1, 2);
        int c2 = graph.addLocation(0.5f, 4.5f);
        int d3 = graph.addLocation(1.5f, 3.8f);
        int e4 = graph.addLocation(2.01f, 0.5f);
        int f5 = graph.addLocation(2, 3);
        int g6 = graph.addLocation(3, 1.5f);
        int h7 = graph.addLocation(2.99f, 3.01f);
        int i8 = graph.addLocation(3, 4);
        int j9 = graph.addLocation(3.3f, 2.2f);
        int k10 = graph.addLocation(4, 1);
        int l11 = graph.addLocation(4.1f, 3);
        int m12 = graph.addLocation(4, 4.5f);
        int n13 = graph.addLocation(4.5f, 4.1f);
        int o14 = graph.addLocation(5, 0);
        int p15 = graph.addLocation(4.9f, 2.5f);
        int q16 = graph.addLocation(5, 5);
        // => 17 locations

        graph.edge(a0, b1, 1, true);
        graph.edge(c2, b1, 1, true);
        graph.edge(c2, d3, 1, true);
        graph.edge(f5, b1, 1, true);
        graph.edge(e4, f5, 1, true);
        graph.edge(m12, d3, 1, true);
        graph.edge(e4, k10, 1, true);
        graph.edge(f5, d3, 1, true);
        graph.edge(f5, i8, 1, true);
        graph.edge(f5, j9, 1, true);
        graph.edge(k10, g6, 1, true);
        graph.edge(j9, l11, 1, true);
        graph.edge(i8, l11, 1, true);
        graph.edge(i8, h7, 1, true);
        graph.edge(k10, n13, 1, true);
        graph.edge(k10, o14, 1, true);
        graph.edge(l11, p15, 1, true);
        graph.edge(m12, p15, 1, true);
        graph.edge(q16, p15, 1, true);
        graph.edge(q16, m12, 1, true);
        return graph;
    }
}
