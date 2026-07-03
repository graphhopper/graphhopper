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

import com.graphhopper.coll.primitive.IntArrayList
import com.graphhopper.routing.ev.EncodedValueLookup
import com.graphhopper.routing.ev.RoadClass
import com.graphhopper.routing.ev.RoadEnvironment
import com.graphhopper.routing.querygraph.QueryGraph
import com.graphhopper.routing.querygraph.VirtualEdgeIteratorState
import com.graphhopper.routing.util.DirectedEdgeFilter
import com.graphhopper.routing.util.EdgeFilter
import com.graphhopper.routing.util.EncodingManager
import com.graphhopper.routing.util.HeadingEdgeFilter
import com.graphhopper.routing.util.NameSimilarityEdgeFilter
import com.graphhopper.routing.util.SnapPreventionEdgeFilter
import com.graphhopper.storage.index.LocationIndex
import com.graphhopper.storage.index.Snap
import com.graphhopper.util.EdgeIterator.Companion.ANY_EDGE
import com.graphhopper.util.EdgeIterator.Companion.NO_EDGE
import com.graphhopper.util.Helper
import com.graphhopper.util.Parameters.Curbsides.CURBSIDE_ANY
import com.graphhopper.util.Parameters.Curbsides.CURBSIDE_AUTO
import com.graphhopper.util.Parameters.Routing.CURBSIDE
import com.graphhopper.util.shapes.GHPoint

/**
 * The methods here can be used to calculate routes with or without via points and implement possible restrictions
 * like snap preventions, headings and curbsides.
 *
 * @author Peter Karich
 * @author easbar
 */
object ViaRouting {

    /**
     * @throws MultiplePointsNotFoundException in case one or more points could not be resolved
     */
    @JvmStatic
    fun lookup(lookup: EncodedValueLookup, points: List<GHPoint>, snapFilter: EdgeFilter,
               locationIndex: LocationIndex, snapPreventions: List<String>, pointHints: List<String>,
               directedSnapFilter: DirectedEdgeFilter, headings: List<Double>): List<Snap> {
        if (points.size < 2)
            throw IllegalArgumentException("At least 2 points have to be specified, but was:" + points.size)

        val roadClassEnc = lookup.getEnumEncodedValue(RoadClass.KEY, RoadClass::class.java)
        val roadEnvEnc = lookup.getEnumEncodedValue(RoadEnvironment.KEY, RoadEnvironment::class.java)
        val strictEdgeFilter = if (snapPreventions.isEmpty())
            snapFilter
        else
            SnapPreventionEdgeFilter(snapFilter, roadClassEnc, roadEnvEnc, snapPreventions)
        val snaps = ArrayList<Snap>(points.size)
        val pointsNotFound = IntArrayList()
        for (placeIndex in points.indices) {
            val point = points[placeIndex]
            var snap: Snap? = null
            if (placeIndex < headings.size && !headings[placeIndex].isNaN()) {
                if (!pointHints.isEmpty() && !Helper.isEmpty(pointHints[placeIndex]))
                    throw IllegalArgumentException("Cannot specify heading and point_hint at the same time. " +
                            "Make sure you specify either an empty point_hint (String) or a NaN heading (double) for point " + placeIndex)
                snap = locationIndex.findClosest(point.lat, point.lon, HeadingEdgeFilter(directedSnapFilter, headings[placeIndex], point))
            } else if (!pointHints.isEmpty()) {
                snap = locationIndex.findClosest(point.lat, point.lon, NameSimilarityEdgeFilter(strictEdgeFilter,
                        pointHints[placeIndex], point, 170.0))
            } else if (!snapPreventions.isEmpty()) {
                snap = locationIndex.findClosest(point.lat, point.lon, strictEdgeFilter)
            }

            if (snap == null || !snap.isValid)
                snap = locationIndex.findClosest(point.lat, point.lon, snapFilter)
            if (!snap.isValid)
                pointsNotFound.add(placeIndex)

            snaps.add(snap)
        }

        if (!pointsNotFound.isEmpty)
            throw MultiplePointsNotFoundException(pointsNotFound)

        return snaps
    }

