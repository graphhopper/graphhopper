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

/**
 * Implementations of this interface define how turn costs and turn times are calculated.
 */
interface TurnCostProvider {
    /**
     * @return the turn weight of a transitions from the edge with id `inEdge` to the edge with id
     * `outEdge` at the node with id `viaNode`
     */
    fun calcTurnWeight(inEdge: Int, viaNode: Int, outEdge: Int): Double

    /**
     * @return the time it takes to take a turn in milli-seconds
     * @see calcTurnWeight
     */
    fun calcTurnMillis(inEdge: Int, viaNode: Int, outEdge: Int): Long

    companion object {
        @JvmField
        val NO_TURN_COST_PROVIDER: TurnCostProvider = object : TurnCostProvider {
            override fun calcTurnWeight(inEdge: Int, viaNode: Int, outEdge: Int): Double {
                return 0.0
            }

            override fun calcTurnMillis(inEdge: Int, viaNode: Int, outEdge: Int): Long {
                return 0
            }
        }
    }
}
