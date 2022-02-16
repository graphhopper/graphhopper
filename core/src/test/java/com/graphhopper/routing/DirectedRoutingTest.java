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
import com.graphhopper.routing.lm.LMConfig;
import com.graphhopper.routing.lm.LMRoutingAlgorithmFactory;
import com.graphhopper.routing.lm.LandmarkStorage;
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
import java.util.stream.Stream;

import static com.graphhopper.routing.weighting.Weighting.INFINITE_U_TURN_COSTS;
import static com.graphhopper.util.EdgeIterator.ANY_EDGE;
import static com.graphhopper.util.EdgeIterator.NO_EDGE;
import static com.graphhopper.util.GHUtility.createRandomSnaps;
import static com.graphhopper.util.Parameters.Algorithms.ASTAR_BI;
import static com.graphhopper.util.Parameters.Algorithms.DIJKSTRA_BI;
import static com.graphhopper.util.Parameters.Routing.ALGORITHM;
import static org.junit.jupiter.api.Assertions.fail;

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
        private LandmarkStorage lm;

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
            encoder = new CarFlagEncoder(5, 5, maxTurnCosts);
            encodingManager = EncodingManager.create(encoder);
            graph = new GraphBuilder(encodingManager).setDir(dir).withTurnCosts(true).create();
            turnCostStorage = graph.getTurnCostStorage();
            weighting = new FastestWeighting(encoder, new DefaultTurnCostProvider(encoder, turnCostStorage, uTurnCosts));
            chConfig = CHConfig.edgeBased("p1", weighting);
            // important: for LM preparation we need to use a weighting without turn costs #1960
            lmConfig = new LMConfig("c2", new FastestWeighting(encoder));
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
                PrepareContractionHierarchies.Result res = pch.doWork();
                routingCHGraph = graph.createCHGraph(res.getCHStorage(), res.getCHConfig());
            }
            if (prepareLM) {
                PrepareLandmarks prepare = new PrepareLandmarks(dir, graph, lmConfig, 16);
                prepare.setMaximumWeight(1000);
                prepare.doWork();
                lm = prepare.getLandmarkStorage();
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
                    return (BidirRoutingAlgorithm) new LMRoutingAlgorithmFactory(lm).createAlgo(graph, weighting, new AlgorithmOptions().setAlgorithm(ASTAR_BI).setTraversalMode(TraversalMode.EDGE_BASED));
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
            return Stream.generate(() -> new FixtureProvider().provideArguments(context)).limit(10).flatMap(s -> s);
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
            int sourceOutEdge = getSourceOutEdge(rnd, source, f.graph);
            int targetInEdge = getTargetInEdge(rnd, target, f.graph);
//            LOGGER.info("source: " + source + ", target: " + target + ", sourceOutEdge: " + sourceOutEdge + ", targetInEdge: " + targetInEdge);
            Path refPath = new DijkstraBidirectionRef(f.graph, ((Graph) f.graph).wrapWeighting(f.weighting), TraversalMode.EDGE_BASED)
                    .calcPath(source, target, sourceOutEdge, targetInEdge);
            Path path = f.createAlgo()
                    .calcPath(source, target, sourceOutEdge, targetInEdge);
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
            Random tmpRnd1 = new Random(seed);
            int sourceOutEdge = getSourceOutEdge(tmpRnd1, source, queryGraph);
            int targetInEdge = getTargetInEdge(tmpRnd1, target, queryGraph);
            Random tmpRnd2 = new Random(seed);
            int chSourceOutEdge = getSourceOutEdge(tmpRnd2, source, queryGraph);
            int chTargetInEdge = getTargetInEdge(tmpRnd2, target, queryGraph);

            Path refPath = new DijkstraBidirectionRef(queryGraph, ((Graph) queryGraph).wrapWeighting(f.weighting), TraversalMode.EDGE_BASED)
                    .calcPath(source, target, sourceOutEdge, targetInEdge);
            Path path = f.createAlgo(queryGraph)
                    .calcPath(source, target, chSourceOutEdge, chTargetInEdge);

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

    private int getTargetInEdge(Random rnd, int node, Graph graph) {
        return getAdjEdge(rnd, node, graph);
    }

    private int getSourceOutEdge(Random rnd, int node, Graph graph) {
        return getAdjEdge(rnd, node, graph);
    }

    private int getAdjEdge(Random rnd, int node, Graph graph) {
        // sometimes do not restrict anything
        if (rnd.nextDouble() < 0.05) {
            return ANY_EDGE;
        }
        // sometimes use NO_EDGE
        if (rnd.nextDouble() < 0.05) {
            return NO_EDGE;
        }
        // use all edge explorer, sometimes we will find an edge we can restrict sometimes we do not
        EdgeExplorer explorer = graph.createEdgeExplorer();
        EdgeIterator iter = explorer.setBaseNode(node);
        List<Integer> edgeIds = new ArrayList<>();
        while (iter.next()) {
            edgeIds.add(iter.getOrigEdgeFirst());
            edgeIds.add(iter.getOrigEdgeLast());
        }
        return edgeIds.isEmpty() ? ANY_EDGE : edgeIds.get(rnd.nextInt(edgeIds.size()));
    }

}
