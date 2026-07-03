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
package com.graphhopper.util.details

import com.graphhopper.routing.querygraph.VirtualEdgeIteratorState
import com.graphhopper.routing.weighting.Weighting
import com.graphhopper.storage.Graph
import com.graphhopper.util.AngleCalc
import com.graphhopper.util.EdgeIteratorState
import com.graphhopper.util.FetchMode
import com.graphhopper.util.GHUtility
import com.graphhopper.util.Parameters.Details.INTERSECTION

/**
 * Calculate the intersections for a route. Every change of the edge id is considered an intersection.
 *
 * The format is inspired by the format that is consumed by Maplibre Navigation SDK.
 *
 * Explanation of the format:
 * - `entries` contain an array of the edges at that intersection. They are sorted by bearing, starting from 0 (which is 0° north) to 359. Every edge that we can turn onto is marked with “true” in the array.
 * - `bearings` contain an array of the edges at that intersection. They are sorted by bearing, starting from 0 (which is 0° north) to 359.  The array contains the bearings of each edge at that intersection.
 * - `in` marks the index in the “bearings” edge we are coming from.
 * - `out` the index we are going to.
 *
 * @author Robin Boldt
 */
class IntersectionDetails(graph: Graph, private val weighting: Weighting) : AbstractPathDetailsBuilder(INTERSECTION) {

    private var fromEdge = -1

    private var intersectionMap: Map<String, Any>? = null

    private val crossingExplorer = graph.createEdgeExplorer()
    private val nodeAccess = graph.nodeAccess

    override fun isEdgeDifferentToLastEdge(edge: EdgeIteratorState): Boolean {
        val toEdge = edgeId(edge)
        if (toEdge != fromEdge) {
            val intersectingEdges = ArrayList<IntersectionValues>()

            val baseNode = edge.baseNode

            val startLat = nodeAccess.getLat(baseNode)
            val startLon = nodeAccess.getLon(baseNode)

            val edgeIter = crossingExplorer.setBaseNode(baseNode)
            while (edgeIter.next()) {
                // We need to call detach to get the edgeId, as we need to check for VirtualEdgeIteratorState in #edgeId(), see discussion in #2590
                val tmpEdge = edgeIter.detach(false)

                val intersectionValues = IntersectionValues()
                intersectionValues.bearing = calculateBearing(startLat, startLon, tmpEdge)
                intersectionValues.`in` = edgeId(tmpEdge) == fromEdge
                intersectionValues.out = edgeId(tmpEdge) == edgeId(edge)
                // The in edge is always false, this means that u-turns are not considered as possible turning option
                intersectionValues.entry = !intersectionValues.`in` && weighting.calcEdgeWeight(tmpEdge, false).isFinite()

                intersectingEdges.add(intersectionValues)
            }

            // stable sort by bearing, exactly like the original stream().sorted(...)
            intersectingEdges.sortBy { it.bearing }

            intersectionMap = IntersectionValues.createIntersection(intersectingEdges)

            fromEdge = toEdge
            return true
        }
        return false
    }

    private fun calculateBearing(startLat: Double, startLon: Double, tmpEdge: EdgeIteratorState): Int {
        val wayGeo = tmpEdge.fetchWayGeometry(FetchMode.PILLAR_AND_ADJ)
        val latitude = wayGeo.getLat(0)
        val longitude = wayGeo.getLon(0)
        return Math.round(AngleCalc.ANGLE_CALC.calcAzimuth(startLat, startLon, latitude, longitude)).toInt()
    }

    private fun edgeId(edge: EdgeIteratorState): Int {
        return if (edge is VirtualEdgeIteratorState) {
            GHUtility.getEdgeFromEdgeKey(edge.originalEdgeKey)
        } else {
            edge.edge
        }
    }

    public override fun getCurrentValue(): Any? = intersectionMap
}
