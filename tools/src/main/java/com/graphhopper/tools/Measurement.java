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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.graphhopper.*;
import com.graphhopper.coll.GHBitSet;
import com.graphhopper.coll.GHBitSetImpl;
import com.graphhopper.config.CHProfile;
import com.graphhopper.config.LMProfile;
import com.graphhopper.config.Profile;
import com.graphhopper.jackson.Jackson;
import com.graphhopper.reader.DataReader;
import com.graphhopper.reader.osm.GraphHopperOSM;
import com.graphhopper.routing.lm.PrepareLandmarks;
import com.graphhopper.routing.util.*;
import com.graphhopper.routing.weighting.Weighting;
import com.graphhopper.routing.weighting.custom.CustomProfile;
import com.graphhopper.routing.weighting.custom.CustomWeighting;
import com.graphhopper.storage.*;
import com.graphhopper.util.*;
import com.graphhopper.util.Parameters.Algorithms;
import com.graphhopper.util.Parameters.CH;
import com.graphhopper.util.Parameters.Landmark;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static com.graphhopper.util.Helper.*;
import static com.graphhopper.util.Parameters.Algorithms.ALT_ROUTE;
import static com.graphhopper.util.Parameters.Routing.BLOCK_AREA;

/**
 * Used to run performance benchmarks for routing and other functionalities of GraphHopper
 *
 * @author Peter Karich
 * @author easbar
 */
public class Measurement {
    private static final Logger logger = LoggerFactory.getLogger(Measurement.class);
    private final Map<String, Object> properties = new TreeMap<>();
    private long seed;
    private int maxNode;
    private String vehicle;

    public static void main(String[] strs) throws IOException {
        PMap args = PMap.read(strs);
        int repeats = args.getInt("measurement.repeats", 10);
        for (int i = 0; i < repeats; ++i)
            new Measurement().start(args);
    }

