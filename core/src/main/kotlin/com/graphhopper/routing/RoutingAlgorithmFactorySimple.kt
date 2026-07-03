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
package com.graphhopper.routing

import com.graphhopper.routing.weighting.BeelineWeightApproximator
import com.graphhopper.routing.weighting.WeightApproximator
import com.graphhopper.routing.weighting.Weighting
import com.graphhopper.storage.Graph
import com.graphhopper.storage.NodeAccess
import com.graphhopper.util.DistanceCalcEarth
import com.graphhopper.util.DistancePlaneProjection
import com.graphhopper.util.Helper
import com.graphhopper.util.PMap
import com.graphhopper.util.Parameters.Algorithms.ALT_ROUTE
import com.graphhopper.util.Parameters.Algorithms.ASTAR
import com.graphhopper.util.Parameters.Algorithms.ASTAR_BI
import com.graphhopper.util.Parameters.Algorithms.DIJKSTRA
import com.graphhopper.util.Parameters.Algorithms.DIJKSTRA_BI
import com.graphhopper.util.Parameters.Algorithms.DIJKSTRA_ONE_TO_MANY

/**
 * A simple factory creating normal algorithms (RoutingAlgorithm) without preparation.
 *
 * @author Peter Karich
 */
class RoutingAlgorithmFactorySimple : RoutingAlgorithmFactory {

    override fun createAlgo(g: Graph, w: Weighting, opts: AlgorithmOptions): RoutingAlgorithm {
        val ra: RoutingAlgorithm
        val algoStr = opts.getAlgorithm()
        val weighting = g.wrapWeighting(w)
        if (DIJKSTRA_BI.equals(algoStr, ignoreCase = true)) {
            ra = DijkstraBidirectionRef(g, weighting, opts.getTraversalMode())
        } else if (DIJKSTRA.equals(algoStr, ignoreCase = true)) {
            ra = Dijkstra(g, weighting, opts.getTraversalMode())
        } else if (ASTAR_BI.equals(algoStr, ignoreCase = true) || Helper.isEmpty(algoStr)) {
            val aStarBi = AStarBidirection(g, weighting, opts.getTraversalMode())
            aStarBi.setApproximation(getApproximation(ASTAR_BI, opts.getHints(), weighting, g.nodeAccess))
            ra = aStarBi
        } else if (DIJKSTRA_ONE_TO_MANY.equals(algoStr, ignoreCase = true)) {
            ra = DijkstraOneToMany(g, weighting, opts.getTraversalMode())
        } else if (ASTAR.equals(algoStr, ignoreCase = true)) {
            val aStar = AStar(g, weighting, opts.getTraversalMode())
            aStar.setApproximation(getApproximation(ASTAR, opts.getHints(), w, g.nodeAccess))
            ra = aStar
        } else if (ALT_ROUTE.equals(algoStr, ignoreCase = true)) {
            ra = AlternativeRoute(g, weighting, opts.getTraversalMode(), opts.getHints())
        } else {
            throw IllegalArgumentException("Algorithm " + algoStr + " not found in " + javaClass.name)
        }

        ra.setMaxVisitedNodes(opts.getMaxVisitedNodes())
        ra.setTimeoutMillis(opts.getTimeoutMillis())
        return ra
    }

    companion object {
        @JvmStatic
        fun getApproximation(prop: String, opts: PMap, weighting: Weighting, na: NodeAccess): WeightApproximator {
            val approxAsStr = opts.getString("$prop.approximation", "BeelineSimplification")
            val epsilon = opts.getDouble("$prop.epsilon", 1.0)

            val approx = BeelineWeightApproximator(na, weighting)
            approx.setEpsilon(epsilon)
            if ("BeelineSimplification" == approxAsStr)
                approx.setDistanceCalc(DistancePlaneProjection.DIST_PLANE)
            else if ("BeelineAccurate" == approxAsStr)
                approx.setDistanceCalc(DistanceCalcEarth.DIST_EARTH)
            else
                throw IllegalArgumentException("Approximation " + approxAsStr + " not found in " + RoutingAlgorithmFactorySimple::class.java.name)

            return approx
        }
    }
}
