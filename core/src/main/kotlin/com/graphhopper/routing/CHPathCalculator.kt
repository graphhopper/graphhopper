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

import com.graphhopper.routing.ch.CHRoutingAlgorithmFactory
import com.graphhopper.util.EdgeIterator.Companion.ANY_EDGE
import com.graphhopper.util.PMap
import com.graphhopper.util.Parameters.Routing.MAX_VISITED_NODES
import com.graphhopper.util.StopWatch
import com.graphhopper.util.exceptions.MaximumNodesExceededException
import java.util.Collections

class CHPathCalculator(private val algoFactory: CHRoutingAlgorithmFactory, private val algoOpts: PMap) : PathCalculator {
    private var debug: String? = null
    private var visitedNodes = 0

    override fun calcPaths(from: Int, to: Int, edgeRestrictions: EdgeRestrictions): List<Path> {
        if (!edgeRestrictions.getUnfavoredEdges().isEmpty)
            throw IllegalArgumentException("Using unfavored edges is currently not supported for CH")
        val algo = createAlgo()
        return calcPaths(from, to, edgeRestrictions, algo)
    }

    private fun createAlgo(): EdgeToEdgeRoutingAlgorithm {
        val sw = StopWatch().start()
        val algo = algoFactory.createAlgo(algoOpts)
        debug = ", algoInit:" + (sw.stop().getNanos() / 1000) + " μs"
        return algo
    }

    private fun calcPaths(from: Int, to: Int, edgeRestrictions: EdgeRestrictions, algo: EdgeToEdgeRoutingAlgorithm): List<Path> {
        val sw = StopWatch().start()
        val paths: List<Path>
        if (edgeRestrictions.getSourceOutEdge() != ANY_EDGE || edgeRestrictions.getTargetInEdge() != ANY_EDGE) {
            paths = Collections.singletonList(
                algo.calcPath(
                    from, to,
                    edgeRestrictions.getSourceOutEdge(),
                    edgeRestrictions.getTargetInEdge()
                )
            )
        } else {
            paths = algo.calcPaths(from, to)
        }
        if (paths.isEmpty())
            throw IllegalStateException("Path list was empty for $from -> $to")
        val maxVisitedNodes = algoOpts.getInt(MAX_VISITED_NODES, Int.MAX_VALUE)
        if (algo.getVisitedNodes() >= maxVisitedNodes)
            throw MaximumNodesExceededException("No path found due to maximum nodes exceeded $maxVisitedNodes", maxVisitedNodes)
        visitedNodes = algo.getVisitedNodes()
        debug += ", " + algo.getName() + "-routing:" + sw.stop().getMillis() + " ms"
        return paths
    }

    override fun getDebugString(): String? = debug

    override fun getVisitedNodes(): Int = visitedNodes
}
