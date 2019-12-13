package com.graphhopper.routing.weighting;

import com.graphhopper.Repeat;
import com.graphhopper.RepeatRule;
import com.graphhopper.routing.*;
import com.graphhopper.routing.ch.PrepareContractionHierarchies;
import com.graphhopper.routing.lm.PrepareLandmarks;
import com.graphhopper.routing.profiles.DecimalEncodedValue;
import com.graphhopper.routing.querygraph.QueryGraph;
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

import static com.graphhopper.routing.util.TraversalMode.EDGE_BASED;
import static com.graphhopper.routing.util.TraversalMode.NODE_BASED;
import static com.graphhopper.util.Parameters.Algorithms.ASTAR_BI;
import static com.graphhopper.util.Parameters.Algorithms.DIJKSTRA_BI;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

/**
 * This test compares the different bidirectional routing algorithms with {@link DijkstraBidirectionRef}
 *
 * @author easbar
 * @see RandomCHRoutingTest - similar but only tests CH algorithms
 * @see DirectedRoutingTest - similar but focusses on edge-based algorithms an directed queries
 */
@RunWith(Parameterized.class)
public class BidirectionalRoutingTest {
    private final Algo algo;
    private final boolean prepareCH;
    private final boolean prepareLM;
    private final TraversalMode traversalMode;
    private Directory dir;
    private GraphHopperStorage graph;
    private List<CHProfile> chProfiles;
    private CHGraph chGraph;
    private CarFlagEncoder encoder;
    private Weighting weighting;
    private EncodingManager encodingManager;
    private PrepareContractionHierarchies pch;
    private PrepareLandmarks lm;

    @Rule
    public RepeatRule repeatRule = new RepeatRule();

    @Parameterized.Parameters(name = "{0}, {3}")
    public static Collection<Object[]> params() {
        return Arrays.asList(new Object[][]{
                {Algo.ASTAR, false, false, NODE_BASED},
                {Algo.CH_ASTAR, true, false, NODE_BASED},
                {Algo.CH_DIJKSTRA, true, false, NODE_BASED},
                {Algo.LM, false, true, NODE_BASED},
                {Algo.ASTAR, false, false, EDGE_BASED},
                {Algo.CH_ASTAR, true, false, EDGE_BASED},
                {Algo.CH_DIJKSTRA, true, false, EDGE_BASED},
                {Algo.LM, false, true, EDGE_BASED}
        });
    }

    private enum Algo {
        ASTAR,
        CH_ASTAR,
        CH_DIJKSTRA,
        LM
    }

    public BidirectionalRoutingTest(Algo algo, boolean prepareCH, boolean prepareLM, TraversalMode traversalMode) {
        this.algo = algo;
        this.prepareCH = prepareCH;
        this.prepareLM = prepareLM;
        this.traversalMode = traversalMode;
    }

    @Before
    public void init() {
        dir = new RAMDirectory();
        // todonow: make this work with speed_both_directions=true!
        encoder = new CarFlagEncoder(5, 5, 1);
        encodingManager = EncodingManager.create(encoder);
        weighting = new FastestWeighting(encoder);
        chProfiles = Arrays.asList(CHProfile.nodeBased(weighting), CHProfile.edgeBased(weighting, TurnWeighting.INFINITE_U_TURN_COSTS));
        graph = createGraph();
    }

    private void preProcessGraph() {
        graph.freeze();
        if (prepareCH) {
            CHProfile chProfile = !traversalMode.isEdgeBased() ? chProfiles.get(0) : chProfiles.get(1);
            pch = PrepareContractionHierarchies.fromGraphHopperStorage(graph, chProfile);
            pch.doWork();
            chGraph = graph.getCHGraph(chProfile);
        }
        if (prepareLM) {
            lm = new PrepareLandmarks(dir, graph, weighting, 16, 8);
            lm.setMaximumWeight(1000);
            lm.doWork();
        }
    }

    private BidirRoutingAlgorithm createAlgo() {
        return createAlgo(prepareCH ? chGraph : graph);
    }

    private BidirRoutingAlgorithm createAlgo(Graph graph) {
        switch (algo) {
            case ASTAR:
                return new AStarBidirection(graph, weighting, traversalMode);
            case CH_DIJKSTRA:
                return (BidirRoutingAlgorithm) pch.getRoutingAlgorithmFactory().createAlgo(graph, AlgorithmOptions.start().weighting(weighting).algorithm(DIJKSTRA_BI).build());
            case CH_ASTAR:
                return (BidirRoutingAlgorithm) pch.getRoutingAlgorithmFactory().createAlgo(graph, AlgorithmOptions.start().weighting(weighting).algorithm(ASTAR_BI).build());
            case LM:
                AStarBidirection astarbi = new AStarBidirection(graph, weighting, traversalMode);
                return (BidirRoutingAlgorithm) lm.getDecoratedAlgorithm(graph, astarbi, AlgorithmOptions.start().build());
            default:
                throw new IllegalArgumentException("unknown algo " + algo);
        }
    }

    @Test
    public void lm_problem_to_node_of_fallback_approximator() {
        // Before #1745 this test used to fail for LM, because when the distance was approximated for the start node 0
        // the LMApproximator used the fall back approximator for which the to node was never set. This in turn meant
        // that the to coordinates were zero and a way too large approximation was returned.
        // Eventually the best path was not updated correctly because the spt entry of the fwd search already had a way
        // too large weight.

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

        Path refPath = new DijkstraBidirectionRef(graph, weighting, NODE_BASED)
                .calcPath(source, target);
        Path path = createAlgo()
                .calcPath(0, 3);
        comparePaths(refPath, path, source, target);
    }

    @Test
    public void lm_issue2() {
        // Before #1745 This would fail for LM, because an underrun of 'delta' would not be treated correctly,
        // and the remaining weight would be over-approximated

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
        Path refPath = new DijkstraBidirectionRef(graph, weighting, NODE_BASED)
                .calcPath(source, target);
        Path path = createAlgo()
                .calcPath(source, target);
        comparePaths(refPath, path, source, target);
    }

    @Test
    @Repeat(times = 5)
    public void randomGraph() {
        // todo: there are some problems with random graph testing for LM, see #1687
        Assume.assumeFalse(algo.equals(Algo.LM));

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
            Path refPath = new DijkstraBidirectionRef(graph, weighting, NODE_BASED)
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
    @Repeat(times = 5)
    public void randomGraph_withQueryGraph() {
        // todo: there are some problems with random graph testing for LM, see #1687
        Assume.assumeFalse(algo.equals(Algo.LM));

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

            QueryGraph chQueryGraph = QueryGraph.lookup(prepareCH ? chGraph : graph, chQueryResults);
            QueryGraph queryGraph = QueryGraph.lookup(graph, queryResults);

            int source = queryResults.get(0).getClosestNode();
            int target = queryResults.get(1).getClosestNode();

            Path refPath = new DijkstraBidirectionRef(queryGraph, weighting, traversalMode)
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
        return new GraphBuilder(encodingManager).setCHProfiles(chProfiles).setDir(dir).withTurnCosts(true).create();
    }

    private int getRandom(Random rnd) {
        return rnd.nextInt(graph.getNodes());
    }

}
