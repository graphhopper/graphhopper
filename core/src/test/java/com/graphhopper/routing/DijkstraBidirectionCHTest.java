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
import com.graphhopper.routing.ch.NodeBasedNodeContractorTest;
import com.graphhopper.routing.ch.PrepareContractionHierarchies;
import com.graphhopper.routing.ch.PrepareEncoder;
import com.graphhopper.routing.profiles.DecimalEncodedValue;
import com.graphhopper.routing.util.*;
import com.graphhopper.routing.weighting.FastestWeighting;
import com.graphhopper.routing.weighting.ShortestWeighting;
import com.graphhopper.routing.weighting.Weighting;
import com.graphhopper.storage.*;
import com.graphhopper.util.CHEdgeIteratorState;
import com.graphhopper.util.EdgeIteratorState;
import com.graphhopper.util.GHUtility;
import com.graphhopper.util.Parameters;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static com.graphhopper.routing.ch.NodeBasedNodeContractorTest.SC_ACCESS;
import static org.junit.Assert.assertEquals;

/**
 * Tests if a graph optimized by contraction hierarchies returns the same results as a none
 * optimized one. Additionally fine grained path unpacking is tested.
 *
 * @author Peter Karich
 */
public class DijkstraBidirectionCHTest extends AbstractRoutingAlgorithmTester {
    @Override
    protected CHGraph getGraph(GraphHopperStorage ghStorage, Weighting weighting) {
        return ghStorage.getGraph(CHGraph.class, weighting);
    }

    @Override
    protected GraphHopperStorage createGHStorage(EncodingManager em,
                                                 List<? extends Weighting> weightings, boolean is3D) {
        return new GraphHopperStorage(weightings, new RAMDirectory(), em, is3D, new GraphExtension.NoOpExtension()).
                create(1000);
    }

    @Override
    public RoutingAlgorithmFactory createFactory(GraphHopperStorage ghStorage, AlgorithmOptions opts) {
        ghStorage.freeze();
        PrepareContractionHierarchies ch = PrepareContractionHierarchies.fromGraphHopperStorage(
                ghStorage, opts.getWeighting(), TraversalMode.NODE_BASED);
        ch.doWork();
        return ch;
    }

    @Test
    public void testPathRecursiveUnpacking() {
        // use an encoder where it is possible to store 2 weights per edge        
        FlagEncoder encoder = new Bike2WeightFlagEncoder();
        EncodingManager em = EncodingManager.create(encoder);
        ShortestWeighting weighting = new ShortestWeighting(encoder);
        GraphHopperStorage ghStorage = createGHStorage(em, Arrays.asList(weighting), false);
        CHGraphImpl g2 = (CHGraphImpl) ghStorage.getGraph(CHGraph.class, weighting);
        g2.edge(0, 1, 1, true);
        EdgeIteratorState iter1_1 = g2.edge(0, 2, 1.4, false);
        EdgeIteratorState iter1_2 = g2.edge(2, 5, 1.4, false);
        g2.edge(1, 2, 1, true);
        g2.edge(1, 3, 3, true);
        g2.edge(2, 3, 1, true);
        g2.edge(4, 3, 1, true);
        g2.edge(2, 5, 1.4, true);
        g2.edge(3, 5, 1, true);
        g2.edge(5, 6, 1, true);
        g2.edge(4, 6, 1, true);
        g2.edge(6, 7, 1, true);
        EdgeIteratorState iter2_2 = g2.edge(5, 7);
        iter2_2.setDistance(1.4).setFlags(GHUtility.setProperties(em.createEdgeFlags(), encoder, 10, true, false));

        ghStorage.freeze();
        // simulate preparation
        CHEdgeIteratorState iter2_1 = g2.shortcut(0, 5);
        iter2_1.setFlagsAndWeight(PrepareEncoder.getScFwdDir(), 1);
        iter2_1.setDistance(2.8);
        iter2_1.setSkippedEdges(iter1_1.getEdge(), iter1_2.getEdge());
        CHEdgeIteratorState tmp = g2.shortcut(0, 7);
        tmp.setFlagsAndWeight(PrepareEncoder.getScFwdDir(), 1);
        tmp.setDistance(4.2);
        tmp.setSkippedEdges(iter2_1.getEdge(), iter2_2.getEdge());
        g2.setLevel(1, 0);
        g2.setLevel(3, 1);
        g2.setLevel(4, 2);
        g2.setLevel(6, 3);
        g2.setLevel(2, 4);
        g2.setLevel(5, 5);
        g2.setLevel(7, 6);
        g2.setLevel(0, 7);

        AlgorithmOptions opts = new AlgorithmOptions(Parameters.Algorithms.DIJKSTRA_BI, weighting);
        Path p = new PrepareContractionHierarchies(
                g2, weighting, TraversalMode.NODE_BASED).
                createAlgo(g2, opts).calcPath(0, 7);

        assertEquals(IntArrayList.from(0, 2, 5, 7), p.calcNodes());
        assertEquals(1064, p.getTime());
        assertEquals(4.2, p.getDistance(), 1e-5);
    }

