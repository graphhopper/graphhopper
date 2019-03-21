package com.graphhopper.routing;

import com.graphhopper.Repeat;
import com.graphhopper.RepeatRule;
import com.graphhopper.routing.ch.PrepareContractionHierarchies;
import com.graphhopper.routing.lm.PrepareLandmarks;
import com.graphhopper.routing.subnetwork.PrepareRoutingSubnetworks;
import com.graphhopper.routing.util.*;
import com.graphhopper.routing.weighting.FastestWeighting;
import com.graphhopper.routing.weighting.Weighting;
import com.graphhopper.storage.*;
import com.graphhopper.storage.index.LocationIndexTree;
import com.graphhopper.storage.index.QueryResult;
import com.graphhopper.util.GHUtility;
import com.graphhopper.util.PMap;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

import static org.junit.Assert.fail;

/**
 * Runs direct comparison between Dijkstra and other algorithms on randomly generated graphs.
 */
@RunWith(Parameterized.class)
public class CompareAlgoVsDijkstraTest {
    private final String algoString;
    // todo: also add edge-based
    private final TraversalMode traversalMode = TraversalMode.NODE_BASED;
    private Directory dir;
    private CarFlagEncoder encoder;
    private Weighting weighting;
    private GraphHopperStorage graph;
    private CHGraph chGraph;
    private NodeAccess na;
    private PrepareLandmarks plm;
    private PrepareContractionHierarchies pch;
    private AlgoFactory algoFactory;
    private long seed;
    private LocationIndexTree locationIndex;
    private Random rnd;

    @Rule
    public RepeatRule repeatRule = new RepeatRule();

    @Parameterized.Parameters(name = "{0}")
    public static Object[] parameters() {
        return new Object[]{
//                "astar",
//                "astarbi",
//                "alt",
                "ch",
                "ch_no_sod"
                // todo: add more algos (ch with/without stall-on-demand, edge based, ch with/without astar, lm/ch combination etc.
        };
    }

    public CompareAlgoVsDijkstraTest(String algoString) {
        this.algoString = algoString;
        algoFactory = getAlgoFactory(algoString);
    }

    @Before
    public void init() {
        dir = new RAMDirectory();
        seed = System.nanoTime();
        rnd = new Random(seed);
        encoder = new CarFlagEncoder();
        EncodingManager em = EncodingManager.create(encoder);
        weighting = new FastestWeighting(encoder);
        graph = new GraphBuilder(em).setCHGraph(weighting).create();
        chGraph = graph.getGraph(CHGraph.class);
        na = graph.getNodeAccess();
        System.out.println("seed: " + seed);
    }

    /**
     * Use this test to search for failing test cases (graph is printed to console if error is found).
     */
    @Test
    @Repeat(times = 1000)
    public void randomGraph() {
        // increasing number of nodes finds failing test case more quickly, but harder to debug
        int numNodes = 100;
        GHUtility.buildRandomGraph(graph, seed, numNodes, 4, true, true, 0.9);
        // we only want to look at fully connected graphs (otherwise we get problems with lm preprocessing)
        // only exit here for very small graphs though, otherwise there will be hardly any graph that works
        if (numNodes < 20 && getNumComponents() > 1) {
            return;
        }

        locationIndex = new LocationIndexTree(graph, dir);
        locationIndex.prepareIndex();

        compareWithDijkstra();
    }

    private int getNumComponents() {
        PrepareRoutingSubnetworks ps = new PrepareRoutingSubnetworks(graph, Arrays.<FlagEncoder>asList(encoder));
        ps.setMinOneWayNetworkSize(1);
        ps.setMinNetworkSize(1);
        ps.doWork();
        return ps.getMaxSubnetworks();
    }

