package com.graphhopper.routing;

import com.graphhopper.routing.ch.CHRoutingAlgorithmFactory;
import com.graphhopper.routing.ch.PrepareContractionHierarchies;
import com.graphhopper.routing.ev.*;
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
import com.graphhopper.core.util.EdgeIteratorState;
import com.graphhopper.core.util.GHUtility;
import com.graphhopper.util.Helper;
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
import static com.graphhopper.core.util.GHUtility.createRandomSnaps;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;
import static org.junit.jupiter.api.Assumptions.assumeFalse;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

public class RandomCHRoutingTest {
    private static final Logger LOGGER = LoggerFactory.getLogger(RandomCHRoutingTest.class);

    private static final class Fixture {
        private final TraversalMode traversalMode;
        private final int maxTurnCosts;
        private final int uTurnCosts;
        private final BooleanEncodedValue accessEnc;
        private final DecimalEncodedValue speedEnc;
        private final DecimalEncodedValue turnCostEnc;
        private Weighting weighting;
        private final BaseGraph graph;
        private CHConfig chConfig;

        Fixture(TraversalMode traversalMode, int uTurnCosts) {
            this.traversalMode = traversalMode;
            this.maxTurnCosts = 10;
            this.uTurnCosts = uTurnCosts;
            accessEnc = new SimpleBooleanEncodedValue("access", true);
            speedEnc = new DecimalEncodedValueImpl("speed", 5, 5, false);
            turnCostEnc = TurnCost.create("car", maxTurnCosts);
            EncodingManager encodingManager = EncodingManager.start().add(accessEnc).add(speedEnc).addTurnCostEncodedValue(turnCostEnc).build();
            graph = new BaseGraph.Builder(encodingManager).withTurnCosts(true).create();
        }

        void freeze() {
            graph.freeze();
            chConfig = traversalMode.isEdgeBased()
                    ? CHConfig.edgeBased("p", new FastestWeighting(accessEnc, speedEnc, new DefaultTurnCostProvider(turnCostEnc, graph.getTurnCostStorage(), uTurnCosts)))
                    : CHConfig.nodeBased("p", new FastestWeighting(accessEnc, speedEnc));
            weighting = chConfig.getWeighting();
        }

        @Override
        public String toString() {
            return traversalMode + ", u-turn-costs=" + uTurnCosts;
        }
    }

    private static class FixtureProvider implements ArgumentsProvider {
        @Override
        public Stream<? extends Arguments> provideArguments(ExtensionContext context) {
            return Stream.of(
                            new Fixture(TraversalMode.NODE_BASED, INFINITE_U_TURN_COSTS),
                            new Fixture(TraversalMode.EDGE_BASED, 40),
                            new Fixture(TraversalMode.EDGE_BASED, INFINITE_U_TURN_COSTS)
                    )
                    .map(Arguments::of);
        }
    }

    /**
     * Runs random routing queries on a random query/CH graph with random speeds and adding random virtual edges and
     * nodes.
     */
    @ParameterizedTest
    @ArgumentsSource(FixtureProvider.class)
    public void random(Fixture f) {
        // you might have to keep this test running in an infinite loop for several minutes to find potential routing
        // bugs (e.g. use intellij 'run until stop/failure').
        int numNodes = 50;
        long seed = System.nanoTime();
        LOGGER.info("seed: " + seed);
        Random rnd = new Random(seed);
        // we may not use an offset when query graph is involved, otherwise traveling via virtual edges will not be
        // the same as taking the direct edge!
        double pOffset = 0;
        GHUtility.buildRandomGraph(f.graph, rnd, numNodes, 2.5, true, true,
                f.accessEnc, f.speedEnc, null, 0.7, 0.9, pOffset);
        if (f.traversalMode.isEdgeBased()) {
            GHUtility.addRandomTurnCosts(f.graph, seed, f.accessEnc, f.turnCostEnc, f.maxTurnCosts, f.graph.getTurnCostStorage());
        }
        runRandomTest(f, rnd, 20);
    }

