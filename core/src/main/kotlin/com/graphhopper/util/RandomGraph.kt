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

package com.graphhopper.util

import androidx.collection.MutableIntSet
import com.graphhopper.coll.primitive.LongArrayList
import com.graphhopper.coll.primitive.LongScatterSet
import com.graphhopper.routing.ev.DecimalEncodedValue
import com.graphhopper.storage.BaseGraph
import java.util.ArrayDeque
import java.util.Random
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Creates random graphs for testing purposes. Nodes are aligned on a grid (+jitter), and connected
 * to their KNN
 */
class RandomGraph private constructor() {

    companion object {
        @JvmStatic
        fun start(): Builder = Builder()
    }

    class Builder {

        private var seed = 42L
        private var nodes = 10
        private var tree = false
        private var chain = false
        private var speed: Double? = null
        private var duplicateEdges = 0.05
        private var curviness = 0.0
        private var speedMean = 16.0
        private var speedStdDev = 8.0
        private var pSpeedZero = 0.0

        private val centerLat = 50.0
        private val centerLon = 10.0
        private val step = 0.001
        private val rowFactor = 0.9
        private val jitter = 0.8
        private val kMin = 2
        private val kMax = 3

        private class TmpGraph(val lats: DoubleArray, val lons: DoubleArray, val edges: LongArrayList)

        fun fill(graph: BaseGraph, speedEnc: DecimalEncodedValue?) {
            if (graph.nodes > 0 || graph.edges > 0)
                throw IllegalStateException("BaseGraph should be empty")
            if (chain && tree)
                throw IllegalArgumentException("chain and tree are mutually exclusive")
            if (chain)
                buildChain(graph, speedEnc)
            else if (tree)
                buildTree(graph, speedEnc)
            else
                buildGraph(graph, speedEnc)
        }

        private fun buildGraph(graph: BaseGraph, speedEnc: DecimalEncodedValue?) {
            val rnd = Random(seed)
            val g = generateTmpGraph(nodes, rnd)
            fillBaseGraph(graph, speedEnc, rnd, g)
        }

        private fun generateTmpGraph(nodes: Int, rnd: Random): TmpGraph {
            val lats = DoubleArray(nodes)
            val lons = DoubleArray(nodes)
            generateNodePositions(rnd, nodes, lats, lons)
            val edges = generateKnnEdges(rnd, nodes, lats, lons)
            val duplicates = ceil(edges.size() * duplicateEdges).toInt()
            for (i in 0 until duplicates)
                edges.add(edges.get(rnd.nextInt(edges.size())))
            return TmpGraph(lats, lons, edges)
        }

        private fun generateNodePositions(rnd: Random, n: Int, lats: DoubleArray, lons: DoubleArray) {
            val cols = max(1, Math.round(sqrt(n / rowFactor)).toInt())
            val rows = ceil(n.toDouble() / cols).toInt()
            val offsetLat = ((rows - 1) * step) / 2.0
            val offsetLon = ((cols - 1) * step) / 2.0
            for (i in 0 until n) {
                val r = i / cols
                val c = i % cols
                lats[i] = centerLat - offsetLat + r * step + (rnd.nextDouble() - 0.5) * jitter * step
                lons[i] = centerLon - offsetLon + c * step + (rnd.nextDouble() - 0.5) * jitter * step
            }
        }

        private fun generateKnnEdges(rnd: Random, n: Int, lats: DoubleArray, lons: DoubleArray): LongArrayList {
            class Pair(val j: Int, val d: Double)

            val edges = LongScatterSet()
            for (i in 0 until n) {
                val ki = kMin + rnd.nextInt(kMax - kMin + 1)
                val list = ArrayList<Pair>()
                for (j in 0 until n) {
                    if (j == i) continue
                    val dLat = lats[i] - lats[j]
                    val dLon = lons[i] - lons[j]
                    list.add(Pair(j, dLat * dLat + dLon * dLon))
                }
                list.sortBy { it.d }
                val limit = min(ki, list.size)
                for (k in 0 until limit) {
                    val j = list[k].j
                    val a = min(i, j)
                    val b = max(i, j)
                    edges.add(BitUtil.LITTLE.toLong(a, b))
                }
            }
            // hppc LongArrayList(LongContainer) materialized the set in cursor-iterator order
            val result = LongArrayList(edges.size())
            edges.forEachInIteratorOrder { e -> result.add(e) }
            return result
        }

        private fun fillBaseGraph(graph: BaseGraph, speedEnc: DecimalEncodedValue?, rnd: Random, g: TmpGraph) {
            val na = graph.nodeAccess
            for (i in g.lats.indices) {
                na.setNode(i, g.lats[i], g.lons[i])
            }
            for (e in g.edges) {
                val from = BitUtil.LITTLE.getIntHigh(e.value)
                val to = BitUtil.LITTLE.getIntLow(e.value)
                val edge = graph.edge(from, to)

                val beeline = GHUtility.getDistance(from, to, na)
                var distance = max(beeline, beeline * (1 + curviness * rnd.nextDouble()))
                if (distance < 0.001) distance = 0.001
                edge.setDistance(distance)

                var fwdSpeed = max(1.0, min(50.0, speedMean + speedStdDev * rnd.nextGaussian()))
                var bwdSpeed = max(1.0, min(50.0, speedMean + speedStdDev * rnd.nextGaussian()))
                // if an explicit speed is given we discard the random speeds and use the given one instead
                val explicitSpeed = speed
                if (explicitSpeed != null) {
                    fwdSpeed = explicitSpeed
                    bwdSpeed = explicitSpeed
                }
                // zero speeds are possible even if an explicit speed is given
                if (rnd.nextDouble() < pSpeedZero)
                    fwdSpeed = 0.0
                if (rnd.nextDouble() < pSpeedZero)
                    bwdSpeed = 0.0
                if (speedEnc != null) {
                    edge.set(speedEnc, fwdSpeed)
                    if (speedEnc.isStoreTwoDirections)
                        edge.setReverse(speedEnc, bwdSpeed)
                }
            }
        }

