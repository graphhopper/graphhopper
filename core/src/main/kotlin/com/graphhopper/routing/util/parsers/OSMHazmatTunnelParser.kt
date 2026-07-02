package com.graphhopper.routing.util.parsers

import com.graphhopper.reader.ReaderWay
import com.graphhopper.routing.ev.EdgeIntAccess
import com.graphhopper.routing.ev.EnumEncodedValue
import com.graphhopper.routing.ev.HazmatTunnel
import com.graphhopper.storage.IntsRef

class OSMHazmatTunnelParser(private val hazTunnelEnc: EnumEncodedValue<HazmatTunnel>) : TagParser {

    override fun handleWayTags(edgeId: Int, edgeIntAccess: EdgeIntAccess, way: ReaderWay, relationFlags: IntsRef?) {
        if (way.hasTag("hazmat:adr_tunnel_cat", *TUNNEL_CATEGORY_NAMES)) {
            val code = HazmatTunnel.valueOf(way.getTag("hazmat:adr_tunnel_cat")!!)
            hazTunnelEnc.setEnum(false, edgeId, edgeIntAccess, code)
        } else if (way.hasTag("hazmat:tunnel_cat", *TUNNEL_CATEGORY_NAMES)) {
            val code = HazmatTunnel.valueOf(way.getTag("hazmat:tunnel_cat")!!)
            hazTunnelEnc.setEnum(false, edgeId, edgeIntAccess, code)
        } else if (way.hasTag("tunnel", "yes")) {
            val codes = HazmatTunnel.entries
            for (i in codes.indices.reversed()) {
                if (way.hasTag("hazmat:" + codes[i].name, "no")) {
                    hazTunnelEnc.setEnum(false, edgeId, edgeIntAccess, codes[i])
                    break
                }
            }
        }
    }

    companion object {
        private val TUNNEL_CATEGORY_NAMES: Array<String> =
            HazmatTunnel.entries.map { it.name }.toTypedArray()
    }
}