    // creates properties file in the format key=value
    // Every value is one y-value in a separate diagram with an identical x-value for every Measurement.start call
    void start(PMap args) throws IOException {
        final String graphLocation = args.getString("graph.location", "lauf-gh");
        args.putObject("datareader.file", args.getString("datareader.file", "core/files/lauf-200115.osm.pbf"));
        args.putObject("datareader.date_range_parser_day", args.getString("datareader.date_range_parser_day", "2019-11-01"));
        args.putObject("graph.flag_encoders", args.getString("graph.flag_encoders", "car"));
        args.putObject("graph.encoded_values", args.getString("graph.encoded_values", "max_width,max_height,toll,hazmat"));
        final int countNewEdgeTests = args.getInt("measurement.count_new_edge_tests", 0);
        put("measurement.count_new_edge_tests", countNewEdgeTests);
        final boolean useJson = args.getBool("measurement.json", false);
        boolean cleanGraph = args.getBool("measurement.clean", false);
        String summaryLocation = args.getString("measurement.summaryfile", "");
        final String timeStamp = new SimpleDateFormat("yyyy-MM-dd_HH:mm:ss").format(new Date());
        put("measurement.timestamp", timeStamp);
        String propFolder = args.getString("measurement.folder", "");
        if (!propFolder.isEmpty()) {
            Files.createDirectories(Paths.get(propFolder));
        }
        String propFilename = args.getString("measurement.filename", "results");
        if (isEmpty(propFilename)) {
            if (useJson) {
                // if we start from IDE or otherwise jar was not built using maven the git commit id will be unknown
                String id = Constants.GIT_INFO != null ? Constants.GIT_INFO.getCommitHash().substring(0, 8) : "unknown";
                propFilename = "measurement_" + id + "_" + timeStamp + ".json";
            } else {
                propFilename = "measurement_" + timeStamp + ".properties";
            }
        }
        final String propLocation = Paths.get(propFolder).resolve(propFilename).toString();
        seed = args.getLong("measurement.seed", 123);
        put("measurement.gitinfo", args.getString("measurement.gitinfo", ""));
        int count = args.getInt("measurement.count", 5000);
        put("measurement.name", args.getString("measurement.name", "no_name"));
        put("measurement.map", args.getString("datareader.file", "unknown"));
        final boolean useMeasurementTimeAsRefTime = args.getBool("measurement.use_measurement_time_as_ref_time", false);
        if (useMeasurementTimeAsRefTime && !useJson) {
            throw new IllegalArgumentException("Using measurement time as reference time only works with json files");
        }

        GraphHopper hopper = new GraphHopperOSM() {
            @Override
            protected void prepareCH(boolean closeEarly) {
                StopWatch sw = new StopWatch().start();
                super.prepareCH(closeEarly);
                // note that we measure the total time of all (possibly edge&node) CH preparations
                put(Parameters.CH.PREPARE + "time", sw.stop().getMillis());
                int edges = getGraphHopperStorage().getEdges();
                if (!getCHPreparationHandler().getNodeBasedCHConfigs().isEmpty()) {
                    CHConfig chConfig = getCHPreparationHandler().getNodeBasedCHConfigs().get(0);
                    int edgesAndShortcuts = getGraphHopperStorage().getCHGraph(chConfig.getName()).getEdges();
                    put(Parameters.CH.PREPARE + "node.shortcuts", edgesAndShortcuts - edges);
                    put(Parameters.CH.PREPARE + "node.time", getCHPreparationHandler().getPreparation(chConfig).getTotalPrepareTime());
                }
                if (!getCHPreparationHandler().getEdgeBasedCHConfigs().isEmpty()) {
                    CHConfig chConfig = getCHPreparationHandler().getEdgeBasedCHConfigs().get(0);
                    int edgesAndShortcuts = getGraphHopperStorage().getCHGraph(chConfig.getName()).getEdges();
                    put(Parameters.CH.PREPARE + "edge.shortcuts", edgesAndShortcuts - edges);
                    put(Parameters.CH.PREPARE + "edge.time", getCHPreparationHandler().getPreparation(chConfig).getTotalPrepareTime());
                }
            }

            @Override
            protected void loadOrPrepareLM(boolean closeEarly) {
                super.loadOrPrepareLM(closeEarly);
                for (PrepareLandmarks plm : getLMPreparationHandler().getPreparations()) {
                    put(Landmark.PREPARE + "time", plm.getTotalPrepareTime());
                }
            }

            @Override
            protected void cleanUp() {
                StopWatch sw = new StopWatch().start();
                super.cleanUp();
                put("graph.subnetwork_removal_time_ms", sw.stop().getMillis());
            }

            @Override
            protected DataReader importData() throws IOException {
                StopWatch sw = new StopWatch().start();
                DataReader dr = super.importData();
                sw.stop();
                put("graph.import_time", sw.getSeconds());
                put("graph.import_time_ms", sw.getMillis());
                return dr;
            }
        };

        hopper.init(createConfigFromArgs(args)).
                // use server to allow path simplification
                        forServer();
        if (cleanGraph) {
            hopper.clean();
        }

        hopper.getRouterConfig().setCHDisablingAllowed(true);
        hopper.getRouterConfig().setLMDisablingAllowed(true);
        hopper.importOrLoad();

        GraphHopperStorage g = hopper.getGraphHopperStorage();
        EncodingManager encodingManager = hopper.getEncodingManager();
        if (encodingManager.fetchEdgeEncoders().size() != 1) {
            throw new IllegalArgumentException("There has to be exactly one encoder for each measurement");
        }
        FlagEncoder encoder = encodingManager.fetchEdgeEncoders().get(0);
        final String vehicleStr = encoder.toString();

        StopWatch sw = new StopWatch().start();
        try {
            maxNode = g.getNodes();
            GHBitSet allowedEdges = printGraphDetails(g, vehicleStr);

            if (hopper.getCHPreparationHandler().isEnabled()) {
                boolean isCH = true;
                boolean isLM = false;
                System.gc();
                if (!hopper.getCHPreparationHandler().getNodeBasedCHConfigs().isEmpty()) {
                    CHConfig chConfig = hopper.getCHPreparationHandler().getNodeBasedCHConfigs().get(0);
                    CHGraph lg = g.getCHGraph(chConfig.getName());
                    fillAllowedEdges(lg.getAllEdges(), allowedEdges);
                    printMiscUnitPerfTests(lg, isCH, chConfig.getWeighting(), encoder, count * 100, countNewEdgeTests, allowedEdges);
                    gcAndWait();
                    printTimeOfRouteQuery(hopper, new QuerySettings("routingCH", count, isCH, isLM).
                            withInstructions().sod());
                }
            }
        } catch (Exception ex) {
            logger.error("Problem while measuring " + graphLocation, ex);
            put("error", ex.toString());
        } finally {
            put("gh.gitinfo", Constants.GIT_INFO != null ? Constants.GIT_INFO.toString() : "unknown");
            put("measurement.count", count);
            put("measurement.seed", seed);
            put("measurement.time", sw.stop().getMillis());
            System.gc();
            put("measurement.totalMB", getTotalMB());
            put("measurement.usedMB", getUsedMB());

            if (!isEmpty(summaryLocation)) {
                writeSummary(summaryLocation, propLocation);
            }
//            if (useJson) {
//                storeJson(propLocation, useMeasurementTimeAsRefTime);
//            } else {
//                storeProperties(propLocation);
//            }
        }
    }

