/*
 *  Licensed to GraphHopper GmbH under one or more contributor
 *  license agreements. See the NOTICE file distributed with this work for
 *  additional information regarding copyright ownership.
 *
 *  GraphHopper GmbH licenses this file to you under the Apache License,
 *  Version 2.0 (the "License"); you may not use this file except in
 *  compliance with the License. You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package com.graphhopper.routing;

import com.carrotsearch.hppc.IntArrayList;
import com.carrotsearch.hppc.IntIndexedContainer;
import com.graphhopper.Repeat;
import com.graphhopper.RepeatRule;
import com.graphhopper.routing.ch.CHRoutingAlgorithmFactory;
import com.graphhopper.routing.ch.PrepareContractionHierarchies;
import com.graphhopper.routing.lm.LMConfig;
import com.graphhopper.routing.lm.PerfectApproximator;
import com.graphhopper.routing.lm.PrepareLandmarks;
import com.graphhopper.routing.querygraph.QueryGraph;
import com.graphhopper.routing.querygraph.QueryRoutingCHGraph;
import com.graphhopper.routing.util.*;
import com.graphhopper.routing.weighting.Weighting;
import com.graphhopper.storage.*;
import com.graphhopper.storage.index.LocationIndexTree;
import com.graphhopper.storage.index.Snap;
import com.graphhopper.util.ArrayUtil;
import com.graphhopper.util.EdgeIteratorState;
import com.graphhopper.util.GHUtility;
import com.graphhopper.util.PMap;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

import static com.graphhopper.routing.util.TraversalMode.EDGE_BASED;
import static com.graphhopper.routing.util.TraversalMode.NODE_BASED;
import static com.graphhopper.util.GHUtility.createRandomSnaps;
import static com.graphhopper.util.Parameters.Algorithms.*;
import static com.graphhopper.util.Parameters.Routing.ALGORITHM;
import static org.junit.Assert.fail;

/**
 * This test compares different routing algorithms with {@link DijkstraBidirectionRef}. Most prominently it uses
 * randomly create graphs to create all sorts of different situations.
 *
 * @author easbar
 * @see RandomCHRoutingTest - similar but only tests CH algorithms
 * @see DirectedRoutingTest - similar but focuses on edge-based algorithms an directed queries
 */
@RunWith(Parameterized.class)
public class RandomizedRoutingTest {
    private static final Logger LOGGER = LoggerFactory.getLogger(RandomizedRoutingTest.class);
    private final Algo algo;
    private final boolean prepareCH;
    private final boolean prepareLM;
    private final TraversalMode traversalMode;
    private Directory dir;
    private GraphHopperStorage graph;
    private List<CHConfig> chConfigs;
    private LMConfig lmConfig;
    private RoutingCHGraph routingCHGraph;
    private FlagEncoder encoder;
    private TurnCostStorage turnCostStorage;
    private int maxTurnCosts;
    private Weighting weighting;
    private EncodingManager encodingManager;
    private PrepareLandmarks lm;

    @Rule
    public RepeatRule repeatRule = new RepeatRule();

    @Parameterized.Parameters(name = "{0}, {3}")
    public static Collection<Object[]> params() {
        return Arrays.asList(new Object[][]{
                {Algo.DIJKSTRA, false, false, NODE_BASED},
                {Algo.ASTAR_UNIDIR, false, false, NODE_BASED},
                {Algo.ASTAR_BIDIR, false, false, NODE_BASED},
                {Algo.CH_ASTAR, true, false, NODE_BASED},
                {Algo.CH_DIJKSTRA, true, false, NODE_BASED},
                {Algo.LM_UNIDIR, false, true, NODE_BASED},
                {Algo.LM_BIDIR, false, true, NODE_BASED},
                {Algo.DIJKSTRA, false, false, EDGE_BASED},
                {Algo.ASTAR_UNIDIR, false, false, EDGE_BASED},
                {Algo.ASTAR_BIDIR, false, false, EDGE_BASED},
                {Algo.CH_ASTAR, true, false, EDGE_BASED},
                {Algo.CH_DIJKSTRA, true, false, EDGE_BASED},
                {Algo.LM_UNIDIR, false, true, EDGE_BASED},
                {Algo.LM_BIDIR, false, true, EDGE_BASED},
                {Algo.PERFECT_ASTAR, false, false, NODE_BASED}
        });
    }

