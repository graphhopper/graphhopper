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
package com.graphhopper.routing.ev

import com.graphhopper.util.Helper

/**
 * This enum defines the road smoothness of an edge. If not tagged the value will be MISSING, which is the default.
 * All unknown smoothness tags will get OTHER .
 */
enum class Smoothness {
    // Order is important to make it roughly comparable
    MISSING, EXCELLENT, GOOD, INTERMEDIATE, BAD, VERY_BAD, HORRIBLE, VERY_HORRIBLE, IMPASSABLE, OTHER;

    override fun toString(): String = Helper.toLowerCase(super.toString())

    companion object {
        const val KEY = "smoothness"

        @JvmStatic
        fun create(): EnumEncodedValue<Smoothness> = EnumEncodedValue(KEY, Smoothness::class.java)

        @JvmStatic
        fun find(name: String?): Smoothness {
            if (Helper.isEmpty(name))
                return MISSING
            return try {
                valueOf(Helper.toUpperCase(name))
            } catch (ex: IllegalArgumentException) {
                OTHER
            }
        }
    }
}
