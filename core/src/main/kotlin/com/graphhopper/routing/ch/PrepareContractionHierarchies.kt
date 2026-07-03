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
import com.graphhopper.coll.MinHeapWithUpdate
import com.graphhopper.routing.ch.CHParameters.CONTRACTED_NODES
import com.graphhopper.routing.ch.CHParameters.LAST_LAZY_NODES_UPDATES
import com.graphhopper.routing.ch.CHParameters.LOG_MESSAGES
import com.graphhopper.routing.ch.CHParameters.NEIGHBOR_UPDATES
import com.graphhopper.routing.ch.CHParameters.NEIGHBOR_UPDATES_MAX
import com.graphhopper.routing.ch.CHParameters.PERIODIC_UPDATES
import com.graphhopper.routing.util.TraversalMode
import com.graphhopper.storage.BaseGraph
import com.graphhopper.storage.CHConfig
import com.graphhopper.storage.CHStorage
import com.graphhopper.storage.CHStorageBuilder
import com.graphhopper.util.Helper
import com.graphhopper.util.Helper.getMemInfo
import com.graphhopper.util.Helper.nf
import com.graphhopper.util.PMap
import com.graphhopper.util.StopWatch
import org.slf4j.LoggerFactory
import java.util.Locale
import java.util.Random
import kotlin.math.max

/**
 * This class prepares the graph for a bidirectional algorithm supporting contraction hierarchies
 * ie. an algorithm returned by createAlgo.
 * <p>
 * There are several descriptions of contraction hierarchies available. The following is one of the
 * more detailed: http://web.cs.du.edu/~sturtevant/papers/highlevelpathfinding.pdf
 * <p>
 * The only difference is that we use two skipped edges instead of one skipped node for faster
 * unpacking.
 * <p>
 *
 * @author Peter Karich
 */
class PrepareContractionHierarchies private constructor(graph: BaseGraph, chConfig: CHConfig) {
    private val logger = LoggerFactory.getLogger(javaClass)
    private val chConfig: CHConfig
    private val chStore: CHStorage
    private val chBuilder: CHStorageBuilder
    private val rand = Random(123)
    private val allSW = StopWatch()
    private val periodicUpdateSW = StopWatch()
    private val lazyUpdateSW = StopWatch()
    private val neighborUpdateSW = StopWatch()
    private val contractionSW = StopWatch()
    private val params: Params
    private val graph: BaseGraph
    private var nodeContractor: NodeContractor? = null
    private val nodes: Int
    private var nodeOrderingProvider: NodeOrderingProvider? = null
    private var maxLevel = 0

    // nodes with highest priority come last
    private var sortedNodes: MinHeapWithUpdate? = null
    private var pMap = PMap()
    private var checkCounter = 0
    private var prepared = false

    companion object {
        @JvmStatic
        fun fromGraph(graph: BaseGraph, chConfig: CHConfig): PrepareContractionHierarchies =
            PrepareContractionHierarchies(graph.baseGraph, chConfig)
    }

    init {
        if (!graph.isFrozen)
            throw IllegalStateException("BaseGraph must be frozen before creating CHs")
        this.graph = graph
        chStore = CHStorage.fromGraph(graph, chConfig)
        chBuilder = CHStorageBuilder(chStore)
        this.chConfig = chConfig
        params = Params.forTraversalMode(chConfig.traversalMode)
        nodes = graph.nodes
        if (chConfig.traversalMode.isEdgeBased) {
            graph.turnCostStorage
                ?: throw IllegalArgumentException("For edge-based CH you need a turn cost storage")
        }
    }

    fun setParams(pMap: PMap): PrepareContractionHierarchies {
        this.pMap = pMap
        params.periodicUpdatesPercentage = pMap.getInt(PERIODIC_UPDATES, params.periodicUpdatesPercentage)
        params.lastNodesLazyUpdatePercentage = pMap.getInt(LAST_LAZY_NODES_UPDATES, params.lastNodesLazyUpdatePercentage)
        params.neighborUpdatePercentage = pMap.getInt(NEIGHBOR_UPDATES, params.neighborUpdatePercentage)
        params.maxNeighborUpdates = pMap.getInt(NEIGHBOR_UPDATES_MAX, params.maxNeighborUpdates)
        params.nodesContractedPercentage = pMap.getInt(CONTRACTED_NODES, params.nodesContractedPercentage)
        params.logMessagesPercentage = pMap.getInt(LOG_MESSAGES, params.logMessagesPercentage)
        return this
    }

