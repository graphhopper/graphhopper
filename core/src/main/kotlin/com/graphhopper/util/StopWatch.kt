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

/**
 * Make simple speed measurements possible.
 *
 * @author Peter Karich
 */
class StopWatch @JvmOverloads constructor(private var name: String = "") {
    private var lastTime: Long = 0
    private var elapsedNanos: Long = 0

    fun setName(name: String): StopWatch {
        this.name = name
        return this
    }

    fun start(): StopWatch {
        lastTime = System.nanoTime()
        return this
    }

    fun stop(): StopWatch {
        if (lastTime < 0)
            return this

        elapsedNanos += System.nanoTime() - lastTime
        lastTime = -1
        return this
    }

    fun getSeconds(): Float = elapsedNanos / 1e9f

    /**
     * returns the total elapsed time on this stopwatch without the need of stopping it
     */
    fun getCurrentSeconds(): Float {
        if (notStarted())
            return 0f

        val lastNanos = if (lastTime < 0) 0 else System.nanoTime() - lastTime
        return (elapsedNanos + lastNanos) / 1e9f
    }

    fun getMillis(): Long = elapsedNanos / 1_000_000

    /**
     * returns the elapsed time in ms but includes the fraction as well to get a precise value
     */
    fun getMillisDouble(): Double = elapsedNanos / 1_000_000.0

    fun getNanos(): Long = elapsedNanos

    override fun toString(): String {
        var str = ""
        if (!Helper.isEmpty(name))
            str += "$name "

        return str + "time:" + getSeconds() + "s"
    }

    fun getTimeString(): String {
        if (elapsedNanos < 1e3) {
            return elapsedNanos.toString() + "ns"
        } else if (elapsedNanos < 1e6) {
            return String.format("%.2fµs", elapsedNanos / 1.0e3)
        } else if (elapsedNanos < 1e9) {
            return String.format("%.2fms", elapsedNanos / 1.0e6)
        } else {
            val seconds = elapsedNanos / 1.0e9
            return if (seconds < 60.0) {
                String.format("%.2fs", elapsedNanos / 1e9)
            } else if (seconds < 60.0 * 60.0) {
                String.format("%dmin %ds", seconds.toInt() / 60, seconds.toInt() % 60)
            } else {
                String.format("%dh %dmin", seconds.toInt() / (60 * 60), seconds.toInt() % (60 * 60) / 60)
            }
        }
    }

    private fun notStarted(): Boolean = lastTime == 0L && elapsedNanos == 0L

    companion object {
        @JvmStatic
        @JvmOverloads
        fun started(name: String = ""): StopWatch = StopWatch(name).start()
    }
}
