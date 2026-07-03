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

import com.graphhopper.coll.GHIntObjectHashMap
import com.graphhopper.routing.util.TraversalMode
import com.graphhopper.util.EdgeIterator
import com.graphhopper.util.EdgeIterator.Companion.ANY_EDGE
import java.util.PriorityQueue

abstract class AbstractBidirAlgo(@JvmField protected val traversalMode: TraversalMode) : EdgeToEdgeRoutingAlgorithm {

    @JvmField
    protected var from = 0

    @JvmField
    protected var to = 0

    @JvmField
    protected var fromOutEdge: Int = ANY_EDGE

    @JvmField
    protected var toInEdge: Int = ANY_EDGE

    protected lateinit var bestWeightMapFrom: GHIntObjectHashMap<SPTEntry>

    protected lateinit var bestWeightMapTo: GHIntObjectHashMap<SPTEntry>

    @JvmField
    protected var bestWeightMapOther: GHIntObjectHashMap<SPTEntry>? = null

    @JvmField
    protected var currFrom: SPTEntry? = null

    @JvmField
    protected var currTo: SPTEntry? = null

    @JvmField
    protected var bestFwdEntry: SPTEntry? = null

    @JvmField
    protected var bestBwdEntry: SPTEntry? = null

    @JvmField
    protected var bestWeight = Double.MAX_VALUE

    @JvmField
    protected var maxVisitedNodes = Int.MAX_VALUE

    @JvmField
    protected var timeoutMillis = Long.MAX_VALUE

    private var finishTimeMillis = Long.MAX_VALUE

    internal lateinit var pqOpenSetFrom: PriorityQueue<SPTEntry>

    internal lateinit var pqOpenSetTo: PriorityQueue<SPTEntry>

    @JvmField
    protected var updateBestPath = true

    @JvmField
    protected var finishedFrom = false

    @JvmField
    protected var finishedTo = false

    @JvmField
    internal var visitedCountFrom = 0

    @JvmField
    internal var visitedCountTo = 0

    private var alreadyRun = false

    protected open fun initCollections(size: Int) {
        pqOpenSetFrom = PriorityQueue(size)
        bestWeightMapFrom = GHIntObjectHashMap(size)

        pqOpenSetTo = PriorityQueue(size)
        bestWeightMapTo = GHIntObjectHashMap(size)
    }

    /**
     * Creates the root shortest path tree entry for the forward or backward search.
     */
    protected abstract fun createStartEntry(node: Int, weight: Double, reverse: Boolean): SPTEntry

    override fun calcPaths(from: Int, to: Int): List<Path> = listOf(calcPath(from, to))

    override fun calcPath(from: Int, to: Int): Path = calcPath(from, to, ANY_EDGE, ANY_EDGE)

    override fun calcPath(from: Int, to: Int, fromOutEdge: Int, toInEdge: Int): Path {
        if ((fromOutEdge != ANY_EDGE || toInEdge != ANY_EDGE) && !traversalMode.isEdgeBased) {
            throw IllegalArgumentException("Restricting the start/target edges is only possible for edge-based graph traversal")
        }
        this.fromOutEdge = fromOutEdge
        this.toInEdge = toInEdge
        checkAlreadyRun()
        setupFinishTime()
        init(from, 0.0, to, 0.0)
        runAlgo()
        return extractPath()
    }

    protected open fun init(from: Int, fromWeight: Double, to: Int, toWeight: Double) {
        initFrom(from, fromWeight)
        initTo(to, toWeight)
        postInit(from, to)
    }

    protected open fun initFrom(from: Int, weight: Double) {
        this.from = from
        val entry = createStartEntry(from, weight, false)
        currFrom = entry
        pqOpenSetFrom.add(entry)
        if (!traversalMode.isEdgeBased) {
            bestWeightMapFrom.put(from, entry)
        }
    }

    protected open fun initTo(to: Int, weight: Double) {
        this.to = to
        val entry = createStartEntry(to, weight, true)
        currTo = entry
        pqOpenSetTo.add(entry)
        if (!traversalMode.isEdgeBased) {
            bestWeightMapTo.put(to, entry)
        }
    }

    protected open fun postInit(from: Int, to: Int) {
        if (!traversalMode.isEdgeBased) {
            if (updateBestPath) {
                bestWeightMapOther = bestWeightMapFrom
                updateBestPath(Double.POSITIVE_INFINITY, currFrom!!, EdgeIterator.NO_EDGE, to, true)
            }
        } else if (from == to && fromOutEdge == ANY_EDGE && toInEdge == ANY_EDGE) {
            // special handling if start and end are the same and no directions are restricted
            // the resulting weight should be zero
            check(!(currFrom!!.weight != 0.0 || currTo!!.weight != 0.0)) {
                "If from=to, the starting weight must be zero for from and to"
            }
            bestFwdEntry = currFrom
            bestBwdEntry = currTo
            bestWeight = 0.0
            finishedFrom = true
            finishedTo = true
            return
        }
        postInitFrom()
        postInitTo()
    }

