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
    private static final int SIZE = 1_000_000_000;
    private static final int ITERATIONS = 30;

    public static void main(String[] args) {
        int segmentSize = 1 << 25;      // 32 MB
        int largeSegmentSize = 1 << 28;  // 256 MB
        System.out.println("DataAccess Benchmark");
        System.out.println("====================");
        System.out.println("Size: " + (SIZE / 1_000_000) + "MB, Iterations: " + ITERATIONS);
        System.out.println();

        // --- Single-segment / contiguous implementations ---
        benchmarkImpl("RAM1SegmentDataAccess", new RAM1SegmentDataAccess("bench_ram1", "", false, segmentSize));
        benchmarkImpl("ForeignMemoryDataAccess", new ForeignMemoryDataAccess("bench_foreign", "", false, segmentSize));

        // --- Segmented implementations: small segments ---
        benchmarkImpl("RAMDataAccess (32MB seg)", new RAMDataAccess("bench_ram", "", false, segmentSize));
        benchmarkImpl("ForeignMemorySegmentedDataAccess (32MB seg)", new ForeignMemorySegmentedDataAccess("bench_native", "", false, segmentSize));

        // --- Segmented implementations: large segments ---
        benchmarkImpl("RAMDataAccess (256MB seg)", new RAMDataAccess("bench_ram_lg", "", false, largeSegmentSize));
        benchmarkImpl("ForeignMemorySegmentedDataAccess (256MB seg)", new ForeignMemorySegmentedDataAccess("bench_native_lg", "", false, largeSegmentSize));
    }

    private static void benchmarkImpl(String name, DataAccess da) {
        da.create(SIZE);
        int intCount = SIZE / 4;
        // Access every 10th int — still covers the full address range but 10x fewer ops
        int stride = 10;
        int opsPerIter = intCount / stride;

        // Pre-compute random positions to avoid Random overhead during measurement
        Random random = new Random(42);
        int[] randomPositions = new int[opsPerIter];
        for (int i = 0; i < opsPerIter; i++)
            randomPositions[i] = random.nextInt(intCount) * 4;

        System.out.println(name + " (" + (opsPerIter / 1_000_000) + "M ops/iter):");

        // Sequential int write
        MiniPerfTest seqWrite = new MiniPerfTest().setIterations(ITERATIONS).start((warmup, run) -> {
            int sum = 0;
            for (int i = 0; i < opsPerIter; i++) {
                long pos = (long) i * stride * 4;
                da.setInt(pos, i);
                sum += i;
            }
            return sum;
        });
        System.out.println("  seq write: " + seqWrite.getReport());

        // Sequential int read
        MiniPerfTest seqRead = new MiniPerfTest().setIterations(ITERATIONS).start((warmup, run) -> {
            int sum = 0;
            for (int i = 0; i < opsPerIter; i++) {
                sum += da.getInt((long) i * stride * 4);
            }
            return sum;
        });
        System.out.println("  seq read:  " + seqRead.getReport());

        // Random int write
        MiniPerfTest randWrite = new MiniPerfTest().setIterations(ITERATIONS).start((warmup, run) -> {
            int sum = 0;
            for (int i = 0; i < opsPerIter; i++) {
                da.setInt(randomPositions[i], i);
                sum += i;
            }
            return sum;
        });
        System.out.println("  rnd write: " + randWrite.getReport());

        // Random int read
        MiniPerfTest randRead = new MiniPerfTest().setIterations(ITERATIONS).start((warmup, run) -> {
            int sum = 0;
            for (int i = 0; i < opsPerIter; i++) {
                sum += da.getInt(randomPositions[i]);
            }
            return sum;
        });
        System.out.println("  rnd read:  " + randRead.getReport());

        da.close();
        System.out.println();
    }
}