    /**
     * Instead of heuristically determining a node ordering for the graph contraction it is also possible
     * to use a fixed ordering. For example this allows re-using a previously calculated node ordering.
     * This will speed up CH preparation, but might lead to slower queries.
     */
    fun useFixedNodeOrdering(nodeOrderingProvider: NodeOrderingProvider?): PrepareContractionHierarchies {
        if (nodeOrderingProvider != null && nodeOrderingProvider.getNumNodes() != nodes) {
            throw IllegalArgumentException(
                    "contraction order size (" + nodeOrderingProvider.getNumNodes() + ")" +
                            " must be equal to number of nodes in graph (" + nodes + ").")
        }
        this.nodeOrderingProvider = nodeOrderingProvider
        return this
    }

    fun doWork(): Result {
        if (prepared)
            throw IllegalStateException("Call doWork only once!")
        prepared = true
        if (!graph.isFrozen) {
            throw IllegalStateException("Given BaseGraph has not been frozen yet")
        }
        if (chStore.getShortcuts() > 0) {
            throw IllegalStateException("Given CHStore already contains shortcuts")
        }
        allSW.start()
        initFromGraph()
        runGraphContraction()
        allSW.stop()
        logFinalGraphStats()
        return Result(
                chConfig, chStore,
                nodeContractor!!.getAddedShortcutsCount(),
                lazyUpdateSW.getCurrentSeconds().toDouble(),
                periodicUpdateSW.getCurrentSeconds().toDouble(),
                neighborUpdateSW.getCurrentSeconds().toDouble(),
                allSW.getMillis()
        )
    }

    fun isPrepared(): Boolean = prepared

    private fun logFinalGraphStats() {
        logger.info("shortcut weights - under minimum: {}, over maximum: {}, minimum valid: {}, maximum valid: {}",
                Helper.nf(chStore.numShortcutsUnderMinWeight.toLong()), Helper.nf(chStore.numShortcutsOverMaxWeight.toLong()),
                chStore.minValidWeight, chStore.maxValidWeight)
        logger.info("took: {}s, graph now - num edges: {}, num nodes: {}, num shortcuts: {}",
                allSW.getSeconds().toInt(), nf(graph.edges.toLong()), nf(nodes.toLong()), nf(chStore.getShortcuts().toLong()))
    }

    private fun runGraphContraction() {
        if (nodes < 1)
            return
        setMaxLevelOnAllNodes()
        if (nodeOrderingProvider != null) {
            contractNodesUsingFixedNodeOrdering()
        } else {
            contractNodesUsingHeuristicNodeOrdering()
        }
    }

    private fun isEdgeBased(): Boolean = chConfig.isEdgeBased

    private fun initFromGraph() {
        logger.info("Creating CH prepare graph, {}", getMemInfo())
        val prepareGraph: CHPreparationGraph
        if (chConfig.traversalMode.isEdgeBased) {
            graph.turnCostStorage
                ?: throw IllegalArgumentException("For edge-based CH you need a turn cost storage")
            val turnCostFunction = CHPreparationGraph.buildTurnCostFunctionFromTurnCostStorage(graph, chConfig.weighting)
            prepareGraph = CHPreparationGraph.edgeBased(graph.nodes, graph.edges, turnCostFunction)
            nodeContractor = EdgeBasedNodeContractor(prepareGraph, chBuilder, pMap)
        } else {
            prepareGraph = CHPreparationGraph.nodeBased(graph.nodes, graph.edges)
            nodeContractor = NodeBasedNodeContractor(prepareGraph, chBuilder, pMap)
        }
        maxLevel = nodes
        // we need a memory-efficient priority queue with an efficient update method
        // TreeMap is not memory-efficient and PriorityQueue does not support an efficient update method
        // (and is not memory efficient either)
        sortedNodes = MinHeapWithUpdate(prepareGraph.getNodes())
        logger.info("Building CH prepare graph, {}", getMemInfo())
        val sw = StopWatch().start()
        CHPreparationGraph.buildFromGraph(prepareGraph, graph, chConfig.weighting)
        logger.info("Finished building CH prepare graph, took: {}s, {}", sw.stop().getSeconds(), getMemInfo())
        nodeContractor!!.initFromGraph()
    }

    private fun setMaxLevelOnAllNodes() {
        chBuilder.setLevelForAllNodes(maxLevel)
    }

    private fun updatePrioritiesOfRemainingNodes() {
        periodicUpdateSW.start()
        val sortedNodes = sortedNodes!!
        sortedNodes.clear()
        for (node in 0 until nodes) {
            if (isContracted(node))
                continue
            val priority = calculatePriority(node)
            sortedNodes.push(node, priority)
        }
        periodicUpdateSW.stop()
    }

