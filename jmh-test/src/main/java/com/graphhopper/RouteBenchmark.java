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
import com.graphhopper.config.Profile;
import com.graphhopper.resources.RouteResource;
import com.graphhopper.routing.ProfileResolver;
import com.graphhopper.routing.ev.DecimalEncodedValue;
import com.graphhopper.routing.ev.Subnetwork;
import com.graphhopper.routing.util.AllEdgesIterator;
import com.graphhopper.routing.util.DefaultSnapFilter;
import com.graphhopper.routing.util.EdgeFilter;
import com.graphhopper.routing.util.FlagEncoder;
import com.graphhopper.routing.weighting.Weighting;
import com.graphhopper.storage.index.LocationIndex;
import com.graphhopper.storage.index.Snap;
import com.graphhopper.util.PMap;
import com.graphhopper.util.shapes.BBox;
import com.graphhopper.util.shapes.GHPoint;
import okhttp3.Call;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.glassfish.jersey.internal.inject.AbstractBinder;
import org.glassfish.jersey.server.ResourceConfig;
import org.glassfish.jersey.servlet.ServletContainer;
import org.openjdk.jmh.annotations.*;
import org.slf4j.LoggerFactory;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
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
    private Server jettyServer;

    @Setup
    public void setup() throws Exception {
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

        // Munich
        bBox = new BBox(
                11.2966,
                11.8844,
                47.9917,
                48.3014
        );

        jettyServer = new Server(8989);
        startServer(jettyServer, hopper);
    }

    @TearDown
    public void tearDown() throws Exception {
        jettyServer.stop();
        while (!jettyServer.isStopped()) {
        }
        jettyServer.destroy();
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
    public double measureRouteHttp(RouteState state) {
        GraphHopperWeb client = new GraphHopperWeb("http://localhost:8989/route");
        client.setPostRequest(false);
        GHResponse response = client.route(state.ghRequest);
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

    @Benchmark
    @BenchmarkMode({Mode.AverageTime})
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    public double measureSumGraph(RouteState state) {
        FlagEncoder encoder = hopper.getEncodingManager().getEncoder("car");
        DecimalEncodedValue speedEnc = encoder.getAverageSpeedEnc();
        double result = 0;
        AllEdgesIterator iter = hopper.getGraphHopperStorage().getAllEdges();
        while (iter.next()) {
            double speed = iter.get(speedEnc);
            if (Double.isInfinite(speed))
                continue;
            result += ((iter.getEdge() % 2 == 0) ? -1.0 : +1.0) * speed;
        }
        return state.checksum = (long) result;
    }

    @Benchmark
    @BenchmarkMode({Mode.AverageTime})
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    public double measureSumGraphHttp(RouteState state) throws IOException {
        OkHttpClient client = new OkHttpClient.Builder().build();
        Call call = client.newCall(new Request.Builder().url("http://localhost:8989/sumgraph").build());
        return state.checksum = (long) Double.parseDouble(call.execute().body().string());
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

    public static void startServer(Server jettyServer, GraphHopper hopper) throws Exception {
        ServletContextHandler handler = new ServletContextHandler(ServletContextHandler.SESSIONS);
        handler.setContextPath("/");
        ResourceConfig rc = new ResourceConfig();
        rc.register(new AbstractBinder() {
            @Override
            public void configure() {
                bind(new RouteResource(hopper,
                        new ProfileResolver(hopper.getEncodingManager(), hopper.getProfiles(), hopper.getCHPreparationHandler().getCHProfiles(), hopper.getLMPreparationHandler().getLMProfiles()),
                        hopper.hasElevation())).to(RouteResource.class);
            }
        });
        rc.register(RouteResource.class);

        rc.register(new AbstractBinder() {
            @Override
            public void configure() {
                bind(new SumGraphResource(hopper)).to(SumGraphResource.class);
            }
        });
        rc.register(SumGraphResource.class);

        handler.addServlet(new ServletHolder(new ServletContainer(rc)), "/*");
        jettyServer.setHandler(handler);
        jettyServer.start();
    }

    @Path("sumgraph")
    public static class SumGraphResource {

        final GraphHopper hopper;

        public SumGraphResource(GraphHopper hopper) {
            this.hopper = hopper;
        }

        @GET
        @Produces({MediaType.APPLICATION_JSON})
        public double sumGraph() {
            FlagEncoder encoder = hopper.getEncodingManager().getEncoder("car");
            DecimalEncodedValue speedEnc = encoder.getAverageSpeedEnc();
            double result = 0;
            AllEdgesIterator iter = hopper.getGraphHopperStorage().getAllEdges();
            while (iter.next()) {
                double speed = iter.get(speedEnc);
                if (Double.isInfinite(speed))
                    continue;
                result += ((iter.getEdge() % 2 == 0) ? -1.0 : +1.0) * speed;
            }
            return result;
        }
    }
}
