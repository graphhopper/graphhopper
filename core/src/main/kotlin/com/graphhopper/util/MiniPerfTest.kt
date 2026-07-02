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
package com.graphhopper.util

import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

/**
 * @author Peter Karich
 */
class MiniPerfTest {
    private var counts = 100
    private var warmups = -1
    private var fullTime = 0L
    private var max = 0L
    private var min = Long.MAX_VALUE
    private var dummySum = 0

    /**
     * Important: Make sure to use the dummy sum in your program somewhere such that it's calculation cannot be skipped
     * by the JVM. Either use [getDummySum] or [getReport] after running this method.
     */
    fun start(m: Task): MiniPerfTest {
        val warmupCount = if (warmups >= 0) warmups else Math.max(1, counts / 3)
        for (i in 0 until warmupCount) {
            dummySum += m.doCalc(true, i)
        }
        val startFull = System.nanoTime()
        for (i in 0 until counts) {
            val start = System.nanoTime()
            dummySum += m.doCalc(false, i)
            val time = System.nanoTime() - start
            if (time < min)
                min = time

            if (time > max)
                max = time
        }
        fullTime = System.nanoTime() - startFull
        return this
    }

    fun interface Task {
        /**
         * @return return some integer as result from your processing to make sure that the JVM cannot
         * optimize (away) the call or within the call something.
         */
        fun doCalc(warmup: Boolean, run: Int): Int
    }

    fun setWarmups(warmups: Int): MiniPerfTest {
        this.warmups = warmups
        return this
    }

    fun setIterations(counts: Int): MiniPerfTest {
        this.counts = counts
        return this
    }

    /**
     * @return minimum time of every call, in ms
     */
    fun getMin(): Double = min / NS_PER_MS

    /**
     * @return maximum time of every calls, in ms
     */
    fun getMax(): Double = max / NS_PER_MS

    /**
     * @return time for all calls accumulated, in ms
     */
    fun getSum(): Double = fullTime / NS_PER_MS

    /**
     * @return mean time per call, in ms
     */
    fun getMean(): Double = getSum() / counts

    private fun formatDuration(durationNs: Double): String {
        val divisor: Double
        val unit: String
        if (durationNs > 1e7) {
            divisor = NS_PER_S
            unit = "s"
        } else if (durationNs > 1e4) {
            divisor = NS_PER_MS
            unit = "ms"
        } else {
            divisor = NS_PER_US
            unit = "µs"
        }
        return nf(durationNs / divisor) + unit
    }

    fun getReport(): String {
        val meanNs = fullTime.toDouble() / counts
        return "sum:" + formatDuration(fullTime.toDouble()) + ", time/call:" + formatDuration(meanNs) + ", dummy: " + dummySum
    }

    fun getDummySum(): Int = dummySum

    private fun nf(num: Number): String =
        DecimalFormat("#.###", DecimalFormatSymbols.getInstance(Locale.ROOT)).format(num)

    companion object {
        private const val NS_PER_S = 1e9
        private const val NS_PER_MS = 1e6
        private const val NS_PER_US = 1e3
    }
}
