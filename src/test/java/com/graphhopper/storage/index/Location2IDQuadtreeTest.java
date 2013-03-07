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
import com.graphhopper.storage.GraphBuilder;
import com.graphhopper.storage.MMapDirectory;
import com.graphhopper.storage.RAMDirectory;
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
public class Location2IDQuadtreeTest extends AbstractLocation2IDIndexTester {

    @Override
    public Location2IDIndex createIndex(Graph g, int resolution) {
        return new Location2IDQuadtree(g, new MMapDirectory(location + "loc2idIndex")).prepareIndex(resolution);
    }

    @Test
    public void testGrid() {
        Graph g = createSampleGraph();
        int locs = g.nodes();

        Location2IDIndex memoryEfficientIndex = createIndex(g, 120);
        // if we would use less array entries then some points gets the same key so avoid that for this test
        // e.g. for 16 we get "expected 6 but was 9" i.e 6 was overwritten by node j9 which is a bit closer to the grid center        
        // go through every point of the graph if all points are reachable
        for (int i = 0; i < locs; i++) {
            double lat = g.getLatitude(i);
            double lon = g.getLongitude(i);
            assertEquals("nodeId:" + i + " " + (float) lat + "," + (float) lon,
                    i, memoryEfficientIndex.findID(lat, lon));
        }

        // hit random lat,lon and compare result to full index
        Random rand = new Random(12);
        Location2IDIndex fullIndex = new Location2IDFullIndex(g);
        DistanceCalc dist = new DistanceCalc();
        for (int i = 0; i < 100; i++) {
            double lat = rand.nextDouble() * 5;
            double lon = rand.nextDouble() * 5;
            int fullId = fullIndex.findID(lat, lon);
            double fullLat = g.getLatitude(fullId);
            double fullLon = g.getLongitude(fullId);
            float fullDist = (float) dist.calcDist(lat, lon, fullLat, fullLon);
            int newId = memoryEfficientIndex.findID(lat, lon);
            double newLat = g.getLatitude(newId);
            double newLon = g.getLongitude(newId);
            float newDist = (float) dist.calcDist(lat, lon, newLat, newLon);

            // conceptual limitation where we are stuck in a blind alley limited
            // to the current tile
            if (i == 6 || i == 36 || i == 90 || i == 96)
                continue;

            assertTrue(i + " orig:" + (float) lat + "," + (float) lon
                    + " full:" + fullLat + "," + fullLon + " fullDist:" + fullDist
                    + " found:" + newLat + "," + newLon + " foundDist:" + newDist,
                    Math.abs(fullDist - newDist) < 50000);
        }
    }

    @Test
    public void testNormedDist() {
        Location2IDQuadtree index = new Location2IDQuadtree(createGraph(), new RAMDirectory());
        index.initAlgo(5, 6);
        assertEquals(1, index.normedDist(0, 1), 1e-6);
        assertEquals(2, index.normedDist(0, 7), 1e-6);
        assertEquals(2, index.normedDist(7, 2), 1e-6);
        assertEquals(1, index.normedDist(7, 1), 1e-6);
        assertEquals(4, index.normedDist(13, 25), 1e-6);
        assertEquals(8, index.normedDist(15, 25), 1e-6);
    }
}
