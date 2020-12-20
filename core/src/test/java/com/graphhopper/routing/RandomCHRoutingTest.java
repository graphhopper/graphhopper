package com.graphhopper.routing;

import com.graphhopper.routing.ch.CHRoutingAlgorithmFactory;
import com.graphhopper.routing.ch.PrepareContractionHierarchies;
import com.graphhopper.routing.ev.DecimalEncodedValue;
import com.graphhopper.routing.querygraph.QueryGraph;
import com.graphhopper.routing.querygraph.QueryRoutingCHGraph;
import com.graphhopper.routing.util.CarFlagEncoder;
import com.graphhopper.routing.util.EdgeFilter;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.routing.util.TraversalMode;
import com.graphhopper.routing.weighting.Weighting;
import com.graphhopper.storage.*;
import com.graphhopper.storage.index.LocationIndexTree;
import com.graphhopper.storage.index.Snap;
import com.graphhopper.util.EdgeIteratorState;
import com.graphhopper.util.GHUtility;
import com.graphhopper.util.Helper;
import com.graphhopper.util.PMap;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

import static com.graphhopper.routing.weighting.Weighting.INFINITE_U_TURN_COSTS;
import static com.graphhopper.util.GHUtility.createRandomSnaps;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

@RunWith(Parameterized.class)
public class RandomCHRoutingTest {
    private static final Logger LOGGER = LoggerFactory.getLogger(RandomCHRoutingTest.class);
    private final TraversalMode traversalMode;
    private final int maxTurnCosts;
    private final int uTurnCosts;
    private Directory dir;
    private CarFlagEncoder encoder;
    private EncodingManager encodingManager;
    private Weighting weighting;
    private GraphHopperStorage graph;
    private CHConfig chConfig;
    private LocationIndexTree locationIndex;

    @Parameterized.Parameters(name = "{0}, u-turn-costs={1}")
    public static Collection<Object[]> params() {
        return Arrays.asList(new Object[][]{
                {TraversalMode.NODE_BASED, INFINITE_U_TURN_COSTS},
                {TraversalMode.EDGE_BASED, 40},
                {TraversalMode.EDGE_BASED, INFINITE_U_TURN_COSTS}
        });
    }

    public RandomCHRoutingTest(TraversalMode traversalMode, int uTurnCosts) {
        this.traversalMode = traversalMode;
        this.maxTurnCosts = 10;
        this.uTurnCosts = uTurnCosts;
    }

    @Before
    public void init() {
        dir = new RAMDirectory();
        encoder = new CarFlagEncoder(5, 5, maxTurnCosts);
        encodingManager = EncodingManager.create(encoder);
        graph = new GraphBuilder(encodingManager)
                .setCHConfigStrings("p|car|fastest|" + (traversalMode.isEdgeBased() ? "edge" : "node") + "|" + uTurnCosts)
                .create();
        chConfig = graph.getCHGraph().getCHConfig();
        weighting = chConfig.getWeighting();
    }

    /**
     * Runs random routing queries on a random query/CH graph with random speeds and adding random virtual edges and
     * nodes.
     */
    @Test
    public void random() {
        // you might have to keep this test running in an infinite loop for several minutes to find potential routing
        // bugs (e.g. use intellij 'run until stop/failure').
        int numNodes = 50;
        long seed = System.nanoTime();
        LOGGER.info("seed: " + seed);
        Random rnd = new Random(seed);
        // we may not use an offset when query graph is involved, otherwise traveling via virtual edges will not be
        // the same as taking the direct edge!
        double pOffset = 0;
        GHUtility.buildRandomGraph(graph, rnd, numNodes, 2.5, true, true,
                encoder.getAccessEnc(), encoder.getAverageSpeedEnc(), null, 0.7, 0.9, pOffset);
        if (traversalMode.isEdgeBased()) {
            GHUtility.addRandomTurnCosts(graph, seed, encodingManager, encoder, maxTurnCosts, graph.getTurnCostStorage());
        }
        runRandomTest(rnd, 20);
    }

