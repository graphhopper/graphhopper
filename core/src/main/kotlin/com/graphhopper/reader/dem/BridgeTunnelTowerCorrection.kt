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
package com.graphhopper.reader.dem

import com.graphhopper.coll.primitive.DoubleArrayList
import com.graphhopper.coll.primitive.IntArrayList
import com.carrotsearch.hppc.IntDoubleHashMap
import com.graphhopper.apache.commons.collections.IntFloatBinaryHeap
import com.graphhopper.coll.GHBitSet
import com.graphhopper.coll.GHTBitSet
import com.graphhopper.routing.ev.EnumEncodedValue
import com.graphhopper.routing.ev.RoadEnvironment
import com.graphhopper.storage.BaseGraph
import com.graphhopper.storage.NodeAccess
import com.graphhopper.util.DistanceCalcEarth
import com.graphhopper.util.DistancePlaneProjection
import com.graphhopper.util.EdgeExplorer
import com.graphhopper.util.EdgeIteratorState
import com.graphhopper.util.FetchMode
import com.graphhopper.util.StopWatch
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.BitSet

/**
 * Fixes the DEM elevation of bridge/tunnel/ferry tower-end nodes from the surrounding road,
 * then re-interpolates the pillars on the adjacent ramp edges.
 *
 * The DEM at a bridge end often hits the valley/river below the structure (the deck) and reads
 * too low, at a tunnel end the surface above and reads too high; the surrounding road is the
 * correct anchor. For each
 * structure-touching tower we run Dijkstra (≤ [MAX_DIST_M]) over non-structure edges,
 * sampling only pure-ground nodes — walking past other structure nodes so shared/parallel bridges
 * don't stop the search — and set the tower to the inverse-distance-weighted mean (IDW) of those
 * samples. An edge that overshoots the budget is sampled at the cutoff.
 *
 * An IDW mean cannot leave the sample range, so a tower at the bottom (or top) of a real gradient
 * would be dragged towards its one-sided samples and spike. We therefore apply a correction only
 * if it does not steepen the road or the structure (see [steepensIncidentEdges]).
 *
 * [EdgeElevationInterpolator] runs after this and fills the structure interior (pillars,
 * inner towers) from the corrected outer towers.
 */
