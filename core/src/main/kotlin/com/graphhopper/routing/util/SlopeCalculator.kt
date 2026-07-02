package com.graphhopper.routing.util

import com.graphhopper.routing.ev.DecimalEncodedValue
import com.graphhopper.storage.Graph
import com.graphhopper.util.DistanceCalcEarth
import com.graphhopper.util.FetchMode

class SlopeCalculator(
    private val maxSlopeEnc: DecimalEncodedValue?,
    private val averageSlopeEnc: DecimalEncodedValue?
) {

    fun execute(graph: Graph) {
        val iter = graph.allEdges
        while (iter.next()) {
            val pointList = iter.fetchWayGeometry(FetchMode.ALL)
            if (!pointList.is3D)
                throw IllegalArgumentException("Cannot calculate slope for 2D PointList $pointList")
            if (pointList.isEmpty) {
                if (maxSlopeEnc != null)
                    iter.set(maxSlopeEnc, 0.0)
                if (averageSlopeEnc != null)
                    iter.set(averageSlopeEnc, 0.0)
                continue
            }
            // Calculate 2d distance, although pointList might be 3D.
            val distance2D = DistanceCalcEarth.calcDistance(pointList, false)
            if (distance2D < MIN_LENGTH) {
                // default minimum of average_slope and max_slope is negative => set it explicitly to 0
                if (averageSlopeEnc != null)
                    iter.set(averageSlopeEnc, 0.0)
                if (maxSlopeEnc != null)
                    iter.set(maxSlopeEnc, 0.0)
                continue
            }

            val towerNodeSlope = calcSlope(pointList.getEle(pointList.size() - 1) - pointList.getEle(0), distance2D)
            if (towerNodeSlope.isNaN())
                throw IllegalArgumentException("average_slope was NaN for edge " + iter.edge + " " + pointList)

            if (averageSlopeEnc != null) {
                if (towerNodeSlope >= 0)
                    iter.set(averageSlopeEnc, Math.min(towerNodeSlope, averageSlopeEnc.maxStorableDecimal))
                else
                    iter.setReverse(averageSlopeEnc, Math.min(Math.abs(towerNodeSlope), averageSlopeEnc.maxStorableDecimal))
            }

            if (maxSlopeEnc != null) {
                // max_slope is more error-prone as the shorter distances increase the fluctuation
                // so apply some more filtering (here we use the average elevation delta of the previous two points)
                var maxSlope = 0.0
                var prevDist = 0.0
                var prevLat = pointList.getLat(0)
                var prevLon = pointList.getLon(0)
                for (i in 1 until pointList.size()) {
                    val pillarDistance2D = DistanceCalcEarth.DIST_EARTH.calcDist(prevLat, prevLon, pointList.getLat(i), pointList.getLon(i))
                    if (i > 1 && prevDist > MIN_LENGTH) {
                        val averagedPrevEle = (pointList.getEle(i - 1) + pointList.getEle(i - 2)) / 2
                        val tmpSlope = calcSlope(pointList.getEle(i) - averagedPrevEle, pillarDistance2D + prevDist / 2)
                        maxSlope = if (Math.abs(tmpSlope) > Math.abs(maxSlope)) tmpSlope else maxSlope
                    }
                    prevDist = pillarDistance2D
                    prevLat = pointList.getLat(i)
                    prevLon = pointList.getLon(i)
                }

                maxSlope = if (Math.abs(towerNodeSlope) > Math.abs(maxSlope)) towerNodeSlope else maxSlope

                if (maxSlope.isNaN())
                    throw IllegalArgumentException("max_slope was NaN for edge " + iter.edge + " " + pointList)

                val value = Math.max(maxSlope, maxSlopeEnc.minStorableDecimal)
                iter.set(maxSlopeEnc, Math.min(maxSlopeEnc.maxStorableDecimal, value))
            }
        }
    }

    companion object {
        // the elevation data fluctuates a lot and so the slope is not that precise for short edges.
        private const val MIN_LENGTH = 8.0

        private fun calcSlope(eleDelta: Double, distance2D: Double): Double = eleDelta * 100 / distance2D
    }
}
