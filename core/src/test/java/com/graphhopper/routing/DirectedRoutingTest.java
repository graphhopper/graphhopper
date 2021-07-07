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

import com.carrotsearch.hppc.DoubleArrayList;
import com.carrotsearch.hppc.IntArrayList;
import com.graphhopper.routing.ch.CHRoutingAlgorithmFactory;
import com.graphhopper.routing.ch.PrepareContractionHierarchies;
import com.graphhopper.routing.lm.LMConfig;
import com.graphhopper.routing.lm.PrepareLandmarks;
import com.graphhopper.routing.querygraph.QueryGraph;
import com.graphhopper.routing.querygraph.QueryRoutingCHGraph;
import com.graphhopper.routing.util.*;
import com.graphhopper.routing.weighting.DefaultTurnCostProvider;
import com.graphhopper.routing.weighting.FastestWeighting;
import com.graphhopper.routing.weighting.Weighting;
import com.graphhopper.storage.*;
import com.graphhopper.storage.index.LocationIndexTree;
import com.graphhopper.storage.index.Snap;
import com.graphhopper.util.EdgeExplorer;
import com.graphhopper.util.EdgeIterator;
import com.graphhopper.util.GHUtility;
import com.graphhopper.util.PMap;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.ArgumentsProvider;
import org.junit.jupiter.params.provider.ArgumentsSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.function.IntToDoubleFunction;
import java.util.stream.Stream;

import static com.graphhopper.routing.weighting.Weighting.INFINITE_U_TURN_COSTS;
import static com.graphhopper.util.GHUtility.createRandomSnaps;
import static com.graphhopper.util.Parameters.Algorithms.ASTAR_BI;
import static com.graphhopper.util.Parameters.Algorithms.DIJKSTRA_BI;
import static com.graphhopper.util.Parameters.Routing.ALGORITHM;
import static org.junit.jupiter.api.Assertions.*;

/**
 * This test makes sure the different bidirectional routing algorithms correctly implement restrictions of the source/
 * target edges, by comparing with {@link DijkstraBidirectionRef}
 *
 * @author easbar
 * @see RandomizedRoutingTest
 * @see DirectedBidirectionalDijkstraTest
 */
public class DirectedRoutingTest {
    private static final Logger LOGGER = LoggerFactory.getLogger(DirectedRoutingTest.class);

    private static class Fixture {
        private final Algo algo;
        private final int uTurnCosts;
        private final boolean prepareCH;
        private final boolean prepareLM;
        private final Directory dir;
        private final GraphHopperStorage graph;
        private final CHConfig chConfig;
        private final LMConfig lmConfig;
        private final FlagEncoder encoder;
        private final TurnCostStorage turnCostStorage;
        private final int maxTurnCosts;
        private final Weighting weighting;
        private final EncodingManager encodingManager;
        private RoutingCHGraph routingCHGraph;
        private PrepareLandmarks lm;

        public Fixture(Algo algo, int uTurnCosts, boolean prepareCH, boolean prepareLM) {
            this.algo = algo;
            this.uTurnCosts = uTurnCosts;
            this.prepareCH = prepareCH;
            this.prepareLM = prepareLM;

            dir = new RAMDirectory();
            maxTurnCosts = 10;
            // todo: this test only works with speedTwoDirections=false (as long as loops are enabled), otherwise it will
            // fail sometimes for edge-based algorithms, #1631, but maybe we can should disable different fwd/bwd speeds
            // only for loops instead?
            encoder = new CarFlagEncoder(8, 1, maxTurnCosts);
            encodingManager = EncodingManager.create(encoder);
            graph = new GraphBuilder(encodingManager).setDir(dir).withTurnCosts(true).build();
            turnCostStorage = graph.getTurnCostStorage();
            weighting = new FastestWeighting(encoder, new DefaultTurnCostProvider(encoder, turnCostStorage, uTurnCosts));
            chConfig = CHConfig.edgeBased("p1", weighting);
            // important: for LM preparation we need to use a weighting without turn costs #1960
            lmConfig = new LMConfig("c2", new FastestWeighting(encoder));
            graph.addCHGraph(chConfig);
            graph.create(1000);
        }

