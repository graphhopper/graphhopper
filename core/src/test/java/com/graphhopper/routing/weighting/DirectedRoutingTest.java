package com.graphhopper.routing.weighting;

import com.graphhopper.Repeat;
import com.graphhopper.RepeatRule;
import com.graphhopper.routing.*;
import com.graphhopper.routing.ch.PrepareContractionHierarchies;
import com.graphhopper.routing.lm.PrepareLandmarks;
import com.graphhopper.routing.querygraph.QueryGraph;
import com.graphhopper.routing.util.*;
import com.graphhopper.storage.*;
import com.graphhopper.storage.index.LocationIndex;
import com.graphhopper.storage.index.LocationIndexTree;
import com.graphhopper.storage.index.QueryResult;
import com.graphhopper.util.CHEdgeIteratorState;
import com.graphhopper.util.EdgeExplorer;
import com.graphhopper.util.EdgeIterator;
import com.graphhopper.util.GHUtility;
import com.graphhopper.util.shapes.BBox;
import com.graphhopper.util.shapes.GHPoint;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.*;

import static com.graphhopper.routing.weighting.TurnWeighting.INFINITE_U_TURN_COSTS;
import static com.graphhopper.util.EdgeIterator.ANY_EDGE;
import static com.graphhopper.util.EdgeIterator.NO_EDGE;
import static com.graphhopper.util.Parameters.Algorithms.ASTAR_BI;
import static com.graphhopper.util.Parameters.Algorithms.DIJKSTRA_BI;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

/**
 * This test makes sure the different bidirectional routing algorithms correctly implement restrictions of the source/
 * target edges, by comparing with {@link DijkstraBidirectionRef}
 *
 * @author easbar
 * @see BidirectionalRoutingTest
 * @see DirectedBidirectionalDijkstraTest
 */
@RunWith(Parameterized.class)
public class DirectedRoutingTest {
    private final Algo algo;
    private final int uTurnCosts;
    private final boolean prepareCH;
    private final boolean prepareLM;
    private Directory dir;
    private GraphHopperStorage graph;
    private CHProfile chProfile;
    private CHGraph chGraph;
    private CarFlagEncoder encoder;
    private TurnCostStorage turnCostStorage;
    private int maxTurnCosts;
    private Weighting weighting;
    private EncodingManager encodingManager;
    private PrepareContractionHierarchies pch;
    private PrepareLandmarks lm;

    @Rule
    public RepeatRule repeatRule = new RepeatRule();

    @Parameterized.Parameters(name = "{0}, u-turn-costs: {1}, prepareCH: {2}, prepareLM: {3}")
    public static Collection<Object[]> params() {
        return Arrays.asList(new Object[][]{
                {Algo.ASTAR, INFINITE_U_TURN_COSTS, false, false},
                {Algo.CH_ASTAR, INFINITE_U_TURN_COSTS, true, false},
                {Algo.CH_DIJKSTRA, INFINITE_U_TURN_COSTS, true, false},
                // todo: yields warnings and fails, see #1665, #1687, #1745
//                {Algo.LM, INFINITE_UTURN_COSTS, false, true}
                {Algo.ASTAR, 40, false, false},
                {Algo.CH_ASTAR, 40, true, false},
                {Algo.CH_DIJKSTRA, 40, true, false},
                // todo: yields warnings and fails, see #1665, 1687, #1745
//                {Algo.LM, 40, false, true}
                // todo: add AlternativeRoute ?
        });
    }

    private enum Algo {
        ASTAR,
        CH_ASTAR,
        CH_DIJKSTRA,
        LM
    }

    public DirectedRoutingTest(Algo algo, int uTurnCosts, boolean prepareCH, boolean prepareLM) {
        this.algo = algo;
        this.uTurnCosts = uTurnCosts;
        this.prepareCH = prepareCH;
        this.prepareLM = prepareLM;
    }

    @Before
    public void init() {
        dir = new RAMDirectory();
        maxTurnCosts = 10;
        // todonow: make this work with speed_both_directions=true!
        encoder = new CarFlagEncoder(5, 5, maxTurnCosts);
        encodingManager = EncodingManager.create(encoder);
        weighting = new FastestWeighting(encoder);
        chProfile = CHProfile.edgeBased(weighting, uTurnCosts);
        graph = createGraph();
        turnCostStorage = graph.getTurnCostStorage();
    }

    private void preProcessGraph() {
        graph.freeze();
        if (!prepareCH && !prepareLM) {
            return;
        }
        if (prepareCH) {
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
                return new AStarBidirection(graph, createTurnWeighting(graph), TraversalMode.EDGE_BASED);
            case CH_DIJKSTRA:
                return (BidirRoutingAlgorithm) pch.getRoutingAlgorithmFactory().createAlgo(graph, AlgorithmOptions.start().weighting(weighting).algorithm(DIJKSTRA_BI).build());
            case CH_ASTAR:
                return (BidirRoutingAlgorithm) pch.getRoutingAlgorithmFactory().createAlgo(graph, AlgorithmOptions.start().weighting(weighting).algorithm(ASTAR_BI).build());
            case LM:
                AStarBidirection astarbi = new AStarBidirection(graph, createTurnWeighting(graph), TraversalMode.EDGE_BASED);
                return (BidirRoutingAlgorithm) lm.getDecoratedAlgorithm(graph, astarbi, AlgorithmOptions.start().build());
            default:
                throw new IllegalArgumentException("unknown algo " + algo);
        }
    }

