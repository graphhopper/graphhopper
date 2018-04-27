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
import com.graphhopper.routing.ch.*;
import com.graphhopper.routing.lm.LMAlgoFactoryDecorator;
import com.graphhopper.routing.subnetwork.PrepareRoutingSubnetworks;
import com.graphhopper.routing.util.*;
import com.graphhopper.routing.weighting.FastestWeighting;
import com.graphhopper.routing.weighting.TurnWeighting;
import com.graphhopper.storage.*;
import com.graphhopper.util.*;
import com.graphhopper.util.exceptions.ConnectionNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

import static com.graphhopper.util.Parameters.Algorithms.ASTAR_BI;
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
        testPerformanceAutomaticNodeOrdering(args);
//        new CHMeasurement().testPerformanceFixedNodeOrdering();
//        new CHMeasurement().analyzeLegacyVsAggressive();
    }

    private void analyzeNodePrio() {
        osmFile = "local/maps/bremen-latest.osm.pbf";
        maxTurnCost = 100;
        seed = 91358696691522L;
        System.out.println("seed : " + seed);

        FlagEncoder encoder = new CarFlagEncoder(5, 5, maxTurnCost);
        EncodingManager encodingManager = new EncodingManager(encoder);
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
        ghStorage.freeze();

        EdgeBasedNodeContractor contractor = new EdgeBasedNodeContractor(dir, ghStorage, chGraph, new TurnWeighting(new PreparationWeighting(weighting), turnCostExtension), TraversalMode.EDGE_BASED_2DIR);
        contractor.initFromGraph();
        int maxLevel = chGraph.getNodes();
        for (int i = 0; i < chGraph.getNodes(); ++i) {
            chGraph.setLevel(i, maxLevel);
        }
        for (int i = 0; i < chGraph.getNodes(); ++i) {
            EdgeBasedNodeContractor.searchType = SearchType.AGGRESSIVE;
            float prio = contractor.calculatePriority(i);
            System.out.println("aggressive: " + prio);
//            System.out.printf("aggressive: %f, sc: %d, sc-prev: %d, o: %d, o-prev: %d, ", prio, contractor.numEdges, contractor.numPrevEdges, contractor.numOrigEdges, contractor.numPrevOrigEdges);
            EdgeBasedNodeContractor.searchType = SearchType.LEGACY_AGGRESSIVE;
            float legacyPrio = contractor.calculatePriority(i);
            System.out.println("legacyaggr: " + legacyPrio);
//            System.out.printf("legacyaggr:  %f, sc: %d, sc-prev: %d, o: %d, o-prev: %d\n", legacyPrio, contractor.numEdges, contractor.numPrevEdges, contractor.numOrigEdges, contractor.numPrevOrigEdges);
        }
    }
    
    private void analyzeLegacyVsAggressive() {
        osmFile = "local/maps/bremen-latest.osm.pbf";
        maxTurnCost = 100;
        seed = 91358696691522L;
        System.out.println("seed : " + seed);

        EdgeBasedNodeContractor.searchType = SearchType.AGGRESSIVE;
        List<ManualPrepareContractionHierarchies.Stats> aggressiveCounts = runContraction();
        System.out.printf("aggressive: numpolled = %d (%d), numsearches = %d (%d)\n", getTotalPolled(aggressiveCounts), WitnessPathFinder.pollCount, getTotalSearches(aggressiveCounts), WitnessPathFinder.searchCount);

        EdgeBasedNodeContractor.searchType = SearchType.LEGACY_AGGRESSIVE;
        List<ManualPrepareContractionHierarchies.Stats> legacyCounts = runContraction();
        System.out.printf("legacyaggr: numpolled = %d (%d), numsearches = %d (%d)\n", getTotalPolled(legacyCounts), LegacyWitnessPathFinder.pollCount, getTotalSearches(legacyCounts), LegacyWitnessPathFinder.searchCount);

        if (aggressiveCounts.size() != legacyCounts.size()) {
            throw new IllegalStateException("shouldnt be really");
        }
        for (int i = 0; i < Math.min(aggressiveCounts.size(), 10); ++i) {
            if (aggressiveCounts.get(i).shortcutCount > legacyCounts.get(i).shortcutCount) {
                System.out.println("found one: " + aggressiveCounts.get(i).nodeId + " idx: " + i + ", " + aggressiveCounts.get(i).shortcutCount + "-" + legacyCounts.get(i).shortcutCount);
            }
        }
        for (int i = 0; i < Math.min(aggressiveCounts.size(), 10); ++i) {
            if (aggressiveCounts.get(i).numPolled > legacyCounts.get(i).numPolled) {
                System.out.println("found one poll count: " + aggressiveCounts.get(i).nodeId + " idx: " + i + ", " + aggressiveCounts.get(i).numPolled + "-" + legacyCounts.get(i).numPolled);
            }
        }
    }

    private int getTotalPolled(List<ManualPrepareContractionHierarchies.Stats> counts) {
        int result = 0;
        for (ManualPrepareContractionHierarchies.Stats s : counts) {
            result += s.numPolled;
        }
        return result;
    }

    private int getTotalSearches(List<ManualPrepareContractionHierarchies.Stats> counts) {
        int result = 0;
        for (ManualPrepareContractionHierarchies.Stats s : counts) {
            result += s.numSearches;
        }
        return result;
    }

    private List<ManualPrepareContractionHierarchies.Stats> runContraction() {
        FlagEncoder encoder = new CarFlagEncoder(5, 5, maxTurnCost);
        EncodingManager encodingManager = new EncodingManager(encoder);
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

        ManualPrepareContractionHierarchies pch = new ManualPrepareContractionHierarchies(
                dir, ghStorage, chGraph, weighting, TraversalMode.EDGE_BASED_2DIR);
        pch.setSeed(seed);
        pch.setContractedNodes(50);
        pch.doWork();
        return pch.getStats();
    }

    /**
     * Parses a given osm file, applies additional random turn costs and restrictions and contracts the graph using
     * a predetermined node contraction order. Afterwards some random routes are compared with the results of a normal
     * Dijkstra algorithm. This is helpful to analyze and debug the performance of the graph contraction, because using
     * an automatic/heuristic node contraction order makes it hard to separate the performance implications of a changed
     * contraction order and a changed contraction algorithm.
     */
    private void testPerformanceFixedNodeOrdering() {
        osmFile = "local/maps/bremen-latest.osm.pbf";
        EdgeBasedNodeContractor.searchType = SearchType.AGGRESSIVE;
        EdgeBasedNodeContractor.arrayBasedWitnessPathFinder = true;
        LegacyWitnessPathFinder.sigmaFactor = 4.0;
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
        int numShortcuts = chGraph.getAllEdges().length() - ghStorage.getAllEdges().length();
        LOGGER.info("### Average contraction time: {} ms, time per node: {} micros, time per shortcut: {} micros",
                Helper.nf(totalElapsed / numRepeats / 1_000_000),
                Helper.nf(totalElapsed / numRepeats / numNodes / 1_000),
                Helper.nf(totalElapsed / numRepeats / numShortcuts / 1_000));
        LOGGER.info("nodes: {}, edges: {}, shortcuts: {}", Helper.nf(numNodes), Helper.nf(ghStorage.getAllEdges().length()), Helper.nf(numShortcuts));

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

        // remove subnetworks, important if we want to reuse some previously found contraction order
        int prevNodeCount = ghStorage.getNodes();
        PrepareRoutingSubnetworks preparation = new PrepareRoutingSubnetworks(ghStorage, encodingManager.fetchEdgeEncoders());
        preparation.setMinNetworkSize(200);
        preparation.setMinOneWayNetworkSize(0);
        preparation.doWork();
        int currNodeCount = ghStorage.getNodes();
        LOGGER.info("edges: " + Helper.nf(ghStorage.getAllEdges().length()) + ", nodes " + Helper.nf(currNodeCount)
                + ", there were " + Helper.nf(preparation.getMaxSubnetworks())
                + " subnetworks. removed them => " + Helper.nf(prevNodeCount - currNodeCount)
                + " less nodes");

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
                Helper.nf(ghStorage.getAllEdges().length()), Helper.nf(chGraph.getAllEdges().length() - ghStorage.getAllEdges().length()));
        long start = nanoTime();
        ManualPrepareContractionHierarchies pch = new ManualPrepareContractionHierarchies(
                dir, ghStorage, chGraph, weighting, TraversalMode.EDGE_BASED_2DIR);
        pch.setSeed(seed);
        pch.setContractedNodes(nodesContractedPercentage);

        // load a previously stored contraction order, for analysis purposes only, remove before merge
