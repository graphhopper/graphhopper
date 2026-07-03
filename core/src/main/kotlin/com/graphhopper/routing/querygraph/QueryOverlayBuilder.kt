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

package com.graphhopper.routing.querygraph

import com.graphhopper.coll.primitive.LongArrayList
import com.graphhopper.coll.GHIntObjectHashMap
import com.graphhopper.search.KVStorage
import com.graphhopper.storage.Graph
import com.graphhopper.storage.IntsRef
import com.graphhopper.storage.index.Snap
import com.graphhopper.util.DistancePlaneProjection
import com.graphhopper.util.DistancePlaneProjection.Companion.DIST_PLANE
import com.graphhopper.util.EdgeIteratorState
import com.graphhopper.util.FetchMode
import com.graphhopper.util.GHUtility
import com.graphhopper.util.PointList
import com.graphhopper.util.shapes.GHPoint3D

internal class QueryOverlayBuilder private constructor(
    private val firstVirtualNodeId: Int,
    private val firstVirtualEdgeId: Int,
    private val is3D: Boolean
) {
    private lateinit var queryOverlay: QueryOverlay

    private val virtualEdgesFwdForSnap = ArrayList<VirtualEdgeIteratorState>()
    private val virtualEdgesBwdForSnap = ArrayList<VirtualEdgeIteratorState>()

    companion object {
        @JvmStatic
        fun build(graph: Graph, snaps: List<Snap>): QueryOverlay =
            build(graph.nodes, graph.edges, graph.nodeAccess.is3D(), snaps)

        @JvmStatic
        fun build(firstVirtualNodeId: Int, firstVirtualEdgeId: Int, is3D: Boolean, snaps: List<Snap>): QueryOverlay =
            QueryOverlayBuilder(firstVirtualNodeId, firstVirtualEdgeId, is3D).build(snaps)
    }

    private fun build(resList: List<Snap>): QueryOverlay {
        queryOverlay = QueryOverlay(resList.size, is3D)
        buildVirtualEdges(resList)
        buildEdgeChangesAtRealNodes()
        return queryOverlay
    }

    /**
     * For all specified snaps calculate the snapped point and if necessary set the closest node
     * to a virtual one and reverse the closest edge. Additionally the wayIndex can change if an edge is
     * swapped.
     */
    private fun buildVirtualEdges(snaps: List<Snap>) {
        val edge2res = GHIntObjectHashMap<MutableList<Snap>>(snaps.size)

        // Phase 1
        // calculate snapped point and swap direction of closest edge if necessary
        for (snap in snaps) {
            // Do not create virtual node for a snap if it is directly on a tower node or not found
            if (snap.snappedPosition == Snap.Position.TOWER)
                continue

            var closestEdge: EdgeIteratorState = snap.closestEdge
                ?: throw IllegalStateException("Do not call QueryGraph.create with invalid Snap $snap")

            val base = closestEdge.baseNode

            // Force the identical direction for all closest edges.
            // It is important to sort multiple results for the same edge by its wayIndex
            var doReverse = base > closestEdge.adjNode
            if (base == closestEdge.adjNode) {
                // check for special case #162 where adj == base and force direction via latitude comparison
                val pl = closestEdge.fetchWayGeometry(FetchMode.PILLAR_ONLY)
                if (pl.size() > 1)
                    doReverse = pl.getLat(0) > pl.getLat(pl.size() - 1)
            }

            if (doReverse) {
                closestEdge = closestEdge.detach(true)
                val fullPL = closestEdge.fetchWayGeometry(FetchMode.ALL)
                snap.closestEdge = closestEdge
                if (snap.snappedPosition == Snap.Position.PILLAR)
                // ON pillar node
                    snap.wayIndex = fullPL.size() - snap.wayIndex - 1
                else
                // for case "OFF pillar node"
                    snap.wayIndex = fullPL.size() - snap.wayIndex - 2

                if (snap.wayIndex < 0)
                    throw IllegalStateException("Problem with wayIndex while reversing closest edge:$closestEdge, $snap")
            }

            // find multiple results on same edge
            val edgeId = closestEdge.edge
            var list = edge2res.get(edgeId)
            if (list == null) {
                list = ArrayList(5)
                edge2res.put(edgeId, list)
            }
            list.add(snap)
        }

        // Phase 2 - now it is clear which points cut one edge
        // 1. create point lists
        // 2. create virtual edges between virtual nodes and its neighbor (virtual or normal nodes)
        // hppc forEach(predicate) order: empty key (0) first, then slots ascending
        edge2res.forEachWhile { edgeId, results ->
            virtualEdgesFwdForSnap.clear()
            virtualEdgesBwdForSnap.clear()
            // we can expect at least one entry in the results
            val closestEdge = results[0].closestEdge!!
            val fullPL = closestEdge.fetchWayGeometry(FetchMode.ALL)
            val baseNode = closestEdge.baseNode
            results.sortWith(object : Comparator<Snap> {
                override fun compare(o1: Snap, o2: Snap): Int {
                    val diff = Integer.compare(o1.wayIndex, o2.wayIndex)
                    return if (diff == 0) {
                        java.lang.Double.compare(distanceOfSnappedPointToPillarNode(o1), distanceOfSnappedPointToPillarNode(o2))
                    } else {
                        diff
                    }
                }

                private fun distanceOfSnappedPointToPillarNode(o: Snap): Double {
                    val snappedPoint = o.getSnappedPoint()
                    val fromLat = fullPL.getLat(o.wayIndex)
                    val fromLon = fullPL.getLon(o.wayIndex)
                    return DistancePlaneProjection.DIST_PLANE.calcNormalizedDist(fromLat, fromLon, snappedPoint.lat, snappedPoint.lon)
                }
            })

            var prevPoint: GHPoint3D = fullPL.get(0)
            val adjNode = closestEdge.adjNode
            val origEdgeKey = closestEdge.edgeKey
            val origRevEdgeKey = closestEdge.reverseEdgeKey
            var prevWayIndex = 1
            var prevNodeId = baseNode
            var virtNodeId = queryOverlay.virtualNodes.size() + firstVirtualNodeId
            var addedEdges = false

            // Create base and adjacent PointLists for all non-equal virtual nodes.
            // We do so via inserting them at the correct position of fullPL and cutting the
            // fullPL into the right pieces.
            for (i in results.indices) {
                val res = results[i]
                if (res.closestEdge!!.baseNode != baseNode)
                    throw IllegalStateException("Base nodes have to be identical but were not: " + closestEdge + " vs " + res.closestEdge)

                val currSnapped = res.getSnappedPoint()

                // no new virtual nodes if very close ("snap" together)
                if (Snap.considerEqual(prevPoint.lat, prevPoint.lon, currSnapped.lat, currSnapped.lon)) {
                    res.closestNode = prevNodeId
                    res.setSnappedPoint(prevPoint)
                    res.wayIndex = if (i == 0) 0 else results[i - 1].wayIndex
                    res.snappedPosition = if (i == 0) Snap.Position.TOWER else results[i - 1].snappedPosition
                    res.queryDistance = DIST_PLANE.calcDist(prevPoint.lat, prevPoint.lon, res.queryPoint.lat, res.queryPoint.lon)
                    continue
                }

                queryOverlay.closestEdges.add(res.closestEdge!!.edge)
                val isPillar = res.snappedPosition == Snap.Position.PILLAR
                createEdges(origEdgeKey, origRevEdgeKey,
                    prevPoint, prevWayIndex, isPillar,
                    res.getSnappedPoint(), res.wayIndex,
                    fullPL, closestEdge, prevNodeId, virtNodeId)

                queryOverlay.virtualNodes.add(currSnapped.lat, currSnapped.lon, currSnapped.ele)

                // add edges again to set adjacent edges for newVirtNodeId
                if (addedEdges) {
                    queryOverlay.addVirtualEdge(queryOverlay.getVirtualEdge(queryOverlay.numVirtualEdges - 2))
                    queryOverlay.addVirtualEdge(queryOverlay.getVirtualEdge(queryOverlay.numVirtualEdges - 2))
                }

                addedEdges = true
                res.closestNode = virtNodeId
                prevNodeId = virtNodeId
                prevWayIndex = res.wayIndex + 1
                prevPoint = currSnapped
                virtNodeId++
            }

            // two edges between last result and adjacent node are still missing if not all points skipped
            if (addedEdges)
                createEdges(origEdgeKey, origRevEdgeKey,
                    prevPoint, prevWayIndex, false,
                    fullPL.get(fullPL.size() - 1), fullPL.size() - 2,
                    fullPL, closestEdge, virtNodeId - 1, adjNode)

            adjustDistances(virtualEdgesFwdForSnap, closestEdge.distance_mm)
            adjustDistances(virtualEdgesBwdForSnap, closestEdge.distance_mm)

            true
        }
    }

    private fun adjustDistances(virtualEdges: List<VirtualEdgeIteratorState>, originalDistance: Long) {
        // the sum of virtual edge distances can differ from the distance of the original edge:
        // - we use dist_plane instead of dist_earth in OSMReader (not entirely sure why)
        // - virtual edge distances include numeric errors (regardless of the dist calc)
        if (virtualEdges.isEmpty())
        // early exit & prevent division by zero below
            return
        val virtualDistances = LongArrayList(virtualEdges.size)
        for (v in virtualEdges)
            virtualDistances.add(v.distance_mm)

        // we allow adjustments up to 1mm. this should be enough to account for dist_plane vs. dist_earth for most segments including the ones we create in random graph tests
        val maxPerElement = 1L
        QueryOverlay.adjustValues(virtualDistances, originalDistance, maxPerElement)
        for (i in virtualEdges.indices)
            virtualEdges[i].setDistance_mm(virtualDistances.get(i))
    }

    private fun createEdges(origEdgeKey: Int, origRevEdgeKey: Int,
                            prevSnapped: GHPoint3D, prevWayIndex: Int, isPillar: Boolean, currSnapped: GHPoint3D, wayIndex: Int,
                            fullPL: PointList, closestEdge: EdgeIteratorState,
                            prevNodeId: Int, nodeId: Int) {
        val max = wayIndex + 1
        val basePoints = PointList(max - prevWayIndex + 1, is3D)
        basePoints.add(prevSnapped.lat, prevSnapped.lon, prevSnapped.ele)
        for (i in prevWayIndex until max) {
            basePoints.add(fullPL, i)
        }
        if (!isPillar) {
            basePoints.add(currSnapped.lat, currSnapped.lon, currSnapped.ele)
        }
        // basePoints must have at least the size of 2 to make sure fetchWayGeometry(FetchMode.ALL) returns at least 2
        assert(basePoints.size() >= 2) { "basePoints must have at least two points" }

        val baseReversePoints = basePoints.clone(true)
        val baseDistance = DistancePlaneProjection.DIST_PLANE.calcDistance(basePoints)
        val virtEdgeId = firstVirtualEdgeId + queryOverlay.numVirtualEdges / 2

        val reverse = closestEdge.get(EdgeIteratorState.REVERSE_STATE)
        // edges between base and snapped point
        val keyValues: Map<String, KVStorage.KValue> = closestEdge.keyValues
        val baseEdge = VirtualEdgeIteratorState(origEdgeKey, GHUtility.createEdgeKey(virtEdgeId, false),
            prevNodeId, nodeId, baseDistance, closestEdge.flags, keyValues, basePoints, reverse)
        val baseReverseEdge = VirtualEdgeIteratorState(origRevEdgeKey, GHUtility.createEdgeKey(virtEdgeId, true),
            nodeId, prevNodeId, baseDistance, IntsRef.deepCopyOf(closestEdge.flags), keyValues, baseReversePoints, !reverse)

        baseEdge.setReverseEdge(baseReverseEdge)
        baseReverseEdge.setReverseEdge(baseEdge)
        queryOverlay.addVirtualEdge(baseEdge)
        queryOverlay.addVirtualEdge(baseReverseEdge)
        // we collect the unique virtual edges separately here, so it is easier to adjust their distances afterwards
        virtualEdgesFwdForSnap.add(baseEdge)
        virtualEdgesBwdForSnap.add(baseReverseEdge)
    }

    private fun buildEdgeChangesAtRealNodes() {
        EdgeChangeBuilder.build(queryOverlay.closestEdges, queryOverlay.virtualEdges, firstVirtualNodeId, queryOverlay.edgeChangesAtRealNodes)
    }
}