    private fun contractNodesUsingHeuristicNodeOrdering() {
        val sw = StopWatch().start()
        logger.info("Building initial queue of nodes to be contracted: {} nodes, {}", nodes, getMemInfo())
        // note that we update the priorities before preparing the node contractor. this does not make much sense,
        // but has always been like that and changing it would possibly require retuning the contraction parameters
        updatePrioritiesOfRemainingNodes()
        logger.info("Finished building queue, took: {}s, {}", sw.stop().getSeconds(), getMemInfo())
        val sortedNodes = sortedNodes!!
        val nodeContractor = nodeContractor!!
        val initSize = sortedNodes.size()
        var level = 0
        checkCounter = 0
        val logSize = if (params.logMessagesPercentage == 0)
            Long.MAX_VALUE
        else
            Math.round(max(10.0, initSize * (params.logMessagesPercentage / 100.0)))

        // specifies after how many contracted nodes the queue of remaining nodes is rebuilt. this takes time but the
        // more often we do this the more up-to-date the node priorities will be
        // todo: instead of using a fixed interval size maybe try adjusting it depending on the number of remaining
        // nodes ?
        val periodicUpdatesCount = if (params.periodicUpdatesPercentage == 0)
            Long.MAX_VALUE
        else
            Math.round(max(10.0, initSize * (params.periodicUpdatesPercentage / 100.0)))
        var updateCounter = 0

        // enable lazy updates for last x percentage of nodes. lazy updates make preparation slower but potentially
        // keep node priorities more up to date, possibly resulting in a better preparation.
        val lastNodesLazyUpdates = Math.round(initSize * (params.lastNodesLazyUpdatePercentage / 100.0))

        // according to paper "Polynomial-time Construction of Contraction Hierarchies for Multi-criteria Objectives" by Funke and Storandt
        // we don't need to wait for all nodes to be contracted
        val nodesToAvoidContract = Math.round(initSize * ((100 - params.nodesContractedPercentage) / 100.0))

        // Recompute priority of (the given percentage of) uncontracted neighbors. Doing neighbor updates takes additional
        // time during preparation but keeps node priorities more up to date. this potentially improves query time and
        // reduces number of shortcuts.
        val neighborUpdate = params.neighborUpdatePercentage != 0

        while (!sortedNodes.isEmpty()) {
            stopIfInterrupted()
            // periodically update priorities of ALL nodes
            if (checkCounter > 0 && checkCounter % periodicUpdatesCount == 0L) {
                updatePrioritiesOfRemainingNodes()
                updateCounter++
                if (sortedNodes.isEmpty())
                    throw IllegalStateException("Cannot prepare as no unprepared nodes where found. Called preparation twice?")
            }

            if (checkCounter % logSize == 0L) {
                logHeuristicStats(updateCounter)
            }

            checkCounter++
            val polledNode = sortedNodes.poll()

            if (!sortedNodes.isEmpty() && sortedNodes.size() < lastNodesLazyUpdates) {
                lazyUpdateSW.start()
                val priority = calculatePriority(polledNode)
                if (priority > sortedNodes.peekValue()) {
                    // current node got more important => insert as new value and contract it later
                    sortedNodes.push(polledNode, priority)
                    lazyUpdateSW.stop()
                    continue
                }
                lazyUpdateSW.stop()
            }

            // contract node v!
            val neighbors = contractNode(polledNode, level)
            level++

            if (sortedNodes.size() < nodesToAvoidContract)
                // skipped nodes are already set to maxLevel
                break

            var neighborCount = 0
            // there might be multiple edges going to the same neighbor nodes -> only calculate priority once per node
            for (neighbor in neighbors) {
                if (neighborUpdate && (params.maxNeighborUpdates < 0 || neighborCount < params.maxNeighborUpdates) && rand.nextInt(100) < params.neighborUpdatePercentage) {
                    neighborCount++
                    neighborUpdateSW.start()
                    val priority = calculatePriority(neighbor.value)
                    sortedNodes.update(neighbor.value, priority)
                    neighborUpdateSW.stop()
                }
            }
        }

        nodeContractor.finishContraction()

        logHeuristicStats(updateCounter)

        logger.info(
                "new shortcuts: " + nf(nodeContractor.getAddedShortcutsCount())
                        + ", initSize:" + nf(initSize.toLong())
                        + ", " + chConfig.weighting
                        + ", periodic:" + params.periodicUpdatesPercentage
                        + ", lazy:" + params.lastNodesLazyUpdatePercentage
                        + ", neighbor:" + params.neighborUpdatePercentage
                        + ", " + getTimesAsString()
                        + ", lazy-overhead: " + (100 * ((checkCounter / initSize.toDouble()) - 1)).toInt() + "%"
                        + ", " + Helper.getMemInfo())

        // Preparation works only once so we can release temporary data.
        // The preparation object itself has to be intact to create the algorithm.
        _close()
    }

