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
 * This enum defines the route network of an edge when part of a hiking or biking network.
 * If not tagged the value will be MISSING (default) and all edges that do not fit get OTHER as value.
 */
enum class RouteNetwork {
    MISSING, INTERNATIONAL, NATIONAL, REGIONAL, LOCAL, OTHER;

    override fun toString(): String = Helper.toLowerCase(super.toString())

    companion object {
        @JvmStatic
        fun key(prefix: String): String = prefix + "_network"

        @JvmStatic
        fun create(name: String): EnumEncodedValue<RouteNetwork> =
            EnumEncodedValue(name, RouteNetwork::class.java)

        @JvmStatic
        fun find(name: String?): RouteNetwork {
            if (Helper.isEmpty(name))
                return MISSING
            return try {
                valueOf(Helper.toUpperCase(name))
            } catch (ex: IllegalArgumentException) {
                MISSING
            }
        }
    }
}
