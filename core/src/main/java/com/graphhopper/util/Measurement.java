/*
 *  Licensed to Peter Karich under one or more contributor license
 *  agreements. See the NOTICE file distributed with this work for
 *  additional information regarding copyright ownership.
 *
 *  Peter Karich licenses this file to you under the Apache License,
 *  Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License. You may obtain a copy of the
 *  License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package com.graphhopper.util;

import com.graphhopper.routing.Path;
import com.graphhopper.routing.ch.PrepareContractionHierarchies;
import com.graphhopper.routing.util.AlgorithmPreparation;
import com.graphhopper.storage.Directory;
import com.graphhopper.storage.Graph;
import com.graphhopper.storage.GraphStorage;
import com.graphhopper.storage.LevelGraph;
import com.graphhopper.storage.LevelGraphStorage;
import com.graphhopper.storage.RAMDirectory;
import com.graphhopper.storage.index.Location2NodesNtreeLG;
import com.graphhopper.util.shapes.BBox;
import gnu.trove.list.TIntList;
import gnu.trove.list.array.TIntArrayList;
import java.io.FileWriter;
import java.io.IOException;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Random;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Peter Karich
 */
public class Measurement {

    public static void main(String[] strs) {
        new Measurement().start(CmdArgs.read(strs));
    }
    private Logger logger = LoggerFactory.getLogger(getClass());
    private Map<String, String> properties = new TreeMap<String, String>();

    // creates properties file in the format key=value
    // Every value is one y-value in a separate diagram with an identical x-value for every Measurement.start call
    void start(CmdArgs args) {
        String graphLocation = args.get("osmreader.graph-location", "");
        if (Helper.isEmpty(graphLocation))
            throw new IllegalStateException("no graph-location specified");

        String propLocation = args.get("measurement.location", "");
        if (Helper.isEmpty(propLocation))
            propLocation = "measurement" + new SimpleDateFormat("yyyy-MM-dd_HH_mm_ss").format(new Date()) + ".properties";

        long seed = args.getLong("measurement.seed", 123);
        Random rand = new Random(seed);
        boolean doPrepare = args.getBool("osmreader.doPrepare", true);
        int count = args.getInt("measurement.count", 1000);
        int lookupCount = 0;

        Directory dir = new RAMDirectory(graphLocation, true);
        LevelGraphStorage g = new LevelGraphStorage(dir);
        if (!g.loadExisting())
            throw new IllegalStateException("Cannot load existing levelgraph at " + graphLocation);
        // TODO make sure the graph is unprepared!

        StopWatch sw = new StopWatch().start();
        try {
            printGraphDetails(g);
            PrepareContractionHierarchies prepare = new PrepareContractionHierarchies().graph(g);
            if (doPrepare) {
                logger.info("nodes:" + g.nodes() + ", edges:" + g.getAllEdges().maxId());
                printPreparationDetails(g, prepare);
            }
            TIntList list = printLocation2IDQuery(g, dir, count, rand);
            lookupCount = list.size();
            printTimeOfRouteQuery(prepare, list);
            logger.info("store into " + propLocation);
        } catch (Exception ex) {
            logger.error("Problem while measuring " + graphLocation, ex);
            put("error", ex.toString());
        } finally {
            put("measurement.count", count);
            put("measurement.lookups", lookupCount);
            put("measurement.seed", seed);
            put("measurement.time", sw.stop().getTime());
            System.gc();
            put("measurement.totalMB", Helper.totalMB());
            put("measurement.usedMB", Helper.usedMB());
            try {
                store(new FileWriter(propLocation), "measurement finish, "
                        + new Date().toString() + ", " + Constants.BUILD_DATE);
            } catch (IOException ex) {
                logger.error("Problem while storing properties " + graphLocation + ", " + propLocation, ex);
            }
        }
    }

    private void printGraphDetails(GraphStorage g) {
        // graph size (edge, node and storage size)
        put("graph.nodes", g.nodes());
        put("graph.edges", g.getAllEdges().maxId());
        put("graph.sizeInMB", g.capacity() / Helper.MB);
    }

