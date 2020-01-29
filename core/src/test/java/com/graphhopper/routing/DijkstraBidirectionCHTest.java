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
package com.graphhopper.routing;

import com.carrotsearch.hppc.IntArrayList;
import com.graphhopper.routing.ch.CHRoutingAlgorithmFactory;
import com.graphhopper.routing.ch.PrepareContractionHierarchies;
import com.graphhopper.routing.ch.PrepareEncoder;
import com.graphhopper.routing.profiles.DecimalEncodedValue;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.routing.util.FlagEncoder;
import com.graphhopper.routing.weighting.FastestWeighting;
import com.graphhopper.routing.weighting.ShortestWeighting;
import com.graphhopper.routing.weighting.Weighting;
import com.graphhopper.storage.CHGraph;
import com.graphhopper.storage.CHProfile;
import com.graphhopper.storage.GraphBuilder;
import com.graphhopper.storage.GraphHopperStorage;
import com.graphhopper.util.EdgeIteratorState;
import com.graphhopper.util.GHUtility;
import com.graphhopper.util.Parameters;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * Run some tests specific for {@link DijkstraBidirectionCH}, e.g. fine grained path unpacking and stall on demand
 *
 * @author Peter Karich
 * @author easbar
 *
 * @see RoutingAlgorithmTest for test cases covering standard node- and edge-based routing with this algorithm
 */
public class DijkstraBidirectionCHTest {

    private final EncodingManager encodingManager;
    private final FlagEncoder carEncoder;
    private final FlagEncoder footEncoder;
    private final FlagEncoder bike2Encoder;
    private final FlagEncoder motorCycleEncoder;

    public DijkstraBidirectionCHTest() {
        encodingManager = EncodingManager.create("car,foot,bike2,motorcycle");
        carEncoder = encodingManager.getEncoder("car");
        footEncoder = encodingManager.getEncoder("foot");
        bike2Encoder = encodingManager.getEncoder("bike2");
        motorCycleEncoder = encodingManager.getEncoder("motorcycle");
    }

    @Test
    public void testPathRecursiveUnpacking() {
        // use an encoder where it is possible to store 2 weights per edge        
        ShortestWeighting weighting = new ShortestWeighting(bike2Encoder);
        GraphHopperStorage g = createGHStorage(weighting);
        g.edge(0, 1, 1, true);
        EdgeIteratorState iter1_1 = g.edge(0, 2, 1.4, false);
        EdgeIteratorState iter1_2 = g.edge(2, 5, 1.4, false);
        g.edge(1, 2, 1, true);
        g.edge(1, 3, 3, true);
        g.edge(2, 3, 1, true);
        g.edge(4, 3, 1, true);
        g.edge(2, 5, 1.4, true);
        g.edge(3, 5, 1, true);
        g.edge(5, 6, 1, true);
        g.edge(4, 6, 1, true);
        g.edge(6, 7, 1, true);
        EdgeIteratorState iter2_2 = g.edge(5, 7);
        iter2_2.setDistance(1.4).setFlags(GHUtility.setProperties(encodingManager.createEdgeFlags(), bike2Encoder, 10, true, false));
        g.freeze();

        CHGraph lg = g.getCHGraph();
        // simulate preparation
        int sc2_1 = lg.shortcut(0, 5, PrepareEncoder.getScFwdDir(), 1, iter1_1.getEdge(), iter1_2.getEdge());
        lg.shortcut(0, 7, PrepareEncoder.getScFwdDir(), 1, sc2_1, iter2_2.getEdge());
        lg.setLevel(1, 0);
        lg.setLevel(3, 1);
        lg.setLevel(4, 2);
        lg.setLevel(6, 3);
        lg.setLevel(2, 4);
        lg.setLevel(5, 5);
        lg.setLevel(7, 6);
        lg.setLevel(0, 7);

        AlgorithmOptions opts = new AlgorithmOptions(Parameters.Algorithms.DIJKSTRA_BI, weighting);
        Path p = new CHRoutingAlgorithmFactory(lg).createAlgo(lg, opts).calcPath(0, 7);

        assertEquals(IntArrayList.from(0, 2, 5, 7), p.calcNodes());
        assertEquals(1064, p.getTime());
        assertEquals(4.2, p.getDistance(), 1e-5);
    }

