package com.graphhopper.routing.util.parsers

import com.graphhopper.reader.ReaderWay
import com.graphhopper.routing.ev.Crossing
import com.graphhopper.routing.ev.EdgeIntAccess
import com.graphhopper.routing.ev.EnumEncodedValue
import com.graphhopper.storage.IntsRef
import com.graphhopper.util.Helper

/**
 * Parses the node information regarding crossing=* and railway=*
 */
class OSMCrossingParser(private val crossingEnc: EnumEncodedValue<Crossing>) : TagParser {

    override fun handleWayTags(edgeId: Int, edgeIntAccess: EdgeIntAccess, way: ReaderWay, relationFlags: IntsRef?) {
        val nodeTags: List<Map<String, Any>> = way.getTag("node_tags", null) ?: return

        for (i in nodeTags.indices) {
            val tags = nodeTags[i]
            if ("crossing" == tags["railway"] || "level_crossing" == tags["railway"]) {
                val barrierVal = tags["crossing:barrier"] as String?
                crossingEnc.setEnum(false, edgeId, edgeIntAccess,
                    if (Helper.isEmpty(barrierVal) || "no" == barrierVal) Crossing.RAILWAY else Crossing.RAILWAY_BARRIER)
                return
            }

            val crossingSignals = tags["crossing:signals"] as String?
            if ("yes" == crossingSignals) {
                crossingEnc.setEnum(false, edgeId, edgeIntAccess, Crossing.TRAFFIC_SIGNALS)
                return
            }

            val crossingMarkings = tags["crossing:markings"] as String?
            if ("yes" == crossingMarkings) {
                crossingEnc.setEnum(false, edgeId, edgeIntAccess, Crossing.MARKED)
                return
            }

            val crossingValue = tags["crossing"] as String?
            // some crossing values like "no" do not require highway=crossing and sometimes no crossing value exists although highway=crossing
            if (Helper.isEmpty(crossingValue) && ("no" == crossingSignals || "no" == crossingMarkings
                        || "crossing" == tags["highway"] || "crossing" == tags["footway"] || "crossing" == tags["cycleway"])) {
                crossingEnc.setEnum(false, edgeId, edgeIntAccess, Crossing.UNMARKED)
                // next node could have more specific Crossing value
                continue
            }
            val crossing = Crossing.find(crossingValue)
            if (crossing != Crossing.MISSING)
                crossingEnc.setEnum(false, edgeId, edgeIntAccess, crossing)
        }
    }
}