    @Test
    public void testBaseGraph() {
        CarFlagEncoder carFE = new CarFlagEncoder();
        EncodingManager em = EncodingManager.create(carFE);
        AlgorithmOptions opts = AlgorithmOptions.start().
                weighting(new ShortestWeighting(carFE)).build();
        GraphHopperStorage ghStorage = createGHStorage(em,
                Arrays.asList(opts.getWeighting()), false);

        initDirectedAndDiffSpeed(ghStorage, carFE);

        // do CH preparation for car        
        createFactory(ghStorage, opts);

        // use base graph for solving normal Dijkstra
        Path p1 = new RoutingAlgorithmFactorySimple().createAlgo(ghStorage, defaultOpts).calcPath(0, 3);
        assertEquals(IntArrayList.from(0, 1, 5, 2, 3), p1.calcNodes());
        assertEquals(p1.toString(), 402.29, p1.getDistance(), 1e-2);
        assertEquals(p1.toString(), 144823, p1.getTime());
    }

    @Test
    public void testBaseGraphMultipleVehicles() {
        AlgorithmOptions footOptions = AlgorithmOptions.start().
                weighting(new FastestWeighting(footEncoder)).build();
        AlgorithmOptions carOptions = AlgorithmOptions.start().
                weighting(new FastestWeighting(carEncoder)).build();

        GraphHopperStorage g = createGHStorage(encodingManager,
                Arrays.asList(footOptions.getWeighting(), carOptions.getWeighting()), false);
        initFootVsCar(g);

        // do CH preparation for car
        RoutingAlgorithmFactory contractedFactory = createFactory(g, carOptions);

        // use contracted graph
        Path p1 = contractedFactory.createAlgo(getGraph(g, carOptions.getWeighting()), carOptions).calcPath(0, 7);
        assertEquals(IntArrayList.from(0, 4, 6, 7), p1.calcNodes());
        assertEquals(p1.toString(), 15000, p1.getDistance(), 1e-6);

        // use base graph for solving normal Dijkstra via car
        Path p2 = new RoutingAlgorithmFactorySimple().createAlgo(g, carOptions).calcPath(0, 7);
        assertEquals(IntArrayList.from(0, 4, 6, 7), p2.calcNodes());
        assertEquals(p2.toString(), 15000, p2.getDistance(), 1e-6);
        assertEquals(p2.toString(), 2700 * 1000, p2.getTime());

        // use base graph for solving normal Dijkstra via foot
        Path p3 = new RoutingAlgorithmFactorySimple().createAlgo(g, footOptions).calcPath(0, 7);
        assertEquals(p3.toString(), 17000, p3.getDistance(), 1e-6);
        assertEquals(p3.toString(), 12240 * 1000, p3.getTime());
        assertEquals(IntArrayList.from(0, 4, 5, 7), p3.calcNodes());
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
        GraphHopperStorage graph = createGHStorage(false);
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
        CHGraph chGraph = graph.getGraph(CHGraph.class);

        // explicitly set the node levels equal to the node ids
        // the graph contraction with this ordering yields no shortcuts
        for (int i = 0; i < 10; ++i) {
            chGraph.setLevel(i, i);
        }
        graph.freeze();
        RoutingAlgorithm algo = createCHAlgo(graph, chGraph, true, defaultOpts);
        Path p = algo.calcPath(1, 0);
        // node 3 will be stalled and nodes 4-7 won't be explored --> we visit 7 nodes
        // note that node 9 will be visited by both forward and backward searches
        assertEquals(7, algo.getVisitedNodes());
        assertEquals(102, p.getDistance(), 1.e-3);
        assertEquals(p.toString(), IntArrayList.from(1, 8, 9, 0), p.calcNodes());

        // without stalling we visit 11 nodes
        RoutingAlgorithm algoNoSod = createCHAlgo(graph, chGraph, false, defaultOpts);
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
        runTestWithDirectionDependentEdgeSpeed(10, 20, 0, 2, IntArrayList.from(0, 1, 2), new MotorcycleFlagEncoder());
        runTestWithDirectionDependentEdgeSpeed(10, 20, 0, 2, IntArrayList.from(0, 1, 2), new Bike2WeightFlagEncoder());
    }

