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
import com.graphhopper.routing.util.TraversalMode
import com.graphhopper.routing.weighting.QueryGraphWeighting
import com.graphhopper.routing.weighting.Weighting
import com.graphhopper.storage.Graph
import com.graphhopper.storage.NodeAccess
import com.graphhopper.util.EdgeExplorer
import com.graphhopper.util.EdgeIteratorState

/**
 * @param graph         specifies the graph where this algorithm will run on
 * @param weighting     set the used weight calculation (e.g. fastest, shortest).
 * @param traversalMode how the graph is traversed e.g. if via nodes or edges.
 *
 * @author Peter Karich
 */
abstract class AbstractRoutingAlgorithm(
    graph: Graph,
    weighting: Weighting,
    traversalMode: TraversalMode
) : RoutingAlgorithm {

    @JvmField
    protected val graph: Graph

    @JvmField
    protected val weighting: Weighting

    @JvmField
    protected val traversalMode: TraversalMode

    @JvmField
    protected val nodeAccess: NodeAccess

    @JvmField
    protected val edgeExplorer: EdgeExplorer

    @JvmField
    protected var maxVisitedNodes = Int.MAX_VALUE

    @JvmField
    protected var timeoutMillis = Long.MAX_VALUE

    private var finishTimeMillis = Long.MAX_VALUE
    private var alreadyRun = false

    init {
        check(!(weighting.hasTurnCosts() && !traversalMode.isEdgeBased)) {
            "Weightings supporting turn costs cannot be used with node-based traversal mode"
        }
        check(!(graph is QueryGraph && weighting !is QueryGraphWeighting)) {
            "Weighting must use QueryGraphWeighting"
        }
        this.weighting = weighting
        this.traversalMode = traversalMode
        this.graph = graph
        this.nodeAccess = graph.nodeAccess
        this.edgeExplorer = graph.createEdgeExplorer()
    }

    override fun setMaxVisitedNodes(numberOfNodes: Int) {
        this.maxVisitedNodes = numberOfNodes
    }

    override fun setTimeoutMillis(timeoutMillis: Long) {
        this.timeoutMillis = timeoutMillis
    }

    protected open fun accept(iter: EdgeIteratorState, prevOrNextEdgeId: Int): Boolean {
        // for edge-based traversal we leave it for calcTurnWeight to decide whether or not a u-turn is acceptable,
        // but for node-based traversal we exclude such a turn for performance reasons already here
        return traversalMode.isEdgeBased || iter.edge != prevOrNextEdgeId
    }

    protected open fun checkAlreadyRun() {
        check(!alreadyRun) { "Create a new instance per call" }
        alreadyRun = true
    }

    protected open fun setupFinishTime() {
        finishTimeMillis = try {
            Math.addExact(System.currentTimeMillis(), timeoutMillis)
        } catch (e: ArithmeticException) {
            Long.MAX_VALUE
        }
    }

    override fun calcPaths(from: Int, to: Int): List<Path> = listOf(calcPath(from, to))

    protected open fun createEmptyPath(): Path = Path(graph)

    override fun getName(): String = javaClass.simpleName

    override fun toString(): String = getName() + "|" + weighting

    protected open fun isMaxVisitedNodesExceeded(): Boolean = maxVisitedNodes < getVisitedNodes()

    protected open fun isTimeoutExceeded(): Boolean =
        finishTimeMillis < Long.MAX_VALUE && System.currentTimeMillis() > finishTimeMillis
}