    private enum Algo {
        DIJKSTRA,
        ASTAR_BIDIR,
        ASTAR_UNIDIR,
        CH_ASTAR,
        CH_DIJKSTRA,
        LM_BIDIR,
        LM_UNIDIR,
        PERFECT_ASTAR
    }

    public RandomizedRoutingTest(Algo algo, boolean prepareCH, boolean prepareLM, TraversalMode traversalMode) {
        this.algo = algo;
        this.prepareCH = prepareCH;
        this.prepareLM = prepareLM;
        this.traversalMode = traversalMode;
    }

    @Before
    public void init() {
        maxTurnCosts = 10;
        dir = new RAMDirectory();
        // todo: this test only works with speedTwoDirections=false (as long as loops are enabled), otherwise it will
        // fail sometimes for edge-based algorithms, #1631, but maybe we can should disable different fwd/bwd speeds
        // only for loops instead?
        encoder = new CarFlagEncoder(5, 5, maxTurnCosts);
        encodingManager = EncodingManager.create(encoder);
        graph = new GraphBuilder(encodingManager)
                .setCHConfigStrings("p1|car|fastest|node", "p2|car|fastest|edge")
                .setDir(dir)
                .create();
        turnCostStorage = graph.getTurnCostStorage();
        chConfigs = graph.getCHConfigs();
        // important: for LM preparation we need to use a weighting without turn costs #1960
        lmConfig = new LMConfig("config", chConfigs.get(0).getWeighting());
        weighting = traversalMode.isEdgeBased() ? chConfigs.get(1).getWeighting() : chConfigs.get(0).getWeighting();
    }

    private void preProcessGraph() {
        graph.freeze();
        if (prepareCH) {
            CHConfig chConfig = !traversalMode.isEdgeBased() ? chConfigs.get(0) : chConfigs.get(1);
            PrepareContractionHierarchies pch = PrepareContractionHierarchies.fromGraphHopperStorage(graph, chConfig);
            pch.doWork();
            routingCHGraph = graph.getRoutingCHGraph(chConfig.getName());
        }
        if (prepareLM) {
            lm = new PrepareLandmarks(dir, graph, lmConfig, 16);
            lm.setMaximumWeight(10000);
            lm.doWork();
        }
    }

    private RoutingAlgorithm createAlgo() {
        return createAlgo(graph);
    }

    private RoutingAlgorithm createAlgo(Graph graph) {
        switch (algo) {
            case DIJKSTRA:
                return new Dijkstra(graph, graph.wrapWeighting(weighting), traversalMode);
            case ASTAR_UNIDIR:
                return new AStar(graph, graph.wrapWeighting(weighting), traversalMode);
            case ASTAR_BIDIR:
                return new AStarBidirection(graph, graph.wrapWeighting(weighting), traversalMode);
            case CH_DIJKSTRA: {
                CHRoutingAlgorithmFactory algoFactory = graph instanceof QueryGraph
                        ? new CHRoutingAlgorithmFactory(new QueryRoutingCHGraph(routingCHGraph, (QueryGraph) graph))
                        : new CHRoutingAlgorithmFactory(routingCHGraph);
                return algoFactory.createAlgo(new PMap().putObject(ALGORITHM, DIJKSTRA_BI));
            }
            case CH_ASTAR: {
                CHRoutingAlgorithmFactory algoFactory = graph instanceof QueryGraph
                        ? new CHRoutingAlgorithmFactory(new QueryRoutingCHGraph(routingCHGraph, (QueryGraph) graph))
                        : new CHRoutingAlgorithmFactory(routingCHGraph);
                return algoFactory.createAlgo(new PMap().putObject(ALGORITHM, ASTAR_BI));
            }
            case LM_BIDIR:
                return lm.getRoutingAlgorithmFactory().createAlgo(graph, AlgorithmOptions.start().weighting(weighting).algorithm(ASTAR_BI).traversalMode(traversalMode).build());
            case LM_UNIDIR:
                return lm.getRoutingAlgorithmFactory().createAlgo(graph, AlgorithmOptions.start().weighting(weighting).algorithm(ASTAR).traversalMode(traversalMode).build());
            case PERFECT_ASTAR: {
                AStarBidirection perfectAStarBi = new AStarBidirection(graph, weighting, traversalMode);
                perfectAStarBi.setApproximation(new PerfectApproximator(graph, weighting, traversalMode, false));
                return perfectAStarBi;
            }
            default:
                throw new IllegalArgumentException("unknown algo " + algo);
        }
    }

