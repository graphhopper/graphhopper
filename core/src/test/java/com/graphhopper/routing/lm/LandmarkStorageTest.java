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
package com.graphhopper.routing.lm;

import com.graphhopper.routing.util.*;
import com.graphhopper.routing.weighting.FastestWeighting;
import com.graphhopper.storage.*;
import com.graphhopper.util.EdgeIteratorState;
import com.graphhopper.util.GHUtility;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.*;

/**
 * @author Peter Karich
 */
public class LandmarkStorageTest {
    private GraphHopperStorage ghStorage;
    private FlagEncoder encoder;
    private TraversalMode tm;

    @Before
    public void setUp() {
        encoder = new CarFlagEncoder();
        tm = TraversalMode.NODE_BASED;
        ghStorage = new GraphHopperStorage(new RAMDirectory(),
                new EncodingManager(encoder), false, new GraphExtension.NoOpExtension());
        ghStorage.create(1000);
    }

    @After
    public void tearDown() {
        if (ghStorage != null)
            ghStorage.close();
    }

    @Test
    public void testInfinitWeight() {
        Directory dir = new RAMDirectory();
        EdgeIteratorState edge = ghStorage.edge(0, 1);
        int res = new LandmarkStorage(ghStorage, dir, 8, new FastestWeighting(encoder) {
            @Override
            public double calcWeight(EdgeIteratorState edgeState, boolean reverse, int prevOrNextEdgeId) {
                return Integer.MAX_VALUE * 2L;
            }
        }, TraversalMode.NODE_BASED).calcWeight(edge, false);
        assertEquals(Integer.MAX_VALUE, res);

        dir = new RAMDirectory();
        res = new LandmarkStorage(ghStorage, dir, 8, new FastestWeighting(encoder) {
            @Override
            public double calcWeight(EdgeIteratorState edgeState, boolean reverse, int prevOrNextEdgeId) {
                return Double.POSITIVE_INFINITY;
            }
        }, TraversalMode.NODE_BASED).calcWeight(edge, false);
        assertEquals(Integer.MAX_VALUE, res);
    }

    @Test
    public void testWithSubnetworks() {
        ghStorage.edge(0, 1, 10, true);
        ghStorage.edge(1, 2, 10, true);

        ghStorage.edge(2, 4).setFlags(encoder.setAccess(0, false, false));
        ghStorage.edge(4, 5, 10, true);
        ghStorage.edge(5, 6, 10, false);

        LandmarkStorage storage = new LandmarkStorage(ghStorage, new RAMDirectory(), 2, new FastestWeighting(encoder), TraversalMode.NODE_BASED);
        storage.setMinimumNodes(2);
        storage.createLandmarks();
        assertEquals(3, storage.getSubnetworksWithLandmarks());
        assertEquals("[0, 2]", Arrays.toString(storage.getLandmarks(1)));
        assertEquals("[4, 5]", Arrays.toString(storage.getLandmarks(2)));
    }

    @Test
    public void testWithOnewaySubnetworks() {
        // create an indifferent problem: node 2 and 3 are part of two 'disconnected' subnetworks
        ghStorage.edge(0, 1, 10, true);
        ghStorage.edge(1, 2, 10, false);
        ghStorage.edge(2, 3, 10, false);

        ghStorage.edge(4, 5, 10, true);
        ghStorage.edge(5, 2, 10, false);

        LandmarkStorage storage = new LandmarkStorage(ghStorage, new RAMDirectory(), 2, new FastestWeighting(encoder), TraversalMode.NODE_BASED);
        storage.setMinimumNodes(2);
        storage.createLandmarks();

        assertEquals(2, storage.getSubnetworksWithLandmarks());
        assertEquals("[0, 1]", Arrays.toString(storage.getLandmarks(1)));
        // assertEquals("[5, 4]", Arrays.toString(storage.getLandmarks(2)));
    }

    @Test
    public void testWeightingConsistence() {
        // create an indifferent problem: shortest weighting can pass the speed==0 edge but fastest cannot (?)
        ghStorage.edge(0, 1, 10, true);
        ghStorage.edge(1, 2).setDistance(10).setFlags(encoder.setProperties(0.9, true, true));
        ghStorage.edge(2, 3, 10, true);

        LandmarkStorage storage = new LandmarkStorage(ghStorage, new RAMDirectory(), 2, new FastestWeighting(encoder), TraversalMode.NODE_BASED);
        storage.setMinimumNodes(2);
        storage.createLandmarks();

        assertEquals(2, storage.getSubnetworksWithLandmarks());
        assertEquals("[2, 3]", Arrays.toString(storage.getLandmarks(1)));
        // assertEquals("[3, 2]", Arrays.toString(storage.getLandmarks(2)));
    }

    @Test
    public void testSetGetWeight() {
        ghStorage.edge(0, 1, 40, true);
        Directory dir = new RAMDirectory();
        DataAccess da = dir.find("landmarks_fastest_car");
        da.create(2000);

        LandmarkStorage lms = new LandmarkStorage(ghStorage, dir, 4, new FastestWeighting(encoder), tm);
        // 2^16=65536, use -1 for infinity and -2 for maximum
        lms.setWeight(0, 65536);
        // reached maximum value but do not reset to 0 instead use 2^16-2
        assertEquals(65536 - 2, lms.getFromWeight(0, 0));
        lms.setWeight(0, 65535);
        assertEquals(65534, lms.getFromWeight(0, 0));
        lms.setWeight(0, 79999);
        assertEquals(65534, lms.getFromWeight(0, 0));

        da.setInt(0, Integer.MAX_VALUE);
        assertTrue(lms.isInfinity(0));
        // for infinity return much bigger value
        // assertEquals(Integer.MAX_VALUE, lms.getFromWeight(0, 0));

        lms.setWeight(0, 79999);
        assertFalse(lms.isInfinity(0));
    }
}
