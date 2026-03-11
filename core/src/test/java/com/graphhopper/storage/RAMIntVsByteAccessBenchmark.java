package com.graphhopper.storage;

import java.util.Random;

/**
 * Benchmark comparing RAMIntDataAccess vs RAMDataAccess for 5-byte integer random access at GB scale.
 * Run with: java -Xmx6g -cp ... com.graphhopper.storage.RAMIntVsByteAccessBenchmark
 */
public class RAMIntVsByteAccessBenchmark {
    // 8 bytes per entry: 4 (int) + 1 (byte) = 5 used bytes, padded to 8 for int alignment
    private static final int ENTRY_BYTES = 8;
    private static final long TOTAL_BYTES = 2L * 1024 * 1024 * 1024; // 2 GB
    private static final int NUM_ENTRIES = (int) (TOTAL_BYTES / ENTRY_BYTES);
    private static final int OPS_PER_ROUND = 10_000_000;
    private static final int WARMUP_ROUNDS = 5;
    private static final int MEASURE_ROUNDS = 10;

    public static void main(String[] args) {
        System.out.println("=== RAMIntDataAccess vs RAMDataAccess: 5-byte integer benchmark ===");
        System.out.printf("Total data: %.1f GB, entries: %,d, ops per round: %,d%n",
                TOTAL_BYTES / (1024.0 * 1024 * 1024), NUM_ENTRIES, OPS_PER_ROUND);
        System.out.println();

        // pre-generate random positions to avoid measuring RNG overhead
        Random rng = new Random(42);
        int[] positions = new int[OPS_PER_ROUND];
        for (int i = 0; i < OPS_PER_ROUND; i++) {
            positions[i] = rng.nextInt(NUM_ENTRIES);
        }

        System.out.println("--- Allocating RAMIntDataAccess ---");
        RAMIntDataAccess intDA = new RAMIntDataAccess("bench_int", "", false, 1 << 20);
        intDA.create(TOTAL_BYTES);
        System.out.printf("  capacity: %,d bytes, segments: %d%n", intDA.getCapacity(), intDA.getSegments());

        System.out.println("--- Allocating RAMDataAccess ---");
        RAMDataAccess byteDA = new RAMDataAccess("bench_byte", "", false, 1 << 20);
        byteDA.create(TOTAL_BYTES);
        System.out.printf("  capacity: %,d bytes, segments: %d%n", byteDA.getCapacity(), byteDA.getSegments());
        System.out.println();

        // Write benchmark
        System.out.println("=== WRITE: setInt + setByte (5-byte integer) ===");
        benchWrite("RAMIntDataAccess", intDA, positions);
        benchWrite("RAMDataAccess   ", byteDA, positions);
        System.out.println();

        // Read benchmark
        System.out.println("=== READ: getInt + getByte (5-byte integer) ===");
        benchRead("RAMIntDataAccess", intDA, positions);
        benchRead("RAMDataAccess   ", byteDA, positions);
        System.out.println();

        // Mixed read-write benchmark
        System.out.println("=== MIXED: write then read back (5-byte integer) ===");
        benchMixed("RAMIntDataAccess", intDA, positions);
        benchMixed("RAMDataAccess   ", byteDA, positions);
        System.out.println();

        // setInt-only benchmark (for reference)
        System.out.println("=== REFERENCE: setInt only ===");
        benchSetIntOnly("RAMIntDataAccess", intDA, positions);
        benchSetIntOnly("RAMDataAccess   ", byteDA, positions);
        System.out.println();

        System.out.println("=== REFERENCE: getInt only ===");
        benchGetIntOnly("RAMIntDataAccess", intDA, positions);
        benchGetIntOnly("RAMDataAccess   ", byteDA, positions);

        intDA.close();
        byteDA.close();
    }

    private static void benchWrite(String label, DataAccess da, int[] positions) {
        long dummy = 0;
        // warmup
        for (int r = 0; r < WARMUP_ROUNDS; r++) {
            for (int i = 0; i < positions.length; i++) {
                long bytePos = (long) positions[i] * ENTRY_BYTES;
                da.setInt(bytePos, i);
                da.setByte(bytePos + 4, (byte) (i >> 32));
            }
            dummy += da.getInt(0);
        }

        // measure
        long[] times = new long[MEASURE_ROUNDS];
        for (int r = 0; r < MEASURE_ROUNDS; r++) {
            long start = System.nanoTime();
            for (int i = 0; i < positions.length; i++) {
                long bytePos = (long) positions[i] * ENTRY_BYTES;
                da.setInt(bytePos, i);
                da.setByte(bytePos + 4, (byte) (i >> 32));
            }
            times[r] = System.nanoTime() - start;
            dummy += da.getInt(0);
        }
        printResults(label, times, dummy);
    }

