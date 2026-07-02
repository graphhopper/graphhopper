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

import com.graphhopper.routing.ev.DecimalEncodedValue
import com.graphhopper.storage.TurnCostStorage
import com.graphhopper.util.EdgeIterator
import com.graphhopper.util.EdgeIteratorState
import kotlin.math.max

open class SpeedWeighting(
    private val speedEnc: DecimalEncodedValue,
    private val turnCostProvider: TurnCostProvider
) : Weighting {

    constructor(speedEnc: DecimalEncodedValue) : this(speedEnc, TurnCostProvider.NO_TURN_COST_PROVIDER)

    constructor(speedEnc: DecimalEncodedValue, turnCostEnc: DecimalEncodedValue?, turnCostStorage: TurnCostStorage?, uTurnCosts: Double) :
            this(speedEnc, createTurnCostProvider(turnCostEnc, turnCostStorage, uTurnCosts))

    override fun calcMinWeightPerDistance(): Double {
        return 10.0 / speedEnc.maxStorableDecimal
    }

    override fun calcEdgeWeight(edgeState: EdgeIteratorState, reverse: Boolean): Double {
        val speed = if (reverse) edgeState.getReverse(speedEnc) else edgeState.get(speedEnc)
        if (speed == 0.0) return Double.POSITIVE_INFINITY
        return Weighting.roundWeight(10 * edgeState.distance / speed)
    }

    override fun calcEdgeMillis(edgeState: EdgeIteratorState, reverse: Boolean): Long {
        val speed = if (reverse) edgeState.getReverse(speedEnc) else edgeState.get(speedEnc)
        if (speed == 0.0) return Long.MAX_VALUE
        return (1000 * (edgeState.distance / speed)).toLong()
    }

    override fun calcTurnWeight(inEdge: Int, viaNode: Int, outEdge: Int): Double {
        val turnWeight = turnCostProvider.calcTurnWeight(inEdge, viaNode, outEdge)
        return Weighting.roundWeight(10 * turnWeight)
    }

    override fun calcTurnMillis(inEdge: Int, viaNode: Int, outEdge: Int): Long {
        return turnCostProvider.calcTurnMillis(inEdge, viaNode, outEdge)
    }

    override fun hasTurnCosts(): Boolean {
        return turnCostProvider !== TurnCostProvider.NO_TURN_COST_PROVIDER
    }

    override fun toString(): String {
        return name
    }

    override val name: String
        get() = "speed"

    companion object {
        private fun createTurnCostProvider(
            turnCostEnc: DecimalEncodedValue?,
            turnCostStorage: TurnCostStorage?,
            uTurnCosts: Double
        ): TurnCostProvider {
            if (turnCostStorage == null || turnCostEnc == null)
                throw IllegalArgumentException("This SpeedWeighting constructor expects turnCostEnc and turnCostStorage to be != null")
            if (uTurnCosts < 0) throw IllegalArgumentException("u-turn costs must be positive")
            return object : TurnCostProvider {
                override fun calcTurnWeight(inEdge: Int, viaNode: Int, outEdge: Int): Double {
                    if (!EdgeIterator.Edge.isValid(inEdge) || !EdgeIterator.Edge.isValid(outEdge))
                        return 0.0
                    return if (inEdge == outEdge)
                        max(turnCostStorage.get(turnCostEnc, inEdge, viaNode, outEdge), uTurnCosts)
                    else
                        turnCostStorage.get(turnCostEnc, inEdge, viaNode, outEdge)
                }

                override fun calcTurnMillis(inEdge: Int, viaNode: Int, outEdge: Int): Long {
                    return (1000 * calcTurnWeight(inEdge, viaNode, outEdge)).toLong()
                }
            }
        }
    }
}
