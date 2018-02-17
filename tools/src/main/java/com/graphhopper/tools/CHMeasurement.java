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
package com.graphhopper.tools;

import com.graphhopper.GHRequest;
import com.graphhopper.GHResponse;
import com.graphhopper.GraphHopper;
import com.graphhopper.reader.osm.GraphHopperOSM;
import com.graphhopper.reader.osm.OSMReader;
import com.graphhopper.routing.AlgorithmOptions;
import com.graphhopper.routing.Dijkstra;
import com.graphhopper.routing.RoutingAlgorithm;
import com.graphhopper.routing.ch.CHAlgoFactoryDecorator;
import com.graphhopper.routing.ch.ManualPrepareContractionHierarchies;
import com.graphhopper.routing.util.*;
import com.graphhopper.routing.weighting.FastestWeighting;
import com.graphhopper.routing.weighting.TurnWeighting;
import com.graphhopper.storage.*;
import com.graphhopper.util.*;
import com.graphhopper.util.exceptions.ConnectionNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static com.graphhopper.util.Parameters.Algorithms.DIJKSTRA_BI;
import static java.lang.System.nanoTime;

/**
 * Measurement class used to analyze different contraction hierarchy parameters.
 */
public class CHMeasurement {
    // todo: make this class more useful, for example use command line arguments to be able to automatically run tests
    // for larger parameter ranges
    private static final Logger LOGGER = LoggerFactory.getLogger(CHMeasurement.class);
    private FastestWeighting weighting;
    private GraphHopperStorage ghStorage;
    private TurnCostExtension turnCostExtension;
    private CHGraph chGraph;
    private int maxTurnCost;
    private double pNodeHasTurnCosts;
    private double pEdgePairHasTurnCosts;
    private double pCostIsRestriction;
    private long seed;
    private String osmFile;
    private long totalElapsed;
    private double nodesContractedPercentage;

    public static void main(String[] args) {
//        testPerformanceAutomaticNodeOrdering();
        new CHMeasurement().testPerformanceFixedNodeOrdering();
    }

    /**
     * Parses a given osm file, applies additional random turn costs and restrictions and contracts the graph using
     * a predetermined node contraction order. Afterwards some random routes are compared with the results of a normal
     * Dijkstra algorithm. This is helpful to analyze and debug the performance of the graph contraction, because using
     * an automatic/heuristic node contraction order makes it hard to separate the performance implications of a changed
     * contraction order and a changed contraction algorithm.
     */
    private void testPerformanceFixedNodeOrdering() {
        osmFile = "bremen-latest.osm.pbf";
        maxTurnCost = 100;
        seed = 123;
        pNodeHasTurnCosts = 0.3;
        pEdgePairHasTurnCosts = 0.6;
        pCostIsRestriction = 0.1;
        // since we are using random contraction orders the contraction becomes unrealistically slow towards the very 
        // end --> do not contract all nodes
        nodesContractedPercentage = 85;

        LOGGER.info("Using seed: {}", seed);
        int numRepeats = 5;
        totalElapsed = 0;
        for (int i = 0; i < numRepeats - 1; ++i) {
            contractGraphWithRandomTurnCosts();
        }
        ManualPrepareContractionHierarchies pch = contractGraphWithRandomTurnCosts();
        int numNodes = ghStorage.getNodes();
        int numShortcuts = chGraph.getAllEdges().getMaxId() - ghStorage.getAllEdges().getMaxId();
        LOGGER.info("### Average contraction time: {} ms, time per node: {} micros, time per shortcut: {} micros",
                Helper.nf(totalElapsed / numRepeats / 1_000_000),
                Helper.nf(totalElapsed / numRepeats / numNodes / 1_000),
                Helper.nf(totalElapsed / numRepeats / numShortcuts / 1_000));
        LOGGER.info("nodes: {}, edges: {}, shortcuts: {}", Helper.nf(numNodes), Helper.nf(ghStorage.getAllEdges().getMaxId()), Helper.nf(numShortcuts));

        LOGGER.info("Running comparison with dijkstra to check correctness... ");
        int errorCount = 0;
        int iterations = 1_000;
        int maxErrorLogCount = 10;
        double maxError = 0;
        double minError = Double.POSITIVE_INFINITY;
        double errorThreshold = 1.e-1;
        Random rnd = new Random(seed);
        for (int i = 0; i < iterations; ++i) {
            int p = rnd.nextInt(numNodes);
            int q = rnd.nextInt(numNodes);
            RoutingAlgorithm chAlgo = pch.createAlgo(chGraph, new AlgorithmOptions(DIJKSTRA_BI, weighting));
            double chWeight = chAlgo.calcPath(p, q).getWeight();

            Dijkstra dijkstra = new Dijkstra(ghStorage, new TurnWeighting(weighting, turnCostExtension), TraversalMode.EDGE_BASED_2DIR);
            double dijkstraWeight = dijkstra.calcPath(p, q).getWeight();

            double error = Math.abs(chWeight - dijkstraWeight);
            maxError = Math.max(error, maxError);
            minError = Math.min(error, minError);
            if (error > errorThreshold) {
                errorCount++;
                if (errorCount < maxErrorLogCount) {
                    LOGGER.error("Dijkstra and CH yielded different weight for path from {} to {}, {}", p, q,
                            getWeightDifferenceString(dijkstraWeight, chWeight));
                } else if (errorCount == maxErrorLogCount) {
                    LOGGER.error("... more errors (not shown), {} / {} queries completed until maximum error log count reached", i, iterations);
                }
            }
        }
        LOGGER.info("Finished {} random queries", iterations);
        LOGGER.info("errors: {}, max error: {}, min error: {}", errorCount, maxError, minError);
    }

