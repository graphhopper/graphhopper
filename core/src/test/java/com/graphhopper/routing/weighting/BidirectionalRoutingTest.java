package com.graphhopper.routing.weighting;

import com.graphhopper.Repeat;
import com.graphhopper.RepeatRule;
import com.graphhopper.routing.*;
import com.graphhopper.routing.ch.PrepareContractionHierarchies;
import com.graphhopper.routing.lm.PrepareLandmarks;
import com.graphhopper.routing.profiles.DecimalEncodedValue;
import com.graphhopper.routing.util.*;
import com.graphhopper.storage.*;
import com.graphhopper.storage.index.LocationIndex;
import com.graphhopper.storage.index.LocationIndexTree;
import com.graphhopper.storage.index.QueryResult;
import com.graphhopper.util.GHUtility;
import com.graphhopper.util.shapes.BBox;
import com.graphhopper.util.shapes.GHPoint;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.*;

import static com.graphhopper.util.Parameters.Algorithms.ASTAR_BI;
import static com.graphhopper.util.Parameters.Algorithms.DIJKSTRA_BI;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

/**
 * This test compares the different bidirectional routing algorithms with {@link DijkstraBidirectionRef}
 * // todo: no real need of emphasizing bidirectional here ?
 *
 * @author easbar
 */
@RunWith(Parameterized.class)
public class BidirectionalRoutingTest {
    private final Algo algo;
    private final boolean prepareCH;
    private final boolean prepareLM;
    private Directory dir;
    private GraphHopperStorage graph;
    private CHGraph chGraph;
    private CarFlagEncoder encoder;
    private Weighting weighting;
    private EncodingManager encodingManager;
    private PrepareContractionHierarchies pch;
    private PrepareLandmarks lm;

    @Rule
    public RepeatRule repeatRule = new RepeatRule();

    @Parameterized.Parameters(name = "{0}")
    public static Collection<Object[]> params() {
        // todonow: run node & edge-based ?
        return Arrays.asList(new Object[][]{
                {Algo.ASTAR, false, false},
                {Algo.CH_ASTAR, true, false},
                {Algo.CH_DIJKSTRA, true, false},
                {Algo.LM, false, true}
        });
    }

    private enum Algo {
        ASTAR,
        CH_ASTAR,
        CH_DIJKSTRA,
        LM
    }

    public BidirectionalRoutingTest(Algo algo, boolean prepareCH, boolean prepareLM) {
        this.algo = algo;
        this.prepareCH = prepareCH;
        this.prepareLM = prepareLM;
    }

    @Before
    public void init() {
        dir = new RAMDirectory();
        // todonow: make this work with speed_both_directions=true!
        encoder = new CarFlagEncoder(5, 5, 0);
        encodingManager = EncodingManager.create(encoder);
        weighting = new FastestWeighting(encoder);
        graph = createGraph();
        chGraph = graph.getCHGraph();
    }

    private void preProcessGraph() {
        graph.freeze();
        if (!prepareCH && !prepareLM) {
            return;
        }
        if (prepareCH) {
            pch = new PrepareContractionHierarchies(chGraph, weighting, TraversalMode.NODE_BASED);
            pch.doWork();
        }
        if (prepareLM) {
            lm = new PrepareLandmarks(dir, graph, weighting, 16, 8);
            lm.setMaximumWeight(1000);
            lm.doWork();
        }
    }

    private AbstractBidirAlgo createAlgo() {
        return createAlgo(prepareCH ? chGraph : graph);
    }

    private AbstractBidirAlgo createAlgo(Graph graph) {
        switch (algo) {
            case ASTAR:
                return new AStarBidirection(graph, weighting, TraversalMode.NODE_BASED);
            case CH_DIJKSTRA:
                return (AbstractBidirAlgo) pch.createAlgo(graph, AlgorithmOptions.start().weighting(weighting).algorithm(DIJKSTRA_BI).build());
            case CH_ASTAR:
                return (AbstractBidirAlgo) pch.createAlgo(graph, AlgorithmOptions.start().weighting(weighting).algorithm(ASTAR_BI).build());
            case LM:
                AStarBidirection astarbi = new AStarBidirection(graph, weighting, TraversalMode.NODE_BASED);
                return (AbstractBidirAlgo) lm.getDecoratedAlgorithm(graph, astarbi, AlgorithmOptions.start().build());
            default:
                throw new IllegalArgumentException("unknown algo " + algo);
        }
    }