    private fun contractNodesUsingFixedNodeOrdering() {
        val nodeOrderingProvider = nodeOrderingProvider!!
        val nodesToContract = nodeOrderingProvider.getNumNodes()
        val logSize = max(10, (params.logMessagesPercentage / 100.0 * nodesToContract).toInt())
        val stopWatch = StopWatch()
        stopWatch.start()
        for (i in 0 until nodesToContract) {
            stopIfInterrupted()
            val node = nodeOrderingProvider.getNodeIdForLevel(i)
            contractNode(node, i)
            if (i % logSize == 0) {
                stopWatch.stop()
                logFixedNodeOrderingStats(i, logSize, stopWatch)
                stopWatch.start()
            }
        }
        nodeContractor!!.finishContraction()
    }

    private fun stopIfInterrupted() {
        if (Thread.currentThread().isInterrupted) {
            throw RuntimeException("Thread was interrupted")
        }
    }

    private fun contractNode(node: Int, level: Int): IntContainer {
        if (isContracted(node))
            throw IllegalArgumentException("Node $node was contracted already")
        contractionSW.start()
        chBuilder.setLevel(node, level)
        val neighbors = nodeContractor!!.contractNode(node)
        contractionSW.stop()
        return neighbors
    }

    private fun isContracted(node: Int): Boolean =
        chStore.getLevel(chStore.toNodePointer(node)) != maxLevel

    private fun logHeuristicStats(updateCounter: Int) {
        logger.info(String.format(Locale.ROOT,
                "%s, nodes: %10s, shortcuts: %10s, updates: %2d, checked-nodes: %10s, %s, %s, %s",
                (if (isEdgeBased()) "edge" else "node"),
                nf(sortedNodes!!.size().toLong()),
                nf(nodeContractor!!.getAddedShortcutsCount()),
                updateCounter,
                nf(checkCounter.toLong()),
                getTimesAsString(),
                nodeContractor!!.getStatisticsString(),
                Helper.getMemInfo()))
    }

    private fun logFixedNodeOrderingStats(nodesContracted: Int, logSize: Int, stopWatch: StopWatch) {
        logger.info(String.format(Locale.ROOT,
                "nodes: %10s / %10s (%6.2f%%), shortcuts: %10s, speed = %6.2f nodes/ms, %s, %s",
                nf(nodesContracted.toLong()),
                nf(nodes.toLong()),
                (100.0 * nodesContracted / nodes),
                nf(nodeContractor!!.getAddedShortcutsCount()),
                if (nodesContracted == 0) 0.0 else logSize / stopWatch.getMillis().toDouble(),
                nodeContractor!!.getStatisticsString(),
                Helper.getMemInfo())
        )
    }

    fun getCHConfig(): CHConfig = chConfig

    private fun getTimesAsString(): String {
        val totalTime = allSW.getCurrentSeconds()
        val periodicUpdateTime = periodicUpdateSW.getCurrentSeconds()
        val lazyUpdateTime = lazyUpdateSW.getCurrentSeconds()
        val neighborUpdateTime = neighborUpdateSW.getCurrentSeconds()
        val contractionTime = contractionSW.getCurrentSeconds()
        val otherTime = totalTime - (periodicUpdateTime + lazyUpdateTime + neighborUpdateTime + contractionTime)
        // dijkstra time is included in the others
        val dijkstraTime = nodeContractor!!.getDijkstraSeconds()
        return String.format(Locale.ROOT,
                "t(total): %6.2f,  t(period): %6.2f, t(lazy): %6.2f, t(neighbor): %6.2f, t(contr): %6.2f, t(other) : %6.2f, dijkstra-ratio: %6.2f%%",
                totalTime, periodicUpdateTime, lazyUpdateTime, neighborUpdateTime, contractionTime, otherTime, dijkstraTime / totalTime * 100)
    }

    fun getTotalPrepareTime(): Long = allSW.getMillis()

    private fun calculatePriority(node: Int): Float {
        if (isContracted(node))
            throw IllegalArgumentException("Priority should only be calculated for not yet contracted nodes")
        return nodeContractor!!.calculatePriority(node)
    }

    override fun toString(): String =
        if (chConfig.isEdgeBased) "prepare|dijkstrabi|edge|ch" else "prepare|dijkstrabi|ch"

    private fun _close() {
        nodeContractor!!.close()
        sortedNodes = null
    }