    private GraphHopperConfig createConfigFromArgs(PMap args) {
        GraphHopperConfig ghConfig = new GraphHopperConfig(args);
        String encodingManagerString = args.getString("graph.flag_encoders", "car");
        List<FlagEncoder> tmpEncoders = EncodingManager.create(encodingManagerString).fetchEdgeEncoders();
        if (tmpEncoders.size() != 1) {
            logger.warn("You configured multiple encoders, only the first one is used for the measurements");
        }
        vehicle = tmpEncoders.get(0).toString();
        boolean turnCosts = tmpEncoders.get(0).supportsTurnCosts();
        String weighting = args.getString("measurement.weighting", "custom");
        boolean useCHEdge = args.getBool("measurement.ch.edge", false);
        boolean useCHNode = args.getBool("measurement.ch.node", true);
        boolean useLM = args.getBool("measurement.lm", false);
        String customModelFile = args.getString("measurement.custom_model_file", "benchmark/very_custom.yml");
        List<Profile> profiles = new ArrayList<>();
        if (!customModelFile.isEmpty()) {
            if (!weighting.equals(CustomWeighting.NAME))
                throw new IllegalArgumentException("To make use of a custom model you need to set measurement.weighting to 'custom'");
            // use custom profile(s) as specified in the given custom model file
            CustomModel customModel = loadCustomModel(customModelFile);
            profiles.add(new CustomProfile("profile_no_tc").setCustomModel(customModel).setVehicle(vehicle).setTurnCosts(false));
            if (turnCosts)
                profiles.add(new CustomProfile("profile_tc").setCustomModel(customModel).setVehicle(vehicle).setTurnCosts(true));
        } else {
            // use standard profiles
            profiles.add(new Profile("profile_no_tc").setVehicle(vehicle).setWeighting(weighting).setTurnCosts(false));
            if (turnCosts)
                profiles.add(new Profile("profile_tc").setVehicle(vehicle).setWeighting(weighting).setTurnCosts(true));
        }
        ghConfig.setProfiles(profiles);

        List<CHProfile> chProfiles = new ArrayList<>();
        if (useCHNode)
            chProfiles.add(new CHProfile("profile_no_tc"));
        if (useCHEdge)
            chProfiles.add(new CHProfile("profile_tc"));
        ghConfig.setCHProfiles(chProfiles);
        List<LMProfile> lmProfiles = new ArrayList<>();
        if (useLM) {
            lmProfiles.add(new LMProfile("profile_no_tc"));
            if (turnCosts)
                // no need for a second LM preparation, we can do cross queries here
                lmProfiles.add(new LMProfile("profile_tc").setPreparationProfile("profile_no_tc"));
        }
        ghConfig.setLMProfiles(lmProfiles);
        return ghConfig;
    }