    private void printPreparationDetails(Graph g, PrepareContractionHierarchies prepare) {
        // time(preparation) + shortcuts number
        StopWatch sw = new StopWatch().start();
        prepare.doWork();
        put("prepare.time", sw.stop().getTime());
        put("prepare.shortcuts", prepare.shortcuts());
    }

    private TIntList printLocation2IDQuery(LevelGraph g, Directory dir, int count, final Random rand) {
        // time(location2id)
        count *= 2;
        final TIntArrayList list = new TIntArrayList(count);
        final BBox bbox = g.bounds();
        final Location2NodesNtreeLG idx = new Location2NodesNtreeLG(g, dir);
        if (!idx.loadExisting())
            throw new IllegalStateException("cannot find index at " + dir);

        final double latDelta = bbox.maxLat - bbox.minLat;
        final double lonDelta = bbox.maxLon - bbox.minLon;
        MiniPerfTest miniPerf = new MiniPerfTest() {
            @Override public int doCalc(boolean warmup, int run) {
                double lat = rand.nextDouble() * latDelta + bbox.minLat;
                double lon = rand.nextDouble() * lonDelta + bbox.minLon;
                int val = idx.findID(lat, lon);
                if (!warmup && val >= 0)
                    list.add(val);
                return val;
            }
        }.count(count).start();

        print("location2id", miniPerf);
        return list;
    }

    private void printTimeOfRouteQuery(final AlgorithmPreparation prepare, final TIntList list) {
        // time(route query)
        final AtomicLong maxDistance = new AtomicLong(0);
        final AtomicLong minDistance = new AtomicLong(Long.MAX_VALUE);
        final AtomicLong sum = new AtomicLong(0);
        int count = list.size() / 2;
        MiniPerfTest miniPerf = new MiniPerfTest() {
            @Override public int doCalc(boolean warmup, int run) {
                run *= 2;
                int from = list.get(run);
                int to = list.get(run + 1);
                Path p = prepare.createAlgo().calcPath(from, to);
                if (!warmup) {
                    long dist = (long) p.distance();
                    sum.addAndGet(dist);
                    if (dist > maxDistance.get())
                        maxDistance.set(dist);
                    if (dist < minDistance.get())
                        minDistance.set(dist);
                }

                return p.calcPoints().size();
            }
        }.count(count).start();

        put("routing.distanceMin", minDistance.get());
        put("routing.distanceMean", (float) sum.get() / count);
        put("routing.distanceMax", maxDistance.get());
        print("routing", miniPerf);
    }

    void print(String prefix, MiniPerfTest perf) {
        logger.info(perf.report());
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

    public abstract class MiniPerfTest {

        private int counts = 100;
        private double fullTime = 0;
        private double max;
        private double min = Double.MAX_VALUE;
        private int dummySum;

        public MiniPerfTest start() {
            int warmupCount = Math.max(1, counts / 3);
            for (int i = 0; i < warmupCount; i++) {
                dummySum += doCalc(true, i);
            }
            long startFull = System.nanoTime();
            for (int i = 0; i < counts; i++) {
                long start = System.nanoTime();
                dummySum += doCalc(false, i);
                long time = System.nanoTime() - start;
                if (time < min)
                    min = time;
                if (time > max)
                    max = time;
            }
            fullTime = System.nanoTime() - startFull;
            logger.info("dummySum:" + dummySum);
            return this;
        }

        public MiniPerfTest count(int counts) {
            this.counts = counts;
            return this;
        }

        // in ms
        public double getMin() {
            return min / 1e6;
        }

        // in ms
        public double getMax() {
            return max / 1e6;
        }

        // in ms
        public double getSum() {
            return fullTime / 1e6;
        }

        // in ms
        public double getMean() {
            return getSum() / counts;
        }

        public String report() {
            return "sum:" + nf(getSum() / 1000f) + "s, time/call:" + nf(getMean() / 1000f) + "s";
        }

        public String nf(Number num) {
            return new DecimalFormat("#.#").format(num);
        }

        /**
         * @return return some integer as result from your processing to make
         * sure that the JVM cannot optimize (away) the call or within the call
         * something.
         */
        public abstract int doCalc(boolean warmup, int run);
    }
}
