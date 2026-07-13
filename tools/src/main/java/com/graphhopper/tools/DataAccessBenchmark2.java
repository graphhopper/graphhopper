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

import com.graphhopper.storage.DataAccess;
import com.graphhopper.storage.ForeignMemoryDataAccess;
import com.graphhopper.storage.RAMInt1SegmentDataAccess;
import com.graphhopper.util.MiniPerfTest;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Random;

public class DataAccessBenchmark2 {
    private static final int FIELDS = 5;
    private static final int RECORDS = 1 << 22; // ~4M records
    private static final int CALLS_PER_RUN = 200_000_000;
    private static final int ROUNDS = 7;
    private static final int INDEX_COUNT = 1 << 20;
    private static final int INDEX_MASK = INDEX_COUNT - 1;
    private static final int[] NS = {5, 10, 20, 30};

    public static void main(String[] args) throws Exception {
        if (args.length >= 1 && (args[0].equals("ramint") || args[0].equals("foreign"))) {
            // Child mode: run benchmark for one type in isolation
            DataAccessBenchmark2 bench = new DataAccessBenchmark2(args[0], "local/l1i_bench/");
            for (int n : NS) bench.run(n);
            return;
        }

        // Parent mode: spawn two child JVMs and print combined table
        System.out.println("=== DataAccess getInt() Scaling Benchmark ===");
        System.out.println("JVM: " + System.getProperty("java.version"));
        System.out.println("Running ramint...");
        Map<Integer, Double> ramint = runChild("ramint");
        System.out.println("Running foreign...");
        Map<Integer, Double> foreign = runChild("foreign");
        System.out.println();
        System.out.println("  N   ramint   foreign   ratio");
        System.out.println("---   ------   -------   -----");
        for (int n : NS) {
            double r = ramint.getOrDefault(n, Double.NaN);
            double f = foreign.getOrDefault(n, Double.NaN);
            System.out.printf(java.util.Locale.US, "%3d   %5.2fns   %5.2fns   %.2fx%n", n, r, f, f / r);
        }
    }