    // s(0)--fast->1--t(2)
    //    \        |
    //    slow     |
    //      \--<---|
    @Test
    public void testDirectionDependentSpeedBwdSearch() {
        runTestWithDirectionDependentEdgeSpeed(20, 10, 2, 0, IntArrayList.from(2, 1, 0), new MotorcycleFlagEncoder());
        runTestWithDirectionDependentEdgeSpeed(20, 10, 2, 0, IntArrayList.from(2, 1, 0), new Bike2WeightFlagEncoder());
    }

    private void runTestWithDirectionDependentEdgeSpeed(double speed, double revSpeed, int from, int to, IntArrayList expectedPath, FlagEncoder encoder) {
        EncodingManager encodingManager = EncodingManager.create(encoder);
        FastestWeighting weighting = new FastestWeighting(encoder);
        AlgorithmOptions algoOpts = AlgorithmOptions.start().weighting(weighting).build();
        GraphHopperStorage graph = createGHStorage(encodingManager, Arrays.asList(weighting), false);
        EdgeIteratorState edge = graph.edge(0, 1, 2, true);
        DecimalEncodedValue avSpeedEnc = encodingManager.getDecimalEncodedValue(EncodingManager.getKey(encoder, "average_speed"));
        edge.set(avSpeedEnc, speed).setReverse(avSpeedEnc, revSpeed);

        graph.edge(1, 2, 1, true);

        CHGraph chGraph = graph.getGraph(CHGraph.class);
        for (int i = 0; i < 3; ++i) {
            chGraph.setLevel(i, i);
        }
        graph.freeze();

        RoutingAlgorithm algo = createCHAlgo(graph, chGraph, true, algoOpts);
        Path p = algo.calcPath(from, to);
        assertEquals(3, p.getDistance(), 1.e-3);
        assertEquals(p.toString(), expectedPath, p.calcNodes());
    }

    private RoutingAlgorithm createCHAlgo(GraphHopperStorage graph, CHGraph chGraph, boolean withSOD, AlgorithmOptions algorithmOptions) {
        PrepareContractionHierarchies ch = new PrepareContractionHierarchies(
                chGraph, algorithmOptions.getWeighting(), TraversalMode.NODE_BASED);
        if (!withSOD) {
            algorithmOptions.getHints().put("stall_on_demand", false);
        }
        return ch.createAlgo(chGraph, algorithmOptions);
    }
}