    @Test
    public void testBaseGraph() {
        ShortestWeighting weighting = new ShortestWeighting(carEncoder);
        GraphHopperStorage ghStorage = createGHStorage(weighting);
        RoutingAlgorithmTest.initDirectedAndDiffSpeed(ghStorage, carEncoder);

        // do CH preparation for car        
        prepareCH(ghStorage, CHProfile.nodeBased(weighting));

        // use base graph for solving normal Dijkstra
        Path p1 = new RoutingAlgorithmFactorySimple().createAlgo(ghStorage, AlgorithmOptions.start().weighting(weighting).build()).calcPath(0, 3);
        assertEquals(IntArrayList.from(0, 1, 5, 2, 3), p1.calcNodes());
        assertEquals(p1.toString(), 402.29, p1.getDistance(), 1e-2);
        assertEquals(p1.toString(), 144823, p1.getTime());
    }

    @Test
    public void testBaseGraphMultipleVehicles() {
        EncodingManager em = EncodingManager.create("foot,car");
        FlagEncoder footEncoder = em.getEncoder("foot");
        FlagEncoder carEncoder = em.getEncoder("car");
        AlgorithmOptions footOptions = AlgorithmOptions.start().
                weighting(new FastestWeighting(footEncoder)).build();
        AlgorithmOptions carOptions = AlgorithmOptions.start().
                weighting(new FastestWeighting(carEncoder)).build();

        CHProfile footProfile = CHProfile.nodeBased(footOptions.getWeighting());
        CHProfile carProfile = CHProfile.nodeBased(carOptions.getWeighting());
        GraphHopperStorage g = new GraphBuilder(em).setCHProfiles(footProfile, carProfile).create();
        RoutingAlgorithmTest.initFootVsCar(carEncoder, footEncoder, g);

        // do CH preparation for car
        RoutingAlgorithmFactory contractedFactory = prepareCH(g, carProfile);

        // use contracted graph for car
        Path p1 = contractedFactory.createAlgo(g.getCHGraph(carProfile), carOptions).calcPath(0, 7);
        assertEquals(IntArrayList.from(0, 4, 6, 7), p1.calcNodes());
        assertEquals(p1.toString(), 15000, p1.getDistance(), 1e-6);

        // use base graph for solving normal Dijkstra via car
        Path p2 = new RoutingAlgorithmFactorySimple().createAlgo(g, carOptions).calcPath(0, 7);
        assertEquals(IntArrayList.from(0, 4, 6, 7), p2.calcNodes());
        assertEquals(p2.toString(), 15000, p2.getDistance(), 1e-6);
        assertEquals(p2.toString(), 2700 * 1000, p2.getTime());

        // use base graph for solving normal Dijkstra via foot
        Path p4 = new RoutingAlgorithmFactorySimple().createAlgo(g, footOptions).calcPath(0, 7);
        assertEquals(p4.toString(), 17000, p4.getDistance(), 1e-6);
        assertEquals(p4.toString(), 12240 * 1000, p4.getTime());
        assertEquals(IntArrayList.from(0, 4, 5, 7), p4.calcNodes());
    }

