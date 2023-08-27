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
import com.graphhopper.routing.ev.DecimalEncodedValue;
import com.graphhopper.routing.ev.DecimalEncodedValueImpl;
import com.graphhopper.routing.ev.Subnetwork;
import com.graphhopper.routing.ev.TurnCost;
import com.graphhopper.routing.lm.LMConfig;
import com.graphhopper.routing.lm.LMRoutingAlgorithmFactory;
import com.graphhopper.routing.lm.LandmarkStorage;
import com.graphhopper.routing.lm.PrepareLandmarks;
import com.graphhopper.routing.querygraph.QueryGraph;
import com.graphhopper.routing.querygraph.QueryRoutingCHGraph;
import com.graphhopper.routing.subnetwork.PrepareRoutingSubnetworks;
import com.graphhopper.routing.util.EdgeFilter;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.routing.util.TraversalMode;
import com.graphhopper.routing.weighting.SpeedWeighting;
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
import java.util.Arrays;
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
 * This test makes sure the different routing algorithms correctly implement restrictions of the source/
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
        private final double uTurnCosts;
        private final boolean prepareCH;
        private final boolean prepareLM;
        private final Directory dir;
        private final BaseGraph graph;
        private final DecimalEncodedValue speedEnc;
        private final DecimalEncodedValue turnCostEnc;
        private final TurnCostStorage turnCostStorage;
        private final int maxTurnCosts;
        private Weighting weighting;
        private final EncodingManager encodingManager;
        private RoutingCHGraph routingCHGraph;
        private LandmarkStorage lm;

        public Fixture(Algo algo, double uTurnCosts, boolean prepareCH, boolean prepareLM) {
            this.algo = algo;
            this.uTurnCosts = uTurnCosts;
            this.prepareCH = prepareCH;
            this.prepareLM = prepareLM;

            dir = new RAMDirectory();
            maxTurnCosts = 10;
            speedEnc = new DecimalEncodedValueImpl("speed", 5, 5, true);
            turnCostEnc = TurnCost.create("car", maxTurnCosts);
            encodingManager = EncodingManager.start().add(speedEnc).addTurnCostEncodedValue(turnCostEnc).add(Subnetwork.create("c2")).build();
            graph = new BaseGraph.Builder(encodingManager).setDir(dir).withTurnCosts(true).create();
            turnCostStorage = graph.getTurnCostStorage();
        }

        @Override
        public String toString() {
            return algo + ", u-turn-costs: " + uTurnCosts + ", prepareCH: " + prepareCH + ", prepareLM: " + prepareLM;
        }

        private void preProcessGraph() {
            graph.freeze();
            weighting = new SpeedWeighting(speedEnc, turnCostEnc, turnCostStorage, uTurnCosts);
            if (!prepareCH && !prepareLM) {
                return;
            }
            if (prepareCH) {
                CHConfig chConfig = CHConfig.edgeBased("p1", weighting);
                PrepareContractionHierarchies pch = PrepareContractionHierarchies.fromGraph(graph, chConfig);
                PrepareContractionHierarchies.Result res = pch.doWork();
                routingCHGraph = RoutingCHGraphImpl.fromGraph(graph, res.getCHStorage(), res.getCHConfig());
            }
            if (prepareLM) {
                // important: for LM preparation we need to use a weighting without turn costs #1960
                LMConfig lmConfig = new LMConfig("c2", new SpeedWeighting(speedEnc));
                // we need the subnetwork EV for LM
                PrepareRoutingSubnetworks preparation = new PrepareRoutingSubnetworks(graph,
                        Arrays.asList(new PrepareRoutingSubnetworks.PrepareJob(encodingManager.getBooleanEncodedValue(Subnetwork.key("c2")), lmConfig.getWeighting())));
                preparation.setMinNetworkSize(0);
                preparation.doWork();

                PrepareLandmarks prepare = new PrepareLandmarks(dir, graph, encodingManager, lmConfig, 16);
                prepare.setMaximumWeight(1000);
                prepare.doWork();
                lm = prepare.getLandmarkStorage();
            }
        }

        private EdgeToEdgeRoutingAlgorithm createAlgo() {
            return createAlgo(graph);
        }

        private EdgeToEdgeRoutingAlgorithm createAlgo(Graph graph) {
            switch (algo) {
                case ASTAR_UNI_BEELINE:
                    return new AStar(graph, graph.wrapWeighting(weighting), TraversalMode.EDGE_BASED);
                case ASTAR_BI_BEELINE:
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
                    return (EdgeToEdgeRoutingAlgorithm) new LMRoutingAlgorithmFactory(lm).createAlgo(graph, weighting, new AlgorithmOptions().setAlgorithm(ASTAR_BI).setTraversalMode(TraversalMode.EDGE_BASED));
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
                    new Fixture(Algo.ASTAR_UNI_BEELINE, Double.POSITIVE_INFINITY, false, false),
                    new Fixture(Algo.ASTAR_BI_BEELINE, Double.POSITIVE_INFINITY, false, false),
                    new Fixture(Algo.CH_ASTAR, Double.POSITIVE_INFINITY, true, false),
                    new Fixture(Algo.CH_DIJKSTRA, Double.POSITIVE_INFINITY, true, false),
                    // todo: LM+directed still fails sometimes, #1971,
                    // todonow: comment out again
                    new Fixture(Algo.LM, Double.POSITIVE_INFINITY, false, true),
                    new Fixture(Algo.ASTAR_UNI_BEELINE, 40, false, false),
                    new Fixture(Algo.ASTAR_BI_BEELINE, 40, false, false),
                    new Fixture(Algo.CH_ASTAR, 40, true, false),
                    new Fixture(Algo.CH_DIJKSTRA, 40, true, false),
                    // todo: LM+directed still fails sometimes, #1971,
                    // todonow: comment out again
                    new Fixture(Algo.LM, 40, false, true)
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
        ASTAR_UNI_BEELINE,
        ASTAR_BI_BEELINE,
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
        GHUtility.buildRandomGraph(f.graph, rnd, 100, 2.2, true, null, f.speedEnc, null, 0.8, 0.8);
        GHUtility.addRandomTurnCosts(f.graph, seed, null, f.turnCostEnc, f.maxTurnCosts, f.turnCostStorage);
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
        GHUtility.buildRandomGraph(f.graph, rnd, 50, 2.2, true, null, f.speedEnc, null, 0.8, pOffset);
        GHUtility.addRandomTurnCosts(f.graph, seed, null, f.turnCostEnc, f.maxTurnCosts, f.turnCostStorage);
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

    // todonow: create a new test for 2581/1971

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
        // use all edge explorer, sometimes we will find an edge we can restrict, sometimes we do not
        EdgeExplorer explorer = graph.createEdgeExplorer();
        EdgeIterator iter = explorer.setBaseNode(node);
        List<Integer> edgeIds = new ArrayList<>();
        while (iter.next())
            edgeIds.add(iter.getEdge());
        return edgeIds.isEmpty() ? ANY_EDGE : edgeIds.get(rnd.nextInt(edgeIds.size()));
    }

}
