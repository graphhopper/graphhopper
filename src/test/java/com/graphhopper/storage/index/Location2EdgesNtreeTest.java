/*
 *  Licensed to Peter Karich under one or more contributor license
 *  agreements. See the NOTICE file distributed with this work for
 *  additional information regarding copyright ownership.
 *
 *  Peter Karich licenses this file to you under the Apache License,
 *  Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License. You may obtain a copy of the
 *  License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package com.graphhopper.storage.index;

import com.graphhopper.storage.Graph;
import com.graphhopper.storage.RAMDirectory;
import com.graphhopper.storage.index.Location2EdgesIndex;
import com.graphhopper.storage.index.Location2EdgesNtree;
import com.graphhopper.util.Helper;
import com.graphhopper.util.RawEdgeIterator;
import com.graphhopper.util.shapes.GHPlace;
import gnu.trove.list.TIntList;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 *
 * @author Peter Karich
 */
public class Location2EdgesNtreeTest {

    @Test
    public void testSimpleGraph() {
        Graph g = createGraph();
        g.setNode(0, -1, -2);
        g.setNode(1, 2, -1);
        g.setNode(2, 0, 1);
        g.setNode(3, 1, 2);
        g.setNode(4, 6, 1);
        g.setNode(5, 4, 4);
        g.setNode(6, 4.5, -0.5);
        g.edge(0, 1, 3.5, true);
        g.edge(0, 2, 2.5, true);
        g.edge(2, 3, 1, true);
        g.edge(3, 4, 2.2, true);
        g.edge(1, 4, 2.4, true);
        g.edge(3, 5, 1.5, true);

        Location2EdgesIndex idx = createIndex(g);
        assertEquals(Helper.createTList(2), idx.findEdges(new GHPlace(5, 2)));
        assertEquals(Helper.createTList(3), idx.findEdges(new GHPlace(1.5, 2)));
        assertEquals(Helper.createTList(0), idx.findEdges(new GHPlace(-1, -1)));
        // now get the edge 1-4 and not node 6
        assertEquals(Helper.createTList(6), idx.findEdges(new GHPlace(4, 0)));
    }

    @Test
    public void testSimpleGraph2() {
        //  6         4
        //  5       
        //          6
        //  4                5
        //  3
        //  2      1  
        //  1            3
        //  0      2      
        // -1   0
        //
        //     -2 -1 0 1 2 3 4
        Graph g = createGraph();
        g.setNode(0, -1, -2);
        g.setNode(1, 2, -1);
        g.setNode(2, 0, 1);
        g.setNode(3, 1, 2);
        g.setNode(4, 6, 1);
        g.setNode(5, 4, 4);
        g.setNode(6, 4.5, -0.5);
        g.edge(0, 1, 3.5, true);
        g.edge(0, 2, 2.5, true);
        g.edge(2, 3, 1, true);
        g.edge(3, 4, 3.2, true);
        g.edge(1, 4, 2.4, true);
        g.edge(3, 5, 1.5, true);

        Location2EdgesIndex idx = createIndex(g);
        assertEquals(Helper.createTList(4), idx.findEdges(new GHPlace(5, 2)));
        assertEquals(Helper.createTList(3), idx.findEdges(new GHPlace(1.5, 2)));
        assertEquals(Helper.createTList(0), idx.findEdges(new GHPlace(-1, -1)));
        assertEquals(Helper.createTList(6), idx.findEdges(new GHPlace(4, 0)));
        assertEquals(Helper.createTList(6), idx.findEdges(new GHPlace(4, -2)));
        // 4 is wrong!
        assertEquals(Helper.createTList(6), idx.findEdges(new GHPlace(4, 1)));
        assertEquals(Helper.createTList(5), idx.findEdges(new GHPlace(3, 3)));
    }