    // 7------8------.---9----0
    // |      | \    |   |
    // 6------   |   |   |
    // |      |  1   |   |
    // 5------   |   |  /
    // |  _,--|   2  | /
    // |/         |  |/
    // 4----------3--/
    @Test
    public void testStallingNodesReducesNumberOfVisitedNodes() {
        ShortestWeighting weighting = new ShortestWeighting(carEncoder);
        GraphHopperStorage graph = createGHStorage(weighting);
        graph.edge(8, 9, 100, false);
        graph.edge(8, 3, 2, false);
        graph.edge(8, 5, 1, false);
        graph.edge(8, 6, 1, false);
        graph.edge(8, 7, 1, false);
        graph.edge(1, 2, 2, false);
        graph.edge(1, 8, 1, false);
        graph.edge(2, 3, 3, false);
        for (int i = 3; i < 7; ++i) {
            graph.edge(i, i + 1, 1, false);
        }
        graph.edge(9, 0, 1, false);
        graph.edge(3, 9, 200, false);
        CHGraph chGraph = graph.getCHGraph();

        // explicitly set the node levels equal to the node ids
        // the graph contraction with this ordering yields no shortcuts
        for (int i = 0; i < 10; ++i) {
            chGraph.setLevel(i, i);
        }
        graph.freeze();
        RoutingAlgorithm algo = createCHAlgo(chGraph, true, AlgorithmOptions.start().weighting(weighting).build());
        Path p = algo.calcPath(1, 0);
        // node 3 will be stalled and nodes 4-7 won't be explored --> we visit 7 nodes
        // note that node 9 will be visited by both forward and backward searches
        assertEquals(7, algo.getVisitedNodes());
        assertEquals(102, p.getDistance(), 1.e-3);
        assertEquals(p.toString(), IntArrayList.from(1, 8, 9, 0), p.calcNodes());

        // without stalling we visit 11 nodes
        RoutingAlgorithm algoNoSod = createCHAlgo(chGraph, false, AlgorithmOptions.start().weighting(weighting).build());
        Path pNoSod = algoNoSod.calcPath(1, 0);
        assertEquals(11, algoNoSod.getVisitedNodes());
        assertEquals(102, pNoSod.getDistance(), 1.e-3);
        assertEquals(pNoSod.toString(), IntArrayList.from(1, 8, 9, 0), pNoSod.calcNodes());
    }

    // t(0)--slow->1--s(2)
    //    \        |
    //    fast     |
    //      \--<---|
    @Test
    public void testDirectionDependentSpeedFwdSearch() {
        runTestWithDirectionDependentEdgeSpeed(10, 20, 0, 2, IntArrayList.from(0, 1, 2), motorCycleEncoder);
        runTestWithDirectionDependentEdgeSpeed(10, 20, 0, 2, IntArrayList.from(0, 1, 2), bike2Encoder);
    }

    // s(0)--fast->1--t(2)
    //    \        |
    //    slow     |
    //      \--<---|
    @Test
    public void testDirectionDependentSpeedBwdSearch() {
        runTestWithDirectionDependentEdgeSpeed(20, 10, 2, 0, IntArrayList.from(2, 1, 0), motorCycleEncoder);
        runTestWithDirectionDependentEdgeSpeed(20, 10, 2, 0, IntArrayList.from(2, 1, 0), bike2Encoder);
    }

    private void runTestWithDirectionDependentEdgeSpeed(double speed, double revSpeed, int from, int to, IntArrayList expectedPath, FlagEncoder encoder) {
        FastestWeighting weighting = new FastestWeighting(encoder);
        GraphHopperStorage graph = createGHStorage(weighting);
        EdgeIteratorState edge = graph.edge(0, 1, 2, true);
        DecimalEncodedValue avSpeedEnc = encodingManager.getDecimalEncodedValue(EncodingManager.getKey(encoder, "average_speed"));
        edge.set(avSpeedEnc, speed).setReverse(avSpeedEnc, revSpeed);

        graph.edge(1, 2, 1, true);

        CHGraph chGraph = graph.getCHGraph();
        for (int i = 0; i < 3; ++i) {
            chGraph.setLevel(i, i);
        }
        graph.freeze();

        RoutingAlgorithm algo = createCHAlgo(chGraph, true, AlgorithmOptions.start().weighting(weighting).build());
        Path p = algo.calcPath(from, to);
        assertEquals(3, p.getDistance(), 1.e-3);
        assertEquals(p.toString(), expectedPath, p.calcNodes());
    }

    private GraphHopperStorage createGHStorage(Weighting weighting) {
        return new GraphBuilder(encodingManager).setCHProfiles(CHProfile.nodeBased(weighting)).create();
    }

    private RoutingAlgorithmFactory prepareCH(GraphHopperStorage graphHopperStorage, CHProfile chProfile) {
        graphHopperStorage.freeze();
        PrepareContractionHierarchies pch = PrepareContractionHierarchies.fromGraphHopperStorage(graphHopperStorage, chProfile);
        pch.doWork();
        return pch.getRoutingAlgorithmFactory();
    }

    private RoutingAlgorithm createCHAlgo(CHGraph chGraph, boolean withSOD, AlgorithmOptions algorithmOptions) {
        if (!withSOD) {
            algorithmOptions.getHints().put("stall_on_demand", false);
        }
        return new CHRoutingAlgorithmFactory(chGraph).createAlgo(chGraph, algorithmOptions);
    }
}
