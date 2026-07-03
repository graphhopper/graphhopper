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
package com.graphhopper.util.details

class IntersectionValues {
    @JvmField
    var bearing: Int = 0

    @JvmField
    var entry: Boolean = false

    @JvmField
    var `in`: Boolean = false

    @JvmField
    var out: Boolean = false

    companion object {
        /**
         * create a List of IntersectionValues from a PathDetail
         */
        @JvmStatic
        fun createList(intersectionMap: Map<String, Any>): List<IntersectionValues> {
            val list = ArrayList<IntersectionValues>()

            @Suppress("UNCHECKED_CAST")
            val bearings = intersectionMap["bearings"] as List<Int>
            val inIndex = intersectionMap.getOrDefault("in", -1) as Int
            val outIndex = intersectionMap.getOrDefault("out", -1) as Int
            @Suppress("UNCHECKED_CAST")
            val entry = intersectionMap["entries"] as List<Boolean>

            if (bearings.size != entry.size) {
                throw IllegalStateException("Bearings and entry array sizes different")
            }
            val numEntries = bearings.size

            for (i in 0 until numEntries) {
                val iv = IntersectionValues()
                iv.bearing = bearings[i]
                iv.entry = entry[i]
                iv.`in` = (inIndex == i)
                iv.out = (outIndex == i)

                list.add(iv)
            }
            return list
        }

        /**
         * create a PathDetail from a List of IntersectionValues
         */
        @JvmStatic
        fun createIntersection(list: List<IntersectionValues>): Map<String, Any> {
            val intersection = HashMap<String, Any>()

            intersection["bearings"] = list.map { it.bearing }
            intersection["entries"] = list.map { it.entry }

            for (m in list.indices) {
                val intersectionValues = list[m]
                if (intersectionValues.`in`) {
                    intersection["in"] = m
                }
                if (intersectionValues.out) {
                    intersection["out"] = m
                }
            }
            return intersection
        }
    }
}
