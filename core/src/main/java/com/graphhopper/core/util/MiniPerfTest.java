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
package com.graphhopper.core.util;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

/**
 * @author Peter Karich
 */
public class MiniPerfTest {

    private static final double NS_PER_S = 1e9;
    private static final double NS_PER_MS = 1e6;
    private static final double NS_PER_US = 1e3;

    private int counts = 100;
    private long fullTime = 0;
    private long max;
    private long min = Long.MAX_VALUE;
    private int dummySum;

    /**
     * Important: Make sure to use the dummy sum in your program somewhere such that it's calculation cannot be skipped
     * by the JVM. Either use {@link #getDummySum()} or {@link #getReport()} after running this method.
     */
    public MiniPerfTest start(Task m) {
        int warmupCount = Math.max(1, counts / 3);
        for (int i = 0; i < warmupCount; i++) {
            dummySum += m.doCalc(true, i);
        }
        long startFull = System.nanoTime();
        for (int i = 0; i < counts; i++) {
            long start = System.nanoTime();
            dummySum += m.doCalc(false, i);
            long time = System.nanoTime() - start;
            if (time < min)
                min = time;

            if (time > max)
                max = time;
        }
        fullTime = System.nanoTime() - startFull;
        return this;
    }

    public interface Task {

        /**
         * @return return some integer as result from your processing to make sure that the JVM cannot
         * optimize (away) the call or within the call something.
         */
        int doCalc(boolean warmup, int run);
    }

    public MiniPerfTest setIterations(int counts) {
        this.counts = counts;
        return this;
    }

    /**
     * @return minimum time of every call, in ms
     */
    public double getMin() {
        return min / NS_PER_MS;
    }

    /**
     * @return maximum time of every calls, in ms
     */
    public double getMax() {
        return max / NS_PER_MS;
    }

    /**
     * @return time for all calls accumulated, in ms
     */
    public double getSum() {
        return fullTime / NS_PER_MS;
    }

    /**
     * @return mean time per call, in ms
     */
    public double getMean() {
        return getSum() / counts;
    }

    private String formatDuration(double durationNs) {
        double divisor;
        String unit;
        if (durationNs > 1e7d) {
            divisor = NS_PER_S;
            unit = "s";
        } else if (durationNs > 1e4d) {
            divisor = NS_PER_MS;
            unit = "ms";
        } else {
            divisor = NS_PER_US;
            unit = "µs";
        }
        return nf(durationNs / divisor) + unit;
    }

    public String getReport() {
        double meanNs = ((double) fullTime) / counts;
        return "sum:" + formatDuration(fullTime) + ", time/call:" + formatDuration(meanNs) + ", dummy: " + dummySum;
    }

    public int getDummySum() {
        return dummySum;
    }

    private String nf(Number num) {
        return new DecimalFormat("#.###", DecimalFormatSymbols.getInstance(Locale.ROOT)).format(num);
    }
}
