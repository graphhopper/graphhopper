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
package com.graphhopper.routing.util

import java.util.TreeMap

/**
 * Used to store a priority value in the way flags of an edge. Used in combination with
 * PriorityWeighting
 *
 * @author Peter Karich
 */
enum class PriorityCode(val value: Int) {
    EXCLUDE(0),
    REACH_DESTINATION(1),
    VERY_BAD(3),
    BAD(5),
    AVOID_MORE(6),
    AVOID(8),
    SLIGHT_AVOID(9),
    UNCHANGED(10),
    SLIGHT_PREFER(11),
    PREFER(12),
    VERY_NICE(13),
    BEST(15);

    fun worse(): PriorityCode {
        val ret = VALUES.lowerEntry(this.value)
        return if (ret == null) EXCLUDE else ret.value
    }

    fun better(): PriorityCode {
        val ret = VALUES.higherEntry(this.value)
        return if (ret == null) BEST else ret.value
    }

    companion object {
        @JvmField
        val VALUES: TreeMap<Int, PriorityCode> = TreeMap<Int, PriorityCode>().apply {
            for (priorityCode in PriorityCode.entries) {
                put(priorityCode.value, priorityCode)
            }
        }

        @JvmStatic
        fun getFactor(value: Int): Double = value / 10.0

        @JvmStatic
        fun getValue(value: Int): Double = getFactor(value)

        @JvmStatic
        fun valueOf(integ: Int): PriorityCode {
            val ret = VALUES.ceilingEntry(integ)
            return if (ret == null) BEST else ret.value
        }
    }
}
