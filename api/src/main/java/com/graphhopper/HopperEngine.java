package com.graphhopper;


import com.graphhopper.engine.configuration.EngineConfiguration;
import com.graphhopper.routing.Path;

import java.util.List;

public interface HopperEngine {

    // Process configuration and inizialize the graph (load, or get the cached value etc...)
    // Usually called automatically from the *HopperClient construction and not by the user
    public HopperEngine inizialize(EngineConfiguration configuration);

    // Get the basic base route data.
    // This must allow to build RouteInstruction and RoutePoint and RouteError
    // in AbstractHopperClient.route()
    //
    // I'm not actually sure that List<Path> is actually sufficient, probably not,
    // but anyway we can make an public EngineResponse route() or something like that...
    public List<Path> route(HopperRequest request);
}
