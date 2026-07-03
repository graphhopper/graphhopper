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

import com.graphhopper.routing.weighting.Weighting
import com.graphhopper.storage.Graph
import com.graphhopper.util.ArrayUtil
import com.graphhopper.util.EdgeIterator
import com.graphhopper.util.GHUtility
import com.graphhopper.util.StopWatch

open class PathExtractor protected constructor(private val graph: Graph, private val weighting: Weighting) {
    @JvmField
    protected val path: Path = Path(graph)

    companion object {
        @JvmStatic
        fun extractPath(graph: Graph, weighting: Weighting, sptEntry: SPTEntry?): Path {
            return PathExtractor(graph, weighting).extract(sptEntry)
        }
    }

    protected fun extract(sptEntry: SPTEntry?): Path {
        if (sptEntry == null) {
            // path not found
            return path
        }
        val sw = StopWatch().start()
        extractPath(sptEntry)
        path.setFound(true)
        path.setWeight(sptEntry.weight)
        setExtractionTime(sw.stop().getNanos())
        return path
    }

    private fun extractPath(sptEntry: SPTEntry) {
        val currEdge = followParentsUntilRoot(sptEntry)
        ArrayUtil.reverse(path.getEdges())
        path.setFromNode(currEdge.adjNode)
        path.setEndNode(sptEntry.adjNode)
    }

    private fun followParentsUntilRoot(sptEntry: SPTEntry): SPTEntry {
        var currEntry = sptEntry
        var parentEntry = currEntry.parent
        while (EdgeIterator.Edge.isValid(currEntry.edge)) {
            onEdge(currEntry.edge, currEntry.adjNode, parentEntry!!.edge)
            currEntry = currEntry.parent!!
            parentEntry = currEntry.parent
        }
        return currEntry
    }

    private fun setExtractionTime(nanos: Long) {
        path.setDebugInfo("path extraction: " + nanos / 1000 + " μs")
    }

    protected open fun onEdge(edge: Int, adjNode: Int, prevEdge: Int) {
        val edgeState = graph.getEdgeIteratorState(edge, adjNode)!!
        path.addDistance_mm(edgeState.distance_mm)
        path.addTime(GHUtility.calcMillisWithTurnMillis(weighting, edgeState, false, prevEdge))
        path.addEdge(edge)
    }
}