    @JvmStatic
    fun calcPaths(points: List<GHPoint>, queryGraph: QueryGraph, snaps: List<Snap>,
                  directedEdgeFilter: DirectedEdgeFilter, pathCalculator: PathCalculator,
                  curbsides: List<String>, curbsideStrictness: String, headings: List<Double>, passThrough: Boolean, em: EncodingManager): Result {
        if (!curbsides.isEmpty() && curbsides.size != points.size)
            throw IllegalArgumentException("If you pass $CURBSIDE, you need to pass exactly one curbside for every point, empty curbsides will be ignored")
        if (!curbsides.isEmpty() && !headings.isEmpty())
            throw IllegalArgumentException("You cannot use curbsides and headings or pass_through at the same time")

        val curbsideAutoFunction = CurbsideAutoHelper.createResolver(directedEdgeFilter, em)

        val legs = snaps.size - 1
        val result = Result(legs)
        for (leg in 0 until legs) {
            val fromSnap = snaps[leg]
            val toSnap = snaps[leg + 1]

            // enforce headings
            // at via-nodes and the target node the heading parameter is interpreted as the direction we want
            // to enforce for arriving (not starting) at this node. the starting direction is not enforced at
            // all for these points (unless using pass through). see this forum discussion:
            // https://discuss.graphhopper.com/t/meaning-of-heading-parameter-for-via-routing/5643/6
            val fromHeading = if (leg == 0 && !headings.isEmpty()) headings[0] else Double.NaN
            val toHeading = if (snaps.size == headings.size && !headings[leg + 1].isNaN()) headings[leg + 1] else Double.NaN

            // enforce pass-through
            var incomingEdge = NO_EDGE
            if (leg != 0) {
                // enforce straight start after via stop
                val prevRoute = result.paths[leg - 1]
                if (prevRoute.getEdgeCount() > 0)
                    incomingEdge = prevRoute.getFinalEdge()!!.edge
            }

            // enforce curbsides
            var fromCurbside = if (curbsides.isEmpty()) CURBSIDE_ANY else curbsides[leg]
            var toCurbside = if (curbsides.isEmpty()) CURBSIDE_ANY else curbsides[leg + 1]

            if (CURBSIDE_AUTO == fromCurbside)
                fromCurbside = curbsideAutoFunction.apply(fromSnap)
            if (CURBSIDE_AUTO == toCurbside)
                toCurbside = curbsideAutoFunction.apply(toSnap)

            val edgeRestrictions = buildEdgeRestrictions(queryGraph, fromSnap, toSnap,
                    fromHeading, toHeading, incomingEdge, passThrough,
                    fromCurbside, toCurbside, directedEdgeFilter)

            edgeRestrictions.setSourceOutEdge(ignoreThrowOrAcceptImpossibleCurbsides(curbsides, edgeRestrictions.getSourceOutEdge(), leg, curbsideStrictness))
            edgeRestrictions.setTargetInEdge(ignoreThrowOrAcceptImpossibleCurbsides(curbsides, edgeRestrictions.getTargetInEdge(), leg + 1, curbsideStrictness))

            // calculate paths
            val paths = pathCalculator.calcPaths(fromSnap.closestNode, toSnap.closestNode, edgeRestrictions)
            result.debug += pathCalculator.getDebugString()

            // for alternative routing we get multiple paths and add all of them (which is ok, because we do not allow
            // via-points for alternatives at the moment). otherwise we would have to return a list<list<path>> and find
            // a good method to decide how to combine the different legs
            for (i in paths.indices) {
                val path = paths[i]
                if (path.getTime() < 0)
                    throw RuntimeException("Time was negative " + path.getTime() + " for index " + i)

                result.paths.add(path)
                result.debug += ", " + path.getDebugInfo()
            }

            result.visitedNodes += pathCalculator.getVisitedNodes()
            result.debug += ", visited nodes sum: " + result.visitedNodes
        }

        return result
    }

    class Result internal constructor(legs: Int) {
        @JvmField
        var paths: MutableList<Path> = ArrayList(legs)

        @JvmField
        var visitedNodes: Long = 0

        @JvmField
        var debug = ""
    }

