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

package com.graphhopper;

import com.graphhopper.api.GraphHopperWeb;
import com.graphhopper.config.CHProfile;
import com.graphhopper.config.Profile;
import com.graphhopper.routing.ev.Subnetwork;
import com.graphhopper.routing.util.DefaultSnapFilter;
import com.graphhopper.routing.util.EdgeFilter;
import com.graphhopper.routing.weighting.Weighting;
import com.graphhopper.storage.index.LocationIndex;
import com.graphhopper.storage.index.Snap;
import com.graphhopper.util.MiniPerfTest;
import com.graphhopper.util.PMap;
import com.graphhopper.util.shapes.BBox;
import com.graphhopper.util.shapes.GHPoint;
import okhttp3.Call;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.ResponseBody;
import org.openjdk.jmh.annotations.*;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.concurrent.TimeUnit;

@State(Scope.Benchmark)
public class RouteBenchmark {
    private static final String PROFILE = "car";
    private BBox bBox;

    @Param({"bayern-220101.osm.pbf"})
    String pbf;

    @Param({"bayern-220101-gh"})
    String graph_folder;
    private GraphHopper hopper;
    private GraphHopperWeb ghClient;
    private OkHttpClient client;

    @Setup
    public void setup() {
        PMap args = new PMap();
        String vehicleStr = "car";
        String weightingStr = "fastest";
        GraphHopperConfig ghConfig = new GraphHopperConfig(args);
        ghConfig.putObject("datareader.file", pbf);
        ghConfig.putObject("graph.location", graph_folder);

        ghConfig.setProfiles(Arrays.asList(
                new Profile(PROFILE).setVehicle(vehicleStr).setWeighting(weightingStr).setTurnCosts(false)
        ));
        hopper = new GraphHopper();
        hopper.init(ghConfig);
        hopper.importOrLoad();

        ghClient = new GraphHopperWeb("http://localhost:8989/route");
        client = ghClient.getDownloader();

        // Munich
        bBox = new BBox(
                11.2966,
                11.8844,
                47.9917,
                48.3014
        );
    }

    @State(Scope.Thread)
    public static class RouteState {
        @Param({"5"})
        int numPoints;

        GHRequest ghRequest;
        long checksum;

        @Setup
        public void setup(RouteBenchmark myBenchmark) {
            ghRequest = new GHRequest();
            ghRequest.setProfile(PROFILE);
            ghRequest.putHint("instructions", false);
            ghRequest.putHint("calc_points", false);
            ghRequest.putHint("ch.disable", true);
            long seed = 123;
            Random rnd = new Random(seed);
            List<GHPoint> points = generateRandomButValidPoints(myBenchmark.hopper, myBenchmark.bBox, PROFILE, numPoints, rnd);
            ghRequest.setPoints(points);
        }

        @TearDown(Level.Iteration)
        public void finish() {
            LoggerFactory.getLogger(RouteBenchmark.class).info("checksum: " + checksum);
        }
    }

    @Benchmark
    @BenchmarkMode({Mode.AverageTime})
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    public double measureInfoHttp(RouteState state) throws IOException {
        Call call = client.newCall(new Request.Builder().url("http://localhost:8989/info").build());
        ResponseBody body = call.execute().body();
        double result = state.checksum = body.contentLength();
        System.out.println(body.string());
        body.close();
        return result;
    }

    @Benchmark
    @BenchmarkMode({Mode.AverageTime})
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    public double measureRouteHttp(RouteState state) {
        GHResponse response = ghClient.route(state.ghRequest);
        if (response.hasErrors())
            throw new IllegalStateException("There should be no errors in Measurement, " + response.getErrors().toString());
        return state.checksum = (long) (response.getBest().getRouteWeight() + response.getBest().getDistance() + response.getBest().getTime());
    }

    @Benchmark
    @BenchmarkMode({Mode.AverageTime})
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    public double measureRoute(RouteState state) {
        GHResponse response = hopper.route(state.ghRequest);
        if (response.hasErrors())
            throw new IllegalStateException("There should be no errors in Measurement, " + response.getErrors().toString());
        return state.checksum = (long) (response.getBest().getRouteWeight() + response.getBest().getDistance() + response.getBest().getTime());
    }

    private static List<GHPoint> generateRandomButValidPoints(GraphHopper hopper, BBox bBox, String profileName, int numPoints, Random rnd) {
        LocationIndex index = hopper.getLocationIndex();
        Weighting weighting = hopper.createWeighting(hopper.getProfile(profileName), new PMap());
        final EdgeFilter edgeFilter = new DefaultSnapFilter(weighting, hopper.getEncodingManager().getBooleanEncodedValue(Subnetwork.key(profileName)));

        int numTries = 10 * numPoints;
        int numAttempts = 0;
        List<GHPoint> points = new ArrayList<>(numPoints);
        while (points.size() < numPoints) {
            numAttempts++;
            double lat = bBox.minLat + rnd.nextDouble() * (bBox.maxLat - bBox.minLat);
            double lon = bBox.minLon + rnd.nextDouble() * (bBox.maxLon - bBox.minLon);
            Snap snap = index.findClosest(lat, lon, edgeFilter);
            if (snap.isValid() && snap.getQueryDistance() < 1000) {
                points.add(new GHPoint(lat, lon));
            }
            if (numAttempts > numTries) {
                throw new IllegalStateException("Could not find " + numPoints + " valid points after " + numTries + " tries.");
            }
        }
        return points;
    }

    public static void main(String[] args) {
        // used to run/debug manually without JMH
        RouteBenchmark b = new RouteBenchmark();
        b.graph_folder = "bayern-220101-gh";
        b.setup();
        RouteState s = new RouteState();
        s.numPoints = 2;
        s.setup(b);

        MiniPerfTest t1 = new MiniPerfTest().setIterations(100).start((warmup, run) -> (int) b.measureRoute(s));
        System.out.println(t1.getReport());

        MiniPerfTest t2 = new MiniPerfTest().setIterations(100).start((warmup, run) -> (int) b.measureRouteHttp(s));
        System.out.println(t2.getReport());
    }
}
