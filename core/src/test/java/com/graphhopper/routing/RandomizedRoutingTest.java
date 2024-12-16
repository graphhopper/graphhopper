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

import com.graphhopper.routing.ch.CHRoutingAlgorithmFactory;
import com.graphhopper.routing.ch.PrepareContractionHierarchies;
import com.graphhopper.routing.ev.*;
import com.graphhopper.routing.lm.*;
import com.graphhopper.routing.querygraph.QueryGraph;
import com.graphhopper.routing.querygraph.QueryRoutingCHGraph;
import com.graphhopper.routing.util.EdgeFilter;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.routing.util.TraversalMode;
import com.graphhopper.routing.weighting.SpeedWeighting;
import com.graphhopper.routing.weighting.Weighting;
import com.graphhopper.storage.*;
import com.graphhopper.storage.index.LocationIndexTree;
import com.graphhopper.storage.index.Snap;
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
import java.util.function.Supplier;
import java.util.stream.Stream;

import static com.graphhopper.routing.util.TraversalMode.EDGE_BASED;
import static com.graphhopper.routing.util.TraversalMode.NODE_BASED;
import static com.graphhopper.util.GHUtility.createRandomSnaps;
import static com.graphhopper.util.Parameters.Algorithms.*;
import static com.graphhopper.util.Parameters.Routing.ALGORITHM;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * This test compares different routing algorithms with {@link DijkstraBidirectionRef}. Most prominently it uses
 * randomly create graphs to create all sorts of different situations.
 *
 * @author easbar
 * @see RandomCHRoutingTest - similar but only tests CH algorithms
 * @see DirectedRoutingTest - similar but focuses on edge-based algorithms and directed queries
 */
public class RandomizedRoutingTest {
    private static final Logger LOGGER = LoggerFactory.getLogger(RandomizedRoutingTest.class);

    private static class FixtureProvider implements ArgumentsProvider {
        @Override
        public Stream<? extends Arguments> provideArguments(ExtensionContext context) {
            return Stream.of(
                    FixtureSupplier.create(Algo.DIJKSTRA, false, false, NODE_BASED),
                    FixtureSupplier.create(Algo.ASTAR_UNIDIR, false, false, NODE_BASED),
                    FixtureSupplier.create(Algo.ASTAR_BIDIR, false, false, NODE_BASED),
                    FixtureSupplier.create(Algo.CH_ASTAR, true, false, NODE_BASED),
                    FixtureSupplier.create(Algo.CH_DIJKSTRA, true, false, NODE_BASED),
                    FixtureSupplier.create(Algo.LM_UNIDIR, false, true, NODE_BASED),
                    FixtureSupplier.create(Algo.LM_BIDIR, false, true, NODE_BASED),
                    FixtureSupplier.create(Algo.DIJKSTRA, false, false, EDGE_BASED),
                    FixtureSupplier.create(Algo.ASTAR_UNIDIR, false, false, EDGE_BASED),
                    FixtureSupplier.create(Algo.ASTAR_BIDIR, false, false, EDGE_BASED),
                    FixtureSupplier.create(Algo.CH_ASTAR, true, false, EDGE_BASED),
                    FixtureSupplier.create(Algo.CH_DIJKSTRA, true, false, EDGE_BASED),
                    FixtureSupplier.create(Algo.LM_UNIDIR, false, true, EDGE_BASED),
                    FixtureSupplier.create(Algo.LM_BIDIR, false, true, EDGE_BASED),
                    FixtureSupplier.create(Algo.PERFECT_ASTAR, false, false, NODE_BASED)
            ).map(Arguments::of);
        }
    }

    private static class FixtureSupplier {
        private final Supplier<Fixture> supplier;
        private final String name;

        static FixtureSupplier create(Algo algo, boolean prepareCH, boolean prepareLM, TraversalMode traversalMode) {
            return new FixtureSupplier(() -> new Fixture(algo, prepareCH, prepareLM, traversalMode), algo.toString());
        }

        public FixtureSupplier(Supplier<Fixture> supplier, String name) {
            this.supplier = supplier;
            this.name = name;
        }

        @Override
        public String toString() {
            return name;
        }
    }