    private static class QuerySettings {
        private final String prefix;
        private final int count;
        final boolean ch, lm;
        int activeLandmarks = -1;
        boolean withInstructions, withPointHints, sod, edgeBased, simplify, alternative;
        String blockArea;

        QuerySettings(String prefix, int count, boolean isCH, boolean isLM) {
            this.prefix = prefix;
            this.count = count;
            this.ch = isCH;
            this.lm = isLM;
        }

        QuerySettings withInstructions() {
            this.withInstructions = true;
            return this;
        }

        QuerySettings withPointHints() {
            this.withPointHints = true;
            return this;
        }

        QuerySettings sod() {
            sod = true;
            return this;
        }

        QuerySettings activeLandmarks(int alm) {
            this.activeLandmarks = alm;
            return this;
        }

        QuerySettings edgeBased() {
            this.edgeBased = true;
            return this;
        }

        QuerySettings simplify() {
            this.simplify = true;
            return this;
        }

        QuerySettings alternative() {
            alternative = true;
            return this;
        }

        QuerySettings blockArea(String str) {
            blockArea = str;
            return this;
        }
    }

    void fillAllowedEdges(AllEdgesIterator iter, GHBitSet bs) {
        bs.clear();
        while (iter.next()) {
            bs.add(iter.getEdge());
        }
    }

    private GHBitSet printGraphDetails(GraphHopperStorage g, String vehicleStr) {
        // graph size (edge, node and storage size)
        put("graph.nodes", g.getNodes());
        put("graph.edges", g.getAllEdges().length());
        put("graph.size_in_MB", g.getCapacity() / MB);
        put("graph.encoder", vehicleStr);

        AllEdgesIterator iter = g.getAllEdges();
        final int maxEdgesId = g.getAllEdges().length();
        final GHBitSet allowedEdges = new GHBitSetImpl(maxEdgesId);
        fillAllowedEdges(iter, allowedEdges);
        put("graph.valid_edges", allowedEdges.getCardinality());
        return allowedEdges;
    }