        @Override
        public String toString() {
            return algo + ", u-turn-costs: " + uTurnCosts + ", prepareCH: " + prepareCH + ", prepareLM: " + prepareLM;
        }

        private void preProcessGraph() {
            graph.freeze();
            if (!prepareCH && !prepareLM) {
                return;
            }
            if (prepareCH) {
                PrepareContractionHierarchies pch = PrepareContractionHierarchies.fromGraphHopperStorage(graph, chConfig);
                pch.doWork();
                routingCHGraph = graph.getRoutingCHGraph(chConfig.getName());
            }
            if (prepareLM) {
                lm = new PrepareLandmarks(dir, graph, lmConfig, 16);
                lm.setMaximumWeight(1000);
                lm.doWork();
            }
        }

        private BidirRoutingAlgorithm createAlgo() {
            return createAlgo(graph);
        }

        private BidirRoutingAlgorithm createAlgo(Graph graph) {
            switch (algo) {
                case ASTAR:
                    return new AStarBidirection(graph, graph.wrapWeighting(weighting), TraversalMode.EDGE_BASED);
                case CH_DIJKSTRA: {
                    CHRoutingAlgorithmFactory algoFactory = graph instanceof QueryGraph
                            ? new CHRoutingAlgorithmFactory(new QueryRoutingCHGraph(routingCHGraph, (QueryGraph) graph))
                            : new CHRoutingAlgorithmFactory(routingCHGraph);
                    return algoFactory.createAlgo(new PMap().putObject(ALGORITHM, DIJKSTRA_BI));
                }
                case CH_ASTAR: {
                    CHRoutingAlgorithmFactory algoFactory = graph instanceof QueryGraph
                            ? new CHRoutingAlgorithmFactory(new QueryRoutingCHGraph(routingCHGraph, (QueryGraph) graph))
                            : new CHRoutingAlgorithmFactory(routingCHGraph);
                    return algoFactory.createAlgo(new PMap().putObject(ALGORITHM, ASTAR_BI));
                }
                case LM:
                    return (BidirRoutingAlgorithm) lm.getRoutingAlgorithmFactory().createAlgo(graph, weighting, new AlgorithmOptions().setAlgorithm(ASTAR_BI).setTraversalMode(TraversalMode.EDGE_BASED));
                default:
                    throw new IllegalArgumentException("unknown algo " + algo);
            }
        }

        private int getRandom(Random rnd) {
            return rnd.nextInt(graph.getNodes());
        }
    }

    private static class FixtureProvider implements ArgumentsProvider {
        @Override
        public Stream<? extends Arguments> provideArguments(ExtensionContext context) {
            return Stream.of(
                    new Fixture(Algo.ASTAR, INFINITE_U_TURN_COSTS, false, false),
                    new Fixture(Algo.CH_ASTAR, INFINITE_U_TURN_COSTS, true, false),
                    new Fixture(Algo.CH_DIJKSTRA, INFINITE_U_TURN_COSTS, true, false),
                    // todo: LM+directed still fails sometimes, #1971,
//                  new Fixture(Algo.LM, INFINITE_U_TURN_COSTS, false, true),
                    new Fixture(Algo.ASTAR, 40, false, false),
                    new Fixture(Algo.CH_ASTAR, 40, true, false),
                    new Fixture(Algo.CH_DIJKSTRA, 40, true, false)
                    // todo: LM+directed still fails sometimes, #1971,
//                  new Fixture(Algo.LM, 40, false, true),
            ).map(Arguments::of);
        }
    }

    private static class RepeatedFixtureProvider implements ArgumentsProvider {
        @Override
        public Stream<? extends Arguments> provideArguments(ExtensionContext context) {
            return Stream.generate(() -> new FixtureProvider().provideArguments(context)).limit(1).flatMap(s -> s);
        }
    }

