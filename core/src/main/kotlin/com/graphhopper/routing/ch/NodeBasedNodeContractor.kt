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

import com.carrotsearch.hppc.IntContainer
import com.graphhopper.routing.ch.CHParameters.EDGE_DIFFERENCE_WEIGHT
import com.graphhopper.routing.ch.CHParameters.MAX_POLL_FACTOR_CONTRACTION_NODE
import com.graphhopper.routing.ch.CHParameters.MAX_POLL_FACTOR_HEURISTIC_NODE
import com.graphhopper.routing.ch.CHParameters.ORIGINAL_EDGE_COUNT_WEIGHT
import com.graphhopper.storage.CHStorageBuilder
import com.graphhopper.util.Helper.nf
import com.graphhopper.util.PMap
import com.graphhopper.util.StopWatch
import java.util.Locale

internal class NodeBasedNodeContractor(
    private val prepareGraph: CHPreparationGraph,
    chBuilder: CHStorageBuilder,
    pMap: PMap
) : NodeContractor {
    private val params = Params()

    // todo: maybe use a set to prevent duplicates instead?
    private var shortcuts: MutableList<Shortcut>? = ArrayList()
    private var chBuilder: CHStorageBuilder? = chBuilder
    private var inEdgeExplorer: PrepareGraphEdgeExplorer? = null
    private var outEdgeExplorer: PrepareGraphEdgeExplorer? = null
    private var existingShortcutExplorer: PrepareGraphEdgeExplorer? = null
    private var witnessPathSearcher: NodeBasedWitnessPathSearcher? = null
    private var addedShortcutsCount = 0
    private var dijkstraCount = 0L
    private val dijkstraSW = StopWatch()

    // meanDegree is the number of edges / number of nodes ratio of the graph, not really the average degree, because
    // each edge can exist in both directions
    private var meanDegree = 0.0

    // temporary counters used for priority calculation
    private var originalEdgesCount = 0
    private var shortcutsCount = 0

    init {
        extractParams(pMap)
    }

    private fun extractParams(pMap: PMap) {
        params.edgeDifferenceWeight = pMap.getFloat(EDGE_DIFFERENCE_WEIGHT, params.edgeDifferenceWeight)
        params.originalEdgesCountWeight = pMap.getFloat(ORIGINAL_EDGE_COUNT_WEIGHT, params.originalEdgesCountWeight)
        params.maxPollFactorHeuristic = pMap.getDouble(MAX_POLL_FACTOR_HEURISTIC_NODE, params.maxPollFactorHeuristic)
        params.maxPollFactorContraction = pMap.getDouble(MAX_POLL_FACTOR_CONTRACTION_NODE, params.maxPollFactorContraction)
    }

    override fun initFromGraph() {
        inEdgeExplorer = prepareGraph.createInEdgeExplorer()
        outEdgeExplorer = prepareGraph.createOutEdgeExplorer()
        existingShortcutExplorer = prepareGraph.createOutEdgeExplorer()
        witnessPathSearcher = NodeBasedWitnessPathSearcher(prepareGraph)
        meanDegree = prepareGraph.getOriginalEdges() * 1.0 / prepareGraph.getNodes()
    }

    override fun close() {
        prepareGraph.close()
        shortcuts = null
        chBuilder = null
        inEdgeExplorer = null
        outEdgeExplorer = null
        existingShortcutExplorer = null
        witnessPathSearcher = null
    }

    /**
     * Warning: the calculated priority must NOT depend on priority(v) and therefore findAndHandleShortcuts should also not
     * depend on the priority(v). Otherwise updating the priority before contracting in contractNodes() could lead to
     * a slowish or even endless loop.
     */
    override fun calculatePriority(node: Int): Float {
        // # huge influence: the bigger the less shortcuts gets created and the faster is the preparation
        //
        // every adjNode has an 'original edge' number associated. initially it is r=1
        // when a new shortcut is introduced then r of the associated edges is summed up:
        // r(u,w)=r(u,v)+r(v,w) now we can define
        // originalEdgesCount = σ(v) := sum_{ (u,w) ∈ shortcuts(v) } of r(u, w)
        shortcutsCount = 0
        originalEdgesCount = 0
        findAndHandleShortcuts(node, this::countShortcuts, (meanDegree * params.maxPollFactorHeuristic).toInt())

        // from shortcuts we can compute the edgeDifference
        // # low influence: with it the shortcut creation is slightly faster
        //
        // |shortcuts(v)| − |{(u, v) | v uncontracted}| − |{(v, w) | v uncontracted}|
        // meanDegree is used instead of outDegree+inDegree as if one adjNode is in both directions
        // only one bucket memory is used. Additionally one shortcut could also stand for two directions.
        val edgeDifference = shortcutsCount - prepareGraph.getDegree(node)

        // according to the paper do a simple linear combination of the properties to get the priority.
        return params.edgeDifferenceWeight * edgeDifference +
                params.originalEdgesCountWeight * originalEdgesCount
        // todo: maybe use contracted-neighbors heuristic (contract nodes with lots of contracted neighbors later) as in GH 1.0 again?
        //       maybe use hierarchy-depths heuristic as in edge-based?
    }

    override fun contractNode(node: Int): IntContainer {
        val degree = findAndHandleShortcuts(node, this::addOrUpdateShortcut, (meanDegree * params.maxPollFactorContraction).toInt())
        insertShortcuts(node)
        // put weight factor on meanDegree instead of taking the average => meanDegree is more stable
        meanDegree = (meanDegree * 2 + degree) / 3
        return prepareGraph.disconnect(node)
    }

    /**
     * Calls the shortcut handler for all edges and shortcuts adjacent to the given node. After this method is called
     * these edges and shortcuts will be removed from the prepare graph, so this method offers the last chance to deal
     * with them.
     */
    private fun insertShortcuts(node: Int) {
        val shortcuts = shortcuts!!
        shortcuts.clear()
        insertOutShortcuts(node)
        insertInShortcuts(node)
        val origEdges = prepareGraph.getOriginalEdges()
        for (sc in shortcuts) {
            val shortcut = chBuilder!!.addShortcutNodeBased(sc.from, sc.to, sc.flags, sc.weight, sc.skippedEdge1, sc.skippedEdge2)
            if (sc.flags == PrepareEncoder.getScFwdDir()) {
                prepareGraph.setShortcutForPrepareEdge(sc.prepareEdgeFwd, origEdges + shortcut)
            } else if (sc.flags == PrepareEncoder.getScBwdDir()) {
                prepareGraph.setShortcutForPrepareEdge(sc.prepareEdgeBwd, origEdges + shortcut)
            } else {
                prepareGraph.setShortcutForPrepareEdge(sc.prepareEdgeFwd, origEdges + shortcut)
                prepareGraph.setShortcutForPrepareEdge(sc.prepareEdgeBwd, origEdges + shortcut)
            }
        }
        addedShortcutsCount += shortcuts.size
    }

    private fun insertOutShortcuts(node: Int) {
        val iter = outEdgeExplorer!!.setBaseNode(node)
        while (iter.next()) {
            if (!iter.isShortcut())
                continue
            shortcuts!!.add(Shortcut(iter.getPrepareEdge(), -1, node, iter.getAdjNode(), iter.getSkipped1(),
                    iter.getSkipped2(), PrepareEncoder.getScFwdDir(), iter.getWeight()))
        }
    }

    private fun insertInShortcuts(node: Int) {
        val iter = inEdgeExplorer!!.setBaseNode(node)
        while (iter.next()) {
            if (!iter.isShortcut())
                continue

            val skippedEdge1 = iter.getSkipped2()
            val skippedEdge2 = iter.getSkipped1()
            // we check if this shortcut already exists (with the same weight) for the other direction and if so we can use
            // it for both ways instead of adding another one
            var bidir = false
            for (sc in shortcuts!!) {
                if (sc.to == iter.getAdjNode()
                        && sc.weight.toBits() == iter.getWeight().toBits()
                        // todo: can we not just compare skippedEdges?
                        && prepareGraph.getShortcutForPrepareEdge(sc.skippedEdge1) == prepareGraph.getShortcutForPrepareEdge(skippedEdge1)
                        && prepareGraph.getShortcutForPrepareEdge(sc.skippedEdge2) == prepareGraph.getShortcutForPrepareEdge(skippedEdge2)
                        && sc.flags == PrepareEncoder.getScFwdDir()) {
                    sc.flags = PrepareEncoder.getScDirMask()
                    sc.prepareEdgeBwd = iter.getPrepareEdge()
                    bidir = true
                    break
                }
            }
            if (!bidir) {
                shortcuts!!.add(Shortcut(-1, iter.getPrepareEdge(), node, iter.getAdjNode(), skippedEdge1, skippedEdge2, PrepareEncoder.getScBwdDir(), iter.getWeight()))
            }
        }
    }

    override fun finishContraction() {
        // during contraction the skip1/2 edges of shortcuts refer to the prepare edge-ids *not* the final shortcut
        // ids (because they are not known before the insertion) -> we need to re-map these ids here
        chBuilder!!.replaceSkippedEdges(prepareGraph::getShortcutForPrepareEdge)
    }

    override fun getStatisticsString(): String =
        String.format(Locale.ROOT, "meanDegree: %.2f, dijkstras: %10s, mem: %10s",
                meanDegree, nf(dijkstraCount), witnessPathSearcher!!.getMemoryUsageAsString())

    /**
     * Searches for shortcuts and calls the given handler on each shortcut that is found. The graph is not directly
     * changed by this method.
     * Returns the 'degree' of the given node (disregarding edges from/to already contracted nodes).
     * Note that here the degree is not the total number of adjacent edges, but only the number of incoming edges
     */
    private fun findAndHandleShortcuts(node: Int, handler: PrepareShortcutHandler, maxVisitedNodes: Int): Long {
        var degree = 0L
        val incomingEdges = inEdgeExplorer!!.setBaseNode(node)
        // collect outgoing nodes (goal-nodes) only once
        while (incomingEdges.next()) {
            val fromNode = incomingEdges.getAdjNode()
            if (fromNode == node)
                throw IllegalStateException("Unexpected loop-edge at node: $node")

            val incomingEdgeWeight = incomingEdges.getWeight()
            // this check is important to prevent calling calcMillis on inaccessible edges and also allows early exit
            if (incomingEdgeWeight.isInfinite()) {
                continue
            }
            // collect outgoing nodes (goal-nodes) only once
            val outgoingEdges = outEdgeExplorer!!.setBaseNode(node)
            witnessPathSearcher!!.init(fromNode, node)
            degree++
            while (outgoingEdges.next()) {
                val toNode = outgoingEdges.getAdjNode()
                // no need to search for witnesses going from a node back to itself
                if (fromNode == toNode)
                    continue

                // Limit weight as ferries or forbidden edges can increase local search too much.
                // If we decrease the correct weight we only explore less and introduce more shortcuts.
                // I.e. no change to accuracy is made.
                val existingDirectWeight = incomingEdgeWeight + outgoingEdges.getWeight()
                if (existingDirectWeight.isInfinite())
                    continue

                dijkstraSW.start()
                dijkstraCount++
                val maxWeight = witnessPathSearcher!!.findUpperBound(toNode, existingDirectWeight, maxVisitedNodes)
                dijkstraSW.stop()

                if (maxWeight <= existingDirectWeight)
                    // FOUND witness path, so do not add shortcut
                    continue

                handler.handleShortcut(fromNode, toNode, existingDirectWeight,
                        outgoingEdges.getPrepareEdge(), outgoingEdges.getOrigEdgeCount(),
                        incomingEdges.getPrepareEdge(), incomingEdges.getOrigEdgeCount())
            }
        }
        return degree
    }

    private fun countShortcuts(fromNode: Int, toNode: Int, existingDirectWeight: Double,
                               outgoingEdge: Int, outOrigEdgeCount: Int,
                               incomingEdge: Int, inOrigEdgeCount: Int) {
        shortcutsCount++
        originalEdgesCount += inOrigEdgeCount + outOrigEdgeCount
    }

    private fun addOrUpdateShortcut(fromNode: Int, toNode: Int, weight: Double,
                                    outgoingEdge: Int, outOrigEdgeCount: Int,
                                    incomingEdge: Int, inOrigEdgeCount: Int) {
        var exists = false
        val iter = existingShortcutExplorer!!.setBaseNode(fromNode)
        while (iter.next()) {
            // do not update base edges!
            if (iter.getAdjNode() != toNode || !iter.isShortcut()) {
                continue
            }
            exists = true
            if (weight < iter.getWeight()) {
                iter.setWeight(weight)
                iter.setSkippedEdges(incomingEdge, outgoingEdge)
                iter.setOrigEdgeCount(inOrigEdgeCount + outOrigEdgeCount)
            }
        }
        if (!exists)
            prepareGraph.addShortcut(fromNode, toNode, -1, -1, incomingEdge, outgoingEdge, weight, inOrigEdgeCount + outOrigEdgeCount)
    }

    override fun getAddedShortcutsCount(): Long = addedShortcutsCount.toLong()

    override fun getDijkstraSeconds(): Float = dijkstraSW.getCurrentSeconds()

    private fun interface PrepareShortcutHandler {
        fun handleShortcut(fromNode: Int, toNode: Int, existingDirectWeight: Double,
                           outgoingEdge: Int, outOrigEdgeCount: Int,
                           incomingEdge: Int, inOrigEdgeCount: Int)
    }

    private class Params {
        // default values were optimized for Unterfranken
        var edgeDifferenceWeight = 10f
        var originalEdgesCountWeight = 1f

        // these values seemed to work best for planet (fast prep without compromising too much for the query time)
        // higher values can further decrease the number of shortcuts and improve the query time, but normally at the
        // cost of a longer preparation (see #2514)
        var maxPollFactorHeuristic = 5.0
        var maxPollFactorContraction = 200.0
    }

    private class Shortcut(
        var prepareEdgeFwd: Int,
        var prepareEdgeBwd: Int,
        var from: Int,
        var to: Int,
        var skippedEdge1: Int,
        var skippedEdge2: Int,
        var flags: Int,
        var weight: Double
    ) {
        override fun toString(): String {
            val str = if (flags == PrepareEncoder.getScDirMask())
                "$from<->"
            else
                "$from->"

            return "$str$to, weight:$weight ($skippedEdge1,$skippedEdge2)"
        }
    }
}
