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

package com.graphhopper.routing.lm

import com.graphhopper.routing.AStar
import com.graphhopper.routing.AStarBidirection
import com.graphhopper.routing.AlgorithmOptions
import com.graphhopper.routing.AlternativeRoute
import com.graphhopper.routing.RoutingAlgorithm
import com.graphhopper.routing.RoutingAlgorithmFactory
import com.graphhopper.routing.weighting.Weighting
import com.graphhopper.storage.Graph
import com.graphhopper.util.Helper
import com.graphhopper.util.Parameters
import com.graphhopper.util.Parameters.Algorithms.ALT_ROUTE
import com.graphhopper.util.Parameters.Algorithms.ASTAR
import com.graphhopper.util.Parameters.Algorithms.ASTAR_BI

class LMRoutingAlgorithmFactory(private val lms: LandmarkStorage) : RoutingAlgorithmFactory {

    private var defaultActiveLandmarks: Int = Math.max(1, Math.min(lms.getLandmarkCount() / 2, 12))

    fun setDefaultActiveLandmarks(defaultActiveLandmarks: Int): LMRoutingAlgorithmFactory {
        this.defaultActiveLandmarks = defaultActiveLandmarks
        return this
    }

    override fun createAlgo(g: Graph, w: Weighting, opts: AlgorithmOptions): RoutingAlgorithm {
        if (!lms.isInitialized())
            throw IllegalStateException("Initialize landmark storage before creating algorithms")
        val activeLM = Math.max(1, opts.getHints().getInt(Parameters.Landmark.ACTIVE_COUNT, defaultActiveLandmarks))
        val algoStr = opts.getAlgorithm()
        val weighting = g.wrapWeighting(w)
        if (ASTAR.equals(algoStr, ignoreCase = true)) {
            val epsilon = opts.getHints().getDouble(Parameters.Algorithms.AStar.EPSILON, 1.0)
            val algo = AStar(g, weighting, opts.getTraversalMode())
            algo.setApproximation(getApproximator(g, weighting, activeLM, epsilon))
            algo.setMaxVisitedNodes(opts.getMaxVisitedNodes())
            algo.setTimeoutMillis(opts.getTimeoutMillis())
            return algo
        } else if (ASTAR_BI.equals(algoStr, ignoreCase = true) || Helper.isEmpty(algoStr)) {
            val epsilon = opts.getHints().getDouble(Parameters.Algorithms.AStarBi.EPSILON, 1.0)
            val algo = AStarBidirection(g, weighting, opts.getTraversalMode())
            algo.setApproximation(getApproximator(g, weighting, activeLM, epsilon))
            algo.setMaxVisitedNodes(opts.getMaxVisitedNodes())
            algo.setTimeoutMillis(opts.getTimeoutMillis())
            return algo
        } else if (ALT_ROUTE.equals(algoStr, ignoreCase = true)) {
            val epsilon = opts.getHints().getDouble(Parameters.Algorithms.AStarBi.EPSILON, 1.0)
            val algo = AlternativeRoute(g, weighting, opts.getTraversalMode(), opts.getHints())
            algo.setApproximation(getApproximator(g, weighting, activeLM, epsilon))
            algo.setMaxVisitedNodes(opts.getMaxVisitedNodes())
            algo.setTimeoutMillis(opts.getTimeoutMillis())
            return algo
        } else {
            throw IllegalArgumentException("Landmarks algorithm only supports algorithm="
                    + ASTAR + "," + ASTAR_BI + " or " + ALT_ROUTE + ", but got: " + algoStr)
        }
    }

    private fun getApproximator(g: Graph, weighting: Weighting, activeLM: Int, epsilon: Double): LMApproximator {
        return LMApproximator(g, g.wrapWeighting(lms.getWeighting()), weighting, lms, activeLM, false).setEpsilon(epsilon)
    }
}