    private void printMiscUnitPerfTests(final Graph graph, boolean isCH, Weighting chWeighting, final FlagEncoder encoder,
                                        int count, int countEdgeTests, final GHBitSet allowedEdges) {
        final Random rand = new Random(seed);
        String description = "";
        if (isCH) {
            description = "CH";
            CHGraph lg = (CHGraph) graph;
            final CHEdgeExplorer chExplorer = lg.createEdgeExplorer(new LevelEdgeFilter(lg));
            MiniPerfTest miniPerf = new MiniPerfTest().setIterations(count).start((warmup, run) -> {
                int nodeId = rand.nextInt(maxNode);
                return GHUtility.count(chExplorer.setBaseNode(nodeId));
            });
            print("unit_testsCH.level_edge_state_next", miniPerf);

            final CHEdgeExplorer chExplorer2 = lg.createEdgeExplorer();
            miniPerf = new MiniPerfTest().setIterations(count).start((warmup, run) -> {
                int nodeId = rand.nextInt(maxNode);
                CHEdgeIterator iter = chExplorer2.setBaseNode(nodeId);
                while (iter.next()) {
                    if (iter.isShortcut())
                        nodeId += (int) iter.getWeight();
                }
                return nodeId;
            });
            print("unit_testsCH.get_weight", miniPerf);

            if (countEdgeTests > 0) {
                // ###
                // ### WHY DOES THIS CODE CAUSE THE (LATER) MEASUREMENTS TO TURN OUT SLOWER?
                // ### (routingCH.mean increases even though it should be unrelated)
                // ###
                runExtraCode(chWeighting, countEdgeTests, rand, lg);
            }
        }

        EdgeFilter outFilter = DefaultEdgeFilter.outEdges(encoder);
        final EdgeExplorer outExplorer = graph.createEdgeExplorer(outFilter);
        MiniPerfTest miniPerf = new MiniPerfTest().setIterations(count).start((warmup, run) -> {
            int nodeId = rand.nextInt(maxNode);
            return GHUtility.count(outExplorer.setBaseNode(nodeId));
        });
        print("unit_tests" + description + ".out_edge_state_next", miniPerf);

        final EdgeExplorer allExplorer = graph.createEdgeExplorer();
        miniPerf = new MiniPerfTest().setIterations(count).start(new MiniPerfTest.MeasurementUnit() {
            @Override
            public int doCalc(boolean warmup, int run) {
                int nodeId = rand.nextInt(maxNode);
                return GHUtility.count(allExplorer.setBaseNode(nodeId));
            }
        });
        print("unit_tests" + description + ".all_edge_state_next", miniPerf);

        final int maxEdgesId = graph.getAllEdges().length();
        miniPerf = new MiniPerfTest().setIterations(count).start((warmup, run) -> {
            while (true) {
                int edgeId = rand.nextInt(maxEdgesId);
                if (allowedEdges.contains(edgeId))
                    return graph.getEdgeIteratorState(edgeId, Integer.MIN_VALUE).getEdge();
            }
        });
        print("unit_tests" + description + ".get_edge_state", miniPerf);
    }

    /**
     * Runs some loop code and writes a few values into the properties map, but otherwise is not supposed to change
     * any state. Still apparently this slows down code running later {@link #printTimeOfRouteQuery})
     */
    private void runExtraCode(Weighting chWeighting, int countEdgeTests, Random rand, CHGraph lg) {
        MiniPerfTest miniPerf;
        RoutingCHGraphImpl routingCHGraph = new RoutingCHGraphImpl(lg, chWeighting);
        final RoutingCHEdgeExplorer chOutEdgeExplorer = routingCHGraph.createOutEdgeExplorer();
        miniPerf = new MiniPerfTest().setIterations(countEdgeTests).start((warmup, run) -> {
            int nodeId = rand.nextInt(maxNode);
            RoutingCHEdgeIterator iter = chOutEdgeExplorer.setBaseNode(nodeId);
            while (iter.next()) {
                nodeId += iter.getAdjNode();
            }
            return nodeId;
        });
        print("unit_testsCH.out_edge_next", miniPerf);

        miniPerf = new MiniPerfTest().setIterations(countEdgeTests).start((warmup, run) -> {
            int nodeId = rand.nextInt(maxNode);
            RoutingCHEdgeIterator iter = chOutEdgeExplorer.setBaseNode(nodeId);
            while (iter.next()) {
                nodeId += iter.getWeight(false);
            }
            return nodeId;
        });
        print("unit_testsCH.out_edge_get_weight", miniPerf);

        final RoutingCHEdgeExplorer chOrigEdgeExplorer = routingCHGraph.createOriginalOutEdgeExplorer();
        miniPerf = new MiniPerfTest().setIterations(countEdgeTests).start((warmup, run) -> {
            int nodeId = rand.nextInt(maxNode);
            RoutingCHEdgeIterator iter = chOrigEdgeExplorer.setBaseNode(nodeId);
            while (iter.next()) {
                nodeId += iter.getAdjNode();
            }
            return nodeId;
        });
        print("unit_testsCH.out_orig_edge_next", miniPerf);
    }

