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
package com.graphhopper.matching.cli;

import com.graphhopper.GHRequest;
import com.graphhopper.GHResponse;
import com.graphhopper.GraphHopper;
import com.graphhopper.matching.MapMatching;
import com.graphhopper.matching.MatchResult;
import com.graphhopper.reader.osm.GraphHopperOSM;
import com.graphhopper.routing.AlgorithmOptions;
import com.graphhopper.routing.util.*;
import com.graphhopper.storage.GraphHopperStorage;
import com.graphhopper.storage.index.LocationIndexTree;
import com.graphhopper.util.*;
import com.graphhopper.util.shapes.BBox;
import com.graphhopper.util.shapes.GHPoint;
import io.dropwizard.cli.Command;
import io.dropwizard.setup.Bootstrap;
import net.sourceforge.argparse4j.inf.Namespace;
import net.sourceforge.argparse4j.inf.Subparser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Random;
import java.util.TreeMap;

/**
 * @author Peter Karisch
 * @author kodonnell
 */
public class MeasurementCommand extends Command {
    private static final Logger logger = LoggerFactory.getLogger(MeasurementCommand.class);
    private final Map<String, String> properties = new TreeMap<>();
    private BBox bbox;
    private DistanceCalcEarth distCalc = new DistanceCalcEarth();
    private long seed;
    private int count;

    public MeasurementCommand() {
        super("measurement", "runs performance tests on the imported graph");
    }

    @Override
    public void configure(Subparser subparser) {
        subparser.addArgument("outfile")
                .type(File.class)
                .required(true)
                .help("output file name for the measurement results");
        subparser.addArgument("--seed")
                .type(Long.class)
                .required(false)
                .setDefault(123L)
                .help("random seed");
        subparser.addArgument("--count")
                .type(Integer.class)
                .required(false)
                .setDefault(100)
                .help("number of operations to perform");
    }

    @Override
    public void run(Bootstrap bootstrap, Namespace args) {
        // read and initialize arguments:
        CmdArgs graphHopperConfiguration = new CmdArgs();
        graphHopperConfiguration.put("graph.location", "graph-cache");
        seed = args.getLong("seed");
        count = args.getInt("count");

        GraphHopper graphHopper = new GraphHopperOSM();
        graphHopper.init(graphHopperConfiguration).forDesktop();
        graphHopper.getCHFactoryDecorator().setEnabled(false);
        graphHopper.getCHFactoryDecorator().setDisablingAllowed(true);
        graphHopper.importOrLoad();
        
        // and map-matching stuff
        GraphHopperStorage graph = graphHopper.getGraphHopperStorage();
        bbox = graph.getBounds();
        LocationIndexTree locationIndex = (LocationIndexTree) graphHopper.getLocationIndex();
        AlgorithmOptions algoOpts = AlgorithmOptions.start()
                .maxVisitedNodes((int) 1e20)
                .build();
        MapMatching mapMatching = new MapMatching(graphHopper, algoOpts);
        
        // start tests:
        StopWatch sw = new StopWatch().start();
        try {
            printLocationIndexMatchQuery(locationIndex);
            printTimeOfMapMatchQuery(graphHopper, mapMatching);
            System.gc();
        } catch (Exception ex) {
            logger.error("Problem while measuring", ex);
            properties.put("error", "" + ex.toString());
        } finally {
            properties.put("measurement.count", "" + count);
            properties.put("measurement.seed", "" + seed);
            properties.put("measurement.time", "" + sw.stop().getMillis());
            System.gc();
            properties.put("measurement.totalMB", "" + Helper.getTotalMB());
            properties.put("measurement.usedMB", "" + Helper.getUsedMB());
            try {
                FileWriter fileWriter = new FileWriter(args.<File>get("outfile"));
                for (Entry<String, String> e : properties.entrySet()) {
                    fileWriter.append(e.getKey());
                    fileWriter.append("=");
                    fileWriter.append(e.getValue());
                    fileWriter.append("\n");
                }
                fileWriter.flush();
            } catch (IOException ex) {
                logger.error(
                        "Problem while writing measurements", ex);
            }
        }
    }