    private void compareWithDijkstra() {
        prepareCH();

        // ensure different seed than used in GHUtility otherwise we always snap to tower nodes?
        Random random = new Random(seed * 2);
        MAIN:
        for (int i = 0; i < 100_000; ++i) {
            if (i % 2000 == 0)
                System.out.println(i);
            List<QueryResult> chLocations = new ArrayList<>(3);
            List<QueryResult> locations = new ArrayList<>(3);
            for (int j = 0; j < 3; j++) {
                double lat = 49.4 + (random.nextDouble() * 0.01);
                double lon = 9.7 + (random.nextDouble() * 0.01);
                QueryResult qr = locationIndex.findClosest(lat, lon, EdgeFilter.ALL_EDGES);
                if (!qr.isValid())
                    continue MAIN;
                chLocations.add(qr);
                locations.add(locationIndex.findClosest(lat, lon, EdgeFilter.ALL_EDGES));
            }

            QueryGraph chQueryGraph = new QueryGraph(graph.getGraph(CHGraph.class, weighting));
            chQueryGraph.lookup(chLocations);

            QueryGraph queryGraph = new QueryGraph(graph);
            queryGraph.lookup(locations);

            for (int j = 1; j < chLocations.size(); j++) {
                int from = locations.get(j - 1).getClosestNode();
                int to = locations.get(j).getClosestNode();
                DijkstraBidirectionRef refAlgo = new DijkstraBidirectionRef(queryGraph, weighting, TraversalMode.NODE_BASED);
                Path refPath = refAlgo.calcPath(from, to);
                if (!refPath.isFound())
                    continue;

                int chFrom = chLocations.get(j - 1).getClosestNode();
                int chTo = chLocations.get(j).getClosestNode();
                RoutingAlgorithm algo = algoFactory.createAlgo(chQueryGraph);
                Path path = algo.calcPath(chFrom, chTo);
                if (!path.isFound()) {
                    GHUtility.printGraphForUnitTest(graph, encoder);
                    fail("error for " + from + "->" + to + ", " + algoString + ": " + path.getWeight());
                }

                double weight = path.getWeight();
                double refWeight = refPath.getWeight();
                // todo: using very rough threshold, but sometimes there are large deviations
                if (Math.abs(refWeight - weight) > 10) {
                    GHUtility.printGraphForUnitTest(graph, encoder);
                    System.out.println("dijkstra: " + refPath.calcNodes());
                    System.out.println(algoString + ": " + path.calcNodes());
                    fail(from + "->" + to + ", dijkstra: " + refWeight + " vs. " + algoString + ": " + path.getWeight());
                }
            }
        }
    }

    private void prepareLM() {
        // todo: use different settings for LM ?
        int numLandMarks = 4;
        int numActiveLandmarks = 4;
        plm = new PrepareLandmarks(dir, graph, weighting, numLandMarks, numActiveLandmarks);
        plm.doWork();
    }

    private void prepareCH() {
        graph.freeze();
        pch = new PrepareContractionHierarchies(chGraph, weighting, traversalMode);
        pch.doWork();
    }

    private AlgoFactory getAlgoFactory(String algoString) {
        switch (algoString) {
            case "astar":
                return new AlgoFactory() {
                    @Override
                    public RoutingAlgorithm createAlgo(Graph g) {
                        return new AStar(g, weighting, traversalMode);
                    }
                };
            case "astarbi":
                return new AlgoFactory() {
                    @Override
                    public RoutingAlgorithm createAlgo(Graph g) {
                        return new AStarBidirection(g, weighting, traversalMode);
                    }
                };
            case "alt":
                return new AlgoFactory() {
                    @Override
                    public RoutingAlgorithm createAlgo(Graph g) {
                        AStarBidirection baseAlgo = new AStarBidirection(g, weighting, traversalMode);
                        return plm.getDecoratedAlgorithm(g, baseAlgo, AlgorithmOptions.start().build());
                    }
                };
            case "ch_no_sod":
                return new AlgoFactory() {
                    @Override
                    public RoutingAlgorithm createAlgo(Graph g) {
                        return pch.createAlgo(g, AlgorithmOptions.start().hints(new PMap().put("stall_on_demand", false)).build());
                    }
                };
            case "ch":
                return new AlgoFactory() {
                    @Override
                    public RoutingAlgorithm createAlgo(Graph g) {
                        return pch.createAlgo(g, AlgorithmOptions.start().build());
                    }
                };
            default:
                throw new IllegalArgumentException("unknown algo string: " + algoString);
        }
    }

    private interface AlgoFactory {
        RoutingAlgorithm createAlgo(Graph g);
    }
}
