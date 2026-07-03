package com.graphhopper.storage.index

import com.graphhopper.coll.primitive.IntArrayList
import com.graphhopper.geohash.SpatialKeyAlgo
import com.graphhopper.util.DistanceCalcEarth
import com.graphhopper.util.DistanceCalcEarth.Companion.DIST_EARTH
import com.graphhopper.util.shapes.BBox
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToLong
import kotlin.math.sqrt

class IndexStructureInfo(
    val entries: IntArray,
    val shifts: ByteArray,
    val pixelGridTraversal: PixelGridTraversal,
    val keyAlgo: SpatialKeyAlgo,
    val bounds: BBox,
    val parts: Int
) {
    val deltaLat: Double
        get() = (bounds.maxLat - bounds.minLat) / parts

    val deltaLon: Double
        get() = (bounds.maxLon - bounds.minLon) / parts

    companion object {
        @JvmStatic
        fun create(bounds: BBox, minResolutionInMeter: Int): IndexStructureInfo {
            // I still need to be able to save and load an empty LocationIndex, and I can't when the extent
            // is zero.
            val bbox = if (!bounds.isValid()) BBox(-10.0, 10.0, -10.0, 10.0) else bounds

            val lat = min(abs(bbox.maxLat), abs(bbox.minLat))
            val maxDistInMeter = max(
                (bbox.maxLat - bbox.minLat) / 360 * DistanceCalcEarth.C,
                (bbox.maxLon - bbox.minLon) / 360 * DIST_EARTH.calcCircumference(lat)
            )
            var tmp = maxDistInMeter / minResolutionInMeter
            tmp *= tmp
            val tmpEntries = IntArrayList()
            // the last one is always 4 to reduce costs if only a single entry
            tmp /= 4
            while (tmp > 1) {
                val tmpNo: Int
                if (tmp >= 16) {
                    tmpNo = 16
                } else if (tmp >= 4) {
                    tmpNo = 4
                } else {
                    break
                }
                tmpEntries.add(tmpNo)
                tmp /= tmpNo
            }
            tmpEntries.add(4)
            val entries = tmpEntries.toArray()
            if (entries.size < 1) {
                // at least one depth should have been specified
                throw IllegalStateException("depth needs to be at least 1")
            }
            val depth = entries.size
            val shifts = ByteArray(depth)
            var lastEntry = entries[0]
            for (i1 in 0 until depth) {
                if (lastEntry < entries[i1]) {
                    throw IllegalStateException("entries should decrease or stay but was:"
                            + entries.contentToString())
                }
                lastEntry = entries[i1]
                shifts[i1] = getShift(entries[i1])
            }
            var shiftSum = 0
            var parts = 1L
            for (i in shifts.indices) {
                shiftSum += shifts[i]
                parts *= entries[i]
            }
            if (shiftSum > 64)
                throw IllegalStateException("sum of all shifts does not fit into a long variable")
            val partsInt = sqrt(parts.toDouble()).roundToLong().toInt()

            return IndexStructureInfo(entries, shifts, PixelGridTraversal(partsInt, bbox), SpatialKeyAlgo(shiftSum, bbox), bbox, partsInt)
        }

        private fun getShift(entries: Int): Byte {
            val b = (ln(entries.toDouble()) / ln(2.0)).roundToLong().toByte()
            if (b <= 0)
                throw IllegalStateException("invalid shift:$b")

            return b
        }
    }
}