    @Test
    public void issue1574_1() {
        Assume.assumeFalse(traversalMode.isEdgeBased());
        Random rnd = new Random(9348906923700L);
        buildRandomGraphLegacy(rnd, 50, 2.5, false, true, 0.9);
        runRandomTest(rnd, 20);
    }

    @Test
    public void issue1574_2() {
        Assume.assumeFalse(traversalMode.isEdgeBased());
        Random rnd = new Random(10093639220394L);
        buildRandomGraphLegacy(rnd, 50, 2.5, false, true, 0.9);
        runRandomTest(rnd, 20);
    }

    @Test
    public void issue1582() {
        Assume.assumeFalse(traversalMode.isEdgeBased());
        Random rnd = new Random(4111485945982L);
        buildRandomGraphLegacy(rnd, 10, 2.5, false, true, 0.9);
        runRandomTest(rnd, 100);
    }

    @Test
    public void issue1583() {
        Assume.assumeFalse(traversalMode.isEdgeBased());
        Random rnd = new Random(10785899964423L);
        buildRandomGraphLegacy(rnd, 50, 2.5, true, true, 0.9);
        runRandomTest(rnd, 20);
    }

    @Test
    public void issue1593() {
        Assume.assumeTrue(traversalMode.isEdgeBased());
        long seed = 60643479675316L;
        Random rnd = new Random(seed);
        GHUtility.buildRandomGraph(graph, rnd, 50, 2.5, true, true,
                encoder.getAccessEnc(), encoder.getAverageSpeedEnc(), null, 0.7, 0.9, 0.0);
        GHUtility.addRandomTurnCosts(graph, seed, encodingManager, encoder, maxTurnCosts, graph.getTurnCostStorage());
        runRandomTest(rnd, 20);
    }

    private void runRandomTest(Random rnd, int numVirtualNodes) {
        locationIndex = new LocationIndexTree(graph, dir);
        locationIndex.prepareIndex();

        graph.freeze();
        RoutingCHGraph chGraph = graph.getRoutingCHGraph(chConfig.getName());
        PrepareContractionHierarchies pch = PrepareContractionHierarchies.fromGraphHopperStorage(graph, chConfig);
        pch.doWork();

        int numQueryGraph = 25;
        for (int j = 0; j < numQueryGraph; j++) {
            // add virtual nodes and edges, because they can change the routing behavior and/or produce bugs, e.g.
            // when via-points are used
            List<Snap> snaps = createRandomSnaps(graph.getBounds(), locationIndex, rnd, numVirtualNodes, false, EdgeFilter.ALL_EDGES);
            QueryGraph queryGraph = QueryGraph.create(graph, snaps);

            int numQueries = 100;
            int numPathsNotFound = 0;
            List<String> strictViolations = new ArrayList<>();
            for (int i = 0; i < numQueries; i++) {
                int from = rnd.nextInt(queryGraph.getNodes());
                int to = rnd.nextInt(queryGraph.getNodes());
                Weighting w = queryGraph.wrapWeighting(weighting);
                // using plain dijkstra instead of bidirectional, because of #1592
                RoutingAlgorithm refAlgo = new Dijkstra(queryGraph, w, traversalMode);
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
    private void buildRandomGraphLegacy(Random random, int numNodes, double meanDegree, boolean allowLoops, boolean allowZeroDistance, double pBothDir) {
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
            EdgeIteratorState edge = GHUtility.setSpeed(60, true, bothDirections, encoder, graph.edge(from, to).setDistance(distance));
            double fwdSpeed = 10 + random.nextDouble() * 120;
            double bwdSpeed = 10 + random.nextDouble() * 120;
            DecimalEncodedValue speedEnc = encoder.getAverageSpeedEnc();
            edge.set(speedEnc, fwdSpeed);
            if (speedEnc.isStoreTwoDirections())
                edge.setReverse(speedEnc, bwdSpeed);
        }
    }
}