    /** Spawns a child JVM for the given type, returns map of N -> ns/call */
    private static Map<Integer, Double> runChild(String type) throws Exception {
        String javaHome = System.getProperty("java.home");
        String classpath = System.getProperty("java.class.path");
        ProcessBuilder pb = new ProcessBuilder(
                javaHome + "/bin/java", "-Xmx4g", "-cp", classpath,
                DataAccessBenchmark2.class.getName(), type);
        pb.redirectErrorStream(true);
        Process proc = pb.start();
        Map<Integer, Double> results = new LinkedHashMap<>();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(proc.getInputStream()))) {
            String line;
            while ((line = br.readLine()) != null) {
                if (line.startsWith("RESULT ")) {
                    String[] parts = line.split("\\s+");
                    int n = Integer.parseInt(parts[1].substring(2));
                    double ns = Double.parseDouble(parts[2].substring(3));
                    results.put(n, ns);
                }
            }
        }
        proc.waitFor();
        return results;
    }

    // --- benchmark instance ---

    private final DataAccess da;
    // byte offsets to the start of each record, loaded from array so C2 treats them as opaque
    private final int[] indices;

    public DataAccessBenchmark2(String type, String storageDir) {
        if (!storageDir.endsWith("/")) storageDir += "/";
        Random rng = new Random(42);
        indices = new int[INDEX_COUNT];
        for (int i = 0; i < INDEX_COUNT; i++) indices[i] = (i & (RECORDS - 1)) * FIELDS * 4;
        long totalBytes = (long) RECORDS * FIELDS * 4;
        da = type.equals("ramint")
                ? new RAMInt1SegmentDataAccess("bench", storageDir, false, -1)
                : new ForeignMemoryDataAccess("bench", storageDir, false, -1);
        da.create(totalBytes);
        for (long j = 0; j < (long) RECORDS * FIELDS; j++) da.setInt(j * 4, rng.nextInt());
    }

    private void run(int n) {
        int iters = CALLS_PER_RUN / n;
        MiniPerfTest perf = new MiniPerfTest().setIterations(ROUNDS).start((warmup, r) -> {
            long s = switch (n) {
                case 5 -> bench5(iters); case 10 -> bench10(iters);
                case 20 -> bench20(iters); case 30 -> bench30(iters);
                default -> throw new IllegalArgumentException();
            };
            return (int) s;
        });
        double nsPerCall = perf.getMin() * 1e6 / iters / n;
        System.out.printf(java.util.Locale.US, "RESULT N=%d ns=%.2f%n", n, nsPerCall);
    }

    // --- 5 call sites: read 1 record ---
    private long bench5(int iters) {
        long sum = 0; int[] idx = this.indices; DataAccess d = this.da;
        for (int i = 0; i < iters; i++) {
            long b = idx[i & INDEX_MASK];
            sum += d.getInt(b) + d.getInt(b + 4) + d.getInt(b + 8) + d.getInt(b + 12) + d.getInt(b + 16);
        }
        return sum;
    }

    // --- 10 call sites: read 2 records ---
    private long bench10(int iters) {
        long sum = 0; int[] idx = this.indices; DataAccess d = this.da;
        for (int i = 0; i < iters; i++) {
            long a = idx[i & INDEX_MASK];
            long b = idx[(i + 1) & INDEX_MASK];
            sum += d.getInt(a) + d.getInt(a + 4) + d.getInt(a + 8) + d.getInt(a + 12) + d.getInt(a + 16)
                    + d.getInt(b) + d.getInt(b + 4) + d.getInt(b + 8) + d.getInt(b + 12) + d.getInt(b + 16);
        }
        return sum;
    }

    // --- 20 call sites: read 4 records ---
    private long bench20(int iters) {
        long sum = 0; int[] idx = this.indices; DataAccess d = this.da;
        for (int i = 0; i < iters; i++) {
            long a = idx[i & INDEX_MASK];
            long b = idx[(i + 1) & INDEX_MASK];
            long c = idx[(i + 2) & INDEX_MASK];
            long e = idx[(i + 3) & INDEX_MASK];
            sum += d.getInt(a) + d.getInt(a + 4) + d.getInt(a + 8) + d.getInt(a + 12) + d.getInt(a + 16)
                    + d.getInt(b) + d.getInt(b + 4) + d.getInt(b + 8) + d.getInt(b + 12) + d.getInt(b + 16)
                    + d.getInt(c) + d.getInt(c + 4) + d.getInt(c + 8) + d.getInt(c + 12) + d.getInt(c + 16)
                    + d.getInt(e) + d.getInt(e + 4) + d.getInt(e + 8) + d.getInt(e + 12) + d.getInt(e + 16);
        }
        return sum;
    }

    // --- 30 call sites: read 6 records ---
    private long bench30(int iters) {
        long sum = 0; int[] idx = this.indices; DataAccess d = this.da;
        for (int i = 0; i < iters; i++) {
            long a = idx[i & INDEX_MASK];
            long b = idx[(i + 1) & INDEX_MASK];
            long c = idx[(i + 2) & INDEX_MASK];
            long e = idx[(i + 3) & INDEX_MASK];
            long f = idx[(i + 4) & INDEX_MASK];
            long g = idx[(i + 5) & INDEX_MASK];
            sum += d.getInt(a) + d.getInt(a + 4) + d.getInt(a + 8) + d.getInt(a + 12) + d.getInt(a + 16)
                    + d.getInt(b) + d.getInt(b + 4) + d.getInt(b + 8) + d.getInt(b + 12) + d.getInt(b + 16)
                    + d.getInt(c) + d.getInt(c + 4) + d.getInt(c + 8) + d.getInt(c + 12) + d.getInt(c + 16)
                    + d.getInt(e) + d.getInt(e + 4) + d.getInt(e + 8) + d.getInt(e + 12) + d.getInt(e + 16)
                    + d.getInt(f) + d.getInt(f + 4) + d.getInt(f + 8) + d.getInt(f + 12) + d.getInt(f + 16)
                    + d.getInt(g) + d.getInt(g + 4) + d.getInt(g + 8) + d.getInt(g + 12) + d.getInt(g + 16);
        }
        return sum;
    }

}
