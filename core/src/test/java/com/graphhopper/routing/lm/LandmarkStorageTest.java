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

import com.graphhopper.routing.Dijkstra;
import com.graphhopper.routing.Path;
import com.graphhopper.routing.RoutingAlgorithmTest;
import com.graphhopper.routing.ev.BooleanEncodedValue;
import com.graphhopper.routing.ev.Subnetwork;
import com.graphhopper.routing.subnetwork.PrepareRoutingSubnetworks;
import com.graphhopper.routing.util.*;
import com.graphhopper.routing.weighting.FastestWeighting;
import com.graphhopper.routing.weighting.WeightApproximator;
import com.graphhopper.routing.weighting.Weighting;
import com.graphhopper.storage.BaseGraph;
import com.graphhopper.storage.Directory;
import com.graphhopper.storage.NodeAccess;
import com.graphhopper.storage.RAMDirectory;
import com.graphhopper.util.EdgeIteratorState;
import com.graphhopper.util.GHUtility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import static java.util.Collections.emptyList;
import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Peter Karich
 */
public class LandmarkStorageTest {
    private BaseGraph graph;
    private FlagEncoder encoder;
    private BooleanEncodedValue subnetworkEnc;
    private EncodingManager encodingManager;

    @BeforeEach
    public void setUp() {
        encoder = FlagEncoders.createCar();
        subnetworkEnc = Subnetwork.create("car");
        encodingManager = new EncodingManager.Builder().add(encoder).add(subnetworkEnc).build();
        graph = new BaseGraph.Builder(encodingManager).create();
    }

    @AfterEach
    public void tearDown() {
        if (graph != null)
            graph.close();
    }

