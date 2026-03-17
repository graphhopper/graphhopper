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

import com.graphhopper.storage.*;
import com.graphhopper.util.MiniPerfTest;

import java.util.Random;

public class DataAccessBenchmark {
    private static final int SIZE = 10_000_000;
    private static final int ITERATIONS = 1000;

    public static void main(String[] args) {
        int segmentSize = 1 << 20;
        System.out.println("DataAccess Benchmark");
        System.out.println("====================");
        System.out.println("Size: " + (SIZE / 1_000_000) + "MB, Iterations: " + ITERATIONS);
        System.out.println();

        benchmarkImpl("RAMDataAccess", new RAMDataAccess("bench_ram", "", false, segmentSize));
        benchmarkImpl("ForeignMemoryDataAccess", new ForeignMemoryDataAccess("bench_foreign", "", false, segmentSize));
        benchmarkImpl("ForeignMemorySegmentedDataAccess", new ForeignMemorySegmentedDataAccess("bench_native", "", false, segmentSize));
    }

    private static void benchmarkImpl(String name, DataAccess da) {
        da.create(SIZE);
        int intCount = SIZE / 4;

        // Pre-compute random positions to avoid Random overhead during measurement
        Random random = new Random(42);
        int[] randomPositions = new int[intCount];
        for (int i = 0; i < intCount; i++)
            randomPositions[i] = random.nextInt(intCount) * 4;

        System.out.println(name + ":");

        // Sequential int write
        MiniPerfTest seqWrite = new MiniPerfTest().setIterations(ITERATIONS).start((warmup, run) -> {
            int sum = 0;
            for (int i = 0; i < intCount; i++) {
                da.setInt((long) i * 4, i);
                sum += i;
            }
            return sum;
        });
        System.out.println("  seq write: " + seqWrite.getReport());

        // Sequential int read
        MiniPerfTest seqRead = new MiniPerfTest().setIterations(ITERATIONS).start((warmup, run) -> {
            int sum = 0;
            for (int i = 0; i < intCount; i++) {
                sum += da.getInt((long) i * 4);
            }
            return sum;
        });
        System.out.println("  seq read:  " + seqRead.getReport());

        // Random int write
        MiniPerfTest randWrite = new MiniPerfTest().setIterations(ITERATIONS).start((warmup, run) -> {
            int sum = 0;
            for (int i = 0; i < intCount; i++) {
                da.setInt(randomPositions[i], i);
                sum += i;
            }
            return sum;
        });
        System.out.println("  rnd write: " + randWrite.getReport());

        // Random int read
        MiniPerfTest randRead = new MiniPerfTest().setIterations(ITERATIONS).start((warmup, run) -> {
            int sum = 0;
            for (int i = 0; i < intCount; i++) {
                sum += da.getInt(randomPositions[i]);
            }
            return sum;
        });
        System.out.println("  rnd read:  " + randRead.getReport());

        da.close();
        System.out.println();
    }
}