    private ManualPrepareContractionHierarchies contractGraphWithRandomTurnCosts() {
        FlagEncoder encoder = new CarFlagEncoder(5, 5, maxTurnCost);
        EncodingManager encodingManager = new EncodingManager(encoder);
        // todo: are there any performance differences between fastest and shortest routes ? this should be the case
        // according to Curt Nowak's PhD thesis.
        weighting = new FastestWeighting(encoder);
        ghStorage = new GraphBuilder(encodingManager).setCHGraph(weighting).setEdgeBasedCH(true).build();
        turnCostExtension = (TurnCostExtension) ghStorage.getExtension();
        chGraph = ghStorage.getGraph(CHGraph.class);
        RAMDirectory dir = new RAMDirectory();
        OSMReader osmReader = new OSMReader(ghStorage);
        osmReader.setFile(new File(osmFile));
        try {
            LOGGER.info("Reading graph for file {}", osmFile);
            osmReader.readGraph();
        } catch (IOException e) {
            throw new IllegalArgumentException("Could not read graph: " + osmFile + ", the error was: " + e.getMessage());
        }

        Random rnd = new Random(seed);
        LOGGER.info("Adding random turn costs and restrictions");
        EdgeExplorer inExplorer = ghStorage.createEdgeExplorer(new DefaultEdgeFilter(encoder, true, false));
        EdgeExplorer outExplorer = ghStorage.createEdgeExplorer(new DefaultEdgeFilter(encoder, false, true));
        for (int node = 0; node < ghStorage.getNodes(); ++node) {
            if (rnd.nextDouble() < pNodeHasTurnCosts) {
                EdgeIterator inIter = inExplorer.setBaseNode(node);
                while (inIter.next()) {
                    EdgeIterator outIter = outExplorer.setBaseNode(node);
                    while (outIter.next()) {
                        if (inIter.getEdge() == outIter.getEdge()) {
                            // leave u-turns as they are
                            continue;
                        }
                        if (rnd.nextDouble() < pEdgePairHasTurnCosts) {
                            boolean restricted = false;
                            if (rnd.nextDouble() < pCostIsRestriction) {
                                restricted = true;
                            }
                            double cost = restricted ? 0 : rnd.nextDouble() * maxTurnCost;
                            turnCostExtension.addTurnInfo(inIter.getEdge(), node, outIter.getEdge(),
                                    encoder.getTurnFlags(restricted, cost));
                        }
                    }
                }
            }
        }

        LOGGER.info("Running graph contraction... ");
        LOGGER.info("nodes: {}, nodes to contract: {}, edges: {}, shortcuts: {}",
                Helper.nf(ghStorage.getNodes()), Helper.nf((int) (ghStorage.getNodes() / 100 * nodesContractedPercentage)),
                Helper.nf(ghStorage.getAllEdges().getMaxId()), Helper.nf(chGraph.getAllEdges().getMaxId() - ghStorage.getAllEdges().getMaxId()));
        long start = nanoTime();
        ManualPrepareContractionHierarchies pch = new ManualPrepareContractionHierarchies(
                dir, ghStorage, chGraph, weighting, TraversalMode.EDGE_BASED_2DIR);
        pch.setSeed(seed);
        pch.setContractedNodes(nodesContractedPercentage);
        pch.doWork();
        long elapsed = nanoTime() - start;
        totalElapsed += elapsed;
        LOGGER.info("### Finished graph contraction, took {} ms", Helper.nf(elapsed / 1_000_000));
        return pch;
    }