    @Test
    public void testInfiniteWeight() {
        Directory dir = new RAMDirectory();
        EdgeIteratorState edge = graph.edge(0, 1);
        int res = new LandmarkStorage(graph, encodingManager, dir, new LMConfig("c1", new FastestWeighting(encoder) {
            @Override
            public double calcEdgeWeight(EdgeIteratorState edgeState, boolean reverse) {
                return Integer.MAX_VALUE * 2L;
            }
        }), 8).setMaximumWeight(LandmarkStorage.PRECISION).calcWeight(edge, false);
        assertEquals(Integer.MAX_VALUE, res);

        dir = new RAMDirectory();
        res = new LandmarkStorage(graph, encodingManager, dir, new LMConfig("c2", new FastestWeighting(encoder) {
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
        LandmarkStorage lms = new LandmarkStorage(graph, encodingManager, dir, new LMConfig("c1", new FastestWeighting(encoder)), 4).
                setMaximumWeight(LandmarkStorage.PRECISION);
        lms._getInternalDA().create(2000);
        // 2^16=65536, use -1 for infinity and -2 for maximum
        lms.setWeight(0, 65536);
        // reached maximum value but do not reset to 0 instead use 2^16-2
        assertEquals(65536 - 2, lms.getFromWeight(0, 0));
        lms.setWeight(0, 65535);
        assertEquals(65534, lms.getFromWeight(0, 0));
        lms.setWeight(0, 79999);
        assertEquals(65534, lms.getFromWeight(0, 0));

        lms._getInternalDA().setInt(0, Integer.MAX_VALUE);
        assertTrue(lms.isInfinity(0));
        // for infinity return much bigger value
        // assertEquals(Integer.MAX_VALUE, lms.getFromWeight(0, 0));

        lms.setWeight(0, 79999);
        assertFalse(lms.isInfinity(0));
    }

    @Test
    public void testWithSubnetworks() {
        // 0-1-2..4-5->6
        GHUtility.setSpeed(60, true, true, encoder, graph.edge(0, 1).setDistance(10.1));
        GHUtility.setSpeed(60, true, true, encoder, graph.edge(1, 2).setDistance(10.2));

        graph.edge(2, 4).set(encoder.getAccessEnc(), false, false);
        GHUtility.setSpeed(60, true, true, encoder, graph.edge(4, 5).setDistance(10.5));
        GHUtility.setSpeed(60, true, false, encoder, graph.edge(5, 6).setDistance(10.6));

        Weighting weighting = new FastestWeighting(encoder);
        // 1 means => 2 allowed edge keys => excludes the node 6
        subnetworkRemoval(weighting, 1);

        LandmarkStorage storage = new LandmarkStorage(graph, encodingManager, new RAMDirectory(), new LMConfig("car", weighting), 2);
        storage.setMinimumNodes(2);
        storage.createLandmarks();
        assertEquals(3, storage.getSubnetworksWithLandmarks());
        assertEquals("[2, 0]", Arrays.toString(storage.getLandmarks(1)));
        // do not include 6 as landmark!
        assertEquals("[5, 4]", Arrays.toString(storage.getLandmarks(2)));
    }

    @Test
    public void testWithStronglyConnectedComponent() {
        // 0 - 1 - 2 = 3 - 4
        GHUtility.setSpeed(60, true, true, encoder, graph.edge(0, 1).setDistance(10.1));
        GHUtility.setSpeed(60, true, true, encoder, graph.edge(1, 2).setDistance(10.2));
        GHUtility.setSpeed(60, true, false, encoder, graph.edge(2, 3).setDistance(10.3));
        GHUtility.setSpeed(60, true, false, encoder, graph.edge(3, 2).setDistance(10.2));
        GHUtility.setSpeed(60, true, true, encoder, graph.edge(3, 4).setDistance(10.4));

        Weighting weighting = new FastestWeighting(encoder);

        // 3 nodes => 6 allowed edge keys but still do not exclude 3 & 4 as strongly connected and not a too small subnetwork!
        subnetworkRemoval(weighting, 4);

        LandmarkStorage storage = new LandmarkStorage(graph, encodingManager, new RAMDirectory(), new LMConfig("car", weighting), 2);
        storage.setMinimumNodes(3);
        storage.createLandmarks();
        assertEquals(2, storage.getSubnetworksWithLandmarks());
        assertEquals("[4, 0]", Arrays.toString(storage.getLandmarks(1)));
    }

    private void subnetworkRemoval(Weighting weighting, int minNodeSize) {
        // currently we rely on subnetwork removal in Landmark preparation, see #2256
        // PrepareRoutingSubnetworks removes OSM bugs regarding turn restriction mapping which the node-based Tarjan in Landmark preparation can't
        new PrepareRoutingSubnetworks(graph, Collections.singletonList(new PrepareRoutingSubnetworks.PrepareJob(subnetworkEnc, weighting))).
                setMinNetworkSize(minNodeSize).
                doWork();
    }

    @Test
    public void testWithOnewaySubnetworks() {
        // 0 -- 1 -> 2 -> 3
        // 4 -- 5 ->/
        GHUtility.setSpeed(60, true, true, encoder, graph.edge(0, 1).setDistance(10.1));
        GHUtility.setSpeed(60, true, false, encoder, graph.edge(1, 2).setDistance(10.2));
        GHUtility.setSpeed(60, true, false, encoder, graph.edge(2, 3).setDistance(10.3));

        GHUtility.setSpeed(60, true, true, encoder, graph.edge(4, 5).setDistance(10.5));
        GHUtility.setSpeed(60, true, false, encoder, graph.edge(5, 2).setDistance(10.2));

        Weighting weighting = new FastestWeighting(encoder);
        // 1 allowed node => 2 allowed edge keys (exclude 2 and 3 because they are separate too small oneway subnetworks)
        subnetworkRemoval(weighting, 1);

        LandmarkStorage storage = new LandmarkStorage(graph, encodingManager, new RAMDirectory(), new LMConfig("car", weighting), 2);
        storage.setMinimumNodes(2);
        storage.createLandmarks();

        assertEquals(3, storage.getSubnetworksWithLandmarks());
        assertEquals("[1, 0]", Arrays.toString(storage.getLandmarks(1)));
        assertEquals("[5, 4]", Arrays.toString(storage.getLandmarks(2)));
    }

    @Test
    public void testWeightingConsistence1() {
        // create an indifferent problem: shortest weighting can pass the speed==0 edge but fastest cannot (?)
        graph.edge(0, 1).setDistance(10.1).set(encoder.getAccessEnc(), true, true);
        GHUtility.setSpeed(30, true, true, encoder, graph.edge(1, 2).setDistance(10));
        graph.edge(2, 3).setDistance(10.1).set(encoder.getAccessEnc(), true, true);

        LandmarkStorage storage = new LandmarkStorage(graph, encodingManager, new RAMDirectory(), new LMConfig("car", new FastestWeighting(encoder)), 2);
        storage.setMinimumNodes(2);
        storage.createLandmarks();

        assertEquals(2, storage.getSubnetworksWithLandmarks());
        assertEquals("[2, 1]", Arrays.toString(storage.getLandmarks(1)));
    }

    @Test
    public void testWeightingConsistence2() {
        GHUtility.setSpeed(30, true, true, encoder, graph.edge(0, 1).setDistance(10));
        graph.edge(2, 3).setDistance(10.1).set(encoder.getAccessEnc(), true, true);
        GHUtility.setSpeed(30, true, true, encoder, graph.edge(2, 3).setDistance(10));

        LandmarkStorage storage = new LandmarkStorage(graph, encodingManager, new RAMDirectory(), new LMConfig("car", new FastestWeighting(encoder)), 2);
        storage.setMinimumNodes(2);
        storage.createLandmarks();

        assertEquals(3, storage.getSubnetworksWithLandmarks());
        assertEquals("[1, 0]", Arrays.toString(storage.getLandmarks(1)));
        assertEquals("[3, 2]", Arrays.toString(storage.getLandmarks(2)));
    }

    @Test
    public void testWithBorderBlocking() {
        RoutingAlgorithmTest.initBiGraph(graph, encoder);

        LandmarkStorage storage = new LandmarkStorage(graph, encodingManager, new RAMDirectory(), new LMConfig("car", new FastestWeighting(encoder)), 2);
        final SplitArea right = new SplitArea(emptyList());
        final SplitArea left = new SplitArea(emptyList());
        final AreaIndex<SplitArea> areaIndex = new AreaIndex<SplitArea>(emptyList()) {
            @Override
            public List<SplitArea> query(double lat, double lon) {
                if (lon > 0.00105) {
                    return Collections.singletonList(right);
                } else {
                    return Collections.singletonList(left);
                }
            }
        };
        storage.setAreaIndex(areaIndex);
        storage.setMinimumNodes(2);
        storage.createLandmarks();
        assertEquals(3, storage.getSubnetworksWithLandmarks());
    }

    @RepeatedTest(100)
    public void testFeasiblePotential_random() {
        // A* routing with weighting w is equivalent to Dijkstra routing with the 'reduced' weighting w_red(s,t):=w(s,t)-h(s)+h(t)
        // The heuristic h is 'feasible' (sometimes 'consistent' or 'monotone') iff w_red>=0.
        // let's see if this is fulfilled for some random graphs:
        long seed = System.nanoTime();
        Random rnd = new Random(seed);
        // todo: try with more nodes and also using one-ways (pBothDir<1) and different speeds (speed=null)
        int nodes = 20;
        GHUtility.buildRandomGraph(graph, rnd, nodes, 2.2, false, false, encoder.getAccessEnc(), encoder.getAverageSpeedEnc(), 60.0, 0, 1.0, 0);
//        GHUtility.printGraphForUnitTest(graph, encoder);

        // todo: try with more landmarks, but first make it work with one...
        int landmarks = 1;
        FastestWeighting weighting = new FastestWeighting(encoder);
        LandmarkStorage storage = new LandmarkStorage(graph, encodingManager, new RAMDirectory(), new LMConfig("car", weighting), landmarks);
        storage.setMinimumNodes(2);
        storage.createLandmarks();

        for (int target = 0; target < graph.getNodes(); target++) {
            LMApproximator heuristic = LMApproximator.forLandmarks(graph, storage, landmarks);
            // this works btw...
//            BeelineWeightApproximator heuristic = new BeelineWeightApproximator(graph.getNodeAccess(), weighting);
            heuristic.setTo(target);

            // first check the weaker property: the heuristic must never overestimate the weight.
            for (int source = 0; source < graph.getNodes(); source++) {
                Dijkstra dijkstra = new Dijkstra(graph, weighting, TraversalMode.NODE_BASED);
                Path path = dijkstra.calcPath(source, target);
                if (!path.isFound())
                    continue;
                double h = heuristic.approximate(source);
                if (h > path.getWeight())
                    throw new IllegalStateException("LM heuristic is not admissible. Heuristic overestimates distance from " + source + " to " + target + ": " + h + " vs. " + path.getWeight());
            }

            AllEdgesIterator edge = graph.getAllEdges();
            while (edge.next()) {
                double reducedWeight = weighting.calcEdgeWeight(edge, false) - heuristic.approximate(edge.getBaseNode()) + heuristic.approximate(edge.getAdjNode());
                if (reducedWeight < 0)
                    throw new IllegalStateException("LM heuristic is not feasible. Negative reduced weight for edge " + edge + ": " + reducedWeight);
            }
        }
    }

    @Test
    public void testFeasiblePotential_small() {
        NodeAccess na = graph.getNodeAccess();
        na.setNode(0, 49.405204, 9.702107);
        na.setNode(1, 49.401397, 9.709844);
        na.setNode(2, 49.401145, 9.700108);
        // 2=0-1
        GHUtility.setSpeed(60.000000, 60.000000, encoder, graph.edge(0, 1).setDistance(701.830000));
        GHUtility.setSpeed(60.000000, 60.000000, encoder, graph.edge(0, 2).setDistance(473.901000));
        GHUtility.setSpeed(60.000000, 60.000000, encoder, graph.edge(2, 0).setDistance(473.901000));
        int landmarks = 1;
        FastestWeighting weighting = new FastestWeighting(encoder);
        LandmarkStorage storage = new LandmarkStorage(graph, encodingManager, new RAMDirectory(), new LMConfig("car", weighting), landmarks);
        storage.setMinimumNodes(2);
        storage.createLandmarks();

        double weight01 = weighting.calcEdgeWeight(graph.getEdgeIteratorStateForKey(0), false);
        double weight02 = weighting.calcEdgeWeight(graph.getEdgeIteratorStateForKey(2), false);
        double weight20 = weighting.calcEdgeWeight(graph.getEdgeIteratorStateForKey(4), false);

        assertEquals(42.1098, weight01, 1.e-6);
        assertEquals(28.43406, weight02, 1.e-6);
        assertEquals(28.43406, weight20, 1.e-6);

        for (int target = 0; target < graph.getNodes(); target++) {
            WeightApproximator heuristic = LMApproximator.forLandmarks(graph, storage, landmarks);
            heuristic.setTo(target);
            double h0 = heuristic.approximate(0);
            double h1 = heuristic.approximate(1);
            double h2 = heuristic.approximate(2);
            assertTrue(weight01 - h0 + h1 >= 0, "heuristic not feasible: w01=" + weight01 + " < h0-h1=" + (h0 - h1) + ", target=" + target);
            assertTrue(weight02 - h0 + h2 >= 0, "heuristic not feasible: w02=" + weight02 + " < h0-h2=" + (h0 - h2) + ", target=" + target);
            assertTrue(weight20 - h2 + h0 >= 0, "heuristic not feasible: w20=" + weight20 + " < h2-h0=" + (h2 - h0) + ", target=" + target);
        }
    }
}