    @Test
    public void lm_problem_to_node_of_fallback_approximator() {
        Assume.assumeTrue(algo.equals(Algo.LM));
        // todonow: this test fails, because when the distance is approximated for the start node 0 the LMApproximator
        // uses the fall back approximator for which the to node is never set. This in turn means that the to coordinates
        // are zero and a way too large approximation is returned. Eventually the best path is not updated correctly
        // because the spt entry of the fwd search already has a way too large weight.

        //   ---<---
        //   |     |
        //   | 4   |
        //   |/  \ 0
        //   1   | |
        //     \ | |
        //       3 |
        // 2 --<----
        DecimalEncodedValue speedEnc = encoder.getAverageSpeedEnc();
        NodeAccess na = graph.getNodeAccess();
        na.setNode(0, 49.405150, 9.709054);
        na.setNode(1, 49.403705, 9.700517);
        na.setNode(2, 49.400112, 9.700209);
        na.setNode(3, 49.403009, 9.708364);
        na.setNode(4, 49.409021, 9.703622);
        // 30s
        graph.edge(4, 3, 1000, true).set(speedEnc, 120);
        graph.edge(0, 2, 1000, false).set(speedEnc, 120);
        // 360s
        graph.edge(1, 3, 1000, true).set(speedEnc, 10);
        // 80s
        graph.edge(0, 1, 1000, false).set(speedEnc, 45);
        graph.edge(1, 4, 1000, true).set(speedEnc, 45);
        preProcessGraph();

        int source = 0;
        int target = 3;

        // only for analysis, build the same approximator and print approximations
//        LMApproximator approx = new LMApproximator(graph, graph.getNodes(), lm.getLandmarkStorage(), 8, lm.getLandmarkStorage().getFactor(), false);
//        ConsistentWeightApproximator approximator = new ConsistentWeightApproximator(approx);
//        approximator.setFrom(source);
//        approximator.setTo(target);
//        for (int i = 0; i < graph.getNodes(); i++) {
//            System.out.println("approx " + i + " fwd -> " + approximator.approximate(i, true));
//            System.out.println("approx " + i + " bwd -> " + approximator.approximate(i, false));
//        }

        Path refPath = new DijkstraBidirectionRef(graph, weighting, TraversalMode.NODE_BASED)
                .calcPath(source, target);
        Path path = createAlgo()
                .calcPath(0, 3);
        comparePaths(refPath, path, source, target);
    }

    @Test
    public void lm_issue2() {
        // todonow: why does this one fail ?

        //                    ---
        //                  /     \
        // 0 - 1 - 5 - 6 - 9 - 4 - 0
        //          \     /
        //            ->-
        NodeAccess na = graph.getNodeAccess();
        DecimalEncodedValue speedEnc = encoder.getAverageSpeedEnc();
        na.setNode(0, 49.406987, 9.709767);
        na.setNode(1, 49.403612, 9.702953);
        na.setNode(2, 49.409755, 9.706517);
        na.setNode(3, 49.409021, 9.708649);
        na.setNode(4, 49.400674, 9.700906);
        na.setNode(5, 49.408735, 9.709486);
        na.setNode(6, 49.406402, 9.700937);
        na.setNode(7, 49.406965, 9.702660);
        na.setNode(8, 49.405227, 9.702863);
        na.setNode(9, 49.409411, 9.709085);
        graph.edge(0, 1, 623.197000, true).set(speedEnc, 112);
        graph.edge(5, 1, 741.414000, true).set(speedEnc, 13);
        graph.edge(9, 4, 1140.835000, true).set(speedEnc, 35);
        graph.edge(5, 6, 670.689000, true).set(speedEnc, 18);
        graph.edge(5, 9, 80.731000, false).set(speedEnc, 88);
        graph.edge(0, 9, 273.948000, true).set(speedEnc, 82);
        graph.edge(4, 0, 956.552000, true).set(speedEnc, 60);
        preProcessGraph();
        int source = 5;
        int target = 4;
        Path refPath = new DijkstraBidirectionRef(graph, weighting, TraversalMode.NODE_BASED)
                .calcPath(source, target);
        Path path = createAlgo()
                .calcPath(source, target);
        comparePaths(refPath, path, source, target);
    }

    @Test
    @Repeat(times = 10)
    public void randomGraph() {
        final long seed = System.nanoTime();
        System.out.println("random Graph seed: " + seed);
        final int numQueries = 50;
        Random rnd = new Random(seed);
        GHUtility.buildRandomGraph(graph, rnd, 100, 2.2, true, true, encoder.getAverageSpeedEnc(), 0.7, 0.8, 0.8);
//        GHUtility.printGraphForUnitTest(graph, encoder);
        preProcessGraph();
        List<String> strictViolations = new ArrayList<>();
        for (int i = 0; i < numQueries; i++) {
            int source = getRandom(rnd);
            int target = getRandom(rnd);
//            System.out.println("source: " + source + ", target: " + target);
            Path refPath = new DijkstraBidirectionRef(graph, weighting, TraversalMode.NODE_BASED)
                    .calcPath(source, target);
            Path path = createAlgo()
                    .calcPath(source, target);
            strictViolations.addAll(comparePaths(refPath, path, source, target));
        }
        if (strictViolations.size() > Math.max(1, 0.20 * numQueries)) {
            for (String strictViolation : strictViolations) {
                System.out.println("strict violation: " + strictViolation);
            }
            fail("Too many strict violations: " + strictViolations.size() + " / " + numQueries);
        }
    }