    private static void benchRead(String label, DataAccess da, int[] positions) {
        long dummy = 0;
        // warmup
        for (int r = 0; r < WARMUP_ROUNDS; r++) {
            for (int i = 0; i < positions.length; i++) {
                long bytePos = (long) positions[i] * ENTRY_BYTES;
                int low = da.getInt(bytePos);
                byte high = da.getByte(bytePos + 4);
                dummy += low + high;
            }
        }

        // measure
        long[] times = new long[MEASURE_ROUNDS];
        for (int r = 0; r < MEASURE_ROUNDS; r++) {
            long start = System.nanoTime();
            for (int i = 0; i < positions.length; i++) {
                long bytePos = (long) positions[i] * ENTRY_BYTES;
                int low = da.getInt(bytePos);
                byte high = da.getByte(bytePos + 4);
                dummy += low + high;
            }
            times[r] = System.nanoTime() - start;
        }
        printResults(label, times, dummy);
    }

    private static void benchMixed(String label, DataAccess da, int[] positions) {
        long dummy = 0;
        // warmup
        for (int r = 0; r < WARMUP_ROUNDS; r++) {
            for (int i = 0; i < positions.length; i++) {
                long bytePos = (long) positions[i] * ENTRY_BYTES;
                long val = (long) i * 7 + 13;
                da.setInt(bytePos, (int) val);
                da.setByte(bytePos + 4, (byte) (val >> 32));
                int low = da.getInt(bytePos);
                byte high = da.getByte(bytePos + 4);
                dummy += low + high;
            }
        }

        // measure
        long[] times = new long[MEASURE_ROUNDS];
        for (int r = 0; r < MEASURE_ROUNDS; r++) {
            long start = System.nanoTime();
            for (int i = 0; i < positions.length; i++) {
                long bytePos = (long) positions[i] * ENTRY_BYTES;
                long val = (long) i * 7 + 13;
                da.setInt(bytePos, (int) val);
                da.setByte(bytePos + 4, (byte) (val >> 32));
                int low = da.getInt(bytePos);
                byte high = da.getByte(bytePos + 4);
                dummy += low + high;
            }
            times[r] = System.nanoTime() - start;
        }
        printResults(label, times, dummy);
    }

    private static void benchSetIntOnly(String label, DataAccess da, int[] positions) {
        long dummy = 0;
        for (int r = 0; r < WARMUP_ROUNDS; r++) {
            for (int i = 0; i < positions.length; i++) {
                long bytePos = (long) positions[i] * ENTRY_BYTES;
                da.setInt(bytePos, i);
            }
            dummy += da.getInt(0);
        }

        long[] times = new long[MEASURE_ROUNDS];
        for (int r = 0; r < MEASURE_ROUNDS; r++) {
            long start = System.nanoTime();
            for (int i = 0; i < positions.length; i++) {
                long bytePos = (long) positions[i] * ENTRY_BYTES;
                da.setInt(bytePos, i);
            }
            times[r] = System.nanoTime() - start;
            dummy += da.getInt(0);
        }
        printResults(label, times, dummy);
    }

    private static void benchGetIntOnly(String label, DataAccess da, int[] positions) {
        long dummy = 0;
        for (int r = 0; r < WARMUP_ROUNDS; r++) {
            for (int i = 0; i < positions.length; i++) {
                long bytePos = (long) positions[i] * ENTRY_BYTES;
                dummy += da.getInt(bytePos);
            }
        }

        long[] times = new long[MEASURE_ROUNDS];
        for (int r = 0; r < MEASURE_ROUNDS; r++) {
            long start = System.nanoTime();
            for (int i = 0; i < positions.length; i++) {
                long bytePos = (long) positions[i] * ENTRY_BYTES;
                dummy += da.getInt(bytePos);
            }
            times[r] = System.nanoTime() - start;
        }
        printResults(label, times, dummy);
    }

    private static void printResults(String label, long[] timesNs, long dummy) {
        long sum = 0, min = Long.MAX_VALUE, max = 0;
        for (long t : timesNs) {
            sum += t;
            if (t < min) min = t;
            if (t > max) max = t;
        }
        double avgMs = (sum / (double) timesNs.length) / 1e6;
        double minMs = min / 1e6;
        double maxMs = max / 1e6;
        double opsPerSec = OPS_PER_ROUND / (avgMs / 1000.0);
        System.out.printf("  %s  avg: %7.1f ms  min: %7.1f ms  max: %7.1f ms  ops/s: %,.0f  (dummy=%d)%n",
                label, avgMs, minMs, maxMs, opsPerSec, dummy);
    }
}
