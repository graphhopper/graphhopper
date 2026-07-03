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

import com.carrotsearch.hppc.IntHashSet
import com.carrotsearch.hppc.IntSet
import com.carrotsearch.hppc.LongHashSet
import com.carrotsearch.hppc.LongSet
import com.graphhopper.coll.primitive.IntScatterSet
import com.graphhopper.routing.ch.CHParameters.EDGE_QUOTIENT_WEIGHT
import com.graphhopper.routing.ch.CHParameters.HIERARCHY_DEPTH_WEIGHT
import com.graphhopper.routing.ch.CHParameters.MAX_POLL_FACTOR_CONTRACTION_EDGE
import com.graphhopper.routing.ch.CHParameters.MAX_POLL_FACTOR_HEURISTIC_EDGE
import com.graphhopper.routing.ch.CHParameters.ORIGINAL_EDGE_QUOTIENT_WEIGHT
import com.graphhopper.storage.CHStorageBuilder
import com.graphhopper.util.BitUtil
import com.graphhopper.util.EdgeIterator
import com.graphhopper.util.GHUtility.reverseEdgeKey
import com.graphhopper.util.Helper.nf
import com.graphhopper.util.PMap
import com.graphhopper.util.StopWatch
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.Locale
import kotlin.math.max

/**
 * This class is used to calculate the priority of or contract a given node in edge-based Contraction Hierarchies as it
 * is required to support turn-costs. This implementation follows the 'aggressive' variant described in
 * 'Efficient Routing in Road Networks with Turn Costs' by R. Geisberger and C. Vetter. Here, we do not store the center
 * node for each shortcut, but introduce helper shortcuts when a loop shortcut is encountered.
 * <p>
 * This class is mostly concerned with triggering the required local searches and introducing the necessary shortcuts
 * or calculating the node priority, while the actual searches for witness paths are delegated to
 * [EdgeBasedWitnessPathSearcher].
 *
 * @author easbar
 */
