package com.graphhopper.routing.weighting;

import com.graphhopper.RepeatRule;
import com.graphhopper.routing.*;
import com.graphhopper.routing.ch.PrepareContractionHierarchies;
import com.graphhopper.routing.lm.PrepareLandmarks;
import com.graphhopper.routing.profiles.DecimalEncodedValue;
import com.graphhopper.routing.util.CarFlagEncoder;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.routing.util.TraversalMode;
import com.graphhopper.storage.*;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.*;

import static com.graphhopper.util.Parameters.Algorithms.ASTAR_BI;
import static com.graphhopper.util.Parameters.Algorithms.DIJKSTRA_BI;
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
        if (prepareCH) {
            pch = new PrepareContractionHierarchies(chGraph);
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
        // this test would fail because when the distance is approximated for the start node 0 the LMApproximator
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

        Path refPath = new DijkstraBidirectionRef(graph, weighting, TraversalMode.NODE_BASED)
                .calcPath(source, target);
        Path path = createAlgo()
                .calcPath(0, 3);
        comparePaths(refPath, path, source, target);
    }

    @Test
    public void lm_issue2() {
        // This would fail because an underrun of 'delta' would not be treated correctly, and the remaining
        // weight would be over-approximated

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
        GraphHopperStorage gh = new GraphHopperStorage(Collections.singletonList(weighting), dir, encodingManager,
                false, new GraphExtension.NoOpExtension());
        gh.create(1000);
        return gh;
    }

}