    /**
     * Determines restrictions for the start/target edges to account for the heading, pass_through and curbside parameters
     * for a single via-route leg.
     *
     * @param fromHeading  the heading at the start node of this leg, or NaN if no restriction should be applied
     * @param toHeading    the heading at the target node (the vehicle's heading when arriving at the target), or NaN if
     *                     no restriction should be applied
     * @param incomingEdge the last edge of the previous leg (or [com.graphhopper.util.EdgeIterator.NO_EDGE] if not available
     */
    private fun buildEdgeRestrictions(
            queryGraph: QueryGraph, fromSnap: Snap, toSnap: Snap,
            fromHeading: Double, toHeading: Double, incomingEdge: Int, passThrough: Boolean,
            fromCurbside: String, toCurbside: String, edgeFilter: DirectedEdgeFilter): EdgeRestrictions {
        var toHeading = toHeading
        val edgeRestrictions = EdgeRestrictions()

        // curbsides
        if (fromCurbside != CURBSIDE_ANY || toCurbside != CURBSIDE_ANY) {
            val directedEdgeFilter = DirectedEdgeFilter { edge, reverse ->
                // todo: maybe find a cleaner way to obtain the original edge given a VirtualEdgeIterator (not VirtualEdgeIteratorState)
                if (queryGraph.isVirtualEdge(edge.edge)) {
                    val virtualEdge = queryGraph.getEdgeIteratorStateForKey(edge.edgeKey)
                    val origEdge = queryGraph.getEdgeIteratorStateForKey((virtualEdge as VirtualEdgeIteratorState).originalEdgeKey)
                    edgeFilter.accept(origEdge, reverse)
                } else
                    edgeFilter.accept(edge, reverse)
            }

            val directionResolver = DirectionResolver(queryGraph, directedEdgeFilter)
            val fromDirection = directionResolver.resolveDirections(fromSnap.closestNode, fromSnap.queryPoint)
            val toDirection = directionResolver.resolveDirections(toSnap.closestNode, toSnap.queryPoint)
            var sourceOutEdge = DirectionResolverResult.getOutEdge(fromDirection, fromCurbside)
            var targetInEdge = DirectionResolverResult.getInEdge(toDirection, toCurbside)
            if (fromSnap.closestNode == toSnap.closestNode) {
                // special case where we go from one point back to itself. for example going from a point A
                // with curbside right to the same point with curbside right is interpreted as 'being there
                // already' -> empty path. Similarly if the curbside for the start/target is not even specified
                // there is no need to drive a loop. However, going from point A/right to point A/left (or the
                // other way around) means we need to drive some kind of loop to get back to the same location
                // (arriving on the other side of the road).
                if (Helper.isEmpty(fromCurbside) || Helper.isEmpty(toCurbside) ||
                        fromCurbside == CURBSIDE_ANY || toCurbside == CURBSIDE_ANY ||
                        fromCurbside == toCurbside) {
                    // we just disable start/target edge constraints to get an empty path
                    sourceOutEdge = ANY_EDGE
                    targetInEdge = ANY_EDGE
                }
            }
            edgeRestrictions.setSourceOutEdge(sourceOutEdge)
            edgeRestrictions.setTargetInEdge(targetInEdge)
        }

        // heading
        if (!fromHeading.isNaN() || !toHeading.isNaN()) {
            // todo: for heading/pass_through with edge-based routing (especially CH) we have to find the edge closest
            // to the heading and use it as sourceOutEdge/targetInEdge here. the heading penalty will not be applied
            // this way (unless we implement this), but this is more or less ok as we can use finite u-turn costs
            // instead. maybe the hardest part is dealing with headings that cannot be fulfilled, like in one-way
            // streets. see also #1765
            val headingResolver = HeadingResolver(queryGraph)
            if (!fromHeading.isNaN())
                edgeRestrictions.getUnfavoredEdges().addAll(headingResolver.getEdgesWithDifferentHeading(fromSnap.closestNode, fromHeading))

            if (!toHeading.isNaN()) {
                toHeading += 180
                if (toHeading > 360)
                    toHeading -= 360
                edgeRestrictions.getUnfavoredEdges().addAll(headingResolver.getEdgesWithDifferentHeading(toSnap.closestNode, toHeading))
            }
        }

        // pass through
        if (incomingEdge != NO_EDGE && passThrough)
            edgeRestrictions.getUnfavoredEdges().add(incomingEdge)
        return edgeRestrictions
    }

    private fun ignoreThrowOrAcceptImpossibleCurbsides(curbsides: List<String>, edge: Int, placeIndex: Int, curbsideStrictness: String): Int {
        if (edge != NO_EDGE) {
            return edge
        }
        return if ("strict" == curbsideStrictness) {
            throwImpossibleCurbsideConstraint(curbsides, placeIndex)
        } else if ("soft" == curbsideStrictness) {
            ANY_EDGE
        } else {
            throw IllegalArgumentException("Unknown curbside_strictness $curbsideStrictness")
        }
    }

    private fun throwImpossibleCurbsideConstraint(curbsides: List<String>, placeIndex: Int): Int {
        throw IllegalArgumentException("Impossible curbside constraint: 'curbside=" + curbsides[placeIndex] + "' at point " + placeIndex)
    }
}
