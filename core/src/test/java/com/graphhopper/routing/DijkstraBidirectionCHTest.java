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
import com.graphhopper.routing.ev.DecimalEncodedValue;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.routing.util.FlagEncoder;
import com.graphhopper.routing.weighting.FastestWeighting;
import com.graphhopper.routing.weighting.ShortestWeighting;
import com.graphhopper.storage.*;
import com.graphhopper.util.EdgeIteratorState;
import com.graphhopper.util.GHUtility;
import com.graphhopper.util.PMap;
import org.junit.jupiter.api.Test;

import static com.graphhopper.util.Parameters.Algorithms.DIJKSTRA_BI;
import static com.graphhopper.util.Parameters.Routing.ALGORITHM;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Run some tests specific for {@link DijkstraBidirectionCH}, e.g. fine grained path unpacking and stall on demand
 *
 * @author Peter Karich
 * @author easbar
 * @see RoutingAlgorithmTest for test cases covering standard node- and edge-based routing with this algorithm
 */
public class DijkstraBidirectionCHTest {

    private final EncodingManager encodingManager;
    private final FlagEncoder carEncoder;
    private final FlagEncoder bike2Encoder;
    private final FlagEncoder motorCycleEncoder;

    public DijkstraBidirectionCHTest() {
        encodingManager = EncodingManager.create("car,foot,bike2,motorcycle");
        carEncoder = encodingManager.getEncoder("car");
        bike2Encoder = encodingManager.getEncoder("bike2");
        motorCycleEncoder = encodingManager.getEncoder("motorcycle");
    }

    @Test
    public void testBaseGraph() {
        GraphHopperStorage ghStorage = createGHStorage();
        RoutingAlgorithmTest.initDirectedAndDiffSpeed(ghStorage, carEncoder);

        // do CH preparation for car
        ShortestWeighting weighting = new ShortestWeighting(carEncoder);
        prepareCH(ghStorage, CHConfig.nodeBased(weighting.getName(), weighting));

        // use base graph for solving normal Dijkstra
        Path p1 = new RoutingAlgorithmFactorySimple().createAlgo(ghStorage, weighting, new AlgorithmOptions()).calcPath(0, 3);
        assertEquals(IntArrayList.from(0, 1, 5, 2, 3), p1.calcNodes());
        assertEquals(402.30, p1.getDistance(), 1e-2, p1.toString());
        assertEquals(144829, p1.getTime(), p1.toString());
    }