    /**
     * Parses a given osm file, contracts the graph and runs random routing queries on it. This is useful to test
     * the node contraction heuristics with regards to the performance of the automatic graph contraction (the node
     * contraction order determines how many and which shortcuts will be introduced) and the resulting query speed.
     * The queries are compared with a normal AStar search for comparison and to ensure correctness.
     */
    private static void testPerformanceAutomaticNodeOrdering() {
        String osmFile = "bremen-latest.osm.pbf";
        final GraphHopper graphHopper = new GraphHopperOSM();
        CmdArgs cmdArgs = new CmdArgs();
        cmdArgs.put("datareader.file", osmFile);
        final boolean withTurnCosts = true;
//        final boolean withTurnCosts = false;
        if (withTurnCosts) {
            cmdArgs.put("graph.flag_encoders", "car|turn_costs=true");
            cmdArgs.put("prepare.ch.weightings", "fastest");
        } else {
            cmdArgs.put("graph.flag_encoders", "car");
            cmdArgs.put("prepare.ch.weightings", "no");
        }
        CHAlgoFactoryDecorator decorator = graphHopper.getCHFactoryDecorator();
        decorator.setDisablingAllowed(true);
        decorator.setPreparationPeriodicUpdates(20); // default: 20
        decorator.setPreparationLazyUpdates(100);     // default: 10
        decorator.setPreparationNeighborUpdates(4); // default: 20
        decorator.setPreparationContractedNodes(100);// default: 100
        decorator.setPreparationLogMessages(20); // default: 20
        graphHopper.init(cmdArgs);

        // remove previous data
        graphHopper.clean();

        StopWatch sw = new StopWatch();
        sw.start();
        graphHopper.importOrLoad();
        sw.stop();
        LOGGER.info("Import and preparation took {}s", sw.getTime() / 1000);

        long seed = nanoTime();
        LOGGER.info("Using seed {}", seed);
        Graph g = graphHopper.getGraphHopperStorage();
        final int numNodes = g.getNodes();
        final NodeAccess nodeAccess = g.getNodeAccess();
        final Random random = new Random(seed);

        final int iterations = 1_000;

        MiniPerfTest compareTest = new MiniPerfTest() {
            long chTime = 0;
            long noChTime = 0;

            @Override
            public int doCalc(boolean warmup, int run) {
                if (!warmup && run % 100 == 0) {
                    LOGGER.info("Finished {} of {} runs. {}", run, iterations,
                            run > 0 ? String.format(" CH: %6.2fms, without CH: %6.2fms",
                                    chTime * 1.e-6 / run, noChTime * 1.e-6 / run) : "");
                }
                GHRequest req = buildRandomRequest(random, numNodes, nodeAccess);
                req.getHints().put(Parameters.Routing.EDGE_BASED, withTurnCosts);
                req.getHints().put(Parameters.CH.DISABLE, false);
                long start = nanoTime();
                GHResponse chRoute = graphHopper.route(req);
                if (!warmup)
                    chTime += (nanoTime() - start);

                req.getHints().put(Parameters.CH.DISABLE, true);
                start = nanoTime();
                GHResponse nonChRoute = graphHopper.route(req);
                if (!warmup)
                    noChTime += nanoTime() - start;

                if (connectionNotFound(chRoute) && connectionNotFound(nonChRoute)) {
                    // random query was not well defined -> ignore
                    return 0;
                }

                if (!chRoute.getErrors().isEmpty() || !nonChRoute.getErrors().isEmpty()) {
                    LOGGER.warn("there were errors: \n with CH: {} \n without CH: {}", chRoute.getErrors(), nonChRoute.getErrors());
                    return chRoute.getErrors().size();
                }

                if (!chRoute.getBest().getPoints().equals(nonChRoute.getBest().getPoints())) {
                    // todo: this test finds some differences that are most likely due to rounding issues (the weights
                    // are very similar, and the paths have minor differences (with ch the route weight seems to be smaller if different)
                    double chWeight = chRoute.getBest().getRouteWeight();
                    double nonCHWeight = nonChRoute.getBest().getRouteWeight();
                    LOGGER.warn("error: found different points for query from {} to {}, {}",
                            req.getPoints().get(0).toShortString(), req.getPoints().get(1).toShortString(),
                            getWeightDifferenceString(chWeight, nonCHWeight));
                }
                return chRoute.getErrors().size();
            }
        };


        MiniPerfTest performanceTest = new MiniPerfTest() {
            private long queryTime;

            @Override
            public int doCalc(boolean warmup, int run) {
                if (!warmup && run % 100 == 0) {
                    LOGGER.info("Finished {} of {} runs. {}", run, iterations,
                            run > 0 ? String.format(" Time: %6.2fms", queryTime * 1.e-6 / run) : "");
                }
                GHRequest req = buildRandomRequest(random, numNodes, nodeAccess);
                req.getHints().put(Parameters.Routing.EDGE_BASED, withTurnCosts);
                long start = nanoTime();
                GHResponse route = graphHopper.route(req);
                if (!warmup)
                    queryTime += nanoTime() - start;
                return getRealErrors(route).size();
            }
        };


        compareTest.setIterations(iterations).start();
        performanceTest.setIterations(iterations).start();
        if (performanceTest.getDummySum() > 0.01 * iterations) {
            throw new IllegalStateException("too many errors, probably something is wrong");
        }
        LOGGER.info("Average query time: {}ms", performanceTest.getMean());


        graphHopper.close();
    }

    private static String getWeightDifferenceString(double weight1, double weight2) {
        return String.format("route weight: %.2f vs. %.2f (diff = %.4f)",
                weight1, weight2, (weight1 - weight2));
    }

    private static boolean connectionNotFound(GHResponse response) {
        for (Throwable t : response.getErrors()) {
            if (t instanceof ConnectionNotFoundException) {
                return true;
            }
        }
        return false;
    }

    private static List<Throwable> getRealErrors(GHResponse response) {
        List<Throwable> realErrors = new ArrayList<>();
        for (Throwable t : response.getErrors()) {
            if (!(t instanceof ConnectionNotFoundException)) {
                realErrors.add(t);
            }
        }
        return realErrors;
    }

    private static GHRequest buildRandomRequest(Random random, int numNodes, NodeAccess nodeAccess) {
        int from = random.nextInt(numNodes);
        int to = random.nextInt(numNodes);
        double fromLat = nodeAccess.getLat(from);
        double fromLon = nodeAccess.getLon(from);
        double toLat = nodeAccess.getLat(to);
        double toLon = nodeAccess.getLon(to);
        return new GHRequest(fromLat, fromLon, toLat, toLon);
    }

}
