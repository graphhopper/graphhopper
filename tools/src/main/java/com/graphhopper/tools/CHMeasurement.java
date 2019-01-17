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
import com.graphhopper.routing.ch.CHAlgoFactoryDecorator;
import com.graphhopper.routing.lm.LMAlgoFactoryDecorator;
import com.graphhopper.storage.Graph;
import com.graphhopper.storage.NodeAccess;
import com.graphhopper.util.*;
import com.graphhopper.util.exceptions.ConnectionNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

import static com.graphhopper.routing.ch.CHParameters.*;
import static com.graphhopper.util.Parameters.Algorithms.ASTAR_BI;
import static com.graphhopper.util.Parameters.Algorithms.DIJKSTRA_BI;
import static java.lang.System.nanoTime;

public class CHMeasurement {
    private static final Logger LOGGER = LoggerFactory.getLogger(CHMeasurement.class);

    public static void main(String[] args) {
        testPerformanceAutomaticNodeOrdering(args);
    }

    /**
     * Parses a given osm file, contracts the graph and runs random routing queries on it. This is useful to test
     * the node contraction heuristics with regards to the performance of the automatic graph contraction (the node
     * contraction order determines how many and which shortcuts will be introduced) and the resulting query speed.
     * The queries are compared with a normal AStar search for comparison and to ensure correctness.
     */
    private static void testPerformanceAutomaticNodeOrdering(String[] args) {
        // example args:
        // map=berlin.pbf stats_file=stats.dat period_updates=0 lazy_updates=100 neighbor_updates=0 contract_nodes=100 log_messages=20 edge_quotient_weight=1.0 orig_edge_quotient_weight=3.0 hierarchy_depth_weight=2.0 sigma_factor=3.0 min_max_settled_edges=100 reset_interval=10000 landmarks=0 cleanup=true turncosts=true threshold=0.1 seed=456 comp_iterations=10 perf_iterations=100 quick=false
        long start = nanoTime();
        CmdArgs cmdArgs = CmdArgs.read(args);
        LOGGER.info("Running analysis with parameters {}", cmdArgs);
        String osmFile = cmdArgs.get("map", "local/maps/unterfranken-latest.osm.pbf");
        cmdArgs.put("datareader.file", osmFile);
        final String statsFile = cmdArgs.get("stats_file", null);
        final int periodicUpdates = cmdArgs.getInt("period_updates", 0);
        final int lazyUpdates = cmdArgs.getInt("lazy_updates", 100);
        final int neighborUpdates = cmdArgs.getInt("neighbor_updates", 0);
        final int contractedNodes = cmdArgs.getInt("contract_nodes", 100);
        final int logMessages = cmdArgs.getInt("log_messages", 20);
        final float edgeQuotientWeight = cmdArgs.getFloat("edge_quotient_weight", 1.0f);
        final float origEdgeQuotientWeight = cmdArgs.getFloat("orig_edge_quotient_weight", 3.0f);
        final float hierarchyDepthWeight = cmdArgs.getFloat("hierarchy_depth_weight", 2.0f);
        final double sigmaFactor = cmdArgs.getFloat("sigma_factor", 3.0f);
        final int minMaxSettledEdges = cmdArgs.getInt("min_max_settled_edges", 100);
        final int resetInterval = cmdArgs.getInt("reset_interval", 10_000);
        final int landmarks = cmdArgs.getInt("landmarks", 0);
        final boolean cleanup = cmdArgs.getBool("cleanup", true);
        final boolean withTurnCosts = cmdArgs.getBool("turncosts", true);
        final double errorThreshold = cmdArgs.getDouble("threshold", 0.1);
        final long seed = cmdArgs.getLong("seed", 456);
        final int compIterations = cmdArgs.getInt("comp_iterations", 100);
        final int perfIterations = cmdArgs.getInt("perf_iterations", 1000);
        final boolean quick = cmdArgs.getBool("quick", false);

        final GraphHopper graphHopper = new GraphHopperOSM();
        if (withTurnCosts) {
            cmdArgs.put("graph.flag_encoders", "car|turn_costs=true");
            cmdArgs.put("prepare.ch.weightings", "fastest");
            cmdArgs.put("prepare.ch.edge_based", "edge_or_node");
            if (landmarks > 0) {
                cmdArgs.put("prepare.lm.weightings", "fastest");
                cmdArgs.put("prepare.lm.landmarks", landmarks);
            }
        } else {
            cmdArgs.put("graph.flag_encoders", "car");
            cmdArgs.put("prepare.ch.weightings", "no");
        }
        CHAlgoFactoryDecorator chDecorator = graphHopper.getCHFactoryDecorator();
        chDecorator.setDisablingAllowed(true);
        cmdArgs.put(PERIODIC_UPDATES, periodicUpdates);
        cmdArgs.put(LAST_LAZY_NODES_UPDATES, lazyUpdates);
        cmdArgs.put(NEIGHBOR_UPDATES, neighborUpdates);
        cmdArgs.put(CONTRACTED_NODES, contractedNodes);
        cmdArgs.put(LOG_MESSAGES, logMessages);
        cmdArgs.put(EDGE_QUOTIENT_WEIGHT, edgeQuotientWeight);
        cmdArgs.put(ORIGINAL_EDGE_QUOTIENT_WEIGHT, origEdgeQuotientWeight);
        cmdArgs.put(HIERARCHY_DEPTH_WEIGHT, hierarchyDepthWeight);
        cmdArgs.put(SIGMA_FACTOR, sigmaFactor);
        cmdArgs.put(MIN_MAX_SETTLED_EDGES, minMaxSettledEdges);
        cmdArgs.put(SETTLED_EDGES_RESET_INTERVAL, resetInterval);

        LMAlgoFactoryDecorator lmDecorator = graphHopper.getLMFactoryDecorator();
        lmDecorator.setEnabled(landmarks > 0);
        lmDecorator.setDisablingAllowed(true);

        LOGGER.info("Initializing graph hopper with args: {}", cmdArgs);
        graphHopper.init(cmdArgs);

        if (cleanup) {
            graphHopper.clean();
        }

        PMap results = new PMap(cmdArgs);

        StopWatch sw = new StopWatch();
        sw.start();
        graphHopper.importOrLoad();
        sw.stop();
        results.put("_prepare_time", sw.getSeconds());
        LOGGER.info("Import and preparation took {}s", sw.getMillis() / 1000);

        if (!quick) {
            runCompareTest(DIJKSTRA_BI, graphHopper, withTurnCosts, seed, compIterations, errorThreshold, results);
            runCompareTest(ASTAR_BI, graphHopper, withTurnCosts, seed, compIterations, errorThreshold, results);
        }

        if (!quick) {
            runPerformanceTest(DIJKSTRA_BI, graphHopper, withTurnCosts, seed, perfIterations, results);
        }

        runPerformanceTest(ASTAR_BI, graphHopper, withTurnCosts, seed, perfIterations, results);

        if (!quick && landmarks > 0) {
            runPerformanceTest("lm", graphHopper, withTurnCosts, seed, perfIterations, results);
        }

        graphHopper.close();

        Map<String, String> resultMap = results.toMap();
        TreeSet<String> sortedKeys = new TreeSet<>(resultMap.keySet());
        for (String key : sortedKeys) {
            LOGGER.info(key + "=" + resultMap.get(key));
        }

        if (statsFile != null) {
            File f = new File(statsFile);
            boolean writeHeader = !f.exists();
            try (OutputStream os = new FileOutputStream(f, true);
                 Writer writer = new OutputStreamWriter(os, StandardCharsets.UTF_8)) {
                if (writeHeader)
                    writer.write(getHeader(sortedKeys));
                writer.write(getStatLine(sortedKeys, resultMap));
            } catch (IOException e) {
                LOGGER.error("Could not write summary to file '{}'", statsFile, e);
            }
        }

        // output to be used by external caller
        StringBuilder sb = new StringBuilder();
        for (String key : sortedKeys) {
            sb.append(key).append(":").append(resultMap.get(key)).append(";");
        }
        sb.deleteCharAt(sb.lastIndexOf(";"));
        System.out.println(sb.toString());

        LOGGER.info("Total time: {}s", fmt((nanoTime() - start) * 1.e-9));
    }