    private enum Algo {
        ASTAR,
        CH_ASTAR,
        CH_DIJKSTRA,
        LM
    }

    @ParameterizedTest
    @ArgumentsSource(RepeatedFixtureProvider.class)
    public void randomGraph(Fixture f) {
        final long seed = System.nanoTime();
        final int numQueries = 50;
        Random rnd = new Random(seed);
        GHUtility.buildRandomGraph(f.graph, rnd, 100, 2.2, true, true,
                f.encoder.getAccessEnc(), f.encoder.getAverageSpeedEnc(), null, 0.7, 0.8, 0.8);
        GHUtility.addRandomTurnCosts(f.graph, seed, f.encodingManager, f.encoder, f.maxTurnCosts, f.turnCostStorage);
//        GHUtility.printGraphForUnitTest(f.graph, f.encoder);
        f.preProcessGraph();
        List<String> strictViolations = new ArrayList<>();
        for (int i = 0; i < numQueries; i++) {
            int source = f.getRandom(rnd);
            int target = f.getRandom(rnd);
//            LOGGER.info("source: " + source + ", target: " + target)
            IntToDoubleFunction calcStartEdgePenalty = getEdgePenalty(rnd, source, f.graph);
            IntToDoubleFunction calcTargetEdgePenalty = getEdgePenalty(rnd, target, f.graph);
            Path refPath = new DijkstraBidirectionRef(f.graph, ((Graph) f.graph).wrapWeighting(f.weighting), TraversalMode.EDGE_BASED)
                    .calcPath(source, target, calcStartEdgePenalty, calcTargetEdgePenalty);
            Path path = f.createAlgo()
                    .calcPath(source, target, calcStartEdgePenalty, calcTargetEdgePenalty);
            // do not check nodes, because there can be ambiguity when there are zero weight loops
            strictViolations.addAll(comparePaths(refPath, path, source, target, false, seed));
        }
        // sometimes there are multiple best paths with different distance/time, if this happens too often something
        // is wrong and we fail
        if (strictViolations.size() > Math.max(1, 0.05 * numQueries)) {
            for (String strictViolation : strictViolations) {
                LOGGER.info("strict violation: " + strictViolation);
            }
            fail("Too many strict violations, with seed: " + seed + " - " + strictViolations.size() + " / " + numQueries);
        }
    }

