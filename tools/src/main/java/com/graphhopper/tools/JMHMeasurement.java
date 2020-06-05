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
import com.graphhopper.GraphHopperConfig;
import com.graphhopper.config.CHProfile;
import com.graphhopper.config.Profile;
import com.graphhopper.reader.osm.GraphHopperOSM;
import com.graphhopper.storage.NodeAccess;
import com.graphhopper.util.EdgeExplorer;
import com.graphhopper.util.EdgeIterator;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.util.Arrays;
import java.util.Random;
import java.util.concurrent.TimeUnit;

public class JMHMeasurement {

    public static void main(String[] args) throws RunnerException {
        Options opt = new OptionsBuilder()
                .include(JMHMeasurement.class.getSimpleName())
                // todo: here we can use the args
                .param("osmFile", "my_map.osm.pbf")
                .param("graph.location", "my_map-jmh-gh")
                .forks(1)
                .build();
        new Runner(opt).run();
    }

    @Benchmark
    @BenchmarkMode({Mode.AverageTime, Mode.SampleTime})
    @OutputTimeUnit(TimeUnit.MICROSECONDS)
    public int edgeTraversal(MyState state) {
        EdgeIterator iter = state.explorer.setBaseNode(state.rnd.nextInt(state.hopper.getGraphHopperStorage().getNodes()));
        int sum = 0;
        while (iter.next())
            sum += iter.getAdjNode();
        return sum;
    }

    @Benchmark
    @BenchmarkMode({Mode.AverageTime, Mode.SampleTime})
    @OutputTimeUnit(TimeUnit.MICROSECONDS)
    public long routing(MyState state) {
        int from = state.rnd.nextInt(state.hopper.getGraphHopperStorage().getNodes());
        int to = state.rnd.nextInt(state.hopper.getGraphHopperStorage().getNodes());
        NodeAccess na = state.hopper.getGraphHopperStorage().getNodeAccess();
        GHRequest req = new GHRequest(na.getLat(from), na.getLon(from), na.getLat(to), na.getLon(to));
        req.setProfile("profile");
        GHResponse res = state.hopper.route(req);
        if (res.hasErrors())
            System.out.println(res.getErrors().toString());
        return res.getBest().getTime();
    }

    // if we ever refactored the Measurement class to use JMH the first step would probably be to establish such a State (like)
    // object that contains all the 'environment' used in the different tests and then split the code into separate
    // methods. we'd also need a way to store the results so we can later write them to a file.
    // todo: re-read the docs about Level,Scope,State and all this
    @State(Scope.Thread)
    public static class MyState {
        @Param("my_map.osm.pbf")
        private String osmFile;

        @Param({"my_map-gh"})
        private String graphLocation;

        private GraphHopper hopper;
        private EdgeExplorer explorer;
        private Random rnd;

        @Setup(Level.Iteration)
        public void setup() {
            System.out.println("--- setup ---");
            GraphHopperConfig ghConfig = new GraphHopperConfig();
            ghConfig.setProfiles(Arrays.asList(
                    new Profile("profile").setVehicle("car").setWeighting("fastest")
            ));
            ghConfig.setCHProfiles(Arrays.asList(
                    new CHProfile("profile")
            ));
            ghConfig.putObject("graph.flag_encoders", "car");
            ghConfig.putObject("datareader.file", osmFile);
            ghConfig.putObject("graph.location", graphLocation);
            hopper = new GraphHopperOSM();
            hopper.init(ghConfig);
            hopper.importOrLoad();
            explorer = hopper.getGraphHopperStorage().getCHGraph("profile").createEdgeExplorer();
            rnd = new Random(123);
        }
    }
}