    private static String getHeader(TreeSet<String> keys) {
        StringBuilder sb = new StringBuilder("#");
        for (String key : keys) {
            sb.append(key).append(";");
        }
        sb.append("\n");
        return sb.toString();
    }

    private static String getStatLine(TreeSet<String> keys, Map<String, String> results) {
        StringBuilder sb = new StringBuilder();
        for (String key : keys) {
            sb.append(results.get(key)).append(";");
        }
        sb.append("\n");
        return sb.toString();
    }

    private static void runCompareTest(final String algo, final GraphHopper graphHopper, final boolean withTurnCosts,
                                       long seed, final int iterations, final double threshold, final PMap results) {
        LOGGER.info("Running compare test for {}, using seed {}", algo, seed);
        Graph g = graphHopper.getGraphHopperStorage();
        final int numNodes = g.getNodes();
        final NodeAccess nodeAccess = g.getNodeAccess();
        final Random random = new Random(seed);

        MiniPerfTest compareTest = new MiniPerfTest() {
            long chTime = 0;
            long noChTime = 0;
            long chErrors = 0;
            long noChErrors = 0;
            long chDeviations = 0;

            @Override
            public int doCalc(boolean warmup, int run) {
                if (!warmup && run % 100 == 0) {
                    LOGGER.info("Finished {} of {} runs. {}", run, iterations,
                            run > 0 ? String.format(Locale.ROOT, " CH: %6.2fms, without CH: %6.2fms",
                                    chTime * 1.e-6 / run, noChTime * 1.e-6 / run) : "");
                }
                if (run == iterations - 1) {
                    String avgChTime = fmt(chTime * 1.e-6 / run);
                    String avgNoChTime = fmt(noChTime * 1.e-6 / run);
                    LOGGER.info("Finished all ({}) runs, CH: {}ms, without CH: {}ms", iterations, avgChTime, avgNoChTime);
                    results.put("_" + algo + ".time_comp_ch", avgChTime);
                    results.put("_" + algo + ".time_comp", avgNoChTime);
                    results.put("_" + algo + ".errors_ch", chErrors);
                    results.put("_" + algo + ".errors", noChErrors);
                    results.put("_" + algo + ".deviations", chDeviations);
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
                    if (!chRoute.getErrors().isEmpty()) {
                        chErrors++;
                    }
                    if (!nonChRoute.getErrors().isEmpty()) {
                        noChErrors++;
                    }
                    return chRoute.getErrors().size();
                }

                double chWeight = chRoute.getBest().getRouteWeight();
                double nonCHWeight = nonChRoute.getBest().getRouteWeight();
                if (Math.abs(chWeight - nonCHWeight) > threshold) {
                    LOGGER.warn("error for {}: difference between best paths with and without CH is above threshold ({}), {}",
                            algo, threshold, getWeightDifferenceString(chWeight, nonCHWeight));
                    chDeviations++;
                }
                if (!chRoute.getBest().getPoints().equals(nonChRoute.getBest().getPoints())) {
                    // small negative deviations are due to weight truncation when shortcuts are stored
                    LOGGER.warn("error for {}: found different points for query from {} to {}, {}", algo,
                            req.getPoints().get(0).toShortString(), req.getPoints().get(1).toShortString(),
                            getWeightDifferenceString(chWeight, nonCHWeight));
                }
                return chRoute.getErrors().size();
            }
        };
        compareTest.setIterations(iterations).start();
    }

    private static void runPerformanceTest(final String algo, final GraphHopper graphHopper, final boolean withTurnCosts,
                                           long seed, final int iterations, final PMap results) {
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
                            run > 0 ? String.format(Locale.ROOT, " Time: %6.2fms", queryTime * 1.e-6 / run) : "");
                }
                if (run == iterations - 1) {
                    String avg = fmt(queryTime * 1.e-6 / run);
                    LOGGER.info("Finished all ({}) runs, avg time: {}ms", iterations, avg);
                    results.put("_" + algo + ".time_ch", avg);
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

    private static String getWeightDifferenceString(double chWeight, double noChWeight) {
        return String.format(Locale.ROOT, "route weight: %.6f (CH) vs. %.6f (no CH) (diff = %.6f)",
                chWeight, noChWeight, (chWeight - noChWeight));
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

    private static String fmt(double number) {
        return String.format(Locale.ROOT, "%.2f", number);
    }

}
