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

/**
 * Builds a [Path] from the two fwd- and bwd-shortest path tree entries of a bidirectional search
 *
 * @author Peter Karich
 * @author easbar
 */
open class DefaultBidirPathExtractor internal constructor(
    private val graph: Graph,
    private val weighting: Weighting?
) : BidirPathExtractor {

    @JvmField
    protected val path: Path = Path(graph)

    companion object {
        @JvmStatic
        fun extractPath(graph: Graph, weighting: Weighting, fwdEntry: SPTEntry?, bwdEntry: SPTEntry?, weight: Double): Path {
            return DefaultBidirPathExtractor(graph, weighting).extract(fwdEntry, bwdEntry, weight)
        }
    }

    override fun extract(fwdEntry: SPTEntry?, bwdEntry: SPTEntry?, bestWeight: Double): Path {
        if (fwdEntry == null || bwdEntry == null) {
            // path not found
            return path
        }
        check(fwdEntry.adjNode == bwdEntry.adjNode) {
            "forward and backward entries must have same adjacent nodes, fwdEntry:$fwdEntry, bwdEntry:$bwdEntry"
        }

        val sw = StopWatch().start()
        extractFwdPath(fwdEntry)
        processMeetingPoint(fwdEntry, bwdEntry)
        extractBwdPath(bwdEntry)
        setExtractionTime(sw.stop().getNanos())
        path.setFound(true)
        path.setWeight(bestWeight)
        return path
    }

    protected fun extractFwdPath(sptEntry: SPTEntry) {
        val fwdRoot = followParentsUntilRoot(sptEntry, false)
        onFwdTreeRoot(fwdRoot.adjNode)
        // since we followed the fwd path in backward direction we need to reverse the edge ids
        ArrayUtil.reverse(path.getEdges())
    }

    protected fun extractBwdPath(sptEntry: SPTEntry) {
        val bwdRoot = followParentsUntilRoot(sptEntry, true)
        onBwdTreeRoot(bwdRoot.adjNode)
    }

    protected fun processMeetingPoint(fwdEntry: SPTEntry, bwdEntry: SPTEntry) {
        val inEdge = getIncEdge(fwdEntry)
        val outEdge = getIncEdge(bwdEntry)
        onMeetingPoint(inEdge, fwdEntry.adjNode, outEdge)
    }

    protected fun followParentsUntilRoot(sptEntry: SPTEntry, reverse: Boolean): SPTEntry {
        var currEntry = sptEntry
        var parentEntry = currEntry.parent
        while (EdgeIterator.Edge.isValid(currEntry.edge)) {
            onEdge(currEntry.edge, currEntry.adjNode, reverse, getIncEdge(parentEntry!!))
            currEntry = parentEntry
            parentEntry = currEntry.parent
        }
        return currEntry
    }

    protected fun setExtractionTime(nanos: Long) {
        path.setDebugInfo("path extraction: " + nanos / 1000 + " μs")
    }

    protected open fun getIncEdge(entry: SPTEntry): Int = entry.edge

    protected open fun onFwdTreeRoot(node: Int) {
        path.setFromNode(node)
    }

    protected open fun onBwdTreeRoot(node: Int) {
        path.setEndNode(node)
    }

    protected open fun onEdge(edge: Int, adjNode: Int, reverse: Boolean, prevOrNextEdge: Int) {
        val edgeState = graph.getEdgeIteratorState(edge, adjNode)!!
        path.addDistance_mm(edgeState.distance_mm)
        path.addTime(GHUtility.calcMillisWithTurnMillis(weighting!!, edgeState, reverse, prevOrNextEdge))
        path.addEdge(edge)
    }

    protected open fun onMeetingPoint(inEdge: Int, viaNode: Int, outEdge: Int) {
        if (!EdgeIterator.Edge.isValid(inEdge) || !EdgeIterator.Edge.isValid(outEdge)) {
            return
        }
        path.addTime(weighting!!.calcTurnMillis(inEdge, viaNode, outEdge))
    }
}
