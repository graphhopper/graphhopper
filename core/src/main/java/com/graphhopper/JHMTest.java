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

import com.graphhopper.routing.ev.*;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.storage.DAType;
import com.graphhopper.storage.DataAccess;
import com.graphhopper.storage.RAMDirectory;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.annotations.State;

import java.util.Random;
import java.util.concurrent.TimeUnit;

import static java.util.concurrent.TimeUnit.SECONDS;

public class JHMTest {
    private static final int EDGES = 1_000_000;
    private static final int BYTES_PER_EDGE = 12;
    private static final int EVS = 96;

    @Benchmark
    @BenchmarkMode({Mode.AverageTime, Mode.SampleTime})
    @Warmup(iterations = 3, time = 10, timeUnit = SECONDS)
    @Measurement(iterations = 3, time = 10, timeUnit = SECONDS)
    @Fork(value = 1, warmups = 1)
    @OutputTimeUnit(TimeUnit.MICROSECONDS)
    public double testEvs(MyState state) {
        double result = 0;
        for (int j = 0; j < 100; j++) {
            int edge = state.rnd.nextInt(EDGES);
            for (int i = 0; i < state.evs.length; i++) {
                result += state.evs[i].getBool(false, edge, state.edgeIntAccess) ? 1 : 0;
            }
        }
        return result;
    }

    @Benchmark
    @BenchmarkMode({Mode.AverageTime, Mode.SampleTime})
    @Warmup(iterations = 3, time = 10, timeUnit = SECONDS)
    @Measurement(iterations = 3, time = 10, timeUnit = SECONDS)
    @Fork(value = 1, warmups = 1)
    @OutputTimeUnit(TimeUnit.MICROSECONDS)
    public double testDirect(MyState state) {
        double result = 0;
        for (int j = 0; j < 100; j++) {
            int edge = state.rnd.nextInt(EDGES);
            for (int i = 0; i < BYTES_PER_EDGE; ++i) {
                byte b = state.da.getByte(edge * BYTES_PER_EDGE + i);
                for (int k = 0; k < 8; ++k) {
                    result += ((b >>> k) & 1);
                }
            }
        }
        return result;
    }

    public static void main(String[] args) throws Exception {
        MyState m1 = new MyState();
        m1.setup();
        double v1 = new JHMTest().testEvs(m1);
        MyState m2 = new MyState();
        m2.setup();
        double v2 = new JHMTest().testDirect(m2);
        if (v1 != v2)
            throw new IllegalStateException("should be the same: " + v1 + " vs " + v2);
        org.openjdk.jmh.Main.main(args);
    }

    @State(Scope.Thread)
    public static class MyState {
        private BooleanEncodedValue[] evs;
        private Random rnd;
        private DataAccess da;
        private EdgeIntAccess edgeIntAccess;

        @Setup(Level.Iteration)
        public void setup() {
            System.out.println("--- setup ---");
            EncodingManager.Builder builder = EncodingManager.start();
            evs = new SimpleBooleanEncodedValue[EVS];
            for (int i = 0; i < EVS; i++)
                builder.add(evs[i] = new SimpleBooleanEncodedValue("my_int_" + i, false));
            builder.build();

            edgeIntAccess = new EdgeIntAccess() {
                @Override
                public int getInt(int edgeId, int index) {
                    return da.getInt((long) edgeId * BYTES_PER_EDGE + index * 4L);
                }

                @Override
                public void setInt(int edgeId, int index, int value) {
                    throw new UnsupportedOperationException();
                }
            };
            RAMDirectory dir = new RAMDirectory();
            da = dir.create("edges", DAType.RAM, EDGES * BYTES_PER_EDGE);
            da.create(EDGES * BYTES_PER_EDGE);
            rnd = new Random(123);
            for (int i = 0; i < EDGES; i++)
                for (int j = 0; j < BYTES_PER_EDGE / 4; j++)
                    da.setInt(i * BYTES_PER_EDGE + j * 4L, rnd.nextInt(Integer.MAX_VALUE));
            // reset
            rnd = new Random(123);

        }
    }
}
