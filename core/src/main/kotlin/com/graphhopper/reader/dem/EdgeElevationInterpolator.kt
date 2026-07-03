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

import com.graphhopper.coll.GHBitSet
import com.graphhopper.coll.GHBitSetImpl
import com.graphhopper.coll.GHIntHashSet
import com.graphhopper.coll.GHTBitSet
import com.graphhopper.routing.ev.EnumEncodedValue
import com.graphhopper.routing.ev.RoadEnvironment
import com.graphhopper.storage.BaseGraph
import com.graphhopper.util.BreadthFirstSearch
import com.graphhopper.util.DistanceCalcEarth
import com.graphhopper.util.EdgeExplorer
import com.graphhopper.util.EdgeIteratorState
import com.graphhopper.util.FetchMode

/**
 * Abstract base class for tunnel/bridge edge elevation interpolators. This
 * class estimates elevation of inner nodes of a tunnel/bridge based on
 * elevations of entry nodes. See #713 for more information.
 *
 * Since inner nodes of tunnel or bridge do not lie on the Earth surface, we
 * should not use elevations returned by the elevation provider for these
 * points. Instead, we'll estimate elevations of these points based on
 * elevations of entry/exit nodes of the tunnel/bridge.
 *
 * To do this, we'll iterate over the graph looking for tunnel or bridge edges
 * using [isInterpolatableEdge]. Once such an edge is
 * found, we'll calculate a connected component of tunnel/bridge edges starting
 * from the base node of this edge, using simple [BreadthFirstSearch].
 * Nodes which only have interpolatabe edges connected to them are inner nodes
 * and are considered to not lie on the Earth surface. Nodes which also have
 * non-interpolatable edges are outer nodes and are considered to lie on the
 * Earth surface. Elevations of inner nodes are then interpolated from the outer
 * nodes using [NodeElevationInterpolator]. Elevations of pillar nodes are
 * calculated using linear interpolation on distances from tower nodes.
 *
 * @author Alexey Valikov
 */
open class EdgeElevationInterpolator(
    private val graph: BaseGraph,
    @JvmField
    protected val roadEnvironmentEnc: EnumEncodedValue<RoadEnvironment>,
    private val interpolateKey: RoadEnvironment
) {
    private val nodeElevationInterpolator = NodeElevationInterpolator(graph)
    private val elevationInterpolator = ElevationInterpolator()

    protected open fun isInterpolatableEdge(edge: EdgeIteratorState): Boolean {
        return edge.get(roadEnvironmentEnc) == interpolateKey
    }

    fun getGraph(): BaseGraph = graph

    fun execute() {
        interpolateElevationsOfInnerTowerNodes()
        interpolateElevationsOfPillarNodes()
    }

    private fun interpolateElevationsOfInnerTowerNodes() {
        val edge = graph.allEdges
        val visitedEdgeIds: GHBitSet = GHBitSetImpl(edge.length())
        val edgeExplorer = graph.createEdgeExplorer()

        while (edge.next()) {
            val edgeId = edge.edge
            if (isInterpolatableEdge(edge)) {
                if (!visitedEdgeIds.contains(edgeId)) {
                    interpolateEdge(edge, visitedEdgeIds, edgeExplorer)
                }
            }
            visitedEdgeIds.add(edgeId)
        }
    }

    private fun interpolateEdge(interpolatableEdge: EdgeIteratorState,
                                visitedEdgeIds: GHBitSet, edgeExplorer: EdgeExplorer) {
        val outerNodeIds = GHIntHashSet()
        val innerNodeIds = GHIntHashSet()
        gatherOuterAndInnerNodeIds(edgeExplorer, interpolatableEdge, visitedEdgeIds, outerNodeIds, innerNodeIds)
        nodeElevationInterpolator.interpolateElevationsOfInnerNodes(outerNodeIds.toArray(), innerNodeIds.toArray())
    }

    fun gatherOuterAndInnerNodeIds(edgeExplorer: EdgeExplorer,
                                   interpolatableEdge: EdgeIteratorState, visitedEdgesIds: GHBitSet,
                                   outerNodeIds: GHIntHashSet, innerNodeIds: GHIntHashSet) {
        val gatherOuterAndInnerNodeIdsSearch = object : BreadthFirstSearch() {
            override fun createBitSet(): GHBitSet {
                return GHTBitSet()
            }

            override fun checkAdjacent(edge: EdgeIteratorState): Boolean {
                visitedEdgesIds.add(edge.edge)
                val baseNodeId = edge.baseNode
                val isInterpolatableEdge = isInterpolatableEdge(edge)
                if (!isInterpolatableEdge) {
                    innerNodeIds.remove(baseNodeId)
                    outerNodeIds.add(baseNodeId)
                } else if (!outerNodeIds.contains(baseNodeId)) {
                    innerNodeIds.add(baseNodeId)
                }
                return isInterpolatableEdge
            }
        }
        gatherOuterAndInnerNodeIdsSearch.start(edgeExplorer, interpolatableEdge.baseNode)
    }

    private fun interpolateElevationsOfPillarNodes() {
        val edge = graph.allEdges
        val nodeAccess = graph.nodeAccess
        while (edge.next()) {
            if (isInterpolatableEdge(edge)) {
                val firstNodeId = edge.baseNode
                val secondNodeId = edge.adjNode

                val lat0 = nodeAccess.getLat(firstNodeId)
                val lon0 = nodeAccess.getLon(firstNodeId)
                val ele0 = nodeAccess.getEle(firstNodeId)

                val lat1 = nodeAccess.getLat(secondNodeId)
                val lon1 = nodeAccess.getLon(secondNodeId)
                val ele1 = nodeAccess.getEle(secondNodeId)

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
        }
    }
}
