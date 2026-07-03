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

import com.graphhopper.coll.primitive.IntIndexedContainer
import com.graphhopper.routing.weighting.Weighting
import com.graphhopper.storage.Graph
import com.graphhopper.storage.RoutingCHGraph
import com.graphhopper.util.EdgeIterator.Companion.ANY_EDGE
import com.graphhopper.util.EdgeIteratorState
import com.graphhopper.util.PMap
import java.util.Comparator

/**
 * Minimum number-of-moving-parts implementation of alternative route search with
 * contraction hierarchies.
 *
 * "Alternative Routes in Road Networks" (Abraham et al.)
 *
 * @author michaz
 */
open class AlternativeRouteEdgeCH(graph: RoutingCHGraph, hints: PMap) : DijkstraBidirectionEdgeCHNoSOD(graph) {

    private val maxWeightFactor: Double = hints.getDouble("alternative_route.max_weight_factor", 1.25)
    private val maxShareFactor: Double = hints.getDouble("alternative_route.max_share_factor", 0.8)
    private val localOptimalityFactor: Double = hints.getDouble("alternative_route.local_optimality_factor", 0.25)
    private val maxPaths: Int = hints.getInt("alternative_route.max_paths", 3)
    private val alternatives = ArrayList<AlternativeInfo>()
    private var extraVisitedNodes = 0

    override fun finished(): Boolean {
        if (finishedFrom && finishedTo)
            return true

        // Continue search longer than for point to point search -- not sure if makes a difference at all
        return currFrom!!.weight >= bestWeight * maxWeightFactor && currTo!!.weight >= bestWeight * maxWeightFactor
    }

    override fun getVisitedNodes(): Int = visitedCountFrom + visitedCountTo + extraVisitedNodes

    @JvmName("calcAlternatives")
    internal fun calcAlternatives(s: Int, t: Int): List<AlternativeInfo> {
        // First, do a regular bidirectional route search
        checkAlreadyRun()
        init(s, 0.0, t, 0.0)
        runAlgo()
        val bestPath = extractPath()
        if (!bestPath.isFound()) {
            return emptyList()
        }

        alternatives.add(AlternativeInfo(bestPath, 0.0))

        val potentialAlternativeInfos = ArrayList<PotentialAlternativeInfo>()

        val bestWeightMapByNode = HashMap<Int, SPTEntry>()
        bestWeightMapTo.forEachWhile { key, value ->
            bestWeightMapByNode.put(value.adjNode, value)
            true
        }

        bestWeightMapFrom.forEachWhile { wurst, fromSPTEntry ->
            val toSPTEntry = bestWeightMapByNode.get(fromSPTEntry.adjNode) ?: return@forEachWhile true

            if (fromSPTEntry.getWeightOfVisitedPath() + toSPTEntry.getWeightOfVisitedPath() > bestPath.getWeight() * maxWeightFactor)
                return@forEachWhile true

            // This gives us a path s -> v -> t, but since we are using contraction hierarchies,
            // s -> v and v -> t need not be shortest paths. In fact, they can sometimes be pretty strange.
            // We still use this preliminary path to filter for shared path length with other alternatives,
            // so we don't have to work so much.
            val preliminaryRoute = createPathExtractor().extract(fromSPTEntry, toSPTEntry, fromSPTEntry.getWeightOfVisitedPath() + toSPTEntry.getWeightOfVisitedPath())
            val preliminaryShare = calculateShare(preliminaryRoute)
            if (preliminaryShare > maxShareFactor) {
                return@forEachWhile true
            }
            assert(fromSPTEntry.adjNode == toSPTEntry.adjNode)
            val potentialAlternativeInfo = PotentialAlternativeInfo()
            potentialAlternativeInfo.v = fromSPTEntry.adjNode
            potentialAlternativeInfo.edgeIn = getIncomingEdge(fromSPTEntry)
            potentialAlternativeInfo.weight = 0.2 * (fromSPTEntry.getWeightOfVisitedPath() + toSPTEntry.getWeightOfVisitedPath()) + preliminaryShare
            potentialAlternativeInfos.add(potentialAlternativeInfo)
            true
        }

        potentialAlternativeInfos.sortWith(Comparator.comparingDouble { o: PotentialAlternativeInfo -> o.weight })

        for (potentialAlternativeInfo in potentialAlternativeInfos) {
            val v = potentialAlternativeInfo.v
            val tailSv = potentialAlternativeInfo.edgeIn

            // Okay, now we want the s -> v -> t shortest via-path, so we route s -> v and v -> t
            // and glue them together.
            val svRouter = DijkstraBidirectionEdgeCHNoSOD(graph)
            val suvPath = svRouter.calcPath(s, v, ANY_EDGE, tailSv)
            extraVisitedNodes += svRouter.getVisitedNodes()

            val u = graph.baseGraph.getEdgeIteratorState(tailSv, v)!!.baseNode

            val vtRouter = DijkstraBidirectionEdgeCHNoSOD(graph)
            val uvtPath = vtRouter.calcPath(u, t, tailSv, ANY_EDGE)
            if (!uvtPath.isFound())
                // we were looking for the s->u->v->(x->)t path, but there might be a turn restriction
                // at u->v->x in which case uvtPath is not found. If we do not stop here we might return
                // an alternative that does not even reach t, and has a lower weight than the best path.
                continue
            val path = concat(graph.baseGraph, graph.baseGraph.wrapWeighting(graph.weighting), suvPath, uvtPath)
            extraVisitedNodes += vtRouter.getVisitedNodes()

            val sharedDistanceWithShortest = sharedDistanceWithShortest(path)
            val detourLength = path.getDistance() - sharedDistanceWithShortest
            val directLength = bestPath.getDistance() - sharedDistanceWithShortest
            if (detourLength > directLength * maxWeightFactor) {
                continue
            }

            val share = calculateShare(path)
            if (share > maxShareFactor) {
                continue
            }

            // This is the final test we need: Discard paths that are not "locally shortest" around v.
            // So move a couple of nodes to the left and right from v on our path,
            // route, and check if v is on the shortest path.
            val svNodes = suvPath.calcNodes()
            val vIndex = svNodes.size() - 1
            if (!tTest(path, vIndex))
                continue

            alternatives.add(AlternativeInfo(path, share))
            if (alternatives.size >= maxPaths)
                break
        }
        return alternatives
    }

