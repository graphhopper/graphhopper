package com.graphhopper.farmy;

import com.graphhopper.GraphHopperAPI;

public class SingleRouteOptimizer extends BaseRouteOptimizer {
    public SingleRouteOptimizer(GraphHopperAPI graphHopper, FarmyOrder[] farmyOrders, FarmyVehicle[] farmyVehicles, IdentifiedGHPoint3D depotPoint) throws Exception {
        super(graphHopper, farmyOrders, farmyVehicles, depotPoint);
    }
}
