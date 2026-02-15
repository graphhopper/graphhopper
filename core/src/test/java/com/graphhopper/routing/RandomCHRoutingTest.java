package com.graphhopper.routing;

import com.graphhopper.routing.ch.CHRoutingAlgorithmFactory;
import com.graphhopper.routing.ch.PrepareContractionHierarchies;
import com.graphhopper.routing.ev.DecimalEncodedValue;
import com.graphhopper.routing.ev.DecimalEncodedValueImpl;
import com.graphhopper.routing.ev.TurnCost;
import com.graphhopper.routing.querygraph.QueryGraph;
import com.graphhopper.routing.querygraph.QueryRoutingCHGraph;
import com.graphhopper.routing.util.EdgeFilter;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.routing.util.TraversalMode;
import com.graphhopper.routing.weighting.SpeedWeighting;
import com.graphhopper.routing.weighting.Weighting;
import com.graphhopper.storage.BaseGraph;
import com.graphhopper.storage.CHConfig;
import com.graphhopper.storage.RoutingCHGraph;
import com.graphhopper.storage.RoutingCHGraphImpl;
import com.graphhopper.storage.index.LocationIndexTree;
import com.graphhopper.storage.index.Snap;
import com.graphhopper.util.GHUtility;
import com.graphhopper.util.PMap;
import com.graphhopper.util.RandomGraph;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.ArgumentsProvider;
import org.junit.jupiter.params.provider.ArgumentsSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Random;
import java.util.stream.Stream;

import static com.graphhopper.util.GHUtility.comparePaths;
import static com.graphhopper.util.GHUtility.createRandomSnaps;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

public class RandomCHRoutingTest {
    private static final Logger LOGGER = LoggerFactory.getLogger(RandomCHRoutingTest.class);

    private static final class Fixture {
        private final TraversalMode traversalMode;
        private final int maxTurnCosts;
        private final double uTurnCosts;
        private final DecimalEncodedValue speedEnc;
        private final DecimalEncodedValue turnCostEnc;
        private Weighting weighting;
        private final BaseGraph graph;
        private CHConfig chConfig;

        Fixture(TraversalMode traversalMode, double uTurnCosts) {
            this.traversalMode = traversalMode;
            this.maxTurnCosts = 10;
            this.uTurnCosts = uTurnCosts;
            speedEnc = new DecimalEncodedValueImpl("speed", 5, 5, true);
            turnCostEnc = TurnCost.create("car", maxTurnCosts);
            EncodingManager encodingManager = EncodingManager.start().add(speedEnc).addTurnCostEncodedValue(turnCostEnc).build();
            graph = new BaseGraph.Builder(encodingManager).withTurnCosts(true).create();
        }

        void freeze() {
            graph.freeze();
            chConfig = traversalMode.isEdgeBased()
                    ? CHConfig.edgeBased("p", new SpeedWeighting(speedEnc, turnCostEnc, graph.getTurnCostStorage(), uTurnCosts))
                    : CHConfig.nodeBased("p", new SpeedWeighting(speedEnc));
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
                            new Fixture(TraversalMode.NODE_BASED, Double.POSITIVE_INFINITY),
                            new Fixture(TraversalMode.EDGE_BASED, 40),
                            new Fixture(TraversalMode.EDGE_BASED, Double.POSITIVE_INFINITY)
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
        run_random(f, false);
    }

    @ParameterizedTest
    @ArgumentsSource(FixtureProvider.class)
    public void random_strict(Fixture f) {
        run_random(f, true);
    }

    private void run_random(Fixture f, boolean tree) {
        // you might have to keep this test running in an infinite loop for several minutes to find potential routing
        // bugs (e.g. use intellij 'run until stop/failure').
        int numNodes = 50;
        long seed = System.nanoTime();
        LOGGER.info("seed: " + seed);
        Random rnd = new Random(seed);
        // curviness must be zero, because otherwise traveling via intermediate virtual nodes won't
        // give the same results as using the original edge
        double curviness = 0;
        RandomGraph.start().seed(seed).nodes(numNodes).curviness(curviness).speedZero(tree ? 0 : 0.1).tree(tree).fill(f.graph, f.speedEnc);
        if (f.traversalMode.isEdgeBased())
            GHUtility.addRandomTurnCosts(f.graph, seed, null, f.turnCostEnc, f.maxTurnCosts, f.graph.getTurnCostStorage());
        runRandomTest(f, rnd, seed, tree);
    }

    private void runRandomTest(Fixture f, Random rnd, long seed, boolean strict) {
        LocationIndexTree locationIndex = new LocationIndexTree(f.graph, f.graph.getDirectory());
        locationIndex.prepareIndex();

        f.freeze();
        PrepareContractionHierarchies pch = PrepareContractionHierarchies.fromGraph(f.graph, f.chConfig);
        PrepareContractionHierarchies.Result res = pch.doWork();
        RoutingCHGraph chGraph = RoutingCHGraphImpl.fromGraph(f.graph, res.getCHStorage(), res.getCHConfig());

        int numQueryGraph = 25;
        int numVirtualNodes = 20;
        for (int j = 0; j < numQueryGraph; j++) {
            // add virtual nodes and edges, because they can change the routing behavior and/or produce bugs, e.g.
            // when via-points are used
            List<Snap> snaps = createRandomSnaps(f.graph.getBounds(), locationIndex, rnd, numVirtualNodes, false, EdgeFilter.ALL_EDGES);
            QueryGraph queryGraph = QueryGraph.create(f.graph, snaps);

            int numQueries = 100;
            int numPathsNotFound = 0;
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

                List<String> strictViolations = comparePaths(refPath, path, from, to, false, seed);
                if (strict && !strictViolations.isEmpty())
                    fail(strictViolations.toString());
            }
            if (numPathsNotFound > 0.9 * numQueries) {
                fail("Too many paths not found: " + numPathsNotFound + "/" + numQueries);
            }
        }
    }

}