    private static class Fixture {
        private final Algo algo;
        private final boolean prepareCH;
        private final boolean prepareLM;
        private final TraversalMode traversalMode;
        private final BaseGraph graph;
        private final DecimalEncodedValue speedEnc;
        private final DecimalEncodedValue turnCostEnc;
        private final TurnCostStorage turnCostStorage;
        private final int maxTurnCosts;
        private Weighting weighting;
        private final EncodingManager encodingManager;
        private RoutingCHGraph routingCHGraph;
        private LandmarkStorage lm;

        Fixture(Algo algo, boolean prepareCH, boolean prepareLM, TraversalMode traversalMode) {
            this.algo = algo;
            this.prepareCH = prepareCH;
            this.prepareLM = prepareLM;
            this.traversalMode = traversalMode;
            maxTurnCosts = 10;
            speedEnc = new DecimalEncodedValueImpl("speed", 5, 5, true);
            turnCostEnc = TurnCost.create("car", maxTurnCosts);
            encodingManager = new EncodingManager.Builder().add(speedEnc).addTurnCostEncodedValue(turnCostEnc).add(Subnetwork.create("car")).build();
            graph = new BaseGraph.Builder(encodingManager)
                    .withTurnCosts(true)
                    .create();
            turnCostStorage = graph.getTurnCostStorage();
        }

        @Override
        public String toString() {
            return algo + ", " + traversalMode;
        }

        private void preProcessGraph() {
            graph.freeze();
            weighting = traversalMode.isEdgeBased()
                    ? new SpeedWeighting(speedEnc, turnCostEnc, graph.getTurnCostStorage(), Double.POSITIVE_INFINITY)
                    : new SpeedWeighting(speedEnc);
            if (prepareCH) {
                CHConfig chConfig = traversalMode.isEdgeBased() ? CHConfig.edgeBased("p", weighting) : CHConfig.nodeBased("p", weighting);
                PrepareContractionHierarchies pch = PrepareContractionHierarchies.fromGraph(graph, chConfig);
                PrepareContractionHierarchies.Result res = pch.doWork();
                routingCHGraph = RoutingCHGraphImpl.fromGraph(graph, res.getCHStorage(), res.getCHConfig());
            }
            if (prepareLM) {
                // important: for LM preparation we need to use a weighting without turn costs #1960
                LMConfig lmConfig = new LMConfig("car", new SpeedWeighting(speedEnc));
                PrepareLandmarks prepare = new PrepareLandmarks(graph.getDirectory(), graph, encodingManager, lmConfig, 16);
                prepare.setMaximumWeight(10000);
                prepare.doWork();
                lm = prepare.getLandmarkStorage();
            }
        }

        private RoutingAlgorithm createAlgo() {
            return createAlgo(graph);
        }

        private RoutingAlgorithm createAlgo(Graph graph) {
            switch (algo) {
                case DIJKSTRA:
                    return new Dijkstra(graph, graph.wrapWeighting(weighting), traversalMode);
                case ASTAR_UNIDIR:
                    return new AStar(graph, graph.wrapWeighting(weighting), traversalMode);
                case ASTAR_BIDIR:
                    return new AStarBidirection(graph, graph.wrapWeighting(weighting), traversalMode);
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
                case LM_BIDIR:
                    return new LMRoutingAlgorithmFactory(lm).createAlgo(graph, weighting, new AlgorithmOptions().setAlgorithm(ASTAR_BI).setTraversalMode(traversalMode));
                case LM_UNIDIR:
                    return new LMRoutingAlgorithmFactory(lm).createAlgo(graph, weighting, new AlgorithmOptions().setAlgorithm(ASTAR).setTraversalMode(traversalMode));
                case PERFECT_ASTAR: {
                    AStarBidirection perfectAStarBi = new AStarBidirection(graph, weighting, traversalMode);
                    perfectAStarBi.setApproximation(new PerfectApproximator(graph, weighting, traversalMode, false));
                    return perfectAStarBi;
                }
                default:
                    throw new IllegalArgumentException("unknown algo " + algo);
            }
        }

    }

    private enum Algo {
        DIJKSTRA,
        ASTAR_BIDIR,
        ASTAR_UNIDIR,
        CH_ASTAR,
        CH_DIJKSTRA,
        LM_BIDIR,
        LM_UNIDIR,
        PERFECT_ASTAR
    }

