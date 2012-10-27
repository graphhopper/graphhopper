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

import static com.graphhopper.storage.Location2IDQuadtreeTest.*;
import com.graphhopper.util.DistanceCalc;
import com.graphhopper.util.Helper;
import java.io.File;
import java.util.Random;
import org.junit.After;
import static org.junit.Assert.*;
import org.junit.Before;
import org.junit.Test;

/**
 *
 * @author Peter Karich
 */
public class Location2IDPreciseIndexTest {

    String location = "./target/tmp";

    public Location2IDIndex createIndex(Graph g, int resolution) {
//        Directory dir = new RAMDirectory();
        Directory dir = new MMapDirectory(location);
        return new Location2IDPreciseIndex(g, dir).prepareIndex(resolution);
    }

    @Before
    public void setUp() {
        Helper.deleteDir(new File(location));
    }

    @After
    public void tearDown() {
        Helper.deleteDir(new File(location));
    }

    @Test
    public void testSimpleGraph() {
        //  6          4
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

        Location2IDIndex idx = createIndex(g, 8);
        assertEquals(4, idx.findID(5, 2));
        assertEquals(3, idx.findID(1.5, 2));
        assertEquals(0, idx.findID(-1, -1));
        // now get the edge 1-4 and not node 6
        assertNotSame(6, idx.findID(4, 0));
    }

    @Test
    public void testSinglePoints8() {
        Graph g = createSampleGraph();
        Location2IDIndex idx = createIndex(g, 8);
        assertIndex(idx);
    }

    @Test
    public void testGrid() {
        Graph g = createSampleGraph();
        int locs = g.getNodes();

        Location2IDIndex memoryEfficientIndex = createIndex(g, 32);
        // if we would use less array entries then some points gets the same key so avoid that for this test
        // e.g. for 16 we get "expected 6 but was 9" i.e 6 was overwritten by node j9 which is a bit closer to the grid center        
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
        Location2IDIndex fullIndex = new Location2IDFullWithEdgesIndex(g);
        DistanceCalc dist = new DistanceCalc();
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

            assertTrue(i + " orig:" + (float) lat + "," + (float) lon
                    + " full:" + fullLat + "," + fullLon + " fullDist:" + fullDist
                    + " found:" + newLat + "," + newLon + " foundDist:" + newDist,
                    Math.abs(fullDist - newDist) < 50);
        }
    }

    @Test
    public void testSinglePoints32() {
        Graph g = createSampleGraph();
        Location2IDIndex idx = createIndex(g, 32);

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
        Graph g = new GraphStorage(new MMapDirectory(location)).createNew(locs);
        Random rand = new Random(12);
        for (int i = 0; i < locs; i++) {
            g.setNode(i, (float) rand.nextDouble() * 10 + 10, (float) rand.nextDouble() * 10 + 10);
        }
        createIndex(g, 200);
    }

    @Test
    public void testSave() {
        File file = new File(location);
        file.mkdirs();

        Graph g = createSampleGraph();
        Location2IDPreciseIndex idx = new Location2IDPreciseIndex(g, new RAMDirectory(location, true));
        idx.prepareIndex(8);
        assertIndex(idx);
        idx.flush();

        idx = new Location2IDPreciseIndex(g, new RAMDirectory(location, true));
        assertTrue(idx.loadExisting());
        assertIndex(idx);

        // throw exception if load is made with a wrong graph => store node count into header as check sum
        g.setNode(g.getNodes(), 12, 23);
        idx = new Location2IDPreciseIndex(g, new RAMDirectory(location, true));
        try {
            idx.loadExisting();
            assertTrue(false);
        } catch (Exception ex) {
        }
    }

    private void assertIndex(Location2IDIndex idx) {
        // maxWidth is ~555km and with size==8 it will be exanded to 4*4 array => maxRasterWidth==555/4
        // assertTrue(idx.getMaxRasterWidthKm() + "", idx.getMaxRasterWidthKm() < 140);
        assertEquals(1, idx.findID(1.637, 2.23));
        assertEquals(10, idx.findID(3.649, 1.375));
        assertEquals(9, idx.findID(3.3, 2.2));
        assertEquals(6, idx.findID(3.0, 1.5));
    }
}