    private void printTimeOfRouteQuery(final GraphHopper hopper, final QuerySettings querySettings) {
        final Graph g = hopper.getGraphHopperStorage();
        final AtomicLong maxDistance = new AtomicLong(0);
        final AtomicLong minDistance = new AtomicLong(Long.MAX_VALUE);
        final AtomicLong distSum = new AtomicLong(0);
        final AtomicLong airDistSum = new AtomicLong(0);
        final AtomicLong altCount = new AtomicLong(0);
        final AtomicInteger failedCount = new AtomicInteger(0);
        final DistanceCalc distCalc = new DistanceCalcEarth();

        final EdgeFilter edgeFilter = DefaultEdgeFilter.allEdges(hopper.getEncodingManager().getEncoder(vehicle));
        final EdgeExplorer edgeExplorer = g.createEdgeExplorer(edgeFilter);
        final AtomicLong visitedNodesSum = new AtomicLong(0);
        final AtomicLong maxVisitedNodes = new AtomicLong(0);
        final Random rand = new Random(seed);
        final NodeAccess na = g.getNodeAccess();

        MiniPerfTest miniPerf = new MiniPerfTest().setIterations(querySettings.count).start((warmup, run) -> {
            int from = -1, to = -1;
            double fromLat = 0, fromLon = 0, toLat = 0, toLon = 0;
            GHRequest req = null;
            for (int i = 0; i < 5; i++) {
                from = rand.nextInt(maxNode);
                to = rand.nextInt(maxNode);
                fromLat = na.getLatitude(from);
                fromLon = na.getLongitude(from);
                toLat = na.getLatitude(to);
                toLon = na.getLongitude(to);
                req = new GHRequest(fromLat, fromLon, toLat, toLon);
                req.setProfile(querySettings.edgeBased ? "profile_tc" : "profile_no_tc");
                if (querySettings.blockArea == null)
                    break;

                try {
                    req.getHints().putObject(BLOCK_AREA, querySettings.blockArea);
                    GraphEdgeIdFinder.createBlockArea(hopper.getGraphHopperStorage(), hopper.getLocationIndex(), req.getPoints(), req.getHints(), edgeFilter);
                    break;
                } catch (IllegalArgumentException ex) {
                    if (i >= 4)
                        throw new RuntimeException("Give up after 5 trials. Cannot find points outside of the block_area "
                                + querySettings.blockArea + " - too big block_area or map too small? Request:" + req);
                }
            }

            req.getHints().
                    putObject(CH.DISABLE, !querySettings.ch).
                    putObject("stall_on_demand", querySettings.sod).
                    putObject(Landmark.DISABLE, !querySettings.lm).
                    putObject(Landmark.ACTIVE_COUNT, querySettings.activeLandmarks).
                    putObject("instructions", querySettings.withInstructions);

            if (querySettings.alternative)
                req.setAlgorithm(ALT_ROUTE);

            if (querySettings.withInstructions)
                req.setPathDetails(Arrays.asList(Parameters.Details.AVERAGE_SPEED));

            if (querySettings.simplify) {
                req.setPathDetails(Arrays.asList(Parameters.Details.AVERAGE_SPEED, Parameters.Details.EDGE_ID, Parameters.Details.STREET_NAME));
            } else {
                // disable path simplification by setting the distance to zero
                req.getHints().putObject(Parameters.Routing.WAY_POINT_MAX_DISTANCE, 0);
            }

            if (querySettings.withPointHints) {
                EdgeIterator iter = edgeExplorer.setBaseNode(from);
                if (!iter.next())
                    throw new IllegalArgumentException("wrong 'from' when adding point hint");
                EdgeIterator iter2 = edgeExplorer.setBaseNode(to);
                if (!iter2.next())
                    throw new IllegalArgumentException("wrong 'to' when adding point hint");
                req.setPointHints(Arrays.asList(iter.getName(), iter2.getName()));
            }

            // put(algo + ".approximation", "BeelineSimplification").
            // put(algo + ".epsilon", 2);

            GHResponse rsp;
            try {
                rsp = hopper.route(req);
            } catch (Exception ex) {
                // 'not found' can happen if import creates more than one subnetwork
                throw new RuntimeException("Error while calculating route! "
                        + "nodes:" + from + " -> " + to + ", request:" + req, ex);
            }

            if (rsp.hasErrors()) {
                if (!warmup)
                    failedCount.incrementAndGet();

                if (rsp.getErrors().get(0).getMessage() == null)
                    rsp.getErrors().get(0).printStackTrace();
                else if (!toLowerCase(rsp.getErrors().get(0).getMessage()).contains("not found"))
                    logger.error("errors should NOT happen in Measurement! " + req + " => " + rsp.getErrors());

                return 0;
            }

            ResponsePath responsePath = rsp.getBest();
            if (!warmup) {
                long visitedNodes = rsp.getHints().getLong("visited_nodes.sum", 0);
                visitedNodesSum.addAndGet(visitedNodes);
                if (visitedNodes > maxVisitedNodes.get()) {
                    maxVisitedNodes.set(visitedNodes);
                }

                long dist = (long) responsePath.getDistance();
                distSum.addAndGet(dist);

                airDistSum.addAndGet((long) distCalc.calcDist(fromLat, fromLon, toLat, toLon));

                if (dist > maxDistance.get())
                    maxDistance.set(dist);

                if (dist < minDistance.get())
                    minDistance.set(dist);

                if (querySettings.alternative)
                    altCount.addAndGet(rsp.getAll().size());
            }

            return responsePath.getPoints().getSize();
        });

        int count = querySettings.count - failedCount.get();

        // if using non-bidirectional algorithm make sure you exclude CH routing
        String algoStr = (querySettings.ch && !querySettings.edgeBased) ? Algorithms.DIJKSTRA_BI : Algorithms.ASTAR_BI;
        if (querySettings.ch && !querySettings.sod) {
            algoStr += "_no_sod";
        }
        String prefix = querySettings.prefix;
        put(prefix + ".guessed_algorithm", algoStr);
        put(prefix + ".failed_count", failedCount.get());
        put(prefix + ".distance_min", minDistance.get());
        put(prefix + ".distance_mean", (float) distSum.get() / count);
        put(prefix + ".air_distance_mean", (float) airDistSum.get() / count);
        put(prefix + ".distance_max", maxDistance.get());
        put(prefix + ".visited_nodes_mean", (float) visitedNodesSum.get() / count);
        put(prefix + ".visited_nodes_max", (float) maxVisitedNodes.get());
        put(prefix + ".alternative_rate", (float) altCount.get() / count);
        print(prefix, miniPerf);
    }

