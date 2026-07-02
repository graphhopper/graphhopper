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
 * Stores temporary so-called conditional restrictions from access:conditional and other conditional
 * tags affecting bikes. See OSMRoadAccessConditionalParser.
 */
enum class BikeTemporalAccess {
    MISSING, YES, NO;

    override fun toString(): String = Helper.toLowerCase(super.toString())

    companion object {
        @JvmField
        val CONDITIONALS: MutableCollection<String> = hashSetOf(
            "access:conditional",
            "vehicle:conditional", "bicycle:conditional"
        )
        const val KEY = "bike_temporal_access"

        @JvmStatic
        fun create(): EnumEncodedValue<BikeTemporalAccess> =
            EnumEncodedValue(KEY, BikeTemporalAccess::class.java)
    }
}
