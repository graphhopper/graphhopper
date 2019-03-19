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
import com.graphhopper.PathWrapper;
import com.graphhopper.coll.GHBitSet;
import com.graphhopper.coll.GHBitSetImpl;
import com.graphhopper.reader.DataReader;
import com.graphhopper.reader.osm.GraphHopperOSM;
import com.graphhopper.routing.util.*;
import com.graphhopper.routing.weighting.Weighting;
import com.graphhopper.storage.CHGraph;
import com.graphhopper.storage.Graph;
import com.graphhopper.storage.GraphHopperStorage;
import com.graphhopper.storage.NodeAccess;
import com.graphhopper.storage.index.LocationIndex;
import com.graphhopper.util.*;
import com.graphhopper.util.Parameters.Algorithms;
import com.graphhopper.util.Parameters.CH;
import com.graphhopper.util.Parameters.Landmark;
import com.graphhopper.util.shapes.BBox;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static com.graphhopper.util.Helper.*;
import static com.graphhopper.util.Parameters.Algorithms.DIJKSTRA_BI;

/**
 * @author Peter Karich
 */
public class Measurement {
    private static final Logger logger = LoggerFactory.getLogger(Measurement.class);
    private final Map<String, String> properties = new TreeMap<>();
    private long seed;
    private int maxNode;

    public static void main(String[] strs) {
        CmdArgs cmdArgs = CmdArgs.read(strs);
        int repeats = cmdArgs.getInt("measurement.repeats", 1);
        for (int i = 0; i < repeats; ++i)
            new Measurement().start(cmdArgs);
    }