class BridgeTunnelTowerCorrection(
    private val graph: BaseGraph,
    private val roadEnvEnc: EnumEncodedValue<RoadEnvironment>
) {

    fun execute() {
        val nodeAccess = graph.nodeAccess
        val explorer = graph.createEdgeExplorer()
        val numNodes = graph.nodes

        var sw = StopWatch().start()

        // structure-touching tower nodes are correction candidates. Later a bit is
        // cleared if the elevation doesn't change (too few ground samples, the computed value equals
        // the DEM, or the guard rejected it), so afterwards a set bit means "elevation changed" —
        // which is what the pillar loop needs.
        val pendingNodes = BitSet(numNodes)
        // nodes with at least one non-structure edge.
        val groundTouching = BitSet(numNodes)
        val allIter = graph.allEdges
        while (allIter.next()) {
            if (isStructureEdge(allIter)) {
                pendingNodes.set(allIter.baseNode)
                pendingNodes.set(allIter.adjNode)
            } else {
                groundTouching.set(allIter.baseNode)
                groundTouching.set(allIter.adjNode)
            }
        }

        val initTime = sw.stop().getSeconds()
        sw = StopWatch().start()

        // For each tower, collect ground samples via Dijkstra and compute the IDW elevation.
        // Corrections are staged in newEles (not applied in place) so the guard below can judge
        // every tower against the same full set of proposals, independent of node order.
        // Scratch buffers are allocated once and clear()ed per tower to avoid GC churn.
        val sampleEles = DoubleArrayList()
        val sampleDists = DoubleArrayList()
        val settled: GHBitSet = GHTBitSet()
        val distFromStart = IntDoubleHashMap()
        val heap = IntFloatBinaryHeap()
        val newEles = IntDoubleHashMap()
        var skipped = 0
        for (n in 0 until numNodes) {
            if (!pendingNodes.get(n)) continue
            sampleEles.clear()
            sampleDists.clear()
            settled.clear()
            distFromStart.clear()
            heap.clear()
            dijkstraCollectRoadSamples(n, nodeAccess, explorer, sampleEles, sampleDists,
                    settled, distFromStart, heap)
            if (sampleEles.isEmpty) {
                pendingNodes.clear(n)
                skipped++
                continue
            }
            // Replace the DEM value outright (no dampening): if it is already consistent IDW ≈ obs,
            // and the threshold below leaves it untouched.
            val newZ = inverseDistanceWeightedMean(sampleEles, sampleDists)
            val obs = nodeAccess.getEle(n)
            if (Math.abs(newZ - obs) > 1e-6)
                newEles.put(n, newZ)
            else
                pendingNodes.clear(n)
        }

        // Guard: drop any correction that would steepen the ramp or structure (see steepensIncidentEdges).
        // Decisions use the full set of proposals (collect first, then remove) so they don't depend
        // on node order — a valley viaduct's two towers are still lifted together. Removing one tower
        // can make a neighbour that was only accepted thanks to that tower's lift now steepen against
        // the reverted DEM, so we repeat until a pass rejects nothing. Removals only ever add
        // rejections, so this reaches a fixed point.
        var rejectedCount = 0
        val rejected = IntArrayList()
        var changed: Boolean
        do {
            rejected.clear()
            for (c in newEles)
                if (steepensIncidentEdges(c.key, c.value, nodeAccess, explorer, newEles, groundTouching))
                    rejected.add(c.key)
            changed = !rejected.isEmpty
            for (c in rejected) {
                newEles.remove(c.value)
                pendingNodes.clear(c.value)
            }
            rejectedCount += rejected.size()
        } while (changed)
        for (c in newEles)
            nodeAccess.setNode(c.key, nodeAccess.getLat(c.key), nodeAccess.getLon(c.key), c.value)
        val corrected = newEles.size()

        val dijkstraTime = sw.stop().getSeconds()
        sw = StopWatch().start()

        // Re-interpolate pillars only on ramp edges whose tower endpoints actually moved (very important filter for performance).
        val elevationInterpolator = ElevationInterpolator()
        val edgeIter = graph.allEdges
        while (edgeIter.next()) {
            if (isStructureEdge(edgeIter)) continue
            if (pendingNodes.get(edgeIter.baseNode)
                    || pendingNodes.get(edgeIter.adjNode))
                reinterpolatePillars(edgeIter, nodeAccess, elevationInterpolator)
        }

        LOGGER.info("BridgeTunnelTowerCorrection: corrected {} towers, skipped {} (insufficient road samples), " +
                        "rejected {} (would steepen the road network). init {}s, dijkstra {}s, interpolate {}s",
                corrected, skipped, rejectedCount, initTime.toInt(), dijkstraTime.toInt(), sw.stop().getSeconds().toInt())
    }

    /**
     * True if setting the tower to `newZ` would make the road steeper than the raw DEM — i.e.
     * the "correction" would create a spike rather than remove one — so it is rejected and the DEM
     * kept. This happens at a tower on a real gradient: the structure blocks one side, all samples
     * are uphill, and the IDW pulls a self-consistent tower up.
     *
     * Rejected if the lift increases the steepest slope over either (a) all incident edges, or
     * (b) the structure edges alone. Test (b) is needed because the lift flattens the steep ramp under
     * such a tower, which would hide the structure steepening if only (a) were checked (the Gsollstraße
     * bridges near the B115). Both compare the steepest edge before/after, so a structure whose DEM is
     * already spiky can still be smoothed (a Monaco hillside tunnel), and (a) still keeps a tower
     * whose lift would steepen the uphill ramp.
     *
     * A neighbour with its own proposed correction is judged at that value; inner towers (no ground
     * contact) carry a meaningless DEM and are skipped — they are filled later by
     * [EdgeElevationInterpolator].
     */
    private fun steepensIncidentEdges(node: Int, newZ: Double, nodeAccess: NodeAccess, explorer: EdgeExplorer,
                                      newEles: IntDoubleHashMap, groundTouching: BitSet): Boolean {
        val obs = nodeAccess.getEle(node)
        var maxBefore = 0.0
        var maxAfter = 0.0
        var maxStructureBefore = 0.0
        var maxStructureAfter = 0.0
        val it = explorer.setBaseNode(node)
        while (it.next()) {
            val dist = it.distance
            if (dist < 1) continue
            val adj = it.adjNode
            // Skip inner towers (no ground touching edges, e.g. B in a bridge A-B-C): B is not lifted but
            // interpolated later, so judging A and C against B's stale elevation would wrongly reject both.
            // Not caught: A lifted while C lowered worsens the combined A-C slope, but we accept it.
            if (!groundTouching.get(adj)) continue
            val adjObs = nodeAccess.getEle(adj)
            val adjNew = newEles.getOrDefault(adj, adjObs)
            val slopeBefore = Math.abs(adjObs - obs) / dist
            val slopeAfter = Math.abs(adjNew - newZ) / dist
            maxBefore = Math.max(maxBefore, slopeBefore)
            maxAfter = Math.max(maxAfter, slopeAfter)
            if (isStructureEdge(it)) {
                maxStructureBefore = Math.max(maxStructureBefore, slopeBefore)
                maxStructureAfter = Math.max(maxStructureAfter, slopeAfter)
            }
        }
        return maxAfter > maxBefore + 1e-9 || maxStructureAfter > maxStructureBefore + 1e-9
    }

    /**
     * Collect pure-ground node elevations within [MAX_DIST_M] via Dijkstra, so the IDW weights
     * and the distance cutoff use each node's true shortest-path distance from the tower. Whether a
     * node touches a structure is checked inline at settle time.
     */
    private fun dijkstraCollectRoadSamples(startTower: Int,
                                           nodeAccess: NodeAccess, explorer: EdgeExplorer,
                                           sampleEles: DoubleArrayList, sampleDists: DoubleArrayList,
                                           settled: GHBitSet, distFromStart: IntDoubleHashMap,
                                           heap: IntFloatBinaryHeap) {
        distFromStart.put(startTower, 0.0)
        heap.insert(0.0, startTower)

        while (!heap.isEmpty()) {
            val n = heap.poll()
            val dN = distFromStart.get(n) // full-precision settled distance (heap key is only a float ordering hint)

            if (settled.contains(n)) continue // stale entry — n was already settled via a shorter path
            settled.add(n)

            val it = explorer.setBaseNode(n)
            var nodeTouchesStructure = false
            while (it.next()) {
                if (isStructureEdge(it)) {
                    nodeTouchesStructure = true
                    continue
                }
                val adj = it.adjNode
                val edgeDist = it.distance
                val newDist = dN + edgeDist
                if (newDist > MAX_DIST_M) {
                    // Edge overshoots: sample along way-geometry at the budget cutoff.
                    val remaining = MAX_DIST_M - dN
                    if (remaining > 0) {
                        val virtualEle = sampleEleAlongEdge(it, remaining)
                        if (!java.lang.Double.isNaN(virtualEle)) {
                            sampleEles.add(virtualEle)
                            sampleDists.add(dN + remaining)
                        }
                    }
                    continue
                }
                if (!settled.contains(adj)
                        && (!distFromStart.containsKey(adj) || newDist < distFromStart.get(adj))) {
                    distFromStart.put(adj, newDist)
                    // Enqueue structure nodes too (walk past them) so parallel bridges sharing a tower
                    // (e.g. the Albertbrücke) don't stop the search; sampling is decided per settled node.
                    heap.insert(newDist, adj)
                }
            }
            // Sample n only if it is pure ground (the start tower touches a structure, so it's excluded).
            if (!nodeTouchesStructure) {
                sampleEles.add(nodeAccess.getEle(n))
                sampleDists.add(dN)
            }
        }
    }

    /**
     * Sample the elevation at a given distance along an edge's way-geometry,
     * linearly interpolating between the two surrounding pillar/tower points.
     * Returns NaN if the edge geometry is unusable.
     */
    private fun sampleEleAlongEdge(edge: EdgeIteratorState, distAlongEdge: Double): Double {
        val pl = edge.fetchWayGeometry(FetchMode.ALL)
        if (pl.size() < 2) return Double.NaN
        var cum = 0.0
        for (i in 0 until pl.size() - 1) {
            val segLen = DistancePlaneProjection.DIST_PLANE.calcDist(
                    pl.getLat(i), pl.getLon(i), pl.getLat(i + 1), pl.getLon(i + 1))
            if (cum + segLen >= distAlongEdge) {
                val frac = if (segLen > 0) (distAlongEdge - cum) / segLen else 0.0
                return pl.getEle(i) + frac * (pl.getEle(i + 1) - pl.getEle(i))
            }
            cum += segLen
        }
        return pl.getEle(pl.size() - 1)
    }

    /**
     * Re-interpolate pillar nodes on a non-structure edge linearly between its two
     * tower endpoints. Mirrors [EdgeElevationInterpolator]'s Phase 2 for
     * bridge/tunnel edges. Also recomputes the edge distance.
     */
    private fun reinterpolatePillars(edge: EdgeIteratorState, nodeAccess: NodeAccess,
                                     elevationInterpolator: ElevationInterpolator) {
        val firstNodeId = edge.baseNode
        val secondNodeId = edge.adjNode
        val lat0 = nodeAccess.getLat(firstNodeId)
        val lon0 = nodeAccess.getLon(firstNodeId)
        val ele0 = nodeAccess.getEle(firstNodeId)
        val lat1 = nodeAccess.getLat(secondNodeId)
        val lon1 = nodeAccess.getLon(secondNodeId)
        val ele1 = nodeAccess.getEle(secondNodeId)

        // Mutate the fetched PointList in place (mirrors EdgeElevationInterpolator). Always recompute
        // the distance — even with no pillars a tower endpoint's elevation may have changed.
        val pointList = edge.fetchWayGeometry(FetchMode.ALL)
        val count = pointList.size()
        for (index in 1 until count - 1) {
            val lat = pointList.getLat(index)
            val lon = pointList.getLon(index)
            val ele = elevationInterpolator.calculateElevationBasedOnTwoPoints(lat, lon,
                    lat0, lon0, ele0, lat1, lon1, ele1)
            pointList.set(index, lat, lon, ele)
        }
        if (count > 2)
            edge.setWayGeometry(pointList.shallowCopy(1, count - 1, false))
        edge.setDistance(DistanceCalcEarth.DIST_EARTH.calcDistance(pointList))
    }

    private fun isStructureEdge(edge: EdgeIteratorState): Boolean {
        val re = edge.get(roadEnvEnc)
        return re == RoadEnvironment.BRIDGE || re == RoadEnvironment.TUNNEL || re == RoadEnvironment.FERRY
    }

    companion object {
        private val LOGGER: Logger = LoggerFactory.getLogger(BridgeTunnelTowerCorrection::class.java)

        // How far outward to search via Dijkstra (in meter).
        private const val MAX_DIST_M = 50.0

        /**
         * Inverse-distance-squared weighting: closer samples dominate, so far ones don't drag the result
         * away from the road right next to the bridge end on steadily climbing/descending terrain.
         */
        private fun inverseDistanceWeightedMean(eles: DoubleArrayList, dists: DoubleArrayList): Double {
            var weightedSum = 0.0
            var totalWeight = 0.0
            for (i in 0 until eles.size()) {
                val d = Math.max(dists.get(i), 1.0)
                val w = 1.0 / (d * d)
                weightedSum += w * eles.get(i)
                totalWeight += w
            }
            return weightedSum / totalWeight
        }
    }
}
