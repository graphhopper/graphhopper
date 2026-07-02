package com.graphhopper.routing.util.parsers

import com.graphhopper.routing.ev.RouteNetwork

object BikeNetworkParserHelper {

    @JvmStatic
    @JvmName("determine")
    internal fun determine(tag: String?): RouteNetwork = when (tag) {
        "lcn" -> RouteNetwork.LOCAL
        "rcn" -> RouteNetwork.REGIONAL
        "ncn" -> RouteNetwork.NATIONAL
        "icn" -> RouteNetwork.INTERNATIONAL
        else -> RouteNetwork.LOCAL
    }
}