    internal fun flush() {
        chStore.flush()
    }

    internal fun close() {
        chStore.close()
    }

    class Result internal constructor(
        private val chConfig: CHConfig,
        private val chStorage: CHStorage,
        private val shortcuts: Long,
        private val lazyTime: Double,
        private val periodTime: Double,
        private val neighborTime: Double,
        private val totalPrepareTime: Long
    ) {
        fun getCHConfig(): CHConfig = chConfig

        fun getCHStorage(): CHStorage = chStorage

        fun getShortcuts(): Long = shortcuts

        fun getLazyTime(): Double = lazyTime

        fun getPeriodTime(): Double = periodTime

        fun getNeighborTime(): Double = neighborTime

        fun getTotalPrepareTime(): Long = totalPrepareTime
    }

    private class Params private constructor(
        periodicUpdatesPercentage: Int, lastNodesLazyUpdatePercentage: Int, neighborUpdatePercentage: Int,
        maxNeighborUpdates: Int, nodesContractedPercentage: Int, logMessagesPercentage: Int
    ) {
        /**
         * Specifies after how many contracted nodes a full refresh of the queue of remaining/not contracted nodes
         * is performed. For example for a graph with 1000 nodes a value of 20 means that a full refresh is performed
         * after every 200 nodes (20% of the number of nodes of the graph). The more of these updates are performed
         * the longer the preparation will take, but the more up-to-date the node priorities will be. Higher values
         * here mean fewer updates!
         */
        var periodicUpdatesPercentage: Int = 0
            set(value) {
                checkPercentage(PERIODIC_UPDATES, value)
                field = value
            }

        /**
         * Specifies the fraction of nodes for which lazy updates will be performed. For example a value of 20 means
         * that lazy updates will be performed for the last 20% of all nodes. A value of 100 means lazy updates will
         * be performed for all nodes. Higher values here lead to a longer preparation time, but the node priorities
         * will be more up-to-date (potentially leading to a better preparation (less shortcuts/faster queries)).
         */
        var lastNodesLazyUpdatePercentage: Int = 0
            set(value) {
                checkPercentage(LAST_LAZY_NODES_UPDATES, value)
                field = value
            }

        /**
         * Specifies the probability that the priority of a given neighbor of a contracted node will be updated after
         * the node was contracted. For example a value of 20 means that on average 20% of the neighbor nodes will be
         * updated / each neighbor will be updated with a chance of 20%. Higher values here lead to longer preparation
         * times, but the node priorities will be more up-to-date.
         */
        var neighborUpdatePercentage: Int = 0
            set(value) {
                checkPercentage(NEIGHBOR_UPDATES, value)
                field = value
            }

        /**
         * Specifies the maximum number of neighbor updates per contracted node. For example for the foot profile we
         * see a large number of neighbor updates that can be limited with this setting. -1 means unlimited.
         */
        var maxNeighborUpdates: Int = 0

        /**
         * Defines how many nodes (percentage) should be contracted. A value of 20 means only the first 20% of all nodes
         * will be contracted. Higher values here mean longer preparation times, but faster queries (because the
         * graph will be fully contracted).
         */
        var nodesContractedPercentage: Int = 0
            set(value) {
                checkPercentage(CONTRACTED_NODES, value)
                field = value
            }

        /**
         * Specifies how often a log message should be printed.
         *
         * @see periodicUpdatesPercentage
         */
        var logMessagesPercentage: Int = 0
            set(value) {
                checkPercentage(LOG_MESSAGES, value)
                field = value
            }

        init {
            this.periodicUpdatesPercentage = periodicUpdatesPercentage
            this.lastNodesLazyUpdatePercentage = lastNodesLazyUpdatePercentage
            this.neighborUpdatePercentage = neighborUpdatePercentage
            this.maxNeighborUpdates = maxNeighborUpdates
            this.nodesContractedPercentage = nodesContractedPercentage
            this.logMessagesPercentage = logMessagesPercentage
        }

        private fun checkPercentage(name: String, value: Int) {
            if (value < 0 || value > 100) {
                throw IllegalArgumentException("$name has to be in [0, 100], to disable it use 0")
            }
        }

        companion object {
            fun forTraversalMode(traversalMode: TraversalMode): Params {
                // Lower values for the neighbor update percentage (and/or max neighbor updates) yield a slower
                // preparation but possibly fewer shortcuts and a slightly better query time.
                return if (traversalMode.isEdgeBased) {
                    Params(0, 100, 50, 3, 100, 5)
                } else {
                    Params(0, 100, 100, 2, 100, 20)
                }
            }
        }
    }
}
