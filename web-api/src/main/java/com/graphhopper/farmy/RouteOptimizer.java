package com.graphhopper.farmy;

import com.graphhopper.GraphHopperAPI;

public class RouteOptimizer extends BaseRouteOptimizer {

    public RouteOptimizer(GraphHopperAPI graphHopper, FarmyOrder[] farmyOrders, FarmyVehicle[] farmyVehicles, IdentifiedGHPoint3D depotPoint) throws Exception {
        super(graphHopper, farmyOrders, farmyVehicles, depotPoint);
    }

}
