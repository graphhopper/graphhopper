package com.graphhopper.routing.util.parsers

import com.graphhopper.reader.ReaderWay
import com.graphhopper.routing.ev.EdgeIntAccess
import com.graphhopper.routing.ev.EnumEncodedValue
import com.graphhopper.routing.ev.MaxWeightExcept
import com.graphhopper.routing.util.TransportationMode
import com.graphhopper.routing.util.parsers.helpers.OSMValueExtractor
import com.graphhopper.routing.util.parsers.helpers.OSMValueExtractor.stringToTons
import com.graphhopper.storage.IntsRef

class MaxWeightExceptParser(private val mweEnc: EnumEncodedValue<MaxWeightExcept>) : TagParser {

    override fun handleWayTags(edgeId: Int, edgeIntAccess: EdgeIntAccess, way: ReaderWay, relationFlags: IntsRef?) {
        // tagging like maxweight:conditional=no/none @ destination/delivery/forestry/service
        val condValue = way.getTag("maxweight:conditional", "")
        if (!condValue.isEmpty()) {
            val values = condValue.split("@").dropLastWhile { it.isEmpty() }
            if (values.size == 2) {
                val key = values[0].trim()
                var value = values[1].trim()
                if ("no" == key || "none" == key) {
                    if (value.startsWith("(") && value.endsWith(")")) value = value.substring(1, value.length - 1)
                    mweEnc.setEnum(false, edgeId, edgeIntAccess, MaxWeightExcept.find(value))
                    return
                }
            }
        }

        // For tagging like vehicle:conditional=destination @ (weight>3.5) AND maxweight=3.5
        // For vehicle:conditional=no @ (weight>3.5) => NONE is used, which is consistent with max_weight being set to 3.5 in this case
        for (restriction in HGV_RESTRICTIONS) {
            val value = way.getTag(restriction, "")
            val atIndex = value.indexOf("@")
            if (atIndex > 0) {
                val dec = OSMValueExtractor.conditionalWeightToTons(value)
                // set it only if the weight value is the same as in max_weight
                if (!dec.isNaN()
                    && (stringToTons(way.getTag("maxweight", "")) == dec
                            || stringToTons(way.getTag("maxweightrating:hgv", "")) == dec
                            || stringToTons(way.getTag("maxgcweight", "")) == dec)
                ) {
                    mweEnc.setEnum(false, edgeId, edgeIntAccess, MaxWeightExcept.find(value.substring(0, atIndex).trim()))
                    break
                }
            }
        }
    }

    companion object {
        private val HGV_RESTRICTIONS = OSMRoadAccessParser.toOSMRestrictions(TransportationMode.HGV)
            .map { it + ":conditional" }
    }
}
