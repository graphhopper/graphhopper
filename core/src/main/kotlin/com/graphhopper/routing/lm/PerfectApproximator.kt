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

import com.graphhopper.routing.Dijkstra
import com.graphhopper.routing.util.TraversalMode
import com.graphhopper.routing.weighting.WeightApproximator
import com.graphhopper.routing.weighting.Weighting
import com.graphhopper.storage.Graph

/**
 * Just a sanity-check implementation for WeightApproximator, which 'approximates' perfectly.
 */
class PerfectApproximator(
    private val graph: Graph,
    private val weighting: Weighting,
    private val traversalMode: TraversalMode,
    private val reverse: Boolean
) : WeightApproximator {

    private var to = 0

    override fun approximate(currentNode: Int): Double {
        val dijkstra = Dijkstra(graph, weighting, traversalMode)
        val path = if (reverse) dijkstra.calcPath(to, currentNode) else dijkstra.calcPath(currentNode, to)
        return if (path.isFound()) path.getWeight() else Double.POSITIVE_INFINITY
    }

    override fun setTo(to: Int) {
        this.to = to
    }

    override fun reverse(): WeightApproximator = PerfectApproximator(graph, weighting, traversalMode, !reverse)

    override val slack: Double
        get() = 0.0
}