        private fun buildTree(graph: BaseGraph, speedEnc: DecimalEncodedValue?) {
            for (attempt in 0 until 1000) {
                val trySeed = seed + attempt
                val rnd = Random(trySeed)
                val g = generateTmpGraph(nodes, rnd)
                val treeEdges = findBFSTreeEdgesFromCenter(g)
                val nodesInTree: MutableIntSet = MutableIntSet()
                for (e in treeEdges) {
                    nodesInTree.add(BitUtil.LITTLE.getIntHigh(e.value))
                    nodesInTree.add(BitUtil.LITTLE.getIntLow(e.value))
                }
                // we wait until we find a graph that is fully connected to make sure our tree has the
                // desired number of nodes
                if (nodesInTree.size == nodes) {
                    val tree = TmpGraph(g.lats, g.lons, treeEdges)
                    fillBaseGraph(graph, speedEnc, rnd, tree)
                    return
                }
            }
            throw IllegalStateException("Could not generate a spanning tree after 1000 attempts")
        }

        private fun buildChain(graph: BaseGraph, speedEnc: DecimalEncodedValue?) {
            val rnd = Random(seed)
            val lats = DoubleArray(nodes)
            val lons = DoubleArray(nodes)
            generateNodePositions(rnd, nodes, lats, lons)

            // connect nodes in serpentine order so consecutive chain nodes are grid-neighbors
            val cols = max(1, Math.round(sqrt(nodes / rowFactor)).toInt())
            val rows = ceil(nodes.toDouble() / cols).toInt()
            val order = IntArray(nodes)
            var idx = 0
            for (r in 0 until rows) {
                val start = r * cols
                val end = min(start + cols, nodes)
                if (r % 2 == 0) {
                    for (c in start until end)
                        order[idx++] = c
                } else {
                    for (c in end - 1 downTo start)
                        order[idx++] = c
                }
            }

            val edges = LongArrayList()
            for (i in 0 until nodes - 1) {
                val a = min(order[i], order[i + 1])
                val b = max(order[i], order[i + 1])
                edges.add(BitUtil.LITTLE.toLong(a, b))
            }
            val g = TmpGraph(lats, lons, edges)
            fillBaseGraph(graph, speedEnc, rnd, g)
        }

        private fun findBFSTreeEdgesFromCenter(g: TmpGraph): LongArrayList {
            val adjNodes = HashMap<Int, MutableList<Int>>()
            for (e in g.edges) {
                val a = BitUtil.LITTLE.getIntHigh(e.value)
                val b = BitUtil.LITTLE.getIntLow(e.value)
                adjNodes.computeIfAbsent(a) { ArrayList() }.add(b)
                adjNodes.computeIfAbsent(b) { ArrayList() }.add(a)
            }
            var center = 0
            var best = Double.MAX_VALUE
            for (i in g.lats.indices) {
                val d = (g.lats[i] - centerLat) * (g.lats[i] - centerLat) + (g.lons[i] - centerLon) * (g.lons[i] - centerLon)
                if (d < best) {
                    best = d
                    center = i
                }
            }
            val visited = BooleanArray(g.lats.size)
            visited[center] = true
            val queue = ArrayDeque<Int>()
            queue.add(center)
            val treeEdges = LongScatterSet()
            while (!queue.isEmpty()) {
                val cur = queue.poll()
                for (nb in adjNodes.getOrDefault(cur, listOf())) {
                    if (!visited[nb]) {
                        visited[nb] = true
                        treeEdges.add(BitUtil.LITTLE.toLong(min(cur, nb), max(cur, nb)))
                        queue.add(nb)
                    }
                }
            }
            // hppc LongArrayList(LongContainer) materialized the set in cursor-iterator order
            val result = LongArrayList(treeEdges.size())
            treeEdges.forEachInIteratorOrder { e -> result.add(e) }
            return result
        }

        fun seed(v: Long): Builder {
            seed = v
            return this
        }

        fun nodes(v: Int): Builder {
            nodes = v
            return this
        }

        fun tree(v: Boolean): Builder {
            tree = v
            return this
        }

        fun chain(v: Boolean): Builder {
            chain = v
            return this
        }

        fun speed(v: Double?): Builder {
            speed = v
            return this
        }

        fun duplicateEdges(v: Double): Builder {
            duplicateEdges = v
            return this
        }

        fun curviness(v: Double): Builder {
            curviness = v
            return this
        }

        fun speedMean(v: Double): Builder {
            speedMean = v
            return this
        }

        fun speedStdDev(v: Double): Builder {
            speedStdDev = v
            return this
        }

        fun speedZero(v: Double): Builder {
            pSpeedZero = v
            return this
        }
    }
}
