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

import com.graphhopper.storage.NodeAccess
import com.graphhopper.util.DistanceCalc
import com.graphhopper.util.DistanceCalcEarth

/**
 * Approximates the distance to the goal node by weighting the beeline distance according to the
 * distance weighting
 *
 * @author jansoe
 */
class BeelineWeightApproximator(private val nodeAccess: NodeAccess, private val weighting: Weighting) : WeightApproximator {
    private val minWeightPerDistance: Double = weighting.calcMinWeightPerDistance()
    private var distanceCalc: DistanceCalc = DistanceCalcEarth.DIST_EARTH
    private var toLat = 0.0
    private var toLon = 0.0
    private var epsilon = 1.0

    override fun setTo(to: Int) {
        toLat = nodeAccess.getLat(to)
        toLon = nodeAccess.getLon(to)
    }

    fun setEpsilon(epsilon: Double): WeightApproximator {
        this.epsilon = epsilon
        return this
    }

    override fun reverse(): WeightApproximator {
        return BeelineWeightApproximator(nodeAccess, weighting).setDistanceCalc(distanceCalc).setEpsilon(epsilon)
    }

    override val slack: Double
        get() = 0.0

    override fun approximate(currentNode: Int): Double {
        val fromLat = nodeAccess.getLat(currentNode)
        val fromLon = nodeAccess.getLon(currentNode)
        val dist2goal = distanceCalc.calcDist(toLat, toLon, fromLat, fromLon)
        val weight2goal = minWeightPerDistance * dist2goal
        return weight2goal * epsilon
    }

    fun setDistanceCalc(distanceCalc: DistanceCalc): BeelineWeightApproximator {
        this.distanceCalc = distanceCalc
        return this
    }

    override fun toString(): String {
        return "beeline"
    }
}