    // creates properties file in the format key=value
    // Every value is one y-value in a separate diagram with an identical x-value for every Measurement.start call
    void start(CmdArgs args) {
        String graphLocation = args.get("graph.location", "");
        String propLocation = args.get("measurement.location", "");
        boolean cleanGraph = args.getBool("measurement.clean", false);
        String summaryLocation = args.get("measurement.summaryfile", "");
        String timeStamp = new SimpleDateFormat("yyyy-MM-dd_HH_mm_ss").format(new Date());
        put("measurement.timestamp", timeStamp);
        if (isEmpty(propLocation)) {
            propLocation = "measurement" + timeStamp + ".properties";
        }

        seed = args.getLong("measurement.seed", 123);
        put("measurement.gitinfo", args.get("measurement.gitinfo", ""));
        int count = args.getInt("measurement.count", 5000);

        GraphHopper hopper = new GraphHopperOSM() {
            @Override
            protected void prepareCH() {
                StopWatch sw = new StopWatch().start();
                super.prepareCH();
                // note that we measure the total time of all (possibly edge&node) CH preparations
                put(Parameters.CH.PREPARE + "time", sw.stop().getMillis());
                int edges = getGraphHopperStorage().getEdges();
                if (!getCHFactoryDecorator().getNodeBasedWeightings().isEmpty()) {
                    Weighting weighting = getCHFactoryDecorator().getNodeBasedWeightings().get(0);
                    int edgesAndShortcuts = getGraphHopperStorage().getGraph(CHGraph.class, weighting).getEdges();
                    put(Parameters.CH.PREPARE + "node.shortcuts", edgesAndShortcuts - edges);
                }
                if (!getCHFactoryDecorator().getEdgeBasedWeightings().isEmpty()) {
                    Weighting weighting = getCHFactoryDecorator().getEdgeBasedWeightings().get(0);
                    int edgesAndShortcuts = getGraphHopperStorage().getGraph(CHGraph.class, weighting).getEdges();
                    put(Parameters.CH.PREPARE + "edge.shortcuts", edgesAndShortcuts - edges);
                }
            }

            @Override
            protected void loadOrPrepareLM() {
                StopWatch sw = new StopWatch().start();
                super.loadOrPrepareLM();
                put(Landmark.PREPARE + "time", sw.stop().getMillis());
            }

            @Override
            protected DataReader importData() throws IOException {
                StopWatch sw = new StopWatch().start();
                DataReader dr = super.importData();
                put("graph.import_time", sw.stop().getSeconds());
                return dr;
            }
        };

        hopper.init(args).
                forDesktop();
        if (cleanGraph) {
            hopper.clean();
        }

        hopper.getCHFactoryDecorator().setDisablingAllowed(true);
        hopper.getLMFactoryDecorator().setDisablingAllowed(true);
        hopper.importOrLoad();

        GraphHopperStorage g = hopper.getGraphHopperStorage();
        EncodingManager encodingManager = hopper.getEncodingManager();
        if (encodingManager.fetchEdgeEncoders().size() != 1) {
            throw new IllegalArgumentException("There has to be exactly one encoder for each measurement");
        }
        FlagEncoder encoder = encodingManager.fetchEdgeEncoders().get(0);
        String vehicleStr = encoder.toString();

        StopWatch sw = new StopWatch().start();
        try {
            maxNode = g.getNodes();
            boolean isCH = false;
            boolean isLM = false;
            GHBitSet allowedEdges = printGraphDetails(g, vehicleStr);
            printMiscUnitPerfTests(g, isCH, encoder, count * 100, allowedEdges);
            printLocationIndexQuery(g, hopper.getLocationIndex(), count);
            printTimeOfRouteQuery(hopper, isCH, isLM, count / 20, "routing", vehicleStr, true, -1, true, false);

            if (hopper.getLMFactoryDecorator().isEnabled()) {
                System.gc();
                isLM = true;
                int activeLMCount = 12;
                for (; activeLMCount > 3; activeLMCount -= 4) {
                    printTimeOfRouteQuery(hopper, isCH, isLM, count / 4, "routingLM" + activeLMCount, vehicleStr, true, activeLMCount, true, false);
                }

                // compareRouting(hopper, vehicleStr, count / 5);
            }

            if (hopper.getCHFactoryDecorator().isEnabled()) {
                isCH = true;
//                compareCHWithAndWithoutSOD(hopper, vehicleStr, count/5);
                if (hopper.getLMFactoryDecorator().isEnabled()) {
                    isLM = true;
                    System.gc();
                    // try just one constellation, often ~4-6 is best
                    int lmCount = 5;
                    printTimeOfRouteQuery(hopper, isCH, isLM, count, "routingCHLM" + lmCount, vehicleStr, true, lmCount, true, false);
                }

                isLM = false;
                System.gc();
                if (!hopper.getCHFactoryDecorator().getNodeBasedWeightings().isEmpty()) {
                    Weighting weighting = hopper.getCHFactoryDecorator().getNodeBasedWeightings().get(0);
                    CHGraph lg = g.getGraph(CHGraph.class, weighting);
                    fillAllowedEdges(lg.getAllEdges(), allowedEdges);
                    printMiscUnitPerfTests(lg, isCH, encoder, count * 100, allowedEdges);
                    printTimeOfRouteQuery(hopper, isCH, isLM, count, "routingCH", vehicleStr, true, -1, true, false);
                    printTimeOfRouteQuery(hopper, isCH, isLM, count, "routingCH_no_sod", vehicleStr, true, -1, false, false);
                    printTimeOfRouteQuery(hopper, isCH, isLM, count, "routingCH_no_instr", vehicleStr, false, -1, true, false);
                }
                if (!hopper.getCHFactoryDecorator().getEdgeBasedWeightings().isEmpty()) {
                    printTimeOfRouteQuery(hopper, isCH, isLM, count, "routingCH_edge", vehicleStr, true, -1, false, true);
                    printTimeOfRouteQuery(hopper, isCH, isLM, count, "routingCH_edge_no_instr", vehicleStr, false, -1, false, true);
                }
            }
        } catch (Exception ex) {
            logger.error("Problem while measuring " + graphLocation, ex);
            put("error", ex.toString());
        } finally {
            put("gh.gitinfo", Constants.GIT_INFO);
            put("measurement.count", count);
            put("measurement.seed", seed);
            put("measurement.time", sw.stop().getMillis());
            System.gc();
            put("measurement.totalMB", getTotalMB());
            put("measurement.usedMB", getUsedMB());

            if (!summaryLocation.trim().isEmpty()) {
                writeSummary(summaryLocation, propLocation);
            }
            storeProperties(graphLocation, propLocation);
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

    private void printLocationIndexQuery(Graph g, final LocationIndex idx, int count) {
        count *= 2;
        final BBox bbox = g.getBounds();
        final double latDelta = bbox.maxLat - bbox.minLat;
        final double lonDelta = bbox.maxLon - bbox.minLon;
        final Random rand = new Random(seed);
        MiniPerfTest miniPerf = new MiniPerfTest() {
            @Override
            public int doCalc(boolean warmup, int run) {
                double lat = rand.nextDouble() * latDelta + bbox.minLat;
                double lon = rand.nextDouble() * lonDelta + bbox.minLon;
                int val = idx.findClosest(lat, lon, EdgeFilter.ALL_EDGES).getClosestNode();
//                if (!warmup && val >= 0)
//                    list.add(val);

                return val;
            }
        }.setIterations(count).start();

        print("location_index", miniPerf);
    }

    private void printMiscUnitPerfTests(final Graph graph, boolean isCH, final FlagEncoder encoder,
                                        int count, final GHBitSet allowedEdges) {
        final Random rand = new Random(seed);
        String description = "";
        if (isCH) {
            description = "CH";
            CHGraph lg = (CHGraph) graph;
            final CHEdgeExplorer chExplorer = lg.createEdgeExplorer(new LevelEdgeFilter(lg));
            MiniPerfTest miniPerf = new MiniPerfTest() {
                @Override
                public int doCalc(boolean warmup, int run) {
                    int nodeId = rand.nextInt(maxNode);
                    return GHUtility.count(chExplorer.setBaseNode(nodeId));
                }
            }.setIterations(count).start();
            print("unit_testsCH.level_edge_state_next", miniPerf);

            final CHEdgeExplorer chExplorer2 = lg.createEdgeExplorer();
            miniPerf = new MiniPerfTest() {
                @Override
                public int doCalc(boolean warmup, int run) {
                    int nodeId = rand.nextInt(maxNode);
                    CHEdgeIterator iter = chExplorer2.setBaseNode(nodeId);
                    while (iter.next()) {
                        if (iter.isShortcut())
                            nodeId += (int) iter.getWeight();
                    }
                    return nodeId;
                }
            }.setIterations(count).start();
            print("unit_testsCH.get_weight", miniPerf);
        }

        EdgeFilter outFilter = DefaultEdgeFilter.outEdges(encoder);
        final EdgeExplorer outExplorer = graph.createEdgeExplorer(outFilter);
        MiniPerfTest miniPerf = new MiniPerfTest() {
            @Override
            public int doCalc(boolean warmup, int run) {
                int nodeId = rand.nextInt(maxNode);
                return GHUtility.count(outExplorer.setBaseNode(nodeId));
            }
        }.setIterations(count).start();
        print("unit_tests" + description + ".out_edge_state_next", miniPerf);

        final EdgeExplorer allExplorer = graph.createEdgeExplorer();
        miniPerf = new MiniPerfTest() {
            @Override
            public int doCalc(boolean warmup, int run) {
                int nodeId = rand.nextInt(maxNode);
                return GHUtility.count(allExplorer.setBaseNode(nodeId));
            }
        }.setIterations(count).start();
        print("unit_tests" + description + ".all_edge_state_next", miniPerf);

        final int maxEdgesId = graph.getAllEdges().length();
        miniPerf = new MiniPerfTest() {
            @Override
            public int doCalc(boolean warmup, int run) {
                while (true) {
                    int edgeId = rand.nextInt(maxEdgesId);
                    if (allowedEdges.contains(edgeId))
                        return graph.getEdgeIteratorState(edgeId, Integer.MIN_VALUE).getEdge();
                }
            }
        }.setIterations(count).start();
        print("unit_tests" + description + ".get_edge_state", miniPerf);
    }

    private void compareRouting(final GraphHopper hopper, String vehicle, int count) {
        logger.info("Comparing " + count + " routes. Differences will be printed to stderr.");
        String algo = Algorithms.ASTAR_BI;
        final Random rand = new Random(seed);
        final Graph g = hopper.getGraphHopperStorage();
        final NodeAccess na = g.getNodeAccess();

        for (int i = 0; i < count; i++) {
            int from = rand.nextInt(maxNode);
            int to = rand.nextInt(maxNode);

            double fromLat = na.getLatitude(from);
            double fromLon = na.getLongitude(from);
            double toLat = na.getLatitude(to);
            double toLon = na.getLongitude(to);
            GHRequest req = new GHRequest(fromLat, fromLon, toLat, toLon).
                    setWeighting("fastest").
                    setVehicle(vehicle).
                    setAlgorithm(algo);

            GHResponse lmRsp = hopper.route(req);
            req.getHints().put(Landmark.DISABLE, true);
            GHResponse originalRsp = hopper.route(req);

            String locStr = " iteration " + i + ". " + fromLat + "," + fromLon + " -> " + toLat + "," + toLon;
            if (lmRsp.hasErrors()) {
                if (originalRsp.hasErrors())
                    continue;
                logger.error("Error for LM but not for original response " + locStr);
            }

            String infoStr = " weight:" + lmRsp.getBest().getRouteWeight() + ", original: " + originalRsp.getBest().getRouteWeight()
                    + " distance:" + lmRsp.getBest().getDistance() + ", original: " + originalRsp.getBest().getDistance()
                    + " time:" + round2(lmRsp.getBest().getTime() / 1000) + ", original: " + round2(originalRsp.getBest().getTime() / 1000)
                    + " points:" + lmRsp.getBest().getPoints().size() + ", original: " + originalRsp.getBest().getPoints().size();

            if (Math.abs(1 - lmRsp.getBest().getRouteWeight() / originalRsp.getBest().getRouteWeight()) > 0.000001)
                logger.error("Too big weight difference for LM. " + locStr + infoStr);
        }
    }

    private void compareCHWithAndWithoutSOD(final GraphHopper hopper, String vehicle, int count) {
        logger.info("Comparing " + count + " routes for CH with and without stall on demand." +
                " Differences will be printed to stderr.");
        final Random rand = new Random(seed);
        final Graph g = hopper.getGraphHopperStorage();
        final NodeAccess na = g.getNodeAccess();

        for (int i = 0; i < count; i++) {
            int from = rand.nextInt(maxNode);
            int to = rand.nextInt(maxNode);

            double fromLat = na.getLatitude(from);
            double fromLon = na.getLongitude(from);
            double toLat = na.getLatitude(to);
            double toLon = na.getLongitude(to);
            GHRequest sodReq = new GHRequest(fromLat, fromLon, toLat, toLon).
                    setWeighting("fastest").
                    setVehicle(vehicle).
                    setAlgorithm(DIJKSTRA_BI);

            GHRequest noSodReq = new GHRequest(fromLat, fromLon, toLat, toLon).
                    setWeighting("fastest").
                    setVehicle(vehicle).
                    setAlgorithm(DIJKSTRA_BI);
            noSodReq.getHints().put("stall_on_demand", false);

            GHResponse sodRsp = hopper.route(sodReq);
            GHResponse noSodRsp = hopper.route(noSodReq);

            String locStr = " iteration " + i + ". " + fromLat + "," + fromLon + " -> " + toLat + "," + toLon;
            if (sodRsp.hasErrors()) {
                if (noSodRsp.hasErrors()) {
                    logger.info("Error with and without SOD");
                    continue;
                } else {
                    logger.error("Error with SOD but not without SOD" + locStr);
                    continue;
                }
            }
            String infoStr =
                    " weight:" + noSodRsp.getBest().getRouteWeight() + ", original: " + sodRsp.getBest().getRouteWeight()
                            + " distance:" + noSodRsp.getBest().getDistance() + ", original: " + sodRsp.getBest().getDistance()
                            + " time:" + round2(noSodRsp.getBest().getTime() / 1000) + ", original: " + round2(sodRsp.getBest().getTime() / 1000)
                            + " points:" + noSodRsp.getBest().getPoints().size() + ", original: " + sodRsp.getBest().getPoints().size();

            if (Math.abs(1 - noSodRsp.getBest().getRouteWeight() / sodRsp.getBest().getRouteWeight()) > 0.000001)
                logger.error("Too big weight difference for SOD. " + locStr + infoStr);
        }
    }

    private void printTimeOfRouteQuery(final GraphHopper hopper, final boolean ch, final boolean lm,
                                       int count, String prefix, final String vehicle,
                                       final boolean withInstructions, final int activeLandmarks, final boolean sod, final boolean edgeBased) {
        final Graph g = hopper.getGraphHopperStorage();
        final AtomicLong maxDistance = new AtomicLong(0);
        final AtomicLong minDistance = new AtomicLong(Long.MAX_VALUE);
        final AtomicLong distSum = new AtomicLong(0);
        final AtomicLong airDistSum = new AtomicLong(0);
        final AtomicInteger failedCount = new AtomicInteger(0);
        final DistanceCalc distCalc = new DistanceCalcEarth();

        final AtomicLong visitedNodesSum = new AtomicLong(0);
//        final AtomicLong extractTimeSum = new AtomicLong(0);
//        final AtomicLong calcPointsTimeSum = new AtomicLong(0);
//        final AtomicLong calcDistTimeSum = new AtomicLong(0);
//        final AtomicLong tmpDist = new AtomicLong(0);
        final Random rand = new Random(seed);
        final NodeAccess na = g.getNodeAccess();

        MiniPerfTest miniPerf = new MiniPerfTest() {
            @Override
            public int doCalc(boolean warmup, int run) {
                int from = rand.nextInt(maxNode);
                int to = rand.nextInt(maxNode);
                double fromLat = na.getLatitude(from);
                double fromLon = na.getLongitude(from);
                double toLat = na.getLatitude(to);
                double toLon = na.getLongitude(to);
                GHRequest req = new GHRequest(fromLat, fromLon, toLat, toLon).
                        setWeighting("fastest").
                        setVehicle(vehicle);

                req.getHints().put(CH.DISABLE, !ch).
                        put("stall_on_demand", sod).
                        put(Parameters.Routing.EDGE_BASED, edgeBased).
                        put(Landmark.DISABLE, !lm).
                        put(Landmark.ACTIVE_COUNT, activeLandmarks).
                        put("instructions", withInstructions);

                if (withInstructions)
                    req.setPathDetails(Arrays.asList(Parameters.DETAILS.AVERAGE_SPEED));

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

                PathWrapper arsp = rsp.getBest();
                if (!warmup) {
                    visitedNodesSum.addAndGet(rsp.getHints().getLong("visited_nodes.sum", 0));
                    long dist = (long) arsp.getDistance();
                    distSum.addAndGet(dist);

                    airDistSum.addAndGet((long) distCalc.calcDist(fromLat, fromLon, toLat, toLon));

                    if (dist > maxDistance.get())
                        maxDistance.set(dist);

                    if (dist < minDistance.get())
                        minDistance.set(dist);

//                    extractTimeSum.addAndGet(p.getExtractTime());                    
//                    long start = System.nanoTime();
//                    size = p.calcPoints().getSize();
//                    calcPointsTimeSum.addAndGet(System.nanoTime() - start);
                }

                return arsp.getPoints().getSize();
            }
        }.setIterations(count).start();

        count -= failedCount.get();

        // if using non-bidirectional algorithm make sure you exclude CH routing
        String algoStr = (ch && !edgeBased) ? Algorithms.DIJKSTRA_BI : Algorithms.ASTAR_BI;
        if (ch && !sod) {
            algoStr += "_no_sod";
        }
        put(prefix + ".guessed_algorithm", algoStr);
        put(prefix + ".failed_count", failedCount.get());
        put(prefix + ".distance_min", minDistance.get());
        put(prefix + ".distance_mean", (float) distSum.get() / count);
        put(prefix + ".air_distance_mean", (float) airDistSum.get() / count);
        put(prefix + ".distance_max", maxDistance.get());
        put(prefix + ".visited_nodes_mean", (float) visitedNodesSum.get() / count);

//        put(prefix + ".extractTime", (float) extractTimeSum.get() / count / 1000000f);
//        put(prefix + ".calcPointsTime", (float) calcPointsTimeSum.get() / count / 1000000f);
//        put(prefix + ".calcDistTime", (float) calcDistTimeSum.get() / count / 1000000f);
        print(prefix, miniPerf);
    }

    void print(String prefix, MiniPerfTest perf) {
        logger.info(prefix + ": " + perf.getReport());
        put(prefix + ".sum", perf.getSum());
//        put(prefix+".rms", perf.getRMS());
        put(prefix + ".min", perf.getMin());
        put(prefix + ".mean", perf.getMean());
        put(prefix + ".max", perf.getMax());
    }

    void put(String key, Object val) {
        // convert object to string to make serialization possible
        properties.put(key, "" + val);
    }

    private void storeProperties(String graphLocation, String propLocation) {
        logger.info("storing measurement properties in " + propLocation);
        try (FileWriter fileWriter = new FileWriter(propLocation)) {
            String comment = "measurement finish, " + new Date().toString() + ", " + Constants.BUILD_DATE;
            fileWriter.append("#" + comment + "\n");
            for (Entry<String, String> e : properties.entrySet()) {
                fileWriter.append(e.getKey());
                fileWriter.append("=");
                fileWriter.append(e.getValue());
                fileWriter.append("\n");
            }
            fileWriter.flush();
        } catch (IOException e) {
            logger.error("Problem while storing properties " + graphLocation + ", " + propLocation, e);
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
                "routingCH_edge.distance_mean",
                "routingCH_edge.mean",
                "routingCH_edge.visited_nodes_mean",
                "routingCH_edge_no_instr.mean",
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
        String result = properties.get(property);
        if (result == null) {
            result = "missing";
        }
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
