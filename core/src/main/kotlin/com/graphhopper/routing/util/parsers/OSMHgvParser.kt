package com.graphhopper.routing.util.parsers

import com.graphhopper.reader.ReaderWay
import com.graphhopper.routing.ev.EdgeIntAccess
import com.graphhopper.routing.ev.EnumEncodedValue
import com.graphhopper.routing.ev.Hgv
import com.graphhopper.routing.util.parsers.helpers.OSMValueExtractor.conditionalWeightToTons
import com.graphhopper.storage.IntsRef

class OSMHgvParser(private val hgvEnc: EnumEncodedValue<Hgv>) : TagParser {

    override fun handleWayTags(edgeId: Int, edgeIntAccess: EdgeIntAccess, way: ReaderWay, relationFlags: IntsRef?) {
        val value = way.getTag("hgv:conditional", "")
        val index = value.indexOf("@")
        val hgvValue = if (index > 0 && conditionalWeightToTons(value) == 3.5)
            Hgv.find(value.substring(0, index).trim())
        else
            Hgv.find(way.getTag("hgv"))
        hgvEnc.setEnum(false, edgeId, edgeIntAccess, hgvValue)
    }
}
