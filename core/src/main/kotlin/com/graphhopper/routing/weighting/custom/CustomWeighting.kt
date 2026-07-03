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
package com.graphhopper.routing.weighting.custom

import com.graphhopper.routing.ev.EdgeIntAccess
import com.graphhopper.routing.weighting.TurnCostProvider
import com.graphhopper.routing.weighting.Weighting
import com.graphhopper.storage.BaseGraph
import com.graphhopper.util.CustomModel
import com.graphhopper.util.EdgeIteratorState

/**
 * The CustomWeighting allows adjusting the edge weights relative to those we'd obtain for a given base flag encoder.
 * For example a car flag encoder already provides speeds and access flags for every edge depending on certain edge
 * properties. By default the CustomWeighting simply makes use of these values, but it is possible to adjust them by
 * setting up rules that apply changes depending on the edges' encoded values.
 *
 * The formula for the edge weights is as follows:
 *
 * weight = distance/speed + distance_costs + stress_costs
 *
 * The first term simply corresponds to the time it takes to travel along the edge.
 * The second term adds a fixed per-distance cost that is proportional to the distance but *independent* of the edge
 * properties, i.e. it reads
 *
 * distance_costs = distance * distance_influence
 *
 * The third term is also proportional to the distance but compared to the second it describes additional costs that *do*
 * depend on the edge properties. It can represent any kind of costs that depend on the edge (like inconvenience or
 * dangers encountered on 'high-stress' roads for bikes, toll roads (because they cost money), stairs (because they are
 * awkward when going by bike) etc.). This 'stress' term reads
 *
 * stress_costs = distance * stress_per_meter
 *
 * and just like the distance term it describes costs measured in seconds. When modelling it, one always has to 'convert'
 * the costs into some time equivalent (e.g. for toll roads one has to think about how much money can be spent to save
 * a certain amount of time). Note that the distance_costs described by the second term in general cannot be properly
 * described by the stress costs, because the distance term allows increasing the per-distance costs per-se (regardless
 * of the type of the road). Also note that both the second and third term are different to the first in that they can
 * increase the edge costs but do *not* modify the travel *time*.
 *
 * Instead of letting you set the speed directly, `CustomWeighting` allows changing the speed relative to the speed we
 * get from the base flag encoder. The stress costs can be specified by using a factor between 0 and 1 that is called
 * 'priority'.
 *
 * Therefore the full edge weight formula reads:
 * <pre>
 * weight = distance / (base_speed * speed_factor * priority)
 *        + distance * distance_influence
 * </pre>
 *
 * The open parameters that we can adjust are therefore: speed_factor, priority and distance_influence and they are
 * specified via the [CustomModel]. The speed can also be restricted to a maximum value, in which case the value
 * calculated via the speed_factor is simply overwritten. Edges that are not accessible according to the access flags of
 * the base vehicle always get assigned an infinite weight and this cannot be changed (yet) using this weighting.
 */
class CustomWeighting(turnCostProvider: TurnCostProvider, parameters: Parameters) : Weighting {

    private val distanceInfluence: Double
    private val headingPenaltySeconds: Double
    private val edgeToSpeedMapping: EdgeToDoubleMapping
    private val edgeToPriorityMapping: EdgeToDoubleMapping
    private val turnCostProvider: TurnCostProvider
    private val maxPrioCalc: MaxCalc
    private val maxSpeedCalc: MaxCalc

    init {
        if (!Weighting.isValidName(name))
            throw IllegalStateException("Not a valid name for a Weighting: " + name)
        this.turnCostProvider = turnCostProvider

        this.edgeToSpeedMapping = parameters.edgeToSpeedMapping
        this.maxSpeedCalc = parameters.maxSpeedCalc

        this.edgeToPriorityMapping = parameters.edgeToPriorityMapping
        this.maxPrioCalc = parameters.maxPrioCalc

        this.headingPenaltySeconds = parameters.headingPenaltySeconds

        // given unit is s/km -> convert to s/m
        this.distanceInfluence = parameters.distanceInfluence / 1000.0
        if (this.distanceInfluence < 0)
            throw IllegalArgumentException("distance_influence cannot be negative " + this.distanceInfluence)
    }

    override fun calcMinWeightPerDistance(): Double {
        return 10 * (1.0 / (maxSpeedCalc.calcMax() / SPEED_CONV) / maxPrioCalc.calcMax() + distanceInfluence)
    }

    override fun calcEdgeWeight(edgeState: EdgeIteratorState, reverse: Boolean): Double {
        val priority = edgeToPriorityMapping.get(edgeState, reverse)
        if (priority == 0.0) return Double.POSITIVE_INFINITY

        val distance = edgeState.distance
        var seconds = calcSeconds(distance, edgeState, reverse)
        if (seconds.isInfinite()) return Double.POSITIVE_INFINITY
        // add penalty at start/stop/via points
        if (edgeState.get(EdgeIteratorState.UNFAVORED_EDGE)) seconds += headingPenaltySeconds
        val distanceCosts = distance * distanceInfluence
        if (distanceCosts.isInfinite()) return Double.POSITIVE_INFINITY
        // we limit the weight increase due to priority to 1M (i.e. ~28h). this guards against
        // tiny priority factors (for example applying 0.001 multiple times to the same edge)
        return Weighting.roundWeight(10 * (Math.min(seconds / priority, seconds + 100_000) + distanceCosts))
    }

    internal fun calcSeconds(distance: Double, edgeState: EdgeIteratorState, reverse: Boolean): Double {
        val speed = edgeToSpeedMapping.get(edgeState, reverse)
        if (speed == 0.0)
            return Double.POSITIVE_INFINITY
        if (speed < 0)
            throw IllegalArgumentException("Speed cannot be negative")

        return distance / speed * SPEED_CONV
    }

    override fun calcEdgeMillis(edgeState: EdgeIteratorState, reverse: Boolean): Long {
        return Math.round(calcSeconds(edgeState.distance, edgeState, reverse) * 1000)
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

    override val name: String
        get() = NAME

    fun interface EdgeToDoubleMapping {
        fun get(edge: EdgeIteratorState, reverse: Boolean): Double
    }

    fun interface TurnPenaltyMapping {
        fun get(graph: BaseGraph, edgeIntAccess: EdgeIntAccess, inEdge: Int, viaNode: Int, outEdge: Int): Double
    }

    fun interface MaxCalc {
        fun calcMax(): Double
    }

    class Parameters(
        val edgeToSpeedMapping: EdgeToDoubleMapping,
        val maxSpeedCalc: MaxCalc,
        val edgeToPriorityMapping: EdgeToDoubleMapping,
        val maxPrioCalc: MaxCalc,
        val turnPenaltyMapping: TurnPenaltyMapping,
        val distanceInfluence: Double,
        val headingPenaltySeconds: Double
    )

    companion object {
        const val NAME = "custom"

        /**
         * Converting to seconds is not necessary but makes adding other penalties easier (e.g. turn
         * costs or traffic light costs etc)
         */
        private const val SPEED_CONV = 3.6
    }
}
