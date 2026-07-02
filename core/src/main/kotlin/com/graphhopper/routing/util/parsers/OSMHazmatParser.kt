package com.graphhopper.routing.util.parsers

import com.graphhopper.reader.ReaderWay
import com.graphhopper.routing.ev.EdgeIntAccess
import com.graphhopper.routing.ev.EnumEncodedValue
import com.graphhopper.routing.ev.Hazmat
import com.graphhopper.storage.IntsRef

class OSMHazmatParser(private val hazEnc: EnumEncodedValue<Hazmat>) : TagParser {

    override fun handleWayTags(edgeId: Int, edgeIntAccess: EdgeIntAccess, way: ReaderWay, relationFlags: IntsRef?) {
        if (way.hasTag("hazmat", "no"))
            hazEnc.setEnum(false, edgeId, edgeIntAccess, Hazmat.NO)
    }
}
