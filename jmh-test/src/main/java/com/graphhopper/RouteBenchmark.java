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
    @Param({"10000000"})
    int size;
    private int[] array;
    private Server jettyServer;

    @Setup
    public void setup() throws Exception {
        Random rnd = new Random(123);
        array = new int[size];
        for (int i = 0; i < array.length; ++i)
            array[i] = rnd.nextInt(100);

        jettyServer = new Server(8989);
        startServer(jettyServer, array);
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
    public double measureSumArray(SumState state) {
        double result = sumArray(array);
        return state.checksum = result;
    }

    @Benchmark
    @BenchmarkMode({Mode.AverageTime})
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    public double measureSumArrayHttp(SumState state) throws IOException {
        OkHttpClient client = new OkHttpClient.Builder().build();
        Call call = client.newCall(new Request.Builder().url("http://localhost:8989/sumarray").build());
        return state.checksum = Double.parseDouble(call.execute().body().string());
    }

    public static void startServer(Server jettyServer, int[] array) throws Exception {
        ServletContextHandler handler = new ServletContextHandler(ServletContextHandler.SESSIONS);
        handler.setContextPath("/");
        ResourceConfig rc = new ResourceConfig();
        rc.register(new AbstractBinder() {
            @Override
            public void configure() {
                bind(new SumArrayResource(array)).to(SumArrayResource.class);
            }
        });
        rc.register(SumArrayResource.class);

        handler.addServlet(new ServletHolder(new ServletContainer(rc)), "/*");
        jettyServer.setHandler(handler);
        jettyServer.start();
    }

    @Path("sumarray")
    public static class SumArrayResource {
        final int[] array;

        public SumArrayResource(int[] array) {
            this.array = array;
        }

        @GET
        @Produces({MediaType.APPLICATION_JSON})
        public double sumArray() {
            return RouteBenchmark.sumArray(array);
        }
    }

    private static double sumArray(int[] array) {
        double result = 0;
        for (int i = 0; i < array.length; i++)
            result += array[i] * ((i % 2 == 0) ? 1 : -1);
        return result;
    }
}
