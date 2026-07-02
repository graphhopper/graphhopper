package com.graphhopper.routing.util.parsers

import com.graphhopper.reader.ReaderWay
import com.graphhopper.routing.ev.BooleanEncodedValue
import com.graphhopper.routing.ev.EdgeIntAccess
import com.graphhopper.storage.IntsRef

/**
 * This parser scans different OSM tags to identify ways where a cyclist has to get off her bike. Like on footway but
 * also in reverse oneway direction.
 *
 * @param bikeAccessEnc used to find out if way is oneway and so it does not matter which bike type is used.
 */
class OSMGetOffBikeParser(
    private val getOffBikeEnc: BooleanEncodedValue,
    private val bikeAccessEnc: BooleanEncodedValue
) : TagParser {

    override fun handleWayTags(edgeId: Int, edgeIntAccess: EdgeIntAccess, way: ReaderWay, relationFlags: IntsRef?) {
        val highway = way.getTag("highway")
        val vehicle = way.getTag("vehicle", "")
        val notIntended = !way.hasTag("bicycle", INTENDED) &&
                (GET_OFF_BIKE.contains(highway)
                        || way.hasTag("railway", "platform")
                        || "cycleway" != highway && way.hasTag("vehicle", "no")
                        || vehicle.contains("forestry")
                        || vehicle.contains("agricultural")
                        || "path" == highway && way.hasTag("foot", "designated") && !way.hasTag("segregated", "yes"))
        if ("steps" == highway || way.hasTag("bicycle", "dismount") || notIntended) {
            getOffBikeEnc.setBool(false, edgeId, edgeIntAccess, true)
            getOffBikeEnc.setBool(true, edgeId, edgeIntAccess, true)
        }
        val fwd = bikeAccessEnc.getBool(false, edgeId, edgeIntAccess)
        val bwd = bikeAccessEnc.getBool(true, edgeId, edgeIntAccess)
        // get off bike for reverse oneways
        if (fwd != bwd) {
            if (!fwd) getOffBikeEnc.setBool(false, edgeId, edgeIntAccess, true)
            if (!bwd) getOffBikeEnc.setBool(true, edgeId, edgeIntAccess, true)
        }
    }

    companion object {
        private val INTENDED = listOf("designated", "yes", "official", "permissive")

        // steps -> special handling, path -> see #2777
        private val GET_OFF_BIKE = hashSetOf("footway", "pedestrian", "platform")
    }
}
