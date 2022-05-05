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

import com.graphhopper.routing.ev.DecimalEncodedValue;
import com.graphhopper.routing.util.AllEdgesIterator;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.routing.util.FlagEncoder;
import com.graphhopper.storage.BaseGraph;
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
import java.util.Random;
import java.util.concurrent.TimeUnit;

@State(Scope.Benchmark)
public class RouteBenchmark {
    @Param({"5000000"})
    int numEdges;
    private BaseGraph baseGraph;
    private Server jettyServer;

    @Setup
    public void setup() throws Exception {
        EncodingManager em = EncodingManager.create("car");
        FlagEncoder encoder = em.getEncoder("car");
        baseGraph = new BaseGraph.Builder(em).create();
        Random rnd = new Random(123);
        for (int i = 0; i < numEdges; i++)
            baseGraph.edge(i, i + 1).set(encoder.getAverageSpeedEnc(), rnd.nextDouble() * 100);

        jettyServer = new Server(8989);
        startServer(jettyServer, baseGraph);
    }

    @TearDown
    public void tearDown() throws Exception {
        jettyServer.stop();
        while (!jettyServer.isStopped()) {
        }
        jettyServer.destroy();
    }

    @State(Scope.Thread)
    public static class SumState {
        double checksum;

        @TearDown(Level.Iteration)
        public void finish() {
            LoggerFactory.getLogger(RouteBenchmark.class).info("checksum: " + checksum);
        }
    }

    @Benchmark
    @BenchmarkMode({Mode.AverageTime})
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    public double measureSumGraph(SumState state) {
        double result = 0;
        AllEdgesIterator iter = baseGraph.getAllEdges();
        while (iter.next()) {
            double diff = iter.getBaseNode() - iter.getAdjNode();
            result += ((iter.getEdge() % 2 == 0) ? -1.0 : +1.0) * diff;
        }
        return state.checksum = result;
    }

    @Benchmark
    @BenchmarkMode({Mode.AverageTime})
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    public double measureSumGraphHttp(SumState state) throws IOException {
        OkHttpClient client = new OkHttpClient.Builder().build();
        Call call = client.newCall(new Request.Builder().url("http://localhost:8989/sumgraph").build());
        return state.checksum = Double.parseDouble(call.execute().body().string());
    }

    @Benchmark
    @BenchmarkMode({Mode.AverageTime})
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    public double measureSumFlags(SumState state) {
        double result = 0;
        AllEdgesIterator iter = baseGraph.getAllEdges();
        while (iter.next()) {
            int flag = iter.getFlags().ints[0];
            if (Double.isInfinite(flag))
                continue;
            result += ((iter.getEdge() % 2 == 0) ? -1.0 : +1.0) * flag;
        }
        return state.checksum = result;
    }

    @Benchmark
    @BenchmarkMode({Mode.AverageTime})
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    public double measureSumFlagsHttp(SumState state) throws IOException {
        OkHttpClient client = new OkHttpClient.Builder().build();
        Call call = client.newCall(new Request.Builder().url("http://localhost:8989/sumflags").build());
        return state.checksum = Double.parseDouble(call.execute().body().string());
    }

    public static void startServer(Server jettyServer, BaseGraph baseGraph) throws Exception {
        ServletContextHandler handler = new ServletContextHandler(ServletContextHandler.SESSIONS);
        handler.setContextPath("/");
        ResourceConfig rc = new ResourceConfig();
        rc.register(new AbstractBinder() {
            @Override
            public void configure() {
                bind(new SumGraphResource(baseGraph)).to(SumGraphResource.class);
            }
        });
        rc.register(SumGraphResource.class);

        rc.register(new AbstractBinder() {
            @Override
            public void configure() {
                bind(new SumFlagsResource(baseGraph)).to(SumFlagsResource.class);
            }
        });
        rc.register(SumFlagsResource.class);

        handler.addServlet(new ServletHolder(new ServletContainer(rc)), "/*");
        jettyServer.setHandler(handler);
        jettyServer.start();
    }

    @Path("sumgraph")
    public static class SumGraphResource {
        final BaseGraph baseGraph;

        public SumGraphResource(BaseGraph baseGraph) {
            this.baseGraph = baseGraph;
        }

        @GET
        @Produces({MediaType.APPLICATION_JSON})
        public double sumGraph() {
            double result = 0;
            AllEdgesIterator iter = baseGraph.getAllEdges();
            while (iter.next()) {
                int diff = iter.getBaseNode() - iter.getAdjNode();
                result += ((iter.getEdge() % 2 == 0) ? -1.0 : +1.0) * diff;
            }
            return result;
        }
    }

    @Path("sumflags")
    public static class SumFlagsResource {

        final BaseGraph baseGraph;

        public SumFlagsResource(BaseGraph baseGraph) {
            this.baseGraph = baseGraph;
        }

        @GET
        @Produces({MediaType.APPLICATION_JSON})
        public double sumGraph() {
            double result = 0;
            AllEdgesIterator iter = baseGraph.getAllEdges();
            while (iter.next()) {
                int flag = iter.getFlags().ints[0];
                if (Double.isInfinite(flag))
                    continue;
                result += ((iter.getEdge() % 2 == 0) ? -1.0 : +1.0) * flag;
            }
            return result;
        }
    }
}