    @Test
    @Repeat(times = 5)
    public void randomGraph() {
        final long seed = System.nanoTime();
        final int numQueries = 50;
        Random rnd = new Random(seed);
        GHUtility.buildRandomGraph(graph, rnd, 100, 2.2, true, true,
                encoder.getAccessEnc(), encoder.getAverageSpeedEnc(), null, 0.7, 0.8, 0.8);
        GHUtility.addRandomTurnCosts(graph, seed, encodingManager, encoder, maxTurnCosts, turnCostStorage);
//        GHUtility.printGraphForUnitTest(graph, encoder);
        preProcessGraph();
        List<String> strictViolations = new ArrayList<>();
        for (int i = 0; i < numQueries; i++) {
            int source = getRandom(rnd);
            int target = getRandom(rnd);
//            LOGGER.info("source: " + source + ", target: " + target);
            Path refPath = new DijkstraBidirectionRef(graph, weighting, traversalMode)
                    .calcPath(source, target);
            Path path = createAlgo()
                    .calcPath(source, target);
            strictViolations.addAll(comparePaths(refPath, path, source, target, seed));
        }
        if (strictViolations.size() > 3) {
            for (String strictViolation : strictViolations) {
                LOGGER.info("strict violation: " + strictViolation);
            }
            fail("Too many strict violations: " + strictViolations.size() + " / " + numQueries + ", seed: " + seed);
        }
    }

    /**
     * Similar to {@link #randomGraph()}, but using the {@link QueryGraph} as it is done in real usage.
     */
    @Test
    @Repeat(times = 5)
    public void randomGraph_withQueryGraph() {
        final long seed = System.nanoTime();
        final int numQueries = 50;

        // we may not use an offset when query graph is involved, otherwise traveling via virtual edges will not be
        // the same as taking the direct edge!
        double pOffset = 0;
        Random rnd = new Random(seed);
        GHUtility.buildRandomGraph(graph, rnd, 50, 2.2, true, true,
                encoder.getAccessEnc(), encoder.getAverageSpeedEnc(), null, 0.7, 0.8, pOffset);
        GHUtility.addRandomTurnCosts(graph, seed, encodingManager, encoder, maxTurnCosts, turnCostStorage);
//        GHUtility.printGraphForUnitTest(graph, encoder);
        preProcessGraph();
        LocationIndexTree index = new LocationIndexTree(graph, dir);
        index.prepareIndex();
        List<String> strictViolations = new ArrayList<>();
        for (int i = 0; i < numQueries; i++) {
            List<Snap> snaps = createRandomSnaps(graph.getBounds(), index, rnd, 2, true, EdgeFilter.ALL_EDGES);
            QueryGraph queryGraph = QueryGraph.create(graph, snaps);

            int source = snaps.get(0).getClosestNode();
            int target = snaps.get(1).getClosestNode();

            Path refPath = new DijkstraBidirectionRef(queryGraph, queryGraph.wrapWeighting(weighting), traversalMode).calcPath(source, target);
            Path path = createAlgo(queryGraph).calcPath(source, target);
            strictViolations.addAll(comparePaths(refPath, path, source, target, seed));
        }
        // we do not do a strict check because there can be ambiguity, for example when there are zero weight loops.
        // however, when there are too many deviations we fail
        if (strictViolations.size() > 3) {
            LOGGER.warn(strictViolations.toString());
            fail("Too many strict violations: " + strictViolations.size() + " / " + numQueries + ", seed: " + seed);
        }
    }

