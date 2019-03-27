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

import com.graphhopper.routing.AbstractRoutingAlgorithmTester;
import com.graphhopper.routing.util.CarFlagEncoder;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.routing.util.FlagEncoder;
import com.graphhopper.routing.util.spatialrules.DefaultSpatialRule;
import com.graphhopper.routing.util.spatialrules.SpatialRule;
import com.graphhopper.routing.util.spatialrules.SpatialRuleLookup;
import com.graphhopper.routing.weighting.FastestWeighting;
import com.graphhopper.storage.*;
import com.graphhopper.util.EdgeIteratorState;
import com.graphhopper.util.GHUtility;
import com.graphhopper.util.shapes.BBox;
import com.graphhopper.util.shapes.GHPoint;
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
    private EncodingManager encodingManager;

    @Before
    public void setUp() {
        encoder = new CarFlagEncoder();
        encodingManager = EncodingManager.create(encoder);
        ghStorage = new GraphHopperStorage(new RAMDirectory(),
                encodingManager, false, new GraphExtension.NoOpExtension());
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
        int res = new LandmarkStorage(ghStorage, dir, new FastestWeighting(encoder) {
            @Override
            public double calcWeight(EdgeIteratorState edgeState, boolean reverse, int prevOrNextEdgeId) {
                return Integer.MAX_VALUE * 2L;
            }
        }, 8).setMaximumWeight(LandmarkStorage.PRECISION).calcWeight(edge, false);
        assertEquals(Integer.MAX_VALUE, res);

        dir = new RAMDirectory();
        res = new LandmarkStorage(ghStorage, dir, new FastestWeighting(encoder) {
            @Override
            public double calcWeight(EdgeIteratorState edgeState, boolean reverse, int prevOrNextEdgeId) {
                return Double.POSITIVE_INFINITY;
            }
        }, 8).setMaximumWeight(LandmarkStorage.PRECISION).calcWeight(edge, false);
        assertEquals(Integer.MAX_VALUE, res);
    }

    @Test
    public void testSetGetWeight() {
        ghStorage.edge(0, 1, 40, true);
        Directory dir = new RAMDirectory();
        DataAccess da = dir.find("landmarks_fastest_car_node");
        da.create(2000);

        LandmarkStorage lms = new LandmarkStorage(ghStorage, dir, new FastestWeighting(encoder), 4).
                setMaximumWeight(LandmarkStorage.PRECISION);
        /* FROM_WEIGHT_BITS = 18
        2^18 = 262144, use -1 for infinity and -2 for maximum
        As the backward weight reaches a too high value it will use the maximum instead of 0 or infinity*/
        lms.setWeight(0, 0, 16, Math.pow(2, 18), true);
        assertEquals((int) Math.pow(2, 18) - 2, lms.getFromWeight(0, 0));
        lms.setWeight(0, 0, 16, 999999, true);
        assertEquals((int) Math.pow(2, 18) - 2, lms.getFromWeight(0, 0));

        /* FROM_WEIGHT_BITS = 18 --> remaining bits: 32-18 = 14
        The delta value is signed and will therefore go from -2^(14-1) to 2^(14-1).
        Now 2^13-1 is used for infinity, 2^13-2 as maximum and -2^13 as minimum.
        If the difference between forward and backward weight is too large it will use
        the maximum (if positive) or the minimum (if negative) instead of 0 or infinity
        The delta will then be added to the backward weight*/
        lms.setWeight(0, 0, 16, 999999, false);
        assertEquals((int) (Math.pow(2, 18) - 2 + Math.pow(2, 13) - 2), lms.getToWeight(0, 0));
        //                 {backward weight}   {delta weight}
        lms.setWeight(0, 0, 16, 1, false);
        assertEquals((int) (Math.pow(2, 18) - 2 + -Math.pow(2, 13)), lms.getToWeight(0, 0));
        //                 {backward weight}   {delta weight}

        da.setInt(0, Integer.MAX_VALUE);
        assertTrue(lms.isInfinity(0));
        // for infinity return much bigger value
        // assertEquals(Integer.MAX_VALUE, lms.getFromWeight(0, 0));

        lms.setWeight(0, 0, 16, 999999, true);
        assertFalse(lms.isInfinity(0));
    }

    @Test
    public void testWithSubnetworks() {
        ghStorage.edge(0, 1, 10, true);
        ghStorage.edge(1, 2, 10, true);

        ghStorage.edge(2, 4).set(encoder.getAccessEnc(), false).setReverse(encoder.getAccessEnc(), false);
        ghStorage.edge(4, 5, 10, true);
        ghStorage.edge(5, 6, 10, false);

        LandmarkStorage storage = new LandmarkStorage(ghStorage, new RAMDirectory(), new FastestWeighting(encoder), 2);
        storage.setMinimumNodes(2);
        storage.createLandmarks();
        assertEquals(3, storage.getSubnetworksWithLandmarks());
        assertEquals("[2, 0]", Arrays.toString(storage.getLandmarks(1)));
        assertEquals("[6, 4]", Arrays.toString(storage.getLandmarks(2)));
    }

    @Test
    public void testWithSubnetworks2() {
        // should not happen with subnetwork preparation
        // 0 - 1 - 2 = 3 - 4
        ghStorage.edge(0, 1, 10, true);
        ghStorage.edge(1, 2, 10, true);
        ghStorage.edge(2, 3, 10, false);
        ghStorage.edge(3, 2, 10, false);
        ghStorage.edge(3, 4, 10, true);

        LandmarkStorage storage = new LandmarkStorage(ghStorage, new RAMDirectory(), new FastestWeighting(encoder), 2);
        storage.setMinimumNodes(3);
        storage.createLandmarks();
        assertEquals(2, storage.getSubnetworksWithLandmarks());
        assertEquals("[4, 0]", Arrays.toString(storage.getLandmarks(1)));
    }

    @Test
    public void testWithOnewaySubnetworks() {
        // should not happen with subnetwork preparation
        // create an indifferent problem: node 2 and 3 are part of two 'disconnected' subnetworks
        ghStorage.edge(0, 1, 10, true);
        ghStorage.edge(1, 2, 10, false);
        ghStorage.edge(2, 3, 10, false);

        ghStorage.edge(4, 5, 10, true);
        ghStorage.edge(5, 2, 10, false);

        LandmarkStorage storage = new LandmarkStorage(ghStorage, new RAMDirectory(), new FastestWeighting(encoder), 2);
        storage.setMinimumNodes(2);
        storage.createLandmarks();

        assertEquals(2, storage.getSubnetworksWithLandmarks());
        assertEquals("[4, 0]", Arrays.toString(storage.getLandmarks(1)));
    }

    @Test
    public void testWeightingConsistence() {
        // create an indifferent problem: shortest weighting can pass the speed==0 edge but fastest cannot (?)
        ghStorage.edge(0, 1, 10, true);
        GHUtility.setProperties(ghStorage.edge(1, 2).setDistance(10), encoder, 0.9, true, true);
        ghStorage.edge(2, 3, 10, true);

        LandmarkStorage storage = new LandmarkStorage(ghStorage, new RAMDirectory(), new FastestWeighting(encoder), 2);
        storage.setMinimumNodes(2);
        storage.createLandmarks();

        assertEquals(2, storage.getSubnetworksWithLandmarks());
        assertEquals("[1, 0]", Arrays.toString(storage.getLandmarks(1)));
    }

    @Test
    public void testWithBorderBlocking() {
        AbstractRoutingAlgorithmTester.initBiGraph(ghStorage);

        LandmarkStorage storage = new LandmarkStorage(ghStorage, new RAMDirectory(), new FastestWeighting(encoder), 2);
        final SpatialRule ruleRight = new DefaultSpatialRule() {
            @Override
            public String getId() {
                return "right";
            }
        };
        final SpatialRule ruleLeft = new DefaultSpatialRule() {
            @Override
            public String getId() {
                return "left";
            }
        };
        final SpatialRuleLookup lookup = new SpatialRuleLookup() {

            @Override
            public SpatialRule lookupRule(double lat, double lon) {
                if (lon > 0.00105)
                    return ruleRight;

                return ruleLeft;
            }

            @Override
            public SpatialRule lookupRule(GHPoint point) {
                return lookupRule(point.lat, point.lon);
            }

            @Override
            public int getSpatialId(SpatialRule rule) {
                throw new IllegalStateException();
            }

            @Override
            public SpatialRule getSpatialRule(int spatialId) {
                throw new IllegalStateException();
            }

            @Override
            public int size() {
                return 2;
            }

            @Override
            public BBox getBounds() {
                return new BBox(-180, 180, -90, 90);
            }
        };

        storage.setSpatialRuleLookup(lookup);
        storage.setMinimumNodes(2);
        storage.createLandmarks();
        assertEquals(3, storage.getSubnetworksWithLandmarks());
    }

    @Test
    public void testDelta() {
        int distance = 1000000;

        ghStorage.edge(1, 2, distance, false);
        ghStorage.edge(2, 3, distance, false);
        ghStorage.edge(3, 1, distance, false);

        ghStorage.edge(2, 4, distance, true);
        ghStorage.edge(4, 5, distance, true);
        ghStorage.edge(5, 6, distance, true);
        ghStorage.edge(6, 7, distance, true);
        ghStorage.edge(7, 8, distance, true);
        ghStorage.edge(8, 9, distance, true);

        ghStorage.edge(3, 10, distance, true);
        ghStorage.edge(10, 11, distance, true);
        ghStorage.edge(11, 12, distance, true);
        ghStorage.edge(12, 13, distance, true);
        ghStorage.edge(13, 14, distance, true);
        ghStorage.edge(14, 15, distance, true);

        LandmarkStorage storage = new LandmarkStorage(ghStorage, new RAMDirectory(), new FastestWeighting(encoder), 2);
        storage.createLandmarks();

        assertEquals(15, storage.getLandmarks(1)[0]);
        assertEquals(9, storage.getLandmarks(1)[1]);

        assertEquals(35680, storage.getFromWeight(0, 1));
        assertEquals(40777, storage.getToWeight(0, 1));
        assertEquals(71361, storage.getFromWeight(0, 9));
        assertEquals(66264, storage.getToWeight(0, 9));
        assertEquals(15291, storage.getFromWeight(0, 12));
        assertEquals(15291, storage.getToWeight(0, 12));
        assertEquals(40777, storage.getFromWeight(1, 1));
        assertEquals(35680, storage.getToWeight(1, 1));
        assertEquals(50972, storage.getFromWeight(1, 12));
        assertEquals(56069, storage.getToWeight(1, 12));
        assertEquals(66264, storage.getFromWeight(1, 15));
        assertEquals(71361, storage.getToWeight(1, 15));
    }

    @Test
    public void testDeltaWarning() {
        int distance = 1000000;

        ghStorage.edge(1, 2, distance, false);
        ghStorage.edge(2, 3, distance, false);
        ghStorage.edge(3, 4, distance, false);
        ghStorage.edge(4, 5, distance, false);
        ghStorage.edge(5, 6, distance, false);
        ghStorage.edge(6, 1, distance, false);

        ghStorage.edge(1, 7, distance, true);
        ghStorage.edge(7, 8, distance, true);
        ghStorage.edge(8, 9, distance, true);

        ghStorage.edge(6, 10, distance, true);
        ghStorage.edge(10, 11, distance, true);
        ghStorage.edge(11, 12, distance, true);

        LandmarkStorage storage = new LandmarkStorage(ghStorage, new RAMDirectory(), new FastestWeighting(encoder), 2);
        storage.createLandmarks();

        assertEquals(12, storage.getLandmarks(1)[0]);
        assertEquals(9, storage.getLandmarks(1)[1]);

        assertEquals((int) Math.pow(2, 13) - 2, storage.getToWeight(0, 9) - storage.getFromWeight(0, 9));
        assertEquals((int) -Math.pow(2, 13), storage.getToWeight(1, 12) - storage.getFromWeight(1, 12));
    }
}
