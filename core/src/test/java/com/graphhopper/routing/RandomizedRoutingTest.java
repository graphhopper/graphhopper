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

import com.graphhopper.core.util.PMap;
import com.carrotsearch.hppc.IntArrayList;
import com.carrotsearch.hppc.IntIndexedContainer;
import com.graphhopper.routing.ch.CHRoutingAlgorithmFactory;
import com.graphhopper.routing.ch.PrepareContractionHierarchies;
import com.graphhopper.routing.ev.*;
import com.graphhopper.routing.lm.*;
import com.graphhopper.routing.querygraph.QueryGraph;
import com.graphhopper.routing.querygraph.QueryRoutingCHGraph;
import com.graphhopper.routing.util.EdgeFilter;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.routing.util.TraversalMode;
import com.graphhopper.routing.weighting.DefaultTurnCostProvider;
import com.graphhopper.routing.weighting.FastestWeighting;
import com.graphhopper.routing.weighting.Weighting;
import com.graphhopper.storage.*;
import com.graphhopper.storage.index.LocationIndexTree;
import com.graphhopper.storage.index.Snap;
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
import static com.graphhopper.core.util.Parameters.Algorithms.*;
import static com.graphhopper.core.util.Parameters.Routing.ALGORITHM;
import com.graphhopper.util.ArrayUtil;
import com.graphhopper.util.EdgeExplorer;
import com.graphhopper.util.EdgeIterator;
import com.graphhopper.util.GHUtility;
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
            return Stream.<Supplier<Fixture>>of(
                    () -> new Fixture(Algo.DIJKSTRA, false, false, NODE_BASED),
                    () -> new Fixture(Algo.ASTAR_UNIDIR, false, false, NODE_BASED),
                    () -> new Fixture(Algo.ASTAR_BIDIR, false, false, NODE_BASED),
                    () -> new Fixture(Algo.CH_ASTAR, true, false, NODE_BASED),
                    () -> new Fixture(Algo.CH_DIJKSTRA, true, false, NODE_BASED),
                    () -> new Fixture(Algo.LM_UNIDIR, false, true, NODE_BASED),
                    () -> new Fixture(Algo.LM_BIDIR, false, true, NODE_BASED),
                    () -> new Fixture(Algo.DIJKSTRA, false, false, EDGE_BASED),
                    () -> new Fixture(Algo.ASTAR_UNIDIR, false, false, EDGE_BASED),
                    () -> new Fixture(Algo.ASTAR_BIDIR, false, false, EDGE_BASED),
                    () -> new Fixture(Algo.CH_ASTAR, true, false, EDGE_BASED),
                    () -> new Fixture(Algo.CH_DIJKSTRA, true, false, EDGE_BASED),
                    () -> new Fixture(Algo.LM_UNIDIR, false, true, EDGE_BASED),
                    () -> new Fixture(Algo.LM_BIDIR, false, true, EDGE_BASED),
                    () -> new Fixture(Algo.PERFECT_ASTAR, false, false, NODE_BASED)
            ).map(Arguments::of);
        }
    }

    private static class Fixture {
        private final Algo algo;
        private final boolean prepareCH;
        private final boolean prepareLM;
        private final TraversalMode traversalMode;
        private final BaseGraph graph;
        private final BooleanEncodedValue accessEnc;
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
            // todo: this test only works with speedTwoDirections=false (as long as loops are enabled), otherwise it will
            // fail sometimes for edge-based algorithms, #1631, but maybe we can should disable different fwd/bwd speeds
            // only for loops instead?
            accessEnc = new SimpleBooleanEncodedValue("access", true);
            speedEnc = new DecimalEncodedValueImpl("speed", 5, 5, false);
            turnCostEnc = TurnCost.create("car", maxTurnCosts);
            encodingManager = new EncodingManager.Builder().add(accessEnc).add(speedEnc).addTurnCostEncodedValue(turnCostEnc).add(Subnetwork.create("car")).build();
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
                    ? new FastestWeighting(accessEnc, speedEnc, new DefaultTurnCostProvider(turnCostEnc, graph.getTurnCostStorage()))
                    : new FastestWeighting(accessEnc, speedEnc);
            if (prepareCH) {
                CHConfig chConfig = traversalMode.isEdgeBased() ? CHConfig.edgeBased("p", weighting) : CHConfig.nodeBased("p", weighting);
                PrepareContractionHierarchies pch = PrepareContractionHierarchies.fromGraph(graph, chConfig);
                PrepareContractionHierarchies.Result res = pch.doWork();
                routingCHGraph = RoutingCHGraphImpl.fromGraph(graph, res.getCHStorage(), res.getCHConfig());
            }
            if (prepareLM) {
                // important: for LM preparation we need to use a weighting without turn costs #1960
                LMConfig lmConfig = new LMConfig("car", new FastestWeighting(accessEnc, speedEnc));
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

        private List<String> comparePaths(Path refPath, Path path, int source, int target, long seed) {
            List<String> strictViolations = new ArrayList<>();
            double refWeight = refPath.getWeight();
            double weight = path.getWeight();
            if (Math.abs(refWeight - weight) > 1.e-2) {
                LOGGER.warn("expected: " + refPath.calcNodes());
                LOGGER.warn("given:    " + path.calcNodes());
                LOGGER.warn("seed: " + seed);
                fail("wrong weight: " + source + "->" + target + "\nexpected: " + refWeight + "\ngiven:    " + weight + "\nseed: " + seed);
            }
            if (Math.abs(path.getDistance() - refPath.getDistance()) > 1.e-1) {
                strictViolations.add("wrong distance " + source + "->" + target + ", expected: " + refPath.getDistance() + ", given: " + path.getDistance());
            }
            if (Math.abs(path.getTime() - refPath.getTime()) > 50) {
                strictViolations.add("wrong time " + source + "->" + target + ", expected: " + refPath.getTime() + ", given: " + path.getTime());
            }
            IntIndexedContainer refNodes = refPath.calcNodes();
            IntIndexedContainer pathNodes = path.calcNodes();
            if (!refNodes.equals(pathNodes)) {
                // sometimes paths are only different because of a zero weight loop. we do not consider these as strict
                // violations, see: #1864
                boolean isStrictViolation = !ArrayUtil.withoutConsecutiveDuplicates(refNodes).equals(ArrayUtil.withoutConsecutiveDuplicates(pathNodes));
                // sometimes there are paths including an edge a-c that has the same distance as the two edges a-b-c. in this
                // case both options are valid best paths. we only check for this most simple and frequent case here...
                if (pathsEqualExceptOneEdge(refNodes, pathNodes))
                    isStrictViolation = false;
                if (isStrictViolation)
                    strictViolations.add("wrong nodes " + source + "->" + target + "\nexpected: " + refNodes + "\ngiven:    " + pathNodes);
            }
            return strictViolations;
        }

        /**
         * Sometimes the graph can contain edges like this:
         * A--C
         * \-B|
         * where A-C is the same distance as A-B-C. In this case the shortest path is not well defined in terms of nodes.
         * This method checks if two node-paths are equal except for such an edge.
         */
        private boolean pathsEqualExceptOneEdge(IntIndexedContainer p1, IntIndexedContainer p2) {
            if (p1.equals(p2))
                throw new IllegalArgumentException("paths are equal");
            if (Math.abs(p1.size() - p2.size()) != 1)
                return false;
            IntIndexedContainer shorterPath = p1.size() < p2.size() ? p1 : p2;
            IntIndexedContainer longerPath = p1.size() < p2.size() ? p2 : p1;
            if (shorterPath.size() < 2)
                return false;
            IntArrayList indicesWithDifferentNodes = new IntArrayList();
            for (int i = 1; i < shorterPath.size(); i++) {
                if (shorterPath.get(i - indicesWithDifferentNodes.size()) != longerPath.get(i)) {
                    indicesWithDifferentNodes.add(i);
                }
            }
            if (indicesWithDifferentNodes.size() != 1)
                return false;
            int b = indicesWithDifferentNodes.get(0);
            int a = b - 1;
            int c = b + 1;
            assert shorterPath.get(a) == longerPath.get(a);
            assert shorterPath.get(b) != longerPath.get(b);
            if (shorterPath.get(b) != longerPath.get(c))
                return false;
            double distABC = getMinDist(longerPath.get(a), longerPath.get(b)) + getMinDist(longerPath.get(b), longerPath.get(c));

            double distAC = getMinDist(shorterPath.get(a), longerPath.get(c));
            if (Math.abs(distABC - distAC) > 0.1)
                return false;
            LOGGER.info("Distance " + shorterPath.get(a) + "-" + longerPath.get(c) + " is the same as distance " +
                    longerPath.get(a) + "-" + longerPath.get(b) + "-" + longerPath.get(c) + " -> there are multiple possibilities " +
                    "for shortest paths");
            return true;
        }

        private double getMinDist(int p, int q) {
            EdgeExplorer explorer = graph.createEdgeExplorer();
            EdgeIterator iter = explorer.setBaseNode(p);
            double distance = Double.MAX_VALUE;
            while (iter.next())
                if (iter.getAdjNode() == q)
                    distance = Math.min(distance, iter.getDistance());
            return distance;
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
    public void randomGraph(Supplier<Fixture> fixtureSupplier) {
        Fixture f = fixtureSupplier.get();
        final long seed = System.nanoTime();
        final int numQueries = 50;
        Random rnd = new Random(seed);
        GHUtility.buildRandomGraph(f.graph, rnd, 100, 2.2, true, true,
                f.accessEnc, f.speedEnc, null, 0.7, 0.8, 0.8);
        GHUtility.addRandomTurnCosts(f.graph, seed, f.accessEnc, f.turnCostEnc, f.maxTurnCosts, f.turnCostStorage);
//        GHUtility.printGraphForUnitTest(f.graph, f.accessEnc, f.speedEnc);
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
            strictViolations.addAll(f.comparePaths(refPath, path, source, target, seed));
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
    public void randomGraph_withQueryGraph(Supplier<Fixture> fixtureSupplier) {
        Fixture f = fixtureSupplier.get();
        final long seed = System.nanoTime();
        final int numQueries = 50;

        // we may not use an offset when query graph is involved, otherwise traveling via virtual edges will not be
        // the same as taking the direct edge!
        double pOffset = 0;
        Random rnd = new Random(seed);
        GHUtility.buildRandomGraph(f.graph, rnd, 50, 2.2, true, true,
                f.accessEnc, f.speedEnc, null, 0.7, 0.8, pOffset);
        GHUtility.addRandomTurnCosts(f.graph, seed, f.accessEnc, f.turnCostEnc, f.maxTurnCosts, f.turnCostStorage);
//        GHUtility.printGraphForUnitTest(f.graph, f.accessEnc, f.speedEnc);
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
            strictViolations.addAll(f.comparePaths(refPath, path, source, target, seed));
        }
        // we do not do a strict check because there can be ambiguity, for example when there are zero weight loops.
        // however, when there are too many deviations we fail
        if (strictViolations.size() > 3) {
            LOGGER.warn(strictViolations.toString());
            fail("Too many strict violations: " + strictViolations.size() + " / " + numQueries + ", seed: " + seed);
        }
    }
}