    /**
     * Test the performance of finding candidate points for the index (which is run for every GPX
     * entry).
     * 
     */
    private void printLocationIndexMatchQuery(final LocationIndexTree idx) {
        final double latDelta = bbox.maxLat - bbox.minLat;
        final double lonDelta = bbox.maxLon - bbox.minLon;
        final Random rand = new Random(seed);
        MiniPerfTest miniPerf = new MiniPerfTest() {
            @Override
            public int doCalc(boolean warmup, int run) {
                double lat = rand.nextDouble() * latDelta + bbox.minLat;
                double lon = rand.nextDouble() * lonDelta + bbox.minLon;
                return idx.findNClosest(lat, lon, EdgeFilter.ALL_EDGES, rand.nextDouble() * 500).size();
            }
        }.setIterations(count).start();
        print("location_index_match", miniPerf);
    }

    /**
     * Test the time taken for map matching on random routes. Note that this includes the index
     * lookups (previous tests), so will be affected by those. Otherwise this is largely testing the
     * routing and HMM performance.
     */
    private void printTimeOfMapMatchQuery(final GraphHopper hopper, final MapMatching mapMatching) {

        // pick random start/end points to create a route, then pick random points from the route,
        // and then run the random points through map-matching.
        final double latDelta = bbox.maxLat - bbox.minLat;
        final double lonDelta = bbox.maxLon - bbox.minLon;
        final Random rand = new Random(seed);
        MiniPerfTest miniPerf = new MiniPerfTest() {
            @Override
            public int doCalc(boolean warmup, int run) {
                // keep going until we find a path (which we may not for certain start/end points)
                while (true) {
                    // create random points and find route between:
                    double lat0 = bbox.minLat + rand.nextDouble() * latDelta;
                    double lon0 = bbox.minLon + rand.nextDouble() * lonDelta;
                    double lat1 = bbox.minLat + rand.nextDouble() * latDelta;
                    double lon1 = bbox.minLon + rand.nextDouble() * lonDelta;
                    GHResponse r = hopper.route(new GHRequest(lat0, lon0, lat1, lon1));

                    // if found, use it for map matching:
                    if (!r.hasErrors()) {
                        long time = 0;
                        double sampleProportion = rand.nextDouble();
                        GHPoint prev = null;
                        List<GPXEntry> mock = new ArrayList<>();
                        PointList points = r.getBest().getPoints();
                        // loop through points and add (approximately) sampleProportion of them:
                        for (GHPoint p : points) {
                            if (null != prev && rand.nextDouble() < sampleProportion) {
                                // estimate a reasonable time taken since the last point, so we
                                // can give the GPXEntry a time. Use the distance between the
                                // points and a random speed to estimate a time.
                                double dx = distCalc.calcDist(prev.lat, prev.lon, p.lat, p.lon);
                                double speedKPH = rand.nextDouble() * 100;
                                double dt = (dx / 1000) / speedKPH * 3600000;
                                time += (long) dt;
                                // randomise the point lat/lon (i.e. so it's not
                                // exactly on the route):
                                GHPoint randomised = distCalc.projectCoordinate(p.lat, p.lon,
                                        20 * rand.nextDouble(), 360 * rand.nextDouble());
                                mock.add(new GPXEntry(randomised, time));
                            }
                            prev = p;
                        }
                        // now match, provided there are enough points
                        if (mock.size() > 2) {
                            MatchResult match = mapMatching.doWork(mock);
                            // return something non-trivial, to avoid JVM optimizing away
                            return match.getEdgeMatches().size();
                        }
                    }
                }
            }
        }.setIterations(count).start();
        print("map_match", miniPerf);
    }

    private void print(String prefix, MiniPerfTest perf) {
        logger.info(prefix + ": " + perf.getReport());
        properties.put(prefix + ".sum", "" + (Object) perf.getSum());
        properties.put(prefix + ".min", "" + (Object) perf.getMin());
        properties.put(prefix + ".mean", "" + (Object) perf.getMean());
        properties.put(prefix + ".max", "" + (Object) perf.getMax());
    }

}
