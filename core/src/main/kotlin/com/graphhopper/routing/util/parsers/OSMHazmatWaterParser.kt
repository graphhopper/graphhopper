package com.graphhopper.routing.util.parsers

import com.graphhopper.reader.ReaderWay
import com.graphhopper.routing.ev.EdgeIntAccess
import com.graphhopper.routing.ev.EnumEncodedValue
import com.graphhopper.routing.ev.HazmatWater
import com.graphhopper.storage.IntsRef

class OSMHazmatWaterParser(private val hazWaterEnc: EnumEncodedValue<HazmatWater>) : TagParser {

    override fun handleWayTags(edgeId: Int, edgeIntAccess: EdgeIntAccess, way: ReaderWay, relationFlags: IntsRef?) {
        if (way.hasTag("hazmat:water", "no")) {
            hazWaterEnc.setEnum(false, edgeId, edgeIntAccess, HazmatWater.NO)
        } else if (way.hasTag("hazmat:water", "permissive")) {
            hazWaterEnc.setEnum(false, edgeId, edgeIntAccess, HazmatWater.PERMISSIVE)
        }
    }
}
