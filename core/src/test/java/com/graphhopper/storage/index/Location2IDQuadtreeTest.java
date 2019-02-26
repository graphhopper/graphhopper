/*
 *  Licensed to GraphHopper GmbH under one or more contributor
 *  license agreements. See the NOTICE file distributed with this work for
 *  additional information regarding copyright ownership.
 *
 *  GraphHopper GmbH licenses this file to you under the Apache License,
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
package com.graphhopper.storage.index;

import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.storage.Graph;
import com.graphhopper.storage.MMapDirectory;
import com.graphhopper.storage.RAMDirectory;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * @author Peter Karich
 */
public class Location2IDQuadtreeTest extends AbstractLocationIndexTester {
    @Override
    public LocationIndex createIndex(Graph g, int resolution) {
        if (resolution < 0)
            resolution = 120;
        return new Location2IDQuadtree(g, new MMapDirectory(location + "loc2idIndex").create()).
                setResolution(resolution).prepareIndex();
    }

    @Test
    public void testNormedDist() {
        Location2IDQuadtree index = new Location2IDQuadtree(createGHStorage(EncodingManager.create("car")), new RAMDirectory());
        index.initAlgo(5, 6);
        assertEquals(1, index.getNormedDist(0, 1), 1e-6);
        assertEquals(2, index.getNormedDist(0, 7), 1e-6);
        assertEquals(2, index.getNormedDist(7, 2), 1e-6);
        assertEquals(1, index.getNormedDist(7, 1), 1e-6);
        assertEquals(4, index.getNormedDist(13, 25), 1e-6);
        assertEquals(8, index.getNormedDist(15, 25), 1e-6);
    }

    @Override
    boolean testGridIgnore(int i) {
        // conceptual limitation where we are stuck in a blind alley limited
        // to the current tile
        if (i == 6 || i == 36 || i == 90 || i == 96) {
            return true;
        }
        return false;
    }

    @Override
    public void testDifferentVehicles() {
        // currently unsupported
    }
}