    @ParameterizedTest
    @ArgumentsSource(FixtureProvider.class)
    public void issue1574_1(Fixture f) {
        assumeFalse(f.traversalMode.isEdgeBased());
        Random rnd = new Random(9348906923700L);
        buildRandomGraphLegacy(f.graph, f.accessEnc, f.speedEnc, rnd, 50, 2.5, false, true, 0.9);
        runRandomTest(f, rnd, 20);
    }

    @ParameterizedTest
    @ArgumentsSource(FixtureProvider.class)
    public void issue1574_2(Fixture f) {
        assumeFalse(f.traversalMode.isEdgeBased());
        Random rnd = new Random(10093639220394L);
        buildRandomGraphLegacy(f.graph, f.accessEnc, f.speedEnc, rnd, 50, 2.5, false, true, 0.9);
        runRandomTest(f, rnd, 20);
    }

    @ParameterizedTest
    @ArgumentsSource(FixtureProvider.class)
    public void issue1582(Fixture f) {
        assumeFalse(f.traversalMode.isEdgeBased());
        Random rnd = new Random(4111485945982L);
        buildRandomGraphLegacy(f.graph, f.accessEnc, f.speedEnc, rnd, 10, 2.5, false, true, 0.9);
        runRandomTest(f, rnd, 100);
    }

    @ParameterizedTest
    @ArgumentsSource(FixtureProvider.class)
    public void issue1583(Fixture f) {
        assumeFalse(f.traversalMode.isEdgeBased());
        Random rnd = new Random(10785899964423L);
        buildRandomGraphLegacy(f.graph, f.accessEnc, f.speedEnc, rnd, 50, 2.5, true, true, 0.9);
        runRandomTest(f, rnd, 20);
    }

    @ParameterizedTest
    @ArgumentsSource(FixtureProvider.class)
    public void issue1593(Fixture f) {
        assumeTrue(f.traversalMode.isEdgeBased());
        long seed = 60643479675316L;
        Random rnd = new Random(seed);
        GHUtility.buildRandomGraph(f.graph, rnd, 50, 2.5, true, true,
                f.accessEnc, f.speedEnc, null, 0.7, 0.9, 0.0);
        GHUtility.addRandomTurnCosts(f.graph, seed, f.accessEnc, f.turnCostEnc, f.maxTurnCosts, f.graph.getTurnCostStorage());
        runRandomTest(f, rnd, 20);
    }