    @ParameterizedTest
    @ArgumentsSource(FixtureProvider.class)
    public void specificSample(Fixture f) {
        // should be covered by the random tests anyway, but adding this for debugging and double safety
        // 4 < 5
        // |   |
        // 0 - 1 - 2 - 3
        GHUtility.setSpeed(36, true, true, f.encoder, f.graph.edge(0, 1).setDistance(100));
        GHUtility.setSpeed(36, true, true, f.encoder, f.graph.edge(1, 2).setDistance(100));
        GHUtility.setSpeed(36, true, true, f.encoder, f.graph.edge(2, 3).setDistance(100));
        GHUtility.setSpeed(36, true, true, f.encoder, f.graph.edge(0, 4).setDistance(1000));
        GHUtility.setSpeed(36, true, false, f.encoder, f.graph.edge(5, 4).setDistance(1000));
        GHUtility.setSpeed(36, true, true, f.encoder, f.graph.edge(5, 1).setDistance(1000));
        f.preProcessGraph();
        {
            BidirRoutingAlgorithm algo = f.createAlgo();
            Path path = algo.calcPath(0, 3, e -> 10, e -> 15);
            assertTrue(path.isFound());
            assertEquals(300, path.getDistance());
            assertEquals(55, path.getWeight());
            assertEquals(30_000, path.getTime());
            assertEquals(IntArrayList.from(0, 1, 2, 3), path.calcNodes());
        }
        {
            BidirRoutingAlgorithm algo = f.createAlgo();
            Path path = algo.calcPath(0, 1, e -> 10, e -> 15);
            assertTrue(path.isFound());
            assertEquals(100, path.getDistance());
            assertEquals(35, path.getWeight());
            assertEquals(10_000, path.getTime());
            assertEquals(IntArrayList.from(0, 1), path.calcNodes());
        }
        {
            // going from one node to the same node, but there are some penalties
            BidirRoutingAlgorithm algo = f.createAlgo();
            Path path = algo.calcPath(0, 0, e -> 10, e -> 15);
            assertTrue(path.isFound());
            if (f.uTurnCosts == INFINITE_U_TURN_COSTS) {
                assertEquals(3100, path.getDistance());
                assertEquals(335, path.getWeight());
                assertEquals(310_000, path.getTime());
                assertEquals(IntArrayList.from(0, 1, 5, 4, 0), path.calcNodes());
            } else {
                assertEquals(200, path.getDistance());
                assertEquals(85, path.getWeight());
                assertEquals(60_000, path.getTime());
                assertEquals(IntArrayList.from(0, 1, 0), path.calcNodes());
            }
        }
        {
            // going from one node to the same node, but there are some (zero) 'penalties'
            BidirRoutingAlgorithm algo = f.createAlgo();
            Path path = algo.calcPath(0, 0, e -> 0, e -> 0);
            assertTrue(path.isFound());
            if (f.uTurnCosts == INFINITE_U_TURN_COSTS) {
                assertEquals(3100, path.getDistance());
                assertEquals(310, path.getWeight());
                assertEquals(310_000, path.getTime());
                assertEquals(IntArrayList.from(0, 1, 5, 4, 0), path.calcNodes());
            } else {
                assertEquals(200, path.getDistance());
                assertEquals(60, path.getWeight());
                assertEquals(60_000, path.getTime());
                assertEquals(IntArrayList.from(0, 1, 0), path.calcNodes());
            }
        }
        {
            // going from one node to the same node, penalties are null. in this case leaving the start node is not
            // enforced at all
            BidirRoutingAlgorithm algo = f.createAlgo();
            Path path = algo.calcPath(0, 0, null, null);
            assertTrue(path.isFound());
            assertEquals(0, path.getDistance());
            assertEquals(0, path.getWeight());
            assertEquals(0, path.getTime());
            assertEquals(IntArrayList.from(0), path.calcNodes());
        }
    }

    /**
     * Similar to {@link #randomGraph}, but using the {@link QueryGraph} as it is done in real usage.
     */
    @ParameterizedTest
    @ArgumentsSource(RepeatedFixtureProvider.class)
    public void randomGraph_withQueryGraph(Fixture f) {
        final long seed = System.nanoTime();
        final int numQueries = 50;

        // we may not use an offset when query graph is involved, otherwise traveling via virtual edges will not be
        // the same as taking the direct edge!
        double pOffset = 0;
        Random rnd = new Random(seed);
        GHUtility.buildRandomGraph(f.graph, rnd, 50, 2.2, true, true,
                f.encoder.getAccessEnc(), f.encoder.getAverageSpeedEnc(), null, 0.7, 0.8, pOffset);
        GHUtility.addRandomTurnCosts(f.graph, seed, f.encodingManager, f.encoder, f.maxTurnCosts, f.turnCostStorage);
        // GHUtility.printGraphForUnitTest(graph, encoder);
        f.preProcessGraph();
        LocationIndexTree index = new LocationIndexTree(f.graph, f.dir);
        index.prepareIndex();
        List<String> strictViolations = new ArrayList<>();
        for (int i = 0; i < numQueries; i++) {
            List<Snap> snaps = createRandomSnaps(f.graph.getBounds(), index, rnd, 2, true, EdgeFilter.ALL_EDGES);
            QueryGraph queryGraph = QueryGraph.create(f.graph, snaps);

            int source = snaps.get(0).getClosestNode();
            int target = snaps.get(1).getClosestNode();
            IntToDoubleFunction calcStartEdgePenalty = getEdgePenalty(rnd, source, queryGraph);
            IntToDoubleFunction calcTargetEdgePenalty = getEdgePenalty(rnd, target, queryGraph);
            Path refPath = new DijkstraBidirectionRef(queryGraph, ((Graph) queryGraph).wrapWeighting(f.weighting), TraversalMode.EDGE_BASED)
                    .calcPath(source, target, calcStartEdgePenalty, calcTargetEdgePenalty);
            Path path = f.createAlgo(queryGraph)
                    .calcPath(source, target, calcStartEdgePenalty, calcTargetEdgePenalty);

            // do not check nodes, because there can be ambiguity when there are zero weight loops
            strictViolations.addAll(comparePaths(refPath, path, source, target, false, seed));
        }
        // sometimes there are multiple best paths with different distance/time, if this happens too often something
        // is wrong and we fail
        if (strictViolations.size() > Math.max(1, 0.05 * numQueries)) {
            fail("Too many strict violations, with seed: " + seed + " - " + strictViolations.size() + " / " + numQueries);
        }
    }