//        List<Integer> contractionOrder = new ArrayList<>();
//        try {
//            FileInputStream fis = new FileInputStream("contraction-order.dat");
//            ObjectInputStream ois = new ObjectInputStream(fis);
//            contractionOrder = (ArrayList) ois.readObject();
//            ois.close();
//            fis.close();
//        } catch (IOException | ClassNotFoundException e) {
//            e.printStackTrace();
//        }
//        pch.setContractionOrder(contractionOrder);
        
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
    private static void testPerformanceAutomaticNodeOrdering(String[] args) {
        String osmFile = "local/maps/bremen-latest.osm.pbf";
        int periodicUpdates = 20;
        int lazyUpdates = 100;
        int neighborUpdates = 4;
        int contractedNodes = 100;
        int logMessages = 5;
        LegacyWitnessPathFinder.sigmaFactor = 3.0;
        WitnessPathFinder.sigmaFactor = 3.0;
        boolean cleanup = true;
        int landmarks = 0;
        if (args.length == 12) {
            LOGGER.info("Running analysis with parameters {}", Arrays.toString(args));
            osmFile = args[0];
            EdgeBasedNodeContractor.searchType = SearchType.valueOf(args[1]);
            double factor = Double.valueOf(args[2]);
            LegacyWitnessPathFinder.sigmaFactor = factor;
            WitnessPathFinder.sigmaFactor = factor;
            EdgeBasedNodeContractor.edgeQuotientWeight = Float.valueOf(args[3]);
            EdgeBasedNodeContractor.originalEdgeQuotientWeight = Float.valueOf(args[4]);
            EdgeBasedNodeContractor.hierarchyDepthWeight = Float.valueOf(args[5]);
            periodicUpdates = Integer.valueOf(args[6]);
            lazyUpdates = Integer.valueOf(args[7]);
            neighborUpdates = Integer.valueOf(args[8]);
            contractedNodes = Integer.valueOf(args[9]);
            landmarks = Integer.valueOf(args[10]);
            cleanup = Boolean.valueOf(args[11]);
        }
        final GraphHopper graphHopper = new GraphHopperOSM();
        CmdArgs cmdArgs = new CmdArgs();
        cmdArgs.put("datareader.file", osmFile);
        final boolean withTurnCosts = true;
//        final boolean withTurnCosts = false;
        if (withTurnCosts) {
            cmdArgs.put("graph.flag_encoders", "car|turn_costs=true");
            cmdArgs.put("prepare.ch.weightings", "fastest");
            if (landmarks > 0) {
                cmdArgs.put("prepare.lm.weightings", "fastest");
                cmdArgs.put("prepare.lm.landmarks", "32");
            }
        } else {
            cmdArgs.put("graph.flag_encoders", "car");
            cmdArgs.put("prepare.ch.weightings", "no");
        }
        CHAlgoFactoryDecorator chDecorator = graphHopper.getCHFactoryDecorator();
        chDecorator.setDisablingAllowed(true);
        chDecorator.setPreparationPeriodicUpdates(periodicUpdates); // default: 20
        chDecorator.setPreparationLazyUpdates(lazyUpdates);     // default: 10
        chDecorator.setPreparationNeighborUpdates(neighborUpdates); // default: 20
        chDecorator.setPreparationContractedNodes(contractedNodes);// default: 100
        chDecorator.setPreparationLogMessages(logMessages); // default: 20

        LMAlgoFactoryDecorator lmDecorator = graphHopper.getLMFactoryDecorator();
        lmDecorator.setEnabled(true);
        lmDecorator.setDisablingAllowed(true);

        graphHopper.init(cmdArgs);

        // remove previous data
        if (cleanup) {
            graphHopper.clean();
        }

        StopWatch sw = new StopWatch();
        sw.start();
        graphHopper.importOrLoad();
        sw.stop();
        LOGGER.info("Import and preparation took {}s", sw.getMillis() / 1000);

        long seed = 456;
        int iterations = 1_000;
        runCompareTest(DIJKSTRA_BI, graphHopper, withTurnCosts, seed, iterations);
        runCompareTest(ASTAR_BI, graphHopper, withTurnCosts, seed, iterations);

        runPerformanceTest(DIJKSTRA_BI, graphHopper, withTurnCosts, seed, iterations);
        runPerformanceTest(ASTAR_BI, graphHopper, withTurnCosts, seed, iterations);

        if (landmarks > 0) {
            runPerformanceTest("lm", graphHopper, withTurnCosts, seed, iterations);
        }

        graphHopper.close();
    }

    private static void runCompareTest(final String algo, final GraphHopper graphHopper, final boolean withTurnCosts, long seed, final int iterations) {
        LOGGER.info("Running compare test for {}, using seed {}", algo, seed);
        Graph g = graphHopper.getGraphHopperStorage();
        final int numNodes = g.getNodes();
        final NodeAccess nodeAccess = g.getNodeAccess();
        final Random random = new Random(seed);

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
                req.getHints().put(Parameters.Landmark.DISABLE, true);
                req.setAlgorithm(algo);
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
                    LOGGER.warn("there were errors for {}: \n with CH: {} \n without CH: {}", algo, chRoute.getErrors(), nonChRoute.getErrors());
                    return chRoute.getErrors().size();
                }

                if (!chRoute.getBest().getPoints().equals(nonChRoute.getBest().getPoints())) {
                    // todo: this test finds some differences that are most likely due to rounding issues (the weights
                    // are very similar, and the paths have minor differences (with ch the route weight seems to be smaller if different)
                    double chWeight = chRoute.getBest().getRouteWeight();
                    double nonCHWeight = nonChRoute.getBest().getRouteWeight();
                    LOGGER.warn("error for {}: found different points for query from {} to {}, {}", algo,
                            req.getPoints().get(0).toShortString(), req.getPoints().get(1).toShortString(),
                            getWeightDifferenceString(chWeight, nonCHWeight));
                }
                return chRoute.getErrors().size();
            }
        };
        compareTest.setIterations(iterations).start();
    }

    private static void runPerformanceTest(final String algo, final GraphHopper graphHopper, final boolean withTurnCosts, long seed, final int iterations) {
        Graph g = graphHopper.getGraphHopperStorage();
        final int numNodes = g.getNodes();
        final NodeAccess nodeAccess = g.getNodeAccess();
        final Random random = new Random(seed);
        final boolean lm = "lm".equals(algo);

        LOGGER.info("Running performance test for {}, seed = {}", algo, seed);
        final long[] numVisitedNodes = {0};
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
                req.getHints().put(Parameters.CH.DISABLE, lm);
                req.getHints().put(Parameters.Landmark.DISABLE, !lm);
                if (!lm) {
                    req.setAlgorithm(algo);
                } else {
                    req.getHints().put(Parameters.Landmark.ACTIVE_COUNT, "8");
                    req.setWeighting("fastest"); // why do we need this for lm, but not ch ?
                }
                long start = nanoTime();
                GHResponse route = graphHopper.route(req);
                numVisitedNodes[0] += route.getHints().getInt("visited_nodes.sum", 0);
                if (!warmup)
                    queryTime += nanoTime() - start;
                return getRealErrors(route).size();
            }
        };
        performanceTest.setIterations(iterations).start();
        if (performanceTest.getDummySum() > 0.01 * iterations) {
            throw new IllegalStateException("too many errors, probably something is wrong");
        }
        LOGGER.info("Average query time for {}: {}ms", algo, performanceTest.getMean());
        LOGGER.info("Visited nodes for {}: {}", algo, Helper.nf(numVisitedNodes[0]));
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