    /**
     * Similar to {@link #randomGraph()}, but using the {@link QueryGraph} as it is done in real usage.
     */
    @Test
    @Repeat(times = 10)
    public void randomGraph_withQueryGraph() {
        final long seed = System.nanoTime();
        System.out.println("randomGraph_withQueryGraph seed: " + seed);
        final int numQueries = 50;

        // we may not use an offset when query graph is involved, otherwise traveling via virtual edges will not be
        // the same as taking the direct edge!
        double pOffset = 0;
        Random rnd = new Random(seed);
        GHUtility.buildRandomGraph(graph, rnd, 50, 2.2, true, true, encoder.getAverageSpeedEnc(), 0.7, 0.8, pOffset);
//        GHUtility.printGraphForUnitTest(graph, encoder);
        preProcessGraph();
        LocationIndexTree index = new LocationIndexTree(graph, dir);
        index.prepareIndex();
        List<String> strictViolations = new ArrayList<>();
        for (int i = 0; i < numQueries; i++) {
            List<GHPoint> points = getRandomPoints(2, index, rnd);

            List<QueryResult> chQueryResults = findQueryResults(index, points);
            List<QueryResult> queryResults = findQueryResults(index, points);

            QueryGraph chQueryGraph = new QueryGraph(prepareCH ? chGraph : graph);
            QueryGraph queryGraph = new QueryGraph(graph);

            chQueryGraph.lookup(chQueryResults);
            queryGraph.lookup(queryResults);

            int source = queryResults.get(0).getClosestNode();
            int target = queryResults.get(1).getClosestNode();

            Path refPath = new DijkstraBidirectionRef(queryGraph, weighting, TraversalMode.NODE_BASED)
                    .calcPath(source, target);
            Path path = createAlgo(chQueryGraph)
                    .calcPath(source, target);
            strictViolations.addAll(comparePaths(refPath, path, source, target));
        }
        // we do not do a strict check because there can be ambiguity, for example when there are zero weight loops.
        // however, when there are too many deviations we fail
        if (strictViolations.size() > Math.max(1, 0.20 * numQueries)) {
            fail("Too many strict violations: " + strictViolations.size() + " / " + numQueries);
        }
    }

    private List<GHPoint> getRandomPoints(int numPoints, LocationIndex index, Random rnd) {
        List<GHPoint> points = new ArrayList<>(numPoints);
        BBox bounds = graph.getBounds();
        final int maxAttempts = 100 * numPoints;
        int attempts = 0;
        while (attempts < maxAttempts && points.size() < numPoints) {
            double lat = rnd.nextDouble() * (bounds.maxLat - bounds.minLat) + bounds.minLat;
            double lon = rnd.nextDouble() * (bounds.maxLon - bounds.minLon) + bounds.minLon;
            QueryResult queryResult = index.findClosest(lat, lon, EdgeFilter.ALL_EDGES);
            if (queryResult.isValid()) {
                points.add(new GHPoint(lat, lon));
            }
            attempts++;
        }
        assertEquals("could not find valid random points after " + attempts + " attempts", numPoints, points.size());
        return points;
    }

    private List<QueryResult> findQueryResults(LocationIndexTree index, List<GHPoint> ghPoints) {
        List<QueryResult> result = new ArrayList<>(ghPoints.size());
        for (GHPoint ghPoint : ghPoints) {
            result.add(index.findClosest(ghPoint.getLat(), ghPoint.getLon(), DefaultEdgeFilter.ALL_EDGES));
        }
        return result;
    }

    private List<String> comparePaths(Path refPath, Path path, int source, int target) {
        List<String> strictViolations = new ArrayList<>();
        double refWeight = refPath.getWeight();
        double weight = path.getWeight();
        if (Math.abs(refWeight - weight) > 1.e-2) {
            System.out.println("expected: " + refPath.calcNodes());
            System.out.println("given:    " + path.calcNodes());
            fail("wrong weight: " + source + "->" + target + ", expected: " + refWeight + ", given: " + weight);
        }
        if (Math.abs(path.getDistance() - refPath.getDistance()) > 1.e-1) {
            strictViolations.add("wrong distance " + source + "->" + target + ", expected: " + refPath.getDistance() + ", given: " + path.getDistance());
        }
        if (Math.abs(path.getTime() - refPath.getTime()) > 50) {
            strictViolations.add("wrong time " + source + "->" + target + ", expected: " + refPath.getTime() + ", given: " + path.getTime());
        }
        if (!refPath.calcNodes().equals(path.calcNodes())) {
            strictViolations.add("wrong nodes " + source + "->" + target + "\nexpected: " + refPath.calcNodes() + "\ngiven:    " + path.calcNodes());
        }
        return strictViolations;
    }

    private GraphHopperStorage createGraph() {
        GraphHopperStorage gh = new GraphHopperStorage(new ArrayList<Weighting>(), Collections.singletonList(weighting), dir, encodingManager,
                false, new GraphExtension.NoOpExtension());
        gh.create(1000);
        return gh;
    }

    private int getRandom(Random rnd) {
        return rnd.nextInt(graph.getNodes());
    }

}