    private List<String> comparePaths(Path refPath, Path path, int source, int target, boolean checkNodes, long seed) {
        List<String> strictViolations = new ArrayList<>();
        double refWeight = refPath.getWeight();
        double weight = path.getWeight();
        if (Math.abs(refWeight - weight) > 1.e-2) {
            LOGGER.warn("expected: " + refPath.calcNodes());
            LOGGER.warn("given:    " + path.calcNodes());
            fail("wrong weight: " + source + "->" + target + ", expected: " + refWeight + ", given: " + weight + ", seed: " + seed);
        }
        if (Math.abs(path.getDistance() - refPath.getDistance()) > 1.e-1) {
            strictViolations.add("wrong distance " + source + "->" + target + ", expected: " + refPath.getDistance() + ", given: " + path.getDistance());
        }
        if (Math.abs(path.getTime() - refPath.getTime()) > 50) {
            strictViolations.add("wrong time " + source + "->" + target + ", expected: " + refPath.getTime() + ", given: " + path.getTime());
        }
        if (checkNodes && !refPath.calcNodes().equals(path.calcNodes())) {
            strictViolations.add("wrong nodes " + source + "->" + target + "\nexpected: " + refPath.calcNodes() + "\ngiven:    " + path.calcNodes());
        }
        return strictViolations;
    }

    private IntToDoubleFunction getEdgePenalty(Random rnd, int node, Graph graph) {
        // sometimes do not restrict anything
        if (rnd.nextDouble() < 0.05) {
            return null;
        }
        // this is different than using null!
        if (rnd.nextDouble() < 0.05) {
            return e -> 0;
        }
        // sometimes restrict everything
        if (rnd.nextDouble() < 0.05) {
            return e -> Double.POSITIVE_INFINITY;
        }
        // use all edge explorer, sometimes we will find an edge we can restrict, sometimes we do not
        EdgeExplorer explorer = graph.createEdgeExplorer();
        EdgeIterator iter = explorer.setBaseNode(node);
        IntArrayList edgeIds = new IntArrayList();
        DoubleArrayList penalties = new DoubleArrayList();
        while (iter.next()) {
            edgeIds.add(iter.getOrigEdgeFirst());
            edgeIds.add(iter.getOrigEdgeLast());
            double penalty = rnd.nextDouble();
            if (penalty < 0.05)
                penalty = 0;
            if (penalty > 0.95)
                penalty = Double.POSITIVE_INFINITY;
            penalty *= 40;
            penalties.add(penalty);
            penalties.add(penalty);
        }
        // sometimes restrict all but one
        if (edgeIds.size() > 0 && rnd.nextDouble() < 0.05) {
            int one = edgeIds.get(rnd.nextInt(edgeIds.size()));
            return e -> e == one ? 0 : Double.POSITIVE_INFINITY;
        }
//        LOGGER.info("restricted edges: {}, penalties: {}", edgeIds, penalties);
        return e -> {
            int index = edgeIds.indexOf(e);
            return index >= 0 ? penalties.get(index) : 0;
        };
    }

}
