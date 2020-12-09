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

import com.graphhopper.routing.RoutingAlgorithmTest;
import com.graphhopper.routing.util.CarFlagEncoder;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.routing.util.FlagEncoder;
import com.graphhopper.routing.util.spatialrules.AbstractSpatialRule;
import com.graphhopper.routing.util.spatialrules.SpatialRule;
import com.graphhopper.routing.util.spatialrules.SpatialRuleLookup;
import com.graphhopper.routing.util.spatialrules.SpatialRuleSet;
import com.graphhopper.routing.weighting.FastestWeighting;
import com.graphhopper.storage.DataAccess;
import com.graphhopper.storage.Directory;
import com.graphhopper.storage.GraphHopperStorage;
import com.graphhopper.storage.RAMDirectory;
import com.graphhopper.util.EdgeIteratorState;
import com.graphhopper.util.GHUtility;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.Polygon;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.*;

/**
 * @author Peter Karich
 */
public class LandmarkStorageTest {
    private GraphHopperStorage graph;
    private FlagEncoder encoder;
    private EncodingManager encodingManager;

    @Before
    public void setUp() {
        encoder = new CarFlagEncoder();
        encodingManager = EncodingManager.create(encoder);
        graph = new GraphHopperStorage(new RAMDirectory(),
                encodingManager, false);
        graph.create(1000);
    }

    @After
    public void tearDown() {
        if (graph != null)
            graph.close();
    }

    @Test
    public void testInfiniteWeight() {
        Directory dir = new RAMDirectory();
        EdgeIteratorState edge = graph.edge(0, 1);
        int res = new LandmarkStorage(graph, dir, new LMConfig("c1", new FastestWeighting(encoder) {
            @Override
            public double calcEdgeWeight(EdgeIteratorState edgeState, boolean reverse) {
                return Integer.MAX_VALUE * 2L;
            }
        }), 8).setMaximumWeight(LandmarkStorage.PRECISION).calcWeight(edge, false);
        assertEquals(Integer.MAX_VALUE, res);

        dir = new RAMDirectory();
        res = new LandmarkStorage(graph, dir, new LMConfig("c2", new FastestWeighting(encoder) {
            @Override
            public double calcEdgeWeight(EdgeIteratorState edgeState, boolean reverse) {
                return Double.POSITIVE_INFINITY;
            }
        }), 8).setMaximumWeight(LandmarkStorage.PRECISION).calcWeight(edge, false);
        assertEquals(Integer.MAX_VALUE, res);
    }

