package com.graphhopper;

import com.graphhopper.routing.RoutingAlgorithmFactory;
import com.graphhopper.routing.util.HintsMap;
import com.graphhopper.storage.CHProfile;

public class CHPreparationHandler {
    // see #1891
    public void addPreparation(CHProfile profile, CHProfileConfig config) {
    }

    public void prepare() {
    }

    public boolean isEnabled() {
        return false;
    }

    public RoutingAlgorithmFactory getDecoratedAlgorithmFactory(RoutingAlgorithmFactory routingAlgorithmFactory, HintsMap map) {
        return routingAlgorithmFactory;
    }
}
