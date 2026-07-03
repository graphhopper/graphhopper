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

import com.graphhopper.coll.primitive.IntHashSet
import com.graphhopper.routing.util.EdgeFilter
import com.graphhopper.routing.util.tour.MultiPointTour
import com.graphhopper.routing.util.tour.TourStrategy
import com.graphhopper.routing.weighting.AvoidEdgesWeighting
import com.graphhopper.storage.index.LocationIndex
import com.graphhopper.storage.index.Snap
import com.graphhopper.util.DistanceCalcEarth
import com.graphhopper.util.PMap
import com.graphhopper.util.Parameters.Algorithms.RoundTrip
import com.graphhopper.util.PointList
import com.graphhopper.util.exceptions.PointNotFoundException
import com.graphhopper.util.shapes.GHPoint
import java.util.Random
import kotlin.math.min

/**
 * Implementation of calculating a route with one or more round trip (route with identical start and
 * end).
 *
 * @author Peter Karich
 */
object RoundTripRouting {

    class Params @JvmOverloads constructor(hints: PMap = PMap(), initialHeading: Double = 0.0, maxRetries: Int = 3) {
        @JvmField
        val distanceInMeter: Double = hints.getDouble(RoundTrip.DISTANCE, 10_000.0)

        @JvmField
        val seed: Long = hints.getLong(RoundTrip.SEED, 0L)

        @JvmField
        val initialHeading: Double = initialHeading

        @JvmField
        val roundTripPointCount: Int = min(20, hints.getInt(RoundTrip.POINTS, 2 + (distanceInMeter / 50000).toInt()))

        @JvmField
        val maxRetries: Int = maxRetries
    }

    @JvmStatic
    fun lookup(points: List<GHPoint>, edgeFilter: EdgeFilter, locationIndex: LocationIndex?, params: Params): List<Snap> {
        // todo: no snap preventions for round trip so far (the nullable locationIndex matches the Java original,
        // where the point-count check must fire before the index is touched)
        if (points.size != 1)
            throw IllegalArgumentException("For round trip calculation exactly one point is required")

        val start = points[0]

        val strategy: TourStrategy = MultiPointTour(Random(params.seed), params.distanceInMeter, params.roundTripPointCount, params.initialHeading)
        val snaps = ArrayList<Snap>(2 + strategy.getNumberOfGeneratedPoints())
        val startSnap = locationIndex!!.findClosest(start.lat, start.lon, edgeFilter)
        if (!startSnap.isValid)
            throw PointNotFoundException("Cannot find point 0: $start", 0)

        snaps.add(startSnap)

        var last: GHPoint = start
        for (i in 0 until strategy.getNumberOfGeneratedPoints()) {
            val heading = strategy.getHeadingForIteration(i)
            val result = generateValidPoint(last, strategy.getDistanceForIteration(i), heading, edgeFilter, locationIndex, params.maxRetries)
            last = result.getSnappedPoint()
            snaps.add(result)
        }

        snaps.add(startSnap)
        return snaps
    }

    private fun generateValidPoint(lastPoint: GHPoint, distanceInMeters: Double, heading: Double, edgeFilter: EdgeFilter, locationIndex: LocationIndex, maxRetries: Int): Snap {
        var distanceInMeters = distanceInMeters
        var tryCount = 0
        while (true) {
            val generatedPoint = DistanceCalcEarth.DIST_EARTH.projectCoordinate(lastPoint.lat, lastPoint.lon, distanceInMeters, heading)
            val snap = locationIndex.findClosest(generatedPoint.lat, generatedPoint.lon, edgeFilter)
            if (snap.isValid)
                return snap

            tryCount++
            distanceInMeters *= 0.95

            if (tryCount >= maxRetries)
                throw IllegalArgumentException("Could not find a valid point after $maxRetries tries, for the point:$lastPoint")
        }
    }

    @JvmStatic
    fun calcPaths(snaps: List<Snap>, pathCalculator: FlexiblePathCalculator): Result {
        val roundTripCalculator = RoundTripCalculator(pathCalculator)
        val result = Result(snaps.size - 1)
        val start = snaps[0]
        for (snapIndex in 1 until snaps.size) {
            // instead getClosestNode (which might be a virtual one and introducing unnecessary tails of the route)
            // use next tower node -> getBaseNode or getAdjNode
            // Later: remove potential route tail, maybe we can just enforce the heading at the start and when coming
            // back, and for tower nodes it does not matter anyway
            val startSnap = snaps[snapIndex - 1]
            val startNode = if (startSnap === start) startSnap.closestNode else startSnap.closestEdge!!.baseNode
            val endSnap = snaps[snapIndex]
            val endNode = if (endSnap === start) endSnap.closestNode else endSnap.closestEdge!!.baseNode

            val path = roundTripCalculator.calcPath(startNode, endNode)
            if (snapIndex == 1) {
                result.wayPoints = PointList(snaps.size, path.getGraph().nodeAccess.is3D())
                result.wayPoints!!.add(path.getGraph().nodeAccess, startNode)
            }
            result.wayPoints!!.add(path.getGraph().nodeAccess, endNode)
            result.visitedNodes += pathCalculator.getVisitedNodes()
            result.paths.add(path)
        }

        return result
    }

    class Result internal constructor(legs: Int) {
        @JvmField
        var paths: MutableList<Path> = ArrayList(legs)

        @JvmField
        var wayPoints: PointList? = null

        @JvmField
        var visitedNodes: Long = 0
    }

    /**
     * Calculates paths and avoids edges of previous path calculations
     */
    private class RoundTripCalculator(val pathCalculator: FlexiblePathCalculator) {
        val previousEdges = IntHashSet()

        init {
            // we make the path calculator use our avoid edges weighting
            val avoidPreviousPathsWeighting = AvoidEdgesWeighting(pathCalculator.getWeighting())
                .setEdgePenaltyFactor(5.0)
            avoidPreviousPathsWeighting.setAvoidedEdges(previousEdges)
            pathCalculator.setWeighting(avoidPreviousPathsWeighting)
        }

        fun calcPath(from: Int, to: Int): Path {
            val path = pathCalculator.calcPaths(from, to, EdgeRestrictions())[0]
            // add the edges of this path to the set of previous edges so they will be avoided from now, otherwise
            // we do not get a nice 'round trip'. note that for this reason we cannot use CH for round-trips currently
            for (c in path.getEdges()) {
                previousEdges.add(c.value)
            }
            return path
        }
    }
}
