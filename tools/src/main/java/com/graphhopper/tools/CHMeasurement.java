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
import com.graphhopper.storage.Graph;
import com.graphhopper.storage.NodeAccess;
import com.graphhopper.util.CmdArgs;
import com.graphhopper.util.MiniPerfTest;
import com.graphhopper.util.Parameters;
import com.graphhopper.util.StopWatch;
import com.graphhopper.util.exceptions.ConnectionNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Measurement class used to analyze different contraction hierarchy parameters.
 */
public class CHMeasurement {
    // todo: make this class more useful, for example use command line arguments to be able to automatically run tests
    // for larger parameter ranges
    private static final Logger LOGGER = LoggerFactory.getLogger(CHMeasurement.class);

    public static void main(String[] args) {
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
        graphHopper.getCHFactoryDecorator().setDisablingAllowed(true);
        graphHopper.init(cmdArgs);
        // remove previous data
        graphHopper.clean();

        StopWatch sw = new StopWatch();
        sw.start();
        graphHopper.importOrLoad();
        sw.stop();
        LOGGER.info("Import and preparation took {}s", sw.getTime() / 1000);

        long seed = System.nanoTime();
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
                long start = System.nanoTime();
                GHResponse chRoute = graphHopper.route(req);
                if (!warmup)
                    chTime += (System.nanoTime() - start);

                req.getHints().put(Parameters.CH.DISABLE, true);
                start = System.nanoTime();
                GHResponse nonChRoute = graphHopper.route(req);
                if (!warmup)
                    noChTime += System.nanoTime() - start;

                getRealErrors(chRoute);

                if (!getRealErrors(chRoute).isEmpty() || !getRealErrors(nonChRoute).isEmpty()) {
                    LOGGER.warn("there were errors: \n with CH: {} \n without CH: {}", chRoute.getErrors(), nonChRoute.getErrors());
                    return chRoute.getErrors().size();
                }

                if (chRoute.hasErrors() || nonChRoute.hasErrors()) {
                    // probably some connection not found error
                    return 0;
                }

                if (!chRoute.getBest().getPoints().equals(nonChRoute.getBest().getPoints())) {
                    // todo: this test finds some differences that are most likely due to rounding issues (the weights
                    // are very similar, and the paths have minor differences (with ch the route weight seems to be smaller if different)
                    LOGGER.warn("error: found different points for query from {} to {}, {}",
                            req.getPoints().get(0).toShortString(), req.getPoints().get(1).toShortString(),
                            String.format("route weight: %.2f vs. %.2f (diff = %.4f)",
                                    chRoute.getBest().getRouteWeight(), nonChRoute.getBest().getRouteWeight(),
                                    (chRoute.getBest().getRouteWeight() - nonChRoute.getBest().getRouteWeight())));
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
                long start = System.nanoTime();
                GHResponse route = graphHopper.route(req);
                if (!warmup)
                    queryTime += System.nanoTime() - start;
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
