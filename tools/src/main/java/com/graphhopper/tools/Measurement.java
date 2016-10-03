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
import com.graphhopper.util.shapes.BBox;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Random;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * @author Peter Karich
 */
public class Measurement {
    private static final Logger logger = LoggerFactory.getLogger(Measurement.class);
    private final Map<String, String> properties = new TreeMap<String, String>();
    private long seed;
    private int maxNode;

    public static void main(String[] strs) {
        new Measurement().start(CmdArgs.read(strs));
    }

    // creates properties file in the format key=value
    // Every value is one y-value in a separate diagram with an identical x-value for every Measurement.start call
    void start(CmdArgs args) {
        String graphLocation = args.get("graph.location", "");
        String propLocation = args.get("measurement.location", "");
        if (Helper.isEmpty(propLocation))
            propLocation = "measurement" + new SimpleDateFormat("yyyy-MM-dd_HH_mm_ss").format(new Date()) + ".properties";

        seed = args.getLong("measurement.seed", 123);
        String gitCommit = args.get("measurement.gitinfo", "");
        int count = args.getInt("measurement.count", 5000);

        GraphHopper hopper = new GraphHopperOSM() {
            @Override
            protected void prepare() {
                StopWatch sw = new StopWatch().start();
                super.prepare();
                put("prepare.time", sw.stop().getTime());
                int edges = getGraphHopperStorage().getAllEdges().getMaxId();
                Weighting weighting = getCHFactoryDecorator().getWeightings().get(0);
                int edgesAndShortcuts = getGraphHopperStorage().getGraph(CHGraph.class, weighting).getAllEdges().getMaxId();
                put("prepare.shortcuts", edgesAndShortcuts - edges);
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
        hopper.getCHFactoryDecorator().setDisablingAllowed(true);
        hopper.importOrLoad();

        GraphHopperStorage g = hopper.getGraphHopperStorage();
        
        String vehicleStr = args.get("graph.flag_encoders", "car");
        FlagEncoder encoder = hopper.getEncodingManager().getEncoder(vehicleStr);
        Weighting weighting = hopper.getCHFactoryDecorator().getWeightings().get(0);

        StopWatch sw = new StopWatch().start();
        try {
            maxNode = g.getNodes();
            GHBitSet allowedEdges = printGraphDetails(g, vehicleStr);
            boolean isCH = false;
            printMiscUnitPerfTests(g, isCH, encoder, count * 100, allowedEdges);
            printLocationIndexQuery(g, hopper.getLocationIndex(), count);

            printTimeOfRouteQuery(hopper, isCH, count / 20, "routing", vehicleStr, true);

            System.gc();

            CHGraph lg = g.getGraph(CHGraph.class, weighting);
            fillAllowedEdges(lg.getAllEdges(), allowedEdges);
            isCH = true;
            printMiscUnitPerfTests(lg, isCH, encoder, count * 100, allowedEdges);
            printTimeOfRouteQuery(hopper, isCH, count, "routingCH", vehicleStr, true);
            printTimeOfRouteQuery(hopper, isCH, count, "routingCH_no_instr", vehicleStr, false);
            logger.info("store into " + propLocation);
        } catch (Exception ex) {
            logger.error("Problem while measuring " + graphLocation, ex);
            put("error", ex.toString());
        } finally {
            put("measurement.gitinfo", gitCommit);
            put("measurement.count", count);
            put("measurement.seed", seed);
            put("measurement.time", sw.stop().getTime());
            System.gc();
            put("measurement.totalMB", Helper.getTotalMB());
            put("measurement.usedMB", Helper.getUsedMB());
            try {
                store(new FileWriter(propLocation), "measurement finish, "
                        + new Date().toString() + ", " + Constants.BUILD_DATE);
            } catch (IOException ex) {
                logger.error("Problem while storing properties " + graphLocation + ", " + propLocation, ex);
            }
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
        put("graph.edges", g.getAllEdges().getMaxId());
        put("graph.size_in_MB", g.getCapacity() / Helper.MB);
        put("graph.encoder", vehicleStr);

        AllEdgesIterator iter = g.getAllEdges();
        final int maxEdgesId = g.getAllEdges().getMaxId();
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

        EdgeFilter outFilter = new DefaultEdgeFilter(encoder, false, true);
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

        final int maxEdgesId = graph.getAllEdges().getMaxId();
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

    private void printTimeOfRouteQuery(final GraphHopper hopper, final boolean ch, int count, String prefix,
                                       final String vehicle, final boolean withInstructions) {
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

        // if using none-bidirectional algorithm make sure you exclude CH routing
        final String algo = Algorithms.DIJKSTRA_BI;
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
                        setVehicle(vehicle).
                        setAlgorithm(algo);
                if (!ch)
                    req.getHints().put(CH.DISABLE, true);

                // req.getHints().put(algo + ".approximation", "BeelineSimplification");
                // req.getHints().put(algo + ".epsilon", 2);
                req.getHints().put("instructions", withInstructions);
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

                    if (!rsp.getErrors().get(0).getMessage().toLowerCase().contains("not found"))
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

    private void store(FileWriter fileWriter, String comment) throws IOException {
        fileWriter.append("#" + comment + "\n");
        for (Entry<String, String> e : properties.entrySet()) {
            fileWriter.append(e.getKey());
            fileWriter.append("=");
            fileWriter.append(e.getValue());
            fileWriter.append("\n");
        }
        fileWriter.flush();
    }
}
