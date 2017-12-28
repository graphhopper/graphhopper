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
import com.graphhopper.util.StopWatch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
        cmdArgs.put("graph.flag_encoders", "car|turn_costs=true");
//        cmdArgs.put("graph.flag_encoders", "car");
        cmdArgs.put("prepare.ch.weightings", "fastest");
//        cmdArgs.put("prepare.ch.weightings", "no");
        graphHopper.init(cmdArgs);

        StopWatch sw = new StopWatch();
        sw.start();
        graphHopper.importOrLoad();
        sw.stop();
        LOGGER.info("Import and preparation took {}s", sw.getTime() / 1000);

        long seed = 123;
        Graph g = graphHopper.getGraphHopperStorage();
        final int numNodes = g.getNodes();
        final NodeAccess nodeAccess = g.getNodeAccess();
        final Random random = new Random(seed);

        MiniPerfTest miniPerfTest = new MiniPerfTest() {
            @Override
            public int doCalc(boolean warmup, int run) {
                int from = random.nextInt(numNodes);
                int to = random.nextInt(numNodes);
                double fromLat = nodeAccess.getLat(from);
                double fromLon = nodeAccess.getLon(from);
                double toLat = nodeAccess.getLat(to);
                double toLon = nodeAccess.getLon(to);
                GHRequest req = new GHRequest(fromLat, fromLon, toLat, toLon);
                GHResponse route = graphHopper.route(req);
                return route.getErrors().size();
            }
        };
        final int iterations = 5_000;
        miniPerfTest.setIterations(iterations).start();
        LOGGER.info("Average query time: {}ms", miniPerfTest.getMean());
        graphHopper.close();
    }

}
