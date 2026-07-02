package com.graphhopper.routing.util

import com.graphhopper.reader.ReaderWay
import com.graphhopper.routing.ev.DecimalEncodedValue
import com.graphhopper.routing.ev.EdgeIntAccess
import com.graphhopper.routing.util.parsers.TagParser
import com.graphhopper.storage.IntsRef

class FerrySpeedCalculator(private val ferrySpeedEnc: DecimalEncodedValue) : TagParser {

    override fun handleWayTags(edgeId: Int, edgeIntAccess: EdgeIntAccess, way: ReaderWay, relationFlags: IntsRef?) {
        if (isFerry(way)) {
            val ferrySpeed = minmax(getSpeed(way), ferrySpeedEnc)
            ferrySpeedEnc.setDecimal(false, edgeId, edgeIntAccess, ferrySpeed)
        }
    }

    companion object {
        @JvmStatic
        fun isFerry(way: ReaderWay): Boolean =
            way.hasTag("route", "ferry") && !way.hasTag("ferry", "no") ||
                    // TODO shuttle_train is sometimes also used in relations, e.g. https://www.openstreetmap.org/relation/1932780
                    way.hasTag("route", "shuttle_train") && !way.hasTag("shuttle_train", "no")

        @JvmStatic
        @JvmName("getSpeed")
        internal fun getSpeed(way: ReaderWay): Double {
            // todo: We cannot account for waiting times for short ferries as speed is slower than the slowest we can store

            // OSMReader adds the artificial 'duration_in_seconds' and 'way_distance_2d' tags that we can
            // use to set the ferry speed. Otherwise we need to use fallback values.
            val durationInSeconds = way.getTag("duration_in_seconds", 0L)
            if (durationInSeconds > 0) {
                // a way can consist of multiple edges like https://www.openstreetmap.org/way/61215714 => use way_distance_2d
                val waitTime = 30 * 60.0
                val wayDistance = way.getTag("way_distance_2d", Double.NaN)
                return Math.round(wayDistance / 1000 / ((durationInSeconds + waitTime) / 60.0 / 60.0)).toDouble()
            } else {
                val edgeDistance = way.getTag("edge_distance", Double.NaN)
                val shuttleFactor = if (way.hasTag("route", "shuttle_train")) 2 else 1
                if (edgeDistance.isNaN())
                    throw IllegalStateException("No 'edge_distance' set for edge created for way: " + way.id)
                // When we have no speed value to work with we have to take a guess based on the distance.
                return if (edgeDistance < 1000) {
                    // Use the slowest possible speed for very short ferries. Note that sometimes these aren't really ferries
                    // that take you from one harbour to another, but rather ways that only represent the beginning of a
                    // longer ferry connection and that are used by multiple different connections, like here: https://www.openstreetmap.org/way/107913687
                    // It should not matter much which speed we use in this case, so we have no special handling for these.
                    (5 * shuttleFactor).toDouble()
                } else if (edgeDistance < 30_000) {
                    (15 * shuttleFactor).toDouble()
                } else {
                    (30 * shuttleFactor).toDouble()
                }
            }
        }

        @JvmStatic
        @JvmName("minmax")
        internal fun minmax(speed: Double, avgSpeedEnc: DecimalEncodedValue): Double =
            Math.max(avgSpeedEnc.smallestNonZeroValue, Math.min(speed, avgSpeedEnc.maxStorableDecimal))
    }
}