    void print(String prefix, MiniPerfTest perf) {
        logger.info(prefix + ": " + perf.getReport());
        put(prefix + ".sum", perf.getSum());
        put(prefix + ".min", perf.getMin());
        put(prefix + ".mean", perf.getMean());
        put(prefix + ".max", perf.getMax());
    }

    void put(String key, Object val) {
        properties.put(key, val);
    }

    private void storeJson(String jsonLocation, boolean useMeasurementTimeAsRefTime) {
        logger.info("storing measurement json in " + jsonLocation);
        Map<String, String> gitInfoMap = new HashMap<>();
        // add git info if available
        if (Constants.GIT_INFO != null) {
            properties.remove("gh.gitinfo");
            gitInfoMap.put("commitHash", Constants.GIT_INFO.getCommitHash());
            gitInfoMap.put("commitMessage", Constants.GIT_INFO.getCommitMessage());
            gitInfoMap.put("commitTime", Constants.GIT_INFO.getCommitTime());
            gitInfoMap.put("branch", Constants.GIT_INFO.getBranch());
            gitInfoMap.put("dirty", String.valueOf(Constants.GIT_INFO.isDirty()));
        }
        Map<String, Object> result = new HashMap<>();
        // add measurement time, use same format as git commit time
        String measurementTime = new SimpleDateFormat("yyy-MM-dd'T'HH:mm:ssZ").format(new Date());
        result.put("measurementTime", measurementTime);
        // set ref time, this is either the git commit time or the measurement time
        if (Constants.GIT_INFO != null && !useMeasurementTimeAsRefTime) {
            result.put("refTime", Constants.GIT_INFO.getCommitTime());
        } else {
            result.put("refTime", measurementTime);
        }
        result.put("periodicBuild", useMeasurementTimeAsRefTime);
        result.put("gitinfo", gitInfoMap);
        result.put("metrics", properties);
        try {
            File file = new File(jsonLocation);
            new ObjectMapper()
                    .writerWithDefaultPrettyPrinter()
                    .writeValue(file, result);
        } catch (IOException e) {
            logger.error("Problem while storing json in: " + jsonLocation, e);
        }
    }

