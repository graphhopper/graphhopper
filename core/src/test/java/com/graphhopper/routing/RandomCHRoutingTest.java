package com.graphhopper.routing;

import com.graphhopper.routing.ch.CHRoutingAlgorithmFactory;
import com.graphhopper.routing.ch.NodeOrderingProvider;
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
import org.junit.jupiter.api.Test;
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
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
        run_random(f, false, false);
    }

    @ParameterizedTest
    @ArgumentsSource(FixtureProvider.class)
    public void random_strict(Fixture f) {
        // with finite u-turn costs paths on a tree are not unique: we can do a necessary u-turn in
        // different tree branches. u-turns can be necessary even without restricted start/target edges due to
        // one-ways or turn restrictions, see edgeBased_turnRestriction_causes_uturn_ambiguity
        boolean chain = Double.isFinite(f.uTurnCosts);
        boolean tree = !chain;
        run_random(f, chain, tree);
    }

    private void run_random(Fixture f, boolean chain, boolean tree) {
        // you might have to keep this test running in an infinite loop for several minutes to find potential routing
        // bugs (e.g. use intellij 'run until stop/failure').
        int numNodes = 50;
        long seed = System.nanoTime();
        LOGGER.info("seed: " + seed);
        Random rnd = new Random(seed);
        // curviness must be zero, because otherwise traveling via intermediate virtual nodes won't
        // give the same results as using the original edge
        double curviness = 0;
        RandomGraph.start().seed(seed).nodes(numNodes).curviness(curviness).speedZero((chain || tree) ? 0 : 0.1).chain(chain).tree(tree).fill(f.graph, f.speedEnc);
        if (f.traversalMode.isEdgeBased())
            GHUtility.addRandomTurnCosts(f.graph, seed, null, f.turnCostEnc, f.maxTurnCosts, f.graph.getTurnCostStorage());
        runRandomTest(f, rnd, seed, chain || tree);
    }

    /**
     * On a tree with no one-ways, a turn restriction can force a U-turn detour. If two branches
     * yield equal-weight detours but cover different physical distances, CH and Dijkstra may pick
     * different branches — same weight, different distance.
     */
    @Test
    public void edgeBased_turnRestriction_causes_uturn_ambiguity() {
        Fixture f = new Fixture(TraversalMode.EDGE_BASED, 40);
        //       2 (north, ~111m from 1)
        //       |  speed=10 → weight≈111
        // 0 --- 1 --- 3
        //       |  speed=5  → weight≈111  (half dist, half speed → same weight)
        //       4 (south, ~56m from 1)
        f.graph.edge(0, 1).set(f.speedEnc, 10, 10);  // edge 0
        f.graph.edge(1, 2).set(f.speedEnc, 10, 10);  // edge 1
        f.graph.edge(1, 3).set(f.speedEnc, 10, 10);  // edge 2
        f.graph.edge(1, 4).set(f.speedEnc, 5, 5);    // edge 3
        GHUtility.updateDistancesFor(f.graph, 1, 50.0, 10.0);
        GHUtility.updateDistancesFor(f.graph, 0, 50.0, 9.999);
        GHUtility.updateDistancesFor(f.graph, 2, 50.001, 10.0);
        GHUtility.updateDistancesFor(f.graph, 3, 50.0, 10.001);
        GHUtility.updateDistancesFor(f.graph, 4, 49.9995, 10.0);

        // Block the direct turn 0→1→3 (edge 0 → node 1 → edge 2). This forces a u-turn to get from 0 to 3.
        f.graph.getTurnCostStorage().set(f.turnCostEnc, 0, 1, 2, Double.POSITIVE_INFINITY);
        f.freeze();

        // Dijkstra on base graph
        Path dijkstra = new Dijkstra(f.graph, f.weighting, f.traversalMode).calcPath(0, 3);
        assertTrue(dijkstra.isFound());

        // CH with a node ordering that makes CH pick the other detour branch
        PrepareContractionHierarchies pch = PrepareContractionHierarchies.fromGraph(f.graph, f.chConfig);
        pch.useFixedNodeOrdering(NodeOrderingProvider.fromArray(2, 4, 0, 3, 1));
        PrepareContractionHierarchies.Result res = pch.doWork();
        RoutingCHGraph chGraph = RoutingCHGraphImpl.fromGraph(f.graph, res.getCHStorage(), res.getCHConfig());
        Path ch = new CHRoutingAlgorithmFactory(chGraph).createAlgo(new PMap()).calcPath(0, 3);
        assertTrue(ch.isFound());

        // same weight, but different distance. this is why for edge-based routing with finite u-turn costs paths are not unique in a tree
        assertEquals(dijkstra.getWeight(), ch.getWeight());
        assertEquals(dijkstra.getDistance_mm(), 365340);
        assertEquals(ch.getDistance_mm(), 254144);
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

