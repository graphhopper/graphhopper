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
package com.graphhopper.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.DecimalFormat;

/**
 * @author Peter Karich
 */
public abstract class MiniPerfTest {
    protected Logger logger = LoggerFactory.getLogger(getClass());
    private int counts = 100;
    private double fullTime = 0;
    private double max;
    private double min = Double.MAX_VALUE;
    private int dummySum;

    public MiniPerfTest start() {
        int warmupCount = Math.max(1, counts / 3);
        for (int i = 0; i < warmupCount; i++) {
            dummySum += doCalc(true, i);
        }
        long startFull = System.nanoTime();
        for (int i = 0; i < counts; i++) {
            long start = System.nanoTime();
            dummySum += doCalc(false, i);
            long time = System.nanoTime() - start;
            if (time < min)
                min = time;

            if (time > max)
                max = time;
        }
        fullTime = System.nanoTime() - startFull;
        logger.info("dummySum:" + dummySum);
        return this;
    }

    public MiniPerfTest setIterations(int counts) {
        this.counts = counts;
        return this;
    }

    /**
     * @return minimum time of every call, in ms
     */
    public double getMin() {
        return min / 1e6;
    }

    /**
     * @return maximum time of every calls, in ms
     */
    public double getMax() {
        return max / 1e6;
    }

    /**
     * @return time for all calls accumulated, in ms
     */
    public double getSum() {
        return fullTime / 1e6;
    }

    /**
     * @return mean time per call, in ms
     */
    public double getMean() {
        return getSum() / counts;
    }

    public String getReport() {
        return "sum:" + nf(getSum() / 1000f) + "s, time/call:" + nf(getMean() / 1000f) + "s";
    }

    public String nf(Number num) {
        return new DecimalFormat("#.###").format(num);
    }

    /**
     * @return return some integer as result from your processing to make sure that the JVM cannot
     * optimize (away) the call or within the call something.
     */
    public abstract int doCalc(boolean warmup, int run);
}
