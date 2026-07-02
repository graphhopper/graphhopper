package com.graphhopper.routing.util

import com.graphhopper.util.AngleCalc
import com.graphhopper.util.DistanceCalcEarth
import com.graphhopper.util.EdgeIteratorState
import com.graphhopper.util.FetchMode
import com.graphhopper.util.shapes.GHPoint

class HeadingEdgeFilter(
    private val directedEdgeFilter: DirectedEdgeFilter,
    private val heading: Double,
    private val pointNearHeading: GHPoint
) : EdgeFilter {

    override fun accept(edgeState: EdgeIteratorState): Boolean {
        val tolerance = 30.0
        // we only accept edges that are not too far away. It might happen that only far away edges match the heading
        // in which case we rather rely on the fallback snapping than return a match here.
        val maxDistance = 20.0
        val headingOfEdge = getHeadingOfGeometryNearPoint(edgeState, pointNearHeading, maxDistance)
        if (headingOfEdge.isNaN())
            // this edge is too far away. we do not accept it.
            return false
        // we accept the edge if either of the two directions roughly has the right heading
        return Math.abs(headingOfEdge - heading) < tolerance && directedEdgeFilter.accept(edgeState, false) ||
                Math.abs((headingOfEdge + 180) % 360 - heading) < tolerance && directedEdgeFilter.accept(edgeState, true)
    }

    companion object {
        /**
         * Calculates the heading (in degrees) of the given edge in fwd direction near the given point. If the point is
         * too far away from the edge (according to the maxDistance parameter) it returns Double.NaN.
         */
        @JvmStatic
        @JvmName("getHeadingOfGeometryNearPoint")
        internal fun getHeadingOfGeometryNearPoint(edgeState: EdgeIteratorState, point: GHPoint, maxDistance: Double): Double {
            val calcDist = DistanceCalcEarth.DIST_EARTH
            var closestDistance = Double.POSITIVE_INFINITY
            val points = edgeState.fetchWayGeometry(FetchMode.ALL)
            var closestPoint = -1
            for (i in 1 until points.size()) {
                val fromLat = points.getLat(i - 1)
                val fromLon = points.getLon(i - 1)
                val toLat = points.getLat(i)
                val toLon = points.getLon(i)
                // the 'distance' between the point and an edge segment is either the vertical distance to the segment or
                // the distance to the closer one of the two endpoints. here we save one call to calcDist per segment,
                // because each endpoint appears in two segments (except the first and last).
                var distance = if (calcDist.validEdgeDistance(point.lat, point.lon, fromLat, fromLon, toLat, toLon))
                    calcDist.calcDenormalizedDist(calcDist.calcNormalizedEdgeDistance(point.lat, point.lon, fromLat, fromLon, toLat, toLon))
                else
                    calcDist.calcDist(fromLat, fromLon, point.lat, point.lon)
                if (i == points.size() - 1)
                    distance = Math.min(distance, calcDist.calcDist(toLat, toLon, point.lat, point.lon))
                if (distance > maxDistance)
                    continue
                if (distance < closestDistance) {
                    closestDistance = distance
                    closestPoint = i
                }
            }
            if (closestPoint < 0)
                return Double.NaN

            val fromLat = points.getLat(closestPoint - 1)
            val fromLon = points.getLon(closestPoint - 1)
            val toLat = points.getLat(closestPoint)
            val toLon = points.getLon(closestPoint)
            return AngleCalc.ANGLE_CALC.calcAzimuth(fromLat, fromLon, toLat, toLon)
        }
    }
}
