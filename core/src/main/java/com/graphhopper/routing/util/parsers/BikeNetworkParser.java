package com.graphhopper.routing.util.parsers;

import com.graphhopper.routing.ev.RouteNetwork;

public class BikeNetworkParser {
    static RouteNetwork determine(String tag) {
        RouteNetwork newBikeNetwork = RouteNetwork.LOCAL;
        if ("lcn".equals(tag)) {
            newBikeNetwork = RouteNetwork.LOCAL;
        } else if ("rcn".equals(tag)) {
            newBikeNetwork = RouteNetwork.REGIONAL;
        } else if ("ncn".equals(tag)) {
            newBikeNetwork = RouteNetwork.NATIONAL;
        } else if ("icn".equals(tag)) {
            newBikeNetwork = RouteNetwork.INTERNATIONAL;
        }
        return newBikeNetwork;
    }
}