    private fun calculateShare(path: Path): Double {
        val sharedDistance = sharedDistance(path)
        return sharedDistance / path.getDistance()
    }

    private fun sharedDistance(path: Path): Double {
        var sharedDistance = 0.0
        val edges = path.calcEdges()
        for (edge in edges) {
            if (nodesInCurrentAlternativeSetContains(edge.baseNode) && nodesInCurrentAlternativeSetContains(edge.adjNode)) {
                sharedDistance += edge.distance
            }
        }
        return sharedDistance
    }

    private fun sharedDistanceWithShortest(path: Path): Double {
        var sharedDistance = 0.0
        val edges = path.calcEdges()
        for (edge in edges) {
            if (alternatives.get(0).nodes.contains(edge.baseNode) && alternatives.get(0).nodes.contains(edge.adjNode)) {
                sharedDistance += edge.distance
            }
        }
        return sharedDistance
    }

    private fun nodesInCurrentAlternativeSetContains(v: Int): Boolean {
        for (alternative in alternatives) {
            if (alternative.nodes.contains(v)) {
                return true
            }
        }
        return false
    }

    private fun tTest(path: Path, vIndex: Int): Boolean {
        if (path.getEdgeCount() == 0) return true
        val detourDistance = detourDistance(path)
        val T = 0.5 * localOptimalityFactor * detourDistance
        val fromNode = getPreviousNodeTMetersAway(path, vIndex, T)
        val toNode = getNextNodeTMetersAway(path, vIndex, T)
        val tRouter = DijkstraBidirectionEdgeCHNoSOD(graph)
        val tPath = tRouter.calcPath(fromNode.baseNode, toNode.adjNode, fromNode.edge, toNode.edge)
        extraVisitedNodes += tRouter.getVisitedNodes()
        val tNodes = tPath.calcNodes()
        val v = path.calcNodes().get(vIndex)
        return tNodes.contains(v)
    }

    private fun detourDistance(path: Path): Double {
        return path.getDistance() - sharedDistanceWithShortest(path)
    }

    private fun getPreviousNodeTMetersAway(path: Path, vIndex: Int, T: Double): EdgeIteratorState {
        val edges = path.calcEdges()
        var distance = 0.0
        var i = vIndex
        while (i > 0 && distance < T) {
            distance += edges.get(i - 1).distance
            i--
        }
        return edges.get(i)
    }

    private fun getNextNodeTMetersAway(path: Path, vIndex: Int, T: Double): EdgeIteratorState {
        val edges = path.calcEdges()
        var distance = 0.0
        var i = vIndex
        while (i < edges.size - 1 && distance < T) {
            distance += edges.get(i).distance
            i++
        }
        return edges.get(i - 1)
    }

    override fun calcPaths(from: Int, to: Int): List<Path> {
        val alts = calcAlternatives(from, to)
        if (alts.isEmpty()) {
            return listOf(createEmptyPath())
        }
        val paths = ArrayList<Path>(alts.size)
        for (a in alts) {
            paths.add(a.path)
        }
        return paths
    }

    companion object {
        private fun concat(graph: Graph, weighting: Weighting, suvPath: Path, uvtPath: Path): Path {
            assert(suvPath.isFound())
            assert(uvtPath.isFound())
            val path = Path(graph)
            path.setFromNode(suvPath.getFromNode())
            path.getEdges().addAll(suvPath.getEdges())
            check(!uvtPath.getEdges().isEmpty) { "uvtPath.getEdges() should not be empty" }
            val uvtPathI = uvtPath.getEdges().iterator()
            val uvEdge = uvtPathI.next().value // skip u-v edge
            uvtPathI.forEachRemaining { edge -> path.addEdge(edge.value) }
            val vuEdgeState = graph.getEdgeIteratorState(uvEdge, uvtPath.getFromNode())!!
            path.setEndNode(uvtPath.getEndNode())
            path.setWeight(suvPath.getWeight() + uvtPath.getWeight() - weighting.calcEdgeWeight(vuEdgeState, true))
            path.addDistance_mm(suvPath.getDistance_mm() + uvtPath.getDistance_mm() - vuEdgeState.distance_mm)
            path.addTime(suvPath.getTime() + uvtPath.getTime() - weighting.calcEdgeMillis(vuEdgeState, true))
            path.setFound(true)
            return path
        }
    }

    class PotentialAlternativeInfo {
        @JvmField
        var v = 0

        @JvmField
        var edgeIn = 0

        @JvmField
        var weight = 0.0

        override fun toString(): String {
            return "node=$v, edgeIn=$edgeIn, weight=$weight"
        }
    }

    class AlternativeInfo internal constructor(@JvmField internal val path: Path, @JvmField internal val shareWeight: Double) {
        @JvmField
        internal val nodes: IntIndexedContainer = path.calcNodes()

        override fun toString(): String {
            return "AlternativeInfo{" +
                    "shareWeight=" + shareWeight +
                    ", path=" + path.calcNodes() +
                    '}'
        }

        fun getPath(): Path = path
    }
}
