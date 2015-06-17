/*
 *  Licensed to GraphHopper and Peter Karich under one or more contributor
 *  license agreements. See the NOTICE file distributed with this work for 
 *  additional information regarding copyright ownership.
 *
 *  GraphHopper licenses this file to you under the Apache License, 
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
import com.graphhopper.routing.util.*;
import com.graphhopper.storage.*;
import com.graphhopper.storage.index.LocationIndex;
import com.graphhopper.util.CmdArgs;
import com.graphhopper.util.Constants;
import com.graphhopper.util.DistanceCalc;
import com.graphhopper.util.DistanceCalcEarth;
import com.graphhopper.util.Helper;
import com.graphhopper.util.MiniPerfTest;
import com.graphhopper.util.StopWatch;
import com.graphhopper.util.shapes.BBox;

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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Peter Karich
 */
public class Measurement
{
    public static void main( String[] strs )
    {
        new Measurement().start(CmdArgs.read(strs));
    }

    private static final Logger logger = LoggerFactory.getLogger(Measurement.class);
    private final Map<String, String> properties = new TreeMap<String, String>();
    private long seed;
    private int maxNode;

    class MeasureHopper extends GraphHopper
    {
        @Override
        protected void prepare()
        {
            // do nothing as we need normal graph first. in second step do it explicitely
        }

        @Override
        protected void ensureNotLoaded()
        {
            // skip check. we know what we are doing
        }

        public void doPostProcessing()
        {
            // re-create index to avoid bug as pickNode in locationIndex.prepare could be wrong while indexing if level is not taken into account and assumed to be 0 for pre-initialized graph            
            StopWatch sw = new StopWatch().start();
            int edges = getGraphHopperStorage().getAllEdges().getCount();
            setAlgorithmFactory(createPrepare());
            super.prepare();
            setLocationIndex(createLocationIndex(new RAMDirectory()));
            put("prepare.time", sw.stop().getTime());
            put("prepare.shortcuts", getGraphHopperStorage().getAllEdges().getCount() - edges);
        }
    }

    // creates properties file in the format key=value
    // Every value is one y-value in a separate diagram with an identical x-value for every Measurement.start call
    void start( CmdArgs args )
    {
        long importTook = args.getLong("graph.importTime", -1);
        put("graph.importTime", importTook);

        String graphLocation = args.get("graph.location", "");
        if (Helper.isEmpty(graphLocation))
            throw new IllegalStateException("no graph.location specified");

        String propLocation = args.get("measurement.location", "");
        if (Helper.isEmpty(propLocation))
            propLocation = "measurement" + new SimpleDateFormat("yyyy-MM-dd_HH_mm_ss").format(new Date()) + ".properties";

        seed = args.getLong("measurement.seed", 123);
        String gitCommit = args.get("measurement.gitinfo", "");
        int count = args.getInt("measurement.count", 5000);

        MeasureHopper hopper = new MeasureHopper();
        hopper.forDesktop();
        if (!hopper.load(graphLocation))
            throw new IllegalStateException("Cannot load existing levelgraph at " + graphLocation);

        GraphHopperStorage g = hopper.getGraphHopperStorage();
        if ("true".equals(g.getProperties().get("prepare.done")))
            throw new IllegalStateException("Graph has to be unprepared but wasn't!");

        String vehicleStr = args.get("graph.flagEncoders", "");
        StopWatch sw = new StopWatch().start();
        try
        {
            maxNode = hopper.getGraphHopperStorage().getNodes();
            printGraphDetails(g, vehicleStr);
            printLocationIndexQuery(g, hopper.getLocationIndex(), count);

            // Route via dijkstrabi. Normal routing takes a lot of time => smaller query number than CH
            // => values are not really comparable to routingCH as e.g. the mean distance etc is different            
            hopper.setCHEnable(false);
            printTimeOfRouteQuery(hopper, count / 20, "routing", vehicleStr, true);

            System.gc();

            // route via CH. do preparation before                        
            hopper.setCHEnable(true);
            hopper.doPostProcessing();
            printTimeOfRouteQuery(hopper, count, "routingCH", vehicleStr, true);
            printTimeOfRouteQuery(hopper, count, "routingCH_no_instr", vehicleStr, false);
            logger.info("store into " + propLocation);
        } catch (Exception ex)
        {
            logger.error("Problem while measuring " + graphLocation, ex);
            put("error", ex.toString());
        } finally
        {
            put("measurement.gitinfo", gitCommit);
            put("measurement.count", count);
            put("measurement.seed", seed);
            put("measurement.time", sw.stop().getTime());
            System.gc();
            put("measurement.totalMB", Helper.getTotalMB());
            put("measurement.usedMB", Helper.getUsedMB());
            try
            {
                store(new FileWriter(propLocation), "measurement finish, "
                        + new Date().toString() + ", " + Constants.BUILD_DATE);
            } catch (IOException ex)
            {
                logger.error("Problem while storing properties " + graphLocation + ", " + propLocation, ex);
            }
        }
    }

