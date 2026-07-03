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

package com.graphhopper.routing.ch

import com.graphhopper.routing.AStarBidirectionCH
import com.graphhopper.routing.AStarBidirectionEdgeCHNoSOD
import com.graphhopper.routing.AlternativeRouteCH
import com.graphhopper.routing.AlternativeRouteEdgeCH
import com.graphhopper.routing.DijkstraBidirectionCH
import com.graphhopper.routing.DijkstraBidirectionCHNoSOD
import com.graphhopper.routing.DijkstraBidirectionEdgeCHNoSOD
import com.graphhopper.routing.EdgeToEdgeRoutingAlgorithm
import com.graphhopper.routing.RoutingAlgorithmFactorySimple
import com.graphhopper.routing.querygraph.QueryGraph
import com.graphhopper.routing.querygraph.QueryRoutingCHGraph
import com.graphhopper.routing.weighting.Weighting
import com.graphhopper.storage.RoutingCHGraph
import com.graphhopper.util.Helper
import com.graphhopper.util.PMap
import com.graphhopper.util.Parameters.Algorithms.ALT_ROUTE
import com.graphhopper.util.Parameters.Algorithms.ASTAR_BI
import com.graphhopper.util.Parameters.Algorithms.DIJKSTRA_BI
import com.graphhopper.util.Parameters.Routing.ALGORITHM
import com.graphhopper.util.Parameters.Routing.MAX_VISITED_NODES
import com.graphhopper.util.Parameters.Routing.TIMEOUT_MS

/**
 * Given a [RoutingCHGraph] and possibly a [QueryGraph] this class sets up and creates routing
 * algorithm instances used for CH.
 */
class CHRoutingAlgorithmFactory(private val routingCHGraph: RoutingCHGraph) {

    constructor(routingCHGraph: RoutingCHGraph, queryGraph: QueryGraph) :
            this(QueryRoutingCHGraph(routingCHGraph, queryGraph))

    fun createAlgo(opts: PMap): EdgeToEdgeRoutingAlgorithm {
        val algo = if (routingCHGraph.isEdgeBased)
            createAlgoEdgeBased(routingCHGraph, opts)
        else
            createAlgoNodeBased(routingCHGraph, opts)
        if (opts.has(MAX_VISITED_NODES))
            algo.setMaxVisitedNodes(opts.getInt(MAX_VISITED_NODES, Integer.MAX_VALUE))
        if (opts.has(TIMEOUT_MS))
            algo.setTimeoutMillis(opts.getLong(TIMEOUT_MS, Long.MAX_VALUE))
        return algo
    }

    private fun createAlgoEdgeBased(g: RoutingCHGraph, opts: PMap): EdgeToEdgeRoutingAlgorithm {
        val defaultAlgo = ASTAR_BI
        var algo = opts.getString(ALGORITHM, defaultAlgo)
        if (Helper.isEmpty(algo))
            algo = defaultAlgo
        return if (ASTAR_BI == algo) {
            AStarBidirectionEdgeCHNoSOD(g)
                .setApproximation(RoutingAlgorithmFactorySimple.getApproximation(ASTAR_BI, opts, getWeighting(), g.baseGraph.nodeAccess))
        } else if (DIJKSTRA_BI == algo) {
            DijkstraBidirectionEdgeCHNoSOD(g)
        } else if (ALT_ROUTE.equals(algo, ignoreCase = true)) {
            AlternativeRouteEdgeCH(g, opts)
        } else {
            throw IllegalArgumentException("Algorithm $algo not supported for edge-based Contraction Hierarchies. Try with ch.disable=true")
        }
    }

    private fun createAlgoNodeBased(g: RoutingCHGraph, opts: PMap): EdgeToEdgeRoutingAlgorithm {
        // use dijkstra by default for node-based (its faster)
        val defaultAlgo = DIJKSTRA_BI
        var algo = opts.getString(ALGORITHM, defaultAlgo)
        if (Helper.isEmpty(algo))
            algo = defaultAlgo
        return if (ASTAR_BI == algo) {
            AStarBidirectionCH(g)
                .setApproximation(RoutingAlgorithmFactorySimple.getApproximation(ASTAR_BI, opts, getWeighting(), g.baseGraph.nodeAccess))
        } else if (DIJKSTRA_BI == algo || Helper.isEmpty(algo)) {
            if (opts.getBool("stall_on_demand", true)) {
                DijkstraBidirectionCH(g)
            } else {
                DijkstraBidirectionCHNoSOD(g)
            }
        } else if (ALT_ROUTE.equals(algo, ignoreCase = true)) {
            AlternativeRouteCH(g, opts)
        } else {
            throw IllegalArgumentException("Algorithm $algo not supported for node-based Contraction Hierarchies. Try with ch.disable=true")
        }
    }

    private fun getWeighting(): Weighting = routingCHGraph.weighting
}
