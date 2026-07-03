package com.graphhopper.reader.dem

import com.graphhopper.coll.primitive.IntDoubleHashMap
import com.graphhopper.util.DistancePlaneProjection
import com.graphhopper.util.PointList

/**
 * Elevation data is read from DEM tiles that have data points for rectangular tiles usually having an
 * edge length of 30 or 90 meter. Elevation in between the middle points of those tiles will be
 * interpolated and weighted by the distance from a node to adjacent tile centers.
 *
 * Ways that go along cliffs or ridges are particularly affected by ups and downs that do not reflect
 * the actual elevation but may be artifacts originated from very accurately mapping when elevation has
 * a lower resolution.
 *
 * @author Christoph Lingg
 */
object EdgeElevationSmoothingMovingAverage {
    @JvmStatic
    fun smooth(geometry: PointList, maxWindowSize: Double) {
        if (geometry.size() <= 2) {
            // geometry consists only of tower nodes, there are no pillar nodes to be smoothed in between
            return
        }

        // calculate the distance between all points once here to avoid repeated calculation.
        // for n nodes there are always n-1 edges
        val distances = DoubleArray(geometry.size() - 1)
        for (i in 0..geometry.size() - 2) {
            distances[i] = DistancePlaneProjection.DIST_PLANE.calcDist(
                    geometry.getLat(i), geometry.getLon(i),
                    geometry.getLat(i + 1), geometry.getLon(i + 1)
            )
        }

        // map that will collect all smoothed elevation values, size is less by 2
        // because elevation of start and end point (tower nodes) won't be touched
        val averagedElevations = IntDoubleHashMap((geometry.size() - 1) * 4 / 3)

        // iterate over every pillar node to smooth its elevation
        // first and last points are left out as they are tower nodes
        for (i in 1..geometry.size() - 2) {
            // first, determine the average window which could be smaller when close to pillar nodes
            var searchDistance = maxWindowSize / 2.0

            var searchDistanceBack = 0.0
            for (j in i - 1 downTo 0) {
                searchDistanceBack += distances[j]
                if (searchDistanceBack > searchDistance) {
                    break
                }
            }

            // update search distance if pillar node is close to START tower node
            searchDistance = Math.min(searchDistance, searchDistanceBack)

            var searchDistanceForward = 0.0
            for (j in i until geometry.size() - 1) {
                searchDistanceForward += distances[j]
                if (searchDistanceForward > searchDistance) {
                    break
                }
            }

            // update search distance if pillar node is close to END tower node
            searchDistance = Math.min(searchDistance, searchDistanceForward)

            if (searchDistance <= 0.0) {
                // there is nothing to smooth. this is an edge case where pillar nodes share exactly the same location
                // as a tower node.
                // by doing so we avoid (at least theoretically) a division by zero later in the function call
                continue
            }

            // area under elevation curve
            var elevationArea = 0.0

            // first going again backwards
            var distanceBack = 0.0
            for (j in i - 1 downTo 0) {
                val dist = distances[j]
                val searchDistLeft = searchDistance - distanceBack
                distanceBack += dist
                if (searchDistLeft < dist) {
                    // node lies outside averaging window
                    val elevationDelta = geometry.getEle(j) - geometry.getEle(j + 1)
                    val elevationAtSearchDistance = geometry.getEle(j + 1) + searchDistLeft / dist * elevationDelta
                    elevationArea += searchDistLeft * (geometry.getEle(j + 1) + elevationAtSearchDistance) / 2.0
                    break
                } else {
                    elevationArea += dist * (geometry.getEle(j + 1) + geometry.getEle(j)) / 2.0
                }
            }

            // now going forward
            var distanceForward = 0.0
            for (j in i until geometry.size() - 1) {
                val dist = distances[j]
                val searchDistLeft = searchDistance - distanceForward
                distanceForward += dist
                if (searchDistLeft < dist) {
                    val elevationDelta = geometry.getEle(j + 1) - geometry.getEle(j)
                    val elevationAtSearchDistance = geometry.getEle(j) + searchDistLeft / dist * elevationDelta
                    elevationArea += searchDistLeft * (geometry.getEle(j) + elevationAtSearchDistance) / 2.0
                    break
                } else {
                    elevationArea += dist * (geometry.getEle(j + 1) + geometry.getEle(j)) / 2.0
                }
            }

            val elevationAverage = elevationArea / (searchDistance * 2)
            averagedElevations.put(i, elevationAverage)
        }

        // after all pillar nodes got an averaged elevation, elevations are overwritten
        averagedElevations.forEach { key, value ->
            geometry.setElevation(key, value)
        }
    }
}