    @Test
    public void testBaseGraphMultipleVehicles() {
        EncodingManager em = EncodingManager.create("foot,car");
        FlagEncoder footEncoder = em.getEncoder("foot");
        FlagEncoder carEncoder = em.getEncoder("car");
        FastestWeighting footWeighting = new FastestWeighting(footEncoder);
        FastestWeighting carWeighting = new FastestWeighting(carEncoder);

        CHConfig carConfig = CHConfig.nodeBased("p_car", carWeighting);
        GraphHopperStorage g = new GraphBuilder(em).create();
        RoutingAlgorithmTest.initFootVsCar(carEncoder, footEncoder, g);

        // do CH preparation for car
        RoutingCHGraph chGraph = prepareCH(g, carConfig);

        // use contracted graph for car
        Path p1 = createCHAlgo(chGraph, true).calcPath(0, 7);
        assertEquals(IntArrayList.from(0, 4, 6, 7), p1.calcNodes());
        assertEquals(15000, p1.getDistance(), 1e-6, p1.toString());

        // use base graph for solving normal Dijkstra via car
        Path p2 = new RoutingAlgorithmFactorySimple().createAlgo(g, carWeighting, new AlgorithmOptions()).calcPath(0, 7);
        assertEquals(IntArrayList.from(0, 4, 6, 7), p2.calcNodes());
        assertEquals(15000, p2.getDistance(), 1e-6, p2.toString());
        assertEquals(2700 * 1000, p2.getTime(), p2.toString());

        // use base graph for solving normal Dijkstra via foot
        Path p4 = new RoutingAlgorithmFactorySimple().createAlgo(g, footWeighting, new AlgorithmOptions()).calcPath(0, 7);
        assertEquals(17000, p4.getDistance(), 1e-6, p4.toString());
        assertEquals(12240 * 1000, p4.getTime(), p4.toString());
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
        GraphHopperStorage graph = createGHStorage();
        GHUtility.setSpeed(60, 0, carEncoder,
                graph.edge(8, 9).setDistance(100),
                graph.edge(8, 3).setDistance(2),
                graph.edge(8, 5).setDistance(1),
                graph.edge(8, 6).setDistance(1),
                graph.edge(8, 7).setDistance(1),
                graph.edge(1, 2).setDistance(2),
                graph.edge(1, 8).setDistance(1),
                graph.edge(2, 3).setDistance(3));
        for (int i = 3; i < 7; ++i) {
            GHUtility.setSpeed(60, true, false, carEncoder, graph.edge(i, i + 1).setDistance(1));
        }
        GHUtility.setSpeed(60, true, false, carEncoder, graph.edge(9, 0).setDistance(1));
        GHUtility.setSpeed(60, true, false, carEncoder, graph.edge(3, 9).setDistance(200));
        graph.freeze();

        ShortestWeighting weighting = new ShortestWeighting(carEncoder);
        CHConfig chConfig = CHConfig.nodeBased(weighting.getName(), weighting);
        CHStorage store = graph.createCHStorage(chConfig);

        // explicitly set the node levels equal to the node ids
        // the graph contraction with this ordering yields no shortcuts
        new CHStorageBuilder(store).setIdentityLevels();
        RoutingCHGraph routingCHGraph = graph.createCHGraph(store, chConfig);
        RoutingAlgorithm algo = createCHAlgo(routingCHGraph, true);
        Path p = algo.calcPath(1, 0);
        // node 3 will be stalled and nodes 4-7 won't be explored --> we visit 7 nodes
        // note that node 9 will be visited by both forward and backward searches
        assertEquals(7, algo.getVisitedNodes());
        assertEquals(102, p.getDistance(), 1.e-3);
        assertEquals(IntArrayList.from(1, 8, 9, 0), p.calcNodes(), p.toString());

        // without stalling we visit 11 nodes
        RoutingAlgorithm algoNoSod = createCHAlgo(routingCHGraph, false);
        Path pNoSod = algoNoSod.calcPath(1, 0);
        assertEquals(11, algoNoSod.getVisitedNodes());
        assertEquals(102, pNoSod.getDistance(), 1.e-3);
        assertEquals(IntArrayList.from(1, 8, 9, 0), pNoSod.calcNodes(), pNoSod.toString());
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
        GraphHopperStorage graph = createGHStorage();
        EdgeIteratorState edge = GHUtility.setSpeed(encoder.getMaxSpeed() / 2, true, true, encoder, graph.edge(0, 1).setDistance(2));
        DecimalEncodedValue avSpeedEnc = encodingManager.getDecimalEncodedValue(EncodingManager.getKey(encoder, "average_speed"));
        edge.set(avSpeedEnc, speed, revSpeed);

        GHUtility.setSpeed(encoder.getMaxSpeed() / 2, true, true, encoder, graph.edge(1, 2).setDistance(1));
        graph.freeze();
        FastestWeighting weighting = new FastestWeighting(encoder);
        CHConfig chConfig = CHConfig.nodeBased(weighting.getName(), weighting);
        CHStorage chStore = graph.createCHStorage(chConfig);
        new CHStorageBuilder(chStore).setIdentityLevels();
        RoutingCHGraph routingCHGraph = graph.createCHGraph(chStore, chConfig);
        RoutingAlgorithm algo = createCHAlgo(routingCHGraph, true);
        Path p = algo.calcPath(from, to);
        assertEquals(3, p.getDistance(), 1.e-3);
        assertEquals(expectedPath, p.calcNodes(), p.toString());
    }

    private GraphHopperStorage createGHStorage() {
        return new GraphBuilder(encodingManager).create();
    }

    private RoutingCHGraph prepareCH(GraphHopperStorage graphHopperStorage, CHConfig chConfig) {
        graphHopperStorage.freeze();
        PrepareContractionHierarchies pch = PrepareContractionHierarchies.fromGraphHopperStorage(graphHopperStorage, chConfig);
        PrepareContractionHierarchies.Result res = pch.doWork();
        return graphHopperStorage.createCHGraph(res.getCHStorage(), res.getCHConfig());
    }

    private RoutingAlgorithm createCHAlgo(RoutingCHGraph chGraph, boolean withSOD) {
        PMap opts = new PMap();
        if (!withSOD) {
            opts.putObject("stall_on_demand", false);
        }
        opts.putObject(ALGORITHM, DIJKSTRA_BI);
        return new CHRoutingAlgorithmFactory(chGraph).createAlgo(opts);
    }
}