    private CustomModel loadCustomModel(String customModelLocation) {
        ObjectMapper yamlOM = Jackson.initObjectMapper(new ObjectMapper(new YAMLFactory()));
        try {
            return yamlOM.readValue(new File(customModelLocation), CustomModel.class);
        } catch (Exception e) {
            throw new RuntimeException("Cannot load custom_model from " + customModelLocation, e);
        }
    }

    private void storeProperties(String propLocation) {
        logger.info("storing measurement properties in " + propLocation);
        try (FileWriter fileWriter = new FileWriter(propLocation)) {
            String comment = "measurement finish, " + new Date().toString() + ", " + Constants.BUILD_DATE;
            fileWriter.append("#" + comment + "\n");
            for (Entry<String, Object> e : properties.entrySet()) {
                fileWriter.append(e.getKey());
                fileWriter.append("=");
                fileWriter.append(e.getValue().toString());
                fileWriter.append("\n");
            }
            fileWriter.flush();
        } catch (IOException e) {
            logger.error("Problem while storing properties in: " + propLocation, e);
        }
    }

    /**
     * Writes a selection of measurement results to a single line in
     * a file. Each run of the measurement class will append a new line.
     */
    private void writeSummary(String summaryLocation, String propLocation) {
        logger.info("writing summary to " + summaryLocation);
        // choose properties that should be in summary here
        String[] properties = {
                "routingCH.mean",
                "routingCH.distance_mean",
                "measurement.count_new_edge_tests"
        };
        File f = new File(summaryLocation);
        boolean writeHeader = !f.exists();
        try (FileWriter writer = new FileWriter(f, true)) {
            if (writeHeader)
                writer.write(getSummaryHeader(properties));
            writer.write(getSummaryLogLine(properties, propLocation));
        } catch (IOException e) {
            logger.error("Could not write summary to file '{}'", summaryLocation, e);
        }
    }

    private String getSummaryHeader(String[] properties) {
        StringBuilder sb = new StringBuilder("#");
        for (String p : properties) {
            String columnName = String.format("%" + getSummaryColumnWidth(p) + "s, ", p);
            sb.append(columnName);
        }
//        sb.append("propertyFile");
        sb.append('\n');
        return sb.toString();
    }

    private String getSummaryLogLine(String[] properties, String propLocation) {
        StringBuilder sb = new StringBuilder(" ");
        for (String p : properties) {
            sb.append(getFormattedProperty(p));
        }
//        sb.append(propLocation);
        sb.append('\n');
        return sb.toString();
    }

    private String getFormattedProperty(String property) {
        Object resultObj = properties.get(property);
        String result = resultObj == null ? "missing" : resultObj.toString();
        // limit number of decimal places for floating point numbers
        try {
            double doubleValue = Double.parseDouble(result.trim());
            if (doubleValue != (long) doubleValue) {
                result = String.format(Locale.US, "%.2f", doubleValue);
            }
        } catch (NumberFormatException e) {
            // its not a number, never mind
        }
        return String.format(Locale.US, "%" + getSummaryColumnWidth(property) + "s, ", result);
    }

    private int getSummaryColumnWidth(String p) {
        return Math.max(10, p.length());
    }
}
