package com.graphhopper.routing.util

import com.graphhopper.routing.ev.DecimalEncodedValue
import com.graphhopper.storage.Graph
import com.graphhopper.util.DistanceCalcEarth
import com.graphhopper.util.FetchMode

class CurvatureCalculator(private val curvatureEnc: DecimalEncodedValue) {

    fun execute(graph: Graph) {
        val iter = graph.allEdges
        while (iter.next()) {
            val pointList = iter.fetchWayGeometry(FetchMode.ALL)
            val edgeDistance = iter.distance
            if (!pointList.isEmpty && edgeDistance > 0.1) {
                val lat0 = pointList.getLat(0)
                val lon0 = pointList.getLon(0)
                val latEnd = pointList.getLat(pointList.size() - 1)
                val lonEnd = pointList.getLon(pointList.size() - 1)
                val beeline = if (pointList.is3D)
                    DistanceCalcEarth.DIST_EARTH.calcDist3D(lat0, lon0, pointList.getEle(0), latEnd, lonEnd, pointList.getEle(pointList.size() - 1))
                else
                    DistanceCalcEarth.DIST_EARTH.calcDist(lat0, lon0, latEnd, lonEnd)
                // For now keep the formula simple. Maybe later use quadratic value as it might improve the "resolution"
                val curvature = beeline / edgeDistance
                iter.set(curvatureEnc, Math.max(curvatureEnc.minStorableDecimal, Math.min(curvatureEnc.maxStorableDecimal, curvature)))
            } else {
                iter.set(curvatureEnc, 1.0)
            }
        }
    }
}
