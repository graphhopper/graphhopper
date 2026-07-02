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

enum class FootRoadAccess {
    MISSING, YES, DESIGNATED, DESTINATION, PRIVATE, MILITARY, USE_SIDEPATH, NO;

    override fun toString(): String = Helper.toLowerCase(super.toString())

    companion object {
        const val KEY = "foot_road_access"

        @JvmStatic
        fun create(): EnumEncodedValue<FootRoadAccess> =
            EnumEncodedValue(KEY, FootRoadAccess::class.java)

        @JvmStatic
        fun find(name: String?): FootRoadAccess {
            if (name.isNullOrEmpty())
                return MISSING
            if (name.equals("permit", ignoreCase = true) || name.equals("customers", ignoreCase = true))
                return PRIVATE
            return try {
                valueOf(Helper.toUpperCase(name))
            } catch (ex: IllegalArgumentException) {
                YES
            }
        }
    }
}
