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
package com.graphhopper.routing.weighting

import com.graphhopper.util.EdgeIteratorState

/**
 * Specifies how the best route is calculated.
 *
 * @author Peter Karich
 */
interface Weighting {

    /**
     * Used only for the heuristic estimation in A*
     *
     * @return minimal weight per meter. E.g. if you calculate the fastest way the return value
     * is '1/max_velocity' or a shortest weighting would return 1.
     */
    fun calcMinWeightPerDistance(): Double

    /**
     * This method calculates the weight of a given [EdgeIteratorState]. E.g. a high value indicates that the edge
     * should be avoided during shortest path search. Make sure that this method is very fast and optimized as this is
     * called potentially millions of times for one route or a lot more for nearly any preprocessing phase.
     *
     * @param edgeState the edge for which the weight should be calculated
     * @param reverse   if the specified edge is specified in reverse direction e.g. from the reverse
     *                  case of a bidirectional search.
     * @return the calculated weight with the specified velocity has to be in the range of 0 and
     * +Infinity. GraphHopper expects weights to be whole numbers only. Consider using [Weighting.roundWeight]
     * to post-process all weights. Make sure your method does not return NaN which can e.g. occur for 0/0.
     */
    fun calcEdgeWeight(edgeState: EdgeIteratorState, reverse: Boolean): Double

    /**
     * This method calculates the time taken (in milliseconds) to travel along the specified edgeState.
     * It is typically used for post-processing and on only a few thousand edges.
     */
    fun calcEdgeMillis(edgeState: EdgeIteratorState, reverse: Boolean): Long

    fun calcTurnWeight(inEdge: Int, viaNode: Int, outEdge: Int): Double

    fun calcTurnMillis(inEdge: Int, viaNode: Int, outEdge: Int): Long

    /**
     * This method can be used to check whether or not this weighting returns turn costs (or if they are all zero).
     * This is sometimes needed to do safety checks as not all graph algorithms can be run edge-based and might yield
     * wrong results when turn costs are applied while running node-based.
     */
    fun hasTurnCosts(): Boolean

    val name: String

    companion object {
        // mirrors the Java `assert` statements this method had: the checks only run when
        // assertions are enabled (-ea) for this class, and the condition is not even
        // evaluated otherwise (hot path)
        private val ASSERTIONS_ENABLED = Weighting::class.java.desiredAssertionStatus()

        @JvmStatic
        fun isValidName(name: String?): Boolean {
            if (name == null || name.isEmpty())
                return false

            return name.matches("[\\|_a-z]+".toRegex())
        }

        @JvmStatic
        fun roundWeight(w: Double): Double {
            if (ASSERTIONS_ENABLED) {
                if (w.isNaN()) throw AssertionError("weights should not be NaN")
                if (w < 0) throw AssertionError("weights should be >= 0, got: $w")
            }
            if (w.isInfinite()) return Double.POSITIVE_INFINITY
            if (w != 0.0 && w < 0.5)
                // we round up to weight 1, because weight 0 introduces ambiguity for shortest paths
                return 1.0
            // Math.round (floor(w + 0.5)) on purpose: kotlin.math.round uses half-even ties
            return Math.round(w).toDouble()
        }
    }
}