    @Test
    public void testSetGetWeight() {
        GHUtility.setSpeed(60, true, true, encoder, graph.edge(0, 1).setDistance(40.1));
        Directory dir = new RAMDirectory();
        DataAccess da = dir.find("landmarks_c1");
        da.create(2000);

        LandmarkStorage lms = new LandmarkStorage(graph, dir, new LMConfig("c1", new FastestWeighting(encoder)), 4).
                setMaximumWeight(LandmarkStorage.PRECISION);
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

    @Test
    public void testWithSubnetworks() {
        GHUtility.setSpeed(60, true, true, encoder, graph.edge(0, 1).setDistance(10.1));
        GHUtility.setSpeed(60, true, true, encoder, graph.edge(1, 2).setDistance(10.2));

        graph.edge(2, 4).set(encoder.getAccessEnc(), false, false);
        GHUtility.setSpeed(60, true, true, encoder, graph.edge(4, 5).setDistance(10.5));
        GHUtility.setSpeed(60, true, false, encoder, graph.edge(5, 6).setDistance(10.6));

        LandmarkStorage storage = new LandmarkStorage(graph, new RAMDirectory(), new LMConfig("c1", new FastestWeighting(encoder)), 2);
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
        GHUtility.setSpeed(60, true, true, encoder, graph.edge(0, 1).setDistance(10.1));
        GHUtility.setSpeed(60, true, true, encoder, graph.edge(1, 2).setDistance(10.2));
        GHUtility.setSpeed(60, true, false, encoder, graph.edge(2, 3).setDistance(10.3));
        GHUtility.setSpeed(60, true, false, encoder, graph.edge(3, 2).setDistance(10.2));
        GHUtility.setSpeed(60, true, true, encoder, graph.edge(3, 4).setDistance(10.4));

        LandmarkStorage storage = new LandmarkStorage(graph, new RAMDirectory(), new LMConfig("c", new FastestWeighting(encoder)), 2);
        storage.setMinimumNodes(3);
        storage.createLandmarks();
        assertEquals(2, storage.getSubnetworksWithLandmarks());
        assertEquals("[4, 0]", Arrays.toString(storage.getLandmarks(1)));
    }

    @Test
    public void testWithOnewaySubnetworks() {
        // should not happen with subnetwork preparation
        // create an indifferent problem: node 2 and 3 are part of two 'disconnected' subnetworks
        GHUtility.setSpeed(60, true, true, encoder, graph.edge(0, 1).setDistance(10.1));
        GHUtility.setSpeed(60, true, false, encoder, graph.edge(1, 2).setDistance(10.2));
        GHUtility.setSpeed(60, true, false, encoder, graph.edge(2, 3).setDistance(10.3));

        GHUtility.setSpeed(60, true, true, encoder, graph.edge(4, 5).setDistance(10.5));
        GHUtility.setSpeed(60, true, false, encoder, graph.edge(5, 2).setDistance(10.2));

        LandmarkStorage storage = new LandmarkStorage(graph, new RAMDirectory(), new LMConfig("c", new FastestWeighting(encoder)), 2);
        storage.setMinimumNodes(2);
        storage.createLandmarks();

        assertEquals(2, storage.getSubnetworksWithLandmarks());
        assertEquals("[4, 0]", Arrays.toString(storage.getLandmarks(1)));
    }

    @Test
    public void testWeightingConsistence() {
        // create an indifferent problem: shortest weighting can pass the speed==0 edge but fastest cannot (?)
        GHUtility.setSpeed(60, true, true, encoder, graph.edge(0, 1).setDistance(10.1));
        GHUtility.setSpeed(0.9, true, true, encoder, graph.edge(1, 2).setDistance(10));
        GHUtility.setSpeed(60, true, true, encoder, graph.edge(2, 3).setDistance(10.3));

        LandmarkStorage storage = new LandmarkStorage(graph, new RAMDirectory(), new LMConfig("c", new FastestWeighting(encoder)), 2);
        storage.setMinimumNodes(2);
        storage.createLandmarks();

        assertEquals(2, storage.getSubnetworksWithLandmarks());
        assertEquals("[1, 0]", Arrays.toString(storage.getLandmarks(1)));
    }

    @Test
    public void testWithBorderBlocking() {
        RoutingAlgorithmTest.initBiGraph(graph, encoder);

        LandmarkStorage storage = new LandmarkStorage(graph, new RAMDirectory(), new LMConfig("c", new FastestWeighting(encoder)), 2);
        final SpatialRule ruleRight = new AbstractSpatialRule(Collections.<Polygon>emptyList()) {
            @Override
            public String getId() {
                return "right";
            }
        };
        final SpatialRule ruleLeft = new AbstractSpatialRule(Collections.<Polygon>emptyList()) {
            @Override
            public String getId() {
                return "left";
            }
        };
        final SpatialRuleLookup lookup = new SpatialRuleLookup() {

            private final List<SpatialRule> rules = Arrays.asList(ruleLeft, ruleRight);

            @Override
            public SpatialRuleSet lookupRules(double lat, double lon) {
                SpatialRule rule;
                if (lon > 0.00105) {
                    rule = ruleRight;
                } else {
                    rule = ruleLeft;
                }

                return new SpatialRuleSet(Collections.singletonList(rule), rules.indexOf(rule) + 1);
            }

            @Override
            public List<SpatialRule> getRules() {
                return rules;
            }

            @Override
            public Envelope getBounds() {
                return new Envelope(-180d, 180d, -90d, 90d);
            }
        };

        storage.setSpatialRuleLookup(lookup);
        storage.setMinimumNodes(2);
        storage.createLandmarks();
        assertEquals(3, storage.getSubnetworksWithLandmarks());
    }
}
