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

import com.carrotsearch.hppc.IntArrayList;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.graphhopper.*;
import com.graphhopper.coll.GHBitSet;
import com.graphhopper.coll.GHBitSetImpl;
import com.graphhopper.config.CHProfile;
import com.graphhopper.config.LMProfile;
import com.graphhopper.config.Profile;
import com.graphhopper.config.TurnCostsConfig;
import com.graphhopper.jackson.Jackson;
import com.graphhopper.routing.TestProfiles;
import com.graphhopper.routing.ch.PrepareContractionHierarchies;
import com.graphhopper.routing.ev.BooleanEncodedValue;
import com.graphhopper.routing.ev.Subnetwork;
import com.graphhopper.routing.ev.TurnRestriction;
import com.graphhopper.routing.ev.VehicleAccess;
import com.graphhopper.routing.lm.LMConfig;
import com.graphhopper.routing.lm.PrepareLandmarks;
import com.graphhopper.routing.util.*;
import com.graphhopper.routing.weighting.Weighting;
import com.graphhopper.routing.weighting.custom.CustomWeighting;
import com.graphhopper.storage.*;
import com.graphhopper.storage.index.LocationIndex;
import com.graphhopper.util.*;
import com.graphhopper.util.Parameters.Algorithms;
import com.graphhopper.util.Parameters.CH;
import com.graphhopper.util.Parameters.Landmark;
import com.graphhopper.util.shapes.BBox;
import com.graphhopper.util.shapes.GHPoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static com.graphhopper.util.GHUtility.readCountries;
import static com.graphhopper.util.Helper.*;
import static com.graphhopper.util.Parameters.Algorithms.ALT_ROUTE;

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
    private boolean stopOnError;
    private int maxNode;
    private String vehicle;

    public static void main(String[] strs) throws IOException {
        PMap args = PMap.read(strs);
        int repeats = args.getInt("measurement.repeats", 1);
        for (int i = 0; i < repeats; ++i)
            new Measurement().start(args);
    }

    // creates properties file in the format key=value
    // Every value is one y-value in a separate diagram with an identical x-value for every Measurement.start call
    void start(PMap args) throws IOException {
        final String graphLocation = args.getString("graph.location", "");
        final boolean useJson = args.getBool("measurement.json", false);
        boolean cleanGraph = args.getBool("measurement.clean", false);
        stopOnError = args.getBool("measurement.stop_on_error", false);
        String summaryLocation = args.getString("measurement.summaryfile", "");
        final String timeStamp = new SimpleDateFormat("yyyy-MM-dd_HH:mm:ss").format(new Date());
        put("measurement.timestamp", timeStamp);
        String propFolder = args.getString("measurement.folder", "");
        if (!propFolder.isEmpty()) {
            Files.createDirectories(Paths.get(propFolder));
        }
        String propFilename = args.getString("measurement.filename", "");
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

        GraphHopper hopper = new GraphHopper() {
            @Override
            protected Map<String, PrepareContractionHierarchies.Result> prepareCH(boolean closeEarly, List<CHConfig> configsToPrepare) {
                StopWatch sw = new StopWatch().start();
                Map<String, PrepareContractionHierarchies.Result> result = super.prepareCH(closeEarly, configsToPrepare);
                // note that we measure the total time of all (possibly edge&node) CH preparations
                put(Parameters.CH.PREPARE + "time", sw.stop().getMillis());
                if (result.get("profile_no_tc") != null) {
                    int shortcuts = result.get("profile_no_tc").getCHStorage().getShortcuts();
                    put(Parameters.CH.PREPARE + "node.shortcuts", shortcuts);
                    put(Parameters.CH.PREPARE + "node.time", result.get("profile_no_tc").getTotalPrepareTime());
                }
                if (result.get("profile_tc") != null) {
                    int shortcuts = result.get("profile_tc").getCHStorage().getShortcuts();
                    put(Parameters.CH.PREPARE + "edge.shortcuts", shortcuts);
                    put(Parameters.CH.PREPARE + "edge.time", result.get("profile_tc").getTotalPrepareTime());
                }
                return result;
            }

            @Override
            protected List<PrepareLandmarks> prepareLM(boolean closeEarly, List<LMConfig> configsToPrepare) {
                List<PrepareLandmarks> prepareLandmarks = super.prepareLM(closeEarly, configsToPrepare);
                for (PrepareLandmarks plm : prepareLandmarks) {
                    put(Landmark.PREPARE + "time", plm.getTotalPrepareTime());
                }
                return prepareLandmarks;
            }

            @Override
            protected void cleanUp() {
                StopWatch sw = new StopWatch().start();
                super.cleanUp();
                put("graph.subnetwork_removal_time_ms", sw.stop().getMillis());
            }

            @Override
            protected void importOSM() {
                StopWatch sw = new StopWatch().start();
                super.importOSM();
                sw.stop();
                put("graph.import_time", sw.getSeconds());
                put("graph.import_time_ms", sw.getMillis());
            }
        };

        hopper.init(createConfigFromArgs(args));
        if (cleanGraph) {
            hopper.clean();
        }

        hopper.importOrLoad();

        BaseGraph g = hopper.getBaseGraph();
        EncodingManager encodingManager = hopper.getEncodingManager();
        BooleanEncodedValue accessEnc = encodingManager.getBooleanEncodedValue(VehicleAccess.key(vehicle));
        boolean withTurnCosts = encodingManager.hasTurnEncodedValue(TurnRestriction.key(vehicle));

        StopWatch sw = new StopWatch().start();
        try {
            maxNode = g.getNodes();

            final boolean runSlow = args.getBool("measurement.run_slow_routing", true);
            printGraphDetails(g, vehicle);
            measureGraphTraversal(g, accessEnc, count * 100);
            measureLocationIndex(g, hopper.getLocationIndex(), count);

            if (runSlow) {
                boolean isCH = false;
                boolean isLM = false;
                measureRouting(hopper, new QuerySettings("routing", count / 20, isCH, isLM).
                        withInstructions());
                measureRouting(hopper, new QuerySettings("routing_alt", count / 500, isCH, isLM).
                        alternative());
                if (withTurnCosts) {
                    measureRouting(hopper, new QuerySettings("routing_edge", count / 20, isCH, isLM).
                            withInstructions().edgeBased());
                    // unfortunately alt routes are so slow that we cannot really afford many iterations
                    measureRouting(hopper, new QuerySettings("routing_edge_alt", count / 500, isCH, isLM).
                            edgeBased().alternative()
                    );
                }
            }

            if (hopper.getLMPreparationHandler().isEnabled()) {
                gcAndWait();
                boolean isCH = false;
                boolean isLM = true;
                Helper.parseList(args.getString("measurement.lm.active_counts", "[4,8,12]")).stream()
                        .mapToInt(Integer::parseInt).forEach(activeLMCount -> {
                            measureRouting(hopper, new QuerySettings("routingLM" + activeLMCount, count / 20, isCH, isLM).
                                    withInstructions().activeLandmarks(activeLMCount));
                            measureRouting(hopper, new QuerySettings("routingLM" + activeLMCount + "_alt", count / 500, isCH, isLM).
                                    activeLandmarks(activeLMCount).alternative());
                            if (args.getBool("measurement.lm.edge_based", withTurnCosts)) {
                                measureRouting(hopper, new QuerySettings("routingLM" + activeLMCount + "_edge", count / 20, isCH, isLM).
                                        withInstructions().activeLandmarks(activeLMCount).edgeBased());
                                measureRouting(hopper, new QuerySettings("routingLM" + activeLMCount + "_alt_edge", count / 500, isCH, isLM).
                                        activeLandmarks(activeLMCount).edgeBased().alternative());
                            }
                        });
            }

            if (hopper.getCHPreparationHandler().isEnabled()) {
                boolean isCH = true;
                boolean isLM = false;
                gcAndWait();
                RoutingCHGraph nodeBasedCH = hopper.getCHGraphs().get("profile_no_tc");
                if (nodeBasedCH != null) {
                    measureGraphTraversalCH(nodeBasedCH, count * 100);
                    gcAndWait();
                    measureRouting(hopper, new QuerySettings("routingCH", count, isCH, isLM).
                            withInstructions().sod());
                    measureRouting(hopper, new QuerySettings("routingCH_alt", count / 100, isCH, isLM).
                            withInstructions().sod().alternative());
                    measureRouting(hopper, new QuerySettings("routingCH_with_hints", count, isCH, isLM).
                            withInstructions().sod().withPointHints());
                    measureRouting(hopper, new QuerySettings("routingCH_no_sod", count, isCH, isLM).
                            withInstructions());
                    measureRouting(hopper, new QuerySettings("routingCH_no_instr", count, isCH, isLM).
                            sod());
                    measureRouting(hopper, new QuerySettings("routingCH_full", count, isCH, isLM).
                            withInstructions().withPointHints().sod().simplify().pathDetails());
                    // for some strange (jvm optimizations) reason adding these measurements reduced the measured time for routingCH_full... see #2056
                    measureRouting(hopper, new QuerySettings("routingCH_via_100", count / 100, isCH, isLM).
                            withPoints(100).sod());
                    measureRouting(hopper, new QuerySettings("routingCH_via_100_full", count / 100, isCH, isLM).
                            withPoints(100).sod().withInstructions().simplify().pathDetails());
                }
                RoutingCHGraph edgeBasedCH = hopper.getCHGraphs().get("profile_tc");
                if (edgeBasedCH != null) {
                    measureRouting(hopper, new QuerySettings("routingCH_edge", count, isCH, isLM).
                            edgeBased().withInstructions());
                    measureRouting(hopper, new QuerySettings("routingCH_edge_alt", count / 100, isCH, isLM).
                            edgeBased().withInstructions().alternative());
                    measureRouting(hopper, new QuerySettings("routingCH_edge_no_instr", count, isCH, isLM).
                            edgeBased());
                    measureRouting(hopper, new QuerySettings("routingCH_edge_full", count, isCH, isLM).
                            edgeBased().withInstructions().withPointHints().simplify().pathDetails());
                    // for some strange (jvm optimizations) reason adding these measurements reduced the measured time for routingCH_edge_full... see #2056
                    measureRouting(hopper, new QuerySettings("routingCH_edge_via_100", count / 100, isCH, isLM).
                            withPoints(100).edgeBased().sod());
                    measureRouting(hopper, new QuerySettings("routingCH_edge_via_100_full", count / 100, isCH, isLM).
                            withPoints(100).edgeBased().sod().withInstructions().simplify().pathDetails());
                }
            }
            measureCountryAreaIndex(count);

        } catch (Exception ex) {
            logger.error("Problem while measuring " + graphLocation, ex);
            if (stopOnError)
                System.exit(1);
            put("error", ex.toString());
        } finally {
            put("gh.gitinfo", Constants.GIT_INFO != null ? Constants.GIT_INFO.toString() : "unknown");
            put("measurement.count", count);
            put("measurement.seed", seed);
            put("measurement.time", sw.stop().getMillis());
            gcAndWait();
            put("measurement.totalMB", getTotalMB());
            put("measurement.usedMB", getUsedMB());

            if (!isEmpty(summaryLocation)) {
                writeSummary(summaryLocation, propLocation);
            }
            if (useJson) {
                storeJson(propLocation, useMeasurementTimeAsRefTime);
            } else {
                storeProperties(propLocation);
            }
        }
    }

    private GraphHopperConfig createConfigFromArgs(PMap args) {
        GraphHopperConfig ghConfig = new GraphHopperConfig(args);
        vehicle = args.getString("measurement.vehicle", "car");
        boolean turnCosts = args.getBool("measurement.turn_costs", false);
        int uTurnCosts = args.getInt("measurement.u_turn_costs", 40);
        String weighting = args.getString("measurement.weighting", "custom");
        boolean useCHEdge = args.getBool("measurement.ch.edge", true);
        boolean useCHNode = args.getBool("measurement.ch.node", true);
        boolean useLM = args.getBool("measurement.lm", true);
        String customModelFile = args.getString("measurement.custom_model_file", "");
        List<Profile> profiles = new ArrayList<>();
        if (turnCosts && !vehicle.equals("car"))
            throw new IllegalArgumentException("turn costs not yet supported for: " + vehicle);
        List<String> restrictions = List.of("motorcar", "motor_vehicle");
        if (!customModelFile.isEmpty()) {
            if (!weighting.equals(CustomWeighting.NAME))
                throw new IllegalArgumentException("To make use of a custom model you need to set measurement.weighting to 'custom'");
            // use custom profile(s) as specified in the given custom model file
            CustomModel customModel = loadCustomModel(customModelFile);
            profiles.add(new Profile("profile_no_tc").setCustomModel(customModel));
            if (turnCosts)
                profiles.add(new Profile("profile_tc").setCustomModel(customModel).setTurnCostsConfig(new TurnCostsConfig(restrictions, uTurnCosts)));
        } else {
            // use standard profiles
            profiles.add(TestProfiles.accessAndSpeed("profile_no_tc", vehicle));
            if (turnCosts)
                profiles.add(TestProfiles.accessAndSpeed("profile_tc", vehicle).setTurnCostsConfig(new TurnCostsConfig(restrictions, uTurnCosts)));
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
        boolean withInstructions, withPointHints, sod, edgeBased, simplify, pathDetails, alternative;
        int points = 2;

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

        QuerySettings withPoints(int points) {
            this.points = points;
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

        QuerySettings pathDetails() {
            this.pathDetails = true;
            return this;
        }

        QuerySettings alternative() {
            alternative = true;
            return this;
        }
    }

    private void printGraphDetails(BaseGraph g, String vehicleStr) {
        // graph size (edge, node and storage size)
        put("graph.nodes", g.getNodes());
        put("graph.edges", g.getAllEdges().length());
        put("graph.size_in_MB", g.getCapacity() / MB);
        put("graph.encoder", vehicleStr);

        final GHBitSet validEdges = getValidEdges(g);
        put("graph.valid_edges", validEdges.getCardinality());
    }

    private void measureLocationIndex(Graph g, final LocationIndex idx, int count) {
        count *= 2;
        final BBox bbox = g.getBounds();
        final double latDelta = bbox.maxLat - bbox.minLat;
        final double lonDelta = bbox.maxLon - bbox.minLon;
        final Random rand = new Random(seed);
        MiniPerfTest miniPerf = new MiniPerfTest().setIterations(count).start((warmup, run) -> {
            double lat = rand.nextDouble() * latDelta + bbox.minLat;
            double lon = rand.nextDouble() * lonDelta + bbox.minLon;
            return idx.findClosest(lat, lon, EdgeFilter.ALL_EDGES).getClosestNode();
        });

        print("location_index", miniPerf);
    }

    private void measureGraphTraversal(final Graph graph, BooleanEncodedValue accessEnc, int count) {
        final Random rand = new Random(seed);

        EdgeFilter outFilter = AccessFilter.outEdges(accessEnc);
        final EdgeExplorer outExplorer = graph.createEdgeExplorer(outFilter);
        MiniPerfTest miniPerf = new MiniPerfTest().setIterations(count).start((warmup, run) -> {
            int nodeId = rand.nextInt(maxNode);
            return GHUtility.count(outExplorer.setBaseNode(nodeId));
        });
        print("unit_tests.out_edge_state_next", miniPerf);

        final EdgeExplorer allExplorer = graph.createEdgeExplorer();
        miniPerf = new MiniPerfTest().setIterations(count).start((warmup, run) -> {
            int nodeId = rand.nextInt(maxNode);
            return GHUtility.count(allExplorer.setBaseNode(nodeId));
        });
        print("unit_tests.all_edge_state_next", miniPerf);

        final int maxEdgesId = graph.getAllEdges().length();
        GHBitSet allowedEdges = getValidEdges(graph);
        miniPerf = new MiniPerfTest().setIterations(count).start((warmup, run) -> {
            while (true) {
                int edgeId = rand.nextInt(maxEdgesId);
                if (allowedEdges.contains(edgeId))
                    return graph.getEdgeIteratorState(edgeId, Integer.MIN_VALUE).getEdge();
            }
        });
        print("unit_tests.get_edge_state", miniPerf);
    }

    private void measureGraphTraversalCH(final RoutingCHGraph lg, int count) {
        final Random rand = new Random(seed);
        final int maxEdgesId = lg.getEdges();
        MiniPerfTest miniPerf = new MiniPerfTest().setIterations(count).start((warmup, run) -> {
            int edgeId = rand.nextInt(maxEdgesId);
            return lg.getEdgeIteratorState(edgeId, Integer.MIN_VALUE).getEdge();
        });
        print("unit_testsCH.get_edge_state", miniPerf);

        final RoutingCHEdgeExplorer chOutEdgeExplorer = lg.createOutEdgeExplorer();
        miniPerf = new MiniPerfTest().setIterations(count).start((warmup, run) -> {
            int nodeId = rand.nextInt(maxNode);
            RoutingCHEdgeIterator iter = chOutEdgeExplorer.setBaseNode(nodeId);
            while (iter.next()) {
                nodeId += iter.getAdjNode();
            }
            return nodeId;
        });
        print("unit_testsCH.out_edge_next", miniPerf);

        miniPerf = new MiniPerfTest().setIterations(count).start((warmup, run) -> {
            int nodeId = rand.nextInt(maxNode);
            RoutingCHEdgeIterator iter = chOutEdgeExplorer.setBaseNode(nodeId);
            while (iter.next()) {
                nodeId += iter.getWeight(false);
            }
            return nodeId;
        });
        print("unit_testsCH.out_edge_get_weight", miniPerf);
    }

    private GHBitSet getValidEdges(Graph g) {
        final GHBitSet result = new GHBitSetImpl(g.getAllEdges().length());
        AllEdgesIterator iter = g.getAllEdges();
        while (iter.next())
            result.add(iter.getEdge());
        return result;
    }

    private void measureCountryAreaIndex(int count) {
        AreaIndex<CustomArea> countryIndex = new AreaIndex<>(readCountries());
        Random rnd = new Random(seed);
        // generate random points in Europe
        final List<GHPoint> randomPoints = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            double lat = 36d + rnd.nextDouble() * 24d;
            double lon = -14d + rnd.nextDouble() * 47d;
            randomPoints.add(new GHPoint(lat, lon));
        }
        MiniPerfTest lookupPerfTest = new MiniPerfTest().setIterations(count).start((warmup, run) -> {
            int checksum = 0;
            for (int i = 0; i < 1_000; i++) {
                GHPoint point = randomPoints.get(rnd.nextInt(randomPoints.size()));
                checksum += countryIndex.query(point.lat, point.lon).size();
            }
            return checksum;
        });
        print("area_index.query", lookupPerfTest);
    }

    private void measureRouting(final GraphHopper hopper, final QuerySettings querySettings) {
        final Graph g = hopper.getBaseGraph();
        final AtomicLong maxDistance = new AtomicLong(0);
        final AtomicLong minDistance = new AtomicLong(Long.MAX_VALUE);
        final AtomicLong distSum = new AtomicLong(0);
        final AtomicLong airDistSum = new AtomicLong(0);
        final AtomicLong altCount = new AtomicLong(0);
        final AtomicInteger failedCount = new AtomicInteger(0);
        final DistanceCalc distCalc = new DistanceCalcEarth();

        String profileName = querySettings.edgeBased ? "profile_tc" : "profile_no_tc";
        Weighting weighting = hopper.createWeighting(hopper.getProfile(profileName), new PMap());
        final EdgeFilter edgeFilter = new DefaultSnapFilter(weighting, hopper.getEncodingManager().getBooleanEncodedValue(Subnetwork.key(profileName)));
        final EdgeExplorer edgeExplorer = g.createEdgeExplorer(edgeFilter);
        final AtomicLong visitedNodesSum = new AtomicLong(0);
        final AtomicLong maxVisitedNodes = new AtomicLong(0);
        final Random rand = new Random(seed);
        final NodeAccess na = g.getNodeAccess();

        MiniPerfTest miniPerf = new MiniPerfTest().setIterations(querySettings.count).start((warmup, run) -> {
            GHRequest req = new GHRequest(querySettings.points);
            IntArrayList nodes = new IntArrayList(querySettings.points);
            List<GHPoint> points = new ArrayList<>();
            List<String> pointHints = new ArrayList<>();
            int tries = 0;
            while (nodes.size() < querySettings.points) {
                int node = rand.nextInt(maxNode);
                if (++tries > g.getNodes())
                    throw new RuntimeException("Could not find accessible points");
                // probe location. it could be a pedestrian area or an edge removed in the subnetwork removal process
                if (GHUtility.count(edgeExplorer.setBaseNode(node)) == 0)
                    continue;
                nodes.add(node);
                points.add(new GHPoint(na.getLat(node), na.getLon(node)));
                if (querySettings.withPointHints) {
                    // we add some point hint to make sure the name similarity filter has to do some actual work
                    pointHints.add("probably_not_found");
                }
            }
            req.setPoints(points);
            req.setPointHints(pointHints);
            req.setProfile(profileName);
            req.getHints().
                    putObject(CH.DISABLE, !querySettings.ch).
                    putObject("stall_on_demand", querySettings.sod).
                    putObject(Landmark.DISABLE, !querySettings.lm).
                    putObject(Landmark.ACTIVE_COUNT, querySettings.activeLandmarks).
                    putObject("instructions", querySettings.withInstructions);

            if (querySettings.alternative)
                req.setAlgorithm(ALT_ROUTE);

            if (querySettings.pathDetails)
                req.setPathDetails(Arrays.asList(Parameters.Details.AVERAGE_SPEED, Parameters.Details.EDGE_ID, Parameters.Details.STREET_NAME));

            if (!querySettings.simplify)
                req.getHints().putObject(Parameters.Routing.WAY_POINT_MAX_DISTANCE, 0);

            GHResponse rsp;
            try {
                rsp = hopper.route(req);
            } catch (Exception ex) {
                // 'not found' can happen if import creates more than one subnetwork
                throw new RuntimeException("Error while calculating route! nodes: " + nodes + ", request:" + req, ex);
            }

            if (rsp.hasErrors()) {
                if (!warmup)
                    failedCount.incrementAndGet();

                if (rsp.getErrors().get(0).getMessage() == null)
                    rsp.getErrors().get(0).printStackTrace();
                else if (!toLowerCase(rsp.getErrors().get(0).getMessage()).contains("not found")) {
                    if (stopOnError)
                        throw new RuntimeException("errors should NOT happen in Measurement! " + req + " => " + rsp.getErrors());
                    else
                        logger.error("errors should NOT happen in Measurement! " + req + " => " + rsp.getErrors());
                }
                return 0;
            }

            ResponsePath responsePath = rsp.getBest();
            if (!warmup) {
                long visitedNodes = rsp.getHints().getLong("visited_nodes.sum", 0);
                visitedNodesSum.addAndGet(visitedNodes);
                if (visitedNodes > maxVisitedNodes.get()) {
                    maxVisitedNodes.set(visitedNodes);
                }

                rsp.getAll().forEach(p -> {
                    long dist = (long) p.getDistance();
                    distSum.addAndGet(dist);
                });

                long dist = (long) responsePath.getDistance();

                GHPoint prev = req.getPoints().get(0);
                for (GHPoint point : req.getPoints()) {
                    airDistSum.addAndGet((long) distCalc.calcDist(prev.getLat(), prev.getLon(), point.getLat(), point.getLon()));
                    prev = point;
                }

                if (dist > maxDistance.get())
                    maxDistance.set(dist);

                if (dist < minDistance.get())
                    minDistance.set(dist);

                if (querySettings.alternative)
                    altCount.addAndGet(rsp.getAll().size());
            }

            return responsePath.getPoints().size();
        });

        int count = querySettings.count - failedCount.get();
        if (count == 0)
            throw new RuntimeException("All requests failed, something must be wrong: " + failedCount.get());

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
        ObjectMapper om = Jackson.initObjectMapper(new ObjectMapper());
        try {
            return om.readValue(Helper.readJSONFileWithoutComments(customModelLocation), CustomModel.class);
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
                "graph.nodes",
                "graph.edges",
                "graph.import_time",
                CH.PREPARE + "time",
                CH.PREPARE + "node.time",
                CH.PREPARE + "edge.time",
                CH.PREPARE + "node.shortcuts",
                CH.PREPARE + "edge.shortcuts",
                Landmark.PREPARE + "time",
                "routing.distance_mean",
                "routing.mean",
                "routing.visited_nodes_mean",
                "routingCH.distance_mean",
                "routingCH.mean",
                "routingCH.visited_nodes_mean",
                "routingCH_no_instr.mean",
                "routingCH_full.mean",
                "routingCH_edge.distance_mean",
                "routingCH_edge.mean",
                "routingCH_edge.visited_nodes_mean",
                "routingCH_edge_no_instr.mean",
                "routingCH_edge_full.mean",
                "routingLM8.distance_mean",
                "routingLM8.mean",
                "routingLM8.visited_nodes_mean",
                "measurement.seed",
                "measurement.gitinfo",
                "measurement.timestamp"
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
        sb.append("propertyFile");
        sb.append('\n');
        return sb.toString();
    }

    private String getSummaryLogLine(String[] properties, String propLocation) {
        StringBuilder sb = new StringBuilder(" ");
        for (String p : properties) {
            sb.append(getFormattedProperty(p));
        }
        sb.append(propLocation);
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

    private static void gcAndWait() {
        long before = getTotalGcCount();
        // trigger gc
        System.gc();
        while (getTotalGcCount() == before) {
            // wait for the gc to have completed
        }
    }

    private static long getTotalGcCount() {
        long sum = 0;
        for (GarbageCollectorMXBean b : ManagementFactory.getGarbageCollectorMXBeans()) {
            long count = b.getCollectionCount();
            if (count != -1) {
                sum += count;
            }
        }
        return sum;
    }
}
