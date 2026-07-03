package com.graphhopper.routing.lm

import com.graphhopper.routing.util.EdgeFilter
import com.graphhopper.storage.index.LocationIndex
import com.graphhopper.util.Helper
import com.graphhopper.util.shapes.BBox
import com.graphhopper.util.shapes.GHPoint
import java.io.IOException

/**
 * This class collects landmarks from an external source for one subnetwork to avoid the expensive and sometimes
 * suboptimal automatic landmark finding process.
 */
class LandmarkSuggestion(private val nodeIds: List<Int>, private val box: BBox) {

    fun getNodeIds(): List<Int> = nodeIds

    fun getBox(): BBox = box

    companion object {
        /**
         * The expected format is lon,lat per line where lines starting with characters will be ignored. You can create
         * such a file manually via geojson.io -> Save as CSV. Optionally add a second line with
         * <pre>#BBOX:minLat,minLon,maxLat,maxLon</pre>
         *
         * to specify an explicit bounding box. TODO: support GeoJSON instead.
         */
        @JvmStatic
        @Throws(IOException::class)
        fun readLandmarks(file: String, locationIndex: LocationIndex): LandmarkSuggestion {
            // landmarks should be suited for all vehicles
            val edgeFilter = EdgeFilter.ALL_EDGES
            val lines = Helper.readFile(file)
            val landmarkNodeIds = ArrayList<Int>()
            var bbox = BBox.createInverse(false)
            var lmSuggestionIdx = 0
            var errors = ""
            for (lmStr in lines) {
                if (lmStr.startsWith("#BBOX:")) {
                    bbox = BBox.parseTwoPoints(lmStr.substring("#BBOX:".length))
                    continue
                } else if (lmStr.isEmpty() || Character.isAlphabetic(lmStr[0].code)) {
                    continue
                }

                val point = GHPoint.fromStringLonLat(lmStr)
                    ?: throw RuntimeException("Invalid format $lmStr for point $lmSuggestionIdx")

                lmSuggestionIdx++
                val result = locationIndex.findClosest(point.lat, point.lon, edgeFilter)
                if (!result.isValid) {
                    errors += "Cannot find close node found for landmark suggestion[$lmSuggestionIdx]=$point.\n"
                    continue
                }

                bbox.update(point.lat, point.lon)
                landmarkNodeIds.add(result.closestNode)
            }

            if (!errors.isEmpty())
                throw RuntimeException(errors)

            return LandmarkSuggestion(landmarkNodeIds, bbox)
        }
    }
}