    private TurnWeighting createTurnWeighting(Graph g) {
        return new TurnWeighting(weighting, g.getTurnCostStorage(), uTurnCosts);
    }

    @Test
    @Repeat(times = 10)
    public void randomGraph() {
        final long seed = System.nanoTime();
        System.out.println("random Graph seed: " + seed);
        final int numQueries = 50;
        Random rnd = new Random(seed);
        GHUtility.buildRandomGraph(graph, rnd, 100, 2.2, true, true, encoder.getAverageSpeedEnc(), 0.7, 0.8, 0.8);
        GHUtility.addRandomTurnCosts(graph, seed, encodingManager, encoder, maxTurnCosts, turnCostStorage);
//        GHUtility.printGraphForUnitTest(graph, encoder);
        preProcessGraph();
        List<String> strictViolations = new ArrayList<>();
        for (int i = 0; i < numQueries; i++) {
            int source = getRandom(rnd);
            int target = getRandom(rnd);
            int sourceOutEdge = getSourceOutEdge(rnd, source, graph);
            int targetInEdge = getTargetInEdge(rnd, target, graph);
//            System.out.println("source: " + source + ", target: " + target + ", sourceOutEdge: " + sourceOutEdge + ", targetInEdge: " + targetInEdge);
            Path refPath = new DijkstraBidirectionRef(graph, createTurnWeighting(graph), TraversalMode.EDGE_BASED)
                    .calcPath(source, target, sourceOutEdge, targetInEdge);
            Path path = createAlgo()
                    .calcPath(source, target, sourceOutEdge, targetInEdge);
            // do not check nodes, because there can be ambiguity when there are zero weight loops
            strictViolations.addAll(comparePaths(refPath, path, source, target, false));
        }
        // sometimes there are multiple best paths with different distance/time, if this happens too often something
        // is wrong and we fail
        if (strictViolations.size() > Math.max(1, 0.05 * numQueries)) {
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
        GHUtility.addRandomTurnCosts(graph, seed, encodingManager, encoder, maxTurnCosts, turnCostStorage);
        // GHUtility.printGraphForUnitTest(graph, encoder);
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
            Random tmpRnd1 = new Random(seed);
            int sourceOutEdge = getSourceOutEdge(tmpRnd1, source, queryGraph);
            int targetInEdge = getTargetInEdge(tmpRnd1, target, queryGraph);
            Random tmpRnd2 = new Random(seed);
            int chSourceOutEdge = getSourceOutEdge(tmpRnd2, source, chQueryGraph);
            int chTargetInEdge = getTargetInEdge(tmpRnd2, target, chQueryGraph);

            final TurnWeighting tw = createTurnWeighting(queryGraph);
            Path refPath = new DijkstraBidirectionRef(queryGraph, tw, TraversalMode.EDGE_BASED)
                    .calcPath(source, target, sourceOutEdge, targetInEdge);
            Path path = createAlgo(chQueryGraph)
                    .calcPath(source, target, chSourceOutEdge, chTargetInEdge);

            // do not check nodes, because there can be ambiguity when there are zero weight loops
            strictViolations.addAll(comparePaths(refPath, path, source, target, false));
        }
        // sometimes there are multiple best paths with different distance/time, if this happens too often something
        // is wrong and we fail
        if (strictViolations.size() > Math.max(1, 0.05 * numQueries)) {
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

    private List<String> comparePaths(Path refPath, Path path, int source, int target, boolean checkNodes) {
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
        if (checkNodes && !refPath.calcNodes().equals(path.calcNodes())) {
            strictViolations.add("wrong nodes " + source + "->" + target + "\nexpected: " + refPath.calcNodes() + "\ngiven:    " + path.calcNodes());
        }
        return strictViolations;
    }

    private GraphHopperStorage createGraph() {
        return new GraphBuilder(encodingManager).setDir(dir).setCHProfiles(chProfile).withTurnCosts(true).create();
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
            if (iter instanceof CHEdgeIteratorState && ((CHEdgeIteratorState) iter).isShortcut()) {
                // skip shortcuts here so we get the same restricted edges for a normal query graph and a
                // query graph that wraps a CH graph (provided that the rnd number generator is in the same
                // state)!
                continue;
            }
            edgeIds.add(iter.getOrigEdgeFirst());
            edgeIds.add(iter.getOrigEdgeLast());
        }
        return edgeIds.isEmpty() ? ANY_EDGE : edgeIds.get(rnd.nextInt(edgeIds.size()));
    }

    private int getRandom(Random rnd) {
        return rnd.nextInt(graph.getNodes());
    }

}