    private void printGraphDetails( GraphHopperStorage g, String vehicleStr )
    {
        // graph size (edge, node and storage size)
        put("graph.nodes", g.getNodes());
        put("graph.edges", g.getAllEdges().getCount());
        put("graph.sizeInMB", g.getCapacity() / Helper.MB);
        put("graph.encoder", vehicleStr);
    }

    private void printLocationIndexQuery( Graph g, final LocationIndex idx, int count )
    {
        count *= 2;
        final BBox bbox = g.getBounds();
        final double latDelta = bbox.maxLat - bbox.minLat;
        final double lonDelta = bbox.maxLon - bbox.minLon;
        final Random rand = new Random(seed);
        MiniPerfTest miniPerf = new MiniPerfTest()
        {
            @Override
            public int doCalc( boolean warmup, int run )
            {
                double lat = rand.nextDouble() * latDelta + bbox.minLat;
                double lon = rand.nextDouble() * lonDelta + bbox.minLon;
                int val = idx.findClosest(lat, lon, EdgeFilter.ALL_EDGES).getClosestNode();
//                if (!warmup && val >= 0)
//                    list.add(val);

                return val;
            }
        }.setIterations(count).start();

        print("location2id", miniPerf);
    }

    private void printTimeOfRouteQuery( final GraphHopper hopper, int count, String prefix,
                                        final String vehicle, final boolean withInstructions )
    {
        final Graph g = hopper.getGraphHopperStorage();
        final AtomicLong maxDistance = new AtomicLong(0);
        final AtomicLong minDistance = new AtomicLong(Long.MAX_VALUE);
        final AtomicLong distSum = new AtomicLong(0);
        final AtomicLong airDistSum = new AtomicLong(0);
        final AtomicInteger failedCount = new AtomicInteger(0);
        final DistanceCalc distCalc = new DistanceCalcEarth();

//        final AtomicLong extractTimeSum = new AtomicLong(0);
//        final AtomicLong calcPointsTimeSum = new AtomicLong(0);
//        final AtomicLong calcDistTimeSum = new AtomicLong(0);
//        final AtomicLong tmpDist = new AtomicLong(0);
        final Random rand = new Random(seed);
        final NodeAccess na = g.getNodeAccess();
        MiniPerfTest miniPerf = new MiniPerfTest()
        {
            @Override
            public int doCalc( boolean warmup, int run )
            {
                int from = rand.nextInt(maxNode);
                int to = rand.nextInt(maxNode);
                double fromLat = na.getLatitude(from);
                double fromLon = na.getLongitude(from);
                double toLat = na.getLatitude(to);
                double toLon = na.getLongitude(to);
                GHRequest req = new GHRequest(fromLat, fromLon, toLat, toLon).
                        setWeighting("fastest").
                        setVehicle(vehicle);
                req.getHints().put("instructions", withInstructions);
                GHResponse res;
                try
                {
                    res = hopper.route(req);
                } catch (Exception ex)
                {
                    // 'not found' can happen if import creates more than one subnetwork
                    throw new RuntimeException("Error while calculating route! "
                            + "nodes:" + from + " -> " + to + ", request:" + req, ex);
                }

                if (res.hasErrors())
                {
                    if (!warmup)
                        failedCount.incrementAndGet();

                    if (!res.getErrors().get(0).getMessage().toLowerCase().contains("not found"))
                        logger.error("errors should NOT happen in Measurement! " + req + " => " + res.getErrors());

                    return 0;
                }

                if (!warmup)
                {
                    long dist = (long) res.getDistance();
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

                return res.getPoints().getSize();
            }
        }.setIterations(count).start();

        count -= failedCount.get();
        put(prefix + ".failedCount", failedCount.get());
        put(prefix + ".distanceMin", minDistance.get());
        put(prefix + ".distanceMean", (float) distSum.get() / count);
        put(prefix + ".airDistanceMean", (float) airDistSum.get() / count);
        put(prefix + ".distanceMax", maxDistance.get());

//        put(prefix + ".extractTime", (float) extractTimeSum.get() / count / 1000000f);
//        put(prefix + ".calcPointsTime", (float) calcPointsTimeSum.get() / count / 1000000f);
//        put(prefix + ".calcDistTime", (float) calcDistTimeSum.get() / count / 1000000f);
        print(prefix, miniPerf);
    }

    void print( String prefix, MiniPerfTest perf )
    {
        logger.info(perf.getReport());
        put(prefix + ".sum", perf.getSum());
//        put(prefix+".rms", perf.getRMS());
        put(prefix + ".min", perf.getMin());
        put(prefix + ".mean", perf.getMean());
        put(prefix + ".max", perf.getMax());
    }

    void put( String key, Object val )
    {
        // convert object to string to make serialization possible
        properties.put(key, "" + val);
    }

    private void store( FileWriter fileWriter, String comment ) throws IOException
    {
        fileWriter.append("#" + comment + "\n");
        for (Entry<String, String> e : properties.entrySet())
        {
            fileWriter.append(e.getKey());
            fileWriter.append("=");
            fileWriter.append(e.getValue());
            fileWriter.append("\n");
        }
        fileWriter.flush();
    }
}