    protected abstract fun postInitFrom()

    protected abstract fun postInitTo()

    protected open fun runAlgo() {
        while (!finished() && !isMaxVisitedNodesExceeded() && !isTimeoutExceeded()) {
            if (!finishedFrom)
                finishedFrom = !fillEdgesFrom()

            if (!finishedTo)
                finishedTo = !fillEdgesTo()
        }
    }

    // http://www.cs.princeton.edu/courses/archive/spr06/cos423/Handouts/EPP%20shortest%20path%20algorithms.pdf
    // a node from overlap may not be on the best path!
    // => when scanning an arc (v, w) in the forward search and w is scanned in the reverseOrder
    //    search, update extractPath = μ if df (v) + (v, w) + dr (w) < μ
    protected open fun finished(): Boolean {
        if (finishedFrom || finishedTo)
            return true

        return currFrom!!.weight + currTo!!.weight >= bestWeight
    }

    internal abstract fun fillEdgesFrom(): Boolean

    internal abstract fun fillEdgesTo(): Boolean

    protected open fun updateBestPath(edgeWeight: Double, entry: SPTEntry, origEdgeIdForCH: Int, traversalId: Int, reverse: Boolean) {
        assert(traversalMode.isEdgeBased != edgeWeight.isInfinite())
        var entry: SPTEntry? = entry
        val entryOther = bestWeightMapOther!!.get(traversalId) ?: return

        // update μ
        var weight = entry!!.getWeightOfVisitedPath() + entryOther.getWeightOfVisitedPath()
        if (traversalMode.isEdgeBased) {
            check(getIncomingEdge(entryOther) == getIncomingEdge(entry)) {
                "cannot happen for edge based execution of " + getName()
            }

            // prevents the path to contain the edge at the meeting point twice and subtracts the weight (excluding turn weight => no previous edge)
            entry = entry.getParent()
            weight -= edgeWeight
        }

        if (weight < bestWeight) {
            bestFwdEntry = if (reverse) entryOther else entry
            bestBwdEntry = if (reverse) entry else entryOther
            bestWeight = weight
        }
    }

    protected abstract fun getInEdgeWeight(entry: SPTEntry): Double

    protected open fun getIncomingEdge(entry: SPTEntry): Int = entry.edge

    protected abstract fun extractPath(): Path

    protected open fun fromEntryCanBeSkipped(): Boolean = false

    protected open fun fwdSearchCanBeStopped(): Boolean = false

    protected open fun toEntryCanBeSkipped(): Boolean = false

    protected open fun bwdSearchCanBeStopped(): Boolean = false

    protected open fun getCurrentFromWeight(): Double = currFrom!!.weight

    protected open fun getCurrentToWeight(): Double = currTo!!.weight

    internal fun getBestFromMap(): GHIntObjectHashMap<SPTEntry> = bestWeightMapFrom

    internal fun getBestToMap(): GHIntObjectHashMap<SPTEntry> = bestWeightMapTo

    internal fun setBestOtherMap(other: GHIntObjectHashMap<SPTEntry>) {
        bestWeightMapOther = other
    }

    protected fun setUpdateBestPath(b: Boolean) {
        updateBestPath = b
    }

    override fun getVisitedNodes(): Int = visitedCountFrom + visitedCountTo

    internal open fun setToDataStructures(other: AbstractBidirAlgo) {
        to = other.to
        toInEdge = other.toInEdge
        pqOpenSetTo = other.pqOpenSetTo
        bestWeightMapTo = other.bestWeightMapTo
        finishedTo = other.finishedTo
        currTo = other.currTo
        visitedCountTo = other.visitedCountTo
        // inEdgeExplorer
    }

    override fun setMaxVisitedNodes(numberOfNodes: Int) {
        this.maxVisitedNodes = numberOfNodes
    }

    override fun setTimeoutMillis(timeoutMillis: Long) {
        this.timeoutMillis = timeoutMillis
    }

    protected fun checkAlreadyRun() {
        check(!alreadyRun) { "Create a new instance per call" }
        alreadyRun = true
    }

    protected fun setupFinishTime() {
        finishTimeMillis = try {
            Math.addExact(System.currentTimeMillis(), timeoutMillis)
        } catch (e: ArithmeticException) {
            Long.MAX_VALUE
        }
    }

    override fun getName(): String = javaClass.simpleName

    protected open fun isMaxVisitedNodesExceeded(): Boolean = maxVisitedNodes < getVisitedNodes()

    protected open fun isTimeoutExceeded(): Boolean =
        finishTimeMillis < Long.MAX_VALUE && System.currentTimeMillis() > finishTimeMillis
}