    private List<String> comparePaths(Path refPath, Path path, int source, int target, long seed) {
        List<String> strictViolations = new ArrayList<>();
        double refWeight = refPath.getWeight();
        double weight = path.getWeight();
        if (Math.abs(refWeight - weight) > 1.e-2) {
            LOGGER.warn("expected: " + refPath.calcNodes());
            LOGGER.warn("given:    " + path.calcNodes());
            LOGGER.warn("seed: " + seed);
            fail("wrong weight: " + source + "->" + target + "\nexpected: " + refWeight + "\ngiven:    " + weight + "\nseed: " + seed);
        }
        if (Math.abs(path.getDistance() - refPath.getDistance()) > 1.e-1) {
            strictViolations.add("wrong distance " + source + "->" + target + ", expected: " + refPath.getDistance() + ", given: " + path.getDistance());
        }
        if (Math.abs(path.getTime() - refPath.getTime()) > 50) {
            strictViolations.add("wrong time " + source + "->" + target + ", expected: " + refPath.getTime() + ", given: " + path.getTime());
        }
        IntIndexedContainer refNodes = refPath.calcNodes();
        IntIndexedContainer pathNodes = path.calcNodes();
        if (!refNodes.equals(pathNodes)) {
            // sometimes paths are only different because of a zero weight loop. we do not consider these as strict
            // violations, see: #1864
            boolean isStrictViolation = !ArrayUtil.withoutConsecutiveDuplicates(refNodes).equals(ArrayUtil.withoutConsecutiveDuplicates(pathNodes));
            // sometimes there are paths including an edge a-c that has the same distance as the two edges a-b-c. in this
            // case both options are valid best paths. we only check for this most simple and frequent case here...
            if (pathsEqualExceptOneEdge(refNodes, pathNodes))
                isStrictViolation = false;
            if (isStrictViolation)
                strictViolations.add("wrong nodes " + source + "->" + target + "\nexpected: " + refNodes + "\ngiven:    " + pathNodes);
        }
        return strictViolations;
    }

    private int getRandom(Random rnd) {
        return rnd.nextInt(graph.getNodes());
    }

    /**
     * Sometimes the graph can contain edges like this:
     * A--C
     * \-B|
     * where A-C is the same distance as A-B-C. In this case the shortest path is not well defined in terms of nodes.
     * This method check if two node-paths are equal with the exception of such an edge.
     */
    private boolean pathsEqualExceptOneEdge(IntIndexedContainer p1, IntIndexedContainer p2) {
        if (p1.equals(p2))
            throw new IllegalArgumentException("paths are equal");
        if (Math.abs(p1.size() - p2.size()) != 1)
            return false;
        IntIndexedContainer shorterPath = p1.size() < p2.size() ? p1 : p2;
        IntIndexedContainer longerPath = p1.size() < p2.size() ? p2 : p1;
        if (shorterPath.size() < 2)
            return false;
        IntArrayList indicesWithDifferentNodes = new IntArrayList();
        for (int i = 1; i < shorterPath.size(); i++) {
            if (shorterPath.get(i - indicesWithDifferentNodes.size()) != longerPath.get(i)) {
                indicesWithDifferentNodes.add(i);
            }
        }
        if (indicesWithDifferentNodes.size() != 1)
            return false;
        int b = indicesWithDifferentNodes.get(0);
        int a = b - 1;
        int c = b + 1;
        assert shorterPath.get(a) == longerPath.get(a);
        assert shorterPath.get(b) != longerPath.get(b);
        if (shorterPath.get(b) != longerPath.get(c))
            return false;
        double distABC = getDist(longerPath.get(a), longerPath.get(b)) + getDist(longerPath.get(b), longerPath.get(c));

        double distAC = getDist(shorterPath.get(a), longerPath.get(c));
        if (Math.abs(distABC - distAC) > 0.1)
            return false;
        LOGGER.info("Distance " + shorterPath.get(a) + "-" + longerPath.get(c) + " is the same as distance " +
                longerPath.get(a) + "-" + longerPath.get(b) + "-" + longerPath.get(c) + " -> there are multiple possibilities " +
                "for shortest paths");
        return true;
    }

    private double getDist(int p, int q) {
        EdgeIteratorState edge = GHUtility.getEdge(graph, p, q);
        if (edge == null) return Double.POSITIVE_INFINITY;
        return edge.getDistance();
    }

}
