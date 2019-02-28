package com.graphhopper.routing;

import com.graphhopper.Repeat;
import com.graphhopper.RepeatRule;
import com.graphhopper.routing.ch.PrepareContractionHierarchies;
import com.graphhopper.routing.lm.PrepareLandmarks;
import com.graphhopper.routing.subnetwork.PrepareRoutingSubnetworks;
import com.graphhopper.routing.util.CarFlagEncoder;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.routing.util.FlagEncoder;
import com.graphhopper.routing.util.TraversalMode;
import com.graphhopper.routing.weighting.FastestWeighting;
import com.graphhopper.routing.weighting.Weighting;
import com.graphhopper.storage.*;
import com.graphhopper.util.GHUtility;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.Arrays;
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
    private Random rnd;

    @Rule
    public RepeatRule repeatRule = new RepeatRule();

    @Parameterized.Parameters(name = "{0}")
    public static Object[] parameters() {
        return new Object[]{
//                "astar",
//                "astarbi",
                "alt",
//                "ch"
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
        int numNodes = 500;
        GHUtility.buildRandomGraph(graph, seed, numNodes, 2.2, true, true, 0.9);
        // we only want to look at fully connected graphs (otherwise we get problems with lm preprocessing)
        // only exit here for very small graphs though, otherwise there will be hardly any graph that works
        if (numNodes < 20 && getNumComponents() > 1) {
            return;
        }
        compareWithDijkstra();
    }

    @Test
    public void failure1() {
        na.setNode(0, 49.408249, 9.706623);
        na.setNode(1, 49.402517, 9.706126);
        na.setNode(2, 49.407259, 9.704586);
        na.setNode(3, 49.402448, 9.701743);
        na.setNode(4, 49.407824, 9.708467);
        na.setNode(5, 49.404076, 9.703126);
        na.setNode(6, 49.401521, 9.708343);
        na.setNode(7, 49.409590, 9.708180);
        na.setNode(8, 49.402218, 9.707801);
        na.setNode(9, 49.405432, 9.701699);
        graph.edge(3, 8, 443.080000, false);
        graph.edge(3, 9, 334.552000, true);
        graph.edge(8, 6, 87.543000, true);
        graph.edge(2, 6, 696.117000, true);
        graph.edge(9, 5, 183.100000, true);
        graph.edge(8, 4, 625.201000, true);
        graph.edge(4, 5, 570.895000, true);
        graph.edge(0, 7, 186.937000, true);
        graph.edge(2, 1, 538.972000, true);
        graph.edge(1, 7, 800.444000, true);
        compareWithDijkstra();
    }

    @Test
    public void failure2() {
        na.setNode(0, 49.401356, 9.709981);
        na.setNode(1, 49.404060, 9.706504);
        na.setNode(2, 49.402297, 9.707753);
        na.setNode(3, 49.406333, 9.705954);
        na.setNode(4, 49.407902, 9.707033);
        na.setNode(5, 49.408493, 9.708042);
        na.setNode(6, 49.402978, 9.707720);
        na.setNode(7, 49.401923, 9.709657);
        na.setNode(8, 49.409824, 9.703268);
        na.setNode(9, 49.408189, 9.706820);
        na.setNode(10, 49.406989, 9.702563);
        na.setNode(11, 49.400188, 9.700618);
        na.setNode(12, 49.407497, 9.703106);
        na.setNode(13, 49.406428, 9.706218);
        na.setNode(14, 49.404405, 9.706979);
        graph.edge(13, 3, 21.889000, false);
        graph.edge(1, 7, 329.368000, false);
        graph.edge(2, 0, 193.828000, false);
        graph.edge(1, 2, 215.843000, true);
        graph.edge(0, 8, 1065.042000, true);
        graph.edge(7, 0, 67.463000, false);
        graph.edge(3, 5, 283.808000, true);
        graph.edge(7, 4, 692.211000, true);
        graph.edge(10, 11, 772.877000, true);
        graph.edge(14, 14, 0.000000, true);
        graph.edge(1, 13, 265.386000, true);
        graph.edge(10, 13, 271.717000, false);
        graph.edge(13, 10, 272.023000, true);
        graph.edge(6, 6, 0.000000, true);
        graph.edge(12, 7, 780.237000, true);
        graph.edge(5, 7, 747.208000, true);
        graph.edge(9, 6, 584.323000, true);
        graph.edge(10, 7, 767.711000, true);
        compareWithDijkstra();
    }

    @Test
    public void failure3() {
        na.setNode(0, 49.409696, 9.702343);
        na.setNode(1, 49.408076, 9.701021);
        na.setNode(2, 49.401475, 9.706790);
        na.setNode(3, 49.400892, 9.706083);
        na.setNode(4, 49.402244, 9.704128);
        na.setNode(5, 49.406651, 9.701573);
        na.setNode(6, 49.400498, 9.700916);
        na.setNode(7, 49.401397, 9.703822);
        na.setNode(8, 49.404309, 9.707009);
        na.setNode(9, 49.406880, 9.701170);
        graph.edge(9, 0, 325.779000, false);
        graph.edge(8, 5, 471.731000, true);
        graph.edge(9, 7, 639.138000, true);
        graph.edge(6, 0, 1037.960000, true);
        graph.edge(1, 8, 608.262000, true);
        graph.edge(4, 1, 689.234000, true);
        graph.edge(1, 7, 776.216000, true);
        graph.edge(6, 2, 441.307000, true);
        graph.edge(8, 3, 387.528000, false);
        graph.edge(2, 5, 688.273000, true);
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
        if (algoString.equals("alt"))
            prepareLM();
        if (algoString.equals("ch"))
            prepareCH();
        for (int i = 0; i < 100_000; ++i) {
            RoutingAlgorithm algo = algoFactory.createAlgo();
            DijkstraBidirectionRef refAlgo = new DijkstraBidirectionRef(graph, weighting, TraversalMode.NODE_BASED);

            int from = rnd.nextInt(graph.getNodes());
            int to = rnd.nextInt(graph.getNodes());
            Path path = algo.calcPath(from, to);
            Path refPath = refAlgo.calcPath(from, to);
            double weight = path.getWeight();
            double refWeight = refPath.getWeight();
            // todo: using very rough threshold, but sometimes there are large deviations
            if (Math.abs(refWeight - weight) > 10) {
                GHUtility.printGraphForUnitTest(graph, encoder);
                System.out.println("dijkstra: " + refPath.calcNodes());
                System.out.println(algoString + ": " + path.calcNodes());
                fail(from + "->" + to + ", dijkstra: " + refWeight + " vs. " + algoString + ": " + path.getWeight());
            }
            // todo: compare calculated distances and nodes as well as soon as weights match
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
                    public RoutingAlgorithm createAlgo() {
                        return new AStar(graph, weighting, traversalMode);
                    }
                };
            case "astarbi":
                return new AlgoFactory() {
                    @Override
                    public RoutingAlgorithm createAlgo() {
                        return new AStarBidirection(graph, weighting, traversalMode);
                    }
                };
            case "alt":
                return new AlgoFactory() {
                    @Override
                    public RoutingAlgorithm createAlgo() {
                        AStarBidirection baseAlgo = new AStarBidirection(graph, weighting, traversalMode);
                        return plm.getDecoratedAlgorithm(graph, baseAlgo, AlgorithmOptions.start().build());
                    }
                };
            case "ch":
                return new AlgoFactory() {
                    @Override
                    public RoutingAlgorithm createAlgo() {
                        return pch.createAlgo(chGraph, AlgorithmOptions.start().build());
                    }
                };
            default:
                throw new IllegalArgumentException("unknown algo string: " + algoString);
        }
    }

    private interface AlgoFactory {
        RoutingAlgorithm createAlgo();
    }
}
