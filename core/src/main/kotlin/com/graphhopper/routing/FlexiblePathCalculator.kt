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

import com.graphhopper.routing.querygraph.QueryGraph
import com.graphhopper.routing.weighting.Weighting
import com.graphhopper.util.EdgeIterator.Companion.ANY_EDGE
import com.graphhopper.util.Parameters
import com.graphhopper.util.StopWatch
import com.graphhopper.util.exceptions.MaximumNodesExceededException
import java.util.Collections

class FlexiblePathCalculator(
    private val queryGraph: QueryGraph,
    private val algoFactory: RoutingAlgorithmFactory,
    private var weighting: Weighting,
    private val algoOpts: AlgorithmOptions
) : PathCalculator {
    private var debug: String? = null
    private var visitedNodes = 0

    override fun calcPaths(from: Int, to: Int, edgeRestrictions: EdgeRestrictions): List<Path> {
        val algo = createAlgo()
        return calcPaths(from, to, edgeRestrictions, algo)
    }

    private fun createAlgo(): RoutingAlgorithm {
        val sw = StopWatch().start()
        val algo = algoFactory.createAlgo(queryGraph, weighting, algoOpts)
        debug = ", algoInit:" + (sw.stop().getNanos() / 1000) + " μs"
        return algo
    }

    private fun calcPaths(from: Int, to: Int, edgeRestrictions: EdgeRestrictions, algo: RoutingAlgorithm): List<Path> {
        val sw = StopWatch().start()
        // todo: so far 'heading' is implemented like this: we mark the unfavored edges on the query graph and then
        // our weighting applies a penalty to these edges. however, this only works for virtual edges and to make
        // this compatible with edge-based routing we would have to use edge keys instead of edge ids. either way a
        // better approach seems to be making the weighting (or the algorithm for that matter) aware of the unfavored
        // edges directly without changing the graph
        queryGraph.unfavorVirtualEdges(edgeRestrictions.getUnfavoredEdges())

        val paths: List<Path>
        if (edgeRestrictions.getSourceOutEdge() != ANY_EDGE || edgeRestrictions.getTargetInEdge() != ANY_EDGE) {
            if (algo !is EdgeToEdgeRoutingAlgorithm)
                throw IllegalArgumentException("To make use of the " + Parameters.Routing.CURBSIDE + " parameter you need a bidirectional algorithm, got: " + algo.getName())
            paths = Collections.singletonList(algo.calcPath(from, to, edgeRestrictions.getSourceOutEdge(), edgeRestrictions.getTargetInEdge()))
        } else {
            paths = algo.calcPaths(from, to)
        }

        // reset all direction enforcements in queryGraph to avoid influencing next path
        // note that afterwards for path processing (like instructions) there will not be a penalty for the unfavored
        // edges so the edge weight calculated then will be different to the one we used when calculating the route
        queryGraph.clearUnfavoredStatus()

        if (paths.isEmpty())
            throw IllegalStateException("Path list was empty for $from -> $to")
        if (algo.getVisitedNodes() >= algoOpts.getMaxVisitedNodes())
            throw MaximumNodesExceededException("No path found due to maximum nodes exceeded " + algoOpts.getMaxVisitedNodes(), algoOpts.getMaxVisitedNodes())
        visitedNodes = algo.getVisitedNodes()
        debug += ", " + algo.getName() + "-routing:" + sw.stop().getMillis() + " ms"
        return paths
    }

    override fun getDebugString(): String? = debug

    override fun getVisitedNodes(): Int = visitedNodes

    fun getWeighting(): Weighting = weighting

    fun setWeighting(weighting: Weighting) {
        this.weighting = weighting
    }
}