internal class EdgeBasedNodeContractor(
    private val prepareGraph: CHPreparationGraph,
    chBuilder: CHStorageBuilder,
    pMap: PMap
) : NodeContractor {
    private var inEdgeExplorer: PrepareGraphEdgeExplorer? = null
    private var outEdgeExplorer: PrepareGraphEdgeExplorer? = null
    private var existingShortcutExplorer: PrepareGraphEdgeExplorer? = null
    private var sourceNodeOrigInEdgeExplorer: PrepareGraphOrigEdgeExplorer? = null
    private var chBuilder: CHStorageBuilder? = chBuilder
    private val params = Params()
    private val dijkstraSW = StopWatch()

    // temporary data used during node contraction
    private val sourceNodes: IntSet = IntHashSet(10)
    private val targetNodes: IntSet = IntHashSet(10)
    private val addedShortcuts: LongSet = LongHashSet()
    private val addingStats = Stats()
    private val countingStats = Stats()
    private var activeStats: Stats? = null

    private var hierarchyDepths: IntArray? = null
    private var witnessPathSearcher: EdgeBasedWitnessPathSearcher? = null
    private var bridgePathFinder: BridgePathFinder? = null
    private val wpsStatsHeur = EdgeBasedWitnessPathSearcher.Stats()
    private val wpsStatsContr = EdgeBasedWitnessPathSearcher.Stats()

    // counts the total number of added shortcuts
    private var addedShortcutsCount = 0

    // edge counts used to calculate priority
    private var numShortcuts = 0
    private var numPrevEdges = 0
    private var numOrigEdges = 0
    private var numPrevOrigEdges = 0
    private var numAllEdges = 0

    private var meanDegree = 0.0

    init {
        extractParams(pMap)
    }

    private fun extractParams(pMap: PMap) {
        params.edgeQuotientWeight = pMap.getFloat(EDGE_QUOTIENT_WEIGHT, params.edgeQuotientWeight)
        params.originalEdgeQuotientWeight = pMap.getFloat(ORIGINAL_EDGE_QUOTIENT_WEIGHT, params.originalEdgeQuotientWeight)
        params.hierarchyDepthWeight = pMap.getFloat(HIERARCHY_DEPTH_WEIGHT, params.hierarchyDepthWeight)
        params.maxPollFactorHeuristic = pMap.getDouble(MAX_POLL_FACTOR_HEURISTIC_EDGE, params.maxPollFactorHeuristic)
        params.maxPollFactorContraction = pMap.getDouble(MAX_POLL_FACTOR_CONTRACTION_EDGE, params.maxPollFactorContraction)
    }

    override fun initFromGraph() {
        inEdgeExplorer = prepareGraph.createInEdgeExplorer()
        outEdgeExplorer = prepareGraph.createOutEdgeExplorer()
        existingShortcutExplorer = prepareGraph.createOutEdgeExplorer()
        sourceNodeOrigInEdgeExplorer = prepareGraph.createInOrigEdgeExplorer()
        hierarchyDepths = IntArray(prepareGraph.getNodes())
        witnessPathSearcher = EdgeBasedWitnessPathSearcher(prepareGraph)
        bridgePathFinder = BridgePathFinder(prepareGraph)
        meanDegree = prepareGraph.getOriginalEdges() * 1.0 / prepareGraph.getNodes()
    }

    override fun calculatePriority(node: Int): Float {
        activeStats = countingStats
        resetEdgeCounters()
        countPreviousEdges(node)
        if (numAllEdges == 0)
            // this node is isolated, maybe it belongs to a removed subnetwork, in any case we can quickly contract it
            // no shortcuts will be introduced
            return Float.NEGATIVE_INFINITY
        stats().stopWatch.start()
        findAndHandlePrepareShortcuts(node, this::countShortcuts, (meanDegree * params.maxPollFactorHeuristic).toInt(), wpsStatsHeur)
        stats().stopWatch.stop()
        // the higher the priority the later (!) this node will be contracted
        val edgeQuotient = numShortcuts / prepareGraph.getDegree(node).toFloat()
        val origEdgeQuotient = numOrigEdges / numPrevOrigEdges.toFloat()
        val hierarchyDepth = hierarchyDepths!![node]
        val priority = params.edgeQuotientWeight * edgeQuotient +
                params.originalEdgeQuotientWeight * origEdgeQuotient +
                params.hierarchyDepthWeight * hierarchyDepth
        if (LOGGER.isTraceEnabled)
            LOGGER.trace("node: {}, eq: {} / {} = {}, oeq: {} / {} = {}, depth: {} --> {}",
                    node,
                    numShortcuts, numPrevEdges, edgeQuotient,
                    numOrigEdges, numPrevOrigEdges, origEdgeQuotient,
                    hierarchyDepth, priority)
        return priority
    }

    override fun contractNode(node: Int): IntScatterSet {
        activeStats = addingStats
        stats().stopWatch.start()
        findAndHandlePrepareShortcuts(node, this::addShortcutsToPrepareGraph, (meanDegree * params.maxPollFactorContraction).toInt(), wpsStatsContr)
        insertShortcuts(node)
        val neighbors = prepareGraph.disconnect(node)
        // We maintain an approximation of the mean degree which we update after every contracted node.
        // We do it the same way as for node-based CH for now.
        meanDegree = (meanDegree * 2 + neighbors.size()) / 3
        updateHierarchyDepthsOfNeighbors(node, neighbors)
        stats().stopWatch.stop()
        return neighbors
    }

    override fun finishContraction() {
        chBuilder!!.replaceSkippedEdges(prepareGraph::getShortcutForPrepareEdge)
    }

    override fun getAddedShortcutsCount(): Long = addedShortcutsCount.toLong()

    override fun getDijkstraSeconds(): Float = dijkstraSW.getCurrentSeconds()

    override fun getStatisticsString(): String =
        String.format(Locale.ROOT, "degree_approx: %3.1f", meanDegree) + ", priority   : " + countingStats + ", " + wpsStatsHeur + ", contraction: " + addingStats + ", " + wpsStatsContr

    /**
     * This method performs witness searches between all nodes adjacent to the given node and calls the
     * given handler for all required shortcuts.
     */
    private fun findAndHandlePrepareShortcuts(node: Int, shortcutHandler: PrepareShortcutHandler, maxPolls: Int, wpsStats: EdgeBasedWitnessPathSearcher.Stats) {
        stats().nodes++
        addedShortcuts.clear()
        sourceNodes.clear()

        // traverse incoming edges/shortcuts to find all the source nodes
        val incomingEdges = inEdgeExplorer!!.setBaseNode(node)
        while (incomingEdges.next()) {
            val sourceNode = incomingEdges.getAdjNode()
            if (sourceNode == node)
                continue
            // make sure we process each source node only once
            if (!sourceNodes.add(sourceNode))
                continue
            // for each source node we need to look at every incoming original edge and check which target edges are reachable
            val origInIter = sourceNodeOrigInEdgeExplorer!!.setBaseNode(sourceNode)
            while (origInIter.next()) {
                val origInKey = reverseEdgeKey(origInIter.getOrigEdgeKeyLast())
                // we search 'bridge paths' leading to the target edges
                val bridgePaths = bridgePathFinder!!.find(origInKey, sourceNode, node)
                if (bridgePaths.isEmpty())
                    continue
                witnessPathSearcher!!.initSearch(origInKey, sourceNode, node, wpsStats)
                // hppc cursor-iterator order (slots ascending, empty key last) — this order drives
                // shortcut creation/dedup/ID assignment and is pinned by the seeded result map
                bridgePaths.forEachInIteratorOrder { targetEdgeKey, bridgePath ->
                    if (!bridgePath.weight.isFinite())
                        throw IllegalStateException("Bridge entry weights should always be finite")
                    dijkstraSW.start()
                    val weight = witnessPathSearcher!!.runSearch(bridgePath.chEntry.adjNode, targetEdgeKey, bridgePath.weight, maxPolls)
                    dijkstraSW.stop()
                    if (weight <= bridgePath.weight)
                        // we found a witness, nothing to do
                        return@forEachInIteratorOrder
                    var root = bridgePath.chEntry
                    while (EdgeIterator.Edge.isValid(root.parent!!.prepareEdge))
                        root = root.parent!!
                    // we make sure to add each shortcut only once. when we are actually adding shortcuts we check for existing
                    // shortcuts anyway, but at least this is important when we *count* shortcuts.
                    val addedShortcutKey = BitUtil.LITTLE.toLong(root.firstEdgeKey, bridgePath.chEntry.incEdgeKey)
                    if (!addedShortcuts.add(addedShortcutKey))
                        return@forEachInIteratorOrder
                    val initialTurnCost = prepareGraph.getTurnWeight(origInKey, sourceNode, root.firstEdgeKey)
                    bridgePath.chEntry.weight -= initialTurnCost
                    LOGGER.trace("Adding shortcuts for target entry {}", bridgePath.chEntry)
                    // todo: re-implement loop-avoidance heuristic as it existed in GH 1.0? it did not work the
                    //       way it was implemented so it was removed at some point
                    shortcutHandler.handleShortcut(root, bridgePath.chEntry, bridgePath.chEntry.origEdges)
                }
                witnessPathSearcher!!.finishSearch()
            }
        }
    }

    /**
     * Calls the shortcut handler for all edges and shortcuts adjacent to the given node. After this method is called
     * these edges and shortcuts will be removed from the prepare graph, so this method offers the last chance to deal
     * with them.
     */
    private fun insertShortcuts(node: Int) {
        insertOutShortcuts(node)
        insertInShortcuts(node)
    }

    private fun insertOutShortcuts(node: Int) {
        val iter = outEdgeExplorer!!.setBaseNode(node)
        while (iter.next()) {
            if (!iter.isShortcut())
                continue
            val shortcut = chBuilder!!.addShortcutEdgeBased(node, iter.getAdjNode(),
                    PrepareEncoder.getScFwdDir(), iter.getWeight(),
                    iter.getSkipped1(), iter.getSkipped2(),
                    iter.getOrigEdgeKeyFirst(),
                    iter.getOrigEdgeKeyLast())
            prepareGraph.setShortcutForPrepareEdge(iter.getPrepareEdge(), prepareGraph.getOriginalEdges() + shortcut)
            addedShortcutsCount++
        }
    }

    private fun insertInShortcuts(node: Int) {
        val iter = inEdgeExplorer!!.setBaseNode(node)
        while (iter.next()) {
            if (!iter.isShortcut())
                continue
            // we added loops already using the outEdgeExplorer
            if (iter.getAdjNode() == node)
                continue
            val shortcut = chBuilder!!.addShortcutEdgeBased(node, iter.getAdjNode(),
                    PrepareEncoder.getScBwdDir(), iter.getWeight(),
                    iter.getSkipped1(), iter.getSkipped2(),
                    iter.getOrigEdgeKeyFirst(),
                    iter.getOrigEdgeKeyLast())
            prepareGraph.setShortcutForPrepareEdge(iter.getPrepareEdge(), prepareGraph.getOriginalEdges() + shortcut)
            addedShortcutsCount++
        }
    }

    private fun countPreviousEdges(node: Int) {
        // todo: this edge counting can probably be simplified, but we might need to re-optimize heuristic parameters then
        val outIter = outEdgeExplorer!!.setBaseNode(node)
        while (outIter.next()) {
            numAllEdges++
            numPrevEdges++
            numPrevOrigEdges += outIter.getOrigEdgeCount()
        }

        val inIter = inEdgeExplorer!!.setBaseNode(node)
        while (inIter.next()) {
            numAllEdges++
            // do not consider loop edges a second time
            if (inIter.getBaseNode() == inIter.getAdjNode())
                continue
            numPrevEdges++
            numPrevOrigEdges += inIter.getOrigEdgeCount()
        }
    }

    private fun updateHierarchyDepthsOfNeighbors(node: Int, neighbors: IntScatterSet) {
        val hierarchyDepths = hierarchyDepths!!
        val level = hierarchyDepths[node]
        // hppc cursor-iterator order (was a for-each over the container's cursors)
        neighbors.forEachInIteratorOrder { n ->
            if (n != node)
                hierarchyDepths[n] = max(hierarchyDepths[n], level + 1)
        }
    }

    private fun addShortcutsToPrepareGraph(edgeFrom: PrepareCHEntry, edgeTo: PrepareCHEntry, origEdgeCount: Int): PrepareCHEntry {
        return if (edgeTo.parent!!.prepareEdge != edgeFrom.prepareEdge) {
            // counting origEdgeCount correctly is tricky with loop shortcuts and the recursion we use here. so we
            // simply ignore this, it probably does not matter that much
            val prev = addShortcutsToPrepareGraph(edgeFrom, edgeTo.parent!!, origEdgeCount)
            doAddShortcut(prev, edgeTo, origEdgeCount)
        } else {
            doAddShortcut(edgeFrom, edgeTo, origEdgeCount)
        }
    }

    private fun doAddShortcut(edgeFrom: PrepareCHEntry, edgeTo: PrepareCHEntry, origEdgeCount: Int): PrepareCHEntry {
        val from = edgeFrom.parent!!.adjNode
        val adjNode = edgeTo.adjNode

        val iter = existingShortcutExplorer!!.setBaseNode(from)
        while (iter.next()) {
            if (!isSameShortcut(iter, adjNode, edgeFrom.firstEdgeKey, edgeTo.incEdgeKey)) {
                // this is some other (shortcut) edge -> we do not care
                continue
            }
            val existingWeight = iter.getWeight()
            if (existingWeight <= edgeTo.weight) {
                // our shortcut already exists with lower weight --> do nothing
                val entry = PrepareCHEntry(iter.getPrepareEdge(), iter.getOrigEdgeKeyFirst(), iter.getOrigEdgeKeyLast(), adjNode, existingWeight, origEdgeCount)
                entry.parent = edgeFrom.parent
                return entry
            } else {
                // update weight
                iter.setSkippedEdges(edgeFrom.prepareEdge, edgeTo.prepareEdge)
                iter.setWeight(edgeTo.weight)
                iter.setOrigEdgeCount(origEdgeCount)
                val entry = PrepareCHEntry(iter.getPrepareEdge(), iter.getOrigEdgeKeyFirst(), iter.getOrigEdgeKeyLast(), adjNode, edgeTo.weight, origEdgeCount)
                entry.parent = edgeFrom.parent
                return entry
            }
        }

        // our shortcut is new --> add it
        val origFirstKey = edgeFrom.firstEdgeKey
        LOGGER.trace("Adding shortcut from {} to {}, weight: {}, firstOrigEdgeKey: {}, lastOrigEdgeKey: {}",
                from, adjNode, edgeTo.weight, origFirstKey, edgeTo.incEdgeKey)
        val prepareEdge = prepareGraph.addShortcut(from, adjNode, origFirstKey, edgeTo.incEdgeKey, edgeFrom.prepareEdge, edgeTo.prepareEdge, edgeTo.weight, origEdgeCount)
        // does not matter here
        val incEdgeKey = -1
        val entry = PrepareCHEntry(prepareEdge, origFirstKey, incEdgeKey, edgeTo.adjNode, edgeTo.weight, origEdgeCount)
        entry.parent = edgeFrom.parent
        return entry
    }

    private fun isSameShortcut(iter: PrepareGraphEdgeIterator, adjNode: Int, firstOrigEdgeKey: Int, lastOrigEdgeKey: Int): Boolean =
        iter.isShortcut()
                && (iter.getAdjNode() == adjNode)
                && (iter.getOrigEdgeKeyFirst() == firstOrigEdgeKey)
                && (iter.getOrigEdgeKeyLast() == lastOrigEdgeKey)

    private fun resetEdgeCounters() {
        numShortcuts = 0
        numPrevEdges = 0
        numOrigEdges = 0
        numPrevOrigEdges = 0
        numAllEdges = 0
    }

    override fun close() {
        prepareGraph.close()
        inEdgeExplorer = null
        outEdgeExplorer = null
        existingShortcutExplorer = null
        sourceNodeOrigInEdgeExplorer = null
        chBuilder = null
        witnessPathSearcher!!.close()
        sourceNodes.release()
        targetNodes.release()
        addedShortcuts.release()
        hierarchyDepths = null
    }

    private fun stats(): Stats = activeStats!!

    private fun interface PrepareShortcutHandler {
        fun handleShortcut(edgeFrom: PrepareCHEntry, edgeTo: PrepareCHEntry, origEdgeCount: Int)
    }

    private fun countShortcuts(edgeFrom: PrepareCHEntry, edgeTo: PrepareCHEntry, origEdgeCount: Int) {
        val fromNode = edgeFrom.parent!!.adjNode
        val toNode = edgeTo.adjNode
        val firstOrigEdgeKey = edgeFrom.firstEdgeKey
        val lastOrigEdgeKey = edgeTo.incEdgeKey

        // check if this shortcut already exists
        val iter = existingShortcutExplorer!!.setBaseNode(fromNode)
        while (iter.next()) {
            if (isSameShortcut(iter, toNode, firstOrigEdgeKey, lastOrigEdgeKey)) {
                // this shortcut exists already, maybe its weight will be updated but we should not count it as
                // a new edge
                return
            }
        }

        // this shortcut is new --> increase counts
        var edgeTo = edgeTo
        while (edgeTo !== edgeFrom) {
            numShortcuts++
            edgeTo = edgeTo.parent!!
        }
        numOrigEdges += origEdgeCount
    }

    fun getNumPolledEdges(): Long = wpsStatsContr.numPolls + wpsStatsHeur.numPolls

    private class Params {
        var edgeQuotientWeight = 100f
        var originalEdgeQuotientWeight = 100f
        var hierarchyDepthWeight = 20f

        // Increasing these parameters (heuristic especially) will lead to a longer preparation time but also to fewer
        // shortcuts and possibly (slightly) faster queries.
        var maxPollFactorHeuristic = 4.0
        var maxPollFactorContraction = 200.0
    }

    private class Stats {
        var nodes = 0
        val stopWatch = StopWatch()

        override fun toString(): String =
            String.format(Locale.ROOT,
                    "time: %7.2fs, nodes: %10s", stopWatch.getCurrentSeconds(), nf(nodes.toLong()))
    }

    companion object {
        private val LOGGER: Logger = LoggerFactory.getLogger(EdgeBasedNodeContractor::class.java)
    }
}