    private static class RepeatedFixtureProvider implements ArgumentsProvider {
        @Override
        public Stream<? extends Arguments> provideArguments(ExtensionContext context) {
            return Stream.generate(() -> new FixtureProvider().provideArguments(context)).limit(5).flatMap(s -> s);
        }
    }

    @ParameterizedTest
    @ArgumentsSource(RepeatedFixtureProvider.class)
    public void randomGraph(FixtureSupplier fixtureSupplier) {
        Fixture f = fixtureSupplier.supplier.get();
        final long seed = System.nanoTime();
        final int numQueries = 50;
        Random rnd = new Random(seed);
        GHUtility.buildRandomGraph(f.graph, rnd, 100, 2.2, true, f.speedEnc, null, 0.8, 0.8);
        GHUtility.addRandomTurnCosts(f.graph, seed, null, f.turnCostEnc, f.maxTurnCosts, f.turnCostStorage);
//        GHUtility.printGraphForUnitTest(f.graph, null, f.speedEnc);
        f.preProcessGraph();
        List<String> strictViolations = new ArrayList<>();
        for (int i = 0; i < numQueries; i++) {
            int source = rnd.nextInt(f.graph.getNodes());
            int target = rnd.nextInt(f.graph.getNodes());
//            LOGGER.info("source: " + source + ", target: " + target);
            Path refPath = new DijkstraBidirectionRef(f.graph, f.weighting, f.traversalMode)
                    .calcPath(source, target);
            Path path = f.createAlgo()
                    .calcPath(source, target);
            strictViolations.addAll(GHUtility.comparePaths(refPath, path, source, target, seed));
        }
        if (strictViolations.size() > 3) {
            for (String strictViolation : strictViolations) {
                LOGGER.info("strict violation: " + strictViolation);
            }
            fail("Too many strict violations: " + strictViolations.size() + " / " + numQueries + ", seed: " + seed);
        }
    }

    /**
     * Similar to {@link #randomGraph}, but using the {@link QueryGraph} as it is done in real usage.
     */
    @ParameterizedTest
    @ArgumentsSource(RepeatedFixtureProvider.class)
    public void randomGraph_withQueryGraph(FixtureSupplier fixtureSupplier) {
        Fixture f = fixtureSupplier.supplier.get();
        final long seed = System.nanoTime();
        final int numQueries = 50;

        // we may not use an offset when query graph is involved, otherwise traveling via virtual edges will not be
        // the same as taking the direct edge!
        double pOffset = 0;
        Random rnd = new Random(seed);
        GHUtility.buildRandomGraph(f.graph, rnd, 50, 2.2, true, f.speedEnc, null, 0.8, pOffset);
        GHUtility.addRandomTurnCosts(f.graph, seed, null, f.turnCostEnc, f.maxTurnCosts, f.turnCostStorage);
//        GHUtility.printGraphForUnitTest(f.graph, null, f.speedEnc);
        f.preProcessGraph();
        LocationIndexTree index = new LocationIndexTree(f.graph, f.graph.getDirectory());
        index.prepareIndex();
        List<String> strictViolations = new ArrayList<>();
        for (int i = 0; i < numQueries; i++) {
            List<Snap> snaps = createRandomSnaps(f.graph.getBounds(), index, rnd, 2, true, EdgeFilter.ALL_EDGES);
            QueryGraph queryGraph = QueryGraph.create(f.graph, snaps);

            int source = snaps.get(0).getClosestNode();
            int target = snaps.get(1).getClosestNode();

            Path refPath = new DijkstraBidirectionRef(queryGraph, queryGraph.wrapWeighting(f.weighting), f.traversalMode).calcPath(source, target);
            Path path = f.createAlgo(queryGraph).calcPath(source, target);
            strictViolations.addAll(GHUtility.comparePaths(refPath, path, source, target, seed));
        }
        // we do not do a strict check because there can be ambiguity, for example when there are zero weight loops.
        // however, when there are too many deviations we fail
        if (strictViolations.size() > 3) {
            LOGGER.warn(strictViolations.toString());
            fail("Too many strict violations: " + strictViolations.size() + " / " + numQueries + ", seed: " + seed);
        }
    }
}