    @Test
    public void testSinglePoints120() {
        Graph g = Location2IDQuadtreeTest.createSampleGraph();
        Location2EdgesIndex idx = createIndex(g);

        // maxWidth is ~555km and with size==8 it will be exanded to 4*4 array => maxRasterWidth==555/4
        // assertTrue(idx.getMaxRasterWidthKm() + "", idx.getMaxRasterWidthKm() < 140);
        assertEquals(Helper.createTList(1), idx.findEdges(new GHPlace(1.637, 2.23)));
        assertEquals(Helper.createTList(10), idx.findEdges(new GHPlace(3.649, 1.375)));
        assertEquals(Helper.createTList(9), idx.findEdges(new GHPlace(3.3, 2.2)));
        assertEquals(Helper.createTList(6), idx.findEdges(new GHPlace(3.0, 1.5)));
        assertEquals(Helper.createTList(10), idx.findEdges(new GHPlace(3.8, 0)));
        assertEquals(Helper.createTList(10), idx.findEdges(new GHPlace(3.8466, 0.021)));
    }

    @Test
    public void testGrid() {
        Graph g = Location2IDQuadtreeTest.createSampleGraph();
        Location2EdgesIndex memoryEfficientIndex = createIndex(g);
        // if we would use less array entries then some points gets the same key so avoid that for this test
        // e.g. for 16 we get "expected 6 but was 9" i.e 6 was overwritten by node j9 which is a bit closer to the grid center        
        // go through every point of the graph if all points are reachable
        RawEdgeIterator iter = g.getAllEdges();
        while (iter.next()) {
            double lat = (g.getLatitude(iter.nodeA()) + g.getLatitude(iter.nodeB())) / 2;
            double lon = (g.getLongitude(iter.nodeA()) + g.getLatitude(iter.nodeB())) / 2;
            assertEquals("nodeId:" + iter + " " + (float) lat + "," + (float) lon,
                    Helper.createTList(iter.nodeA(), iter.nodeB()),
                    memoryEfficientIndex.findEdges(new GHPlace(lat, lon)));
        }

        // hit random lat,lon and compare result to full index
        // TODO NOW
//        Random rand = new Random(12);
//        Location2IDIndex fullIndex = new Location2IDFullIndex(g);
//        DistanceCalc dist = new DistanceCalc();
//        for (int i = 0; i < 100; i++) {
//            double lat = rand.nextDouble() * 5;
//            double lon = rand.nextDouble() * 5;
//            int fullId = fullIndex.findID(lat, lon);
//            double fullLat = g.getLatitude(fullId);
//            double fullLon = g.getLongitude(fullId);
//            float fullDist = (float) dist.calcDist(lat, lon, fullLat, fullLon);
//            int newId = memoryEfficientIndex.findID(lat, lon);
//            double newLat = g.getLatitude(newId);
//            double newLon = g.getLongitude(newId);
//            float newDist = (float) dist.calcDist(lat, lon, newLat, newLon);
//
//            // conceptual limitation where we are stuck in a blind alley limited
//            // to the current tile
//            if (i == 6 || i == 36 || i == 90 || i == 96)
//                continue;
//
//            assertTrue(i + " orig:" + (float) lat + "," + (float) lon
//                    + " full:" + fullLat + "," + fullLon + " fullDist:" + fullDist
//                    + " found:" + newLat + "," + newLon + " foundDist:" + newDist,
//                    Math.abs(fullDist - newDist) < 50000);
//        }
    }

    @Test
    public void testSinglePoints32() {
        Graph g = Location2IDQuadtreeTest.createSampleGraph();
        Location2EdgesIndex idx = createIndex(g);

        // 10 or 6
        assertEquals(Helper.createTList(10), idx.findEdges(new GHPlace(3.649, 1.375)));
        assertEquals(Helper.createTList(10), idx.findEdges(new GHPlace(3.8465748, 0.021762699)));
        assertEquals(Helper.createTList(6), idx.findEdges(new GHPlace(2.485, 1.373)));
        assertEquals(Helper.createTList(0), idx.findEdges(new GHPlace(0.64628404, 0.53006625)));
    }

    @Test
    public void testFindEdges() {
        Graph graph = createGraph();
        Location2EdgesIndex instance = createIndex(graph);
        TIntList result = instance.findEdges(new GHPlace(42, 12));
        assertEquals(Helper.createTList(1, 2), result);
    }

    Location2EdgesIndex createIndex(Graph g) {
        Location2EdgesNtree index = new Location2EdgesNtree(g, new RAMDirectory());
        index.prepareIndex();
        return index;
    }

    private Graph createGraph() {
        return Location2IDQuadtreeTest.createSampleGraph();
    }
}
