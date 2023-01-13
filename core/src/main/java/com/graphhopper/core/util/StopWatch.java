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

import com.graphhopper.util.Helper;

/**
 * Make simple speed measurements possible.
 * <p>
 *
 * @author Peter Karich
 */
public class StopWatch {
    private long lastTime;
    private long elapsedNanos;
    private String name = "";

    public StopWatch(String name) {
        this.name = name;
    }

    public StopWatch() {
    }

    public static StopWatch started() {
        return started("");
    }

    public static StopWatch started(String name) {
        return new StopWatch(name).start();
    }

    public StopWatch setName(String name) {
        this.name = name;
        return this;
    }

    public StopWatch start() {
        lastTime = System.nanoTime();
        return this;
    }

    public StopWatch stop() {
        if (lastTime < 0)
            return this;

        elapsedNanos += System.nanoTime() - lastTime;
        lastTime = -1;
        return this;
    }

    public float getSeconds() {
        return elapsedNanos / 1e9f;
    }

    /**
     * returns the total elapsed time on this stopwatch without the need of stopping it
     */
    public float getCurrentSeconds() {
        if (notStarted()) {
            return 0;
        }
        long lastNanos = lastTime < 0 ? 0 : System.nanoTime() - lastTime;
        return (elapsedNanos + lastNanos) / 1e9f;
    }

    public long getMillis() {
        return elapsedNanos / 1_000_000;
    }

    /**
     * returns the elapsed time in ms but includes the fraction as well to get a precise value
     */
    public double getMillisDouble() {
        return elapsedNanos / 1_000_000.0;
    }

    public long getNanos() {
        return elapsedNanos;
    }

    @Override
    public String toString() {
        String str = "";
        if (!Helper.isEmpty(name)) {
            str += name + " ";
        }

        return str + "time:" + getSeconds() + "s";
    }

    public String getTimeString() {
        if (elapsedNanos < 1e3) {
            return elapsedNanos + "ns";
        } else if (elapsedNanos < 1e6) {
            return String.format("%.2fµs", elapsedNanos / 1.e3);
        } else if (elapsedNanos < 1e9) {
            return String.format("%.2fms", elapsedNanos / 1.e6);
        } else {
            double seconds = elapsedNanos / 1.e9;
            if (seconds < 60) {
                return String.format("%.2fs", elapsedNanos / 1e9);
            } else if (seconds < 60 * 60) {
                return String.format("%dmin %ds", ((int) seconds / 60), (((int) seconds) % 60));
            } else {
                return String.format("%dh %dmin", ((int) seconds / (60 * 60)), ((int) seconds) % (60 * 60) / 60);
            }
        }
    }

    private boolean notStarted() {
        return lastTime == 0 && elapsedNanos == 0;
    }
}