    private void runRandomTest(Fixture f, Random rnd, int numVirtualNodes) {
        LocationIndexTree locationIndex = new LocationIndexTree(f.graph, f.graph.getDirectory());
        locationIndex.prepareIndex();

        f.freeze();
        PrepareContractionHierarchies pch = PrepareContractionHierarchies.fromGraph(f.graph, f.chConfig);
        PrepareContractionHierarchies.Result res = pch.doWork();
        RoutingCHGraph chGraph = RoutingCHGraphImpl.fromGraph(f.graph, res.getCHStorage(), res.getCHConfig());

        int numQueryGraph = 25;
        for (int j = 0; j < numQueryGraph; j++) {
            // add virtual nodes and edges, because they can change the routing behavior and/or produce bugs, e.g.
            // when via-points are used
            List<Snap> snaps = createRandomSnaps(f.graph.getBounds(), locationIndex, rnd, numVirtualNodes, false, EdgeFilter.ALL_EDGES);
            QueryGraph queryGraph = QueryGraph.create(f.graph, snaps);

            int numQueries = 100;
            int numPathsNotFound = 0;
            List<String> strictViolations = new ArrayList<>();
            for (int i = 0; i < numQueries; i++) {
                int from = rnd.nextInt(queryGraph.getNodes());
                int to = rnd.nextInt(queryGraph.getNodes());
                Weighting w = queryGraph.wrapWeighting(f.weighting);
                // using plain dijkstra instead of bidirectional, because of #1592
                RoutingAlgorithm refAlgo = new Dijkstra(queryGraph, w, f.traversalMode);
                Path refPath = refAlgo.calcPath(from, to);
                double refWeight = refPath.getWeight();

                QueryRoutingCHGraph routingCHGraph = new QueryRoutingCHGraph(chGraph, queryGraph);
                RoutingAlgorithm algo = new CHRoutingAlgorithmFactory(routingCHGraph).createAlgo(new PMap().putObject("stall_on_demand", true));

                Path path = algo.calcPath(from, to);
                if (refPath.isFound() && !path.isFound())
                    fail("path not found for " + from + "->" + to + ", expected weight: " + refWeight);
                assertEquals(refPath.isFound(), path.isFound());
                if (!path.isFound()) {
                    numPathsNotFound++;
                    continue;
                }

                double weight = path.getWeight();
                if (Math.abs(refWeight - weight) > 1.e-2) {
                    LOGGER.warn("expected: " + refPath.calcNodes());
                    LOGGER.warn("given:    " + path.calcNodes());
                    fail("wrong weight: " + from + "->" + to + ", dijkstra: " + refWeight + " vs. ch: " + path.getWeight());
                }
                if (Math.abs(path.getDistance() - refPath.getDistance()) > 1.e-1) {
                    strictViolations.add("wrong distance " + from + "->" + to + ", expected: " + refPath.getDistance() + ", given: " + path.getDistance());
                }
                if (Math.abs(path.getTime() - refPath.getTime()) > 50) {
                    strictViolations.add("wrong time " + from + "->" + to + ", expected: " + refPath.getTime() + ", given: " + path.getTime());
                }
            }
            if (numPathsNotFound > 0.9 * numQueries) {
                fail("Too many paths not found: " + numPathsNotFound + "/" + numQueries);
            }
            if (strictViolations.size() > 0.05 * numQueries) {
                fail("Too many strict violations: " + strictViolations.size() + "/" + numQueries + "\n" +
                        Helper.join("\n", strictViolations));
            }
        }
    }

    /**
     * More or less does the same as {@link GHUtility#buildRandomGraph}, but since some special seeds
     * are used in a few tests above this code is kept here. Do not use it for new tests.
     */
    private void buildRandomGraphLegacy(Graph graph, BooleanEncodedValue accessEnc, DecimalEncodedValue speedEnc, Random random, int numNodes, double meanDegree, boolean allowLoops, boolean allowZeroDistance, double pBothDir) {
        for (int i = 0; i < numNodes; ++i) {
            double lat = 49.4 + (random.nextDouble() * 0.0001);
            double lon = 9.7 + (random.nextDouble() * 0.0001);
            graph.getNodeAccess().setNode(i, lat, lon);
        }
        double minDist = Double.MAX_VALUE;
        double maxDist = Double.MIN_VALUE;
        int numEdges = (int) (0.5 * meanDegree * numNodes);
        for (int i = 0; i < numEdges; ++i) {
            int from = random.nextInt(numNodes);
            int to = random.nextInt(numNodes);
            if (!allowLoops && from == to) {
                continue;
            }
            double distance = GHUtility.getDistance(from, to, graph.getNodeAccess());
            if (!allowZeroDistance) {
                distance = Math.max(0.001, distance);
            }
            // add some random offset for most cases, but also allow duplicate edges with same weight
            if (random.nextDouble() < 0.8)
                distance += random.nextDouble() * distance * 0.01;
            minDist = Math.min(minDist, distance);
            maxDist = Math.max(maxDist, distance);
            // using bidirectional edges will increase mean degree of graph above given value
            boolean bothDirections = random.nextDouble() < pBothDir;
            EdgeIteratorState edge = GHUtility.setSpeed(60, true, bothDirections, accessEnc, speedEnc, graph.edge(from, to).setDistance(distance));
            double fwdSpeed = 10 + random.nextDouble() * 120;
            double bwdSpeed = 10 + random.nextDouble() * 120;
            edge.set(speedEnc, fwdSpeed);
            if (speedEnc.isStoreTwoDirections())
                edge.setReverse(speedEnc, bwdSpeed);
        }
    }
}

